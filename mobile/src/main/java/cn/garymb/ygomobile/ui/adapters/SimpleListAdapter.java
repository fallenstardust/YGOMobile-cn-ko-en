package cn.garymb.ygomobile.ui.adapters;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.utils.YGOUtil;

public class SimpleListAdapter extends BaseAdapterPlus<String> {

    /** 星标点击回调：item 为条目文本，position 为列表显示位置 */
    public interface OnFavoriteClickListener {
        void onFavoriteClick(String item, int position);
    }

    private static final int SELECTED_BG_COLOR = YGOUtil.c(R.color.list_selected_sky);
    private int selectedPosition = -1;
    /** 列表项字号（sp），<=0 表示沿用 item_bot_list 的默认字号 */
    private float textSize = 0f;
    private Set<Integer> multiSelectedPositions = new HashSet<>();
    private boolean isMultiSelectMode = false;
    /** 与条目一一对应的尾部右对齐标记；非 null 即启用 item_bot_list_marked 条目布局 */
    private List<String> trailingMarks;
    /** 已收藏条目名；非 null 即启用 item_bot_list_favorite 条目布局（左侧星标列） */
    private Set<String> favoriteNames;
    private OnFavoriteClickListener onFavoriteClickListener;

    public SimpleListAdapter(Context context) {
        super(context);
    }

    /** 设置列表项字号（sp），仅影响使用本适配器的列表 */
    public void setTextSize(float textSize) {
        this.textSize = textSize;
    }

    public void setSelectedPosition(int position) {
        this.selectedPosition = position;
        notifyDataSetChanged();
    }

    public int getSelectedPosition() {
        return selectedPosition;
    }

    public void setMultiSelectMode(boolean multiSelect) {
        this.isMultiSelectMode = multiSelect;
        if (!multiSelect) {
            multiSelectedPositions.clear();
        }
        notifyDataSetChanged();
    }

    public boolean isMultiSelectMode() {
        return isMultiSelectMode;
    }

    public void toggleSelection(int position) {
        if (isMultiSelectMode) {
            if (multiSelectedPositions.contains(position)) {
                multiSelectedPositions.remove(position);
            } else {
                multiSelectedPositions.add(position);
            }
            notifyDataSetChanged();
        }
    }

    public void setItemSelected(int position, boolean selected) {
        if (isMultiSelectMode) {
            if (selected) {
                multiSelectedPositions.add(position);
            } else {
                multiSelectedPositions.remove(position);
            }
            notifyDataSetChanged();
        }
    }

    public boolean isItemSelected(int position) {
        if (isMultiSelectMode) {
            return multiSelectedPositions.contains(position);
        }
        return position == selectedPosition;
    }

    public Set<Integer> getMultiSelectedPositions() {
        return new HashSet<>(multiSelectedPositions);
    }

    public void clearMultiSelection() {
        multiSelectedPositions.clear();
        notifyDataSetChanged();
    }

    /**
     * 设置与条目一一对应的尾部右对齐标记（如人机 TAG 席位 ②③④）：空串或越界项不显示。
     * 启用后条目布局固定为带标记列的 item_bot_list_marked，标记为空时该列 GONE，观感不变；
     * 须在首次布局前调用一次，避免列表复用池里混用两种条目布局。
     */
    public void setTrailingMarks(List<String> marks) {
        this.trailingMarks = marks;
        notifyDataSetChanged();
    }

    /**
     * 设置已收藏条目名（按条目文本匹配）：启用左侧收藏星标列，须在首次布局前调用一次，
     * 避免列表复用池里混用两种条目布局。传 null 关闭星标列。
     */
    public void setFavoriteNames(Set<String> names) {
        this.favoriteNames = names;
        notifyDataSetChanged();
    }

    public void setOnFavoriteClickListener(OnFavoriteClickListener listener) {
        this.onFavoriteClickListener = listener;
    }

    /**
     * 一次性同步条目、尾部标记、单选位置与多选集合（收藏重排后全部位置都变了，需原子刷新），只刷新一次。
     */
    public void setDisplayState(List<String> items, List<String> marks, int selectedPosition,
                               Collection<Integer> multiSelected) {
        set(items);
        this.trailingMarks = marks;
        this.selectedPosition = selectedPosition;
        multiSelectedPositions.clear();
        if (multiSelected != null) multiSelectedPositions.addAll(multiSelected);
        notifyDataSetChanged();
    }

    @Override
    protected View createView(int position, ViewGroup parent) {
        int layout;
        if (favoriteNames != null) {
            layout = R.layout.item_bot_list_favorite;
        } else if (trailingMarks != null) {
            layout = R.layout.item_bot_list_marked;
        } else {
            layout = R.layout.item_bot_list;
        }
        View view = inflate(layout, null);
        ItemHolder holder = new ItemHolder();
        holder.textView = view.findViewById(R.id.tv_bot_item);
        holder.markView = view.findViewById(R.id.tv_bot_item_mark);
        holder.starCell = view.findViewById(R.id.fl_bot_item_star);
        holder.starView = view.findViewById(R.id.iv_bot_item_star);
        if (textSize > 0f) {
            holder.textView.setTextSize(textSize);
        }
        view.setTag(holder);
        return view;
    }

    @Override
    protected void attach(View view, String item, int position) {
        ItemHolder holder = (ItemHolder) view.getTag();
        TextView textView = holder.textView;
        if (item != null) {
            textView.setText(item);
        }
        if (holder.markView != null) {
            String mark = trailingMarks != null && position < trailingMarks.size()
                    ? trailingMarks.get(position) : null;
            boolean showMark = mark != null && !mark.isEmpty();
            holder.markView.setVisibility(showMark ? View.VISIBLE : View.GONE);
            if (showMark) {
                holder.markView.setText(mark);
            }
        }

        boolean isSelected;
        if (isMultiSelectMode) {
            isSelected = multiSelectedPositions.contains(position);
        } else {
            isSelected = position == selectedPosition;
        }

        int bg = isSelected ? SELECTED_BG_COLOR : Color.TRANSPARENT;
        textView.setBackgroundColor(bg);
        if (holder.markView != null) {
            // 标记列同色，保证选中高亮整行连续
            holder.markView.setBackgroundColor(bg);
        }
        if (holder.starView != null && favoriteNames != null) {
            // 星标单元同色，保证选中高亮整行连续
            if (holder.starCell != null) holder.starCell.setBackgroundColor(bg);
            boolean favorite = item != null && favoriteNames.contains(item);
            holder.starView.setImageResource(favorite
                    ? R.drawable.ic_bot_favorite_star_on : R.drawable.ic_bot_favorite_star_off);
            holder.starView.setOnClickListener(v -> {
                if (onFavoriteClickListener != null) {
                    onFavoriteClickListener.onFavoriteClick(item, position);
                }
            });
        }
    }

    /** 条目视图缓存：可选左侧星标列 + 名称列 + 可选尾部标记列 */
    private static class ItemHolder {
        TextView textView;
        TextView markView;
        View starCell;
        ImageView starView;
    }
}
