package cn.garymb.ygomobile.game;

import android.graphics.Bitmap;
import android.os.Handler;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.YGOProActivity;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.render.CardDetailPanel;
import cn.garymb.ygomobile.render.CardStatusTipHelper;
import cn.garymb.ygomobile.render.GameFieldView;
import cn.garymb.ygomobile.render.GameFieldViewController;
import cn.garymb.ygomobile.render.TextureLoader;
import cn.garymb.ygomobile.ui.dialogs.CardSelectDialog;
import cn.garymb.ygomobile.ui.dialogs.CmdMenuDialog;
import ocgcore.enums.DuelPhase;

/**
 * 决斗场管理类门面：卡片/区域点击与长按、卡片命令菜单、场上命令上下文、提示信息；
 * 对外公共 API 与构造签名保持不变。行为逻辑按 // === 分栏拆到同包协作类：
 * 放置/直接/合计选择会话 → FieldSelectManager；
 * 聊天/弹幕/大厅/表情 → FieldChatBoard；
 * 阶段按钮 → FieldPhaseBar。
 * 协作类经包级私有直连本门面的共享状态（engine/viewController/activity/mainHandler/topInfoManager/
 * 视图字段）与底层原语（showHint/showCardCommandMenu/dismissCmdMenu）。
 * layout_top_info 区域（双方 LP/名字/手卡数/计时/头像 + 回合数）已在 GameTopInfoManager。
 */
public class GameFieldController implements GameFieldView.OnCardClickListener {

    private static final String TAG = "YGONativeGame";
    static final int CMD_CONTEXT_IDLE = 1;
    static final int CMD_CONTEXT_BATTLE = 2;
    static final int CMD_CONTEXT_CHAIN = 3;

    final YGOProActivity activity;
    final Handler mainHandler;
    final GameTopInfoManager topInfoManager;
    GameFieldViewController viewController;
    GameEngine engine;
    /** init() 时留存：旋转重建新视图树时重新接线渲染器需要（不清场不重建引擎） */
    private ImageLoader imageLoaderRef;
    int cmdContext = 0;

    private CmdMenuDialog cmdMenuDialog;

    private TextView tvHintMessage;
    FrameLayout layoutChatMessages;
    TextView tvChatMessage1, tvChatMessage2;
    FrameLayout layoutDanmaku;
    /** 顶部信息条（gameTopInfo）：其实测高度作为相机顶部内缩（确保对方手卡不遮挡，问题1）；
     *  竖屏另经父容器 layout_hud_top 整体等比缩放，内缩量需乘缩放系数 */
    private View layoutTopInfo;
    /** 竖屏顶部 HUD 缩放容器（横屏无此节点，恒 null） */
    private View layoutHudTop;

    /** 正在显示的模态对话框集合（是/否、卡片选择/确认、命令菜单）。
     *  非空时禁用决斗场三个阶段按钮，全部隐藏后恢复。 */
    private final Set<Object> activeModalDialogs = new HashSet<>();

    // ==== 协作类（按 // === 分栏拆分，构造注入本门面引用） ====
    FieldSelectManager select;
    FieldChatBoard chat;
    FieldPhaseBar phaseBar;

    public GameFieldController(YGOProActivity activity, Handler mainHandler, GameTopInfoManager topInfoManager) {
        this.activity = activity;
        this.mainHandler = mainHandler;
        this.topInfoManager = topInfoManager;
    }

    public void create() {
        viewController = new GameFieldViewController(activity);
        select = new FieldSelectManager(this);
        chat = new FieldChatBoard(this);
        phaseBar = new FieldPhaseBar(this);
        bindChatViews();
        phaseBar.setupPhaseButtons();
        setupOverlayAnchoring();
    }

