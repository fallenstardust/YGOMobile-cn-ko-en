package cn.garymb.ygomobile;


import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatDelegate;

import com.bumptech.glide.Glide;
import com.yuyh.library.imgsel.ISNav;
import com.yuyh.library.imgsel.common.ImageLoader;

import cn.garymb.ygomobile.interfaces.GameConfig;
import cn.garymb.ygomobile.interfaces.GameHost;
import cn.garymb.ygomobile.utils.CrashHandler;
import cn.garymb.ygomobile.utils.ScreenUtil;
import libwindbot.windbot.WindBot;

public class App extends GameApplication {
    private GameHost gameHost;

    @Override
    public void onCreate() {
        super.onCreate();
        if (!isGameProcess()) {
            AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
            //初始化图片选择器
            initImgsel();
//        QbSdk.initX5Environment(this, null);
//        QbSdk.setCurrentID("");
            AppsSettings.init(this);
        }
        //初始化异常工具类
        CrashHandler crashHandler = CrashHandler.getInstance();
        crashHandler.init(getApplicationContext());
        if(getPackageName().equals(getAppProcessName())){
            registerReceiver(new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String args = intent.getStringExtra("args");
                    WindBot.runAndroid(args);
                }
            }, new IntentFilter(Constants.ACTION_WINDBOT));
        }
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
        if(gameHost == null) {
            synchronized (this) {
                if(gameHost == null) {
                    gameHost = new LocalGameHost(this);
                }
            }
        }
        return gameHost;
    }

    public static GameConfig genConfig() {
        GameConfig config = new GameConfig();
        config.setNotchHeight(AppsSettings.get().getNotchHeight());
        config.setNativeInitOptions(AppsSettings.get().getNativeInitOptions());
        config.setLockScreenOrientation(AppsSettings.get().isLockScreenOrientation());
        config.setSensorRefresh(AppsSettings.get().isSensorRefresh());
        config.setImmerSiveMode(AppsSettings.get().isImmerSiveMode());
        config.setEnableSoundEffect(AppsSettings.get().isSoundEffect());
        config.setKeepScale(AppsSettings.get().isKeepScale());
        return config;
    }
}
