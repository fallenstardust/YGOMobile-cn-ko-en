package cn.garymb.ygomobile;


import android.content.Context;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatDelegate;

import com.bumptech.glide.Glide;
import com.yuyh.library.imgsel.ISNav;
import com.yuyh.library.imgsel.common.ImageLoader;

import cn.garymb.ygomobile.interfaces.GameConfig;
import cn.garymb.ygomobile.interfaces.GameHost;
import cn.garymb.ygomobile.utils.CrashHandler;
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