    /**
     * 屏幕旋转后重新绑定新视图树（YGOProActivity.onConfigurationChanged 已 setContentView 重载
     * layout-port/layout 变体）：只重建 viewController 与三个协作件并重新接线渲染器，
     * 引擎/通讯/GameField 卡局数据原样保留——严禁触达 hide()（会 clear 场面）。
     */
    public void rebindAfterRotation() {
        viewController = new GameFieldViewController(activity);
        select = new FieldSelectManager(this);
        // 聊天协作类跨旋转复用同一实例：myChatLines/opChatLines 是纯数据，随实例保留即保住
        // 双方聊天记录（历史新建实例导致横竖屏切换后聊天内容全丢失，用户反馈）；
        // bindChatViews 后把保存的聊天行整列回灌到新覆盖层（旧覆盖层随旋转销毁）
        if (chat == null) chat = new FieldChatBoard(this);
        phaseBar = new FieldPhaseBar(this);
        bindChatViews();
        phaseBar.setupPhaseButtons();
        setupOverlayAnchoring();
        chat.retainAcrossRotation();
        if (engine != null && imageLoaderRef != null) {
            viewController.init(engine.getField(), imageLoaderRef, this);
        }
    }

    /** 旋转重建前的收尾：关闭挂旧视图树上的命令菜单（重建后不再回显） */
    public void releaseForRebuild() {
        if (cmdMenuDialog != null) {
            cmdMenuDialog.dismiss();
            cmdMenuDialog = null;
        }
    }

    private void bindChatViews() {
        tvHintMessage = activity.findViewById(R.id.tv_hint_message);
        layoutChatMessages = activity.findViewById(R.id.layout_chat_messages);
        tvChatMessage1 = activity.findViewById(R.id.tv_chat_message_1);
        tvChatMessage2 = activity.findViewById(R.id.tv_chat_message_2);
        layoutDanmaku = activity.findViewById(R.id.layout_danmaku);
        chat.ivPlayerEmoteBubble = activity.findViewById(R.id.iv_player_emote_bubble);
        chat.ivOpponentEmoteBubble = activity.findViewById(R.id.iv_opponent_emote_bubble);
        layoutTopInfo = activity.findViewById(R.id.layout_top_info);
        layoutHudTop = activity.findViewById(R.id.layout_hud_top);
    }

    /**
     * 问题1：把 gameTopInfo 实测高度喂给相机作为顶部内缩（对方手卡不遮挡顶部条），
     * 并在相机每次重建后把聊天信息 + 中央提示文本重新锚定到「对方手卡正上方」。
     * 顶部条高度变化 / 屏幕旋转 / 折叠屏切换都会经 OnLayoutChange 与相机回调自适应。
     */
    private void setupOverlayAnchoring() {
        if (viewController == null) return;
        final View.OnLayoutChangeListener insetListener =
                (v, l, t, r, b, ol, ot, or, ob) -> updateCameraTopInset();
        if (layoutTopInfo != null) layoutTopInfo.addOnLayoutChangeListener(insetListener);
        // 竖屏顶部流容器 layout_top_stack（gameTopInfo 血条行→聊天/提示行）：聊天行数
        // 变化等把 HUD 内容撑高时靠 stack 布局变更重算（顶部 1/3 面板在 layout_game_right
        // 之外，其显隐直接改变决斗场区域尺寸并由 onSurfaceChanged 重解相机，无需内缩补偿）
        View stack = activity.findViewById(R.id.layout_top_stack);
        if (stack != null) stack.addOnLayoutChangeListener(insetListener);
        viewController.setOnCameraChangedListener(this::anchorChatAboveOpponentHand);
    }

    /** 相机顶部内缩 = layout_top_info 视觉底边（相对决斗场视图）距场顶的距离：
     *  横屏生效；竖屏决斗场已排在详情栏下方的内容区内且解算居中，传入值被 FieldCamera 忽略 */
    private void updateCameraTopInset() {
        if (viewController == null || layoutTopInfo == null) return;
        int rawH = layoutTopInfo.getHeight();
        if (rawH <= 0) return;
        float scale = layoutHudTop != null ? layoutHudTop.getScaleY() : 1f; // 现均 1，保留兼容
        int hgt;
        View field = activity.findViewById(R.id.game_field_view);
        if (field != null) {
            int[] a = new int[2];
            int[] b = new int[2];
            layoutTopInfo.getLocationInWindow(a);
            field.getLocationInWindow(b);
            hgt = (a[1] - b[1]) + (int) (rawH * scale);
        } else {
            hgt = (int) (rawH * scale);
        }
        if (hgt > 0) viewController.setTopInsetPx(hgt);
    }

