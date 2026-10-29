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

    private volatile long lastAskRxAt;
    /**
     * 最近一条「需要本端应答」的询问（MSG_SELECT_* 与宣言类 MSG_ANNOUNCE_*）到达时刻：
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
            if (tx == 0L || tx <= lastAskRxAt || !canProbeAsk() || askProbeCount >= ASK_PROBE_MAX) {
                askWatchdogRunning = false;
                return;
            }
            long now = System.currentTimeMillis();
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

    /** 由 GameActions 在实际发出 CTOS_RESPONSE 后回调：开始看门狗计时。 */
    void notifyResponseSent() {
        // 本方有了新的操作：解除撤回一次性闩锁
        engine.undoBlockedUntilNewAction = false;
        lastResponseTxAt = System.currentTimeMillis();
        if (!canProbeAsk() || askWatchdogRunning) {
            return;
        }
        askWatchdogRunning = true;
        engine.mainHandler.postDelayed(askWatchdogTask, ASK_WATCHDOG_TICK_MS);
    }

    /**
     * 询问/等待提示到达：求援计数作废；msgType 为需本端应答的询问（MSG_SELECT_* = 10..26
     * 或宣言类 MSG_ANNOUNCE_* = 140..143）时另记待答询问时戳，供 {@link #isQuestionPending()}。
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

    /** 这条消息是否需要本端给出应答（与询问快照留存范围同源，见 MsgDispatchGate）。 */
    static boolean isAnswerableQuestion(int msgType) {
        return (msgType >= GameMessage.SelectBattleCmd.value()
                && msgType <= GameMessage.SelectUnselectCard.value())
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
