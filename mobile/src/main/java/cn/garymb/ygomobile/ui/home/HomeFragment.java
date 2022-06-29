package cn.garymb.ygomobile.ui.home;

import static cn.garymb.ygomobile.Constants.ASSET_SERVER_LIST;
import static cn.garymb.ygomobile.Constants.ORI_DECK;
import static cn.garymb.ygomobile.Constants.ORI_PICS;
import static cn.garymb.ygomobile.Constants.ORI_REPLAY;
import static cn.garymb.ygomobile.ui.home.ResCheckTask.getDatapath;

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
import androidx.cardview.widget.CardView;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;

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
import cn.garymb.ygomobile.bean.ServerInfo;
import cn.garymb.ygomobile.bean.ServerList;
import cn.garymb.ygomobile.bean.events.ServerInfoEvent;
import cn.garymb.ygomobile.lite.BuildConfig;
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
import cn.garymb.ygomobile.utils.FileUtils;
import cn.garymb.ygomobile.utils.IOUtils;
import ocgcore.CardManager;
import ocgcore.DataManager;
import ocgcore.data.Card;


public class HomeFragment extends BaseFragemnt implements View.OnClickListener {
    private static final int ID_HOMEFRAGMENT = 0;

    private static final int TYPE_BANNER_QUERY_OK = 0;
    private static final int TYPE_BANNER_QUERY_EXCEPTION = 1;
    private static final int TYPE_RES_LOADING_OK = 2;
    private static final String ARG_MC_NEWS_LIST = "mcNewsList";
    private boolean isMcNewsLoadException = false;

    ShimmerTextView tv;
    Shimmer shimmer;
    protected SwipeMenuRecyclerView mServerList;
    private ServerListAdapter mServerListAdapter;
    private ServerListManager mServerListManager;
    private CardManager mCardManager;
    private CardDetailRandom mCardDetailRandom;
    private ImageLoader mImageLoader;
    //轮播图
    private CardView cv_banner;
    private TextView tv_banner_loading;
    private XBanner xb_banner;
    private ArrayList<McNews> mcNewsList;
    //ygopro功能
    private CardView cv_game;
    private CardView cv_bot_game;
    private CardView cv_watch_replay;
    //辅助功能
    private CardView cv_download_ex;
    private CardView cv_reset_res;
    //外连
    private CardView cv_donation;
    private CardView cv_join_QQ;
    private CardView cv_help;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        View layoutView;
        if (isHorizontal)
            layoutView = inflater.inflate(R.layout.main_horizontal_fragment, container, false);
        else
            layoutView = inflater.inflate(R.layout.fragment_home, container, false);
        initBanner(layoutView, savedInstanceState);
        initView(layoutView);
        //event
        if(!EventBus.getDefault().isRegistered(this)){//加上判断
            EventBus.getDefault().register(this);
        }
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
        /*
        cv_download_ex.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                startActivity(new Intent(getActivity(), FileLogActivity.class));
                return true;
            }
        });
        cv_reset_res = view.findViewById(R.id.action_reset_game_res);
        cv_reset_res.setOnClickListener(this);
        cv_join_QQ = view.findViewById(R.id.action_join_qq_group);
        cv_join_QQ.setOnClickListener(this);*/
        cv_donation = view.findViewById(R.id.nav_webpage);
        cv_donation.setOnClickListener(this);
        cv_help = view.findViewById(R.id.action_help);
        cv_help.setOnClickListener(this);

