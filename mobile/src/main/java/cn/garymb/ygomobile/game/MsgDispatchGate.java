package cn.garymb.ygomobile.game;

import android.util.Log;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import cn.garymb.ygomobile.utils.CrashHandler;
import ocgcore.enums.GameMessage;

/** 消息串行闸门（duelclient.cpp ClientAnalyze + WaitFrameSignal）：入队派发、动画/对话框持闸、回放快进排空与观战跳当前；自 GameEngine 拆出，共享状态仍留在引擎，构造注入引擎引用。 */
class MsgDispatchGate {
    private static final String TAG = "GameEngine";

    /** 闸门兜底超时：万一特效队列因异常未排空，超时后强制重开，避免消息永久卡死 */
    private static final long ANIM_GATE_TIMEOUT_MS = 8000L;

    private final GameEngine engine;

    MsgDispatchGate(GameEngine engine) {
        this.engine = engine;
    }

    /** STOC_GAME_MSG 入队（duelclient.cpp ClientAnalyze 调用点），入队后由闸门串行派发 */
    void enqueueGameMsg(int msgType, ByteBuffer data) {
        data.order(ByteOrder.LITTLE_ENDIAN);
        // 询问自愈：收到挂给本席位的询问或「等对方」提示即证明服务端仍在正常推进对局
        if (AskWatchdog.isAskOrWaitingMessage(msgType)) {
            engine.askWatchdog.notifyAskReceived();
        }
        // 对齐 duelclient.cpp L1298-1303：除 MSG_RETRY 外每条消息均缓存一份快照，供 RETRY（无效应答）后重放重建选择 UI（服务端不会重发原 SELECT）
        if (msgType != GameMessage.Retry.value()) {
            ByteBuffer snapshot = data.duplicate();
            byte[] body = new byte[snapshot.remaining()];
            snapshot.get(body);
            engine.lastGameMsgType = msgType;
            engine.lastGameMsgBody = body;
            engine.retryReplayCount = 0;
            // 单独留存最近一条询问（MSG_SELECT_* = 10..26）快照：撤回被拒/超时时据此重派发询问
            if (msgType >= GameMessage.SelectBattleCmd.value()
                    && msgType <= GameMessage.SelectUnselectCard.value()) {
                engine.undoCoord.lastQuestionType = msgType;
                engine.undoCoord.lastQuestionBody = body;
            }
            engine.recordMsgFrame(msgType, body);
        }
        // 入队后由闸门串行派发：动画消息会关闭闸门，暂缓后续消息（对齐 C++ WaitFrameSignal 阻塞语义）
        engine.pendingMsgs.offer(() -> dispatchGameMsg(msgType, data));
        drainPendingMsgs();
    }

    /** MSG_RETRY 后重放上一条消息（duelclient.cpp L1351-1404）：重建被无效应答打断的选择 UI；连续重放上限 3 次 */
    void replayLastGameMsg() {
        if (engine.lastGameMsgType < 0 || engine.lastGameMsgBody == null) {
            // 服务端现在会直接重发询问（见 DuelAnalyzer 的 MSG_RETRY 分支），本方法仅剩兜底作用；兜底也拿不到任何快照时界面将再也等不到询问，此处必须落盘现场而非静默 return
            if (engine.undoCoord.lastQuestionType >= 0 && engine.undoCoord.lastQuestionBody != null) {
                engine.undoCoord.restorePendingQuestionUi();
                return;
            }
            Log.w(TAG, "retry: no cached message to replay (undo cleared it and no question snapshot)");
            return;
        }
        if (++engine.retryReplayCount > 3) {
            Log.e(TAG, "replayLastGameMsg: retry replay limit exceeded, give up (msgType="
                    + engine.lastGameMsgType + ")");
            return;
        }
        ByteBuffer buf = ByteBuffer.wrap(engine.lastGameMsgBody);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        // 直接入队但不刷新缓存（保留原快照以便再次重放），故不经 enqueueGameMsg 的缓存分支
        engine.pendingMsgs.offer(() -> dispatchGameMsg(engine.lastGameMsgType, buf));
        drainPendingMsgs();
    }

