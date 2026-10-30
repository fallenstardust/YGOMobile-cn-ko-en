package cn.garymb.ygomobile;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.MotionEvent;
import android.view.OrientationEventListener;
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
import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygodata.YGOGameOptions;
import cn.garymb.ygomobile.audio.SoundManager;
import cn.garymb.ygomobile.engine.NativeScriptBootstrap;
import cn.garymb.ygomobile.game.chat.ChatInputUI;
import cn.garymb.ygomobile.game.deck.DeckEditorManager;
import cn.garymb.ygomobile.game.engine.GameEngine;
import cn.garymb.ygomobile.game.gamefield.GameField;
import cn.garymb.ygomobile.game.gamefield.ClientCard;
import cn.garymb.ygomobile.game.gamefield.GameFieldController;
import cn.garymb.ygomobile.game.gamefield.GameTopInfoManager;
import cn.garymb.ygomobile.game.replay.ReplayPlayer;
import cn.garymb.ygomobile.game.select.ShowDialogUtil;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.render.card.CardDetailPanel;
import cn.garymb.ygomobile.render.gamefieldview.GameFieldView;
import cn.garymb.ygomobile.render.spec.SpecEffectOverlay;
import cn.garymb.ygomobile.render.field.TextureLoader;
import cn.garymb.ygomobile.ui.dialogs.lobby.CreateHostDialog;
import cn.garymb.ygomobile.ui.dialogs.duel.DuelLogDialog;
import cn.garymb.ygomobile.ui.dialogs.lobby.EmotionDialog;
import cn.garymb.ygomobile.ui.dialogs.lobby.LanModeDialog;
import cn.garymb.ygomobile.ui.dialogs.lobby.MainMenuDialog;
import cn.garymb.ygomobile.ui.dialogs.lobby.PlayerWaitingDialog;
import cn.garymb.ygomobile.ui.dialogs.replay.ReplayModeDialog;
import cn.garymb.ygomobile.ui.dialogs.lobby.SettingsDialog;
import cn.garymb.ygomobile.ui.dialogs.lobby.SingleModeDialog;
import cn.garymb.ygomobile.ui.dialogs.duel.YesOrNoDialog;
import cn.garymb.ygomobile.utils.CrashHandler;
import cn.garymb.ygomobile.utils.DraggablePopupHelper;
import cn.garymb.ygomobile.utils.FullScreenUtils;
import cn.garymb.ygomobile.utils.ImePopupAdjuster;
import cn.garymb.ygomobile.utils.RightAlignedTiledDrawable;
import ocgcore.data.Card;
import ocgcore.DataManager;
import ocgcore.StringManager;


/**
 * 决斗主界面门面的抽象基类（自原 YGOProActivity 拆分的继承链顶层）：
 * 集中承载全部共享字段/常量、视图装配（setupFullScreen/initViews）、状态查询与
 * 对话框持有等核心成员；同包协作类（EngineCallbackDelegate / MainMenuNavigator /
 * BgmSceneController / GameSettingsApplier / DeckEditorViewHost）经包级私有直连本类共享状态。
 * 跨层调用经文末 abstract 钩子由链上下游类实现（拆分工艺与 DraggablePopupHelper 链一致）。
 */
public abstract class YGOProActivityBase extends AppCompatActivity {

    protected static final String TAG = "YGONativeGame";

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
    protected MainMenuNavigator menuNav;

    // 自本门面拆出的同包协作件（构造仅需 this，均为被动委托，无初始化顺序依赖）
    final BgmSceneController bgmCtl = new BgmSceneController((YGOProActivity) this);
    final GameSettingsApplier settingsCtl = new GameSettingsApplier((YGOProActivity) this);
    final DeckEditorViewHost deckEditorHost = new DeckEditorViewHost((YGOProActivity) this);

    DeckEditorManager deckEditorManager;
    View layoutDeckEditor;

