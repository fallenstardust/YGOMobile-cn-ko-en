package cn.garymb.ygomobile.utils;

import android.text.TextUtils;

import ocgcore.StringManager;
import ocgcore.data.Card;
import ocgcore.enums.CardType;

public class CardUtils {
    public static String getAllTypeString(Card card, StringManager stringManager) {
        StringBuilder stringBuilder = new StringBuilder();
        CardType[] cardTypes = CardType.values();
        boolean isFirst = true;
        for (CardType type : cardTypes) {
            if (card.isType(type)) {
                if (!isFirst) {
                    stringBuilder.append("/");
                } else {
                    isFirst = false;
                }
                String str = stringManager.getTypeString(type.getId());
                if (TextUtils.isEmpty(str)) {
                    stringBuilder.append("0x");
                    stringBuilder.append(String.format("%X", type.getId()));
                } else {
                    stringBuilder.append(str);
                }
            }
        }
        return stringBuilder.toString();
    }


    public int value2Index(long type) {
        //0 1 2 3 4
        //1 2 4 8 16
        int i = 0;
        long start;
        do {
            start = (long) Math.pow(2, i);
            if (start == type) {
                return i;
            } else if (start > type) {
                return -1;
            }
            i++;
        }
        while (start < type);
        return i;
    }

}
