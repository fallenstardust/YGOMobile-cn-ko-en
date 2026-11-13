package cn.garymb.ygomobile.ui.adapters;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.utils.YGOUtil;

public class SimpleListAdapter extends BaseAdapterPlus<String> {

    private static final int SELECTED_BG_COLOR = YGOUtil.c(R.color.list_selected_sky);
    private int selectedPosition = -1;
    /** 列表项字号（sp），<=0 表示沿用 item_bot_list 的默认字号 */
    private float textSize = 0f;
    private Set<Integer> multiSelectedPositions = new HashSet<>();
    private boolean isMultiSelectMode = false;
    /** 与条目一一对应的尾部右对齐标记；非 null 即启用 item_bot_list_marked 条目布局 */
    private List<String> trailingMarks;

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

    @Override
    protected View createView(int position, ViewGroup parent) {
        boolean marked = trailingMarks != null;
        View view = inflate(marked ? R.layout.item_bot_list_marked : R.layout.item_bot_list, null);
        ItemHolder holder = new ItemHolder();
        holder.textView = view.findViewById(R.id.tv_bot_item);
        holder.markView = view.findViewById(R.id.tv_bot_item_mark);
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
    }

    /** 条目视图缓存：名称列 + 可选尾部标记列 */
    private static class ItemHolder {
        TextView textView;
        TextView markView;
    }
}
