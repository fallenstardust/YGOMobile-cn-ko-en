package cn.garymb.ygomobile.ui.home;

import android.os.Bundle;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;


import cn.garymb.ygomobile.base.BaseFragemnt;

import cn.garymb.ygomobile.lite.R;


public class HomeFragment extends BaseFragemnt {


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        View layoutView;
        if (isHorizontal)
            layoutView = inflater.inflate(R.layout.main_horizontal_fragment, container, false);
        else
            layoutView = inflater.inflate(R.layout.main_fragment, container, false);

        //initView(layoutView, savedInstanceState);

        return layoutView;
    }


    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onFirstUserVisible() {

    }

    @Override
    public void onUserVisible() {

    }

    @Override
    public void onFirstUserInvisible() {

    }

    @Override
    public void onUserInvisible() {

    }

}
