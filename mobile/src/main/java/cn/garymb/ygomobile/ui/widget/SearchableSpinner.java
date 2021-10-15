package cn.garymb.ygomobile.ui.widget;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.TypedArray;
import androidx.appcompat.widget.AppCompatSpinner;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.SpinnerAdapter;

import java.util.ArrayList;

import cn.garymb.ygomobile.lite.R;

public class SearchableSpinner extends AppCompatSpinner implements View.OnTouchListener,
        SearchableListDialog.onSearchItemClickListener {

    public static final int NO_ITEM_SELECTED = -1;
    private SearchableListDialog _searchableListDialog;

    private final ArrayList<Object> _items = new ArrayList<>();
    private boolean _isDirty;
    private BaseAdapter _arrayAdapter;
    private String _strHintText;
    private boolean _isFromInit;
    private String mTitleString = "Select Item";
    private int mFirstIndex = 0;

    public SearchableSpinner(Context context) {
        this(context, null);
    }

    public SearchableSpinner(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public SearchableSpinner(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.SearchableSpinner);
        if (a != null) {
            final int N = a.getIndexCount();
            for (int i = 0; i < N; ++i) {
                int attr = a.getIndex(i);
                if (attr == R.styleable.SearchableSpinner_hintText) {
                    _strHintText = a.getString(attr);
                } else if (attr == R.styleable.SearchableSpinner_searchTitle) {
                    mTitleString = a.getString(attr);
                }
            }
            a.recycle();
        }
        init();
    }

    private void init() {
        _searchableListDialog = new SearchableListDialog(getContext());
        _searchableListDialog.setTitle(mTitleString);
        _searchableListDialog.setOnSearchableItemClickListener(this);
        setOnTouchListener(this);

        _arrayAdapter = (BaseAdapter) getAdapter();
        if (!TextUtils.isEmpty(_strHintText)) {
            ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(getContext(),
                    android.R.layout.simple_list_item_1,
                    new String[]{_strHintText});
            _isFromInit = true;
            setAdapter(arrayAdapter);
        }
    }

    public void setFirstIndex(int firstIndex) {
        this.mFirstIndex = firstIndex;
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_UP) {
            if (null != _arrayAdapter) {
                //修复 重复点击 bug
                if(!_searchableListDialog.isShowing()) {
                    _items.clear();
                    int N = _arrayAdapter.getCount();
                    for (int i = mFirstIndex; i < N; i++) {
                        _items.add(_arrayAdapter.getItem(i));
                    }
                    _searchableListDialog.show(_items);
                }
            }
        }
        return true;
    }

    @Override
    public void setAdapter(SpinnerAdapter adapter) {
        if (!_isFromInit) {
            _arrayAdapter = (BaseAdapter) adapter;
            if (!TextUtils.isEmpty(_strHintText) && !_isDirty) {
                ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(getContext(), android.R.layout
                        .simple_list_item_1, new String[]{_strHintText});
                super.setAdapter(arrayAdapter);
            } else {
                super.setAdapter(adapter);
            }

        } else {
            _isFromInit = false;
            super.setAdapter(adapter);
        }
    }

    @Override
    public void onSearchableItemClicked(Object item, int position) {
        setSelection(_items.indexOf(item) + mFirstIndex);
        if (!_isDirty) {
            _isDirty = true;
            setAdapter(_arrayAdapter);
            setSelection(_items.indexOf(item));
        }
    }

    public void setTitle(String strTitle) {
        _searchableListDialog.setTitle(strTitle);
    }

    public void setOnSearchTextChangedListener(SearchableListDialog.OnSearchTextChanged onSearchTextChanged) {
        _searchableListDialog.setOnSearchTextChangedListener(onSearchTextChanged);
    }

    private Activity scanForActivity(Context cont) {
        if (cont == null)
            return null;
        else if (cont instanceof Activity)
            return (Activity) cont;
        else if (cont instanceof ContextWrapper)
            return scanForActivity(((ContextWrapper) cont).getBaseContext());
        return null;
    }

    @Override
    public int getSelectedItemPosition() {
        if (!TextUtils.isEmpty(_strHintText) && !_isDirty) {
            return NO_ITEM_SELECTED;
        } else {
            return super.getSelectedItemPosition();
        }
    }

    @Override
    public Object getSelectedItem() {
        if (!TextUtils.isEmpty(_strHintText) && !_isDirty) {
            return null;
        } else {
            return super.getSelectedItem();
        }
    }
}