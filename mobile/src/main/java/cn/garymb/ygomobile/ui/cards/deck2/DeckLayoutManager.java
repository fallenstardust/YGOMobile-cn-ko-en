package cn.garymb.ygomobile.ui.cards.deck2;

import android.content.Context;

import androidx.recyclerview.widget.GridLayoutManagerPlus;


class DeckLayoutManager extends GridLayoutManagerPlus {
    DeckLayoutManager(Context context, final int span, final IDeckLayout deckAdapter) {
        super(context, span);
        setSpanSizeLookup(new SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                if (deckAdapter.isLabel(position)) {
                    return span;
                } else if (deckAdapter.isMain(position)) {
                    position = deckAdapter.getMainIndex(position);
                    int limit = deckAdapter.getMainLimit();
                    if (position % limit == (limit - 1)) {
                        return span - limit + 1;
                    }
                } else if (deckAdapter.isExtra(position)) {
                    position = deckAdapter.getExtraIndex(position);
                    if (deckAdapter.getExtraCount() < deckAdapter.getLineCardCount()) {
                        if (position == deckAdapter.getExtraCount() - 1) {
                            return deckAdapter.getLineCardCount() - position;
                        }
                    }
                } else if (deckAdapter.isSide(position)) {

                }
                return 1;
            }
        });
    }
}
