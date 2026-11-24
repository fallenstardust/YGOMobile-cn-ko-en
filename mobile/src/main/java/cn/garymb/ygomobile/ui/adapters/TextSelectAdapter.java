package cn.garymb.ygomobile.ui.adapters;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.view.View;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.listener.OnItemClickListener;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;

import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.bean.TextSelect;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.utils.YGOUtil;

public class TextSelectAdapter<T extends TextSelect> extends BaseQuickAdapter<T, BaseViewHolder> {

    private OnItemSelectListener onItemSelectListener;
    private int selectPosition;
    private final boolean isSelect;
    private boolean isManySelect;
    private final List<T> selectList;
    /** 与条目一一对应的名称下方第二行文字（卡组分类列表用它显示该分类的卡组数量，如「(12)」）；
     *  null/空串/越界项不显示，条目回到单行高度，不影响不需要第二行的使用方 */
    private List<String> subTexts;

    public TextSelectAdapter(List<T> data, int select) {
        super(R.layout.text_select_item, data);
        this.selectPosition = select;
        isSelect = select >= 0;
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
                    onItemSelectListener.onItemSelect(position, data.get(position));
            }
        });
    }

    @SuppressLint("ResourceType")
    @Override
    protected void convert(BaseViewHolder helper, T item) {
        int position = getItemPosition(item);

        helper.setText(R.id.tv_name, item.getName());
        String sub = subTexts != null && position >= 0 && position < subTexts.size()
                ? subTexts.get(position) : null;
        boolean showSub = sub != null && !sub.isEmpty();
        helper.setVisible(R.id.tv_count, showSub);
        if (showSub) {
            helper.setText(R.id.tv_count, sub);
        }
        if (isManySelect) {
            if (selectList.contains(item))
                helper.setBackgroundColor(R.id.ll_layout, YGOUtil.c(R.color.colorMain));
            else
                helper.setBackgroundResource(R.id.ll_layout, Color.TRANSPARENT);
        } else if (isSelect) {
            if (position == selectPosition) {
                helper.setBackgroundColor(R.id.ll_layout, YGOUtil.c(R.color.colorMain));
            } else {
                helper.setBackgroundResource(R.id.ll_layout, Color.TRANSPARENT);
            }
        } else {
            helper.setBackgroundResource(R.id.ll_layout, Color.TRANSPARENT);
        }

    }

    public void setSelectPosition(int selectPosition) {
        this.selectPosition = selectPosition;
    }

    /**
     * 设置名称下方第二行文字（与条目一一对应）；调用方在同一列表上原地更新内容后
     * 再调一次本方法（或 notifyDataSetChanged）即可刷新
     */
    public void setSubTexts(List<String> subTexts) {
        this.subTexts = subTexts;
        notifyDataSetChanged();
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
