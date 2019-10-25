package cn.garymb.ygomobile.tool;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

/**
 * @author mabin
 */
public final class NetworkController {

    private WifiManager mWM;
    private ConnectivityManager mCM;

    /**
     *
     */
    public NetworkController(Context context) {
        mWM = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        mCM = (ConnectivityManager) context
                .getSystemService(Context.CONNECTIVITY_SERVICE);
    }

    /**
     * @return
     * @author: mabin
     **/
    public boolean isWifiConnected() {
        boolean isWifiEnabled = mWM.isWifiEnabled();
        NetworkInfo ni = mCM.getActiveNetworkInfo();
        if (isWifiEnabled && null != ni && ni.isConnected()
                && ni.getType() == ConnectivityManager.TYPE_WIFI) {
            return true;
        }
        return false;
    }

    public int getIPAddress() {
        if (!isWifiConnected()) {
            return -1;
        }
        WifiInfo wi = mWM.getConnectionInfo();
        return wi.getIpAddress();
    }
}
