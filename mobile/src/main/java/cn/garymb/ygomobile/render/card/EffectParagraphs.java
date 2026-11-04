package cn.garymb.ygomobile.render.card;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 卡片描述文本（cdb {@code datas.desc} / {@code texts.desc}）的「效果段落」模型——文档序分段与
 * 「决斗中只能使用1次」条款判定的唯一实现，供卡详已发动标记（{@link CardInfoBinder}）与
 * 持续适用效果提示（{@code ContinuousEffectApplier}）同源复用，避免两处各写一套分段规则。
 * <p>分段规则（用户规格，与整改后的上游脚本编号轴一致）：
 * <ol>
 * <li>段 = 段首效果标记行起、到下一标记行前止，其间的续行/●行/【】框标题行等所有行归该段。
 * 标记行＝换行符后首个 ①②③ 圆圈序号行（U+2460~U+2473）；全文无圆圈序号的传统卡才认
 * 段首 ●/项目符行分段（此时项目符按出现次序续编号）。</li>
 * <li>文档序（{@code paraOf} 的值、亦是脚本 {@code aux.Stringid(code, n)} 的 n）全文连续，
 * 跨框不重启：灵摆框段占 0..p-1、怪兽框段占 p..n-1。</li>
 * <li>分框原则（灵摆怪兽卡）：含「【」或「[」且非圆圈序号开头的行为框标题行，第 1 条起灵摆框、
 * 第 2 条起怪兽框；标题行与分隔线自身不归任何段（{@code paraOf=-1}），段也不跨框延续——
 * 第 2 条标题行、以及其上方紧邻的分隔线都会关闭当前段，否则灵摆框末段会渗染到怪兽框内
 * 「含序号但非序号开头」的文本行而被误标。</li>
 * </ol>
 * <p>不可变值对象：{@link #build(String)} 一次解析，后续查询只读；纯字符串运算，可在后台线程执行。
 */
public final class EffectParagraphs {

    /** 原始描述文本 */
    public final String desc;
    /** 每行的字符区间 {@code [start, end)}（不含换行符） */
    public final List<int[]> lines;
    /** 每行归属的段落文档序；-1 = 不属任何段（首段前的背景文本行、框标题行、分隔线） */
    public final int[] paraOf;
    /** 段落文档序 → 段首圆圈/项目符序位（判定限制句中的「①的效果…」与本段对应关系时用） */
    public final int[] paraAnchor;
    /**
     * 段落文档序 → 所在框标题序（{@link #build} 里的 frameTitle：0=第 1 条框标题之前、
     * 1=第 1 框（灵摆怪兽卡即灵摆框）、2=第 2 框（即怪兽框）…）。灵摆怪兽卡的灵摆框与怪兽框
     * 因此可分（见 {@link #boxText}）；非灵摆/无框标题卡各段恒为同一值。
     */
    public final int[] frameOf;
    /** 已识别的框标题行条数（&lt;=1=单框卡，取句范围即整卡；&gt;=2=灵摆怪兽等多框卡） */
    public final int frameCount;
    /** 段落总数＝最大文档序 + 1；越界的 offset（辅助/适用效果让位槽）自然无命中 */
    public final int paragraphCount;

    private EffectParagraphs(String desc, List<int[]> lines, int[] paraOf,
                             int[] paraAnchor, int[] frameOf, int frameCount, int paragraphCount) {
        this.desc = desc;
        this.lines = lines;
        this.paraOf = paraOf;
        this.paraAnchor = paraAnchor;
        this.frameOf = frameOf;
        this.frameCount = frameCount;
        this.paragraphCount = paragraphCount;
    }

    /** 解析描述文本为段落模型；空文本返回零段模型（各查询一律落空，调用方无需判空） */
    public static EffectParagraphs build(String desc) {
        List<int[]> lines = new ArrayList<>();
        if (desc == null || desc.isEmpty()) {
            return new EffectParagraphs(desc == null ? "" : desc, lines, new int[0], new int[0],
                    new int[0], 0, 0);
        }
        // 拆行：[start, end) 不含换行符
        int lineStart = 0;
        for (int i = 0; i < desc.length(); i++) {
            if (desc.charAt(i) == '\n') {
                lines.add(new int[]{lineStart, i});
                lineStart = i + 1;
            }
        }
        lines.add(new int[]{lineStart, desc.length()});
        int lineCount = lines.size();
        // 先扫一遍判定有无圆圈序号行：有则项目符不再切段（传统 ● 分段仅用于无圆圈序号的卡文）
        boolean hasCircled = false;
        for (int k = 0; k < lineCount && !hasCircled; k++) {
            hasCircled = leadingCircledIndex(lineAt(lines, desc, k).trim()) >= 0;
        }
        int[] paraOf = new int[lineCount];
        List<int[]> meta = new ArrayList<>();
        int docIdx = -1, para = -1, bulletSeq = 0;
        int frameTitle = 0, firstTitleAt = -1, dividerPending = -1;
        for (int i = 0; i < lineCount; i++) {
            String raw = lineAt(lines, desc, i);
            String trimmed = raw.trim();
            if (isDividerLine(trimmed)) {
                paraOf[i] = -1;
                // 紧邻框标题行上方的分隔线同样是段落上界（用户规格3「以分隔线为界再往上一行」）
                if (para >= 0 && dividerPending < 0) dividerPending = i;
                continue;
            }
            int circled = leadingCircledIndex(trimmed);
            // 框标题行判据（用户规格3）：整行含 【 或 [ 即算，圆圈序号开头行除外（那是效果段）；
            // 第 1 条起灵摆框、第 2 条起怪兽框；第 2 条标题行关闭当前段（灵摆序号文本截止在
            // 其上一段），两条标题行都清空其上方待决分隔线
            if (circled < 0 && (trimmed.indexOf('【') >= 0 || trimmed.indexOf('[') >= 0)) {
                paraOf[i] = -1;
                frameTitle++;
                if (frameTitle == 1) firstTitleAt = i;
                if (frameTitle >= 2) para = -1;
                dividerPending = -1;
                continue;
            }
            // 第 2 条标题行上方的分隔线（灵摆框上界）：其下无上界时关闭当前段（同上规格）
            if (dividerPending >= 0) {
                if (frameTitle >= 2 && dividerPending > firstTitleAt) para = -1;
                dividerPending = -1;
            }
            int anchor;
            if (circled >= 0) {
                anchor = circled;
                bulletSeq = circled + 1;   // 其后若混排 ● 续编号，避开已占用的圆圈序号
            } else if (!hasCircled && !trimmed.isEmpty() && isBulletChar(trimmed.charAt(0))) {
                anchor = bulletSeq++;
            } else {
                anchor = -1;
            }
            if (anchor >= 0) {
                docIdx++;
                para = docIdx;
                meta.add(new int[]{docIdx, anchor, frameTitle});
            }
            paraOf[i] = para;
        }
        // 段落→段首序号：per-duel 条款判定时需要知道限制句中序号与本段的对应关系；
        // 段落→所在框：灵摆怪兽卡据此把灵摆框与怪兽框分开（兜底取句不得跨框，见 {@link #boxText}）
        int[] paraAnchor = new int[docIdx + 1];
        int[] frameOf = new int[docIdx + 1];
        for (int[] m : meta) {
            paraAnchor[m[0]] = m[1];
            frameOf[m[0]] = m[2];
        }
        return new EffectParagraphs(desc, lines, paraOf, paraAnchor, frameOf, frameTitle, docIdx + 1);
    }

    /** 第 index 行的原文（不含换行符） */
    public String lineAt(int index) {
        return lineAt(lines, desc, index);
    }

    private static String lineAt(List<int[]> lines, String desc, int index) {
        int[] ln = lines.get(index);
        return desc.substring(ln[0], ln[1]);
    }

    /** 该文档序是否存在（越界的 offset 一律 false，调用方可据此跳过） */
    public boolean hasParagraph(int offset) {
        return offset >= 0 && offset < paragraphCount;
    }

    /** 该段首行下标；段不存在时 -1 */
    public int firstLineOf(int offset) {
        if (!hasParagraph(offset)) return -1;
        for (int i = 0; i < paraOf.length; i++) {
            if (paraOf[i] == offset) return i;
        }
        return -1;
    }

    /** 该段全部行（含续行与归入本段的框标题行）拼接的段落全文，行间以换行符连接；段不存在时空串 */
    public String paragraphText(int offset) {
        if (!hasParagraph(offset)) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < paraOf.length; i++) {
            if (paraOf[i] != offset) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append(lineAt(i));
        }
        return sb.toString();
    }

    /**
     * 该段所在「段落块」内是否存在归属本段的「决斗中只能使用1次」类条款：段落块 = 该段全部行
     * + 段首上方紧邻的连续背景行（发动限制语句与誓约句惯写在段首之前，如「这个卡名的②的效果在决斗中
     * 只能使用1次。」/「这张卡发动的回合自己不能特殊召唤。」）。命中即该段的发动标记跨回合持续
     * （{@link CardInfoBinder} 的已发动置灰、{@code ContinuousEffectApplier} 的整局适用中判定共用此门控）。
     */
    public boolean isPerDuel(int offset) {
        int[] range = blockRange(offset);
        if (range == null) return false;
        char anchorCh = (char) (0x2460 + paraAnchor[offset]);
        for (int k = range[0]; k <= range[1]; k++) {
            if (lineMarksPerDuel(lineAt(k), anchorCh)) return true;
        }
        return false;
    }

    /**
     * 该段的「段落块」行区间 {@code [lo, hi]}：本段全部行的上下界，再往上吞掉紧邻的连续背景行
     * （{@code paraOf<0} 的发动限制句/誓约句/框标题行/分隔线）；段不存在时返回 {@code null}。
     * <p>OCG Rule《誓约》明确『发动的回合，○○』这类誓约「有的放在了效果序号之前，而有的放在了
     * 序号之后，没有区别」，故抽句与条款判定都必须把段首上方的紧邻行一并纳入。
     */
    private int[] blockRange(int offset) {
        if (!hasParagraph(offset)) return null;
        int lo = Integer.MAX_VALUE, hi = -1;
        for (int k = 0; k < paraOf.length; k++) {
            if (paraOf[k] != offset) continue;
            if (k < lo) lo = k;
            if (k > hi) hi = k;
        }
        if (hi < 0) return null;
        while (lo - 1 >= 0 && paraOf[lo - 1] < 0) lo--;
        return new int[]{lo, hi};
    }

    /**
     * 该段所在段落块的全文（含段首上方紧邻的连续背景行，即写在序号之前的誓约句），行间以换行符
     * 连接；框标题行与分隔线自身不归任何段，此处按同一口径剔除（它们只是版式，不是效果文本）。
     * <p>与 {@link #isPerDuel} 的块口径严格一致；段不存在时返回空串。
     */
    public String blockText(int offset) {
        int[] range = blockRange(offset);
        if (range == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int k = range[0]; k <= range[1]; k++) {
            String raw = lineAt(k);
            String trimmed = raw.trim();
            if (trimmed.isEmpty() || isDividerLine(trimmed) || isFrameTitleLine(trimmed)) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append(raw);
        }
        return sb.toString();
    }

    /**
     * 该段所在「框」的全部段落块文本拼接（同 {@link #blockText} 的段级口径，只是把范围从单段
     * 扩到本框的所有段），供「发动段落抽不出适用句、须在本框内按唯一适用句兜底」时用。
     * <p>灵摆怪兽卡的灵摆框与怪兽框是两个互不相干的效果框：怪兽效果发动后绝不能再把灵摆框里
     * 的「这个回合」句当作本次发动的适用句（反之亦然），故兜底取句范围必须按框收紧，不能扫整卡。
     * <p>单框卡（{@link #frameCount}&lt;=1，无第 2 条框标题）没有跨框误染风险，直接返回整卡描述，
     * 保持既有「同类描述不许漏」兜底口径；段不存在时空串。
     */
    public String boxText(int offset) {
        if (!hasParagraph(offset)) return "";
        if (frameCount <= 1) return desc == null ? "" : desc;
        int frame = frameOf[offset];
        StringBuilder sb = new StringBuilder();
        for (int p = 0; p < paragraphCount; p++) {
            if (frameOf[p] != frame) continue;
            String t = blockText(p);
            if (t.isEmpty()) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append(t);
        }
        return sb.toString();
    }

    /** 框标题行（【灵摆效果】/【怪兽效果】一类，与 {@link #build} 的判定口径一致） */
    private static boolean isFrameTitleLine(String trimmed) {
        return leadingCircledIndex(trimmed) < 0
                && (trimmed.indexOf('【') >= 0 || trimmed.indexOf('[') >= 0);
    }

    /** 「决斗中只能使用1次」类限制条款关键词（实测 7 套语言 cdb）：中文「决斗中只能…」、
     *  繁中「決鬥中/決闘中」、日「デュエル中に１度しか…」、韩「듀얼( 중에) 1번밖에/1장밖에」、
     *  西「por Duel(o)」、英/葡「per Duel」；拉丁部分小写比较 */
    private static final String[] PER_DUEL_MARKS = {
            "决斗中", "決鬥中", "決闘中", "デュエル中", "듀얼", "per duel", "por duelo"
    };
    /** 与决斗词同现的「一度/しか」类限定词，排除仅卡名含决斗字样的行 */
    private static final String[] PER_DUEL_ONCE_WORDS = {"只能", "しか", "밖에", "만", "once", "vez"};

    /** 单行是否携归属本段的 per-duel 条款：含决斗词+一度限定词的行按读句分隔符拆小句，
     *  含决斗词的小句无圆圈序号（全局限制句，如「这个卡名的效果在决斗中只能使用1次」）
     *  即命中；有小句带序号时须含本段序号（如「①的效果1回合只能使用1次，②的效果在决斗
     *  中只能使用1次」只对②持续） */
    private static boolean lineMarksPerDuel(String line, char anchorCh) {
        String low = line.toLowerCase(Locale.ROOT);
        boolean anyMark = false;
        for (String m : PER_DUEL_MARKS) {
            if (low.contains(m)) { anyMark = true; break; }
        }
        if (!anyMark) return false;
        boolean anyOnce = false;
        for (String w : PER_DUEL_ONCE_WORDS) {
            if (low.contains(w)) { anyOnce = true; break; }
        }
        if (!anyOnce) return false;
        for (String c : line.split("[，,、。．；;]")) {
            String cl = c.toLowerCase(Locale.ROOT);
            boolean hasMark = false;
            for (String m : PER_DUEL_MARKS) {
                if (cl.contains(m)) { hasMark = true; break; }
            }
            if (!hasMark) continue;
            boolean hasCircled = false;
            for (int k = 0; k < c.length(); k++) {
                char ch = c.charAt(k);
                if (ch >= 0x2460 && ch <= 0x2473) { hasCircled = true; break; }
            }
            if (!hasCircled) return true;
            if (anchorCh != 0 && c.indexOf(anchorCh) >= 0) return true;
        }
        return false;
    }

    /**
     * 分隔线（同框标题行一样不归入任何段落）：整行由横线/短横类字符组成，
     * 如英/葡「----------------------------------------」、西「━━━━━━━━…」；
     * 第2条框标题行上方的分隔线即灵摆框上界（用户规格3「再往上一行」，并关闭灵摆框末段
     * 防其渗染到怪兽框文本）
     */
    public static boolean isDividerLine(String trimmed) {
        if (trimmed.isEmpty()) return false;
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c == '-' || c == '—' || c == '–' || c == '─' || c == '━' || c == '═'
                    || c == '=' || c == '_' || c == '~' || c == '〜' || c == '·' || c == '*') {
                continue;
            }
            return false;
        }
        return true;
    }

    /** 项目符（全文无圆圈序号的传统卡用它分段） */
    public static boolean isBulletChar(char c) {
        return c == '●' || c == '○' || c == '◦' || c == '・' || c == '-' || c == '–' || c == '—'
                || c == '*' || c == '▪' || c == '‣' || c == '•' || c == '◆' || c == '◇' || c == '■';
    }

    /**
     * 行首圆圈序号：①~⑳（U+2460~U+2473）映射为效果序位 0~19，非圆圈开头返回 -1。
     * 现代卡描述以段首圆圈序号标注各效果，该序位即脚本 aux.Stringid(code, n) 的 n，
     * 亦是 CHINT_DESC_ADD hint 值（code*16+n）的低 4 位，据此定位已发动效果段落。
     */
    public static int leadingCircledIndex(String trimmedLine) {
        if (trimmedLine.isEmpty()) return -1;
        char c = trimmedLine.charAt(0);
        return (c >= '\u2460' && c <= '\u2473') ? c - '\u2460' : -1;
    }
}
