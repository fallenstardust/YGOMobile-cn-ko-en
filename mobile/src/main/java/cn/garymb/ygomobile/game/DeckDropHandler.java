package cn.garymb.ygomobile.game;

import android.view.View;
import android.view.ViewConfiguration;

import java.util.List;

import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.bean.DeckInfo;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.widget.CardGroupView;
import ocgcore.data.Card;

/**
 * === 卡组拖放（网格内换位、跨网格移动、搜索结果拖入、拖回搜索区删除）===
 * 自 DeckEditorManager 拆出：落点回调由 DeckEditorManager#onCardDrop 一行转发，
 * 跨网格移动只校验类型与容量（卡片已在卡组中，不改变全局同名卡数与 GeneSys 总分），
 * 校验失败还原原位；从搜索结果拖入走 DeckEditorManager 的 pushMain/pushExtra/pushSide
 * 完整禁限校验。仅在 UI 线程调用。
 */
class DeckDropHandler {

    private final DeckEditorManager owner;

    DeckDropHandler(DeckEditorManager owner) {
        this.owner = owner;
    }

    /** 注册三个卡组网格与搜索结果列表为落点目标，并把拖拽阈值同步给卡池面板 */
    void setup() {
        owner.touchSlop = ViewConfiguration.get(owner.activity).getScaledTouchSlop();
        owner.cardSearcherManager.setDragState(owner.touchSlop, owner.isReadonly || owner.isPackMode);
        owner.dragHelper.addDropTarget(owner.cgvMain);
        owner.dragHelper.addDropTarget(owner.cgvExtra);
        owner.dragHelper.addDropTarget(owner.cgvSide);
        if (owner.rootView != null) {
            View rvSearchResults = owner.rootView.findViewById(R.id.rv_deck_search_results);
            if (rvSearchResults != null) owner.dragHelper.addDropTarget(rvSearchResults);
        }
    }

    /**
     * 应用内自定义拖拽的落点回调：按落点目标完成卡片的移动/新增/删除。
     * 来自卡组网格的卡先移出原位再插入落点（复用类型/数量/禁限校验），校验失败还原原位。
     */
    void onCardDrop(View target, DeckInfo.Type source, int index, Card card, float rawX, float rawY) {
        if (card == null || owner.isReadonly) return;

        View searchRV = owner.cardSearcherManager.getSearchRecyclerView();
        if (target == searchRV) {
            if (source != null) {
                List<Card> list = getDeckList(source);
                if (index >= 0 && index < list.size()) {
                    list.remove(index);
                    owner.isModified = true;
                    owner.notifyDeckChanged();
                }
            }
            return;
        }

        if (!(target instanceof CardGroupView)) return;
        DeckInfo.Type targetType;
        if (target == owner.cgvMain) targetType = DeckInfo.Type.Main;
        else if (target == owner.cgvExtra) targetType = DeckInfo.Type.Extra;
        else targetType = DeckInfo.Type.Side;

        int[] loc = new int[2];
        target.getLocationOnScreen(loc);
        int dropIndex = ((CardGroupView) target).getIndexByPosition(rawX - loc[0], rawY - loc[1]);

        if (source != null) {
            List<Card> sourceList = getDeckList(source);
            if (index < 0 || index >= sourceList.size()) return;
            Card moved = sourceList.remove(index);

            if (source == targetType) {
                int insert = (index < dropIndex) ? dropIndex - 1 : dropIndex;
                insert = Math.max(0, Math.min(insert, sourceList.size()));
                sourceList.add(insert, moved);
                owner.isModified = true;
                owner.notifyDeckChanged();
                return;
            }

            owner.currentDeck.syncCounts();
            if (!moveToDeck(targetType, moved, dropIndex)) {
                sourceList.add(Math.min(index, sourceList.size()), moved);
                owner.notifyDeckChanged();
            }
            return;
        }

        //搜索结果拖入：直接插入目标网格
        pushToDeck(targetType, card, dropIndex);
    }

    /**
     * 跨卡组移动：跳过checkLimit禁限/分数校验，仅校验卡片类型兼容性和目标卡组容量。
     * 卡片已在卡组中（非新增），移动不改变全局同名卡总数和GeneSys总分。
     */
    private boolean moveToDeck(DeckInfo.Type type, Card card, int seq) {
        if (card == null) return false;
        if (type == DeckInfo.Type.Main && Card.isExtraCard(card.Type)) return false;
        if (type == DeckInfo.Type.Extra && !Card.isExtraCard(card.Type)) return false;
        if (type == DeckInfo.Type.Main && !owner.isPackMode
                && owner.currentDeck.getMainCount() >= Constants.DECK_MAIN_MAX) return false;
        if (type == DeckInfo.Type.Extra
                && owner.currentDeck.getExtraCount() >= Constants.DECK_EXTRA_MAX) return false;
        if (type == DeckInfo.Type.Side
                && owner.currentDeck.getSideCount() >= Constants.DECK_SIDE_MAX) return false;
        List<Card> list = getDeckList(type);
        int insert = Math.max(0, Math.min(seq, list.size()));
        list.add(insert, card);
        owner.currentDeck.syncCounts();
        owner.isModified = true;
        owner.notifyDeckChanged();
        return true;
    }

    private boolean pushToDeck(DeckInfo.Type type, Card card, int seq) {
        if (type == DeckInfo.Type.Main) return owner.pushMain(card, seq);
        if (type == DeckInfo.Type.Extra) return owner.pushExtra(card, seq);
        return owner.pushSide(card, seq);
    }

    private List<Card> getDeckList(DeckInfo.Type type) {
        if (type == DeckInfo.Type.Main) return owner.currentDeck.mainCards;
        if (type == DeckInfo.Type.Extra) return owner.currentDeck.extraCards;
        return owner.currentDeck.sideCards;
    }
}
