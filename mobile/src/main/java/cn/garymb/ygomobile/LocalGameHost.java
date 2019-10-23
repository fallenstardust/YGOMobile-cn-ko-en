package cn.garymb.ygomobile;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.graphics.Point;
import android.util.Log;

import java.io.File;

import cn.garymb.ygomobile.core.YGOCore;
import cn.garymb.ygomobile.interfaces.GameConfig;
import cn.garymb.ygomobile.interfaces.GameHost;
import cn.garymb.ygomobile.interfaces.GameSize;
import cn.garymb.ygomobile.lite.BuildConfig;

class LocalGameHost extends GameHost {
    private Context context;
    private SharedPreferences settings;

    LocalGameHost(Context context) {
        super(context);
        this.context = context;
        settings = context.getSharedPreferences("ygo_settings", Context.MODE_PRIVATE);
        if(!GameApplication.isGameProcess()){
            Log.e("kk", "GameHost don't running in game process.");
        }
    }

    @Override
    public String getSetting(String key) {
        if (YGOCore.CONF_LAST_DECK.equals(key)) {
            return LocalConfig.getInstance(context).getLastDeck();
        } else if (YGOCore.CONF_LAST_CATEGORY.equals(key)) {
            return LocalConfig.getInstance(context).getLastCategory();
        } else {
            return settings.getString(key, null);
        }
    }

    @Override
    public int getIntSetting(String key, int def) {
        return settings.getInt(key, def);
    }

    @SuppressLint("ApplySharedPref")
    @Override
    public void saveIntSetting(String key, int value) {
        settings.edit().putInt(key, value).commit();
    }

    @SuppressLint("ApplySharedPref")
    @Override
    public void saveSetting(String key, String value) {
        if (YGOCore.CONF_LAST_DECK.equals(key)) {
            LocalConfig.getInstance(context).setLastDeck(value);
        } else if (YGOCore.CONF_LAST_CATEGORY.equals(key)) {
            LocalConfig.getInstance(context).setLastCategory(value);
        } else {
            settings.edit().putString(key, value).commit();
        }
    }

    @Override
    public void runWindbot(String cmd) {
        Intent intent = new Intent();
        intent.putExtra("args", cmd);
        intent.setAction("RUN_WINDBOT");
        context.sendBroadcast(intent);
    }

    @Override
    public AssetManager getGameAsset() {
        return context.getAssets();
    }

    @Override
    public GameSize getGameSize(Activity activity, GameConfig config) {
        boolean immerSiveMode = config.isImmerSiveMode();
        boolean keepScale = config.isKeepScale();
        int maxW, maxH;
        int fullW, fullH, actW, actH;
        Point size = new Point();
        activity.getWindowManager().getDefaultDisplay().getRealSize(size);
        fullW = size.x;
        fullH = size.y;
        actW = activity.getWindowManager().getDefaultDisplay().getWidth();
        actH = activity.getWindowManager().getDefaultDisplay().getHeight();
        int w1, h1;
        if (immerSiveMode) {
            w1 = fullW;
            h1 = fullH;
        } else {
            w1 = actW;
            h1 = actH;
        }
        maxW = Math.max(w1, h1);
        maxH = Math.min(w1, h1);
        Log.i("kk", "maxW=" + maxW + ",maxH=" + maxH);
        float sx, sy, scale;
        int gw, gh;
        if (keepScale) {
            sx = (float) maxW / YGOCore.GAME_WIDTH;
            sy = (float) maxH / YGOCore.GAME_HEIGHT;
            scale = Math.min(sx, sy);
            gw = (int) (YGOCore.GAME_WIDTH * scale);
            gh = (int) (YGOCore.GAME_HEIGHT * scale);
        } else {
            gw = maxW;
            gh = maxH;
        }
        Log.i("kk", "game=" + gw + "x" + gh);
        //fix touch point
        int left = (maxW - gw) / 2;
        int top = (maxH - gh) / 2;
        Log.i("kk", "touch fix=" + left + "x" + top);
        //if(huawei and liuhai){
        // left-=liuhai
        // }
        return new GameSize(gw, gh, left, top);
    }

    @Override
    public boolean isDebugMode() {
        return BuildConfig.DEBUG;
    }

    @Override
    public void onBeforeCreate(Activity activity) {
//        if(AppsSettings.get().isHideHwNotouch()){
//            HwNotchSizeUtil.setFullScreenWindowLayoutInDisplayCutout(activity);
//        }
    }

    @Override
    public void onAfterCreate(Activity activity) {

    }

    @Override
    public void onGameExit(Activity activity) {

    }
}
