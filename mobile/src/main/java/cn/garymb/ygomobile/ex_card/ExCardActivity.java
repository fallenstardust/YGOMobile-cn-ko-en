package cn.garymb.ygomobile.ex_card;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;

import androidx.appcompat.widget.Toolbar;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.tabs.TabLayout;

import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.activities.BaseActivity;

public class ExCardActivity extends BaseActivity {
    private Context context;
    private Toolbar toolbar;
    public static TabLayout tabLayout;
    public static ViewPager viewPager;
    private PackageTabAdapter adapter;

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
        Log.i("webCrawler", "excard activity destroy");
    }
    private void createTabFragment() {
        adapter = new PackageTabAdapter(getSupportFragmentManager(), tabLayout);
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
