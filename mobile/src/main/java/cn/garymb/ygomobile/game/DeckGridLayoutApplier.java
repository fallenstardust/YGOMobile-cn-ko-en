package cn.garymb.ygomobile.game;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;

import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.ui.widget.CardGroupView;

/**
 * === 卡组网格卡片尺寸与卡包展示模式（对应 deck_con.cpp 的 Resize/Build 版面逻辑）===
 * 自 DeckEditorManager 拆出：按主卡组区域实测尺寸计算统一卡面宽高（普通模式固定
 * 4 行 + 额外/副卡组各 1 行；卡包模式主网格铺满剩余高度），并负责卡包模式的
 * 统计行/网格显隐与标签文案切换。全部方法在 UI 线程调用。
 */
class DeckGridLayoutApplier {

    private final DeckEditorManager owner;

    DeckGridLayoutApplier(DeckEditorManager owner) {
        this.owner = owner;
    }

    /**
     * 根据主卡组区域的实际测量宽高动态计算卡片尺寸：
     * 普通模式保证一行放下 {@link Constants#DECK_WIDTH_COUNT} 张且主卡组4行完整显示；
     * 卡包展示模式主网格铺满可用高度，行数随高度动态计算（不再限制4行/60张）。
     */
    void requestUpdate() {
        if (owner.cgvMain == null) return;
        owner.cgvMain.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                int mainWidth = owner.cgvMain.getWidth();
                int mainHeight = owner.cgvMain.getHeight();
                if (mainWidth <= 0 || mainHeight <= 0) return;
                owner.cgvMain.getViewTreeObserver().removeOnGlobalLayoutListener(this);

                int extraHeight = owner.cgvExtra != null ? owner.cgvExtra.getHeight() : 0;
                int sideHeight = owner.cgvSide != null ? owner.cgvSide.getHeight() : 0;

                applyDeckCardSize(mainWidth, mainHeight, extraHeight, sideHeight);
            }
        });
    }

    private void applyDeckCardSize(int mainWidth, int mainHeight, int extraHeight, int sideHeight) {
        int availWidth = mainWidth - owner.cgvMain.getPaddingLeft() - owner.cgvMain.getPaddingRight();
        if (availWidth <= 0) return;
        float ratio = (float) Constants.CORE_SKIN_CARD_SMALL_SIZE[1] / (float) Constants.CORE_SKIN_CARD_SMALL_SIZE[0];
        int mainAvail = Math.max(0, mainHeight - owner.cgvMain.getPaddingTop() - owner.cgvMain.getPaddingBottom());
        int extraAvail = extraHeight > 0 && owner.cgvExtra != null
                ? Math.max(0, extraHeight - owner.cgvExtra.getPaddingTop() - owner.cgvExtra.getPaddingBottom()) : 0;
        int sideAvail = sideHeight > 0 && owner.cgvSide != null
                ? Math.max(0, sideHeight - owner.cgvSide.getPaddingTop() - owner.cgvSide.getPaddingBottom()) : 0;
        if (owner.isPackMode) {
            int cardWidth, cardHeight;
            if (owner.savedNormalCardWidth > 0 && owner.savedNormalCardHeight > 0) {
                cardWidth = owner.savedNormalCardWidth;
                cardHeight = owner.savedNormalCardHeight;
            } else {
                int totalAvail = mainAvail + extraAvail + sideAvail;
                int wByCol = availWidth / Constants.DECK_WIDTH_COUNT;
                int wByH = totalAvail > 0 ? (int) ((totalAvail / 6f) / ratio) : Integer.MAX_VALUE;
                cardWidth = Math.max(1, Math.min(wByCol, wByH));
                cardHeight = Math.max(1, (int) (cardWidth * ratio));
            }
            int rows = Math.max(1, mainAvail / cardHeight);
            applyCardSizeToAll(cardWidth, cardHeight);
            owner.cgvMain.setLineLimit(rows, Constants.DECK_WIDTH_COUNT, Constants.DECK_WIDTH_MAX_COUNT);
            owner.notifyDeckChanged();
            return;
        }
        int totalAvail = mainAvail + extraAvail + sideAvail;
        int wByCol = availWidth / Constants.DECK_WIDTH_COUNT;
        int wByH = totalAvail > 0 ? (int) ((totalAvail / 6f) / ratio) : Integer.MAX_VALUE;
        int cardWidth = Math.max(1, Math.min(wByCol, wByH));
        int cardHeight = Math.max(1, (int) (cardWidth * ratio));
        owner.savedNormalCardWidth = cardWidth;
        owner.savedNormalCardHeight = cardHeight;
        applyCardSizeToAll(cardWidth, cardHeight);
        owner.cgvMain.setLineLimit(4, 10, 15);
        applyGroupExactHeight(owner.cgvMain, cardHeight * 4);
        applyGroupExactHeight(owner.cgvExtra, cardHeight);
        applyGroupExactHeight(owner.cgvSide, cardHeight);
        // 竖屏宽度小→卡面小→左列定高生效后比容器矮，右侧搜索结果列表（match_parent）
        // 底边低于副卡组网格；定高布局完成后按实测把列表底边对齐副卡组网格底边
        scheduleSearchListSideBottomAlign();
        owner.notifyDeckChanged();
    }

    private void applyCardSizeToAll(int w, int h) {
        owner.cgvMain.setCardSize(w, h);
        owner.cgvExtra.setCardSize(w, h);
        owner.cgvSide.setCardSize(w, h);
        owner.cardSearcherManager.setCardSize(w, h);
    }

    private void applyGroupExactHeight(CardGroupView view, int contentHeight) {
        if (view == null) return;
        ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp == null) return;
        lp.height = contentHeight + view.getPaddingTop() + view.getPaddingBottom();
        if (lp instanceof LinearLayout.LayoutParams) {
            ((LinearLayout.LayoutParams) lp).weight = 0;
        }
        view.setLayoutParams(lp);
    }

    /** 在网格定高触发的下一次布局完成后，把搜索结果列表底边对齐副卡组网格底边（一次性监听） */
    private void scheduleSearchListSideBottomAlign() {
        if (owner.cgvSide == null) return;
        final View rv = owner.cardSearcherManager.getSearchRecyclerView();
        if (rv == null) return;
        owner.cgvSide.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        if (owner.cgvSide == null) return;
                        owner.cgvSide.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        alignSearchListToSideBottom(rv);
                    }
                });
    }

    /**
     * 搜索结果列表高度 = 副卡组网格底缘 − 列表顶缘（同窗口坐标实测，横屏本就对齐时
     * delta=0 不动；列表高度改为定值后下次定高流程再按实测重新求差，不叠加）
     */
    private void alignSearchListToSideBottom(View rv) {
        View side = owner.cgvSide;
        if (side == null || rv == null || side.getVisibility() != View.VISIBLE
                || side.getHeight() <= 0 || rv.getHeight() <= 0) return;
        int[] sp = new int[2], rp = new int[2];
        side.getLocationInWindow(sp);
        rv.getLocationInWindow(rp);
        int delta = (sp[1] + side.getHeight()) - (rp[1] + rv.getHeight());
        if (delta == 0) return;
        ViewGroup.LayoutParams lp = rv.getLayoutParams();
        int target = (lp.height > 0 ? lp.height : rv.getHeight()) + delta;
        if (target <= 0) return;
        lp.height = target;
        rv.setLayoutParams(lp);
    }

    /**
     * 切换卡包展示模式：卡包卡组（ygocore/pack）隐藏额外/副卡组的统计行与网格，
     * 主卡组网格铺满剩余高度，行数与最大数量随高度动态计算（不再限制4行/60张）。
     */
    void applyPackMode(boolean packMode) {
        if (owner.isPackMode == packMode) return;
        owner.isPackMode = packMode;
        if (owner.tvLabelMainDeck != null) {
            owner.tvLabelMainDeck.setText(owner.mStringManager.getSystemString(packMode ? 1477 : 1330, "主卡组:"));
        }
        int vis = packMode ? View.GONE : View.VISIBLE;
        if (owner.layoutExtraStats != null) owner.layoutExtraStats.setVisibility(vis);
        if (owner.cgvExtra != null) owner.cgvExtra.setVisibility(vis);
        if (owner.layoutSideStats != null) owner.layoutSideStats.setVisibility(vis);
        if (owner.cgvSide != null) owner.cgvSide.setVisibility(vis);
        if (packMode) {
            setMainGridFillHeight();
        }
        owner.cardSearcherManager.setDragState(owner.touchSlop, owner.isReadonly || owner.isPackMode);
        requestUpdate();
    }

    //卡包模式下主卡组网格铺满剩余高度（height=0dp + weight=1），普通模式由applyGroupExactHeight恢复定高
    private void setMainGridFillHeight() {
        if (owner.cgvMain == null) return;
        ViewGroup.LayoutParams lp = owner.cgvMain.getLayoutParams();
        if (lp instanceof LinearLayout.LayoutParams) {
            lp.height = 0;
            ((LinearLayout.LayoutParams) lp).weight = 1;
            owner.cgvMain.setLayoutParams(lp);
        }
    }
}
