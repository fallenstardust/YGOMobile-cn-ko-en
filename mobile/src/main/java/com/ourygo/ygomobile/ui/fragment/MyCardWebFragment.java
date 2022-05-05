package com.ourygo.ygomobile.ui.fragment;


import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.ourygo.ygomobile.base.listener.BaseMcFragment;
import com.ourygo.ygomobile.base.listener.OnMcUserListener;
import com.ourygo.ygomobile.util.IntentUtil;
import com.ourygo.ygomobile.util.McUserManagement;
import com.ourygo.ygomobile.util.OYUtil;
import cn.garymb.ygomobile.utils.StatUtil;
import com.tencent.smtt.sdk.QbSdk;
import com.tencent.smtt.sdk.ValueCallback;
import com.tencent.smtt.sdk.WebChromeClient;
import com.tencent.smtt.sdk.WebSettings;
import com.tencent.smtt.sdk.WebView;

import java.text.MessageFormat;

import cn.garymb.ygomobile.base.BaseFragemnt;
import cn.garymb.ygomobile.lite.BuildConfig;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.mycard.MyCard;
import cn.garymb.ygomobile.ui.mycard.MyCardWebView;
import cn.garymb.ygomobile.ui.mycard.bean.McUser;
import cn.garymb.ygomobile.ui.mycard.mcchat.SplashActivity;

public class MyCardWebFragment extends BaseFragemnt implements MyCard.MyCardListener, BaseMcFragment, OnMcUserListener {

    private static final int TYPE_LOGIN = 0;
    private static final int FILECHOOSER_RESULTCODE = 10;
    private MyCardWebView mWebViewPlus;
    private MyCard mMyCard;

    private ProgressBar mProgressBar;
    private ValueCallback<Uri> uploadMessage;
    private ValueCallback<Uri[]> mUploadCallbackAboveL;
    private McLayoutFragment mcLayoutFragment;
    @SuppressLint("HandlerLeak")
    Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case TYPE_LOGIN:
                    if (mcLayoutFragment != null)
                        mcLayoutFragment.setCurrentFragment(1);
//                    ((OYMainActivity)getActivity()).refreshMyCardUser(((MyCard.User) msg.obj).getUsername());
                    Object[] objects= (Object[]) msg.obj;
                    McUserManagement.getInstance().login((McUser) objects[0],(boolean)objects[1]);
                    break;
            }
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.mycard_web_fragment, null);
        Log.e("MyCardWeb","Web加载");
        initView(v);
        initData();
        return v;
    }

    private void initView(View v) {
        mWebViewPlus = v.findViewById(R.id.wv_web);
        mProgressBar = v.findViewById(R.id.progressBar);
    }




//    @Override
//    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
//        super.onActivityResult(requestCode, resultCode, data);
//        if (requestCode == FILECHOOSER_RESULTCODE) {
//            if (null == uploadMessage && null == mUploadCallbackAboveL) return;
//            Uri result = data == null || resultCode != RESULT_OK ? null : data.getData();
//            if (mUploadCallbackAboveL != null) {
//                onActivityResultAboveL(requestCode, resultCode, data);
//            } else if (uploadMessage != null) {
//                uploadMessage.onReceiveValue(result);
//                uploadMessage = null;
//            }
//        }
//    }

//    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
//    private void onActivityResultAboveL(int requestCode, int resultCode, Intent data) {
//        if (requestCode != FILECHOOSER_RESULTCODE
//                || mUploadCallbackAboveL == null) {
//            return;
//        }
//        Uri[] results = null;
//        if (resultCode == Activity.RESULT_OK) {
//            if (data == null) {
//            } else {
//                String dataString = data.getDataString();
//                ClipData clipData = data.getClipData();
//                if (clipData != null) {
//                    results = new Uri[clipData.getItemCount()];
//                    for (int i = 0; i < clipData.getItemCount(); i++) {
//                        ClipData.Item item = clipData.getItemAt(i);
//                        results[i] = item.getUri();
//                    }
//                }
//                if (dataString != null)
//                    results = new Uri[]{Uri.parse(dataString)};
//            }
//        }
//        mUploadCallbackAboveL.onReceiveValue(results);
//        mUploadCallbackAboveL = null;
//        return;
//    }


    /*@Override
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
    }*/

    private void initData() {

        Log.e("MyCardWeb", "准备登录");
        if (!McUserManagement.getInstance().isLogin())
            QbSdk.clearAllWebViewCache(getActivity(), true);

        mMyCard = new MyCard(getActivity());
        mProgressBar.setMax(100);

        WebSettings settings = mWebViewPlus.getSettings();
        settings.setUserAgentString(settings.getUserAgentString() + MessageFormat.format(
                " YGOMobile/{0} ({1} {2,number,#})",
                OYUtil.s(R.string.app_version_name),
                BuildConfig.APPLICATION_ID,
                BuildConfig.VERSION_CODE
        ));

        mWebViewPlus.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress == 100) {
                    mProgressBar.setVisibility(View.GONE);
                } else {
                    if (View.GONE == mProgressBar.getVisibility()) {
                        mProgressBar.setVisibility(View.VISIBLE);
                    }
                    mProgressBar.setProgress(newProgress);
                }
                super.onProgressChanged(view, newProgress);
            }

            @Override
            public void openFileChooser(ValueCallback<Uri> valueCallback, String acceptType, String capture) {
                uploadMessage = valueCallback;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                startActivityForResult(Intent.createChooser(i, "File Browser"), FILECHOOSER_RESULTCODE);

            }

            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> valueCallback, FileChooserParams fileChooserParams) {
                mUploadCallbackAboveL = valueCallback;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                startActivityForResult(
                        Intent.createChooser(i, "File Browser"),
                        FILECHOOSER_RESULTCODE);

                return true;
            }
        });
        mMyCard.attachWeb(mWebViewPlus, this);
        mWebViewPlus.loadUrl(mMyCard.getHomeUrl());
        McUserManagement.getInstance().addListener(this);
        Log.e("MyCardWeb", "登录完毕");
    }

