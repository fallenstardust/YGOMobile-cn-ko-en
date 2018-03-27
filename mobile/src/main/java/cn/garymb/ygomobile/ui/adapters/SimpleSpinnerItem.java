package cn.garymb.ygomobile.ui.adapters;


public class SimpleSpinnerItem {
    public long value;
    public String text;
    public Object tag;

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

    @Override
    public String toString() {
        return text;
    }
}
