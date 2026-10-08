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
    // TAG 赛制需 4 副（4 席位），其余 2 副；统一按 4 槽分配，多余的槽位不使用。
    final PlayerDeck[] decks = new PlayerDeck[]{new PlayerDeck(), new PlayerDeck(), new PlayerDeck(), new PlayerDeck()};
    final int[] deckError = new int[4];

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

    /** Solo mode flag (decoded from hostInfo.mode & 0x10). */
    boolean soloMode = false;
    /** Solo 房间且赛制为 TAG：4 席位全部由房主一人操控（对齐 TagDuel 的 4 副卡组装载）。 */
    boolean tagMode = false;
    /** Track how many decks have been submitted in solo mode (expect 2, or 4 for TAG). */
    int soloDeckCount = 0;
    /** match 换 side 阶段已提交的备牌卡组份数（solo 逐席位引导，非 solo 不使用）。 */
    int soloSideCount = 0;
    /** 本局换 side 会话内各 decks 槽位是否已被写回：solo 按内容归位后占位，
     *  防止同一槽位被重复写回；每局 duelEndProc 复位。 */
    final boolean[] soloSideFilled = new boolean[4];

    /** solo 开局前需收集的卡组份数：TAG 4 份、其余 2 份。 */
    int requiredSoloDecks() {
        return tagMode ? 4 : 2;
    }

    /**
     * solo 换 side：按提交卡组“主+额外+副整体多重集”在未写回的槽位中定位归属，
     * 鲁棒于先后手轮转造成的槽位错位；内容均不匹配（如双方卡组完全一致）时回退首个未写回槽位。
     * 无可用槽位（已全部写回）返回 -1。
     */
    int resolveSoloSideSlot(PlayerDeck nd, int required) {
        for (int i = 0; i < required; i++) {
            if (!soloSideFilled[i] && nd.sameCardUnion(decks[i])) {
                return i;
            }
        }
        for (int i = 0; i < required; i++) {
            if (!soloSideFilled[i]) {
                return i;
            }
        }
        return -1;
    }

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
        if (hostInfo.duelMode() > MODE_TAG) {
            hostInfo.mode = MODE_SINGLE | (hostInfo.mode & 0x10); // preserve solo bit
        }
        soloMode = hostInfo.soloMode();
        // 本内置服务端实现了 CTOS_UNDO（撤回）：在 HostInfo 的 pad 扩展位上打能力标记，
        // 该标记经 STOC_JOIN_GAME / 局域网房间列表回显给连入方，使其也能显示撤回按钮
        //（gframe 等第三方服务端该 3 字节为未定义填充，不会凑出这个魔术字节）
        hostInfo.extMagic = HOST_EXT_MAGIC;
        hostInfo.extCaps = HOST_CAP_UNDO | HOST_CAP_ASK_RESEND;
        matchMode = hostInfo.duelMode() == MODE_MATCH;
        tagMode = soloMode && hostInfo.duelMode() == MODE_TAG;
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
        // 系统消息（chat_player_type=8）广播：非建房者入位/入观战时在聊天流里通报，
        // 客户端以「[System]: xxx」弹幕展示（对齐 gframe AddChatMsg 的 8 号语义）。
        // 历史上内置服务端从不产生 8/9/10 类型消息，故系统消息从不显示——本处即生产方。
        // 此时 dp 已入位（players[pos] 或 observers），sendToAllPresent 会连本人一并送达。
        if (!isCreater) {
            broadcastSystemChat(displayName(dp)
                    + (dp.type <= 1 ? " 加入了房间" : " 加入观战"));
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
        leaveGame(dp, false);
    }

    /**
     * 离开房间。{@code kicked} 为真时由房主踢出触发（{@link #playerKick}），系统消息文案区分
     * 「离开 / 被踢」；两种路径都要向在场所有人广播 type 8 系统消息（弹幕可见）。
     */
    void leaveGame(ServerConnection dp, boolean kicked) {
        String leftText = displayName(dp) + (kicked ? " 被踢出房间" : " 离开了房间");
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
            broadcastSystemChat(displayName(dp) + (kicked ? " 被踢出房间" : " 退出观战"));
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
            // 席位已置空，广播不再回给离开者本人
            broadcastSystemChat(leftText);
            dp.close();
        } else {
            broadcastSystemChat(leftText);
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
        if (soloMode && isReady) {
            // In solo mode, ready is managed by updateDeck (auto-set after 2 decks loaded);
            // explicit CTOS_HS_READY is ignored to avoid premature ready before both decks
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
        leaveGame(players[pos], true);
    }

    // ==================================================================
    // 卡组更新
    // ==================================================================

    void updateDeck(ServerConnection dp, ByteBuffer body) {
        int required = requiredSoloDecks();
        // solo：首局按已收集份数门槛；换 side 阶段按已提交备牌份数；非 solo：按座位就绪位
        boolean full = duelCount > 0 ? soloSideCount >= required : soloDeckCount >= required && ready[0];
        if (dp.type > 1 || (soloMode ? full : ready[dp.type])) {
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
        // Solo mode: decks fill seats in submission order 0..required-1 (TAG=4, else 2).
        int targetSlot = soloMode ? (duelCount > 0 ? soloSideCount : soloDeckCount) : dp.type;
        if (duelCount == 0) {
            deckError[targetSlot] = decks[targetSlot].load(buf, mainc, sidec);
        } else {
            // match 换边（side）
            PlayerDeck nd = new PlayerDeck();
            int err = nd.load(buf, mainc, sidec);
            if (err != 0) {
                sendError(dp, ERRMSG_SIDEERROR, 0);
                return;
            }
            if (soloMode) {
                // solo：换 side 改由客户端本地逐席位驱动（不再依赖服务端每提交一份回一条
                // CHANGE_SIDE 的跨端回环）。服务端按卡组“主+额外+副整体多重集”将提交内容归位
                // 到对应槽位（soloSideFilled 占位、鲁棒于先后手轮转的槽位错位），
                // 收齐 required 份后统一置 ready 并进入下一局；中途不下发 CHANGE_SIDE。
                int slot = resolveSoloSideSlot(nd, required);
                if (slot < 0 || !nd.canSwapTo(decks[slot])) {
                    sendError(dp, ERRMSG_SIDEERROR, 0);
                    return;
                }
                soloSideFilled[slot] = true;
                decks[slot] = nd;
                soloSideCount++;
                if (soloSideCount >= required) {
                    ready[0] = true;
                    ready[1] = true;
                    dp.send(STOC_DUEL_START, null);
                    if (duel != null) {
                        duel.startNextDuelFromSide();
                    }
                }
                return;
            }
            if (!nd.canSwapTo(decks[targetSlot])) {
                sendError(dp, ERRMSG_SIDEERROR, 0);
                return;
            }
            decks[targetSlot] = nd;
            ready[targetSlot] = true;
            dp.send(STOC_DUEL_START, null);
            if (ready[0] && ready[1] && duel != null) {
                duel.startNextDuelFromSide();
            }
            return;
        }
        if (soloMode) {
            soloDeckCount++;
            boolean allValid = true;
            for (int i = 0; i < required; i++) {
                if (deckError[i] != 0) {
                    allValid = false;
                    break;
                }
            }
            if (soloDeckCount >= required && allValid) {
                // All decks valid: auto-ready
                ready[0] = true;
                byte[] pc = new byte[]{(byte) ((0 << 4) | PLAYERCHANGE_READY)};
                dp.send(STOC_HS_PLAYER_CHANGE, pc);
                for (ServerConnection o : observers) {
                    o.send(STOC_HS_PLAYER_CHANGE, pc);
                }
            } else if (deckError[targetSlot] != 0) {
                // Deck error on current slot: notify and reset counter
                dp.send(STOC_HS_PLAYER_CHANGE, new byte[]{(byte) ((dp.type << 4) | PLAYERCHANGE_NOTREADY)});
                sendError(dp, ERRMSG_DECKERROR, deckError[targetSlot]);
                soloDeckCount = 0; // restart deck selection
            }
        } else {
            // Notify self of deck state (original logic)
            if (deckError[dp.type] != 0) {
                dp.send(STOC_HS_PLAYER_CHANGE, new byte[]{(byte) ((dp.type << 4) | PLAYERCHANGE_NOTREADY)});
                sendError(dp, ERRMSG_DECKERROR, deckError[dp.type]);
            }
        }
    }

    // ==================================================================
    // 开始决斗 → 猜拳 → 先攻
    // ==================================================================

    void startDuel(ServerConnection dp) {
        if (dp != hostPlayer) {
            return;
        }
        if (soloMode) {
            // Solo mode: only player 0 needs to be ready; assign same connection to both slots
            if (!ready[0]) {
                return;
            }
            if (players[1] == null) {
                players[1] = players[0];
            }
            ready[1] = true;
        } else {
            if (!ready[0] || !ready[1]) {
                return;
            }
        }
        server.stopListen();
        players[0].send(STOC_DUEL_START, null);
        if (!soloMode) {
            players[1].send(STOC_DUEL_START, null);
        }
        for (ServerConnection o : observers) {
            o.state = CTOS_LEAVE_GAME;
            o.send(STOC_DUEL_START, null);
        }
        if (tagMode) {
            // TAG：房主（players[0]）看自席 decks[0] 与队友席 decks[2]（对齐 TagDuel::StartDuel）
            players[0].send(STOC_DECK_COUNT, PlayerDeck.deckCountPayload(decks[0], decks[2], 0));
        } else {
            players[0].send(STOC_DECK_COUNT, PlayerDeck.deckCountPayload(decks[0], decks[1], 0));
        }
        if (!soloMode) {
            players[1].send(STOC_DECK_COUNT, PlayerDeck.deckCountPayload(decks[0], decks[1], 1));
        }
        if (soloMode) {
            // Skip rock-paper-scissors: directly ask host to choose first/second
            players[0].send(STOC_SELECT_TP, null);
            tpPlayer = 0;
            players[0].state = CTOS_TP_RESULT;
            duelStage = DUEL_STAGE_FIRSTGO;
        } else {
            players[0].send(STOC_SELECT_HAND, null);
            players[1].send(STOC_SELECT_HAND, null);
            handResult[0] = 0;
            handResult[1] = 0;
            players[0].state = CTOS_HAND_RESULT;
            players[1].state = CTOS_HAND_RESULT;
            duelStage = DUEL_STAGE_FINGER;
        }
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
        // 观战发言（type=7）：STOC_CHAT 协议只携 [chat_player_type][文本]，无独立昵称字段，
        // 客户端观战弹幕历史上只能显示固定星号遮罩「[********]」；中继时把观战名拼进文本
        //（无名字时兜底 Watcher），客户端前缀为「[Spectator] 名字: 内容」；玩家(0-3)不变，
        // 仍由客户端按座位号自取 seatNames 拼前缀
        String text = utf16Text(msg);
        if (dp.type == NETPLAYER_TYPE_OBSERVER) {
            String who = (dp.name == null || dp.name.isEmpty()) ? "Watcher" : dp.name;
            text = who + ": " + text;
        }
        byte[] payload = chatPayload(dp.type, text);
        // 中继统一走 sendToAllPresent：solo 模式下 players[1] == players[0] 为同一连接，
        // 按连接身份去重，避免同一条聊天被双发（历史缺陷：双端收到重复消息/重复弹幕）
        sendToAllPresent(STOC_CHAT, payload);
        // 录像：对局中把玩家/观战发言录为 0xF1 伪帧，使纯消息流回放血条下按时间线重现；
        // 与引擎消息同处单线程房间执行器，追加顺序即时序。playerType 取 dp.type（与实时
        // STOC_CHAT 首字节同值，回放分侧/命名一致）；文本含观战名（回放弹幕同样可见）。
        if (isDueling() && duel != null && duel.replay != null) {
            duel.replay.writeChatFrame(dp.type, text);
            // 录制验证落点：每条对局聊天录为 0xF1 伪帧均留痕，logcat 过滤 "GameRoom" 即可
            // 确认聊天已落盘；回放开场信息另标注文件内聊天帧总条数（ReplayPlayer.buildReplayInfo）
            Log.i(TAG, "replay chat frame recorded: type=" + dp.type + " len=" + text.length());
        } else if (dp.type < 4) {
            Log.d(TAG, "chat not recorded (dueling=" + isDueling()
                    + " duel=" + (duel != null) + "): stage not in-duel");
        }
    }

    /** 解码 UTF-16LE 聊天文本并去除尾部 NUL 码元（GameRoom.chat 入参已校验以 0 结尾） */
    private static String utf16Text(byte[] msg) {
        int units = msg.length / 2;
        while (units > 0 && ((msg[units * 2 - 2] & 0xFF) | ((msg[units * 2 - 1] & 0xFF) << 8)) == 0) {
            units--;
        }
        return new String(msg, 0, units * 2, java.nio.charset.StandardCharsets.UTF_16LE);
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
        ServerConnection p0 = players[0];
        ServerConnection p1 = players[1];
        if (p0 != null) {
            p0.send(STOC_GAME_MSG, data);
        }
        // Solo mode: p1 == p0 is the same connection; sending to both would deliver the
        // message (e.g. MSG_WIN overlay) twice, so dedupe by connection identity.
        if (p1 != null && p1 != p0) {
            p1.send(STOC_GAME_MSG, data);
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
        ServerConnection p0 = players[0];
        ServerConnection p1 = players[1];
        if (p0 != null) {
            p0.send(proto, payload);
        }
        // Solo mode: p1 == p0 is the same connection; dedupe by identity.
        if (p1 != null && p1 != p0) {
            p1.send(proto, payload);
        }
        for (ServerConnection o : observers) {
            o.send(proto, payload);
        }
    }

    private void broadcastPlayersAndObservers(int proto, byte[] payload) {
        sendToAllPresent(proto, payload);
    }

    /**
     * 组装 STOC_CHAT 载荷：[u16 LE chat_player_type][UTF-16LE 文本码元 + 结尾 NUL]
     * （对齐 netserver.cpp CreateChatPacket 与 gframe 客户端 STOC_CHAT 解析）。
     */
    static byte[] chatPayload(int playerType, byte[] utf16WithNul) {
        byte[] payload = new byte[2 + utf16WithNul.length];
        payload[0] = (byte) (playerType & 0xFF);
        payload[1] = (byte) ((playerType >> 8) & 0xFF);
        System.arraycopy(utf16WithNul, 0, payload, 2, utf16WithNul.length);
        return payload;
    }

    /** 文本版 {@link #chatPayload(int, byte[])}：自动转 UTF-16LE 并补结尾 NUL，长文本按 LEN_CHAT_MSG 截断。 */
    static byte[] chatPayload(int playerType, String text) {
        String s = text == null ? "" : text;
        // 载荷上限：LEN_CHAT_MSG 个 u16（含结尾 NUL），逐字编码后补两字节 NUL
        int maxUnits = 256 - 1;
        if (s.length() > maxUnits) {
            s = s.substring(0, maxUnits);
        }
        byte[] payload = new byte[2 + (s.length() + 1) * 2];
        payload[0] = (byte) (playerType & 0xFF);
        payload[1] = (byte) ((playerType >> 8) & 0xFF);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            payload[2 + i * 2] = (byte) (c & 0xFF);
            payload[3 + i * 2] = (byte) ((c >> 8) & 0xFF);
        }
        // 末尾两字节保持 0：新 byte[] 默认即 NUL 终止符
        return payload;
    }

    /** 显示名：连接未携昵称时兜底，避免系统消息里出现空白主语。 */
    static String displayName(ServerConnection dp) {
        if (dp == null || dp.name == null || dp.name.isEmpty()) {
            return "Player";
        }
        return dp.name;
    }

    /**
     * 广播一条服务端系统消息（chat_player_type=8）给在场决斗位与观战：
     * 客户端在等待界面与对局中均以「[System]: 内容」弹幕展示。
     */
    void broadcastSystemChat(String text) {
        sendToAllPresent(STOC_CHAT, chatPayload(CHAT_PLAYER_TYPE_SYSTEM, text));
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
        /** HostInfo pad[0]：本工程私有扩展魔术字节（见 {@code YGOProtocol.HOST_EXT_MAGIC}），
         *  服务端建房时强制置位，用于向连入方声明“本房间服务端为本工程内置服务端”。 */
        int extMagic;
        /** HostInfo pad[1]：服务端能力位掩码（见 {@code YGOProtocol.HOST_CAP_*}）。 */
        int extCaps;

        /** Solo mode is encoded in mode bit 4 (0x10); original mode values 0/1/2 use lower bits only. */
        boolean soloMode() { return (mode & 0x10) != 0; }
        /** Duel mode without the solo bit (0=Single, 1=Match, 2=Tag). */
        int duelMode() { return mode & 0x0F; }

        void read(ByteBuffer b) {
            lflist = b.getInt();
            rule = b.get() & 0xFF;
            mode = b.get() & 0xFF;
            duelRule = b.get() & 0xFF;
            noCheckDeck = b.get() & 0xFF;
            noShuffleDeck = b.get() & 0xFF;
            extMagic = b.get() & 0xFF;
            extCaps = b.get() & 0xFF;
            b.get(); // pad[2]（保留未用）
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
            b.put((byte) extMagic);
            b.put((byte) extCaps);
            b.put((byte) 0); // pad[2]（保留未用）
            b.putInt(startLp);
            b.put((byte) startHand);
            b.put((byte) drawCount);
            b.putShort((short) timeLimit);
            return b.array();
        }
    }
}
