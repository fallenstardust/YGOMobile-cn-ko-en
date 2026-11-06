package cn.garymb.ygomobile.game.gamefield;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import ocgcore.DataManager;
import ocgcore.data.Card;

/**
 * 决斗级「已发动效果」账本：按（卡码 × 半场 × 回合）三维登记效果发动记录，数据源为
 * MSG_CHAINING 的 desc（processor.cpp L4159-4166 每次效果发动必写 peffect->description
 * =code*16+offset 与 triggering_controler），另存一条 triggering_location 供「从手卡/从盖放发动的场合」
 * 类前置条件判定（本端把它按 卡码×段落×半场×回合 归一，区域只随首条记录走）。
 * <p>按卡码而非 ClientCard 对象登记的原因：墓地/卡组/额外等「列表 → 点卡看详情」场景
 * 会以卡码重建临时 ClientCard（CardSelectController.showCardInfoFromItem），逐对象记录
 * 必然丢失；异画卡与的本卡共用效果脚本，登记时按卡码镜像写入其 alias，查询按 卡码+alias 并集。
 * <p>半场维度（用户规格）：我方发动绿勾、对手发动红勾，同名卡双方记录严格分离。
 * 引擎不下发「1回合1次/决斗中1次」限制类别（MSG_CHAINING 仅 code/loc/desc，
 * CHINT_DESC_ADD 也只带 hint 文本），故账本只附带动作发生时的回合号（MSG_NEW_TURN
 * 维护的 field.turnCount）：普通记录只在发动当回合可见（回合轮转即「过期」），
 * 「决斗中只能使用1次」类限制由 CardInfoBinder 渲染时按 cdb 描述文本条款判定，
 * 命中则跨回合持续标记，直到胜负（MSG_WIN）/新一局（MSG_START）整体清空。
 * <p>链结算结果（用户新规格）：MSG_CHAIN_NEGATED / MSG_CHAIN_DISABLED 经 {@link #markPledgeNegated}
 * 回到对应条目上，供「持续适用中效果」按用户归纳的记述规律区分两型无效收紧显示（发动被无效只留下
 * 括号内『（这个回合，○○）』；效果被无效另可留下『…发动的回合，○○』；两类都不是效果，
 * 而『这个回合/发动后/发动过的回合』是卡的效果，任一型无效都不适用）。
 * <p>线程模型：引擎派发线程写（record/setCurrentTurn）、UI/后台绑定线程读；
 * ConcurrentHashMap + CopyOnWrite 集合 + volatile 回合号，无需外部同步；
 * 撤回回退（dropBeyond）沿追加日志剪除后缀条目，与 record 共用 LOG 监视器串行。
 */
public final class EffectActivationLedger {

    /** 一条发动记录：效果段落锚点 offset + 发动半场（本地索引 0=我方 1=对方）+ 发动回合号
     * + 发动时所在区域（MSG_CHAINING 的 triggering_location，0=未知）。
     *  side 非 final：视角互换就地翻转（保持对象身份，使 {@link #LOG} 里的引用仍可精确剪除）。
     *  negated/disabled 也非 final 且不进 equals/hashCode：它们是链的结算结果（状态）而不是
     *  发动本身的身份，同键的另一条已存在记录仍应归一 */
    public static final class Activation {
        public final int offset;
        public int side;
        public final int turn;
        /** 发动时卡所在区域（ocgcore  LOCATION：手卡 0x02 / 魔陷区 0x08 …）：「这张卡从手卡发动的场合」
         *  「盖放的这张卡发动的场合」这类写在适用句同句里的前置条件，只能靠它判成立与否 */
        public final int location;
        /** 本次发动的链被无效（MSG_CHAIN_NEGATED，即<b>发动无效</b>）：『…发动的回合，○○』与
         *  『这个回合/发动后/发动过的回合』类都不再适用，仅括号内『（这个回合，○○）』仍适用 */
        public boolean negated;
        /** 本次发动成功但效果被无效（MSG_CHAIN_DISABLED，即<b>效果无效</b>）：不是效果的『（这个回合，○○）』
         *  与『…发动的回合，○○』仍适用，只有属卡的效果的『这个回合/发动后/发动过的回合』类附加处理不适用 */
        public boolean disabled;
        /** 本次发动已走到「适用与否已成定局」的时点（用户新规格：持续适用列表只收已适用的条目，
         *  不是一挂上连锁就有）：MSG_CHAIN_SOLVED（效果处理完成）或 MSG_CHAIN_NEGATED /
         *  MSG_CHAIN_DISABLED（链已结算完毕，括号内与『发动的回合』档按规律仍可能适用）时置位；
         *  状态不入 equals/hashCode（同 negated/disabled：它们是结算结果而非发动身份） */
        public boolean applied;

