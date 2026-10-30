package cn.garymb.ygomobile.game.engine;
import cn.garymb.ygomobile.game.gamefield.EffectActivationLedger;
import cn.garymb.ygomobile.game.gamefield.GameField;

import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import cn.garymb.ygomobile.network.YGOProtocol;
import cn.garymb.ygomobile.render.card.CardDetailPanel;

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
    /** 待消费的「回退后重放前缀内有效效果发动」条数（STOC_UNDO_ACK 尾部新字段）：
     *  随 reload 落地时把发动账本超出该条数的后缀剪除（被撤回抹掉的使用过的效果恢复
     *  未使用态）；-1 = 旧服务端短载荷/无 latch，跳过账本回退。 */
    volatile int undoResyncChainEvents = -1;
    /** 本次撤回重同步消费的「保留发动条数」（applyUndoResync 从上一字段取出后暂存，同批重同步里由
     *  ReloadFieldBuilder 读它剪除场地持续态标注中属于被抹掉那次发动的位）；-1 = 旧服务端不报条数，不回退；
     *  ACK 与 reload 同走 DuelClient.mainHandler 一条 FIFO，volatile 与同组闩锁字段一致，为跨线程可见性兜底 */
    volatile int undoKeptChainEvents = -1;

    // === 撤回发出闸门 ===

    /** 两次 CTOS_UNDO 之间的最小间隔：覆盖服务端「留白 400ms 吞旧应答 + 延后重挂询问」与客户端
     *  「全量 reload + 各区 refresh 派发」的整段窗口，避免两批 ACK/reload 在单槽闩锁上错拍 */
    private static final long UNDO_SEND_MIN_INTERVAL_MS = 1000L;
    /** 上一次发出 CTOS_UNDO 的时刻（0 = 本局尚未发过），新一局随 {@link #clearUndoResync()} 复位 */
    private volatile long lastUndoSentAt;

    /** 重同步落地超时自愈时限（两种挂住形态共用）：reload 因异常载荷早退或被丢时单槽闩锁会永久挂住
     *  撤回入口；超过此时限即在 canSendUndo 判定处复位重同步组（不动 lastUndoSentAt 的 1s 间隔计时，
     *  也不动有自身 5s 兜底的应答屏障与 DENIED 恢复待答询问的路径） */
    private static final long UNDO_RESYNC_TIMEOUT_MS = 8000L;
    /** 「连 ACK 都没回来」形态的自愈时限：比 reload 窗口宽一倍，那一趟要覆盖服务端整场重建引擎 +
     *  静默重放全部前缀应答 + 重录录像的总耗时，误判会让第二次 CTOS_UNDO 在第一次还在算的时候发出去 */
    private static final long UNDO_ACK_TIMEOUT_MS = 16000L;
    /** ACK=OK/REBUILT 到达时刻（0 = 无待落地重同步）：onUndoAck 写入，applyUndoResync 消费、超时自愈与 clearUndoResync 清零 */
    private volatile long undoResyncLatchAtMs;

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

    // === sendUndo / canUndo / canSendUndo / isUndoPromptActive ===

    void sendUndo() {
        // 准入闸门（用户方案2）：上一次撤回的重同步未落地、回退点的询问还没挂回界面、或距上次
        // 撤回不足最小间隔时一律不发 CTOS_UNDO；能不能撤的最终判定仍交回服务端（STOC_UNDO_STATE）
        if (!canSendUndo()) return;
        engine.undoBlockedUntilNewAction = true;
        lastUndoSentAt = System.currentTimeMillis();
        armUndoResponseBarrier();
        engine.lobbyActions.sendUndo();
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onUndoStateChanged(engine.undoAvailable);
        });
    }

    boolean canUndo() {
        return (engine.isHost || engine.serverCapsUndo) && !engine.isSingleMode && !engine.replayMode;
    }

    /** 现在是否允许再发一条 CTOS_UNDO：闩锁已解（重同步落地或被拒）+ 无待消费重同步（超时自愈见
     *  {@link #healStaleUndoResyncLatch}）+ 应答屏障已放行（回退点询问已重新派发）+ 过了最小间隔；
     *  屏障须内联读 undoBarrierSeq，不能用会置 undoBarrierDropped 的 {@link #isResponseBlocked()} */
    boolean canSendUndo() {
        healStaleUndoResyncLatch();
        if (engine.undoBlockedUntilNewAction || undoResyncTurn >= 0) return false;
        long barrier = undoBarrierSeq;
        if (barrier >= 0 && questionSeq <= barrier) return false;
        long last = lastUndoSentAt;
        return last == 0L || System.currentTimeMillis() - last >= UNDO_SEND_MIN_INTERVAL_MS;
    }

    /** 超时自愈（两种挂住形态，同一入口）：① ACK=OK/REBUILT 已到但其 reload 超时未被 applyUndoResync 消费
     *  （按 undoResyncLatchAtMs 计时）；② 连 ACK 都没回来（服务端不支持/静默丢弃此包，只剩发送侧闩锁挂着），
     *  改按 lastUndoSentAt 计时并放宽到 {@link #UNDO_ACK_TIMEOUT_MS}。两者都只整组复位重同步闩锁与
     *  undoBlockedUntilNewAction（迟到的旧 reload/ACK 不得再把陈旧回合回填进已推进的新局面，也不让一个误点
     *  把入口锁到本局结束），不碰 {@link #lastUndoSentAt} 的 1s 间隔与应答屏障；DENIED 分支早已把闩锁解了、
     *  turn 置 -1，进不了本判定；只在主线程 canSendUndo 入口调用，与同线程 applyUndoResync 串行不争抢 */
    private void healStaleUndoResyncLatch() {
        boolean resyncPending = undoResyncTurn >= 0;
        if (!resyncPending && !engine.undoBlockedUntilNewAction) return;
        long at = resyncPending ? undoResyncLatchAtMs : lastUndoSentAt;
        long limit = resyncPending ? UNDO_RESYNC_TIMEOUT_MS : UNDO_ACK_TIMEOUT_MS;
        long elapsed = at <= 0L ? 0L : System.currentTimeMillis() - at;
        if (elapsed < limit) return;
        Log.w(TAG, "undo round stale for " + elapsed + "ms (" + (resyncPending
                ? "MSG_RELOAD_FIELD never landed" : "no STOC_UNDO_ACK at all")
                + "): reset gates");
        resetUndoResyncLatch();
        engine.undoBlockedUntilNewAction = false;
    }

    /** 复位待消费的重同步闩锁组（不动 lastUndoSentAt 的 1s 最小间隔计时，也不动应答屏障）；
     *  超时自愈复用它做幂等复位：DENIED/clearUndoResync 调用时各字段多为 -1，重复写入无副作用 */
    private void resetUndoResyncLatch() {
        undoResyncTurn = -1;
        undoResyncPlayer = -1;
        undoResyncPhase = -1;
        undoResyncChainEvents = -1;
        undoKeptChainEvents = -1;
        undoResyncLatchAtMs = 0L;
    }

    boolean isUndoPromptActive() {
        return canUndo() && engine.undoAvailable && canSendUndo();
    }

    // === STOC_UNDO_ACK 处理 ===

    void onUndoAck(int result, int turn, int currentPlayer, int phase, int chainEvents) {
        if (result == YGOProtocol.UNDO_ACK_DENIED) {
            resetUndoResyncLatch();
            // 被拒即无后续重同步：闩锁当场解除，本方手上的询问答完（或新询问到达）就能再次发起撤回
            engine.undoBlockedUntilNewAction = false;
            boolean droppedWhileArmed = undoBarrierDropped;
            releaseUndoResponseBarrier();
            if (droppedWhileArmed) restorePendingQuestionUi();
            postUndoHint("撤回不可用：没有可撤回的操作，或该操作不是你这方做出的");
            // 通知界面恢复点击撤回时即时隐藏的询问弹窗：丢弃过应答时询问已由上方
            // restorePendingQuestionUi 按新询问重新派发（旧快照不得再恢复，否则两窗叠加），
            // 未丢弃时询问仍在原弹窗上待答，不恢复就永久不可见
            final boolean redelivered = droppedWhileArmed;
            engine.mainHandler.post(() -> {
                if (engine.listener != null) engine.listener.onUndoDenied(redelivered);
            });
            return;
        }
        engine.invalidateCurrentMsgSegment();
        clearStaleMsgStateAfterUndo();
        undoResyncTurn = turn;
        undoResyncPlayer = currentPlayer;
        undoResyncPhase = phase;
        undoResyncChainEvents = chainEvents;
        // 记下 ACK 到达时刻：它与其后的 MSG_RELOAD_FIELD 同源同序，超过时限仍未被消费即自愈
        undoResyncLatchAtMs = System.currentTimeMillis();
        postUndoHint(result == YGOProtocol.UNDO_ACK_REBUILT
                ? "撤回失败：已按原局面重建并重同步" : "已撤回上一步操作");
        if (engine.listener != null) engine.listener.onUndoResync();
    }

    private void postUndoHint(String text) {
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onNoticeMessage(text);
        });
    }

    // === 重同步落点 ===

    boolean applyUndoResync(GameField field) {
        int turn = undoResyncTurn;
        if (turn < 0) return false;
        undoResyncTurn = -1;
        // 重同步已落地，ACK→reload 的超时自愈计时随之作废
        undoResyncLatchAtMs = 0L;
        // 用户方案1：闩锁改在「重同步已落地」这一时刻解除（旧的解锁条件是本方发出一条应答）。
        // 此刻回退点的询问尚未挂回，屏障仍在压制，canSendUndo() 要等新询问派发后才会放行，
        // 既支持纯单人模式一路撤回到底，也不让两批 ACK/reload 交叠
        engine.undoBlockedUntilNewAction = false;
        field.turnCount = turn;
        // 撤回重同步回到历史回合：发动标记的「本回合」基准随之校正，并刷新正在展示的卡详
        EffectActivationLedger.setCurrentTurn(turn);
        // 发动账本回退（用户新规格）：服务端重放前缀里只有 kept 条有效发动，账本尾部超出
        // 的部分属于被回退的那一段——剪除后「使用过→未使用」的卡即时恢复常规效果文本；
        // 保留前缀内的条目继续标记本回合/决斗已使用；旧服务端短载荷（-1）不回退
        int chainEvents = undoResyncChainEvents;
        undoResyncChainEvents = -1;
        undoKeptChainEvents = chainEvents;
        if (chainEvents >= 0) {
            EffectActivationLedger.dropBeyond(chainEvents);
        }
        CardDetailPanel.refreshCardDetailMarks();
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
        resetUndoResyncLatch();
        lastUndoSentAt = 0L;
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

    /**
     * 按留存的最近询问快照重派发那一条待答询问（撤回被拒/超时、屏障期间丢弃过应答、
     * MSG_RETRY 兜底共用）：各选择类工厂据此重建完全相同的待答窗，并重建选择上下文。
     *
     * @return 是否真的重派发了（无快照可重派发时返回 false，调用方需改走「恢复旧窗实例」
     *         等其它恢复路径，否则询问永久不可见无法应答）
     */
    boolean restorePendingQuestionUi() {
        int type = lastQuestionType;
        byte[] body = lastQuestionBody;
        if (type < 0 || body == null) return false;
        ByteBuffer buf = ByteBuffer.wrap(body);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        engine.pendingMsgs.offer(() -> engine.dispatchGameMsg(type, buf));
        engine.drainPendingMsgs();
        return true;
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
