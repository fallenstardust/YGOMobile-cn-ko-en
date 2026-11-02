package cn.garymb.ygomobile.game.gamefield;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 决斗级「召唤誓约」账本：登记那些<b>不入连锁、不产生 MSG_CHAINING</b> 的召唤事件
 * （通常召唤 / 反转召唤 / 特殊召唤），供顶部 HUD「持续适用中效果」列表把
 * 『把这张卡特殊召唤的回合，○○』『○○召唤成功的回合，○○』这类召唤誓约也列出来
 * （用户规格：玩家在自己不能做什么的时候，要能确认自己受了哪条誓约约束）。
 * <p>与 {@link EffectActivationLedger} 是两本账：那本按「卡码×段落×半场×回合」登记效果发动，
 * 本本只按「卡码×半场×回合」登记召唤成功——召唤没有段落序，誓约句要从整卡描述里抽。
 * <p>登记时序（对齐 ocgcore operations.cpp {@code special_summon_step}）：核心先发
 * MSG_SPSUMMONING（step 5，携卡码与落位），只有召唤真正成功才走到 step 16 发 MSG_SPSUMMONED；
 * 召唤被无效的分支（step 11）直接结束、<b>不会</b>发 SPSUMMONED。故本账本用两段式：
 * <ol>
 * <li>{@link #addPending} 在 *SUMMONING 时挂账（此刻还不知道会不会成功）；</li>
 * <li>{@link #commitPending} 在 *SUMMONED 时把整批 pending 落入正式账本；</li>
 * <li>{@link #cancelPending} 在 pending 卡被 {@code MSG_MOVE} 移回非怪兽区时撤单——这就是
 * 客户端能拿到的「这次召唤没有成功」的确定性信号（无效召唤后卡回手卡/额外/墓地）；</li>
 * <li>{@link #dropPending} 在回合轮转时丢掉仍未落账的挂账（一次召唤的两段消息必在同一回合内闭合，
 * 带到下回合只会变成误提交）。</li>
 * </ol>
 * <p>撤回回退：条目携带登记时的 {@link EffectActivationLedger#eventCount()} 快照（stamp），
 * {@link #dropBeyond} 与服务端 ACK 的保留条数同口径剪除——召唤本身不由 MSG_CHAINING 计数，
 * 但其前后的发动条数是单调推进的，故仍可用同一根轴回退。
 * <p>线程模型：引擎派发线程写（addPending/commitPending/cancelPending/clear/dropBeyond），
 * 主线程 {@link #snapshot()} 读；LOG 监视器串行，快照返回只读副本。
 */
public final class SummonPledgeLedger {

    /** 一条召唤誓约：卡码 + 半场（本地索引 0=我方 1=对方）+ 召唤回合号 + 登记时的发动条数戳 */
    public static final class Entry {
        public final int code;
        public int side;
        public final int turn;
        public final int stamp;

        Entry(int code, int side, int turn, int stamp) {
            this.code = code;
            this.side = side;
            this.turn = turn;
            this.stamp = stamp;
        }
    }

    private static final ArrayList<Entry> LOG = new ArrayList<>();

    /** 已挂账待确认的召唤（*SUMMONING 起、*SUMMONED 或撤单止）；side 为本地半场索引 */
    private static final ArrayList<Entry> PENDING = new ArrayList<>();

    private SummonPledgeLedger() {}

    /** *SUMMONING：挂账。同一 (code, side, turn) 重复挂账（MSG_RETRY 重放派发）不叠加 */
    public static void addPending(int code, int side) {
        if (code <= 0) return;
        synchronized (LOG) {
            int turn = EffectActivationLedger.getCurrentTurn();
            for (int i = 0; i < PENDING.size(); i++) {
                Entry p = PENDING.get(i);
                if (p.code == code && p.side == side && p.turn == turn) return;
            }
            for (int i = 0; i < LOG.size(); i++) {
                Entry e = LOG.get(i);
                if (e.code == code && e.side == side && e.turn == turn) return;
            }
            PENDING.add(new Entry(code, side, turn, EffectActivationLedger.eventCount()));
        }
    }

    /**
     * *SUMMONED（召唤成功，整批）：把 pending 全数落入正式账本并清空 pending
     */
    public static void commitPending() {
        synchronized (LOG) {
            if (PENDING.isEmpty()) return;
            LOG.addAll(PENDING);
            PENDING.clear();
        }
    }

    /**
     * 丢弃尚未提交的挂账（MSG_NEW_TURN）：一次召唤的 SPSUMMONING→SPSUMMONED 必在同一回合内闭合，
     * 回合已轮转却仍未落账的挂账只能是没有发出成功消息的那一次（无效召唤/异常丢包），
     * 不留到下一次 *SUMMONED 才提交，否则会把没成功的召唤当成成功誓约列出来
     */
    public static void dropPending() {
        synchronized (LOG) {
            PENDING.clear();
        }
    }

    /**
     * MSG_MOVE：pending 中的卡被移回非怪兽区（{@code newLoc} 不含 0x04 位）即视为这次召唤没有成功，
     * 撤单。召唤真正成功后卡再离场时 pending 已空，不会误撤。
     */
    public static void cancelPending(int code, int newLoc) {
        if (code <= 0 || (newLoc & 0x04) != 0) return;
        synchronized (LOG) {
            for (int i = PENDING.size() - 1; i >= 0; i--) {
                if (PENDING.get(i).code == code) PENDING.remove(i);
            }
        }
    }

    /** 全量快照（只读副本，按挂账/落账顺序即回合升序） */
    public static List<Entry> snapshot() {
        synchronized (LOG) {
            return Collections.unmodifiableList(new ArrayList<>(LOG));
        }
    }

    /** 新一局开始（MSG_START）/胜负（MSG_WIN）/回放从头重播清零 */
    public static void clear() {
        synchronized (LOG) {
            LOG.clear();
            PENDING.clear();
        }
    }

    /** 视角互换：半场归属随屏幕翻转（与 {@link EffectActivationLedger#swapSides()} 同批次调用） */
    public static void swapSides() {
        synchronized (LOG) {
            for (Entry e : LOG) e.side = 1 - e.side;
            for (Entry p : PENDING) p.side = 1 - p.side;
        }
    }

    /** 撤回回退：剪除戳大于服务端保留发动条数的召唤条目（stamp&lt;0 的旧服务端载荷不调用本方法） */
    public static void dropBeyond(int keptEvents) {
        if (keptEvents < 0) return;
        synchronized (LOG) {
            for (int i = LOG.size() - 1; i >= 0; i--) {
                if (LOG.get(i).stamp > keptEvents) LOG.remove(i);
            }
            PENDING.clear();
        }
    }
}
