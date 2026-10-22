package cn.garymb.ygomobile.game.engine;
import cn.garymb.ygomobile.game.gamefield.ClientCard;
import cn.garymb.ygomobile.game.gamefield.GameField;
import cn.garymb.ygomobile.game.replay.ReplayPlayer;

import android.os.Handler;
import android.os.Looper;

import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.audio.SoundManager;
import cn.garymb.ygomobile.engine.LuaScriptEngine;
import cn.garymb.ygomobile.network.DuelClient;
import cn.garymb.ygomobile.network.DuelStocHandler;
import cn.garymb.ygomobile.network.YGOProtocol;

/** 引擎统一门面：对外公共 API 不变；行为逻辑按 // === 分栏拆至同包协作类，构造注入并一行转发：DuelHintManager / SummonAnimationManager / DeckHandMotionManager / ConnectionManager / LobbyActions / GameActions / CommandDataParser / DuelEventHandler / GameMessageParser / ReplayPlayer / DuelStocHandler / UndoClientCoordinator / MsgFrameRecorder / AskWatchdog / MsgDispatchGate（消息串行闸门）/ DuelViewpointManager（对局视角）；本体仅留共享状态、状态机与命令状态。 */
public class GameEngine {
    private static final String TAG = "GameEngine";

    public enum GameState {
        IDLE,
        CONNECTING,
        LOBBY,
        DECK_SELECT,
        HAND_SELECT,
        TP_SELECT,
        DUELING,
        SIDING,
        DUEL_END,
        DISCONNECTED
    }

    public interface EngineListener {
        void onStateChanged(GameState newState);

        void onFieldChanged();

        void onPlayerInfoUpdated(int player);

        void onPhaseChanged(int phase);

        /** MSG_NEW_TURN（duelclient.cpp L2865-2877）：刷新时点按钮，player 为本地视角回合玩家 */
        void onTurnStarted(int player);

        void onChatReceived(int playerType, String message);

        void onSelectRequired(int selectType, ByteBuffer data);

        void onDuelResult(int winner, int reason);

        void onHintMessage(String hint);

        /** Android 特有知会通道（连接/错误/撤回/操作反馈等非通讯文本）：文本显示功能已彻底移除，默认空实现，既有调用点不再产生任何展示 */
        default void onNoticeMessage(String msg) {}

        /** MSG_HINT 居中文本动画（duelclient.cpp L1463-1521）：sys1510/1511/1512，SpecEffectOverlay 串行队列展示 */
        default void onActionMessage(String text) {}

        /** 通讯提示栏（对齐 gframe stHintMsg）：显示后持续，直到下一条消息或显式隐藏 */
        void onDuelHint(String hint);

        /** 通讯提示栏隐藏（对齐 duelclient.cpp ClientAnalyze 开头 stHintMsg->setVisible(false)） */
        void onDuelHintHide();

        void onReplayData(byte[] data);

        void onTimeLimitUpdate(int player, int leftTime);

        void onChainAnimation(int code, int controler, int location, int sequence);

        void onPlayerEnter(String name, int pos);

        void onPlayerChange(int status);

        void onWatchChange(int watchCount);

        void onJoinGame(int lflist, int rule, int mode, int duelRule,
                        int noCheckDeck, int noShuffleDeck,
                        int startLp, int startHand, int drawCount, int timeLimit);

        void onTypeChange(int type);

        void onDeckError(int errorType, int cardCode);

        /** 猜拳结果（STOC_HAND_RESULT），均为本方视角的手势常量（1=剪刀 2=石头 3=布） */
        void onHandResult(int myHand, int oppHand);

        /** 召唤居中动画（MSG_SUMMONING/SPSUMMONING/FLIPSUMMONING）：summonType 取 SUMMON_NORMAL/SPECIAL/FLIP */
        void onSummonAnimation(int code, int summonType);

        /** 效果无效居中动画（MSG_CHAIN_NEGATED/DISABLED，showcard=3）：code 为被无效连锁卡码 */
        void onNegatedAnimation(int code);

        /** SpecEffectOverlay.isBusy()：供统一动画屏障查询 */
        boolean isSpecEffectBusy();

        /** tag 模式：队友请求投降，本方需确认是否同意（对齐 STOC_TEAMMATE_SURRENDER + sysString 1355） */
        default void onTeammateSurrenderRequest() {}

