package com.ourygo.ygomobile.ui.fragment;

import android.os.Bundle;
import android.util.Log;
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

    private static final String ARG_MC_WEB = "mcWeb";
    private static final String ARG_MC = "mc";
    private static final String ARG_CURRENT_FRAGMENT = "currentFragment";
    private MyCardWebFragment myCardWebFragment;
    private MyCardFragment myCardFragment;
    private FragmentManager fragmentManager;
    private int currentPosition;
    private Fragment currentFragment;
    private Bundle currentSaveBundle;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.mycard_layout_fragment, container, false);
        this.currentSaveBundle=savedInstanceState;
        initView(view, savedInstanceState);
        return view;
    }

    private void initView(View view, Bundle saveBundle) {
        fragmentManager = getChildFragmentManager();
        if (saveBundle != null) {
            myCardFragment = (MyCardFragment) fragmentManager.getFragment(saveBundle, ARG_MC);
            myCardWebFragment = (MyCardWebFragment) fragmentManager.getFragment(saveBundle, ARG_MC_WEB);
            currentFragment=fragmentManager.getFragment(saveBundle,ARG_CURRENT_FRAGMENT);
        }

        if (myCardFragment == null)
            myCardFragment = new MyCardFragment();
        if (myCardWebFragment == null)
            myCardWebFragment = new MyCardWebFragment();


        myCardFragment.onMcLayout(this);
        myCardWebFragment.onMcLayout(this);

    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        if (myCardWebFragment != null && myCardWebFragment.isAdded())
            fragmentManager.putFragment(outState, ARG_MC_WEB, myCardWebFragment);
        if (myCardFragment != null && myCardFragment.isAdded())
            fragmentManager.putFragment(outState, ARG_MC, myCardFragment);
        if (currentFragment != null && currentFragment.isAdded())
            fragmentManager.putFragment(outState, ARG_CURRENT_FRAGMENT, currentFragment);
        super.onSaveInstanceState(outState);
    }

    private void initData() {
        if (currentSaveBundle==null||currentFragment==null) {
            setCurrentFragment(0);
        }
    }

    private void initFragment() {
        Log.e("MCLayoutFragment","初始化Fragment");
        currentFragment = myCardWebFragment;
        fragmentManager.beginTransaction().add(R.id.fm_mc, currentFragment).commit();
        McUserManagement.getInstance().addListener(this);
    }

    public void setCurrentFragment(int position) {
        Log.e("MCLayoutFragment","设置fragment"+position);
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
            if (currentFragment == null)
                initFragment();
            else
                // 先判断是否被add过
                fragmentTransaction.hide(currentFragment).add(R.id.fm_mc, f3).commitAllowingStateLoss(); // 隐藏当前的fragment，add下一个到Activity中
        } else {
            Log.e("MCLayoutFragment","切换"+(currentFragment!=null));
            fragmentTransaction
                    .hide(currentFragment)
                    .show(f3)
                    .commitAllowingStateLoss(); // 隐藏当前的fragment，显示下一个
        }
        currentFragment = f3;

    }


    @Override
    public void onFirstUserVisible() {
        super.onFirstUserVisible();
        initData();
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
