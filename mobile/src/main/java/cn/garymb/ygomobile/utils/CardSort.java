package cn.garymb.ygomobile.utils;

import java.util.Comparator;

import ocgcore.data.Card;
import ocgcore.enums.CardType;


public class CardSort implements Comparator<Card> {
    public static final CardSort ASC = new CardSort(false, null);
    public static final CardSort FULL_ASC = new CardSort(true, null);
    private final boolean full;
    private final java.util.List<Integer> priorityOrder;
    private CardSort(boolean full, java.util.List<Integer> priorityOrder) {
        this.full = full;
        this.priorityOrder = priorityOrder;
    }

    public static CardSort ASC_inCards(java.util.List<Integer> priorityOrder) {
        return new CardSort(false, priorityOrder);
    }

    public static CardSort FULL_ASC_inCards(java.util.List<Integer> priorityOrder) {
        return new CardSort(true, priorityOrder);
    }
    private int comp(long l1, long l2) {
        return Long.compare(l1, l2);
    }

    private static final int SORT_OTHER = 999;
    private static final int SORT_MONSTER = 1;
    private static final int SORT_LINK = 2;
    private static final int SORT_SPELL = 3;
    private static final int SORT_TRAP = 4;
    private static final int SORT_FUSION = 10;
    private static final int SORT_SYNCHRO = 11;
    private static final int SORT_XYZ = 12;

    private int getSortKey1(Card c1){
        if (c1.isType(CardType.Link)) {
            return SORT_LINK;
        } else if (c1.isType(CardType.Monster)) {
            return SORT_MONSTER;
        } else if (c1.isType(CardType.Spell)) {
            return SORT_SPELL;
        } else if (c1.isType(CardType.Trap)) {
            return SORT_TRAP;
        } else{
            return SORT_OTHER;
        }
    }

    private int getSortKey2(Card c1){
        if (c1.isType(CardType.Fusion)) {
            return SORT_FUSION;
        } else if (c1.isType(CardType.Synchro)) {
            return SORT_SYNCHRO;
        } else if (c1.isType(CardType.Xyz)) {
            return SORT_XYZ;
        } else{
            return SORT_OTHER;
        }
    }

    @Override
    public int compare(Card c1, Card c2) {
        // 首先检查是否有预定义的优先级顺序
        if (priorityOrder != null && !priorityOrder.isEmpty()) {
            int index1 = priorityOrder.indexOf(c1.getCode());
            int index2 = priorityOrder.indexOf(c2.getCode());

            if (index1 != -1 && index2 != -1) {
                // 两张卡都在优先级列表中，按列表顺序排序
                return Integer.compare(index1, index2);
            } else if (index1 != -1) {
                // 只有c1在优先级列表中，c1优先
                return -1;
            } else if (index2 != -1) {
                // 只有c2在优先级列表中，c2优先
                return 1;
            }
            // 如果两张卡都不在优先级列表中，继续使用默认排序逻辑
        }

        // 原有的比较逻辑
        if (c1 == null) {
            return c2 != null ? 1 : 0;
        }
        if (c2 == null) {
            return 1;
        }
        //同名卡
        if(c1.getCode() == c2.getCode()){
            return comp(c1.Code, c2.Code);
        }
        int sortKey1 = getSortKey1(c1);
        int sortKey2 = getSortKey1(c2);
        if(sortKey1 != sortKey2){
            return Integer.compare(sortKey1, sortKey2);
        }
        if(sortKey1 == SORT_SPELL || sortKey2 == SORT_TRAP){
            long type1, type2;
            if(sortKey1 == SORT_SPELL){
                type1 = c1.Type ^ CardType.Spell.getId();
                type2 = c2.Type ^ CardType.Spell.getId();
            } else {
                type1 = c1.Type ^ CardType.Trap.getId();
                type2 = c2.Type ^ CardType.Trap.getId();
            }
            int rs = comp(type1, type2);
            if (rs == 0) {
                return comp(c1.Code, c2.Code);
            } else {
                return rs;
            }
        } else if(sortKey1 == SORT_OTHER){
            return comp(c1.Code, c2.Code);
//        } else if(sortKey1 == SORT_LINK){
//            return Integer.compare(c2.getStar(), c1.getStar());
        } else {
            //monster
            if(full) {
                sortKey1 = getSortKey2(c1);
                sortKey2 = getSortKey2(c2);
                if (sortKey1 != sortKey2) {
                    //需要反转
                    return Integer.compare(sortKey1, sortKey2);
                }
            }
            //高星的在前面
            int rs = comp(c2.getStar(), c1.getStar());
            if (rs != 0) {
                return rs;
            }
            //高攻击的在前面
            rs = comp(c2.Attack, c1.Attack);
            if (rs != 0) {
                return rs;
            }
            //高防御的在前面
            rs = comp(c2.Defense, c1.Defense);
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
        return comp(c1.Code, c2.Code);
    }

    private boolean isSameType(Card c1, Card c2, CardType type){
        return c1.isType(type) && c2.isType(type);
    }

    private boolean isSpecialType(Card c1) {
        return c1.isType(CardType.Fusion) || c1.isType(CardType.Synchro) || c1.isType(CardType.Xyz) || c1.isType(CardType.Link);
    }
}
