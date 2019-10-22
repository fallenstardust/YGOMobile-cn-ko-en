package cn.garymb.ygomobile.ui.preference;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.garymb.ygomobile.lite.BuildConfig;


public class YGOPreferencesProvider extends ContentProvider {
    public static final String AUTH = BuildConfig.APPLICATION_ID + ".preference";
    public static final String TYPE_STRING = "string";
    public static final String TYPE_INT = "int";
    public static final String TYPE_LONG = "long";
    public static final String TYPE_FLOAT = "float";
    public static final String TYPE_BOOLEAN = "boolean";
    public static final String COL_NAME = "name";
    public static final String COL_VALUE = "value";
    public static final int COL_VALUE_INDEX = 0;

    private static final Map<String, WeakReference<SharedPreferences>> sMap = new HashMap<>();

    public static SharedPreferences getOrCreate(Context context, String name) {
        if (name == null) {
            name = context.getPackageName();
        }
        WeakReference<SharedPreferences> val;
        synchronized (sMap) {
            val = sMap.get(name);
            if (val == null || val.get() == null) {
                val = new WeakReference<SharedPreferences>(context.getSharedPreferences(name, Context.MODE_MULTI_PROCESS));
                sMap.put(name, val);
            }
        }
        return val.get();
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, @Nullable String[] projection, @Nullable String selection, @Nullable String[] selectionArgs, @Nullable String sortOrder) {
        List<String> paths = uri.getPathSegments();
        String name = paths.get(0);
        String type = paths.get(1);
        String key = paths.get(2);
        String def = selection;
        SharedPreferences sharedPreferences = getOrCreate(getContext(), name);
        MatrixCursor cursor = new MatrixCursor(new String[]{COL_VALUE});
        try {
            if (TYPE_BOOLEAN.equals(type)) {
                cursor.addRow(new Object[]{(int) (sharedPreferences.getBoolean(key, "true".equalsIgnoreCase(def)) ? 1 : 0)});
            } else if (TYPE_FLOAT.equals(type)) {
                if (def == null) {
                    def = "0";
                }
                cursor.addRow(new Object[]{sharedPreferences.getFloat(key, Float.parseFloat(def))});
            } else if (TYPE_LONG.equals(type)) {
                if (def == null) {
                    def = "0";
                }
                cursor.addRow(new Object[]{sharedPreferences.getLong(key, Long.parseLong(def))});
            } else if (TYPE_INT.equals(type)) {
                if (def == null) {
                    def = "0";
                }
                cursor.addRow(new Object[]{sharedPreferences.getInt(key, Integer.parseInt(def))});
            } else if (TYPE_STRING.equals(type)) {
                cursor.addRow(new Object[]{sharedPreferences.getString(key, def)});
            } else {
                cursor.close();
                return null;
            }
        } catch (Throwable e) {
            Log.e("kk", "query", e);
            cursor.close();
            return null;
        }
        return cursor;
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        return null;
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, @Nullable ContentValues values) {
        return null;
    }

    @Override
    public int delete(@NonNull Uri uri, @Nullable String selection, @Nullable String[] selectionArgs) {
        List<String> paths = uri.getPathSegments();
        String name = paths.get(0);
        String key = paths.get(1);
        SharedPreferences sharedPreferences = getOrCreate(getContext(), name);
        if (sharedPreferences.contains(key)) {
            sharedPreferences.edit().remove(key).apply();
            return 1;
        }
        return 0;
    }

    @Override
    public int update(@NonNull Uri uri, @Nullable ContentValues values, @Nullable String selection, @Nullable String[] selectionArgs) {
        Log.e("kk", "update " + uri);
        List<String> paths = uri.getPathSegments();
        String name = paths.get(0);
        String type = paths.get(1);
        String key = paths.get(2);
        SharedPreferences sharedPreferences = getOrCreate(getContext(), name);
        try {
            if (TYPE_BOOLEAN.equals(type)) {
                sharedPreferences.edit().putBoolean(key, values.getAsBoolean(COL_VALUE)).apply();
            } else if (TYPE_FLOAT.equals(type)) {
                sharedPreferences.edit().putFloat(key, values.getAsFloat(COL_VALUE)).apply();
            } else if (TYPE_LONG.equals(type)) {
                sharedPreferences.edit().putLong(key, values.getAsLong(COL_VALUE)).apply();
            } else if (TYPE_INT.equals(type)) {
                sharedPreferences.edit().putInt(key, values.getAsInteger(COL_VALUE)).apply();
            } else if (TYPE_STRING.equals(type)) {
                sharedPreferences.edit().putString(key, values.getAsString(COL_VALUE)).apply();
            } else {
                return 0;
            }
        }catch (Throwable e){
            Log.e("kk", "update", e);
            return 0;
        }
        return 1;
    }
}
