package cn.garymb.ygomobile.ui.cards.deck;

import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.bean.DeckInfo;

/**
 * 卡组编辑的撤销 / 重做历史（进程内单例，供卡组编辑页 DeckManagerFragment 与
 * 决斗内卡组编辑器 DeckEditorManager 共用）。
 * <p>
 * 快照列表按“时间轴 + 游标”维护：新快照落在游标位置时先丢弃游标之后的记录（与浏览器
 * 前进/后退一致），与游标处内容相同的快照直接跳过，因此连续无变化的操作与 undo 后的刷新
 * 都不会撑大历史、也不会吃掉重做分支。
 * <p>
 * 归属边界：历史以“卡组标识”（当前 ydk 文件绝对路径）为单位。编辑器切到另一个卡组文件时
 * 必须先调 {@link #bindDeck(String)}，key 变化即整段清零——避免把 A 卡组的快照 undo 进
 * B 卡组造成串档。同一份卡组被两个编辑器先后打开时，key 相同，历史因此是真正互通的。
 * <p>
 * 只做内存回退：undo / redo 仅改写卡组数据对象并通知界面刷新，不写 ydk 文件，
 * 由用户另行“保存”落盘。仅在 UI 线程调用。
 */
public final class DeckHistory {

    /**
     * 历史变化通知（新增快照、撤销、重做、清零都会触发）
     */
    public interface Listener {
        void onDeckHistoryChanged();
    }

    private static final DeckHistory INSTANCE = new DeckHistory();

    /**
     * 共享单例：两个编辑器引用同一个快照列表
     */
    public static DeckHistory get() {
        return INSTANCE;
    }

    private final List<DeckInfo> snapshots = new ArrayList<>();
    private final List<Listener> listeners = new ArrayList<>();
    private int index = -1;
    /** 原点下标：与已落盘内容一致的那条快照，用于判定“是否真的改过卡组” */
    private int originIndex = -1;
    private String deckKey = "";

    private DeckHistory() {
    }

    public void addListener(Listener listener) {
        if (listener != null && !listeners.contains(listener)) listeners.add(listener);
    }

    public void removeListener(Listener listener) {
        listeners.remove(listener);
    }

    /**
     * 声明当前编辑的卡组归属。key 与上一次不同（含从空变非空）即整段清空历史。
     *
     * @param key 卡组标识，通常传 ydk 文件绝对路径；无文件时传空串
     * @return 是否发生了清零（key 变化）
     */
    public boolean bindDeck(String key) {
        String next = key == null ? "" : key;
        if (next.equals(deckKey)) return false;
        deckKey = next;
        clear();
        return true;
    }

    public String getDeckKey() {
        return deckKey;
    }

    /**
     * 记录一次卡组快照：与游标处内容相同则直接跳过（刷新回调常常重复推同一份状态），
     * 否则先丢弃游标之后的旧记录再追加。
     * <p>
     * 去重先于截尾判断，是为了保护 redo 分支：undo 应用旧状态后通常会紧跟一次界面刷新，
     * 若先截尾再记录，刚 undo 掉的那一步会被当成新历史，重做链当场失效。
     */
    public void push(DeckInfo state) {
        if (state == null) return;
        DeckInfo copy = snapshot(state);
        if (index >= 0 && index < snapshots.size()
                && snapshots.get(index).toLongString().equals(copy.toLongString())) {
            return;
        }
        if (index < snapshots.size() - 1) {
            snapshots.subList(index + 1, snapshots.size()).clear();
        }
        snapshots.add(copy);
        index = snapshots.size() - 1;
        notifyChanged();
    }

    public void clear() {
        if (snapshots.isEmpty() && index == -1) return;
        snapshots.clear();
        index = -1;
        originIndex = -1;
        notifyChanged();
    }

    /**
     * 把游标所在位置标为“与磁盘一致”的原点：卡组载入完成或保存成功后调用，
     * 之后的 undo/redo 靠 {@link #isAtOrigin()} 回推 isModified。
     */
    public void markOrigin() {
        originIndex = index;
    }

    /** 当前是否处于原点（内容与已保存/刚载入的卡组一致） */
    public boolean isAtOrigin() {
        return index == originIndex;
    }

    public boolean canUndo() {
        return index > 0;
    }

    public boolean canRedo() {
        return index < snapshots.size() - 1;
    }

    /** 历史长度（判断“是否存在可回退的第二份状态”用） */
    public int size() {
        return snapshots.size();
    }

    /**
     * 回退一步
     *
     * @return 需要应用的上一个状态，无可回退时返回 null（调用方不刷新即可）
     */
    public DeckInfo undo() {
        if (!canUndo()) return null;
        index--;
        notifyChanged();
        return snapshot(snapshots.get(index));
    }

    /**
     * 前进一步
     *
     * @return 需要应用的下一个状态，无可前进时返回 null
     */
    public DeckInfo redo() {
        if (!canRedo()) return null;
        index++;
        notifyChanged();
        return snapshot(snapshots.get(index));
    }

    /**
     * 生成可在历史里独立保存的副本：三个区的列表新建（Card 实例复用，卡组编辑不涉及
     * 单卡属性改动），并带上文件与云端标识，undo 应用后仍能存回原 ydk / 原 deckId。
     */
    public static DeckInfo snapshot(DeckInfo source) {
        DeckInfo copy = new DeckInfo();
        if (source != null) {
            copy.setMainCards(new ArrayList<>(source.mainCards));
            copy.setExtraCards(new ArrayList<>(source.extraCards));
            copy.setSideCards(new ArrayList<>(source.sideCards));
            copy.source = source.source;
            copy.deckId = source.deckId;
            copy.userId = source.userId;
        }
        copy.syncCounts();
        return copy;
    }

    /**
     * 把历史快照应用到目标卡组对象上（就地改写列表，保持调用方持有的引用不变）。
     */
    public static void apply(DeckInfo target, DeckInfo state) {
        if (target == null || state == null) return;
        target.setMainCards(new ArrayList<>(state.mainCards));
        target.setExtraCards(new ArrayList<>(state.extraCards));
        target.setSideCards(new ArrayList<>(state.sideCards));
        target.source = state.source;
        target.deckId = state.deckId;
        target.userId = state.userId;
        target.syncCounts();
    }

    private void notifyChanged() {
        //回调期间可能增删监听，遍历副本
        for (Listener listener : new ArrayList<>(listeners)) {
            listener.onDeckHistoryChanged();
        }
    }
}
