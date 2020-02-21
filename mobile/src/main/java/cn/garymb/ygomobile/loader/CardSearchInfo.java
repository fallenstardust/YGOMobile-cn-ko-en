package cn.garymb.ygomobile.loader;

import android.text.TextUtils;

import java.util.List;

import ocgcore.data.Card;
import ocgcore.enums.CardType;

class CardSearchInfo {
    //名字或者描述
    String word, prefixWord, suffixWord;
    int attribute;
    int level, ot, pscale = -1;
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
            if (TextUtils.isDigitsOnly(word) && word.length() >= 5) {
                //code
                long code = Long.parseLong(word);
                return card.Code == code || card.Alias == code;
            } else if (!((card.Name != null && card.Name.contains(word))
                    || (card.Desc != null && card.Desc.contains(word)))) {
                return false;
            }
        } else if (!TextUtils.isEmpty(prefixWord) && !TextUtils.isEmpty(suffixWord)) {
            boolean has = false;
            int i1 = -1, i2 = -1, i3, i4;
            if (card.Name != null) {
                i1 = card.Name.indexOf(prefixWord);
                i2 = card.Name.indexOf(suffixWord);
                if (i1 >= 0 && i2 >= 0) {
                    has = true;
                }
            }
            if (!has) {
                if (card.Desc != null) {
                    i3 = card.Desc.indexOf(prefixWord);
                    i4 = card.Desc.indexOf(suffixWord);
                    if ((i3 >= 0 && i4 >= 0)
                            || (i3 >= 0 && i2 >= 0)
                            || (i1 >= 0 && i4 >= 0)) {
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
            if (!card.isType(CardType.Pendulum) || card.LScale != pscale && card.RScale != pscale) {
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
                    //效果怪兽
                    if (type == CardType.Effect.value()) {
                        if ((card.Type & CardType.Effect.value()) == CardType.Effect.value()) {
                            //如果是融合/仪式/同调/超量/连接
                            if ((card.Type & CardType.Fusion.value()) == CardType.Fusion.value()
                                    || (card.Type & CardType.Ritual.value()) == CardType.Ritual.value()
                                    || (card.Type & CardType.Synchro.value()) == CardType.Synchro.value()
                                    || (card.Type & CardType.Xyz.value()) == CardType.Xyz.value()
                                    || (card.Type & CardType.Link.value()) == CardType.Link.value()
                                    )
                                return false;
                        }else {
                            return false;
                        }
                    }else  if (type == CardType.Non_Effect.value()) {
                        //非效果怪兽
                        if ((card.Type & CardType.Effect.value()) == CardType.Effect.value())
                            return false;
                    }else  if ((card.Type & type) != type) {
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
