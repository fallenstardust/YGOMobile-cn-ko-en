package ocgcore.data;

import android.os.Parcel;

public class CardFullInfo extends Card {
    public String[] Strs;

    public CardFullInfo() {
        Strs = new String[0x10];
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        super.writeToParcel(dest, flags);
        dest.writeStringArray(this.Strs);
    }

    protected CardFullInfo(Parcel in) {
        super(in);
        this.Strs = in.createStringArray();
    }

    public static final Creator<CardFullInfo> CREATOR = new Creator<CardFullInfo>() {
        @Override
        public CardFullInfo createFromParcel(Parcel source) {
            return new CardFullInfo(source);
        }

        @Override
        public CardFullInfo[] newArray(int size) {
            return new CardFullInfo[size];
        }
    };
}
