package cn.garymb.ygomobile.utils;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import cn.garymb.ygomobile.lite.R;

/**
 * 对话框收缩模式：把弹窗临时缩小成贴屏幕底部、与聊天输入框同高同位的横条，
 * 横条保持对话框原本的宽度、水平位置与背景贴图，不再拉成整屏宽长条；
 * 供玩家确认整个场地（横条左侧为「▲」向上箭头图标 + 标题，点击横条恢复原始尺寸）；
 * 展开态在内容左上角提供「▼」收缩把手，点击即收缩。
 *
 * 本项目的弹窗有两种架构，均复用本类的静态工厂与几何解算：
 * 1) 经 {@link DraggablePopupHelper#setupDraggablePopup} 包装为全窗口覆盖层的弹窗
 *    （宣言属性/种族/数字/卡片、选卡、卡片确认、是/否询问）：横条与把手作为
 *    包装层（DragFrameLayout）的额外子视图，靠显隐切换实现收缩；
 * 2) 内容尺寸的普通弹窗（选项、表示形式）：用本类实例壳层包裹内容，
 *    收缩时通过 popup.update 把窗口整体 resize 为底部横条。
 */
public class CollapsiblePopupShell {

    /** 横条内标题 TextView 的 tag，供 {@link #setCollapseBarTitle} 定位 */
    private static final String TAG_BAR_TITLE = "collapse_bar_title";
    /** 聊天输入框不可见时的横条兜底高度（dp） */
    private static final int FALLBACK_BAR_HEIGHT_DP = 40;
    /** 横条最小高度（dp），保证可点击目标不小于舒适触控尺寸 */
    private static final int MIN_BAR_HEIGHT_DP = 28;

    /**
     * 收缩横条：圆角条，左侧「▲」向上箭头图标 + 标题文本，点击恢复原始尺寸。
     * 实际使用时由 {@link #styleBarWithDialogContent} 叠加对话框内容根的背景贴图
     * 与左右内边距，本方法的程序化圆角条仅作内容根无背景时的兜底。
     * 高度与底部位置由调用方按 {@link #barMetrics} 结果设置（与聊天输入框同高同位）。
     */
    public static LinearLayout createCollapseBar(Context context, String title) {
        LinearLayout bar = new LinearLayout(context);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xE0303030);
        bg.setCornerRadius(dp(context, 6));
        bg.setStroke(dp(context, 1), 0xFF7A7A7A);
        bar.setBackground(bg);
        bar.setClickable(true);
        int padH = dp(context, 8);
        bar.setPadding(padH, 0, padH, 0);

