package cn.garymb.ygomobile.game.gamefield;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;

/**
 * 「持续适用中效果」下拉面板（用户新规格：点头像角上的金色感叹号 → 向下展开列表，
 * 行左为卡片图缩小版、行右为 cdb str 效果文字）。
 * <p>图层：必须走 PopupWindow——GameFieldView 是 {@code setZOrderOnTop(true)} 的
 * GLSurfaceView，GL 曲面合成在 Activity 窗口之上，作为 layout_game_right 子 View 的面板
 * 会被场地/卡片纹理遮挡（同 LP 变化浮字、分侧聊天行的图层约定）。
 * <p>归属（用户规格）：只显示被点击那一侧的条目，标题随半场取「我方/对方持续适用中的效果」；
 * 面板横向对齐该侧血条面板、纵向贴其下缘，条目按发动回合升序（整局级在前）。
 * <p>行点击（用户新规格）：转交 {@link OnCardClick}，由 GameTopInfoManager 按卡码合成临时卡
 * 走 ClientCard 展示态路径刷 carddetailpanel（带已打勾的序号段落标记）；面板不收起，便于连续点看。
 * <p>字号（用户新规格）：正文与标题固定 7sp（不随 HUD 系数放大，否则长句在窄面板里根本放不下），
 * 仅卡图缩略版随 HUD 系数等比。
 * <p>生命周期：由 {@link GameTopInfoManager} 持有单实例——徽标点击切换显隐，回合轮转/胜负/
 * 开局/撤回/换视角刷新时若该侧已无条目即自动收起，HUD 隐藏（{@link GameTopInfoManager#hide()}）
 * 时一并 dismiss 防窗口泄漏。
 * <p>刷新（用户新规格：刷新不再整窗重建）：同一侧的窗口还在展示时，条目没变则完全不重绘，
 * 条目变了也只重建行并用 {@code PopupWindow.update} 改尺寸与位置，不 dismiss、不 new 窗口，
 * 并按重建前的滚动位置回贴（避免每笔发动/每次刷新都闪一下并把玩家滚到的位置归零）。
 * <p>缩放：字号/卡图/内边距全部乘 HUD 等比系数 f（与血条面板同源），平板与手机上占比恒定。
 */
public final class ApplyingEffectsPopup {

    /** 列表最大高度占 layout_game_right 高的比例（超出走 ScrollView 滚动，不盖住整个决斗场） */
    private static final float MAX_HEIGHT_FRACTION = 0.42f;
    /** 卡图缩略版基准尺寸（dp，f=1）：按 177:254 原比例，随 HUD 系数等比放大 */
    private static final float CARD_W_DP = 20f;
    private static final float CARD_H_DP = 29f;
    /** 正文/标题字号（sp）：固定 7sp 不随 HUD 缩放（用户新规格：列表文字放大一号） */
    private static final float TEXT_SIZE_SP = 7f;
    /** 行内文字左内边距基准（dp，随 HUD 系数） */
    private static final float ROW_TEXT_PAD_DP = 5f;

    /** 行点击回调（用户新规格：点列表上的卡 → carddetailpanel 显示该卡详情与已标记序号文本） */
    public interface OnCardClick {
        void onCardClick(int code, int side);
    }

    private final Activity activity;
    private final ImageLoader imageLoader;
    private PopupWindow window;
    private View content;
    private TextView tvTitle;
    private LinearLayout rows;
    private ScrollView scroll;
    /** 当前展开的半场（-1=未展开）：再次点击同侧徽标收起，点击另一侧徽标切换归属 */
    private int shownSide = -1;
    /** 上一次落地到窗口里的条目指纹与缩放/宽度基准（原地刷新据此判断要不要重绘，见 {@link #show}） */
    private String shownKeys = "";
    private float shownScale;
    private int shownWidth;
    /** 行点击回调（由 GameTopInfoManager 注入） */
    private OnCardClick onCardClick;

    public ApplyingEffectsPopup(Activity activity, ImageLoader imageLoader) {
        this.activity = activity;
        this.imageLoader = imageLoader;
    }

    /** 注入行点击回调（展开列表时点击卡片 → 卡详面板显示该卡） */
    public void setOnCardClick(OnCardClick listener) {
        onCardClick = listener;
    }

    /** 当前是否正在展示指定半场的列表（徽标点击据此决定收起还是展开） */
    public boolean isShowing(int side) {
        return window != null && window.isShowing() && shownSide == side;
    }

    public boolean isShowing() {
        return window != null && window.isShowing();
    }

