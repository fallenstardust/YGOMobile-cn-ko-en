package cn.garymb.ygomobile.utils;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.AbsListView;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.HorizontalScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.core.view.ScrollingView;
import androidx.core.widget.NestedScrollView;

import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.lite.R;

public class DraggablePopupHelper {
    private static final String PREF_NAME = "popup_positions";
    private static final String KEY_X = "_x";
    private static final String KEY_Y = "_y";
    private static final boolean ENABLE_DRAG = true;
    private static final int DRAG_THRESHOLD = 8;

    /** 收缩横条内标题 TextView 的 tag，供 {@link #setBarTitle} 定位 */
    private static final String TAG_BAR_TITLE = "collapse_bar_title";
    /** 聊天输入框不可见时收缩横条的兜底高度（dp） */
    private static final int FALLBACK_BAR_HEIGHT_DP = 40;
    /** 收缩横条最小高度（dp），保证可点击目标不小于舒适触控尺寸 */
    private static final int MIN_BAR_HEIGHT_DP = 28;

    /**
     * 已显示的拖拽弹窗层，按窗口层级由下到上排列（末尾 = 最上层）。
     * 这些弹窗都是铺满窗口的独立 PopupWindow，触摸只会送到最上层窗口，
     * 因此由最上层包装层统一做归属判定：命中下层弹窗则跨窗口转发，实现
     * 「手卡公开等下层弹窗的卡片仍可点击」与「点击的弹窗自动置顶」
     */
    private static final List<DragFrameLayout> ACTIVE_LAYERS = new ArrayList<>();

    private final Context context;
    private final SharedPreferences prefs;
    private final String dialogId;

    private int lastX = 0;
    private int lastY = 0;
    private boolean hasSavedPosition = false;

    public DraggablePopupHelper(Context context, String dialogId) {
        this.context = context;
        this.dialogId = dialogId;
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        loadPosition();
    }

    private void loadPosition() {
        lastX = prefs.getInt(dialogId + KEY_X, 0);
        lastY = prefs.getInt(dialogId + KEY_Y, 0);
        hasSavedPosition = prefs.contains(dialogId + KEY_X);
    }

    public void savePosition(int x, int y) {
        lastX = x;
        lastY = y;
        hasSavedPosition = true;
        prefs.edit()
            .putInt(dialogId + KEY_X, x)
            .putInt(dialogId + KEY_Y, y)
            .apply();
    }

    public void setupDraggablePopup(PopupWindow popupWindow, View contentView,
                                     int designW, int designH) {
        setupDraggablePopup(popupWindow, contentView, designW, designH, false);
    }

