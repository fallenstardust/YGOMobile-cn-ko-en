package cn.garymb.ygomobile.ui.cards.deck2;


import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.ItemTouchHelperPlus;
import androidx.recyclerview.widget.RecyclerView;

class DeckHelperCallback extends ItemTouchHelperPlus.Callback2 {
    private IDeckLayout mDeckAdapter;

    DeckHelperCallback(IDeckLayout deckAdapter) {
        mDeckAdapter = deckAdapter;
    }

    private boolean isLabel(int position) {
        return mDeckAdapter.isLabel(position);
    }

    @Override
    public int getMovementFlags(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
        int id = viewHolder.getAdapterPosition();
        if (isLabel(id)) {
            return makeMovementFlags(0, 0);
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
    public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
        return mDeckAdapter.moveItem(viewHolder.getAdapterPosition(), target.getAdapterPosition());
    }

    @Override
    public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {

    }
}
