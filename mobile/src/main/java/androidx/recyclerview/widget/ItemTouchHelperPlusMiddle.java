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
 * ItemTouchHelperPlus 拆分继承链的中间层。
 *
 * <p>由原 {@link ItemTouchHelperPlus}（2708 行）拆分而来，功能与细节保持不变。本类承载
 * 触摸事件分发（{@code OnItemTouchListener}）、动画恢复与交换判定等核心流程，
 * 上承 {@link ItemTouchHelperPlusBase} 的共享状态，下接叶子类 {@link ItemTouchHelperPlus}。</p>
 */
@SuppressWarnings("RestrictedApi")
public abstract class ItemTouchHelperPlusMiddle extends ItemTouchHelperPlusBase {
    protected final OnItemTouchListener mOnItemTouchListener = new OnItemTouchListener() {
        @Override
        public boolean onInterceptTouchEvent(@NonNull RecyclerView recyclerView,
                                             @NonNull MotionEvent event) {
            mGestureDetector.onTouchEvent(event);
            if (DEBUG) {
                Log.d(TAG, "intercept: x:" + event.getX() + ",y:" + event.getY() + ", " + event);
            }
            final int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                mActivePointerId = event.getPointerId(0);
                mInitialTouchX = event.getX();
                mInitialTouchY = event.getY();
                obtainVelocityTracker();
                if (mSelected == null) {
                    final RecoverAnimation animation = findAnimation(event);
                    if (animation != null) {
                        mInitialTouchX -= animation.mX;
                        mInitialTouchY -= animation.mY;
                        endRecoverAnimation(animation.mViewHolder, true);
                        if (mPendingCleanup.remove(animation.mViewHolder.itemView)) {
                            mCallback.clearView(mRecyclerView, animation.mViewHolder);
                        }
                        select(animation.mViewHolder, animation.mActionState);
                        updateDxDy(event, mSelectedFlags, 0);
                    }
                }
            } else if (action == MotionEvent.ACTION_CANCEL || action == MotionEvent.ACTION_UP) {
                mActivePointerId = ACTIVE_POINTER_ID_NONE;
                select(null, ACTION_STATE_IDLE);
            } else if (mActivePointerId != ACTIVE_POINTER_ID_NONE) {
                // in a non scroll orientation, if distance change is above threshold, we
                // can select the item
                final int index = event.findPointerIndex(mActivePointerId);
                if (DEBUG) {
                    Log.d(TAG, "pointer index " + index);
                }
                if (index >= 0) {
                    checkSelectForSwipe(action, event, index);
                }
            }
            if (mVelocityTracker != null) {
                mVelocityTracker.addMovement(event);
            }
            return mSelected != null;
        }

        @Override
        public void onTouchEvent(@NonNull RecyclerView recyclerView, @NonNull MotionEvent event) {
            mGestureDetector.onTouchEvent(event);
            if (DEBUG) {
                Log.d(TAG,
                        "on touch: x:" + mInitialTouchX + ",y:" + mInitialTouchY + ", :" + event);
            }
            if (mVelocityTracker != null) {
                mVelocityTracker.addMovement(event);
            }
            if (mActivePointerId == ACTIVE_POINTER_ID_NONE) {
                return;
            }
            final int action = event.getActionMasked();
            final int activePointerIndex = event.findPointerIndex(mActivePointerId);
            if (activePointerIndex >= 0) {
                checkSelectForSwipe(action, event, activePointerIndex);
            }
            ViewHolder viewHolder = mSelected;
            if (viewHolder == null) {
                return;
            }
            switch (action) {
                case MotionEvent.ACTION_MOVE: {
                    // Find the index of the active pointer and fetch its position
                    if (activePointerIndex >= 0) {
                        updateDxDy(event, mSelectedFlags, activePointerIndex);
                        moveIfNecessary(viewHolder);
                        mRecyclerView.removeCallbacks(mScrollRunnable);
                        mScrollRunnable.run();
                        mRecyclerView.invalidate();
                    }
                    break;
                }
                case MotionEvent.ACTION_CANCEL:
                    if (mVelocityTracker != null) {
                        mVelocityTracker.clear();
                    }
                    // fall through
                case MotionEvent.ACTION_UP:
                    select(null, ACTION_STATE_IDLE);
                    mActivePointerId = ACTIVE_POINTER_ID_NONE;
                    break;
                case MotionEvent.ACTION_POINTER_UP: {
                    final int pointerIndex = event.getActionIndex();
                    final int pointerId = event.getPointerId(pointerIndex);
                    if (pointerId == mActivePointerId) {
                        // This was our active pointer going up. Choose a new
                        // active pointer and adjust accordingly.
                        final int newPointerIndex = pointerIndex == 0 ? 1 : 0;
                        mActivePointerId = event.getPointerId(newPointerIndex);
                        updateDxDy(event, mSelectedFlags, pointerIndex);
                    }
                    break;
                }
            }
        }

