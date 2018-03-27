package cn.garymb.ygomobile.ui.cards;

import ocgcore.data.Card;

public interface CardListProvider {
    int getCardsCount();

    Card getCard(int posotion);
}
