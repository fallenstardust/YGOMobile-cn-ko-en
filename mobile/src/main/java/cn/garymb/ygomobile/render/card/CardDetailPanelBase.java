package cn.garymb.ygomobile.render.card;
import cn.garymb.ygomobile.render.field.TextureLoader;

import android.graphics.Bitmap;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.YGOProActivity;
import cn.garymb.ygomobile.audio.SoundManager;
import cn.garymb.ygomobile.game.gamefield.ClientCard;
import cn.garymb.ygomobile.game.gamefield.GameFieldController;
import cn.garymb.ygomobile.game.replay.ReplayPlayer;
import cn.garymb.ygomobile.game.select.ShowDialogUtil;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.ui.dialogs.duel.CardDisplayDialog;
import cn.garymb.ygomobile.ui.dialogs.duel.CardSelectDialog;
import cn.garymb.ygomobile.ui.dialogs.duel.YesOrNoDialog;
import ocgcore.DataManager;
import ocgcore.StringManager;
import ocgcore.data.Card;


/**
 * Card detail panel, base layer of the split inheritance chain
 * (CardDetailPanelBase <- CardDetailPanelMid <- CardDetailPanel).
 * Owns every panel state field, the CardInfoBinder delegate surface,
 * the side-icon update APIs, the chain tri-state accessors, the
 * selection-context setters and the small visibility helpers. The
 * original constructor body lives here under the base name so the
 * final activity/binder fields keep their finality.
 */
abstract class CardDetailPanelBase {

    protected final YGOProActivity activity;
    // 卡片详情绑定器（同包）：详情控件/卡码/卡背状态已平移，本面板留显隐协调与按钮域
    protected final CardInfoBinder binder = new CardInfoBinder();

    protected LinearLayout layout;
    /** include 根（activity_ygo_game.xml 中 layout_card_detail_panel）：
     *  本字段与 layout 并行控制，避免 PlayerWaitingDialog 进入时（hideGameUI）
     *  仅 layout_card_detail 子列隐去、左侧侧图标栏所在根仍默认可见。 */
    protected LinearLayout panelRoot;

    protected ImageButton btnSettings, btnChat, btnSound, btnSpeed, btnEmote, btnNote;

    protected LinearLayout layoutBottomActions;
    protected Button btnSurrender, btnIgnoreTiming, btnShowTiming, btnAvailableTiming;
    protected Button btnCancelOrFinish, btnShuffleHand;
    // 关联卡片入口：横竖屏布局同为属性/等级行右上角的 btn_related_cards（ic_related 图标、
    // list_item_bg 背景的 ImageButton，参考 CardDetail.btn_related），点击交由 CardInfoBinder
    // 按当前卡计算关联卡并分流；仅当前卡存在关联卡时由 binder 显示
    protected ImageButton btnRelatedCards;
    // 撤回入口不在本面板：已按用户规格改放顶部信息条中央回合数下方的发光 ic_undo
    //（layout_game_right 的 iv_undo，由 GameTopInfoManager.setUndoPrompt 按 STOC_UNDO_STATE 闪动）

    protected LinearLayout layoutReplayControl;
    protected Button btnReplayPlay, btnReplayPause, btnReplayNext, btnReplayLast, btnReplayShuffle, btnReplayQuit;
    // 观战控制集（需求5）：平时隐藏、仅观战时显示，代替录像控制条的切换视角/退出，
    // 额外提供「跳到当前」（横竖屏两套 XML 均有同 ID 控件）
    protected LinearLayout layoutSpectatorControl;
    protected Button btnSpectatorSkip, btnSpectatorSwap, btnSpectatorQuit;
    protected LinearLayout layoutDeckControl;

    /** 观战模式：左侧面板常驻录像控制条但只显示「切换视角/退出」（播控按钮 INVISIBLE），
     *  两按钮点击分流到观战版视角交换/退出观战（对齐 event_handler.cpp player_type==7 分支） */
    protected boolean spectatorMode;

    protected StringManager mStringManager = DataManager.get().getStringManager();

    // === 时点三态（对齐 gframe game.h: ignore_chain / always_chain / chain_when_avail，三者互斥） ===
    /** 忽略时点（sys1292）：跳过所有非强制、非诱发的时点询问 */
    protected boolean ignoreChain;
    /** 显示时点（sys1293）：即使没有可发动的卡也询问每个时点 */
    protected boolean alwaysChain;
    /** 可用时点（sys1294）：只要存在可发动的卡就询问 */
    protected boolean chainWhenAvail;

    // 选择上下文（cancelOrFinish 决策所需，由 YGOProActivity 注册同步）
    protected int currentSelectType = -1;
    protected YesOrNoDialog currentDialog;
    protected CardSelectDialog cardSelectDialog;
    protected CardDisplayDialog cardDisplayDialog;

