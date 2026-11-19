package cn.garymb.ygomobile.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.Handler;
import android.os.Looper;
import android.text.format.DateFormat;
import android.util.TypedValue;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;

import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import cn.garymb.ygomobile.lite.R;

/**
 * 发售日期日历：按月铺开，任何日期都可以点选（有卡包的日子下方还会列出包名），
 * 选中的两端就是时间轴的开始/结束区间，区间内铺一条带圆角的连续高亮条。
 *
 * <p>打开时带着时间轴当前的区间与“先改哪一端”；点一个日期先改这一端并自动切到另一端，
 * 再点一个日期就把另一端也定下来（回调 finished = true，调用方可关闭日历），
 * 于是同一个日历里就能把左右两个拨块的日期范围一起选好；只点一次也可以，只改那一端。</p>
 *
 * <p>手势：轻点选日期；<b>横划</b>按月翻页；<b>长按高亮条</b>后拖动可直接拉出一个区间
 * （拖动端随手走，拖到左右边缘还会连续翻月，方便跨月选范围），松手后区间提交、日历不关。</p>
 *
 * <p>标题行左右各两对按钮：最外侧「» «」按年跳、内侧「› ‹」按月跳，
 * 翻到卡包数据覆盖的最早/最晚月份就停住。月份标题与星期名随系统语言，
 * 一周从周几开始按系统区域。</p>
 */
public class ReleaseDateCalendarView extends View {

    /** 区间变化：finished = 两端都已点选（第二次点选），调用方据此关闭日历 */
    public interface OnDateRangeChangedListener {
        void onDateRangeChanged(int startDate, int endDate, boolean finished);
    }

    /** 一个月最多 6 行（实际按月份占 4~6 行） */
    private static final int ROWS = 6;
    private static final int COLS = 7;
    /** 每个日期下方最多列几个卡包名，多出来的用“+N”表示 */
    private static final int MAX_PACK_LINES = 3;

    /** 手势状态 */
    private static final int GESTURE_NONE = 0;
    /** 按下还没定性：抬起算轻点 */
    private static final int GESTURE_PENDING = 1;
    /** 横向滑动：抬起时翻月 */
    private static final int GESTURE_SWIPE = 2;
    /** 长按后拖动选范围 */
    private static final int GESTURE_RANGE = 3;
    /** 竖向滑动：交给外层 ScrollView */
    private static final int GESTURE_VERTICAL = 4;

    /** 横划翻月的判定距离（dp） */
    private static final int SWIPE_THRESHOLD_DP = 48;
    /** 拖动时离左右边缘多近开始自动翻月（dp） */
    private static final int EDGE_PAGE_DP = 24;
    /** 边缘自动翻月的间隔（毫秒） */
    private static final long EDGE_PAGE_INTERVAL = 380L;

    private final Paint mTitlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mArrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mArrowDisabledPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mWeekPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mRangePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mRangeMutedPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mDayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mDayPlainPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mDayInRangePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mRangeBandPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mEndpointFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mEndpointStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mEndpointTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mEndpointOutlineTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mPackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mPackEndpointPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final RectF mCellRect = new RectF();
    private final RectF mPrevMonthRect = new RectF();
    private final RectF mNextMonthRect = new RectF();
    private final RectF mPrevYearRect = new RectF();
    private final RectF mNextYearRect = new RectF();
    /** 高亮条的路径（整条一次填充，圆角只加在真实起止处） */
    private final Path mBandPath = new Path();
    private final float[] mBandRadii = new float[8];

    /** 当前展示的月份（固定为 1 号） */
    private final Calendar mMonth = Calendar.getInstance();
    private final Calendar mWork = Calendar.getInstance();
    /** 标题下方“起 → 止”那行用的日历与日期格式（跟随系统语言） */
    private final Calendar mDateWork = Calendar.getInstance();
    private final SimpleDateFormat mDateFormat;
    private final Set<Integer> mAvailableDates = new HashSet<>();
    /** 日期 → 当天发售的卡包名 */
    private final Map<Integer, List<String>> mPackNames = new HashMap<>();
    private OnDateRangeChangedListener mListener;