        TextView arrow = new TextView(context);
        arrow.setText("▲");
        arrow.setTextColor(Color.WHITE);
        arrow.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        arrow.setGravity(Gravity.CENTER);
        bar.addView(arrow, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.MATCH_PARENT));

        TextView tvTitle = new TextView(context);
        tvTitle.setTag(TAG_BAR_TITLE);
        tvTitle.setText(title == null ? "" : title);
        tvTitle.setTextColor(Color.WHITE);
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tvTitle.setTypeface(Typeface.DEFAULT_BOLD);
        tvTitle.setSingleLine(true);
        tvTitle.setEllipsize(TextUtils.TruncateAt.END);
        tvTitle.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tlp.leftMargin = dp(context, 6);
        bar.addView(tvTitle, tlp);
        return bar;
    }

    /** 展开态收缩把手：「▼」向下箭头小按钮，贴内容左上角，点击收缩为横条 */
    public static TextView createCollapseHandle(Context context) {
        TextView handle = new TextView(context);
        handle.setText("▼");
        handle.setTextColor(Color.WHITE);
        handle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        handle.setGravity(Gravity.CENTER);
        handle.setMinWidth(dp(context, 24));
        handle.setMinHeight(dp(context, 20));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xCC303030);
        bg.setCornerRadius(dp(context, 4));
        bg.setStroke(dp(context, 1), 0xFF7A7A7A);
        handle.setBackground(bg);
        handle.setClickable(true);
        int padH = dp(context, 4);
        handle.setPadding(padH, 0, padH, 0);
        return handle;
    }

    /** 更新横条标题（选卡/卡片确认等动态标题弹窗在 setText 后同步调用） */
    public static void setCollapseBarTitle(View bar, String title) {
        if (!(bar instanceof ViewGroup)) return;
        ViewGroup group = (ViewGroup) bar;
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (TAG_BAR_TITLE.equals(child.getTag()) && child instanceof TextView) {
                ((TextView) child).setText(title == null ? "" : title);
                return;
            }
        }
    }

    /** 收缩横条复用对话框内容根的背景贴图与左右内边距：各 dialog 背景来自其布局
     *  android:background（如 sdialogl/window2s），运行时从 content.getBackground() 克隆
     *  一份独立实例（避免两视图共享同一 Drawable 造成 bounds 互串）；内容根无背景时
     *  保留默认圆角半透明条 */
    public static void styleBarWithDialogContent(View bar, View content) {
        if (bar == null || content == null) return;
        Drawable bg = content.getBackground();
        if (bg != null) {
            Drawable.ConstantState st = bg.getConstantState();
            bar.setBackground(st != null ? st.newDrawable() : bg);
        }
        bar.setPadding(content.getPaddingLeft(), 0, content.getPaddingRight(), 0);
    }

    /**
     * 横条布局几何：{窗口宽, 窗口高, 横条高, 横条底边与窗口底边的间隙}（均为 px，
     * 前三项与 popup.update / topMargin 直接使用）。聊天输入框（et_chat_input）
     * 可见时与其同高、底边对齐；不可见时兜底固定高度贴窗口底部。
     */
    public static int[] barMetrics(Context context) {
        Activity activity = resolveActivity(context);
        View decor = activity != null ? activity.getWindow().getDecorView() : null;
        int winW = decor != null && decor.getWidth() > 0
                ? decor.getWidth() : context.getResources().getDisplayMetrics().widthPixels;
        int winH = decor != null && decor.getHeight() > 0
                ? decor.getHeight() : context.getResources().getDisplayMetrics().heightPixels;
        int barH = dp(context, FALLBACK_BAR_HEIGHT_DP);
        int gap = 0;
        View chat = activity != null ? activity.findViewById(R.id.et_chat_input) : null;
        if (chat != null && chat.isShown() && chat.getHeight() > 0) {
            barH = Math.max(chat.getHeight(), dp(context, MIN_BAR_HEIGHT_DP));
            int[] loc = new int[2];
            chat.getLocationInWindow(loc);
            gap = winH - (loc[1] + chat.getHeight());
            if (gap < 0) gap = 0;
        }
        return new int[]{winW, winH, barH, gap};
    }

    // ══════════════════════════════════════════════════════════════════════
    // 普通内容尺寸弹窗（选项 / 表示形式）的壳层控制器：
    // shell = FrameLayout{ original, bar(GONE), handle }，收缩/恢复经 popup.update
    // 在「展开几何」与「底部横条几何」之间切换窗口尺寸与位置。
    // ══════════════════════════════════════════════════════════════════════

    private final Context context;
    private final FrameLayout shell;
    private final View original;
    private final View bar;
    private final View handle;
    private PopupWindow popup;
    /** 展开态几何（由对话框 showAtLocation 时已解算好的 x/y/宽/高传入，窗口坐标） */
    private boolean geometrySaved;
    private int savedX, savedY, savedW, savedH;
    private boolean collapsed;

    /**
     * @param originalContent 弹窗原内容根
     * @param title           横条标题（空时兜底「点击展开」）
     * @param matchParentWidth 原弹窗宽度为固定值时传 true（内容填满窗口宽）；
     *                         原弹窗为 WRAP 宽时传 false，保持包裹测量
     */
    public CollapsiblePopupShell(Context context, View originalContent,
                                 String title, boolean matchParentWidth) {
        this.context = context;
        this.original = originalContent;
        String barTitle = (title == null || title.isEmpty()) ? "点击展开" : title;

        shell = new FrameLayout(context);
        shell.addView(originalContent, new FrameLayout.LayoutParams(
                matchParentWidth ? ViewGroup.LayoutParams.MATCH_PARENT
                        : ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        bar = createCollapseBar(context, barTitle);
        styleBarWithDialogContent(bar, originalContent);
        bar.setVisibility(View.GONE);
        bar.setOnClickListener(v -> expand());
        shell.addView(bar, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        handle = createCollapseHandle(context);
        handle.setOnClickListener(v -> collapse());
        FrameLayout.LayoutParams hlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START);
        hlp.leftMargin = dp(context, 2);
        hlp.topMargin = dp(context, 2);
        shell.addView(handle, hlp);
    }

    /** 作为 PopupWindow 内容视图的壳层根 */
    public View getView() {
        return shell;
    }

    /** 绑定弹窗窗口（须在 collapse/expand 前调用） */
    public void attachPopup(PopupWindow popupWindow) {
        this.popup = popupWindow;
    }

    /** 记录展开态几何：showAtLocation 定位成功后调用，把手自此可收缩 */
    public void rememberExpanded(int x, int y, int w, int h) {
        savedX = x;
        savedY = y;
        savedW = w;
        savedH = h;
        geometrySaved = true;
    }

    /** 收缩：窗口 resize 为与聊天输入框同高同位的横条，内容隐藏；
     *  横条保持展开态的弹窗原宽与水平位置（不再拉伸整屏宽） */
    public void collapse() {
        if (collapsed || popup == null || !popup.isShowing() || !geometrySaved) return;
        int[] m = barMetrics(context);
        int bw = savedW > 0 ? savedW : m[0];
        int by = m[1] - m[3] - m[2];
        if (by < 0) by = 0;
        try {
            popup.update(savedX, by, bw, m[2]);
        } catch (Exception ignored) {
            return;
        }
        applyState(true);
    }

    /** 恢复：窗口回到展开几何，内容重新显示 */
    public void expand() {
        if (!collapsed || popup == null || !popup.isShowing()) return;
        try {
            popup.update(savedX, savedY, savedW, savedH);
        } catch (Exception ignored) {
            return;
        }
        applyState(false);
    }

    private void applyState(boolean doCollapse) {
        collapsed = doCollapse;
        original.setVisibility(doCollapse ? View.GONE : View.VISIBLE);
        handle.setVisibility(doCollapse ? View.GONE : View.VISIBLE);
        bar.setVisibility(doCollapse ? View.VISIBLE : View.GONE);
    }

    public boolean isCollapsed() {
        return collapsed;
    }

    private static Activity resolveActivity(Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) {
                Activity activity = (Activity) context;
                if (activity.isFinishing() || activity.isDestroyed()) return null;
                return activity;
            }
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }

    static int dp(Context context, float value) {
        return DialogScale.dpToPx(context, value);
    }
}
