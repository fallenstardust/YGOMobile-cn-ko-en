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
import android.view.View;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.tubb.smrv.SwipeMenuRecyclerView;

/**
 * 搜索结果列表：底部一段做成半透明渐隐（内容 alpha 由 1 渐到 0），
 * 让列表下边缘自然融进下方时间轴的透明背景，而不是被硬切成一条直线。
 *
 * <p>做法是在 {@link #draw(Canvas)} 里先整层离屏（saveLayer），画完内容再用
 * {@link PorterDuff.Mode#DST_IN} 的竖向渐变把底部 alpha 压下去；渐隐终点落在内容区的
 * 下边缘（扣掉 paddingBottom）。</p>
 *
 * <p>渐隐高度会随“最后一项被裁掉多少”自动缩放：滑到底时最后一行整行落在内容区里，
 * 渐隐长度归零，整行都能看清；向上滑，被裁掉的最后一行越多，渐隐越长（上限 {@code 40dp}）。
 * 于是既不会把最后一行吃掉，也不会有生硬的遮挡切换。</p>
 */
public class FadeBottomRecyclerView extends SwipeMenuRecyclerView {

    /** 渐隐区默认高度（dp）：约半行卡图高度，过渡足够柔和又不至于吃掉太多内容 */
    private static final float DEFAULT_FADE_HEIGHT_DP = 40f;

    private final Paint mFadeMaskPaint = new Paint();
    /** 渐隐区高度上限（px），<= 0 关闭渐隐 */
    private int mFadeHeight;
    /** 渐变按当次实际渐隐长度生成，变了就重建 */
    private Shader mFadeMaskShader;
    private int mShaderFadeTop = -1;
    private int mShaderFadeBottom = -1;

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

    /** 设定渐隐区高度上限（px）；传 0 关闭渐隐。不设时用默认 40dp */
    public void setFadeHeight(int heightPx) {
        int height = Math.max(0, heightPx);
        if (mFadeHeight == height) {
            return;
        }
        mFadeHeight = height;
        mShaderFadeTop = -1;
        invalidate();
    }

    @Override
    public void draw(Canvas canvas) {
        final int fadeHeight = resolveFadeHeight();
        final int contentBottom = getHeight() - getPaddingBottom();
        final int fadeTop = contentBottom - fadeHeight;
        // 本次不需要渐隐（已滑到底 / 内容不足以滚动 / 列表还没量出高度）：原样画
        if (fadeHeight <= 0 || fadeTop < getPaddingTop()) {
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

    /**
     * 本次绘制实际要用的渐隐高度：最后一行被裁掉多少像素就渐隐多少（不超过 mFadeHeight）。
     * 滑到底时最后一行整行落在内容区里，被裁量为 0，于是不渐隐，整行都能看清；
     * 还有内容在裁切线以下时按裁掉的高度逐步加长，过渡是连续的，不会突然切换
     */
    private int resolveFadeHeight() {
        if (mFadeHeight <= 0) {
            return 0;
        }
        RecyclerView.LayoutManager lm = getLayoutManager();
        RecyclerView.Adapter<?> adapter = getAdapter();
        if (!(lm instanceof LinearLayoutManager) || adapter == null) {
            return mFadeHeight;
        }
        LinearLayoutManager linear = (LinearLayoutManager) lm;
        int lastVisible = linear.findLastVisibleItemPosition();
        if (lastVisible == RecyclerView.NO_POSITION) {
            return 0;
        }
        // 后面还有内容没露出来：按满额渐隐
        if (lastVisible < adapter.getItemCount() - 1) {
            return mFadeHeight;
        }
        View lastChild = linear.findViewByPosition(lastVisible);
        if (lastChild == null) {
            return mFadeHeight;
        }
        int contentBottom = getHeight() - getPaddingBottom();
        int cutOff = lastChild.getBottom() - contentBottom;
        return Math.max(0, Math.min(mFadeHeight, cutOff));
    }

    /** 渐变遮罩：顶端纯白（alpha=1，保留内容），底端全透明（alpha=0，内容淡出） */
    private void ensureFadeShader(int fadeTop, int contentBottom) {
        if (mFadeMaskShader != null && mShaderFadeTop == fadeTop
                && mShaderFadeBottom == contentBottom) {
            return;
        }
        mFadeMaskShader = new LinearGradient(0, fadeTop, 0, contentBottom,
                0xFFFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP);
        mFadeMaskPaint.setShader(mFadeMaskShader);
        mShaderFadeTop = fadeTop;
        mShaderFadeBottom = contentBottom;
    }
}
