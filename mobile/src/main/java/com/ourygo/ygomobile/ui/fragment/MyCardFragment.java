package com.ourygo.ygomobile.ui.fragment;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentManager;
import androidx.viewpager.widget.ViewPager;

import com.feihua.dialogutils.util.DialogUtils;
import com.ourygo.ygomobile.adapter.FmPagerAdapter;
import com.ourygo.ygomobile.base.listener.BaseMcFragment;
import com.ourygo.ygomobile.base.listener.OnMcMatchListener;
import com.ourygo.ygomobile.base.listener.OnMcUserListener;
import com.ourygo.ygomobile.bean.FragmentData;
import com.ourygo.ygomobile.bean.McDuelInfo;
import com.ourygo.ygomobile.bean.YGOServer;
import com.ourygo.ygomobile.ui.activity.WatchDuelActivity;
import com.ourygo.ygomobile.util.HandlerUtil;
import com.ourygo.ygomobile.util.McUserManagement;
import com.ourygo.ygomobile.util.MyCardUtil;
import com.ourygo.ygomobile.util.OYUtil;
import com.ourygo.ygomobile.util.StatUtil;
import com.ourygo.ygomobile.util.YGOUtil;
import com.ourygo.ygomobile.view.OYTabLayout;

import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.base.BaseFragemnt;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.mycard.base.OnJoinChatListener;
import cn.garymb.ygomobile.ui.mycard.bean.McUser;
import cn.garymb.ygomobile.ui.mycard.mcchat.ChatListener;
import cn.garymb.ygomobile.ui.mycard.mcchat.ChatMessage;
import cn.garymb.ygomobile.ui.mycard.mcchat.SplashActivity;
import cn.garymb.ygomobile.ui.mycard.mcchat.management.ServiceManagement;

/**
 * Create By feihua  On 2021/10/19
 */
public class MyCardFragment extends BaseFragemnt implements BaseMcFragment, OnMcUserListener, View.OnClickListener, OnJoinChatListener, ChatListener {

    private static final int REQUEST_MATCH_ATHLETIC = 10;
    private static final int REQUEST_MATCH_ENTERTAIN = 11;

    private static final int QUERY_DUEL_INFO_OK = 0;
    private static final int QUERY_DUEL_INFO_EXCEPTION = 1;
    private static final int MC_MATCH_ATHLETIC_OK = 2;
    private static final int MC_MATCH_ATHLETIC_EXCEPTION = 3;
    private static final int MC_MATCH_ENTERTAIN_OK = 4;
    private static final int MC_MATCH_ENTERTAIN_EXCEPTION = 5;
    private static final String ARG_MC_DUEL_INFO = "mcDuelInfo";
    private static final String ARG_MATCH = "matchRecord";
    private static final String ARG_FUN = "funRecord";

