package cn.garymb.ygomobile.network;

import android.util.Log;

import java.nio.ByteBuffer;

import cn.garymb.ygomobile.audio.SoundManager;
import cn.garymb.ygomobile.game.GameEngine;

/**
 * STOC 通讯回调处理（自 DuelClient.StocHandler 平移为顶层类，逻辑零改；
 * 原为 DuelClient 静态内部类，故对 YGOProtocol 常量的裸引用统一补 YGOProtocol. 限定）：
 * 共享状态经 GameEngine 访问；游戏消息（onGameMsg）交由 GameEngine 的「消息串行闸门」排队派发。
 */
public class DuelStocHandler implements DuelClient.ClientListener {
    // 保持拆分前日志标识，便于与旧版日志比对
    private static final String TAG = "GameEngine";

    private final GameEngine engine;

    public DuelStocHandler(GameEngine engine) {
        this.engine = engine;
    }

    @Override
    public void onConnected() {
        Log.i(TAG, "Connected to server");
        // 新会话建立时作废上一次连接的房间信息缓存，避免等待界面补发陈旧规则
        engine.hasJoinRoomInfoCache = false;
        // 同理丢弃上一会话的服务端能力位（换成不支持撤回的服务器时不得残留按钮）
        engine.serverCapsUndo = false;
        engine.serverCapsAskResend = false;
        engine.undoAvailable = false;
        // 新连接同样复位撤回闩锁（onConnected 早于 onDuelStart）：上一局残留的
        // 「已撤回、等待新操作」状态不得带入新会话，否则本局按钮会被永久压住
        engine.undoBlockedUntilNewAction = false;
    }

    @Override
    public void onDisconnected() {
        Log.i(TAG, "Disconnected from server");
        if (engine.getState() != GameEngine.GameState.DUEL_END) {
            engine.setEngineState(GameEngine.GameState.DISCONNECTED);
        }
    }

    @Override
    public void onError(String message) {
        Log.e(TAG, "Network error: " + message);
    }

    @Override
    public void onPacketReceived(int proto, ByteBuffer data) {
        Log.d(TAG, "Unhandled packet: " + String.format("0x%02X", proto));
    }

