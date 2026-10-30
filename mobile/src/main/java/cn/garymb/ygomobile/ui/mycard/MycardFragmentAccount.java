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
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.FragmentTransaction;
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
 * MycardFragment 拆分继承链的账户数据层。
 *
 * <p>承载段位展示（娱乐/匹配排名视图与刷新）、匹配开赛、胜率饼图、
 * 退出登录实现以及 MyCard 聊天/房间监听回调等与账户数据相关的功能集群。
 * 拆分仅为控制单类规模，所有方法实现与原文件逐行一致。</p>
 */
public abstract class MycardFragmentAccount extends MycardFragmentBase {
    protected void initRankViews(View view) {
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

    protected void initMatchViews(View view) {
        pb_loading = view.findViewById(R.id.pb_loading);
        iv_refresh = view.findViewById(R.id.iv_refresh);
        tv_dp_title = view.findViewById(R.id.tv_dp_title);
        ll_athletic = view.findViewById(R.id.ll_athletic);
        ll_entertain = view.findViewById(R.id.ll_entertain);

        ll_user_data = view.findViewById(R.id.ll_user_data);
        ll_user_data.setOnClickListener(v -> {
            showUserDuelDetailDialog();
        });

        ll_athletic.setOnClickListener(this);
        ll_entertain.setOnClickListener(this);
        iv_refresh.setOnClickListener(v -> {
            queryDuelInfo();
        });
    }

    protected void initWatchDuelView(View view) {
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
                                    YGOUtil.show(R.string.unknown_room);
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
                                YGOUtil.show(YGOUtil.s(R.string.start_game_error) + e.getMessage());
                            });
                        }
                    }
                }).start();
            }
        });

        onRefresh();
    }

    protected void onRefresh() {
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

    protected void refreshData() {
        if (duelManagement != null) {
            duelManagement.start();
        }
    }

    protected void updateTitle() {
        if (getActivity() != null && duelRoomBQAdapter != null) {
            int size = duelRoomBQAdapter.getData().size();
            String title = YGOUtil.s(R.string.watch_duel) + "（" + size + "）";

            if (getActivity() instanceof AppCompatActivity) {
                AppCompatActivity appCompatActivity = (AppCompatActivity) getActivity();
                if (appCompatActivity.getSupportActionBar() != null) {
                    appCompatActivity.getSupportActionBar().setTitle(title);
                }
            }
        }
    }

    protected void showUserDuelDetailDialog() {
        if (currentMcDuelInfo == null || mMcUser == null) {
            YGOUtil.showTextToast(R.string.loading);
            return;
        }

        String username = mMcUser.getUsername();
        if (TextUtils.isEmpty(username)) {
            YGOUtil.showTextToast(R.string.login_mycard);
            return;
        }

        DialogPlus dialog = new DialogPlus(getContext());
        dialog.setTitle(username);
        dialog.setContentView(R.layout.dialog_mycard_user_duel_detail);

        duelDetailAdapter = new UserDuelDetailAdapter(getContext(), dialog.getContentView());
        duelDetailAdapter.setUsername(username);
        duelDetailAdapter.bindDuelInfo(currentMcDuelInfo, username);

        dialog.show();
    }

    protected void updateFunRank(McDuelInfo mcDuelInfo) {
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

    protected void updateMatchRank(McDuelInfo mcDuelInfo) {
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

    protected void matchAthletic() {
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

    protected void matchEntertain() {
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

    protected void showDuelArena() {
        if (homeActivity.fragment_duel_arena == null) {
            homeActivity.fragment_duel_arena = new MycardDuelArenaFragment();
        }

        boolean isAdded = homeActivity.fragment_duel_arena.isAdded();

        FragmentTransaction transaction = getChildFragmentManager().beginTransaction()
                .setCustomAnimations(R.anim.push_in, R.anim.push_out,
                        R.anim.push_in, R.anim.push_out);

        if (isAdded) {
            transaction.show(homeActivity.fragment_duel_arena);
        } else {
            transaction.add(R.id.fragment_duel_arena_content, homeActivity.fragment_duel_arena);
        }

        transaction.addToBackStack(null).commit();

        View mainContentView = getView().findViewById(R.id.ll_main_ui);
        if (mainContentView != null) {
            mainContentView.setVisibility(View.GONE);
        }
    }

    protected void joinMyCardWindbot(YGOServer server, String windbotName) {
        if (server == null || TextUtils.isEmpty(windbotName)) {
            return;
        }
        server.setPlayerName(mMcUser.getUsername());
        YGOUtil.joinGame(getActivity(), server, "AI#" + windbotName);
    }

    protected void initPieChartViews(View view) {
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

    protected void loadDeckWinRateData() {
        MyCardPieChart.getDayAthleticDeckTypeAnalytics(new MyCardPieChart.OnMyCardPieChartListener() {
            @Override
            public void onMyCardPieChartQuery(MyCardPieChart pieChart, String exception) {
                if (getActivity() == null) {
                    return;
                }

                getActivity().runOnUiThread(() -> {
                    if (exception != null) {
                        YGOUtil.show(YGOUtil.s(R.string.loading_failed) + ": " + exception);
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

    protected void updatePieChart(MyCardPieChart pieChart) {
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

    protected int calculateTotal(MyCardPieChart.First first) {
        if (first == null) return 0;
        return parseInt(first.getWin()) + parseInt(first.getDraw()) + parseInt(first.getLose());
    }

    protected int calculateTotal(MyCardPieChart.Second second) {
        if (second == null) return 0;
        return parseInt(second.getWin()) + parseInt(second.getDraw()) + parseInt(second.getLose());
    }

    protected int parseInt(String value) {
        if (value == null || value.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    protected void performLogout() {
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

}