    private OYTabLayout tl_tab;
    private ViewPager vp_pager;
    private MatchRecordFragment matchRecordFragment;
    private FunRecordFragment funRecordFragment;
    private McLayoutFragment mcLayoutFragment;
    private ProgressBar pb_loading, pb_chat_loading;
    private ImageView iv_refresh;
    private RelativeLayout rl_chat;
    private TextView tv_message, tv_match_title;
    private LinearLayout ll_visit_duel, ll_athletic, ll_entertain;
    private ServiceManagement serviceManagement;
    private ChatMessage currentMessage;
    private DialogUtils du;
    private McDuelInfo currentMcDuelInfo;
    private Bundle currentBundle;
    Handler handler = new Handler() {

        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case QUERY_DUEL_INFO_OK:
                    currentMcDuelInfo = (McDuelInfo) msg.obj;
                    Log.e("MycardFragment", "当前情况" + (currentBundle != null));
                    matchRecordFragment.onBaseDuelInfo(currentMcDuelInfo, null);
                    funRecordFragment.onBaseDuelInfo(currentMcDuelInfo, null);
                    tv_match_title.setText("竞技匹配（D.P：" + currentMcDuelInfo.getDp() + ")");
                    pb_loading.setVisibility(View.GONE);
                    iv_refresh.setVisibility(View.VISIBLE);
                    break;
                case QUERY_DUEL_INFO_EXCEPTION:
                    matchRecordFragment.onBaseDuelInfo(null, msg.obj.toString());
                    funRecordFragment.onBaseDuelInfo(null, msg.obj.toString());
                    pb_loading.setVisibility(View.GONE);
                    iv_refresh.setVisibility(View.VISIBLE);
                    OYUtil.snackExceptionToast(getActivity(), pb_loading, "战绩加载失败", msg.obj.toString());
                    break;
                case MC_MATCH_ATHLETIC_OK:
                    du.dis();
                    YGOServer ygoServer = (YGOServer) msg.obj;
                    if (ygoServer == null) {
                        OYUtil.snackShow(ll_athletic, "未匹配到对手");
                        break;
                    }
                    YGOUtil.joinGame(getActivity(), ygoServer, ygoServer.getPassword(), REQUEST_MATCH_ATHLETIC);
                    break;
                case MC_MATCH_ATHLETIC_EXCEPTION:
                    du.dis();
                    OYUtil.snackExceptionToast(getActivity(), ll_entertain, "匹配失败", msg.obj.toString());
                    break;
                case MC_MATCH_ENTERTAIN_OK:
                    du.dis();
                    YGOServer ygoServer1 = (YGOServer) msg.obj;
                    if (ygoServer1 == null) {
                        OYUtil.snackShow(ll_athletic, "未匹配到对手");
                        break;
                    }
                    YGOUtil.joinGame(getActivity(), ygoServer1, ygoServer1.getPassword(), REQUEST_MATCH_ENTERTAIN);
                    break;
                case MC_MATCH_ENTERTAIN_EXCEPTION:
                    du.dis();
                    OYUtil.snackExceptionToast(getActivity(), ll_entertain, "匹配失败", msg.obj.toString());
                    break;
            }
        }
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        View view;
        if (isHorizontal)
            view = inflater.inflate(R.layout.mycard_horizontal_fragment, container, false);
        else
            view = inflater.inflate(R.layout.mycard_fragment, container, false);
        this.currentBundle = savedInstanceState;
        initView(view);
        return view;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        FragmentManager fragmentManager = getChildFragmentManager();
        fragmentManager.putFragment(outState, ARG_MATCH, matchRecordFragment);
        fragmentManager.putFragment(outState, ARG_FUN, funRecordFragment);

        super.onSaveInstanceState(outState);
        outState.putSerializable(ARG_MC_DUEL_INFO, currentMcDuelInfo);

    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.rl_chat:
                startActivity(new Intent(getActivity(), SplashActivity.class));
                break;
            case R.id.ll_visit_duel:
                watchDuel();
                break;
            case R.id.ll_athletic:
                matchAthletic();
                break;
            case R.id.ll_entertain:
                matchEnterTain();

                break;
        }
    }

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

    private void matchEnterTain() {
        Button button = du.dialogj(null, "娱乐匹配中，请稍等");
        button.setText("取消匹配");
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MyCardUtil.cancelMatch();
                du.dis();
            }
        });
        MyCardUtil.startMatch(McUserManagement.getInstance().getUser(), MyCardUtil.MATCH_TYPE_ENTERTAIN, new OnMcMatchListener() {
            @Override
            public void onMcMatch(YGOServer ygoServer, String password, String exception) {
                HandlerUtil.sendMessage(handler, exception, MC_MATCH_ATHLETIC_OK, ygoServer, MC_MATCH_ATHLETIC_EXCEPTION);
            }
        });
    }

    private void matchAthletic() {
        Button button = du.dialogj(null, "竞技匹配中，请稍等");
        button.setText("取消匹配");
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                MyCardUtil.cancelMatch();
                du.dis();
            }
        });
        MyCardUtil.startMatch(McUserManagement.getInstance().getUser(), MyCardUtil.MATCH_TYPE_ATHLETIC, new OnMcMatchListener() {
            @Override
            public void onMcMatch(YGOServer ygoServer, String passsword, String exception) {
                HandlerUtil.sendMessage(handler, exception, MC_MATCH_ATHLETIC_OK, ygoServer, MC_MATCH_ATHLETIC_EXCEPTION);
            }
        });
    }

    private void watchDuel() {
        startActivity(new Intent(getActivity(), WatchDuelActivity.class));
    }

    private void initView(View view) {
        tl_tab = view.findViewById(R.id.tl_tab);
        vp_pager = view.findViewById(R.id.vp_pager);
        pb_loading = view.findViewById(R.id.pb_loading);
        iv_refresh = view.findViewById(R.id.iv_refresh);
        rl_chat = view.findViewById(R.id.rl_chat);
        tv_message = view.findViewById(R.id.tv_message);
        ll_visit_duel = view.findViewById(R.id.ll_visit_duel);
        pb_chat_loading = view.findViewById(R.id.pb_chat_loading);
        ll_athletic = view.findViewById(R.id.ll_athletic);
        ll_entertain = view.findViewById(R.id.ll_entertain);
        tv_match_title = view.findViewById(R.id.tv_match_title);

        serviceManagement = ServiceManagement.getDx();
        du = DialogUtils.getInstance(getActivity());

        tl_tab.setShowMode(OYTabLayout.MODE_BACKGROUND);
        tl_tab.setTextSizeM();

        rl_chat.setOnClickListener(this);
        ll_visit_duel.setOnClickListener(this);
        ll_athletic.setOnClickListener(this);
        ll_entertain.setOnClickListener(this);

        serviceManagement.addJoinRoomListener(this);
        serviceManagement.addListener(this);

        List<FragmentData> fragmentList = new ArrayList<>();

        if (currentBundle != null) {
            FragmentManager fragmentManager = getChildFragmentManager();
            matchRecordFragment = (MatchRecordFragment) fragmentManager.getFragment(currentBundle, ARG_MATCH);
            funRecordFragment = (FunRecordFragment) fragmentManager.getFragment(currentBundle, ARG_FUN);
        }

        if (matchRecordFragment == null)
            matchRecordFragment = new MatchRecordFragment();
        if (funRecordFragment == null)
            funRecordFragment = new FunRecordFragment();

        fragmentList.add(FragmentData.toFragmentData(OYUtil.s(R.string.match_record), matchRecordFragment));
        fragmentList.add(FragmentData.toFragmentData(OYUtil.s(R.string.fun_record), funRecordFragment));

        vp_pager.setAdapter(new FmPagerAdapter(getChildFragmentManager(), fragmentList));
//        tl_tab.setTabMode(TabLayout.MODE_FIXED);
        //缓存两个页面
        vp_pager.setOffscreenPageLimit(2);
        //TabLayout加载viewpager
        tl_tab.setViewPager(vp_pager);
        tl_tab.setCurrentTab(0);

        McUserManagement.getInstance().addListener(this);
        iv_refresh.setOnClickListener(v -> {
            initData(null, 0);
        });
    }

    private void initData(Bundle saveBundle, int position) {
        Log.e("MycardFragment", position + "情况" + (saveBundle != null) + "  " + (currentBundle != null));
        if (McUserManagement.getInstance().isLogin()) {
            pb_loading.setVisibility(View.VISIBLE);
            iv_refresh.setVisibility(View.GONE);

            if (saveBundle == null) {
                MyCardUtil.findUserDuelInfo(McUserManagement.getInstance().getUser().getUsername(), (mcDuelInfo, exception) -> {
                    HandlerUtil.sendMessage(handler, exception, QUERY_DUEL_INFO_OK, mcDuelInfo, QUERY_DUEL_INFO_EXCEPTION);
                });
                serviceManagement.start();
            } else {
                currentMcDuelInfo = (McDuelInfo) saveBundle.getSerializable(ARG_MC_DUEL_INFO);
                Log.e("MycardFragment", "决斗信息" + (currentMcDuelInfo != null));
                if (currentMcDuelInfo != null) {
                    HandlerUtil.sendMessage(handler, QUERY_DUEL_INFO_OK, currentMcDuelInfo);
                } else {
                    MyCardUtil.findUserDuelInfo(McUserManagement.getInstance().getUser().getUsername(), (mcDuelInfo, exception) -> {
                        HandlerUtil.sendMessage(handler, exception, QUERY_DUEL_INFO_OK, mcDuelInfo, QUERY_DUEL_INFO_EXCEPTION);
                    });
                }
                serviceManagement.start();
            }
        } else {
            if (mcLayoutFragment != null)
                mcLayoutFragment.setCurrentFragment(0);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK) {
            switch (requestCode) {
                case REQUEST_MATCH_ATHLETIC:
                case REQUEST_MATCH_ENTERTAIN:
                    initData(null, 1);
            }
        }
    }

    @Override
    public void onFirstUserVisible() {

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
    public void onMcLayout(McLayoutFragment mcLayoutFragment) {
        this.mcLayoutFragment = mcLayoutFragment;
    }

    @Override
    public void onChatLogin(String exception) {
        Log.e("MyCardFragment", "登录情况" + exception);
        pb_chat_loading.setVisibility(View.GONE);
        if (TextUtils.isEmpty(exception)) {
            if (currentMessage==null){
                List<ChatMessage> data=serviceManagement.getData();
                if (data!=null&&data.size()>0)
                    currentMessage=data.get(data.size()-1);
            }
            if (currentMessage == null)
                tv_message.setText("聊天信息加载中");
            else
                tv_message.setText(currentMessage.getName() + "：" + currentMessage.getMessage());
        } else {
            tv_message.setText(OYUtil.s(R.string.logining_failed));
        }

    }

    @Override
    public void onChatLoginLoading() {
        Log.e("MyCardFragment", "加载中");
        pb_chat_loading.setVisibility(View.VISIBLE);
        tv_message.setText(OYUtil.s(R.string.logining_in));
    }

    @Override
    public void onJoinRoomLoading() {
        Log.e("MyCardFragment", "加入房间中");
        pb_chat_loading.setVisibility(View.VISIBLE);
        tv_message.setText(OYUtil.s(R.string.logining_in));
    }

    @Override
    public void onChatUserNull() {
        Log.e("MyCardFragment", "为空");
        pb_chat_loading.setVisibility(View.GONE);
        tv_message.setText("登录失败，请退出登录后重新登录");
    }

    @Override
    public void addChatMessage(ChatMessage message) {
        currentMessage = message;
        if (message != null)
            tv_message.setText(message.getName() + "：" + message.getMessage());
    }

    @Override
    public void removeChatMessage(ChatMessage chatMessage) {

    }

    @Override
    public void reChatLogin(boolean state) {
        pb_chat_loading.setVisibility(View.VISIBLE);
        if (state) {
            tv_message.setText("登录成功");
        } else {
            tv_message.setText("连接断开,重新登录中……");
        }
    }

    @Override
    public void reChatJoin(boolean state) {
        pb_chat_loading.setVisibility(View.VISIBLE);
        if (state) {
            onChatLogin(null);
        } else {
            tv_message.setText("重新加入聊天室中……");
        }
    }

    @Override
    public void onLogin(McUser user, String exception) {
        if (TextUtils.isEmpty(exception))
            initData(currentBundle, 2);
    }

    @Override
    public void onLogout() {
//        HandlerUtil.sendMessage(handler, QUERY_DUEL_INFO_OK, null);
    }

    @Override
    public boolean isListenerEffective() {
        return OYUtil.isContextExisted(getActivity());
    }
}
