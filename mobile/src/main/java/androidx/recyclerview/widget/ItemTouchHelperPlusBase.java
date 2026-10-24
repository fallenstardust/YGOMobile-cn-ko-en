/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.recyclerview.widget;


import android.animation.Animator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.util.Log;
import android.view.GestureDetector;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.view.animation.Interpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.GestureDetectorCompat;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.R;
import androidx.recyclerview.widget.RecyclerView.OnItemTouchListener;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;

import java.util.ArrayList;
import java.util.List;

/**
 * ItemTouchHelperPlus 拆分后的抽象基类（继承链顶端）。
 *
 * <p>本类由原 2708 行的 {@link ItemTouchHelperPlus} 单文件拆分而来，行为与实现细节完全保持不变。
 * 继承链为：ItemTouchHelperPlusBase ← {@link ItemTouchHelperPlusMiddle} ← {@link ItemTouchHelperPlus}。
 * 本类持有触摸助手的共享状态字段、常量、绘制回调以及 {@code Callback}／{@code Callback2} 等嵌套类型声明，
 * 嵌套类型保留在此以保证全项目范围内 {@code ItemTouchHelperPlus.Callback} 之类的限定引用继续可解析。</p>
 *
 * <p>文末的抽象钩子（setupCallbacks/select/scrollIfNecessary 等）由链下游子类实现，
 * 使本类中原本调用叶子类方法的代码保持原有调用方向不变。</p>
 */
@SuppressWarnings("RestrictedApi")
public abstract class ItemTouchHelperPlusBase extends RecyclerView.ItemDecoration implements RecyclerView.OnChildAttachStateChangeListener {
    /**
     * Up direction, used for swipe & drag control.
     */
    public static final int UP = 1;

    /**
     * Down direction, used for swipe & drag control.
     */
    public static final int DOWN = 1 << 1;

    /**
     * Left direction, used for swipe & drag control.
     */
    public static final int LEFT = 1 << 2;

    /**
     * Right direction, used for swipe & drag control.
     */
    public static final int RIGHT = 1 << 3;

    // If you change these relative direction values, update Callback#convertToAbsoluteDirection,
    // Callback#convertToRelativeDirection.
    /**
     * Horizontal start direction. Resolved to LEFT or RIGHT depending on RecyclerView's layout
     * direction. Used for swipe & drag control.
     */
    public static final int START = LEFT << 2;

    /**
     * Horizontal end direction. Resolved to LEFT or RIGHT depending on RecyclerView's layout
     * direction. Used for swipe & drag control.
     */
    public static final int END = RIGHT << 2;

    /**
     * ItemTouchHelper is in idle state. At this state, either there is no related motion event by
     * the user or latest motion events have not yet triggered a swipe or drag.
     */
    public static final int ACTION_STATE_IDLE = 0;

    /**
     * A View is currently being swiped.
     */
    @SuppressWarnings("WeakerAccess")
    public static final int ACTION_STATE_SWIPE = 1;

    /**
     * A View is currently being dragged.
     */
    @SuppressWarnings("WeakerAccess")
    public static final int ACTION_STATE_DRAG = 2;

    /**
     * Animation type for views which are swiped successfully.
     */
    @SuppressWarnings("WeakerAccess")
    public static final int ANIMATION_TYPE_SWIPE_SUCCESS = 1 << 1;

    /**
     * Animation type for views which are not completely swiped thus will animate back to their
     * original position.
     */
    @SuppressWarnings("WeakerAccess")
    public static final int ANIMATION_TYPE_SWIPE_CANCEL = 1 << 2;

    /**
     * Animation type for views that were dragged and now will animate to their final position.
     */
    @SuppressWarnings("WeakerAccess")
    public static final int ANIMATION_TYPE_DRAG = 1 << 3;

    protected static final String TAG = "ItemTouchHelper";

    protected static final boolean DEBUG = false;

    protected static final int ACTIVE_POINTER_ID_NONE = -1;

    static final int DIRECTION_FLAG_COUNT = 8;

    protected static final int ACTION_MODE_IDLE_MASK = (1 << DIRECTION_FLAG_COUNT) - 1;

    static final int ACTION_MODE_SWIPE_MASK = ACTION_MODE_IDLE_MASK << DIRECTION_FLAG_COUNT;

