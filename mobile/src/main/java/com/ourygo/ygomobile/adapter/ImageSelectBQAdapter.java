package com.ourygo.ygomobile.adapter;

import android.graphics.Typeface;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.listener.OnItemClickListener;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.ourygo.ygomobile.base.listener.OnImageSelectListener;
import com.ourygo.ygomobile.base.listener.OnOYSelectListener;
import com.ourygo.ygomobile.bean.ImageSelectItem;
import com.ourygo.ygomobile.util.OYUtil;

import java.util.List;

import cn.garymb.ygomobile.lite.R;

/**
 * Create By feihua  On 2021/10/23
 */
public class ImageSelectBQAdapter extends BaseQuickAdapter<ImageSelectItem, BaseViewHolder> {
    private final int backgroundColor,selectBackgroundColor;
    private final int messageColor,selectMessageColor;
    private int selectPosition;
    private OnItemClickListener currentOnItemClickListener;
    private OnImageSelectListener onImageSelectListener;

    public ImageSelectBQAdapter(List<ImageSelectItem> data) {
        super(R.layout.image_select_item,data);
        selectPosition=-1;

        backgroundColor = R.drawable.normal_frame_background;
        selectBackgroundColor = R.drawable.select_frame_background;
        selectMessageColor = OYUtil.c(R.color.colorAccent);
        messageColor = OYUtil.c(R.color.gray);
        setItemClickListener();
    }

    @Override
    protected void convert(@NonNull BaseViewHolder baseViewHolder, ImageSelectItem imageSelectItem) {
        baseViewHolder.setText(R.id.tv_name, imageSelectItem.getName());
        baseViewHolder.setImageResource(R.id.iv_image, imageSelectItem.getImage());

        ImageView iv_image=baseViewHolder.getView(R.id.iv_image);
        if (selectPosition == -1) {
            iv_image.setBackgroundResource(backgroundColor);
            baseViewHolder.setTextColor(R.id.tv_name,messageColor);
            ((TextView)baseViewHolder.getView(R.id.tv_name)).setTypeface(Typeface.DEFAULT);
        } else {

            if (getItemPosition(imageSelectItem) == selectPosition) {
                iv_image.setBackgroundResource(selectBackgroundColor);
                iv_image.setImageAlpha(0xff);
                baseViewHolder.setTextColor(R.id.tv_name,selectMessageColor);
                ((TextView)baseViewHolder.getView(R.id.tv_name)).setTypeface(Typeface.DEFAULT_BOLD);
            } else {
                iv_image.setBackgroundResource(backgroundColor);
                iv_image.setImageAlpha((int) (0xff * 0.3));
                baseViewHolder.setTextColor(R.id.tv_name,messageColor);
                ((TextView)baseViewHolder.getView(R.id.tv_name)).setTypeface(Typeface.DEFAULT);
            }
        }

    }

    public void setOnItemClickListener(OnItemClickListener onItemClickListener) {
        this.currentOnItemClickListener = onItemClickListener;
    }

    private void setItemClickListener() {
        super.setOnItemClickListener((adapter, view, position) -> {
            //未选中——选中
            setSelectPosition(position);

            if (currentOnItemClickListener != null)
                currentOnItemClickListener.onItemClick(adapter, view, position);
        });
    }

    public int getSelectPosttion() {
        return selectPosition;
    }

    public void setSelectPosition(int selectPosition) {
        int lastPosition = this.selectPosition;
        setSelectPosition(lastPosition,selectPosition);
    }

    public void setSelectPosition(int lastPosition,int selectPosition) {
        this.selectPosition = selectPosition;
        notifyDataSetChanged();
        if (onImageSelectListener != null)
            onImageSelectListener.onImageSelect(getItem(selectPosition), lastPosition, selectPosition);
    }


    public void setOnSelectListener(OnImageSelectListener onImageSelectListener) {
        this.onImageSelectListener = onImageSelectListener;
    }

}
