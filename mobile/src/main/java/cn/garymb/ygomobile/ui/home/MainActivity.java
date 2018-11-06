package cn.garymb.ygomobile.ui.home;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;

import java.io.IOException;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.GameUriManager;
import cn.garymb.ygomobile.YGOMobileActivity;
import cn.garymb.ygomobile.YGOStarter;
import cn.garymb.ygomobile.core.IrrlichtBridge;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.plus.DialogPlus;
import cn.garymb.ygomobile.ui.plus.VUiKit;
import cn.garymb.ygomobile.utils.ComponentUtils;
import cn.garymb.ygomobile.utils.IOUtils;
import cn.garymb.ygomobile.utils.NetUtils;

import static cn.garymb.ygomobile.Constants.ACTION_RELOAD;
import static cn.garymb.ygomobile.Constants.NETWORK_IMAGE;
import static cn.garymb.ygomobile.ui.home.ResCheckTask.ResCheckListener;
import static cn.garymb.ygomobile.ui.home.ResCheckTask.getDatapath;

public class MainActivity extends HomeActivity{
    private GameUriManager mGameUriManager;
    private ImageUpdater mImageUpdater;
    private boolean enableStart;
    ResCheckTask mResCheckTask;
 /*   private final String[] PERMISSIONS ={
//            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
    };*/

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        YGOStarter.onCreated(this);
        mImageUpdater = new ImageUpdater(this);
        //资源复制
        checkRes();
       //动态权限
     //   ActivityCompat.requestPermissions(this, PERMISSIONS, 0);
    }

    /*@Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        for(int i=0;i<permissions.length;i++){
            if(grantResults[i] == PackageManager.PERMISSION_DENIED){
                showToast(R.string.tip_no_permission);
                break;
            }
        }
        //资源复制
        checkRes();
    }*/

    private void checkRes() {
        checkResourceDownload((error, isNew) -> {
            if (error < 0) {
                enableStart = false;
            } else {
                enableStart = true;
            }
            if (isNew) {
                if (!getGameUriManager().doIntent(getIntent())) {
                    new DialogPlus(this)
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
                            })
                            .show();
                }
            } else {
                getGameUriManager().doIntent(getIntent());
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        YGOStarter.onResumed(this);
        //如果游戏Activity已经不存在了，则
        if (!ComponentUtils.isActivityRunning(this, new ComponentName(this, YGOMobileActivity.class))) {
            sendBroadcast(new Intent(IrrlichtBridge.ACTION_STOP).setPackage(getPackageName()));
        }
    }

    @Override
    protected void onDestroy() {
        YGOStarter.onDestroy(this);
        super.onDestroy();
        mResCheckTask.unregisterMReceiver();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (ACTION_RELOAD.equals(intent.getAction())) {
            checkResourceDownload((error, isNew) -> {
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
    protected void checkResourceDownload(ResCheckListener listener) {
        mResCheckTask = new ResCheckTask(this, listener);
        if (Build.VERSION.SDK_INT >= 11) {
            mResCheckTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        } else {
            mResCheckTask.execute();
        }
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
        DialogPlus dialog = DialogPlus.show(this, null, getString(R.string.message));
        dialog.show();
        VUiKit.defer().when(() -> {
            if (IOUtils.hasAssets(this, getDatapath(Constants.CORE_PICS_ZIP))) {
                try {
                    IOUtils.copyFilesFromAssets(this, getDatapath(Constants.CORE_PICS_ZIP),
                            AppsSettings.get().getResourcePath(), true);
                    IOUtils.copyFilesFromAssets(this, getDatapath(Constants.DATABASE_NAME),
                            AppsSettings.get().getResourcePath(), true);
                    IOUtils.copyFilesFromAssets(this, getDatapath(Constants.CORE_SCRIPTS_ZIP),
                            AppsSettings.get().getResourcePath(), true);
                    IOUtils.copyFilesFromAssets(this, getDatapath(Constants.CORE_STRING_PATH),
                            AppsSettings.get().getResourcePath(), true);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).done((rs) -> {
            dialog.dismiss();
        });
    }
/*        checkResourceDownload((result, isNewVersion) -> {
            Toast.makeText(this, R.string.tip_reset_game_res, Toast.LENGTH_SHORT).show();
        });*/
}
