package cn.garymb.ygomobile.ui.widget;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ScrollView;

import java.util.List;
import java.util.Map;
import java.util.Set;

import cn.garymb.ygomobile.lite.R;

/**
 * 发售日期日历的浮动页面：点时间轴滑块上的日期弹出，只有卡包里真实存在的发售日可选。
 * 打开时带上时间轴当前区间，区间内铺连续高亮条；点一下先改一端、再点一下定下另一端，
 * 同一个日历就能把左右两个拨块的日期范围一起选好。
 * 日历顶部两个日期牌加一行指引文字会写明这一次点选定的是开始还是结束日期。
 *
 * <p>两端都选完不会自动收页，留着日历接着微调；收页靠右上角的圆圈关闭按钮，
 * 点浮窗外与返回键同样能关。</p>
 */
public class ReleaseDateCalendarDialog extends Dialog {

    public interface OnDateRangeChangedListener {
        /** 区间变化；finished = 两端都已点选（第二次点选），日历不关，调用方自行决定是否还要做什么 */
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
            // 两端都选完也不收页：留着日历让用户继续调，关掉交给右上角的圆圈按钮
        });

        int padding = dp(context, 12);
        // 顶部空出一档给关闭按钮，免得压在翻年的 » 上
        int barHeight = dp(context, 26);
        FrameLayout panel = new FrameLayout(context);
        panel.setPadding(padding, padding, padding, padding);
        panel.setBackgroundResource(R.drawable.button_n);
        // 日历行数与格子高度都固定，翻页时弹窗高度不变；
        // 外壳仍用 ScrollView：屏幕够高时紧贴内容，放不下时日历自己滚
        int widthPx = dp(context, 300);
        ScrollView scroller = new ScrollView(context);
        scroller.addView(calendar, new FrameLayout.LayoutParams(widthPx,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        FrameLayout.LayoutParams scrollerParams = new FrameLayout.LayoutParams(widthPx,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        scrollerParams.topMargin = barHeight;
        panel.addView(scroller, scrollerParams);

        int closeSize = dp(context, 26);
        ImageView close = new ImageView(context);
        // 直接用弹层里惯用的圆圈叉图标，只把颜色滤成与日历控件一致的金色
        close.setImageResource(R.drawable.ic_failed);
        close.setImageTintList(ColorStateList.valueOf(context.getColor(R.color.gold)));
        close.setBackgroundResource(R.color.transparent);
        close.setContentDescription(context.getString(R.string.calendar_close));
        close.setClickable(true);
        close.setFocusable(true);
        close.setOnClickListener(v -> dismiss());
        FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(closeSize, closeSize,
                Gravity.TOP | Gravity.END);
        closeParams.topMargin = dp(context, 1);
        closeParams.rightMargin = dp(context, 1);
        panel.addView(close, closeParams);

        final int maxHeight = (int) (context.getResources().getDisplayMetrics().heightPixels * 0.86f)
                - barHeight - padding * 2;
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
