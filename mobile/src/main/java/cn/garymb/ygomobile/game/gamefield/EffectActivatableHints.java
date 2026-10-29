package cn.garymb.ygomobile.game.gamefield;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

import ocgcore.DataManager;
import ocgcore.data.Card;

/**
 * 指令级「可发动效果」登记簿：登记当前命令询问下发的各可发动选项 desc
 *（MSG_SELECT_IDLE_CMD / MSG_SELECT_BATTLE_CMD / MSG_SELECT_CHAIN 每条可发动项均携带
 * 效果 uid = code*16+段落文档序）。登记点与呼吸绿点/conti_act 动画同源：
 * CommandDataParser / ChainSelectController 在置 COMMAND_ACTIVATE 标志、入列
 * GameEngine.activatableCards 的同一时刻即登记，玩家点击带绿点的卡即发光，
 * 无需等到命令菜单出现「发动」按钮。
 * <p>与 {@link EffectActivationLedger}（已发动：绿/红勾+置灰，跨回合账本）相对，本登记簿
 * 只描述「此刻可发动」：登记维度为卡码 × 半场（本地索引 0=我方 1=对方，同账本口径），
 * 异画与本卡共用效果脚本，登记时按卡码镜像写入其 alias，查询按 卡码+alias 并集。
 * <p>双方均登记：主阶/战斗指令（MSG_SELECT_IDLE_CMD / MSG_SELECT_BATTLE_CMD）为全桌广播，
 * 对方轮的报文同样经 CommandDataParser 解析入列，其条目落在半场 1；连锁询问按收信方下发，
 * 谁收到谁的条目。卡详据此着色：展示卡自身半场可发动→荧光绿，对方半场可发动→荧光橙。
 * <p>生命周期：随命令询问存续——GameEngine.clearCommandFlags（新 idle/battle 询问解析前、
 * 连锁询问进入前、决斗结束清场、撤回重同步）与 ChainSelectController.clearChainSelect
 *（连锁应答/清理，与绿点消失同步）一并清空。
 * <p>线程模型：引擎派发线程写（命令解析入列）、UI/后台绑定线程读；与账本同款并发安全容器。
 */
public final class EffectActivatableHints {

    private static final ConcurrentHashMap<Integer, Set<Integer>> BY_CODE = new ConcurrentHashMap<>();

    private EffectActivatableHints() {}

    /** 登记 code 卡（半场 side）的一个可发动选项 desc：仅卡码型字符串 id（>MAX_STRING_ID）
     *  有段落序含义，desc==0（无效果文字的选项，如召唤/特召段）与系统字符串 id 忽略；
     *  低 4 位=效果段落文档序下标，选项重复下发以幂等集合去重 */
    public static void register(int code, int side, int descRaw) {
        long uid = descRaw & 0xFFFFFFFFL;
        if (code <= 0 || uid <= DataManager.MAX_STRING_ID) return;
        add(code, side, (int) (uid & 0xf));
        // alias 镜像：异画卡与其的本卡（或反之）共用同一效果段落编号，两码都要能查到
        Card c = DataManager.get().getCardManager().getCard(code);
        if (c != null && c.Alias > 0 && c.Alias != code) {
            add(c.Alias, side, (int) (uid & 0xf));
        }
    }

    private static void add(int code, int side, int offset) {
        int key = code * 2 + side;
        Set<Integer> s = BY_CODE.get(key);
        if (s == null) {
            s = new CopyOnWriteArraySet<>();
            Set<Integer> prev = BY_CODE.putIfAbsent(key, s);
            if (prev != null) s = prev;
        }
        s.add(offset);
    }

    /** 本卡码（含 alias 并集）在指定半场当前可发动的效果段落文档序集合；
     *  side=-1（纯卡表展示无归属）不发光，返回空 */
    public static List<Integer> offsetsFor(int code, int alias, int side) {
        if (code <= 0 || side < 0) return Collections.emptyList();
        List<Integer> out = new ArrayList<>();
        collect(code, side, out);
        if (alias > 0 && alias != code) collect(alias, side, out);
        return out;
    }

    private static void collect(int code, int side, List<Integer> out) {
        Set<Integer> s = BY_CODE.get(code * 2 + side);
        if (s == null) return;
        for (Integer o : s) {
            if (!out.contains(o)) out.add(o);
        }
    }

    /** 新命令询问到达 / 连锁清理 / 决斗结束时的整体清空 */
    public static void clear() {
        BY_CODE.clear();
    }

    /** 视角互换（纯单人逐回合自动翻转/观战切视角/回放交换）：条目键按奇偶编码半场
     *  （code*2+side），与 {@link EffectActivationLedger#swapSides()} 同批次奇偶对调，
     *  否则 swapField 已翻转场卡 controler 而登记半场过期——自己方可发动条目被记到对面，
     *  点自己的卡查不到、点对方卡反而命中（发光颜色绿橙互换） */
    public static void swapSides() {
        for (java.util.Map.Entry<Integer, Set<Integer>> e : new ArrayList<>(BY_CODE.entrySet())) {
            int key = e.getKey();
            Set<Integer> offsets = e.getValue();
            if (offsets == null || offsets.isEmpty()) continue;
            int flipped = key ^ 1;
            BY_CODE.remove(key);
            Set<Integer> dst = BY_CODE.get(flipped);
            if (dst == null) {
                dst = new CopyOnWriteArraySet<>();
                Set<Integer> prev = BY_CODE.putIfAbsent(flipped, dst);
                if (prev != null) dst = prev;
            }
            dst.addAll(offsets);
        }
    }
}
