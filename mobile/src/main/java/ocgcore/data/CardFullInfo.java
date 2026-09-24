package ocgcore.data;

import android.os.Parcel;

public class CardFullInfo extends Card {

    public CardFullInfo() {
        Strs = new String[0x10];
    }


    protected CardFullInfo(Parcel in) {
        super(in);
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
