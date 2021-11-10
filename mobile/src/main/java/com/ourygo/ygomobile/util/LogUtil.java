package com.ourygo.ygomobile.util;

import android.util.Log;

/**
 * Create By feihua  On 2020/4/12
 */
public class LogUtil {

    private static long lastTime = 0;
    private static long sumTime=0;
    private static boolean isDebug=true;

    public static void time(String tag, String message) {
        long time = System.currentTimeMillis();
        if (lastTime == 0)
            lastTime = time;
        Log.e(tag, message + "  " + (time - lastTime));
        sumTime+=time-lastTime;
        lastTime = time;
    }

    public static void setLastTime(long lastTime) {
        LogUtil.lastTime = lastTime;
    }

    public static void setLastTime() {
        LogUtil.lastTime = System.currentTimeMillis();
    }

    public static void printSumTime(String tag) {
        Log.e(tag,   "SumTime：  " + getSumTime());
    }

    public static void printSumTimeAndClear(String tag) {
        Log.e(tag,   "SumTime：  " + getSumTimeAndClear());
    }

    public static long getSumTime() {
        return sumTime;
    }

    public static long getSumTimeAndClear() {
        long sumTime1=sumTime;
        sumTime=0;
        return sumTime1;
    }

    public static void e(String tag,String message){
        if (isDebug)
            Log.e(tag,message);
    }

    public static void d(String tag,String message){
        if (isDebug)
            Log.d(tag,message);
    }

}
