package cn.garymb.ygomobile;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import androidx.annotation.Keep;

import java.lang.reflect.Method;

import cn.garymb.ygomobile.interfaces.GameHost;

public abstract class GameApplication extends Application {

    private static GameApplication sGameApplication;
    private static String sProcessName;

    @Override
    public void onCreate() {
        super.onCreate();
        sProcessName = getCurrentProcessName();
        sGameApplication = this;
    }

    public static boolean isGameProcess(){
        return sProcessName != null && sProcessName.endsWith(":game");
    }

    public static String getAppProcessName() {
        return sProcessName;
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

    public static GameApplication get() {
        return sGameApplication;
    }

    @Keep
    public abstract GameHost getGameHost();

    public static boolean isDebug(){
        return get().getGameHost().isDebugMode();
    }

    /**
     * @deprecated
     */
    @Keep
    public boolean canNdkCash() {
        return true;
    }
}
