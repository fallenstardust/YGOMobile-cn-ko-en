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
 * ItemTouchHelperPlus 拆分后的具体叶子类（对外入口，保持原类名）。
 *
 * <p>原单一文件共 2708 行，为符合单类规模规范拆为继承链：
 * {@link ItemTouchHelperPlusBase} ← {@link ItemTouchHelperPlusMiddle} ← 本类。
 * 本类持有公开构造器、选中/拖拽切换（select）、边界滚动与交换移动等叶子逻辑，
 * 以及 {@code SimpleCallback}、{@code ViewDropHandler} 等嵌套类型。
 * 所有外部调用方式（构造、attachToRecyclerView、静态常量引用等）与拆分前完全一致。</p>
 */
@SuppressWarnings("RestrictedApi")
public class ItemTouchHelperPlus extends ItemTouchHelperPlusMiddle {

    /**
     * Creates an ItemTouchHelper that will work with the given Callback.
     * <p>
     * You can attach ItemTouchHelper to a RecyclerView via
     * {@link #attachToRecyclerView(RecyclerView)}. Upon attaching, it will add an item decoration,
     * an onItemTouchListener and a Child attach / detach listener to the RecyclerView.
     *
     * @param callback The Callback which controls the behavior of this touch helper.
     */
    public ItemTouchHelperPlus(@NonNull ItemTouchHelperPlus.Callback callback) {
        mCallback = callback;
    }

    /**
     * Starts dragging or swiping the given View. Call with null if you want to clear it.
     *
     * @param selected    The ViewHolder to drag or swipe. Can be null if you want to cancel the
     *                    current action, but may not be null if actionState is ACTION_STATE_DRAG.
     * @param actionState The type of action
     */
    @SuppressWarnings("WeakerAccess") /* synthetic access */
    void select(@Nullable ViewHolder selected, int actionState) {
        if (selected == mSelected && actionState == mActionState) {
            return;
        }
        mDragScrollStartTimeInMs = Long.MIN_VALUE;
        final int prevActionState = mActionState;
        // prevent duplicate animations
        endRecoverAnimation(selected, true);
        mActionState = actionState;
        if (actionState == ACTION_STATE_DRAG) {
            if (selected == null) {
                throw new IllegalArgumentException("Must pass a ViewHolder when dragging");
            }

            // we remove after animation is complete. this means we only elevate the last drag
            // child but that should perform good enough as it is very hard to start dragging a
            // new child before the previous one settles.
            mOverdrawChild = selected.itemView;
            addChildDrawingOrderCallback();
        }
        int actionStateMask = (1 << (DIRECTION_FLAG_COUNT + DIRECTION_FLAG_COUNT * actionState))
                - 1;
        boolean preventLayout = false;

        if (mSelected != null) {
            final ViewHolder prevSelected = mSelected;
            if (prevSelected.itemView.getParent() != null) {
                final int swipeDir = prevActionState == ACTION_STATE_DRAG ? 0
                        : swipeIfNecessary(prevSelected);
                releaseVelocityTracker();
                // find where we should animate to
                final float targetTranslateX, targetTranslateY;
                int animationType;
                switch (swipeDir) {
                    case LEFT:
                    case RIGHT:
                    case START:
                    case END:
                        targetTranslateY = 0;
                        targetTranslateX = Math.signum(mDx) * mRecyclerView.getWidth();
                        break;
                    case UP:
                    case DOWN:
                        targetTranslateX = 0;
                        targetTranslateY = Math.signum(mDy) * mRecyclerView.getHeight();
                        break;
                    default:
                        targetTranslateX = 0;
                        targetTranslateY = 0;
                }
                if (prevActionState == ACTION_STATE_DRAG) {
                    animationType = ANIMATION_TYPE_DRAG;
                } else if (swipeDir > 0) {
                    animationType = ANIMATION_TYPE_SWIPE_SUCCESS;
                } else {
                    animationType = ANIMATION_TYPE_SWIPE_CANCEL;
                }
                getSelectedDxDy(mTmpPosition);
                final float currentTranslateX = mTmpPosition[0];
                final float currentTranslateY = mTmpPosition[1];
                final RecoverAnimation rv = new RecoverAnimation(prevSelected, animationType,
                        prevActionState, currentTranslateX, currentTranslateY,
                        targetTranslateX, targetTranslateY) {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        super.onAnimationEnd(animation);
                        if (this.mOverridden) {
                            return;
                        }
                        if (swipeDir <= 0) {
                            // this is a drag or failed swipe. recover immediately
                            mCallback.clearView(mRecyclerView, prevSelected);
                            // full cleanup will happen on onDrawOver
                        } else {
                            // wait until remove animation is complete.
                            mPendingCleanup.add(prevSelected.itemView);
                            mIsPendingCleanup = true;
                            if (swipeDir > 0) {
                                // Animation might be ended by other animators during a layout.
                                // We defer callback to avoid editing adapter during a layout.
                                postDispatchSwipe(this, swipeDir);
                            }
                        }
                        // removed from the list after it is drawn for the last time
                        if (mOverdrawChild == prevSelected.itemView) {
                            removeChildDrawingOrderCallbackIfNecessary(prevSelected.itemView);
                        }
                    }
                };
                final long duration = mCallback.getAnimationDuration(mRecyclerView, animationType,
                        targetTranslateX - currentTranslateX, targetTranslateY - currentTranslateY);
                rv.setDuration(duration);
                mRecoverAnimations.add(rv);
                rv.start();
                preventLayout = true;
            } else {
                removeChildDrawingOrderCallbackIfNecessary(prevSelected.itemView);
                mCallback.clearView(mRecyclerView, prevSelected);
            }
            mSelected = null;
        }
        if (selected != null) {
            mSelectedFlags =
                    (mCallback.getAbsoluteMovementFlags(mRecyclerView, selected) & actionStateMask)
                            >> (mActionState * DIRECTION_FLAG_COUNT);
            mSelectedStartX = selected.itemView.getLeft();
            mSelectedStartY = selected.itemView.getTop();
            mSelected = selected;

            if (actionState == ACTION_STATE_DRAG) {
                mSelected.itemView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            }
        }
        final ViewParent rvParent = mRecyclerView.getParent();
        if (rvParent != null) {
            rvParent.requestDisallowInterceptTouchEvent(mSelected != null);
        }
        if (!preventLayout) {
            mRecyclerView.getLayoutManager().requestSimpleAnimationsInNextLayout();
        }
        mCallback.onSelectedChanged(mSelected, mActionState);
        mRecyclerView.invalidate();

