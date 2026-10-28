package cn.garymb.ygomobile.ui.mycard;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.listener.OnItemClickListener;
import com.google.android.material.tabs.TabLayout;
import com.king.view.circleprogressview.CircleProgressView;
import com.ourygo.lib.duelassistant.util.Util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.YGOStarter;
import cn.garymb.ygomobile.adapter.DuelRoomBQAdapter;
import cn.garymb.ygomobile.base.BaseFragemnt;
import cn.garymb.ygomobile.bean.ServerInfo;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.adapters.SimpleSpinnerAdapter;
import cn.garymb.ygomobile.ui.adapters.SimpleSpinnerItem;
import cn.garymb.ygomobile.ui.cards.deck_square.DeckSquareApiUtil;
import cn.garymb.ygomobile.ui.cards.deck_square.api_response.LoginResponse;
import cn.garymb.ygomobile.ui.home.HomeActivity;
import cn.garymb.ygomobile.ui.mycard.adapter.McNewsAdapter;
import cn.garymb.ygomobile.ui.mycard.adapter.UserDuelDetailAdapter;
import cn.garymb.ygomobile.ui.mycard.arena.MycardDuelArenaFragment;
import cn.garymb.ygomobile.ui.mycard.base.OnDuelRoomListener;
import cn.garymb.ygomobile.ui.mycard.base.OnJoinChatListener;
import cn.garymb.ygomobile.ui.mycard.base.OnMcMatchListener;
import cn.garymb.ygomobile.ui.mycard.bean.DuelRoom;
import cn.garymb.ygomobile.ui.mycard.bean.McDuelInfo;
import cn.garymb.ygomobile.ui.mycard.bean.McNews;
import cn.garymb.ygomobile.ui.mycard.bean.McUser;
import cn.garymb.ygomobile.ui.mycard.bean.MyCardPieChart;
import cn.garymb.ygomobile.ui.mycard.bean.YGOServer;
import cn.garymb.ygomobile.ui.mycard.mcchat.ChatListener;
import cn.garymb.ygomobile.ui.mycard.mcchat.ChatMessage;
import cn.garymb.ygomobile.ui.mycard.mcchat.management.ServiceManagement;
import cn.garymb.ygomobile.ui.mycard.mcchat.management.UserManagement;
import cn.garymb.ygomobile.ui.mycard.watchDuel.WaitingDuelManagement;
import cn.garymb.ygomobile.ui.mycard.watchDuel.WatchDuelManagement;
import cn.garymb.ygomobile.ui.plus.DialogPlus;
import cn.garymb.ygomobile.ui.plus.VUiKit;
import cn.garymb.ygomobile.ui.widget.DeckPieChartView;
import cn.garymb.ygomobile.utils.DownloadUtil;
import cn.garymb.ygomobile.utils.HandlerUtil;
import cn.garymb.ygomobile.utils.SharedPreferenceUtil;
import cn.garymb.ygomobile.utils.YGOUtil;
import cn.garymb.ygomobile.utils.glide.GlideCompat;
import ocgcore.DataManager;
import ocgcore.StringManager;

/**
 * MycardFragment 拆分后的抽象基类（继承链顶端）。
 *
 * <p>由原 2298 行的 {@link MycardFragment} 拆分而来，功能与实现细节完全保持不变。
 * 继承链：MycardFragmentBase ← {@link MycardFragmentAccount} ← {@link MycardFragmentNavigation} ← {@link MycardFragment}。
 * 本类持有全部共享字段与常量、消息处理器、onCreateView/initView、登录状态检查、
 * 决斗信息查询及生命周期回调；文末抽象钩子由链下游子类实现，保证原有调用方向不变。</p>
 */
public abstract class MycardFragmentBase extends BaseFragemnt implements View.OnClickListener, MyCard.MyCardListener, OnJoinChatListener, ChatListener, OnDuelRoomListener {
    protected static final int FILECHOOSER_RESULTCODE = 10;
    protected static final int TYPE_MC_LOGIN = 0;
    protected static final int TYPE_MC_LOGIN_FAILED = -1;
    protected static final int QUERY_DUEL_INFO_OK = 8;
    protected static final int QUERY_DUEL_INFO_EXCEPTION = 9;
    protected static final int MC_MATCH_ATHLETIC_OK = 10;
    protected static final int MC_MATCH_ATHLETIC_EXCEPTION = 11;
    protected static final int MC_MATCH_ENTERTAIN_OK = 12;
    protected static final int MC_MATCH_ENTERTAIN_EXCEPTION = 13;
    protected static final int REQUEST_MATCH_ATHLETIC = 14;
    protected static final int REQUEST_MATCH_ENTERTAIN = 15;
    protected static final int TYPE_MC_NEWS_QUERY_OK = 16;
    protected static final int TYPE_MC_NEWS_QUERY_EXCEPTION = 17;

