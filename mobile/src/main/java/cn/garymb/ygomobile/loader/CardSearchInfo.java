package cn.garymb.ygomobile.loader;

import java.util.List;

import android.text.TextUtils;

import ocgcore.data.Card;
import ocgcore.enums.CardOt;
import ocgcore.enums.CardType;

public class CardSearchInfo implements ICardFilter{
    //名字或者描述
    private CardKeyWord keyWord;
    private int ot;
    private List<Integer> pscale;
    private String atk;
    private String def;
    private int linkKey;
    private int limitType;
    private String limitName;

    private List<Integer> attribute;
    private List<Integer> level;
    private List<Long> race;
    private List<Long> category;
    private List<Long> types;
    private List<Long> setcode;

    //true为and逻辑, false为or逻辑
    private boolean type_logic;
    private boolean setcode_logic;

    public CardSearchInfo() {
    }

    public int getLimitType() {
        return limitType;
    }

    public String getLimitName() {
        return limitName;
    }

    public CardKeyWord getKeyWord() {
        return keyWord;
    }

    public List<Integer> getAttribute() {
        return attribute;
    }

    public List<Integer> getLevel() {
        return level;
    }

    public int getOt() {
        return ot;
    }

    public List<Integer> getPscale() {
        return pscale;
    }

    public List<Long> getRace() {
        return race;
    }

    public List<Long> getCategory() {
        return category;
    }

    public String getAtk() {
        return atk;
    }

    public String getDef() {
        return def;
    }

    public int getLinkKey() {
        return linkKey;
    }

    public List<Long> getTypes() {
        return types;
    }

    public List<Long> getSetcode() {
        return setcode;
    }

    public String getTypeLogice() {
        return type_logic ? "and" : "or";
    }

    public String getSetCodeLogice() {
        return setcode_logic ? "and" : "or";
    }

    public static class Builder {
        private final CardSearchInfo searchInfo = new CardSearchInfo();

        public CardSearchInfo build() {
            return searchInfo;
        }

        public Builder limitType(int limit){
            searchInfo.limitType = limit;
            return this;
        }

        public Builder limitName(String val) {
            searchInfo.limitName = val;
            return this;
        }

        public Builder keyword(String val) {
            searchInfo.keyWord = new CardKeyWord(val);
            return this;
        }

        public Builder attribute(List<Integer> val) {
            searchInfo.attribute = val;
            return this;
        }

        public Builder level(List<Integer> val) {
            searchInfo.level = val;
            return this;
        }

        public Builder ot(int val) {
            searchInfo.ot = val;
            return this;
        }

        public Builder pscale(List<Integer> val) {
            searchInfo.pscale = val;
            return this;
        }

        public Builder race(List<Long> val) {
            searchInfo.race = val;
            return this;
        }

        public Builder category(List<Long> val) {
            searchInfo.category = val;
            return this;
        }

        public Builder atk(String val) {
            searchInfo.atk = val;
            return this;
        }

        public Builder def(String val) {
            searchInfo.def = val;
            return this;
        }

        public Builder linkKey(int linkKey) {
            searchInfo.linkKey = linkKey;
            return this;
        }

        public Builder types(List<Long> types) {
            searchInfo.types = types;
            return this;
        }

        public Builder setcode(List<Long> setcode) {
            searchInfo.setcode = setcode;
            return this;
        }

        public Builder type_logic(boolean logic) {
            searchInfo.type_logic = logic;
            return this;
        }

        public Builder setcode_logic(boolean logic) {
            searchInfo.setcode_logic = logic;
            return this;
        }
    }

    public String toString(){
        return "CardSearchInfo{" +
                "LimitType="+getLimitType() +
                ", Ot=" + getOt() +
                ", LimitName=" + getLimitName() +
                ", KeyWord=" + getKeyWord() +
                ", Attribute=" + getAttribute() +
                ", Level=" + getLevel() +
                ", PScale=" + getPscale() +
                ", Category=" + getCategory() +
                ", ATK=" + getAtk() +
                ", DEF=" + getDef() +
                ", LINK=" + getLinkKey() +
                ", Race=" + getRace() +
                ", Type=" + getTypes() +
                ", SetCode=" + getSetcode() +
                ", TypeLogic=" + getTypeLogice() +
                ", SetCodeLogic=" + getSetCodeLogice() +
                '}';
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

    public boolean chkAtkDef(int ct, String search) {
        switch (search.charAt(0)) {
            case '>':
                if (search.length() > 1 && search.charAt(1) == '=') {
                    return ct >= (TextUtils.isDigitsOnly(search.substring(2)) ? i(search.substring(2)) : -2);
                } else {
                    return ct > (TextUtils.isDigitsOnly(search.substring(1)) ? i(search.substring(1)) : -2);
                }
            case '<':
                if (search.length() > 1 && search.charAt(1) == '=') {
                    return ct <= (TextUtils.isDigitsOnly(search.substring(2)) ? i(search.substring(2)) : -2);
                } else {
                    return ct < (TextUtils.isDigitsOnly(search.substring(1)) ? i(search.substring(1)) : -2);
                }
            case '=':
                return ct == (TextUtils.isDigitsOnly(search.substring(1)) ? i(search.substring(1)) : -2);
            default:
                return ct == (TextUtils.isDigitsOnly(search) ? i(search) : -2);
        }
    }

    @Override
    public boolean isValid(Card card) {
        if(keyWord != null && !keyWord.isValid(card)){
            return false;
        }
        if (!attribute.isEmpty() && !attribute.contains(card.Attribute)) {
            return false;
        }
        if (!level.isEmpty() && !level.contains(card.getStar())) {
            return false;
        }
        if (!TextUtils.isEmpty(atk)) {
            if (atk.contains("-")) {
                String[] atks = atk.split("-");
                if (!(i(atks[0]) <= card.Attack && card.Attack <= i(atks[1]))) {
                    return false;
                }
            } else if (!chkAtkDef(card.Attack, atk)) {
                return false;
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
                } else if (card.isLink() || !chkAtkDef(card.Defense, def)) {
                    return false;
                }
            }
        }
        if (ot > CardOt.ALL.getId()) {
            if (ot == CardOt.NO_EXCLUSIVE.getId()) {
                if (card.Ot == CardOt.OCG.getId() || card.Ot == CardOt.TCG.getId() || card.Ot == CardOt.CUSTOM.getId()) {
                    return false;
                }
            } else if (ot == CardOt.OCG.getId() || ot == CardOt.TCG.getId()) {
                if (card.Ot != ot) {
                    return false;
                }
            } else if ((card.Ot & ot) == 0) {
                return false;
            }
        }

        if (!pscale.isEmpty()
            && (!card.isType(CardType.Pendulum)
                || (!pscale.contains(card.LeftScale)
                    && !pscale.contains(card.RightScale)
                )
            )
        ) {
            return false;
        }

        if (!race.isEmpty() && !race.contains(card.Race)) {
            return false;
        }
        if (!category.isEmpty()
            && category.stream().filter(i -> (card.Category & i) == i).count() == 0
        ) {
            return false;
        }
        long type_ct = types.stream().filter(type -> (card.Type & type) == type).count();
        if (!types.isEmpty()
            && (type_logic ? type_ct != types.size() : type_ct == 0)
        ) {
            return false;
        }
        //TODO setcode
        long setcode_ct = setcode.stream().filter(i -> card.isSetCode(i)).count();
        if (!setcode.isEmpty()
            && (setcode_logic ? setcode_ct != setcode.size() : setcode_ct == 0)
        ) {
            return false;
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
