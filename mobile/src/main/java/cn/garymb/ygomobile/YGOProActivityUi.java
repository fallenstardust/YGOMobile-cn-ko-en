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
import ocgcore.DataManager;
import ocgcore.StringManager;


/**
 * 决斗主界面门面的 UI/生命周期中间层（继承链第二层）：Activity 生命周期回调
 * （onCreate/onNewIntent/onConfigurationChanged/onPause/onResume/onDestroy）、
 * 息屏旋转冻结守卫、屏幕旋转时运行时重建 UI、触摸分发与各局域网对话框
 * 显示/隐藏/销毁管理；引擎初始化与场景导航由链下游类实现，经基类钩子调用。
 */
abstract class YGOProActivityUi extends YGOProActivityBase {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // 引擎引导放在 onCreate 第一时间拉起：脚本解压、卡包物化、cdb 加载全在
        // 子线程与界面初始化并行进行，用主菜单/建房/猜拳/选先后攻的全部自然耗时
        // 覆盖首次引导开销，使 ServerDuel#startDuel 开局闸门即时通过、选完直接发牌
        warmUpDuelEngine();
        // 启动方向按“游戏横屏锁定”设置决定（见 applyOrientationLock）：
        // 锁定启用→始终横屏（SENSOR_LANDSCAPE 仍可左右横屏对调旋转，不自动转竖屏）；
        // 未启用→SCREEN_ORIENTATION_FULL_SENSOR，无视系统自动旋转开关也随设备方向自动旋转
        applyOrientationLock();
        // 息屏/亮屏旋转守卫（用户规格）：游戏显示中息屏后重新亮屏保持原布局，亮屏瞬间不切换
        // 横竖屏；之后仅当未开启“游戏横屏锁定”设置时才随物理姿态变化旋转切换
        wakeRotationWatcher = new OrientationEventListener(this) {
            @Override
            public void onOrientationChanged(int orientation) {
                if (!wakeRotationFrozen || orientation == ORIENTATION_UNKNOWN) return;
                boolean physicalPortrait =
                        orientation >= 315 || orientation <= 45
                                || (orientation >= 135 && orientation <= 225);
                int physical = physicalPortrait ? Configuration.ORIENTATION_PORTRAIT
                        : Configuration.ORIENTATION_LANDSCAPE;
                if (physical != frozenOrientation) {
                    // 亮屏后用户实际改变了持机姿态：解除冻结并按设置恢复旋转——
                    // 横屏锁定开启→SENSOR_LANDSCAPE（仍不转竖屏）；未开启→FULL_SENSOR（可横竖切换）
                    wakeRotationFrozen = false;
                    disable();
                    applyOrientationLock();
                }
            }
        };
        IntentFilter screenFilter = new IntentFilter();
        screenFilter.addAction(Intent.ACTION_SCREEN_OFF);
        screenFilter.addAction(Intent.ACTION_SCREEN_ON);
        registerReceiver(screenStateReceiver, screenFilter);
        // 崩溃诊断场景锚点：未捕获异常经全局 CrashHandler 落盘到 ygocore/log
        CrashHandler.getInstance().setScene("游戏-启动初始化");
        setupFullScreen();
        setContentView(R.layout.activity_ygo_game);