    // cancelOrFinish 与洗手卡互斥显隐协调（取消/完成按钮显示时隐藏洗手卡，隐藏后按通讯允许恢复）
    /** 通讯（MSG_SELECT_IDLECMD show_shuffle）是否允许洗切手卡 */
    protected boolean shuffleAllowedByMsg;
    /** cancelOrFinish 按钮当前是否处于可见态（VISIBLE） */
    protected boolean cancelOrFinishShown;
    /** 表情按钮常规可见态：卡组编辑模式隐去时置假，退出回放恢复表情按钮
     *  时据此不越权恢复（hideReplayControls 在 enterDeckEditorMode 内也会先被调用） */
    protected boolean emoteNormallyVisible = true;
    /** cancelOrFinish 按钮周围的蚂蚁线高亮动画 */
    protected MarchingAntsDrawable antsHighlight;

    /**
     * 侧边六按钮查找：横屏、竖屏均直接从视图树取——横屏六按钮列 layout_image_buttons_panel
     * 作为 layout_game_right 水平行最左侧一格直接承载在树内（不再用独立 PopupWindow 浮层，
     * 以免盖住可拖动的 popup 窗口），竖屏则取底部按钮行的树内按钮。
     */
    protected ImageButton findSideButton(int id) {
        View v = activity.findViewById(id);
        return v instanceof ImageButton ? (ImageButton) v : null;
    }

    public void setImageLoader(ImageLoader imageLoader) {
        binder.setImageLoader(imageLoader);
    }

    /**
     * 注入/清除卡组编辑关键词导航器（{@code DeckEditorManager} 在进入时注入搜索到卡组列表、退出时置空）：
     * 非空时卡详描述里的高亮词点击不再弹关键词列表弹窗，而是填入卡组检索并搜索。
     */
    public void setDeckKeywordNavigator(CardInfoBinder.KeywordNavigator navigator) {
        binder.setDeckKeywordNavigator(navigator);
    }

    /**
     * 注入/清除卡组编辑关联卡片列表展示器（{@code DeckEditorManager} 进入时注入把关联卡填到卡组
     * 搜索结果列表、退出时置空）：非空时点击关联卡片按钮不再弹关键词列表弹窗，而是直接显示在卡组搜索结果列表。
     */
    public void setDeckCardListHandler(CardInfoBinder.CardListHandler handler) {
        binder.setDeckCardListHandler(handler);
    }

    /** 更新聊天按钮图标（对齐 gframe event_handler.cpp BUTTON_CHATTING：停用聊天显示 tShut、启用显示 tTalk） */
    public void updateChatIcon(boolean ignored) {
        if (btnChat == null) return;
        Bitmap bmp = ignored ? TextureLoader.get().getShutTexture() : TextureLoader.get().getTalkTexture();
        if (bmp != null) btnChat.setImageBitmap(bmp);
    }

    /**
     * 聊天开关按钮显隐（由 YGOProActivity.updateChatUIVisibility 按场景集中控制：
     * 卡组编辑/录像/残局这些无聊天对象场景隐藏，对齐 gframe 隐藏 wChat；
     * enter/exitDeckEditorMode 不再直接操作，避免与集中控制冲突）。
     * 隐藏用 INVISIBLE 而非 GONE（用户规格）：竖屏底部按钮行为 weight 布局，
     * GONE 会让其余按钮与聊天输入框宽度跳变，INVISIBLE 保持占位稳定
     */
    public void setChatToggleVisible(boolean visible) {
        if (btnChat != null) btnChat.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
    }

    /**
     * 侧边功能按钮整列显隐（用户规格：横屏按钮列已自 carddetailpanel 平移至
     * layout_game_right 最左侧 GameFieldView 上一层，不再随卡详面板根一起隐藏）：
     * 竖屏底部按钮行在卡组编辑时随顶部面板仍可见，故卡组编辑经本方法整列 INVISIBLE
     * 隐藏，与旧「面板隐藏→按钮不可见」行为一致；决斗/大厅场景恢复可见，
     * 单按钮态（录像隐表情、大厅隐记录/加速）由各自入口叠加管控
     */
    public void setSideButtonsVisible(boolean visible) {
        int v = visible ? View.VISIBLE : View.INVISIBLE;
        if (btnSettings != null) btnSettings.setVisibility(v);
        if (btnNote != null) btnNote.setVisibility(v);
        if (btnSound != null) btnSound.setVisibility(v);
        if (btnSpeed != null) btnSpeed.setVisibility(v);
        if (btnEmote != null) btnEmote.setVisibility(v);
        if (btnChat != null) btnChat.setVisibility(v);
    }

