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
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.ScrollView;
import android.widget.TextView;

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
import cn.garymb.ygomobile.network.YGOProtocol;
import cn.garymb.ygomobile.render.CardDetailPanel;
import cn.garymb.ygomobile.render.GameFieldView;
import cn.garymb.ygomobile.render.GameFieldViewController;
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
        GameFieldView.OnCardClickListener,
        LanModeDialog.OnLanModeListener {

    private static final String TAG = "YGONativeGame";
    private static final int PRO_VERSION = 0x1362;

    private GameEngine engine;
    private SoundManager soundManager;
    private ImageLoader imageLoader;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private GameFieldViewController fieldViewController;
    private TextView tvPlayerLp, tvPlayerName, tvOpponentLp, tvOpponentName;
    private TextView tvTurnCounter;
    private TextView tvPlayerHandCount, tvOpponentHandCount;
    private ImageView ivPlayerAvatar, ivOpponentAvatar;
    private TextView tvHintMessage;

    private LinearLayout layoutTopInfo, layoutLeftButtons;
    private LinearLayout layoutCardDetail;
    private LinearLayout layoutBottomActions, layoutDeckIndicators;
    private LinearLayout layoutChatMessages;
    private TextView tvChatMessage1, tvChatMessage2;
    private ImageView ivCardImage;
    private TextView tvCardName, tvCardAttr, tvCardLevel, tvCardDesc;
    private CardDetailPanel cardDetailPanel;
    private ImageButton btnSettings, btnChat, btnSound, btnSpeed, btnEmote, btnNote;
    private Button btnSurrender, btnIgnoreTiming, btnShowTiming, btnAvailableTiming;
    private Button btnCancelOrFinish;
    private Button btnPhaseCurrent, btnPhaseNext, btnEp;
    private FrameLayout layoutPhaseButtons;
    private int currentSelectType = -1;
    private DialogPlus currentDialog;

    private TextView tvPlayerDeckCount, tvPlayerGraveCount;
    private TextView tvOpponentDeckCount, tvOpponentGraveCount;

    private FrameLayout dialogContainer;
    private RelativeLayout layoutMainMenu;
    private TextView tvVersion;
    private LanModeDialog lanModeDialog;

    private String chatHistory = "";
    private TextView tvPlayerTime, tvOpponentTime;
    private TextView tvChatLog;
    private LinearLayout layoutActionButtons;
    private Button btnChain, btnCancel;
    private LinearLayout layoutChat;
    private EditText etChatInput;
    private LinearLayout layoutLobby;
    private TextView tvLobbyStatus;
    private Button btnLobbyReady, btnLobbyLeave;

    private LinearLayout layoutOpponentInfo;
    private LinearLayout layoutPlayerInfo;
    private boolean isMyTurn = false;
    private volatile boolean isGameStarted = false;
    private DraggablePopupHelper mainMenuDragHelper;

    private LinearLayout layoutReplayControl;
    private Button btnReplayPlay, btnReplayPause, btnReplayNext, btnReplayLast, btnReplayShuffle, btnReplayQuit;
    private ReplayEngine currentReplayEngine;

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

    @Override
    public void onPlayerEnter(String name, int pos) {
        runOnUiThread(() -> {
            if (lanModeDialog != null && lanModeDialog.isPlayerWaitingVisible()) {
                lanModeDialog.removeObserver(name);
                lanModeDialog.setPlayerName(pos, name);
                lanModeDialog.refreshPlayerDisplay();
            }
        });
    }

    @Override
    public void onPlayerChange(int status) {
        runOnUiThread(() -> {
            if (lanModeDialog != null && lanModeDialog.isPlayerWaitingVisible()) {
                int pos = (status >> 4) & 0x0F;
                int state = status & 0x0F;

                if (state < 8) {
                    String oldName = "";
                    switch (pos) {
                        case 0:
                            oldName = lanModeDialog.getPlayerName(0);
                            break;
                        case 1:
                            oldName = lanModeDialog.getPlayerName(1);
                            break;
                        case 2:
                            oldName = lanModeDialog.getPlayerName(2);
                            break;
                        case 3:
                            oldName = lanModeDialog.getPlayerName(3);
                            break;
                    }

                    lanModeDialog.movePlayer(pos, state);

                    if (!oldName.isEmpty() && state >= 4) {
                        lanModeDialog.addObserver(oldName);
                    }
                    if (pos >= 4 && !oldName.isEmpty() && state < 4) {
                        lanModeDialog.removeObserver(oldName);
                    }
                } else if (state == 0x8) {
                    String observerName = "";
                    switch (pos) {
                        case 0:
                            observerName = lanModeDialog.getPlayerName(0);
                            break;
                        case 1:
                            observerName = lanModeDialog.getPlayerName(1);
                            break;
                        case 2:
                            observerName = lanModeDialog.getPlayerName(2);
                            break;
                        case 3:
                            observerName = lanModeDialog.getPlayerName(3);
                            break;
                    }
                    lanModeDialog.clearPlayerPos(pos);
                    if (!observerName.isEmpty()) {
                        lanModeDialog.addObserver(observerName);
                    }
                } else if (state == 0x9) {
                    lanModeDialog.setPlayerReady(pos, true);
                } else if (state == 0xa) {
                    lanModeDialog.setPlayerReady(pos, false);
                } else if (state == 0xb) {
                    String leavingName = "";
                    switch (pos) {
                        case 0:
                            leavingName = lanModeDialog.getPlayerName(0);
                            break;
                        case 1:
                            leavingName = lanModeDialog.getPlayerName(1);
                            break;
                        case 2:
                            leavingName = lanModeDialog.getPlayerName(2);
                            break;
                        case 3:
                            leavingName = lanModeDialog.getPlayerName(3);
                            break;
                    }
                    lanModeDialog.clearPlayerPos(pos);
                    if (!leavingName.isEmpty()) {
                        lanModeDialog.removeObserver(leavingName);
                    }
                }
                lanModeDialog.refreshPlayerDisplay();
            }
        });
    }

    @Override
    public void onWatchChange(int watchCount) {
        runOnUiThread(() -> {
            if (lanModeDialog != null && lanModeDialog.isPlayerWaitingVisible()) {
                lanModeDialog.updateWatchCount(watchCount);
                lanModeDialog.refreshPlayerDisplay();
            }
        });
    }

    @Override
    public void onJoinGame(int lflist, int rule, int mode, int duelRule,
                           int noCheckDeck, int noShuffleDeck,
                           int startLp, int startHand, int drawCount, int timeLimit) {
        runOnUiThread(() -> {
            if (lanModeDialog != null && lanModeDialog.isPlayerWaitingVisible()) {
                lanModeDialog.updateRoomInfo(mode, startLp, startHand, drawCount, timeLimit);
            }
        });
    }

    @Override
    public void onTypeChange(int type) {
        runOnUiThread(() -> {
            if (lanModeDialog != null && lanModeDialog.isPlayerWaitingVisible()) {
                int selfType = type & 0x0F;
                boolean isHost = ((type >> 4) & 0x0F) != 0;
                boolean isTag = engine.getGameMode() == 2;
                lanModeDialog.updateTypeChange(selfType, isTag, isHost);
                lanModeDialog.refreshPlayerDisplay();
            }
        });
    }

    @Override
    public void onDeckError(int errorType, int cardCode) {
        String errorDesc;
        switch (errorType) {
            case YGOProtocol.DECKERROR_LFLIST:
                errorDesc = "禁限卡表违规";
                break;
            case YGOProtocol.DECKERROR_OCGONLY:
                errorDesc = "仅限OCG卡片";
                break;
            case YGOProtocol.DECKERROR_TCGONLY:
                errorDesc = "仅限TCG卡片";
                break;
            case YGOProtocol.DECKERROR_UNKNOWNCARD:
                errorDesc = "未知卡片";
                break;
            case YGOProtocol.DECKERROR_CARDCOUNT:
                errorDesc = "卡片数量超限";
                break;
            case YGOProtocol.DECKERROR_MAINCOUNT:
                errorDesc = "主卡组数量不符(" + cardCode + "张)";
                break;
            case YGOProtocol.DECKERROR_EXTRACOUNT:
                errorDesc = "额外卡组数量超限(" + cardCode + "张)";
                break;
            case YGOProtocol.DECKERROR_SIDECOUNT:
                errorDesc = "副卡组数量超限(" + cardCode + "张)";
                break;
            case YGOProtocol.DECKERROR_NOTAVAIL:
                errorDesc = "卡片不可用";
                break;
            default:
                errorDesc = "未知卡组错误(type=" + errorType + ")";
                break;
        }

        String cardName = "";
        if (cardCode > 0 && errorType != YGOProtocol.DECKERROR_MAINCOUNT
                && errorType != YGOProtocol.DECKERROR_EXTRACOUNT
                && errorType != YGOProtocol.DECKERROR_SIDECOUNT) {
            cardName = getCardDisplayName(cardCode);
        }

        String title = "卡组验证失败";
        String message = errorDesc;
        if (!cardName.isEmpty()) {
            message += "\n卡片: " + cardName + " (" + cardCode + ")";
        }

        DialogPlus dialog = new DialogPlus(this);
        dialog.setTitle(title);
        dialog.setMessage(message);
        dialog.setRightButtonText("确定");
        dialog.setRightButtonListener((d, w) -> d.dismiss());
        dialog.show();
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
        btnSurrender = findViewById(R.id.btn_surrender);
        btnIgnoreTiming = findViewById(R.id.btn_ignore_timing);
        btnShowTiming = findViewById(R.id.btn_show_timing);
        btnAvailableTiming = findViewById(R.id.btn_available_timing);
        btnCancelOrFinish = findViewById(R.id.btn_cancel_or_finish);

        btnPhaseCurrent = findViewById(R.id.btn_phase_current);
        btnPhaseNext = findViewById(R.id.btn_phase_next);
        btnEp = findViewById(R.id.btn_ep);
        layoutPhaseButtons = findViewById(R.id.layout_phase_buttons);


        ivCardImage = findViewById(R.id.iv_card_image);
        tvCardName = findViewById(R.id.tv_card_name);
        tvCardAttr = findViewById(R.id.tv_card_attr);
        tvCardLevel = findViewById(R.id.tv_card_level);
        tvCardDesc = findViewById(R.id.tv_card_desc);

        fieldViewController = new GameFieldViewController(this);
        tvPlayerLp = findViewById(R.id.tv_player_lp);
        tvPlayerName = findViewById(R.id.tv_player_name);
        tvOpponentLp = findViewById(R.id.tv_opponent_lp);
        tvOpponentName = findViewById(R.id.tv_opponent_name);
        tvTurnCounter = findViewById(R.id.tv_turn_counter);
        tvPlayerHandCount = findViewById(R.id.tv_player_hand_count);
        tvOpponentHandCount = findViewById(R.id.tv_opponent_hand_count);
        tvHintMessage = findViewById(R.id.tv_hint_message);
        ivPlayerAvatar = findViewById(R.id.iv_player_avatar);
        ivOpponentAvatar = findViewById(R.id.iv_opponent_avatar);

        layoutTopInfo = findViewById(R.id.layout_top_info);
        layoutLeftButtons = findViewById(R.id.layout_left_buttons);
        layoutCardDetail = findViewById(R.id.layout_card_detail);
        layoutBottomActions = findViewById(R.id.layout_bottom_actions);
        layoutDeckIndicators = findViewById(R.id.layout_deck_indicators);
        layoutChatMessages = findViewById(R.id.layout_chat_messages);

        tvChatMessage1 = findViewById(R.id.tv_chat_message_1);
        tvChatMessage2 = findViewById(R.id.tv_chat_message_2);

        btnSettings = findViewById(R.id.btn_settings);
        btnChat = findViewById(R.id.btn_chat);
        btnSound = findViewById(R.id.btn_sound);
        btnSpeed = findViewById(R.id.btn_speed);
        btnEmote = findViewById(R.id.btn_emote);
        btnNote = findViewById(R.id.btn_note);

        btnSurrender = findViewById(R.id.btn_surrender);
        btnIgnoreTiming = findViewById(R.id.btn_ignore_timing);
        btnShowTiming = findViewById(R.id.btn_show_timing);
        btnAvailableTiming = findViewById(R.id.btn_available_timing);

        ivCardImage = findViewById(R.id.iv_card_image);
        tvCardName = findViewById(R.id.tv_card_name);
        tvCardAttr = findViewById(R.id.tv_card_attr);
        tvCardLevel = findViewById(R.id.tv_card_level);
        tvCardDesc = findViewById(R.id.tv_card_desc);

        tvPlayerDeckCount = findViewById(R.id.tv_player_deck_count);
        tvPlayerGraveCount = findViewById(R.id.tv_player_grave_count);
        tvOpponentDeckCount = findViewById(R.id.tv_opponent_deck_count);
        tvOpponentGraveCount = findViewById(R.id.tv_opponent_grave_count);

        dialogContainer = findViewById(R.id.dialog_container);

        layoutReplayControl = findViewById(R.id.layout_replay_control);
        btnReplayPlay = findViewById(R.id.btn_replay_play);
        btnReplayPause = findViewById(R.id.btn_replay_pause);
        btnReplayNext = findViewById(R.id.btn_replay_next);
        btnReplayLast = findViewById(R.id.btn_replay_last);
        btnReplayShuffle = findViewById(R.id.btn_replay_shuffle);
        btnReplayQuit = findViewById(R.id.btn_replay_quit);

        setupButtonListeners();
        setupAvatarImages();
    }

    private void setupAvatarImages() {
        Bitmap myAvatar = TextureLoader.get().getAvatar(true);
        if (myAvatar != null) ivPlayerAvatar.setImageBitmap(myAvatar);
        Bitmap opAvatar = TextureLoader.get().getAvatar(false);
        if (opAvatar != null) ivOpponentAvatar.setImageBitmap(opAvatar);
    }

    private void setupButtonListeners() {
        btnSurrender.setOnClickListener(v -> {
            if (engine != null) engine.sendSurrender();
        });

        btnIgnoreTiming.setOnClickListener(v -> {
            sendActionResponse(-1);
        });

        btnShowTiming.setOnClickListener(v -> {
        });

        btnAvailableTiming.setOnClickListener(v -> {
        });

        btnSettings.setOnClickListener(v -> {
            showSettingsDialog();
        });

        btnChat.setOnClickListener(v -> {
            toggleChatInput();
        });

        btnSound.setOnClickListener(v -> {
            if (soundManager != null) {
                SharedPreferences prefs = getSharedPreferences(getPackageName() + ".settings", Context.MODE_PRIVATE);
                boolean currentSound = prefs.getBoolean("chkEnableSound", true);
                boolean currentMusic = prefs.getBoolean("chkEnableMusic", true);
                boolean newMuted = !currentSound && !currentMusic;
                soundManager.enableSounds(newMuted);
                soundManager.enableMusic(newMuted);
                prefs.edit()
                        .putBoolean("chkEnableSound", newMuted)
                        .putBoolean("chkEnableMusic", newMuted)
                        .apply();
            }
        });

        btnSpeed.setOnClickListener(v -> {
        });

        btnEmote.setOnClickListener(v -> {
        });

        btnNote.setOnClickListener(v -> {
            showMainMenu();
        });

        if (btnCancelOrFinish != null) {
            btnCancelOrFinish.setOnClickListener(v -> cancelOrFinish());
        }

        if (btnPhaseNext != null) {
            btnPhaseNext.setOnClickListener(v -> {
                if (engine == null || engine.getClient() == null) return;
                String label = btnPhaseNext.getText().toString();
                if ("BP".equals(label) && currentSelectType == 11) {
                    sendResponseInt(6);
                } else if ("M2".equals(label) && currentSelectType == 10) {
                    sendResponseInt(2);
                }
            });
        }

        if (btnEp != null) {
            btnEp.setOnClickListener(v -> {
                if (engine == null || engine.getClient() == null) return;
                if (currentSelectType == 10) {
                    sendResponseInt(3);
                } else if (currentSelectType == 11) {
                    sendResponseInt(7);
                }
            });
        }

        if (btnReplayPlay != null) {
            btnReplayPlay.setOnClickListener(v -> {
                if (currentReplayEngine != null) currentReplayEngine.resume();
            });
        }
        if (btnReplayPause != null) {
            btnReplayPause.setOnClickListener(v -> {
                if (currentReplayEngine != null) currentReplayEngine.pause();
            });
        }
        if (btnReplayNext != null) {
            btnReplayNext.setOnClickListener(v -> {
                if (currentReplayEngine != null) currentReplayEngine.skipAhead();
            });
        }
        if (btnReplayLast != null) {
            btnReplayLast.setOnClickListener(v -> {
                if (currentReplayEngine != null) currentReplayEngine.undo();
            });
        }
        if (btnReplayShuffle != null) {
            btnReplayShuffle.setOnClickListener(v -> {
                if (currentReplayEngine != null) currentReplayEngine.swapField();
            });
        }
        if (btnReplayQuit != null) {
            btnReplayQuit.setOnClickListener(v -> {
                if (currentReplayEngine != null) currentReplayEngine.stop();
                hideReplayControls();
                restoreMainMenu();
            });
        }
    }

    private void initEngine() {
        soundManager = new SoundManager(this);
        soundManager.init(0.8, 0.6, true, true);

        imageLoader = new ImageLoader(true);

        cardDetailPanel = new CardDetailPanel(findViewById(android.R.id.content), imageLoader);

        engine = new GameEngine(soundManager);
        engine.setListener(this);
        engine.setPlayerName(Constants.PlayerName);

        fieldViewController.init(engine.getField(), imageLoader, this);

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
                showPlayerWaitingForDirectJoin(options);
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
            showPlayerWaitingForDirectJoin(null);
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
        String password = options.mRoomName != null ? options.mRoomName : "";
        engine.setPlayerName(user);
        engine.connectToServer(host, port, false, room, password,
                0, 0, 5, 8000, 5, 1, 0, false, false);
    }

    private void showPlayerWaitingForDirectJoin(YGOGameOptions options) {
        if (layoutMainMenu == null) {
            layoutMainMenu = findViewById(R.id.layout_main_menu);
            tvVersion = findViewById(R.id.tv_version);
            bindMainMenuButtons();
        }

        hideGameUI();

        String name = Constants.PlayerName;
        if (options != null && options.mUserName != null && !options.mUserName.isEmpty()) {
            name = options.mUserName;
        }
        final String playerName = name;

        layoutMainMenu.post(() -> {
            if (isFinishing() || isDestroyed()) return;

            lanModeDialog = new LanModeDialog(this, YGONativeGameActivity.this);
            lanModeDialog.show(layoutMainMenu);
            lanModeDialog.setOnDismissListener(() -> restoreMainMenu());

            if (options != null) {
                lanModeDialog.preFillConnectionFields(
                        options.mUserName,
                        options.mServerAddr,
                        String.valueOf(options.mPort),
                        options.mRoomPasswd
                );
            }

            lanModeDialog.showPlayerWaiting();
            lanModeDialog.setPlayerName(0, playerName);

            soundManager.playBGM(SoundManager.BGM.DUEL);
        });
    }

    // === Main Menu ===

    private void showMainMenu() {
        layoutMainMenu = findViewById(R.id.layout_main_menu);
        tvVersion = findViewById(R.id.tv_version);
        layoutMainMenu.setVisibility(View.VISIBLE);

        mainMenuDragHelper = new DraggablePopupHelper(this, "main_menu");
        mainMenuDragHelper.setupDraggableView(layoutMainMenu);
        mainMenuDragHelper.applySavedPositionToView(layoutMainMenu);

        hideGameUI();
        bindMainMenuButtons();

        soundManager.playBGM(SoundManager.BGM.MENU);
        applySettingsToEngine();
    }

    private void hideMainMenu() {
        if (layoutMainMenu != null) {
            layoutMainMenu.setVisibility(View.GONE);
        }
        showGameUI();
        soundManager.playBGM(SoundManager.BGM.DUEL);
    }

    private void restoreMainMenu() {
        if (layoutMainMenu != null) {
            layoutMainMenu.setVisibility(View.VISIBLE);
        }
    }

    private void hideGameUI() {
        if (fieldViewController != null) fieldViewController.hide();
        if (layoutTopInfo != null) layoutTopInfo.setVisibility(View.GONE);
        if (layoutLeftButtons != null) layoutLeftButtons.setVisibility(View.GONE);
        if (cardDetailPanel != null) cardDetailPanel.hide();
        if (layoutBottomActions != null) layoutBottomActions.setVisibility(View.GONE);
        if (layoutPhaseButtons != null) layoutPhaseButtons.setVisibility(View.GONE);
        if (layoutDeckIndicators != null) layoutDeckIndicators.setVisibility(View.GONE);
        if (layoutChatMessages != null) layoutChatMessages.setVisibility(View.GONE);
        if (dialogContainer != null) dialogContainer.setVisibility(View.GONE);
        hideCancelOrFinishButton();
    }

    private void showGameUI() {
        if (fieldViewController != null) fieldViewController.show();
        if (layoutTopInfo != null) layoutTopInfo.setVisibility(View.VISIBLE);
        if (layoutLeftButtons != null) layoutLeftButtons.setVisibility(View.VISIBLE);
        if (layoutBottomActions != null) layoutBottomActions.setVisibility(View.VISIBLE);
        if (layoutDeckIndicators != null) layoutDeckIndicators.setVisibility(View.VISIBLE);
        if (cardDetailPanel != null) cardDetailPanel.showDefault();
        hideCancelOrFinishButton();
    }

    private void showLanModeDialog() {
        lanModeDialog = new LanModeDialog(this, this);
        lanModeDialog.show(layoutMainMenu);
        lanModeDialog.setOnDismissListener(() -> restoreMainMenu());
    }

    @Override
    public void onCreateHostConfirmed(int lflist, int ruleIdx, int modeIdx, int duelRule,
                                      int startLP, int startHand, int drawCount, int timeLimit,
                                      boolean noCheckDeck, boolean noShuffleDeck,
                                      String hostName, String password) {
        hideMainMenu();

        String roomName = (hostName != null && !hostName.isEmpty()) ? hostName : "Local Game";

        engine.startLocalServerWithSettings(lflist, ruleIdx, modeIdx, duelRule,
                noCheckDeck, noShuffleDeck,
                startLP, startHand, drawCount, timeLimit,
                roomName, password != null ? password : "");
    }

    @Override
    public void onJoinGameRequested(String ip, String port, String password, String nickname) {
        hideMainMenu();
        int portNum;
        try {
            portNum = Integer.parseInt(port);
        } catch (NumberFormatException e) {
            portNum = 7911;
        }
        String userName = (nickname != null && !nickname.isEmpty()) ? nickname : Constants.PlayerName;
        engine.setPlayerName(userName);
        engine.connectToServer(ip, portNum, false, "", password,
                0, 0, 5, 8000, 5, 1, 0, false, false);
    }

    @Override
    public void onExitLan() {
    }

    @Override
    public void onPlayerWaitingReady() {
        if (engine != null) engine.sendReady();
    }

    @Override
    public void onPlayerWaitingNotReady() {
        if (engine != null) engine.sendNotReady();
    }

    @Override
    public void onPlayerWaitingToDuelist() {
        if (engine != null) engine.sendToDuelist();
    }

    @Override
    public void onPlayerWaitingToObserver() {
        if (engine != null) engine.sendToObserver();
    }

    @Override
    public void onPlayerWaitingExit() {
        if (engine != null) engine.disconnect();
        restoreMainMenu();
    }

    @Override
    public void onPlayerWaitingDeckUpdate(List<Integer> main, List<Integer> extra, List<Integer> side) {
        if (engine != null) {
            engine.sendDeckUpdate(main, extra, side);
        }
    }

    @Override
    public void onStartGameRequested() {
        if (engine != null) {
            engine.sendStart();
        }
    }

    @Override
    public void onKickPlayerRequested(int pos) {
        if (engine != null) {
            engine.sendKick(pos);
        }
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
        currentReplayEngine = replayEngine;

        replayEngine.setListener(new ReplayEngine.ReplayListener() {
            @Override
            public void onReplayStateChanged(ReplayEngine.ReplayState state) {
                runOnUiThread(() -> {
                    switch (state) {
                        case PLAYING:
                            if (btnPhaseCurrent != null) btnPhaseCurrent.setText("▶");
                            if (layoutBottomActions != null) layoutBottomActions.setVisibility(View.GONE);
                            if (layoutReplayControl != null) layoutReplayControl.setVisibility(View.VISIBLE);
                            break;
                        case PAUSED:
                            if (btnPhaseCurrent != null) btnPhaseCurrent.setText("⏸");
                            break;
                        case FINISHED:
                            if (btnPhaseCurrent != null) btnPhaseCurrent.setText("⏹");
                            hideReplayControls();
                            break;
                    }
                });
            }

            @Override
            public void onReplayFieldChanged() {
                fieldViewController.invalidate();
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
                    if (btnPhaseCurrent != null && dp != null) {
                        switch (dp) {
                            case Draw: btnPhaseCurrent.setText("DP"); break;
                            case Standby: btnPhaseCurrent.setText("SP"); break;
                            case Main1: btnPhaseCurrent.setText("M1"); break;
                            case BattleStart:
                            case BattleStep:
                            case Battle:
                            case Damage:
                            case DamageCal: btnPhaseCurrent.setText("BP"); break;
                            case Main2: btnPhaseCurrent.setText("M2"); break;
                            case End: btnPhaseCurrent.setText("EP"); break;
                            default: btnPhaseCurrent.setText(dp.name()); break;
                        }
                    }
                    tvTurnCounter.setText("Turn " + engine.getField().turnCount);
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
                    hideReplayControls();
                    showResultDialog(result);
                });
            }
        });

        replayEngine.loadAndPlay(replayPath, startTurn);
    }

    private void hideReplayControls() {
        if (layoutReplayControl != null) layoutReplayControl.setVisibility(View.GONE);
        if (layoutBottomActions != null) layoutBottomActions.setVisibility(View.VISIBLE);
        currentReplayEngine = null;
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
                if (lanModeDialog != null && lanModeDialog.isPlayerWaitingVisible()) {
                    // Already showing player waiting via LanModeDialog, do nothing
                } else {
                    hideMainMenu();
                }
                break;
            case DECK_SELECT:
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
                if (lanModeDialog != null) lanModeDialog.dismiss();
                if (layoutLobby != null) layoutLobby.setVisibility(View.GONE);
                if (layoutActionButtons != null) layoutActionButtons.setVisibility(View.GONE);
                if (layoutChat != null) layoutChat.setVisibility(View.VISIBLE);
                if (layoutBottomActions != null) layoutBottomActions.setVisibility(View.VISIBLE);
                isGameStarted = true;
                break;
            case SIDING:
                showSideSelectDialog();
                break;
            case DUEL_END:
                closeGameButtons();
                showDuelEndDialog();
                break;
            case DISCONNECTED:
                if (!isFinishing()) {
                    runOnUiThread(() -> {
                        if (lanModeDialog != null) {
                            lanModeDialog.showLanMain();
                        }
                    });
                }
                break;
        }
    }

    @Override
    public void onFieldChanged() {
        fieldViewController.invalidate();
    }

    @Override
    public void onPlayerInfoUpdated(int player) {
        runOnUiThread(() -> {
            GameEngine.PlayerInfo info = engine.playerInfos[player];
            GameField.PlayerField pf = engine.getField().players[player];
            if (player == 0) {
                tvPlayerLp.setText(String.valueOf(pf.lp));
                tvPlayerName.setText(info.name.isEmpty() ? Constants.PlayerName : info.name);
                int handCount = engine.getField().getCardCount(player, ocgcore.enums.CardLocation.Hand.value());
                tvPlayerHandCount.setText("手卡:" + handCount);

                int deckCount = engine.getField().getCardCount(player, ocgcore.enums.CardLocation.Deck.value());
                if (tvPlayerDeckCount != null) tvPlayerDeckCount.setText(String.valueOf(deckCount));

                int graveCount = engine.getField().getCardCount(player, ocgcore.enums.CardLocation.Grave.value());
                if (tvPlayerGraveCount != null)
                    tvPlayerGraveCount.setText(String.valueOf(graveCount));
            } else {
                tvOpponentLp.setText(String.valueOf(pf.lp));
                tvOpponentName.setText(info.name.isEmpty() ? "Opponent" : info.name);
                int handCount = engine.getField().getCardCount(player, ocgcore.enums.CardLocation.Hand.value());
                tvOpponentHandCount.setText("手卡:" + handCount);

                int deckCount = engine.getField().getCardCount(player, ocgcore.enums.CardLocation.Deck.value());
                if (tvOpponentDeckCount != null)
                    tvOpponentDeckCount.setText(String.valueOf(deckCount));

                int graveCount = engine.getField().getCardCount(player, ocgcore.enums.CardLocation.Grave.value());
                if (tvOpponentGraveCount != null)
                    tvOpponentGraveCount.setText(String.valueOf(graveCount));
            }
        });
    }

    @Override
    public void onPhaseChanged(int phase) {
        runOnUiThread(() -> {
            tvTurnCounter.setText(String.valueOf(engine.getField().turnCount));
            isMyTurn = (engine.getField().currentPlayer == engine.getClient().selfType);
            updateActionButtonsForPhase(phase);
        });
    }

    private void updateActionButtonsForPhase(int phase) {
        DuelPhase dp = DuelPhase.valueOf(phase);
        if (dp == null) return;

        if (layoutPhaseButtons != null) {
            layoutPhaseButtons.setVisibility(isMyTurn ? View.VISIBLE : View.GONE);
        }

        if (btnPhaseCurrent == null) return;

        if (btnPhaseNext != null) btnPhaseNext.setVisibility(View.GONE);
        if (btnEp != null) btnEp.setVisibility(View.GONE);

        switch (dp) {
            case Draw:
                btnPhaseCurrent.setText("DP");
                break;
            case Standby:
                btnPhaseCurrent.setText("SP");
                break;
            case Main1:
                btnPhaseCurrent.setText("M1");
                if (isMyTurn) {
                    if (btnPhaseNext != null) {
                        btnPhaseNext.setText("BP");
                        btnPhaseNext.setVisibility(View.VISIBLE);
                    }
                    if (btnEp != null) btnEp.setVisibility(View.VISIBLE);
                }
                break;
            case BattleStart:
            case BattleStep:
            case Damage:
            case DamageCal:
            case Battle:
                btnPhaseCurrent.setText("BP");
                if (isMyTurn) {
                    if (btnPhaseNext != null) {
                        btnPhaseNext.setText("M2");
                        btnPhaseNext.setVisibility(View.VISIBLE);
                    }
                    if (btnEp != null) btnEp.setVisibility(View.VISIBLE);
                }
                break;
            case Main2:
                btnPhaseCurrent.setText("M2");
                if (isMyTurn) {
                    if (btnEp != null) btnEp.setVisibility(View.VISIBLE);
                }
                break;
            case End:
                btnPhaseCurrent.setText("EP");
                break;
            default:
                btnPhaseCurrent.setText(dp.name());
                break;
        }
    }

    @Override
    public void onChatReceived(String player, String message) {
        runOnUiThread(() -> appendChat(player, message));
    }

    private void appendChat(String player, String message) {
        String chatLine = "[" + player + "] " + message;

        if (tvChatMessage1 != null && tvChatMessage1.getVisibility() == View.GONE) {
            tvChatMessage1.setText(chatLine);
            tvChatMessage1.setVisibility(View.VISIBLE);
        } else if (tvChatMessage2 != null && tvChatMessage2.getVisibility() == View.GONE) {
            tvChatMessage2.setText(chatLine);
            tvChatMessage2.setVisibility(View.VISIBLE);
        } else {
            if (tvChatMessage1 != null) {
                tvChatMessage1.setText(tvChatMessage2 != null ? tvChatMessage2.getText() : "");
            }
            if (tvChatMessage2 != null) {
                tvChatMessage2.setText(chatLine);
                tvChatMessage2.setVisibility(View.VISIBLE);
            }
        }

        if (layoutChatMessages != null) {
            layoutChatMessages.setVisibility(View.VISIBLE);
        }
    }

    private void toggleChatInput() {
    }

    @Override
    public void onSelectRequired(int selectType, ByteBuffer data) {
        runOnUiThread(() -> {
            currentSelectType = selectType;
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
            Log.d(TAG, "Time limit for player " + player + ": " + timeStr);
        });
    }

    @Override
    public void onChainAnimation(int code, int controler, int location, int sequence) {
        runOnUiThread(() -> {
            fieldViewController.selectCardWithAutoClear(controler, location, sequence, 1500);
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
        if (card != null && card.code > 0) {
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
        currentDialog = dialog;
        dialog.setTitle("确认");
        dialog.setMessage(desc);
        dialog.setLeftButtonText("是");
        dialog.setLeftButtonListener((d, w) -> {
            sendResponseInt(1);
            hideCancelOrFinishButton();
            d.dismiss();
        });
        dialog.setRightButtonText("否");
        dialog.setRightButtonListener((d, w) -> {
            sendResponseInt(0);
            hideCancelOrFinishButton();
            d.dismiss();
        });
        showCancelOrFinishButton("否");
        dialog.setOnDismissListener((d) -> {
            hideCancelOrFinishButton();
            currentDialog = null;
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
        fieldViewController.highlightField(mask);
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
        fieldViewController.clearHighlight();

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
        currentDialog = dialog;
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
            hideCancelOrFinishButton();
            d.dismiss();
        });
        if (!hasForced) {
            showCancelOrFinishButton("不连锁");
        }
        dialog.setOnDismissListener(d -> {
            hideCancelOrFinishButton();
            currentDialog = null;
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

        boolean cancelable = (min == 0);

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
        currentDialog = dialog;
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
                    hideCancelOrFinishButton();
                    dialog.dismiss();
                }
                updateCancelOrFinishButton(selCount >= min, cancelable, selCount > 0);
            });
            layoutOptions.addView(btn);
        }

        if (cancelable) {
            showCancelOrFinishButton("取消");
            dialog.setRightButtonText("取消");
            dialog.setRightButtonListener((d, w) -> {
                ByteBuffer buf = ByteBuffer.allocate(1);
                buf.put((byte) 0);
                engine.sendResponse(buf.array());
                hideCancelOrFinishButton();
                d.dismiss();
            });
        } else if (min <= max) {
            showCancelOrFinishButton("完成选择");
        }
        dialog.setOnDismissListener(d -> {
            hideCancelOrFinishButton();
            currentDialog = null;
        });
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
        tempCard.position = 0x1;
        showCardInfoPanel(tempCard);
    }

    private void showCardInfoPanel(GameField.ClientCard card) {
        if (card == null || card.code <= 0) return;
        if (cardDetailPanel != null) {
            cardDetailPanel.showCard(card);
        }
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

    private void closeGameButtons() {
        hideCancelOrFinishButton();
        if (btnPhaseCurrent != null) btnPhaseCurrent.setText("");
        if (btnPhaseNext != null) btnPhaseNext.setVisibility(View.GONE);
        if (btnEp != null) btnEp.setVisibility(View.GONE);
        if (layoutPhaseButtons != null) layoutPhaseButtons.setVisibility(View.GONE);
        if (layoutBottomActions != null) layoutBottomActions.setVisibility(View.GONE);
        if (layoutActionButtons != null) layoutActionButtons.setVisibility(View.GONE);
    }

    // === CancelOrFinish (mirrors C++ ClientField::CancelOrFinish) ===

    private void showCancelOrFinishButton(String text) {
        if (btnCancelOrFinish != null) {
            btnCancelOrFinish.setText(text);
            btnCancelOrFinish.setVisibility(View.VISIBLE);
        }
    }

    private void hideCancelOrFinishButton() {
        if (btnCancelOrFinish != null) {
            btnCancelOrFinish.setVisibility(View.GONE);
        }
    }

    private void updateCancelOrFinishButton(boolean ready, boolean cancelable, boolean hasSelection) {
        if (btnCancelOrFinish == null) return;
        if (ready) {
            btnCancelOrFinish.setText("完成选择");
            btnCancelOrFinish.setVisibility(View.VISIBLE);
        } else if (cancelable && !hasSelection) {
            btnCancelOrFinish.setText("取消");
            btnCancelOrFinish.setVisibility(View.VISIBLE);
        } else {
            btnCancelOrFinish.setVisibility(View.GONE);
        }
    }

    private void cancelOrFinish() {
        switch (currentSelectType) {
            case 13:
            case 12: {
                sendResponseInt(0);
                hideCancelOrFinishButton();
                if (currentDialog != null) currentDialog.dismiss();
                break;
            }
            case 15:
            case 20: {
                GameField field = engine.getField();
                if (field.selectedCards.isEmpty()) {
                    if (field.selectCancelable) {
                        sendResponseInt(-1);
                        hideCancelOrFinishButton();
                        if (currentDialog != null) currentDialog.dismiss();
                    }
                } else if (field.selectReady) {
                    sendSelectedCardsResponse();
                    hideCancelOrFinishButton();
                    if (currentDialog != null) currentDialog.dismiss();
                }
                break;
            }
            case 23: {
                GameField field = engine.getField();
                if (field.selectReady) {
                    sendSelectedCardsResponse();
                    hideCancelOrFinishButton();
                    if (currentDialog != null) currentDialog.dismiss();
                }
                break;
            }
            case 16: {
                sendResponseInt(-1);
                hideCancelOrFinishButton();
                if (currentDialog != null) currentDialog.dismiss();
                break;
            }
            case 18:
            case 24: {
                if (isPlaceSelecting) {
                    int selfType = engine.getClient().selfType;
                    ByteBuffer buf = ByteBuffer.allocate(3);
                    buf.put((byte) selfType);
                    buf.put((byte) 0);
                    buf.put((byte) 0);
                    engine.sendResponse(buf.array());
                    isPlaceSelecting = false;
                    fieldViewController.clearHighlight();
                    hideCancelOrFinishButton();
                }
                break;
            }
            case 25: {
                sendResponseInt(-1);
                hideCancelOrFinishButton();
                if (currentDialog != null) currentDialog.dismiss();
                break;
            }
            case 10:
            case 11: {
                hideCancelOrFinishButton();
                if (currentDialog != null) currentDialog.dismiss();
                break;
            }
            default: {
                hideCancelOrFinishButton();
                if (currentDialog != null) currentDialog.dismiss();
                break;
            }
        }
        currentSelectType = -1;
    }

    private void sendSelectedCardsResponse() {
        GameField field = engine.getField();
        int len = field.selectedCards.size();
        if (len > 255) len = 255;
        byte[] respbuf = new byte[len + 1];
        respbuf[0] = (byte) len;
        for (int i = 0; i < len; i++) {
            respbuf[i + 1] = (byte) field.selectedCards.get(i).select_seq;
        }
        engine.sendResponse(respbuf);
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
