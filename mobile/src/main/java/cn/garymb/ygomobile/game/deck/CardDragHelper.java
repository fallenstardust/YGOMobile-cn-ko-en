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
 * 应用内自定义拖拽助手：不走系统startDragAndDrop，规避ColorOS"内容传送门"等
 * 系统级拖拽增强功能弹出底部浮窗拦截落点的问题。
 * 拖拽阴影为卡图快照，添加到Activity的content层跟随手指；
 * 松手时对已注册的落点目标做命中检测并回调完成移动。
 */
public class CardDragHelper {

    public interface DropHandler {
        void onCardDrop(View target, DeckInfo.Type source, int index, Card card, float rawX, float rawY);
    }

    private final Activity activity;
    private final DropHandler dropHandler;
    /** 拖拽音效源（对齐 C++ deck_con.cpp：拾起卡 CARD_PICK，放下卡 CARD_DROP），可为 null */
    private final SoundManager soundManager;
    private final List<View> dropTargets = new ArrayList<>();

    private ViewGroup content;
    private int contentX, contentY;
    private ImageView dragLayer;
    private Bitmap dragBitmap;
    private boolean dragging;
    private DeckInfo.Type dragSource;
    private int dragIndex;
    private Card dragCard;
    private float grabDX, grabDY;
    private View highlightTarget;
    /** 落点/悬停判定坐标的复用容器（存虚影中心，而非手指位置） */
    private final float[] hit = new float[2];

    public CardDragHelper(Activity activity, DropHandler dropHandler, SoundManager soundManager) {
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
     * 触摸监听器在移动超过阈值后调用：快照shadowView作为拖拽阴影加入content层并进入拖拽模式，
     * 后续触摸事件经{@link #onTouchEvent}继续转发。
     */
    public void startDrag(View shadowView, DeckInfo.Type source, int index, Card card, MotionEvent trigger) {
        if (card == null) return;
        //上一次的拖动没收过抬手（拖到一半被弹窗/重绑抢走事件）时残留的虚影先清掉再继续，
        //不能因 dragging 还挂着就直接早退
        if (dragging) cancelDrag();
        DragLayers.sweep();
        content = activity.findViewById(android.R.id.content);
        if (content == null) return;

        dragBitmap = snapshot(shadowView);
        if (dragBitmap == null) return;
        dragLayer = new ImageView(activity);
        dragLayer.setImageBitmap(dragBitmap);
        dragLayer.setAlpha(0.85f);
        content.addView(dragLayer, new FrameLayout.LayoutParams(
                dragBitmap.getWidth(), dragBitmap.getHeight()));
        //登记到跨实例活浮层表：抬手丢失时由下一次按下或新拖动的 sweep 自愈
        DragLayers.add(dragLayer, content);

        int[] cloc = new int[2];
        content.getLocationOnScreen(cloc);
        contentX = cloc[0];
        contentY = cloc[1];

        dragging = true;
        dragSource = source;
        dragIndex = index;
        dragCard = card;
        int[] loc = new int[2];
        shadowView.getLocationOnScreen(loc);
        grabDX = trigger.getRawX() - loc[0];
        grabDY = trigger.getRawY() - loc[1];
        moveLayer(trigger.getRawX(), trigger.getRawY());
        //对齐 C++ EMIE_MOUSE_MOVED 开始拖拽时 PlaySoundEffect(CARD_PICK)
        if (soundManager != null) soundManager.playSoundEffect(SoundManager.SFX.CARD_PICK);
    }

    /**
     * 拖拽期间接收触摸监听器转发的触摸事件：移动阴影、高亮悬停目标、松手命中落点。
     */
    public boolean onTouchEvent(MotionEvent event) {
        if (!dragging) return false;
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                moveLayer(event.getRawX(), event.getRawY());
                updateHighlight(event.getRawX(), event.getRawY());
                return true;
            case MotionEvent.ACTION_UP:
                moveLayer(event.getRawX(), event.getRawY());
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
        removeDragLayer();
        reset();
    }

    private Bitmap snapshot(View view) {
        if (view == null || view.getWidth() <= 0 || view.getHeight() <= 0) return null;
        Bitmap bmp = Bitmap.createBitmap(view.getWidth(), view.getHeight(), Bitmap.Config.ARGB_8888);
        view.draw(new Canvas(bmp));
        return bmp;
    }

    private void moveLayer(float rawX, float rawY) {
        if (dragLayer == null) return;
        dragLayer.setX(rawX - grabDX - contentX);
        dragLayer.setY(rawY - grabDY - contentY);
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
     * 落点 / 悬停判定点取虚影中心而不是手指位置：按住卡片边角起拖时两者可差半张卡，
     * 按手指判定会出现“虚影明明在某区、实际却落到另一区”。阴影左上角的屏幕坐标恒为
     * “手指坐标 - 抓取偏移”（见{@link #moveLayer}），因此直接算而不查 View。
     */
    private void hitPoint(float rawX, float rawY) {
        hit[0] = rawX - grabDX;
        hit[1] = rawY - grabDY;
        if (dragBitmap != null) {
            hit[0] += dragBitmap.getWidth() / 2f;
            hit[1] += dragBitmap.getHeight() / 2f;
        }
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
        //对齐 C++ EMIE_LMOUSE_LEFT_UP 拖拽结束放置时 PlaySoundEffect(CARD_DROP)
        if (soundManager != null) soundManager.playSoundEffect(SoundManager.SFX.CARD_DROP);
        //先把落点换算成虚影中心，命中判定与下游插入下标均用该点，与玩家看到的阴影位置一致
        hitPoint(rawX, rawY);
        float dropX = hit[0];
        float dropY = hit[1];
        View target = findTarget(dropX, dropY);
        DeckInfo.Type source = dragSource;
        int index = dragIndex;
        Card card = dragCard;
        cancelDrag();
        if (target != null) {
            dropHandler.onCardDrop(target, source, index, card, dropX, dropY);
        }
    }

    private void removeDragLayer() {
        if (dragLayer != null) {
            //经登记处摘除：父容器已换（旋转/重建）时按当前父容器也能摘干净
            DragLayers.remove(dragLayer);
            dragLayer = null;
        }
        if (dragBitmap != null) {
            dragBitmap.recycle();
            dragBitmap = null;
        }
    }

    private void reset() {
        dragging = false;
        dragSource = null;
        dragIndex = -1;
        dragCard = null;
    }
}