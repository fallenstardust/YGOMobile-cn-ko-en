package cn.garymb.ygomobile.game;

import android.graphics.Bitmap;
import android.os.Handler;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
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
import ocgcore.DataManager;
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
    int cmdContext = 0;

    private CmdMenuDialog cmdMenuDialog;

    private TextView tvHintMessage;
    FrameLayout layoutChatMessages;
    TextView tvChatMessage1, tvChatMessage2;
    FrameLayout layoutDanmaku;
    /** 顶部信息条（gameTopInfo）：其实测高度作为相机顶部内缩量，确保对方手卡不遮挡（问题1） */
    private View layoutTopInfo;

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

    private void bindChatViews() {
        tvHintMessage = activity.findViewById(R.id.tv_hint_message);
        layoutChatMessages = activity.findViewById(R.id.layout_chat_messages);
        tvChatMessage1 = activity.findViewById(R.id.tv_chat_message_1);
        tvChatMessage2 = activity.findViewById(R.id.tv_chat_message_2);
        layoutDanmaku = activity.findViewById(R.id.layout_danmaku);
        chat.ivPlayerEmoteBubble = activity.findViewById(R.id.iv_player_emote_bubble);
        chat.ivOpponentEmoteBubble = activity.findViewById(R.id.iv_opponent_emote_bubble);
        layoutTopInfo = activity.findViewById(R.id.layout_top_info);
    }

    /**
     * 问题1：把 gameTopInfo 实测高度喂给相机作为顶部内缩（对方手卡不遮挡顶部条），
     * 并在相机每次重建后把聊天信息 + 中央提示文本重新锚定到「对方手卡正上方」。
     * 顶部条高度变化 / 屏幕旋转 / 折叠屏切换都会经 OnLayoutChange 与相机回调自适应。
     */
    private void setupOverlayAnchoring() {
        if (viewController == null) return;
        if (layoutTopInfo != null) {
            layoutTopInfo.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
                int hgt = v.getHeight();
                if (hgt > 0) viewController.setTopInsetPx(hgt);
            });
        }
        viewController.setOnCameraChangedListener(this::anchorChatAboveOpponentHand);
    }

    private void anchorChatAboveOpponentHand() {
        if (viewController == null || layoutChatMessages == null) return;
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
        viewController.init(engine.getField(), imageLoader, this);
    }

    public void show() {
        if (viewController != null) viewController.show();
        if (topInfoManager != null) topInfoManager.show();
    }

    public void hide() {
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
        // 通讯（MSG_SELECT_IDLE_CMD）允许进入结束阶段
        phaseBar.setEpButtonAllowed(true);
        showHint("点击手牌或场上卡片进行操作", 2500);
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
        // 通讯（MSG_SELECT_BATTLE_CMD）允许进入结束阶段
        phaseBar.setEpButtonAllowed(true);
        showHint("点击卡片进行攻击或发动", 2500);
    }

    /**
     * 连锁发动模式（对齐 gframe MSG_SELECT_CHAIN 非 panelmode 路径 + BUTTON_YES）：
     * 询问窗「是」后进入——场上可发动卡片已高亮（is_selectable 黄色脉冲），玩家点击高亮卡片
     * 弹出命令菜单发动，响应仅发送连锁项索引（见 CmdMenuDialog 连锁分支 / ShowDialogUtil.activateChainOption）。
     * 与 idle/battle 不同：连锁期间不启用结束阶段按钮（selectType==16 时 YGOProActivity 已关闭 EP）。
     */
    public void beginChainCommand() {
        setCmdContext(CMD_CONTEXT_CHAIN);
        showHint("点击场上高亮的卡片发动效果", 2500);
    }

    /** 退出连锁发动模式：复位命令上下文并关闭残留命令菜单 */
    public void endChainCommand() {
        setCmdContext(0);
        dismissCmdMenu();
    }

    // === 提示信息 ===

    /** 提示定时隐藏任务：showHint 与 showDuelHint 共用，避免多次延时任务叠加导致提前隐藏 */
    private final Runnable hideHintRunnable = () -> {
        if (tvHintMessage != null) tvHintMessage.setVisibility(View.GONE);
    };

    public void showHint(String msg, int durationMs) {
        mainHandler.removeCallbacks(hideHintRunnable);
        tvHintMessage.setText(msg);
        tvHintMessage.setVisibility(View.VISIBLE);
        mainHandler.postDelayed(hideHintRunnable, durationMs);
    }

    /**
     * 通讯提示（对齐 gframe stHintMsg）：显示后持续，由下一条通讯消息或显式隐藏
     * （调用方 GameEngine.onGameMsg / onWaiting / onSelectXxx，见 stHintMsg 调用点）
     */
    public void showDuelHint(String text) {
        mainHandler.removeCallbacks(hideHintRunnable);
        tvHintMessage.setText(text);
        tvHintMessage.setVisibility(View.VISIBLE);
    }

    /** 隐藏通讯提示（对齐 duelclient.cpp ClientAnalyze 开头 stHintMsg->setVisible(false)） */
    public void hideDuelHint() {
        mainHandler.removeCallbacks(hideHintRunnable);
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
        // 都先把该卡详情显示到左侧 cardDetailPanel
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
}