    static final int ACTION_MODE_DRAG_MASK = ACTION_MODE_SWIPE_MASK << DIRECTION_FLAG_COUNT;

    /**
     * The unit we are using to track velocity
     */
    protected static final int PIXELS_PER_SECOND = 1000;


    /**
     * Views, whose state should be cleared after they are detached from RecyclerView.
     * This is necessary after swipe dismissing an item. We wait until animator finishes its job
     * to clean these views.
     */
    final List<View> mPendingCleanup = new ArrayList<>();

    /**
     * Re-use array to calculate dx dy for a ViewHolder
     */
    protected final float[] mTmpPosition = new float[2];

    /**
     * Currently selected view holder
     */
    @SuppressWarnings("WeakerAccess") /* synthetic access */
            ViewHolder mSelected = null;

    /**
     * The reference coordinates for the action start. For drag & drop, this is the time long
     * press is completed vs for swipe, this is the initial touch point.
     */
    float mInitialTouchX;

    float mInitialTouchY;

    /**
     * Set when ItemTouchHelper is assigned to a RecyclerView.
     */
    protected float mSwipeEscapeVelocity;

    /**
     * Set when ItemTouchHelper is assigned to a RecyclerView.
     */
    protected float mMaxSwipeVelocity;

    /**
     * The diff between the last event and initial touch.
     */
    float mDx;

    float mDy;

    /**
     * The coordinates of the selected view at the time it is selected. We record these values
     * when action starts so that we can consistently position it even if LayoutManager moves the
     * View.
     */
    protected float mSelectedStartX;

    protected float mSelectedStartY;

    /**
     * The pointer we are tracking.
     */
    @SuppressWarnings("WeakerAccess") /* synthetic access */
            int mActivePointerId = ACTIVE_POINTER_ID_NONE;

    /**
     * Developer callback which controls the behavior of ItemTouchHelperPlus.
     */
    @NonNull
    ItemTouchHelperPlus.Callback mCallback;

    /**
     * Current mode.
     */
    protected int mActionState = ACTION_STATE_IDLE;

    /**
     * The direction flags obtained from unmasking
     * {@link ItemTouchHelperPlus.Callback#getAbsoluteMovementFlags(RecyclerView, RecyclerView.ViewHolder)} for the current
     * action state.
     */
    @SuppressWarnings("WeakerAccess") /* synthetic access */
            int mSelectedFlags;

    /**
     * When a View is dragged or swiped and needs to go back to where it was, we create a Recover
     * Animation and animate it to its location using this custom Animator, instead of using
     * framework Animators.
     * Using framework animators has the side effect of clashing with ItemAnimator, creating
     * jumpy UIs.
     */
    List<RecoverAnimation> mRecoverAnimations = new ArrayList<>();

    protected int mSlop;

    RecyclerView mRecyclerView;

    /**
     * When user drags a view to the edge, we start scrolling the LayoutManager as long as View
     * is partially out of bounds.
     */
    @SuppressWarnings("WeakerAccess") /* synthetic access */
    final Runnable mScrollRunnable = new Runnable() {
        @Override
        public void run() {
            if (mSelected != null && scrollIfNecessary()) {
                if (mSelected != null) { //it might be lost during scrolling
                    moveIfNecessary(mSelected);
                }
                mRecyclerView.removeCallbacks(mScrollRunnable);
                ViewCompat.postOnAnimation(mRecyclerView, this);
            }
        }
    };

    /**
     * Used for detecting fling swipe
     */
    VelocityTracker mVelocityTracker;

    //re-used list for selecting a swap target
    protected List<ViewHolder> mSwapTargets;

    //re used for for sorting swap targets
    protected List<Integer> mDistances;

    /**
     * If drag & drop is supported, we use child drawing order to bring them to front.
     */
    protected RecyclerView.ChildDrawingOrderCallback mChildDrawingOrderCallback = null;

    /**
     * This keeps a reference to the child dragged by the user. Even after user stops dragging,
     * until view reaches its final position (end of recover animation), we keep a reference so
     * that it can be drawn above other children.
     */
    @SuppressWarnings("WeakerAccess") /* synthetic access */
            View mOverdrawChild = null;

