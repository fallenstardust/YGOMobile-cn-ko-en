package cn.garymb.ygomobile.network.server;

import android.os.SystemClock;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.engine.NativeScriptBootstrap;
import cn.garymb.ygomobile.engine.OcgDuelEngine;
import cn.garymb.ygomobile.network.YGOProtocol;

/**
 * 决斗引擎生命周期，移植 {@code Classes/gframe/single_duel.cpp} 的开局/应答/计时/结束
 * （TPResult 起 → create_duel_v2/set_player_info/new_card/start_duel → Process 循环 →
 * GetResponse/TimeConfirm/TimerTick/Surrender/EndDuel/DuelEndProc）。
 *
 * <p>引擎消息解析（Analyze）与区域刷新（Refresh*）遮蔽逻辑委托给 {@link DuelAnalyzer}；房间共享状态
 * （players/pplayer/observers/hostInfo/decks/duelStage/match 系列/timeLimit）读写自 {@link GameRoom}。
 * 引擎非线程安全，本类所有方法均在 {@link LanGameServer} 的单线程房间执行器上串行调用；
 * {@link #startDuel} 内联运行 {@link #process} 直到引擎等待玩家应答。
 */
final class ServerDuel extends ServerDuelBase {

    ServerDuel(GameRoom room) {
        this.room = room;
        this.analyzer = new DuelAnalyzer(this);
    }

    // ==================================================================
    // 撤回操作（undo）：本工程扩展——局域网房间内回退「最近一个完整动作」
    // ==================================================================