        /** rewind 重排时清空聊天显示防重复 */
        default void onReplayChatReset() {}

        /** 观战切视角：宿主把双方聊天左右对调 */
        default void onViewpointSwapped() {}

        /** 撤回被服务端接受：关旧询问弹窗与蚂陈线，重同步后服务端重挂询问 */
        default void onUndoResync() {}

        /** 撤回被拒：点击撤回时即时隐藏了在显询问弹窗，若屏障期间未丢弃过应答（questionRedelivered=false，无重派路径接管）须恢复这批弹窗，否则待答询问永久不可见无法应答；丢弃过应答时询问已按新询问重新派发显示 */
        default void onUndoDenied(boolean questionRedelivered) {}

        /** 可撤回状态变更（STOC_UNDO_STATE）：据此控制顶部撤回图标闪动 */
        default void onUndoStateChanged(boolean available) {}

        /** 回放重排（上一步/从头重放）渲染冻结屏障：hold=true 同时冻结 GL 渲染（重算权威态期间保留当前帧不上屏）并抑制/清空 SpecEffectOverlay 居中特效；hold=false 落点后统一解除（驱动逆向补间与恢复正常特效接收）；默认空实现 */
        default void onReplayRenderHold(boolean hold) {}
    }

    // 核心状态与基础设施（协作类经 engine. 引用访问：同包类用包级私有，network 包的 DuelStocHandler 需 public）
    private GameState state = GameState.IDLE;
    public final DuelClient client;
    public final SoundManager soundManager;
    public final GameField field;
    LuaScriptEngine scriptEngine;
    public EngineListener listener;
    public final Handler mainHandler = new Handler(Looper.getMainLooper());

    /** 消息串行闸门（duelclient.cpp ClientAnalyze + WaitFrameSignal）：主线程队列 + 动画闸门，全在主线程执行无需加锁 */
    final ArrayDeque<Runnable> pendingMsgs = new ArrayDeque<>();
    volatile boolean dispatchingMsg = false;    // 正在派发一条消息（防重入；回放投喂线程亦读取）
    boolean animGateClosed = false;    // 动画播放期间关闭闸门，暂缓后续消息
    /** 确认卡片对话框持闸（对齐 C++ MSG_CONFIRM_CARDS 显示面板后 actionSignal.Wait() 阻塞消息线程）：置位期间轮询器/超时兜底/idle 快路径一律不得重开闸门继续派发，直到对话框确认释放 */
    volatile boolean dialogGateHeld = false;
    final Runnable animGateFailsafe = () -> {
        if (dialogGateHeld) return; // 对话框持闸优先于超时兜底，不得提前放行后续消息
        animGateClosed = false;
        drainPendingMsgs();
    };

    /** 定时屏障（MSG_ATTACK 弧光等不产生卡片动画的 WaitFrameSignal）；持有期内 isAnyAnimationBusy() 恒 true */
    static final long ATTACK_HOLD_MS = 700L;
    public volatile long animHoldUntilMs;

    /** 闸门轮询间隔：16ms 检测动画是否播完（轮询而非 GL 捕捉跳变，避免掉帧漏检） */
    static final long ANIM_GATE_POLL_MS = 16L;
    final Runnable animGatePoller = new Runnable() {
        @Override
        public void run() {
            if (!animGateClosed) return;
            if (isAnyAnimationBusy()) {
                mainHandler.postDelayed(this, ANIM_GATE_POLL_MS);
            } else {
                gate.reopenAnimGate();
            }
        }
    };

    // 对局/会话状态（跨包经 DuelStocHandler 读写，故 public；同包协作类访问的成员见各类注释）
    String playerName = "Player";
    public int duelStage = YGOProtocol.DUEL_STAGE_BEGIN;
    int matchResult = 0;
    int currentMatch = 0;
    public int maxMatch = 1;
    public boolean isHost = false;
    public boolean isBotMode = false;
    /** 服务端支持 CTOS_UNDO（YGOProtocol.HOST_CAP_UNDO） */
    public boolean serverCapsUndo = false;
    /** 服务端支持 CTOS_ASK_RESEND（YGOProtocol.HOST_CAP_ASK_RESEND） */
    public boolean serverCapsAskResend = false;
    /** 服务端确认存在可回退锚点（STOC_UNDO_STATE） */
    public boolean undoAvailable = false;
    /** 撤回一次性锩锁：发出 CTOS_UNDO 置位，本方发出一条应答时清除；置位期间 isUndoPromptActive() 恒 false（服务端允许连续撤回，但连续点击会让 ACK/重同步/屏障布防叠加破坏派发链） */
    public volatile boolean undoBlockedUntilNewAction = false;

