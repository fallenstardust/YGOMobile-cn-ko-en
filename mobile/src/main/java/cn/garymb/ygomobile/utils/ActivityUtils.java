package cn.garymb.ygomobile.utils;

import android.app.Activity;
import android.os.Build;

public class ActivityUtils {
    /**
     * 判断Activity是否在运行
     *
     * @param activity
     * @return
     */
    public static boolean isActivityExist(Activity activity) {
        if (activity == null || activity.isFinishing()) {
            return false;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            if (activity.isDestroyed()) {
                return false;
            }
        }

        return true;
    }
}
