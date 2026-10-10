package cn.garymb.ygomobile.game;

import android.util.Log;

import java.nio.ByteBuffer;

/**
 * 引擎消息流切片器（与 {@code Classes/gframe/replay_mode.cpp::ReplayAnalyze} 的长度表同源，
 * SELECT 系长度与实况 {@code GameMessageParser} 的读取次序一致）。
 *
 * <p>录像内的 MSG 流是 ocgcore 原始输出拼接：每条 = 1 字节消息号 + 变/定长消息体，
 * 没有分隔符，只能按 common.h 消息号 + 固定长度表推进游标。回放（无论取自录像文件里的
 * 消息流还是旧格式重跑时引擎现产生的消息）都先用本类切出消息体，再投喂 GameEngine 实况管线。
 *
 * <p>UPDATE_DATA(6) 的消息体是「按区域条目排列的自界长块」，条目数取决于该区域当前卡片数，
 * 由调用方按实况同口径（{@code CommandDataParser.parseUpdateData}）经 {@link ZoneBlocks} 提供；
 * 调用方须在切片前等实况管线追上游标，否则两侧条目数不一致会导致游标错位。
 */
public final class ReplayMessageSlicer {

    private static final String TAG = "ReplaySlicer";

    /** 区域 query 块数量提供者（UPDATE_DATA 用），口径同实况 parseUpdateData */
    public interface ZoneBlocks {
        int count(int player, int location);
    }

    private ReplayMessageSlicer() {
    }

    /** 消息号真值（ocgcore common.h），仅本类内部使用，避免满屏魔法数字；
     *  MSG_WIN 需由回放投喂循环特判（结算是终止点），故公开 */
    public static final int MSG_WIN = 5;
    private static final int MSG_RETRY = 1, MSG_HINT = 2, MSG_WAITING = 3, MSG_START = 4,
            MSG_UPDATE_DATA = 6, MSG_UPDATE_CARD = 7, MSG_REQUEST_DECK = 8,
            MSG_SELECT_BATTLECMD = 10, MSG_SELECT_IDLECMD = 11, MSG_SELECT_EFFECTYN = 12,
            MSG_SELECT_YESNO = 13, MSG_SELECT_OPTION = 14, MSG_SELECT_CARD = 15,
            MSG_SELECT_CHAIN = 16, MSG_SORT_CHAIN = 17, MSG_SELECT_PLACE = 18,
            MSG_SELECT_POSITION = 19, MSG_SELECT_SUM = 23, MSG_SORT_CARD = 25,
            MSG_SELECT_UNSELECT_CARD = 26, MSG_SELECT_TRIBUTE = 20, MSG_SELECT_COUNTER = 22,
            MSG_SELECT_DISFIELD = 24, MSG_CONFIRM_DECKTOP = 30, MSG_CONFIRM_CARDS = 31,
            MSG_SHUFFLE_DECK = 32, MSG_SHUFFLE_HAND = 33, MSG_REFRESH_DECK = 34,
            MSG_SWAP_GRAVE_DECK = 35, MSG_SHUFFLE_SET_CARD = 36, MSG_REVERSE_DECK = 37,
            MSG_DECK_TOP = 38, MSG_SHUFFLE_EXTRA = 39, MSG_NEW_TURN = 40, MSG_NEW_PHASE = 41,
            MSG_CONFIRM_EXTRATOP = 42, MSG_MOVE = 50, MSG_POS_CHANGE = 53, MSG_SET = 54,
            MSG_SWAP = 55, MSG_FIELD_DISABLED = 56, MSG_SUMMONING = 60, MSG_SUMMONED = 61,
            MSG_SPSUMMONING = 62, MSG_SPSUMMONED = 63, MSG_FLIPSUMMONING = 64,
            MSG_FLIPSUMMONED = 65, MSG_CHAINING = 70, MSG_CHAINED = 71, MSG_CHAIN_SOLVING = 72,
            MSG_CHAIN_SOLVED = 73, MSG_CHAIN_END = 74, MSG_CHAIN_NEGATED = 75,
            MSG_CHAIN_DISABLED = 76, MSG_CARD_SELECTED = 80, MSG_RANDOM_SELECTED = 81,
            MSG_BECOME_TARGET = 83, MSG_DRAW = 90, MSG_DAMAGE = 91, MSG_RECOVER = 92,
            MSG_EQUIP = 93, MSG_LPUPDATE = 94, MSG_UNEQUIP = 95, MSG_CARD_TARGET = 96,
            MSG_CANCEL_TARGET = 97, MSG_PAY_LPCOST = 100, MSG_ADD_COUNTER = 101,
            MSG_REMOVE_COUNTER = 102, MSG_ATTACK = 110, MSG_BATTLE = 111,
            MSG_ATTACK_DISABLED = 112, MSG_DAMAGE_STEP_START = 113, MSG_DAMAGE_STEP_END = 114,
            MSG_MISSED_EFFECT = 120, MSG_TOSS_COIN = 130, MSG_TOSS_DICE = 131,
            MSG_ROCK_PAPER_SCISSORS = 132, MSG_HAND_RES = 133, MSG_ANNOUNCE_RACE = 140,
            MSG_ANNOUNCE_ATTRIB = 141, MSG_ANNOUNCE_CARD = 142, MSG_ANNOUNCE_NUMBER = 143,
            MSG_CARD_HINT = 160, MSG_TAG_SWAP = 161, MSG_RELOAD_FIELD = 162,
            MSG_AI_NAME = 163, MSG_SHOW_HINT = 164, MSG_PLAYER_HINT = 165,
            MSG_MATCH_KILL = 170;