    /**
     * @param swapForPortrait 竖屏宽高比重排（供建主/局域网/单机/录像/玩家等待这类横屏设计为宽大于高的弹窗使用）：
     *                        竖屏宽铺满屏宽（与 Activity 同宽）且高与宽相等（正方形），转回横屏
     *                        恢复设计宽高比（宽大于高）；其余弹窗传 false 行为不变。
     */
    public void setupDraggablePopup(PopupWindow popupWindow, View contentView,
                                     int designW, int designH, boolean swapForPortrait) {
        if (!ENABLE_DRAG) return;

        ViewGroup originalParent = (ViewGroup) contentView.getParent();
        int index = -1;
        ViewGroup.LayoutParams lp = contentView.getLayoutParams();
        if (originalParent != null) {
            index = originalParent.indexOfChild(contentView);
            originalParent.removeView(contentView);
        }

        DragFrameLayout wrapper = new DragFrameLayout(
                contentView.getContext(), prefs, dialogId);
        // 包装层铺满窗口，内部对话框以 Gravity.CENTER 居中，内容区之外的触摸需转发给下层弹窗或 Activity 窗口，
        // 否则弹窗显示期间决斗场、双方手卡公开面板等下层 UI 无法响应点击
        wrapper.setHostPopup(popupWindow);
        wrapper.setPassThroughTarget(resolveActivityDecorView(contentView.getContext()));
        // 记录设计尺寸与创建时的等比系数：屏幕旋转后按新屏宽重新解算弹窗显示宽度、并按
        // 当前方向系数相对创建系数的比例重缩放弹窗（见 relayoutActivePopupsForOrientation），
        // 故此处入参语义为“设计（未限宽）尺寸”，由本方法统一按当前屏宽解算实际显示尺寸
        wrapper.setDesignSize(designW, designH, DialogScale.factor(contentView.getContext()));
        wrapper.setSwapForPortrait(swapForPortrait);

        int[] fitted = orientedFitSize(contentView.getContext(), designW, designH, swapForPortrait);
        FrameLayout.LayoutParams centerLp = new FrameLayout.LayoutParams(fitted[0], fitted[1]);
        centerLp.gravity = Gravity.CENTER;
        contentView.setLayoutParams(centerLp);
        wrapper.addView(contentView);

        if (originalParent != null) {
            originalParent.addView(wrapper, index, lp);
        } else {
            wrapper.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            wrapper.setClipChildren(false);
            popupWindow.setContentView(wrapper);
            popupWindow.setWidth(ViewGroup.LayoutParams.MATCH_PARENT);
            popupWindow.setHeight(ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }

    /**
     * 为经 {@link #setupDraggablePopup} 包装的弹窗注册自定义旋转重排逻辑：默认重排只按设计
     * 尺寸重解弹窗显示宽度，而选卡/卡片确认类弹窗宽度由 <code>layout_game_right</code> 区域
     * 实时解算并据此烘焙每张卡图尺寸，默认重排无法同步子视图尺寸，会导致横竖屏切换后卡片被裁剪。
     * 注册后旋转重排改由传入的 runnable 全权负责（重解宽度、重烘焙子视图、重新限宽与居中）。
     */
    public void registerOrientationRelayout(PopupWindow popupWindow, Runnable handler) {
        if (popupWindow == null || !(popupWindow.getContentView() instanceof DragFrameLayout)) {
            return;
        }
        ((DragFrameLayout) popupWindow.getContentView()).setCustomRelayout(handler);
    }

    /**
     * 为经 {@link #setupDraggablePopup} 包装、内容布局带 btn_dialog_collapse 收缩按钮的
     * dialog 启用收缩模式：点按钮把弹窗缩为贴屏幕底部、与聊天输入框同高同位、保持对话框
     * 原宽与原背景贴图的横条（横条左侧「▲」箭头 + 标题，点击横条恢复原始尺寸），供临时确认场地。
     * 横条与把手作为包装层（DragFrameLayout）的额外子视图，靠显隐切换实现收缩，
     * 收缩态窗口仍铺满全屏：横条以外触摸照旧透传，旋转重排照常按新屏尺寸复位。
     */
    public static void enableCollapse(PopupWindow popupWindow, String barTitle) {
        enableCollapse(popupWindow, null, barTitle);
    }

    /**
     * 启用收缩模式：内容布局声明了 btn_dialog_collapse 的 dialog 直接用它作收缩把手；
     * 未声明时创建程序化「▼」把手并嵌入 {@code titleView} 左侧（标题可见时）。
     * @param titleView 该 dialog 的标题视图（可空/可 GONE）：可见时程序化把手贴其左侧、颜色随标题；
     *                  为空或不可见时把手维持内容左上角悬浮
     * @param barTitle  收缩横条标题（无标题的 dialog 传其 message 文本）
     */
    public static void enableCollapse(PopupWindow popupWindow, View titleView, String barTitle) {
        if (popupWindow != null && popupWindow.getContentView() instanceof DragFrameLayout) {
            ((DragFrameLayout) popupWindow.getContentView()).makeCollapsible(titleView, barTitle);
        }
    }

    /** 同步弹窗显示期间的横条标题（选卡/卡片确认等动态标题弹窗在 setText 后调用） */
    public static void setCollapseBarTitle(PopupWindow popupWindow, String barTitle) {
        if (popupWindow != null && popupWindow.getContentView() instanceof DragFrameLayout) {
            ((DragFrameLayout) popupWindow.getContentView()).setCollapseTitle(barTitle);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // 收缩到底部横条的视图与几何辅助（供 DragFrameLayout 的收缩模式使用）：
    // 各 dialog 布局 XML 声明的 btn_dialog_collapse 作收缩把手，横条与聊天输入框
    // （et_chat_input）同高同位；几何解算一律按本文件 DragFrameLayout 的显隐切换方式。
    // ══════════════════════════════════════════════════════════════════════

    /**
     * 收缩横条：圆角条，左侧「▲」向上箭头图标 + 标题文本，点击恢复原始尺寸。
     * 实际使用时由 {@link #styleBarWithDialogContent} 叠加对话框内容根的背景贴图
     * 与左右内边距，本方法的程序化圆角条仅作内容根无背景时的兜底。
     * 高度与底部位置由调用方按 {@link #barMetrics} 结果设置（与聊天输入框同高同位）。
     */
    private static LinearLayout createCollapseBar(Context context, String title) {
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

        ImageView arrow = createArrowButton(context, R.drawable.baseline_keyboard_arrow_up_24);
        // 箭头仅作“按钮”外观的指示：不可点击，否则它会吞掉横条本身的恢复点击（整条可点即恢复）
        arrow.setClickable(false);
        arrow.setFocusable(false);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                dp(context, 22), LinearLayout.LayoutParams.MATCH_PARENT);
        // 箭头按钮始终维持正方形：横条高（贴聊天输入框）常小于 22dp 名义宽，固定宽会把
        // 「▲」左右压窄；宽度恒等于横条实际高度（宽=高 的正方形），图标居中不裁切
        bar.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, ob, olr) -> {
            int h = b - t;
            if (h > 0 && alp.width != h) {
                alp.width = h;
                arrow.setLayoutParams(alp);
            }
        });
        bar.addView(arrow, alp);

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

    /** 收缩态箭头按钮：selected_light 描边圆角底 + 指定箭头图标，图标居中自适应缩放 */
    private static ImageView createArrowButton(Context context, int iconRes) {
        ImageButton button = new ImageButton(context);
        button.setBackgroundResource(R.drawable.selected_light);
        button.setImageResource(iconRes);
        button.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int pad = dp(context, 2);
        button.setPadding(pad, pad, pad, pad);
        button.setMinimumWidth(0);
        button.setMinimumHeight(0);
        button.setAdjustViewBounds(true);
        return button;
    }

    /** 展开态收缩把手（仅内容未声明 btn_dialog_collapse 时程序化创建）：
     *  selected_light 描边圆角底 + 向下箭头图标，贴内容左上角为兜底位，
     *  有可见标题时由 {@link #positionHandleBesideTitle} 移到标题左侧 */
    private static ImageView createCollapseHandle(Context context) {
        ImageButton handle = (ImageButton) createArrowButton(
                context, R.drawable.baseline_keyboard_arrow_down_24);
        handle.setMinimumWidth(dp(context, 22));
        handle.setMinimumHeight(dp(context, 22));
        handle.setClickable(true);
        return handle;
    }

    /**
     * 把收缩把手贴到标题行左侧：水平贴内容可视左缘 + 小 inset，垂直与标题文字居中对齐。
     * handle 与 content 同处一个父层（包装层）坐标系，用 FrameLayout.LayoutParams 的
     * leftMargin/topMargin 定位；颜色跟随标题文字色以适配深浅背景。
     * titleView 不可见、非 TextView、或未布局（宽高为 0）时返回 false，调用方回退左上角。
     */
    private static boolean positionHandleBesideTitle(View handle, View content, View titleView) {
        if (handle == null || content == null || titleView == null) return false;
        if (!(handle instanceof ImageView)) return false;
        if (!(handle.getLayoutParams() instanceof FrameLayout.LayoutParams)) return false;
        ViewGroup parent = (ViewGroup) handle.getParent();
        if (parent == null) return false;
        if (titleView.getVisibility() != View.VISIBLE
                || titleView.getWidth() <= 0 || titleView.getHeight() <= 0
                || content.getWidth() <= 0) {
            return false;
        }
        FrameLayout.LayoutParams hlp = (FrameLayout.LayoutParams) handle.getLayoutParams();
        int inset = dp(handle.getContext(), 4);
        // 内容可视左缘（考虑缩放：pivot 默认中心）
        float sx = content.getScaleX();
        int contentVisLeft = Math.round(content.getLeft() - content.getWidth() * (sx - 1f) / 2f);
        // 标题中心 Y 换算到父层坐标，减去把手半高实现垂直居中
        int[] tvLoc = new int[2];
        int[] parentLoc = new int[2];
        titleView.getLocationInWindow(tvLoc);
        parent.getLocationInWindow(parentLoc);
        int titleCenterY = tvLoc[1] + titleView.getHeight() / 2 - parentLoc[1];
        int handleH = handle.getHeight() > 0 ? handle.getHeight() : dp(handle.getContext(), 22);
        // 钉在父层可视范围内：旋转/缩放过程中坐标快照可能瞬时不一致，不钉住会把把手推出屏外，
        // 表现为「▼」图标转屏后消失
        int maxLeft = Math.max(0, parent.getWidth() - handle.getWidth());
        int maxTop = Math.max(0, parent.getHeight() - handleH);
        hlp.leftMargin = Math.max(0, Math.min(maxLeft, Math.max(0, contentVisLeft) + inset));
        hlp.topMargin = Math.max(0, Math.min(maxTop, titleCenterY - handleH / 2));
        hlp.gravity = Gravity.TOP | Gravity.START;
        handle.setLayoutParams(hlp);
        // 箭头图标着色跟随标题文字色，适配深浅背景（图标本体为白色 tint）
        int titleColor = ((TextView) titleView).getCurrentTextColor();
        if (titleColor != 0) ((ImageView) handle).setColorFilter(titleColor);
        return true;
    }

    /** 更新横条标题（选卡/卡片确认等动态标题弹窗在 setText 后同步调用） */
    private static void setBarTitle(View bar, String title) {
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
    private static void styleBarWithDialogContent(View bar, View content) {
        if (bar == null || content == null) return;
        Drawable bg = content.getBackground();
        if (bg != null) {
            Drawable.ConstantState st = bg.getConstantState();
            bar.setBackground(st != null ? st.newDrawable() : bg);
        }
        bar.setPadding(content.getPaddingLeft(), 0, content.getPaddingRight(), 0);
    }

    /**
     * 横条布局几何：{窗口宽, 窗口高, 横条高, 横条底边与窗口底边的间隙}（均为 px）。
     * 聊天输入框（et_chat_input）可见时与其同高、底边对齐；不可见时兜底固定高度贴窗口底部。
     */
    private static int[] barMetrics(Context context) {
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

    /** dp 转 px（按弹窗创建时的 DialogScale 密度上下文换算） */
    private static int dp(Context context, float value) {
        return DialogScale.dpToPx(context, value);
    }

    public void setupDraggablePopup(PopupWindow popupWindow, View contentView, View handle) {
        if (!ENABLE_DRAG) return;
        final float[] downX = new float[1];
        final float[] downY = new float[1];
        final int[] lastPos = new int[2];
        final boolean[] dragging = new boolean[]{false};

        handle.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    downX[0] = event.getRawX();
                    downY[0] = event.getRawY();
                    int[] loc = new int[2];
                    contentView.getLocationOnScreen(loc);
                    lastPos[0] = loc[0];
                    lastPos[1] = loc[1];
                    dragging[0] = false;
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - downX[0];
                    float dy = event.getRawY() - downY[0];
                    if (!dragging[0]) {
                        if (Math.abs(dx) < DRAG_THRESHOLD && Math.abs(dy) < DRAG_THRESHOLD) {
                            return true;
                        }
                        dragging[0] = true;
                        v.performHapticFeedback(
                                android.view.HapticFeedbackConstants.LONG_PRESS);
                        downX[0] = event.getRawX();
                        downY[0] = event.getRawY();
                        return true;
                    }
                    downX[0] = event.getRawX();
                    downY[0] = event.getRawY();
                    lastPos[0] += (int) dx;
                    lastPos[1] += (int) dy;
                    popupWindow.update(lastPos[0], lastPos[1], -1, -1);
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (dragging[0]) {
                        savePosition(lastPos[0], lastPos[1]);
                    }
                    dragging[0] = false;
                    return true;
            }
            return false;
        });
    }

    /**
     * 从 Context 链解析 Activity 的 decorView，作为弹窗内容区之外触摸事件的转发目标；
     * 非 Activity 上下文或 Activity 已销毁时返回 null（退化为不转发）
     */
    private static View resolveActivityDecorView(Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) {
                Activity activity = (Activity) context;
                if (activity.isFinishing() || activity.isDestroyed()) return null;
                return activity.getWindow().getDecorView();
            }
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }

