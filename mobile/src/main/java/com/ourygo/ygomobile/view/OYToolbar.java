package com.ourygo.ygomobile.view;

import android.app.Activity;
import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import cn.garymb.ygomobile.lite.R;

public class OYToolbar extends LinearLayout {

   private ImageView iv_back;
    private TextView tv_title;

    public OYToolbar(Context context) {
        super(context);
        initView();
    }

    public OYToolbar(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initView();
    }

    public OYToolbar(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initView();
    }

    private void initView(){
        View v= LayoutInflater.from(getContext()).inflate(R.layout.oy_toolbar,this,false);
        iv_back=v.findViewById(R.id.iv_back);
        tv_title=v.findViewById(R.id.tv_title);
        iv_back.setOnClickListener(v1 -> {
            if (getContext() instanceof Activity){
                ((Activity)getContext()).finish();
            }
        });
        addView(v);
    }

    public void setTitle(String title){
        tv_title.setText(title);
    }

    public TextView getTitle(){
        return tv_title;
    }

    public void setOnclickListener(OnClickListener onclickListener){
        iv_back.setOnClickListener(onclickListener);
    }
}
