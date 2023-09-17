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
        viewPager.setOffscreenPageLimit(2);
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

}
