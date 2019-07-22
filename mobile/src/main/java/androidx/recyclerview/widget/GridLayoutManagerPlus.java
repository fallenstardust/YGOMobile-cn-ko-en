package androidx.recyclerview.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.lang.reflect.Field;

public class GridLayoutManagerPlus extends GridLayoutManager {
    public GridLayoutManagerPlus(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public GridLayoutManagerPlus(Context context, int spanCount) {
        super(context, spanCount);
    }

    public GridLayoutManagerPlus(Context context, int spanCount, int orientation, boolean reverseLayout) {
        super(context, spanCount, orientation, reverseLayout);
    }

    @Override
    public RecyclerView.LayoutParams generateDefaultLayoutParams() {
        if (mOrientation == HORIZONTAL) {
            return new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);
        } else {
            return new LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    @Override
    public RecyclerView.LayoutParams generateLayoutParams(Context c, AttributeSet attrs) {
        return new LayoutParams(c, attrs);
    }

    @Override
    public RecyclerView.LayoutParams generateLayoutParams(ViewGroup.LayoutParams lp) {
        if (lp instanceof ViewGroup.MarginLayoutParams) {
            return new LayoutParams((ViewGroup.MarginLayoutParams) lp);
        } else {
            return new LayoutParams(lp);
        }
    }

    public static class LayoutParams extends GridLayoutManager.LayoutParams {
        public LayoutParams(Context c, AttributeSet attrs) {
            super(c, attrs);
        }

        public LayoutParams(int width, int height) {
            super(width, height);
        }

        public LayoutParams(ViewGroup.MarginLayoutParams source) {
            super(source);
        }

        public LayoutParams(ViewGroup.LayoutParams source) {
            super(source);
        }

        public LayoutParams(RecyclerView.LayoutParams source) {
            super(source);
        }

        @Override
        public boolean isItemChanged() {
            return super.isItemChanged() || ((GridViewHolder) mViewHolder).isChanged();
        }
    }

    public static class GridViewHolder extends RecyclerView.ViewHolder {
        private Field mFlags;

        public GridViewHolder(View itemView) {
            super(itemView);
            try {
                mFlags = RecyclerView.ViewHolder.class.getDeclaredField("mFlags");
                mFlags.setAccessible(true);
            } catch (NoSuchFieldException e) {
                e.printStackTrace();
            }
        }

        private int getFlags() {
            if (mFlags != null) {
                try {
                    return (int) mFlags.get(this);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return 0;
        }

        @Override
        void offsetPosition(int offset, boolean applyToPreLayout) {
            super.offsetPosition(offset, applyToPreLayout);
            changed();
        }

        private void changed() {
            addFlags(FLAG_MOVED);
        }

        private boolean isChanged() {
            return (getFlags() & FLAG_MOVED) != 0;
        }
    }
}
