package cn.garymb.ygomobile.ui.dialogs;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import cn.garymb.ygomobile.game.DeckCardAdapter;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.utils.DraggablePopupHelper;
import ocgcore.DataManager;
import ocgcore.StringManager;
import ocgcore.data.Card;

/**
 * 决斗内「关键词卡片列表」贴边面板：点击卡片详情描述中「」/""之间的高亮关键词后，
 * 在 layout_game_right 最左侧停靠显示命中该关键词的卡片纵向列表。
 *
 * <p>形态为非模态 {@link PopupWindow}（对齐 {@link CardDisplayDialog} 的弹窗惯例）：不遮挡决斗场、
 * 决斗可继续，点面板外或右上角关闭收起。列表 item 与 adapter 复用卡组编辑搜索结果的
 * {@link DeckCardAdapter}（独立模式构造，无拖拽、点击仅回调外部刷新左侧详情面板）。
 *
 * <p>关键词命中集合的查询语义与 {@code CardDetail.queryList/queryable} 一致：
 * 字段码匹配 + 卡名/描述包含关键词，全部命中卡纵向列出（含当前卡自身）。
 */
public class KeywordCardListDialog {

    /** 面板宽（dp）：容纳小卡图 + 卡名/信息/攻守文本，与卡组编辑搜索结果 item 观感一致 */
    private static final float PANEL_WIDTH_DP = 145f;

    /** 拖拽记忆/收缩横条对齐用的稳定 id（供 DraggablePopupHelper 持久化位置与旋转重排识别） */
    private static final String DIALOG_ID = "keyword_card_list";

    private final Context context;
    /** 居中/锚定区域 layout_game_right：作为 DraggablePopupHelper 全窗口层内内容的居中区域与窗口 token 源 */
    private final View anchor;
    private final DeckCardAdapter adapter;
    private PopupWindow popupWindow;
    private DraggablePopupHelper draggableHelper;
    private final int panelWidthPx;
    private final View root;
    private final TextView tvTitle;

    public KeywordCardListDialog(Context context, ImageLoader imageLoader, View anchor,
                                 List<Card> cards, String keyword,
                                 DeckCardAdapter.OnCardItemClickListener onCardClick) {
        this.context = context;
        this.anchor = anchor;
        this.root = LayoutInflater.from(context).inflate(R.layout.dialog_keyword_card_list, null);
        this.tvTitle = root.findViewById(R.id.tv_keyword_card_title);
        this.panelWidthPx = dp2px(PANEL_WIDTH_DP);

        RecyclerView rv = root.findViewById(R.id.rv_keyword_card_list);
        rv.setLayoutManager(new LinearLayoutManager(context));
        this.adapter = new DeckCardAdapter(imageLoader, card -> {
            // 条目点击刷新左侧详情面板（外部回调），面板保持打开以便继续链式点关键词
            if (onCardClick != null) onCardClick.onCardClick(card);
        });
        adapter.setCards(cards);
        rv.setAdapter(adapter);

        // 标题「数量-关键词」：数量前置，避免关键词过长把命中数挤出可视区（收缩横条标题同步）
        String title = (cards == null ? 0 : cards.size()) + "-" + (keyword == null ? "" : keyword);
        tvTitle.setText(title);

        root.findViewById(R.id.btn_keyword_card_close).setOnClickListener(v -> dismiss());

        // 非模态 + 高度自适应（WRAP_CONTENT：结果少时收缩到最后一张卡）
        popupWindow = new PopupWindow(root, panelWidthPx, ViewGroup.LayoutParams.WRAP_CONTENT, false);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setOutsideTouchable(true);
        popupWindow.setFocusable(false);

        // 复用 DraggablePopupHelper：可拖拽移动并记忆位置、旋转按新区域重排重居中
        draggableHelper = new DraggablePopupHelper(context, DIALOG_ID);
        draggableHelper.setupDraggablePopup(popupWindow, root,
                panelWidthPx, ViewGroup.LayoutParams.WRAP_CONTENT);
        // 收缩模式：点内容左上角「▼」把手缩为与聊天输入框同高的底部横条（「▲」+标题），点横条恢复
        DraggablePopupHelper.enableCollapse(popupWindow, title);
    }

