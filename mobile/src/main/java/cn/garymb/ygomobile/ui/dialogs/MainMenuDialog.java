package cn.garymb.ygomobile.ui.dialogs;

import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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

    public MainMenuDialog(YGOProActivity activity) {
        this.activity = activity;

        View layoutMainMenu = LayoutInflater.from(DialogScale.wrap(activity)).inflate(R.layout.popup_window_main_menu, null);
        TextView tvVersion = layoutMainMenu.findViewById(R.id.tv_version);
        tvVersion.setText(getVersionText());

        bindButtons(layoutMainMenu);

        popupWindow = new PopupWindow(layoutMainMenu,
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, true);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setOutsideTouchable(false);
        popupWindow.setFocusable(false);
        popupWindow.setAnimationStyle(R.style.PopupCenterAnimation);

        draggableHelper = new DraggablePopupHelper(activity, "main_menu");
        draggableHelper.setupDraggablePopup(popupWindow, layoutMainMenu,
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
    }

    public void showMainMenu() {
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
}