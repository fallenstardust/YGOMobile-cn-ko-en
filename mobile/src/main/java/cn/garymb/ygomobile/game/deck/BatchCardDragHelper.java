package cn.garymb.ygomobile.game.deck;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.audio.SoundManager;
import cn.garymb.ygomobile.bean.DeckInfo;
import ocgcore.data.Card;

/**
 * 批量勾选模式下的“整组拖动”助手：与单卡 {@link CardDragHelper} 同样的应用内自定义拖拽
 * （不走系统 startDragAndDrop，规避 ColorOS“内容传送门”等系统级浮窗拦截落点），
 * 区别是为每张已勾选的卡片各生成一个卡图浮层，浮层之间的相对位置与网格中的相对位置一致，
 * 整组随手指平移；松手时以手指落点命中已注册的落点目标并回调一次整组落点。
 * <p>
 * 本类只负责“浮层跟随 + 落点命中”，不触碰卡组数据：类型是否允许、张数是否超限等校验
 * 全部由 {@link DeckBatchSelectionManager} 在回调里决定，保证拖动过程中卡组内容不被预改。
 * 仅在 UI 线程调用。
 */
public class BatchCardDragHelper {

    /**
     * 整组落点回调：target 为手指松手处命中的已注册落点视图（未命中为 null）
     */
    public interface BatchDropHandler {
        void onBatchDrop(View target, float rawX, float rawY);
    }

    /**
     * 一个被拖动条目：所属卡区 + 在该卡区列表中的下标 + 卡片数据。
     * view 为该卡在网格中的视图，可为 null（视图尚未附加时不生成浮层，但仍参与整组搬运）。
     */
    public static final class BatchItem {
        public final View view;
        public final DeckInfo.Type zone;
        public final int index;
        public final Card card;

        public BatchItem(View view, DeckInfo.Type zone, int index, Card card) {
            this.view = view;
            this.zone = zone;
            this.index = index;
            this.card = card;
        }
    }

    private final Activity activity;
    private final BatchDropHandler dropHandler;
    /** 拖拽音效源（与单卡拖拽同源：拾起 CARD_PICK，放下 CARD_DROP），可为 null */
    private final SoundManager soundManager;
    private final List<View> dropTargets = new ArrayList<>();

    private ViewGroup content;
    private int contentX, contentY;
    private final List<ImageView> layers = new ArrayList<>();
    private final List<Bitmap> bitmaps = new ArrayList<>();
    /** 每个浮层相对锚点卡（手指按住的那张卡）左上角的屏幕偏移 */
    private final List<int[]> offsets = new ArrayList<>();
    /** 手指相对锚点卡左上角的抓取偏移 */
    private float grabDX, grabDY;
    /** 锚点浮层（虚影）尺寸，用于把落点与悬停判定算到虚影中心而非手指位置 */
    private float anchorW, anchorH;
    /** hitPoint 的复用容器，避免每次 MOVE 新建数组 */
    private final float[] hit = new float[2];
    private boolean dragging;
    private View highlightTarget;

    public BatchCardDragHelper(Activity activity, BatchDropHandler dropHandler, SoundManager soundManager) {
        this.activity = activity;
        this.dropHandler = dropHandler;
        this.soundManager = soundManager;
    }

    public void addDropTarget(View view) {
        if (view != null && !dropTargets.contains(view)) dropTargets.add(view);
    }

    public boolean isDragging() {
        return dragging;
    }

    /**
     * 开始整组拖动：为每个有视图的勾选条目生成卡图快照浮层，加入 Activity 的 content 层，
     * 并按当前网格布局定好彼此相对位置。anchorIndex 指向的那一项就是手指按住的卡。
     *
     * @return 是否真正进入了拖动状态（无有效锚点视图时返回 false）
     */
    public boolean startDrag(List<BatchItem> items, int anchorIndex, MotionEvent trigger) {
        if (items == null || items.isEmpty()) return false;
        if (anchorIndex < 0 || anchorIndex >= items.size()) return false;
        View anchorView = items.get(anchorIndex).view;
        if (anchorView == null) return false;
        //上一次整组拖动的抬手丢了（被弹窗/重绑抢事件）时，残影先清掉再开新一轮，不早退
        if (dragging) cancelDrag();
        DragLayers.sweep();
        content = activity.findViewById(android.R.id.content);
        if (content == null) return false;

        int[] anchorLoc = new int[2];
        anchorView.getLocationOnScreen(anchorLoc);
        grabDX = trigger.getRawX() - anchorLoc[0];
        grabDY = trigger.getRawY() - anchorLoc[1];

        int[] loc = new int[2];
        anchorW = 0f;
        anchorH = 0f;
        for (int i = 0; i < items.size(); i++) {
            BatchItem item = items.get(i);
            if (item.view == null) continue;
            Bitmap bmp = snapshot(item.view);
            if (bmp == null) continue;
            item.view.getLocationOnScreen(loc);
            layers.add(appendLayer(bmp));
            bitmaps.add(bmp);
            offsets.add(new int[]{loc[0] - anchorLoc[0], loc[1] - anchorLoc[1]});
            if (i == anchorIndex) {
                anchorW = bmp.getWidth();
                anchorH = bmp.getHeight();
            }
        }
        // 锚点卡的快照未能出浮层（尺寸为 0 等）时无法定出虚影中心，放弃本次拖动
        if (layers.isEmpty() || anchorW <= 0f || anchorH <= 0f) {
            removeLayers();
            content = null;
            return false;
        }
        int[] cloc = new int[2];
        content.getLocationOnScreen(cloc);
        contentX = cloc[0];
        contentY = cloc[1];

        dragging = true;
        moveGroup(trigger.getRawX(), trigger.getRawY());
        if (soundManager != null) soundManager.playSoundEffect(SoundManager.SFX.CARD_PICK);
        return true;
    }

