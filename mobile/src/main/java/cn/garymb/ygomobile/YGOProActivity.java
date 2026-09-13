package cn.garymb.ygomobile;

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
import android.view.View;
import android.view.WindowManager;
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
import cn.garymb.ygomobile.game.ChatInputUI;
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
import cn.garymb.ygomobile.render.SpecEffectOverlay;
import cn.garymb.ygomobile.render.TextureLoader;
import cn.garymb.ygomobile.ui.dialogs.CreateHostDialog;
import cn.garymb.ygomobile.ui.dialogs.DuelLogDialog;
import cn.garymb.ygomobile.ui.dialogs.EmotionDialog;
import cn.garymb.ygomobile.ui.dialogs.LanModeDialog;
import cn.garymb.ygomobile.ui.dialogs.MainMenuDialog;
import cn.garymb.ygomobile.ui.dialogs.PlayerWaitingDialog;
import cn.garymb.ygomobile.ui.dialogs.ReplayModeDialog;
import cn.garymb.ygomobile.ui.dialogs.ReplaySaveDialog;
import cn.garymb.ygomobile.ui.dialogs.SettingsDialog;
import cn.garymb.ygomobile.ui.dialogs.SingleModeDialog;
import cn.garymb.ygomobile.ui.dialogs.YesOrNoDialog;
import cn.garymb.ygomobile.utils.DraggablePopupHelper;
import cn.garymb.ygomobile.utils.FullScreenUtils;
import ocgcore.DataManager;
import ocgcore.StringManager;
import ocgcore.data.Card;
import ocgcore.enums.DuelPhase;