        if (selected != this.mSelected || actionState != mActionState) {
            mActionState = actionState;
            if (selected != null) {
                mSelectedStartX = (float) selected.itemView.getLeft();
                mSelectedStartY = (float) selected.itemView.getTop();
                mSelected = selected;
            }
        }
    }

    /**
     * If user drags the view to the edge, trigger a scroll if necessary.
     */
    @SuppressWarnings("WeakerAccess") /* synthetic access */
    boolean scrollIfNecessary() {
        if (mSelected == null) {
            mDragScrollStartTimeInMs = Long.MIN_VALUE;
            return false;
        }
        final long now = System.currentTimeMillis();
        final long scrollDuration = mDragScrollStartTimeInMs
                == Long.MIN_VALUE ? 0 : now - mDragScrollStartTimeInMs;
        RecyclerView.LayoutManager lm = mRecyclerView.getLayoutManager();
        if (mTmpRect == null) {
            mTmpRect = new Rect();
        }
        int scrollX = 0;
        int scrollY = 0;
        lm.calculateItemDecorationsForChild(mSelected.itemView, mTmpRect);
        if (lm.canScrollHorizontally()) {
            int curX = (int) (mSelectedStartX + mDx);
            final int leftDiff = curX - mTmpRect.left - mRecyclerView.getPaddingLeft();
            if (mDx < 0 && leftDiff < 0) {
                scrollX = leftDiff;
            } else if (mDx > 0) {
                final int rightDiff =
                        curX + mSelected.itemView.getWidth() + mTmpRect.right
                                - (mRecyclerView.getWidth() - mRecyclerView.getPaddingRight());
                if (rightDiff > 0) {
                    scrollX = rightDiff;
                }
            }
        }
        if (lm.canScrollVertically()) {
            int curY = (int) (mSelectedStartY + mDy);
            final int topDiff = curY - mTmpRect.top - mRecyclerView.getPaddingTop();
            if (mDy < 0 && topDiff < 0) {
                scrollY = topDiff;
            } else if (mDy > 0) {
                final int bottomDiff = curY + mSelected.itemView.getHeight() + mTmpRect.bottom
                        - (mRecyclerView.getHeight() - mRecyclerView.getPaddingBottom());
                if (bottomDiff > 0) {
                    scrollY = bottomDiff;
                }
            }
        }
        if (scrollX != 0) {
            scrollX = mCallback.interpolateOutOfBoundsScroll(mRecyclerView,
                    mSelected.itemView.getWidth(), scrollX,
                    mRecyclerView.getWidth(), scrollDuration);
        }
        if (scrollY != 0) {
            scrollY = mCallback.interpolateOutOfBoundsScroll(mRecyclerView,
                    mSelected.itemView.getHeight(), scrollY,
                    mRecyclerView.getHeight(), scrollDuration);
        }
        if (scrollX != 0 || scrollY != 0) {
            if (mDragScrollStartTimeInMs == Long.MIN_VALUE) {
                mDragScrollStartTimeInMs = now;
            }
            mRecyclerView.scrollBy(scrollX, scrollY);
            return true;
        }
        mDragScrollStartTimeInMs = Long.MIN_VALUE;
        return false;
    }

    protected List<ViewHolder> findSwapTargets(ViewHolder viewHolder) {
        if (mSwapTargets == null) {
            mSwapTargets = new ArrayList<>();
            mDistances = new ArrayList<>();
        } else {
            mSwapTargets.clear();
            mDistances.clear();
        }
        final int margin = mCallback.getBoundingBoxMargin();
        final int left = Math.round(mSelectedStartX + mDx) - margin;
        final int top = Math.round(mSelectedStartY + mDy) - margin;
        final int right = left + viewHolder.itemView.getWidth() + 2 * margin;
        final int bottom = top + viewHolder.itemView.getHeight() + 2 * margin;
        final int centerX = (left + right) / 2;
        final int centerY = (top + bottom) / 2;
        final RecyclerView.LayoutManager lm = mRecyclerView.getLayoutManager();
        final int childCount = lm.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View other = lm.getChildAt(i);
            if (other == viewHolder.itemView) {
                continue; //myself!
            }
            if (other.getBottom() < top || other.getTop() > bottom
                    || other.getRight() < left || other.getLeft() > right) {
                continue;
            }
            final ViewHolder otherVh = mRecyclerView.getChildViewHolder(other);
            if (mCallback.canDropOver(mRecyclerView, mSelected, otherVh)) {
                // find the index to add
                final int dx = Math.abs(centerX - (other.getLeft() + other.getRight()) / 2);
                final int dy = Math.abs(centerY - (other.getTop() + other.getBottom()) / 2);
                final int dist = dx * dx + dy * dy;

                int pos = 0;
                final int cnt = mSwapTargets.size();
                for (int j = 0; j < cnt; j++) {
                    if (dist > mDistances.get(j)) {
                        pos++;
                    } else {
                        break;
                    }
                }
                mSwapTargets.add(pos, otherVh);
                mDistances.add(pos, dist);
            }
        }
        return mSwapTargets;
    }

    /**
     * Checks if we should swap w/ another view holder.
     */
    @SuppressWarnings("WeakerAccess") /* synthetic access */
    void moveIfNecessary(ViewHolder viewHolder) {
        if (mRecyclerView.isLayoutRequested()) {
            return;
        }
        if (mActionState != ACTION_STATE_DRAG) {
            return;
        }

        final float threshold = mCallback.getMoveThreshold(viewHolder);
        final int x = (int) (mSelectedStartX + mDx);
        final int y = (int) (mSelectedStartY + mDy);
        if (Math.abs(y - viewHolder.itemView.getTop()) < viewHolder.itemView.getHeight() * threshold
                && Math.abs(x - viewHolder.itemView.getLeft())
                < viewHolder.itemView.getWidth() * threshold) {
            return;
        }
        List<ViewHolder> swapTargets = findSwapTargets(viewHolder);
        if (swapTargets.size() == 0) {
            return;
        }
        // may swap.
        ViewHolder target = mCallback.chooseDropTarget(viewHolder, swapTargets, x, y);
        if (target == null) {
            mSwapTargets.clear();
            mDistances.clear();
            return;
        }
        final int toPosition = target.getAdapterPosition();
        final int fromPosition = viewHolder.getAdapterPosition();
        if (mCallback.onMove(mRecyclerView, viewHolder, target)) {
            // keep target visible
            mCallback.onMoved(mRecyclerView, viewHolder, fromPosition,
                    target, toPosition, x, y);
        }


        if (mRecyclerView.isLayoutRequested()) {
            return;
        }
        if (getActionState() != ACTION_STATE_DRAG) {
            return;
        }


//        if (Math.abs(y - viewHolder.itemView.getTop()) < viewHolder.itemView.getHeight() * threshold
//                && Math.abs(x - viewHolder.itemView.getLeft())
//                < viewHolder.itemView.getWidth() * threshold) {
//            return;
//        }
        mCallback2.cancelLongPress();
    }

    /**
     * An interface which can be implemented by LayoutManager for better integration with
     * {@link ItemTouchHelper}.
     */
    public interface ViewDropHandler {

        /**
         * Called by the {@link ItemTouchHelper} after a View is dropped over another View.
         * <p>
         * A LayoutManager should implement this interface to get ready for the upcoming move
         * operation.
         * <p>
         * For example, LinearLayoutManager sets up a "scrollToPositionWithOffset" calls so that
         * the View under drag will be used as an anchor View while calculating the next layout,
         * making layout stay consistent.
         *
         * @param view   The View which is being dragged. It is very likely that user is still
         *               dragging this View so there might be other calls to
         *               {@code prepareForDrop()} after this one.
         * @param target The target view which is being dropped on.
         * @param x      The <code>left</code> offset of the View that is being dragged. This value
         *               includes the movement caused by the user.
         * @param y      The <code>top</code> offset of the View that is being dragged. This value
         *               includes the movement caused by the user.
         */
        void prepareForDrop(@NonNull View view, @NonNull View target, int x, int y);
    }

    /**
     * A simple wrapper to the default Callback which you can construct with drag and swipe
     * directions and this class will handle the flag callbacks. You should still override onMove
     * or
     * onSwiped depending on your use case.
     *
     * <pre>
     * ItemTouchHelper mIth = new ItemTouchHelper(
     *     new ItemTouchHelperPlus.SimpleCallback(ItemTouchHelperPlus.UP | ItemTouchHelperPlus.DOWN,
     *         ItemTouchHelperPlus.LEFT) {
     *         public abstract boolean onMove(RecyclerView recyclerView,
     *             ViewHolder viewHolder, ViewHolder target) {
     *             final int fromPos = viewHolder.getAdapterPosition();
     *             final int toPos = target.getAdapterPosition();
     *             // move item in `fromPos` to `toPos` in adapter.
     *             return true;// true if moved, false otherwise
     *         }
     *         public void onSwiped(ViewHolder viewHolder, int direction) {
     *             // remove from adapter
     *         }
     * });
     * </pre>
     */
    public abstract static class SimpleCallback extends ItemTouchHelperPlus.Callback {

        protected int mDefaultSwipeDirs;

        protected int mDefaultDragDirs;

        /**
         * Creates a Callback for the given drag and swipe allowance. These values serve as
         * defaults
         * and if you want to customize behavior per ViewHolder, you can override
         * {@link #getSwipeDirs(RecyclerView, RecyclerView.ViewHolder)}
         * and / or {@link #getDragDirs(RecyclerView, RecyclerView.ViewHolder)}.
         *
         * @param dragDirs  Binary OR of direction flags in which the Views can be dragged. Must be
         *                  composed of {@link #LEFT}, {@link #RIGHT}, {@link #START}, {@link
         *                  #END},
         *                  {@link #UP} and {@link #DOWN}.
         * @param swipeDirs Binary OR of direction flags in which the Views can be swiped. Must be
         *                  composed of {@link #LEFT}, {@link #RIGHT}, {@link #START}, {@link
         *                  #END},
         *                  {@link #UP} and {@link #DOWN}.
         */
        public SimpleCallback(int dragDirs, int swipeDirs) {
            mDefaultSwipeDirs = swipeDirs;
            mDefaultDragDirs = dragDirs;
        }

        /**
         * Updates the default swipe directions. For example, you can use this method to toggle
         * certain directions depending on your use case.
         *
         * @param defaultSwipeDirs Binary OR of directions in which the ViewHolders can be swiped.
         */
        @SuppressWarnings({"WeakerAccess", "unused"})
        public void setDefaultSwipeDirs(@SuppressWarnings("unused") int defaultSwipeDirs) {
            mDefaultSwipeDirs = defaultSwipeDirs;
        }

        /**
         * Updates the default drag directions. For example, you can use this method to toggle
         * certain directions depending on your use case.
         *
         * @param defaultDragDirs Binary OR of directions in which the ViewHolders can be dragged.
         */
        @SuppressWarnings({"WeakerAccess", "unused"})
        public void setDefaultDragDirs(@SuppressWarnings("unused") int defaultDragDirs) {
            mDefaultDragDirs = defaultDragDirs;
        }

        /**
         * Returns the swipe directions for the provided ViewHolder.
         * Default implementation returns the swipe directions that was set via constructor or
         * {@link #setDefaultSwipeDirs(int)}.
         *
         * @param recyclerView The RecyclerView to which the ItemTouchHelper is attached to.
         * @param viewHolder   The ViewHolder for which the swipe direction is queried.
         * @return A binary OR of direction flags.
         */
        @SuppressWarnings("WeakerAccess")
        public int getSwipeDirs(@SuppressWarnings("unused") @NonNull RecyclerView recyclerView,
                                @NonNull @SuppressWarnings("unused") ViewHolder viewHolder) {
            return mDefaultSwipeDirs;
        }

        /**
         * Returns the drag directions for the provided ViewHolder.
         * Default implementation returns the drag directions that was set via constructor or
         * {@link #setDefaultDragDirs(int)}.
         *
         * @param recyclerView The RecyclerView to which the ItemTouchHelper is attached to.
         * @param viewHolder   The ViewHolder for which the swipe direction is queried.
         * @return A binary OR of direction flags.
         */
        @SuppressWarnings("WeakerAccess")
        public int getDragDirs(@SuppressWarnings("unused") @NonNull RecyclerView recyclerView,
                               @SuppressWarnings("unused") @NonNull ViewHolder viewHolder) {
            return mDefaultDragDirs;
        }

        @Override
        public int getMovementFlags(@NonNull RecyclerView recyclerView,
                                    @NonNull ViewHolder viewHolder) {
            return makeMovementFlags(getDragDirs(recyclerView, viewHolder),
                    getSwipeDirs(recyclerView, viewHolder));
        }
    }

