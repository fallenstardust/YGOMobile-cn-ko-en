package cn.garymb.ygomobile.ui.preference;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.preference.Preference;
import android.widget.Toast;

import com.yuyh.library.imgsel.ISNav;
import com.yuyh.library.imgsel.config.ISListConfig;
import com.yuyh.library.imgsel.ui.ISListActivity;
import com.zlm.libs.preferences.PreferencesProviderUtils;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.file.FileActivity;
import cn.garymb.ygomobile.ui.file.FileOpenType;
import cn.garymb.ygomobile.utils.FileUtils;

import static cn.garymb.ygomobile.Constants.REQUEST_CHOOSE_FILE;
import static cn.garymb.ygomobile.Constants.REQUEST_CHOOSE_FOLDER;
import static cn.garymb.ygomobile.Constants.REQUEST_CHOOSE_IMG;

public abstract class PreferenceFragmentPlus extends BasePreferenceFragment {
    private Preference curPreference;
    private CurImageInfo mCurImageInfo;

    protected void onChooseFileOk(Preference preference, String file) {
        onPreferenceChange(preference, file);
    }

    protected void onChooseFileFail(Preference preference) {

    }

    protected void showFolderChooser(Preference preference, String defPath, String title) {
        curPreference = preference;
        Intent intent = FileActivity.getIntent(getActivity(), title, null, defPath, false, FileOpenType.SelectFolder);
        startActivityForResult(intent, REQUEST_CHOOSE_FOLDER);
    }

    /***
     * @param preference
     * @param type       *\/*
     */
    protected void showFileChooser(Preference preference, String type, String defPath, String title) {
        curPreference = preference;
        Intent intent = FileActivity.getIntent(getActivity(), title, type, defPath, false, FileOpenType.SelectFile);
        startActivityForResult(intent, REQUEST_CHOOSE_FILE);
//
//        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
////        intent.addCategory(Intent.CATEGORY_OPENABLE);
//        intent.setType(type);
//        try {
//            startActivityForResult(intent,
//                    REQUEST_CHOOSE_FILE);
//        } catch (android.content.ActivityNotFoundException ex) {
//            Toast.makeText(getActivity(), R.string.no_find_file_selectotr, Toast.LENGTH_SHORT)
//                    .show();
//            onChooseFileFail(preference);
//        }
    }

    public Context getContext() {
        if (Build.VERSION.SDK_INT >= 23) {
            return super.getContext();
        } else {
            return super.getActivity();
        }
    }

    /***
     */
    protected void showImageCropChooser(Preference preference, String title, String outFile, boolean isJpeg, int width, int height) {
        mCurImageInfo = new CurImageInfo();
        mCurImageInfo.mOutFile = outFile;
        mCurImageInfo.mJpeg = isJpeg;
        mCurImageInfo.width = width;
        mCurImageInfo.height = height;
        mCurImageInfo.mCurTitle = title;
        curPreference = preference;
        String defPath = new File(outFile).getParent();
//        Intent intent = FileActivity.getIntent(getActivity(), title, "*.[jpg|png|bmp]", defPath, false, FileOpenType.SelectFile);
//        startActivityForResult(intent, REQUEST_CHOOSE_IMG);

//      intent.addCategory(Intent.CATEGORY_OPENABLE);
//      intent.setType("image/*");
        ISListConfig config = new ISListConfig.Builder()
                // 是否多选, 默认true
                .multiSelect(false)
                // 是否记住上次选中记录, 仅当multiSelect为true的时候配置，默认为true
                .rememberSelected(false)
                // “确定”按钮背景色
                .btnBgColor(Color.BLACK)
                // “确定”按钮文字颜色
                .btnTextColor(Color.WHITE)
                // 使用沉浸式状态栏
                .statusBarColor(Color.parseColor("#11113d"))
                // 返回图标ResId
                .backResId(R.drawable.ic_back)
                // 标题
                .title(getString(R.string.images))
                // 标题文字颜色
                .titleColor(Color.WHITE)
                // TitleBar背景色
                .titleBgColor(Color.parseColor("#11113d"))
                // 裁剪大小。needCrop为true的时候配置
                .cropSize(mCurImageInfo.width, mCurImageInfo.height, mCurImageInfo.width, mCurImageInfo.height)
                .needCrop(true)
                // 第一个是否显示相机，默认true
                .needCamera(false)
                // 最大选择图片数量，默认9
                .maxNum(1)
                .build();

        // 跳转到图片选择器
        ISNav.getInstance().toListActivity(this, config, REQUEST_CHOOSE_IMG);
    }

