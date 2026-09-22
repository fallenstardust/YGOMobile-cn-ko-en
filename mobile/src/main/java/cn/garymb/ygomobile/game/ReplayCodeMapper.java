package cn.garymb.ygomobile.game;

import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import cn.garymb.ygomobile.utils.ComparisonTableUtil;
import ocgcore.DataManager;
import ocgcore.data.Card;

/**
 * 回放卡码的运行时归一（不改写 .yrp 文件）。
 *
 * <p>录像里的卡码可能来自两套号：早期录像以 9 位先行号（1002xxxxx / 1012xxxxx）记录，
 * 而本机卡表（cards.cdb + expansions 扩展库，即 {@code CardDetailPanel.showCard} 判定
 * unknown 的同一张表）只认其中一种号，卡图与名称取不到就显示 unknown。
 *
 * <p>因此映射不能无条件按对照表替换：本机卡表已认得的码必须原样保留（否则会把能显示的
 * 码换成卡表里没有的码，反而 more unknown）。规则统一为
 * 「原码查不到 → 换成对照表目标码，且仅当目标码查得到时才换」，双向（先行→正式、
 * 正式→先行）都试一次，取第一个查得到的结果。
 *
 * <p>各消息的卡码偏移与 {@link ReplayMessageSlicer} 的切片长度、{@code GameMessageParser}
 * 的读取次序逐条对齐（写侧口径见 ocgcore card.cpp / operations.cpp / processor.cpp /
 * libduel.cpp：MSG_MOVE/POS_CHANGE/SET/SUMMONING/CHAINING 首字段即 code，
 * MSG_DRAW 为 player cnt + cnt×code 等）。SELECT 系消息含卡码但不投喂实况管线，不映射。
 *
 * <p>每次播放开始调用 {@link #beginSession()}，结束后 {@link #buildReport()} 给出
 * 映射/未解析码统计，随诊断日志落盘（ygocore/log）。
 */
public final class ReplayCodeMapper {

    private static final String TAG = "ReplayCodeMapper";

    /** query 块数量提供者：UPDATE_DATA 的块数随该区域当前条目数变化，
     *  由调用方按实况解析同一口径（{@code CommandDataParser.parseUpdateData}）给出 */
    public interface BlockCountProvider {
        int blockCount(int player, int location);
    }

    /** 卡表可解析判定：与 CardDetailPanel 显示 unknown 的判据完全一致 */
    public interface CardResolver {
        boolean known(int code);
    }

    /** 默认解析器：ocgcore.DataManager 卡表（含 expansions 扩展库）；未加载完视为全未知 */
    private static volatile CardResolver resolver = ReplayCodeMapper::cardTableHas;

    /** 先行→正式 / 正式→先行 双向索引，首次使用时按对照表建一次 */
    private static volatile Map<Integer, Integer> toNew;
    private static volatile Map<Integer, Integer> toOld;

    // ==== 本次播放的诊断统计（单线程回放投喂线程使用，仅计数无需加锁） ====
    private static int mappedCount;
    private static int keptCount;
    private static final LinkedHashSet<Integer> unresolved = new LinkedHashSet<>();
    private static final Map<Integer, Integer> mappedSamples = new LinkedHashMap<>();

    private ReplayCodeMapper() {
    }

    /** 注入自定义卡表判定（单测/特殊数据源用），传 null 恢复默认卡表判定 */
    public static void setResolver(CardResolver r) {
        resolver = r == null ? ReplayCodeMapper::cardTableHas : r;
    }

    /** 一次回放开始时清零统计 */
    public static void beginSession() {
        mappedCount = 0;
        keptCount = 0;
        unresolved.clear();
        mappedSamples.clear();
    }

    /**
     * 单码归一：卡表认得的原码一律保留；否则查对照表，目标码认得才换。
     * 码值最高位是回放侧携带的翻面标志（{@code MSG_DRAW} 等），映射时原样保留。
     */
    public static int mapCode(int code) {
        if (code == 0) return 0;
        int faceUpFlag = code & 0x80000000;
        int base = code & 0x7fffffff;
        CardResolver res = resolver;
        if (res != null && res.known(base)) {
            keptCount++;
            return code;
        }
        Integer alt = alternate(base);
        if (alt != null && (res == null || res.known(alt))) {
            if (mappedSamples.size() < 40) {
                mappedSamples.put(base, alt);
            }
            mappedCount++;
            return alt | faceUpFlag;
        }
        if (unresolved.size() < 200) {
            unresolved.add(base);
        }
        return code;   // 两个号都查不到：保持原码，交给诊断日志定位
    }

