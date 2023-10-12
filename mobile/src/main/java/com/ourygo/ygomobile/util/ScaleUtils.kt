package com.ourygo.ygomobile.util

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.util.TypedValue
import com.ourygo.ygomobile.OYApplication

/**
 * Create By feihua  On 2022/1/16
 */
object ScaleUtils {
    //dp转px
    fun dp2px(dpValue: Float): Int {
        val scale = OYApplication.get().resources.displayMetrics.density
        return (dpValue * scale + 0.5f).toInt()
    }

    //px转dp
    fun px2dp(pxValue: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            pxValue.toFloat(),
            OYApplication.get().resources.displayMetrics
        ).toInt()
    }

    /**
     * sp转换成px
     */
    fun sp2px(spValue: Float): Int {
        val fontScale = OYApplication.get().resources.displayMetrics.scaledDensity
        return (spValue * fontScale + 0.5f).toInt()
    }

    /**
     * px转换成sp
     */
    fun px2sp(pxValue: Float): Int {
        val fontScale = OYApplication.get().resources.displayMetrics.scaledDensity
        return (pxValue / fontScale + 0.5f).toInt()
    }

    val statusBarHeight: Int
        get() {
            val resources = OYApplication.get().resources
            val resourceId = resources.getIdentifier("status_bar_height", "dimen", "android")
            return resources.getDimensionPixelSize(resourceId)
        }

    fun getScreenInfo(context: Activity): IntArray {
        val screenWidth = context.windowManager.defaultDisplay.width // 屏幕宽（像素，如：480px）
        val screenHeight = context.windowManager.defaultDisplay.height // 屏幕高（像素，如：800p）
        return intArrayOf(screenHeight, screenWidth)
    }

    val isScreenOriatationPortrait: Boolean
        /**
         * 返回当前屏幕是否为竖屏。
         * @return 当且仅当当前屏幕为竖屏时返回true,否则返回false。
         */
        get() = OYApplication.get().resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT

    //判定当前的屏幕是竖屏还是横屏
    fun ScreenOrient(activity: Activity): Int {
        var orient = activity.requestedOrientation
        if (orient != ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE && orient != ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
            val windowManager = activity.windowManager
            val display = windowManager.defaultDisplay
            val screenWidth = display.width
            val screenHeight = display.height
            orient =
                if (screenWidth < screenHeight) ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        return orient
    }
}