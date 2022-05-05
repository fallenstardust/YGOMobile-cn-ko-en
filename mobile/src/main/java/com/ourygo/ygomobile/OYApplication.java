package com.ourygo.ygomobile;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import androidx.multidex.MultiDex;

import com.ourygo.ygomobile.util.LogUtil;


import org.litepal.LitePal;

import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.App;
import cn.garymb.ygomobile.AppsSettings;

/**
 * Create By feihua  On 2021/2/10
 */
public class OYApplication extends App {

    public static final String BUGLY_ID="669adbac35";
    private static List<Activity> activitys = new ArrayList<>();
    private static int num = 0;
    public static String TAG="OYApplication";
    private static boolean isInitRes;

    @Override
    public void onCreate() {
        LogUtil.time(TAG,"准备初始化");
        String processName = getProcessName(OYApplication.this);
        if (processName != null) {
            if (processName.equals("com.ourygo.ygomobile") && num == 0) {
                num++;
                super.onCreate();
                isInitRes=false;
                registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {

                    @Override
                    public void onActivityCreated(Activity p1, Bundle p2) {
                        Log.e("OYApplication","入栈"+p1.getClass().getName());
                        activitys.add(p1);
                    }

                    @Override
                    public void onActivityStarted(Activity p1) {

                    }

                    @Override
                    public void onActivityResumed(Activity p1) {

                    }

                    @Override
                    public void onActivityPaused(Activity p1) {

                    }

                    @Override
                    public void onActivityStopped(Activity p1) {

                    }

                    @Override
                    public void onActivitySaveInstanceState(Activity p1, Bundle p2) {

                    }

                    @Override
                    public void onActivityDestroyed(Activity p1) {
                        Log.e("OYApplication","出栈"+p1.getClass().getName());
                        activitys.remove(p1);
                    }
                });
                LitePal.initialize(getApplicationContext());
                initUmeng();
                LogUtil.time(TAG,"初始化完毕");
            }else {
                AppsSettings.init(this);
            }
        }
    }

//    public void initUmeng() {
//        UMConfigure.preInit(OYApplication.get(), OYApplication.UM_KEY, OYApplication.CHANNEL);
//        UMConfigure.init(OYApplication.get(), OYApplication.UM_KEY, OYApplication.CHANNEL, UMConfigure.DEVICE_TYPE_PHONE,"");
//        MobclickAgent.setPageCollectionMode(MobclickAgent.PageMode.MANUAL);
//    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        MultiDex.install(this);
    }

    public static void setIsInitRes(boolean isInitRes) {
        OYApplication.isInitRes = isInitRes;
    }

    public static boolean isIsInitRes() {
        return isInitRes;
    }

    private String getProcessName(Context context) {
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (am==null)
            return null;
        List<ActivityManager.RunningAppProcessInfo> runningApps = am.getRunningAppProcesses();
        if (runningApps == null) {
            return null;
        }
        for (ActivityManager.RunningAppProcessInfo proInfo : runningApps) {
            if (proInfo.pid == android.os.Process.myPid()) {
                if (proInfo.processName != null) {
                    return proInfo.processName;
                }
            }
        }
        return null;
    }
}
