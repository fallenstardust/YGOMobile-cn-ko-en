package cn.garymb.ygomobile.interfaces;

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
    protected GameSoundPlayer mPlayer;
    private NetworkController mNetworkController;
    private boolean mEnableSound;

    public GameHost(Context context) {
        mPlayer = new GameSoundPlayer(getAssetManager(context));
        mNetworkController = new NetworkController(context);
    }

    public void setEnableSound(boolean enableSound) {
        this.mEnableSound = enableSound;
        if(enableSound){
            mPlayer.initSoundEffectPool();
        }else{
            mPlayer.release();
        }
    }

    public boolean isEnableSound() {
        return mEnableSound;
    }

    protected AssetManager getAssetManager(Context context) {
        return context.getAssets();
    }

    public abstract String getSetting(String key);

    public abstract int getIntSetting(String key, int def);

    public abstract void saveIntSetting(String key, int value);

    public abstract void saveSetting(String key, String value);

    public abstract void runWindbot(String cmd);

    public void playSoundEffect(String path){
        if(isEnableSound()) {
            mPlayer.playSoundEffect(path);
        }
    }

    public int getLocalAddr() {
        return mNetworkController.getIPAddress();
    }

}
