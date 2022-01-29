package com.ourygo.ygomobile.adapter;

import android.graphics.Typeface;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.ourygo.ygomobile.bean.FragmentData;
import com.ourygo.ygomobile.bean.VerTab;
import com.ourygo.ygomobile.util.OYUtil;

import java.util.List;

import cn.garymb.ygomobile.lite.R;

/**
 * Create By feihua  On 2022/1/24
 */
public class VerTabBQAdapter extends BaseQuickAdapter<FragmentData, BaseViewHolder> {
    private int selectPosition;

    public VerTabBQAdapter(List<FragmentData> data) {
        super(R.layout.ver_tab_item, data);
        selectPosition=0;
    }


    @Override
    protected void convert(@NonNull BaseViewHolder baseViewHolder, FragmentData verTab) {
        if (selectPosition==baseViewHolder.getAdapterPosition()){
            baseViewHolder.setBackgroundResource(R.id.ll_background,R.drawable.click_accent_background_radius);
            baseViewHolder.setTextColor(R.id.tv_title, OYUtil.c(R.color.colorAccent));
            ((TextView)baseViewHolder.getView(R.id.tv_title)).setTypeface(Typeface.DEFAULT_BOLD);
            ((ImageView)baseViewHolder.getView(R.id.iv_icon)).setColorFilter(OYUtil.c(R.color.colorAccent));
        }else {
            baseViewHolder.setBackgroundResource(R.id.ll_background,R.drawable.click_background_radius);
            baseViewHolder.setTextColor(R.id.tv_title, OYUtil.c(R.color.blackLight));
            ((TextView)baseViewHolder.getView(R.id.tv_title)).setTypeface(Typeface.DEFAULT);
            ((ImageView)baseViewHolder.getView(R.id.iv_icon)).setColorFilter(OYUtil.c(R.color.blackLight));
        }
        baseViewHolder.setText(R.id.tv_title,verTab.getTitle());
        if (verTab.getIcon()==VerTab.ICON_NULL){
            baseViewHolder.setGone(R.id.iv_icon,true);
        }else {
            baseViewHolder.setGone(R.id.iv_icon,false);
            baseViewHolder.setImageResource(R.id.iv_icon,verTab.getIcon());
        }
    }

    public void setSelectPosition(int selectPosition) {
        int lastPosition=this.selectPosition;
        this.selectPosition = selectPosition;
        notifyItemChanged(selectPosition);
        notifyItemChanged(lastPosition);
    }

    public int getSelectPosition() {
        return selectPosition;
    }
}
