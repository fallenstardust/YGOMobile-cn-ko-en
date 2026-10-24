package cn.garymb.ygomobile.ui.home;

import static cn.garymb.ygomobile.Constants.ASSET_SERVER_LIST;
import static cn.garymb.ygomobile.ui.home.HomeActivity.pre_code_list;
import static cn.garymb.ygomobile.ui.home.HomeActivity.released_code_list;
import static cn.garymb.ygomobile.utils.ComparisonTableUtil.newIDsArray;
import static cn.garymb.ygomobile.utils.ComparisonTableUtil.oldIDsArray;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.LinearLayoutCompat;
import androidx.cardview.widget.CardView;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;

import com.ourygo.lib.duelassistant.listener.OnDuelAssistantListener;
import com.ourygo.lib.duelassistant.util.DuelAssistantManagement;
import com.ourygo.lib.duelassistant.util.Util;
import com.stx.xhb.androidx.XBanner;
import com.tubb.smrv.SwipeMenuRecyclerView;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygodata.YGOGameOptions;
import cn.garymb.ygomobile.App;
import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.YGOStarter;
import cn.garymb.ygomobile.base.BaseFragemnt;
import cn.garymb.ygomobile.bean.Deck;
import cn.garymb.ygomobile.bean.ServerInfo;
import cn.garymb.ygomobile.bean.ServerList;
import cn.garymb.ygomobile.bean.events.ExCardEvent;
import cn.garymb.ygomobile.bean.events.ServerInfoEvent;
import cn.garymb.ygomobile.ex_card.ExCardActivity;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.activities.WebActivity;
import cn.garymb.ygomobile.ui.adapters.ServerListAdapter;
import cn.garymb.ygomobile.ui.adapters.SimpleListAdapter;
import cn.garymb.ygomobile.ui.cards.CardDetailRandom;
import cn.garymb.ygomobile.ui.cards.deck_square.DeckManageDialog;
import cn.garymb.ygomobile.ui.mycard.bean.McNews;
import cn.garymb.ygomobile.ui.mycard.MyCard;
import cn.garymb.ygomobile.ui.mycard.mcchat.util.ImageUtil;
import cn.garymb.ygomobile.ui.plus.DefWebViewClient;
import cn.garymb.ygomobile.ui.plus.DialogPlus;
import cn.garymb.ygomobile.ui.plus.VUiKit;
import cn.garymb.ygomobile.ui.widget.Shimmer;
import cn.garymb.ygomobile.ui.widget.ShimmerTextView;
import cn.garymb.ygomobile.utils.FileLogUtil;
import cn.garymb.ygomobile.utils.LogUtil;
import cn.garymb.ygomobile.utils.ServerUtil;
import cn.garymb.ygomobile.utils.SharedPreferenceUtil;
import cn.garymb.ygomobile.utils.YGOUtil;
import cn.hutool.core.util.ArrayUtil;
import ocgcore.CardManager;
import ocgcore.DataManager;
import ocgcore.data.Card;


/**
 * Home tab fragment, base layer of the split inheritance chain
 * (HomeFragmentBase <- HomeFragment). Owns all shared state fields and
 * the download/banner handler, the server-list view wiring, the join /
 * quick-join room flows, the deck save-and-open pipeline, the eventbus
 * subscribers, the duel-assistant lifecycle hooks and the click
 * navigation overrides.
 */
abstract class HomeFragmentBase extends BaseFragemnt implements OnDuelAssistantListener, View.OnClickListener {

