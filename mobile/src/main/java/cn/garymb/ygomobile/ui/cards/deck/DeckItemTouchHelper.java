package cn.garymb.ygomobile.ui.cards.deck;

import static androidx.recyclerview.widget.ItemTouchHelperPlus.ACTION_STATE_DRAG;
import static androidx.recyclerview.widget.ItemTouchHelperPlus.ACTION_STATE_IDLE;

import android.graphics.Canvas;
import android.util.Log;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.ItemTouchHelperPlus;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.lite.R;

public class DeckItemTouchHelper extends ItemTouchHelperPlus.Callback2 {
    private final DeckDrager mDeckDrager;
    private static final String TAG = "drag";
    private static final boolean DEBUG = false;
    private final DeckAdapater deckAdapater;
    /** 长按手抖容差（与父类判“真拖动”的阈值同源，即 {@code R.dimen.drag_rect}）：位移超过它，本次手势就是拖卡而不是按住不动 */
    private final int dragRectPx;
    /** 本次拖拽会话是否已经把卡片真的拖动起来（见 {@link #isCardDragging()}） */
    private boolean cardDragging;
    /** 拖动那一瞬间的回调：宿主据此把本次手势的长按语义（当场进批量勾选）当场撤销 */
    private Runnable onDragMovedListener;

    public DeckItemTouchHelper(DeckAdapater deckAdapater, RecyclerView recyclerView) {
        this.mDeckDrager = new DeckDrager(deckAdapater, recyclerView);
        this.deckAdapater = deckAdapater;
        int size = (int) deckAdapater.getContext().getResources().getDimension(R.dimen.drag_rect);
        this.dragRectPx = size;
        //长按手抖范围
        setDragSize(size, size);
        //长按时间
        setLongTime(Constants.LONG_PRESS_DRAG);
//        setDragSize();
    }

    /**
     * 登记“卡片已拖动起来”的瞬时回调（由卡组编辑页用它撤销本次手势误入的批量勾选模式）。
     */
    public void setOnDragMovedListener(Runnable listener) {
        this.onDragMovedListener = listener;
    }

    /**
     * 本次手势是否已经把卡片拖动起来（位移超过长按手抖容差），拖拽会话开始时重置。
     * <p>
     * 卡组网格上有两条并行的长按链：本类父类那一条（进入拖拽态后才起 500ms 定时器，且真实位移
     * 一旦超阈值就不再触发）与 {@code RecyclerViewItemListener} 的 GestureDetector 那一条（按下即起
     * 500ms 定时器）。单按即拖模下拖拽很早就被 ItemTouchHelper 接管，接管后
     * {@code RecyclerViewItemListener} 再也收不到任何后续事件，它的定时器不能取消，仍会按按下那一刻
     * 的旧坐标到点回调长按——于是“拖卡拖到一半突然进了批量勾选”正是这条链触发的。
     * 因此批量模式的入口必须先问这里。
     */
    public boolean isCardDragging() {
        return cardDragging;
    }

    private final int Min_Pos = -10;

    @Override
    public boolean canDropOver(RecyclerView recyclerView, RecyclerView.ViewHolder current,
                               RecyclerView.ViewHolder target) {
        int id = target.getAdapterPosition();
        if (isLongPressMode()) {
            return false;
        }
        return !DeckItemUtils.isLabel(id);
    }

    /**
     * 控制哪些可以拖拽
     */
    @Override
    public int getMovementFlags(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder) {
        int id = viewHolder.getAdapterPosition();
        // 人机/AI 与卡包展示/缓存分类的卡组只读：不给任何拖拽标志位，ItemTouchHelper 连本次手势
        // 都不 grab（hasDragFlag 为假即不会进入拖拽选择态），卡片既不能区内改序也不能跨区搬运；
        // 点击与详情展示不走这条链，仍照常可用
        if (!deckAdapater.isEditable()) {
            return makeMovementFlags(0, 0);
        }
        // 批量勾选模式下单卡拖拽语义让位给“整组拖动”（由 DeckBatchEditController 接管），
        // 这里一律返回 0 标志位，ItemTouchHelper 不会 grab 本次手势，
        // 点击与长按才能正常回到 RecyclerViewItemListener
        if (deckAdapater.isBatchMode()) {
            return makeMovementFlags(0, 0);
        }
        if (DeckItemUtils.isLabel(id)) {
            return makeMovementFlags(0, 0);
        }
        if (DeckItemUtils.isMain(id)) {
            if (id >= DeckItem.MainStart + deckAdapater.getMainCount()) {
                return makeMovementFlags(0, 0);
            }
        } else if (DeckItemUtils.isExtra(id)) {
            if (id >= DeckItem.ExtraStart + deckAdapater.getExtraCount()) {
                return makeMovementFlags(0, 0);
            }
        } else if (DeckItemUtils.isSide(id)) {
            if (id >= DeckItem.SideStart + deckAdapater.getSideCount()) {
                return makeMovementFlags(0, 0);
            }
        }
        int dragFlags;
        if (recyclerView.getLayoutManager() instanceof GridLayoutManager) {
            dragFlags = ItemTouchHelper.UP | ItemTouchHelper.DOWN | ItemTouchHelper.RIGHT | ItemTouchHelper.LEFT;
        } else {
            dragFlags = ItemTouchHelper.UP | ItemTouchHelper.DOWN;
        }
        return makeMovementFlags(dragFlags, 0);
    }

