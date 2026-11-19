package cn.garymb.ygomobile.ui.cards;

import android.view.View;
import android.widget.TextView;

import java.util.HashSet;
import java.util.Set;
import java.util.function.IntSupplier;

import cn.garymb.ygomobile.ui.adapters.CardListAdapter;
import cn.garymb.ygomobile.ui.widget.DateRangeSlider;
import cn.garymb.ygomobile.ui.widget.ReleaseDateCalendarDialog;
import ocgcore.DataManager;
import ocgcore.PackManager;

/**
 * 搜索结果顶部发售日期时间轴的接线（卡查页与卡组管理页共用一份）。
 *
 * <p>职责有三件：把卡包管理器里全部卡包的发售日期当作滑动节点喂给时间轴（滑块每动一步
 * 就是从一个卡包日期跳到下一个卡包日期，不再逐日增减）；时间轴一动就把两端日期写进
 * {@link CardListAdapter} 的过滤条件并重排当前结果；顶端计数按过滤后的条数刷新。</p>
 *
 * <p>两端都拉到首尾节点时视为“不做日期过滤”（{@link CardListAdapter#clearReleaseDateFilter()}），
 * 这样不在任何卡包 ydk 里的卡（没有发售日期可比）在未收紧范围时仍然照常显示。</p>
 *
 * <p>点时间轴滑块上的日期气泡还会弹出售卖日期日历：可选日就是同一条节点表（卡包里真实存在的
 * 发售日），选完把那一端滑块直接移过去。</p>
 */
public class CardReleaseDateFilter implements DateRangeSlider.OnRangeChangedListener {

    /** 卡包数据还没读完时的自查间隔与次数上限（读完就不再轮询） */
    private static final long RANGE_RETRY_DELAY = 500L;
    private static final int RANGE_RETRY_MAX = 30;

    private final PackManager mPackManager;
    private final DateRangeSlider mSlider;
    private final CardListAdapter mAdapter;
    private final TextView mCountView;
    /** 页面当前的排序档位（0=星数↑ … 3=名称↓），过滤后按同一档位重排 */
    private final IntSupplier mSortRule;
    /** 过滤重排完成后的收尾（刷新列表布局等），由页面提供 */
    private final Runnable mOnApplied;
    private final Runnable mRetryRange = this::refreshRange;
    private int mRetryCount;

    public CardReleaseDateFilter(DateRangeSlider slider, CardListAdapter adapter, TextView countView,
                                 IntSupplier sortRule, Runnable onApplied) {
        mSlider = slider;
        mAdapter = adapter;
        mCountView = countView;
        mSortRule = sortRule;
        mOnApplied = onApplied;
        mPackManager = DataManager.get().getPackManager();
        if (mSlider != null) {
            mSlider.setOnRangeChangedListener(this);
            mSlider.setOnDateLabelClickListener(this::openReleaseDateCalendar);
        }
    }

    /**
     * 点时间轴气泡：弹出售卖日期日历。可选日就是时间轴的滑动节点（卡包里真实存在的发售日），
     * 不可选的日期在日历里置灰；日历带上当前区间，点一下先改这一端、再点一下定下另一端，
     * 每次变化都把区间写回滑块（只点一次也能只改一端）
     */
    private void openReleaseDateCalendar(int thumb, int date) {
        if (mSlider == null) {
            return;
        }
        int[] nodes = mPackManager.getReleaseDateNodes();
        if (nodes.length == 0) {
            return;
        }
        Set<Integer> available = new HashSet<>(nodes.length * 2);
        for (int node : nodes) {
            available.add(node);
        }
        // 日期下方的卡包名小字（同一天多个包会分多行列）
        ReleaseDateCalendarDialog.show(mSlider.getContext(), available,
                mPackManager.getPackNamesByDate(),
                mSlider.getStartDate(), mSlider.getEndDate(),
                thumb == DateRangeSlider.THUMB_START,
                (start, end, finished) -> mSlider.setDateRange(start, end));
    }

    /**
     * 按卡包管理器当前的发售日期节点复核一次时间轴。卡包数据是启动后异步读完的，
     * 可能比页面晚到，所以每次拿到新结果、每次抽屉打开都该调一次；
     * 还一个节点都没读到时先把时间轴藏起来（不显示就不能设过滤）
     */
    public void refreshRange() {
        if (mSlider == null) {
            return;
        }
        int[] nodes = mPackManager.getReleaseDateNodes();
        if (nodes.length == 0) {
            mSlider.setVisibility(View.GONE);
            retryLater();
            return;
        }
        mRetryCount = 0;
        mSlider.removeCallbacks(mRetryRange);
        // 节点表变了（卡包数据重新载入）时滑块会被重新夹回新节点：过滤条件要跟着收紧，
        // 否则时间轴显示的区间与结果列表的区间会对不上
        boolean hadRange = mSlider.hasRange();
        int prevStart = mSlider.getStartDate();
        int prevEnd = mSlider.getEndDate();
        mSlider.setReleaseNodes(nodes);
        mSlider.setVisibility(View.VISIBLE);
        if (hadRange && (prevStart != mSlider.getStartDate() || prevEnd != mSlider.getEndDate())) {
            applyRangeToAdapter();
        }
    }

    /**
     * 卡包数据是启动后异步读完的，页面先建好时先自己定时回看一眼；
     * 次数封顶，不把“确实没有任何卡包”这种情形变成永久轮询
     */
    private void retryLater() {
        if (mRetryCount >= RANGE_RETRY_MAX) {
            return;
        }
        mRetryCount++;
        mSlider.removeCallbacks(mRetryRange);
        mSlider.postDelayed(mRetryRange, RANGE_RETRY_DELAY);
    }

    /** 新一批结果已按当前范围填进适配器：计数要跟着换成过滤后的条数 */
    public void updateResultCount() {
        if (mCountView == null || mAdapter == null) {
            return;
        }
        mCountView.setText(String.valueOf(mAdapter.getItems().size()));
    }

    @Override
    public void onRangeChanged(int startDate, int endDate) {
        applyRangeToAdapter();
    }

    /**
     * 把时间轴当前两端的日期写成适配器的过滤条件并重排当前结果；
     * 两端都在首尾节点时清掉过滤（全范围不拿日期挑卡）
     */
    private void applyRangeToAdapter() {
        if (mAdapter == null || mSlider == null) {
            return;
        }
        if (mSlider.isFullRange()) {
            mAdapter.clearReleaseDateFilter();
        } else {
            mAdapter.setReleaseDateFilter(mSlider.getStartDate(), mSlider.getEndDate());
        }
        mAdapter.applySortRule(mSortRule.getAsInt());
        updateResultCount();
        if (mOnApplied != null) {
            mOnApplied.run();
        }
    }
}
