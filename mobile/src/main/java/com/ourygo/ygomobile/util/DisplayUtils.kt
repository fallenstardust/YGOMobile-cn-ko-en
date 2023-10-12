package com.ourygo.ygomobile.util

import android.R
import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.media.ThumbnailUtils
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.annotation.ColorInt
import androidx.annotation.Px
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils

/**
 * Display utils.
 */
object DisplayUtils {
    private const val MAX_TABLET_ADAPTIVE_LIST_WIDTH_DIP_PHONE = 512
    private const val MAX_TABLET_ADAPTIVE_LIST_WIDTH_DIP_TABLET = 600
    private const val MIN = 19
    fun dpToPx(context: Context, dp: Float): Float {
        return dp * (context.resources.displayMetrics.densityDpi / 160f)
    }

    fun spToPx(context: Context, sp: Int): Float {
        return sp * context.resources.displayMetrics.scaledDensity
    }

    /**
     *
     * @param context 上下文对象
     * @param window  窗口对象
     * @param statusShader     是否对状态栏着色
     * @param lightStatus      是否亮色状态栏（白底,否则是暗底）
     * @param navigationShader 是否对底栏着色
     * @param lightNavigation  是否亮色底栏
     */
    fun setSystemBarStyle(
        context: Context?, window: Window,
        statusShader: Boolean, lightStatus: Boolean,
        navigationShader: Boolean, lightNavigation: Boolean
    ) {
        setSystemBarStyle(
            context, window,
            false, statusShader, lightStatus, navigationShader, lightNavigation
        )
    }