    @Override
    public RecyclerView.ViewHolder chooseDropTarget(RecyclerView.ViewHolder selected,
                                                    List<RecyclerView.ViewHolder> dropTargets, int curX, int curY) {
        RecyclerView.ViewHolder viewHolder = super.chooseDropTarget(selected, dropTargets, curX, curY);
        if (viewHolder != null) {
            int id = viewHolder.getAdapterPosition();
            if (viewHolder instanceof DeckViewHolder) {
                DeckViewHolder deckholder = (DeckViewHolder) viewHolder;
                if (deckholder.getItemType() == DeckItemType.MainLabel
                        || deckholder.getItemType() == DeckItemType.SideLabel
                        || deckholder.getItemType() == DeckItemType.ExtraLabel) {
//                Log.d("kk", "move is label or space " + id);
                    return null;
                }
            } else {
                if (DeckItemUtils.isLabel(id)) {
//                Log.d("kk", "move is label " + id);
                    if (isLongPressMode()) {
                        return viewHolder;
                    }
                    return null;
                }
            }
        }
        return viewHolder;
    }

    @Override
    public void onSelectedChanged(RecyclerView.ViewHolder viewHolder, int actionState) {
        super.onSelectedChanged(viewHolder, actionState);
        if (actionState == ACTION_STATE_DRAG) {
            // 新的拖拽会话开始：本次手势目前还没动过，长按语义允许成立
            cardDragging = false;
            mDeckDrager.onDragStart();
            if (Constants.DEBUG)
                Log.d("kk", "start drag");
        } else if (actionState == ACTION_STATE_IDLE) {
            // 会话收尾（含回弹动画）：这里不能把 cardDragging 抹回 false——
            // RecyclerViewItemListener 那条链的长按定时器是按上一次按下的时刻起算的，
            // 拖卡抬手后它仍可能在几百毫秒后到点；留着 true 才能让这次陈旧的长按回调被拒绝，
            // 下一次真正按下时会由上面的 ACTION_STATE_DRAG 分支当场重置
            mDeckDrager.onDragEnd();
        }
    }

    /**
     * 拖拽跟手期间的逐帧回调：{@code dX}/{@code dY} 即手指相对拖拽起点的位移。
     * 位移首次超过长按手抖容差就把本次手势定性为“拖卡”，并当场通知宿主撤销长按语义；
     * 抬手与回弹（{@code isCurrentlyActive == false}）不算，否则长按进模式后正常松手也会被抹掉。
     */
    @Override
    public void onChildDrawOver(Canvas c, RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder,
                                float dX, float dY, int actionState, boolean isCurrentlyActive) {
        // 先走父类：它在位移超阈值时就地退出长按模式并回调宿主的 onDragLongPressEnd
        super.onChildDrawOver(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive);
        if (!isCurrentlyActive || actionState != ACTION_STATE_DRAG || cardDragging) {
            return;
        }
        if (Math.abs(dX) <= dragRectPx && Math.abs(dY) <= dragRectPx) {
            return;
        }
        cardDragging = true;
        if (onDragMovedListener != null) {
            onDragMovedListener.run();
        }
    }

    @Override
    public boolean onMove(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, RecyclerView.ViewHolder target) {
        if (viewHolder.getAdapterPosition() < 0) {
            return false;
        }
        return mDeckDrager.move((DeckViewHolder) viewHolder, (DeckViewHolder) target);
    }

    public void remove(int id) {
        mDeckDrager.delete(id);
    }

    @Override
    public void onMoved(RecyclerView recyclerView, RecyclerView.ViewHolder viewHolder, int fromPos, RecyclerView.ViewHolder target, int toPos, int x, int y) {
        super.onMoved(recyclerView, viewHolder, fromPos, target, toPos, x, y);
    }

    @Override
    public void onSwiped(RecyclerView.ViewHolder viewHolder, int direction) {
    }
}
