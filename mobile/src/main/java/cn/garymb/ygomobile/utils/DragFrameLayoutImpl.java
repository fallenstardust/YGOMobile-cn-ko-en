package cn.garymb.ygomobile.utils;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.AbsListView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.HorizontalScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.core.view.ScrollingView;
import androidx.core.widget.NestedScrollView;

import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.game.chat.ChatInputUI;
import cn.garymb.ygomobile.game.gamefield.GameFieldController;
import cn.garymb.ygomobile.lite.R;

import static cn.garymb.ygomobile.utils.DraggablePopupHelperBase.DragFrameLayout;
import static cn.garymb.ygomobile.utils.DraggablePopupHelper.*;

/**
 * 弹窗全窗口包装层的具体实现叶子类（{@link DragFrameLayoutBase} 的下游）。
 *
 * <p>持有滚动/按压子视图判定、拖拽平移处理与限幅，以及重写的
 * {@code dispatchTouchEvent}（跨窗口触摸转发、点击置顶与拖拽抢占的统一入口）。
 * 运行时实际实例为声明在 {@link DraggablePopupHelperBase} 中的壳类型
 * {@code DragFrameLayout}（本类的子类），行为与原嵌套类完全一致。</p>
 */
class DragFrameLayoutImpl extends DragFrameLayoutBase {
        DragFrameLayoutImpl(Context context, SharedPreferences prefs, String dialogId) {
            super(context, prefs, dialogId);
        }

        /**
         * 触点是否落在任一可见已布局子视图（对话框内容 / 收缩横条 / 收缩把手）内，
         * 隐藏中的不计；子视图尚未布局（宽高为 0）时视为不在内，避免误吞事件
         */
        protected boolean isTouchInsideContent(float x, float y) {
            for (int i = 0; i < getChildCount(); i++) {
                if (isTouchInView(getChildAt(i), x, y)) return true;
            }
            return false;
        }

        /** 触点是否落在指定子视图的可见已布局区域内（包装层本地坐标） */
        protected static boolean isTouchInView(View child, float x, float y) {
            if (child == null || child.getVisibility() != VISIBLE) return false;
            if (child.getWidth() <= 0 || child.getHeight() <= 0) return false;
            float left = child.getLeft() + child.getTranslationX();
            float top = child.getTop() + child.getTranslationY();
            return x >= left && x < left + child.getWidth()
                    && y >= top && y < top + child.getHeight();
        }

        /** 触点是否落在收缩横条/把手上：自行消费（普通子视图点击），不参与拖拽 */
        protected boolean isTouchOnCollapseWidget(float x, float y) {
            return isTouchInView(collapseBar, x, y) || isTouchInView(collapseHandle, x, y);
        }