    /** tag 模式本方是否已发起投降（等待队友回应）：防止对 STOC_TEAMMATE_SURRENDER 自我弹窗与重复发起 */
    public boolean tagSurrenderInitiated = false;
    public int gameMode = 0;
    public int gameRule = 0;
    public int gameLflist = 0;
    public int gameStartLp = 8000;
    public int gameStartHand = 5;
    public int gameDrawCount = 1;
    public int gameTimeLimit = 0;
    public int gameNoCheckDeck = 0;
    public int gameNoShuffleDeck = 0;
    /** 是否已收到过 STOC_JOIN_GAME 房间信息（gameXxx 字段与 field.dInfo.duelRule 有效），供等待界面就绪后补发 */
    public boolean hasJoinRoomInfoCache = false;

    public int getGameMode() { return gameMode; }
    public int getGameRule() { return gameRule; }
    public int getGameLflist() { return gameLflist; }
    public int getGameStartLp() { return gameStartLp; }
    public int getGameStartHand() { return gameStartHand; }
    public int getGameDrawCount() { return gameDrawCount; }
    public int getGameTimeLimit() { return gameTimeLimit; }
    public int getGameNoCheckDeck() { return gameNoCheckDeck; }
    public int getGameNoShuffleDeck() { return gameNoShuffleDeck; }

    public static class PlayerInfo {
        public String name = "";
        /** tag 模式同队队友昵称（对齐 dInfo.hostname_tag / clientname_tag，座位 pos1/pos3） */
        public String nameTag = "";
        public int lp = 8000;
        public int startLp = 8000;
        public int cardCount = 0;
    }

    public final PlayerInfo[] playerInfos = new PlayerInfo[]{new PlayerInfo(), new PlayerInfo()};
    /** 按大厅座位号存储昵称（STOC_HS_PLAYER_ENTER pos 0-3）：0/1 我方队、2/3 对方队，供 STOC_CHAT 显示"昵称: 内容"（对齐 game.cpp AddChatMsg 的 hostname/clientname/hostname_tag/clientname_tag 前缀） */
    public final String[] seatNames = new String[]{"", "", "", ""};

    /** 对齐 dInfo.tag_player[2]：tag 模式该队当前轮到哪位选手行动（true=队友在打，名字按 hostname_tag/clientname_tag 显示，drawing.cpp L1036-1049）；MSG_START 初始化（非先动队置 true）、MSG_NEW_TURN(turn!=1) 逐回合翻转 */
    public final boolean[] tagPlayer = new boolean[2];

    /** 本地视角显示名（实现见 DuelViewpointManager） */
    public String displayName(int localIdx) { return viewpoint.displayName(localIdx); }

    /** 是否观战位（实现见 DuelViewpointManager） */
    public boolean isSpectator() { return viewpoint.isSpectator(); }

    /** 进凋斗前绑定本地视角昵称（实现见 DuelViewpointManager） */
    public void bindViewNames() { viewpoint.bindViewNames(); }

    // ==== 协作类（按 // === 分栏拆分，构造注入本引擎引用） ====

    public final DuelHintManager hintManager;
    public final SummonAnimationManager summonAnim;
    /** 卡组 / 手卡堆动画（洗切、确认卡组顶/确认卡片、堆刷新）：自 DuelEventHandler 拆出，实况与回放共用（回放快进时按 field.instantPlace 同步落位） */
    public final DeckHandMotionManager deckMotion;
    final CommandDataParser dataParser;
    public final DuelEventHandler duelEvents;
    public final MessageUIHandler messageParser;
    final ConnectionManager connection;
    final LobbyActions lobbyActions;
    final GameActions gameActions;
    /** 录像回放播放器（唯一回放入口：消息投喂本引擎实况管线，与联机/观战同一渲染路径） */
    // 撤回/录制/看门狗协作类（同包，持 engine 反向引用）
    final UndoClientCoordinator undoCoord;
    final MsgFrameRecorder msgRecorder;
    final AskWatchdog askWatchdog;
    /** 消息串行闸门（入队派发/动画持闸/回放排空，实现在 MsgDispatchGate）与对局视角（显示名/切视角，实现在 DuelViewpointManager） */
    final MsgDispatchGate gate;
    final DuelViewpointManager viewpoint;

