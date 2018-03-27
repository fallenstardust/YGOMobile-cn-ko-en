package cn.garymb.ygomobile.ui.cards.deck;

import android.content.Context;
import android.support.v7.widget.GridLayoutManager;

public class DeckLayoutManager2 extends GridLayoutManager {

    private static final int DEF_LINE_COUNT = 10;
    private static final int DEF_LINE_MAX_COUNT = 15;

    public DeckLayoutManager2(Context context, int span) {
        super(context, span);
        setSpanSizeLookup(new SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                if (DeckItemUtils.isLabel(position)) {
                    return getChildCount();
                }
                return 1;
            }
        });
    }

}
