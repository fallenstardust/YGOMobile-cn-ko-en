package cn.garymb.ygomobile.loader;

import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;

import java.util.List;

import ocgcore.data.Card;
import ocgcore.enums.CardOt;
import ocgcore.enums.CardType;

public class CardSearchInfo implements ICardFilter {
    //名字或者描述
    private CardKeyWord keyWord;
    private int ot;
    private List<Integer> pscale;
    private String atk;
    private String def;
    private int linkKey;
    private int limitType;
    private String limitName;

    private List<Long> attribute;
    private List<Integer> level;
    private List<Long> race;
    private List<Long> category;
    private List<Long> types;
    private List<Long> except_types;
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

    public List<Long> getAttribute() {
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

    public List<Long> getExceptTypes() {
        return except_types;
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

        public Builder limitType(int limit) {
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

        public Builder attribute(List<Long> val) {
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

        public Builder except_types(List<Long> except_types) {
            searchInfo.except_types = except_types;
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

    @NonNull
    public String toString() {
        return "CardSearchInfo{" +
                "LimitType=" + getLimitType() +
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
                ", ExceptType=" + getExceptTypes() +
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

    /**
     * 验证卡片是否符合当前过滤器的所有条件
     *
     * @param card 待验证的卡片对象
     * @return 如果卡片符合所有过滤条件则返回true，否则返回false
     */
    @Override
    public boolean isValid(Card card) {
        // 检查关键词过滤条件
        if (keyWord != null && !keyWord.isValid(card)) {
            return false;
        }
        Log.i("CardSearchInfo",attribute + "isValid: " + card.Attribute);
        // 检查属性过滤条件
        if (!attribute.isEmpty() && !attribute.contains(card.Attribute)) {
            return false;
        }
        // 检查等级/星级过滤条件
        if (!level.isEmpty() && !level.contains(card.getStar())) {
            return false;
        }
        // 检查攻击力过滤条件（支持范围和精确值）
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

        // 检查链接值过滤条件（如果是链接怪兽）或防御力过滤条件
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

        // 检查卡片OCG\TCG独有过滤条件
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

        // 检查灵摆刻度过滤条件
        if (!pscale.isEmpty()
                && (!card.isType(CardType.Pendulum)
                || (!pscale.contains(card.LeftScale)
                && !pscale.contains(card.RightScale)))
        ) {
            return false;
        }

        // 检查种族过滤条件
        if (!race.isEmpty() && !race.contains(card.Race)) {
            return false;
        }
        // 检查分类过滤条件
        if (!category.isEmpty()
                && category.stream().noneMatch(i -> (card.Category & i) == i)
        ) {
            return false;
        }
        // 检查排除类型过滤条件
        if (!except_types.isEmpty()
                && except_types.stream().anyMatch(type -> (card.Type & type) == type)
        ) {
            return false;
        }
        // 检查卡片类型过滤条件（支持逻辑与/或）
        if (!types.isEmpty()
                && (type_logic ?
                types.stream().filter(type -> (card.Type & type) == type).count() != types.size()
                : types.stream().noneMatch(type -> (card.Type & type) == type))
        ) {
            return false;
        }
        //TODO setcode
        // 检查系列代码过滤条件（支持逻辑与/或）
        if (!setcode.isEmpty()
                && (setcode_logic ?
                setcode.stream().filter(card::isSetCode).count() != setcode.size()
                : setcode.stream().noneMatch(card::isSetCode))
        ) {
            return false;
        }
        return true;
    }


    private int i(String str) {
        try {
            return Integer.parseInt(str);
        } catch (Exception e) {
            return 0;
        }
    }
}
