package cn.garymb.ygomobile.ui.adapters;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import cn.garymb.ygomobile.lite.R;

public class SimpleListAdapter extends BaseAdapterPlus<String> {
    public SimpleListAdapter(Context context) {
        super(context);
    }

    @Override
    protected View createView(int position, ViewGroup parent) {
        View view = inflate(android.R.layout.simple_list_item_1, null);
        TextView textView = (TextView) view.findViewById(android.R.id.text1);
        textView.setTextColor(context.getResources().getColor(R.color.item_title));
        view.setTag(textView);
        return view;
    }

    @Override
    protected void attach(View view, String item, int position) {
        TextView textView = (TextView) view.getTag();
        if (item != null) {
            textView.setText(item);
        }
    }
}