        initViews();
        initEngine();
        loadData();
        startWindbotListener();
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
     * 未启用时取 SCREEN_ORIENTATION_FULL_SENSOR（用户规格）：即使系统自动旋转未开启，
     * 也跟随设备物理方向在横/竖屏间自动旋转（FULL_SENSOR 无视系统自动旋转开关，
     * 区别于 USER——后者仍受系统开关阻断）；锁定开启时的 SENSOR_LANDSCAPE 保持原有语义。
     * configChanges 已声明 orientation|screenSize，旋转不重建 Activity。
     */
    protected void applyOrientationLock() {
        if (AppsSettings.get().isLockSreenOrientation()) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        } else {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR);
        }
    }

    /**
     * 息屏冻结（用户规格）：游戏显示中息屏后重新亮屏要保持原布局，亮屏瞬间不切换横竖屏——
     * 息屏广播到达时（早于 onPause）立即用 SCREEN_ORIENTATION_LOCKED 锁定当前显示方向，
     * 息屏期间即使持机姿态改变，亮屏时系统也不会旋转到新方向；冻结由 wakeRotationWatcher
     * 在亮屏后物理姿态跨轴（竖↔横）变化时解除，再按横屏锁定设置恢复旋转行为。
     */
    protected void freezeWakeRotation() {
        frozenOrientation = getResources().getConfiguration().orientation;
        wakeRotationFrozen = true;
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LOCKED);
        if (wakeRotationWatcher != null) {
            wakeRotationWatcher.enable();
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
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        if (isFinishing() || isDestroyed()) return;
        // 息屏→亮屏冻结期的竞态兜底：冻结期内到达的方向变化不重建，保持息屏前布局，
        // 待物理姿态跨轴变化经 wakeRotationWatcher 解除冻结后再按设置旋转
        if (wakeRotationFrozen && newConfig.orientation != frozenOrientation) {
            return;
        }
        rebuildUiForOrientation();
    }
    /**
     * 屏幕旋转后运行时重建 UI（不重建引擎/通讯/场面数据，决斗不断线）：
     * setContentView 按当前方向取 res/layout-port（竖屏：我方血条左下/对方右上/聊天自下而上/
     * 按钮列左上）或默认 res/layout（横屏：左侧 cardDetailPanel + 右侧决斗场），
     * 再把各管理器重新绑定到新视图树并按重建前阶段回显。
     */
    protected void rebuildUiForOrientation() {
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
        // 「取消操作/完成选择」按钮旋转前若在显示（选择会话进行中）：快照其可见态与文字，
        // 供重建尾部 onGameUIShown 复位后回显——会话跨旋转保留，按钮不得凭空消失（用户反馈）
        if (cardDetailPanel != null) cardDetailPanel.saveCancelOrFinishForRotation();

        // 输入法抬升态在旋转重建起点即解除（用户规格：横竖屏切换后 dialog 一律回到区域中央）：
        // 须早于 relayoutActivePopupsForOrientation 与猜拳/先后攻等自定位弹窗的旋转重定位，
        // 否则抬升守卫会推迟区域锚定、弹窗卡在收缩后的临时位置
        ImePopupAdjuster.notifyImeHidden();

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
        // 旋转后新输入框实例同步重绑联动工具：刷新静置上沿基准的采样视图（旧视图树作废）
        ImePopupAdjuster.bindChatInput(etChatInput);
        // 横屏侧边六按钮列直接承载在新视图树 layout_game_right 内，把聊天输入框高度重新
        // 同步为树内 btn_chat 实测高（旋转重建后新树默认尺寸需重算）
        syncChatInputHeightToToggle();
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
            // 卡组/副卡组编辑器：旋转专用重绑——只把视图与监听重挂到新树并按现态重渲染，
            // 保留当前卡组/卡包展示与未保存修改（show() 的 initialize 会 loadLastDeck 重载覆盖）
            deckEditorHost.showForRotation();
            // 与决斗分支同规格回显卡详：enterDeckEditorMode 内部 showDefault 已清面板，
            // 此处按重建前快照（detailShowing/detailCardCode）回显当前卡，对齐用户
            // 规格“横竖屏切换前后保持同一张卡详情显示”
            if (cardDetailPanel != null) {
                cardDetailPanel.restoreAfterRebind(detailShowing, detailCardCode);
            }
        } else if (lobbyChat) {
            enterLobbyChatUI();
            // 旋转前若聊天输入法已把弹窗抬升到输入框上方：旋转开始时已统一 notifyImeHidden
            // 解除抬升态（见本方法 1) 与 2) 之间），弹窗此时按新屏回到区域中央
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
                // 回显旋转前的取消/完成按钮：选择会话跨旋转保留，按钮续显后玩家可继续
                // 取消/完成该会话（currentSelectType 随复用的面板保留，点击即走正确分支）
                cardDetailPanel.restoreCancelOrFinishAfterRotation();
            }
        } else {
            // 其他场景（主菜单/局域网与建主等待弹窗/设置等，用户规格）：竖屏布局变体的
            // layout_game_right 默认 VISIBLE（XML visibility=gone 双重保险），非决斗分支
            // 不得回显卡片详情布局与决斗场区：整棵内容树隐藏；严禁触达 fieldCtl.hide()
            //（旋转仅是视图重建，场面数据必须保留）
            if (layoutGameContent != null) layoutGameContent.setVisibility(View.GONE);
            if (layoutGameRight != null) layoutGameRight.setVisibility(View.GONE);
            setGameTopPanelVisible(false);
            // 非决斗分支回显后 FPS 角标随手势/旋转前的决斗态一并隐藏
            updateFpsVisibility();
        }
        // 聊天输入框/开关在新视图树回显完成后按当前场景重算（卡组编辑分支经 deckEditorHost.show
        // 已算一次，此处覆盖决斗/大厅/菜单各分支，避免旋转后新树默认 VISIBLE 漏显抑制态）
        updateChatUIVisibility();
        // 新视图树 tv_fps 默认 GONE：决斗/录像分支回显后按决斗场实际可见性统一重算角标
        updateFpsVisibility();
        // 旋转后新视图树的 layout_game_right 背景随旧树销毁，按最近背景图重贴区域平铺背景
        updateFieldRegionBackground();
        // gameEngine 相关对话框（PopupWindow 独立窗口，不随 setContentView 重建）按新屏宽
        // 重新解算显示宽度并重新居中，避免横转竖宽度超屏文字截断、竖转横显示过小（用户规格）
        DraggablePopupHelper.relayoutActivePopupsForOrientation(this);
        // 猜拳/先后攻弹窗以 showAsDropDown/showAtLocation 定位、不经 DraggablePopupHelper 包装，旋转后需单独重定位，
        // 保证竖屏转横屏时仍在 layout_game_right 内各自对齐位置（猜拳底部居中、先后攻正中），
        // 而非停留在旋转前的旧坐标
        if (dialogUtil != null) {
            dialogUtil.repositionHandSelectDialog();
            dialogUtil.repositionTPSelectDialog();
        }
        // 主菜单阶段：MainMenuDialog 是独立窗口不受 setContentView 影响，无需回显
    }
    public void toggleEmotionDialog(View anchor) {
        if (emotionDialog == null) {
            emotionDialog = new EmotionDialog((YGOProActivity) this);
        }
        emotionDialog.toggle(anchor);
    }
    /**
     * 决斗内聊天行/弹幕长按观察：事件穿透的透明覆盖窗（SpecEffectOverlay）自身不接收触摸，
     * 故在 Activity 派发点非消费式转发给聊天协作类——仅据此命中顶部文字做长按复制/减速，
     * 普通点按继续原样穿透到下层决斗场（return super 不改派发链，不影响卡片选择等正常操作）。
     * <p>
     * 另外：卡片状态气泡（CardStatusTipHelper.FieldTip）是事件穿透的全屏 PopupWindow，下层
     * 任何控件（阶段按钮/顶部信息/聊天等非 GL 区域）的点击都不会经过决斗场的空白点隐藏路径，
     * 故在派发起点按下去重：每次新触摸按下先收起在显示的气泡；若本次落点是可显示气泡的卡片，
     * 抬手时 onCardClick 会以新代际重新排期展示，不受本次收起影响（需求：点屏上任意
     * 不产出新气泡的位置即隐藏旧气泡）。
     * <p>
     * 聊天输入框失焦（用户规格）：按下点不在输入框上时取消其焦点并收起输入法，避免光标
     * 一直闪动、输入法已隐藏而焦点仍在输入框时再点输入框唤不起输入法；弹窗包装层转发
     * 进来的触摸不经本派发点，由 DragFrameLayout 的按下归属判定处调同一方法补齐。
     */
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        boolean down = ev.getActionMasked() == MotionEvent.ACTION_DOWN;
        if (fieldCtl != null) {
            fieldCtl.onFieldTouchForChat(ev);
            if (down) fieldCtl.hideFieldStatusTip();
        }
        if (down) {
            ChatInputUI.clearFocusIfTouchedOutside(this, ev.getRawX(), ev.getRawY());
        }
        return super.dispatchTouchEvent(ev);
    }
    /**
     * 决斗/回放开场：局域网主界面与建主界面仅隐藏（hideForNavigation 保留 reshow 复用态，
     * 不触发外部 dismiss 回调），下次重新打开回到关闭前的规则/输入选择状态（用户规格）；
     * 玩家等待界面维持销毁语义（性能权衡：其房间列表/玩家状态每次都必须重建）
     */
    protected void hideLanDialogsForDuel() {
        if (lanModeDialog != null) {
            lanModeDialog.hideForNavigation();
        }
        if (createHostDialog != null) {
            createHostDialog.hideForNavigation();
        }
        if (playerWaitingDialog != null) {
            playerWaitingDialog.setOnDismissListener(null);
            playerWaitingDialog.dismiss();
        }
    }

    /**
     * 彻底销毁三个局域网对话框（仅 Activity onDestroy 用：隐藏态 PopupWindow 随窗口销毁会
     * WindowLeaked，必须真 dismiss；先清空 dismiss 回调避免误恢复主菜单）
     */
    protected void dismissAllLanDialogs() {
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
            // 在 Settings 的 PopupWindow.dismiss 同步回调里立即重显主菜单窗时，设置窗尚未
            // 从 WindowManager 完成移除，主菜单 showAtLocation 偶发因窗口 token 瞬时失效抛异常，
            // 又被 DraggablePopupHelper.showPopup 静默吞掉 → 界面只剩背景图（按返回键的
            // 干净上下文调用故能显示）。推迟到下一帧等旧窗彻底移除后再重显，并由
            // MainMenuDialog 内部对显示失败补一帧重试，双保险消除该竞态。
            dialogContainer.post(() -> {
                if (settingsDialog != null) return; // 帧内重新打开了设置弹窗：由其 dismiss 时再重显
                boolean deckEditorShowing = layoutDeckEditor != null
                        && layoutDeckEditor.getVisibility() == View.VISIBLE;
                boolean gameRightShowing = layoutGameRight != null
                        && layoutGameRight.getVisibility() == View.VISIBLE;
                if (!deckEditorShowing && !gameRightShowing && !getMainMenuDialog().isShowing()) {
                    getMainMenuDialog().restoreMainMenu();
                }
            });
        });
    }
    // === Lifecycle ===

    @Override
    protected void onPause() {
        super.onPause();
        uiResumed = false;
        // 切到桌面/其他应用：暂停 BGM（不释放播放器，回前台原曲续播）
        if (soundManager != null) soundManager.pauseBGM();
    }

    @Override
    protected void onResume() {
        super.onResume();
        uiResumed = true;
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
        try {
            unregisterReceiver(screenStateReceiver);
        } catch (Exception ignored) {
            // 未注册/已注销时忽略
        }
        if (engineCallback != null) engineCallback.cancelReplayProcessing();
        DraggablePopupHelper.resetAllPositions(this);
        // 释放局域网/单机/录像选择对话框，避免持有已销毁的窗口/上下文
        //（必须真 dismiss：隐藏态 PopupWindow 随窗口销毁会 WindowLeaked）
        dismissAllLanDialogs();
        if (singleModeDialog != null) {
            singleModeDialog.dismiss();
            singleModeDialog = null;
        }
        if (replayModeDialog != null) {
            replayModeDialog.dismiss();
            replayModeDialog = null;
        }
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
        // 释放卡片详情面板资源
        if (cardDetailPanel != null) {
            cardDetailPanel.onDestroy();
        }
    }

    protected void setupBackPressedHandler() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                // 已禁用返回键响应：用户需要细化每个场景的返回键操作需求，当前保持返回键无效
                // （避免在决斗进行中意外弹出退出询问对话框，或影响其他场景的交互）
                // TODO: 后续根据用户规格细化返回键行为（主菜单/决斗中/回放中等不同场景）
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
            duelLogDialog = new DuelLogDialog((YGOProActivity) this);
        }
        duelLogDialog.toggle();
    }
}

