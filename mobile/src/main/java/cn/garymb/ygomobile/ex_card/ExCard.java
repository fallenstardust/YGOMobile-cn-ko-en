package cn.garymb.ygomobile.ex_card;

import android.os.Parcel;
import android.os.Parcelable;

public class ExCard implements Parcelable {
    private String name;
    private String imageUrl;
    private String description;

    public ExCard(String name, String imageUrl, String description) {
        this.name = name;
        this.imageUrl = imageUrl;
        this.description = description;
    }

    protected ExCard(Parcel in) {
        name = in.readString();
        imageUrl = in.readString();
        description = in.readString();
    }

    public static final Creator<ExCard> CREATOR = new Creator<ExCard>() {
        @Override
        public ExCard createFromParcel(Parcel in) {
            return new ExCard(in);
        }

        @Override
        public ExCard[] newArray(int size) {
            return new ExCard[size];
        }
    };

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.name);
        dest.writeString(this.imageUrl);
        dest.writeString(this.description);
    }
}
