package cn.garymb.ygomobile.utils;

import android.os.Build;

import java.lang.reflect.Method;

public class AndroidHideApi {
    private static boolean sBypassedP = false;

    public static void enableHideApi() {
        if (sBypassedP) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                Method forNameMethod = Class.class.getDeclaredMethod("forName", String.class);
                Class<?> clazz = (Class<?>) forNameMethod.invoke(null, "dalvik.system.VMRuntime");
                Method getMethodMethod = Class.class.getDeclaredMethod("getDeclaredMethod", String.class, Class[].class);
                Method getRuntime = (Method) getMethodMethod.invoke(clazz, "getRuntime", new Class[0]);
                Method setHiddenApiExemptions = (Method) getMethodMethod.invoke(clazz, "setHiddenApiExemptions", new Class[]{String[].class});
                Object runtime = getRuntime.invoke(null);
                setHiddenApiExemptions.invoke(runtime, new Object[]{
                        new String[]{
                                "Landroid/",
                                "Lcom/android/",
                                "Ljava/lang/",
                                "Ldalvik/system/",
                                "Llibcore/io/",
                                "Lhuawei/"
                        }
                });
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
        sBypassedP = true;
    }
}
