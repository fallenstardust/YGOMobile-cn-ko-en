package cn.garymb.ygomobile.ui.home;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.ashokvarma.bottomnavigation.BottomNavigationBar;
import com.ashokvarma.bottomnavigation.BottomNavigationItem;
import com.tencent.bugly.beta.Beta;
import com.tencent.smtt.sdk.QbSdk;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.lite.BuildConfig;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.activities.BaseActivity;
import cn.garymb.ygomobile.ui.cards.CardSearchFragment;
import cn.garymb.ygomobile.ui.cards.DeckManagerFragment;
import cn.garymb.ygomobile.ui.mycard.MycardFragment;
import cn.garymb.ygomobile.ui.mycard.mcchat.MycardChatFragment;
import cn.garymb.ygomobile.ui.settings.SettingFragment;
import cn.garymb.ygomobile.utils.ScreenUtil;

public abstract class HomeActivity extends BaseActivity implements BottomNavigationBar.OnTabSelectedListener {

    long exitLasttime = 0;

    private BottomNavigationBar bottomNavigationBar;
    private FrameLayout frameLayout;
    private Fragment mFragment;

    public HomeFragment fragment_home;
    public CardSearchFragment fragment_search;
    public DeckManagerFragment fragment_deck_cards;
    public MycardFragment fragment_mycard;
    public SettingFragment fragment_settings;
    public MycardChatFragment fragment_mycard_chatting_room;
    private Bundle mBundle;


    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            savedInstanceState.putParcelable("android:support:fragments", null);
        }
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        setExitAnimEnable(false);
        mBundle = new Bundle();
        //
        initQbSdk();
        //
        checkNotch();
        //showNewbieGuide("homePage");
        initBottomNavigationBar();
        onNewIntent(getIntent());
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        //activity被回收后直接清除所有Bundle
        outState.clear();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        int mFlag = intent.getIntExtra("flag", 0);
        if (mFlag == 4) { //判断获取到的flag值
            switchSettingFragment();
        } else if (mFlag == 3) {
            switchFragment(fragment_mycard, 3, false);
        } else if (intent.hasExtra(Intent.EXTRA_TEXT)) {
            String strDeck = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (!strDeck.isEmpty()) {
                mBundle.putString("setDeck", strDeck);
                fragment_deck_cards.setArguments(mBundle);
            }
            switchFragment(fragment_deck_cards, 2, true);
        } else if (mFlag == 1) {
            switchFragment(fragment_search, 1, false);
        }
    }

    private void initBottomNavigationBar() {
        frameLayout = (FrameLayout) findViewById(R.id.fragment_content);
        // 获取页面上的底部导航栏控件
        bottomNavigationBar = (BottomNavigationBar) findViewById(R.id.bottom_navigation_bar);
        bottomNavigationBar
                .addItem(new BottomNavigationItem(R.drawable.home, R.string.mc_home))
                .addItem(new BottomNavigationItem(R.drawable.searcher, R.string.search))
                .addItem(new BottomNavigationItem(R.drawable.deck, R.string.deck_manager))
                .addItem(new BottomNavigationItem(R.drawable.mycard, R.string.mycard))
                .addItem(new BottomNavigationItem(R.drawable.my, R.string.personal))
                .setActiveColor(R.color.holo_blue_bright)
                .setBarBackgroundColor(R.color.transparent)
                .setMode(BottomNavigationBar.MODE_FIXED)
                .setFirstSelectedPosition(0)
                .initialise();//所有的设置需在调用该方法前完成
        bottomNavigationBar.setTabSelectedListener(this);
        fragment_home = new HomeFragment();
        fragment_search = new CardSearchFragment();
        fragment_deck_cards = new DeckManagerFragment();
        fragment_mycard = new MycardFragment();
        fragment_settings = new SettingFragment();

        fragment_mycard_chatting_room = new MycardChatFragment();

        mFragment = fragment_home;
        getSupportFragmentManager().beginTransaction().add(R.id.fragment_content, fragment_home).commit();
        getSupportActionBar().hide();
    }

    @Override
    public void onTabSelected(int position) {
        switch (position) {
            case 0:
                switchFragment(fragment_home, position, false);
                break;
            case 1:
                switchFragment(fragment_search, position, false);
                break;
            case 2:
                switchFragment(fragment_deck_cards, position, false);
                break;
            case 3:
                switchFragment(fragment_mycard, position, false);
                break;
            case 4:
                switchSettingFragment();
                break;
        }
    }

    public void switchSettingFragment() {
        bottomNavigationBar.setFirstSelectedPosition(4).initialise();
        getSupportFragmentManager().beginTransaction().hide(mFragment).commit();
        if (fragment_settings.isAdded()) {
            if (fragment_settings.isHidden()) {
                getFragmentManager().beginTransaction().show(fragment_settings).commit();
            }
        } else {
            getFragmentManager().beginTransaction().add(R.id.fragment_content, fragment_settings).commit();
        }

    }

    public void switchFragment(Fragment fragment, int page, boolean replace) {
        if (fragment_settings.isVisible())
            getFragmentManager().beginTransaction().hide(fragment_settings).commit();
        //用于intent到指定fragment时底部图标也跟着设置为选中状态
        bottomNavigationBar.setFirstSelectedPosition(page).initialise();
        if (mFragment.isHidden())
            getSupportFragmentManager().beginTransaction().show(mFragment).commit();
        //判断当前显示的Fragment是不是切换的Fragment
        if (mFragment != fragment) {
            //判断切换的Fragment是否已经添加过
            if (!fragment.isAdded()) {
                //如果没有，则先把当前的Fragment隐藏，把切换的Fragment添加上
                getSupportFragmentManager().beginTransaction().hide(mFragment)
                        .add(R.id.fragment_content, fragment).commit();
            } else {
                //如果已经添加过，则先把当前的Fragment隐藏，把切换的Fragment显示出来
                if (replace) {
                    //需要重新加载onCreateView需要detach再attach，而不是replace
                    getSupportFragmentManager().beginTransaction().hide(mFragment).detach(fragment).attach(fragment)
                            .show(fragment)//重启该fragment后需要重新show
                            .commit();
                } else {
                    getSupportFragmentManager().beginTransaction().hide(mFragment).show(fragment).commit();
                }
            }
            mFragment = fragment;
        } else {
            if (replace) {
                //需要重新加载onCreateView需要detach再attach，而不是replace
                getSupportFragmentManager().beginTransaction().hide(mFragment).detach(fragment).attach(fragment)
                        .show(fragment)//重启该fragment后需要重新show
                        .commit();
            }
        }
    }

    private void initQbSdk() {
        QbSdk.PreInitCallback cb = new QbSdk.PreInitCallback() {
            @Override
            public void onViewInitFinished(boolean arg0) {
                //x5內核初始化完成的回调，为true表示x5内核加载成功，否则表示x5内核加载失败，会自动切换到系统内核。
                if (arg0) {
                    //Toast.makeText(getActivity(), "加载X5内核成功", Toast.LENGTH_SHORT).show();
                } else {
                    //Toast.makeText(getActivity(), "加载系统内核成功", Toast.LENGTH_SHORT).show();
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
    }


    @Override
    protected void onResume() {
        super.onResume();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }

    @Override
    protected void onStart() {
        super.onStart();

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
        if (fragment_mycard.isVisible() && fragment_mycard.onBackPressed())
            return;
        if (fragment_search.isVisible() && fragment_search.onBackPressed())
            return;
        if (fragment_deck_cards.isVisible() && fragment_deck_cards.onBackPressed())
            return;

        if (System.currentTimeMillis() - exitLasttime <= 3000) {
            super.onBackPressed();
        } else {
            exitLasttime = System.currentTimeMillis();
            if (fragment_home.isVisible() || fragment_settings.isVisible())
                Toast.makeText(getContext(), R.string.back_tip, Toast.LENGTH_SHORT).show();
        }
    }

    protected abstract void checkResourceDownload(ResCheckTask.ResCheckListener listener);

    protected abstract void openGame();

}
