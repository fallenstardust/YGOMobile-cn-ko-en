package cn.garymb.ygomobile.ui.preference;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;

import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.activities.BaseActivity;
import cn.garymb.ygomobile.ui.activities.PermissionsActivity;
import cn.garymb.ygomobile.ui.preference.fragments.SettingFragment;

import static cn.garymb.ygomobile.ui.activities.PermissionsActivity.PERMISSIONS_GRANTED;

public class SettingsActivity extends BaseActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        Toolbar toolbar = $(R.id.toolbar);
        setSupportActionBar(toolbar);
        enableBackHome();
        getFragmentManager().beginTransaction().replace(R.id.fragment, new SettingFragment()).commit();
    }
}
