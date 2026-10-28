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
        if (analyzer.soloMode && analyzer.room.players[0] == analyzer.room.players[1]) {
            // Solo mode: same connection; no MSG_WAITING needed, just set state
            analyzer.room.players[playerid].state = CTOS_RESPONSE;
            return;
        }
        byte[] waiting = new byte[]{(byte) EngineMessage.MSG_WAITING};
        if (analyzer.room.players[1 - playerid] != null) {
            analyzer.room.players[1 - playerid].send(STOC_GAME_MSG, waiting);
        }
        if (analyzer.room.hostInfo.timeLimit != 0) {
            byte[] sctl = timeLimitPayload(playerid, analyzer.room.timeLimit[playerid]);
            if (analyzer.room.players[0] != null) {
                analyzer.room.players[0].send(STOC_TIME_LIMIT, sctl);
            }
            if (analyzer.room.players[1] != null) {
                analyzer.room.players[1].send(STOC_TIME_LIMIT, sctl);
            }
            analyzer.room.players[playerid].state = CTOS_TIME_CONFIRM;
        } else {
            analyzer.room.players[playerid].state = CTOS_RESPONSE;
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
     * 双视角发送并录制完整帧：owner 收 full、对手与观战收 masked；录像仅存 full 一条。
     * 取代这些分支原先的 sendToPlayer(owner, full) + sendToPlayer(opp, masked) +
     * sendToObservers(masked)，使录像不再泄漏 players[0] 遮蔽视图；实时发送语义与原逻辑一致。
     */
    void sendDualViewAndRecord(int ownerPlayer, byte[] full, byte[] masked) {
        recordFrame(full);
        ServerConnection op = analyzer.room.players[ownerPlayer];
        ServerConnection opp = analyzer.room.players[1 - ownerPlayer];
        if (analyzer.soloMode && op == opp) {
            // Solo mode: same connection is both players; send full only (masked would overwrite)
            emit(op, full);
            emitObservers(masked);
            return;
        }
        emit(op, full);
        emit(opp, masked);
        emitObservers(masked);
    }

    void sendToPlayer(ServerConnection dp, byte[] data) {
        if (dp == null) {
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
        sendToPlayer(analyzer.room.players[0], data);
        // Solo mode: players[1] == players[0] is the same connection. Sending to both
        // slots would deliver animation-bearing messages (new phase / summoning / turn)
        // twice to the host, so SpecEffectOverlay queues and plays each effect twice.
        // Dedupe by connection identity so a solo host receives exactly one copy.
        if (analyzer.room.players[1] != analyzer.room.players[0]) {
            sendToPlayer(analyzer.room.players[1], data);
        }
        emitObservers(data);
    }

    /** 统一出站口：静默重放（撤回）期只录不发。 */
    void emit(ServerConnection dp, byte[] data) {
        if (dp != null && !analyzer.silent) {
            dp.send(STOC_GAME_MSG, data);
        }
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
