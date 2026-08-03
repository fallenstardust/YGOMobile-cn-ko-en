package cn.garymb.ygomobile.ui.dialogs;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
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

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(context.getDrawable(R.drawable.sdialogl));

        // ── Title bar (drag handle) ──────────────────────────────
        LinearLayout titleBar = new LinearLayout(context);
        titleBar.setOrientation(LinearLayout.HORIZONTAL);
        titleBar.setGravity(Gravity.CENTER_VERTICAL);
        int titleHeight = (int) (44 * density);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, titleHeight);
        titleBar.setLayoutParams(titleLp);
        int titlePadH = (int) (12 * density);
        titleBar.setPadding(titlePadH, 0, titlePadH, 0);

        TextView tvTitle = new TextView(context);
        tvTitle.setText(title);
        tvTitle.setTextColor(context.getColor(R.color.item_title));
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tvTitle.setSingleLine(true);
        LinearLayout.LayoutParams titleTextLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tvTitle.setLayoutParams(titleTextLp);
        titleBar.addView(tvTitle);

        TextView dragHint = new TextView(context);
        dragHint.setText("⋮⋮");
        dragHint.setTextColor(0x66FFFFFF);
        dragHint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        dragHint.setPadding((int) (4 * density), 0, 0, 0);
        titleBar.addView(dragHint);

        root.addView(titleBar);

        // ── Divider ──────────────────────────────────────────────
        View divider = new View(context);
        divider.setBackgroundColor(0x33FFFFFF);
        root.addView(divider, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));

        // ── Content area ─────────────────────────────────────────
        if (customContentView != null) {
            LinearLayout.LayoutParams contentLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
            int contentMarginV = (int) (8 * density);
            contentLp.setMargins(0, contentMarginV, 0, contentMarginV);
            root.addView(customContentView, contentLp);
        } else {
            ScrollView scrollView = new ScrollView(context);
            scrollView.setVerticalScrollBarEnabled(false);
            LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
            int msgMarginV = (int) (8 * density);
            scrollLp.setMargins(0, msgMarginV, 0, msgMarginV);
            scrollView.setLayoutParams(scrollLp);

            TextView tvMessage = new TextView(context);
            tvMessage.setText(message);
            tvMessage.setTextColor(Color.WHITE);
            tvMessage.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            int msgPadH = (int) (14 * density);
            int msgPadV = (int) (4 * density);
            tvMessage.setPadding(msgPadH, msgPadV, msgPadH, msgPadV);
            scrollView.addView(tvMessage);
            root.addView(scrollView);
        }

        // ── Button area ──────────────────────────────────────────
        boolean needsButtons = customContentView == null || positiveListener != null
                || negativeListener != null || type == TYPE_YES_NO;
        if (needsButtons) {
            LinearLayout btnArea = new LinearLayout(context);
            btnArea.setOrientation(LinearLayout.HORIZONTAL);
            btnArea.setGravity(Gravity.CENTER);
            int btnAreaH = (int) (44 * density);
            LinearLayout.LayoutParams btnAreaLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, btnAreaH);
            btnAreaLp.setMargins(0, (int) (4 * density), 0, (int) (8 * density));
            btnArea.setLayoutParams(btnAreaLp);

            int btnMinW = (int) (90 * density);
            int btnMargin = (int) (16 * density);

            if (type == TYPE_MESSAGE) {
                Button btnOk = makeButton(positiveText, btnMinW);
                LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                btnOk.setOnClickListener(v -> {
                    if (positiveListener != null) positiveListener.onClick(v);
                    dismiss();
                });
                btnArea.addView(btnOk, btnLp);
            } else {
                Button btnYes = makeButton(positiveText, btnMinW);
                LinearLayout.LayoutParams yesLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                yesLp.setMarginEnd(btnMargin);
                btnYes.setOnClickListener(v -> {
                    if (positiveListener != null) positiveListener.onClick(v);
                    dismiss();
                });
                btnArea.addView(btnYes, yesLp);

                Button btnNo = makeButton(negativeText, btnMinW);
                btnNo.setBackground(context.getDrawable(R.drawable.button3_bg));
                LinearLayout.LayoutParams noLp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                btnNo.setOnClickListener(v -> {
                    if (negativeListener != null) negativeListener.onClick(v);
                    dismiss();
                });
                btnArea.addView(btnNo, noLp);
            }

            root.addView(btnArea);
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

    private Button makeButton(String text, int minWidth) {
        Button btn = new Button(context);
        btn.setText(text);
        btn.setTextColor(Color.WHITE);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        btn.setMinimumWidth(minWidth);
        btn.setMinHeight(0);
        int padV = (int) (6 * context.getResources().getDisplayMetrics().density);
        btn.setPadding((int) (14 * context.getResources().getDisplayMetrics().density), padV,
                (int) (14 * context.getResources().getDisplayMetrics().density), padV);
        btn.setBackground(context.getDrawable(R.drawable.button3_bg));
        return btn;
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