package cn.garymb.ygomobile.utils;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.AbsListView;
import android.widget.FrameLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.HorizontalScrollView;
import android.widget.SeekBar;

import androidx.core.view.ScrollingView;
import androidx.core.widget.NestedScrollView;

import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.Constants;

public class DraggablePopupHelper {
    private static final String PREF_NAME = "popup_positions";
    private static final String KEY_X = "_x";
    private static final String KEY_Y = "_y";
    private static final boolean ENABLE_DRAG = true;
    private static final int DRAG_THRESHOLD = 8;

    /**
     * 已显示的拖拽弹窗层，按窗口层级由下到上排列（末尾 = 最上层）。
     * 这些弹窗都是铺满窗口的独立 PopupWindow，触摸只会送到最上层窗口，
     * 因此由最上层包装层统一做归属判定：命中下层弹窗则跨窗口转发，实现
     * 「手卡公开等下层弹窗的卡片仍可点击」与「点击的弹窗自动置顶」
     */
    private static final List<DragFrameLayout> ACTIVE_LAYERS = new ArrayList<>();

    private final Context context;
    private final SharedPreferences prefs;
    private final String dialogId;

    private int lastX = 0;
    private int lastY = 0;
    private boolean hasSavedPosition = false;

    public DraggablePopupHelper(Context context, String dialogId) {
        this.context = context;
        this.dialogId = dialogId;
        this.prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        loadPosition();
    }

    private void loadPosition() {
        lastX = prefs.getInt(dialogId + KEY_X, 0);
        lastY = prefs.getInt(dialogId + KEY_Y, 0);
        hasSavedPosition = prefs.contains(dialogId + KEY_X);
    }

    public void savePosition(int x, int y) {
        lastX = x;
        lastY = y;
        hasSavedPosition = true;
        prefs.edit()
            .putInt(dialogId + KEY_X, x)
            .putInt(dialogId + KEY_Y, y)
            .apply();
    }

