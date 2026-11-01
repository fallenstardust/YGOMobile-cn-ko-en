package cn.garymb.ygomobile.game.deck;

import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import cn.garymb.ygomobile.audio.SoundManager;
import cn.garymb.ygomobile.bean.DeckInfo;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.widget.CardGroupView;
import cn.garymb.ygomobile.ui.widget.CardView;
import cn.garymb.ygomobile.utils.YGOUtil;
import ocgcore.data.Card;

/**
 * === 卡组编辑器批量勾选模式（长按进入 / 点击勾选 / 按住整组拖动）===
 * <p>
 * 与单卡拖拽（{@link DeckDropHandler}）互斥协作：本类只在“批量模式”激活时接管卡片的点击、
 * 长按与拖动，退出后一切恢复原有单卡语义。整组拖动松手后的三条落点规则：
 * <ul>
 * <li>落点为主卡组：勾选里只能放额外卡组的卡（融合 / 同调 / 超量 / 连接）保持原位不动，
 *     其余卡参与搬运；若整组都是额外卡组卡则本次不动并提示。</li>
 * <li>落点为额外卡组：主卡组向的卡保持原位不动，其余（即额外卡组卡）可整组移入额外或副卡组区。</li>
 * <li>落点为副卡组：全部勾选卡都可整组移入。</li>
 * </ul>
 * 搬运前先按 {@code getMaxMainCount / getMaxExtraCount / getMaxSideCount}（含换副卡组期间的
 * 临时放宽口径）预演拖动后三个区各自的张数，任一区会超出上限则整组移动全部作废并 toast 原因，
 * 不做部分生效。校验通过才真正改 {@link DeckInfo} 的三个列表。仅在 UI 线程调用。
 */
class DeckBatchSelectionManager implements BatchCardDragHelper.BatchDropHandler {

    /** 勾选条目在卡区间固定按 主→额外→副、区内按下标升序排列，保证移动顺序可预期 */
    private static final DeckInfo.Type[] ZONE_ORDER =
            {DeckInfo.Type.Main, DeckInfo.Type.Extra, DeckInfo.Type.Side};

    private final DeckEditorManager owner;
    /**
     * 勾选集：卡区 → （区内下标 → 勾选当时该下标上的 Card 实例）。
     * 额外存卡片实例是为了在下标因洗牌/排序/换卡组/整组搬运而失去原义时，
     * 能自动识别并丢弃失效勾选，不会把勾错到另一张卡上。
     */
    private final Map<DeckInfo.Type, TreeMap<Integer, Card>> checked = new EnumMap<>(DeckInfo.Type.class);

    private BatchCardDragHelper batchDragHelper;
    /**
     * 整组拖到此处即批量删除的落点（搜索结果列表）。本方法在
     * {@code cardSearcherManager.bindViews} 之前执行，故不能用 getSearchRecyclerView()（此时还是
     * 上一棵视图树 / null 的引用），与 DeckDropHandler#setup 同样直接从 rootView 查。
     */
    private View deleteTargetView;
    private boolean batchMode;

    /** 一个勾选条目：所属卡区 + 区内下标 + 卡片 */
    private static final class Entry {
        final DeckInfo.Type zone;
        final int index;
        final Card card;

        Entry(DeckInfo.Type zone, int index, Card card) {
            this.zone = zone;
            this.index = index;
            this.card = card;
        }
    }

    DeckBatchSelectionManager(DeckEditorManager owner) {
        this.owner = owner;
        for (DeckInfo.Type zone : ZONE_ORDER) checked.put(zone, new TreeMap<>());
    }

    /** 建立整组拖动助手并把三个卡组网格、搜索结果列表登记为落点（视图树重建后需重新调用） */
    void setup() {
        SoundManager sound = owner.soundManager;
        batchDragHelper = new BatchCardDragHelper(owner.activity, this, sound);
        batchDragHelper.addDropTarget(owner.cgvMain);
        batchDragHelper.addDropTarget(owner.cgvExtra);
        batchDragHelper.addDropTarget(owner.cgvSide);
        //与单卡拖拽同口径：搜索结果列表也是落点，整组松手在此即视为批量删除
        deleteTargetView = owner.rootView == null ? null
                : owner.rootView.findViewById(R.id.rv_deck_search_results);
        if (deleteTargetView != null) batchDragHelper.addDropTarget(deleteTargetView);
    }