//    @Override
//    public void onResume() {
////        YGOStarter.onResumed(getActivity());
//        super.onResume();
//    }

    @Override
    public void onResume() {
        super.onResume();
        StatUtil.onResume(getClass().getName());
    }

    @Override
    public void onPause() {
        super.onPause();
        StatUtil.onPause(getClass().getName());
    }

    @Override
    public void onFirstUserVisible() {
        initData();
    }

    @Override
    public void onUserVisible() {
    }

    @Override
    public void onFirstUserInvisible() {
    }

    @Override
    public void onUserInvisible() {
    }

    @Override
    public void onDestroy() {
        mWebViewPlus.stopLoading();
        //mWebViewPlus.onDestroy();
//        YGOStarter.onDestroy(getActivity());
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
//                startActivity(new Intent(getActivity(), DeckManagerActivity.class));
                startActivity(IntentUtil.getEZIntent(getActivity()));
//                closeDrawer();
                break;
            case R.id.action_arena:
                mWebViewPlus.loadUrl(mMyCard.getArenaUrl());
                break;
            case R.id.action_quit:
//                finish();
                break;
            case R.id.action_home:
                onHome();
                break;
            case R.id.action_bbs:
                mWebViewPlus.loadUrl(mMyCard.getBBSUrl());
                break;
            case R.id.action_chat:
                startActivity(new Intent(getActivity(), SplashActivity.class));
                break;
            default:
                return false;
        }
        return true;
    }

    @Override
    public void onLogin(McUser mcUser,boolean isUpdate, String statu) {
        Log.e("MyCardWeb", "登录回调"+true);

        Message message = new Message();
        message.what = TYPE_LOGIN;
        message.obj = new Object[]{mcUser,isUpdate};
        handler.sendMessage(message);
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
        startActivity(new Intent(getActivity(), SplashActivity.class));
    }

    @Override
    public void closeDrawer() {

    }

    @Override
    public void backHome() {

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
    public void onMcLayout(McLayoutFragment mcLayoutFragment) {
        this.mcLayoutFragment = mcLayoutFragment;
    }

    @Override
    public void onLogin(McUser user, String exception) {


    }

    @Override
    public void onLogout() {
        Log.e("CookUtil", "退出用户监听");
//        mWebViewPlus.removeAllCookie("mycard.moe");
//        mWebViewPlus.removeAllCookie("ygobbs.com");
//        CookieUtil.remove(false);
//        getActivity().deleteDatabase("webview_core_x5.db");
////        CookieUtil.remove("mycard.moe");
////        CookieUtil.remove("ygobbs.com");
//        mWebViewPlus.clearCache(true);
        //清除cookie
        QbSdk.clearAllWebViewCache(getActivity(), true);
        //清除cookie
//        CookieManager.getInstance().removeAllCookies(null);
//清除storage相关缓存
//        WebStorage.getInstance().deleteAllData();;
//清除用户密码信息
//        WebViewDatabase.getInstance(getActivity()).clearUsernamePassword();
//清除httpauth信息
//        WebViewDatabase.getInstance(getActivity()).clearHttpAuthUsernamePassword();
//清除表单数据
//        WebViewDatabase.getInstance(getActivity()).clearFormData();
        mWebViewPlus.loadUrl(MyCard.mHomeUrl);
    }

    @Override
    public boolean isListenerEffective() {
        return OYUtil.isContextExisted(getActivity());
    }
}
