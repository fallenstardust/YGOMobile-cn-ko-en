package cn.garymb.ygomobile;


import android.app.Activity;
import android.content.Intent;
import android.support.v7.app.AppCompatDelegate;

public class App extends GameApplication {

    @Override
    public void onCreate() {
        super.onCreate();
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
        AppsSettings.init(this);
        if (AppsSettings.get().isSoundEffect()) {
            initSoundEffectPool();
           setInitSoundEffectPool(true);
        }

//        QbSdk.initX5Environment(this, null);
//        QbSdk.setCurrentID("");
    }

    @Override
    public NativeInitOptions getNativeInitOptions() {
        NativeInitOptions options = AppsSettings.get().getNativeInitOptions();
        return options;
    }

    @Override
    public float getSmallerSize() {
        return AppsSettings.get().getSmallerSize();
    }

    @Override
    public void attachGame(Activity activity) {
        super.attachGame(activity);
        AppsSettings.get().update(activity);
    }

    @Override
    public float getXScale() {
        return AppsSettings.get().getXScale();
    }

    @Override
    public float getYScale() {
        return AppsSettings.get().getYScale();
    }

    @Override
    public String getCardImagePath() {
        return AppsSettings.get().getCardImagePath();
    }

    @Override
    public String getFontPath() {
        return AppsSettings.get().getFontPath();
    }


    @Override
    public void saveSetting(String key, String value) {
        AppsSettings.get().saveSettings(key, value);
    }

    @Override
    public String getSetting(String key) {
        return AppsSettings.get().getSettings(key);
    }

    @Override
    public int getIntSetting(String key, int def) {
        return AppsSettings.get().getIntSettings(key, def);
    }

    @Override
    public void saveIntSetting(String key, int value) {
        AppsSettings.get().saveIntSettings(key, value);
    }

    @Override
    public float getScreenWidth() {
        return AppsSettings.get().getScreenWidth();
    }

    @Override
    public boolean isLockSreenOrientation() {
        return AppsSettings.get().isLockSreenOrientation();
    }

    @Override
    public boolean canNdkCash() {
        return false;
    }

    @Override
    public boolean isImmerSiveMode() {
        return AppsSettings.get().isImmerSiveMode();
    }

    public boolean isSensorRefresh() {
        return AppsSettings.get().isSensorRefresh();
    }

    @Override
    public float getScreenHeight() {
        return AppsSettings.get().getScreenHeight();
    }

    @Override
    public void runWindbot(String args) {
        Intent intent = new Intent();
        intent.putExtra("args", args);
        intent.setAction("RUN_WINDBOT");
        getBaseContext().sendBroadcast(intent);
    }
}
