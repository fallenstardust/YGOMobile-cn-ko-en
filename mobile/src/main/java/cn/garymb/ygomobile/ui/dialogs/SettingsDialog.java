package cn.garymb.ygomobile.ui.dialogs;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

import cn.garymb.ygomobile.lite.R;

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
        SharedPreferences prefs = context.getSharedPreferences(
                context.getPackageName() + ".settings", Context.MODE_PRIVATE);
        String[] keys = {
                "chkMAutoPos", "chkSTAutoPos", "chkRandomPos",
                "chkAutoChain", "chkWaitChain", "chkDefaultShowChain",
                "chkAutoSaveReplay", "chkEnableSound", "chkEnableMusic"
        };
        String[] labels = {
                "主卡位置自动", "魔陷位置自动", "随机出卡",
                "自动连锁", "等待连锁确认", "显示连锁标记",
                "自动保存录像", "启用音效", "启用BGM"
        };

        float density = context.getResources().getDisplayMetrics().density;

        LinearLayout rootLayout = new LinearLayout(context);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setBackgroundResource(R.drawable.sdialogl);
        int pad = (int) (16 * density);
        rootLayout.setPadding(pad, pad, pad, pad);

        TextView tvTitle = new TextView(context);
        tvTitle.setText("系统设定");
        tvTitle.setTextSize(18);
        tvTitle.setTextColor(0xFFFFFFFF);
        tvTitle.setPadding(0, 0, 0, (int) (8 * density));
        rootLayout.addView(tvTitle);

        ScrollView scrollContainer = new ScrollView(context);
        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        rootLayout.addView(scrollContainer, scrollLp);

        LinearLayout checkboxContainer = new LinearLayout(context);
        checkboxContainer.setOrientation(LinearLayout.VERTICAL);
        scrollContainer.addView(checkboxContainer);

        CheckBox[] checkBoxes = new CheckBox[keys.length];
        for (int i = 0; i < keys.length; i++) {
            CheckBox cb = new CheckBox(context);
            cb.setText(labels[i]);
            cb.setTextColor(0xFFFFFFFF);
            cb.setChecked(prefs.getBoolean(keys[i], false));
            checkboxContainer.addView(cb);
            checkBoxes[i] = cb;
        }

        LinearLayout buttonLayout = new LinearLayout(context);
        buttonLayout.setOrientation(LinearLayout.HORIZONTAL);
        buttonLayout.setGravity(Gravity.CENTER);

        Button btnSave = new Button(context);
        btnSave.setText("保存");
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        btnLp.setMargins(0, 0, (int) (4 * density), 0);
        buttonLayout.addView(btnSave, btnLp);

        Button btnCancel = new Button(context);
        btnCancel.setText("取消");
        LinearLayout.LayoutParams btnCancelLp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        btnCancelLp.setMargins((int) (4 * density), 0, 0, 0);
        buttonLayout.addView(btnCancel, btnCancelLp);

        rootLayout.addView(buttonLayout);

        int popupWidth = (int) (380 * density);
        int popupHeight = (int) (350 * density);
        popupWindow = new PopupWindow(rootLayout, popupWidth, popupHeight, true);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setOutsideTouchable(true);
        popupWindow.setAnimationStyle(R.style.PopupCenterAnimation);

        btnSave.setOnClickListener(v -> {
            SharedPreferences.Editor editor = prefs.edit();
            for (int i = 0; i < keys.length; i++) {
                editor.putBoolean(keys[i], checkBoxes[i].isChecked());
            }
            editor.apply();
            if (listener != null) {
                listener.onSettingsSaved();
            }
            popupWindow.dismiss();
        });

        btnCancel.setOnClickListener(v -> popupWindow.dismiss());

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