    /** 从 Context 链解析 Activity（供旋转重排按 id 重新查找重建后的居中区域） */
    private static Activity resolveActivity(Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) return (Activity) context;
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }

    /**
     * 按当前屏幕宽度解算弹窗实际显示尺寸：设计宽度超出屏宽时限为屏宽，
     * 具体高度（designH&gt;0）按原宽高比等比缩小；高度同样限幅：超出屏高时按宽高比
     * 整体缩回屏内（竖屏解算的设计尺寸转横屏后屏高只剩短边，避免弹窗超屏被放大
     * 到不合当前 activity 比例）；高度为 WRAP_CONTENT/MATCH_PARENT（&lt;=0）时不改高度；
     * 设计宽度 &lt;=0（如 MATCH_PARENT）原样返回。度量一律取实时显示度量（见
     * {@link DialogScale#screenMetrics}），不受弹窗携带的缩放上下文派生值影响；
     * 弹窗创建与屏幕旋转重排共用同一解算，保证两个方向下尺寸均正确。
     */
    public static int[] fitSizeToScreen(Context context, int designW, int designH) {
        if (designW <= 0) return new int[]{designW, designH};
        DisplayMetrics screen = DialogScale.screenMetrics(context);
        int w = designW;
        int h = designH;
        if (w > screen.widthPixels) {
            if (h > 0) h = (int) ((long) h * screen.widthPixels / w);
            w = screen.widthPixels;
        }
        if (h > screen.heightPixels) {
            w = (int) ((long) w * screen.heightPixels / h);
            if (w > screen.widthPixels) w = screen.widthPixels;
            h = screen.heightPixels;
        }
        return new int[]{w, h};
    }

    /** 当前显示是否为竖屏（高大于宽）：以实时显示度量为准，不受缩放上下文快照影响 */
    private static boolean isPortrait(Context context) {
        DisplayMetrics dm = DialogScale.screenMetrics(context);
        return dm.heightPixels > dm.widthPixels;
    }

    /**
     * 按当前方向解算弹窗显示尺寸。swapForPortrait 且竖屏时（供建主/局域网/单机/录像/玩家等待这类
     * 横屏设计为宽大于高的弹窗）：宽度铺满屏宽（与 Activity 同宽，避免按钮/表单文字被挤得换行），
     * 高度取与宽度一致的正方形比例（边长=屏宽，不超过屏高）；横屏（含转回）走 {@link #fitSizeToScreen}
     * 按设计宽高恢复横屏比例。弹窗创建与旋转重排共用。
     */
    private static int[] orientedFitSize(Context context, int designW, int designH, boolean swapForPortrait) {
        if (swapForPortrait && designW > 0 && designH > 0 && isPortrait(context)) {
            DisplayMetrics dm = DialogScale.screenMetrics(context);
            // 竖屏：正方形，边长取屏宽（竖屏下屏宽≤屏高，自然不超屏高）
            int side = Math.min(dm.widthPixels, dm.heightPixels);
            return new int[]{side, side};
        }
        return fitSizeToScreen(context, designW, designH);
    }

    /**
     * 屏幕旋转后重解所有活动拖拽弹窗的显示宽度（供 YGOProActivity.onConfigurationChanged
     * 重建视图树后调用）：横屏转竖屏限宽不超屏避免文字截断，竖屏转横屏按设计宽度回弹
     * 避免显示过小；对声明了居中区域的弹窗同步按新视图树中的同 id 区域重新居中。
     */
    public static void relayoutActivePopupsForOrientation(Context context) {
        if (ACTIVE_LAYERS.isEmpty()) return;
        // 遍历副本：重排可能触发 requestLayout/重新居中，避免边遍历边改集合
        for (DragFrameLayout layer : new ArrayList<>(ACTIVE_LAYERS)) {
            if (layer != null) layer.relayoutForOrientation();
        }
    }

    private static class DragFrameLayout extends FrameLayout {
        private static final String PREF_X = "_x";
        private static final String PREF_Y = "_y";
        private final SharedPreferences prefs;
        private final String dialogId;
        private float grabDX, grabDY, downX, downY;
        private boolean dragging;
        private boolean touchOnScrollable;
        private boolean childConsumedDown;
        /** 本层所属 PopupWindow：点击置顶时重排其窗口层级 */
        private PopupWindow hostPopup;
        /** 兜底转发目标（Activity decorView），null 表示不转发 */
        private View passThroughTarget;
        /** 当前手势的转发目标：null 表示由本层自行处理 */
        private View gestureTarget;
        /** 转发手势的 downTime 与最近落点（目标坐标），手势被打断时补发 ACTION_CANCEL 用 */
        private long forwardedDownTime;
        private float forwardedX, forwardedY;
        /** 正在处理由其他弹窗层转发进来的事件：跳过归属判定，避免二次转发 */
        private boolean receivingForwarded;
        /** 弹窗设计（未限宽）尺寸，旋转后据此按新屏宽重新解算显示宽度 */
        private int designW = 0;
        private int designH = 0;
        /** 创建弹窗时的横屏等比系数（与内容 inflate 密度一致）：旋转重排时按
         *  当前方向系数/创建系数的比例重缩放弹窗可视尺寸（用户规格：竖屏转横屏后
         *  dialog 仍按横屏时的高度比例显示，幸存弹窗无法重 inflate 密度，整体缩放等效） */
        private float createdFactor = 1f;
        /** 竖屏时交换宽高基准（与创建期一致），供建主/局域网/单机弹窗旋转后重解使用 */
        private boolean swapForPortrait = false;
        /** 居中区域（如 layout_game_right）的视图 id 与弱引用，旋转重建后按 id 重新解析新实例 */
        private int centerRegionId = View.NO_ID;
        private java.lang.ref.WeakReference<View> centerRegionRef;
        /** 停靠模式：true=内容贴区域左缘+垂直居中（{@link #dockPopupInRegionLeft}），
         *  false=区域居中（{@link #centerPopupInRegion}）；旋转重排按此标志重新定位 */
        private boolean dockLeftInRegion = false;
        /** 自定义旋转重排逻辑；非空时 {@link #relayoutForOrientation} 完全交由其处理，跳过默认限宽 */
        private Runnable customRelayout;
        // ── 收缩到底部横条模式（经 enableCollapse 启用，视图与几何由本类静态辅助解算）──
        /** 底部收缩横条：与聊天输入框同高同位、保持对话框原宽/原位/原背景贴图的横条，点击恢复原始尺寸；
         *  作为包装层后加子视图，child 0 始终保持为对话框内容 */
        private View collapseBar;
        /** 收缩态横条几何快照：展开可见时取对话框内容的原宽与左缘，横条不再拉伸为整屏宽 */
        private int collapseBarWidth;
        private int collapseBarLeft;
        /** 展开态收缩把手：优先为内容布局 XML 声明的 btn_dialog_collapse（位置由布局
         *  声明，免程序化贴位），内容未声明时才是包装层左上角的程序化「▼」小按钮 */
        private View collapseHandle;
        /** 收缩按钮来自 dialog 布局 XML（btn_dialog_collapse）：不参与程序化把手贴位 */
        private boolean handleInContent;
        /** 把手嵌入目标：该弹窗标题视图（可空/可 GONE），可见时把手贴其左侧，否则左上角悬浮 */
        private View collapseTitleView;
        private boolean collapsible;
        private boolean collapsed;
        /** 本次手势 DOWN 是否落在收缩横条/把手上：是则整体禁用拖拽逻辑 */
        private boolean touchOnCollapseWidget;
        /** 收缩横条的布局补偿监听目标（旋转重建后聊天输入框/区域尚未布局时挂一次性重锚，按实例去重） */
        private final List<View> collapseAnchorWatches = new ArrayList<>();

        DragFrameLayout(Context context, SharedPreferences prefs, String dialogId) {
            super(context);
            this.prefs = prefs;
            this.dialogId = dialogId;
            setClipChildren(false);
        }

        void setHostPopup(PopupWindow popup) {
            this.hostPopup = popup;
        }

        void setPassThroughTarget(View target) {
            this.passThroughTarget = target;
        }

        void setDesignSize(int w, int h, float createdF) {
            this.designW = w;
            this.designH = h;
            this.createdFactor = createdF > 0f ? createdF : 1f;
        }

        void setSwapForPortrait(boolean swap) {
            this.swapForPortrait = swap;
        }

        void setCustomRelayout(Runnable handler) {
            this.customRelayout = handler;
        }

        /** 记录居中区域（供旋转后按新视图树同 id 区域重新居中） */
        void setCenterRegion(View region) {
            setCenterRegion(region, false);
        }

        /** 记录居中/停靠区域与停靠模式（dockLeft=true 贴区域左缘，false 居中） */
        void setCenterRegion(View region, boolean dockLeft) {
            if (region != null) {
                this.centerRegionId = region.getId();
                this.centerRegionRef = new java.lang.ref.WeakReference<>(region);
                this.dockLeftInRegion = dockLeft;
            }
        }

        /**
         * 启用收缩模式：包装层底部加与聊天输入框同高同位、保持对话框原宽与原背景贴图的
         * 横条；收缩把手优先复用内容布局 XML 声明的 btn_dialog_collapse（各 dialog 自带，
         * 位置由布局声明），内容未声明时才在左上角加程序化「▼」把手；收缩态内容 GONE 仅剩横条，
         * 横条以外触摸照旧透传给 Activity，供玩家临时确认场地，点击横条恢复。横条与把手作为
         * 后加子视图，child 0 始终是对话框内容，{@link #centerPopupInRegion} 的 margin 居中与
         * 旋转限宽逻辑不受影响。
         */
        void makeCollapsible(View titleView, String title) {
            if (getChildCount() == 0) return;
            final View content = getChildAt(0);
            collapsible = true;
            collapseTitleView = titleView;

            collapseBar = createCollapseBar(getContext(), title);
            styleBarWithDialogContent(collapseBar, content);
            FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    barMetrics(getContext())[2],
                    Gravity.TOP | Gravity.START);
            collapseBar.setVisibility(GONE);
            collapseBar.setOnClickListener(v -> setCollapsed(false));
            addView(collapseBar, blp);

            // 收缩触发器：内容声明的 btn_dialog_collapse 作把手（点击即收缩），
            // 未声明时回落程序化「▼」把手并同步贴位
            View xmlBtn = content.findViewById(R.id.btn_dialog_collapse);
            if (xmlBtn != null) {
                handleInContent = true;
                collapseHandle = xmlBtn;
            } else {
                collapseHandle = createCollapseHandle(getContext());
                FrameLayout.LayoutParams hlp = new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                        Gravity.TOP | Gravity.START);
                hlp.leftMargin = dp(getContext(), 2);
                hlp.topMargin = dp(getContext(), 2);
                addView(collapseHandle, hlp);
            }
            collapseHandle.setOnClickListener(v -> setCollapsed(true));

            // 内容经居中 margin / 旋转重解宽度而移动，把手跟随同步
            content.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, ob, olr) -> syncCollapseHandle());
            // 标题自身尺寸/可见变化（如 setText、横竖屏重建）时也需重算把手位置
            if (titleView != null) {
                titleView.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, ob, olr) -> syncCollapseHandle());
            }
            syncCollapseHandle();
        }

        void setCollapseTitle(String title) {
            setBarTitle(collapseBar, title);
        }

        private void setCollapsed(boolean doCollapse) {
            if (!collapsible || getChildCount() == 0) return;
            View content = getChildAt(0);
            if (doCollapse) {
                refreshCollapseAnchors();
            }
            collapsed = doCollapse;
            content.setVisibility(doCollapse ? GONE : VISIBLE);
            collapseHandle.setVisibility(doCollapse ? GONE : VISIBLE);
            collapseBar.setVisibility(doCollapse ? VISIBLE : GONE);
            if (!doCollapse) {
                syncCollapseHandle();
            }
        }

        /**
         * 横条与聊天输入框对齐并在 layout_game_right 区域内水平居中。垂直用包装层底部锚定
         *（Gravity.BOTTOM + bottomMargin=gap，gap 为聊天输入框底边到窗口底边的距离），
         * 与真实窗口底边对齐而不依赖包装层当前测得高度：旋转重建瞬间（onConfigurationChanged
         * 内同步跑 relayoutForOrientation）包装层/区域/聊天输入框尚未完成布局，旧的按
         * decor+屏高反推 topMargin 的写法会以旧方向屏高为基准算出越界的 topMargin，把横条
         * 顶出窗口底边之外而整条看不见（表现为横屏最小化转竖屏后横条消失、无法点击还原）。
         * 改底部锚定后即便此刻度量退化横条也稳定显示在屏幕底部，横向按区域实时坐标居中并
         * 限幅到屏宽（区域未布局时退回快照亦不越屏），配合一次性重锚监听在布局完成后精确对齐
         */
        private void refreshCollapseAnchors() {
            if (collapseBar == null) return;
            int[] m = barMetrics(getContext());
            FrameLayout.LayoutParams blp = (FrameLayout.LayoutParams) collapseBar.getLayoutParams();
            blp.height = m[2];
            // 内容可见已布局时取快照（含旋转重缩放系数：横条保持展开态弹窗的可视宽度）；
            // 收缩态内容 GONE 不参与布局，沿用上一次的快照值
            if (getChildCount() > 0) {
                View content = getChildAt(0);
                if (content.getVisibility() == VISIBLE && content.getWidth() > 0) {
                    float sx = content.getScaleX();
                    collapseBarWidth = Math.round(content.getWidth() * sx);
                    collapseBarLeft = Math.max(0, Math.round(
                            content.getLeft() - content.getWidth() * (sx - 1f) / 2f));
                }
            }
            // 水平：在区域内居中（旋转重建后按新坐标重解）；无宽度快照时取区域宽；
            // 区域不可用才退回内容原左缘快照；两种情形都限幅到屏宽，杜绝转屏后横条整条偏出屏外
            int barW = collapseBarWidth;
            int left;
            View region = resolveCollapseRegion();
            if (region != null) {
                if (barW <= 0 || barW > region.getWidth()) barW = region.getWidth();
                int[] regLoc = new int[2];
                region.getLocationInWindow(regLoc);
                View decor = resolveActivityDecorView(getContext());
                int[] decorLoc = new int[2];
                if (decor != null) decor.getLocationOnScreen(decorLoc);
                int[] myLoc = new int[2];
                getLocationOnScreen(myLoc);
                left = decorLoc[0] + regLoc[0] + (region.getWidth() - barW) / 2 - myLoc[0];
            } else {
                left = Math.max(0, collapseBarLeft);
            }
            if (left < 0) left = 0;
            if (left + barW > m[0]) left = Math.max(0, m[0] - barW);
            blp.width = barW > 0 ? barW : ViewGroup.LayoutParams.MATCH_PARENT;
            blp.leftMargin = blp.width == ViewGroup.LayoutParams.MATCH_PARENT ? 0 : left;
            // 垂直：底部锚定与聊天输入框底边对齐（bottomMargin=gap），不依赖包装层高度，
            // 免疫旋转时窗口高度滞后导致的横条被顶出屏外
            blp.gravity = Gravity.BOTTOM | Gravity.START;
            blp.topMargin = 0;
            blp.bottomMargin = m[3];
            collapseBar.setLayoutParams(blp);
            requestCollapseAnchorRetry();
        }

        /** 横条水平居中参照的区域：优先记录的居中区域，旋转重建旧实例脱离视图树后按 id 重解析；
         *  未登记/不可用时回退 layout_game_right；仍未布局返回 null 由调用方降级 */
        private View resolveCollapseRegion() {
            View region = centerRegionRef != null ? centerRegionRef.get() : null;
            if (region == null || !region.isAttachedToWindow()) {
                Activity act = resolveActivity(getContext());
                int id = centerRegionId != View.NO_ID ? centerRegionId : R.id.layout_game_right;
                region = act != null ? act.findViewById(id) : null;
            }
            return region != null && region.getWidth() > 0 && region.getHeight() > 0 ? region : null;
        }

        /** 旋转重建视图树时聊天输入框/区域常尚未完成布局，度量退化为兜底值：
         *  给未布局的目标挂一次性布局监听补重锚，目标布局完成后横条才真正与
         *  聊天输入框同高同位、在区域内居中 */
        private void requestCollapseAnchorRetry() {
            if (!collapsible) return;
            Activity act = resolveActivity(getContext());
            if (act == null) return;
            watchLayoutOnce(act.findViewById(R.id.et_chat_input));
            watchLayoutOnce(act.findViewById(R.id.layout_game_right));
        }

        /** 给目标挂一次性「布局完成后重锚横条」监听（按实例去重，触发即摘） */
        private void watchLayoutOnce(View target) {
            if (target == null || target.getHeight() > 0 || collapseAnchorWatches.contains(target)) return;
            collapseAnchorWatches.add(target);
            target.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View v, int l, int t, int r, int b,
                                           int oldL, int oldT, int oldR, int oldB) {
                    if (b - t <= 0) return;
                    collapseAnchorWatches.remove(v);
                    v.removeOnLayoutChangeListener(this);
                    refreshCollapseAnchors();
                }
            });
        }

        /** 把手位置：XML 声明的 btn_dialog_collapse 由布局自行定位不参与贴位；
         *  程序化把手优先贴标题左侧（标题可见且已布局），否则贴内容左上角 */
        private void syncCollapseHandle() {
            if (handleInContent || collapseHandle == null || getChildCount() == 0) return;
            View content = getChildAt(0);
            // 标题可见时把手移到标题行左侧、垂直居中，颜色随标题（positionHandleBesideTitle 内部处理）
            if (positionHandleBesideTitle(collapseHandle, content, collapseTitleView)) {
                return;
            }
            FrameLayout.LayoutParams hlp =
                    (FrameLayout.LayoutParams) collapseHandle.getLayoutParams();
            int inset = dp(getContext(), 2);
            // 把手贴缩放后内容的可视左上角（pivot 默认在中心，可视边界由缩放比例外扩/内缩）
            float sx = content.getScaleX();
            float sy = content.getScaleY();
            int handleW = collapseHandle.getWidth();
            int handleH = collapseHandle.getHeight();
            int left = Math.round(
                    content.getLeft() - content.getWidth() * (sx - 1f) / 2f) + inset;
            int top = Math.round(
                    content.getTop() - content.getHeight() * (sy - 1f) / 2f) + inset;
            // 钉在本层可视范围内，避免旋转瞬时坐标不一致把手被推到屏外而“看不见”
            hlp.leftMargin = Math.max(0,
                    Math.min(Math.max(0, getWidth() - handleW), left));
            hlp.topMargin = Math.max(0,
                    Math.min(Math.max(0, getHeight() - handleH), top));
            hlp.gravity = Gravity.TOP | Gravity.START;
            collapseHandle.setLayoutParams(hlp);
        }

        /**
         * 屏幕旋转后按新屏宽重新解算并应用弹窗显示宽度；若记录了居中区域，旋转重建后
         * 旧区域实例已脱离视图树，按 id 从当前 Activity 视图树重新解析同 id 新实例并重算居中偏移。
         */
        void relayoutForOrientation() {
            // 旋转一律重新按区域定位：先归零拖拽残留的整层平移（与 centerPopupInRegion/dock 同语义），
            // 否则旧方向的平移叠加新 margin 会把内容与把手推出可视区（标题缺顶、「▼」消失、询问窗无法取消）
            setTranslationX(0f);
            setTranslationY(0f);
            // 子视图尺寸依赖外部解算的弹窗（如选卡/卡片确认按区域宽烘焙卡图）→ 交由自定义重排全权处理
            if (customRelayout != null) {
                customRelayout.run();
                if (collapsible) {
                    post(this::refreshCollapseAnchors);
                    post(this::syncCollapseHandle);
                }
                return;
            }
            if (getChildCount() == 0) return;
            View content = getChildAt(0);
            int[] fitted = orientedFitSize(getContext(), designW, designH, swapForPortrait);
            // 按当前方向等比系数相对创建系数的比例整体重缩放弹窗：内容以创建方向
            // 系数（如竖屏 1.0）inflate 且旋转后无法重 inflate，缩放后竖屏创建的弹窗
            // 转横屏即按横屏高度比例显示；限幅使缩放后的可视尺寸不超出当前屏幕；
            // swapForPortrait（正方形重排）与铺宽内容（designW<=0）不参与，保持原行为
            float s = 1f;
            if (!swapForPortrait && designW > 0 && createdFactor > 0f) {
                s = DialogScale.factor(getContext()) / createdFactor;
                if (Float.compare(s, 1f) != 0) {
                    DisplayMetrics m = DialogScale.screenMetrics(getContext());
                    int refW = fitted[0] > 0 ? fitted[0] : designW;
                    if (refW > 0) s = Math.min(s, (float) m.widthPixels / refW);
                    int refH = fitted[1] > 0 ? fitted[1]
                            : (content.getHeight() > 0 ? content.getHeight() : 0);
                    if (refH > 0) s = Math.min(s, (float) m.heightPixels / refH);
                    if (s < 0.5f) s = 0.5f;
                    else if (s > 2f) s = 2f;
                }
            }
            content.setScaleX(s);
            content.setScaleY(s);
            ViewGroup.LayoutParams raw = content.getLayoutParams();
            if (raw instanceof FrameLayout.LayoutParams) {
                FrameLayout.LayoutParams flp = (FrameLayout.LayoutParams) raw;
                flp.width = fitted[0];
                // 具体高度才按比例重解；WRAP_CONTENT/MATCH_PARENT 保持不变交由内容自适应
                if (designH > 0) flp.height = fitted[1];
                content.setLayoutParams(flp);
            }
            content.requestLayout();
            if (centerRegionId != View.NO_ID && hostPopup != null) {
                View region = centerRegionRef != null ? centerRegionRef.get() : null;
                if (region == null || !region.isAttachedToWindow()) {
                    Activity act = resolveActivity(getContext());
                    region = act != null ? act.findViewById(centerRegionId) : null;
                }
                if (region != null) {
                    if (dockLeftInRegion) dockPopupInRegionLeft(hostPopup, region);
                    else centerPopupInRegion(hostPopup, region);
                }
            }
            if (collapsible) {
                refreshCollapseAnchors();
                post(this::syncCollapseHandle);
            }
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (passThroughTarget == null) {
                passThroughTarget = resolveActivityDecorView(getContext());
            }
            ACTIVE_LAYERS.remove(this);
            ACTIVE_LAYERS.add(this);
            if (collapsible) {
                post(this::refreshCollapseAnchors);
                post(this::syncCollapseHandle);
            }
        }

        /**
         * 触点是否落在任一可见已布局子视图（对话框内容 / 收缩横条 / 收缩把手）内，
         * 隐藏中的不计；子视图尚未布局（宽高为 0）时视为不在内，避免误吞事件
         */
        private boolean isTouchInsideContent(float x, float y) {
            for (int i = 0; i < getChildCount(); i++) {
                if (isTouchInView(getChildAt(i), x, y)) return true;
            }
            return false;
        }

        /** 触点是否落在指定子视图的可见已布局区域内（包装层本地坐标） */
        private static boolean isTouchInView(View child, float x, float y) {
            if (child == null || child.getVisibility() != VISIBLE) return false;
            if (child.getWidth() <= 0 || child.getHeight() <= 0) return false;
            float left = child.getLeft() + child.getTranslationX();
            float top = child.getTop() + child.getTranslationY();
            return x >= left && x < left + child.getWidth()
                    && y >= top && y < top + child.getHeight();
        }

        /** 触点是否落在收缩横条/把手上：自行消费（普通子视图点击），不参与拖拽 */
        private boolean isTouchOnCollapseWidget(float x, float y) {
            return isTouchInView(collapseBar, x, y) || isTouchInView(collapseHandle, x, y);
        }

        /** 本层内容区是否覆盖该屏幕坐标（跨弹窗归属判定用；含收缩横条/把手区域） */
        private boolean containsScreenPoint(float rawX, float rawY) {
            if (!isAttachedToWindow() || getChildCount() == 0) return false;
            int[] loc = new int[2];
            getLocationOnScreen(loc);
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child.getVisibility() != VISIBLE) continue;
                if (child.getWidth() <= 0 || child.getHeight() <= 0) continue;
                float left = loc[0] + child.getLeft() + child.getTranslationX();
                float top = loc[1] + child.getTop() + child.getTranslationY();
                if (rawX >= left && rawX < left + child.getWidth()
                        && rawY >= top && rawY < top + child.getHeight()) {
                    return true;
                }
            }
            return false;
        }

        /** 由最上层往下查找触点所属的弹窗层，都不命中返回 null */
        private static DragFrameLayout findTopLayerAt(float rawX, float rawY) {
            for (int i = ACTIVE_LAYERS.size() - 1; i >= 0; i--) {
                DragFrameLayout layer = ACTIVE_LAYERS.get(i);
                if (layer != null && layer.containsScreenPoint(rawX, rawY)) return layer;
            }
            return null;
        }

        /**
         * 判定本次手势的转发目标：
         * 1) 触点在本层内容区 → null（自身处理，按钮点击/拖拽照旧）；
         * 2) 触点在其他弹窗层内容区 → 该层（跨窗口转发，抬手后置顶）；
         * 3) 都不命中 → Activity decorView（决斗场、手卡等下层 UI 继续响应）
         */
        private View resolveGestureTarget(MotionEvent ev) {
            if (isTouchInsideContent(ev.getX(), ev.getY())) return null;
            DragFrameLayout layer = findTopLayerAt(ev.getRawX(), ev.getRawY());
            if (layer != null && layer != this) return layer;
            return passThroughTarget;
        }

        /**
         * 把事件按屏幕坐标换算后转发给目标视图（下层弹窗包装层或 Activity decorView），
         * 使弹窗显示期间决斗场卡片、手卡公开面板的卡片、阶段按钮等仍可响应点击与拖动
         */
        private void forwardEventTo(View target, MotionEvent ev) {
            if (target == null || !target.isAttachedToWindow()) return;
            int[] loc = new int[2];
            target.getLocationOnScreen(loc);
            float x = ev.getRawX() - loc[0];
            float y = ev.getRawY() - loc[1];
            forwardedX = x;
            forwardedY = y;
            MotionEvent copy = MotionEvent.obtain(ev);
            boolean isLayer = target instanceof DragFrameLayout;
            if (isLayer) ((DragFrameLayout) target).receivingForwarded = true;
            try {
                copy.setLocation(x, y);
                target.dispatchTouchEvent(copy);
            } catch (Throwable ignored) {
                // 目标窗口已销毁时忽略，不影响本弹窗交互
            } finally {
                if (isLayer) ((DragFrameLayout) target).receivingForwarded = false;
                copy.recycle();
            }
        }

        /**
         * 手势被中途打断（手指未抬起时弹窗关闭或重排）时向转发目标补发 ACTION_CANCEL，
         * 避免目标视图树残留未结束的按下状态，导致后续点击卡顿或事件错乱
         */
        private void cancelForwardedGesture() {
            if (gestureTarget == null) return;
            View target = gestureTarget;
            gestureTarget = null;
            long downTime = forwardedDownTime;
            forwardedDownTime = 0;
            if (!target.isAttachedToWindow() || downTime == 0) return;
            MotionEvent cancel = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(),
                    MotionEvent.ACTION_CANCEL, forwardedX, forwardedY, 0);
            try {
                target.dispatchTouchEvent(cancel);
            } catch (Throwable ignored) {
                // 目标已销毁时忽略
            } finally {
                cancel.recycle();
            }
        }

        /** 被点中的下层弹窗在抬手后提到最上层（post 到本次派发结束，避免派发中改窗口树） */
        private void bringLayerToFront(View target) {
            if (!(target instanceof DragFrameLayout)) return;
            final DragFrameLayout layer = (DragFrameLayout) target;
            if (ACTIVE_LAYERS.isEmpty()
                    || ACTIVE_LAYERS.get(ACTIVE_LAYERS.size() - 1) == layer) return;
            layer.post(layer::bringHostPopupToFront);
        }

        /**
         * 把本弹窗窗口重排到同类型窗口的最上层：直接对 PopupDecorView 做无动画的
         * removeViewImmediate + addView，不走 PopupWindow.dismiss，避免误触发各对话框
         * OnDismissListener 的业务清理；包装层实例与拖拽位移原样保留，位置与内容状态不丢失
         */
        private void bringHostPopupToFront() {
            PopupWindow popup = hostPopup;
            if (popup == null || !popup.isShowing() || !isAttachedToWindow()) return;
            View decor = getRootView();
            if (decor == this) return;
            ViewGroup.LayoutParams raw = decor.getLayoutParams();
            if (!(raw instanceof WindowManager.LayoutParams)) return;
            WindowManager.LayoutParams lp = (WindowManager.LayoutParams) raw;
            WindowManager wm = (WindowManager) getContext()
                    .getSystemService(Context.WINDOW_SERVICE);
            if (wm == null) return;
            int animations = lp.windowAnimations;
            try {
                // 动画属性先置 0：抑制重排时的退场/入场动画，避免弹窗闪烁
                lp.windowAnimations = 0;
                wm.updateViewLayout(decor, lp);
                wm.removeViewImmediate(decor);
                wm.addView(decor, lp);
            } catch (Throwable ignored) {
                // 窗口状态异常时放弃置顶，弹窗自身仍可继续交互
                return;
            }
            // 恢复动画属性，保证之后 dismiss 仍有退场动画
            lp.windowAnimations = animations;
            try {
                wm.updateViewLayout(decor, lp);
            } catch (Throwable ignored) {
            }
        }

        private boolean isTouchOnScrollableView(float x, float y) {
            if (getChildCount() == 0) return false;
            View contentView = getChildAt(0);
            if (contentView.getVisibility() != VISIBLE) return false;
            if (!(contentView instanceof ViewGroup)) return false;
            float cx = x - contentView.getLeft() + contentView.getScrollX();
            float cy = y - contentView.getTop() + contentView.getScrollY();
            if (cx < 0 || cy < 0 || cx >= contentView.getWidth() || cy >= contentView.getHeight())
                return false;
            return findScrollableChild((ViewGroup) contentView, cx, cy) != null;
        }

        private View findScrollableChild(ViewGroup parent, float x, float y) {
            for (int i = parent.getChildCount() - 1; i >= 0; i--) {
                View child = parent.getChildAt(i);
                if (!child.isShown()) continue;

                float cx = x - child.getLeft() + child.getScrollX();
                float cy = y - child.getTop() + child.getScrollY();

                if (cx < 0 || cy < 0 || cx >= child.getWidth() || cy >= child.getHeight()) {
                    continue;
                }

                if (child instanceof ListView) {
                    ListView lv = (ListView) child;
                    int pos = lv.pointToPosition((int) cx, (int) cy);
                    if (pos != ListView.INVALID_POSITION) {
                        return child;
                    }
                } else if (isScrollableView(child)) {
                    return child;
                }

                if (child instanceof ViewGroup) {
                    View found = findScrollableChild((ViewGroup) child, cx, cy);
                    if (found != null) return found;
                }
            }
            return null;
        }

        private static boolean isScrollableView(View v) {
            return v instanceof AbsListView
                    || v instanceof ScrollView
                    || v instanceof HorizontalScrollView
                    || v instanceof NestedScrollView
                    || v instanceof ScrollingView
                    || v instanceof SeekBar;
        }

        private void handleDrag(MotionEvent ev) {
            switch (ev.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    downX = ev.getRawX();
                    downY = ev.getRawY();
                    int[] loc = new int[2];
                    getLocationOnScreen(loc);
                    grabDX = ev.getRawX() - loc[0];
                    grabDY = ev.getRawY() - loc[1];
                    dragging = false;
                    break;

                case MotionEvent.ACTION_MOVE:
                    if (!dragging) {
                        if (Math.abs(ev.getRawX() - downX) >= DRAG_THRESHOLD
                                || Math.abs(ev.getRawY() - downY) >= DRAG_THRESHOLD) {
                            dragging = true;
                            performHapticFeedback(
                                    android.view.HapticFeedbackConstants.LONG_PRESS);
                        }
                    }
                    if (dragging) {
                        int[] cur = new int[2];
                        getLocationOnScreen(cur);
                        int laidX = (int) (cur[0] - getTranslationX());
                        int laidY = (int) (cur[1] - getTranslationY());
                        float[] clamped = clampLayerTranslation(
                                ev.getRawX() - grabDX - laidX,
                                ev.getRawY() - grabDY - laidY);
                        setTranslationX(clamped[0]);
                        setTranslationY(clamped[1]);
                    }
                    break;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (dragging) {
                        // 区域锚定弹窗不持久化整层偏移：下次显示/旋转都按区域重算定位，
                        // 旧方向的绝对屏幕坐标重放到新方向会把内容推到屏外
                        if (centerRegionId == View.NO_ID) {
                            int[] finalLoc = new int[2];
                            getLocationOnScreen(finalLoc);
                            prefs.edit()
                                    .putInt(dialogId + PREF_X, finalLoc[0])
                                    .putInt(dialogId + PREF_Y, finalLoc[1])
                                    .apply();
                        }
                    }
                    dragging = false;
                    break;
            }
        }

        /**
         * 限定整层平移幅度：保证对话框内容至少保留 {@code keep} 像素在屏内。区域锚定弹窗一旦被
         * 整层平移到屏外将无法再与其交互（询问窗丢失即死锁），因此拖拽不允许把内容拖到不可恢复的位置。
         */
        private float[] clampLayerTranslation(float tx, float ty) {
            if (getChildCount() == 0 || getWidth() <= 0 || getHeight() <= 0) {
                return new float[]{tx, ty};
            }
            View content = getChildAt(0);
            if (content.getVisibility() != VISIBLE || content.getWidth() <= 0
                    || content.getHeight() <= 0) {
                return new float[]{tx, ty};
            }
            int keep = dp(getContext(), 32);
            float w = content.getWidth() * content.getScaleX();
            float h = content.getHeight() * content.getScaleY();
            float visLeft = content.getLeft() + (content.getWidth() - w) / 2f;
            float visTop = content.getTop() + (content.getHeight() - h) / 2f;
            float minTx = keep - (visLeft + w);
            float maxTx = getWidth() - keep - visLeft;
            float minTy = keep - (visTop + h);
            float maxTy = getHeight() - keep - visTop;
            return new float[]{
                    Math.max(minTx, Math.min(maxTx, tx)),
                    Math.max(minTy, Math.min(maxTy, ty))};
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent ev) {
            final int action = ev.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                if (gestureTarget != null) {
                    // 极端情况下上一手势未收到 UP/CANCEL：先结束它再重新判定
                    cancelForwardedGesture();
                }
                // 转发进来的事件不再做归属判定，直接按本层内容处理（可点击、可拖动）
                gestureTarget = receivingForwarded ? null : resolveGestureTarget(ev);
                if (gestureTarget != null) {
                    forwardedDownTime = ev.getDownTime();
                    forwardEventTo(gestureTarget, ev);
                    // 必须消费 DOWN：窗口不消费按下事件时系统不再派发本手势后续事件，
                    // 目标将收不到 UP（点击不生效），状态位卡死还会让本弹窗按钮与拖拽失效
                    return true;
                }
            } else if (gestureTarget != null) {
                View target = gestureTarget;
                boolean ending = action == MotionEvent.ACTION_UP
                        || action == MotionEvent.ACTION_CANCEL;
                // 先复位再转发：目标可能在 UP 回调中关闭本弹窗，避免重复补发 CANCEL
                if (ending) {
                    gestureTarget = null;
                    forwardedDownTime = 0;
                }
                forwardEventTo(target, ev);
                if (action == MotionEvent.ACTION_UP) {
                    bringLayerToFront(target);
                }
                return true;
            }

            switch (ev.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    touchOnCollapseWidget = isTouchOnCollapseWidget(ev.getX(), ev.getY());
                    if (touchOnCollapseWidget) {
                        // 收缩横条/把手上的触点只作普通子视图点击：禁用拖拽避免移动整个包装层
                        childConsumedDown = false;
                        return super.dispatchTouchEvent(ev);
                    }
                    touchOnScrollable = isTouchOnScrollableView(ev.getX(), ev.getY());
                    if (touchOnScrollable) {
                        childConsumedDown = false;
                        return super.dispatchTouchEvent(ev);
                    }
                    handleDrag(ev);
                    boolean result = super.dispatchTouchEvent(ev);
                    childConsumedDown = result;
                    return result || true;

                case MotionEvent.ACTION_MOVE:
                    if (!touchOnScrollable && !touchOnCollapseWidget) {
                        handleDrag(ev);
                        if (dragging) {
                            if (childConsumedDown) {
                                MotionEvent cancel = MotionEvent.obtain(ev);
                                cancel.setAction(MotionEvent.ACTION_CANCEL);
                                super.dispatchTouchEvent(cancel);
                                cancel.recycle();
                            }
                            return true;
                        }
                    }
                    return super.dispatchTouchEvent(ev);

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (!touchOnScrollable && !touchOnCollapseWidget) {
                        handleDrag(ev);
                    }
                    boolean upResult = super.dispatchTouchEvent(ev);
                    dragging = false;
                    touchOnScrollable = false;
                    touchOnCollapseWidget = false;
                    childConsumedDown = false;
                    return childConsumedDown ? upResult : (upResult || true);
            }
            return super.dispatchTouchEvent(ev);
        }
    }

    public void setupDraggableView(View targetView) {
        if (!ENABLE_DRAG) return;
        if (!(targetView instanceof ViewGroup)) {
            setupSimpleDraggableView(targetView);
            return;
        }

        final ViewGroup viewGroup = (ViewGroup) targetView;
        final float[] grabDX = new float[1];
        final float[] grabDY = new float[1];
        final boolean[] dragging = new boolean[]{false};

        View.OnTouchListener dragListener = (v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    grabDX[0] = event.getRawX() - targetView.getLeft();
                    grabDY[0] = event.getRawY() - targetView.getTop();
                    dragging[0] = false;
                    return false;

                case MotionEvent.ACTION_MOVE:
                    if (!dragging[0]) {
                        float dx = event.getRawX() - (targetView.getLeft() + grabDX[0]);
                        float dy = event.getRawY() - (targetView.getTop() + grabDY[0]);
                        if (Math.abs(dx) < 8 && Math.abs(dy) < 8) {
                            return false;
                        }
                        dragging[0] = true;
                        targetView.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                    }
                    int newX = (int) (event.getRawX() - grabDX[0]);
                    int newY = (int) (event.getRawY() - grabDY[0]);
                    targetView.setLeft(newX);
                    targetView.setTop(newY);
                    targetView.setRight(newX + targetView.getWidth());
                    targetView.setBottom(newY + targetView.getHeight());
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (dragging[0]) {
                        savePosition(targetView.getLeft(), targetView.getTop());
                    }
                    dragging[0] = false;
                    return false;
            }
            return false;
        };

        setTouchListenerRecursively(viewGroup, dragListener);
        targetView.setOnTouchListener(dragListener);
    }

    private void setTouchListenerRecursively(ViewGroup parent, View.OnTouchListener listener) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            child.setOnTouchListener(listener);
            
            if (child instanceof ViewGroup) {
                setTouchListenerRecursively((ViewGroup) child, listener);
            }
        }
    }

    private void setupSimpleDraggableView(View targetView) {
        if (!ENABLE_DRAG) return;
        final float[] grabDX = new float[1];
        final float[] grabDY = new float[1];
        final boolean[] dragging = new boolean[]{false};

        targetView.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    grabDX[0] = event.getRawX() - targetView.getLeft();
                    grabDY[0] = event.getRawY() - targetView.getTop();
                    dragging[0] = false;
                    return false;

                case MotionEvent.ACTION_MOVE:
                    if (!dragging[0]) {
                        float dx = event.getRawX() - (targetView.getLeft() + grabDX[0]);
                        float dy = event.getRawY() - (targetView.getTop() + grabDY[0]);
                        if (Math.abs(dx) < 8 && Math.abs(dy) < 8) {
                            return false;
                        }
                        dragging[0] = true;
                        v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                    }
                    int newX = (int) (event.getRawX() - grabDX[0]);
                    int newY = (int) (event.getRawY() - grabDY[0]);
                    targetView.setLeft(newX);
                    targetView.setTop(newY);
                    targetView.setRight(newX + targetView.getWidth());
                    targetView.setBottom(newY + targetView.getHeight());
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (dragging[0]) {
                        savePosition(targetView.getLeft(), targetView.getTop());
                    }
                    dragging[0] = false;
                    return false;
            }
            return false;
        });
    }

    public void applySavedPositionToView(View targetView) {
        if (hasSavedPosition) {
            targetView.post(() -> {
                targetView.setLeft(lastX);
                targetView.setTop(lastY);
                targetView.setRight(lastX + targetView.getWidth());
                targetView.setBottom(lastY + targetView.getHeight());
            });
        }
    }

    public void showPopup(PopupWindow popupWindow, View anchorView) {
        showPopup(popupWindow, anchorView, Gravity.CENTER, 0, 0);
    }

    /**
     * 将经 setupDraggablePopup 包装为全窗口的 popup 内容，从「整个窗口居中」改为
     * 「按指定区域宽高居中」（如 layout_game_right）：
     * 包装层（DragFrameLayout）铺满窗口，内部对话框以 Gravity.CENTER 居中，
     * 通过非对称 margin 把内容中心平移「区域中心 - 窗口中心」的偏移量即可。
     * 若区域尚未布局完成（宽高为 0），通过 OnGlobalLayoutListener 等待布局后再应用，
     * 避免退化为按整个窗口居中。
     */
    public static void centerPopupInRegion(PopupWindow popupWindow, View region) {
        if (popupWindow == null || region == null) return;
        // 记录居中区域到底层包装层，供屏幕旋转重建视图树后按同 id 重新解析并再次居中
        if (popupWindow.getContentView() instanceof DragFrameLayout) {
            ((DragFrameLayout) popupWindow.getContentView()).setCenterRegion(region);
        }
        if (region.getWidth() <= 0 || region.getHeight() <= 0) {
            region.getViewTreeObserver().addOnGlobalLayoutListener(
                    new ViewTreeObserver.OnGlobalLayoutListener() {
                        @Override
                        public void onGlobalLayout() {
                            region.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                            centerPopupInRegion(popupWindow, region);
                        }
                    });
            return;
        }
        View wrapper = popupWindow.getContentView();
        if (!(wrapper instanceof ViewGroup)) return;
        ViewGroup wrapperGroup = (ViewGroup) wrapper;
        if (wrapperGroup.getChildCount() == 0) return;
        // 区域锚定与贴左缘同义：“每次绝对贴区域中心”，归零上次拖拽对整层施加的平移；
        // 不归零时残留平移与本处 margin 叠加，旋转/重显后内容与把手被整体推出可视区
        //（表现为标题缺顶、转屏后「▼」消失、再转一次弹窗彻底不可见且无法取消）
        wrapper.setTranslationX(0f);
        wrapper.setTranslationY(0f);
        View content = wrapperGroup.getChildAt(0);
        ViewGroup.LayoutParams raw = content.getLayoutParams();
        if (!(raw instanceof FrameLayout.LayoutParams)) return;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) raw;

        View window = region.getRootView();
        int winW = window.getWidth();
        int winH = window.getHeight();
        if (winW <= 0 || winH <= 0) return;
        int[] regionLoc = new int[2];
        region.getLocationInWindow(regionLoc);
        int[] winLoc = new int[2];
        window.getLocationInWindow(winLoc);
        // CENTER gravity 下 leftMargin/topMargin 将内容整体平移该偏移量
        lp.leftMargin = (regionLoc[0] + region.getWidth() / 2) - (winLoc[0] + winW / 2);
        lp.rightMargin = 0;
        lp.topMargin = (regionLoc[1] + region.getHeight() / 2) - (winLoc[1] + winH / 2);
        lp.bottomMargin = 0;
        content.setLayoutParams(lp);
    }

    /**
     * 将经 {@link #setupDraggablePopup} 包装为全窗口的 popup 内容贴到指定区域（如 layout_game_right）
     * 的**左缘**、区域内垂直居中（区别于 {@link #centerPopupInRegion} 的水平居中）：
     * 包装层内对话框内容以 Gravity.CENTER 布局，左缘基准 = 窗口左 +（窗口宽 - 内容宽）/2，
     * 据此反推 leftMargin 使内容左缘 = 区域左缘；topMargin 与居中同式使内容垂直居中于区域。
     * 同时把区域与 dockLeft 模式记入包装层，旋转重建后由 {@link DragFrameLayout#relayoutForOrientation}
     * 按同 id 新实例重新贴回左缘。区域尚未布局（宽高为 0）时注册一次性布局监听待完成后应用。
     */
    public static void dockPopupInRegionLeft(PopupWindow popupWindow, View region) {
        if (popupWindow == null || region == null) return;
        if (popupWindow.getContentView() instanceof DragFrameLayout) {
            ((DragFrameLayout) popupWindow.getContentView()).setCenterRegion(region, true);
        }
        if (region.getWidth() <= 0 || region.getHeight() <= 0) {
            region.getViewTreeObserver().addOnGlobalLayoutListener(
                    new ViewTreeObserver.OnGlobalLayoutListener() {
                        @Override
                        public void onGlobalLayout() {
                            region.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                            dockPopupInRegionLeft(popupWindow, region);
                        }
                    });
            return;
        }
        View wrapper = popupWindow.getContentView();
        if (!(wrapper instanceof ViewGroup)) return;
        ViewGroup wrapperGroup = (ViewGroup) wrapper;
        if (wrapperGroup.getChildCount() == 0) return;
        // 归零拖拽期间对整层施加的平移：本模式语义是“每次停靠绝对贴区域左缘”，
        // 避免上次拖拽的 translation 叠加 margin 使内容偏离左缘（旋转载荷时同属此路）
        wrapper.setTranslationX(0f);
        wrapper.setTranslationY(0f);
        View content = wrapperGroup.getChildAt(0);
        ViewGroup.LayoutParams raw = content.getLayoutParams();
        if (!(raw instanceof FrameLayout.LayoutParams)) return;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) raw;

        View window = region.getRootView();
        int winW = window.getWidth();
        int winH = window.getHeight();
        if (winW <= 0 || winH <= 0) return;
        int[] regionLoc = new int[2];
        region.getLocationInWindow(regionLoc);
        int[] winLoc = new int[2];
        window.getLocationInWindow(winLoc);
        // 内容宽：优先用已解算的布局宽（旋转重排前已置为 fitted 宽），未布局时退回实测宽
        int contentW = lp.width > 0 ? lp.width : content.getWidth();
        // CENTER 下左缘 = winLeft + (winW - contentW)/2，叠加 leftMargin 后贴区域左缘
        lp.leftMargin = regionLoc[0] - (winLoc[0] + (winW - contentW) / 2);
        lp.rightMargin = 0;
        lp.topMargin = (regionLoc[1] + region.getHeight() / 2) - (winLoc[1] + winH / 2);
        lp.bottomMargin = 0;
        content.setLayoutParams(lp);
    }

    public void showPopup(PopupWindow popupWindow, View anchorView, int gravity, int xOffset, int yOffset) {
        if (context instanceof Activity) {
            Activity activity = (Activity) context;
            if (activity.isFinishing() || activity.isDestroyed()) {
                return;
            }
        }

        View effectiveAnchor = null;
        if (context instanceof Activity) {
            Activity activity = (Activity) context;
            View decorView = activity.getWindow().getDecorView();
            effectiveAnchor = decorView;
        }
        if (effectiveAnchor == null) {
            effectiveAnchor = anchorView;
        }
        if (effectiveAnchor == null || effectiveAnchor.getWindowToken() == null) {
            return;
        }

        try {
            if (hasSavedPosition && !isRegionAnchoredPopup(popupWindow)) {
                popupWindow.showAtLocation(effectiveAnchor, Gravity.NO_GRAVITY, lastX, lastY);
            } else {
                // 区域锚定的全窗口包装层不接受“整窗绝对偏移”式记忆位：那是上一方向的屏幕坐标，
                // 在新方向重放会把铺满整屏的层连同 margin 定位一起推出屏外，故忽略并清除
                if (hasSavedPosition) clearSavedPosition();
                popupWindow.showAtLocation(effectiveAnchor, gravity, xOffset, yOffset);
            }
        } catch (Exception e) {
            // Token may become invalid (e.g. OPPO ColorOS OplusViewRootImplHooks$ColorW)
        }
    }

    /**
     * 该弹窗是否为“内容按区域定位”的全窗口包装层（已由 {@link #centerPopupInRegion} /
     * {@link #dockPopupInRegionLeft} 记下过居中区）：这类弹窗每次显示/旋转都重算区域定位，
     * 拖拽位置仅作会话内平移，不应以窗口绝对偏移形式恢复。
     */
    private static boolean isRegionAnchoredPopup(PopupWindow popupWindow) {
        if (popupWindow == null) return false;
        View content = popupWindow.getContentView();
        return content instanceof DragFrameLayout
                && ((DragFrameLayout) content).centerRegionId != View.NO_ID;
    }

    /** 清除本弹窗持久化的拖拽位置，下次显示回到默认居中布局。 */
    public void clearSavedPosition() {
        lastX = 0;
        lastY = 0;
        hasSavedPosition = false;
        prefs.edit().remove(dialogId + KEY_X).remove(dialogId + KEY_Y).apply();
    }

    /** 按 dialogId 清除某个弹窗持久化的拖拽位置（供对话框释放自身布局时调用，不影响其他弹窗）。 */
    public static void clearPosition(Context context, String dialogId) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .remove(dialogId + KEY_X)
            .remove(dialogId + KEY_Y)
            .apply();
    }

    public static void resetAllPositions(Context context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply();
    }
}
