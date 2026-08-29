package cn.garymb.ygomobile.ui.dialogs;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.util.List;

import cn.garymb.ygomobile.YGOProActivity;
import cn.garymb.ygomobile.lite.R;

/**
 * 卡片命令菜单（对应桌面版 gframe 的 wCmdMenu）：
 * 点击场上/手卡卡片时，以点击位置作为菜单左下角弹出，尺寸较小；
 * 点击菜单以外区域自动关闭。菜单项由卡片 cmdFlag 动态生成。
 */
public class CmdMenuDialog {

    private static final int MENU_WIDTH_DP = 120;

    private final YGOProActivity activity;
    private final PopupWindow popupWindow;
    private final View contentView;
    private final TextView tvTitle;
    private final LinearLayout layoutItems;

    public CmdMenuDialog(YGOProActivity activity) {
        this.activity = activity;
        contentView = LayoutInflater.from(activity).inflate(R.layout.popup_window_cmd_menu, null);
        tvTitle = contentView.findViewById(R.id.tv_cmd_menu_title);
        layoutItems = contentView.findViewById(R.id.layout_cmd_menu_items);

        popupWindow = new PopupWindow(contentView, dp(MENU_WIDTH_DP),
                ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setFocusable(true);
        popupWindow.setOutsideTouchable(true);
    }

    public void setTitle(String title) {
        if (title == null || title.isEmpty()) {
            tvTitle.setVisibility(View.GONE);
        } else {
            tvTitle.setVisibility(View.VISIBLE);
            tvTitle.setText(title);
        }
    }

    /**
     * 菜单项与动作一一对应；末尾自动追加“取消”项
     */
    public void setItems(List<String> labels, List<Runnable> actions) {
        layoutItems.removeAllViews();
        int count = Math.min(labels.size(), actions.size());
        for (int i = 0; i < count; i++) {
            final Runnable action = actions.get(i);
            Button btn = createItemButton(labels.get(i));
            btn.setOnClickListener(v -> {
                dismiss();
                if (action != null) action.run();
            });
            layoutItems.addView(btn);
        }
        Button btnCancel = createItemButton(activity.getString(R.string.Cancel));
        btnCancel.setOnClickListener(v -> dismiss());
        layoutItems.addView(btnCancel);
    }

    /**
     * 以点击位置为菜单左下角显示
     *
     * @param anchorView 接收点击的场地 View（用于把触点换算为屏幕坐标）
     * @param tapX       相对 anchorView 的点击 X
     * @param tapY       相对 anchorView 的点击 Y
     */
    public void show(View anchorView, float tapX, float tapY) {
        if (anchorView == null || anchorView.getWindowToken() == null) return;
        if (popupWindow.isShowing()) {
            popupWindow.dismiss();
        }

        View decor = activity.getWindow().getDecorView();
        int screenW = decor.getWidth() > 0 ? decor.getWidth()
                : activity.getResources().getDisplayMetrics().widthPixels;
        int screenH = decor.getHeight() > 0 ? decor.getHeight()
                : activity.getResources().getDisplayMetrics().heightPixels;

        int widthPx = dp(MENU_WIDTH_DP);
        contentView.measure(
                View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(screenH, View.MeasureSpec.AT_MOST));
        int menuHeight = contentView.getMeasuredHeight();
        int maxHeight = screenH / 2;
        if (menuHeight > maxHeight) {
            popupWindow.setHeight(maxHeight);
            menuHeight = maxHeight;
        } else {
            popupWindow.setHeight(ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        int[] loc = new int[2];
        anchorView.getLocationOnScreen(loc);
        int clickX = loc[0] + (int) tapX;
        int clickY = loc[1] + (int) tapY;

        // 点击点=菜单左下角：底边对齐触点；上方放不下则翻转到触点下方显示，并钳制四边
        int left = clickX;
        int top = clickY - menuHeight;
        if (top < 0) top = clickY;
        if (left + widthPx > screenW) left = screenW - widthPx;
        if (left < 0) left = 0;
        if (top + menuHeight > screenH) top = screenH - menuHeight;
        if (top < 0) top = 0;

        popupWindow.showAtLocation(decor, Gravity.TOP | Gravity.START, left, top);
    }

    public boolean isShowing() {
        return popupWindow.isShowing();
    }

    public void dismiss() {
        if (popupWindow.isShowing()) {
            popupWindow.dismiss();
        }
    }

    private Button createItemButton(String text) {
        Button btn = new Button(activity);
        btn.setText(text);
        btn.setTextColor(0xFFFFFFFF);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        btn.setAllCaps(false);
        btn.setBackgroundColor(0xFF335577);
        btn.setMinHeight(dp(28));
        btn.setPadding(dp(6), dp(2), dp(6), dp(2));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(2);
        btn.setLayoutParams(lp);
        return btn;
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}