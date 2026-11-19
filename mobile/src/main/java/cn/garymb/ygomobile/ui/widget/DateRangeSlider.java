package cn.garymb.ygomobile.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import java.util.Arrays;
import java.util.Locale;

import cn.garymb.ygomobile.lite.R;

/**
 * 搜索结果的横向发售日期时间轴：一条时间线 + 左右两个滑块，两滑块之间即保留的日期区间（闭区间）。
 *
 * <p>时间轴摆在结果列表下方：控件自己不画背景（全透明），列表底部由
 * {@code FadeBottomRecyclerView} 做半透明渐隐，二者在交界处自然衔接。</p>
 *
 * <p>两个滑块上方的日期气泡常显（左端 = 开始日、右端 = 结束日），两气泡互相避让、不超出控件边界；
 * 气泡里的日期可点击，点开日历后只能选卡包里真实存在的发售日，选完该端滑块就移到那一天。
 * 按在两个滑块之外则最近的一端直接跳到按下处（时间轴整体可点）。
 * 未设置日期跨度时本控件不参与手势，由页面决定是否隐藏。</p>
 *
 * <p>时间线两端标出卡包数据覆盖的最早/最晚年份（左端最早、右端最晚），
 * 年份文字垫一层 {@code button_radius_black_transparents} 圆角黑板底；
 * 点左侧年份牌会把开始滑块复位到最早期、点右侧年份牌会把结束滑块复位到最晚期，
 * 滑块则是“倒锥五边形”（上宽下尖），尖端指向它所代表的时间点，便于手指按住拖动。</p>
 *
 * <p>日期一律用 {@code yyyyMMdd} 的整数表示：既能直接比较大小，也不受时区影响；
 * 滑块位置按“自 1970-01-01 起的天数”线性排布，与公历月份长短无关；
 * 卡包发售日期作为滑动节点（{@link #setReleaseNodes(int[])}），滑块每次移动都吸附到
 * 最近的一个卡包日期上，不再逐日增减。</p>
 */
public class DateRangeSlider extends View {
    /** 拖动/点按后区间变化的回调，两端都是 yyyyMMdd */
    public interface OnRangeChangedListener {
        void onRangeChanged(int startDate, int endDate);
    }

    /** 点日期气泡的回调：thumb 为 THUMB_START/THUMB_END，date 为该端当前日期 yyyyMMdd */
    public interface OnDateLabelClickListener {
        void onDateLabelClick(int thumb, int date);
    }

    /** 年份牌 drawable 的按下/普通状态（用于点击复位时的视觉反馈） */
    private static final int[] YEAR_STATE_PRESSED = new int[]{android.R.attr.state_pressed};
    private static final int[] YEAR_STATE_DEFAULT = new int[0];

    public static final int THUMB_START = 0;
    public static final int THUMB_END = 1;
    private static final int THUMB_NONE = -1;
    /** 两个日期气泡相距太近时的最小间隙（dp） */
    private static final int LABEL_GAP_DP = 2;

    private final Paint mTrackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mActiveTrackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mTickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mYearPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mThumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mThumbRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mLabelBgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mLabelBorderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mLabelTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();
    /** 倒锥五边形滑块的路径（每帧按当前位置重建） */
    private final Path mThumbPath = new Path();
    /** 两侧年份文字的圆角黑板底（左右各一份，按下态互不影响，也不会在一帧里反复切状态） */
    private final Drawable mMinYearBackground;
    private final Drawable mMaxYearBackground;

