package cn.garymb.ygomobile.utils;

import android.content.Context;
import android.content.res.Configuration;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;

/**
 * 横屏弹窗（ui/dialogs 下的 PopupWindow 型对话框）等比缩放工具。
 *
 * <p>需求：横屏时让每个对话框的尺寸随 Activity（决斗界面）宽高等比例变化，
 * 使高分辨率平板上对话框既不显小也不变形（保持原宽高比）。
 *
 * <p>方案：以 gframe 横屏逻辑基准 {@link #BASE_W}×{@link #BASE_H} 为设计尺寸，
 * 缩放系数 {@code factor = clamp(min(屏宽/BASE_W, 屏高/BASE_H), MIN, MAX)}。
 * 竖屏或非放大场景返回 1.0（行为完全不变）。系数同时作用于：
 * <ul>
 *   <li>{@link #wrap(Context)}：返回按系数覆写 density 的 Context，供
 *       {@code LayoutInflater.from(...)} 与所有 dp/sp/padding 资源解析，
 *       使 XML 布局与文字随屏幕等比放大（density 覆写对 dp 与 sp 同步生效，故不变形）；</li>
 *   <li>{@link #factor(Context)}：供代码内 {@code dp()}/{@code dp2px()}/{@code density}
 *       换算统一乘以该系数，使以 dp 常量设定的弹窗宽高、按钮尺寸同步放大。</li>
 * </ul>
 * 由于宽度按 min(宽比, 高比) 取值，放大后的弹窗不会超出屏幕短边，避免溢出与变形。
 */
public final class DialogScale {

    /** 横屏设计基准宽（px）：对应 gframe 常规横屏逻辑分辨率宽度 */
    private static final float BASE_W = 1280f;
    /** 横屏设计基准高（px） */
    private static final float BASE_H = 800f;
    /** 缩放下限：不缩小，保持手机横屏原样 */
    private static final float MIN_SCALE = 1.0f;
    /** 缩放上限：平板上最多放大 1.6 倍，避免弹窗过大撑爆屏幕 */
    private static final float MAX_SCALE = 1.6f;

    private DialogScale() {
    }

    /** 当前显示是否横屏（宽不小于高）；取实时显示度量，免疫缩放上下文派生值 */
    public static boolean isLandscape(Context context) {
        if (context == null) return false;
        DisplayMetrics dm = screenMetrics(context);
        return dm.widthPixels >= dm.heightPixels;
    }

    /**
     * 实时物理显示度量：{@link #factor(Context)}/{@link #dpToPx(Context, float)}/
     * {@link #wrap(Context)} 解算的唯一度量来源。{@link #wrap} 经
     * createConfigurationContext 覆写 densityDpi 后，该上下文的 Resources 派生
     * widthPixels/heightPixels 已含放大系数（=真实 px×系数）；弹窗包装层等持有缩放
     * 上下文回流本类解算时，旧实现按派生度量再乘一次系数，产生 f² 复利放大与
     * 限宽失效（竖屏转横屏后弹窗尺寸超出当前 Activity 比例的根源）。WindowManager
     * 的显示度量不受 Resources 覆写影响且随旋转实时更新；不可得时回退上下文度量。
     */
    public static DisplayMetrics screenMetrics(Context context) {
        DisplayMetrics dm = new DisplayMetrics();
        if (context != null) {
            try {
                Object ws = context.getSystemService(Context.WINDOW_SERVICE);
                if (ws instanceof WindowManager) {
                    Display display = ((WindowManager) ws).getDefaultDisplay();
                    if (display != null) {
                        display.getRealMetrics(dm);
                        if (dm.widthPixels > 0 && dm.heightPixels > 0) return dm;
                    }
                }
            } catch (Throwable ignored) {
                // 非界面上下文取不到 WindowManager，走下方回退
            }
            DisplayMetrics src = context.getResources().getDisplayMetrics();
            if (src.widthPixels > 0 && src.heightPixels > 0) {
                dm.widthPixels = src.widthPixels;
                dm.heightPixels = src.heightPixels;
                dm.density = src.density;
                dm.densityDpi = src.densityDpi;
                dm.scaledDensity = src.scaledDensity;
                dm.xdpi = src.xdpi;
                dm.ydpi = src.ydpi;
            }
        }
        return dm;
    }

    /**
     * 横屏等比缩放系数：
     * 【需求变更】以竖屏显示尺寸为统一标准——无论横屏还是竖屏都返回 1.0，
     * 使横屏启动时对话框不会因 density 覆写而被放大，保证横竖屏对话框物理尺寸一致，
     * 避免玩家旋转屏幕时的视觉不适应感。
     */
    public static float factor(Context context) {
        return MIN_SCALE;
    }

    /**
     * 返回覆写 density 的缩放 Context（供布局填充与 dp/sp 资源解析）。
     * 系数 ≤ 1 时原样返回 base，避免不必要的 Resources 创建。
     */
    public static Context wrap(Context base) {
        if (base == null) return null;
        float f = factor(base);
        if (f <= 1.0f + 1e-4f) return base;
        // 基准 density 同样取实时显示度量：传入上下文已是缩放上下文时不叠乘
        DisplayMetrics real = screenMetrics(base);
        Configuration cfg = new Configuration(base.getResources().getConfiguration());
        cfg.densityDpi = Math.max(1, Math.round(real.densityDpi * f));
        return base.createConfigurationContext(cfg);
    }

    /**
     * 供代码内 dp→px 换算统一乘以缩放系数（density 未走 {@link #wrap} 的场景，
     * 如以 Activity 资源测量但需随屏幕放大的按钮/弹窗宽高）。
     */
    public static int dpToPx(Context context, float dp) {
        if (context == null) return Math.round(dp);
        float density = screenMetrics(context).density;
        return Math.round(dp * density * factor(context));
    }
}
