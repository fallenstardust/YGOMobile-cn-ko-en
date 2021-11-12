package com.ourygo.ygomobile.ui.activity;

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.viewpager.widget.ViewPager;

import com.feihua.dialogutils.util.DialogUtils;
import com.ourygo.assistant.base.listener.OnDuelAssistantListener;
import com.ourygo.assistant.util.ClipManagement;
import com.ourygo.assistant.util.DuelAssistantManagement;
import com.ourygo.assistant.util.YGODAUtil;
import com.ourygo.ygomobile.OYApplication;
import com.ourygo.ygomobile.adapter.FmPagerAdapter;
import com.ourygo.ygomobile.bean.FragmentData;
import com.ourygo.ygomobile.bean.YGOServer;
import com.ourygo.ygomobile.ui.fragment.MainFragment;
import com.ourygo.ygomobile.ui.fragment.McLayoutFragment;
import com.ourygo.ygomobile.ui.fragment.MyCardFragment;
import com.ourygo.ygomobile.ui.fragment.MyCardWebFragment;
import com.ourygo.ygomobile.ui.fragment.OtherFunctionFragment;
import com.ourygo.ygomobile.util.LogUtil;
import com.ourygo.ygomobile.util.OYDialogUtil;
import com.ourygo.ygomobile.util.OYUtil;
import com.ourygo.ygomobile.util.SdkInitUtil;
import com.ourygo.ygomobile.util.SharedPreferenceUtil;
import com.ourygo.ygomobile.util.StatUtil;
import com.ourygo.ygomobile.util.YGOUtil;
import com.ourygo.ygomobile.view.OYTabLayout;
import com.tencent.bugly.Bugly;
//import com.tencent.bugly.Bugly;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.GameUriManager;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.activities.BaseActivity;
import cn.garymb.ygomobile.ui.home.MainActivity;
import cn.garymb.ygomobile.ui.home.ResCheckTask;
import cn.garymb.ygomobile.utils.FileLogUtil;
import cn.garymb.ygomobile.utils.ScreenUtil;

public class OYMainActivity extends BaseActivity implements OnDuelAssistantListener {



    private static final String TAG = "TIME-MainActivity";
    private static final int ID_MAINACTIVITY = 0;

    private Toolbar toolbar;
    private OYTabLayout tl_tab;
    private ViewPager vp_pager;

    private List<FragmentData> fragmentList;

    private MainFragment mainFragment;
    private MyCardWebFragment myCardWebFragment;
    private MyCardFragment myCardFragment;
    private McLayoutFragment mcLayoutFragment;
    private OtherFunctionFragment otherFunctionFragment;
    private ResCheckTask mResCheckTask;
    private DuelAssistantManagement duelAssistantManagement;
    private DialogUtils dialogUtils;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_mian_oy);
        LogUtil.time(TAG, "0");
        new Thread(() -> SdkInitUtil.getInstance().initX5WebView()).start();
        LogUtil.time(TAG, "1");
        initView();
        LogUtil.time(TAG, "2");
        checkNotch();
        LogUtil.time(TAG, "3");
