package cn.garymb.ygomobile;

import android.app.Application;

import androidx.annotation.Keep;

import cn.garymb.ygomobile.interfaces.GameConfig;
import cn.garymb.ygomobile.interfaces.GameHost;


public abstract class GameApplication extends Application {

    private static GameApplication sGameApplication;

    @Override
    public void onCreate() {
        super.onCreate();
        sGameApplication = this;
    }

    public static GameApplication get() {
        return sGameApplication;
    }

    @Keep
    public abstract GameConfig getConfig();

    @Keep
    public abstract GameHost getGameHost();

    public static boolean isDebug(){
        return get().getConfig().isDebugMode();
    }

    /**
     * @deprecated
     */
    @Keep
    public boolean canNdkCash() {
        return true;
    }
}
