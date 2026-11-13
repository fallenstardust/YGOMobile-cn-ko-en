package cn.garymb.ygomobile.network.server;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import cn.garymb.ygomobile.network.YGOProtocol;

/**
 * 出站广播 / 应答挂起域实现（自 DuelAnalyzer 平移，逻辑零改）：双视角发送并录制、
 * 统一出站口、观战广播、等待应答挂起。所有共享状态读写宿主 {@link DuelAnalyzer}（同包包私有）。
 */
final class DuelViewBroadcaster implements YGOProtocol {

    private static final String TAG = "DuelAnalyzer";

    private final DuelAnalyzer analyzer;

    DuelViewBroadcaster(DuelAnalyzer analyzer) {
        this.analyzer = analyzer;
    }

    /** 等待应答挂起：非静默时换询问代次、发 MSG_WAITING / 限时包并置席位状态。 */
    void waitforResponse(int playerid) {
        analyzer.room.lastResponse = playerid;
        if (analyzer.silent) {
            // 静默重放：仅记录“引擎此刻在等谁的哪条询问”（应答方已由上面 room.lastResponse 记下），
            // 不改连接状态、不发 MSG_WAITING/限时包
            analyzer.silentWaitPending = true;
            return;
        }
        // 新的询问正挂出去：待重挂标记与保活计数随之换一个宿主（保活只管这一条询问）
        analyzer.askGeneration++;
        analyzer.liveQuestionResponder = playerid;
        analyzer.liveQuestionAtMs = System.currentTimeMillis();
        analyzer.liveQuestionRedeliveries = 0;
        analyzer.liveQuestionFromUndo = false;
        // 引擎侧 player 对应的应答连接：真 TAG 为该队当前行动席位，其余为该座位连接
        ServerConnection acting = analyzer.room.duelPlayer(playerid);
        if (analyzer.soloMode && analyzer.room.players[0] == analyzer.room.players[1]) {
            // Solo mode: same connection; no MSG_WAITING needed, just set state
            if (acting != null) {
                acting.state = CTOS_RESPONSE;
            }
            return;
        }
        byte[] waiting = new byte[]{(byte) EngineMessage.MSG_WAITING};
        // MSG_WAITING 发给除应答席位以外的全部决斗位（对齐 tag_duel.cpp L1580-1582）
        for (ServerConnection other : analyzer.room.otherSeats(acting)) {
            other.send(STOC_GAME_MSG, waiting);
        }
        if (analyzer.room.hostInfo.timeLimit != 0) {
            int team = playerid;
            byte[] sctl = timeLimitPayload(team, analyzer.room.timeLimit[team]);
            int seats = analyzer.room.seatCount();
            for (int i = 0; i < seats; i++) {
                if (analyzer.room.players[i] != null) {
                    analyzer.room.players[i].send(STOC_TIME_LIMIT, sctl);
                }
            }
            if (acting != null) {
                acting.state = CTOS_TIME_CONFIRM;
            }
        } else if (acting != null) {
            acting.state = CTOS_RESPONSE;
        }
    }

    private static byte[] timeLimitPayload(int player, int left) {
        ByteBuffer b = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        b.put((byte) player);
        b.put((byte) 0);
        b.putShort((short) left);
        return b.array();
    }

    /**
     * 录制一帧权威（未遮蔽）引擎消息：联机录像保存双方完整卡码，使纯消息流回放
     * 能正面显示对方手牌/暗盖/除外卡；仅由需要替代 players[0] 遮蔽视角的分支调用。
     */
    void recordFrame(byte[] full) {
        if (analyzer.recordSuppressed) {
            return;
        }
        if (analyzer.owner.replay != null) {
            analyzer.owner.replay.writeMessage(full, full.length);
        }
    }

    /**
     * 双视角发送并录制完整帧：当前行动席位收 full、其余席位与观战收 masked；录像仅存 full 一条。
     * 取代这些分支原先的 sendToPlayer(owner, full) + sendToPlayer(opp, masked) +
     * sendToObservers(masked)，使录像不再泄漏 players[0] 遮蔽视图；实时发送语义与原逻辑一致。
     * <p>真 TAG 下 masked 同时覆盖同队另一席位（对齐 tag_duel.cpp 的「除 cur_player 外全部席位」）。
     */
    void sendDualViewAndRecord(int ownerPlayer, byte[] full, byte[] masked) {
        recordFrame(full);
        ServerConnection op = analyzer.room.duelPlayer(ownerPlayer);
        if (analyzer.soloMode && analyzer.room.players[0] == analyzer.room.players[1]) {
            // Solo mode: same connection is both players; send full only (masked would overwrite)
            emit(op, full);
            emitObservers(masked);
            return;
        }
        emit(op, full);
        for (ServerConnection other : analyzer.room.otherSeats(op)) {
            emit(other, masked);
        }
        emitObservers(masked);
    }

