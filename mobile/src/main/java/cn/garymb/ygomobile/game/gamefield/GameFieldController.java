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
public class GameFieldController extends GameFieldControllerBase {

    /**
     * Constructor (public entry of the split chain; the assignment lives
     * in the base layer).
     */
    public GameFieldController(YGOProActivity activity, Handler mainHandler, GameTopInfoManager topInfoManager) {
        super(activity, mainHandler, topInfoManager);
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
        // 选择会话协作类跨旋转复用同一实例（对齐 chat/phaseBar 的保留策略）：isPlaceSelecting /
        // isCardSelecting / isSumSelecting / 已选累计位等都是纯会话状态，新建实例会把进行中的
        // 选格/选卡会话清零——决斗中横竖屏切换后高亮格子消失、且点击不再被路由到选择处理
        //（远程联机无法撤回 → 彻底卡死无法继续操作）。随实例保留会话，下面再把选格高亮 mask
        // 回灌到新视图即可无缝续用（卡片可选轮廓源自 field 级 is_selectable，本随场面数据保留）
        if (select == null) select = new FieldSelectManager(this);
        // 聊天协作类跨旋转复用同一实例：myChatLines/opChatLines 是纯数据，随实例保留即保住
        // 双方聊天记录（历史新建实例导致横竖屏切换后聊天内容全丢失，用户反馈）；
        // bindChatViews 后把保存的聊天行整列回灌到新覆盖层（旧覆盖层随旋转销毁）
        if (chat == null) chat = new FieldChatBoard(this);
        // 阶段按钮协作类同样跨旋转复用同一实例：phaseCurrentVisible/Label、phaseNextLabel、
        // phaseEpVisible 是通讯驱动的纯显示状态，旋转时服务端正等玩家应答、不会再下发
        // MSG_SELECT_IDLE/BATTLECMD 或阶段切换，新建实例把它们清零后永无重新推送点——
        // 决斗中横竖屏切换阶段按钮消失且再转也不回来（用户反馈），随实例保留即回显如旧
        if (phaseBar == null) phaseBar = new FieldPhaseBar(this);
        bindChatViews();
        // 点击监听重挂到新 viewController（旧视图树随 setContentView 销毁，委托不随实例保留）
        phaseBar.setupPhaseButtons();
        setupOverlayAnchoring();
        chat.retainAcrossRotation();
        // 旋转后覆盖层为新实例：若存续选择/等待类提示（stHintMsg）则重投到新层并按新 HUD 底边定位
        if (pendingDuelHint != null && !pendingDuelHint.isEmpty()) pushHintToOverlay(pendingDuelHint);
        if (engine != null && imageLoaderRef != null) {
            viewController.init(engine.getField(), imageLoaderRef, this);
        }
        // 回灌旋转前的选格高亮：视图态 highlightFieldMask 随旧 GameFieldView 销毁归零，
        // 而询问的 selectFieldMask 存活于引擎——选格会话进行中则按同一 mask 重推给新视图
        if (select.isPlaceSelecting && engine != null) {
            viewController.highlightField(engine.selectFieldMask);
        }
        // 回灌旋转前的阶段按钮显示状态到新 GameFieldView，并重放模态禁用登记
        //（activeModalDialogs 在控制器层跨旋转保留，新视图默认 enabled 需按登记重置）
        phaseBar.pushPhaseDisplay();
        updatePhaseButtonsEnabled();
    }

    /** 旋转重建前的收尾：关闭挂旧视图树上的命令菜单（重建后不再回显） */
    public void releaseForRebuild() {
        if (cmdMenuDialog != null) {
            cmdMenuDialog.dismiss();
            cmdMenuDialog = null;
        }
    }

