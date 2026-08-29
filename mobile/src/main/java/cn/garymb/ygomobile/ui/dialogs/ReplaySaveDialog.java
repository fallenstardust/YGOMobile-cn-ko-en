package cn.garymb.ygomobile.ui.dialogs;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.utils.DraggablePopupHelper;
import ocgcore.DataManager;

/**
 * 录像保存对话框（决斗结束后弹出）：
 * tv_title 显示 sys 1342「录像文件：」，EditText 回显通讯发来的录像默认文件名，
 * 下方「保存」(sys 1341) /「取消」(sys 1212) 按钮。
 */
public class ReplaySaveDialog {

    public interface OnReplayActionListener {
        void onSave(String fileName);
        void onCancel();
    }

    private final Context context;
    private PopupWindow popupWindow;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private EditText etReplayName;
    private OnReplayActionListener actionListener;
    private String defaultName = "";
    private DraggablePopupHelper draggableHelper;
    // 指定弹窗居中区域（如 layout_game_right）；null = 按整个窗口居中（原行为）
    private View centerInView;

    public ReplaySaveDialog(Context context) {
        this.context = context;
    }

    /** 弹窗按指定区域的宽高居中显示（如 layout_game_right），而非整个 Activity 窗口 */
    public ReplaySaveDialog setCenterInView(View region) {
        this.centerInView = region;
        return this;
    }

    public ReplaySaveDialog setDefaultName(String name) {
        this.defaultName = name != null ? name : "";
        if (etReplayName != null) {
            etReplayName.setText(this.defaultName);
        }
        return this;
    }

    public ReplaySaveDialog setOnReplayActionListener(OnReplayActionListener listener) {
        this.actionListener = listener;
        return this;
    }

    private void build() {
        float density = context.getResources().getDisplayMetrics().density;
        int dialogWidth = (int) (280 * density);

        LinearLayout root = (LinearLayout) LayoutInflater.from(context)
                .inflate(R.layout.dialog_replay_save, null);

        TextView tvTitle = root.findViewById(R.id.tv_title);
        etReplayName = root.findViewById(R.id.et_replay_name);
        Button btnSave = root.findViewById(R.id.btn_replay_save);
        Button btnCancel = root.findViewById(R.id.btn_replay_cancel);

        tvTitle.setText(DataManager.get().getStringManager().getSystemString(1342, "录像文件："));
        btnSave.setText(DataManager.get().getStringManager().getSystemString(1341, "保存"));
        btnCancel.setText(DataManager.get().getStringManager().getSystemString(1212, "取消"));
        etReplayName.setText(defaultName);
        etReplayName.setSelection(etReplayName.getText().length());

        btnSave.setOnClickListener(v -> {
            String name = etReplayName.getText() != null
                    ? etReplayName.getText().toString().trim() : "";
            if (actionListener != null) actionListener.onSave(name);
            dismiss();
        });
        btnCancel.setOnClickListener(v -> {
            if (actionListener != null) actionListener.onCancel();
            dismiss();
        });

        popupWindow = new PopupWindow(root, dialogWidth,
                LinearLayout.LayoutParams.WRAP_CONTENT, false);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setOutsideTouchable(false);
        popupWindow.setFocusable(true);
        popupWindow.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        popupWindow.setTouchInterceptor((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_OUTSIDE) return true;
            return false;
        });
        popupWindow.setAnimationStyle(R.style.PopupCenterAnimation);

        draggableHelper = new DraggablePopupHelper(context, "game_dialog_replay_save");
        draggableHelper.setupDraggablePopup(popupWindow, root, dialogWidth,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    public void show() {
        build();
        if (popupWindow == null) return;

        Runnable showAction = () -> {
            if (popupWindow == null || popupWindow.isShowing()) return;
            View anchor = null;
            if (context instanceof android.app.Activity) {
                android.app.Activity act = (android.app.Activity) context;
                if (!act.isFinishing() && !act.isDestroyed()) {
                    anchor = act.getWindow().getDecorView();
                }
            }
            if (anchor == null || anchor.getWindowToken() == null) return;
            try {
                DraggablePopupHelper.centerPopupInRegion(popupWindow, centerInView);
                if (draggableHelper != null) {
                    draggableHelper.showPopup(popupWindow, anchor);
                } else {
                    popupWindow.showAtLocation(anchor, Gravity.CENTER, 0, 0);
                }
            } catch (Exception e) {
                // Token expired or window already showing
            }
        };

        if (Looper.myLooper() == Looper.getMainLooper()) {
            showAction.run();
        } else {
            handler.post(showAction);
        }
    }

    public void dismiss() {
        if (popupWindow != null && popupWindow.isShowing()) {
            try {
                popupWindow.dismiss();
            } catch (Exception e) {
                // Ignore
            }
        }
    }

    public boolean isShowing() {
        return popupWindow != null && popupWindow.isShowing();
    }
}