    /** 滑块的上半宽与整体高度（尖端落在时间线上） */
    private final int mThumbHalfW;
    private final int mThumbHeight;
    private final int mThumbRingWidth;
    private final int mTrackThickness;
    private final int mTickHalf;
    private final int mLabelPadH;
    private final int mLabelPadV;
    private final int mLabelCorner;
    private final int mLabelGap;
    /** 年份文字两侧/上下的留白（圆角黑板底比文字大一圈） */
    private final int mYearPillPadH;
    private final int mYearPillPadV;
    /** 两边年份牌的点击容差：牌子本身不大，手指不必精确命中（横向收紧，不抢滑块抓取区） */
    private final int mYearHitSlopH;
    private final int mYearHitSlopV;
    /** 日期气泡的固定高度：无论气泡是否在显都占位，否则气泡一显一隐滑块会跟着跳 */
    private final int mLabelHeight;
    private final int mDesiredHeight;
    /** 时间线两端年份文字与时间线之间的横向间距 */
    private final int mYearGap;
    /** 抓住某一端滑块的横向容差：比滑块本身宽一些，手指不必精确压在图形上 */
    private final int mGrabSlop;
    private final int mTouchSlop;

    private OnRangeChangedListener mListener;
    private OnDateLabelClickListener mLabelClickListener;

    /** 日期跨度（yyyyMMdd）与它对应的天数刻度；滑块位置按天数在两者之间线性插值 */
    private int mMinDate;
    private int mMaxDate;
    private long mMinDay;
    private long mMaxDay;
    /** 两端滑块当前所在的天数，恒落在 [mMinDay, mMaxDay] 内且 start <= end */
    private long mStartDay;
    private long mEndDay;
    private boolean mHasRange;
    /**
     * 滑动节点：卡包发售日期（yyyyMMdd 升序）与对应的天数刻度。
     * 为空时滑块退回逐日滑动（没拿到节点表的老路径）
     */
    private int[] mNodeDates;
    private long[] mNodeDays;

    /** 时间线可用的 x 区间（两端各让出一个滑块与年份文字）与线的中心 y */
    private float mTrackLeft;
    private float mTrackRight;
    private float mTrackCenterY;
    private float mLabelTop;
    /** 时间线左右两端标注的年份文字与各自的宽度 */
    private String mMinYearText = "";
    private String mMaxYearText = "";
    private float mMinYearWidth;
    private float mMaxYearWidth;
    /** 两端年份牌的绘制区（同时也是点击复位的热区，绘制与命中共用同一份几何） */
    private final RectF mMinYearRect = new RectF();
    private final RectF mMaxYearRect = new RectF();
    private final RectF mHitRect = new RectF();
    /** 当前被按住的年份牌对应哪一端；THUMB_NONE = 本次手势不是点年份牌 */
    private int mYearPressedThumb = THUMB_NONE;

    /** 两个日期气泡的绘制区（同时也是点开日历的热区，绘制与命中共用同一份几何） */
    private final RectF mStartLabelRect = new RectF();
    private final RectF mEndLabelRect = new RectF();
    /** 本次手势按在日期气泡上：抬手前不再拖滑块 */
    private boolean mLabelPressed;
    private int mActiveThumb = THUMB_NONE;
    /** 两端重合时先不认领滑块，等第一次移动按方向决定（否则往右拉永远动不了终点） */
    private boolean mThumbPending;
    private float mDownX;

    public DateRangeSlider(Context context) {
        this(context, null);
    }

