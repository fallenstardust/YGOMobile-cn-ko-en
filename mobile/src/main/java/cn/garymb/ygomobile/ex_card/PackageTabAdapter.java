package cn.garymb.ygomobile.ex_card;

import android.app.Activity;
import android.content.Context;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;

import com.google.android.material.tabs.TabLayout;

import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.activities.WebActivity;
import cn.garymb.ygomobile.ui.home.HomeActivity;

/**
 * Tab的适配器，用来实现页面切换
 */
public class PackageTabAdapter extends FragmentStatePagerAdapter {
    TabLayout tabLayout;
    public PackageTabAdapter(FragmentManager fm, TabLayout _tabLayout) {
        super(fm);
        this.tabLayout = _tabLayout;
    }

    @Override
    public Fragment getItem(int position) {
        Fragment fragment = null;
        if (position == 0)
        {
            fragment = new ExCardListFragment();//TODO
        }
        else if (position == 1)
        {
            fragment = new ExCardLogFragment();
        }
        return fragment;
    }

    @Override
    public int getCount() {
        return 2;
    }

    @Override
    public CharSequence getPageTitle(int position) {
        String title = null;
        if (position == 0)
        {
            title = "先行卡";//TODO
        }
        else if (position == 1)
        {
            title = "更新日志";
        }
        return title;
    }
}
