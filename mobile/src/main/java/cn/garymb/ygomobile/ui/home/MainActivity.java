package cn.garymb.ygomobile.ui.home;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.AssetManager;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.IOException;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.GameUriManager;
import cn.garymb.ygomobile.YGOMobileActivity;
import cn.garymb.ygomobile.YGOStarter;
import cn.garymb.ygomobile.core.YGOCore;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.activities.WebActivity;
import cn.garymb.ygomobile.ui.plus.DialogPlus;
import cn.garymb.ygomobile.ui.plus.VUiKit;
import cn.garymb.ygomobile.utils.ComponentUtils;
import cn.garymb.ygomobile.utils.IOUtils;
import cn.garymb.ygomobile.utils.NetUtils;
import cn.garymb.ygomobile.utils.PermissionUtil;
import libwindbot.windbot.WindBot;

import static cn.garymb.ygomobile.Constants.ACTION_RELOAD;
import static cn.garymb.ygomobile.Constants.CORE_BOT_CONF_PATH;
import static cn.garymb.ygomobile.Constants.DATABASE_NAME;
import static cn.garymb.ygomobile.Constants.NETWORK_IMAGE;
import static cn.garymb.ygomobile.ui.home.ResCheckTask.OnCompletedListener;

public class MainActivity extends HomeActivity {
    private static final String TAG = "ResCheckTask";
    private GameUriManager mGameUriManager;
    private ImageUpdater mImageUpdater;
    private boolean enableStart;
    ResCheckTask mResCheckTask;
    private final String[] PERMISSIONS = {
//            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.SYSTEM_ALERT_WINDOW,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        YGOStarter.onCreated(this);
        mImageUpdater = new ImageUpdater(this);
        mResCheckTask = new ResCheckTask(this);
        //动态权限
//        ActivityCompat.requestPermissions(this, PERMISSIONS, 0);
        //资源复制
        mResCheckTask.start(this::onCheckCompleted);
        registerReceiver(mWindBotReceiver, new IntentFilter(Constants.WINDBOT_ACTION));
    }

