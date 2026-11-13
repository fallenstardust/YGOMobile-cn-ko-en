package cn.garymb.ygomobile.network.server;

import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;

import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.network.BufferIO;
import cn.garymb.ygomobile.network.YGOProtocol;

/**
 * LAN game room state machine, middle layer of the split inheritance
 * chain (GameRoomBase <- GameRoomMid <- GameRoom). Owns the room
 * session flows: create / join / leave / disconnect, seat changes
 * (toDuelist / toObserver / ready / kick), deck update, duel start
 * with the rock-paper-scissors chain and the chat relay.
 */
abstract class GameRoomMid extends GameRoomBase {

    /**
     * 转发构造器：GameRoomBase 仅有带参构造器（无默认无参构造），中间层必须
     * 显式沿链传递 server，保持与原实现完全一致的赋值语义（可见性与父子两层一致为包级私有）
     */
    GameRoomMid(LanGameServer server) {
        super(server);
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
        matchMode = hostInfo.duelMode() == MODE_MATCH;
        // TAG 不再只限纯单人：4 条连接各占一席位同样走引擎 TAG 开局（对齐 TagDuel）
        tagMode = hostInfo.duelMode() == MODE_TAG;
        // 本内置服务端实现了 CTOS_UNDO（撤回）：在 HostInfo 的 pad 扩展位上打能力标记，
        // 该标记经 STOC_JOIN_GAME / 局域网房间列表回显给连入方，使其也能显示撤回按钮
        //（gframe 等第三方服务端该 3 字节为未定义填充，不会凑出这个魔术字节）。
        // TAG 同样开放撤回：整局重放重同步已按“多个重放席位”实现，与纯单人 TAG 体验一致。
        hostInfo.extMagic = HOST_EXT_MAGIC;
        hostInfo.extCaps = HOST_CAP_UNDO | HOST_CAP_ASK_RESEND;
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
        if (hostPlayer == null && firstFreeSeat() >= 0 && observers.isEmpty()) {
            hostPlayer = dp;
            dp.host = true;
        }
        dp.state = ServerConnection.STATE_FREE;

        int typeChange = (hostPlayer == dp) ? 0x10 : 0;
        int seats = seatCount();
        int pos = firstFreeSeat();
        if (pos >= 0) {
            // 通知已在房间的玩家与观战：新人进入
            byte[] enter = playerEnterPayload(dp.name, pos);
            for (int i = 0; i < seats; i++) {
                if (players[i] != null) {
                    players[i].send(STOC_HS_PLAYER_ENTER, enter);
                }
            }
            for (ServerConnection o : observers) {
                o.send(STOC_HS_PLAYER_ENTER, enter);
            }
            players[pos] = dp;
            dp.type = pos;
            typeChange |= pos;
        } else {
            if (observers.size() >= MAX_OBSERVER) {
                sendError(dp, ERRMSG_JOINERROR, 1);
                return;
            }
            observers.add(dp);
            dp.type = NETPLAYER_TYPE_OBSERVER;
            typeChange |= NETPLAYER_TYPE_OBSERVER;
            byte[] wc = watchChangePayload(observers.size());
            for (int i = 0; i < seats; i++) {
                if (players[i] != null) {
                    players[i].send(STOC_HS_WATCH_CHANGE, wc);
                }
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
                    + (dp.type < seats ? " 加入了房间" : " 加入观战"));
        }
        // 回执本人：房间信息 + 自身座位
        dp.send(STOC_JOIN_GAME, hostInfo.toBytes());
        dp.send(STOC_TYPE_CHANGE, new byte[]{(byte) typeChange});
        // 把已存在的对手/队友与观战人数补发给新人
        for (int i = 0; i < seats; i++) {
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

    /** 首个空闲决斗位（0..seatCount()-1），已坐满返回 -1。 */
    private int firstFreeSeat() {
        int seats = seatCount();
        for (int i = 0; i < seats; i++) {
            if (players[i] == null) {
                return i;
            }
        }
        return -1;
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
        if (dp.type >= seatCount()) {
            dp.close();
            return;
        }
        if (duelStage == DUEL_STAGE_BEGIN) {
            players[dp.type] = null;
            ready[dp.type] = false;
            byte[] pc = new byte[]{(byte) ((dp.type << 4) | PLAYERCHANGE_LEAVE)};
            for (int i = 0; i < seatCount(); i++) {
                if (players[i] != null && i != dp.type) {
                    players[i].send(STOC_HS_PLAYER_CHANGE, pc);
                }
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
        if (dp.type != NETPLAYER_TYPE_OBSERVER) {
            return;
        }
        int pos = firstFreeSeat();
        if (pos < 0) {
            return;
        }
        observers.remove(dp);
        players[pos] = dp;
        dp.type = pos;
        byte[] enter = playerEnterPayload(dp.name, pos);
        byte[] wc = watchChangePayload(observers.size());
        sendToAllPresent(STOC_HS_PLAYER_ENTER, enter);
        sendToAllPresent(STOC_HS_WATCH_CHANGE, wc);
        dp.send(STOC_TYPE_CHANGE, new byte[]{(byte) ((dp == hostPlayer ? 0x10 : 0) | dp.type)});
    }

    void toObserver(ServerConnection dp) {
        if (dp.type >= seatCount()) {
            return;
        }
        byte[] pc = new byte[]{(byte) ((dp.type << 4) | PLAYERCHANGE_OBSERVE)};
        for (int i = 0; i < seatCount(); i++) {
            if (players[i] != null) {
                players[i].send(STOC_HS_PLAYER_CHANGE, pc);
            }
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
        if (dp.type >= seatCount() || ready[dp.type] == isReady) {
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
        for (int i = 0; i < seatCount(); i++) {
            ServerConnection p = players[i];
            // solo 下一条连接占两席位，按连接身份去重避免双收同一条准备回显
            if (p != null && (i == 0 || p != players[0])) {
                p.send(STOC_HS_PLAYER_CHANGE, pc);
            }
        }
        for (ServerConnection o : observers) {
            o.send(STOC_HS_PLAYER_CHANGE, pc);
        }
    }

    void playerKick(ServerConnection dp, int pos) {
        if (pos >= seatCount() || dp != hostPlayer || dp == players[pos] || players[pos] == null) {
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
        if (dp.type >= seatCount() || (soloMode ? full : ready[dp.type])) {
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
            if (allSeatsReady() && duel != null) {
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

    /** 本房间全部决斗位都已准备（TAG 需 4 席位，其余 2）。 */
    private boolean allSeatsReady() {
        int seats = seatCount();
        for (int i = 0; i < seats; i++) {
            if (!ready[i]) {
                return false;
            }
        }
        return true;
    }

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
            if (!allSeatsReady()) {
                return;
            }
        }
        // 对齐 single_duel.cpp / tag_duel.cpp StartDuel 的 NetServer::StopListen()：开局即停监听，
        // 决斗进行中不接受任何新连接（观战也只在开局前入座，本局中途接入无通讯可补）。
        server.stopListen();
        int seats = seatCount();
        ServerConnection seat0 = players[0];
        for (int i = 0; i < seats; i++) {
            // solo 下 players[1]==players[0] 为同一连接，按身份去重，避免开局包双发
            if (players[i] != null && (i == 0 || players[i] != seat0)) {
                players[i].send(STOC_DUEL_START, null);
            }
        }
        for (ServerConnection o : observers) {
            o.state = CTOS_LEAVE_GAME;
            o.send(STOC_DUEL_START, null);
        }
        if (tagMode) {
            // TAG：卡组堆叠按队下发（对齐 tag_duel.cpp L303-310）——本队主位 decks[0]/decks[2] 在前，
            // 同队两席位收到同一份，另一队两席位收到前后半交换的那一份。
            byte[] teamA = PlayerDeck.deckCountPayload(decks[0], decks[2], 0);
            byte[] teamB = PlayerDeck.deckCountPayload(decks[0], decks[2], 1);
            if (soloMode) {
                players[0].send(STOC_DECK_COUNT, teamA);
            } else {
                players[0].send(STOC_DECK_COUNT, teamA);
                players[1].send(STOC_DECK_COUNT, teamA);
                players[2].send(STOC_DECK_COUNT, teamB);
                players[3].send(STOC_DECK_COUNT, teamB);
            }
        } else {
            players[0].send(STOC_DECK_COUNT, PlayerDeck.deckCountPayload(decks[0], decks[1], 0));
            if (!soloMode) {
                players[1].send(STOC_DECK_COUNT, PlayerDeck.deckCountPayload(decks[0], decks[1], 1));
            }
        }
        // STOC_DECK_COUNT 只发决斗位（对齐 single_duel.cpp L346-351 / tag_duel.cpp L303-309）：
        // 观战席位不收本包，它的卡组/额外堆叠由 TPResult 里发给它的那条 MSG_START 按帧内 deckc/
        // extrac 建立，与桌面 gframe 完全一致。
        if (soloMode) {
            // Skip rock-paper-scissors: directly ask host to choose first/second
            players[0].send(STOC_SELECT_TP, null);
            tpPlayer = 0;
            players[0].state = CTOS_TP_RESULT;
            duelStage = DUEL_STAGE_FIRSTGO;
        } else {
            // 猜拳只由两队首席操作（对齐 tag_duel.cpp L311-316：players[0] 与 players[2]）
            for (int team = 0; team < 2; team++) {
                ServerConnection cap = players[teamCaptainSeat(team)];
                if (cap == null) {
                    return;
                }
                cap.send(STOC_SELECT_HAND, null);
                cap.state = CTOS_HAND_RESULT;
            }
            handResult[0] = 0;
            handResult[1] = 0;
            duelStage = DUEL_STAGE_FINGER;
        }
    }

    /** 把同一包发给某队的全部决斗位（真 TAG 两席，其余单席），观战另走 observers。 */
    private void sendToTeamSeats(int team, int proto, byte[] payload) {
        for (ServerConnection c : teamSeats(team)) {
            if (c != null) {
                c.send(proto, payload);
            }
        }
    }

    void handResult(ServerConnection dp, int res) {
        if (res == 0 || res > 3 || dp.state != CTOS_HAND_RESULT) {
            return;
        }
        // 每队只收一份手势（对齐 tag_duel.cpp L322：player = (dp->type & 0x2) >> 1）
        int p = seatTeam(dp.type);
        if (handResult[p] != 0) {
            return;
        }
        handResult[p] = res;
        if (handResult[0] != 0 && handResult[1] != 0) {
            byte[] ab = new byte[]{(byte) handResult[0], (byte) handResult[1]};
            byte[] ba = new byte[]{(byte) handResult[1], (byte) handResult[0]};
            // A 队（含其两席位）与观战收 res1=自己、B 队收 res1=对方
            sendToTeamSeats(0, STOC_HAND_RESULT, ab);
            for (ServerConnection o : observers) {
                o.send(STOC_HAND_RESULT, ab);
            }
            sendToTeamSeats(1, STOC_HAND_RESULT, ba);
            int capA = teamCaptainSeat(0);
            int capB = teamCaptainSeat(1);
            if (handResult[0] == handResult[1]) {
                players[capA].send(STOC_SELECT_HAND, null);
                players[capB].send(STOC_SELECT_HAND, null);
                handResult[0] = 0;
                handResult[1] = 0;
                players[capA].state = CTOS_HAND_RESULT;
                players[capB].state = CTOS_HAND_RESULT;
            } else if ((handResult[0] == 1 && handResult[1] == 2)
                    || (handResult[0] == 2 && handResult[1] == 3)
                    || (handResult[0] == 3 && handResult[1] == 1)) {
                players[capB].send(STOC_SELECT_TP, null);
                tpPlayer = 1;
                players[capA].state = ServerConnection.STATE_NONE;
                players[capB].state = CTOS_TP_RESULT;
                duelStage = DUEL_STAGE_FIRSTGO;
            } else {
                players[capA].send(STOC_SELECT_TP, null);
                players[capB].state = ServerConnection.STATE_NONE;
                players[capA].state = CTOS_TP_RESULT;
                tpPlayer = 0;
                duelStage = DUEL_STAGE_FIRSTGO;
            }
        }
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
        byte[] payload = chatPayload(dp.type, msg);
        // 中继统一走 sendToAllPresent：solo 模式下 players[1] == players[0] 为同一连接，
        // 按连接身份去重，避免同一条聊天被双发（历史缺陷：双端收到重复消息/重复弹幕）
        sendToAllPresent(STOC_CHAT, payload);
        // 录像：对局中把玩家/观战发言录为 0xF1 伪帧，使纯消息流回放血条下按时间线重现；
        // 与引擎消息同处单线程房间执行器，追加顺序即时序。playerType 取 dp.type（与实时
        // STOC_CHAT 首字节同值，回放分侧/命名一致）；文本取 UTF-16LE 并去除尾部 NUL 码元。
        if (isDueling() && duel != null && duel.replay != null) {
            int units = msg.length / 2;
            while (units > 0 && ((msg[units * 2 - 2] & 0xFF) | ((msg[units * 2 - 1] & 0xFF) << 8)) == 0) {
                units--;
            }
            String text = new String(msg, 0, units * 2, StandardCharsets.UTF_16LE);
            duel.replay.writeChatFrame(dp.type, text);
            // 录制验证落点：每条对局聊天录为 0xF1 伪帧均留痕，logcat 过滤 "GameRoom" 即可
            // 确认聊天已落盘；回放开场信息另标注文件内聊天帧总条数（ReplayPlayer.buildReplayInfo）
            Log.i(TAG, "replay chat frame recorded: type=" + dp.type + " len=" + text.length());
        } else if (dp.type < 4) {
            Log.d(TAG, "chat not recorded (dueling=" + isDueling()
                    + " duel=" + (duel != null) + "): stage not in-duel");
        }
    }
}
