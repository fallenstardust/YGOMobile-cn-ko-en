package cn.garymb.ygomobile.game;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.garymb.ygomobile.utils.ComparisonTableUtil;

/**
 * 回放先行码 → 正式码的运行时映射（不改写 .yrp 文件）：早期录像以 9 位先行号
 *（1002xxxxx / 1012xxxxx）记录卡码，正式卡库里没有这些号，回放时取不到卡图与名称而显示
 * unknown。按官方对照表 {@link ComparisonTableUtil}（同 DeckLoader/HomeFragment 的
 * ArrayUtil.contains(oldIDsArray)→newIDsArray 口径）在投喂实况管线前就地替换消息体内各处
 * 卡码，录像头部卡组/额外列表同步替换。
 *
 * <p>各消息的卡码偏移与 {@code ReplayEngine.sliceMsgBody} 的切片长度、
 * {@code GameMessageParser} 的读取次序逐条对齐（写侧口径见 ocgcore
 * processor.cpp / field.cpp / libduel.cpp）。SELECT 系消息含卡码但不投喂实况管线，不映射。
 */
public final class ReplayCodeMapper {

    /** query 块数量提供者：UPDATE_DATA 的块数随该区域当前条目数变化，
     *  由调用方按实况解析同一口径（{@code CommandDataParser.parseUpdateData}）给出 */
    public interface BlockCountProvider {
        int blockCount(int player, int location);
    }

    /** 先行号→正式号索引表：首次使用时按对照表建一次，之后 O(1) 查表 */
    private static volatile Map<Integer, Integer> codeMap;

    private ReplayCodeMapper() {
    }

    /** 单码映射：不在对照表内（正式码 / 0）原样返回；码值高位携带的翻面标志位保持不动 */
    public static int mapCode(int code) {
        if (code == 0) return 0;
        int faceUpFlag = code & 0x80000000;
        Integer mapped = table().get(code & 0x7fffffff);
        return mapped == null ? code : (mapped | faceUpFlag);
    }

    /** 卡组/额外卡码列表就地替换（ReplayReader.DeckInfo.main/extra） */
    public static void mapDeck(List<Integer> codes) {
        if (codes == null || codes.isEmpty()) return;
        for (int i = 0; i < codes.size(); i++) {
            Integer id = codes.get(i);
            if (id == null) continue;
            int mapped = mapCode(id);
            if (mapped != id) codes.set(i, mapped);
        }
    }

    /** 就地映射一条消息体（body 为不含类型字节的纯体）内的卡码；无卡码的消息直接返回 */
    public static void mapMessage(int msgType, byte[] body, BlockCountProvider blocks) {
        if (body == null || body.length == 0) return;
        ByteBuffer buf = ByteBuffer.wrap(body).order(ByteOrder.LITTLE_ENDIAN);
        switch (msgType) {
            case 50: // MOVE: code + [ctrl loc seq pos]×2 + reason
            case 53: // POS_CHANGE: code + ctrl loc seq oldpos newpos
            case 54: // SET: code + ctrl loc seq pos
            case 60: // SUMMONING
            case 62: // SPSUMMONING
            case 64: // FLIPSUMMONING
            case 70: // CHAINING: code + pcc pcl pcs subs cc cl cs + desc + ct
                putCode(buf, 0);
                return;
            case 55: // SWAP: 两张卡各 8 字节（code + ctrl loc seq pos）
                putCode(buf, 0);
                putCode(buf, 8);
                return;
            case 90: // DRAW: player cnt + cnt×code
            case 33: // SHUFFLE_HAND: player cnt + cnt×code
            case 39: // SHUFFLE_EXTRA: player cnt + cnt×code
                stepCodes(buf, 2, 4, countAt(buf, 1));
                return;
            case 30: // CONFIRM_DECKTOP: player cnt + cnt×(code ctrl loc)
            case 42: // CONFIRM_EXTRATOP: 同上
                stepCodes(buf, 2, 7, countAt(buf, 1));
                return;
            case 31: // CONFIRM_CARDS: player skip_panel cnt + cnt×(code ctrl loc seq)
                stepCodes(buf, 3, 7, countAt(buf, 2));
                return;
            case 38: // DECK_TOP: player seq code
                putCode(buf, 2);
                return;
            case 6:  // UPDATE_DATA
            case 7:  // UPDATE_CARD
                mapUpdateBlocks(buf, msgType == 6, blocks);
                return;
            default:
                return;
        }
    }

    /** 查询块内仅 QUERY_CODE(0x1) 置位时紧随 flag 的首个 int32 是新卡码，
     *  其余字段（位置/属性/攻守/计数…）不含卡码，故只改这一处、不动块边界 */
    private static void mapUpdateBlocks(ByteBuffer buf, boolean multi, BlockCountProvider blocks) {
        int p;
        int count;
        if (multi) {
            // UPDATE_DATA: player(1) location(1) + 区域条目数个自界长块
            if (buf.limit() < 2 || blocks == null) return;
            count = blocks.blockCount(buf.get(0) & 0xFF, buf.get(1) & 0xFF);
            p = 2;
        } else {
            // UPDATE_CARD: player(1) location(1) sequence(1) + 单块
            if (buf.limit() < 3) return;
            count = 1;
            p = 3;
        }
        for (int i = 0; i < count; i++) {
            if (p + 4 > buf.limit()) return;
            int len = buf.getInt(p);
            if (len < 4 || p + len > buf.limit()) return;
            if (len > 8 && (buf.getInt(p + 4) & 0x1) != 0) putCode(buf, p + 8);
            p += len;   // len 含自身 4 字节（与 parseUpdateData 的 next 计算一致）
        }
    }

    private static void putCode(ByteBuffer buf, int pos) {
        if (pos + 4 > buf.limit()) return;
        buf.putInt(pos, mapCode(buf.getInt(pos)));
    }

    /** 从 from 起按 step 步进读取 cnt 个 int32 卡码并映射 */
    private static void stepCodes(ByteBuffer buf, int from, int step, int cnt) {
        for (int i = 0; i < cnt; i++) {
            int pos = from + i * step;
            if (pos + 4 > buf.limit()) return;
            putCode(buf, pos);
        }
    }

    private static int countAt(ByteBuffer buf, int index) {
        return index < buf.limit() ? (buf.get(index) & 0xFF) : 0;
    }

    private static Map<Integer, Integer> table() {
        Map<Integer, Integer> m = codeMap;
        if (m == null) {
            m = new HashMap<>();
            int[] oldIds = ComparisonTableUtil.oldIDsArray;
            int[] newIds = ComparisonTableUtil.newIDsArray;
            int n = Math.min(oldIds.length, newIds.length);
            for (int i = 0; i < n; i++) {
                if (newIds[i] != 0) m.put(oldIds[i], newIds[i]);
            }
            codeMap = m;
        }
        return m;
    }
}
