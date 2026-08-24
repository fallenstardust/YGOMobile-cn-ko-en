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
     * 双方停止线相对 layout_game_right 半高中心线的偏移：
     * 我方顶边停在（半高 − 10px），对方底边停在（半高 + 10px），
     * 两图相对边之间形成固定 20px 间隙
     */
    private static final float STOP_GAP_HALF_PX = 10f;

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
    /** 猜拳结果动画专用覆盖层（PopupWindow 层位于 GameFieldView 的 GL Surface 之上） */
    private PopupWindow animWindow;

    public RPSDialog(Context context) {
        this.context = context;
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
        View game_field_view = activity.findViewById(R.id.game_field_view);
        if (game_field_view != null && game_field_view.getWidth() > 0) {
            // 已布局完成：直接按 layout_game_right 实际宽度定位
            showAlignedToField(game_field_view);
            showing = true;
        } else if (game_field_view != null) {
            // 决斗 UI 刚切为可见（enterDuelingUI 同帧），此时宽度为 0，
            // 必须等布局完成后再显示，否则会退化为按整个窗口居中
            showing = true;
            game_field_view.getViewTreeObserver().addOnGlobalLayoutListener(
                    new ViewTreeObserver.OnGlobalLayoutListener() {
                        @Override
                        public void onGlobalLayout() {
                            game_field_view.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                            if (!showing) return; // 等待期间已被 dismiss 取消
                            showAlignedToField(game_field_view);
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
        dismissAnimWindow();
        try {
            if (popupWindow != null && popupWindow.isShowing()) popupWindow.dismiss();
        } catch (Exception ignored) {
        }
        showing = false;
    }

    /**
     * 猜拳结果动画：
     * GameFieldView 是 setZOrderOnTop(true) 的 GLSurfaceView，GL 曲面合成在 Activity 窗口之上，
     * 加在布局里的普通 ImageView 会被场地纹理遮挡，因此两图放进全屏透明、不拦截触摸的
     * PopupWindow（与 RPSDialog 同层，稳定显示在 GL 曲面之上），布局直接使用窗口坐标。
     * 我方手势图从场地中央底边开始向上移动，顶边停在 layout_game_right 高度一半 − 10px；
     * 对方手势图倒置（rotation 180°）从 layout_game_right 顶部开始向下移动，底边停在
     * layout_game_right 高度一半 + 10px；两图相对边之间保持 20px 间隙。
     * 停留 HOLD_MS 后两图淡出移除并关闭覆盖层。平局（手势相同）时重新显示 RPSDialog 供玩家再出；
     * 分出胜负（手势不同）后不再显示 RPSDialog，由 ShowDialogUtil 按结果抑制。
     */
    public void playResultAnimation(int myHand, int oppHand) {
        if (!(context instanceof Activity)) return;
        Activity activity = (Activity) context;
        View gameRight = activity.findViewById(R.id.layout_game_right);
        View fieldView = activity.findViewById(R.id.game_field_view);
        if (gameRight == null || fieldView == null
                || gameRight.getWidth() <= 0 || gameRight.getHeight() <= 0
                || fieldView.getWidth() <= 0 || fieldView.getHeight() <= 0) return;

        // 中止上一次未完成的动画（平局连续出拳时可能出现重叠）
        dismissAnimWindow();

        // 手势位图为 64×64 方形解码，视图同取方形，避免 FIT_CENTER 产生上下透明留白，
        // 使图案边缘与计算边缘一致，两图间隙即为真实的 20px
        final int imgW = dp2px(60);
        final int imgH = dp2px(60);

        // 覆盖层铺满窗口，图片布局直接使用窗口坐标（getLocationInWindow）
        int[] grLoc = new int[2];
        gameRight.getLocationInWindow(grLoc);
        int[] fLoc = new int[2];
        fieldView.getLocationInWindow(fLoc);

        final int grLeft = grLoc[0];
        final int grTop = grLoc[1];
        // 中心线 = layout_game_right 高度一半（窗口坐标）；
        // 我方顶边停止线 = 中心线 − 10px，对方底边停止线 = 中心线 + 10px → 两图间 20px 间隙
        final float midY = grTop + gameRight.getHeight() / 2f;
        final float myStopTop = midY - STOP_GAP_HALF_PX;
        final float oppStopBottom = midY + STOP_GAP_HALF_PX;
        final int centerX = grLeft + (gameRight.getWidth() - imgW) / 2;
        // 我方起点：场地中央底边（图片底边与场地底边齐平）
        final int myStartTop = fLoc[1] + fieldView.getHeight() - imgH;
        // 对方起点：layout_game_right 顶部
        final int oppStartTop = grTop;

        FrameLayout overlay = new FrameLayout(context);
        ImageView myIv = createHandImage(myHand);
        FrameLayout.LayoutParams myLp = new FrameLayout.LayoutParams(imgW, imgH);
        myLp.leftMargin = centerX;
        myLp.topMargin = myStartTop;
        overlay.addView(myIv, myLp);

        ImageView oppIv = createHandImage(oppHand);
        oppIv.setRotation(180f);
        FrameLayout.LayoutParams oppLp = new FrameLayout.LayoutParams(imgW, imgH);
        oppLp.leftMargin = centerX;
        oppLp.topMargin = oppStartTop;
        overlay.addView(oppIv, oppLp);

        PopupWindow window = new PopupWindow(overlay,
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        // 不抢焦点、不拦截触摸：动画纯展示，触摸事件穿透到下层游戏 UI
        window.setFocusable(false);
        window.setOutsideTouchable(false);
        window.setTouchable(false);
        animWindow = window;
        window.showAtLocation(activity.getWindow().getDecorView(), Gravity.NO_GRAVITY, 0, 0);

        // 我方：自底边上升，顶边停在（layout_game_right 半高 − 10px）
        myIv.animate().translationY(myStopTop - myStartTop).setDuration(MOVE_MS)
                .setInterpolator(MOVE_INTERPOLATOR).start();
        // 对方：倒置图自 layout_game_right 顶部下降，底边停在（layout_game_right 半高 + 10px）
        oppIv.animate().translationY(oppStopBottom - oppStartTop - imgH).setDuration(MOVE_MS)
                .setInterpolator(MOVE_INTERPOLATOR)
                .withEndAction(() -> overlay.postDelayed(() -> {
                    myIv.animate().alpha(0f).setDuration(FADE_MS).start();
                    oppIv.animate().alpha(0f).setDuration(FADE_MS)
                            .withEndAction(this::dismissAnimWindow).start();
                }, HOLD_MS))
                .start();
    }

    private void dismissAnimWindow() {
        try {
            if (animWindow != null && animWindow.isShowing()) animWindow.dismiss();
        } catch (Exception ignored) {
        }
        animWindow = null;
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
                    // 点击瞬间立即隐藏弹窗，再回调结果（不依赖回调内部是否执行成功）
                    dismiss();
                    if (resultListener != null) resultListener.onResult(hand);
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