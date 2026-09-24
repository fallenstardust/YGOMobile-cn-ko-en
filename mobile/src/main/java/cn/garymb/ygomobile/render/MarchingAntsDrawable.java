package cn.garymb.ygomobile.render;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

/**
 * 蚂蚁线高亮描边：在按钮四周绘制一圈持续「行进」的绿色虚线圆角矩形，
 * 作为「完成选择 / 取消」按钮的视觉高亮（对齐 gframe drawing.cpp DrawSelectionLine
 * 对可完成选择按钮的行进式选中框）。以深绿描边相对亮绿描边偏移约 1dp 形成双色蚂蚁线观感。
 * <p>
 * 动画自驱动：{@link #start()} 起、{@link #stop()} 停；通过 Drawable.Callback（View 的
 * foreground 会提供）按帧 {@code invalidateSelf()} 推进相位，随按钮显隐同步起停。
 */
public class MarchingAntsDrawable extends Drawable {

    private final Paint mFore = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mBack = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float[] mIntervals;
    private final float density;

    private float mPhase;       // 行进相位（px），递减使虚线顺时针流动
    private boolean mRunning;

    // 每帧推进的速度与间隔：约 30dp/s
    private static final float STEP_DP_PER_FRAME = 1.0f;
    private static final long FRAME_MS = 32L;

    private final Runnable mTick = new Runnable() {
        @Override
        public void run() {
            if (!mRunning) return;
            mPhase -= STEP_DP_PER_FRAME * density;
            applyPhase();
            invalidateSelf();
            scheduleSelf(mTick, FRAME_MS);
        }
    };

    public MarchingAntsDrawable(float density) {
        this.density = density <= 0f ? 1f : density;
        float stroke = 2f * this.density;
        float on = 4f * this.density;
        float off = 3f * this.density;
        mIntervals = new float[]{on, off};

        mFore.setStyle(Paint.Style.STROKE);
        mFore.setStrokeWidth(stroke);
        mFore.setColor(0xFF3CFF5A);       // 亮绿

        mBack.setStyle(Paint.Style.STROKE);
        mBack.setStrokeWidth(stroke);
        mBack.setColor(0xE6001A00);       // 深绿描边（垫底形成蚂蚁线双色）

        applyPhase();
    }

    /** PathEffect 不可改相位，每帧重建一个带当前 phase 的虚线效果并回填到两支画笔 */
    private void applyPhase() {
        DashPathEffect fx = new DashPathEffect(mIntervals, mPhase);
        mFore.setPathEffect(fx);
        mBack.setPathEffect(fx);
    }

    /** 开始行进动画（幂等）。 */
    public void start() {
        if (mRunning) return;
        mRunning = true;
        scheduleSelf(mTick, FRAME_MS);
        invalidateSelf();
    }

    /** 停止行进动画。 */
    public void stop() {
        if (!mRunning) return;
        mRunning = false;
        unscheduleSelf(mTick);
    }

    @Override
    public void draw(Canvas canvas) {
        Rect b = getBounds();
        if (b.isEmpty()) return;
        float inset = mFore.getStrokeWidth() / 2f + 0.5f * density;
        float l = b.left + inset, t = b.top + inset, r = b.right - inset, bt = b.bottom - inset;
        float radius = 3f * density;
        float shift = 1f * density;
        // 深绿先绘（偏移 1dp），亮绿覆盖：虚线段错位即成黑/绿相间的行进蚂蚁线
        canvas.drawRoundRect(l + shift, t + shift, r + shift, bt + shift, radius, radius, mBack);
        canvas.drawRoundRect(l, t, r, bt, radius, radius, mFore);
    }

    @Override
    public void setAlpha(int alpha) {
        mFore.setAlpha(alpha);
        mBack.setAlpha(alpha);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {
        mFore.setColorFilter(colorFilter);
        mBack.setColorFilter(colorFilter);
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