//        checkRes();
        initBugly();
        LogUtil.time(TAG, "4");
        LogUtil.printSumTime(TAG);

    }

    public void initBugly() {
        Bugly.init(this, OYApplication.BUGLY_ID, false);

        //检测是否有更新,不提示
//        OYUtil.checkUpdate(false, true);
    }

    protected void checkResourceDownload(ResCheckTask.ResCheckListener listener) {

        mResCheckTask = new ResCheckTask(this, listener);
        mResCheckTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
//        if (Build.VERSION.SDK_INT >= 11) {
//            mResCheckTask.exefcuteOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
//        } else {
//            mResCheckTask.execute();
//        }
    }

    private void checkNotch() {
        ScreenUtil.findNotchInformation(OYMainActivity.this, (isNotch, notchHeight, phoneType) -> {
            try {
                FileLogUtil.writeAndTime("检查刘海" + isNotch + "   " + notchHeight);
            } catch (IOException e) {
                e.printStackTrace();
            }
            AppsSettings.get().setNotchHeight(notchHeight);
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        StatUtil.onResume(this,true);
        duelAssistantCheck();
    }

    @Override
    protected void onPause() {
        super.onPause();
        StatUtil.onPause(this);
    }

    private void duelAssistantCheck() {
        if (AppsSettings.get().isServiceDuelAssistant()) {
            Handler handler = new Handler();
            handler.postDelayed(() -> {
                try {
                    FileLogUtil.writeAndTime("主页决斗助手检查");
                } catch (IOException e) {
                    e.printStackTrace();
                }
                duelAssistantManagement.checkClip(ID_MAINACTIVITY);
            }, 500);
        }
    }

    private void checkRes() {
        checkResourceDownload((error, isNew) -> {
//            if (error < 0) {
//                enableStart = false;
//            } else {
//                enableStart = true;
//            }
//            if (isNew) {
//                if (!getGameUriManager().doIntent(getIntent())) {
//                    DialogPlus dialog = new DialogPlus(this)
//                            .setTitleText(getString(R.string.settings_about_change_log))
//                            .loadUrl("file:///android_asset/changelog.html", Color.TRANSPARENT)
//                            .hideButton()
//                            .setOnCloseLinster((dlg) -> {
//                                dlg.dismiss();
//                                //mImageUpdater
//                                if (NETWORK_IMAGE && NetUtils.isConnected(getContext())) {
//                                    if (!mImageUpdater.isRunning()) {
//                                        mImageUpdater.start();
//                                    }
//                                }
//                            });
//                    dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
//                        @Override
//                        public void onDismiss(DialogInterface dialogInterface) {
//                            PermissionUtil.isServicePermission(cn.garymb.ygomobile.ui.home.MainActivity.this, true);
//
//                        }
//                    });
//                    dialog.show();
//                }
//            } else {
//                PermissionUtil.isServicePermission(cn.garymb.ygomobile.ui.home.MainActivity.this, true);
//                getGameUriManager().doIntent(getIntent());
//            }

        });
    }

    private void initDuelAssistant() {
        duelAssistantManagement = DuelAssistantManagement.getInstance();
        duelAssistantManagement.init(getApplicationContext());
        duelAssistantManagement.addDuelAssistantListener(this);
//        YGOUtil.startDuelService(this);
    }

    public void selectMycard() {
        tl_tab.setCurrentTab(1);
    }

    private void initView() {

        tl_tab = findViewById(R.id.tl_tab);
        vp_pager = findViewById(R.id.vp_pager);

        String mcName = SharedPreferenceUtil.getMyCardUserName();
        refreshMyCardUser(mcName);

        mainFragment = new MainFragment();
        myCardWebFragment = new MyCardWebFragment();
        myCardFragment = new MyCardFragment();
        mcLayoutFragment = new McLayoutFragment();
        otherFunctionFragment = new OtherFunctionFragment();

        dialogUtils = DialogUtils.getInstance(this);
        fragmentList = new ArrayList<>();

        fragmentList.add(FragmentData.toFragmentData(s(R.string.homepage), mainFragment));
        fragmentList.add(FragmentData.toFragmentData(s(R.string.mycard), mcLayoutFragment));
        fragmentList.add(FragmentData.toFragmentData(s(R.string.other_funstion), otherFunctionFragment));

        vp_pager.setAdapter(new FmPagerAdapter(getSupportFragmentManager(), fragmentList));
//        tl_tab.setTabMode(TabLayout.MODE_FIXED);
        //缓存两个页面
        vp_pager.setOffscreenPageLimit(3);
        //TabLayout加载viewpager
        tl_tab.setViewPager(vp_pager);
        tl_tab.setCurrentTab(0);

        initDuelAssistant();
        checkIntent();

        if (SharedPreferenceUtil.isFristStart()){
            View[] views=dialogUtils.dialogt(null,"欢迎使用YGOMobile OY,本软件为YGOMobile原版的简约探索版，" +
                    "这里有正在探索的功能，但相对没有原版稳定，你可以选择下载原版使用，下载地址：https://www.pgyer.com/ygomobilecn\n\n" +
                    "如果你觉得好用，可以对我们进行支持，每一份支持都将帮助我们更好的建设平台");
            Dialog dialog=dialogUtils.getDialog();
            Button b1,b2;
            b1= (Button) views[0];
            b2= (Button) views[1];
            b1.setText("取消");
            b2.setText("支持我们");
            b1.setOnClickListener(v -> dialog.dismiss());
            b2.setOnClickListener(v -> {
                dialog.dismiss();
                OYUtil.startAifadian(OYMainActivity.this);
            });
            TextView tv_message=dialogUtils.getMessageTextView();
            tv_message.setLineSpacing(OYUtil.dp2px(3),1f);
            SharedPreferenceUtil.setFirstStart(false);
            SharedPreferenceUtil.setNextAifadianNum(SharedPreferenceUtil.getAppStartTimes()+(10+ (int) (Math.random() * 20)));

            dialog.setOnDismissListener(dialog12 -> {
                Button b3=dialogUtils.dialogt1("卡组导入提示","YGOMobile OY储存路径为内部储存/ygcore，如果你之前有使用过原版" +
                        "，可以打开原版软件，点击主页右下角的功能菜单——卡组编辑——功能菜单——备份/还原来导入或导出原版ygo中的卡组");
               Dialog dialog1=dialogUtils.getDialog();
                b3.setOnClickListener(v -> dialog1.dismiss());
                TextView tv_message1 =dialogUtils.getMessageTextView();
                tv_message1.setLineSpacing(OYUtil.dp2px(3),1f);
                dialog1.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {

                    }
                });
            });

        }
        if (SharedPreferenceUtil.getNextAifadianNum()==SharedPreferenceUtil.getAppStartTimes()){
            View[] views=dialogUtils.dialogt(null,"如果喵觉得软件好用，可以对我们进行支持，每一份支持都将帮助我们更好的建设平台");
            Dialog dialog=dialogUtils.getDialog();
            Button b1,b2;
            b1= (Button) views[0];
            b2= (Button) views[1];
            b1.setText("取消");
            b2.setText("支持我们");
            b1.setOnClickListener(v -> dialogUtils.dis());
            b2.setOnClickListener(v -> {
                dialog.dismiss();
                OYUtil.startAifadian(OYMainActivity.this);
            });
        }
    }

    private void checkIntent() {

        Intent intent = getIntent();

//        if (!Intent.ACTION_VIEW.equals(intent.getAction()))
//            return;
        if (intent.getData() != null) {
            Uri uri = getIntent().getData();

            if (Constants.URI_ROOM.equals(uri.getHost())) {
                duelAssistantManagement.setLastMessage(ClipManagement.getInstance().getClipMessage());
                YGODAUtil.deRoomListener(uri, (host1, port, password, exception) -> {
                    if (TextUtils.isEmpty(exception))
                        joinDARoom(host1, port, password);
                    else
                        OYUtil.snackExceptionToast(OYMainActivity.this, tl_tab, "加入房间失败", exception);
                });
                return;
            }else if (Constants.URI_DECK.equals(uri.getHost())){
                duelAssistantManagement.setLastMessage(ClipManagement.getInstance().getClipMessage());
            }
        }
        new GameUriManager(this).doIntent(intent);
    }

    public void refreshMyCardUser(String name) {
//        if (TextUtils.isEmpty(name)) {
//            tv_name.setText(getString(R.string.no_login));
//            iv_avatar.setImageResource(R.drawable.avatar);
//            iv_avatar.setOnClickListener(new View.OnClickListener() {
//                @Override
//                public void onClick(View v) {
//                    vp_pager.setCurrentItem(1);
//                }
//            });
//            tv_name.setOnClickListener(new View.OnClickListener() {
//                @Override
//                public void onClick(View v) {
//                    vp_pager.setCurrentItem(1);
//                }
//            });
//        } else {
//            tv_name.setText(name);
//            ImageUtil.setImage(this, MyCardUtil.getAvatarUrl(name), iv_avatar);
//        }
    }

    @Override
    public void onBackPressed() {
        if (tl_tab.getCurrentTab() != 0) {
            tl_tab.setCurrentTab(0);
            return;
        }
        super.onBackPressed();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.e("MainF", requestCode + " 返回1 " + resultCode);
    }

    @Override
    public void onJoinRoom(String host, int port, String password, int id) {
        if (id == ID_MAINACTIVITY) {
            joinDARoom(host, port, password);
        }
    }

    public void joinDARoom(String host, int port, String password) {
        YGOUtil.getYGOServerList(serverList -> {
            YGOServer ygoServer = serverList.getServerInfoList().get(0);
            if (!TextUtils.isEmpty(host)) {
                ygoServer.setServerAddr(host);
                ygoServer.setPort(port);
            }
            OYDialogUtil.dialogDAJoinRoom(OYMainActivity.this, ygoServer, password);

        });
    }

    @Override
    public void onCardSearch(String key, int id) {

    }

    @Override
    public void onSaveDeck(String message, boolean isUrl, int id) {
        saveDeck(message, isUrl);
    }

    @Override
    public boolean isListenerEffective() {
        return OYUtil.isContextExisted(this);
    }


    private void saveDeck(String deckMessage, boolean isUrl) {
        OYDialogUtil.dialogDASaveDeck(this, deckMessage, isUrl ? OYDialogUtil.DECK_TYPE_URL : OYDialogUtil.DECK_TYPE_MESSAGE);
    }


}
