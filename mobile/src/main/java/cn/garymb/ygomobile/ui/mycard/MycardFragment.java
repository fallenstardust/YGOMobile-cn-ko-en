package cn.garymb.ygomobile.ui.mycard;

import static android.app.Activity.RESULT_OK;
import static okhttp3.internal.Util.UTF_8;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.app.hubert.guide.util.LogUtil;
import com.ourygo.assistant.util.Util;
import com.tencent.smtt.sdk.ValueCallback;
import com.tencent.smtt.sdk.WebChromeClient;
import com.tencent.smtt.sdk.WebSettings;
import com.tencent.smtt.sdk.WebView;

import java.text.MessageFormat;
import java.util.List;

import cn.garymb.ygomobile.App;
import cn.garymb.ygomobile.YGOStarter;
import cn.garymb.ygomobile.base.BaseFragemnt;
import cn.garymb.ygomobile.lite.BuildConfig;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.home.HomeActivity;
import cn.garymb.ygomobile.ui.mycard.base.OnJoinChatListener;
import cn.garymb.ygomobile.ui.mycard.bean.McUser;
import cn.garymb.ygomobile.ui.mycard.mcchat.ChatListener;
import cn.garymb.ygomobile.ui.mycard.mcchat.ChatMessage;
import cn.garymb.ygomobile.ui.mycard.mcchat.management.ServiceManagement;
import cn.garymb.ygomobile.utils.HandlerUtil;
import cn.garymb.ygomobile.utils.YGOUtil;
import cn.garymb.ygomobile.utils.glide.GlideCompat;

public class MycardFragment extends BaseFragemnt implements View.OnClickListener, MyCard.MyCardListener, OnJoinChatListener, ChatListener {
    private static final int FILECHOOSER_RESULTCODE = 10;
    private static final int TYPE_MC_LOGIN = 0;
    private static final int TYPE_MC_LOGIN_FAILED = 1;
    private HomeActivity homeActivity;
    long exitLasttime = 0;
    //头像昵称账号
    private LinearLayout ll_head_login;
    private ImageView mHeadView;
    private TextView mNameView, mStatusView;
    private TextView tv_back_mc;
    //萌卡webview
    public MyCardWebView mWebViewPlus;
    private MyCard mMyCard;
    //聊天室
    public RelativeLayout rl_chat;
    private TextView tv_message;
    private ProgressBar pb_chat_loading;
    private ServiceManagement serviceManagement;
    private ChatMessage currentMessage;

