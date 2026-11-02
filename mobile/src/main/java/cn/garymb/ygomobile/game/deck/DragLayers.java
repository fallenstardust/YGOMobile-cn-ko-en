package cn.garymb.ygomobile.game.deck;

import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 应用内拖拽浮层（卡图虚影）的跨实例登记处：{@link CardDragHelper} 与 {@link BatchCardDragHelper}
 * 每挂一个浮层就在此登记，每摘一个就注销。
 * <p>为什么需要它（用户反馈的「场上残留一个无法点击、无法操作的卡片矩形虚影」）：浮层挂在
 * Activity 的 {@code android.R.id.content} 上，只有 helper 自己收到 ACTION_UP/CANCEL 才会摘除。
 * 一旦本次手势的抬手没有送到 helper —— 父级容器抢走事件、询问窗/Toast 弹出、卡组网格
 * {@code notifyDataSetChanged} 中途换掉触摸监听、多指切换、页面暂停 —— 那个 ImageView 就永久
 * 留在内容层上：它没有点击监听，既不挡住也不接受操作，看上去就是"一张卡片钉在场上"。
 * 单靠 helper 自身无法自愈（残层可能属于另一个已经不再持有引用的实例），故统一登记 + 按下即清扫。
 */
final class DragLayers {

    /** 一条活浮层记录：视图 + 它实际挂载的父容器（父容器随旋转/重建换过也能正确摘除） */
    private static final class Live {
        final View view;
        final ViewGroup parent;

        Live(View view, ViewGroup parent) {
            this.view = view;
            this.parent = parent;
        }
    }

    private static final List<Live> LIVE = new ArrayList<>();

    private DragLayers() {}

    /** 登记一个刚挂上内容层的浮层 */
    static void add(View layer, ViewGroup parent) {
        if (layer != null && parent != null) LIVE.add(new Live(layer, parent));
    }

    /** 摘除并注销指定浮层（父容器已换了就按当前父容器摘，避免 addView 的新父没对上而摘不掉） */
    static void remove(View layer) {
        if (layer == null) return;
        for (Iterator<Live> it = LIVE.iterator(); it.hasNext(); ) {
            Live l = it.next();
            if (l.view != layer) continue;
            detach(l.view, l.parent);
            it.remove();
        }
    }

    /**
     * 清扫所有登记在册的活浮层（新一次按下/开始拖动前调用）：即便上一个手势的抬手彻底丢失，
     * 玩家下一次碰卡片时残影就会立刻消失，不会一直挂在场上。
     *
     * @return 实际摘除的个数
     */
    static int sweep() {
        int n = LIVE.size();
        for (Live l : LIVE) detach(l.view, l.parent);
        LIVE.clear();
        return n;
    }

    private static void detach(View layer, ViewGroup parent) {
        if (layer == null) return;
        ViewGroup vp = parent != null ? parent : (layer.getParent() instanceof ViewGroup
                ? (ViewGroup) layer.getParent() : null);
        if (vp != null) vp.removeView(layer);
        else if (layer.getParent() instanceof ViewGroup) ((ViewGroup) layer.getParent()).removeView(layer);
    }
}
