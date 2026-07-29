package cn.garymb.ygomobile.ui.dialogs;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
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
import cn.garymb.ygomobile.utils.DraggablePopupHelper;
import ocgcore.DataManager;
import ocgcore.LimitManager;
import ocgcore.StringManager;

public class SettingsDialog {

    private Context context;
    private PopupWindow popupWindow;
    private DraggablePopupHelper draggableHelper;

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
        View rootLayout = LayoutInflater.from(context).inflate(R.layout.popup_window_settings, null);

        StringManager stringManager = DataManager.get().getStringManager();

        CheckBox chkMAutoPos = rootLayout.findViewById(R.id.chkMAutoPos);
        chkMAutoPos.setText(stringManager.getSystemString(1274, ""));
        CheckBox chkSTAutoPos = rootLayout.findViewById(R.id.chkSTAutoPos);
        chkSTAutoPos.setText(stringManager.getSystemString(1278, ""));
        CheckBox chkRandomPos = rootLayout.findViewById(R.id.chkRandomPos);
        chkRandomPos.setText(stringManager.getSystemString(1275, ""));
        CheckBox chkAutoChain = rootLayout.findViewById(R.id.chkAutoChain);
        chkAutoChain.setText(stringManager.getSystemString(1276, ""));
        CheckBox chkWaitChain = rootLayout.findViewById(R.id.chkWaitChain);
        chkWaitChain.setText(stringManager.getSystemString(1277, ""));
        CheckBox chkDefaultShowChain = rootLayout.findViewById(R.id.chkDefaultShowChain);
        chkDefaultShowChain.setText(stringManager.getSystemString(1354, ""));
        CheckBox chkHideNickName = rootLayout.findViewById(R.id.chkHideNickName);
        chkHideNickName.setText(stringManager.getSystemString(1289, ""));
        CheckBox chkDrawFieldSpell = rootLayout.findViewById(R.id.chkDrawFieldSpell);
        chkDrawFieldSpell.setText(stringManager.getSystemString(1283, ""));
        CheckBox chkQuickAnimation = rootLayout.findViewById(R.id.chkQuickAnimation);
        chkQuickAnimation.setText(stringManager.getSystemString(1299, ""));
        CheckBox chkGenesysMode = rootLayout.findViewById(R.id.chkGenesysMode);
        chkGenesysMode.setText(stringManager.getSystemString(1698, ""));
        CheckBox chkBanList = rootLayout.findViewById(R.id.chkBanList);
        chkBanList.setText(stringManager.getSystemString(1288, ""));
        Spinner spinnerBanList = rootLayout.findViewById(R.id.spinner_banlist);
        CheckBox chkMuteSpectators = rootLayout.findViewById(R.id.chkMuteSpectators);
        CheckBox chkDisableChatting = rootLayout.findViewById(R.id.chkDisableChatting);
        chkDisableChatting.setText(stringManager.getSystemString(1290, ""));
        CheckBox chkAutoSaveReplay = rootLayout.findViewById(R.id.chkAutoSaveReplay);
        chkAutoSaveReplay.setText(stringManager.getSystemString(1366, ""));
        CheckBox chkSwitchBGM = rootLayout.findViewById(R.id.chkSwitchBGM);
        chkSwitchBGM.setText(stringManager.getSystemString(1281, ""));
        CheckBox chkEnableSound = rootLayout.findViewById(R.id.chkEnableSound);
        chkEnableSound.setText(stringManager.getSystemString(1279, ""));
        SeekBar seekbarSound = rootLayout.findViewById(R.id.seekbar_sound);
        CheckBox chkEnableMusic = rootLayout.findViewById(R.id.chkEnableMusic);
        chkEnableMusic.setText(stringManager.getSystemString(1280, ""));
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
        // 禁卡表开关按当前模式读取use_lflist/use_genesys_lflist，默认值为1
        chkBanList.setChecked(appsSettings.getIntSettings(
                appsSettings.getGenesysMode() == 1 ? "use_genesys_lflist" : "use_lflist", 1) == 1);

