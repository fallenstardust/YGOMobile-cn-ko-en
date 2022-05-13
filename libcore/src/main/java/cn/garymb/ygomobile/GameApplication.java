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
import com.umeng.analytics.MobclickAgent;
import com.umeng.commonsdk.UMConfigure;

public abstract class GameApplication extends Application implements IrrlichtBridge.IrrlichtApplication {

    private static GameApplication sGameApplication;
    private static final String UM_KEY = "618e15c1e014255fcb77324a";
    private static final String CHANNEL = "群文件";
//Group File

    @Override
    public void onCreate() {
        super.onCreate();
        initUmeng();
//        Reflection.unseal(this);
    }

    public void initUmeng() {
        UMConfigure.preInit(getApplicationContext(), UM_KEY, CHANNEL);
        UMConfigure.init(getApplicationContext(), UM_KEY, CHANNEL, UMConfigure.DEVICE_TYPE_PHONE,"");
        MobclickAgent.setPageCollectionMode(MobclickAgent.PageMode.MANUAL);
        UMConfigure.setLogEnabled(true);
    }

    public static GameApplication get() {
        return sGameApplication;
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        sGameApplication = this;
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