    /**
     * CTOS_UNDO：把局面回退到本席位最近一个动作宣言之前（引擎按开局 seed 重建 + 重放更早应答）；可连续多次，直到 {@link #undoSteps} 再无动作锚点（不越过本局首个动作，match 换边每局新栈天然隔离）。
     * <p>撤回单位是一个完整动作：栈中仅 IDLECMD/BATTLECMD 应答及回合玩家在 DP/SP 以 CHAIN/EFFECTYN 挂出的几步是「动作锚点」，锚点后的连锁/选格/表示形式/对方应答均属该动作处理过程，一次撤回连带丢弃多步。
     * <p>权限：最近锚点由请求者宣言才回退（谁操作谁撤回；对方连锁应答非锚点不关本方窗口，但对方一旦新宣言则本方窗口即关）；solo 任意锚点算本方；观战（type&gt;1）拒绝。
     * <p>同步：不重发 MSG_START（客户端会 field.clear 归零回合），改先 STOC_UNDO_ACK（带回合/玩家/阶段）再 MSG_RELOAD_FIELD 全量重载 + 各区域 refresh + 重挂待答询问；重同步不写录像。
     * <p>录像：清空已录应答/消息帧，由静默重放整段重录，产物与“那一步从未做过”逐帧等价。
     */
    void undoLastResponse(ServerConnection requester) {
        if (requester == null || requester.type > 1 || pduel == 0L
                || room.duelStage != DUEL_STAGE_DUELING || undoSteps.isEmpty()) {
            Log.i(TAG, "undo 拒绝：duelStage=" + room.duelStage + " pduel=" + pduel
                    + " steps=" + undoSteps.size() + " requesterType="
                    + (requester == null ? -1 : requester.type));
            denyUndo(requester);
            return;
        }
        // 回退目标 = 栈中最近一个动作锚点，且必须由本席位宣言（该锚点及其之后的全部步骤一并丢弃）；
        // 最近锚点属对方（单纯对方操作）或栈中根本无锚点（只有抽卡/连锁应答等非宣言步骤）时拒绝。
        int target = lastIndexOfOwnAnchor(requester);
        if (target < 0) {
            Log.i(TAG, "undo 不受理：最近锚点非本方宣言或栈中无动作锚点（steps=" + undoSteps.size()
                    + ", requester=" + requester.type + "）");
            denyUndo(requester);
            return;
        }
        // 对方席位此刻正握着待应答询问（AI 算连锁 / 对方选卡）时原则上不受理撤回：回退点落在本方行动询问上、对方询问不在重挂范围会被凭空抹掉，
        // 其错拍应答使引擎喂入非法值引发 retry 风暴或直接走偏；solo 与本方自己被询问不受此限。
        // 例外（防御性放行）：待答席位正是本次回退锚点的应答方且该锚点是栈中最后一步时，重挂询问与其手上逐字节相同，放行不会错拍。
        UndoStep targetStep = undoSteps.get(target);
        boolean askIsRollbackTarget = target == undoSteps.size() - 1
                && room.lastResponse == targetStep.responder;
        ServerConnection asking = room.lastResponse >= 0 && room.lastResponse < room.players.length
                ? room.players[room.lastResponse] : null;
        if (asking != null && asking != requester && !askIsRollbackTarget
                && (asking.state == CTOS_RESPONSE || asking.state == CTOS_TIME_CONFIRM)) {
            Log.i(TAG, "undo 拒绝：对方席位 " + room.lastResponse + " 正在待应答，不得打断其询问");
            denyUndo(requester);
            return;
        }
        UndoStep step = targetStep;
        // 计时快照：回退到 step 应答之前的值；重建失败时恢复回现值
        int tl0 = room.timeLimit[0];
        int tl1 = room.timeLimit[1];
        int te = room.timeElapsed;
        ArrayList<UndoStep> before = new ArrayList<>(undoSteps);
        undoSteps.subList(target, undoSteps.size()).clear();
        int keep = undoSteps.size();
        replay.resetRecordStream();
        room.timeLimit[0] = step.timeLimit0;
        room.timeLimit[1] = step.timeLimit1;
        room.timeElapsed = step.timeElapsed;
        if (rebuildAndReplay(keep)) {
            Log.i(TAG, "undo 成功：回退 " + (before.size() - keep) + " 步（重放到第 " + keep
                    + " 步为止），回合="
                    + analyzer.silentTurnCount + " player=" + analyzer.silentTurnPlayer
                    + " phase=" + Integer.toHexString(analyzer.silentPhase));
            sendUndoAckToAll(UNDO_ACK_OK, analyzer.silentTurnCount,
                    analyzer.silentTurnPlayer, analyzer.silentPhase,
                    analyzer.silentChainEvents);
            analyzer.resyncAfterUndo(OcgDuelEngine.queryFieldInfo(pduel));
            scheduleUndoQuestionHang();
            sendUndoStateToAll();
            return;
        }
        // 回退重建失败（理论上不应发生）：把弹掉的步骤全部加回去，按撤回前的局面整体重建，局面不丢
        Log.e(TAG, "undo 回退重建失败，按撤回前局面重建");
        undoSteps.clear();
        undoSteps.addAll(before);
        replay.resetRecordStream();
        room.timeLimit[0] = tl0;
        room.timeLimit[1] = tl1;
        room.timeElapsed = te;
        if (rebuildAndReplay(undoSteps.size())) {
            sendUndoAckToAll(UNDO_ACK_REBUILT, analyzer.silentTurnCount,
                    analyzer.silentTurnPlayer, analyzer.silentPhase,
                    analyzer.silentChainEvents);
            analyzer.resyncAfterUndo(OcgDuelEngine.queryFieldInfo(pduel));
            scheduleUndoQuestionHang();
            sendUndoStateToAll();
        } else {
            // 两次重建均失败：引擎句柄已不可用，只能告知客户端撤回无效
            Log.e(TAG, "undo 兜底重建也失败：引擎重建异常，本局不可继续");
            denyUndo(requester);
        }
    }

