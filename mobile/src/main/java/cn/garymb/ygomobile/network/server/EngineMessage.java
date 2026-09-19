package cn.garymb.ygomobile.network.server;

/**
 * 服务器侧引擎消息常量与遮蔽工具。
 *
 * <p>对齐 {@code Classes/ocgcore/common.h} 的 MSG_* / POS_* / LOCATION_* 常量，
 * 以及 {@code Classes/gframe/netserver.h} 的 ShouldHideFacedownCode / StripRevealFlag、
 * {@code Classes/gframe/network.h} 的 GetPosition。
 *
 * <p>位置/姿态/查询/决斗规则等基础常量复用 {@link cn.garymb.ygomobile.engine.OcgDuelEngine}，
 * 本类只补充引擎消息号与服务器专用的组合掩码。
 */
public final class EngineMessage {

    private EngineMessage() {
    }

    // ===== 引擎消息号（common.h L264-357） =====
    public static final int MSG_RETRY = 1;
    public static final int MSG_HINT = 2;
    public static final int MSG_WAITING = 3;
    public static final int MSG_START = 4;
    public static final int MSG_WIN = 5;
    public static final int MSG_UPDATE_DATA = 6;
    public static final int MSG_UPDATE_CARD = 7;
    public static final int MSG_REQUEST_DECK = 8;
    public static final int MSG_SELECT_BATTLECMD = 10;
    public static final int MSG_SELECT_IDLECMD = 11;
    public static final int MSG_SELECT_EFFECTYN = 12;
    public static final int MSG_SELECT_YESNO = 13;
    public static final int MSG_SELECT_OPTION = 14;
    public static final int MSG_SELECT_CARD = 15;
    public static final int MSG_SELECT_CHAIN = 16;
    public static final int MSG_SELECT_PLACE = 18;
    public static final int MSG_SELECT_POSITION = 19;
    public static final int MSG_SELECT_TRIBUTE = 20;
    public static final int MSG_SELECT_COUNTER = 22;
    public static final int MSG_SELECT_SUM = 23;
    public static final int MSG_SELECT_DISFIELD = 24;
    public static final int MSG_SORT_CARD = 25;
    public static final int MSG_SELECT_UNSELECT_CARD = 26;
    public static final int MSG_CONFIRM_DECKTOP = 30;
    public static final int MSG_CONFIRM_CARDS = 31;
    public static final int MSG_SHUFFLE_DECK = 32;
    public static final int MSG_SHUFFLE_HAND = 33;
    public static final int MSG_REFRESH_DECK = 34;
    public static final int MSG_SWAP_GRAVE_DECK = 35;
    public static final int MSG_SHUFFLE_SET_CARD = 36;
    public static final int MSG_REVERSE_DECK = 37;
    public static final int MSG_DECK_TOP = 38;
    public static final int MSG_SHUFFLE_EXTRA = 39;
    public static final int MSG_NEW_TURN = 40;
    public static final int MSG_NEW_PHASE = 41;
    public static final int MSG_CONFIRM_EXTRATOP = 42;
    public static final int MSG_MOVE = 50;
    public static final int MSG_POS_CHANGE = 53;
    public static final int MSG_SET = 54;
    public static final int MSG_SWAP = 55;
    public static final int MSG_FIELD_DISABLED = 56;
    public static final int MSG_SUMMONING = 60;
    public static final int MSG_SUMMONED = 61;
    public static final int MSG_SPSUMMONING = 62;
    public static final int MSG_SPSUMMONED = 63;
    public static final int MSG_FLIPSUMMONING = 64;
    public static final int MSG_FLIPSUMMONED = 65;
    public static final int MSG_CHAINING = 70;
    public static final int MSG_CHAINED = 71;
    public static final int MSG_CHAIN_SOLVING = 72;
    public static final int MSG_CHAIN_SOLVED = 73;
    public static final int MSG_CHAIN_END = 74;
    public static final int MSG_CHAIN_NEGATED = 75;
    public static final int MSG_CHAIN_DISABLED = 76;
    public static final int MSG_CARD_SELECTED = 80;
    public static final int MSG_RANDOM_SELECTED = 81;
    public static final int MSG_BECOME_TARGET = 83;
    public static final int MSG_DRAW = 90;
    public static final int MSG_DAMAGE = 91;
    public static final int MSG_RECOVER = 92;
    public static final int MSG_EQUIP = 93;
    public static final int MSG_LPUPDATE = 94;
    public static final int MSG_UNEQUIP = 95;
    public static final int MSG_CARD_TARGET = 96;
    public static final int MSG_CANCEL_TARGET = 97;
    public static final int MSG_PAY_LPCOST = 100;
    public static final int MSG_ADD_COUNTER = 101;
    public static final int MSG_REMOVE_COUNTER = 102;
    public static final int MSG_ATTACK = 110;
    public static final int MSG_BATTLE = 111;
    public static final int MSG_ATTACK_DISABLED = 112;
    public static final int MSG_DAMAGE_STEP_START = 113;
    public static final int MSG_DAMAGE_STEP_END = 114;
    public static final int MSG_MISSED_EFFECT = 120;
    public static final int MSG_TOSS_COIN = 130;
    public static final int MSG_TOSS_DICE = 131;
    public static final int MSG_ROCK_PAPER_SCISSORS = 132;
    public static final int MSG_HAND_RES = 133;
    public static final int MSG_ANNOUNCE_RACE = 140;
    public static final int MSG_ANNOUNCE_ATTRIB = 141;
    public static final int MSG_ANNOUNCE_CARD = 142;
    public static final int MSG_ANNOUNCE_NUMBER = 143;
    public static final int MSG_CARD_HINT = 160;
    public static final int MSG_PLAYER_HINT = 165;
    public static final int MSG_MATCH_KILL = 170;