        @Override
        public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {
            if (!disallowIntercept) {
                return;
            }
            select(null, ACTION_STATE_IDLE);
        }
    };

    protected void setupCallbacks() {
        ViewConfiguration vc = ViewConfiguration.get(mRecyclerView.getContext());
        mSlop = vc.getScaledTouchSlop();
        mRecyclerView.addItemDecoration(this);
        mRecyclerView.addOnItemTouchListener(mOnItemTouchListener);
        mRecyclerView.addOnChildAttachStateChangeListener(this);
        startGestureDetection();
    }

    protected void destroyCallbacks() {
        mRecyclerView.removeItemDecoration(this);
        mRecyclerView.removeOnItemTouchListener(mOnItemTouchListener);
        mRecyclerView.removeOnChildAttachStateChangeListener(this);
        // clean all attached
        final int recoverAnimSize = mRecoverAnimations.size();
        for (int i = recoverAnimSize - 1; i >= 0; i--) {
            final RecoverAnimation recoverAnimation = mRecoverAnimations.get(0);
            mCallback.clearView(mRecyclerView, recoverAnimation.mViewHolder);
        }
        mRecoverAnimations.clear();
        mOverdrawChild = null;
        mOverdrawChildPosition = -1;
        releaseVelocityTracker();
        stopGestureDetection();
    }

    protected void startGestureDetection() {
        mItemTouchHelperGestureListener = new ItemTouchHelperPlus.ItemTouchHelperGestureListener();
        mGestureDetector = new GestureDetectorCompat(mRecyclerView.getContext(),
                mItemTouchHelperGestureListener);
    }

    protected void stopGestureDetection() {
        if (mItemTouchHelperGestureListener != null) {
            mItemTouchHelperGestureListener.doNotReactToLongPress();
            mItemTouchHelperGestureListener = null;
        }
        if (mGestureDetector != null) {
            mGestureDetector = null;
        }
    }

    @SuppressWarnings("WeakerAccess") /* synthetic access */
    void postDispatchSwipe(final RecoverAnimation anim, final int swipeDir) {
        // wait until animations are complete.
        mRecyclerView.post(new Runnable() {
            @Override
            public void run() {
                if (mRecyclerView != null && mRecyclerView.isAttachedToWindow()
                        && !anim.mOverridden
                        && anim.mViewHolder.getAdapterPosition() != RecyclerView.NO_POSITION) {
                    final RecyclerView.ItemAnimator animator = mRecyclerView.getItemAnimator();
                    // if animator is running or we have other active recover animations, we try
                    // not to call onSwiped because DefaultItemAnimator is not good at merging
                    // animations. Instead, we wait and batch.
                    if ((animator == null || !animator.isRunning(null))
                            && !hasRunningRecoverAnim()) {
                        mCallback.onSwiped(anim.mViewHolder, swipeDir);
                    } else {
                        mRecyclerView.post(this);
                    }
                }
            }
        });
    }

    @SuppressWarnings("WeakerAccess") /* synthetic access */
    boolean hasRunningRecoverAnim() {
        final int size = mRecoverAnimations.size();
        for (int i = 0; i < size; i++) {
            if (!mRecoverAnimations.get(i).mEnded) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void onChildViewAttachedToWindow(@NonNull View view) {
    }

    @Override
    public void onChildViewDetachedFromWindow(@NonNull View view) {
        removeChildDrawingOrderCallbackIfNecessary(view);
        final ViewHolder holder = mRecyclerView.getChildViewHolder(view);
        if (holder == null) {
            return;
        }
        if (mSelected != null && holder == mSelected) {
            select(null, ACTION_STATE_IDLE);
        } else {
            endRecoverAnimation(holder, false); // this may push it into pending cleanup list.
            if (mPendingCleanup.remove(holder.itemView)) {
                mCallback.clearView(mRecyclerView, holder);
            }
        }
    }

    /**
     * Returns the animation type or 0 if cannot be found.
     */
    @SuppressWarnings("WeakerAccess") /* synthetic access */
    void endRecoverAnimation(ViewHolder viewHolder, boolean override) {
        final int recoverAnimSize = mRecoverAnimations.size();
        for (int i = recoverAnimSize - 1; i >= 0; i--) {
            final RecoverAnimation anim = mRecoverAnimations.get(i);
            if (anim.mViewHolder == viewHolder) {
                anim.mOverridden |= override;
                if (!anim.mEnded) {
                    anim.cancel();
                }
                mRecoverAnimations.remove(i);
                return;
            }
        }
    }

    @Override
    public void getItemOffsets(Rect outRect, View view, RecyclerView parent,
                               RecyclerView.State state) {
        outRect.setEmpty();
    }

    @SuppressWarnings("WeakerAccess") /* synthetic access */
    void obtainVelocityTracker() {
        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
        }
        mVelocityTracker = VelocityTracker.obtain();
    }

    protected void releaseVelocityTracker() {
        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
            mVelocityTracker = null;
        }
    }

    protected ViewHolder findSwipedView(MotionEvent motionEvent) {
        final RecyclerView.LayoutManager lm = mRecyclerView.getLayoutManager();
        if (mActivePointerId == ACTIVE_POINTER_ID_NONE) {
            return null;
        }
        final int pointerIndex = motionEvent.findPointerIndex(mActivePointerId);
        final float dx = motionEvent.getX(pointerIndex) - mInitialTouchX;
        final float dy = motionEvent.getY(pointerIndex) - mInitialTouchY;
        final float absDx = Math.abs(dx);
        final float absDy = Math.abs(dy);

        if (absDx < mSlop && absDy < mSlop) {
            return null;
        }
        if (absDx > absDy && lm.canScrollHorizontally()) {
            return null;
        } else if (absDy > absDx && lm.canScrollVertically()) {
            return null;
        }
        View child = findChildView(motionEvent);
        if (child == null) {
            return null;
        }
        return mRecyclerView.getChildViewHolder(child);
    }

    /**
     * Checks whether we should select a View for swiping.
     */
    @SuppressWarnings("WeakerAccess") /* synthetic access */
    void checkSelectForSwipe(int action, MotionEvent motionEvent, int pointerIndex) {
        if (mSelected != null || action != MotionEvent.ACTION_MOVE
                || mActionState == ACTION_STATE_DRAG || !mCallback.isItemViewSwipeEnabled()) {
            return;
        }
        if (mRecyclerView.getScrollState() == RecyclerView.SCROLL_STATE_DRAGGING) {
            return;
        }
        final ViewHolder vh = findSwipedView(motionEvent);
        if (vh == null) {
            return;
        }
        final int movementFlags = mCallback.getAbsoluteMovementFlags(mRecyclerView, vh);

        final int swipeFlags = (movementFlags & ACTION_MODE_SWIPE_MASK)
                >> (DIRECTION_FLAG_COUNT * ACTION_STATE_SWIPE);

        if (swipeFlags == 0) {
            return;
        }

        // mDx and mDy are only set in allowed directions. We use custom x/y here instead of
        // updateDxDy to avoid swiping if user moves more in the other direction
        final float x = motionEvent.getX(pointerIndex);
        final float y = motionEvent.getY(pointerIndex);

        // Calculate the distance moved
        final float dx = x - mInitialTouchX;
        final float dy = y - mInitialTouchY;
        // swipe target is chose w/o applying flags so it does not really check if swiping in that
        // direction is allowed. This why here, we use mDx mDy to check slope value again.
        final float absDx = Math.abs(dx);
        final float absDy = Math.abs(dy);

        if (absDx < mSlop && absDy < mSlop) {
            return;
        }
        if (absDx > absDy) {
            if (dx < 0 && (swipeFlags & LEFT) == 0) {
                return;
            }
            if (dx > 0 && (swipeFlags & RIGHT) == 0) {
                return;
            }
        } else {
            if (dy < 0 && (swipeFlags & UP) == 0) {
                return;
            }
            if (dy > 0 && (swipeFlags & DOWN) == 0) {
                return;
            }
        }
        mDx = mDy = 0f;
        mActivePointerId = motionEvent.getPointerId(0);
        select(vh, ACTION_STATE_SWIPE);
    }

    @SuppressWarnings("WeakerAccess") /* synthetic access */
    View findChildView(MotionEvent event) {
        // first check elevated views, if none, then call RV
        final float x = event.getX();
        final float y = event.getY();
        if (mSelected != null) {
            final View selectedView = mSelected.itemView;
            if (hitTest(selectedView, x, y, mSelectedStartX + mDx, mSelectedStartY + mDy)) {
                return selectedView;
            }
        }
        for (int i = mRecoverAnimations.size() - 1; i >= 0; i--) {
            final RecoverAnimation anim = mRecoverAnimations.get(i);
            final View view = anim.mViewHolder.itemView;
            if (hitTest(view, x, y, anim.mX, anim.mY)) {
                return view;
            }
        }
        return mRecyclerView.findChildViewUnder(x, y);
    }

    @SuppressWarnings("WeakerAccess") /* synthetic access */
    RecoverAnimation findAnimation(MotionEvent event) {
        if (mRecoverAnimations.isEmpty()) {
            return null;
        }
        View target = findChildView(event);
        for (int i = mRecoverAnimations.size() - 1; i >= 0; i--) {
            final RecoverAnimation anim = mRecoverAnimations.get(i);
            if (anim.mViewHolder.itemView == target) {
                return anim;
            }
        }
        return null;
    }

    @SuppressWarnings("WeakerAccess") /* synthetic access */
    void updateDxDy(MotionEvent ev, int directionFlags, int pointerIndex) {
        final float x = ev.getX(pointerIndex);
        final float y = ev.getY(pointerIndex);

        // Calculate the distance moved
        mDx = x - mInitialTouchX;
        mDy = y - mInitialTouchY;
        if ((directionFlags & LEFT) == 0) {
            mDx = Math.max(0, mDx);
        }
        if ((directionFlags & RIGHT) == 0) {
            mDx = Math.min(0, mDx);
        }
        if ((directionFlags & UP) == 0) {
            mDy = Math.max(0, mDy);
        }
        if ((directionFlags & DOWN) == 0) {
            mDy = Math.min(0, mDy);
        }
    }

    protected int swipeIfNecessary(ViewHolder viewHolder) {
        if (mActionState == ACTION_STATE_DRAG) {
            return 0;
        }
        final int originalMovementFlags = mCallback.getMovementFlags(mRecyclerView, viewHolder);
        final int absoluteMovementFlags = mCallback.convertToAbsoluteDirection(
                originalMovementFlags,
                ViewCompat.getLayoutDirection(mRecyclerView));
        final int flags = (absoluteMovementFlags
                & ACTION_MODE_SWIPE_MASK) >> (ACTION_STATE_SWIPE * DIRECTION_FLAG_COUNT);
        if (flags == 0) {
            return 0;
        }
        final int originalFlags = (originalMovementFlags
                & ACTION_MODE_SWIPE_MASK) >> (ACTION_STATE_SWIPE * DIRECTION_FLAG_COUNT);
        int swipeDir;
        if (Math.abs(mDx) > Math.abs(mDy)) {
            if ((swipeDir = checkHorizontalSwipe(viewHolder, flags)) > 0) {
                // if swipe dir is not in original flags, it should be the relative direction
                if ((originalFlags & swipeDir) == 0) {
                    // convert to relative
                    return ItemTouchHelperPlus.Callback.convertToRelativeDirection(swipeDir,
                            ViewCompat.getLayoutDirection(mRecyclerView));
                }
                return swipeDir;
            }
            if ((swipeDir = checkVerticalSwipe(viewHolder, flags)) > 0) {
                return swipeDir;
            }
        } else {
            if ((swipeDir = checkVerticalSwipe(viewHolder, flags)) > 0) {
                return swipeDir;
            }
            if ((swipeDir = checkHorizontalSwipe(viewHolder, flags)) > 0) {
                // if swipe dir is not in original flags, it should be the relative direction
                if ((originalFlags & swipeDir) == 0) {
                    // convert to relative
                    return ItemTouchHelperPlus.Callback.convertToRelativeDirection(swipeDir,
                            ViewCompat.getLayoutDirection(mRecyclerView));
                }
                return swipeDir;
            }
        }
        return 0;
    }

    protected int checkHorizontalSwipe(ViewHolder viewHolder, int flags) {
        if ((flags & (LEFT | RIGHT)) != 0) {
            final int dirFlag = mDx > 0 ? RIGHT : LEFT;
            if (mVelocityTracker != null && mActivePointerId > -1) {
                mVelocityTracker.computeCurrentVelocity(PIXELS_PER_SECOND,
                        mCallback.getSwipeVelocityThreshold(mMaxSwipeVelocity));
                final float xVelocity = mVelocityTracker.getXVelocity(mActivePointerId);
                final float yVelocity = mVelocityTracker.getYVelocity(mActivePointerId);
                final int velDirFlag = xVelocity > 0f ? RIGHT : LEFT;
                final float absXVelocity = Math.abs(xVelocity);
                if ((velDirFlag & flags) != 0 && dirFlag == velDirFlag
                        && absXVelocity >= mCallback.getSwipeEscapeVelocity(mSwipeEscapeVelocity)
                        && absXVelocity > Math.abs(yVelocity)) {
                    return velDirFlag;
                }
            }

            final float threshold = mRecyclerView.getWidth() * mCallback
                    .getSwipeThreshold(viewHolder);

            if ((flags & dirFlag) != 0 && Math.abs(mDx) > threshold) {
                return dirFlag;
            }
        }
        return 0;
    }

    protected int checkVerticalSwipe(ViewHolder viewHolder, int flags) {
        if ((flags & (UP | DOWN)) != 0) {
            final int dirFlag = mDy > 0 ? DOWN : UP;
            if (mVelocityTracker != null && mActivePointerId > -1) {
                mVelocityTracker.computeCurrentVelocity(PIXELS_PER_SECOND,
                        mCallback.getSwipeVelocityThreshold(mMaxSwipeVelocity));
                final float xVelocity = mVelocityTracker.getXVelocity(mActivePointerId);
                final float yVelocity = mVelocityTracker.getYVelocity(mActivePointerId);
                final int velDirFlag = yVelocity > 0f ? DOWN : UP;
                final float absYVelocity = Math.abs(yVelocity);
                if ((velDirFlag & flags) != 0 && velDirFlag == dirFlag
                        && absYVelocity >= mCallback.getSwipeEscapeVelocity(mSwipeEscapeVelocity)
                        && absYVelocity > Math.abs(xVelocity)) {
                    return velDirFlag;
                }
            }

            final float threshold = mRecyclerView.getHeight() * mCallback
                    .getSwipeThreshold(viewHolder);
            if ((flags & dirFlag) != 0 && Math.abs(mDy) > threshold) {
                return dirFlag;
            }
        }
        return 0;
    }

    protected void addChildDrawingOrderCallback() {
        if (Build.VERSION.SDK_INT >= 21) {
            return; // we use elevation on Lollipop
        }
        if (mChildDrawingOrderCallback == null) {
            mChildDrawingOrderCallback = new RecyclerView.ChildDrawingOrderCallback() {
                @Override
                public int onGetChildDrawingOrder(int childCount, int i) {
                    if (mOverdrawChild == null) {
                        return i;
                    }
                    int childPosition = mOverdrawChildPosition;
                    if (childPosition == -1) {
                        childPosition = mRecyclerView.indexOfChild(mOverdrawChild);
                        mOverdrawChildPosition = childPosition;
                    }
                    if (i == childCount - 1) {
                        return childPosition;
                    }
                    return i < childPosition ? i : i + 1;
                }
            };
        }
        mRecyclerView.setChildDrawingOrderCallback(mChildDrawingOrderCallback);
    }

    @SuppressWarnings("WeakerAccess") /* synthetic access */
    void removeChildDrawingOrderCallbackIfNecessary(View view) {
        if (view == mOverdrawChild) {
            mOverdrawChild = null;
            // only remove if we've added
            if (mChildDrawingOrderCallback != null) {
                mRecyclerView.setChildDrawingOrderCallback(null);
            }
        }
    }

}
