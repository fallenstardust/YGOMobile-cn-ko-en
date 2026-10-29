package cn.garymb.ygomobile.game.gamefield;
import cn.garymb.ygomobile.game.chat.FieldChatBoard;
import cn.garymb.ygomobile.game.engine.GameEngine;

import android.content.res.Configuration;
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
import cn.garymb.ygomobile.render.card.CardDetailPanel;
import cn.garymb.ygomobile.render.card.CardStatusTipHelper;
import cn.garymb.ygomobile.render.gamefieldview.GameFieldView;
import cn.garymb.ygomobile.render.gamefieldview.GameFieldViewController;
import cn.garymb.ygomobile.render.spec.SpecEffectOverlay;
import cn.garymb.ygomobile.render.field.TextureLoader;
import cn.garymb.ygomobile.ui.dialogs.duel.CardSelectDialog;
import cn.garymb.ygomobile.ui.dialogs.duel.CmdMenuDialog;
import cn.garymb.ygomobile.utils.ImePopupAdjuster;
import ocgcore.enums.DuelPhase;


/**
 * Base layer of the split inheritance chain (GameFieldControllerBase <- GameFieldController):
 * owns the shared facade state, the chat-view binding, camera inset anchoring,
 * the hint overlay pipeline, the card command menu and the field status tip.
 * The collaborator construction (new FieldSelectManager(this) etc.) and the
 * command/select/chat forwarding entry points live in the leaf class.
 */
abstract class GameFieldControllerBase implements GameFieldView.OnCardClickListener {

    protected static final String TAG = "YGONativeGame";
    static final int CMD_CONTEXT_IDLE = 1;
    static final int CMD_CONTEXT_BATTLE = 2;
    static final int CMD_CONTEXT_CHAIN = 3;

    public final YGOProActivity activity;
    public final Handler mainHandler;
    public final GameTopInfoManager topInfoManager;
    GameFieldViewController viewController;
    public GameEngine engine;
    /** init() 时留存：旋转重建新视图树时重新接线渲染器需要（不清场不重建引擎） */
    protected ImageLoader imageLoaderRef;
    int cmdContext = 0;

    protected CmdMenuDialog cmdMenuDialog;

    protected TextView tvHintMessage;
    /** 当前投递到覆盖层提示带的文本与在屏标志：旋转/布局变化后按新 HUD 底边重定位用 */
    protected String shownHintText;
    protected boolean hintShown;
    public FrameLayout layoutChatMessages;
    public TextView tvChatMessage1, tvChatMessage2;
    public FrameLayout layoutDanmaku;
    /** 顶部信息条（gameTopInfo）：其实测高度作为相机顶部内缩（确保对方手卡不遮挡，问题1）；
     *  竖屏另经父容器 layout_hud_top 整体等比缩放，内缩量需乘缩放系数 */
    protected View layoutTopInfo;
    /** 竖屏顶部 HUD 缩放容器（横屏无此节点，恒 null） */
    protected View layoutHudTop;
    /** 聊天输入框（et_chat_input）：横屏浮于决斗场底部，其顶边作为相机纵向下锚线，
     *  使决斗场/场上卡片/我方手卡整体下移到输入框上方而不被遮挡 */
    protected View etChatInput;

    /** 正在显示的模态对话框集合（是/否、卡片选择/确认、命令菜单）。
     *  非空时禁用决斗场三个阶段按钮，全部隐藏后恢复。 */
    protected final Set<Object> activeModalDialogs = new HashSet<>();

    // ==== 协作类（按 // === 分栏拆分，构造注入本门面引用） ====
    FieldSelectManager select;
    FieldChatBoard chat;
    FieldPhaseBar phaseBar;

    /**
     * Constructor body anchor kept verbatim so the final fields are
     * assigned exactly once from the leaf forwarding constructor.
     */
    protected GameFieldControllerBase(YGOProActivity activity, Handler mainHandler, GameTopInfoManager topInfoManager) {
        this.activity = activity;
        this.mainHandler = mainHandler;
        this.topInfoManager = topInfoManager;
    }