    /** SELECT/询问/确认类：回放侧不弹交互窗，只推进游标（旧格式还需据此喂响应记录） */
    public static boolean isSelectable(int msgType) {
        return (msgType >= MSG_SELECT_BATTLECMD && msgType <= MSG_SELECT_UNSELECT_CARD)
                || msgType == MSG_ROCK_PAPER_SCISSORS
                || (msgType >= MSG_ANNOUNCE_RACE && msgType <= MSG_ANNOUNCE_NUMBER);
    }

    /** 不投喂实况管线的消息：SELECT/询问类（弹窗会悬挂流程）与 RETRY/WAITING/REQUEST_DECK */
    public static boolean isNoFeed(int msgType) {
        if (msgType == MSG_RETRY || msgType == MSG_WAITING || msgType == MSG_REQUEST_DECK) return true;
        return isSelectable(msgType);
    }

    /** 可见步（pauseable 收窄）：有卡片移动/大图/回合阶段推进的步才计步并等待节奏 */
    public static boolean isVisibleStep(int msgType) {
        switch (msgType) {
            case MSG_NEW_TURN:
            case MSG_NEW_PHASE:
            case MSG_MOVE:
            case MSG_POS_CHANGE:
            case MSG_SWAP:
            case MSG_SUMMONING: case MSG_SUMMONED: case MSG_SPSUMMONING:
            case MSG_SPSUMMONED: case MSG_FLIPSUMMONING: case MSG_FLIPSUMMONED:
            case MSG_CHAINING: case MSG_CHAINED:
            case MSG_DRAW:
            case MSG_ATTACK:
                return true;
            default:
                return false;
        }
    }

