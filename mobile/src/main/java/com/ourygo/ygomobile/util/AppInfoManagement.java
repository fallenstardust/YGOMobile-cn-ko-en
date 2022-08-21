package com.ourygo.ygomobile.util;

import android.content.SharedPreferences;

import com.ourygo.ygomobile.OYApplication;

import cn.garymb.ygomobile.App;
import cn.garymb.ygomobile.utils.SystemUtils;

/**
 * Create By feihua  On 2022/8/21
 */
public class AppInfoManagement {
    private static final AppInfoManagement ourInstance = new AppInfoManagement();
    private boolean isNewVersion;

    private AppInfoManagement() {
        isNewVersion = false;
    }

    public static AppInfoManagement getInstance() {
        return ourInstance;
    }

    public boolean isNewVersion() {
        if (isNewVersion)
            return true;
        SharedPreferences sh = App.get().getSharedPreferences("AppVersion", App.get().MODE_PRIVATE);
        int vercode = SystemUtils.getVersion(OYApplication.get());
        int vn = sh.getInt("versionCode", 0);
        if (vn < vercode) {
            sh.edit().putInt("versionCode", vercode).apply();
            isNewVersion = true;
        } else {
            isNewVersion = false;
        }
        return isNewVersion;
    }

    public void setNewVersion(boolean newVersion) {
        isNewVersion = newVersion;
    }

    //软件关闭时一定要调用
    public void close() {
        isNewVersion = false;
    }
}