    /** 实际派发单条通讯消息（对齐 duelclient.cpp ClientAnalyze 主体） */
    void dispatchGameMsg(int msgType, ByteBuffer data) {
        // 对齐 duelclient.cpp L1307-1311：仅非回放时，除 MSG_WAITING/MSG_CARD_SELECTED 外每条通讯消息开始先停止等待动画并隐藏 stHintMsg（提示栏随通讯推进实时显隐；回放链路与 Java 回放均不显示决斗提示，保持一致）
        if (!engine.replayMode && msgType != GameMessage.Waiting.value() && msgType != GameMessage.CardSelected.value()) {
            engine.hintManager.stopWaitHint();
            engine.hintManager.postDuelHintHide();
        }
        try {
            // 场地事件 handler = DuelEventHandler（实现 MessageHandler；选择/提示类转发 messageParser，召唤动画类转发 summonAnim）
            GameMessageParser.parse(msgType, data, engine.duelEvents);
        } catch (BufferUnderflowException e) {
            Log.e(TAG, "Failed to parse game message type=" + msgType + ", remaining=" + data.remaining(), e);
        } catch (RuntimeException | Error e) {
            // 崩溃挂钩：主线程派发链路（含回放投喂的每条消息、观战与联机对局）异常先带消息号落盘 ygocore/log，再原样抛出交给全局 CrashHandler，既不改变崩溃行为又留下现场
            CrashHandler.getInstance().report("dispatchGameMsg type=" + msgType
                    + (engine.replayMode ? " (replay step)" : " (live)"), e);
            throw e;
        }
    }

    /** 串行派发待处理消息：闸门开启时逐条处理，某条触发动画（closeAnimGate）则停止循环 */
    void drainPendingMsgs() {
        if (engine.dispatchingMsg || engine.animGateClosed) return;
        engine.dispatchingMsg = true;
        try {
            while (true) {
                // 观战「切换视角」：延迟到消息消费点执行（对齐 duelclient.cpp is_swapping → ClientAnalyze 内 ReplaySwap），与消息处理同在主线程派发链上，不与字段读写竞争
                if (engine.pendingSpectatorSwap) {
                    engine.pendingSpectatorSwap = false;
                    engine.viewpoint.performSpectatorSwap();
                }
                Runnable task = engine.pendingMsgs.poll();
                if (task == null) break;
                task.run();
                // 泛化动画屏障：本条消息若同步触发了场地卡片移动/淡入淡出（moveCardAnimated 立即置 aniFrame）或居中特效（startCard→startLoop 立即置 running），随即关闭闸门暂缓后续消息，待动画播完（轮询器或 idle 快路径重开）再继续——对齐 C++ 每条动画消息后的 WaitFrameSignal；回放快进（replaySkip）中无视残留动画持续排空：落点即终态、instantPlace 已把旧动画掐掉，若此处关闸会让投喂线程的 awaitDispatchDrain 陪动画节奏爬行，undo 呈现为「从头重播动画」
                if (!engine.replaySkip && isAnyAnimationBusy()) {
                    closeAnimGate();
                    break;
                }
                // 确认卡片对话框持闸（MSG_CONFIRM_CARDS 在派发链上同步 holdMsgGateForDialog）：闸门已关但无卡片动画在播，循环不会经上面分支退出，此处据 animGateClosed 断链——对齐 C++ panel_confirm 显示后 actionSignal.Wait() 阻塞消息线程，不点确定后续 msg 一律不派发
                if (engine.animGateClosed) break;
            }
        } finally {
            engine.dispatchingMsg = false;
        }
    }

    /** 关闭动画闸门：启动 16ms 轮询器检测动画是否播完，idle 回调为快路径，另设超时兜底 */
    private void closeAnimGate() {
        if (engine.animGateClosed) return;
        engine.animGateClosed = true;
        engine.mainHandler.removeCallbacks(engine.animGateFailsafe);
        engine.mainHandler.postDelayed(engine.animGateFailsafe, ANIM_GATE_TIMEOUT_MS);
        engine.mainHandler.removeCallbacks(engine.animGatePoller);
        engine.mainHandler.postDelayed(engine.animGatePoller, GameEngine.ANIM_GATE_POLL_MS);
    }

