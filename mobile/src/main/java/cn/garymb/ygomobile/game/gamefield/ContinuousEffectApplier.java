package cn.garymb.ygomobile.game.gamefield;

import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import cn.garymb.ygomobile.render.card.EffectParagraphs;
import ocgcore.DataManager;
import ocgcore.data.Card;

/**
 * 「持续适用中效果」解算器：从决斗级发动账本 {@link EffectActivationLedger} 里挑出「本回合
 * 或本局决斗一直适用中」的效果，供顶部 HUD 头像角上的金色感叹号徽标与下拉列表展示。
 * <p>判据（用户规格，两层串联，缺一不可）：
 * <ol>
 * <li><b>存续门控</b>——与卡详「已发动」勾完全同源：当回合发动的记录恒存续；更早回合的记录
 * 须命中该段落块的「决斗中只能使用1次」类条款（{@link EffectParagraphs#isPerDuel}）才跨回合
 * 存续。回合轮转/胜负/开局/撤回由账本自身过期，本类不另立生命周期。</li>
 * <li><b>时长语过滤</b>——该段落在 cdb {@code texts.desc} 中的正文（或 str）里存在
 * 「持续适用句」：含整回合/整局时长语（{@link #TURN_DURATION_MARKS} / {@link #DUEL_DURATION_MARKS}，
 * 已逐词在 10 套语言实库上验过命中率），且不是该效果自身的<b>发动时机句</b>
 * （{@link #isApplyingSentence}，用户新规格1）。一次性解卡（破坏/抽卡/特殊召唤等）不命中，不进列表。</li>
 * </ol>
 * <p>「这张卡/这个效果发动的回合」与「这个效果的发动后」同族（用户新规格3：两者等价，均属适用中），
 * 但当它们作定语修饰阶段/时点并与「才能发动」类提示语同现时（「这张卡发动的回合的自己主要阶段
 * 才能发动」）则只是发动时机，剔除；时长语后紧跟逗号的（「…才能发动（这个效果发动的回合，这张卡
 * 不能攻击）」）仍作适用中保留。
 * <p>整局轴上另排除「决斗中只能使用1次」这类<b>使用限制</b>小句：它只限制发动次数、本身不产生
 * 持续适用的效果，命中该小句不算数（见 {@link #marksDuelDuration}）。
 * <p>列表文字（用户新规格）：不列整段序号文本，只取该段落里携时长语的子句
 * （如「这个回合，对方不能把魔法・陷阱・怪兽的效果发动。」），取自 cdb 的
 * {@code texts.desc} 段落正文（或 str）；无子句可抽时才降级用 str 短标签/段落全文/卡名。
 * 玩家要看完整效果与已打勾的序号段落，点列表上的卡即可在卡详面板展开。
 * <p>纯函数、无视图依赖：可在主线程调用（账本条目数为个位数～几十，且段落解析结果按卡码缓存）。
 */
public final class ContinuousEffectApplier {

    /** 一条持续适用中效果：发动卡码（已按 alias 归一，异画与本卡合并）+ 段落文档序 + 半场 +
     *  发动回合 + 展示文本（携时长语的子句，缺时降级 str/段落全文/卡名）+ 是否整局级 */
    public static final class Applying {
        public final int code;
        public final int offset;
        public final int side;
        public final int turn;
        public final String text;
        /** true=整局决斗适用中，false=本回合适用中 */
        public final boolean wholeDuel;

        Applying(int code, int offset, int side, int turn, String text, boolean wholeDuel) {
            this.code = code;
            this.offset = offset;
            this.side = side;
            this.turn = turn;
            this.text = text;
            this.wholeDuel = wholeDuel;
        }
    }

    private ContinuousEffectApplier() {}