    private void anchorChatAboveOpponentHand() {
        if (viewController == null || layoutChatMessages == null) return;
        // 竖屏（layout-port）聊天区在顶部流内自然位于双方血条行正下方（与横屏同构），
        // 不再叠加横屏的 translationY 动态锚定，复位后直接返回
        if (activity.getResources().getConfiguration().orientation
                == android.content.res.Configuration.ORIENTATION_PORTRAIT) {
            layoutChatMessages.setTranslationY(0f);
            return;
        }
        final float oppTopY = viewController.getOpponentHandTopScreenY();
        final int topH = layoutTopInfo != null ? layoutTopInfo.getHeight() : 0;
        layoutChatMessages.post(() -> {
            int hgt = layoutChatMessages.getHeight();
            if (hgt <= 0) return;
            // 让聊天/提示容器的底边贴到对方手卡上缘之上（容器顶基准 = 顶部条高度）
            float ty = oppTopY - topH - hgt;
            if (ty < 0f) ty = 0f;
            layoutChatMessages.setTranslationY(ty);
        });
    }

    /** 模态对话框显示——登记并禁用三个阶段按钮 */
    public void onModalDialogShown(Object dialog) {
        if (dialog == null) return;
        activeModalDialogs.add(dialog);
        updatePhaseButtonsEnabled();
    }

    /** 模态对话框隐藏——注销，集合为空时恢复三个阶段按钮 */
    public void onModalDialogHidden(Object dialog) {
        if (activeModalDialogs.remove(dialog)) {
            updatePhaseButtonsEnabled();
        }
    }

    private void updatePhaseButtonsEnabled() {
        if (viewController != null) {
            viewController.setPhaseButtonsEnabled(activeModalDialogs.isEmpty());
        }
    }

    public void init(GameEngine engine, ImageLoader imageLoader) {
        this.engine = engine;
        this.imageLoaderRef = imageLoader;
        viewController.init(engine.getField(), imageLoader, this);
    }

    public void show() {
        if (viewController != null) viewController.show();
        if (topInfoManager != null) topInfoManager.show();
        // 离场 hide() 把聊天/提示容器置 GONE，进入决斗必须恢复：提示栏 tv_hint_message
        // 寄宿其中，父容器 GONE 时 showDuelHint 的 setText+VISIBLE 全部无效
        ensureHintContainerVisible();
    }

    /** 恢复 stHintMsg 宿主容器可见（hide() 离场置 GONE 后的对称还原）；
     *  子视图（聊天行/提示栏）各自仍按自身显隐控制，容器还原不产生多余内容 */
    private void ensureHintContainerVisible() {
        if (layoutChatMessages != null && layoutChatMessages.getVisibility() != View.VISIBLE)
            layoutChatMessages.setVisibility(View.VISIBLE);
    }

    /**
     * 清场并强制重绘空场：新一场决斗/回放开始前调用，修复「进入时一开头还显示
     * 上一场 gamefieldview 的卡片局面」（MSG_START 的 field.clear() 在异步加载之后才到达）
     */
    public void resetField() {
        if (engine != null && engine.getField() != null) engine.getField().clear();
        // 兜底复位视图侧格子蚂蚁线 mask：field.clear() 不触及视图状态，
        // 上一局选格询问残留的 highlightFieldMask 会跨局继续绘制
        if (viewController != null) viewController.clearHighlight();
        if (viewController != null) viewController.invalidate();
    }

    /**
     * 决斗结束/断开连接时清除蚂蚁线显示（不清场）：复位选择态列表与卡片标记、
     * 清引擎选格残留（selectFieldMask 等），并把视图格子高亮 mask 归零、重绘。
     */
    public void clearSelectionVisuals() {
        if (engine == null) return;
        if (engine.getField() != null) engine.getField().clearSelectionVisuals();
        engine.clearCommandFlags();
        if (viewController != null) {
            viewController.clearHighlight();
            viewController.invalidate();
        }
    }

