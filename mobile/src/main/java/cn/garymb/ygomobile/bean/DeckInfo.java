package cn.garymb.ygomobile.bean;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.ui.cards.deck.DeckUtils;
import cn.garymb.ygomobile.utils.CardSort;
import cn.garymb.ygomobile.utils.MD5Util;
import ocgcore.data.Card;

public class DeckInfo {
    public enum Type {
        Main,
        Extra,
        Side,
    }

    private final List<Card> mainCards;
    private final List<Card> extraCards;
    private final List<Card> sideCards;

    public File source;

    private int mainCount, extraCount, sideCount;

    public DeckInfo() {
        mainCards = new ArrayList<>();
        extraCards = new ArrayList<>();
        sideCards = new ArrayList<>();
    }

    public void move(Type type, int from, int to) {
        if (from == to) return;
        List<Card> list;
        if (type == Type.Main) {
            list = mainCards;
        } else if (type == Type.Extra) {
            list = extraCards;
        } else {
            list = sideCards;
        }
        Card c = list.remove(from);
        if (from > to) {
            list.add(to, c);
        } else {
            list.add(to, c);
        }
    }

    public Card removeMain(int index) {
        Card card = getMainCard(index);
        if (removeMain(card)) {
            return card;
        }
        return null;
    }

    public Card removeExtra(int index) {
        Card card = getExtraCard(index);
        if (removeExtra(card)) {
            return card;
        }
        return null;
    }

    public Card removeSide(int index) {
        Card card = getSideCard(index);
        if (removeSide(card)) {
            return card;
        }
        return null;
    }

    public boolean removeMain(Card c) {
        if (c != null && mainCards.remove(c)) {
            mainCount--;
            return true;
        }
        return false;
    }

    public boolean removeExtra(Card c) {
        if (c != null && extraCards.remove(c)) {
            extraCount--;
            return true;
        }
        return false;
    }

    public boolean removeSide(Card c) {
        if (c != null && sideCards.remove(c)) {
            sideCount--;
            return true;
        }
        return false;
    }

    public boolean addMainCards(Card card) {
        return addMainCards(-1, card);
    }

    public boolean addMainCards(int index, Card card) {
        if (card != null && mainCount < Constants.DECK_MAIN_MAX) {
            if (index >= 0 && index <= mainCards.size()) {
                this.mainCards.add(index, card);
            } else {
                this.mainCards.add(card);
            }
            mainCount++;
            return true;
        }
        return false;
    }

    public boolean addExtraCards(Card card) {
        return addExtraCards(-1, card);
    }

    public boolean addExtraCards(int index, Card card) {
        if (card != null && extraCount < Constants.DECK_EXTRA_MAX) {
            if (index >= 0 && index <= extraCards.size()) {
                this.extraCards.add(index, card);
            } else {
                this.extraCards.add(card);
            }
            extraCount++;
            return true;
        }
        return false;
    }

    public boolean addSideCards(Card card) {
        return addSideCards(-1, card);
    }

    public boolean addSideCards(int index, Card card) {
        if (card != null && sideCount < Constants.DECK_SIDE_MAX) {
            if (index >= 0 && index <= sideCards.size()) {
                this.sideCards.add(index, card);
            } else {
                this.sideCards.add(card);
            }
            sideCount++;
            return true;
        }
        return false;
    }

    public void setMainCards(Collection<Card> mainCards) {
        this.mainCards.clear();
        if (mainCards != null) {
            this.mainCards.addAll(mainCards);
        }
        mainCount = Math.min(mainCards.size(), Constants.DECK_MAIN_MAX);
    }

    public void setExtraCards(Collection<Card> extraCards) {
        this.extraCards.clear();
        if (extraCards != null) {
            this.extraCards.addAll(extraCards);
        }
        extraCount = Math.min(extraCards.size(), Constants.DECK_EXTRA_MAX);
    }

    public void update(DeckInfo deck) {
        setMainCards(deck.mainCards);
        setExtraCards(deck.extraCards);
        setSideCards(deck.sideCards);
    }

    public void setSideCards(Collection<Card> sideCards) {
        this.sideCards.clear();
        if (sideCards != null) {
            this.sideCards.addAll(sideCards);
        }
        sideCount = Math.min(sideCards.size(), Constants.DECK_SIDE_MAX);
    }

    public int getMainCount() {
        return mainCount;
    }

    public int getExtraCount() {
        return extraCount;
    }

    public int getSideCount() {
        return sideCount;
    }


    public Card getMainCard(int index) {
        if (index >= 0 && index < getMainCount()) {
            return mainCards.get(index);
        }
        return null;
    }

    public Card getExtraCard(int index) {
        if (index >= 0 && index < getExtraCount()) {
            return extraCards.get(index);
        }
        return null;
    }

    public int getCardUseCount(int code) {
        int count = 0;
        for (Card card : mainCards) {
            if (code == card.Code) {
                count++;
            }
        }
        for (Card card : extraCards) {
            if (code == card.Code) {
                count++;
            }
        }
        for (Card card : sideCards) {
            if (code == card.Code) {
                count++;
            }
        }
        return count;
    }

    public String makeMd5() {
        return MD5Util.getStringMD5(DeckUtils.getDeckString(this));
    }

    public Card getSideCard(int index) {
        if (index >= 0 && index < getSideCount()) {
            return sideCards.get(index);
        }
        return null;
    }

    public void unSort() {
        Random random = new Random(System.currentTimeMillis());
        int count = mainCount;
        for (int i = 0; i < Constants.UNSORT_TIMES; i++) {
            int index1 = random.nextInt(count);
            int index2 = random.nextInt(count);
            if (index1 != index2) {
                int sp = (count - Math.max(index1, index2));
                int c = sp > 1 ? random.nextInt(sp - 1) : 1;
                for (int j = 0; j < c; j++) {
                    Collections.swap(mainCards, index1 + j, index2 + j);
                }
            }
        }
    }

    private boolean comp(Card c1, Card c2) {
        return CardSort.ASC.compare(c1, c2) < 0;
    }

    public void sortAll() {
        sortMain();
        sortExtra();
        sortSide();
    }

    public void sortMain() {
        sort(mainCards);
    }

    public void sortExtra() {
        sort(extraCards);
    }

    public void sortSide() {
        sort(sideCards);
    }

    private int sort(List<Card> cards) {
        int len = cards.size();
        for (int i = 0; i < len - 1; i++) {
            for (int j = 0; j < len - 1 - i; j++) {
                Card d1 = cards.get(j);
                Card d2 = cards.get(j + 1);
                if (comp(d1, d2)) {
                    Collections.swap(cards, j, j + 1);
                }
            }
        }
        return len;
    }

    @Override
    public String toString() {
        return "DeckInfo{" +
                "mainCards=" + mainCards.size() +
                ", extraCards=" + extraCards.size() +
                ", sideCards=" + sideCards.size() +
                '}';
    }

    public String toLongString() {
        return "DeckInfo{" +
                "mainCards=" + mainCards +
                ", extraCards=" + extraCards +
                ", sideCards=" + sideCards +
                '}';
    }

    public Deck toDeck() {
        Deck deck = new Deck();
        for (Card card : mainCards) {
            deck.addMain(card.Code);
        }
        for (Card card : extraCards) {
            deck.addExtra(card.Code);
        }
        for (Card card : sideCards) {
            deck.addSide(card.Code);
        }
        return deck;
    }

    public List<Card> getMainCards() {
        return mainCards;
    }

    public List<Card> getExtraCards() {
        return extraCards;
    }

    public List<Card> getSideCards() {
        return sideCards;
    }
}