    /**
     * 整回合时长语（逐词在 setDesception/ygopro-database 实库 10 套语言 cards.cdb 上验过命中率，
     * 小写比较）：分三类——
     * <ul>
     * <li>纯时长状语：「这个回合」「ターン終了時まで」「for the rest of this turn」；</li>
     * <li>「（这张卡/这个效果）发动的回合」族（用户新规格1/3）：本回合内因这次发动而持续成立，
     * 与「这个效果的发动后」等价；它们与 {@link #ACTIVATION_CUES} 同现且不是逗号引导的独立状语时，
     * 则是在交代该效果的<b>发动时机</b>（如「这张卡发动的回合的自己主要阶段才能发动」），不算适用中；</li>
     * <li>「这个效果的发动后」族（含多语言对应写法）。</li>
     * </ul>
     * 不取「1回合1次」这类只限次数的措辞（它们不命中本表）。
     */
    private static final String[] TURN_DURATION_MARKS = {
            // 简中（官方 zh-CN 译法）
            "这个回合", "回合结束时", "直到回合结束", "这回合",
            "发动的回合", "发动过的回合", "这个效果的发动后",
            // 简中（另一套 zh-SC 译法：「此卡/此效果…发动的回合」「发动此卡的回合」）
            "此回合", "发动此卡的回合", "发动了此卡的回合", "发动此效果的回合",
            "此卡发动的回合", "此效果发动的回合", "此效果发动过的回合", "此效果发动后",
            "直至回合结束",
            // 繁中
            "這個回合", "這張卡發動的回合", "這個效果發動的回合", "這個效果發動過的回合",
            "這個效果的發動後", "直至回合結束", "回合結束時", "發動的回合", "發動過的回合",
            // 日（用「を発動…ターン」主干涵盖卡/效果/卡的效果各主语变体）
            "このターン", "ターン終了時まで", "このターンのエンドフェイズ", "ターン中",
            "を発動したターン", "を発動するターン", "この効果の発動後",
            // 韩（同样用主干涵盖变体）
            "이 턴", "턴 종료시", "턴 종료시까지", "를 발동한 턴", "를 발동하는 턴",
            "이 효과의 발동 후",
            // 英
            "this turn", "rest of this turn", "end of this turn",
            "the turn you activate", "the turn you activated",
            "the turn this card was activated", "the turn this effect was activated",
            "after you activate this effect", "after activating this effect",
            "if you activate this effect",
            // 西
            "este turno", "resto de este turno", "final de este turno",
            "el turno en el que activas", "el turno en el que activaste",
            "durante el turno en el que", "después de activar este efecto",
            // 葡
            "este turno", "restante deste turno", "final deste turno",
            "no turno em que ativar", "o turno em que ativar",
            "turno em que você ativar", "após ativar este efeito",
            // 法
            "ce tour", "reste de ce tour", "fin de ce tour",
            "le tour où vous activez", "le tour où vous avez activé",
            "après l'activation de cet effet",
            // 意
            "questo turno", "resto di questo turno", "fine del turno",
            "turno in cui attivi", "turno in cui hai attivato",
            "dopo l'attivazione di questo effetto"
    };
    /**
     * 整局时长语：「这场决斗中…」类持续条款（与 {@link #DUEL_LIMIT_WORDS} 的使用限制句区分）。
     * 同样逐词在 10 套语言实库上验过命中率（de-DE 实库无德文卡文，不入表）。
     */
    private static final String[] DUEL_DURATION_MARKS = {
            "这场决斗中", "這個決鬥中", "这场决斗", "決闘中", "决斗中", "デュエル中", "듀얼",
            "this duel", "duel this", "per duel", "por duelo", "du duel",
            "during the duel", "for the rest of the duel", "rest of the duel",
            "resto de este duelo", "reste de ce duel", "resto di questo duello"
    };
    /** 「只能使用1次」类限定词：与整局词同现的小句是使用限制而非持续适用，不计入列表 */
    private static final String[] DUEL_LIMIT_WORDS = {
            "只能", "しか", "밖에", "만", "once", "vez", "fois", "mal", "einmal"
    };
    /**
     * 效果「才能发动」类提示语（各语言）：与时长语同现时，该句很可能是在交代效果的发动时机
     * 而不是在陈述持续成立的状态（用户新规格1）。仅当该句里的时长语<b>不是</b>逗号引导的独立
     * 状语时才据此剔除，保住「…才能发动（这个效果发动的回合，这张卡不能攻击）」这类括号内的
     * 真实持续限制句。拉丁语系靠尾随空格界定（"you can " 不匹 "you cannot"）。
     */
    private static final String[] ACTIVATION_CUES = {
            "才能发动", "可以发动", "才可发动", "才能發動", "可以發動",
            "発動できる", "発動する事ができる",
            "발동할 수 있다",
            "you can ", "you may ",
            "puedes ", "pueda ",
            "você pode ", "voce pode ",
            "vous pouvez ",
            "puoi ", "è possibile "
    };
    /**
     * 否定式（不能/不可/できない/할 수 없/cannot/no puede/non puoi/ne pouvez pas）：句中出现即说明
     * 该句在陈述限制，{@link #ACTIVATION_CUES} 一律不算命中——拉丁语系的「No puedes Invocar…
     * en el turno en el que activas esta carta」「Non puoi Evocare…nel turno in cui attivi…」
     * 里的 puedes/puoi 是「不能」的一部分，当作发动提示语会误删真实持续限制句（实库回归：
     * 西语误删 696 张、意语 268 张）。
     */
    private static final String[] NEGATION_TOKENS = {
            "不能", "不可", "无法", "できない", "出来ません", "できず",
            "할 수 없", "하지 못", "되지 않",
            "cannot", "can not", "no puede", "não pode", "nao pode",
            "non pu", "ne pouvez pas", "niet "
    };
    /** 逗号族：时长语后紧跟逗号＝独立状语从句，在陈述本回合持续成立的状态（不是发动时机） */
    private static final String COMMA_LIKE = "，,、";
    /** 扫描时长语后继字符时跳过的空白与日/韩格助词（不影响「の/의」定语判定的结果） */
    private static final String SKIP_AFTER_MARK = " \tのはがをにでと을를에서은는가";
    /** 读句分隔符（与 EffectParagraphs 的 per-duel 小句拆分口径一致） */
    private static final String CLAUSE_SPLIT = "[，,、。．；;]";

