package cn.garymb.ygomobile.ui.cards.deck2;


import android.graphics.Rect;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

class DeckItemDecoration extends RecyclerView.ItemDecoration {
    private IDeckLayout mDeckLayout;

    DeckItemDecoration(IDeckLayout deckLayout) {
        mDeckLayout = deckLayout;
    }

    @Override
    public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
        final int LINE_COUNT = mDeckLayout.getLineLimitCount();
        final int CARD_COUNT = mDeckLayout.getLineCardCount();
        int position = ((RecyclerView.LayoutParams) view.getLayoutParams()).getViewLayoutPosition();
        if (mDeckLayout.isLabel(position)) {
            super.getItemOffsets(outRect, view, parent, state);
            return;
        }
        float w = mDeckLayout.getWidth10() - mDeckLayout.getWidth15();
        int limit;
        if (mDeckLayout.isMain(position)) {
            position = mDeckLayout.getMainIndex(position);
            limit = mDeckLayout.getMainLimit();

        } else if (mDeckLayout.isExtra(position)) {
            position = mDeckLayout.getExtraIndex(position);
            limit = mDeckLayout.getExtraLimit();//10
        } else if (mDeckLayout.isSide(position)) {
            position = mDeckLayout.getSideIndex(position);
            limit = mDeckLayout.getSideLimit();//10
        } else {
            return;
        }
        float _w = w / (limit - 1.0f);
        float w2;
        if (limit < LINE_COUNT - 1) {
            //10-13
            w = mDeckLayout.getWidth15() * (LINE_COUNT - limit);
            w2 = (w / (limit - 1.0f)) - _w;
        } else if (limit == LINE_COUNT - 1) {
            //14
            w = mDeckLayout.getWidth15() * (LINE_COUNT - limit);
            _w = _w / 64.0f * 65.0f;
            w2 = (w / (limit - 1.0f)) - _w;
        } else {
            //15
            w = mDeckLayout.getWidth15() * (LINE_COUNT - limit);
            _w = _w / 32.0f * 33.0f;
            w2 = (w / (limit - 1.0f)) - _w;
        }
        int linePos = position % limit;
        outRect.left = (int) (w2 * linePos);
    }
}