    /**
     * 大厅（player waiting）场景按钮裁剪（用户规格）：进入等待界面以 GONE 隐藏
     * 决斗记录与加速动画按钮，进入游戏时恢复；其余按钮不受影响
     */
    public void setLobbyButtonsVisible(boolean duelUiContext) {
        int v = duelUiContext ? View.VISIBLE : View.GONE;
        if (btnNote != null) btnNote.setVisibility(v);
        if (btnSpeed != null) btnSpeed.setVisibility(v);
    }

    /** 更新声音按钮图标（对齐 gframe imgVol：声音开启显示 tPlay、静音显示 tMute） */
    public void updateSoundIcon(boolean enabled) {
        if (btnSound == null) return;
        Bitmap bmp = enabled ? TextureLoader.get().getPlayTexture() : TextureLoader.get().getMuteTexture();
        if (bmp != null) btnSound.setImageBitmap(bmp);
    }

    /** 更新速度按钮图标（对齐 gframe imgQuickAnimation：快速动画 tDoubleX、常速 tOneX） */
    public void updateSpeedIcon(boolean quick) {
        if (btnSpeed == null) return;
        Bitmap bmp = quick ? TextureLoader.get().getDoubleXTexture() : TextureLoader.get().getOneXTexture();
        if (bmp != null) btnSpeed.setImageBitmap(bmp);
    }

    /** 当前回放播放器（GameEngine 协作件，恒存在；仅在真正有回放会话时返回） */
    protected ReplayPlayer replayPlayer() {
        ReplayPlayer rp = activity.getReplayPlayer();
        return rp != null && rp.hasActiveSession() ? rp : null;
    }

    // === 卡片详情面板（实现已平移至 CardInfoBinder，以下为签名不变的薄委托） ===

    public void showCardInfo(ClientCard card) {
        binder.showCardInfo(card);
    }

    public void showDefault() {
        binder.showDefault();
    }

    public void showCard(ClientCard clientCard) {
        binder.showCard(clientCard);
    }

    public void showCard(Card card) {
        binder.showCard(card);
    }

    public void hide() {
        binder.setCurrentCardCode(-1);
        // 面板隐藏同时收起关键词卡片列表弹窗，避免残留遮挡
        binder.dismissKeywordListDialog();
        if (layout != null) {
            layout.setVisibility(View.GONE);
        }
        // 同时收起 include 根，修复横屏下 PlayerWaitingDialog 进入时左侧侧栏图标仍默认可见
        if (panelRoot != null) {
            panelRoot.setVisibility(View.GONE);
        }
    }

    public boolean isShowing() {
        return layout != null && layout.getVisibility() == View.VISIBLE;
    }

    /** 旋转重建后回显卡片详情（委托 CardInfoBinder；savedCode 为重建前快照的卡码，无则 <=0） */
    public void restoreAfterRebind(boolean wasShowingBeforeRebind, int savedCode) {
        binder.restoreAfterRebind(wasShowingBeforeRebind, savedCode);
    }

    public int getCurrentCardCode() {
        return binder.getCurrentCardCode();
    }

    // === 时点按钮 (对应 C++ btnChainIgnore / btnChainAlways / btnChainWhenAvail) ===

    /** 显示时点（gframe always_chain）：供 ShowDialogUtil 在 MSG_SELECT_CHAIN 中判定自动应答 */
    public boolean isAlwaysChain() {
        return alwaysChain;
    }

    /** 忽略时点（gframe ignore_chain） */
    public boolean isIgnoreChain() {
        return ignoreChain;
    }

    /** 可用时点（gframe chain_when_avail） */
    public boolean isChainWhenAvail() {
        return chainWhenAvail;
    }

    /**
     * 刷新三个时点按钮的按下态（对齐 event_handler.cpp L2874-2880 ClientField::UpdateChainButtons）。
     * drawable/button3_bg.xml 中 state_selected 与 state_pressed 同为 @drawable/sbutton_p，
     * 因此 setSelected(flag) 等价于 gframe 的
     * ChangeToIGUIImageButton(btn, tButton_S, tButton_S_pressed) + setPressed(flag)
     */
    public void updateChainButtons() {
        if (btnIgnoreTiming != null) btnIgnoreTiming.setSelected(ignoreChain);
        if (btnShowTiming != null) btnShowTiming.setSelected(alwaysChain);
        if (btnAvailableTiming != null) btnAvailableTiming.setSelected(chainWhenAvail);
    }

    /** 对齐 gframe 各 BUTTON_* handler 开头的 PlaySoundEffect(SoundManager::SFX::BUTTON) */
    protected void playButtonSound() {
        SoundManager soundManager = activity.getSoundManager();
        if (soundManager != null) soundManager.playSoundEffect(SoundManager.SFX.BUTTON);
    }

