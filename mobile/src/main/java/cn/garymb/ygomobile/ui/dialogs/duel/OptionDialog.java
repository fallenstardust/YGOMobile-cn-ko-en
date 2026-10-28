package cn.garymb.ygomobile.ui.dialogs.duel;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
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
import cn.garymb.ygomobile.utils.DraggablePopupHelper;
import cn.garymb.ygomobile.utils.ImePopupAdjuster;

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
 *
 * 拖动与收缩：接入 {@link DraggablePopupHelper} 通用机制（对齐 YesOrNoDialog 等 17 处
 * 弹窗）——setupDraggablePopup 用 DragFrameLayout 包装提供拖动、记忆位置与跨弹窗归属；
 * enableCollapse 借助内容布局声明的 btn_dialog_collapse 作把手，收缩到与聊天输入框
 * 同高同位的横条。中心定位仍限幅在 layout_game_right 区域内（centerPopupInRegion）。
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
    private DraggablePopupHelper draggableHelper;
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
     * 决斗结束 / 退出对战时关闭可能残留的选项弹窗（由 ShowDialogUtil.dismissOpenGameDialogs 调用）：
     * MSG_SELECT_OPTION 的弹窗不可取消，若结束时仍在显示会残留遮挡返回界面，故统一主动关闭。
     */
    public static void dismissCurrent() {
        if (current != null) {
            current.dismiss();
            current = null;
        }
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
        Activity activity = (Activity) context;
        final View gameRight = activity.findViewById(R.id.layout_game_right);
        // 内容尺寸预先按 layout_game_right 限幅：选项过多时压缩 ScrollView 高度，
        // 让 DragFrameLayout 测量到的设计尺寸就是不越界的实际尺寸（DraggablePopupHelper
        // 的 translation/记忆位置以此尺寸为基准，事后再改尺寸会让记忆几何失准）
        clampScrollHeightToRegion(gameRight);

        popupWindow = new PopupWindow(contentView, dp2px(DIALOG_WIDTH_DP),
                ViewGroup.LayoutParams.WRAP_CONTENT, false);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        // 不可取消：对齐 gframe wOptions 无关闭按钮，必须点击选项才能关闭
        // 1) outsideTouchable=false 外部点击不关闭；2) focusable=false BACK 键无法 dismiss；
        // 3) touchable=true 按钮仍正常接收触摸；DragFrameLayout 内部处理拖动/收缩把手
        popupWindow.setOutsideTouchable(false);
        popupWindow.setFocusable(false);
        popupWindow.setTouchable(true);
        // 输入法弹出时让本弹窗窗口被压缩、内容随之上移（ADJUST_NOTHING 会把键盘压在全屏弹窗后面）；
        // 键盘收起后由 DragFrameLayout.onSizeChanged 按区域重新解算定位，使 dialog 回到原位。
        popupWindow.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        popupWindow.setTouchInterceptor((v, event) -> {
            // 双保险：吞掉 ACTION_OUTSIDE，防止任何窗口外触摸事件进入
            if (event.getAction() == MotionEvent.ACTION_OUTSIDE) return true;
            return false;
        });
        popupWindow.setOnDismissListener(() -> {
            showing = false;
            if (dismissListener != null) dismissListener.onDismiss();
        });

        // 拖动 + 收缩 + 记忆位置 + 跨弹窗归属：与 YesOrNoDialog 一致的通用机制。
        // setupDraggablePopup 会把 popup 尺寸改为 MATCH_PARENT，把 contentView 包进
        // DragFrameLayout 并以设计尺寸居中；随后 centerPopupInRegion 用 translation
        // 把内容挪到 layout_game_right 中心（游戏区域内居中而非全屏居中）。
        draggableHelper = new DraggablePopupHelper(context, "game_dialog_option");
        draggableHelper.setupDraggablePopup(popupWindow, contentView,
                dp2px(DIALOG_WIDTH_DP), ViewGroup.LayoutParams.WRAP_CONTENT);
        TextView tvTitle = contentView.findViewById(R.id.tv_option_title);
        DraggablePopupHelper.enableCollapse(popupWindow, tvTitle,
                title == null || title.isEmpty() ? "点击展开" : title);

        if (gameRight != null && gameRight.getWidth() > 0 && gameRight.getHeight() > 0) {
            showAnchoredInGameRight(gameRight);
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
                            clampScrollHeightToRegion(gameRight);
                            showAnchoredInGameRight(gameRight);
                        }
                    });
        } else {
            // 极端兜底：找不到锚点时按 decorView 屏幕居中
            View decor = activity.getWindow().getDecorView();
            DraggablePopupHelper.centerPopupInRegion(popupWindow, null);
            draggableHelper.showPopup(popupWindow, decor);
            ImePopupAdjuster.attach(popupWindow);
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
        // 预留高度在 clampScrollHeightToRegion 中按「5 个按钮」计算；这里开启 fillViewport
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

    /**
     * 已布局则把弹窗居中到 layout_game_right 区域并显示；未布局时回退到全屏居中。
     * centerPopupInRegion 通过 DragFrameLayout 的 translation 把 280dp 内容平移到区域中心，
     * showPopup 走 helper 的显示路径以便记忆位置/归属登记与旋转重排生效。
     */
    private void showAnchoredInGameRight(View gameRight) {
        DraggablePopupHelper.centerPopupInRegion(popupWindow, gameRight);
        draggableHelper.showPopup(popupWindow, gameRight);
        // 输入法抬升联动（用户规格）：抬键盘时本弹窗底边贴聊天输入框上沿，
        // 收起后回到 layout_game_right 中央，逻辑统一在 ImePopupAdjuster
        ImePopupAdjuster.attach(popupWindow);
    }

    /**
     * 若 layout_game_right 已布局且内容超出区域高度，压缩 ScrollView 使弹窗不越界；
     * 选项数 &lt; {@link #CENTER_VERTICAL_MAX_OPTIONS} 时预置「5 按钮」固定高度供垂直居中，
     * 与旧实现的 showCenteredInGameRight 尺寸解算语义一致（此处只负责内容尺寸，
     * 定位交给 centerPopupInRegion）。
     */
    private void clampScrollHeightToRegion(View gameRight) {
        if (contentView == null) return;
        ScrollView scroll = contentView.findViewById(R.id.scroll_option);
        if (scroll == null) return;
        boolean centerButtons = options != null && options.size() < CENTER_VERTICAL_MAX_OPTIONS;
        ViewGroup.LayoutParams slp = scroll.getLayoutParams();
        if (centerButtons) {
            slp.height = dp2px(CENTER_VERTICAL_MAX_OPTIONS
                    * (BUTTON_MIN_HEIGHT_DP + ITEM_BOTTOM_MARGIN_DP));
        } else {
            slp.height = ViewGroup.LayoutParams.WRAP_CONTENT;
        }
        scroll.setLayoutParams(slp);
        if (gameRight == null || gameRight.getWidth() <= 0 || gameRight.getHeight() <= 0) return;
        contentView.measure(
                View.MeasureSpec.makeMeasureSpec(dp2px(DIALOG_WIDTH_DP), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int popupH = contentView.getMeasuredHeight();
        int regionH = gameRight.getHeight();
        // ScrollView maxHeight 仅 API30+ 生效，minSdk 25 走此手动限幅
        if (popupH > regionH) {
            slp = scroll.getLayoutParams();
            slp.height = Math.max(dp2px(MIN_SCROLL_HEIGHT_DP),
                    scroll.getMeasuredHeight() - (popupH - regionH));
            scroll.setLayoutParams(slp);
            contentView.measure(
                    View.MeasureSpec.makeMeasureSpec(dp2px(DIALOG_WIDTH_DP), View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        }
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
}
