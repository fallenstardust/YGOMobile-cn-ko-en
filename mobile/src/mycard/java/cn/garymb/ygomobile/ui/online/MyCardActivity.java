package cn.garymb.ygomobile.ui.online;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.NavigationView;
import android.support.v4.widget.DrawerLayout;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

import com.bumptech.glide.Glide;

import org.xwalk.core.XWalkSettings;
import org.xwalk.core.XWalkUIClient;
import org.xwalk.core.XWalkView;

import java.text.MessageFormat;

import cn.garymb.ygomobile.YGOStarter;
import cn.garymb.ygomobile.lite.BuildConfig;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.activities.BaseActivity;
import cn.garymb.ygomobile.ui.cards.DeckManagerActivity;

public class MyCardActivity extends BaseActivity implements MyCard.MyCardListener, NavigationView.OnNavigationItemSelectedListener {

    private MyCardWebView mWebViewPlus;
    private MyCard mMyCard;
    protected DrawerLayout mDrawerlayout;
    private ImageView mHeadView;
    private TextView mNameView, mStatusView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_online_mycard);
        enableBackHome();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            Window window = getWindow();
            window.addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            window.setStatusBarColor(getResources().getColor(R.color.mediumPurple));
        }

        YGOStarter.onCreated(this);
        mMyCard = new MyCard(this);
        mWebViewPlus = $(R.id.webbrowser);
        mDrawerlayout = $(R.id.drawer_layout);

        NavigationView navigationView = $(R.id.nav_main);
        navigationView.setNavigationItemSelectedListener(this);
        View navHead = navigationView.getHeaderView(0);
        mHeadView = (ImageView) navHead.findViewById(R.id.img_head);
        mNameView = (TextView) navHead.findViewById(R.id.tv_name);
        mStatusView = (TextView) navHead.findViewById(R.id.tv_dp);
        mWebViewPlus.enableHtml5();

        XWalkSettings settings = mWebViewPlus.getSettings();
        settings.setUserAgentString(settings.getUserAgentString() + MessageFormat.format(
                " YGOMobile/{0} ({1} {2,number,#})",
                BuildConfig.VERSION_NAME,
                BuildConfig.APPLICATION_ID,
                BuildConfig.VERSION_CODE
        ));

        mWebViewPlus.setUIClient(new XWalkUIClient(mWebViewPlus) {
            @Override
            public void onReceivedTitle(XWalkView view, String title) {
                super.onReceivedTitle(view, title);
                setTitle(title);
            }

            @Override
            public boolean onConsoleMessage(XWalkView view, String message, int lineNumber, String sourceId, ConsoleMessageType messageType) {
                if (BuildConfig.DEBUG)
                    Log.i("webview", sourceId + ":" + lineNumber + "\n" + message);
                return super.onConsoleMessage(view, message, lineNumber, sourceId, messageType);
            }
        });
        mMyCard.attachWeb(mWebViewPlus, this);
        mWebViewPlus.loadUrl(mMyCard.getHomeUrl());
    }

    @Override
    protected void onBackHome() {
        if (mDrawerlayout.isDrawerOpen(Gravity.LEFT)) {
            closeDrawer();
            return;
        }
        if (mWebViewPlus.canGoBack()) {
            mWebViewPlus.goBack();
        } else {
            finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (mWebViewPlus != null) {
            mWebViewPlus.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (mWebViewPlus != null) {
            mWebViewPlus.onNewIntent(intent);
        }
    }

    @Override
    public void onBackPressed() {
        onBackHome();
    }

    @Override
    protected void onResume() {
        YGOStarter.onResumed(this);
        super.onResume();
    }

    @Override
    protected void onDestroy() {
        mWebViewPlus.stopLoading();
        mWebViewPlus.onDestroy();
        YGOStarter.onDestroy(this);
        super.onDestroy();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (doMenu(item.getItemId())) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private boolean doMenu(int id) {
        closeDrawer();
        switch (id) {
            case android.R.id.home:
                mWebViewPlus.loadUrl(mMyCard.getHomeUrl());
                break;
            case R.id.action_deck_manager:
                startActivity(new Intent(this, DeckManagerActivity.class));
                closeDrawer();
                break;
            case R.id.action_arena:
                mWebViewPlus.loadUrl(mMyCard.getArenaUrl());
                break;
            case R.id.action_quit:
                finish();
                break;
            case R.id.action_home:
                onHome();
                break;
            case R.id.action_bbs:
                mWebViewPlus.loadUrl(mMyCard.getBBSUrl());
                break;
            default:
                return false;
        }
        return true;
    }

    @Override
    public void onLogin(String name, String icon, String statu) {

        if(!TextUtils.isEmpty(icon)){
            Glide.with(this).load(Uri.parse(icon)).into(mHeadView);
        }
        mNameView.setText(name);
        mStatusView.setText(statu);
    }

    @Override
    public void onHome() {
        mWebViewPlus.loadUrl(mMyCard.getHomeUrl());
    }

    @Override
    public void watchReplay() {

    }

    @Override
    public void puzzleMode() {

    }

    @Override
    public void openDrawer() {
        if (!mDrawerlayout.isDrawerOpen(Gravity.LEFT)) {
            mDrawerlayout.openDrawer(Gravity.LEFT);
        }
    }

    @Override
    public void backHome() {
        finish();
    }

    @Override
    public void closeDrawer() {
        if (mDrawerlayout.isDrawerOpen(Gravity.LEFT)) {
            mDrawerlayout.closeDrawer(Gravity.LEFT);
        }
    }

    @Override
    public void share(String text) {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, "分享");
        intent.putExtra(Intent.EXTRA_TEXT, text);
        intent.putExtra(Intent.EXTRA_TITLE, getString(R.string.app_name));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(Intent.createChooser(intent, "请选择"));
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        if (doMenu(item.getItemId())) {
            return true;
        }
        return false;
    }
}
