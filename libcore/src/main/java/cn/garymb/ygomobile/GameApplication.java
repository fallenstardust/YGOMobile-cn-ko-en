package cn.garymb.ygomobile;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.res.AssetManager;
import android.media.AudioManager;
import android.media.SoundPool;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import cn.garymb.ygomobile.core.IrrlichtBridge;


public abstract class GameApplication extends Application implements IrrlichtBridge.IrrlichtApplication {

    private static GameApplication sGameApplication;

    @Override
    public void onCreate() {
        super.onCreate();
        sGameApplication = this;
//        Reflection.unseal(this);

    }

    public static GameApplication get() {
        return sGameApplication;
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
    }

    public int getGameWidth(){
        return 1024;
    }

    public int getGameHeight(){
        return 640;
    }

    public abstract boolean isKeepScale();

    public abstract NativeInitOptions getNativeInitOptions();

    public abstract float getSmallerSize();

    public abstract boolean isLockSreenOrientation();

    public abstract boolean isSensorRefresh();

    /**
     * @deprecated
     */
    public boolean canNdkCash() {
        return true;
    }

    public void attachGame(Activity activity) {

    }

    /***
     * 隐藏底部导航栏
     */
    public abstract boolean isImmerSiveMode();

}
