package cn.garymb.ygomobile.utils;

import java.util.Comparator;

import ocgcore.data.Card;
import ocgcore.enums.CardType;


public class CardSort implements Comparator<Card> {
    public static final CardSort ASC = new CardSort();

    public CardSort() {
    }

    private int comp(long l1, long l2) {
        return Long.compare(l1, l2);
    }

    @Override
    public int compare(Card c1, Card c2) {
        if (c1 == null) {
            return c2 != null ? 1 : 0;
        }
        if (c2 == null) {
            return 1;
        }
//            boolean log = c1.Code == 74003290||c2.Code == 74003290;
        if (c1.isType(CardType.Spell)) {
            if (c2.isType(CardType.Monster)) {
                //怪兽在前面
                return -1;
            } else if (c2.isType(CardType.Trap)) {
                //陷阱在前面
                return 1;
            }
            long type1 = c1.Type ^ CardType.Spell.value();
            long type2 = c2.Type ^ CardType.Spell.value();
            int rs = comp(type1, type2);
            if (rs == 0) {
                return comp(c1.Code, c2.Code);
            } else {
                return rs;
            }
        } else if (c1.isType(CardType.Trap)) {
            //怪兽魔法在前面
            if (c2.isType(CardType.Monster) || c2.isType(CardType.Spell)) {
                return -1;
            }
            long type1 = c1.Type ^ CardType.Trap.value();
            long type2 = c2.Type ^ CardType.Trap.value();
            int rs = comp(type1, type2);
            if (rs == 0) {
                return comp(c1.Code, c2.Code);
            } else {
                return rs;
            }
        } else if (c1.isType(CardType.Monster)) {
            //魔法陷阱在后面
            if (c2.isType(CardType.Spell) || c2.isType(CardType.Trap)) {
                //怪兽在前面
                return 1;
            }
            if (isSameType(c1, c2, CardType.Fusion)
                    || isSameType(c1, c2, CardType.Synchro)
                    || isSameType(c1, c2, CardType.Xyz)
                    || isSameType(c1, c2, CardType.Link)
                    || (!isSpecialType(c1) && !isSpecialType(c2))) {
                int rs = comp(c1.getStar(), c2.getStar());
                if (rs != 0) {
                    return rs;
                }
                rs = comp(c1.Attack, c2.Attack);
                if (rs != 0) {
                    return rs;
                }
                rs = comp(c1.Defense, c2.Defense);
                if (rs != 0) {
                    return rs;
                }

                rs = comp(c1.Attribute, c2.Attribute);
                if (rs != 0) {
                    return rs;
                }
                rs = comp(c1.Race, c2.Race);
                if (rs != 0) {
                    return rs;
                }
                rs = comp(c1.Ot, c2.Ot);
                if (rs != 0) {
                    return rs;
                }
            }
            else {
                //超量，同调，融合
                //Fusion,Synchro,Xyz,Link
                //1     ,2      ,3  ,4
                if (c1.isType(CardType.Xyz)) {
                    if (c2.isType(CardType.Synchro)) {
                        return -1;
                    } else if (c2.isType(CardType.Fusion)) {
                        return -2;
                    } else if (c2.isType(CardType.Link)) {
                        return 1;
                    } else {
                        return 2;
                    }
                }
                else if (c1.isType(CardType.Synchro)) {
                    if (c2.isType(CardType.Fusion)) {
                        return -1;
                    } else if (c2.isType(CardType.Xyz)) {
                        return 1;
                    } else if (c2.isType(CardType.Link)) {
                        return 2;
                    } else {
                        return 3;
                    }
                }
                else if (c1.isType(CardType.Fusion)) {
                    if (c2.isType(CardType.Synchro)) {
                        return 1;
                    } else if (c2.isType(CardType.Xyz)) {
                        return 2;
                    } else if (c2.isType(CardType.Link)) {
                        return 3;
                    } else {
                        return 4;
                    }
                }
                else if (c1.isType(CardType.Link)) {
                    if (c2.isType(CardType.Xyz)) {
                        return -3;
                    } else if (c2.isType(CardType.Synchro)) {
                        return -2;
                    } else if (c2.isType(CardType.Fusion)) {
                        return -1;
                    } else {
                        return 1;
                    }
                }
            }
        }
        return comp(c1.Code, c2.Code);
    }

    private boolean isSameType(Card c1, Card c2, CardType type){
        return c1.isType(type) && c2.isType(type);
    }

    private boolean isSpecialType(Card c1) {
        return c1.isType(CardType.Fusion) || c1.isType(CardType.Synchro) || c1.isType(CardType.Xyz) || c1.isType(CardType.Link);
    }
}
