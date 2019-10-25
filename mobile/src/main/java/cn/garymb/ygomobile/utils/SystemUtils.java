package cn.garymb.ygomobile.utils;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.DisplayMetrics;
import android.view.Display;

import java.lang.reflect.Method;

import cn.garymb.ygomobile.lite.BuildConfig;

public class SystemUtils {
    public static String getVersionName(Context context) {
        PackageInfo packageInfo = null;
        try {
            packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException e) {
        }
        if (packageInfo != null) {
            return packageInfo.versionName;
        }
        return "?";
    }

    public static int getVersion(Context context) {
//        PackageInfo packageInfo = null;
//        try {
//            packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
//        } catch (PackageManager.NameNotFoundException e) {
//        }
//        if (packageInfo != null) {
//            return packageInfo.versionCode;
//        }
        return BuildConfig.VERSION_CODE;
    }

    public static DisplayMetrics getHasVirtualDisplayMetrics(Activity context) {
        int dpi = 0;
        Display display = context.getWindowManager().getDefaultDisplay();
        DisplayMetrics dm = new DisplayMetrics();
        Class<?> c;
        try {
            c = Class.forName("android.view.Display");
            Method method = c.getMethod("getRealMetrics", DisplayMetrics.class);
            method.invoke(display, dm);
            dpi = dm.heightPixels;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return dm;
    }
}
