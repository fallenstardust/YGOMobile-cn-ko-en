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

import static androidx.recyclerview.widget.ItemTouchHelperPlusBase.*;

/**
 * {@code ItemTouchHelperPlus.Callback} 拆分的顶层实现类（下半部分）。
 *
 * <p>承接 {@link ItemTouchHelperCallbackBase}，包含拖拽/滑动过程中的交互回调：
 * canDropOver、onMove、onSwiped、onDraw/onDrawOver、滑动阈值计算与动画时长等。
 * 拆分仅为控制单类规模，方法实现与原文件逐行一致。</p>
 */
@SuppressWarnings("RestrictedApi")
public abstract class ItemTouchHelperCallbackImpl extends ItemTouchHelperCallbackBase {
        /**
         * Return true if the current ViewHolder can be dropped over the the target ViewHolder.
         * <p>
         * This method is used when selecting drop target for the dragged View. After Views are
         * eliminated either via bounds check or via this method, resulting set of views will be
         * passed to {@link #chooseDropTarget(RecyclerView.ViewHolder, java.util.List, int, int)}.
         * <p>
         * Default implementation returns true.
         *
         * @param recyclerView The RecyclerView to which ItemTouchHelper is attached to.
         * @param current      The ViewHolder that user is dragging.
         * @param target       The ViewHolder which is below the dragged ViewHolder.
         * @return True if the dragged ViewHolder can be replaced with the target ViewHolder, false
         * otherwise.
         */
        @SuppressWarnings("WeakerAccess")
        public boolean canDropOver(@NonNull RecyclerView recyclerView, @NonNull ViewHolder current,
                                   @NonNull ViewHolder target) {
            return true;
        }

        /**
         * Called when ItemTouchHelper wants to move the dragged item from its old position to
         * the new position.
         * <p>
         * If this method returns true, ItemTouchHelper assumes {@code viewHolder} has been moved
         * to the adapter position of {@code target} ViewHolder
         * ({@link RecyclerView.ViewHolder#getAdapterPosition()
         * ViewHolder#getAdapterPosition()}).
         * <p>
         * If you don't support drag & drop, this method will never be called.
         *
         * @param recyclerView The RecyclerView to which ItemTouchHelper is attached to.
         * @param viewHolder   The ViewHolder which is being dragged by the user.
         * @param target       The ViewHolder over which the currently active item is being
         *                     dragged.
         * @return True if the {@code viewHolder} has been moved to the adapter position of
         * {@code target}.
         * @see #onMoved(RecyclerView, RecyclerView.ViewHolder, int, RecyclerView.ViewHolder, int, int, int)
         */
        public abstract boolean onMove(@NonNull RecyclerView recyclerView,
                                       @NonNull ViewHolder viewHolder, @NonNull ViewHolder target);

        /**
         * Returns whether ItemTouchHelper should start a drag and drop operation if an item is
         * long pressed.
         * <p>
         * Default value returns true but you may want to disable this if you want to start
         * dragging on a custom view touch using {@link #startDrag(RecyclerView.ViewHolder)}.
         *
         * @return True if ItemTouchHelper should start dragging an item when it is long pressed,
         * false otherwise. Default value is <code>true</code>.
         * @see #startDrag(RecyclerView.ViewHolder)
         */
        public boolean isLongPressDragEnabled() {
            return true;
        }

        /**
         * Returns whether ItemTouchHelper should start a swipe operation if a pointer is swiped
         * over the View.
         * <p>
         * Default value returns true but you may want to disable this if you want to start
         * swiping on a custom view touch using {@link #startSwipe(RecyclerView.ViewHolder)}.
         *
         * @return True if ItemTouchHelper should start swiping an item when user swipes a pointer
         * over the View, false otherwise. Default value is <code>true</code>.
         * @see #startSwipe(RecyclerView.ViewHolder)
         */
        public boolean isItemViewSwipeEnabled() {
            return true;
        }