    /**
     * 为头部有照片的设置沉浸式状态栏
     */
    fun immersiveInImage(activity: Activity) {
        if (Build.VERSION.SDK_INT < MIN) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            activity.window.statusBarColor = Color.TRANSPARENT
            activity.window.decorView.systemUiVisibility =
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
        } else {
            val translucentView = activity.window.decorView.findViewById<View>(R.id.custom)
            if (translucentView != null) translucentView.visibility = View.GONE
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        }
    }

    fun setSystemBarStyle(
        context: Context?, window: Window, miniAlpha: Boolean,
        statusShader: Boolean, lightStatus: Boolean,
        navigationShader: Boolean, lightNavigation: Boolean
    ) {
        var lightStatus = lightStatus
        var navigationShader = navigationShader
        var lightNavigation = lightNavigation
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return
        }

        // statusShader &= Build.VERSION.SDK_INT < Build.VERSION_CODES.Q;
        lightStatus = lightStatus and (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
        navigationShader = navigationShader and (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
        lightNavigation = lightNavigation and (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        var visibility =  //                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            //                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        if (lightStatus) {
            visibility = visibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }
        if (lightNavigation) {
            visibility = visibility or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        }
        window.decorView.systemUiVisibility = visibility
        setSystemBarColor(
            context,
            window,
            miniAlpha,
            statusShader,
            lightStatus,
            navigationShader,
            lightNavigation
        )
    }

    fun setSystemBarColor(
        context: Context?, window: Window, miniAlpha: Boolean,
        statusShader: Boolean, lightStatus: Boolean,
        navigationShader: Boolean, lightNavigation: Boolean
    ) {
        var lightStatus = lightStatus
        var navigationShader = navigationShader
        var lightNavigation = lightNavigation
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return
        }

        // statusShader &= Build.VERSION.SDK_INT < Build.VERSION_CODES.Q;
        lightStatus = lightStatus and (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
        navigationShader = navigationShader and (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
        lightNavigation = lightNavigation and (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        if (!statusShader) {
            window.statusBarColor = Color.argb(0, 0, 0, 0)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.statusBarColor =
                getStatusBarColor23(context, lightStatus, miniAlpha)
        } else {
            window.statusBarColor = statusBarColor21
        }
        if (!navigationShader) {
            window.navigationBarColor = Color.argb(0, 0, 0, 0)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            window.navigationBarColor = getStatusBarColor26(
                context,
                lightNavigation,
                miniAlpha
            )
        } else {
            window.navigationBarColor = navigationBarColor21
        }
    }

    @get:RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    @get:ColorInt
    private val statusBarColor21: Int
        get() = ColorUtils.setAlphaComponent(Color.BLACK, (0.1 * 255).toInt())

    @ColorInt
    @RequiresApi(Build.VERSION_CODES.M)
    private fun getStatusBarColor23(context: Context?, light: Boolean, miniAlpha: Boolean): Int {
        return if (miniAlpha) {
            if (light) ColorUtils.setAlphaComponent(
                Color.WHITE,
                (0.2 * 255).toInt()
            ) else ColorUtils.setAlphaComponent(
                Color.BLACK,
                (0.1 * 255).toInt()
            )
        } else ColorUtils.setAlphaComponent(
            ContextCompat.getColor(
                context!!,
                if (light) cn.garymb.ygomobile.lite.R.color.colorRoot_light else cn.garymb.ygomobile.lite.R.color.colorRoot_dark
            ), (0.8 * 255).toInt()
        )
    }

    @get:RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    @get:ColorInt
    private val navigationBarColor21: Int
        get() = ColorUtils.setAlphaComponent(Color.BLACK, (0.1 * 255).toInt())

    @ColorInt
    @RequiresApi(Build.VERSION_CODES.O)
    private fun getStatusBarColor26(context: Context?, light: Boolean, miniAlpha: Boolean): Int {
        return if (miniAlpha) {
            if (light) ColorUtils.setAlphaComponent(
                Color.WHITE,
                (0.2 * 255).toInt()
            ) else ColorUtils.setAlphaComponent(
                Color.BLACK,
                (0.1 * 255).toInt()
            )
        } else ColorUtils.setAlphaComponent(
            ContextCompat.getColor(
                context!!,
                if (light) cn.garymb.ygomobile.lite.R.color.colorRoot_light else cn.garymb.ygomobile.lite.R.color.colorRoot_dark
            ), (0.8 * 255).toInt()
        )
    }

    fun isTabletDevice(context: Context): Boolean {
        return (context.resources.configuration.screenLayout
                and Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_LARGE
    }

    fun isLandscape(context: Context): Boolean {
        return context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    }

    fun isDarkMode(context: Context): Boolean {
        return (context.resources.configuration.uiMode
                and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    fun drawableToBitmap(drawable: Drawable): Bitmap {
        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth,
            drawable.intrinsicHeight,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, drawable.intrinsicWidth, drawable.intrinsicHeight)
        drawable.draw(canvas)
        return bitmap
    }

    @ColorInt
    fun bitmapToColorInt(bitmap: Bitmap): Int {
        return ThumbnailUtils.extractThumbnail(bitmap, 1, 1)
            .getPixel(0, 0)
    }

    fun isLightColor(@ColorInt color: Int): Boolean {
        val alpha = 0xFF shl 24
        var grey = color
        val red = grey and 0x00FF0000 shr 16
        val green = grey and 0x0000FF00 shr 8
        val blue = grey and 0x000000FF
        grey = (red * 0.3 + green * 0.59 + blue * 0.11).toInt()
        grey = alpha or (grey shl 16) or (grey shl 8) or grey
        return grey > -0x424243
    }

    @Px
    fun getTabletListAdaptiveWidth(context: Context, @Px width: Int): Int {
        return if (!isTabletDevice(context) && !isLandscape(
                context
            )
        ) {
            width
        } else Math.min(
            width.toFloat(),
            dpToPx(
                context,
                (
                        if (isTabletDevice(context)) MAX_TABLET_ADAPTIVE_LIST_WIDTH_DIP_TABLET else MAX_TABLET_ADAPTIVE_LIST_WIDTH_DIP_PHONE
                        ).toFloat()
            )
        ).toInt()
    }
}