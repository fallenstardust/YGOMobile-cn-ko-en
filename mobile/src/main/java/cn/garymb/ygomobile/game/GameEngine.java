package cn.garymb.ygomobile.game;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.audio.SoundManager;
import cn.garymb.ygomobile.engine.LuaScriptEngine;
import cn.garymb.ygomobile.network.DuelClient;
import cn.garymb.ygomobile.network.YGOProtocol;
import ocgcore.enums.GameMessage;

/**
 * 引擎统一门面：对外公共 API 保持不变（连接/大厅/对局操作/状态查询/命令状态），
 * 行为逻辑已按 // === 分栏拆分至同包协作类，由构造函数装配并经本类一行转发：
 * - DuelHintManager：stHintMsg 提示栏 + 提示栏文字生成
 * - SummonAnimationManager：召唤/无效居中动画
 * - ConnectionManager：Connection（联机/本地房/残局/人机/录像）
 * - LobbyActions / GameActions：大厅与对局内主动操作
 * - CommandDataParser：Data parsing helpers（update/battle/idle 命令解析）
 * - DuelEventHandler：场地事件消息 handler（实现 GameMessageParser.MessageHandler）
 * - GameMessageParser：选择/提示类消息实现 + 静态 parse 派发
 * - DuelClient.StocHandler：DuelClient.ClientListener 实现（STOC 回调）
 *
 * 留在本类的仅有：共享状态（命令列表/区域选择/对局标志/游戏参数）、状态机、
 * 消息串行闸门（动画屏障，对齐 duelclient.cpp ClientAnalyze + WaitFrameSignal 的串行语义）。
 */
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

        /**
         * MSG_NEW_TURN（对齐 duelclient.cpp L2865-2877）：
         * 用于在每回合开始时显示/刷新左侧面板的时点按钮
         *
         * @param player 本地视角的当前回合玩家（0 = 我方）
         */
        void onTurnStarted(int player);

        void onChatReceived(int playerType, String message);

        void onSelectRequired(int selectType, ByteBuffer data);

        void onDuelResult(int winner, int reason);

        void onHintMessage(String hint);

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

        /**
         * 召唤类卡片居中动画（对齐 duelclient.cpp MSG_SUMMONING/MSG_SPSUMMONING/MSG_FLIPSUMMONING）：
         * summonType 取 SUMMON_NORMAL/SUMMON_SPECIAL/SUMMON_FLIP，
         * 宿主据此播放 case 7（通常/反转：翻面进入）或 case 5（特殊：放大淡入）
         */
        void onSummonAnimation(int code, int summonType);

        /**
         * 效果无效卡片居中动画（对齐 duelclient.cpp MSG_CHAIN_NEGATED/MSG_CHAIN_DISABLED，showcard=3）：
         * code 为被无效连锁的卡码，居中显示卡片 + 无效图标（破坏被无效即"不会被破坏"）
         */
        void onNegatedAnimation(int code);

        /**
         * 居中特效层是否仍在播放（供统一动画屏障查询）：宿主返回 SpecEffectOverlay.isBusy()。
         * 与场地卡片动画（GameField.isAnimating）一起构成动画屏障，任一在播则暂缓派发后续消息，
         * 把 GameFieldView 的卡片移动纳入与特效、弹窗相同的串行序列。
         */
        boolean isSpecEffectBusy();

        /** tag 模式：队友请求投降，本方需确认是否同意（对齐 STOC_TEAMMATE_SURRENDER + sysString 1355） */
        default void onTeammateSurrenderRequest() {}
    }

    // 核心状态与基础设施（协作类经 engine. 引用访问：同包类用包级私有，
    // network 包的 DuelClient.StocHandler 需 public）
    private GameState state = GameState.IDLE;
    public final DuelClient client;
    public final SoundManager soundManager;
    public final GameField field;
    LuaScriptEngine scriptEngine;
    public EngineListener listener;
    public final Handler mainHandler = new Handler(Looper.getMainLooper());

    /**
     * 消息串行闸门（对齐 duelclient.cpp ClientAnalyze + WaitFrameSignal 的串行语义）：
     * C++ 网络线程处理到召唤/发动/无效等动画消息时用 WaitFrameSignal 阻塞，直到动画播完才处理
     * 下一条消息（如"是否发动/是否连锁"的询问弹窗）。本工程所有消息经 DuelClient.handlePacket
     * 的 mainHandler.post 投递到主线程处理，无法阻塞网络线程（阻塞主线程会让 Choreographer 停摆、
     * 动画永远播不完 → 死锁），故改为「主线程消息队列 + 动画闸门」：动画消息派发后关闭闸门，
     * 暂缓派发后续消息，待特效队列排空（notifySpecEffectIdle）再继续。
     * 全部逻辑均在主线程执行，无需加锁。
     */
    private final ArrayDeque<Runnable> pendingMsgs = new ArrayDeque<>();
    private boolean dispatchingMsg = false;    // 正在派发一条消息（防重入）
    private boolean animGateClosed = false;    // 动画播放期间关闭闸门，暂缓后续消息
    /** 闸门兜底超时：万一特效队列因异常未排空，超时后强制重开，避免消息永久卡死 */
    private static final long ANIM_GATE_TIMEOUT_MS = 8000L;
    private final Runnable animGateFailsafe = () -> {
        animGateClosed = false;
        drainPendingMsgs();
    };

    /**
     * 定时动画屏障（对齐不产生卡片动画的 WaitFrameSignal）：如 MSG_ATTACK 的弧光展示
     * （duelclient.cpp L3861-3865 GenArrow + WaitFrameSignal(40)≈667ms）——弧光是时间窗口
     * 绘制、不占用 aniFrame/特效队列，若无此屏障后续消息（伤害步骤/效果询问弹窗）立即推进，
     * 弹窗遮挡 GL 场地使弧线（尤其无后续卡片动画的直接攻击）来不及展示。
     * 持有期内 isAnyAnimationBusy() 恒 true，闸门保持关闭直至到期。
     */
    static final long ATTACK_HOLD_MS = 700L;
    volatile long animHoldUntilMs;

    /**
     * 动画闸门轮询间隔：闸门关闭后主线程每 16ms 查询一次「场地卡片动画 + 居中特效」是否仍在播，
     * 两者都空闲即重开闸门。用轮询而非在 GL 渲染线程捕捉「animating→idle 跳变」，是为规避掉帧时
     * 单帧跨越多帧动画（onDrawFrame 中 dt 上限 0.1 → animationSpeed 可达 12，8 帧移动可能一帧结束）
     * 导致跳变被错过、闸门一直卡到超时兜底。
     */
    private static final long ANIM_GATE_POLL_MS = 16L;
    private final Runnable animGatePoller = new Runnable() {
        @Override
        public void run() {
            if (!animGateClosed) return;
            if (isAnyAnimationBusy()) {
                mainHandler.postDelayed(this, ANIM_GATE_POLL_MS);
            } else {
                reopenAnimGate();
            }
        }
    };

    // 对局/会话状态（跨包经 DuelClient.StocHandler 读写，故 public；
    // 同包协作类访问的成员见各类注释）
    String playerName = "Player";
    public int duelStage = YGOProtocol.DUEL_STAGE_BEGIN;
    int matchResult = 0;
    int currentMatch = 0;
    public int maxMatch = 1;
    public boolean isHost = false;
    public boolean isBotMode = false;

    /** tag 模式本方是否已发起投降（等待队友回应）：防止对 STOC_TEAMMATE_SURRENDER 自我弹窗与重复发起 */
    public boolean tagSurrenderInitiated = false;
    ReplayEngine replayEngine;
    public int gameMode = 0;
    public int gameRule = 0;
    public int gameLflist = 0;
    public int gameStartLp = 8000;
    public int gameStartHand = 5;
    public int gameDrawCount = 1;
    public int gameTimeLimit = 0;
    public int gameNoCheckDeck = 0;
    public int gameNoShuffleDeck = 0;

    public int getGameMode() { return gameMode; }
    public int getGameRule() { return gameRule; }
    public int getGameLflist() { return gameLflist; }
    public int getGameStartLp() { return gameStartLp; }
    public int getGameStartHand() { return gameStartHand; }
    public int getGameDrawCount() { return gameDrawCount; }
    public int getGameTimeLimit() { return gameTimeLimit; }
    public int getGameNoCheckDeck() { return gameNoCheckDeck; }
    public int getGameNoShuffleDeck() { return gameNoShuffleDeck; }

    public ReplayEngine getReplayEngine() {
        return replayEngine;
    }

    public void setReplayEngine(ReplayEngine engine) {
        this.replayEngine = engine;
    }

    public static class PlayerInfo {
        public String name = "";
        public int lp = 8000;
        public int startLp = 8000;
        public int cardCount = 0;
    }

    public final PlayerInfo[] playerInfos = new PlayerInfo[]{new PlayerInfo(), new PlayerInfo()};
    /** 按大厅座位号存储昵称（STOC_HS_PLAYER_ENTER pos 0-3）：0/1 我方队、2/3 对方队，
     *  供 STOC_CHAT 显示"昵称: 内容"（对齐 game.cpp AddChatMsg 的 hostname/clientname/hostname_tag/clientname_tag 前缀） */
    public final String[] seatNames = new String[]{"", "", "", ""};

    // ==== 协作类（按 // === 分栏拆分，构造注入本引擎引用） ====

    public final DuelHintManager hintManager;
    public final SummonAnimationManager summonAnim;
    final CommandDataParser dataParser;
    public final DuelEventHandler duelEvents;
    public final GameMessageParser messageParser;
    final ConnectionManager connection;
    final LobbyActions lobbyActions;
    final GameActions gameActions;

    public GameEngine(SoundManager soundManager) {
        this.client = new DuelClient();
        this.soundManager = soundManager;
        this.field = new GameField();
        this.scriptEngine = LuaScriptEngine.get();
        // 装配顺序：hintManager 最早（其余协作类派发链路会用到），最后挂接网络回调
        this.hintManager = new DuelHintManager(this);
        this.summonAnim = new SummonAnimationManager(this);
        this.dataParser = new CommandDataParser(this);
        this.duelEvents = new DuelEventHandler(this);
        this.messageParser = new GameMessageParser(this);
        this.connection = new ConnectionManager(this);
        this.lobbyActions = new LobbyActions(this);
        this.gameActions = new GameActions(this);
        client.setListener(new DuelClient.StocHandler(this));
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

    // === 召唤动画类型（onSummonAnimation 的 summonType 参数，对齐 duelclient.cpp showcard=5/7） ===
    // 常量定义已随拆分迁入 SummonAnimationManager，此处保留别名以兼容既有引用点
    public static final int SUMMON_NORMAL = SummonAnimationManager.SUMMON_NORMAL;   // 通常召唤（MSG_SUMMONING，case 7 翻面）
    public static final int SUMMON_SPECIAL = SummonAnimationManager.SUMMON_SPECIAL;  // 特殊召唤（MSG_SPSUMMONING，case 5 放大淡入）
    public static final int SUMMON_FLIP = SummonAnimationManager.SUMMON_FLIP;     // 反转召唤（MSG_FLIPSUMMONING，case 7 翻面）

    public static class CmdCardInfo {
        public GameField.ClientCard card;
        public int code;
        public int desc;
        public int flag;
        public int index;
        public CmdCardInfo(GameField.ClientCard card, int code, int desc, int flag, int index) {
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
            for (GameField.ClientCard c : field.players[p].monsterZone) {
                if (c != null) c.clearCmdFlag();
            }
            for (GameField.ClientCard c : field.players[p].spellZone) {
                if (c != null) c.clearCmdFlag();
            }
            for (GameField.ClientCard c : field.players[p].hand) {
                if (c != null) c.clearCmdFlag();
            }
            for (GameField.ClientCard c : field.players[p].grave) {
                if (c != null) c.clearCmdFlag();
            }
            for (GameField.ClientCard c : field.players[p].removed) {
                if (c != null) c.clearCmdFlag();
            }
            for (GameField.ClientCard c : field.players[p].extra) {
                if (c != null) c.clearCmdFlag();
            }
            for (GameField.ClientCard c : field.players[p].deck) {
                if (c != null) c.clearCmdFlag();
            }
        }
    }

    /**
     * 复位「区域发动提示 / conti_act」渲染标志（对齐 duelclient.cpp MSG_SELECT_IDLECMD/BATTLECMD
     * 每次重新计算 grave_act/remove_act/extra_act/deck_act 与 conti_act/conti_cards）：
     * 在解析 idle/battle 命令前清空，避免上一选择的脏标志残留。
     *（连锁路径由 ShowDialogUtil 设置、clearChainSelect 复位，不经此方法，故不放这里以免误清连锁提示）
     */
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

    public void startSingleMode(String luaPath) {
        connection.startSingleMode(luaPath);
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

    /** setState 的公开出口（供 network 包的 DuelClient.StocHandler 跨包驱动状态机） */
    public void setEngineState(GameState newState) {
        setState(newState);
    }

    // === 游戏消息串行闸门（原 DuelClient.ClientListener.onGameMsg 投递口，实现见 StocHandler） ===

    /**
     * STOC_GAME_MSG 入队（对齐 duelclient.cpp ClientAnalyze 的调用点）：
     * 入队后由闸门串行派发：动画消息会关闭闸门，暂缓后续消息（对齐 C++ WaitFrameSignal 阻塞语义）
     */
    public void enqueueGameMsg(int msgType, ByteBuffer data) {
        data.order(ByteOrder.LITTLE_ENDIAN);
        // 对齐 duelclient.cpp L1298-1303：除 MSG_RETRY 外每条消息均缓存一份快照，
        // 供服务端下发 RETRY（无效应答）后重放重建选择 UI（服务端不会重发原 SELECT）
        if (msgType != GameMessage.Retry.value()) {
            ByteBuffer snapshot = data.duplicate();
            byte[] body = new byte[snapshot.remaining()];
            snapshot.get(body);
            lastGameMsgType = msgType;
            lastGameMsgBody = body;
            retryReplayCount = 0;
        }
        // 入队后由闸门串行派发：动画消息会关闭闸门，暂缓后续消息（对齐 C++ WaitFrameSignal 阻塞语义）
        pendingMsgs.offer(() -> dispatchGameMsg(msgType, data));
        drainPendingMsgs();
    }

    /** 上一条非 RETRY 游戏消息缓存（对齐 duelclient.cpp last_successful_msg）与重放次数守卫 */
    private int lastGameMsgType = -1;
    private byte[] lastGameMsgBody;
    private int retryReplayCount = 0;

    /**
     * 收到 MSG_RETRY 后重放上一条消息（对齐 duelclient.cpp L1351-1404 的
     * ClientAnalyze(last_successful_msg)）：重建被无效应答打断的选择 UI。
     * 连续重放设上限防“自动非法应答 ↔ 服务端 RETRY”热循环，收到新消息即清零。
     */
    public void replayLastGameMsg() {
        if (lastGameMsgType < 0 || lastGameMsgBody == null) return;
        if (++retryReplayCount > 3) {
            Log.e(TAG, "replayLastGameMsg: retry replay limit exceeded, give up (msgType="
                    + lastGameMsgType + ")");
            return;
        }
        ByteBuffer buf = ByteBuffer.wrap(lastGameMsgBody);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        // 直接入队但不刷新缓存（保留原快照以便再次重放），故不经 enqueueGameMsg 的缓存分支
        pendingMsgs.offer(() -> dispatchGameMsg(lastGameMsgType, buf));
        drainPendingMsgs();
    }

    /** 实际派发单条通讯消息（对齐 duelclient.cpp ClientAnalyze 主体） */
    private void dispatchGameMsg(int msgType, ByteBuffer data) {
        // 对齐 duelclient.cpp L1307-1311：除 MSG_WAITING/MSG_CARD_SELECTED 外，
        // 每条通讯消息开始先停止等待动画并隐藏 stHintMsg（提示栏随通讯推进实时显隐）
        if (msgType != GameMessage.Waiting.value() && msgType != GameMessage.CardSelected.value()) {
            hintManager.stopWaitHint();
            hintManager.postDuelHintHide();
        }
        try {
            // 场地事件 handler = DuelEventHandler（实现 MessageHandler；选择/提示类转发 messageParser，
            // 召唤动画类转发 summonAnim）
            GameMessageParser.parse(msgType, data, duelEvents);
        } catch (BufferUnderflowException e) {
            Log.e(TAG, "Failed to parse game message type=" + msgType + ", remaining=" + data.remaining(), e);
        }
    }

    /**
     * 串行派发待处理消息：闸门开启（无动画播放）时逐条取出处理；一旦某条消息触发动画并关闭闸门
     * （closeAnimGate），循环停止，剩余消息保留在队列，待动画结束（notifySpecEffectIdle）再继续。
     */
    private void drainPendingMsgs() {
        if (dispatchingMsg || animGateClosed) return;
        dispatchingMsg = true;
        try {
            while (true) {
                Runnable task = pendingMsgs.poll();
                if (task == null) break;
                task.run();
                // 泛化动画屏障：本条消息若同步触发了场地卡片移动/淡入淡出（moveCardAnimated 立即置
                // aniFrame）或居中特效（startCard→startLoop 立即置 running），随即关闭闸门暂缓后续消息，
                // 待动画播完（轮询器或 idle 快路径重开）再继续——对齐 C++ 每条动画消息后的 WaitFrameSignal。
                if (isAnyAnimationBusy()) {
                    closeAnimGate();
                    break;
                }
            }
        } finally {
            dispatchingMsg = false;
        }
    }

    /**
     * 关闭动画闸门（对齐 C++ 动画消息后的 WaitFrameSignal）：暂缓派发后续消息，
     * 启动轮询器每 16ms 检测「场地卡片动画 + 居中特效」是否播完，全部空闲即重开；
     * 特效队列排空的 idle 回调（notifySpecEffectIdle）作为快路径可提前重开；另设超时兜底防卡死。
     */
    private void closeAnimGate() {
        if (animGateClosed) return;
        animGateClosed = true;
        mainHandler.removeCallbacks(animGateFailsafe);
        mainHandler.postDelayed(animGateFailsafe, ANIM_GATE_TIMEOUT_MS);
        mainHandler.removeCallbacks(animGatePoller);
        mainHandler.postDelayed(animGatePoller, ANIM_GATE_POLL_MS);
    }

    /**
     * 重开动画闸门：清除轮询器与超时兜底，继续派发被暂缓的后续消息
     * （阶段文字/卡片移动播完后的下一条消息、动画后的"是否发动/连锁"询问弹窗、可选择外框等）。
     */
    private void reopenAnimGate() {
        if (!animGateClosed) return;
        animGateClosed = false;
        mainHandler.removeCallbacks(animGatePoller);
        mainHandler.removeCallbacks(animGateFailsafe);
        drainPendingMsgs();
    }

    /**
     * 统一动画屏障：场地卡片移动/淡入淡出（GL 线程逐帧推进 aniFrame）、LP 变化动画
     * （浮字停留 30 帧 + 血条数字过渡 10 帧，主线程心跳推进）与居中特效（SpecEffectOverlay 队列/视图）
     * 任一仍在播放即返回 true；全部空闲才放行后续消息，从而把 GameFieldView 的卡片移动、
     * gameTopInfo 的血量变化纳入与特效、弹窗相同的串行序列。
     */
    private boolean isAnyAnimationBusy() {
        // 定时屏障持有期（MSG_ATTACK 弧光展示等）：未到期一律视为忙，串行化后续消息
        if (System.currentTimeMillis() < animHoldUntilMs) return true;
        boolean fieldBusy = false;
        try {
            // isAnimating()=卡片移动；isLpAnimating()=LP 浮字/血条动画（对齐 duelclient.cpp
            // MSG_DAMAGE/RECOVER/PAY_LPCOST 的 WaitFrameSignal(30)+(11)、MSG_LPUPDATE 的 WaitFrameSignal(11)）
            fieldBusy = field != null && (field.isAnimating() || field.isLpAnimating());
        } catch (Throwable ignored) {
        }
        boolean overlayBusy = false;
        try {
            overlayBusy = listener != null && listener.isSpecEffectBusy();
        } catch (Throwable ignored) {
        }
        return fieldBusy || overlayBusy;
    }

    /**
     * UI 特效队列排空回调（由 SpecEffectOverlay 的 OnIdleListener 触发）：重开闸门的快路径。
     * 特效排空不代表场地卡片动画也结束，故仍需 isAnyAnimationBusy() 复核；场地仍在动画则交轮询器等待，
     * 避免过早放行导致卡片移动与后续消息并发。
     */
    public void notifySpecEffectIdle() {
        if (!animGateClosed) return;
        if (isAnyAnimationBusy()) return;
        reopenAnimGate();
    }

    // === 对局状态标志与视角换算 ===

    /** Game::LocalPlayer：dInfo.isFirst ? player : 1 - player */
    boolean duelIsFirst = true;

    /** 对局状态标志，对齐 gframe dInfo.isStarted / dInfo.isInDuel / Game.is_siding，
     *  供聊天 ChatLocalPlayer 的分边与昵称判定使用。转换点（对齐 duelclient.cpp）：
     *  STOC_DUEL_START→started；MSG_START→inDuel；
     *  STOC_CHANGE_SIDE→siding 且 started=false；STOC_WAITING_SIDE/STOC_DUEL_END→复位 */
    public boolean duelStarted = false;
    public boolean inDuel = false;
    public boolean siding = false;

    public boolean isStarted() { return duelStarted; }
    public boolean isInDuel() { return inDuel; }
    public boolean isSiding() { return siding; }
    /** 本局我方是否先攻（对齐 dInfo.isFirst） */
    public boolean isDuelFirst() { return duelIsFirst; }

    public int localPlayer(int player) {
        return duelIsFirst ? player : 1 - player;
    }

    /** 给定协议侧玩家索引（0/1）是否代表我方（2=平局返回 false） */
    public boolean isSelfSide(int player) {
        return player != 2 && localPlayer(player & 1) == 0;
    }

    public void release() {
        disconnect();
        scriptEngine.release();
    }
}