    protected static final String TAG = "HomeFragment";
    public static final int ID_HOMEFRAGMENT = 0;
    protected DuelAssistantManagement duelAssistantManagement;
    protected HomeActivity activity;
    protected static final int TYPE_BANNER_QUERY_OK = 0;
    protected static final int TYPE_BANNER_QUERY_EXCEPTION = 1;
    protected static final int TYPE_RES_LOADING_OK = 2;
    public static final int TYPE_GET_DATA_VER_OK = 3;
    protected static final String ARG_MC_NEWS_LIST = "mcNewsList";
    protected boolean isMcNewsLoadException = false;
    protected LinearLayout ll_back;
    ShimmerTextView tv;
    ShimmerTextView tv2;
    Shimmer shimmer;
    protected SwipeMenuRecyclerView mServerList;
    protected ServerListAdapter mServerListAdapter;
    protected ServerListManager mServerListManager;
    protected CardManager mCardManager;
    protected SparseArray<Card> cards;
    protected CardDetailRandom mCardDetailRandom;
    //轮播图
    protected CardView cv_banner;
    protected TextView tv_banner_loading;
    protected XBanner xb_banner;
    protected McNews mcNews;
    protected ArrayList<McNews> mcNewsList;
    //ygopro功能
    protected CardView cv_game;
    protected CardView cv_bot_game;
    protected CardView cv_watch_replay;
    //辅助功能

    /**
     * 先行卡下载按钮，点击后跳转到先行卡下载页面
     */
    protected CardView cv_download_ex;
    protected LinearLayoutCompat ll_new_notice;
    //外连
    protected CardView cv_donation;
    protected CardView cv_help;

    protected Bundle mBundle;
    protected View layoutView;