        /**
         * When finding views under a dragged view, by default, ItemTouchHelper searches for views
         * that overlap with the dragged View. By overriding this method, you can extend or shrink
         * the search box.
         *
         * @return The extra margin to be added to the hit box of the dragged View.
         */
        @SuppressWarnings("WeakerAccess")
        public int getBoundingBoxMargin() {
            return 0;
        }

        /**
         * Returns the fraction that the user should move the View to be considered as swiped.
         * The fraction is calculated with respect to RecyclerView's bounds.
         * <p>
         * Default value is .5f, which means, to swipe a View, user must move the View at least
         * half of RecyclerView's width or height, depending on the swipe direction.
         *
         * @param viewHolder The ViewHolder that is being dragged.
         * @return A float value that denotes the fraction of the View size. Default value
         * is .5f .
         */
        @SuppressWarnings("WeakerAccess")
        public float getSwipeThreshold(@NonNull ViewHolder viewHolder) {
            return .5f;
        }

        /**
         * Returns the fraction that the user should move the View to be considered as it is
         * dragged. After a view is moved this amount, ItemTouchHelper starts checking for Views
         * below it for a possible drop.
         *
         * @param viewHolder The ViewHolder that is being dragged.
         * @return A float value that denotes the fraction of the View size. Default value is
         * .5f .
         */
        @SuppressWarnings("WeakerAccess")
        public float getMoveThreshold(@NonNull ViewHolder viewHolder) {
            return .5f;
        }

        /**
         * Defines the minimum velocity which will be considered as a swipe action by the user.
         * <p>
         * You can increase this value to make it harder to swipe or decrease it to make it easier.
         * Keep in mind that ItemTouchHelper also checks the perpendicular velocity and makes sure
         * current direction velocity is larger then the perpendicular one. Otherwise, user's
         * movement is ambiguous. You can change the threshold by overriding
         * {@link #getSwipeVelocityThreshold(float)}.
         * <p>
         * The velocity is calculated in pixels per second.
         * <p>
         * The default framework value is passed as a parameter so that you can modify it with a
         * multiplier.
         *
         * @param defaultValue The default value (in pixels per second) used by the
         *                     ItemTouchHelperPlus.
         * @return The minimum swipe velocity. The default implementation returns the
         * <code>defaultValue</code> parameter.
         * @see #getSwipeVelocityThreshold(float)
         * @see #getSwipeThreshold(RecyclerView.ViewHolder)
         */
        @SuppressWarnings("WeakerAccess")
        public float getSwipeEscapeVelocity(float defaultValue) {
            return defaultValue;
        }

        /**
         * Defines the maximum velocity ItemTouchHelper will ever calculate for pointer movements.
         * <p>
         * To consider a movement as swipe, ItemTouchHelper requires it to be larger than the
         * perpendicular movement. If both directions reach to the max threshold, none of them will
         * be considered as a swipe because it is usually an indication that user rather tried to
         * scroll then swipe.
         * <p>
         * The velocity is calculated in pixels per second.
         * <p>
         * You can customize this behavior by changing this method. If you increase the value, it
         * will be easier for the user to swipe diagonally and if you decrease the value, user will
         * need to make a rather straight finger movement to trigger a swipe.
         *
         * @param defaultValue The default value(in pixels per second) used by the ItemTouchHelperPlus.
         * @return The velocity cap for pointer movements. The default implementation returns the
         * <code>defaultValue</code> parameter.
         * @see #getSwipeEscapeVelocity(float)
         */
        @SuppressWarnings("WeakerAccess")
        public float getSwipeVelocityThreshold(float defaultValue) {
            return defaultValue;
        }

