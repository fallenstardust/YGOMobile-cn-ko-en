package com.ourygo.ygomobile.view;


import android.content.Context;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.widget.TextView;

import com.flyco.tablayout.SlidingTabLayout;
import com.ourygo.ygomobile.util.OYUtil;

import cn.garymb.ygomobile.lite.R;

public class OYTabLayout extends SlidingTabLayout {

    public static final int MODE_PROMINENT = 0;
    public static final int MODE_BACKGROUND = 1;

    private float selectTextSize = 24;
    private float normalTextSize = 14;
    private int showMode;

    private boolean isAugment = true;

    public OYTabLayout(Context context) {
        super(context);
        showMode = MODE_PROMINENT;
        updateTabSelection(getCurrentTab());
    }

    public OYTabLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        showMode = MODE_PROMINENT;
        updateTabSelection(getCurrentTab());
    }

    public OYTabLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        showMode = MODE_PROMINENT;
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

    public void setTextSizeL() {
        selectTextSize = 24;
        normalTextSize = 14;
        updateTabSelection(getCurrentTab());
    }

    public void setTextSizeM() {
        selectTextSize = 20;
        normalTextSize = 12;
        updateTabSelection(getCurrentTab());
    }

    public boolean isAugment() {
        return isAugment;
    }

    public void setAugment(boolean augment) {
        isAugment = augment;
    }

    public void setShowMode(int showMode) {
        this.showMode = showMode;
    }

    private void updateTabSelection(int position) {
        for (int i = 0; i < getTabCount(); ++i) {
            final boolean isSelect = i == position;
            TextView tab_title = getTitleView(i);
            tab_title.setPadding(OYUtil.dp2px(6), OYUtil.dp2px(3), OYUtil.dp2px(6), OYUtil.dp2px(3));
            if (tab_title == null)
                return;

            tab_title.setBackgroundResource(R.drawable.click_window_background_radius);

            tab_title.setTextSize(normalTextSize);
            tab_title.setTypeface(Typeface.DEFAULT);
            if (isSelect) {
                switch (showMode) {
                    case MODE_PROMINENT:
                        if (isAugment)
                            tab_title.setTextSize(selectTextSize);
                        tab_title.setTypeface(Typeface.DEFAULT_BOLD);
                        break;
                    case MODE_BACKGROUND:
                        tab_title.setBackgroundResource(R.drawable.click_gray_light_radius);
                        tab_title.setPadding(OYUtil.dp2px(10), OYUtil.dp2px(5), OYUtil.dp2px(10), OYUtil.dp2px(5));
                        break;
                }

            }
            if (getTextBold() == 1) {
                tab_title.getPaint().setFakeBoldText(isSelect);
            }

        }
    }

}