    /** 退出批量模式：收起在飞浮层、清空勾选与打勾图标（切卡组 / 旋转重绑 / 退出编辑器 / 返回键共用） */
    void reset() {
        boolean wasBatch = batchMode;
        //旋转或退出时可能正处于整组拖动中：先把浮层收掉，否则卡图快照会残留在界面上
        if (batchDragHelper != null && batchDragHelper.isDragging()) {
            batchDragHelper.cancelDrag();
        }
        batchMode = false;
        clearChecks();
        if (wasBatch) rebindCardViewTouchListeners();
    }

    boolean isBatchMode() {
        return batchMode;
    }

    /**
     * 卡片被点击：批量模式下改为切换该卡勾选并吞掉点击（不再走显示卡详的旧路径）。
     *
     * @return true 表示本次点击已被批量模式消费，调用方不应再执行卡片详情展示
     */
    boolean handleTap(DeckInfo.Type type, int position) {
        if (!batchMode) return false;
        toggleCheck(type, position);
        return true;
    }

    /**
     * 给网格里的每张卡挂上批量模式的长按入口与打勾态。卡组每次刷新后都要调用，
     * 否则视图重建会丢掉打勾图标。
     */
    void attachCardView(CardView cardView, DeckInfo.Type type, int index) {
        if (cardView == null) return;
        // 显式置长按可点：CardView 自身只由卡组刷新挂了 click / touch 监听，
        // 不预设该标志时部分 ROM 上不会起长按计时
        cardView.setLongClickable(true);
        cardView.setOnLongClickListener(v -> onLongPress(type, index));
        cardView.setBatchChecked(isChecked(type, index));
    }

    /**
     * 批量模式下的触摸监听：按住已勾选（或长按刚勾选）的卡片移动超过阈值即整组拖动；
     * 未勾选的卡片被按住拖动时先补勾上，保证“按住即搬整组”的手势不被打断。
     * 与单卡 {@code CardDragTouchListener} 一样，ACTION_DOWN 返回 false 让 CardView 继续
     * 走自身的 click / long-click 判定，只有真正进入拖动后才接管事件流。
     */
    View.OnTouchListener createBatchTouchListener(DeckInfo.Type type, int index, CardView cardView) {
        return new BatchTouchListener(type, index, cardView);
    }

    private final class BatchTouchListener implements View.OnTouchListener {
        private final DeckInfo.Type type;
        private final int index;
        private final CardView cardView;
        private float downX, downY;
        /**
         * 本监听器是否亲眼收到过 ACTION_DOWN。进入批量模式时会在手指还按着的这次手势
         * 中途被换上，此时 downX/downY 还是初始 0，不区分就会把第一个 MOVE 当成“已移动超阈值”
         * 而凭空起拖（且锚点抓取坐标错乱）。
         */
        private boolean sawDown;

        BatchTouchListener(DeckInfo.Type type, int index, CardView cardView) {
            this.type = type;
            this.index = index;
            this.cardView = cardView;
        }

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if (batchDragHelper == null) return false;
            //拖动进行中：后续触摸事件全部转交整组拖动助手
            if (batchDragHelper.isDragging()) {
                return batchDragHelper.onTouchEvent(event);
            }
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    downX = event.getX();
                    downY = event.getY();
                    sawDown = true;
                    return false;
                case MotionEvent.ACTION_MOVE: {
                    if (!canEdit() || cardView.getCard() == null) return false;
                    if (!sawDown) {
                        //以当前点为起点补算，本次不判拖动；同时取消 View 仍挂着的长按计时，
                        //否则拖动中途到点会回灌一次 onLongPress 把已拖的卡组又切回批量勾选
                        v.cancelLongPress();
                        downX = event.getX();
                        downY = event.getY();
                        sawDown = true;
                        return false;
                    }
                    if (Math.abs(event.getX() - downX) <= owner.touchSlop
                            && Math.abs(event.getY() - downY) <= owner.touchSlop) {
                        return false;
                    }
                    //接管本事件后 View.onTouchEvent 不再跑，需自己取消长按，保证“按住=拖动”
                    v.cancelLongPress();
                    //按住未勾选的卡也先把它勾上，整组包含它
                    if (!isChecked(type, index)) setChecked(type, index, true);
                    List<BatchCardDragHelper.BatchItem> items = buildDragItems();
                    int anchor = indexOfItem(items, type, index);
                    if (items.isEmpty() || anchor < 0) return false;
                    if (v.getParent() != null) {
                        //禁止父级拦截后续事件，保证拖动期间持续收到 MOVE/UP
                        v.getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    boolean started = batchDragHelper.startDrag(items, anchor, event);
                    if (!started) sawDown = false;
                    return started;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    sawDown = false;
                    return false;
                default:
                    return false;
            }
        }
    }

