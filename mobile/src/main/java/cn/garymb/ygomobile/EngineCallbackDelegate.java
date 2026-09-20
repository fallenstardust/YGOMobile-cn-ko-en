package cn.garymb.ygomobile;

import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import cn.garymb.ygomobile.game.GameEngine;
import cn.garymb.ygomobile.game.GameField;
import cn.garymb.ygomobile.game.ReplayReader;
import cn.garymb.ygomobile.game.ShowDialogUtil;
import cn.garymb.ygomobile.render.SpecEffectOverlay;
import cn.garymb.ygomobile.ui.dialogs.ReplaySaveDialog;
import cn.garymb.ygomobile.ui.dialogs.YesOrNoDialog;
import ocgcore.enums.DuelPhase;

/**
 * 引擎回调委托类（由 YGOProActivity 按 // === 分栏拆分而来）：承载 GameEngine.EngineListener
 * 的全部回调实现（状态/字段/阶段/选择/结果/录像/动画/玩家等待转发），并内聚与其强相关的
 * 决斗结束录像处理队列、居中特效覆盖层（SpecEffectOverlay）、阶段文字码与本地玩家显示名解析。
 * 与门面同包，通过包级私有直连 YGOProActivity 的共享状态字段与 UI 编排方法，不引入 Context 对象。
 */
class EngineCallbackDelegate implements GameEngine.EngineListener {

    private static final String TAG = "YGONativeGame";

    private final YGOProActivity activity;

    EngineCallbackDelegate(YGOProActivity activity) {
        this.activity = activity;
    }

    // === 决斗结束 / 录像处理状态（随回调内聚于本类） ===
    private final List<byte[]> pendingReplays = new ArrayList<>();
    private boolean duelEndHandling = false;
    // 显式退出等待界面时置位：本次 DISCONNECTED 回调跳过 returnToLanMain，
    // 返回导航由退出入口独占（bot→SingleModeDialog / LAN→LanModeDialog），
    // 避免 LanModeDialog 与 MainMenuDialog 连带弹出
    private boolean suppressDisconnectedReturn = false;
    private YesOrNoDialog resultDialog;
    private ReplaySaveDialog replaySaveDialog;
    // STOC_REPLAY 紧随 STOC_DUEL_END 下发：录像处理延迟到该窗口内无新数据到达再启动，
    // 避免在录像数据尚未收齐时就走到「决斗结束」弹窗
    private static final long REPLAY_ARRIVAL_WAIT_MS = 800;
    private final Runnable duelEndReplayProcessor = new Runnable() {
        @Override
        public void run() {
            processPendingReplays();
        }
    };

    // === 居中特效覆盖层（随回调内聚于本类） ===
    private SpecEffectOverlay specEffectOverlay;

    // 回合归属（本地视角：0=我方），由 onPhaseChanged/onTurnStarted 维护
    private boolean isMyTurn = false;

    // === EngineListener ===