    /**
     * We cache the position of the overdraw child to avoid recalculating it each time child
     * position callback is called. This value is invalidated whenever a child is attached or
     * detached.
     */
    @SuppressWarnings("WeakerAccess") /* synthetic access */
            int mOverdrawChildPosition = -1;

    /**
     * Used to detect long press.
     */
    @SuppressWarnings("WeakerAccess") /* synthetic access */
            GestureDetectorCompat mGestureDetector;

    /**
     * Callback for when long press occurs.
     */
    protected ItemTouchHelperPlus.ItemTouchHelperGestureListener mItemTouchHelperGestureListener;


    /**
     * Temporary rect instance that is used when we need to lookup Item decorations.
     */
    protected Rect mTmpRect;

    /**
     * When user started to drag scroll. Reset when we don't scroll
     */
    protected long mDragScrollStartTimeInMs;

    protected static boolean hitTest(View child, float x, float y, float left, float top) {
        return x >= left
                && x <= left + child.getWidth()
                && y >= top
                && y <= top + child.getHeight();
    }

    /**
     * Attaches the ItemTouchHelper to the provided RecyclerView. If TouchHelper is already
     * attached to a RecyclerView, it will first detach from the previous one. You can call this
     * method with {@code null} to detach it from the current RecyclerView.
     *
     * @param recyclerView The RecyclerView instance to which you want to add this helper or
     *                     {@code null} if you want to remove ItemTouchHelper from the current
     *                     RecyclerView.
     */
    public void attachToRecyclerView(@Nullable RecyclerView recyclerView) {
        if (mRecyclerView == recyclerView) {
            return; // nothing to do
        }
        if (mRecyclerView != null) {
            destroyCallbacks();
        }
        mRecyclerView = recyclerView;
        if (recyclerView != null) {
            final Resources resources = recyclerView.getResources();
            mSwipeEscapeVelocity = resources
                    .getDimension(R.dimen.item_touch_helper_swipe_escape_velocity);
            mMaxSwipeVelocity = resources
                    .getDimension(R.dimen.item_touch_helper_swipe_escape_max_velocity);
            setupCallbacks();
        }
        mGestureDetector = new GestureDetectorCompat(mRecyclerView.getContext(),
                new ItemTouchHelperGestureListener());
    }

    /**
     * This class is the contract between ItemTouchHelper and your application. It lets you control
     * which touch behaviors are enabled per each ViewHolder and also receive callbacks when user
     * performs these actions.
     * <p>
     * To control which actions user can take on each view, you should override
     * {@link #getMovementFlags(RecyclerView, RecyclerView.ViewHolder)} and return appropriate set
     * of direction flags. ({@link #LEFT}, {@link #RIGHT}, {@link #START}, {@link #END},
     * {@link #UP}, {@link #DOWN}). You can use
     * {@link #makeMovementFlags(int, int)} to easily construct it. Alternatively, you can use
     * {@link ItemTouchHelperPlus.SimpleCallback}.
     * <p>
     * If user drags an item, ItemTouchHelper will call
     * {@link ItemTouchHelperPlus.Callback#onMove(RecyclerView, RecyclerView.ViewHolder, RecyclerView.ViewHolder)
     * onMove(recyclerView, dragged, target)}.
     * Upon receiving this callback, you should move the item from the old position
     * ({@code dragged.getAdapterPosition()}) to new position ({@code target.getAdapterPosition()})
     * in your adapter and also call {@link RecyclerView.Adapter#notifyItemMoved(int, int)}.
     * To control where a View can be dropped, you can override
     * {@link #canDropOver(RecyclerView, RecyclerView.ViewHolder, RecyclerView.ViewHolder)}. When a
     * dragging View overlaps multiple other views, Callback chooses the closest View with which
     * dragged View might have changed positions. Although this approach works for many use cases,
     * if you have a custom LayoutManager, you can override
     * {@link #chooseDropTarget(RecyclerView.ViewHolder, java.util.List, int, int)} to select a
     * custom drop target.
     * <p>
     * When a View is swiped, ItemTouchHelper animates it until it goes out of bounds, then calls
     * {@link #onSwiped(RecyclerView.ViewHolder, int)}. At this point, you should update your
     * adapter (e.g. remove the item) and call related Adapter#notify event.
     */
    @SuppressWarnings("UnusedParameters")