    /**
     * 长按入口：未在批量模式时进入并勾选该卡（附一次操作提示）；已在批量模式时只补勾该卡，
     * 已勾选的卡长按不取消（否则“按住准备整组拖动”会被 500ms 长按误判成取消勾选）。
     *
     * @return 是否已消费本次长按
     */
    private boolean onLongPress(DeckInfo.Type type, int index) {
        if (!canEdit()) return false;
        // 手指先小幅移动就会由单卡拖拽助手抢起手（View 的长按计时不会被跳过），此时换上
        // 触摸监听会让旧的 CardDragHelper 再也收不到 MOVE/UP，卡图阴影永久停在按下点：
        // 进模式前先把在飞单卡拖拽就地取消，阴影随本帧消失
        if (owner.dragHelper != null && owner.dragHelper.isDragging()) {
            owner.dragHelper.cancelDrag();
        }
        if (!batchMode) {
            batchMode = true;
            clearChecks();
            // 模式切换后卡片触摸语义也要立刻切换（不重建视图，否则手指还按着的
            // 这次手势会被取消，无法“长按后直接拖动”）
            rebindCardViewTouchListeners();
            // 本编辑器的删除落点是搜索结果列表，与卡组编辑页的垃圾桶不同，用专用文案
            YGOUtil.showTextToast(owner.activity.getString(R.string.batch_mode_hint_editor));
        }
        if (!isChecked(type, index)) setChecked(type, index, true);
        return true;
    }

    /**
     * 不重建卡片视图，只按当前模式重新施加每张卡的触摸监听：
     * 批量模式 → 整组拖动；否则 → 原来的单卡拖拽。卡组重建（notifyDeckChanged）时由
     * {@code refreshCardGroupView} 自行分流，这里只处理“模式当场切换”的场景。
     * 整组拖动进行中不换监听，否则会把正在跟随手指的浮层与事件流一同拆掉。
     */
    private void rebindCardViewTouchListeners() {
        if (batchDragHelper != null && batchDragHelper.isDragging()) return;
        DeckCardAdapter adapter = batchMode ? null : owner.cardSearcherManager.getSearchAdapter();
        for (DeckInfo.Type zone : ZONE_ORDER) {
            CardGroupView group = groupView(zone);
            if (group == null) continue;
            int count = group.getChildCount();
            for (int i = 0; i < count; i++) {
                CardView cardView = group.getCardViewAt(i);
                if (cardView == null) continue;
                if (batchMode) {
                    cardView.setOnTouchListener(createBatchTouchListener(zone, i, cardView));
                } else if (adapter != null) {
                    cardView.setOnTouchListener(adapter.createDragTouchListener(zone, i,
                            cardView.getCard(), owner.touchSlop, owner.isReadonly || owner.isPackMode));
                }
                cardView.setBatchChecked(isChecked(zone, i));
            }
        }
    }

    private boolean canEdit() {
        return !owner.isReadonly && !owner.isPackMode;
    }

    /** 勾选位上当前的卡与勾选当时记录的卡是否同一实例（实例失配即视为未勾选） */
    private boolean isChecked(DeckInfo.Type type, int index) {
        TreeMap<Integer, Card> map = checked.get(type);
        if (map == null || !map.containsKey(index)) return false;
        return map.get(index) == cardAt(type, index);
    }

    private Card cardAt(DeckInfo.Type type, int index) {
        List<Card> list = cardList(type);
        return index >= 0 && index < list.size() ? list.get(index) : null;
    }

