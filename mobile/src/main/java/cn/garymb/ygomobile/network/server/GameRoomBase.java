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
 * LAN game room state machine, base layer of the split inheritance
 * chain (GameRoomBase <- GameRoomMid <- GameRoom). Owns every room
 * state field, the solo slot helpers, the STOC sending utilities,
 * the payload builders and the HostInfo record. The original
 * package-private constructor body lives here under the base name
 * so the final server / hostInfo fields keep their finality.
 */
abstract class GameRoomBase implements YGOProtocol {

    protected static final String TAG = "GameRoom";

    /** STOC_HS_PLAYER_CHANGE 状态位（network.h PLAYERCHANGE_*）。 */
    static final int PLAYERCHANGE_OBSERVE = 0x8;
    static final int PLAYERCHANGE_READY = 0x9;
    static final int PLAYERCHANGE_NOTREADY = 0xa;
    static final int PLAYERCHANGE_LEAVE = 0xb;

    /** 观战人数上限（对齐 game.h DEFAULT_WATCHER，取一个保守值）。 */
    protected static final int MAX_OBSERVER = 50;

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


    GameRoomBase(LanGameServer server) {
        this.server = server;
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

    protected void sendToAllPresent(int proto, byte[] payload) {
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

    protected void broadcastPlayersAndObservers(int proto, byte[] payload) {
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