    /**
     * 按开局 seed 重建引擎，静默（只录不发）重放前 {@code keep} 条应答，回到“做过 keep 步”的局面。
     * 重放过程逐条重新写录像，与首跑序列一致（同 seed + 同应答的决定性引擎）。
     *
     * @return true = 重建并重放成功，且已回到“等待应答”状态
     *         （{@link DuelAnalyzer#silentTerminal} 已捕获待重发的询问消息）
     */
    protected boolean rebuildAndReplay(int keep) {
        if (undoSeed == null || undoStartFrame == null || replay == null) {
            return false;
        }
        OcgDuelEngine.endDuel(pduel);
        pduel = 0L;
        long fresh = OcgDuelEngine.createDuelV2(undoSeed);
        if (fresh == 0L) {
            return false;
        }
        pduel = fresh;
        GameRoom.HostInfo hi = room.hostInfo;
        analyzer.beginSilentReplay();
        boolean ok;
        try {
            OcgDuelEngine.setPlayerInfo(pduel, 0, hi.startLp, hi.startHand, hi.drawCount);
            OcgDuelEngine.setPlayerInfo(pduel, 1, hi.startLp, hi.startHand, hi.drawCount);
            // 卡组装载：录像 base 段（双方卡组名与卡码）已在首次开局写好，只喂引擎不再写
            baseRecordFrozen = true;
            try {
                if (room.tagMode) {
                    // 装载顺序与 {@link #startDuel} 严格一致（洗牌后的 room.decks 序未变）
                    loadOneZone(room.decks[0].main, 0, OcgDuelEngine.LOCATION_DECK);
                    loadOneZone(room.decks[0].extra, 0, OcgDuelEngine.LOCATION_EXTRA);
                    loadOneZoneTag(room.decks[1].main, 0, OcgDuelEngine.LOCATION_DECK);
                    loadOneZoneTag(room.decks[1].extra, 0, OcgDuelEngine.LOCATION_EXTRA);
                    loadOneZone(room.decks[3].main, 1, OcgDuelEngine.LOCATION_DECK);
                    loadOneZone(room.decks[3].extra, 1, OcgDuelEngine.LOCATION_EXTRA);
                    loadOneZoneTag(room.decks[2].main, 1, OcgDuelEngine.LOCATION_DECK);
                    loadOneZoneTag(room.decks[2].extra, 1, OcgDuelEngine.LOCATION_EXTRA);
                } else {
                    loadDeckToEngine(room.decks[0], 0);
                    loadDeckToEngine(room.decks[1], 1);
                }
            } finally {
                baseRecordFrozen = false;
            }
            // 消息流：开局的 MSG_START 帧与两次 extra 刷新在原录像中存在，清流后需按原序补回
            replay.writeMessage(undoStartFrame, undoStartFrame.length);
            analyzer.refreshExtra(0);
            analyzer.refreshExtra(1);
            OcgDuelEngine.startDuel(pduel, undoOpt);
            ok = processSilent();
            for (int i = 0; ok && i < keep; i++) {
                UndoStep s = undoSteps.get(i);
                replay.writeResponse(s.resb, s.len);
                OcgDuelEngine.setResponseB(pduel, s.resb);
                ok = processSilent();
            }
            // 回退点必须是“引擎在等应答”，否则无询问可重挂，客户端会挂在无操作局面
            ok = ok && analyzer.silentTerminal != null;
        } finally {
            analyzer.endSilentReplay();
        }
        return ok;
    }

    /**
     * 静默版 {@link #process()}：取消息循环完全同构，但跑到终局（MSG_WIN，回退点不应在终局之后）
     * 则返回 false 交调用方判定重建失败。调用前必须已处于静默态。
     */
    protected boolean processSilent() {
        int engFlag = 0;
        int stop = 0;
        while (stop == 0) {
            if (engFlag == OcgDuelEngine.PROCESSOR_END) {
                break;
            }
            int result = OcgDuelEngine.process(pduel);
            int engLen = result & OcgDuelEngine.PROCESSOR_BUFFER_LEN;
            engFlag = result & OcgDuelEngine.PROCESSOR_FLAG;
            if (engLen > 0) {
                byte[] msg = OcgDuelEngine.getMessage(pduel);
                stop = analyzer.analyze(msg, msg.length);
            }
        }
        return stop != 2;
    }

    /** MSG_RETRY：本次应答未被引擎接受（重问），它不构成可撤回的一步，弹出快照。 */
    void discardPendingUndoStep() {
        // 静默重放（撤回重建）中的 RETRY 不得动快照栈：那一步早已定格，弹了会错位
        if (analyzer.silent) {
            return;
        }
        int n = undoSteps.size();
        if (n > 0) {
            UndoStep popped = undoSteps.remove(n - 1);
            // 重问的仍是同一个询问：它若是行动宣言（IDLE/BATTLE CMD），锚点属性须随被弹掉的
            // 那一步一并回填，否则玩家改口后的正确应答会被记成普通步骤而撤不回
            analyzer.pendingActionQuestion = popped.anchorType;
        }
    }

    protected void denyUndo(ServerConnection requester) {
        if (requester != null) {
            requester.send(STOC_UNDO_ACK, undoAckPayload(UNDO_ACK_DENIED, 0, 0, 0, 0));
        }
    }