    public final ReplayPlayer replayPlayer;
    /** 残局播放器（本地引擎直驱残局 lua，消息投喂本引擎实况管线，实现见 {@link SingleModeRunner}） */
    public final SingleModeRunner singleRunner;

    public GameEngine(SoundManager soundManager) {
        this.client = new DuelClient();
        this.soundManager = soundManager;
        this.field = new GameField();
        this.scriptEngine = LuaScriptEngine.get();
        // 装配顺序：hintManager 最早（其余协作类派发链路会用到），最后挂接网络回调
        this.hintManager = new DuelHintManager(this);
        this.summonAnim = new SummonAnimationManager(this);
        this.deckMotion = new DeckHandMotionManager(this);
        this.dataParser = new CommandDataParser(this);
        this.duelEvents = new DuelEventHandler(this);
        this.messageParser = new MessageUIHandler(this);
        this.connection = new ConnectionManager(this);
        this.lobbyActions = new LobbyActions(this);
        this.gameActions = new GameActions(this);
        this.undoCoord = new UndoClientCoordinator(this);
        this.msgRecorder = new MsgFrameRecorder(this);
        this.askWatchdog = new AskWatchdog(this);
        this.gate = new MsgDispatchGate(this);
        this.viewpoint = new DuelViewpointManager(this);
        this.replayPlayer = new ReplayPlayer(this);
        this.singleRunner = new SingleModeRunner(this);
        client.setListener(new DuelStocHandler(this));
    }

    public void setListener(EngineListener listener) {
        this.listener = listener;
    }

    public GameField getField() {
        return field;
    }

    public GameState getState() {
        return state;
    }

    public DuelClient getClient() {
        return client;
    }

    // === 命令状态（多方共享：ShowDialogUtil / GameFieldController / GameFieldView 直接引用） ===

    public static final int COMMAND_ACTIVATE = 0x0001;
    public static final int COMMAND_SUMMON   = 0x0002;
    public static final int COMMAND_SPSUMMON = 0x0004;
    public static final int COMMAND_MSET     = 0x0008;
    public static final int COMMAND_SSET     = 0x0010;
    public static final int COMMAND_REPOS    = 0x0020;
    public static final int COMMAND_ATTACK   = 0x0040;

    // === 召唤动画类型（onSummonAnimation 的 summonType 参数，对齐 duelclient.cpp showcard=5/7）：常量定义已随拆分迁入 SummonAnimationManager，此处保留别名以兼容既有引用点 ===
    public static final int SUMMON_NORMAL = SummonAnimationManager.SUMMON_NORMAL;   // 通常召唤（MSG_SUMMONING，case 7 翻面）
    public static final int SUMMON_SPECIAL = SummonAnimationManager.SUMMON_SPECIAL;  // 特殊召唤（MSG_SPSUMMONING，case 5 放大淡入）
    public static final int SUMMON_FLIP = SummonAnimationManager.SUMMON_FLIP;     // 反转召唤（MSG_FLIPSUMMONING，case 7 翻面）

    public static class CmdCardInfo {
        public ClientCard card;
        public int code;
        public int desc;
        public int flag;
        public int index;
        public CmdCardInfo(ClientCard card, int code, int desc, int flag, int index) {
            this.card = card; this.code = code; this.desc = desc; this.flag = flag; this.index = index;
        }
    }

    public List<CmdCardInfo> activatableCards = new ArrayList<>();
    public List<CmdCardInfo> attackableCards = new ArrayList<>();
    public List<CmdCardInfo> summonableCards = new ArrayList<>();
    public List<CmdCardInfo> spsummonableCards = new ArrayList<>();
    public List<CmdCardInfo> reposableCards = new ArrayList<>();
    public List<CmdCardInfo> msetableCards = new ArrayList<>();
    public List<CmdCardInfo> ssetableCards = new ArrayList<>();
    public boolean showBP, showEP, showM2, showShuffle;

