package cn.garymb.ygomobile.ui.cards.deck;

import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.bean.DeckInfo;
import cn.garymb.ygomobile.utils.DeckUtil;

/**
 * 卡组编辑的撤销 / 重做历史，同时也是两个编辑器的**实时同步总线**（进程内单例，
 * 供卡组编辑页 DeckManagerFragment 与决斗内卡组编辑器 DeckEditorManager 共用）。
 * <p>
 * 快照列表按“时间轴 + 游标”维护：新快照落在游标位置时先丢弃游标之后的记录（与浏览器
 * 前进/后退一致），与游标处内容相同的快照直接跳过，因此连续无变化的操作与 undo 后的刷新
 * 都不会撑大历史、也不会吃掉重做分支。
 * <p>
 * 归属边界：历史以“卡组标识”（当前 ydk 文件绝对路径）为单位。编辑器切到另一个卡组文件时
 * 必须先调 {@link #bindDeck(String, int)}，key 变化即整段清零——避免把 A 卡组的快照 undo 进
 * B 卡组造成串档。同一份卡组被两个编辑器先后打开时，key 相同，历史因此是真正互通的。
 * <p>
 * 跨屏联动就建立在这三点上：{@link #bindDeck} 换户广播 = “另一个也换成同一份卡组”，
 * {@link #push} 写入广播 = “另一个马上读到这步修改”，{@link #current()} = “没打开的那一侧
 * 下次打开时直接加载到最后修改状态”。写入方标识 {@link #PUBLISHER_DECK_PAGE} /
 * {@link #PUBLISHER_DECK_EDITOR} 让接收方过滤自己的回声，配合内容等值比较保证不循环。
 * <p>
 * 只做内存回退：undo / redo 仅改写卡组数据对象并通知界面刷新，不写 ydk 文件，
 * 由用户另行“保存”落盘。仅在 UI 线程调用。
 */
public final class DeckHistory {

    /** 写入方：未知 / 非两个编辑器发起（如外部导入） */
    public static final int PUBLISHER_NONE = 0;
    /** 写入方：卡组编辑页 DeckManagerFragment */
    public static final int PUBLISHER_DECK_PAGE = 1;
    /** 写入方：决斗内卡组编辑器 DeckEditorManager */
    public static final int PUBLISHER_DECK_EDITOR = 2;

    /**
     * 历史变化通知（新增快照、撤销、重做、清零都会触发）
     */
    public interface Listener {
        void onDeckHistoryChanged();

        /**
         * 另一编辑器切换了卡组文件（key 变化）。接收方自行处理“自身有未保存修改先弹确认”。
         *
         * @param key       新的卡组标识（ydk 绝对路径）
         * @param publisher {@link #PUBLISHER_DECK_PAGE} 等发起方标识
         */
        default void onDeckKeyChanged(String key, int publisher) {
        }

        /**
         * 另一编辑器向当前卡组写入了新内容（或游标移动了），接收方应以 {@link #current()}
         * 为准刷新自身；内容已与自身一致时不要回写，否则会循环广播。
         */
        default void onDeckContentChanged(String key, int publisher) {
        }
    }

    private static final DeckHistory INSTANCE = new DeckHistory();

    /**
     * 共享单例：两个编辑器引用同一个快照列表
     */
    public static DeckHistory get() {
        return INSTANCE;
    }

    /** 人机/AI 卡组目录下的只读卡组 */
    public static boolean isReadonlyDeck(String path) {
        String aiDeckDir = AppsSettings.get().getAiDeckDir();
        return path != null && aiDeckDir != null && path.startsWith(aiDeckDir);
    }

    /**
     * 卡包展示卡组（只读的展示态）：磁盘 pack/ 目录、旧版解压的 cacheDeck 目录，
     * 以及卡包档案内的 ydk 引用（现在的默认形式，见 DeckUtil.YDK_ARCHIVE_SEP，不再解压落盘）
     */
    public static boolean isPackDeck(String path) {
        if (path == null) return false;
        if (DeckUtil.isArchiveYdk(path)) return true;
        String packDir = AppsSettings.get().getPackDeckDir();
        String cacheDir = AppsSettings.get().getCacheDeckDir();
        return (packDir != null && path.startsWith(packDir))
                || (cacheDir != null && path.startsWith(cacheDir));
    }

