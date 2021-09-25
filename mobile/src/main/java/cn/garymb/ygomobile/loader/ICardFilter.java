package cn.garymb.ygomobile.loader;

import ocgcore.data.Card;

public interface ICardFilter {
    boolean isValid(Card card);
}
