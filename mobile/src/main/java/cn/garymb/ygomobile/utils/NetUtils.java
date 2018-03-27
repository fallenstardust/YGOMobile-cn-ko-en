package cn.garymb.ygomobile.utils;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

/**
 * Created by keyongyu on 2017/2/1.
 */

public class NetUtils {
    private static ConnectivityManager sConnectivityManager;
    public static boolean isWifiConnected(Context context) {
        if (sConnectivityManager == null) {
            synchronized (NetUtils.class) {
                if (sConnectivityManager == null) {
                    sConnectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
                }
            }
        }
        NetworkInfo networkInfo = sConnectivityManager.getActiveNetworkInfo();
        return networkInfo != null && networkInfo.getType() == ConnectivityManager.TYPE_WIFI;
    }
    public static boolean isConnected(Context context) {
        if (sConnectivityManager == null) {
            synchronized (NetUtils.class) {
                if (sConnectivityManager == null) {
                    sConnectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
                }
            }
        }
        return sConnectivityManager.getActiveNetworkInfo() != null;
    }
}
