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
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;

import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.utils.DraggablePopupHelper;

public class YesOrNoDialog {

    public static final int TYPE_MESSAGE = 0;
    public static final int TYPE_YES_NO = 1;

    private final Context context;
    private PopupWindow popupWindow;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private String title = "";
    private String message = "";
    private int type = TYPE_MESSAGE;

    private String positiveText = "确定";
    private String negativeText = "取消";

    private View.OnClickListener positiveListener;
    private View.OnClickListener negativeListener;
    private OnDismissListener dismissListener;

    private boolean cancelable = true;
    private View contentView;
    private View customContentView;
    private int softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING;
    private DraggablePopupHelper draggableHelper;

    public interface OnDismissListener {
        void onDismiss();
    }

    public YesOrNoDialog(Context context) {
        this.context = context;
    }

    public YesOrNoDialog setTitle(String title) {
        this.title = title;
        return this;
    }

    public YesOrNoDialog setMessage(String message) {
        this.message = message;
        return this;
    }

    public YesOrNoDialog setType(int type) {
        this.type = type;
        return this;
    }

    public YesOrNoDialog setPositiveButtonText(String text) {
        this.positiveText = text;
        return this;
    }

    public YesOrNoDialog setNegativeButtonText(String text) {
        this.negativeText = text;
        return this;
    }

    public YesOrNoDialog setPositiveButton(View.OnClickListener listener) {
        this.positiveListener = listener;
        return this;
    }

    public YesOrNoDialog setNegativeButton(View.OnClickListener listener) {
        this.negativeListener = listener;
        return this;
    }

    public YesOrNoDialog setCancelable(boolean cancelable) {
        this.cancelable = cancelable;
        return this;
    }

    public YesOrNoDialog setOnDismissListener(OnDismissListener listener) {
        this.dismissListener = listener;
        return this;
    }

    public YesOrNoDialog setContentView(View view) {
        this.customContentView = view;
        return this;
    }

    public YesOrNoDialog setContentView(int layoutId) {
        this.customContentView = LayoutInflater.from(context).inflate(layoutId, null);
        return this;
    }

    public YesOrNoDialog setSoftInputMode(int mode) {
        this.softInputMode = mode;
        return this;
    }

    private void build() {
        float density = context.getResources().getDisplayMetrics().density;
        int dialogWidth = (int) (280 * density);

        LinearLayout root = (LinearLayout) LayoutInflater.from(context)
                .inflate(R.layout.dialog_yes_or_no, null);

        ScrollView scrollView = root.findViewById(R.id.yes_no_scroll);
        TextView tvMessage = root.findViewById(R.id.tv_yes_no_message);
        FrameLayout customContainer = root.findViewById(R.id.yes_no_custom_content);
        LinearLayout buttonArea = root.findViewById(R.id.yes_no_button_area);
        Button btnPositive = root.findViewById(R.id.btn_yes_no_positive);
        Button btnNegative = root.findViewById(R.id.btn_yes_no_negative);

        // ── Content area ─────────────────────────────────────────
        if (customContentView != null) {
            scrollView.setVisibility(View.GONE);
            customContainer.setVisibility(View.VISIBLE);
            customContainer.addView(customContentView, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT));
        } else {
            tvMessage.setText(message);
        }

        // ── Button area ──────────────────────────────────────────
        boolean needsButtons = customContentView == null || positiveListener != null
                || negativeListener != null || type == TYPE_YES_NO;
        if (!needsButtons) {
            buttonArea.setVisibility(View.GONE);
        } else {
            btnPositive.setText(positiveText);
            btnNegative.setText(negativeText);
            if (type == TYPE_YES_NO) {
                btnNegative.setVisibility(View.VISIBLE);
            } else {
                btnNegative.setVisibility(View.GONE);
                LinearLayout.LayoutParams positiveLp =
                        (LinearLayout.LayoutParams) btnPositive.getLayoutParams();
                positiveLp.setMarginEnd(0);
            }
            btnPositive.setOnClickListener(v -> {
                if (positiveListener != null) positiveListener.onClick(v);
                dismiss();
            });
            btnNegative.setOnClickListener(v -> {
                if (negativeListener != null) negativeListener.onClick(v);
                dismiss();
            });
        }

        contentView = root;

        // ── PopupWindow setup ────────────────────────────────────
        popupWindow = new PopupWindow(contentView, dialogWidth,
                LinearLayout.LayoutParams.WRAP_CONTENT, false);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setOutsideTouchable(cancelable);
        popupWindow.setFocusable(true);
        popupWindow.setSoftInputMode(softInputMode);
        popupWindow.setTouchInterceptor((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_OUTSIDE) return true;
            return false;
        });
        popupWindow.setAnimationStyle(R.style.PopupCenterAnimation);
        popupWindow.setOnDismissListener(() -> {
            if (dismissListener != null) dismissListener.onDismiss();
        });

        draggableHelper = new DraggablePopupHelper(context, "game_dialog_" + title);
        draggableHelper.setupDraggablePopup(popupWindow, root, dialogWidth,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    public void show() {
        show(null);
    }

    public void show(View anchorView) {
        build();
        if (popupWindow == null) return;

        Runnable showAction = () -> {
            if (popupWindow == null || popupWindow.isShowing()) return;
            View anchor = anchorView;
            if (anchor == null && context instanceof android.app.Activity) {
                android.app.Activity act = (android.app.Activity) context;
                if (!act.isFinishing() && !act.isDestroyed()) {
                    anchor = act.getWindow().getDecorView();
                }
            }
            if (anchor == null || anchor.getWindowToken() == null) return;
            try {
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

    public View getContentView() {
        return customContentView != null ? customContentView : contentView;
    }
}