    private void toggleCheck(DeckInfo.Type type, int index) {
        setChecked(type, index, !isChecked(type, index));
    }

    private void setChecked(DeckInfo.Type type, int index, boolean value) {
        TreeMap<Integer, Card> map = checked.get(type);
        if (map == null) return;
        if (value) {
            Card card = cardAt(type, index);
            // 越界或空位不记录，避免把“不存在的位置”当成已勾选
            if (card == null) return;
            map.put(index, card);
        } else {
            map.remove(index);
        }
        CardGroupView view = groupView(type);
        if (view != null) view.applyBatchChecked(validIndexes(type));
    }

    /** 当前卡区里仍然有效的勾选下标（位上卡片实例与记录一致的那些） */
    private TreeSet<Integer> validIndexes(DeckInfo.Type type) {
        TreeSet<Integer> valid = new TreeSet<>();
        TreeMap<Integer, Card> map = checked.get(type);
        if (map == null) return valid;
        for (Map.Entry<Integer, Card> entry : map.entrySet()) {
            if (entry.getValue() != null && entry.getValue() == cardAt(type, entry.getKey())) {
                valid.add(entry.getKey());
            }
        }
        return valid;
    }

    private void clearChecks() {
        for (TreeMap<Integer, Card> map : checked.values()) map.clear();
        applyChecks();
    }

    /** 按当前勾选集合重刷三个网格的打勾图标（卡组刷新后由 DeckEditorManager 调用） */
    void applyChecks() {
        // 视图尚未绑定（initialize 之前的数据刷新）时直接跳过，网格重建后会另行施加
        if (owner.cgvMain == null || owner.cgvExtra == null || owner.cgvSide == null) return;
        if (!canEdit()) {
            owner.cgvMain.clearBatchChecked();
            owner.cgvExtra.clearBatchChecked();
            owner.cgvSide.clearBatchChecked();
            return;
        }
        owner.cgvMain.applyBatchChecked(validIndexes(DeckInfo.Type.Main));
        owner.cgvExtra.applyBatchChecked(validIndexes(DeckInfo.Type.Extra));
        owner.cgvSide.applyBatchChecked(validIndexes(DeckInfo.Type.Side));
    }

    private CardGroupView groupView(DeckInfo.Type type) {
        if (type == DeckInfo.Type.Main) return owner.cgvMain;
        if (type == DeckInfo.Type.Extra) return owner.cgvExtra;
        return owner.cgvSide;
    }

    private CardGroupView groupViewOfTarget(View target) {
        if (target == owner.cgvMain || target == owner.cgvExtra || target == owner.cgvSide) {
            return (CardGroupView) target;
        }
        return null;
    }

    private List<Card> cardList(DeckInfo.Type type) {
        if (type == DeckInfo.Type.Main) return owner.currentDeck.mainCards;
        if (type == DeckInfo.Type.Extra) return owner.currentDeck.extraCards;
        return owner.currentDeck.sideCards;
    }

    /**
     * 当前勾选条目，按 主→额外→副、区内下标升序。顺手剪掉因洗牌 / 排序 / 换卡组而
     * “下标上的卡片实例与勾选记录不再同一”的失效条目，保证拖动与搬运只会作用于原本勾选的卡。
     */
    private List<Entry> snapshotSelection() {
        List<Entry> list = new ArrayList<>();
        for (DeckInfo.Type zone : ZONE_ORDER) {
            TreeMap<Integer, Card> map = checked.get(zone);
            if (map == null || map.isEmpty()) continue;
            List<Integer> stale = null;
            for (Map.Entry<Integer, Card> entry : map.entrySet()) {
                Card current = cardAt(zone, entry.getKey());
                if (entry.getValue() != null && entry.getValue() == current) {
                    list.add(new Entry(zone, entry.getKey(), current));
                } else {
                    if (stale == null) stale = new ArrayList<>();
                    stale.add(entry.getKey());
                }
            }
            if (stale != null) map.keySet().removeAll(stale);
        }
        return list;
    }

