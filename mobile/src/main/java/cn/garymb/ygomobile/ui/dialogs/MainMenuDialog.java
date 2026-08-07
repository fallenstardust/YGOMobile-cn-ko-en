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
import cn.garymb.ygomobile.audio.SoundManager;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.utils.DraggablePopupHelper;

public class MainMenuDialog {

    private final YGOProActivity activity;
    private PopupWindow popupWindow;
    private DraggablePopupHelper draggableHelper;

    public MainMenuDialog(YGOProActivity activity) {
        this.activity = activity;

        View layoutMainMenu = LayoutInflater.from(activity).inflate(R.layout.popup_window_main_menu, null);
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
        activity.getSoundManager().playBGM(SoundManager.BGM.MENU);
        activity.applySettingsToEngine();
    }

    public void hideMainMenu() {
        if (popupWindow.isShowing()) {
            popupWindow.dismiss();
        }
        activity.getSoundManager().playBGM(SoundManager.BGM.DUEL);
    }

    public void restoreMainMenu() {
        activity.setWindowBackground(Constants.CORE_SKIN_PATH + "/" + Constants.CORE_SKIN_BG_MENU);
        showMainMenu();
    }

    public boolean isShowing() {
        return popupWindow != null && popupWindow.isShowing();
    }

    private void bindButtons(View root) {
        root.findViewById(R.id.btn_menu_lan).setOnClickListener(v -> LanModeDialog.showLanModeDialog(activity));
        root.findViewById(R.id.btn_menu_single).setOnClickListener(v -> SingleModeDialog.showSingleModeDialog(activity));
        root.findViewById(R.id.btn_menu_replay).setOnClickListener(v -> ReplayModeDialog.showReplayModeDialog(activity));
        root.findViewById(R.id.btn_menu_deck).setOnClickListener(v -> activity.showDeckEditDialog());
        root.findViewById(R.id.btn_menu_settings).setOnClickListener(v -> activity.showSettingsDialog());
        root.findViewById(R.id.btn_menu_exit).setOnClickListener(v -> {
            activity.getSoundManager().stopBGM();
            activity.finish();
        });
    }

    private String getVersionText() {
        int v1 = (activity.getProVersion() & 0xf000) >> 12;
        int v2 = (activity.getProVersion() & 0x0ff0) >> 4;
        int v3 = activity.getProVersion() & 0x000f;
        return String.format("YGOPro Version:%X.0%X.%X", v1, v2, v3);
    }
}