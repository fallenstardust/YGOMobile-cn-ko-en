package cn.garymb.ygomobile.ui.dialogs;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.YGOProActivity;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.utils.DialogScale;

import ocgcore.DataManager;

/**
 * 选项选择弹窗，效仿 gframe game.cpp L828-851 wOptions 与
 * client_field.cpp ShowSelectOption / event_handler.cpp BUTTON_OPTION 应答逻辑：
 * 通讯收到 MSG_SELECT_OPTION（效果处理中需要玩家选择效果分支）时，
 * 将经 DataManager.getDesc 解析的选项文字（<=0x7ff 为系统字符串；
 * 否则卡号*16+n 取 cdb texts.str1~str16 缓存进 Card 的脚本提示文字）列成按钮；
 * 点击选项关闭弹窗并回调其索引，由调用方发送 CTOS_RESPONSE
 * （int32 索引，playerop.cpp select_option 校验索引范围）。
 *
 * 与 wOptions（关闭按钮隐藏）一致：本弹窗不可取消，必须点击选项才能关闭。
 */
public class OptionDialog {

    public interface OnOptionSelectedListener {
        void onSelected(int index);
    }

    public interface OnDismissListener {
        void onDismiss();
    }

    /** 弹窗宽度，与 YesOrNoDialog 一致（对应 wOptions 390 设计宽） */
    private static final int DIALOG_WIDTH_DP = 280;
    private static final int ITEM_BOTTOM_MARGIN_DP = 4;
    private static final int BUTTON_MIN_HEIGHT_DP = 30;
    /** 选项过多收缩滚动区时的最小高度 */
    private static final int MIN_SCROLL_HEIGHT_DP = 60;
    /** 选项少于此数量时，按钮组在弹窗内垂直居中排列（否则顶部对齐并可滚动） */
    private static final int CENTER_VERTICAL_MAX_OPTIONS = 5;

    /** 当前正在显示的选项弹窗（去重用），由静态工厂 showOptionDialog 维护 */
    private static OptionDialog current;

    private final Context context;
    private PopupWindow popupWindow;
    private View contentView;
    /** 收缩模式壳层（本 dialog 私有实现）：原内容 + 底部收缩横条 + 左上收缩把手 */
    private CollapseShell collapseShell;
    private String title = "";
    private List<String> options;
    private OnOptionSelectedListener selectListener;
    private OnDismissListener dismissListener;
    private boolean showing;

    /**
     * MSG_SELECT_OPTION 静态工厂（供 ShowDialogUtil.showOptionDialog 委托调用）：
     * data 为 player(1) + count(1) + count×desc(4)。选项经 DataManager.getDesc 解析：
     * <=0x7ff 为系统字符串，否则 卡号*16+n 取 cdb 缓存进 Card.Stras 的脚本提示文字；
     * 标题取系统字符串 555（"Select an option."）。无选项兜底应答 0，避免通讯挂起。
     * 点击选项发送 CTOS_RESPONSE（int32 索引，playerop.cpp select_option 校验范围）。
     */
    public static void showOptionDialog(YGOProActivity activity, ByteBuffer data) {
        if (data == null || data.remaining() < 2) return;
        data.get(); // selecting_player
        int count = data.get() & 0xFF;
        List<String> options = new ArrayList<>();
        for (int i = 0; i < count && data.remaining() >= 4; i++) {
            int descId = data.getInt();
            options.add(DataManager.get().getDesc(descId, "Option " + (i + 1)));
        }
        if (options.isEmpty()) {
            // 无可解析选项时兜底应答 0，避免通讯挂起（core 侧会校验索引合法性）
            activity.sendResponseInt(0);
            return;
        }
        if (current != null && current.isShowing()) return;
        OptionDialog dialog = new OptionDialog(activity);
        current = dialog;
        dialog.setTitle(optionTitleText(activity))
                .setOptions(options)
                .setOnOptionSelectedListener(activity::sendResponseInt)
                .setOnDismissListener(() -> current = null);
        dialog.show();
    }

    /**
     * 选项弹窗标题：系统字符串 555；消费 selectHint 避免残留影响后续选卡标题。
     * 用链式访问 activity.getEngine().getField().selectHint，无需导入 GameEngine/GameField。
     */
    private static String optionTitleText(YGOProActivity activity) {
        if (activity.getEngine() != null && activity.getEngine().getField() != null) {
            activity.getEngine().getField().selectHint = 0;
        }
        return DataManager.get().getStringManager().getSystemString(555, "请选择一项");
    }