    /**
     * 按长度表推进游标切出一条消息体：调用后 buf 游标即消息体末尾。
     *
     * @return false 表示消息体不完整或消息号未知（游标已不可靠，调用方应终止回放）
     */
    public static boolean slice(int msgType, ByteBuffer buf, ZoneBlocks zones) {
        switch (msgType) {
            case MSG_RETRY:
            case MSG_WAITING:
            case MSG_SUMMONED: case MSG_SPSUMMONED: case MSG_FLIPSUMMONED:
            case MSG_CHAIN_END:
            case MSG_ATTACK_DISABLED: case MSG_DAMAGE_STEP_START: case MSG_DAMAGE_STEP_END:
            case MSG_MATCH_KILL:      // replay_mode.cpp 不消费 body
                return true;
            case MSG_HINT:            // type(1) player(1) data(4)
            case MSG_PLAYER_HINT:
                return takeFixed(buf, 6);
            case MSG_WIN:             // result(1) reason(1)（processor.cpp L5022-5031）
                return takeFixed(buf, 2);
            case MSG_START:           // player+duelrule+lp0+lp1+deckc/extrac*2（本机录制为 18 字节）
                return takeFixed(buf, 18);
            case MSG_REQUEST_DECK:
                return takeFixed(buf, 1);
            case MSG_UPDATE_DATA:     // player loc + 区域卡数×[clen(含自身)][payload]
                return walkUpdateDataBlocks(buf, zones);
            case MSG_UPDATE_CARD:     // player loc seq + 单块
                return takeFixed(buf, 3) && takeQueryBlock(buf);
            case MSG_SELECT_BATTLECMD: {  // player cnt + cnt*11 + cnt2*8 + 2
                if (!takeFixed(buf, 2)) return false;
                if (!takeFixed(buf, lastU8(buf) * 11 + 1)) return false;
                return takeFixed(buf, lastU8(buf) * 8 + 2);
            }
            case MSG_SELECT_IDLECMD: {    // player + 5组(cnt*7) + cnt*11 + 3
                if (!takeFixed(buf, 1)) return false;
                for (int t = 0; t < 5; t++) {
                    if (!takeFixed(buf, 1)) return false;
                    if (!takeFixed(buf, lastU8(buf) * 7)) return false;
                }
                if (!takeFixed(buf, 1)) return false;
                return takeFixed(buf, lastU8(buf) * 11 + 3);
            }
            case MSG_SELECT_EFFECTYN: return takeFixed(buf, 13);
            case MSG_SELECT_YESNO:    return takeFixed(buf, 5);
            case MSG_SELECT_OPTION:   // player cnt + cnt*4
                return takeFixed(buf, 2) && takeFixed(buf, lastU8(buf) * 4);
            case MSG_SELECT_CARD: case MSG_SELECT_TRIBUTE:  // player + 3 + cnt + cnt*8
                return takeFixed(buf, 5) && takeFixed(buf, lastU8(buf) * 8);
            case MSG_SELECT_CHAIN:    // player cnt + 9 + cnt*14
                return takeFixed(buf, 2) && takeFixed(buf, 9 + lastU8(buf) * 14);
            case MSG_SORT_CHAIN:      // player cnt + cnt*7
                return takeFixed(buf, 2) && takeFixed(buf, lastU8(buf) * 7);
            case MSG_SELECT_PLACE: case MSG_SELECT_POSITION: case MSG_SELECT_DISFIELD:
                return takeFixed(buf, 6);
            case MSG_SELECT_COUNTER:  // player + 4 + cnt + cnt*9
                return takeFixed(buf, 6) && takeFixed(buf, lastU8(buf) * 9);
            case MSG_SELECT_SUM: {      // op player + 6 + cnt + cnt*11 + cnt2*11
                if (!takeFixed(buf, 9)) return false;
                if (!takeFixed(buf, lastU8(buf) * 11 + 1)) return false;
                return takeFixed(buf, lastU8(buf) * 11);
            }
            case MSG_SORT_CARD:       // player cnt + cnt*7
                return takeFixed(buf, 2) && takeFixed(buf, lastU8(buf) * 7);
            case MSG_SELECT_UNSELECT_CARD: {  // player + 4 + cnt + cnt*8 + cnt2*8
                if (!takeFixed(buf, 6)) return false;
                if (!takeFixed(buf, lastU8(buf) * 8 + 1)) return false;
                return takeFixed(buf, lastU8(buf) * 8);
            }
            case MSG_CONFIRM_DECKTOP: case MSG_CONFIRM_EXTRATOP:  // player cnt + cnt*7
                return takeFixed(buf, 2) && takeFixed(buf, lastU8(buf) * 7);
            case MSG_CONFIRM_CARDS:   // player skip cnt + cnt*7
                return takeFixed(buf, 3) && takeFixed(buf, lastU8(buf) * 7);
            case MSG_SHUFFLE_DECK:    return takeFixed(buf, 1);
            case MSG_SHUFFLE_HAND:    // player cnt + cnt*4
                return takeFixed(buf, 2) && takeFixed(buf, lastU8(buf) * 4);
            case MSG_REFRESH_DECK: case MSG_SWAP_GRAVE_DECK: return takeFixed(buf, 1);
            case MSG_REVERSE_DECK:    return true;   // 无 body（replay_mode.cpp 不推进）
            case MSG_SHUFFLE_SET_CARD:// player cnt + cnt*8
                return takeFixed(buf, 2) && takeFixed(buf, lastU8(buf) * 8);
            case MSG_DECK_TOP:        return takeFixed(buf, 6);   // player seq code
            case MSG_SHUFFLE_EXTRA:   // player cnt + cnt*4
                return takeFixed(buf, 2) && takeFixed(buf, lastU8(buf) * 4);
            case MSG_NEW_TURN:        return takeFixed(buf, 1);
            case MSG_NEW_PHASE:       return takeFixed(buf, 2);
            case MSG_MOVE:            return takeFixed(buf, 16);
            case MSG_POS_CHANGE:      return takeFixed(buf, 9);
            case MSG_SET:             return takeFixed(buf, 8);
            case MSG_SWAP:            return takeFixed(buf, 16);
            case MSG_FIELD_DISABLED:  return takeFixed(buf, 4);
            case MSG_SUMMONING: case MSG_SPSUMMONING: case MSG_FLIPSUMMONING:
                return takeFixed(buf, 8);
            case MSG_CHAINING:        return takeFixed(buf, 16);
            case MSG_CHAINED: case MSG_CHAIN_SOLVING: case MSG_CHAIN_SOLVED:
            case MSG_CHAIN_NEGATED: case MSG_CHAIN_DISABLED:
                return takeFixed(buf, 1);
            case MSG_CARD_SELECTED: case MSG_RANDOM_SELECTED:  // player cnt + cnt*4
                return takeFixed(buf, 2) && takeFixed(buf, lastU8(buf) * 4);
            case MSG_BECOME_TARGET:   // cnt + cnt*4
                return takeFixed(buf, 1) && takeFixed(buf, lastU8(buf) * 4);
            case MSG_DRAW:            // player cnt + cnt*4
                return takeFixed(buf, 2) && takeFixed(buf, lastU8(buf) * 4);
            case MSG_DAMAGE: case MSG_RECOVER: case MSG_LPUPDATE: case MSG_PAY_LPCOST:
                return takeFixed(buf, 5);
            case MSG_EQUIP:           return takeFixed(buf, 8);
            case MSG_UNEQUIP:         return takeFixed(buf, 4);
            case MSG_CARD_TARGET: case MSG_CANCEL_TARGET:
                return takeFixed(buf, 8);
            case MSG_ADD_COUNTER: case MSG_REMOVE_COUNTER:
                return takeFixed(buf, 7);
            case MSG_ATTACK:          return takeFixed(buf, 8);
            case MSG_BATTLE:          return takeFixed(buf, 26);
            case MSG_MISSED_EFFECT:   return takeFixed(buf, 8);
            case MSG_TOSS_COIN: case MSG_TOSS_DICE:  // player cnt + cnt*1
                return takeFixed(buf, 2) && takeFixed(buf, lastU8(buf));
            case MSG_ROCK_PAPER_SCISSORS: return takeFixed(buf, 1);
            case MSG_HAND_RES:        return takeFixed(buf, 1);
            case MSG_ANNOUNCE_RACE: case MSG_ANNOUNCE_ATTRIB:
                return takeFixed(buf, 6);
            case MSG_ANNOUNCE_CARD: case MSG_ANNOUNCE_NUMBER:  // player cnt + cnt*4
                return takeFixed(buf, 2) && takeFixed(buf, lastU8(buf) * 4);
            case MSG_CARD_HINT:       return takeFixed(buf, 9);
            case MSG_TAG_SWAP: {      // player mainc extrac pcount handc + 顶码4 + handc*4 + extrac*4
                // 与 replay_mode.cpp `pbuf += pbuf[2]*4 + pbuf[4]*4 + 9` 同口径
                //（body[2]=额外数、body[4]=手卡数）
                if (!takeFixed(buf, 5)) return false;
                int extraCount = buf.get(buf.position() - 3) & 0xFF;
                int handCount = lastU8(buf);
                return takeFixed(buf, extraCount * 4 + handCount * 4 + 4);
            }
            case MSG_RELOAD_FIELD:    return sliceReloadField(buf);
            case MSG_AI_NAME: case MSG_SHOW_HINT: {  // uint16 len + str + nul
                if (!takeFixed(buf, 2)) return false;
                return takeFixed(buf, lastU16(buf) + 1);
            }
            default:
                Log.w(TAG, "slice: unknown msg " + msgType);
                return false;
        }
    }

