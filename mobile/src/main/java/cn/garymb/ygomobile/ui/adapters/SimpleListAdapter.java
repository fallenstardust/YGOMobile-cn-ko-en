package cn.garymb.ygomobile.ui.adapters;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.util.HashSet;
import java.util.Set;

import cn.garymb.ygomobile.lite.R;

public class SimpleListAdapter extends BaseAdapterPlus<String> {

    private static final int SELECTED_BG_COLOR = 0x5587CEEB;
    private int selectedPosition = -1;
    private Set<Integer> multiSelectedPositions = new HashSet<>();
    private boolean isMultiSelectMode = false;

    public SimpleListAdapter(Context context) {
        super(context);
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

    @Override
    protected View createView(int position, ViewGroup parent) {
        View view = inflate(R.layout.item_bot_list, null);
        TextView textView = view.findViewById(R.id.tv_bot_item);
        view.setTag(textView);
        return view;
    }

    @Override
    protected void attach(View view, String item, int position) {
        TextView textView = (TextView) view.getTag();
        if (item != null) {
            textView.setText(item);
        }
        
        boolean isSelected;
        if (isMultiSelectMode) {
            isSelected = multiSelectedPositions.contains(position);
        } else {
            isSelected = position == selectedPosition;
        }
        
        if (isSelected) {
            textView.setBackgroundColor(SELECTED_BG_COLOR);
        } else {
            textView.setBackgroundColor(Color.TRANSPARENT);
        }
    }
}