    @Override
    public void onStateChanged(GameEngine.GameState newState) {
        Log.i(TAG, "State: " + newState);
        switch (newState) {
            case LOBBY:
                duelEndHandling = false;
                // 已通过 PlayerWaitingDialog 显示玩家等待界面时无需处理；否则隐藏主菜单
                if (activity.playerWaitingDialog == null || !activity.playerWaitingDialog.isShowing()) {
                    activity.getMainMenuDialog().hideMainMenu();
                }
                break;
            case DECK_SELECT:
                activity.getDialogUtil().showDeckSelectDialog();
                break;
            case HAND_SELECT:
                activity.enterDuelingUI();
                activity.getDialogUtil().resetRpsResultState();
                activity.getDialogUtil().showHandSelectDialog();
                break;
            case TP_SELECT:
                activity.enterDuelingUI();
                activity.getDialogUtil().showTPSelectDialog();
                break;
            case DUELING:
                activity.enterDuelingUI();
                activity.cardDetailPanel.showBottomActions();
                // 对齐 duelclient.cpp L912-916：STOC_GAME_START 按 chkDefaultShowChain 初始化时点三态
                activity.cardDetailPanel.onDuelStarted();
                // 进入决斗后仅对战玩家显示投降按钮（观战者 selfType>=7 不显示）；
                // 猜拳/选先后阶段保持隐藏（onGameUIShown 已默认隐藏）
                int selfSeat = activity.engine.getClient().selfType;
                activity.cardDetailPanel.setSurrenderVisible(selfSeat >= 0 && selfSeat < 7);
                pendingReplays.clear();
                duelEndHandling = false;
                break;
            case SIDING:
                activity.showDeckEditorView();              // 打开卡组编辑器
                if (activity.deckEditorManager != null) {
                    activity.deckEditorManager.enterSideMode();  // 记录替换前张数并允许编辑
                }
                break;
            case DUEL_END:
                activity.cardDetailPanel.closeGameButtons();
                duelEndHandling = true;
                if (activity.dialogUtil != null) activity.dialogUtil.dismissOpenGameDialogs();
                if (resultDialog != null) {
                    resultDialog.dismiss();
                    resultDialog = null;
                }
                if (activity.engine != null) activity.engine.disconnect();
                // 顺序：先处理通讯发来的录像（保存/取消），全部完成后再弹「决斗结束」对话框
                scheduleReplayProcessing();
                break;
            case DISCONNECTED:
                if (duelEndHandling) break; // 决斗结束流程已接管返回逻辑，避免重复
                if (suppressDisconnectedReturn) {
                    suppressDisconnectedReturn = false; // 退出入口已接管导航，单次消费
                    break;
                }
                activity.returnToLanMain(activity.isGameStarted ? "与服务器连接已断开" : null);
                break;
        }
    }

    /** 抑制下一次 DISCONNECTED 自动 returnToLanMain（由退出等待界面入口在 disconnect 前置位） */
    void suppressNextDisconnectedReturn() {
        suppressDisconnectedReturn = true;
    }

    @Override
    public void onFieldChanged() {
        activity.fieldCtl.invalidate();
        activity.runOnUiThread(() -> activity.topInfoManager.updateCardCountDisplay(activity.engine.getField()));
    }

    @Override
    public void onPlayerInfoUpdated(int player) {
        activity.runOnUiThread(() -> {
            // player 为本地视角索引（0=我方）；playerInfos 按座位号存储（STOC_HS_PLAYER_ENTER），
            // 我方名称取 selfType 座位、对方取另一座位（1v1），越界座位回退默认名
            int selfSeat = activity.engine.getClient().selfType;
            int seat = (player == 0) ? selfSeat : (selfSeat ^ 1);
            GameEngine.PlayerInfo info = (seat >= 0 && seat < activity.engine.playerInfos.length)
                    ? activity.engine.playerInfos[seat] : null;
            GameField.PlayerField pf = activity.engine.getField().players[player];
            String defaultName = (player == 0) ? Constants.PlayerName : "Opponent";
            String name = (info == null || info.name.isEmpty()) ? defaultName : info.name;
            activity.topInfoManager.setPlayerDisplay(player, name, String.valueOf(pf.lp));
            activity.topInfoManager.updateLpBars(activity.engine.getField());
            activity.topInfoManager.updateCardCountDisplay(activity.engine.getField());
        });
    }

    @Override
    public void onPhaseChanged(int phase) {
        activity.runOnUiThread(() -> {
            isMyTurn = (activity.engine.getField().currentPlayer == 0);
            activity.topInfoManager.updateTurn(activity.engine.getField().turnCount, isMyTurn);
            activity.fieldCtl.updateActionButtonsForPhase(phase, isMyTurn);
            // case 101：阶段文字跟随通讯切换（DuelPhase → showcardcode 4~9）
            int textCode = phaseTextCode(phase);
            if (textCode > 0) specEffect().showText(textCode);
        });
    }

    /**
     * MSG_NEW_TURN（对齐 duelclient.cpp L2865-2877）：
     * 回合方切换的第一时间同步 LPBarFrame 彩色/灰色（drawing.cpp L996-1003），
     * 并显示左侧面板的三个时点按钮、刷新其按下态
     */
    @Override
    public void onTurnStarted(int player) {
        activity.runOnUiThread(() -> {
            // player 为本地视角索引（GameEngine.onNewTurn 已做 localPlayer 转换）：0=我方回合
            isMyTurn = (player == 0);
            activity.topInfoManager.updateTurn(activity.engine.getField().turnCount, isMyTurn);
            if (activity.cardDetailPanel != null) activity.cardDetailPanel.showChainButtons();
        });
    }

