package cn.garymb.ygomobile;

import android.text.TextUtils;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public final class NativeInitOptions {

    private static final int BUFFER_MAX_SIZE = 8192;

    public int mOpenglVersion;

    //工作目录
    public String mWorkPath;

    // /data/data/cards.cdb;a.cdb;b.cdb
    public final List<String> mDbList;

    //pics.zip;scripts.zip;a.zip;b.zip
    public final List<String> mArchiveList;

    public int mCardQuality;

    public boolean mIsFontAntiAliasEnabled;

    public boolean mIsPendulumScaleEnabled;

    public NativeInitOptions() {
        mDbList = new ArrayList<>();
        mArchiveList = new ArrayList<>();
    }

    public ByteBuffer toNativeBuffer() {
        ByteBuffer buffer = ByteBuffer.allocateDirect(BUFFER_MAX_SIZE);
        putInt(buffer, mOpenglVersion);
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
        return buffer;
    }

    @Override
    public String toString() {
        return "NativeInitOptions{" +
                "mOpenglVersion=" + mOpenglVersion +
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
}
