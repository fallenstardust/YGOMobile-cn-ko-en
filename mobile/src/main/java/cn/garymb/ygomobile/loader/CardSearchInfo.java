package cn.garymb.ygomobile.loader;

import android.text.TextUtils;

import java.util.List;

import ocgcore.data.Card;
import ocgcore.enums.CardType;

class CardSearchInfo {
    //名字或者描述
    String keyWord1, keyWord2;
    int attribute;
    int level, ot, pscale = -1;
    long race, category;
    String atk, def;
    int linkKey;
    List<Integer> inCards;
    long[] types;
    long setcode, keyWordSetcode1, keyWordSetcode2;

    CardSearchInfo() {
    }

    public static boolean containsIgnoreCase(String src, String what) {
        // https://stackoverflow.com/a/25379180
        final int length = what.length();
        if (length == 0)
            return true; // Empty string is contained

        final char firstLo = Character.toLowerCase(what.charAt(0));
        final char firstUp = Character.toUpperCase(what.charAt(0));

        for (int i = src.length() - length; i >= 0; i--) {
            // Quick check before calling the more expensive regionMatches() method:
            final char ch = src.charAt(i);
            if (ch != firstLo && ch != firstUp)
                continue;

            if (src.regionMatches(true, i, what, 0, length))
                return true;
        }

        return false;
    }

    List<Integer> getInCards() {
        return inCards;
    }

    public boolean check(Card card) {
        if (inCards != null && !inCards.contains(Integer.valueOf(card.Code))) {
            return false;
        }
        if (!TextUtils.isEmpty(keyWord1)) {
            if (TextUtils.isDigitsOnly(keyWord1) && keyWord1.length() >= 5) {
                //code
                long code = Long.parseLong(keyWord1);
                return card.Code == code || card.Alias == code;
            } else if (!((card.Name != null && containsIgnoreCase(card.Name, keyWord1))
                    || (card.Desc != null && containsIgnoreCase(card.Desc, keyWord1))
                    || (keyWordSetcode1 > 0 && card.isSetCode(keyWordSetcode1)))) {
                return false;
            }
        }
        if (!TextUtils.isEmpty(keyWord2)) {
            if (!((card.Name != null && containsIgnoreCase(card.Name, keyWord2))
                    || (card.Desc != null && containsIgnoreCase(card.Desc, keyWord2))
                    || (keyWordSetcode2 > 0 && card.isSetCode(keyWordSetcode2)))) {
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
            if (card.Ot.getId() != ot) {
                return false;
            }
        }

        if (pscale != -1) {
            if (!card.isType(CardType.Pendulum) || card.LeftScale != pscale && card.RightScale != pscale) {
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
                    } else {
                        //排除通常怪兽里的token卡
                        if (type == CardType.Normal.value()) {
                            if ((card.Type & CardType.Token.value()) == CardType.Token.value())
                                return false;
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
                        } else {
                            return false;
                        }
                    } else if (type == CardType.Non_Effect.value()) {
                        //非效果怪兽
                        if ((card.Type & CardType.Effect.value()) == CardType.Effect.value())
                            return false;
                    } else if ((card.Type & type) != type) {
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
