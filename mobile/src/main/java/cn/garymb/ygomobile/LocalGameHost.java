package cn.garymb.ygomobile;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.graphics.Point;
import android.util.Log;

import cn.garymb.ygomobile.core.YGOCore;
import cn.garymb.ygomobile.interfaces.GameHost;
import cn.garymb.ygomobile.interfaces.GameSize;
import cn.garymb.ygomobile.lite.BuildConfig;
import cn.garymb.ygomobile.utils.ScreenUtil;

class LocalGameHost extends GameHost {
    private Context context;
    LocalGameHost(Context context) {
        super(context);
        this.context = context;
    }

    @Override
    public String getSetting(String key) {
        return AppsSettings.get().getSettings(key);
    }

    @Override
    public int getIntSetting(String key, int def) {
        return AppsSettings.get().getIntSettings(key, def);
    }

    @Override
    public void saveIntSetting(String key, int value) {
        AppsSettings.get().saveIntSettings(key, value);
    }

    @Override
    public void saveSetting(String key, String value) {
        AppsSettings.get().saveSettings(key, value);
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
    public GameSize getGameSize(Activity activity) {
        boolean immerSiveMode = AppsSettings.get().isImmerSiveMode();
        boolean keepScale = AppsSettings.get().isKeepScale();
        int maxW, maxH;
        int fullW,fullH,actW,actH;
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
    public boolean isAutoKeepGame() {
        return false;
    }
}
