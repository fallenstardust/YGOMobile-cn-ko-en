package cn.garymb.ygomobile;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextUtils;

import androidx.annotation.Keep;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class NativeInitOptions implements Parcelable {

    private static final int BUFFER_MAX_SIZE = 8192;

    public int mOpenglVersion;
    public boolean mIsSoundEffectEnabled;

    //工作目录
    public String mWorkPath;

    // /data/data/cards.cdb;a.cdb;b.cdb
    public final List<String> mDbList;

    //pics.zip;scripts.zip;a.zip;b.zip
    public final List<String> mArchiveList;

    public int mCardQuality;

    public boolean mIsFontAntiAliasEnabled;

    public boolean mIsPendulumScaleEnabled;

    public String mFontFile;
    public String mResDir;
    public String mImageDir;

    public NativeInitOptions() {
        mDbList = new ArrayList<>();
        mArchiveList = new ArrayList<>();
    }

    public ByteBuffer toNativeBuffer() {
        ByteBuffer buffer = ByteBuffer.allocateDirect(BUFFER_MAX_SIZE);
        putInt(buffer, mOpenglVersion);
        putInt(buffer, mIsSoundEffectEnabled ? 1 : 0);
        putInt(buffer, mCardQuality);
        putInt(buffer, mIsFontAntiAliasEnabled ? 1 : 0);
        putInt(buffer, mIsPendulumScaleEnabled ? 1 : 0);

        putString(buffer, mWorkPath);
        putInt(buffer, mDbList.size());
        for(String str:mDbList){
            putString(buffer, str);
        }
        putInt(buffer, mArchiveList.size());
        for (String str : mArchiveList) {
            putString(buffer, str);
        }
        putString(buffer, mFontFile);
        putString(buffer, mResDir);
        putString(buffer, mImageDir);
        return buffer;
    }

    @Override
    public String toString() {
        return "NativeInitOptions{" +
                "mOpenglVersion=" + mOpenglVersion +
                ", mIsSoundEffectEnabled=" + mIsSoundEffectEnabled +
                ", mWorkPath='" + mWorkPath + '\'' +
                ", mDbList='" + mDbList + '\'' +
                ", mArchiveList='" + mArchiveList + '\'' +
                ", mCardQuality=" + mCardQuality +
                ", mIsFontAntiAliasEnabled=" + mIsFontAntiAliasEnabled +
                ", mIsPendulumScaleEnabled=" + mIsPendulumScaleEnabled +
                '}';
    }

    private void putString(ByteBuffer buffer, String str) {
        if (TextUtils.isEmpty(str)) {
            buffer.putInt(Integer.reverseBytes(0));
        } else {
            buffer.putInt(Integer.reverseBytes(str.getBytes().length));
            buffer.put(str.getBytes());
        }
    }

    @SuppressWarnings("unused")
    private void putChar(ByteBuffer buffer, char value) {
        Short svalue = (short) value;
        buffer.putShort((Short.reverseBytes(svalue)));
    }

    private void putInt(ByteBuffer buffer, int value) {
        buffer.putInt((Integer.reverseBytes(value)));
    }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NativeInitOptions options = (NativeInitOptions) o;
        return mOpenglVersion == options.mOpenglVersion &&
                mIsSoundEffectEnabled == options.mIsSoundEffectEnabled &&
                mCardQuality == options.mCardQuality &&
                mIsFontAntiAliasEnabled == options.mIsFontAntiAliasEnabled &&
                mIsPendulumScaleEnabled == options.mIsPendulumScaleEnabled &&
                Objects.equals(mWorkPath, options.mWorkPath) &&
                Objects.equals(mDbList, options.mDbList) &&
                Objects.equals(mArchiveList, options.mArchiveList) &&
                Objects.equals(mFontFile, options.mFontFile) &&
                Objects.equals(mResDir, options.mResDir) &&
                Objects.equals(mImageDir, options.mImageDir);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mOpenglVersion, mIsSoundEffectEnabled, mWorkPath, mDbList, mArchiveList, mCardQuality, mIsFontAntiAliasEnabled, mIsPendulumScaleEnabled, mFontFile, mResDir, mImageDir);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(this.mOpenglVersion);
        dest.writeByte(this.mIsSoundEffectEnabled ? (byte) 1 : (byte) 0);
        dest.writeString(this.mWorkPath);
        dest.writeStringList(this.mDbList);
        dest.writeStringList(this.mArchiveList);
        dest.writeInt(this.mCardQuality);
        dest.writeByte(this.mIsFontAntiAliasEnabled ? (byte) 1 : (byte) 0);
        dest.writeByte(this.mIsPendulumScaleEnabled ? (byte) 1 : (byte) 0);
        dest.writeString(this.mFontFile);
        dest.writeString(this.mResDir);
        dest.writeString(this.mImageDir);
    }

    protected NativeInitOptions(Parcel in) {
        this.mOpenglVersion = in.readInt();
        this.mIsSoundEffectEnabled = in.readByte() != 0;
        this.mWorkPath = in.readString();
        this.mDbList = in.createStringArrayList();
        this.mArchiveList = in.createStringArrayList();
        this.mCardQuality = in.readInt();
        this.mIsFontAntiAliasEnabled = in.readByte() != 0;
        this.mIsPendulumScaleEnabled = in.readByte() != 0;
        this.mFontFile = in.readString();
        this.mResDir = in.readString();
        this.mImageDir = in.readString();
    }

    public static final Parcelable.Creator<NativeInitOptions> CREATOR = new Parcelable.Creator<NativeInitOptions>() {
        @Override
        public NativeInitOptions createFromParcel(Parcel source) {
            return new NativeInitOptions(source);
        }

        @Override
        public NativeInitOptions[] newArray(int size) {
            return new NativeInitOptions[size];
        }
    };
}
