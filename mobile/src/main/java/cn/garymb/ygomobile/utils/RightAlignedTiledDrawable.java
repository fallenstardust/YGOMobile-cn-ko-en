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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * 竖屏决斗场区域背景（用户规格：背景只显示在下方 2/3 的 layout_game_right 上，
 * 图片平铺显示右侧的部分，左侧超出屏幕的忽略不计）。
 * <p>
 * 实现：BitmapShader REPEAT 横向平铺 + 局部矩阵——按 bounds 高度等比缩放图片
 * （s = bounds高 / 图高），并平移使图片右缘对齐 bounds 右缘；bounds 左缘若不足
 * 一张图宽则向左以 REPEAT 平铺补齐（即屏幕上看到的是拼贴后的右侧部分，
 * 超出屏幕的左侧平铺副本自然不可见）。作为 layout_game_right 的 View 背景时，
 * GameFieldView(setZOrderOnTop) 的透明像素透出窗口缓冲，本背景即在 GL 曲面之下可见。
 */
public class RightAlignedTiledDrawable extends Drawable {

    private final Bitmap bitmap;
    private final BitmapShader shader;
    private final Paint paint;
    private final Matrix matrix = new Matrix();

    public RightAlignedTiledDrawable(@NonNull Bitmap bitmap) {
        this.bitmap = bitmap;
        this.shader = new BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.CLAMP);
        this.paint = new Paint(Paint.FILTER_BITMAP_FLAG);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect b = getBounds();
        if (b.isEmpty() || bitmap.getWidth() <= 0 || bitmap.getHeight() <= 0) return;
        float s = (float) b.height() / bitmap.getHeight();
        // p' = s·p + t：右缘采样落在图片右缘（x'=bmpW），顶缘落在 y'=0
        matrix.reset();
        matrix.postScale(s, s);
        matrix.postTranslate(bitmap.getWidth() - s * b.right, -s * b.top);
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
