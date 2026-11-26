package cn.garymb.ygomobile.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewTreeObserver;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import cn.garymb.ygomobile.lite.R;

/**
 * 搜索结果列表 / 宫格的分段说明：每段第一行上方留出一条说明带，带子中央画一条浅色横线，
 * 横线在中间断开写该段说明（星数「13星」、阶级「6阶」、LINK「LINK-3」、攻守「5000~4501」、
 * 名称「A」、时间「2024年」），文本末尾再带一个箭头图标。
 *
 * <p>点分割带即可折叠 / 展开对应分段（用户规格）：收起后段名后的箭头朝上
 * （baseline_keyboard_arrow_up_24），展开时朝下（baseline_keyboard_arrow_down_24）。
 * 折叠由数据层（{@link SectionTitleProvider#toggleSection}）把该段的卡从可见条目里摘掉，
 * 本类只管留高度、画带子与把手势认出来，所以条目位置、点击/拖拽位置都不受影响；
 * 在带子上按住拖动时本类自己推列表上下滚，收起态不会失去滚动能力。</p>
 *
 * <p>整段被收起后它自己没有任何可见条目，于是它的分割带挂在下一段首行的上方（自上而下
 * 依次排开，可以连续叠多条）；末尾几段都收起时挂在最后一个可见条目的下方；
 * 全部分段都收起时改由本类维护一个自己的滚动偏移从列表顶部画带子，
 * 因为此时列表里没有任何条目、RecyclerView 没有可滚的内容，
 * 段数多的排序档一屏装不下所有线（用户报的“全收起后滚不动、下面的线展不开”）。</p>
 *
 * <p>折叠与展开的条目变化由数据层发 notifyItemRangeRemoved / notifyItemRangeInserted，
 * 本类在 {@link #attachTo(RecyclerView)} 里把增删动画收到 180ms 左右，看起来是快速过渡而不是整列闪跳；
 * 同时把被点的那条线当锚点：本次布局完成后、绘制之前按像素差把列表推回去，
 * 收起或展开后那条线仍停在屏幕上原来的位置（不会闪回顶部）。</p>
 *
 * <p>列表模式下“一行”就是一张卡；宫格模式下适配器会把每段最后一张卡撑满本行，
 * 于是每个分段都从新的一行开始，说明带加在整行上方（该行每张卡都加同样的顶偏移，行内不会错位），
 * 也不会把下一段的卡塞进上一段的末行。</p>
 */
public class SectionDividerDecoration extends RecyclerView.ItemDecoration {

    /**
     * 一条分割带：折叠判定用的段名键（不含张数）+ 要画的文本（含张数）+ 该段当前是否已收起
     */
    public static final class SectionBand {
        public final String key;
        public final String text;
        public final boolean collapsed;

        public SectionBand(@NonNull String key, @NonNull String text, boolean collapsed) {
            this.key = key;
            this.text = text;
            this.collapsed = collapsed;
        }
    }

    /**
     * 分段提供者：由搜索结果适配器实现。分割带的位置、文本与折叠态都由数据层算好，
     * 本类只按这些数据画线与图标
     */
    public interface SectionTitleProvider {
        /**
         * 该位置所属分段首位置上方要画的分割带（自上而下；含紧随其上、整段被收起的段）。
         * 同一宫格首行里的任意位置问出来都应该是同一份；不分段时返回 null
         */
        @Nullable
        List<SectionBand> getTopBands(int position);

        /** 该位置下方要画的分割带（末尾整段被收起时挂在最后一个可见条目下面）；没有返回 null */
        @Nullable
        List<SectionBand> getBottomBands(int position);

        /** 该位置所属分段的首位置；不分段或越界返回 -1 */
        int getSectionHead(int position);

        /** 一条可见条目都没有时（全部分段都被收起）要从列表顶部画出的分割带；没有返回 null */
        @Nullable
        List<SectionBand> getEmptyBands();