    LinearLayout layoutDeckControl;
    // layout_game_right 横竖屏根节点均为 LinearLayout（六按钮列与聊天输入行横排流），
    // 消费点只用 View 级 API（可见性/背景/居中锚），声明为 View 兼容布局演进
    View layoutGameRight;
    /**
     * 竖屏顶部面板（卡片详情+时点/录像/卡组按钮行，占屏高 1/3）；横屏无此节点，全程空保护
     */
    View layoutGameTopPanel;
    View layoutGameContent;

    FrameLayout dialogContainer;
    protected MainMenuDialog mainMenuDialog;
    LanModeDialog lanModeDialog;
    CreateHostDialog createHostDialog;
    // 单机/录像选择窗同样持实例跨关闭复用：首次 show 初始化后关闭仅隐藏，重新打开走
    // reshow 回到关闭前的选择状态（用户规格，与 LanModeDialog/CreateHostDialog 同模式）
    SingleModeDialog singleModeDialog;
    ReplayModeDialog replayModeDialog;
    PlayerWaitingDialog playerWaitingDialog;

    EditText etChatInput;
    protected EmotionDialog emotionDialog;
    protected DuelLogDialog duelLogDialog;
    /**
     * 当前设置弹窗实例：显示中再次点击设置按钮应隐藏它而非叠开新的（横竖屏同一入口）
     */
    protected SettingsDialog settingsDialog;

    /**
     * 点击撤回上一步时被即时隐藏的询问弹窗快照（YesOrNoDialog）：撤回被拒且询问未被
     * 屏障路径重派时逐个恢复显示；撤回被接受（onUndoResync）时询问已被撤销，快照作废
     */
    protected final List<YesOrNoDialog> undoHiddenDialogs = new ArrayList<>();

    /**
     * 点击撤回瞬间是否除询问窗外还收口了其它应答类弹窗（选卡/确认/选项/宣言/表示形式）：
     * 这类窗没有可原地恢复的实例快照（弹完即只留静态 current 引用），被拒时只能按最近询问
     * 快照重派发重建；未被收口时维持旧的「恢复询问窗实例」路径，不重派发以免把已答完的旧询问
     * 弹回。标志随 clearUndoHiddenDialogs 一并作废，consumeUndoClosedQuestionDialog 读后复位。
     */
    protected boolean undoClosedQuestionDialog;

    /**
     * 点击撤回瞬间被作废的连锁询问现场备份：连锁窗静态引用（handleChainCancel 据此重新弹出）
     * 与强制连锁标志（决定「取消操作」是否允许）。撤回被拒恢复询问窗时必须一并回灌，否则
     * 连锁询问窗虽然重新可见，却因静态引用为空而使「取消操作」认不到自己那张窗。
     */
    protected YesOrNoDialog undoChainQueryBackup;
    protected boolean undoChainForcedBackup;

    /** 取用并复位「撤回瞬间收口过其它应答弹窗」标志（撤回被拒的恢复路径判定用） */
    public boolean consumeUndoClosedQuestionDialog() {
        boolean closed = undoClosedQuestionDialog;
        undoClosedQuestionDialog = false;
        return closed;
    }

    /**
     * 当前正在显示的投降确认对话框：显示期间（直至其消失）忽略投降按钮的重复点击，
     * 避免玩家多次点击弹出多个投降确认对话框（用户规格，竖屏/横屏按钮共用本入口）
     */
    protected YesOrNoDialog surrenderDialog;

    volatile boolean isGameStarted = false;

    CardDetailPanel cardDetailPanel;
    protected ChatInputUI chatInputUI;
    GameTopInfoManager topInfoManager;
    GameFieldController fieldCtl;
    ShowDialogUtil dialogUtil;
    protected boolean exitOnReturn = true;
    protected int directEnterMode = 0; // 0=normal, 1=replay dialog, 2=single dialog
    protected FullScreenUtils mFullScreenUtils;
    /**
     * 息屏→亮屏周期内冻结显示方向：息屏即锁定当前方向，重新亮屏时保持息屏前布局，
     * 不切换横竖屏（用户规格）；冻结经 wakeRotationWatcher 在亮屏后物理姿态跨轴变化时
     * 解除，才按“游戏横屏锁定”设置决定是否允许旋转
     */
    protected boolean wakeRotationFrozen = false;
    /** 息屏时刻的显示方向（ORIENTATION_LANDSCAPE/PORTRAIT），供亮屏后姿态比对 */
    protected int frozenOrientation = Configuration.ORIENTATION_UNDEFINED;
    /** 亮屏后的物理姿态监听：姿态轴与息屏前不同时解除冻结并按设置恢复旋转 */
    protected OrientationEventListener wakeRotationWatcher;
    /** 前台可交互状态（onResume→onPause 区间）：息屏广播在 onPause 之前送达，时序可靠 */
    protected boolean uiResumed = false;