    @SuppressLint("HandlerLeak")
    Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            if (msg.what == TYPE_MC_LOGIN) {
           McUser mcUser= (McUser) msg.obj;
                if (!TextUtils.isEmpty(mcUser.getAvatar_url())) {
                    GlideCompat.with(getActivity()).load(mcUser.getAvatar_url()).into(mHeadView);//刷新头像图片
                }
                mNameView.setText(mcUser.getUsername());//刷新用户名
                mStatusView.setText(mcUser.getEmail());//刷新账号信息
                serviceManagement.start();
            }
            if (msg.what == TYPE_MC_LOGIN_FAILED) {

            }
        }
    };
    private ProgressBar mProgressBar;
    private ValueCallback<Uri> uploadMessage;
    private ValueCallback<Uri[]> mUploadCallbackAboveL;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        homeActivity = (HomeActivity) getActivity();
        View view;
        view = inflater.inflate(R.layout.fragment_mycard, container, false);
        initView(view);
        return view;
    }

    public void initView(View view) {
        YGOStarter.onCreated(getActivity());
        mMyCard = new MyCard(getActivity());
        mWebViewPlus = view.findViewById(R.id.webbrowser);
        mProgressBar = view.findViewById(R.id.progressBar);
        mProgressBar.setMax(100);
        tv_back_mc = view.findViewById(R.id.tv_back_mc);
        tv_back_mc.setOnClickListener(this);

        ll_head_login = view.findViewById(R.id.ll_head_login);
        ll_head_login.setOnClickListener(this);
        mHeadView = view.findViewById(R.id.img_head);
        mNameView = view.findViewById(R.id.tv_name);
        mStatusView = view.findViewById(R.id.tv_dp);
        //mWebViewPlus.enableHtml5();

        rl_chat = view.findViewById(R.id.rl_chat);
        rl_chat.setOnClickListener(this);
        tv_message = view.findViewById(R.id.tv_message);
        pb_chat_loading = view.findViewById(R.id.pb_chat_loading);

        serviceManagement = ServiceManagement.getDx();
        serviceManagement.addJoinRoomListener(this);
        serviceManagement.addListener(this);

        WebSettings settings = mWebViewPlus.getSettings();
        settings.setUserAgentString(settings.getUserAgentString() + MessageFormat.format(
                " YGOMobile/{0} ({1} {2,number,#})",
                BuildConfig.VERSION_NAME,
                BuildConfig.APPLICATION_ID,
                BuildConfig.VERSION_CODE
        ));

        mWebViewPlus.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (newProgress == 100) {
                    mProgressBar.setVisibility(View.GONE);
                    if (view.getUrl().contains(mMyCard.getMcHost()))
                        tv_back_mc.setVisibility(View.GONE);
                    else
                        tv_back_mc.setVisibility(View.VISIBLE);
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
    }

    @Override
    public void onResume() {
        YGOStarter.onResumed(getActivity());
        super.onResume();
    }

    @Override
    public void onDestroy() {
        mWebViewPlus.stopLoading();
        //mWebViewPlus.onDestroy();
        YGOStarter.onDestroy(getActivity());
        super.onDestroy();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILECHOOSER_RESULTCODE) {
            if (null == uploadMessage && null == mUploadCallbackAboveL) return;
            Uri result = data == null || resultCode != RESULT_OK ? null : data.getData();
            if (mUploadCallbackAboveL != null) {
                onActivityResultAboveL(requestCode, resultCode, data);
            } else if (uploadMessage != null) {
                uploadMessage.onReceiveValue(result);
                uploadMessage = null;
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void onActivityResultAboveL(int requestCode, int resultCode, Intent data) {
        if (requestCode != FILECHOOSER_RESULTCODE
                || mUploadCallbackAboveL == null) {
            return;
        }
        Uri[] results = null;
        if (resultCode == RESULT_OK) {
            if (data != null) {
                String dataString = data.getDataString();
                ClipData clipData = data.getClipData();
                if (clipData != null) {
                    results = new Uri[clipData.getItemCount()];
                    for (int i = 0; i < clipData.getItemCount(); i++) {
                        ClipData.Item item = clipData.getItemAt(i);
                        results[i] = item.getUri();
                    }
                }
                if (dataString != null)
                    results = new Uri[]{Uri.parse(dataString)};
            }
        }
        mUploadCallbackAboveL.onReceiveValue(results);
        mUploadCallbackAboveL = null;
    }

    /**
     * 第一次fragment可见（进行初始化工作）
     */
    @Override
    public void onFirstUserVisible() {

    }

    /**
     * fragment可见（切换回来或者onResume）
     */
    @Override
    public void onUserVisible() {

    }

    /**
     * 第一次fragment不可见（不建议在此处理事件）
     */
    @Override
    public void onFirstUserInvisible() {

    }

    /**
     * fragment不可见（切换掉或者onPause）
     */
    @Override
    public void onUserInvisible() {

    }

    @Override
    public void onBackHome() {

    }

    @Override
    public boolean onBackPressed() {
        if (homeActivity.fragment_mycard_chatting_room.isVisible()) {
            getChildFragmentManager().beginTransaction().hide(homeActivity.fragment_mycard_chatting_room).commit();
            mWebViewPlus.setVisibility(View.VISIBLE);
            rl_chat.setVisibility(View.VISIBLE);
        } else if (mWebViewPlus.getUrl().equals(mMyCard.getMcMainUrl())) {
            //与home相同双击返回
            if (System.currentTimeMillis() - exitLasttime <= 3000) {
                return false;
            } else {
                exitLasttime = System.currentTimeMillis();
                Toast.makeText(getContext(), R.string.back_tip, Toast.LENGTH_SHORT).show();
            }
        }
        if (mWebViewPlus.canGoBack() && !homeActivity.fragment_mycard_chatting_room.isVisible()) {
            mWebViewPlus.goBack();
        }
        return true;
    }

    /**
     * Called when a view has been clicked.
     *
     * @param v The view that was clicked.
     */
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.ll_head_login:
                if (homeActivity.fragment_mycard_chatting_room.isVisible())
                    getChildFragmentManager().beginTransaction().hide(homeActivity.fragment_mycard_chatting_room).commit();
                mWebViewPlus.loadUrl(MyCard.getMCLogoutUrl());
                break;
            case R.id.tv_back_mc:
                onHome();
                break;
            case R.id.rl_chat:
                //这里显示聊天室fragment
                if (serviceManagement.isConnected()) {
                    if (!homeActivity.fragment_mycard_chatting_room.isAdded()) {
                        getChildFragmentManager().beginTransaction().add(R.id.fragment_content, homeActivity.fragment_mycard_chatting_room).commit();
                        mWebViewPlus.setVisibility(View.INVISIBLE);
                        rl_chat.setVisibility(View.INVISIBLE);
                    } else {
                        if (homeActivity.fragment_mycard_chatting_room.isHidden()) {
                            getChildFragmentManager().beginTransaction().show(homeActivity.fragment_mycard_chatting_room).commit();
                            mWebViewPlus.setVisibility(View.INVISIBLE);
                            rl_chat.setVisibility(View.INVISIBLE);
                        } else {
                            getChildFragmentManager().beginTransaction().hide(homeActivity.fragment_mycard_chatting_room).commit();
                            mWebViewPlus.setVisibility(View.VISIBLE);
                            rl_chat.setVisibility(View.VISIBLE);
                        }

                    }
                } else {
                    //点击重新登录
                    serviceManagement.start();
                }
                break;
        }
    }

    @Override
    public void onLogin(McUser mcUser, String exception) {
        if (!TextUtils.isEmpty(exception)){
            return;
        }
        serviceManagement.disSerVice();//先退出当前账号，待TYPE_MC_LOGIN处重新执行start（）

        //登录成功发送message
        Message message = new Message();
        message.obj = mcUser;
        message.what = TYPE_MC_LOGIN;
        handler.sendMessage(message);
    }

    @Override
    public void onUpdate(String name, String icon, String statu) {
        McUser mcUser=new McUser();
        mcUser.setUsername(name);
        mcUser.setAvatar_url(icon);
        mcUser.setEmail(statu);
        //登录成功发送message
        Message message = new Message();
        message.obj = mcUser;
        message.what = TYPE_MC_LOGIN;
        handler.sendMessage(message);
    }

    @Override
    public void onLogout(String message) {
        if (!TextUtils.isEmpty(message))
            YGOUtil.show(message);
        serviceManagement.disSerVice();
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
    public void onHome() {
        mWebViewPlus.loadUrl(mMyCard.getHomeUrl());
    }

    @Override
    public void onChatLogin(String exception) {
        pb_chat_loading.setVisibility(View.GONE);
        if (TextUtils.isEmpty(exception)) {
            if (currentMessage == null) {
                List<ChatMessage> data = serviceManagement.getData();
                if (data != null && data.size() > 0)
                    currentMessage = data.get(data.size() - 1);
            }
            if (currentMessage == null)
                tv_message.setText(R.string.loading);
            else
                tv_message.setText(currentMessage.getName() + "：" + currentMessage.getMessage());
        } else {
            Log.e("MyCardFragment","登录失败"+exception);
            tv_message.setText(R.string.logining_failed);
            HandlerUtil.sendMessage(handler, TYPE_MC_LOGIN_FAILED, exception);
            serviceManagement.setIsListener(false);
            YGOUtil.show(getString(R.string.failed_reason) + exception);
        }
    }

    @Override
    public void onChatLoginLoading() {
        pb_chat_loading.setVisibility(View.VISIBLE);
        tv_message.setText(R.string.logining_in);
    }

    @Override
    public void onJoinRoomLoading() {
        pb_chat_loading.setVisibility(View.VISIBLE);
        tv_message.setText(R.string.logining_in);
    }

    @Override
    public void onChatUserNull() {
        pb_chat_loading.setVisibility(View.GONE);
        HandlerUtil.sendMessage(handler, TYPE_MC_LOGIN_FAILED, "exception");
        tv_message.setText(R.string.logining_failed);
    }

    @Override
    public boolean isListenerEffective() {
        return Util.isContextExisted(getActivity());
    }

    @Override
    public void addChatMessage(ChatMessage message) {
        currentMessage = message;
        if (message != null)
            tv_message.setText(message.getName() + "：" + message.getMessage());
    }

    @Override
    public void removeChatMessage(ChatMessage message) {

    }

    @Override
    public void reChatLogin(boolean state) {
        pb_chat_loading.setVisibility(View.VISIBLE);
        if (state) {
            tv_message.setText(R.string.login_succeed);
        } else {
            tv_message.setText(R.string.reChatJoining);
        }
    }

    @Override
    public void reChatJoin(boolean state) {
        pb_chat_loading.setVisibility(View.VISIBLE);
        if (state) {
            onChatLogin(null);
        } else {
            tv_message.setText(R.string.reChatJoining);
        }
    }
}