    /** 重开动画闸门：清轮询器与超时兜底，继续派发被暂缓的后续消息 */
    void reopenAnimGate() {
        if (!engine.animGateClosed) return;
        if (engine.dialogGateHeld) return; // 对话框持闸期间不得因动画空闲/特效 idle 回调而重开
        engine.animGateClosed = false;
        engine.mainHandler.removeCallbacks(engine.animGatePoller);
        engine.mainHandler.removeCallbacks(engine.animGateFailsafe);
        drainPendingMsgs();
    }

    /** 确认卡片对话框显示时持有消息闸门：对齐 C++ MSG_CONFIRM_CARDS（player_type!=7 显示面板后 actionSignal.Reset(); actionSignal.Wait();）——不点确定则后续通讯一律挂起，关闭轮询器/超时兜底避免动画播完后自动重开 */
    void holdMsgGateForDialog() {
        engine.dialogGateHeld = true;
        engine.mainHandler.removeCallbacks(engine.animGatePoller);
        engine.mainHandler.removeCallbacks(engine.animGateFailsafe);
        engine.animGateClosed = true;
    }

    /** 确认卡片对话框点确定后释放闸门：解除持闸标志并重开，继续派发被暂缓的后续消息 */
    void releaseMsgGateForDialog() {
        if (!engine.dialogGateHeld) return;
        engine.dialogGateHeld = false;
        engine.animGateClosed = false;
        engine.mainHandler.removeCallbacks(engine.animGatePoller);
        engine.mainHandler.removeCallbacks(engine.animGateFailsafe);
        drainPendingMsgs();
    }

    /** 统一动画屏障：场地卡片动画、LP 变化动画与居中特效任一在播即 true */
    boolean isAnyAnimationBusy() {
        // 定时屏障持有期（MSG_ATTACK 弧光展示等）：未到期一律视为忙，串行化后续消息
        if (System.currentTimeMillis() < engine.animHoldUntilMs) return true;
        boolean fieldBusy = false;
        try {
            // isAnimating()=卡片移动；isLpAnimating()=LP 浮字/血条动画（对齐 duelclient.cpp MSG_DAMAGE/RECOVER/PAY_LPCOST 的 WaitFrameSignal(30)+(11)、MSG_LPUPDATE 的 WaitFrameSignal(11)）
            fieldBusy = engine.field != null && (engine.field.isAnimating() || engine.field.isLpAnimating());
        } catch (Throwable ignored) {
        }
        boolean overlayBusy = false;
        try {
            overlayBusy = engine.listener != null && engine.listener.isSpecEffectBusy();
        } catch (Throwable ignored) {
        }
        return fieldBusy || overlayBusy;
    }

    /** 待派发消息队列是否仍有存量（回放投喂线程据此等待实况管线消化完再喂下一条） */
    boolean hasPendingMsgs() {
        return !engine.pendingMsgs.isEmpty();
    }

    /** 队列已空且主线程未处于派发中：实况侧场地数据已追上游标位置（回放切片前对齐卡数用） */
    boolean isMsgQueueIdle() {
        return engine.pendingMsgs.isEmpty() && !engine.dispatchingMsg;
    }

    /** 回放快进专用：强制重开闸门并在主线程一次性排空 pendingMsgs（忽略动画占用） */
    void drainReplayQueueNow() {
        engine.mainHandler.post(() -> {
            engine.animGateClosed = false;
            engine.animHoldUntilMs = 0;
            engine.mainHandler.removeCallbacks(engine.animGatePoller);
            engine.mainHandler.removeCallbacks(engine.animGateFailsafe);
            engine.dispatchingMsg = true;
            try {
                Runnable r;
                while ((r = engine.pendingMsgs.poll()) != null) {
                    r.run();
                }
            } finally {
                engine.dispatchingMsg = false;
            }
        });
    }

