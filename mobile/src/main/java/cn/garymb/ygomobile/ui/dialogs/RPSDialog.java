package cn.garymb.ygomobile.ui.dialogs;

import android.animation.TimeInterpolator;
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
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
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

    private static final long MOVE_MS = 600;
    private static final long HOLD_MS = 500;
    private static final long FADE_MS = 200;

    /**
     * 位移速度曲线（参考 drawing.cpp 短帧快动画设计）：
     * 前 50% 时间为较快匀速段（走完 70% 路程），后 50% 时间平方减速直至停止，
     * 分段点处位移连续（0.5 → 0.7）
     */
    private static final TimeInterpolator MOVE_INTERPOLATOR = input -> {
        if (input <= 0.5f) {
            return input * 1.4f;
        }
        float t = (input - 0.5f) * 2f;
        return 0.7f + 0.3f * (1f - (1f - t) * (1f - t));
    };

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
        if (cancelable) {
            // 可取消：外部点击/返回键均可关闭
            popupWindow.setOutsideTouchable(true);
            popupWindow.setFocusable(true);
        } else {
            // 不可取消（YGOProActivity 传入 setCancelable(false)）：
            // 1) outsideTouchable=false —— 外部点击不关闭；
            // 2) focusable=false —— 弹窗不接收按键，BACK 键无法将其 dismiss
            //    （焦点型弹窗的 BACK 由系统 PopupDecorView 无条件关闭，内容层拦截不到）；
            // 3) touchable=true —— 三个手势按钮的 OnTouchListener 仍正常接收触摸
            popupWindow.setOutsideTouchable(false);
            popupWindow.setFocusable(false);
            popupWindow.setTouchable(true);
        }
        popupWindow.setTouchInterceptor((v, event) -> {
            // 双保险：吞掉 ACTION_OUTSIDE，防止任何窗口外触摸事件进入
            if (event.getAction() == MotionEvent.ACTION_OUTSIDE) return true;
            return false;
        });
        popupWindow.setOnDismissListener(() -> showing = false);
        Activity activity = (Activity) context;
        View gameRight = activity.findViewById(R.id.layout_game_right);
        if (gameRight != null && gameRight.getWidth() > 0) {
            // 已布局完成：直接按 layout_game_right 实际宽度定位
            showAlignedToField(gameRight);
            showing = true;
        } else if (gameRight != null) {
            // 决斗 UI 刚切为可见（enterDuelingUI 同帧），此时宽度为 0，
            // 必须等布局完成后再显示，否则会退化为按整个窗口居中
            showing = true;
            gameRight.getViewTreeObserver().addOnGlobalLayoutListener(
                    new ViewTreeObserver.OnGlobalLayoutListener() {
                        @Override
                        public void onGlobalLayout() {
                            gameRight.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                            if (!showing) return; // 等待期间已被 dismiss 取消
                            showAlignedToField(gameRight);
                        }
                    });
        } else {
            // 极端兜底：找不到锚点时屏幕居中
            popupWindow.showAtLocation(activity.getWindow().getDecorView(), Gravity.CENTER, 0, 0);
            showing = true;
        }
    }

    /**
     * 水平：在 layout_game_right 实际宽度内居中；
     * 垂直：弹窗底边与 GameFieldView 底边齐平
     */
    private void showAlignedToField(View gameRight) {
        contentView.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int popupW = contentView.getMeasuredWidth();
        int popupH = contentView.getMeasuredHeight();

        int[] grLoc = new int[2];
        gameRight.getLocationInWindow(grLoc);
        int x = grLoc[0] + (gameRight.getWidth() - popupW) / 2;

        Activity activity = (Activity) context;
        View fieldView = activity.findViewById(R.id.layout_game_right);
        View bottomRef = (fieldView != null && fieldView.getHeight() > 0) ? fieldView : gameRight;
        int[] brLoc = new int[2];
        bottomRef.getLocationInWindow(brLoc);
        int y = brLoc[1] + bottomRef.getHeight() - popupH;
        if (x < 0) x = 0;
        if (y < 0) y = 0;

        popupWindow.showAtLocation(gameRight, Gravity.NO_GRAVITY, x, y);
    }

    public void dismiss() {
        try {
            if (popupWindow != null && popupWindow.isShowing()) popupWindow.dismiss();
        } catch (Exception ignored) {
        }
        showing = false;
    }

    /**
     * 猜拳结果动画：
     * 我方手势图底边从 GameFieldView 中央底边开始向上移动，图片顶部触碰中轴线时停止；
     * 对方手势图倒置（rotation 180°）从 GameFieldView 顶边开始向下移动，图片顶部触碰中轴线时停止；
     * 停留 HOLD_MS 后两图淡出移除。后续继续猜拳（平局）或先后攻对话框由服务器消息自动驱动。
     */
    public void playResultAnimation(int myHand, int oppHand) {
        if (!(context instanceof Activity)) return;
        Activity activity = (Activity) context;
        ViewGroup container = activity.findViewById(R.id.layout_duel_container);
        View fieldView = activity.findViewById(R.id.game_field_view);
        if (container == null || fieldView == null
                || fieldView.getWidth() <= 0 || fieldView.getHeight() <= 0) return;

        final int fieldW = fieldView.getWidth();
        final int fieldH = fieldView.getHeight();
        final int imgW = dp2px(60);
        final int imgH = dp2px(78);
        final int centerX = (fieldW - imgW) / 2;

        // 会合线 = layout_game_right 高度的一半再减 10px。
        // 动画图添加在 layout_duel_container 中，需将 gameRight 的窗口坐标换算到容器坐标系
        View gameRight = activity.findViewById(R.id.layout_game_right);
        float axis;
        if (gameRight != null && gameRight.getHeight() > 0) {
            int[] grLoc = new int[2];
            gameRight.getLocationInWindow(grLoc);
            int[] ctLoc = new int[2];
            container.getLocationInWindow(ctLoc);
            axis = grLoc[1] + gameRight.getHeight() / 2f - ctLoc[1] - 10f;
        } else {
            // 兜底：gameRight 不可用时退用场地自身中轴线减 10px
            axis = fieldH / 2f - 10f;
        }

        ImageView myIv = createHandImage(myHand);
        FrameLayout.LayoutParams myLp = new FrameLayout.LayoutParams(imgW, imgH);
        myLp.leftMargin = centerX;
        myLp.topMargin = fieldH - imgH;
        container.addView(myIv, myLp);

        ImageView oppIv = createHandImage(oppHand);
        oppIv.setRotation(180f);
        FrameLayout.LayoutParams oppLp = new FrameLayout.LayoutParams(imgW, imgH);
        oppLp.leftMargin = centerX;
        oppLp.topMargin = 0;
        container.addView(oppIv, oppLp);

        // 我方：自底边上升，顶边停在会合线（gameRightH/2 − 10px）
        myIv.animate().translationY(axis - (fieldH - imgH)).setDuration(MOVE_MS)
                .setInterpolator(MOVE_INTERPOLATOR).start();
        // 对方：倒置图自顶边下降，底边（倒置后的视觉"顶部"）停在会合线
        oppIv.animate().translationY(axis - imgH).setDuration(MOVE_MS)
                .setInterpolator(MOVE_INTERPOLATOR)
                .withEndAction(() -> container.postDelayed(() -> {
                    myIv.animate().alpha(0f).setDuration(FADE_MS)
                            .withEndAction(() -> container.removeView(myIv)).start();
                    oppIv.animate().alpha(0f).setDuration(FADE_MS)
                            .withEndAction(() -> container.removeView(oppIv)).start();
                }, HOLD_MS))
                .start();
    }

    private ImageView createHandImage(int hand) {
        ImageView iv = new ImageView(context);
        iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
        loadHandImage(iv, hand);
        return iv;
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