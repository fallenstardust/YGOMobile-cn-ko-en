package cn.garymb.ygomobile.ui.cards.deck;

import android.content.Context;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SearchEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.RecyclerView;

import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.loader.ImageLoader;
import ocgcore.data.Card;

/**
 * 卡组编辑页"长按搜索结果卡片拖入卡组"的浮层拖拽辅助类。
 * 长按搜索结果列表中的卡片后，收起抽屉并在手指位置生成一个跟随手指移动的浮动卡片，
 * 松手时根据浮动卡片所在位置计算目标卡区（主卡组/额外卡组/副卡组）的插入点，
 * 交由回调处理校验与插入；松手位置不在卡组网格内则浮动卡片直接消失。
 * <p>
 * 实现要点：长按触发时手指的ACTION_DOWN早已被列表消费，中途加入视图树的浮层无法收到本次
 * 触摸序列，且closeDrawer会让DrawerLayout接管手势并向子view发送ACTION_CANCEL。
 * 因此采用窗口级触摸捕获（包装Window.Callback直接观察原始触摸流），
 * 浮动卡片挂在DecorView上仅负责显示，不参与触摸分发。
 * <p>
 * 抽屉侧：起拖必须把两扇抽屉「锁死 + 立即归位」而不是普通带动画收起，否则本次手势会被
 * DrawerLayout 判成拉抽屉、抽屉跟着手指左右漂移（见 {@link #lockDrawersForDrag()}）。
 */
public class DeckDragDropHelper {

    /**
     * 松手后的落点回调
     */
    public interface OnDeckDropListener {
        /**
         * 浮动卡片松手时回调
         *
         * @param card   被拖动的卡片
         * @param target 落点解析结果（松手时落点有效才会回调）
         */
        void onCardDropped(Card card, DropTarget target);

        /**
         * 拖动开始（浮动卡片已创建、抽屉开始收起）时回调
         */
        void onDragBegin();
    }

    /**
     * 落点解析结果：目标卡区类型 + 在卡组item列表中的插入位置
     */
    public static class DropTarget {
        public final DeckItemType type;
        public final int insertPos;

        DropTarget(DeckItemType type, int insertPos) {
            this.type = type;
            this.insertPos = insertPos;
        }
    }

    private final Context context;
    private final DrawerLayout drawerLayout;
    private final RecyclerView deckGrid;
    private final DeckAdapater deckAdapater;
    private final ImageLoader imageLoader;
    private final OnDeckDropListener listener;

    private Window.Callback originalCallback;
    private ViewGroup decorView;
    private ImageView floatCard;
    private Card draggingCard;
    /** 本次拖拽是否已把两扇抽屉锁死（见 {@link #lockDrawersForDrag()}）；解锁一律回 UNLOCKED，本页从未其它锁定时机 */
    private boolean drawersLocked;

    public DeckDragDropHelper(Context context, DrawerLayout drawerLayout, RecyclerView deckGrid,
                              DeckAdapater deckAdapater, ImageLoader imageLoader,
                              OnDeckDropListener listener) {
        this.context = context;
        this.drawerLayout = drawerLayout;
        this.deckGrid = deckGrid;
        this.deckAdapater = deckAdapater;
        this.imageLoader = imageLoader;
        this.listener = listener;
    }

