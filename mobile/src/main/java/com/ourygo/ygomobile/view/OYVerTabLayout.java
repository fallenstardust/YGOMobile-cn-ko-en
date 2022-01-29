package com.ourygo.ygomobile.view;


import android.content.Context;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.widget.TextView;

import androidx.viewpager.widget.ViewPager;

import com.ourygo.ygomobile.adapter.FmPagerAdapter;
import com.ourygo.ygomobile.util.OYUtil;

import cn.garymb.ygomobile.lite.R;
import q.rorbin.verticaltablayout.VerticalTabLayout;
import q.rorbin.verticaltablayout.adapter.TabAdapter;
import q.rorbin.verticaltablayout.widget.QTabView;
import q.rorbin.verticaltablayout.widget.TabView;

public class OYVerTabLayout extends VerticalTabLayout {

    public static final int MODE_PROMINENT = 0;
    public static final int MODE_BACKGROUND = 1;

    private float selectTextSize = 24;
    private float normalTextSize = 14;
    private int showMode;

    private boolean isAugment = true;
    private FmPagerAdapter mPagerAdapter;
    private Context mContext;
    private ViewPager mViewPager;

    public OYVerTabLayout(Context context) {
        super(context);
//        showMode = MODE_PROMINENT;
//        updateTabSelection(getSelectedTabPosition());
//        addOnTabSelectedListener(new OnTabSelectedListener() {
//            @Override
//            public void onTabSelected(TabView tab, int position) {
//                updateTabSelection(position);
//            }
//
//            @Override
//            public void onTabReselected(TabView tab, int position) {
//
//            }
//        });
    }

    public OYVerTabLayout(Context context, AttributeSet attrs) {
        super(context, attrs);
//        showMode = MODE_PROMINENT;
//        updateTabSelection(getSelectedTabPosition());
//        addOnTabSelectedListener(new OnTabSelectedListener() {
//            @Override
//            public void onTabSelected(TabView tab, int position) {
//                updateTabSelection(position);
//            }
//
//            @Override
//            public void onTabReselected(TabView tab, int position) {
//
//            }
//        });
    }

    public OYVerTabLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
//        showMode = MODE_PROMINENT;
//        updateTabSelection(getSelectedTabPosition());
//        addOnTabSelectedListener(new OnTabSelectedListener() {
//            @Override
//            public void onTabSelected(TabView tab, int position) {
//                updateTabSelection(position);
//            }
//
//            @Override
//            public void onTabReselected(TabView tab, int position) {
//
//            }
//        });
    }

//    @Override
//    public void onPageSelected(int position) {
//        super.onPageSelected(position);
//        updateTabSelection(position);
//    }

//    @Override
//    public void setCurrentTab(int currentTab) {
//        super.setCurrentTab(currentTab);
//        updateTabSelection(currentTab);
//    }

    @Override
    public void setTabSelected(int position) {
        super.setTabSelected(position);
        updateTabSelection(position);
    }

    public void setTextSizeL() {
        selectTextSize = 24;
        normalTextSize = 14;
        updateTabSelection(getSelectedTabPosition());
    }

    public void setTextSizeM() {
        selectTextSize = 20;
        normalTextSize = 12;
        updateTabSelection(getSelectedTabPosition());
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

    private void populateFromPagerAdapter() {
        removeAllTabs();
        if (mPagerAdapter != null) {
            final int adapterCount = mPagerAdapter.getCount();
            if (mPagerAdapter instanceof TabAdapter) {
                setTabAdapter((TabAdapter) mPagerAdapter);
            } else {
                for (int i = 0; i < adapterCount; i++) {
                    String title = mPagerAdapter.getPageTitle(i) == null ? "tab" + i : mPagerAdapter.getPageTitle(i).toString();
                    addTab(new QTabView(mContext).setTitle(
                            new QTabView.TabTitle.Builder().setContent(title).build()).setBackground(R.drawable.click_window_background_radius));
                }
            }

            // Make sure we reflect the currently set ViewPager item
            if (mViewPager != null && adapterCount > 0) {
                final int curItem = mViewPager.getCurrentItem();
                if (curItem != getSelectedTabPosition() && curItem < getTabCount()) {
                    setTabSelected(curItem);
                }
            }
        } else {
            removeAllTabs();
        }
    }

    private void updateTabSelection(int position) {
        for (int i = 0; i < getTabCount(); ++i) {
            final boolean isSelect = i == position;
            TabView tabView=getTabAt(i);
            TextView tab_title = tabView.getTitleView();
//            tab_title.setPadding(OYUtil.dp2px(6), OYUtil.dp2px(3), OYUtil.dp2px(6), OYUtil.dp2px(3));
            if (tab_title == null)
                return;

//            Drawable back=tabView.getBackground();

//            tab_title.setTextSize(normalTextSize);
            tab_title.setTypeface(Typeface.DEFAULT);
            if (isSelect) {
                switch (showMode) {
                    case MODE_PROMINENT:
                        if (isAugment)
//                            tab_title.setTextSize(selectTextSize);
                        tab_title.setTypeface(Typeface.DEFAULT_BOLD);
                        break;
                    case MODE_BACKGROUND:
//                        tabView.setBackgroundResource(R.drawable.click_gray_light_radius);
//                        tab_title.setPadding(OYUtil.dp2px(10), OYUtil.dp2px(5), OYUtil.dp2px(10), OYUtil.dp2px(5));
                        break;
                }
//                tabView.setBackground(back);
            }else {
//                tabView.setBackgroundResource(R.drawable.click_window_background_radius);
            }
//            if (getTextBold() == 1) {
//                tab_title.getPaint().setFakeBoldText(isSelect);
//            }

        }
    }

}
