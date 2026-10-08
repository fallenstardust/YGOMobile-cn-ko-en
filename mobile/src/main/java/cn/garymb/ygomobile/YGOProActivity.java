package cn.garymb.ygomobile;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
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
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import cn.garymb.ygodata.YGOGameOptions;
import cn.garymb.ygomobile.audio.SoundManager;
import cn.garymb.ygomobile.engine.NativeScriptBootstrap;
import cn.garymb.ygomobile.game.ChatInputUI;
import cn.garymb.ygomobile.game.DeckEditorManager;
import cn.garymb.ygomobile.game.GameEngine;
import cn.garymb.ygomobile.game.GameField;
import cn.garymb.ygomobile.game.GameFieldController;
import cn.garymb.ygomobile.game.GameTopInfoManager;
import cn.garymb.ygomobile.game.ReplayPlayer;
import cn.garymb.ygomobile.game.ShowDialogUtil;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.render.CardDetailPanel;
import cn.garymb.ygomobile.render.GameFieldView;
import cn.garymb.ygomobile.render.SpecEffectOverlay;
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
import cn.garymb.ygomobile.utils.CrashHandler;
import cn.garymb.ygomobile.utils.DraggablePopupHelper;
import cn.garymb.ygomobile.utils.FullScreenUtils;
import cn.garymb.ygomobile.utils.RightAlignedTiledDrawable;
import ocgcore.DataManager;
import ocgcore.StringManager;

/**
 * 决斗主界面门面：保留 Activity 生命周期、视图装配、UI 编排与全部对外公共 API，
 * 按 // === 分栏把 GameEngine.EngineListener 回调下沉到 {@link EngineCallbackDelegate}、
 * 三个对话框监听接口下沉到 {@link MainMenuNavigator}、场景 BGM 决策下沉到
 * {@link BgmSceneController}、已保存设置应用下沉到 {@link GameSettingsApplier}、
 * 卡组编辑器视图切换下沉到 {@link DeckEditorViewHost}（均同包，包级私有直连本类共享状态）。
 */
public class YGOProActivity extends AppCompatActivity {

    private static final String TAG = "YGONativeGame";

    /**
     * 公共字符串管理器：初始化后可供整个类调用（对齐 CardDetailPanel.mStringManager 惯例）
     */
    public final StringManager mStringManager = DataManager.get().getStringManager();

    // 以下共享字段被同包协作类（EngineCallbackDelegate / MainMenuNavigator / BgmSceneController /
    // GameSettingsApplier / DeckEditorViewHost）经包级私有直连访问
    GameEngine engine;
    SoundManager soundManager;
    ImageLoader imageLoader;
    final Handler mainHandler = new Handler(Looper.getMainLooper());

    EngineCallbackDelegate engineCallback;
    private MainMenuNavigator menuNav;

    // 自本门面拆出的同包协作件（构造仅需 this，均为被动委托，无初始化顺序依赖）
    final BgmSceneController bgmCtl = new BgmSceneController(this);
    final GameSettingsApplier settingsCtl = new GameSettingsApplier(this);
    final DeckEditorViewHost deckEditorHost = new DeckEditorViewHost(this);

    DeckEditorManager deckEditorManager;
    View layoutDeckEditor;

    LinearLayout layoutDeckControl;
    FrameLayout layoutGameRight;
    /**
     * 竖屏顶部面板（卡片详情+时点/录像/卡组按钮行，占屏高 1/3）；横屏无此节点，全程空保护
     */
    View layoutGameTopPanel;
    View layoutGameContent;

    FrameLayout dialogContainer;
    private MainMenuDialog mainMenuDialog;
    LanModeDialog lanModeDialog;
    CreateHostDialog createHostDialog;
    PlayerWaitingDialog playerWaitingDialog;

    EditText etChatInput;
    private EmotionDialog emotionDialog;
    private DuelLogDialog duelLogDialog;
    /**
     * 当前设置弹窗实例：显示中再次点击设置按钮应隐藏它而非叠开新的（横竖屏同一入口）
     */
    private SettingsDialog settingsDialog;

    volatile boolean isGameStarted = false;

    CardDetailPanel cardDetailPanel;
    private ChatInputUI chatInputUI;
    GameTopInfoManager topInfoManager;
    GameFieldController fieldCtl;
    ShowDialogUtil dialogUtil;
    private boolean exitOnReturn = true;
    private int directEnterMode = 0; // 0=normal, 1=replay dialog, 2=single dialog
    private FullScreenUtils mFullScreenUtils;
    private String currentBgPath;
    /**
     * 最近一次解码成功的背景图（竖屏下另挂到 layout_game_right 区域平铺背景，旋转重建后据此重贴）
     */
    private Bitmap lastBgBitmap;

