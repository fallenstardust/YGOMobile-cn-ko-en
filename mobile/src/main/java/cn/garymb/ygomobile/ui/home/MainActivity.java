package cn.garymb.ygomobile.ui.home;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.AssetManager;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;

import androidx.annotation.NonNull;

import com.feihua.dialogutils.util.DialogUtils;

import java.io.File;
import java.io.FileInputStream;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.GameUriManager;
import cn.garymb.ygomobile.YGOMobileActivity;
import cn.garymb.ygomobile.YGOStarter;
import cn.garymb.ygomobile.bean.ServerInfo;
import cn.garymb.ygomobile.bean.ServerList;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ourygo.base.OnDuelClipBoardListener;
import cn.garymb.ygomobile.ourygo.util.DuelAssistantManagement;
import cn.garymb.ygomobile.ui.activities.BaseActivity;
import cn.garymb.ygomobile.ui.activities.LogoActivity;
import cn.garymb.ygomobile.ui.activities.WebActivity;
import cn.garymb.ygomobile.ui.plus.DialogPlus;
import cn.garymb.ygomobile.ui.plus.VUiKit;
import cn.garymb.ygomobile.utils.ComponentUtils;
import cn.garymb.ygomobile.utils.DeckUtil;
import cn.garymb.ygomobile.utils.IOUtils;
import cn.garymb.ygomobile.utils.NetUtils;
import cn.garymb.ygomobile.utils.PermissionUtil;
import cn.garymb.ygomobile.utils.YGOUtil;
import libwindbot.windbot.WindBot;

import static cn.garymb.ygomobile.Constants.ASSET_SERVER_LIST;
import static cn.garymb.ygomobile.Constants.CORE_BOT_CONF_PATH;
import static cn.garymb.ygomobile.Constants.DATABASE_NAME;
import static cn.garymb.ygomobile.Constants.NETWORK_IMAGE;

public class MainActivity extends HomeActivity implements OnDuelClipBoardListener {
    private static final String TAG = "ResCheckTask";
    private GameUriManager mGameUriManager;
    private ImageUpdater mImageUpdater;
    private boolean enableStart;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        YGOStarter.onCreated(this);
        mImageUpdater = new ImageUpdater(this);
        Log.i("kk", "MainActivity:onCreate");
        boolean isNew = getIntent().getBooleanExtra(LogoActivity.EXTRA_NEW_VERSION, false);
        int err = getIntent().getIntExtra(LogoActivity.EXTRA_ERROR, ResCheckTask.ERROR_NONE);
        //资源复制
        onCheckCompleted(err, isNew);
//        if (DuelAssistantManagement.getInstance().isStart()){
//            DuelAssistantManagement.getInstance().checkMessage(YGOUtil.getCopyMessage(),this);
//            Log.e("BaseActivity","检测复制内容"+YGOUtil.getCopyMessage());
//        }

    }

    @Override
    protected void onResume() {
        super.onResume();
        YGOStarter.onResumed(this);
        //如果游戏Activity已经不存在了，则
        if (!ComponentUtils.isActivityRunning(this, new ComponentName(this, YGOMobileActivity.class))) {
            ComponentUtils.killActivity(this, new ComponentName(this, YGOMobileActivity.class));
        }
        if (DuelAssistantManagement.getInstance().isStart()){
            DuelAssistantManagement.getInstance().checkMessage(YGOUtil.getCopyMessage(),this);
        }

    }

    @Override
    protected void onDestroy() {
        YGOStarter.onDestroy(this);
        super.onDestroy();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.i("kk", "MainActivity:onNewIntent");
        getGameUriManager().doIntent(intent);
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
                        if (AppsSettings.get().isServiceDuelAssistant())
                            PermissionUtil.isServicePermission(MainActivity.this, true);

                    }
                });
                dialog.show();
            }
        } else {
            if (AppsSettings.get().isServiceDuelAssistant())
                PermissionUtil.isServicePermission(MainActivity.this, true);
            getGameUriManager().doIntent(getIntent());
        }
    }

    @Override
    public void onDeckCode(String deckCode,boolean isDebounce) {
        if (isDebounce)
            return;
        DialogPlus dialogPlus=new DialogPlus(this);
        dialogPlus.setMessage("检测到卡组，是否保存？");
        dialogPlus.setLeftButtonText("保存");
        dialogPlus.setRightButtonText("取消");
        dialogPlus.setLeftButtonListener(new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                DeckUtil.saveDeck(MainActivity.this,deckCode,false);
                dialog.dismiss();
            }
        });
        dialogPlus.setRightButtonListener(new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        dialogPlus.show();
    }

    @Override
    public void onDeckUrl(String deckUrl,boolean isDebounce) {
        if (isDebounce)
            return;

        DialogPlus dialogPlus=new DialogPlus(this);
        dialogPlus.setMessage("检测到卡组，是否保存？");
        dialogPlus.setLeftButtonText("保存");
        dialogPlus.setRightButtonText("取消");
        dialogPlus.setLeftButtonListener(new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                DeckUtil.saveDeck(MainActivity.this,deckUrl,true);
                dialog.dismiss();
            }
        });
        dialogPlus.setRightButtonListener(new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        dialogPlus.show();
    }

    @Override
    public void onCardQuery(String cardNameKey,boolean isDebounce) {
    }

    @Override
    public void onDuelPassword(String password,boolean isDebounce) {
        if (isDebounce)
            return;

        DialogPlus dialogPlus=new DialogPlus(this);
        dialogPlus.setTitle(password);
        dialogPlus.setMessage("检测到决斗密码，是否加入房间？");
        dialogPlus.setLeftButtonText("加入");
        dialogPlus.setRightButtonText("取消");
        dialogPlus.setLeftButtonListener(new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                File xmlFile = new File(getFilesDir(), Constants.SERVER_FILE);
                VUiKit.defer().when(() -> {
                    ServerList assetList = ServerListManager.readList(MainActivity.this.getAssets().open(ASSET_SERVER_LIST));
                    ServerList fileList = xmlFile.exists() ? ServerListManager.readList(new FileInputStream(xmlFile)) : null;
                    if (fileList == null) {
                        return assetList;
                    }
                    if (fileList.getVercode() < assetList.getVercode()) {
                        xmlFile.delete();
                        return assetList;
                    }
                    return fileList;
                }).done((list) -> {
                    if (list != null) {
                        ServerInfo serverInfo = list.getServerInfoList().get(0);
                        YGOUtil.duelIntent(MainActivity.this, serverInfo.getServerAddr(), serverInfo.getPort(), serverInfo.getPlayerName(), password);
                    }
                });
                dialog.dismiss();
            }
        });
        dialogPlus.setRightButtonListener(new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });
        dialogPlus.show();
    }

    @Override
    public boolean isEffective() {
        return YGOUtil.isContextExisted(this);
    }

}