    @Override
    public void onChatReceived(int playerType, String message) {
        activity.runOnUiThread(() -> activity.fieldCtl.appendChat(playerType, message));
    }

    @Override
    public void onSelectRequired(int selectType, ByteBuffer data) {
        activity.runOnUiThread(() -> {
            // 结束阶段按钮仅在通讯允许进入 EP 时有效：
            // 空闲指令(11)/战斗指令(10) 路径内会按指令可用性重新启用；其余请求一律隐藏
            activity.fieldCtl.setEpButtonAllowed(selectType == 10 || selectType == 11);
            activity.cardDetailPanel.setSelectType(selectType);
            ShowDialogUtil showDialogUtil = activity.getDialogUtil();
            switch (selectType) {
                case 0:
                    showDialogUtil.showHandSelectDialog();
                    break;
                case 1:
                    showDialogUtil.showTPSelectDialog();
                    break;
                case 10:
                    showDialogUtil.showBattleCmdDialog(data);
                    break;
                case 11:
                    showDialogUtil.showIdleCmdDialog(data);
                    break;
                case 12:
                    showDialogUtil.showEffectYnDialog(data);
                    break;
                case 13:
                    showDialogUtil.showYesNoDialog(data);
                    break;
                case 14:
                    showDialogUtil.showOptionDialog(data);
                    break;
                case 15:
                    showDialogUtil.showCardSelectDialog(data);
                    break;
                case 16:
                    showDialogUtil.showChainSelectDialog(data);
                    break;
                case 18:
                    // 对齐 gframe MSG_SELECT_PLACE：先按 chkMAutoPos/chkSTAutoPos 尝试自动放置，失败再弹选择框
                    if (!activity.fieldCtl.tryAutoPlaceSelect()) {
                        showDialogUtil.showPlaceSelectDialog(false);
                    }
                    break;
                case 19:
                    showDialogUtil.showPositionSelectDialog(data);
                    break;
                case 20:
                    showDialogUtil.showTributeSelectDialog(data);
                    break;
                case 21:
                    showDialogUtil.showSortChainDialog(data);
                    break;
                case 22:
                    showDialogUtil.showCounterSelectDialog(data);
                    break;
                case 23:
                    showDialogUtil.showSumSelectDialog(data);
                    break;
                case 24:
                    showDialogUtil.showPlaceSelectDialog(true);
                    break;
                case 25:
                    showDialogUtil.showSortCardDialog(data);
                    break;
                case 26:
                    showDialogUtil.showUnselectCardDialog(data);
                    break;
                case 27:
                    showDialogUtil.showConfirmCardsDialog(data);
                    break;
                case 140:
                    showDialogUtil.showAnnounceRaceDialog(data);
                    break;
                case 141:
                    showDialogUtil.showAnnounceAttribDialog(data);
                    break;
                case 142:
                    showDialogUtil.showAnnounceCardDialog(data);
                    break;
                case 143:
                    showDialogUtil.showAnnounceNumberDialog(data);
                    break;
                default:
                    Log.w(TAG, "Unhandled select type: " + selectType);
                    break;
            }
        });
    }

    @Override
    public void onDuelResult(int winner, int reason) {
        activity.topInfoManager.stopTimer();
        activity.runOnUiThread(() -> {
            boolean selfWon = winner != 2 && activity.engine.isSelfSide(winner);
            int code = winner == 2 ? SpecEffectOverlay.TEXT_DRAW_GAME
                    : (selfWon ? SpecEffectOverlay.TEXT_YOU_WIN
                       : SpecEffectOverlay.TEXT_YOU_LOSE);
            // 对齐 duelclient.cpp MSG_WIN："[X] 原因" 前缀里的 X 是【败方】昵称
            //（duelclient.cpp STOC_DUEL_START：hostname=我方 self、clientname=对方；
            //  LocalPlayer(winner)==0 即我方胜 → 用 clientname(对方=败者)；否则我方负 → 用 hostname(我方=败者)）。
            // 之前误传胜者名，后攻时表现为一胜一负名字对调。playerDisplayName(0)=我方、(1)=对方。
            String vicName = (winner == 2) ? null
                    : playerDisplayName(selfWon ? 1 : 0);
            specEffect().showWinText(code, reason, vicName);

        });
    }