        /**
         * Called by ItemTouchHelper to select a drop target from the list of ViewHolders that
         * are under the dragged View.
         * <p>
         * Default implementation filters the View with which dragged item have changed position
         * in the drag direction. For instance, if the view is dragged UP, it compares the
         * <code>view.getTop()</code> of the two views before and after drag started. If that value
         * is different, the target view passes the filter.
         * <p>
         * Among these Views which pass the test, the one closest to the dragged view is chosen.
         * <p>
         * This method is called on the main thread every time user moves the View. If you want to
         * override it, make sure it does not do any expensive operations.
         *
         * @param selected    The ViewHolder being dragged by the user.
         * @param dropTargets The list of ViewHolder that are under the dragged View and
         *                    candidate as a drop.
         * @param curX        The updated left value of the dragged View after drag translations
         *                    are applied. This value does not include margins added by
         *                    {@link RecyclerView.ItemDecoration}s.
         * @param curY        The updated top value of the dragged View after drag translations
         *                    are applied. This value does not include margins added by
         *                    {@link RecyclerView.ItemDecoration}s.
         * @return A ViewHolder to whose position the dragged ViewHolder should be
         * moved to.
         */
        @SuppressWarnings("WeakerAccess")
        public ViewHolder chooseDropTarget(@NonNull ViewHolder selected,
                                           @NonNull List<ViewHolder> dropTargets, int curX, int curY) {
            int right = curX + selected.itemView.getWidth();
            int bottom = curY + selected.itemView.getHeight();
            ViewHolder winner = null;
            int winnerScore = -1;
            final int dx = curX - selected.itemView.getLeft();
            final int dy = curY - selected.itemView.getTop();
            final int targetsSize = dropTargets.size();
            for (int i = 0; i < targetsSize; i++) {
                final ViewHolder target = dropTargets.get(i);
                if (dx > 0) {
                    int diff = target.itemView.getRight() - right;
                    if (diff < 0 && target.itemView.getRight() > selected.itemView.getRight()) {
                        final int score = Math.abs(diff);
                        if (score > winnerScore) {
                            winnerScore = score;
                            winner = target;
                        }
                    }
                }
                if (dx < 0) {
                    int diff = target.itemView.getLeft() - curX;
                    if (diff > 0 && target.itemView.getLeft() < selected.itemView.getLeft()) {
                        final int score = Math.abs(diff);
                        if (score > winnerScore) {
                            winnerScore = score;
                            winner = target;
                        }
                    }
                }
                if (dy < 0) {
                    int diff = target.itemView.getTop() - curY;
                    if (diff > 0 && target.itemView.getTop() < selected.itemView.getTop()) {
                        final int score = Math.abs(diff);
                        if (score > winnerScore) {
                            winnerScore = score;
                            winner = target;
                        }
                    }
                }

                if (dy > 0) {
                    int diff = target.itemView.getBottom() - bottom;
                    if (diff < 0 && target.itemView.getBottom() > selected.itemView.getBottom()) {
                        final int score = Math.abs(diff);
                        if (score > winnerScore) {
                            winnerScore = score;
                            winner = target;
                        }
                    }
                }
            }
            return winner;
        }

        /**
         * Called when a ViewHolder is swiped by the user.
         * <p>
         * If you are returning relative directions ({@link #START} , {@link #END}) from the
         * {@link #getMovementFlags(RecyclerView, RecyclerView.ViewHolder)} method, this method
         * will also use relative directions. Otherwise, it will use absolute directions.
         * <p>
         * If you don't support swiping, this method will never be called.
         * <p>
         * ItemTouchHelper will keep a reference to the View until it is detached from
         * RecyclerView.
         * As soon as it is detached, ItemTouchHelper will call
         * {@link #clearView(RecyclerView, RecyclerView.ViewHolder)}.
         *
         * @param viewHolder The ViewHolder which has been swiped by the user.
         * @param direction  The direction to which the ViewHolder is swiped. It is one of
         *                   {@link #UP}, {@link #DOWN},
         *                   {@link #LEFT} or {@link #RIGHT}. If your
         *                   {@link #getMovementFlags(RecyclerView, RecyclerView.ViewHolder)}
         *                   method
         *                   returned relative flags instead of {@link #LEFT} / {@link #RIGHT};
         *                   `direction` will be relative as well. ({@link #START} or {@link
         *                   #END}).
         */
        public abstract void onSwiped(@NonNull ViewHolder viewHolder, int direction);

