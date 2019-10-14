package cn.garymb.ygomobile.core;

import android.content.Context;
import android.content.res.AssetManager;

import java.nio.ByteBuffer;

import cn.garymb.ygomobile.NativeInitOptions;

public abstract class GameHost {
    protected GameSoundPlayer mPlayer;

    public GameHost(Context context) {
        mPlayer = new GameSoundPlayer(getAssetManager(context));
    }

    public void initSoundEffectPool() {
        mPlayer.initSoundEffectPool();
    }

    protected AssetManager getAssetManager(Context context) {
        return context.getAssets();
    }

    public abstract String getSetting(String key);

    public abstract int getIntSetting(String key, int def);

    public abstract void saveIntSetting(String key, int value);

    public abstract void saveSetting(String key, String value);

    public abstract void runWindbot(String cmd);

    public int getWindowWidth(){return 0;}

    public int getWindowHeight(){return 0;}

    public ByteBuffer getInitOptions(){return null;}

    public void attachNativeDevice(int device){}

    public void playSoundEffect(String path){
        mPlayer.playSoundEffect(path);
    }

    public int getLocalAddr() {
        return 0;
    }

    public void toggleIME(boolean show, String message) {
    }

    public void performHapticFeedback() {
    }

    public void showComboBoxCompat(String[] items, boolean isShow, int mode) {
    }
}
