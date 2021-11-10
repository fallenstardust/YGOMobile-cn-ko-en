package com.ourygo.ygomobile.adapter;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;

import com.ourygo.ygomobile.bean.FragmentData;

import java.util.List;

/**
 * Create By feihua  On 2021/10/19
 */
public class FmPagerAdapter extends FragmentPagerAdapter {

    private List<FragmentData> data;

    public FmPagerAdapter(FragmentManager fm, List<FragmentData> data) {
        super(fm);
        this.data=data;
    }

    @Override
    public Fragment getItem(int position) {
        return data.get(position).getFragment();
    }

    @Override
    public int getCount() {
        return data.size();
    }

    @Nullable
    @Override
    public CharSequence getPageTitle(int position) {
        return data.get(position).getTitle();
    }
}

