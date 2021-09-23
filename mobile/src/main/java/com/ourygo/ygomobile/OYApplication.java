package com.ourygo.ygomobile;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import androidx.multidex.MultiDex;

import com.ourygo.ygomobile.util.LogUtil;

import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.App;
import cn.garymb.ygomobile.AppsSettings;

/**
 * Create By feihua  On 2021/2/10
 */
public class OYApplication extends App {

    private static List<Activity> activitys = new ArrayList<>();
    private static int num = 0;
    private String TAG="OYApplication";

    @Override
    public void onCreate() {
        LogUtil.time(TAG,"准备初始化");
        String processName = getProcessName(OYApplication.this);
        if (processName != null) {
            if (processName.equals("com.ourygo.ygomobile") && num == 0) {
                num++;
                super.onCreate();
                registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {

                    @Override
                    public void onActivityCreated(Activity p1, Bundle p2) {
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
                        activitys.remove(p1);
                    }
                });
                LogUtil.time(TAG,"初始化完毕");
            }else {
                AppsSettings.init(this);
            }
        }
    }

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        MultiDex.install(this);
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