    public int selectFieldMask;
    public int selectFieldPlayer;
    public int selectFieldCount;

    public void clearCommandFlags() {
        activatableCards.clear();
        attackableCards.clear();
        summonableCards.clear();
        spsummonableCards.clear();
        reposableCards.clear();
        msetableCards.clear();
        ssetableCards.clear();
        showBP = false;
        showEP = false;
        showM2 = false;
        showShuffle = false;
        for (int p = 0; p < 2; p++) {
            for (ClientCard c : field.players[p].monsterZone) {
                if (c != null) c.clearCmdFlag();
            }
            for (ClientCard c : field.players[p].spellZone) {
                if (c != null) c.clearCmdFlag();
            }
            for (ClientCard c : field.players[p].hand) {
                if (c != null) c.clearCmdFlag();
            }
            for (ClientCard c : field.players[p].grave) {
                if (c != null) c.clearCmdFlag();
            }
            for (ClientCard c : field.players[p].removed) {
                if (c != null) c.clearCmdFlag();
            }
            for (ClientCard c : field.players[p].extra) {
                if (c != null) c.clearCmdFlag();
            }
            for (ClientCard c : field.players[p].deck) {
                if (c != null) c.clearCmdFlag();
            }
        }
    }

    /** 复位区域发动提示 / conti_act 标志（解析 idle/battle 前清空，防上一选择脏标志；连锁路径不经此方法） */
    void resetFieldCommandHints() {
        for (int p = 0; p < 2; p++) {
            field.deckAct[p] = false;
            field.graveAct[p] = false;
            field.removeAct[p] = false;
            field.extraAct[p] = false;
            field.pzoneAct[p] = false;
        }
        field.contiCards.clear();
        field.contiAct = false;
    }

    public boolean hasIdleCommands() {
        return !summonableCards.isEmpty() || !spsummonableCards.isEmpty()
                || !reposableCards.isEmpty() || !msetableCards.isEmpty()
                || !ssetableCards.isEmpty() || !activatableCards.isEmpty()
                || showBP || showEP || showShuffle;
    }

    public boolean hasBattleCommands() {
        return !attackableCards.isEmpty() || !activatableCards.isEmpty()
                || showM2 || showEP;
    }

    public void setPlayerName(String name) {
        this.playerName = name;
    }

    public void setBotMode(boolean botMode) {
        this.isBotMode = botMode;
    }

    // === Connection（实现见 ConnectionManager） ===

    public void connectToServer(String host, int port, boolean createGame,
                                String roomName, String password,
                                int rule, int mode, int duelRule,
                                int startLp, int startHand, int drawCount, int timeLimit,
                                boolean noCheckDeck, boolean noShuffleDeck) {
        connection.connectToServer(host, port, createGame, roomName, password,
                rule, mode, duelRule, startLp, startHand, drawCount, timeLimit,
                noCheckDeck, noShuffleDeck);
    }

    public void startLocalServer() {
        connection.startLocalServer();
    }

    public void startLocalServerWithSettings(int lflist, int rule, int mode, int duelRule,
                                              boolean noCheckDeck, boolean noShuffleDeck,
                                              int startLp, int startHand, int drawCount, int timeLimit,
                                              String roomName, String password) {
        connection.startLocalServerWithSettings(lflist, rule, mode, duelRule,
                noCheckDeck, noShuffleDeck, startLp, startHand, drawCount, timeLimit,
                roomName, password);
    }

    /** 启动残局（对齐 single_mode.cpp SinglePlayThread）：luaPath 为残局脚本路径，noShuffleToDeck 启用「不洗切时回卡组改为回顶端」标志 */
    public void startSingleMode(String luaPath, boolean noShuffleToDeck) {
        connection.startSingleMode(luaPath, noShuffleToDeck);
    }

    public void startBotDuel(String host, int port, String botCommand, String deckFile) {
        connection.startBotDuel(host, port, botCommand, deckFile);
    }

    /** 启动 WindBot 加入指定主机（人机对战），实现见 ConnectionManager */
    public void launchWindBot(String host, int port, String botCommand, String deckFile) {
        connection.launchWindBot(host, port, botCommand, deckFile);
    }

