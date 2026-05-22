package cn.garymb.ygomobile.ui.settings;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;

import androidx.preference.CheckBoxPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

abstract class BasePreferenceFragment extends PreferenceFragmentCompat implements Preference.OnPreferenceClickListener
        , Preference.OnPreferenceChangeListener {

    public BasePreferenceFragment() {
        super();
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        // 子类需要实现此方法来加载偏好设置
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object value) {
        if (value == null) return false;
        String stringValue;
        if (value instanceof String) {
            stringValue = (String) value;
        } else {
            stringValue = value.toString();
        }
        if (preference instanceof ListPreference) {
            // For list preferences, look up the correct display value in
            // the preference's 'entries' list.
            ListPreference listPreference = (ListPreference) preference;
            if (!TextUtils.equals(listPreference.getValue(), stringValue)) {
                listPreference.setValue(stringValue);
                preference.setSummary(listPreference.getEntry());
            } else {
                int index = listPreference.findIndexOfValue(stringValue);
                CharSequence desc = index >= 0
                        ? listPreference.getEntries()[index]
                        : null;
                preference.setSummary(desc);
            }
        } else if (preference instanceof CheckBoxPreference) {
            CheckBoxPreference checkBoxPreference = (CheckBoxPreference) preference;
            checkBoxPreference.setChecked((Boolean) value);
        } else {
            // For all other preferences, set the summary to the value's
            // simple string representation.
            preference.setSummary(stringValue);
        }
        return true;
    }

    protected final void bind(String key, Object defValue) {
        Preference preference = findPreference(key);
        if (preference == null) return;
        Object value = getValue(preference.getKey(), defValue, preference instanceof CheckBoxPreference);
        onPreferenceChange(preference, value);
        preference.setOnPreferenceChangeListener(this);
        if (!(preference instanceof ListPreference)) {
            preference.setOnPreferenceClickListener(this);
        }
    }

    protected final void bind(String key) {
        bind(key, null);
    }

    protected Object getValue(String key, Object defValue, boolean isbool) {
        if (mSharedPreferences == null) {
            return null;
        }
        Object value = null;
        try {
            if (isbool) {
                boolean def = defValue != null && (Boolean) defValue;
                value = mSharedPreferences.getBoolean(key, def);
            } else {
                value = mSharedPreferences.getString(key, "" + defValue);
            }
        } catch (Exception e) {
            return defValue;
        }
        return value;
    }

    protected abstract SharedPreferences getSharedPreferences();

    protected SharedPreferences mSharedPreferences;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        mSharedPreferences = getSharedPreferences();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            startActivity(new Intent(getActivity(), getActivity().getClass()));
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}