    /**
     * 撤回后中断一切进行中的场上交互（命令上下文 / 命令菜单 / 选格与选卡会话）：
     * 这些状态由已被撤销的询问建立（连锁点击模式的 cmdContext=CHAIN、灵摆/放置询问的选格会话等），
     * 而回退后的新局面会重新下发询问并按新语义重建它们；任其存活则下一次的卡片/格子点击
     * 会拿旧询问的编码去应答新局面（仅索引、选格三元组等）→ 非法应答 → MSG_RETRY 风暴。
     * 本方法只复位状态，不发出任何应答（区别于 cancelPlaceSelect / finishChainPass）。
     */
    public void abortPendingSelectSessions() {
        setCmdContext(0);
        dismissCmdMenu();
        select.abortSelectSessions();
    }

    public void hide() {
        // 决斗/回放离场即释放场面：清空 GameField 数据，下次进入不再残留上一场卡片局面
        //（showMainMenu/returnToLanMain/quitReplay 等入口均只在非决斗中状态触达本方法）
        if (engine != null && engine.getField() != null) engine.getField().clear();
        // 离场同样复位视图蚂蚁线 mask，下次进入决斗不残留上局格子高亮
        if (viewController != null) viewController.clearHighlight();
        if (viewController != null) viewController.hide();
        if (topInfoManager != null) topInfoManager.hide();
        if (layoutChatMessages != null) layoutChatMessages.setVisibility(View.GONE);
        if (cmdMenuDialog != null) cmdMenuDialog.dismiss();
        if (select.isCardSelecting) select.endCardSelect();
        hideDuelHint();
        // 清场时清空双方聊天记录/弹幕/大厅聊天/表情气泡（FieldChatBoard）
        chat.resetOnHide();
        // 清场时重置阶段按钮显示状态（FieldPhaseBar）
        phaseBar.resetOnHide();
        // 清场时重置模态对话框登记并恢复阶段按钮可用
        activeModalDialogs.clear();
        if (viewController != null) viewController.setPhaseButtonsEnabled(true);
    }

    public void invalidate() {
        viewController.invalidate();
    }

    /**
     * 动画速度倍率（对齐 gframe gameConf.quick_animation）：转发到渲染视图，
     * 缩放所有卡片移动/淡入淡出动画的帧推进量；1=原速，2=加速动画
     */
    public void setAnimationSpeed(float multiplier) {
        if (viewController != null && viewController.getView() != null)
            viewController.getView().setAnimationSpeed(multiplier);
    }

    public void selectCardWithAutoClear(int controler, int location, int sequence, int durationMs) {
        viewController.selectCardWithAutoClear(controler, location, sequence, durationMs);
    }

    void setCmdContext(int context) {
        cmdContext = context;
    }

    // === 场上命令模式（主阶/战斗阶段：直接点击场上卡片操作，不弹模态对话框） ===

    public void beginIdleCommand() {
        setCmdContext(CMD_CONTEXT_IDLE);
        if (engine == null) return;
        // 与原弹窗逻辑一致：无任何可执行操作时直接结束阶段
        if (!engine.hasIdleCommands()) {
            activity.sendResponseInt(7);
            return;
        }
        // 下一阶段按钮 BP 依通讯可用性(MSG_SELECT_IDLECMD btnBP)显示——
        // 先攻第一回合服务器不下发 btnBP，故此时不显示 BP
        phaseBar.setNextPhaseButton(engine.showBP ? "BP" : "");
        // 洗切手卡按钮（对齐 duelclient.cpp MSG_SELECT_IDLECMD L1859-1865）：
        // 通讯 show_shuffle 允许时显示，每次主阶命令重新评估
        CardDetailPanel panel = activity.getCardDetailPanel();
        if (panel != null) panel.updateShuffleButton(engine.showShuffle);
        // 通讯（MSG_SELECT_IDLE_CMD）允许进入结束阶段
        phaseBar.setEpButtonAllowed(true);
    }

