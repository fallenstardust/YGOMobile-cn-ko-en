package cn.garymb.ygomobile.ui.activities;

import static cn.garymb.ygomobile.Constants.CORE_LIMIT_PATH;
import static cn.garymb.ygomobile.Constants.CORE_PICS_ZIP;
import static cn.garymb.ygomobile.Constants.CORE_STRING_PATH;
import static cn.garymb.ygomobile.Constants.RESOURCE_CDB;
import static cn.garymb.ygomobile.Constants.RESOURCE_LFLIST;
import static cn.garymb.ygomobile.Constants.RESOURCE_PICS;
import static cn.garymb.ygomobile.Constants.RESOURCE_STRINGS;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.home.ResCheckTask;
import cn.garymb.ygomobile.ui.plus.DialogPlus;

/**
 * 资源分享/卡组保存 Activity
 * 透明主题，用于通过FileProvider分享游戏资源文件 或 保存卡组文件
 */
public class ResourceShareActivity extends Activity implements ResCheckTask.ResCheckListener {
    private static final String TAG = "ResourceShareActivity";

    private DialogPlus mProgressDialog;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        String action = getIntent().getAction();
        if (Constants.ACTION_SAVE_DECK.equals(action)) {
            handleSaveDeck();
        } else if (Constants.ACTION_SHARE_RESOURCE.equals(action)) {
            handleShareResource();
        } else {
            Log.w(TAG, "unknown action: " + action);
            setResult(RESULT_CANCELED);
            finish();
        }
    }

    private void handleSaveDeck() {
        String deckName = getIntent().getStringExtra(Constants.ARG_DECK_NAME);
        Uri deckUri = getDeckUriFromClipData();

        if (deckUri == null || TextUtils.isEmpty(deckName)) {
            Log.w(TAG, "save deck: missing uri or name");
            setResult(RESULT_CANCELED);
            finish();
            return;
        }

        try {
            String category = getRequestingAppName();
            File categoryDir = new File(AppsSettings.get().getResourcePath(), Constants.CORE_DECK_PATH + "/" + category);
            if (!categoryDir.exists()) {
                categoryDir.mkdirs();
            }

            String fileName = deckName.endsWith(Constants.YDK_FILE_EX) ? deckName : deckName + Constants.YDK_FILE_EX;
            File targetFile = new File(categoryDir, fileName);

            InputStream inputStream = getContentResolver().openInputStream(deckUri);
            if (inputStream == null) {
                Log.e(TAG, "save deck: cannot open input stream for uri: " + deckUri);
                setResult(RESULT_CANCELED);
                finish();
                return;
            }

            OutputStream outputStream = new FileOutputStream(targetFile);
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            outputStream.close();
            inputStream.close();

            Log.i(TAG, "save deck success: " + targetFile.getAbsolutePath());
            setResult(RESULT_OK);

        } catch (Exception e) {
            Log.e(TAG, "save deck failed", e);
            setResult(RESULT_CANCELED);
        }

        finish();
    }

    private Uri getDeckUriFromClipData() {
        ClipData clipData = getIntent().getClipData();
        if (clipData == null || clipData.getItemCount() == 0) {
            return null;
        }
        return clipData.getItemAt(0).getUri();
    }

    private void handleShareResource() {
        int[] resources = getIntent().getIntArrayExtra(Constants.ARG_RESOURCES);
        if (resources == null || resources.length == 0) {
            setResult(RESULT_CANCELED);
            finish();
            return;
        }

        mProgressDialog = new DialogPlus(this);
        mProgressDialog.setMessage(getString(R.string.check_res));
        mProgressDialog.setCancelable(false);
        mProgressDialog.show();

        new ResCheckTask(this, this).execute();
    }

    @Override
    public void onResCheckFinished(int result, boolean isNewVersion) {
        if (mProgressDialog != null && mProgressDialog.isShowing()) {
            mProgressDialog.dismiss();
        }

        if (result != ResCheckTask.ERROR_NONE) {
            setResult(RESULT_CANCELED);
            finish();
            return;
        }

        showResourceConfirmDialog();
    }

    private void showResourceConfirmDialog() {
        int[] resources = getIntent().getIntArrayExtra(Constants.ARG_RESOURCES);
        List<String> resourceNames = new ArrayList<>();
        final Map<Integer, File> resourceFiles = new LinkedHashMap<>();

        for (int type : resources) {
            File file = null;
            String name = null;
            switch (type) {
                case RESOURCE_CDB:
                    file = AppsSettings.get().getDatabaseFile();
                    name = getString(R.string.resource_cdb);
                    break;
                case RESOURCE_LFLIST:
                    file = new File(AppsSettings.get().getResourcePath(), CORE_LIMIT_PATH);
                    name = getString(R.string.resource_lflist);
                    break;
                case RESOURCE_STRINGS:
                    file = new File(AppsSettings.get().getResourcePath(), CORE_STRING_PATH);
                    name = getString(R.string.resource_strings);
                    break;
                case RESOURCE_PICS:
                    file = new File(AppsSettings.get().getResourcePath(), CORE_PICS_ZIP);
                    name = getString(R.string.resource_pics);
                    break;
            }
            if (file != null && file.exists()) {
                resourceFiles.put(type, file);
                resourceNames.add(name);
            }
        }

        if (resourceFiles.isEmpty()) {
            setResult(RESULT_CANCELED);
            finish();
            return;
        }

        String requestingApp = getRequestingAppName();
        String message = getString(R.string.resource_share_message, requestingApp, TextUtils.join(", ", resourceNames));

        DialogPlus dialog = new DialogPlus(this);
        dialog.setTitle(R.string.resource_share_title);
        dialog.setMessage(message);
        dialog.setLeftButtonText(R.string.cancel);
        dialog.setRightButtonText(R.string.ok);
        dialog.setOnCloseLinster(dlg -> {
            setResult(RESULT_CANCELED);
            finish();
        });
        dialog.setLeftButtonListener((dlg, s) -> {
            setResult(RESULT_CANCELED);
            finish();
        });
        dialog.setRightButtonListener((dlg, s) -> {
            shareResources(resourceFiles);
        });
        dialog.show();
    }

    private void shareResources(Map<Integer, File> resourceFiles) {
        Bundle resourceUriMap = new Bundle();
        ArrayList<Uri> uriList = new ArrayList<>();

        for (Map.Entry<Integer, File> entry : resourceFiles.entrySet()) {
            File file = entry.getValue();
            Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".gamefiles", file);
            resourceUriMap.putParcelable(String.valueOf(entry.getKey()), uri);
            uriList.add(uri);
        }

        if (uriList.isEmpty()) {
            setResult(RESULT_CANCELED);
            finish();
            return;
        }

        Intent resultIntent = new Intent();
        resultIntent.putExtra(Constants.ARG_RESOURCE_URIS, resourceUriMap);

        try {
            int versionCode = getPackageManager().getPackageInfo(getPackageName(), 0).versionCode;
            resultIntent.putExtra(Constants.ARG_VERSION_CODE, versionCode);
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "get versionCode failed", e);
        }
        resultIntent.putExtra(Constants.ARG_PACKAGE_NAME, getPackageName());

        ClipData clipData = ClipData.newRawUri("resources", uriList.get(0));
        for (int i = 1; i < uriList.size(); i++) {
            clipData.addItem(new ClipData.Item(uriList.get(i)));
        }
        resultIntent.setClipData(clipData);
        resultIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

        setResult(RESULT_OK, resultIntent);
        finish();
    }

    private String getRequestingAppName() {
        String callingPackage = getCallingPackage();
        if (TextUtils.isEmpty(callingPackage)) {
            return "未知应用";
        }

        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo appInfo = pm.getApplicationInfo(callingPackage, 0);
            String appName = pm.getApplicationLabel(appInfo).toString();
            if (!TextUtils.isEmpty(appName)) {
                return appName;
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "getRequestingAppName exception: " + callingPackage, e);
        }

        return callingPackage;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mProgressDialog != null && mProgressDialog.isShowing()) {
            mProgressDialog.dismiss();
            mProgressDialog = null;
        }
    }
}