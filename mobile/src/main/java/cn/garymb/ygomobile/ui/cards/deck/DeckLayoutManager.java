package cn.garymb.ygomobile.ui.cards.deck;


import android.content.Context;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class DeckLayoutManager extends GridLayoutManager {
    private Context context;

    public DeckLayoutManager(Context context, final int span) {
        super(context, span);
        this.context = context;
        setSpanSizeLookup(new SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                if (DeckItemUtils.isLabel(position)) {
                    return span;
                }
                return 1;
            }
        });
    }

    @Override
    public void onLayoutChildren(RecyclerView.Recycler recycler, RecyclerView.State state) {
        try {
            super.onLayoutChildren(recycler, state);
        }catch (Exception e){

        }
    }
}
