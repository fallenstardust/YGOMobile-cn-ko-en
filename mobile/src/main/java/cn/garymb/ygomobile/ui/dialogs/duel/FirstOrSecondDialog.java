package cn.garymb.ygomobile.ui.dialogs.duel;

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
import cn.garymb.ygomobile.utils.ImePopupAdjuster;
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
 *
 * <p>同 RPSDialog 一样以绝对坐标定位、不经 DraggablePopupHelper 包装，故旋转重排不会覆盖它，
 * 横竖屏切换后须由 {@link #repositionAfterRotation()} 按新的 layout_game_right 重新居中
 *（局域网与纯单人模式的先后攻选择走同一弹窗，故一并生效）。
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
    /**
     * 当前正在监听尺寸变化的锚视图与对应监听器（对齐 RPSDialog 自愈机制）：
     * 纯单人模式跳过猜拳直接选先后攻，show() 可能撞上 layout_game_right 由整屏宽
     * （大厅聊天态）收缩到决斗区宽的过渡宽度帧，按当时的瞬时宽算出的居中坐标会偏。
     * 弹窗展示期间监听锚视图尺寸变化，一发现宽/高变了就用新尺寸重算居中坐标并 update，
     * 保证横竖屏、过渡宽度下弹窗始终在 layout_game_right 正中央。dismiss 时摘除。
     */
    private View anchorWatching;
    private View.OnLayoutChangeListener anchorSizeWatcher;

    public FirstOrSecondDialog(Context context) {
        this.context = context;
    }

    public FirstOrSecondDialog setOnSelectListener(OnSelectListener listener) {
        this.selectListener = listener;
        return this;
    }

    public boolean isShowing() {
        // 同 RPSDialog：showing 表示「已显示或等待布局完成后显示」，不叠加 popupWindow.isShowing()。
        // onTPSelect 会先 setState(TP_SELECT) 再 onSelectRequired(1)，两条路径都调用 showTPSelectDialog，
        // 延迟显示期间若 isShowing() 误报 false 会创建出第二个弹窗，导致先后攻弹窗残留关不掉。
        return showing;
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
        if (gameRight != null) {
            showing = true;
            showAtFieldCenterOrAwaitLayout(gameRight);
            // 输入法抬升联动（用户规格）：抬键盘时本弹窗底边贴聊天输入框上沿，
            // 收起后回到 layout_game_right 中央，逻辑统一在 ImePopupAdjuster
            ImePopupAdjuster.attach(popupWindow);
        } else {
            // 极端兜底：找不到锚点时屏幕居中
            popupWindow.showAtLocation(activity.getWindow().getDecorView(), Gravity.CENTER, 0, 0);
            showing = true;
        }
    }

    /**
     * 按 layout_game_right 的当前尺寸显示弹窗：已布局完成则直接按实际尺寸定位；
     * 宽度为 0（决斗 UI 刚切为可见，enterDuelingUI 同帧）时注册一次性布局监听待完成后显示，
     * 否则会退化为按整个窗口居中。首次显示与旋转重建后的延迟补显示共用本方法。
     */
    private void showAtFieldCenterOrAwaitLayout(View gameRight) {
        if (gameRight.getWidth() > 0) {
            showAtFieldCenter(gameRight);
            return;
        }
        gameRight.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        gameRight.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        // 等待期间已被 dismiss 取消，或已按旋转后的新视图树补显示，均不再处理
                        if (!showing || popupWindow == null || popupWindow.isShowing()) return;
                        showAtFieldCenter(gameRight);
                    }
                });
    }

    /**
     * 水平、垂直均在 layout_game_right 实际区域内居中（对齐 game.cpp wFTSelect 屏幕居中语义）；
     * 同时挂上锚视图尺寸监听，过渡宽度收敛后自动按新尺寸重居中。
     */
    private void showAtFieldCenter(View gameRight) {
        int[] pos = computeCenterPosition(gameRight);
        popupWindow.showAtLocation(gameRight, Gravity.NO_GRAVITY, pos[0], pos[1]);
        attachAnchorSizeWatcher(gameRight);
    }

    /**
     * 解算弹窗在窗口坐标系下的左上角 (x, y)：水平、垂直都在 layout_game_right 实际区域内居中。
     * 供首次显示与屏幕旋转后重定位复用，保证旋转后仍按新方向的 layout_game_right 区域重新居中
     *（横屏为右侧决斗区、竖屏为整宽决斗区，沿用旧绝对坐标会偏出中央）。
     *
     * @return int[]{x, y}
     */
    private int[] computeCenterPosition(View gameRight) {
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
        return new int[]{x, y};
    }

    /**
     * 屏幕旋转后重新定位：本弹窗以绝对坐标 showAtLocation 定位、不经 DraggablePopupHelper 包装，
     * 故 {@code relayoutActivePopupsForOrientation} 不会重排它；旋转重建后 layout_game_right 的窗口位置
     * 与宽高都变了，沿用旧绝对坐标会停在旋转前的位置而不在中央。本方法按重建后的新视图重新解算
     * 居中坐标并 update。由 YGOProActivity 旋转重建末尾经 ShowDialogUtil 调用；新视图树此刻可能尚未
     * 完成布局（宽度为 0），则注册一次性布局监听待完成后再重定位。
     */
    public void repositionAfterRotation() {
        if (!showing || popupWindow == null || !(context instanceof Activity)) return;
        Activity activity = (Activity) context;
        final View gameRight = activity.findViewById(R.id.layout_game_right);
        if (gameRight == null) return;
        if (!popupWindow.isShowing()) {
            // 旋转前仍处于「等待布局完成后显示」的延迟分支：旧监听挂在已被 setContentView 销毁的
            // 视图树上不会再触发，按新树重新走一次显示，避免弹窗永不出现且 showing 卡死挡住重显
            showAtFieldCenterOrAwaitLayout(gameRight);
            return;
        }
        if (gameRight.getWidth() > 0) {
            doReposition(gameRight);
        } else {
            gameRight.getViewTreeObserver().addOnGlobalLayoutListener(
                    new ViewTreeObserver.OnGlobalLayoutListener() {
                        @Override
                        public void onGlobalLayout() {
                            gameRight.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                            if (showing && popupWindow != null && popupWindow.isShowing()) {
                                doReposition(gameRight);
                            }
                        }
                    });
        }
    }

    /** 按当前 layout_game_right 重新解算居中坐标并更新已显示弹窗位置（保持原尺寸） */
    private void doReposition(View gameRight) {
        int[] pos = computeCenterPosition(gameRight);
        popupWindow.update(pos[0], pos[1], -1, -1);
        // 旋转重建后锚视图为新实例，将尺寸监听器迁移到新视图
        attachAnchorSizeWatcher(gameRight);
    }

    /**
     * 在锚视图上挂一个尺寸变化监听：任何原因导致 layout_game_right 的宽高变化（包括纯单人
     * 模式下 card_detail_panel 从 GONE 恢复后的重布、横竖屏切换），都会重新解算居中坐标，
     * 保证弹窗在 layout_game_right 中始终居中。同一锚视图重复调用不会叠加监听。
     */
    private void attachAnchorSizeWatcher(View gameRight) {
        if (anchorWatching == gameRight) return;
        detachAnchorSizeWatcher();
        if (gameRight == null) return;
        anchorSizeWatcher = (v, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            int newW = right - left;
            int oldW = oldRight - oldLeft;
            int newH = bottom - top;
            int oldH = oldBottom - oldTop;
            if ((newW != oldW || newH != oldH)
                    && popupWindow != null && popupWindow.isShowing()) {
                int[] pos = computeCenterPosition(v);
                popupWindow.update(pos[0], pos[1], -1, -1);
            }
        };
        gameRight.addOnLayoutChangeListener(anchorSizeWatcher);
        anchorWatching = gameRight;
    }

    private void detachAnchorSizeWatcher() {
        if (anchorWatching != null && anchorSizeWatcher != null) {
            anchorWatching.removeOnLayoutChangeListener(anchorSizeWatcher);
        }
        anchorWatching = null;
        anchorSizeWatcher = null;
    }

    public void dismiss() {
        detachAnchorSizeWatcher();
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