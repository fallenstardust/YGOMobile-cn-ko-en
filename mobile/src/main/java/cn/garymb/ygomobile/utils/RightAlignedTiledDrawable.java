package cn.garymb.ygomobile.utils;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 竖屏决斗场背景（用户规格：竖屏显示的背景必须与 <code>layout_game_right</code> 实机宽度
 * 所处位置的那一片背景一致，且该区域内不得出现图片左边缘与右边缘的拼接缝）。
 * <p>
 * 旧实现按「各自 bounds 的高度」等比缩放并右对齐、X 方向 REPEAT 补边：窗口背景（整屏）与
 * 区域背景（下方 2/3）因此是两套互不相同的缩放/平移映射，同一张图片在两处显示的比例与取样
 * 位置都不同，交界处必然出现错位拼缝；区域宽高比大于图片宽高比时缩放后宽度还不足区域宽度，
 * REPEAT 直接把图片右缘与左缘拼在一起，形成贯穿屏幕的可见竖缝。
 * <p>
 * 现实现：两个 drawable 实例（窗口背景与区域背景）一律以<b>基准区</b>（见 {@code regionAnchor}，
 * 未传则为整个窗口）为基准解算同一映射——缩放取 cover（同时覆盖基准区宽高，永不留白、永不重复
 * 平铺），水平右对齐到基准区右缘（只显示图片最右侧的部分，左侧超出屏幕的部分被裁掉），垂直按
 * 基准区居中，再减去宿主视图在窗口内的偏移换算到本视图的画布坐标。于是区域显示的正是它在窗口中
 * 所处位置的那一片图，与窗口背景逐像素连续；X 方向取 CLAMP，即使极端比例下也不会再出现左右
 * 边缘拼接缝。
 * <p>
 * 基准区（用户规格）：竖屏决斗背景不得延伸到卡片详情区域，图片高度只到 {@code layout_game_right}
 * 顶部为止——故传入 {@code regionAnchor}（竖屏顶部面板）时，基准区 = 该面板底缘以下到窗口底，
 * cover 按基准区高而非整屏高解算（不再把图放大到铺满整块屏），且基准区之上那一段一律用
 * {@code outsideColor} 重涂盖住。面板未显示（大厅聊天/主菜单，此时 layout_game_right 升为整屏）
 * 或未传锚视图时基准区即整个窗口，与旧实现一致。
 * <p>
 * GameFieldView(setZOrderOnTop) 的透明像素透出窗口缓冲，故本背景画在 GL 曲面之下仍然可见。
 */
public class RightAlignedTiledDrawable extends Drawable {

    private final Bitmap bitmap;
    private final BitmapShader shader;
    private final Paint paint;
    /** 基准区之上那一段（卡片详情区）的覆盖填充画笔 */
    private final Paint fillPaint = new Paint();
    private final Matrix matrix = new Matrix();
    private final int[] hostLoc = new int[2];
    private final int[] anchorLoc = new int[2];
    /** 基准区锚视图（竖屏＝顶部面板 layout_game_top_panel）：其底缘即背景图的上边界，不存在/未显示则为 0 */
    private final View regionAnchor;
    /** 基准区之上的填充色（0 = 不填充，透出窗口原色） */
    private final int outsideColor;

    public RightAlignedTiledDrawable(@NonNull Bitmap bitmap) {
        this(bitmap, null, 0);
    }

    /**
     * @param regionAnchor 基准区锚视图（顶部面板），其底缘为背景图上边界；传 null 则以整窗口为基准
     * @param outsideColor 上边界以上那一段的填充色，0 表示不填充
     */
    public RightAlignedTiledDrawable(@NonNull Bitmap bitmap, @Nullable View regionAnchor,
                                     int outsideColor) {
        this.bitmap = bitmap;
        this.regionAnchor = regionAnchor;
        this.outsideColor = outsideColor;
        // X 也取 CLAMP：映射按基准区 cover 解算，可见区恒落在图片内部，不需要也不允许平铺补边
        this.shader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        this.paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect b = getBounds();
        if (b.isEmpty() || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) return;

        // 宿主视图与窗口：背景既用作窗口背景（宿主=DecorView，窗口偏移 0）也用作区域背景
        // （宿主=layout_game_right），两者必须落在同一个窗口基准映射上才无缝
        Callback cb = getCallback();
        View host = cb instanceof View ? (View) cb : null;
        View root = host != null ? host.getRootView() : null;
        int winW = root != null && root.getWidth() > 0 ? root.getWidth() : b.width();
        int winH = root != null && root.getHeight() > 0 ? root.getHeight() : b.height();
        int offX = 0;
        int offY = 0;
        if (host != null && root != null) {
            host.getLocationInWindow(hostLoc);
            offX = hostLoc[0];
            offY = hostLoc[1];
        }

        // 基准区上缘（窗口坐标）：竖屏顶部面板正在显示时取其底缘——背景图高度止于此处，
        // 卡片详情那一段不再透出背景图；面板未显示（大厅/菜单，此时决斗场区升为整屏）
        // 或未传锚视图则为 0，即退回整窗口基准
        int regionTop = 0;
        if (regionAnchor != null && regionAnchor.isShown() && regionAnchor.getHeight() > 0) {
            regionAnchor.getLocationInWindow(anchorLoc);
            int anchorBottom = anchorLoc[1] + regionAnchor.getHeight();
            // 底缘已到窗口底属异常比例：基准区高为 0 会让 cover 解算退化，退回整窗口
            if (anchorBottom > 0 && anchorBottom < winH) regionTop = anchorBottom;
        }
        int regionH = winH - regionTop;

        // cover：图片缩放到同时铺满基准区宽高（不留白、不重复）
        float s = Math.max((float) winW / bitmap.getWidth(), (float) regionH / bitmap.getHeight());
        // 图片在窗口坐标中的落点：右缘贴基准区右缘、垂直按基准区居中
        float winLeft = winW - s * bitmap.getWidth();
        float winTop = regionTop + (regionH - s * bitmap.getHeight()) / 2f;
        // 换算到本视图画布坐标：背景画在未平移的视图画布上（bounds 只界定绘制范围、不平移画布），
        // 故画布点 c 的窗口坐标 = c + 宿主视图在窗口中的位置
        float left = winLeft - offX;
        float top = winTop - offY;

        // 着色器取样点 = M⁻¹·c，故 M 即「图片→画布」的正向变换：M(p) = s·p + (left, top)
        matrix.reset();
        matrix.postScale(s, s);
        matrix.postTranslate(left, top);
        shader.setLocalMatrix(matrix);
        paint.setShader(shader);
        canvas.drawRect(b, paint);

        // 盖掉基准区以上：窗口背景的宿主是整个 DecorView，上面那次 drawRect 会把图一并铺到
        // 卡片详情区，故按同一基准把该段重涂纯色；区域背景的 bounds 本就等于基准区
        // （offY == regionTop），本段为空、自然不触发
        int fillBottom = Math.min(b.bottom, Math.max(b.top, regionTop - offY));
        if (outsideColor != 0 && fillBottom > b.top) {
            fillPaint.setColor(outsideColor);
            canvas.drawRect(b.left, b.top, b.right, fillBottom, fillPaint);
        }
    }

    @Override
    public void setAlpha(int alpha) {
        paint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter colorFilter) {
        paint.setColorFilter(colorFilter);
    }

    @Override
    public int getOpacity() {
        return PixelFormat.OPAQUE;
    }
}
