package cn.garymb.ygomobile.utils;

import android.text.TextUtils;

import ocgcore.StringManager;
import ocgcore.data.Card;
import ocgcore.enums.CardType;

public class CardUtils {
    public static String getAllTypeString(Card card, StringManager stringManager) {
        StringBuilder stringBuilder = new StringBuilder();
        CardType[] cardTypes = CardType.values();
        boolean isFrst = true;
        if (card.isType(CardType.Spell)) {
            for (CardType type : cardTypes) {
                if (card.isType(type)) {
                    stringBuilder.append(stringManager.getTypeString(type.value()));
//                    break;
                }
            }
//            stringBuilder.append(stringManager.getTypeString(CardType.Spell.value()));
        } else if (card.isType(CardType.Trap)) {
            for (CardType type : cardTypes) {
                if (card.isType(type)) {
                    stringBuilder.append(stringManager.getTypeString(type.value()));
                }
//                break;
            }
//            stringBuilder.append(stringManager.getTypeString(CardType.Trap.value()));
        } else {
            for (CardType type : cardTypes) {
                if (card.isType(type)) {
                    if (!isFrst) {
                        stringBuilder.append("/");
                    } else {
                        isFrst = false;
                    }
                    String str = stringManager.getTypeString(type.value());
                    if (TextUtils.isEmpty(str)) {
                        stringBuilder.append("0x");
                        stringBuilder.append(String.format("%X", type.value()));
                    } else {
                        stringBuilder.append(str);
                    }
                }
            }
        }
        return stringBuilder.toString();
    }
}
