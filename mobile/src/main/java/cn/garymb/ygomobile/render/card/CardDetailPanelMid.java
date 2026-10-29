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
 * Card detail panel, middle layer of the split inheritance chain
 * (CardDetailPanelBase <- CardDetailPanelMid <- CardDetailPanel).
 * Owns the side-icon binding pass, the system-string button title
 * appliers, the chain-button show/hide flows, the cancelOrFinish
 * decision engine with its ants highlight / shuffle interplay and
 * the replay / spectator / deck-editor control bars.
 */
abstract class CardDetailPanelMid extends CardDetailPanelBase {

    /**
     * 转发构造器：Base 仅有带参构造器（无默认无参构造），中间层必须显式
     * 沿链传递 activity，保持与原实现完全一致的赋值语义
     */
    protected CardDetailPanelMid(YGOProActivity activity) {
        super(activity);
    }

    /**
     * 面板初始化时统一从 TextureLoader 获取侧边功能按钮图标
     * （对齐 gframe image_manager.cpp extra 图标：tSettings/tLogs/tPlay/tOneX/tEmoticon/tTalk），
     * 必须在 TextureLoader.init() 之后调用；纹理缺失时保留 XML 默认图兜底
     */
    public void bindSideButtonIcons() {
        TextureLoader tl = TextureLoader.get();
        if (btnSettings != null) {
            Bitmap bmp = tl.getSettingsTexture();
            if (bmp != null) btnSettings.setImageBitmap(bmp);
        }
        if (btnNote != null) {
            Bitmap bmp = tl.getLogsTexture();
            if (bmp != null) btnNote.setImageBitmap(bmp);
        }
        if (btnSound != null) {
            // 对齐 gframe game.cpp imgVol：声音开启显示 tPlay、全部静音显示 tMute
            boolean soundOn = AppsSettings.get().getIntSettings("chkEnableSound", 1) == 1
                    || AppsSettings.get().getIntSettings("chkEnableMusic", 1) == 1;
            Bitmap bmp = soundOn ? tl.getPlayTexture() : tl.getMuteTexture();
            if (bmp != null) btnSound.setImageBitmap(bmp);
        }
        if (btnSpeed != null) {
            // 对齐 gframe game.cpp imgQuickAnimation：快速动画 tDoubleX、常速 tOneX
            boolean quick = AppsSettings.get().getIntSettings("chkQuickAnimation", 0) == 1;
            Bitmap bmp = quick ? tl.getDoubleXTexture() : tl.getOneXTexture();
            if (bmp != null) btnSpeed.setImageBitmap(bmp);
        }
        if (btnEmote != null) {
            Bitmap bmp = tl.getEmoticonTexture();
            if (bmp != null) btnEmote.setImageBitmap(bmp);
        }
        if (btnChat != null) {
            // 对齐 gframe game.cpp L1319：初始图标跟随停用聊天设置（chkDisableChatting），停用显示 tShut、启用显示 tTalk
            boolean ignored = AppsSettings.get().getIntSettings("chkDisableChatting", 0) == 1;
            Bitmap bmp = ignored ? tl.getShutTexture() : tl.getTalkTexture();
            if (bmp != null) btnChat.setImageBitmap(bmp);
        }
    }

    public void closeGameButtons() {
        // 对齐 game.cpp Game::CloseGameButtons() L2395-2400：隐藏时点按钮、取消/完成与洗切手卡按钮
        // 决斗/回放结束额外复位时点三态逻辑标志（用户规格：本场无论选过哪个时点按钮，
        // 结束后全部回到默认未按下）：hideChainButtons 只清视图 selected 态，不清三个 boolean，
        // 下一局 STOC_GAME_START 的 onDuelStarted → updateChainButtons 会把残留按下态还原显示；
        // 默认态=全不按下（未勾选 chkDefaultShowChain 时），勾选则下一局 onDuelStarted 照常置起
        ignoreChain = false;
        alwaysChain = false;
        chainWhenAvail = false;
        hideChainButtons();
        hideCancelOrFinishButton();
        updateShuffleButton(false);
        // 决斗结束隐藏按钮行用 INVISIBLE 而非 GONE（用户规格）：竖屏顶部按钮行为 weight 布局，
        // GONE 会整行塌陷/其他控件跳位，INVISIBLE 保持占位稳定
        if (layoutBottomActions != null) layoutBottomActions.setVisibility(View.INVISIBLE);
    }