    public void setupDraggablePopup(PopupWindow popupWindow, View contentView,
                                     int contentW, int contentH) {
        if (!ENABLE_DRAG) return;

        ViewGroup originalParent = (ViewGroup) contentView.getParent();
        int index = -1;
        ViewGroup.LayoutParams lp = contentView.getLayoutParams();
        if (originalParent != null) {
            index = originalParent.indexOfChild(contentView);
            originalParent.removeView(contentView);
        }

        DragFrameLayout wrapper = new DragFrameLayout(
                contentView.getContext(), prefs, dialogId);
        // 包装层铺满窗口，内容区之外的触摸需转发给下层弹窗或 Activity 窗口，
        // 否则弹窗显示期间决斗场、双方手卡公开面板等下层 UI 无法响应点击
        wrapper.setHostPopup(popupWindow);
        wrapper.setPassThroughTarget(resolveActivityDecorView(contentView.getContext()));

        FrameLayout.LayoutParams centerLp = new FrameLayout.LayoutParams(contentW, contentH);
        centerLp.gravity = Gravity.CENTER;
        contentView.setLayoutParams(centerLp);
        wrapper.addView(contentView);

        if (originalParent != null) {
            originalParent.addView(wrapper, index, lp);
        } else {
            wrapper.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT));
            wrapper.setClipChildren(false);
            popupWindow.setContentView(wrapper);
            popupWindow.setWidth(ViewGroup.LayoutParams.MATCH_PARENT);
            popupWindow.setHeight(ViewGroup.LayoutParams.MATCH_PARENT);
        }
    }

    public void setupDraggablePopup(PopupWindow popupWindow, View contentView, View handle) {
        if (!ENABLE_DRAG) return;
        final float[] downX = new float[1];
        final float[] downY = new float[1];
        final int[] lastPos = new int[2];
        final boolean[] dragging = new boolean[]{false};

        handle.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    downX[0] = event.getRawX();
                    downY[0] = event.getRawY();
                    int[] loc = new int[2];
                    contentView.getLocationOnScreen(loc);
                    lastPos[0] = loc[0];
                    lastPos[1] = loc[1];
                    dragging[0] = false;
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float dx = event.getRawX() - downX[0];
                    float dy = event.getRawY() - downY[0];
                    if (!dragging[0]) {
                        if (Math.abs(dx) < DRAG_THRESHOLD && Math.abs(dy) < DRAG_THRESHOLD) {
                            return true;
                        }
                        dragging[0] = true;
                        v.performHapticFeedback(
                                android.view.HapticFeedbackConstants.LONG_PRESS);
                        downX[0] = event.getRawX();
                        downY[0] = event.getRawY();
                        return true;
                    }
                    downX[0] = event.getRawX();
                    downY[0] = event.getRawY();
                    lastPos[0] += (int) dx;
                    lastPos[1] += (int) dy;
                    popupWindow.update(lastPos[0], lastPos[1], -1, -1);
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (dragging[0]) {
                        savePosition(lastPos[0], lastPos[1]);
                    }
                    dragging[0] = false;
                    return true;
            }
            return false;
        });
    }

    /**
     * 从 Context 链解析 Activity 的 decorView，作为弹窗内容区之外触摸事件的转发目标；
     * 非 Activity 上下文或 Activity 已销毁时返回 null（退化为不转发）
     */
    private static View resolveActivityDecorView(Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) {
                Activity activity = (Activity) context;
                if (activity.isFinishing() || activity.isDestroyed()) return null;
                return activity.getWindow().getDecorView();
            }
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }

    private static class DragFrameLayout extends FrameLayout {
        private static final String PREF_X = "_x";
        private static final String PREF_Y = "_y";
        private final SharedPreferences prefs;
        private final String dialogId;
        private float grabDX, grabDY, downX, downY;
        private boolean dragging;
        private boolean touchOnScrollable;
        private boolean childConsumedDown;
        /** 本层所属 PopupWindow：点击置顶时重排其窗口层级 */
        private PopupWindow hostPopup;
        /** 兜底转发目标（Activity decorView），null 表示不转发 */
        private View passThroughTarget;
        /** 当前手势的转发目标：null 表示由本层自行处理 */
        private View gestureTarget;
        /** 转发手势的 downTime 与最近落点（目标坐标），手势被打断时补发 ACTION_CANCEL 用 */
        private long forwardedDownTime;
        private float forwardedX, forwardedY;
        /** 正在处理由其他弹窗层转发进来的事件：跳过归属判定，避免二次转发 */
        private boolean receivingForwarded;

        DragFrameLayout(Context context, SharedPreferences prefs, String dialogId) {
            super(context);
            this.prefs = prefs;
            this.dialogId = dialogId;
            setClipChildren(false);
        }

        void setHostPopup(PopupWindow popup) {
            this.hostPopup = popup;
        }

        void setPassThroughTarget(View target) {
            this.passThroughTarget = target;
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            if (passThroughTarget == null) {
                passThroughTarget = resolveActivityDecorView(getContext());
            }
            ACTIVE_LAYERS.remove(this);
            ACTIVE_LAYERS.add(this);
        }

        @Override
        protected void onDetachedFromWindow() {
            ACTIVE_LAYERS.remove(this);
            cancelForwardedGesture();
            super.onDetachedFromWindow();
        }

        /**
         * 触点是否落在对话框内容区（唯一子视图）内；
         * 内容尚未布局（宽高为 0）时视为不在内，避免误吞事件
         */
        private boolean isTouchInsideContent(float x, float y) {
            if (getChildCount() == 0) return false;
            View content = getChildAt(0);
            if (content.getWidth() <= 0 || content.getHeight() <= 0) return false;
            float left = content.getLeft() + content.getTranslationX();
            float top = content.getTop() + content.getTranslationY();
            return x >= left && x < left + content.getWidth()
                    && y >= top && y < top + content.getHeight();
        }

        /** 本层内容区是否覆盖该屏幕坐标（跨弹窗归属判定用） */
        private boolean containsScreenPoint(float rawX, float rawY) {
            if (!isAttachedToWindow() || getChildCount() == 0) return false;
            View content = getChildAt(0);
            if (content.getVisibility() != View.VISIBLE) return false;
            if (content.getWidth() <= 0 || content.getHeight() <= 0) return false;
            int[] loc = new int[2];
            getLocationOnScreen(loc);
            float left = loc[0] + content.getLeft() + content.getTranslationX();
            float top = loc[1] + content.getTop() + content.getTranslationY();
            return rawX >= left && rawX < left + content.getWidth()
                    && rawY >= top && rawY < top + content.getHeight();
        }

        /** 由最上层往下查找触点所属的弹窗层，都不命中返回 null */
        private static DragFrameLayout findTopLayerAt(float rawX, float rawY) {
            for (int i = ACTIVE_LAYERS.size() - 1; i >= 0; i--) {
                DragFrameLayout layer = ACTIVE_LAYERS.get(i);
                if (layer != null && layer.containsScreenPoint(rawX, rawY)) return layer;
            }
            return null;
        }

        /**
         * 判定本次手势的转发目标：
         * 1) 触点在本层内容区 → null（自身处理，按钮点击/拖拽照旧）；
         * 2) 触点在其他弹窗层内容区 → 该层（跨窗口转发，抬手后置顶）；
         * 3) 都不命中 → Activity decorView（决斗场、手卡等下层 UI 继续响应）
         */
        private View resolveGestureTarget(MotionEvent ev) {
            if (isTouchInsideContent(ev.getX(), ev.getY())) return null;
            DragFrameLayout layer = findTopLayerAt(ev.getRawX(), ev.getRawY());
            if (layer != null && layer != this) return layer;
            return passThroughTarget;
        }

        /**
         * 把事件按屏幕坐标换算后转发给目标视图（下层弹窗包装层或 Activity decorView），
         * 使弹窗显示期间决斗场卡片、手卡公开面板的卡片、阶段按钮等仍可响应点击与拖动
         */
        private void forwardEventTo(View target, MotionEvent ev) {
            if (target == null || !target.isAttachedToWindow()) return;
            int[] loc = new int[2];
            target.getLocationOnScreen(loc);
            float x = ev.getRawX() - loc[0];
            float y = ev.getRawY() - loc[1];
            forwardedX = x;
            forwardedY = y;
            MotionEvent copy = MotionEvent.obtain(ev);
            boolean isLayer = target instanceof DragFrameLayout;
            if (isLayer) ((DragFrameLayout) target).receivingForwarded = true;
            try {
                copy.setLocation(x, y);
                target.dispatchTouchEvent(copy);
            } catch (Throwable ignored) {
                // 目标窗口已销毁时忽略，不影响本弹窗交互
            } finally {
                if (isLayer) ((DragFrameLayout) target).receivingForwarded = false;
                copy.recycle();
            }
        }

        /**
         * 手势被中途打断（手指未抬起时弹窗关闭或重排）时向转发目标补发 ACTION_CANCEL，
         * 避免目标视图树残留未结束的按下状态，导致后续点击卡顿或事件错乱
         */
        private void cancelForwardedGesture() {
            if (gestureTarget == null) return;
            View target = gestureTarget;
            gestureTarget = null;
            long downTime = forwardedDownTime;
            forwardedDownTime = 0;
            if (!target.isAttachedToWindow() || downTime == 0) return;
            MotionEvent cancel = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(),
                    MotionEvent.ACTION_CANCEL, forwardedX, forwardedY, 0);
            try {
                target.dispatchTouchEvent(cancel);
            } catch (Throwable ignored) {
                // 目标已销毁时忽略
            } finally {
                cancel.recycle();
            }
        }

        /** 被点中的下层弹窗在抬手后提到最上层（post 到本次派发结束，避免派发中改窗口树） */
        private void bringLayerToFront(View target) {
            if (!(target instanceof DragFrameLayout)) return;
            final DragFrameLayout layer = (DragFrameLayout) target;
            if (ACTIVE_LAYERS.isEmpty()
                    || ACTIVE_LAYERS.get(ACTIVE_LAYERS.size() - 1) == layer) return;
            layer.post(layer::bringHostPopupToFront);
        }

        /**
         * 把本弹窗窗口重排到同类型窗口的最上层：直接对 PopupDecorView 做无动画的
         * removeViewImmediate + addView，不走 PopupWindow.dismiss，避免误触发各对话框
         * OnDismissListener 的业务清理；包装层实例与拖拽位移原样保留，位置与内容状态不丢失
         */
        private void bringHostPopupToFront() {
            PopupWindow popup = hostPopup;
            if (popup == null || !popup.isShowing() || !isAttachedToWindow()) return;
            View decor = getRootView();
            if (decor == this) return;
            ViewGroup.LayoutParams raw = decor.getLayoutParams();
            if (!(raw instanceof WindowManager.LayoutParams)) return;
            WindowManager.LayoutParams lp = (WindowManager.LayoutParams) raw;
            WindowManager wm = (WindowManager) getContext()
                    .getSystemService(Context.WINDOW_SERVICE);
            if (wm == null) return;
            int animations = lp.windowAnimations;
            try {
                // 动画属性先置 0：抑制重排时的退场/入场动画，避免弹窗闪烁
                lp.windowAnimations = 0;
                wm.updateViewLayout(decor, lp);
                wm.removeViewImmediate(decor);
                wm.addView(decor, lp);
            } catch (Throwable ignored) {
                // 窗口状态异常时放弃置顶，弹窗自身仍可继续交互
                return;
            }
            // 恢复动画属性，保证之后 dismiss 仍有退场动画
            lp.windowAnimations = animations;
            try {
                wm.updateViewLayout(decor, lp);
            } catch (Throwable ignored) {
            }
        }

        private boolean isTouchOnScrollableView(float x, float y) {
            if (getChildCount() == 0) return false;
            View contentView = getChildAt(0);
            if (!(contentView instanceof ViewGroup)) return false;
            float cx = x - contentView.getLeft() + contentView.getScrollX();
            float cy = y - contentView.getTop() + contentView.getScrollY();
            if (cx < 0 || cy < 0 || cx >= contentView.getWidth() || cy >= contentView.getHeight())
                return false;
            return findScrollableChild((ViewGroup) contentView, cx, cy) != null;
        }

        private View findScrollableChild(ViewGroup parent, float x, float y) {
            for (int i = parent.getChildCount() - 1; i >= 0; i--) {
                View child = parent.getChildAt(i);
                if (!child.isShown()) continue;

                float cx = x - child.getLeft() + child.getScrollX();
                float cy = y - child.getTop() + child.getScrollY();

                if (cx < 0 || cy < 0 || cx >= child.getWidth() || cy >= child.getHeight()) {
                    continue;
                }

                if (child instanceof ListView) {
                    ListView lv = (ListView) child;
                    int pos = lv.pointToPosition((int) cx, (int) cy);
                    if (pos != ListView.INVALID_POSITION) {
                        return child;
                    }
                } else if (isScrollableView(child)) {
                    return child;
                }

                if (child instanceof ViewGroup) {
                    View found = findScrollableChild((ViewGroup) child, cx, cy);
                    if (found != null) return found;
                }
            }
            return null;
        }

        private static boolean isScrollableView(View v) {
            return v instanceof AbsListView
                    || v instanceof ScrollView
                    || v instanceof HorizontalScrollView
                    || v instanceof NestedScrollView
                    || v instanceof ScrollingView
                    || v instanceof SeekBar;
        }

        private void handleDrag(MotionEvent ev) {
            switch (ev.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    downX = ev.getRawX();
                    downY = ev.getRawY();
                    int[] loc = new int[2];
                    getLocationOnScreen(loc);
                    grabDX = ev.getRawX() - loc[0];
                    grabDY = ev.getRawY() - loc[1];
                    dragging = false;
                    break;

                case MotionEvent.ACTION_MOVE:
                    if (!dragging) {
                        if (Math.abs(ev.getRawX() - downX) >= DRAG_THRESHOLD
                                || Math.abs(ev.getRawY() - downY) >= DRAG_THRESHOLD) {
                            dragging = true;
                            performHapticFeedback(
                                    android.view.HapticFeedbackConstants.LONG_PRESS);
                        }
                    }
                    if (dragging) {
                        int[] cur = new int[2];
                        getLocationOnScreen(cur);
                        int laidX = (int) (cur[0] - getTranslationX());
                        int laidY = (int) (cur[1] - getTranslationY());
                        setTranslationX(ev.getRawX() - grabDX - laidX);
                        setTranslationY(ev.getRawY() - grabDY - laidY);
                    }
                    break;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (dragging) {
                        int[] finalLoc = new int[2];
                        getLocationOnScreen(finalLoc);
                        prefs.edit()
                                .putInt(dialogId + PREF_X, finalLoc[0])
                                .putInt(dialogId + PREF_Y, finalLoc[1])
                                .apply();
                    }
                    dragging = false;
                    break;
            }
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent ev) {
            final int action = ev.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                if (gestureTarget != null) {
                    // 极端情况下上一手势未收到 UP/CANCEL：先结束它再重新判定
                    cancelForwardedGesture();
                }
                // 转发进来的事件不再做归属判定，直接按本层内容处理（可点击、可拖动）
                gestureTarget = receivingForwarded ? null : resolveGestureTarget(ev);
                if (gestureTarget != null) {
                    forwardedDownTime = ev.getDownTime();
                    forwardEventTo(gestureTarget, ev);
                    // 必须消费 DOWN：窗口不消费按下事件时系统不再派发本手势后续事件，
                    // 目标将收不到 UP（点击不生效），状态位卡死还会让本弹窗按钮与拖拽失效
                    return true;
                }
            } else if (gestureTarget != null) {
                View target = gestureTarget;
                boolean ending = action == MotionEvent.ACTION_UP
                        || action == MotionEvent.ACTION_CANCEL;
                // 先复位再转发：目标可能在 UP 回调中关闭本弹窗，避免重复补发 CANCEL
                if (ending) {
                    gestureTarget = null;
                    forwardedDownTime = 0;
                }
                forwardEventTo(target, ev);
                if (action == MotionEvent.ACTION_UP) {
                    bringLayerToFront(target);
                }
                return true;
            }

            switch (ev.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    touchOnScrollable = isTouchOnScrollableView(ev.getX(), ev.getY());
                    if (touchOnScrollable) {
                        childConsumedDown = false;
                        return super.dispatchTouchEvent(ev);
                    }
                    handleDrag(ev);
                    boolean result = super.dispatchTouchEvent(ev);
                    childConsumedDown = result;
                    return result || true;

                case MotionEvent.ACTION_MOVE:
                    if (!touchOnScrollable) {
                        handleDrag(ev);
                        if (dragging) {
                            if (childConsumedDown) {
                                MotionEvent cancel = MotionEvent.obtain(ev);
                                cancel.setAction(MotionEvent.ACTION_CANCEL);
                                super.dispatchTouchEvent(cancel);
                                cancel.recycle();
                            }
                            return true;
                        }
                    }
                    return super.dispatchTouchEvent(ev);

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (!touchOnScrollable) {
                        handleDrag(ev);
                    }
                    boolean upResult = super.dispatchTouchEvent(ev);
                    dragging = false;
                    touchOnScrollable = false;
                    childConsumedDown = false;
                    return childConsumedDown ? upResult : (upResult || true);
            }
            return super.dispatchTouchEvent(ev);
        }
    }

    public void setupDraggableView(View targetView) {
        if (!ENABLE_DRAG) return;
        if (!(targetView instanceof ViewGroup)) {
            setupSimpleDraggableView(targetView);
            return;
        }

        final ViewGroup viewGroup = (ViewGroup) targetView;
        final float[] grabDX = new float[1];
        final float[] grabDY = new float[1];
        final boolean[] dragging = new boolean[]{false};

        View.OnTouchListener dragListener = (v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    grabDX[0] = event.getRawX() - targetView.getLeft();
                    grabDY[0] = event.getRawY() - targetView.getTop();
                    dragging[0] = false;
                    return false;

                case MotionEvent.ACTION_MOVE:
                    if (!dragging[0]) {
                        float dx = event.getRawX() - (targetView.getLeft() + grabDX[0]);
                        float dy = event.getRawY() - (targetView.getTop() + grabDY[0]);
                        if (Math.abs(dx) < 8 && Math.abs(dy) < 8) {
                            return false;
                        }
                        dragging[0] = true;
                        targetView.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                    }
                    int newX = (int) (event.getRawX() - grabDX[0]);
                    int newY = (int) (event.getRawY() - grabDY[0]);
                    targetView.setLeft(newX);
                    targetView.setTop(newY);
                    targetView.setRight(newX + targetView.getWidth());
                    targetView.setBottom(newY + targetView.getHeight());
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (dragging[0]) {
                        savePosition(targetView.getLeft(), targetView.getTop());
                    }
                    dragging[0] = false;
                    return false;
            }
            return false;
        };

        setTouchListenerRecursively(viewGroup, dragListener);
        targetView.setOnTouchListener(dragListener);
    }

    private void setTouchListenerRecursively(ViewGroup parent, View.OnTouchListener listener) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            child.setOnTouchListener(listener);
            
            if (child instanceof ViewGroup) {
                setTouchListenerRecursively((ViewGroup) child, listener);
            }
        }
    }

    private void setupSimpleDraggableView(View targetView) {
        if (!ENABLE_DRAG) return;
        final float[] grabDX = new float[1];
        final float[] grabDY = new float[1];
        final boolean[] dragging = new boolean[]{false};

        targetView.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    grabDX[0] = event.getRawX() - targetView.getLeft();
                    grabDY[0] = event.getRawY() - targetView.getTop();
                    dragging[0] = false;
                    return false;

                case MotionEvent.ACTION_MOVE:
                    if (!dragging[0]) {
                        float dx = event.getRawX() - (targetView.getLeft() + grabDX[0]);
                        float dy = event.getRawY() - (targetView.getTop() + grabDY[0]);
                        if (Math.abs(dx) < 8 && Math.abs(dy) < 8) {
                            return false;
                        }
                        dragging[0] = true;
                        v.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
                    }
                    int newX = (int) (event.getRawX() - grabDX[0]);
                    int newY = (int) (event.getRawY() - grabDY[0]);
                    targetView.setLeft(newX);
                    targetView.setTop(newY);
                    targetView.setRight(newX + targetView.getWidth());
                    targetView.setBottom(newY + targetView.getHeight());
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (dragging[0]) {
                        savePosition(targetView.getLeft(), targetView.getTop());
                    }
                    dragging[0] = false;
                    return false;
            }
            return false;
        });
    }

    public void applySavedPositionToView(View targetView) {
        if (hasSavedPosition) {
            targetView.post(() -> {
                targetView.setLeft(lastX);
                targetView.setTop(lastY);
                targetView.setRight(lastX + targetView.getWidth());
                targetView.setBottom(lastY + targetView.getHeight());
            });
        }
    }

    public void showPopup(PopupWindow popupWindow, View anchorView) {
        showPopup(popupWindow, anchorView, Gravity.CENTER, 0, 0);
    }

    /**
     * 将经 setupDraggablePopup 包装为全窗口的 popup 内容，从「整个窗口居中」改为
     * 「按指定区域宽高居中」（如 layout_game_right）：
     * 包装层（DragFrameLayout）铺满窗口，内部对话框以 Gravity.CENTER 居中，
     * 通过非对称 margin 把内容中心平移「区域中心 - 窗口中心」的偏移量即可。
     * 若区域尚未布局完成（宽高为 0），通过 OnGlobalLayoutListener 等待布局后再应用，
     * 避免退化为按整个窗口居中。
     */
    public static void centerPopupInRegion(PopupWindow popupWindow, View region) {
        if (popupWindow == null || region == null) return;
        if (region.getWidth() <= 0 || region.getHeight() <= 0) {
            region.getViewTreeObserver().addOnGlobalLayoutListener(
                    new ViewTreeObserver.OnGlobalLayoutListener() {
                        @Override
                        public void onGlobalLayout() {
                            region.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                            centerPopupInRegion(popupWindow, region);
                        }
                    });
            return;
        }
        View wrapper = popupWindow.getContentView();
        if (!(wrapper instanceof ViewGroup)) return;
        ViewGroup wrapperGroup = (ViewGroup) wrapper;
        if (wrapperGroup.getChildCount() == 0) return;
        View content = wrapperGroup.getChildAt(0);
        ViewGroup.LayoutParams raw = content.getLayoutParams();
        if (!(raw instanceof FrameLayout.LayoutParams)) return;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) raw;

        View window = region.getRootView();
        int winW = window.getWidth();
        int winH = window.getHeight();
        if (winW <= 0 || winH <= 0) return;
        int[] regionLoc = new int[2];
        region.getLocationInWindow(regionLoc);
        int[] winLoc = new int[2];
        window.getLocationInWindow(winLoc);
        // CENTER gravity 下 leftMargin/topMargin 将内容整体平移该偏移量
        lp.leftMargin = (regionLoc[0] + region.getWidth() / 2) - (winLoc[0] + winW / 2);
        lp.rightMargin = 0;
        lp.topMargin = (regionLoc[1] + region.getHeight() / 2) - (winLoc[1] + winH / 2);
        lp.bottomMargin = 0;
        content.setLayoutParams(lp);
    }

    public void showPopup(PopupWindow popupWindow, View anchorView, int gravity, int xOffset, int yOffset) {
        if (context instanceof Activity) {
            Activity activity = (Activity) context;
            if (activity.isFinishing() || activity.isDestroyed()) {
                return;
            }
        }

        View effectiveAnchor = null;
        if (context instanceof Activity) {
            Activity activity = (Activity) context;
            View decorView = activity.getWindow().getDecorView();
            effectiveAnchor = decorView;
        }
        if (effectiveAnchor == null) {
            effectiveAnchor = anchorView;
        }
        if (effectiveAnchor == null || effectiveAnchor.getWindowToken() == null) {
            return;
        }

        try {
            if (hasSavedPosition) {
                popupWindow.showAtLocation(effectiveAnchor, Gravity.NO_GRAVITY, lastX, lastY);
            } else {
                popupWindow.showAtLocation(effectiveAnchor, gravity, xOffset, yOffset);
            }
        } catch (Exception e) {
            // Token may become invalid (e.g. OPPO ColorOS OplusViewRootImplHooks$ColorW)
        }
    }

    public static void resetAllPositions(Context context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply();
    }
}