        /**
         * Called when the ViewHolder swiped or dragged by the ItemTouchHelper is changed.
         * <p/>
         * If you override this method, you should call super.
         *
         * @param viewHolder  The new ViewHolder that is being swiped or dragged. Might be null if
         *                    it is cleared.
         * @param actionState One of {@link ItemTouchHelper#ACTION_STATE_IDLE},
         *                    {@link ItemTouchHelper#ACTION_STATE_SWIPE} or
         *                    {@link ItemTouchHelper#ACTION_STATE_DRAG}.
         * @see #clearView(RecyclerView, RecyclerView.ViewHolder)
         */
        public void onSelectedChanged(@Nullable ViewHolder viewHolder, int actionState) {
            if (viewHolder != null) {
                ItemTouchUIUtilImpl.INSTANCE.onSelected(viewHolder.itemView);
            }

        }

        protected int getMaxDragScroll(RecyclerView recyclerView) {
            if (mCachedMaxScrollSpeed == -1) {
                mCachedMaxScrollSpeed = recyclerView.getResources().getDimensionPixelSize(
                        R.dimen.item_touch_helper_max_drag_scroll_per_frame);
            }
            return mCachedMaxScrollSpeed;
        }

        /**
         * Called when {@link #onMove(RecyclerView, RecyclerView.ViewHolder, RecyclerView.ViewHolder)} returns true.
         * <p>
         * ItemTouchHelper does not create an extra Bitmap or View while dragging, instead, it
         * modifies the existing View. Because of this reason, it is important that the View is
         * still part of the layout after it is moved. This may not work as intended when swapped
         * Views are close to RecyclerView bounds or there are gaps between them (e.g. other Views
         * which were not eligible for dropping over).
         * <p>
         * This method is responsible to give necessary hint to the LayoutManager so that it will
         * keep the View in visible area. For example, for LinearLayoutManager, this is as simple
         * as calling {@link LinearLayoutManager#scrollToPositionWithOffset(int, int)}.
         * <p>
         * Default implementation calls {@link RecyclerView#scrollToPosition(int)} if the View's
         * new position is likely to be out of bounds.
         * <p>
         * It is important to ensure the ViewHolder will stay visible as otherwise, it might be
         * removed by the LayoutManager if the move causes the View to go out of bounds. In that
         * case, drag will end prematurely.
         *
         * @param recyclerView The RecyclerView controlled by the ItemTouchHelperPlus.
         * @param viewHolder   The ViewHolder under user's control.
         * @param fromPos      The previous adapter position of the dragged item (before it was
         *                     moved).
         * @param target       The ViewHolder on which the currently active item has been dropped.
         * @param toPos        The new adapter position of the dragged item.
         * @param x            The updated left value of the dragged View after drag translations
         *                     are applied. This value does not include margins added by
         *                     {@link RecyclerView.ItemDecoration}s.
         * @param y            The updated top value of the dragged View after drag translations
         *                     are applied. This value does not include margins added by
         *                     {@link RecyclerView.ItemDecoration}s.
         */
        public void onMoved(@NonNull final RecyclerView recyclerView,
                            @NonNull final ViewHolder viewHolder, int fromPos, @NonNull final ViewHolder target,
                            int toPos, int x, int y) {
            final RecyclerView.LayoutManager layoutManager = recyclerView.getLayoutManager();
            if (layoutManager instanceof ItemTouchHelperPlus.ViewDropHandler) {
                ((ItemTouchHelperPlus.ViewDropHandler) layoutManager).prepareForDrop(viewHolder.itemView,
                        target.itemView, x, y);
                return;
            }

            // if layout manager cannot handle it, do some guesswork
            if (layoutManager.canScrollHorizontally()) {
                final int minLeft = layoutManager.getDecoratedLeft(target.itemView);
                if (minLeft <= recyclerView.getPaddingLeft()) {
                    recyclerView.scrollToPosition(toPos);
                }
                final int maxRight = layoutManager.getDecoratedRight(target.itemView);
                if (maxRight >= recyclerView.getWidth() - recyclerView.getPaddingRight()) {
                    recyclerView.scrollToPosition(toPos);
                }
            }

            if (layoutManager.canScrollVertically()) {
                final int minTop = layoutManager.getDecoratedTop(target.itemView);
                if (minTop <= recyclerView.getPaddingTop()) {
                    recyclerView.scrollToPosition(toPos);
                }
                final int maxBottom = layoutManager.getDecoratedBottom(target.itemView);
                if (maxBottom >= recyclerView.getHeight() - recyclerView.getPaddingBottom()) {
                    recyclerView.scrollToPosition(toPos);
                }
            }
        }