        Activation(int offset, int side, int turn, int location) {
            this.offset = offset;
            this.side = side;
            this.turn = turn;
            this.location = location;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Activation)) return false;
            Activation a = (Activation) o;
            return a.offset == offset && a.side == side && a.turn == turn;
        }

        @Override
        public int hashCode() {
            return (offset * 31 + side) * 31 + turn;
        }
    }

    private static final ConcurrentHashMap<Integer, Set<Activation>> BY_CODE = new ConcurrentHashMap<>();

    /** 追加日志：每次 {@link #record} 调用一条（含本码与 alias 镜像两个集合的实际新增项），
     *  与服务端静默重放统计的「有效效果发动」条数同序对齐；撤回时按服务端告知的保留条数
     *  从尾部剪除被回退段登记的条目（用户新规格：区分是否本次撤回导致效果回退为未使用） */
    private static final class LoggedAdds {
        final int[] codes;
        final Activation[] acts;
        LoggedAdds(int[] codes, Activation[] acts) { this.codes = codes; this.acts = acts; }
    }
    private static final ArrayList<LoggedAdds> LOG = new ArrayList<>();

    /** 当前回合号（MSG_NEW_TURN 的 field.turnCount 快照；MSG_START/撤回重同步一并校正） */
    private static volatile int currentTurn = 0;

    private EffectActivationLedger() {}

    /** 登记 code 卡（半场 side、回合 turn、发动区域 location）发动了 descRaw 指向的效果：仅卡码型字符串 id
     *（>MAX_STRING_ID）有段落序含义，desc==0（脚本未 SetDescription）与系统字符串 id 忽略。
     * <p>幂等去重（评审 Minor①）：同 (code, offset, side, turn) 键已存在任何日志行时直接返回，
     * 不重复成行——重放/自愈重复投递同一条 MSG_CHAINING 不再把 LOG 推得比服务端
     *  silentChainEvents 大（dropBeyond 误清保留前缀方向）；合法的同键重复发动本只需一条标记，
     *  存在性语义与 kept 前缀判定仍一致（该键被保留 ⇒ 标记留，被全撤 ⇒ kept 小于唯一行序 ⇒ 清），
     *  与服务端每链计数口径的差值只会落在「清不少」的安全侧 */
    public static void record(int code, int descRaw, int side, int turn, int location) {
        long uid = descRaw & 0xFFFFFFFFL;
        if (code <= 0 || uid <= DataManager.MAX_STRING_ID) return;
        int offset = (int) (uid & 0xf);
        synchronized (LOG) {
            // 主码行四字段全等即视为同一笔发动（side 为当前视角屏幕侧，swapSides 后两侧
            // 同步翻转，比较口径自洽）
            for (LoggedAdds row : LOG) {
                Activation a0 = row.acts[0];
                if (row.codes[0] == code && a0.offset == offset && a0.side == side && a0.turn == turn) {
                    return;
                }
            }
        }
        // alias 镜像：发动的异画卡与其的本卡（或反之）共用同一效果段落编号，两码都要能查到
        Card c = DataManager.get().getCardManager().getCard(code);
        boolean mirror = c != null && c.Alias > 0 && c.Alias != code;
        synchronized (LOG) {
            int n = mirror ? 2 : 1;
            int[] codes = new int[n];
            Activation[] acts = new Activation[n];
            codes[0] = code;
            acts[0] = new Activation(offset, side, turn, location);
            addCode(code, acts[0]);
            if (mirror) {
                codes[1] = c.Alias;
                acts[1] = new Activation(offset, side, turn, location);
                addCode(c.Alias, acts[1]);
            }
            // 每条 record() 调用占一行日志（即使等值去重未新增也记录，与发动事件计数同长），
            // 剪除时只移除集合中确实持有的本对象（按对象身份扫描）
            LOG.add(new LoggedAdds(codes, acts));
        }
    }

    private static boolean addCode(int code, Activation act) {
        Set<Activation> s = BY_CODE.get(code);
        if (s == null) {
            s = new CopyOnWriteArraySet<>();
            Set<Activation> prev = BY_CODE.putIfAbsent(code, s);
            if (prev != null) s = prev;
        }
        return s.add(act);
    }

    /** 本卡码（含 alias 并集）的发动记录；side 传 0/1 只取该半场，传 -1 取双方（无归属信息的
     *  纯卡表展示，如关键词查卡/决斗记录，渲染方按半场分别出绿/红勾）；code/alias 命中同一
     *  Activation 对象（alias 镜像为等值记录），以对象身份去重即可 */
    public static List<Activation> activationsFor(int code, int alias, int side) {
        List<Activation> out = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        collect(BY_CODE.get(code), side, out, seen);
        if (alias > 0 && alias != code) collect(BY_CODE.get(alias), side, out, seen);
        return out;
    }

    private static void collect(Set<Activation> s, int side, List<Activation> out, Set<Integer> seen) {
        if (s == null) return;
        for (Activation a : s) {
            if (side >= 0 && a.side != side) continue;
            if (seen.add(System.identityHashCode(a))) out.add(a);
        }
    }

    /** 当前回合号（渲染时判「本回合发动」与否的基准） */
    public static int getCurrentTurn() {
        return currentTurn;
    }

    /**
     * 把链的结算结果回到发动条目上（用户新规格：被无效的效果，其誓约部分不应继续显示为适用中）：
     * {@code disabled=false} 为 MSG_CHAIN_NEGATED（发动被无效），{@code true} 为 MSG_CHAIN_DISABLED
     *（发动成功但效果被无效）。按 卡码×段落×半场×回合 命中，异画镜像的等值条目一并标上；
     * 只改状态不新建对象，{@link #dropBeyond} 按对象身份剪除的机制不受影响。
     * <p>被无效的链也已「结算完毕」：括号内『（这个回合，○○）』档发动被无效仍适用，
     * 『…发动的回合，○○』档效果被无效仍适用，所以本方法同时置 {@code applied}
     *（否则被无效后反而永不入列）；具体留哪几档由 {@code ContinuousEffectApplier} 按两型无效分别筛。
     */
    public static void markPledgeNegated(int code, int offset, int side, int turn, boolean disabled) {
        forEachMatch(code, offset, side, turn, new MatchAction() {
            @Override
            public void apply(Activation a) {
                if (disabled) a.disabled = true;
                else a.negated = true;
                a.applied = true;
            }
        });
    }

    /**
     * 链效果处理成功（MSG_CHAIN_SOLVED）：把该笔发动标为「已适用」（用户新规格：持续适用中效果的
     * 条目与金色徽标要从「效果成功适用」那一刻起才出现，而不是刚挂上连锁就亮）。
     */
    public static void markApplied(int code, int offset, int side, int turn) {
        forEachMatch(code, offset, side, turn, new MatchAction() {
            @Override
            public void apply(Activation a) {
                a.applied = true;
            }
        });
    }

    private interface MatchAction {
        void apply(Activation a);
    }

    /** 按 卡码×段落×半场×回合 命中主码与 alias 镜像条目，对其执行同一状态变更 */
    private static void forEachMatch(int code, int offset, int side, int turn, MatchAction action) {
        if (code <= 0) return;
        Card c = DataManager.get().getCardManager().getCard(code);
        int alias = c != null ? c.Alias : 0;
        for (int key = 0; key < 2; key++) {
            int k = key == 0 ? code : alias;
            if (k <= 0 || k == code && key == 1) continue;
            Set<Activation> s = BY_CODE.get(k);
            if (s == null) continue;
            for (Activation a : s) {
                if (a.offset == offset && a.side == side && a.turn == turn) action.apply(a);
            }
        }
    }

    /** 账本全量条目（卡码 + 该条发动记录），供「持续适用中效果」按半场整体枚举 */
    public static final class Entry {
        public final int code;
        public final Activation activation;

        Entry(int code, Activation activation) {
            this.code = code;
            this.activation = activation;
        }
    }

    /** 快照全部登记条目（含 alias 镜像行）：BY_CODE 为并发容器，弱一致遍历不抛
     *  ConcurrentModificationException；调用方自行按卡名/段落去重与回合门控 */
    public static List<Entry> snapshot() {
        List<Entry> out = new ArrayList<>();
        for (Map.Entry<Integer, Set<Activation>> e : BY_CODE.entrySet()) {
            Set<Activation> s = e.getValue();
            if (s == null) continue;
            for (Activation a : s) {
                out.add(new Entry(e.getKey(), a));
            }
        }
        return out;
    }

    /** 已登记的「有效效果发动」条数（追加日志长度，与服务端静默重放的 silentChainEvents 同口径）：
     *  场地持续态标注（HINT_ZONE 变色格/无效徽记、MSG_FIELD_DISABLED 白 X）写入时取此快照，
     *  撤回后条数大于服务端保留条数的标注即属于被抹掉的那次发动 */
    public static int eventCount() {
        synchronized (LOG) {
            return LOG.size();
        }
    }

    /** 回合号同步：MSG_START 置 0、MSG_NEW_TURN 置 field.turnCount、撤回重同步置重同步落点 */
    public static void setCurrentTurn(int turn) {
        currentTurn = turn;
    }

    /** 新一局开始 / 回放从头重播（MSG_START）与胜负（MSG_WIN）清零 */
    public static void clear() {
        BY_CODE.clear();
        synchronized (LOG) {
            LOG.clear();
        }
    }

    /** 撤回回退（用户新规格）：服务端静默重放按开局 seed 重算出回退后局面里的
     *  「有效效果发动」条数（同 record 判据：code>0 且 desc>MAX_STRING_ID），随 STOC_UNDO_ACK
     *  下发；账本把超出该条数的尾部条目剪除——被本次撤回抹掉的发动恢复未使用态（常规文本），
     *  保留前缀内的条目继续显示绿/红勾+置灰。条数大于现有日志（观战中途加入等旧服务端短
     *  载荷为 -1 时不经本方法）时自然空转，不会错删。 */
    public static void dropBeyond(int keptEvents) {
        synchronized (LOG) {
            while (LOG.size() > keptEvents && !LOG.isEmpty()) {
                LoggedAdds e = LOG.remove(LOG.size() - 1);
                for (int i = 0; i < e.codes.length; i++) {
                    Set<Activation> s = BY_CODE.get(e.codes[i]);
                    if (!(s instanceof CopyOnWriteArraySet)) continue;
                    final Activation victim = e.acts[i];
                    // 按对象身份移除：同 卡码×半场×回合×offset 的等值但不同实例条目（更早的
                    // 同笔发动）不得被误删——victim 未被集合持有（等值去重）时本行空转
                    CopyOnWriteArraySet<Activation> cs = (CopyOnWriteArraySet<Activation>) s;
                    for (Activation a : cs) {
                        if (a == victim) {
                            cs.remove(a);
                            break;
                        }
                    }
                    if (cs.isEmpty()) BY_CODE.remove(e.codes[i], cs);
                }
            }
        }
    }

    /** 视角互换（观战/单人自动翻转/回放「切换视角」，用户规格3）：side 语义是“屏幕第几侧
     *  （0=我方半场）”，切视角后同一张物理卡换到对面半场，发动记录的归属必须随屏翻转，
     *  否则正在展示卡的绿/红勾会与半场事实相反。与 duelIsFirst 翻转/field.swapField 同批次
     *  调用（DuelViewpointManager.performSpectatorSwap 与 ReplayPlayer.performSwapField） */
    public static void swapSides() {
        // 就地翻转存储对象的 side（不新建实例）：追加日志里的引用仍指向同一对象，
        // 后续撤回 dropBeyond 仍可按对象身份精确剪除
        for (Set<Activation> s : BY_CODE.values()) {
            for (Activation a : s) {
                a.side = 1 - a.side;
            }
        }
    }
}
