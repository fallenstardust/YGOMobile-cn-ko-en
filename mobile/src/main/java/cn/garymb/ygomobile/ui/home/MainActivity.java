package cn.garymb.ygomobile.ui.home;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

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
import libwindbot.windbot.WindBot;

import static cn.garymb.ygomobile.Constants.ACTION_RELOAD;
import static cn.garymb.ygomobile.Constants.CORE_PICS_ZIP;
import static cn.garymb.ygomobile.Constants.DATABASE_NAME;
import static cn.garymb.ygomobile.Constants.NETWORK_IMAGE;
import static cn.garymb.ygomobile.Constants.PREF_DEF_GAME_DIR;
import static cn.garymb.ygomobile.ui.home.ResCheckTask.getDatapath;

public class MainActivity extends HomeActivity {
    private GameUriManager mGameUriManager;
    private ImageUpdater mImageUpdater;
    private boolean enableStart;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        YGOStarter.onCreated(this);
        //checkWindbot();
        han.sendEmptyMessage(0);
        mImageUpdater = new ImageUpdater(this);
        //资源复制
        checkResourceDownload((error, isNew) -> {
            if (error < 0) {
                enableStart = false;
            } else {
                enableStart = true;
            }
            if (isNew) {
                if(!getGameUriManager().doIntent(getIntent())) {
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
        unregisterReceiver(mReceiver);
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
    protected void checkResourceDownload(ResCheckTask.ResCheckListener listener) {
        ResCheckTask task = new ResCheckTask(this, listener);
        if (Build.VERSION.SDK_INT >= 11) {
            task.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        } else {
            task.execute();
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
        DialogPlus dialog  = DialogPlus.show(this, null, getString(R.string.message));
        dialog.show();
        VUiKit.defer().when(()->{
            if (IOUtils.hasAssets(this, ResCheckTask.getDatapath(Constants.CORE_PICS_ZIP))) {
                try {
                    IOUtils.copyFilesFromAssets(this, ResCheckTask.getDatapath(Constants.CORE_PICS_ZIP),
                            AppsSettings.get().getResourcePath(), true);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).done((rs)->{
            dialog.dismiss();
        });
    }
/*        checkResourceDownload((result, isNewVersion) -> {
            Toast.makeText(this, R.string.tip_reset_game_res, Toast.LENGTH_SHORT).show();
        });*/




    public class MessageReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals("RUN_WINDBOT")) {
                String args = intent.getStringExtra("args");
                WindBot.runAndroid(args);
            }
        }
    }

    public void checkWindbot() {
        try {
            WindBot.initAndroid(getFilesDir().getPath(), AppsSettings.get().getDataBasePath() + "/" + DATABASE_NAME);
        }catch (Throwable e){
            e.printStackTrace();
        }
        MessageReceiver mReceiver = new MessageReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction("RUN_WINDBOT");
        registerReceiver(mReceiver, filter);
    }
    Handler han = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            // TODO: Implement this method
            super.handleMessage(msg);
            switch (msg.what) {
                case 0:
                    checkWindbot();
                    break;
            }
        }
    };

}
