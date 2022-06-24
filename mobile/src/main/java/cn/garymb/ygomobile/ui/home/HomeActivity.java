package cn.garymb.ygomobile.ui.home;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.navigation.ui.AppBarConfiguration;

import com.ashokvarma.bottomnavigation.BottomNavigationBar;
import com.ashokvarma.bottomnavigation.BottomNavigationItem;
import com.ourygo.assistant.base.listener.OnDuelAssistantListener;
import com.ourygo.assistant.util.DuelAssistantManagement;
import com.ourygo.assistant.util.Util;
import com.tencent.bugly.beta.Beta;
import com.tencent.smtt.sdk.QbSdk;

import java.io.File;
import java.io.IOException;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.bean.Deck;
import cn.garymb.ygomobile.lite.BuildConfig;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.activities.BaseActivity;
import cn.garymb.ygomobile.ui.cards.CardSearchFragment;
import cn.garymb.ygomobile.ui.cards.DeckManagerFragment;
import cn.garymb.ygomobile.ui.cards.deck.DeckUtils;
import cn.garymb.ygomobile.ui.mycard.MycardFragment;
import cn.garymb.ygomobile.ui.plus.DialogPlus;
import cn.garymb.ygomobile.ui.preference.fragments.SettingFragment;
import cn.garymb.ygomobile.utils.FileLogUtil;
import cn.garymb.ygomobile.utils.ScreenUtil;
import cn.garymb.ygomobile.utils.YGOUtil;

public abstract class HomeActivity extends BaseActivity implements OnDuelAssistantListener {

    private static final int ID_MAINACTIVITY = 0;

    long exitLasttime = 0;

    private HomeFragment fragment_home;
    private CardSearchFragment fragment_search;
    private DeckManagerFragment fragment_deck_cards;
    private MycardFragment fragment_mycard;
    private SettingFragment fragment_settings;


    private DuelAssistantManagement duelAssistantManagement;
    private AppBarConfiguration appBarConfiguration;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        setExitAnimEnable(false);

