package cn.garymb.ygomobile.loader;

import android.text.TextUtils;
import android.util.Log;

import java.util.List;

import ocgcore.data.Card;
import ocgcore.enums.CardType;

class CardSearchInfo {
    //名字或者描述
    String word, prefixWord, suffixWord;
    int attribute;
    int level, ot, pscale=-1;
    long race, category;
    String atk, def;
    int linkKey;
    List<Integer> inCards;
    long[] types;
    long setcode;

    CardSearchInfo() {
    }

    List<Integer> getInCards() {
        return inCards;
    }

    public boolean check(Card card) {
        if (inCards != null && !inCards.contains(Integer.valueOf(card.Code))) {
            return false;
        }
        if (!TextUtils.isEmpty(word)) {
            if (!((card.Name != null && card.Name.contains(word))
                    || (card.Desc != null && card.Desc.contains(word)))) {
                return false;
            }
        } else if (!TextUtils.isEmpty(prefixWord) && !TextUtils.isEmpty(suffixWord)) {
            boolean has = false;
            if (card.Name != null) {
                int i1 = card.Name.indexOf(prefixWord);
                int i2 = card.Name.indexOf(suffixWord);
                if (i1 >= 0 && i2 >= 0 && i1 < i2) {
                    has = true;
                }
            }
            if (!has) {
                if (card.Desc != null) {
                    int i1 = card.Desc.indexOf(prefixWord);
                    int i2 = card.Desc.indexOf(suffixWord);
                    if (i1 >= 0 && i2 >= 0 && i1 < i2) {
                        has = true;
                    }
                }
            }
            if (!has) {
                return false;
            }
        }
        if (attribute != 0) {
            if (card.Attribute != attribute) {
                return false;
            }
        }
        if (level != 0) {
            if (card.getStar() != level) {
                return false;
            }
        }
        if (!TextUtils.isEmpty(atk)) {
            if (atk.contains("-")) {
                String[] atks = atk.split("-");
                if (!(i(atks[0]) <= card.Attack && card.Attack <= i(atks[1]))) {
                    return false;
                }
            } else {
                if (card.Attack != ((TextUtils.isDigitsOnly(atk) ? i(atk) : -2))) {
                    return false;
                }
            }
        }

        if (linkKey > 0) {
            if (!((card.Defense & linkKey) == linkKey && (card.isType(CardType.Link)))) {
                return false;
            }
        } else {
            if (!TextUtils.isEmpty(def)) {
                if (def.contains("-")) {
                    String[] defs = def.split("-");
                    if (!(i(defs[0]) <= card.Defense && card.Defense <= i(defs[1]))) {
                        return false;
                    }
                } else {
                    if (card.Defense != ((TextUtils.isDigitsOnly(def) ? i(def) : -2))) {
                        return false;
                    }
                }
            }
        }
        if (ot > 0) {
            if (card.Ot != ot) {
                return false;
            }
        }

        if (pscale != -1) {
            if (!((card.Level >> 16 & 255) == pscale || (card.Level >> 24 & 255) == pscale)) {
                return false;
            }
        }

        if (race != 0) {
            if (card.Race != race) {
                return false;
            }
        }
        if (category != 0) {
            if ((card.Category & category) != category) {
                return false;
            }
        }
        if (types.length > 0) {
            boolean st = false;
            for (long cardType : types) {
                if (cardType == CardType.Spell.value() || cardType == CardType.Trap.value()) {
                    st = true;
                    break;
                }
            }

            for (long type : types) {
                if (type > 0) {
                    if (st) {
                        //魔法
                        if (type == CardType.Normal.value()) {
                            //通常
                            if (card.isType(CardType.Normal)) {
                                //带通常的魔法陷阱
                                if (card.Type != (CardType.Spell.value() | CardType.Normal.value())
                                        && card.Type != (CardType.Trap.value() | CardType.Normal.value())) {
                                    return false;
                                }
                            } else {
                                //只有魔法/陷阱
                                if (card.Type != CardType.Spell.value() && card.Type != CardType.Trap.value())
                                    return false;
                            }
                            continue;
                        }
                    }
                    if ((card.Type & type) != type) {
                        return false;
                    }
                }
            }
        }
        //TODO setcode
        if (setcode > 0) {
            if (!card.isSetCode(setcode)) {
                return false;
            }
        }
        return true;
    }

    private int i(String str) {
        try {
            return Integer.valueOf(str);
        } catch (Exception e) {
            return 0;
        }
    }
}