    /** 息屏/亮屏系统广播：游戏在前台时息屏冻结旋转；亮屏补处息屏广播未收到的快速开关屏 */
    protected final BroadcastReceiver screenStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_SCREEN_OFF.equals(action)) {
                if (!isFinishing() && uiResumed) freezeWakeRotation();
            } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
                if (!isFinishing() && uiResumed && !wakeRotationFrozen) freezeWakeRotation();
            }
        }
    };
    protected String currentBgPath;
    /**
     * 最近一次解码成功的背景图（竖屏下另挂到 layout_game_right 区域平铺背景，旋转重建后据此重贴）
     */
    protected Bitmap lastBgBitmap;

    // 最近一次加入/创建房间的连接信息：断线或决斗结束返回局域网主界面时回显
    String lastJoinNickname = "";
    String lastJoinHost = "";
    int lastJoinPort = 0;
    String lastJoinRoomName = "";
    // 上一局主机/加入密码：决斗结束或退出玩家等待重新显示 LanModeDialog 时回填房间密码（用户规格，不清空）
    String lastJoinPassword = "";
    // 纯单人建主来源区分：仅当由 SingleModeDialog 纯单人页确定时置 true（退出等待应回 MainMenuDialog），
    // 由 LanMode→CreateHostDialog 勾选纯单人时保持 false（退出等待仍回 LanModeDialog）
    public boolean soloLaunchedFromSingleMode = false;
    protected void setupFullScreen() {
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

    protected void initViews() {
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

        // 聊天：输入法把输入框抬高时，所有在显弹窗（玩家等待 + 决斗中的是/否询问、宣言类、
        // 猜拳、先后攻、位置选择、卡片选择/显示、决斗记录、录像保存等，各 dialog 自行向
        // ImePopupAdjuster 登记）统一抬成「底边紧贴输入框上沿」，不再与输入框/键盘互相遮挡；
        // 输入法收起后各弹窗回到 layout_game_right 中央（区域锚定由工具类还原时补做）
        chatInputUI.setImeVisibilityListener(new ChatInputUI.ImeVisibilityListener() {
            @Override
            public void onImeShown() {
                ImePopupAdjuster.notifyImeShown(etChatInput);
            }

            @Override
            public void onImeHidden() {
                ImePopupAdjuster.notifyImeHidden();
                // 输入法收起：把血条下方各带（双方聊天行/弹幕带/提示带）重锚回正在落定的血条位置
                //（未落定时由本调用自行按轮等待，不采压缩中间态），消除抬键盘期间发出的记录
                // 顶在屏幕顶部、降回后不回位的问题（用户反馈）
                if (fieldCtl != null) fieldCtl.repinHudBands();
            }
        });
        // 提早把聊天输入框登记进联动工具：静置期采样其屏幕上沿基准，供玩家等待弹窗
        // 按「输入框是否真被抬高」的几何判定抬升/回位（不随输入法显隐事件翻转闪动）
        ImePopupAdjuster.bindChatInput(etChatInput);

        cardDetailPanel = new CardDetailPanel((YGOProActivity) this);
        // 横屏侧边六按钮列直接承载在视图树 layout_game_right 内（不再用浮层，见方法注释），
        // 把聊天输入框高度同步为 btn_chat 实测高使其与聊天开关同高、底部同线
        syncChatInputHeightToToggle();
        cardDetailPanel.bindViews();
        // 聊天输入框/聊天开关初始可见性：停用聊天设置与抑制场景（卡组编辑/录像/残局）
        // 统一核算（对齐 gframe wChat：停用聊天与无聊天对象场景均隐藏）
        updateChatUIVisibility();
        topInfoManager = new GameTopInfoManager((YGOProActivity) this, mainHandler);
        topInfoManager.initViews();
        // 聊天输入框随 gameTopInfo 同一 HUD 系数等比例缩放（平板上固定 dp 输入框极细难点）
        if (chatInputUI != null) chatInputUI.bindHudScale(topInfoManager);
        fieldCtl = new GameFieldController((YGOProActivity) this, mainHandler, topInfoManager);
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
    /**
     * 用户规格：横屏聊天输入框高度与聊天开关按钮（btn_chat）实测高度一致，使两者底部同线、
     * 视觉等高。六按钮列 layout_image_buttons_panel 直接作为 layout_game_right 水平行的最左侧
     * 一格承载在视图树内（GameFieldView 所在 FrameLayout 的同级左邻，不与 GL 曲面重叠），
     * 底部贴合区域下缘；本方法把树内 btn_chat 实测高同步到同行的 et_chat_input（随纹理换图尺寸
     * 变化）。竖屏底部按钮行按钮与输入框同为固定 30dp 一行、已等高，不做同步。
     */
    protected void syncChatInputHeightToToggle() {
        if (etChatInput == null || etChatInput.getLayoutParams() == null)
            return;
        if (getResources().getConfiguration().orientation
                != Configuration.ORIENTATION_LANDSCAPE)
            return;
        final View btnChat = findViewById(R.id.btn_chat);
        if (btnChat == null) return;
        btnChat.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, ob, olr) -> {
            int h = b - t;
            if (h > 0 && etChatInput.getLayoutParams() != null
                    && etChatInput.getLayoutParams().height != h) {
                etChatInput.getLayoutParams().height = h;
                etChatInput.requestLayout();
            }
        });
        // 已布局时直接同步一次，避免首帧高度缺省
        if (btnChat.getHeight() > 0) {
            etChatInput.getLayoutParams().height = btnChat.getHeight();
            etChatInput.requestLayout();
        }
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
        ReplayModeDialog.quitReplay((YGOProActivity) this);
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
    // === Main Menu ===

    public MainMenuDialog getMainMenuDialog() {
        if (mainMenuDialog == null) {
            mainMenuDialog = new MainMenuDialog((YGOProActivity) this);
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
    public ShowDialogUtil getDialogUtil() {
        if (dialogUtil == null) {
            dialogUtil = new ShowDialogUtil((YGOProActivity) this, imageLoader, mainHandler);
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

    public void setSingleModeDialog(SingleModeDialog dialog) {
        singleModeDialog = dialog;
    }

    public SingleModeDialog getSingleModeDialog() {
        return singleModeDialog;
    }

    public void setReplayModeDialog(ReplayModeDialog dialog) {
        replayModeDialog = dialog;
    }

    public ReplayModeDialog getReplayModeDialog() {
        return replayModeDialog;
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
        // 关闭日志面板并清空记录（对齐桌面版 CloseDuelWindow 的 lstLog->clear）；聊天tab 同生命周期一并清空
        if (duelLogDialog != null) duelLogDialog.dismiss();
        DuelLogDialog.clearLogs();
        DuelLogDialog.clearChats();
        if (dialogContainer != null) dialogContainer.setVisibility(View.GONE);
        if (layoutGameRight != null) layoutGameRight.setVisibility(View.GONE);
        setGameTopPanelVisible(false);
        if (layoutGameContent != null) layoutGameContent.setVisibility(View.GONE);
        // 决斗场已隐藏：FPS 角标同步隐藏（用户规格：仅决斗进行中显示）
        updateFpsVisibility();
        // 离开决斗场后重算聊天 UI：layoutDeckEditor 显隐此时已定型，输入框/开关按场景归位
        updateChatUIVisibility();
        // 离开决斗场后重算场景：duelActive 已置假 → 非卡组编辑则回 MENU（修正结束一局后
        // WIN/LOSE 音乐残留不回菜单的问题）
        updateBGM();
    }

    protected void showGameUI() {
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
        // 决斗场开始显示：点亮 FPS 角标
        updateFpsVisibility();
        // 进入决斗场重算聊天 UI（对齐 gframe：回放/残局这些无聊天对象的场景显示 GameUI 时
        // 隐藏 wChat；正常决斗/观战显示），覆盖退出录像后残留的隐藏态
        updateChatUIVisibility();
        updateBGM();
    }
    void saveLastConnectionInfo(String nickname, String host, int port, String roomName, String password) {
        lastJoinNickname = nickname != null ? nickname : "";
        lastJoinHost = host != null ? host : "";
        lastJoinPort = port;
        lastJoinRoomName = roomName != null ? roomName : "";
        lastJoinPassword = password != null ? password : "";
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
     * 图片边缘”根因）。改为保持原本横向图，用 {@link RightAlignedTiledDrawable} 以<b>整个窗口</b>
     * 为基准做 cover 缩放并右对齐（只显示图片最右侧的部分，左侧超出屏幕的部分被裁掉，X 方向
     * CLAMP 不再平铺补边）：窗口背景与 layout_game_right 区域背景共用同一映射，区域显示的就是
     * 它实机宽度所处位置的那一片图，两者逐像素连续、无拼接缝——决斗(bg.jpg)/卡组编辑(bg_deck.jpg)/
     * 大厅等待界面同规格。</li>
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

    /**
     * FPS 角标显隐集中核算（用户规格：仅决斗开始时、即 game_field_view 显示时展示，
     * 决斗结束后继续隐藏）：以 {@link View#isShown()} 为准——自身与全部祖先（
     * layout_game_content/layout_game_right）均可见才显示；大厅聊天期间决斗场被显式
     * GONE 也随之隐藏。isShown() 读可见性标志链，setVisibility 后立即可判不依赖布局帧。
     * 各决斗场显隐切换点（showGameUI/hideGameUI/进退大厅/旋转回显尾部）统一调用本方法。
     */
    void updateFpsVisibility() {
        TextView tvFps = findViewById(R.id.tv_fps);
        if (tvFps == null) return;
        View gv = findViewById(R.id.game_field_view);
        tvFps.setVisibility(gv != null && gv.isShown() ? View.VISIBLE : View.GONE);
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
    protected boolean isChatUiSuppressedScene() {
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
            // 大厅等待界面：输入框作为聊天主入口的初始可见由 enterLobbyChatUI 无条件 VISIBLE 保障；
            // 但用户经聊天开关明确停用（ChatInputUI 运行时状态）后同样以 INVISIBLE 隐藏输入框
            //（用户规格：横竖屏点击开关必须隐藏，INVISIBLE 保持占位不影响 gameField 位置）；
            // 非大厅场景仍按持久停用聊天设置与抑制场景核算
            boolean lobby = playerWaitingDialog != null && playerWaitingDialog.isShowing();
            boolean visible = lobby
                    ? (chatInputUI == null || chatInputUI.isChatEnabled())
                    : (settingAllow && !suppressed);
            // 隐藏一律用 INVISIBLE 而非 GONE（用户规格）：竖屏底部聊天输入框为 weight 布局，
            // GONE 会让左侧按钮组宽度跳变，INVISIBLE 保持占位稳定
            etChatInput.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
        }
        if (cardDetailPanel != null) {
            // 停用聊天只隐藏输入框与聊天信息，开关恒可见（非抑制场景）
            cardDetailPanel.setChatToggleVisible(!suppressed);
        }
    }
    public String getCardDisplayName(int code) {
        if (code <= 0) return "???";
        Card card = DataManager.get().getCardManager().getCard(code);
        if (card != null && card.Name != null) return card.Name;
        return "Card#" + code;
    }

    // cross-layer hooks: implemented by downstream classes of the chain
    abstract void warmUpDuelEngine();

    abstract void initEngine();

    abstract void loadData();

    abstract void startWindbotListener();

    abstract boolean handleDirectIntent(Intent intent);

    abstract void enterLobbyChatUI();

    abstract void freezeWakeRotation();
}

