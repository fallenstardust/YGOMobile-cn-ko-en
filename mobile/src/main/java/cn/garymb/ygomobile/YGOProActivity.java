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
 * 决斗主界面门面（拆分继承链的叶子，保持类名不变以兼容 manifest 启动与
 * YGOStarter/各对话框的全部外部引用）：场景导航（决斗/录像/大厅聊天/
 * 卡组编辑/返回局域网主界面）与投降/撤回等决斗交互入口。
 * 链结构：{@link YGOProActivityBase} ← {@link YGOProActivityUi} ←
 * {@link YGOProActivityEngine} ← 本类；GameEngine.EngineListener 回调下沉到
 * {@link EngineCallbackDelegate}，三个对话框监听接口下沉到 {@link MainMenuNavigator}，
 * 场景 BGM 决策下沉到 {@link BgmSceneController}，已保存设置应用下沉到
 * {@link GameSettingsApplier}，卡组编辑器视图切换下沉到 {@link DeckEditorViewHost}
 * （均同包，包级私有直连本类共享状态）。
 */
public class YGOProActivity extends YGOProActivityEngine {
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
    void enterDuelingUI() {
        getMainMenuDialog().hideMainMenu();
        // 决斗开始：从 player waiting 大厅聊天切回决斗显示
        //（恢复决斗场渲染，聊天改回玩家分侧 + 系统/观战弹幕逻辑）
        exitLobbyChatUI();
        showGameUI();
        hideLanDialogsForDuel();
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
        hideLanDialogsForDuel();
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
        // 撤回快照里被即时隐藏的询问窗一并作废：决斗已结束，服务端迟到/被拒的撤回 ACK
        // 不得再把上一局的旧询问窗重新弹回主界面（restoreUndoHiddenDialogs 只认快照）
        clearUndoHiddenDialogs();
        hideGameUI();
        if (layoutDeckEditor != null) layoutDeckEditor.setVisibility(View.GONE);
        if (layoutDeckControl != null) layoutDeckControl.setVisibility(View.GONE);
        setWindowBackground(Constants.CORE_SKIN_PATH + "/" + Constants.CORE_SKIN_BG_MENU);

        // 关闭玩家等待界面（销毁语义不变）；建主界面改为仅隐藏保留实例：下次建主 reshow
        // 回到上次规则/卡表/输入选择状态（用户规格）
        if (playerWaitingDialog != null) {
            playerWaitingDialog.setOnDismissListener(null);
            playerWaitingDialog.dismiss();
            playerWaitingDialog = null;
        }
        if (createHostDialog != null) {
            createHostDialog.hideForNavigation();
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
        // 横屏侧边按钮列已自 carddetailpanel 平移至 layout_game_right（随决斗场区可见，不再随面板
        // hide 消失）：进入大厅先确定化整列可见（设置/音乐/表情/聊天开关，大厅需可发表情），
        // 再按用户规格以 GONE 隐藏决斗记录/加速动画按钮（进入游戏由 onGameUIShown 恢复）
        if (cardDetailPanel != null) {
            cardDetailPanel.setSideButtonsVisible(true);
            cardDetailPanel.setLobbyButtonsVisible(false);
        }
        // 大厅聊天期间隐藏决斗场 GL 渲染（GLSurfaceView 置 GONE，决斗开始时恢复）
        View gameFieldView = findViewById(R.id.game_field_view);
        if (gameFieldView != null) gameFieldView.setVisibility(View.GONE);
        // 大厅聊天非决斗：决斗场隐藏后 FPS 角标一并隐藏
        updateFpsVisibility();
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
    protected void exitLobbyChatUI() {
        View gameFieldView = findViewById(R.id.game_field_view);
        if (gameFieldView != null) gameFieldView.setVisibility(View.VISIBLE);
        if (fieldCtl != null) fieldCtl.exitLobbyChatMode();
        // 切回决斗显示：FPS 角标点亮（祖先 layout_game_content 尚未点亮时由 showGameUI 再算）
        updateFpsVisibility();

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
    /**
     * 投降入口（对齐 event_handler.cpp BUTTON_LEAVE_GAME → wSurrender(1359) 确认 → CTOS_SURRENDER）：
     * 先弹 YesOrNoDialog 二次确认，确认后才发送投降通讯；
     * tag 模式下若本方已发起投降、正在等待队友回应，则忽略重复点击
     */
    public void requestSurrender() {
        if (engine == null || !engine.isInDuel()) return;
        if (engine.isTagMode() && engine.isSurrenderPending()) return;
        // 投降确认对话框正在显示时忽略重复点击：直到该对话框消失前不再弹出新的，
        // 避免多次点击堆叠出多个投降确认对话框（用户规格）
        if (surrenderDialog != null && surrenderDialog.isShowing()) return;
        YesOrNoDialog dialog = new YesOrNoDialog(this);
        boolean swapPos = AppsSettings.get().getIntSettings("swapButtonPositions", 0) == 1;
        dialog.setTitle(mStringManager.getSystemString(1351, "投降"))
                .setMessage(mStringManager.getSystemString(1359, "是否确定投降？"))
                .setType(YesOrNoDialog.TYPE_YES_NO)
                .setPositiveButtonText(mStringManager.getSystemString(1213, "是"))
                .setNegativeButtonText(mStringManager.getSystemString(1214, "否"))
                .setPositiveButton(v -> {
                    if (engine != null) engine.sendSurrender();
                })
                .setCenterInView(layoutGameRight)
                .setCancelable(false)
                .setOnDismissListener(() -> {
                    // 对话框消失后清空引用，重新放行下一次投降点击
                    if (surrenderDialog == dialog) surrenderDialog = null;
                })
                .setSwapButtonPositions(swapPos);
        surrenderDialog = dialog;
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
        // sendUndo 的防连点闸门一致：本次不会真正发出撤回请求时不隐藏弹窗（隐藏后无人恢复）
        if (engine.undoBlockedUntilNewAction) return;
        // 点击撤回瞬间先隐藏全部正在显示的询问弹窗（YesOrNoDialog），不等服务端 ACK：
        // 回退后旧询问必然失效，留着旧窗只会在 ACK 前误答旧询问。快照存入
        // undoHiddenDialogs：撤回被拒（onUndoDenied）且询问未被重派时逐个恢复，
        // 撤回被接受（onUndoResync）时快照作废，服务端重同步后会重新挂回新询问
        undoHiddenDialogs.clear();
        // 连锁询问现场备份：下面的收口会连静态引用与必发标志一起作废，被拒恢复时要回灌
        undoChainQueryBackup = YesOrNoDialog.getChainQueryDialog();
        undoChainForcedBackup = YesOrNoDialog.isChainForcedMode();
        for (YesOrNoDialog d : YesOrNoDialog.snapshotShowingDialogs()) {
            undoHiddenDialogs.add(d);
            d.dismiss();
        }
        // 用户规格：duel 相关的其余弹窗（选卡/确认/选项/宣言/表示形式/命令菜单）此刻一并自动隐藏。
        // 点击瞬间引擎已布防撤回应答代次屏障，这些窗上的按钮再点也发不出应答（被丢弃），与其
        // 留个“看起来能点、点了没反应”的旧窗遮住回退后的新局面，不如按撤回成功时的同一口径立即
        // 收口；确实收口过应答窗时记下方志，被拒时据此重建（见 EngineCallbackDelegate.onUndoDenied）
        undoClosedQuestionDialog = closeDuelUiForUndo();
        engine.sendUndo();
    }

    /**
     * 撤回相关的一刀切收口（点击撤回瞬间与撤回成功重同步前共用）：关闭选卡/确认/展示/选项/
     * 宣言/表示形式等全部 duel 弹窗，并作废其选择上下文与进行中的交互会话（这些状态由已被
     * 撤销的询问建立，任其存活则下一次卡片/格子点击会拿旧询问的编码去应答新局面 → 非法应答
     * → MSG_RETRY 风暴），同时取消卡片点击选中态与状态气泡。
     * <p>不发出任何应答，也不依赖弹窗实例可重建（询问窗自身的快照恢复另由 undoHiddenDialogs 负责）。
     *
     * @return 本次是否真的关掉了询问窗之外的应答类弹窗
     */
    public boolean closeDuelUiForUndo() {
        boolean closedOther = false;
        if (dialogUtil != null) closedOther = dialogUtil.dismissOpenGameDialogs();
        if (cardDetailPanel != null) {
            // 与 dismissOpenGameDialogs 内部的同一方去重关闭（已关时仅返回 false）：保留原有
            // 双重收口调用点，避免撤回链上因 dialogUtil 未就绪而漏关
            closedOther |= cardDetailPanel.dismissOpenDialogs();
            // 面板的选择上下文必须一并作废：「取消操作 / 完成选择」按钮按 currentSelectType 直接
            // 编码应答，撤回后新询问已到达，误触这个旧按钮就是把一份旧询问的答复打到新局面上
            cardDetailPanel.setSelectType(-1);
            cardDetailPanel.hideCancelOrFinishButton();
            cardDetailPanel.setCurrentDialog(null);
            cardDetailPanel.setCardSelectDialog(null);
            cardDetailPanel.setCardDisplayDialog(null);
        }
        // 连锁必发标志与询问窗静态引用作废：否则「取消操作」会被吞掉，或去 show 一个已死弹窗
        YesOrNoDialog.setChainForcedMode(false);
        YesOrNoDialog.setChainQueryDialog(null);
        if (fieldCtl != null) {
            fieldCtl.abortPendingSelectSessions();
            fieldCtl.clearSelectionVisuals();
            // 撤回后局面已变：旧的卡片点击选中态（金色呼吸外框/手卡抬升）与状态气泡一并收起
            fieldCtl.cancelCardClickSelection();
            fieldCtl.invalidate();
        }
        return closedOther;
    }

    /** 撤回被拒且询问未被屏障路径重派：恢复点击撤回时隐藏的询问弹窗，避免待答询问永久不可见 */
    public void restoreUndoHiddenDialogs() {
        boolean chainRestored = false;
        for (YesOrNoDialog d : undoHiddenDialogs) {
            if (!d.isShowing()) d.show();
            if (d == undoChainQueryBackup) chainRestored = true;
        }
        undoHiddenDialogs.clear();
        // 恢复出的确是那张连锁询问窗：静态引用与必发标志原样回灌，「取消操作」继续可用
        if (chainRestored) {
            YesOrNoDialog.setChainQueryDialog(undoChainQueryBackup);
            YesOrNoDialog.setChainForcedMode(undoChainForcedBackup);
        }
        undoChainQueryBackup = null;
    }

    /** 撤回被接受或询问已按新询问重派：隐藏快照、连锁现场备份与「收口过其它应答弹窗」标志一并作废 */
    public void clearUndoHiddenDialogs() {
        undoHiddenDialogs.clear();
        undoClosedQuestionDialog = false;
        undoChainQueryBackup = null;
        undoChainForcedBackup = false;
    }
}

