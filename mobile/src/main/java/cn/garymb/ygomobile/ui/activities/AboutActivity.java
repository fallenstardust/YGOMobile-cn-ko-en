package cn.garymb.ygomobile.ui.activities;

import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;

import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.preference.fragments.AboutFragment;

public class AboutActivity extends BaseActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        Toolbar toolbar = $(R.id.toolbar);
        setSupportActionBar(toolbar);
        enableBackHome();
        getFragmentManager().beginTransaction().replace(R.id.fragment, new AboutFragment()).commit();
    }
}
