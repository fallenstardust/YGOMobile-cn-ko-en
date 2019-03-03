package com.ourygo.ygomobile.adapter;

import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.BaseViewHolder;
import com.ourygo.ygomobile.base.listener.OnOYSelectListener;
import com.ourygo.ygomobile.bean.OYSelect;
import com.ourygo.ygomobile.bean.YGOServer;
import com.ourygo.ygomobile.util.OYUtil;

import java.util.List;

import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import cn.garymb.ygomobile.lite.R;

public class OYSelectBQAdapter extends BaseQuickAdapter<OYSelect, BaseViewHolder> {

    private boolean isShowTitle = true;
    private boolean isShowMessage = true;
    private int titleSize = 12;
    private int messageSize = 10;
    private int selectPosttion;
    private int messageColor=-1;
    private int backgroundColor;
    private int selectBackgroundColor;
    private int layoutGravity = Gravity.CENTER;
    private int layoutWidth = 0;
    private int layoutHeight = 0;
    private boolean isMessageBold;
    private OnOYSelectListener onOYselectListener;

    public OYSelectBQAdapter(@Nullable List<OYSelect> data) {
        super(R.layout.oy_select_item, data);
        backgroundColor = OYUtil.c(R.color.colorAccentLight);
        selectBackgroundColor = OYUtil.c(R.color.black);
        messageColor=OYUtil.c(R.color.colorAccent);
        selectPosttion = -1;
        setItemClickListener();
    }

    private void setItemClickListener() {
        setOnItemClickListener(new BaseQuickAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(BaseQuickAdapter adapter, View view, int position) {
                //未选中——选中
                if (position != selectPosttion) {
                    setSelectPosition(position);
                    notifyDataSetChanged();
                }
            }
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
        CardView cv_card=helper.getView(R.id.cv_card);
        if (selectPosttion == -1) {
           cv_card.setCardBackgroundColor(backgroundColor);
        } else {
            if (helper.getAdapterPosition() == selectPosttion) {
                cv_card.setCardBackgroundColor(selectBackgroundColor);
            } else {
                cv_card.setCardBackgroundColor(backgroundColor);
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

    public void setMessageColor(int color){
        messageColor=color;
    }


    public void setMessageBold(boolean isBold){
        this.isMessageBold=isBold;
    }
    public int getSelectPosttion() {
        return selectPosttion;
    }

    public void setSelectPosition(int selectPosition) {
        this.selectPosttion = selectPosition;
        if (onOYselectListener!=null)
        onOYselectListener.onOYSelect(getItem(selectPosition),selectPosition);
    }

    public void setSelectListener(OnOYSelectListener onOYSelectListener) {
        this.onOYselectListener=onOYSelectListener;
    }

    public void setSelectBackground(int color) {
        selectBackgroundColor = color;
    }

    public void setLayoutGravity(int layoutGravity) {
        this.layoutGravity = layoutGravity;
    }

    public void setBackground(int color) {
        this.backgroundColor = color;
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
