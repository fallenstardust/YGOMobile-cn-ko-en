package cn.garymb.ygomobile;


import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.AssetManager;
import android.graphics.Point;
import android.os.Build;
import android.os.Process;
import android.util.Log;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatDelegate;

import com.bumptech.glide.Glide;
import com.yuyh.library.imgsel.ISNav;
import com.yuyh.library.imgsel.common.ImageLoader;

import java.lang.reflect.Method;

import cn.garymb.ygomobile.core.YGOCore;
import cn.garymb.ygomobile.interfaces.GameConfig;
import cn.garymb.ygomobile.interfaces.GameHost;
import cn.garymb.ygomobile.interfaces.GameSize;
import cn.garymb.ygomobile.lite.BuildConfig;
import cn.garymb.ygomobile.utils.CrashHandler;
import cn.garymb.ygomobile.utils.ScreenUtil;
import jonathanfinerty.once.Once;

public class App extends GameApplication {
    private GameHost gameHost;

    @Override
    public void onCreate() {
        super.onCreate();
        if (!isGameProcess()) {
            Once.initialise(this);
            AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
            //初始化图片选择器
            initImgsel();
//        QbSdk.initX5Environment(this, null);
//        QbSdk.setCurrentID("");
        }
        AppsSettings.init(this);
        //初始化异常工具类
        CrashHandler crashHandler = CrashHandler.getInstance();
        crashHandler.init(getApplicationContext());
        gameHost = new LocalGameHost(this);
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

    @Override
    public GameHost getGameHost() {
        return gameHost;
    }

    private class LocalGameHost extends GameHost {
        LocalGameHost(Context context) {
            super(context);
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
            sendBroadcast(intent);
        }

        @Override
        public AssetManager getGameAsset() {
            return getAssets();
        }

        @Override
        public GameSize getGameSize(Activity activity) {
            int maxW, maxH;
            int w1 = activity.getWindowManager().getDefaultDisplay().getWidth();
            int h1 = activity.getWindowManager().getDefaultDisplay().getHeight();
            maxW = Math.max(w1, h1);
            maxH = Math.min(w1, h1);
            if (AppsSettings.get().isImmerSiveMode()) {
                maxH += ScreenUtil.getCurrentNavigationBarHeight(activity);
            }
            float sx, sy, scale;
            int gw, gh;
            if (AppsSettings.get().isKeepScale()) {
                sx = (float) maxW / YGOCore.GAME_WIDTH;
                sy = (float) maxH / YGOCore.GAME_HEIGHT;
                scale = Math.min(sx, sy);
                gw = (int) (YGOCore.GAME_WIDTH * scale);
                gh = (int) (YGOCore.GAME_HEIGHT * scale);
            } else {
                gw = maxW;
                gh = maxH;
            }
            //fix touch point
            int left = (maxW - gw) / 2;
            int top = (maxH - gh) / 2;
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

    public static GameConfig genConfig() {
        GameConfig config = new GameConfig();
        config.setNativeInitOptions(AppsSettings.get().getNativeInitOptions());
        config.setLockScreenOrientation(AppsSettings.get().isLockScreenOrientation());
        config.setSensorRefresh(AppsSettings.get().isSensorRefresh());
        config.setImmerSiveMode(AppsSettings.get().isImmerSiveMode());
        config.setEnableSoundEffect(AppsSettings.get().isSoundEffect());
        return config;
    }
}
