package com.ourygo.oy.ui.activity;

import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import com.google.android.material.tabs.TabLayout;
import com.ourygo.oy.bean.FragmentData;
import com.ourygo.oy.ui.fragment.MainFragment;
import com.ourygo.oy.ui.fragment.YGOServerFragemnt;
import com.ourygo.oy.util.OYUtil;
import com.ourygo.oy.view.OYTabLayout;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.fragment.app.FragmentTransaction;
import androidx.viewpager.widget.ViewPager;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.activities.BaseActivity;

public class MainActivity extends BaseActivity {

    private static final String TAG="TIME-MainActivity";

    private Toolbar toolbar;
    private OYTabLayout tl_tab;
    private ViewPager vp_pager;
    private List<FragmentData> fragmentList;

    private MainFragment mainFragment;
    private MyCardFragment myCardFragment;
    private OtherFunctionFragment otherFunctionFragment;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.mian_oy);

        Log.e(TAG,"1");
        initView();
        Log.e(TAG,"2");
    }

    private void initView() {
//        toolbar=findViewById(R.id.toolbar);
//        OYUtil.initToolbar(this,toolbar,"YGOMobile",false);

        tl_tab=findViewById(R.id.tl_tab);
        vp_pager=findViewById(R.id.vp_pager);

        mainFragment=new MainFragment();
        myCardFragment=new MyCardFragment();
        otherFunctionFragment=new OtherFunctionFragment();

        fragmentList=new ArrayList<>();

        fragmentList.add(FragmentData.toFragmentData(s(R.string.homepage),mainFragment));
        fragmentList.add(FragmentData.toFragmentData(s(R.string.mycard),myCardFragment));
        fragmentList.add(FragmentData.toFragmentData(s(R.string.other_funstion),otherFunctionFragment));

        vp_pager.setAdapter(new FmPagerAdapter(getSupportFragmentManager()));
        tl_tab.setTabMode(TabLayout.MODE_FIXED);
        //TabLayout加载viewpager
        tl_tab.setupWithViewPager(vp_pager);
        //缓存两个页面
        vp_pager.setOffscreenPageLimit(3);

        tl_tab.setSelectTextSize(25);

//        YGOServerFragemnt ygoFragemnt=new YGOServerFragemnt();
//        FragmentManager fragmentManager = getSupportFragmentManager();
//        FragmentTransaction transaction = fragmentManager. beginTransaction();
//        transaction.replace(R.id.fragment, ygoFragemnt);
//        transaction.commit();
    }

    class FmPagerAdapter extends FragmentPagerAdapter{

        public FmPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            return fragmentList.get(position).getFragment();
        }

        @Override
        public int getCount() {
            return fragmentList.size();
        }

        @Nullable
        @Override
        public CharSequence getPageTitle(int position) {
            return fragmentList.get(position).getTitle();
        }
    }

}
