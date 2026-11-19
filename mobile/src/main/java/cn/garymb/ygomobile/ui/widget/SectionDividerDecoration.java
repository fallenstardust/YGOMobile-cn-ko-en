package cn.garymb.ygomobile.ui.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.TypedValue;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

/**
 * 搜索结果列表 / 宫格的分段说明：每段第一行上方留出一条说明带，带子中央画一条浅色横线，
 * 横线在中间断开写该段说明（星数「13星」、攻守「5000~4501」、名称「A」、时间「2024年」）。
 *
 * <p>不插入任何数据，只在绘制层面加偏移与说明，所以条目位置、点击/拖拽位置都不受影响。</p>
 *
 * <p>列表模式下“一行”就是一张卡；宫格模式下适配器会把每段最后一张卡撑满本行，
 * 于是每个分段都从新的一行开始，说明带加在整行上方（该行每张卡都加同样的顶偏移，行内不会错位），
 * 也不会把下一段的卡塞进上一段的末行。</p>
 */
public class SectionDividerDecoration extends RecyclerView.ItemDecoration {

    /** 段位提供者：返回该位置所属分段的说明；返回 null 表示该排序档不分段 */
    public interface SectionTitleProvider {
        String getSectionTitle(int position);
    }

    private final Paint mLinePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    /** 说明带高度（px） */
    private final int mBandHeight;
    /** 说明文字两侧与分割线之间的留白（px） */
    private final int mTextGap;
    private final SectionTitleProvider mProvider;

    public SectionDividerDecoration(Context context, SectionTitleProvider provider) {
        mProvider = provider;
        mBandHeight = dp(context, 26);
        mTextGap = dp(context, 10);
        mLinePaint.setColor(0x33FFFFFF);
        mLinePaint.setStrokeWidth(Math.max(1f, dp(context, 1) * 0.5f));
        mTextPaint.setColor(0xAAFFFFFF);
        mTextPaint.setTextSize(sp(context, 10));
        mTextPaint.setTextAlign(Paint.Align.LEFT);
    }

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
        if (sectionRowStart(position, spanCount(parent)) >= 0) {
            outRect.top += mBandHeight;
        }
    }

    @Override
    public void onDraw(@NonNull Canvas canvas, @NonNull RecyclerView parent,
                       @NonNull RecyclerView.State state) {
        if (mProvider == null) {
            return;
        }
        final int span = spanCount(parent);
        final float left = parent.getPaddingLeft();
        final float right = parent.getWidth() - parent.getPaddingRight();
        final int childCount = parent.getChildCount();
        for (int i = 0; i < childCount; i++) {
            View child = parent.getChildAt(i);
            int position = parent.getChildAdapterPosition(child);
            if (position == RecyclerView.NO_POSITION) {
                continue;
            }
            // 只在分段首行的第一个位置画一次，宽度跨整行
            if (sectionRowStart(position, span) != position) {
                continue;
            }
            String title = mProvider.getSectionTitle(position);
            if (title == null) {
                continue;
            }
            // 说明带就是该行上方的偏移区，横线与文字都居中
            final float centerY = child.getTop() - mBandHeight / 2f;
            final float baseTextSize = mTextPaint.getTextSize();
            float textWidth = mTextPaint.measureText(title);
            // 带上张数且本地化后可能很长：放不下就按比例缩小字号，画完再复原
            float maxTextWidth = (right - left) - mTextGap * 2;
            if (maxTextWidth > 0 && textWidth > maxTextWidth) {
                mTextPaint.setTextSize(baseTextSize * maxTextWidth / textWidth);
                textWidth = maxTextWidth;
            }
            final float textX = left + (right - left - textWidth) / 2f;
            Paint.FontMetrics fm = mTextPaint.getFontMetrics();
            final float baseline = centerY - (fm.ascent + fm.descent) / 2f;
            // 横线在文字两侧断开，形成“—— 13星 · 25张 ——”的观感
            float lineLeftEnd = textX - mTextGap;
            float lineRightStart = textX + textWidth + mTextGap;
            if (lineLeftEnd > left) {
                canvas.drawLine(left, centerY, lineLeftEnd, centerY, mLinePaint);
            }
            if (lineRightStart < right) {
                canvas.drawLine(lineRightStart, centerY, right, centerY, mLinePaint);
            }
            canvas.drawText(title, textX, baseline, mTextPaint);
            mTextPaint.setTextSize(baseTextSize);
        }
    }

    /**
     * 该位置所在的行是否是一个分段的首行：是则返回该段首个位置，否则返回 -1。
     * <p>
     * 适配器给每段末张卡加了跨度把本行占满，所以每个分段都从新的一行开始；
     * 由此一行里最多只有 span 个位置，往前找 span 个就能确定本行从哪里起，
     * 再看那个起点是不是段首（段名与上一张不同）
     */
    private int sectionRowStart(int position, int span) {
        for (int back = 0; back < span; back++) {
            int candidate = position - back;
            if (candidate < 0) {
                break;
            }
            String title = mProvider.getSectionTitle(candidate);
            if (title == null) {
                continue;
            }
            String previous = candidate > 0 ? mProvider.getSectionTitle(candidate - 1) : null;
            if (title.equals(previous)) {
                continue; // 不是段首
            }
            // 本行长度：从段首往后最远到 span 张，遇到别的段就停
            int run = 1;
            while (run < span) {
                String next = mProvider.getSectionTitle(candidate + run);
                if (next == null || !next.equals(title)) {
                    break;
                }
                run++;
            }
            if (position < candidate + run) {
                return candidate;
            }
        }
        return -1;
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
