package cn.garymb.ygomobile.ui.home;

import static cn.garymb.ygomobile.Constants.ASSET_SERVER_LIST;

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
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.ui.activities.WebActivity;
import cn.garymb.ygomobile.ui.adapters.ServerListAdapter;
import cn.garymb.ygomobile.ui.adapters.SimpleListAdapter;
import cn.garymb.ygomobile.ui.cards.CardDetailRandom;
import cn.garymb.ygomobile.ui.mycard.McNews;
import cn.garymb.ygomobile.ui.mycard.MyCard;
import cn.garymb.ygomobile.ui.mycard.mcchat.util.ImageUtil;
import cn.garymb.ygomobile.ui.plus.DialogPlus;
import cn.garymb.ygomobile.ui.plus.VUiKit;
import cn.garymb.ygomobile.ui.widget.Shimmer;
import cn.garymb.ygomobile.ui.widget.ShimmerTextView;
import cn.garymb.ygomobile.utils.FileLogUtil;
import cn.garymb.ygomobile.utils.ServerUtil;
import cn.garymb.ygomobile.utils.YGOUtil;
import ocgcore.CardManager;
import ocgcore.DataManager;
import ocgcore.data.Card;


public class HomeFragment extends BaseFragemnt implements OnDuelAssistantListener, View.OnClickListener {
    private static final String TAG = "HomeFragment";
    public static final int ID_HOMEFRAGMENT = 0;
    private DuelAssistantManagement duelAssistantManagement;
    private HomeActivity activity;
    private static final int TYPE_BANNER_QUERY_OK = 0;
    private static final int TYPE_BANNER_QUERY_EXCEPTION = 1;
    private static final int TYPE_RES_LOADING_OK = 2;
    public static final int TYPE_GET_DATA_VER_OK = 3;
    private static final String ARG_MC_NEWS_LIST = "mcNewsList";
    private boolean isMcNewsLoadException = false;

    private LinearLayout ll_back;
    ShimmerTextView tv;
    ShimmerTextView tv2;
    Shimmer shimmer;
    private SwipeMenuRecyclerView mServerList;
    private ServerListAdapter mServerListAdapter;
    private ServerListManager mServerListManager;
    private CardManager mCardManager;
    private CardDetailRandom mCardDetailRandom;
    private ImageLoader mImageLoader;
    //轮播图
    private CardView cv_banner;
    private TextView tv_banner_loading;
    private XBanner xb_banner;
    private McNews mcNews;
    private ArrayList<McNews> mcNewsList;
    //ygopro功能
    private CardView cv_game;
    private CardView cv_bot_game;
    private CardView cv_watch_replay;
    //辅助功能
    private CardView cv_download_ex;
    private LinearLayoutCompat ll_new_notice;
    //外连
    private CardView cv_donation;
    private CardView cv_help;