    protected void getSelectedDxDy(float[] outPosition) {
        if ((mSelectedFlags & (LEFT | RIGHT)) != 0) {
            outPosition[0] = mSelectedStartX + mDx - mSelected.itemView.getLeft();
        } else {
            outPosition[0] = mSelected.itemView.getTranslationX();
        }
        if ((mSelectedFlags & (UP | DOWN)) != 0) {
            outPosition[1] = mSelectedStartY + mDy - mSelected.itemView.getTop();
        } else {
            outPosition[1] = mSelected.itemView.getTranslationY();
        }
    }

    @Override
    public void onDrawOver(Canvas c, RecyclerView parent, RecyclerView.State state) {
        float dx = 0, dy = 0;
        if (mSelected != null) {
            getSelectedDxDy(mTmpPosition);
            dx = mTmpPosition[0];
            dy = mTmpPosition[1];
        }
        mCallback.onDrawOver(c, parent, mSelected,
                mRecoverAnimations, mActionState, dx, dy);
    }

    @Override
    public void onDraw(Canvas c, RecyclerView parent, RecyclerView.State state) {
        // we don't know if RV changed something so we should invalidate this index.
        mOverdrawChildPosition = -1;
        float dx = 0, dy = 0;
        if (mSelected != null) {
            getSelectedDxDy(mTmpPosition);
            dx = mTmpPosition[0];
            dy = mTmpPosition[1];
        }
        mCallback.onDraw(c, parent, mSelected,
                mRecoverAnimations, mActionState, dx, dy);
    }

    /**
     * Starts dragging the provided ViewHolder. By default, ItemTouchHelper starts a drag when a
     * View is long pressed. You can disable that behavior by overriding
     * {@link ItemTouchHelperPlus.Callback#isLongPressDragEnabled()}.
     * <p>
     * For this method to work:
     * <ul>
     * <li>The provided ViewHolder must be a child of the RecyclerView to which this
     * ItemTouchHelper
     * is attached.</li>
     * <li>{@link ItemTouchHelperPlus.Callback} must have dragging enabled.</li>
     * <li>There must be a previous touch event that was reported to the ItemTouchHelper
     * through RecyclerView's ItemTouchListener mechanism. As long as no other ItemTouchListener
     * grabs previous events, this should work as expected.</li>
     * </ul>
     * <p>
     * For example, if you would like to let your user to be able to drag an Item by touching one
     * of its descendants, you may implement it as follows:
     * <pre>
     *     viewHolder.dragButton.setOnTouchListener(new View.OnTouchListener() {
     *         public boolean onTouch(View v, MotionEvent event) {
     *             if (MotionEvent.getActionMasked(event) == MotionEvent.ACTION_DOWN) {
     *                 mItemTouchHelperPlus.startDrag(viewHolder);
     *             }
     *             return false;
     *         }
     *     });
     * </pre>
     * <p>
     *
     * @param viewHolder The ViewHolder to start dragging. It must be a direct child of
     *                   RecyclerView.
     * @see ItemTouchHelperPlus.Callback#isItemViewSwipeEnabled()
     */
    public void startDrag(@NonNull ViewHolder viewHolder) {
        if (!mCallback.hasDragFlag(mRecyclerView, viewHolder)) {
            Log.e(TAG, "Start drag has been called but dragging is not enabled");
            return;
        }
        if (viewHolder.itemView.getParent() != mRecyclerView) {
            Log.e(TAG, "Start drag has been called with a view holder which is not a child of "
                    + "the RecyclerView which is controlled by this ItemTouchHelperPlus.");
            return;
        }
        obtainVelocityTracker();
        mDx = mDy = 0f;
        select(viewHolder, ACTION_STATE_DRAG);
    }

