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
public final class GameRoom implements YGOProtocol {

    private static final String TAG = "GameRoom";

    /** STOC_HS_PLAYER_CHANGE 状态位（network.h PLAYERCHANGE_*）。 */
    static final int PLAYERCHANGE_OBSERVE = 0x8;
    static final int PLAYERCHANGE_READY = 0x9;
    static final int PLAYERCHANGE_NOTREADY = 0xa;
    static final int PLAYERCHANGE_LEAVE = 0xb;

    /** 观战人数上限（对齐 game.h DEFAULT_WATCHER，取一个保守值）。 */
    private static final int MAX_OBSERVER = 50;

    /** 最大可用规则号（对齐 game.h CURRENT_RULE / MASTER_RULE）。 */
    static final int CURRENT_RULE = 5;

    final LanGameServer server;

    // —— 房间基本信息 ——
    String roomName = "";
    String roomPass = "";
    final HostInfo hostInfo = new HostInfo();
    ServerConnection hostPlayer;

    // —— 玩家座位（对齐 SingleDuel::players/pplayer/ready）——
    final ServerConnection[] players = new ServerConnection[2];
    final ServerConnection[] pplayer = new ServerConnection[2];
    final boolean[] ready = new boolean[2];
    final Set<ServerConnection> observers = new LinkedHashSet<>();

    // —— 卡组（校验后按 main/extra 拆分，见 ServerDuel 装载）——
    final PlayerDeck[] decks = new PlayerDeck[]{new PlayerDeck(), new PlayerDeck()};
    final int[] deckError = new int[2];

    // —— 开局前猜拳/先攻 ——
    final int[] handResult = new int[2];
    int tpPlayer = 0;
    int duelStage = DUEL_STAGE_BEGIN;

    // —— match 赛制（MODE_MATCH）——
    boolean matchMode = false;
    int matchKill = 0;
    int duelCount = 0;
    final int[] matchResult = new int[3];

    // —— 计时 ——
    final int[] timeLimit = new int[2];
    int timeElapsed = 0;
    int lastResponse = 0;

    /** 进行中的决斗引擎，TPResult 时创建，EndDuel 后置空。 */
    ServerDuel duel;

    GameRoom(LanGameServer server) {
        this.server = server;
    }

    // ==================================================================
    // CTOS 分发（对齐 netserver.cpp::HandleCTOSPacket 状态门 + switch）
    // ==================================================================

