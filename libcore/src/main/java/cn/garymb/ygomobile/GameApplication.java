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

import cn.garymb.ygomobile.core.GameConfig;
import cn.garymb.ygomobile.core.GameSize;
import cn.garymb.ygomobile.core.IrrlichtBridge;


public abstract class GameApplication extends Application implements IrrlichtBridge.IrrlichtApplication {
    private SoundPool mSoundEffectPool;
    private Map<String, Integer> mSoundIdMap;
    private GameConfig gameConfig = new GameConfig();
    private static GameApplication sGameApplication;
    private boolean isInitSoundEffectPool=false;
	private static String sProcessName;

    public boolean setGameConfig(GameConfig gameConfig) {
        if (!this.gameConfig.equals(gameConfig)) {
            this.gameConfig = gameConfig;
            Log.i("kk", "setGameConfig:" + gameConfig);
            if(gameConfig.isEnableSoundEffect()){
                initSoundEffectPool();
                setInitSoundEffectPool(true);
            }
            return true;
        }
        return false;
    }

    public final GameConfig getGameConfig() {
        return gameConfig;
    }

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

    public abstract GameSize getGameSize(Activity activity);

    public NativeInitOptions getNativeInitOptions() {
        return getGameConfig().getNativeInitOptions();
    }

    @Override
    public abstract float getXScale();

    @Override
    public abstract float getYScale();

    @Override
    public String getCardImagePath() {
        return getGameConfig().getImagePath();
    }

    @Override
    public String getFontPath() {
        return getGameConfig().getFontPath();
    }

    public boolean isKeepScale() {
        return getGameConfig().isKeepScale();
    }

    public boolean isLockSreenOrientation() {
        return getGameConfig().isLockScreenOrientation();
    }

    /***
     * 隐藏底部导航栏
     */
    public boolean isImmerSiveMode() {
        return getGameConfig().isImmerSiveMode();
    }

    public boolean isSensorRefresh() {
        return getGameConfig().isSensorRefresh();
    }


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
