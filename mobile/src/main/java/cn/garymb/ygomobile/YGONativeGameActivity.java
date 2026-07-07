package cn.garymb.ygomobile;

import static cn.garymb.ygomobile.utils.BotUtil.parseBotConfig;
import static cn.garymb.ygomobile.utils.PuzzleUtil.loadPuzzleFiles;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import cn.garymb.ygodata.YGOGameOptions;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygodata.YGOGameOptions;
import cn.garymb.ygomobile.audio.SoundManager;
import cn.garymb.ygomobile.game.GameEngine;
import cn.garymb.ygomobile.game.GameField;
import cn.garymb.ygomobile.game.ReplayEngine;
import cn.garymb.ygomobile.game.ReplayReader;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.render.GameFieldView;
import cn.garymb.ygomobile.render.TextureLoader;
import cn.garymb.ygomobile.ui.dialogs.DeckEditDialog;
import cn.garymb.ygomobile.ui.dialogs.LanModeDialog;
import cn.garymb.ygomobile.ui.dialogs.ReplayModeDialog;
import cn.garymb.ygomobile.ui.dialogs.SettingsDialog;
import cn.garymb.ygomobile.ui.dialogs.SingleModeDialog;
import cn.garymb.ygomobile.ui.plus.DialogPlus;
import cn.garymb.ygomobile.utils.BotUtil;
import cn.garymb.ygomobile.utils.DraggablePopupHelper;
import cn.garymb.ygomobile.utils.PuzzleUtil;
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

    private LinearLayout layoutOpponentInfo;
    private LinearLayout layoutPlayerInfo;

    private String chatHistory = "";
    private boolean isMyTurn = false;
    private volatile boolean isGameStarted = false;
    private DraggablePopupHelper mainMenuDragHelper;

    private static final int CMD_CONTEXT_IDLE = 1;
    private static final int CMD_CONTEXT_BATTLE = 2;
    private int cmdContext = 0;
    private boolean isPlaceSelecting = false;
    private boolean isSelectingSum = false;
    private int sumSelectValue = 0;
    private int sumSelectMin = 0;
    private int sumSelectMax = 0;
    private List<SumCardInfo> sumCardInfos;
    private boolean[] sumSelected;
    private boolean exitOnReturn = true;
    private int directEnterMode = 0; // 0=normal, 1=replay dialog, 2=single dialog

    private static class SumCardInfo {
        int code, controler, location, sequence, opParam, value, index;

        SumCardInfo(int code, int ctrl, int loc, int seq, int opParam, int value, int index) {
            this.code = code;
            this.controler = ctrl;
            this.location = loc;
            this.sequence = seq;
            this.opParam = opParam;
            this.value = value;
            this.index = index;
        }
    }

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
            if (cmdContext == CMD_CONTEXT_BATTLE) {
                sendActionResponse(2);
            }
        });
        btnEp.setOnClickListener(v -> {
            if (cmdContext == CMD_CONTEXT_BATTLE) {
                sendActionResponse(3);
            } else if (cmdContext == CMD_CONTEXT_IDLE) {
                sendActionResponse(7);
            }
        });
        btnBp.setOnClickListener(v -> {
            if (cmdContext == CMD_CONTEXT_IDLE) {
                sendActionResponse(6);
            }
        });
        btnChain.setOnClickListener(v -> {
            if (cmdContext == CMD_CONTEXT_IDLE) {
                sendActionResponse(8);
            }
        });
        btnCancel.setOnClickListener(v -> {
            if (isPlaceSelecting) {
                isPlaceSelecting = false;
                gameFieldView.setHighlightFieldMask(0);
                sendResponseInt(-1);
            } else {
                sendActionResponse(-1);
            }
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

        String[] args = cn.garymb.ygomobile.core.IrrlichtBridge.getArgs(intent);
        if (args != null && args.length > 0) {
            return handleArgs(args);
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

    private boolean handleArgs(String[] args) {
        boolean keepOnReturn = false;
        boolean showReplayDialog = false;
        boolean showSingleDialog = false;

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("-k".equals(arg)) {
                keepOnReturn = true;
                exitOnReturn = false;
            } else if ("-r".equals(arg)) {
                exitOnReturn = !keepOnReturn;
                String replayName = null;
                if (i + 1 < args.length && !args[i + 1].startsWith("-")) {
                    replayName = args[i + 1];
                    i++;
                }
                if (replayName != null) {
                    File replayFile = new File(AppsSettings.get().getResourcePath() + "/" + Constants.CORE_REPLAY_PATH, replayName);
                    if (replayFile.exists()) {
                        hideMainMenu();
                        startReplayPlayback(replayFile.getAbsolutePath(), 1);
                        return true;
                    }
                } else {
                    showReplayDialog = true;
                }
            } else if ("-s".equals(arg)) {
                exitOnReturn = !keepOnReturn;
                String singleName = null;
                if (i + 1 < args.length && !args[i + 1].startsWith("-")) {
                    singleName = args[i + 1];
                    i++;
                }
                if (singleName != null) {
                    File singleFile = new File(AppsSettings.get().getResourcePath() + "/" + Constants.CORE_SINGLE_PATH, singleName);
                    if (singleFile.exists()) {
                        hideMainMenu();
                        engine.startSingleMode(singleFile.getAbsolutePath());
                        return true;
                    }
                } else {
                    showSingleDialog = true;
                }
            } else if ("-j".equals(arg) || "-c".equals(arg)) {
                exitOnReturn = !keepOnReturn;
            }
        }

        if (showReplayDialog) {
            initMainMenuIfNeeded();
            directEnterMode = 1;
            layoutMainMenu.post(() -> showReplayModeDialog());
            return true;
        }

        if (showSingleDialog) {
            initMainMenuIfNeeded();
            directEnterMode = 2;
            layoutMainMenu.post(() -> showSingleModeDialog());
            return true;
        }

        return false;
    }

    private void initMainMenuIfNeeded() {
        if (layoutMainMenu == null) {
            layoutMainMenu = findViewById(R.id.layout_main_menu);
            tvVersion = findViewById(R.id.tv_version);
            bindMainMenuButtons();
        }
    }

    private void bindMainMenuButtons() {
        int v1 = (PRO_VERSION & 0xf000) >> 12;
        int v2 = (PRO_VERSION & 0x0ff0) >> 4;
        int v3 = PRO_VERSION & 0x000f;
        tvVersion.setText(String.format("YGOPro Version:%X.0%X.%X", v1, v2, v3));

        findViewById(R.id.btn_menu_lan).setOnClickListener(v -> showLanModeDialog());
        findViewById(R.id.btn_menu_single).setOnClickListener(v -> showSingleModeDialog());
        findViewById(R.id.btn_menu_replay).setOnClickListener(v -> showReplayModeDialog());
        findViewById(R.id.btn_menu_deck).setOnClickListener(v -> showDeckEditDialog());
        findViewById(R.id.btn_menu_settings).setOnClickListener(v -> showSettingsDialog());
        findViewById(R.id.btn_menu_exit).setOnClickListener(v -> {
            soundManager.stopBGM();
            finish();
        });
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

        mainMenuDragHelper = new DraggablePopupHelper(this, "main_menu");
        mainMenuDragHelper.setupDraggableView(layoutMainMenu);
        mainMenuDragHelper.applySavedPositionToView(layoutMainMenu);

        // 隐藏所有游戏界面元素
        if (gameFieldView != null) gameFieldView.setVisibility(View.GONE);
        if (layoutOpponentInfo != null) layoutOpponentInfo.setVisibility(View.GONE);
        if (layoutPlayerInfo != null) layoutPlayerInfo.setVisibility(View.GONE);
        if (layoutActionButtons != null) layoutActionButtons.setVisibility(View.GONE);
        if (layoutChat != null) layoutChat.setVisibility(View.GONE);
        if (dialogContainer != null) dialogContainer.setVisibility(View.GONE);
        if (layoutLobby != null) layoutLobby.setVisibility(View.GONE);

        bindMainMenuButtons();

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
        LanModeDialog dialog = new LanModeDialog(this, new LanModeDialog.OnLanModeListener() {
            @Override
            public void onCreateHostConfirmed(String banlist, String rule, String cardAllowed,
                                              String startLP, String duelMode, String startHand,
                                              String timeLimit, String drawCount,
                                              boolean noCheckDeck, boolean noShuffleDeck,
                                              String hostName, String password) {
                hideMainMenu();
                engine.startLocalServer();
            }

            @Override
            public void onExitLan() {
            }
        });
        dialog.show(layoutMainMenu);
        dialog.setOnDismissListener(() -> restoreMainMenu());
    }

    private void showSingleModeDialog() {
        File botConfFile = new File(AppsSettings.get().getResourcePath(), Constants.CORE_BOT_CONF_PATH);
        List<BotUtil.BotInfo> botList = parseBotConfig(botConfFile);

        File singleDir = new File(AppsSettings.get().getResourcePath(), Constants.CORE_SINGLE_PATH);
        List<PuzzleUtil.PuzzleInfo> puzzleList = loadPuzzleFiles(singleDir);

        SingleModeDialog dialog = new SingleModeDialog(this, new SingleModeDialog.OnSingleModeListener() {
            @Override
            public void onStartBotDuel(String botCommand, String deckFile) {
                hideMainMenu();
                engine.startBotDuel("127.0.0.1", 7911, botCommand, deckFile);
            }

            @Override
            public void onStartSingleMode(String luaFilePath) {
                hideMainMenu();
                engine.startSingleMode(luaFilePath);
            }
        });
        dialog.show(layoutMainMenu, botList, puzzleList);
        dialog.setOnDismissListener(() -> restoreMainMenu());
    }

    private void showReplayModeDialog() {
        File replayDir = new File(AppsSettings.get().getResourcePath(), Constants.CORE_REPLAY_PATH);
        ReplayModeDialog dialog = new ReplayModeDialog(this, (replayPath, startTurn) -> {
            hideMainMenu();
            startReplayPlayback(replayPath, startTurn);
        });
        dialog.show(layoutMainMenu, replayDir);
        dialog.setOnDismissListener(() -> restoreMainMenu());
    }

    private void startReplayPlayback(String replayPath, int startTurn) {
        if (engine == null) return;
        ReplayEngine replayEngine = new ReplayEngine(engine.getField(), soundManager);
        engine.setReplayEngine(replayEngine);

        replayEngine.setListener(new ReplayEngine.ReplayListener() {
            @Override
            public void onReplayStateChanged(ReplayEngine.ReplayState state) {
                runOnUiThread(() -> {
                    switch (state) {
                        case PLAYING:
                            tvPhaseInfo.setText("▶ 回放中");
                            layoutActionButtons.setVisibility(View.GONE);
                            break;
                        case PAUSED:
                            tvPhaseInfo.setText(" 已暂停");
                            break;
                        case FINISHED:
                            tvPhaseInfo.setText("⏹ 回放结束");
                            break;
                    }
                });
            }

            @Override
            public void onReplayFieldChanged() {
                runOnUiThread(() -> gameFieldView.invalidate());
            }

            @Override
            public void onReplayPlayerInfoUpdated(int player) {
                runOnUiThread(() -> {
                    GameField.PlayerField pf = engine.getField().players[player];
                    ReplayReader.ReplayData rd = replayEngine.getReplayData();
                    String name = (rd != null && player < rd.playerNames.size()) ? rd.playerNames.get(player) : "Player " + (player + 1);
                    if (player == 0) {
                        tvPlayerLp.setText("LP: " + pf.lp);
                        tvPlayerName.setText(name);
                    } else {
                        tvOpponentLp.setText("LP: " + pf.lp);
                        tvOpponentName.setText(name);
                    }
                });
            }

            @Override
            public void onReplayPhaseChanged(int phase) {
                runOnUiThread(() -> {
                    ocgcore.enums.DuelPhase dp = ocgcore.enums.DuelPhase.valueOf(phase);
                    String phaseName = dp != null ? dp.name() : "Unknown";
                    tvPhaseInfo.setText("▶ " + phaseName);
                    tvTurnInfo.setText("Turn " + engine.getField().turnCount);
                });
            }

            @Override
            public void onReplayHintMessage(String hint) {
                runOnUiThread(() -> {
                    tvHintMessage.setText(hint);
                    tvHintMessage.setVisibility(View.VISIBLE);
                    mainHandler.postDelayed(() -> tvHintMessage.setVisibility(View.GONE), 3000);
                });
            }

            @Override
            public void onReplayFinished(String result) {
                runOnUiThread(() -> {
                    showResultDialog(result);
                });
            }
        });

        replayEngine.loadAndPlay(replayPath, startTurn);
        showReplayControlOverlay(replayEngine);
    }

    private void showReplayControlOverlay(ReplayEngine replayEngine) {
        LinearLayout controlBar = new LinearLayout(this);
        controlBar.setOrientation(LinearLayout.HORIZONTAL);
        controlBar.setGravity(Gravity.CENTER);
        controlBar.setBackgroundColor(0x88000000);

        String[] labels = {"⏸ 暂停", "▶ 继续", "⏩ 快进", "↩ 撤销", "🔄 交换", "⏹ 停止"};
        for (String label : labels) {
            Button btn = new Button(this);
            btn.setText(label);
            btn.setTextColor(0xFFFFFFFF);
            btn.setBackgroundColor(0x00000000);
            btn.setTextSize(12);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            btn.setLayoutParams(lp);
            controlBar.addView(btn);
        }

        controlBar.getChildAt(0).setOnClickListener(v -> replayEngine.pause());
        controlBar.getChildAt(1).setOnClickListener(v -> replayEngine.resume());
        controlBar.getChildAt(2).setOnClickListener(v -> replayEngine.skipAhead());
        controlBar.getChildAt(3).setOnClickListener(v -> replayEngine.undo());
        controlBar.getChildAt(4).setOnClickListener(v -> replayEngine.swapField());
        controlBar.getChildAt(5).setOnClickListener(v -> {
            replayEngine.stop();
            controlBar.setVisibility(View.GONE);
            restoreMainMenu();
        });

        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        flp.gravity = Gravity.BOTTOM;
        dialogContainer.addView(controlBar, flp);
    }

    private void showDeckEditDialog() {
        setWindowBackground(Constants.CORE_SKIN_PATH + "/" + Constants.CORE_SKIN_BG_DECK);
        new DeckEditDialog(this).show();
    }
    private void setWindowBackground(String relativePath) {
        String path = AppsSettings.get().getResourcePath() + "/" + relativePath;
        File file = new File(path);
        if (file.exists()) {
            try {
                Bitmap bitmap = BitmapFactory.decodeFile(path);
                if (bitmap != null) {
                    getWindow().setBackgroundDrawable(new BitmapDrawable(getResources(), bitmap));
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to load background: " + relativePath, e);
            }
        }
    }


    private void showSettingsDialog() {
        SettingsDialog dialog = new SettingsDialog(this, () -> applySettingsToEngine());
        dialog.show(layoutMainMenu);
        dialog.setOnDismissListener(() -> restoreMainMenu());
    }

    private void applySettingsToEngine() {
        SharedPreferences prefs = getSharedPreferences(getPackageName() + ".settings", Context.MODE_PRIVATE);
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
                    showCardSelectDialog(data);
                    break;
                case 16:
                    showChainSelectDialog(data);
                    break;
                case 18:
                    showPlaceSelectDialog(false);
                    break;
                case 19:
                    showPositionSelectDialog();
                    break;
                case 20:
                    showTributeSelectDialog(data);
                    break;
                case 21:
                    showSortChainDialog(data);
                    break;
                case 22:
                    showCounterSelectDialog(data);
                    break;
                case 23:
                    showSumSelectDialog(data);
                    break;
                case 24:
                    showPlaceSelectDialog(true);
                    break;
                case 25:
                    showSortCardDialog(data);
                    break;
                case 140:
                    showAnnounceRaceDialog();
                    break;
                case 141:
                    showAnnounceAttribDialog();
                    break;
                case 142:
                    showAnnounceCardDialog(data);
                    break;
                case 143:
                    showAnnounceNumberDialog(data);
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
        if (card != null && card.cmdFlag != 0) {
            showCardCommandMenu(card, player, location, sequence);
            return;
        }
        if (card != null && card.isFaceUp() && card.code > 0) {
            showCardInfoPanel(card);
        }
    }

    @Override
    public void onZoneClick(int player, int location, int sequence) {
        Log.d(TAG, "Zone click: p=" + player + " loc=" + location);
        if (isPlaceSelecting) {
            handlePlaceSelection(player, location, sequence);
        }
    }

    @Override
    public void onFieldLongPress(int player, int location, int sequence) {
        Log.d(TAG, "Long press: p=" + player + " loc=" + location + " seq=" + sequence);
        GameField.ClientCard card = engine.getField().getCard(player, location, sequence);
        if (card != null && card.code > 0) {
            showCardInfoPanel(card);
        }
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
        cmdContext = CMD_CONTEXT_BATTLE;
        List<String> options = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();

        for (int i = 0; i < engine.attackableCards.size(); i++) {
            GameEngine.CmdCardInfo info = engine.attackableCards.get(i);
            String cardName = getCardDisplayName(info.code);
            options.add("⚔ " + cardName);
            final int idx = i;
            actions.add(() -> sendResponseInt((idx << 16) + 1));
        }

        for (int i = 0; i < engine.activatableCards.size(); i++) {
            GameEngine.CmdCardInfo info = engine.activatableCards.get(i);
            String cardName = getCardDisplayName(info.code);
            String descStr = info.desc > 0
                    ? DataManager.get().getStringManager().getSystemString(info.desc, "效果")
                    : "发动";
            options.add("✦ " + cardName + " - " + descStr);
            final int idx = i;
            actions.add(() -> sendResponseInt(idx << 16));
        }

        if (engine.showM2) {
            options.add("▶ 进入Main2");
            actions.add(() -> sendResponseInt(2));
        }
        if (engine.showEP) {
            options.add("▶ 结束阶段");
            actions.add(() -> sendResponseInt(3));
        }

        if (options.isEmpty()) {
            sendResponseInt(3);
            return;
        }

        DialogPlus dialog = new DialogPlus(this);
        dialog.setTitle("战斗阶段");
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
            btn.setTextSize(12);
            btn.setSingleLine(false);
            String opt = options.get(i);
            if (opt.startsWith("⚔")) {
                btn.setBackgroundColor(0xFF883333);
            } else if (opt.startsWith("✦")) {
                btn.setBackgroundColor(0xFF886633);
            } else {
                btn.setBackgroundColor(0xFF335577);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = 4;
            btn.setLayoutParams(lp);
            final int idx = i;
            btn.setOnClickListener(v -> {
                actions.get(idx).run();
                dialog.dismiss();
            });
            layoutOptions.addView(btn);
        }

        ScrollView scrollContainer = new ScrollView(this);
        scrollContainer.addView(layoutOptions);
        dialog.setCancelable(false);
        dialog.show();
    }

    private void showIdleCmdDialog(ByteBuffer data) {
        cmdContext = CMD_CONTEXT_IDLE;
        List<String> options = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();

        for (int i = 0; i < engine.summonableCards.size(); i++) {
            GameEngine.CmdCardInfo info = engine.summonableCards.get(i);
            String cardName = getCardDisplayName(info.code);
            options.add("召唤: " + cardName);
            final int idx = i;
            actions.add(() -> sendResponseInt(idx << 16));
        }

        for (int i = 0; i < engine.spsummonableCards.size(); i++) {
            GameEngine.CmdCardInfo info = engine.spsummonableCards.get(i);
            String cardName = getCardDisplayName(info.code);
            options.add("特殊召唤: " + cardName);
            final int idx = i;
            actions.add(() -> sendResponseInt((idx << 16) + 1));
        }

        for (int i = 0; i < engine.reposableCards.size(); i++) {
            GameEngine.CmdCardInfo info = engine.reposableCards.get(i);
            String cardName = getCardDisplayName(info.code);
            options.add("切换表示: " + cardName);
            final int idx = i;
            actions.add(() -> sendResponseInt((idx << 16) + 2));
        }

        for (int i = 0; i < engine.msetableCards.size(); i++) {
            GameEngine.CmdCardInfo info = engine.msetableCards.get(i);
            String cardName = getCardDisplayName(info.code);
            options.add("盖放(怪兽): " + cardName);
            final int idx = i;
            actions.add(() -> sendResponseInt((idx << 16) + 3));
        }

        for (int i = 0; i < engine.ssetableCards.size(); i++) {
            GameEngine.CmdCardInfo info = engine.ssetableCards.get(i);
            String cardName = getCardDisplayName(info.code);
            options.add("设置(魔陷): " + cardName);
            final int idx = i;
            actions.add(() -> sendResponseInt((idx << 16) + 4));
        }

        for (int i = 0; i < engine.activatableCards.size(); i++) {
            GameEngine.CmdCardInfo info = engine.activatableCards.get(i);
            String cardName = getCardDisplayName(info.code);
            String descStr = info.desc > 0
                    ? DataManager.get().getStringManager().getSystemString(info.desc, "效果")
                    : "发动";
            options.add("发动: " + cardName + " - " + descStr);
            final int idx = i;
            actions.add(() -> sendResponseInt((idx << 16) + 5));
        }

        if (engine.showBP) {
            options.add("▶ 进入战斗阶段");
            actions.add(() -> sendResponseInt(6));
        }
        if (engine.showEP) {
            options.add("▶ 结束阶段");
            actions.add(() -> sendResponseInt(7));
        }
        if (engine.showShuffle) {
            options.add("🔀 洗牌");
            actions.add(() -> sendResponseInt(8));
        }

        if (options.isEmpty()) {
            sendResponseInt(7);
            return;
        }

        DialogPlus dialog = new DialogPlus(this);
        dialog.setTitle("主要阶段");
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
            btn.setTextSize(12);
            btn.setSingleLine(false);
            String opt = options.get(i);
            if (opt.startsWith("召唤")) {
                btn.setBackgroundColor(0xFF338833);
            } else if (opt.startsWith("特殊召唤")) {
                btn.setBackgroundColor(0xFF338888);
            } else if (opt.startsWith("切换表示")) {
                btn.setBackgroundColor(0xFF555588);
            } else if (opt.startsWith("盖放") || opt.startsWith("设置")) {
                btn.setBackgroundColor(0xFF555555);
            } else if (opt.startsWith("发动")) {
                btn.setBackgroundColor(0xFF886633);
            } else {
                btn.setBackgroundColor(0xFF335577);
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = 4;
            btn.setLayoutParams(lp);
            final int idx = i;
            btn.setOnClickListener(v -> {
                actions.get(idx).run();
                dialog.dismiss();
            });
            layoutOptions.addView(btn);
        }

        ScrollView scrollContainer = new ScrollView(this);
        scrollContainer.addView(layoutOptions);
        dialog.setCancelable(false);
        dialog.show();
    }

    private void showCardCommandMenu(GameField.ClientCard card, int player, int location, int sequence) {
        int flag = card.cmdFlag;
        List<String> options = new ArrayList<>();
        List<Runnable> actions = new ArrayList<>();
        String cardName = getCardDisplayName(card.code);

        if ((flag & GameEngine.COMMAND_ACTIVATE) != 0) {
            List<GameEngine.CmdCardInfo> matches = new ArrayList<>();
            for (int i = 0; i < engine.activatableCards.size(); i++) {
                if (engine.activatableCards.get(i).card == card) {
                    matches.add(engine.activatableCards.get(i));
                }
            }
            if (matches.size() == 1) {
                GameEngine.CmdCardInfo info = matches.get(0);
                String descStr = info.desc > 0
                        ? DataManager.get().getStringManager().getSystemString(info.desc, "发动")
                        : "发动";
                options.add("✦ " + descStr);
                final int idx = info.index;
                if (cmdContext == CMD_CONTEXT_BATTLE) {
                    actions.add(() -> sendResponseInt(idx << 16));
                } else {
                    actions.add(() -> sendResponseInt((idx << 16) + 5));
                }
            } else if (matches.size() > 1) {
                for (GameEngine.CmdCardInfo info : matches) {
                    String descStr = info.desc > 0
                            ? DataManager.get().getStringManager().getSystemString(info.desc, "效果")
                            : "效果";
                    options.add("✦ " + descStr);
                    final int idx = info.index;
                    if (cmdContext == CMD_CONTEXT_BATTLE) {
                        actions.add(() -> sendResponseInt(idx << 16));
                    } else {
                        actions.add(() -> sendResponseInt((idx << 16) + 5));
                    }
                }
            }
        }

        if ((flag & GameEngine.COMMAND_ATTACK) != 0) {
            int idx = -1;
            for (int i = 0; i < engine.attackableCards.size(); i++) {
                if (engine.attackableCards.get(i).card == card) {
                    idx = engine.attackableCards.get(i).index;
                    break;
                }
            }
            if (idx >= 0) {
                options.add("⚔ 攻击");
                final int attackIdx = idx;
                actions.add(() -> sendResponseInt((attackIdx << 16) + 1));
            }
        }

        if ((flag & GameEngine.COMMAND_SUMMON) != 0 && cmdContext == CMD_CONTEXT_IDLE) {
            int idx = -1;
            for (int i = 0; i < engine.summonableCards.size(); i++) {
                if (engine.summonableCards.get(i).card == card) {
                    idx = engine.summonableCards.get(i).index;
                    break;
                }
            }
            if (idx >= 0) {
                options.add("召唤");
                final int summonIdx = idx;
                actions.add(() -> sendResponseInt(summonIdx << 16));
            }
        }

        if ((flag & GameEngine.COMMAND_SPSUMMON) != 0 && cmdContext == CMD_CONTEXT_IDLE) {
            int idx = -1;
            for (int i = 0; i < engine.spsummonableCards.size(); i++) {
                if (engine.spsummonableCards.get(i).card == card) {
                    idx = engine.spsummonableCards.get(i).index;
                    break;
                }
            }
            if (idx >= 0) {
                options.add("特殊召唤");
                final int spIdx = idx;
                actions.add(() -> sendResponseInt((spIdx << 16) + 1));
            }
        }

        if ((flag & GameEngine.COMMAND_REPOS) != 0 && cmdContext == CMD_CONTEXT_IDLE) {
            int idx = -1;
            for (int i = 0; i < engine.reposableCards.size(); i++) {
                if (engine.reposableCards.get(i).card == card) {
                    idx = engine.reposableCards.get(i).index;
                    break;
                }
            }
            if (idx >= 0) {
                String reposText;
                if ((card.position & 0xA) != 0) {
                    reposText = "反转";
                } else if (card.isAttack()) {
                    reposText = "改为守备";
                } else {
                    reposText = "改为攻击";
                }
                options.add(reposText);
                final int reposIdx = idx;
                actions.add(() -> sendResponseInt((reposIdx << 16) + 2));
            }
        }

        if ((flag & GameEngine.COMMAND_MSET) != 0 && cmdContext == CMD_CONTEXT_IDLE) {
            int idx = -1;
            for (int i = 0; i < engine.msetableCards.size(); i++) {
                if (engine.msetableCards.get(i).card == card) {
                    idx = engine.msetableCards.get(i).index;
                    break;
                }
            }
            if (idx >= 0) {
                options.add("盖放(怪兽)");
                final int msetIdx = idx;
                actions.add(() -> sendResponseInt((msetIdx << 16) + 3));
            }
        }

        if ((flag & GameEngine.COMMAND_SSET) != 0 && cmdContext == CMD_CONTEXT_IDLE) {
            int idx = -1;
            for (int i = 0; i < engine.ssetableCards.size(); i++) {
                if (engine.ssetableCards.get(i).card == card) {
                    idx = engine.ssetableCards.get(i).index;
                    break;
                }
            }
            if (idx >= 0) {
                options.add("设置(魔陷)");
                final int ssetIdx = idx;
                actions.add(() -> sendResponseInt((ssetIdx << 16) + 4));
            }
        }

        options.add("ℹ 查看卡片信息");
        actions.add(() -> showCardInfoPanel(card));

        DialogPlus dialog = new DialogPlus(this);
        dialog.setTitle(cardName);
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
            btn.setTextSize(13);
            btn.setBackgroundColor(0xFF335577);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = 4;
            btn.setLayoutParams(lp);
            final int idx = i;
            btn.setOnClickListener(v -> {
                actions.get(idx).run();
                dialog.dismiss();
            });
            layoutOptions.addView(btn);
        }

        dialog.setRightButtonText("取消");
        dialog.setRightButtonListener((d, w) -> d.dismiss());
        dialog.show();
    }

    private void showPlaceSelectDialog(boolean isDisfield) {
        isPlaceSelecting = true;
        int mask = engine.selectFieldMask;
        gameFieldView.setHighlightFieldMask(mask);
        gameFieldView.invalidate();
        String msg = isDisfield ? "请选择要禁用的区域" : "请选择放置位置";
        showHintMessage(msg);
    }

    private void handlePlaceSelection(int player, int location, int sequence) {
        int bitPos = getZoneBitPos(player, location, sequence);
        if (bitPos < 0 || (engine.selectFieldMask & (1 << bitPos)) == 0) {
            showHintMessage("该区域不可选择");
            return;
        }
        isPlaceSelecting = false;
        gameFieldView.setHighlightFieldMask(0);

        int respPlayer = player;
        int respLocation;
        if (location == 0x04) {
            respLocation = 0x04;
        } else if (location == 0x08) {
            respLocation = 0x08;
        } else {
            respLocation = location;
        }
        int respSeq = sequence;

        if (respPlayer != engine.getClient().selfType) {
            respPlayer = engine.getClient().selfType;
        }

        ByteBuffer buf = ByteBuffer.allocate(3);
        buf.put((byte) respPlayer);
        buf.put((byte) respLocation);
        buf.put((byte) respSeq);
        engine.sendResponse(buf.array());
    }

    private void showSideDeckPicker(File[] deckFiles) {
        DialogPlus dialog = new DialogPlus(this);
        dialog.setTitle("选择卡组 (副卡组替换)");
        dialog.setContentView(R.layout.dialog_game_select);
        View contentView = dialog.getContentView();
        contentView.findViewById(getResId("tv_select_title", "id")).setVisibility(View.GONE);
        contentView.findViewById(getResId("tv_select_hint", "id")).setVisibility(View.GONE);
        contentView.findViewById(getResId("layout_select_buttons", "id")).setVisibility(View.GONE);
        LinearLayout layoutOptions = contentView.findViewById(getResId("layout_options", "id"));

        for (int i = 0; i < deckFiles.length; i++) {
            Button btn = new Button(this);
            btn.setText(deckFiles[i].getName().replace(Constants.YDK_FILE_EX, ""));
            btn.setTextColor(0xFFFFFFFF);
            btn.setBackgroundColor(0xFF335577);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = 4;
            btn.setLayoutParams(lp);
            final int pos = i;
            btn.setOnClickListener(v -> {
                loadAndSendDeckWithSide(deckFiles[pos]);
                dialog.dismiss();
            });
            layoutOptions.addView(btn);
        }

        ScrollView scrollContainer = new ScrollView(this);
        scrollContainer.addView(layoutOptions);
        dialog.setLeftButtonText("跳过");
        dialog.setLeftButtonListener((d, w) -> {
            d.dismiss();
            sendSideSkip();
        });
        dialog.setCancelable(false);
        dialog.show();
    }

    private void loadAndSendDeckWithSide(File ydkFile) {
        new Thread(() -> {
            List<Integer> main = new ArrayList<>();
            List<Integer> extra = new ArrayList<>();
            List<Integer> side = new ArrayList<>();
            try (BufferedReader reader = new BufferedReader(new FileReader(ydkFile))) {
                String line;
                int section = 0;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#") && !line.equalsIgnoreCase("#main")
                            && !line.equalsIgnoreCase("#extra") && !line.equalsIgnoreCase("!side"))
                        continue;
                    if (line.equalsIgnoreCase("#main")) {
                        section = 1;
                        continue;
                    }
                    if (line.equalsIgnoreCase("#extra")) {
                        section = 2;
                        continue;
                    }
                    if (line.equalsIgnoreCase("!side")) {
                        section = 3;
                        continue;
                    }
                    if (line.startsWith("#")) continue;
                    try {
                        int code = Integer.parseInt(line);
                        switch (section) {
                            case 1:
                                main.add(code);
                                break;
                            case 2:
                                extra.add(code);
                                break;
                            case 3:
                                side.add(code);
                                break;
                        }
                    } catch (NumberFormatException e) { /* skip */ }
                }
            } catch (Exception e) {
                mainHandler.post(() -> showHintMessage("卡组加载失败"));
                return;
            }

            if (side.isEmpty()) {
                engine.sendDeckUpdate(main, extra, side);
                mainHandler.post(() -> showHintMessage("卡组已发送 (无副卡组)"));
                return;
            }

            final List<Integer> fMain = new ArrayList<>(main);
            final List<Integer> fExtra = new ArrayList<>(extra);
            final List<Integer> fSide = new ArrayList<>(side);
            mainHandler.post(() -> showSideSwapUI(fMain, fExtra, fSide));
        }, "SideDeckLoad").start();
    }

    private void showSideSwapUI(List<Integer> main, List<Integer> extra, List<Integer> side) {
        DialogPlus dialog = new DialogPlus(this);
        dialog.setTitle("副卡组替换 (点击交换)");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (8 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);

        TextView tvMain = new TextView(this);
        tvMain.setTextColor(0xFFFFFFFF);
        tvMain.setTextSize(12);
        tvMain.setText("Main: " + main.size() + "张");
        root.addView(tvMain);

        LinearLayout mainRow = new LinearLayout(this);
        mainRow.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < Math.min(main.size(), 10); i++) {
            Button btn = new Button(this);
            btn.setText(getCardDisplayName(main.get(i)).substring(0, Math.min(4, getCardDisplayName(main.get(i)).length())));
            btn.setTextSize(9);
            btn.setTextColor(0xFFFFFFFF);
            btn.setBackgroundColor(0xFF335577);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            lp.rightMargin = 2;
            btn.setLayoutParams(lp);
            final int idx = i;
            btn.setOnClickListener(v -> {
                int removed = main.remove(idx);
                side.add(removed);
                showHintMessage("移出: " + getCardDisplayName(removed));
                tvMain.setText("Main: " + main.size() + "张 | Side: " + side.size() + "张");
            });
            mainRow.addView(btn);
        }
        root.addView(mainRow);

        TextView tvSide = new TextView(this);
        tvSide.setTextColor(0xFFCCCCCC);
        tvSide.setTextSize(12);
        tvSide.setText("Side: " + side.size() + "张");
        root.addView(tvSide);

        LinearLayout sideRow = new LinearLayout(this);
        sideRow.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < Math.min(side.size(), 10); i++) {
            Button btn = new Button(this);
            btn.setText(getCardDisplayName(side.get(i)).substring(0, Math.min(4, getCardDisplayName(side.get(i)).length())));
            btn.setTextSize(9);
            btn.setTextColor(0xFFFFFFFF);
            btn.setBackgroundColor(0xFF557733);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1);
            lp.rightMargin = 2;
            btn.setLayoutParams(lp);
            final int idx = i;
            btn.setOnClickListener(v -> {
                int removed = side.remove(idx);
                main.add(removed);
                showHintMessage("加入: " + getCardDisplayName(removed));
                tvMain.setText("Main: " + main.size() + "张 | Side: " + side.size() + "张");
            });
            sideRow.addView(btn);
        }
        root.addView(sideRow);

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(root);
        dialog.setContentView(scrollView);
        dialog.setLeftButtonText("确认发送");
        dialog.setLeftButtonListener((d, w) -> {
            engine.sendDeckUpdate(main, extra, side);
            showHintMessage("副卡组替换完成: " + main.size() + "+" + extra.size() + "+" + side.size());
            d.dismiss();
        });
        dialog.setRightButtonText("跳过");
        dialog.setRightButtonListener((d, w) -> {
            d.dismiss();
            sendSideSkip();
        });
        dialog.setCancelable(false);
        dialog.show();
    }

    private void sendSideSkip() {
        new Thread(() -> {
            File deckDir = new File(AppsSettings.get().getResourcePath(), Constants.CORE_DECK_PATH);
            File[] files = deckDir.exists()
                    ? deckDir.listFiles((dir, name) -> name.endsWith(Constants.YDK_FILE_EX))
                    : null;
            if (files != null && files.length > 0) {
                loadAndSendDeck(files[0]);
            }
        }, "SideSkip").start();
    }

    private int getZoneBitPos(int player, int location, int sequence) {
        int base = (player == engine.getClient().selfType) ? 0 : 16;
        if (location == 0x04) return base + sequence;
        if (location == 0x08) {
            if (sequence < 6) return base + 8 + sequence;
            if (sequence == 6) return base + 14;
            if (sequence == 7) return base + 15;
        }
        return -1;
    }

    private void sendCardSelectResponse(List<CardSelectInfo> cardInfos, boolean[] selected, int count) {
        ByteBuffer buf = ByteBuffer.allocate(1 + count);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.put((byte) count);
        for (int i = 0; i < selected.length; i++) {
            if (selected[i]) {
                buf.put((byte) cardInfos.get(i).selectSeq);
            }
        }
        engine.sendResponse(buf.array());
    }

    private String getCardDisplayName(int code) {
        if (code <= 0) return "???";
        ocgcore.data.Card card = DataManager.get().getCardManager().getCard(code);
        if (card != null && card.Name != null) return card.Name;
        return "Card#" + code;
    }

    private String getLocationName(int location) {
        switch (location) {
            case 0x01:
                return "卡组";
            case 0x02:
                return "手牌";
            case 0x04:
                return "怪兽区";
            case 0x08:
                return "魔陷区";
            case 0x10:
                return "墓地";
            case 0x20:
                return "除外";
            case 0x40:
                return "额外";
            case 0x80:
                return "超量素材";
            default:
                return "区域" + location;
        }
    }

    private static class CardSelectInfo {
        int code, controler, location, sequence, subSeq, selectSeq;

        CardSelectInfo(int code, int ctrl, int loc, int seq, int sub, int selSeq) {
            this.code = code;
            this.controler = ctrl;
            this.location = loc;
            this.sequence = seq;
            this.subSeq = sub;
            this.selectSeq = selSeq;
        }
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
        File deckDir = new File(AppsSettings.get().getResourcePath(), Constants.CORE_DECK_PATH);
        File[] deckFiles = deckDir.exists()
                ? deckDir.listFiles((dir, name) -> name.endsWith(Constants.YDK_FILE_EX))
                : null;

        List<String> deckNames = new ArrayList<>();
        if (deckFiles != null && deckFiles.length > 0) {
            for (File f : deckFiles) {
                deckNames.add(f.getName().replace(Constants.YDK_FILE_EX, ""));
            }
        } else {
            deckNames.add("（暂无卡组文件）");
        }

        final File[] finalDeckFiles = deckFiles;
        DialogPlus dialog = new DialogPlus(this);
        dialog.setTitle("选择卡组");
        dialog.setContentView(R.layout.dialog_game_select);
        View contentView = dialog.getContentView();
        contentView.findViewById(getResId("tv_select_title", "id")).setVisibility(View.GONE);
        contentView.findViewById(getResId("tv_select_hint", "id")).setVisibility(View.GONE);
        contentView.findViewById(getResId("layout_select_buttons", "id")).setVisibility(View.GONE);
        LinearLayout layoutOptions = contentView.findViewById(getResId("layout_options", "id"));

        for (int i = 0; i < deckNames.size(); i++) {
            Button btn = new Button(this);
            btn.setText(deckNames.get(i));
            btn.setTextColor(0xFFFFFFFF);
            btn.setBackgroundColor(0xFF335577);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = 4;
            btn.setLayoutParams(lp);
            final int pos = i;
            btn.setOnClickListener(v -> {
                if (finalDeckFiles != null && pos < finalDeckFiles.length) {
                    loadAndSendDeck(finalDeckFiles[pos]);
                }
                dialog.dismiss();
            });
            layoutOptions.addView(btn);
        }

        dialog.setRightButtonText("取消");
        dialog.setRightButtonListener((d, w) -> {
            d.dismiss();
            engine.disconnect();
        });
        dialog.setCancelable(false);
        dialog.show();
    }

    private void loadAndSendDeck(File ydkFile) {
        new Thread(() -> {
            List<Integer> main = new ArrayList<>();
            List<Integer> extra = new ArrayList<>();
            List<Integer> side = new ArrayList<>();

            try (BufferedReader reader = new BufferedReader(new FileReader(ydkFile))) {
                String line;
                int section = 0;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    if (line.equalsIgnoreCase("#main")) {
                        section = 1;
                        continue;
                    }
                    if (line.equalsIgnoreCase("#extra")) {
                        section = 2;
                        continue;
                    }
                    if (line.equalsIgnoreCase("!side")) {
                        section = 3;
                        continue;
                    }
                    try {
                        int code = Integer.parseInt(line);
                        switch (section) {
                            case 1:
                                main.add(code);
                                break;
                            case 2:
                                extra.add(code);
                                break;
                            case 3:
                                side.add(code);
                                break;
                        }
                    } catch (NumberFormatException e) { /* skip */ }
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to load deck: " + ydkFile.getName(), e);
                mainHandler.post(() -> showHintMessage("卡组加载失败"));
                return;
            }

            engine.sendDeckUpdate(main, extra, side);
            mainHandler.post(() -> {
                showHintMessage("卡组已发送: " + main.size() + "+" + extra.size() + "+" + side.size());
            });
        }, "DeckLoad").start();
    }

    private void showSideSelectDialog() {
        showHintMessage("副卡组替换 - 请在大厅中选择卡组");
    }

    private void showChainSelectDialog(ByteBuffer data) {
        if (data == null || data.remaining() < 2) {
            sendResponseInt(-1);
            return;
        }
        int count = data.get() & 0xFF;
        int specount = data.get() & 0xFF;
        if (data.remaining() < 8) {
            sendResponseInt(-1);
            return;
        }
        int hint0 = data.getInt();
        int hint1 = data.getInt();

        List<String> chainOptions = new ArrayList<>();
        List<Integer> chainFlags = new ArrayList<>();
        for (int i = 0; i < count && data.remaining() >= 14; i++) {
            int flag = data.get() & 0xFF;
            int forced = data.get() & 0xFF;
            flag |= forced << 8;
            int code = data.getInt();
            int ctrl = data.get() & 0xFF;
            int loc = data.get() & 0xFF;
            int seq = data.get() & 0xFF;
            int subSeq = data.get() & 0xFF;
            int desc = data.getInt();

            String cardName = getCardDisplayName(code);
            String descStr = desc > 0 ? DataManager.get().getStringManager().getSystemString(desc, "效果") : "效果";
            chainOptions.add(cardName + " - " + descStr);
            chainFlags.add(flag);
        }

        if (chainOptions.isEmpty()) {
            sendResponseInt(-1);
            return;
        }

        boolean hasForced = false;
        for (int f : chainFlags) {
            if ((f & 0x100) != 0) {
                hasForced = true;
                break;
            }
        }

        if (hasForced) {
            for (int i = 0; i < chainFlags.size(); i++) {
                if ((chainFlags.get(i) & 0x100) != 0) {
                    sendResponseInt(i);
                    return;
                }
            }
        }

        DialogPlus dialog = new DialogPlus(this);
        dialog.setTitle("连锁选择");
        dialog.setContentView(R.layout.dialog_game_select);
        View contentView = dialog.getContentView();
        contentView.findViewById(getResId("tv_select_title", "id")).setVisibility(View.GONE);
        contentView.findViewById(getResId("tv_select_hint", "id")).setVisibility(View.GONE);
        contentView.findViewById(getResId("layout_select_buttons", "id")).setVisibility(View.GONE);
        LinearLayout layoutOptions = contentView.findViewById(getResId("layout_options", "id"));

        for (int i = 0; i < chainOptions.size(); i++) {
            Button btn = new Button(this);
            btn.setText(chainOptions.get(i));
            btn.setTextColor(0xFFFFFFFF);
            btn.setBackgroundColor((chainFlags.get(i) & 0x100) != 0 ? 0xFFAA3333 : 0xFF335577);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = 4;
            btn.setLayoutParams(lp);
            final int idx = i;
            btn.setOnClickListener(v -> {
                sendResponseInt(idx);
                dialog.dismiss();
            });
            layoutOptions.addView(btn);
        }

        dialog.setRightButtonText("不连锁");
        dialog.setRightButtonListener((d, w) -> {
            sendResponseInt(-1);
            d.dismiss();
        });
        dialog.setCancelable(false);
        dialog.show();
    }

    private void showCardSelectDialog(ByteBuffer data) {
        if (data == null || data.remaining() < 4) {
            sendResponseInt(0);
            return;
        }
        int selectType = data.get() & 0xFF;
        int selectPlayer = data.get() & 0xFF;
        int min = data.get() & 0xFF;
        int max = data.get() & 0xFF;

        List<CardSelectInfo> cardInfos = new ArrayList<>();
        int count = data.remaining() >= 1 ? (data.get() & 0xFF) : 0;
        for (int i = 0; i < count && data.remaining() >= 4; i++) {
            int code = data.getInt();
            int ctrl = data.get() & 0xFF;
            int loc = data.get() & 0xFF;
            int seq = data.get() & 0xFF;
            int subSeq = data.remaining() >= 1 ? (data.get() & 0xFF) : 0;
            cardInfos.add(new CardSelectInfo(code, ctrl, loc, seq, subSeq, i));
        }

        if (cardInfos.isEmpty()) {
            sendResponseInt(0);
            return;
        }

        boolean[] selected = new boolean[cardInfos.size()];

        DialogPlus dialog = new DialogPlus(this);
        dialog.setTitle("选择卡片 (" + min + "-" + max + ")");
        dialog.setContentView(R.layout.dialog_game_select);
        View contentView = dialog.getContentView();
        contentView.findViewById(getResId("tv_select_title", "id")).setVisibility(View.GONE);
        contentView.findViewById(getResId("tv_select_hint", "id")).setVisibility(View.GONE);
        contentView.findViewById(getResId("layout_select_buttons", "id")).setVisibility(View.GONE);
        LinearLayout layoutOptions = contentView.findViewById(getResId("layout_options", "id"));

        TextView tvCount = new TextView(this);
        tvCount.setTextColor(0xFFFFFF00);
        tvCount.setTextSize(14);
        tvCount.setText("已选: 0/" + max);
        layoutOptions.addView(tvCount);

        for (int i = 0; i < cardInfos.size(); i++) {
            CardSelectInfo info = cardInfos.get(i);
            Button btn = new Button(this);
            String cardName = getCardDisplayName(info.code);
            String locName = getLocationName(info.location);
            btn.setText(cardName + " [" + locName + "]");
            btn.setTextColor(0xFFFFFFFF);
            btn.setTextSize(12);
            btn.setSingleLine(false);
            btn.setBackgroundColor(0xFF335577);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = 4;
            btn.setLayoutParams(lp);

            final int idx = i;
            btn.setOnClickListener(v -> {
                selected[idx] = !selected[idx];
                int selCount = 0;
                for (boolean b : selected) if (b) selCount++;
                tvCount.setText("已选: " + selCount + "/" + max);
                btn.setBackgroundColor(selected[idx] ? 0xFF00AA44 : 0xFF335577);

                if (selCount >= min && selCount <= max) {
                    sendCardSelectResponse(cardInfos, selected, selCount);
                    dialog.dismiss();
                }
            });
            layoutOptions.addView(btn);
        }

        if (min == 0) {
            dialog.setRightButtonText("取消");
            dialog.setRightButtonListener((d, w) -> {
                ByteBuffer buf = ByteBuffer.allocate(1);
                buf.put((byte) 0);
                engine.sendResponse(buf.array());
                d.dismiss();
            });
        }

        ScrollView scrollContainer = new ScrollView(this);
        scrollContainer.addView(layoutOptions);
        dialog.setCancelable(false);
        dialog.show();
    }

    private void showTributeSelectDialog(ByteBuffer data) {
        showCardSelectDialog(data);
    }

    private void showSortChainDialog(ByteBuffer data) {
        if (data == null || data.remaining() < 1) {
            sendResponseInt(0);
            return;
        }
        int count = data.get() & 0xFF;
        if (count <= 1) {
            sendResponseInt(0);
            return;
        }
        showHintMessage("连锁排序: 自动排序");
        sendResponseInt(0);
    }

    private void showCounterSelectDialog(ByteBuffer data) {
        if (data == null || data.remaining() < 5) {
            sendResponseInt(0);
            return;
        }
        int player = data.get() & 0xFF;
        int counterType = data.getShort() & 0xFFFF;
        int count = data.get() & 0xFF;
        int descId = data.remaining() >= 4 ? data.getInt() : 0;

        DialogPlus dialog = new DialogPlus(this);
        dialog.setTitle("选择指示物数量");
        dialog.setMessage("请选择要放置的指示物数量 (1-" + count + ")");
        dialog.setContentView(R.layout.dialog_game_select);
        View contentView = dialog.getContentView();
        contentView.findViewById(getResId("tv_select_title", "id")).setVisibility(View.GONE);
        contentView.findViewById(getResId("tv_select_hint", "id")).setVisibility(View.GONE);
        contentView.findViewById(getResId("layout_select_buttons", "id")).setVisibility(View.GONE);
        LinearLayout layoutOptions = contentView.findViewById(getResId("layout_options", "id"));

        for (int i = 1; i <= count; i++) {
            Button btn = new Button(this);
            btn.setText(String.valueOf(i));
            btn.setTextColor(0xFFFFFFFF);
            btn.setBackgroundColor(0xFF335577);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = 4;
            btn.setLayoutParams(lp);
            final int val = i;
            btn.setOnClickListener(v -> {
                sendResponseInt(val);
                dialog.dismiss();
            });
            layoutOptions.addView(btn);
        }
        dialog.setCancelable(false);
        dialog.show();
    }

    private void showSumSelectDialog(ByteBuffer data) {
        if (data == null || data.remaining() < 7) {
            sendResponseInt(0);
            return;
        }

        int selectMode = data.get() & 0xFF;
        int selectingPlayer = data.get() & 0xFF;
        int sumVal = data.get() & 0xFF;
        int minCount = data.get() & 0xFF;
        int maxCount = data.get() & 0xFF;
        int mustCount = data.get() & 0xFF;

        sumSelectValue = sumVal;
        sumSelectMin = minCount;
        sumSelectMax = maxCount;

        List<SumCardInfo> mustCards = new ArrayList<>();
        for (int i = 0; i < mustCount && data.remaining() >= 8; i++) {
            int code = data.getInt();
            int ctrl = data.get() & 0xFF;
            int loc = data.get() & 0xFF;
            int seq = data.get() & 0xFF;
            int subSeq = data.get() & 0xFF;
            int opParam = data.remaining() >= 4 ? data.getInt() : 0;
            int value = (selectMode == 0) ? opParam : (opParam >> 16);
            if (value <= 0) value = 1;
            mustCards.add(new SumCardInfo(code, ctrl, loc, seq, opParam, value, i));
        }

        int count = data.remaining() >= 1 ? (data.get() & 0xFF) : 0;
        sumCardInfos = new ArrayList<>();
        for (int i = 0; i < count && data.remaining() >= 8; i++) {
            int code = data.getInt();
            int ctrl = data.get() & 0xFF;
            int loc = data.get() & 0xFF;
            int seq = data.get() & 0xFF;
            int subSeq = data.get() & 0xFF;
            int opParam = data.remaining() >= 4 ? data.getInt() : 0;
            int value = (selectMode == 0) ? opParam : (opParam >> 16);
            if (value <= 0) value = 1;
            sumCardInfos.add(new SumCardInfo(code, ctrl, loc, seq, opParam, value, i));
        }

        sumSelected = new boolean[sumCardInfos.size()];
        for (SumCardInfo must : mustCards) {
            for (int i = 0; i < sumCardInfos.size(); i++) {
                SumCardInfo sc = sumCardInfos.get(i);
                if (sc.code == must.code && sc.controler == must.controler
                        && sc.location == must.location && sc.sequence == must.sequence) {
                    sumSelected[i] = true;
                    break;
                }
            }
        }

        isSelectingSum = true;
        showSumSelectUI(mustCards.size());
    }

    private void showSumSelectUI(int mustCount) {
        DialogPlus dialog = new DialogPlus(this);
        dialog.setTitle("选择卡片 (总和=" + sumSelectValue + ")");
        dialog.setContentView(R.layout.dialog_game_select);
        View contentView = dialog.getContentView();
        contentView.findViewById(getResId("tv_select_title", "id")).setVisibility(View.GONE);
        contentView.findViewById(getResId("tv_select_hint", "id")).setVisibility(View.GONE);
        contentView.findViewById(getResId("layout_select_buttons", "id")).setVisibility(View.GONE);
        LinearLayout layoutOptions = contentView.findViewById(getResId("layout_options", "id"));

        TextView tvSum = new TextView(this);
        tvSum.setTextColor(0xFFFFFF00);
        tvSum.setTextSize(14);
        layoutOptions.addView(tvSum);

        int currentSum = 0;
        int selectedCount = 0;
        for (int i = 0; i < sumCardInfos.size(); i++) {
            if (sumSelected[i]) {
                currentSum += sumCardInfos.get(i).value;
                selectedCount++;
            }
        }
        tvSum.setText("当前: " + currentSum + " / " + sumSelectValue + " (已选" + selectedCount + "张)");

        for (int i = 0; i < sumCardInfos.size(); i++) {
            SumCardInfo info = sumCardInfos.get(i);
            Button btn = new Button(this);
            String cardName = getCardDisplayName(info.code);
            btn.setText(cardName + " [值:" + info.value + "]");
            btn.setTextColor(0xFFFFFFFF);
            btn.setTextSize(12);
            btn.setSingleLine(false);
            btn.setBackgroundColor(sumSelected[i] ? 0xFF00AA44 : 0xFF335577);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = 4;
            btn.setLayoutParams(lp);

            final int idx = i;
            btn.setOnClickListener(v -> {
                sumSelected[idx] = !sumSelected[idx];
                int newSum = 0;
                int newCount = 0;
                for (int j = 0; j < sumCardInfos.size(); j++) {
                    if (sumSelected[j]) {
                        newSum += sumCardInfos.get(j).value;
                        newCount++;
                    }
                }
                tvSum.setText("当前: " + newSum + " / " + sumSelectValue + " (已选" + newCount + "张)");
                btn.setBackgroundColor(sumSelected[idx] ? 0xFF00AA44 : 0xFF335577);

                if (newSum == sumSelectValue && newCount >= sumSelectMin && newCount <= sumSelectMax) {
                    sendSumResponse();
                    dialog.dismiss();
                }
            });
            layoutOptions.addView(btn);
        }

        if (sumSelectMin == 0) {
            dialog.setRightButtonText("取消");
            dialog.setRightButtonListener((d, w) -> {
                sendResponseInt(0);
                d.dismiss();
            });
        }

        ScrollView scrollContainer = new ScrollView(this);
        scrollContainer.addView(layoutOptions);
        dialog.setCancelable(false);
        dialog.show();
    }

    private void sendSumResponse() {
        int selCount = 0;
        for (boolean b : sumSelected) if (b) selCount++;
        ByteBuffer buf = ByteBuffer.allocate(1 + selCount);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.put((byte) selCount);
        for (int i = 0; i < sumSelected.length; i++) {
            if (sumSelected[i]) {
                buf.put((byte) sumCardInfos.get(i).index);
            }
        }
        engine.sendResponse(buf.array());
    }

    private void showAnnounceRaceDialog() {
        String[] races = {"战士", "魔法师", "炎", "水", "雷", "岩石", "植物", "兽",
                "兽战士", "恐龙", "昆虫", "爬虫", "海龙", "鱼", "机械", "超能",
                "幻神兽", "创造神", "龙"};
        DialogPlus dialog = new DialogPlus(this);
        dialog.setTitle("选择种族");
        dialog.setContentView(R.layout.dialog_game_select);
        View contentView = dialog.getContentView();
        contentView.findViewById(getResId("tv_select_title", "id")).setVisibility(View.GONE);
        contentView.findViewById(getResId("tv_select_hint", "id")).setVisibility(View.GONE);
        contentView.findViewById(getResId("layout_select_buttons", "id")).setVisibility(View.GONE);
        LinearLayout layoutOptions = contentView.findViewById(getResId("layout_options", "id"));

        for (int i = 0; i < races.length; i++) {
            Button btn = new Button(this);
            btn.setText(races[i]);
            btn.setTextColor(0xFFFFFFFF);
            btn.setBackgroundColor(0xFF335577);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = 4;
            btn.setLayoutParams(lp);
            final int raceBit = 1 << i;
            btn.setOnClickListener(v -> {
                sendResponseInt(raceBit);
                dialog.dismiss();
            });
            layoutOptions.addView(btn);
        }
        dialog.setCancelable(false);
        dialog.show();
    }

    private void showAnnounceAttribDialog() {
        String[] attribs = {"光", "暗", "水", "炎", "地", "风", "神"};
        DialogPlus dialog = new DialogPlus(this);
        dialog.setTitle("选择属性");
        dialog.setContentView(R.layout.dialog_game_select);
        View contentView = dialog.getContentView();
        contentView.findViewById(getResId("tv_select_title", "id")).setVisibility(View.GONE);
        contentView.findViewById(getResId("tv_select_hint", "id")).setVisibility(View.GONE);
        contentView.findViewById(getResId("layout_select_buttons", "id")).setVisibility(View.GONE);
        LinearLayout layoutOptions = contentView.findViewById(getResId("layout_options", "id"));

        for (int i = 0; i < attribs.length; i++) {
            Button btn = new Button(this);
            btn.setText(attribs[i]);
            btn.setTextColor(0xFFFFFFFF);
            btn.setBackgroundColor(0xFF335577);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = 4;
            btn.setLayoutParams(lp);
            final int attrBit = 1 << i;
            btn.setOnClickListener(v -> {
                sendResponseInt(attrBit);
                dialog.dismiss();
            });
            layoutOptions.addView(btn);
        }
        dialog.setCancelable(false);
        dialog.show();
    }

    private void showAnnounceCardDialog(ByteBuffer data) {
        DialogPlus dialog = new DialogPlus(this);
        dialog.setTitle("宣言卡片");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (10 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);

        EditText input = new EditText(this);
        input.setHint("输入卡片名称搜索...");
        input.setTextColor(0xFFFFFFFF);
        input.setHintTextColor(0xFF888888);
        input.setSingleLine(true);
        root.addView(input);

        LinearLayout resultLayout = new LinearLayout(this);
        resultLayout.setOrientation(LinearLayout.VERTICAL);
        root.addView(resultLayout);

        final int[] selectedCode = {0};
        final TextView tvSelected = new TextView(this);
        tvSelected.setTextColor(0xFFFFFF00);
        tvSelected.setTextSize(13);
        tvSelected.setText("未选择卡片");
        root.addView(tvSelected);

        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                resultLayout.removeAllViews();
                String query = s.toString().trim();
                if (query.length() < 2) return;
                SparseArray<Card> allCards = DataManager.get().getCardManager().getAllCards();
                List<Card> results = new ArrayList<>();
                for (int i = 0; i < allCards.size(); i++) {
                    Card c = allCards.valueAt(i);
                    if (c.containsName(query)) {
                        results.add(c);
                        if (results.size() >= 10) break;
                    }
                }
                int limit = Math.min(results.size(), 10);
                for (int i = 0; i < limit; i++) {
                    Card c = results.get(i);
                    Button btn = new Button(YGONativeGameActivity.this);
                    btn.setText(c.Name + " [" + c.Code + "]");
                    btn.setTextColor(0xFFFFFFFF);
                    btn.setBackgroundColor(0xFF335577);
                    btn.setTextSize(12);
                    btn.setSingleLine(true);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                    lp.bottomMargin = 2;
                    btn.setLayoutParams(lp);
                    final int code = c.Code;
                    btn.setOnClickListener(v -> {
                        selectedCode[0] = code;
                        tvSelected.setText("已选择: " + c.Name);
                    });
                    resultLayout.addView(btn);
                }
            }
        });

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(root);
        dialog.setContentView(scrollView);
        dialog.setLeftButtonText("确认");
        dialog.setLeftButtonListener((d, w) -> {
            if (selectedCode[0] > 0) {
                sendResponseInt(selectedCode[0]);
            }
            d.dismiss();
        });
        dialog.setRightButtonText("取消");
        dialog.setRightButtonListener((d, w) -> {
            sendResponseInt(0);
            d.dismiss();
        });
        dialog.setCancelable(false);
        dialog.show();
    }

    private void showAnnounceNumberDialog(ByteBuffer data) {
        if (data == null || data.remaining() < 1) {
            sendResponseInt(0);
            return;
        }
        int count = data.get() & 0xFF;
        List<Integer> numbers = new ArrayList<>();
        for (int i = 0; i < count && data.remaining() >= 4; i++) {
            numbers.add(data.getInt());
        }
        if (numbers.isEmpty()) {
            sendResponseInt(0);
            return;
        }

        DialogPlus dialog = new DialogPlus(this);
        dialog.setTitle("选择数字");
        dialog.setContentView(R.layout.dialog_game_select);
        View contentView = dialog.getContentView();
        contentView.findViewById(getResId("tv_select_title", "id")).setVisibility(View.GONE);
        contentView.findViewById(getResId("tv_select_hint", "id")).setVisibility(View.GONE);
        contentView.findViewById(getResId("layout_select_buttons", "id")).setVisibility(View.GONE);
        LinearLayout layoutOptions = contentView.findViewById(getResId("layout_options", "id"));

        for (int i = 0; i < numbers.size(); i++) {
            Button btn = new Button(this);
            btn.setText(String.valueOf(numbers.get(i)));
            btn.setTextColor(0xFFFFFFFF);
            btn.setBackgroundColor(0xFF335577);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = 4;
            btn.setLayoutParams(lp);
            final int val = numbers.get(i);
            btn.setOnClickListener(v -> {
                sendResponseInt(val);
                dialog.dismiss();
            });
            layoutOptions.addView(btn);
        }
        dialog.setCancelable(false);
        dialog.show();
    }

    private void showCardInfoDialog(int cardCode) {
        GameField.ClientCard tempCard = new GameField.ClientCard();
        tempCard.code = cardCode;
        showCardInfoPanel(tempCard);
    }

    private void showCardInfoPanel(GameField.ClientCard card) {
        if (card == null || card.code <= 0) return;
        Card cardData = DataManager.get().getCardManager().getCard(card.code);
        String name = cardData != null ? cardData.Name : "Unknown Card";
        if (name == null) name = "Unknown Card";

        DialogPlus dialog = new DialogPlus(this);
        dialog.setTitle(name + " [" + card.code + "]");

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (10 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);

        if (card.isFaceUp() && card.isMonster()) {
            LinearLayout statsRow = new LinearLayout(this);
            statsRow.setOrientation(LinearLayout.HORIZONTAL);
            TextView tvAtk = new TextView(this);
            tvAtk.setTextColor(0xFFFF6666);
            tvAtk.setTextSize(14);
            tvAtk.setText("ATK: " + card.attack);
            statsRow.addView(tvAtk);

            if (!card.isLink()) {
                TextView tvSep = new TextView(this);
                tvSep.setTextColor(0xFFFFFFFF);
                tvSep.setTextSize(14);
                tvSep.setText(" / ");
                statsRow.addView(tvSep);

                TextView tvDef = new TextView(this);
                tvDef.setTextColor(0xFF6666FF);
                tvDef.setTextSize(14);
                tvDef.setText("DEF: " + card.defense);
                statsRow.addView(tvDef);
            }
            root.addView(statsRow);
        }

        if (card.isFaceUp() && card.level > 0) {
            TextView tvLevel = new TextView(this);
            tvLevel.setTextColor(0xFFFFCC00);
            tvLevel.setTextSize(12);
            tvLevel.setText("等级: " + "★".repeat(Math.min(card.level, 12)));
            root.addView(tvLevel);
        }
        if (card.isFaceUp() && card.rank > 0) {
            TextView tvRank = new TextView(this);
            tvRank.setTextColor(0xFFFFCC00);
            tvRank.setTextSize(12);
            tvRank.setText("阶级: " + "☆".repeat(Math.min(card.rank, 12)));
            root.addView(tvRank);
        }

        if (card.isFaceUp()) {
            String posStr;
            if ((card.position & 0x1) != 0) posStr = "表侧攻击";
            else if ((card.position & 0x2) != 0) posStr = "里侧攻击";
            else if ((card.position & 0x4) != 0) posStr = "表侧守备";
            else if ((card.position & 0x8) != 0) posStr = "里侧守备";
            else posStr = "未知";
            TextView tvPos = new TextView(this);
            tvPos.setTextColor(0xFFCCCCCC);
            tvPos.setTextSize(11);
            tvPos.setText("表示: " + posStr + " | 位置: " + getLocationName(card.location));
            root.addView(tvPos);
        }

        if (card.overlayCards != null && !card.overlayCards.isEmpty()) {
            TextView tvOverlay = new TextView(this);
            tvOverlay.setTextColor(0xFF8888FF);
            tvOverlay.setTextSize(11);
            tvOverlay.setText("超量素材: ×" + card.overlayCards.size());
            root.addView(tvOverlay);
        }
        if (card.equipCard != null) {
            TextView tvEquip = new TextView(this);
            tvEquip.setTextColor(0xFFFFCC88);
            tvEquip.setTextSize(11);
            tvEquip.setText("装备: " + getCardDisplayName(card.equipCard.code));
            root.addView(tvEquip);
        }
        if (card.counters != null && !card.counters.isEmpty()) {
            StringBuilder counterStr = new StringBuilder("指示物: ");
            for (int[] c : card.counters) {
                counterStr.append("[").append(c[0]).append("×").append(c[1]).append("] ");
            }
            TextView tvCounter = new TextView(this);
            tvCounter.setTextColor(0xFFCC88CC);
            tvCounter.setTextSize(11);
            tvCounter.setText(counterStr.toString());
            root.addView(tvCounter);
        }

        if (cardData != null && cardData.Desc != null) {
            TextView tvDesc = new TextView(this);
            tvDesc.setTextColor(0xFFDDDDDD);
            tvDesc.setTextSize(10);
            tvDesc.setText(cardData.Desc);
            tvDesc.setPadding(0, pad, 0, 0);
            root.addView(tvDesc);
        }

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(root);
        dialog.setContentView(scrollView);
        dialog.setLeftButtonText("关闭");
        dialog.setLeftButtonListener((d, w) -> d.dismiss());
        dialog.show();
    }

    private void showSortCardDialog(ByteBuffer data) {
        if (data == null || data.remaining() < 1) {
            sendResponseInt(0);
            return;
        }
        int player = data.get() & 0xFF;
        int count = data.get() & 0xFF;
        if (count <= 1) {
            byte[] resp = new byte[count];
            for (int i = 0; i < count; i++) resp[i] = (byte) i;
            engine.sendResponse(resp);
            return;
        }

        List<SortCardInfo> sortCards = new ArrayList<>();
        for (int i = 0; i < count && data.remaining() >= 8; i++) {
            int code = data.getInt();
            int ctrl = data.get() & 0xFF;
            int loc = data.get() & 0xFF;
            int seq = data.get() & 0xFF;
            int subSeq = data.get() & 0xFF;
            sortCards.add(new SortCardInfo(code, ctrl, loc, seq, i));
        }

        int[] sortList = new int[count];
        int[] currentOrder = {0};

        DialogPlus dialog = new DialogPlus(this);
        dialog.setTitle("卡片排序 (按顺序点击)");
        dialog.setContentView(R.layout.dialog_game_select);
        View contentView = dialog.getContentView();
        contentView.findViewById(getResId("tv_select_title", "id")).setVisibility(View.GONE);
        contentView.findViewById(getResId("tv_select_hint", "id")).setVisibility(View.GONE);
        contentView.findViewById(getResId("layout_select_buttons", "id")).setVisibility(View.GONE);
        LinearLayout layoutOptions = contentView.findViewById(getResId("layout_options", "id"));

        TextView tvOrder = new TextView(this);
        tvOrder.setTextColor(0xFFFFFF00);
        tvOrder.setTextSize(13);
        tvOrder.setText("点击顺序: 0/" + count);
        layoutOptions.addView(tvOrder);

        for (int i = 0; i < sortCards.size(); i++) {
            SortCardInfo info = sortCards.get(i);
            Button btn = new Button(this);
            String cardName = getCardDisplayName(info.code);
            btn.setText(cardName);
            btn.setTextColor(0xFFFFFFFF);
            btn.setBackgroundColor(0xFF335577);
            btn.setTextSize(12);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = 4;
            btn.setLayoutParams(lp);

            final int idx = i;
            btn.setOnClickListener(v -> {
                if (sortList[idx] != 0) return;
                currentOrder[0]++;
                sortList[idx] = currentOrder[0];
                btn.setText(currentOrder[0] + ". " + cardName);
                btn.setBackgroundColor(0xFF00AA44);
                tvOrder.setText("点击顺序: " + currentOrder[0] + "/" + count);

                if (currentOrder[0] == count) {
                    byte[] resp = new byte[count];
                    for (int j = 0; j < count; j++) {
                        resp[j] = (byte) (sortList[j] - 1);
                    }
                    engine.sendResponse(resp);
                    dialog.dismiss();
                }
            });
            layoutOptions.addView(btn);
        }

        ScrollView scrollContainer = new ScrollView(this);
        scrollContainer.addView(layoutOptions);
        dialog.setCancelable(false);
        dialog.show();
    }

    private static class SortCardInfo {
        int code, controler, location, sequence, index;

        SortCardInfo(int code, int ctrl, int loc, int seq, int idx) {
            this.code = code;
            this.controler = ctrl;
            this.location = loc;
            this.sequence = seq;
            this.index = idx;
        }
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
        buf.order(ByteOrder.LITTLE_ENDIAN);
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
        DraggablePopupHelper.resetAllPositions(this);
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
