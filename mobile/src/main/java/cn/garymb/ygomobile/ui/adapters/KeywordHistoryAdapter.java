package cn.garymb.ygomobile.ui.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.util.List;

import cn.garymb.ygomobile.lite.R;

/**
 * 关键词历史记录下拉适配器：在每条记录右侧显示删除图标，点击图标删除该条记录，
 * 点击记录本身仍按原逻辑填入关键词。供 AutoCompleteTextView 的下拉列表使用。
 */
public class KeywordHistoryAdapter extends ArrayAdapter<String> {

    /**
     * 某条关键词记录被点击删除时的回调
     */
    public interface OnKeywordDeleteListener {
        void onDelete(String keyword);
    }

    private final int itemLayoutRes;
    private final OnKeywordDeleteListener deleteListener;

    /**
     * 使用默认的关键词记录布局 {@code R.layout.item_keyword_history}
     */
    public KeywordHistoryAdapter(Context context, List<String> objects,
                                 OnKeywordDeleteListener deleteListener) {
        this(context, R.layout.item_keyword_history, objects, deleteListener);
    }

    public KeywordHistoryAdapter(Context context, int resource, List<String> objects,
                                 OnKeywordDeleteListener deleteListener) {
        super(context, resource, android.R.id.text1, objects);
        this.itemLayoutRes = resource;
        this.deleteListener = deleteListener;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        return buildItemView(position, convertView, parent);
    }

    @Override
    public View getDropDownView(int position, View convertView, ViewGroup parent) {
        return buildItemView(position, convertView, parent);
    }

    private View buildItemView(int position, View convertView, ViewGroup parent) {
        View view = convertView;
        if (view == null) {
            view = LayoutInflater.from(parent.getContext())
                    .inflate(itemLayoutRes, parent, false);
        }
        final String keyword = getItem(position);
        TextView text = view.findViewById(android.R.id.text1);
        if (text != null) {
            text.setText(keyword);
        }
        View deleteBtn = view.findViewById(R.id.iv_delete_keyword);
        if (deleteBtn != null) {
            deleteBtn.setOnClickListener(v -> {
                if (deleteListener != null && keyword != null) {
                    deleteListener.onDelete(keyword);
                }
            });
        }
        return view;
    }
}