    /** 卡组/额外卡码列表就地归一（ReplayReader.DeckInfo.main/extra） */
    public static void mapDeck(List<Integer> codes) {
        if (codes == null || codes.isEmpty()) return;
        for (int i = 0; i < codes.size(); i++) {
            Integer id = codes.get(i);
            if (id == null) continue;
            int mapped = mapCode(id);
            if (mapped != id) codes.set(i, mapped);
        }
    }

    /** 就地归一一条消息体（body 为不含类型字节的纯体）内的卡码；无卡码的消息直接返回 */
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
            case 30: // CONFIRM_DECKTOP: player cnt + cnt×(code ctrl loc seq)
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

    /** 从 from 起按 step 步进读取 cnt 个 int32 卡码并归一 */
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

    /** 对照表双向候选：先行号给正式号、正式号给先行号；无对照关系返回 null */
    private static Integer alternate(int base) {
        Map<Integer, Integer> n2o = toOldMap();
        Map<Integer, Integer> o2n = toNewMap();
        Integer cand = o2n == null ? null : o2n.get(base);
        if (cand != null) return cand;
        return n2o == null ? null : n2o.get(base);
    }

    /** 卡表是否认得该码（与 CardDetailPanel 的 unknown 判据一致） */
    private static boolean cardTableHas(int code) {
        try {
            Card card = DataManager.get().getCardManager().getCard(code);
            return card != null;
        } catch (Throwable t) {
            // 卡表尚未初始化：视为不可判定，交由 mapCode 的“目标码也要认得”规则兜底
            Log.d(TAG, "card table unavailable: " + t);
            return false;
        }
    }

    private static Map<Integer, Integer> toNewMap() {
        Map<Integer, Integer> m = toNew;
        if (m == null) {
            m = buildTable(true);
            toNew = m;
        }
        return m;
    }

    private static Map<Integer, Integer> toOldMap() {
        Map<Integer, Integer> m = toOld;
        if (m == null) {
            m = buildTable(false);
            toOld = m;
        }
        return m;
    }

    private static Map<Integer, Integer> buildTable(boolean oldToNew) {
        Map<Integer, Integer> m = new HashMap<>();
        int[] oldIds = ComparisonTableUtil.oldIDsArray;
        int[] newIds = ComparisonTableUtil.newIDsArray;
        int n = Math.min(oldIds.length, newIds.length);
        for (int i = 0; i < n; i++) {
            if (oldIds[i] == 0 || newIds[i] == 0) continue;
            m.put(oldToNew ? oldIds[i] : newIds[i], oldToNew ? newIds[i] : oldIds[i]);
        }
        return m;
    }

    /** 本次播放是否发生过映射或仍有查不到的码（决定是否落诊断日志） */
    public static boolean hasFindings() {
        return mappedCount > 0 || !unresolved.isEmpty();
    }

    /** 本次播放的卡码诊断文本（落 ygocore/log 便于定位仍显示 unknown 的卡） */
    public static String buildReport() {
        StringBuilder sb = new StringBuilder();
        int dbCount = -1;
        try {
            dbCount = DataManager.get().getCardManager().getCount();
        } catch (Throwable ignored) {
        }
        sb.append("卡表条目=").append(dbCount)
                .append("，保留可解析码=").append(keptCount)
                .append("，映射替换=").append(mappedCount)
                .append("，仍无法解析=").append(unresolved.size()).append('\n');
        List<Integer> samples = new ArrayList<>(mappedSamples.keySet());
        sb.append("映射样例: ");
        appendCodes(sb, samples, 12);
        sb.append("\n无法解析码: ");
        appendCodes(sb, new ArrayList<>(unresolved), 20);
        return sb.toString();
    }

    private static void appendCodes(StringBuilder sb, List<Integer> codes, int max) {
        if (codes.isEmpty()) {
            sb.append("无");
        }
        for (int i = 0; i < Math.min(max, codes.size()); i++) {
            if (i > 0) sb.append(',');
            sb.append(codes.get(i));
        }
        if (codes.size() > max) sb.append("…");
        sb.append('\n');
    }
}
