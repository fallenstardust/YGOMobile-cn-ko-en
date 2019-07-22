package cn.garymb.ygomobile.ui.widget;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.cards.deck.ImageTop;
import ocgcore.data.Card;
import ocgcore.data.LimitList;

public class CardGroupView extends FrameLayout {
    private int mMaxLines = 1;
    private int mLineLimit = 10;
    private int mOrgLineLimit = 10;
    private int mLineMaxCount = 15;
    private int mCardWidth = 177, mCardHeight = 255;
    private boolean mPausePadding;

    //region init
    public CardGroupView(@NonNull Context context) {
        this(context, null);
    }

    public CardGroupView(@NonNull Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CardGroupView(@NonNull Context context, @Nullable AttributeSet attrs, @AttrRes int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        if (attrs != null) {
            TypedArray array = context.obtainStyledAttributes(attrs, R.styleable.CardGroupView);
            if (array != null) {
                mMaxLines = array.getInteger(R.styleable.CardGroupView_lines, mMaxLines);
                mCardWidth = array.getInteger(R.styleable.CardGroupView_card_width, mCardWidth);
                mCardHeight = array.getInteger(R.styleable.CardGroupView_card_height, mCardHeight);
                mLineLimit = array.getInteger(R.styleable.CardGroupView_line_limit, mLineLimit);
                mLineMaxCount = array.getInteger(R.styleable.CardGroupView_line_max_count, mLineMaxCount);
                mOrgLineLimit = mLineLimit;
            }
        }
    }

    public int getLineByIndex(int index) {
        return index / mLineLimit;
    }

    //
    public int getLineStartByIndex(int index) {
        return index % mLineLimit;
    }

    public int getMaxCardCount() {
        return mMaxLines * mLineMaxCount;
    }

    public void setCardSize(int cardWidth, int cardHeight) {
        mCardWidth = cardWidth;
        mCardHeight = cardHeight;
    }

    public int getMaxLines() {
        return mMaxLines;
    }

    public int getLineLimit() {
        return mLineLimit;
    }

    public void setLineLimit(int lines, int lineLimit, int lineMaxCount) {
        mMaxLines = lines;
        mLineLimit = lineLimit;
        mLineMaxCount = lineMaxCount;
    }

    public int getLineMaxCount() {
        return mLineMaxCount;
    }

    private void init(int line) {
        mMaxLines = line;
    }
    //endregion

    //region add/remove
    @Override
    public void addView(View child) {
        addView(child, getChildCount());
    }

    @Override
    public void addView(View child, int index) {
        if (index < 0) {
            index = getChildCount();
        }
        addView(child, index, getLayoutParamsAt(index));
    }

    @Override
    public void addView(View child, ViewGroup.LayoutParams params) {
        addView(child, getChildCount(), params);
    }

    @Override
    public void removeView(View view) {
        removeViewAt(indexOfChild(view));
    }

    @Override
    public void removeViewAt(int index) {
        View view = getChildAt(index);
        if (view != null) {
            super.removeViewAt(index);
            onCardRemoved((CardView) view, index);
        }
    }

    @Override
    public void addView(View child, int index, ViewGroup.LayoutParams params) {
        super.addView(child, index, params);
        onCardAdd((CardView) child, index);
    }
    //endregion

    //region params
    public void refreshLayout() {
        refreshLayoutParams(0, getChildCount());
    }

    public void refreshLayout(int index, int count) {
        refreshLayoutParams(index, count);
    }

    private void refreshLayoutParams(int count) {
        refreshLayoutParams(0, count);
    }

    private void refreshLayoutParams(int index, int count) {
        mLineLimit = (int) Math.max(mOrgLineLimit, Math.ceil((float) count / (float) mMaxLines));
        if (mPausePadding) return;
        int p = 0;
        if (mLineLimit > mOrgLineLimit) {
            p = -(int) Math.ceil((double) ((mLineLimit - mOrgLineLimit) * mCardWidth) / (float) (mLineLimit - 1));
        }
        int childcount = getChildCount();
        for (int i = index; i < childcount && count > 0; i++, count--) {
            View view = getChildAt(i);
            LayoutParams lp = (LayoutParams) view.getLayoutParams();
            if (lp == null) {
                lp = getLayoutParamsAt(i, 0);
            } else {
                fillLayoutParams(i, lp, p);
            }
            view.setLayoutParams(lp);
        }
    }

    private void fillLayoutParams(int index, LayoutParams layoutParams, int p) {
        int line = getLineByIndex(index);
        int x = getLineStartByIndex(index);
        layoutParams.topMargin = line * mCardHeight;
        layoutParams.leftMargin = x * (mCardWidth + p);
    }

    private LayoutParams getLayoutParamsAt(int index) {
        return getLayoutParamsAt(index, 0);
    }

    private LayoutParams getLayoutParamsAt(int index, int p) {
        LayoutParams layoutParams = new LayoutParams(mCardWidth, mCardHeight);
        fillLayoutParams(index, layoutParams, p);
        return layoutParams;
    }
    //endregion

    private void onCardAdd(CardView cardView, int index) {
        refreshLayoutParams(getChildCount());
    }

    private void onCardRemoved(CardView cardView, int index) {
        refreshLayoutParams(getChildCount());
    }

    public int addCards(List<Card> cards) {
        int max = Math.min(getChildCount() + cards.size(), getMaxCardCount());
        mPausePadding = true;
        refreshLayoutParams(max);
        int count = max - getChildCount();
        for (int i = 0; i < count; i++) {
            Card card = cards.get(i);
            addCard(card);
        }
        mPausePadding = false;
        return count;
    }

    public boolean addCard(Card card) {
        return addCard(card, -1);
    }

    public boolean addCard(Card card, int index) {
        int count = getChildCount();
        if (count >= getMaxCardCount()) {
            return false;
        }
        if (index < 0) {
            index = getChildCount();
        }
        if (!mPausePadding) {
            refreshLayoutParams(count + 1);
        }
        CardView cardView = new CardView(getContext());
        cardView.showCard(card);
        addView(cardView, index);
        return true;
    }

    public void removeAllCards() {
        removeAllViews();
    }

    public int removeCards(List<Card> cards) {
        if (cards == null) return 0;
        int r = 0;
        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            CardView cardView = (CardView) getChildAt(i);
            if (cardView != null) {
                int index = cards.indexOf(cardView.getCard());
                if (index >= 0) {
                    removeViewAt(i);
                    cards.remove(index);
                    r++;
                    if (cards.size() == 0) {
                        break;
                    }
                }
            }
        }
        return r;
    }

    public boolean removeCard(Card card) {
        if (card == null) return false;
        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            CardView cardView = (CardView) getChildAt(i);
            if (cardView != null && card.equals(cardView.getCard())) {
                removeViewAt(i);
                return true;
            }
        }
        return false;
    }


    public void updateTopImage(ImageTop imageTop, LimitList limitList) {
        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            CardView cardView = (CardView) getChildAt(i);
            cardView.updateLimit(imageTop, limitList);
        }
    }
}