    private Bundle mBundle;
    private View layoutView;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        if (isHorizontal)
            layoutView = inflater.inflate(R.layout.fragment_home_horizontal, container, false);
        else
            layoutView = inflater.inflate(R.layout.fragment_home, container, false);
        initView(layoutView);
        initBanner(layoutView, savedInstanceState);
        //初始化决斗助手
        initDuelAssistant();
        activity = (HomeActivity) getActivity();
        mBundle = new Bundle();
        //event
        if (!EventBus.getDefault().isRegistered(this)) {//加上判断
            EventBus.getDefault().register(this);
        }
        changeColor();
        //showNewbieGuide("homePage");
        return layoutView;
    }

    private void initView(View view) {
        //服务器列表
        mServerList = view.findViewById(R.id.list_server);
        mServerListAdapter = new ServerListAdapter(getContext());
        LayoutInflater infla = LayoutInflater.from(getContext());
        //添加服务器
        View footView = infla.inflate(R.layout.item_ic_add, null);
        TextView add_server = footView.findViewById(R.id.add_server);
        add_server.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mServerListManager.addServer();
            }
        });
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

        mImageLoader = new ImageLoader(false);
        mCardManager = DataManager.get().getCardManager();
    }

    //轮播图
    public void initBanner(View view, Bundle saveBundle) {
        xb_banner = view.findViewById(R.id.xb_banner);
        cv_banner = view.findViewById(R.id.cv_banner);
        cv_banner.post(() -> {
            ViewGroup.LayoutParams layoutParams = cv_banner.getLayoutParams();
            if (isHorizontal) {
                layoutParams.width = cv_banner.getWidth();
                layoutParams.height = layoutParams.width / 5;
            } else {
                layoutParams.width = cv_banner.getWidth();
                layoutParams.height = layoutParams.width / 3;
            }
            cv_banner.setLayoutParams(layoutParams);
        });
        tv_banner_loading = view.findViewById(R.id.tv_banner_loading);
        tv_banner_loading.setOnClickListener(this);
        xb_banner.setOnItemClickListener((banner, model, v, position) ->
                WebActivity.open(getContext(), getString(R.string.McNews), mcNewsList.get(position).getNews_url())
        );
        xb_banner.loadImage((banner, model, v, position) -> {
            TextView tv_time, tv_title, tv_type;
            ImageView iv_image;

            tv_time = v.findViewById(R.id.tv_time);
            tv_title = v.findViewById(R.id.tv_title);
            tv_type = v.findViewById(R.id.tv_type);
            iv_image = v.findViewById(R.id.iv_image);

            mcNews = mcNewsList.get(position);
            ImageUtil.setImageAndBackground(getContext(), mcNews.getImage_url(), iv_image);
            tv_time.setText(mcNews.getCreate_time());
            tv_title.setText(mcNews.getTitle());
            tv_type.setVisibility(View.GONE);
        });
        if (saveBundle == null) {
            findMcNews();
        } else {
            mcNewsList = (ArrayList<McNews>) saveBundle.getSerializable(ARG_MC_NEWS_LIST);
            if (mcNewsList != null)
                handler.sendEmptyMessage(TYPE_BANNER_QUERY_OK);
            else
                findMcNews();
            handler.sendEmptyMessage(TYPE_RES_LOADING_OK);
        }
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
        Log.i(TAG, "check excard new mark, version:" + ServerUtil.exCardState);
        if (ServerUtil.exCardState == ServerUtil.ExCardState.UPDATED) {
            ll_new_notice.setVisibility(View.GONE);
        } else if (ServerUtil.exCardState == ServerUtil.ExCardState.NEED_UPDATE) {
            ll_new_notice.setVisibility(View.VISIBLE);
        } else if (ServerUtil.exCardState == ServerUtil.ExCardState.ERROR) {
            Toast.makeText(getActivity(), "无法获取服务器先行卡信息", Toast.LENGTH_SHORT).show();
            ll_new_notice.setVisibility(View.GONE);
        }

    }

    private void changeColor() {
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


    private void findMcNews() {
        isMcNewsLoadException = false;
        tv_banner_loading.setVisibility(View.VISIBLE);
        tv_banner_loading.setText(R.string.loading);
        MyCard.findMyCardNews((myCardNewsList, exception) -> {
            Message message = new Message();
            if (TextUtils.isEmpty(exception)) {
                while (myCardNewsList.size() > 5) {
                    myCardNewsList.remove(myCardNewsList.size() - 1);
                }
                HomeFragment.this.mcNewsList = (ArrayList<McNews>) myCardNewsList;
                message.what = TYPE_BANNER_QUERY_OK;
            } else {
                Log.e("HomeFragemnt", "查询失败" + exception);
                message.obj = exception;
                message.what = TYPE_BANNER_QUERY_EXCEPTION;
            }
            handler.sendMessage(message);
        });
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
                Toast toast = Toast.makeText(App.get().getApplicationContext(), R.string.tip_return_to_duel, Toast.LENGTH_SHORT);
                toast.setGravity(Gravity.CENTER, 0, 0);
                toast.show();
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
        options.mUserName = serverInfo.getPlayerName();
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
            Toast.makeText(getActivity(), tips, Toast.LENGTH_LONG).show();
        }
    }

    public void onJoinRoom(String host, int port, String password, int id) {
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
        saveDeck(uri, mainList, exList, sideList, isCompleteDeck, exception);
    }

    public void saveDeck(Uri uri, List<Integer> mainList, List<Integer> exList, List<Integer> sideList, boolean isCompleteDeck, String exception) {
        if (!TextUtils.isEmpty(exception)) {
            YGOUtil.show("卡组解析失败，原因为：" + exception);
            return;
        }
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
            Deck deckInfo;
            //如果是卡组url
            if (uri != null) {
                deckInfo = new Deck(uri, mainList, exList, sideList);
            } else {
                deckInfo = new Deck(getString(R.string.rename_deck) + System.currentTimeMillis(), mainList, exList, sideList);
            }
            deckInfo.setCompleteDeck(isCompleteDeck);
            File file = deckInfo.saveTemp(AppsSettings.get().getDeckDir());
            if (!deckInfo.isCompleteDeck()) {
                YGOUtil.show("当前卡组缺少完整信息，将只显示已有卡片");
            }
            if (!file.getAbsolutePath().isEmpty()) {
                mBundle.putString("setDeck", file.getAbsolutePath());
                activity.fragment_deck_cards.setArguments(mBundle);
            }
            activity.switchFragment(activity.fragment_deck_cards, 2, true);
        });
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
                    Toast.makeText(getActivity(), R.string.ex_card_check_toast_message, Toast.LENGTH_LONG).show();
                } else if (ServerUtil.exCardState != ServerUtil.ExCardState.UPDATED) {
                    //如果是先行卡服务器，并且未开启下载先行卡，则通过toast提示
                    Toast.makeText(getActivity(), R.string.ex_card_check_toast_message_ii, Toast.LENGTH_LONG).show();
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
        mCardDetailRandom = CardDetailRandom.genRandomCardDetail(getContext(), mImageLoader, cardInfo);
    }

    protected void openGame() {
        YGOStarter.startGame(getActivity(), null);
        getFragmentManager().beginTransaction().remove(activity.fragment_deck_cards).commit();
    }

    private void duelAssistantCheck() {
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

    private void initDuelAssistant() {
        duelAssistantManagement = DuelAssistantManagement.getInstance();
        duelAssistantManagement.init(getActivity());
        duelAssistantManagement.addDuelAssistantListener(this);
//        YGOUtil.startDuelService(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        duelAssistantCheck();
        BacktoDuel();
        //server list
        mServerListManager.syncLoadData();
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

    /**
     * Called when a view has been clicked.
     *
     * @param v The view that was clicked.
     */
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.action_game:
                setRandomCardDetail();
                if (mCardDetailRandom != null) {
                    mCardDetailRandom.show();
                }
                openGame();
                break;
            case R.id.action_replay:
                YGOStarter.startGame(getActivity(), null, "-k", "-r");
                getFragmentManager().beginTransaction().remove(activity.fragment_deck_cards).commit();
                break;
            case R.id.action_bot:
                YGOStarter.startGame(getActivity(), null, "-k", "-s");
                getFragmentManager().beginTransaction().remove(activity.fragment_deck_cards).commit();
                break;
            case R.id.action_download_ex:
//                if (!AppsSettings.get().isReadExpansions()) {//如果未开启扩展卡设置，直接跳过
//                    Toast.makeText(getActivity(), R.string.ypk_go_setting, Toast.LENGTH_LONG).show();
//                    break;
//                }
                /* using Web crawler to extract the information of pre card */
                Intent exCardIntent = new Intent(getActivity(), ExCardActivity.class);
                startActivity(exCardIntent);
                break;
            case R.id.action_help: {
                final DialogPlus dialog = new DialogPlus(getContext());
                dialog.setContentView(R.layout.dialog_help);
                dialog.setTitle(R.string.question);
                dialog.show();
                View viewDialog = dialog.getContentView();
                Button btnMasterRule = viewDialog.findViewById(R.id.masterrule);
                Button btnTutorial = viewDialog.findViewById(R.id.tutorial);

                btnMasterRule.setOnClickListener(v1 -> {
                    WebActivity.open(getContext(), getString(R.string.masterrule), Constants.URL_MASTER_RULE_CN);
                    dialog.dismiss();
                });
                btnTutorial.setOnClickListener(v1 -> {
                    WebActivity.open(getContext(), getString(R.string.help), Constants.URL_HELP);
                    dialog.dismiss();
                });

            }
            break;
            case R.id.nav_webpage: {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(Constants.URL_DONATE));
                Toast.makeText(getActivity(), R.string.donatefor, Toast.LENGTH_LONG).show();
                startActivity(intent);
            }
            break;
            case R.id.return_to_duel: {
                openGame();
            }
            break;
            case R.id.tv_banner_loading:
                if (isMcNewsLoadException)
                    findMcNews();
                break;
        }
    }


    /*//https://www.jianshu.com/p/99649af3b191
    public void showNewbieGuide(String scene) {
        HighlightOptions options = new HighlightOptions.Builder()//绘制一个高亮虚线圈
                .setOnHighlightDrewListener(new OnHighlightDrewListener() {
                    @Override
                    public void onHighlightDrew(Canvas canvas, RectF rectF) {
                        Paint paint = new Paint();
                        paint.setColor(Color.WHITE);
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setStrokeWidth(20);
                        paint.setPathEffect(new DashPathEffect(new float[]{20, 20}, 0));
                        canvas.drawCircle(rectF.centerX(), rectF.centerY(), rectF.width() / 2 + 10, paint);
                    }
                }).build();
        HighlightOptions options2 = new HighlightOptions.Builder()//绘制一个高亮虚线矩形
                .setOnHighlightDrewListener(new OnHighlightDrewListener() {
                    @Override
                    public void onHighlightDrew(Canvas canvas, RectF rectF) {
                        Paint paint = new Paint();
                        paint.setColor(Color.WHITE);
                        paint.setStyle(Paint.Style.STROKE);
                        paint.setStrokeWidth(20);
                        paint.setPathEffect(new DashPathEffect(new float[]{20, 20}, 0));
                        canvas.drawRect(rectF, paint);
                    }
                }).build();
        if (scene == "homePage") {
            NewbieGuide.with(this)//with方法可以传入Activity或者Fragment，获取引导页的依附者
                    .setLabel("homepageGuide")
                    .addGuidePage(
                            GuidePage.newInstance().setEverywhereCancelable(true)
                                    .setBackgroundColor(0xbc000000)
                                    .addHighLightWithOptions(layoutView.findViewById(R.id.cv_banner), HighLight.Shape.RECTANGLE, options)
                                    .setLayoutRes(R.layout.view_guide_home)
                                    .setOnLayoutInflatedListener(new OnLayoutInflatedListener() {

                                        @Override
                                        public void onLayoutInflated(View view, Controller controller) {
                                            //可只创建一个引导layout并把相关内容都放在其中并GONE，获得ID并初始化相应为显示
                                            TextView tv = view.findViewById(R.id.text_about);
                                            tv.setVisibility(View.VISIBLE);
                                            tv.setText(R.string.guide_view_banner);
                                        }
                                    })

                    )
                    .addGuidePage(
                            GuidePage.newInstance().setEverywhereCancelable(true)
                                    .setBackgroundColor(0xbc000000)
                                    .addHighLightWithOptions(layoutView.findViewById(R.id.list_server), HighLight.Shape.ROUND_RECTANGLE, options2)
                                    .setLayoutRes(R.layout.view_guide_home)
                                    .setOnLayoutInflatedListener(new OnLayoutInflatedListener() {

                                        @Override
                                        public void onLayoutInflated(View view, Controller controller) {
                                            TextView tv = view.findViewById(R.id.text_abt_mid_right);
                                            tv.setVisibility(View.VISIBLE);
                                            tv.setText(R.string.guide_serverlist);
                                        }
                                    })
                    )
                    .addGuidePage(
                            GuidePage.newInstance().setEverywhereCancelable(true)
                                    .setBackgroundColor(0xbc000000)
                                    .setLayoutRes(R.layout.view_guide_home)
                                    .setOnLayoutInflatedListener(new OnLayoutInflatedListener() {

                                        @Override
                                        public void onLayoutInflated(View view, Controller controller) {
                                            TextView tv = view.findViewById(R.id.text_abt_mid_right);
                                            tv.setVisibility(View.VISIBLE);
                                            tv.setText(R.string.guide_serverlist);
                                        }
                                    })
                    )
                    .addGuidePage(
                            GuidePage.newInstance().setEverywhereCancelable(true)
                                    .setBackgroundColor(0xbc000000)
                                    .setLayoutRes(R.layout.view_guide_home)
                                    .setOnLayoutInflatedListener(new OnLayoutInflatedListener() {

                                        @Override
                                        public void onLayoutInflated(View view, Controller controller) {
                                            TextView tv = view.findViewById(R.id.text_abt_mid_right);
                                            tv.setVisibility(View.VISIBLE);
                                            tv.setText(R.string.guide_serverlist);
                                        }
                                    })
                    )
                    .addGuidePage(
                            GuidePage.newInstance().setEverywhereCancelable(true)
                                    .setBackgroundColor(0xbc000000)
                                    .addHighLightWithOptions(layoutView.findViewById(R.id.action_help), HighLight.Shape.RECTANGLE, options)
                                    .setLayoutRes(R.layout.view_guide_home)
                                    .setOnLayoutInflatedListener(new OnLayoutInflatedListener() {

                                        @Override
                                        public void onLayoutInflated(View view, Controller controller) {
                                            TextView tv = view.findViewById(R.id.text_abt_mid_left);
                                            tv.setVisibility(View.VISIBLE);
                                            tv.setText(R.string.guide_help);
                                        }
                                    })
                    )
                    .addGuidePage(
                            GuidePage.newInstance().setEverywhereCancelable(true)
                                    .setBackgroundColor(0xbc000000)
                                    .addHighLightWithOptions(layoutView.findViewById(R.id.nav_webpage), HighLight.Shape.RECTANGLE, options)
                                    .setLayoutRes(R.layout.view_guide_home)
                                    .setOnLayoutInflatedListener(new OnLayoutInflatedListener() {

                                        @Override
                                        public void onLayoutInflated(View view, Controller controller) {
                                            TextView tv = view.findViewById(R.id.text_abt_mid_left);
                                            tv.setVisibility(View.VISIBLE);
                                            tv.setText(R.string.guide_help);
                                        }
                                    })
                    )
                    //.alwaysShow(true)//总是显示，调试时可以打开
                    .show();
        } else if (scene == "joinRoom") {
            NewbieGuide.with(this)
                    .setLabel("joinRoomGuide")
                    .addGuidePage(
                            GuidePage.newInstance().setEverywhereCancelable(true)
                                    .setBackgroundColor(0xbc000000)
                                    .setLayoutRes(R.layout.view_guide_home)
                                    .setOnLayoutInflatedListener(new OnLayoutInflatedListener() {

                                        @Override
                                        public void onLayoutInflated(View view, Controller controller) {
                                            view.findViewById(R.id.view_abt_join_room).setVisibility(View.VISIBLE);
                                        }
                                    })

                    )
                    .show();
        }
    }*/
}
