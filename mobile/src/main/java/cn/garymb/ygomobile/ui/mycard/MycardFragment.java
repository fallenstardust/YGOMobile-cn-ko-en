package cn.garymb.ygomobile.ui.mycard;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.listener.OnItemClickListener;
import com.feihua.dialogutils.util.DialogUtils;
import com.king.view.circleprogressview.CircleProgressView;
import com.ourygo.lib.duelassistant.util.Util;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.YGOStarter;
import cn.garymb.ygomobile.adapter.DuelRoomBQAdapter;
import cn.garymb.ygomobile.base.BaseFragemnt;
import cn.garymb.ygomobile.bean.ServerInfo;
import cn.garymb.ygomobile.lite.BuildConfig;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.cards.deck_square.DeckSquareApiUtil;
import cn.garymb.ygomobile.ui.cards.deck_square.api_response.LoginResponse;
import cn.garymb.ygomobile.ui.home.HomeActivity;
import cn.garymb.ygomobile.ui.mycard.base.OnDuelRoomListener;
import cn.garymb.ygomobile.ui.mycard.base.OnJoinChatListener;
import cn.garymb.ygomobile.ui.mycard.base.OnMcMatchListener;
import cn.garymb.ygomobile.ui.mycard.base.OnMcUserListener;
import cn.garymb.ygomobile.ui.mycard.bean.DuelRoom;
import cn.garymb.ygomobile.ui.mycard.bean.McDuelInfo;
import cn.garymb.ygomobile.ui.mycard.bean.McUser;
import cn.garymb.ygomobile.ui.mycard.bean.YGOServer;
import cn.garymb.ygomobile.ui.mycard.mcchat.ChatListener;
import cn.garymb.ygomobile.ui.mycard.mcchat.ChatMessage;
import cn.garymb.ygomobile.ui.mycard.watchDuel.McUserManagement;
import cn.garymb.ygomobile.ui.mycard.watchDuel.WatchDuelManagement;
import cn.garymb.ygomobile.ui.mycard.mcchat.management.ServiceManagement;
import cn.garymb.ygomobile.ui.mycard.mcchat.management.UserManagement;
import cn.garymb.ygomobile.ui.plus.DialogPlus;
import cn.garymb.ygomobile.ui.plus.VUiKit;
import cn.garymb.ygomobile.utils.DownloadUtil;
import cn.garymb.ygomobile.utils.HandlerUtil;
import cn.garymb.ygomobile.utils.SharedPreferenceUtil;
import cn.garymb.ygomobile.utils.YGOUtil;
import cn.garymb.ygomobile.utils.glide.GlideCompat;
import ocgcore.DataManager;

public class MycardFragment extends BaseFragemnt implements View.OnClickListener, MyCard.MyCardListener, OnJoinChatListener, ChatListener, OnDuelRoomListener {
    private static final int FILECHOOSER_RESULTCODE = 10;
    private static final int TYPE_MC_LOGIN = 0;
    private static final int TYPE_MC_LOGIN_FAILED = -1;
    private static final int QUERY_DUEL_INFO_OK = 8;
    private static final int QUERY_DUEL_INFO_EXCEPTION = 9;
    private static final int MC_MATCH_ATHLETIC_OK = 10;
    private static final int MC_MATCH_ATHLETIC_EXCEPTION = 11;
    private static final int MC_MATCH_ENTERTAIN_OK = 12;
    private static final int MC_MATCH_ENTERTAIN_EXCEPTION = 13;
    private static final int REQUEST_MATCH_ATHLETIC = 14;
    private static final int REQUEST_MATCH_ENTERTAIN = 15;