    /**
     * 处理除 CREATE_GAME/PLAYER_INFO 外的所有 CTOS 包。CREATE_GAME/PLAYER_INFO 由
     * {@link LanGameServer} 在房间创建前后先行处理。
     */
    void handlePacket(ServerConnection conn, int proto, ByteBuffer body) {
        // 状态门：投降/聊天不受限；其余包要求 state==自由(0) 或 state==本包类型
        if (proto != CTOS_SURRENDER && proto != CTOS_CHAT
                && (conn.state == ServerConnection.STATE_NONE
                || (conn.state != ServerConnection.STATE_FREE && conn.state != proto))) {
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

    // ==================================================================
    // 建房 / 加入 / 离开
    // ==================================================================

    /**
     * CTOS_CREATE_GAME：由 {@link LanGameServer} 在房间尚不存在时调用，按包内 HostInfo 建房，
     * 创建者即房主并占 players[0]（等价 JoinGame(is_creater=true)）。
     */
    void createGame(ServerConnection conn, ByteBuffer body) {
        if (body.remaining() < 100) {
            return;
        }
        hostInfo.read(body);
        if (hostInfo.rule > CURRENT_RULE) {
            hostInfo.rule = CURRENT_RULE;
        }
        if (hostInfo.mode > MODE_TAG) {
            hostInfo.mode = MODE_SINGLE;
        }
        matchMode = hostInfo.mode == MODE_MATCH;
        roomName = BufferIO.readUTF16(body, 20);
        roomPass = BufferIO.readUTF16(body, 20);
        conn.host = true;
        joinGame(conn, null, true);
    }

    /** CTOS_JOIN_GAME：非创建者加入，做版本/密码/满员校验。 */
    void joinGame(ServerConnection dp, ByteBuffer body, boolean isCreater) {
        if (!isCreater) {
            if (dp.type != ServerConnection.TYPE_NONE) {
                sendError(dp, ERRMSG_JOINERROR, 0);
                dp.close();
                return;
            }
            if (body == null || body.remaining() < 48) {
                return;
            }
            int version = body.getShort() & 0xFFFF;
            body.getShort(); // padding u16
            body.getInt(); // gameid u32（此处不校验，对齐 C++ 忽略）
            // CTOS_JoinGame: version(u16)+pad(u16)+gameid(u32)+pass(u16[20]) = 48 字节，pass 起始偏移 8
            if (version != Constants.PRO_VERSION) {
                sendError(dp, ERRMSG_VERERROR, Constants.PRO_VERSION);
                dp.close();
                return;
            }
            body.position(8); // 跳到 pass
            String jpass = BufferIO.readUTF16(body, 20);
            if (!jpass.equals(roomPass)) {
                sendError(dp, ERRMSG_JOINERROR, 1);
                return;
            }
        }
        if (hostPlayer == null && players[0] == null && players[1] == null && observers.isEmpty()) {
            hostPlayer = dp;
            dp.host = true;
        }
        dp.state = ServerConnection.STATE_FREE;

        int typeChange = (hostPlayer == dp) ? 0x10 : 0;
        if (players[0] == null || players[1] == null) {
            int pos = players[0] == null ? 0 : 1;
            // 通知已在房间的玩家与观战：新人进入
            byte[] enter = playerEnterPayload(dp.name, pos);
            if (players[0] != null) {
                players[0].send(STOC_HS_PLAYER_ENTER, enter);
            }
            if (players[1] != null) {
                players[1].send(STOC_HS_PLAYER_ENTER, enter);
            }
            for (ServerConnection o : observers) {
                o.send(STOC_HS_PLAYER_ENTER, enter);
            }
            if (players[0] == null) {
                players[0] = dp;
                dp.type = NETPLAYER_TYPE_PLAYER1;
                typeChange |= NETPLAYER_TYPE_PLAYER1;
            } else {
                players[1] = dp;
                dp.type = NETPLAYER_TYPE_PLAYER2;
                typeChange |= NETPLAYER_TYPE_PLAYER2;
            }
        } else {
            if (observers.size() >= MAX_OBSERVER) {
                sendError(dp, ERRMSG_JOINERROR, 1);
                return;
            }
            observers.add(dp);
            dp.type = NETPLAYER_TYPE_OBSERVER;
            typeChange |= NETPLAYER_TYPE_OBSERVER;
            byte[] wc = watchChangePayload(observers.size());
            if (players[0] != null) {
                players[0].send(STOC_HS_WATCH_CHANGE, wc);
            }
            if (players[1] != null) {
                players[1].send(STOC_HS_WATCH_CHANGE, wc);
            }
            for (ServerConnection o : observers) {
                o.send(STOC_HS_WATCH_CHANGE, wc);
            }
        }
        // 回执本人：房间信息 + 自身座位
        dp.send(STOC_JOIN_GAME, hostInfo.toBytes());
        dp.send(STOC_TYPE_CHANGE, new byte[]{(byte) typeChange});
        // 把已存在的对手/队友与观战人数补发给新人
        for (int i = 0; i < 2; i++) {
            if (players[i] != null) {
                dp.send(STOC_HS_PLAYER_ENTER, playerEnterPayload(players[i].name, i));
                if (ready[i]) {
                    dp.send(STOC_HS_PLAYER_CHANGE, new byte[]{(byte) ((i << 4) | PLAYERCHANGE_READY)});
                }
            }
        }
        if (!observers.isEmpty()) {
            dp.send(STOC_HS_WATCH_CHANGE, watchChangePayload(observers.size()));
        }
    }

    void leaveGame(ServerConnection dp) {
        if (dp == hostPlayer) {
            if (duel != null) {
                duel.endDuel();
            }
            server.stop();
            return;
        }
        if (dp.type == NETPLAYER_TYPE_OBSERVER) {
            observers.remove(dp);
            if (duelStage == DUEL_STAGE_BEGIN) {
                byte[] wc = watchChangePayload(observers.size());
                broadcastPlayersAndObservers(STOC_HS_WATCH_CHANGE, wc);
            }
            dp.close();
            return;
        }
        if (dp.type > 1) {
            dp.close();
            return;
        }
        if (duelStage == DUEL_STAGE_BEGIN) {
            players[dp.type] = null;
            ready[dp.type] = false;
            byte[] pc = new byte[]{(byte) ((dp.type << 4) | PLAYERCHANGE_LEAVE)};
            if (players[0] != null && dp.type != 0) {
                players[0].send(STOC_HS_PLAYER_CHANGE, pc);
            }
            if (players[1] != null && dp.type != 1) {
                players[1].send(STOC_HS_PLAYER_CHANGE, pc);
            }
            for (ServerConnection o : observers) {
                o.send(STOC_HS_PLAYER_CHANGE, pc);
            }
            dp.close();
        } else {
            if (duel != null) {
                duel.onOpponentLeft(dp);
            }
            dp.close();
        }
    }

    void onDisconnect(ServerConnection conn) {
        if (conn.type == ServerConnection.TYPE_NONE) {
            // 从未入座，直接丢弃
            return;
        }
        leaveGame(conn);
    }

    // ==================================================================
    // 座位/准备/踢人
    // ==================================================================

    void toDuelist(ServerConnection dp) {
        if (dp.type != NETPLAYER_TYPE_OBSERVER || (players[0] != null && players[1] != null)) {
            return;
        }
        observers.remove(dp);
        int pos;
        if (players[0] == null) {
            players[0] = dp;
            dp.type = NETPLAYER_TYPE_PLAYER1;
            pos = 0;
        } else {
            players[1] = dp;
            dp.type = NETPLAYER_TYPE_PLAYER2;
            pos = 1;
        }
        byte[] enter = playerEnterPayload(dp.name, pos);
        byte[] wc = watchChangePayload(observers.size());
        sendToAllPresent(STOC_HS_PLAYER_ENTER, enter);
        sendToAllPresent(STOC_HS_WATCH_CHANGE, wc);
        dp.send(STOC_TYPE_CHANGE, new byte[]{(byte) ((dp == hostPlayer ? 0x10 : 0) | dp.type)});
    }

    void toObserver(ServerConnection dp) {
        if (dp.type > 1) {
            return;
        }
        byte[] pc = new byte[]{(byte) ((dp.type << 4) | PLAYERCHANGE_OBSERVE)};
        if (players[0] != null) {
            players[0].send(STOC_HS_PLAYER_CHANGE, pc);
        }
        if (players[1] != null) {
            players[1].send(STOC_HS_PLAYER_CHANGE, pc);
        }
        for (ServerConnection o : observers) {
            o.send(STOC_HS_PLAYER_CHANGE, pc);
        }
        players[dp.type] = null;
        ready[dp.type] = false;
        dp.type = NETPLAYER_TYPE_OBSERVER;
        observers.add(dp);
        dp.send(STOC_TYPE_CHANGE, new byte[]{(byte) ((dp == hostPlayer ? 0x10 : 0) | dp.type)});
    }

    void playerReady(ServerConnection dp, boolean isReady) {
        if (dp.type > 1 || ready[dp.type] == isReady) {
            return;
        }
        if (isReady) {
            int deckError2 = 0;
            if (hostInfo.noCheckDeck == 0) {
                if (deckError[dp.type] != 0) {
                    deckError2 = (DECKERROR_UNKNOWNCARD << 28) | deckError[dp.type];
                } else {
                    deckError2 = DeckChecker.checkDeck(decks[dp.type], hostInfo.lflist, hostInfo.rule);
                }
            }
            if (deckError2 != 0) {
                dp.send(STOC_HS_PLAYER_CHANGE, new byte[]{(byte) ((dp.type << 4) | PLAYERCHANGE_NOTREADY)});
                sendError(dp, ERRMSG_DECKERROR, deckError2);
                return;
            }
        }
        ready[dp.type] = isReady;
        byte[] pc = new byte[]{(byte) ((dp.type << 4) | (isReady ? PLAYERCHANGE_READY : PLAYERCHANGE_NOTREADY))};
        if (players[dp.type] != null) {
            players[dp.type].send(STOC_HS_PLAYER_CHANGE, pc);
        }
        if (players[1 - dp.type] != null) {
            players[1 - dp.type].send(STOC_HS_PLAYER_CHANGE, pc);
        }
        for (ServerConnection o : observers) {
            o.send(STOC_HS_PLAYER_CHANGE, pc);
        }
    }

    void playerKick(ServerConnection dp, int pos) {
        if (pos > 1 || dp != hostPlayer || dp == players[pos] || players[pos] == null) {
            return;
        }
        leaveGame(players[pos]);
    }

    // ==================================================================
    // 卡组更新
    // ==================================================================

    void updateDeck(ServerConnection dp, ByteBuffer body) {
        if (dp.type > 1 || ready[dp.type]) {
            return;
        }
        if (body.remaining() < 8) {
            return;
        }
        int mainc = body.getInt();
        int sidec = body.getInt();
        boolean valid = mainc <= Constants.DECK_MAIN_MAX + Constants.DECK_EXTRA_MAX
                && sidec <= Constants.DECK_SIDE_MAX
                && body.remaining() >= (mainc + sidec) * 4;
        if (!valid) {
            sendError(dp, ERRMSG_DECKERROR, 0);
            return;
        }
        int[] buf = new int[mainc + sidec];
        for (int i = 0; i < buf.length; i++) {
            buf[i] = body.getInt();
        }
        if (duelCount == 0) {
            deckError[dp.type] = decks[dp.type].load(buf, mainc, sidec);
        } else {
            // match 换边（side）
            PlayerDeck nd = new PlayerDeck();
            int err = nd.load(buf, mainc, sidec);
            if (err == 0 && nd.canSwapTo(decks[dp.type])) {
                decks[dp.type] = nd;
                ready[dp.type] = true;
                dp.send(STOC_DUEL_START, null);
                if (ready[0] && ready[1] && duel != null) {
                    duel.startNextDuelFromSide();
                }
            } else {
                sendError(dp, ERRMSG_SIDEERROR, 0);
            }
        }
    }

    // ==================================================================
    // 开始决斗 → 猜拳 → 先攻
    // ==================================================================

    void startDuel(ServerConnection dp) {
        if (dp != hostPlayer || !ready[0] || !ready[1]) {
            return;
        }
        server.stopListen();
        players[0].send(STOC_DUEL_START, null);
        players[1].send(STOC_DUEL_START, null);
        for (ServerConnection o : observers) {
            o.state = CTOS_LEAVE_GAME;
            o.send(STOC_DUEL_START, null);
        }
        players[0].send(STOC_DECK_COUNT, PlayerDeck.deckCountPayload(decks[0], decks[1], 0));
        players[1].send(STOC_DECK_COUNT, PlayerDeck.deckCountPayload(decks[0], decks[1], 1));
        players[0].send(STOC_SELECT_HAND, null);
        players[1].send(STOC_SELECT_HAND, null);
        handResult[0] = 0;
        handResult[1] = 0;
        players[0].state = CTOS_HAND_RESULT;
        players[1].state = CTOS_HAND_RESULT;
        duelStage = DUEL_STAGE_FINGER;
    }

    void handResult(ServerConnection dp, int res) {
        if (res == 0 || res > 3 || dp.state != CTOS_HAND_RESULT) {
            return;
        }
        int p = dp.type;
        if (handResult[p] != 0) {
            return;
        }
        handResult[p] = res;
        if (handResult[0] != 0 && handResult[1] != 0) {
            players[0].send(STOC_HAND_RESULT, new byte[]{(byte) handResult[0], (byte) handResult[1]});
            for (ServerConnection o : observers) {
                o.send(STOC_HAND_RESULT, new byte[]{(byte) handResult[0], (byte) handResult[1]});
            }
            players[1].send(STOC_HAND_RESULT, new byte[]{(byte) handResult[1], (byte) handResult[0]});
            if (handResult[0] == handResult[1]) {
                players[0].send(STOC_SELECT_HAND, null);
                players[1].send(STOC_SELECT_HAND, null);
                handResult[0] = 0;
                handResult[1] = 0;
                players[0].state = CTOS_HAND_RESULT;
                players[1].state = CTOS_HAND_RESULT;
            } else if ((handResult[0] == 1 && handResult[1] == 2)
                    || (handResult[0] == 2 && handResult[1] == 3)
                    || (handResult[0] == 3 && handResult[1] == 1)) {
                players[1].send(STOC_SELECT_TP, null);
                tpPlayer = 1;
                players[0].state = ServerConnection.STATE_NONE;
                players[1].state = CTOS_TP_RESULT;
                duelStage = DUEL_STAGE_FIRSTGO;
            } else {
                players[0].send(STOC_SELECT_TP, null);
                players[1].state = ServerConnection.STATE_NONE;
                players[0].state = CTOS_TP_RESULT;
                tpPlayer = 0;
                duelStage = DUEL_STAGE_FIRSTGO;
            }
        }
    }

    void tpResult(ServerConnection dp, int tp) {
        if (dp.state != CTOS_TP_RESULT) {
            return;
        }
        duel = new ServerDuel(this);
        duel.startDuel(dp, tp);
    }

    // ==================================================================
    // 聊天
    // ==================================================================

    void chat(ServerConnection dp, ByteBuffer body) {
        byte[] msg = remaining(body);
        if (msg.length == 0 || (msg.length & 1) != 0) {
            return;
        }
        // 校验 UTF-16 以 0 结尾
        int lastChar = (msg[msg.length - 2] & 0xFF) | ((msg[msg.length - 1] & 0xFF) << 8);
        if (lastChar != 0) {
            return;
        }
        byte[] payload = new byte[2 + msg.length];
        payload[0] = (byte) (dp.type & 0xFF);
        payload[1] = 0;
        System.arraycopy(msg, 0, payload, 2, msg.length);
        if (players[0] != null) {
            players[0].send(STOC_CHAT, payload);
        }
        if (players[1] != null) {
            players[1].send(STOC_CHAT, payload);
        }
        for (ServerConnection o : observers) {
            o.send(STOC_CHAT, payload);
        }
    }

    // ==================================================================
    // 发送工具
    // ==================================================================

    void sendError(ServerConnection dp, int msgType, long code) {
        if (dp == null) {
            return;
        }
        ByteBuffer b = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        b.put((byte) msgType);
        b.put(new byte[3]); // padding
        b.putInt((int) code);
        dp.send(STOC_ERROR_MSG, b.array());
    }

    /** 广播一条 STOC_GAME_MSG（引擎消息）给 players[who] 与观战，或指定单个玩家。 */
    void sendGameMsg(ServerConnection dp, byte[] data) {
        if (dp != null) {
            dp.send(STOC_GAME_MSG, data);
        }
    }

    /** 向双方玩家与观战广播同一条 GAME_MSG（对齐 SendBufferToPlayer + ReSendToPlayer）。 */
    void broadcastGameMsg(byte[] data) {
        if (players[0] != null) {
            players[0].send(STOC_GAME_MSG, data);
        }
        if (players[1] != null) {
            players[1].send(STOC_GAME_MSG, data);
        }
        for (ServerConnection o : observers) {
            o.send(STOC_GAME_MSG, data);
        }
    }

    void broadcastToEmpty(int proto, byte[] data) {
        if (players[0] != null) {
            players[0].send(proto, data);
        }
        if (players[1] != null) {
            players[1].send(proto, data);
        }
        for (ServerConnection o : observers) {
            o.send(proto, data);
        }
    }

    private void sendToAllPresent(int proto, byte[] payload) {
        if (players[0] != null) {
            players[0].send(proto, payload);
        }
        if (players[1] != null) {
            players[1].send(proto, payload);
        }
        for (ServerConnection o : observers) {
            o.send(proto, payload);
        }
    }

    private void broadcastPlayersAndObservers(int proto, byte[] payload) {
        sendToAllPresent(proto, payload);
    }

    static byte[] playerEnterPayload(String name, int pos) {
        ByteBuffer b = ByteBuffer.allocate(41).order(ByteOrder.LITTLE_ENDIAN);
        BufferIO.writeUTF16(b, name == null ? "" : name, 20);
        b.put((byte) pos);
        return b.array();
    }

    static byte[] watchChangePayload(int count) {
        ByteBuffer b = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN);
        b.putShort((short) count);
        return b.array();
    }

    static byte[] remaining(ByteBuffer body) {
        byte[] out = new byte[body.remaining()];
        body.get(out);
        return out;
    }

    /** 是否处于决斗中（players/pplayer 已就位、引擎在跑）。 */
    boolean isDueling() {
        return duelStage == DUEL_STAGE_DUELING || duelStage == DUEL_STAGE_SIDING;
    }

    // ==================================================================
    // HostInfo（20 字节，对齐 network.h HostInfo）
    // ==================================================================

    static final class HostInfo {
        int lflist;
        int rule;
        int mode;
        int duelRule;
        int noCheckDeck;
        int noShuffleDeck;
        int startLp;
        int startHand;
        int drawCount;
        int timeLimit;

        void read(ByteBuffer b) {
            lflist = b.getInt();
            rule = b.get() & 0xFF;
            mode = b.get() & 0xFF;
            duelRule = b.get() & 0xFF;
            noCheckDeck = b.get() & 0xFF;
            noShuffleDeck = b.get() & 0xFF;
            b.get();
            b.get();
            b.get(); // pad[3]
            startLp = b.getInt();
            startHand = b.get() & 0xFF;
            drawCount = b.get() & 0xFF;
            timeLimit = b.getShort() & 0xFFFF;
        }

        byte[] toBytes() {
            ByteBuffer b = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN);
            b.putInt(lflist);
            b.put((byte) rule);
            b.put((byte) mode);
            b.put((byte) duelRule);
            b.put((byte) noCheckDeck);
            b.put((byte) noShuffleDeck);
            b.put(new byte[3]); // pad
            b.putInt(startLp);
            b.put((byte) startHand);
            b.put((byte) drawCount);
            b.putShort((short) timeLimit);
            return b.array();
        }
    }
}