    /** 回放快进落点专用同步屏障：与 drainReplayQueueNow 同体但额外阻塞投喂线程直到该排空 Runnable 真正在主线程执行完才返回——投喂侧每条 feedMessage 都是 post 到 mainHandler FIFO 的 Runnable，主线程处理进度可能落后于 post 进度，仅轮询 hasPendingMsgs() 会在「feedMessage Runnable 尚在队列、pendingMsgs 瞬时为空」时提前返回，使调用方在其后 endInstantSkip 复位 instantPlace 前未能派发完全部快进消息，起手 MSG_DRAW 遂在 instantPlace=false 时走动画分支、解冻后呈现错误的「回放起手抽卡动画」；本屏障借助 FIFO 保证排空 Runnable 必然晚于此前 post 的全部 feedMessage 执行完，即快进期每条消息都在 instantPlace=true 下瞬时落位；@param timeoutMs 兜底超时防主线程异常时投喂线程永久阻塞，true=派发追平，false=超时/中断按尽力排空处理 */
    boolean drainReplayQueueSync(long timeoutMs) {
        final CountDownLatch done = new CountDownLatch(1);
        engine.mainHandler.post(() -> {
            try {
                engine.animGateClosed = false;
                engine.animHoldUntilMs = 0;
                engine.dialogGateHeld = false; // 快进重排不受确认卡片对话框持闸影响
                engine.mainHandler.removeCallbacks(engine.animGatePoller);
                engine.mainHandler.removeCallbacks(engine.animGateFailsafe);
                engine.dispatchingMsg = true;
                try {
                    Runnable r;
                    while ((r = engine.pendingMsgs.poll()) != null) {
                        r.run();
                    }
                } finally {
                    engine.dispatchingMsg = false;
                }
            } finally {
                done.countDown();
            }
        });
        try {
            return done.await(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /** 特效队列排空回调（SpecEffectOverlay idle）：重开闸门快路径，仍需 isAnyAnimationBusy() 复核 */
    void notifySpecEffectIdle() {
        if (!engine.animGateClosed) return;
        if (isAnyAnimationBusy()) return;
        reopenAnimGate();
    }

    /** 观战「跳到当前」：无视动画，把当前已收到的全部 pendingMsgs 一次性即时落位到最后一条 msg 的局面令观战者无需再看前面动画；复用回放快进语义（replaySkip + field.instantPlace + 音效静默）与 ReplayPlayer.beginInstantSkip/endInstantSkip 同构，仅观战生效，排空后恢复常规节奏后续新到消息仍按正常动画播放 */
    void spectatorSkipToCurrent() {
        if (!engine.isSpectator()) return;
        engine.mainHandler.post(() -> {
            engine.dialogGateHeld = false; // 观战本就不持确认卡片闸门，防御性复位
            engine.replaySkip = true;
            engine.field.instantPlace = true;
            if (engine.soundManager != null) engine.soundManager.setEffectsSuppressed(true);
            engine.animGateClosed = false;
            engine.animHoldUntilMs = 0;
            engine.mainHandler.removeCallbacks(engine.animGatePoller);
            engine.mainHandler.removeCallbacks(engine.animGateFailsafe);
            // 一次性排空当前队列（忽略动画占用），落到最新局面
            engine.dispatchingMsg = true;
            try {
                Runnable r;
                while ((r = engine.pendingMsgs.poll()) != null) r.run();
            } finally {
                engine.dispatchingMsg = false;
            }
            // 还原正常播放节奏
            engine.replaySkip = false;
            engine.field.instantPlace = false;
            if (engine.soundManager != null) engine.soundManager.setEffectsSuppressed(false);
            engine.animHoldUntilMs = 0;
            // 按最终局面的坐标强制对齐所有卡面（instantPlace 已直接落位，此处重刷避免残留插值坐标）
            engine.field.refreshAllCards();
            if (engine.listener != null) {
                engine.listener.onFieldChanged();
                engine.listener.onPlayerInfoUpdated(0);
                engine.listener.onPlayerInfoUpdated(1);
            }
        });
    }
}