    //已弃用裁剪
    /*
    protected void openPhotoCut(Preference preference, Uri srcfile, CurImageInfo info) {
        // 裁剪图片
        if (srcfile == null || info == null) {
            onChooseFileFail(preference);
            return;
        }
        Log.i("我是srcfile", srcfile + "");
        File file = new File(info.mOutFile);
        Intent intent = new Intent("com.android.camera.action.CROP");
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            saveimgUri = FileProvider.getUriForFile(getContext(), BuildConfig.APPLICATION_ID + ".fallenstardust", file);
        } else {
            saveimgUri = Uri.fromFile(file);
        }
        intent.setDataAndType(srcfile, "image/*");
        // 下面这个crop=true是设置在开启的Intent中设置显示的VIEW可裁剪
        intent.putExtra("crop", "true");
        // aspectX aspectY 是宽高的比例
        intent.putExtra("aspectX", info.width);
        intent.putExtra("aspectY", info.height);
        // outputX outputY 是裁剪图片宽高
        intent.putExtra("outputX", info.width);
        intent.putExtra("outputY", info.height);
        intent.putExtra("scale", true);// 黑边
        intent.putExtra("scaleUpIfNeeded", true);// 黑边
        intent.putExtra("return-data", false);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, saveimgUri);
        intent.putExtra("outputFormat", info.mJpeg ? Bitmap.CompressFormat.JPEG.toString() : Bitmap.CompressFormat.PNG.toString());

        try {
            startActivityForResult(Intent.createChooser(intent, info.mCurTitle), Constants.REQUEST_CUT_IMG);
        } catch (Exception e) {
            Log.i("我是e", e + "");
            Toast.makeText(getActivity(), R.string.no_find_image_cutor, Toast.LENGTH_SHORT).show();
        }
    }*/

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
//        Log.i("kk", "result "+requestCode+",data="+data);
        if (requestCode == Constants.REQUEST_CHOOSE_IMG && resultCode == Activity.RESULT_OK && data != null) {
            ArrayList<String> photos = data.getStringArrayListExtra(ISListActivity.INTENT_RESULT);
            if (mCurImageInfo != null) {
                String cachePath = photos.get(0);
                try {
                    FileUtils.copyFile(cachePath, mCurImageInfo.mOutFile, true);
                } catch (IOException e) {
                    Toast.makeText(getContext(), e + "", Toast.LENGTH_LONG).show();
                    onChooseFileFail(curPreference);
                }
                onChooseFileOk(curPreference, mCurImageInfo.mOutFile);
            } else {
                onChooseFileFail(curPreference);
            }
        } else if (requestCode == Constants.REQUEST_CHOOSE_FILE) {
            //选择文件
            if (data != null) {
                Uri uri = data.getData();
                if (uri != null) {
                    File file = new File(uri.getPath());
                    if (file.exists()) {
                        onChooseFileOk(curPreference, file.getAbsolutePath());
                        return;
                    }
                }
            }
            onChooseFileFail(curPreference);
        } else if (requestCode == Constants.REQUEST_CHOOSE_FOLDER) {
            //选择文件
            if (data != null) {
                Uri uri = data.getData();
                if (uri != null) {
                    File file = new File(uri.getPath());
                    if (file.exists()) {
                        onChooseFileOk(curPreference, file.getAbsolutePath());
                        return;
                    }
                }
            }
            onChooseFileFail(curPreference);
        }
    }

    public static class SharedPreferencesPlus implements SharedPreferences {

        private SharedPreferences mSharedPreferences;
        private boolean autoSave = false;
        private boolean isMultiProess = false;
        private String spName;
        private Context context;

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

    private class CurImageInfo {
        public String mOutFile;
        public String mCurTitle;
        public boolean mJpeg;
        public int width;
        public int height;

        public CurImageInfo() {

        }
    }
}
