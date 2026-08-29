package cn.garymb.ygomobile;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import cn.garymb.ygodata.YGOGameOptions;
import cn.garymb.ygomobile.audio.SoundManager;
import cn.garymb.ygomobile.game.DeckEditorManager;
import cn.garymb.ygomobile.game.GameEngine;
import cn.garymb.ygomobile.game.GameField;
import cn.garymb.ygomobile.game.GameFieldController;
import cn.garymb.ygomobile.game.GameTopInfoManager;
import cn.garymb.ygomobile.game.ReplayEngine;
import cn.garymb.ygomobile.game.ReplayReader;
import cn.garymb.ygomobile.game.ShowDialogUtil;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.network.LanDiscoveryManager;
import cn.garymb.ygomobile.render.CardDetailPanel;
import cn.garymb.ygomobile.render.TextureLoader;
import cn.garymb.ygomobile.ui.dialogs.LanModeDialog;
import cn.garymb.ygomobile.ui.dialogs.MainMenuDialog;
import cn.garymb.ygomobile.ui.dialogs.ReplayModeDialog;
import cn.garymb.ygomobile.ui.dialogs.ReplaySaveDialog;
import cn.garymb.ygomobile.ui.dialogs.SettingsDialog;
import cn.garymb.ygomobile.ui.dialogs.SingleModeDialog;
import cn.garymb.ygomobile.ui.dialogs.EmotionDialog;
import cn.garymb.ygomobile.ui.dialogs.DuelLogDialog;
import cn.garymb.ygomobile.ui.dialogs.YesOrNoDialog;
import cn.garymb.ygomobile.utils.DraggablePopupHelper;
import cn.garymb.ygomobile.utils.FullScreenUtils;
import ocgcore.DataManager;
import ocgcore.StringManager;
import ocgcore.data.Card;

