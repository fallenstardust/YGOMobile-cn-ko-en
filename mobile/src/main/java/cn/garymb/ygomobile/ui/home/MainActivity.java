package cn.garymb.ygomobile.ui.home;

import static cn.garymb.ygomobile.Constants.ACTION_RELOAD;
import static cn.garymb.ygomobile.Constants.NETWORK_IMAGE;
import static cn.garymb.ygomobile.Constants.ORI_DECK;
import static cn.garymb.ygomobile.ui.home.ResCheckTask.ResCheckListener;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;

import java.io.File;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.GameUriManager;
import cn.garymb.ygomobile.YGOStarter;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.activities.WebActivity;
import cn.garymb.ygomobile.ui.cards.CardFavorites;
import cn.garymb.ygomobile.ui.plus.DialogPlus;
import cn.garymb.ygomobile.ui.plus.VUiKit;
import cn.garymb.ygomobile.utils.FileUtils;
import cn.garymb.ygomobile.utils.NetUtils;

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
            //加载收藏夹
            CardFavorites.get().load();
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
                            WebActivity.open(this, getString(R.string.masterrule), Constants.URL_MASTER_RULE_CN);
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
                    dialog.setOnDismissListener(dialogInterface -> {
//                        if (AppsSettings.get().isServiceDuelAssistant() && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
//                            YGOUtil.isServicePermission(MainActivity.this, true);
                        File oriDeckFiles = new File(ORI_DECK);
                        File deckFiles = new File(AppsSettings.get().getDeckDir());
                        if (oriDeckFiles.exists() && deckFiles.list().length <= 1) {
                            DialogPlus dlgpls = new DialogPlus(MainActivity.this);
                            dlgpls.setTitle(R.string.tip);
                            dlgpls.setMessage(R.string.restore_deck);
                            dlgpls.setLeftButtonText(R.string.Cancel);
                            dlgpls.setLeftButtonListener((dlg, i) -> {
                                dlgpls.dismiss();
                            });
                            dlgpls.setRightButtonText(R.string.deck_restore);
                            dlgpls.setRightButtonListener((dlg, i) -> {
                                startPermissionsActivity();
                                dlgpls.dismiss();
                            });
                            dlgpls.show();
                        }

                    });
                    dialog.show();
                }
            } else {
//                if (AppsSettings.get().isServiceDuelAssistant() && Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
//                    YGOUtil.isServicePermission(MainActivity.this, true);
                getGameUriManager().doIntent(getIntent());
            }

        });
    }

    @Override
    protected void onPermission(boolean isOk) {
        super.onPermission(isOk);
        if (isOk) {
            try {
                FileUtils.copyDir(ORI_DECK, AppsSettings.get().getDeckDir(), false);
            } catch (Throwable e) {
                Toast.makeText(MainActivity.this, e + "", Toast.LENGTH_SHORT).show();
            }
            Toast.makeText(MainActivity.this, R.string.done, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        YGOStarter.onResumed(this);
    }

    @Override
    protected void onDestroy() {
        YGOStarter.onDestroy(this);
        super.onDestroy();
        if (mResCheckTask.mReceiver != null)
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
    public void updateImages() {}

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == Constants.REQUEST_SETTINGS_CODE) {
            //TODO
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    /*        checkResourceDownload((result, isNewVersion) -> {
                Toast.makeText(this, R.string.tip_reset_game_res, Toast.LENGTH_SHORT).show();
            });*/
}
