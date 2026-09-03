package cn.garymb.ygomobile.ui.dialogs;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.PopupWindow;

import cn.garymb.ygomobile.lite.R;
import ocgcore.DataManager;

/**
 * 先后攻选择弹窗（PopupWindow 实现，替代原 YesOrNoDialog 版 TPSelectDialog）。
 * 布局参考 game.cpp wFTSelect（L771-779）：标题 + 上下两个按钮
 * btnFirst（sys 100=先攻）/ btnSecond（sys 101=后攻）。
 * 应答值对齐 event_handler.cpp BUTTON_FIRST/BUTTON_SECOND：CTOS_TP_RESULT 先攻=1、后攻=0。
 * 显示位置与 RPSDialog 一致：锚定 layout_game_right（水平+垂直居中），
 * 决斗 UI 同帧刚可见（宽度为 0）时等待布局完成再显示。
 * 该弹窗必须选择后才能关闭（协议在等待应答）：
 * outsideTouchable=false + focusable=false（BACK 不关闭）+ TouchInterceptor
 * 吞掉 ACTION_OUTSIDE 双保险，禁止任何外部点击关闭。
 */
public class FirstOrSecondDialog {

    public interface OnSelectListener {
        void onSelect(boolean first);
    }

    private final Context context;
    private PopupWindow popupWindow;
    private View contentView;
    private OnSelectListener selectListener;
    private boolean showing;

    public FirstOrSecondDialog(Context context) {
        this.context = context;
    }

    public FirstOrSecondDialog setOnSelectListener(OnSelectListener listener) {
        this.selectListener = listener;
        return this;
    }

    public boolean isShowing() {
        return showing && popupWindow != null && popupWindow.isShowing();
    }

    public void show() {
        if (isShowing()) return;
        contentView = LayoutInflater.from(context).inflate(R.layout.popup_window_first_or_second, null);
        Button btnFirst = contentView.findViewById(R.id.btn_ft_first);
        Button btnSecond = contentView.findViewById(R.id.btn_ft_second);
        btnFirst.setText(DataManager.get().getStringManager().getSystemString(100, "先攻"));
        btnSecond.setText(DataManager.get().getStringManager().getSystemString(101, "后攻"));
        bindSelectButton(btnFirst, true);
        bindSelectButton(btnSecond, false);

        popupWindow = new PopupWindow(contentView,
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        // 不可取消：
        // 1) outsideTouchable=false —— 外部点击不关闭；
        // 2) focusable=false —— 弹窗不接收按键，BACK 键无法将其 dismiss；
        // 3) touchable=true —— 两个按钮的 OnTouchListener 仍正常接收触摸
        popupWindow.setOutsideTouchable(false);
        popupWindow.setFocusable(false);
        popupWindow.setTouchable(true);
        popupWindow.setTouchInterceptor((v, event) -> {
            // 双保险：吞掉 ACTION_OUTSIDE，防止任何窗口外触摸事件进入
            if (event.getAction() == MotionEvent.ACTION_OUTSIDE) return true;
            return false;
        });
        popupWindow.setOnDismissListener(() -> showing = false);

        Activity activity = (Activity) context;
        View gameRight = activity.findViewById(R.id.layout_game_right);
        if (gameRight != null && gameRight.getWidth() > 0) {
            // 已布局完成：直接按 layout_game_right 实际尺寸定位
            showAtFieldCenter(gameRight);
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
                            showAtFieldCenter(gameRight);
                        }
                    });
        } else {
            // 极端兜底：找不到锚点时屏幕居中
            popupWindow.showAtLocation(activity.getWindow().getDecorView(), Gravity.CENTER, 0, 0);
            showing = true;
        }
    }

    /**
     * 水平、垂直均在 layout_game_right 实际区域内居中（对齐 game.cpp wFTSelect 屏幕居中语义）
     */
    private void showAtFieldCenter(View gameRight) {
        contentView.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int popupW = contentView.getMeasuredWidth();
        int popupH = contentView.getMeasuredHeight();

        int[] loc = new int[2];
        gameRight.getLocationInWindow(loc);
        int x = loc[0] + (gameRight.getWidth() - popupW) / 2;
        int y = loc[1] + (gameRight.getHeight() - popupH) / 2;
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

    private void bindSelectButton(Button button, boolean first) {
        button.setOnTouchListener((v, event) -> {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    v.setAlpha(0.6f);
                    return true;
                case MotionEvent.ACTION_UP:
                    v.setAlpha(1f);
                    // 点击瞬间立即隐藏弹窗，再回调结果（不依赖回调内部是否执行成功）
                    dismiss();
                    if (selectListener != null) selectListener.onSelect(first);
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    v.setAlpha(1f);
                    return true;
            }
            return false;
        });
    }
}