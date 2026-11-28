package cn.garymb.ygomobile.game.engine;

import android.util.Log;

import cn.garymb.ygomobile.network.YGOProtocol;
import ocgcore.enums.GameMessage;

/**
 * 询问丢失自愈看门狗（自 GameEngine 平移，逻辑零改）：答完上一步却收不到下一条询问时
 * 向服务端求援（CTOS_ASK_RESEND），最多 ASK_PROBE_MAX 次。
 */
class AskWatchdog {

    private static final String TAG = "GameEngine";

    /** 应答发出后多久仍收不到询问/等待提示，即判定询问丢在半路。 */
    private static final long ASK_STUCK_GRACE_MS = 2500L;
    /** 同一次卡死最多求援几次。 */
    private static final int ASK_PROBE_MAX = 3;
    /** 两次求援之间的最小间隔。 */
    private static final long ASK_PROBE_INTERVAL_MS = 1500L;
    /** 看门狗复查周期。 */
    private static final long ASK_WATCHDOG_TICK_MS = 500L;
    /**
     * 不依赖服务端求援能力的链路卡死判定窗口：本端答完之后既没有新询问也没有等待提示
     * 达此时长，即认为与主机的链路已经不通，转入断线重连。
     * <p>取宽于求援窗口（2.5s）的 8s，是为了把“服务端只是慢/求援能修好”与“链路已断”分开；
     * 它不会误伤对方长考：引擎在等别人应答时一定会向本端下发 MSG_WAITING，而等待提示同样
     * 刷新 {@link #lastAskRxAt}（与求援判据同源）。
     */
    private static final long LINK_STUCK_MS = 8000L;

    private volatile long lastAskRxAt;
    /**
     * 最近一条「需要本端应答」的询问（MSG_SELECT_*、宣言类 MSG_ANNOUNCE_* 与决斗中猜拳
     * MSG_ROCK_PAPER_SCISSORS）到达时刻：
     * 与 {@link #lastResponseTxAt} 比较即得「当前是否仍有一条询问待答」（见
     * {@link #isQuestionPending()}），撤回被拒时据此决定能否按询问快照重派发重建弹窗。
     * 不能复用 lastAskRxAt：它含 MSG_WAITING，等待提示会把已答完的旧询问误判为待答。
     */
    private volatile long lastQuestionRxAt;
    private volatile long lastResponseTxAt;
    private int askProbeCount;
    private long askProbeAt;
    private boolean askWatchdogRunning;

    private final GameEngine engine;

    AskWatchdog(GameEngine engine) {
        this.engine = engine;
    }

    private final Runnable askWatchdogTask = new Runnable() {
        @Override
        public void run() {
            if (!askWatchdogRunning) {
                return;
            }
            long tx = lastResponseTxAt;
            long now = System.currentTimeMillis();
            // 答完之后长时间无任何询问/等待提示（不论服务端是否支持求援）：按用户规格视为
            // 我方与服务端（主机）通信超时，转入重连（我方头像转圈，不再把界面退回大厅）
            if (tx != 0L && tx > lastAskRxAt && now - tx >= LINK_STUCK_MS
                    && engine.beginLinkResume("no message " + (now - tx) + "ms after our response")) {
                Log.w(TAG, "no ask/waiting message " + (now - tx) + "ms after our response: link lost, resuming");
                askWatchdogRunning = false;
                return;
            }
            if (tx == 0L || tx <= lastAskRxAt || !canProbeAsk() || askProbeCount >= ASK_PROBE_MAX) {
                askWatchdogRunning = false;
                return;
            }
            if (now - tx >= ASK_STUCK_GRACE_MS && now - askProbeAt >= ASK_PROBE_INTERVAL_MS) {
                askProbeCount++;
                askProbeAt = now;
                Log.w(TAG, "no question or waiting hint arrived " + (now - tx)
                        + "ms after our response: ask server to re-hang (try " + askProbeCount + ")");
                engine.client.sendAskResend();
            }
            engine.mainHandler.postDelayed(this, ASK_WATCHDOG_TICK_MS);
        }
    };

    /** 本端可以发求援包：联机决斗中、非回放/残局/观战，且服务端认得这个包号。 */
    boolean canProbeAsk() {
        return engine.duelStarted && !engine.replayMode && !engine.isSingleMode
                && !engine.isSpectator()
                && (engine.isHost || engine.serverCapsAskResend);
    }

