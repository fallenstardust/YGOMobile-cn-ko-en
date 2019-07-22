package androidx.appcompat.widget;


import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.view.GestureDetectorCompat;
import androidx.recyclerview.widget.RecyclerView;

public class RecyclerViewItemListener extends RecyclerView.SimpleOnItemTouchListener {
    private GestureDetectorCompat gestureDetector;

    public interface OnItemListener {

        void onItemClick(View view, int pos);

        void onItemLongClick(View view, int pos);

        void onItemDoubleClick(View view, int pos);
    }

    public RecyclerViewItemListener(final RecyclerView recyclerView, final OnItemListener listener) {
        gestureDetector = new GestureDetectorCompat(recyclerView.getContext(),
                new GestureDetector.SimpleOnGestureListener() {
                    @Override
                    public boolean onSingleTapUp(MotionEvent e) {
                        if (listener != null) {
                            View childView = recyclerView.findChildViewUnder(e.getX(), e.getY());
                            if (childView != null) {
                                listener.onItemClick(childView, recyclerView.getChildAdapterPosition(childView));
                            }
                        }
                        return true;
                    }

                    @Override
                    public boolean onDoubleTap(MotionEvent e) {
                        if (listener != null) {
                            View childView = recyclerView.findChildViewUnder(e.getX(), e.getY());
                            if (childView != null) {
                                listener.onItemDoubleClick(childView, recyclerView.getChildAdapterPosition(childView));
                            }
                        }
                        return true;
                    }

                    @Override
                    public void onLongPress(MotionEvent e) {
                        if (listener != null) {
                            View childView = recyclerView.findChildViewUnder(e.getX(), e.getY());
                            if (childView != null) {
                                listener.onItemLongClick(childView,
                                        recyclerView.getChildAdapterPosition(childView));
                            }
                        }
                    }
                });
    }

    @Override
    public boolean onInterceptTouchEvent(RecyclerView rv, MotionEvent e) {
        gestureDetector.onTouchEvent(e);
        return false;
    }

}