    // ==== 录像回放控制（实现见 ConnectionManager） ====

    public void loadReplay(String replayPath) {
        connection.loadReplay(replayPath);
    }

    public void pauseReplay() {
        connection.pauseReplay();
    }

    public void resumeReplay() {
        connection.resumeReplay();
    }

    public void stopReplay() {
        connection.stopReplay();
    }

    public void skipReplayAhead() {
        connection.skipReplayAhead();
    }

    public void disconnect() {
        connection.disconnect();
    }

    // === Lobby Actions（实现见 LobbyActions） ===

    public void sendReady() {
        lobbyActions.sendReady();
    }

    public void sendNotReady() {
        lobbyActions.sendNotReady();
    }

    public void sendStart() {
        lobbyActions.sendStart();
    }

    public void sendKick(int pos) {
        lobbyActions.sendKick(pos);
    }

    public void sendChat(String message) {
        lobbyActions.sendChat(message);
    }

    public void sendSurrender() {
        lobbyActions.sendSurrender();
    }
    public void sendUndo() { undoCoord.sendUndo(); }

    public boolean canUndo() { return undoCoord.canUndo(); }

    public void onUndoAck(int result, int turn, int currentPlayer, int phase) { undoCoord.onUndoAck(result, turn, currentPlayer, phase); }

    public boolean applyUndoResync(GameField field) { return undoCoord.applyUndoResync(field); }

    public void clearUndoResync() { undoCoord.clearUndoResync(); }

    public void onQuestionDispatched() { undoCoord.onQuestionDispatched(); }

    public boolean isResponseBlocked() { return undoCoord.isResponseBlocked(); }

    public long captureQuestionToken() { return undoCoord.captureQuestionToken(); }

    public boolean isQuestionStale(long token) { return undoCoord.isQuestionStale(token); }
    // === 询问丢失自愈看门狗（实现见 AskWatchdog） ===

    public void notifyResponseSent() { askWatchdog.notifyResponseSent(); }

    public void resetAskWatchdog() { askWatchdog.resetAskWatchdog(); }

    public boolean isUndoPromptActive() { return undoCoord.isUndoPromptActive(); }

    /** 是否 tag 双人模式（gameMode == MODE_TAG） */
    public boolean isTagMode() {
        return lobbyActions.isTagMode();
    }

    /** tag 模式：本方已发起投降、正在等待队友回应 */
    public boolean isSurrenderPending() {
        return lobbyActions.isSurrenderPending();
    }

    public void sendToDuelist() {
        lobbyActions.sendToDuelist();
    }

    public void sendToObserver() {
        lobbyActions.sendToObserver();
    }

    // === Game Actions（实现见 GameActions） ===

    public void sendHandResult(int result) {
        gameActions.sendHandResult(result);
    }

    public void sendTPResult(boolean chooseFirst) {
        gameActions.sendTPResult(chooseFirst);
    }

    public void sendDeckUpdate(List<Integer> main, List<Integer> extra, List<Integer> side) {
        gameActions.sendDeckUpdate(main, extra, side);
    }

    public void sendResponse(byte[] responseData) {
        gameActions.sendResponse(responseData);
    }

    public void sendTimeConfirm() {
        gameActions.sendTimeConfirm();
    }

    public int getSelfType() {
        return gameActions.getSelfType();
    }

    public String getPlayerName() {
        return playerName;
    }

    // === State Management ===

    void setState(GameState newState) {
        if (this.state == newState) return;
        this.state = newState;
        mainHandler.post(() -> {
            if (listener != null) listener.onStateChanged(newState);
        });
    }

    /** setState 的公开出口（供 network 包的 DuelStocHandler 跨包驱动状态机） */
    public void setEngineState(GameState newState) {
        setState(newState);
    }

    /** 强制重派状态回调（不去重）：solo 连续换 side 等「状态未变但需重建界面」场景 */
    public void redispatchState(GameState state) {
        if (listener != null) listener.onStateChanged(state);
    }

    // === 游戏消息串行闸门（原 DuelClient.ClientListener.onGameMsg 投递口，实现见 StocHandler） ===

