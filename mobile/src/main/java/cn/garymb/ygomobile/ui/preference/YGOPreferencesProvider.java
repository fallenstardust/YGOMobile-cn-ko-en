package cn.garymb.ygomobile.ui.preference;

import com.zlm.libs.preferences.PreferencesProvider;

public class YGOPreferencesProvider extends PreferencesProvider {
    @Override
    public String getAuthorities() {
        return "cn.garymb.ygomobile.ui.preference.YGOPreferencesProvider";
    }
}
