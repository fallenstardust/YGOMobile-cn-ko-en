package cn.garymb.ygomobile;

import android.app.Application;

import cn.garymb.ygomobile.core.GameConfig;
import cn.garymb.ygomobile.core.GameHost;


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

    public abstract GameConfig getConfig();

    public abstract GameHost getGameHost();

    /**
     * @deprecated
     */
    public boolean canNdkCash() {
        return true;
    }
}