    /** 按钮文字取自 strings.conf（对齐 game.cpp L1343/1344/1345/1360/1346/1347 的录像控制按钮） */
    protected void applyReplayButtonTitles() {
        mStringManager = DataManager.get().getStringManager();
        if (btnReplayPlay != null)
            btnReplayPlay.setText(mStringManager.getSystemString(1343, "播放"));
        if (btnReplayPause != null)
            btnReplayPause.setText(mStringManager.getSystemString(1344, "暂停"));
        if (btnReplayNext != null)
            btnReplayNext.setText(mStringManager.getSystemString(1345, "下一步"));
        if (btnReplayLast != null)
            btnReplayLast.setText(mStringManager.getSystemString(1360, "上一步"));
        if (btnReplayShuffle != null)
            btnReplayShuffle.setText(mStringManager.getSystemString(1346, "切换视角"));
        if (btnReplayQuit != null)
            btnReplayQuit.setText(mStringManager.getSystemString(1347, "退出"));
    }

    /** 观战控制集按钮文字：切换视角/退出复用 gframe 系统字符串 1346/1347（与录像条同源），
     *  跳到当前无对应系统字符串索引，保留 XML 的 @string 资源文本不做 getSystemString 覆盖 */
    protected void applySpectatorButtonTitles() {
        mStringManager = DataManager.get().getStringManager();
        if (btnSpectatorSwap != null)
            btnSpectatorSwap.setText(mStringManager.getSystemString(1346, "切换视角"));
        if (btnSpectatorQuit != null)
            btnSpectatorQuit.setText(mStringManager.getSystemString(1347, "退出"));
    }

    /** 按钮文字取自 strings.conf（对齐 game.cpp L1348/1350/1352 的 GetSysString(1292/1293/1294)） */
    protected void applyChainButtonTitles() {
        mStringManager = DataManager.get().getStringManager();
        if (btnIgnoreTiming != null) {
            btnIgnoreTiming.setText(mStringManager.getSystemString(1292, "忽略时点"));
        }
        if (btnShowTiming != null) {
            btnShowTiming.setText(mStringManager.getSystemString(1293, "显示时点"));
        }
        if (btnAvailableTiming != null) {
            btnAvailableTiming.setText(mStringManager.getSystemString(1294, "可用时点"));
        }
    }

    /**
     * MSG_NEW_TURN 时显示时点按钮（对齐 duelclient.cpp L2865-2877）：
     * control_mode == 0 → 显示三键并刷新按下态；否则隐藏三键与取消/完成按钮
     */
    public void showChainButtons() {
        if (AppsSettings.get().getIntSettings("control_mode", 0) != 0) {
            hideChainButtons();
            hideCancelOrFinishButton();
            return;
        }
        applyChainButtonTitles();
        if (btnIgnoreTiming != null) btnIgnoreTiming.setVisibility(View.VISIBLE);
        if (btnShowTiming != null) btnShowTiming.setVisibility(View.VISIBLE);
        if (btnAvailableTiming != null) btnAvailableTiming.setVisibility(View.VISIBLE);
        updateChainButtons();
    }

    /**
     * 隐藏时点按钮。用 INVISIBLE 而非 GONE（与本布局 btn_shuffle_hand 一致）：
     * layout_bottom_actions 是 weight 布局，GONE 会让"投降"按钮尺寸跳变
     */
    public void hideChainButtons() {
        if (btnIgnoreTiming != null) {
            btnIgnoreTiming.setSelected(false);
            btnIgnoreTiming.setVisibility(View.INVISIBLE);
        }
        if (btnShowTiming != null) {
            btnShowTiming.setSelected(false);
            btnShowTiming.setVisibility(View.INVISIBLE);
        }
        if (btnAvailableTiming != null) {
            btnAvailableTiming.setSelected(false);
            btnAvailableTiming.setVisibility(View.INVISIBLE);
        }
    }

