package cn.garymb.ygomobile.game;

import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import cn.garymb.ygomobile.network.YGOProtocol;

/**
 * 撤回（undo）客户端域实现（自 GameEngine 平移，逻辑零改）：CTOS_UNDO 发起、
 * STOC_UNDO_ACK 处理、重同步闩锁、应答代次屏障。GameEngine 保留同名薄委托方法供外部调用。
 */
class UndoClientCoordinator {

    private static final String TAG = "GameEngine";

    // === 撤回重同步闩锁字段 ===

    /** 待消费的撤回重同步回合数；-1 = 无待消费 latch。 */
    volatile int undoResyncTurn = -1;
    volatile int undoResyncPlayer = -1;
    volatile int undoResyncPhase = -1;

    // === 应答代次屏障字段 ===

    private volatile long questionSeq;
    private volatile long undoBarrierSeq = -1L;
    private static final long UNDO_BARRIER_TIMEOUT_MS = 5000L;
    private final Runnable undoBarrierFailsafe = () -> {
        if (!isResponseBlocked()) return;
        Log.w(TAG, "undo response barrier timed out: release");
        releaseUndoResponseBarrier();
    };
    /** 最近一条询问快照，供撤回被拒时重派发 */
    int lastQuestionType = -1;
    byte[] lastQuestionBody;
    /** 本次布防期间是否丢弃过出口应答 */
    private volatile boolean undoBarrierDropped;

    private final GameEngine engine;

    UndoClientCoordinator(GameEngine engine) {
        this.engine = engine;
    }

    // === sendUndo / canUndo / isUndoPromptActive ===

    void sendUndo() {
        if (engine.undoBlockedUntilNewAction) return;
        engine.undoBlockedUntilNewAction = true;
        armUndoResponseBarrier();
        engine.lobbyActions.sendUndo();
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onUndoStateChanged(engine.undoAvailable);
        });
    }

    boolean canUndo() {
        return (engine.isHost || engine.serverCapsUndo) && !engine.isSingleMode && !engine.replayMode;
    }

    boolean isUndoPromptActive() {
        return canUndo() && engine.undoAvailable && !engine.undoBlockedUntilNewAction;
    }

    // === STOC_UNDO_ACK 处理 ===

    void onUndoAck(int result, int turn, int currentPlayer, int phase) {
        if (result == YGOProtocol.UNDO_ACK_DENIED) {
            undoResyncTurn = -1;
            boolean droppedWhileArmed = undoBarrierDropped;
            releaseUndoResponseBarrier();
            if (droppedWhileArmed) restorePendingQuestionUi();
            postUndoHint("撤回不可用：没有可撤回的操作，或该操作不是你这方做出的");
            return;
        }
        engine.invalidateCurrentMsgSegment();
        clearStaleMsgStateAfterUndo();
        undoResyncTurn = turn;
        undoResyncPlayer = currentPlayer;
        undoResyncPhase = phase;
        postUndoHint(result == YGOProtocol.UNDO_ACK_REBUILT
                ? "撤回失败：已按原局面重建并重同步" : "已撤回上一步操作");
        if (engine.listener != null) engine.listener.onUndoResync();
    }

    private void postUndoHint(String text) {
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onHintMessage(text);
        });
    }

    // === 重同步落点 ===

    boolean applyUndoResync(GameField field) {
        int turn = undoResyncTurn;
        if (turn < 0) return false;
        undoResyncTurn = -1;
        field.turnCount = turn;
        if (undoResyncPlayer >= 0) {
            field.currentPlayer = engine.localPlayer(undoResyncPlayer & 1);
        }
        if (undoResyncPhase >= 0) {
            field.currentPhase = undoResyncPhase;
        }
        if (engine.soloMode && !engine.replayMode && engine.inDuel && field.currentPlayer == 1) {
            engine.requestSpectatorSwap();
        }
        return true;
    }

    void clearUndoResync() {
        undoResyncTurn = -1;
        undoResyncPlayer = -1;
        undoResyncPhase = -1;
        releaseUndoResponseBarrier();
        engine.undoBlockedUntilNewAction = false;
    }

    // === 撤回后残留清理 ===

    private void clearStaleMsgStateAfterUndo() {
        engine.pendingMsgs.clear();
        engine.lastGameMsgType = -1;
        engine.lastGameMsgBody = null;
        engine.retryReplayCount = 0;
        lastQuestionType = -1;
        lastQuestionBody = null;
        engine.animGateClosed = false;
        engine.animHoldUntilMs = 0;
        engine.mainHandler.removeCallbacks(engine.animGatePoller);
        engine.mainHandler.removeCallbacks(engine.animGateFailsafe);
        if (engine.field != null) {
            engine.clearCommandFlags();
            engine.resetFieldCommandHints();
            engine.field.clearSelectionVisuals();
        }
        armUndoResponseBarrier();
    }

    // === 应答代次屏障 ===

    void onQuestionDispatched() {
        questionSeq++;
    }

    private void armUndoResponseBarrier() {
        undoBarrierSeq = questionSeq;
        undoBarrierDropped = false;
        engine.mainHandler.removeCallbacks(undoBarrierFailsafe);
        engine.mainHandler.postDelayed(undoBarrierFailsafe, UNDO_BARRIER_TIMEOUT_MS);
    }

    private void releaseUndoResponseBarrier() {
        undoBarrierSeq = -1L;
        engine.mainHandler.removeCallbacks(undoBarrierFailsafe);
    }

    void restorePendingQuestionUi() {
        int type = lastQuestionType;
        byte[] body = lastQuestionBody;
        if (type < 0 || body == null) return;
        ByteBuffer buf = ByteBuffer.wrap(body);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        engine.pendingMsgs.offer(() -> engine.dispatchGameMsg(type, buf));
        engine.drainPendingMsgs();
    }

    boolean isResponseBlocked() {
        long barrier = undoBarrierSeq;
        if (barrier < 0 || questionSeq > barrier) return false;
        undoBarrierDropped = true;
        return true;
    }

    long captureQuestionToken() {
        return questionSeq;
    }

    boolean isQuestionStale(long token) {
        return token != questionSeq;
    }
}