    public void beginBattleCommand() {
        setCmdContext(CMD_CONTEXT_BATTLE);
        if (engine == null) return;
        if (!engine.hasBattleCommands()) {
            activity.sendResponseInt(3);
            return;
        }
        // 下一阶段按钮 M2 依通讯可用性(MSG_SELECT_BATTLECMD btnM2)显示
        phaseBar.setNextPhaseButton(engine.showM2 ? "M2" : "");
        // 战斗命令无洗切手卡（gframe 仅 IDLECMD 置可见），离开主阶即隐藏
        CardDetailPanel panel = activity.getCardDetailPanel();
        if (panel != null) panel.updateShuffleButton(false);
        // 通讯（MSG_SELECT_BATTLE_CMD）允许进入结束阶段
        phaseBar.setEpButtonAllowed(true);
    }

    /**
     * 连锁发动模式（对齐 gframe MSG_SELECT_CHAIN 非 panelmode 路径 + BUTTON_YES）：
     * 询问窗「是」后进入——场上可发动卡片已高亮（is_selectable 黄色脉冲），玩家点击高亮卡片
     * 弹出命令菜单发动，响应仅发送连锁项索引（见 CmdMenuDialog 连锁分支 / ShowDialogUtil.activateChainOption）。
     * 与 idle/battle 不同：连锁期间不启用结束阶段按钮（selectType==16 时 YGOProActivity 已关闭 EP）。
     */
    public void beginChainCommand() {
        setCmdContext(CMD_CONTEXT_CHAIN);
    }

    /** 退出连锁发动模式：复位命令上下文并关闭残留命令菜单 */
    public void endChainCommand() {
        setCmdContext(0);
        dismissCmdMenu();
    }

    // === 提示信息 ===

    /** 临时提示到时隐藏 Runnable（仅隐藏本通道文本；存续的 stHintMsg 文本到时恢复显示） */
    private Runnable hideHintRunnable;
    /** 当前存续的 stHintMsg 文本（showDuelHint 记录，hideDuelHint/下一条消息清空），
     *  供临时提示超时隐藏时恢复，不被临时知会顺带抹掉 */
    private String pendingDuelHint;

    /**
     * 临时知会类提示（撤回结果、HINT_MESSAGE、服务器知会等）：显示在 tvHintMessage
     * 原本位置，durationMs 到时自动隐藏；选择/等待类通讯提示（对齐 gframe stHintMsg，
     * 文本均来自 strings.conf 系统字符串）仍走 showDuelHint 持续显示通道，互不影响。
     * 决斗界面未显示（大厅/建主机流程）时退回 Toast，避免知会文本丢失。
     */
    public void showHint(String msg, int durationMs) {
        View gameRight = activity.findViewById(R.id.layout_game_right);
        if (tvHintMessage == null || gameRight == null || !gameRight.isShown()) {
            android.widget.Toast.makeText(activity, msg,
                    durationMs > 2000 ? android.widget.Toast.LENGTH_LONG : android.widget.Toast.LENGTH_SHORT)
                    .show();
            return;
        }
        if (hideHintRunnable == null) {
            hideHintRunnable = () -> {
                // 临时知会到期：若仍有存续的选择/等待类提示（stHintMsg）则恢复其文本，否则隐藏
                if (pendingDuelHint != null) {
                    tvHintMessage.setText(pendingDuelHint);
                } else {
                    tvHintMessage.setVisibility(View.GONE);
                }
            };
        }
        mainHandler.removeCallbacks(hideHintRunnable);
        ensureHintContainerVisible();
        tvHintMessage.setText(msg);
        tvHintMessage.setVisibility(View.VISIBLE);
        mainHandler.postDelayed(hideHintRunnable, Math.max(durationMs, 500));
    }

    /**
     * 通讯提示（对齐 gframe stHintMsg）：显示后持续，由下一条通讯消息或显式隐藏
     * （调用方 GameEngine.onGameMsg / onWaiting / onSelectXxx，见 stHintMsg 调用点）
     */
    public void showDuelHint(String text) {
        if (tvHintMessage == null) return;
        ensureHintContainerVisible();
        pendingDuelHint = text;
        tvHintMessage.setText(text);
        tvHintMessage.setVisibility(View.VISIBLE);
    }

    /** 隐藏通讯提示（对齐 duelclient.cpp ClientAnalyze 开头 stHintMsg->setVisible(false)） */
    public void hideDuelHint() {
        pendingDuelHint = null;
        if (tvHintMessage != null) tvHintMessage.setVisibility(View.GONE);
    }