    /**
     * STOC_GAME_START 时初始化时点三态（对齐 duelclient.cpp L912-916）：
     * 勾选「开局默认显示所有时点」(chkDefaultShowChain) → always_chain = true，另两态清零。
     * 未勾选时保持原值不清零，与 gframe 一致（Game 成员跨局保留，
     * 下一局首个 MSG_NEW_TURN 会由 UpdateChainButtons 还原按下态显示）
     */
    public void onDuelStarted() {
        if (AppsSettings.get().getIntSettings("chkDefaultShowChain", 0) == 1) {
            alwaysChain = true;
            ignoreChain = false;
            chainWhenAvail = false;
        }
        updateChainButtons();
    }

    public void showCancelOrFinishButton(String text) {
        setCancelOrFinishShown(true, text);
    }

    /**
     * 隐藏完成选择/取消按钮：用 INVISIBLE 而非 GONE（同 hideChainButtons）——
     * layout_bottom_actions 为 weight 布局，GONE 会让其余按钮尺寸跳变
     */
    public void hideCancelOrFinishButton() {
        setCancelOrFinishShown(false, null);
    }

    /**
     * 重建尾部回显：仅当旋转前按钮在显示时，按快照的文字重新点亮（连带恢复蚂蚁线描边、
     * 洗切手卡联动隐藏，与正常 updateCancelOrFinishButton 显示路径完全一致）。
     */
    public void restoreCancelOrFinishAfterRotation() {
        if (savedCancelOrFinishShown && btnCancelOrFinish != null) {
            setCancelOrFinishShown(true, savedCancelOrFinishText);
        }
    }

    /**
     * cancelOrFinish 可见态统一入口：切换按钮显隐、同步蚂蚁线高亮、并联动洗切手卡按钮显隐
     *（显示时暂时隐藏洗切手卡，隐藏后若通讯允许则恢复）。
     */
    protected void setCancelOrFinishShown(boolean visible, String text) {
        if (btnCancelOrFinish == null) return;
        if (text != null) btnCancelOrFinish.setText(text);
        cancelOrFinishShown = visible;
        btnCancelOrFinish.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
        updateAntsHighlight();
        applyShuffleVisibility();
    }

    /** cancelOrFinish 可见则挂上并启动蚂蚁线，否则停止并移除 */
    protected void updateAntsHighlight() {
        if (btnCancelOrFinish == null) return;
        if (cancelOrFinishShown) {
            if (antsHighlight == null) {
                float density = activity.getResources().getDisplayMetrics().density;
                antsHighlight = new MarchingAntsDrawable(density);
            }
            btnCancelOrFinish.setForeground(antsHighlight);
            antsHighlight.start();
        } else {
            if (antsHighlight != null) antsHighlight.stop();
            btnCancelOrFinish.setForeground(null);
        }
    }

    public void updateCancelOrFinishButton(boolean ready, boolean cancelable, boolean hasSelection) {
        if (btnCancelOrFinish == null) return;
        mStringManager = DataManager.get().getStringManager();
        if (ready) {
            // 完成选择（对齐 game.cpp 1296）
            setCancelOrFinishShown(true, mStringManager.getSystemString(1296, "完成选择"));
        } else if (cancelable && !hasSelection) {
            // 取消操作（对齐 game.cpp 1295）
            setCancelOrFinishShown(true, mStringManager.getSystemString(1295, "取消操作"));
        } else {
            setCancelOrFinishShown(false, null);
        }
    }

