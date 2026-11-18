package cn.garymb.ygomobile.ui.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.widget.HorizontalScrollView;

/**
 * 可横向滑动的按钮条容器：滑动它时不让外层容器（如 DrawerLayout）抢走这次手势。
 *
 * <p>卡组管理页的排序按钮行位于搜索结果抽屉里，抽屉会把横向拖动当成“拖动抽屉关闭”，
 * 于是滑动这排按钮时整条抽屉会跟着左右移动。本类在按下时就请求父容器不要拦截，
 * 把这一次手势完整留给横向滚动；抬起后系统会自行恢复，其它位置照常可以拖动抽屉。</p>
 */
public class HorizontalScrollViewPlus extends HorizontalScrollView {

    public HorizontalScrollViewPlus(Context context) {
        super(context);
    }

    public HorizontalScrollViewPlus(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public HorizontalScrollViewPlus(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        // 按下即独吞：父容器（抽屉）后续不再拦截 MOVE，横向拖动只滚动本控件
        if (ev.getActionMasked() == MotionEvent.ACTION_DOWN && getParent() != null) {
            getParent().requestDisallowInterceptTouchEvent(true);
        }
        return super.onInterceptTouchEvent(ev);
    }
}
