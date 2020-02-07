package cn.garymb.ygomobile.ui.home;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import androidx.annotation.NonNull;

import java.io.IOException;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.GameUriManager;
import cn.garymb.ygomobile.YGOMobileActivity;
import cn.garymb.ygomobile.YGOStarter;
import cn.garymb.ygomobile.core.IrrlichtBridge;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.activities.WebActivity;
import cn.garymb.ygomobile.ui.plus.DialogPlus;
import cn.garymb.ygomobile.ui.plus.VUiKit;
import cn.garymb.ygomobile.utils.ComponentUtils;
import cn.garymb.ygomobile.utils.IOUtils;
import cn.garymb.ygomobile.utils.NetUtils;
import cn.garymb.ygomobile.utils.YGOUtil;

import com.ourygo.assistant.util.PermissionUtil;

import static cn.garymb.ygomobile.Constants.ACTION_RELOAD;
import static cn.garymb.ygomobile.Constants.NETWORK_IMAGE;
import static cn.garymb.ygomobile.ui.home.ResCheckTask.ResCheckListener;
import static cn.garymb.ygomobile.ui.home.ResCheckTask.getDatapath;

public class MainActivity extends HomeActivity {
    private final String[] PERMISSIONS = {
//            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.SYSTEM_ALERT_WINDOW,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
    };
    ResCheckTask mResCheckTask;
    private GameUriManager mGameUriManager;
    private ImageUpdater mImageUpdater;
    private boolean enableStart;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        YGOStarter.onCreated(this);
        mImageUpdater = new ImageUpdater(this);
        //动态权限
//        ActivityCompat.requestPermissions(this, PERMISSIONS, 0);
        //资源复制
        checkRes();
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

    private void checkRes() {
        checkResourceDownload((error, isNew) -> {
            if (error < 0) {
                enableStart = false;
            } else {
                enableStart = true;
            }
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
                            if (AppsSettings.get().isServiceDuelAssistant() && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
                                YGOUtil.isServicePermission(MainActivity.this, true);
                        }
                    });
                    dialog.show();
                }
            } else {
                if (AppsSettings.get().isServiceDuelAssistant() && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
                    YGOUtil.isServicePermission(MainActivity.this, true);
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
        if (mResCheckTask != null)
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
        Log.e("MainActivity", "重置资源");
        DialogPlus dialog = DialogPlus.show(this, null, getString(R.string.message));
        dialog.show();
        VUiKit.defer().when(() -> {
            Log.e("MainActivity", "开始复制");
            try {
                IOUtils.createNoMedia(AppsSettings.get().getResourcePath());
                if (IOUtils.hasAssets(this, getDatapath(Constants.CORE_PICS_ZIP))) {
                    IOUtils.copyFilesFromAssets(this, getDatapath(Constants.CORE_PICS_ZIP),
                            AppsSettings.get().getResourcePath(), true);
                }
                if (IOUtils.hasAssets(this, getDatapath(Constants.CORE_SCRIPTS_ZIP))) {
                    IOUtils.copyFilesFromAssets(this, getDatapath(Constants.CORE_SCRIPTS_ZIP),
                            AppsSettings.get().getResourcePath(), true);
                }
                IOUtils.copyFilesFromAssets(this, getDatapath(Constants.DATABASE_NAME),
                        AppsSettings.get().getResourcePath(), true);

                IOUtils.copyFilesFromAssets(this, getDatapath(Constants.CORE_STRING_PATH),
                        AppsSettings.get().getResourcePath(), true);

                IOUtils.copyFilesFromAssets(this, getDatapath(Constants.WINDBOT_PATH),
                        AppsSettings.get().getResourcePath(), true);

                IOUtils.copyFilesFromAssets(this, getDatapath(Constants.CORE_SKIN_PATH),
                        AppsSettings.get().getCoreSkinPath(), false);

                IOUtils.copyFilesFromAssets(this, getDatapath(Constants.CORE_SOUND_PATH),
                        AppsSettings.get().getSoundPath(), false);
            } catch (IOException e) {
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

}
