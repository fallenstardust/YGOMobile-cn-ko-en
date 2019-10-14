package cn.garymb.ygomobile.core;

import android.content.res.AssetManager;

import cn.garymb.ygomobile.NativeInitOptions;

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
}