    protected void bindChatViews() {
        tvHintMessage = activity.findViewById(R.id.tv_hint_message);
        // 提示栏已迁至 SpecEffectOverlay 覆盖层（不被输入法顶高，且与对方手卡重叠时浮于其上）：
        // 布局内旧 tv_hint_message 恒 GONE，使 layout_chat_messages 塌缩为 0 高——相机上锚线不再
        // 为其预留顶部提示带，决斗场整体放大（横竖屏对称，用户规格）
        if (tvHintMessage != null) tvHintMessage.setVisibility(View.GONE);
        layoutChatMessages = activity.findViewById(R.id.layout_chat_messages);
        tvChatMessage1 = activity.findViewById(R.id.tv_chat_message_1);
        tvChatMessage2 = activity.findViewById(R.id.tv_chat_message_2);
        layoutDanmaku = activity.findViewById(R.id.layout_danmaku);
        chat.ivPlayerEmoteBubble = activity.findViewById(R.id.iv_player_emote_bubble);
        chat.ivOpponentEmoteBubble = activity.findViewById(R.id.iv_opponent_emote_bubble);
        layoutTopInfo = activity.findViewById(R.id.layout_top_info);
        layoutHudTop = activity.findViewById(R.id.layout_hud_top);
        etChatInput = activity.findViewById(R.id.et_chat_input);
    }

    /**
     * 把 gameTopInfo 实测高度（血条行 + 其下聊天/提示行）喂给相机作为顶部内缩（上锚线），
     * 并以下锚线（聊天输入框顶边）把决斗场整体下移，使提示文本始终落在 HUD 保留带内。
     * 顶部条/输入框高度变化 / 屏幕旋转 / 折叠屏切换均经 OnLayoutChange 重算。
     * <p>
     * 用户规格：提示栏必须保持在 gameTopInfo 布局位置，不跟随对方手卡升降，也不得因其
     * 显隐而改变决斗场取景——故本方法只接线「与提示显隐无关的恒定量」：聊天/提示行不再
     * 做任何 translationY 动态锚定（旧 anchorChatAboveOpponentHand 已删除），行高恒定的前提
     * 则由提示栏恒占位（隐藏用 INVISIBLE）+ XML 固定高度保证（见 layout(_port)layout_game_right）
     */
    protected void setupOverlayAnchoring() {
        if (viewController == null) return;
        final View.OnLayoutChangeListener insetListener = (v, l, t, r, b, ol, ot, or, ob) -> {
            updateCameraTopInset();
            updateCameraBottomInset();
            // 血条/HUD/输入框重布局（旋转、折叠屏、输入法升降）后，把血条下方各带（聊天行/
            // 弹幕带/提示带）按新的实测血条位置重锚
            repinHudBands();
        };
        if (layoutTopInfo != null) layoutTopInfo.addOnLayoutChangeListener(insetListener);
        // 提示栏已迁至覆盖层，layout_chat_messages 塌缩为 0 高不再参与上锚线，无需监听其布局变化
        View stack = activity.findViewById(R.id.layout_top_stack);
        if (stack != null) stack.addOnLayoutChangeListener(insetListener);
        if (etChatInput != null) etChatInput.addOnLayoutChangeListener(insetListener);
    }

    // === 血条下方各带的重锚（聊天输入框降回静置位后归位，用户规格）===

    /** 重锚落定等待的轮询间隔与上限：键盘收起动画约 300ms，输入框实测上沿落定前不采信新锚点 */
    protected static final long REPIN_RETRY_MS = 120L;
    protected static final int REPIN_MAX_RETRIES = 8;
    /** 本次重锚触发已用去的落定等待轮数 */
    protected int repinRetries;
    /** 落定等待中的重锚任务（先 remove 再 post，多个触发点只保留一轮排队） */
    protected final Runnable repinBandsTask = this::doRepinHudBands;

