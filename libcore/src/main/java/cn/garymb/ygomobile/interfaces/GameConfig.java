package cn.garymb.ygomobile.interfaces;

import android.app.Activity;
import android.content.res.AssetManager;

import androidx.annotation.Keep;

import cn.garymb.ygomobile.NativeInitOptions;

@Keep
public interface GameConfig {

    boolean isKeepScale();

    NativeInitOptions getNativeInitOptions();

    boolean isLockScreenOrientation();

    boolean isSensorRefresh();

    /***
     * 隐藏底部导航栏
     */
    boolean isImmerSiveMode();

    boolean isEnableSoundEffect();

    AssetManager getGameAsset();

    int[] getGameSize(Activity activity);

    boolean isDebugMode();
}
