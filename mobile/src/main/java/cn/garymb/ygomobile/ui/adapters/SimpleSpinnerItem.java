package cn.garymb.ygomobile.ui.adapters;


import androidx.annotation.NonNull;

public class SimpleSpinnerItem {
    public long value;
    public String text;
    public transient Object tag;

    public SimpleSpinnerItem(long value, String text) {
        this.value = value;
        this.text = text;
    }

    public Object getTag() {
        return tag;
    }

    public SimpleSpinnerItem setTag(Object tag) {
        this.tag = tag;
        return this;
    }

    @NonNull
    @Override
    public String toString() {
        return "" + text;
    }
}