    /** 段落模型按卡码缓存：cdb 描述在运行期不变，避免每次刷新重复拆行分段 */
    private static final HashMap<Integer, EffectParagraphs> MODEL_CACHE = new HashMap<>();

    /**
     * 指定半场（本地索引 0=我方 1=对方，随视角互换由账本 {@code swapSides} 统一翻转）
     * 当前持续适用中的效果列表：整局级在前、其内按发动回合升序再按卡码稳定排列，
     * 异画与本卡归一后去重（同一笔发动只列一行）。
     */
    public static List<Applying> forSide(int side) {
        int nowTurn = EffectActivationLedger.getCurrentTurn();
        List<EffectActivationLedger.Entry> entries = EffectActivationLedger.snapshot();
        List<Applying> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (EffectActivationLedger.Entry e : entries) {
            EffectActivationLedger.Activation a = e.activation;
            if (a == null || a.side != side || e.code <= 0) continue;
            Card cd = DataManager.get().getCardManager().getCard(e.code);
            // alias 归一：异画卡与本卡共用效果段落与账本镜像条目，同一笔发动只列一行；
            // 归一码同时决定展示卡图与 str 取用
            int canonical = cd != null && cd.Alias > 0 && cd.Alias != e.code ? cd.Alias : e.code;
            if (!seen.add(canonical + ":" + a.offset + ":" + a.turn)) continue;
            // 门控①存续：与卡详已发动勾同款判定（当回合恒存续，更早回合须命中 per-duel 条款）。
            // 例外：全文无①②序号也无 ● 项目符的传统单效果卡（paragraphCount==0，如多数
            // 永续/场地魔法）没有段落可命中，但它的 desc 就是该效果本身，同样纳入判定
            EffectParagraphs model = modelFor(canonical);
            boolean legacySingle = model.paragraphCount == 0;
            boolean hit = model.hasParagraph(a.offset);
            if (!hit && !legacySingle) continue;
            if (a.turn != nowTurn && !(hit && model.isPerDuel(a.offset))) continue;
            String paragraph = hit ? model.paragraphText(a.offset) : (legacySingle ? model.desc : "");
            // cdb str：上游整改后即该段效果文字（短标签或全文）
            String str = DataManager.get().getDesc((int) ((long) canonical * 16 + a.offset), "");
            // 门控②时长＋时机（用户新规格1/2/3）：只收「持续适用句」——段落正文或 str 里存不在
            // 发动时机句形态的时长语子句，就不算「一直适用中」（抽不出子句直接丢弃，
            // 不再降级到整段全文，避免把一次性解卡列进来）
            String clause = durationClause(paragraph);
            if (clause.isEmpty()) clause = durationClause(str);
            if (clause.isEmpty()) continue;
            // 子句里含整局词即归整局级（寿命跨回合），否则为本回合级
            boolean duel = marksDuelDuration(clause);
            String text = clause.trim();
            if (TextUtils.isEmpty(text)) continue;
            out.add(new Applying(canonical, a.offset, side, a.turn, text, duel));
        }
        Collections.sort(out, BY_TURN_THEN_CODE);
        return out;
    }