        QbSdk.PreInitCallback cb = new QbSdk.PreInitCallback() {
            @Override
            public void onViewInitFinished(boolean arg0) {
                //x5內核初始化完成的回调，为true表示x5内核加载成功，否则表示x5内核加载失败，会自动切换到系统内核。
                if (arg0) {
                    Toast.makeText(getActivity(), "加载X5内核成功", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getActivity(), "加载系统内核成功", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onCoreInitFinished() {
            }
        };
        //x5内核初始化接口
        QbSdk.initX5Environment(this, cb);
        if (!BuildConfig.BUILD_TYPE.equals("debug")) {
            //release才检查版本
            if (!Constants.ACTION_OPEN_GAME.equals(getIntent().getAction())) {
                Beta.checkUpgrade(false, false);
            }
        }
        //初始化决斗助手
        initDuelAssistant();
        //
        checkNotch();
        //showNewbieGuide("homePage");
        initBottomNavigationBar();
    }

    private void initBottomNavigationBar() {
        // 获取页面上的底部导航栏控件
        BottomNavigationBar bottomNavigationBar = (BottomNavigationBar) findViewById(R.id.bottom_navigation_bar);
        bottomNavigationBar
                .addItem(new BottomNavigationItem(R.drawable.ic_home, R.string.mc_home))
                .addItem(new BottomNavigationItem(R.drawable.ic_search, R.string.search))
                .addItem(new BottomNavigationItem(R.drawable.ic_album, R.string.deck_manager))
                .addItem(new BottomNavigationItem(R.drawable.ic_add, R.string.mycard))
                .addItem(new BottomNavigationItem(R.drawable.ic_settings, R.string.settings))
                .setActiveColor(R.color.holo_blue_bright)
                .setBarBackgroundColor(R.color.transparent)
                .setMode(BottomNavigationBar.MODE_FIXED)
                .initialise();//所有的设置需在调用该方法前完成
        bottomNavigationBar.setTabSelectedListener(new BottomNavigationBar.OnTabSelectedListener() {
            @Override
            public void onTabSelected(int position) {
                //未选中->选中
            }

            @Override
            public void onTabUnselected(int position) {
                //选中->未选中
            }

            @Override
            public void onTabReselected(int position) {
                //选中->选中
            }
        });
        fragment_home = new HomeFragment();
        fragment_search = new CardSearchFragment();
        fragment_deck_cards = new DeckManagerFragment();
        fragment_mycard = new MycardFragment();
        getSupportFragmentManager().beginTransaction()
                .add(R.id.fragment_home, fragment_home)
                .add(R.id.fragment_search, fragment_search)
                .add(R.id.fragment_deck_cards, fragment_deck_cards)
                .add(R.id.fragment_mycard, fragment_mycard)
                //.add(R.id.fragment_settings,new Fragment())
                .commit();
        getSupportActionBar().hide();
    }

    @Override
    protected void onResume() {
        super.onResume();
        duelAssistantCheck();
    }

    @Override
    protected void onStop() {
        //mImageLoader.clearZipCache();
        super.onStop();
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


    @Override
    protected void onStart() {
        super.onStart();

    }

    @Override
    public void onJoinRoom(String host, int port, String password, int id) {
    }

    @Override
    public void onCardSearch(String key, int id) {
        if (id == ID_MAINACTIVITY) {
            Intent intent = new Intent(this, CardSearchFragment.class);
            intent.putExtra(CardSearchFragment.SEARCH_MESSAGE, key);
            startActivity(intent);
        }
    }

    @Override
    public void onSaveDeck(String message, boolean isUrl, int id) {
        if (id == ID_MAINACTIVITY) {
            saveDeck(message, isUrl);
        }
    }

    @Override
    public boolean isListenerEffective() {
        return Util.isContextExisted(this);
    }


    private void initDuelAssistant() {
        duelAssistantManagement = DuelAssistantManagement.getInstance();
        duelAssistantManagement.init(getApplicationContext());
        duelAssistantManagement.addDuelAssistantListener(this);
//        YGOUtil.startDuelService(this);
    }

    //检查是否有刘海
    private void checkNotch() {
        ScreenUtil.findNotchInformation(HomeActivity.this, new ScreenUtil.FindNotchInformation() {
            @Override
            public void onNotchInformation(boolean isNotch, int notchHeight, int phoneType) {
                AppsSettings.get().setNotchHeight(notchHeight);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        duelAssistantManagement.removeDuelAssistantListener(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        return super.onOptionsItemSelected(item);

    }

    @Override
    public HomeActivity getActivity() {
        return this;
    }

    @Override
    public void startActivity(Intent intent) {
        super.startActivity(intent);
    }

    @Override
    public void onBackPressed() {
        if (System.currentTimeMillis() - exitLasttime <= 3000) {
            super.onBackPressed();
        } else {
            showToast(R.string.back_tip, Toast.LENGTH_SHORT);
            exitLasttime = System.currentTimeMillis();
        }
    }


    protected abstract void checkResourceDownload(ResCheckTask.ResCheckListener listener);

    protected abstract void openGame();

    public abstract void updateImages();

    private void saveDeck(String deckMessage, boolean isUrl) {
        DialogPlus dialog = new DialogPlus(this);
        dialog.setTitle(R.string.question);
        dialog.setMessage(R.string.find_deck_text);
        dialog.setMessageGravity(Gravity.CENTER_HORIZONTAL);
        dialog.setLeftButtonText(R.string.Cancel);
        dialog.setRightButtonText(R.string.save_n_open);
        dialog.show();
        dialog.setLeftButtonListener((dlg, s) -> {
            dialog.dismiss();
        });
        dialog.setRightButtonListener((dlg, s) -> {
            dialog.dismiss();
            //如果是卡组url
            if (isUrl) {
                Deck deckInfo = new Deck(getString(R.string.rename_deck) + System.currentTimeMillis(), Uri.parse(deckMessage));
                File file = deckInfo.saveTemp(AppsSettings.get().getDeckDir());
                if (!deckInfo.isCompleteDeck()) {
                    YGOUtil.show("当前卡组缺少完整信息，将只显示已有卡片");
                }
                DeckManagerFragment.start(this, file.getAbsolutePath());
            } else {
                //如果是卡组文本
                try {
                    //以当前时间戳作为卡组名保存卡组
                    File file = DeckUtils.save(getString(R.string.rename_deck) + System.currentTimeMillis(), deckMessage);
                    DeckManagerFragment.start(this, file.getAbsolutePath());
                } catch (IOException e) {
                    e.printStackTrace();
                    Toast.makeText(this, getString(R.string.save_failed_bcos) + e, Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

/*
    //https://www.jianshu.com/p/99649af3b191
    public void showNewbieGuide(String scene) {
        HighlightOptions options = new HighlightOptions.Builder()//绘制一个高亮虚线圈
                .setOnHighlightDrewListener(new OnHighlightDrewListener() {
                    @Override
                    public void onHighlightDrew(Canvas canvas, RectF rectF) {
                        Paint paint = new Paint();
                        paint.setColor(Color.WHITE);
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setStrokeWidth(20);
                        paint.setPathEffect(new DashPathEffect(new float[]{20, 20}, 0));
                        canvas.drawCircle(rectF.centerX(), rectF.centerY(), rectF.width() / 2 + 10, paint);
                    }
                }).build();
        HighlightOptions options2 = new HighlightOptions.Builder()//绘制一个高亮虚线矩形
                .setOnHighlightDrewListener(new OnHighlightDrewListener() {
                    @Override
                    public void onHighlightDrew(Canvas canvas, RectF rectF) {
                        Paint paint = new Paint();
                        paint.setColor(Color.WHITE);
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setStrokeWidth(20);
                        paint.setPathEffect(new DashPathEffect(new float[]{20, 20}, 0));
                        canvas.drawRect(rectF, paint);
                    }
                }).build();
        if (scene == "homePage") {
            NewbieGuide.with(this)//with方法可以传入Activity或者Fragment，获取引导页的依附者
                    .setLabel("homepageGuide")
                    .addGuidePage(
                            GuidePage.newInstance().setEverywhereCancelable(true)
                                    .setBackgroundColor(0xbc000000)
                                    .addHighLightWithOptions(findViewById(R.id.menu), HighLight.Shape.CIRCLE, options)
                                    .setLayoutRes(R.layout.view_guide_home)
                                    .setOnLayoutInflatedListener(new OnLayoutInflatedListener() {

                                        @Override
                                        public void onLayoutInflated(View view, Controller controller) {
                                            //可只创建一个引导layout并把相关内容都放在其中并GONE，获得ID并初始化相应为显示
                                            view.findViewById(R.id.view_abt_menu).setVisibility(View.VISIBLE);
                                        }
                                    })

                    )
                    .addGuidePage(
                            GuidePage.newInstance().setEverywhereCancelable(true)
                                    .setBackgroundColor(0xbc000000)
                                    .addHighLightWithOptions(findViewById(R.id.mycard), HighLight.Shape.CIRCLE, options)
                                    .setLayoutRes(R.layout.view_guide_home)
                                    .setOnLayoutInflatedListener(new OnLayoutInflatedListener() {

                                        @Override
                                        public void onLayoutInflated(View view, Controller controller) {
                                            TextView tv = view.findViewById(R.id.text_about);
                                            tv.setVisibility(View.VISIBLE);
                                            tv.setText(R.string.guide_mycard);
                                        }
                                    })
                    )
                    .addGuidePage(
                            GuidePage.newInstance().setEverywhereCancelable(true)
                                    .setBackgroundColor(0xbc000000)
                                    .addHighLightWithOptions(findViewById(R.id.list_server), HighLight.Shape.ROUND_RECTANGLE, options2)
                                    .setLayoutRes(R.layout.view_guide_home)
                                    .setOnLayoutInflatedListener(new OnLayoutInflatedListener() {

                                        @Override
                                        public void onLayoutInflated(View view, Controller controller) {
                                            TextView tv = view.findViewById(R.id.text_about);
                                            tv.setVisibility(View.VISIBLE);
                                            tv.setText(R.string.guide_serverlist);
                                        }
                                    })
                    )
                    .addGuidePage(
                            GuidePage.newInstance().setEverywhereCancelable(true)
                                    .setBackgroundColor(0xbc000000)
                                    .setLayoutRes(R.layout.view_guide_home)
                                    .setOnLayoutInflatedListener(new OnLayoutInflatedListener() {

                                        @Override
                                        public void onLayoutInflated(View view, Controller controller) {
                                            view.findViewById(R.id.view_abt_server_edit).setVisibility(View.VISIBLE);
                                        }
                                    })
                    )
                    //.alwaysShow(true)//总是显示，调试时可以打开
                    .show();
        } else if (scene == "joinRoom") {
            NewbieGuide.with(this)
                    .setLabel("joinRoomGuide")
                    .addGuidePage(
                            GuidePage.newInstance().setEverywhereCancelable(true)
                                    .setBackgroundColor(0xbc000000)
                                    .setLayoutRes(R.layout.view_guide_home)
                                    .setOnLayoutInflatedListener(new OnLayoutInflatedListener() {

                                        @Override
                                        public void onLayoutInflated(View view, Controller controller) {
                                            view.findViewById(R.id.view_abt_join_room).setVisibility(View.VISIBLE);
                                        }
                                    })

                    )
                    .show();
        }
    }*/
}
