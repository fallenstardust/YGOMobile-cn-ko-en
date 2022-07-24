package cn.garymb.ygomobile;


import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatDelegate;

import com.tencent.bugly.Bugly;
import com.tencent.bugly.beta.Beta;
import com.tencent.smtt.export.external.TbsCoreSettings;
import com.tencent.smtt.sdk.QbSdk;
import com.yuyh.library.imgsel.ISNav;
import com.yuyh.library.imgsel.common.ImageLoader;

import java.util.HashMap;

import cn.garymb.ygomobile.lite.BuildConfig;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.home.MainActivity;
import cn.garymb.ygomobile.utils.CrashHandler;
import cn.garymb.ygomobile.utils.ProcessUtils;
import cn.garymb.ygomobile.utils.glide.GlideCompat;

public class App extends GameApplication {

    @Override
    public void onCreate() {
        super.onCreate();
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true);
        AppsSettings.init(this);
        //初始化异常工具类
        CrashHandler crashHandler = CrashHandler.getInstance();
        crashHandler.init(getApplicationContext());
        //初始化bugly
        initBugly();
        if(!ProcessUtils.getCurrentProcessName(this).endsWith(":game")){
            //初始化图片选择器
            initImgsel();
            //x5
            HashMap map = new HashMap();
            map.put(TbsCoreSettings.TBS_SETTINGS_USE_SPEEDY_CLASSLOADER, true);
            map.put(TbsCoreSettings.TBS_SETTINGS_USE_DEXLOADER_SERVICE, true);
            QbSdk.initTbsSettings(map);
        }
    }

    @Override
    public NativeInitOptions getNativeInitOptions() {
        return AppsSettings.get().getNativeInitOptions();
    }

    @Override
    public float getSmallerSize() {
        return AppsSettings.get().getSmallerSize();
    }

    @Override
    public void attachGame(Activity activity) {
        super.attachGame(activity);
        AppsSettings.get().update(activity);
    }

    @Override
    public float getXScale() {
        return AppsSettings.get().getXScale(getGameWidth(), getGameHeight());
    }

    @Override
    public float getYScale() {
        return AppsSettings.get().getYScale(getGameWidth(), getGameHeight());
    }
//
//    @Override
//    public int getGameHeight() {
//        return 720;
//    }
//
//    @Override
//    public int getGameWidth() {
//        return 1280;
//    }

    @Override
    public String getCardImagePath() {
        return AppsSettings.get().getCardImagePath();
    }

    @Override
    public String getFontPath() {
        return AppsSettings.get().getFontPath();
    }

    @Override
    public boolean isKeepScale() {
        return AppsSettings.get().isKeepScale();
    }

    @Override
    public void saveSetting(String key, String value) {
        AppsSettings.get().saveSettings(key, value);
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
    public float getScreenWidth() {
        return AppsSettings.get().getScreenWidth();
    }

    @Override
    public boolean isLockSreenOrientation() {
        return AppsSettings.get().isLockSreenOrientation();
    }

    @Override
    public boolean canNdkCash() {
        return false;
    }

    @Override
    public boolean isImmerSiveMode() {
        return AppsSettings.get().isImmerSiveMode();
    }

    public boolean isSensorRefresh() {
        return AppsSettings.get().isSensorRefresh();
    }

    @Override
    public float getScreenHeight() {
        return AppsSettings.get().getScreenHeight();
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
                GlideCompat.with(context).load(path).into(imageView);
            }
        });
    }

    public void initBugly() {
        Beta.initDelay = 0;
        Beta.showInterruptedStrategy = true;
        Beta.largeIconId = R.drawable.ic_icon_round;
        Beta.defaultBannerId = R.drawable.ic_icon_round;
        Beta.strToastYourAreTheLatestVersion = this.getString(R.string.Already_Lastest);
        Beta.strToastCheckingUpgrade = this.getString(R.string.Checking_Update);
        Beta.upgradeDialogLayoutId = R.layout.dialog_upgrade;
        Beta.enableHotfix = false;
        Beta.autoCheckHotfix = false;
        Beta.autoCheckUpgrade = false;
        Beta.autoCheckAppUpgrade = false;
        //添加可显示弹窗的Activity
        Beta.canShowUpgradeActs.add(MainActivity.class);
        ApplicationInfo appInfo = null;
        try {
            appInfo = this.getPackageManager().getApplicationInfo(getPackageName(), PackageManager.GET_META_DATA);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        String msg = appInfo.metaData.getString("BUGLY_APPID");
        Bugly.init(this, msg, BuildConfig.DEBUG_MODE);
    }
}
