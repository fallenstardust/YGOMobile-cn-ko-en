package ocgcore.data;

import android.os.Parcel;
import android.os.Parcelable;

public class CardData implements Parcelable{

    public CardData() {
    }

    public CardData(int code) {
        Code = code;
    }

    public int Code;
    public int Ot;
    public int Alias;
    public long Setcode;
    public long Type;
    public int Level;
    public int Attribute;
    public long Race;
    public int Attack;
    public int Defense;
    public int LScale;
    public int RScale;
    public long Category;

    @Override
    public String toString() {
        return "CardData{" +
                "Code=" + Code +
                ", Ot=" + Ot +
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
                ", Category=" + Category +
                '}';
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(this.Code);
        dest.writeInt(this.Ot);
        dest.writeInt(this.Alias);
        dest.writeLong(this.Setcode);
        dest.writeLong(this.Type);
        dest.writeInt(this.Level);
        dest.writeInt(this.Attribute);
        dest.writeLong(this.Race);
        dest.writeInt(this.Attack);
        dest.writeInt(this.Defense);
        dest.writeInt(this.LScale);
        dest.writeInt(this.RScale);
        dest.writeLong(this.Category);
    }

    protected CardData(Parcel in) {
        this.Code = in.readInt();
        this.Ot = in.readInt();
        this.Alias = in.readInt();
        this.Setcode = in.readLong();
        this.Type = in.readLong();
        this.Level = in.readInt();
        this.Attribute = in.readInt();
        this.Race = in.readLong();
        this.Attack = in.readInt();
        this.Defense = in.readInt();
        this.LScale = in.readInt();
        this.RScale = in.readInt();
        this.Category = in.readLong();
    }

    public static final Creator<CardData> CREATOR = new Creator<CardData>() {
        @Override
        public CardData createFromParcel(Parcel source) {
            return new CardData(source);
        }

        @Override
        public CardData[] newArray(int size) {
            return new CardData[size];
        }
    };
}
