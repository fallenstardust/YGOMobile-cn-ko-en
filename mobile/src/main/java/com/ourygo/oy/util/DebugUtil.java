package com.ourygo.oy.util;

import android.util.Log;

public class DebugUtil {

    private static long lastTime=0;

    public static void time(String tag){
        long currentTime=System.currentTimeMillis();
        Log.e(tag,currentTime-lastTime+"");
        lastTime=currentTime;
    }

}
