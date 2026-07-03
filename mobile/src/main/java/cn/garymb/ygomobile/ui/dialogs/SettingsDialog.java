package cn.garymb.ygomobile.ui.dialogs;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.PopupWindow;
import android.widget.SeekBar;
import android.widget.Spinner;

import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.utils.YGOUtil;
import ocgcore.DataManager;
import ocgcore.LimitManager;
import ocgcore.StringManager;

public class SettingsDialog {

    private Context context;
    private PopupWindow popupWindow;

    public interface OnSettingsSaveListener {
        void onSettingsSaved();
    }

    private OnSettingsSaveListener listener;

    public SettingsDialog(Context context, OnSettingsSaveListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void show(View anchorView) {
        AppsSettings appsSettings = AppsSettings.get();
        float density = context.getResources().getDisplayMetrics().density;
        View rootLayout = LayoutInflater.from(context).inflate(R.layout.dialog_settings, null);

        CheckBox chkMAutoPos = rootLayout.findViewById(R.id.chkMAutoPos);
        CheckBox chkSTAutoPos = rootLayout.findViewById(R.id.chkSTAutoPos);
        CheckBox chkRandomPos = rootLayout.findViewById(R.id.chkRandomPos);
        CheckBox chkAutoChain = rootLayout.findViewById(R.id.chkAutoChain);
        CheckBox chkWaitChain = rootLayout.findViewById(R.id.chkWaitChain);
        CheckBox chkDefaultShowChain = rootLayout.findViewById(R.id.chkDefaultShowChain);
        CheckBox chkHideNickName = rootLayout.findViewById(R.id.chkHideNickName);
        CheckBox chkDrawFieldSpell = rootLayout.findViewById(R.id.chkDrawFieldSpell);
        CheckBox chkQuickAnimation = rootLayout.findViewById(R.id.chkQuickAnimation);
        CheckBox chkGenesysMode = rootLayout.findViewById(R.id.chkGenesysMode);
        CheckBox chkBanList = rootLayout.findViewById(R.id.chkBanList);
        Spinner spinnerBanList = rootLayout.findViewById(R.id.spinner_banlist);
        CheckBox chkMuteSpectators = rootLayout.findViewById(R.id.chkMuteSpectators);
        CheckBox chkDisableChatting = rootLayout.findViewById(R.id.chkDisableChatting);
        CheckBox chkAutoSaveReplay = rootLayout.findViewById(R.id.chkAutoSaveReplay);
        CheckBox chkSwitchBGM = rootLayout.findViewById(R.id.chkSwitchBGM);
        CheckBox chkEnableSound = rootLayout.findViewById(R.id.chkEnableSound);
        SeekBar seekbarSound = rootLayout.findViewById(R.id.seekbar_sound);
        CheckBox chkEnableMusic = rootLayout.findViewById(R.id.chkEnableMusic);
        SeekBar seekbarMusic = rootLayout.findViewById(R.id.seekbar_music);
        Button btnCancel = rootLayout.findViewById(R.id.btn_cancel);

        chkMAutoPos.setChecked(appsSettings.getIntSettings("chkMAutoPos", 0) == 1);
        chkSTAutoPos.setChecked(appsSettings.getIntSettings("chkSTAutoPos", 0) == 1);
        chkRandomPos.setChecked(appsSettings.getIntSettings("chkRandomPos", 0) == 1);
        chkAutoChain.setChecked(appsSettings.getIntSettings("chkAutoChain", 0) == 1);
        chkWaitChain.setChecked(appsSettings.getIntSettings("chkWaitChain", 0) == 1);
        chkDefaultShowChain.setChecked(appsSettings.getIntSettings("chkDefaultShowChain", 0) == 1);
        chkHideNickName.setChecked(appsSettings.getIntSettings("chkHideNickName", 0) == 1);
        chkDrawFieldSpell.setChecked(appsSettings.getIntSettings("chkDrawFieldSpell", 0) == 1);
        chkQuickAnimation.setChecked(appsSettings.getIntSettings("chkQuickAnimation", 0) == 1);
        chkGenesysMode.setChecked(appsSettings.getGenesysMode() == 1);
        chkMuteSpectators.setChecked(appsSettings.getIntSettings("chkMuteSpectators", 0) == 1);
        chkDisableChatting.setChecked(appsSettings.getIntSettings("chkDisableChatting", 0) == 1);
        chkAutoSaveReplay.setChecked(appsSettings.getIntSettings("chkAutoSaveReplay", 0) == 1);
        chkSwitchBGM.setChecked(appsSettings.getIntSettings("chkSwitchBGM", 0) == 1);
        chkEnableSound.setChecked(appsSettings.getIntSettings("chkEnableSound", 1) == 1);
        seekbarSound.setProgress(appsSettings.getIntSettings("soundVolume", 50));
        chkEnableMusic.setChecked(appsSettings.getIntSettings("chkEnableMusic", 1) == 1);
        seekbarMusic.setProgress(appsSettings.getIntSettings("musicVolume", 50));
        chkBanList.setChecked(appsSettings.getIntSettings("chkBanList", 0) == 1);

        LimitManager limitManager = DataManager.get().getLimitManager();
        List<String> currentBanListNames = new ArrayList<>();
        boolean isGenesysMode = appsSettings.getGenesysMode() == 1;
        
        if (limitManager != null) {
            if (isGenesysMode) {
                currentBanListNames.addAll(limitManager.getGenesysLimitNames());
            } else {
                currentBanListNames.addAll(limitManager.getLimitNames());
            }
        }
        
        ArrayAdapter<String> banListAdapter = new ArrayAdapter<>(context,
                android.R.layout.simple_spinner_item, currentBanListNames);
        banListAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinnerBanList.setAdapter(banListAdapter);

        boolean isBanListEnabled = appsSettings.getIntSettings("chkBanList", 0) == 1;
        String lastLimit;
        if (!isBanListEnabled) {
            lastLimit = "N/A";
            int naIndex = currentBanListNames.indexOf("N/A");
            if (naIndex >= 0) {
                spinnerBanList.setSelection(naIndex);
            } else {
                spinnerBanList.setSelection(0);
            }
            if (isGenesysMode) {
                appsSettings.setLastGenesysLimit("N/A");
            } else {
                appsSettings.setLastLimit("N/A");
            }
        } else {
            lastLimit = isGenesysMode ? appsSettings.getLastGenesysLimit() : appsSettings.getLastLimit();
            int lastLimitIndex = currentBanListNames.indexOf(lastLimit);
            if (lastLimitIndex >= 0) {
                spinnerBanList.setSelection(lastLimitIndex);
            } else {
                spinnerBanList.setSelection(0);
            }
        }

        final List<String>[] banListData = new List[]{currentBanListNames};
        final boolean[] isCurrentlyGenesys = new boolean[]{isGenesysMode};

        chkGenesysMode.setOnCheckedChangeListener((buttonView, isChecked) -> {
            isCurrentlyGenesys[0] = isChecked;
            
            List<String> newBanListNames = new ArrayList<>();
            if (limitManager != null) {
                if (isChecked) {
                    newBanListNames.addAll(limitManager.getGenesysLimitNames());
                } else {
                    newBanListNames.addAll(limitManager.getLimitNames());
                }
            }
            
            banListData[0] = newBanListNames;
            ArrayAdapter<String> newAdapter = new ArrayAdapter<>(context,
                    android.R.layout.simple_spinner_item, newBanListNames);
            newAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinnerBanList.setAdapter(newAdapter);
            
            String savedLimit = isChecked ? appsSettings.getLastGenesysLimit() : appsSettings.getLastLimit();
            int newIndex = newBanListNames.indexOf(savedLimit);
            if (newIndex >= 0) {
                spinnerBanList.setSelection(newIndex);
            } else {
                spinnerBanList.setSelection(0);
            }
            
            spinnerBanList.setEnabled(!isChecked);
            chkBanList.setEnabled(!isChecked);
        });
        
        spinnerBanList.setEnabled(!isGenesysMode);
        chkBanList.setEnabled(!isGenesysMode);

        chkBanList.setOnCheckedChangeListener((buttonView, isChecked) -> {
            spinnerBanList.setEnabled(isChecked);
        });
        spinnerBanList.setEnabled(chkBanList.isChecked());

        spinnerBanList.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedLimit = (String) parent.getItemAtPosition(position);
                if (selectedLimit != null && !selectedLimit.isEmpty()) {
                    if (isCurrentlyGenesys[0]) {
                        appsSettings.setLastGenesysLimit(selectedLimit);
                    } else {
                        appsSettings.setLastLimit(selectedLimit);
                    }
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        int popupWidth = (int) (Constants.DIALOG_POPUP_WIDTH_DP * density);
        int popupHeight = (int) (Constants.DIALOG_POPUP_HEIGHT_DP * density);
        popupWindow = new PopupWindow(rootLayout, popupWidth, popupHeight, true);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setOutsideTouchable(true);
        popupWindow.setAnimationStyle(R.style.PopupCenterAnimation);

        btnCancel.setOnClickListener(v -> {
            appsSettings.saveIntSettings("chkMAutoPos", chkMAutoPos.isChecked() ? 1 : 0);
            appsSettings.saveIntSettings("chkSTAutoPos", chkSTAutoPos.isChecked() ? 1 : 0);
            appsSettings.saveIntSettings("chkRandomPos", chkRandomPos.isChecked() ? 1 : 0);
            appsSettings.saveIntSettings("chkAutoChain", chkAutoChain.isChecked() ? 1 : 0);
            appsSettings.saveIntSettings("chkWaitChain", chkWaitChain.isChecked() ? 1 : 0);
            appsSettings.saveIntSettings("chkDefaultShowChain", chkDefaultShowChain.isChecked() ? 1 : 0);
            appsSettings.saveIntSettings("chkHideNickName", chkHideNickName.isChecked() ? 1 : 0);
            appsSettings.saveIntSettings("chkDrawFieldSpell", chkDrawFieldSpell.isChecked() ? 1 : 0);
            appsSettings.saveIntSettings("chkQuickAnimation", chkQuickAnimation.isChecked() ? 1 : 0);
            appsSettings.setGenesysMode(chkGenesysMode.isChecked() ? 1 : 0);
            appsSettings.saveIntSettings("chkMuteSpectators", chkMuteSpectators.isChecked() ? 1 : 0);
            appsSettings.saveIntSettings("chkDisableChatting", chkDisableChatting.isChecked() ? 1 : 0);
            appsSettings.saveIntSettings("chkAutoSaveReplay", chkAutoSaveReplay.isChecked() ? 1 : 0);
            appsSettings.saveIntSettings("chkSwitchBGM", chkSwitchBGM.isChecked() ? 1 : 0);
            appsSettings.saveIntSettings("chkEnableSound", chkEnableSound.isChecked() ? 1 : 0);
            appsSettings.saveIntSettings("soundVolume", seekbarSound.getProgress());
            appsSettings.saveIntSettings("chkEnableMusic", chkEnableMusic.isChecked() ? 1 : 0);
            appsSettings.saveIntSettings("musicVolume", seekbarMusic.getProgress());

            if (listener != null) {
                listener.onSettingsSaved();
            }
            popupWindow.dismiss();
        });

        anchorView.setVisibility(View.GONE);
        popupWindow.showAtLocation(anchorView, Gravity.CENTER, 0, 0);
    }

    public void dismiss() {
        if (popupWindow != null && popupWindow.isShowing()) {
            popupWindow.dismiss();
        }
    }

    public void setOnDismissListener(PopupWindow.OnDismissListener listener) {
        if (popupWindow != null) {
            popupWindow.setOnDismissListener(listener);
        }
    }
}
