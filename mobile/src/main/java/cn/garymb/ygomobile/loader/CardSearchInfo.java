package cn.garymb.ygomobile.loader;

import android.text.TextUtils;

import ocgcore.data.Card;
import ocgcore.enums.CardOt;
import ocgcore.enums.CardType;

public class CardSearchInfo implements ICardFilter{
    //名字或者描述
    private CardKeyWord keyWord;
    private int attribute;
    private int level;
    private int ot;
    private int pscale = -1;
    private long race;
    private long category;
    private String atk;
    private String def;
    private int linkKey;
    private long[] types;
    private long setcode;
    private int limitType;
    private String limitName;

    private CardSearchInfo() {
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

    public int getAttribute() {
        return attribute;
    }

    public int getLevel() {
        return level;
    }

    public int getOt() {
        return ot;
    }

    public int getPscale() {
        return pscale;
    }

    public long getRace() {
        return race;
    }

    public long getCategory() {
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

    public long[] getTypes() {
        return types;
    }

    public long getSetcode() {
        return setcode;
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

        public Builder attribute(int val) {
            searchInfo.attribute = val;
            return this;
        }

        public Builder level(int val) {
            searchInfo.level = val;
            return this;
        }

        public Builder ot(int val) {
            searchInfo.ot = val;
            return this;
        }

        public Builder pscale(int val) {
            searchInfo.pscale = val;
            return this;
        }

        public Builder race(long val) {
            searchInfo.race = val;
            return this;
        }

        public Builder category(long val) {
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

        public Builder types(long[] types) {
            searchInfo.types = types;
            return this;
        }

        public Builder setcode(long setcode) {
            searchInfo.setcode = setcode;
            return this;
        }
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

    @Override
    public boolean isValid(Card card) {
        if(keyWord != null && !keyWord.isValid(card)){
            return false;
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
        if (ot > CardOt.ALL.getId()) {
            if (ot == CardOt.NO_EXCLUSIVE.getId()) {
                if (card.Ot == CardOt.OCG.getId() || card.Ot == CardOt.TCG.getId()) {
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
                if (cardType == CardType.Spell.getId() || cardType == CardType.Trap.getId()) {
                    st = true;
                    break;
                }
            }

            for (long type : types) {
                if (type > 0) {
                    if (st) {
                        //效果以外
                        if (type == CardType.Non_Effect.getId()) {
                            if (card.isType(CardType.Effect)) {
                                return false;
                            }
                        }
                        //魔法
                        else if (type == CardType.Normal.getId()) {
                            //通常
                            if (card.isType(CardType.Normal)) {
                                //带通常的魔法陷阱
                                if (card.Type != (CardType.Spell.getId() | CardType.Normal.getId())
                                        && card.Type != (CardType.Trap.getId() | CardType.Normal.getId())) {
                                    return false;
                                }
                            } else {
                                //只有魔法/陷阱
                                if (card.Type != CardType.Spell.getId() && card.Type != CardType.Trap.getId())
                                    return false;
                            }
                            continue;
                        }
                    } else {
                        //排除通常怪兽里的token卡
                        if (type == CardType.Normal.getId()) {
                            if ((card.Type & CardType.Token.getId()) == CardType.Token.getId())
                                return false;
                        }
                    }
                    //效果怪兽
                    if (type == CardType.Effect.getId()) {
                        if ((card.Type & CardType.Effect.getId()) == CardType.Effect.getId()) {
                            //如果是融合/仪式/同调/超量/连接
                            if ((card.Type & CardType.Fusion.getId()) == CardType.Fusion.getId()
                                    || (card.Type & CardType.Ritual.getId()) == CardType.Ritual.getId()
                                    || (card.Type & CardType.Synchro.getId()) == CardType.Synchro.getId()
                                    || (card.Type & CardType.Xyz.getId()) == CardType.Xyz.getId()
                                    || (card.Type & CardType.Link.getId()) == CardType.Link.getId()
                            )
                                return false;
                        } else {
                            return false;
                        }
                    } else if (type == CardType.Non_Effect.getId()) {
                        //非效果怪兽
                        if ((card.Type & CardType.Effect.getId()) == CardType.Effect.getId())
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
