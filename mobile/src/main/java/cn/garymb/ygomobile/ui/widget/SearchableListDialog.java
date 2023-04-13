package cn.garymb.ygomobile.ui.widget;

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
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.TextView;

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

    public SearchableListDialog(Context context) {
        super(context);
        LayoutInflater inflater = LayoutInflater.from(context);
        View rootView = inflater.inflate(R.layout.searchable_list_dialog, null);
        SearchManager searchManager = (SearchManager)context.getSystemService(Context.SEARCH_SERVICE);
        _searchView = rootView.findViewById(R.id.search);
        if(context instanceof Activity) {
            _searchView.setSearchableInfo(searchManager
                    .getSearchableInfo(((Activity)context).getComponentName()));
        }
        _searchView.setIconifiedByDefault(false);
        _searchView.setOnQueryTextListener(this);
        _searchView.setOnCloseListener(this);
        _searchView.clearFocus();
        int id = _searchView.getContext().getResources().getIdentifier("android:id/search_src_text", null, null);
        TextView textView1 = _searchView.findViewById(id);
        if(textView1 != null) {
            textView1.setTextColor(getContext().getResources().getColor(R.color.search_text_color));
        }
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
            if(_onSearchItemClickListener != null) {
                _onSearchItemClickListener.onSearchableItemClicked(listAdapter.getItem(position), position);
            }
            this.dismiss();
        });
        setContentView(rootView);
        hideButton();
    }

    @Override
    public void show() {
        super.show();
        getWindow().setSoftInputMode(WindowManager.LayoutParams
                .SOFT_INPUT_STATE_HIDDEN);
    }

    public void show(List<Object> items){
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