package cn.garymb.ygomobile.ui.cards.deck;

import android.content.Context;

import cn.garymb.ygomobile.bean.DeckInfo;
import cn.garymb.ygomobile.lite.R;
import ocgcore.data.Card;
import ocgcore.enums.CardType;

public class LabelInfo {
    private int mMainCount = 0;
    private int mExtraCount = 0;
    private int mSideCount = 0;
    private int mMainMonsterCount = 0;
    private int mMainSpellCount = 0;
    private int mMainTrapCount = 0;
    private int mExtraFusionCount = 0;
    private int mExtraXyzCount = 0;
    private int mExtraSynchroCount = 0;
    private int mExtraLinkCount = 0;
    private int mSideMonsterCount = 0;
    private int mSideSpellCount = 0;
    private int mSideTrapCount = 0;
    private Context mContext;

    public LabelInfo(Context context) {
        mContext = context;
    }

    public void reset() {
        mMainCount = 0;
        mExtraCount = 0;
        mSideCount = 0;
        mMainMonsterCount = 0;
        mMainSpellCount = 0;
        mMainTrapCount = 0;
        mExtraFusionCount = 0;
        mExtraXyzCount = 0;
        mExtraSynchroCount = 0;
        mExtraLinkCount = 0;
        mSideMonsterCount = 0;
        mSideSpellCount = 0;
        mSideTrapCount = 0;
    }

    public Context getContext() {
        return mContext;
    }

    public String getMainString() {
        return getContext().getString(R.string.deck_main, mMainCount, mMainMonsterCount, mMainSpellCount, mMainTrapCount);
    }

    public String getExtraString() {
        return getContext().getString(R.string.deck_extra, mExtraCount, mExtraFusionCount, mExtraSynchroCount, mExtraXyzCount, mExtraLinkCount);
    }

    public String getSideString() {
        return getContext().getString(R.string.deck_side, mSideCount, mSideMonsterCount, mSideSpellCount, mSideTrapCount);
    }

    public void update(DeckInfo deckInfo) {
        reset();
        for (Card card : deckInfo.getMainCards()) {
            updateMain(card, false);
        }
        for (Card card : deckInfo.getExtraCards()) {
            updateExtra(card, false);
        }
        for (Card card : deckInfo.getSideCards()) {
            updateSide(card, false);
        }
    }

    public void updateMain(Card card, boolean remove) {
        if (remove) {
            mMainCount--;
            if (card.isType(CardType.Spell)) {
                mMainSpellCount--;
            } else if (card.isType(CardType.Trap)) {
                mMainTrapCount--;
            } else {
                mMainMonsterCount--;
            }
        } else {
            mMainCount++;
            if (card.isType(CardType.Spell)) {
                mMainSpellCount++;
            } else if (card.isType(CardType.Trap)) {
                mMainTrapCount++;
            } else {
                mMainMonsterCount++;
            }
        }
    }

    public void updateExtra(Card card, boolean remove) {
        if (remove) {
            mExtraCount--;
            if (card.isType(CardType.Fusion)) {
                mExtraFusionCount--;
            } else if (card.isType(CardType.Synchro)) {
                mExtraSynchroCount--;
            } else if (card.isType(CardType.Xyz)) {
                mExtraXyzCount--;
            } else if (card.isType(CardType.Link)) {
                mExtraLinkCount--;
            }
        } else {
            mExtraCount++;
            if (card.isType(CardType.Fusion)) {
                mExtraFusionCount++;
            } else if (card.isType(CardType.Synchro)) {
                mExtraSynchroCount++;
            } else if (card.isType(CardType.Xyz)) {
                mExtraXyzCount++;
            } else if (card.isType(CardType.Link)) {
                mExtraLinkCount++;
            }
        }
    }

    public void updateSide(Card card, boolean remove) {
        if (remove) {
            mSideCount--;
            if (card.isType(CardType.Spell)) {
                mSideSpellCount--;
            } else if (card.isType(CardType.Trap)) {
                mSideTrapCount--;
            } else {
                mSideMonsterCount--;
            }
        } else {
            mSideCount++;
            if (card.isType(CardType.Spell)) {
                mSideSpellCount++;
            } else if (card.isType(CardType.Trap)) {
                mSideTrapCount++;
            } else {
                mSideMonsterCount++;
            }
        }
    }
}
