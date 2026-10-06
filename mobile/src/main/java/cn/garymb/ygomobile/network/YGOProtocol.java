package cn.garymb.ygomobile.network;

public interface YGOProtocol {
    int NETWORK_SERVER_ID = 0x7428;
    int NETWORK_CLIENT_ID = 0xdef6;

    int NETPLAYER_TYPE_PLAYER1 = 0;
    int NETPLAYER_TYPE_PLAYER2 = 1;
    int NETPLAYER_TYPE_OBSERVER = 7;

    /**
     * STOC_CHAT 载荷首字节 u16（chat_player_type）语义，对齐 gframe duelclient.cpp STOC_CHAT：
     * 0-3=决斗座位、4-6=备用位、7=观战（{@code NETPLAYER_TYPE_OBSERVER}）、8=服务端系统消息、
     * 9=脚本错误、10=隐藏名；除 8 与 11-19 外的非玩家类型在客户端一律归一为 10 显示。
     */
    int CHAT_PLAYER_TYPE_SYSTEM = 8;
    int CHAT_PLAYER_TYPE_SCRIPT_ERROR = 9;
    int CHAT_PLAYER_TYPE_HIDDEN_NAME = 10;

    int CTOS_RESPONSE = 0x1;
    int CTOS_UPDATE_DECK = 0x2;
    int CTOS_HAND_RESULT = 0x3;
    int CTOS_TP_RESULT = 0x4;
    int CTOS_PLAYER_INFO = 0x10;
    int CTOS_CREATE_GAME = 0x11;
    int CTOS_JOIN_GAME = 0x12;
    int CTOS_LEAVE_GAME = 0x13;
    int CTOS_SURRENDER = 0x14;
    int CTOS_TIME_CONFIRM = 0x15;
    int CTOS_CHAT = 0x16;
    int CTOS_EXTERNAL_ADDRESS = 0x17;
    int CTOS_HS_TODUELIST = 0x20;
    int CTOS_HS_TOOBSERVER = 0x21;
    int CTOS_HS_READY = 0x22;
    int CTOS_HS_NOTREADY = 0x23;
    int CTOS_HS_KICK = 0x24;
    int CTOS_HS_START = 0x25;
    /** 撤回上一步操作（本工程扩展包号，gframe 无对应）：局域网房间内由房主或刚应答的一方发起。 */
    int CTOS_UNDO = 0x26;
    /**
     * 索取询问重发（本工程扩展包号）：客户端已经答完上一步、但之后一段时间再没有任何消息进来
     * （既没有新询问也没有等待提示）时发此包，请服务端把引擎此刻挂在它席位上的那条询问
     * 原样重发一次。无载荷、不改引擎、不写录像，仅在服务端声明 {@code HOST_CAP_ASK_RESEND}
     * 时才会发出，因此不会误落在不了解该包号的第三方 gframe 服务器上。
     */
    int CTOS_ASK_RESEND = 0x27;

    int STOC_GAME_MSG = 0x1;
    int STOC_ERROR_MSG = 0x2;
    int STOC_SELECT_HAND = 0x3;
    int STOC_SELECT_TP = 0x4;
    int STOC_HAND_RESULT = 0x5;
    int STOC_TP_RESULT = 0x6;
    int STOC_CHANGE_SIDE = 0x7;
    int STOC_WAITING_SIDE = 0x8;
    int STOC_DECK_COUNT = 0x9;
    int STOC_CREATE_GAME = 0x11;
    int STOC_JOIN_GAME = 0x12;
    int STOC_TYPE_CHANGE = 0x13;
    int STOC_LEAVE_GAME = 0x14;
    int STOC_DUEL_START = 0x15;
    int STOC_DUEL_END = 0x16;
    int STOC_REPLAY = 0x17;
    int STOC_TIME_LIMIT = 0x18;
    int STOC_CHAT = 0x19;
    int STOC_HS_PLAYER_ENTER = 0x20;
    int STOC_HS_PLAYER_CHANGE = 0x21;
    int STOC_HS_WATCH_CHANGE = 0x22;
    int STOC_TEAMMATE_SURRENDER = 0x23;
    int STOC_FIELD_FINISH = 0x30;
    /** 撤回结果（本工程扩展包号）：载荷 [result(1B)][turn(1B)][currentPlayer(1B)][phase(u16 LE)]，
     *  result 为 UNDO_ACK_* 之一；除拒绝外，每种结果之后紧跟一条 MSG_RELOAD_FIELD 全量重同步。 */
    int STOC_UNDO_ACK = 0x24;
    /** 可撤回状态（本工程扩展包号）：1 字节 [0|1]，按连接身份分别下发——1 = 本席位（或房主可
     *  代撤的范围内）存在可回退的动作锚点，客户端据此让撤回图标闪动发光提示「现在可以撤回」。 */
    int STOC_UNDO_STATE = 0x25;

    int ERRMSG_JOINERROR = 0x1;
    int ERRMSG_DECKERROR = 0x2;
    int ERRMSG_SIDEERROR = 0x3;
    int ERRMSG_VERERROR = 0x4;

    int DECKERROR_LFLIST      = 0x1;
    int DECKERROR_OCGONLY     = 0x2;
    int DECKERROR_TCGONLY     = 0x3;
    int DECKERROR_UNKNOWNCARD = 0x4;
    int DECKERROR_CARDCOUNT   = 0x5;
    int DECKERROR_MAINCOUNT   = 0x6;
    int DECKERROR_EXTRACOUNT  = 0x7;
    int DECKERROR_SIDECOUNT   = 0x8;
    int DECKERROR_NOTAVAIL    = 0x9;

    int MODE_SINGLE = 0x0;
    int MODE_MATCH = 0x1;
    int MODE_TAG = 0x2;

    /** STOC_UNDO_ACK.result：撤回成功，随后是全场重载重同步（回合号/阶段随载荷回填）。 */
    int UNDO_ACK_OK = 0;
    /** STOC_UNDO_ACK.result：回退重建失败，已按撤回前的局面整体重建并重同步（界面不变、告知失败）。 */
    int UNDO_ACK_REBUILT = 1;
    /** STOC_UNDO_ACK.result：拒绝撤回（无记录/无权限/观战者），无后续重同步。 */
    int UNDO_ACK_DENIED = 2;

    /**
     * HostInfo pad[0] 的本工程私有扩展魔术字节（gframe 侧该 3 字节为未定义填充，
     * 第三方服务器不会写出此值）：只有等于该值时 pad[1] 才被当作服务器能力位。
     */
    int HOST_EXT_MAGIC = 0x55;
    /** HostInfo pad[1] 能力位：bit0 = 本机服务端实现了 CTOS_UNDO（撤回上一步操作）。 */
    int HOST_CAP_UNDO = 0x1;
    /** HostInfo pad[1] 能力位：bit1 = 本机服务端实现了 CTOS_ASK_RESEND（询问丢失自愈重挂）。 */
    int HOST_CAP_ASK_RESEND = 0x2;

    int DUEL_STAGE_BEGIN = 0;
    int DUEL_STAGE_FINGER = 1;
    int DUEL_STAGE_FIRSTGO = 2;
    int DUEL_STAGE_DUELING = 3;
    int DUEL_STAGE_SIDING = 4;
    int DUEL_STAGE_END = 5;
}
