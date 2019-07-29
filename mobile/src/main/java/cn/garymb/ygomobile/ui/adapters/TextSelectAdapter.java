package cn.garymb.ygomobile.ui.adapters;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.view.View;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;

import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.bean.TextSelect;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.utils.YGOUtil;

public class TextSelectAdapter<T extends TextSelect> extends BaseQuickAdapter<T, BaseViewHolder> {

    private OnItemSelectListener onItemSelectListener;
    private int selectPosition;
    private boolean isSelect;
    private boolean isManySelect;
    private List<T> selectList;

    public TextSelectAdapter(List<T> data, int select) {
        super(R.layout.text_select_item, data);
        this.selectPosition = select;
        if (select >= 0)
            isSelect = true;
        else
            isSelect = false;
        isManySelect = false;
        selectList = new ArrayList<>();
        setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                if (isSelect && position == selectPosition)
                    return;
                selectPosition = position;
                notifyDataSetChanged();
                if (onItemSelectListener != null)
                    onItemSelectListener.onItemSelect(position, data.get(position).getObject());
            }
        });
    }

    @SuppressLint("ResourceType")
    @Override
    protected void convert(BaseViewHolder helper, T item) {
        int position = helper.getAdapterPosition();

        helper.setText(R.id.tv_name, item.getName());
        if (isManySelect) {
            if (selectList.contains(item))
                helper.setBackgroundColor(R.id.ll_layout, YGOUtil.c(R.color.colorMain));
            else
                helper.setBackgroundRes(R.id.ll_layout, Color.TRANSPARENT);
        } else if (isSelect) {
            if (position == selectPosition) {
                helper.setBackgroundColor(R.id.ll_layout, YGOUtil.c(R.color.colorMain));
            } else {
                helper.setBackgroundRes(R.id.ll_layout, Color.TRANSPARENT);
            }
        }else {
            helper.setBackgroundRes(R.id.ll_layout, Color.TRANSPARENT);
        }

    }

    public void setSelectPosition(int selectPosition) {
        this.selectPosition = selectPosition;
    }

    public boolean isSelect() {
        return isSelect;
    }

    public boolean isManySelect() {
        return isManySelect;
    }

    public void addManySelect(T t) {
        if (selectList.contains(t))
            selectList.remove(t);
        else
            selectList.add(t);
    }

    public void setManySelect(boolean manySelect) {
        isManySelect = manySelect;
        if (!isManySelect) {
            selectList.clear();
            notifyDataSetChanged();
        }
    }

    public List<T> getSelectList() {
        return selectList;
    }

    public int getSelectPosition() {
        return selectPosition;
    }

    public void setOnItemSelectListener(OnItemSelectListener onItemSelectListener) {
        this.onItemSelectListener = onItemSelectListener;
    }

    public interface OnItemSelectListener<T> {
        void onItemSelect(int position, T item);
    }

}
