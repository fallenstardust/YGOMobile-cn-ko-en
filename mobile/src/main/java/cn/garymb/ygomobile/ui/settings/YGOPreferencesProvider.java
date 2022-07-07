package cn.garymb.ygomobile.ui.settings;

import com.zlm.libs.preferences.PreferencesProvider;

import cn.garymb.ygomobile.App;

public class YGOPreferencesProvider extends PreferencesProvider {
    @Override
    public String getAuthorities() {
        return App.get().getPackageName() + ".ui.preference.YGOPreferencesProvider";
    }
}
