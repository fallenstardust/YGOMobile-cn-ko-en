package cn.garymb.ygomobile.ui.widget;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ScrollView;

import java.util.List;
import java.util.Map;
import java.util.Set;

import cn.garymb.ygomobile.lite.R;

/**
 * 发售日期日历的浮动页面：点时间轴滑块上的日期弹出，只有卡包里真实存在的发售日可选。
 * 打开时带上时间轴当前区间，区间内铺连续高亮条；点一下先改一端、再点一下定下另一端，
 * 同一个日历就能把左右两个拨块的日期范围一起选好。
 */
public class ReleaseDateCalendarDialog extends Dialog {

    public interface OnDateRangeChangedListener {
        /** 区间变化；finished = 两端都已点选（第二次点选），此时日历已关闭 */
        void onDateRangeChanged(int startDate, int endDate, boolean finished);
    }

    /**
     * 弹出发售日期日历
     *
     * @param availableDates 可选日期（yyyyMMdd，即时间轴的滑动节点）
     * @param packNames      日期 → 当天发售的卡包名，显示在日期下方的小字
     * @param startDate      时间轴当前的开始日（区间内铺连续高亮条）
     * @param endDate        时间轴当前的结束日
     * @param activeStart    true = 先改开始端（点的是左拨块的气泡），否则先改结束端
     */
    public static void show(Context context, Set<Integer> availableDates,
                            Map<Integer, List<String>> packNames,
                            int startDate, int endDate, boolean activeStart,
                            OnDateRangeChangedListener listener) {
        if (context == null || availableDates == null || availableDates.isEmpty()) {
            return;
        }
        new ReleaseDateCalendarDialog(context, availableDates, packNames,
                startDate, endDate, activeStart, listener).show();
    }

    private ReleaseDateCalendarDialog(Context context, Set<Integer> availableDates,
                                      Map<Integer, List<String>> packNames,
                                      int startDate, int endDate, boolean activeStart,
                                      OnDateRangeChangedListener listener) {
        super(context);
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        ReleaseDateCalendarView calendar = new ReleaseDateCalendarView(context);
        calendar.setAvailableDates(availableDates);
        calendar.setPackNamesByDate(packNames);
        calendar.setActiveStart(activeStart);
        calendar.setDateRange(startDate, endDate);
        calendar.setOnDateRangeChangedListener((start, end, finished) -> {
            if (listener != null) {
                listener.onDateRangeChanged(start, end, finished);
            }
            if (finished) {
                dismiss();
            }
        });

        int padding = dp(context, 12);
        FrameLayout panel = new FrameLayout(context);
        panel.setPadding(padding, padding, padding, padding);
        panel.setBackgroundResource(R.drawable.radius_p);
        // 日历行数与格子高度都固定，翻页时弹窗高度不变；
        // 外壳仍用 ScrollView：屏幕够高时紧贴内容，放不下时日历自己滚
        int widthPx = dp(context, 300);
        ScrollView scroller = new ScrollView(context);
        scroller.addView(calendar, new FrameLayout.LayoutParams(widthPx,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        panel.addView(scroller, new FrameLayout.LayoutParams(widthPx,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        final int maxHeight = (int) (context.getResources().getDisplayMetrics().heightPixels * 0.86f);
        calendar.addOnLayoutChangeListener((v, left, top, right, bottom,
                                            oldLeft, oldTop, oldRight, oldBottom) -> {
            int target = Math.min(bottom - top, maxHeight);
            ViewGroup.LayoutParams params = scroller.getLayoutParams();
            if (params != null && params.height != target) {
                params.height = target;
                scroller.setLayoutParams(params);
            }
        });
        setContentView(panel);
        setCanceledOnTouchOutside(true);

        Window window = getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams params = window.getAttributes();
            params.dimAmount = 0.45f;
            params.width = WindowManager.LayoutParams.WRAP_CONTENT;
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            params.gravity = Gravity.CENTER;
            window.setAttributes(params);
        }
    }

    private static int dp(Context context, float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                context.getResources().getDisplayMetrics()));
    }
}
