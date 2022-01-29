package com.ourygo.ygomobile.util;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.util.TypedValue;
import android.view.Display;
import android.view.WindowManager;

import com.ourygo.ygomobile.OYApplication;

/**
 * Create By feihua  On 2022/1/16
 */
public class ScaleUtils {
    //dp转px
    public static int dp2px(float dpValue) {
        final float scale = OYApplication.get().getResources().getDisplayMetrics().density;
        return (int) (dpValue * scale + 0.5f);
    }
    //px转dp
    public static int px2dp(int pxValue) {
        return ((int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, pxValue, OYApplication.get().getResources().getDisplayMetrics()));
    }

    /**
     * sp转换成px
     */
    public static int sp2px(float spValue){
        float fontScale=OYApplication.get().getResources().getDisplayMetrics().scaledDensity;
        return (int) (spValue*fontScale+0.5f);
    }
    /**
     * px转换成sp
     */
    public static int px2sp(float pxValue){
        float fontScale=OYApplication.get().getResources().getDisplayMetrics().scaledDensity;
        return (int) (pxValue/fontScale+0.5f);
    }

    public static int getStatusBarHeight() {
        Resources resources = OYApplication.get().getResources();
        int resourceId = resources.getIdentifier("status_bar_height", "dimen", "android");
        return resources.getDimensionPixelSize(resourceId);
    }

    public static int[] getScreenInfo(Activity context){
        int screenWidth = context.getWindowManager().getDefaultDisplay().getWidth(); // 屏幕宽（像素，如：480px）
        int screenHeight = context.getWindowManager().getDefaultDisplay().getHeight(); // 屏幕高（像素，如：800p）
        return new int[]{screenHeight,screenWidth};
    }

    /**
     * 返回当前屏幕是否为竖屏。
     * @return 当且仅当当前屏幕为竖屏时返回true,否则返回false。
     */
    public static boolean isScreenOriatationPortrait() {
         return OYApplication.get().getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
    }

    //判定当前的屏幕是竖屏还是横屏
    public static int ScreenOrient(Activity activity)
    {
        int orient = activity.getRequestedOrientation();
        if(orient != ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE && orient != ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
            WindowManager windowManager = activity.getWindowManager();
            Display display = windowManager.getDefaultDisplay();
            int screenWidth  = display.getWidth();
            int screenHeight = display.getHeight();
            orient = screenWidth < screenHeight ?  ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE:ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
        }
        return orient;
    }

}