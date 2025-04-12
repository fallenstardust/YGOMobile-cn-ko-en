package cn.garymb.ygomobile.deck_square.api_response;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import java.util.List;

public class MyDeckResponse {
    public String code;
    public String message;
    public List<MyDeckData> data;

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public List<MyDeckData> getData() {
        return data;
    }

    public void setData(List<MyDeckData> data) {
        this.data = data;
    }

    public static class MyDeckData implements Parcelable {

        public String deckId;
        public String deckContributor;
        public String deckName;
        public String deckRank;
        public String deckLike;
        public String deckUploadDate;
        public String deckUpdateDate;
        public int deckCoverCard1;
        public int deckCoverCard2;
        public int deckCoverCard3;
        public String deckCase;
        public String deckProtector;
        public String deckMainSerial;
        public String deckYdk;
        public String userId;
        public String isPublic;
        public String isDelete;

        protected MyDeckData(Parcel in) {
            deckId = in.readString();
            deckContributor = in.readString();
            deckName = in.readString();
            deckRank = in.readString();
            deckLike = in.readString();
            deckUploadDate = in.readString();
            deckUpdateDate = in.readString();
            deckCoverCard1 = in.readInt();
            deckCoverCard2 = in.readInt();
            deckCoverCard3 = in.readInt();
            deckCase = in.readString();
            deckProtector = in.readString();
            deckMainSerial = in.readString();
            deckYdk = in.readString();
            userId = in.readString();
            isPublic = in.readString();
            isDelete = in.readString();
        }

        public static final Creator<MyDeckData> CREATOR = new Creator<MyDeckData>() {
            @Override
            public MyDeckData createFromParcel(Parcel in) {
                return new MyDeckData(in);
            }

            @Override
            public MyDeckData[] newArray(int size) {
                return new MyDeckData[size];
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
            dest.writeString(deckRank);
            dest.writeString(deckLike);
            dest.writeString(deckUploadDate);
            dest.writeString(deckUpdateDate);
            dest.writeInt(deckCoverCard1);
            dest.writeInt(deckCoverCard2);
            dest.writeInt(deckCoverCard3);
            dest.writeString(deckCase);
            dest.writeString(deckProtector);
            dest.writeString(deckMainSerial);
            dest.writeString(deckYdk);
            dest.writeString(userId);
            dest.writeString(isPublic);
            dest.writeString(isDelete);

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

        public String getDeckRank() {
            return deckRank;
        }

        public void setDeckRank(String deckRank) {
            this.deckRank = deckRank;
        }

        public String getDeckLike() {
            return deckLike;
        }

        public void setDeckLike(String deckLike) {
            this.deckLike = deckLike;
        }

        public String getDeckUploadDate() {
            return deckUploadDate;
        }

        public void setDeckUploadDate(String deckUploadDate) {
            this.deckUploadDate = deckUploadDate;
        }

        public String getDeckUpdateDate() {
            return deckUpdateDate;
        }

        public void setDeckUpdateDate(String deckUpdateDate) {
            this.deckUpdateDate = deckUpdateDate;
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

        public String getDeckCase() {
            return deckCase;
        }

        public void setDeckCase(String deckCase) {
            this.deckCase = deckCase;
        }

        public String getDeckProtector() {
            return deckProtector;
        }

        public void setDeckProtector(String deckProtector) {
            this.deckProtector = deckProtector;
        }

        public String getDeckMainSerial() {
            return deckMainSerial;
        }

        public void setDeckMainSerial(String deckMainSerial) {
            this.deckMainSerial = deckMainSerial;
        }

        public String getDeckYdk() {
            return deckYdk;
        }

        public void setDeckYdk(String deckYdk) {
            this.deckYdk = deckYdk;
        }

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getIsPublic() {
            return isPublic;
        }

        public void setIsPublic(String isPublic) {
            this.isPublic = isPublic;
        }

        public String getIsDelete() {
            return isDelete;
        }

        public void setIsDelete(String isDelete) {
            this.isDelete = isDelete;
        }
    }
}
