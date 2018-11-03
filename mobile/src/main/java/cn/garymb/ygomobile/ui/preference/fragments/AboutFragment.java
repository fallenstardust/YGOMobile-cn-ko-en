package cn.garymb.ygomobile.ui.preference.fragments;

import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.Preference;

import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.preference.PreferenceFragmentPlus;
import cn.garymb.ygomobile.utils.AlipayPayUtils;
import cn.garymb.ygomobile.utils.SystemUtils;

public class AboutFragment extends PreferenceFragmentPlus {
    @Override
    protected SharedPreferences getSharedPreferences() {
        return null;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(false);
        addPreferencesFromResource(R.xml.preference_about);
        PackageInfo packageInfo = null;
        try {
            packageInfo = getActivity().getPackageManager().getPackageInfo(getActivity().getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        bind("pref_key_about_version", (packageInfo == null) ? "?" : packageInfo.versionName);
        bind("pref_key_open_alipay");
        bind("pref_key_about_version");
        String text = SystemUtils.getVersionName(getActivity())
                + " (" + SystemUtils.getVersion(getActivity()) + ")";
        findPreference("pref_key_about_version").setSummary(text);
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        String key = preference.getKey();
        if ("pref_key_open_alipay".equals(key)) {
            AlipayPayUtils.openAlipayPayPage(getContext(), Constants.ALIPAY_URL);
        }
        return false;
    }
}
