package cn.garymb.ygomobile.ui.file;

import android.os.Parcel;
import android.os.Parcelable;

public class FileOpenInfo implements Parcelable {
    private String mTitle;
    private String mFileFilter;
    private String mDefPath;
    private FileOpenType mType;
    private boolean showHide;

    public void setTitle(String title) {
        mTitle = title;
    }

    public void setFileFilter(String fileFilter) {
        mFileFilter = fileFilter;
    }

    public void setDefPath(String defPath) {
        mDefPath = defPath;
    }

    public void setType(FileOpenType type) {
        mType = type;
    }

    public String getTitle() {
        return mTitle;
    }

    public String getFileFilter() {
        return mFileFilter;
    }

    public String getDefPath() {
        return mDefPath;
    }

    public FileOpenType getType() {
        return mType;
    }

    public FileOpenInfo() {
    }
    public String getFileExtention(){
        if(mFileFilter==null){
            return "";
        }
        int i = mFileFilter.lastIndexOf(".");
        if(i>=0){
            return mFileFilter.substring(i);
        }
        return "";
    }
    public FileOpenInfo(String title, String fileFilter, boolean showHide, String defPath, FileOpenType type) {
        mTitle = title;
        mFileFilter = fileFilter;
        mDefPath = defPath;
        this.showHide = showHide;
        mType = type;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(this.mTitle);
        dest.writeString(this.mFileFilter);
        dest.writeString(this.mDefPath);
        dest.writeInt(this.mType == null ? -1 : this.mType.ordinal());
        dest.writeByte(this.showHide ? (byte) 1 : (byte) 0);
    }

    protected FileOpenInfo(Parcel in) {
        this.mTitle = in.readString();
        this.mFileFilter = in.readString();
        this.mDefPath = in.readString();
        int tmpMType = in.readInt();
        this.mType = tmpMType == -1 ? null : FileOpenType.values()[tmpMType];
        this.showHide = in.readByte() != 0;
    }

    public static final Creator<FileOpenInfo> CREATOR = new Creator<FileOpenInfo>() {
        @Override
        public FileOpenInfo createFromParcel(Parcel source) {
            return new FileOpenInfo(source);
        }

        @Override
        public FileOpenInfo[] newArray(int size) {
            return new FileOpenInfo[size];
        }
    };
}