    /**
     * Starts swiping the provided ViewHolder. By default, ItemTouchHelper starts swiping a View
     * when user swipes their finger (or mouse pointer) over the View. You can disable this
     * behavior
     * by overriding {@link ItemTouchHelperPlus.Callback}
     * <p>
     * For this method to work:
     * <ul>
     * <li>The provided ViewHolder must be a child of the RecyclerView to which this
     * ItemTouchHelper is attached.</li>
     * <li>{@link ItemTouchHelperPlus.Callback} must have swiping enabled.</li>
     * <li>There must be a previous touch event that was reported to the ItemTouchHelper
     * through RecyclerView's ItemTouchListener mechanism. As long as no other ItemTouchListener
     * grabs previous events, this should work as expected.</li>
     * </ul>
     * <p>
     * For example, if you would like to let your user to be able to swipe an Item by touching one
     * of its descendants, you may implement it as follows:
     * <pre>
     *     viewHolder.dragButton.setOnTouchListener(new View.OnTouchListener() {
     *         public boolean onTouch(View v, MotionEvent event) {
     *             if (MotionEvent.getActionMasked(event) == MotionEvent.ACTION_DOWN) {
     *                 mItemTouchHelperPlus.startSwipe(viewHolder);
     *             }
     *             return false;
     *         }
     *     });
     * </pre>
     *
     * @param viewHolder The ViewHolder to start swiping. It must be a direct child of
     *                   RecyclerView.
     */
    public void startSwipe(@NonNull ViewHolder viewHolder) {
        if (!mCallback.hasSwipeFlag(mRecyclerView, viewHolder)) {
            Log.e(TAG, "Start swipe has been called but swiping is not enabled");
            return;
        }
        if (viewHolder.itemView.getParent() != mRecyclerView) {
            Log.e(TAG, "Start swipe has been called with a view holder which is not a child of "
                    + "the RecyclerView controlled by this ItemTouchHelperPlus.");
            return;
        }
        obtainVelocityTracker();
        mDx = mDy = 0f;
        select(viewHolder, ACTION_STATE_SWIPE);
    }

    protected boolean enableClickDrag;
    protected Context mContext;
    protected Callback2 mCallback2;
    protected OnItemDragListener mItemDragListener;


    public void setItemDragListener(OnItemDragListener itemDragListener) {
        mItemDragListener = itemDragListener;
    }

    public void setEnableClickDrag(boolean enableClickDrag) {
        this.enableClickDrag = enableClickDrag;
    }

    public boolean isEnableClickDrag() {
        return enableClickDrag;
    }


    public Context getContext() {
        return mContext;
    }

    protected int getActionState() {
        return mActionState;
    }

    public float getSelectedStartX() {
        return mSelectedStartX;
    }

    public float getSelectedStartY() {
        return mSelectedStartY;
    }


    //region callback
    // 修改 ItemTouchHelperPlus.java 中的 Callback2 类

    public abstract static class Callback2 extends ItemTouchHelperPlus.Callback {
        protected Handler mHandler;
        protected ItemTouchHelperPlus mItemTouchHelper;
        protected long mLongTime = 1000;
        protected boolean mLongPressMode;
        protected int mSelectId;
        protected volatile long longPressTime = 0;
        protected boolean isLongPressCancel = false;
        protected int mDx = 2;
        protected int mDy = 2;
        protected boolean isDragging = false; // 添加拖拽状态标识

        public void setLongTime(long longTime) {
            mLongTime = longTime;
        }

        public void setItemTouchHelper(ItemTouchHelperPlus itemTouchHelper) {
            mItemTouchHelper = itemTouchHelper;
            mHandler = new Handler(mItemTouchHelper.getContext().getMainLooper());
        }

        public void setDragSize(int dx, int dy) {
            mDx = dx;
            mDy = dy;
        }

        OnItemDragListener getOnDragListener() {
            if (mItemTouchHelper == null) return null;
            return mItemTouchHelper.mItemDragListener;
        }

        public int getSelectId() {
            return mSelectId;
        }

        public boolean isLongPressMode() {
            return mLongPressMode;
        }