        /** 本层内容区是否覆盖该屏幕坐标（跨弹窗归属判定用；含收缩横条/把手区域） */
        protected boolean containsScreenPoint(float rawX, float rawY) {
            if (!isAttachedToWindow() || getChildCount() == 0) return false;
            int[] loc = new int[2];
            getLocationOnScreen(loc);
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child.getVisibility() != VISIBLE) continue;
                if (child.getWidth() <= 0 || child.getHeight() <= 0) continue;
                float left = loc[0] + child.getLeft() + child.getTranslationX();
                float top = loc[1] + child.getTop() + child.getTranslationY();
                if (rawX >= left && rawX < left + child.getWidth()
                        && rawY >= top && rawY < top + child.getHeight()) {
                    return true;
                }
            }
            return false;
        }

        /** 由最上层往下查找触点所属的弹窗层，都不命中返回 null */
        protected static DragFrameLayout findTopLayerAt(float rawX, float rawY) {
            for (int i = ACTIVE_LAYERS.size() - 1; i >= 0; i--) {
                DragFrameLayout layer = ACTIVE_LAYERS.get(i);
                if (layer != null && layer.containsScreenPoint(rawX, rawY)) return layer;
            }
            return null;
        }

        /**
         * 触点是否落在 Activity 视图树的聊天输入框上（屏幕坐标判定）：决斗中显示的对话框
         * 底边可能压到输入框上沿，此时按「触点在本层内容区」由本层吞掉事件，玩家就点不到
         * 输入框、无法唤起输入法（用户规格：显示 dialog 时也要能点输入框抬键盘与输入法）。
         * 命中时本层放弃这次手势，原样交给 Activity 视图树。
         */
        protected boolean isTouchOnChatInput(float rawX, float rawY) {
            Activity act = resolveActivity(getContext());
            if (act == null) return false;
            View chat = act.findViewById(R.id.et_chat_input);
            if (chat == null || !chat.isShown() || chat.getWidth() <= 0 || chat.getHeight() <= 0) {
                return false;
            }
            int[] loc = new int[2];
            chat.getLocationOnScreen(loc);
            return rawX >= loc[0] && rawX < loc[0] + chat.getWidth()
                    && rawY >= loc[1] && rawY < loc[1] + chat.getHeight();
        }

        /**
         * 判定本次手势的转发目标：
         * 0) 触点在 Activity 的聊天输入框上 → Activity decor（对话框压住输入框时点击仍直达输入框）；
         * 1) 触点在本层内容区 → null（自身处理，按钮点击/拖拽照旧）；
         * 2) 触点在其他弹窗层内容区 → 该层（跨窗口转发，抬手后置顶）；
         * 3) 都不命中 → Activity decorView（决斗场、手卡、侧边六按钮等树内下层 UI 继续响应）
         */
        protected View resolveGestureTarget(MotionEvent ev) {
            if (isTouchOnChatInput(ev.getRawX(), ev.getRawY())) return passThroughTarget;
            // 触点不在 Activity 的聊天输入框上：先按用户规格让持有焦点的输入框失焦并收起
            // 输入法（本层接管的按下事件不经 Activity.dispatchTouchEvent，需在此补同一判定）
            ChatInputUI.clearFocusIfTouchedOutside(resolveActivity(getContext()),
                    ev.getRawX(), ev.getRawY());
            if (isTouchInsideContent(ev.getX(), ev.getY())) return null;
            DragFrameLayout layer = findTopLayerAt(ev.getRawX(), ev.getRawY());
            if (layer != null && layer != this) return layer;
            return passThroughTarget;
        }

        /**
         * 把事件按屏幕坐标换算后转发给目标视图（下层弹窗包装层或 Activity decorView），
         * 使弹窗显示期间决斗场卡片、手卡公开面板的卡片、阶段按钮等仍可响应点击与拖动
         */
        protected void forwardEventTo(View target, MotionEvent ev) {
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
        protected void cancelForwardedGesture() {
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
        protected void bringLayerToFront(View target) {
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
        protected void bringHostPopupToFront() {
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

        protected boolean isTouchOnScrollableView(float x, float y) {
            if (getChildCount() == 0) return false;
            View contentView = getChildAt(0);
            if (contentView.getVisibility() != VISIBLE) return false;
            if (!(contentView instanceof ViewGroup)) return false;
            // 命中坐标以子视图「可视位置」为准（布局位+平移）：拖拽平移施加在子视图
            // translation 上（层自身不动），Android 真实派发按变换后矩阵命中子视图，
            // 预判定不计平移会把滑块/列表的按下误判成非滚动区而抢去拖 dialog（用户反馈）
            float cx = x - contentView.getLeft() - contentView.getTranslationX()
                    + contentView.getScrollX();
            float cy = y - contentView.getTop() - contentView.getTranslationY()
                    + contentView.getScrollY();
            if (cx < 0 || cy < 0 || cx >= contentView.getWidth() || cy >= contentView.getHeight())
                return false;
            return findScrollableChild((ViewGroup) contentView, cx, cy) != null;
        }
        protected View findScrollableChild(ViewGroup parent, float x, float y) {
            for (int i = parent.getChildCount() - 1; i >= 0; i--) {
                View child = parent.getChildAt(i);
                if (!child.isShown()) continue;

                float cx = x - child.getLeft() - child.getTranslationX() + child.getScrollX();
                float cy = y - child.getTop() - child.getTranslationY() + child.getScrollY();

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

        protected static boolean isScrollableView(View v) {
            return v instanceof AbsListView
                    || v instanceof ScrollView
                    || v instanceof HorizontalScrollView
                    || v instanceof NestedScrollView
                    || v instanceof ScrollingView
                    || v instanceof SeekBar;
        }

        /**
         * 内容子树中是否有「正在按压跟踪」的可滚动视图（滑块/列表）：预判定因缩放、
         * 旋转重排等残余坐标误差漏判时，子视图实际已接住 DOWN 并在跟踪（Android 真实
         * 派发按变换后矩阵命中），此刻绝不允许拖拽再抢走手势——否则选卡/卡片确认弹窗
         * 拖完一次横向滑块后再次按住滑块会变成拖动 dialog（用户反馈）。
         */
        protected boolean hasPressedScrollableChild() {
            return getChildCount() > 0 && findPressedScrollable(getChildAt(0));
        }

        protected static boolean findPressedScrollable(View v) {
            if (!v.isShown()) return false;
            if (isScrollableView(v) && v.isPressed()) return true;
            if (v instanceof ViewGroup) {
                ViewGroup g = (ViewGroup) v;
                for (int i = 0, n = g.getChildCount(); i < n; i++) {
                    if (findPressedScrollable(g.getChildAt(i))) return true;
                }
            }
            return false;
        }

        protected void handleDrag(MotionEvent ev) {
            if (!dragEnabled) return;
            switch (ev.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    downX = ev.getRawX();
                    downY = ev.getRawY();
                    // 以当前拖拽平移量为起点，子视图随手指相对位移移动（层自身不平移）
                    startDragTX = dragTX;
                    startDragTY = dragTY;
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
                        float[] clamped = clampLayerTranslation(
                                startDragTX + (ev.getRawX() - downX),
                                startDragTY + (ev.getRawY() - downY));
                        dragTX = clamped[0];
                        dragTY = clamped[1];
                        applyDragOffsetToChildren();
                    }
                    break;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (dragging) {
                        // 区域锚定弹窗不持久化拖拽偏移：下次显示/旋转都按区域重算定位，
                        // 旧方向的绝对屏幕坐标重放到新方向会把内容推到屏外
                        if (centerRegionId == View.NO_ID) {
                            int[] finalLoc = new int[2];
                            getLocationOnScreen(finalLoc);
                            prefs.edit()
                                    .putInt(dialogId + PREF_X, finalLoc[0] + (int) dragTX)
                                    .putInt(dialogId + PREF_Y, finalLoc[1] + (int) dragTY)
                                    .apply();
                        }
                    }
                    dragging = false;
                    break;
            }
        }

        /**
         * 限定拖拽平移幅度：保证对话框内容至少保留 {@code keep} 像素在屏内。拖拽偏移一旦
         * 把内容移到屏外将无法再与其交互（询问窗丢失即死锁），因此不允许拖到不可恢复的位置。
         * 平移施加在各子视图上（层自身铺满窗口不移动），几何解算以内容子视图布局位为基准。
         */
        protected float[] clampLayerTranslation(float tx, float ty) {
            if (getChildCount() == 0 || getWidth() <= 0 || getHeight() <= 0) {
                return new float[]{tx, ty};
            }
            View content = getChildAt(0);
            if (content.getVisibility() != VISIBLE || content.getWidth() <= 0
                    || content.getHeight() <= 0) {
                return new float[]{tx, ty};
            }
            int keep = dp(getContext(), 32);
            float w = content.getWidth() * content.getScaleX();
            float h = content.getHeight() * content.getScaleY();
            float visLeft = content.getLeft() + (content.getWidth() - w) / 2f;
            float visTop = content.getTop() + (content.getHeight() - h) / 2f;
            float minTx = keep - (visLeft + w);
            float maxTx = getWidth() - keep - visLeft;
            float minTy = keep - (visTop + h);
            float maxTy = getHeight() - keep - visTop;
            return new float[]{
                    Math.max(minTx, Math.min(maxTx, tx)),
                    Math.max(minTy, Math.min(maxTy, ty))};
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent ev) {
            final int action = ev.getActionMasked();
            // 卡片选中态与状态气泡的取消观察（用户规格：点弹窗自身的按钮/其它弹窗/其它按钮
            // 都要取消被选中的卡）：本层接管的手势不经 Activity.dispatchTouchEvent，故在此
            // 双挂同一登记/结算——登记为「落在决斗场之外」候选，手势落定时若决斗场并未接住
            // 本次按下即取消选中（收起金色呼吸外框、手卡降回常规高度并收起状态气泡）
            if (action == MotionEvent.ACTION_DOWN) {
                GameFieldController.onOutsideGestureDown();
            } else {
                GameFieldController.onOutsideGestureUp(resolveActivity(getContext()), action);
            }
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
                    touchOnCollapseWidget = isTouchOnCollapseWidget(ev.getX(), ev.getY());
                    if (touchOnCollapseWidget) {
                        // 收缩横条/把手上的触点只作普通子视图点击：禁用拖拽避免移动整个包装层
                        childConsumedDown = false;
                        return super.dispatchTouchEvent(ev);
                    }
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
                    if (!touchOnScrollable && !touchOnCollapseWidget && hasPressedScrollableChild()) {
                        // DOWN 时预判定漏了、但可滚动子视图（滑块/列表）实际已接住并在跟踪：
                        // 改判为滚动手势交回子视图，禁止起拖 dialog
                        touchOnScrollable = true;
                        return super.dispatchTouchEvent(ev);
                    }
                    if (!touchOnScrollable && !touchOnCollapseWidget) {
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
                    if (!touchOnScrollable && !touchOnCollapseWidget) {
                        handleDrag(ev);
                    }
                    boolean upResult = super.dispatchTouchEvent(ev);
                    dragging = false;
                    touchOnScrollable = false;
                    touchOnCollapseWidget = false;
                    childConsumedDown = false;
                    return childConsumedDown ? upResult : (upResult || true);
            }
            return super.dispatchTouchEvent(ev);
        }
}

