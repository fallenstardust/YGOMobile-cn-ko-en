package cn.garymb.ygomobile.loader;

import android.util.SparseArray;

import java.util.List;

import ocgcore.data.Card;

public interface ICardLoader {
    List<Card> sort(List<Card> cards);
    boolean isOpen();
    SparseArray<Card> readCards(List<Integer> ids,  boolean isSorted);
}
