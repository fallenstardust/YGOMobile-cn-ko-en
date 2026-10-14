package cn.garymb.ygomobile.render;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.PopupWindow;

import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.utils.DraggablePopupHelper;

/**
 * 横屏侧边六按钮（设置/决斗记录/音乐/加速动画/表情/聊天开关）独立浮层承载，
 * 布局见 layout_image_buttons_panel。
 * <p>
 * GameFieldView 为 setZOrderOnTop(true) 的 GL 曲面，合成在 Activity 窗口之上，
 * 视图树内普通 View 会被不透明场图像素遮挡——竖列上部的设置/记录/音乐/加速四颗
 * 在决斗中会被场地图覆盖，故本浮层把六按钮放入自有 PopupWindow：独立窗口层级位于
 * GL 曲面之上，与 C++ gframe 的 GUI 恒在场景之上一致，决斗场因此恢复铺满
 * layout_game_right 的原始布局。
 * <p>
 * 视图树中的 layout_image_buttons_panel（layout_game_right 底部 include）在横屏由
 * YGOProActivity 置 INVISIBLE 只保留宽度占位，聊天输入框在同一直行以 0dp+weight 占
 * 剩余宽度，浮层实际绘制按钮落在占位区上方——按钮与输入框按同一水平线并列、互不重叠。
 * <p>
 * 锚定与显隐：紧贴 layout_game_right 最左侧边缘、按区域底部定位（下边距与原 XML 一致
 * 4dp），定位基准取锚点在 Activity 窗口内的坐标（getLocationInWindow）而非屏幕坐标：
 * PopupWindow 的 showAtLocation/update 偏移量以锚点所属窗口为原点，屏幕坐标在刘海遮挡/
 * 非全屏窗口时会多出窗口左缘偏移，导致按钮列不能贴齐区域左缘。锚点不可见（卡组编辑/
 * 主菜单等场景）时自动隐藏；锚点尺寸/位置变化经全局布局监听实时跟随。
 * 全屏透明 dialog 窗口盖在上层时，落在按钮区的触摸经
 * {@link DraggablePopupHelper#registerTouchOverlay} 穿透注册表跨窗口转发给本浮层，
 * 保证 dialog 显示期间六按钮仍可点击。
 */
public class SideButtonsOverlay {

    private final Activity activity;
    private final View root;
    private View anchor;
    private PopupWindow window;
    private ViewTreeObserver.OnGlobalLayoutListener followListener;
    private final int marginBottomPx;
    /** 上次定位结果，避免每次布局回调都 update 窗口 */
    private int lastX = Integer.MIN_VALUE;
    private int lastY = Integer.MIN_VALUE;

    public SideButtonsOverlay(Activity activity, View anchor) {
        this.activity = activity;
        this.anchor = anchor;
        float density = activity.getResources().getDisplayMetrics().density;
        marginBottomPx = Math.round(4f * density);
        root = LayoutInflater.from(activity)
                .inflate(R.layout.layout_image_buttons_panel, null);
        if (anchor != null) {
            followListener = this::sync;
            anchor.getViewTreeObserver().addOnGlobalLayoutListener(followListener);
        }
        sync();
    }

    public View getRoot() {
        return root;
    }

    public boolean isShowing() {
        return window != null && window.isShowing();
    }

    /** 按锚点当前显隐与几何同步浮层：显示中仅挪位，锚点不可见时收起 */
    public void sync() {
        if (activity.isFinishing()
                || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1
                    && activity.isDestroyed())) {
            return;
        }
        if (anchor == null || !anchor.isShown()) {
            dismissWindow();
            return;
        }
        ensureWindow();
        int[] pos = resolvePosition();
        View decor = activity.getWindow().getDecorView();
        if (decor == null || decor.getWindowToken() == null) {
            // 窗口 token 未就绪（首帧前/旋转重建中）：下一帧重试
            root.post(this::sync);
            return;
        }
        if (window.isShowing()) {
            if (pos[0] != lastX || pos[1] != lastY) {
                lastX = pos[0];
                lastY = pos[1];
                try {
                    window.update(pos[0], pos[1], -1, -1);
                } catch (Exception ignored) {
                    // 窗口状态异常时等待下一次布局回调重同步
                }
            }
            return;
        }
        try {
            window.showAtLocation(decor, Gravity.NO_GRAVITY, pos[0], pos[1]);
            lastX = pos[0];
            lastY = pos[1];
            // 首显后内容实测尺寸就绪再校正一次：预估高与实际布局高有差时贴齐底部
            root.post(this::sync);
        } catch (Exception ignored) {
            root.post(this::sync);
        }
    }

    /** 锚点窗口内左缘（紧贴，用户规格）与底部（上留原 XML 4dp 下边距）解算浮层位置 */
    private int[] resolvePosition() {
        int[] loc = new int[2];
        // 窗口坐标为基准：showAtLocation/update 的偏移以锚点所属（Activity）窗口为原点，
        // 用屏幕坐标会在刘海遮挡/非全屏窗口时多出窗口左缘偏移，无法贴齐区域左缘
        anchor.getLocationInWindow(loc);
        int h = root.getHeight();
        if (h <= 0) {
            // 首显前未布局：按 UNSPECIFIED 实测一次，显示后布局回调再校正
            root.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            h = root.getMeasuredHeight();
        }
        int x = loc[0];
        int y = loc[1] + anchor.getHeight() - marginBottomPx - h;
        return new int[]{x, y};
    }

    private void ensureWindow() {
        if (window != null) return;
        window = new PopupWindow(root,
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        // 非聚焦小窗：不抢对话框/输入法的焦点，窗口范围外的触摸自然落到下层窗口
        window.setFocusable(false);
        window.setOutsideTouchable(false);
        window.setTouchable(true);
        DraggablePopupHelper.registerTouchOverlay(root);
    }

    private void dismissWindow() {
        lastX = Integer.MIN_VALUE;
        lastY = Integer.MIN_VALUE;
        try {
            if (window != null && window.isShowing()) window.dismiss();
        } catch (Exception ignored) {
        }
    }

    /** 旋转重建/销毁时释放：摘除布局监听、注销穿透注册并收起窗口 */
    public void release() {
        if (anchor != null && followListener != null
                && anchor.getViewTreeObserver().isAlive()) {
            anchor.getViewTreeObserver().removeOnGlobalLayoutListener(followListener);
        }
        followListener = null;
        DraggablePopupHelper.unregisterTouchOverlay(root);
        dismissWindow();
        anchor = null;
    }
}