    // 对齐 C++ ClientField::CancelOrFinish：按当前选择类型执行完成/取消
    public void cancelOrFinish() {
        // 注：selectType 12/13（EFFECTYN/YESNO 是/否询问）不在此列——对齐 gframe wQuery，
        // 该询问只有弹窗内「是/否」两键，cancelOrFinish 按钮不参与也不显示
        switch (currentSelectType) {
            case 15:
            case 20: {
                if (cardSelectDialog != null) {
                    if (cardSelectDialog.getSelectedCount() >= cardSelectDialog.getMinSelect()) {
                        cardSelectDialog.confirm();
                    } else if (cardSelectDialog.isCancelable() && cardSelectDialog.getSelectedCount() == 0) {
                        activity.sendResponseInt(-1);
                        hideCancelOrFinishButton();
                        cardSelectDialog.dismiss();
                    }
                } else {
                    // 场上/手牌直接选择模式（无弹窗）：由 GameFieldController 完成/取消
                    GameFieldController fieldCtl = activity.getFieldCtl();
                    if (fieldCtl != null && fieldCtl.finishCardSelect()) {
                        hideCancelOrFinishButton();
                    }
                }
                break;
            }
            case 23: {
                if (cardSelectDialog != null) {
                    if (cardSelectDialog.isReady()) {
                        cardSelectDialog.confirm();
                    }
                } else {
                    // 场上/手牌合计选择模式（无弹窗）：selectReady 时由 GameFieldController 应答（C++ L3156-3158）
                    GameFieldController fieldCtl = activity.getFieldCtl();
                    if (fieldCtl != null && fieldCtl.finishCardSelect()) {
                        hideCancelOrFinishButton();
                    }
                }
                break;
            }
            case 26: {
                // event_handler.cpp L968-971：UNSELECT 的完成/取消按钮 = 发送 -1
                if (cardSelectDialog == null) {
                    // 场上/手牌直接选择模式（无弹窗，连接手续逐步选素材）：
                    // 应答 -1 并清理场上会话（finishCardSelect 内走 UNSELECT 分支→cancelCardSelect）
                    GameFieldController fieldCtl = activity.getFieldCtl();
                    if (fieldCtl != null && fieldCtl.finishCardSelect()) {
                        hideCancelOrFinishButton();
                    }
                    break;
                }
                activity.sendResponseInt(-1);
                hideCancelOrFinishButton();
                cardSelectDialog.dismiss();
                break;
            }
            case 27: {
                hideCancelOrFinishButton();
                if (cardDisplayDialog != null) cardDisplayDialog.dismiss();
                break;
            }
            case 16: {
                // 连锁（MSG_SELECT_CHAIN）取消：优先按 wQuery 语义处理——场上点击模式下点击「取消操作」
                // 重新弹出询问窗（对齐 CancelOrFinish 的 PopupElement(wQuery)），实现「暂时隐藏」后可恢复；
                // 无询问窗（panelmode 列表）时回退到直接应答 -1。handleChainCancel 内部自行管理 selectType，
                // 故此处直接 return，跳过方法末尾的 currentSelectType=-1 复位
                ShowDialogUtil du = activity.getDialogUtil();
                if (du != null && du.handleChainCancel()) {
                    return;
                }
                activity.sendResponseInt(-1);
                hideCancelOrFinishButton();
                if (currentDialog != null) currentDialog.dismiss();
                break;
            }
            case 25: {
                activity.sendResponseInt(-1);
                hideCancelOrFinishButton();
                if (currentDialog != null) currentDialog.dismiss();
                break;
            }
            case 18:
            case 24: {
                GameFieldController fieldCtl = activity.getFieldCtl();
                if (fieldCtl != null && fieldCtl.cancelPlaceSelect()) {
                    hideCancelOrFinishButton();
                }
                break;
            }
            default: {
                hideCancelOrFinishButton();
                if (currentDialog != null) currentDialog.dismiss();
                break;
            }
        }
        currentSelectType = -1;
    }