    /**
     * 试算整条消息流的可见步总数（回放进度条用）：任何一条切片失败即止于该处。
     * 不改动传入 buf 的游标。
     */
    public static int countVisibleSteps(byte[] stream, ZoneBlocks zones) {
        ByteBuffer buf = ByteBuffer.wrap(stream).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        int visible = 0;
        while (buf.hasRemaining()) {
            int msgType = buf.get() & 0xFF;
            if (!slice(msgType, buf, zones)) break;
            if (isVisibleStep(msgType)) visible++;
        }
        return visible;
    }

    // ==== 游标推进基础件 ====

    /** UPDATE_DATA 体切片：player(1) + location(1) 后按区域条目数消费自界长块 */
    private static boolean walkUpdateDataBlocks(ByteBuffer buf, ZoneBlocks zones) {
        if (buf.remaining() < 2) return false;
        int player = buf.get() & 0xFF;
        int location = buf.get() & 0xFF;
        if (zones == null) return false;
        for (int i = 0, n = zones.count(player, location); i < n; i++) {
            if (!takeQueryBlock(buf)) return false;
        }
        return true;
    }

    /** MSG_RELOAD_FIELD 变长切片：player + 2×(4 + 7×(1+3标记) + 8×(1+2标记) + 6) + 1，
     *  与 gframe 定长口径一致（非空位才有附加字节） */
    private static boolean sliceReloadField(ByteBuffer buf) {
        if (!takeFixed(buf, 1)) return false;
        for (int p = 0; p < 2; p++) {
            if (!takeFixed(buf, 4)) return false;
            for (int s = 0; s < 7; s++) {
                if (!takeFixed(buf, 1)) return false;
                if (lastU8(buf) != 0 && !takeFixed(buf, 2)) return false;
            }
            for (int s = 0; s < 8; s++) {
                if (!takeFixed(buf, 1)) return false;
                if (lastU8(buf) != 0 && !takeFixed(buf, 1)) return false;
            }
            if (!takeFixed(buf, 6)) return false;
        }
        return takeFixed(buf, 1);
    }

    /** [int32 clen(含自身)][payload...] 单块游标推进 */
    private static boolean takeQueryBlock(ByteBuffer buf) {
        if (buf.remaining() < 4) return false;
        int clen = buf.getInt();
        int adv = clen - 4;
        if (adv < 0 || buf.remaining() < adv) return false;
        buf.position(buf.position() + adv);
        return true;
    }

    /** 定长推进：不足则判定游标不可靠（消息体截断） */
    private static boolean takeFixed(ByteBuffer buf, int n) {
        if (n < 0 || buf.remaining() < n) return false;
        buf.position(buf.position() + n);
        return true;
    }

    /** 切片时读取刚消费的最后一字节（均为 cnt/val 类字段） */
    private static int lastU8(ByteBuffer buf) {
        return buf.get(buf.position() - 1) & 0xFF;
    }

    /** 切片时读取刚消费的两个字节（uint16 小端长度，如 AI_NAME / SHOW_HINT 的字符串长） */
    private static int lastU16(ByteBuffer buf) {
        int p = buf.position();
        return (buf.get(p - 2) & 0xFF) | ((buf.get(p - 1) & 0xFF) << 8);
    }
}
