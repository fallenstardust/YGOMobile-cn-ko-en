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
 * <p>时长语<b>落在括号里</b>的句子只截括号内那一句（用户新规格）：
 * 「②：把墓地的这张卡除外才能发动（这个效果发动的回合，自己不是机械族怪兽不能特殊召唤）。」
 * 列表里只要括号内的限制句，前面的发动正文不列（见 {@link #clipToBracket}）。
 * <p>时长语一律按小写比较（{@link #lowerForSearch}）：官方英/西/葡卡文把句首字母大写
 * （"During the turn this card was activated…"/"El turno en el que activas…"），按原文大小写找词
 * 会整批漏掉拉丁语系；中/日/韩文小写化不变长、不改变下标，命中面与原一致。
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
            "直到下个回合",
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
            "的发动后", "效果发动后", "卡在发动后", "发动后", "特殊召唤的回合", "召唤成功的回合",
            "特殊召唤过的回合", "这个效果发动的场合",
            // 繁中
            "這個回合", "這張卡發動的回合", "這個效果發動的回合", "這個效果發動過的回合",
            "這個效果的發動後", "直至回合結束", "回合結束時", "發動的回合", "發動過的回合",
            "直到下個回合", "直至下個回合",
            // 誓约句式族（繁中同族，剔除规则同简中）
            "的發動後", "效果發動後", "卡在發動後", "發動後", "特殊召喚的回合", "召喚成功的回合",
            "特殊召喚過的回合", "這個效果發動的場合",
            // 日（用「を発動…ターン」主干涵盖卡/效果/卡的效果各主语变体）
            "このターン", "ターン終了時まで", "このターンのエンドフェイズ", "ターン中",
            "を発動したターン", "を発動するターン", "この効果の発動後",
            // 誓约句式族（日，已在本机 assets/jp cards.cdb 上逐词量过命中面：
            // 「このカードを特殊召喚するターン、自分は通常召喚できない」1 句、「このカードは
            // 召喚・反転召喚・特殊召喚したターンには攻撃できない」14 句、「このカードはＬ召喚された
            // ターンにはＬ素材にできない」12 句、「このカードの発動後、次のターンの終了時まで…」13 句、
            // 「①：自分ターンの間、…」15 句；『を発動した場合』与中文裸「发动的场合」同为发动
            // 条件高发写法，仍不入表。本主干去掉格助词「を/で」是为了接住「召喚・特殊召喚したターン」
            // 「この方法で特殊召喚したターン」等变体；带「発動できる」的发动时机句仍由
            // {@link #ACTIVATION_CUES} 剔除）
            "特殊召喚したターン", "特殊召喚するターン", "召喚に成功したターン", "召喚されたターン",
            "の発動後", "は発動後", "発動後", "ターンの終了時まで", "ターンの間",
            // 韩（同样用主干涵盖变体）
            "이 턴", "턴 종료시", "턴 종료시까지", "를 발동한 턴", "를 발동하는 턴",
            "이 효과의 발동 후",
            // 誓约句式族（韩，已在本机 assets/kor cards.cdb 上逐词量过命中面：「이 방법으로 특수 소환한
            // 턴에, 자신은 … 몬스터밖에 특수 소환할 수 없다」18 句、「이 카드를 일반 소환 / 특수 소환하는
            // 턴에 …」、「이 카드는 링크 소환된 턴에는 링크 소재로 할 수 없다」12 句、「이 카드의 발동 후,
            // 다음 턴의 종료시까지 …」11 句；主干同样去宾格词「를/을」以涵盖「方法로 소환한 턴」与
            // 「일반 소환 / 특수 소환한 턴」；『을 발동할 경우』同裸「发动的场合」不入表）
            "특수 소환한 턴", "특수 소환하는 턴", "소환에 성공한 턴", "소환된 턴",
            "의 발동 후", "는 발동 후", "발동 후", "턴의 종료시",
            // 英
            "this turn", "rest of this turn", "end of this turn", "end of the turn",
            "the turn you activate", "the turn you activated",
            "the turn this card was activated", "the turn this effect was activated",
            // 英官方记述另用被动式（"…during the turn this card is activated"/"Cannot attack
            // the turn it is Summoned."），已在本机 assets/en cards.cdb 上逐词量过：前者 15 句、
            // 后者 55 句（带 "except the turn it was sent there" 的除外式靠发动提示语剔除）
            "the turn this card is activated", "the turn it is activated", "the turn it was activated",
            "the turn it is Summoned", "the turn it was Summoned",
            "after you activate this effect", "after activating this effect",
            "after activating this card", "after this card is activated", "after this effect is activated",
            "after the activation", "this card is activated",
            "if you activate this effect",
            "the end of your next turn",
            // 誓约句式族（英官方记述：The turn this card is / was Special Summoned …）
            "the turn this card is Special Summoned", "the turn this card was Special Summoned",
            "the turn you Special Summon",
            // 西（主干「turno en el que」涵盖 activas/activaste/es Invocado/fue mandada 各变体，
            // 已在本机 assets/es cards.cdb 上逐词量过；「excepto en el turno en el que…」这类除外式靠
            // 发动提示语剔除，「No puede atacar en el turno en el que es Invocado.」靠否定式保住）
            "este turno", "resto de este turno", "final de este turno", "final del turno",
            "turno en el que", "próximo turno",
            "el turno en el que activas", "el turno en el que activaste",
            "durante el turno en el que", "después de activar este efecto",
            "después de activar", "después de la activación", "tras activar",
            // 葡（同上，主干「turno em que」涵盖 for Invocado/ativar/for ativado 各变体）
            "este turno", "restante deste turno", "final deste turno", "final do turno",
            "turno em que", "próximo turno",
            "no turno em que ativar", "o turno em que ativar",
            "turno em que você ativar", "após ativar este efeito",
            "após ativar", "após a ativação", "depois de ativar",
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
            // 西/葡官方记述用「durante el/este Duelo」「el resto del Duelo」「restante do Duelo」
            "este duelo", "durante el duelo", "resto del duelo",
            "durante o duelo", "restante do duelo",
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
            "the turn this card is activated", "the turn it is activated", "the turn it was activated",
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
            "特殊召喚したターン", "特殊召喚するターン", "召喚に成功したターン", "召喚されたターン",
            "특수 소환한 턴", "특수 소환하는 턴", "소환에 성공한 턴", "소환된 턴",
            "the turn this card is Special Summoned", "the turn this card was Special Summoned",
            "the turn you Special Summon", "the turn it is Summoned", "the turn it was Summoned"
    };
    /**
     * 「以下效果适用」母句标记（用户新规格：黑点子效果不能靠协议帧判定哪一个在生效时的兜底显示，
     * 见 {@link #motherClause}）：这类卡把持续适用限制写成「…以下效果适用。」+ 换行 ● 子效果，
     * 而 ● 子效果由脚本在发动解算时按活场子计数/选项分支注册（联机时客户端既无逐卡 Lua 执行，
     * 协议也不下发「本次注册了第几个 ●」），故黑点正文无法回指、只能退回列出母句本身，让玩家至少
     * 知道本回合有锁在适用。命中面已在实库六套卡文逐词量过（简中160/日207/韩198/英65/西77/葡14），
     * 一律取「效果＋适用/apply/aplica/aplique」的稳定搭配，避开裸「以下效果」与「选择1个发动」族
     * （多作代价/时机定语，会误伤）。繁中无独立卡库，按既有纪律不入表（宁缺勿错列）。入口随静态块统一小写化。
     */
    private static final String[] MOTHER_EFFECT_MARKS = {
            "以下效果适用",                       // 简中
            "以下の効果を適用",                    // 日
            "이하의 효과를 적용",                  // 韩
            "apply these effects", "apply the following effects", "apply 1 of these effects",   // 英
            "aplica estos efectos", "aplica los siguientes efectos", "aplica 1 de estos efectos", // 西
            "aplique os seguintes efeitos"        // 葡
    };
    /**
     * 跨回合族（用户新规格：这类誓约要跨多回合显示）：『直到下次的自己回合…』『下次的自己回合的
     * ○○阶段跳过』从发动/召唤当回合起一直适用到自己的下个回合结束。本表不参与「是不是适用句」
     * 判定，只在句子已被认定后决定是否把存续期延长到 {@code 发动回合 + }{@link #NEXT_TURN_SPAN}。
     */
    private static final String[] NEXT_TURN_MARKS = {
            "下次的自己回合", "下次的自己的回合", "下个回合", "次の自分のターン", "次のターン",
            "다음의 자신의 턴", "다음 턴", "your next turn", "next turn", "próximo turno"
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

    static {
        //词表入口统一小写化：命中一律走小写比较（lowerForSearch / containsAny），而拉丁语系官方记述
        //把 Summoned / Special 这类词写成大写（“the turn this card is Special Summoned”），不归一
        //则整条永远匹配不到；中/日/韩文取 Locale.ROOT 小写不变，不影响既有命中面。
        for (String[] table : new String[][]{TURN_DURATION_MARKS, DUEL_DURATION_MARKS, DUEL_LIMIT_WORDS,
                ACTIVATION_CUES, NEGATION_TOKENS, TURN_PLEDGE_MARKS, SUMMON_PLEDGE_MARKS, NEXT_TURN_MARKS,
                MOTHER_EFFECT_MARKS}) {
            for (int i = 0; i < table.length; i++) {
                table[i] = table[i].toLowerCase(Locale.ROOT);
            }
        }
    }

    /** 段落模型按卡码缓存：cdb 描述在运行期不变，避免每次刷新重复拆行分段 */
    private static final HashMap<Integer, EffectParagraphs> MODEL_CACHE = new HashMap<>();

    /**
     * 指定半场当前持续适用中的效果列表（无引擎提示来源的便捷入口，等价于 {@code forSide(side, null)}）。
     */
    public static List<Applying> forSide(int side) {
        return forSide(side, null);
    }

    /**
     * 指定半场（本地索引 0=我方 1=对方，随视角互换由两本账本的 {@code swapSides} 统一翻转）
     * 当前持续适用中的效果列表：发动账本、召唤账本与引擎权威上报的在场提示（{@code clientHints}，
     * 用户新规格：来自 {@link GameField#collectApplyingHints} 的 descHints）三源合并解算，整局级在前、
     * 其内按发动/召唤回合升序再按卡码稳定排列，异画与本卡归一后去重（同一笔发动、同一次召唤或同一条
     * 在场提示只列一行）。
     *
     * @param clientHints 该侧在场卡当前「引擎上报为正在适用」的段落提示列表，每项 {@code {卡码, offset}}；
     *                    可为 null（无该来源）。这些提示由服务器随持续效果的注册/注销增删，存在即代表此刻在
     *                    适用（故不另做回合/无效门控），仅当该段落确实抽得出携时长语的适用句时才成行。
     */
    public static List<Applying> forSide(int side, List<int[]> clientHints) {
        int nowTurn = EffectActivationLedger.getCurrentTurn();
        List<Applying> out = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (EffectActivationLedger.Entry e : EffectActivationLedger.snapshot()) {
            EffectActivationLedger.Activation a = e.activation;
            if (a == null || a.side != side || e.code <= 0) continue;
            // 门控0（用户新规格）：效果「成功适用」前不入列表也不亮徽标——刚挂上连锁（MSG_CHAINING）
            // 时本条目被跳过，到 MSG_CHAIN_SOLVED（或链被无效而结算完毕）时才置 applied
            if (!a.applied) continue;
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
            // 兜底（用户新规格：同类描述不许漏）：段落序能解出句子时不走——只有账本里的 offset
            // 对不上本次文本（脚本序与文本圆圈序不一致、辅动/让位槽 offset 越界）且本框恰好一句适用句时才用它。
            // 取句范围按框收紧（用户新规格）：灵摆怪兽卡发动的是怪兽框效果时，绝不能把灵摆框里含「这个回合」
            // 的句子当成本次发动的适用句（反之亦然），故段落命中时只在发动段落所在框内兜底，不再扫整卡；
            // 单框卡与整卡即单效果的传统卡（legacySingle）没有跨框误染风险，仍用整卡描述兜底
            if (clause.isEmpty()) {
                String scope = hit ? model.boxText(a.offset) : model.desc;
                clause = uniqueCardClause(scope, maxGrade);
            }
            // 兜底2（用户新规格：黑点子效果无协议帧可判定哪一个在生效）：发动段落抽不出任何时长语子句时，
            // 若段落块含「…以下效果适用」母句，列出母句本身，让本回合的锁不至于整条不显示
            if (clause.isEmpty()) clause = motherClause(paragraph);
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
            // 兜底路径可能把同码多个 offset 归到同一句，同句不重复成行
            if (listsSameText(out, canonical, text)) continue;
            out.add(new Applying(canonical, a.offset, side, a.turn, text, duel, untilTurn));
        }
        appendSummonEntries(side, out, seen, nowTurn);
        // 引擎权威来源（用户新规格）：在场卡 descHints 指向的段落确为「此刻适用中」的持续效果，
        // 其存在本身即存续门控（服务器随效果注册/注销加删提示，不做回合/无效过滤），仍须按段落取到
        // 携时长语的适用句才成行，抽不出即跳过（与发动/召唤来源同一取句口径，不新增噪声条目）
        appendClientHintEntries(side, clientHints, out, seen, nowTurn);
        Collections.sort(out, BY_TURN_THEN_CODE);
        return out;
    }

    /**
     * 引擎在场提示来源（用户新规格：黑点子效果按 {@link GameField#collectApplyingHints} 上报的信号取句）：
     * 逐条把 {@code {卡码, offset}} 解成一行——按发动段落所在框取句，与 {@link #forSide} 主循环同一
     * durationClause→框内唯一句口径（不走整卡兜底、不走母句兜底：提示已精确指到生效段落，若该段落本身
     * 没有时长语子句说明它是无持续语义的瞬时/代价提示，直接不成行）。已按 {@code canonical:offset} 去重，
     * 同码同句不重复成行。提示为“当前在场适用”，回合基准取 nowTurn。
     */
    private static void appendClientHintEntries(int side, List<int[]> hints, List<Applying> out,
                                                Set<String> seen, int nowTurn) {
        if (hints == null || hints.isEmpty()) return;
        for (int[] h : hints) {
            if (h == null || h.length < 2) continue;
            int code = h[0], offset = h[1];
            if (code <= 0) continue;
            Card cd = DataManager.get().getCardManager().getCard(code);
            int canonical = cd != null && cd.Alias > 0 && cd.Alias != code ? cd.Alias : code;
            if (!seen.add(canonical + ":" + offset)) continue;
            EffectParagraphs model = modelFor(canonical);
            if (!model.hasParagraph(offset)) continue;
            String paragraph = model.blockText(offset);
            String str = DataManager.get().getDesc((int) ((long) canonical * 16 + offset), "");
            String clause = durationClause(paragraph, GRADE_ALL);
            if (clause.isEmpty()) clause = durationClause(str, GRADE_ALL);
            if (clause.isEmpty()) clause = uniqueCardClause(model.boxText(offset), GRADE_ALL);
            if (clause.isEmpty()) continue;
            boolean duel = marksDuelDuration(clause);
            boolean span = !duel && hasNextTurnPledge(clause);
            int untilTurn = duel ? Integer.MAX_VALUE : (span ? nowTurn + NEXT_TURN_SPAN : nowTurn);
            String text = clause.trim();
            if (text.isEmpty()) continue;
            if (listsSameText(out, canonical, text)) continue;
            out.add(new Applying(canonical, offset, side, nowTurn, text, duel, untilTurn));
        }
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

    /** 同码行里已有相同展示文本（兜底抽取与整卡唯一句会撞车，不重复成行） */
    private static boolean listsSameText(List<Applying> rows, int code, String text) {
        for (Applying r : rows) {
            if (r.code == code && text.equals(r.text)) return true;
        }
        return false;
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
     * <p>时机句的剔除只在<b>时长语所在范围</b>内判提示语：时长语在括号里时，提示语只算同括号内
     * 出现的——主句那侧的「才能发动 / you can」是在交代这次发动的代价与时机，与括号里的持续限制无关
     *（实库回归：英/西/葡「negate its effects (until the end of this turn), then you can …」
     * 这类括号内时长语被整句的 you can 连坐删掉，三套库共 18 句）。
     * <p>档位：时长语落在括号内 → {@link #GRADE_BRACKET}（文档：即使那个发动被无效仍然适用）；
     * 命中 {@link #TURN_PLEDGE_MARKS}（『…发动的回合，○○』）→ {@link #GRADE_TURN_PLEDGE}
     *（文档：这个发动被无效的场合，这种描述不再适用）；其余 → {@link #GRADE_APPENDAGE}
     *（效果的附加处理，发动或效果被无效都不适用）。
     */
    private static int pledgeGrade(String sentence) {
        if (TextUtils.isEmpty(sentence)) return -1;
        String hay = lowerForSearch(sentence);
        boolean hasMark = false;
        boolean commaLedMark = false;
        int markOpen = -1, markClose = -1;
        for (String m : TURN_DURATION_MARKS) {
            int from = 0;
            int i;
            while ((i = hay.indexOf(m, from)) >= 0) {
                hasMark = true;
                if (followedByComma(hay, i + m.length())) commaLedMark = true;
                if (markOpen < 0) {
                    int[] pair = bracketAround(hay, i);
                    if (pair != null) {
                        markOpen = pair[0];
                        markClose = pair[1];
                    }
                }
                from = i + m.length();
            }
        }
        for (String m : DUEL_DURATION_MARKS) {
            int from = 0;
            int i;
            while ((i = hay.indexOf(m, from)) >= 0) {
                hasMark = true;
                if (followedByComma(hay, i + m.length())) commaLedMark = true;
                if (markOpen < 0) {
                    int[] pair = bracketAround(hay, i);
                    if (pair != null) {
                        markOpen = pair[0];
                        markClose = pair[1];
                    }
                }
                from = i + m.length();
            }
        }
        if (!hasMark) return -1;
        if (!commaLedMark) {
            //时长语在括号内（括号没闭合时到句末）就只在这一段里找发动提示语
            String scope = sentence;
            if (markOpen >= 0) {
                scope = sentence.substring(markOpen + 1, markClose > markOpen ? markClose : sentence.length());
            }
            if (hasActivationCue(scope)) return -1;
        }
        if (markOpen >= 0) return GRADE_BRACKET;
        return containsAny(sentence, TURN_PLEDGE_MARKS) ? GRADE_TURN_PLEDGE : GRADE_APPENDAGE;
    }

    /**
     * 时长语出现处所在的说明括号区间（{@code [左括号, 右括号]}），不在任何说明括号内时返回 null。
     * <p>只认引号外的括号：韩/英/葡卡文里卡名常写成 "GMX(제노믹스)"、"PSY(싸이)프레임"，
     * 里面的圆括号是卡名的一部分（且常不闭合），当成说明括号会把时长语误判为「括号内」，
     * 截取时还会把切点落在卡名上（实库例：kor 2618725）。判据：左括号前出现奇数个引号
     * 即它在引号里面；括号内自带引号（如 (but you cannot apply that same effect of "X" again this turn)）
     * 不影响它自成为一个说明括号对。
     */
    private static int[] bracketAround(String s, int at) {
        int[] best = null;
        List<Integer> stack = new ArrayList<>();
        int quotes = 0;
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '"') {
                quotes++;
            } else if (BRACKET_OPEN.indexOf(ch) >= 0) {
                if (quotes % 2 == 0) stack.add(Integer.valueOf(i));
            } else if (BRACKET_CLOSE.indexOf(ch) >= 0 && quotes % 2 == 0 && !stack.isEmpty()) {
                int[] pair = new int[]{stack.remove(stack.size() - 1).intValue(), i};
                //嵌套时取最外层（包含本位置的左括号最小、右括号最大那一对）
                if (pair[0] <= at && at < pair[1]
                        && (best == null || pair[0] < best[0] || (pair[0] == best[0] && pair[1] > best[1]))) {
                    best = pair;
                }
            }
        }
        return best;
    }

    /**
     * 小写比较用的副本：{@code Locale.ROOT} 下长度不变才敢拿它的小标回原句取值（否则变长会
     * 把括号/逗号定位带到错位的地方）；个别语言变音符号导致变长时退回原句，代价只是本句
     * 按原文大小写找词（中文/日文/韩文不会走到退回分支）。
     */
    private static String lowerForSearch(String sentence) {
        if (sentence == null) return "";
        String low = sentence.toLowerCase(Locale.ROOT);
        return low.length() == sentence.length() ? low : sentence;
    }

    /**
     * 句子只截括号里那一句（用户新规格）：时长语在括号内时，整句往往还带着
     * 「②：把墓地的这张卡除外才能发动」这样的发动正文，而括号里的『这个效果发动的回合，○○』
     * 才是适用中的限制，列表只该看到后者。
     * <p>只在该句<b>所有</b>时长语命中都在括号内时才裁剪；有一处在括号外就保持整句（否则
     * 「①：这张卡召唤成功的回合，……（……）」这类括号内外的两层适用句会被裁丢一层）。
     * 同句里并列多个括号时逐组截取并以空格拼接，不把两组括号之间的正文一起拉进来。
     * <p>括号字符只看全/半角圆括号（{@link #BRACKET_OPEN} / {@link #BRACKET_CLOSE}），且只认引号外
     * 的说明括号（{@link #bracketAround}）；【】与 [] 是灵摆/怪兽框标题的版式，不能当截取边界。
     */
    private static String clipToBracket(String sentence) {
        if (TextUtils.isEmpty(sentence)) return sentence;
        String hay = lowerForSearch(sentence);
        List<int[]> spans = new ArrayList<>();
        boolean outsideBracketHit = false;
        for (String[] table : new String[][]{TURN_DURATION_MARKS, DUEL_DURATION_MARKS}) {
            for (String m : table) {
                int from = 0;
                int i;
                while ((i = hay.indexOf(m, from)) >= 0) {
                    int[] pair = bracketAround(hay, i);
                    //命中不在说明括号里：整句保留，不能只裁到一半
                    if (pair == null) outsideBracketHit = true;
                    else spans.add(pair);
                    from = i + m.length();
                }
            }
        }
        if (outsideBracketHit || spans.isEmpty()) return sentence;
        Collections.sort(spans, BY_BRACKET_OPEN);
        StringBuilder sb = new StringBuilder();
        int consumedTo = -1;
        for (int[] sp : spans) {
            if (sp[0] < consumedTo) continue;   //同一括号里的多个时长语只取一次
            String inner = sentence.substring(sp[0] + 1, sp[1]).trim();
            if (inner.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(inner);
            consumedTo = sp[1] + 1;
        }
        return sb.length() == 0 ? sentence : sb.toString();
    }

    /** 括号区间按左括号排序，供 {@link #clipToBracket} 合并同一括号内的多个时长语命中 */
    private static final Comparator<int[]> BY_BRACKET_OPEN = new Comparator<int[]>() {
        @Override
        public int compare(int[] x, int[] y) {
            return x[0] - y[0];
        }
    };

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
     * 命中句里的时长语全在括号内时，只截括号内那一句（{@link #clipToBracket}）。
     * 无命中时返回空串（被无效到不剩一句时，整行不列）。
     */
    public static String durationClause(String text, int maxGrade) {
        if (TextUtils.isEmpty(text)) return "";
        StringBuilder sb = new StringBuilder();
        for (String sentence : applyingSentences(text)) {
            int grade = pledgeGrade(sentence);
            if (grade < 0 || grade > maxGrade) continue;
            String trimmed = clipToBracket(sentence).trim();
            if (trimmed.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(trimmed);
        }
        return sb.toString();
    }

    /**
     * 逐句取候选（用户新规格：黑点不列）：按行推进，一旦进入以项目符（●○•…　{@link
     * EffectParagraphs#isBulletChar}）起头的子项，就跳过本子项及其续行——黑点句里抽出的时长语
     * 无法回指它属于哪个效果（一张卡常并列多个 ●，列表只看到一句「这个回合，○○」而不知是谁的限制），
     * 直到下一个 ①②③ 圆圈序号行才重新回到可列段落。召唤来源与发动来源同一口径。
     */
    private static List<String> applyingSentences(String text) {
        List<String> out = new ArrayList<>();
        if (TextUtils.isEmpty(text)) return out;
        boolean inBullet = false;
        for (String line : text.split("\n")) {
            String t = line.trim();
            if (t.isEmpty()) continue;
            if (EffectParagraphs.leadingCircledIndex(t) >= 0) {
                inBullet = false;
            } else if (EffectParagraphs.isBulletChar(t.charAt(0))) {
                inBullet = true;
            }
            if (inBullet) continue;
            out.addAll(splitSentences(line));
        }
        return out;
    }

    /**
     * 给定范围内恰好只有一句适用句时用它（用户新规格：同类描述不许漏）：当发动账本里的段落序对不上
     * 本次文本（脚本 {@code aux.Stringid} 偏移与文本圆圈序不一致、或辅动/让位槽 offset 越界）时，
     * 入参 {@code text} 里恰好只有一句适用句则用它；多句（歧义，不知是哪条）或零句时不列。
     * <p>调用方传入的范围已按框收紧（灵摆怪兽卡用 {@link EffectParagraphs#boxText}，只用发动段落
     * 所在框；单框/传统单效果卡用整卡描述），故本方法不会把另一个框（如灵摆框）的誓约当成本次发动的。
     * <p>本方法只用于兜底，段落序能解出句子时一律不走。
     */
    private static String uniqueCardClause(String text, int maxGrade) {
        String first = null;
        for (String sentence : applyingSentences(text)) {
            int grade = pledgeGrade(sentence);
            if (grade < 0 || grade > maxGrade) continue;
            String trimmed = clipToBracket(sentence).trim();
            if (trimmed.isEmpty()) continue;
            if (first != null) return "";   //多句歧义 → 不列
            first = trimmed;
        }
        return first == null ? "" : first;
    }

    /**
     * 「以下效果适用」母句兜底（用户新规格，与发动账本兜底取句同级）：当发动段落本身抽不出任何时长语
     * 子句（因限制都写在 ● 黑点子项里、而 {@link #applyingSentences} 一律跳过黑点行），但段落块里确有一句
     * 「…以下效果适用」母句时，列出该母句，让玩家知道本回合有一条“以下效果适用”的锁在生效（黑点里具体
     * 是哪一条无法从协议帧回指，故不细分）。只取携 {@link #MOTHER_EFFECT_MARKS} 标记的那句，多句只取首句。
     * <p>本方法只在按框取句/整卡唯一句都落空后才走，不会抢在正常带时长语的适用句之前。
     */
    private static String motherClause(String text) {
        if (TextUtils.isEmpty(text)) return "";
        for (String sentence : applyingSentences(text)) {
            String low = lowerForSearch(sentence);
            for (String m : MOTHER_EFFECT_MARKS) {
                if (low.contains(m)) {
                    String trimmed = sentence.trim();
                    if (!trimmed.isEmpty()) return trimmed;
                }
            }
        }
        return "";
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
        for (String sentence : applyingSentences(text)) {
            if (pledgeGrade(sentence) < 0) continue;
            if (!containsAny(sentence, SUMMON_PLEDGE_MARKS)) continue;
            String trimmed = clipToBracket(sentence).trim();
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

    /**
     * 按句终止符切句，终止符本身归入前句（连续终止符如「。\n」并成一句）。
     * <p>括号内的终止符不断句（英/西/葡官方记述把整条持续限制放在括号里，如
     * 「(The effects remain negated until the end of this turn.)」，按句点切开会截不出括号、
     * 只剩个带半个括号的残句）；但换行是卡文的硬边界，括号没闭合时也照切，
     * 避免一个未闭合左括号吞掉后面整段描述。
     */
    private static List<String> splitSentences(String text) {
        List<String> out = new ArrayList<>();
        int start = 0;
        int depth = 0;
        int quotes = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '"') {
                quotes++;
                continue;
            }
            //引号里的圆括号是卡名的一部分（"GMX(제노믹스)" 一类，且常不闭合），不计入括号深度
            if (BRACKET_OPEN.indexOf(ch) >= 0) {
                if (quotes % 2 == 0) depth++;
                continue;
            }
            if (BRACKET_CLOSE.indexOf(ch) >= 0) {
                if (quotes % 2 == 0 && depth > 0) depth--;
                continue;
            }
            //括号里的句号不算句结束（换行除外），否则会把括号内的一句拆残
            if (ch != '\n' && depth != 0) continue;
            if (SENTENCE_ENDS.indexOf(ch) < 0) continue;
            int end = i + 1;
            while (end < text.length()) {
                char nx = text.charAt(end);
                if (nx == '\n') {
                    end++;
                    continue;
                }
                //括号字符留给下一轮循环统计深度，不在终止符归并里吞掉
                if (depth != 0 || BRACKET_OPEN.indexOf(nx) >= 0 || BRACKET_CLOSE.indexOf(nx) >= 0
                        || SENTENCE_ENDS.indexOf(nx) < 0) break;
                end++;
            }
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