    /** 已选的区间（yyyyMMdd）；打开时就是时间轴当前的区间 */
    private int mRangeStart;
    private int mRangeEnd;
    /** 下一次点选改哪一端（true = 改开始端） */
    private boolean mActiveStart = true;
    /** 画成实心金底的是哪一端（刚动过的那端，打开时就是被点气泡的那端） */
    private boolean mFilledStart = true;
    /** 本次打开后是否已经点过一次（第二次点选即完成区间） */
    private boolean mPickedOnce;
    /** 可翻到的月份上下限（年*12+月，含）；0 = 不限制（还没拿到可选日期） */
    private int mMinYearMonth;
    private int mMaxYearMonth;
    /** 卡包数据里最早/最晚的可选日期；区间端点碰到它们时“起 → 止”那一侧变淡 */
    private int mMinAvailDate;
    private int mMaxAvailDate;

    private final int mTitleHeight;
    /** 标题下方“起 → 止”那行的高度 */
    private final int mRangeHeight;
    private final int mWeekHeight;
    /** 当前月份占几行、每格多高（随月份与卡包名行数重算） */
    private int mRows = ROWS;
    private int mPackLines;
    private int mCellHeight;
    private final int mNumberHeight;
    private final int mPackLineHeight;
    private final int mYearHitWidth;
    private final int mMonthHitWidth;

    private final int mTouchSlop;
    private final int mSwipeThreshold;
    private final int mEdgeWidth;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private int mGestureMode = GESTURE_NONE;
    private float mDownX;
    private float mDownY;
    /** 长按拖动：不动的一端（锚点）与随手移动的一端 */
    private int mRangeAnchorDate;
    private int mDragDate;
    /** 边缘自动翻月：-1 向左、1 向右、0 不翻 */
    private int mEdgeDirection;

    private final Runnable mLongPressRunnable = new Runnable() {
        @Override
        public void run() {
            if (mGestureMode != GESTURE_PENDING) {
                return;
            }
            int date = dateAtPoint(mDownX, mDownY);
            if (date == 0) {
                return;
            }
            mGestureMode = GESTURE_RANGE;
            mRangeAnchorDate = date;
            mDragDate = date;
            mRangeStart = date;
            mRangeEnd = date;
            mFilledStart = true;
            mActiveStart = false;
            // 拖动期间别让外层 ScrollView 抢走竖向手势
            if (getParent() != null) {
                getParent().requestDisallowInterceptTouchEvent(true);
            }
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            invalidate();
        }
    };

    private final Runnable mEdgePageRunnable = new Runnable() {
        @Override
        public void run() {
            if (mGestureMode != GESTURE_RANGE || mEdgeDirection == 0) {
                return;
            }
            int before = yearMonth();
            shiftMonth(mEdgeDirection);
            if (yearMonth() == before) {
                mEdgeDirection = 0; // 已经到头，不再翻
                return;
            }
            // 翻到新月后把拖动端落到该月靠边的一天，区间跟着延伸
            mDragDate = mEdgeDirection < 0 ? firstDayOfShownMonth() : lastDayOfShownMonth();
            applyDragRange();
            mHandler.postDelayed(this, EDGE_PAGE_INTERVAL);
        }
    };

    public ReleaseDateCalendarView(Context context) {
        super(context);
        mTitleHeight = dp(34);
        mRangeHeight = dp(18);
        mWeekHeight = dp(18);
        mNumberHeight = dp(16);
        mPackLineHeight = dp(9);
        mYearHitWidth = dp(32);
        mMonthHitWidth = dp(28);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        mSwipeThreshold = dp(SWIPE_THRESHOLD_DP);
        mEdgeWidth = dp(EDGE_PAGE_DP);

        mTitlePaint.setColor(getColor(R.color.gold));
        mTitlePaint.setTextSize(sp(13));
        mTitlePaint.setTextAlign(Paint.Align.CENTER);
        mTitlePaint.setFakeBoldText(true);

        mArrowPaint.setColor(getColor(R.color.gold));
        mArrowPaint.setTextSize(sp(17));
        mArrowPaint.setTextAlign(Paint.Align.CENTER);

        mArrowDisabledPaint.setColor(0x40FFFFFF);
        mArrowDisabledPaint.setTextSize(sp(17));
        mArrowDisabledPaint.setTextAlign(Paint.Align.CENTER);

        mWeekPaint.setColor(0x99FFFFFF);
        mWeekPaint.setTextSize(sp(10));
        mWeekPaint.setTextAlign(Paint.Align.CENTER);

        mRangePaint.setColor(getColor(R.color.gold));
        mRangePaint.setTextSize(sp(11));
        mRangePaint.setTextAlign(Paint.Align.LEFT);
        mRangeMutedPaint.setColor(0x66FFD700);
        mRangeMutedPaint.setTextSize(sp(11));
        mRangeMutedPaint.setTextAlign(Paint.Align.LEFT);

        String datePattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "yMd");
        mDateFormat = new SimpleDateFormat(datePattern, Locale.getDefault());