    // === 卡片命令菜单 ===

    /**
     * 菜单构建逻辑已迁移至 CmdMenuDialog.showCardCommandMenu：
     * 此处仅转发触点与当前命令上下文（idle/battle）
     */
    void showCardCommandMenu(GameField.ClientCard card, float tapX, float tapY) {
        showCardCommandMenu(card, tapX, tapY, false);
    }

    void showCardCommandMenu(GameField.ClientCard card, float tapX, float tapY, boolean viewButton) {
        if (cmdMenuDialog == null) {
            cmdMenuDialog = new CmdMenuDialog(activity);
        }
        cmdMenuDialog.showCardCommandMenu(card, engine, cmdContext,
                viewController != null ? viewController.getView() : null, tapX, tapY, viewButton);
    }

    /** 本次点击无可执行命令时，关闭残留的旧菜单 */
    void dismissCmdMenu() {
        if (cmdMenuDialog != null) cmdMenuDialog.dismiss();
    }

    /** 回放态堆叠区整列表查看（FieldSelectManager 委托）：回放中无命令菜单，
     *  点双方卡组/额外/墓地/除外直接弹正面卡片列表，复用 CmdMenuDialog 的「查看」实现 */
    void showReplayPileView(GameField.ClientCard card) {
        if (cmdMenuDialog == null) {
            cmdMenuDialog = new CmdMenuDialog(activity);
        }
        cmdMenuDialog.showPileViewList(card, engine);
    }

    // === 选择会话转发（FieldSelectManager） ===

    public void beginPlaceSelect(boolean isDisfield) {
        select.beginPlaceSelect(isDisfield);
    }

    public boolean tryAutoPlaceSelect() {
        return select.tryAutoPlaceSelect();
    }

    public boolean cancelPlaceSelect() {
        return select.cancelPlaceSelect();
    }

    public void beginCardSelect(List<CardSelectDialog.CardItem> items, int min, int max, boolean cancelable) {
        select.beginCardSelect(items, min, max, cancelable);
    }

    public void beginUnselectCardSelect(List<CardSelectDialog.CardItem> items, int selectableCount,
                                        int min, int max, boolean finishable, boolean cancelable) {
        select.beginUnselectCardSelect(items, selectableCount, min, max, finishable, cancelable);
    }

    public boolean finishCardSelect() {
        return select.finishCardSelect();
    }

    public void beginSumSelect(List<CardSelectDialog.CardItem> mustItems, List<CardSelectDialog.CardItem> optItems,
                               int selectMode, int sumVal, int min, int max) {
        select.beginSumSelect(mustItems, optItems, selectMode, sumVal, min, max);
    }

    @Override
    public void onZoneClick(int player, int location, int sequence, float tapX, float tapY) {
        select.onZoneClick(player, location, sequence, tapX, tapY);
    }

    // === 聊天转发（FieldChatBoard） ===

    public void appendChat(int playerType, String message) {
        chat.appendChat(playerType, message);
    }

    /** 切换视角（观战/录像 ReplaySwap）：左右对调双方聊天内容（与 gametopinfo 昵称同步对调） */
    public void swapChatSides() {
        if (chat != null) chat.swapChatSides();
    }

    public void clearChatMessages() {
        chat.clearChatMessages();
    }

    public void enterLobbyChatMode() {
        chat.enterLobbyChatMode();
    }

    public void exitLobbyChatMode() {
        chat.exitLobbyChatMode();
    }

    // === 阶段按钮转发（FieldPhaseBar） ===

    public void setEpButtonAllowed(boolean allowed) {
        phaseBar.setEpButtonAllowed(allowed);
    }

    public void updateActionButtonsForPhase(int phase, boolean isMyTurn) {
        phaseBar.updateActionButtonsForPhase(phase, isMyTurn);
    }

    public void setPhaseByValue(int phase) {
        phaseBar.setPhaseByValue(phase);
    }

    public void setPhaseText(String text) {
        phaseBar.setPhaseText(text);
    }

    public void closePhaseButtons() {
        phaseBar.closePhaseButtons();
    }

