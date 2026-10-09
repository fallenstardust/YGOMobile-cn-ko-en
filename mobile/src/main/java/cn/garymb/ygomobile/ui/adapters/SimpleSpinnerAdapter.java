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
    private Integer dropDownBgColor;
    private Integer dropDownBgRes;

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

    // 设置下拉项背景颜色（与setDropDownBackgroundResource互斥，后设置的生效）
    public void setDropDownBackgroundColor(int color) {
        this.dropDownBgColor = color;
        this.dropDownBgRes = null;
    }

    // 设置下拉项背景drawable资源（与setDropDownBackgroundColor互斥，后设置的生效）
    public void setDropDownBackgroundResource(int resId) {
        this.dropDownBgRes = resId;
        this.dropDownBgColor = null;
    }

    @Override
    protected View createView(int position, ViewGroup parent) {
        View view = inflate(R.layout.item_simple_spinner, parent, false);
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
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        if (convertView == null) {
            convertView = inflate(R.layout.item_simple_spinner_dropdown, parent, false);
            TextView textView = convertView.findViewById(android.R.id.text1);
            convertView.setTag(textView);
            textView.setMaxLines(maxLines);
            textView.setTextSize(textSize);
            if (singleLine) {
                textView.setSingleLine();
            }
        }
        if (dropDownBgRes != null) {
            convertView.setBackgroundResource(dropDownBgRes);
        } else if (dropDownBgColor != null) {
            convertView.setBackgroundColor(dropDownBgColor);
        }
        attach(convertView, getItem(position), position);
        return convertView;
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
