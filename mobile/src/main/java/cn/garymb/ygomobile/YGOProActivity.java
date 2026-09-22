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
import android.widget.TextView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import cn.garymb.ygodata.YGOGameOptions;
import cn.garymb.ygomobile.audio.SoundManager;
import cn.garymb.ygomobile.engine.NativeScriptBootstrap;
import cn.garymb.ygomobile.game.ChatInputUI;
import cn.garymb.ygomobile.game.DeckEditorManager;
import cn.garymb.ygomobile.game.GameEngine;
import cn.garymb.ygomobile.game.GameField;
import cn.garymb.ygomobile.game.GameFieldController;
import cn.garymb.ygomobile.game.GameTopInfoManager;
import cn.garymb.ygomobile.game.ReplayEngine;
import cn.garymb.ygomobile.game.ShowDialogUtil;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.render.CardDetailPanel;
import cn.garymb.ygomobile.render.GameFieldView;
import cn.garymb.ygomobile.render.TextureLoader;
import cn.garymb.ygomobile.ui.dialogs.CreateHostDialog;
import cn.garymb.ygomobile.ui.dialogs.DuelLogDialog;
import cn.garymb.ygomobile.ui.dialogs.EmotionDialog;
import cn.garymb.ygomobile.ui.dialogs.LanModeDialog;
import cn.garymb.ygomobile.ui.dialogs.MainMenuDialog;
import cn.garymb.ygomobile.ui.dialogs.PlayerWaitingDialog;
import cn.garymb.ygomobile.ui.dialogs.ReplayModeDialog;
import cn.garymb.ygomobile.ui.dialogs.SettingsDialog;
import cn.garymb.ygomobile.ui.dialogs.SingleModeDialog;
import cn.garymb.ygomobile.ui.dialogs.YesOrNoDialog;
import cn.garymb.ygomobile.utils.DraggablePopupHelper;
import cn.garymb.ygomobile.utils.FullScreenUtils;
import ocgcore.DataManager;
import ocgcore.StringManager;
import ocgcore.data.Card;

/**
 * 决斗主界面门面：保留 Activity 生命周期、视图装配、UI 编排与全部对外公共 API，
 * 按 // === 分栏把 GameEngine.EngineListener 回调下沉到 {@link EngineCallbackDelegate}、
 * 三个对话框监听接口下沉到 {@link MainMenuNavigator}（同包，包级私有直连本类共享状态）。
 */
public class YGOProActivity extends AppCompatActivity {

    private static final String TAG = "YGONativeGame";

    /**
     * 公共字符串管理器：初始化后可供整个类调用（对齐 CardDetailPanel.mStringManager 惯例）
     */
    public final StringManager mStringManager = DataManager.get().getStringManager();

    // 以下共享字段被同包协作类（EngineCallbackDelegate / MainMenuNavigator）经包级私有直连访问
    GameEngine engine;
    private SoundManager soundManager;
    private ImageLoader imageLoader;
    final Handler mainHandler = new Handler(Looper.getMainLooper());

    private EngineCallbackDelegate engineCallback;
    private MainMenuNavigator menuNav;

    DeckEditorManager deckEditorManager;
    private View layoutDeckEditor;

    private LinearLayout layoutDeckControl;
    FrameLayout layoutGameRight;
    private View layoutGameContent;

    FrameLayout dialogContainer;
    private MainMenuDialog mainMenuDialog;
    LanModeDialog lanModeDialog;
    CreateHostDialog createHostDialog;
    PlayerWaitingDialog playerWaitingDialog;

    private EditText etChatInput;
    private EmotionDialog emotionDialog;
    private DuelLogDialog duelLogDialog;

    volatile boolean isGameStarted = false;

    // 场景 BGM 胜负覆盖（对齐 game.cpp Game::playBGM 的 dInfo.isFinished && showcardcode 判定）：
    // 决斗场显示时若已判定胜负则优先播放 WIN/LOSE，否则按 LP 差判定 ADVANTAGE/DISADVANTAGE/DUEL
    private static final int BGM_RESULT_NONE = 0;
    private static final int BGM_RESULT_WIN = 1;
    private static final int BGM_RESULT_LOSE = 2;
    private int bgmDuelResult = BGM_RESULT_NONE;
    /** 决斗中双方 LP 差达到该阈值时切换优势/劣势 BGM（对齐需求「LP 相差大于等于 4000」） */
    private static final int BGM_LP_DIFF_THRESHOLD = 4000;

