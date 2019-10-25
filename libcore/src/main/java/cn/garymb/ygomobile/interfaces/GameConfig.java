package cn.garymb.ygomobile.interfaces;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.Keep;

import java.util.Objects;

import cn.garymb.ygomobile.NativeInitOptions;

@Keep
public class GameConfig implements Parcelable {

    public static final String EXTRA_CONFIG = "ygo_config";

    private NativeInitOptions nativeInitOptions;

    private boolean lockScreenOrientation;

    private boolean sensorRefresh;

    private boolean keepScale;

    /***
     * 隐藏底部导航栏
     */
    private boolean immerSiveMode;

    private boolean enableSoundEffect;

    private int notchHeight;

    public int getNotchHeight() {
        return notchHeight;
    }

    public void setNotchHeight(int notchHeight) {
        this.notchHeight = notchHeight;
    }

    public NativeInitOptions getNativeInitOptions() {
        return nativeInitOptions;
    }

    public void setNativeInitOptions(NativeInitOptions nativeInitOptions) {
        this.nativeInitOptions = nativeInitOptions;
    }

    public boolean isLockScreenOrientation() {
        return lockScreenOrientation;
    }

    public void setLockScreenOrientation(boolean lockScreenOrientation) {
        this.lockScreenOrientation = lockScreenOrientation;
    }

    public boolean isSensorRefresh() {
        return sensorRefresh;
    }

    public void setSensorRefresh(boolean sensorRefresh) {
        this.sensorRefresh = sensorRefresh;
    }

    public boolean isImmerSiveMode() {
        return immerSiveMode;
    }

    public void setImmerSiveMode(boolean immerSiveMode) {
        this.immerSiveMode = immerSiveMode;
    }

    public boolean isEnableSoundEffect() {
        return enableSoundEffect;
    }

    public void setEnableSoundEffect(boolean enableSoundEffect) {
        this.enableSoundEffect = enableSoundEffect;
    }

    public boolean isKeepScale() {
        return keepScale;
    }

    public void setKeepScale(boolean keepScale) {
        this.keepScale = keepScale;
    }

    public GameConfig() {
        nativeInitOptions = new NativeInitOptions();
        lockScreenOrientation = false;
        sensorRefresh = true;
        immerSiveMode = false;
        enableSoundEffect = true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        GameConfig config = (GameConfig) o;
        return lockScreenOrientation == config.lockScreenOrientation &&
                sensorRefresh == config.sensorRefresh &&
                keepScale == config.keepScale &&
                immerSiveMode == config.immerSiveMode &&
                enableSoundEffect == config.enableSoundEffect &&
                Objects.equals(nativeInitOptions, config.nativeInitOptions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nativeInitOptions, lockScreenOrientation, sensorRefresh, keepScale, immerSiveMode, enableSoundEffect);
    }


    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(this.nativeInitOptions, flags);
        dest.writeByte(this.lockScreenOrientation ? (byte) 1 : (byte) 0);
        dest.writeByte(this.sensorRefresh ? (byte) 1 : (byte) 0);
        dest.writeByte(this.keepScale ? (byte) 1 : (byte) 0);
        dest.writeByte(this.immerSiveMode ? (byte) 1 : (byte) 0);
        dest.writeByte(this.enableSoundEffect ? (byte) 1 : (byte) 0);
        dest.writeInt(this.notchHeight);
    }

    protected GameConfig(Parcel in) {
        this.nativeInitOptions = in.readParcelable(NativeInitOptions.class.getClassLoader());
        this.lockScreenOrientation = in.readByte() != 0;
        this.sensorRefresh = in.readByte() != 0;
        this.keepScale = in.readByte() != 0;
        this.immerSiveMode = in.readByte() != 0;
        this.enableSoundEffect = in.readByte() != 0;
        this.notchHeight = in.readInt();
    }

    public static final Creator<GameConfig> CREATOR = new Creator<GameConfig>() {
        @Override
        public GameConfig createFromParcel(Parcel source) {
            return new GameConfig(source);
        }

        @Override
        public GameConfig[] newArray(int size) {
            return new GameConfig[size];
        }
    };

    @Override
    public String toString() {
        return "GameConfig{" +
                "nativeInitOptions=" + nativeInitOptions +
                ", lockScreenOrientation=" + lockScreenOrientation +
                ", sensorRefresh=" + sensorRefresh +
                ", keepScale=" + keepScale +
                ", immerSiveMode=" + immerSiveMode +
                ", enableSoundEffect=" + enableSoundEffect +
                ", notchHeight=" + notchHeight +
                '}';
    }
}