    // === 取消或完成按钮 (对应 C++ ClientField::CancelOrFinish) ===

    // 选择上下文注册（由 YGOProActivity 在创建/关闭选择对话框时同步）
    public void setSelectType(int selectType) {
        this.currentSelectType = selectType;
    }

    public int getSelectType() {
        return currentSelectType;
    }

    public void setCurrentDialog(YesOrNoDialog dialog) {
        this.currentDialog = dialog;
    }

    public void setCardSelectDialog(CardSelectDialog dialog) {
        this.cardSelectDialog = dialog;
    }

    public void setCardDisplayDialog(CardDisplayDialog dialog) {
        this.cardDisplayDialog = dialog;
    }

    /**
     * 旋转重建期间的按钮快照字段：onGameUIShown() 会无条件 hideCancelOrFinishButton()
     * 复位按钮组，而选择会话（isPlaceSelecting / isCardSelecting / 连锁等待）跨旋转保留，
     * currentSelectType 也随复用的面板实例保留——若不回显按钮，玩家横竖屏切换后「取消操作/
     * 完成选择」凭空消失，既无法取消/完成进行中的会话，又会对残留询问乱点卡片（命令菜单把应答
     * 发进错位的问题里 → 无实际作用）→ 卡死无法继续（用户反馈）。
     * savedCancelOrFinishShown/Text 记录旋转前是否正在显示及按钮文字，供重建尾部回显。
     */
    protected boolean savedCancelOrFinishShown;
    protected String savedCancelOrFinishText;

    /**
     * 重建前（bindViews 换新视图树、onGameUIShown 复位之前）快照当前按钮态与文字。
     * 只捕获确实处于显示态的按钮：隐藏态无需回显，避免旋转后凭空出现取消/完成按钮。
     */
    public void saveCancelOrFinishForRotation() {
        savedCancelOrFinishShown = cancelOrFinishShown;
        savedCancelOrFinishText = btnCancelOrFinish != null
                ? btnCancelOrFinish.getText().toString() : null;
    }

    /**
     * 洗切手卡按钮显隐（对齐 duelclient.cpp MSG_SELECT_IDLECMD L1859-1865：
     * show_shuffle → btnShuffle->setVisible(true/false)）；记录通讯允许态，
     * 实际可见性另受 cancelOrFinish 是否显示约束（见 applyShuffleVisibility）
     */
    public void updateShuffleButton(boolean visible) {
        shuffleAllowedByMsg = visible;
        applyShuffleVisibility();
    }

    /** 洗切手卡可见 = 通讯允许 且 cancelOrFinish 未显示；均用 INVISIBLE 保持 weight 布局稳定 */
    protected void applyShuffleVisibility() {
        if (btnShuffleHand != null) {
            btnShuffleHand.setVisibility(shuffleAllowedByMsg && !cancelOrFinishShown
                    ? View.VISIBLE : View.INVISIBLE);
        }
    }

    // === 整体可见性 ===
    
    public void stopAntsHighlightImmediately() {
        if (antsHighlight != null) {
            antsHighlight.stop();
            btnCancelOrFinish.setForeground(null);
            antsHighlight = null;
        }
    }

    /**
     * 控制投降按钮显隐。使用 INVISIBLE 而非 GONE（与本布局链式按钮一致）：
     * layout_bottom_actions 为 weight 布局，GONE 会让其它按钮尺寸跳变
     */
    public void setSurrenderVisible(boolean visible) {
        if (btnSurrender != null)
            btnSurrender.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
    }

    public void showBottomActions() {
        if (layoutBottomActions != null) layoutBottomActions.setVisibility(View.VISIBLE);
    }

    /** 副卡组替换模式下暂时收起卡组编辑控制栏（退出编辑/打乱/排序/清空/删除按钮区）：
     *  仅换副卡组阶段调用（enterSideMode）。用 INVISIBLE 而非 GONE（用户规格）——
     *  控制栏所在卡详面板为 weight 布局，GONE 会让其余区域尺寸跳变，INVISIBLE 保持占位稳定 */
    public void hideDeckControl() {
        if (layoutDeckControl != null) layoutDeckControl.setVisibility(View.INVISIBLE);
    }

    /** 退出副卡组替换模式后恢复卡组编辑控制栏 */
    public void showDeckControl() {
        if (layoutDeckControl != null) layoutDeckControl.setVisibility(View.VISIBLE);
    }

    /** 当前是否处于观战控制面板模式 */
    public boolean isSpectatorMode() {
        return spectatorMode;
    }

    /** 释放资源（由 YGOProActivity 在销毁时调用） */
    public void onDestroy() {
        binder.onDestroy();
    }

    protected CardDetailPanelBase(YGOProActivity activity) {
        this.activity = activity;
    }
}
