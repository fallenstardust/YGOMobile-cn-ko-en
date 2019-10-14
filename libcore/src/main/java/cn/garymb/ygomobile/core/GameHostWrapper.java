package cn.garymb.ygomobile.core;

import android.content.Context;

import java.nio.ByteBuffer;

public class GameHostWrapper extends GameHost {
    private GameHost mBase;

    public GameHostWrapper(Context context, GameHost base) {
        super(context);
        mBase = base;
    }

    @Override
    public String getSetting(String key) {
        return mBase.getSetting(key);
    }

    @Override
    public int getIntSetting(String key, int def) {
        return mBase.getIntSetting(key, def);
    }

    @Override
    public void saveIntSetting(String key, int value) {
        mBase.saveIntSetting(key, value);
    }

    @Override
    public void saveSetting(String key, String value) {
        mBase.saveSetting(key, value);
    }

    @Override
    public void runWindbot(String cmd) {
        mBase.runWindbot(cmd);
    }

    @Override
    public void playSoundEffect(String name) {
        mBase.playSoundEffect(name);
    }

    @Override
    public int getLocalAddr() {
        return mBase.getLocalAddr();
    }

    @Override
    public void toggleIME(boolean show, String message) {
        mBase.toggleIME(show, message);
    }

    @Override
    public void performHapticFeedback() {
        mBase.performHapticFeedback();
    }

    @Override
    public void showComboBoxCompat(String[] items, boolean isShow, int mode) {
        mBase.showComboBoxCompat(items, isShow, mode);
    }

    @Override
    public ByteBuffer getInitOptions() {
        return mBase.getInitOptions();
    }

    @Override
    public int getWindowWidth() {
        return mBase.getWindowWidth();
    }

    @Override
    public int getWindowHeight() {
        return mBase.getWindowHeight();
    }

    @Override
    public void attachNativeDevice(int device) {
        mBase.attachNativeDevice(device);
    }
}
