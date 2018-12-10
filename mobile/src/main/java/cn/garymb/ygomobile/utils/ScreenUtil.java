package cn.garymb.ygomobile.utils;

import android.app.Activity;
import android.content.Context;
import android.graphics.Rect;
import android.os.Build;
import android.support.annotation.RequiresApi;
import android.util.Log;
import android.view.Display;
import android.view.DisplayCutout;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

//屏幕工具类
public class ScreenUtil {


    public static final int VIVO_NOTCH = 0x00000020;//是否有刘海
    public static final int VIVO_FILLET = 0x00000008;//是否有圆角


    //是否是刘海屏
    public static boolean isNotchInScreen(Activity activity) {
        if (!(isNotchOPPO(activity) || isNotchVivo(activity) || isNotchHuawei(activity))||isNotchXiaomi(activity)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                return isNotchP(activity);
            else
                return false;
        }
        return true;
    }

    //获取刘海高度
    public static int getNotchHeight(Activity activity) {
        int NotchHeight=0;
        if ((NotchHeight=getNotchHeightOPPO(activity))!=0)
            return NotchHeight;
        if ((NotchHeight=getNotchHeightVivo(activity))!=0)
            return NotchHeight;
        if ((NotchHeight=getNotchHeightHuawei(activity))!=0)
            return NotchHeight;
        if ((NotchHeight=getNotchHeightXiaomi(activity))!=0)
            return NotchHeight;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if ((NotchHeight=getNotchHeightP(activity))!=0)
                return NotchHeight;
        }
        return 0;
    }


    @RequiresApi(api = Build.VERSION_CODES.P)
    public static int getNotchHeightP(Activity activity) {
        DisplayCutout displayCutout = getDisplayCutout(activity);
        if (displayCutout == null)
            return 0;
        return displayCutout.getSafeInsetTop();
//        Log.e("TAG", "安全区域距离屏幕左边的距离 SafeInsetLeft:" + displayCutout.getSafeInsetLeft());
//        Log.e("TAG", "安全区域距离屏幕右部的距离 SafeInsetRight:" + displayCutout.getSafeInsetRight());
//        Log.e("TAG", "安全区域距离屏幕顶部的距离 SafeInsetTop:" + displayCutout.getSafeInsetTop());
//        Log.e("TAG", "安全区域距离屏幕底部的距离 SafeInsetBottom:" + displayCutout.getSafeInsetBottom());


    }

    @RequiresApi(api = Build.VERSION_CODES.P)
    public static DisplayCutout getDisplayCutout(Activity activity) {
        return activity.getWindow().getDecorView().getRootWindowInsets().getDisplayCutout();
    }


    //小米的状态栏高度会略高于刘海屏的高度，因此可以通过获取状态栏的高度来间接避开刘海屏
    public static int getNotchHeightXiaomi(Activity activity) {
        if (isNotchXiaomi(activity))
            return 0;
        return getStatusBarHeight(activity);
    }

    //获取状态栏高度
    public static int getStatusBarHeight(Context context) {
        int statusBarHeight = 0;
        int resourceId = context.getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (resourceId > 0) {
            statusBarHeight = context.getResources().getDimensionPixelSize(resourceId);
        }
        return statusBarHeight;
    }


    //OPPO是否有刘海
    public static boolean isNotchOPPO(Context context) {
        return context.getPackageManager().hasSystemFeature("com.oppo.feature.screen.heteromorphism");
    }

    //vivo是否有刘海
    public static boolean isNotchVivo(Context context) {
        boolean ret = false;
        try {
            ClassLoader classLoader = context.getClassLoader();
            Class FtFeature = classLoader.loadClass("android.util.FtFeature");
            Method method = FtFeature.getMethod("isFeatureSupport", int.class);
            ret = (boolean) method.invoke(FtFeature, VIVO_NOTCH);
        } catch (ClassNotFoundException e) {
            Log.e("Notch", "hasNotchAtVivo ClassNotFoundException");
        } catch (NoSuchMethodException e) {
            Log.e("Notch", "hasNotchAtVivo NoSuchMethodException");
        } catch (Exception e) {
            Log.e("Notch", "hasNotchAtVivo Exception");
        } finally {
            return ret;
        }
    }

    //华为是否有刘海
    public static boolean isNotchHuawei(Context context) {
        boolean ret = false;
        try {
            ClassLoader classLoader = context.getClassLoader();
            Class HwNotchSizeUtil = classLoader.loadClass("com.huawei.android.util.HwNotchSizeUtil");
            Method get = HwNotchSizeUtil.getMethod("hasNotchInScreen");
            ret = (boolean) get.invoke(HwNotchSizeUtil);
        } catch (ClassNotFoundException e) {
            Log.e("Notch", "hasNotchAtHuawei ClassNotFoundException");
        } catch (NoSuchMethodException e) {
            Log.e("Notch", "hasNotchAtHuawei NoSuchMethodException");
        } catch (Exception e) {
            Log.e("Notch", "hasNotchAtHuawei Exception");
        } finally {
            return ret;
        }
    }

    public static boolean isNotchXiaomi(Activity activity){
        return getInt("ro.miui.notch",activity)==1;
    }

    /**
     * 小米刘海屏判断.
     * @return 0 if it is not notch ; return 1 means notch
     * @throws IllegalArgumentException if the key exceeds 32 characters
     */
    public static int getInt(String key,Activity activity) {
        int result = 0;
        if (ROMUtil.isXiaomi()){
            try {
                ClassLoader classLoader = activity.getClassLoader();
                @SuppressWarnings("rawtypes")
                Class SystemProperties = classLoader.loadClass("android.os.SystemProperties");
                //参数类型
                @SuppressWarnings("rawtypes")
                Class[] paramTypes = new Class[2];
                paramTypes[0] = String.class;
                paramTypes[1] = int.class;
                Method getInt = SystemProperties.getMethod("getInt", paramTypes);
                //参数
                Object[] params = new Object[2];
                params[0] = new String(key);
                params[1] = new Integer(0);
                result = (Integer) getInt.invoke(SystemProperties, params);

            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            } catch (NoSuchMethodException e) {
                e.printStackTrace();
            } catch (IllegalAccessException e) {
                e.printStackTrace();
            } catch (IllegalArgumentException e) {
                e.printStackTrace();
            } catch (InvocationTargetException e) {
                e.printStackTrace();
            }
        }
        return result;
    }

    //其他安卓p的机子是否有刘海
    @RequiresApi(api = Build.VERSION_CODES.P)
    public static boolean isNotchP(Activity activity) {
        DisplayCutout displayCutout = getDisplayCutout(activity);
        if (displayCutout == null)
            return false;
        List<Rect> rects = displayCutout.getBoundingRects();
        return rects == null || rects.size() == 0;
//        if (rects == null || rects.size() == 0) {
//            Log.e("TAG", "不是刘海屏");
//        } else {
//            Log.e("TAG", "刘海屏数量:" + rects.size());
//            for (Rect rect : rects) {
//                Log.e("TAG", "刘海屏区域：" + rect);
//            }
//        }
    }


    //获取oppo刘海高度
    public static int getNotchHeightOPPO(Context context) {
        if (isNotchVivo(context)) {
            //oppo刘海区域则都是宽度为324px, 高度为80px
            return (int) DensityUtils.px2dp(context, 80);
        }
        return 0;
    }

    //获取vivo刘海高度
    public static int getNotchHeightVivo(Context context) {
        if (isNotchVivo(context)) {
            //vivo不提供接口获取刘海尺寸，目前vivo的刘海宽为100dp,高为27dp。
            return 27;
        }
        return 0;
    }


    //获取华为刘海高度
    public static int getNotchHeightHuawei(Context context) {
        if (isNotchHuawei(context))
            return getNotchSizeAtHuawei(context)[1];
        return 0;
    }

    //获取华为刘海尺寸：width、height
    //int[0]值为刘海宽度 int[1]值为刘海高度
    public static int[] getNotchSizeAtHuawei(Context context) {
        int[] ret = new int[]{0, 0};
        try {
            ClassLoader cl = context.getClassLoader();
            Class HwNotchSizeUtil = cl.loadClass("com.huawei.android.util.HwNotchSizeUtil");
            Method get = HwNotchSizeUtil.getMethod("getNotchSize");
            ret = (int[]) get.invoke(HwNotchSizeUtil);
        } catch (ClassNotFoundException e) {
            Log.e("Notch", "getNotchSizeAtHuawei ClassNotFoundException");
        } catch (NoSuchMethodException e) {
            Log.e("Notch", "getNotchSizeAtHuawei NoSuchMethodException");
        } catch (Exception e) {
            Log.e("Notch", "getNotchSizeAtHuawei Exception");
        } finally {
            return ret;
        }
    }


}