        void onDraw(Canvas c, RecyclerView parent, ViewHolder selected,
                    List<RecoverAnimation> recoverAnimationList,
                    int actionState, float dX, float dY) {
            final int recoverAnimSize = recoverAnimationList.size();
            for (int i = 0; i < recoverAnimSize; i++) {
                final RecoverAnimation anim = recoverAnimationList.get(i);
                anim.update();
                final int count = c.save();
                onChildDraw(c, parent, anim.mViewHolder, anim.mX, anim.mY, anim.mActionState,
                        false);
                c.restoreToCount(count);
            }
            if (selected != null) {
                final int count = c.save();
                onChildDraw(c, parent, selected, dX, dY, actionState, true);
                c.restoreToCount(count);
            }
        }

        void onDrawOver(Canvas c, RecyclerView parent, ViewHolder selected,
                        List<RecoverAnimation> recoverAnimationList,
                        int actionState, float dX, float dY) {
            final int recoverAnimSize = recoverAnimationList.size();
            for (int i = 0; i < recoverAnimSize; i++) {
                final RecoverAnimation anim = recoverAnimationList.get(i);
                final int count = c.save();
                onChildDrawOver(c, parent, anim.mViewHolder, anim.mX, anim.mY, anim.mActionState,
                        false);
                c.restoreToCount(count);
            }
            if (selected != null) {
                final int count = c.save();
                onChildDrawOver(c, parent, selected, dX, dY, actionState, true);
                c.restoreToCount(count);
            }
            boolean hasRunningAnimation = false;
            for (int i = recoverAnimSize - 1; i >= 0; i--) {
                final RecoverAnimation anim = recoverAnimationList.get(i);
                if (anim.mEnded && !anim.mIsPendingCleanup) {
                    recoverAnimationList.remove(i);
                } else if (!anim.mEnded) {
                    hasRunningAnimation = true;
                }
            }
            if (hasRunningAnimation) {
                parent.invalidate();
            }
        }

        /**
         * Called by the ItemTouchHelper when the user interaction with an element is over and it
         * also completed its animation.
         * <p>
         * This is a good place to clear all changes on the View that was done in
         * {@link #onSelectedChanged(RecyclerView.ViewHolder, int)},
         * {@link #onChildDraw(Canvas, RecyclerView, RecyclerView.ViewHolder, float, float, int,
         * boolean)} or
         * {@link #onChildDrawOver(Canvas, RecyclerView, RecyclerView.ViewHolder, float, float, int, boolean)}.
         *
         * @param recyclerView The RecyclerView which is controlled by the ItemTouchHelperPlus.
         * @param viewHolder   The View that was interacted by the user.
         */
        public void clearView(@NonNull RecyclerView recyclerView, @NonNull ViewHolder viewHolder) {
            ItemTouchUIUtilImpl.INSTANCE.clearView(viewHolder.itemView);
        }