    /**
     * 取本地视角玩家（0=我方，1=对方）的显示名，复用 onPlayerInfoUpdated 的座位映射逻辑，
     * 用于 MSG_WIN 胜利说明的 "[败者名] 原因" 前缀（对齐 duelclient.cpp L1586-1599）
     */
    private String playerDisplayName(int localIndex) {
        int selfSeat = activity.engine.getClient().selfType;
        int seat = (localIndex == 0) ? selfSeat : (selfSeat ^ 1);
        GameEngine.PlayerInfo info = (seat >= 0 && seat < activity.engine.playerInfos.length)
                ? activity.engine.playerInfos[seat] : null;
        String defaultName = (localIndex == 0) ? Constants.PlayerName : "Opponent";
        return (info == null || info.name.isEmpty()) ? defaultName : info.name;
    }

    /**
     * tag 模式队友请求投降（对齐 STOC_TEAMMATE_SURRENDER + sysString 1355）：
     * 弹出询问框，本方同意后再次发送 CTOS_SURRENDER，
     * 服务器（tag_duel.cpp Surrender）检测到双方均投降才会判定 MSG_WIN
     */
    @Override
    public void onTeammateSurrenderRequest() {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        YesOrNoDialog dialog = new YesOrNoDialog(activity);
        dialog.setMessage(activity.mStringManager.getSystemString(1355, "投降(1/2)"))
                .setType(YesOrNoDialog.TYPE_YES_NO)
                .setPositiveButtonText(activity.mStringManager.getSystemString(1213, "是"))
                .setNegativeButtonText(activity.mStringManager.getSystemString(1214, "否"))
                .setPositiveButton(v -> {
                    if (activity.engine != null) activity.engine.sendSurrender();
                })
                .setNegativeButton(v -> { /* 拒绝：保持对局，双方未全部同意，服务器不会判定投降 */ })
                .setCenterInView(activity.layoutGameRight)
                .setCancelable(false);
        dialog.show();
    }

    @Override
    public void onHintMessage(String hint) {
        activity.runOnUiThread(() -> activity.fieldCtl.showHint(hint, 2000));
    }

    @Override
    public void onDuelHint(String hint) {
        activity.runOnUiThread(() -> activity.fieldCtl.showDuelHint(hint));
    }

    @Override
    public void onDuelHintHide() {
        activity.runOnUiThread(() -> activity.fieldCtl.hideDuelHint());
    }

    @Override
    public void onReplayData(byte[] data) {
        Log.i(TAG, "Replay data received, size=" + data.length);
        activity.runOnUiThread(() -> {
            pendingReplays.add(data);
            // 决斗结束流程中通讯仍在补发录像：重置等待窗口，确保队列收全后再开始处理
            if (duelEndHandling) {
                scheduleReplayProcessing();
            }
        });
    }

    @Override
    public void onTimeLimitUpdate(int player, int leftTime) {
        activity.runOnUiThread(() -> activity.topInfoManager.onTimeLimitUpdate(player, leftTime, activity.engine.getGameTimeLimit()));
    }

    @Override
    public void onChainAnimation(int code, int controler, int location, int sequence) {
        activity.runOnUiThread(() -> {
            activity.fieldCtl.selectCardWithAutoClear(controler, location, sequence, 1500);
            specEffect().showActivate(code);          // case 1：发动卡片大图
        });
    }

    @Override
    public void onSummonAnimation(int code, int summonType) {
        activity.runOnUiThread(() -> {
            if (summonType == GameEngine.SUMMON_SPECIAL) {
                specEffect().showSpecialSummon(code); // case 5：特殊召唤，放大 + 淡入
            } else {
                specEffect().showSummon(code);        // case 7：通常/反转召唤，翻面进入
            }
        });
    }

    @Override
    public void onNegatedAnimation(int code) {
        // case 3：效果无效（破坏被无效即"不会被破坏"），居中卡片 + 无效图标
        activity.runOnUiThread(() -> specEffect().showNegated(code));
    }