    /** 把撤回结果推给全体在场连接（solo 一条连接占两席位，按连接身份去重）。 */
    protected void sendUndoAckToAll(int result, int turn, int player, int phase, int chainEvents) {
        byte[] ack = undoAckPayload(result, turn, player, phase, chainEvents);
        ServerConnection p0 = room.players[0];
        ServerConnection p1 = room.players[1];
        if (p0 != null) {
            p0.send(STOC_UNDO_ACK, ack);
        }
        if (p1 != null && p1 != p0) {
            p1.send(STOC_UNDO_ACK, ack);
        }
        for (ServerConnection o : room.observers) {
            o.send(STOC_UNDO_ACK, ack);
        }
    }

    /** STOC_UNDO_ACK 载荷：{@code [result(1B)][turn(1B)][currentPlayer(1B)][phase(u16 LE)][chainEvents(i32 LE)]}。
     *  chainEvents = 回退后重放前缀内的「有效效果发动」条数（与客户端 EffectActivationLedger
     *  登记同口径），客户端把账本超出该条数的后缀条目剪除（撤回连带回退已使用效果标记）；
     *  拒绝态无重放，5=无后续重同步语义不变。 */
    protected static byte[] undoAckPayload(int result, int turn, int player, int phase, int chainEvents) {
        ByteBuffer b = ByteBuffer.allocate(9).order(ByteOrder.LITTLE_ENDIAN);
        b.put((byte) result);
        b.put((byte) turn);
        b.put((byte) player);
        b.putShort((short) phase);
        b.putInt(chainEvents);
        return b.array();
    }

    /**
     * 延后把回退点的待应答询问挂回席位（重载与刷新已在 {@link DuelAnalyzer#resyncAfterUndo}
     * 里发完，两席位状态也已复位）。不立刻重挂的理由：
     * <ul>
     *   <li>被丢弃那一段里可能已有应答在路上——人机局的 AI 作为独立进程，它针对旧连锁询问算完的
     *       答复不受本机应答屏障约束，晚到一步就会被当成对新询问的应答喂进引擎；</li>
     *   <li>客户端刚收到全量重载，旧弹窗的关闭与自动应答的取消都在主线程队列上排队，先挂询问
     *       会让这些残留在新询问到达前后抢发出去。</li>
     * </ul>
     * 留白期间两席位都是 STATE_NONE，任何应答都被状态门丢弃；留白结束后才挂出询问，
     * 此时界面上只可能存在本次新局面派下来的弹窗与命令列表，应答链路重新干净。
     * 重挂任务回投到单线程房间执行器上，与应答/撤回串行；代次比较使连续撤回时旧任务作废。
     */
    protected void scheduleUndoQuestionHang() {
        if (analyzer.undoAskResponder < 0) {
            return;
        }
        final int gen = ++undoHangGen;
        ScheduledExecutorService exec = undoHangTimer;
        if (exec == null || exec.isShutdown()) {
            exec = Executors.newSingleThreadScheduledExecutor();
            undoHangTimer = exec;
        }
        exec.schedule(() -> room.server.post(() -> {
            if (gen != undoHangGen || pduel == 0L || room.duelStage != DUEL_STAGE_DUELING) {
                return;
            }
            analyzer.hangUndoTerminalQuestion();
        }), UNDO_HANG_DELAY_MS, TimeUnit.MILLISECONDS);
        // 重挂之后开一路保活：延后重挂跑在另一个线程上（回投房间执行器），它可能因执行器已关、
        // 代次作废、或客户端把询问包吞在动画闸门里而落空；落空就是永久卡死（界面无询问、点卡弹不
        // 出发动/召唤/盖放菜单，但墓地/除外区这类本地列表照旧可看）
        startUndoQuestionKeepAlive(exec, gen);
    }

