package cn.garymb.ygomobile.ui.cards.deck_square.bo;

import android.os.Parcel;
import android.os.Parcelable;

public class DeckInfo implements Parcelable {

    private String name;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    protected DeckInfo(Parcel in) {
        name = in.readString();
    }

    public static final Creator<DeckInfo> CREATOR = new Creator<DeckInfo>() {
        @Override
        public DeckInfo createFromParcel(Parcel in) {
            return new DeckInfo(in);
        }

        @Override
        public DeckInfo[] newArray(int size) {
            return new DeckInfo[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.name);
    }
}
