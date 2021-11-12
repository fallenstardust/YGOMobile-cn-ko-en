package com.ourygo.ygomobile.ui.fragment;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.ourygo.ygomobile.base.listener.OnMcUserListener;
import com.ourygo.ygomobile.util.McUserManagement;
import com.ourygo.ygomobile.util.OYUtil;
import com.ourygo.ygomobile.util.StatUtil;

import cn.garymb.ygomobile.base.BaseFragemnt;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.mycard.bean.McUser;

/**
 * Create By feihua  On 2021/10/20
 */
public class McLayoutFragment extends BaseFragemnt implements OnMcUserListener {

    private MyCardWebFragment myCardWebFragment;
    private MyCardFragment myCardFragment;
    private FragmentManager fragmentManager;
    private int currentPosition;
    private Fragment currentFragment;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.mycard_layout_fragment, container, false);
        initView(view);
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        StatUtil.onResume(getClass().getName());
    }

    @Override
    public void onPause() {
        super.onPause();
        StatUtil.onPause(getClass().getName());
    }

    private void initView(View view) {
        myCardFragment = new MyCardFragment();
        myCardWebFragment = new MyCardWebFragment();
        fragmentManager = getChildFragmentManager();

        myCardFragment.onMcLayout(this);
        myCardWebFragment.onMcLayout(this);

    }


    private void initData() {
        initFragment();
    }

    private void initFragment(){
        currentFragment = myCardWebFragment;
        fragmentManager.beginTransaction().add(R.id.fm_mc, currentFragment).commit();
        McUserManagement.getInstance().addListener(this);
    }

    public void setCurrentFragment(int position) {
        if (position > 1)
            position = 1;
        currentPosition = position;
        Fragment f3 = null;
        FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        switch (position) {
            case 0:
                f3 = myCardWebFragment;
                break;
            case 1:
                f3 = myCardFragment;
                break;
        }
        if (!f3.isAdded()) {
            // 先判断是否被add过
            fragmentTransaction.hide(currentFragment).add(R.id.fm_mc, f3).commitAllowingStateLoss(); // 隐藏当前的fragment，add下一个到Activity中
        } else {
            fragmentTransaction.hide(currentFragment).show(f3).commitAllowingStateLoss(); // 隐藏当前的fragment，显示下一个
        }
        currentFragment = f3;

    }


    @Override
    public void onFirstUserVisible() {
        initData();
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

    @Override
    public void onLogin(McUser user, String exception) {

    }

    @Override
    public void onLogout() {
        setCurrentFragment(0);
    }

    @Override
    public boolean isListenerEffective() {
        return OYUtil.isContextExisted(getActivity());
    }
}
