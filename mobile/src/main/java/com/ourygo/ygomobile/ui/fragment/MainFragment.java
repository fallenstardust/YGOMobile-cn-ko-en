package com.ourygo.ygomobile.ui.fragment;

import java.util.ArrayList;
import java.util.Calendar;

import org.litepal.LitePal;

import com.feihua.dialogutils.util.DialogUtils;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.ourygo.lib.duelassistant.util.YGODAUtil;
import com.ourygo.ygomobile.OYApplication;
import com.ourygo.ygomobile.adapter.YGOServerBQAdapter;
import com.ourygo.ygomobile.bean.McNews;
import com.ourygo.ygomobile.bean.YGOServer;
import com.ourygo.ygomobile.ui.activity.DeckManagementActivity;
import com.ourygo.ygomobile.ui.activity.NewServerActivity;
import com.ourygo.ygomobile.util.AppInfoManagement;
import com.ourygo.ygomobile.util.IntentUtil;
import com.ourygo.ygomobile.util.LogUtil;
import com.ourygo.ygomobile.util.MyCardUtil;
import com.ourygo.ygomobile.util.OYDialogUtil;
import com.ourygo.ygomobile.util.OYUtil;
import com.ourygo.ygomobile.util.Record;
import com.ourygo.ygomobile.util.SharedPreferenceUtil;
import com.ourygo.ygomobile.util.YGOUtil;
import com.stx.xhb.androidx.XBanner;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.transition.Fade;
import androidx.transition.TransitionManager;

import cn.garymb.ygomobile.base.BaseFragemnt;
import cn.garymb.ygomobile.bean.Deck;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.home.ResCheckTask;
import cn.garymb.ygomobile.ui.mycard.mcchat.util.ImageUtil;

public class MainFragment extends BaseFragemnt implements View.OnClickListener {

    private static final int TYPE_BANNER_QUERY_OK = 0;
    private static final int TYPE_BANNER_QUERY_EXCEPTION = 1;
    private static final int TYPE_RES_LOADING_OK = 2;

    private static final int REQUEST_NEW_SERVER = 0;
    private static final String TAG = "TIME-Mainfragment";
    private static final String ARG_MC_NEWS_LIST = "mcNewsList";

    private XBanner xb_banner;
    private ArrayList<McNews> mcNewsList;
    //    private OYTabLayout tl_game_option,tl_replay;
//    private ViewPager vp_game;
    private ImageView iv_add_setting, iv_list_mode;
    //    private List<FragmentData> fragmentDataList;
//    private RecyclerView rv_replay;
    private CardView cv_ai, cv_deck, cv_replay;
    private RecyclerView rv_service_list;
    private FloatingActionButton fab_join;
    private DialogUtils du;
    private YGOServerBQAdapter ygoServerAdp, ygoServerListAdp, ygoServerGridAdp;
    private CardView cv_join_room, cv_loacl_duel, cv_banner;
    private ProgressBar pb_res_loading;
    private TextView tv_banner_loading;
    private boolean isMcNewsLoadException = false;
    private boolean isFirstLoadBanner=true;

