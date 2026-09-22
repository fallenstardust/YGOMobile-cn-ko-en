package cn.garymb.ygomobile.game;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.audio.SoundManager;
import cn.garymb.ygomobile.utils.CrashHandler;

/**
 * 录像回放播放器（GameEngine 协作类，取代旧的 ReplayEngine）。
 *
 * <p><b>唯一渲染路径</b>：本类只负责「加载录像 → 取消息 → 卡码归一 → 按节奏投喂
 * {@link GameEngine} 的实况管线（{@code enqueueGameMsg} + 动画闸门）→ 播控（暂停/单步/
 * 上一步/从头重放/视角互换/停止）」。场地动画、召唤与连锁大图、音效、LP 浮字、阶段文字、
 * 卡片详情全部由实况侧（GameMessageParser → DuelEventHandler → GameField/GameFieldView +
 * EngineCallbackDelegate）产生，与联机对局、观战<b>完全同源</b>；旧 ReplayEngine 里那套
 * 自建渲染分支（processMessage / replayRefresh / applyFieldQuery 家族，约 1900 行）整体删除，
 * 这也是回放动画队列不如观战流畅的根因。
 *
 * <p>消息来自两种 {@link ReplaySource}，本类对二者一视同仁：
 * {@link MsgStreamReplaySource}（录像内含主机 MSG 流）与 {@link EngineReplaySource}
 * （旧格式重跑 ocgcore + 录制响应记录）。
 *
 * <p>节奏：只有「可见步」（移动/召唤/连锁/攻击/抽卡/回合阶段切换）才等待管线排空并留地板
 * 间隔，其余消息瞬流过；上一步/从头重放/跳过回合用 <em>快进三标志</em>
 * （{@code engine.replaySkip} + {@code field.instantPlace} + 音效静默）把管线推到目标步后落点暂停。
 *
 * <p>卡码：投喂前经 {@link ReplayCodeMapper} 做「卡表感知的条件映射」（本机卡表认得的码
 * 原样保留，查不到才换成对照表目标码且目标码必须查得到），解决先行号/正式号双表导致的
 * 手卡/卡组 unknown；引擎重跑喂给 ocgcore 的仍是<b>原始码</b>（seed 复现依赖原码）。
 * 每次播放结束的映射统计随 {@link CrashHandler} 落盘到 ygocore/log。
 */
