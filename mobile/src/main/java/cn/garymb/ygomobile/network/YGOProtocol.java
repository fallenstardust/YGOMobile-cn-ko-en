package cn.garymb.ygomobile.network;

public interface YGOProtocol {
    int NETWORK_SERVER_ID = 0x7428;
    int NETWORK_CLIENT_ID = 0xdef6;

    int NETPLAYER_TYPE_PLAYER1 = 0;
    int NETPLAYER_TYPE_PLAYER2 = 1;
    int NETPLAYER_TYPE_OBSERVER = 7;

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

    int STOC_GAME_MSG = 0x1;
    int STOC_ERROR_MSG = 0x2;
    int STOC_SELECT_HAND = 0x3;
    int STOC_SELECT_TP = 0x4;
    int STOC_HAND_RESULT = 0x5;
    int STOC_CHANGE_SIDE = 0x7;
    int STOC_WAITING_SIDE = 0x8;
    int STOC_CREATE_GAME = 0x11;
    int STOC_JOIN_GAME = 0x12;
    int STOC_TYPE_CHANGE = 0x13;
    int STOC_DUEL_START = 0x15;
    int STOC_DUEL_END = 0x16;
    int STOC_REPLAY = 0x17;
    int STOC_TIME_LIMIT = 0x18;
    int STOC_CHAT = 0x19;
    int STOC_HS_PLAYER_ENTER = 0x20;
    int STOC_HS_PLAYER_CHANGE = 0x21;
    int STOC_HS_WATCH_CHANGE = 0x22;

    int ERRMSG_JOINERROR = 0x1;
    int ERRMSG_DECKERROR = 0x2;
    int ERRMSG_SIDEERROR = 0x3;
    int ERRMSG_VERERROR = 0x4;

    int MODE_SINGLE = 0x0;
    int MODE_MATCH = 0x1;
    int MODE_TAG = 0x2;

    int DUEL_STAGE_BEGIN = 0;
    int DUEL_STAGE_FINGER = 1;
    int DUEL_STAGE_FIRSTGO = 2;
    int DUEL_STAGE_DUELING = 3;
    int DUEL_STAGE_SIDING = 4;
    int DUEL_STAGE_END = 5;
}