    /**
     * 链路卡死判定（与求援不同，不要求服务端支持 {@code CTOS_ASK_RESEND}）：
     * 第三方主机也能用，它只会走重连尝试而不发求援包。
     */
    boolean canWatchLink() {
        return engine.duelStarted && !engine.replayMode && !engine.isSingleMode
                && !engine.isSpectator();
    }

    /** 由 GameActions 在实际发出 CTOS_RESPONSE 后回调：开始看门狗计时。
     *  撤回闩锁不再在此解除（旧语义「本方发出一条应答才算完成一次撤回」使连续撤回不可用）：
     *  改由 {@link UndoClientCoordinator#applyUndoResync} 在重同步落地时、以及被拒时解除，
     *  能不能再撤的准入见 {@link UndoClientCoordinator#canSendUndo} */
    void notifyResponseSent() {
        lastResponseTxAt = System.currentTimeMillis();
        // 看门狗同时担当“答完后链路卡死”的判定（不依赖求援能力），因此连不支持
        // CTOS_ASK_RESEND 的第三方主机也能进入重连；任务内的求援仍受 canProbeAsk 把关
        if (!canProbeAsk() && !canWatchLink() || askWatchdogRunning) {
            return;
        }
        askWatchdogRunning = true;
        engine.mainHandler.postDelayed(askWatchdogTask, ASK_WATCHDOG_TICK_MS);
    }

    /**
     * 询问/等待提示到达：求援计数作废；msgType 为需本端应答的询问（MSG_SELECT_* = 10..26、
     * 宣言类 MSG_ANNOUNCE_* = 140..143 与决斗中猜拳 MSG_ROCK_PAPER_SCISSORS = 132）时另记待答询问时戳，
     * 供 {@link #isQuestionPending()}。
     */
    void notifyAskReceived(int msgType) {
        long now = System.currentTimeMillis();
        lastAskRxAt = now;
        if (isAnswerableQuestion(msgType)) {
            lastQuestionRxAt = now;
        }
        askProbeCount = 0;
    }

    /** 本端是否仍有一条尚未应答的询问：最近一条需应答的询问晚于最近一次实际发出的应答。 */
    boolean isQuestionPending() {
        return lastQuestionRxAt > lastResponseTxAt;
    }

    /**
     * 这条消息是否需要本端给出应答（与询问快照留存范围同源，见 MsgDispatchGate）：
     * MSG_SELECT_*(10..26)、宣言类 MSG_ANNOUNCE_*(140..143)，再加决斗中脚本猜拳
     * MSG_ROCK_PAPER_SCISSORS(132)——core 写完那一字节就停在
     * PROCESSOR_ROCK_PAPER_SCISSORS 等 int32 应答（operations.cpp field::rock_paper_scissors），
     * 不纳入则「变则齿轮」(58297729) 一类卡既无求援也不落询问快照，对局始终挂死。
     * MSG_HAND_RES(133) 则不能纳入：它是纯展示的结果广播、core 不等任何人应答，误记为
     * 待答询问会使 isQuestionPending() 长期为真，把撤回恢复与应答屏障带偏。
     */
    static boolean isAnswerableQuestion(int msgType) {
        return (msgType >= GameMessage.SelectBattleCmd.value()
                && msgType <= GameMessage.SelectUnselectCard.value())
                || msgType == GameMessage.RockPaperScissors.value()
                || (msgType >= GameMessage.AnnounceRace.value()
                && msgType <= GameMessage.AnnounceNumber.value());
    }

    /** 新一局开始：作废上一局时戳与求援计数。 */
    void resetAskWatchdog() {
        lastAskRxAt = 0L;
        lastQuestionRxAt = 0L;
        lastResponseTxAt = 0L;
        askProbeCount = 0;
        askProbeAt = 0L;
        askWatchdogRunning = false;
        engine.mainHandler.removeCallbacks(askWatchdogTask);
    }

    /** 这条消息是否让界面重新有了可应答项。 */
    static boolean isAskOrWaitingMessage(int msgType) {
        if (msgType == GameMessage.Waiting.value()) {
            return true;
        }
        if (msgType >= GameMessage.SelectBattleCmd.value()
                && msgType <= GameMessage.SelectUnselectCard.value()) {
            return true;
        }
        return msgType == GameMessage.RockPaperScissors.value()
                || (msgType >= GameMessage.AnnounceRace.value()
                && msgType <= GameMessage.AnnounceNumber.value());
    }
}
