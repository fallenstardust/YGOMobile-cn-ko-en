package cn.garymb.ygomobile.game.gamefield;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import ocgcore.DataManager;
import ocgcore.data.Card;

/**
 * 决斗级「已发动效果」账本：按（卡码 × 半场 × 回合）三维登记效果发动记录，数据源为
 * MSG_CHAINING 的 desc（processor.cpp L4159-4166 每次效果发动必写 peffect->description
 * =code*16+offset 与 triggering_controler）。
 * <p>按卡码而非 ClientCard 对象登记的原因：墓地/卡组/额外等「列表 → 点卡看详情」场景
 * 会以卡码重建临时 ClientCard（CardSelectController.showCardInfoFromItem），逐对象记录
 * 必然丢失；异画卡与的本卡共用效果脚本，登记时按卡码镜像写入其 alias，查询按 卡码+alias 并集。
 * <p>半场维度（用户规格）：我方发动绿勾、对手发动红勾，同名卡双方记录严格分离。
 * 引擎不下发「1回合1次/决斗中1次」限制类别（MSG_CHAINING 仅 code/loc/desc，
 * CHINT_DESC_ADD 也只带 hint 文本），故账本只附带动作发生时的回合号（MSG_NEW_TURN
 * 维护的 field.turnCount）：普通记录只在发动当回合可见（回合轮转即「过期」），
 * 「决斗中只能使用1次」类限制由 CardInfoBinder 渲染时按 cdb 描述文本条款判定，
 * 命中则跨回合持续标记，直到胜负（MSG_WIN）/新一局（MSG_START）整体清空。
 * <p>线程模型：引擎派发线程写（record/setCurrentTurn）、UI/后台绑定线程读；
 * ConcurrentHashMap + CopyOnWrite 集合 + volatile 回合号，无需外部同步；
 * 撤回回退（dropBeyond）沿追加日志剪除后缀条目，与 record 共用 LOG 监视器串行。
 */
public final class EffectActivationLedger {

    /** 单条发动记录：效果段落锚点 offset + 发动半场（本地索引 0=我方 1=对方）+ 发动回合号。
     *  side 非 final：视角互换就地翻转（保持对象身份，使 {@link #LOG} 里的引用仍可精确剪除） */
    public static final class Activation {
        public final int offset;
        public int side;
        public final int turn;

        Activation(int offset, int side, int turn) {
            this.offset = offset;
            this.side = side;
            this.turn = turn;
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
    private static final java.util.ArrayList<LoggedAdds> LOG = new java.util.ArrayList<>();

    /** 当前回合号（MSG_NEW_TURN 的 field.turnCount 快照；MSG_START/撤回重同步一并校正） */
    private static volatile int currentTurn = 0;

    private EffectActivationLedger() {}

    /** 登记 code 卡（半场 side、回合 turn）发动了 descRaw 指向的效果：仅卡码型字符串 id
     *（>MAX_STRING_ID）有段落序含义，desc==0（脚本未 SetDescription）与系统字符串 id 忽略 */
    public static void record(int code, int descRaw, int side, int turn) {
        long uid = descRaw & 0xFFFFFFFFL;
        if (code <= 0 || uid <= DataManager.MAX_STRING_ID) return;
        int offset = (int) (uid & 0xf);
        // alias 镜像：发动的异画卡与其的本卡（或反之）共用同一效果段落编号，两码都要能查到
        Card c = DataManager.get().getCardManager().getCard(code);
        boolean mirror = c != null && c.Alias > 0 && c.Alias != code;
        synchronized (LOG) {
            int n = mirror ? 2 : 1;
            int[] codes = new int[n];
            Activation[] acts = new Activation[n];
            codes[0] = code;
            acts[0] = new Activation(offset, side, turn);
            addCode(code, acts[0]);
            if (mirror) {
                codes[1] = c.Alias;
                acts[1] = new Activation(offset, side, turn);
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