    /**
     * 触发一次血条下方各带重锚（HUD/输入框布局变化与输入法收起事件都经此）：复位等待轮数后
     * 先解一次，未落定则由 {@link #doRepinHudBands()} 自行续排。
     */
    public void repinHudBands() {
        repinRetries = 0;
        mainHandler.removeCallbacks(repinBandsTask);
        doRepinHudBands();
    }

    /**
     * 把血条下方各带重锚到当前实测血条位置（用户反馈：抬键盘期间发出的聊天顶在屏幕顶端显示，
     * 输入框降回、血条回到原处后不回位）。
     * <p>仅当聊天输入框已几何落在静置位才执行：否则（仍抬高或键盘动画途中）按
     * {@link #REPIN_RETRY_MS} 追加有限轮等待——把被键盘压缩的中间态采成锚点正是“再不回位”的
     * 成因，宁可多等几轮也不采错值；抬高期间覆盖层自行沿用静置锚点，本方法不参与。
     * <p>各带仅在错位在位时重建（覆盖层 {@code getBandTop} 比对），不会随每次布局变化重排。
     */
    protected void doRepinHudBands() {
        if (!ImePopupAdjuster.isChatInputAtRest()) {
            if (repinRetries < REPIN_MAX_RETRIES) {
                repinRetries++;
                mainHandler.removeCallbacks(repinBandsTask);
                mainHandler.postDelayed(repinBandsTask, REPIN_RETRY_MS);
            }
            return;
        }
        repinRetries = 0;
        // 提示带锚点也随时回灌（与是否正在显示无关）：抬高期间第一条提示才有静置基准可用
        SpecEffectOverlay overlay = activity.getSpecOverlay();
        if (overlay != null) {
            overlay.rememberBandAnchor(SpecEffectOverlay.BAND_HINT, hintBandRectInWindow()[1]);
        }
        if (chat != null) chat.repinFieldBands();
        // 提示带：抬高期间无静置锚点时可能落在临时锚点上，降回后按新的 HUD 底边重下发
        if (hintShown) pushHintToOverlay(shownHintText);
    }

    /**
     * 相机顶部内缩 = 顶部 HUD 视觉底边（相对决斗场视图）距场顶的距离：包含血条行与其下方的
     * 聊天/提示行（tv_hint_message）。GameFieldView 为 setZOrderOnTop(true) 的 GL 曲面，不透的
     * 底板与手卡会把该行的普通 View 整层盖住（旧实现只量血条行 → 提示文本常被对方手卡遮挡），
     * 现横竖屏相机均以此作上锚线。该锚线只由血条面板高度与提示带固定高决定，与提示文本
     * 是否正在显示无关，因而不再出现「提示一显示决斗场就缩小/下移、一隐藏又弹回」的晃动
     */
    protected void updateCameraTopInset() {
        if (viewController == null || layoutTopInfo == null) return;
        View field = activity.findViewById(R.id.game_field_view);
        if (field == null || field.getHeight() <= 0) return;
        int[] b = new int[2];
        field.getLocationInWindow(b);
        int hgt = visualBottomInField(layoutTopInfo, b[1]);
        // 竖屏 HUD 整体包在 layout_hud_top（血条行）内，直接取其实测底边；
        // 提示栏已迁至覆盖层，layout_chat_messages 塌缩为 0 高，不再计入上锚线（决斗场因此放大）
        hgt = Math.max(hgt, visualBottomInField(layoutHudTop, b[1]));
        if (hgt > 0) viewController.setTopInsetPx(hgt);
    }

    /**
     * 视图在决斗场视图坐标系内的底边（相对场顶，像素）；仅 GONE（真正退出布局、不占位）
     * 或未测量时返回 0。INVISIBLE 必须照样计入：它仍占着布局空间，相机不为其保留就会把
     * 卡片画在这段空白上，等视图转 VISIBLE 时再重解取景 —— 正是提示栏显隐引起决斗场晃动的成因
     */
    protected int visualBottomInField(View v, int fieldTopInWindow) {
        if (v == null || v.getHeight() <= 0 || v.getVisibility() == View.GONE) return 0;
        int[] p = new int[2];
        v.getLocationInWindow(p);
        return (p[1] - fieldTopInWindow) - (int) v.getTranslationY() + v.getHeight();
    }

