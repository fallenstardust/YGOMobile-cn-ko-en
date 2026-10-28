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
 * 现实现：两个 drawable 实例（窗口背景与区域背景）一律以<b>整个窗口</b>为基准解算同一映射
 * ——缩放取 cover（同时覆盖窗口宽高，永不留白、永不重复平铺），水平右对齐到窗口右缘（只显示
 * 图片最右侧的部分，左侧超出屏幕的部分被裁掉），垂直按窗口居中，再减去宿主视图在窗口内的偏移
 * 换算到本视图的画布坐标。于是区域显示的正是它在窗口中所处位置的那一片图，与窗口背景逐像素
 * 连续；X 方向取 CLAMP，即使极端比例下也不会再出现左右边缘拼接缝。
 * <p>
 * GameFieldView(setZOrderOnTop) 的透明像素透出窗口缓冲，故本背景画在 GL 曲面之下仍然可见。
 */
public class RightAlignedTiledDrawable extends Drawable {

    private final Bitmap bitmap;
    private final BitmapShader shader;
    private final Paint paint;
    private final Matrix matrix = new Matrix();
    private final int[] hostLoc = new int[2];

    public RightAlignedTiledDrawable(@NonNull Bitmap bitmap) {
        this.bitmap = bitmap;
        // X 也取 CLAMP：映射按窗口 cover 解算，可见区恒落在图片内部，不需要也不允许平铺补边
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

        // cover：图片缩放到同时铺满窗口宽高（不留白、不重复）
        float s = Math.max((float) winW / bitmap.getWidth(), (float) winH / bitmap.getHeight());
        // 图片在窗口坐标中的落点：右缘贴窗口右缘、垂直居中
        float winLeft = winW - s * bitmap.getWidth();
        float winTop = (winH - s * bitmap.getHeight()) / 2f;
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