        /**
         * Called by ItemTouchHelper on RecyclerView's onDraw callback.
         * <p>
         * If you would like to customize how your View's respond to user interactions, this is
         * a good place to override.
         * <p>
         * Default implementation translates the child by the given <code>dX</code>,
         * <code>dY</code>.
         * ItemTouchHelper also takes care of drawing the child after other children if it is being
         * dragged. This is done using child re-ordering mechanism. On platforms prior to L, this
         * is
         * achieved via {@link android.view.ViewGroup#(int, int)} and on L
         * and after, it changes View's elevation value to be greater than all other children.)
         *
         * @param c                 The canvas which RecyclerView is drawing its children
         * @param recyclerView      The RecyclerView to which ItemTouchHelper is attached to
         * @param viewHolder        The ViewHolder which is being interacted by the User or it was
         *                          interacted and simply animating to its original position
         * @param dX                The amount of horizontal displacement caused by user's action
         * @param dY                The amount of vertical displacement caused by user's action
         * @param actionState       The type of interaction on the View. Is either {@link
         *                          #ACTION_STATE_DRAG} or {@link #ACTION_STATE_SWIPE}.
         * @param isCurrentlyActive True if this view is currently being controlled by the user or
         *                          false it is simply animating back to its original state.
         * @see #onChildDrawOver(Canvas, RecyclerView, RecyclerView.ViewHolder, float, float, int,
         * boolean)
         */
        public void onChildDraw(@NonNull Canvas c, @NonNull RecyclerView recyclerView,
                                @NonNull ViewHolder viewHolder,
                                float dX, float dY, int actionState, boolean isCurrentlyActive) {
            ItemTouchUIUtilImpl.INSTANCE.onDraw(c, recyclerView, viewHolder.itemView, dX, dY,
                    actionState, isCurrentlyActive);
        }

        /**
         * Called by ItemTouchHelper on RecyclerView's onDraw callback.
         * <p>
         * If you would like to customize how your View's respond to user interactions, this is
         * a good place to override.
         * <p>
         * Default implementation translates the child by the given <code>dX</code>,
         * <code>dY</code>.
         * ItemTouchHelper also takes care of drawing the child after other children if it is being
         * dragged. This is done using child re-ordering mechanism. On platforms prior to L, this
         * is
         * achieved via {@link android.view.ViewGroup#(int, int)} and on L
         * and after, it changes View's elevation value to be greater than all other children.)
         *
         * @param c                 The canvas which RecyclerView is drawing its children
         * @param recyclerView      The RecyclerView to which ItemTouchHelper is attached to
         * @param viewHolder        The ViewHolder which is being interacted by the User or it was
         *                          interacted and simply animating to its original position
         * @param dX                The amount of horizontal displacement caused by user's action
         * @param dY                The amount of vertical displacement caused by user's action
         * @param actionState       The type of interaction on the View. Is either {@link
         *                          #ACTION_STATE_DRAG} or {@link #ACTION_STATE_SWIPE}.
         * @param isCurrentlyActive True if this view is currently being controlled by the user or
         *                          false it is simply animating back to its original state.
         * @see #onChildDrawOver(Canvas, RecyclerView, RecyclerView.ViewHolder, float, float, int,
         * boolean)
         */
        public void onChildDrawOver(@NonNull Canvas c, @NonNull RecyclerView recyclerView,
                                    ViewHolder viewHolder,
                                    float dX, float dY, int actionState, boolean isCurrentlyActive) {
            ItemTouchUIUtilImpl.INSTANCE.onDrawOver(c, recyclerView, viewHolder.itemView, dX, dY,
                    actionState, isCurrentlyActive);
        }