    /**
     * 开始一次拖拽：在手指位置创建浮动卡片并捕获窗口触摸流，同时立即收起搜索结果抽屉
     *
     * @param card       被拖动的卡片
     * @param rawScreenX 手指位置的屏幕X坐标
     * @param rawScreenY 手指位置的屏幕Y坐标
     */
    public void beginDrag(Card card, float rawScreenX, float rawScreenY) {
        if (card == null || floatCard != null) {
            return;
        }
        if (!(context instanceof AppCompatActivity)) {
            return;
        }
        Window window = ((AppCompatActivity) context).getWindow();
        if (window == null || window.getCallback() == null
                || !(window.getDecorView() instanceof ViewGroup)) {
            return;
        }
        draggingCard = card;
        decorView = (ViewGroup) window.getDecorView();
        originalCallback = window.getCallback();

        // 浮动卡片：与卡组网格单格同宽同高，挂在DecorView上仅负责显示
        int cardWidth = deckGrid.getMeasuredWidth() / Constants.DECK_WIDTH_COUNT;
        int cardHeight = deckAdapater.getItemHeight() > 0 ? deckAdapater.getItemHeight() : scaleHeight(cardWidth);
        floatCard = new ImageView(context);
        floatCard.setScaleType(ImageView.ScaleType.FIT_XY);
        decorView.addView(floatCard, new FrameLayout.LayoutParams(cardWidth, cardHeight));
        imageLoader.bindImage(floatCard, card, ImageLoader.Type.small);
        floatCard.setAlpha(0.9f);
        ViewCompat.setElevation(floatCard, 12f);
        positionFloatCard(rawScreenX, rawScreenY);

        // 包装窗口回调，直接捕获本次手势的原始触摸流（不依赖视图分发路径）
        window.setCallback(new DragWindowCallback(originalCallback));

        if (listener != null) {
            listener.onDragBegin();
        }
        // 起拖当场把结果抽屉完全收起并锁死，露出卡组网格
        lockDrawersForDrag();
    }

    /**
     * 拖拽期间锁死两扇抽屉并当场立即归位（不走动画）。
     * <p>
     * 根因：搜索结果列表是 DrawerLayout 的抽屉子View，长按卡片后手指横向移动时，
     * DrawerLayout 的 onInterceptTouchEvent 会把这次位移判成「拉抽屉」（captureOpenAsDrag/canDragView）
     * 并接管剩余事件流，于是抽屉跟着被拖动的卡片左右漂移；带动画的 closeDrawer 处于 SETTLING
     * 时同样会被后续 MOVE 抢占成 DRAGGING，所以「先收起再拖」并不保险。
     * <p>
     * 两条一起做：先置 LOCK_MODE_LOCKED_CLOSED 切断拦截链（canDragView 对非 UNLOCKED 抽屉直接判假），
     * 再 animate=false 立即归位，终止在途的 settle/drag 与残留位移；本项目的抽屉从不长期锁定，
     * 因此解锁统一回 UNLOCKED。
     * <p>
     * 立即归位前必须先问 isDrawerVisible：本布局缺某一扇抽屉时，DrawerLayout 的
     * {@code closeDrawer(int, boolean)} 找不到该 gravity 的抽屉会直抛
     * {@code IllegalArgumentException}（而 setDrawerLockMode 是静默返回），等于给只带一扇
     * 抽屉的复用方埋一颗「长按起拖那一帧崩溃」的坑；isDrawerVisible 同 gravity 不抛异常。
     */
    private void lockDrawersForDrag() {
        if (drawersLocked) {
            return;
        }
        drawersLocked = true;
        forceCloseDrawerIfPresent(Constants.CARD_RESULT_GRAVITY);
        forceCloseDrawerIfPresent(Constants.CARD_SEARCH_GRAVITY);
    }

