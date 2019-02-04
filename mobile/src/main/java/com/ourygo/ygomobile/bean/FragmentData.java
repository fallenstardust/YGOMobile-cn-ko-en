package com.ourygo.ygomobile.bean;

import androidx.fragment.app.Fragment;

public class FragmentData {

    private Fragment fragment;
    private String title;

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

    public  static FragmentData toFragmentData(String title, Fragment fragment){
        FragmentData fragmentData=new FragmentData();
        fragmentData.setTitle(title);
        fragmentData.setFragment(fragment);
        return fragmentData;
    }

}
