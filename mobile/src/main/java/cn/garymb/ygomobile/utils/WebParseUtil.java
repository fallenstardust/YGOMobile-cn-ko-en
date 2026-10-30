package cn.garymb.ygomobile.utils;

import android.text.TextUtils;

/**
 * 用于解析html网页内容的工具类
 */
public class WebParseUtil {
    /**
     * 检查字符串是否是IPv4
     */
    public static boolean isValidIP(String s) {
        if (TextUtils.isEmpty(s)) {
            return false;
        }
        String[] arr = s.split("\\.");
        if (arr.length != 4) {
            return false;
        }
        for (String value : arr) {
            try {
                int n = Integer.parseInt(value);
                if (!(n >= 0 && n <= 255)) {
                    return false;
                }
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }
}

