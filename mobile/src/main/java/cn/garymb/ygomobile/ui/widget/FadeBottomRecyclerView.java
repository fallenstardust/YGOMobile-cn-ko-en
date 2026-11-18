package cn.garymb.ygomobile.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.util.TypedValue;

import com.tubb.smrv.SwipeMenuRecyclerView;

/**
 * 搜索结果列表：底部一段做成半透明渐隐（内容 alpha 由 1 渐到 0），
 * 让列表下边缘自然融进下方时间轴的透明背景，而不是被硬切成一条直线。
 *
 * <p>做法是在 {@link #draw(Canvas)} 里先整层离屏（saveLayer），画完内容再用
 * {@link PorterDuff.Mode#DST_IN} 的竖向渐变把底部 alpha 压下去；渐隐终点落在内容区的
 * 下边缘（扣掉 paddingBottom），所以最后一行滚到底时会自然淡出，不会出现硬边。</p>
 */
public class FadeBottomRecyclerView extends SwipeMenuRecyclerView {

    /** 渐隐区默认高度（dp）：约一行卡图高度的四分之一，过渡看起来最柔和 */
    private static final float DEFAULT_FADE_HEIGHT_DP = 26f;

    private final Paint mFadeMaskPaint = new Paint();
    /** 渐隐区高度（px），<= 0 关闭渐隐 */
    private int mFadeHeight;
    /** 渐变按当前高度与内边距生成，变了就重建（只需重建一次） */
    private Shader mFadeMaskShader;
    private int mShaderViewHeight = -1;
    private int mShaderPaddingBottom = -1;

    public FadeBottomRecyclerView(Context context) {
        this(context, null);
    }

    public FadeBottomRecyclerView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FadeBottomRecyclerView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mFadeHeight = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,
                DEFAULT_FADE_HEIGHT_DP, getResources().getDisplayMetrics()));
        mFadeMaskPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
    }

    /** 设定渐隐区高度（px）；传 0 关闭渐隐。不设时用默认 26dp */
    public void setFadeHeight(int heightPx) {
        int height = Math.max(0, heightPx);
        if (mFadeHeight == height) {
            return;
        }
        mFadeHeight = height;
        mShaderViewHeight = -1;
        invalidate();
    }

    @Override
    public void draw(Canvas canvas) {
        final int contentBottom = getHeight() - getPaddingBottom();
        final int fadeTop = contentBottom - mFadeHeight;
        // 没开渐隐、列表还没量出高度、或列表比渐隐区还矮：保持原样画
        if (mFadeHeight <= 0 || fadeTop < getPaddingTop()) {
            super.draw(canvas);
            return;
        }
        ensureFadeShader(fadeTop, contentBottom);
        // 整层离屏后画内容，再用 DST_IN 遮罩把底部 alpha 渐到 0，只会影响列表自身内容，
        // 不会擦到列表下面的页面背景
        int saveCount = canvas.saveLayer(0, 0, getWidth(), getHeight(), null);
        super.draw(canvas);
        canvas.drawRect(0, fadeTop, getWidth(), contentBottom, mFadeMaskPaint);
        canvas.restoreToCount(saveCount);
    }

    /** 渐变遮罩：顶端纯白（alpha=1，保留内容），底端全透明（alpha=0，内容淡出） */
    private void ensureFadeShader(int fadeTop, int contentBottom) {
        if (mFadeMaskShader != null && mShaderViewHeight == getHeight()
                && mShaderPaddingBottom == getPaddingBottom()) {
            return;
        }
        mFadeMaskShader = new LinearGradient(0, fadeTop, 0, contentBottom,
                0xFFFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP);
        mFadeMaskPaint.setShader(mFadeMaskShader);
        mShaderViewHeight = getHeight();
        mShaderPaddingBottom = getPaddingBottom();
    }
}
