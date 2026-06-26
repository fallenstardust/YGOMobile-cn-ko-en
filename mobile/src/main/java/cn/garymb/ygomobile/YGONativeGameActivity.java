package cn.garymb.ygomobile;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygodata.YGOGameOptions;
import cn.garymb.ygomobile.audio.SoundManager;
import cn.garymb.ygomobile.game.GameEngine;
import cn.garymb.ygomobile.game.GameField;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.render.GameFieldView;
import cn.garymb.ygomobile.render.TextureLoader;
import cn.garymb.ygomobile.ui.adapters.SimpleListAdapter;
import cn.garymb.ygomobile.ui.plus.DialogPlus;
import ocgcore.DataManager;
import ocgcore.data.Card;
import ocgcore.enums.DuelPhase;

public class YGONativeGameActivity extends AppCompatActivity implements
        GameEngine.EngineListener,
        GameFieldView.OnCardClickListener {

    private static final String TAG = "YGONativeGame";
    private static final int PRO_VERSION = 0x1362;

    private GameEngine engine;
    private SoundManager soundManager;
    private ImageLoader imageLoader;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private GameFieldView gameFieldView;
    private TextView tvPlayerLp, tvPlayerName, tvOpponentLp, tvOpponentName;
    private TextView tvPhaseInfo, tvTurnInfo;
    private TextView tvPlayerTime, tvOpponentTime;
    private TextView tvHintMessage;
    private TextView tvChatLog;
    private TextView tvPlayerHandCount, tvOpponentHandCount;
    private ImageView ivPlayerAvatar, ivOpponentAvatar;
    private LinearLayout layoutActionButtons;
    private Button btnM2, btnEp, btnBp, btnChain, btnCancel;
    private LinearLayout layoutChat;
    private EditText etChatInput;
    private FrameLayout dialogContainer;
    private LinearLayout layoutLobby;
    private TextView tvLobbyStatus;
    private Button btnLobbyReady, btnLobbyLeave;

    private RelativeLayout layoutMainMenu;
    private TextView tvVersion;
    
    // 添加对游戏界面元素的引用
    private LinearLayout layoutOpponentInfo;
    private LinearLayout layoutPlayerInfo;

    private String chatHistory = "";
    private boolean isMyTurn = false;
    private volatile boolean isGameStarted = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        setupFullScreen();
        setContentView(R.layout.activity_ygo_game);

        initViews();
        initEngine();
        loadData();
        setupBackPressedHandler();

        if (!handleDirectIntent(getIntent())) {
            showMainMenu();
        }
    }

    private void setupFullScreen() {
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    private void initViews() {
        gameFieldView = findViewById(R.id.game_field_view);
        gameFieldView.setCardClickListener(this);

        tvPlayerLp = findViewById(R.id.tv_player_lp);
        tvPlayerName = findViewById(R.id.tv_player_name);
        tvOpponentLp = findViewById(R.id.tv_opponent_lp);
        tvOpponentName = findViewById(R.id.tv_opponent_name);
        tvPhaseInfo = findViewById(R.id.tv_phase_info);
        tvTurnInfo = findViewById(R.id.tv_turn_info);
        tvPlayerTime = findViewById(R.id.tv_player_time);
        tvOpponentTime = findViewById(R.id.tv_opponent_time);
        tvHintMessage = findViewById(R.id.tv_hint_message);
        tvChatLog = findViewById(R.id.tv_chat_log);
        tvPlayerHandCount = findViewById(R.id.tv_opponent_hand_count);
        ivPlayerAvatar = findViewById(R.id.iv_player_avatar);
        ivOpponentAvatar = findViewById(R.id.iv_opponent_avatar);

        layoutActionButtons = findViewById(R.id.layout_action_buttons);
        btnM2 = findViewById(R.id.btn_m2);
        btnEp = findViewById(R.id.btn_ep);
        btnBp = findViewById(R.id.btn_bp);
        btnChain = findViewById(R.id.btn_chain);
        btnCancel = findViewById(R.id.btn_cancel);

        layoutChat = findViewById(R.id.layout_chat);
        etChatInput = findViewById(R.id.et_chat_input);
        Button btnChatSend = findViewById(R.id.btn_chat_send);

        dialogContainer = findViewById(R.id.dialog_container);
        layoutLobby = findViewById(R.id.layout_lobby);
        tvLobbyStatus = findViewById(R.id.tv_lobby_status);
        btnLobbyReady = findViewById(R.id.btn_lobby_ready);
        btnLobbyLeave = findViewById(R.id.btn_lobby_leave);
        
        // 保存游戏界面元素的引用
        layoutOpponentInfo = findViewById(R.id.layout_opponent_info);
        layoutPlayerInfo = findViewById(R.id.layout_player_info);

        setupButtonListeners(btnChatSend);
        setupAvatarImages();
    }

    private void setupAvatarImages() {
        Bitmap myAvatar = TextureLoader.get().getAvatar(true);
        if (myAvatar != null) ivPlayerAvatar.setImageBitmap(myAvatar);
        Bitmap opAvatar = TextureLoader.get().getAvatar(false);
        if (opAvatar != null) ivOpponentAvatar.setImageBitmap(opAvatar);
    }

    private void setupButtonListeners(Button btnChatSend) {
        btnM2.setOnClickListener(v -> {
            sendActionResponse(2);
        });
        btnEp.setOnClickListener(v -> {
            sendActionResponse(3);
        });
        btnBp.setOnClickListener(v -> {
            sendActionResponse(1);
        });
        btnChain.setOnClickListener(v -> {
            sendActionResponse(4);
        });
        btnCancel.setOnClickListener(v -> {
            sendActionResponse(-1);
        });

        btnLobbyReady.setOnClickListener(v -> {
            if (engine != null) engine.sendReady();
        });
        btnLobbyLeave.setOnClickListener(v -> {
            if (engine != null) engine.disconnect();
            finish();
        });

        btnChatSend.setOnClickListener(v -> {
            String msg = etChatInput.getText().toString().trim();
            if (!TextUtils.isEmpty(msg) && engine != null) {
                engine.sendChat(msg);
                appendChat("Me", msg);
                etChatInput.setText("");
            }
        });
    }

    private void initEngine() {
        soundManager = new SoundManager(this);
        soundManager.init(0.8, 0.6, true, true);

        imageLoader = new ImageLoader(true);

        engine = new GameEngine(soundManager);
        engine.setListener(this);
        engine.setPlayerName(Constants.PlayerName);

        gameFieldView.setField(engine.getField());
        gameFieldView.setImageLoader(imageLoader);

        TextureLoader.get().init();
    }

    private void loadData() {
        new Thread(() -> {
            DataManager.get().load(false);
            Log.i(TAG, "DataManager loaded");
        }, "DataLoad").start();
    }

    private boolean handleDirectIntent(Intent intent) {
        if (intent == null) return false;

        YGOGameOptions options = intent.getParcelableExtra(YGOGameOptions.YGO_GAME_OPTIONS_BUNDLE_KEY);
        if (options != null) {
            long time = intent.getLongExtra(YGOGameOptions.YGO_GAME_OPTIONS_BUNDLE_TIME, 0);
            if (System.currentTimeMillis() - time < YGOGameOptions.TIME_OUT) {
                joinFromOptions(options);
                hideMainMenu();
                return true;
            }
        }

        String host = intent.getStringExtra("host");
        if (!TextUtils.isEmpty(host)) {
            int port = intent.getIntExtra("port", 7911);
            String room = intent.getStringExtra("room");
            engine.connectToServer(host, port, false,
                    room != null ? room : "", "",
                    0, 0, 5, 8000, 5, 1, 0, false, false);
            hideMainMenu();
            return true;
        }

        if (intent.getBooleanExtra("botMode", false)) {
            engine.setBotMode(true);
            engine.connectToServer("127.0.0.1", 7911, true,
                    "Bot Game", "",
                    5, 0, 5, 8000, 5, 1, 0, true, false);
            engine.startBotDuel("127.0.0.1", 7911, "WindBot", "");
            hideMainMenu();
            return true;
        }

        return false;
    }

    private void joinFromOptions(YGOGameOptions options) {
        String host = options.mServerAddr;
        int port = options.mPort;
        String room = options.mRoomName != null ? options.mRoomName : "";
        String user = options.mUserName != null ? options.mUserName : Constants.PlayerName;
        engine.setPlayerName(user);
        engine.connectToServer(host, port, false, room, "",
                0, 0, 5, 8000, 5, 1, 0, false, false);
    }

    // === Main Menu ===

    private void showMainMenu() {
        layoutMainMenu = findViewById(R.id.layout_main_menu);
        tvVersion = findViewById(R.id.tv_version);
        layoutMainMenu.setVisibility(View.VISIBLE);
        
        // 隐藏所有游戏界面元素
        if (gameFieldView != null) gameFieldView.setVisibility(View.GONE);
        if (layoutOpponentInfo != null) layoutOpponentInfo.setVisibility(View.GONE);
        if (layoutPlayerInfo != null) layoutPlayerInfo.setVisibility(View.GONE);
        if (layoutActionButtons != null) layoutActionButtons.setVisibility(View.GONE);
        if (layoutChat != null) layoutChat.setVisibility(View.GONE);
        if (dialogContainer != null) dialogContainer.setVisibility(View.GONE);
        if (layoutLobby != null) layoutLobby.setVisibility(View.GONE);

        // 设置背景图片
        String bgPath = AppsSettings.get().getResourcePath() + "textures/extra/bg_menu.jpg";
        java.io.File bgFile = new java.io.File(bgPath);
        if (bgFile.exists()) {
            try {
                android.graphics.Bitmap bitmap = android.graphics.BitmapFactory.decodeFile(bgPath);
                if (bitmap != null) {
                    layoutMainMenu.setBackground(new android.graphics.drawable.BitmapDrawable(getResources(), bitmap));
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to load background image", e);
            }
        }

        int v1 = (PRO_VERSION & 0xf000) >> 12;
        int v2 = (PRO_VERSION & 0x0ff0) >> 4;
        int v3 = PRO_VERSION & 0x000f;
        tvVersion.setText(String.format("YGOPro Version:%X.0%X.%X", v1, v2, v3));

        // 绑定按钮点击事件
        findViewById(R.id.btn_menu_lan).setOnClickListener(v -> showLanModeDialog());
        findViewById(R.id.btn_menu_single).setOnClickListener(v -> showSingleModeDialog());
        findViewById(R.id.btn_menu_replay).setOnClickListener(v -> showReplayModeDialog());
        findViewById(R.id.btn_menu_deck).setOnClickListener(v -> showDeckEditDialog());
        findViewById(R.id.btn_menu_settings).setOnClickListener(v -> showSettingsDialog());
        findViewById(R.id.btn_menu_exit).setOnClickListener(v -> {
            soundManager.stopBGM();
            finish();
        });

        soundManager.playBGM(SoundManager.BGM.MENU);
        applySettingsToEngine();
    }

    private void hideMainMenu() {
        if (layoutMainMenu != null) {
            layoutMainMenu.setVisibility(View.GONE);
        }
        if (gameFieldView != null) gameFieldView.setVisibility(View.VISIBLE);
        soundManager.playBGM(SoundManager.BGM.DUEL);
    }

    private void restoreMainMenu() {
        if (layoutMainMenu != null) {
            layoutMainMenu.setVisibility(View.VISIBLE);
        }
    }

    private void showLanModeDialog() {
        View customView = getLayoutInflater().inflate(R.layout.dialog_lan_connection, null);
        
        View layoutLanMain = customView.findViewById(R.id.layout_lan_main);
        View layoutCreateHost = customView.findViewById(R.id.layout_create_host_settings);
        View layoutPlayerWaiting = customView.findViewById(R.id.layout_player_waiting);
        
        EditText etNickname = layoutLanMain.findViewById(R.id.et_nickname);
        EditText etHostIp = layoutLanMain.findViewById(R.id.et_host_ip);
        EditText etHostPort = layoutLanMain.findViewById(R.id.et_host_port);
        EditText etRoomPassword = layoutLanMain.findViewById(R.id.et_room_password);
        ListView lvHostList = layoutLanMain.findViewById(R.id.lv_host_list);
        Button btnCreateHost = layoutLanMain.findViewById(R.id.btn_create_host);
        Button btnRefreshLan = layoutLanMain.findViewById(R.id.btn_refresh_lan);
        Button btnJoinGame = layoutLanMain.findViewById(R.id.btn_join_game);
        Button btnExitLan = layoutLanMain.findViewById(R.id.btn_exit_lan);
        
        Spinner spinnerBanlist = layoutCreateHost.findViewById(R.id.spinner_banlist);
        Spinner spinnerRule = layoutCreateHost.findViewById(R.id.spinner_rule);
        Spinner spinnerCardAllowed = layoutCreateHost.findViewById(R.id.spinner_card_allowed);
        EditText etStartLP = layoutCreateHost.findViewById(R.id.et_start_lp);
        Spinner spinnerDuelMode = layoutCreateHost.findViewById(R.id.spinner_duel_mode);
        EditText etStartHand = layoutCreateHost.findViewById(R.id.et_start_hand);
        EditText etTimeLimit = layoutCreateHost.findViewById(R.id.et_time_limit);
        EditText etDrawCount = layoutCreateHost.findViewById(R.id.et_draw_count);
        CheckBox chkNoCheckDeck = layoutCreateHost.findViewById(R.id.chk_no_check_deck);
        CheckBox chkNoShuffleDeck = layoutCreateHost.findViewById(R.id.chk_no_shuffle_deck);
        EditText etHostName = layoutCreateHost.findViewById(R.id.et_host_name);
        EditText etHostPassword = layoutCreateHost.findViewById(R.id.et_host_password);
        Button btnConfirmCreate = layoutCreateHost.findViewById(R.id.btn_confirm_create);
        Button btnCancelCreate = layoutCreateHost.findViewById(R.id.btn_cancel_create);
        
        EditText etPlayer1Name = layoutPlayerWaiting.findViewById(R.id.et_player1_name);
        EditText etPlayer2Name = layoutPlayerWaiting.findViewById(R.id.et_player2_name);
        CheckBox chkPlayer1Ready = layoutPlayerWaiting.findViewById(R.id.chk_player1_ready);
        CheckBox chkPlayer2Ready = layoutPlayerWaiting.findViewById(R.id.chk_player2_ready);
        Button btnDuelistMode = layoutPlayerWaiting.findViewById(R.id.btn_duelist_mode);
        Button btnSpectatorMode = layoutPlayerWaiting.findViewById(R.id.btn_spectator_mode);
        Button btnReady = layoutPlayerWaiting.findViewById(R.id.btn_ready);
        Spinner spinnerDeckSelect = layoutPlayerWaiting.findViewById(R.id.spinner_deck_select);
        TextView tvBanlist = layoutPlayerWaiting.findViewById(R.id.tv_banlist);
        TextView tvCardAllowed = layoutPlayerWaiting.findViewById(R.id.tv_card_allowed);
        TextView tvDuelMode = layoutPlayerWaiting.findViewById(R.id.tv_duel_mode);
        TextView tvStartLP = layoutPlayerWaiting.findViewById(R.id.tv_start_lp);
        TextView tvStartHand = layoutPlayerWaiting.findViewById(R.id.tv_start_hand);
        TextView tvDrawCount = layoutPlayerWaiting.findViewById(R.id.tv_draw_count);
        Button btnExitWaiting = layoutPlayerWaiting.findViewById(R.id.btn_exit_waiting);
        
        int popupWidth = (int) (640 * getResources().getDisplayMetrics().density);
        int popupHeight = (int) (480 * getResources().getDisplayMetrics().density);
        PopupWindow popupWindow = new PopupWindow(customView, popupWidth, popupHeight, true);
        popupWindow.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(android.graphics.Color.TRANSPARENT));
        popupWindow.setOutsideTouchable(true);
        popupWindow.setOnDismissListener(() -> restoreMainMenu());
        
        btnCreateHost.setOnClickListener(v -> {
            layoutLanMain.setVisibility(View.GONE);
            layoutCreateHost.setVisibility(View.VISIBLE);
        });
        
        btnCancelCreate.setOnClickListener(v -> {
            layoutCreateHost.setVisibility(View.GONE);
            layoutLanMain.setVisibility(View.VISIBLE);
        });
        
        btnConfirmCreate.setOnClickListener(v -> {
            String banlist = spinnerBanlist.getSelectedItem() != null ? spinnerBanlist.getSelectedItem().toString() : "";
            String rule = spinnerRule.getSelectedItem() != null ? spinnerRule.getSelectedItem().toString() : "";
            String cardAllowed = spinnerCardAllowed.getSelectedItem() != null ? spinnerCardAllowed.getSelectedItem().toString() : "";
            String startLP = etStartLP.getText().toString();
            String duelMode = spinnerDuelMode.getSelectedItem() != null ? spinnerDuelMode.getSelectedItem().toString() : "";
            String startHand = etStartHand.getText().toString();
            String timeLimit = etTimeLimit.getText().toString();
            String drawCount = etDrawCount.getText().toString();
            boolean noCheckDeck = chkNoCheckDeck.isChecked();
            boolean noShuffleDeck = chkNoShuffleDeck.isChecked();
            String hostName = etHostName.getText().toString();
            String password = etHostPassword.getText().toString();
            
            popupWindow.dismiss();
            hideMainMenu();
            engine.startLocalServer();
            
            layoutCreateHost.setVisibility(View.GONE);
            layoutPlayerWaiting.setVisibility(View.VISIBLE);
            
            tvBanlist.setText(banlist.isEmpty() ? "N/A" : banlist);
            tvCardAllowed.setText(cardAllowed.isEmpty() ? "所有卡片" : cardAllowed);
            tvDuelMode.setText(duelMode.isEmpty() ? "单局模式" : duelMode);
            tvStartLP.setText(startLP.isEmpty() ? "8000" : startLP);
            tvStartHand.setText(startHand.isEmpty() ? "5" : startHand);
            tvDrawCount.setText(drawCount.isEmpty() ? "1" : drawCount);
        });
        
        btnExitLan.setOnClickListener(v -> popupWindow.dismiss());
        
        btnExitWaiting.setOnClickListener(v -> popupWindow.dismiss());
        
        btnReady.setOnClickListener(v -> {
            btnReady.setEnabled(false);
            btnReady.setText("已准备");
        });
        
        btnDuelistMode.setOnClickListener(v -> {
            btnDuelistMode.setEnabled(false);
            btnSpectatorMode.setEnabled(true);
        });
        
        btnSpectatorMode.setOnClickListener(v -> {
            btnSpectatorMode.setEnabled(false);
            btnDuelistMode.setEnabled(true);
        });
        
        layoutMainMenu.setVisibility(View.GONE);
        popupWindow.showAtLocation(layoutMainMenu, Gravity.CENTER, 0, 0);
    }

    private void showSingleModeDialog() {
        File singleDir = new File(AppsSettings.get().getResourcePath(), Constants.CORE_SINGLE_PATH);
        File[] files = singleDir.exists()
                ? singleDir.listFiles((dir, name) -> name.endsWith(Constants.LUA_FILE_EX))
                : null;
        List<String> nameList = new ArrayList<>();
        List<String> descList = new ArrayList<>();
        if (files != null && files.length > 0) {
            for (File f : files) {
                String name = f.getName().replace(Constants.LUA_FILE_EX, "");
                nameList.add(name);
                descList.add(readLuaDescription(f));
            }
        } else {
            nameList.add("（暂无残局文件）");
            descList.add("");
        }

        final File[] finalFiles = files;
        DialogPlus dialog = new DialogPlus(this);
        dialog.setTitle("单人游戏 - 残局模式");
        dialog.setContentView(R.layout.dialog_edit_and_list);
        dialog.bind(R.id.room_name).setVisibility(View.GONE);
        ListView listView = dialog.bind(R.id.room_list);
        SimpleListAdapter adapter = new SimpleListAdapter(this);
        adapter.set(nameList);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (finalFiles != null && position < finalFiles.length) {
                dialog.dismiss();
                hideMainMenu();
                engine.startSingleMode(finalFiles[position].getAbsolutePath());
            }
        });
        dialog.setLeftButtonText("退出");
        dialog.setLeftButtonListener((d, w) -> {
            d.dismiss();
            restoreMainMenu();
        });
        layoutMainMenu.setVisibility(View.GONE);
        dialog.show();
    }

    private String readLuaDescription(File luaFile) {
        StringBuilder message = new StringBuilder();
        boolean inMessage = false;
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(luaFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("--[[message")) {
                    if (line.length() <= 13) {
                        inMessage = true;
                        continue;
                    } else {
                        int end = line.indexOf(']', 11);
                        if (end > 11) {
                            message.append(line, 12, end - 1);
                            break;
                        }
                    }
                }
                if (inMessage) {
                    if (line.startsWith("]]")) break;
                    message.append(line);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to read lua description: " + luaFile.getName(), e);
        }
        return message.toString();
    }

    private void showReplayModeDialog() {
        File replayDir = new File(AppsSettings.get().getResourcePath(), Constants.CORE_REPLAY_PATH);
        File[] files = replayDir.exists()
                ? replayDir.listFiles((dir, name) -> name.endsWith(Constants.YRP_FILE_EX))
                : null;
        List<String> nameList = new ArrayList<>();
        if (files != null && files.length > 0) {
            java.util.Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
            for (File f : files) {
                nameList.add(f.getName());
            }
        } else {
            nameList.add("（暂无录像文件）");
        }

        final File[] finalFiles = files;
        DialogPlus dialog = new DialogPlus(this);
        dialog.setTitle("观看录像");
        dialog.setContentView(R.layout.dialog_edit_and_list);
        dialog.bind(R.id.room_name).setVisibility(View.GONE);
        ListView listView = dialog.bind(R.id.room_list);
        SimpleListAdapter adapter = new SimpleListAdapter(this);
        adapter.set(nameList);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            if (finalFiles != null && position < finalFiles.length) {
                dialog.dismiss();
                hideMainMenu();
                engine.loadReplay(finalFiles[position].getAbsolutePath());
            }
        });
        dialog.setLeftButtonText("退出");
        dialog.setLeftButtonListener((d, w) -> {
            d.dismiss();
            restoreMainMenu();
        });
        layoutMainMenu.setVisibility(View.GONE);
        dialog.show();
    }

    private void showDeckEditDialog() {
        layoutMainMenu.setVisibility(View.GONE);
        Intent intent = new Intent(this, cn.garymb.ygomobile.ui.home.HomeActivity.class);
        intent.putExtra("tab", 2);
        startActivity(intent);
    }

    private void showSettingsDialog() {
        android.content.SharedPreferences prefs = getSharedPreferences(getPackageName() + ".settings", Context.MODE_PRIVATE);
        String[] keys = {
                "chkMAutoPos", "chkSTAutoPos", "chkRandomPos",
                "chkAutoChain", "chkWaitChain", "chkDefaultShowChain",
                "chkAutoSaveReplay", "chkEnableSound", "chkEnableMusic"
        };
        String[] labels = {
                "主卡位置自动", "魔陷位置自动", "随机出卡",
                "自动连锁", "等待连锁确认", "显示连锁标记",
                "自动保存录像", "启用音效", "启用BGM"
        };

        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (12 * getResources().getDisplayMetrics().density);
        container.setPadding(pad, pad, pad, pad);
        android.widget.CheckBox[] checkBoxes = new android.widget.CheckBox[keys.length];
        for (int i = 0; i < keys.length; i++) {
            android.widget.CheckBox cb = new android.widget.CheckBox(this);
            cb.setText(labels[i]);
            cb.setTextColor(0xFFFFFFFF);
            cb.setChecked(prefs.getBoolean(keys[i], false));
            container.addView(cb);
            checkBoxes[i] = cb;
        }

        ScrollView scrollContainer = new ScrollView(this);
        scrollContainer.addView(container);

        DialogPlus dialog = new DialogPlus(this);
        dialog.setTitle("系统设定");
        dialog.setContentView(scrollContainer);
        dialog.setLeftButtonText("保存");
        dialog.setLeftButtonListener((d, w) -> {
            android.content.SharedPreferences.Editor editor = prefs.edit();
            for (int i = 0; i < keys.length; i++) {
                editor.putBoolean(keys[i], checkBoxes[i].isChecked());
            }
            editor.apply();
            applySettingsToEngine();
            d.dismiss();
            restoreMainMenu();
        });
        dialog.setRightButtonText("取消");
        dialog.setRightButtonListener((d, w) -> {
            d.dismiss();
            restoreMainMenu();
        });
        layoutMainMenu.setVisibility(View.GONE);
        dialog.show();
    }

    private void applySettingsToEngine() {
        android.content.SharedPreferences prefs = getSharedPreferences(getPackageName() + ".settings", Context.MODE_PRIVATE);
        boolean enableSound = prefs.getBoolean("chkEnableSound", true);
        boolean enableMusic = prefs.getBoolean("chkEnableMusic", true);
        if (soundManager != null) {
            soundManager.enableSounds(enableSound);
            soundManager.enableMusic(enableMusic);
        }
    }

    // === EngineListener ===

    @Override
    public void onStateChanged(GameEngine.GameState newState) {
        Log.i(TAG, "State: " + newState);
        switch (newState) {
            case LOBBY:
                hideMainMenu();
                if (layoutLobby != null) layoutLobby.setVisibility(View.VISIBLE);
                tvLobbyStatus.setText("已连接 - 等待玩家准备");
                break;
            case DECK_SELECT:
                if (layoutLobby != null) layoutLobby.setVisibility(View.GONE);
                showDeckSelectDialog();
                break;
            case HAND_SELECT:
                showHandSelectDialog();
                break;
            case TP_SELECT:
                showTPSelectDialog();
                break;
            case DUELING:
                hideMainMenu();
                if (layoutLobby != null) layoutLobby.setVisibility(View.GONE);
                if (layoutActionButtons != null) layoutActionButtons.setVisibility(View.GONE);
                if (layoutChat != null) layoutChat.setVisibility(View.VISIBLE);
                isGameStarted = true;
                break;
            case SIDING:
                showSideSelectDialog();
                break;
            case DUEL_END:
                showDuelEndDialog();
                break;
            case DISCONNECTED:
                if (!isFinishing()) {
                    runOnUiThread(() -> {
                        Toast.makeText(this, "连接已断开", Toast.LENGTH_SHORT).show();
                        finish();
                    });
                }
                break;
        }
    }

    @Override
    public void onFieldChanged() {
        runOnUiThread(() -> {
            gameFieldView.invalidate();
        });
    }

    @Override
    public void onPlayerInfoUpdated(int player) {
        runOnUiThread(() -> {
            GameEngine.PlayerInfo info = engine.playerInfos[player];
            GameField.PlayerField pf = engine.getField().players[player];
            if (player == 0) {
                tvPlayerLp.setText("LP: " + pf.lp);
                tvPlayerName.setText(info.name.isEmpty() ? Constants.PlayerName : info.name);
            } else {
                tvOpponentLp.setText("LP: " + pf.lp);
                tvOpponentName.setText(info.name.isEmpty() ? "Opponent" : info.name);
                int handCount = engine.getField().getCardCount(player, ocgcore.enums.CardLocation.Hand.value());
                tvPlayerHandCount.setText("Hand: " + handCount);
            }
        });
    }

    @Override
    public void onPhaseChanged(int phase) {
        runOnUiThread(() -> {
            DuelPhase dp = DuelPhase.valueOf(phase);
            String phaseName = dp != null ? dp.name() : "Unknown";
            tvPhaseInfo.setText(phaseName + " Phase");
            tvTurnInfo.setText("Turn " + engine.getField().turnCount);
            isMyTurn = (engine.getField().currentPlayer == engine.getClient().selfType);
            layoutActionButtons.setVisibility(isMyTurn ? View.VISIBLE : View.GONE);
            updateActionButtonsForPhase(phase);
        });
    }

    private void updateActionButtonsForPhase(int phase) {
        DuelPhase dp = DuelPhase.valueOf(phase);
        if (dp == null) return;
        switch (dp) {
            case Main1:
                btnBp.setVisibility(View.VISIBLE);
                btnEp.setVisibility(View.GONE);
                btnM2.setVisibility(View.GONE);
                break;
            case Main2:
                btnBp.setVisibility(View.GONE);
                btnEp.setVisibility(View.VISIBLE);
                btnM2.setVisibility(View.GONE);
                break;
            case BattleStep:
                btnBp.setVisibility(View.GONE);
                btnEp.setVisibility(View.VISIBLE);
                btnM2.setVisibility(View.VISIBLE);
                break;
            default:
                btnBp.setVisibility(View.GONE);
                btnEp.setVisibility(View.VISIBLE);
                btnM2.setVisibility(View.GONE);
                break;
        }
    }

    @Override
    public void onChatReceived(String player, String message) {
        runOnUiThread(() -> appendChat(player, message));
    }

    private void appendChat(String player, String message) {
        chatHistory += "[" + player + "] " + message + "\n";
        tvChatLog.setText(chatHistory);
        ScrollView scrollChat = findViewById(R.id.scroll_chat);
        scrollChat.post(() -> scrollChat.fullScroll(ScrollView.FOCUS_DOWN));
    }

    @Override
    public void onSelectRequired(int selectType, ByteBuffer data) {
        runOnUiThread(() -> {
            switch (selectType) {
                case 0:
                    showHandSelectDialog();
                    break;
                case 1:
                    showTPSelectDialog();
                    break;
                case 10:
                    showBattleCmdDialog(data);
                    break;
                case 11:
                    showIdleCmdDialog(data);
                    break;
                case 12:
                    showEffectYnDialog(data);
                    break;
                case 13:
                    showYesNoDialog(data);
                    break;
                case 14:
                    showOptionDialog(data);
                    break;
                case 15:
                case 20:
                case 23:
                    showCardSelectDialog(data);
                    break;
                case 18:
                case 24:
                    break;
                case 19:
                    showPositionSelectDialog();
                    break;
                default:
                    Log.w(TAG, "Unhandled select type: " + selectType);
                    break;
            }
        });
    }

    @Override
    public void onDuelResult(int winner, int reason) {
        runOnUiThread(() -> {
            String result;
            if (winner == 2) {
                result = "平局";
            } else if (winner == engine.getClient().selfType) {
                result = "🎉 你赢了！";
            } else {
                result = "😢 你输了";
            }
            showResultDialog(result);
        });
    }

    @Override
    public void onHintMessage(String hint) {
        runOnUiThread(() -> {
            tvHintMessage.setText(hint);
            tvHintMessage.setVisibility(View.VISIBLE);
            mainHandler.postDelayed(() -> tvHintMessage.setVisibility(View.GONE), 2000);
        });
    }

    @Override
    public void onReplayData(byte[] data) {
        Log.i(TAG, "Replay data received, size=" + data.length);
    }

    @Override
    public void onTimeLimitUpdate(int player, int leftTime) {
        runOnUiThread(() -> {
            String timeStr = (leftTime / 60) + ":" + String.format("%02d", leftTime % 60);
            if (player == 0) {
                tvPlayerTime.setText(timeStr);
                tvPlayerTime.setVisibility(View.VISIBLE);
            } else {
                tvOpponentTime.setText(timeStr);
                tvOpponentTime.setVisibility(View.VISIBLE);
            }
        });
    }

    @Override
    public void onChainAnimation(int code, int controler, int location, int sequence) {
        runOnUiThread(() -> {
            gameFieldView.setSelectedCard(controler, location, sequence);
            mainHandler.postDelayed(() -> gameFieldView.clearSelection(), 1500);
        });
    }

    // === GameFieldView.OnCardClickListener ===

    @Override
    public void onCardClick(int player, int location, int sequence) {
        Log.d(TAG, "Card click: p=" + player + " loc=" + location + " seq=" + sequence);
        GameField.ClientCard card = engine.getField().getCard(player, location, sequence);
        if (card != null && card.isFaceUp() && card.code > 0) {
            showCardInfoDialog(card.code);
        }
    }

    @Override
    public void onZoneClick(int player, int location, int sequence) {
        Log.d(TAG, "Zone click: p=" + player + " loc=" + location);
    }

    @Override
    public void onFieldLongPress(int player, int location, int sequence) {
        Log.d(TAG, "Long press: p=" + player + " loc=" + location + " seq=" + sequence);
    }

    // === Dialog Methods ===

    private int getResId(String name, String type) {
        return getResources().getIdentifier(name, type, getPackageName());
    }

    private void showHandSelectDialog() {
        DialogPlus dialog = new DialogPlus(this);
        dialog.setTitle("猜拳");
        dialog.setMessage("请选择石头、剪刀或布");
        dialog.setContentView(R.layout.dialog_game_select);
        View contentView = dialog.getContentView();
        contentView.findViewById(getResId("tv_select_title", "id")).setVisibility(View.GONE);
        contentView.findViewById(getResId("tv_select_hint", "id")).setVisibility(View.GONE);
        contentView.findViewById(getResId("layout_select_buttons", "id")).setVisibility(View.GONE);
        LinearLayout layoutOptions = contentView.findViewById(getResId("layout_options", "id"));
        String[] choices = {"石头", "剪刀", "布"};
        for (int i = 0; i < choices.length; i++) {
            Button btn = new Button(this);
            btn.setText(choices[i]);
            btn.setTextColor(0xFFFFFFFF);
            btn.setBackgroundColor(0xFF006688);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = 8;
            btn.setLayoutParams(lp);
            final int result = i + 1;
            btn.setOnClickListener(v -> {
                engine.sendHandResult(result);
                dialog.dismiss();
            });
            layoutOptions.addView(btn);
        }
        dialog.setCancelable(false);
        dialog.show();
    }

    private void showTPSelectDialog() {
        DialogPlus dialog = new DialogPlus(this);
        dialog.setTitle("先攻选择");
        dialog.setMessage("是否选择先攻？");
        dialog.setLeftButtonText("先攻");
        dialog.setLeftButtonListener((d, w) -> {
            engine.sendTPResult(true);
            d.dismiss();
        });
        dialog.setRightButtonText("后攻");
        dialog.setRightButtonListener((d, w) -> {
            engine.sendTPResult(false);
            d.dismiss();
        });
        dialog.setCancelable(false);
        dialog.show();
    }

    private void showYesNoDialog(ByteBuffer data) {
        int descId = 0;
        if (data != null && data.remaining() >= 4) {
            descId = data.getInt();
        }
        String desc = descId > 0
                ? DataManager.get().getStringManager().getSystemString(descId, "是否发动效果？")
                : "是否发动效果？";

        DialogPlus dialog = new DialogPlus(this);
        dialog.setTitle("确认");
        dialog.setMessage(desc);
        dialog.setLeftButtonText("是");
        dialog.setLeftButtonListener((d, w) -> {
            sendResponseInt(1);
            d.dismiss();
        });
        dialog.setRightButtonText("否");
        dialog.setRightButtonListener((d, w) -> {
            sendResponseInt(0);
            d.dismiss();
        });
        dialog.setCancelable(false);
        dialog.show();
    }

    private void showOptionDialog(ByteBuffer data) {
        if (data == null) return;
        int count = data.get() & 0xFF;
        List<String> options = new ArrayList<>();
        for (int i = 0; i < count && data.remaining() >= 4; i++) {
            int descId = data.getInt();
            String str = DataManager.get().getStringManager().getSystemString(descId, "Option " + (i + 1));
            options.add(str);
        }

        DialogPlus dialog = new DialogPlus(this);
        dialog.setTitle("请选择");
        dialog.setContentView(R.layout.dialog_game_select);
        View contentView = dialog.getContentView();
        contentView.findViewById(getResId("tv_select_title", "id")).setVisibility(View.GONE);
        contentView.findViewById(getResId("tv_select_hint", "id")).setVisibility(View.GONE);
        contentView.findViewById(getResId("layout_select_buttons", "id")).setVisibility(View.GONE);
        LinearLayout layoutOptions = contentView.findViewById(getResId("layout_options", "id"));
        for (int i = 0; i < options.size(); i++) {
            Button btn = new Button(this);
            btn.setText(options.get(i));
            btn.setTextColor(0xFFFFFFFF);
            btn.setBackgroundColor(0xFF006688);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = 8;
            btn.setLayoutParams(lp);
            final int idx = i;
            btn.setOnClickListener(v -> {
                sendResponseInt(idx);
                dialog.dismiss();
            });
            layoutOptions.addView(btn);
        }
        dialog.setCancelable(false);
        dialog.show();
    }

    private void showEffectYnDialog(ByteBuffer data) {
        showYesNoDialog(data);
    }

    private void showBattleCmdDialog(ByteBuffer data) {
        showListDialog("战斗阶段", new String[]{"攻击", "切换为守备表示", "结束战斗阶段"}, which -> {
            sendResponseInt(which);
        });
    }

    private void showIdleCmdDialog(ByteBuffer data) {
        showListDialog("主要阶段", new String[]{
                "召唤", "特殊召唤", "放置", "发动", "切换表示",
                "设置", "进入战斗阶段", "进入结束阶段"
        }, which -> {
            sendResponseInt(which);
        });
    }

    private void showCardSelectDialog(ByteBuffer data) {
        showHintMessage("请在场地中选择卡片");
    }

    private void showPositionSelectDialog() {
        showListDialog("选择表示形式", new String[]{
                "表侧攻击表示", "里侧攻击表示",
                "表侧守备表示", "里侧守备表示"
        }, which -> {
            int pos;
            switch (which) {
                case 0:
                    pos = 0x1;
                    break;
                case 1:
                    pos = 0x2;
                    break;
                case 2:
                    pos = 0x4;
                    break;
                case 3:
                    pos = 0x8;
                    break;
                default:
                    pos = 0x1;
                    break;
            }
            sendResponseInt(pos);
        });
    }

    private void showDeckSelectDialog() {
        showHintMessage("请选择你的卡组");
    }

    private void showSideSelectDialog() {
        showHintMessage("请替换副卡组");
    }

    private void showCardInfoDialog(int cardCode) {
        Card card = DataManager.get().getCardManager().getCard(cardCode);
        String name = card != null ? card.Name : "Unknown Card";
        if (name == null) name = "Unknown Card";
        DialogPlus dialog = new DialogPlus(this);
        dialog.setTitle(name + " (" + cardCode + ")");
        dialog.setLeftButtonText("关闭");
        dialog.setLeftButtonListener((d, w) -> d.dismiss());
        dialog.show();
    }

    private void showResultDialog(String result) {
        DialogPlus dialog = new DialogPlus(this);
        dialog.setTitle("决斗结果");
        dialog.setMessage(result);
        dialog.setLeftButtonText("确定");
        dialog.setLeftButtonListener((d, w) -> {
            d.dismiss();
            finish();
        });
        dialog.setCancelable(false);
        dialog.show();
    }

    private void showDuelEndDialog() {
        DialogPlus dialog = new DialogPlus(this);
        dialog.setTitle("决斗结束");
        dialog.setMessage("本次决斗已结束");
        dialog.setLeftButtonText("确定");
        dialog.setLeftButtonListener((d, w) -> {
            d.dismiss();
            finish();
        });
        dialog.setRightButtonText("继续等待");
        dialog.setRightButtonListener((d, w) -> d.dismiss());
        dialog.setCancelable(false);
        dialog.show();
    }

    private void showHintMessage(String msg) {
        tvHintMessage.setText(msg);
        tvHintMessage.setVisibility(View.VISIBLE);
        mainHandler.postDelayed(() -> tvHintMessage.setVisibility(View.GONE), 3000);
    }

    private interface OnItemPickedListener {
        void onPicked(int which);
    }

    private void showListDialog(String title, String[] items, OnItemPickedListener listener) {
        DialogPlus dialog = new DialogPlus(this);
        dialog.setTitle(title);
        dialog.setContentView(R.layout.dialog_game_select);
        View contentView = dialog.getContentView();
        contentView.findViewById(getResId("tv_select_title", "id")).setVisibility(View.GONE);
        contentView.findViewById(getResId("tv_select_hint", "id")).setVisibility(View.GONE);
        contentView.findViewById(getResId("layout_select_buttons", "id")).setVisibility(View.GONE);
        LinearLayout layoutOptions = contentView.findViewById(getResId("layout_options", "id"));
        for (int i = 0; i < items.length; i++) {
            Button btn = new Button(this);
            btn.setText(items[i]);
            btn.setTextColor(0xFFFFFFFF);
            btn.setBackgroundColor(0xFF006688);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = 8;
            btn.setLayoutParams(lp);
            final int idx = i;
            btn.setOnClickListener(v -> {
                if (listener != null) listener.onPicked(idx);
                dialog.dismiss();
            });
            layoutOptions.addView(btn);
        }
        dialog.setCancelable(false);
        dialog.show();
    }

    // === Response helpers ===

    private void sendResponseInt(int value) {
        ByteBuffer buf = ByteBuffer.allocate(4);
        buf.order(java.nio.ByteOrder.LITTLE_ENDIAN);
        buf.putInt(value);
        engine.sendResponse(buf.array());
    }

    private void sendActionResponse(int action) {
        sendResponseInt(action);
        layoutActionButtons.setVisibility(View.GONE);
    }

    // === Lifecycle ===

    @Override
    protected void onResume() {
        super.onResume();
        setupFullScreen();
        if (!isGameStarted && layoutMainMenu != null
                && layoutMainMenu.getVisibility() != View.VISIBLE) {
            restoreMainMenu();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (engine != null) {
            engine.release();
        }
        if (soundManager != null) {
            soundManager.release();
        }
        if (imageLoader != null) {
            imageLoader.close();
        }
        TextureLoader.get().release();
    }

    private void setupBackPressedHandler() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (layoutMainMenu != null && layoutMainMenu.getVisibility() == View.VISIBLE) {
                    soundManager.stopBGM();
                    finish();
                    return;
                }
                if (!isGameStarted) {
                    restoreMainMenu();
                    return;
                }
                DialogPlus dialog = new DialogPlus(YGONativeGameActivity.this);
                dialog.setTitle("退出决斗");
                dialog.setMessage("确定要退出当前决斗吗？");
                dialog.setLeftButtonText("确定");
                dialog.setLeftButtonListener((d, w) -> {
                    if (engine != null) {
                        if (engine.getState() == GameEngine.GameState.DUELING) {
                            engine.sendSurrender();
                        } else {
                            engine.disconnect();
                        }
                    }
                    d.dismiss();
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                });
                dialog.setRightButtonText("取消");
                dialog.setRightButtonListener((d, w) -> d.dismiss());
                dialog.show();
            }
        });
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            setupFullScreen();
        }
    }
}
