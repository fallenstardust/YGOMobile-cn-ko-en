package cn.garymb.ygomobile.ui.settings;

import android.content.Context;
import android.content.SharedPreferences;

import com.zlm.libs.preferences.PreferencesProviderUtils;

import java.util.Map;
import java.util.Set;

public class SharedPreferencesPlus implements SharedPreferences {

    private final SharedPreferences mSharedPreferences;
    private boolean autoSave = false;
    private boolean isMultiProess = false;
    private final String spName;
    private final Context context;

    private SharedPreferencesPlus(Context context, String name, int mode) {
        spName = name;
        this.context = context;
        mSharedPreferences = context.getSharedPreferences(name, mode);
        isMultiProess = (mode & Context.MODE_MULTI_PROCESS) == Context.MODE_MULTI_PROCESS;
    }

    public static SharedPreferencesPlus create(Context context, String name) {
        return create(context, name, Context.MODE_PRIVATE | Context.MODE_MULTI_PROCESS);
    }

    public static SharedPreferencesPlus create(Context context, String name, int mode) {
        return new SharedPreferencesPlus(context, name, mode);
    }

    public SharedPreferences getSharedPreferences() {
        return mSharedPreferences;
    }

    public boolean isAutoSave() {
        return autoSave;
    }

    public void setAutoSave(boolean autoSave) {
        this.autoSave = autoSave;
    }

    public Editor edit() {
        return mSharedPreferences.edit();
    }

    public void putString(String key, String value) {
        Editor editor = edit().putString(key, value);
        if (autoSave) {
            if (isMultiProess) {
//                    editor.commit();
                PreferencesProviderUtils.putString(context, spName, key, value);
            } else {
                editor.apply();
            }
        }
    }

    public void putStringSet(String key, Set<String> values) {
        Editor editor = edit().putStringSet(key, values);
        if (autoSave) {
            if (isMultiProess) {
                editor.commit();
            } else {
                editor.apply();
            }
        }
    }

    public void putInt(String key, int value) {
        Editor editor = edit().putInt(key, value);
        if (autoSave) {
            if (isMultiProess) {
//                    editor.commit();
                PreferencesProviderUtils.putInt(context, spName, key, value);
            } else {
                editor.apply();
            }
        }
    }

    public void putLong(String key, long value) {
        Editor editor = edit().putLong(key, value);
        if (autoSave) {
            if (isMultiProess) {
//                    editor.commit();
                PreferencesProviderUtils.putLong(context, spName, key, value);
            } else {
                editor.apply();
            }
        }
    }

    public void putFloat(String key, float value) {
        Editor editor = edit().putFloat(key, value);
        if (autoSave) {
            if (isMultiProess) {
//                    editor.commit();
                PreferencesProviderUtils.putFloat(context, spName, key, value);
            } else {
                editor.apply();
            }
        }
    }

    public void putBoolean(String key, boolean value) {
        Editor editor = edit().putBoolean(key, value);
        if (autoSave) {
            if (isMultiProess) {
//                    editor.commit();
                PreferencesProviderUtils.putBoolean(context, spName, key, value);
            } else {
                editor.apply();
            }
        }
    }

    public void remove(String key) {
        Editor editor = edit().remove(key);
        if (autoSave) {
            if (isMultiProess) {
                editor.commit();
            } else {
                editor.apply();
            }
        }
    }

    public void clear() {
        Editor editor = edit().clear();
        if (autoSave) {
            if (isMultiProess) {
                editor.commit();
            } else {
                editor.apply();
            }
        }
    }

    @Override
    public Map<String, ?> getAll() {
        return mSharedPreferences.getAll();
    }

    @Override
    public String getString(String key, String defValue) {
        if (isMultiProess)
            return PreferencesProviderUtils.getString(context, spName, key, defValue);
        else
            return mSharedPreferences.getString(key, defValue);
    }

    @Override
    public Set<String> getStringSet(String key, Set<String> defValues) {
        return mSharedPreferences.getStringSet(key, defValues);
    }

    @Override
    public int getInt(String key, int defValue) {
        if (isMultiProess)
            return PreferencesProviderUtils.getInt(context, spName, key, defValue);
        else
            return mSharedPreferences.getInt(key, defValue);
    }

    @Override
    public long getLong(String key, long defValue) {
        if (isMultiProess)
            return PreferencesProviderUtils.getLong(context, spName, key, defValue);
        else
            return mSharedPreferences.getLong(key, defValue);
    }

    @Override
    public float getFloat(String key, float defValue) {
        if (isMultiProess)
            return PreferencesProviderUtils.getFloat(context, spName, key, defValue);
        else
            return mSharedPreferences.getFloat(key, defValue);
    }

    @Override
    public boolean getBoolean(String key, boolean defValue) {
        if (isMultiProess)
            return PreferencesProviderUtils.getBoolean(context, spName, key, defValue);
        else
            return mSharedPreferences.getBoolean(key, defValue);
    }

    @Override
    public boolean contains(String key) {
        return mSharedPreferences.contains(key);
    }

    @Override
    public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
        mSharedPreferences.registerOnSharedPreferenceChangeListener(listener);
    }

    @Override
    public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
        mSharedPreferences.unregisterOnSharedPreferenceChangeListener(listener);
    }
}