public class YGOProActivity extends AppCompatActivity implements
        GameEngine.EngineListener,
        LanModeDialog.OnLanModeListener,
        CreateHostDialog.OnCreateHostListener,
        PlayerWaitingDialog.OnPlayerWaitingListener {

    private static final String TAG = "YGONativeGame";

    /**
     * 公共字符串管理器：初始化后可供整个类调用（对齐 CardDetailPanel.mStringManager 惯例）
     */
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
    private CreateHostDialog createHostDialog;
    private PlayerWaitingDialog playerWaitingDialog;

    private EditText etChatInput;
    private EmotionDialog emotionDialog;
    private DuelLogDialog duelLogDialog;

    private boolean isMyTurn = false;
    private volatile boolean isGameStarted = false;

    private ReplayEngine currentReplayEngine;
    private CardDetailPanel cardDetailPanel;
    private ChatInputUI chatInputUI;
    private GameTopInfoManager topInfoManager;
    private GameFieldController fieldCtl;
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
            if (playerWaitingDialog != null) playerWaitingDialog.handlePlayerEnter(name, pos);
        });
    }

    @Override
    public void onPlayerChange(int status) {
        runOnUiThread(() -> {
            if (playerWaitingDialog != null) playerWaitingDialog.handlePlayerChange(status);
        });
    }

    @Override
    public void onWatchChange(int watchCount) {
        runOnUiThread(() -> {
            if (playerWaitingDialog != null) playerWaitingDialog.handleWatchChange(watchCount);
        });
    }

    @Override
    public void onJoinGame(int lflist, int rule, int mode, int duelRule,
                           int noCheckDeck, int noShuffleDeck,
                           int startLp, int startHand, int drawCount, int timeLimit) {
        runOnUiThread(() -> {
            if (playerWaitingDialog != null)
                playerWaitingDialog.handleJoinGame(lflist, rule, mode, duelRule,
                        noCheckDeck, noShuffleDeck, startLp, startHand, drawCount, timeLimit);
        });
    }

    @Override
    public void onTypeChange(int type) {
        runOnUiThread(() -> {
            if (playerWaitingDialog != null) {
                boolean isTag = engine.getGameMode() == 2;
                playerWaitingDialog.handleTypeChange(type, isTag);
            }
        });
    }

    @Override
    public void onDeckError(int errorType, int cardCode) {
        runOnUiThread(() -> {
            if (playerWaitingDialog != null)
                playerWaitingDialog.handleDeckError(errorType, cardCode);
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
        EditText etChatInput = findViewById(R.id.et_chat_input);

        // 初始化聊天输入框 UI 管理器
        chatInputUI = new ChatInputUI(this, null);
        chatInputUI.bindChatInput(etChatInput);

        // 设置聊天消息监听器
        chatInputUI.setOnChatMessageListener(message -> {
            if (engine != null && engine.getClient() != null) {
                engine.sendChat(message);
            }
        });

        // 聊天输入框初始可见性跟随停用聊天设置（对齐 gframe wChat：停用聊天时隐藏）
        if (etChatInput != null
                && AppsSettings.get().getIntSettings("chkDisableChatting", 0) == 1) {
            etChatInput.setVisibility(View.GONE);
        }

        cardDetailPanel = new CardDetailPanel(this);
        cardDetailPanel.bindViews();
        topInfoManager = new GameTopInfoManager(this, mainHandler);
        topInfoManager.initViews();
        fieldCtl = new GameFieldController(this, mainHandler, topInfoManager);
        fieldCtl.create();

        setWindowBackground(Constants.CORE_SKIN_PATH + "/" + Constants.CORE_SKIN_BG_MENU);
    }

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

    /**
     * 决斗速度开关（对齐 gframe imgQuickAnimation 点击切换 quick_animation 并保存）
     */
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
                PlayerWaitingDialog.showPlayerWaitingForDirectJoin(this, options);
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
            PlayerWaitingDialog.showPlayerWaitingForDirectJoin(this, null);
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
    }

    public LanModeDialog getLanModeDialog() {
        return lanModeDialog;
    }

    public void setPlayerWaitingDialog(PlayerWaitingDialog dialog) {
        playerWaitingDialog = dialog;
        if (playerWaitingDialog != null) {
            playerWaitingDialog.setCardNameResolver(this::getCardDisplayName);
        }
    }

    public PlayerWaitingDialog getPlayerWaitingDialog() {
        return playerWaitingDialog;
    }

    public SoundManager getSoundManager() {
        return soundManager;
    }

    public void hideGameUI() {
        fieldCtl.hide();
        cardDetailPanel.onGameUIHidden();
        // 退出对战（layout_game_right 隐藏）时，一并关闭正在显示的表情面板
        if (emotionDialog != null) emotionDialog.dismiss();
        // 退出对战时关闭正在显示的宣言类对话框（属性/数字/种族），避免残留弹窗遮挡返回界面
        if (dialogUtil != null) dialogUtil.dismissAnnounceDialogs();
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
        // 决斗开始：从 player waiting 大厅聊天切回决斗显示
        //（恢复决斗场渲染，聊天改回玩家分侧 + 系统/观战弹幕逻辑）
        exitLobbyChatUI();
        showGameUI();
        dismissAllLanDialogs();
        isGameStarted = true;
    }

    /**
     * 决斗开始/彻底离开局域网流程时，关闭三个局域网对话框（先清空 dismiss 回调避免误恢复主菜单）
     */
    private void dismissAllLanDialogs() {
        if (lanModeDialog != null) {
            lanModeDialog.setOnDismissListener(null);
            lanModeDialog.dismiss();
        }
        if (createHostDialog != null) {
            createHostDialog.setOnDismissListener(null);
            createHostDialog.dismiss();
        }
        if (playerWaitingDialog != null) {
            playerWaitingDialog.setOnDismissListener(null);
            playerWaitingDialog.dismiss();
        }
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

        // 关闭玩家等待/建主界面，回到局域网主界面
        if (playerWaitingDialog != null) {
            playerWaitingDialog.setOnDismissListener(null);
            playerWaitingDialog.dismiss();
            playerWaitingDialog = null;
        }
        if (createHostDialog != null) {
            createHostDialog.setOnDismissListener(null);
            createHostDialog.dismiss();
            createHostDialog = null;
        }

        if (TextUtils.isEmpty(lastJoinHost)) {
            getMainMenuDialog().restoreMainMenu();
        } else {
            LanModeDialog.showLanModeDialog(this);
            if (lanModeDialog != null) {
                lanModeDialog.preFillConnectionFields(lastJoinNickname, lastJoinHost,
                        String.valueOf(lastJoinPort), lastJoinRoomName);
            }
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
    public void onCreateHostRequested(String nickname) {
        if (lanModeDialog != null) lanModeDialog.hideForNavigation();
        showCreateHost(nickname);
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

        if (createHostDialog != null) createHostDialog.hideForNavigation();
        showPlayerWaiting(userName, modeIdx == 2);
    }

    @Override
    public void onCancelCreate() {
        if (createHostDialog != null) createHostDialog.hideForNavigation();
        LanModeDialog.showLanModeDialog(this);
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

        if (lanModeDialog != null) lanModeDialog.hideForNavigation();
        showPlayerWaiting(userName, false);
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
    public void onExitWaiting() {
        if (engine != null) engine.disconnect();
        if (playerWaitingDialog != null) playerWaitingDialog.hideForNavigation();
        LanModeDialog.showLanModeDialog(this);
        if (lanModeDialog != null) {
            lanModeDialog.preFillConnectionFields(lastJoinNickname, lastJoinHost,
                    String.valueOf(lastJoinPort), lastJoinRoomName);
        }
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

    @Override
    public void onPlayerWaitingShown() {
        runOnUiThread(this::enterLobbyChatUI);
    }

    /**
     * player waiting 大厅聊天界面：显示聊天输入框与 layout_danmaku 聊天列表；
     * gameTopInfo（layout_top_info）的子布局与决斗场渲染在此期间不显示
     */
    private void enterLobbyChatUI() {
        if (layoutGameContent != null) layoutGameContent.setVisibility(View.VISIBLE);
        if (layoutGameRight != null) layoutGameRight.setVisibility(View.VISIBLE);
        // 大厅聊天期间隐藏决斗场 GL 渲染（GLSurfaceView 置 GONE，决斗开始时恢复）
        View gameFieldView = findViewById(R.id.game_field_view);
        if (gameFieldView != null) gameFieldView.setVisibility(View.GONE);
        if (topInfoManager != null) topInfoManager.hide();
        fieldCtl.enterLobbyChatMode();

        // 使用 ChatInputUI 进入大厅聊天模式
        if (chatInputUI != null) {
            chatInputUI.enterLobbyChatUI();
        }

        if (dialogContainer != null) dialogContainer.setVisibility(View.VISIBLE);
    }

    /**
     * 从大厅聊天切回决斗显示：恢复决斗场渲染并退出大厅聊天列表模式
     */
    private void exitLobbyChatUI() {
        View gameFieldView = findViewById(R.id.game_field_view);
        if (gameFieldView != null) gameFieldView.setVisibility(View.VISIBLE);
        if (fieldCtl != null) fieldCtl.exitLobbyChatMode();

        // 使用 ChatInputUI 退出大厅聊天模式
        if (chatInputUI != null) {
            chatInputUI.exitLobbyChatUI();
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
                // 已通过 PlayerWaitingDialog 显示玩家等待界面时无需处理；否则隐藏主菜单
                if (playerWaitingDialog == null || !playerWaitingDialog.isShowing()) {
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
                // 对齐 duelclient.cpp L912-916：STOC_GAME_START 按 chkDefaultShowChain 初始化时点三态
                cardDetailPanel.onDuelStarted();
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
            isMyTurn = (engine.getField().currentPlayer == 0);
            topInfoManager.updateTurn(engine.getField().turnCount, isMyTurn);
            fieldCtl.updateActionButtonsForPhase(phase, isMyTurn);
            // case 101：阶段文字跟随通讯切换（DuelPhase → showcardcode 4~9）
            int textCode = phaseTextCode(phase);
            if (textCode > 0) specEffect().showText(textCode);
        });
    }

    /**
     * MSG_NEW_TURN（对齐 duelclient.cpp L2865-2877）：
     * 回合方切换的第一时间同步 LPBarFrame 彩色/灰色（drawing.cpp L996-1003），
     * 并显示左侧面板的三个时点按钮、刷新其按下态
     */
    @Override
    public void onTurnStarted(int player) {
        runOnUiThread(() -> {
            // player 为本地视角索引（GameEngine.onNewTurn 已做 localPlayer 转换）：0=我方回合
            isMyTurn = (player == 0);
            topInfoManager.updateTurn(engine.getField().turnCount, isMyTurn);
            if (cardDetailPanel != null) cardDetailPanel.showChainButtons();
        });
    }

    @Override
    public void onChatReceived(int playerType, String message) {
        runOnUiThread(() -> fieldCtl.appendChat(playerType, message));
    }

    /**
     * 聊天开关（对齐 gframe event_handler.cpp BUTTON_CHATTING）：
     * 停用状态（chkIgnore1=1）→ 启用：图标 tTalk、显示聊天输入框；
     * 启用状态 → 停用：图标 tShut、隐藏聊天输入框并清空聊天消息
     */
    public void toggleChatInput() {
        if (chatInputUI != null) {
            chatInputUI.toggleChatInput(!chatInputUI.isChatEnabled(), () -> {
                if (fieldCtl != null) {
                    fieldCtl.clearChatMessages();
                }
            });
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
                    showDialogUtil.showAnnounceRaceDialog(data);
                    break;
                case 141:
                    showDialogUtil.showAnnounceAttribDialog(data);
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
            int code = winner == 2 ? SpecEffectOverlay.TEXT_DRAW_GAME
                    : (engine.isSelfSide(winner) ? SpecEffectOverlay.TEXT_YOU_WIN
                                                 : SpecEffectOverlay.TEXT_YOU_LOSE);
            // case 101：胜负文字 + 胜利原因（对齐 duelclient.cpp MSG_WIN，reason<0x10 时前缀胜者名）
            String winnerName = (winner == 2) ? null
                    : playerDisplayName(engine.isSelfSide(winner) ? 0 : 1);
            specEffect().showWinText(code, reason, winnerName);

        });
    }

    /**
     * 取本地视角玩家（0=我方，1=对方）的显示名，复用 onPlayerInfoUpdated 的座位映射逻辑，
     * 用于 MSG_WIN 胜利说明的 "[胜者名] 原因" 前缀（对齐 duelclient.cpp L1586-1599）
     */
    private String playerDisplayName(int localIndex) {
        int selfSeat = engine.getClient().selfType;
        int seat = (localIndex == 0) ? selfSeat : (selfSeat ^ 1);
        GameEngine.PlayerInfo info = (seat >= 0 && seat < engine.playerInfos.length)
                ? engine.playerInfos[seat] : null;
        String defaultName = (localIndex == 0) ? Constants.PlayerName : "Opponent";
        return (info == null || info.name.isEmpty()) ? defaultName : info.name;
    }

    @Override
    public void onHintMessage(String hint) {
        runOnUiThread(() -> fieldCtl.showHint(hint, 2000));
    }

    @Override
    public void onDuelHint(String hint) {
        runOnUiThread(() -> fieldCtl.showDuelHint(hint));
    }

    @Override
    public void onDuelHintHide() {
        runOnUiThread(() -> fieldCtl.hideDuelHint());
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
        runOnUiThread(() -> {
            fieldCtl.selectCardWithAutoClear(controler, location, sequence, 1500);
            specEffect().showActivate(code);          // case 1：发动卡片大图
        });
    }

    @Override
    public void onSummonAnimation(int code, int summonType) {
        runOnUiThread(() -> {
            if (summonType == GameEngine.SUMMON_SPECIAL) {
                specEffect().showSpecialSummon(code); // case 5：特殊召唤，放大 + 淡入
            } else {
                specEffect().showSummon(code);        // case 7：通常/反转召唤，翻面进入
            }
        });
    }

    @Override
    public void onNegatedAnimation(int code) {
        // case 3：效果无效（破坏被无效即"不会被破坏"），居中卡片 + 无效图标
        runOnUiThread(() -> specEffect().showNegated(code));
    }

    @Override
    public boolean isSpecEffectBusy() {
        // 统一动画屏障的特效侧查询：直接读字段（不用 specEffect() 以免按需创建），
        // 覆盖层未创建即视为空闲，供 GameEngine 判断居中特效是否仍在播放
        return specEffectOverlay != null && specEffectOverlay.isBusy();
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
     * 回放结束：用阶段文字（case 101）显示胜负 + 胜利原因，替代原来的 showResultDialog 弹窗。
     * 回放为观战视角，约定 player 0 胜=YOU WIN、player 1 胜=YOU LOSE、player 2=平局；
     * winner<0 表示回放自然播放完毕（无 MSG_WIN 判定），不显示胜负文字。
     */
    public void showReplayResult(int winner, int reason, String winnerName) {
        if (winner < 0) return;
        int code = winner == 2 ? SpecEffectOverlay.TEXT_DRAW_GAME
                : (winner == 0 ? SpecEffectOverlay.TEXT_YOU_WIN
                   : SpecEffectOverlay.TEXT_YOU_LOSE);
        specEffect().showWinText(code, reason, winnerName);
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
        // 释放局域网三对话框，避免持有已销毁的窗口/上下文
        dismissAllLanDialogs();
        lanModeDialog = null;
        createHostDialog = null;
        playerWaitingDialog = null;
        // 关闭主菜单弹窗：其 PopupWindow 可能因 restoreMainMenu 恢复后仍未 dismiss，
        // Activity 销毁时会触发 android.view.WindowLeaked
        if (mainMenuDialog != null) {
            mainMenuDialog.dismiss();
            mainMenuDialog = null;
        }
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

    /**
     * 决斗日志面板（对齐桌面版 imgLog 开关 wLogs）：由卡片详情面板侧栏按钮触发
     */
    public void showDuelLogDialog() {
        if (duelLogDialog == null) {
            duelLogDialog = new DuelLogDialog(this);
        }
        duelLogDialog.toggle();
    }

    // === 局域网三对话框导航 ===

    private void showCreateHost(String nickname) {
        if (createHostDialog == null) {
            createHostDialog = new CreateHostDialog(this, this);
            // 点击外部/返回键意外关闭建主界面时回到局域网主界面
            createHostDialog.setOnDismissListener(() -> LanModeDialog.showLanModeDialog(this));
        }
        createHostDialog.setNickname(nickname);
        if (createHostDialog.canReshow()) {
            createHostDialog.reshow(dialogContainer);
        } else if (!createHostDialog.isShowing()) {
            createHostDialog.show(dialogContainer);
        }
    }

    private void showPlayerWaiting(String nickname, boolean tagMode) {
        if (playerWaitingDialog != null) {
            playerWaitingDialog.hideForNavigation();
        }
        PlayerWaitingDialog dialog = new PlayerWaitingDialog(this, this);
        setPlayerWaitingDialog(dialog);
        dialog.setOnDismissListener(() -> getMainMenuDialog().restoreMainMenu());
        dialog.show(dialogContainer);
        String name = (nickname != null && !nickname.isEmpty()) ? nickname : Constants.PlayerName;
        dialog.setPlayerName(0, name);
        dialog.setTagPlayersVisible(tagMode);
    }

    private SpecEffectOverlay specEffectOverlay;

    private SpecEffectOverlay specEffect() {
        if (specEffectOverlay == null) {
            specEffectOverlay = new SpecEffectOverlay(this);
            // 特效队列排空 → 通知引擎重开消息闸门，实现「召唤/发动动画播完后再弹询问框」的串行序列
            specEffectOverlay.setOnIdleListener(() -> {
                if (engine != null) engine.notifySpecEffectIdle();
            });
        }
        return specEffectOverlay;
    }

    /**
     * MSG_NEW_PHASE 的 phase 値 → DrawSpec case 101 的 showcardcode（对齐 duelclient.cpp L2905-2929）：
     * Draw→4, Standby→5, Main1→6, BattleStart→7, Main2→8, End→9；
     * 战斗子阶段等无独立提示文字的相位返回 0（不显示阶段文字）
     */
    private int phaseTextCode(int phase) {
        DuelPhase dp = DuelPhase.valueOf(phase);
        if (dp == null) return 0;
        switch (dp) {
            case Draw:
                return SpecEffectOverlay.TEXT_DRAW_PHASE;
            case Standby:
                return SpecEffectOverlay.TEXT_STANDBY_PHASE;
            case Main1:
                return SpecEffectOverlay.TEXT_MAIN_PHASE_1;
            case BattleStart:
                return SpecEffectOverlay.TEXT_BATTLE_PHASE;
            case Main2:
                return SpecEffectOverlay.TEXT_MAIN_PHASE_2;
            case End:
                return SpecEffectOverlay.TEXT_END_PHASE;
            default:
                return 0;
        }
    }
}