    private HomeActivity homeActivity;
    private LinearLayout ll_head_login, ll_dialog_login, ll_main_ui;
    private EditText et_username, et_password;
    private TextView tv_account_warning, tv_pwd_warning;
    private Button btn_login, btn_register;
    private ProgressBar progressBar_login;
    private ImageView mHeadView, img_logout;
    private TextView mNameView, mStatusView;
    private TextView tv_back_mc;
    private ImageView btnDeckWinRate;
    private MyCard mMyCard;
    private McUser mMcUser;
    public RelativeLayout rl_chat;
    private TextView tv_message, tv_match_title;
    private ProgressBar pb_chat_loading, pb_loading;
    private ImageView iv_refresh, btn_mycard_bbs;
    private Button btn_athletic, btn_entertain;
    private ServiceManagement serviceManagement;
    private ChatMessage currentMessage;
    private DialogUtils dialogUtils;

    private CircleProgressView funCpvRank, matchCpvRank;
    private TextView funTvRank, funTvWin, funTvLose, funTvDraw, funTvAll;
    private TextView matchTvRank, matchTvWin, matchTvLose, matchTvDraw, matchTvAll;
    private McDuelInfo currentMcDuelInfo;

    private SwipeRefreshLayout srl_update;
    private RecyclerView rv_list;

    private WatchDuelManagement duelManagement;
    private DuelRoomBQAdapter duelRoomBQAdapter;

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
                    currentMcDuelInfo = (McDuelInfo) msg.obj;
                    updateFunRank(currentMcDuelInfo);
                    updateMatchRank(currentMcDuelInfo);
                    if (currentMcDuelInfo != null) {
                        tv_match_title.setText("萌卡匹配（D.P：" + currentMcDuelInfo.getDp() + ")");
                    }
                    pb_loading.setVisibility(View.GONE);
                    iv_refresh.setVisibility(View.VISIBLE);
                    break;
                case QUERY_DUEL_INFO_EXCEPTION:
                    Log.e("MCFragment", "查询决斗信息失败: " + msg.obj);
                    pb_loading.setVisibility(View.GONE);
                    iv_refresh.setVisibility(View.VISIBLE);
                    YGOUtil.show("战绩加载失败: " + msg.obj.toString());
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

        ll_dialog_login = view.findViewById(R.id.ll_dialog_login);
        ll_main_ui = view.findViewById(R.id.ll_main_ui);
        et_username = view.findViewById(R.id.et_username);
        et_password = view.findViewById(R.id.et_password);
        tv_account_warning = view.findViewById(R.id.tv_account_warning);
        tv_pwd_warning = view.findViewById(R.id.tv_pwd_warning);
        btn_login = view.findViewById(R.id.btn_login);
        btn_register = view.findViewById(R.id.btn_register);
        progressBar_login = view.findViewById(R.id.progressBar_login);

        tv_back_mc = view.findViewById(R.id.tv_back_mc);
        tv_back_mc.setOnClickListener(this);

        btnDeckWinRate = view.findViewById(R.id.btn_deck_win_rate);
        if (btnDeckWinRate != null) {
            btnDeckWinRate.setOnClickListener(this);
        }

        btn_mycard_bbs = view.findViewById(R.id.btn_mycard_bbs);
        btn_mycard_bbs.setOnClickListener(this);

        ll_head_login = view.findViewById(R.id.ll_head_login);
        ll_head_login.setOnClickListener(this);
        mHeadView = view.findViewById(R.id.img_head);
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

        btn_login.setOnClickListener(v -> attemptLogin());
        btn_register.setOnClickListener(v -> {
        });