    /**
     * 显示：先把内容贴到 layout_game_right（anchor）区域**左缘**、区域内垂直居中，再由
     * DraggablePopupHelper 落到全窗口层显示。后续可拖拽移动（位置持久化）、点左上角把手收缩为底部
     * 横条；横竖屏切换时弹窗不隐藏，旋转重排/重新贴左缘由
     * {@code DraggablePopupHelper.relayoutActivePopupsForOrientation}（Activity 旋转重建末尾调用）按 dialogId 统一处理。
     */
    public void show() {
        if (popupWindow == null || popupWindow.isShowing() || anchor == null) return;
        // 先清除历史拖拽记忆位：否则 showPopup 会用 NO_GRAVITY 把铺满整屏的层平移到上次拖拽位置，
        // 与 dockPopupInRegionLeft 按 margin 的贴左缘叠加，导致永远不贴 layout_game_right 左缘
        draggableHelper.clearSavedPosition();
        DraggablePopupHelper.dockPopupInRegionLeft(popupWindow, anchor);
        draggableHelper.showPopup(popupWindow, anchor);
    }

    public void dismiss() {
        if (popupWindow != null && popupWindow.isShowing()) {
            popupWindow.dismiss();
        }
    }

    public boolean isShowing() {
        return popupWindow != null && popupWindow.isShowing();
    }

    private int dp2px(float dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density + 0.5f);
    }

    // === 关键词命中集合查询（语义与 CardDetail.queryList / queryable 对齐） ===

    /**
     * 按关键词查询命中卡片：字段码匹配 + 卡名/描述包含关键词，结果去重。
     * 与 {@code CardDetail.queryList} 一致，用于详情描述高亮与列表展示同源。
     */
    public static List<Card> queryCardsByKeyword(String keyword) {
        List<Card> results = new ArrayList<>();
        if (keyword == null || keyword.isEmpty()) return results;
        StringManager stringManager = DataManager.get().getStringManager();
        SparseArray<Card> cards = DataManager.get().getCardManager().getAllCards();
        if (cards == null) return results;
        long setcode = stringManager.getSetCode(keyword, true);
        Set<Card> matchingCards = new HashSet<>();
        List<Long> cardInfoSetCodes = new ArrayList<>();
        for (int i = 0; i < cards.size(); i++) {
            Card card = cards.valueAt(i);
            if (card.Name == null && card.Desc == null) continue;
            cardInfoSetCodes.clear();
            for (long setCode : card.getSetCode()) {
                if (setCode > 0) cardInfoSetCodes.add(setCode);
            }
            if (setcode > 0 && cardInfoSetCodes.contains(setcode)) {
                matchingCards.add(card);
            }
            if ((card.Name != null && card.Name.contains(keyword))
                    || (card.Desc != null && card.Desc.contains(keyword))) {
                matchingCards.add(card);
            }
        }
        results.addAll(matchingCards);
        return results;
    }

    /**
     * 关键词是否「可查询」：无命中或唯一命中即当前卡自身时返回 false（描述高亮显示为白色不可点），
     * 其余返回 true（显示为蓝色可点弹列表）。与 {@code CardDetail.queryable} 一致。
     */
    public static boolean isQueryable(String keyword, Card current) {
        List<Card> matchingCards = queryCardsByKeyword(keyword);
        if (matchingCards.isEmpty()) {
            return false;
        } else if (matchingCards.size() == 1) {
            Card matchedCard = matchingCards.get(0);
            return current == null || !current.equals(matchedCard);
        }
        return true;
    }
}
