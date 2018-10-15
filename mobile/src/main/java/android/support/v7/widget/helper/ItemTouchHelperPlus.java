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

package android.support.v7.widget.helper;

import android.content.Context;
import android.graphics.Canvas;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v4.view.GestureDetectorCompat;
import android.support.v7.widget.RecyclerView;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

@SuppressWarnings("RestrictedApi")
public class ItemTouchHelperPlus extends ItemTouchHelper {
    private boolean enableClickDrag;
    private Context mContext;
    private Callback mCallback2;
    private OnItemDragListener mItemDragListener;

    public ItemTouchHelperPlus(Context context, ItemTouchHelperPlus.Callback callback) {
        super(callback);
        mContext = context;
        mCallback2 = callback;
        mCallback2.setItemTouchHelper(this);
    }

    public void setItemDragListener(OnItemDragListener itemDragListener) {
        mItemDragListener = itemDragListener;
    }

    public void setEnableClickDrag(boolean enableClickDrag) {
        this.enableClickDrag = enableClickDrag;
    }

    public boolean isEnableClickDrag() {
        return enableClickDrag;
    }

    @Override
    public void attachToRecyclerView(@Nullable RecyclerView recyclerView) {
        super.attachToRecyclerView(recyclerView);
        mGestureDetector = new GestureDetectorCompat(mRecyclerView.getContext(),
                new ItemTouchHelperGestureListener());
    }

    public Context getContext() {
        return mContext;
    }

    private int mActionState;
    private float mSelectedStartX, mSelectedStartY;
    private RecyclerView.ViewHolder mSelected;

    private int getActionState(){
        return mActionState;
    }

    public float getSelectedStartX() {
        return mSelectedStartX;
    }

    public float getSelectedStartY() {
        return mSelectedStartY;
    }

    @Override
    void select(@Nullable RecyclerView.ViewHolder selected, int actionState) {
        super.select(selected, actionState);
        if (selected != this.mSelected || actionState != mActionState) {
            mActionState = actionState;
            if (selected != null) {
                mSelectedStartX = (float)selected.itemView.getLeft();
                mSelectedStartY = (float)selected.itemView.getTop();
                mSelected = selected;
            }
        }
    }

    @Override
    void moveIfNecessary(RecyclerView.ViewHolder viewHolder) {
        super.moveIfNecessary(viewHolder);
        if (mRecyclerView.isLayoutRequested()) {
            return;
        }
        if (getActionState() != ACTION_STATE_DRAG) {
            return;
        }

        final float threshold = mCallback.getMoveThreshold(viewHolder);
        final int x = (int) (getSelectedStartX() + mDx);
        final int y = (int) (getSelectedStartY() + mDy);
        if (Math.abs(y - viewHolder.itemView.getTop()) < viewHolder.itemView.getHeight() * threshold
                && Math.abs(x - viewHolder.itemView.getLeft())
                < viewHolder.itemView.getWidth() * threshold) {
            return;
        }
        mCallback2.cancelLongPress();
    }

    //region callback
    public abstract static class Callback extends ItemTouchHelper.Callback {
        private Handler mHandler;
        private ItemTouchHelperPlus mItemTouchHelper;
        private long mLongTime = 1000;
        private boolean mLongPressMode;
        private int mSelectId;
        private volatile long longPressTime = 0;
        private boolean isLongPressCancel = false;
        private int mDx = 2;
        private int mDy = 2;

        public void setLongTime(long longTime) {
            mLongTime = longTime;
        }

        public void setItemTouchHelper(ItemTouchHelperPlus itemTouchHelper) {
            mItemTouchHelper = itemTouchHelper;
            mHandler = new Handler(itemTouchHelper.getContext().getMainLooper());
        }

        public void setDragSize(int dx, int dy) {
            mDx = dx;
            mDy = dy;
        }

        OnItemDragListener getOnDragListener() {
            if(mItemTouchHelper == null)return null;
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
            if (isCurrentlyActive && actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                if (dX > mDx || dY > mDy) {
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
            if (viewHolder != null && actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                if (getOnDragListener() != null) {
                    getOnDragListener().onDragStart();
                }
                isLongPressCancel = false;
                mSelectId = viewHolder.getAdapterPosition();
                longPressTime = System.currentTimeMillis();
                mHandler.removeCallbacks(enterLongPress);
                if (mItemTouchHelper.isEnableClickDrag()) {
                    mHandler.postDelayed(enterLongPress, mLongTime);
                }
            } else if (actionState == ItemTouchHelper.ACTION_STATE_IDLE) {
                endLongPressMode();
                if (getOnDragListener() != null) {
                    getOnDragListener().onDragEnd();
                }
            } else if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                endLongPressMode();
            }
        }

        private Runnable enterLongPress = new Runnable() {
            @Override
            public void run() {
                if (System.currentTimeMillis() - longPressTime >= mLongTime) {
                    mLongPressMode = true;
                    if (!isLongPressCancel) {
                        if (getOnDragListener() != null && mSelectId >= 0) {
                            getOnDragListener().onDragLongPress(mSelectId);
                        }
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
    //endregion

    private class ItemTouchHelperGestureListener extends GestureDetector.SimpleOnGestureListener {

        ItemTouchHelperGestureListener() {
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

        private void startDrag(MotionEvent e) {
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
}