    /** 相机底部内缩（纵向下锚线）：= 决斗场视图底边→聊天输入框顶边的距离，使决斗场整体下移到
     *  输入框上方而不遮挡输入框。横屏（用户规格）留 0 间隙使我方手卡底缘紧贴输入框上边缘（不再下探
     *  遮住输入框）；竖屏保持不变仍留 4dp 使手卡略高于输入框。与上锚线同一原则：
     *  只有真正退出布局的 GONE 才取消保留；输入框/场视图不存在时内缩归零 */
    protected void updateCameraBottomInset() {
        if (viewController == null) return;
        View field = activity.findViewById(R.id.game_field_view);
        if (field == null || field.getHeight() <= 0
                || etChatInput == null
                || etChatInput.getVisibility() == View.GONE
                || etChatInput.getHeight() <= 0) {
            viewController.setBottomInsetPx(0f);
            return;
        }
        int[] a = new int[2];
        int[] b = new int[2];
        etChatInput.getLocationInWindow(a);
        field.getLocationInWindow(b);
        int inset = (b[1] + field.getHeight()) - a[1];
        if (inset <= 0) {
            viewController.setBottomInsetPx(0f);
            return;
        }
        boolean landscape = activity.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        // 横屏：gap=0 → inset 恰为输入框顶边到场底的距离，手卡底缘紧贴输入框上边缘且不遮挡；
        // 竖屏：保持原有 4dp 间隙不变
        float gap = landscape ? 0f
                : 4f * activity.getResources().getDisplayMetrics().density;
        viewController.setBottomInsetPx(inset + gap);
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

    protected void updatePhaseButtonsEnabled() {
        if (viewController != null) {
            viewController.setPhaseButtonsEnabled(activeModalDialogs.isEmpty());
        }
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
    protected void ensureHintContainerVisible() {
        if (layoutChatMessages != null && layoutChatMessages.getVisibility() != View.VISIBLE)
            layoutChatMessages.setVisibility(View.VISIBLE);
    }

    void setCmdContext(int context) {
        cmdContext = context;
    }

    // === 提示信息 ===

    /** 临时提示到时隐藏 Runnable（仅隐藏本通道文本；存续的 stHintMsg 文本到时恢复显示） */
    protected Runnable hideHintRunnable;
    /** 当前存续的 stHintMsg 文本（showDuelHint 记录，hideDuelHint/下一条消息清空），
     *  供临时提示超时隐藏时恢复，不被临时知会顺带抹掉 */
    protected String pendingDuelHint;

    /**
     * 临时知会类提示（撤回结果、HINT_MESSAGE、服务器知会等）：显示在 tvHintMessage
     * 原本位置，durationMs 到时自动隐藏；选择/等待类通讯提示（对齐 gframe stHintMsg，
     * 文本均来自 strings.conf 系统字符串）仍走 showDuelHint 持续显示通道，互不影响。
     * 决斗界面未显示（大厅/建主机流程）时退回 Toast，避免知会文本丢失。
     */
    public void showHint(String msg, int durationMs) {
        View gameRight = activity.findViewById(R.id.layout_game_right);
        if (gameRight == null || !gameRight.isShown()) {
            android.widget.Toast.makeText(activity, msg,
                    durationMs > 2000 ? android.widget.Toast.LENGTH_LONG : android.widget.Toast.LENGTH_SHORT)
                    .show();
            return;
        }
        if (hideHintRunnable == null) {
            hideHintRunnable = () -> {
                // 临时知会到期：若仍有存续的选择/等待类提示（stHintMsg）则恢复其文本，否则隐藏
                if (pendingDuelHint != null && !pendingDuelHint.isEmpty()) {
                    pushHintToOverlay(pendingDuelHint);
                } else {
                    pullHintFromOverlay();
                }
            };
        }
        mainHandler.removeCallbacks(hideHintRunnable);
        pushHintToOverlay(msg);
        mainHandler.postDelayed(hideHintRunnable, Math.max(durationMs, 500));
    }

    public void showDuelHint(String text) {
        pendingDuelHint = text;
        if (text == null || text.isEmpty()) {
            pullHintFromOverlay();
        } else {
            pushHintToOverlay(text);
        }
    }

    public void hideDuelHint() {
        pendingDuelHint = null;
        pullHintFromOverlay();
    }

    /**
     * 把提示文本投递到 SpecEffectOverlay 提示带（显示在 GL 曲面之上，随窗口 SOFT_INPUT_ADJUST_NOTHING
     * 不被输入法顶高）：顶边取当前顶部 HUD 底边（＝原 tv_hint_message 所在带，保持“相同位置”），
     * 字号随横竖屏切换（10sp / 12sp）。空文本等价于隐藏。
     */
    protected void pushHintToOverlay(String text) {
        if (text == null || text.isEmpty()) {
            pullHintFromOverlay();
            return;
        }
        SpecEffectOverlay overlay = activity.obtainSpecOverlay();
        if (overlay == null) return;
        boolean landscape = activity.getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
        int[] band = hintBandRectInWindow();
        overlay.showHint(text, band[0], band[1], band[2], landscape ? 10f : 12f);
        shownHintText = text;
        hintShown = true;
    }

    /** 收起覆盖层提示带 */
    protected void pullHintFromOverlay() {
        SpecEffectOverlay overlay = activity.getSpecOverlay();
        if (overlay != null) overlay.hideHint();
        shownHintText = null;
        hintShown = false;
    }

    /**
     * 提示带在窗口坐标的矩形 int[]{left, top(=底边), width}：取 layout_top_info（与内层决斗场 FrameLayout
     * 同宽，横屏不跨左侧六按钮列）的左/底边/宽，使覆盖层提示与原布局 tv_hint_message 逐像素对齐
     * （居中基准=场地区而非全屏）；未布局时回退双方血条矩形并集。
     */
    protected int[] hintBandRectInWindow() {
        if (layoutTopInfo != null && layoutTopInfo.getWidth() > 0 && layoutTopInfo.getHeight() > 0) {
            int[] p = new int[2];
            layoutTopInfo.getLocationInWindow(p);
            return new int[]{p[0], p[1] + layoutTopInfo.getHeight(), layoutTopInfo.getWidth()};
        }
        int[] me = topInfoManager != null ? topInfoManager.getLpBarRectInWindow(0) : null;
        int[] op = topInfoManager != null ? topInfoManager.getLpBarRectInWindow(1) : null;
        if (me != null && op != null && me[2] > 0 && op[2] > 0) {
            int left = Math.min(me[0], op[0]);
            int right = Math.max(me[0] + me[2], op[0] + op[2]);
            int top = Math.max(me[1] + me[3], op[1] + op[3]);
            return new int[]{left, top, right - left};
        }
        return new int[]{0, 0, 0};
    }

    // === 卡片命令菜单 ===

    /**
     * 菜单构建逻辑已迁移至 CmdMenuDialog.showCardCommandMenu：
     * 此处仅转发触点与当前命令上下文（idle/battle）
     */
    void showCardCommandMenu(ClientCard card, float tapX, float tapY) {
        showCardCommandMenu(card, tapX, tapY, false);
    }

    void showCardCommandMenu(ClientCard card, float tapX, float tapY, boolean viewButton) {
        // 谜题（CARD_QUESTION）发动至效果处理完毕期间禁止本端查墓（对齐 event_handler.cpp
        // L1303/L1355 墓地 LOCATION_GRAVE 分支 `if(cant_check_grave) break;`）：本次点击卡位于墓地
        //（0x10）且本端处于 cantCheckGrave 态时直接早退，不弹 cmdmenudialog、不刷新详情（等价 C++ 作废该次点击）
        if (card != null && card.location == 0x10
                && engine != null && engine.getField() != null && engine.getField().cantCheckGrave) {
            return;
        }
        if (cmdMenuDialog == null) {
            cmdMenuDialog = new CmdMenuDialog(activity);
        }
        cmdMenuDialog.showCardCommandMenu(card, engine, cmdContext,
                viewController != null ? viewController.getView() : null, tapX, tapY, viewButton);
        // 菜单优先渲染：详情面板刷新（含关键词可查判定的全卡表扫描）延后到下一帧，
        // 不再在菜单弹出前同步执行（菜单变慢的主因），所有调用路径经本转发壳统一延后
        GameFieldView fieldView = viewController != null ? viewController.getView() : null;
        if (fieldView != null) {
            fieldView.post(() -> activity.showCardInfoPanel(card));
        } else {
            activity.showCardInfoPanel(card);
        }
    }

    /** 本次点击无可执行命令时，关闭残留的旧菜单 */
    void dismissCmdMenu() {
        if (cmdMenuDialog != null) cmdMenuDialog.dismiss();
    }

    /** 回放态堆叠区整列表查看（FieldSelectManager 委托）：回放中无命令菜单，
     *  点双方卡组/额外/墓地/除外直接弹正面卡片列表，复用 CmdMenuDialog 的「查看」实现 */
    void showReplayPileView(ClientCard card) {
        if (cmdMenuDialog == null) {
            cmdMenuDialog = new CmdMenuDialog(activity);
        }
        cmdMenuDialog.showPileViewList(card, engine);
    }

    /** 排期展示点击气泡：取代际后延后一帧执行，期间任何隐藏使本次排期作废 */
    protected void scheduleFieldStatusTip(GameFieldView fieldView, ClientCard card,
                                        int player, int location, int sequence,
                                        float tapX, float tapY) {
        final long gen = ++tipGeneration;
        if (fieldView != null) {
            fieldView.post(() -> showFieldStatusTip(gen, card, player, location, sequence, tapX, tapY));
        } else {
            showFieldStatusTip(gen, card, player, location, sequence, tapX, tapY);
        }
    }

    /** 状态气泡延后显示的代际：期间任何「点击卡片以外」的隐藏都会自增，使过期气泡作废 */
    protected long tipGeneration;

    /** 隐藏场上/手卡点击气泡（public：CmdMenuDialog 点菜单以外关闭时联动收起） */
    public void hideFieldStatusTip() {
        tipGeneration++;
        CardStatusTipHelper.FieldTip.hide();
    }

    /** 点击卡片后的状态气泡：无状态文字/投影失败时收起旧标签，有则直接替换（show 内部先 hide） */
    protected void showFieldStatusTip(long gen, ClientCard card, int player,
                                    int location, int sequence, float tapX, float tapY) {
        if (gen != tipGeneration) return;   // 延后期间已被其他点击取代
        GameFieldView fieldView = viewController != null ? viewController.getView() : null;
        GameField field = engine != null ? engine.getField() : null;
        String tip = CardStatusTipHelper.buildStatusText(field, card);
        float[] bounds = fieldView != null
                ? fieldView.getCardScreenBounds(player, location, sequence) : null;
        if (tip == null || tip.isEmpty() || bounds == null) {
            hideFieldStatusTip();
            return;
        }
        CardStatusTipHelper.FieldTip.show(activity, fieldView, tip, tapX, tapY, bounds, player == 0);
    }

    /** 当前是否竖屏（聊天/详情等展示形态分支保留入口，供其余分栏代码使用） */
    protected boolean isPortrait() {
        return activity.getResources().getConfiguration().orientation
                == android.content.res.Configuration.ORIENTATION_PORTRAIT;
    }
}
