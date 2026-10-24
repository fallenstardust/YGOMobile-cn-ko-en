package cn.garymb.ygomobile.network.server;

import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.LinkedHashSet;
import java.util.Set;

import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.network.BufferIO;
import cn.garymb.ygomobile.network.YGOProtocol;

/**
 * 单个局域网房间的状态机，等价 {@code Classes/gframe/netserver.cpp} 的房间分发
 * （{@code HandleCTOSPacket} 状态门）加 {@code single_duel.cpp} 的开局前段
 * （JoinGame/LeaveGame/ToDuelist/ToObserver/PlayerReady/PlayerKick/UpdateDeck/StartDuel/HandResult）。
 *
 * <p>真正的决斗引擎循环（TPResult 起）委托给 {@link ServerDuel}；本类持有房间共享状态与所有底层
 * STOC 发送工具，二者互相引用。所有公共入口（{@link #createGame}/{@link #handlePacket}/
 * {@link #onDisconnect}）均由 {@link LanGameServer} 投递到同一单线程执行器上串行执行，
 * 因此本类内部方法可直接同步读写状态而无需额外加锁。
 *
 * <p>type：0/1=决斗位、7=观战、0xff=未分配；state：等待应答的 CTOS 类型、0=自由、0xff=阻塞。
 */
public final class GameRoom extends GameRoomMid {

    GameRoom(LanGameServer server) {
        super(server);
    }

    // ==================================================================
    // CTOS 分发（对齐 netserver.cpp::HandleCTOSPacket 状态门 + switch）
    // ==================================================================

    /**
     * 处理除 CREATE_GAME/PLAYER_INFO 外的所有 CTOS 包。CREATE_GAME/PLAYER_INFO 由
     * {@link LanGameServer} 在房间创建前后先行处理。
     */
    void handlePacket(ServerConnection conn, int proto, ByteBuffer body) {
        // 状态门：投降/聊天/撤回不受限；其余包要求 state==自由(0) 或 state==本包类型。
        // CTOS_UNDO 必须豁免：发起撤回的连接此刻处于 STATE_NONE（它刚应答完，或对方正在等待），
        // 按常规门会被直接丢弃；撤回的合法条件（决斗中/有可撤回记录/权限）全部在服务端内部校验。
        // CTOS_ASK_RESEND 同理豁免：它恰恰是在「本席位没被问到」时发出的求援包，
        // 要能递到对局手里才能把那条丢失的询问重新挂出来
        if (proto != CTOS_SURRENDER && proto != CTOS_CHAT && proto != CTOS_UNDO
                && proto != CTOS_ASK_RESEND
                && (conn.state == ServerConnection.STATE_NONE
                || (conn.state != ServerConnection.STATE_FREE && conn.state != proto))) {
            // 决斗中应答被状态门吞掉：此刻该席位并没有被问到（多为撤回后延后重挂留白期内
            // 迟到的旧应答，或非应答席位误发）；落盘现场以便事后判定“错拍应答”类卡死
            if (proto == CTOS_RESPONSE && duelStage == DUEL_STAGE_DUELING) {
                Log.i(TAG, "CTOS_RESPONSE dropped by state gate: seat=" + conn.type
                        + " state=0x" + Integer.toHexString(conn.state & 0xFF)
                        + " lastResponse=" + lastResponse);
                // 交对局自愈：若引擎此刻等的正是这个席位，说明它手上那个询问并未被答掉，而客户端
                // 已以为自己答过、界面不再挂任何询问（点卡弹不出命令菜单，决斗永久互等），
                // 重挂该询问即可恢复；其余情形的被吞应答属于已回退的那一段，照旧丢弃
                if (duel != null) {
                    duel.onResponseDropped(conn);
                }
            }
            return;
        }
        switch (proto) {
            case CTOS_RESPONSE: {
                if (duel == null) {
                    return;
                }
                byte[] resp = remaining(body);
                duel.getResponse(conn, resp);
                break;
            }
            case CTOS_UNDO: {
                // 撤回上一步操作（本工程扩展）：局域网房主房间与人机（WindBot）房间同走此通路
                if (duel != null) {
                    duel.undoLastResponse(conn);
                }
                break;
            }
            case CTOS_ASK_RESEND: {
                // 询问丢失自愈（本工程扩展）：客户端答完上一步后长时间再无任何消息进来，
                // 说明它那一头的询问没能挂住；请服务端把引擎此刻等的那一条原样重发
                if (duel != null) {
                    duel.onAskResendRequest(conn);
                }
                break;
            }
            case CTOS_TIME_CONFIRM: {
                if (duel != null) {
                    duel.timeConfirm(conn);
                }
                break;
            }
            case CTOS_CHAT: {
                chat(conn, body);
                break;
            }
            case CTOS_UPDATE_DECK: {
                updateDeck(conn, body);
                break;
            }
            case CTOS_HAND_RESULT: {
                if (body.remaining() < 1) {
                    return;
                }
                handResult(conn, body.get() & 0xFF);
                break;
            }
            case CTOS_TP_RESULT: {
                if (body.remaining() < 1) {
                    return;
                }
                tpResult(conn, body.get() & 0xFF);
                break;
            }
            case CTOS_LEAVE_GAME: {
                leaveGame(conn);
                break;
            }
            case CTOS_SURRENDER: {
                if (duel != null) {
                    duel.surrender(conn);
                }
                break;
            }
            case CTOS_HS_TODUELIST: {
                if (duelStage == DUEL_STAGE_DUELING) {
                    return;
                }
                toDuelist(conn);
                break;
            }
            case CTOS_HS_TOOBSERVER: {
                if (duelStage == DUEL_STAGE_DUELING) {
                    return;
                }
                toObserver(conn);
                break;
            }
            case CTOS_HS_READY: {
                if (duelStage == DUEL_STAGE_DUELING) {
                    return;
                }
                playerReady(conn, true);
                break;
            }
            case CTOS_HS_NOTREADY: {
                if (duelStage == DUEL_STAGE_DUELING) {
                    return;
                }
                playerReady(conn, false);
                break;
            }
            case CTOS_HS_KICK: {
                if (duelStage == DUEL_STAGE_DUELING || body.remaining() < 1) {
                    return;
                }
                playerKick(conn, body.get() & 0xFF);
                break;
            }
            case CTOS_HS_START: {
                if (duelStage == DUEL_STAGE_DUELING) {
                    return;
                }
                startDuel(conn);
                break;
            }
            default:
                break;
        }
    }

    void tpResult(ServerConnection dp, int tp) {
        if (dp.state != CTOS_TP_RESULT) {
            return;
        }
        duel = new ServerDuel(this);
        duel.startDuel(dp, tp);
    }
}
