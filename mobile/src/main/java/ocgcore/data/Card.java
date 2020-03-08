package ocgcore.data;


import android.os.Parcel;
import android.os.Parcelable;

import ocgcore.enums.CardType;

public class Card extends CardData implements Parcelable {
    public static final int SETCODE_MAX = 4;
    public static final Creator<Card> CREATOR = new Creator<Card>() {
        @Override
        public Card createFromParcel(Parcel source) {
            return new Card(source);
        }

        @Override
        public Card[] newArray(int size) {
            return new Card[size];
        }
    };
    public String Name;
    public String Desc;

    public Card() {
    }

    public Card(Card card) {
        this((CardData) card);
        if (card != null) {
            this.Name = card.Name;
            this.Desc = card.Desc;
        }
    }

    public Card(int code) {
        super(code);
    }

    public Card(CardData cardData) {
        super();
        if (cardData != null) {
            this.Code = cardData.Code;
            this.Alias = cardData.Alias;
            this.Setcode = cardData.Setcode;
            this.Type = cardData.Type;
            this.Level = cardData.Level;
            this.Attribute = cardData.Attribute;
            this.Race = cardData.Race;
            this.Attack = cardData.Attack;
            this.Defense = cardData.Defense;
            this.LScale = cardData.LScale;
            this.RScale = cardData.RScale;
            this.Category = cardData.Category;
        }
    }

    protected Card(Parcel in) {
        super(in);
        this.Name = in.readString();
        this.Desc = in.readString();
    }

    public static boolean isType(long Type, CardType type) {
        return ((Type & type.value()) != 0);
    }

    public static boolean isSpellTrap(long Type) {
        return (isType(Type, CardType.Spell) || isType(Type, CardType.Trap));
    }

    public static boolean isExtraCard(long Type) {
        return (isType(Type, CardType.Fusion) || isType(Type, CardType.Synchro) || isType(Type, CardType.Xyz) || isType(Type, CardType.Link));
    }

    public Card type(long type) {
        this.Type = type;
        return this;
    }

    public int getStar() {
        return (Level & 0xff);
    }

    public boolean isType(CardType type) {
        return ((Type & type.value()) != 0);
    }

    public boolean onlyType(CardType type) {
        return (Type == type.value());
    }

    public boolean isSpellTrap() {
        return isSpellTrap(Type);
    }

    public boolean isExtraCard() {
        return (isType(CardType.Fusion) || isType(CardType.Synchro) || isType(CardType.Xyz) || isType(Type, CardType.Link));
    }

    public long[] getSetCode() {
        long[] setcodes = new long[SETCODE_MAX];
        for (int i = 0, k = 0; i < SETCODE_MAX; k += 0x10, i++) {
            setcodes[i] = (Setcode >> k) & 0xffff;
        }
        return setcodes;
    }

    public void setSetCode(long[] setcodes) {
        int i = 0;
        this.Setcode = 0;
        if (setcodes != null) {
            for (long sc : setcodes) {
                this.Setcode += (sc << i);
                i += 0x10;
            }
        }
    }

    public boolean isSetCode(long _setcode) {
        int settype = (int) _setcode & 0xfff;
        int setsubtype = (int) _setcode & 0xf000;
        long[] setcodes = getSetCode();
        for (long setcode : setcodes) {
            if (((int) setcode & 0xfff) == settype && ((int) setcode & 0xf000 & setsubtype) == setsubtype)
                return true;
        }
        return false;
    }

    @Override
    public String toString() {
        return "Card{" +
                "Code=" + Code +
                ", Alias=" + Alias +
                ", Setcode=" + Setcode +
                ", Type=" + Type +
                ", Level=" + Level +
                ", Attribute=" + Attribute +
                ", Race=" + Race +
                ", Attack=" + Attack +
                ", Defense=" + Defense +
                ", LScale=" + LScale +
                ", RScale=" + RScale +
                ", Name='" + Name + '\'' +
                ", Desc='" + Desc + '\'' +
                '}';
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeString(this.Name);
        dest.writeString(this.Desc);
    }
}
