package cn.garymb.ygomobile.ui.settings;

import static cn.garymb.ygomobile.Constants.REQUEST_CHOOSE_FILE;
import static cn.garymb.ygomobile.Constants.REQUEST_CHOOSE_FOLDER;
import static cn.garymb.ygomobile.Constants.REQUEST_CHOOSE_IMG;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.preference.Preference;
import android.widget.Toast;

import com.yuyh.library.imgsel.ISNav;
import com.yuyh.library.imgsel.config.ISListConfig;
import com.yuyh.library.imgsel.ui.ISListActivity;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.file.FileActivity;
import cn.garymb.ygomobile.ui.file.FileOpenType;
import cn.garymb.ygomobile.utils.CurImageInfo;
import cn.garymb.ygomobile.utils.FileUtils;
import cn.garymb.ygomobile.utils.YGOUtil;

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
                .needCrop(true)
                // 裁剪大小。needCrop为true的时候配置
                .cropSize(mCurImageInfo.width, mCurImageInfo.height, mCurImageInfo.width, mCurImageInfo.height)
                // 第一个是否显示相机，默认true
                .needCamera(false)
                // 最大选择图片数量，默认9
                .maxNum(1)
                .build();

        // 跳转到图片选择器
        ISNav.getInstance().toListActivity(this, config, REQUEST_CHOOSE_IMG);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
//        Log.i("kk", "result "+requestCode+",data="+data);
        if (requestCode == Constants.REQUEST_CHOOSE_IMG && resultCode == Activity.RESULT_OK && data != null) {
            ArrayList<String> photos = data.getStringArrayListExtra(ISListActivity.INTENT_RESULT);
            if (mCurImageInfo != null) {
                String cachePath = photos.get(0);
                try {
                    FileUtils.copyFile(cachePath, mCurImageInfo.mOutFile);
                } catch (IOException e) {
                    YGOUtil.showTextToast(e + "", Toast.LENGTH_LONG);
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

}