    public void onGameUIShown() {
        // 每次进入决斗/回放 UI 先清掉上一场残留的时点三键/洗切手卡/录像控制条，
        // 后续通讯状态（DUELING / 回放 PLAYING / MSG_NEW_TURN）会重新点亮，
        // 修复「看完录像再决斗（或反之）一开头显示不该出现的按钮」
        hideChainButtons();
        updateShuffleButton(false);
        hideReplayControls();
        // 进入游戏（决斗/回放）恢复被大厅（player waiting）以 GONE 裁剪的决斗记录/加速动画按钮
        //（用户规格：进等待界面隐藏、进入游戏恢复；横屏按钮列已平移至 layout_game_right，
        // 不再随本面板 hide/show，需在此显式复位）
        setLobbyButtonsVisible(true);
        if (layoutBottomActions != null) layoutBottomActions.setVisibility(View.VISIBLE);
        // 投降按钮默认隐藏：猜拳(HAND_SELECT)/选先后(TP_SELECT)阶段不显示，
        // 进入决斗(DUELING)且为对战玩家时由 Activity 调用 setSurrenderVisible(true) 显示
        //（对齐 gframe：btnLeaveGame 在 STOC_DUEL_START 前不出现，观战者不显示投降）
        setSurrenderVisible(false);
        showDefault();
        hideCancelOrFinishButton();
    }

    // 撤回图标的显隐/闪动已下沉到 {@code GameTopInfoManager.setUndoPrompt}（顶部回合数下方）

    public void onGameUIHidden() {
        hide();
        spectatorMode = false;
        if (layoutBottomActions != null) layoutBottomActions.setVisibility(View.GONE);
        hideCancelOrFinishButton();
    }

    /**
     * 统一关闭当前打开的选择/确认/展示对话框（断线、决斗结束或撤回瞬间清理），
     * 避免残留弹窗遮挡重新显示的局域网主界面。
     *
     * @return 本次是否真的关掉了选卡窗（MSG_SELECT_CARD/解放/合计/弃牌等询问的应答窗）：
     *         撤回路径据此决定走「按询问快照重建」还是「恢复询问窗旧实例」（用户规格）
     */
    public boolean dismissOpenDialogs() {
        boolean selectDialogClosed = cardSelectDialog != null && cardSelectDialog.isShowing();
        if (currentDialog != null) currentDialog.dismiss();
        if (cardSelectDialog != null) cardSelectDialog.dismiss();
        if (cardDisplayDialog != null) cardDisplayDialog.dismiss();
        currentDialog = null;
        cardSelectDialog = null;
        cardDisplayDialog = null;
        currentSelectType = -1;
        hideCancelOrFinishButton();
        return selectDialogClosed;
    }

    // === 卡组编辑器模式 ===

    public void enterDeckEditorMode() {
        if (btnNote != null) btnNote.setVisibility(View.INVISIBLE);
        if (btnSpeed != null) btnSpeed.setVisibility(View.INVISIBLE);
        if (btnEmote != null) btnEmote.setVisibility(View.INVISIBLE);
        emoteNormallyVisible = false;
        // btnChat 不再在此直接隐藏：聊天开关由 YGOProActivity.updateChatUIVisibility
        // 集中按场景核算（卡组编辑属抑制场景），避免 enter/exit 与集中控制互相覆盖
        showDefault();
        if (layoutBottomActions != null) layoutBottomActions.setVisibility(View.GONE);
        if (layoutReplayControl != null) layoutReplayControl.setVisibility(View.GONE);
        if (layoutSpectatorControl != null) layoutSpectatorControl.setVisibility(View.GONE);
        if (layoutDeckControl != null) layoutDeckControl.setVisibility(View.VISIBLE);
    }

    public void exitDeckEditorMode() {
        if (layoutDeckControl != null) layoutDeckControl.setVisibility(View.GONE);
        hide();
        if (btnNote != null) btnNote.setVisibility(View.VISIBLE);
        if (btnSpeed != null) btnSpeed.setVisibility(View.VISIBLE);
        if (btnEmote != null) btnEmote.setVisibility(View.VISIBLE);
        emoteNormallyVisible = true;
        // btnChat 同 enterDeckEditorMode：由集中核算按退出后的实际场景重定显隐
    }

