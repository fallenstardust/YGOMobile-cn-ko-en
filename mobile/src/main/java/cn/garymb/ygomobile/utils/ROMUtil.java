package cn.garymb.ygomobile.utils;

import android.os.Build;

//ROM工具类
public class ROMUtil {

    // 是否是小米手机
    public static boolean isXiaomi() {
        return "Xiaomi".equals(Build.MANUFACTURER);
    }

}