    @Override
    public boolean isSpecEffectBusy() {
        // 统一动画屏障的特效侧查询：直接读字段（不用 specEffect() 以免按需创建），
        // 覆盖层未创建即视为空闲，供 GameEngine 判断居中特效是否仍在播放
        return specEffectOverlay != null && specEffectOverlay.isBusy();
    }

    @Override
    public void onHandResult(int myHand, int oppHand) {
        activity.runOnUiThread(() -> activity.getDialogUtil().onHandResult(myHand, oppHand));
    }

    // === 玩家等待界面转发（引擎回调 → playerWaitingDialog.handleXxx） ===

    @Override
    public void onPlayerEnter(String name, int pos) {
        activity.runOnUiThread(() -> {
            if (activity.playerWaitingDialog != null) activity.playerWaitingDialog.handlePlayerEnter(name, pos);
        });
    }

    @Override
    public void onPlayerChange(int status) {
        activity.runOnUiThread(() -> {
            if (activity.playerWaitingDialog != null) activity.playerWaitingDialog.handlePlayerChange(status);
        });
    }

    @Override
    public void onWatchChange(int watchCount) {
        activity.runOnUiThread(() -> {
            if (activity.playerWaitingDialog != null) activity.playerWaitingDialog.handleWatchChange(watchCount);
        });
    }

    @Override
    public void onJoinGame(int lflist, int rule, int mode, int duelRule,
                           int noCheckDeck, int noShuffleDeck,
                           int startLp, int startHand, int drawCount, int timeLimit) {
        // 对齐 gframe duelclient.cpp L737：STOC_JOIN_GAME 到达即写入 duel_rule/start_lp，
        // 早于 MSG_START（猜拳之前），让场地贴图 field/field2/field3 与格子布局按 rule 提前分流
        activity.engine.field.dInfo.duelRule = duelRule;
        activity.engine.field.dInfo.startLp = startLp;
        activity.runOnUiThread(() -> {
            if (activity.playerWaitingDialog != null)
                activity.playerWaitingDialog.handleJoinGame(lflist, rule, mode, duelRule,
                        noCheckDeck, noShuffleDeck, startLp, startHand, drawCount, timeLimit);
        });
    }

    @Override
    public void onTypeChange(int type) {
        activity.runOnUiThread(() -> {
            if (activity.playerWaitingDialog != null) {
                boolean isTag = activity.engine.getGameMode() == 2;
                activity.playerWaitingDialog.handleTypeChange(type, isTag);
            }
        });
    }

    @Override
    public void onDeckError(int errorType, int cardCode) {
        activity.runOnUiThread(() -> {
            if (activity.playerWaitingDialog != null)
                activity.playerWaitingDialog.handleDeckError(errorType, cardCode);
        });
    }

    // === 决斗结束录像处理 ===

    /**
     * 决斗结束提示框：仅显示「确定」按钮（TYPE_MESSAGE）。
     * 弹窗时机已调整为：通讯发来的录像全部确认保存或取消之后（见 processPendingReplays），
     * 点击确定后隐藏决斗 UI 并重新显示 LanModeDialog
     */
    private void showDuelEndDialog() {
        if (activity.isFinishing() || activity.isDestroyed()) return;
        YesOrNoDialog dialog = new YesOrNoDialog(activity);
        dialog.setMessage(activity.mStringManager.getSystemString(1500, "決斗结束。"))
                .setType(YesOrNoDialog.TYPE_MESSAGE)
                .setPositiveButtonText(activity.mStringManager.getSystemString(1211, "确定"))
                .setPositiveButton(v -> activity.returnToLanMain(null))
                .setCenterInView(activity.layoutGameRight)
                .setCancelable(false);
        dialog.show();
    }

    /**
     * 延迟启动录像处理：先取消上一次调度，窗口内若有新录像到达（onReplayData）会再次重置，
     * 直到通讯不再发送录像才真正开始逐个弹出录像保存对话框
     */
    private void scheduleReplayProcessing() {
        activity.mainHandler.removeCallbacks(duelEndReplayProcessor);
        activity.mainHandler.postDelayed(duelEndReplayProcessor, REPLAY_ARRIVAL_WAIT_MS);
    }

