package cn.garymb.ygomobile.ui.dialogs;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.PopupWindow;

import java.io.File;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.utils.BitmapUtil;

/*开局猜拳的弹窗*/
public class RPSDialog {

    public interface OnResultListener {
        void onResult(int hand);
    }

    public static final int HAND_SCISSORS = 1;
    public static final int HAND_ROCK = 2;
    public static final int HAND_PAPER = 3;

    private static final String ASSETS_TEXTURES = "data/textures/";

    private final Context context;
    private PopupWindow popupWindow;
    private View contentView;
    private OnResultListener resultListener;
    private boolean cancelable = true;
    private boolean showing;

    public RPSDialog(Context context) {
        this.context = context;
    }

    public RPSDialog setTitle(String title) {
        return this;
    }

    public RPSDialog setCancelable(boolean cancelable) {
        this.cancelable = cancelable;
        return this;
    }

    public RPSDialog setOnResultListener(OnResultListener listener) {
        this.resultListener = listener;
        return this;
    }

    public boolean isShowing() {
        return showing && popupWindow != null && popupWindow.isShowing();
    }

    public void show() {
        if (isShowing()) return;
        contentView = LayoutInflater.from(context).inflate(R.layout.popup_window_rps, null);
        bindHandButton(R.id.btn_rps_scissors, HAND_SCISSORS);
        bindHandButton(R.id.btn_rps_rock, HAND_ROCK);
        bindHandButton(R.id.btn_rps_paper, HAND_PAPER);

        popupWindow = new PopupWindow(contentView,
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setOutsideTouchable(false);
        popupWindow.setFocusable(true);
        popupWindow.setTouchInterceptor((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_OUTSIDE) return true;
            return false;
        });
        popupWindow.setOnDismissListener(() -> showing = false);
        Activity activity = (Activity) context;
        View anchor = activity.findViewById(R.id.et_chat_input);
        if (anchor == null) {
            anchor = activity.findViewById(R.id.layout_game_right);
        }
        if (anchor == null) {
            anchor = activity.getWindow().getDecorView();
        }
        popupWindow.showAtLocation(anchor, Gravity.TOP | Gravity.CENTER_HORIZONTAL, 0, -dp2px(12));
        showing = true;
    }

    public void dismiss() {
        try {
            if (popupWindow != null && popupWindow.isShowing()) popupWindow.dismiss();
        } catch (Exception ignored) {
        }
        showing = false;
    }

    private void bindHandButton(int viewId, int hand) {
        ImageView imageView = contentView.findViewById(viewId);
        loadHandImage(imageView, hand);
        imageView.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    v.setAlpha(0.6f);
                    return true;
                case MotionEvent.ACTION_UP:
                    v.setAlpha(1f);
                    if (resultListener != null) resultListener.onResult(hand);
                    v.post(this::dismiss);
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    v.setAlpha(1f);
                    return true;
            }
            return false;
        });
    }

    private void loadHandImage(ImageView imageView, int hand) {
        String fileName = fileNameFor(hand);
        int size = dp2px(64);
        Bitmap bitmap = null;
        try {
            bitmap = BitmapUtil.getBitmapFromFile(new File(AppsSettings.get().getCoreSkinPath(), fileName).getAbsolutePath(), size, size);
        } catch (Exception ignored) {
        }
        if (bitmap == null) {
            bitmap = BitmapUtil.getBitmapFormAssets(context, ASSETS_TEXTURES + fileName, size, size);
        }
        if (bitmap != null) {
            imageView.setImageBitmap(bitmap);
        }
    }

    private String fileNameFor(int hand) {
        switch (hand) {
            case HAND_SCISSORS:
                return "f1.jpg";
            case HAND_ROCK:
                return "f2.jpg";
            case HAND_PAPER:
            default:
                return "f3.jpg";
        }
    }



    private int dp2px(float dp) {
        return (int) (dp * context.getResources().getDisplayMetrics().density + 0.5f);
    }
}