    public OptionDialog(Context context) {
        this.context = context;
    }

    public OptionDialog setTitle(String title) {
        this.title = title;
        return this;
    }

    public OptionDialog setOptions(List<String> options) {
        this.options = options;
        return this;
    }

    public OptionDialog setOnOptionSelectedListener(OnOptionSelectedListener listener) {
        this.selectListener = listener;
        return this;
    }

    public OptionDialog setOnDismissListener(OnDismissListener listener) {
        this.dismissListener = listener;
        return this;
    }

    public boolean isShowing() {
        return showing && popupWindow != null && popupWindow.isShowing();
    }

    public void show() {
        if (isShowing()) return;
        if (options == null || options.isEmpty()) return;
        if (!(context instanceof Activity)) return;

        build();
        // 收缩模式：本 dialog 自带的壳层实现，把手用内容 XML 声明的 btn_dialog_collapse，
        // 收缩后与聊天输入框同高同位，点横条恢复
        collapseShell = new CollapseShell(contentView, title, true);
        popupWindow = new PopupWindow(collapseShell.getView(), dp2px(DIALOG_WIDTH_DP),
                ViewGroup.LayoutParams.WRAP_CONTENT);
        collapseShell.attachPopup(popupWindow);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        // 不可取消：对齐 gframe wOptions 无关闭按钮，必须点击选项才能关闭
        // 1) outsideTouchable=false 外部点击不关闭；2) focusable=false BACK 键无法 dismiss；
        // 3) touchable=true 按钮仍正常接收触摸
        popupWindow.setOutsideTouchable(false);
        popupWindow.setFocusable(false);
        popupWindow.setTouchable(true);
        popupWindow.setTouchInterceptor((v, event) -> {
            // 双保险：吞掉 ACTION_OUTSIDE，防止任何窗口外触摸事件进入
            if (event.getAction() == MotionEvent.ACTION_OUTSIDE) return true;
            return false;
        });
        popupWindow.setOnDismissListener(() -> {
            showing = false;
            if (collapseShell != null) collapseShell.detach();
            if (dismissListener != null) dismissListener.onDismiss();
        });

        Activity activity = (Activity) context;
        View gameRight = activity.findViewById(R.id.layout_game_right);
        if (gameRight != null && gameRight.getWidth() > 0 && gameRight.getHeight() > 0) {
            // 已布局完成：直接在 layout_game_right 内居中显示
            showCenteredInGameRight(gameRight);
            showing = true;
        } else if (gameRight != null) {
            // 宽度尚为 0（决斗 UI 刚切为可见的同帧），等布局完成后再显示
            showing = true;
            gameRight.getViewTreeObserver().addOnGlobalLayoutListener(
                    new ViewTreeObserver.OnGlobalLayoutListener() {
                        @Override
                        public void onGlobalLayout() {
                            gameRight.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                            if (!showing) return; // 等待期间已被 dismiss 取消
                            showCenteredInGameRight(gameRight);
                        }
                    });
        } else {
            // 极端兜底：找不到锚点时屏幕居中
            popupWindow.showAtLocation(activity.getWindow().getDecorView(), Gravity.CENTER, 0, 0);
            showing = true;
        }
    }

    private void build() {
        contentView = LayoutInflater.from(DialogScale.wrap(context))
                .inflate(R.layout.popup_window_option, null);
        TextView tvTitle = contentView.findViewById(R.id.tv_option_title);
        if (title != null && !title.isEmpty()) {
            tvTitle.setVisibility(View.VISIBLE);
            tvTitle.setText(title);
        } else {
            tvTitle.setVisibility(View.GONE);
        }
        LinearLayout container = contentView.findViewById(R.id.layout_option_items);
        container.removeAllViews();
        for (int i = 0; i < options.size(); i++) {
            Button btn = new Button(DialogScale.wrap(context));
            btn.setText(options.get(i));
            btn.setTextColor(Color.WHITE);
            btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            btn.setAllCaps(false);
            // button_n/button_p 九宫格对应 gframe tButton_L/tButton_L_pressed
            btn.setBackgroundResource(R.drawable.button3_bg);
            btn.setMinHeight(dp2px(BUTTON_MIN_HEIGHT_DP));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = dp2px(ITEM_BOTTOM_MARGIN_DP);
            btn.setLayoutParams(lp);
            final int index = i;
            btn.setOnClickListener(v -> {
                // 点击瞬间先隐藏弹窗再回调（对齐 gframe HideElement(wOptions, true) 后发送响应）
                dismiss();
                if (selectListener != null) selectListener.onSelected(index);
            });
            container.addView(btn);
        }
        // 选项少于 5 个时：按钮组在弹窗内垂直居中排列（而非顶部对齐）。
        // 预留高度在 showCenteredInGameRight 中按「5 个按钮」计算；这里开启 fillViewport
        // 并让容器填满滚动区、内容垂直居中即可。
        if (options.size() < CENTER_VERTICAL_MAX_OPTIONS) {
            ScrollView scroll = contentView.findViewById(R.id.scroll_option);
            if (scroll != null) scroll.setFillViewport(true);
            ViewGroup.LayoutParams clp = container.getLayoutParams();
            if (clp != null) {
                clp.height = ViewGroup.LayoutParams.MATCH_PARENT;
                container.setLayoutParams(clp);
            }
            container.setGravity(Gravity.CENTER_VERTICAL);
        }
    }

