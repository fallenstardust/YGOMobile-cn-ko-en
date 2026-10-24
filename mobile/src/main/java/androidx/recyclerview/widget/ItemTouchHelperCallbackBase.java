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
 * {@code ItemTouchHelperPlus.Callback} 拆分的顶层实现基类（上半部分）。
 *
 * <p>原嵌套类 {@code ItemTouchHelperPlus.Callback} 超过 800 行，故提升为同包顶层类并分为两层：
 * 本类（方向常量、movement flags 构造/转换等）← {@link ItemTouchHelperCallbackImpl}（回调行为方法）。
 * {@code ItemTouchHelperPlusBase.Callback} 作为壳类继承自 Impl，因此全项目原有的
 * {@code ItemTouchHelperPlus.Callback} 限定引用无需任何修改仍可解析，行为保持不变。</p>
 */
@SuppressWarnings("RestrictedApi")
public abstract class ItemTouchHelperCallbackBase {
        @SuppressWarnings("WeakerAccess")
        public static final int DEFAULT_DRAG_ANIMATION_DURATION = 200;

        @SuppressWarnings("WeakerAccess")
        public static final int DEFAULT_SWIPE_ANIMATION_DURATION = 250;

        static final int RELATIVE_DIR_FLAGS = START | END
                | ((START | END) << DIRECTION_FLAG_COUNT)
                | ((START | END) << (2 * DIRECTION_FLAG_COUNT));

        protected static final int ABS_HORIZONTAL_DIR_FLAGS = LEFT | RIGHT
                | ((LEFT | RIGHT) << DIRECTION_FLAG_COUNT)
                | ((LEFT | RIGHT) << (2 * DIRECTION_FLAG_COUNT));

        protected static final Interpolator sDragScrollInterpolator = new Interpolator() {
            @Override
            public float getInterpolation(float t) {
                return t * t * t * t * t;
            }
        };

        protected static final Interpolator sDragViewScrollCapInterpolator = new Interpolator() {
            @Override
            public float getInterpolation(float t) {
                t -= 1.0f;
                return t * t * t * t * t + 1.0f;
            }
        };

        /**
         * Drag scroll speed keeps accelerating until this many milliseconds before being capped.
         */
        protected static final long DRAG_SCROLL_ACCELERATION_LIMIT_TIME_MS = 2000;

        protected int mCachedMaxScrollSpeed = -1;

        /**
         * Returns the {@link ItemTouchUIUtil} that is used by the {@link ItemTouchHelperPlus.Callback} class for
         * visual
         * changes on Views in response to user interactions. {@link ItemTouchUIUtil} has different
         * implementations for different platform versions.
         * <p>
         * By default, {@link ItemTouchHelperPlus.Callback} applies these changes on
         * {@link RecyclerView.ViewHolder#itemView}.
         * <p>
         * For example, if you have a use case where you only want the text to move when user
         * swipes over the view, you can do the following:
         * <pre>
         *     public void clearView(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder){
         *         getDefaultUIUtil().clearView(((ItemTouchViewHolder) viewHolder).textView);
         *     }
         *     public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
         *         if (viewHolder != null){
         *             getDefaultUIUtil().onSelected(((ItemTouchViewHolder) viewHolder).textView);
         *         }
         *     }
         *     public void onChildDraw(Canvas c, RecyclerView recyclerView,
         *             RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState,
         *             boolean isCurrentlyActive) {
         *         getDefaultUIUtil().onDraw(c, recyclerView,
         *                 ((ItemTouchViewHolder) viewHolder).textView, dX, dY,
         *                 actionState, isCurrentlyActive);
         *         return true;
         *     }
         *     public void onChildDrawOver(Canvas c, RecyclerView recyclerView,
         *             RecyclerView.ViewHolder viewHolder, float dX, float dY, int actionState,
         *             boolean isCurrentlyActive) {
         *         getDefaultUIUtil().onDrawOver(c, recyclerView,
         *                 ((ItemTouchViewHolder) viewHolder).textView, dX, dY,
         *                 actionState, isCurrentlyActive);
         *         return true;
         *     }
         * </pre>
         *
         * @return The {@link ItemTouchUIUtil} instance that is used by the {@link ItemTouchHelperPlus.Callback}
         */
        @SuppressWarnings("WeakerAccess")
        @NonNull
        public static ItemTouchUIUtil getDefaultUIUtil() {
            return ItemTouchUIUtilImpl.INSTANCE;
        }

        /**
         * Replaces a movement direction with its relative version by taking layout direction into
         * account.
         *
         * @param flags           The flag value that include any number of movement flags.
         * @param layoutDirection The layout direction of the View. Can be obtained from
         *                        {@link ViewCompat#getLayoutDirection(android.view.View)}.
         * @return Updated flags which uses relative flags ({@link #START}, {@link #END}) instead
         * of {@link #LEFT}, {@link #RIGHT}.
         * @see #convertToAbsoluteDirection(int, int)
         */
        @SuppressWarnings("WeakerAccess")
        public static int convertToRelativeDirection(int flags, int layoutDirection) {
            int masked = flags & ABS_HORIZONTAL_DIR_FLAGS;
            if (masked == 0) {
                return flags; // does not have any abs flags, good.
            }
            flags &= ~masked; //remove left / right.
            if (layoutDirection == ViewCompat.LAYOUT_DIRECTION_LTR) {
                // no change. just OR with 2 bits shifted mask and return
                flags |= masked << 2; // START is 2 bits after LEFT, END is 2 bits after RIGHT.
                return flags;
            } else {
                // add RIGHT flag as START
                flags |= ((masked << 1) & ~ABS_HORIZONTAL_DIR_FLAGS);
                // first clean RIGHT bit then add LEFT flag as END
                flags |= ((masked << 1) & ABS_HORIZONTAL_DIR_FLAGS) << 2;
            }
            return flags;

        }

