package cn.garymb.ygomobile.ex_card;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.List;

public class ExCardLogItem implements Parcelable {
    private int count;
    private String dateTime;
    private List<String> logs;

    public ExCardLogItem(int count, String dateTime, List<String> logs) {
        this.count = count;
        this.dateTime = dateTime;
        this.logs = logs;
    }

    protected ExCardLogItem(Parcel in) {
        count = in.readInt();
        dateTime = in.readString();
        logs = in.createStringArrayList();
    }

    public static final Creator<ExCardLogItem> CREATOR = new Creator<ExCardLogItem>() {
        @Override
        public ExCardLogItem createFromParcel(Parcel in) {
            return new ExCardLogItem(in);
        }

        @Override
        public ExCardLogItem[] newArray(int size) {
            return new ExCardLogItem[size];
        }
    };

    public String getDateTime() {
        return dateTime;
    }

    public void setDateTime(String dateTime) {
        this.dateTime = dateTime;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        this.count = count;
    }

    public List<String> getLogs() {
        return logs;
    }

    public void setLogs(List<String> logs) {
        this.logs = logs;
    }


    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(count);
        dest.writeString(dateTime);
        dest.writeStringList(logs);
    }
}