    protected HomeActivity homeActivity;
    protected StringManager mStringManager;
    protected LinearLayout ll_athletic, ll_entertain, ll_dialog_login, ll_main_ui, ll_mycard_waiting_rooms;
    protected EditText et_username, et_password;
    protected TextView matchTvRank, matchTvWin, matchTvLose, matchTvDraw, matchTvAll, funTvRank, funTvWin, funTvLose, funTvDraw, funTvAll, tv_message, tv_dp_title, mNameView, mStatusView, tv_account_warning, tv_pwd_warning, tv_mycard_bbs;
    protected Button btn_login, btn_register;
    protected ProgressBar progressBar_login;
    protected ImageView mHeadView, img_logout, iv_refresh, btn_mycard_bbs;
    protected MyCard mMyCard;
    protected McUser mMcUser;
    public RelativeLayout rl_chat;
    protected ProgressBar pb_chat_loading, pb_loading;
    protected ServiceManagement serviceManagement;
    protected ChatMessage currentMessage;
    protected Switch swToggleCardImage;

    protected CircleProgressView funCpvRank, matchCpvRank;
    protected McDuelInfo currentMcDuelInfo;
    protected LinearLayout ll_user_data;
    protected UserDuelDetailAdapter duelDetailAdapter;

    protected SwipeRefreshLayout srl_update;
    protected RecyclerView rv_list;
    protected WatchDuelManagement duelManagement;
    protected WaitingDuelManagement waitingDuelManagement;
    protected DuelRoomBQAdapter duelRoomBQAdapter;
    protected DeckPieChartView pieChartView;
    protected TextView tvEmpty;
    protected SwipeRefreshLayout srl_mcNews;
    protected RecyclerView rv_mcNews_list;
    protected McNewsAdapter mcNewsAdapter;
    protected List<McNews> mcNewsList = new ArrayList<>();
    protected TabLayout tabLayout;
    protected LinearLayout ll_create_room;
    protected boolean isSpectateTab = true;
    protected RecyclerView rv_waiting_list;
    protected SwipeRefreshLayout srl_waiting;
    protected DuelRoomBQAdapter waitingRoomAdapter;
    protected List<DuelRoom> waitingRoomList = new ArrayList<>();


