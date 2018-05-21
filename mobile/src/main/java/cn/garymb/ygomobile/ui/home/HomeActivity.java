package cn.garymb.ygomobile.ui.home;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.NavigationView;
import android.support.v7.widget.DividerItemDecoration;
import android.support.v7.widget.LinearLayoutManager;
import android.text.TextUtils;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import com.base.bj.trpayjar.utils.TrPay;
import com.nightonke.boommenu.BoomButtons.BoomButton;
import com.nightonke.boommenu.BoomButtons.TextOutsideCircleButton;
import com.nightonke.boommenu.BoomMenuButton;
import com.qihoo.appstore.common.updatesdk.lib.UpdateHelper;
import com.tubb.smrv.SwipeMenuRecyclerView;

import org.chromium.mojo.bindings.MessageReceiver;
import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.util.List;

import cn.garymb.ygodata.YGOGameOptions;
import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.YGOStarter;
import cn.garymb.ygomobile.bean.ServerInfo;
import cn.garymb.ygomobile.bean.events.ServerInfoEvent;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.activities.AboutActivity;
import cn.garymb.ygomobile.ui.activities.BaseActivity;
import cn.garymb.ygomobile.ui.activities.WebActivity;
import cn.garymb.ygomobile.ui.adapters.ServerListAdapter;
import cn.garymb.ygomobile.ui.adapters.SimpleListAdapter;
import cn.garymb.ygomobile.ui.cards.CardSearchAcitivity;
import cn.garymb.ygomobile.ui.cards.DeckManagerActivity;
import cn.garymb.ygomobile.ui.online.MyCardActivity;
import cn.garymb.ygomobile.ui.plus.DefaultOnBoomListener;
import cn.garymb.ygomobile.ui.plus.DialogPlus;
import cn.garymb.ygomobile.ui.preference.SettingsActivity;
import cn.garymb.ygomobile.utils.AlipayPayUtils;
import libwindbot.windbot.WindBot;

