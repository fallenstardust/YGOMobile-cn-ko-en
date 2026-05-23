package cn.garymb.ygomobile.ui.mycard;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
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
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.chad.library.adapter.base.BaseQuickAdapter;
import com.chad.library.adapter.base.listener.OnItemClickListener;
import com.feihua.dialogutils.util.DialogUtils;
import com.google.android.material.tabs.TabLayout;
import com.king.view.circleprogressview.CircleProgressView;
import com.ourygo.lib.duelassistant.util.Util;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.TimeZone;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.YGOStarter;
import cn.garymb.ygomobile.adapter.DuelRoomBQAdapter;
import cn.garymb.ygomobile.base.BaseFragemnt;
import cn.garymb.ygomobile.bean.ServerInfo;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.adapters.SimpleListAdapter;
import cn.garymb.ygomobile.ui.cards.deck_square.DeckSquareApiUtil;
import cn.garymb.ygomobile.ui.cards.deck_square.api_response.LoginResponse;
import cn.garymb.ygomobile.ui.home.HomeActivity;
import cn.garymb.ygomobile.ui.mycard.adapter.McNewsAdapter;
import cn.garymb.ygomobile.ui.mycard.bean.McNews;
import cn.garymb.ygomobile.ui.mycard.bean.MyCardPieChart;
import cn.garymb.ygomobile.ui.widget.DeckPieChartView;
import cn.garymb.ygomobile.ui.mycard.base.OnDuelRoomListener;
import cn.garymb.ygomobile.ui.mycard.base.OnJoinChatListener;
import cn.garymb.ygomobile.ui.mycard.base.OnMcMatchListener;
import cn.garymb.ygomobile.ui.mycard.bean.DuelRoom;
import cn.garymb.ygomobile.ui.mycard.bean.McDuelInfo;
import cn.garymb.ygomobile.ui.mycard.bean.McDuelResult;
import cn.garymb.ygomobile.ui.mycard.bean.McUser;
import cn.garymb.ygomobile.ui.mycard.bean.YGOServer;
import cn.garymb.ygomobile.ui.mycard.mcchat.ChatListener;
import cn.garymb.ygomobile.ui.mycard.mcchat.ChatMessage;
import cn.garymb.ygomobile.ui.mycard.mcchat.management.ServiceManagement;
import cn.garymb.ygomobile.ui.mycard.mcchat.management.UserManagement;
import cn.garymb.ygomobile.ui.mycard.watchDuel.WatchDuelManagement;
import cn.garymb.ygomobile.ui.plus.DialogPlus;
import cn.garymb.ygomobile.ui.plus.VUiKit;
import cn.garymb.ygomobile.utils.DownloadUtil;
import cn.garymb.ygomobile.utils.HandlerUtil;
import cn.garymb.ygomobile.utils.OkhttpUtil;
import cn.garymb.ygomobile.utils.SharedPreferenceUtil;
import cn.garymb.ygomobile.utils.YGOUtil;
import cn.garymb.ygomobile.utils.glide.GlideCompat;
import ocgcore.DataManager;
import ocgcore.StringManager;
import okhttp3.Call;

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
    private static final int TYPE_MC_NEWS_QUERY_OK = 16;
    private static final int TYPE_MC_NEWS_QUERY_EXCEPTION = 17;
    private static final long MATCH_RESULT_LOOKBACK_MS = 2 * 60 * 1000;

    private HomeActivity homeActivity;
    private LinearLayout ll_athletic, ll_entertain, ll_dialog_login, ll_main_ui, ll_mycard_waiting_rooms;
    private EditText et_username, et_password;
    private TextView matchTvRank, matchTvWin, matchTvLose, matchTvDraw, matchTvAll, funTvRank, funTvWin, funTvLose, funTvDraw, funTvAll, tv_message, tv_dp_title, mNameView, mStatusView, tv_account_warning, tv_pwd_warning, tv_mycard_bbs;
    private Button btn_login, btn_register, btn_mycard_ai;
    private ProgressBar progressBar_login;
    private ImageView mHeadView, img_logout, iv_refresh, btn_mycard_bbs;
    private MyCard mMyCard;
    private McUser mMcUser;
    public RelativeLayout rl_chat;
    private ProgressBar pb_chat_loading, pb_loading;
    private ServiceManagement serviceManagement;
    private ChatMessage currentMessage;
    private DialogUtils dialogUtils;

    private CircleProgressView funCpvRank, matchCpvRank;
    private McDuelInfo currentMcDuelInfo;
    private SwipeRefreshLayout srl_update;
    private RecyclerView rv_list;
    private WatchDuelManagement duelManagement;
    private DuelRoomBQAdapter duelRoomBQAdapter;
    private DeckPieChartView pieChartView;
    private TextView tvEmpty;
    private View mainContentView;
    private SwipeRefreshLayout srl_mcNews;
    private RecyclerView rv_mcNews_list;
    private McNewsAdapter mcNewsAdapter;
    private List<McNews> mcNewsList = new ArrayList<>();
    private TabLayout tabLayout;
    private Button btnCreateRoom;
    private boolean isSpectateTab = true;
    private RecyclerView rv_waiting_list;
    private SwipeRefreshLayout srl_waiting;
    private DuelRoomBQAdapter waitingRoomAdapter;
    private List<DuelRoom> waitingRoomList = new ArrayList<>();
    private List<YGOServer> myCardServers = new ArrayList<>();
    private YGOServer currentWindbotServer;
    private int pendingMatchType = -1;
    private long pendingMatchStartedAt = 0;
    private String lastShownResultEndTime;


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
                        tv_dp_title.setText("D.P：" + currentMcDuelInfo.getDp());
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
                    pendingMatchType = msg.what == MC_MATCH_ATHLETIC_OK ? MyCard.MATCH_TYPE_ATHLETIC : MyCard.MATCH_TYPE_ENTERTAIN;
                    pendingMatchStartedAt = System.currentTimeMillis();
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
                    YGOUtil.show("资讯加载失败: " + msg.obj.toString());
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

        mainContentView = view.findViewById(R.id.ll_main_ui);
        ll_dialog_login = view.findViewById(R.id.ll_dialog_login);
        ll_main_ui = view.findViewById(R.id.ll_main_ui);
        et_username = view.findViewById(R.id.et_username);
        et_password = view.findViewById(R.id.et_password);
        tv_account_warning = view.findViewById(R.id.tv_account_warning);
        tv_pwd_warning = view.findViewById(R.id.tv_pwd_warning);
        btn_login = view.findViewById(R.id.btn_login);
        btn_register = view.findViewById(R.id.btn_register);
        progressBar_login = view.findViewById(R.id.progressBar_login);

        tv_mycard_bbs = view.findViewById(R.id.tv_mycard_bbs);
        btn_mycard_bbs = view.findViewById(R.id.btn_mycard_bbs);
        btn_mycard_bbs.setOnClickListener(this);

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
        initWaitingRoomView(view);
        initPieChartViews(view);
        initMcNewsView(view);
        setupTabLayout(view);

        btn_login.setOnClickListener(v -> attemptLogin());
        btn_register.setOnClickListener(v -> {
        });
        btnCreateRoom.setVisibility(View.VISIBLE);
        btnCreateRoom.setOnClickListener(v -> showMyCardCustomGame());

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
            LoginResponse result = DeckSquareApiUtil.loginWithDetailedError(username, password);
            if (result == null || result.token == null || result.user == null) {
                throw new IOException("Login response is empty or missing token/user.");
            }
            SharedPreferenceUtil.setServerToken(result.token);
            SharedPreferenceUtil.setServerUserId(result.user.id);
            SharedPreferenceUtil.setMyCardUserName(result.user.username);
            return result;
        }).fail((e) -> {
            String detail = e != null && !TextUtils.isEmpty(e.getMessage()) ? e.getMessage() : String.valueOf(e);
            Log.e("MCFragment", "MyCard login failed: " + detail, e);
            YGOUtil.showTextToast(getString(R.string.logining_failed) + ": " + detail, Toast.LENGTH_LONG);
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
        tv_dp_title = view.findViewById(R.id.tv_dp_title);
        ll_athletic = view.findViewById(R.id.ll_athletic);
        ll_entertain = view.findViewById(R.id.ll_entertain);
        btn_mycard_ai = view.findViewById(R.id.btn_mycard_ai);

        dialogUtils = DialogUtils.getInstance(getActivity());

        ll_athletic.setOnClickListener(this);
        ll_entertain.setOnClickListener(this);
        btn_mycard_ai.setOnClickListener(this);
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

    private void showMyCardAiBattle() {
        if (!isUserLoggedIn()) {
            YGOUtil.showTextToast(R.string.login_mycard);
            return;
        }
        if (!myCardServers.isEmpty()) {
            showWindbotServerDialog(getWindbotServers(myCardServers));
            return;
        }

        DialogPlus loading = DialogPlus.show(getActivity(), getString(R.string.mycard_ai_battle), getString(R.string.loading), false);
        MyCard.findMyCardServers((servers, exception) -> {
            Activity activity = getActivity();
            if (activity == null) {
                return;
            }
            activity.runOnUiThread(() -> {
                loading.dismiss();
                if (!TextUtils.isEmpty(exception)) {
                    YGOUtil.show(getString(R.string.mycard_server_load_failed) + exception);
                    return;
                }
                myCardServers = servers != null ? servers : new ArrayList<>();
                showWindbotServerDialog(getWindbotServers(myCardServers));
            });
        });
    }

    private List<YGOServer> getWindbotServers(List<YGOServer> servers) {
        List<YGOServer> result = new ArrayList<>();
        if (servers == null) {
            return result;
        }
        for (YGOServer server : servers) {
            if (server == null || server.isHidden()) {
                continue;
            }
            if (server.getWindbot() != null && !server.getWindbot().isEmpty()) {
                result.add(server);
            }
        }
        return result;
    }

    private void showWindbotServerDialog(List<YGOServer> windbotServers) {
        if (windbotServers == null || windbotServers.isEmpty()) {
            YGOUtil.show(R.string.mycard_ai_empty);
            return;
        }
        String[] names = new String[windbotServers.size()];
        for (int i = 0; i < windbotServers.size(); i++) {
            YGOServer server = windbotServers.get(i);
            names[i] = TextUtils.isEmpty(server.getName()) ? server.getServerAddr() + ":" + server.getPort() : server.getName();
        }

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.mycard_select_environment)
                .setItems(names, (dialog, which) -> {
                    currentWindbotServer = windbotServers.get(which);
                    showWindbotDialog(currentWindbotServer);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showWindbotDialog(YGOServer server) {
        if (server == null || server.getWindbot() == null || server.getWindbot().isEmpty()) {
            YGOUtil.show(R.string.mycard_ai_empty);
            return;
        }
        List<String> windbots = new ArrayList<>();
        windbots.add(getString(R.string.random));
        windbots.addAll(server.getWindbot());
        String[] names = windbots.toArray(new String[0]);
        String title = getString(R.string.mycard_select_ai);
        if (!TextUtils.isEmpty(server.getName())) {
            title += " - " + server.getName();
        }

        new AlertDialog.Builder(requireContext())
                .setTitle(title)
                .setItems(names, (dialog, which) -> {
                    String windbotName = which == 0
                            ? server.getWindbot().get(new Random().nextInt(server.getWindbot().size()))
                            : windbots.get(which);
                    joinMyCardWindbot(server, windbotName);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void joinMyCardWindbot(YGOServer server, String windbotName) {
        if (server == null || TextUtils.isEmpty(windbotName)) {
            return;
        }
        server.setPlayerName(mMcUser.getUsername());
        YGOUtil.joinGame(getActivity(), server, "AI#" + windbotName);
    }

    private void showMyCardCustomGame() {
        if (!isUserLoggedIn()) {
            YGOUtil.showTextToast(R.string.login_mycard);
            return;
        }
        if (!myCardServers.isEmpty()) {
            showCustomServerDialog(getCustomServers(myCardServers));
            return;
        }

        DialogPlus loading = DialogPlus.show(getActivity(), getString(R.string.create_custom_room), getString(R.string.loading), false);
        MyCard.findMyCardServers((servers, exception) -> {
            Activity activity = getActivity();
            if (activity == null) {
                return;
            }
            activity.runOnUiThread(() -> {
                loading.dismiss();
                if (!TextUtils.isEmpty(exception)) {
                    YGOUtil.show(getString(R.string.mycard_server_load_failed) + exception);
                    return;
                }
                myCardServers = servers != null ? servers : new ArrayList<>();
                showCustomServerDialog(getCustomServers(myCardServers));
            });
        });
    }

    private List<YGOServer> getCustomServers(List<YGOServer> servers) {
        List<YGOServer> result = new ArrayList<>();
        if (servers == null) {
            return result;
        }
        for (YGOServer server : servers) {
            if (server == null || server.isHidden()) {
                continue;
            }
            if (server.isCustom()) {
                result.add(server);
            }
        }
        return result;
    }

    private void showCustomServerDialog(List<YGOServer> customServers) {
        if (customServers == null || customServers.isEmpty()) {
            YGOUtil.show(R.string.mycard_custom_empty);
            return;
        }
        String[] names = new String[customServers.size()];
        for (int i = 0; i < customServers.size(); i++) {
            YGOServer server = customServers.get(i);
            names[i] = TextUtils.isEmpty(server.getName()) ? server.getServerAddr() + ":" + server.getPort() : server.getName();
        }

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.mycard_select_environment)
                .setItems(names, (dialog, which) -> showCustomActionDialog(customServers.get(which)))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showCustomActionDialog(YGOServer server) {
        String[] actions = {
                getString(R.string.mycard_create_room),
                getString(R.string.mycard_join_private_room)
        };
        new AlertDialog.Builder(requireContext())
                .setTitle(TextUtils.isEmpty(server.getName()) ? getString(R.string.create_custom_room) : server.getName())
                .setItems(actions, (dialog, which) -> {
                    if (which == 0) {
                        showCreateRoomDialog(server);
                    } else {
                        showJoinPrivateRoomDialog(server);
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private void showCreateRoomDialog(YGOServer server) {
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        int padding = YGOUtil.dp2px(12);
        root.setPadding(padding, padding, padding, 0);

        EditText titleInput = createNumberlessEditText(mMcUser.getUsername() + getString(R.string.mycard_room_suffix));
        Spinner ruleSpinner = createSpinner(new String[]{"OCG", "TCG", getString(R.string.mycard_rule_simplified), getString(R.string.mycard_rule_custom), getString(R.string.mycard_rule_exclusive_ban), getString(R.string.mycard_rule_all_cards)}, 0);
        Spinner modeSpinner = createSpinner(new String[]{getString(R.string.mode_single_duel), getString(R.string.mode_match_duel), getString(R.string.mode_tag_duel)}, 1);
        Spinner duelRuleSpinner = createSpinner(new String[]{"Master Rule", "Master Rule 2", "Master Rule 3", getString(R.string.mycard_rule_new_master), "Master Rule 2020"}, 4);
        EditText lpInput = createNumberEditText("8000");
        EditText handInput = createNumberEditText("5");
        EditText drawInput = createNumberEditText("1");
        CheckBox privateBox = createCheckBox(R.string.mycard_private_room);
        CheckBox noCheckBox = createCheckBox(R.string.mycard_no_check_deck);
        CheckBox noShuffleBox = createCheckBox(R.string.mycard_no_shuffle_deck);
        CheckBox autoDeathBox = createCheckBox(R.string.mycard_auto_death);

        root.addView(labelAndView(R.string.mycard_room_title, titleInput));
        root.addView(labelAndView(R.string.mycard_card_rule, ruleSpinner));
        root.addView(labelAndView(R.string.mycard_duel_mode, modeSpinner));
        root.addView(labelAndView(R.string.mycard_duel_rule, duelRuleSpinner));
        root.addView(labelAndView(R.string.mycard_start_lp, lpInput));
        root.addView(labelAndView(R.string.mycard_start_hand, handInput));
        root.addView(labelAndView(R.string.mycard_draw_count, drawInput));
        root.addView(privateBox);
        root.addView(noCheckBox);
        root.addView(noShuffleBox);
        root.addView(autoDeathBox);

        modeSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                lpInput.setText(position == YGOServer.MODE_TAG ? "16000" : "8000");
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.mycard_create_room)
                .setView(root)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.mycard_create_room, (dialog, which) -> {
                    boolean isPrivate = privateBox.isChecked();
                    String suffix = isPrivate
                            ? String.valueOf(mMcUser.getExternal_id() ^ 0x54321)
                            : titleInput.getText().toString();
                    createMyCardRoom(server,
                            isPrivate,
                            duelRuleSpinner.getSelectedItemPosition() + 1,
                            autoDeathBox.isChecked(),
                            ruleSpinner.getSelectedItemPosition(),
                            modeSpinner.getSelectedItemPosition(),
                            noCheckBox.isChecked(),
                            noShuffleBox.isChecked(),
                            parseEditInt(lpInput, 8000),
                            parseEditInt(handInput, 5),
                            parseEditInt(drawInput, 1),
                            suffix);
                })
                .show();
    }

    private void showJoinPrivateRoomDialog(YGOServer server) {
        EditText input = createNumberlessEditText("");
        input.setHint(R.string.mycard_private_room_password);
        int padding = YGOUtil.dp2px(20);
        input.setPadding(padding, 0, padding, 0);
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.mycard_join_private_room)
                .setView(input)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.quick_join, (dialog, which) -> joinPrivateMyCardRoom(server, input.getText().toString()))
                .show();
    }

    private void createMyCardRoom(YGOServer server, boolean isPrivate, int duelRule, boolean autoDeath,
                                  int rule, int mode, boolean noCheckDeck, boolean noShuffleDeck,
                                  int startLp, int startHand, int drawCount, String suffix) {
        fetchU16SecretAndJoin(server, u16Secret -> {
            String password = YGOUtil.getMyCardCreateRoomPassword(isPrivate, duelRule, autoDeath, rule, mode,
                    noCheckDeck, noShuffleDeck, startLp, startHand, drawCount, u16Secret, suffix);
            server.setPlayerName(mMcUser.getUsername());
            YGOUtil.joinGame(getActivity(), server, password);
            if (isPrivate) {
                YGOUtil.copyMessage(requireContext(), suffix);
                YGOUtil.show(getString(R.string.mycard_private_room_copied) + suffix);
            }
        });
    }

    private void joinPrivateMyCardRoom(YGOServer server, String privatePassword) {
        if (TextUtils.isEmpty(privatePassword)) {
            YGOUtil.show(R.string.mycard_private_room_password);
            return;
        }
        fetchU16SecretAndJoin(server, u16Secret -> {
            String password = YGOUtil.getMyCardJoinPrivatePassword(privatePassword, u16Secret);
            server.setPlayerName(mMcUser.getUsername());
            YGOUtil.joinGame(getActivity(), server, password);
        });
    }

    private void fetchU16SecretAndJoin(YGOServer server, OnU16SecretReady listener) {
        new Thread(() -> {
            try {
                String token = SharedPreferenceUtil.getServerToken();
                if (TextUtils.isEmpty(token)) {
                    throw new Exception("token not found");
                }
                int u16Secret = MyCard.getUserU16Secret(token);
                Activity activity = getActivity();
                if (activity != null) {
                    activity.runOnUiThread(() -> listener.onReady(u16Secret));
                }
            } catch (Exception e) {
                Log.e("MyCard", "get u16Secret failed: " + e);
                Activity activity = getActivity();
                if (activity != null) {
                    activity.runOnUiThread(() -> YGOUtil.show(getString(R.string.create_room_failed) + ": " + e.getMessage()));
                }
            }
        }).start();
    }

    private View labelAndView(int labelRes, View input) {
        LinearLayout container = new LinearLayout(requireContext());
        container.setOrientation(LinearLayout.VERTICAL);
        TextView label = new TextView(requireContext());
        label.setText(labelRes);
        container.addView(label);
        container.addView(input);
        return container;
    }

    private EditText createNumberEditText(String value) {
        EditText input = createNumberlessEditText(value);
        input.setInputType(InputType.TYPE_CLASS_NUMBER);
        return input;
    }

    private EditText createNumberlessEditText(String value) {
        EditText input = new EditText(requireContext());
        input.setText(value);
        input.setSingleLine(true);
        return input;
    }

    private Spinner createSpinner(String[] values, int selected) {
        Spinner spinner = new Spinner(requireContext());
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(selected);
        return spinner;
    }

    private CheckBox createCheckBox(int textRes) {
        CheckBox checkBox = new CheckBox(requireContext());
        checkBox.setText(textRes);
        return checkBox;
    }

    private int parseEditInt(EditText editText, int fallback) {
        try {
            return Integer.parseInt(editText.getText().toString());
        } catch (Exception e) {
            return fallback;
        }
    }

    private interface OnU16SecretReady {
        void onReady(int u16Secret);
    }

    private void initPieChartViews(View view) {
        pieChartView = view.findViewById(R.id.pie_chart_view);
        tvEmpty = view.findViewById(R.id.tv_empty);

        pieChartView.setOnClickListener(v -> {switchDeckWinRateFragment();});
        loadDeckWinRateData();
    }

    private void loadDeckWinRateData() {
        MyCardPieChart.getDayAthleticDeckTypeAnalytics(new MyCardPieChart.OnMyCardPieChartListener() {
            @Override
            public void onMyCardPieChartQuery(MyCardPieChart pieChart, String exception) {
                if (getActivity() == null) {
                    return;
                }

                getActivity().runOnUiThread(() -> {
                    if (exception != null) {
                        YGOUtil.show("加载卡组数据失败: " + exception);
                        tvEmpty.setVisibility(View.VISIBLE);
                        return;
                    }

                    if (pieChart != null && !pieChart.isEmpty()) {
                        updatePieChart(pieChart);
                    } else {
                        tvEmpty.setVisibility(View.VISIBLE);
                        pieChartView.clearData();
                    }
                });
            }
        });
    }

    private void updatePieChart(MyCardPieChart pieChart) {
        if (pieChart == null || pieChart.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            pieChartView.clearData();
            return;
        }

        int totalMatches = 0;
        List<DeckPieChartView.DeckData> deckDataList = new ArrayList<>();

        for (MyCardPieChart.Item item : pieChart) {
            MyCardPieChart.Matchup matchup = item.getMatchup();
            if (matchup == null) continue;

            int firstTotal = calculateTotal(matchup.getFirst());
            int secondTotal = calculateTotal(matchup.getSecond());
            int deckTotal = firstTotal + secondTotal;

            if (deckTotal > 0) {
                deckDataList.add(new DeckPieChartView.DeckData(item.getName(), deckTotal));
                totalMatches += deckTotal;
            }
        }

        if (totalMatches == 0) {
            tvEmpty.setVisibility(View.VISIBLE);
            pieChartView.clearData();
            return;
        }

        deckDataList.sort((d1, d2) -> Integer.compare(d2.count, d1.count));

        pieChartView.setData(deckDataList, totalMatches);
        tvEmpty.setVisibility(View.GONE);
    }

    private int calculateTotal(MyCardPieChart.First first) {
        if (first == null) return 0;
        return parseInt(first.getWin()) + parseInt(first.getDraw()) + parseInt(first.getLose());
    }

    private int calculateTotal(MyCardPieChart.Second second) {
        if (second == null) return 0;
        return parseInt(second.getWin()) + parseInt(second.getDraw()) + parseInt(second.getLose());
    }

    private int parseInt(String value) {
        if (value == null || value.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private boolean isUserLoggedIn() {
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
        super.onResume();

        if (mainContentView != null && isUserLoggedIn() && !hasVisibleChildFragment()) {
            mainContentView.setVisibility(View.VISIBLE);
        }
        checkPendingMatchResult();
    }

    private void checkPendingMatchResult() {
        if (pendingMatchType == -1 || !isUserLoggedIn()) {
            return;
        }
        MyCard.findLatestDuelResult(mMcUser.getUsername(), (result, exception) -> {
            Activity activity = getActivity();
            if (activity == null) {
                return;
            }
            activity.runOnUiThread(() -> {
                if (!TextUtils.isEmpty(exception)) {
                    Log.e("MCFragment", "query latest result failed: " + exception);
                    return;
                }
                if (result == null || TextUtils.isEmpty(result.getEndTime())) {
                    return;
                }
                if (result.getEndTime().equals(lastShownResultEndTime)) {
                    pendingMatchType = -1;
                    return;
                }
                long endTime = parseMyCardTime(result.getEndTime());
                if (pendingMatchStartedAt > 0 && endTime > 0 && endTime + MATCH_RESULT_LOOKBACK_MS < pendingMatchStartedAt) {
                    return;
                }
                String expectedType = pendingMatchType == MyCard.MATCH_TYPE_ATHLETIC ? MyCard.ARG_ATHLETIC : MyCard.ARG_ENTERTAIN;
                if (!TextUtils.isEmpty(result.getType()) && !expectedType.equals(result.getType())) {
                    return;
                }
                lastShownResultEndTime = result.getEndTime();
                pendingMatchType = -1;
                pendingMatchStartedAt = 0;
                showMatchResultDialog(result);
                queryDuelInfo();
            });
        });
    }

    private long parseMyCardTime(String value) {
        if (TextUtils.isEmpty(value)) {
            return 0;
        }
        String[] patterns = {
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd HH:mm:ss"
        };
        for (String pattern : patterns) {
            try {
                SimpleDateFormat format = new SimpleDateFormat(pattern, Locale.US);
                if (pattern.endsWith("'Z'")) {
                    format.setTimeZone(TimeZone.getTimeZone("UTC"));
                }
                Date date = format.parse(value);
                if (date != null) {
                    return date.getTime();
                }
            } catch (ParseException ignored) {
            }
        }
        return 0;
    }

    private void showMatchResultDialog(McDuelResult result) {
        String username = mMcUser != null ? mMcUser.getUsername() : "";
        String resultText;
        if (result.isDraw()) {
            resultText = getString(R.string.mycard_result_draw);
        } else if (result.isWin(username)) {
            resultText = getString(R.string.mycard_result_win);
        } else {
            resultText = getString(R.string.mycard_result_lose);
        }
        StringBuilder message = new StringBuilder();
        message.append(resultText).append('\n');
        message.append(getString(R.string.mycard_result_score))
                .append(result.getUserscorea())
                .append(" : ")
                .append(result.getUserscoreb())
                .append('\n');
        message.append(getString(R.string.mycard_result_opponent))
                .append(result.getOpponent(username))
                .append('\n');
        int dp = result.getPtFor(username);
        int dpEx = result.getPtExFor(username);
        if (result.isFirstWin() && result.isWin(username)) {
            dp -= 5;
        }
        message.append("D.P ")
                .append(formatSigned(dp - dpEx))
                .append('\n');
        if (result.isFirstWin() && result.isWin(username)) {
            message.append(getString(R.string.mycard_result_first_win))
                    .append(formatSigned(5))
                    .append('\n');
        }
        message.append("EXP ")
                .append(formatSigned(result.getExpFor(username) - result.getExpExFor(username)));

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.mycard_match_result)
                .setMessage(message.toString())
                .setPositiveButton(android.R.string.ok, null)
                .show();
    }

    private String formatSigned(int value) {
        return value > 0 ? "+" + value : String.valueOf(value);
    }

    private boolean hasVisibleChildFragment() {
        if (homeActivity.fragment_mycard_chatting_room != null &&
                homeActivity.fragment_mycard_chatting_room.isAdded() &&
                homeActivity.fragment_mycard_chatting_room.isVisible()) {
            return true;
        }
        if (homeActivity.fragment_deck_win_rate != null &&
                homeActivity.fragment_deck_win_rate.isAdded() &&
                homeActivity.fragment_deck_win_rate.isVisible()) {
            return true;
        }
        if (homeActivity.fragment_mycard_web != null &&
                homeActivity.fragment_mycard_web.isAdded() &&
                homeActivity.fragment_mycard_web.isVisible()) {
            return true;
        }
        return false;
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
        if (homeActivity.fragment_mycard_chatting_room != null && 
            homeActivity.fragment_mycard_chatting_room.isAdded() && 
            homeActivity.fragment_mycard_chatting_room.isVisible()) {
            getChildFragmentManager().beginTransaction()
                    .setCustomAnimations(R.anim.in_from_bottom, R.anim.out_to_top,
                            R.anim.in_from_bottom, R.anim.out_to_top)
                    .hide(homeActivity.fragment_mycard_chatting_room)
                    .commit();
            rl_chat.setVisibility(View.VISIBLE);
            return true;
        }
        
        if (homeActivity.fragment_deck_win_rate != null && 
            homeActivity.fragment_deck_win_rate.isAdded() && 
            homeActivity.fragment_deck_win_rate.isVisible()) {
            getChildFragmentManager().beginTransaction()
                    .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out,
                            android.R.anim.fade_in, android.R.anim.fade_out)
                    .hide(homeActivity.fragment_deck_win_rate)
                    .commit();
            if (mainContentView != null) {
                mainContentView.setVisibility(View.VISIBLE);
            }
            // 恢复所有按钮状态
            updateToolBarButtonState(null);
            return true;
        }
        
        if (homeActivity.fragment_mycard_web != null && 
            homeActivity.fragment_mycard_web.isAdded() && 
            homeActivity.fragment_mycard_web.isVisible()) {
            getChildFragmentManager().beginTransaction()
                    .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out,
                            android.R.anim.fade_in, android.R.anim.fade_out)
                    .remove(homeActivity.fragment_mycard_web)
                    .commit();
            homeActivity.fragment_mycard_web = null;
            if (mainContentView != null) {
                mainContentView.setVisibility(View.VISIBLE);
            }
            // 恢复所有按钮状态
            updateToolBarButtonState(null);
            return true;
        }
        
        return false;
    }

    /**
     * Called when a view has been clicked.
     *
     * @param v The view that was clicked.
     */
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.img_logout:
                performLogout();
                break;
            case R.id.rl_chat:
                if (serviceManagement.isConnected()) {
                    if (!homeActivity.fragment_mycard_chatting_room.isAdded()) {
                        getChildFragmentManager().beginTransaction()
                                .setCustomAnimations(R.anim.in_from_bottom, R.anim.out_to_top,
                                        R.anim.in_from_bottom, R.anim.out_to_top)
                                .add(R.id.fragment_chat_content, homeActivity.fragment_mycard_chatting_room).commit();
                        rl_chat.setVisibility(View.INVISIBLE);
                    } else {
                        if (homeActivity.fragment_mycard_chatting_room.isHidden()) {
                            getChildFragmentManager().beginTransaction()
                                    .setCustomAnimations(R.anim.in_from_bottom, R.anim.out_to_top,
                                            R.anim.in_from_bottom, R.anim.out_to_top)
                                    .show(homeActivity.fragment_mycard_chatting_room).commit();
                            rl_chat.setVisibility(View.INVISIBLE);
                        } else {
                            getChildFragmentManager().beginTransaction()
                                    .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out,
                                            android.R.anim.fade_in, android.R.anim.fade_out)
                                    .hide(homeActivity.fragment_mycard_chatting_room).commit();
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
            case R.id.ll_athletic:
                matchAthletic();
                break;
            case R.id.ll_entertain:
                matchEntertain();
                break;
            case R.id.btn_mycard_ai:
                showMyCardAiBattle();
                break;
            case R.id.btn_mycard_bbs:
                switchBBSWithWebView();
                break;
        }
    }

    private void switchBBSWithWebView() {
        if (!isUserLoggedIn()) {
            YGOUtil.showTextToast(R.string.login_mycard);
            return;
        }

        // 判断 MyCardWebFragment 是否已经显示
        boolean isShowing = homeActivity.fragment_mycard_web != null &&
                homeActivity.fragment_mycard_web.isAdded() &&
                homeActivity.fragment_mycard_web.isVisible();

        if (isShowing) {
            // 如果正在显示，则移除它
            getChildFragmentManager().beginTransaction()
                    .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out,
                            android.R.anim.fade_in, android.R.anim.fade_out)
                    .remove(homeActivity.fragment_mycard_web)
                    .commit();
            homeActivity.fragment_mycard_web = null;
            
            if (mainContentView != null) {
                mainContentView.setVisibility(View.VISIBLE);
            }
            
            // 恢复所有按钮状态
            updateToolBarButtonState(null);
        } else {
            // 如果未显示，则打开它
            
            // 如果 DeckWinRateFragment 正在显示，先隐藏它
            if (homeActivity.fragment_deck_win_rate != null &&
                    homeActivity.fragment_deck_win_rate.isAdded() &&
                    homeActivity.fragment_deck_win_rate.isVisible()) {
                getChildFragmentManager().beginTransaction()
                        .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out,
                                android.R.anim.fade_in, android.R.anim.fade_out)
                        .hide(homeActivity.fragment_deck_win_rate)
                        .commit();
            }

            String bbsUrl = mMyCard.getBBSUrl();

            // 每次都创建新的实例，避免 arguments 丢失的问题
            homeActivity.fragment_mycard_web = MyCardWebFragment.newInstance(
                    bbsUrl,
                    YGOUtil.s(R.string.mycard_bbs)
            );

            if (mainContentView != null) {
                mainContentView.setVisibility(View.GONE);
            }

            getChildFragmentManager().beginTransaction()
                    .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out,
                            android.R.anim.fade_in, android.R.anim.fade_out)
                    .add(R.id.fragment_web_content, homeActivity.fragment_mycard_web)
                    .commit();

            // 更新按钮状态，将萌卡论坛按钮设置为激活状态
            updateToolBarButtonState(btn_mycard_bbs);
        }
    }

    private void switchDeckWinRateFragment() {
        if (homeActivity == null) {
            return;
        }

        // 判断 DeckWinRateFragment 是否已经显示
        boolean isShowing = homeActivity.fragment_deck_win_rate != null &&
                homeActivity.fragment_deck_win_rate.isAdded() &&
                homeActivity.fragment_deck_win_rate.isVisible();

        if (isShowing) {
            // 如果正在显示，则隐藏它
            getChildFragmentManager().beginTransaction()
                    .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out,
                            android.R.anim.fade_in, android.R.anim.fade_out)
                    .hide(homeActivity.fragment_deck_win_rate)
                    .commit();
            
            if (mainContentView != null) {
                mainContentView.setVisibility(View.VISIBLE);
            }
            
            // 恢复所有按钮状态
            updateToolBarButtonState(null);
        } else {
            // 如果未显示，则打开它
            
            // 如果 MyCardWebFragment 正在显示，先隐藏它
            if (homeActivity.fragment_mycard_web != null &&
                    homeActivity.fragment_mycard_web.isAdded() &&
                    homeActivity.fragment_mycard_web.isVisible()) {
                getChildFragmentManager().beginTransaction()
                        .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out,
                                android.R.anim.fade_in, android.R.anim.fade_out)
                        .hide(homeActivity.fragment_mycard_web)
                        .commit();
                Log.d("MycardFragment", "隐藏 MyCardWebFragment");
            }

            if (homeActivity.fragment_deck_win_rate == null) {
                homeActivity.fragment_deck_win_rate = new DeckWinRateFragment();
            }

            if (mainContentView != null) {
                mainContentView.setVisibility(View.GONE);
            }

            if (!homeActivity.fragment_deck_win_rate.isAdded()) {
                getChildFragmentManager().beginTransaction()
                        .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out,
                                android.R.anim.fade_in, android.R.anim.fade_out)
                        .add(R.id.fragment_deck_win_rate_content, homeActivity.fragment_deck_win_rate)
                        .commit();
            } else {
                getChildFragmentManager().beginTransaction()
                        .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out,
                                android.R.anim.fade_in, android.R.anim.fade_out)
                        .show(homeActivity.fragment_deck_win_rate)
                        .commit();
            }
        }
    }

    /**
     * 更新顶部工具栏按钮状态
     * 当打开某个按钮对应的页面时，该按钮显示为关闭状态，其他按钮恢复为原始状态
     * @param activeButton 当前激活的按钮View，传null表示所有按钮都恢复为原始状态
     */
    private void updateToolBarButtonState(View activeButton) {
        // 定义按钮配置数组：每个元素包含 {按钮ImageView, 文字TextView, 原始图标资源ID, 原始文字资源ID}
        Object[][] buttonConfigs = {
            {btn_mycard_bbs, tv_mycard_bbs, R.drawable.ic_forum, R.string.mycard_bbs}
        };

        // 遍历所有按钮，根据是否为激活按钮来设置状态
        for (Object[] config : buttonConfigs) {
            ImageView button = (ImageView) config[0];
            TextView textView = (TextView) config[1];
            int originalIconResId = (int) config[2];
            int originalTextResId = (int) config[3];

            if (button == null || textView == null) {
                continue;
            }

            if (button == activeButton) {
                // 当前激活的按钮，显示为关闭状态
                button.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
                textView.setText(R.string.search_close);
            } else {
                // 其他按钮，恢复为原始状态
                button.setImageResource(originalIconResId);
                textView.setText(originalTextResId);
            }
        }
    }
    private void initMcNewsView(View view) {
        srl_mcNews = view.findViewById(R.id.srl_mcNews);
        rv_mcNews_list = view.findViewById(R.id.rv_mcNews_list);

        rv_mcNews_list.setLayoutManager(new LinearLayoutManager(requireContext()));

        mcNewsAdapter = new McNewsAdapter(news -> {
            if (news.getNews_url() != null && !news.getNews_url().isEmpty()) {
                openNewsInWebView(news);
            }
        });
        rv_mcNews_list.setAdapter(mcNewsAdapter);

        srl_mcNews.setColorSchemeColors(YGOUtil.c(R.color.colorAccent));
        srl_mcNews.setOnRefreshListener(() -> {
            loadMcNews();
        });

        loadMcNews();
    }

    private void loadMcNews() {
        if (srl_mcNews != null && !srl_mcNews.isRefreshing()) {
            srl_mcNews.setRefreshing(true);
        }

        MyCard.findMyCardNews((myCardNewsList, exception) -> {
            Message message = new Message();
            if (exception == null || TextUtils.isEmpty(exception)) {
                if (myCardNewsList != null && myCardNewsList.size() > 5) {
                    myCardNewsList = myCardNewsList.subList(0, 5);
                }
                message.what = TYPE_MC_NEWS_QUERY_OK;
                message.obj = myCardNewsList;
            } else {
                Log.e("MCFragment", "查询新闻失败: " + exception);
                message.what = TYPE_MC_NEWS_QUERY_EXCEPTION;
                message.obj = exception;
            }
            handler.sendMessage(message);
        });
    }

    private void updateMcNewsList(List<McNews> newsList) {
        if (mcNewsAdapter != null) {
            mcNewsAdapter.setList(newsList != null ? newsList : new ArrayList<>());
        }
    }

    private void openNewsInWebView(McNews news) {
        if (homeActivity == null) {
            return;
        }

        boolean isShowing = homeActivity.fragment_mycard_web != null &&
                homeActivity.fragment_mycard_web.isAdded() &&
                homeActivity.fragment_mycard_web.isVisible();

        if (isShowing) {
            getChildFragmentManager().beginTransaction()
                    .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out,
                            android.R.anim.fade_in, android.R.anim.fade_out)
                    .remove(homeActivity.fragment_mycard_web)
                    .commit();
            homeActivity.fragment_mycard_web = null;

            if (mainContentView != null) {
                mainContentView.setVisibility(View.VISIBLE);
            }

            updateToolBarButtonState(null);
        }

        if (homeActivity.fragment_deck_win_rate != null &&
                homeActivity.fragment_deck_win_rate.isAdded() &&
                homeActivity.fragment_deck_win_rate.isVisible()) {
            getChildFragmentManager().beginTransaction()
                    .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out,
                            android.R.anim.fade_in, android.R.anim.fade_out)
                    .hide(homeActivity.fragment_deck_win_rate)
                    .commit();
        }

        String title = news.getTitle() != null ? news.getTitle() : YGOUtil.s(R.string.McNews);
        homeActivity.fragment_mycard_web = MyCardWebFragment.newInstance(
                news.getNews_url(),
                title
        );

        if (mainContentView != null) {
            mainContentView.setVisibility(View.GONE);
        }

        getChildFragmentManager().beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out,
                        android.R.anim.fade_in, android.R.anim.fade_out)
                .add(R.id.fragment_web_content, homeActivity.fragment_mycard_web)
                .commit();

        updateToolBarButtonState(btn_mycard_bbs);
    }

    private void performLogout() {
        Log.d("MCFragment", "用户点击退出登录按钮");

        serviceManagement.disSerVice();
        serviceManagement.setIsListener(false);

        currentMcDuelInfo = null;
        updateFunRank(null);
        updateMatchRank(null);
        tv_dp_title.setText("");

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

    private void initWaitingRoomView(View view) {
        ll_mycard_waiting_rooms = view.findViewById(R.id.ll_mycard_waiting_rooms);
        srl_waiting = view.findViewById(R.id.srl_waiting);
        rv_waiting_list = view.findViewById(R.id.rv_waiting_list);

        rv_waiting_list.setLayoutManager(new LinearLayoutManager(requireContext()));

        waitingRoomAdapter = new DuelRoomBQAdapter(requireContext(), new ArrayList<DuelRoom>());
        rv_waiting_list.setAdapter(waitingRoomAdapter);

        // 设置下拉刷新监听器
        srl_waiting.setOnRefreshListener(() -> {
            loadWaitingRooms();
        });

        // 初始化时加载等待中的房间
        loadWaitingRooms();
    }

    private void loadWaitingRooms() {
        // TODO: 这里添加获取等待中房间的逻辑
        // 目前暂时模拟一些数据，后续应替换为实际API调用
        if (srl_waiting != null) {
            srl_waiting.setRefreshing(true);
        }

    }

    private void setupTabLayout(View view) {
        tabLayout = view.findViewById(R.id.tablayout_mycard);
        btnCreateRoom = view.findViewById(R.id.btn_create_room);

        // 添加两个标签
        tabLayout.addTab(tabLayout.newTab().setText(R.string.watch_duel));
        tabLayout.addTab(tabLayout.newTab().setText(R.string.quick_join));

        // 设置默认选中的标签
        tabLayout.selectTab(tabLayout.getTabAt(0));
        isSpectateTab = true;

        // 设置标签切换监听器
        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                int position = tab.getPosition();
                if (position == 0) {
                    // 萌卡观战标签 - 显示观战列表
                    isSpectateTab = true;
                    rv_list.setVisibility(View.VISIBLE);
                    ll_mycard_waiting_rooms.setVisibility(View.GONE);
                } else {
                    // 等待中的房间标签 - 隐藏观战列表
                    isSpectateTab = false;
                    rv_list.setVisibility(View.GONE);
                    ll_mycard_waiting_rooms.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });
    }

}