        /** 点中某条分割带：数据层翻转该段的折叠状态并刷新列表 */
        void toggleSection(String sectionKey);
    }

    private final Paint mLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** 说明带高度（px） */
    private final int mBandHeight;
    /** 说明文字两侧与分割线之间的留白（px） */
    private final int mTextGap;
    /** 文字与箭头图标之间的留白（px） */
    private final int mArrowGap;
    /** 箭头图标边长（px，按字号取，缩字号时同比收） */
    private final int mArrowSize;
    /** 拖动分割带滚列表的手抖容差（px），与 RecyclerView 自己的滑动阈值同源 */
    private final int mTouchSlop;
    /** 收起态与展开态的箭头（分割线文本末尾的小图标） */
    private final Drawable mArrowUp;
    private final Drawable mArrowDown;
    /** 画与命中测试都要用条目的“含分割线外边距”外框，复用同一个 Rect 避免每帧分配 */
    private final Rect mBounds = new Rect();
    /**
     * “全部分段都被收起”时那批分割带自己的滚动量（px）：此时列表里没有任何条目，
     * RecyclerView 没有可滚的内容（段数多的排序档一屏装不下所有线），靠本偏移把下面的线拖上来
     */
    private int mStackScroll;
    /** 最近一次命中的分割带顶边（屏幕坐标），折叠完拿它当锚点 */
    private int mLastHitTop;
    /** 本次锚点：被点那条线的段名与它在屏幕上的顶边，以及还允许校对几次 */
    private String mAnchorKey;
    private int mAnchorTop;
    private int mAnchorTries;
    /** 一次性布局回调：布局算完、还没绘制时把锚点线推回原位 */
    private ViewTreeObserver.OnGlobalLayoutListener mAnchorListener;
    private final SectionTitleProvider mProvider;

    public SectionDividerDecoration(Context context, SectionTitleProvider provider) {
        mProvider = provider;
        mBandHeight = dp(context, 26);
        mTextGap = dp(context, 10);
        mArrowGap = dp(context, 5);
        mArrowSize = dp(context, 14);
        mTouchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        mLinePaint.setColor(0x33FFFFFF);
        mLinePaint.setStrokeWidth(Math.max(1f, dp(context, 1) * 0.5f));
        mTextPaint.setColor(0xAAFFFFFF);
        mTextPaint.setTextSize(sp(context, 10));
        mTextPaint.setTextAlign(Paint.Align.LEFT);
        mArrowUp = loadArrow(context, R.drawable.baseline_keyboard_arrow_up_24);
        mArrowDown = loadArrow(context, R.drawable.baseline_keyboard_arrow_down_24);
    }

    /** 箭头图标与本类文字同色（含半透明），避免矢量图自带的纯白 tint 比分割线文本亮一档 */
    private static Drawable loadArrow(Context context, int resId) {
        Drawable drawable = ContextCompat.getDrawable(context, resId);
        if (drawable != null) {
            drawable = drawable.mutate();
            drawable.setColorFilter(0xAAFFFFFF, PorterDuff.Mode.SRC_IN);
        }
        return drawable;
    }

    /**
     * 接上分割带的点击手势：页面在 {@code addItemDecoration} 之后调一次。
     * <p>
     * 本项目的 RecyclerView 产物里 {@code ItemDecoration} 没有 onAttachedToRecyclerView /
     * onDetachedFromRecyclerView 这两个回调，拿不到附着时机，只能由页面显式接进来；
     * 先摘后接是为了同一个装饰器被重复挂上时不会一次点击翻两次折叠态。
     */
    public void attachTo(RecyclerView recyclerView) {
        if (recyclerView == null) {
            return;
        }
        recyclerView.removeOnItemTouchListener(mBandTouch);
        recyclerView.addOnItemTouchListener(mBandTouch);
        // 上一次折叠的锚点回调没走到位（列表被重建、页面切走等）时当场清掉，免得误推一次
        clearAnchor(recyclerView);
        // 折叠/展开靠 ItemAnimator 的增删动画，长度按“快速过渡”取 180ms；
        // 位置挪动更短，change 动画关掉（整列白闪一下比直接到位更硬）
        RecyclerView.ItemAnimator animator = recyclerView.getItemAnimator();
        if (animator != null) {
            animator.setAddDuration(COLLAPSE_ANIM_MS);
            animator.setRemoveDuration(COLLAPSE_ANIM_MS);
            animator.setMoveDuration(COLLAPSE_MOVE_MS);
            animator.setChangeDuration(0L);
            if (animator instanceof DefaultItemAnimator) {
                ((DefaultItemAnimator) animator).setSupportsChangeAnimations(false);
            }
        }
    }