    /** STOC_GAME_MSG 入队（duelclient.cpp ClientAnalyze 调用点），入队后由闸门串行派发；实现见 MsgDispatchGate */
    public void enqueueGameMsg(int msgType, ByteBuffer data) { gate.enqueueGameMsg(msgType, data); }

    /** 上一条非 RETRY 游戏消息缓存（对齐 duelclient.cpp last_successful_msg）与重放次数守卫 */
    int lastGameMsgType = -1;
    byte[] lastGameMsgBody;
    int retryReplayCount = 0;

    // === 逐局 MSG 录制（实现见 MsgFrameRecorder） ===

    void recordMsgFrame(int msgType, byte[] body) { msgRecorder.record(msgType, body); }

    public List<byte[]> takeRecordedMsgSegment() { return msgRecorder.takeRecordedMsgSegment(); }

    public void resetMsgRecording() { msgRecorder.resetMsgRecording(); }

    void invalidateCurrentMsgSegment() { msgRecorder.invalidateCurrentMsgSegment(); }

    public void recordChatFrame(int playerType, String message) { msgRecorder.recordChatFrame(playerType, message); }

    /** 回放聊天伪帧消息号（对齐 MsgFrameRecorder，保留 GameEngine 层引用兼容） */
    public static final int REPLAY_CHAT_FRAME = MsgFrameRecorder.REPLAY_CHAT_FRAME;

    /** MSG_RETRY 后重放上一条消息（duelclient.cpp L1351-1404）：重建被无效应答打断的选择 UI；实现见 MsgDispatchGate */
    public void replayLastGameMsg() { gate.replayLastGameMsg(); }

    /** 实际派发单条通讯消息（对齐 duelclient.cpp ClientAnalyze 主体）；实现见 MsgDispatchGate */
    void dispatchGameMsg(int msgType, ByteBuffer data) { gate.dispatchGameMsg(msgType, data); }

    /** 串行派发待处理消息：闸门开启时逐条处理，某条触发动画则停止循环；实现见 MsgDispatchGate */
    void drainPendingMsgs() { gate.drainPendingMsgs(); }

    /** 确认卡片对话框显示时持有消息闸门（对齐 C++ MSG_CONFIRM_CARDS 显示面板后 actionSignal.Wait()）；实现见 MsgDispatchGate */
    public void holdMsgGateForDialog() { gate.holdMsgGateForDialog(); }

    /** 确认卡片对话框点确定后释放闸门；实现见 MsgDispatchGate */
    public void releaseMsgGateForDialog() { gate.releaseMsgGateForDialog(); }

    /** 统一动画屏障：场地卡片动画、LP 变化动画与居中特效任一在播即 true（实现见 MsgDispatchGate） */
    public boolean isAnyAnimationBusy() { return gate.isAnyAnimationBusy(); }

    /** 待派发消息队列是否仍有存量（回放投喂线程据此等待实况管线消化完再喂下一条） */
    public boolean hasPendingMsgs() { return gate.hasPendingMsgs(); }

    /** 队列已空且主线程未处于派发中：实况侧场地数据已追上游标位置（回放切片前对齐卡数用） */
    public boolean isMsgQueueIdle() { return gate.isMsgQueueIdle(); }

    /** 回放快进专用：强制重开闸门并在主线程一次性排空 pendingMsgs（忽略动画占用）；实现见 MsgDispatchGate */
    public void drainReplayQueueNow() { gate.drainReplayQueueNow(); }

    /** 回放快进落点专用同步屏障：与 drainReplayQueueNow 同体但额外阻塞投喂线程直到排空 Runnable 真正在主线程执行完（借助 mainHandler FIFO 保证快进期每条消息都在 instantPlace=true 下瞬时落位，避免解冻后错现「回放起手抽卡动画」）；实现见 MsgDispatchGate */
    public boolean drainReplayQueueSync(long timeoutMs) { return gate.drainReplayQueueSync(timeoutMs); }

    /** 特效队列排空回调（SpecEffectOverlay idle）：重开闸门快路径（实现见 MsgDispatchGate） */
    public void notifySpecEffectIdle() { gate.notifySpecEffectIdle(); }

    /** 观战「跳到当前」：无视动画一次性即时落位到最新局面（复用回放快进语义，实现见 MsgDispatchGate） */
    public void spectatorSkipToCurrent() { gate.spectatorSkipToCurrent(); }