    /** 勾选条目转成拖动浮层数据（视图已附加的才有浮层） */
    private List<BatchCardDragHelper.BatchItem> buildDragItems() {
        List<Entry> selection = snapshotSelection();
        List<BatchCardDragHelper.BatchItem> items = new ArrayList<>(selection.size());
        for (Entry entry : selection) {
            CardGroupView group = groupView(entry.zone);
            CardView cardView = group != null ? group.getCardViewAt(entry.index) : null;
            items.add(new BatchCardDragHelper.BatchItem(cardView, entry.zone, entry.index, entry.card));
        }
        return items;
    }

    private int indexOfItem(List<BatchCardDragHelper.BatchItem> items, DeckInfo.Type zone, int index) {
        for (int i = 0; i < items.size(); i++) {
            BatchCardDragHelper.BatchItem item = items.get(i);
            if (item.zone == zone && item.index == index) return i;
        }
        return -1;
    }

    @Override
    public void onBatchDrop(View target, float rawX, float rawY) {
        if (!batchMode || !canEdit()) return;
        // 搜索结果列表区：与单卡“拖到卡组区外删除”同一语义，整组一律删除
        if (deleteTargetView != null && target == deleteTargetView) {
            deleteSelection();
            return;
        }
        CardGroupView group = groupViewOfTarget(target);
        if (group == null) return;
        DeckInfo.Type targetType = target == owner.cgvMain ? DeckInfo.Type.Main
                : target == owner.cgvExtra ? DeckInfo.Type.Extra : DeckInfo.Type.Side;
        int[] loc = new int[2];
        target.getLocationOnScreen(loc);
        int dropIndex = group.getIndexByPosition(rawX - loc[0], rawY - loc[1]);
        moveSelectionTo(targetType, dropIndex);
    }

    /**
     * 批量删除：把当前勾选的卡从各自卡区摘掉，删完立即退出批量模式。
     * 与单卡拖到搜索区同口径受换副卡组限制（isSiding 期间不得减员），且同样不弹二次确认：
     * 删除已计入卡组历史，侧边的撤销键可直接回退。
     */
    private void deleteSelection() {
        // 副卡组替换期间只允许三区调配，批量删卡会造成不可逆减员
        if (owner.isSiding) return;
        List<Entry> selection = snapshotSelection();
        if (selection.isEmpty()) return;
        removeEntries(selection);
        owner.currentDeck.syncCounts();
        owner.isModified = true;
        owner.notifyDeckChanged();
        // 用户规格：批量删除完立即退出批量勾选模式
        reset();
        YGOUtil.showTextToast(owner.activity.getString(R.string.batch_delete_done, selection.size()));
    }

    /**
     * 把条目从所属卡区列表中摘除：同一区内按下标降序删除，避免前面的摘除影响后面的下标。
     * 不在此处动 currentDeck 计数与刷新，由调用方统一处理。
     */
    private void removeEntries(List<Entry> entries) {
        Map<DeckInfo.Type, List<Entry>> byZone = new EnumMap<>(DeckInfo.Type.class);
        for (Entry entry : entries) {
            List<Entry> zoneEntries = byZone.get(entry.zone);
            if (zoneEntries == null) {
                zoneEntries = new ArrayList<>();
                byZone.put(entry.zone, zoneEntries);
            }
            zoneEntries.add(entry);
        }
        for (Map.Entry<DeckInfo.Type, List<Entry>> group : byZone.entrySet()) {
            List<Entry> zoneEntries = group.getValue();
            zoneEntries.sort(Comparator.comparingInt((Entry e) -> e.index).reversed());
            List<Card> sourceList = cardList(group.getKey());
            for (Entry entry : zoneEntries) {
                if (entry.index < sourceList.size()) sourceList.remove(entry.index);
            }
        }
    }