    @Override
    public void onChatMessage(int playerType, String message) {
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onChatReceived(playerType, message);
        });
        engine.soundManager.playSoundEffect(SoundManager.SFX.CHAT);
    }

    @Override
    public void onPlayerEnter(String name, int pos) {
        Log.i(TAG, "Player entered: " + name + " at pos " + pos);
        if (pos < engine.playerInfos.length) {
            engine.playerInfos[pos].name = name;
        }
        // 座位 0-3 全量记录：tag 模式下 pos1/pos3 为双方 tag 同伴，聊天昵称需要
        if (pos >= 0 && pos < engine.seatNames.length) {
            engine.seatNames[pos] = name;
        }
        engine.soundManager.playSoundEffect(SoundManager.SFX.PLAYER_ENTER);
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onPlayerEnter(name, pos);
        });
    }

    @Override
    public void onPlayerChange(int status) {
        Log.i(TAG, "Player change: " + String.format("0x%02X", status));
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onPlayerChange(status);
        });
    }

    @Override
    public void onWatchChange(int watchCount) {
        Log.i(TAG, "Watch count changed: " + watchCount);
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onWatchChange(watchCount);
        });
    }

    @Override
    public void onDeckCount(int deck0, int extra0, int side0, int deck1, int extra1, int side1) {
        // STOC_DECK_COUNT 在 STOC_DUEL_START 之后、MSG_START 之前下发双方卡组/额外/副卡组数量，
        // 供猜拳阶段在场地展示「卡组堆叠 / 额外卡组堆叠 / 除外区堆叠(显示副卡组数量)」。
        // 对齐 duelclient.cpp STOC_DECK_COUNT L584-598：C++ 用字面量 Initial(0)/Initial(1)（本地视角），
        // 故此处不经 localPlayer 映射；field 已由 onDuelStart 清空，这里只填充不重复 clear。
        engine.field.initial(0, deck0, extra0, side0);
        engine.field.initial(1, deck1, extra1, side1);
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onFieldChanged();
        });
    }

    @Override
    public void onDuelStart() {
        engine.field.clear();
        // 新一场决斗（含 match 三局的开场包）：丢弃上一场未与录像配对的残留 MSG 段
        engine.resetMsgRecording();
        // 作废上一局可能残留的撤回 latch（ACK 先到、reload 因断线未落地），
        // 否则下一局的普通 MSG_RELOAD_FIELD 会把陈旧的回合数回填进去
        engine.clearUndoResync();
        // 新一局：服务端会在 startDuel 末尾重新下发可撤回状态，此处先复位避免带入上一局
        engine.undoAvailable = false;
        // 新一局重新开始：作废上一局的应答/询问时戳与求援计数，避免局间残留误发 CTOS_ASK_RESEND
        engine.resetAskWatchdog();
        // 对齐 STOC_JOIN_GAME 的 dInfo.isTag = (mode==2)：field.isTag 此前无实况侧赋值点，
        // 进决斗时按已缓存的房间 gameMode 显式置位（tag 名字/手卡切换与 LP 减半显示的前提），
        // 并复位上一局的 tag_player（对齐 replay_mode.cpp 的 tag_player 复位），
        // 再按大厅座位绑定本地视角昵称（对齐 STOC_DUEL_START 填 hostname/clientname）
        engine.field.isTag = engine.gameMode == 2;
        engine.tagPlayer[0] = false;
        engine.tagPlayer[1] = false;
        // 新一局开始复位 solo 换 side 席位会话计数（match 每局之间的换 side 从席位 0 重新引导）
        engine.soloSideSession = 0;
        engine.bindViewNames();
        engine.duelStarted = true;
        engine.inDuel = false;
        engine.siding = false;
        engine.tagSurrenderInitiated = false;
        engine.duelStage = YGOProtocol.DUEL_STAGE_DUELING;
        engine.setEngineState(GameEngine.GameState.DUELING);
        // 不在此直接切决斗曲：STOC_DUEL_START 时仍停留在玩家等待界面（猜拳前），
        // 紧接的 DUELING 状态回调 enterDuelingUI→showGameUI 显示 layout_game_right 时
        // 经 updateBGM 按 DUEL 场景起播
        // 对局开场清理残留提示（对齐 game.cpp CloseGameWindow L2426 stHintMsg->setVisible(false)）
        engine.hintManager.stopWaitHint();
        engine.hintManager.postDuelHintHide();
    }

    @Override
    public void onDuelEnd() {
        engine.duelStarted = false;
        engine.inDuel = false;
        engine.siding = false;
        engine.tagSurrenderInitiated = false;
        engine.duelStage = YGOProtocol.DUEL_STAGE_END;
        engine.setEngineState(GameEngine.GameState.DUEL_END);
        engine.soundManager.stopBGM();
        engine.hintManager.stopWaitHint();
        engine.hintManager.postDuelHintHide();
    }

    @Override
    public void onReplay(byte[] data) {
        Log.i(TAG, "Replay data received from server, size=" + data.length);
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onReplayData(data);
        });
    }

    @Override
    public void onGameMsg(int msgType, ByteBuffer data) {
        // 入队后由 GameEngine 闸门串行派发：动画消息会关闭闸门，暂缓后续消息
        // （对齐 C++ WaitFrameSignal 阻塞语义，队列/闸门逻辑在 GameEngine）
        engine.enqueueGameMsg(msgType, data);
    }

    @Override
    public void onHandSelect() {
        engine.setEngineState(GameEngine.GameState.HAND_SELECT);
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onSelectRequired(0, null);
        });
    }

    @Override
    public void onTPSelect() {
        engine.setEngineState(GameEngine.GameState.TP_SELECT);
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onSelectRequired(1, null);
        });
    }

    @Override
    public void onHandResult(int res1, int res2) {
        Log.i(TAG, "Hand result: " + res1 + " vs " + res2);
        // 对齐 duelclient.cpp L528：STOC_HAND_RESULT 公布猜拳结果时隐藏提示
        engine.hintManager.postDuelHintHide();
        // STOC_HAND_RESULT 按服务器视角下发 player0/player1 手势，转换为本方视角
        int self = engine.client.selfType;
        final int myHand = (self == 1) ? res2 : res1;
        final int oppHand = (self == 1) ? res1 : res2;
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onHandResult(myHand, oppHand);
        });
    }

    @Override
    public void onChangeSide() {
        engine.duelStarted = false;
        engine.inDuel = false;
        engine.siding = true;
        engine.duelStage = YGOProtocol.DUEL_STAGE_SIDING;
        // Solo 连续换 side：上一份 side 提交后编辑器已隐藏但引擎状态仍停留在 SIDING，
        // 服务端随即下发第二条 CHANGE_SIDE。setEngineState 因状态未变会去重、不回调，
        // 导致下一席位换 side 界面无法重新载入（表现为编辑器消失后不再出现）。
        // 此处识别重入，强制重新派发 SIDING 回调以重建界面；首次进入（状态非 SIDING）仍走状态回调。
        if (engine.getState() == GameEngine.GameState.SIDING) {
            engine.redispatchState(GameEngine.GameState.SIDING);
        } else {
            engine.setEngineState(GameEngine.GameState.SIDING);
        }
    }

    @Override
    public void onWaitingSide() {
        engine.inDuel = false;
        Log.i(TAG, "Waiting for side change");
        // 对齐 duelclient.cpp L575-580：STOC_WAITING_SIDE 显示"等待换备卡"
        engine.hintManager.stopWaitHint();
        engine.hintManager.postDuelHint(engine.hintManager.sysString(1409, "等待对方换备卡..."));
    }

    @Override
    public void onTimeLimit(int player, int leftTime) {
        // 协议侧玩家索引统一转本地视角（0=我方），我方为后攻时倒计时也落入我方布局
        final int p = engine.localPlayer(player & 1);
        // 对齐 duelclient.cpp L1091-1093：限时局收到 STOC_TIME_LIMIT 且轮到我方时立即回
        // CTOS_TIME_CONFIRM——服务端 WaitforResponse 在限时把 state 置 CTOS_TIME_CONFIRM
        // （single_duel.cpp L1480），包门控（netserver.cpp L417）会静默丢弃此后我方的一切
        // CTOS_RESPONSE，直到 TimeConfirm 把 state 改回 CTOS_RESPONSE；漏发即表现为
        // “操作一两个发动后卡死、无断线提示”（233 等开限时的服务器必现）
        if (p == 0) engine.sendTimeConfirm();
        if (engine.field.dInfo.timeLimit <= 0) {
            engine.field.dInfo.timeLimit = Math.max(engine.gameTimeLimit, leftTime);
        }
        engine.field.dInfo.timePlayer = p;
        engine.field.dInfo.timeLeft[p] = leftTime;
        engine.field.resetTimeTick();
        engine.field.refreshTimeDisplay();
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onTimeLimitUpdate(p, leftTime);
        });
    }

    @Override
    public void onErrorMsg(int msg, int code) {
        String errorMsg;
        switch (msg) {
            case YGOProtocol.ERRMSG_JOINERROR:
                errorMsg = "无法加入房间";
                break;
            case YGOProtocol.ERRMSG_DECKERROR: {
                int errorType = (code >> 28) & 0xF;
                int cardCode = code & 0x0FFFFFFF;
                engine.mainHandler.post(() -> {
                    if (engine.listener != null) engine.listener.onDeckError(errorType, cardCode);
                });
                return;
            }
            case YGOProtocol.ERRMSG_SIDEERROR:
                errorMsg = "副卡组错误";
                break;
            case YGOProtocol.ERRMSG_VERERROR:
                errorMsg = "版本不匹配";
                break;
            default:
                errorMsg = "未知错误: " + msg;
                break;
        }
        Log.e(TAG, "Server error: " + errorMsg);
        final String finalMsg = errorMsg;
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onNoticeMessage(finalMsg);
        });
    }

    @Override
    public void onTypeChange(int type) {
        Log.i(TAG, "Type changed to: " + type);
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onTypeChange(type);
        });
        engine.setEngineState(GameEngine.GameState.LOBBY);
    }

    @Override
    public void onTeammateSurrender() {
        // tag_duel.cpp Surrender 会把 STOC_TEAMMATE_SURRENDER 同时发给发起方与队友；
        // 发起方只是知会（已在等待队友，不再弹窗），未发起的队友才弹出“是否同意投降”确认框
        if (engine.tagSurrenderInitiated) return;
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onTeammateSurrenderRequest();
        });
    }

    @Override
    public void onUndoResult(int result, int turn, int currentPlayer, int phase) {
        // ACK 不进动画闸门（它不是对局消息），直接把回退后的回合/阶段 latch 到引擎，
        // 由随后的 MSG_RELOAD_FIELD 落地时消费（reload 不带回合号，也不重发 MSG_START）
        Log.i(TAG, "Undo ack: result=" + result + " turn=" + turn + " player=" + currentPlayer
                + " phase=0x" + Integer.toHexString(phase));
        engine.onUndoAck(result, turn, currentPlayer, phase);
    }

    @Override
    public void onUndoState(int canUndo) {
        // 不进动画闸门：它只是提示状态，不改变局面。落到引擎后由宿主驱动撤回图标的闪动，
        // 使「能点」与「点了可能被拒绝」不再靠猜（服务端只在本席位确有动作锚点时才回 1）
        engine.undoAvailable = canUndo != 0;
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onUndoStateChanged(engine.undoAvailable);
        });
    }

    @Override
    public void onJoinGame(int lflist, int rule, int mode, int duelRule,
                           int noCheckDeck, int noShuffleDeck,
                           int startLp, int startHand, int drawCount, int timeLimit,
                           int extCaps) {
        // 服务端能力位（HostInfo pad 扩展）：支持撤回时在连入方也点亮撤回按钮，
        // 让“谁操作谁撤回”在两台设备对局时同样成立（房主端另经 isHost 保证）
        engine.serverCapsUndo = (extCaps & YGOProtocol.HOST_CAP_UNDO) != 0;
        // bit1：服务端会在询问可能被丢掉时重发（CTOS_ASK_RESEND），看门狗仅在它为真时布防
        engine.serverCapsAskResend = (extCaps & YGOProtocol.HOST_CAP_ASK_RESEND) != 0;
        // Solo mode is encoded in mode bit 4 (0x10). Strip it before storing gameMode so
        // existing comparisons (gameMode==2 for tag / ==MODE_MATCH etc.) keep working.
        engine.soloMode = (mode & 0x10) != 0;
        int duelModeOnly = mode & 0x0F;
        engine.playerInfos[0].startLp = startLp;
        engine.playerInfos[1].startLp = startLp;
        engine.playerInfos[0].lp = startLp;
        engine.playerInfos[1].lp = startLp;
        engine.maxMatch = (duelModeOnly == YGOProtocol.MODE_MATCH) ? 3 : 1;
        engine.gameMode = duelModeOnly;
        engine.gameRule = rule;
        engine.gameLflist = lflist;
        engine.gameStartLp = startLp;
        engine.gameStartHand = startHand;
        engine.gameDrawCount = drawCount;
        engine.gameTimeLimit = timeLimit;
        engine.gameNoCheckDeck = noCheckDeck;
        engine.gameNoShuffleDeck = noShuffleDeck;
        engine.field.dInfo.timeLimit = timeLimit;
        engine.field.dInfo.startLp = startLp;
        engine.field.dInfo.lp[0] = startLp;
        engine.field.dInfo.lp[1] = startLp;
        // 缓存完整房间信息（含 duelRule 已写入 dInfo），供 PlayerWaitingDialog 就绪后补发
        engine.hasJoinRoomInfoCache = true;
        final int fwdMode = duelModeOnly;
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onJoinGame(lflist, rule, fwdMode, duelRule,
                    noCheckDeck, noShuffleDeck,
                    startLp, startHand, drawCount, timeLimit);
        });
        engine.setEngineState(GameEngine.GameState.LOBBY);
    }
}
