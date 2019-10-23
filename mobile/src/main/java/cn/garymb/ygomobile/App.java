package cn.garymb.ygomobile;


import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Point;
import android.util.Log;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatDelegate;

import com.bumptech.glide.Glide;
import com.yuyh.library.imgsel.ISNav;
import com.yuyh.library.imgsel.common.ImageLoader;

import cn.garymb.ygomobile.core.GameConfig;
import cn.garymb.ygomobile.core.GameSize;
import cn.garymb.ygomobile.core.IrrlichtBridge;
import cn.garymb.ygomobile.utils.CrashHandler;

public class App extends GameApplication {
    private SharedPreferences settings;
    private GameSize mGameSize = new GameSize();

    public SharedPreferences getSettings() {
        if (settings == null) {
            synchronized (this) {
                if (settings == null) {
                    settings = getSharedPreferences("ygo_settings", Context.MODE_PRIVATE);
                }
            }
        }
        if(!isGameProcess()){
            Log.e("kk", "don't running in game process");
        }
        return settings;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
        AppsSettings.init(this);
        //初始化异常工具类
        CrashHandler crashHandler = CrashHandler.getInstance();
        crashHandler.init(getApplicationContext());
        //初始化图片选择器
        initImgsel();
//        QbSdk.initX5Environment(this, null);
//        QbSdk.setCurrentID("");
    }


    @Override
    public GameSize getGameSize(Activity activity) {
        boolean immerSiveMode = getGameConfig().isImmerSiveMode();
        boolean keepScale = getGameConfig().isKeepScale();
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
            sx = (float) maxW / IrrlichtBridge.GAME_WIDTH;
            sy = (float) maxH / IrrlichtBridge.GAME_HEIGHT;
            scale = Math.min(sx, sy);
            gw = (int) (IrrlichtBridge.GAME_WIDTH * scale);
            gh = (int) (IrrlichtBridge.GAME_HEIGHT * scale);
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
        mGameSize =  new GameSize(gw, gh, left, top);
        return mGameSize;
    }

    @Override
    public float getXScale() {
        if (mGameSize == null) {
            //TODO error
            return 1.0f;
        }
        return mGameSize.getWidth() / IrrlichtBridge.GAME_WIDTH;
    }

    @Override
    public float getYScale() {
        if (mGameSize == null) {
            //TODO error
            return 1.0f;
        }
        return mGameSize.getHeight() / IrrlichtBridge.GAME_HEIGHT;
    }

    @SuppressLint("ApplySharedPref")
    @Override
    public void saveSetting(String key, String value) {
        if (Constants.CONF_LAST_DECK.equals(key)) {
            LocalConfig.getInstance(this).setLastDeck(value);
        } else if (Constants.CONF_LAST_CATEGORY.equals(key)) {
            LocalConfig.getInstance(this).setLastCategory(value);
        } else {
            getSettings().edit().putString(key, value).commit();
        }
    }

    @Override
    public String getSetting(String key) {
        if (Constants.CONF_LAST_DECK.equals(key)) {
            return LocalConfig.getInstance(this).getLastDeck();
        } else if (Constants.CONF_LAST_CATEGORY.equals(key)) {
            return LocalConfig.getInstance(this).getLastCategory();
        } else {
            return getSettings().getString(key, null);
        }
    }

    @Override
    public int getIntSetting(String key, int def) {
        return getSettings().getInt(key, def);
    }

    @SuppressLint("ApplySharedPref")
    @Override
    public void saveIntSetting(String key, int value) {
        getSettings().edit().putInt(key, value).commit();
    }

    @Override
    public void runWindbot(String args) {
        Intent intent = new Intent();
        intent.putExtra("args", args);
        intent.setAction("RUN_WINDBOT");
        getBaseContext().sendBroadcast(intent);
    }

    private void initImgsel() {
        // 自定义图片加载器
        ISNav.getInstance().init(new ImageLoader() {
            @Override
            public void displayImage(Context context, String path, ImageView imageView) {
                Glide.with(context).load(path).into(imageView);
            }
        });
    }
}
