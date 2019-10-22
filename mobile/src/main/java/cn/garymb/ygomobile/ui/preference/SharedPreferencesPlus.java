package cn.garymb.ygomobile.ui.preference;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import cn.garymb.ygomobile.App;
import cn.garymb.ygomobile.GameApplication;

public class SharedPreferencesPlus implements SharedPreferences, SharedPreferences.Editor {
    private String spName;
    private Context context;
    private ContentResolver mResolver;
    private static final String TAG = "kk";

    public SharedPreferencesPlus(Context context, String name, int mode) {
        this.spName = name;
        this.context = context;
        this.mResolver = context.getContentResolver();
    }

    public Editor edit() {
        return this;
    }

    protected Uri.Builder create() {
        return new Uri.Builder()
                .scheme("content")
                .authority(YGOPreferencesProvider.AUTH)
                .appendPath(spName);
    }

    private Uri create(String type, String key) {
        return create()
                .appendPath(type)
                .appendPath(key).build();
    }

    @Override
    public Editor putString(String key, String value) {
        Uri uri = create(YGOPreferencesProvider.TYPE_STRING, key);
        ContentValues contentValues = new ContentValues();
        contentValues.put(YGOPreferencesProvider.COL_VALUE, value);
        try {
            mResolver.update(uri, contentValues, null, null);
        } catch (Throwable e) {
            Log.e(TAG, "putString:" + key + "=" + value, e);
        }
        return this;
    }

    @Override
    public Editor putStringSet(String key, Set<String> values) {
        return this;
    }

    @Override
    public Editor putInt(String key, int value) {
        Uri uri = create(YGOPreferencesProvider.TYPE_INT, key);
        ContentValues contentValues = new ContentValues();
        contentValues.put(YGOPreferencesProvider.COL_VALUE, value);
        try {
            mResolver.update(uri, contentValues, null, null);
        } catch (Throwable e) {
            Log.e(TAG, "putInt:" + key + "=" + value, e);
        }
        return this;
    }

    @Override
    public Editor putLong(String key, long value) {
        Uri uri = create(YGOPreferencesProvider.TYPE_LONG, key);
        ContentValues contentValues = new ContentValues();
        contentValues.put(YGOPreferencesProvider.COL_VALUE, value);
        try {
            mResolver.update(uri, contentValues, null, null);
        } catch (Throwable e) {
            Log.e(TAG, "putLong:" + key + "=" + value, e);
        }
        return this;
    }

    @Override
    public Editor putFloat(String key, float value) {
        Uri uri = create(YGOPreferencesProvider.TYPE_FLOAT, key);
        ContentValues contentValues = new ContentValues();
        contentValues.put(YGOPreferencesProvider.COL_VALUE, value);
        try {
            mResolver.update(uri, contentValues, null, null);
        } catch (Throwable e) {
            Log.e(TAG, "putFloat:" + key + "=" + value, e);
        }
        return this;
    }

    @Override
    public Editor putBoolean(String key, boolean value) {
        Uri uri = create(YGOPreferencesProvider.TYPE_BOOLEAN, key);
        ContentValues contentValues = new ContentValues();
        contentValues.put(YGOPreferencesProvider.COL_VALUE, value);
        try {
            mResolver.update(uri, contentValues, null, null);
        } catch (Throwable e) {
            Log.e(TAG, "putBoolean:" + key + "=" + value, e);
        }
        return this;
    }

    @Override
    public Editor remove(String key) {
        Uri uri = create()
                .appendPath(key).build();
        try {
            mResolver.delete(uri, null, null);
        } catch (Throwable e) {
            Log.e(TAG, "remove:" + key, e);
        }
        return this;
    }

    @Override
    public Editor clear() {
        //TODO
        return this;
    }

    @Override
    public Map<String, ?> getAll() {
        //TODO
        return new HashMap<>();
    }

    @Override
    public String getString(String key, String defValue) {
        Uri uri = create(YGOPreferencesProvider.TYPE_STRING, key);
        Cursor cursor = mResolver.query(uri, null, defValue, null, null);
        if (cursor != null) {
            try {
                cursor.moveToFirst();
                return cursor.getString(YGOPreferencesProvider.COL_VALUE_INDEX);
            } catch (Throwable e) {
                Log.e(TAG, "getString:" + key, e);
            } finally {
                cursor.close();
            }
        }
        return defValue;
    }

    @Override
    public Set<String> getStringSet(String key, Set<String> defValues) {
        //TODO
        return defValues;
    }

    @Override
    public int getInt(String key, int defValue) {
        Uri uri = create(YGOPreferencesProvider.TYPE_INT, key);
        Cursor cursor = mResolver.query(uri, null, String.valueOf(defValue), null, null);
        if (cursor != null) {
            try{
                cursor.moveToFirst();
                return cursor.getInt(YGOPreferencesProvider.COL_VALUE_INDEX);
            } catch (Throwable e) {
                Log.e(TAG, "getInt:" + key, e);
            } finally {
                cursor.close();
            }
        }
        return defValue;
    }

    @Override
    public long getLong(String key, long defValue) {
        Uri uri = create(YGOPreferencesProvider.TYPE_LONG, key);
        Cursor cursor = mResolver.query(uri, null, String.valueOf(defValue), null, null);
        if (cursor != null) {
            try{
                cursor.moveToFirst();
                return cursor.getLong(YGOPreferencesProvider.COL_VALUE_INDEX);
            } catch (Throwable e) {
                Log.e(TAG, "getLong:" + key, e);
            } finally {
                cursor.close();
            }
        }
        return defValue;
    }

    @Override
    public float getFloat(String key, float defValue) {
        Uri uri = create(YGOPreferencesProvider.TYPE_FLOAT, key);
        Cursor cursor = mResolver.query(uri, null, String.valueOf(defValue), null, null);
        if (cursor != null) {
            try{
                cursor.moveToFirst();
                return cursor.getFloat(YGOPreferencesProvider.COL_VALUE_INDEX);
            } catch (Throwable e) {
                Log.e(TAG, "getFloat:" + key, e);
            } finally {
                cursor.close();
            }
        }
        return defValue;
    }

    @Override
    public boolean getBoolean(String key, boolean defValue) {
        Uri uri = create(YGOPreferencesProvider.TYPE_BOOLEAN, key);
        Cursor cursor = mResolver.query(uri, null, String.valueOf(defValue), null, null);
        if (cursor != null) {
            try{
                cursor.moveToFirst();
                return cursor.getInt(YGOPreferencesProvider.COL_VALUE_INDEX) > 0;
            } catch (Throwable e) {
                Log.e(TAG, "getBoolean:" + key, e);
            } finally {
                cursor.close();
            }
        }
        return defValue;
    }

    @Override
    public boolean contains(String key) {
        //TODO
        return true;
    }

    @Override
    public boolean commit() {
        //TODO
        return true;
    }

    @Override
    public void apply() {
        //TODO
    }

    @Override
    public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
        //TODO
    }

    @Override
    public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener) {
        //TODO
    }
}
