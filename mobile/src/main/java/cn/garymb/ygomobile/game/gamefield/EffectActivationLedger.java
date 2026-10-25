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
 * ConcurrentHashMap + CopyOnWrite 集合 + volatile 回合号，无需外部同步。
 */
public final class EffectActivationLedger {

    /** 单条发动记录：效果段落锚点 offset + 发动半场（本地索引 0=我方 1=对方）+ 发动回合号 */
    public static final class Activation {
        public final int offset;
        public final int side;
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

    /** 当前回合号（MSG_NEW_TURN 的 field.turnCount 快照；MSG_START/撤回重同步一并校正） */
    private static volatile int currentTurn = 0;

    private EffectActivationLedger() {}

    /** 登记 code 卡（半场 side、回合 turn）发动了 descRaw 指向的效果：仅卡码型字符串 id
     *（>MAX_STRING_ID）有段落序含义，desc==0（脚本未 SetDescription）与系统字符串 id 忽略 */
    public static void record(int code, int descRaw, int side, int turn) {
        long uid = descRaw & 0xFFFFFFFFL;
        if (code <= 0 || uid <= DataManager.MAX_STRING_ID) return;
        addCode(code, new Activation((int) (uid & 0xf), side, turn));
        // alias 镜像：发动的异画卡与其的本卡（或反之）共用同一效果段落编号，两码都要能查到
        Card c = DataManager.get().getCardManager().getCard(code);
        if (c != null && c.Alias > 0 && c.Alias != code) {
            addCode(c.Alias, new Activation((int) (uid & 0xf), side, turn));
        }
    }

    private static void addCode(int code, Activation act) {
        Set<Activation> s = BY_CODE.get(code);
        if (s == null) {
            s = new CopyOnWriteArraySet<>();
            Set<Activation> prev = BY_CODE.putIfAbsent(code, s);
            if (prev != null) s = prev;
        }
        s.add(act);
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
    }

    /** 视角互换（观战/单人自动翻转/回放「切换视角」，用户规格3）：side 语义是“屏幕第几侧
     *  （0=我方半场）”，切视角后同一张物理卡换到对面半场，发动记录的归属必须随屏翻转，
     *  否则正在展示卡的绿/红勾会与半场事实相反。与 duelIsFirst 翻转/field.swapField 同批次
     *  调用（DuelViewpointManager.performSpectatorSwap 与 ReplayPlayer.performSwapField） */
    public static void swapSides() {
        for (java.util.Map.Entry<Integer, Set<Activation>> e : BY_CODE.entrySet()) {
            Set<Activation> flipped = new CopyOnWriteArraySet<>();
            for (Activation a : e.getValue()) {
                flipped.add(new Activation(a.offset, 1 - a.side, a.turn));
            }
            e.setValue(flipped);
        }
    }
}
