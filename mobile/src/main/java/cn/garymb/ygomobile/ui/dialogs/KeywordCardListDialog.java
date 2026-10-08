package cn.garymb.ygomobile.ui.dialogs;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import cn.garymb.ygomobile.game.DeckCardAdapter;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.utils.DialogScale;
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
 * <p>高度解算：面板始终单列，总高由 {@code layout_game_right} 的**区域高**为上限解算——
 * 命中条目全部装得下时收缩到内容总高（一屏完整显示、无需滚动），装不下时限幅到区域可用高、
 * 差额交由 RecyclerView 内部滚动。内容因此恒完整落在区域内，不会溢出窗口下沿而被裁掉
 *（旧实现用 WRAP_CONTENT 交由整窗高测量，竖屏区域只占 2/3 屏高时列表底部被裁、
 * 滚到最底也看不到最后一张卡）；横竖屏切换按新视图树的区域高重解。
 *
 * <p>关键词命中集合的查询语义与 {@code CardDetail.queryList/queryable} 一致：
 * 字段码匹配 + 卡名/描述包含关键词，全部命中卡纵向列出（含当前卡自身）。
 */
public class KeywordCardListDialog {

    /** 面板宽（dp）：容纳小卡图 + 卡名/信息/攻守文本，与卡组编辑搜索结果 item 观感一致 */
    private static final float PANEL_WIDTH_DP = 145f;

    /** 面板与 {@code layout_game_right} 上下边缘之间保留的安全间隙（dp），避免贴住血条与底部按钮行 */
    private static final float REGION_SAFE_GAP_DP = 2f;

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
    private final RecyclerView recyclerView;
    /** 标题行（tvTitle 的父容器）：其实测高与根上下内边距合计为面板「非列表」开销 */
    private final View titleRow;
    /** 内容根左右内边距之和（px）：条目与标题行的可用宽 = 面板宽 - 该值 */
    private final int rootPadH;
    /** 内容根上下内边距之和（px） */
    private final int rootPadV;
    /** 条目可用宽（px）：解算条目/标题行实测高时统一以此限定宽度 */
    private final int innerWidthPx;
    /** 命中卡片数：面板高解算依据（条目布局定高，故总高 = 条目高 × 数量） */
    private final int cardCount;
    /** 单条目实测高（px）：-1 表示尚未测量；条目高只随 density 变化，测一次后复用 */
    private int itemHeightPx = -1;

    public KeywordCardListDialog(Context context, ImageLoader imageLoader, View anchor,
                                 List<Card> cards, String keyword,
                                 DeckCardAdapter.OnCardItemClickListener onCardClick) {
        this.context = context;
        this.anchor = anchor;
        this.root = LayoutInflater.from(context).inflate(R.layout.dialog_keyword_card_list, null);
        this.tvTitle = root.findViewById(R.id.tv_keyword_card_title);
        this.titleRow = (View) tvTitle.getParent();
        this.panelWidthPx = dp2px(PANEL_WIDTH_DP);
        this.rootPadH = root.getPaddingLeft() + root.getPaddingRight();
        this.rootPadV = root.getPaddingTop() + root.getPaddingBottom();
        this.innerWidthPx = Math.max(1, panelWidthPx - rootPadH);
        this.cardCount = cards == null ? 0 : cards.size();

        this.recyclerView = root.findViewById(R.id.rv_keyword_card_list);
        recyclerView.setLayoutManager(new LinearLayoutManager(context));
        this.adapter = new DeckCardAdapter(imageLoader, card -> {
            // 条目点击刷新左侧详情面板（外部回调），面板保持打开以便继续链式点关键词
            if (onCardClick != null) onCardClick.onCardClick(card);
        });
        adapter.setCards(cards);
        recyclerView.setAdapter(adapter);

        // 标题“关键词（数量）”：关键词与数量分属两个 TextView——关键词过长在自身尾部省略（weight=1+ellipsize），
        // 数量 TextView 固定完整显示（不被挤出）；收缩横条为单串，沿用“关键词（数量）”由其自身省略
        String keywordText = keyword == null ? "" : keyword;
        String countText = "\uFF08" + (cards == null ? 0 : cards.size()) + "\uFF09";
        tvTitle.setText(keywordText);
        TextView tvCount = root.findViewById(R.id.tv_keyword_card_count);
        tvCount.setText(countText);
        String title = keywordText + countText;

        root.findViewById(R.id.btn_keyword_card_close).setOnClickListener(v -> dismiss());

        // 非模态 + 高度按区域可用高解算（见 {@link #resolvePanelHeightPx}）：结果少时收缩到最后一张卡，
        // 结果多时不超出 layout_game_right，剩余条目交由列表内部滚动
        int panelHeightPx = resolvePanelHeightPx();
        popupWindow = new PopupWindow(root, panelWidthPx, panelHeightPx, false);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setOutsideTouchable(true);
        popupWindow.setFocusable(false);

        // 复用 DraggablePopupHelper：可拖拽移动并记忆位置、旋转按新区域重排重居中
        draggableHelper = new DraggablePopupHelper(context, DIALOG_ID);
        draggableHelper.setupDraggablePopup(popupWindow, root,
                panelWidthPx, panelHeightPx);
        // 面板高是「区域高」的函数而默认重排只重解设计宽：注册自定义旋转重排，重解高后重新贴区域左缘
        draggableHelper.registerOrientationRelayout(popupWindow, this::applyOrientationRelayout);
        // 收缩模式：点标题左侧「▼」把手缩为与聊天输入框同高的底部横条（「▲」+标题），点横条恢复
        DraggablePopupHelper.enableCollapse(popupWindow, tvTitle, title);
    }

