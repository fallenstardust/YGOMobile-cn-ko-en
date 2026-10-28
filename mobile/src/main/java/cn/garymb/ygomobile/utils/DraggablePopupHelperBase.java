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
import android.widget.EditText;
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

/**
 * 可拖动弹窗辅助工具的基类（拆分后的顶部抽象层）。
 *
 * <p>原 {@link DraggablePopupHelper} 单一文件共 2005 行，为符合单类规模规范拆为两族继承链：
 * 本类 ← {@link DraggablePopupHelper}（对外保持原类名与全部公共 API），以及由原嵌套类
 * DragFrameLayout 提升而来的 {@link DragFrameLayoutBase} ← {@link DragFrameLayoutImpl}。
 * 本层持有位置记忆存取、屏幕尺寸/方向解算、收缩横条视图与几何辅助、区域锚定统一实现，
 * 并声明包装层壳类型 {@code DragFrameLayout}（继承链各级对它的无限定引用均解析到此壳）。</p>
 *
 * <p>拆分仅做机械搬移与可见性放宽，行为与实现细节与原单一类完全一致。</p>
 */
public abstract class DraggablePopupHelperBase {
    protected static final String PREF_NAME = "popup_positions";
    protected static final String KEY_X = "_x";
    protected static final String KEY_Y = "_y";
    protected static final boolean ENABLE_DRAG = true;
    protected static final int DRAG_THRESHOLD = 8;

    /** 收缩横条内标题 TextView 的 tag，供 {@link #setBarTitle} 定位 */
    protected static final String TAG_BAR_TITLE = "collapse_bar_title";
    /** 聊天输入框不可见时收缩横条的兜底高度（dp） */
    protected static final int FALLBACK_BAR_HEIGHT_DP = 40;
    /** 收缩横条最小高度（dp），保证可点击目标不小于舒适触控尺寸 */
    protected static final int MIN_BAR_HEIGHT_DP = 28;
    /** 收缩横条底边相对聊天输入框上沿预留的安全间隙（dp）：横条一律贴在输入框上方，
     *  与 {@link ImePopupAdjuster} 抬升安全间隙同值，两种收缩来源（手动/输入法自动）定位统一 */
    protected static final int COLLAPSE_BAR_SAFETY_DP = 2;

    /**
     * 已显示的拖拽弹窗层，按窗口层级由下到上排列（末尾 = 最上层）。
     * 这些弹窗都是铺满窗口的独立 PopupWindow，触摸只会送到最上层窗口，
     * 因此由最上层包装层统一做归属判定：命中下层弹窗则跨窗口转发，实现
     * 「手卡公开等下层弹窗的卡片仍可点击」与「点击的弹窗自动置顶」
     */
    protected static final List<DragFrameLayout> ACTIVE_LAYERS = new ArrayList<>();

    // 字段由叶子类 DraggablePopupHelper 构造器赋值（拆分后声明与构造器分属两层，
    // Java 不允许跨类构造器赋 final，去掉 final，语义与原单一类一致）
    protected Context context;
    protected SharedPreferences prefs;
    protected String dialogId;

