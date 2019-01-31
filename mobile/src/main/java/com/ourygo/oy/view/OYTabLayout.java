package com.ourygo.oy.view;


import android.content.Context;
import android.graphics.Typeface;
import android.util.AttributeSet;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.google.android.material.tabs.TabLayout;
import com.ourygo.oy.util.OYUtil;

import androidx.annotation.Nullable;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;
import cn.garymb.ygomobile.lite.R;

public class OYTabLayout extends TabLayout {

    private int selectTextSize=20;

    public OYTabLayout(Context context) {
        super(context);
        initTabLayout();
    }

    public OYTabLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
        initTabLayout();
    }

    public OYTabLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initTabLayout();
    }

    private void initTabLayout(){
        setSelectedTabIndicatorHeight(0);
        setTabMode(TabLayout.MODE_FIXED);
        setTabGravity(TabLayout.INDICATOR_GRAVITY_BOTTOM);
    }

    @Override
    public void setupWithViewPager(@Nullable ViewPager viewPager) {
        super.setupWithViewPager(viewPager);
        initTabTextStyle(viewPager.getAdapter());
    }

    public void initTabTextStyle(PagerAdapter fragmentPagerAdapter) {
        for (int i = 0; i < getTabCount(); i++) {
            TabLayout.Tab tab = getTabAt(i);
            if (tab != null) {
                if (fragmentPagerAdapter != null)
                    tab.setCustomView(getTabView(fragmentPagerAdapter.getPageTitle(i), tab.isSelected()));
                else
                    tab.setCustomView(getTabView(tab.getText(), tab.isSelected()));
            }
        }

        addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                TextView textView = tab.getCustomView().findViewById(R.id.text1);
                setTabText(textView, true);
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
                TextView textView = tab.getCustomView().findViewById(R.id.text1);
                setTabText(textView, false);
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {

            }
        });
    }

    public void setSelectTextSize(int textSize){
        this.selectTextSize=textSize;
        for (int i = 0; i < getTabCount(); i++) {
            TabLayout.Tab tab = getTabAt(i);
            if (tab != null) {
                TextView textView = tab.getCustomView().findViewById(R.id.text1);
                setTabText(textView, tab.isSelected());
            }
        }
    }

    private View getTabView(CharSequence title, boolean isSelecd) {
        View view = LayoutInflater.from(getContext()).inflate(R.layout.tab_layout_text, null);
        TextView textView = view.findViewById(R.id.text1);
        textView.setText(title);
        setTabText(textView, isSelecd);
        return view;
    }

    private void setTabText(TextView textView, boolean isSelecd) {
        textView.setGravity(Gravity.BOTTOM);
        if (isSelecd) {
            textView.setTextColor(OYUtil.c(R.color.colorAccentDark));
            textView.setTextSize(selectTextSize);
            textView.setTypeface(Typeface.DEFAULT_BOLD);
        } else {
            textView.setTextColor(OYUtil.c(R.color.colorAccent));
            textView.setTextSize(13);
            textView.setTypeface(Typeface.DEFAULT);
        }
    }


}