    /**
     * 显示：先把内容贴到 layout_game_right（anchor）区域**左缘**、区域内垂直居中，再由
     * DraggablePopupHelper 落到全窗口层显示。面板高在显示前按当前区域高重解一次（区域尚未布局
     * 时挂布局监听补解），使内容完整落在区域内。后续可拖拽移动（位置持久化）、点左上角把手收缩为
     * 底部横条；横竖屏切换时弹窗不隐藏，旋转重排/重新贴左缘由
     * {@code DraggablePopupHelper.relayoutActivePopupsForOrientation}（Activity 旋转重建末尾调用）经
     * {@link #applyOrientationRelayout} 按新区域高与本 dialogId 统一处理。
     */
    public void show() {
        if (popupWindow == null || popupWindow.isShowing() || anchor == null) return;
        // 先清除历史拖拽记忆位：否则 showPopup 会用 NO_GRAVITY 把铺满整屏的层平移到上次拖拽位置，
        // 与 dockPopupInRegionLeft 按 margin 的贴左缘叠加，导致永远不贴 layout_game_right 左缘
        draggableHelper.clearSavedPosition();
        // 一律取当前视图树里的区域实例：旋转重建后 final anchor 已脱离视图树，其高/坐标不再可信
        View region = currentRegion();
        // 区域尚未布局时先按屏高兜底显示，并挂一次性布局监听待区域高就绪后重解（同帧取不到真实高度）
        schedulePanelHeight();
        DraggablePopupHelper.dockPopupInRegionLeft(popupWindow, region != null ? region : anchor);
        draggableHelper.showPopup(popupWindow, region != null ? region : anchor);
    }

    /**
     * 旋转重排：先按新视图树的区域重新贴左缘（{@link DraggablePopupHelper} 默认重排已交由本实现处
     * 全权处理），再按新区域高重解面板高——竖屏 {@code layout_game_right} 只占下方 2/3 屏高、
     * 横屏为满屏高，不重解会使竖屏建立的列表转横屏后高于区域被裁。
     */
    private void applyOrientationRelayout() {
        View region = currentRegion();
        if (popupWindow != null && region != null) {
            DraggablePopupHelper.dockPopupInRegionLeft(popupWindow, region);
        }
        schedulePanelHeight();
    }

