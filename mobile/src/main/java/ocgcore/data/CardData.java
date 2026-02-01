package ocgcore.data;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

public class CardData implements Parcelable {

    public CardData() {
    }

    public CardData(int code) {
        Code = code;
    }

    public int Code;
    public int Ot;
    public int Alias;
    public long SetCode;
    public long Type;
    public int Level;
    public long Attribute;
    public long Race;
    public int Attack;
    public int Defense;
    public int LeftScale;
    public int RightScale;
    public long Category;

    @NonNull
    @Override
    public String toString() {
        return "CardData{" +
                "Code=" + Code +
                ", Ot=" + Ot +
                ", Alias=" + Alias +
                ", Setcode=" + SetCode +
                ", Type=" + Type +
                ", Level=" + Level +
                ", Attribute=" + Attribute +
                ", Race=" + Race +
                ", Attack=" + Attack +
                ", Defense=" + Defense +
                ", LScale=" + LeftScale +
                ", RScale=" + RightScale +
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
        dest.writeLong(this.SetCode);
        dest.writeLong(this.Type);
        dest.writeInt(this.Level);
        dest.writeLong(this.Attribute);
        dest.writeLong(this.Race);
        dest.writeInt(this.Attack);
        dest.writeInt(this.Defense);
        dest.writeInt(this.LeftScale);
        dest.writeInt(this.RightScale);
        dest.writeLong(this.Category);
    }

    protected CardData(Parcel in) {
        this.Code = in.readInt();
        this.Ot = in.readInt();
        this.Alias = in.readInt();
        this.SetCode = in.readLong();
        this.Type = in.readLong();
        this.Level = in.readInt();
        this.Attribute = in.readLong();
        this.Race = in.readLong();
        this.Attack = in.readInt();
        this.Defense = in.readInt();
        this.LeftScale = in.readInt();
        this.RightScale = in.readInt();
        this.Category = in.readLong();
    }

    /**
     * 规则同名卡，如果有alias则返回alias，否则返回code，判断严格，用于卡组投入最大数量的判断
     */
    public int getGameCode() {
        return Alias > 0 ? Alias : Code;
    }

    /**
     * 同卡，不同卡图
     * 只对异画的情况如果有alias则返回alias，否则返回code，判断较为宽松，适合规则上视为同名卡但效果不同的卡，适合计算genesys点数或者判断
     * TODO 暂定最大差异值是20，因为目前单张卡异画数量还未到这个值，未来很大可能会出现更多，需要即时调整
     */
    public int getCode() {
        return Alias > 0 && Math.abs(Alias - Code) <= 20 ? Alias : Code;
    }

    /**
     * 根据卡密判断是否是一张卡，只判断多卡图的
     */
    public boolean isSame(long code) {
        return this.Code == code || getCode() == code;
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