    protected boolean isShouldSaveAndOpenDeck = false;
    protected Uri pendingUri;
    protected List<Integer> pendingMainList;
    protected List<Integer> pendingExList;
    protected List<Integer> pendingSideList;
    protected boolean pendingIsCompleteDeck;
    protected void initView(View view) {
        //服务器列表
        mServerList = view.findViewById(R.id.list_server);
        mServerListAdapter = new ServerListAdapter(getContext());
        LayoutInflater infla = LayoutInflater.from(getContext());
        //添加服务器
        View footView = infla.inflate(R.layout.item_home_ic_add, null);
        TextView add_server = footView.findViewById(R.id.add_server);
        add_server.setOnClickListener(this);

        mServerListAdapter.addFooterView(footView);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(getContext());
        mServerList.setLayoutManager(linearLayoutManager);
        DividerItemDecoration dividerItemDecoration =
                new DividerItemDecoration(getContext(), DividerItemDecoration.VERTICAL);
        mServerList.addItemDecoration(dividerItemDecoration);
        mServerList.setAdapter(mServerListAdapter);
        mServerListManager = new ServerListManager(getContext(), mServerListAdapter);
        mServerListManager.bind(mServerList);
        mServerListManager.syncLoadData();
        cv_game = view.findViewById(R.id.action_game);
        cv_game.setOnClickListener(this);
        cv_bot_game = view.findViewById(R.id.action_bot);
        cv_bot_game.setOnClickListener(this);
        cv_watch_replay = view.findViewById(R.id.action_replay);
        cv_watch_replay.setOnClickListener(this);
        cv_download_ex = view.findViewById(R.id.action_download_ex);
        cv_download_ex.setOnClickListener(this);
        ll_new_notice = view.findViewById(R.id.ll_new_notice);
        /*
        cv_download_ex.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                startActivity(new Intent(getActivity(), FileLogActivity.class));
                return true;
            }
        });*/
        cv_donation = view.findViewById(R.id.nav_webpage);
        cv_donation.setOnClickListener(this);
        cv_help = view.findViewById(R.id.action_help);
        cv_help.setOnClickListener(this);

        ll_back = view.findViewById(R.id.return_to_duel);
        ll_back.setOnClickListener(this);
        tv = view.findViewById(R.id.shimmer_tv);
        tv2 = view.findViewById(R.id.shimmer_tv2);
        toggleAnimation(tv);
        toggleAnimation(tv2);

        mCardManager = DataManager.get().getCardManager();
    }
    @SuppressLint("HandlerLeak")
    Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case TYPE_BANNER_QUERY_OK:
                    tv_banner_loading.setVisibility(View.GONE);
                    xb_banner.setBannerData(R.layout.item_banner_main, mcNewsList);
                    break;
                case TYPE_BANNER_QUERY_EXCEPTION:
                    tv_banner_loading.setText(R.string.loading_failed);
                    isMcNewsLoadException = true;
                    break;

            }

        }
    };

    /**
     * 通过http访问web读取先行卡版本号。
     * 读取结果通过handler发到ui线程
     * 注意在ExCardActivity中包含一个相同实现
     * ServerUtil获取到版本状态后会通过eventmessage通知调用本函数，不需要在主函数显式调用
     */
    public void changeExCardNewMark() {
        LogUtil.i(TAG, "check excard new mark, version:" + ServerUtil.exCardState);
        if (ServerUtil.exCardState == ServerUtil.ExCardState.UPDATED) {
            ll_new_notice.setVisibility(View.GONE);
        } else if (ServerUtil.exCardState == ServerUtil.ExCardState.NEED_UPDATE) {
            ll_new_notice.setVisibility(View.VISIBLE);
        } else if (ServerUtil.exCardState == ServerUtil.ExCardState.ERROR) {
            YGOUtil.showTextToast(R.string.ex_card_check_toast_message_iii);
            ll_new_notice.setVisibility(View.GONE);
        } else if (ServerUtil.exCardState == ServerUtil.ExCardState.UNCHECKED) {
            //do nothing
            //由于UNCHECKED状态仅在app刚启动时短暂存在，因此不需要对其进行处理
            //the UNCHECKED state only exists temporarily before the check action, so we need not handle it.
        }

    }
    /**
     * 根据同服务器状态设置展示列表中每项的颜色（服务器不可用就设置为灰色）
     */
    protected void changeColor() {
        /* 同步设置服务器列表的状态，在syncLoadData()里更新recyclerview的数据，在更新数据时convert()方法自动更改item的颜色 */
        mServerListManager.syncLoadData();

        /* 改变“扩展卡下载”按钮的颜色 */
//        if (AppsSettings.get().isReadExpansions()) {
//            Paint paint = getPaint(1);
//            cv_download_ex.setLayerType(View.LAYER_TYPE_HARDWARE, paint);
//        } else {
//            Paint paint = getPaint(0);
//            cv_download_ex.setLayerType(View.LAYER_TYPE_HARDWARE, paint);
//        }
    }
    public void joinRoom(int position) {
        ServerInfo serverInfo = mServerListAdapter.getItem(position);
        if (serverInfo == null) {
            return;
        }
        //进入房间
        DialogPlus builder = new DialogPlus(getContext());
        builder.setTitle(R.string.intput_room_name);
        builder.setContentView(R.layout.dialog_edit_and_list);
        EditText editText = builder.bind(R.id.room_name);
        ListView listView = builder.bind(R.id.room_list);
        TextView text_abt_roomlist = builder.bind(R.id.abt_room_list);
        SimpleListAdapter simpleListAdapter = new SimpleListAdapter(getContext());
        simpleListAdapter.set(AppsSettings.get().getLastRoomList());
        if (AppsSettings.get().getLastRoomList().size() > 0)
            text_abt_roomlist.setVisibility(View.VISIBLE);
        else text_abt_roomlist.setVisibility(View.GONE);
        listView.setAdapter(simpleListAdapter);
        listView.setOnItemClickListener((a, v, pos, index) -> {
            String name = simpleListAdapter.getItemById(index);
            editText.setText(name);
        });
        editText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                builder.dismiss();
                String name = editText.getText().toString();
                if (!TextUtils.isEmpty(name)) {
                    List<String> items = simpleListAdapter.getItems();
                    int index = items.indexOf(name);
                    if (index >= 0) {
                        items.remove(index);
                        items.add(0, name);
                    } else {
                        items.add(0, name);
                    }
                    AppsSettings.get().setLastRoomList(items);
                    simpleListAdapter.notifyDataSetChanged();
                }
                joinGame(serverInfo, name);
                return true;
            }
            return false;
        });
        listView.setOnItemLongClickListener((a, v, i, index) -> {
            String name = simpleListAdapter.getItemById(index);
            int pos = simpleListAdapter.findItem(name);
            if (pos >= 0) {
                simpleListAdapter.remove(pos);
                simpleListAdapter.notifyDataSetChanged();
                AppsSettings.get().setLastRoomList(simpleListAdapter.getItems());
            }
            return true;
        });
        builder.setLeftButtonText(R.string.join_game);
        builder.setLeftButtonListener((dlg, i) -> {
            dlg.dismiss();
            if (Build.VERSION.SDK_INT >= 23 && YGOStarter.isGameRunning(getActivity())) {
                YGOUtil.showTextToast(Gravity.CENTER, R.string.tip_return_to_duel);
                openGame();
            } else {
                //保存名字
                String name = editText.getText().toString();
                if (!TextUtils.isEmpty(name)) {
                    List<String> items = simpleListAdapter.getItems();
                    int index = items.indexOf(name);
                    if (index >= 0) {
                        items.remove(index);
                        items.add(0, name);
                    } else {
                        items.add(0, name);
                    }
                    AppsSettings.get().setLastRoomList(items);
                    simpleListAdapter.notifyDataSetChanged();
                }
                joinGame(serverInfo, name);
            }
        });
        builder.setOnCloseLinster((dlg) -> {
            dlg.dismiss();
        });
        builder.setOnCancelListener((dlg) -> {
        });
        builder.show();
    }
    void joinGame(ServerInfo serverInfo, String name) {
        showTipsToast();
        YGOGameOptions options = new YGOGameOptions();
        options.mServerAddr = serverInfo.getServerAddr();
        // 获取用户唯一码并在用户名后面添加$和唯一码
        String userUniqueId = SharedPreferenceUtil.getUserUniqueId();
        String playerName = serverInfo.getPlayerName();
        if (userUniqueId != null && !userUniqueId.isEmpty()) {
            playerName = playerName.contains("$") ? playerName : playerName + "$" + userUniqueId;
        }
        options.mUserName = playerName;
        options.mPort = serverInfo.getPort();
        options.mRoomName = name;
        YGOStarter.startGame(getActivity(), options);
    }
    public void quickjoinRoom(String host, int port, String password) {

        String message;
        if (!TextUtils.isEmpty(host))
            message = getString(R.string.quick_join)
                    + "\nIP：" + host
                    + "\n端口：" + port
                    + "\n密码：" + password;
        else
            message = getString(R.string.quick_join) + "：\"" + password + "\"";

        DialogPlus dialog = new DialogPlus(getContext());
        dialog.setTitle(R.string.question);
        dialog.setMessage(message);
        dialog.setMessageGravity(Gravity.CENTER_HORIZONTAL);
        dialog.setLeftButtonText(R.string.Cancel);
        dialog.setRightButtonText(R.string.join);
        dialog.show();
        dialog.setRightButtonListener((dlg, s) -> {
            dialog.dismiss();
            ServerListAdapter mServerListAdapter = new ServerListAdapter(getContext());
            ServerListManager mServerListManager = new ServerListManager(getContext(), mServerListAdapter);
            mServerListManager.syncLoadData();
            File xmlFile = new File(App.get().getFilesDir(), Constants.SERVER_FILE);
            VUiKit.defer().when(() -> {
                ServerList assetList = ServerListManager.readList(getContext().getAssets().open(ASSET_SERVER_LIST));
                ServerList fileList = xmlFile.exists() ? ServerListManager.readList(new FileInputStream(xmlFile)) : null;
                if (fileList == null) {
                    return assetList;
                }
                if (fileList.getVercode() < assetList.getVercode()) {
                    xmlFile.delete();
                    return assetList;
                }
                return fileList;
            }).done((list) -> {
                if (list != null) {
                    String host1 = host;
                    int port1 = port;
                    ServerInfo serverInfo = list.getServerInfoList().get(0);
                    if (!TextUtils.isEmpty(host1)) {
                        serverInfo.setServerAddr(host1);
                        serverInfo.setPort(port1);
                    }
                    joinGame(serverInfo, password);
                }
            });
        });
        dialog.setLeftButtonListener((dlg, s) -> {
            dialog.dismiss();
        });
    }
    public void BacktoDuel() {
        if (YGOStarter.isGameRunning(getActivity())) {
            ll_back.setVisibility(View.VISIBLE);
        } else {
            ll_back.setVisibility(View.GONE);
        }
    }

    public void toggleAnimation(ShimmerTextView target) {
        if (shimmer != null && shimmer.isAnimating()) {
            shimmer.cancel();
        } else {
            shimmer = new Shimmer();
            shimmer.start(target);
        }
    }

    public void showTipsToast() {
        if (!YGOStarter.isGameRunning(getActivity())) {
            String[] tipsList = this.getResources().getStringArray(R.array.tips);
            int x = (int) (Math.random() * tipsList.length);
            String tips = tipsList[x];
            YGOUtil.showTextToast(tips, Toast.LENGTH_LONG);
        }
    }
    public void onJoinRoom(String host, int port, String password, int id) {
        if (YGOStarter.isGameRunning(getActivity())) {
            DialogPlus dlg = new DialogPlus(getActivity());
            dlg.setMessage(R.string.tip_ygopro_is_running);
            dlg.setLeftButtonListener((d, s) -> {
                if (!TextUtils.isEmpty(password)) {
                    SimpleListAdapter simpleListAdapter = new SimpleListAdapter(getContext());
                    simpleListAdapter.set(AppsSettings.get().getLastRoomList());
                    List<String> items = simpleListAdapter.getItems();
                    int index = items.indexOf(password);
                    if (index >= 0) {
                        items.remove(index);
                        items.add(0, password);
                    } else {
                        items.add(0, password);
                    }
                    AppsSettings.get().setLastRoomList(items);
                    simpleListAdapter.notifyDataSetChanged();
                }
                dlg.dismiss();
            });
            dlg.show();
        }
        if (id == ID_HOMEFRAGMENT) {
            quickjoinRoom(host, port, password);
        }
    }
    @Override
    public void onCardQuery(String key, int id) {
        /*
        if (id == ID_HOMEFRAGMENT) {
            Intent intent = new Intent(this, CardSearchFragment.class);
            intent.putExtra(CardSearchFragment.SEARCH_MESSAGE, key);
            startActivity(intent);
        }*/
    }

    @Override
    public void onSaveDeck(Uri uri, List<Integer> mainList, List<Integer> exList, List<Integer> sideList, boolean isCompleteDeck, String exception, int id) {
        saveDeck(uri, mainList, exList, sideList, isCompleteDeck, exception, id != DefWebViewClient.CHECK_ID_WEB_VIEW_NEW_ACTIVITY);
    }
    public void saveDeck(Uri uri, List<Integer> mainList, List<Integer> exList, List<Integer> sideList, boolean isCompleteDeck, String exception, boolean isShowDialog) {
        if (isShowDialog && !TextUtils.isEmpty(exception)) {
            YGOUtil.showTextToast("卡组解析失败，原因为：" + exception);
            return;
        }
        for (Integer id : mainList) {
            if (released_code_list.contains(id)) {//先查看id对应的卡片密码是否在正式数组中存在
                mainList.set(mainList.indexOf(id), pre_code_list.get(released_code_list.indexOf(id)));//使用set而不是用remove和add为了避免Arraylist的ConcurrentModificationException异常
            }
            //执行完后变成先行密码，如果constants对照表里存在该密码，则如下又转换一次，确保正式更新后不会出错，最好发布app后必须及时更新在线对照表
            if (ArrayUtil.contains(oldIDsArray, id)) {
                mainList.set(mainList.indexOf(id), ArrayUtil.get(newIDsArray, ArrayUtil.indexOf(oldIDsArray, id)));
            }
        }
        for (Integer id : exList) {
            if (released_code_list.contains(id)) {
                exList.set(exList.indexOf(id), pre_code_list.get(released_code_list.indexOf(id)));
            }
            if (ArrayUtil.contains(oldIDsArray, id)) {
                exList.set(exList.indexOf(id), ArrayUtil.get(newIDsArray, ArrayUtil.indexOf(oldIDsArray, id)));
            }
        }
        for (Integer id : sideList) {
            if (released_code_list.contains(id)) {
                sideList.set(sideList.indexOf(id), pre_code_list.get(released_code_list.indexOf(id)));
            }
            if (ArrayUtil.contains(oldIDsArray, id)) {
                sideList.set(sideList.indexOf(id), ArrayUtil.get(newIDsArray, ArrayUtil.indexOf(oldIDsArray, id)));
            }
        }

        if (isShowDialog) {
            DialogPlus dialog = new DialogPlus(getContext());
            dialog.setTitle(R.string.question);
            dialog.setMessage(R.string.find_deck_text);
            dialog.setMessageGravity(Gravity.CENTER_HORIZONTAL);
            dialog.setLeftButtonText(R.string.Cancel);
            dialog.setRightButtonText(R.string.save_n_open);
            dialog.show();
            dialog.setLeftButtonListener((dlg, s) -> {
                dialog.dismiss();
            });
            dialog.setRightButtonListener((dlg, s) -> {
                dialog.dismiss();
                saveAndOpenDeck(uri, mainList, exList, sideList, isCompleteDeck);
            });
        } else {
            if (isAdded() && isResumed()) {
                saveAndOpenDeck(uri, mainList, exList, sideList, isCompleteDeck);
            } else {
                isShouldSaveAndOpenDeck = true;
                pendingUri = uri;
                pendingMainList = mainList;
                pendingExList = exList;
                pendingSideList = sideList;
                pendingIsCompleteDeck = isCompleteDeck;
            }
        }
    }

    protected void saveAndOpenDeck(Uri uri, List<Integer> mainList, List<Integer> exList, List<Integer> sideList, boolean isCompleteDeck) {
        Deck deck;
        //如果是卡组url
        if (uri != null) {
            deck = new Deck(uri, mainList, exList, sideList);
        } else {
            deck = new Deck(getString(R.string.rename_deck) + System.currentTimeMillis(), mainList, exList, sideList);
        }
        deck.setCompleteDeck(isCompleteDeck);
        File file = deck.saveTemp(AppsSettings.get().getDeckDir());
        if (!deck.isCompleteDeck()) {
            YGOUtil.showTextToast(activity.getString(R.string.tip_deckInfo_isNot_completeDeck));
        }
        if (!file.getAbsolutePath().isEmpty()) {
            mBundle.putString("setDeck", file.getAbsolutePath());
            activity.fragment_deck_cards.setArguments(mBundle);
            // 保存剪贴板中的卡组码后，卡组已落地到本地卡组目录，
            // 通知卡组管理对话框刷新“本地卡组”列表，使其与磁盘保持一致
            syncDeckManageDialog();
        }
        activity.switchFragment(activity.fragment_deck_cards, 2, true);
    }

    /**
     * 从剪贴板保存卡组码生成新卡组文件后，同步刷新卡组管理对话框中的本地卡组列表。
     * 若对话框当前不存在，则下次创建时会以最新数据构建，无需处理。
     */
    protected void syncDeckManageDialog() {
        if (!isAdded() || activity == null) {
            return;
        }
        Fragment fragment = activity.getSupportFragmentManager()
                .findFragmentByTag(DeckManageDialog.DIALOG_TAG);
        if (fragment instanceof DeckManageDialog) {
            ((DeckManageDialog) fragment).refreshLocalDeckList();
        }
    }
    @Override
    public boolean isListenerEffective() {
        return Util.isContextExisted(getActivity());
    }


    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onMessageReceived(ExCardEvent event) {
        if (event.getType() == ExCardEvent.EventType.exCardPackageChange) {
            changeExCardNewMark();
            changeColor();
        } else if (event.getType() == ExCardEvent.EventType.exCardPrefChange) {
            /* 可以设置在不开启扩展卡的情况下“扩展卡下载”图标是否显示为灰色 */
            changeColor();
        }
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onServerInfoEvent(ServerInfoEvent event) {
        if (event.delete) {
            DialogPlus dialogPlus = new DialogPlus(getContext());
            dialogPlus.setTitle(R.string.question);
            dialogPlus.setMessage(R.string.delete_server_info);
            dialogPlus.setMessageGravity(Gravity.CENTER_HORIZONTAL);
            dialogPlus.setLeftButtonListener((dialog, which) -> {
                mServerListManager.delete(event.position);
                mServerListAdapter.notifyDataSetChanged();
                dialog.dismiss();
            });
            dialogPlus.setCancelable(true);
            dialogPlus.setOnCloseLinster(null);
            dialogPlus.show();
        } else if (event.join) {
            if (ServerUtil.isPreServer(event.serverInfo.getPort(), event.serverInfo.getServerAddr())) {

                //如果是先行卡服务器，并且未开启先行卡设置，则通过toast提示
                if (!AppsSettings.get().isReadExpansions()) {
                    YGOUtil.showTextToast(R.string.ex_card_check_toast_message, Toast.LENGTH_LONG);
                } else if (ServerUtil.exCardState != ServerUtil.ExCardState.UPDATED) {
                    //如果是先行卡服务器，并且未开启下载先行卡，则通过toast提示
                    YGOUtil.showTextToast(R.string.ex_card_check_toast_message_ii, Toast.LENGTH_LONG);
                }
            }
            joinRoom(event.position);
            //showNewbieGuide("joinRoom");
        } else {
            mServerListManager.showEditDialog(event.position);
        }

    }

    public void setRandomCardDetail() {
        //加载数据库中所有卡片卡片
        mCardManager.loadCards();
        //mCardManager = DataManager.get().getCardManager();
        SparseArray<Card> cards = mCardManager.getAllCards();
        int y = (int) (Math.random() * cards.size());
        Card cardInfo = cards.valueAt(y);
        if (cardInfo == null)
            return;
        mCardDetailRandom = CardDetailRandom.genRandomCardDetail(getContext(), activity.getImageLoader(), cardInfo);
    }

    protected void openGame() {
        YGOStarter.startGame(getActivity(), null);
        //getParentFragmentManager().beginTransaction().remove(activity.fragment_deck_cards).commit();
    }
    protected void duelAssistantCheck() {
        if (AppsSettings.get().isServiceDuelAssistant()) {
            Handler handler = new Handler();
            handler.postDelayed(() -> {
                try {
                    FileLogUtil.writeAndTime("主页决斗助手检查");
                } catch (IOException e) {
                    e.printStackTrace();
                }
                duelAssistantManagement.checkClip(ID_HOMEFRAGMENT);
            }, 500);
        }
    }

    protected void initDuelAssistant() {
        duelAssistantManagement = DuelAssistantManagement.getInstance();
        duelAssistantManagement.init(getActivity());
        duelAssistantManagement.addDuelAssistantListener(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        duelAssistantCheck();
        BacktoDuel();
        //server list
        mServerListManager.syncLoadData();
        if (isShouldSaveAndOpenDeck
                && pendingMainList != null
                && pendingExList != null
                && pendingSideList != null
        ) {
            saveAndOpenDeck(pendingUri, pendingMainList, pendingExList, pendingSideList, pendingIsCompleteDeck);
            isShouldSaveAndOpenDeck = false;
            pendingUri = null;
            pendingMainList = null;
            pendingExList = null;
            pendingSideList = null;
            pendingIsCompleteDeck = false;
        }
    }

    @Override
    public void onDestroy() {
        duelAssistantManagement.removeDuelAssistantListener(this);
        if (EventBus.getDefault().isRegistered(this))//加上判断
            EventBus.getDefault().unregister(this);
        super.onDestroy();
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
        return false;
    }
}
