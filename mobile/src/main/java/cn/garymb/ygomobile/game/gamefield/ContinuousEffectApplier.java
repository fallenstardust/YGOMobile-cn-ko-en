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
 * 「持续适用中效果」解算器：从决斗级账本里挑出「此刻还在适用」的效果，供顶部 HUD 的金色徽标
 * 与下拉列表展示（每行只放携时长语的子句，完整效果与已打勾段落点列表上的卡回卡详看）。
 * <p>三个来源：{@link EffectActivationLedger}（效果发动）、{@link SummonPledgeLedger}（召唤成功，
 * 不入连锁所以没有发动记录，但召唤誓约正是玩家「现在不能做什么」的直接原因）、
 * {@link GameField#collectApplyingHints}（引擎上报在场适用的段落）。
 * <p>一句要不要列，先看它携哪个时长的档位（{@link #pledgeGrade}），再看这次结算有没有把它否掉：
 * <pre>
 *   档位           记述形态                  发动无效  效果无效  本卡在场被无效
 *   GRADE_BRACKET     括号内「（这个回合，…）」  列       列       列
 *   GRADE_TURN_PLEDGE 不带括号「…发动的回合，…」 不列     列       列
 *   GRADE_APPENDAGE   不带括号「这个回合…」「发动后…」 不列 不列   不列
 * </pre>
 * 上两档<b>不是效果</b>、发动时点立即适用（规律 1/2：发动被无效时括号内的仍适用、「发动的回合」的不适用）；
 * APPENDAGE <b>是卡的效果</b>（前段效果的附加处理），效果处理时才适用，所以任一型无效都不适用
 *（规律 3/4：逗号连接的从句跟正文整句同进同出，{@link #splitSentences} 不以逗号断句）。
 * <p>召唤誓约按适用时点分两族（规律 5/6/7）：『把这张卡特殊召唤的回合』属召唤时点即适用，只有召唤
 * 被无效才不适用（无效召唤根本不落账）；含「成功」的 {@link #SUMMON_SUCCESS_WORDS} 与
 * {@link #SUMMON_APPLY_WORDS}（「…召唤时适用」族）是召唤成功后<b>在场上适用</b>的效果，
 * 那张卡在场且被无效即不适用（特殊召唤<b>之际</b>还不适用，由挂账/落账时序保证）。
 * <p>「本卡在场被无效」这一门控对三个来源统一生效：{@link #fieldNegated} 按
 * {@link GameField#collectNegatedCodes} 把上限收到 {@link #GRADE_TURN_PLEDGE}（摘掉 APPENDAGE），
 * 召唤来源再按 {@link #SUMMON_ONFIELD_MARKS} 逐句摘除；无效解除后下一次解算自行恢复。
 * <p>取句口径：段落块 {@link EffectParagraphs#blockText}（誓约写在序号前后同等看待）、时长语全在
 * 括号内时只截括号内那句（{@link #clipToBracket}）、词表一律小写比较（{@link #lowerForSearch}）、
 * 「决斗中只能使用1次」是使用限制不是持续适用（{@link #marksDuelDuration}）。
 * <p>还有一道前置条件闸门（{@link #blockedByActivateZone}）：列王诗篇、灵王的波动、霆王的闪光、
 * 命王的螺旋把条件写在本句开头——「这张卡从手卡发动的场合，发动后，这次决斗中自己不能…」，从场上盖放
 * 发动时后半句并不适用；无限泡影同型（「盖放的这张卡发动的场合，再在这个回合中…」）。这类条件凭
 * MSG_CHAINING 带的发动区域判（{@link EffectActivationLedger.Activation#location}：手卡 0x02 /
 * 魔陷区 0x08），区域不符则该句不列；区域未知（引擎提示来源）时不判。
 * <p>纯函数、无视图依赖，可在主线程调用（账本条目个位数～几十，段落模型按卡码缓存）。
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

    /** 把下面的共用词组合成实际用的辨识表，避开同一批词在几张表里各抄一遍 */
    private static String[] concat(String[] head, String[] tail) {
        String[] out = new String[head.length + tail.length];
        System.arraycopy(head, 0, out, 0, head.length);
        System.arraycopy(tail, 0, out, head.length, tail.length);
        return out;
    }

    /**
     * 「…召唤时适用。○○」族（规律 6）：不写「…的回合」而直接声明何时起适用的记述，
     * 六套 cdb 实测命中：简中 10/日 30/韩 10/英 18/西 23/葡 16 张。一份词表进四处：
     * 算适用句（{@link #TURN_DURATION_MARKS}）、召唤来源取句（{@link #SUMMON_PLEDGE_MARKS}）、
     * 本卡被无效时不列（{@link #SUMMON_ONFIELD_MARKS}）、不当作发动时机句（{@link #pledgeGrade}）。
     * 不用宽主干：英 "after this card is" 命中里有 5 张是 "…after this card is activated"，
     * "if this card is Special Summoned" 命中 469 张多为 "…: You can …" 的发动条件句。
     */
    private static final String[] SUMMON_APPLY_WORDS = {
            "召唤时适用", "召喚した時に適用", "소환했을 때에 적용",
            "after this card is fusion summoned", "after this card is synchro summoned",
            "after this card is xyz summoned", "after this card is special summoned",
            "after this card is normal or special summoned", "after you normal summon this card",
            "after you special summon this card", "after you normal or special summon this card",
            "después de que esta carta sea invocada", "después de que esta carta es invocada",
            "después de que esta carta fue invocada", "después de que invoques esta carta",
            "depois que este card for invocado", "depois de invocar este card"
    };

    /**
     * 整回合时长语（逐词在 setDesception/ygopro-database 实库 10 套语言 cards.cdb 上验过命中率，
     * 小写比较）：含纯时长状语（「这个回合」「ターン終了時まで」）、这回合内因这次发动而持续成立的
     * 「…发动的回合/发动后」族与多语言对应写法；不取「1回合1次」这类只限次数的措辞。
     * 本表只列各语言的时长词形；命中后的取舍见类注释的三档与 {@link #pledgeGrade}。
     * 法/意的誓约译法未逐词验证，按既有纪律不凭猜测入词（宁缺勿错列）。
     */
    private static final String[] TURN_DURATION_WORDS = {
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
    /** 适用句时长语总表＝各语言时长词形 + 「…召唤时适用」族 */
    private static final String[] TURN_DURATION_MARKS = concat(TURN_DURATION_WORDS, SUMMON_APPLY_WORDS);
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
     * 「召唤的回合」族：召唤记录没有段落序，只能从整卡描述里挑出属于本次召唤的句子
     *（{@link #summonPledgeClause}）；其余时长句属于某个可发动段落，已由发动账本按 offset 列出。
     */
    private static final String[] SUMMON_TURN_WORDS = {
            "特殊召唤的回合", "召唤成功的回合", "特殊召唤过的回合",
            "特殊召喚的回合", "召喚成功的回合", "特殊召喚過的回合",
            "特殊召喚したターン", "特殊召喚するターン", "召喚に成功したターン", "召喚されたターン",
            "특수 소환한 턴", "특수 소환하는 턴", "소환에 성공한 턴", "소환된 턴",
            "the turn this card is Special Summoned", "the turn this card was Special Summoned",
            "the turn you Special Summon", "the turn it is Summoned", "the turn it was Summoned"
    };
    /**
     * {@link #SUMMON_TURN_WORDS} 里含「成功」的子集（规律 6/7）：要特殊召唤<b>成功</b>后才适用、
     * 属在场上一直适用的效果，所以本卡在场且被无效即不列（无效解除后下一次解算自行恢复）。
     * 不含「成功」的『把这张卡特殊召唤的回合』按规律 5 属召唤时点即适用，场上无效不影响。
     */
    private static final String[] SUMMON_SUCCESS_WORDS = {
            "召唤成功的回合", "特殊召唤过的回合",
            "召喚成功的回合", "特殊召喚過的回合",
            "召喚に成功したターン", "소환에 성공한 턴"
    };
    /** 召唤来源的取句表＝「召唤的回合」族 + 「…召唤时适用」族 */
    private static final String[] SUMMON_PLEDGE_MARKS = concat(SUMMON_TURN_WORDS, SUMMON_APPLY_WORDS);
    /** 场上无效时要一并摘掉的召唤誓约族（规律 6/7）＝含「成功」子集 + 「…召唤时适用」族 */
    private static final String[] SUMMON_ONFIELD_MARKS = concat(SUMMON_SUCCESS_WORDS, SUMMON_APPLY_WORDS);
    /** ocgcore common.h LOCATION_HAND / LOCATION_SZONE：MSG_CHAINING 带的发动区域取值 */
    private static final int LOC_HAND = 0x02;
    private static final int LOC_SZONE = 0x08;
    /**
     * 「这张卡从手卡发动的场合」族（用户新规格）：这类卡把<b>后续持续适用的前置条件</b>就写在适用句同句里
     * （「…才能发动。那个效果无效。这张卡从手卡发动的场合，发动后，这次决斗中自己不能…」），从场上盖放
     * 发动时后半句根本不适用 → 不得进列表。成立与否只能凭 {@link EffectActivationLedger.Activation#location}
     * 判（手卡发动 ⇔ 0x02），故命中本族而发动区域不是手卡时该句不列。
     * <p>锚长到带「这张卡/このカード/이 카드를」主语是实测必需：短主干会误收
     * 「雷族怪兽的效果在手卡发动的场合才能发动」那类发动时机句（带主语版三套各 7 张，
     * 短版则 9/10 张）；英/西/葡的对应记述是「Set 过且结算时仍在场」的复合条件，后半条客户端无信号，
     * 按既有纪律不入表（不判即保持现有显示，不会新增漏列）。
     */
    private static final String[] FROM_HAND_ACTIVATE_MARKS = {
            "从手卡发动的场合", "このカードを手札から発動した場合", "이 카드를 패에서 발동했을 경우"
    };
    /**
     * 「盖放的这张卡发动的场合」族（同上机制，成立⇔发动区域＝魔陷区 0x08）：无限泡影类
     * 「那只怪兽的效果直到回合结束时无效。盖放的这张卡发动的场合，再在这个回合中…」——从手卡直接发动时
     * 后半句不适用。同样只用带主语的完整主干（三套各 2 张）；短主干「盖放的这张卡」命中 102 张，不可用。
     */
    private static final String[] FROM_SET_ACTIVATE_MARKS = {
            "盖放的这张卡发动的场合", "セットされていたこのカードを発動した場合",
            "세트되어 있던 이 카드를 발동했을 경우"
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
     * 誓约档位（按用户归纳的记述规律分档，值越大越容易被无效摘掉）：
     * {@link #GRADE_BRACKET} 括号内『（这个回合，○○）』——不是效果，发动时点立即适用，
     * 发动被无效也仍适用；{@link #GRADE_TURN_PLEDGE}『…发动的回合，○○』——同样不是效果，
     * 但发动被无效即不再适用（效果被无效仍适用）；{@link #GRADE_APPENDAGE}『这个回合』『发动后』
     * 『发动过的回合』『这个效果发动的场合』『这次决斗中』——卡的效果（前段效果的附加处理），
     * 发动被无效或效果被无效都不适用。
     */
    private static final int GRADE_BRACKET = 0;
    private static final int GRADE_TURN_PLEDGE = 1;
    private static final int GRADE_APPENDAGE = 2;
    /** 结算结果未受影响时全部档位都收（正常发动与召唤誓约都没有链可无效） */
    private static final int GRADE_ALL = GRADE_APPENDAGE;
    /** 逗号族：时长语后紧跟逗号＝独立状语从句，在陈述本回合持续成立的状态（不是发动时机） */
    private static final String COMMA_LIKE = "，,、";
    /**
     * 定语标记：时长语后紧跟此字即「在挑对象」而不是「在定时间」——「可以选｛这个回合被破坏｝的
     * 1只怪兽」是一次性处理里的定语，不是一条本回合持续成立的限制（实测六库共 17 句这类误报，
     * 而「直到下个回合的结束时…」这类真持续句后跟的是「的」，不被本判据误伤）。
     */
    private static final String ATTRIBUTIVE_FOLLOW = "被";
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
                MOTHER_EFFECT_MARKS, SUMMON_ONFIELD_MARKS, SUMMON_APPLY_WORDS,
                FROM_HAND_ACTIVATE_MARKS, FROM_SET_ACTIVATE_MARKS}) {
            for (int i = 0; i < table.length; i++) {
                table[i] = table[i].toLowerCase(Locale.ROOT);
            }
        }
    }

    /** 段落模型按卡码缓存：cdb 描述在运行期不变，避免每次刷新重复拆行分段 */
    private static final HashMap<Integer, EffectParagraphs> MODEL_CACHE = new HashMap<>();

    /**
     * 指定半场当前持续适用中的效果列表（无引擎提示与场上无效来源的便捷入口，
     * 等价于 {@code forSide(side, null, null)}）。
     */
    public static List<Applying> forSide(int side) {
        return forSide(side, null, null);
    }

    /**
     * 指定半场（本地索引 0=我方 1=对方，随视角互换由两本账本的 {@code swapSides} 统一翻转）
     * 当前持续适用中的效果列表：发动账本、召唤账本与引擎权威上报的在场提示（{@code clientHints}，
     * 用户新规格：来自 {@link GameField#collectApplyingHints} 的 descHints）三源合并解算，整局级在前、
     * 其内按发动/召唤回合升序再按卡码稳定排列，异画与本卡归一后去重（同一笔发动、同一次召唤或同一条
     * 在场提示只列一行）。
     *
     * @param clientHints    该侧在场卡当前「引擎上报为正在适用」的段落提示列表，每项 {@code {卡码, offset}}；
     *                       可为 null（无该来源）。这些提示由服务器随持续效果的注册/注销增删，存在即代表此刻在
     *                       适用（故不另做回合/无效门控），仅当该段落确实抽得出携时长语的适用句时才成行。
     * @param negatedOnField 该侧场上此刻效果被无效的卡码（{@link GameField#collectNegatedCodes}，可为 null）：
     *                       用户规律 3/6/7，发动账本的附属处理（{@link #GRADE_APPENDAGE}）与召唤誓约的
     *                       "在场上适用"那族（{@link #SUMMON_ONFIELD_MARKS}）在本卡被无效期间不拼入行内；
     *                       不含"成功"的『把这张卡特殊召唤的回合』族属召唤时点适用，不受本参数影响（规律 5）。
     */
    public static List<Applying> forSide(int side, List<int[]> clientHints, Set<Integer> negatedOnField) {
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
            // 档位收紧（按规律 1/2/3 区分两型无效）：只收「持续适用句」，发动被无效（negated）时摘掉
            // 『…发动的回合』与附加处理档、只留括号内的仍然适用的誓约；效果被无效（disabled）时摘掉
            // 附加处理档（是卡的效果）、保留括号内与『…发动的回合』两档（都不是效果）；
            // 摘完不剩句子就不列（抽不出子句直接丢弃，不降级到整段全文）
            int baseMaxGrade = a.negated ? GRADE_BRACKET : (a.disabled ? GRADE_TURN_PLEDGE : GRADE_ALL);
            // 场上无效门控：那张卡此刻在场且被无效时，附属处理档一律不拼入行内（无论发动/效果哪型无效）
            int maxGrade = fieldNegated(baseMaxGrade, e.code, canonical, negatedOnField);
            // 取句时另按发动区域筛掉「从手卡/从盖放发动的场合」类前置条件不成立的句（用户新规格）
            String clause = durationClause(paragraph, maxGrade, a.location);
            if (clause.isEmpty()) clause = durationClause(str, maxGrade, a.location);
            // 兜底（用户新规格：同类描述不许漏）：段落序能解出句子时不走——只有账本里的 offset
            // 对不上本次文本（脚本序与文本圆圈序不一致、辅动/让位槽 offset 越界）且本框恰好一句适用句时才用它。
            // 取句范围按框收紧（用户新规格）：灵摆怪兽卡发动的是怪兽框效果时，绝不能把灵摆框里含「这个回合」
            // 的句子当成本次发动的适用句（反之亦然），故段落命中时只在发动段落所在框内兜底，不再扫整卡；
            // 单框卡与整卡即单效果的传统卡（legacySingle）没有跨框误染风险，仍用整卡描述兜底
            if (clause.isEmpty()) {
                String scope = hit ? model.boxText(a.offset) : model.desc;
                clause = uniqueCardClause(scope, maxGrade, a.location);
            }
            // 兜底2（用户新规格：黑点子效果无协议帧可判定哪一个在生效）：发动段落抽不出任何时长语子句时，
            // 若段落块含「…以下效果适用」母句，列出母句本身，让本回合的锁不至于整条不显示。
            // 母句本身就是这段效果的处理（规律 3：是卡的效果），发动被无效或效果被无效都不适用，不再兜底列出
            if (clause.isEmpty() && maxGrade == GRADE_ALL) clause = motherClause(paragraph);
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
        appendSummonEntries(side, out, seen, nowTurn, negatedOnField);
        // 引擎权威来源（用户新规格）：在场卡 descHints 指向的段落确为「此刻适用中」的持续效果，
        // 其存在本身即存续门控（服务器随效果注册/注销加删提示，不做回合/无效过滤），仍须按段落取到
        // 携时长语的适用句才成行，抽不出即跳过（与发动/召唤来源同一取句口径，不新增噪声条目）
        appendClientHintEntries(side, clientHints, out, seen, nowTurn, negatedOnField);
        Collections.sort(out, BY_TURN_THEN_CODE);
        return out;
    }

    /**
     * 引擎在场提示来源（用户新规格：黑点子效果按 {@link GameField#collectApplyingHints} 上报的信号取句）：
     * 逐条把 {@code {卡码，offset}} 解成一行——按发动段落所在框取句，与 {@link #forSide} 主循环同一
     * durationClause→框内唯一句口径（不走整卡兜底、不走母句兜底：提示已精确指到生效段落，若该段落本身
     * 没有时长语子句说明它是无持续语义的瞬时/代价提示，直接不成行）。已按 {@code canonical:offset} 去重，
     * 同码同句不重复成行。提示为“当前在场适用”，回合基准取 nowTurn。
     * <p>例外（用户新规格：列王诗篇、灵王的波动、霆王的闪光、命王的螺旋类）：这类卡把附加处理效果的
     * {@code CLIENT_HINT} 注册放在「从手卡发动才成立」的分支里（脚本里是
     * {@code if e:GetHandler():IsStatus(STATUS_ACT_FROM_HAND) then e:SetLabel(100)} 才 RegisterEffect），
     * 所以本通道的存在就是「前置条件已满足」的权威信号（盖放发动时核心根本不注册，也没有提示），
     * 而它们的提示 offset 是脚本侧 {@code aux.Stringid(id,2/3)} 的偏移，对得上文本里的圆圈序（这类卡
     * 描述只有一个 ①），且 cdb str 只有「「XX」效果适用中」这种短标签（不含时长语）——故允许段落对不上时
     * 退回整卡描述里的<b>唯一</b>适用句（多句歧义仍不列），不再因 offset 越界直接丢弃。
     */
    private static void appendClientHintEntries(int side, List<int[]> hints, List<Applying> out,
                                                Set<String> seen, int nowTurn, Set<Integer> negatedOnField) {
        if (hints == null || hints.isEmpty()) return;
        for (int[] h : hints) {
            if (h == null || h.length < 2) continue;
            int code = h[0], offset = h[1];
            if (code <= 0) continue;
            Card cd = DataManager.get().getCardManager().getCard(code);
            int canonical = cd != null && cd.Alias > 0 && cd.Alias != code ? cd.Alias : code;
            if (!seen.add(canonical + ":" + offset)) continue;
            EffectParagraphs model = modelFor(canonical);
            // 场上无效门控（规律 3）：提示虽由服务器给出，归属卡被无效时附属处理档也不适用
            int maxGrade = fieldNegated(GRADE_ALL, code, canonical, negatedOnField);
            // 提示本身不带发动区域信息，传 0 不做前置条件判定（条件本身已由提示存在与否保证）
            String clause = "";
            if (model.hasParagraph(offset)) {
                clause = durationClause(model.blockText(offset), maxGrade, 0);
                if (clause.isEmpty()) {
                    String str = DataManager.get().getDesc((int) ((long) canonical * 16 + offset), "");
                    clause = durationClause(str, maxGrade, 0);
                }
                if (clause.isEmpty()) clause = uniqueCardClause(model.boxText(offset), maxGrade, 0);
            }
            // offset 对不上文本圆圈序（脚本侧偏移）时的整卡唯一适用句兜底
            if (clause.isEmpty()) clause = uniqueCardClause(model.desc, maxGrade, 0);
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
     * <p>规律 5：『把这张卡特殊召唤的回合，○○』属召唤时点即适用的誓约，只有召唤被无效才不适用，
     * 而本账本只收 {@code *SUMMONED} 落账的成功召唤（无效召唤停在挂账阶段、卡回到非怪兽区时即刻撤单，
     * 回合轮转再清）→ 此类誓约天然不会为被无效的召唤成行。
     * <p>规律 6/7：『…召唤成功的回合，○○』『…召唤时适用。○○』与『这个方法特殊召唤（成功）过的回合，○○』
     * 要成功后才适用、是<b>在场上适用</b>的效果，所以那张卡此刻在场且效果被无效时不拼入本行
     *（无效解除后下一次解算自行恢复）。
     */
    private static void appendSummonEntries(int side, List<Applying> out, Set<String> seen, int nowTurn,
                                           Set<Integer> negatedOnField) {
        for (SummonPledgeLedger.Entry se : SummonPledgeLedger.snapshot()) {
            if (se.side != side || se.code <= 0) continue;
            Card cd = DataManager.get().getCardManager().getCard(se.code);
            int canonical = cd != null && cd.Alias > 0 && cd.Alias != se.code ? cd.Alias : se.code;
            if (!seen.add(canonical + ":S:" + se.turn)) continue;
            // 场上无效门控只对「在场上适用」那一族句子生效（规律 5 的召唤时点族不受影响）
            boolean fieldNegated = negatedOnField != null
                    && (negatedOnField.contains(se.code) || negatedOnField.contains(canonical));
            String clause = summonPledgeClause(modelFor(canonical).desc, out, canonical, fieldNegated);
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
     * 单句判档（返回 {@link #GRADE_BRACKET}/{@link #GRADE_TURN_PLEDGE}/{@link #GRADE_APPENDAGE}，
     * 不算适用句时返回 -1），三条剔除依次走：
     * <ol>
     * <li>不带时长语 → -1；</li>
     * <li>时长语全是定语用法（后跟「被」，如「这个回合被破坏的那只怪兽」）且无逗号引导 → -1；</li>
     * <li>同句带「才能发动 / you can」一类提示语且时长语都不是逗号引导的独立状语 → 这只是在
     * 交代发动时机（「这张卡发动的回合的自己主要阶段才能发动」）→ -1；但命中
     * {@link #SUMMON_APPLY_WORDS}（「…召唤时适用」族本身就在声明适用时点）时不剔除，
     * 提示语只在<b>时长语所在的括号内</b>时才算数。</li>
     * </ol>
     * 剩下的分档：在括号内＝规律 2（不是效果，发动被无效仍适用）；命中
     * {@link #TURN_PLEDGE_MARKS}＝规律 1（不是效果，但发动被无效即不适用）；其余＝规律 3/4
     *（这个回合/发动后/发动过的回合都是卡的效果，两型无效都不适用）。
     */
    private static int pledgeGrade(String sentence) {
        if (TextUtils.isEmpty(sentence)) return -1;
        String hay = lowerForSearch(sentence);
        boolean hasMark = false;
        boolean commaLedMark = false;
        boolean allAttributive = true;
        int markOpen = -1, markClose = -1;
        for (String[] table : new String[][]{TURN_DURATION_MARKS, DUEL_DURATION_MARKS}) {
            for (String m : table) {
                int from = 0;
                int i;
                while ((i = hay.indexOf(m, from)) >= 0) {
                    hasMark = true;
                    if (followedByComma(hay, i + m.length())) commaLedMark = true;
                    else if (!isAttributiveUse(hay, i + m.length())) allAttributive = false;
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
        }
        if (!hasMark || (allAttributive && !commaLedMark)) return -1;
        if (!commaLedMark && !containsAny(hay, SUMMON_APPLY_WORDS)) {
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

    /** 时长语出现处之后（同样跳过空白与格助词）是否为「被」：是则该时长语在作定语挑选对象 */
    private static boolean isAttributiveUse(String s, int after) {
        int k = after;
        while (k < s.length() && SKIP_AFTER_MARK.indexOf(s.charAt(k)) >= 0) k++;
        return k < s.length() && ATTRIBUTIVE_FOLLOW.indexOf(s.charAt(k)) >= 0;
    }

    /**
     * 该句是否因「前置发动来源条件与本次发动区域不符」而不适用（用户新规格：列王诗篇、灵王的波动、
     * 霆王的闪光、命王的螺旋类「这张卡从手卡发动的场合，发动后，…」，与无限泡影类「盖放的这张卡发动的
     * 场合，再在这个回合…」）：句里写的条件就是后半句持续适用的前提，前提不成立时该句不得入列。
     * <p>{@code activateLoc} 为 0（未知来源，如引擎 descHints）时一律不判，保持现有显示。
     */
    private static boolean blockedByActivateZone(String sentence, int activateLoc) {
        if (activateLoc <= 0 || TextUtils.isEmpty(sentence)) return false;
        String low = sentence.toLowerCase(Locale.ROOT);
        if (containsAny(low, FROM_HAND_ACTIVATE_MARKS)) return activateLoc != LOC_HAND;
        if (containsAny(low, FROM_SET_ACTIVATE_MARKS)) return activateLoc != LOC_SZONE;
        return false;
    }

    /**
     * 从整段效果文本里只抽出「正在适用中且未被无效摘掉」的那句（用户新规格：列表不展示完整序号文本，
     * 只要「这个回合，xxx」/「这个效果发动过的回合，xxx」/「这个效果的发动后，xxx」这类子句，
     * 并排除「这张卡发动的回合的…阶段才能发动」这类发动时机句）：
     * 按句终止符（。．！？!?；; 与换行）切句，保留 {@link #pledgeGrade} 命中且档位不高于
     * {@code maxGrade} 的句子并以空格拼接；逗号/顿号不断句（「这个回合，」与后半句必须同现）。
     * 命中句里的时长语全在括号内时，只截括号内那一句（{@link #clipToBracket}）。
     * 无命中时返回空串（被无效到不剩一句时，整行不列）；调用方传给 {@code maxGrade} 的上限已按
     * 两型无效与场上无效门控（{@link #fieldNegated}）算好。
     */
    public static String durationClause(String text, int maxGrade) {
        return durationClause(text, maxGrade, 0);
    }

    /**
     * 同上，并带上本次发动所在区域 {@code activateLoc}（0＝未知）：句里写了「从手卡/从盖放发动的场合」
     * 类前置条件而区域不符时，该句不适用（{@link #blockedByActivateZone}）。
     */
    public static String durationClause(String text, int maxGrade, int activateLoc) {
        if (TextUtils.isEmpty(text)) return "";
        StringBuilder sb = new StringBuilder();
        for (String sentence : applyingSentences(text)) {
            int grade = pledgeGrade(sentence);
            if (grade < 0 || grade > maxGrade) continue;
            if (blockedByActivateZone(sentence, activateLoc)) continue;
            String trimmed = clipToBracket(sentence).trim();
            if (trimmed.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(trimmed);
        }
        return sb.toString();
    }

    /**
     * 场上无效门控（用户规律 3/6/7）：那段落效果的归属卡此刻在场上且效果被无效时，附属处理档
     *（{@link #GRADE_APPENDAGE}，即「这个回合」「发动后」「发动过的回合」这类<b>卡的效果</b>）不适用，
     * 故把上限收到 {@link #GRADE_TURN_PLEDGE}（只余括号内与『…发动的回合』两档，它们都不是效果）；
     * 未被无效时原样返回。链上的发动无效/效果无效仍由调用方先按规律 1/2/3 定出基准档位。
     */
    private static int fieldNegated(int baseMaxGrade, int code, int canonical, Set<Integer> negatedOnField) {
        if (negatedOnField == null
                || (!negatedOnField.contains(Integer.valueOf(code))
                    && !negatedOnField.contains(Integer.valueOf(canonical)))) {
            return baseMaxGrade;
        }
        return Math.min(baseMaxGrade, GRADE_TURN_PLEDGE);
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
     * <p>本方法只用于兜底，段落序能解出句子时一律不走；同样按 {@code activateLoc} 筛掉前置条件不符的句。
     */
    private static String uniqueCardClause(String text, int maxGrade, int activateLoc) {
        String first = null;
        for (String sentence : applyingSentences(text)) {
            int grade = pledgeGrade(sentence);
            if (grade < 0 || grade > maxGrade) continue;
            if (blockedByActivateZone(sentence, activateLoc)) continue;
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
     * <p>{@code fieldNegated} 为真时按用户规律 6/7 再挑掉「在场上适用」那一族（携
     * {@link #SUMMON_ONFIELD_MARKS} 的句子，『…召唤成功的回合』『…特殊召唤（成功）过的回合』
     * 『…召唤时适用。○○』），
     * 不含「成功」的『把这张卡特殊召唤的回合』族按规律 5 继续保留。
     */
    private static String summonPledgeClause(String text, List<Applying> sameCodeRows, int canonical,
                                             boolean fieldNegated) {
        if (TextUtils.isEmpty(text)) return "";
        StringBuilder sb = new StringBuilder();
        for (String sentence : applyingSentences(text)) {
            if (pledgeGrade(sentence) < 0) continue;
            if (!containsAny(sentence, SUMMON_PLEDGE_MARKS)) continue;
            if (fieldNegated && containsAny(sentence, SUMMON_ONFIELD_MARKS)) continue;
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
