package cn.garymb.ygomobile.game.replay;
import cn.garymb.ygomobile.game.engine.GameEngine;
import cn.garymb.ygomobile.game.gamefield.GameField;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.audio.SoundManager;
import cn.garymb.ygomobile.render.card.CardDetailPanel;
import cn.garymb.ygomobile.utils.CrashHandler;
import ocgcore.DataManager;

/** 录像回放播放器：加载录像→取消息→卡码归一→按节奏投喂 GameEngine 实况管线并做播控，场地/动画/音效/卡详等全部与联机观战同源渲染，本类不再自建渲染分支。 */
public final class ReplayPlayer implements ReplayMessageSlicer.ZoneBlocks,
        ReplayCodeMapper.BlockCountProvider {

    private static final String TAG = "ReplayPlayer";

    /** 消息号（ocgcore common.h）：本类需要特判的几条 */
    private static final int MSG_RETRY = 1;
    static final int MSG_START = 4;
    static final int MSG_NEW_TURN = 40;

    public enum State { IDLE, LOADING, PLAYING, PAUSED, FINISHED, ERROR }

    /** 回放对外回调：仅播放状态/顶部提示/播放结束三件事，其余场地/玩家/回合刷新由实况管线经 EngineCallbackDelegate 派发。 */
    public interface Listener {
        void onReplayStateChanged(State state);

        void onReplayHintMessage(String hint);

        /** MSG_WIN 结算（winner=2 平局；reason 见 sysString 1300 表） */
        void onReplayFinished(int winner, int reason);
    }

    final GameEngine engine;
    final SoundManager soundManager;
    final GameField field;
    final Handler mainHandler = new Handler(Looper.getMainLooper());
    /** 区域计数与卡面回填协作类（同包，持本实例反向引用） */
    private final ReplayZoneCounter zoneCounter = new ReplayZoneCounter(this);
    /** 投喂节奏/等待/胜负协作类（同包，持本实例反向引用） */
    private final ReplayPacer pacer = new ReplayPacer(this);

    Listener listener;
    volatile State state = State.IDLE;

    private ReplaySource source;
    ReplayReader.ReplayData replayData;
    /** 当前会话录像文件路径：旧格式重跑成功播毕后据此转码固化写回（见 {@link ReplayTranscoder}） */
    private volatile String replayFilePath;
    /** 显示用卡组/额外卡码（已按本机卡表归一）；引擎重跑仍用 replayData.decks 的原码 */
    List<Integer> displayMain0, displayExtra0, displayMain1, displayExtra1;

    private Thread loadThread;
    private Thread pumpThread;
    volatile boolean isRunning;
    volatile boolean isPaused;
    volatile boolean isRestarting;
    private volatile boolean isSwapping;
    /** 快进重排中（跳回合/undo/restart）：即时落位+抑制动画音效，直到落点还原 */
    volatile boolean isSkipping;
    volatile boolean replayWinSeen;
    volatile int stepsRemaining;
    volatile int currentStep;
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
    /** 快进剩余待跳过的可见步/回合数（落点由 skipStep==0 或 skipTurn==0 判定） */
    int skipStep;
    int skipTurn;
    private volatile int restartTargetStep;
    private int restartFromStep;
    /** 本次会话录像文件 V2 尾段内的 0xF1 聊天伪帧条数（加载时统计供录制验证）；-1=非逐帧流无从统计 */
    private int chatFrameCount = -1;
    /** 聊天时间线（逐帧流含 0xF1 帧时加载构建）：引擎重跑流无 0xF1、快进丢弃内联帧，均靠落点后按步号补齐 */
    private final ReplayChatTimeline chatTimeline = new ReplayChatTimeline();
    /** undo/restart 目标步为 0：消费并重投 MSG_START（重建初始场）后即停 */
    boolean landAtStart;
    volatile String lastErrorMessage;
    /** 会话代数：stop()/loadAndPlay 每次递增，作废旧 load/pump 线程的收尾派发（防首播即弹「录像已结束」）。 */
    private volatile long sessionGen;

    /** 最近一个可见步的消息类型，供「上一步」判定是否可逆向倒放；-1=未播过可见步 */
    volatile int lastVisibleMsgType = -1;
    /** 本次「上一步」落点后是否施加逆向补间：undo 判定置位、performRestart 冻结并抓快照、落点消费复位；从头重放/普通快进恒 false。 */
    private volatile boolean pendingReverse = false;
    /** 步骤 N 各显示卡的渲染位置快照：投喂线程重算前抓取、落点投主线程消费，仅服务紧邻一次倒放，不落全量历史栈。 */
    private final List<ReplayPosSnap> displaySnapN = new ArrayList<>();

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

    /** 上一步：回到上一个可见步（重放到该步后暂停）。
     *  若离开的那一步（{@code lastVisibleMsgType}）为可倒放位移步，则置 {@code pendingReverse}：
     *  重算 N-1 权威态期间冻结渲染，落点后对位移卡做逆向补间；复杂步维持瞬间跳变。 */
    public void undo() {
        if (skipStep > 0 || currentStep == 0 || !isRunning) return;
        pendingReverse = lastVisibleMsgType != -1
                && ReplayMessageSlicer.isReverseAnimatable(lastVisibleMsgType);
        Log.i(TAG, "replay undo: step=" + currentStep
                + " lastVisibleType=" + lastVisibleMsgType
                + " reverse=" + pendingReverse);
        restartTargetStep = currentStep - 1;
        isRestarting = true;
        resume();
    }

    /** 从头重放 */
    public void restart() {
        if (!isRunning) return;
        pendingReverse = false;
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

    /** 后台线程解析录像并起投喂线程；startTurn &gt; 1 时先快进到该回合起点再正常播放（对齐 gframe「起始回合」）。 */
    public void loadAndPlay(String replayPath, int startTurn) {
        // 留住旧会话线程：新建的 load 线程先限时等它们退出再开源建局，避免旧 load 仍在
        // open()/startDuel()（可能卡在 ensureEngineReady 十几秒）时与新会话并发触碰
        // 进程级 ocgcore 单例（外部重开同/不同录像时 native 崩溃的直接来源）
        final Thread oldLoad = loadThread;
        final Thread oldPump = pumpThread;
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
        lastVisibleMsgType = -1;
        pendingReverse = false;
        displaySnapN.clear();
        ReplayCodeMapper.beginSession();
        loadThread = new Thread(() -> {
            joinQuietly(oldPump, 5000L);
            joinQuietly(oldLoad, 5000L);
            load(replayPath, startTurn, gen);
        }, "ReplayLoad");
        loadThread.setDaemon(true);
        CrashHandler.getInstance().hookThread(loadThread, "回放-加载");
        loadThread.start();
    }

    /** 限时等待旧会话线程退出（本方法在 worker 线程调用，不可在主线程用）；超时/中断都继续 */
    private static void joinQuietly(Thread t, long timeoutMs) {
        if (t == null || t == Thread.currentThread() || !t.isAlive()) return;
        try {
            t.join(timeoutMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (t.isAlive()) Log.w(TAG, "previous replay thread still alive after " + timeoutMs + "ms");
    }

    private void load(String replayPath, int startTurn, long gen) {
        try {
            // 等卡数据就绪：外部打开 yrp 时 YGOProActivity.loadData 的全量加载与回放线程并发
            // 读卡表会错映射卡码（引擎重跑失步→“卡住”）/并发写坏卡表（越界崩溃）
            if (!DataManager.get().awaitLoaded(20000L)) {
                Log.w(TAG, "card data not ready within 20s, replay may mis-map card codes");
            }
            if (sessionGen != gen) return;    // 等待期间已被退出/重新点播作废：静默退出
            replayData = ReplayReader.loadReplay(replayPath);
            if (sessionGen != gen) return;    // 加载期间已被退出/重新点播作废：静默退出
            if (replayData == null) {
                fail("无法加载录像文件");
                return;
            }
            // 回放源选择：含 MSG 流（V2/V1）恒直接播流（脱离 ocgcore 可播）；仅无流旧格式或 REPLAY_MSG_GUEST_VIEW 遮蔽文件优先引擎重跑重现双方手卡，引擎不可用则退回播流（preserveMaskedHandCode 保正面）。
            // 全程用局部引用 s，仅在会话仍有效时才发布到 source 字段：避免 stop() 跨线程关闭
            // 本线程尚在 open() 的源，也避免会话作废时泄漏已创建的 pduel
            boolean sourceOpened = false;
            ReplaySource s;
            if (replayData.msgFrames != null || replayData.msgBuffer != null) {
                if (replayData.hasFlag(ReplayReader.REPLAY_MSG_GUEST_VIEW)) {
                    ReplaySource engineSource = new EngineReplaySource(this);
                    if (engineSource.open()) {
                        s = engineSource;
                        sourceOpened = true;    // 已开过源，不得二次 open（startDuel 重复重建会泄漏 pduel）
                    } else {
                        closeSourceQuietly(engineSource);
                        s = new MsgStreamReplaySource(this);
                    }
                } else {
                    s = new MsgStreamReplaySource(this);
                }
            } else {
                s = new EngineReplaySource(this);
            }
            if (!sourceOpened && !s.open()) {
                closeSourceQuietly(s);  // 开源失败也可能已创建 pduel（startDuel 中途失败），释放原生资源
                fail(s.getLastError() == null ? "录像数据源初始化失败" : s.getLastError());
                return;
            }
            if (sessionGen != gen) {    // 同上：开源自检期间会话已作废，关掉自己开的源后静默退出
                closeSourceQuietly(s);
                return;
            }
            source = s;
            totalSteps = computeTotalSteps();
            prepareDisplayDecks();
            chatFrameCount = ReplayChatTimeline.countChatFrames(replayData.msgFrames);
            chatTimeline.build(replayData == null ? null : replayData.msgFrames);
            startSession();
            // 消息记录·聊天tab（用户规格：打开录像即全部可见）：时间线建好、昵称写入后即把全部
            // 聊天帧一次性预载入表（主线程侧解析格式化），无需播放到对应时间点
            final List<byte[]> preloadChat = chatTimeline.collectChatBodies();
            if (!preloadChat.isEmpty()) {
                engine.mainHandler.post(() -> {
                    if (engine.listener != null) engine.listener.onReplayChatPreload(preloadChat);
                });
            }
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
            notifyHint(ReplayReportUtil.buildReplayInfo(replayData, startTurn, chatFrameCount));
            pumpThread = new Thread(() -> pump(gen), "ReplayPump");
            pumpThread.setDaemon(true);
            CrashHandler.getInstance().hookThread(pumpThread, "回放-投喂");
            pumpThread.start();
        } catch (Throwable t) {
            CrashHandler.getInstance().report("replay-load " + replayPath, t);
            fail("录像加载异常：" + t);
        }
    }

    /** 预计算可见步总数：仅 V2 逐帧流可算（每帧首字节即消息号按 isVisibleStep 计数，与 currentStep 同口径），V1/旧格式无法预算返回 -1。 */
    private int computeTotalSteps() {
        ReplayReader.ReplayData d = replayData;
        if (d == null || d.msgFrames == null) return -1;
        int n = 0;
        for (byte[] f : d.msgFrames) {
            if (f != null && f.length > 0 && ReplayMessageSlicer.isVisibleStep(f[0] & 0xFF)) n++;
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

    /** 旧格式重跑的初始场（对齐 replay_mode.cpp StartDuel 的 dField.Initial），纯消息模式由实况管线 MSG_START 建场，两路口径一致。 */
    private void buildInitialField() {
        field.clear();
        int startLp = replayData.params.startLp;
        field.setDuelRule(replayData.params.duelFlag >> 16);
        // 初始场按当前视角建立：视角切换后重跑/重放时引擎 P0（录制者）落 localPlayer(0) 容器，与后续消息 localPlayer 映射一致
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
                    // 聊天/观战发言伪帧：非引擎消息，不计步/不投喂；有时间线时按步号在落地点补齐，无时间线的 V1 拼接流才内联即时派发
                    if (chatTimeline.isEmpty()) dispatchReplayChat(msg.body);
                    continue;
                }
                if (msg.type == MSG_RETRY) {
                    // isNoFeed 跳过不投喂（应答已固化在流里），仅计数供结束弹窗排查失步来源
                    skippedRetryCount++;
                    continue;
                }
                if (msg.type == ReplayMessageSlicer.MSG_WIN) {
                    // 结算不走实况管线（messageParser.onWin 回放侧已抑制），此处判结束并通知 UI
                    pacer.handleWin(msg.body);
                    break;
                }
                if (!ReplayMessageSlicer.isNoFeed(msg.type)) {
                    // 卡码归一在投喂副本上就地完成不改写文件；引擎重跑合成的刷新消息已在源头按查询块自界长归一
                    if (!msg.synthetic) {
                        ReplayCodeMapper.mapMessage(msg.type, msg.body, this);
                    }
                    pacer.feedMessage(msg.type, msg.body);
                    fedSincePump++;
                }
                if (msg.type == MSG_START) {
                    // MSG_START 经实况管线建场（field.initial）后回填卡组/额外卡面（快进/正常路径均适用）
                    pacer.awaitDispatchDrain();
                    zoneCounter.applyReplayDeckCodes();
                }
                fedSincePump = pacer.paceAfter(msg.type, fedSincePump);
                // 聊天按步号落地：正常节奏=currentStep 刚推进，快进落点=isSkipping 刚复位则批量补齐落点前全部聊天，undo 不再清空历史
                if (!isSkipping && !chatTimeline.isEmpty()) chatTimeline.dispatchUpTo(currentStep, this::dispatchReplayChat);
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

    /** 0xF1 聊天帧派发（帧体=playerType(1)+UTF-8 文本）：快进期间丢弃，否则经唯一入口 engine.listener.onChatReceived 按时间线重现，与实况完全同一渲染路径。 */
    private void dispatchReplayChat(byte[] body) {
        if (isSkipping || body == null || body.length < 2) return;
        final int playerType = body[0] & 0xFF;
        final String text = new String(body, 1, body.length - 1,
                StandardCharsets.UTF_8);
        if (text.isEmpty()) return;
        Log.i(TAG, "replay chat dispatch: type=" + playerType + " len=" + text.length()
                + (isSkipping ? " (skipped)" : ""));
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onChatReceived(playerType, text);
        });
    }

    /** 切片 UPDATE_DATA 前等实况管线消化完已投喂消息（实现在 ReplayPacer）：ReplaySource 经本出口调用 */
    void awaitDispatchDrain() {
        pacer.awaitDispatchDrain();
    }

    // === 播控：重排 / 视角 ===

    private void performRestart() {
        isRestarting = false;
        isSkipping = true;
        // 快进三标志前置：主队列里尚未派发完的步骤 N 消息在首次 drainAndSettle 时即瞬时落位（instantPlace 无动画落位、replaySkip 丢弃大图/特效/音效），点「上一步」不再等残留动画也不再弹大图
        beginInstantSkip();
        // 重排全程冻结屏障（对任意步型统一生效）：点瞬间起冻结 GL 并抑制/清空居中特效，重算期间见 N 静帧，落点（deliverPendingReverseAtLanding）统一解除；主线程 FIFO 使本 post 早于随后派发消息
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onReplayRenderHold(true);
        });
        if (pendingReverse) {
            // 逆向倒放：投喂线程抓取当前（N）各显示卡渲染位置快照，落点后交主线程从 N 位逆向补间回 N-1 位
            displaySnapN.clear();
            displaySnapN.addAll(field.collectDisplayPositions());
        } else {
            displaySnapN.clear();
        }
        pacer.drainAndSettle();
        source.rewind();
        // 聊天/弹幕帧随消息流回卷重新流入：先清空现有显示，防重复行/重复弹幕
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onReplayChatReset();
        });
        ReplayCodeMapper.beginSession();
        replayWinSeen = false;
        currentStep = 0;
        chatTimeline.resetCursor();   // 聊天时间线随回卷重放（落点后按步号批量补齐）
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
            // 回到 0 步：初始场即落点，无需快进（标志已在开头前置，此处直接还原）
            endInstantSkip();
            isSkipping = false;
            deliverPendingReverseAtLanding();
            pause();
            return;
        }
        // 快进标志已在方法开头前置（残余消息也走瞬时落位），此处无需再 begin
    }

    private void performSwapField() {
        // 视角互换对齐 C++ ReplaySwap 全量字段：① duelIsFirst 翻转使后续消息 localPlayer 随视角翻转；② field.swapField() 对调各区列表并逐卡重算 controler；③ 昵称/LP 对调 + currentPlayer 翻转，血条/卡数/回合高亮按新视角重取。
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
        // dInfo.lp 是视角索引的血条显示值：容器已对调直接按 players[].lp 重对齐（视角切换仅暂停态执行，LP 动画必空闲赋值安全）
        field.dInfo.lp[0] = field.players[0].lp;
        field.dInfo.lp[1] = field.players[1].lp;
        field.refreshAllCards();
        // 卡详已发动标记随视角翻转（用户规格3，与实况 performSpectatorSwap 同口径）：
        // 账本半场归属整体对调，展示中已标记卡的绿/红勾按新视角重绑；内部 post 主线程，
        // 投喂线程/主线程（暂停态直接交换）调用均安全
        CardDetailPanel.notifyViewpointSwapped();
        notifyPlayerInfo(0);      // 名字/LP 条/卡数按新视角重取
        notifyPlayerInfo(1);
        // 回合方高亮（LPBarFrame 彩色/灰色与名字色）随视角翻转重刷
        engine.mainHandler.post(() -> {
            if (engine.listener != null) {
                engine.listener.onTurnStarted(field.currentPlayer);
                // 切视角同步左右对调双方聊天内容（与昵称/LP 对调同批次），使 player1 消息随其所在侧搬到对调后对应血条下方
                engine.listener.onViewpointSwapped();
            }
        });
    }

    // === 实况管线会话标志 ===

    /** 开始回放会话：置实况管线回放标志；录制者视角恒等映射（duelIsFirst=true），双方昵称写 playerInfos */
    private void startSession() {
        engine.replayMode = true;
        // 回放遮蔽保护：手卡区遮蔽零块（合并客户端视角流里对方手码被置零）不再覆盖流内 MSG_DRAW/MSG_MOVE 已写入的真实卡码→对方手卡保持正面
        GameField.preserveMaskedHandCode = true;
        engine.duelIsFirst = true;
        engine.inDuel = false;
        if (replayData != null) {
            // 对齐 replay_mode.cpp 名字装载：非 tag 时 names[0/1] 为对戦双方；tag 时 names[0..3]=hostname/hostname_tag/clientname/clientname_tag（座位 0/1 同队、2/3 同队），并同步 isTag/tag_player 复位
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
        GameField.preserveMaskedHandCode = false;
        engine.replaySkip = false;
        field.instantPlace = false;
        engine.duelIsFirst = true;
        soundManager.setEffectsSuppressed(false);
        isSkipping = false;
        skipStep = 0;
        skipTurn = 0;
        landAtStart = false;
        stepsRemaining = 0;
        // 逆向倒放安全解锁：若会话在重算/补间途中被停/作废，此前 post 的渲染冻结须释放否则画面永久冻结；pendingReverse/displaySnapN 一并复位，post(false) 幂等
        pendingReverse = false;
        displaySnapN.clear();
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onReplayRenderHold(false);
        });
    }

    /** 快进三标志开启：实况侧抑制动画/特效/音效，消息处理即时落位 */
    private void beginInstantSkip() {
        engine.replaySkip = true;
        field.instantPlace = true;
        soundManager.setEffectsSuppressed(true);
    }

    /** 快进三标志还原：恢复正常播放节奏（引擎侧动画持闸残留一并清零） */
    void endInstantSkip() {
        engine.replaySkip = false;
        field.instantPlace = false;
        soundManager.setEffectsSuppressed(false);
        engine.animHoldUntilMs = 0;
    }

    /** 快进落点：排空、还原标志、暂停并回填卡组卡面 */
    void landInstantSkip() {
        pacer.drainAndSettle();
        endInstantSkip();
        isSkipping = false;
        currentStep = restartFromStep;
        zoneCounter.applyReplayDeckCodes();
        deliverPendingReverseAtLanding();
        pause();
    }

    /** 「上一步」落点统一解冻：解除渲染冻结屏障，可倒放位移步则先在主线程把步骤 N 快照逆向补间回 N-1 落位再解冻，复杂步/restart 无快照直接跳变；快照在投喂线程拷贝避免与重算竞态。 */
    private void deliverPendingReverseAtLanding() {
        final List<ReplayPosSnap> snap = pendingReverse ? new ArrayList<>(displaySnapN) : null;
        pendingReverse = false;
        displaySnapN.clear();
        engine.mainHandler.post(() -> {
            // 落点帧先全场落定并清除残留态，再对位移卡从 N 快照逆向补间，最后统一解冻
            field.refreshAllCards();
            if (snap != null) field.applyReverseAnimation(snap);
            if (engine.listener != null) engine.listener.onReplayRenderHold(false);
        });
    }

    /** 会话收尾：结算派发、转码固化与终态落定 */
    private void finishSession(long gen) {
        // 旧会话被作废（退出/重新点播）：stop() 已清标志关源，此处静默退出不再派发（否则首播即弹「录像已结束」）
        if (sessionGen != gen) return;
        if (source != null && source.getLastError() != null && lastErrorMessage == null) {
            lastErrorMessage = source.getLastError();
        }
        // 收尾动画屏障②：等胜负文字与全部残余卡片/LP 动画播完再派发终态，确保结束弹窗落在动画之后
        if (!isSkipping) pacer.waitForAnimationsSettled(8000L);
        if (sessionGen != gen) return;   // 等待期间被新会话作废：静默退出，不再派发终态
        // 转码固化素材：成功播完的旧格式重跑抓取帧列表与响应段快照，条件不满足则保持 null
        List<byte[]> transcodeFrames = null;
        byte[] responseSnapshot = null;
        if (lastErrorMessage == null && source != null && source.engineDriven()) {
            ReplayReader.ReplayData d = replayData;
            List<byte[]> captured = source.capturedEngineFrames();
            if (d != null && d.header.base.id == ReplayReader.REPLAY_ID_YRP2
                    && !d.isSingleMode && !d.isTag && d.replayBuffer != null
                    && captured != null && !captured.isEmpty()) {
                transcodeFrames = chatTimeline.buildTranscodeFrames(captured);   // 聊天 0xF1 帧按步号并回，转码不丢聊天
                ByteBuffer rb = d.replayBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN);
                responseSnapshot = new byte[rb.remaining()];
                rb.get(responseSnapshot);
            }
        }
        // 落定播完态（须在 clearReplayFlags 前判定）：无错且胜负结算或流尽=播完，否则提前结束附原因
        playbackCompleted = lastErrorMessage == null && (replayWinSeen || endOfStream);
        if (!playbackCompleted && lastErrorMessage == null) {
            earlyEndNote = isSkipping ? "快进重排中止" : "消息流未播尽";
        }
        // 自然播毕补齐：最后可见步之后的聊天帧（结算后发言/引擎重跑尾部时基）按时间线落地
        if (!isSkipping && (endOfStream || replayWinSeen)) chatTimeline.dispatchUpTo(Integer.MAX_VALUE, this::dispatchReplayChat);
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
        ReplayReportUtil.writeDiagnostics(TAG, lastErrorMessage, source, currentStep, replayData);
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

    /** 回退路径专用：关闭开源失败的引擎源（释放已创建的 pduel 等原生资源），不影响后续改用的流源 */
    private void closeSourceQuietly(ReplaySource s) {
        try {
            if (s != null) s.close();
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
        ReplayReportUtil.writeDiagnostics(TAG, lastErrorMessage, source, currentStep, replayData);
    }

    // === 投喂线程状态 ===

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

    void setState(State newState) {
        if (state == newState && newState != State.PLAYING) return;
        state = newState;
        CrashHandler.getInstance().setScene("回放-" + newState);
        mainHandler.post(() -> {
            if (listener != null) listener.onReplayStateChanged(newState);
        });
    }

    void notifyHint(String hint) {
        mainHandler.post(() -> {
            if (listener != null) listener.onReplayHintMessage(hint);
        });
    }

    private void notifyPlayerInfo(int player) {
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onPlayerInfoUpdated(player);
        });
    }

}
