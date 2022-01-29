package com.ourygo.ygomobile.bean;

import androidx.fragment.app.Fragment;

public class FragmentData {
    public static final int ICON_NULL = -1;


    private Fragment fragment;
    private String title;

    private int icon;


    public FragmentData() {
        this.icon = ICON_NULL;
    }

    public static FragmentData toFragmentData(String title, Fragment fragment) {
       return toFragmentData(title,ICON_NULL,fragment);
    }

    public static FragmentData toFragmentData(String title,int icon, Fragment fragment) {
        FragmentData fragmentData = new FragmentData();
        fragmentData.setTitle(title);
        fragmentData.setIcon(icon);
        fragmentData.setFragment(fragment);
        return fragmentData;
    }

    public Fragment getFragment() {
        return fragment;
    }

    public void setFragment(Fragment fragment) {
        this.fragment = fragment;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public int getIcon() {
        return icon;
    }

    public void setIcon(int icon) {
        this.icon = icon;
    }
}