    /**
     * 逐个处理通讯发来的录像：有待保存项则弹出录像保存对话框；
     * 全部处理完毕（保存或取消跳过）后才弹出「决斗结束」对话框
     */
    private void processPendingReplays() {
        // 防止延迟调度与队列内递归调用重叠执行
        activity.mainHandler.removeCallbacks(duelEndReplayProcessor);
        if (activity.isFinishing() || activity.isDestroyed()) {
            pendingReplays.clear();
            return;
        }
        if (pendingReplays.isEmpty()) {
            showDuelEndDialog();
            return;
        }
        final byte[] replayData = pendingReplays.remove(0);
        // 对齐 gframe duelclient.cpp STOC_REPLAY：勾选自动保存录像时不弹窗，
        // 直接以录像开始时间命名自动保存（对应提示 1367）
        if (AppsSettings.get().getIntSettings("chkAutoSaveReplay", 0) == 1) {
            saveReplayFile(replayData, getReplayDefaultName(replayData), true);
            activity.mainHandler.post(this::processPendingReplays);
            return;
        }
        replaySaveDialog = new ReplaySaveDialog(activity);
        replaySaveDialog.setDefaultName(getReplayDefaultName(replayData))
                .setCenterInView(activity.layoutGameRight)
                .setOnReplayActionListener(new ReplaySaveDialog.OnReplayActionListener() {
                    @Override
                    public void onSave(String fileName) {
                        saveReplayFile(replayData, fileName, false);
                        activity.mainHandler.post(() -> processPendingReplays());
                    }

                    @Override
                    public void onCancel() {
                        // 对齐 gframe duelclient.cpp STOC_REPLAY L1080-1082：
                        // 点「否」(actionParam==0) 时仍将最近一局静默保存为 _LastReplay.yrp
                        saveLastReplay(replayData);
                        // 继续检查通讯是否还发来了其它录像文件
                        activity.mainHandler.post(() -> processPendingReplays());
                    }
                });
        replaySaveDialog.show();
    }

    /**
     * 从通讯发来的录像数据中解析默认文件名，
     * 与 gframe duelclient.cpp STOC_REPLAY 一致：录像开始时间 %Y-%m-%d %H-%M-%S
     */
    private String getReplayDefaultName(byte[] data) {
        try {
            if (data == null || data.length < 24) return "_LastReplay";
            ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
            int id = buf.getInt();
            if (id != ReplayReader.REPLAY_ID_YRP1 && id != ReplayReader.REPLAY_ID_YRP2) {
                return "_LastReplay";
            }
            buf.getInt(); // version
            int flag = buf.getInt();
            int seed = buf.getInt();
            buf.getInt(); // datasize
            int startTime = buf.getInt();
            long ts = ((flag & ReplayReader.REPLAY_UNIFORM) != 0)
                    ? Integer.toUnsignedLong(startTime)
                    : Integer.toUnsignedLong(seed);
            return new SimpleDateFormat("yyyy-MM-dd HH-mm-ss", Locale.US)
                    .format(new Date(ts * 1000L));
        } catch (Exception e) {
            return "_LastReplay";
        }
    }