        /**
         * Called by the ItemTouchHelper when user action finished on a ViewHolder and now the View
         * will be animated to its final position.
         * <p>
         * Default implementation uses ItemAnimator's duration values. If
         * <code>animationType</code> is {@link #ANIMATION_TYPE_DRAG}, it returns
         * {@link RecyclerView.ItemAnimator#getMoveDuration()}, otherwise, it returns
         * {@link RecyclerView.ItemAnimator#getRemoveDuration()}. If RecyclerView does not have
         * any {@link RecyclerView.ItemAnimator} attached, this method returns
         * {@code DEFAULT_DRAG_ANIMATION_DURATION} or {@code DEFAULT_SWIPE_ANIMATION_DURATION}
         * depending on the animation type.
         *
         * @param recyclerView  The RecyclerView to which the ItemTouchHelper is attached to.
         * @param animationType The type of animation. Is one of {@link #ANIMATION_TYPE_DRAG},
         *                      {@link #ANIMATION_TYPE_SWIPE_CANCEL} or
         *                      {@link #ANIMATION_TYPE_SWIPE_SUCCESS}.
         * @param animateDx     The horizontal distance that the animation will offset
         * @param animateDy     The vertical distance that the animation will offset
         * @return The duration for the animation
         */
        @SuppressWarnings("WeakerAccess")
        public long getAnimationDuration(@NonNull RecyclerView recyclerView, int animationType,
                                         float animateDx, float animateDy) {
            final RecyclerView.ItemAnimator itemAnimator = recyclerView.getItemAnimator();
            if (itemAnimator == null) {
                return animationType == ANIMATION_TYPE_DRAG ? DEFAULT_DRAG_ANIMATION_DURATION
                        : DEFAULT_SWIPE_ANIMATION_DURATION;
            } else {
                return animationType == ANIMATION_TYPE_DRAG ? itemAnimator.getMoveDuration()
                        : itemAnimator.getRemoveDuration();
            }
        }

        /**
         * Called by the ItemTouchHelper when user is dragging a view out of bounds.
         * <p>
         * You can override this method to decide how much RecyclerView should scroll in response
         * to this action. Default implementation calculates a value based on the amount of View
         * out of bounds and the time it spent there. The longer user keeps the View out of bounds,
         * the faster the list will scroll. Similarly, the larger portion of the View is out of
         * bounds, the faster the RecyclerView will scroll.
         *
         * @param recyclerView        The RecyclerView instance to which ItemTouchHelper is
         *                            attached to.
         * @param viewSize            The total size of the View in scroll direction, excluding
         *                            item decorations.
         * @param viewSizeOutOfBounds The total size of the View that is out of bounds. This value
         *                            is negative if the View is dragged towards left or top edge.
         * @param totalSize           The total size of RecyclerView in the scroll direction.
         * @param msSinceStartScroll  The time passed since View is kept out of bounds.
         * @return The amount that RecyclerView should scroll. Keep in mind that this value will
         * be passed to {@link RecyclerView#scrollBy(int, int)} method.
         */
        @SuppressWarnings("WeakerAccess")
        public int interpolateOutOfBoundsScroll(@NonNull RecyclerView recyclerView,
                                                int viewSize, int viewSizeOutOfBounds,
                                                int totalSize, long msSinceStartScroll) {
            final int maxScroll = getMaxDragScroll(recyclerView);
            final int absOutOfBounds = Math.abs(viewSizeOutOfBounds);
            final int direction = (int) Math.signum(viewSizeOutOfBounds);
            // might be negative if other direction
            float outOfBoundsRatio = Math.min(1f, 1f * absOutOfBounds / viewSize);
            final int cappedScroll = (int) (direction * maxScroll
                    * sDragViewScrollCapInterpolator.getInterpolation(outOfBoundsRatio));
            final float timeRatio;
            if (msSinceStartScroll > DRAG_SCROLL_ACCELERATION_LIMIT_TIME_MS) {
                timeRatio = 1f;
            } else {
                timeRatio = (float) msSinceStartScroll / DRAG_SCROLL_ACCELERATION_LIMIT_TIME_MS;
            }
            final int value = (int) (cappedScroll * sDragScrollInterpolator
                    .getInterpolation(timeRatio));
            if (value == 0) {
                return viewSizeOutOfBounds > 0 ? 1 : -1;
            }
            return value;
        }

}