    /**
     * 拖动期间接收触摸监听器转发的触摸事件：整组平移、高亮悬停目标、松手命中落点。
     */
    public boolean onTouchEvent(MotionEvent event) {
        if (!dragging) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                moveGroup(event.getRawX(), event.getRawY());
                updateHighlight(event.getRawX(), event.getRawY());
                return true;
            case MotionEvent.ACTION_UP:
                moveGroup(event.getRawX(), event.getRawY());
                performDrop(event.getRawX(), event.getRawY());
                return true;
            case MotionEvent.ACTION_CANCEL:
                cancelDrag();
                return true;
            default:
                return true;
        }
    }

    public void cancelDrag() {
        clearHighlight();
        removeLayers();
        dragging = false;
    }

    private ImageView appendLayer(Bitmap bmp) {
        ImageView layer = new ImageView(activity);
        layer.setImageBitmap(bmp);
        layer.setAlpha(0.85f);
        content.addView(layer, new FrameLayout.LayoutParams(bmp.getWidth(), bmp.getHeight()));
        DragLayers.add(layer, content);
        return layer;
    }

    private Bitmap snapshot(View view) {
        if (view == null || view.getWidth() <= 0 || view.getHeight() <= 0) return null;
        Bitmap bmp = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bmp));
        return bmp;
    }

    /**
     * 整组平移：锚点浮层回到“手指减去抓取偏移”的屏幕位置，其余浮层按各自记录的相对偏移跟随。
     */
    private void moveGroup(float rawX, float rawY) {
        float anchorLeft = rawX - grabDX - contentX;
        float anchorTop = rawY - grabDY - contentY;
        for (int i = 0; i < layers.size(); i++) {
            ImageView layer = layers.get(i);
            int[] offset = offsets.get(i);
            layer.setX(anchorLeft + offset[0]);
            layer.setY(anchorTop + offset[1]);
        }
    }

    private View findTarget(float rawX, float rawY) {
        int[] loc = new int[2];
        for (View target : dropTargets) {
            target.getLocationOnScreen(loc);
            if (rawX >= loc[0] && rawX <= loc[0] + target.getWidth()
                    && rawY >= loc[1] && rawY <= loc[1] + target.getHeight()) {
                return target;
            }
        }
        return null;
    }

    /**
     * 落点 / 悬停判定点：取锚点虚影的中心而不是手指位置。按住卡片边角起拖时两者可差半张卡，
     * 按手指判定会出现“虚影明明在某区、实际却落到另一区”。虚影左上角屏幕坐标恒为
     * “手指坐标 - 抓取偏移”，所以直接算而不查浮层（查 View 会受未生效布局影响的干扰）。
     */
    private void hitPoint(float rawX, float rawY) {
        hit[0] = rawX - grabDX + anchorW / 2f;
        hit[1] = rawY - grabDY + anchorH / 2f;
    }

    private void updateHighlight(float rawX, float rawY) {
        hitPoint(rawX, rawY);
        View target = findTarget(hit[0], hit[1]);
        if (target == highlightTarget) return;
        clearHighlight();
        highlightTarget = target;
        if (target != null) target.setAlpha(0.7f);
    }

    private void clearHighlight() {
        if (highlightTarget != null) {
            highlightTarget.setAlpha(1f);
            highlightTarget = null;
        }
    }

    private void performDrop(float rawX, float rawY) {
        if (soundManager != null) soundManager.playSoundEffect(SoundManager.SFX.CARD_DROP);
        //先把落点换算成虚影中心，后续命中与插入下标均用该点，与浮层所见完全一致
        hitPoint(rawX, rawY);
        float dropX = hit[0];
        float dropY = hit[1];
        View target = findTarget(dropX, dropY);
        cancelDrag();
        if (target != null) {
            dropHandler.onBatchDrop(target, dropX, dropY);
        }
    }

    private void removeLayers() {
        for (ImageView layer : layers) {
            DragLayers.remove(layer);
        }
        layers.clear();
        offsets.clear();
        for (Bitmap bmp : bitmaps) bmp.recycle();
        bitmaps.clear();
    }
}
