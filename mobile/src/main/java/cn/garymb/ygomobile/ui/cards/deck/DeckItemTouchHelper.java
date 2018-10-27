package cn.garymb.ygomobile.ui.cards.deck;

import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.support.v7.widget.helper.ItemTouchHelperPlus;
import android.util.Log;

import java.util.List;

import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.lite.R;

import static android.support.v7.widget.helper.ItemTouchHelper.ACTION_STATE_DRAG;
import static android.support.v7.widget.helper.ItemTouchHelper.ACTION_STATE_IDLE;

public class DeckItemTouchHelper extends ItemTouchHelperPlus.Callback {
    private DeckDrager mDeckDrager;
    private static final String TAG = "drag";
    private static final boolean DEBUG = false;
    private DeckAdapater deckAdapater;

    public DeckItemTouchHelper(DeckAdapater deckAdapater) {
        this.mDeckDrager = new DeckDrager(deckAdapater);
        this.deckAdapater = deckAdapater;
        int size = (int) deckAdapater.getContext().getResources().getDimension(R.dimen.drag_rect);
        //长按手抖范围
        setDragSize(size, size);
        //长按时间
        setLongTime(Constants.LONG_PRESS_DRAG);
//        setDragSize();
    }

    private int Min_Pos = -10;

    @Override
    public boolean canDropOver(RecyclerView recyclerView, RecyclerView.ViewHolder current,
                               RecyclerView.ViewHolder target) {
        int id = target.getAdapterPosition();
        if (isLongPressMode()) {
            return false;
        }
        return !DeckItemUtils.isLabel(id);
    }

    /**
     * 控制哪些可以拖拽
     */
    @Override
    public int getMovementFlags(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
        int id = viewHolder.getAdapterPosition();
        if (DeckItemUtils.isLabel(id)) {
            return makeMovementFlags(0, 0);
        }
        if (DeckItemUtils.isMain(id)) {
            if (id >= DeckItem.MainStart + deckAdapater.getMainCount()) {
                return makeMovementFlags(0, 0);
            }
        } else if (DeckItemUtils.isExtra(id)) {
            if (id >= DeckItem.ExtraStart + deckAdapater.getExtraCount()) {
                return makeMovementFlags(0, 0);
            }
        } else if (DeckItemUtils.isSide(id)) {
            if (id >= DeckItem.SideStart + deckAdapater.getSideCount()) {
                return makeMovementFlags(0, 0);
            }
        }
        int dragFlags;
        if (recyclerView.getLayoutManager() instanceof GridLayoutManager) {
            dragFlags = ItemTouchHelper.UP | ItemTouchHelper.DOWN | ItemTouchHelper.RIGHT | ItemTouchHelper.LEFT;
        } else {
            dragFlags = ItemTouchHelper.UP | ItemTouchHelper.DOWN;
        }
        return makeMovementFlags(dragFlags, 0);
    }

    @Override
    public RecyclerView.ViewHolder chooseDropTarget(RecyclerView.ViewHolder selected,
                                                    List<RecyclerView.ViewHolder> dropTargets, int curX, int curY) {
        RecyclerView.ViewHolder viewHolder = super.chooseDropTarget(selected, dropTargets, curX, curY);
        if (viewHolder != null) {
            int id = viewHolder.getAdapterPosition();
            if (viewHolder instanceof DeckViewHolder) {
                DeckViewHolder deckholder = (DeckViewHolder) viewHolder;
                if (deckholder.getItemType() == DeckItemType.MainLabel
                        || deckholder.getItemType() == DeckItemType.SideLabel
                        || deckholder.getItemType() == DeckItemType.ExtraLabel) {
//                Log.d("kk", "move is label or space " + id);
                    return null;
                }
            } else {
                if (DeckItemUtils.isLabel(id)) {
//                Log.d("kk", "move is label " + id);
                    if (isLongPressMode()) {
                        return viewHolder;
                    }
                    return null;
                }
            }
        }
        return viewHolder;
    }

    @Override
    public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
        super.onSelectedChanged(viewHolder, actionState);
        if (actionState == ACTION_STATE_DRAG) {
            mDeckDrager.onDragStart();
            if (Constants.DEBUG)
                Log.d("kk", "start drag");
        } else if (actionState == ACTION_STATE_IDLE) {
            mDeckDrager.onDragEnd();
        }
    }

    @Override
    public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
        if (viewHolder.getAdapterPosition() < 0) {
            return false;
        }
        return mDeckDrager.move((DeckViewHolder) viewHolder, (DeckViewHolder) target);
    }

    public void remove(int id) {
        mDeckDrager.delete(id);
    }

    @Override
    public void onMoved(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, int fromPos, RecyclerView.ViewHolder target, int toPos, int x, int y) {
        super.onMoved(recyclerView, viewHolder, fromPos, target, toPos, x, y);
    }

    @Override
    public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
    }
}
