package com.ourygo.ygomobile.view;


import android.content.Context;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.TextView;

import com.flyco.tablayout.SlidingTabLayout;
import com.ourygo.ygomobile.util.OYUtil;

import cn.garymb.ygomobile.lite.R;

public class OYTabLayout extends SlidingTabLayout {

    private float selectTextSize = 24;
    private float normalTextSize=14;

    private boolean isAugment = true;

    public OYTabLayout(Context context) {
        super(context);
        Log.e("SCTablayout", "初始选择" + getCurrentTab());
        updateTabSelection(getCurrentTab());
    }

    public OYTabLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        Log.e("SCTablayout",
                "初始选择1" + getCurrentTab());
        updateTabSelection(getCurrentTab());
    }

    public OYTabLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        Log.e("SCTablayout", "初始选择2" + getCurrentTab());
        updateTabSelection(getCurrentTab());
    }


    @Override
    public void onPageSelected(int position) {
        super.onPageSelected(position);
        updateTabSelection(position);
    }

    @Override
    public void setCurrentTab(int currentTab) {
        super.setCurrentTab(currentTab);
        updateTabSelection(currentTab);
    }

    public void setTextSizeL(){
        selectTextSize=24;
        normalTextSize=14;
        updateTabSelection(getCurrentTab());
    }

    public void setTextSizeM(){
        selectTextSize=20;
        normalTextSize=12;
        updateTabSelection(getCurrentTab());
    }

    public boolean isAugment() {
        return isAugment;
    }

    public void setAugment(boolean augment) {
        isAugment = augment;
    }

    private void updateTabSelection(int position) {

        for (int i = 0; i < getTabCount(); ++i) {
            final boolean isSelect = i == position;
            TextView tab_title = getTitleView(i);
            tab_title.setPadding(OYUtil.dp2px(6), OYUtil.dp2px(3),OYUtil.dp2px(6),OYUtil.dp2px(3));
            Log.e("SCTablayout", "是否为空" + (tab_title == null));
            if (tab_title != null) {
                tab_title.setBackgroundResource(R.drawable.click_window_background_radius);
                if (isSelect) {
//                    tab_title.setTextColor(SCUtil.c(R.color.colorAccentDark));
                    if (isAugment)
                        tab_title.setTextSize(selectTextSize);
                    tab_title.setTypeface(Typeface.DEFAULT_BOLD);
                } else {
//                    tab_title.setTextColor(SCUtil.c(R.color.colorAccent));
                    if (isAugment)
                        tab_title.setTextSize(14);
                    tab_title.setTypeface(Typeface.DEFAULT);
                }
                if (getTextBold() == 1) {
                    tab_title.getPaint().setFakeBoldText(isSelect);
                }
//                tab_title.setTextColor(isSelect ? mTextSelectColor : mTextUnselectColor);
//                if (mTextBold == TEXT_BOLD_WHEN_SELECT) {
//                    tab_title.getPaint().setFakeBoldText(isSelect);
//                }
            }
        }
    }

}
