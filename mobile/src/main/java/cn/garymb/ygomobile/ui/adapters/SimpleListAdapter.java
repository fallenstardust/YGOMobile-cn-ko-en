package cn.garymb.ygomobile.ui.adapters;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import cn.garymb.ygomobile.lite.R;

public class SimpleListAdapter extends BaseAdapterPlus<String> {

    private static final int SELECTED_BG_COLOR = 0x5587CEEB;
    private int selectedPosition = -1;

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
        if (position == selectedPosition) {
            textView.setBackgroundColor(SELECTED_BG_COLOR);
        } else {
            textView.setBackgroundColor(Color.TRANSPARENT);
        }
    }
}
