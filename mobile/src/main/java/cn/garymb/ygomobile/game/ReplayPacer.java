package cn.garymb.ygomobile.game;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** 回放投喂执行协作类：承载 pump 单步后的节奏推进、快进计数与落点、实况队列排空/动画等待与胜负结算（持 ReplayPlayer 反向引用，读写同一实例字段以保持时序语义）。 */
final class ReplayPacer {

    private final ReplayPlayer p;

    ReplayPacer(ReplayPlayer p) {
        this.p = p;
    }

    /** 主线程投递消息进实况管线（队列/动画闸门均为主线程态，必须 post）。 */
    void feedMessage(int msgType, byte[] body) {
        p.engine.mainHandler.post(() ->
                p.engine.enqueueGameMsg(msgType, ByteBuffer.wrap(body).order(ByteOrder.LITTLE_ENDIAN)));
    }

    /** 单条消息投喂后的节奏处理（快进计数/落点/单步自动暂停/可见步等待管线排空）：返回投喂计数，负值表示应终止投喂循环。 */
    long paceAfter(int msgType, long fedSincePump) throws InterruptedException {
        boolean visible = ReplayMessageSlicer.isVisibleStep(msgType);
        if (p.isSkipping && p.skipStep == 0 && !p.landAtStart) {
            // 跳回合快进：可见步照常计步（步号与正常播放一致），但不等动画、不停顿
            if (visible) {
                p.currentStep++;
                p.lastVisibleMsgType = msgType;
            }
            if (msgType == ReplayPlayer.MSG_NEW_TURN && p.skipTurn > 0 && --p.skipTurn == 0) {
                p.endInstantSkip();
                p.isSkipping = false;
                p.notifyHint("快进结束，从当前回合开始正常播放");
            }
            return pumpGuard(fedSincePump);
        }
        if (p.isSkipping) {
            // undo/restart 快进重排：不计数，到目标可见步（或初始场落点）即停；途中每消耗一条可见步同步 lastVisibleMsgType，落点即 N-1 步类型，保证连续 undo 与 undo 后再 forward 分类正确
            if (p.skipStep > 0) {
                if (visible) {
                    p.lastVisibleMsgType = msgType;
                    if (--p.skipStep == 0) p.landInstantSkip();
                }
            } else if (p.landAtStart && msgType == ReplayPlayer.MSG_START) {
                p.landInstantSkip();      // 重投 MSG_START 重建初始场后即停（回到 0 步）
            }
            return pumpGuard(fedSincePump);
        }
        if (!visible) return fedSincePump;   // 非可见步瞬流过（无卡片移动的消息不再停顿）
        p.currentStep++;
        p.lastVisibleMsgType = msgType;
        if (p.stepsRemaining > 0) {
            p.stepsRemaining--;
            if (p.stepsRemaining == 0) {
                p.isPaused = true;
                p.setState(ReplayPlayer.State.PAUSED);
            }
        }
        // 等实况管线（动画闸门 + pendingMsgs）消化完本步再投喂下一条：动画完成信号来自实况闸门，与观战同一节奏来源
        waitForPipelineIdle();
        if (p.stepsRemaining == 0 && !p.isPaused && !p.isRestarting) {
            Thread.sleep(p.field.animationSpeed > 1f ? 40L : 80L);
        }
        return fedSincePump;
    }

    /** 投喂过多时强制排空实况队列（防主线程队列无限堆积）；返回 -1 表示投喂线程已被中止。 */
    long pumpGuard(long fedSincePump) {
        if (!p.isRunning) return -1;
        if (fedSincePump >= 512) {
            p.engine.drainReplayQueueNow();
            return 0;
        }
        return fedSincePump;
    }

    void handleWin(byte[] body) {
        int winner = body != null && body.length > 0 ? (body[0] & 0xFF) : 2;
        int reason = body != null && body.length > 1 ? (body[1] & 0xFF) : 0;
        p.replayWinSeen = true;
        if (p.isSkipping) return;   // 快进重排途中遇到 WIN：不弹结果
        p.soundManager.stopBGM();
        // 收尾动画屏障①：MSG_WIN 常与最后一段 LP 扣减/结算消息同批到达，先等实况管线消化完已投喂消息并把卡片移动/LP 浮字/血条归零动画全部播完，再派发胜负文字（修复：弹窗与 LP 减少/you win 文字动画同时进行）
        awaitDispatchDrain();
        waitForAnimationsSettled(6000L);
        if (!p.isRunning) return;   // 等待期间用户退出：不再派发结果
        final CountDownLatch shown = new CountDownLatch(1);
        p.mainHandler.post(() -> {
            try {
                if (p.listener != null) p.listener.onReplayFinished(winner, reason);
            } finally {
                shown.countDown();
            }
        });
        // 等 UI 实际执行完 showWinText（YOU WIN/LOSE 110 帧文字已入队）再返回，finishSession 的屏障②才能确实等到胜负文字播完
        try {
            shown.await(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** 投喂线程调用：强制排空实况队列并等待消化完毕，用同步屏障使快进落点在复位 instantPlace 前每条消息都已瞬时落位，杜绝逃逸到动画分支重播。 */
    void drainAndSettle() {
        p.engine.drainReplayQueueSync(3000L);
        waitForPendingDrain();
    }

    /** 等待实况管线空闲：pendingMsgs 排空且动画闸门开启（动画/特效全部播完）；重排抢占时提前返回。 */
    void waitForPipelineIdle() {
        long deadline = System.currentTimeMillis() + 8000L;
        while (p.isRunning && !p.isRestarting) {
            if (!p.engine.hasPendingMsgs() && !p.engine.isAnyAnimationBusy()) return;
            if (System.currentTimeMillis() > deadline) return;   // 动画兜底超时，防卡死
            try {
                Thread.sleep(16);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    /** 仅等待队列消化（不要求动画空闲）：快进落点/终止收尾用。 */
    void waitForPendingDrain() {
        long deadline = System.currentTimeMillis() + 3000L;
        while (p.isRunning && p.engine.hasPendingMsgs() && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    /** 收尾专用等待：实况队列排空且全部动画播完才返回，不依赖 isRunning，超时兜底防卡死，pump 线程被 interrupt 时立即返回。 */
    void waitForAnimationsSettled(long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (!p.engine.hasPendingMsgs() && !p.engine.isAnyAnimationBusy()) return;
            try {
                Thread.sleep(16);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /** 切片 UPDATE_DATA 前等实况管线消化完已投喂消息（有限等待，保证区域卡数与实况一致）。 */
    void awaitDispatchDrain() {
        long deadline = System.currentTimeMillis() + 2000L;
        while (p.isRunning && !p.engine.isMsgQueueIdle() && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(5);
            } catch (InterruptedException e) {
                return;
            }
        }
    }
}
