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
        hostInfo.extCaps = HOST_CAP_UNDO | HOST_CAP_ASK_RESEND | HOST_CAP_RESUME;
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
        // 对局进行中的意外掉线不再立刻判负（用户规格）：先把席位留在宽限期内等同一玩家重连，
        // 并向其它端下发「该席位正在重连」指示（头像转圈 + 半透黑底）；主动退出（CTOS_LEAVE_GAME）
        // 不经本路，仍按原规则立即离开
        if (holdDetachedSeat(conn)) {
            return;
        }
        leaveGame(conn);
    }

    /**
     * 把刚掉线的决斗位按原席位号保留在宽限期内。
     *
     * <p>为什么保留连接对象而不是置空 {@code players[seat]}：引擎、区域刷新与询问挂回全部靠
     * {@code room.duelPlayer(team)} 解席位，置空会让服务端认为“无人在答”而挂不住询问，回不来也重挂不了；
     * 保留并用 {@code linkDetached} 屏蔽写入，则引擎对同一席位的一切下行都是空操作，局面自然停在
     * 掉线那一刻（限时局仍按原有计时走到超时判负，与 C++ 一致）。
     *
     * <p>房主连接掉线不保留：房主就是建主端自己，它没了意味着本机正在退出/停服，重连无意义。
     *
     * @return true 表示本席位已进入重连宽限期（调用方不得再走离开流程）
     */
    private boolean holdDetachedSeat(ServerConnection dp) {
        if (dp == hostPlayer || duel == null) {
            return false;
        }
        final int seat = dp.type;
        if (seat == NETPLAYER_TYPE_OBSERVER || seat >= seatCount()) {
            return false;
        }
        if (duelStage != DUEL_STAGE_FINGER && duelStage != DUEL_STAGE_FIRSTGO
                && duelStage != DUEL_STAGE_DUELING && duelStage != DUEL_STAGE_SIDING) {
            return false;
        }
        dp.linkDetached = true;
        detachedAtMs[seat] = System.currentTimeMillis();
        Log.i(TAG, "席位 " + seat + "（" + displayName(dp) + "）掉线，保留 "
                + (RESUME_GRACE_MS / 1000L) + "s 等待重连");
        notifyPeerLinkState(seat, LINK_STATE_RECONNECTING);
        broadcastSystemChat(displayName(dp) + " 连接中断，正在尝试重连…");
        server.scheduleDetachedSeatExpiry(this, seat);
        return true;
    }

    /** 向在场其它端广播某席位的链路状态（观战同步可见：他们不显示头像指示但聊天流里有通报）。 */
    void notifyPeerLinkState(int seat, int state) {
        byte[] payload = new byte[]{(byte) seat, (byte) state};
        for (int i = 0; i < seatCount(); i++) {
            ServerConnection c = players[i];
            if (c != null && i != seat) {
                c.send(STOC_PEER_LINK_STATE, payload);
            }
        }
        for (ServerConnection o : observers) {
            o.send(STOC_PEER_LINK_STATE, payload);
        }
    }

    /**
     * 宽限期耗尽仍未重连：按原有的“对手离开”收尾（席位置空、对局结束并结算），同时通知其它端
     * 指示可以收回。由 {@link LanGameServer#scheduleDetachedSeatExpiry} 延时投递到房间执行器，
     * 已重连（{@code detachedAtMs[seat]} 已清零）或席位已换人时空转。
     */
    void onDetachedSeatExpired(int seat) {
        if (seat < 0 || seat >= detachedAtMs.length || detachedAtMs[seat] == 0L) {
            return;
        }
        ServerConnection dp = players[seat];
        detachedAtMs[seat] = 0L;
        Log.i(TAG, "席位 " + seat + " 重连宽限期耗尽，按离开处理");
        notifyPeerLinkState(seat, LINK_STATE_GONE);
        if (dp == null) {
            return;
        }
        dp.linkDetached = false;
        broadcastSystemChat(displayName(dp) + " 重连超时，已离开房间");
        if (duel != null) {
            duel.onOpponentLeft(dp);
        } else {
            players[seat] = null;
            ready[seat] = false;
        }
        dp.close();
    }

    /**
     * CTOS_RESUME_GAME（本工程扩展）：掉线席位带原席位号与房间密码重新连入，把它接回进行中的对局。
     * <p>身份核对只用房间密码（与加入同一凭证）：同一设备上重连的昵称可能与掉线前不同（玩家改过
     * 名字），强行比对昵称会把正常重连拒之门外；席位号+密码已足以限定“只能接回自己原来那一席”。
     * 昵称与掉线前不一致时只记日志不拒。
     */
    void resumeGame(ServerConnection conn, ByteBuffer body) {
        if (body == null || body.remaining() < 48) {
            conn.close();
            return;
        }
        int version = body.getShort() & 0xFFFF;
        body.getShort(); // padding u16
        int seat = body.getShort() & 0xFFFF;
        body.getShort(); // padding u16（CTOS_JoinGame 的 gameid 高 16 位）
        String pass = BufferIO.readUTF16(body, 20);
        if (version != Constants.PRO_VERSION || !pass.equals(roomPass)
                || seat >= seatCount() || detachedAtMs[seat] == 0L) {
            Log.w(TAG, "resume 被拒: seat=" + seat + " version=" + version
                    + " detached=" + (seat < detachedAtMs.length ? detachedAtMs[seat] : -1));
            sendError(conn, ERRMSG_JOINERROR, 2);
            conn.close();
            return;
        }
        ServerConnection dead = players[seat];
        if (dead != null && !dead.name.equals(conn.name)) {
            Log.i(TAG, "resume 昵称与掉线前不同（" + dead.name + " → " + conn.name + "），仍按席位接回");
        }
        if (conn.name == null || conn.name.isEmpty()) {
            conn.name = dead != null ? dead.name : "";
        }
        detachedAtMs[seat] = 0L;
        // 新连接替下已断的连接：席位表与开局前席位/TAG 行动席位一并换人，后续下行全部走新 socket
        if (dead != null) {
            dead.linkDetached = false;
            for (int i = 0; i < pplayer.length; i++) {
                if (pplayer[i] == dead) pplayer[i] = conn;
            }
            for (int i = 0; i < curPlayer.length; i++) {
                if (curPlayer[i] == dead) curPlayer[i] = conn;
            }
        }
        players[seat] = conn;
        conn.type = seat;
        conn.host = dead != null && dead.host;
        conn.state = ServerConnection.STATE_FREE;
        // 回执本人席位与服务端侧真值（回合/当前玩家/阶段），紧接的重同步批靠本包补齐；
        // duel 为空（理论上进不到本分支，掉线保留只对存在的对局生效）时给零值而不是 null，
        // 以免客户端因包长不足而丢弃回执、始终收不到接回确认只能始终转圈到重试超时
        conn.send(STOC_RESUME_ACK, duel != null ? duel.resumeAckPayload(seat)
                : new byte[]{(byte) seat, 0, 0, 0, 0});
        conn.send(STOC_TYPE_CHANGE, new byte[]{(byte) ((conn.host ? 0x10 : 0) | seat)});
        notifyPeerLinkState(seat, LINK_STATE_RESUMED);
        broadcastSystemChat(displayName(conn) + " 已重新连接");
        if (duel != null) {
            duel.resyncResumedPeer(conn);
        }
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
        String text = decodeChatText(msg).trim();
        // 聊天 “/ai [卡组名]” 命令（本工程扩展）：主机端据此拉起人机加入本房间，纯 /ai 为随机
        if (text.equals(AI_CHAT_COMMAND)) {
            handleAiChatCommand("");
        } else if (text.startsWith(AI_CHAT_COMMAND + " ")) {
            handleAiChatCommand(text.substring(AI_CHAT_COMMAND.length() + 1).trim());
        }
        byte[] payload = chatPayload(dp.type, msg);
        // 仅人机房间里的 AI 决斗席位（非房主、非观战）才过闸门；观战/房主/局域网对局一律照常
        boolean aiSeat = peerNeedsReplayResync && dp != hostPlayer && duel != null
                && dp.type >= 0 && dp.type < seatCount();
        // 人机房间：AI 席位只在自己正要操作时才中继发言——
        // ① 撤回的静默重放期一律丢弃（重放会让人机对旧询问重复回话）；
        // ② 必须是本队当前行动席位；
        // ③ 本回合属于它所在队伍，或它正在行动：引擎刚把询问交给它这一队（beingAsked），
        //    或它刚完成一次行动（justActed，带时间窗）——对手回合发动卡片效果/连锁应答属于后者，应放行；
        //    其余非本回合的牵连回话依旧丢弃。
        // 玩家（房主）发言不受限；单局/比赛只有一个 AI 且免回合限制，观感与原先一致。
        if (aiSeat) {
            int team = seatTeam(dp.type);
            boolean silentReplay = duel.isSilentReplay();
            boolean acting = dp == duelPlayer(team);
            boolean ownTurn = seatCount() <= 2 || duel.currentTurnPlayer() == team;
            boolean beingAsked = lastResponse == team;
            long sinceAct = System.currentTimeMillis() - lastActingAtMs;
            boolean justActed = lastActingSeat == dp.type
                    && sinceAct >= 0 && sinceAct <= AI_CHAT_ACTION_WINDOW_MS;
            if (silentReplay || !acting || (!ownTurn && !beingAsked && !justActed)) {
                Log.i(TAG, "chat dropped: AI seat " + dp.type
                        + (silentReplay ? " during undo replay"
                        : !acting ? " is not the acting seat of team " + team
                        : " is neither on its own turn nor taking an action"));
                return;
            }
        }
        // 人机席位的操作播报排队等候：WindBot 会在一个决策点把整轮行动说明一次发完（先发聊天、后发应答），
        // 立即中继会出现「台词先于动作」且「一股脑提前弹出所有步骤」。改为入队，每个被接受的应答只
        // 放行一条（见 {@code ServerDuelBase#getResponse} → flushPendingAiChat），使台词与实际操作
        // 节点逐一对齐；应答被拒/被吞、撤回、回合切换或本局终止时队列清空（未发生的动作不得播报）。
        if (aiSeat && dp == duelPlayer(seatTeam(dp.type))) {
            if (dp.pendingAiChats.size() >= AI_CHAT_QUEUE_LIMIT) {
                Log.w(TAG, "chat queue overflow: AI seat " + dp.type + " drops new line");
                return;
            }
            dp.pendingAiChats.addLast(new ServerConnection.PendingChat(payload, text));
            Log.i(TAG, "chat queued until action lands: seat " + dp.type
                    + " len=" + text.length() + " queue=" + dp.pendingAiChats.size());
            return;
        }
        // 中继统一走 broadcastChat：人机房间只发给房主与观战（AI 不收聊天，避免回话刷屏）；
        // solo 模式下 players[1] == players[0] 为同一连接，按连接身份去重，避免同一条聊天被双发
        broadcastChat(payload);
        recordChatFrame(dp.type, text);
    }

    /**
     * 放行一条被压住的人机操作播报（它的应答已被引擎接受、动作消息已下发），并补录回放聊天帧。
     * 每次应答只放一条：人机若在决策点把整轮计划一次说完，台词会随其后的逐个操作依次出现。
     */
    void flushPendingAiChat(ServerConnection dp) {
        if (dp == null) {
            return;
        }
        ServerConnection.PendingChat chat = dp.pendingAiChats.pollFirst();
        if (chat == null) {
            return;
        }
        broadcastChat(chat.payload);
        recordChatFrame(dp.type, chat.text == null ? "" : chat.text);
        Log.i(TAG, "chat released after action: seat " + dp.type
                + " (left=" + dp.pendingAiChats.size() + ")");
    }

    /** 丢弃压住的人机操作播报（应答被拒/被吞、回合切换或本局终止：未发生的动作不得播报）。 */
    void clearPendingAiChat(ServerConnection dp) {
        if (dp != null) {
            dp.pendingAiChats.clear();
        }
    }

    /** 录像：对局中把玩家/观战/人机发言录为 0xF1 伪帧，使纯消息流回放血条下按时间线重现。 */
    private void recordChatFrame(int playerType, String text) {
        if (isDueling() && duel != null && duel.replay != null) {
            duel.replay.writeChatFrame(playerType, text);
            Log.i(TAG, "replay chat frame recorded: type=" + playerType + " len=" + text.length());
        } else if (playerType < 4) {
            Log.d(TAG, "chat not recorded (dueling=" + isDueling()
                    + " duel=" + (duel != null) + "): stage not in-duel");
        }
    }

    /** 聊天 “/ai” 命令前缀（后跟一个空格与卡组名，与线上服务器约定一致）。 */
    private static final String AI_CHAT_COMMAND = "/ai";

    /** 人机聊天闸门的“刚行动过”时间窗：应答/挂询问后该时长内仍允许 AI 播报（覆盖应答与聊天包的先后错位）。 */
    private static final long AI_CHAT_ACTION_WINDOW_MS = 5000L;

    /** 人机操作播报的等待队列上限：超过则丢弃新台词（多为超出本轮实际操作步数的计划尾句）。 */
    private static final int AI_CHAT_QUEUE_LIMIT = 8;

    /** UTF-16LE 聊天文本解码：去掉尾部 NUL 码元，供 /ai 命令判定与录像帧共用。 */
    private static String decodeChatText(byte[] msg) {
        int units = msg.length / 2;
        while (units > 0 && ((msg[units * 2 - 2] & 0xFF) | ((msg[units * 2 - 1] & 0xFF) << 8)) == 0) {
            units--;
        }
        return new String(msg, 0, units * 2, StandardCharsets.UTF_16LE);
    }

    /** “/ai [卡组名]” 命令：仅建房等待阶段、非纯单人且仍有空闲决斗位时，请求主机端拉起人机（空名=随机）。 */
    private void handleAiChatCommand(String deckName) {
        if (duelStage != DUEL_STAGE_BEGIN || soloMode) return;
        if (firstFreeSeat() < 0) return;
        server.requestChatBot(deckName, hostInfo.lflist);
    }
}