    public DateRangeSlider(Context context, @Nullable AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public DateRangeSlider(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        mThumbHalfW = dp(8);
        mThumbHeight = dp(12);
        mThumbRingWidth = Math.max(1, dp(1));
        mTrackThickness = dp(3);
        mTickHalf = dp(4);
        mLabelPadH = dp(7);
        mLabelPadV = dp(3);
        mLabelCorner = dp(4);
        mLabelGap = dp(2);
        mYearGap = dp(7);
        mYearPillPadH = dp(4);
        mYearPillPadV = dp(2);
        mYearHitSlopH = dp(4);
        mYearHitSlopV = dp(10);
        mGrabSlop = dp(22);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        mTrackPaint.setColor(0x55FFFFFF);
        mActiveTrackPaint.setColor(getColor(R.color.holo_blue_bright));
        mTickPaint.setColor(0x40FFFFFF);
        mTickPaint.setStrokeWidth(Math.max(1f, dp(1)));
        mYearPaint.setColor(0x99FFFFFF);
        mYearPaint.setTextSize(sp(10));
        mYearPaint.setTextAlign(Paint.Align.LEFT);
        mThumbPaint.setColor(getColor(R.color.gold));
        mThumbRingPaint.setColor(0xCC000000);
        mThumbRingPaint.setStyle(Paint.Style.STROKE);
        mThumbRingPaint.setStrokeWidth(mThumbRingWidth);
        mLabelBgPaint.setColor(getColor(R.color.colorNavy));
        mLabelBorderPaint.setColor(getColor(R.color.gold));
        mLabelBorderPaint.setStyle(Paint.Style.STROKE);
        mLabelBorderPaint.setStrokeWidth(Math.max(1f, dp(1)));
        mLabelTextPaint.setColor(getColor(R.color.gold));
        mLabelTextPaint.setTextSize(sp(10));
        mLabelTextPaint.setTextAlign(Paint.Align.LEFT);
        mMinYearBackground = ContextCompat.getDrawable(context, R.drawable.button_radius_black_transparents);
        mMaxYearBackground = ContextCompat.getDrawable(context, R.drawable.button_radius_black_transparents);

        Paint.FontMetrics fm = mLabelTextPaint.getFontMetrics();
        mLabelHeight = Math.round(fm.descent - fm.ascent) + mLabelPadV * 2;
        // 自上而下：日期气泡（常驻占位）→ 间隙 → 五边形滑块 → 时间线
        mDesiredHeight = mLabelHeight + mLabelGap + mThumbHeight + dp(9);
    }

    /**
     * 设定滑动节点：卡包发售日期（yyyyMMdd，升序去重），首尾节点就是时间轴跨度。
     * 之后滑块每次移动都吸附到离手指最近的那个节点上，而不是逐日增减。
     * 节点与跨度都没变时不动两端滑块，避免拖动中途被页面刷新打断。
     */
    public void setReleaseNodes(int[] nodeDates) {
        if (nodeDates == null || nodeDates.length == 0) {
            return;
        }
        int minDate = nodeDates[0];
        int maxDate = nodeDates[nodeDates.length - 1];
        if (!isValidDate(minDate) || !isValidDate(maxDate) || minDate > maxDate) {
            return;
        }
        if (mHasRange && minDate == mMinDate && maxDate == mMaxDate && isSameNodes(nodeDates)) {
            return;
        }
        // 两端原本就在首尾节点 = 用户还没收紧过，节点变了就跟着回到新的全范围
        boolean wasFullRange = !mHasRange || (mStartDay <= mMinDay && mEndDay >= mMaxDay);
        mNodeDates = nodeDates.clone();
        mNodeDays = new long[mNodeDates.length];
        for (int i = 0; i < mNodeDates.length; i++) {
            int date = mNodeDates[i];
            mNodeDays[i] = toEpochDay(date / 10000, date / 100 % 100, date % 100);
        }
        mMinDate = minDate;
        mMaxDate = maxDate;
        mMinDay = mNodeDays[0];
        mMaxDay = mNodeDays[mNodeDays.length - 1];
        if (wasFullRange) {
            mStartDay = mMinDay;
            mEndDay = mMaxDay;
        } else {
            // 节点表换了：原来夹到新跨度两端的滑块重新落回节点上
            mStartDay = snapToNode(Math.max(mMinDay, Math.min(mStartDay, mMaxDay)));
            mEndDay = snapToNode(Math.max(mStartDay, Math.min(mEndDay, mMaxDay)));
        }
        mHasRange = true;
        // 两端年份文字：左端最早年份、右端最晚年份（时间线夹在两者中间）
        mMinYearText = String.valueOf(minDate / 10000);
        mMaxYearText = String.valueOf(maxDate / 10000);
        // 宽度含圆角黑板底的左右留白，时间线要让开整个牌子
        mMinYearWidth = mYearPaint.measureText(mMinYearText) + mYearPillPadH * 2;
        mMaxYearWidth = mYearPaint.measureText(mMaxYearText) + mYearPillPadH * 2;
        updateTrackGeometry(getWidth());
        invalidate();
    }

    private boolean isSameNodes(int[] nodeDates) {
        if (mNodeDates == null || mNodeDates.length != nodeDates.length) {
            return false;
        }
        for (int i = 0; i < nodeDates.length; i++) {
            if (nodeDates[i] != mNodeDates[i]) {
                return false;
            }
        }
        return true;
    }

    public void setOnRangeChangedListener(OnRangeChangedListener listener) {
        mListener = listener;
    }

    /** 点日期气泡的回调：页面接到后弹日历，选完再调 {@link #setDateRange(int, int)} */
    public void setOnDateLabelClickListener(OnDateLabelClickListener listener) {
        mLabelClickListener = listener;
    }

    public boolean hasRange() {
        return mHasRange;
    }

    /** 时间轴的最早日期（yyyyMMdd），未设置跨度时为 0 */
    public int getMinDate() {
        return mHasRange ? mMinDate : 0;
    }

    /** 时间轴的最晚日期（yyyyMMdd），未设置跨度时为 0 */
    public int getMaxDate() {
        return mHasRange ? mMaxDate : 0;
    }

    /** 左滑块的当前值（yyyyMMdd），未设置跨度时为 0 */
    public int getStartDate() {
        return mHasRange ? toDate(mStartDay) : 0;
    }

    /** 右滑块的当前值（yyyyMMdd），未设置跨度时为 0 */
    public int getEndDate() {
        return mHasRange ? toDate(mEndDay) : 0;
    }

    /** 两端是否都落在首尾节点上（页面据此决定还要不要按日期过滤） */
    public boolean isFullRange() {
        return !mHasRange || (mStartDay <= mMinDay && mEndDay >= mMaxDay);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = getDefaultSize(getSuggestedMinimumWidth(), widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int height = heightMode == MeasureSpec.EXACTLY
                ? MeasureSpec.getSize(heightMeasureSpec)
                : (heightMode == MeasureSpec.AT_MOST
                ? Math.min(mDesiredHeight, MeasureSpec.getSize(heightMeasureSpec))
                : mDesiredHeight);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        updateTrackGeometry(w);
    }

    /**
     * 按当前宽度与两端年份文字牌子的宽度排布：年份牌子在两端外侧，时间线夹在它们中间；
     * 上下固定为「气泡占位 → 间隙 → 五边形滑块（尖端落在线上）」的顺序。
     * 两端年份牌同时算出绘制区与点击热区（绘制与命中共用同一份几何）
     */
    private void updateTrackGeometry(int width) {
        mLabelTop = getPaddingTop();
        mTrackCenterY = mLabelTop + mLabelHeight + mLabelGap + mThumbHeight;
        mTrackLeft = getPaddingLeft() + mMinYearWidth + mYearGap + mThumbHalfW;
        mTrackRight = Math.max(mTrackLeft,
                width - getPaddingRight() - mMaxYearWidth - mYearGap - mThumbHalfW);

        Paint.FontMetrics fm = mYearPaint.getFontMetrics();
        float yearHalf = (fm.descent - fm.ascent) / 2f + mYearPillPadV;
        mMinYearRect.set(getPaddingLeft(), mTrackCenterY - yearHalf,
                getPaddingLeft() + mMinYearWidth, mTrackCenterY + yearHalf);
        mMaxYearRect.set(width - getPaddingRight() - mMaxYearWidth, mTrackCenterY - yearHalf,
                width - getPaddingRight(), mTrackCenterY + yearHalf);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (!mHasRange || mTrackRight <= mTrackLeft) {
            return;
        }
        final float cy = mTrackCenterY;
        final float half = mTrackThickness / 2f;

        mRect.set(mTrackLeft, cy - half, mTrackRight, cy + half);
        canvas.drawRoundRect(mRect, half, half, mTrackPaint);

        drawYearTicks(canvas, cy);

        final float startX = xForDay(mStartDay);
        final float endX = xForDay(mEndDay);
        if (endX > startX) {
            mRect.set(startX, cy - half, endX, cy + half);
            canvas.drawRoundRect(mRect, half, half, mActiveTrackPaint);
        }

        drawYearLabels(canvas, cy);
        drawThumb(canvas, startX, cy);
        drawThumb(canvas, endX, cy);
        // 日期气泡常显（用户规格）：左右滑块各一个，点气泡可打开日历选日期
        drawDateLabels(canvas, startX, endX);
    }

    /** 年度刻度：跨年处各画一条短竖线，让人一眼看出时间轴按年份铺开 */
    private void drawYearTicks(Canvas canvas, float cy) {
        int firstYear = mMinDate / 10000;
        int lastYear = mMaxDate / 10000;
        for (int year = firstYear + 1; year <= lastYear; year++) {
            long day = toEpochDay(year, 1, 1);
            if (day <= mMinDay || day >= mMaxDay) {
                continue;
            }
            float x = xForDay(day);
            canvas.drawLine(x, cy - mTickHalf, x, cy + mTickHalf, mTickPaint);
        }
    }

    /** 时间线两端标出卡包数据覆盖的最早 / 最晚年份，文字垫圆角黑板底（点它可复位对应滑块） */
    private void drawYearLabels(Canvas canvas, float cy) {
        if (mMinYearText.isEmpty()) {
            return;
        }
        Paint.FontMetrics fm = mYearPaint.getFontMetrics();
        float baseline = cy - (fm.ascent + fm.descent) / 2f;
        drawYearLabel(canvas, mMinYearText, mMinYearRect, baseline, mMinYearBackground, THUMB_START);
        drawYearLabel(canvas, mMaxYearText, mMaxYearRect, baseline, mMaxYearBackground, THUMB_END);
    }

    /**
     * 一端年份：先垫 button_radius_black_transparents 圆角黑板底，再写字；
     * 那一端正被按住时切到 drawable 的 pressed 状态，点下去有视觉反馈
     */
    private void drawYearLabel(Canvas canvas, String text, RectF pill, float baseline,
                               Drawable background, int thumb) {
        if (background != null) {
            background.setState(mYearPressedThumb == thumb ? YEAR_STATE_PRESSED : YEAR_STATE_DEFAULT);
            background.setBounds(Math.round(pill.left), Math.round(pill.top),
                    Math.round(pill.right), Math.round(pill.bottom));
            background.draw(canvas);
        }
        canvas.drawText(text, pill.left + mYearPillPadH, baseline, mYearPaint);
    }

    /**
     * 倒锥五边形滑块：上边水平，两侧竖边到肩部后向内收成尖端，
     * 尖端正好压在时间线上，指向它代表的那一天
     */
    private void drawThumb(Canvas canvas, float x, float cy) {
        final float top = cy - mThumbHeight;
        final float shoulder = cy - mThumbHeight * 0.42f;
        mThumbPath.reset();
        mThumbPath.moveTo(x - mThumbHalfW, top);
        mThumbPath.lineTo(x + mThumbHalfW, top);
        mThumbPath.lineTo(x + mThumbHalfW, shoulder);
        mThumbPath.lineTo(x, cy);
        mThumbPath.lineTo(x - mThumbHalfW, shoulder);
        mThumbPath.close();
        canvas.drawPath(mThumbPath, mThumbPaint);
        canvas.drawPath(mThumbPath, mThumbRingPaint);
    }

    /**
     * 两个日期气泡：各自画在自己滑块上方，先居中，挤到一起时左气泡让到右气泡左侧，
     * 同时夹在控件左右边界内，保证不重叠也不超界
     */
    private void drawDateLabels(Canvas canvas, float startX, float endX) {
        String startText = formatDate(toDate(mStartDay));
        String endText = formatDate(toDate(mEndDay));
        Paint.FontMetrics fm = mLabelTextPaint.getFontMetrics();
        float startWidth = mLabelTextPaint.measureText(startText) + mLabelPadH * 2f;
        float endWidth = mLabelTextPaint.measureText(endText) + mLabelPadH * 2f;
        float minLeft = getPaddingLeft();
        float maxLeft = getWidth() - getPaddingRight();

        float startLeft = clamp(startX - startWidth / 2f, minLeft, maxLeft - startWidth);
        float endLeft = clamp(endX - endWidth / 2f, minLeft, maxLeft - endWidth);
        int gap = dp(LABEL_GAP_DP);
        if (startLeft + startWidth + gap > endLeft) {
            // 两滑块靠得很近：左气泡退到右气泡左侧，实在放不下就贴最左
            startLeft = Math.max(minLeft, endLeft - startWidth - gap);
        }
        mStartLabelRect.set(startLeft, mLabelTop, startLeft + startWidth, mLabelTop + mLabelHeight);
        mEndLabelRect.set(endLeft, mLabelTop, endLeft + endWidth, mLabelTop + mLabelHeight);
        drawLabelRect(canvas, startText, mStartLabelRect, fm);
        drawLabelRect(canvas, endText, mEndLabelRect, fm);
    }

    private void drawLabelRect(Canvas canvas, String text, RectF rect, Paint.FontMetrics fm) {
        canvas.drawRoundRect(rect, mLabelCorner, mLabelCorner, mLabelBgPaint);
        canvas.drawRoundRect(rect, mLabelCorner, mLabelCorner, mLabelBorderPaint);
        canvas.drawText(text, rect.left + mLabelPadH, rect.top + mLabelPadV - fm.ascent, mLabelTextPaint);
    }

    private static float clamp(float value, float min, float max) {
        if (max < min) {
            return min;
        }
        return Math.max(min, Math.min(value, max));
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled() || !mHasRange) {
            return false;
        }
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                // 清掉上一次可能残留的按下态（正常情况在 UP 已清）
                mYearPressedThumb = THUMB_NONE;
                mLabelPressed = false;
                // 点在日期气泡上：交给页面弹日历选日期，本次手势不再拖滑块
                int labelThumb = findDateLabelAt(event.getX(), event.getY());
                if (labelThumb != THUMB_NONE) {
                    mLabelPressed = true;
                    if (getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    if (mLabelClickListener != null) {
                        mLabelClickListener.onDateLabelClick(labelThumb,
                                labelThumb == THUMB_START ? getStartDate() : getEndDate());
                    }
                    return true;
                }
                // 点在最左 / 最右年份牌上：对应滑块直接复位到那一端，本次手势不再拖滑块
                int yearThumb = findYearLabelAt(event.getX(), event.getY());
                if (yearThumb != THUMB_NONE) {
                    mYearPressedThumb = yearThumb;
                    resetThumbToEdge(yearThumb);
                    if (getParent() != null) {
                        getParent().requestDisallowInterceptTouchEvent(true);
                    }
                    return true;
                }
                mDownX = event.getX();
                float startX = xForDay(mStartDay);
                float endX = xForDay(mEndDay);
                float dStart = Math.abs(mDownX - startX);
                float dEnd = Math.abs(mDownX - endX);
                mActiveThumb = dStart <= dEnd ? THUMB_START : THUMB_END;
                // 两端几乎重合：先记下，等第一次移动按方向认领
                mThumbPending = Math.abs(startX - endX) <= mTouchSlop;
                if (!mThumbPending && Math.min(dStart, dEnd) > mGrabSlop) {
                    // 按在两个滑块之外：最近的一端直接跳到按下处
                    moveThumbToDay(mActiveThumb, dayForX(mDownX));
                }
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                invalidate();
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                if (mLabelPressed) {
                    // 点气泡弹日历的那次手势不拖滑块
                    return true;
                }
                if (mYearPressedThumb != THUMB_NONE) {
                    // 点年份牌的那次手势只做复位，不跟着拖动
                    return true;
                }
                if (mThumbPending) {
                    float dx = event.getX() - mDownX;
                    if (Math.abs(dx) <= mTouchSlop) {
                        return true;
                    }
                    mActiveThumb = dx > 0 ? THUMB_END : THUMB_START;
                    mThumbPending = false;
                }
                moveThumbToDay(mActiveThumb, dayForX(event.getX()));
                return true;
            }
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: {
                mThumbPending = false;
                mLabelPressed = false;
                if (mYearPressedThumb != THUMB_NONE) {
                    mYearPressedThumb = THUMB_NONE;
                    invalidate();
                }
                return true;
            }
            default:
                break;
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected void onDetachedFromWindow() {
        mLabelPressed = false;
        super.onDetachedFromWindow();
    }

    /**
     * 命中的是哪一端年份牌：左右两侧各自向外留出点击容差。
     * 横向只放宽 4dp（牌子右侧到滑块抓取区还有间隙，不会抢掉按住滑块的手势），纵向放宽 10dp
     */
    private int findYearLabelAt(float x, float y) {
        mHitRect.set(mMinYearRect);
        mHitRect.inset(-mYearHitSlopH, -mYearHitSlopV);
        if (mHitRect.contains(x, y)) {
            return THUMB_START;
        }
        mHitRect.set(mMaxYearRect);
        mHitRect.inset(-mYearHitSlopH, -mYearHitSlopV);
        if (mHitRect.contains(x, y)) {
            return THUMB_END;
        }
        return THUMB_NONE;
    }

    /** 点两端年份牌：对应滑块复位到时间轴那一端 */
    private void resetThumbToEdge(int thumb) {
        mActiveThumb = thumb;
        moveThumbToDay(thumb, thumb == THUMB_START ? mMinDay : mMaxDay);
        // 本来就在那一端时 moveThumbToDay 不会重绘，这里补一次，保证按下高亮能出来
        invalidate();
    }

    private void moveThumbToDay(int thumb, long day) {
        if (thumb == THUMB_START) {
            day = Math.max(mMinDay, Math.min(day, mEndDay));
            if (day == mStartDay) {
                return;
            }
            mStartDay = day;
        } else {
            day = Math.min(mMaxDay, Math.max(day, mStartDay));
            if (day == mEndDay) {
                return;
            }
            mEndDay = day;
        }
        invalidate();
        if (mListener != null) {
            mListener.onRangeChanged(toDate(mStartDay), toDate(mEndDay));
        }
    }

    /** 命中的是哪一端的日期气泡（气泡在滑块上方，点它弹日历） */
    private int findDateLabelAt(float x, float y) {
        if (mStartLabelRect.contains(x, y)) {
            return THUMB_START;
        }
        if (mEndLabelRect.contains(x, y)) {
            return THUMB_END;
        }
        return THUMB_NONE;
    }

    /**
     * 一次设定一个区间（日历里先后点选的开始/结束日）：
     * 日历可以选任意日期，所以这里不吸附卡包节点，只把两端夹在时间轴跨度内；
     * 传入的日序颠倒时自动摆正，只回调一次
     */
    public void setDateRange(int startDate, int endDate) {
        if (!mHasRange) {
            return;
        }
        long start = isValidDate(startDate) ? clampDay(startDate) : mStartDay;
        long end = isValidDate(endDate) ? clampDay(endDate) : mEndDay;
        if (start > end) {
            long temp = start;
            start = end;
            end = temp;
        }
        if (start == mStartDay && end == mEndDay) {
            return;
        }
        mStartDay = start;
        mEndDay = end;
        invalidate();
        if (mListener != null) {
            mListener.onRangeChanged(getStartDate(), getEndDate());
        }
    }

    /** yyyyMMdd → 天数并夹在时间轴跨度内（不吸附节点） */
    private long clampDay(int date) {
        long day = toEpochDay(date / 10000, date / 100 % 100, date % 100);
        return Math.max(mMinDay, Math.min(day, mMaxDay));
    }

    private float xForDay(long day) {
        long span = mMaxDay - mMinDay;
        if (span <= 0) {
            return mTrackLeft;
        }
        float ratio = (float) (day - mMinDay) / span;
        return mTrackLeft + (mTrackRight - mTrackLeft) * ratio;
    }

    private long dayForX(float x) {
        long span = mMaxDay - mMinDay;
        if (span <= 0 || mTrackRight <= mTrackLeft) {
            return mMinDay;
        }
        float ratio = (x - mTrackLeft) / (mTrackRight - mTrackLeft);
        ratio = Math.max(0f, Math.min(1f, ratio));
        return snapToNode(mMinDay + Math.round(ratio * span));
    }

    /**
     * 吸附到最近的卡包发售日期节点（节点按天数升序，二分找插入点后比两侧谁更近）；
     * 没有节点表时原样返回，退回逐日滑动
     */
    private long snapToNode(long day) {
        if (mNodeDays == null || mNodeDays.length == 0) {
            return day;
        }
        int found = Arrays.binarySearch(mNodeDays, day);
        if (found >= 0) {
            return mNodeDays[found];
        }
        int insert = -found - 1;
        if (insert <= 0) {
            return mNodeDays[0];
        }
        if (insert >= mNodeDays.length) {
            return mNodeDays[mNodeDays.length - 1];
        }
        long before = mNodeDays[insert - 1];
        long after = mNodeDays[insert];
        // 等距时取更早的那个，保证来回拖动同一位置的落点稳定
        return (day - before) <= (after - day) ? before : after;
    }

    private int getColor(int id) {
        return getResources().getColor(id);
    }

    private int dp(float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                getResources().getDisplayMetrics()));
    }

