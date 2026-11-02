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
 * 「持续适用中效果」解算器：从两本决斗级账本里挑出「本回合（含跨到下个自己回合的誓约）
 * 或本局决斗一直适用中」的效果，供顶部 HUD 头像角上的金色感叹号徽标与下拉列表展示。
 * <p>两类来源（用户规格）：
 * <ol>
 * <li><b>发动账本</b> {@link EffectActivationLedger}——MSG_CHAINING 登记的效果发动；</li>
 * <li><b>召唤账本</b> {@link SummonPledgeLedger}——通常/反转/特殊召唤成功。召唤不入连锁、
 * 不产生 MSG_CHAINING，而『把这张卡特殊召唤的回合，○○』『○○召唤成功的回合，○○』这类召唤
 * 誓约恰恰是玩家「现在不能做什么」的直接原因（用户新规格：必须能查到）。</li>
 * </ol>
 * <p>判据（两层串联，缺一不可）：
 * <ol>
 * <li><b>时长语过滤</b>——该段落（或召唤记录下的整卡描述）里存在「持续适用句」：含整回合/整局/
 * 下个回合时长语（{@link #TURN_DURATION_MARKS} / {@link #DUEL_DURATION_MARKS} /
 * {@link #NEXT_TURN_MARKS}），且不是该效果自身的<b>发动时机句</b>。一次性解卡（破坏/抽卡等）不命中。</li>
 * <li><b>存续门控</b>——与卡详「已发动」勾同源：发动当回合恒存续；更早回合须满足下列之一——
 * 命中该段落块的「决斗中只能使用1次」条款（{@link EffectParagraphs#isPerDuel}），
 * 或该子句携「直到下次的自己回合」族（到期回合＝发动回合+2 前均存续）。</li>
 * </ol>
 * <p>关键词与档位按 OCG Rule《誓约》（ocg-rule.readthedocs.io → c03/誓约）校准，分三档
 *（{@link #GRADE_BRACKET} / {@link #GRADE_TURN_PLEDGE} / {@link #GRADE_APPENDAGE}）：
 * 括号内『（这个回合，○○）』与『…发动的回合，○○』都是<b>不是效果</b>的誓约，发动时点立即适用；
 * 前者「即使那个发动被无效仍然适用」，后者「这个发动被无效的场合不再适用」；『这个回合○○』
 * 『发动后，○○』『发动<b>过</b>的回合，○○』『这个效果发动的场合，○○』都是<b>效果的附加处理</b>，
 * 不论发动被无效还是效果被无效都不适用。据此按链的结算结果收紧（用户新规格）：
 * {@link EffectActivationLedger.Activation#negated} 时只保留括号档，
 * {@link EffectActivationLedger.Activation#disabled} 时保留括号档与『发动的回合』档。
 * <p>誓约句与序号位置无关（文档明载「有的放在效果序号之前，有的放在之后，没有区别」），故抽句一律
 * 取 {@link EffectParagraphs#blockText} 的「段落块」而非单段。
 * <p>整局轴上另排除「决斗中只能使用1次」这类<b>使用限制</b>小句：它只限制发动次数、本身不产生
 * 持续适用的效果，命中该小句不算数（见 {@link #marksDuelDuration}）。
 * <p>列表文字（用户新规格）：不列整段序号文本，只取该段落里携时长语的子句；玩家要看完整效果与
 * 已打勾的序号段落，点列表上的卡即可在卡详面板展开。
 * <p>纯函数、无视图依赖：可在主线程调用（账本条目数为个位数～几十，且段落解析结果按卡码缓存）。
 */
public final class ContinuousEffectApplier {

    /** 一条持续适用中效果：发动/召唤卡码（已按 alias 归一，异画与本卡合并）+ 段落文档序
     *（召唤来源无段落序，记 -1）+ 半场 + 发动/召唤回合 + 展示文本（携时长语的子句）
     * + 是否整局级 + 生效截止回合 */
    public static final class Applying {
        public final int code;
        public final int offset;
        public final int side;
        public final int turn;
        public final String text;
        /** true=整局决斗适用中，false=本回合适用中 */
        public final boolean wholeDuel;
        /** 生效截止回合（含）：本回合誓约＝发动回合，『直到下次的自己回合』类＝发动回合+2，
         *  整局级＝{@link Integer#MAX_VALUE}；仅供解算与排序，列表不展示（用户规格：行不加新字段） */
        public final int untilTurn;

        Applying(int code, int offset, int side, int turn, String text, boolean wholeDuel, int untilTurn) {
            this.code = code;
            this.offset = offset;
            this.side = side;
            this.turn = turn;
            this.text = text;
            this.wholeDuel = wholeDuel;
            this.untilTurn = untilTurn;
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
     * <p>誓约句式族的简中形态已在本机实库上逐句量过命中面与误伤面（见表内注释），
     * 西/葡/法/意的誓约译法未逐词验证，按本表既有纪律不凭猜测入词（宁缺勿错列）。
     */
    private static final String[] TURN_DURATION_MARKS = {
            // 简中（官方 zh-CN 译法）
            "这个回合", "回合结束时", "直到回合结束", "这回合",
            "发动的回合", "发动过的回合", "这个效果的发动后",
            // 简中（另一套 zh-SC 译法：「此卡/此效果…发动的回合」「发动此卡的回合」）
            "此回合", "发动此卡的回合", "发动了此卡的回合", "发动此效果的回合",
            "此卡发动的回合", "此效果发动的回合", "此效果发动过的回合", "此效果发动后",
            "直至回合结束",
            // 誓约句式族（OCG Rule《誓约》校准，并在实库 cards.cdb 上逐词量过命中面：
            // 『这张卡/这个效果的发动后，○○』460 句、『把这张卡特殊召唤的回合，○○』93 句、
            // 『○○召唤成功的回合，○○』51 句、『这个方法特殊召唤过的回合，○○』14 句、
            // 『这个效果发动的场合，○○』12 句、『（直到）下次的自己回合…』71 句，都是
            // 「本回合/跨回合适用的限制或状态句」。刻意不入表的高误伤形态：
            // 裸『发动的场合』（245 句里绝大多数是「…的效果发动的场合…才能发动」这类发动条件，
            // 只有带主语的『这个效果发动的场合』才是誓约）、裸『这个方法特殊召唤』（67 句多为
            // 「这个方法特殊召唤的这张卡的攻击力变成…」的常驻描述，属在场就一直适用而不属本回合）、
            // 『发动过的场合』（3 句多为召唤条件）。「下次的自己回合」族单独入
            // {@link #NEXT_TURN_MARKS}（跨回合到期，不影响是不是适用句的判定）。
            "的发动后", "特殊召唤的回合", "召唤成功的回合", "特殊召唤过的回合",
            "这个效果发动的场合",
            // 繁中
            "這個回合", "這張卡發動的回合", "這個效果發動的回合", "這個效果發動過的回合",
            "這個效果的發動後", "直至回合結束", "回合結束時", "發動的回合", "發動過的回合",
            // 誓约句式族（繁中同族，剔除规则同简中）
            "的發動後", "特殊召喚的回合", "召喚成功的回合",
            "特殊召喚過的回合", "這個效果發動的場合",
            // 日（用「を発動…ターン」主干涵盖卡/效果/卡的效果各主语变体）
            "このターン", "ターン終了時まで", "このターンのエンドフェイズ", "ターン中",
            "を発動したターン", "を発動するターン", "この効果の発動後",
            // 誓约句式族（日：只入特殊召唤誓约主干；『を発動した場合』与中文裸「发动的场合」
            // 同为发动条件高发写法，不入表。本机 cdb 只有简中，日/韩/英形态按官方记述惯例录入，
            // 未逐词在实库上验证）
            "を特殊召喚したターン", "で特殊召喚したターン", "召喚に成功したターン",
            // 韩（同样用主干涵盖变体）
            "이 턴", "턴 종료시", "턴 종료시까지", "를 발동한 턴", "를 발동하는 턴",
            "이 효과의 발동 후",
            // 誓约句式族（韩：只入特殊召唤誓约主干，『을 발동할 경우』同裸「发动的场合」不入表）
            "를 특수 소환한 턴", "을 특수 소환한 턴", "소환에 성공한 턴",
            // 英
            "this turn", "rest of this turn", "end of this turn",
            "the turn you activate", "the turn you activated",
            "the turn this card was activated", "the turn this effect was activated",
            "after you activate this effect", "after activating this effect",
            "if you activate this effect",
            // 誓约句式族（英官方记述：The turn this card is / was Special Summoned …）
            "the turn this card is Special Summoned", "the turn this card was Special Summoned",
            "the turn you Special Summon",
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
            "这场决斗中", "這個決鬥中", "这次决斗", "這次決鬥", "这场决斗", "決鬥中", "决斗中", "デュエル中", "듀얼",
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
    /**
     * 『…发动的回合，○○』形态族（文档意义上的「不是效果的誓约」）：判档时命中本表即
     * {@link #GRADE_TURN_PLEDGE}——发动被无效则不再适用，但效果被无效仍适用。
     * <p>注意只收<b>不带「过」</b>的形态：『发动<b>过</b>的回合，○○』按文档属「效果的附加处理」。
     */
    private static final String[] TURN_PLEDGE_MARKS = {
            "发动的回合", "此卡发动的回合", "发动此卡的回合", "发动了此卡的回合",
            "這張卡發動的回合", "這個效果發動的回合", "此效果发动的回合",
            "を発動したターン", "を発動するターン",
            "를 발동한 턴", "를 발동하는 턴",
            "the turn you activate", "the turn you activated", "the turn this card was activated",
            "el turno en el que activas", "el turno en el que activaste",
            "no turno em que ativar", "le tour où vous activez", "turno in cui attivi"
    };
    /**
     * 「召唤的回合」族（{@link SummonPledgeLedger} 的召唤来源专用筛子）：召唤记录没有段落序，
     * 只能从整卡描述里挑出属于本次召唤的子句；其余时长语句属于某个可发动段落，已由发动账本
     * 按 offset 精确列出，从描述里再抓一遍就会重复成行。本表各项均为 {@link #TURN_DURATION_MARKS}
     * 的子集，是不是适用句仍走同一条 {@link #pledgeGrade} 通路（发动时机句一律剔除）。
     */
    private static final String[] SUMMON_PLEDGE_MARKS = {
            "特殊召唤的回合", "召唤成功的回合", "特殊召唤过的回合",
            "特殊召喚的回合", "召喚成功的回合", "特殊召喚過的回合",
            "を特殊召喚したターン", "で特殊召喚したターン", "召喚に成功したターン",
            "를 특수 소환한 턴", "을 특수 소환한 턴", "소환에 성공한 턴",
            "the turn this card is Special Summoned", "the turn this card was Special Summoned",
            "the turn you Special Summon"
    };
    /**
     * 跨回合族（用户新规格：这类誓约要跨多回合显示）：『直到下次的自己回合…』『下次的自己回合的
     * ○○阶段跳过』从发动/召唤当回合起一直适用到自己的下个回合结束。本表不参与「是不是适用句」
     * 判定，只在句子已被认定后决定是否把存续期延长到 {@code 发动回合 + }{@link #NEXT_TURN_SPAN}。
     */
    private static final String[] NEXT_TURN_MARKS = {
            "下次的自己回合", "下次的自己的回合", "次の自分のターン",
            "다음의 자신의 턴", "your next turn"
    };
    /** 『直到下次的自己回合』的到期回合跨度：1v1 里自己的下个回合＝回合计数 +2（tag 下偏差一个回合，取同值） */
    private static final int NEXT_TURN_SPAN = 2;
    /** 括号起始/结束符：时长语落在括号内即「不是效果的誓约」（文档『（这个回合，○○）』一节） */
    private static final String BRACKET_OPEN = "（(";
    private static final String BRACKET_CLOSE = "）)";
    /**
     * 誓约档位（按 OCG Rule《誓约》两档再按形态细分，值越大越容易被链无效摘掉）：
     * {@link #GRADE_BRACKET} 括号内『（这个回合，○○）』——不是效果，发动被无效也仍适用；
     * {@link #GRADE_TURN_PLEDGE}『…发动的回合，○○』——同样不是效果，但发动被无效即不再适用
     *（效果被无效仍适用）；{@link #GRADE_APPENDAGE}『这个回合』『发动后』『发动过的回合』
     * 『这个效果发动的场合』『这次决斗中』——效果的附加处理，发动被无效或效果被无效都不适用。
     */
    private static final int GRADE_BRACKET = 0;
    private static final int GRADE_TURN_PLEDGE = 1;
    private static final int GRADE_APPENDAGE = 2;
    /** 结算结果未受影响时全部档位都收（正常发动与召唤誓约都没有链可无效） */
    private static final int GRADE_ALL = GRADE_APPENDAGE;
    /** 逗号族：时长语后紧跟逗号＝独立状语从句，在陈述本回合持续成立的状态（不是发动时机） */
    private static final String COMMA_LIKE = "，,、";
    /** 扫描时长语后继字符时跳过的空白与日/韩格助词（不影响「の/의」定语判定的结果） */
    private static final String SKIP_AFTER_MARK = " \tのはがをにでと을를에서은는가";
    /** 读句分隔符（与 EffectParagraphs 的 per-duel 小句拆分口径一致） */
    private static final String CLAUSE_SPLIT = "[，,、。．；;]";

    /** 段落模型按卡码缓存：cdb 描述在运行期不变，避免每次刷新重复拆行分段 */
    private static final HashMap<Integer, EffectParagraphs> MODEL_CACHE = new HashMap<>();

    /**
     * 指定半场（本地索引 0=我方 1=对方，随视角互换由两本账本的 {@code swapSides} 统一翻转）
     * 当前持续适用中的效果列表：发动账本与召唤账本合并解算，整局级在前、其内按发动/召唤回合升序
     * 再按卡码稳定排列，异画与本卡归一后去重（同一笔发动或同一次召唤只列一行）。
     */
    public static List<Applying> forSide(int side) {
        int nowTurn = EffectActivationLedger.getCurrentTurn();
        List<Applying> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (EffectActivationLedger.Entry e : EffectActivationLedger.snapshot()) {
            EffectActivationLedger.Activation a = e.activation;
            if (a == null || a.side != side || e.code <= 0) continue;
            Card cd = DataManager.get().getCardManager().getCard(e.code);
            // alias 归一：异画卡与本卡共用效果段落与账本镜像条目，同一笔发动只列一行；
            // 归一码同时决定展示卡图与 str 取用
            int canonical = cd != null && cd.Alias > 0 && cd.Alias != e.code ? cd.Alias : e.code;
            if (!seen.add(canonical + ":" + a.offset + ":" + a.turn)) continue;
            // 例外：全文无①②序号也无 ● 项目符的传统单效果卡（paragraphCount==0，如多数
            // 永续/场地魔法）没有段落可命中，但它的 desc 就是该效果本身，同样纳入判定
            EffectParagraphs model = modelFor(canonical);
            boolean legacySingle = model.paragraphCount == 0;
            boolean hit = model.hasParagraph(a.offset);
            if (!hit && !legacySingle) continue;
            String paragraph = hit ? model.blockText(a.offset) : (legacySingle ? model.desc : "");
            // cdb str：上游整改后即该段效果文字（短标签或全文）
            String str = DataManager.get().getDesc((int) ((long) canonical * 16 + a.offset), "");
            // 门控①时长＋时机＋档位（用户新规格1/2/3，叠加被无效收紧）：只收「持续适用句」，
            // 并按链的结算结果摘掉相应档位——发动被无效只留括号档、效果被无效留括号与『发动的回合』档；
            // 摘完不剩句子就不列（抽不出子句直接丢弃，不降级到整段全文）
            int maxGrade = a.negated ? GRADE_BRACKET : (a.disabled ? GRADE_TURN_PLEDGE : GRADE_ALL);
            String clause = durationClause(paragraph, maxGrade);
            if (clause.isEmpty()) clause = durationClause(str, maxGrade);
            if (clause.isEmpty()) continue;
            // 子句里含整局词即归整局级（寿命跨回合）；否则若携跨回合族，寿命延到下个自己回合
            boolean duel = marksDuelDuration(clause);
            boolean span = !duel && hasNextTurnPledge(clause);
            int untilTurn = duel ? Integer.MAX_VALUE : (span ? a.turn + NEXT_TURN_SPAN : a.turn);
            // 门控②存续：与卡详已发动勾同款判定（当回合恒存续，更早回合须命中 per-duel 条款），
            // 再叠加跨回合誓约的到期放行
            if (a.turn != nowTurn && !(hit && model.isPerDuel(a.offset))
                    && !(span && nowTurn <= untilTurn)) continue;
            String text = clause.trim();
            if (text.isEmpty()) continue;
            out.add(new Applying(canonical, a.offset, side, a.turn, text, duel, untilTurn));
        }
        appendSummonEntries(side, out, seen, nowTurn);
        Collections.sort(out, BY_TURN_THEN_CODE);
        return out;
    }

    /**
     * 召唤誓约来源（用户新规格2）：把 {@link SummonPledgeLedger} 里该侧的召唤成功记录解算成行——
     * 从整卡描述里只取「召唤的回合」族子句（{@link #SUMMON_PLEDGE_MARKS}），同一张卡的多个召唤誓约句
     * 拼成一行（沿用每卡一行的既定形态）。
     * <p>召唤没有链可无效（无效召唤根本不会落账，见 {@link SummonPledgeLedger}），故一律按
     * {@link #GRADE_ALL} 取句；已被发动账本同码同回合行列过的句子不再重复拼入。
     */
    private static void appendSummonEntries(int side, List<Applying> out, Set<String> seen, int nowTurn) {
        for (SummonPledgeLedger.Entry se : SummonPledgeLedger.snapshot()) {
            if (se.side != side || se.code <= 0) continue;
            Card cd = DataManager.get().getCardManager().getCard(se.code);
            int canonical = cd != null && cd.Alias > 0 && cd.Alias != se.code ? cd.Alias : se.code;
            if (!seen.add(canonical + ":S:" + se.turn)) continue;
            String clause = summonPledgeClause(modelFor(canonical).desc, out, canonical);
            if (clause.isEmpty()) continue;
            boolean duel = marksDuelDuration(clause);
            boolean span = !duel && hasNextTurnPledge(clause);
            int untilTurn = duel ? Integer.MAX_VALUE : (span ? se.turn + NEXT_TURN_SPAN : se.turn);
            if (se.turn != nowTurn && nowTurn > untilTurn) continue;
            String text = clause.trim();
            if (text.isEmpty()) continue;
            out.add(new Applying(canonical, -1, side, se.turn, text, duel, untilTurn));
        }
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
     * 单句的誓约档位（用户新规格1的核心判定，叠加被无效收紧）：含时长语（{@link #TURN_DURATION_MARKS}
     * / {@link #DUEL_DURATION_MARKS}）且不是在交代该效果自身的发动时机时返回档位，否则 -1。
     * <p>发动时机句的特征：既带「才能发动」类提示语，句里的时长语又都是定语用法（后面不紧跟逗号），
     * 如「①：这张卡发动的回合的自己主要阶段才能发动。」/「①：このカードを発動したターンの自分
     * メインフェイズに発動できる。」/「①： 이 카드를 발동한 턴의 자신 메인 페이즈에 발동할 수 있다。」
     * /「During your Main Phase, if this card was activated this turn: You can …」。
     * <p>而时长语后紧跟逗号的，是独立状语从句、在陈述本回合持续成立的状态，即使同句带发动提示语也保留，
     * 如「②：把墓地的这张卡除外才能发动（这个效果发动的回合，自己不是机械族怪兽不能特殊召唤）。」
     * <p>档位：时长语落在括号内 → {@link #GRADE_BRACKET}（文档：即使那个发动被无效仍然适用）；
     * 命中 {@link #TURN_PLEDGE_MARKS}（『…发动的回合，○○』）→ {@link #GRADE_TURN_PLEDGE}
     *（文档：这个发动被无效的场合，这种描述不再适用）；其余 → {@link #GRADE_APPENDAGE}
     *（效果的附加处理，发动或效果被无效都不适用）。
     */
    private static int pledgeGrade(String sentence) {
        if (TextUtils.isEmpty(sentence)) return -1;
        boolean hasMark = false;
        boolean commaLedMark = false;
        boolean bracketed = false;
        for (String m : TURN_DURATION_MARKS) {
            int from = 0;
            int i;
            while ((i = sentence.indexOf(m, from)) >= 0) {
                hasMark = true;
                if (followedByComma(sentence, i + m.length())) commaLedMark = true;
                if (!bracketed) bracketed = isBracketed(sentence, i);
                from = i + m.length();
            }
        }
        for (String m : DUEL_DURATION_MARKS) {
            int from = 0;
            int i;
            while ((i = sentence.indexOf(m, from)) >= 0) {
                hasMark = true;
                if (followedByComma(sentence, i + m.length())) commaLedMark = true;
                if (!bracketed) bracketed = isBracketed(sentence, i);
                from = i + m.length();
            }
        }
        if (!hasMark) return -1;
        if (!commaLedMark && hasActivationCue(sentence)) return -1;
        if (bracketed) return GRADE_BRACKET;
        return containsAny(sentence, TURN_PLEDGE_MARKS) ? GRADE_TURN_PLEDGE : GRADE_APPENDAGE;
    }

    /** 时长语出现处是否被括号包住：从其位置向左退，先撞右括号则不在括号内，先撞左括号则在内 */
    private static boolean isBracketed(String s, int at) {
        for (int i = at - 1; i >= 0; i--) {
            char ch = s.charAt(i);
            if (BRACKET_CLOSE.indexOf(ch) >= 0) return false;
            if (BRACKET_OPEN.indexOf(ch) >= 0) return true;
        }
        return false;
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
     * 从整段效果文本里只抽出「正在适用中且未被无效摘掉」的那句（用户新规格：列表不展示完整序号文本，
     * 只要「这个回合，xxx」/「这个效果发动过的回合，xxx」/「这个效果的发动后，xxx」这类子句，
     * 并排除「这张卡发动的回合的…阶段才能发动」这类发动时机句）：
     * 按句终止符（。．！？!?；; 与换行）切句，保留 {@link #pledgeGrade} 命中且档位不高于
     * {@code maxGrade} 的句子并以空格拼接；逗号/顿号不断句（「这个回合，」与后半句必须同现）。
     * 无命中时返回空串（被无效到不剩一句时，整行不列）。
     */
    public static String durationClause(String text, int maxGrade) {
        if (TextUtils.isEmpty(text)) return "";
        StringBuilder sb = new StringBuilder();
        for (String sentence : splitSentences(text)) {
            int grade = pledgeGrade(sentence);
            if (grade < 0 || grade > maxGrade) continue;
            String trimmed = sentence.trim();
            if (trimmed.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(trimmed);
        }
        return sb.toString();
    }

    /**
     * 从整卡描述里只抽出属于<b>本次召唤</b>的誓约句（{@link #SUMMON_PLEDGE_MARKS}）：适用句判定
     * 与发动来源同一走 {@link #pledgeGrade}（一律按 {@link #GRADE_ALL}，召唤没有链可无效），
     * 但只保留携「召唤的回合」族词的句子，避免把已由发动账本按 offset 列过的同码句子重复成行
     *（{@code sameCodeRows} 里已有文本包含本句时直接跳过）。
     */
    private static String summonPledgeClause(String text, List<Applying> sameCodeRows, int canonical) {
        if (TextUtils.isEmpty(text)) return "";
        StringBuilder sb = new StringBuilder();
        for (String sentence : splitSentences(text)) {
            if (pledgeGrade(sentence) < 0) continue;
            if (!containsAny(sentence, SUMMON_PLEDGE_MARKS)) continue;
            String trimmed = sentence.trim();
            if (trimmed.isEmpty()) continue;
            boolean dup = false;
            for (Applying r : sameCodeRows) {
                if (r.code == canonical && r.text != null && r.text.contains(trimmed)) {
                    dup = true;
                    break;
                }
            }
            if (dup) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(trimmed);
        }
        return sb.toString();
    }

    /** 子句是否携「直到下次的自己回合」族：是则本行跨回合存续，到期回合＝发动/召唤回合+2 */
    private static boolean hasNextTurnPledge(String clause) {
        return containsAny(clause, NEXT_TURN_MARKS);
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