    /**
     * 展开（或原地刷新）指定半场的持续适用中效果列表。
     *
     * @param anchor   定位锚（layout_top_info）；面板贴其下缘
     * @param panel    该半场的血条面板（layout_player_panel / layout_opponent_panel），决定横向区间
     * @param side     本地半场索引（0=我方 1=对方）
     * @param items    该侧条目（{@link ContinuousEffectApplier#forSide}）
     * @param hudScale HUD 等比系数
     */
    public void show(View anchor, View panel, int side, List<ContinuousEffectApplier.Applying> items,
                     float hudScale) {
        if (anchor == null || panel == null || items == null || items.isEmpty()) {
            dismiss();
            return;
        }
        if (activity.isFinishing() || activity.isDestroyed()) return;
        int[] anchorLoc = new int[2];
        int[] panelLoc = new int[2];
        anchor.getLocationInWindow(anchorLoc);
        panel.getLocationInWindow(panelLoc);
        int width = panel.getWidth();
        int avail = anchor.getRootView() != null ? anchor.getRootView().getHeight() : 0;
        if (width <= 0 || avail <= 0) return;
        float f = hudScale <= 0 ? 1f : hudScale;
        String key = itemsKey(items);
        // 用户新规格：刷新不再整窗重建——同侧窗口还在且条目与缩放都没变就一律不动（连重绘也不做，
        // 不会打断滚动）；条目变了只重建行 + update 尺寸；换侧/首次展开（或 HUD 系数变了）才重建窗口
        boolean live = window != null && window.isShowing() && shownSide == side;
        if (live && key.equals(shownKeys) && Math.abs(f - shownScale) < 0.001f && width == shownWidth) return;
        ensureContent();
        int scrollY = live && scroll != null ? scroll.getScrollY() : 0;
        // 字号不乘 f：硬 7sp（用户新规格：长句要在窄面板里放得下）
        tvTitle.setText(side == 0 ? R.string.applying_effects_self : R.string.applying_effects_opponent);
        tvTitle.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, TEXT_SIZE_SP);
        buildRows(items, f);
        content.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int height = Math.min(content.getMeasuredHeight(), Math.round(avail * MAX_HEIGHT_FRACTION));
        int x = panelLoc[0];
        int y = anchorLoc[1] + anchor.getHeight();
        if (live) {
            window.update(x, y, width, height);
            shownKeys = key;
            shownScale = f;
            shownWidth = width;
            // 行重建会连带重排版面，把玩家原本滚到的位置在下一帧贴回去（新内容更短时 ScrollView 自行夹取）
            if (scroll != null && scrollY > 0) {
                final int restore = scrollY;
                scroll.post(() -> scroll.scrollTo(0, restore));
            }
            return;
        }
        dismiss();
        window = new PopupWindow(content, width, height);
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.setOutsideTouchable(true);
        window.setTouchable(true);
        window.setFocusable(false);
        window.setOnDismissListener(new PopupWindow.OnDismissListener() {
            @Override
            public void onDismiss() {
                shownSide = -1;
            }
        });
        try {
            window.showAtLocation(anchor, android.view.Gravity.NO_GRAVITY, x, y);
            shownSide = side;
            shownKeys = key;
            shownScale = f;
            shownWidth = width;
        } catch (Exception ignored) {
            // 窗口 token 失效（Activity 正在销毁/旋转中）：本次展开放弃，下一次刷新重建
            window = null;
            shownSide = -1;
            shownKeys = "";
        }
    }

    /** 按条目重建列表行（卡图缩略版随 HUD 系数、正文固定 7sp），整行可点转卡详 */
    private void buildRows(List<ContinuousEffectApplier.Applying> items, float f) {
        rows.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(activity);
        float density = activity.getResources().getDisplayMetrics().density;
        for (ContinuousEffectApplier.Applying item : items) {
            View row = inflater.inflate(R.layout.item_applying_effect, rows, false);
            ImageView iv = row.findViewById(R.id.iv_applying_effect_card);
            TextView tv = row.findViewById(R.id.tv_applying_effect_text);
            int w = Math.round(CARD_W_DP * f * density);
            int h = Math.round(CARD_H_DP * f * density);
            ViewGroup.LayoutParams iLP = iv.getLayoutParams();
            iLP.width = w;
            iLP.height = h;
            iv.setLayoutParams(iLP);
            imageLoader.bindImage(iv, item.code, ImageLoader.Type.small);
            tv.setText(item.text);
            tv.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, TEXT_SIZE_SP);
            int pad = Math.round(ROW_TEXT_PAD_DP * f * density);
            tv.setPadding(pad, 0, pad, 0);
            // 整行可点：按卡码+半场回调 GameTopInfoManager 刷卡详（带已发动序号段落标记）；
            // 面板保持展开，玩家可连续点若干张卡对比
            final int clickedCode = item.code;
            final int clickedSide = item.side;
            row.setClickable(true);
            row.setOnClickListener(v -> {
                if (onCardClick != null) onCardClick.onCardClick(clickedCode, clickedSide);
            });
            rows.addView(row);
        }
    }

    /** 条目集合指纹（卡码×段落×半场×回合×整局/本回合×文本长度）：完全一致才允许跳过重绘 */
    private static String itemsKey(List<ContinuousEffectApplier.Applying> items) {
        StringBuilder sb = new StringBuilder();
        for (ContinuousEffectApplier.Applying it : items) {
            sb.append(it.code).append(':').append(it.offset).append(':')
                    .append(it.side).append(':').append(it.turn).append(':')
                    .append(it.wholeDuel ? 'D' : 'T').append(':')
                    .append(it.text == null ? 0 : it.text.length()).append(';');
        }
        return sb.toString();
    }

    /** 惰性构建面板骨架（横竖屏共用 layout_applying_effects，随本实例复用避免反复 inflate） */
    private void ensureContent() {
        if (content != null) return;
        content = LayoutInflater.from(activity).inflate(R.layout.layout_applying_effects, null);
        tvTitle = content.findViewById(R.id.tv_applying_title);
        rows = content.findViewById(R.id.layout_applying_rows);
        scroll = content.findViewById(R.id.scroll_applying_effects);
    }

    /** 收起并清除原地刷新基准（换侧/首次展开、该侧条目清空、HUD 隐藏与旋转重绑均经此） */
    public void dismiss() {
        shownSide = -1;
        shownKeys = "";
        shownScale = 0f;
        shownWidth = 0;
        if (window != null) {
            try {
                if (window.isShowing()) window.dismiss();
            } catch (Exception ignored) {
            }
            window = null;
        }
    }
}
