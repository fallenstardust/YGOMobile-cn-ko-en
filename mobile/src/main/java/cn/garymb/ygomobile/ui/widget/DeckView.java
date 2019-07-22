package cn.garymb.ygomobile.ui.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;

import cn.garymb.ygomobile.bean.DeckInfo;
import cn.garymb.ygomobile.ui.cards.deck.ImageTop;
import cn.garymb.ygomobile.ui.cards.deck.LabelInfo;
import ocgcore.data.Card;
import ocgcore.data.LimitList;

public class DeckView extends LinearLayout {
    private final DeckLabel mMainLabel, mExtraLabel, mSideLabel;
    private final CardGroupView mMainGroup, mExtraGroup, mSideGroup;
    private final LabelInfo mLabelInfo;
    private LimitList mLimitList;
    private final ImageTop mImageTop;
    private boolean mAutoSort, mEditMode, mLimitChanged;

    //region init
    public DeckView(Context context) {
        this(context, null);
    }

    public DeckView(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DeckView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOrientation(VERTICAL);
        mImageTop = new ImageTop(getContext());
        mLabelInfo = new LabelInfo(context);
        mMainLabel = new DeckLabel(context);
        mExtraLabel = new DeckLabel(context);
        mSideLabel = new DeckLabel(context);
        mMainGroup = new CardGroupView(context);
        mExtraGroup = new CardGroupView(context);
        mSideGroup = new CardGroupView(context);
        int cardWidth = 0;
        int cardHeight = 0;
        if (cardWidth <= 0) {
            int width = (getMeasuredWidth() - getPaddingLeft() - getPaddingRight());
            cardWidth = width / 10;
        }
        if (cardWidth <= 0) {
            cardWidth = (getResources().getDisplayMetrics().widthPixels - getPaddingLeft() - getPaddingRight()) / 10;
        }
        cardHeight = Math.round((255.0f / 177.0f) * cardWidth);
        mMainGroup.setCardSize(cardWidth, cardHeight);
        mMainGroup.setLineLimit(4, 10, 15);

        mExtraGroup.setCardSize(cardWidth, cardHeight);
        mExtraGroup.setLineLimit(1, 10, 15);

        mSideGroup.setCardSize(cardWidth, cardHeight);
        mSideGroup.setLineLimit(1, 10, 15);

        addView(mMainLabel, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        addView(mMainGroup, new LayoutParams(LayoutParams.MATCH_PARENT, cardHeight * 4));
        addView(mExtraLabel, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        addView(mExtraGroup, new LayoutParams(LayoutParams.MATCH_PARENT, cardHeight));
        addView(mSideLabel, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        addView(mSideGroup, new LayoutParams(LayoutParams.MATCH_PARENT, cardHeight));
    }

    public ImageTop getImageTop() {
        return mImageTop;
    }

    public boolean isAutoSort() {
        return mAutoSort;
    }

    public void setAutoSort(boolean autoSort) {
        mAutoSort = autoSort;
    }

    public boolean isEditMode() {
        return mEditMode;
    }

    public void setEditMode(boolean editMode) {
        mEditMode = editMode;
    }

    public LimitList getLimitList() {
        return mLimitList;
    }

    public void setLimitList(LimitList limitList) {
        mLimitList = limitList;
        mLimitChanged = true;
    }
    //endregion

    //region refresh
    public void setDeck(DeckInfo deck) {
        mMainGroup.removeAllCards();
        mMainGroup.addCards(deck.getMainCards());
        mExtraGroup.removeAllCards();
        mExtraGroup.addCards(deck.getExtraCards());
        mSideGroup.removeAllCards();
        mSideGroup.addCards(deck.getSideCards());
        mLabelInfo.update(deck);
    }

    public boolean addMainCards(Card card) {
        if (mMainGroup.addCard(card)) {
            mLabelInfo.updateMain(card, false);
            mMainLabel.setText(mLabelInfo.getMainString());
            return true;
        }
        return false;
    }

    public boolean addExtraCards(Card card) {
        if (mExtraGroup.addCard(card)) {
            mLabelInfo.updateExtra(card, false);
            mExtraLabel.setText(mLabelInfo.getExtraString());
            return true;
        }
        return false;
    }

    public boolean addSideCards(Card card) {
        if (mSideGroup.addCard(card)) {
            mLabelInfo.updateSide(card, false);
            mSideLabel.setText(mLabelInfo.getSideString());
            return true;
        }
        return false;
    }

    public void notifyDataSetChanged() {
        resetLastChoose();
        mMainGroup.refreshLayout();
        mExtraGroup.refreshLayout();
        mSideGroup.refreshLayout();
        mMainLabel.setText(mLabelInfo.getMainString());
        mExtraLabel.setText(mLabelInfo.getExtraString());
        mSideLabel.setText(mLabelInfo.getSideString());
        if (mLimitChanged) {
            mMainGroup.updateTopImage(getImageTop(), mLimitList);
            mExtraGroup.updateTopImage(getImageTop(), mLimitList);
            mSideGroup.updateTopImage(getImageTop(), mLimitList);
        }
        mLimitChanged = false;
    }
    //endregion

    public void deleteChoose() {
        //TODO
    }

    public void resetLastChoose() {
        //TODO
    }

    public DeckInfo getDeckInfo() {
        //TODO
        return new DeckInfo();
    }

    public void unSort() {
        //TODO
    }

    public void sort() {
        //TODO
    }


}
