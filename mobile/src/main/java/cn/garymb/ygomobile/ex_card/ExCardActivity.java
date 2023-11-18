package cn.garymb.ygomobile.ex_card;

import android.os.Bundle;

import androidx.viewpager.widget.ViewPager;

import com.google.android.material.tabs.TabLayout;

import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.activities.BaseActivity;
import cn.garymb.ygomobile.utils.LogUtil;

public class ExCardActivity extends BaseActivity {
    private static final String TAG = String.valueOf(ExCardActivity.class);
    public static TabLayout tabLayout;
    public static ViewPager viewPager;
    private ExPackageTabAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ex_card);
        viewPager = findViewById(R.id.viewPager);
        viewPager.setOffscreenPageLimit(1);
        tabLayout = findViewById(R.id.packagetablayout);
        createTabFragment();

    }

    @Override
    protected void onDestroy() {

        super.onDestroy();
        LogUtil.i(TAG, "excard activity destroy");
    }

    private void createTabFragment() {
        adapter = new ExPackageTabAdapter(getSupportFragmentManager(), tabLayout, getContext());
        viewPager.setAdapter(adapter);
        /* setupWithViewPager() is used to link the TabLayout to the ViewPager */
        tabLayout.setupWithViewPager(viewPager);
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    //todo 当未下载完先行卡就退出页面时，会导致软件错误退出。未来通过监听返回事件，判断下载状态，若正在下载则阻拦返回键。
    //若发生错误或已完成，则不阻拦返回。
    @Override
    public void onBackPressed() {
        // 完全由自己控制返回键逻辑，系统不再控制，但是有个前提是：
        // 不要在Activity的onKeyDown或者OnKeyUp中拦截掉返回键

        // 拦截：就是在OnKeyDown或者OnKeyUp中自己处理了返回键
        //（这里处理之后return true.或者return false都会导致onBackPressed不会执行）

        // 不拦截：在OnKeyDown和OnKeyUp中返回super对应的方法
        //（如果两个方法都被覆写就分别都要返回super.onKeyDown,super.onKeyUp）
        super.onBackPressed();
    }

}