    // === 对局状态标志与视角换算 ===

    /** Game::LocalPlayer：dInfo.isFirst ? player : 1 - player */
    public boolean duelIsFirst = true;

    /** 对局状态标志，对齐 gframe dInfo.isStarted / dInfo.isInDuel / Game.is_siding，供聊天 ChatLocalPlayer 的分边与昵称判定使用；转换点（对齐 duelclient.cpp）：STOC_DUEL_START→started、MSG_START→inDuel、STOC_CHANGE_SIDE→siding 且 started=false、STOC_WAITING_SIDE/STOC_DUEL_END→复位 */
    public boolean duelStarted = false;
    public boolean inDuel = false;
    public boolean siding = false;

    /** 回放模式：录像消息经 ReplayPlayer 切片后投入本引擎实况管线渲染；SELECT 询问/胜负结算在 GameMessageParser 侧抑制（应答已录制在文件里，弹选择窗会悬挂流程） */
    public boolean replayMode = false;
    /** 残局（single mode）模式：本地引擎直驱残局 lua，消息投喂本引擎实况管线；与 replayMode 互斥 */
    public boolean isSingleMode = false;

    /** 局域网纯单人模式（房主同控双方，HostInfo.mode bit4=0x10）：回合切换自动翻转视角，isSelfSide 恒 true；由 EngineCallbackDelegate.onJoinGame 置位 */
    public boolean soloMode = false;
    /** Solo 模式下四个席位展示的卡组名（index 0..3）：由 PlayerWaitingDialog 在房主为席位 1/2/3 选卡时回填，enterDuelingUI 会优先用 slot0/slot1 名称写回 GameTopInfo 代替默认玩家昵称；非 solo 模式不写入 */
    public final String[] soloSeatDeckNames = new String[]{"", "", "", ""};
    /** Solo 模式下四个席位选定的卡组文件路径（index 0..3）：与 soloSeatDeckNames 同源，由 PlayerWaitingDialog 回填；match 换 side 时按席位顺序载入对应卡组供编辑；非 solo 不写入 */
    public final String[] soloSeatDeckPaths = new String[]{"", "", "", ""};
    /** Solo match 换 side 会话内已弹出的换 side 界面计数：每次进入 SIDING 载入 soloSeatDeckPaths 的下一席位卡组并自增，新局开局（onDuelStart）复位为 0 */
    public int soloSideSession = 0;
    /** 回放快进重排中（undo/restart/跳回合）：drawspec 覆盖层与长动画派发丢弃，配合 GameField.instantPlace 即时落位与音效静默，令闸门不阻塞、队列单帧排空；置位/复位在投喂线程而判读在主线程派发链，volatile 保证跨线程立即可见、防 JIT 提升旧值使抑制形同虚设 */
    public volatile boolean replaySkip = false;

    public boolean isStarted() { return duelStarted; }
    public boolean isInDuel() { return inDuel; }
    public boolean isSiding() { return siding; }
    /** 本局我方是否先攻（对齐 dInfo.isFirst） */
    public boolean isDuelFirst() { return duelIsFirst; }

    public int localPlayer(int player) {
        return duelIsFirst ? player : 1 - player;
    }

    /** 回放态：从录像头部卡码直取卡组/额外卡表，供堆叠区弹窗展开全面；其余模式返回 null */
    public List<Integer> getReplayZoneCodes(int viewPlayer, int location) {
        if (!replayMode || replayPlayer == null) return null;
        return replayPlayer.getReplayZoneCodes(viewPlayer, location);
    }

    /** 观战切视角请求（event_handler.cpp BUTTON_REPLAY_SWAP 观战分支 → is_swapping，实际交换延迟到消息消费点；实现见 DuelViewpointManager） */
    volatile boolean pendingSpectatorSwap;

    public void requestSpectatorSwap() { viewpoint.requestSpectatorSwap(); }

    /** 给定协议侧玩家索引（0/1）是否代表我方（2=平局返回 false）；单人模式下双方均为我方 */
    public boolean isSelfSide(int player) {
        if (soloMode) return player != 2;
        return player != 2 && localPlayer(player & 1) == 0;
    }

    public void release() {
        replayPlayer.stop();
        singleRunner.stop();
        disconnect();
        scriptEngine.release();
    }
}