    // 最近一次加入/创建房间的连接信息：断线或决斗结束返回局域网主界面时回显
    String lastJoinNickname = "";
    String lastJoinHost = "";
    int lastJoinPort = 0;
    String lastJoinRoomName = "";
    // 上一局主机/加入密码：决斗结束或退出玩家等待重新显示 LanModeDialog 时回填房间密码（用户规格，不清空）
    String lastJoinPassword = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 启动方向按“游戏横屏锁定”设置决定（见 applyOrientationLock）：
        // 锁定启用→始终横屏（SENSOR_LANDSCAPE 仍可左右横屏对调旋转，不自动转竖屏）；
        // 未启用→SCREEN_ORIENTATION_USER 跟随系统当前屏幕方向直接以对应方向启动
        applyOrientationLock();
        // 崩溃诊断场景锚点：未捕获异常经全局 CrashHandler 落盘到 ygocore/log
        CrashHandler.getInstance().setScene("游戏-启动初始化");
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
        // 启动主菜单：布局就绪后触发 MENU 场景 BGM（否则启动后无声，直到下一次显式 updateBGM）
        mainHandler.post(this::updateBGM);
    }

    /**
     * 按“游戏横屏锁定”设置应用启动显示方向：
     * 启用时锁定为横屏但保留左右横屏 180° 对调旋转（SENSOR_LANDSCAPE，不会自动转竖屏）；
     * 未启用时取 SCREEN_ORIENTATION_USER，按系统当前屏幕方向（竖/横）直接以对应方向显示，
     * 并随系统自动旋转切换。configChanges 已声明 orientation|screenSize，旋转不重建 Activity。
     */
    private void applyOrientationLock() {
        if (AppsSettings.get().isLockSreenOrientation()) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER);
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
        layoutGameTopPanel = findViewById(R.id.layout_game_top_panel);
        layoutGameContent = findViewById(R.id.layout_game_content);
        if (layoutGameContent != null) layoutGameContent.setVisibility(View.GONE);
        etChatInput = findViewById(R.id.et_chat_input);

        // 初始化聊天输入框 UI 管理器
        chatInputUI = new ChatInputUI(this, null);
        chatInputUI.bindChatInput(etChatInput);

        // 设置聊天消息监听器
        chatInputUI.setOnChatMessageListener(message -> {
            if (engine != null && engine.getClient() != null) {
                engine.sendChat(message);
            }
        });

        cardDetailPanel = new CardDetailPanel(this);
        cardDetailPanel.bindViews();
        // 聊天输入框/聊天开关初始可见性：停用聊天设置与抑制场景（卡组编辑/录像/残局）
        // 统一核算（对齐 gframe wChat：停用聊天与无聊天对象场景均隐藏）
        updateChatUIVisibility();
        topInfoManager = new GameTopInfoManager(this, mainHandler);
        topInfoManager.initViews();
        // 聊天输入框随 gameTopInfo 同一 HUD 系数等比例缩放（平板上固定 dp 输入框极细难点）
        if (chatInputUI != null) chatInputUI.bindHudScale(topInfoManager);
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

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (isFinishing() || isDestroyed()) return;
        rebuildUiForOrientation();
    }

    /**
     * 屏幕旋转后运行时重建 UI（不重建引擎/通讯/场面数据，决斗不断线）：
     * setContentView 按当前方向取 res/layout-port（竖屏：我方血条左下/对方右上/聊天自下而上/
     * 按钮列左上）或默认 res/layout（横屏：左侧 cardDetailPanel + 右侧决斗场），
     * 再把各管理器重新绑定到新视图树并按重建前阶段回显。
     */
    private void rebuildUiForOrientation() {
        // 1) 重建前阶段快照（setContentView 后据此回显）
        boolean deckEditorVisible = layoutDeckEditor != null
                && layoutDeckEditor.getVisibility() == View.VISIBLE;
        // 决斗语境（决斗进行中/副卡组替换/残局/观战/录像回放）：用户规格，竖屏的卡片详情
        // 布局与 layout_game_right 只在录像、决斗、卡组编辑场景回显；大厅聊天（玩家等待）
        // 虽借 layout_game_right 承载聊天区但不属决斗，不得回显卡详面板与决斗场
        GameEngine eng = engine;
        boolean replaySessionActive = eng != null && eng.replayMode
                && eng.replayPlayer != null && eng.replayPlayer.hasActiveSession();
        // 决斗结束弹窗（待点确定返回主界面）期间 engine.disconnect 已令 isStarted/isSpectator 等
        // 全部为假，但「决斗结束」dialog 仍居中于 layout_game_right，旋转不得隐藏它（用户规格：
        // 保持显示到点确定按钮后才隐藏，隐藏由 returnToLanMain 走非决斗分支完成）
        boolean duelEndPending = engineCallback != null && engineCallback.isDuelEndHandling();
        boolean duelContext = eng != null && (eng.isStarted() || eng.isSiding()
                || eng.isSingleMode || eng.isSpectator() || replaySessionActive) || duelEndPending;
        boolean lobbyChat = !deckEditorVisible && !duelContext
                && playerWaitingDialog != null && playerWaitingDialog.isShowing();
        boolean duelUiVisible = !deckEditorVisible && duelContext && !lobbyChat
                && layoutGameRight != null && layoutGameRight.getVisibility() == View.VISIBLE;
        // 卡片详情回显快照（旧视图树）：旋转前正在显示某张卡详情则重建后继续显示同一张（用户规格）；
        // 卡码一并快照：回显前 onGameUIShown() 会 showDefault() 清掉面板内的当前卡码，不能事后取
        boolean detailShowing = cardDetailPanel != null && cardDetailPanel.isShowing();
        int detailCardCode = (detailShowing && cardDetailPanel != null)
                ? cardDetailPanel.getCurrentCardCode() : -1;

        // 2) 关闭挂在旧视图树上的瞬态浮层：表情面板/drawspec 覆盖层（LP浮字/弹幕/居中特效）/
        // 卡片命令菜单；后续特效/弹幕经懒建新实例回到新树
        if (emotionDialog != null) emotionDialog.dismiss();
        if (engineCallback != null) engineCallback.releaseSpecOverlayForRebuild();
        if (fieldCtl != null) fieldCtl.releaseForRebuild();

        // 3) 重新加载布局变体并恢复沉浸式全屏
        setContentView(R.layout.activity_ygo_game);
        setupFullScreen();

        // 4) Activity 共享视图引用重新绑定新树
        dialogContainer = findViewById(R.id.dialog_container);
        layoutDeckControl = findViewById(R.id.layout_deck_control);
        layoutGameRight = findViewById(R.id.layout_game_right);
        layoutGameTopPanel = findViewById(R.id.layout_game_top_panel);
        layoutGameContent = findViewById(R.id.layout_game_content);
        layoutDeckEditor = findViewById(R.id.layout_deck_editor);
        etChatInput = findViewById(R.id.et_chat_input);
        if (chatInputUI != null) chatInputUI.bindChatInput(etChatInput);
        // 聊天 UI 可见性在重建尾部统一重算（须等 layoutDeckEditor 等新引用就绪，见方法尾）
        // 三大管理器复用实例只重绑视图（保留 ignoreChain 时点三态/大厅聊天等业务状态），
        // 严禁触达 fieldCtl.hide()——会 clear 场面数据
        if (cardDetailPanel != null) {
            cardDetailPanel.bindViews();
            cardDetailPanel.setImageLoader(imageLoader);
            cardDetailPanel.bindSideButtonIcons();
        }
        if (topInfoManager != null) topInfoManager.initViews();
        if (fieldCtl != null) fieldCtl.rebindAfterRotation();
        // 旋转后新 GameFieldView 实例的 animSpeedMultiplier 重置为默认 1f，
        // 按当前设置重新应用动画速率（chkQuickAnimation 2x / 正常 1x）
        settingsCtl.applyAnimationSpeed();
        // FPS 回调重接线（tv_fps 与 game_field_view 均为新视图树实例）
        final TextView tvFps = findViewById(R.id.tv_fps);
        if (tvFps != null) {
            View gv = findViewById(R.id.game_field_view);
            if (gv instanceof GameFieldView) {
                ((GameFieldView) gv).setOnFpsListener(fps -> tvFps.setText("FPS " + fps));
            }
        }

        // 5) 按重建前阶段回显
        if (layoutGameContent != null) layoutGameContent.setVisibility(View.GONE);
        if (deckEditorVisible) {
            // 卡组/副卡组编辑器：host.show() 幂等（懒建 manager + initialize 重绑新视图）
            deckEditorHost.show();
        } else if (lobbyChat) {
            enterLobbyChatUI();
        } else if (duelUiVisible) {
            if (layoutGameContent != null) layoutGameContent.setVisibility(View.VISIBLE);
            if (layoutGameRight != null) layoutGameRight.setVisibility(View.VISIBLE);
            setGameTopPanelVisible(true);
            if (dialogContainer != null) dialogContainer.setVisibility(View.VISIBLE);
            if (topInfoManager != null) {
                topInfoManager.prepareForDisplay();
                GameField field = engine != null ? engine.getField() : null;
                if (field != null) {
                    // 回合数/回合方高亮/LP血条/卡数对齐实时场面（initViews 仅重置为默认值）
                    topInfoManager.updateTurn(field.turnCount, field.currentPlayer == 0);
                    topInfoManager.updateLpBars(field);
                    topInfoManager.updateCardCountDisplay(field);
                }
            }
            if (fieldCtl != null) fieldCtl.show();
            if (cardDetailPanel != null) {
                boolean replayActive = engine != null && engine.replayMode
                        && engine.replayPlayer != null && engine.replayPlayer.hasActiveSession();
                // 决斗结束弹窗期间旋转：onGameUIShown 会复位 spectatorMode 并隐藏控制条，
                // 先快照结束前的观战态以便回显观战控制集（用户规格：保持显示到点确定）
                boolean wasSpectatorPanel = cardDetailPanel.isSpectatorMode();
                cardDetailPanel.onGameUIShown(); // 先复位按钮组到默认态再按模式覆盖
                if (replayActive) {
                    cardDetailPanel.showReplayControls();
                    cardDetailPanel.updateReplayButtonStates(engine.replayPlayer.isPaused());
                } else if (duelEndPending) {
                    // 决斗结束弹窗期间旋转：场面保持显示，控制面板沿用结束前状态——观战保留观战
                    // 控制集，对战玩家隐藏底部行动/时点按钮（对齐 DUEL_END 的 closeGameButtons），
                    // 直到点确定返回主界面才整体隐藏
                    if (wasSpectatorPanel) cardDetailPanel.showSpectatorControls();
                    else cardDetailPanel.closeGameButtons();
                } else if (engine != null && engine.isSpectator()) {
                    cardDetailPanel.showSpectatorControls();
                } else if (isGameStarted) {
                    cardDetailPanel.showChainButtons();
                    cardDetailPanel.setSurrenderVisible(true);
                    // 撤回入口在顶部回合数下方（iv_undo）：旋转/重进决斗界面后按当前可撤回
                    // 状态重建图标闪动（initViews 里的 reset 会先停掉动画）
                    if (topInfoManager != null) {
                        topInfoManager.setUndoPrompt(engine != null && engine.isUndoPromptActive());
                    }
                }
                cardDetailPanel.restoreAfterRebind(detailShowing, detailCardCode);
            }
        } else {
            // 其他场景（主菜单/局域网与建主等待弹窗/设置等，用户规格）：竖屏布局变体的
            // layout_game_right 默认 VISIBLE（XML visibility=gone 双重保险），非决斗分支
            // 不得回显卡片详情布局与决斗场区：整棵内容树隐藏；严禁触达 fieldCtl.hide()
            //（旋转仅是视图重建，场面数据必须保留）
            if (layoutGameContent != null) layoutGameContent.setVisibility(View.GONE);
            if (layoutGameRight != null) layoutGameRight.setVisibility(View.GONE);
            setGameTopPanelVisible(false);
        }
        // 聊天输入框/开关在新视图树回显完成后按当前场景重算（卡组编辑分支经 deckEditorHost.show
        // 已算一次，此处覆盖决斗/大厅/菜单各分支，避免旋转后新树默认 VISIBLE 漏显抑制态）
        updateChatUIVisibility();
        // 旋转后新视图树的 layout_game_right 背景随旧树销毁，按最近背景图重贴区域平铺背景
        updateFieldRegionBackground();
        // gameEngine 相关对话框（PopupWindow 独立窗口，不随 setContentView 重建）按新屏宽
        // 重新解算显示宽度并重新居中，避免横转竖宽度超屏文字截断、竖转横显示过小（用户规格）
        DraggablePopupHelper.relayoutActivePopupsForOrientation(this);
        // 猜拳弹窗以绝对坐标定位、不经 DraggablePopupHelper 包装，旋转后需单独重定位，
        // 保证竖屏转横屏时仍在 layout_game_right 底部居中（避免停留在左下角）
        if (dialogUtil != null) {
            dialogUtil.repositionHandSelectDialog();
        }
        // 主菜单阶段：MainMenuDialog 是独立窗口不受 setContentView 影响，无需回显
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
        soundManager.setMusicMode(appsSettings.getIntSettings("chkSwitchBGM", 1) == 1);
        // 召唤主题歌（chants）播完：按当前场景重新选曲恢复 BGM
        soundManager.setOnChantFinishListener(this::updateBGM);

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
        Thread t = new Thread(() -> {
            DataManager.get().load(false);
            Log.i(TAG, "DataManager loaded");
        }, "DataLoad");
        CrashHandler.getInstance().hookThread(t, "游戏-卡片数据加载");
        t.start();
    }

    /**
     * 初始化即后台启动 windbot：提前完成 WindBot.initAndroid 与 RUN_WINDBOT 监听注册，
     * 使人机对战不必等到进入 PlayerWaitingDialog 才初始化（原 ResCheckTask 路径在
     * isOnlyGame 直达决斗界面时会被跳过），节约启动等待时间。
     * initAndroid 在 WindBotService 内做了进程级去重：MainActivity 已初始化过时
     * 此处仅确保监听已注册，不会重复进入 Mono 运行时（重复 init 会导致 libmonosgen 崩溃）
     */
    private void startWindbotListener() {
        Thread t = new Thread(() -> {
            WindBotService.startListening(getApplicationContext());
            Log.i(TAG, "WindBot listener ready");
        }, "WindBotInit");
        CrashHandler.getInstance().hookThread(t, "游戏-WindBot初始化");
        t.start();
    }

    /**
     * 后台预热 native 决斗引擎：提前完成 scripts.zip 解压与 cards.cdb/脚本加载
     * （{@code OcgDuelEngine.init} 的首次耗时数秒一次性开销），使首次建立局域网主机
     * 时无需再等待引擎引导，握手即时完成。ensureEngineReady 幂等且同步，与建主线程
     * 并发时后者会阻塞至预热完成，不会重复加载。
     */
    private void warmUpDuelEngine() {
        Thread t = new Thread(() -> {
            boolean ready = NativeScriptBootstrap.ensureEngineReady();
            Log.i(TAG, "Duel engine warm-up " + (ready ? "done" : "skipped/failed"));
        }, "EngineWarmUp");
        CrashHandler.getInstance().hookThread(t, "游戏-引擎预热");
        t.start();
    }

    // === 供三个UI管理类回调的桥接方法 ===

    public GameEngine getEngine() {
        return engine;
    }

    public int getCurrentSelectType() {
        return cardDetailPanel.getSelectType();
    }

    /**
     * 当前回放播放器（{@link GameEngine} 的常驻协作件，与实况管线共用同一个 GameField /
     * GameFieldView，故不再需要「当前回放引擎」这种可空引用；未就绪时返回 null）
     */
    public ReplayPlayer getReplayPlayer() {
        return engine != null ? engine.replayPlayer : null;
    }

    public void quitReplay() {
        ReplayModeDialog.quitReplay(this);
    }

    /**
     * 声音 / 音乐静音切换（实现见 {@link GameSettingsApplier}）
     */
    public void toggleSoundMute() {
        settingsCtl.toggleSoundMute();
    }

    /**
     * 决斗速度开关（对齐 gframe imgQuickAnimation 点击切换 quick_animation 并保存）
     */
    public void toggleQuickAnimation() {
        settingsCtl.toggleQuickAnimation();
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
            saveLastConnectionInfo(Constants.PlayerName, host, port, room, "");
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
                        engine.startSingleMode(singleFile.getAbsolutePath(), false); // 默认不使用顶端返回
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
        saveLastConnectionInfo(user, host, port, room, password);
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

    /**
     * 模态对话框（是/否、卡片选择/确认、命令菜单）显示——禁用决斗场三个阶段按钮
     */
    public void notifyGameDialogShown(Object dialog) {
        if (fieldCtl != null) fieldCtl.onModalDialogShown(dialog);
    }

    /**
     * 模态对话框隐藏——恢复决斗场三个阶段按钮
     */
    public void notifyGameDialogHidden(Object dialog) {
        if (fieldCtl != null) fieldCtl.onModalDialogHidden(dialog);
    }

    public GameTopInfoManager getTopInfoManager() {
        return topInfoManager;
    }

    // === drawspec 特效覆盖层（居中动作文本 / 观战与系统消息弹幕宿主，委托 EngineCallbackDelegate 持有） ===

    /**
     * drawspec 覆盖层按需创建（弹幕入场用；与特效回调共用同一实例）
     */
    public SpecEffectOverlay obtainSpecOverlay() {
        return engineCallback != null ? engineCallback.ensureSpecOverlay() : null;
    }

    /**
     * 已存在的 drawspec 覆盖层（不创建）：弹幕全部移除后的空闲收口用
     */
    public SpecEffectOverlay getSpecOverlay() {
        return engineCallback != null ? engineCallback.peekSpecOverlay() : null;
    }

    /**
     * 观战「切换视角」（对齐 event_handler.cpp BUTTON_REPLAY_SWAP player_type==7 分支 →
     * DuelClient::SwapField）：置位请求由引擎在主线程消息消费点执行 ReplaySwap 同构交换
     */
    public void onSpectatorSwapField() {
        if (engine != null) engine.requestSpectatorSwap();
    }

    /**
     * 观战「跳到当前」（需求5）：无视动画将当前已收到的全部通讯 msg 一次性即时落位到最后
     * 一条 msg 的决斗局面，让观战者无需再看前面的回放/特效动画。
     */
    public void onSpectatorSkipToCurrent() {
        if (engine != null) engine.spectatorSkipToCurrent();
    }

    /**
     * 观战退出（对齐 event_handler.cpp BUTTON_LEAVE_GAME player_type==7：StopClient +
     * CloseDuelWindow）：断开连接并返回局域网主界面；导航由本入口独占，
     * 抑制 DISCONNECTED 回调的重复返回
     */
    public void quitSpectator() {
        suppressNextDisconnectedReturn();
        if (engine != null) engine.disconnect();
        returnToLanMain(null);
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

    /**
     * 供 LanModeDialog 静态入口构造时接线：返回承载 OnLanModeListener 的协作实例
     */
    public LanModeDialog.OnLanModeListener getDialogNavListener() {
        return menuNav;
    }

    /**
     * 供 PlayerWaitingDialog 静态入口构造时接线：返回承载 OnPlayerWaitingListener 的协作实例
     */
    public PlayerWaitingDialog.OnPlayerWaitingListener getPlayerWaitingListener() {
        return menuNav;
    }

    /**
     * 集中决策当前场景 BGM 并交 SoundManager 播放（实现见 {@link BgmSceneController}），
     * 必须在主线程调用。
     */
    public void updateBGM() {
        bgmCtl.update();
    }

    /**
     * 决斗判定胜负时设置 BGM 胜负覆盖并刷新场景
     * （对齐 Game::playBGM 的 dInfo.isFinished && showcardcode==1/2/3 分支）
     */
    public void setBgmDuelResult(boolean selfWon) {
        bgmCtl.setDuelResult(selfWon);
    }

    public void hideGameUI() {
        // 离开决斗场即切出对局/回放语境，崩溃诊断场景跟着回退
        CrashHandler.getInstance().setScene("游戏-菜单与大厅");
        // 退出对战（layout_game_right 隐藏）即离开决斗语境：清除 BGM 决斗进行标志与
        // 上一局残留的胜负覆盖（对齐 dInfo.isStarted / isFinished 复位），末尾 updateBGM 切回 MENU/DECK
        bgmCtl.leaveDuel();
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
        setGameTopPanelVisible(false);
        if (layoutGameContent != null) layoutGameContent.setVisibility(View.GONE);
        // 离开决斗场后重算聊天 UI：layoutDeckEditor 显隐此时已定型，输入框/开关按场景归位
        updateChatUIVisibility();
        // 离开决斗场后重算场景：duelActive 已置假 → 非卡组编辑则回 MENU（修正结束一局后
        // WIN/LOSE 音乐残留不回菜单的问题）
        updateBGM();
    }

    private void showGameUI() {
        CrashHandler.getInstance().setScene("游戏-横屏决斗场");
        // 新开一局/回放：置决斗进行标志并清除上一局残留的 BGM 胜负覆盖（刚开局按 DUEL 走）
        bgmCtl.enterDuel();
        setWindowBackground(Constants.CORE_SKIN_PATH + "/" + Constants.CORE_SKIN_BG);
        getMainMenuDialog().hideMainMenu();
        if (layoutGameContent != null) layoutGameContent.setVisibility(View.VISIBLE);
        if (layoutGameRight != null) layoutGameRight.setVisibility(View.VISIBLE);
        // 竖屏顶部面板（卡片详情 + 时点/录像/卡组按钮行）与决斗场同进同退
        setGameTopPanelVisible(true);

        // layout_game_right 显示第一时间初始化顶部信息：头像/玩家名称/房间血量（猜拳前可见）
        if (topInfoManager != null) topInfoManager.prepareForDisplay();

        fieldCtl.show();
        cardDetailPanel.onGameUIShown();
        if (dialogContainer != null) dialogContainer.setVisibility(View.VISIBLE);
        // 进入决斗场重算聊天 UI（对齐 gframe：回放/残局这些无聊天对象的场景显示 GameUI 时
        // 隐藏 wChat；正常决斗/观战显示），覆盖退出录像后残留的隐藏态
        updateChatUIVisibility();
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
        // Solo 模式：席位上房主代选的卡组名优先写回 GameTopInfo，代替默认玩家昵称展示（
        // 非 TAG solo 仅 slot0/1；TAG solo 同样取 slot0/1 写回两列、slot2/3 留在等待面板上）
        if (engine != null && engine.soloMode && topInfoManager != null) {
            String selfName = engine.soloSeatDeckNames[0];
            String oppName = engine.soloSeatDeckNames[1];
            if (selfName != null && !selfName.isEmpty()) {
                topInfoManager.setPlayerDisplay(0, selfName, null);
            }
            if (oppName != null && !oppName.isEmpty()) {
                topInfoManager.setPlayerDisplay(1, oppName, null);
            }
        }
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
                // 保留上一局主机密码：重新显示局域网主界面时回填房间密码（用户规格，不再清空）
                lanModeDialog.preFillConnectionFields(lastJoinNickname, lastJoinHost,
                        String.valueOf(lastJoinPort), lastJoinPassword);
            }
            // 返回局域网主界面属“其他情况”，切 MENU 场景
            updateBGM();
        }
        if (toastMsg != null && !toastMsg.isEmpty()) {
            Toast.makeText(this, toastMsg, Toast.LENGTH_SHORT).show();
        }
    }

    void saveLastConnectionInfo(String nickname, String host, int port, String roomName, String password) {
        lastJoinNickname = nickname != null ? nickname : "";
        lastJoinHost = host != null ? host : "";
        lastJoinPort = port;
        lastJoinRoomName = roomName != null ? roomName : "";
        lastJoinPassword = password != null ? password : "";
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
        // 大厅聊天非决斗阶段：顶部面板隐藏，layout_game_right 升为整屏承载聊天区
        setGameTopPanelVisible(false);
        // 大厅聊天尚未进入决斗：卡片详情列（include 根 layout_card_detail_panel）与侧边图标栏
        // 均不得展示，修复横屏下进入 PlayerWaitingDialog 时 CardDetailPanel 错误可见
        //（旋转重建后 bindViews 会重绑默认 VISIBLE 的新视图树，因此需显式 hide）
        if (cardDetailPanel != null) cardDetailPanel.hide();
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
        // 玩家等待/大厅聊天尚未进入真正决斗（对齐 C++ dInfo.isStarted 仍为假）：
        // 虽让 layout_game_right 可见以承载聊天区，但 BGM 场景应为 MENU，故清决斗标志并刷新
        bgmCtl.leaveDuel();
        updateBGM();
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

    /**
     * 卡组编辑器视图切换（实现见 {@link DeckEditorViewHost}）
     */
    public void showDeckEditorView() {
        deckEditorHost.show();
    }

    public void setWindowBackground(String relativePath) {
        String path = AppsSettings.get().getResourcePath() + "/" + relativePath;
        if (TextUtils.equals(path, currentBgPath)) {
            // 命中缓存也要重贴区域背景：旋转重建后 layout_game_right 是新视图树实例，背景已丢
            updateFieldRegionBackground();
            return;
        }
        File file = new File(path);
        if (file.exists()) {
            try {
                Bitmap bitmap = BitmapFactory.decodeFile(path);
                if (bitmap != null) {
                    currentBgPath = path;
                    // 存原始解码位图（不预旋转）；窗口/区域背景的实际朝向由
                    // updateFieldRegionBackground 按当前屏幕方向派生，旋转切换时自动重算
                    lastBgBitmap = bitmap;
                    // 按当前方向应用背景（竖屏旋转为纵向，横屏用原图）
                    updateFieldRegionBackground();
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to load background: " + relativePath, e);
            }
        }
    }

    /**
     * 按当前屏幕方向应用背景图（用户规格）：
     * <ul>
     * <li>横屏：直接用原始横向图作窗口背景，layout_game_right 不设区域背景（透出窗口原图）；</li>
     * <li>竖屏：不再把横向图旋转 90° 后按高等比 REPEAT 平铺——旋转得到的纵向图窄于屏宽，
     * 平铺补边会在屏幕正中留下可见的图片拼贴边（历史 PlayerWaitingDialog 竖屏背景“正中显示
     * 图片边缘”根因）。改为保持原本横向图，用 {@link RightAlignedTiledDrawable} 按 bounds 高等比
     * 缩放并右对齐：横向图缩放到屏高后宽度恒大于屏宽，于是屏幕只显示图片最右侧的部分、左侧
     * 超出屏幕的部分自然裁剪掉（无平铺缝）——决斗(bg.jpg)/卡组编辑(bg_deck.jpg)/大厅等待界面同规格。</li>
     * </ul>
     * 原始解码位图不改动，故旋转切换（rebuildUiForOrientation）与缓存命中（setWindowBackground）都会重算。
     */
    void updateFieldRegionBackground() {
        if (lastBgBitmap == null || lastBgBitmap.isRecycled()) {
            if (layoutGameRight != null) layoutGameRight.setBackground(null);
            return;
        }
        boolean portrait = getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_PORTRAIT;
        if (portrait) {
            // 竖屏：保持原本横向图，右对齐裁剪左侧超出（每张单独实例，避免窗口背景与区域背景共享）
            getWindow().setBackgroundDrawable(new RightAlignedTiledDrawable(lastBgBitmap));
            if (layoutGameRight != null) {
                layoutGameRight.setBackground(new RightAlignedTiledDrawable(lastBgBitmap));
            }
        } else {
            getWindow().setBackgroundDrawable(new BitmapDrawable(getResources(), lastBgBitmap));
            if (layoutGameRight != null) layoutGameRight.setBackground(null);
        }
    }

    /**
     * 竖屏顶部面板显隐（与 layout_game_right 的决斗/卡组编辑器生命周期同步）：
     * 决斗场显示/卡组编辑器（需卡片详情+卡组按钮）→ VISIBLE（占屏高 1/3，
     * layout_game_right 余 2/3）；菜单/大厅聊天 → GONE（不占位，区域升为整屏）。
     * 横屏布局无该节点，findViewById 为 null 时静默跳过。
     */
    void setGameTopPanelVisible(boolean visible) {
        if (layoutGameTopPanel == null)
            layoutGameTopPanel = findViewById(R.id.layout_game_top_panel);
        if (layoutGameTopPanel != null)
            layoutGameTopPanel.setVisibility(visible ? View.VISIBLE : View.GONE);
    }


    public void showSettingsDialog() {
        // 设置弹窗已在显示中：再次点击设置按钮改为隐藏当前弹窗，不新建叠加
        //（CardDetailPanel 设置按钮横竖屏共用本入口，故横竖屏行为一致）
        if (settingsDialog != null && settingsDialog.isShowing()) {
            settingsDialog.dismiss();
            return;
        }
        getMainMenuDialog().hideMainMenu();
        SettingsDialog dialog = new SettingsDialog(this, () -> applySettingsToEngine());
        settingsDialog = dialog;
        dialog.show(dialogContainer);
        dialog.setOnDismissListener(() -> {
            if (settingsDialog == dialog) settingsDialog = null;
            boolean deckEditorShowing = layoutDeckEditor != null
                    && layoutDeckEditor.getVisibility() == View.VISIBLE;
            boolean gameRightShowing = layoutGameRight != null
                    && layoutGameRight.getVisibility() == View.VISIBLE;
            if (!deckEditorShowing && !gameRightShowing) {
                getMainMenuDialog().restoreMainMenu();
            }
        });
    }

    /**
     * 按 AppsSettings 当前值统一应用已保存设置（音频 / 侧栏图标 / 动画速度 / 禁限表，
     * 实现见 {@link GameSettingsApplier}）
     */
    public void applySettingsToEngine() {
        settingsCtl.apply();
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
            // 切换后走统一场景核算重置输入框可见性（ChatInputUI 内的直接显隐不感知抑制场景）
            updateChatUIVisibility();
        }
    }

    /**
     * 聊天 UI 抑制场景（用户规格对齐 gframe：这些场景不显示 wChat 聊天框）：
     * 卡组/副卡组编辑器、残局（single mode）、录像回放观看。观战不抑制（仍有聊天/弹幕对象）。
     */
    private boolean isChatUiSuppressedScene() {
        if (layoutDeckEditor != null && layoutDeckEditor.getVisibility() == View.VISIBLE)
            return true;
        GameEngine eng = engine;
        if (eng == null) return false;
        if (eng.isSingleMode) return true;
        return eng.replayMode && eng.replayPlayer != null && eng.replayPlayer.hasActiveSession();
    }

    /**
     * 聊天输入框与聊天开关按钮可见性统一核算：输入框需设置允许（未停用聊天）且非抑制场景；
     * 开关按钮（用户规格）不受停用聊天设置影响、仅抑制场景隐藏——停用后必须留着入口才能点回启用。
     * 卡组编辑/录像/残局进入与退出、旋转重建、设置变更均经此刷新（用户规格）。
     */
    public void updateChatUIVisibility() {
        boolean settingAllow = AppsSettings.get().getIntSettings("chkDisableChatting", 0) != 1;
        boolean suppressed = isChatUiSuppressedScene();
        if (etChatInput != null) {
            // 大厅等待界面例外：输入框是聊天主入口，即使停用聊天设置也无条件显示
            //（与 chatInputUI.enterLobbyChatUI 的无条件可见语义一致，旋转重建经此不得回退）
            boolean lobby = playerWaitingDialog != null && playerWaitingDialog.isShowing();
            etChatInput.setVisibility(lobby || (settingAllow && !suppressed) ? View.VISIBLE : View.GONE);
        }
        if (cardDetailPanel != null) {
            // 停用聊天只隐藏输入框与聊天信息，开关恒可见（非抑制场景）
            cardDetailPanel.setChatToggleVisible(!suppressed);
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

    /**
     * 撤回入口（本工程扩展，gframe 无对应交互）：向局域网房间的服务端发 CTOS_UNDO，
     * 请其回退本方最近一次应答。不做二次确认：撤回本身是可逆的回退动作，误触后可再次
     * 发起或由对方重新操作；结果（已回退 / 重建失败 / 不可撤回）由服务端经 STOC_UNDO_ACK
     * 回报，走 {@code GameEngine.onUndoAck} 提示并在随后的 MSG_RELOAD_FIELD 重同步场面
     */
    public void requestUndo() {
        if (engine == null || !engine.isInDuel() || !engine.canUndo()) return;
        engine.sendUndo();
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

    // 录像回放的召唤/连锁/无效大图与阶段文字已由实况管线（EngineCallbackDelegate 的消息回调）
    // 直接派发，回放不再需要一套专用转发入口

    // === Response helpers ===

    public void sendResponseInt(int value) {
        ByteBuffer buf = ByteBuffer.allocate(4);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(value);
        engine.sendResponse(buf.array());
    }

    // === Lifecycle ===

    @Override
    protected void onPause() {
        super.onPause();
        // 切到桌面/其他应用：暂停 BGM（不释放播放器，回前台原曲续播）
        if (soundManager != null) soundManager.pauseBGM();
    }

    @Override
    protected void onResume() {
        super.onResume();
        setupFullScreen();
        // 回前台：先续播后台暂停的曲目，再重算场景 BGM（同场景且仍在播时
        // SoundManager 内部去重，不会重新起曲）
        if (soundManager != null) soundManager.resumeBGM();
        mainHandler.post(this::updateBGM);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        CrashHandler.getInstance().setScene("游戏-销毁中");
        if (engineCallback != null) engineCallback.cancelReplayProcessing();
        DraggablePopupHelper.resetAllPositions(this);
        // 释放局域网三对话框，避免持有已销毁的窗口/上下文
        dismissAllLanDialogs();
        lanModeDialog = null;
        createHostDialog = null;
        playerWaitingDialog = null;
        // 释放设置弹窗（独立 PopupWindow，显示中销毁会 WindowLeaked）并清引用
        if (settingsDialog != null) {
            settingsDialog.dismiss();
            settingsDialog = null;
        }
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
                ReplayPlayer rp = getReplayPlayer();
                if (rp != null && rp.hasActiveSession()) {
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
