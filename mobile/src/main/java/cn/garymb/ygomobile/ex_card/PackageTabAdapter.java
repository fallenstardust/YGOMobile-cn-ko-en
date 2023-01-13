package cn.garymb.ygomobile.ex_card;

import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentStatePagerAdapter;
import com.google.android.material.tabs.TabLayout;

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
        Log.i("webCrawler", "getItem");
        Fragment fragment = null;
        if (position == 0)
        {
            fragment = new ExCardFragment();//TODO
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
            title = "Domestic";
        }
        else if (position == 1)
        {
            title = "International";
        }
        return title;
    }



}
