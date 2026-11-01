package cn.garymb.ygomobile.ui.cards.deck;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * 卡组编辑页“批量勾选模式”的控制器（长按进入 / 点击勾选 / 按住整组拖动）。
 * <p>
 * 手势分工：进入模式由 {@code ItemTouchHelperPlus} 的长按回调转交（见
 * {@code DeckManagerFragmentBase#onDragLongPress}）；模式激活后
 * {@link DeckItemTouchHelper#getMovementFlags} 一律返回 0 标志位，单卡拖拽不再 grab 手势，
 * 本类以 {@link RecyclerView.OnItemTouchListener} 的身份接管“按住已勾选卡片移动超过阈值”
 * 的整组拖动，点击则回到 {@code RecyclerViewItemListener} 由上层转成勾选切换。
 * <p>
 * 整组拖动的视觉：为每张已勾选且当前可见的卡片各生成一个卡图浮层挂在 DecorView 上，
 * 浮层之间保持它们在网格里的相对位置，整组随手指平移；松手时以手指落点解析目标卡区与
 * 插入位，交给 {@link DeckAdapater#batchMoveCheckedTo} 做类型/容量判定与一次性搬运。
 * 本类不改任何卡组数据。
 */
public class DeckBatchEditController implements RecyclerView.OnItemTouchListener {

    /**
     * 与宿主页面的交互口
     */
    public interface Callback {
        /**
         * 是否允许进入批量模式（卡包展示、只读卡组等场景返回 false）
         */
        boolean isBatchModeAllowed();

        /**
         * 模式刚被激活或退出，供宿主刷新界面（如收起抽屉、提示文案）
         */
        void onBatchModeChanged(boolean enabled);

        /**
         * 一次整组拖动松手后回调（落点在卡组网格内才会走到搬运分支）。
         *
         * @param result 搬运结果，SUCCESS 时宿主需要记录一次卡组历史快照
         * @param target 落点解析结果
         */
        void onBatchMoved(DeckAdapater.DragInsertResult result, DeckDragDropHelper.DropTarget target);

        /**
         * 请求删除当前全部已勾选的卡：由“整组松手在删除区（垃圾桶所在行）上”或
         * “批量模式下再次长按卡片”触发。宿主完成删除、计入历史并退出批量模式。
         */
        void onBatchDeleteRequested();

        /**
         * 整组拖动期间手指是否悬停在删除区上，宿主据此放大 / 还原垃圾桶图标。
         */
        void onBatchDeleteZoneHoverChanged(boolean inside);
    }

    private final Activity activity;
    private final RecyclerView deckGrid;
    private final DeckAdapater deckAdapater;
    private final Callback callback;
    private final int touchSlop;

    private ViewGroup decorView;
    /**
     * “拖到此处删除”的落点视图（卡组编辑页为 undo/redo/垃圾桶那一行），由宿主设定；
     * 为 null 时整组拖到网格外不做任何变更。
     */
    private View deleteZone;
    /** 上一次向宿主报出的删除区悬停状态，避免每个 MOVE 重复回调 */
    private boolean hoverInDeleteZone;
    private final int[] zoneLoc = new int[2];
    private final List<ImageView> layers = new ArrayList<>();
    private final List<Bitmap> bitmaps = new ArrayList<>();
    /** 每个浮层相对锚点卡图左上角的屏幕偏移 */
    private final List<int[]> offsets = new ArrayList<>();
    /** 手指相对锚点卡图左上角的抓取偏移 */
    private float grabDX, grabDY;
    /** 锚点浮层（虚影）尺寸：落点与删除区悬停均按虚影中心判定，而非手指位置 */
    private float anchorW, anchorH;
    /** hitPoint 的复用容器，避免每次 MOVE 新建数组 */
    private final float[] hit = new float[2];
    private float downRawX, downRawY;
    private int anchorPos = RecyclerView.NO_POSITION;
    private boolean dragging;
    /** 上一条长按的处理时刻，用于吃掉同一次手势的重复长按回调 */
    private long lastLongPressHandledAt;
    private static final long LONG_PRESS_DEBOUNCE_MS = 300L;

    public DeckBatchEditController(Activity activity, RecyclerView deckGrid, DeckAdapater deckAdapater,
                                   Callback callback) {
        this.activity = activity;
        this.deckGrid = deckGrid;
        this.deckAdapater = deckAdapater;
        this.callback = callback;
        this.touchSlop = ViewConfiguration.get(activity).getScaledTouchSlop();
    }

    /** 挂到卡组网格上开始观察手势（模式开关本身由适配器持有） */
    public void install() {
        deckGrid.addOnItemTouchListener(this);
    }

    /** 视图销毁时摘除监听并收起残留浮层 */
    public void uninstall() {
        cancelDrag();
        deckGrid.removeOnItemTouchListener(this);
    }

    public boolean isBatchMode() {
        return deckAdapater.isBatchMode();
    }

    public boolean isDragging() {
        return dragging;
    }

    /** 设定“拖到此处即批量删除”的落点视图（传 null 则关闭该入口） */
    public void setDeleteZone(View zone) {
        cancelDeleteZoneHover();
        deleteZone = zone;
    }

    /** 拖动结束后把悬停状态复位，避免垃圾桶图标停在放大态 */
    private void cancelDeleteZoneHover() {
        if (!hoverInDeleteZone) return;
        hoverInDeleteZone = false;
        if (callback != null) callback.onBatchDeleteZoneHoverChanged(false);
    }

    /** 屏幕坐标是否落在删除区视图的矩形范围内 */
    private boolean isInsideDeleteZone(float rawX, float rawY) {
        View zone = deleteZone;
        if (zone == null || !zone.isShown()) return false;
        zone.getLocationOnScreen(zoneLoc);
        return rawX >= zoneLoc[0] && rawX <= zoneLoc[0] + zone.getWidth()
                && rawY >= zoneLoc[1] && rawY <= zoneLoc[1] + zone.getHeight();
    }

    /**
     * 判定点取锚点虚影的中心而不是手指位置：按住卡片边角起拖时两者可差半张卡，
     * 按手指判定会出现“虚影明明在某区（或删除区）上、实际却落到另一处”。
     * 浮层是直接按屏幕坐标 setX/setY 挂在 DecorView 上的，锚点虚影左上角即
     * “手指坐标 - 抓取偏移”，故直接算而不查 View。
     */
    private void hitPoint(float rawX, float rawY) {
        hit[0] = anchorW > 0f && anchorH > 0f
                ? rawX - grabDX + anchorW / 2f : rawX;
        hit[1] = anchorW > 0f && anchorH > 0f
                ? rawY - grabDY + anchorH / 2f : rawY;
    }

    /** 整组拖动过程中跟随虚影刷新删除区悬停态（进出各回调一次） */
    private void updateDeleteZoneHover(float rawX, float rawY) {
        hitPoint(rawX, rawY);
        boolean inside = isInsideDeleteZone(hit[0], hit[1]);
        if (inside == hoverInDeleteZone) return;
        hoverInDeleteZone = inside;
        if (callback != null) callback.onBatchDeleteZoneHoverChanged(inside);
    }

    /**
     * 长按卡组网格卡片：
     * <ul>
     * <li>未进模式：进入批量模式并勾选该卡（该路径由 ItemTouchHelperPlus 的长按回调转交）；</li>
     * <li>已在模式：再次长按即删除当前全部已勾选的卡（该路径由 RecyclerViewItemListener
     * 的长按回调转交，此时单卡拖拽标志位已置 0，两条路径不会重叠）。</li>
     * </ul>
     *
     * @return 是否已消费本次长按
     */
    public boolean onDeckCardLongPress(int pos) {
        if (callback == null || !callback.isBatchModeAllowed() || dragging) return false;
        //同一手势可能被两条长按链路先后回调，短窗口内只认第一次
        long now = android.os.SystemClock.uptimeMillis();
        if (now - lastLongPressHandledAt < LONG_PRESS_DEBOUNCE_MS) return false;
        if (!deckAdapater.isBatchMode()) {
            if (pos < 0 || deckAdapater.batchCardAt(pos) == null) return false;
            lastLongPressHandledAt = now;
            deckAdapater.setBatchMode(true);
            callback.onBatchModeChanged(true);
            deckAdapater.ensureBatchChecked(pos);
            return true;
        }
        if (!deckAdapater.hasBatchChecked()) return false;
        lastLongPressHandledAt = now;
        callback.onBatchDeleteRequested();
        return true;
    }

    /**
     * 点击卡组网格条目：批量模式下改为切换勾选并吞掉点击（不再弹卡片详情）。
     *
     * @return true 表示本次点击已被批量模式消费
     */
    public boolean onDeckCardTap(int pos) {
        if (!deckAdapater.isBatchMode()) return false;
        if (pos >= 0) {
            deckAdapater.toggleBatchCheck(pos);
        }
        return true;
    }

    /** 退出批量模式：收起浮层与删除区高亮、清空勾选并通知宿主（返回键专用） */
    public void exitBatchMode() {
        cancelDrag();
        if (deckAdapater.isBatchMode()) {
            deckAdapater.setBatchMode(false);
            if (callback != null) callback.onBatchModeChanged(false);
        }
    }

    /** 卡组被外部整体替换（撤销/重做/重载）后同步退出批量模式，不通知宿主以免多余弹窗 */
    public void reset() {
        cancelDrag();
        cancelDeleteZoneHover();
        deckAdapater.setBatchMode(false);
    }

    //region 整组拖动
    @Override
    public boolean onInterceptTouchEvent(@NonNull RecyclerView rv, MotionEvent e) {
        if (dragging) {
            onTouchEvent(rv, e);
            return true;
        }
        final int action = e.getActionMasked();
        // 抬手兜底：部分 RecyclerView 版本只把 UP/CANCEL 回灌给 onInterceptTouchEvent（见
        // ItemTouchHelperPlus 的同样写法），此处一并交给 onTouchEvent 收尾；
        // onTouchEvent 以 dragging 作门，重复调用不会二次落子
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            onTouchEvent(rv, e);
            return false;
        }
        if (!deckAdapater.isBatchMode() || callback == null || !callback.isBatchModeAllowed()) {
            return false;
        }
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                anchorPos = resolveAnchorPos(e);
                downRawX = e.getRawX();
                downRawY = e.getRawY();
                return false;
            case MotionEvent.ACTION_MOVE: {
                if (anchorPos < 0) return false;
                if (Math.abs(e.getRawX() - downRawX) <= touchSlop
                        && Math.abs(e.getRawY() - downRawY) <= touchSlop) {
                    return false;
                }
                //按住未勾选的卡也先把它勾上，整组包含它
                deckAdapater.ensureBatchChecked(anchorPos);
                return startGroupDrag(e);
            }
            default:
                return false;
        }
    }

    @Override
    public void onTouchEvent(@NonNull RecyclerView rv, MotionEvent e) {
        if (!dragging) return;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                moveGroup(e.getRawX(), e.getRawY());
                updateDeleteZoneHover(e.getRawX(), e.getRawY());
                break;
            case MotionEvent.ACTION_UP: {
                //落点先按虚影中心算好：cancelDrag 会清掉虚影尺寸，算晚了只能退到手指位置
                hitPoint(e.getRawX(), e.getRawY());
                float dropX = hit[0];
                float dropY = hit[1];
                // 先取悬停结论再收浮层（cancelDrag 会把悬停态复位）
                boolean toDeleteZone = isInsideDeleteZone(dropX, dropY)
                        && deckAdapater.hasBatchChecked();
                // 浮层与落点无关，先收起再改数据：改数据会触发网格重排，避免浮层与旧位置错帧
                cancelDrag();
                if (toDeleteZone) {
                    //松手时虚影落在删除区（垃圾桶所在行）：整组批量删除，不做二次确认（可撤销）
                    callback.onBatchDeleteRequested();
                    return;
                }
                DeckDragDropHelper.DropTarget target =
                        DeckDragDropHelper.resolveTargetAt(deckGrid, dropX, dropY);
                if (target != null) {
                    DeckAdapater.DragInsertResult result =
                            deckAdapater.batchMoveCheckedTo(target.type, target.insertPos);
                    callback.onBatchMoved(result, target);
                }
                //虚影既不在网格内也不在删除区：本次整组拖动作废，卡组保持不变
                break;
            }
            case MotionEvent.ACTION_CANCEL:
                cancelDrag();
                break;
            default:
                break;
        }
    }

    /**
     * 本类靠 {@code onInterceptTouchEvent} 返回 true 拿到后续事件流，不关心父级的
     * disallowIntercept 请求，空实现仅用于满足接口契约。
     */
    @Override
    public void onRequestDisallowInterceptTouchEvent(boolean disallowIntercept) {
    }

    /**
     * 按下处对应的卡组条目下标：只认卡片型条目，分隔栏与占位格不能作为整组拖动的锚点。
     */
    private int resolveAnchorPos(MotionEvent e) {
        View child = deckGrid.findChildViewUnder(e.getX(), e.getY());
        if (child == null) return RecyclerView.NO_POSITION;
        int pos = deckGrid.getChildAdapterPosition(child);
        if (pos == RecyclerView.NO_POSITION) return RecyclerView.NO_POSITION;
        RecyclerView.ViewHolder holder = deckGrid.getChildViewHolder(child);
        if (!(holder instanceof DeckViewHolder)) return RecyclerView.NO_POSITION;
        return deckAdapater.batchCardAt(pos) != null ? pos : RecyclerView.NO_POSITION;
    }

    /**
     * 为每张已勾选且可见的卡片生成卡图浮层，锚点卡必须在屏幕上可见且已有尺寸，
     * 否则返回 false 不进入拖动（没有锚点虚影就定不出可靠的落点中心）。
     */
    private boolean startGroupDrag(MotionEvent trigger) {
        ImageView anchorImage = cardImageOf(anchorPos);
        if (anchorImage == null) return false;
        decorView = resolveDecorView();
        if (decorView == null) return false;

        int[] anchorLoc = new int[2];
        anchorImage.getLocationOnScreen(anchorLoc);
        grabDX = trigger.getRawX() - anchorLoc[0];
        grabDY = trigger.getRawY() - anchorLoc[1];
        anchorW = anchorImage.getWidth();
        anchorH = anchorImage.getHeight();

        int[] loc = new int[2];
        for (Integer pos : deckAdapater.getBatchCheckedPositions()) {
            ImageView cardImage = cardImageOf(pos);
            if (cardImage == null) continue;
            Bitmap bmp = snapshot(cardImage);
            if (bmp == null) continue;
            cardImage.getLocationOnScreen(loc);
            layers.add(appendLayer(bmp));
            bitmaps.add(bmp);
            offsets.add(new int[]{loc[0] - anchorLoc[0], loc[1] - anchorLoc[1]});
        }
        if (layers.isEmpty()) {
            decorView = null;
            return false;
        }
        dragging = true;
        moveGroup(trigger.getRawX(), trigger.getRawY());
        //拖动期间禁止父级（DrawerLayout 等）拦截后续事件，保证收得到完整的 MOVE/UP 序列
        deckGrid.requestDisallowInterceptTouchEvent(true);
        return true;
    }

    /** 取某个卡组条目当前的卡图视图；条目未附加到屏幕（滚动出屏）时返回 null，该卡不出浮层 */
    private ImageView cardImageOf(int pos) {
        RecyclerView.ViewHolder holder = deckGrid.findViewHolderForAdapterPosition(pos);
        if (!(holder instanceof DeckViewHolder)) return null;
        ImageView cardImage = ((DeckViewHolder) holder).cardImage;
        return cardImage == null || cardImage.getWidth() <= 0 || cardImage.getHeight() <= 0
                ? null : cardImage;
    }

    private ViewGroup resolveDecorView() {
        if (activity == null || activity.getWindow() == null) return null;
        View root = activity.getWindow().getDecorView();
        return root instanceof ViewGroup ? (ViewGroup) root : null;
    }

    private ImageView appendLayer(Bitmap bmp) {
        ImageView layer = new ImageView(activity);
        layer.setImageBitmap(bmp);
        layer.setScaleType(ImageView.ScaleType.FIT_XY);
        layer.setAlpha(0.85f);
        decorView.addView(layer, new FrameLayout.LayoutParams(bmp.getWidth(), bmp.getHeight()));
        return layer;
    }

    private Bitmap snapshot(View view) {
        if (view == null || view.getWidth() <= 0 || view.getHeight() <= 0) return null;
        Bitmap bmp = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bmp));
        return bmp;
    }

    /** 整组平移：锚点浮层回到“手指减去抓取偏移”的屏幕位置，其余按各自记录的相对偏移跟随 */
    private void moveGroup(float rawX, float rawY) {
        float anchorLeft = rawX - grabDX;
        float anchorTop = rawY - grabDY;
        for (int i = 0; i < layers.size(); i++) {
            ImageView layer = layers.get(i);
            int[] offset = offsets.get(i);
            //DecorView 原点即屏幕原点，直接用屏幕坐标定位
            layer.setX(anchorLeft + offset[0]);
            layer.setY(anchorTop + offset[1]);
        }
    }

    /** 结束或取消拖动：移除全部浮层、复位删除区悬停态并回收位图。以 dragging 作门，方法幂等可重复调用 */
    private void cancelDrag() {
        if (!dragging && layers.isEmpty()) return;
        for (ImageView layer : layers) {
            if (decorView != null) decorView.removeView(layer);
        }
        layers.clear();
        offsets.clear();
        for (Bitmap bmp : bitmaps) bmp.recycle();
        bitmaps.clear();
        decorView = null;
        dragging = false;
        anchorPos = RecyclerView.NO_POSITION;
        anchorW = 0f;
        anchorH = 0f;
        cancelDeleteZoneHover();
    }
    //endregion
}