        checkLoginState();

    }

    private void checkLoginState() {
        String token = SharedPreferenceUtil.getServerToken();
        Log.d("MCFragment", "检查登录状态 - token: " + (token != null ? "已存在" : "null"));

        if (TextUtils.isEmpty(token)) {
            Log.d("MCFragment", "未登录状态，显示登录界面");
            ll_dialog_login.setVisibility(View.VISIBLE);
            ll_main_ui.setVisibility(View.GONE);
            mMcUser = new McUser();
        } else {
            Log.d("MCFragment", "已登录状态，显示主界面");
            ll_dialog_login.setVisibility(View.GONE);
            ll_main_ui.setVisibility(View.VISIBLE);

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
                Log.w("MCFragment", "用户信息不完整，清除token");
                SharedPreferenceUtil.deleteServerToken();
                ll_dialog_login.setVisibility(View.VISIBLE);
                ll_main_ui.setVisibility(View.GONE);
                mMcUser = new McUser();
            }
        }
    }

    private void attemptLogin() {
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

    private void initRankViews(View view) {
        funCpvRank = view.findViewById(R.id.fun_cpv_rank);
        funTvRank = view.findViewById(R.id.fun_tv_rank);
        funTvWin = view.findViewById(R.id.fun_tv_win);
        funTvLose = view.findViewById(R.id.fun_tv_lose);
        funTvDraw = view.findViewById(R.id.fun_tv_draw);
        funTvAll = view.findViewById(R.id.fun_tv_all);

        matchCpvRank = view.findViewById(R.id.match_cpv_rank);
        matchTvRank = view.findViewById(R.id.match_tv_rank);
        matchTvWin = view.findViewById(R.id.match_tv_win);
        matchTvLose = view.findViewById(R.id.match_tv_lose);
        matchTvDraw = view.findViewById(R.id.match_tv_draw);
        matchTvAll = view.findViewById(R.id.match_tv_all);

        if (funCpvRank != null) {
            funCpvRank.setMax(10000);
            funCpvRank.setOnChangeListener((progress, max) -> {
            });
        }

        if (matchCpvRank != null) {
            matchCpvRank.setMax(10000);
            matchCpvRank.setOnChangeListener((progress, max) -> {
            });
        }
    }

    private void initMatchViews(View view) {
        pb_loading = view.findViewById(R.id.pb_loading);
        iv_refresh = view.findViewById(R.id.iv_refresh);
        tv_match_title = view.findViewById(R.id.tv_match_title);
        btn_athletic = view.findViewById(R.id.btn_athletic);
        btn_entertain = view.findViewById(R.id.btn_entertain);

        dialogUtils = DialogUtils.getInstance(getActivity());

        btn_athletic.setOnClickListener(this);
        btn_entertain.setOnClickListener(this);
        iv_refresh.setOnClickListener(v -> {
            queryDuelInfo();
        });
    }

    private void initWatchDuelView(View view) {
        srl_update = view.findViewById(R.id.srl_update);
        rv_list = view.findViewById(R.id.rv_list);

        rv_list.setLayoutManager(new LinearLayoutManager(requireContext()));

        duelRoomBQAdapter = new DuelRoomBQAdapter(requireContext(), new ArrayList<DuelRoom>());
        rv_list.setAdapter(duelRoomBQAdapter);

        duelManagement = WatchDuelManagement.getInstance();
        duelManagement.addListener(this);

        duelRoomBQAdapter.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(@NonNull BaseQuickAdapter adapter, @NonNull View view, int position) {
                DuelRoom duelRoom = duelRoomBQAdapter.getItem(position);

                if (mMcUser == null || !isUserLoggedIn()) {
                    YGOUtil.showTextToast(R.string.login_mycard);
                    return;
                }

                new Thread(() -> {
                    try {
                        String token = SharedPreferenceUtil.getServerToken();
                        if (TextUtils.isEmpty(token)) {
                            throw new Exception("token not found");
                        }

                        int u16SecretStr = MyCard.getUserU16Secret(token);
                        if (u16SecretStr == 0) {
                            throw new Exception("获取u16Secret失败");
                        }

                        Log.e("WatchDuel", "u16SecretStr: " + u16SecretStr);

                        String password = YGOUtil.getWatchDuelPassword(duelRoom.getId(), mMcUser.getExternal_id(), u16SecretStr);
                        Log.e("WatchDuel password", "password: " + password);

                        ServerInfo serverInfo = new ServerInfo();
                        boolean isArenaTypeValid = true;

                        switch (duelRoom.getArenaType()) {
                            case DuelRoom.TYPE_ARENA_MATCH:
                                serverInfo.setServerAddr(MyCard.HOST_MC_MATCH);
                                serverInfo.setPort(MyCard.PORT_MC_MATCH);
                                break;
                            case DuelRoom.TYPE_ARENA_FUN:
                            case DuelRoom.TYPE_ARENA_AI:
                            case DuelRoom.TYPE_ARENA_FUN_MATCH:
                            case DuelRoom.TYPE_ARENA_FUN_SINGLE:
                            case DuelRoom.TYPE_ARENA_FUN_TAG:
                                serverInfo.setServerAddr(MyCard.HOST_MC_OTHER);
                                serverInfo.setPort(MyCard.PORT_MC_OTHER);
                                break;
                            default:
                                isArenaTypeValid = false;
                                break;
                        }

                        final boolean finalValid = isArenaTypeValid;
                        Activity activity = getActivity();

                        if (activity != null) {
                            activity.runOnUiThread(() -> {
                                if (!finalValid) {
                                    YGOUtil.show("未知房间，请更新软件后进入");
                                    return;
                                }

                                serverInfo.setPlayerName(mMcUser.getUsername());
                                YGOUtil.joinGame(activity, serverInfo, password);
                            });
                        }

                    } catch (Exception e) {
                        Log.e("MyCard", "获取u16Secret失败: " + e);
                        Activity activity = getActivity();
                        if (activity != null) {
                            activity.runOnUiThread(() -> {
                                YGOUtil.show("进入失败: " + e.getMessage());
                            });
                        }
                    }
                }).start();
            }
        });

        onRefresh();
    }

    private void onRefresh() {
        if (srl_update != null) {
            srl_update.setColorSchemeColors(YGOUtil.c(R.color.colorAccent));
            srl_update.setOnRefreshListener(new SwipeRefreshLayout.OnRefreshListener() {
                @Override
                public void onRefresh() {
                    refreshData();
                }
            });
            srl_update.setRefreshing(true);
        }

        refreshData();
    }

    private void refreshData() {
        if (duelManagement != null) {
            duelManagement.start();
        }
    }

    private void updateTitle() {
        if (getActivity() != null && duelRoomBQAdapter != null) {
            int size = duelRoomBQAdapter.getData().size();
            String title = "观战（" + size + "）";

            if (getActivity() instanceof androidx.appcompat.app.AppCompatActivity) {
                androidx.appcompat.app.AppCompatActivity appCompatActivity = (androidx.appcompat.app.AppCompatActivity) getActivity();
                if (appCompatActivity.getSupportActionBar() != null) {
                    appCompatActivity.getSupportActionBar().setTitle(title);
                }
            }
        }
    }

    private void queryDuelInfo() {
        if (mMcUser == null) {
            Log.w("MCFragment", "queryDuelInfo: mMcUser 为 null，无法查询战绩");
            return;
        }

        String username = mMcUser.getUsername();
        if (TextUtils.isEmpty(username)) {
            Log.w("MCFragment", "queryDuelInfo: username 为空，无法查询战绩");
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

    private void updateFunRank(McDuelInfo mcDuelInfo) {
        if (funCpvRank == null) return;

        if (mcDuelInfo == null) {
            funCpvRank.setProgress(0);
            funCpvRank.setLabelText("");
            if (funTvLose != null) funTvLose.setText("0");
            if (funTvWin != null) funTvWin.setText("0");
            if (funTvRank != null) funTvRank.setText("-");
            if (funTvDraw != null) funTvDraw.setText("0");
            if (funTvAll != null) funTvAll.setText("0");
            return;
        }

        float winRatio = mcDuelInfo.getFunWinRratio();
        int pro = (int) (winRatio * 100);
        funCpvRank.showAnimation(pro, 600);
        funCpvRank.setProgress(pro);
        funCpvRank.setLabelText(String.format("%.1f%%", winRatio));

        Integer exp = mcDuelInfo.getExp();
        Integer dp = mcDuelInfo.getDp();

        if (funTvLose != null) {
            funTvLose.setText(mcDuelInfo.getFunLose() != null ? mcDuelInfo.getFunLose().toString() : "0");
        }
        if (funTvWin != null) {
            funTvWin.setText(mcDuelInfo.getFunWin() != null ? mcDuelInfo.getFunWin().toString() : "0");
        }
        if (funTvDraw != null) {
            funTvDraw.setText(mcDuelInfo.getFunDraw() != null ? mcDuelInfo.getFunDraw().toString() : "0");
        }
        if (funTvAll != null) {
            funTvAll.setText(mcDuelInfo.getFunAll() != null ? mcDuelInfo.getFunAll().toString() : "0");
        }
        if (funTvRank != null) {
            funTvRank.setText(mcDuelInfo.getFunRank() != null ? mcDuelInfo.getFunRank().toString() : "-");
        }
    }

    private void updateMatchRank(McDuelInfo mcDuelInfo) {
        if (matchCpvRank == null) return;

        if (mcDuelInfo == null) {
            matchCpvRank.setProgress(0);
            matchCpvRank.setLabelText("");
            if (matchTvLose != null) matchTvLose.setText("0");
            if (matchTvWin != null) matchTvWin.setText("0");
            if (matchTvRank != null) matchTvRank.setText("-");
            if (matchTvDraw != null) matchTvDraw.setText("0");
            if (matchTvAll != null) matchTvAll.setText("0");
            return;
        }

        float winRatio = mcDuelInfo.getMatchWinRatio();
        int pro = (int) (winRatio * 100);
        matchCpvRank.showAnimation(pro, 600);
        matchCpvRank.setProgress(pro);
        matchCpvRank.setLabelText(String.format("%.1f%%", winRatio));

        Integer exp = mcDuelInfo.getExp();

        if (matchTvLose != null) {
            matchTvLose.setText(mcDuelInfo.getMatchLose() != null ? mcDuelInfo.getMatchLose().toString() : "0");
        }
        if (matchTvWin != null) {
            matchTvWin.setText(mcDuelInfo.getMatchWin() != null ? mcDuelInfo.getMatchWin().toString() : "0");
        }
        if (matchTvDraw != null) {
            matchTvDraw.setText(mcDuelInfo.getMatchDraw() != null ? mcDuelInfo.getMatchDraw().toString() : "0");
        }
        if (matchTvAll != null) {
            matchTvAll.setText(mcDuelInfo.getMatchAll() != null ? mcDuelInfo.getMatchAll().toString() : "0");
        }
        if (matchTvRank != null) {
            matchTvRank.setText(mcDuelInfo.getMatchRank() != null ? mcDuelInfo.getMatchRank().toString() : "-");
        }
    }

    private void matchAthletic() {
        if (!isUserLoggedIn()) {
            YGOUtil.showTextToast(R.string.login_mycard);
            return;
        }
        DialogPlus dlg = new DialogPlus(getActivity());
        dlg.setTitle(R.string.match_start);
        dlg.setCancelable(false);
        dlg.showProgressBar();
        dlg.setMessage(R.string.waiting_message);
        dlg.setLeftButtonText(R.string.cancel);
        dlg.setLeftButtonListener((d, s) -> {
            MyCard.cancelMatch();
            dlg.dismiss();
        });
        dlg.show();

        MyCard.startMatch(mMcUser, MyCard.MATCH_TYPE_ATHLETIC, new OnMcMatchListener() {
            @Override
            public void onMcMatch(YGOServer ygoServer, String password, String exception) {
                dlg.dismiss();
                HandlerUtil.sendMessage(handler, exception, MC_MATCH_ATHLETIC_OK, ygoServer, MC_MATCH_ATHLETIC_EXCEPTION);
            }
        });
    }

    private void matchEntertain() {
        if (!isUserLoggedIn()) {
            YGOUtil.showTextToast(R.string.login_mycard);
            return;
        }

        DialogPlus dlg = new DialogPlus(getActivity());
        dlg.setTitle(R.string.fun_start);
        dlg.setCancelable(false);
        dlg.showProgressBar();
        dlg.setMessage(R.string.waiting_message);
        dlg.setLeftButtonText(R.string.cancel);
        dlg.setLeftButtonListener((d, s) -> {
            MyCard.cancelMatch();
            dlg.dismiss();
        });
        dlg.show();

        MyCard.startMatch(mMcUser, MyCard.MATCH_TYPE_ENTERTAIN, new OnMcMatchListener() {
            @Override
            public void onMcMatch(YGOServer ygoServer, String password, String exception) {
                dlg.dismiss();
                HandlerUtil.sendMessage(handler, exception, MC_MATCH_ENTERTAIN_OK, ygoServer, MC_MATCH_ENTERTAIN_EXCEPTION);
            }
        });
    }

    private boolean isUserLoggedIn() {
        if (mMcUser == null) {
            Log.d("MCFragment", "用户未登录: mMcUser 为 null");
            return false;
        }

        String username = mMcUser.getUsername();
        int externalId = mMcUser.getExternal_id();

        boolean hasUsername = !TextUtils.isEmpty(username);
        boolean hasExternalId = externalId != 0;

        Log.d("MCFragment", "登录状态检查 - username: " + username + ", external_id: " + externalId + ", hasUsername: " + hasUsername + ", hasExternalId: " + hasExternalId);

        return hasUsername && hasExternalId;
    }

    @Override
    public void onResume() {
        YGOStarter.onResumed(getActivity());
        super.onResume();
    }

    @Override
    public void onDestroy() {
        YGOStarter.onDestroy(getActivity());
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
    public boolean onBackPressed() {
        if (homeActivity.fragment_mycard_chatting_room.isVisible()) {
            getChildFragmentManager().beginTransaction().hide(homeActivity.fragment_mycard_chatting_room).commit();
            rl_chat.setVisibility(View.VISIBLE);
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
            case R.id.btn_deck_win_rate:
                openDeckWinRateFragment();
                break;
            case R.id.img_logout:
                performLogout();
                break;
            case R.id.ll_head_login:
                if (homeActivity.fragment_mycard_chatting_room.isVisible()) {
                    getChildFragmentManager().beginTransaction().hide(homeActivity.fragment_mycard_chatting_room).commit();
                    rl_chat.setVisibility(View.VISIBLE);
                }
                break;
            case R.id.tv_back_mc:
                onHome();
                break;
            case R.id.rl_chat:
                if (serviceManagement.isConnected()) {
                    if (!homeActivity.fragment_mycard_chatting_room.isAdded()) {
                        getChildFragmentManager().beginTransaction().add(R.id.fragment_content, homeActivity.fragment_mycard_chatting_room).commit();
                        rl_chat.setVisibility(View.INVISIBLE);
                    } else {
                        if (homeActivity.fragment_mycard_chatting_room.isHidden()) {
                            getChildFragmentManager().beginTransaction().show(homeActivity.fragment_mycard_chatting_room).commit();
                            rl_chat.setVisibility(View.INVISIBLE);
                        } else {
                            getChildFragmentManager().beginTransaction().hide(homeActivity.fragment_mycard_chatting_room).commit();
                            rl_chat.setVisibility(View.VISIBLE);
                        }

                    }
                } else {
                    if (mMcUser.getUsername() != null && mMcUser.getPassword() != null) {
                        serviceManagement.start();
                    } else {
                        YGOUtil.showTextToast(R.string.login_mycard);
                    }
                }
                break;
            case R.id.btn_athletic:
                matchAthletic();
                break;
            case R.id.btn_entertain:
                matchEntertain();
                break;
            case R.id.btn_mycard_bbs:
                openBBSWithWebView();
                break;
        }
    }

    private void openBBSWithWebView() {
        if (!isUserLoggedIn()) {
            YGOUtil.showTextToast(R.string.login_mycard);
            return;
        }
        
        MyCardWebFragment bbsFragment = MyCardWebFragment.newInstance(
                mMyCard.getBBSUrl(),
                "萌卡论坛",
                true
        );
        
        homeActivity.getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_content, bbsFragment)
                .addToBackStack(null)
                .commit();
    }

    private void openDeckWinRateFragment() {
        if (homeActivity == null) {
            return;
        }

        DeckWinRateFragment deckWinRateFragment = new DeckWinRateFragment();
        homeActivity.getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_content, deckWinRateFragment)
                .addToBackStack(null)
                .commit();
    }

    private void performLogout() {
        Log.d("MCFragment", "用户点击退出登录按钮");

        serviceManagement.disSerVice();
        serviceManagement.setIsListener(false);

        currentMcDuelInfo = null;
        updateFunRank(null);
        updateMatchRank(null);
        tv_match_title.setText("匹配对战");

        pb_loading.setVisibility(View.GONE);
        iv_refresh.setVisibility(View.VISIBLE);

        pb_chat_loading.setVisibility(View.GONE);
        tv_message.setText(R.string.login_mycard);
        currentMessage = null;

        mMcUser = new McUser();

        SharedPreferenceUtil.deleteServerToken();
        SharedPreferenceUtil.setServerUserId(0);
        SharedPreferenceUtil.setMyCardUserName("");

        ll_dialog_login.setVisibility(View.VISIBLE);
        ll_main_ui.setVisibility(View.GONE);

        et_username.setText("");
        et_password.setText("");

        YGOUtil.showTextToast(R.string.logout_mycard);

        Log.d("MCFragment", "退出登录完成，已显示登录界面");
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
        if (!isUserLoggedIn()) {

        }
    }

    @Override
    public void onChatLogin(String exception) {
        pb_chat_loading.setVisibility(View.GONE);
        if (TextUtils.isEmpty(exception)) {
            if (currentMessage == null) {
                List<ChatMessage> data = serviceManagement.getData();
                if (data != null && data.size() > 0) currentMessage = data.get(data.size() - 1);
            }
            if (currentMessage == null) tv_message.setText(R.string.loading);
            else tv_message.setText(currentMessage.getName() + "：" + currentMessage.getMessage());
        } else {
            Log.e("MyCardFragment", "登录失败" + exception);
            tv_message.setText(R.string.logining_failed);
            HandlerUtil.sendMessage(handler, TYPE_MC_LOGIN_FAILED, exception);
            serviceManagement.setIsListener(false);
            if (exception.endsWith("not-authorized")) {
                YGOUtil.showTextToast(getString(R.string.notice_verify_email));
            } else if (exception.endsWith("No address associated with hostname")) {
                YGOUtil.showTextToast(getString(R.string.tip_no_netwrok));
            } else {
                YGOUtil.showTextToast(getString(R.string.mc_chat) + getString(R.string.failed_reason) + exception);
            }
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
    public void onInit(List<DuelRoom> duelRoomList) {
        if (srl_update != null) {
            srl_update.setRefreshing(false);
            srl_update.setEnabled(false);
        }

        duelRoomBQAdapter.addData(duelRoomList);
        updateTitle();
    }

    @Override
    public void onCreate(List<DuelRoom> duelRoomList) {
        duelRoomBQAdapter.addData(duelRoomList);
        updateTitle();
    }

    @Override
    public void onUpdate(List<DuelRoom> duelRoomList) {

    }

    @Override
    public void onDelete(List<DuelRoom> duelRoomList) {
        duelRoomBQAdapter.remove(duelRoomList);
        updateTitle();
    }

    @Override
    public boolean isListenerEffective() {
        return Util.isContextExisted(getActivity());
    }

    @Override
    public void addChatMessage(ChatMessage message) {
        currentMessage = message;
        if (message != null) tv_message.setText(message.getName() + "：" + message.getMessage());
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
