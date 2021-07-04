package com.ourygo.ygomobile.ui.activity;

import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentPagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.tabs.TabLayout;
import com.ourygo.ygomobile.bean.FragmentData;
import com.ourygo.ygomobile.ui.fragment.MainFragment;
import com.ourygo.ygomobile.util.LogUtil;
import com.ourygo.ygomobile.util.MyCardUtil;
import com.ourygo.ygomobile.util.SdkInitUtil;
import com.ourygo.ygomobile.util.SharedPreferenceUtil;
import com.ourygo.ygomobile.view.OYTabLayout;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.activities.BaseActivity;
import cn.garymb.ygomobile.ui.home.ResCheckTask;
import cn.garymb.ygomobile.ui.mycard.mcchat.util.ImageUtil;
import cn.garymb.ygomobile.utils.FileLogUtil;
import cn.garymb.ygomobile.utils.ScreenUtil;

public class OYMainActivity extends BaseActivity {

    private static final String TAG = "TIME-MainActivity";

    private Toolbar toolbar;
    private OYTabLayout tl_tab;
    private ViewPager vp_pager;
    private ImageView iv_avatar;
    private TextView tv_name;

    private List<FragmentData> fragmentList;

    private MainFragment mainFragment;
    private MyCardFragment myCardFragment;
    private OtherFunctionFragment otherFunctionFragment;
    private ResCheckTask mResCheckTask;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mian_oy);
        LogUtil.time(TAG,"0");
        new Thread(new Runnable() {
            @Override
            public void run() {
                SdkInitUtil.getInstance().initX5WebView();
            }
        }).start();
        LogUtil.time(TAG,"1");
        initView();
        LogUtil.time(TAG,"2");
        checkNotch();
        LogUtil.time(TAG, "3");
        checkRes();
        LogUtil.time(TAG,"4");
        LogUtil.printSumTime(TAG);
    }

    protected void checkResourceDownload(ResCheckTask.ResCheckListener listener) {

        mResCheckTask = new ResCheckTask(this, listener);
        mResCheckTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
//        if (Build.VERSION.SDK_INT >= 11) {
//            mResCheckTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
//        } else {
//            mResCheckTask.execute();
//        }
    }

    private void checkNotch() {
        ScreenUtil.findNotchInformation(OYMainActivity.this, new ScreenUtil.FindNotchInformation() {
            @Override
            public void onNotchInformation(boolean isNotch, int notchHeight, int phoneType) {
                try {
                    FileLogUtil.writeAndTime("检查刘海" + isNotch + "   " + notchHeight);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                AppsSettings.get().setNotchHeight(notchHeight);
            }
        });
    }

    private void checkRes() {
        checkResourceDownload((error, isNew) -> {
//            if (error < 0) {
//                enableStart = false;
//            } else {
//                enableStart = true;
//            }
//            if (isNew) {
//                if (!getGameUriManager().doIntent(getIntent())) {
//                    DialogPlus dialog = new DialogPlus(this)
//                            .setTitleText(getString(R.string.settings_about_change_log))
//                            .loadUrl("file:///android_asset/changelog.html", Color.TRANSPARENT)
//                            .hideButton()
//                            .setOnCloseLinster((dlg) -> {
//                                dlg.dismiss();
//                                //mImageUpdater
//                                if (NETWORK_IMAGE && NetUtils.isConnected(getContext())) {
//                                    if (!mImageUpdater.isRunning()) {
//                                        mImageUpdater.start();
//                                    }
//                                }
//                            });
//                    dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
//                        @Override
//                        public void onDismiss(DialogInterface dialogInterface) {
//                            PermissionUtil.isServicePermission(cn.garymb.ygomobile.ui.home.MainActivity.this, true);
//
//                        }
//                    });
//                    dialog.show();
//                }
//            } else {
//                PermissionUtil.isServicePermission(cn.garymb.ygomobile.ui.home.MainActivity.this, true);
//                getGameUriManager().doIntent(getIntent());
//            }

        });
    }


    private void initView() {

        tl_tab = findViewById(R.id.tl_tab);
        vp_pager = findViewById(R.id.vp_pager);
        iv_avatar = findViewById(R.id.iv_avatar);
        tv_name = findViewById(R.id.tv_name);

        String mcName = SharedPreferenceUtil.getMyCardUserName();
        refreshMyCardUser(mcName);

        mainFragment = new MainFragment();
        myCardFragment = new MyCardFragment();
        otherFunctionFragment = new OtherFunctionFragment();

        fragmentList = new ArrayList<>();

        fragmentList.add(FragmentData.toFragmentData(s(R.string.homepage), mainFragment));
        fragmentList.add(FragmentData.toFragmentData(s(R.string.mycard), myCardFragment));
        fragmentList.add(FragmentData.toFragmentData(s(R.string.other_funstion), otherFunctionFragment));

        vp_pager.setAdapter(new FmPagerAdapter(getSupportFragmentManager()));
        tl_tab.setTabMode(TabLayout.MODE_FIXED);
        //TabLayout加载viewpager
        tl_tab.setupWithViewPager(vp_pager);
        //缓存两个页面
        vp_pager.setOffscreenPageLimit(3);

        tl_tab.setSelectTextSize(22);

//        YGOServerFragemnt ygoFragemnt=new YGOServerFragemnt();
//        FragmentManager fragmentManager = getSupportFragmentManager();
//        FragmentTransaction transaction = fragmentManager. beginTransaction();
//        transaction.replace(R.id.fragment, ygoFragemnt);
//        transaction.commit();
    }

    public void refreshMyCardUser(String name) {
        if (TextUtils.isEmpty(name)) {
            tv_name.setText(getString(R.string.no_login));
            iv_avatar.setImageResource(R.drawable.avatar);
            iv_avatar.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    vp_pager.setCurrentItem(1);
                }
            });
            tv_name.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    vp_pager.setCurrentItem(1);
                }
            });
        } else {
            tv_name.setText(name);
            ImageUtil.setImage(this, MyCardUtil.getAvatarUrl(name), iv_avatar);
        }
    }

    class FmPagerAdapter extends FragmentPagerAdapter {

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