    private int sp(float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value,
                getResources().getDisplayMetrics()));
    }

    private static boolean isValidDate(int date) {
        int year = date / 10000;
        int month = date / 100 % 100;
        int day = date % 100;
        return year > 0 && year <= 9999 && month >= 1 && month <= 12 && day >= 1 && day <= 31;
    }

    /** yyyyMMdd → 展示文本 */
    private static String formatDate(int date) {
        return String.format(Locale.US, "%04d-%02d-%02d", date / 10000, date / 100 % 100, date % 100);
    }

    /**
     * 公历 y/m/d → 自 1970-01-01 起的天数（Howard Hinnant days_from_civil）。
     * 纯整数运算，不受时区与夏令时影响，2000 年前后的日期同样准确。
     */
    private static long toEpochDay(int year, int month, int day) {
        int y = year - (month <= 2 ? 1 : 0);
        long era = (y >= 0 ? y : y - 399) / 400;
        long yoe = y - era * 400;
        long doy = (153L * (month + (month > 2 ? -3 : 9)) + 2) / 5 + day - 1;
        long doe = yoe * 365 + yoe / 4 - yoe / 100 + doy;
        return era * 146097 + doe - 719468;
    }

    /** 天数 → yyyyMMdd（上面算法的逆运算） */
    private static int toDate(long epochDay) {
        long z = epochDay + 719468;
        long era = (z >= 0 ? z : z - 146096) / 146097;
        long doe = z - era * 146097;
        long yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365;
        long y = yoe + era * 400;
        long doy = doe - (365 * yoe + yoe / 4 - yoe / 100);
        long mp = (5 * doy + 2) / 153;
        long d = doy - (153 * mp + 2) / 5 + 1;
        long m = mp + (mp < 10 ? 3 : -9);
        long year = y + (m <= 2 ? 1 : 0);
        return (int) (year * 10000 + m * 100 + d);
    }
}