public final class ReplayPlayer implements ReplayMessageSlicer.ZoneBlocks,
        ReplayCodeMapper.BlockCountProvider {

    private static final String TAG = "ReplayPlayer";

    /** 消息号（ocgcore common.h）：本类需要特判的两条 */
    private static final int MSG_START = 4;
    private static final int MSG_NEW_TURN = 40;

    public enum State { IDLE, LOADING, PLAYING, PAUSED, FINISHED, ERROR }

    /**
     * 回放对外回调：只剩「播放状态 / 顶部提示 / 播放结束」三件事。
     * 场地、玩家信息、回合与阶段刷新由实况管线经 EngineCallbackDelegate 派发，无需重复一套。
     */
    public interface Listener {
        void onReplayStateChanged(State state);

        void onReplayHintMessage(String hint);

        /** MSG_WIN 结算（winner=2 平局；reason 见 sysString 1300 表） */
        void onReplayFinished(int winner, int reason);
    }

    private final GameEngine engine;
    private final SoundManager soundManager;
    private final GameField field;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private Listener listener;
    private volatile State state = State.IDLE;

    private ReplaySource source;
    private ReplayReader.ReplayData replayData;
    /** 显示用卡组/额外卡码（已按本机卡表归一）；引擎重跑仍用 replayData.decks 的原码 */
    private List<Integer> displayMain0, displayExtra0, displayMain1, displayExtra1;

    private Thread loadThread;
    private Thread pumpThread;
    private volatile boolean isRunning;
    private volatile boolean isPaused;
    private volatile boolean isRestarting;
    private volatile boolean isSwapping;
    /** 快进重排中（跳回合 / undo / restart）：即时落位 + 抑制动画音效，直到落点还原 */
    private volatile boolean isSkipping;
    private volatile boolean replayWinSeen;
    private volatile int stepsRemaining;
    private volatile int currentStep;
    /** 快进剩余待跳过的可见步 / 待跳过的回合数（落点由 skipStep==0 或 skipTurn==0 判定） */
    private int skipStep;
    private int skipTurn;
    private volatile int restartTargetStep;
    private int restartFromStep;
    /** undo/restart 目标步为 0：消费并重投 MSG_START（重建初始场）后即停 */
    private boolean landAtStart;
    private volatile String lastErrorMessage;
    /**
     * 会话代数：{@link #stop()} / {@link #loadAndPlay} 每次递增，作废旧 load/pump 线程的收尾派发。
     * 修复「首次点播即弹录像结束」：旧写法 stop() 末尾 setState(FINISHED)，会把上一次会话的
     * 终态派发给刚挂上的监听器；且 setState 对 FINISHED 去重，导致第二次进入反而不弹。
     */
    private volatile long sessionGen;

    public ReplayPlayer(GameEngine engine) {
        this.engine = engine;
        this.soundManager = engine.soundManager;
        this.field = engine.field;
    }

    // === 对外状态与控制 ===

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    /** 摘除 UI 监听：重复进入回放时先于 {@link #stop()} 调用，使旧会话剩余回调静默 */
    public void detachListener() {
        this.listener = null;
    }

    public State getState() {
        return state;
    }

    public String getLastErrorMessage() {
        return lastErrorMessage;
    }

    public ReplayReader.ReplayData getReplayData() {
        return replayData;
    }

    public int getCurrentStep() {
        return currentStep;
    }

    /** 是否有正在进行的回放会话（UI 判定按钮可用性与退出确认） */
    public boolean hasActiveSession() {
        return isRunning;
    }

    public void pause() {
        isPaused = true;
        setState(State.PAUSED);
    }

    public void resume() {
        isPaused = false;
        setState(State.PLAYING);
    }

    /** 步进前进：处理下一个可见步（带动画）后自动暂停，对齐 C++ ReplayMode 单步执行 */
    public void skipAhead() {
        if (isPaused && isRunning) {
            stepsRemaining = 1;
            isPaused = false;
            setState(State.PLAYING);
        }
    }

    /** 上一步：回到上一个可见步（重放到该步后暂停） */
    public void undo() {
        if (skipStep > 0 || currentStep == 0 || !isRunning) return;
        restartTargetStep = currentStep - 1;
        isRestarting = true;
        resume();
    }

    /** 从头重放 */
    public void restart() {
        if (!isRunning) return;
        restartTargetStep = 0;
        isRestarting = true;
        resume();
    }

    /** 交换上下方视角（对齐 gframe 录像界面的“洗牌”按钮 = SwapField） */
    public void swapField() {
        if (!isRunning) return;
        if (isPaused) {
            performSwapField();
        } else {
            isSwapping = true;
        }
    }

    public void stop() {
        sessionGen++;             // 作废当前会话：旧 load/pump 线程收尾时静默丢弃
        isRunning = false;
        isPaused = false;
        Thread t = pumpThread;
        if (t != null) t.interrupt();
        Thread l = loadThread;
        if (l != null) l.interrupt();
        clearReplayFlags();
        closeSourceQuietly();
        // 停止不派发结束态：「录像已结束」弹窗只来自真实终态（finishSession / fail）；
        // 状态静默回 IDLE，由下次 loadAndPlay 重新派发 LOADING
        state = State.IDLE;
    }

    // === 加载 ===

    public void loadAndPlay(String replayPath) {
        loadAndPlay(replayPath, 1);
    }

    /**
     * 后台线程解析录像并起投喂线程。startTurn &gt; 1 时先快进到该回合起点再正常播放
     * （对齐 gframe 录像界面的「起始回合」）。
     */
    public void loadAndPlay(String replayPath, int startTurn) {
        stop();
        final long gen = sessionGen;      // 本次会话代数（stop 刚递增过）
        setState(State.LOADING);
        lastErrorMessage = null;
        currentStep = 0;
        replayWinSeen = false;
        ReplayCodeMapper.beginSession();
        loadThread = new Thread(() -> load(replayPath, startTurn, gen), "ReplayLoad");
        loadThread.setDaemon(true);
        CrashHandler.getInstance().hookThread(loadThread, "回放-加载");
        loadThread.start();
    }

    private void load(String replayPath, int startTurn, long gen) {
        try {
            replayData = ReplayReader.loadReplay(replayPath);
            if (sessionGen != gen) return;    // 加载期间已被退出/重新点播作废：静默退出
            if (replayData == null) {
                fail("无法加载录像文件");
                return;
            }
            // 含 MSG 流的录像直接消费消息流；旧格式重跑引擎复现消息流（ReplaySource 内部自适应）
            source = replayData.msgBuffer != null
                    ? new MsgStreamReplaySource(this)
                    : new EngineReplaySource(this);
            if (!source.open()) {
                fail(source.getLastError() == null ? "录像数据源初始化失败" : source.getLastError());
                return;
            }
            if (sessionGen != gen) return;    // 同上：开源自检期间会话已作废
            prepareDisplayDecks();
            startSession();
            if (source.engineDriven()) {
                // 旧格式重跑不产 MSG_START（gframe 由 dField.Initial 建场），此处自行建初始场
                buildInitialField();
                applyReplayDeckCodes();
            }
            setState(State.PLAYING);
            isRunning = true;
            isPaused = false;
            skipTurn = startTurn > 1 ? startTurn - 1 : 0;
            isSkipping = skipTurn > 0;
            notifyHint(buildReplayInfo(startTurn));
            pumpThread = new Thread(() -> pump(gen), "ReplayPump");
            pumpThread.setDaemon(true);
            CrashHandler.getInstance().hookThread(pumpThread, "回放-投喂");
            pumpThread.start();
        } catch (Throwable t) {
            CrashHandler.getInstance().report("replay-load " + replayPath, t);
            fail("录像加载异常：" + t);
        }
    }

    /** 显示用卡码副本：按本机卡表归一（先行号↔正式号），原始 replayData.decks 留给引擎重跑 */
    private void prepareDisplayDecks() {
        displayMain0 = displayExtra0 = displayMain1 = displayExtra1 = null;
        if (replayData == null || replayData.decks.isEmpty()) return;
        List<ReplayReader.DeckInfo> decks = replayData.decks;
        int p1 = replayData.isTag ? 2 : 1;
        displayMain0 = mappedCodes(deckAt(decks, 0), true);
        displayExtra0 = mappedCodes(deckAt(decks, 0), false);
        displayMain1 = mappedCodes(deckAt(decks, p1), true);
        displayExtra1 = mappedCodes(deckAt(decks, p1), false);
    }

    private static ReplayReader.DeckInfo deckAt(List<ReplayReader.DeckInfo> decks, int index) {
        return index >= 0 && index < decks.size() ? decks.get(index) : null;
    }

    private static List<Integer> mappedCodes(ReplayReader.DeckInfo deck, boolean main) {
        if (deck == null) return null;
        List<Integer> src = main ? deck.main : deck.extra;
        List<Integer> out = new ArrayList<>(src == null ? 0 : src.size());
        if (src == null) return out;
        for (Integer id : src) {
            out.add(id == null ? 0 : ReplayCodeMapper.mapCode(id));
        }
        return out;
    }

    /**
     * 旧格式重跑的初始场（对齐 replay_mode.cpp StartDuel 的 dField.Initial）：
     * 纯消息模式由实况管线的 MSG_START 建场，两条路径的建场口径与此一致。
     */
    private void buildInitialField() {
        field.clear();
        int startLp = replayData.params.startLp;
        field.dInfo.duelRule = replayData.params.duelFlag >> 16;
        field.players[0].lp = startLp;
        field.players[1].lp = startLp;
        field.dInfo.startLp = startLp;
        field.dInfo.lp[0] = startLp;
        field.dInfo.lp[1] = startLp;
        if (!replayData.isSingleMode) {
            field.initial(0, sizeOf(displayMain0), sizeOf(displayExtra0), 0);
            field.initial(1, sizeOf(displayMain1), sizeOf(displayExtra1), 0);
        }
        notifyPlayerInfo(0);
        notifyPlayerInfo(1);
    }

    private static int sizeOf(List<Integer> list) {
        return list == null ? 0 : list.size();
    }

    // === 投喂主循环（两种消息来源共用） ===

    private void pump(long gen) {
        long fedSincePump = 0;
        try {
            soundManager.playBGM(SoundManager.BGM.DUEL);
            if (isSkipping) beginInstantSkip();
            while (isRunning) {
                if (isPaused && !isRestarting) {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        break;
                    }
                    continue;
                }
                if (isRestarting) {
                    performRestart();
                    fedSincePump = 0;
                    continue;
                }
                if (isSwapping) {
                    performSwapField();
                    isSwapping = false;
                }
                ReplaySource.Msg msg = source.next();
                if (msg == null) {
                    String err = source.getLastError();
                    if (err != null && lastErrorMessage == null) lastErrorMessage = err;
                    break;
                }
                if (msg.type == ReplayMessageSlicer.MSG_WIN) {
                    // 结算不走实况管线（messageParser.onWin 回放侧已抑制），此处判结束并通知 UI
                    handleWin(msg.body);
                    break;
                }
                if (!ReplayMessageSlicer.isNoFeed(msg.type)) {
                    // 卡码归一在投喂副本上就地完成，不改写录像文件；
                    // 引擎重跑合成的刷新消息已在源头按查询块自界长归一（不依赖实况侧卡数）
                    if (!msg.synthetic) {
                        ReplayCodeMapper.mapMessage(msg.type, msg.body, this);
                    }
                    feedMessage(msg.type, msg.body);
                    fedSincePump++;
                }
                if (msg.type == MSG_START) {
                    // MSG_START 经实况管线建场（field.initial）后回填卡组/额外卡面（快进/正常路径均适用）
                    awaitDispatchDrain();
                    applyReplayDeckCodes();
                }
                fedSincePump = paceAfter(msg.type, fedSincePump);
                if (fedSincePump < 0) break;
            }
        } catch (Throwable t) {
            CrashHandler.getInstance().report("replay-pump step=" + currentStep, t);
            if (lastErrorMessage == null) lastErrorMessage = "回放投喂异常：" + t;
            Log.e(TAG, "Replay pump error", t);
        } finally {
            finishSession(gen);
        }
    }

    /**
     * 单条消息投喂后的节奏处理（快进计数 / 落点 / 单步自动暂停 / 可见步等待管线排空）。
     *
     * @return 投喂计数（供批量排空），负值表示应终止投喂循环
     */
    private long paceAfter(int msgType, long fedSincePump) throws InterruptedException {
        boolean visible = ReplayMessageSlicer.isVisibleStep(msgType);
        if (isSkipping && skipStep == 0 && !landAtStart) {
            // 跳回合快进：可见步照常计步（步号与正常播放一致），但不等动画、不停顿
            if (visible) currentStep++;
            if (msgType == MSG_NEW_TURN && skipTurn > 0 && --skipTurn == 0) {
                endInstantSkip();
                isSkipping = false;
                notifyHint("快进结束，从当前回合开始正常播放");
            }
            return pumpGuard(fedSincePump);
        }
        if (isSkipping) {
            // undo/restart 快进重排：不计数，到达目标可见步（或初始场落点）即停下还原
            if (skipStep > 0) {
                if (visible && --skipStep == 0) landInstantSkip();
            } else if (landAtStart && msgType == MSG_START) {
                landInstantSkip();      // 重投 MSG_START 重建初始场后即停（回到 0 步）
            }
            return pumpGuard(fedSincePump);
        }
        if (!visible) return fedSincePump;   // 非可见步瞬流过（无卡片移动的消息不再停顿）
        currentStep++;
        if (stepsRemaining > 0) {
            stepsRemaining--;
            if (stepsRemaining == 0) {
                isPaused = true;
                setState(State.PAUSED);
            }
        }
        // 等实况管线（动画闸门 + pendingMsgs）消化完本步再投喂下一条：
        // 动画完成信号来自实况闸门，与观战同一节奏来源
        waitForPipelineIdle();
        if (stepsRemaining == 0 && !isPaused && !isRestarting) {
            Thread.sleep(field.animationSpeed > 1f ? 40L : 80L);
        }
        return fedSincePump;
    }

    /** 投喂过多时强制排空实况队列（防主线程队列无限堆积）；返回 -1 表示投喂线程已被中止 */
    private long pumpGuard(long fedSincePump) {
        if (!isRunning) return -1;
        if (fedSincePump >= 512) {
            engine.drainReplayQueueNow();
            return 0;
        }
        return fedSincePump;
    }

    private void handleWin(byte[] body) {
        int winner = body != null && body.length > 0 ? (body[0] & 0xFF) : 2;
        int reason = body != null && body.length > 1 ? (body[1] & 0xFF) : 0;
        replayWinSeen = true;
        if (isSkipping) return;   // 快进重排途中遇到 WIN：不弹结果
        soundManager.stopBGM();
        mainHandler.post(() -> {
            if (listener != null) listener.onReplayFinished(winner, reason);
        });
    }

    // === 播控：重排 / 视角 ===

    private void performRestart() {
        isRestarting = false;
        isSkipping = true;
        drainAndSettle();
        source.rewind();
        ReplayCodeMapper.beginSession();
        replayWinSeen = false;
        currentStep = 0;
        skipStep = Math.max(0, restartTargetStep);
        restartFromStep = skipStep;
        restartTargetStep = 0;
        landAtStart = skipStep == 0;
        boolean engineDriven = source.engineDriven();
        if (engineDriven) {
            // 重跑无 MSG_START：初始场与卡面就地重建（与 load 路径同口径）
            buildInitialField();
            applyReplayDeckCodes();
            landAtStart = false;
        }
        if (engineDriven && skipStep == 0) {
            // 回到 0 步：初始场即落点，无需快进
            endInstantSkip();
            isSkipping = false;
            pause();
            return;
        }
        beginInstantSkip();
    }

    private void performSwapField() {
        field.swapField();
        field.refreshAllCards();
    }

    // === 实况管线会话标志 ===

    /** 开始回放会话：置实况管线回放标志；录制者视角恒等映射（duelIsFirst=true），双方昵称写 playerInfos */
    private void startSession() {
        engine.replayMode = true;
        engine.duelIsFirst = true;
        engine.inDuel = false;
        if (replayData != null) {
            for (int p = 0; p < 2 && p < replayData.playerNames.size()
                    && p < engine.playerInfos.length; p++) {
                engine.playerInfos[p].name = replayData.playerNames.get(p);
            }
        }
    }

    /** 退出/停止回放：还原实况管线全部回放标志并复位播控状态（MSG_START 重放前也调用以清旧游标） */
    private void clearReplayFlags() {
        engine.replayMode = false;
        engine.replaySkip = false;
        field.instantPlace = false;
        engine.duelIsFirst = true;
        soundManager.setEffectsSuppressed(false);
        isSkipping = false;
        skipStep = 0;
        skipTurn = 0;
        landAtStart = false;
        stepsRemaining = 0;
    }

    /** 快进三标志开启：实况侧抑制动画/特效/音效，消息处理即时落位 */
    private void beginInstantSkip() {
        engine.replaySkip = true;
        field.instantPlace = true;
        soundManager.setEffectsSuppressed(true);
    }

    /** 快进三标志还原：恢复正常播放节奏（引擎侧动画持闸残留一并清零） */
    private void endInstantSkip() {
        engine.replaySkip = false;
        field.instantPlace = false;
        soundManager.setEffectsSuppressed(false);
        engine.animHoldUntilMs = 0;
    }

    /** 快进落点：排空、还原标志、暂停并回填卡组卡面 */
    private void landInstantSkip() {
        drainAndSettle();
        endInstantSkip();
        isSkipping = false;
        currentStep = restartFromStep;
        applyReplayDeckCodes();
        pause();
    }

    /** 投喂线程调用：强制排空实况队列并等待消化完毕（落点/终止前保证已投喂消息全部渲染） */
    private void drainAndSettle() {
        engine.drainReplayQueueNow();
        waitForPendingDrain();
    }

    private void finishSession(long gen) {
        // 旧会话被作废（退出/重新点播）：stop() 已清标志并关源，共享状态归新会话所有，
        // 此处直接静默退出，不得再派发状态/回调（否则首播即弹「录像已结束」）
        if (sessionGen != gen) return;
        if (source != null && source.getLastError() != null && lastErrorMessage == null) {
            lastErrorMessage = source.getLastError();
        }
        clearReplayFlags();
        closeSourceQuietly();
        isRunning = false;
        setState(lastErrorMessage != null ? State.ERROR : State.FINISHED);
        final boolean winShown = replayWinSeen;
        mainHandler.post(() -> {
            soundManager.stopBGM();
            // 自然播毕（未经 MSG_WIN 判定胜负）：winner=-1 表示无结果，UI 不显示胜负文字
            if (!winShown && listener != null) listener.onReplayFinished(-1, 0);
        });
        writeDiagnostics();
    }

    private void closeSourceQuietly() {
        try {
            if (source != null) source.close();
        } catch (Throwable t) {
            Log.w(TAG, "close source failed", t);
        }
    }

    private void fail(String message) {
        lastErrorMessage = message;
        Log.w(TAG, "replay load failed: " + message);
        clearReplayFlags();
        isRunning = false;
        setState(State.ERROR);
        notifyHint(message);
        writeDiagnostics();
    }

    // === 投喂与等待 ===

    /** 主线程投递消息进实况管线（队列/动画闸门均为主线程态，必须 post） */
    private void feedMessage(int msgType, byte[] body) {
        engine.mainHandler.post(() ->
                engine.enqueueGameMsg(msgType, ByteBuffer.wrap(body).order(ByteOrder.LITTLE_ENDIAN)));
    }

    /** 等待实况管线空闲：pendingMsgs 排空且动画闸门开启（动画/特效全部播完）；重排抢占时提前返回 */
    private void waitForPipelineIdle() {
        long deadline = System.currentTimeMillis() + 8000L;
        while (isRunning && !isRestarting) {
            if (!engine.hasPendingMsgs() && !engine.isAnyAnimationBusy()) return;
            if (System.currentTimeMillis() > deadline) return;   // 动画兜底超时，防卡死
            try {
                Thread.sleep(16);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    /** 仅等待队列消化（不要求动画空闲）：快进落点/终止收尾用 */
    private void waitForPendingDrain() {
        long deadline = System.currentTimeMillis() + 3000L;
        while (isRunning && engine.hasPendingMsgs() && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    /** 切片 UPDATE_DATA 前等实况管线消化完已投喂消息（有限等待，保证区域卡数与实况一致） */
    void awaitDispatchDrain() {
        long deadline = System.currentTimeMillis() + 2000L;
        while (isRunning && !engine.isMsgQueueIdle() && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    /** 投喂线程是否仍在跑（ReplaySource 的取消息循环据此及时退出） */
    boolean isPumpAlive() {
        return isRunning;
    }

    /**
     * 某区域当前卡片数（UPDATE_DATA 的 query 块数），口径同实况
     * {@code CommandDataParser.parseUpdateData}：固定槽位区域（怪兽区/魔法区）整列计数，
     * 动态列表只计实际存在的卡。
     */
    private int zoneBlockCount(int player, int location) {
        List<GameField.ClientCard> list = field.players[engine.localPlayer(player)]
                .getLocationList(location);
        if (list == null) return 0;
        if (location == 0x04 || location == 0x08) return list.size();
        int count = 0;
        for (GameField.ClientCard card : list) {
            if (card != null) count++;
        }
        return count;
    }

    @Override
    public int count(int player, int location) {
        return zoneBlockCount(player, location);
    }

    @Override
    public int blockCount(int player, int location) {
        return zoneBlockCount(player, location);
    }

    // === 卡面回填 ===

    /**
     * 录像头部卡组/额外卡码回填：MSG_START 与 {@code field.initial} 只按数量建背面卡
     * （code=0），此处把已归一的卡码按索引同序写进 ClientCard，使双方卡组/额外可直接点开
     * 查看正面（修复手卡/卡组 unknown）；初始场建立后及每次快进落点后均需调用（重排会重建卡对象）
     */
    private void applyReplayDeckCodes() {
        if (replayData == null) return;
        writeZoneCodes(field.players[0].deck, displayMain0);
        writeZoneCodes(field.players[0].extra, displayExtra0);
        writeZoneCodes(field.players[1].deck, displayMain1);
        writeZoneCodes(field.players[1].extra, displayExtra1);
        field.refreshAllCards();
    }

    private void writeZoneCodes(List<GameField.ClientCard> list, List<Integer> codes) {
        if (list == null || codes == null) return;
        int n = Math.min(list.size(), codes.size());
        for (int i = 0; i < n; i++) {
            GameField.ClientCard card = list.get(i);
            if (card == null) continue;
            card.code = codes.get(i);
        }
    }

    // === UI 通知 ===

    private void setState(State newState) {
        if (state == newState && newState != State.PLAYING) return;
        state = newState;
        CrashHandler.getInstance().setScene("回放-" + newState);
        mainHandler.post(() -> {
            if (listener != null) listener.onReplayStateChanged(newState);
        });
    }

    private void notifyHint(String hint) {
        mainHandler.post(() -> {
            if (listener != null) listener.onReplayHintMessage(hint);
        });
    }

    private void notifyPlayerInfo(int player) {
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onPlayerInfoUpdated(player);
        });
    }

    private String buildReplayInfo(int startTurn) {
        StringBuilder sb = new StringBuilder("录像回放\n");
        for (int i = 0; i < replayData.playerNames.size(); i++) {
            if (i > 0) sb.append(" vs ");
            sb.append(replayData.playerNames.get(i));
        }
        sb.append("\nLP: ").append(replayData.params.startLp)
                .append(" | 手牌: ").append(replayData.params.startHand)
                .append(" | 抽卡: ").append(replayData.params.drawCount);
        if (replayData.isTag) sb.append(" [双打]");
        if (replayData.isSingleMode) sb.append(" [残局]");
        if (startTurn > 1) sb.append(" | 从第").append(startTurn).append("回合开始");
        return sb.toString();
    }

    /** 本次播放的卡码/异常诊断落盘（ygocore/log），便于定位仍显示 unknown 的卡 */
    private void writeDiagnostics() {
        try {
            String error = lastErrorMessage;
            if (error == null && !ReplayCodeMapper.hasFindings()) return;
            StringBuilder sb = new StringBuilder();
            sb.append("== 回放诊断 ==\n源: ")
                    .append(source == null ? "未建立" : (source.engineDriven() ? "引擎重跑(旧格式)" : "消息流"))
                    .append("\n步数=").append(currentStep)
                    .append("\n错误=").append(error == null ? "无" : error).append('\n')
                    .append(ReplayCodeMapper.buildReport());
            if (replayData != null) {
                sb.append("录像: seed=").append(replayData.header.base.seed)
                        .append(" flag=0x").append(Integer.toHexString(replayData.header.base.flag))
                        .append(" decks=").append(replayData.decks.size()).append('\n');
            }
            CrashHandler.getInstance().writeReport("replay", sb.toString());
        } catch (Throwable t) {
            Log.w(TAG, "writeDiagnostics failed", t);
        }
    }
}
