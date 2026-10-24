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
 * MycardFragment 拆分继承链的页面导航层。
 *
 * <p>承载返回键处理、点击事件分发、Web 页面（论坛/注册/用户资料/新闻）切换、
 * 工具栏按钮状态更新与萌卡新闻列表等导航类功能集群。
 * 拆分仅为控制单类规模，所有方法实现与原文件逐行一致。</p>
 */
public abstract class MycardFragmentNavigation extends MycardFragmentAccount {
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

        if (homeActivity.fragment_duel_arena != null &&
                homeActivity.fragment_duel_arena.isAdded() &&
                homeActivity.fragment_duel_arena.isVisible()) {
            getChildFragmentManager().beginTransaction()
                    .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out,
                            android.R.anim.fade_in, android.R.anim.fade_out)
                    .hide(homeActivity.fragment_duel_arena)
                    .commit();
            if (ll_main_ui != null) {
                ll_main_ui.setVisibility(View.VISIBLE);
            }
            updateToolBarButtonState(null);
            return true;
        }

        if (homeActivity.fragment_mycard_web != null &&
                homeActivity.fragment_mycard_web.isAdded() &&
                homeActivity.fragment_mycard_web.isVisible()) {
            if (homeActivity.fragment_mycard_web.onBackPressed()) {
                return true;
            }

            removeMycardWebFragment();

            return true;
        }
        
        return false;
    }

    /**
     * 移除 MyCardWebFragment 并恢复主界面
     */
    protected void removeMycardWebFragment() {
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
                showDuelArena();
                //switchDuelArenaWithWebView();
                break;
        }
    }

    /**
     * 打开用户资料页面
     */
    protected void openUserProfile() {
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
            if (homeActivity.fragment_duel_arena != null &&
                    homeActivity.fragment_duel_arena.isAdded() &&
                    homeActivity.fragment_duel_arena.isVisible()) {
                getChildFragmentManager().beginTransaction()
                        .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out,
                                android.R.anim.fade_in, android.R.anim.fade_out)
                        .hide(homeActivity.fragment_duel_arena)
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

    protected void switchBBSWithWebView() {
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
            if (homeActivity.fragment_duel_arena != null &&
                    homeActivity.fragment_duel_arena.isAdded() &&
                    homeActivity.fragment_duel_arena.isVisible()) {
                getChildFragmentManager().beginTransaction()
                        .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out,
                                android.R.anim.fade_in, android.R.anim.fade_out)
                        .hide(homeActivity.fragment_duel_arena)
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

    protected void switchRegisterWithWebView() {
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


    protected void switchDuelArenaWithWebView() {
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

    /**
     * 更新顶部工具栏按钮状态
     * 当打开某个按钮对应的页面时，该按钮显示为关闭状态，其他按钮恢复为原始状态
     * @param activeButton 当前激活的按钮View，传null表示所有按钮都恢复为原始状态
     */
    protected void updateToolBarButtonState(View activeButton) {
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

    /**
     * 供子Fragment调用，更新工具栏按钮状态
     * @param activeButton 当前激活的按钮View，传null表示所有按钮都恢复为原始状态
     */
    public void updateToolBarButtonStatePublic(View activeButton) {
        updateToolBarButtonState(activeButton);
    }

    protected void initMcNewsView(View view) {
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

    protected void loadMcNews() {
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

    protected void updateMcNewsList(List<McNews> newsList) {
        if (mcNewsAdapter != null) {
            mcNewsAdapter.setList(newsList != null ? newsList : new ArrayList<>());
        }
    }

    protected void openNewsInWebView(McNews news) {
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

        if (homeActivity.fragment_duel_arena != null &&
                homeActivity.fragment_duel_arena.isAdded() &&
                homeActivity.fragment_duel_arena.isVisible()) {
            getChildFragmentManager().beginTransaction()
                    .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out,
                            android.R.anim.fade_in, android.R.anim.fade_out)
                    .hide(homeActivity.fragment_duel_arena)
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

}
