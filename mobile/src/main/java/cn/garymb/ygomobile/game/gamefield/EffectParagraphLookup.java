package cn.garymb.ygomobile.game.gamefield;

import android.util.Log;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import cn.garymb.ygomobile.render.card.EffectParagraphs;
import ocgcore.DataManager;
import ocgcore.data.Card;

/**
 * 效果描述字符串 id（{@code code*16+n}）→ 卡面效果段落文档序 的文本反查入口，
 * 供「可发动效果背景呼吸」（{@link EffectActivatableHints}）与「已发动效果账本」
 * （{@link EffectActivationLedger}）共用。
 * <p>问题背景：{@code desc} 的低 4 位只是 cdb {@code texts.str{n+1}} 的索引，脚本
 * {@code SetDescription(aux.Stringid(id, n))} 的 n 并不保证等于卡面 ①②③ 圆圈序
 * （典型如 53334641：index 0 被超量素材提示占用，①② 用的是 1/2）。此前两处直接取
 * {@code desc & 0xf} 当段落序，导致呼吸高亮错段。现改为：用 {@code DataManager.getDesc}
 * 取出该 desc 的提示文本，在卡面 {@code texts.desc} 的段落模型里按文本反查真实段落序。
 * <p>回退策略：文本取不到（系统字符串/空串）、卡表未加载、卡面无描述、反查无唯一命中、
 * 结果超出低 4 位可表示范围（渲染侧以 {@code code*16+offset} 重建后再 {@code &0xf}）时，
 * 一律退回旧的 {@code desc & 0xf} 口径，保证不改变既有可用场景的行为。
 * <p>线程模型：引擎派发线程写（命令解析/连锁登记）、UI 线程读；卡码→段落模型缓存为
 * 并发容器，缓存条目以 {@code Card.Desc} 引用做失效判据——卡表 force 重载/切换语言后
 * 新 Card 实例的描述串引用必变，命中旧引用时自动重建；另提供 {@link #clearCache()} 供
 * 新一局/胜负重同步时主动收口。
 */
final class EffectParagraphLookup {

    private static final String TAG = "EffectParagraphLookup";

    private EffectParagraphLookup() {}

    private static final class Cached {
        /** 建缓存时的 Card.Desc 引用：卡表重载/换语言后引用必变，据此自动失效 */
        final String descSource;
        final EffectParagraphs model;

        Cached(String descSource, EffectParagraphs model) {
            this.descSource = descSource;
            this.model = model;
        }
    }

    private static final ConcurrentHashMap<Integer, Cached> CACHE = new ConcurrentHashMap<>();

    /** 已告警过的 (卡码, desc)：同一笔失败只报一次，避免每个命令/连锁反复刷屏 */
    private static final Set<Long> WARNED = ConcurrentHashMap.newKeySet();

    /**
     * 把 desc 整数解析为卡面段落文档序；无法可靠反查时回退 {@code desc & 0xf}。
     *
     * @param code    效果归属卡码（报文里的卡码，异画镜像由调用方各自处理）
     * @param descRaw 效果描述整数（{@code code*16+n} 或系统字符串 id）
     */
    static int offsetOf(int code, int descRaw) {
        final int fallback = (int) (descRaw & 0xf);
        final long uid = descRaw & 0xFFFFFFFFL;
        if (code <= 0 || uid <= DataManager.MAX_STRING_ID) return fallback;
        DataManager dm = DataManager.get();
        // 启动期/重载期卡表未就绪属正常态：静默回退，不当成反查失败告警
        if (!dm.isDataLoaded()) return fallback;
        String text = dm.getDesc(descRaw, null);
        if (text == null || text.isEmpty()) {
            warnFallback(code, descRaw, text, fallback, -1, "cdb str 为空");
            return fallback;
        }
        Card card = dm.getCardManager().getCard(code);
        if (card == null || card.Desc == null || card.Desc.isEmpty()) {
            warnFallback(code, descRaw, text, fallback, -1, "卡面描述为空");
            return fallback;
        }
        int offset = modelFor(code, card.Desc).indexOfText(text);
        if (offset < 0 || offset > 0xf) {
            warnFallback(code, descRaw, text, fallback, offset,
                    offset < 0 ? "无唯一匹配" : "匹配段落超出 0..15");
            return fallback;
        }
        return offset;
    }

    /** 文本反查失败回退 desc 低 4 位时的告警：按 (卡码, desc) 进程内只报一次。
     *  日志保留完整 desc（含十六进制）与 cdb str 文本，供发现「str 不是卡面段落原文」的
     *  cdb 治理点；修复 cdb 后 {@link #clearCache()} 会连同已告警集合一并清空，便于重新诊断 */
    private static void warnFallback(int code, int descRaw, String text, int fallback, int matched, String reason) {
        long key = ((long) code << 32) | (descRaw & 0xFFFFFFFFL);
        if (!WARNED.add(key)) return;
        Log.w(TAG, "effect paragraph fallback(" + reason + "): code=" + code
                + " desc=" + descRaw + "(0x" + Integer.toHexString(descRaw) + ")"
                + " str=\"" + brief(text) + "\" match=" + matched + " -> offset=" + fallback);
    }

    /** 日志文本截断：段落文本可能很长，logcat 单条有长度上限，保留开头足以识别 cdb 条目 */
    private static String brief(String text) {
        if (text == null) return "null";
        return text.length() <= 200 ? text : text.substring(0, 200) + "...";
    }

    /** 卡码 → 段落模型：Card.Desc 引用未变直接命中；变了（重载/换语言）就地重建 */
    private static EffectParagraphs modelFor(int code, String desc) {
        Cached cached = CACHE.get(code);
        if (cached != null && cached.descSource == desc) return cached.model;
        EffectParagraphs built = EffectParagraphs.build(desc);
        CACHE.put(code, new Cached(desc, built));
        return built;
    }

    /** 新一局/胜负/撤回重同步时清缓存（卡表本身不变，仅防长期驻留与热更卡包后过期）；
     *  已告警集合一并清空：卡包修复后同一笔失败能重新输出诊断日志 */
    static void clearCache() {
        CACHE.clear();
        WARNED.clear();
    }
}
