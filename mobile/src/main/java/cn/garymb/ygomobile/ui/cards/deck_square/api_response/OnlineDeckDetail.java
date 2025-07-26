package cn.garymb.ygomobile.ui.cards.deck_square.api_response;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

public class OnlineDeckDetail implements Parcelable {

    private String deckId;
    private String deckContributor;
    private String deckName;
    private int deckLike;
    private int deckCoverCard1;
    private int deckCoverCard2;
    private int deckCoverCard3;
    private int deckCase;
    private int deckProtector;
    private long lastDate;
    private int userId;


    protected OnlineDeckDetail(Parcel in) {
        deckId = in.readString();
        deckContributor = in.readString();
        deckName = in.readString();
        deckLike = in.readInt();
        deckCoverCard1 = in.readInt();
        deckCoverCard2 = in.readInt();
        deckCoverCard3 = in.readInt();
        deckCase = in.readInt();
        deckProtector = in.readInt();
        lastDate = in.readLong();
        userId = in.readInt();
    }

    public static final Creator<OnlineDeckDetail> CREATOR = new Creator<OnlineDeckDetail>() {
        @Override
        public OnlineDeckDetail createFromParcel(Parcel in) {
            return new OnlineDeckDetail(in);
        }

        @Override
        public OnlineDeckDetail[] newArray(int size) {
            return new OnlineDeckDetail[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeString(deckId);
        dest.writeString(deckContributor);
        dest.writeString(deckName);
        dest.writeInt(deckLike);
        dest.writeInt(deckCoverCard1);
        dest.writeInt(deckCoverCard2);
        dest.writeInt(deckCoverCard3);
        dest.writeInt(deckCase);
        dest.writeInt(deckProtector);
        dest.writeLong(lastDate);
        dest.writeInt(userId);
    }

    public String getDeckId() {
        return deckId;
    }

    public void setDeckId(String deckId) {
        this.deckId = deckId;
    }

    public String getDeckContributor() {
        return deckContributor;
    }

    public void setDeckContributor(String deckContributor) {
        this.deckContributor = deckContributor;
    }

    public String getDeckName() {
        return deckName;
    }

    public void setDeckName(String deckName) {
        this.deckName = deckName;
    }

    public int getDeckLike() {
        return deckLike;
    }

    public void setDeckLike(int deckLike) {
        this.deckLike = deckLike;
    }

    public int getDeckCoverCard1() {
        return deckCoverCard1;
    }

    public void setDeckCoverCard1(int deckCoverCard1) {
        this.deckCoverCard1 = deckCoverCard1;
    }

    public int getDeckCoverCard2() {
        return deckCoverCard2;
    }

    public void setDeckCoverCard2(int deckCoverCard2) {
        this.deckCoverCard2 = deckCoverCard2;
    }

    public int getDeckCoverCard3() {
        return deckCoverCard3;
    }

    public void setDeckCoverCard3(int deckCoverCard3) {
        this.deckCoverCard3 = deckCoverCard3;
    }

    public int getDeckCase() {
        return deckCase;
    }

    public void setDeckCase(int deckCase) {
        this.deckCase = deckCase;
    }

    public int getDeckProtector() {
        return deckProtector;
    }

    public void setDeckProtector(int deckProtector) {
        this.deckProtector = deckProtector;
    }

    public long getLastDate() {return lastDate;}

    public void setLastDate(long lastDate) {
        this.lastDate = lastDate;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(int userId) {
        this.userId = userId;
    }
}
