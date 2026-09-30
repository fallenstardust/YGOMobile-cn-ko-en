package cn.garymb.ygomobile.game;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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

    /** 消息号（ocgcore common.h）：本类需要特判的几条 */
    private static final int MSG_RETRY = 1;
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

    final GameEngine engine;
    private final SoundManager soundManager;
    private final GameField field;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    // 区域计数与卡面回填协作类（同包，持本实例反向引用）
    private final ReplayZoneCounter zoneCounter = new ReplayZoneCounter(this);

    private Listener listener;
    private volatile State state = State.IDLE;

    private ReplaySource source;
    ReplayReader.ReplayData replayData;
    /** 当前会话录像文件路径：旧格式重跑成功播毕后据此转码固化写回（见 {@link ReplayTranscoder}） */
    private volatile String replayFilePath;
    /** 显示用卡组/额外卡码（已按本机卡表归一）；引擎重跑仍用 replayData.decks 的原码 */
    List<Integer> displayMain0, displayExtra0, displayMain1, displayExtra1;

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
    /** 本会话消息流是否耗尽（next()==null 且无错 = 自然播毕，finishSession 判定播完态用） */
    private volatile boolean endOfStream;
    /** V2 逐帧流预计算的可见步总数（进度/结束弹窗展示用）；旧格式重跑与 V1 原始流无法预知 = -1 */
    private volatile int totalSteps = -1;
    /** 本会话被 isNoFeed 跳过不投喂的 MSG_RETRY 条数（结束弹窗 debug 信息） */
    private volatile int skippedRetryCount;
    /** 本次会话是否成功播完（无错且 MSG_WIN 或流尽），由 finishSession 落定 */
    private volatile boolean playbackCompleted;
    /** 未出错但提前结束时的原因说明（快进重排中止等）；null=不适用 */
    private volatile String earlyEndNote;
    /** 快进剩余待跳过的可见步 / 待跳过的回合数（落点由 skipStep==0 或 skipTurn==0 判定） */
    private int skipStep;
    private int skipTurn;
    private volatile int restartTargetStep;
    private int restartFromStep;
    /** 本次会话录像文件 V2 尾段内的 0xF1 聊天伪帧条数（加载时统计，随开场信息展示供录制验证）；-1=非逐帧流无从统计 */
    private int chatFrameCount = -1;
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

    /** 录像可见步总数：V2 逐帧流点播前预计算；旧格式重跑/V1 原始流返回 -1（未知） */
    public int getTotalSteps() {
        return totalSteps;
    }

    /** 本会话跳过不投喂的 MSG_RETRY 步条数（结束弹窗 debug 信息） */
    public int getSkippedRetryCount() {
        return skippedRetryCount;
    }

    /** 本次会话是否播完（无错且到达 MSG_WIN 结算或消息流完整耗尽），由 finishSession 落定 */
    public boolean isPlaybackCompleted() {
        return playbackCompleted;
    }

    /** 未出错但提前结束（未播完）时的原因说明；null=不适用 */
    public String getEarlyEndNote() {
        return earlyEndNote;
    }

    /** 是否有正在进行的回放会话（UI 判定按钮可用性与退出确认） */
    public boolean hasActiveSession() {
        return isRunning;
    }

    public void pause() {
        isPaused = true;
        setState(State.PAUSED);
    }

    /** 当前是否处于暂停态（旋转重建后恢复录像控制条按钮互斥显隐用） */
    public boolean isPaused() {
        return isPaused;
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
        replayFilePath = replayPath;
        final long gen = sessionGen;      // 本次会话代数（stop 刚递增过）
        setState(State.LOADING);
        lastErrorMessage = null;
        currentStep = 0;
        replayWinSeen = false;
        endOfStream = false;
        totalSteps = -1;
        skippedRetryCount = 0;
        playbackCompleted = false;
        earlyEndNote = null;
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
            // 含 MSG 流的录像（V2 逐帧 / V1 原始流）直接消费消息流；旧格式重跑引擎复现消息流（ReplaySource 内部自适应）
            source = (replayData.msgFrames != null || replayData.msgBuffer != null)
                    ? new MsgStreamReplaySource(this)
                    : new EngineReplaySource(this);
            if (!source.open()) {
                fail(source.getLastError() == null ? "录像数据源初始化失败" : source.getLastError());
                return;
            }
            if (sessionGen != gen) return;    // 同上：开源自检期间会话已作废
            totalSteps = computeTotalSteps();
            prepareDisplayDecks();
            chatFrameCount = countChatFrames();
            startSession();
            if (source.engineDriven()) {
                // 旧格式重跑不产 MSG_START（gframe 由 dField.Initial 建场），此处自行建初始场
                buildInitialField();
                zoneCounter.applyReplayDeckCodes();
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

    /**
     * 预计算可见步总数（结束弹窗 debug 信息/进度展示）：仅 V2 逐帧流可算——每帧首字节即
     * 消息号，按 {@link ReplayMessageSlicer#isVisibleStep} 计数与投喂侧 currentStep 同口径；
     * V1 原始拼接流的 UPDATE_DATA 块数依赖实况场况、旧格式重跑消息尚未产生，均无法预算，
     * 返回 -1（UI 标注「未知」）。
     */
    private int computeTotalSteps() {
        ReplayReader.ReplayData d = replayData;
        if (d == null || d.msgFrames == null) return -1;
        int n = 0;
        for (byte[] f : d.msgFrames) {
            if (f != null && f.length > 0 && ReplayMessageSlicer.isVisibleStep(f[0] & 0xFF)) n++;
        }
        return n;
    }

    /** 本会话消息流内 0xF1 聊天伪帧条数（V2 逐帧流加载时统计）：开场信息展示用，
     *  使用户无需逐条比对即可确认聊天是否真的录进了录像；V1 原始流/引擎重跑源返回 -1 */
    private int countChatFrames() {
        if (replayData == null || replayData.msgFrames == null) return -1;
        int n = 0;
        for (byte[] f : replayData.msgFrames) {
            if (f != null && f.length >= 2 && (f[0] & 0xFF) == GameEngine.REPLAY_CHAT_FRAME) n++;
        }
        return n;
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
        // 初始场按当前视角建立：视角切换后重跑/重放时引擎 P0（录制者）落 localPlayer(0)
        // 容器，与后续消息的 localPlayer 映射一致（C++ replay_mode.cpp::StartDuel
        // dField.Initial(LocalPlayer(i)) 同口径）
        int l0 = engine.localPlayer(0);
        int l1 = engine.localPlayer(1);
        field.players[l0].lp = startLp;
        field.players[l1].lp = startLp;
        field.dInfo.startLp = startLp;
        field.dInfo.lp[l0] = startLp;
        field.dInfo.lp[l1] = startLp;
        if (!replayData.isSingleMode) {
            field.initial(l0, sizeOf(displayMain0), sizeOf(displayExtra0), 0);
            field.initial(l1, sizeOf(displayMain1), sizeOf(displayExtra1), 0);
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
                    if (err != null) {
                        if (lastErrorMessage == null) lastErrorMessage = err;
                    } else {
                        endOfStream = true;   // 消息流完整耗尽：自然播毕（finishSession 判定播完态）
                    }
                    break;
                }
                if (msg.type == GameEngine.REPLAY_CHAT_FRAME) {
                    // 聊天/观战发言伪帧：非引擎消息，不计步、不参与节奏、不投喂管线，独立派发显示
                    dispatchReplayChat(msg.body);
                    continue;
                }
                if (msg.type == MSG_RETRY) {
                    // isNoFeed 跳过不投喂（应答已固化在流里），仅计数供结束弹窗排查失步来源
                    skippedRetryCount++;
                    continue;
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
                    zoneCounter.applyReplayDeckCodes();
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
     * 0xF1 聊天帧派发（帧体 = playerType(1)+UTF-8 文本）：快进重排期间丢弃（rewind 已清屏，
     * 落点后聊天随流重新流入）；否则经实况唯一聊天入口 engine.listener.onChatReceived 按
     * 时间线重现——分侧聊天行/系统弹幕/表情气泡与实况完全同一渲染路径（录制侧在回放模式
     * 自动丢弃，不会自录循环）。
     */
    private void dispatchReplayChat(byte[] body) {
        if (isSkipping || body == null || body.length < 2) return;
        final int playerType = body[0] & 0xFF;
        final String text = new String(body, 1, body.length - 1,
                java.nio.charset.StandardCharsets.UTF_8);
        if (text.isEmpty()) return;
        Log.i(TAG, "replay chat dispatch: type=" + playerType + " len=" + text.length()
                + (isSkipping ? " (skipped)" : ""));
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onChatReceived(playerType, text);
        });
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
        // 收尾动画屏障①：MSG_WIN 常与最后一段 LP 扣减/结算消息同批到达，先等实况管线
        // 消化完已投喂消息并把卡片移动/LP 浮字/血条归零动画全部播完，再派发胜负文字
        //（修复：弹窗与 LP 减少/you win 文字动画同时进行）
        awaitDispatchDrain();
        waitForAnimationsSettled(6000L);
        if (!isRunning) return;   // 等待期间用户退出：不再派发结果
        final CountDownLatch shown = new CountDownLatch(1);
        mainHandler.post(() -> {
            try {
                if (listener != null) listener.onReplayFinished(winner, reason);
            } finally {
                shown.countDown();
            }
        });
        // 等 UI 实际执行完 showWinText（YOU WIN/LOSE 110 帧文字已入队）再返回，
        // finishSession 的屏障②才能确实等到胜负文字播完
        try {
            shown.await(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // === 播控：重排 / 视角 ===

    private void performRestart() {
        isRestarting = false;
        isSkipping = true;
        drainAndSettle();
        source.rewind();
        // 聊天/弹幕帧随消息流回卷重新流入：先清空现有显示，防重复行/重复弹幕
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onReplayChatReset();
        });
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
            zoneCounter.applyReplayDeckCodes();
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
        // 视角互换，对齐 C++ client_field.cpp::ClientField::ReplaySwap 全量字段：
        // ① duelIsFirst（dInfo.isFirst）翻转——后续投喂消息的 localPlayer 映射必须随视角
        //    翻转，否则消息仍写进对调前的容器，即「双方场卡/手卡混排显示」的根因；
        // ② field.swapField() 对调双方各区列表并逐卡重算 controler（含超量素材/连锁/
        //    disabledField/extraPCount，C++ 同名步骤）；
        // ③ 昵称/LP 对调（C++ hostname↔clientname、lp/strLP swap）+ currentPlayer 翻转，
        //    血条/卡数/回合高亮按新视角重取。
        engine.duelIsFirst = !engine.duelIsFirst;
        field.swapField();
        GameEngine.PlayerInfo a = engine.playerInfos[0];
        GameEngine.PlayerInfo b = engine.playerInfos[1];
        String tmpName = a.name;
        a.name = b.name;
        b.name = tmpName;
        // tag 队友名随视角一并左右对调（对齐 C++ ReplaySwap 的 hostname_tag↔clientname_tag swap）
        String tmpNameTag = a.nameTag;
        a.nameTag = b.nameTag;
        b.nameTag = tmpNameTag;
        int tmpLp = a.lp;
        a.lp = b.lp;
        b.lp = tmpLp;
        int tmpStart = a.startLp;
        a.startLp = b.startLp;
        b.startLp = tmpStart;
        field.currentPlayer = 1 - field.currentPlayer;
        // dInfo.lp 是视角索引的血条显示值：容器已对调，直接按 players[].lp 重新对齐
        //（视角切换仅在暂停态执行，LP 动画必空闲，赋值安全）
        field.dInfo.lp[0] = field.players[0].lp;
        field.dInfo.lp[1] = field.players[1].lp;
        field.refreshAllCards();
        notifyPlayerInfo(0);      // 名字/LP 条/卡数按新视角重取
        notifyPlayerInfo(1);
        // 回合方高亮（LPBarFrame 彩色/灰色与名字色）随视角翻转重刷
        engine.mainHandler.post(() -> {
            if (engine.listener != null) {
                engine.listener.onTurnStarted(field.currentPlayer);
                // 切视角同步左右对调双方聊天内容：与昵称/LP 对调同一批次，
                // 使 player1 的消息随其所在侧一并搬到对调后的对应血条下方
                engine.listener.onViewpointSwapped();
            }
        });
    }

    // === 实况管线会话标志 ===

    /** 开始回放会话：置实况管线回放标志；录制者视角恒等映射（duelIsFirst=true），双方昵称写 playerInfos */
    private void startSession() {
        engine.replayMode = true;
        engine.duelIsFirst = true;
        engine.inDuel = false;
        if (replayData != null) {
            // 对齐 replay_mode.cpp 名字装载：非 tag 时 names[0/1] 为对戦双方；
            // tag 时 names[0..3] = hostname/hostname_tag/clientname/clientname_tag
            //（座位 0/1 同队、2/3 同队），录制者视角左=座位 0 队、右=座位 2 队；
            // 并同步 isTag/tag_player 复位（对齐 replay_mode.cpp L66-67/L260-261）
            field.isTag = replayData.isTag;
            engine.tagPlayer[0] = false;
            engine.tagPlayer[1] = false;
            engine.playerInfos[0].nameTag = "";
            engine.playerInfos[1].nameTag = "";
            List<String> names = replayData.playerNames;
            if (replayData.isTag && names.size() >= 4) {
                engine.playerInfos[0].name = names.get(0);
                engine.playerInfos[0].nameTag = names.get(1);
                engine.playerInfos[1].name = names.get(2);
                engine.playerInfos[1].nameTag = names.get(3);
            } else {
                for (int p = 0; p < 2 && p < names.size()
                        && p < engine.playerInfos.length; p++) {
                    engine.playerInfos[p].name = names.get(p);
                }
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
        zoneCounter.applyReplayDeckCodes();
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
        // 收尾动画屏障②：等胜负文字（YOU WIN/LOSE，110 帧）与全部残余卡片/LP 动画播完
        // 再派发终态——「录像播放结束」dialog 由 onReplayStateChanged(FINISHED) 弹出，
        // 必然落在所有动画之后（isAnyAnimationBusy 含 SpecEffectOverlay 队列/播放中）
        if (!isSkipping) waitForAnimationsSettled(8000L);
        if (sessionGen != gen) return;   // 等待期间被新会话作废：静默退出，不再派发终态
        // 转码固化素材：成功播完（无错）的旧格式重跑已在源内积累完整消息帧（原码），
        // 关源前抓取帧列表与响应段快照；条件不满足（YRP1/残局/tag/出错/无帧）则保持 null
        List<byte[]> transcodeFrames = null;
        byte[] responseSnapshot = null;
        if (lastErrorMessage == null && source != null && source.engineDriven()) {
            ReplayReader.ReplayData d = replayData;
            List<byte[]> captured = source.capturedEngineFrames();
            if (d != null && d.header.base.id == ReplayReader.REPLAY_ID_YRP2
                    && !d.isSingleMode && !d.isTag && d.replayBuffer != null
                    && captured != null && !captured.isEmpty()) {
                transcodeFrames = new ArrayList<>(captured);
                ByteBuffer rb = d.replayBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN);
                responseSnapshot = new byte[rb.remaining()];
                rb.get(responseSnapshot);
            }
        }
        // 落定播完态（须在 clearReplayFlags 复位 isSkipping 之前判定）：无错且 MSG_WIN 结算
        // 或消息流完整耗尽 = 播完；否则无错的提前结束（快进重排中止、流未读尽）附原因说明，
        // 由 ReplayModeDialog.showReplayEndDialog 在 FINISHED 分支展示
        playbackCompleted = lastErrorMessage == null && (replayWinSeen || endOfStream);
        if (!playbackCompleted && lastErrorMessage == null) {
            earlyEndNote = isSkipping ? "快进重排中止" : "消息流未播尽";
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
        if (transcodeFrames != null) {
            ReplayTranscoder.transcodeToMsgStream(TAG, MSG_START,
                    replayFilePath, replayData, responseSnapshot, transcodeFrames);
        }
    }

    // === 转码固化（实现已平移至 ReplayTranscoder） ===

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

    /**
     * 收尾专用等待：实况队列排空且全部动画（卡片移动/LP 浮字与血条过渡/胜负文字特效）
     * 播完才返回。不依赖 isRunning（finishSession 里可能已被其他路径置位），超时兑底防卡死；
     * pump 线程被 interrupt（用户退出）时立即返回。
     */
    private void waitForAnimationsSettled(long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (!engine.hasPendingMsgs() && !engine.isAnyAnimationBusy()) return;
            try {
                Thread.sleep(16);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
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

    @Override
    public int count(int player, int location) {
        return zoneCounter.zoneBlockCount(player, location);
    }

    @Override
    public int blockCount(int player, int location) {
        return zoneCounter.zoneBlockCount(player, location);
    }

    /** 录像头部卡组/额外卡码取数（实现在 ReplayZoneCounter）：视角互换后自动跟随，非卡组区域返回 null */
    public List<Integer> getReplayZoneCodes(int viewPlayer, int location) {
        return zoneCounter.getReplayZoneCodes(viewPlayer, location);
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
        // 聊天录制验证：开场信息直接标注文件内聊天伪帧条数，0 条=录制侧未落盘，
        // -1=非逐帧流无法统计（旧格式重跑/ V1）
        if (chatFrameCount >= 0) sb.append(" | 聊天").append(chatFrameCount).append("条");
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