    /** 排序：整局级在前（其寿命覆盖全局，玩家更需先看），其内按发动回合、再按卡码稳定排列 */
    private static final Comparator<Applying> BY_TURN_THEN_CODE = new Comparator<Applying>() {
        @Override
        public int compare(Applying x, Applying y) {
            if (x.wholeDuel != y.wholeDuel) return x.wholeDuel ? -1 : 1;
            if (x.turn != y.turn) return x.turn - y.turn;
            if (x.code != y.code) return x.code - y.code;
            return x.offset - y.offset;
        }
    };

    /**
     * 单句是否为「持续适用句」（用户新规格1的核心判定）：含时长语，且不是在交代该效果自身的发动时机。
     * <p>发动时机句的特征：既带「才能发动」类提示语，句里的时长语又都是定语用法（后面不紧跟逗号），
     * 如「①：这张卡发动的回合的自己主要阶段才能发动。」/「①：このカードを発動したターンの自分
     * メインフェイズに発動できる。」/「①： 이 카드를 발동한 턴의 자신 메인 페이즈에 발동할 수 있다。」
     * /「During your Main Phase, if this card was activated this turn: You can …」。
     * <p>而时长语后紧跟逗号的，是独立状语从句、在陈述本回合持续成立的状态，即使同句带发动提示语也保留，
     * 如「②：把墓地的这张卡除外才能发动（这个效果发动的回合，自己不是机械族怪兽不能特殊召唤）。」
     */
    private static boolean isApplyingSentence(String sentence) {
        if (TextUtils.isEmpty(sentence)) return false;
        boolean hasMark = false;
        boolean commaLedMark = false;
        for (String m : TURN_DURATION_MARKS) {
            int from = 0;
            int i;
            while ((i = sentence.indexOf(m, from)) >= 0) {
                hasMark = true;
                if (followedByComma(sentence, i + m.length())) commaLedMark = true;
                from = i + m.length();
            }
        }
        for (String m : DUEL_DURATION_MARKS) {
            int from = 0;
            int i;
            while ((i = sentence.indexOf(m, from)) >= 0) {
                hasMark = true;
                if (followedByComma(sentence, i + m.length())) commaLedMark = true;
                from = i + m.length();
            }
        }
        if (!hasMark) return false;
        if (commaLedMark) return true;
        return !hasActivationCue(sentence);
    }

    /** 该句是否带「才能发动」类发动提示语：含否定式时一律不算（否定句是在陈述限制而非发动可能） */
    private static boolean hasActivationCue(String sentence) {
        if (containsAny(sentence, NEGATION_TOKENS)) return false;
        return containsAny(sentence, ACTIVATION_CUES);
    }

    /** 时长语出现处之后（跳过空白与格助词）是否为逗号族字符 */
    private static boolean followedByComma(String s, int after) {
        int k = after;
        while (k < s.length() && SKIP_AFTER_MARK.indexOf(s.charAt(k)) >= 0) k++;
        return k < s.length() && COMMA_LIKE.indexOf(s.charAt(k)) >= 0;
    }