    @SuppressLint("HandlerLeak")
    Handler handler = new Handler() {

        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case TYPE_MC_LOGIN:
                    McUser mcUser = (McUser) msg.obj;
                    if (!TextUtils.isEmpty(mcUser.getAvatar_url())) {
                        GlideCompat.with(getActivity()).load(mcUser.getAvatar_url()).into(mHeadView);//刷新头像图片
                    }
                    mNameView.setText(mcUser.getUsername());//刷新用户名
                    mStatusView.setText(mcUser.getEmail());//刷新账号信息
                    serviceManagement.start();
                    break;

                case TYPE_MC_LOGIN_FAILED:
                    break;
                case DownloadUtil.TYPE_DOWNLOAD_ING:
                    break;
                case DownloadUtil.TYPE_DOWNLOAD_EXCEPTION:
                    YGOUtil.showTextToast(getString(R.string.tip_download_failed));
                    break;
                case DownloadUtil.TYPE_DOWNLOAD_OK:
                    if (msg.obj.toString().endsWith(Constants.YDK_FILE_EX)) {
                        YGOUtil.showTextToast(Gravity.TOP, getString(R.string.tip_download_OK) + getString(R.string.deck_list), Toast.LENGTH_SHORT);
                    } else if (msg.obj.toString().endsWith(Constants.YRP_FILE_EX)) {
                        YGOUtil.showTextToast(Gravity.TOP, getString(R.string.tip_download_OK) + getString(R.string.replay_list), Toast.LENGTH_SHORT);
                    } else if (msg.obj.toString().endsWith(Constants.YPK_FILE_EX) || msg.obj.toString().endsWith(Constants.CORE_LIMIT_PATH)) {
                        YGOUtil.showTextToast(Gravity.TOP, getString(R.string.ypk_installed) + getString(R.string.restart_app), Toast.LENGTH_SHORT);
                        DataManager.get().load(true);
                    } else {
                        YGOUtil.showTextToast(Gravity.TOP, getString(R.string.tip_download_OK) + AppsSettings.get().getResourcePath(), Toast.LENGTH_LONG);
                    }
                    break;
                case QUERY_DUEL_INFO_OK:
                    McDuelInfo newMcDuelInfo = (McDuelInfo) msg.obj;
                    if (currentMcDuelInfo != null && newMcDuelInfo != null) {
                        StringBuilder diffMsg = new StringBuilder();
                        Integer oldDp = currentMcDuelInfo.getDp();
                        Integer newDp = newMcDuelInfo.getDp();
                        Integer oldExp = currentMcDuelInfo.getExp();
                        Integer newExp = newMcDuelInfo.getExp();
                        if (oldDp != null && newDp != null && !oldDp.equals(newDp)) {
                            int dpDiff = newDp - oldDp;
                            diffMsg.append("D.P ").append(dpDiff > 0 ? "+" : "").append(dpDiff);
                        }
                        if (oldExp != null && newExp != null && !oldExp.equals(newExp)) {
                            int expDiff = newExp - oldExp;
                            if (diffMsg.length() > 0) {
                                diffMsg.append("  ");
                            }
                            diffMsg.append("EXP ").append(expDiff > 0 ? "+" : "").append(expDiff);
                        }
                        if (diffMsg.length() > 0) {
                            YGOUtil.showTextToast(Gravity.TOP, diffMsg.toString(), Toast.LENGTH_LONG);
                        }
                    }
                    currentMcDuelInfo = newMcDuelInfo;
                    updateFunRank(currentMcDuelInfo);
                    updateMatchRank(currentMcDuelInfo);
                    if (currentMcDuelInfo != null) {
                        tv_dp_title.setText("D.P: " + currentMcDuelInfo.getDp() + "\nEXP: " + currentMcDuelInfo.getExp());
                    }
                    pb_loading.setVisibility(View.GONE);
                    iv_refresh.setVisibility(View.VISIBLE);
                    break;
                case QUERY_DUEL_INFO_EXCEPTION:
                    Log.e("MCFragment", "查询决斗信息失败: " + msg.obj);
                    pb_loading.setVisibility(View.GONE);
                    iv_refresh.setVisibility(View.VISIBLE);
                    YGOUtil.show(YGOUtil.s(R.string.loading_failed) +": " + msg.obj.toString());
                    break;
                case MC_MATCH_ATHLETIC_OK:
                case MC_MATCH_ENTERTAIN_OK:
                    YGOServer ygoServer = (YGOServer) msg.obj;
                    if (ygoServer == null) {
                        YGOUtil.show("未匹配到对手");
                        break;
                    }
                    YGOUtil.joinGame(getActivity(), ygoServer, ygoServer.getPassword());
                    break;
                case MC_MATCH_ATHLETIC_EXCEPTION:
                case MC_MATCH_ENTERTAIN_EXCEPTION:
                    YGOUtil.show("匹配失败: " + msg.obj.toString());
                    break;
                case TYPE_MC_NEWS_QUERY_OK:
                    mcNewsList = (List<McNews>) msg.obj;
                    if (srl_mcNews != null) {
                        srl_mcNews.setRefreshing(false);
                    }
                    updateMcNewsList(mcNewsList);
                    break;
                case TYPE_MC_NEWS_QUERY_EXCEPTION:
                    Log.e("MCFragment", "查询新闻失败: " + msg.obj);
                    if (srl_mcNews != null) {
                        srl_mcNews.setRefreshing(false);
                    }
                    YGOUtil.show(YGOUtil.s(R.string.loading_failed) + ": " + msg.obj.toString());
                    break;
            }
        }
    };

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
        mStringManager = DataManager.get().getStringManager();
        ll_dialog_login = view.findViewById(R.id.ll_dialog_login);
        ll_main_ui = view.findViewById(R.id.ll_main_ui);
        et_username = view.findViewById(R.id.et_username);
        et_password = view.findViewById(R.id.et_password);
        tv_account_warning = view.findViewById(R.id.tv_account_warning);
        tv_pwd_warning = view.findViewById(R.id.tv_pwd_warning);

        btn_login = view.findViewById(R.id.btn_login);
        btn_login.setOnClickListener(this);
        btn_register = view.findViewById(R.id.btn_register);
        btn_register.setOnClickListener(this);

        progressBar_login = view.findViewById(R.id.progressBar_login);

        tv_mycard_bbs = view.findViewById(R.id.tv_mycard_bbs);
        btn_mycard_bbs = view.findViewById(R.id.btn_mycard_bbs);
        btn_mycard_bbs.setOnClickListener(this);

        mHeadView = view.findViewById(R.id.img_head);
        mHeadView.setOnClickListener(this);
        img_logout = view.findViewById(R.id.img_logout);
        img_logout.setOnClickListener(this);
        mNameView = view.findViewById(R.id.tv_name);
        mStatusView = view.findViewById(R.id.tv_dp);

        rl_chat = view.findViewById(R.id.rl_chat);
        rl_chat.setOnClickListener(this);
        tv_message = view.findViewById(R.id.tv_message);
        pb_chat_loading = view.findViewById(R.id.pb_chat_loading);

        serviceManagement = ServiceManagement.getDx();
        serviceManagement.addJoinRoomListener(this);
        serviceManagement.addListener(this);

        initRankViews(view);
        initMatchViews(view);
        initWatchDuelView(view);
        initWaitingRoomView(view);
        initPieChartViews(view);
        initMcNewsView(view);
        setupTabLayout(view);

        checkLoginState();

    }

    protected void checkLoginState() {
        String token = SharedPreferenceUtil.getServerToken();
        Log.d("MCFragment", "检查登录状态 - token: " + (token != null ? "已存在" : "null"));

        if (TextUtils.isEmpty(token)) {
            Log.d("MCFragment", "未登录状态，显示登录界面");
            ll_dialog_login.setVisibility(View.VISIBLE);
            //ll_main_ui.setVisibility(View.GONE);
            mMcUser = new McUser();
        } else {
            Log.d("MCFragment", "已登录状态，显示主界面");
            ll_dialog_login.setVisibility(View.GONE);
            //ll_main_ui.setVisibility(View.VISIBLE);

            String userName = SharedPreferenceUtil.getMyCardUserName();
            int userId = SharedPreferenceUtil.getServerUserId();

            Log.d("MCFragment", "用户信息 - username: " + userName + ", userId: " + userId);

            if (!TextUtils.isEmpty(userName) && userId != 0) {
                mNameView.setText(userName);

                mMcUser = new McUser();
                mMcUser.setUsername(userName);
                mMcUser.setExternal_id(userId);
                mMcUser.setToken(token);

                UserManagement.getDx().setMcUser(mMcUser);

                GlideCompat.with(getActivity()).load(ChatMessage.getAvatarUrl(userName)).into(mHeadView);

                queryDuelInfo();

                serviceManagement.start();
            } else {
                SharedPreferenceUtil.deleteServerToken();
                ll_dialog_login.setVisibility(View.VISIBLE);
                ll_main_ui.setVisibility(View.GONE);
                mMcUser = new McUser();
            }
        }
    }

    protected void attemptLogin() {
        String username = et_username.getText().toString().trim();
        String password = et_password.getText().toString().trim();

        if (username.isEmpty()) {
            tv_account_warning.setVisibility(View.VISIBLE);
            return;
        } else {
            tv_account_warning.setVisibility(View.GONE);
        }

        if (password.isEmpty()) {
            tv_pwd_warning.setVisibility(View.VISIBLE);
            return;
        } else {
            tv_pwd_warning.setVisibility(View.GONE);
        }

        progressBar_login.setVisibility(View.VISIBLE);
        btn_login.setEnabled(false);

        VUiKit.defer().when(() -> {
            LoginResponse result = DeckSquareApiUtil.login(username, password);
            SharedPreferenceUtil.setServerToken(result.token);
            SharedPreferenceUtil.setServerUserId(result.user.id);
            SharedPreferenceUtil.setMyCardUserName(result.user.username);
            return result;
        }).fail((e) -> {
            YGOUtil.showTextToast(R.string.logining_failed);
            progressBar_login.setVisibility(View.GONE);
            btn_login.setEnabled(true);
        }).done((result) -> {
            if (result != null) {
                ll_dialog_login.setVisibility(View.GONE);
                ll_main_ui.setVisibility(View.VISIBLE);
                progressBar_login.setVisibility(View.GONE);
                btn_login.setEnabled(true);

                String userName = result.user.username;
                mNameView.setText(userName);

                McUser mcUser = new McUser();
                mcUser.setUsername(userName);
                mcUser.setExternal_id(result.user.id);
                mcUser.setToken(result.token);
                mcUser.setAvatar_url(ChatMessage.getAvatarUrl(userName));

                mMcUser = mcUser;
                UserManagement.getDx().setMcUser(mcUser);

                GlideCompat.with(getActivity()).load(mcUser.getAvatar_url()).into(mHeadView);

                YGOUtil.showTextToast(R.string.login_succeed);

                queryDuelInfo();
            } else {
                YGOUtil.showTextToast(R.string.logining_failed);
                progressBar_login.setVisibility(View.GONE);
                btn_login.setEnabled(true);
            }
        });
    }

    protected void queryDuelInfo() {
        if (mMcUser == null) {
            return;
        }

        String username = mMcUser.getUsername();
        if (TextUtils.isEmpty(username)) {
            return;
        }

        Log.d("MCFragment", "开始查询决斗信息，用户名: " + username);
        pb_loading.setVisibility(View.VISIBLE);
        iv_refresh.setVisibility(View.GONE);
        MyCard.findUserDuelInfo(username, (mcDuelInfo, exception) -> {
            if (exception != null) {
                Log.e("MCFragment", "查询决斗信息异常: " + exception);
            } else if (mcDuelInfo != null) {
                Log.d("MCFragment", "查询决斗信息成功: " + mcDuelInfo);
            }
            HandlerUtil.sendMessage(handler, exception, QUERY_DUEL_INFO_OK, mcDuelInfo, QUERY_DUEL_INFO_EXCEPTION);
        });
    }

    protected boolean isUserLoggedIn() {
        if (mMcUser == null) {
            return false;
        }

        String username = mMcUser.getUsername();
        String token = SharedPreferenceUtil.getServerToken();

        boolean hasUsername = !TextUtils.isEmpty(username);
        boolean hasExternalId = !TextUtils.isEmpty(token);

        Log.d("MCFragment", "登录状态检查 - username: " + username + ", external_id: " + token + ", hasUsername: " + hasUsername + ", hasExternalId: " + hasExternalId);

        return hasUsername && hasExternalId;
    }

    @Override
    public void onResume() {
        YGOStarter.onResumed(getActivity());
        queryDuelInfo();
        super.onResume();
    }

    @Override
    public void onDestroy() {
        YGOStarter.onDestroy(getActivity());
        if (duelManagement != null) {
            duelManagement.closeConnect();
        }
        if (waitingDuelManagement != null) {
            waitingDuelManagement.closeConnect();
        }
        super.onDestroy();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == Activity.RESULT_OK) {
            switch (requestCode) {
                case REQUEST_MATCH_ATHLETIC:
                case REQUEST_MATCH_ENTERTAIN:
                    queryDuelInfo();
                    break;
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
    public void onBackHome() {

    }

    @Override
    public void onLogin(McUser mcUser, String exception) {
        if (!TextUtils.isEmpty(exception)) {
            Log.e("MCFragment", "登录失败: " + exception);
            return;
        }

        Log.d("MCFragment", "登录成功 - username: " + mcUser.getUsername() + ", external_id: " + mcUser.getExternal_id() + ", token: " + (mcUser.getToken() != null ? "已设置" : "null"));

        mMcUser = mcUser;
        serviceManagement.disSerVice();

        Message message = new Message();
        message.obj = mcUser;
        message.what = TYPE_MC_LOGIN;
        handler.sendMessage(message);
        queryDuelInfo();
    }

    @Override
    public void onUpdate(String name, String icon, String statu) {
        McUser mcUser = new McUser();
        mcUser.setUsername(name);
        mcUser.setAvatar_url(icon);
        mcUser.setEmail(statu);
        mMcUser = mcUser;

        Message message = new Message();
        message.obj = mcUser;
        message.what = TYPE_MC_LOGIN;
        handler.sendMessage(message);

        queryDuelInfo();
    }

    @Override
    public void onLogout(String message) {
        Log.d("MCFragment", "收到登出回调: " + message);

        if (!TextUtils.isEmpty(message)) {
            YGOUtil.showTextToast(message);
        }

        performLogout();
    }

    // ==== hooks implemented by subclasses in the chain (split artifact) ====
    abstract void updateFunRank(McDuelInfo mcDuelInfo);
    abstract void updateMatchRank(McDuelInfo mcDuelInfo);
    abstract void updateMcNewsList(List<McNews> newsList);
    abstract void performLogout();
    abstract void matchAthletic();
    abstract void matchEntertain();
    abstract void showDuelArena();
    abstract void joinMyCardWindbot(YGOServer server, String windbotName);
    abstract void initRankViews(View view);
    abstract void initMatchViews(View view);
    abstract void initWatchDuelView(View view);
    abstract void initWaitingRoomView(View view);
    abstract void initPieChartViews(View view);
    abstract void initMcNewsView(View view);
    abstract void setupTabLayout(View view);

}