    @SuppressLint("HandlerLeak")
    Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case TYPE_BANNER_QUERY_OK:
                    tv_banner_loading.setVisibility(View.GONE);
                    TransitionManager.beginDelayedTransition(xb_banner,new Fade().setDuration(1000));
                    xb_banner.setBannerData(R.layout.banner_main_item, mcNewsList);
                    if (isFirstLoadBanner) {
                        isFirstLoadBanner=false;
//                        xb_banner.setAlpha(0);
//                        ViewPropertyAnimator viewPropertyAnimator=
//                                xb_banner.animate().alpha(1).setDuration(1000).withLayer();
//                        OYUtil.onStartBefore(viewPropertyAnimator,xb_banner);
                    }
                    break;
                case TYPE_BANNER_QUERY_EXCEPTION:
                    if (mcNewsList==null||mcNewsList.size()==0) {
                        tv_banner_loading.setText("加载失败，点击重试");
                        isMcNewsLoadException = true;
                    }
//                    OYUtil.snackExceptionToast(getActivity(), xb_banner, getString(R.string.query_exception), msg.obj.toString());
                    break;
                case TYPE_RES_LOADING_OK:
                    pb_res_loading.setVisibility(View.GONE);
                    OYApplication.setIsInitRes(true);
                    break;
            }

        }
    };
    private ImageView iv_card_bag;
    private ResCheckTask mResCheckTask;


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        LogUtil.time(TAG, "1");
        super.onCreateView(inflater, container, savedInstanceState);
        View layoutView;
        if (isHorizontal)
            layoutView = inflater.inflate(R.layout.main_horizontal_fragment, container, false);
        else
            layoutView = inflater.inflate(R.layout.main_fragment, container, false);

        LogUtil.time(TAG, "2");
        initView(layoutView, savedInstanceState);
        LogUtil.time(TAG, "3");
        initServiceList();
        LogUtil.time(TAG, "4");
        LogUtil.printSumTime(TAG);

        return layoutView;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        if (mcNewsList != null)
            outState.putSerializable(ARG_MC_NEWS_LIST, mcNewsList);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.cv_ai:
                IntentUtil.startYGOEndgame(getActivity());
                break;
            case R.id.cv_deck:
                if (SharedPreferenceUtil.isToastNewCardBag()) {
                    OYDialogUtil.dialogNewDeck(getActivity());
                    SharedPreferenceUtil.setToastNewCardBag(false);
                    break;
                }

                switch (SharedPreferenceUtil.getDeckEditType()) {
                    case SharedPreferenceUtil.DECK_EDIT_TYPE_LOCAL:
                        IntentUtil.startYGODeck(getActivity());
                        break;
                    case SharedPreferenceUtil.DECK_EDIT_TYPE_DECK_MANAGEMENT:
                        startActivity(new Intent(getActivity(), DeckManagementActivity.class));
                        break;
                    case SharedPreferenceUtil.DECK_EDIT_TYPE_OURYGO_EZ:
                        if (OYUtil.isApp(Record.PACKAGE_NAME_EZ))
                            startActivity(IntentUtil.getAppIntent(getActivity(), Record.PACKAGE_NAME_EZ));
                        else
                            startActivity(IntentUtil.getWebIntent(getActivity(), "http://ez.ourygo.top/"));
                        break;
                }
                break;
            case R.id.cv_loacl_duel:
