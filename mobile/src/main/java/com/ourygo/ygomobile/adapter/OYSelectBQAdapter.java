package com.ourygo.ygomobile.adapter;

import android.graphics.Typeface;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.listener.OnItemClickListener;
import com.chad.library.adapter.base.viewholder.BaseViewHolder;
import com.ourygo.ygomobile.base.listener.OnOYSelectListener;
import com.ourygo.ygomobile.bean.OYSelect;
import com.ourygo.ygomobile.util.OYUtil;

import java.util.List;

import cn.garymb.ygomobile.lite.R;

public class OYSelectBQAdapter extends BaseQuickAdapter<OYSelect, BaseViewHolder> {

    private boolean isShowTitle = true;
    private boolean isShowMessage = true;
    private int titleSize = 12;
    private int messageSize = 10;
    private int selectPosition;
    private int messageColor = -1;
    private int backgroundColor;
    private int selectBackgroundColor;
    private int layoutGravity = Gravity.CENTER;
    private int layoutWidth = 0;
    private int layoutHeight = 0;
    private boolean isMessageBold;
    private OnOYSelectListener onOYselectListener;
    private OnItemClickListener currentOnItemClickListener;

    public OYSelectBQAdapter(@Nullable List<OYSelect> data) {
        super(R.layout.oy_select_item, data);
//        backgroundColor = OYUtil.getRadiusBackground(OYUtil.c(R.color.grayLight));
        backgroundColor = R.drawable.click_gray_light_radius;
//        selectBackgroundColor = OYUtil.getRadiusBackground(OYUtil.c(R.color.grayDark2));
        selectBackgroundColor = R.drawable.click_gray_dark_radius;
        messageColor = OYUtil.c(R.color.blackLight);
        selectPosition = -1;
        setItemClickListener();
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

    @Override
    protected void convert(BaseViewHolder helper, OYSelect item) {
        helper.setText(R.id.tv_title, item.getName());
        helper.setText(R.id.tv_message, item.getMessage());

        TextView tv_title, tv_message;
        tv_message = helper.getView(R.id.tv_message);
        tv_title = helper.getView(R.id.tv_title);

        LinearLayout ll_layout = helper.getView(R.id.ll_layout);
        ll_layout.setGravity(layoutGravity);
        LinearLayout cv_card = helper.getView(R.id.ll_layout);
        if (selectPosition == -1) {
            cv_card.setBackgroundResource(backgroundColor);
        } else {
            if (helper.getAdapterPosition() == selectPosition) {
                cv_card.setBackgroundResource(selectBackgroundColor);
            } else {
                cv_card.setBackgroundResource(backgroundColor);
            }
        }

        if (isShowTitle) {
            tv_title.setVisibility(View.VISIBLE);
            tv_title.setTextSize(titleSize);
        } else
            tv_title.setVisibility(View.GONE);

        if (isShowMessage) {
            tv_message.setVisibility(View.VISIBLE);
            tv_message.setTextSize(messageSize);
        } else
            tv_message.setVisibility(View.GONE);

        tv_message.setTextColor(messageColor);

        if (isMessageBold)
            tv_message.setTypeface(Typeface.DEFAULT_BOLD);
        else
            tv_message.setTypeface(Typeface.DEFAULT);

        ViewGroup.LayoutParams layoutParams = ll_layout.getLayoutParams();

        if (layoutWidth == 0)
            layoutParams.width = LinearLayout.LayoutParams.WRAP_CONTENT;
        else
            layoutParams.width = layoutWidth;

        if (layoutHeight == 0)
            layoutParams.height = LinearLayout.LayoutParams.WRAP_CONTENT;
        else
            layoutParams.height = layoutHeight;

        ll_layout.setLayoutParams(layoutParams);
    }

    public void setLayoutSize(int width, int height) {
        layoutWidth = width;
        layoutHeight = height;
    }

    public void setMessageColor(int color) {
        messageColor = color;
    }

    public void setMessageBold(boolean isBold) {
        this.isMessageBold = isBold;
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
        if (onOYselectListener != null)
            onOYselectListener.onOYSelect(getItem(selectPosition), lastPosition, selectPosition);
    }

    public void setOnSelectListener(OnOYSelectListener onOYSelectListener) {
        this.onOYselectListener = onOYSelectListener;
    }

    public void setSelectBackground(int drawable) {
        selectBackgroundColor = drawable;
    }

    public void setLayoutGravity(int layoutGravity) {
        this.layoutGravity = layoutGravity;
    }

    public void setBackground(int drawable) {
        this.backgroundColor = drawable;
    }

    public void setTitleSize(int size) {
        titleSize = size;
    }

    public void setMessageSize(int size) {
        messageSize = size;
    }

    public void showTitle() {
        isShowTitle = true;
    }

    public void showMessage() {
        isShowMessage = true;

    }

    public void hideTitle() {
        isShowTitle = false;
    }

    public void hideMessage() {
        isShowMessage = false;
    }
}
