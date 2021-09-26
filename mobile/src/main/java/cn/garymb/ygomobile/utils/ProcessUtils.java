package cn.garymb.ygomobile.utils;

import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.os.Process;

public class ProcessUtils {

    public static String getCurrentProcessName(Application application) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return Application.getProcessName();
        }
        try {
            return FileUtils.readToString("/proc/self/cmdline").trim();
        } catch (Throwable e) {
            //ignore
        }
        return getProcessName(application);
    }

    private static String getProcessName(Context context) {
        int pid = Process.myPid();
        String processName = null;
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningAppProcessInfo info : am.getRunningAppProcesses()) {
            if (info.pid == pid) {
                processName = info.processName;
                break;
            }
        }
        if (processName == null) {
            return context.getPackageName();
        }
        return processName;
    }
}
