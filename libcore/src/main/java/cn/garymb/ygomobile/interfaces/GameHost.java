package cn.garymb.ygomobile.interfaces;

import android.app.Activity;
import android.content.Context;
import android.content.res.AssetManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;

import androidx.annotation.Keep;

import cn.garymb.ygomobile.tool.GameSoundPlayer;
import cn.garymb.ygomobile.tool.NetworkController;

@Keep
public abstract class GameHost {
    private final NetworkController mNetworkController;

    public GameHost(Context context) {
        mNetworkController = new NetworkController(context);
    }

    public abstract String getSetting(String key);

    public abstract int getIntSetting(String key, int def);

    public abstract void saveIntSetting(String key, int value);

    public abstract void saveSetting(String key, String value);

    public abstract void runWindbot(String cmd);

    public int getLocalAddr() {
        return mNetworkController.getIPAddress();
    }

    public abstract AssetManager getGameAsset();

    public abstract GameSize getGameSize(Activity activity, GameConfig config);

    public boolean isDebugMode(){
        return false;
    }

    public void onBeforeCreate(Activity activity){

    }

    public void onAfterCreate(Activity activity){

    }

    public void onGameExit(Activity activity){

    }

}