    // ===== 补全的位置/姿态掩码（common.h） =====
    public static final int LOCATION_OVERLAY = 0x80;
    public static final int LOCATION_MZONE = 0x04;
    public static final int LOCATION_SZONE = 0x08;
    public static final int LOCATION_HAND = 0x02;
    public static final int LOCATION_DECK = 0x01;
    public static final int LOCATION_GRAVE = 0x10;
    public static final int LOCATION_EXTRA = 0x40;
    public static final int LOCATION_ONFIELD = LOCATION_MZONE | LOCATION_SZONE;

    public static final int POS_FACEUP = 0x5;
    public static final int POS_FACEDOWN = 0xa;
    public static final int POS_REVEAL = 0x80;

    /** common.h LEN_HEADER */
    public static final int LEN_HEADER = 8;
    /** common.h SIZE_RETURN_VALUE */
    public static final int SIZE_RETURN_VALUE = 256;
    /** 查询缓冲区上限（对齐 gframe SIZE_QUERY_BUFFER 使用点） */
    public static final int SIZE_QUERY_BUFFER = 0x4000;

    /**
     * 对齐 network.h GetPosition(qbuf, offset)：读取 offset 处 4 字节小端整数并右移 24 位取姿态字节。
     */
    public static int getPosition(byte[] buf, int offset) {
        long info = (buf[offset] & 0xFFL)
                | ((buf[offset + 1] & 0xFFL) << 8)
                | ((buf[offset + 2] & 0xFFL) << 16)
                | ((buf[offset + 3] & 0xFFL) << 24);
        return (int) (info >> 24);
    }

    /** 对齐 netserver.h ShouldHideFacedownCode。 */
    public static boolean shouldHideFacedownCode(int position) {
        return (position & POS_FACEDOWN) != 0 && (position & POS_REVEAL) == 0;
    }

    /**
     * 对齐 netserver.h StripRevealFlag：清除 offset 处 4 字节整数的 bit(24+7)（POS_REVEAL<<24），返回姿态字节。
     */
    public static int stripRevealFlag(byte[] buf, int offset) {
        long info = (buf[offset] & 0xFFL)
                | ((buf[offset + 1] & 0xFFL) << 8)
                | ((buf[offset + 2] & 0xFFL) << 16)
                | ((buf[offset + 3] & 0xFFL) << 24);
        info &= ~((long) POS_REVEAL << 24);
        buf[offset] = (byte) (info & 0xFF);
        buf[offset + 1] = (byte) ((info >> 8) & 0xFF);
        buf[offset + 2] = (byte) ((info >> 16) & 0xFF);
        buf[offset + 3] = (byte) ((info >> 24) & 0xFF);
        return (int) (info >> 24);
    }
}