    /** 锁死该 gravity 的抽屉并立即归位（该 gravity 无抽屉时静默跳过，不抛） */
    private void forceCloseDrawerIfPresent(int gravity) {
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED, gravity);
        if (drawerLayout.isDrawerVisible(gravity)) {
            drawerLayout.closeDrawer(gravity, false);
        }
    }

    /** 拖拽收尾恢复抽屉可正常拉出（幂等，未锁过时静默跳过） */
    private void unlockDrawersAfterDrag() {
        if (!drawersLocked) {
            return;
        }
        drawersLocked = false;
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, Constants.CARD_RESULT_GRAVITY);
        drawerLayout.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED, Constants.CARD_SEARCH_GRAVITY);
    }

    /**
     * 取消进行中的拖拽（如视图销毁时），恢复窗口回调并移除浮动卡片，不触发落点回调
     */
    public void cancel() {
        finishDrag(null, null);
    }

    public boolean isDragging() {
        return floatCard != null;
    }

    /**
     * 结束拖拽：移除浮动卡片、还原Window.Callback；
     * card与target均非null时（正常松手且落点有效）触发落点回调。方法幂等，可重复调用
     */
    private void finishDrag(Card card, DropTarget target) {
        // 先取出并复位状态，保证幂等
        Window.Callback callback = originalCallback;
        Card droppedCard = (card != null && target != null) ? card : null;
        originalCallback = null;
        draggingCard = null;
        if (decorView != null && floatCard != null) {
            decorView.removeView(floatCard);
        }
        floatCard = null;
        decorView = null;
        unlockDrawersAfterDrag();
        if (callback != null && context instanceof AppCompatActivity) {
            Window window = ((AppCompatActivity) context).getWindow();
            if (window != null && window.getCallback() instanceof DragWindowCallback) {
                window.setCallback(callback);
            }
        }
        if (droppedCard != null && listener != null) {
            listener.onCardDropped(droppedCard, target);
        }
    }

    /**
     * 根据屏幕坐标在卡组网格中解析落点（卡区类型与插入位置）
     *
     * @return 落点解析结果，坐标不在网格或不在有效卡区范围内时返回null
     */
    private DropTarget resolveTarget(float screenX, float screenY) {
        return resolveTargetAt(deckGrid, screenX, screenY);
    }

    /**
     * 落点解析的共用实现：把屏幕坐标映射为卡组网格里的目标卡区与插入下标。
     * 包级静态：搜索结果单卡拖入（本类）与批量勾选的整组拖动
     * （{@link DeckBatchEditController}）共用同一套落点口径，避免两处判定随时间漂移。
     *
     * @return 落点解析结果，坐标不在网格或不在有效卡区范围内时返回 null
     */
    static DropTarget resolveTargetAt(RecyclerView deckGrid, float screenX, float screenY) {
        if (deckGrid == null) {
            return null;
        }
        int[] loc = new int[2];
        deckGrid.getLocationOnScreen(loc);
        int localX = (int) (screenX - loc[0]);
        int localY = (int) (screenY - loc[1]);
        View child = findChildUnder(deckGrid, localX, localY);
        if (child == null) {
            return null;
        }
        RecyclerView.ViewHolder holder = deckGrid.getChildViewHolder(child);
        if (!(holder instanceof DeckViewHolder)) {
            return null;
        }
        int pos = holder.getAdapterPosition();
        if (pos < 0) {
            return null;
        }
        DeckItemType holderType = ((DeckViewHolder) holder).getItemType();
        // 落在分隔栏上：插入到该栏对应卡区的末尾
        if (holderType == DeckItemType.MainLabel) {
            return new DropTarget(DeckItemType.MainCard, DeckItem.MainEnd);
        }
        if (holderType == DeckItemType.ExtraLabel) {
            return new DropTarget(DeckItemType.ExtraCard, DeckItem.ExtraEnd);
        }
        if (holderType == DeckItemType.SideLabel) {
            return new DropTarget(DeckItemType.SideCard, DeckItem.SideEnd);
        }
        // 落在卡片或空格上：插入到该格所在位置（挤开此处的卡片）
        if (DeckItemUtils.isMain(pos)) {
            return new DropTarget(DeckItemType.MainCard, pos);
        }
        if (DeckItemUtils.isExtra(pos)) {
            return new DropTarget(DeckItemType.ExtraCard, pos);
        }
        if (DeckItemUtils.isSide(pos)) {
            return new DropTarget(DeckItemType.SideCard, pos);
        }
        return null;
    }

    /**
     * 在卡组网格当前可见子view中查找坐标所在的item
     */
    private static View findChildUnder(RecyclerView deckGrid, int x, int y) {
        for (int i = deckGrid.getChildCount() - 1; i >= 0; i--) {
            View child = deckGrid.getChildAt(i);
            if (x >= child.getLeft() && x < child.getRight()
                    && y >= child.getTop() && y < child.getBottom()) {
                return child;
            }
        }
        return null;
    }

    /**
     * 按卡图宽高比（177:254）由宽度计算高度
     */
    private int scaleHeight(int width) {
        return Math.round((float) width * ((float) Constants.CORE_SKIN_CARD_COVER_SIZE[1]
                / (float) Constants.CORE_SKIN_CARD_COVER_SIZE[0]));
    }

    /**
     * 以手指位置为中心放置浮动卡片（相对DecorView通过margin定位，DecorView原点即屏幕原点）
     */
    private void positionFloatCard(float screenX, float screenY) {
        if (floatCard == null) {
            return;
        }
        ViewGroup.LayoutParams params = floatCard.getLayoutParams();
        if (params instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) params;
            lp.leftMargin = (int) (screenX - floatCard.getWidth() / 2f);
            lp.topMargin = (int) (screenY - floatCard.getHeight() / 2f);
            floatCard.setLayoutParams(lp);
        } else {
            floatCard.setX(screenX - floatCard.getWidth() / 2f);
            floatCard.setY(screenY - floatCard.getHeight() / 2f);
        }
    }

    /**
     * 窗口触摸捕获回调：ACTION_MOVE让浮动卡片跟随手指，ACTION_UP解析落点并结束拖拽，
     * 所有事件均原样转发给原始Callback，不影响系统正常分发
     */
    private class DragWindowCallback implements Window.Callback {
        private final Window.Callback delegate;

        DragWindowCallback(Window.Callback delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent event) {
            if (floatCard != null && draggingCard != null) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_MOVE:
                        positionFloatCard(event.getRawX(), event.getRawY());
                        break;
                    case MotionEvent.ACTION_UP: {
                        Card card = draggingCard;
                        DropTarget target = resolveTarget(event.getRawX(), event.getRawY());
                        finishDrag(card, target);
                        break;
                    }
                    case MotionEvent.ACTION_CANCEL:
                        finishDrag(null, null);
                        break;
                }
            }
            return delegate.dispatchTouchEvent(event);
        }

        @Override
        public boolean dispatchKeyEvent(KeyEvent event) {
            return delegate.dispatchKeyEvent(event);
        }

        @Override
        public boolean dispatchKeyShortcutEvent(KeyEvent event) {
            return delegate.dispatchKeyShortcutEvent(event);
        }

        @Override
        public boolean dispatchTrackballEvent(MotionEvent event) {
            return delegate.dispatchTrackballEvent(event);
        }

        @Override
        public boolean dispatchGenericMotionEvent(MotionEvent event) {
            return delegate.dispatchGenericMotionEvent(event);
        }

        @Override
        public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
            return delegate.dispatchPopulateAccessibilityEvent(event);
        }

        @Override
        public View onCreatePanelView(int featureId) {
            return delegate.onCreatePanelView(featureId);
        }

        @Override
        public boolean onCreatePanelMenu(int featureId, Menu menu) {
            return delegate.onCreatePanelMenu(featureId, menu);
        }

        @Override
        public boolean onPreparePanel(int featureId, View view, Menu menu) {
            return delegate.onPreparePanel(featureId, view, menu);
        }

        @Override
        public boolean onMenuOpened(int featureId, Menu menu) {
            return delegate.onMenuOpened(featureId, menu);
        }

        @Override
        public boolean onMenuItemSelected(int featureId, MenuItem item) {
            return delegate.onMenuItemSelected(featureId, item);
        }

        @Override
        public void onWindowAttributesChanged(WindowManager.LayoutParams attrs) {
            delegate.onWindowAttributesChanged(attrs);
        }

        @Override
        public void onContentChanged() {
            delegate.onContentChanged();
        }

        @Override
        public void onWindowFocusChanged(boolean hasFocus) {
            delegate.onWindowFocusChanged(hasFocus);
        }

        @Override
        public void onAttachedToWindow() {
            delegate.onAttachedToWindow();
        }

        @Override
        public void onDetachedFromWindow() {
            delegate.onDetachedFromWindow();
        }

        @Override
        public void onPanelClosed(int featureId, Menu menu) {
            delegate.onPanelClosed(featureId, menu);
        }

        @Override
        public boolean onSearchRequested() {
            return delegate.onSearchRequested();
        }

        @Override
        public boolean onSearchRequested(SearchEvent searchEvent) {
            return delegate.onSearchRequested(searchEvent);
        }

        @Override
        public ActionMode onWindowStartingActionMode(ActionMode.Callback callback) {
            return delegate.onWindowStartingActionMode(callback);
        }

        @Override
        public ActionMode onWindowStartingActionMode(ActionMode.Callback callback, int type) {
            return delegate.onWindowStartingActionMode(callback, type);
        }

        @Override
        public void onActionModeStarted(ActionMode mode) {
            delegate.onActionModeStarted(mode);
        }

        @Override
        public void onActionModeFinished(ActionMode mode) {
            delegate.onActionModeFinished(mode);
        }
    }
}
