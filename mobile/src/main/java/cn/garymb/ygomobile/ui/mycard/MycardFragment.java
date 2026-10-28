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
 * 萌卡（MyCard）主页 Fragment（拆分后的具体叶子类，保持原类名）。
 *
 * <p>原单一文件共 2298 行，为符合单类规模规范拆为继承链：
 * {@link MycardFragmentBase} ← {@link MycardFragmentAccount} ← {@link MycardFragmentNavigation} ← 本类。
 * 本类持有观战/等待房间列表、自定义建房对话框、Spinner 工具与页签切换等叶子逻辑。
 * 外部调用方式（HomeActivity 实例化、updateToolBarButtonStatePublic 等）与拆分前完全一致。</p>
 */
public class MycardFragment extends MycardFragmentNavigation {
    protected void initWaitingRoomView(View view) {
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

                        Log.i("WaitingDuel", "u16SecretStr: " + u16SecretStr);

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
                                    YGOUtil.show(R.string.unknown_room);
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
                                YGOUtil.show(YGOUtil.s(R.string.start_game_error) + e.getMessage());
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

    protected void loadWaitingRooms() {
        if (srl_waiting != null) {
            srl_waiting.setRefreshing(true);
        }

        if (waitingDuelManagement != null) {
            waitingDuelManagement.start();
        }
    }

    protected void showCreateRoomDialog() {
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

    protected void showCreateRoomDialog(List<YGOServer> servers) {
        DialogPlus dialog = new DialogPlus(requireContext());
        dialog.setContentView(R.layout.dialog_mycard_custom_mode_select_with_tabs);
        dialog.setTitle(R.string.create_custom_room);

        TextView serverLabel = dialog.findViewById(R.id.tv_create_room_server);
        Spinner serverSpinner = dialog.findViewById(R.id.spinner_create_room_server);

        TabLayout tabLayout = dialog.findViewById(R.id.tl_room_n_bot);
        View tabRoomSettings = dialog.findViewById(R.id.tab_room_settings);
        View tabAiList = dialog.findViewById(R.id.tab_ai_list);
        View tabJoinPrivateRoom = dialog.findViewById(R.id.tab_join_private_room);
        EditText etPrivateRoomPassword = dialog.findViewById(R.id.et_private_room_password);
        ListView lvAiList = dialog.findViewById(R.id.lv_ai_list);
        TextView tvAiEmpty = dialog.findViewById(R.id.tv_ai_empty);

        EditText titleEdit = dialog.findViewById(R.id.et_custom_room_title);
        TextView titleCount = dialog.findViewById(R.id.tv_custom_room_title_count);
        Spinner ruleSpinner = dialog.findViewById(R.id.spinner_custom_room_rule);
        Spinner modeSpinner = dialog.findViewById(R.id.spinner_custom_room_mode);
        Spinner duelRuleSpinner = dialog.findViewById(R.id.spinner_custom_room_duel_rule);
        EditText startLpEdit = dialog.findViewById(R.id.et_custom_room_start_lp);
        EditText startHandEdit = dialog.findViewById(R.id.et_custom_room_start_hand);
        EditText drawCountEdit = dialog.findViewById(R.id.et_custom_room_draw_count);
        CheckBox privateBox = dialog.findViewById(R.id.cb_custom_room_private);

        CheckBox autoDeathBox = dialog.findViewById(R.id.cb_custom_room_auto_death);
        CheckBox noCheckDeckBox = dialog.findViewById(R.id.cb_custom_room_no_check_deck);
        CheckBox noShuffleDeckBox = dialog.findViewById(R.id.cb_custom_room_no_shuffle_deck);
        noCheckDeckBox.setText(mStringManager.getSystemString(1229, ""));
        noShuffleDeckBox.setText(mStringManager.getSystemString(1230, ""));

        TextView tv_custom_room_start_LP = dialog.findViewById(R.id.tv_custom_room_start_LP);
        TextView tv_custom_room_start_hand = dialog.findViewById(R.id.tv_custom_room_start_hand);
        TextView tv_custom_room_draw_count = dialog.findViewById(R.id.tv_custom_room_draw_count);
        TextView tv_custom_room_rule = dialog.findViewById(R.id.tv_custom_room_rule);
        tv_custom_room_start_LP.setText(mStringManager.getSystemString(1231, ""));
        tv_custom_room_start_hand.setText(mStringManager.getSystemString(1232, ""));
        tv_custom_room_draw_count.setText(mStringManager.getSystemString(1233, ""));
        tv_custom_room_rule.setText(mStringManager.getSystemString(1236, ""));


        titleEdit.setFilters(new InputFilter[]{new InputFilter.LengthFilter(12)});
        titleEdit.setText(mMcUser.getUsername() + YGOUtil.s(R.string.s_room));
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

        setupServerSpinner(serverSpinner, servers);
        setupRuleSpinner(ruleSpinner);
        setupModeSpinner(modeSpinner);
        setupDuelRuleSpinner(duelRuleSpinner);

        int defaultServerIndex = getDefaultServerIndex(servers);
        serverSpinner.setSelection(defaultServerIndex);
        serverLabel.setText(YGOUtil.s(R.string.server_area) + " " + servers.get(defaultServerIndex).getName());

        List<String> currentAiList = new ArrayList<>();
        ArrayAdapter<String> aiAdapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_list_item_1, currentAiList);
        lvAiList.setAdapter(aiAdapter);

        tabLayout.addTab(tabLayout.newTab().setText(R.string.settings_game).setIcon(R.drawable.setting));
        tabLayout.addTab(tabLayout.newTab().setText(R.string.mycard_ai_battle).setIcon(R.drawable.bot));
        tabLayout.addTab(tabLayout.newTab().setText(R.string.mycard_join_private_room).setIcon(R.drawable.ic_search));

        int[] currentTab = {0};

        tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                currentTab[0] = tab.getPosition();
                if (tab.getPosition() == 0) {
                    tabRoomSettings.setVisibility(View.VISIBLE);
                    tabAiList.setVisibility(View.GONE);
                    tabJoinPrivateRoom.setVisibility(View.GONE);
                    dialog.hideButton(false);
                } else if (tab.getPosition() == 1) {
                    tabRoomSettings.setVisibility(View.GONE);
                    tabAiList.setVisibility(View.VISIBLE);
                    tabJoinPrivateRoom.setVisibility(View.GONE);
                    dialog.hideButton(true);
                } else {
                    tabRoomSettings.setVisibility(View.GONE);
                    tabAiList.setVisibility(View.GONE);
                    tabJoinPrivateRoom.setVisibility(View.VISIBLE);
                    dialog.hideButton(false);
                }
            }

            @Override
            public void onTabUnselected(TabLayout.Tab tab) {
            }

            @Override
            public void onTabReselected(TabLayout.Tab tab) {
            }
        });

        serverSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                serverLabel.setText(YGOUtil.s(R.string.server_area) + " " + servers.get(position).getName());

                YGOServer selectedServer = servers.get(position);
                currentAiList.clear();
                if (selectedServer.getWindbot() != null && !selectedServer.getWindbot().isEmpty()) {
                    currentAiList.add(getString(R.string.random));
                    currentAiList.addAll(selectedServer.getWindbot());
                    tvAiEmpty.setVisibility(View.GONE);
                    lvAiList.setVisibility(View.VISIBLE);
                } else {
                    tvAiEmpty.setVisibility(View.VISIBLE);
                    lvAiList.setVisibility(View.GONE);
                }
                aiAdapter.notifyDataSetChanged();
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
                titleEdit.setText(mMcUser.getUsername() + YGOUtil.s(R.string.s_room));
                titleCount.setVisibility(View.VISIBLE);
            }
        });

        lvAiList.setOnItemClickListener((parent, view, position, id) -> {
            if (position >= 0 && position < currentAiList.size()) {
                String selectedAi = currentAiList.get(position);
                YGOServer selectedServer = servers.get(serverSpinner.getSelectedItemPosition());
                if (position == 0 && currentAiList.size() > 1) {
                    int randomIndex = 1 + (int) (Math.random() * (currentAiList.size() - 1));
                    selectedAi = currentAiList.get(randomIndex);
                }

                dialog.dismiss();
                joinMyCardWindbot(selectedServer, selectedAi);
            }
        });

        dialog.setLeftButtonListener((dlg, s) -> {
            if (currentTab[0] == 2) {
                String privatePassword = etPrivateRoomPassword.getText().toString().trim();
                if (TextUtils.isEmpty(privatePassword)) {
                    YGOUtil.show(R.string.mycard_private_room_password);
                    return;
                }
                YGOServer server = servers.get(serverSpinner.getSelectedItemPosition());
                joinPrivateCustomRoom(server, privatePassword);
                dialog.dismiss();
            } else {
                boolean privateRoom = privateBox.isChecked();
                String title = titleEdit.getText().toString().trim();
                if (!privateRoom && TextUtils.isEmpty(title)) {
                    YGOUtil.show(R.string.input_room_name);
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
            }
        });
        dialog.setLeftButtonText(R.string.create_custom_room);

        dialog.show();
    }

    protected void createCustomRoom(DialogPlus dialog, YGOServer server, DuelRoom.OptionsBean options, String title, boolean privateRoom) {
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
                        YGOUtil.copyMessage(activity, hostPassword);
                        YGOUtil.show(YGOUtil.s(R.string.mycard_private_room_copied) + hostPassword);
                    }
                });
            } catch (Exception e) {
                Log.e("MyCard", "创建自定义房间失败: " + e);
                Activity activity = getActivity();
                if (activity != null) {
                    activity.runOnUiThread(() -> {
                        YGOUtil.show(YGOUtil.s(R.string.create_room_failed) + e.getMessage());
                    });
                }
            }
        }).start();
    }

    protected void showJoinPrivateRoomDialog(DialogPlus parentDialog, YGOServer server) {
        EditText input = new EditText(requireContext());
        input.setSingleLine(true);
        input.setHint(R.string.mycard_private_room_password);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        int padding = YGOUtil.dp2px(20);
        input.setPadding(padding, 0, padding, 0);

        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.mycard_join_private_room)
                .setView(input)
                .setNegativeButton(R.string.Cancel, null)
                .setPositiveButton(R.string.quick_join, (dialog, which) -> {
                    String privatePassword = input.getText().toString().trim();
                    if (TextUtils.isEmpty(privatePassword)) {
                        YGOUtil.show(R.string.mycard_private_room_password);
                        return;
                    }
                    parentDialog.dismiss();
                    joinPrivateCustomRoom(server, privatePassword);
                })
                .show();
    }

    protected void joinPrivateCustomRoom(YGOServer server, String privatePassword) {
        if (!isUserLoggedIn()) {
            YGOUtil.showTextToast(R.string.login_mycard);
            return;
        }

        new Thread(() -> {
            try {
                String token = SharedPreferenceUtil.getServerToken();
                if (TextUtils.isEmpty(token)) {
                    throw new IOException("token not found");
                }

                int u16Secret = MyCard.getUserU16Secret(token);
                String password = MyCard.createPrivateRoomJoinPassword(privatePassword, u16Secret);
                Activity activity = getActivity();
                if (activity == null) {
                    return;
                }

                activity.runOnUiThread(() -> {
                    server.setPlayerName(mMcUser.getUsername());
                    YGOUtil.joinGame(activity, server, password);
                });
            } catch (Exception e) {
                Log.e("MyCard", "加入私密房间失败: " + e);
                Activity activity = getActivity();
                if (activity != null) {
                    activity.runOnUiThread(() -> YGOUtil.show(YGOUtil.s(R.string.start_game_error) + e.getMessage()));
                }
            }
        }).start();
    }

    protected void setupServerSpinner(Spinner spinner, List<YGOServer> servers) {
        List<SimpleSpinnerItem> items = new ArrayList<>();
        for (int i = 0; i < servers.size(); i++) {
            YGOServer server = servers.get(i);
            String displayName = getServerDisplayName(server);
            items.add(new SimpleSpinnerItem(i, displayName));
        }

        SimpleSpinnerAdapter adapter = new SimpleSpinnerAdapter(requireContext());
        adapter.setColor(Color.WHITE);
        adapter.set(items);
        spinner.setPopupBackgroundResource(R.color.colorNavy);
        spinner.setAdapter(adapter);
    }

    protected void setupRuleSpinner(Spinner spinner) {
        List<SimpleSpinnerItem> items = new ArrayList<>();
        int[] ruleIds = {1481, 1482, 1483, 1484, 1485, 1486};

        for (int i = 0; i < ruleIds.length; i++) {
            String text = mStringManager.getSystemString(ruleIds[i], "");
            items.add(new SimpleSpinnerItem(i, text));
        }

        SimpleSpinnerAdapter adapter = new SimpleSpinnerAdapter(requireContext());
        adapter.setColor(Color.WHITE);
        adapter.set(items);
        spinner.setPopupBackgroundResource(R.color.colorNavy);
        spinner.setAdapter(adapter);
    }

    protected void setupModeSpinner(Spinner spinner) {
        List<SimpleSpinnerItem> items = new ArrayList<>();
        int[] modeIds = {1244, 1245, 1246};

        for (int i = 0; i < modeIds.length; i++) {
            String text = mStringManager.getSystemString(modeIds[i], "");
            items.add(new SimpleSpinnerItem(i, text));
        }

        SimpleSpinnerAdapter adapter = new SimpleSpinnerAdapter(requireContext());
        adapter.setColor(Color.WHITE);
        adapter.set(items);
        spinner.setSelection(DuelRoom.MODE_MATCH);
        spinner.setPopupBackgroundResource(R.color.colorNavy);
        spinner.setAdapter(adapter);
    }

    protected void setupDuelRuleSpinner(Spinner spinner) {
        List<SimpleSpinnerItem> items = new ArrayList<>();
        int[] duelRuleIds = {1260, 1261, 1262, 1263, 1264};

        for (int i = 0; i < duelRuleIds.length; i++) {
            String text = mStringManager.getSystemString(duelRuleIds[i], "");
            items.add(new SimpleSpinnerItem(i, text));
        }

        SimpleSpinnerAdapter adapter = new SimpleSpinnerAdapter(requireContext());
        adapter.setColor(Color.WHITE);
        adapter.set(items);
        spinner.setPopupBackgroundResource(R.color.colorNavy);
        spinner.setAdapter(adapter);
        spinner.setSelection(4);
    }

    protected String[] getServerNames(List<YGOServer> servers) {
        String[] names = new String[servers.size()];
        for (int i = 0; i < servers.size(); i++) {
            names[i] = getServerDisplayName(servers.get(i));
        }
        return names;
    }

    protected String getServerDisplayName(YGOServer server) {
        if (server == null) {
            return "";
        }
        if (!TextUtils.isEmpty(server.getName())) {
            return server.getName();
        }
        return server.getServerAddr() + ":" + server.getPort();
    }

    protected int getDefaultServerIndex(List<YGOServer> servers) {
        YGOServer defaultServer = MyCard.getDefaultCustomServer(servers);
        for (int i = 0; i < servers.size(); i++) {
            YGOServer server = servers.get(i);
            if (!TextUtils.isEmpty(server.getId()) && server.getId().equals(defaultServer.getId())) {
                return i;
            }
        }
        return 0;
    }

    protected int readInt(EditText editText, int min, int max, int defaultValue) {
        try {
            int value = Integer.parseInt(editText.getText().toString().trim());
            return Math.max(min, Math.min(max, value));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    protected void setupTabLayout(View view) {
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