        tv = (ShimmerTextView) view.findViewById(R.id.shimmer_tv);
        toggleAnimation(tv);
        mImageLoader = new ImageLoader(false);
        mCardManager = DataManager.get().getCardManager();
    }

    //轮播图
    public void initBanner(View view, Bundle saveBundle){
        xb_banner = view.findViewById(R.id.xb_banner);
        cv_banner = view.findViewById(R.id.cv_banner);
        tv_banner_loading = view.findViewById(R.id.tv_banner_loading);
        tv_banner_loading.setOnClickListener(this);
        cv_banner.post(() -> {
            ViewGroup.LayoutParams layoutParams = cv_banner.getLayoutParams();
            layoutParams.width = cv_banner.getWidth();
            layoutParams.height = layoutParams.width / 3;
            cv_banner.setLayoutParams(layoutParams);
        });
        xb_banner.setOnItemClickListener((banner, model, v, position) ->
                WebActivity.open(getContext(), "新闻", mcNewsList.get(position).getNews_url())
        );
        xb_banner.loadImage((banner, model, v, position) -> {
            TextView tv_time, tv_title, tv_type;
            ImageView iv_image;

            tv_time = v.findViewById(R.id.tv_time);
            tv_title = v.findViewById(R.id.tv_title);
            tv_type = v.findViewById(R.id.tv_type);
            iv_image = v.findViewById(R.id.iv_image);

            McNews mcNews = mcNewsList.get(position);
            ImageUtil.setImageAndBackground(getContext(), mcNews.getImage_url(), iv_image);
            tv_time.setText(mcNews.getCreate_time());
            tv_title.setText(mcNews.getTitle());
            tv_type.setVisibility(View.GONE);
        });
        if (saveBundle == null) {
            findMcNews();
        } else {
            HomeFragment.this.mcNewsList = (ArrayList<McNews>) saveBundle.getSerializable(ARG_MC_NEWS_LIST);
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
                    tv_banner_loading.setText("加载失败，点击重试");
                    isMcNewsLoadException = true;
                    break;
            }

        }
    };

    private void findMcNews() {
        isMcNewsLoadException = false;
        tv_banner_loading.setVisibility(View.VISIBLE);
        tv_banner_loading.setText("加载中");
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
        builder.setContentView(R.layout.dialog_room_name);
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
        tv.setOnClickListener((v) -> {
            openGame();
        });
        if (YGOStarter.isGameRunning(getActivity())) {
            tv.setVisibility(View.VISIBLE);
        } else {
            tv.setVisibility(View.GONE);
        }
    }

    public void toggleAnimation(View target) {
        if (shimmer != null && shimmer.isAnimating()) {
            shimmer.cancel();
        } else {
            shimmer = new Shimmer();
            shimmer.start(tv);
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
    }

    public void updateImages() {
        Log.e("MainActivity", "重置资源");
        DialogPlus dialog = DialogPlus.show(getContext(), null, getString(R.string.message));
        dialog.show();
        VUiKit.defer().when(() -> {
            Log.e("MainActivity", "开始复制");
            try {
                IOUtils.createNoMedia(AppsSettings.get().getResourcePath());

                FileUtils.delFile(AppsSettings.get().getResourcePath() + "/" + Constants.CORE_SCRIPT_PATH);

                if (IOUtils.hasAssets(getContext(), getDatapath(Constants.CORE_PICS_ZIP))) {
                    IOUtils.copyFilesFromAssets(getContext(), getDatapath(Constants.CORE_PICS_ZIP),
                            AppsSettings.get().getResourcePath(), true);
                }
                if (IOUtils.hasAssets(getContext(), getDatapath(Constants.CORE_SCRIPTS_ZIP))) {
                    IOUtils.copyFilesFromAssets(getContext(), getDatapath(Constants.CORE_SCRIPTS_ZIP),
                            AppsSettings.get().getResourcePath(), true);
                }
                IOUtils.copyFilesFromAssets(getContext(), getDatapath(Constants.DATABASE_NAME),
                        AppsSettings.get().getResourcePath(), true);

                IOUtils.copyFilesFromAssets(getContext(), getDatapath(Constants.CORE_STRING_PATH),
                        AppsSettings.get().getResourcePath(), true);

                IOUtils.copyFilesFromAssets(getContext(), getDatapath(Constants.WINDBOT_PATH),
                        AppsSettings.get().getResourcePath(), true);

                IOUtils.copyFilesFromAssets(getContext(), getDatapath(Constants.CORE_SKIN_PATH),
                        AppsSettings.get().getCoreSkinPath(), false);
                String fonts = AppsSettings.get().getResourcePath() + "/" + Constants.FONT_DIRECTORY;
                if (new File(fonts).list() != null)
                    FileUtils.delFile(fonts);
                IOUtils.copyFilesFromAssets(getContext(), getDatapath(Constants.FONT_DIRECTORY),
                        AppsSettings.get().getFontDirPath(), true);
                /*
                IOUtils.copyFilesFromAssets(this, getDatapath(Constants.CORE_SOUND_PATH),
                        AppsSettings.get().getSoundPath(), false);*/

                //复制原目录文件
                if (new File(ORI_DECK).list() != null)
                    FileUtils.copyDir(ORI_DECK, AppsSettings.get().getDeckDir(), false);
                if (new File(ORI_REPLAY).list() != null)
                    FileUtils.copyDir(ORI_REPLAY, AppsSettings.get().getResourcePath() + "/" + Constants.CORE_REPLAY_PATH, false);
                if (new File(ORI_PICS).list() != null)
                    FileUtils.copyDir(ORI_PICS, AppsSettings.get().getCardImagePath(), false);
            } catch (IOException e) {
                e.printStackTrace();
                Log.e("MainActivity", "错误" + e);
            }
        }).done((rs) -> {
            Toast.makeText(getContext(), R.string.done, Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        });
    }

    public boolean joinQQGroup(String key) {
        Intent intent = new Intent();
        intent.setData(Uri.parse("mqqopensdkapi://bizAgent/qm/qr?url=http%3A%2F%2Fqm.qq.com%2Fcgi-bin%2Fqm%2Fqr%3Ffrom%3Dapp%26p%3Dandroid%26k%3D" + key));
        // 此Flag可根据具体产品需要自定义，如设置，则在加群界面按返回，返回手Q主界面，不设置，按返回会返回到呼起产品界面    //intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(intent);
            return true;
        } catch (Exception e) {
            // 未安装手Q或安装的版本不支持
            return false;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        BacktoDuel();
        //server list
        mServerListManager.syncLoadData();
    }

    @Override
    public void onDestroy() {
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
    public void onBackPressed() {

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
                break;
            case R.id.action_bot:
                YGOStarter.startGame(getActivity(), null, "-k", "-s");
                break;
            case R.id.action_download_ex:
                WebActivity.open(getContext(), getString(R.string.action_download_expansions), Constants.URL_YGO233_ADVANCE);
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
            break;/*
            case R.id.action_join_qq_group:
                String key = "anEjPCDdhLgxtfLre-nT52G1Coye3LkK";
                joinQQGroup(key);
                break;
            case R.id.action_reset_game_res:
                updateImages();
                break;*/
            case R.id.nav_webpage: {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse(BuildConfig.URL_DONATE));
                startActivity(intent);
            }
            break;
            case R.id.tv_banner_loading:
                if (isMcNewsLoadException)
                    findMcNews();
                break;
        }
    }
}
