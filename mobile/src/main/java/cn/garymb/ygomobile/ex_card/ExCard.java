package cn.garymb.ygomobile.ex_card;

import android.os.Parcel;
import android.os.Parcelable;

/*
This class contains two types of card information:ex-card information and updating log, which is marked
by "type 0" and "type 1", respectively.
本类包括两种卡牌信息，先行卡信息和更新日志（分别由·type 0和type 1表示)。
 */
public class ExCard implements Parcelable {
    private String name;
    private String imageUrl;
    private String description;
    /* used to mark this object as ex-card(the value is 0) or updating log(the value is 1) */
    private Integer type;

    public ExCard(String name, String imageUrl, String description, Integer type) {
        this.name = name;
        this.imageUrl = imageUrl;
        this.description = description;
        this.type = type;
    }

    protected ExCard(Parcel in) {
        name = in.readString();
        imageUrl = in.readString();
        description = in.readString();
        type = in.readInt();
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

    public Integer getType() {
        return type;
    }

    public void setType(Integer type) {
        this.type = type;
    }

    public boolean isUpdatingLog(){
        return (type == 1);
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
        dest.writeInt(this.type);
    }
}