public class YGOProActivity extends AppCompatActivity implements
        GameEngine.EngineListener,
        LanModeDialog.OnLanModeListener {

    private static final String TAG = "YGONativeGame";

    /** 公共字符串管理器：初始化后可供整个类调用（对齐 CardDetailPanel.mStringManager 惯例） */
    public final StringManager mStringManager = DataManager.get().getStringManager();

    private GameEngine engine;
    private SoundManager soundManager;
    private ImageLoader imageLoader;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private DeckEditorManager deckEditorManager;
    private View layoutDeckEditor;

    private LinearLayout layoutDeckControl;
    private FrameLayout layoutGameRight;
    private View layoutGameContent;

    private FrameLayout dialogContainer;
    private MainMenuDialog mainMenuDialog;
    private LanModeDialog lanModeDialog;

    private EditText etChatInput;
    private EmotionDialog emotionDialog;
    private DuelLogDialog duelLogDialog;

    private boolean isMyTurn = false;
    private volatile boolean isGameStarted = false;

    private ReplayEngine currentReplayEngine;
    private CardDetailPanel cardDetailPanel;
    private GameFieldController fieldCtl;
    private GameTopInfoManager topInfoManager;
    private ShowDialogUtil dialogUtil;
    private boolean exitOnReturn = true;
    private int directEnterMode = 0; // 0=normal, 1=replay dialog, 2=single dialog
    private FullScreenUtils mFullScreenUtils;
    private String currentBgPath;

    // 最近一次加入/创建房间的连接信息：断线或决斗结束返回局域网主界面时回显
    private String lastJoinNickname = "";
    private String lastJoinHost = "";
    private int lastJoinPort = 0;
    private String lastJoinRoomName = "";

    // 决斗结束后通讯发来的待保存录像队列（STOC_REPLAY 可能发来多个）
    private final List<byte[]> pendingReplays = new ArrayList<>();
    private boolean duelEndHandling = false;
    private YesOrNoDialog resultDialog;
    private ReplaySaveDialog replaySaveDialog;
    // STOC_REPLAY 紧随 STOC_DUEL_END 下发：录像处理延迟到该窗口内无新数据到达再启动，
    // 避免在录像数据尚未收齐时就走到「决斗结束」弹窗
    private static final long REPLAY_ARRIVAL_WAIT_MS = 800;
    private final Runnable duelEndReplayProcessor = new Runnable() {
        @Override
        public void run() {
            processPendingReplays();
        }
    };

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
            getMainMenuDialog().showMainMenu();
        }
    }

    @Override
    public void onPlayerEnter(String name, int pos) {
        runOnUiThread(() -> {
            if (lanModeDialog != null) lanModeDialog.handlePlayerEnter(name, pos);
        });
    }

    @Override
    public void onPlayerChange(int status) {
        runOnUiThread(() -> {
            if (lanModeDialog != null) lanModeDialog.handlePlayerChange(status);
        });
    }

    @Override
    public void onWatchChange(int watchCount) {
        runOnUiThread(() -> {
            if (lanModeDialog != null) lanModeDialog.handleWatchChange(watchCount);
        });
    }

    @Override
    public void onJoinGame(int lflist, int rule, int mode, int duelRule,
                           int noCheckDeck, int noShuffleDeck,
                           int startLp, int startHand, int drawCount, int timeLimit) {
        runOnUiThread(() -> {
            if (lanModeDialog != null) lanModeDialog.handleJoinGame(lflist, rule, mode, duelRule,
                    noCheckDeck, noShuffleDeck, startLp, startHand, drawCount, timeLimit);
        });
    }

    @Override
    public void onTypeChange(int type) {
        runOnUiThread(() -> {
            if (lanModeDialog != null) {
                boolean isTag = engine.getGameMode() == 2;
                lanModeDialog.handleTypeChange(type, isTag);
            }
        });
    }

    @Override
    public void onDeckError(int errorType, int cardCode) {
        runOnUiThread(() -> {
            if (lanModeDialog != null) lanModeDialog.handleDeckError(errorType, cardCode);
        });
    }

    private void setupFullScreen() {
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (getSupportActionBar() != null) {
            getSupportActionBar().hide();
        }
        if (mFullScreenUtils == null) {
            mFullScreenUtils = new FullScreenUtils(this, AppsSettings.get().isImmerSiveMode());
            mFullScreenUtils.onCreate();
        }
        mFullScreenUtils.fullscreen();
    }

    private void initViews() {
        dialogContainer = findViewById(R.id.dialog_container);
        layoutDeckControl = findViewById(R.id.layout_deck_control);
        layoutGameRight = findViewById(R.id.layout_game_right);
        layoutGameContent = findViewById(R.id.layout_game_content);
        if (layoutGameContent != null) layoutGameContent.setVisibility(View.GONE);
        etChatInput = findViewById(R.id.et_chat_input);
        // 聊天输入框初始可见性跟随停用聊天设置（对齐 gframe wChat：停用聊天时隐藏）
        if (etChatInput != null
                && AppsSettings.get().getIntSettings("chkDisableChatting", 0) == 1) {
            etChatInput.setVisibility(View.GONE);
        }
        setupChatInput();

        cardDetailPanel = new CardDetailPanel(this);
        cardDetailPanel.bindViews();
        topInfoManager = new GameTopInfoManager(this, mainHandler);
        topInfoManager.initViews();
        fieldCtl = new GameFieldController(this, mainHandler, topInfoManager);
        fieldCtl.create();

        setWindowBackground(Constants.CORE_SKIN_PATH + "/" + Constants.CORE_SKIN_BG_MENU);
    }

    private void setupChatInput() {
        if (etChatInput == null) return;
        etChatInput.setOnEditorActionListener((v, actionId, event) -> {
            boolean isSend = actionId == EditorInfo.IME_ACTION_SEND
                    || actionId == EditorInfo.IME_ACTION_DONE
                    || (event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN);
            if (isSend) {
                sendChatMessage();
                return true;
            }
            return false;
        });
    }

    private void sendChatMessage() {
        String message = etChatInput.getText().toString().trim();
        if (message.isEmpty()) return;
        if (engine != null && engine.getClient() != null) {
            engine.sendChat(message);
        }
        etChatInput.setText("");
    }

    /** 表情入口（CardDetailPanel 的 btn_emote，对齐 gframe BUTTON_EMOTICON）：开关切换 4x4 表情面板 */
    public void toggleEmotionDialog(View anchor) {
        if (emotionDialog == null) {
            emotionDialog = new EmotionDialog(this);
        }
        emotionDialog.toggle(anchor);
    }

    private void initEngine() {
        AppsSettings appsSettings = AppsSettings.get();
        soundManager = new SoundManager(this);
        // 初始化即按保存的音频设置应用（对齐 gframe game.cpp LoadConfig：
        // enable_sound/sound_volume/enable_music/music_volume/chkSwitchBGM）
        soundManager.init(
                appsSettings.getIntSettings("soundVolume", 50) / 100.0,
                appsSettings.getIntSettings("musicVolume", 50) / 100.0,
                appsSettings.getIntSettings("chkEnableSound", 1) == 1,
                appsSettings.getIntSettings("chkEnableMusic", 1) == 1);
        soundManager.setAutoSwitchBGM(appsSettings.getIntSettings("chkSwitchBGM", 0) == 1);

        imageLoader = new ImageLoader(true);

        engine = new GameEngine(soundManager);
        engine.setListener(this);
        engine.setPlayerName(Constants.PlayerName);

        TextureLoader.get().init();

        cardDetailPanel.setImageLoader(imageLoader);
        cardDetailPanel.bindSideButtonIcons();
        fieldCtl.init(engine, imageLoader);

        // 初始化时通过 getIntSettings 统一应用全部已保存设置到对应功能
        applySettingsToEngine();
    }

    private void loadData() {
        new Thread(() -> {
            DataManager.get().load(false);
            Log.i(TAG, "DataManager loaded");
        }, "DataLoad").start();
    }

    // === 供三个UI管理类回调的桥接方法 ===

    public GameEngine getEngine() {
        return engine;
    }

    public int getCurrentSelectType() {
        return cardDetailPanel.getSelectType();
    }

    public ReplayEngine getCurrentReplayEngine() {
        return currentReplayEngine;
    }

    public void quitReplay() {
        ReplayModeDialog.quitReplay(this);
    }

    public void toggleSoundMute() {
        if (soundManager == null) return;
        // 对齐 gframe imgVol 开关：走 AppsSettings 保存（与 SettingsDialog 的
        // chkEnableSound/chkEnableMusic 同一存储），避免设置对话框与声音按钮脱节
        AppsSettings settings = AppsSettings.get();
        boolean currentSound = settings.getIntSettings("chkEnableSound", 1) == 1;
        boolean currentMusic = settings.getIntSettings("chkEnableMusic", 1) == 1;
        boolean muted = currentSound || currentMusic;
        settings.saveIntSettings("chkEnableSound", muted ? 0 : 1);
        settings.saveIntSettings("chkEnableMusic", muted ? 0 : 1);
        soundManager.enableSounds(!muted);
        soundManager.enableMusic(!muted);
        if (cardDetailPanel != null) cardDetailPanel.updateSoundIcon(!muted);
    }

    /** 决斗速度开关（对齐 gframe imgQuickAnimation 点击切换 quick_animation 并保存） */
    public void toggleQuickAnimation() {
        AppsSettings settings = AppsSettings.get();
        boolean quick = settings.getIntSettings("chkQuickAnimation", 0) == 1;
        settings.saveIntSettings("chkQuickAnimation", quick ? 0 : 1);
        if (cardDetailPanel != null) cardDetailPanel.updateSpeedIcon(!quick);
    }

    private boolean handleDirectIntent(Intent intent) {
        if (intent == null) return false;

        YGOGameOptions options = intent.getParcelableExtra(YGOGameOptions.YGO_GAME_OPTIONS_BUNDLE_KEY);
        if (options != null) {
            long time = intent.getLongExtra(YGOGameOptions.YGO_GAME_OPTIONS_BUNDLE_TIME, 0);
            if (System.currentTimeMillis() - time < YGOGameOptions.TIME_OUT) {
                joinFromOptions(options);
                LanModeDialog.showPlayerWaitingForDirectJoin(this, options);
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
            saveLastConnectionInfo(Constants.PlayerName, host, port, room);
            engine.connectToServer(host, port, false,
                    room != null ? room : "", "",
                    0, 0, 5, 8000, 5, 1, 0, false, false);
            LanModeDialog.showPlayerWaitingForDirectJoin(this, null);
            return true;
        }

        if (intent.getBooleanExtra("botMode", false)) {
            engine.setBotMode(true);
            engine.connectToServer("127.0.0.1", 7911, true,
                    "Bot Game", "",
                    5, 0, 5, 8000, 5, 1, 0, true, false);
            engine.startBotDuel("127.0.0.1", 7911, "WindBot", "");
            getMainMenuDialog().hideMainMenu();
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
                        getMainMenuDialog().hideMainMenu();
                        ReplayModeDialog.startReplayPlayback(this, replayFile.getAbsolutePath(), 1);
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
                        getMainMenuDialog().hideMainMenu();
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
            directEnterMode = 1;
            dialogContainer.post(() -> ReplayModeDialog.showReplayModeDialog(this));
            return true;
        }

        if (showSingleDialog) {
            directEnterMode = 2;
            dialogContainer.post(() -> SingleModeDialog.showSingleModeDialog(this));
            return true;
        }

        return false;
    }

    private void joinFromOptions(YGOGameOptions options) {
        String host = options.mServerAddr;
        int port = options.mPort;
        String room = options.mRoomName != null ? options.mRoomName : "";
        String user = options.mUserName != null ? options.mUserName : Constants.PlayerName;
        String password = options.mRoomName != null ? options.mRoomName : "";
        saveLastConnectionInfo(user, host, port, room);
        engine.setPlayerName(user);
        engine.connectToServer(host, port, false, room, password,
                0, 0, 5, 8000, 5, 1, 0, false, false);
    }

    // === Main Menu ===

    public MainMenuDialog getMainMenuDialog() {
        if (mainMenuDialog == null) {
            mainMenuDialog = new MainMenuDialog(this);
        }
        return mainMenuDialog;
    }

    public GameFieldController getFieldCtl() {
        return fieldCtl;
    }

    public GameTopInfoManager getTopInfoManager() {
        return topInfoManager;
    }

    public ShowDialogUtil getDialogUtil() {
        if (dialogUtil == null) {
            dialogUtil = new ShowDialogUtil(this, imageLoader, mainHandler);
        }
        return dialogUtil;
    }

    public CardDetailPanel getCardDetailPanel() {
        return cardDetailPanel;
    }

    public void setCurrentReplayEngine(ReplayEngine engine) {
        currentReplayEngine = engine;
    }

    public View getDialogContainer() {
        return dialogContainer;
    }

    public void setLanModeDialog(LanModeDialog dialog) {
        lanModeDialog = dialog;
        lanModeDialog.setCardNameResolver(this::getCardDisplayName);
    }

    public SoundManager getSoundManager() {
        return soundManager;
    }

    public void hideGameUI() {
        fieldCtl.hide();
        cardDetailPanel.onGameUIHidden();
        // 退出对战（layout_game_right 隐藏）时，一并关闭正在显示的表情面板
        if (emotionDialog != null) emotionDialog.dismiss();
        // 关闭日志面板并清空记录（对齐桌面版 CloseDuelWindow 的 lstLog->clear）
        if (duelLogDialog != null) duelLogDialog.dismiss();
        DuelLogDialog.clearLogs();
        if (dialogContainer != null) dialogContainer.setVisibility(View.GONE);
        if (layoutGameRight != null) layoutGameRight.setVisibility(View.GONE);
        if (layoutGameContent != null) layoutGameContent.setVisibility(View.GONE);
    }

    private void showGameUI() {
        setWindowBackground(Constants.CORE_SKIN_PATH + "/" + Constants.CORE_SKIN_BG);
        getMainMenuDialog().hideMainMenu();
        if (layoutGameContent != null) layoutGameContent.setVisibility(View.VISIBLE);
        if (layoutGameRight != null) layoutGameRight.setVisibility(View.VISIBLE);

        // layout_game_right 显示第一时间初始化顶部信息：头像/玩家名称/房间血量（猜拳前可见）
        if (topInfoManager != null) topInfoManager.prepareForDisplay();

        fieldCtl.show();
        cardDetailPanel.onGameUIShown();
        if (dialogContainer != null) dialogContainer.setVisibility(View.VISIBLE);
    }

    private void enterDuelingUI() {
        getMainMenuDialog().hideMainMenu();
        showGameUI();
        if (lanModeDialog != null) {
            lanModeDialog.setOnDismissListener(null);
            lanModeDialog.dismiss();
        }
        isGameStarted = true;
    }

    /**
     * 连接断开 / 决斗结束时调用：不退出 Activity，
     * 隐藏左侧卡片详情面板(layout_card_detail_panel)与右侧决斗场区(layout_game_right)，
     * 重新显示 LanModeDialog 的 lan main 布局，并回显加入游戏时填写的
     * username、host、port、roomname 等信息；无连接信息时回退主菜单
     */
    private void returnToLanMain(String toastMsg) {
        if (isFinishing() || isDestroyed()) return;
        isGameStarted = false;
        duelEndHandling = false;
        pendingReplays.clear();
        if (replaySaveDialog != null) {
            replaySaveDialog.dismiss();
            replaySaveDialog = null;
        }
        if (topInfoManager != null) topInfoManager.stopTimer();
        if (dialogUtil != null) dialogUtil.dismissOpenGameDialogs();
        hideGameUI();
        if (layoutDeckEditor != null) layoutDeckEditor.setVisibility(View.GONE);
        if (layoutDeckControl != null) layoutDeckControl.setVisibility(View.GONE);
        setWindowBackground(Constants.CORE_SKIN_PATH + "/" + Constants.CORE_SKIN_BG_MENU);

        if (TextUtils.isEmpty(lastJoinHost)) {
            getMainMenuDialog().restoreMainMenu();
        } else if (lanModeDialog != null && lanModeDialog.isShowing()) {
            lanModeDialog.showLanMain();
            lanModeDialog.preFillConnectionFields(lastJoinNickname, lastJoinHost,
                    String.valueOf(lastJoinPort), lastJoinRoomName);
        } else {
            LanModeDialog dialog = new LanModeDialog(this, this);
            setLanModeDialog(dialog);
            dialog.show(dialogContainer);
            dialog.setOnDismissListener(() -> getMainMenuDialog().restoreMainMenu());
            dialog.preFillConnectionFields(lastJoinNickname, lastJoinHost,
                    String.valueOf(lastJoinPort), lastJoinRoomName);
            dialog.showLanMain();
        }
        if (toastMsg != null && !toastMsg.isEmpty()) {
            Toast.makeText(this, toastMsg, Toast.LENGTH_SHORT).show();
        }
    }

    private void saveLastConnectionInfo(String nickname, String host, int port, String roomName) {
        lastJoinNickname = nickname != null ? nickname : "";
        lastJoinHost = host != null ? host : "";
        lastJoinPort = port;
        lastJoinRoomName = roomName != null ? roomName : "";
    }

    @Override
    public void onCreateHostConfirmed(int lflist, int ruleIdx, int modeIdx, int duelRule,
                                      int startLP, int startHand, int drawCount, int timeLimit,
                                      boolean noCheckDeck, boolean noShuffleDeck,
                                      String hostName, String password, String nickname) {
        String roomName = (hostName != null && !hostName.isEmpty()) ? hostName : "Local Game";
        String userName = (nickname != null && !nickname.isEmpty()) ? nickname : Constants.PlayerName;

        String localIp = LanDiscoveryManager.getLocalIpAddress();
        saveLastConnectionInfo(userName, localIp != null ? localIp : "127.0.0.1", 7911, roomName);

        engine.setPlayerName(userName);
        engine.startLocalServerWithSettings(lflist, ruleIdx, modeIdx, duelRule,
                noCheckDeck, noShuffleDeck,
                startLP, startHand, drawCount, timeLimit,
                roomName, password != null ? password : "");
    }

    @Override
    public void onJoinGameRequested(String ip, String port, String password, String nickname) {
        int portNum;
        try {
            portNum = Integer.parseInt(port);
        } catch (NumberFormatException e) {
            portNum = 7911;
        }
        String userName = (nickname != null && !nickname.isEmpty()) ? nickname : Constants.PlayerName;
        saveLastConnectionInfo(userName, ip, portNum, password);
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

    public void showDeckEditorView() {
        setWindowBackground(Constants.CORE_SKIN_PATH + "/" + Constants.CORE_SKIN_BG_DECK);
        getMainMenuDialog().hideMainMenu();
        hideGameUI();
        if (layoutGameContent != null) layoutGameContent.setVisibility(View.VISIBLE);
        if (layoutDeckEditor == null) {
            layoutDeckEditor = findViewById(R.id.layout_deck_editor);
        }
        if (layoutDeckEditor != null) {
            layoutDeckEditor.setVisibility(View.VISIBLE);
        }

        // 隐藏右侧决斗场区，让卡组编辑器占据其空间
        if (layoutGameRight != null) layoutGameRight.setVisibility(View.GONE);

        if (layoutDeckControl == null) layoutDeckControl = findViewById(R.id.layout_deck_control);
        if (layoutDeckControl != null) layoutDeckControl.setVisibility(View.VISIBLE);

        // 立刻显示左侧卡片详情面板（默认内容），并切换为卡组编辑器模式
        cardDetailPanel.enterDeckEditorMode();

        if (deckEditorManager == null) {
            deckEditorManager = new DeckEditorManager(this, imageLoader, cardDetailPanel);
            deckEditorManager.setListener(new DeckEditorManager.DeckEditorListener() {
                @Override
                public void onDeckModified() {
                }

                @Override
                public void onDeckSaved() {
                }

                @Override
                public void onExitEditor() {
                    hideDeckEditorView();
                    getMainMenuDialog().restoreMainMenu();
                }

                @Override
                public void onCardSelected(Card card) {
                }

                @Override
                public void onSearchResultsUpdated(int count) {
                }

                @Override
                public void onSideDeckFinished(List<Integer> main, List<Integer> extra, List<Integer> side) {
                    if (engine != null) {
                        engine.sendDeckUpdate(main, extra, side);
                    }
                    // 副卡组替换完成：退出副卡组模式并隐藏整个卡组编辑器布局，
                    // 等待下次 STOC_CHANGE_SIDE 进入副卡组替换模式时再显示
                    if (deckEditorManager != null) {
                        deckEditorManager.exitSideMode();
                    }
                    hideDeckEditorView();
                }
            });
        }
        if (layoutDeckEditor != null) {
            deckEditorManager.initialize(layoutDeckEditor);
        }
    }

    private void hideDeckEditorView() {
        if (layoutDeckEditor != null) {
            layoutDeckEditor.setVisibility(View.GONE);
        }
        if (layoutDeckControl != null) layoutDeckControl.setVisibility(View.GONE);
        cardDetailPanel.exitDeckEditorMode();
    }

    public void setWindowBackground(String relativePath) {
        String path = AppsSettings.get().getResourcePath() + "/" + relativePath;
        if (TextUtils.equals(path, currentBgPath)) {
            return;
        }
        File file = new File(path);
        if (file.exists()) {
            try {
                Bitmap bitmap = BitmapFactory.decodeFile(path);
                if (bitmap != null) {
                    getWindow().setBackgroundDrawable(new BitmapDrawable(getResources(), bitmap));
                    currentBgPath = path;
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to load background: " + relativePath, e);
            }
        }
    }


    public void showSettingsDialog() {
        getMainMenuDialog().hideMainMenu();
        SettingsDialog dialog = new SettingsDialog(this, () -> applySettingsToEngine());
        dialog.show(dialogContainer);
        dialog.setOnDismissListener(() -> {
            boolean deckEditorShowing = layoutDeckEditor != null
                    && layoutDeckEditor.getVisibility() == View.VISIBLE;
            boolean gameRightShowing = layoutGameRight != null
                    && layoutGameRight.getVisibility() == View.VISIBLE;
            if (!deckEditorShowing && !gameRightShowing) {
                getMainMenuDialog().restoreMainMenu();
            }
        });
    }

    public void applySettingsToEngine() {
        AppsSettings appsSettings = AppsSettings.get();
        boolean enableSound = appsSettings.getIntSettings("chkEnableSound", 1) == 1;
        boolean enableMusic = appsSettings.getIntSettings("chkEnableMusic", 1) == 1;
        if (soundManager != null) {
            soundManager.enableSounds(enableSound);
            soundManager.enableMusic(enableMusic);
            soundManager.setSoundVolume(appsSettings.getIntSettings("soundVolume", 50) / 100.0);
            soundManager.setMusicVolume(appsSettings.getIntSettings("musicVolume", 50) / 100.0);
            soundManager.setAutoSwitchBGM(appsSettings.getIntSettings("chkSwitchBGM", 0) == 1);
        }
        if (cardDetailPanel != null) {
            // 对齐 gframe imgVol/imgQuickAnimation：声音与速度按钮图标同步设置状态
            cardDetailPanel.updateSoundIcon(enableSound || enableMusic);
            cardDetailPanel.updateSpeedIcon(appsSettings.getIntSettings("chkQuickAnimation", 0) == 1);
            // 对齐 gframe BUTTON_CHATTING：聊天按钮图标与输入框可见性同步停用聊天设置
            boolean chatDisabled = appsSettings.getIntSettings("chkDisableChatting", 0) == 1;
            cardDetailPanel.updateChatIcon(chatDisabled);
            if (etChatInput != null) {
                etChatInput.setVisibility(chatDisabled ? View.GONE : View.VISIBLE);
            }
        }
        if (deckEditorManager != null) {
            deckEditorManager.refreshLimitList();
        }
    }

    // === EngineListener ===

    @Override
    public void onStateChanged(GameEngine.GameState newState) {
        Log.i(TAG, "State: " + newState);
        switch (newState) {
            case LOBBY:
                duelEndHandling = false;
                if (lanModeDialog != null && lanModeDialog.isPlayerWaitingVisible()) {
                    // Already showing player waiting via LanModeDialog, do nothing
                } else {
                    getMainMenuDialog().hideMainMenu();
                }
                break;
            case DECK_SELECT:
                getDialogUtil().showDeckSelectDialog();
                break;
            case HAND_SELECT:
                enterDuelingUI();
                getDialogUtil().resetRpsResultState();
                getDialogUtil().showHandSelectDialog();
                break;
            case TP_SELECT:
                enterDuelingUI();
                getDialogUtil().showTPSelectDialog();
                break;
            case DUELING:
                enterDuelingUI();
                cardDetailPanel.showBottomActions();
                pendingReplays.clear();
                duelEndHandling = false;
                break;
            case SIDING:
                showDeckEditorView();              // 打开卡组编辑器
                if (deckEditorManager != null) {
                    deckEditorManager.enterSideMode();  // 记录替换前张数并允许编辑
                }
                break;
            case DUEL_END:
                cardDetailPanel.closeGameButtons();
                duelEndHandling = true;
                if (dialogUtil != null) dialogUtil.dismissOpenGameDialogs();
                if (resultDialog != null) {
                    resultDialog.dismiss();
                    resultDialog = null;
                }
                if (engine != null) engine.disconnect();
                // 顺序：先处理通讯发来的录像（保存/取消），全部完成后再弹「决斗结束」对话框
                scheduleReplayProcessing();
                break;
            case DISCONNECTED:
                if (duelEndHandling) break; // 决斗结束流程已接管返回逻辑，避免重复
                returnToLanMain(isGameStarted ? "与服务器连接已断开" : null);
                break;
        }
    }

    @Override
    public void onFieldChanged() {
        fieldCtl.invalidate();
        runOnUiThread(() -> topInfoManager.updateCardCountDisplay(engine.getField()));
    }

    @Override
    public void onPlayerInfoUpdated(int player) {
        runOnUiThread(() -> {
            // player 为本地视角索引（0=我方）；playerInfos 按座位号存储（STOC_HS_PLAYER_ENTER），
            // 我方名称取 selfType 座位、对方取另一座位（1v1），越界座位回退默认名
            int selfSeat = engine.getClient().selfType;
            int seat = (player == 0) ? selfSeat : (selfSeat ^ 1);
            GameEngine.PlayerInfo info = (seat >= 0 && seat < engine.playerInfos.length)
                    ? engine.playerInfos[seat] : null;
            GameField.PlayerField pf = engine.getField().players[player];
            String defaultName = (player == 0) ? Constants.PlayerName : "Opponent";
            String name = (info == null || info.name.isEmpty()) ? defaultName : info.name;
            topInfoManager.setPlayerDisplay(player, name, String.valueOf(pf.lp));
            topInfoManager.updateLpBars(engine.getField());
            topInfoManager.updateCardCountDisplay(engine.getField());
        });
    }

    @Override
    public void onPhaseChanged(int phase) {
        runOnUiThread(() -> {
            // currentPlayer 为本地视角索引（MSG_NEW_TURN 已做 localPlayer 转换）：
            // 不管我方是先攻还是后攻，我方回合恒为 0；
            // selfType 是座位号，不能与协议侧回合索引直接比较
            isMyTurn = (engine.getField().currentPlayer == 0);
            topInfoManager.updateTurn(engine.getField().turnCount, isMyTurn);
            fieldCtl.updateActionButtonsForPhase(phase, isMyTurn);
        });
    }

    @Override
    public void onChatReceived(String player, String message) {
        runOnUiThread(() -> fieldCtl.appendChat(player, message));
    }

    /**
     * 聊天开关（对齐 gframe event_handler.cpp BUTTON_CHATTING）：
     * 停用状态（chkIgnore1=1）→ 启用：图标 tTalk、显示聊天输入框；
     * 启用状态 → 停用：图标 tShut、隐藏聊天输入框并清空聊天消息
     */
    public void toggleChatInput() {
        if (etChatInput == null) return;
        AppsSettings settings = AppsSettings.get();
        boolean ignored = settings.getIntSettings("chkDisableChatting", 0) == 1;
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (ignored) {
            settings.saveIntSettings("chkDisableChatting", 0);
            cardDetailPanel.updateChatIcon(false);
            etChatInput.setVisibility(View.VISIBLE);
            etChatInput.requestFocus();
            if (imm != null) {
                imm.showSoftInput(etChatInput, InputMethodManager.SHOW_IMPLICIT);
            }
        } else {
            settings.saveIntSettings("chkDisableChatting", 1);
            cardDetailPanel.updateChatIcon(true);
            if (imm != null && etChatInput.getWindowToken() != null) {
                imm.hideSoftInputFromWindow(etChatInput.getWindowToken(), 0);
            }
            etChatInput.clearFocus();
            etChatInput.setVisibility(View.GONE);
            if (fieldCtl != null) fieldCtl.clearChatMessages();
        }
    }

    @Override
    public void onSelectRequired(int selectType, ByteBuffer data) {
        runOnUiThread(() -> {
            // 结束阶段按钮仅在通讯允许进入 EP 时有效：
            // 空闲指令(11)/战斗指令(10) 路径内会按指令可用性重新启用；其余请求一律隐藏
            fieldCtl.setEpButtonAllowed(selectType == 10 || selectType == 11);
            cardDetailPanel.setSelectType(selectType);
            ShowDialogUtil showDialogUtil = getDialogUtil();
            switch (selectType) {
                case 0:
                    showDialogUtil.showHandSelectDialog();
                    break;
                case 1:
                    showDialogUtil.showTPSelectDialog();
                    break;
                case 10:
                    showDialogUtil.showBattleCmdDialog(data);
                    break;
                case 11:
                    showDialogUtil.showIdleCmdDialog(data);
                    break;
                case 12:
                    showDialogUtil.showEffectYnDialog(data);
                    break;
                case 13:
                    showDialogUtil.showYesNoDialog(data);
                    break;
                case 14:
                    showDialogUtil.showOptionDialog(data);
                    break;
                case 15:
                    showDialogUtil.showCardSelectDialog(data);
                    break;
                case 16:
                    showDialogUtil.showChainSelectDialog(data);
                    break;
                case 18:
                    // 对齐 gframe MSG_SELECT_PLACE：先按 chkMAutoPos/chkSTAutoPos 尝试自动放置，失败再弹选择框
                    if (!fieldCtl.tryAutoPlaceSelect()) {
                        showDialogUtil.showPlaceSelectDialog(false);
                    }
                    break;
                case 19:
                    showDialogUtil.showPositionSelectDialog(data);
                    break;
                case 20:
                    showDialogUtil.showTributeSelectDialog(data);
                    break;
                case 21:
                    showDialogUtil.showSortChainDialog(data);
                    break;
                case 22:
                    showDialogUtil.showCounterSelectDialog(data);
                    break;
                case 23:
                    showDialogUtil.showSumSelectDialog(data);
                    break;
                case 24:
                    showDialogUtil.showPlaceSelectDialog(true);
                    break;
                case 25:
                    showDialogUtil.showSortCardDialog(data);
                    break;
                case 26:
                    showDialogUtil.showUnselectCardDialog(data);
                    break;
                case 27:
                    showDialogUtil.showConfirmCardsDialog(data);
                    break;
                case 140:
                    showDialogUtil.showAnnounceRaceDialog();
                    break;
                case 141:
                    showDialogUtil.showAnnounceAttribDialog();
                    break;
                case 142:
                    showDialogUtil.showAnnounceCardDialog(data);
                    break;
                case 143:
                    showDialogUtil.showAnnounceNumberDialog(data);
                    break;
                default:
                    Log.w(TAG, "Unhandled select type: " + selectType);
                    break;
            }
        });
    }

    @Override
    public void onDuelResult(int winner, int reason) {
        topInfoManager.stopTimer();
        runOnUiThread(() -> {
            String result;
            if (winner == 2) {
                result = "平局";
            } else if (engine.isSelfSide(winner)) {
                result = "🎉 你赢了！";
            } else {
                result = "😢 你输了";
            }
            showResultDialog(result, false);
        });
    }

    @Override
    public void onHintMessage(String hint) {
        runOnUiThread(() -> fieldCtl.showHint(hint, 2000));
    }

    @Override
    public void onReplayData(byte[] data) {
        Log.i(TAG, "Replay data received, size=" + data.length);
        runOnUiThread(() -> {
            pendingReplays.add(data);
            // 决斗结束流程中通讯仍在补发录像：重置等待窗口，确保队列收全后再开始处理
            if (duelEndHandling) {
                scheduleReplayProcessing();
            }
        });
    }

    @Override
    public void onTimeLimitUpdate(int player, int leftTime) {
        runOnUiThread(() -> topInfoManager.onTimeLimitUpdate(player, leftTime, engine.getGameTimeLimit()));
    }

    @Override
    public void onChainAnimation(int code, int controler, int location, int sequence) {
        runOnUiThread(() -> fieldCtl.selectCardWithAutoClear(controler, location, sequence, 1500));
    }

    @Override
    public void onHandResult(int myHand, int oppHand) {
        runOnUiThread(() -> getDialogUtil().onHandResult(myHand, oppHand));
    }

    public String getCardDisplayName(int code) {
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

    public void showCardInfoPanel(GameField.ClientCard card) {
        cardDetailPanel.showCardInfo(card);
    }

    public void showResultDialog(String result) {
        showResultDialog(result, true);
    }

    public void showResultDialog(String result, boolean exitOnConfirm) {
        if (resultDialog != null) resultDialog.dismiss();
        resultDialog = new YesOrNoDialog(this);
        resultDialog.setTitle("决斗结果")
                .setMessage(result)
                .setPositiveButton(v -> {
                    if (exitOnConfirm) {
                        finish();
                    }
                })
                .setOnDismissListener(() -> resultDialog = null)
                .setCancelable(false);
        resultDialog.show();
    }

    /**
     * 决斗结束提示框：仅显示「确定」按钮（TYPE_MESSAGE）。
     * 弹窗时机已调整为：通讯发来的录像全部确认保存或取消之后（见 processPendingReplays），
     * 点击确定后隐藏决斗 UI 并重新显示 LanModeDialog
     */
    private void showDuelEndDialog() {
        if (isFinishing() || isDestroyed()) return;
        YesOrNoDialog dialog = new YesOrNoDialog(this);
        dialog.setMessage(mStringManager.getSystemString(1500, "決斗结束。"))
                .setType(YesOrNoDialog.TYPE_MESSAGE)
                .setPositiveButtonText(mStringManager.getSystemString(1211, "确定"))
                .setPositiveButton(v -> returnToLanMain(null))
                .setCenterInView(layoutGameRight)
                .setCancelable(false);
        dialog.show();
    }

    /**
     * 延迟启动录像处理：先取消上一次调度，窗口内若有新录像到达（onReplayData）会再次重置，
     * 直到通讯不再发送录像才真正开始逐个弹出录像保存对话框
     */
    private void scheduleReplayProcessing() {
        mainHandler.removeCallbacks(duelEndReplayProcessor);
        mainHandler.postDelayed(duelEndReplayProcessor, REPLAY_ARRIVAL_WAIT_MS);
    }

    /**
     * 逐个处理通讯发来的录像：有待保存项则弹出录像保存对话框；
     * 全部处理完毕（保存或取消跳过）后才弹出「决斗结束」对话框
     */
    private void processPendingReplays() {
        // 防止延迟调度与队列内递归调用重叠执行
        mainHandler.removeCallbacks(duelEndReplayProcessor);
        if (isFinishing() || isDestroyed()) {
            pendingReplays.clear();
            return;
        }
        if (pendingReplays.isEmpty()) {
            showDuelEndDialog();
            return;
        }
        final byte[] replayData = pendingReplays.remove(0);
        // 对齐 gframe duelclient.cpp STOC_REPLAY：勾选自动保存录像时不弹窗，
        // 直接以录像开始时间命名自动保存（对应提示 1367）
        if (AppsSettings.get().getIntSettings("chkAutoSaveReplay", 0) == 1) {
            saveReplayFile(replayData, getReplayDefaultName(replayData), true);
            mainHandler.post(this::processPendingReplays);
            return;
        }
        replaySaveDialog = new ReplaySaveDialog(this);
        replaySaveDialog.setDefaultName(getReplayDefaultName(replayData))
                .setCenterInView(layoutGameRight)
                .setOnReplayActionListener(new ReplaySaveDialog.OnReplayActionListener() {
                    @Override
                    public void onSave(String fileName) {
                        saveReplayFile(replayData, fileName, false);
                        mainHandler.post(() -> processPendingReplays());
                    }

                    @Override
                    public void onCancel() {
                        // 跳过当前录像，检查通讯是否还发来了其它录像文件
                        mainHandler.post(() -> processPendingReplays());
                    }
                });
        replaySaveDialog.show();
    }

    /**
     * 从通讯发来的录像数据中解析默认文件名，
     * 与 gframe duelclient.cpp STOC_REPLAY 一致：录像开始时间 %Y-%m-%d %H-%M-%S
     */
    private String getReplayDefaultName(byte[] data) {
        try {
            if (data == null || data.length < 24) return "_LastReplay";
            ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
            int id = buf.getInt();
            if (id != ReplayReader.REPLAY_ID_YRP1 && id != ReplayReader.REPLAY_ID_YRP2) {
                return "_LastReplay";
            }
            buf.getInt(); // version
            int flag = buf.getInt();
            int seed = buf.getInt();
            buf.getInt(); // datasize
            int startTime = buf.getInt();
            long ts = ((flag & ReplayReader.REPLAY_UNIFORM) != 0)
                    ? Integer.toUnsignedLong(startTime)
                    : Integer.toUnsignedLong(seed);
            return new SimpleDateFormat("yyyy-MM-dd HH-mm-ss", Locale.US)
                    .format(new Date(ts * 1000L));
        } catch (Exception e) {
            return "_LastReplay";
        }
    }

    private void saveReplayFile(byte[] data, String fileName, boolean autoSave) {
        String safeName = sanitizeReplayName(fileName);
        try {
            File dir = new File(AppsSettings.get().getReplayDir());
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, safeName + Constants.YRP_FILE_EX);
            FileOutputStream fos = new FileOutputStream(file);
            try {
                fos.write(data);
                fos.flush();
            } finally {
                fos.close();
            }
            Log.i(TAG, "Replay saved: " + file.getAbsolutePath());
            if (autoSave) {
                // 对齐 gframe 自動保存提示（系统字符串 1367「リプレイ自動保存 %ls.yrp」）：
                // 将 %ls 占位替换为实际保存的录像文件名
                String template = mStringManager
                        .getSystemString(1367, "リプレイ自動保存 %ls.yrp");
                Toast.makeText(this, template.replace("%ls", safeName), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, mStringManager
                        .getSystemString(1335, "保存成功"), Toast.LENGTH_SHORT).show();
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to save replay", e);
            Toast.makeText(this, "录像保存失败: " + safeName, Toast.LENGTH_SHORT).show();
        }
    }

    private String sanitizeReplayName(String name) {
        String n = (name == null) ? "" : name.trim();
        if (n.toLowerCase(Locale.US).endsWith(Constants.YRP_FILE_EX)) {
            n = n.substring(0, n.length() - Constants.YRP_FILE_EX.length());
        }
        n = n.replaceAll("[\\\\/:*?\"<>|]", "").trim();
        if (n.isEmpty()) n = "_LastReplay";
        return n;
    }

    public void showHintMessage(String msg) {
        fieldCtl.showHint(msg, 3000);
    }

    // === Response helpers ===

    public void sendResponseInt(int value) {
        ByteBuffer buf = ByteBuffer.allocate(4);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(value);
        engine.sendResponse(buf.array());
    }

    // === Lifecycle ===

    @Override
    protected void onResume() {
        super.onResume();
        setupFullScreen();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mainHandler.removeCallbacks(duelEndReplayProcessor);
        DraggablePopupHelper.resetAllPositions(this);
        if (topInfoManager != null) {
            topInfoManager.stopTimer();
        }
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
                if (mainMenuDialog != null && mainMenuDialog.isShowing()) {
                    soundManager.stopBGM();
                    finish();
                    return;
                }
                if (!isGameStarted) {
                    getMainMenuDialog().restoreMainMenu();
                    return;
                }
                YesOrNoDialog dialog = new YesOrNoDialog(YGOProActivity.this);
                dialog.setTitle("退出决斗")
                        .setMessage("确定要退出当前决斗吗？")
                        .setType(YesOrNoDialog.TYPE_YES_NO)
                        .setPositiveButtonText("确定")
                        .setNegativeButtonText("取消")
                        .setPositiveButton(v -> {
                            if (engine != null) {
                                if (engine.getState() == GameEngine.GameState.DUELING) {
                                    engine.sendSurrender();
                                } else {
                                    engine.disconnect();
                                }
                            }
                            setEnabled(false);
                            getOnBackPressedDispatcher().onBackPressed();
                        });
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

    /** 决斗日志面板（对齐桌面版 imgLog 开关 wLogs）：由卡片详情面板侧栏按钮触发 */
    public void showDuelLogDialog() {
        if (duelLogDialog == null) {
            duelLogDialog = new DuelLogDialog(this);
        }
        duelLogDialog.toggle();
    }
}