    private ReplayEngine currentReplayEngine;
    CardDetailPanel cardDetailPanel;
    private ChatInputUI chatInputUI;
    GameTopInfoManager topInfoManager;
    GameFieldController fieldCtl;
    ShowDialogUtil dialogUtil;
    private boolean exitOnReturn = true;
    private int directEnterMode = 0; // 0=normal, 1=replay dialog, 2=single dialog
    private FullScreenUtils mFullScreenUtils;
    private String currentBgPath;

    // 最近一次加入/创建房间的连接信息：断线或决斗结束返回局域网主界面时回显
    String lastJoinNickname = "";
    String lastJoinHost = "";
    int lastJoinPort = 0;
    String lastJoinRoomName = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        setupFullScreen();
        setContentView(R.layout.activity_ygo_game);

        initViews();
        initEngine();
        loadData();
        startWindbotListener();
        warmUpDuelEngine();
        setupBackPressedHandler();

        if (!handleDirectIntent(getIntent())) {
            getMainMenuDialog().showMainMenu();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        // singleTop：已在前台时从外部再次打开 .yrp（GameUriManager → YGOStarter 带 -r 转发）
        // 不会再走 onCreate，必须在此重新派发，否则回放参数被丢弃、停留在主菜单界面
        handleDirectIntent(intent);
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

        // 左上角 FPS 实时显示——GameFieldView 每秒回调帧率到主线程刷新 tv_fps
        final TextView tvFps = findViewById(R.id.tv_fps);
        if (tvFps != null) {
            View gv = findViewById(R.id.game_field_view);
            if (gv instanceof GameFieldView) {
                ((GameFieldView) gv).setOnFpsListener(fps -> tvFps.setText("FPS " + fps));
            }
        }

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
        soundManager.setMusicMode(appsSettings.getIntSettings("chkSwitchBGM", 0) == 1);

        imageLoader = new ImageLoader(true);

        // 引擎回调与对话框监听分别下沉到同包协作类，经包级私有直连本门面的共享状态/UI 编排
        engineCallback = new EngineCallbackDelegate(this);
        menuNav = new MainMenuNavigator(this);

        engine = new GameEngine(soundManager);
        engine.setListener(engineCallback);
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

    /**
     * 初始化即后台启动 windbot：提前完成 WindBot.initAndroid 与 RUN_WINDBOT 监听注册，
     * 使人机对战不必等到进入 PlayerWaitingDialog 才初始化（原 ResCheckTask 路径在
     * isOnlyGame 直达决斗界面时会被跳过），节约启动等待时间。
     * initAndroid 在 WindBotService 内做了进程级去重：MainActivity 已初始化过时
     * 此处仅确保监听已注册，不会重复进入 Mono 运行时（重复 init 会导致 libmonosgen 崩溃）
     */
    private void startWindbotListener() {
        new Thread(() -> {
            WindBotService.startListening(getApplicationContext());
            Log.i(TAG, "WindBot listener ready");
        }, "WindBotInit").start();
    }

    /**
     * 后台预热 native 决斗引擎：提前完成 scripts.zip 解压与 cards.cdb/脚本加载
     * （{@code OcgDuelEngine.init} 的首次耗时数秒一次性开销），使首次建立局域网主机
     * 时无需再等待引擎引导，握手即时完成。ensureEngineReady 幂等且同步，与建主线程
     * 并发时后者会阻塞至预热完成，不会重复加载。
     */
    private void warmUpDuelEngine() {
        new Thread(() -> {
            boolean ready = NativeScriptBootstrap.ensureEngineReady();
            Log.i(TAG, "Duel engine warm-up " + (ready ? "done" : "skipped/failed"));
        }, "EngineWarmUp").start();
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
        // 切换后立即应用新速度（对齐 event_handler.cpp BUTTON_QUICK_ANIMIATION 同步设置生效）
        applyAnimationSpeed();
    }

    // === 动画速度（对齐 gframe gameConf.quick_animation：WaitFrameSignal 截半、appear 12/20，≈ 2 倍速） ===

    /** 基础动画速度倍率（quick_animation 关闭） */
    private static final float ANIM_SPEED_NORMAL = 1f;
    /** 加速动画速度倍率（quick_animation 开启，C++ 等待帧数截半的等价实现） */
    private static final float ANIM_SPEED_QUICK = 2f;

    /**
     * 按 chkQuickAnimation 当前值随时调节动画速度：场上卡片移动/淡入淡出
     * （GameFieldController→GameFieldView）与居中特效（SpecEffectOverlay）两套动画同步，
     * 设置对话框 checkbox、详情面板按钮与启动时 applySettingsToEngine 均经此入口生效
     */
    private void applyAnimationSpeed() {
        boolean quick = AppsSettings.get().getIntSettings("chkQuickAnimation", 0) == 1;
        float speed = quick ? ANIM_SPEED_QUICK : ANIM_SPEED_NORMAL;
        if (fieldCtl != null) fieldCtl.setAnimationSpeed(speed);
        if (engineCallback != null) engineCallback.setAnimationSpeed(speed);
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

    /** 模态对话框（是/否、卡片选择/确认、命令菜单）显示——禁用决斗场三个阶段按钮 */
    public void notifyGameDialogShown(Object dialog) {
        if (fieldCtl != null) fieldCtl.onModalDialogShown(dialog);
    }

    /** 模态对话框隐藏——恢复决斗场三个阶段按钮 */
    public void notifyGameDialogHidden(Object dialog) {
        if (fieldCtl != null) fieldCtl.onModalDialogHidden(dialog);
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
    public ImageLoader getImageLoader() {
        return imageLoader;
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

    /** 供 LanModeDialog 静态入口构造时接线：返回承载 OnLanModeListener 的协作实例 */
    public LanModeDialog.OnLanModeListener getDialogNavListener() {
        return menuNav;
    }

    /** 供 PlayerWaitingDialog 静态入口构造时接线：返回承载 OnPlayerWaitingListener 的协作实例 */
    public PlayerWaitingDialog.OnPlayerWaitingListener getPlayerWaitingListener() {
        return menuNav;
    }

    /**
     * 集中决策当前场景 BGM 并交 SoundManager 播放（对齐 game.cpp Game::playBGM）：
     * 依据布局可见性与对局状态计算场景，同场景由 SoundManager 内部去重不重复切歌。
     * - 决斗场 layout_game_right 显示：胜负已判定 → WIN/LOSE；否则 LP 差≥阈值时
     *   对方血多 → DISADVANTAGE、我方血多 → ADVANTAGE，其余 → DUEL
     * - 卡组编辑器 layout_deck_editor 显示（含副卡组替换）→ DECK
     * - 其他 → MENU
     * 必须在主线程调用。
     */
    public void updateBGM() {
        if (soundManager == null) return;
        SoundManager.BGM scene;
        boolean gameRightShowing = layoutGameRight != null
                && layoutGameRight.getVisibility() == View.VISIBLE;
        boolean deckEditorShowing = layoutDeckEditor != null
                && layoutDeckEditor.getVisibility() == View.VISIBLE;
        if (gameRightShowing) {
            if (bgmDuelResult == BGM_RESULT_WIN) {
                scene = SoundManager.BGM.WIN;
            } else if (bgmDuelResult == BGM_RESULT_LOSE) {
                scene = SoundManager.BGM.LOSE;
            } else {
                int myLp = engine != null ? engine.field.players[0].lp : 0;
                int oppLp = engine != null ? engine.field.players[1].lp : 0;
                if (Math.abs(myLp - oppLp) >= BGM_LP_DIFF_THRESHOLD) {
                    scene = oppLp > myLp ? SoundManager.BGM.DISADVANTAGE
                            : SoundManager.BGM.ADVANTAGE;
                } else {
                    scene = SoundManager.BGM.DUEL;
                }
            }
        } else if (deckEditorShowing) {
            scene = SoundManager.BGM.DECK;
        } else {
            scene = SoundManager.BGM.MENU;
        }
        soundManager.playBGM(scene);
    }

    /**
     * 决斗判定胜负时设置 BGM 胜负覆盖并刷新场景
     *（对齐 Game::playBGM 的 dInfo.isFinished && showcardcode==1/2/3 分支）
     */
    public void setBgmDuelResult(boolean selfWon) {
        bgmDuelResult = selfWon ? BGM_RESULT_WIN : BGM_RESULT_LOSE;
        updateBGM();
    }

    public void hideGameUI() {
        // 决斗场隐藏即退出本局胜负语境，清空 BGM 胜负覆盖（对齐 dInfo.isFinished 复位）
        bgmDuelResult = BGM_RESULT_NONE;
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
        // 新开一局/回放：清除上一局残留的 BGM 胜负覆盖
        bgmDuelResult = BGM_RESULT_NONE;
        setWindowBackground(Constants.CORE_SKIN_PATH + "/" + Constants.CORE_SKIN_BG);
        getMainMenuDialog().hideMainMenu();
        if (layoutGameContent != null) layoutGameContent.setVisibility(View.VISIBLE);
        if (layoutGameRight != null) layoutGameRight.setVisibility(View.VISIBLE);

        // layout_game_right 显示第一时间初始化顶部信息：头像/玩家名称/房间血量（猜拳前可见）
        if (topInfoManager != null) topInfoManager.prepareForDisplay();

        fieldCtl.show();
        cardDetailPanel.onGameUIShown();
        if (dialogContainer != null) dialogContainer.setVisibility(View.VISIBLE);
        updateBGM();
    }

    void enterDuelingUI() {
        getMainMenuDialog().hideMainMenu();
        // 决斗开始：从 player waiting 大厅聊天切回决斗显示
        //（恢复决斗场渲染，聊天改回玩家分侧 + 系统/观战弹幕逻辑）
        exitLobbyChatUI();
        showGameUI();
        dismissAllLanDialogs();
        isGameStarted = true;
    }

    /**
     * 录像回放开始：切换到决斗场 UI（对齐 game.cpp Main::Replay 显示 GameUI 窗口），
     * 与 enterDuelingUI 相同的布局切换但不置 isGameStarted（回放无投降/断线流程）
     */
    public void enterReplayUI() {
        getMainMenuDialog().hideMainMenu();
        exitLobbyChatUI();
        showGameUI();
        dismissAllLanDialogs();
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
    void returnToLanMain(String toastMsg) {
        if (isFinishing() || isDestroyed()) return;
        isGameStarted = false;
        if (engineCallback != null) engineCallback.resetDuelEndState();
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
                // 房间密码不自动回显，由玩家自行输入
                lanModeDialog.preFillConnectionFields(lastJoinNickname, lastJoinHost,
                        String.valueOf(lastJoinPort));
            }
            // 返回局域网主界面属“其他情况”，切 MENU 场景
            updateBGM();
        }
        if (toastMsg != null && !toastMsg.isEmpty()) {
            Toast.makeText(this, toastMsg, Toast.LENGTH_SHORT).show();
        }
    }

    void saveLastConnectionInfo(String nickname, String host, int port, String roomName) {
        lastJoinNickname = nickname != null ? nickname : "";
        lastJoinHost = host != null ? host : "";
        lastJoinPort = port;
        lastJoinRoomName = roomName != null ? roomName : "";
    }

    /**
     * 显式退出玩家等待界面时由 MainMenuNavigator.onExitWaiting 在 disconnect 前调用：
     * 抑制本次 DISCONNECTED 自动 returnToLanMain（否则会连带弹出 LanModeDialog/MainMenuDialog），
     * 返回导航（bot→SingleModeDialog / LAN→LanModeDialog）由退出入口独占
     */
    void suppressNextDisconnectedReturn() {
        if (engineCallback != null) engineCallback.suppressNextDisconnectedReturn();
    }

    /**
     * player waiting 大厅聊天界面：显示聊天输入框与 layout_danmaku 聊天列表；
     * gameTopInfo（layout_top_info）的子布局与决斗场渲染在此期间不显示
     */
    void enterLobbyChatUI() {
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
        // 卡组编辑器（含副卡组替换）布局已显示：切换 DECK 场景（对齐 Game::playBGM 的 is_building 分支）
        updateBGM();
    }

    private void hideDeckEditorView() {
        if (layoutDeckEditor != null) {
            layoutDeckEditor.setVisibility(View.GONE);
        }
        if (layoutDeckControl != null) layoutDeckControl.setVisibility(View.GONE);
        cardDetailPanel.exitDeckEditorMode();
        // 卡组编辑器隐藏后重算场景（无其他布局显示 → MENU）
        updateBGM();
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
            soundManager.setMusicMode(appsSettings.getIntSettings("chkSwitchBGM", 0) == 1);
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
        // 动画速度随 chkQuickAnimation 即时生效（启动初始化与设置对话框变更均经此）
        applyAnimationSpeed();
        if (deckEditorManager != null) {
            deckEditorManager.refreshLimitList();
        }
    }

    // === 决斗场内联交互（保留在门面：由卡片详情面板/聊天 UI 等直接调用的公共入口） ===

    /**
     * 聊天开关（对齐 gframe event_handler.cpp BUTTON_CHATTING）：
     * 停用状态（chkIgnore1=1）→ 启用：图标 tTalk、显示聊天输入框；
     * 启用状态 → 停用：图标 tShut、隐藏聊天输入框并清空聊天消息
     */
    public void toggleChatInput() {
        if (chatInputUI != null) {
            boolean enable = !chatInputUI.isChatEnabled();
            chatInputUI.toggleChatInput(enable, () -> {
                if (fieldCtl != null) {
                    fieldCtl.clearChatMessages();
                }
            });
            // 点击切换后立即同步图标（对齐 event_handler.cpp BUTTON_CHATTING）：
            // 停用聊天显示 tShut、启用显示 tTalk，不再依赖设置对话框路径的 applySettingsToEngine
            if (cardDetailPanel != null) {
                cardDetailPanel.updateChatIcon(!enable);
            }
        }
    }

    /**
     * 投降入口（对齐 event_handler.cpp BUTTON_LEAVE_GAME → wSurrender(1359) 确认 → CTOS_SURRENDER）：
     * 先弹 YesOrNoDialog 二次确认，确认后才发送投降通讯；
     * tag 模式下若本方已发起投降、正在等待队友回应，则忽略重复点击
     */
    public void requestSurrender() {
        if (engine == null || !engine.isInDuel()) return;
        if (engine.isTagMode() && engine.isSurrenderPending()) return;
        YesOrNoDialog dialog = new YesOrNoDialog(this);
        dialog.setTitle(mStringManager.getSystemString(1351, "投降"))
                .setMessage(mStringManager.getSystemString(1359, "是否确定投降？"))
                .setType(YesOrNoDialog.TYPE_YES_NO)
                .setPositiveButtonText(mStringManager.getSystemString(1213, "是"))
                .setNegativeButtonText(mStringManager.getSystemString(1214, "否"))
                .setPositiveButton(v -> {
                    if (engine != null) engine.sendSurrender();
                })
                .setCenterInView(layoutGameRight)
                .setCancelable(false);
        dialog.show();
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
     * 回放结束：用阶段文字（case 101）显示胜负 + 胜利原因，委托 EngineCallbackDelegate 的居中特效层。
     */
    public void showReplayResult(int winner, int reason, String winnerName) {
        if (engineCallback != null) engineCallback.showReplayResult(winner, reason, winnerName);
    }

    /** 录像回放召唤动画：委托 EngineCallbackDelegate 的 specEffect 居中卡片动画 */
    public void showReplaySummonAnimation(int code, int summonType) {
        if (engineCallback != null) engineCallback.showReplaySummonAnimation(code, summonType);
    }

    /** 录像回放连锁发动动画（MSG 模式）：选卡高亮 + 发动大图 */
    public void showReplayChainAnimation(int code, int controler, int location, int sequence) {
        if (engineCallback != null) engineCallback.showReplayChainAnimation(code, controler, location, sequence);
    }

    /** 录像回放效果无效动画（MSG 模式）：居中卡片 + 无效图标 */
    public void showReplayNegateAnimation(int code) {
        if (engineCallback != null) engineCallback.showReplayNegateAnimation(code);
    }

    /** 录像回放阶段文字提示 */
    public void showReplayPhaseText(int textCode) {
        if (engineCallback != null) engineCallback.showReplayPhaseText(textCode);
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
        if (engineCallback != null) engineCallback.cancelReplayProcessing();
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
                // 回放进行中：返回键 = 退出回放回主菜单（对应 gframe 回放窗口关闭）
                if (currentReplayEngine != null) {
                    ReplayModeDialog.quitReplay(YGOProActivity.this);
                    return;
                }
                // 玩家等待界面显示中：弹窗不再获焦（聊天输入框需窗口焦点），返回键转发为退出等待回局域网主界面
                if (playerWaitingDialog != null && playerWaitingDialog.isShowing()) {
                    getPlayerWaitingListener().onExitWaiting();
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
}