    protected int lastX = 0;
    protected int lastY = 0;
    protected boolean hasSavedPosition = false;
    protected void loadPosition() {
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


    /**
     * 弹窗不响应返回键与返回手势（用户规格：所有 dialog 都不因返回键被隐藏，否则决斗
     * 询问窗被误关后无法应答、游戏卡死）。系统 PopupWindow 的 PopupDecorView 在
     * {@code dispatchKeyEvent} 中对 KEYCODE_BACK/KEYCODE_ESCAPE 无条件 {@code dismiss()}，
     * 该内容是弹窗视图树的根，事件根本不会下发到弹窗内容层，故 OnKeyListener/重写
     * dispatchKeyEvent 一律拦不到（RPSDialog 已按本结论验证）；唯一可靠做法是给弹窗窗口
     * 加 FLAG_NOT_FOCUSABLE，使其不接收按键、返回键落到 Activity 由其决定是否响应。
     * <p>
     * 含输入框（EditText/AppCompatEditText）的弹窗例外：窗口不可获焦时键盘无法向其输入文字，
     * 为保输入可用维持原获焦设置，此类弹窗返回键仍会将其关闭。
     */
    public static void ignoreBackKey(PopupWindow popupWindow, View content) {
        if (popupWindow == null || content == null) return;
        if (containsTextInput(content)) return;
        popupWindow.setFocusable(false);
    }

    /** 内容视图中是否存在文本输入框（递归查找，AppCompatEditText 继承自 EditText 一并命中） */
    protected static boolean containsTextInput(View view) {
        if (view instanceof EditText) return true;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (containsTextInput(group.getChildAt(i))) return true;
            }
        }
        return false;
    }

    /**
     * Android 12+ 键盘分层统一修复（用户规格：聊天输入框/输入法不得被任何弹窗压在下面）：
     * S 起系统把软键盘改为相对「IME 目标窗口」分层，普通弹窗窗口反而垫在键盘之上，
     * 键盘与其间被输入法抬高的聊天输入框都被弹窗遮挡、按键落空。PlayerWaitingDialog
     * 已按本方案验证生效，现推广到全部走 {@link #setupDraggablePopup} 包装的弹窗与命令菜单：
     * INPUT_METHOD_NEEDED 使 computeFlags() 为本窗口加 FLAG_ALT_FOCUSABLE_IM，官方语义为
     * 「按输入法目标对待、分层在输入法窗口之下」，键盘显示在 dialog 上方且按键直达 IME；
     * Android 11 及以下键盘恒在所有应用窗口之上，同组合语义相反（自动补全压键盘），
     * 故整块按版本门控；调用方已显式自定义输入法模式的（如自带输入框的弹窗）不覆盖。
     */
    public static void applyImeLayeringFix(PopupWindow popupWindow) {
        if (popupWindow == null) return;
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.S) return;
        if (popupWindow.getInputMethodMode() == PopupWindow.INPUT_METHOD_FROM_FOCUSABLE) {
            popupWindow.setInputMethodMode(PopupWindow.INPUT_METHOD_NEEDED);
        }
    }

    /** 从弹窗内容视图解析其 DragFrameLayout 包装层（包装层即弹窗内容时直接取得，否则向下递归查找） */
    protected static DragFrameLayout findLayerOfPopup(PopupWindow popupWindow) {
        if (popupWindow == null) return null;
        View content = popupWindow.getContentView();
        if (content instanceof DragFrameLayout) return (DragFrameLayout) content;
        if (content instanceof ViewGroup) {
            return findLayerRecursive((ViewGroup) content);
        }
        return null;
    }

    protected static DragFrameLayout findLayerRecursive(ViewGroup parent) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child instanceof DragFrameLayout) return (DragFrameLayout) child;
            if (child instanceof ViewGroup) {
                DragFrameLayout found = findLayerRecursive((ViewGroup) child);
                if (found != null) return found;
            }
        }
        return null;
    }

    /**
     * 为经 {@link #setupDraggablePopup} 包装、内容布局带 btn_dialog_collapse 收缩按钮的
     * dialog 启用收缩模式：点按钮把弹窗缩为紧贴聊天输入框上方、与其同高、保持对话框
     * 原宽与原背景贴图的横条（横条左侧「▲」箭头 + 标题，点击横条恢复原始尺寸），供临时确认场地。
     * 用户规格：收缩一律贴输入框上方，不再与输入框同位而遮挡它（见 {@link #barMetrics}）。
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
    // （et_chat_input）同高、底边贴其上沿；几何解算一律按本文件 DragFrameLayout 的显隐切换方式。
    // ══════════════════════════════════════════════════════════════════════

    /**
     * 收缩横条：圆角条，左侧「▲」向上箭头图标 + 标题文本，点击恢复原始尺寸。
     * 实际使用时由 {@link #styleBarWithDialogContent} 叠加对话框内容根的背景贴图
     * 与左右内边距，本方法的程序化圆角条仅作内容根无背景时的兜底。
     * 高度与底部位置由调用方按 {@link #barMetrics} 结果设置（与聊天输入框同高、贴其上方）。
     */
    protected static LinearLayout createCollapseBar(Context context, String title) {
        LinearLayout bar = new LinearLayout(context);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(YGOUtil.c(R.color.popup_frame_fill));
        bg.setCornerRadius(dp(context, 6));
        bg.setStroke(dp(context, 1), YGOUtil.c(R.color.popup_frame_stroke));
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
    protected static ImageView createArrowButton(Context context, int iconRes) {
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
    protected static ImageView createCollapseHandle(Context context) {
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
    protected static boolean positionHandleBesideTitle(View handle, View content, View titleView) {
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
    protected static void setBarTitle(View bar, String title) {
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
    protected static void styleBarWithDialogContent(View bar, View content) {
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
     * 聊天输入框（et_chat_input）可见时与其同高、横条底边贴其上沿（−安全间隙）；
     * 不可见时取默认固定高度贴窗口底部。
     */
    protected static int[] barMetrics(Context context) {
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
            // 统一贴聊天输入框上方（用户规格：不再与输入框同位遮挡输入框）：
            // 横条底边 = 输入框上沿 − 安全间隙，手动收缩与输入法自动最小化共用同一锚定
            gap = winH - loc[1] + dp(context, COLLAPSE_BAR_SAFETY_DP);
            if (gap < 0) gap = 0;
        }
        return new int[]{winW, winH, barH, gap};
    }

    /** dp 转 px（按弹窗创建时的 DialogScale 密度上下文换算） */
    protected static int dp(Context context, float value) {
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
        // 同 5 参重载：把手式弹窗也不响应返回键/返回手势（含输入框的弹窗除外）
        ignoreBackKey(popupWindow, contentView);
        // 同 5 参重载：把手式弹窗也统一应用 Android 12+ 键盘分层修复
        applyImeLayeringFix(popupWindow);
    }

    /**
     * 从 Context 链解析 Activity 的 decorView，作为弹窗内容区之外触摸事件的转发目标；
     * 非 Activity 上下文或 Activity 已销毁时返回 null（退化为不转发）
     */
    protected static View resolveActivityDecorView(Context context) {
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
    protected static Activity resolveActivity(Context context) {
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
    protected static boolean isPortrait(Context context) {
        DisplayMetrics dm = DialogScale.screenMetrics(context);
        return dm.heightPixels > dm.widthPixels;
    }

    /**
     * 按当前方向解算弹窗显示尺寸。swapForPortrait 且竖屏时（供建主/局域网/单机/录像/玩家等待这类
     * 横屏设计为宽大于高的弹窗）：宽度铺满屏宽（与 Activity 同宽，避免按钮/表单文字被挤得换行），
     * 高度取与宽度一致的正方形比例（边长=屏宽，不超过屏高）；但平板/折叠屏大分辨率近方屏
     * （短边&gt;1080px 且长短边比&lt;1.7，见 {@link DialogScale#isLargeFormatDisplay}）下 4:3 屏宽即屏高的
     * 75%，满屏宽正方形会占据屏高 75% 而显得过大，改按 {@link DialogScale#portraitSquareCapPx}
     * （=弹窗设计宽 420dp）封顶边长，四周留边居中，与常规手机的 dp 量级一致；常规手机不命中该档，
     * 取到 0 封顶值即行为完全不变。横屏（含转回）走 {@link #fitSizeToScreen}
     * 按设计宽高恢复横屏比例。弹窗创建与旋转重排共用。
     */
    protected static int[] orientedFitSize(Context context, int designW, int designH, boolean swapForPortrait) {
        if (swapForPortrait && designW > 0 && designH > 0 && isPortrait(context)) {
            DisplayMetrics dm = DialogScale.screenMetrics(context);
            // 竖屏：正方形，边长取屏宽（竖屏下屏宽≤屏高，自然不超屏高）
            int side = Math.min(dm.widthPixels, dm.heightPixels);
            // 平板/折叠屏大分辨率近方屏：边长按 dp 上限封顶，不再贴满屏宽（常规手机返回 0 不封顶）
            int cap = DialogScale.portraitSquareCapPx(context);
            if (cap > 0 && side > cap) {
                side = cap;
            }
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

    /** 区域锚定三种模式：内容居中于区域 / 贴区域左缘垂直居中 / 贴区域右上角 */
    protected static final int ANCHOR_CENTER = 0;
    protected static final int ANCHOR_DOCK_LEFT = 1;
    protected static final int ANCHOR_TOP_RIGHT = 2;
    /**
     * 按模式解算并应用区域锚定 margin（三种锚定的统一几何实现）：区域与窗口坐标一律
     * 实时测量，贴区域中心/左缘/右上角的公式与原各专用实现一致。返回 true 表示已应用
     * 或参数/视图结构异常（重试也无意义，终止）；返回 false 表示区域或承载窗口此刻
     * 尚未完成布局、几何暂不可解——交由调用方挂布局监听重试。旧写法在窗口 resize
     * 滞后于区域布局的中间态（旋转重建瞬间、IME 升降、决斗记录等铺屏转发层参与重排时
     * 更易命中）下直接 return 且不重试，弹窗 margin 永久停留旧值，表现为「旋转后询问/
     * 宣言弹窗不回 layout_game_right 中央」（用户反馈）。
     */
    protected static boolean applyRegionAnchor(PopupWindow popupWindow, View region, int mode) {
        if (popupWindow == null || region == null) return true;
        // 输入法抬升期间不锚定：此刻内容按「底边贴抬高后的聊天输入框上沿」临时定位，
        // 锚定 margin 会把内容拉回区域中央而与输入框重叠（用户规格：互不遮挡）。返回 false
        // 让重试监听继续等待，输入法收起后由 ImePopupAdjuster 还原时补做锚定，弹窗回到区域中央
        if (ImePopupAdjuster.isAdjusted(popupWindow)) return false;
        if (region.getWidth() <= 0 || region.getHeight() <= 0) return false;
        View wrapper = popupWindow.getContentView();
        if (!(wrapper instanceof ViewGroup)) return true;
        ViewGroup wrapperGroup = (ViewGroup) wrapper;
        if (wrapperGroup.getChildCount() == 0) return true;
        // 三种锚定同语义「每次绝对贴区域」：归零上次拖拽/重排施加的平移（含子视图），
        // 残留平移叠加 margin 会把内容推出可视区（标题缺顶、「▼」消失、弹窗无法取消）
        if (wrapper instanceof DragFrameLayout) {
            ((DragFrameLayout) wrapper).clearDragOffset();
        } else {
            wrapper.setTranslationX(0f);
            wrapper.setTranslationY(0f);
        }
        View content = wrapperGroup.getChildAt(0);
        ViewGroup.LayoutParams raw = content.getLayoutParams();
        if (!(raw instanceof FrameLayout.LayoutParams)) return true;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) raw;

        View window = region.getRootView();
        int winW = window.getWidth();
        int winH = window.getHeight();
        if (winW <= 0 || winH <= 0) return false;
        int[] regionLoc = new int[2];
        region.getLocationInWindow(regionLoc);
        int[] winLoc = new int[2];
        window.getLocationInWindow(winLoc);
        switch (mode) {
            case ANCHOR_DOCK_LEFT: {
                // 内容宽：优先用已解算的布局宽（旋转重排前已置为 fitted 宽），未布局时退回实测宽
                int contentW = lp.width > 0 ? lp.width : content.getWidth();
                // CENTER 下左缘 = winLeft + (winW - contentW)/2，叠加 leftMargin 后贴区域左缘
                lp.leftMargin = regionLoc[0] - (winLoc[0] + (winW - contentW) / 2);
                lp.rightMargin = 0;
                lp.topMargin = (regionLoc[1] + region.getHeight() / 2) - (winLoc[1] + winH / 2);
                lp.bottomMargin = 0;
                break;
            }
            case ANCHOR_TOP_RIGHT: {
                int regionRight = regionLoc[0] + region.getWidth() - winLoc[0];
                // TOP|END：right 边 = 窗口右 - rightMargin，令其贴区域右缘；top 贴区域顶缘
                lp.gravity = Gravity.TOP | Gravity.END;
                lp.leftMargin = 0;
                lp.rightMargin = Math.max(0, winW - regionRight);
                lp.topMargin = regionLoc[1] - winLoc[1];
                lp.bottomMargin = 0;
                break;
            }
            default: {
                // CENTER gravity 下 leftMargin/topMargin 将内容整体平移该偏移量
                lp.leftMargin = (regionLoc[0] + region.getWidth() / 2) - (winLoc[0] + winW / 2);
                lp.rightMargin = 0;
                lp.topMargin = (regionLoc[1] + region.getHeight() / 2) - (winLoc[1] + winH / 2);
                lp.bottomMargin = 0;
                break;
            }
        }
        content.setLayoutParams(lp);
        return true;
    }
    /**
     * 区域/窗口尚未布局时挂布局监听逐帧重试锚定：applyRegionAnchor 应用成功才摘除，
     * 中间不一致态（旋转重建首帧、popup 窗口 resize 滞后、IME 升降中）保留监听等待下次
     * 布局完成再试，保证询问/宣言等区域锚定弹窗最终贴回区域中心。region 若随下一次旋转
     * 重建脱离视图树，其监听随视图销毁自然失效（新弹窗实例重新注册），不会泄漏叠加。
     */
    protected static void addRegionAnchorRetryListener(
            final PopupWindow popupWindow, final View region, final int mode) {
        region.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        if (applyRegionAnchor(popupWindow, region, mode)) {
                            region.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        }
                    }
                });
    }

    static class DragFrameLayout extends DragFrameLayoutImpl {
        DragFrameLayout(Context context, SharedPreferences prefs, String dialogId) {
            super(context, prefs, dialogId);
        }
    }
}

