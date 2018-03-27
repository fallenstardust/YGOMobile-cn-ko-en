package cn.garymb.ygomobile.ui.cards.deck;

import cn.garymb.ygomobile.Constants;
import ocgcore.data.Card;

public class DeckItem {
//    public final static int HeadView = 0;
    public final static int MainLabel = 0;
    public final static int MainStart = MainLabel + 1;
    public final static int MainEnd = MainStart + Constants.DECK_MAIN_MAX - 1;

    public final static int ExtraLabel = MainEnd + 1;
    public final static int ExtraStart = ExtraLabel + 1;
    public final static int ExtraEnd = ExtraStart + Constants.DECK_EXTRA_COUNT - 1;
    public final static int SideLabel = ExtraEnd + 1;
    public final static int SideStart = SideLabel + 1;
    public final static int SideEnd = SideStart + Constants.DECK_SIDE_COUNT - 1;


    private DeckItemType mType;
    private Card mCardInfo;

    public DeckItem() {
        mType = DeckItemType.Space;
    }

    public DeckItem(Card cardInfo, DeckItemType type) {
        mType = type;
        mCardInfo = cardInfo;
    }

    public DeckItem(DeckItem deckItem) {
        set(deckItem);
    }

    public void set(DeckItem deckItem) {
        mType = deckItem.getType();
        mCardInfo = deckItem.getCardInfo();
    }

    public void setType(DeckItemType type) {
        mType = type;
    }

    public DeckItem(DeckItemType type) {
        this.mType = type;
    }

    public Card getCardInfo() {
        return mCardInfo;
    }

    public DeckItemType getType() {
        return mType;
    }
}
