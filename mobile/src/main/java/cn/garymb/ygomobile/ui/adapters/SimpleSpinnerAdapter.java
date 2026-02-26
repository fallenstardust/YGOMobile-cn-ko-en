package cn.garymb.ygomobile.ui.adapters;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Spinner;
import android.widget.TextView;

import cn.garymb.ygomobile.lite.R;


public class SimpleSpinnerAdapter extends BaseAdapterPlus<SimpleSpinnerItem> {
    private int color;
    private int maxLines = 2;
    private boolean singleLine = false;
    private float textSize = 14f; // 默认字体大小

    public SimpleSpinnerAdapter(Context context) {
        super(context);
        color = context.getResources().getColor(R.color.item_title);
    }

    public void setColor(int color) {
        this.color = color;
    }

    public void setSingleLine(boolean singleLine) {
        this.singleLine = singleLine;
    }

    public void setMaxLines(int maxLines) {
        this.maxLines = maxLines;
    }
    
    // 添加设置字体大小的方法
    public void setTextSize(float textSize) {
        this.textSize = textSize;
    }

    @Override
    protected View createView(int position, ViewGroup parent) {
        View view = inflate(android.R.layout.simple_list_item_1, null);
        TextView textView = view.findViewById(android.R.id.text1);
        view.setTag(textView);
        textView.setMaxLines(maxLines);
        textView.setTextSize(textSize); // 设置字体大小
        if (singleLine) {
            textView.setSingleLine();
        }
        return view;
    }

    @Override
    protected void attach(View view, SimpleSpinnerItem item, int position) {
        TextView textView = (TextView) view.getTag();
        textView.setTextColor(color);
        textView.setMaxLines(maxLines);
        textView.setTextSize(textSize); // 确保更新时也应用字体大小
        if (item != null) {
            textView.setText(item.toString());
        }
    }

    public static Object getSelectTag(Spinner spinner) {
        if (spinner.getCount() > 0) {
            Object item = spinner.getSelectedItem();
            if (item != null && item instanceof SimpleSpinnerItem) {
                SimpleSpinnerItem spItem = (SimpleSpinnerItem) item;
                return spItem.tag;
            }
        }
        return null;
    }

    public static String getSelectText(Spinner spinner) {
        if (spinner.getCount() > 0) {
            Object item = spinner.getSelectedItem();
            if (item != null && item instanceof SimpleSpinnerItem) {
                SimpleSpinnerItem spItem = (SimpleSpinnerItem) item;
                return spItem.text;
            }
        }
        return null;
    }

    public static long getSelect(Spinner spinner) {
        if (spinner.getCount() > 0) {
            Object item = spinner.getSelectedItem();
            if (item != null && item instanceof SimpleSpinnerItem) {
                SimpleSpinnerItem spItem = (SimpleSpinnerItem) item;
                return spItem.value;
            }
        }
        return 0;
    }
}