    /**
     * 从整段效果文本里只抽出「正在适用中」的那句（用户新规格：列表不展示完整序号文本，
     * 只要「这个回合，xxx」/「这个效果发动过的回合，xxx」/「这个效果的发动后，xxx」这类子句，
     * 并排除「这张卡发动的回合的…阶段才能发动」这类发动时机句）：
     * 按句终止符（。．！？!?；; 与换行）切句，保留 {@link #isApplyingSentence} 命中的句子并以空格拼接；
     * 逗号/顿号不断句（「这个回合，」与后半句必须同现）。无命中时返回空串。
     */
    public static String durationClause(String text) {
        if (TextUtils.isEmpty(text)) return "";
        StringBuilder sb = new StringBuilder();
        for (String sentence : splitSentences(text)) {
            if (!isApplyingSentence(sentence)) continue;
            String trimmed = sentence.trim();
            if (trimmed.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(trimmed);
        }
        return sb.toString();
    }

    /** 句终止符（含全/半角与换行；逗号、顿号不在此列，时长语前导逗号属于同一小句） */
    private static final String SENTENCE_ENDS = "。．！!？?；;.\n";

    /** 按句终止符切句，终止符本身归入前句（连续终止符如「。\n」并成一句） */
    private static List<String> splitSentences(String text) {
        List<String> out = new ArrayList<>();
        int start = 0;
        for (int i = start; i < text.length(); i++) {
            if (SENTENCE_ENDS.indexOf(text.charAt(i)) < 0) continue;
            int end = i + 1;
            while (end < text.length() && SENTENCE_ENDS.indexOf(text.charAt(end)) >= 0) end++;
            out.add(text.substring(start, end));
            start = end;
            i = end - 1;
        }
        if (start < text.length()) out.add(text.substring(start));
        return out;
    }

    /**
     * 文本是否携整局时长语：按读句分隔符拆小句，含整局词的小句须<b>不</b>带「只能/しか/once」
     * 类使用限制词才算数——「这个卡名的效果在决斗中只能使用1次」只限次数、不产生持续适用的效果。
     * 无分隔符可拆的短 str 标签（整句即一个小句）自然落到同一判定。
     */
    public static boolean marksDuelDuration(String text) {
        if (TextUtils.isEmpty(text)) return false;
        for (String clause : text.split(CLAUSE_SPLIT)) {
            String low = clause.toLowerCase(Locale.ROOT);
            boolean hasDuel = false;
            for (String m : DUEL_DURATION_MARKS) {
                if (low.contains(m)) { hasDuel = true; break; }
            }
            if (!hasDuel) continue;
            boolean limited = false;
            for (String w : DUEL_LIMIT_WORDS) {
                if (low.contains(w)) { limited = true; break; }
            }
            if (!limited) return true;
        }
        return false;
    }

    private static boolean containsAny(String text, String[] marks) {
        if (TextUtils.isEmpty(text)) return false;
        String low = text.toLowerCase(Locale.ROOT);
        for (String m : marks) {
            if (low.contains(m)) return true;
        }
        return false;
    }

    /** 卡码 → 段落模型（缓存命中直接返回；解析纯字符串运算，无视图依赖）。
     *  卡表未完成加载（启动初期 / force 重加载期间）时不写缓存：否则会把读到空壳卡的
     *  空段落模型永久缓存，后续徽标再也不会亮 */
    private static EffectParagraphs modelFor(int code) {
        boolean cacheable = DataManager.get().isDataLoaded();
        EffectParagraphs model;
        synchronized (MODEL_CACHE) {
            model = MODEL_CACHE.get(code);
        }
        if (model == null) {
            Card cd = cacheable ? DataManager.get().getCardManager().getCard(code) : null;
            model = EffectParagraphs.build(cd == null || cd.Desc == null ? "" : cd.Desc);
            if (cacheable) {
                synchronized (MODEL_CACHE) {
                    MODEL_CACHE.put(code, model);
                }
            }
        }
        return model;
    }

    /** 新一局/胜负/撤回重同步时清缓存（卡表本身不变，仅防长期驻留与热更卡包后过期） */
    public static void clearCache() {
        synchronized (MODEL_CACHE) {
            MODEL_CACHE.clear();
        }
    }
}
