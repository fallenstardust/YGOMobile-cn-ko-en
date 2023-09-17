package cn.garymb.ygomobile.utils;

import android.util.Log;

import cn.garymb.ygomobile.lite.BuildConfig;

public class LogUtil {
    /**
     * error的不判断直接输出
     *
     * @param tag
     * @param message
     */
    public static void e(String tag, String message) {
        Log.e(tag, message);
    }

    public static void e(String tag, String message, Throwable e) {
        Log.e(tag, message, e);
    }

    public static void w(String tag, String message) {
        Log.w(tag, message);

    }

    public static void i(String tag, String message) {
        if (BuildConfig.DEBUG) {
            Log.i(tag, message);
        }
    }

    public static void d(String tag, String message) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, message);
        }
    }

    public static void v(String tag, String message) {
        if (BuildConfig.DEBUG) {
            Log.v(tag, message);
        }
    }
}