    // === GameFieldView.OnCardClickListener ===

    @Override
    public void onCardClick(int player, int location, int sequence, float tapX, float tapY) {
        Log.d(TAG, "Card click: p=" + player + " loc=" + location + " seq=" + sequence);
        if (engine == null) return;
        if (select.isCardSelecting) {
            select.handleCardSelection(player, location, sequence);
            return;
        }
        GameField.ClientCard card = engine.getField().getCard(player, location, sequence);
        if (card == null) {
            dismissCmdMenu();
            return;
        }
        // 点击场上/手卡卡片，无论是否有可执行命令（是否弹命令菜单），
        // 都先把该卡详情显示到卡片详情面板：横屏左侧 cardDetailPanel，
        // 竖屏底部详情栏（layout-port 同 ID 结构，CardDetailPanel.showCard 自动显隐）
        activity.showCardInfoPanel(card);
        // 持有超量素材的怪兽，以及卡组/额外/墓地/除外堆叠区，弹出含「查看」的命令菜单，
        // 并把该卡在通讯中可执行的其他命令（发动/特殊召唤/攻击等）一并列出
        boolean isPile = (location == 0x01 || location == 0x40
                || location == 0x10 || location == 0x20);
        boolean xyzWithMats = (location == 0x04) && !card.overlayed.isEmpty();
        if (card.cmdFlag != 0 || isPile || xyzWithMats) {
            showCardCommandMenu(card, tapX, tapY, isPile || xyzWithMats);
            return;
        }
        // 无可执行命令：关闭残留的命令菜单（详情面板已在上方显示，手卡确认动画由场内完成）
        dismissCmdMenu();
    }

    @Override
    public void onFieldLongPress(int player, int location, int sequence, float x, float y) {
        Log.d(TAG, "Long press: p=" + player + " loc=" + location + " seq=" + sequence);
        GameField field = engine.getField();
        GameField.ClientCard card = field.getCard(player, location, sequence);
        if (card == null) return;
        // 详情面板只显卡表原始数据；通讯当前值与原始值的差异行走悬浮标签
        // （对应 gframe：ShowCardInfo(code) 原值详情 + DrawStatus/标签状态信息）
        // 横屏/竖屏统一走卡片详情面板（竖屏为底部详情栏，r2 取消旧长按小窗）
        activity.showCardInfoPanel(card);
        String tip = CardStatusTipHelper.buildStatusText(field, card);
        if (tip != null && !tip.isEmpty() && viewController != null
                && viewController.getView() != null) {
            // 投影该卡屏幕包围盒，按我方/对方决定气泡锚定到卡片上边缘还是下边缘
            float[] bounds = viewController.getView()
                    .getCardScreenBounds(player, location, sequence);
            boolean mine = (player == 0);
            CardStatusTipHelper.FieldTip.show(activity, viewController.getView(), tip, x, y, bounds, mine);
        }
    }

    @Override
    public void onFieldLongPressEnd() {
        CardStatusTipHelper.FieldTip.hide();
    }

    /** 当前是否竖屏（聊天/详情等展示形态分支保留入口，供其余分栏代码使用） */
    private boolean isPortrait() {
        return activity.getResources().getConfiguration().orientation
                == android.content.res.Configuration.ORIENTATION_PORTRAIT;
    }

    /**
     * 点击场地中央 conti_act（待效果结算）堆叠：仅在命令上下文（主阶/战斗/连锁）内有效，
     * 弹出含「效果处理」按钮的命令菜单（对齐 gframe event_handler.cpp POSITION_HINT →
     * ShowMenu(COMMAND_OPERATION) → btnOperation），后续选卡/应答见 CmdMenuDialog
     */
    @Override
    public void onContiActClick(float tapX, float tapY) {
        if (engine == null || cmdContext == 0) return;
        GameField field = engine.getField();
        if (field == null || !field.contiAct || field.contiCards.isEmpty()) return;
        if (cmdMenuDialog == null) {
            cmdMenuDialog = new CmdMenuDialog(activity);
        }
        cmdMenuDialog.showContiOperationMenu(engine, cmdContext,
                viewController != null ? viewController.getView() : null, tapX, tapY);
    }
}