        /**
         * Convenience method to create movement flags.
         * <p>
         * For instance, if you want to let your items be drag & dropped vertically and swiped
         * left to be dismissed, you can call this method with:
         * <code>makeMovementFlags(UP | DOWN, LEFT);</code>
         *
         * @param dragFlags  The directions in which the item can be dragged.
         * @param swipeFlags The directions in which the item can be swiped.
         * @return Returns an integer composed of the given drag and swipe flags.
         */
        public static int makeMovementFlags(int dragFlags, int swipeFlags) {
            return makeFlag(ACTION_STATE_IDLE, swipeFlags | dragFlags)
                    | makeFlag(ACTION_STATE_SWIPE, swipeFlags)
                    | makeFlag(ACTION_STATE_DRAG, dragFlags);
        }

        /**
         * Shifts the given direction flags to the offset of the given action state.
         *
         * @param actionState The action state you want to get flags in. Should be one of
         *                    {@link #ACTION_STATE_IDLE}, {@link #ACTION_STATE_SWIPE} or
         *                    {@link #ACTION_STATE_DRAG}.
         * @param directions  The direction flags. Can be composed from {@link #UP}, {@link #DOWN},
         *                    {@link #RIGHT}, {@link #LEFT} {@link #START} and {@link #END}.
         * @return And integer that represents the given directions in the provided actionState.
         */
        @SuppressWarnings("WeakerAccess")
        public static int makeFlag(int actionState, int directions) {
            return directions << (actionState * DIRECTION_FLAG_COUNT);
        }

        /**
         * Should return a composite flag which defines the enabled move directions in each state
         * (idle, swiping, dragging).
         * <p>
         * Instead of composing this flag manually, you can use {@link #makeMovementFlags(int,
         * int)}
         * or {@link #makeFlag(int, int)}.
         * <p>
         * This flag is composed of 3 sets of 8 bits, where first 8 bits are for IDLE state, next
         * 8 bits are for SWIPE state and third 8 bits are for DRAG state.
         * Each 8 bit sections can be constructed by simply OR'ing direction flags defined in
         * {@link ItemTouchHelper}.
         * <p>
         * For example, if you want it to allow swiping LEFT and RIGHT but only allow starting to
         * swipe by swiping RIGHT, you can return:
         * <pre>
         *      makeFlag(ACTION_STATE_IDLE, RIGHT) | makeFlag(ACTION_STATE_SWIPE, LEFT | RIGHT);
         * </pre>
         * This means, allow right movement while IDLE and allow right and left movement while
         * swiping.
         *
         * @param recyclerView The RecyclerView to which ItemTouchHelper is attached.
         * @param viewHolder   The ViewHolder for which the movement information is necessary.
         * @return flags specifying which movements are allowed on this ViewHolder.
         * @see #makeMovementFlags(int, int)
         * @see #makeFlag(int, int)
         */
        public abstract int getMovementFlags(@NonNull RecyclerView recyclerView,
                                             @NonNull ViewHolder viewHolder);

        /**
         * Converts a given set of flags to absolution direction which means {@link #START} and
         * {@link #END} are replaced with {@link #LEFT} and {@link #RIGHT} depending on the layout
         * direction.
         *
         * @param flags           The flag value that include any number of movement flags.
         * @param layoutDirection The layout direction of the RecyclerView.
         * @return Updated flags which includes only absolute direction values.
         */
        @SuppressWarnings("WeakerAccess")
        public int convertToAbsoluteDirection(int flags, int layoutDirection) {
            int masked = flags & RELATIVE_DIR_FLAGS;
            if (masked == 0) {
                return flags; // does not have any relative flags, good.
            }
            flags &= ~masked; //remove start / end
            if (layoutDirection == ViewCompat.LAYOUT_DIRECTION_LTR) {
                // no change. just OR with 2 bits shifted mask and return
                flags |= masked >> 2; // START is 2 bits after LEFT, END is 2 bits after RIGHT.
                return flags;
            } else {
                // add START flag as RIGHT
                flags |= ((masked >> 1) & ~RELATIVE_DIR_FLAGS);
                // first clean start bit then add END flag as LEFT
                flags |= ((masked >> 1) & RELATIVE_DIR_FLAGS) >> 2;
            }
            return flags;
        }

        final int getAbsoluteMovementFlags(RecyclerView recyclerView,
                                           ViewHolder viewHolder) {
            final int flags = getMovementFlags(recyclerView, viewHolder);
            return convertToAbsoluteDirection(flags, ViewCompat.getLayoutDirection(recyclerView));
        }

        boolean hasDragFlag(RecyclerView recyclerView, ViewHolder viewHolder) {
            final int flags = getAbsoluteMovementFlags(recyclerView, viewHolder);
            return (flags & ACTION_MODE_DRAG_MASK) != 0;
        }

        boolean hasSwipeFlag(RecyclerView recyclerView,
                             ViewHolder viewHolder) {
            final int flags = getAbsoluteMovementFlags(recyclerView, viewHolder);
            return (flags & ACTION_MODE_SWIPE_MASK) != 0;
        }

}