    /**
     * 按队双视角发送并录制：本队全部席位收 full、另一队全部席位与观战收 masked
     *（对齐 tag_duel.cpp RefreshMzone/RefreshSzone/RefreshSingle 的 players[pid] 与 players[pid+1] 双发）。
     * 非 TAG 时每队只有一席，与 {@link #sendDualViewAndRecord} 完全同形。
     */
    void sendTeamPairViewAndRecord(int team, byte[] full, byte[] masked) {
        recordFrame(full);
        if (analyzer.soloMode && analyzer.room.players[0] == analyzer.room.players[1]) {
            // Solo mode: 同一连接即本队全部席位，只发一份全量（masked 会覆盖它）
            for (ServerConnection c : analyzer.room.teamSeats(team)) {
                if (c != null) {
                    emit(c, full);
                    break;
                }
            }
            emitObservers(masked);
            return;
        }
        ServerConnection keep = null;
        for (ServerConnection c : analyzer.room.teamSeats(team)) {
            if (c != null) {
                emit(c, full);
                if (keep == null) {
                    keep = c;
                }
            }
        }
        for (ServerConnection other : analyzer.room.otherSeats(keep)) {
            emit(other, masked);
        }
        emitObservers(masked);
    }

    void sendToPlayer(ServerConnection dp, byte[] data) {
        if (dp == null) {
            return;
        }
        // 整局重放同步过的席位：撤回的 reload/refresh 全量重同步批不得再发给它（它们不实现 MSG_RELOAD_FIELD，
        // 重放已把它们带回回退点；重发一份会把它们已有卡的属性改坏）
        if (analyzer.undoResyncBurst && analyzer.isUndoReplaySink(dp)) {
            return;
        }
        // 录像：广播类与发给 players[0] 的提示/选择消息在此按主机视角录制（本就含完整
        // 公开信息）；含隐藏卡码的遮蔽类分支不经此路径，改由 sendDualViewAndRecord 录全量帧
        if (!analyzer.recordSuppressed && dp == analyzer.room.players[0] && analyzer.owner.replay != null) {
            analyzer.owner.replay.writeMessage(data, data.length);
        }
        if (analyzer.silent) {
            // 紧接 waitforResponse 的那条终端询问被留存，供撤回后原样重发
            if (analyzer.silentWaitPending) {
                analyzer.silentTerminal = data;
                analyzer.silentWaitPending = false;
            }
            // 整局重放：静默重放的每条消息同时按原屏蔽语义送给人机席位，它们的本地局面随之逐帧回到回退点
            if (analyzer.isUndoReplaySink(dp)) {
                dp.send(STOC_GAME_MSG, data);
                // 重放里每挂出一条询问就是一笔迟早到达的旧答复欠账，由状态门之外的丢弃闸门按数吞掉
                int qtype = data != null && data.length > 0 ? (data[0] & 0xFF) : -1;
                if (qtype >= EngineMessage.MSG_SELECT_BATTLECMD && qtype <= EngineMessage.MSG_SELECT_UNSELECT_CARD) {
                    dp.replayAnswerDebt++;
                }
            }
            return;
        }
        // 记下这条询问（首字节即引擎消息号，MSG_SELECT_* 编号连续取 10..26 区间）
        if (data != null && data.length > 0) {
            int qtype = data[0] & 0xFF;
            if (qtype >= EngineMessage.MSG_SELECT_BATTLECMD && qtype <= EngineMessage.MSG_SELECT_UNSELECT_CARD) {
                analyzer.liveQuestion = data;
                analyzer.liveQuestionGeneration = analyzer.askGeneration;
                if (!analyzer.resendingAfterRetry) {
                    // 挂出的是一条新询问：上一条询问的连续非法计数与熔断记录一并作废
                    analyzer.retryStreak = 0;
                    analyzer.lastRetryAsk = null;
                    analyzer.deadlockBreakGeneration = -1;
                }
            }
            analyzer.resendingAfterRetry = false;
        }
        dp.send(STOC_GAME_MSG, data);
    }

    void sendToObservers(byte[] data) {
        emitObservers(data);
    }

    void broadcastMsg(byte[] data) {
        ServerConnection p0 = analyzer.room.players[0];
        sendToPlayer(p0, data);
        int seats = analyzer.room.seatCount();
        for (int i = 1; i < seats; i++) {
            ServerConnection p = analyzer.room.players[i];
            // Solo mode: 席位可能共享同一连接，按身份去重，否则动画类消息会被双收双播
            if (p != null && p != p0) {
                sendToPlayer(p, data);
            }
        }
        emitObservers(data);
    }

    /** 统一出站口：静默重放（撤回前的重建重放）期只录不发，但整局重放的人机席位除外；reload 重同步批不再发给它们。 */
    void emit(ServerConnection dp, byte[] data) {
        if (dp == null) {
            return;
        }
        if (analyzer.silent) {
            if (analyzer.isUndoReplaySink(dp)) {
                dp.send(STOC_GAME_MSG, data);
            }
            return;
        }
        if (analyzer.undoResyncBurst && analyzer.isUndoReplaySink(dp)) {
            return;
        }
        dp.send(STOC_GAME_MSG, data);
    }

    /** 观战统一出站口：静默重放期只录不发。 */
    void emitObservers(byte[] data) {
        if (analyzer.silent) {
            return;
        }
        for (ServerConnection o : analyzer.room.observers) {
            o.send(STOC_GAME_MSG, data);
        }
    }
}
