package cn.garymb.ygomobile.ui.dialogs;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.TextView;

import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.lite.R;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import cn.garymb.ygomobile.ui.adapters.SimpleListAdapter;

public class ReplayModeDialog {

    private Context context;
    private PopupWindow popupWindow;

    public interface OnReplaySelectedListener {
        void onReplaySelected(String replayFilePath);
    }

    private OnReplaySelectedListener listener;

    public ReplayModeDialog(Context context, OnReplaySelectedListener listener) {
        this.context = context;
        this.listener = listener;
    }

    public void show(View anchorView, File replayDir) {
        File[] files = replayDir.exists()
                ? replayDir.listFiles((dir, name) -> name.endsWith(".yrp"))
                : null;
        List<String> nameList = new ArrayList<>();
        if (files != null && files.length > 0) {
            Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
            for (File f : files) {
                nameList.add(f.getName());
            }
        } else {
            nameList.add("（暂无录像文件）");
        }

        final File[] finalFiles = files;
        float density = context.getResources().getDisplayMetrics().density;

        LinearLayout rootLayout = new LinearLayout(context);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setBackgroundResource(cn.garymb.ygomobile.lite.R.drawable.sdialogl);
        int pad = (int) (16 * density);
        rootLayout.setPadding(pad, pad, pad, pad);

        TextView tvTitle = new TextView(context);
        tvTitle.setText("观看录像");
        tvTitle.setTextSize(18);
        tvTitle.setTextColor(0xFFFFFFFF);
        tvTitle.setPadding(0, 0, 0, (int) (8 * density));
        rootLayout.addView(tvTitle);

        ListView listView = new ListView(context);
        SimpleListAdapter adapter = new SimpleListAdapter(context);
        adapter.set(nameList);
        listView.setAdapter(adapter);
        LinearLayout.LayoutParams listLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        rootLayout.addView(listView, listLp);

        Button btnExit = new Button(context);
        btnExit.setText("退出");
        rootLayout.addView(btnExit);

        int popupWidth = (int) (Constants.DIALOG_POPUP_WIDTH_DP * density);
        int popupHeight = (int) (Constants.DIALOG_POPUP_HEIGHT_DP * density);
        popupWindow = new PopupWindow(rootLayout, popupWidth, popupHeight, true);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setOutsideTouchable(true);
        popupWindow.setAnimationStyle(R.style.PopupCenterAnimation);

        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (finalFiles != null && position < finalFiles.length) {
                String path = finalFiles[position].getAbsolutePath();
                popupWindow.dismiss();
                if (listener != null) {
                    listener.onReplaySelected(path);
                }
            }
        });

        btnExit.setOnClickListener(v -> popupWindow.dismiss());

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
