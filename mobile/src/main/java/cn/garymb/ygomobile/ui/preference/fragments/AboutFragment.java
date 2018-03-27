package cn.garymb.ygomobile.ui.preference.fragments;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;

import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.plus.DialogPlus;
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
        bind("pref_key_change_log");
        bind("pref_key_open_alipay");
        bind("pref_key_about_version");
        bind("pref_key_about_check_update");
        String text = SystemUtils.getVersionName(getActivity())
                + " (" + SystemUtils.getVersion(getActivity()) + ")";
        findPreference("pref_key_about_version").setSummary(text);
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        String key = preference.getKey();
        if ("pref_key_change_log".equals(key)) {
//            VUiKit.defer().when(() -> {
//                String html = null;
//                InputStream inputStream = null;
//                ByteArrayOutputStream byteArrayOutputStream = null;
//                try {
//                    inputStream = getContext().getAssets().open("changelog.html");
//                    byteArrayOutputStream = new ByteArrayOutputStream();
//                    byte[] data = new byte[4096];
//                    int len;
//                    while ((len = inputStream.read(data)) != -1) {
//                        byteArrayOutputStream.write(data, 0, len);
//                    }
//                    html = byteArrayOutputStream.toString();
//                } catch (IOException e) {
//                    e.printStackTrace();
//                } finally {
//
//                    IOUtils.close(byteArrayOutputStream);
//                    IOUtils.close(inputStream);
//                }
//                return html;
//            }).done((html) -> {
//                new DialogPlus(getActivity())
//                        .setTitle(getString(R.string.settings_about_change_log))
//                        .loadHtml(html, Color.TRANSPARENT)
//                        .show();
//            });
            new DialogPlus(getActivity())
                    .setTitleText(getString(R.string.settings_about_change_log))
                    .loadUrl("file:///android_asset/changelog.html", Color.TRANSPARENT)
                    .show();
        } else if ("pref_key_open_alipay".equals(key)) {
            AlipayPayUtils.openAlipayPayPage(getContext(), Constants.ALIPAY_URL);
        } else if ("pref_key_about_check_update".equals(key)) {
            Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(Constants.DOWNLOAD_HOME));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            try {
                startActivity(intent);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return false;
    }
}