    /**
     * 执行整组搬运：先按落点卡区筛掉不允许进入的卡（保持原位），再预演三区张数，
     * 任一区超限则整组作废并 toast 原因；通过后一次性完成“从原区摘除 + 插入落点”。
     */
    private void moveSelectionTo(DeckInfo.Type targetType, int dropIndex) {
        List<Entry> selection = snapshotSelection();
        if (selection.isEmpty()) return;

        List<Entry> movable = new ArrayList<>(selection.size());
        for (Entry entry : selection) {
            if (acceptsInZone(targetType, entry.card)) movable.add(entry);
        }
        if (movable.isEmpty()) {
            // 整组都是与落点卡区互斥的卡：没有任何卡可动，直接提示原因
            YGOUtil.showTextToast(owner.activity.getString(
                    targetType == DeckInfo.Type.Main ? R.string.tip_drag_extra_in_main
                            : R.string.tip_drag_normal_in_extra));
            return;
        }

        if (!checkCapacity(targetType, movable)) return;

        // 落点在原列表中的位置要扣掉“同区内已摘除且位于落点之前”的条目，否则插入点整体后移
        int shift = 0;
        for (Entry entry : movable) {
            if (entry.zone == targetType && entry.index < dropIndex) shift++;
        }
        List<Card> targetList = cardList(targetType);
        int insert = Math.max(0, Math.min(dropIndex - shift, targetList.size()));

        // 每个来源区内按下标降序摘除，避免前面的摘除影响后面的下标
        removeEntries(movable);
        // 按 主→额外→副、区内升序（movable 本身已是该序）依次插入落点，保持组内相对顺序
        for (Entry entry : movable) {
            targetList.add(Math.min(insert, targetList.size()), entry.card);
            insert++;
        }
        owner.currentDeck.syncCounts();
        owner.isModified = true;
        owner.notifyDeckChanged();
        // 与批量删除同口径：整组搬运落定后立即退出批量勾选模式（拖动已收完成，此处重绑监听安全，
        // BatchCardDragHelper 在回调本方法前已将 dragging 置 false）
        reset();
    }

    /**
     * 预演整组搬运后三区张数：来源区减去被摘除数、落点区加上插入数（同区时先减后加净为零）。
     * 任一区超出当前上限（卡包展示模式不限制主卡组张数，与 {@code moveToDeck} 口径一致）
     * 即返回 false 并 toast 具体原因，调用方据此整组作废。
     */
    private boolean checkCapacity(DeckInfo.Type targetType, List<Entry> movable) {
        Map<DeckInfo.Type, Integer> movedOut = new EnumMap<>(DeckInfo.Type.class);
        for (Entry entry : movable) {
            Integer prev = movedOut.get(entry.zone);
            movedOut.put(entry.zone, prev == null ? 1 : prev + 1);
        }
        int maxMain = owner.getMaxMainCount();
        int maxExtra = owner.getMaxExtraCount();
        int maxSide = owner.getMaxSideCount();
        int newMain = owner.currentDeck.mainCards.size() - count(movedOut, DeckInfo.Type.Main)
                + (targetType == DeckInfo.Type.Main ? movable.size() : 0);
        int newExtra = owner.currentDeck.extraCards.size() - count(movedOut, DeckInfo.Type.Extra)
                + (targetType == DeckInfo.Type.Extra ? movable.size() : 0);
        int newSide = owner.currentDeck.sideCards.size() - count(movedOut, DeckInfo.Type.Side)
                + (targetType == DeckInfo.Type.Side ? movable.size() : 0);
        if (!owner.isPackMode && newMain > maxMain) {
            YGOUtil.showTextToast(owner.activity.getString(R.string.tip_drag_main_full, maxMain));
            return false;
        }
        if (newExtra > maxExtra) {
            YGOUtil.showTextToast(owner.activity.getString(R.string.tip_drag_extra_full, maxExtra));
            return false;
        }
        if (newSide > maxSide) {
            YGOUtil.showTextToast(owner.activity.getString(R.string.tip_drag_side_full, maxSide));
            return false;
        }
        return true;
    }

    private int count(Map<DeckInfo.Type, Integer> map, DeckInfo.Type type) {
        Integer value = map.get(type);
        return value == null ? 0 : value;
    }

    /**
     * 卡片能否落入目标卡区：主卡组不收额外卡组卡（融合 / 同调 / 超量 / 连接），
     * 额外卡组只收额外卡组卡，副卡组两者皆收——与 {@code DeckDropHandler#moveToDeck} 同口径。
     */
    private boolean acceptsInZone(DeckInfo.Type zone, Card card) {
        boolean extraOnly = Card.isExtraCard(card.Type);
        if (zone == DeckInfo.Type.Main) return !extraOnly;
        if (zone == DeckInfo.Type.Extra) return extraOnly;
        return true;
    }
}