    /**
     * 区域已布局则直接重解面板高，否则挂一次性全局布局监听待布局完成再解算：
     * setContentView 重建视图树的同帧区域宽高为 0，直接取高会退化按屏高解算而与区域不一致。
     */
    private void schedulePanelHeight() {
        final View region = currentRegion();
        if (region == null) return;
        if (region.getHeight() > 0) {
            applyPanelHeight(resolvePanelHeightPx());
            return;
        }
        region.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        region.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        applyPanelHeight(resolvePanelHeightPx());
                    }
                });
    }

    /** 把解算出的面板高写回包装层内的内容根（宽恒为单列设计宽，不随方向变化） */
    private void applyPanelHeight(int heightPx) {
        ViewGroup.LayoutParams raw = root.getLayoutParams();
        if (!(raw instanceof FrameLayout.LayoutParams)) return;
        FrameLayout.LayoutParams flp = (FrameLayout.LayoutParams) raw;
        if (flp.width == panelWidthPx && flp.height == heightPx) return;
        flp.width = panelWidthPx;
        flp.height = heightPx;
        root.setLayoutParams(flp);
        root.requestLayout();
    }

    /**
     * 解算面板总高：标题行 + 全部条目总高，且不超过 {@code layout_game_right} 内留给面板的高度。
     * 全部条目装得下时面板收缩到内容总高（一屏完整显示、无需滚动）；装不下时限幅到区域可用高，
     * 列表可视区恰为剩余高度，因而滚到最底一定能看到最后一张卡。
     */
    private int resolvePanelHeightPx() {
        int chrome = rootPadV + measureViewHeight(titleRow);
        int listH = measureItemHeightPx() * Math.max(0, cardCount);
        int avail = regionAvailableHeightPx();
        if (avail > 0) {
            listH = Math.min(listH, Math.max(0, avail - chrome));
        }
        return chrome + listH;
    }

    /** 区域内留给面板的高度上限：区域高减去上下安全间隙；区域暂不可得时退用实时屏高 */
    private int regionAvailableHeightPx() {
        View region = currentRegion();
        int safe = 2 * dp2px(REGION_SAFE_GAP_DP);
        if (region != null && region.getHeight() > 0) {
            return Math.max(dp2px(24), region.getHeight() - safe);
        }
        return Math.max(dp2px(24), DialogScale.screenMetrics(context).heightPixels - safe);
    }

    /** 单条目实测高（含 item 内边距）：按面板内宽限定宽度、高度不限，测一次后复用 */
    private int measureItemHeightPx() {
        if (itemHeightPx > 0) return itemHeightPx;
        View probe = LayoutInflater.from(context)
                .inflate(R.layout.item_deck_card_horizontal, recyclerView, false);
        itemHeightPx = measureViewHeight(probe);
        return itemHeightPx;
    }

    /** 以面板内宽实测视图高（标题行与条目根均为 match_parent 宽 + wrap_content 高） */
    private int measureViewHeight(View view) {
        view.measure(View.MeasureSpec.makeMeasureSpec(innerWidthPx, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        return Math.max(1, view.getMeasuredHeight());
    }

    /**
     * 当前视图树中的 {@code layout_game_right}：旋转重建后 final anchor 指向已废弃的旧实例，
     * 区域高与贴左缘坐标一律从 Activity 重新解析，解析不到才退回 anchor。
     * context 可能是 DialogScale/主题包过的 {@link android.content.ContextWrapper}，沿链取 Activity。
     */
    private View currentRegion() {
        Context ctx = context;
        while (ctx instanceof android.content.ContextWrapper) {
            if (ctx instanceof Activity) break;
            ctx = ((android.content.ContextWrapper) ctx).getBaseContext();
        }
        if (ctx instanceof Activity) {
            Activity act = (Activity) ctx;
            if (!act.isFinishing() && !act.isDestroyed()) {
                View region = act.findViewById(R.id.layout_game_right);
                if (region != null) return region;
            }
        }
        return anchor;
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
     *
     * <p>性能：卡表在一次决斗内静态不变，而卡详描述里的高亮关键词高度重复（同一召唤条件/
     * 字段名在多张卡间反复出现），故按关键词缓存命中结果，避免每次卡详绑定都对全卡表重复
     * 扫描（原本会在点击卡片的同一 UI 线程消息里阻塞命令菜单弹出）。返回结果列表的拷贝，
     * 使调用方（如 openKeywordList 会排序）可自由修改而不污染缓存。
     */
    private static final Map<String, List<Card>> KEYWORD_QUERY_CACHE = new HashMap<>();

    public static List<Card> queryCardsByKeyword(String keyword) {
        if (keyword == null || keyword.isEmpty()) return new ArrayList<>();
        List<Card> cached = KEYWORD_QUERY_CACHE.get(keyword);
        if (cached != null) return new ArrayList<>(cached);
        List<Card> results = new ArrayList<>();
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
        KEYWORD_QUERY_CACHE.put(keyword, results);
        return new ArrayList<>(results);
    }

    /** 卡表重新加载（换卡包/数据热重载）时清空关键词命中缓存，避免返回过期结果。 */
    public static void clearKeywordQueryCache() {
        KEYWORD_QUERY_CACHE.clear();
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
