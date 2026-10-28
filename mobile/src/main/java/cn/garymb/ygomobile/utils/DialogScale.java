package cn.garymb.ygomobile.utils;

import android.content.Context;
import android.content.res.Configuration;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.WindowManager;

import cn.garymb.ygomobile.Constants;

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
 *
 * <p>平板/折叠屏大分辨率近方屏（4:3 之类，见 {@link #isLargeFormatDisplay}）另走一套显示分级：
 * 此类屏竖屏下“满屏宽正方形”会占到屏高 75%（常规手机仅 45%~56%）而显得过大，故竖屏正方形
 * 弹窗的边长按 {@link #portraitSquareCapPx} 封顶到弹窗设计宽度 420dp，四周留边居中；横屏规则与
 * 内容字号密度均不变，使弹窗在平板上仍与常规手机同 dp 量级。该分级只影响
 * {@code setupDraggablePopup(..., swapForPortrait = true)} 的建主/局域网/单机/录像/玩家等待五个弹窗。
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

    /**
     * 平板/折叠屏大分辨率近方屏的短边阈值（px）：常规手机竖屏短边即 720p/1080p 的 720、1080，
     * 严格大于 1080 才算大分辨率（用户规格：超过 1080p），1080×1920/1080×2400 等手机恒不命中
     */
    private static final int LARGE_SHORT_SIDE_PX = 1080;
    /**
     * 平板/折叠屏大分辨率近方屏的长短边比上限：常规手机最“矮”也是 16:9≈1.78，取 1.7 作为
     * 安全下界，使 4:3（1.33）、16:10（1.6）、折叠屏内屏（≈1.0~1.1）命中而任何手机形态不命中
     */
    private static final float LARGE_ASPECT_MAX = 1.7f;

    private DialogScale() {
    }

    /**
     * 平板/折叠屏大分辨率近方屏（用户规格：超过 1080p 且屏幕比例接近 4:3）判定：
     * 实时显示短边 &gt; {@value #LARGE_SHORT_SIDE_PX}px <b>且</b> 长边/短边 &lt; {@value #LARGE_ASPECT_MAX}。
     *
     * <p>两个条件都是排除性的：只按分辨率会把 20:9 的大屏手机误纳；只按比例会把
     * 800×1280 这类小平板误纳。度量一律取 {@link #screenMetrics}（实时物理显示，不受
     * 缩放上下文派生值与 Resources 覆写影响），因此分屏/多窗口下仍按物理屏分级。
     *
     * <p>命中时只改变竖屏正方形弹窗的边长上限（见 {@link #portraitSquareCapPx}）；常规手机
     * 该式退化为不封顶，行为零变化。横屏尺寸规则两档相同，内容 dp/sp 亦不因分级而覆写密度。
     */
    public static boolean isLargeFormatDisplay(Context context) {
        DisplayMetrics dm = screenMetrics(context);
        int shortSide = Math.min(dm.widthPixels, dm.heightPixels);
        int longSide = Math.max(dm.widthPixels, dm.heightPixels);
        if (shortSide <= 0 || longSide <= 0) return false;
        return shortSide > LARGE_SHORT_SIDE_PX && (float) longSide / shortSide < LARGE_ASPECT_MAX;
    }

    /**
     * 竖屏正方形重排（建主/局域网/单机/录像/玩家等待）的边长上限（px）：仅
     * {@link #isLargeFormatDisplay} 命中时返回正值，其余（常规手机）返回 0 表示不封顶，
     * 保持“边长=屏宽铺满”的成熟 UI 不变。
     *
     * <p>上限取弹窗设计宽度 {@link Constants#DIALOG_POPUP_WIDTH_DP}（420dp）：常规手机竖屏
     * 边长即屏宽≈ 360dp，平板/折叠屏按同一 dp 上限封顶后，弹窗与内部控件的 dp 尺寸与手机
     * 同量级（内容不随屏放大，用户规格），不再出现 4:3 竖屏下“满屏宽正方形占屏高 75%”的
     * 过大形态；分辨率越大则四周留边越多，弹窗始终屏幕居中。
     */
    public static int portraitSquareCapPx(Context context) {
        if (!isLargeFormatDisplay(context)) return 0;
        return Math.round(screenMetrics(context).density * Constants.DIALOG_POPUP_WIDTH_DP);
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