    /** 折叠/展开的增删动画时长（毫秒） */
    private static final long COLLAPSE_ANIM_MS = 180L;
    /** 因折叠而上下挪位的条目动画时长 */
    private static final long COLLAPSE_MOVE_MS = 120L;

    @Override
    public void getItemOffsets(@NonNull Rect outRect, @NonNull View view,
                               @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
        if (mProvider == null) {
            return;
        }
        int position = parent.getChildAdapterPosition(view);
        if (position == RecyclerView.NO_POSITION) {
            return;
        }
        List<SectionBand> top = mProvider.getTopBands(position);
        // 宫格首行里的每个格子都要留出同样的高度（整行的顶偏移一致才不会错位）；
        // 列表模式列数为 1，只有段首那条符合条件
        if (top != null && !top.isEmpty() && inSectionHeadRow(position, parent)) {
            outRect.top += mBandHeight * top.size();
        }
        List<SectionBand> bottom = mProvider.getBottomBands(position);
        if (bottom != null && !bottom.isEmpty()) {
            outRect.bottom += mBandHeight * bottom.size();
        }
    }

    @Override
    public void onDraw(@NonNull Canvas canvas, @NonNull RecyclerView parent,
                       @NonNull RecyclerView.State state) {
        if (mProvider == null) {
            return;
        }
        List<SectionBand> stack = emptyStack();
        if (stack != null) {
            // 全部分段都被收起：带子从列表顶部按 mStackScroll 往上叠着画，拖得动也点得开
            clampStackScroll(parent, stack.size());
            drawBands(canvas, parent, parent.getPaddingTop() - mStackScroll, stack);
            return;
        }
        mStackScroll = 0;
        final int childCount = parent.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = parent.getChildAt(i);
            int position = parent.getChildAdapterPosition(child);
            if (position == RecyclerView.NO_POSITION) {
                continue;
            }
            // 带子就占在本条目的顶/底偏移区里：外框（含条目自身外边距）才是可靠的参照，
            // 条目自己的 getTop/getBottom 在不同版本里含不含偏移并不一致
            parent.getDecoratedBoundsWithMargins(child, mBounds);
            // 上方的分割带只由段首那条画一次（宽度跨整行），同行其他格子只留高度
            if (position == mProvider.getSectionHead(position)) {
                List<SectionBand> top = mProvider.getTopBands(position);
                if (top != null && !top.isEmpty()) {
                    drawBands(canvas, parent, mBounds.top, top);
                }
            }
            List<SectionBand> bottom = mProvider.getBottomBands(position);
            if (bottom != null && !bottom.isEmpty()) {
                drawBands(canvas, parent, mBounds.bottom - mBandHeight * bottom.size(), bottom);
            }
        }
    }

    /** 全部分段都被收起时要画的分割带堆；只要还有可见条目就返回 null */
    @Nullable
    private List<SectionBand> emptyStack() {
        if (mProvider == null) {
            return null;
        }
        List<SectionBand> stack = mProvider.getEmptyBands();
        return stack == null || stack.isEmpty() ? null : stack;
    }

    /** 把带子堆的滚动量夹在可用区间里（带子总量超过列表可见高时才能往下滚） */
    private void clampStackScroll(RecyclerView parent, int bandCount) {
        int viewport = parent.getHeight() - parent.getPaddingTop() - parent.getPaddingBottom();
        int max = Math.max(0, bandCount * mBandHeight - Math.max(0, viewport));
        if (mStackScroll > max) {
            mStackScroll = max;
        }
        if (mStackScroll < 0) {
            mStackScroll = 0;
        }
    }

    /** 从 {@code topY} 起自上而下画出这一批分割带，每条带子各占 {@link #mBandHeight} */
    private void drawBands(Canvas canvas, RecyclerView parent, float topY, List<SectionBand> bands) {
        final float left = parent.getPaddingLeft();
        final float right = parent.getWidth() - parent.getPaddingRight();
        for (int i = 0; i < bands.size(); i++) {
            drawBand(canvas, bands.get(i), left, right, topY + i * mBandHeight);
        }
    }

    /** 画一条分割带：横线在中间断开，中间是段名文本，文本末尾跟一个折叠/展开箭头 */
    private void drawBand(Canvas canvas, SectionBand band, float left, float right, float bandTop) {
        final float centerY = bandTop + mBandHeight / 2f;
        final float baseTextSize = mTextPaint.getTextSize();
        float textWidth = mTextPaint.measureText(band.text);
        float arrowSize = mArrowSize;
        float total = textWidth + mArrowGap + arrowSize;
        // 带上张数且本地化后可能很长：放不下就把字号与箭头一起按比例缩小，画完再复原
        float maxTotal = (right - left) - mTextGap * 2;
        if (maxTotal > 0 && total > maxTotal) {
            float scale = maxTotal / total;
            mTextPaint.setTextSize(baseTextSize * scale);
            textWidth *= scale;
            arrowSize *= scale;
            total = textWidth + mArrowGap + arrowSize;
        }
        final float startX = left + (right - left - total) / 2f;
        Paint.FontMetrics fm = mTextPaint.getFontMetrics();
        final float baseline = centerY - (fm.ascent + fm.descent) / 2f;
        // 横线在文字与箭头两侧断开，形成“—— 6星 · 25张 ▲ ——”的观感
        float lineLeftEnd = startX - mTextGap;
        float lineRightStart = startX + total + mTextGap;
        if (lineLeftEnd > left) {
            canvas.drawLine(left, centerY, lineLeftEnd, centerY, mLinePaint);
        }
        if (lineRightStart < right) {
            canvas.drawLine(lineRightStart, centerY, right, centerY, mLinePaint);
        }
        canvas.drawText(band.text, startX, baseline, mTextPaint);
        Drawable arrow = band.collapsed ? mArrowUp : mArrowDown;
        if (arrow != null) {
            int size = Math.round(arrowSize);
            int arrowTop = Math.round(centerY - size / 2f);
            arrow.setBounds(Math.round(startX + textWidth + mArrowGap), arrowTop,
                    Math.round(startX + textWidth + mArrowGap + size), arrowTop + size);
            arrow.draw(canvas);
        }
        // 本轮可能被缩小过字号，复原回构造时的基准，下一条分割带与下一帧都不受影响
        mTextPaint.setTextSize(baseTextSize);
    }

    /** 该位置是否处在自己那段的“首行”里（宫格一行可能有好几张卡） */
    private boolean inSectionHeadRow(int position, RecyclerView parent) {
        int head = mProvider.getSectionHead(position);
        if (head < 0 || position < head) {
            return false;
        }
        return position - head < spanCount(parent);
    }

    /**
     * 分割带的手势：按下落在某条带子上就把整段手势接过来（否则会被当成点了段首那张卡），
     * 但接过来不等于不能滚动：手指移动超过手抖容差后按位移量推列表上下滚（全收起态没条目可滚，
     * 改为推本类的带子堆偏移），否则整段收起时屏幕上方就只剩这几条带子可摸，
     * 列表会看起来“拖不动”而且下方的线再也展不开（用户报的问题）；
     * 没超过容差且抬手仍在同一条带子上才算一次折叠/展开点击
     */
    private final RecyclerView.OnItemTouchListener mBandTouch = new RecyclerView.OnItemTouchListener() {
        private SectionBand mPressed;
        private float mDownY;
        private float mLastY;
        private boolean mDragging;
        /** 本次手势按在“全收起”的带子堆上：滚动靠 mStackScroll，不能走 rv.scrollBy */
        private boolean mStackGesture;

        @Override
        public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
            if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                mPressed = hitBand(rv, e.getX(), e.getY());
                mStackGesture = mPressed != null && emptyStack() != null;
                mDownY = mLastY = e.getY();
                mDragging = false;
                return mPressed != null;
            }
            return false;
        }

        @Override
        public void onTouchEvent(@NonNull RecyclerView rv, @NonNull MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_MOVE: {
                    if (mPressed == null) {
                        break;
                    }
                    float y = e.getY();
                    if (!mDragging) {
                        if (Math.abs(y - mDownY) <= mTouchSlop) {
                            break;
                        }
                        // 进入拖动：不补上刚开始那一段位移，免得列表突然跳一下
                        mDragging = true;
                        rv.stopScroll();
                        mLastY = y;
                        break;
                    }
                    int delta = Math.round(mLastY - y);
                    mLastY = y;
                    if (mStackGesture) {
                        List<SectionBand> stack = emptyStack();
                        if (stack == null) {
                            break;
                        }
                        mStackScroll += delta;
                        clampStackScroll(rv, stack.size());
                        // 没有条目可滚，只能自己重画带子堆（onDraw 会重新取 mStackScroll）
                        rv.invalidate();
                        break;
                    }
                    rv.scrollBy(0, delta);
                    break;
                }
                case MotionEvent.ACTION_UP: {
                    SectionBand pressed = mPressed;
                    boolean dragging = mDragging;
                    mPressed = null;
                    mDragging = false;
                    mStackGesture = false;
                    if (pressed == null || dragging || mProvider == null) {
                        break;
                    }
                    if (pressed == hitBand(rv, e.getX(), e.getY())) {
                        final String key = pressed.key;
                        final int anchorTop = mLastHitTop;
                        // 数据变更推迟到本轮触摸派发之外：touch 事件可能正好落在算布局/滚动的保护区间里，
                        // 当场刷新会撞上“computing layout / scrolling”限制
                        rv.post(() -> {
                            mProvider.toggleSection(key);
                            // 变更完了把刚点的那条线钉回原来的屏幕位置
                            anchorBandTo(rv, key, anchorTop);
                        });
                    }
                    break;
                }
                case MotionEvent.ACTION_CANCEL:
                    mPressed = null;
                    mDragging = false;
                    mStackGesture = false;
                    break;
                default:
                    break;
            }
        }

        @Override
        public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        }
    };

    /** 命中测试：返回坐标落在哪条分割带上，不在任何带子上返回 null */
    @Nullable
    private SectionBand hitBand(RecyclerView rv, float x, float y) {
        if (mProvider == null) {
            return null;
        }
        // 分割线只跨列表内容宽（左右内边距以外），落在列表外就不算点带子
        if (x < rv.getPaddingLeft() || x > rv.getWidth() - rv.getPaddingRight()) {
            return null;
        }
        final int count = rv.getChildCount();
        List<SectionBand> stack = emptyStack();
        if (stack != null) {
            // 全收起态：命中区间就是带子堆（按 mStackScroll 上移后）本身
            return bandAt(stack, rv.getPaddingTop() - mStackScroll, y);
        }
        if (count == 0) {
            return null;
        }
        // 不靠 findChildViewUnder：分割线在条目的偏移区里，那个区间未必算在条目自己的帧内，
        // 逐条子 View 用含偏移的外框比反而与 onDraw 的口径完全一致
        for (int i = 0; i < count; i++) {
            View child = rv.getChildAt(i);
            int position = rv.getChildAdapterPosition(child);
            if (position == RecyclerView.NO_POSITION) {
                continue;
            }
            rv.getDecoratedBoundsWithMargins(child, mBounds);
            List<SectionBand> top = mProvider.getTopBands(position);
            if (top != null && !top.isEmpty() && inSectionHeadRow(position, rv)) {
                SectionBand hit = bandAt(top, mBounds.top, y);
                if (hit != null) {
                    return hit;
                }
            }
            List<SectionBand> bottom = mProvider.getBottomBands(position);
            if (bottom != null && !bottom.isEmpty()) {
                SectionBand hit = bandAt(bottom, mBounds.bottom - mBandHeight * bottom.size(), y);
                if (hit != null) {
                    return hit;
                }
            }
        }
        return null;
    }

    /** 从 {@code topY} 起的一批分割带里取纵坐标命中的那条；没落在带子里返回 null */
    @Nullable
    private SectionBand bandAt(List<SectionBand> bands, float topY, float y) {
        if (y < topY || y >= topY + bands.size() * mBandHeight) {
            return null;
        }
        int index = (int) ((y - topY) / mBandHeight);
        if (index < 0 || index >= bands.size()) {
            return null;
        }
        // 记下这条带的顶边：折叠/展开完要把它钉回这个屏幕位置
        mLastHitTop = Math.round(topY + index * mBandHeight);
        return bands.get(index);
    }

    /**
     * 把刚点的那条分割线钉回原来的屏幕位置（用户规格：折叠/展开后线不能跳、列表不能闪回顶部）。
     * <p>
     * 变更后这条线的布局位置必然变（收起时它下面少了几十行，展开时它从“叠在别段头上”变成本段段首），
     * 而 RecyclerView 自己的锚点只认段首条目，看上去就是整列抖动或跳到顶部。这里在本次布局算完、
     * 绘制之前按像素差平移内容，同帧完成，不会先画一帧错位的样子。
     * <p>
     * 平移会连带重算 ItemDecoration 偏移并再走一次布局，所以允许最多校对 {@link #ANCHOR_MAX_TRIES}
     * 次，直到量出来就是锚点位置才收掉回调（对不上也不能无限循环）。
     */
    private void anchorBandTo(final RecyclerView rv, final String key, final int anchorTop) {
        clearAnchor(rv);
        if (key == null || anchorTop == Integer.MIN_VALUE) {
            return;
        }
        final ViewTreeObserver observer = rv.getViewTreeObserver();
        if (!observer.isAlive()) {
            return;
        }
        mAnchorKey = key;
        mAnchorTop = anchorTop;
        mAnchorTries = ANCHOR_MAX_TRIES;
        final ViewTreeObserver.OnGlobalLayoutListener[] self =
                new ViewTreeObserver.OnGlobalLayoutListener[1];
        self[0] = new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                if (mAnchorTries <= 0 || !key.equals(mAnchorKey) || anchorTop != mAnchorTop) {
                    clearAnchor(rv);
                    return;
                }
                mAnchorTries--;
                if (shiftBandTo(rv, key, anchorTop)) {
                    clearAnchor(rv);
                }
            }
        };
        mAnchorListener = self[0];
        observer.addOnGlobalLayoutListener(self[0]);
    }

    /** 锚点线最多校对次数 */
    private static final int ANCHOR_MAX_TRIES = 3;
    /** 误差在一像素内就当已经对齐 */
    private static final int ANCHOR_TOLERANCE = 1;

    /**
     * 按像素差把锚点线推到 anchorTop
     *
     * @return 已经对齐（或干脆找不到这条线）时返回 true，可以收掉布局回调
     */
    private boolean shiftBandTo(RecyclerView rv, String key, int anchorTop) {
        List<SectionBand> stack = emptyStack();
        if (stack != null) {
            // 全收起态：线的位置由 mStackScroll 决定，直接改偏移
            int index = indexOfBand(stack, key);
            if (index < 0) {
                return true;
            }
            mStackScroll = Math.round(rv.getPaddingTop() + index * mBandHeight - anchorTop);
            clampStackScroll(rv, stack.size());
            rv.invalidate();
            return Math.abs(rv.getPaddingTop() - mStackScroll + index * mBandHeight - anchorTop)
                    <= ANCHOR_TOLERANCE;
        }
        int currentTop = findBandTop(rv, key);
        if (currentTop == Integer.MIN_VALUE) {
            return true;
        }
        int delta = currentTop - anchorTop;
        if (Math.abs(delta) <= ANCHOR_TOLERANCE) {
            return true;
        }
        if (rv.isComputingLayout()) {
            // 这一帧还在算布局，不能平移（会抛 computing layout），留给下一次布局回调
            return false;
        }
        // 本回调在 performLayout 之后、performDraw 之前，这次平移不会先画一帧错位的样子
        rv.scrollBy(0, delta);
        int settledTop = findBandTop(rv, key);
        return settledTop == Integer.MIN_VALUE
                || Math.abs(settledTop - anchorTop) <= ANCHOR_TOLERANCE;
    }

    /** 该段分割线当前的屏幕顶边（与 onDraw 同一套几何）；找不到返回 Integer.MIN_VALUE */
    private int findBandTop(RecyclerView rv, String key) {
        List<SectionBand> stack = emptyStack();
        if (stack != null) {
            int index = indexOfBand(stack, key);
            return index < 0 ? Integer.MIN_VALUE
                    : Math.round(rv.getPaddingTop() - mStackScroll + index * mBandHeight);
        }
        final int count = rv.getChildCount();
        for (int i = 0; i < count; i++) {
            View child = rv.getChildAt(i);
            int position = rv.getChildAdapterPosition(child);
            if (position == RecyclerView.NO_POSITION) {
                continue;
            }
            rv.getDecoratedBoundsWithMargins(child, mBounds);
            List<SectionBand> top = mProvider.getTopBands(position);
            if (top != null && !top.isEmpty() && position == mProvider.getSectionHead(position)) {
                int index = indexOfBand(top, key);
                if (index >= 0) {
                    return mBounds.top + index * mBandHeight;
                }
            }
            List<SectionBand> bottom = mProvider.getBottomBands(position);
            if (bottom != null && !bottom.isEmpty()) {
                int index = indexOfBand(bottom, key);
                if (index >= 0) {
                    return mBounds.bottom - (bottom.size() - index) * mBandHeight;
                }
            }
        }
        return Integer.MIN_VALUE;
    }

    private static int indexOfBand(List<SectionBand> bands, String key) {
        for (int i = 0; i < bands.size(); i++) {
            if (bands.get(i).key.equals(key)) {
                return i;
            }
        }
        return -1;
    }

    /** 撤掉还没走到的锚点回调（连续点击、列表重建、页面切走时都会用到） */
    private void clearAnchor(RecyclerView rv) {
        mAnchorKey = null;
        mAnchorTries = 0;
        if (mAnchorListener == null || rv == null) {
            return;
        }
        ViewTreeObserver observer = rv.getViewTreeObserver();
        if (observer.isAlive()) {
            observer.removeOnGlobalLayoutListener(mAnchorListener);
        }
        mAnchorListener = null;
    }

    /** 当前布局一行有几列：宫格取列数，列表（线性）当作 1 列 */
    private static int spanCount(RecyclerView parent) {
        RecyclerView.LayoutManager lm = parent.getLayoutManager();
        if (lm instanceof GridLayoutManager) {
            return Math.max(1, ((GridLayoutManager) lm).getSpanCount());
        }
        return 1;
    }

    private static int dp(Context context, float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                context.getResources().getDisplayMetrics()));
    }

    private static float sp(Context context, float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value,
                context.getResources().getDisplayMetrics());
    }
}
