package cn.garymb.ygomobile.ui.mycard;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
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
import com.feihua.dialogutils.util.DialogUtils;
import com.google.android.material.tabs.TabLayout;
import com.king.view.circleprogressview.CircleProgressView;
import com.ourygo.lib.duelassistant.util.Util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.YGOMobileActivity;
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
import cn.garymb.ygomobile.ui.mycard.watchDuel.WaitingDuelManagement;
import cn.garymb.ygomobile.ui.widget.DeckPieChartView;
import cn.garymb.ygomobile.ui.mycard.base.OnDuelRoomListener;
import cn.garymb.ygomobile.ui.mycard.base.OnJoinChatListener;
import cn.garymb.ygomobile.ui.mycard.base.OnMcMatchListener;
import cn.garymb.ygomobile.ui.mycard.bean.DuelRoom;
import cn.garymb.ygomobile.ui.mycard.bean.McDuelInfo;
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

    private HomeActivity homeActivity;
    private StringManager mStringManager;
    private LinearLayout ll_athletic, ll_entertain, ll_dialog_login, ll_main_ui, ll_mycard_waiting_rooms;
    private EditText et_username, et_password;
    private TextView matchTvRank, matchTvWin, matchTvLose, matchTvDraw, matchTvAll, funTvRank, funTvWin, funTvLose, funTvDraw, funTvAll, tv_message, tv_dp_title, mNameView, mStatusView, tv_account_warning, tv_pwd_warning, tv_mycard_bbs;
    private Button btn_login, btn_register;
    private ProgressBar progressBar_login;
    private ImageView mHeadView, img_logout, iv_refresh, btn_mycard_bbs;
    private MyCard mMyCard;
    private McUser mMcUser;
    public RelativeLayout rl_chat;
    private ProgressBar pb_chat_loading, pb_loading;
    private ServiceManagement serviceManagement;
    private ChatMessage currentMessage;
    private Switch swToggleCardImage;

    private CircleProgressView funCpvRank, matchCpvRank;
    private McDuelInfo currentMcDuelInfo;
    private SwipeRefreshLayout srl_update;
    private RecyclerView rv_list;
    private WatchDuelManagement duelManagement;
    private WaitingDuelManagement waitingDuelManagement;
    private DuelRoomBQAdapter duelRoomBQAdapter;
    private DeckPieChartView pieChartView;
    private TextView tvEmpty;
    private SwipeRefreshLayout srl_mcNews;
    private RecyclerView rv_mcNews_list;
    private McNewsAdapter mcNewsAdapter;
    private List<McNews> mcNewsList = new ArrayList<>();
    private TabLayout tabLayout;
    private LinearLayout ll_create_room;
    private boolean isSpectateTab = true;
    private RecyclerView rv_waiting_list;
    private SwipeRefreshLayout srl_waiting;
    private DuelRoomBQAdapter waitingRoomAdapter;
    private List<DuelRoom> waitingRoomList = new ArrayList<>();


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

    private void checkLoginState() {
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
        tv_dp_title = view.findViewById(R.id.tv_dp_title);
        ll_athletic = view.findViewById(R.id.ll_athletic);
        ll_entertain = view.findViewById(R.id.ll_entertain);

        ll_athletic.setOnClickListener(this);
        ll_entertain.setOnClickListener(this);
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
                // 游戏运行中则直接打开游戏，不加入房间

                if (YGOStarter.isGameRunning(getActivity())) {
                    YGOStarter.startGame(getActivity(), null);
                    return;
                }

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
    private void initPieChartViews(View view) {
        pieChartView = view.findViewById(R.id.pie_chart_view);
        tvEmpty = view.findViewById(R.id.tv_empty);
        swToggleCardImage = view.findViewById(R.id.sw_toggle_card_image);

        pieChartView.setOnClickListener(this);

        // 设置 Switch 开关的监听器，用于切换卡图显示模式
        swToggleCardImage.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // isChecked: true-显示卡图模式，false-纯色模式
            pieChartView.setShowCardImages(isChecked);
        });

        // 默认设置为开启状态（显示卡图）
        swToggleCardImage.setChecked(true);

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
        queryDuelInfo();
        super.onResume();
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
            if (ll_main_ui != null) {
                ll_main_ui.setVisibility(View.VISIBLE);
            }
            // 恢复所有按钮状态
            updateToolBarButtonState(null);
            return true;
        }
        
        if (homeActivity.fragment_mycard_web != null && 
            homeActivity.fragment_mycard_web.isAdded() && 
            homeActivity.fragment_mycard_web.isVisible()) {
            // 优先让WebView返回上一页
            if (homeActivity.fragment_mycard_web.onBackPressed()) {
                return true;
            }
            // WebView没有上一页可返回，如果是论坛页面则弹出确认对话框
            Bundle webArgs = homeActivity.fragment_mycard_web.getArguments();
            String webUrl = webArgs != null ? webArgs.getString("url") : null;

            removeMycardWebFragment();

            return true;
        }
        
        return false;
    }

    /**
     * 移除 MyCardWebFragment 并恢复主界面
     */
    private void removeMycardWebFragment() {
        if (homeActivity.fragment_mycard_web == null) {
            return;
        }
        getChildFragmentManager().beginTransaction()
                .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out,
                        android.R.anim.fade_in, android.R.anim.fade_out)
                .remove(homeActivity.fragment_mycard_web)
                .commit();
        homeActivity.fragment_mycard_web = null;
        if (ll_main_ui != null) {
            ll_main_ui.setVisibility(View.VISIBLE);
        }
        // 恢复所有按钮状态
        updateToolBarButtonState(null);
    }

    /**
     * Called when a view has been clicked.
     *
     * @param v The view that was clicked.
     */
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.img_head:
                openUserProfile();
                break;
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
            case R.id.btn_mycard_bbs:
                switchBBSWithWebView();
                break;
            case R.id.btn_login:
                attemptLogin();
                break;
            case R.id.btn_register:
                switchRegisterWithWebView();
                break;
            case R.id.pie_chart_view:
                switchDuelArenaWithWebView();
                break;
        }
    }

    /**
     * 打开用户资料页面
     */
    private void openUserProfile() {
        if (homeActivity == null) {
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
            
            if (ll_main_ui != null) {
                ll_main_ui.setVisibility(View.VISIBLE);
            }
            
            // 恢复所有按钮状态
            updateToolBarButtonState(null);
        } else {
            // 如果未显示，则打开用户资料页面
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

            // 创建并显示用户资料页面的 Web Fragment
            homeActivity.fragment_mycard_web = MyCardWebFragment.newInstance(
                    MyCard.URL_MC_USER_PROFILE,
                    "用户资料"
            );

            if (ll_main_ui != null) {
                ll_main_ui.setVisibility(View.GONE);
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

    private void switchBBSWithWebView() {
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
            
            if (ll_main_ui != null) {
                ll_main_ui.setVisibility(View.VISIBLE);
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

            if (ll_main_ui != null) {
                ll_main_ui.setVisibility(View.GONE);
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

    private void switchRegisterWithWebView() {
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

            if (ll_dialog_login != null) {
                ll_dialog_login.setVisibility(View.VISIBLE);
            }
        } else {
            // 如果未显示，则打开注册页面
            // 创建并显示注册页面的 Web Fragment
            homeActivity.fragment_mycard_web = MyCardWebFragment.newInstance(
                    MyCard.URL_MC_SIGN_UP,
                    YGOUtil.s(R.string.register)
            );

            getChildFragmentManager().beginTransaction()
                    .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out,
                            android.R.anim.fade_in, android.R.anim.fade_out)
                    .add(R.id.fragment_web_content, homeActivity.fragment_mycard_web)
                    .commit();

            // 更新按钮状态，将萌卡论坛按钮设置为激活（关闭）状态
            updateToolBarButtonState(btn_mycard_bbs);

        }
    }


    private void switchDuelArenaWithWebView() {
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

            if (ll_dialog_login != null) {
                ll_dialog_login.setVisibility(View.VISIBLE);
            }
        } else {
            // 如果未显示，则打开注册页面
            // 创建并显示注册页面的 Web Fragment
            homeActivity.fragment_mycard_web = MyCardWebFragment.newInstance(
                    MyCard.getArenaUrl(),
                    YGOUtil.s(R.string.register)
            );

            getChildFragmentManager().beginTransaction()
                    .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out,
                            android.R.anim.fade_in, android.R.anim.fade_out)
                    .add(R.id.fragment_web_content, homeActivity.fragment_mycard_web)
                    .commit();

            // 更新按钮状态，将萌卡论坛按钮设置为激活（关闭）状态
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
            
            if (ll_main_ui != null) {
                ll_main_ui.setVisibility(View.VISIBLE);
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

            if (ll_main_ui != null) {
                ll_main_ui.setVisibility(View.GONE);
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

            if (ll_main_ui != null) {
                ll_main_ui.setVisibility(View.VISIBLE);
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

        if (ll_main_ui != null) {
            ll_main_ui.setVisibility(View.GONE);
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
        //ll_main_ui.setVisibility(View.GONE);

        //et_username.setText("");
        //et_password.setText("");

        // 设置默认用户名和头像
        mNameView.setText(R.string.login_mycard);
        GlideCompat.with(getActivity()).load(R.drawable.avatar).into(mHeadView);

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
        waitingRoomAdapter.setShowRoomName(true);
        rv_waiting_list.setAdapter(waitingRoomAdapter);

        waitingDuelManagement = WaitingDuelManagement.getInstance();
        waitingDuelManagement.addListener(new OnDuelRoomListener() {
            @Override
            public void onInit(List<DuelRoom> duelRoomList) {
                if (srl_waiting != null) {
                    srl_waiting.setRefreshing(false);
                }
                waitingRoomAdapter.getData().clear();
                if (duelRoomList != null && !duelRoomList.isEmpty()) {
                    waitingRoomAdapter.addData(duelRoomList);
                }
            }

            @Override
            public void onCreate(List<DuelRoom> duelRoomList) {
                waitingRoomAdapter.addData(duelRoomList);
            }

            @Override
            public void onUpdate(List<DuelRoom> duelRoomList) {
                waitingRoomAdapter.notifyDataSetChanged();
            }

            @Override
            public void onDelete(List<DuelRoom> duelRoomList) {
                waitingRoomAdapter.remove(duelRoomList);
            }

            @Override
            public boolean isListenerEffective() {
                return getActivity() != null && !getActivity().isFinishing();
            }
        });

        waitingRoomAdapter.setOnItemClickListener(new OnItemClickListener() {
            @Override
            public void onItemClick(@NonNull BaseQuickAdapter adapter, @NonNull View view, int position) {
                // 游戏运行中则直接打开游戏，不加入房间
                if (YGOStarter.isGameRunning(getActivity())) {
                    YGOStarter.startGame(getActivity(), null);
                    return;
                }

                DuelRoom duelRoom = waitingRoomAdapter.getItem(position);

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

                        Log.e("WaitingDuel", "u16SecretStr: " + u16SecretStr);

                        String password = YGOUtil.getWatchDuelPassword(duelRoom.getId(), mMcUser.getExternal_id(), u16SecretStr);
                        Log.e("WaitingDuel password", "password: " + password);

                        ServerInfo serverInfo;
                        boolean isArenaTypeValid = true;

                        if (duelRoom.getServer() != null) {
                            serverInfo = duelRoom.getServer();
                        } else {
                            serverInfo = new ServerInfo();
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
                        }

                        final boolean finalValid = isArenaTypeValid;
                        final ServerInfo finalServerInfo = serverInfo;
                        Activity activity = getActivity();

                        if (activity != null) {
                            activity.runOnUiThread(() -> {
                                if (!finalValid) {
                                    YGOUtil.show("未知房间，请更新软件后进入");
                                    return;
                                }

                                finalServerInfo.setPlayerName(mMcUser.getUsername());
                                YGOUtil.joinGame(activity, finalServerInfo, password);
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

        srl_waiting.setOnRefreshListener(() -> {
            loadWaitingRooms();
        });

        loadWaitingRooms();
    }

    private void loadWaitingRooms() {
        if (srl_waiting != null) {
            srl_waiting.setRefreshing(true);
        }

        if (waitingDuelManagement != null) {
            waitingDuelManagement.start();
        }
    }

    private void showCreateRoomDialog() {
        if (!isUserLoggedIn()) {
            YGOUtil.showTextToast(R.string.login_mycard);
            return;
        }

        ll_create_room.setEnabled(false);
        new Thread(() -> {
            List<YGOServer> servers;
            try {
                servers = MyCard.getCustomServers();
            } catch (Exception e) {
                Log.e("MyCard", "加载自定义服务器失败: " + e);
                servers = new ArrayList<>();
                servers.add(MyCard.getDefaultCustomServer(null));
            }

            Activity activity = getActivity();
            if (activity == null) {
                return;
            }
            List<YGOServer> finalServers = servers;
            activity.runOnUiThread(() -> {
                ll_create_room.setEnabled(true);
                showCreateRoomDialog(finalServers);
            });
        }).start();
    }

    private void showCreateRoomDialog(List<YGOServer> servers) {
        DialogPlus dialog = new DialogPlus(requireContext());
        dialog.setContentView(R.layout.dialog_custom_mode_select);
        dialog.setTitle(R.string.create_custom_room);

        TextView serverLabel = dialog.findViewById(R.id.tv_create_room_server);
        Spinner serverSpinner = dialog.findViewById(R.id.spinner_create_room_server);
        EditText titleEdit = dialog.findViewById(R.id.et_custom_room_title);
        TextView titleCount = dialog.findViewById(R.id.tv_custom_room_title_count);
        Spinner ruleSpinner = dialog.findViewById(R.id.spinner_custom_room_rule);
        Spinner modeSpinner = dialog.findViewById(R.id.spinner_custom_room_mode);
        Spinner duelRuleSpinner = dialog.findViewById(R.id.spinner_custom_room_duel_rule);
        EditText startLpEdit = dialog.findViewById(R.id.et_custom_room_start_lp);
        EditText startHandEdit = dialog.findViewById(R.id.et_custom_room_start_hand);
        EditText drawCountEdit = dialog.findViewById(R.id.et_custom_room_draw_count);
        CheckBox privateBox = dialog.findViewById(R.id.cb_custom_room_private);
        CheckBox noCheckDeckBox = dialog.findViewById(R.id.cb_custom_room_no_check_deck);
        CheckBox noShuffleDeckBox = dialog.findViewById(R.id.cb_custom_room_no_shuffle_deck);
        CheckBox autoDeathBox = dialog.findViewById(R.id.cb_custom_room_auto_death);

        titleEdit.setFilters(new InputFilter[]{new InputFilter.LengthFilter(12)});
        titleEdit.setText(mMcUser.getUsername() + "的房间");
        titleCount.setText(titleEdit.getText().length() + " / 12");
        titleEdit.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                titleCount.setText(s.length() + " / 12");
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        setSpinnerValues(serverSpinner, getServerNames(servers));
        setSpinnerValues(ruleSpinner, new String[]{
                mStringManager.getSystemString(1481, ""),
                mStringManager.getSystemString(1482, ""),
                mStringManager.getSystemString(1483, ""),
                mStringManager.getSystemString(1484, ""),
                mStringManager.getSystemString(1485, ""),
                mStringManager.getSystemString(1486, ""),
        });
        setSpinnerValues(modeSpinner, new String[]{
                mStringManager.getSystemString(1244, ""),
                mStringManager.getSystemString(1245, ""),
                mStringManager.getSystemString(1246, "")
        });
        setSpinnerValues(duelRuleSpinner, new String[]{
                mStringManager.getSystemString(1260, ""),
                mStringManager.getSystemString(1261, ""),
                mStringManager.getSystemString(1262, ""),
                mStringManager.getSystemString(1263, ""),
                mStringManager.getSystemString(1264, "")
        });
        modeSpinner.setSelection(DuelRoom.MODE_MATCH);
        duelRuleSpinner.setSelection(4);

        int defaultServerIndex = getDefaultServerIndex(servers);
        serverSpinner.setSelection(defaultServerIndex);
        serverLabel.setText(YGOUtil.s(R.string.server_area) + " " + servers.get(defaultServerIndex).getName());
        serverSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                serverLabel.setText(YGOUtil.s(R.string.server_area) + " " + servers.get(position).getName());
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        modeSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                startLpEdit.setText(position == DuelRoom.MODE_TAG ? "16000" : "8000");
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        privateBox.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked) {
                titleEdit.setText(String.valueOf(mMcUser.getExternal_id() ^ 0x54321));
                titleEdit.setEnabled(false);
                titleCount.setVisibility(View.GONE);
            } else {
                titleEdit.setEnabled(true);
                titleEdit.setText(mMcUser.getUsername() + "的房间");
                titleCount.setVisibility(View.VISIBLE);
            }
        });

        dialog.setLeftButtonListener((dlg, s) -> {
            boolean privateRoom = privateBox.isChecked();
            String title = titleEdit.getText().toString().trim();
            if (!privateRoom && TextUtils.isEmpty(title)) {
                YGOUtil.show("请输入房间标题");
                return;
            }

            DuelRoom.OptionsBean options = new DuelRoom.OptionsBean();
            options.setRule(ruleSpinner.getSelectedItemPosition());
            options.setMode(modeSpinner.getSelectedItemPosition());
            options.setDuel_rule(duelRuleSpinner.getSelectedItemPosition() + 1);
            options.setStart_lp(readInt(startLpEdit, 1, 65535, modeSpinner.getSelectedItemPosition() == DuelRoom.MODE_TAG ? 16000 : 8000));
            options.setStart_hand(readInt(startHandEdit, 0, 16, 5));
            options.setDraw_count(readInt(drawCountEdit, 0, 16, 1));
            options.setNo_check_deck(noCheckDeckBox.isChecked());
            options.setNo_shuffle_deck(noShuffleDeckBox.isChecked());
            options.setAuto_death(autoDeathBox.isChecked());

            YGOServer server = servers.get(serverSpinner.getSelectedItemPosition());
            createCustomRoom(dialog, server, options, title, privateRoom);
        });

        dialog.show();
    }

    private void createCustomRoom(DialogPlus dialog, YGOServer server, DuelRoom.OptionsBean options, String title, boolean privateRoom) {
        new Thread(() -> {
            try {
                String token = SharedPreferenceUtil.getServerToken();
                if (TextUtils.isEmpty(token)) {
                    throw new IOException("token not found");
                }

                int u16Secret = MyCard.getUserU16Secret(token);
                String hostPassword = String.valueOf(mMcUser.getExternal_id() ^ 0x54321);
                String password = MyCard.createCustomRoomPassword(options, title, privateRoom, hostPassword, u16Secret);
                Activity activity = getActivity();
                if (activity == null) {
                    return;
                }

                activity.runOnUiThread(() -> {
                    dialog.dismiss();
                    server.setPlayerName(mMcUser.getUsername());
                    YGOUtil.joinGame(activity, server, password);
                    if (privateRoom) {
                        YGOUtil.show("房间密码是 " + hostPassword);
                    }
                });
            } catch (Exception e) {
                Log.e("MyCard", "创建自定义房间失败: " + e);
                Activity activity = getActivity();
                if (activity != null) {
                    activity.runOnUiThread(() -> {
                        YGOUtil.show("创建房间失败: " + e.getMessage());
                    });
                }
            }
        }).start();
    }

    private void setSpinnerValues(Spinner spinner, String[] values) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }

    private String[] getServerNames(List<YGOServer> servers) {
        String[] names = new String[servers.size()];
        for (int i = 0; i < servers.size(); i++) {
            names[i] = servers.get(i).getName();
        }
        return names;
    }

    private int getDefaultServerIndex(List<YGOServer> servers) {
        YGOServer defaultServer = MyCard.getDefaultCustomServer(servers);
        for (int i = 0; i < servers.size(); i++) {
            YGOServer server = servers.get(i);
            if (!TextUtils.isEmpty(server.getId()) && server.getId().equals(defaultServer.getId())) {
                return i;
            }
        }
        return 0;
    }

    private int readInt(EditText editText, int min, int max, int defaultValue) {
        try {
            int value = Integer.parseInt(editText.getText().toString().trim());
            return Math.max(min, Math.min(max, value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private void setupTabLayout(View view) {
        tabLayout = view.findViewById(R.id.tablayout_mycard);
        ll_create_room = view.findViewById(R.id.ll_create_room);
        ll_create_room.setOnClickListener(v -> showCreateRoomDialog());

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