    private void saveReplayFile(byte[] data, String fileName, boolean autoSave) {
        String safeName = sanitizeReplayName(fileName);
        try {
            File dir = new File(AppsSettings.get().getReplayDir());
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, safeName + Constants.YRP_FILE_EX);
            FileOutputStream fos = new FileOutputStream(file);
            try {
                fos.write(data);
                fos.flush();
            } finally {
                fos.close();
            }
            Log.i(TAG, "Replay saved: " + file.getAbsolutePath());
            if (autoSave) {
                // 对齐 gframe 自動保存提示（系统字符串 1367「リプレイ自動保存 %ls.yrp」）：
                // 将 %ls 占位替换为实际保存的录像文件名
                String template = activity.mStringManager
                        .getSystemString(1367, "リプレイ自動保存 %ls.yrp");
                Toast.makeText(activity, template.replace("%ls", safeName), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(activity, activity.mStringManager
                        .getSystemString(1335, "保存成功"), Toast.LENGTH_SHORT).show();
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to save replay", e);
            Toast.makeText(activity, "录像保存失败: " + safeName, Toast.LENGTH_SHORT).show();
            // 对齐 gframe duelclient.cpp STOC_REPLAY L1078-1079：保存失败时兜底存为 _LastReplay.yrp
            saveLastReplay(data);
        }
    }

    /**
     * 静默保存最近一局为 _LastReplay.yrp（不弹任何提示）：
     * 对应 gframe duelclient.cpp STOC_REPLAY 中点「否」与保存失败两条
     * new_replay.SaveReplay(L"_LastReplay") 路径
     */
    private void saveLastReplay(byte[] data) {
        try {
            File dir = new File(AppsSettings.get().getReplayDir());
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, "_LastReplay" + Constants.YRP_FILE_EX);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                fos.write(data);
                fos.flush();
            }
            Log.i(TAG, "Last replay saved: " + file.getAbsolutePath());
        } catch (IOException e) {
            Log.e(TAG, "Failed to save last replay", e);
        }
    }

    private String sanitizeReplayName(String name) {
        String n = (name == null) ? "" : name.trim();
        if (n.toLowerCase(Locale.US).endsWith(Constants.YRP_FILE_EX)) {
            n = n.substring(0, n.length() - Constants.YRP_FILE_EX.length());
        }
        n = n.replaceAll("[\\\\/:*?\"<>|]", "").trim();
        if (n.isEmpty()) n = "_LastReplay";
        return n;
    }

    /**
     * 回放结束：用阶段文字（case 101）显示胜负 + 胜利原因（供门面 public showReplayResult 转发）。
     * 回放为观战视角，约定 player 0 胜=YOU WIN、player 1 胜=YOU LOSE、player 2=平局；
     * winner<0 表示回放自然播放完毕（无 MSG_WIN 判定），不显示胜负文字。
     */
    void showReplayResult(int winner, int reason, String winnerName) {
        if (winner < 0) return;
        int code = winner == 2 ? SpecEffectOverlay.TEXT_DRAW_GAME
                : (winner == 0 ? SpecEffectOverlay.TEXT_YOU_WIN
                   : SpecEffectOverlay.TEXT_YOU_LOSE);
        specEffect().showWinText(code, reason, winnerName);
    }

    // === 门面生命周期 / 返回流程对录像状态的复位 ===

    /** returnToLanMain：断线/决斗结束返回时清空待处理录像与相关标志 */
    void resetDuelEndState() {
        duelEndHandling = false;
        pendingReplays.clear();
        if (replaySaveDialog != null) {
            replaySaveDialog.dismiss();
            replaySaveDialog = null;
        }
    }

    /** onDestroy：取消尚未触发的录像处理调度 */
    void cancelReplayProcessing() {
        activity.mainHandler.removeCallbacks(duelEndReplayProcessor);
    }

    // === 居中特效覆盖层 / 阶段文字码 ===

    private SpecEffectOverlay specEffect() {
        if (specEffectOverlay == null) {
            specEffectOverlay = new SpecEffectOverlay(activity);
            // 特效队列排空 → 通知引擎重开消息闸门，实现「召唤/发动动画播完后再弹询问框」的串行序列
            specEffectOverlay.setOnIdleListener(() -> {
                if (activity.engine != null) activity.engine.notifySpecEffectIdle();
            });
        }
        return specEffectOverlay;
    }

    /**
     * MSG_NEW_PHASE 的 phase 値 → DrawSpec case 101 的 showcardcode（对齐 duelclient.cpp L2905-2929）：
     * Draw→4, Standby→5, Main1→6, BattleStart→7, Main2→8, End→9；
     * 战斗子阶段等无独立提示文字的相位返回 0（不显示阶段文字）
     */
    private int phaseTextCode(int phase) {
        DuelPhase dp = DuelPhase.valueOf(phase);
        if (dp == null) return 0;
        switch (dp) {
            case Draw:
                return SpecEffectOverlay.TEXT_DRAW_PHASE;
            case Standby:
                return SpecEffectOverlay.TEXT_STANDBY_PHASE;
            case Main1:
                return SpecEffectOverlay.TEXT_MAIN_PHASE_1;
            case BattleStart:
                return SpecEffectOverlay.TEXT_BATTLE_PHASE;
            case Main2:
                return SpecEffectOverlay.TEXT_MAIN_PHASE_2;
            case End:
                return SpecEffectOverlay.TEXT_END_PHASE;
            default:
                return 0;
        }
    }
}