//                IntentUtil.startYGOGame(getActivity());
                break;
            case R.id.cv_join_room:
            case R.id.fab_join:
                OYDialogUtil.dialogJoinRoom(getActivity(), null);
                break;
            case R.id.cv_replay:
                IntentUtil.startYGOReplay(getActivity(), "");
                break;
            case R.id.tv_banner_loading:
                if (isMcNewsLoadException)
                    findMcNews();
                break;
        }
    }


    private void initView(View v, Bundle saveBundle) {
        xb_banner = v.findViewById(R.id.xb_banner);
        iv_add_setting = v.findViewById(R.id.iv_add_setting);
        iv_list_mode = v.findViewById(R.id.iv_list_mode);
//        tl_game_option=v.findViewById(R.id.tl_game_option);
//        vp_game=v.findViewById(R.id.vp_game);
//        tl_replay=v.findViewById(R.id.tl_replay);
//        rv_replay=v.findViewById(R.id.rv_replay);
        cv_ai = v.findViewById(R.id.cv_ai);
        cv_deck = v.findViewById(R.id.cv_deck);
        cv_replay = v.findViewById(R.id.cv_replay);
        cv_loacl_duel = v.findViewById(R.id.cv_loacl_duel);
        rv_service_list = v.findViewById(R.id.rv_service_list);

        pb_res_loading = v.findViewById(R.id.pb_res_loading);
        cv_banner = v.findViewById(R.id.cv_banner);
        tv_banner_loading = v.findViewById(R.id.tv_banner_loading);
        iv_card_bag = v.findViewById(R.id.iv_card_bag);

        if (!isHorizontal)
            fab_join = v.findViewById(R.id.fab_join);
        else
            cv_join_room = v.findViewById(R.id.cv_join_room);

        du = DialogUtils.getInstance(getActivity());

        int serverListType = SharedPreferenceUtil.getServerListType();
        ygoServerListAdp = new YGOServerBQAdapter(new ArrayList<>(), isHorizontal, SharedPreferenceUtil.SERVER_LIST_TYPE_LIST);
        ygoServerGridAdp = new YGOServerBQAdapter(new ArrayList<>(), isHorizontal, SharedPreferenceUtil.SERVER_LIST_TYPE_GRID);
        ygoServerListAdp.setAnimationEnable(true);
        ygoServerGridAdp.setAnimationEnable(true);

        setServerListType(serverListType);
        LogUtil.time(TAG,"2.1");
        long time = SharedPreferenceUtil.getVersionUpdateTime();
        if (AppInfoManagement.INSTANCE.isNewVersion()) {
            SharedPreferenceUtil.setVersionUpdateTime(System.currentTimeMillis());
            SharedPreferenceUtil.setToastNewCardBag(true);
            iv_card_bag.setImageResource(R.drawable.ic_new_card_bag);
            ViewGroup.LayoutParams layoutParams1=iv_card_bag.getLayoutParams();
            layoutParams1.width=OYUtil.dp2px(56);
            layoutParams1.height=OYUtil.dp2px(56);
            iv_card_bag.setLayoutParams(layoutParams1);
            iv_card_bag.setAlpha(0f);
            ViewPropertyAnimator viewPropertyAnimator= iv_card_bag
                    .animate().alpha(1f).setDuration(1000).withLayer();
            OYUtil.onStartBefore(viewPropertyAnimator,iv_card_bag);
        } else {
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(time);
            calendar.add(Calendar.DAY_OF_MONTH, 1);
            if (System.currentTimeMillis() < calendar.getTimeInMillis()) {
                iv_card_bag.setImageResource(R.drawable.ic_new_card_bag);
                ViewGroup.LayoutParams layoutParams=iv_card_bag.getLayoutParams();
                layoutParams.width=OYUtil.dp2px(56);
                layoutParams.height=OYUtil.dp2px(56);
                iv_card_bag.setLayoutParams(layoutParams);
                iv_card_bag.setAlpha(0f);
                ViewPropertyAnimator viewPropertyAnimator= iv_card_bag
                        .animate().alpha(1f).setDuration(1000).withLayer();
                OYUtil.onStartBefore(viewPropertyAnimator,iv_card_bag);
            } else {
                SharedPreferenceUtil.setToastNewCardBag(false);
            }
        }

        LogUtil.time(TAG,"2.2");
        iv_add_setting.setOnClickListener(v1 -> startActivityForResult(new Intent(getActivity(), NewServerActivity.class), REQUEST_NEW_SERVER));
        iv_list_mode.setOnClickListener(view -> {
            if (SharedPreferenceUtil.getServerListType() == SharedPreferenceUtil.SERVER_LIST_TYPE_LIST) {
                SharedPreferenceUtil.setServerListType(SharedPreferenceUtil.SERVER_LIST_TYPE_GRID);
                setServerListType(SharedPreferenceUtil.SERVER_LIST_TYPE_GRID);

            } else {
                SharedPreferenceUtil.setServerListType(SharedPreferenceUtil.SERVER_LIST_TYPE_LIST);
                setServerListType(SharedPreferenceUtil.SERVER_LIST_TYPE_LIST);
            }
        });

        cv_deck.setOnClickListener(this);
        cv_replay.setOnClickListener(this);
        cv_loacl_duel.setOnClickListener(this);
        cv_ai.setOnClickListener(this);

        tv_banner_loading.setOnClickListener(this);

        if (!isHorizontal)
            fab_join.setOnClickListener(this);
        else
            cv_join_room.setOnClickListener(this);

//        cv_banner.post(() -> {
////            xb_banner.setViewPagerMargin(OYUtil.px2dp(300));
////                Log.e("MainFragment","宽"+xb_banner.getWidth());
////                Log.e("MainFragment","算数"+OYUtil.px2dp(xb_banner.getHeight())*2);
////            xb_banner.setClipChildrenLeftRightMargin((OYUtil.px2dp(xb_banner.getWidth())-OYUtil.px2dp(xb_banner.getHeight()-OYUtil.px2dp(10))*2)/2);
//
////                xb_banner.setClipChildrenLeftRightMargin(75);
//            ViewGroup.LayoutParams layoutParams = cv_banner.getLayoutParams();
////            if (isHorizontal)
////                layoutParams.width = (cv_banner.getWidth() - ScaleUtils.dp2px(80)) / 2;
////            else
//            layoutParams.width = cv_banner.getWidth();
//            layoutParams.height = layoutParams.width / 3;
//            cv_banner.setLayoutParams(layoutParams);
//
////                xb_banner.setClipChildrenLeftRightMargin(50);
////                xb_banner.setma
//
//        });
        xb_banner.setOnItemClickListener((banner, model, view, position) -> startActivity(IntentUtil.getWebIntent(getActivity(), mcNewsList.get(position).getNews_url())));
        xb_banner.loadImage((banner, model, view, position) -> {
            TextView tv_time, tv_title, tv_type;
            ImageView iv_image;

            tv_time = view.findViewById(R.id.tv_time);
            tv_title = view.findViewById(R.id.tv_title);
            tv_type = view.findViewById(R.id.tv_type);
            iv_image = view.findViewById(R.id.iv_image);

            McNews mcNews = mcNewsList.get(position);
            ImageUtil.setImageAndBackground(MainFragment.this.getContext(), mcNews.getImage_url(), iv_image);
            tv_time.setText(mcNews.getCreate_time());
            String title = mcNews.getTitle();
            String type = null;
            int start, end;
            start = title.indexOf("【");
            if (start != -1) {
                end = title.indexOf("】", start);
                if (end != -1) {
                    type = title.substring(start + 1, end);
                    title = title.substring(end + 1);
                }
            }
            tv_title.setText(title);
            if (TextUtils.isEmpty(type)) {
                tv_type.setVisibility(View.GONE);
            } else {
                tv_type.setVisibility(View.VISIBLE);
                tv_type.setBackground(OYUtil.getRadiusBackground(OYUtil.c(R.color.banner_type_background)));
                tv_type.setText(type);
            }
//
//                Log.e("MainFragment","Height"+view.getHeight());
//                Log.e("MainFragment","Width"+view.getWidth());

        });

        LogUtil.time(TAG,"2.3");
        if (saveBundle == null) {
            findMcNews();
            LogUtil.time(TAG,"2.4");
            checkRes();
            LogUtil.time(TAG,"2.5");
        } else {
//            Log.e(TAG,"列表"+mcNewsList.size());
            MainFragment.this.mcNewsList = (ArrayList<McNews>) saveBundle.getSerializable(ARG_MC_NEWS_LIST);
            if (mcNewsList != null)
                handler.sendEmptyMessage(TYPE_BANNER_QUERY_OK);
            else
                findMcNews();
            handler.sendEmptyMessage(TYPE_RES_LOADING_OK);
        }
    }

    private void setServerListType(int type) {
        RecyclerView.LayoutManager linearLayoutManager = new LinearLayoutManager(getActivity());
        switch (type) {
            case SharedPreferenceUtil.SERVER_LIST_TYPE_LIST:
                if (!isHorizontal) {
                    linearLayoutManager = new LinearLayoutManager(getActivity());
                    ygoServerListAdp.setNewInstance(ygoServerGridAdp.getData());
                    ygoServerAdp = ygoServerListAdp;
                } else {
                    linearLayoutManager = new LinearLayoutManager(getActivity());
                    ygoServerListAdp.setNewInstance(ygoServerGridAdp.getData());
                    ygoServerAdp = ygoServerListAdp;
                }
                iv_list_mode.setImageResource(R.drawable.ic_list);
                break;
            case SharedPreferenceUtil.SERVER_LIST_TYPE_GRID:
                if (!isHorizontal) {
                    linearLayoutManager = new GridLayoutManager(getActivity(), 2);
                    ygoServerGridAdp.setNewInstance(ygoServerListAdp.getData());
                    ygoServerAdp = ygoServerGridAdp;
                } else {
                    linearLayoutManager = new LinearLayoutManager(getActivity());
                    ygoServerListAdp.setNewInstance(ygoServerGridAdp.getData());
                    ygoServerAdp = ygoServerListAdp;
                }
                iv_list_mode.setImageResource(R.drawable.ic_grid);
                break;
        }

        ygoServerAdp.addChildClickViewIds(R.id.tv_create_and_share);
        ygoServerAdp.setOnItemChildClickListener((adapter, view, position) -> {
            switch (view.getId()) {
                case R.id.tv_create_and_share:
                    joinRoom((YGOServer) adapter.getItem(position), true);
                    break;
            }
        });
        ygoServerAdp.setOnItemClickListener((adapter, view, position) -> {
            YGOServer serverInfo = (YGOServer) adapter.getItem(position);
            switch (serverInfo.getOpponentType()) {
                case YGOServer.OPPONENT_TYPE_AI:
                    OYDialogUtil.dialogAiList(getActivity(), serverInfo);
                    break;
                default:
                    joinRoom(serverInfo, false);
                    break;
            }
        });

        rv_service_list.setLayoutManager(linearLayoutManager);
        rv_service_list.setAdapter(ygoServerAdp);
    }

    private void findMcNews() {
        isMcNewsLoadException = false;
        mcNewsList= (ArrayList<McNews>) LitePal.findAll(McNews.class);
        if (mcNewsList!=null&&mcNewsList.size()!=0){
            Message message = new Message();
            while (mcNewsList.size() > 5) {
                mcNewsList.remove(mcNewsList.size() - 1);
            }
            message.what = TYPE_BANNER_QUERY_OK;
            handler.sendMessage(message);
        }else {
            tv_banner_loading.setVisibility(View.VISIBLE);
            tv_banner_loading.setText("加载中");
        }
        MyCardUtil.findMyCardNews((myCardNewsList, exception) -> {
            Message message = new Message();
            if (TextUtils.isEmpty(exception)) {
                //如果新获取的数据和老数据都对应相等，则视为没有新的新闻，不刷新
                if (mcNewsList!=null&&mcNewsList.size()!=0) {
                    int num = Math.min(mcNewsList.size(), myCardNewsList.size());
                    boolean isRefresh = false;
                    for (int i = 0; i < num; i++) {
                        String id = mcNewsList.get(i).getNewId();
                        if (id!=null&&!id.equals(myCardNewsList.get(i).getNewId())) {
                            isRefresh = true;
                            break;
                        }
                    }

                    Log.e("MainFragemnt", "刷新取消" + !isRefresh);
                    if (!isRefresh)
                        return;
                }

                while (myCardNewsList.size() > 5) {
                    myCardNewsList.remove(myCardNewsList.size() - 1);
                }
                MainFragment.this.mcNewsList = (ArrayList<McNews>) myCardNewsList;
                message.what = TYPE_BANNER_QUERY_OK;
            } else {
                Log.e("MainFragemnt", "查询失败" + exception);
                message.obj = exception;
                message.what = TYPE_BANNER_QUERY_EXCEPTION;
            }
            handler.sendMessage(message);
        });
    }

    private void checkRes() {
        checkResourceDownload((error, isNew) -> {
            handler.sendEmptyMessage(TYPE_RES_LOADING_OK);
//            if (error < 0) {
//                enableStart = false;
//            } else {
//                enableStart = true;
//            }
//            if (isNew) {
//                if (!getGameUriManager().doIntent(getIntent())) {
//                    DialogPlus dialog = new DialogPlus(this)
//                            .setTitleText(getString(R.string.settings_about_change_log))
//                            .loadUrl("file:///android_asset/changelog.html", Color.TRANSPARENT)
//                            .hideButton()
//                            .setOnCloseLinster((dlg) -> {
//                                dlg.dismiss();
//                                //mImageUpdater
//                                if (NETWORK_IMAGE && NetUtils.isConnected(getContext())) {
//                                    if (!mImageUpdater.isRunning()) {
//                                        mImageUpdater.start();
//                                    }
//                                }
//                            });
//                    dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
//                        @Override
//                        public void onDismiss(DialogInterface dialogInterface) {
//                            PermissionUtil.isServicePermission(cn.garymb.ygomobile.ui.home.MainActivity.this, true);
//
//                        }
//                    });
//                    dialog.show();
//                }
//            } else {
//                PermissionUtil.isServicePermission(cn.garymb.ygomobile.ui.home.MainActivity.this, true);
//                getGameUriManager().doIntent(getIntent());
//            }

        });
    }

    protected void checkResourceDownload(ResCheckTask.ResCheckListener listener) {

        mResCheckTask = new ResCheckTask(getActivity(), listener);
        LogUtil.time(TAG,"2.6");
        mResCheckTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
        LogUtil.time(TAG,"2.7");
//        if (Build.VERSION.SDK_INT >= 11) {
//            mResCheckTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
//        } else {
//            mResCheckTask.execute();
//        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.e("MainF", requestCode + " 返回 " + resultCode);
        if (resultCode == Activity.RESULT_OK)
            switch (requestCode) {
                case REQUEST_NEW_SERVER:
                    YGOUtil.getYGOServerList(serverList -> ygoServerAdp.setList(serverList.getServerInfoList()));

                    break;
            }
    }

    @Override
    public void onResume() {
        super.onResume();
        xb_banner.startAutoPlay();
    }

    @Override
    public void onStop() {
        super.onStop();
        xb_banner.stopAutoPlay();
    }

    private void initServiceList() {
        YGOUtil.getYGOServerList(serverList -> {
            ygoServerAdp.setList(serverList.getServerInfoList());
        });

    }

    private void joinRoom(YGOServer ygoServer, boolean isClickButton) {

        if (isClickButton) {
            switch (ygoServer.getOpponentType()) {
                case YGOServer.OPPONENT_TYPE_FRIEND:
                    OYDialogUtil.dialogcreateRoom(getActivity(), ygoServer);
                    break;
                case YGOServer.OPPONENT_TYPE_RANDOM:
                    String password = "";
                    switch (ygoServer.getMode()) {
                        case YGOServer.MODE_ONE:
                            password = "S";
                            break;
                        case YGOServer.MODE_MATCH:
                            password = "M";
                            break;
                        case YGOServer.MODE_TAG:
                            password = "T";
                            break;
                    }
                    if (!OYApplication.isIsInitRes()) {
                        OYUtil.show("请等待资源加载完毕后加入游戏");
                        return;
                    }
                    YGOUtil.joinGame(getActivity(), ygoServer, password);
                    break;
                case YGOServer.OPPONENT_TYPE_AI:
                    if (!OYApplication.isIsInitRes()) {
                        OYUtil.show("请等待资源加载完毕后加入游戏");
                        return;
                    }
                    YGOUtil.joinGame(getActivity(), ygoServer, "AI");
                    break;
                default:
                    OYDialogUtil.dialogcreateRoom(getActivity(), ygoServer);
            }
        } else {
            OYDialogUtil.dialogJoinRoom(getActivity(), ygoServer);
        }
    }

//    class FmPagerAdapter extends FragmentPagerAdapter{
//
//        public FmPagerAdapter(FragmentManager fm) {
//            super(fm);
//        }
//
//        @Override
//        public Fragment getItem(int position) {
//            return fragmentDataList.get(position).getFragment();
//        }
//
//        @Override
//        public int getCount() {
//            return fragmentDataList.size();
//        }
//
//        @Nullable
//        @Override
//        public CharSequence getPageTitle(int position) {
//            return fragmentDataList.get(position).getTitle();
//        }
//    }

}