    /**
     * 撤回重挂询问的保活：每秒复查一次，仅对「撤回重挂出去且仍无人应答」那一条询问生效，
     * 超过 {@link #QUESTION_KEEPALIVE_GRACE_MS} 即原样重发（最多
     * {@link #QUESTION_KEEPALIVE_MAX_REDELIVERIES} 次）。留白期（{@code undoAskResponder} 仍待重挂）
     * 跳过，以免把属于被撤销那一段的旧询问抢先挂回。
     */
    protected void startUndoQuestionKeepAlive(ScheduledExecutorService exec, final int gen) {
        if (questionKeepAliveTask != null) {
            questionKeepAliveTask.cancel(false);
        }
        questionKeepAliveTask = exec.scheduleWithFixedDelay(() -> room.server.post(() -> {
            if (gen != undoHangGen || pduel == 0L || room.duelStage != DUEL_STAGE_DUELING) {
                return;
            }
            if (analyzer.undoAskResponder >= 0) {
                return;
            }
            analyzer.beatPendingQuestion(System.currentTimeMillis(),
                    QUESTION_KEEPALIVE_GRACE_MS, QUESTION_KEEPALIVE_MAX_REDELIVERIES);
        }), UNDO_HANG_DELAY_MS + QUESTION_KEEPALIVE_TICK_MS,
                QUESTION_KEEPALIVE_TICK_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * 待答席位的应答被状态门吞掉时由 {@link GameRoom#handlePacket} 回调（自愈入口）。
     *
     * <p>这是“撤回后再发动一次就推不下去”的真正形状：客户端认为自己已经把那个询问答了（弹窗已收、
     * 命令列表已清），而服务端引擎那一问并未答掉——于是界面不再挂任何询问，点卡弹不出发动/召唤/盖放，
     * 而墓地/除外/额外属于本地列表所以照旧能看，双方永久互等。把当前询问连等待态一并重挂即可恢复；
     * 本方法不改写引擎、不写录像，只重发一份询问字节。
     */
    void onResponseDropped(ServerConnection conn) {
        if (pduel == 0L || room.duelStage != DUEL_STAGE_DUELING
                || conn == null || conn.type < 0 || conn.type > 1) {
            return;
        }
        // 撤回的延后重挂仍在路上：此刻被吞的应答本就由那次重挂接管，不可拿撤回前的旧询问抢先挂回
        if (analyzer.undoAskResponder >= 0) {
            return;
        }
        // 只修“引擎此刻等的正是这个席位”：其余情形的被吞应答属于已被回退的那一段，本就应当丢弃
        if (room.lastResponse != conn.type) {
            return;
        }
        if (!analyzer.canRedeliverQuestion(QUESTION_KEEPALIVE_MAX_REDELIVERIES)) {
            return;
        }
        Log.w(TAG, "awaited seat " + conn.type
                + " response swallowed by state gate: re-hang its question");
        analyzer.rehangPendingQuestion("dropped response");
    }

    /**
     * CTOS_ASK_RESEND：客户端索取「引擎此刻挂在我这个席位上的那条询问」的重发
     * （本工程扩展，仅在服务端声明 {@code HOST_CAP_ASK_RESEND} 时客户端才会发）。
     *
     * <p>它补掉了保活心跳与状态门自愈都管不到的那一类：询问确实被服务端挂出、席位状态也正常，
     * 但那一条报文没能到达客户端（或到了客户端被吞），于是客户端界面手上没有询问、服务端引擎
     * 在等一个永远不会来的答复——双方永久互等，症状是点卡片弹不出发动/召唤/盖放菜单，而墓地、
     * 除外区、额外卡组这类本地列表照旧可看。心跳只管撤回重挂那一条询问（怕打扰长考的玩家），
     * 而客户端自己知道「我刚答完、之后一个字也没收到」，由它来触发这一步既精确又不会误伤正常节奏。
     *
     * <p>与应答、撤回同在这条单线程房间执行器上串行，故不会与 {@code getResponse} 抢引擎；
     * 询问是否仍是「当前这一条」由 {@link DuelAnalyzer#hasLiveAsk()} 的代次配对把关，过期的旧询问
     * 绝不会被挂回。
     */
    void onAskResendRequest(ServerConnection conn) {
        if (pduel == 0L || room.duelStage != DUEL_STAGE_DUELING) {
            return;
        }
        // solo 一条连接占两席位，引擎等待方按 room.lastResponse 计（与 getResponse 同一套换算）
        int responder = room.soloMode ? room.lastResponse : (conn == null ? -1 : conn.type);
        if (conn == null || conn.type < 0 || conn.type > 1 || responder != room.lastResponse) {
            Log.i(TAG, "ask-resend from seat " + (conn == null ? -1 : conn.type)
                    + " ignored: engine is waiting on " + room.lastResponse);
            return;
        }
        if (analyzer.undoAskResponder >= 0) {
            // 撤回的延后重挂已在路上，那次重挂本就负责把询问挂出来
            return;
        }
        if (!analyzer.canRedeliverQuestion(ASK_RESEND_MAX_REDELIVERIES)) {
            Log.w(TAG, "ask-resend denied: no live question cached for seat " + responder
                    + " (or redelivery limit reached)");
            return;
        }
        analyzer.rehangPendingQuestion("client ask-resend");
    }
}