    /** 水平+垂直均在 layout_game_right 实际范围内居中，且整体不越出该区域 */
    private void showCenteredInGameRight(View gameRight) {
        ScrollView scroll = contentView.findViewById(R.id.scroll_option);
        boolean centerButtons = options != null && options.size() < CENTER_VERTICAL_MAX_OPTIONS;
        if (scroll != null) {
            ViewGroup.LayoutParams slp = scroll.getLayoutParams();
            if (centerButtons) {
                // 少于 5 个选项：为滚动区预留「5 个按钮」的固定高度，
                // 使按钮组可在其中垂直居中（fillViewport + 容器 gravity 已在 build 中设置）
                slp.height = dp2px(CENTER_VERTICAL_MAX_OPTIONS
                        * (BUTTON_MIN_HEIGHT_DP + ITEM_BOTTOM_MARGIN_DP));
            } else {
                // 复位上一次的高度限制，先按自然高度测量
                slp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
            }
            scroll.setLayoutParams(slp);
        }
        contentView.measure(
                View.MeasureSpec.makeMeasureSpec(dp2px(DIALOG_WIDTH_DP), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int popupW = contentView.getMeasuredWidth();
        int popupH = contentView.getMeasuredHeight();
        int regionW = gameRight.getWidth();
        int regionH = gameRight.getHeight();
        // 选项过多超出区域高度时（ScrollView maxHeight 仅 API30+ 生效，minSdk 25），
        // 收缩滚动区，保证弹窗整体留在 layout_game_right 内
        if (scroll != null && popupH > regionH) {
            ViewGroup.LayoutParams slp = scroll.getLayoutParams();
            slp.height = Math.max(dp2px(MIN_SCROLL_HEIGHT_DP),
                    scroll.getMeasuredHeight() - (popupH - regionH));
            scroll.setLayoutParams(slp);
            contentView.measure(
                    View.MeasureSpec.makeMeasureSpec(dp2px(DIALOG_WIDTH_DP), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            popupH = contentView.getMeasuredHeight();
        }
        int[] loc = new int[2];
        gameRight.getLocationInWindow(loc);
        int x = loc[0] + (regionW - popupW) / 2;
        int y = loc[1] + (regionH - popupH) / 2;
        // 越界钳制到 layout_game_right 区域左上角（而非窗口左上角）
        if (x < loc[0]) x = loc[0];
        if (y < loc[1]) y = loc[1];
        popupWindow.showAtLocation(gameRight, Gravity.NO_GRAVITY, x, y);
        // 记住展开态几何，收缩把手/恢复据此在展开与底部横条间 resize 窗口
        if (collapseShell != null) collapseShell.rememberExpanded(x, y, popupW, popupH);
    }

    public void dismiss() {
        try {
            if (popupWindow != null && popupWindow.isShowing()) popupWindow.dismiss();
        } catch (Exception ignored) {
        }
        showing = false;
    }

    private int dp2px(float dp) {
        return DialogScale.dpToPx(context, dp);
    }

    // ════════════════════════════════════════════════════════════════════
    // 本 dialog 的收缩模式实现：把弹窗临时缩小成贴屏幕底部、与聊天输入框
    // 同高同位的横条（横条左侧「▲」箭头 + 标题，点击恢复），展开态由内容布局
    // XML 声明的 btn_dialog_collapse 作收缩把手；收缩/恢复经 popup.update 在
    // 「展开几何」与「底部横条几何」之间切换窗口尺寸与位置。
    // 横条保持对话框原宽与水平位置（不拉伸整屏宽）；聊天输入框不可见时
    // 兜底固定高度贴窗口底部。
    // ════════════════════════════════════════════════════════════════════

    /** 横条内标题 TextView 的 tag，供创建/更新横条标题时定位 */
    private static final String TAG_BAR_TITLE = "collapse_bar_title";
    /** 聊天输入框不可见时的横条兜底高度（dp） */
    private static final int FALLBACK_BAR_HEIGHT_DP = 40;
    /** 横条最小高度（dp），保证可点击目标不小于舒适触控尺寸 */
    private static final int MIN_BAR_HEIGHT_DP = 28;

    private class CollapseShell {
        private final FrameLayout shell;
        private final View original;
        private final View bar;
        private final View handle;
        private PopupWindow popup;
        /** 旋转/布局变化重锚回调的宿主 decor 与监听（detach 时摘除） */
        private View decorView;
        private ViewTreeObserver.OnGlobalLayoutListener reanchorListener;
        /** 最近一次已应用的横条几何（相等则跳过重复 popup.update，防全局布局回调高频触发） */
        private int lastBarLeft = -1, lastBarWidth, lastBarTop;
        /** 展开态几何（showCenteredInGameRight 定位成功后传入，窗口坐标） */
        private boolean geometrySaved;
        private int savedX, savedY, savedW, savedH;
        private boolean collapsed;

        CollapseShell(View originalContent, String barTitle, boolean matchParentWidth) {
            this.original = originalContent;
            shell = new FrameLayout(context);
            shell.addView(originalContent, new FrameLayout.LayoutParams(
                    matchParentWidth ? ViewGroup.LayoutParams.MATCH_PARENT
                            : ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));

            bar = createCollapseBar((barTitle == null || barTitle.isEmpty()) ? "点击展开" : barTitle);
            styleBarWithDialogContent(bar, originalContent);
            bar.setVisibility(View.GONE);
            bar.setOnClickListener(v -> expand());
            shell.addView(bar, new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

            // 把手来自 dialog 布局 XML（btn_dialog_collapse）：位置由布局声明，免程序化贴位
            handle = originalContent.findViewById(R.id.btn_dialog_collapse);
            handle.setOnClickListener(v -> collapse());
        }

        View getView() {
            return shell;
        }

        void attachPopup(PopupWindow popupWindow) {
            this.popup = popupWindow;
            // 旋转重建视图树后聊天输入框/区域重新排布，收缩态经 Activity decor 的全局布局
            // 回调重锚横条，保持与聊天输入框同高同位、在 layout_game_right 内居中
            if (context instanceof Activity) {
                decorView = ((Activity) context).getWindow().getDecorView();
                reanchorListener = this::reanchor;
                decorView.getViewTreeObserver().addOnGlobalLayoutListener(reanchorListener);
            }
        }

        /** 弹窗关闭时摘除重锚回调 */
        void detach() {
            if (decorView != null && reanchorListener != null) {
                ViewTreeObserver vto = decorView.getViewTreeObserver();
                if (vto.isAlive()) vto.removeOnGlobalLayoutListener(reanchorListener);
            }
            decorView = null;
            reanchorListener = null;
        }

        /** 记录展开态几何：showAtLocation 定位成功后调用，把手自此可收缩 */
        void rememberExpanded(int x, int y, int w, int h) {
            savedX = x;
            savedY = y;
            savedW = w;
            savedH = h;
            geometrySaved = true;
        }

        /** 收缩：窗口 resize 为与聊天输入框同高同位、在 layout_game_right 内居中的横条，内容隐藏 */
        void collapse() {
            if (collapsed || popup == null || !popup.isShowing() || !geometrySaved) return;
            int[] g = resolveBarGeometry();
            try {
                popup.update(g[0], g[2], g[1], g[3]);
            } catch (Exception ignored) {
                return;
            }
            lastBarLeft = g[0];
            lastBarWidth = g[1];
            lastBarTop = g[2];
            applyState(true);
        }

        /** 收缩态遇旋转/布局变化重锚：重解底部横条几何并应用（不变则跳过） */
        void reanchor() {
            if (!collapsed || popup == null || !popup.isShowing()) return;
            int[] g = resolveBarGeometry();
            if (g[0] == lastBarLeft && g[1] == lastBarWidth && g[2] == lastBarTop) return;
            try {
                popup.update(g[0], g[2], g[1], g[3]);
            } catch (Exception ignored) {
                return;
            }
            lastBarLeft = g[0];
            lastBarWidth = g[1];
            lastBarTop = g[2];
        }

        /**
         * 底部横条几何 {left, width, top, barHeight}（窗口坐标，供 popup.update）：
         * 水平在 layout_game_right 区域内居中（区域未布局时退回展开态原位），
         * 高度与底边按 barMetrics 与聊天输入框对齐；宽度保持展开态弹窗原宽
         * 并限幅到区域宽，使横条始终留在区域内
         */
        private int[] resolveBarGeometry() {
            int[] m = barMetrics();
            Activity activity = context instanceof Activity ? (Activity) context : null;
            View gameRight = activity != null ? activity.findViewById(R.id.layout_game_right) : null;
            int regionLeft = 0;
            int regionW = m[0];
            if (gameRight != null && gameRight.getWidth() > 0) {
                int[] loc = new int[2];
                gameRight.getLocationInWindow(loc);
                regionLeft = loc[0];
                regionW = Math.min(gameRight.getWidth(), m[0]);
            }
            int bw = savedW > 0 ? Math.min(savedW, regionW) : regionW;
            int left = gameRight != null && gameRight.getWidth() > 0
                    ? regionLeft + (regionW - bw) / 2 : savedX;
            if (left < 0) left = 0;
            if (left + bw > m[0]) left = Math.max(0, m[0] - bw);
            int top = m[1] - m[3] - m[2];
            if (top < 0) top = 0;
            return new int[]{left, bw, top, m[2]};
        }

        /** 恢复：窗口回到展开几何，内容重新显示 */
        void expand() {
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
    }

    /** 收缩横条：圆角条，左侧「▲」向上箭头图标 + 标题文本，点击恢复原始尺寸；
     *  实际使用时叠加内容根背景贴图，程序化圆角条仅作内容根无背景时的兜底 */
    private LinearLayout createCollapseBar(String title) {
        LinearLayout bar = new LinearLayout(context);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xE0303030);
        bg.setCornerRadius(dp2px(6));
        bg.setStroke(dp2px(1), 0xFF7A7A7A);
        bar.setBackground(bg);
        bar.setClickable(true);
        int padH = dp2px(8);
        bar.setPadding(padH, 0, padH, 0);

        ImageView arrow = createArrowButton(R.drawable.baseline_keyboard_arrow_up_24);
        // 箭头仅作“按钮”外观的指示：不可点击，否则它会吞掉横条本身的恢复点击（整条可点即恢复）
        arrow.setClickable(false);
        arrow.setFocusable(false);
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                dp2px(22), LinearLayout.LayoutParams.MATCH_PARENT);
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
        tlp.leftMargin = dp2px(6);
        bar.addView(tvTitle, tlp);
        return bar;
    }

    /** 收缩态箭头按钮：selected_light 描边圆角底 + 指定箭头图标，图标居中自适应缩放 */
    private ImageView createArrowButton(int iconRes) {
        ImageButton button = new ImageButton(context);
        button.setBackgroundResource(R.drawable.selected_light);
        button.setImageResource(iconRes);
        button.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        int pad = dp2px(2);
        button.setPadding(pad, pad, pad, pad);
        button.setMinimumWidth(0);
        button.setMinimumHeight(0);
        button.setAdjustViewBounds(true);
        return button;
    }

    /** 收缩横条复用对话框内容根的背景贴图与左右内边距（克隆独立实例避免 bounds 互串） */
    private void styleBarWithDialogContent(View bar, View content) {
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
    private int[] barMetrics() {
        Activity activity = context instanceof Activity ? (Activity) context : null;
        View decor = activity != null ? activity.getWindow().getDecorView() : null;
        int winW = decor != null && decor.getWidth() > 0
                ? decor.getWidth() : context.getResources().getDisplayMetrics().widthPixels;
        int winH = decor != null && decor.getHeight() > 0
                ? decor.getHeight() : context.getResources().getDisplayMetrics().heightPixels;
        int barH = dp2px(FALLBACK_BAR_HEIGHT_DP);
        int gap = 0;
        View chat = activity != null ? activity.findViewById(R.id.et_chat_input) : null;
        if (chat != null && chat.isShown() && chat.getHeight() > 0) {
            barH = Math.max(chat.getHeight(), dp2px(MIN_BAR_HEIGHT_DP));
            int[] loc = new int[2];
            chat.getLocationInWindow(loc);
            gap = winH - (loc[1] + chat.getHeight());
            if (gap < 0) gap = 0;
        }
        return new int[]{winW, winH, barH, gap};
    }
}