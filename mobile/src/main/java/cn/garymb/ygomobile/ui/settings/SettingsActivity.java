package cn.garymb.ygomobile.ui.settings;

import android.os.Bundle;

import androidx.annotation.Nullable;

import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.activities.BaseActivity;
import cn.garymb.ygomobile.ui.settings.fragments.SettingFragment;

public class SettingsActivity extends BaseActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        getSupportActionBar().hide();
        enableBackHome();
        getFragmentManager().beginTransaction().replace(R.id.fragment, new SettingFragment()).commit();
    }
}
