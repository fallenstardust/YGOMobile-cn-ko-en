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
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.ViewPager;

import com.feihua.dialogutils.util.DialogUtils;
import com.ourygo.assistant.base.listener.OnDuelAssistantListener;
import com.ourygo.assistant.util.ClipManagement;
import com.ourygo.assistant.util.DuelAssistantManagement;
import com.ourygo.assistant.util.YGODAUtil;
import com.ourygo.ygomobile.OYApplication;
import com.ourygo.ygomobile.adapter.FmPagerAdapter;
import com.ourygo.ygomobile.adapter.VerTabBQAdapter;
import com.ourygo.ygomobile.bean.FragmentData;
import com.ourygo.ygomobile.bean.YGOServer;
import com.ourygo.ygomobile.ui.fragment.MainFragment;
import com.ourygo.ygomobile.ui.fragment.McLayoutFragment;
import com.ourygo.ygomobile.ui.fragment.MyCardFragment;
import com.ourygo.ygomobile.ui.fragment.MyCardWebFragment;
import com.ourygo.ygomobile.ui.fragment.OtherFunctionFragment;
import com.ourygo.ygomobile.util.AppInfoManagement;
import com.ourygo.ygomobile.util.IntentUtil;
import com.ourygo.ygomobile.util.LogUtil;
import com.ourygo.ygomobile.util.OYDialogUtil;
import com.ourygo.ygomobile.util.OYUtil;
import com.ourygo.ygomobile.util.Record;
import com.ourygo.ygomobile.util.SdkInitUtil;
import com.ourygo.ygomobile.util.SharedPreferenceUtil;
import com.ourygo.ygomobile.util.StatUtil;
import com.ourygo.ygomobile.util.YGOUtil;
import com.ourygo.ygomobile.view.OYTabLayout;
import com.tencent.bugly.Bugly;
import com.tencent.bugly.crashreport.common.info.AppInfo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.GameUriManager;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.activities.BaseActivity;
import cn.garymb.ygomobile.ui.home.ResCheckTask;
import cn.garymb.ygomobile.ui.mycard.mcchat.management.ServiceManagement;
import cn.garymb.ygomobile.utils.FileLogUtil;
import cn.garymb.ygomobile.utils.ScreenUtil;

public class OYMainActivity extends BaseActivity implements OnDuelAssistantListener {


    private static final String TAG = "TIME-MainActivity";
    private static final int ID_MAINACTIVITY = 0;

    private Toolbar toolbar;
    private OYTabLayout tl_tab;
//    private VerticalTabLayout vtab;
    private RecyclerView rv_tab;
    private ViewPager vp_pager;
    private ImageView iv_card_query;

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
        Log.e("OyMainActivity", "创建" + (savedInstanceState != null));
        if (isHorizontal)
            setContentView(R.layout.oy_main_horizontal_activity);
        else
            setContentView(R.layout.oy_main_activity);
        isFragmentActivity=true;
        LogUtil.time(TAG, "0");
        new Thread(() -> {
            SdkInitUtil.getInstance().initX5WebView();
            initBugly();
        }).start();
        LogUtil.time(TAG, "1");
        initView();
        initData();
        LogUtil.time(TAG, "2");
        checkNotch();
        LogUtil.time(TAG, "3");
//        checkRes();
        LogUtil.time(TAG, "4");
        LogUtil.printSumTime(TAG);

    }

    public void initBugly() {
//        Bugly.init(this, OYApplication.BUGLY_ID, false);

        //检测是否有更新,不提示
        OYUtil.checkUpdate(this, false);
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
        duelAssistantCheck();
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
        if (isHorizontal) {
//            vtab.setTabSelected(1);
            vp_pager.setCurrentItem(1);
        }else {
            tl_tab.setCurrentTab(1);
        }
    }

    private void initView() {

        if (isHorizontal) {
//            vtab = findViewById(R.id.vTab);
            rv_tab=findViewById(R.id.rv_tab);
            rv_tab.setLayoutManager(new LinearLayoutManager(this));
        }else {
            tl_tab = findViewById(R.id.tl_tab);
            iv_card_query = findViewById(R.id.iv_card_query);
        }

        vp_pager = findViewById(R.id.vp_pager);
        LogUtil.time(TAG,"1.2");
//        String mcName = SharedPreferenceUtil.getMyCardUserName();
//        refreshMyCardUser(mcName);
        mainFragment = new MainFragment();
        myCardWebFragment = new MyCardWebFragment();
//        myCardFragment = new MyCardFragment();
        mcLayoutFragment = new McLayoutFragment();
        otherFunctionFragment = new OtherFunctionFragment();
        LogUtil.time(TAG,"1.3");
        dialogUtils = DialogUtils.getInstance(this);
        fragmentList = new ArrayList<>();

        fragmentList.add(FragmentData.toFragmentData(s(R.string.homepage),R.drawable.ic_home_gray, mainFragment));
        fragmentList.add(FragmentData.toFragmentData(s(R.string.mycard),R.drawable.ic_mycard, mcLayoutFragment));
        fragmentList.add(FragmentData.toFragmentData(s(R.string.other_funstion),R.drawable.ic_other_gray, otherFunctionFragment));

    }

    public void initData() {
        vp_pager.setAdapter(new FmPagerAdapter(getSupportFragmentManager(), fragmentList));
//        tl_tab.setTabMode(TabLayout.MODE_FIXED);
        //缓存两个页面
        vp_pager.setOffscreenPageLimit(3);

        LogUtil.time(TAG,"1.4");
        //TabLayout加载viewpager
        if (isHorizontal) {
            VerTabBQAdapter verTabBQAdapter=new VerTabBQAdapter(fragmentList);
            rv_tab.setAdapter(verTabBQAdapter);
            verTabBQAdapter.setOnItemClickListener((adapter, view, position) -> {
                if (position!=verTabBQAdapter.getSelectPosition()){
                    verTabBQAdapter.setSelectPosition(position);
                    vp_pager.setCurrentItem(position);
                }
            });
            vp_pager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
                @Override
                public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

                }

                @Override
                public void onPageSelected(int position) {
                    Log.e("OYMainac","滑动监听"+position);
                    verTabBQAdapter.setSelectPosition(position);
                }

                @Override
                public void onPageScrollStateChanged(int state) {

                }
            });