        mDayPaint.setColor(0xE6FFFFFF);
        mDayPaint.setTextSize(sp(12));
        mDayPaint.setTextAlign(Paint.Align.CENTER);

        mDayPlainPaint.setColor(0xA6FFFFFF);
        mDayPlainPaint.setTextSize(sp(12));
        mDayPlainPaint.setTextAlign(Paint.Align.CENTER);

        mDayInRangePaint.setColor(0xB3FFFFFF);
        mDayInRangePaint.setTextSize(sp(12));
        mDayInRangePaint.setTextAlign(Paint.Align.CENTER);

        // 高亮条用与时间轴选中区间同一个颜色（holo_blue_bright）的半透明版，
        // 让用户一眼看出日历里这一条就是时间轴上的那段蓝色区间
        int activeTrackColor = getColor(R.color.holo_blue_bright);
        mRangeBandPaint.setColor((activeTrackColor & 0x00FFFFFF) | 0x4D000000);

        mEndpointFillPaint.setColor(getColor(R.color.gold));

        mEndpointStrokePaint.setColor(getColor(R.color.gold));
        mEndpointStrokePaint.setStyle(Paint.Style.STROKE);
        mEndpointStrokePaint.setStrokeWidth(Math.max(1f, dp(1.2f)));

        mEndpointTextPaint.setColor(0xFF101828);
        mEndpointTextPaint.setTextSize(sp(12));
        mEndpointTextPaint.setTextAlign(Paint.Align.CENTER);
        mEndpointTextPaint.setFakeBoldText(true);

        mEndpointOutlineTextPaint.setColor(getColor(R.color.gold));
        mEndpointOutlineTextPaint.setTextSize(sp(12));
        mEndpointOutlineTextPaint.setTextAlign(Paint.Align.CENTER);
        mEndpointOutlineTextPaint.setFakeBoldText(true);

        mPackPaint.setColor(0x8CFFFFFF);
        mPackPaint.setTextSize(sp(7));
        mPackPaint.setTextAlign(Paint.Align.CENTER);

        mPackEndpointPaint.setColor(getColor(R.color.gold));
        mPackEndpointPaint.setTextSize(sp(7));
        mPackEndpointPaint.setTextAlign(Paint.Align.CENTER);