    /**
     * 不纳入跨编辑器同步范围的卡组：只读（人机/AI）、卡包展示/缓存，以及尚未选定卡组（空路径）。
     * <p>
     * 两套编辑器均以此为准：这类卡组既不得被对端的未保存内容盖掉，也不得把自己的载入或浏览
     * 当成“切换卡组”广播给对方——否则“开局顺手载入一份人机卡组”或“页内看一眼卡包”都会
     * 清掉另一侧那份卡组的修改历史并把页头抢走。与 DeckEditorManagerBase 置位
     * isReadonly/isPackMode 同源，保证“不参与联动”与“不可编辑”不会因目录规则改动而走偏。
     */
    public static boolean isOutsideSyncScope(String path) {
        return path == null || path.isEmpty() || isReadonlyDeck(path) || isPackDeck(path);
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
     * 声明当前编辑的卡组归属（不带发起方的兼容入口，仅用于不属于两个编辑器的外部写入）。
     */
    public boolean bindDeck(String key) {
        return bindDeck(key, PUBLISHER_NONE);
    }

    /**
     * 声明当前编辑的卡组归属。key 与上一次不同（含从空变非空）即整段清空历史，
     * 并广播换户事件，供另一侧同步切换到同一份卡组。
     *
     * @param key       卡组标识，通常传 ydk 文件绝对路径；无文件时传空串
     * @param publisher 发起切换的一侧
     * @return 是否发生了换户（key 变化）
     */
    public boolean bindDeck(String key, int publisher) {
        String next = key == null ? "" : key;
        if (next.equals(deckKey)) return false;
        deckKey = next;
        clear();
        for (Listener listener : new ArrayList<>(listeners)) {
            listener.onDeckKeyChanged(deckKey, publisher);
        }
        return true;
    }

    /**
     * 当前卡组归属标识（ydk 绝对路径，未绑定为空串）
     */
    public String getDeckKey() {
        return deckKey;
    }

    /**
     * 游标处的权威内容拷贝；历史为空（还未有任何编辑器载入卡组）时返回 null。
     * 另一侧下次打开同一卡组时用它覆盖磁盘内容，即可“直接加载到最后的修改状态”。
     */
    public DeckInfo current() {
        if (index < 0 || index >= snapshots.size()) return null;
        return snapshot(snapshots.get(index));
    }

    /**
     * 给定卡组与游标处的权威内容是否完全一致。接收侧在应用前先比一次，相等就什么都不做：
     * 这既是“不循环广播”的兜底（自己写回同内容不会触发对端再次广播），
     * 也避开了无谓的卡组网格重建。
     */
    public boolean currentMatches(DeckInfo other) {
        if (index < 0 || index >= snapshots.size()) return other == null;
        if (other == null) return false;
        return snapshots.get(index).toLongString().equals(other.toLongString());
    }

    /**
     * 记录一次卡组快照（不带发起方的兼容入口）。
     */
    public void push(DeckInfo state) {
        push(state, PUBLISHER_NONE);
    }

    /**
     * 记录一次卡组快照：与游标处内容相同则直接跳过（刷新回调常常重复推同一份状态），
     * 否则先丢弃游标之后的旧记录再追加，并广播内容变更供另一侧实时跟随。
     * <p>
     * 去重先于截尾判断，是为了保护 redo 分支：undo 应用旧状态后通常会紧跟一次界面刷新，
     * 若先截尾再记录，刚 undo 掉的那一步会被当成新历史，重做链当场失效。
     */
    public void push(DeckInfo state, int publisher) {
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
        for (Listener listener : new ArrayList<>(listeners)) {
            listener.onDeckContentChanged(deckKey, publisher);
        }
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
        broadcastContent();
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
        broadcastContent();
        return snapshot(snapshots.get(index));
    }

    /** 游标移动（undo/redo）也是一次内容变更，同样广播供另一侧实时跟随 */
    private void broadcastContent() {
        for (Listener listener : new ArrayList<>(listeners)) {
            listener.onDeckContentChanged(deckKey, PUBLISHER_NONE);
        }
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
