package cn.garymb.ygomobile;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.content.res.AssetManager;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import cn.garymb.ygomobile.core.IrrlichtBridge;


public abstract class GameApplication extends Application implements IrrlichtBridge.IrrlichtApplication {
    private SoundPool mSoundEffectPool;
    private Map<String, Integer> mSoundIdMap;

    private static GameApplication sGameApplication;
    private boolean isInitSoundEffectPool=false;
	private static String sProcessName;

    @Override
    public void onCreate() {
        super.onCreate();
		sProcessName = getCurrentProcessName();
        sGameApplication = this;
//        Reflection.unseal(this);
//        initSoundEffectPool();
    }
	
	public static boolean isGameProcess(){
        return sProcessName != null && sProcessName.endsWith(":game");
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
        mSoundEffectPool.release();
    }

    public boolean isInitSoundEffectPool() {
        return isInitSoundEffectPool;
    }

    protected void setInitSoundEffectPool(boolean initSoundEffectPool) {
        isInitSoundEffectPool = initSoundEffectPool;
    }

    public int getGameWidth(){
        return 1024;
    }

    public int getGameHeight(){
        return 640;
    }

    public abstract boolean isKeepScale();

    @SuppressWarnings("deprecation")
    public void initSoundEffectPool() {
        mSoundEffectPool = new SoundPool(2, AudioManager.STREAM_MUSIC, 0);
        AssetManager am = getAssets();
        String[] sounds;
        mSoundIdMap = new HashMap<String, Integer>();
        try {
            sounds = am.list("sound");
            for (String sound : sounds) {
                String path = "sound" + File.separator + sound;
                mSoundIdMap
                        .put(path, mSoundEffectPool.load(am.openFd(path), 1));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

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

    @Override
    public void playSoundEffect(String path) {
        Integer id = mSoundIdMap.get(path);
        if (id != null) {
            mSoundEffectPool.play(id, 0.5f, 0.5f, 2, 0, 1.0f);
        }
    }
	
	@SuppressLint({"PrivateApi", "DiscouragedPrivateApi"})
    protected String getCurrentProcessName() {
        if (Build.VERSION.SDK_INT >= 28) {
            return getProcessName();
        }
        try {
            Class<?> clazz = Class.forName("android.app.ActivityThread");
            Method currentProcessName = clazz.getDeclaredMethod("currentProcessName");
            return (String) currentProcessName.invoke(null);
        } catch (Throwable e) {
            Log.w("kk", "currentProcessName", e);
        }
        int pid = android.os.Process.myPid();
        String processName = getPackageName();
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningAppProcessInfo info : am.getRunningAppProcesses()) {
            if (info.pid == pid) {
                processName = info.processName;
                break;
            }
        }
        return processName;
    }
}