    // === 录像控制条 ===

    public void showReplayControls() {
        spectatorMode = false;
        if (layoutBottomActions != null) layoutBottomActions.setVisibility(View.GONE);
        if (layoutSpectatorControl != null) layoutSpectatorControl.setVisibility(View.GONE);
        if (layoutReplayControl != null) layoutReplayControl.setVisibility(View.VISIBLE);
        if (btnReplayShuffle != null) btnReplayShuffle.setVisibility(View.VISIBLE);
        if (btnReplayQuit != null) btnReplayQuit.setVisibility(View.VISIBLE);
        // 观看录像隐藏表情按钮（用户规格，横竖屏共用本面板）：回放无对局对手，
        // 表情发送无接收方；用 INVISIBLE 保持竖屏底部按钮行 weight 布局占位稳定
        if (btnEmote != null) btnEmote.setVisibility(View.INVISIBLE);
        // 初始状态：正在播放（显示暂停，隐藏播放/上一步/下一步）
        updateReplayButtonStates(false);
    }

    /**
     * 观战控制面板（对齐 event_handler.cpp：观战无行动/时点权限，btnSwapY 与退出可用）：
     * 隐藏底部行动区与时点三键，用专用观战控制集（跳到当前/切换视角/退出，需求5）
     * 代替录像控制条；与录像条同位互斥，进观战时隐藏录像条、显示观战集
     */
    public void showSpectatorControls() {
        spectatorMode = true;
        hideChainButtons();
        hideCancelOrFinishButton();
        updateShuffleButton(false);
        setSurrenderVisible(false);
        if (layoutBottomActions != null) layoutBottomActions.setVisibility(View.GONE);
        if (layoutReplayControl != null) layoutReplayControl.setVisibility(View.GONE);
        if (layoutSpectatorControl != null) layoutSpectatorControl.setVisibility(View.VISIBLE);
    }

    /**
     * 录像控制按钮互斥显隐：播放中显示暂停按钮、隐藏播放/上一步/下一步；
     * 暂停时隐藏暂停、显示播放/上一步/下一步。切换时使用 INVISIBLE 不破坏布局。
     * 对齐 replay_mode.cpp 中 pause 状态下 UI 元素可用性逻辑。
     */
    public void updateReplayButtonStates(boolean isPaused) {
        if (btnReplayPause != null)
            btnReplayPause.setVisibility(isPaused ? View.INVISIBLE : View.VISIBLE);
        if (btnReplayPlay != null)
            btnReplayPlay.setVisibility(isPaused ? View.VISIBLE : View.INVISIBLE);
        if (btnReplayLast != null)
            btnReplayLast.setVisibility(isPaused ? View.VISIBLE : View.INVISIBLE);
        if (btnReplayNext != null)
            btnReplayNext.setVisibility(isPaused ? View.VISIBLE : View.INVISIBLE);
    }

    /**
     * 隐藏录像控制条。不再顺手把 layoutBottomActions 置 VISIBLE：底部按钮区的可见性
     * 由 onGameUIShown / showBottomActions / closeGameButtons 按场景统一管控，
     * 避免退出回放瞬间露出不该出现的投降/时点等按钮
     */
    public void hideReplayControls() {
        spectatorMode = false;
        if (layoutReplayControl != null) layoutReplayControl.setVisibility(View.GONE);
        if (layoutSpectatorControl != null) layoutSpectatorControl.setVisibility(View.GONE);
        // 退出回放恢复表情按钮：卡组编辑模式下 btnEmote 由 enterDeckEditorMode 隐去
        // （且本方法在其后仍会被调用），据 emoteNormallyVisible 标志不越权恢复
        if (btnEmote != null && emoteNormallyVisible) {
            btnEmote.setVisibility(View.VISIBLE);
        }
    }
}
