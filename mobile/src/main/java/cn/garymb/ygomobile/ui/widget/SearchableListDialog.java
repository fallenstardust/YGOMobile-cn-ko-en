package cn.garymb.ygomobile.ui.widget;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.SearchManager;
import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.google.android.flexbox.FlexboxLayout;

import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.plus.DialogPlus;

public class SearchableListDialog extends DialogPlus implements
        SearchView.OnQueryTextListener, SearchView.OnCloseListener {

    private final ArrayAdapter<Object> listAdapter;
    private onSearchItemClickListener _onSearchItemClickListener;
    private OnSearchTextChanged _onSearchTextChanged;
    private final SearchView _searchView;
    private final List<Object> items = new ArrayList<>();
    private final FlexboxLayout tagsContainer;

    public SearchableListDialog(Context context) {
        super(context);
        LayoutInflater inflater = LayoutInflater.from(context);
        View rootView = inflater.inflate(R.layout.searchable_list_dialog, null);
        SearchManager searchManager = (SearchManager) context.getSystemService(Context.SEARCH_SERVICE);
        _searchView = rootView.findViewById(R.id.search);
        if (context instanceof Activity) {
            _searchView.setSearchableInfo(searchManager
                    .getSearchableInfo(((Activity) context).getComponentName()));
        }
        _searchView.setIconifiedByDefault(false);
        _searchView.setOnQueryTextListener(this);
        _searchView.setOnCloseListener(this);
        _searchView.clearFocus();
        int id = _searchView.getContext().getResources().getIdentifier("android:id/search_src_text", null, null);
        TextView textView1 = _searchView.findViewById(id);
        if (textView1 != null) {
            textView1.setTextColor(getContext().getResources().getColor(R.color.search_text_color));
        }

        tagsContainer = rootView.findViewById(R.id.tagsContainer);

        ListView _listViewItems = rootView.findViewById(R.id.listItems);

        listAdapter = new ArrayAdapter<Object>(context, android.R.layout.simple_list_item_1, items) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView textView = (TextView) super.getView(position, convertView, parent);
                textView.setTextColor(getContext().getResources().getColor(R.color.search_list_item_color));
                return textView;
            }
        };

        //attach the adapter to the list
        _listViewItems.setAdapter(listAdapter);
        _listViewItems.setTextFilterEnabled(true);
        _listViewItems.setOnItemClickListener((parent, view, position, id1) -> {
            if (_onSearchItemClickListener != null) {
                _onSearchItemClickListener.onSearchableItemClicked(listAdapter.getItem(position), position);
            }
            // 添加选中的项目为标签
            addTagToList(listAdapter.getItem(position).toString());
            //this.dismiss();
        });
        setContentView(rootView);
        hideButton();
    }


    public void addTagToListFrom(String tagText) {
        addTagToList(tagText);
    }

    private void addTagToList(String tagText) {
        if (tagText != null && !tagText.trim().isEmpty()) {
            // 检查是否已存在相同的标签
            for (int i = 0; i < tagsContainer.getChildCount(); i++) {
                View existingTagView = tagsContainer.getChildAt(i);
                if (existingTagView instanceof LinearLayout) {
                    LinearLayout tagLayout = (LinearLayout) existingTagView;
                    if (tagLayout.getChildCount() >= 1 &&
                            tagLayout.getChildAt(0) instanceof TextView) {
                        TextView existingTagText = (TextView) tagLayout.getChildAt(0);
                        if (tagText.equals(existingTagText.getText().toString())) {
                            // 如果标签已存在，将其移动到第一位
                            tagsContainer.removeView(tagLayout);
                            tagsContainer.addView(tagLayout, 0);
                            return; // 直接返回，不再创建新标签
                        }
                    }
                }
            }

            // 创建标签容器布局
            LinearLayout tagLayout = new LinearLayout(getContext());
            tagLayout.setOrientation(LinearLayout.HORIZONTAL);
            tagLayout.setBackgroundResource(R.drawable.selected); // 需要创建标签背景
            tagLayout.setPadding(8, 4, 8, 4);

            // 创建标签文本
            TextView tagView = new TextView(getContext());
            tagView.setText(tagText);
            tagView.setTextSize(16);
            tagView.setPadding(8, 0, 8, 0);
            tagView.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));

            // 创建关闭图标
            TextView closeIcon = new TextView(getContext());
            closeIcon.setText("×");
            closeIcon.setTextSize(14);
            closeIcon.setPadding(8, 0, 4, 0);
            closeIcon.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));

            // 添加删除功能
            closeIcon.setOnClickListener(v -> {
                tagsContainer.removeView(tagLayout);
            });

            // 将文本和关闭图标添加到标签容器
            tagLayout.addView(tagView);
            tagLayout.addView(closeIcon);

            // 将新标签添加到第一位
            tagsContainer.addView(tagLayout, 0);
        }
    }


    @Override
    public void show() {
        super.show();
        getWindow().setSoftInputMode(WindowManager.LayoutParams
                .SOFT_INPUT_STATE_HIDDEN);
    }

    public void show(List<Object> items) {
        //隐藏输入法
        InputMethodManager mgr = (InputMethodManager) getContext().getSystemService(Context
                .INPUT_METHOD_SERVICE);
        mgr.hideSoftInputFromWindow(_searchView.getWindowToken(), 0);
        //设置列表
        this.items.clear();
        this.items.addAll(items);
        listAdapter.notifyDataSetChanged();
        show();
    }

    public void setOnSearchableItemClickListener(onSearchItemClickListener onSearchItemClickListener) {
        this._onSearchItemClickListener = onSearchItemClickListener;
    }

    public void setOnSearchTextChangedListener(OnSearchTextChanged onSearchTextChanged) {
        this._onSearchTextChanged = onSearchTextChanged;
    }

    @Override
    public boolean onClose() {
        return false;
    }

    @Override
    public boolean onQueryTextSubmit(String s) {
        _searchView.clearFocus();
        return true;
    }

    @Override
    public boolean onQueryTextChange(String s) {
        if (TextUtils.isEmpty(s)) {
            listAdapter.getFilter().filter(null);
        } else {
            listAdapter.getFilter().filter(s);
        }
        if (null != _onSearchTextChanged) {
            _onSearchTextChanged.onSearchTextChanged(s);
        }
        return true;
    }

    public interface onSearchItemClickListener {
        void onSearchableItemClicked(Object item, int position);
    }

    public interface OnSearchTextChanged {
        void onSearchTextChanged(String strText);
    }
}