    @SuppressLint({"StringFormatMatches", "StringFormatInvalid"})
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
//        for(int i=0;i<permissions.length;i++){
//            if(grantResults[i] == PackageManager.PERMISSION_DENIED){
//                showToast(getString(R.string.tip_no_permission,permissions[i]));
//                break;
//            }
//        }

    }

    @Override
    protected void onResume() {
        super.onResume();
        YGOStarter.onResumed(this);
        //如果游戏Activity已经不存在了，则
        if (!ComponentUtils.isActivityRunning(this, new ComponentName(this, YGOMobileActivity.class))) {
            sendBroadcast(new Intent(YGOCore.ACTION_STOP).setPackage(getPackageName()));
        }
    }

    public BroadcastReceiver mWindBotReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Constants.WINDBOT_ACTION.equals(intent.getAction())) {
                String args = intent.getStringExtra("args");
                WindBot.runAndroid(args);
            }
        }
    };

    private void initWindBot() {
        Log.i("路径", getFilesDir().getPath());
        Log.i("路径2", AppsSettings.get().getDataBasePath() + "/" + DATABASE_NAME);
        try {
            WindBot.initAndroid(AppsSettings.get().getResourcePath(),
                    AppsSettings.get().getDataBasePath() + "/" + DATABASE_NAME,
                    AppsSettings.get().getResourcePath() + "/" + CORE_BOT_CONF_PATH);
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(mWindBotReceiver);
        YGOStarter.onDestroy(this);
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (ACTION_RELOAD.equals(intent.getAction())) {
            mResCheckTask.start((error, isNew) -> {
                if (error < 0) {
                    enableStart = false;
                } else {
                    enableStart = true;
                }
                getGameUriManager().doIntent(getIntent());
            });
        } else {
            getGameUriManager().doIntent(intent);
        }
    }

    private GameUriManager getGameUriManager() {
        if (mGameUriManager == null) {
            mGameUriManager = new GameUriManager(this);
        }
        return mGameUriManager;
    }

    @Override
    protected void openGame() {
        if (enableStart) {
            YGOStarter.startGame(this, null);
        } else {
            VUiKit.show(this, R.string.dont_start_game);
        }
    }

    @Override
    public void updateImages() {
        Log.e("MainActivity", "重置资源");
        DialogPlus dialog = DialogPlus.show(this, null, getString(R.string.message));
        final AssetManager assetManager = getAssets();
        String resPath = AppsSettings.get().getResourcePath();
        VUiKit.defer().when(() -> {
            Log.e("MainActivity", "开始复制");
            try {
                IOUtils.createNoMedia(AppsSettings.get().getResourcePath());
                if (IOUtils.hasAssets(assetManager, Constants.ASSET_PICS_FILE_PATH)) {
                    IOUtils.copyFile(assetManager, Constants.ASSET_PICS_FILE_PATH,
                            new File(resPath, Constants.CORE_PICS_ZIP), true);
                }
                if (IOUtils.hasAssets(assetManager, Constants.ASSET_SCRIPTS_FILE_PATH)) {
                    IOUtils.copyFile(assetManager, Constants.ASSET_SCRIPTS_FILE_PATH,
                            new File(resPath, Constants.CORE_SCRIPTS_ZIP), true);
                }
                IOUtils.copyFile(assetManager, Constants.ASSET_CARDS_CDB_FILE_PATH,
                        new File(AppsSettings.get().getDataBasePath(), Constants.DATABASE_NAME), true);

                IOUtils.copyFile(assetManager, Constants.ASSET_STRING_CONF_FILE_PATH,
                        new File(AppsSettings.get().getResourcePath(), Constants.CORE_STRING_PATH), true);

                IOUtils.copyFolder(assetManager, Constants.ASSET_SKIN_DIR_PATH,
                        AppsSettings.get().getCoreSkinPath(), false);

                IOUtils.copyFolder(assetManager, Constants.ASSET_FONTS_DIR_PATH,
                        AppsSettings.get().getFontDirPath(), false);

                IOUtils.copyFolder(assetManager, Constants.ASSET_WINDBOT_DECK_DIR_PATH,
                        new File(resPath, Constants.LIB_WINDBOT_DECK_PATH).getPath(), true);
                IOUtils.copyFolder(assetManager, Constants.ASSET_WINDBOT_DIALOG_DIR_PATH,
                        new File(resPath, Constants.LIB_WINDBOT_DIALOG_PATH).getPath(), true);
            } catch (Throwable e) {
                e.printStackTrace();
                Log.e("MainActivity", "错误" + e);
            }
        }).done((rs) -> {
            Log.e("MainActivity", "复制完毕");
            dialog.dismiss();
        });
    }

    /*        checkResourceDownload((result, isNewVersion) -> {
                Toast.makeText(this, R.string.tip_reset_game_res, Toast.LENGTH_SHORT).show();
            });*/

    private void onCheckCompleted(int error, boolean isNew) {
        if (error < 0) {
            enableStart = false;
        } else {
            enableStart = true;
        }
        initWindBot();
        if (isNew) {
            if (!getGameUriManager().doIntent(getIntent())) {
                final DialogPlus dialog = new DialogPlus(this);
                dialog.showTitleBar();
                dialog.setTitle(getString(R.string.settings_about_change_log));
                dialog.loadUrl("file:///android_asset/changelog.html", Color.TRANSPARENT);
                dialog.setLeftButtonText(R.string.help);
                dialog.setLeftButtonListener((dlg, i) -> {
                    dialog.setContentView(R.layout.dialog_help);
                    dialog.setTitle(R.string.question);
                    dialog.hideButton();
                    dialog.show();
                    View viewDialog = dialog.getContentView();
                    Button btnMasterRule = viewDialog.findViewById(R.id.masterrule);
                    Button btnTutorial = viewDialog.findViewById(R.id.tutorial);

                    btnMasterRule.setOnClickListener((v) -> {
                        WebActivity.open(this, getString(R.string.masterrule), Constants.URL_MASTERRULE_CN);
                        dialog.dismiss();
                    });
                    btnTutorial.setOnClickListener((v) -> {
                        WebActivity.open(this, getString(R.string.help), Constants.URL_HELP);
                        dialog.dismiss();
                    });
                });
                dialog.setRightButtonText(R.string.OK);
                dialog.setRightButtonListener((dlg, i) -> {
                    dlg.dismiss();
                    //mImageUpdater
                    if (NETWORK_IMAGE && NetUtils.isConnected(getContext())) {
                        if (!mImageUpdater.isRunning()) {
                            mImageUpdater.start();
                        }
                    }
                });
                    /*DialogPlus dialog = new DialogPlus(this)
                            .setTitleText(getString(R.string.settings_about_change_log))
                            .loadUrl("file:///android_asset/changelog.html", Color.TRANSPARENT)
                            .hideButton()
                            .setOnCloseLinster((dlg) -> {
                                dlg.dismiss();
                                //mImageUpdater
                                if (NETWORK_IMAGE && NetUtils.isConnected(getContext())) {
                                    if (!mImageUpdater.isRunning()) {
                                        mImageUpdater.start();
                                    }
                                }
                            });*/
                dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialogInterface) {
                        PermissionUtil.isServicePermission(MainActivity.this, true);

                    }
                });
                dialog.show();
            }
        } else {
            PermissionUtil.isServicePermission(MainActivity.this, true);
            getGameUriManager().doIntent(getIntent());
        }
    }
}