//            vtab.setupWithViewPager(vp_pager);
//
//            vtab.setTabAdapter(new TabAdapter() {
//                @Override
//                public int getCount() {
//                    return 1;
//                }
//
//                @Override
//                public ITabView.TabBadge getBadge(int position) {
//                    return null;
//                }
//
//                @Override
//                public ITabView.TabIcon getIcon(int position) {
//                    return new ITabView.TabIcon.Builder().setIcon(R.drawable.ic_lizi,R.drawable.ic_lizi).build();
//                }
//
//                @Override
//                public ITabView.TabTitle getTitle(int position) {
//                    return new QTabView.TabTitle.Builder().setContent("首页").build();
//                }
//
//                @Override
//                public int getBackground(int position) {
//                    return 0;
//                }
//            });
//            vtab.setTabSelected(0);
        } else {
            LogUtil.time(TAG,"1.4.1");
            tl_tab.setViewPager(vp_pager);
            LogUtil.time(TAG,"1.4.2");
            tl_tab.setCurrentTab(0);
            LogUtil.time(TAG,"1.5");
            iv_card_query.setOnClickListener(
                    view -> startActivity(IntentUtil.getWebIntent(OYMainActivity.this,Record.YGO_CARD_QUERY_URL)));
        }

        initDuelAssistant();
        checkIntent();

        if (SharedPreferenceUtil.isFristStart()) {
            View[] views = dialogUtils.dialogt(null, "欢迎使用YGOMobile OY,本软件为YGOMobile原版的简约探索版，" +
                    "这里有正在探索的功能，但相对没有原版稳定，你可以选择下载原版使用，下载地址：https://www.pgyer.com/ygomobilecn\n\n" +
                    "如果你觉得好用，可以对我们进行支持，每一份支持都将帮助我们更好的建设平台");
            Dialog dialog = dialogUtils.getDialog();
            Button b1, b2;
            b1 = (Button) views[0];
            b2 = (Button) views[1];
            b1.setText("取消");
            b2.setText("支持我们");
            b1.setOnClickListener(v -> dialog.dismiss());
            b2.setOnClickListener(v -> {
                dialog.dismiss();
                OYUtil.startAifadian(OYMainActivity.this);
            });
            TextView tv_message = dialogUtils.getMessageTextView();
            tv_message.setLineSpacing(OYUtil.dp2px(3), 1f);
            SharedPreferenceUtil.setFirstStart(false);
            SharedPreferenceUtil.setNextAifadianNum(SharedPreferenceUtil.getAppStartTimes() + (10 + (int) (Math.random() * 20)));

            dialog.setOnDismissListener(dialog12 -> {
                Button b3 = dialogUtils.dialogt1("卡组导入提示", "YGOMobile OY储存路径为内部储存/ygocore，如果你之前有使用过原版" +
                        "，可以打开原版软件，点击下边栏的卡组选项——功能菜单——备份/还原来导入或导出原版ygo中的卡组");
                Dialog dialog1 = dialogUtils.getDialog();
                b3.setOnClickListener(v -> dialog1.dismiss());
                TextView tv_message1 = dialogUtils.getMessageTextView();
                tv_message1.setLineSpacing(OYUtil.dp2px(3), 1f);
                dialog1.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {

                    }
                });
            });

        }
        if (SharedPreferenceUtil.getNextAifadianNum() == SharedPreferenceUtil.getAppStartTimes()) {
            View[] views1 = dialogUtils.dialogt(null, "如果喵觉得软件好用，可以对我们进行支持，每一份支持都将帮助我们更好的建设平台");
            Dialog dialog1 = dialogUtils.getDialog();
            Button b11, b21;
            b11 = (Button) views1[0];
            b21 = (Button) views1[1];
            b11.setText("取消");
            b21.setText("支持我们");
            b11.setOnClickListener(v -> dialogUtils.dis());
            b21.setOnClickListener(v -> {
                dialog1.dismiss();
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
                        OYUtil.snackExceptionToast(OYMainActivity.this, vp_pager, "加入房间失败", exception);
                });
                return;
            } else if (Constants.URI_DECK.equals(uri.getHost())) {
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
        if (isHorizontal) {
//            if (vtab.getSelectedTabPosition() != 0) {
//                vtab.setTabSelected(0);
//                return;
//            }
            if (vp_pager.getCurrentItem()!=0){
                vp_pager.setCurrentItem(0);
                return;
            }

        } else {
            if (tl_tab.getCurrentTab() != 0) {
                tl_tab.setCurrentTab(0);
                return;
            }
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

    @Override
    protected void onDestroy() {
        ServiceManagement.getDx().disClass();
        AppInfoManagement.getInstance().close();
        super.onDestroy();
    }
}