abstract class HomeActivity extends BaseActivity implements NavigationView.OnNavigationItemSelectedListener{
    protected SwipeMenuRecyclerView mServerList;
    private ServerListAdapter mServerListAdapter;
    private ServerListManager mServerListManager;



    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);
        setExitAnimEnable(false);
        mServerList = $(R.id.list_server);
        mServerListAdapter = new ServerListAdapter(this);
        //server list
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this);
        mServerList.setLayoutManager(linearLayoutManager);
        DividerItemDecoration dividerItemDecoration =
                new DividerItemDecoration(this, DividerItemDecoration.VERTICAL);
        mServerList.addItemDecoration(dividerItemDecoration);
        mServerList.setAdapter(mServerListAdapter);
        mServerListManager = new ServerListManager(this, mServerListAdapter);
        mServerListManager.bind(mServerList);
        mServerListManager.syncLoadData();
        //windbot
        //WindBot.initAndroid(getPackageResourcePath(),);
        WindBot.initAndroid("data/data/cn.garymb.ygomobile/file","data/data/cn.garymb.ygomobile/file/cards.cdb");
        MessageReceiver mReceiver = new MessageReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction("RUN_WINDBOT");
        getContext().registerReceiver(mReceiver, filter);
        //event
        EventBus.getDefault().register(this);
        initBoomMenuButton($(R.id.bmb));
        //trpay
        TrPay.getInstance(HomeActivity.this).initPaySdk("e1014da420ea4405898c01273d6731b6","YGOMobile");
        //autoupadte checking
        checkForceUpdateSilent();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventBus.getDefault().unregister(this);
    }

    public class MessageReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals("RUN_WINDBOT")) {
                String args=intent.getStringExtra("args");
                WindBot.runAndroid(args);
            }
        }
    };

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onServerInfoEvent(ServerInfoEvent event) {
        if (event.delete) {
            DialogPlus dialogPlus = new DialogPlus(getContext());
            dialogPlus.setTitle(R.string.question);
            dialogPlus.setMessage(R.string.delete_server_info);
            dialogPlus.setMessageGravity(Gravity.CENTER_HORIZONTAL);
            dialogPlus.setLeftButtonListener((dialog, which) -> {
                mServerListManager.delete(event.position);
                dialog.dismiss();
            });
            dialogPlus.setCancelable(false);
            dialogPlus.setOnCloseLinster(null);
            dialogPlus.show();
        } else if (event.join) {
            joinRoom(event.position);
        } else {
            mServerListManager.showEditDialog(event.position);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (doMenu(item.getItemId())) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        if (doMenu(item.getItemId())) {
            return true;
        }
        return false;
    }

    @Override
    public HomeActivity getActivity() {
        return this;
    }

    @Override
    public void startActivity(Intent intent) {
        super.startActivity(intent);
    }

    private boolean doMenu(int id) {
        switch (id) {
            case R.id.nav_donation: {

                final  DialogPlus dialog = new DialogPlus(getContext());
                dialog.setContentView(R.layout.dialog_alipay_or_wechat);
                dialog.setTitle(R.string.logo_text);
                dialog.show();
                View viewDialog= dialog.getContentView();
                Button btnalipay= viewDialog.findViewById(R.id.button_alipay);
                Button btnwechat= viewDialog.findViewById(R.id.button_wechat);

                btnalipay.setOnClickListener((v) -> {
                    AlipayPayUtils.openAlipayPayPage(getContext(), Constants.ALIPAY_URL);
                    dialog.dismiss();
//                Intent intent = new Intent(this, AboutActivity.class);
                    //               startActivity(intent);
                });
                btnwechat.setOnClickListener((v) -> {
                    AlipayPayUtils.inputMoney(HomeActivity.this);
                    dialog.dismiss();
                });

            }
            break;
            case R.id.action_game:
                openGame();
                break;
            case R.id.action_settings: {
                Intent intent = new Intent(this, SettingsActivity.class);
                startActivity(intent);
            }
            break;
            case R.id.action_quit: {
//                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                DialogPlus builder = new DialogPlus(this);
                builder.setTitle(R.string.question);
                builder.setMessage(R.string.quit_tip);
                builder.setMessageGravity(Gravity.CENTER_HORIZONTAL);
                builder.setLeftButtonListener((dlg, s) -> {
                    dlg.dismiss();
                    finish();
                });
                builder.show();
            }
            break;
            case R.id.action_add_server:
                mServerListManager.addServer();
                break;
            case R.id.action_card_search:
                startActivity(new Intent(this, CardSearchAcitivity.class));
                break;
            case R.id.action_deck_manager:
                startActivity(new Intent(this, DeckManagerActivity.getDeckManager()));
                break;
            case R.id.action_mycard:
                if (Constants.SHOW_MYCARD) {
                    startActivity(new Intent(this, MyCardActivity.class));
                }
                break;
            case R.id.action_help: {
                WebActivity.open(this, getString(R.string.help), Constants.URL_HELP);
            }
            break;
            case R.id.action_reset_game_res:
                updateImages();
                break;
            default:
                return false;
        }
        return true;
    }

    long exitLasttime = 0;

    @Override
    public void onBackPressed() {
        if (System.currentTimeMillis() - exitLasttime <= 3000) {
            super.onBackPressed();
        } else {
            showToast(R.string.back_tip, Toast.LENGTH_SHORT);
            exitLasttime = System.currentTimeMillis();
        }
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
        SimpleListAdapter simpleListAdapter = new SimpleListAdapter(getContext());
        simpleListAdapter.set(AppsSettings.get().getLastRoomList());
        listView.setAdapter(simpleListAdapter);
        listView.setOnItemClickListener((a, v, pos, index) -> {
//                builder.dismiss();
            String name = simpleListAdapter.getItemById(index);
            editText.setText(name);
//                joinGame(serverInfo, name);
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
        });
        builder.setOnCloseLinster((dlg) -> {
            dlg.dismiss();
        });
        builder.setOnCancelListener((dlg) -> {
        });
        builder.show();
    }

    void joinGame(ServerInfo serverInfo, String name) {
        YGOGameOptions options = new YGOGameOptions();
        options.mServerAddr = serverInfo.getServerAddr();
        options.mUserName = serverInfo.getPlayerName();
        options.mPort = serverInfo.getPort();
        options.mRoomName = name;
        YGOStarter.startGame(this, options);
    }

    protected abstract void checkResourceDownload(ResCheckTask.ResCheckListener listener);

    protected abstract void openGame();

    public abstract void updateImages();

    private void initBoomMenuButton(BoomMenuButton menu) {
        final SparseArray<Integer> mMenuIds = new SparseArray<>();
        addMenuButton(mMenuIds, menu, R.id.action_game, R.string.action_game, R.drawable.start);
        addMenuButton(mMenuIds, menu, R.id.action_card_search, R.string.tab_search, R.drawable.search);
        addMenuButton(mMenuIds, menu, R.id.action_deck_manager, R.string.deck_manager, R.drawable.deck);

        addMenuButton(mMenuIds, menu, R.id.action_add_server, R.string.action_add_server, R.drawable.addsever);
        addMenuButton(mMenuIds, menu, R.id.action_mycard, R.string.mycard, R.drawable.mycard);
        addMenuButton(mMenuIds, menu, R.id.action_help, R.string.help, R.drawable.help);

        addMenuButton(mMenuIds, menu, R.id.action_reset_game_res, R.string.reset_game_res, R.drawable.downloadimages);
        addMenuButton(mMenuIds, menu, R.id.action_settings, R.string.settings, R.drawable.setting);
        addMenuButton(mMenuIds, menu, R.id.nav_donation, R.string.donation, R.drawable.about);

        menu.setOnBoomListener(new DefaultOnBoomListener() {
            @Override
            public void onClicked(int index, BoomButton boomButton) {
                doMenu(mMenuIds.get(index));
            }
        });
    }

    private void addMenuButton(SparseArray<Integer> mMenuIds, BoomMenuButton menuButton, int menuId, int stringId, int image) {
        addMenuButton(mMenuIds, menuButton, menuId, getString(stringId), image);
    }

    private void addMenuButton(SparseArray<Integer> mMenuIds, BoomMenuButton menuButton, int menuId, String str, int image) {
        TextOutsideCircleButton.Builder builder = new TextOutsideCircleButton.Builder()
                .shadowColor(Color.TRANSPARENT)
                .normalColor(Color.TRANSPARENT)
                .normalImageRes(image)
                .normalText(str);
        menuButton.addBuilder(builder);
        mMenuIds.put(mMenuIds.size(), menuId);
    }

    private void checkForceUpdateSilent() {
        UpdateHelper.getInstance().init(getApplicationContext(), Color.parseColor("#0A93DB"));
        UpdateHelper.getInstance().setDebugMode(false);
        long intervalMillis = 0 * 1000L;
        UpdateHelper.getInstance().autoUpdate(getPackageName(), false, intervalMillis);
    }

}