//    private class ItemTouchHelperGestureListener extends GestureDetector.SimpleOnGestureListener {
//
//        /**
//         * Whether to execute code in response to the the invoking of
//         * {@link ItemTouchHelperPlus.ItemTouchHelperGestureListener#onLongPress(MotionEvent)}.
//         *
//         * It is necessary to control this here because
//         * {@link GestureDetector.SimpleOnGestureListener} can only be set on a
//         * {@link GestureDetector} in a GestureDetector's constructor, a GestureDetector will call
//         * onLongPress if an {@link MotionEvent#ACTION_DOWN} event is not followed by another event
//         * that would cancel it (like {@link MotionEvent#ACTION_UP} or
//         * {@link MotionEvent#ACTION_CANCEL}), the long press responding to the long press event
//         * needs to be cancellable to prevent unexpected behavior.
//         *
//         * @see #doNotReactToLongPress()
//         */
//        private boolean mShouldReactToLongPress = true;
//
//        ItemTouchHelperGestureListener() {
//        }
//
//        /**
//         * Call to prevent executing code in response to
//         * {@link ItemTouchHelperPlus.ItemTouchHelperGestureListener#onLongPress(MotionEvent)} being called.
//         */
//        void doNotReactToLongPress() {
//            mShouldReactToLongPress = false;
//        }
//
//        @Override
//        public boolean onDown(MotionEvent e) {
//            return true;
//        }
//
//        @Override
//        public void onLongPress(MotionEvent e) {
//            if (!mShouldReactToLongPress) {
//                return;
//            }
//            View child = findChildView(e);
//            if (child != null) {
//                ViewHolder vh = mRecyclerView.getChildViewHolder(child);
//                if (vh != null) {
//                    if (!mCallback.hasDragFlag(mRecyclerView, vh)) {
//                        return;
//                    }
//                    int pointerId = e.getPointerId(0);
//                    // Long press is deferred.
//                    // Check w/ active pointer id to avoid selecting after motion
//                    // event is canceled.
//                    if (pointerId == mActivePointerId) {
//                        final int index = e.findPointerIndex(mActivePointerId);
//                        final float x = e.getX(index);
//                        final float y = e.getY(index);
//                        mInitialTouchX = x;
//                        mInitialTouchY = y;
//                        mDx = mDy = 0f;
//                        if (DEBUG) {
//                            Log.d(TAG,
//                                    "onlong press: x:" + mInitialTouchX + ",y:" + mInitialTouchY);
//                        }
//                        if (mCallback.isLongPressDragEnabled()) {
//                            select(vh, ACTION_STATE_DRAG);
//                        }
//                    }
//                }
//            }
//        }
//    }


    public ItemTouchHelperPlus(Context context, ItemTouchHelperPlus.Callback2 callback) {
        this(callback);
        mContext = context;
        mCallback2 = callback;
        mCallback2.setItemTouchHelper(this);
    }

}