    public void init(GameEngine engine, ImageLoader imageLoader) {
        this.engine = engine;
        this.imageLoaderRef = imageLoader;
        viewController.init(engine.getField(), imageLoader, this);
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
        // 退出对战（layout_game_right 隐藏）一并收起场上/手卡状态悬浮气泡：它是独立全屏窗口，
        // 不随决斗场视图显隐销毁，决斗结束时若不主动 dismiss 会残留浮在返回界面上
        hideFieldStatusTip();
        if (topInfoManager != null) topInfoManager.hide();
        if (layoutChatMessages != null) layoutChatMessages.setVisibility(View.GONE);
        if (cmdMenuDialog != null) cmdMenuDialog.dismiss();
        if (select.isCardSelecting) select.endCardSelect();
        // 离场：取消未触发的临时提示到时隐藏，收起覆盖层提示带（hideDuelHint 会 pullHintFromOverlay）
        if (hideHintRunnable != null) mainHandler.removeCallbacks(hideHintRunnable);
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

    /**
     * 回放「上一步」逆向倒放渲染冻结（对齐 {@code GameEngine.EngineListener.onReplayRenderHold}）：
     * hold=true 冻结 GL 渲染（重算 N-1 权威态期间保留当前帧不上屏），hold=false 恢复逐帧驱动逆向补间。
     */
    public void setRenderHold(boolean hold) {
        if (viewController != null && viewController.getView() != null)
            viewController.getView().setRenderHold(hold);
    }

    public void selectCardWithAutoClear(int controler, int location, int sequence, int durationMs) {
        viewController.selectCardWithAutoClear(controler, location, sequence, durationMs);
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
        // 点到格子（非卡片命中）属于「卡片以外」：收起状态悬浮标签
        hideFieldStatusTip();
        select.onZoneClick(player, location, sequence, tapX, tapY);
    }

    @Override
    public void onFieldBlankClick(float tapX, float tapY) {
        // 点击卡片以外的位置（空白处/场内阶段按钮/无卡区域）：隐藏状态悬浮标签
        hideFieldStatusTip();
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

    /** 录像打开即全部可见：把聊天时间线全部 0xF1 帧体一次性预载入消息记录·聊天tab（主线程转发 FieldChatBoard） */
    public void preloadReplayChat(java.util.List<byte[]> bodies) {
        if (chat != null) chat.preloadReplayChat(bodies);
    }

    public void enterLobbyChatMode() {
        chat.enterLobbyChatMode();
    }

    public void exitLobbyChatMode() {
        chat.exitLobbyChatMode();
    }

    /**
     * 全局触摸转发给聊天协作类，用于决斗内「长按聊天行/弹幕复制、按住弹幕减速」的命中观察。
     * 聊天行/弹幕宿主为事件穿透的透明弹窗（setTouchable(false)），收不到触摸，故由 Activity
     * 的 dispatchTouchEvent 非消费式转发（不影响普通点按继续穿透到决斗场）。由 YGOProActivity 调用。
     */
    public void onFieldTouchForChat(android.view.MotionEvent ev) {
        if (chat != null) chat.onFieldTouch(ev);
    }

    // === 阶段按钮转发（FieldPhaseBar） ===

    public void setEpButtonAllowed(boolean allowed) {
        phaseBar.setEpButtonAllowed(allowed);
    }

    /**
     * 应答离场即时收起指令按钮（对齐 duelclient.cpp DuelClient::SendResponse L4507-4520）：
     * 战斗指令(10)应答收 M2/EP，主阶指令(11)应答再收 BP/EP/洗切；其余询问类型不触碰
     */
    public void onCommandResponded(int selectType) {
        if (selectType != 10 && selectType != 11) return;
        phaseBar.hideNextAndEpButtons();
        if (selectType == 11) {
            // 洗切手卡与 btnBP/btnEP 同属 IDLECMD 应答收起集（gframe btnShuffle 同点隐藏）
            CardDetailPanel panel = activity.getCardDetailPanel();
            if (panel != null) panel.updateShuffleButton(false);
        }
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
        GameFieldView fieldView = viewController != null ? viewController.getView() : null;
        if (select.isCardSelecting) {
            // 先于会话应答捕获被点击卡与点击瞬间的「可选」状态：只选 1 张的会话（超量叠放、
            // 单对象选取等）本次点击即自动确认，endCardSelect 的 clearSelect 会把候选的
            // is_selectable 洗回 false——若拿会话后的状态判断是否弹菜单，点击候选超量怪兽会被
            // 误判为「非候选且持素材」而误弹命令菜单（gframe MSG_SELECT_CARD 的 ClickCard
            // L1459-1492：候选卡点击只切换选中/应答，从不 ShowMenu）
            ClientCard picked = engine.getField().getCard(player, location, sequence);
            final boolean wasSelectable = picked != null && picked.is_selectable;
            select.handleCardSelection(player, location, sequence);
            // 场上直接选卡的点击同样展示状态气泡（与旧长按可见性持平）；
            // 堆叠区（卡组/墓地/除外/额外）不展示顶卡气泡——C++ 悬停 stTip 的 stat 段同样
            // 仅 location & 0xe（手卡/怪区/魔陷区，event_handler.cpp L1675）才拼接，堆叠区无标签
            if (picked != null) {
                boolean pickedPile = location == 0x01 || location == 0x40
                        || location == 0x10 || location == 0x20;
                if (pickedPile) {
                    hideFieldStatusTip();
                } else {
                    scheduleFieldStatusTip(fieldView, picked, player, location, sequence, tapX, tapY);
                }
                // 效果发动各步骤（支付cost/是否连锁/选择连锁卡等）期间的点击查看：无论该卡
                // 是否为本次候选，都即时刷新详情面板（对齐 gframe ClickCard 后无条件 ShowCardInfo）；
                // 用会话前捕获的卡片引用，自动应答结束会话、场区列表随之变化后依旧取得到被点卡；
                // 与非选卡分支同样延后一帧，避免抢占 UI 线程拖慢选中反馈
                if (fieldView != null) {
                    fieldView.post(() -> activity.showCardInfoPanel(picked));
                } else {
                    activity.showCardInfoPanel(picked);
                }
                // 期间点击堆叠区（卡组/额外/墓地/除外）或持素材超量怪兽：该卡在点击时不是本次
                // 候选才弹出带「查看列表」的位置菜单（候选卡点击由会话直接切换选中，不弹菜单打断连点）
                boolean pileOrXyz = location == 0x01 || location == 0x40
                        || location == 0x10 || location == 0x20
                        || (location == 0x04 && !picked.overlayed.isEmpty());
                if (pileOrXyz && !wasSelectable) {
                    showCardCommandMenu(picked, tapX, tapY, true);
                }
            }
            return;
        }
        ClientCard card = engine.getField().getCard(player, location, sequence);
        if (card == null) {
            dismissCmdMenu();
            hideFieldStatusTip();
            return;
        }
        // 持有超量素材的怪兽，以及卡组/额外/墓地/除外堆叠区，弹出含「查看」的命令菜单，
        // 并把该卡在通讯中可执行的其他命令（发动/特殊召唤/攻击等）一并列出
        boolean isPile = (location == 0x01 || location == 0x40
                || location == 0x10 || location == 0x20);
        boolean xyzWithMats = (location == 0x04) && !card.overlayed.isEmpty();
        if (card.cmdFlag != 0 || isPile || xyzWithMats) {
            // 菜单先弹；详情刷新已在转发壳内延后到下一帧
            showCardCommandMenu(card, tapX, tapY, isPile || xyzWithMats);
        } else {
            // 无可执行命令：关闭残留的命令菜单（旧菜单点外关闭会联动收起标签，
            // 代际自增发生在本方法取 gen 之前，不影响本次点击的气泡）
            dismissCmdMenu();
            // 点击场上/手卡卡片都刷新卡片详情面板；但详情绑定含关键词可查判定（全卡表扫描，
            // 已按关键词缓存），放到下一帧执行，避免与菜单弹出抢占同一 UI 线程消息
            if (fieldView != null) {
                fieldView.post(() -> activity.showCardInfoPanel(card));
            } else {
                activity.showCardInfoPanel(card);
            }
        }
        // 通讯当前值行走悬浮标签（原长按触发，现改为点击触发，点卡片
        // 以外位置隐藏）：气泡为独立全屏窗口，创建耗时，同样延后到下一帧，
        // 不得与命令菜单同消息显示拖慢菜单上屏。堆叠区（卡组/墓地/除外/额外）
        // 不展示顶卡气泡（同 C++ 悬停 stTip 不含堆叠区），并收起旧气泡
        if (isPile) {
            hideFieldStatusTip();
        } else {
            scheduleFieldStatusTip(fieldView, card, player, location, sequence, tapX, tapY);
        }
    }

    @Override
    public void onFieldLongPress(int player, int location, int sequence, float x, float y) {
        Log.d(TAG, "Long press: p=" + player + " loc=" + location + " seq=" + sequence);
        // 用户规格：按住卡片与点击卡片一样显示卡片详情——任何场合长按场上/手卡/堆叠区的卡
        // 都刷新详情面板（原先只在选卡会话内且限候选卡才响应，普通场景按住毫无反应）。
        // GestureDetector 已识别为长按的手势不会再派发 onSingleTapUp，故会话中长按候选卡
        // 只查看卡片、不会切换选中或发出应答；长按也不弹命令菜单、不带状态气泡
        if (engine == null) return;
        ClientCard card = engine.getField().getCard(player, location, sequence);
        if (card == null) return;
        GameFieldView fieldView = viewController != null ? viewController.getView() : null;
        if (fieldView != null) {
            fieldView.post(() -> activity.showCardInfoPanel(card));
        } else {
            activity.showCardInfoPanel(card);
        }
    }

    @Override
    public void onFieldLongPressEnd() {
        // 状态标签改为点击后持续显示（直到点击卡片以外/另一张卡取代），抬手不再隐藏
    }

    /**
     * 点击场地中央 conti_act（待效果结算）堆叠：仅在命令上下文（主阶/战斗/连锁）内有效，
     * 弹出含「效果处理」按钮的命令菜单（对齐 gframe event_handler.cpp POSITION_HINT →
     * ShowMenu(COMMAND_OPERATION) → btnOperation），后续选卡/应答见 CmdMenuDialog
     */
    @Override
    public void onContiActClick(float tapX, float tapY) {
        // 中央结算堆不属于单张卡片的点击：先收起状态悬浮标签
        hideFieldStatusTip();
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