        // 勾选状态变化时立即保存
        chkMAutoPos.setOnCheckedChangeListener((buttonView, isChecked) ->
                appsSettings.saveIntSettings("chkMAutoPos", isChecked ? 1 : 0));
        chkSTAutoPos.setOnCheckedChangeListener((buttonView, isChecked) ->
                appsSettings.saveIntSettings("chkSTAutoPos", isChecked ? 1 : 0));
        chkRandomPos.setOnCheckedChangeListener((buttonView, isChecked) ->
                appsSettings.saveIntSettings("chkRandomPos", isChecked ? 1 : 0));
        chkAutoChain.setOnCheckedChangeListener((buttonView, isChecked) ->
                appsSettings.saveIntSettings("chkAutoChain", isChecked ? 1 : 0));
        chkWaitChain.setOnCheckedChangeListener((buttonView, isChecked) ->
                appsSettings.saveIntSettings("chkWaitChain", isChecked ? 1 : 0));
        chkDefaultShowChain.setOnCheckedChangeListener((buttonView, isChecked) ->
                appsSettings.saveIntSettings("chkDefaultShowChain", isChecked ? 1 : 0));
        chkHideNickName.setOnCheckedChangeListener((buttonView, isChecked) ->
                appsSettings.saveIntSettings("chkHideNickName", isChecked ? 1 : 0));
        chkDrawFieldSpell.setOnCheckedChangeListener((buttonView, isChecked) ->
                appsSettings.saveIntSettings("chkDrawFieldSpell", isChecked ? 1 : 0));
        chkQuickAnimation.setOnCheckedChangeListener((buttonView, isChecked) ->
                appsSettings.saveIntSettings("chkQuickAnimation", isChecked ? 1 : 0));
        chkMuteSpectators.setOnCheckedChangeListener((buttonView, isChecked) ->
                appsSettings.saveIntSettings("chkMuteSpectators", isChecked ? 1 : 0));
        chkDisableChatting.setOnCheckedChangeListener((buttonView, isChecked) ->
                appsSettings.saveIntSettings("chkDisableChatting", isChecked ? 1 : 0));
        chkAutoSaveReplay.setOnCheckedChangeListener((buttonView, isChecked) ->
                appsSettings.saveIntSettings("chkAutoSaveReplay", isChecked ? 1 : 0));
        chkSwitchBGM.setOnCheckedChangeListener((buttonView, isChecked) ->
                appsSettings.saveIntSettings("chkSwitchBGM", isChecked ? 1 : 0));
        chkEnableSound.setOnCheckedChangeListener((buttonView, isChecked) ->
                appsSettings.saveIntSettings("chkEnableSound", isChecked ? 1 : 0));
        chkEnableMusic.setOnCheckedChangeListener((buttonView, isChecked) ->
                appsSettings.saveIntSettings("chkEnableMusic", isChecked ? 1 : 0));
        seekbarSound.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                appsSettings.saveIntSettings("soundVolume", seekBar.getProgress());
            }
        });
        seekbarMusic.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                appsSettings.saveIntSettings("musicVolume", seekBar.getProgress());
            }
        });

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

        //初始化时只读取保存值，不写入
        boolean isBanListEnabled = chkBanList.isChecked();
        if (!isBanListEnabled) {
            int naIndex = currentBanListNames.indexOf("N/A");
            if (naIndex >= 0) {
                spinnerBanList.setSelection(naIndex);
            } else {
                spinnerBanList.setSelection(0);
            }
        } else {
            String lastLimit = isGenesysMode ? appsSettings.getLastGenesysLimit() : appsSettings.getLastLimit();
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
            appsSettings.setGenesysMode(isChecked ? 1 : 0);
            
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

            //切换模式后按对应键重新读取禁卡表开关状态（对齐game.cpp两个独立开关）
            boolean banEnabled = appsSettings.getIntSettings(
                    isChecked ? "use_genesys_lflist" : "use_lflist", 1) == 1;
            if (chkBanList.isChecked() != banEnabled) {
                //状态不同时通过setChecked触发chkBanList监听器完成spinner联动
                chkBanList.setChecked(banEnabled);
            } else if (!banEnabled) {
                int naIndex = newBanListNames.indexOf("N/A");
                if (naIndex >= 0) {
                    spinnerBanList.setSelection(naIndex);
                } else {
                    spinnerBanList.setSelection(0);
                }
                spinnerBanList.setEnabled(false);
            } else {
                String savedLimit = isChecked ? appsSettings.getLastGenesysLimit() : appsSettings.getLastLimit();
                int newIndex = newBanListNames.indexOf(savedLimit);
                if (newIndex >= 0) {
                    spinnerBanList.setSelection(newIndex);
                } else {
                    spinnerBanList.setSelection(0);
                }
                spinnerBanList.setEnabled(true);
            }
        });
        
        spinnerBanList.setEnabled(chkBanList.isChecked());
        chkBanList.setEnabled(true);

        chkBanList.setOnCheckedChangeListener((buttonView, isChecked) -> {
            //对齐game.cpp：按当前模式保存到use_lflist/use_genesys_lflist
            appsSettings.saveIntSettings(
                    isCurrentlyGenesys[0] ? "use_genesys_lflist" : "use_lflist", isChecked ? 1 : 0);
            spinnerBanList.setEnabled(isChecked);
            if (!isChecked) {
                //仅显示N/A，不覆盖已保存的禁卡表名（对齐game.cpp关闭时不写last_limit_list_name）
                int naIndex = banListData[0].indexOf("N/A");
                if (naIndex >= 0) {
                    spinnerBanList.setSelection(naIndex);
                }
            } else {
                //重新启用时优先恢复保存的禁卡表，不存在则选第一个非N/A项
                String savedLimit = isCurrentlyGenesys[0] ? appsSettings.getLastGenesysLimit() : appsSettings.getLastLimit();
                int targetIndex = -1;
                if (savedLimit != null && !"N/A".equals(savedLimit)) {
                    targetIndex = banListData[0].indexOf(savedLimit);
                }
                if (targetIndex < 0) {
                    for (int i = 0; i < banListData[0].size(); i++) {
                        if (!"N/A".equals(banListData[0].get(i))) {
                            targetIndex = i;
                            break;
                        }
                    }
                }
                if (targetIndex >= 0) {
                    spinnerBanList.setSelection(targetIndex);
                    String selectedLimit = banListData[0].get(targetIndex);
                    if (isCurrentlyGenesys[0]) {
                        appsSettings.setLastGenesysLimit(selectedLimit);
                    } else {
                        appsSettings.setLastLimit(selectedLimit);
                    }
                } else {
                    int naIndex = banListData[0].indexOf("N/A");
                    if (naIndex >= 0) {
                        spinnerBanList.setSelection(naIndex);
                    }
                }
            }
        });

        spinnerBanList.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                //开关关闭时不保存，避免初始化/切换adapter自动触发时把N/A写入保存值
                if (!chkBanList.isChecked()) {
                    return;
                }
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
        popupWindow.setOutsideTouchable(false);
        popupWindow.setFocusable(false);
        popupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        popupWindow.setTouchInterceptor((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {
                return true;
            }
            return false;
        });
        popupWindow.setAnimationStyle(R.style.PopupCenterAnimation);

        draggableHelper = new DraggablePopupHelper(context, "settings_dialog");
        draggableHelper.setupDraggablePopup(popupWindow, rootLayout);

        btnCancel.setOnClickListener(v -> {
            if (listener != null) {
                listener.onSettingsSaved();
            }
            popupWindow.dismiss();
        });

        anchorView.setVisibility(View.GONE);
        draggableHelper.showPopup(popupWindow, anchorView);
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
