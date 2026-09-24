package cn.garymb.ygomobile.ui.dialogs;

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
        popupWindow = new PopupWindow(contentView, dp2px(DIALOG_WIDTH_DP),
                ViewGroup.LayoutParams.WRAP_CONTENT);
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
        contentView = LayoutInflater.from(context)
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
            Button btn = new Button(context);
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
    }

    public void dismiss() {
        try {
            if (popupWindow != null && popupWindow.isShowing()) popupWindow.dismiss();
        } catch (Exception ignored) {
        }
        showing = false;
    }

    private int dp2px(float dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}