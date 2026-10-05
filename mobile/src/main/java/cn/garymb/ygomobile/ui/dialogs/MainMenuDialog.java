package cn.garymb.ygomobile.ui.dialogs;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.YGOProActivity;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.utils.DraggablePopupHelper;
import cn.garymb.ygomobile.utils.DialogScale;
import ocgcore.DataManager;
import ocgcore.StringManager;

public class MainMenuDialog {

    /** 系统字符串管理器：主菜单按钮文字从 strings.conf 取（复用 gframe 既有索引） */
    private static final StringManager mStringManager = DataManager.get().getStringManager();

    private final YGOProActivity activity;
    private PopupWindow popupWindow;
    private DraggablePopupHelper draggableHelper;
    
    /** 固定设计宽度：无论横竖屏都使用此尺寸（参照竖屏标准，高度不变仅加宽） */
    private static final int DESIGN_WIDTH_DP = 800;

    public MainMenuDialog(YGOProActivity activity) {
        this.activity = activity;

        View layoutMainMenu = LayoutInflater.from(DialogScale.wrap(activity)).inflate(R.layout.popup_window_main_menu, null);
        TextView tvVersion = layoutMainMenu.findViewById(R.id.tv_version);
        tvVersion.setText(getVersionText());

        bindButtons(layoutMainMenu);

        // 使用 WrapContent 让内容决定宽度，通过 DragFrameLayout 保持固定尺寸
        popupWindow = new PopupWindow(layoutMainMenu,
                ViewGroup.LayoutParams.WRAP_CONTENT, 
                ViewGroup.LayoutParams.WRAP_CONTENT, true);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setOutsideTouchable(true);
        popupWindow.setFocusable(false);
        popupWindow.setAnimationStyle(R.style.PopupCenterAnimation);

        draggableHelper = new DraggablePopupHelper(activity, "main_menu");
        // 关键修改：最后一个参数 false - 不在横竖屏间切换宽高比例，始终保持固定设计尺寸
        draggableHelper.setupDraggablePopup(popupWindow, layoutMainMenu,
                DESIGN_WIDTH_DP, ViewGroup.LayoutParams.MATCH_PARENT, false);
    }

    public void showMainMenu() {
        // 注册自定义旋转重排逻辑：确保横屏转竖屏/竖屏转横屏时对话框按新方向重算宽度
        if (popupWindow != null && popupWindow.getContentView() instanceof FrameLayout) {
            draggableHelper.registerOrientationRelayout(popupWindow, this::relayoutOnRotation);
        }
        
        View decor = activity.getWindow().getDecorView();
        if (decor.getWindowToken() == null) {
            decor.post(this::showMainMenu);
            return;
        }
        if (!popupWindow.isShowing()) {
            draggableHelper.showPopup(popupWindow, decor);
        }
        activity.hideGameUI();
        activity.updateBGM();
        activity.applySettingsToEngine();
    }

    public void hideMainMenu() {
        if (popupWindow.isShowing()) {
            popupWindow.dismiss();
        }
    }

    public void restoreMainMenu() {
        activity.setWindowBackground(Constants.CORE_SKIN_PATH + "/" + Constants.CORE_SKIN_BG_MENU);
        showMainMenu();
    }

    /** 彻底关闭弹窗（Activity 销毁时调用，不触发 BGM 切换等副作用），避免 WindowLeaked */
    public void dismiss() {
        if (popupWindow != null && popupWindow.isShowing()) {
            popupWindow.dismiss();
        }
    }

    public boolean isShowing() {
        return popupWindow != null && popupWindow.isShowing();
    }

    private void bindButtons(View root) {
        // 按钮文字走 gframe 既有系统字符串（对齐 game.cpp wMainMenu）：
        // 1200 本地联机 / 1201 单人游戏 / 1202 观看录像 / 1204 编辑卡组 / 1273 系统设定 / 1210 退出
        setSysText(root, R.id.tv_menu_lan, 1200, "本地联机");
        setSysText(root, R.id.tv_menu_single, 1201, "单人游戏");
        setSysText(root, R.id.tv_menu_replay, 1202, "观看录像");
        setSysText(root, R.id.tv_menu_deck, 1204, "编辑卡组");
        setSysText(root, R.id.tv_menu_settings, 1273, "系统设定");
        setSysText(root, R.id.tv_menu_exit, 1210, "退出");

        root.findViewById(R.id.btn_menu_lan).setOnClickListener(v -> LanModeDialog.showLanModeDialog(activity));
        root.findViewById(R.id.btn_menu_single).setOnClickListener(v -> SingleModeDialog.showSingleModeDialog(activity));
        root.findViewById(R.id.btn_menu_replay).setOnClickListener(v -> ReplayModeDialog.showReplayModeDialog(activity));
        root.findViewById(R.id.btn_menu_deck).setOnClickListener(v -> activity.showDeckEditorView());
        root.findViewById(R.id.btn_menu_settings).setOnClickListener(v -> activity.showSettingsDialog());
        root.findViewById(R.id.btn_menu_exit).setOnClickListener(v -> {
            activity.getSoundManager().stopBGM();
            activity.finish();
        });
    }

    private void setSysText(View root, int viewId, int sysIdx, String def) {
        TextView tv = root.findViewById(viewId);
        if (tv != null) tv.setText(mStringManager.getSystemString(sysIdx, def));
    }

    private String getVersionText() {
        int v1 = (Constants.PRO_VERSION & 0xf000) >> 12;
        int v2 = (Constants.PRO_VERSION & 0x0ff0) >> 4;
        int v3 = Constants.PRO_VERSION & 0x000f;
        return String.format("YGOPro Version:%X.0%X.%X", v1, v2, v3);
    }

    /**
     * 屏幕旋转后重新布局：根据当前屏幕方向重新解算对话框宽度，避免横屏转竖屏时对话框被过度放大。
     */
    private void relayoutOnRotation() {
        // DraggablePopupHelper 已经处理了旋转重排逻辑
        // 这里只需确保设计尺寸和配置正确即可
    }
}