        @Override
        public void onChildDrawOver(Canvas c, RecyclerView recyclerView,
                                    RecyclerView.ViewHolder viewHolder,
                                    float dX, float dY, int actionState, boolean isCurrentlyActive) {
            super.onChildDrawOver(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
            if (isCurrentlyActive && actionState == ItemTouchHelperPlus.ACTION_STATE_DRAG) {
                // 检测到真正的拖拽移动，设置拖拽状态并取消长按
                if ((Math.abs(dX) > mDx || Math.abs(dY) > mDy) && !isDragging) {
                    isDragging = true;
                    if (!isLongPressMode() && !isLongPressCancel) {
                        isLongPressCancel = true;
                        endLongPressMode();
                    }
                }
            }
        }

        @Override
        public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
            super.onSelectedChanged(viewHolder, actionState);
            if (viewHolder != null && actionState == ItemTouchHelperPlus.ACTION_STATE_DRAG) {
                if (getOnDragListener() != null) {
                    getOnDragListener().onDragStart();
                }
                isLongPressCancel = false;
                isDragging = false; // 重置拖拽状态
                mSelectId = viewHolder.getAdapterPosition();
                longPressTime = System.currentTimeMillis();
                mHandler.removeCallbacks(enterLongPress);
                if (mItemTouchHelper.isEnableClickDrag()) {
                    mHandler.postDelayed(enterLongPress, mLongTime);
                }
            } else if (actionState == ItemTouchHelperPlus.ACTION_STATE_IDLE) {
                endLongPressMode();
                isDragging = false; // 重置拖拽状态
                if (getOnDragListener() != null) {
                    getOnDragListener().onDragEnd();
                }
            } else if (actionState == ItemTouchHelperPlus.ACTION_STATE_SWIPE) {
                endLongPressMode();
                isDragging = false; // 重置拖拽状态
            }
        }

        protected final Runnable enterLongPress = new Runnable() {
            @Override
            public void run() {
                // 只有在非拖拽状态下才触发长按
                if (!isDragging && System.currentTimeMillis() - longPressTime >= mLongTime) {
                    mLongPressMode = true;
                    if (getOnDragListener() != null && mSelectId >= 0) {
                        getOnDragListener().onDragLongPress(mSelectId);
                    }
                }
            }
        };

        public void cancelLongPress() {
            if (!isLongPressMode() && !isLongPressCancel) {
                isLongPressCancel = true;
                endLongPressMode();
            }
        }

        public void endLongPressMode() {
            longPressTime = System.currentTimeMillis();
            mHandler.removeCallbacks(enterLongPress);
            if (mLongPressMode) {
                if (getOnDragListener() != null) {
                    getOnDragListener().onDragLongPressEnd();
                }
            }
            mLongPressMode = false;
        }
    }

    //    //endregion
//
    protected class ItemTouchHelperGestureListener extends GestureDetector.SimpleOnGestureListener {

        protected boolean mShouldReactToLongPress = true;

        ItemTouchHelperGestureListener() {
        }

        void doNotReactToLongPress() {
            mShouldReactToLongPress = false;
        }

        @Override
        public void onShowPress(MotionEvent e) {
            if (isEnableClickDrag()) {
                startDrag(e);
            }
        }

        @Override
        public boolean onDown(MotionEvent e) {
            return true;
        }

        @Override
        public void onLongPress(MotionEvent e) {
            if (!isEnableClickDrag()) {
                startDrag(e);
            }
        }

        protected void startDrag(MotionEvent e) {
            View child = findChildView(e);
            if (child != null) {
                RecyclerView.ViewHolder vh = mRecyclerView.getChildViewHolder(child);
                if (vh != null) {
                    if (!mCallback.hasDragFlag(mRecyclerView, vh)) {
                        return;
                    }
                    int pointerId = e.getPointerId(0);
                    // Long press is deferred.
                    // Check w/ active pointer id to avoid selecting after motion
                    // event is canceled.
                    if (pointerId == mActivePointerId) {
                        final int index = e.findPointerIndex(mActivePointerId);
                        final float x = e.getX(index);
                        final float y = e.getY(index);
                        mInitialTouchX = x;
                        mInitialTouchY = y;
                        mDx = mDy = 0f;
                        if (mCallback.isLongPressDragEnabled()) {
                            select(vh, ACTION_STATE_DRAG);
                        }
                    }
                }
            }
        }
    }

    public abstract static class Callback extends ItemTouchHelperCallbackImpl {
    }

    // ==== hooks implemented by subclasses in the chain (split artifact) ====
    abstract void setupCallbacks();
    abstract void destroyCallbacks();
    abstract boolean scrollIfNecessary();
    abstract void moveIfNecessary(ViewHolder viewHolder);
    abstract void select(ViewHolder selected, int actionState);
    abstract View findChildView(MotionEvent event);
    abstract void obtainVelocityTracker();
    abstract void releaseVelocityTracker();

}