        mMonth.set(Calendar.DAY_OF_MONTH, 1);
        updateMetrics();
    }

    /**
     * 按当前月份重算版式：该月实际占几行（4~6 行），每格需为卡包名留几行小字（0~3 行）。
     * 于是没有卡包名可显示的月份不会在格子底部盲目留白，行数少的月份底部也不会多出一整行
     */
    private void updateMetrics() {
        int year = mMonth.get(Calendar.YEAR);
        int month = mMonth.get(Calendar.MONTH);
        int days = daysInMonth(year, month);
        int offset = monthOffset(year, month);
        int rows = Math.max(1, Math.min(ROWS, (offset + days + COLS - 1) / COLS));
        int lines = 0;
        for (int day = 1; day <= days; day++) {
            List<String> names = mPackNames.get(year * 10000 + (month + 1) * 100 + day);
            if (names != null && !names.isEmpty()) {
                lines = Math.max(lines, Math.min(MAX_PACK_LINES, names.size()));
            }
        }
        int cell = dp(3) + mNumberHeight + dp(3);
        if (lines > 0) {
            cell += dp(2) + mPackLineHeight * lines;
        }
        boolean changed = rows != mRows || cell != mCellHeight;
        mRows = rows;
        mPackLines = lines;
        mCellHeight = cell;
        if (changed) {
            requestLayout();
        }
        invalidate();
    }

    /** 可选日期（yyyyMMdd）：决定能翻到的最早/最晚月份，也用于日期下方的小字与亮度 */
    public void setAvailableDates(Set<Integer> dates) {
        mAvailableDates.clear();
        if (dates != null) {
            mAvailableDates.addAll(dates);
        }
        int min = 0;
        int max = 0;
        for (Integer date : mAvailableDates) {
            if (date == null || date <= 0) {
                continue;
            }
            if (min == 0 || date < min) {
                min = date;
            }
            if (max == 0 || date > max) {
                max = date;
            }
        }
        mMinYearMonth = min == 0 ? 0 : toYearMonth(min);
        mMaxYearMonth = max == 0 ? 0 : toYearMonth(max);
        mMinAvailDate = min;
        mMaxAvailDate = max;
        if (mMinYearMonth > 0) {
            setMonthClamped(yearMonth());
        }
        updateMetrics();
    }

    /** 日期 → 当天发售的卡包名；日期下方用小字列出来 */
    public void setPackNamesByDate(Map<Integer, List<String>> packNames) {
        mPackNames.clear();
        if (packNames != null) {
            mPackNames.putAll(packNames);
        }
        updateMetrics();
    }

    /**
     * 设定当前区间（通常是时间轴上的开始/结束日）：区间内铺连续高亮条，
     * 并把日历翻到“下一次要改的那一端”所在的月份
     */
    public void setDateRange(int startDate, int endDate) {
        mRangeStart = startDate;
        mRangeEnd = endDate;
        int focus = mActiveStart ? startDate : endDate;
        int year = focus / 10000;
        int month = focus / 100 % 100;
        if (year > 0 && month >= 1 && month <= 12) {
            setMonthClamped(year * 12 + (month - 1));
        }
        invalidate();
    }

    /** 打开时从哪一端开始改：true = 先改开始端（点的是左拨块的气泡） */
    public void setActiveStart(boolean activeStart) {
        mActiveStart = activeStart;
        mFilledStart = activeStart;
        mPickedOnce = false;
    }

    public void setOnDateRangeChangedListener(OnDateRangeChangedListener listener) {
        mListener = listener;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = getDefaultSize(dp(300), widthMeasureSpec);
        int height = mTitleHeight + mRangeHeight + mWeekHeight + mCellHeight * mRows;
        setMeasuredDimension(width, resolveSize(height, heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float cellWidth = width / (float) COLS;
        int year = mMonth.get(Calendar.YEAR);
        int month = mMonth.get(Calendar.MONTH);
        int daysInMonth = daysInMonth(year, month);
        int offset = monthOffset(year, month);
        float gridTop = mTitleHeight + mRangeHeight + mWeekHeight;

        drawTitle(canvas, width);
        drawRangeText(canvas, width);
        drawWeekHeader(canvas, cellWidth);
        // 先铺区间高亮条，再画日期数字与卡包名，条子在字下面
        drawRangeBand(canvas, cellWidth, gridTop, year, month, offset, daysInMonth);
        drawDays(canvas, cellWidth, gridTop, year, month, offset, daysInMonth);
    }

    private void drawTitle(Canvas canvas, float width) {
        mPrevYearRect.set(0, 0, mYearHitWidth, mTitleHeight);
        mPrevMonthRect.set(mYearHitWidth, 0, mYearHitWidth + mMonthHitWidth, mTitleHeight);
        mNextMonthRect.set(width - mYearHitWidth - mMonthHitWidth, 0,
                width - mYearHitWidth, mTitleHeight);
        mNextYearRect.set(width - mYearHitWidth, 0, width, mTitleHeight);

        float baseline = (mTitleHeight - (mArrowPaint.ascent() + mArrowPaint.descent())) / 2f;
        // 已经到了卡包数据覆盖的最早/最晚月份：那一侧的箭头置灰，不能再翻
        Paint prevPaint = canGoPrevMonth() ? mArrowPaint : mArrowDisabledPaint;
        Paint nextPaint = canGoNextMonth() ? mArrowPaint : mArrowDisabledPaint;
        canvas.drawText("«", mPrevYearRect.centerX(), baseline, prevPaint);
        canvas.drawText("‹", mPrevMonthRect.centerX(), baseline, prevPaint);
        canvas.drawText("›", mNextMonthRect.centerX(), baseline, nextPaint);
        canvas.drawText("»", mNextYearRect.centerX(), baseline, nextPaint);

        String pattern = DateFormat.getBestDateTimePattern(Locale.getDefault(), "yMMMM");
        String title = new SimpleDateFormat(pattern, Locale.getDefault()).format(mMonth.getTime());
        // 标题居中；语言不同标题可能很长，放不下就按比例缩小字号，画完复原
        float midLeft = mPrevMonthRect.right + dp(2);
        float midRight = mNextMonthRect.left - dp(2);
        float maxWidth = midRight - midLeft;
        float textSize = mTitlePaint.getTextSize();
        float textWidth = mTitlePaint.measureText(title);
        if (maxWidth > 0 && textWidth > maxWidth) {
            mTitlePaint.setTextSize(textSize * maxWidth / textWidth);
        }
        Paint.FontMetrics fm = mTitlePaint.getFontMetrics();
        float titleBaseline = (mTitleHeight - (fm.ascent + fm.descent)) / 2f;
        canvas.drawText(title, (midLeft + midRight) / 2f, titleBaseline, mTitlePaint);
        mTitlePaint.setTextSize(textSize);
    }

    /**
     * 标题下方的“起 → 止”：日期格式跟随系统语言；
     * 端点已经贴到（或超出）卡包数据的最早/最晚日期时，那一侧文字变淡，提示已达边界
     */
    private void drawRangeText(Canvas canvas, float width) {
        if (mRangeStart <= 0 || mRangeEnd <= 0) {
            return;
        }
        String startText = formatShortDate(mRangeStart);
        String endText = formatShortDate(mRangeEnd);
        String arrow = " → ";
        Paint startPaint = mMinAvailDate > 0 && mRangeStart <= mMinAvailDate
                ? mRangeMutedPaint : mRangePaint;
        Paint endPaint = mMaxAvailDate > 0 && mRangeEnd >= mMaxAvailDate
                ? mRangeMutedPaint : mRangePaint;

        float startWidth = mRangePaint.measureText(startText);
        float arrowWidth = mRangePaint.measureText(arrow);
        float endWidth = mRangePaint.measureText(endText);
        float total = startWidth + arrowWidth + endWidth;
        float maxWidth = width - dp(16);
        float baseSize = mRangePaint.getTextSize();
        if (maxWidth > 0 && total > maxWidth) {
            // 日期格式随语言长短不一，放不下就整体缩小字号，画完复原
            float scaled = baseSize * maxWidth / total;
            mRangePaint.setTextSize(scaled);
            mRangeMutedPaint.setTextSize(scaled);
            startWidth = mRangePaint.measureText(startText);
            arrowWidth = mRangePaint.measureText(arrow);
            endWidth = mRangePaint.measureText(endText);
            total = startWidth + arrowWidth + endWidth;
        }
        Paint.FontMetrics fm = mRangePaint.getFontMetrics();
        float baseline = mTitleHeight + mRangeHeight / 2f - (fm.ascent + fm.descent) / 2f;
        float x = (width - total) / 2f;
        canvas.drawText(startText, x, baseline, startPaint);
        x += startWidth;
        canvas.drawText(arrow, x, baseline, mRangeMutedPaint);
        x += arrowWidth;
        canvas.drawText(endText, x, baseline, endPaint);
        mRangePaint.setTextSize(baseSize);
        mRangeMutedPaint.setTextSize(baseSize);
    }

    /** yyyyMMdd → 跟随系统语言的短日期 */
    private String formatShortDate(int date) {
        mDateWork.set(date / 10000, date / 100 % 100 - 1, date % 100);
        return mDateFormat.format(mDateWork.getTime());
    }

    private void drawWeekHeader(Canvas canvas, float cellWidth) {
        String[] names = DateFormatSymbols.getInstance(Locale.getDefault()).getShortWeekdays();
        int firstDayOfWeek = Calendar.getInstance().getFirstDayOfWeek();
        Paint.FontMetrics fm = mWeekPaint.getFontMetrics();
        float baseline = mTitleHeight + mRangeHeight + (mWeekHeight - (fm.ascent + fm.descent)) / 2f;
        for (int i = 0; i < COLS; i++) {
            int dayOfWeek = (firstDayOfWeek - 1 + i) % 7 + 1;
            canvas.drawText(names[dayOfWeek], cellWidth * i + cellWidth / 2f, baseline, mWeekPaint);
        }
    }

    /**
     * 区间高亮条：按行拼成一条路径一次填充，行与行之间略微交叠不出缝；
     * 圆角只加在区间真正的起始行（左侧）与结束行（右侧），跨月时那一侧不圆角，
     * 看起来就是一条从开始日一直延续到结束日的连续条子
     */
    private void drawRangeBand(Canvas canvas, float cellWidth, float gridTop,
                               int year, int month, int offset, int daysInMonth) {
        if (mRangeStart <= 0 || mRangeEnd < mRangeStart) {
            return;
        }
        int startRow = -1;
        int endRow = -1;
        for (int row = 0; row < mRows; row++) {
            for (int column = 0; column < COLS; column++) {
                int date = dateAt(year, month, row, column, offset, daysInMonth);
                if (date == 0) {
                    continue;
                }
                if (date == mRangeStart) {
                    startRow = row;
                }
                if (date == mRangeEnd) {
                    endRow = row;
                }
            }
        }
        mBandPath.reset();
        float radius = dp(8);
        for (int row = 0; row < mRows; row++) {
            int minColumn = -1;
            int maxColumn = -1;
            for (int column = 0; column < COLS; column++) {
                int date = dateAt(year, month, row, column, offset, daysInMonth);
                if (date >= mRangeStart && date <= mRangeEnd) {
                    if (minColumn < 0) {
                        minColumn = column;
                    }
                    maxColumn = column;
                }
            }
            if (minColumn < 0) {
                continue;
            }
            boolean roundLeft = row == startRow;
            boolean roundRight = row == endRow;
            mBandRadii[0] = mBandRadii[1] = roundLeft ? radius : 0f;
            mBandRadii[2] = mBandRadii[3] = roundRight ? radius : 0f;
            mBandRadii[4] = mBandRadii[5] = roundRight ? radius : 0f;
            mBandRadii[6] = mBandRadii[7] = roundLeft ? radius : 0f;
            float top = gridTop + row * mCellHeight;
            mCellRect.set(minColumn * cellWidth, top - 0.5f,
                    (maxColumn + 1) * cellWidth, top + mCellHeight + 0.5f);
            mBandPath.addRoundRect(mCellRect, mBandRadii, Path.Direction.CW);
        }
        canvas.drawPath(mBandPath, mRangeBandPaint);
    }

    private void drawDays(Canvas canvas, float cellWidth, float gridTop,
                          int year, int month, int offset, int daysInMonth) {
        float inset = dp(3);
        for (int cell = 0; cell < mRows * COLS; cell++) {
            int row = cell / COLS;
            int column = cell % COLS;
            int date = dateAt(year, month, row, column, offset, daysInMonth);
            if (date == 0) {
                continue;
            }
            float left = column * cellWidth;
            float top = gridTop + row * mCellHeight;
            int day = date % 100;
            boolean available = mAvailableDates.contains(date);
            boolean inRange = mRangeStart > 0 && date >= mRangeStart && date <= mRangeEnd;
            boolean isStart = mRangeEnd > 0 && date == mRangeStart;
            boolean isEnd = mRangeEnd > 0 && date == mRangeEnd;
            boolean endpoint = isStart || isEnd;

            // 日期数字占格子顶部一小条；区间两端：实心金底 = 刚动过的一端，金框 = 下一次点选要改的一端
            float numberTop = top + dp(3);
            float numberBottom = numberTop + mNumberHeight;
            mCellRect.set(left + inset, numberTop, left + cellWidth - inset, numberBottom);
            Paint dayPaint;
            if (endpoint) {
                boolean filled = (isStart && mFilledStart) || (isEnd && !mFilledStart);
                if (filled) {
                    canvas.drawRoundRect(mCellRect, dp(6), dp(6), mEndpointFillPaint);
                    dayPaint = mEndpointTextPaint;
                } else {
                    canvas.drawRoundRect(mCellRect, dp(6), dp(6), mEndpointStrokePaint);
                    dayPaint = mEndpointOutlineTextPaint;
                }
            } else if (inRange) {
                dayPaint = mDayInRangePaint;
            } else {
                dayPaint = available ? mDayPaint : mDayPlainPaint;
            }
            Paint.FontMetrics dayFm = dayPaint.getFontMetrics();
            float dayBaseline = (numberTop + numberBottom) / 2f - (dayFm.ascent + dayFm.descent) / 2f;
            canvas.drawText(String.valueOf(day), left + cellWidth / 2f, dayBaseline, dayPaint);

            List<String> names = mPackNames.get(date);
            if (!available && (names == null || names.isEmpty())) {
                continue;
            }
            Paint packPaint = endpoint ? mPackEndpointPaint : mPackPaint;
            if (names == null || names.isEmpty()) {
                canvas.drawCircle(left + cellWidth / 2f, numberBottom + dp(3), dp(1.2f), packPaint);
                continue;
            }
            int shown = Math.min(MAX_PACK_LINES, names.size());
            float maxTextWidth = cellWidth - dp(2);
            for (int i = 0; i < shown; i++) {
                String text;
                if (i == shown - 1 && names.size() > shown) {
                    text = "+" + (names.size() - shown);
                } else {
                    text = fitText(names.get(i), maxTextWidth);
                }
                float lineBaseline = numberBottom + dp(2) + mPackLineHeight * (i + 1) - dp(2);
                canvas.drawText(text, left + cellWidth / 2f, lineBaseline, packPaint);
            }
        }
    }

    /** 卡包名放不下一列宽时，末尾截断加省略号 */
    private String fitText(String text, float maxWidth) {
        if (mPackPaint.measureText(text) <= maxWidth) {
            return text;
        }
        int length = text.length();
        while (length > 1 && mPackPaint.measureText(text, 0, length) + mPackPaint.measureText("…") > maxWidth) {
            length--;
        }
        return text.substring(0, length) + "…";
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                mDownX = event.getX();
                mDownY = event.getY();
                mGestureMode = GESTURE_PENDING;
                mEdgeDirection = 0;
                mHandler.removeCallbacks(mEdgePageRunnable);
                mHandler.removeCallbacks(mLongPressRunnable);
                mHandler.postDelayed(mLongPressRunnable, ViewConfiguration.getLongPressTimeout());
                return true;
            }
            case MotionEvent.ACTION_MOVE: {
                float x = event.getX();
                float y = event.getY();
                if (mGestureMode == GESTURE_PENDING) {
                    float dx = Math.abs(x - mDownX);
                    float dy = Math.abs(y - mDownY);
                    if (dx > mTouchSlop || dy > mTouchSlop) {
                        // 动了就不算长按；横向为主算横划翻月，纵向交给外层滚动
                        mHandler.removeCallbacks(mLongPressRunnable);
                        mGestureMode = dx > dy ? GESTURE_SWIPE : GESTURE_VERTICAL;
                    }
                } else if (mGestureMode == GESTURE_RANGE) {
                    moveRangeSelection(x, y);
                }
                return true;
            }
            case MotionEvent.ACTION_UP: {
                mHandler.removeCallbacks(mLongPressRunnable);
                mHandler.removeCallbacks(mEdgePageRunnable);
                mEdgeDirection = 0;
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                }
                int mode = mGestureMode;
                mGestureMode = GESTURE_NONE;
                if (mode == GESTURE_RANGE) {
                    // 拖动结束：把拉出的区间提交给调用方（日历不关，可以接着点另一端）
                    mPickedOnce = true;
                    if (mListener != null) {
                        mListener.onDateRangeChanged(mRangeStart, mRangeEnd, false);
                    }
                } else if (mode == GESTURE_SWIPE) {
                    float dx = event.getX() - mDownX;
                    if (Math.abs(dx) >= mSwipeThreshold) {
                        shiftMonth(dx < 0 ? 1 : -1);
                    }
                } else if (mode == GESTURE_PENDING) {
                    handleTap(event.getX(), event.getY());
                }
                return true;
            }
            case MotionEvent.ACTION_CANCEL: {
                mHandler.removeCallbacks(mLongPressRunnable);
                mHandler.removeCallbacks(mEdgePageRunnable);
                mEdgeDirection = 0;
                mGestureMode = GESTURE_NONE;
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(false);
                }
                return true;
            }
            default:
                return super.onTouchEvent(event);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        mHandler.removeCallbacks(mLongPressRunnable);
        mHandler.removeCallbacks(mEdgePageRunnable);
        super.onDetachedFromWindow();
    }

    /** 轻点：标题行按钮翻页，日期格子改区间（点一下改一端，再点一下改另一端并结束） */
    private void handleTap(float x, float y) {
        if (y <= mTitleHeight) {
            // 最外侧按年跳，内侧按月跳；超出卡包数据覆盖的月份会被夹住
            if (mPrevYearRect.contains(x, y)) {
                shiftMonth(-12);
            } else if (mNextYearRect.contains(x, y)) {
                shiftMonth(12);
            } else if (mPrevMonthRect.contains(x, y)) {
                shiftMonth(-1);
            } else if (mNextMonthRect.contains(x, y)) {
                shiftMonth(1);
            }
            return;
        }
        int date = dateAtPoint(x, y);
        if (date == 0) {
            return;
        }
        if (mActiveStart) {
            mRangeStart = date;
            if (mRangeStart > mRangeEnd) {
                mRangeEnd = mRangeStart;
            }
        } else {
            mRangeEnd = date;
            if (mRangeEnd < mRangeStart) {
                mRangeStart = mRangeEnd;
            }
        }
        // 刚点的一端画实心；下一次点选改另一端
        mFilledStart = mActiveStart;
        mActiveStart = !mActiveStart;
        boolean finished = mPickedOnce;
        mPickedOnce = true;
        invalidate();
        if (mListener != null) {
            mListener.onDateRangeChanged(mRangeStart, mRangeEnd, finished);
        }
    }

    /** 长按拖动中：拖动端跟手，区间 = 锚点与拖动端之间，并处理贴边连续翻月 */
    private void moveRangeSelection(float x, float y) {
        int date = dateAtPoint(x, y);
        if (date != 0) {
            mDragDate = date;
            applyDragRange();
        }
        int direction = 0;
        if (x < mEdgeWidth) {
            direction = -1;
        } else if (x > getWidth() - mEdgeWidth) {
            direction = 1;
        }
        if (direction != mEdgeDirection) {
            mEdgeDirection = direction;
            mHandler.removeCallbacks(mEdgePageRunnable);
            if (direction != 0) {
                mHandler.post(mEdgePageRunnable);
            }
        }
    }

    /** 按锚点与拖动端算出展示用的区间，并把刚动过的一端画成实心 */
    private void applyDragRange() {
        mRangeStart = Math.min(mRangeAnchorDate, mDragDate);
        mRangeEnd = Math.max(mRangeAnchorDate, mDragDate);
        mFilledStart = mDragDate <= mRangeAnchorDate;
        mActiveStart = !mFilledStart;
        invalidate();
    }

    /** 屏幕坐标 → yyyyMMdd；不在本月格子里返回 0 */
    private int dateAtPoint(float x, float y) {
        float gridTop = mTitleHeight + mRangeHeight + mWeekHeight;
        if (y < gridTop) {
            return 0;
        }
        float cellWidth = getWidth() / (float) COLS;
        int column = (int) (x / cellWidth);
        int row = (int) ((y - gridTop) / mCellHeight);
        if (column < 0 || column >= COLS || row < 0 || row >= mRows) {
            return 0;
        }
        int year = mMonth.get(Calendar.YEAR);
        int month = mMonth.get(Calendar.MONTH);
        return dateAt(year, month, row, column, monthOffset(year, month), daysInMonth(year, month));
    }

    /** 某格子对应的日期；不是当月的格子返回 0 */
    private int dateAt(int year, int month, int row, int column, int offset, int daysInMonth) {
        int day = row * COLS + column - offset + 1;
        if (day < 1 || day > daysInMonth) {
            return 0;
        }
        return year * 10000 + (month + 1) * 100 + day;
    }

    /** 翻月/翻年：结果夹在可选日期的最早/最晚月份之间，到头就停在那一月 */
    private void shiftMonth(int months) {
        setMonthClamped(yearMonth() + months);
        invalidate();
    }

    /** 把当前展示月份设到指定的年*12+月，超出可选日期的月份时夹到边界 */
    private void setMonthClamped(int yearMonth) {
        if (mMinYearMonth > 0 && yearMonth < mMinYearMonth) {
            yearMonth = mMinYearMonth;
        }
        if (mMaxYearMonth > 0 && yearMonth > mMaxYearMonth) {
            yearMonth = mMaxYearMonth;
        }
        int current = yearMonth();
        if (current != yearMonth) {
            mMonth.add(Calendar.MONTH, yearMonth - current);
            mMonth.set(Calendar.DAY_OF_MONTH, 1);
        }
        // 月份变了，行数与是否需要给卡包名留行都可能变
        updateMetrics();
    }

    private int yearMonth() {
        return mMonth.get(Calendar.YEAR) * 12 + mMonth.get(Calendar.MONTH);
    }

    private boolean canGoPrevMonth() {
        return mMinYearMonth <= 0 || yearMonth() > mMinYearMonth;
    }

    private boolean canGoNextMonth() {
        return mMaxYearMonth <= 0 || yearMonth() < mMaxYearMonth;
    }

    private int firstDayOfShownMonth() {
        return mMonth.get(Calendar.YEAR) * 10000 + (mMonth.get(Calendar.MONTH) + 1) * 100 + 1;
    }

    private int lastDayOfShownMonth() {
        return mMonth.get(Calendar.YEAR) * 10000 + (mMonth.get(Calendar.MONTH) + 1) * 100
                + mMonth.getActualMaximum(Calendar.DAY_OF_MONTH);
    }

    /** yyyyMMdd → 年*12+月（0 起的月号） */
    private static int toYearMonth(int date) {
        return (date / 10000) * 12 + (date / 100 % 100 - 1);
    }

    /** 该月第一天在当月网格里的列号（按系统区域的一周首日） */
    private int monthOffset(int year, int month) {
        mWork.set(year, month, 1);
        int firstDayOfWeek = Calendar.getInstance().getFirstDayOfWeek();
        return (mWork.get(Calendar.DAY_OF_WEEK) - firstDayOfWeek + 7) % 7;
    }

    private int daysInMonth(int year, int month) {
        mWork.set(year, month, 1);
        return mWork.getActualMaximum(Calendar.DAY_OF_MONTH);
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
}
