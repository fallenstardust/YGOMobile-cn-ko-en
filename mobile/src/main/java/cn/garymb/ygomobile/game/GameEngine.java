package cn.garymb.ygomobile.game;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.audio.SoundManager;
import cn.garymb.ygomobile.engine.LuaScriptEngine;
import cn.garymb.ygomobile.network.DuelClient;
import cn.garymb.ygomobile.network.YGOProtocol;
import cn.garymb.ygomobile.utils.CrashHandler;
import ocgcore.enums.GameMessage;

/**
 * 引擎统一门面：对外公共 API 保持不变（连接/大厅/对局操作/状态查询/命令状态），
 * 行为逻辑已按 // === 分栏拆分至同包协作类，由构造函数装配并经本类一行转发：
 * - DuelHintManager：stHintMsg 提示栏 + 提示栏文字生成
 * - SummonAnimationManager：召唤/无效居中动画
 * - DeckHandMotionManager：卡组与手卡堆动画（洗切 / 确认卡组顶 / 确认卡片 / 堆刷新）
 * - ConnectionManager：Connection（联机/本地房/残局/人机/录像）
 * - LobbyActions / GameActions：大厅与对局内主动操作
 * - CommandDataParser：Data parsing helpers（update/battle/idle 命令解析）
 * - DuelEventHandler：场地事件消息 handler（实现 GameMessageParser.MessageHandler）
 * - GameMessageParser：选择/提示类消息实现 + 静态 parse 派发
 * - ReplayPlayer：录像回放（取消息 + 卡码归一 + 按节奏投喂本引擎实况管线 + 播控）
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

        /**
         * MSG_HINT 的 HINT_OPSELECTED/RACE/ATTRIB/CODE/NUMBER 居中消息文本动画
         *（对齐 duelclient.cpp L1463-1521 各分支：SetStaticText(stACMessage) + PopupElement(wACMessage)
         * + WaitFrameSignal(40)）：text 为已格式化的 sys1510/1511/1512 文本，宿主据此在
         * layout_game_right 居中淡入淡出展示（复用 SpecEffectOverlay 串行队列）。
         */
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

        /**
         * 回放 rewind 重排（上一步/从头重放）：聊天/弹幕伪帧（{@link #REPLAY_CHAT_FRAME}）随
         * 消息流回卷后会重新派发，宿主应清空当前聊天显示（分侧聊天行与弹幕）防重复
         */
        default void onReplayChatReset() {}

        /**
         * 观战/录像切换视角（{@code ReplaySwap}）：昵称/LP/场面已左右对调，宿主应同步把双方
         * 聊天内容也左右对调，避免 player1 的消息因切视角错显示到 player2 一侧。
         */
        default void onViewpointSwapped() {}

        /**
         * 局域网撤回（CTOS_UNDO）已被服务端接受（STOC_UNDO_ACK = OK/REBUILT）：紧接会来一条
         * MSG_RELOAD_FIELD 全量重载。宿主应在此关掉旧询问弹窗与选择态蚂蚁线（该询问已被回退，
         * 重同步完成后服务端会重新挂回回退点的那一条询问），避免残留弹窗与新局面错位。
         */
        default void onUndoResync() {}

        /**
         * 局域网可撤回状态变更（STOC_UNDO_STATE）：服务端只在自上一个「行动宣言」（召唤 / 反转召唤 /
         * 特殊召唤 / 盖卡 / 发动效果 / 攻击宣言 / 切换阶段）之后存在可整段回退的动作时回 true。
         * 宿主据此让顶部回合数下方的撤回图标闪动发光；false 时静止隐藏（仍可点击时不提示）。
         */
        default void onUndoStateChanged(boolean available) {}
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
    private volatile boolean dispatchingMsg = false;    // 正在派发一条消息（防重入；回放投喂线程亦读取）
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
    /**
     * 本次连接的服务端声明支持 CTOS_UNDO（撤回）：由 STOC_JOIN_GAME 回显的 HostInfo pad
     * 能力位解出（见 {@code YGOProtocol.HOST_CAP_UNDO}）。房主端（{@link #isHost}）自带该能力，
     * 本标志让连入本机房间的另一台设备同样能发起自己一方的撤回。
     */
    public boolean serverCapsUndo = false;
    /**
     * 本次连接的服务端支持询问重发请求（{@code CTOS_ASK_RESEND}，见
     * {@code YGOProtocol.HOST_CAP_ASK_RESEND}）：只有它为真（或本机就是房主）时，
     * 「答完话却始终等不到回包」的看门狗才会去求援（见 {@link #notifyResponseSent}）。
     */
    public boolean serverCapsAskResend = false;
    /**
     * 服务端告知的「当前有可整段回退的动作锚点」（STOC_UNDO_STATE）。与 {@link #canUndo()} 相与
     * 后才是撤回图标闪动的条件：前者说明房间支持撤回，后者说明真的有一步可撤。
     */
    public boolean undoAvailable = false;
    /**
     * 撤回一次性闩锁（客户端防连续撤回护栏）：发出 CTOS_UNDO 时置位，本方真实发出
     * 一条应答（{@link #notifyResponseSent}，即有了新的操作）时清除。置位期间
     * {@link #isUndoPromptActive()} 恒为 false——即使撤回成功后服务端因锚点栈中仍有
     * 更早的旧锚点而继续下发 STOC_UNDO_STATE=1，撤回按钮也保持隐藏。
     * <p>服务端设计上允许连续撤回，但连续点击会让多轮 ACK/重同步/屏障布防叠加，
     * 破坏客户端消息派发链（表现为点卡仍能弹「发动」但应答被吞、阶段按钮不再出现，
     * 决斗无法继续）。故按「一次操作只许撤回一次」限制：本方有了新的操作后，
     * 服务端下一次状态刷新才会重新点亮按钮。
     */
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
    /** 按大厅座位号存储昵称（STOC_HS_PLAYER_ENTER pos 0-3）：0/1 我方队、2/3 对方队，
     *  供 STOC_CHAT 显示"昵称: 内容"（对齐 game.cpp AddChatMsg 的 hostname/clientname/hostname_tag/clientname_tag 前缀） */
    public final String[] seatNames = new String[]{"", "", "", ""};

    /** 对齐 dInfo.tag_player[2]：tag 模式该队当前轮到哪位选手行动（true=队友在打，
     *  名字按 hostname_tag/clientname_tag 显示，drawing.cpp L1036-1049）；
     *  MSG_START 初始化（非先动队置 true）、MSG_NEW_TURN(turn!=1) 逐回合翻转 */
    public final boolean[] tagPlayer = new boolean[2];

    /** 本地视角（0=我方/1=对方，或观战视角的左右侧）显示名：
     *  playerInfos 已按视角绑定（{@link #bindViewNames} / 回放 startSession），
     *  tag 模式该队 tag_player 在打时取队友名 nameTag（对齐 drawing.cpp 的 hostname/hostname_tag 分支） */
    public String displayName(int localIdx) {
        if (localIdx < 0 || localIdx >= playerInfos.length) return "";
        // Solo 模式下优先取等待面板中房主为席位代选的卡组名（slot0=我方、slot1=对方）；
        // 未选卡时回落到协议回显的玩家昵称，避免空列
        if (soloMode && localIdx < soloSeatDeckNames.length
                && soloSeatDeckNames[localIdx] != null && !soloSeatDeckNames[localIdx].isEmpty()) {
            return soloSeatDeckNames[localIdx];
        }
        PlayerInfo info = playerInfos[localIdx];
        if (field.isTag && localIdx < tagPlayer.length && tagPlayer[localIdx]
                && info.nameTag != null && !info.nameTag.isEmpty()) {
            return info.nameTag;
        }
        return info.name == null ? "" : info.name;
    }

    /** 是否观战位（对齐 duelclient.cpp STOC_DUEL_START 观战判定：非 tag selftype>1、tag selftype>3；
     *  与 gframe player_type=7 等价的本地视角参照） */
    public boolean isSpectator() {
        if (replayMode || isSingleMode) return false;
        int st = client.selfType;
        return field.isTag ? st > 3 : st > 1;
    }

    /** 进决斗前按大厅座位（seatNames）绑定本地视角昵称：
     *  1v1 对战方 左=座位 selfType、右=另一座位；观战方恒以座位 0 侧为左；
     *  tag 每侧取该队主位（pos0/pos2）与队友位（pos1/pos3）；
     *  对齐 duelclient.cpp STOC_HS_PLAYER_ENTER 填 hostname/hostname_tag/clientname/clientname_tag 的语义，
     *  使观战进入时 topInfo 也能显示对战双方名字（修复观战无名字 bug） */
    public void bindViewNames() {
        if (replayMode || isSingleMode) return; // 回放/残局由各自 runner 按视角直写 playerInfos
        int st = client.selfType;
        boolean tag = field.isTag;
        int myMain;
        if (tag) {
            myMain = (st > 3) ? 0 : (st & ~1);   // 观战以 A 队为左；玩家以自己队伍主位为左
        } else {
            myMain = (st > 1) ? 0 : st;          // 观战固定座位 0 侧在左；玩家取自己座位
        }
        int oppMain = tag ? (myMain ^ 2) : (myMain ^ 1);
        playerInfos[0].name = seatAt(myMain);
        playerInfos[1].name = seatAt(oppMain);
        playerInfos[0].nameTag = tag ? seatAt(myMain | 1) : "";
        playerInfos[1].nameTag = tag ? seatAt(oppMain | 1) : "";
    }

    private String seatAt(int seat) {
        return (seat >= 0 && seat < seatNames.length) ? seatNames[seat] : "";
    }

    // ==== 协作类（按 // === 分栏拆分，构造注入本引擎引用） ====

    public final DuelHintManager hintManager;
    public final SummonAnimationManager summonAnim;
    /** 卡组 / 手卡堆动画（洗切、确认卡组顶/确认卡片、堆刷新）：自 DuelEventHandler 拆出，
     *  实况与回放共用（回放快进时按 {@code field.instantPlace} 同步落位） */
    final DeckHandMotionManager deckMotion;
    final CommandDataParser dataParser;
    public final DuelEventHandler duelEvents;
    public final GameMessageParser messageParser;
    final ConnectionManager connection;
    final LobbyActions lobbyActions;
    final GameActions gameActions;
    /** 录像回放播放器（唯一回放入口：消息投喂本引擎实况管线，与联机/观战同一渲染路径） */
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
        this.messageParser = new GameMessageParser(this);
        this.connection = new ConnectionManager(this);
        this.lobbyActions = new LobbyActions(this);
        this.gameActions = new GameActions(this);
        this.replayPlayer = new ReplayPlayer(this);
        this.singleRunner = new SingleModeRunner(this);
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

    /** 启动残局（对齐 single_mode.cpp SinglePlayThread）。
     * @param luaPath 残局脚本路径
     * @param noShuffleToDeck 是否启用「不洗切时回卡组改为回顶端」标志 */
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

    /** CTOS_UNDO：请求服务端回退最近一次操作（仅局域网房主/人机房间，实现见 {@code LobbyActions}）。 */
    public void sendUndo() {
        // 一次性闩锁：已请求过一次撤回且本方尚未做出新操作时，后续点击（含连点残尾，
        // 按钮隐藏前的同一帧内重复触发）直接忽略，绝不允许第二次 CTOS_UNDO 上路
        if (undoBlockedUntilNewAction) return;
        undoBlockedUntilNewAction = true;
        // 先在本机布下应答屏障，再发出请求：TCP 保序保证服务端一定先处理 CTOS_UNDO、
        // 再处理此后到达的任何 CTOS_RESPONSE，故「撤回请求已送出、ACK 尚未回来」这段窗口里不会
        // 再有应答漏网（旧询问的自动应答/点击若在此刻发出，会被服务端当作回退后新询问的答复，
        // 直接把回退点之后的局面又推进一步）。若服务端拒收（DENIED），局面未变，随即解除布防
        // 并用询问快照重建界面，期间被丢弃的应答会随重派发重新发出
        armUndoResponseBarrier();
        lobbyActions.sendUndo();
        // 立即走一次状态回调让按钮收起（不等 ACK）：isUndoPromptActive 已因闩锁为 false，
        // delegate 侧 setUndoPrompt(available && isUndoPromptActive()) 自然熄灭图标
        mainHandler.post(() -> {
            if (listener != null) listener.onUndoStateChanged(undoAvailable);
        });
    }

    /**
     * 本房间是否可用撤回：本机建立主机的房间（CreateHostDialog 建主与人机/WindBot 建主，
     * {@link #isHost}）或连入的服务端声明支持 CTOS_UNDO（{@link #serverCapsUndo}）。
     * 第三方 gframe 服务器两者均为 false，撤回按钮不出现。
     */
    public boolean canUndo() {
        return (isHost || serverCapsUndo) && !isSingleMode && !replayMode;
    }

    // === 撤回（undo）重同步：STOC_UNDO_ACK 暂存的回合信息，到 MSG_RELOAD_FIELD 落地时消费 ===

    /** 待消费的撤回重同步回合数；-1 = 无待消费 latch（非撤回触发的 reload 不动现有值）。 */
    volatile int undoResyncTurn = -1;
    /** 回退后的当前回合玩家（协议侧 0/1）与阶段值。 */
    volatile int undoResyncPlayer = -1;
    volatile int undoResyncPhase = -1;

    /**
     * STOC_UNDO_ACK 落地（已由 {@code DuelClient.StocHandler} 投到主线程）：成功/重建两类结果
     * 之后必定紧跟一条 MSG_RELOAD_FIELD，故此处只暂存回退后的回合/阶段，交由
     * {@link #applyUndoResync} 在重载结束时写回 field——reload 载荷不含回合号与阶段，
     * 且不能重发 MSG_START（那会走 {@code field.clear()} 把回合计数归零）。
     * 拒绝结果不改变局面（故不动派发链），只解除本机布防并提示；若布防期间吞过应答，
     * 则重派发最近一条询问，给被吞的自动应答第二次机会。
     */
    public void onUndoAck(int result, int turn, int currentPlayer, int phase) {
        if (result == YGOProtocol.UNDO_ACK_DENIED) {
            undoResyncTurn = -1;
            // 撤回未成立：局面与待应答询问都还在，解除布防并用询问快照重建选择 UI——
            // 仅当确实丢弃过应答才重派发（否则重派发一个已不属于现在的询问会凭空弹出幽灵窗）：
            // 弹窗从未因拒绝而关闭过，普通点击只需重新一击；但 chkAutoChain / 放弃连锁那类不经点击的
            // 自动应答被丢弃后无人再触发，必须靠重派发给它第二次机会
            boolean droppedWhileArmed = undoBarrierDropped;
            releaseUndoResponseBarrier();
            if (droppedWhileArmed) restorePendingQuestionUi();
            postUndoHint("撤回不可用：没有可撤回的操作，或该操作不是你这方做出的");
            return;
        }
        // 本局客户端侧消息帧流已含被撤销的动画帧与重同步帧，无法与响应段对齐 →
        // 作废本局 MSG 尾段（退回服务端已重建的 .yrp 单文件，仍可按脚本重放完整观看）
        invalidateCurrentMsgSegment();
        // 清掉「被撤销片段」留在客户端派发链与应答出口上的残留（详见方法注释）
        clearStaleMsgStateAfterUndo();
        undoResyncTurn = turn;
        undoResyncPlayer = currentPlayer;
        undoResyncPhase = phase;
        postUndoHint(result == YGOProtocol.UNDO_ACK_REBUILT
                ? "撤回失败：已按原局面重建并重同步" : "已撤回上一步操作");
        // 关弹窗/复位交互会话必须同步做（本方法已在主线程，且严格早于紧随其后的 reload/refresh/
        // 新询问的派发 runnable）：若 post 到队列尾，它会排到整批重同步包之后才执行，把服务端
        // 刚重挂的新询问弹窗一并 dismiss 掉 → 界面再也拿不到待应答询问，决斗无法继续
        if (listener != null) listener.onUndoResync();
    }

    private void postUndoHint(String text) {
        mainHandler.post(() -> {
            if (listener != null) listener.onHintMessage(text);
        });
    }

    /**
     * 撤回重同步落点（{@code DuelEventHandler.onReloadField} 末尾调用）：把 STOC_UNDO_ACK
     * 暂存的回合数/当前回合玩家/阶段写回 field 并消费 latch；无 latch 时什么都不做。
     *
     * @return true = 本次重载确实来自撤回（调用方需额外重刷回合高亮/阶段文本）
     */
    boolean applyUndoResync(GameField field) {
        int turn = undoResyncTurn;
        if (turn < 0) return false;
        undoResyncTurn = -1;
        field.turnCount = turn;
        if (undoResyncPlayer >= 0) {
            field.currentPlayer = localPlayer(undoResyncPlayer & 1);
        }
        if (undoResyncPhase >= 0) {
            field.currentPhase = undoResyncPhase;
        }
        // 单人模式视角恒随行动方（同 DuelEventHandler.onNewTurn 的自动翻视角）：撤回把回合
        // 所有权翻回另一席位时（典型：刚跨过回合就撤回 EP 宣言，回退到自己按 EP 之前），
        // ACK 里的回合玩家按当前视角映射落在上方（currentPlayer==1），必须把视角翻回
        // 行动方在下半屏。走 requestSpectatorSwap 同一延迟路径：交换在下一消息消费点执行，
        // 先于重同步批次的后续消息（refresh/重挂询问），容器对调与消息换算不竞争。
        if (soloMode && !replayMode && inDuel && field.currentPlayer == 1) {
            requestSpectatorSwap();
        }
        return true;
    }

    /** 决斗结束/断线/重新开局：丢弃残留 latch，避免下一局的普通 reload 误消费 */
    public void clearUndoResync() {
        undoResyncTurn = -1;
        undoResyncPlayer = -1;
        undoResyncPhase = -1;
        releaseUndoResponseBarrier();
        // 新一局不存在「等本方新操作」的历史包袱：闩锁一并复位
        // （本方法在 STOC_DUEL_START 的 onDuelStart 中调用，服务端随后会重新下发可撤回状态）
        undoBlockedUntilNewAction = false;
    }

    /**
     * 撤回成立后清理「被撤销片段」在客户端留下的三类残留（在 STOC_UNDO_ACK 落地时执行，主线程）：
     * ① 尚未派发的排队消息——动画闸门关闭时被暂缓的消息里就有被撤销的动画帧与旧询问，闸门一开
     *    它们会重新播动画/重新弹窗，把界面带回已被回退的场面；服务端紧接着会重发
     *    MSG_RELOAD_FIELD + 各区域 refresh 全量覆盖，故丢弃这些消息不会留下缺口；
     * ② lastGameMsg 快照——服务端下一次 MSG_RETRY 会经 {@link #replayLastGameMsg()} 重放它，
     *    重放的正是被撤销的那条旧询问 UI；
     * ③ 动画闸门与定时屏障——被撤销片段的动画可能正把闸门关着，不强制重开的话 reload 与重挂的
     *    询问会一直排到 8s 超时兜底，期间界面拿不到新询问。
     * 最后布应答代次屏障（{@link #armUndoResponseBarrier()}）。
     */
    private void clearStaleMsgStateAfterUndo() {
        pendingMsgs.clear();
        lastGameMsgType = -1;
        lastGameMsgBody = null;
        retryReplayCount = 0;
        // 询问快照同步作废：被撤销的那条询问已不存在，往后的 DENIED/兜底不得再把它重派回界面
        lastQuestionType = -1;
        lastQuestionBody = null;
        animGateClosed = false;
        animHoldUntilMs = 0;
        mainHandler.removeCallbacks(animGatePoller);
        mainHandler.removeCallbacks(animGateFailsafe);
        // 本方命令标记与待应答列表一并作废：服务端重挂询问前会先留一段空白（丢弃在路上的迟到
        // 应答），这期间若留着被撤销那一段算出的命令列表，点卡仍能弹出「发动 / 召唤 / 特殊召唤」
        // 菜单并按旧索引编码应答——新询问一到手就会把这份错拍应答喂进引擎
        if (field != null) {
            clearCommandFlags();
            resetFieldCommandHints();
            field.clearSelectionVisuals();
        }
        armUndoResponseBarrier();
    }

    // === 撤回后的应答代次屏障 ===
    // 撤回把一段已完成的动作连同其后的所有询问一起抹掉，但客户端可能仍握着旧询问的产物：
    // 尚未关闭的弹窗、未取消的延迟自动应答、按旧命令列表编出的点击。这些应答打到回退后的
    // 新局面上就是「错拍应答」——服务端把它当作当前待应答询问的答复喂给引擎，轻则非法应答
    // 引发 MSG_RETRY 风暴（表现为通讯中断、无法继续决斗），重则旧列表里的合法指令（切换阶段等）
    // 直接作用在新局面上（表现为凭空进入下个回合，本方失去操作机会）。故以询问代次为准绳拦掉。

    /** 询问代次计数：每向 UI 派发一条 SELECT 类询问（{@code EngineCallbackDelegate.onSelectRequired}）
     * 自增一次，标识「界面上此刻挂着的是第几次询问」。
     */
    private volatile long questionSeq;
    /**
     * 应答屏障下界：撤回成立时记为当时的 questionSeq；此后代次未前进（还没有新询问派发下来）
     * 则丢弃一切出口应答。新询问一到，代越此界，屏障自动失效直到下次撤回。-1 = 未布防。
     */
    private volatile long undoBarrierSeq = -1L;
    /**
     * 布防兜底超时：万一服务端不受理 CTOS_UNDO（一个 ACK 都不回），屏障至多持有 5s 即自动解除，
     * 避免“按了撤回就再也发不出应答”的死锁。解除时不重派询问：未收到 ACK 就没有走过
     * 重同步路径，弹窗从未被关闭，界面仍在原地等玩家应答；而回退后的询问属于对方席位时
     * 本就没有自己的询问，重派会凭空弹出旧询问的幽灵窗。
     */
    private static final long UNDO_BARRIER_TIMEOUT_MS = 5000L;
    private final Runnable undoBarrierFailsafe = () -> {
        if (!isResponseBlocked()) return;
        Log.w(TAG, "undo response barrier timed out: release");
        releaseUndoResponseBarrier();
    };
    /** 最近一条询问（MSG_SELECT_* 10..26）快照，供撤回被拒时重派发询问、重建 UI */
    private int lastQuestionType = -1;
    private byte[] lastQuestionBody;

    /** 询问派发点调用：推进询问代次（在投给 UI 之前同步执行，确保与应答出口之间的先后可见） */
    public void onQuestionDispatched() {
        questionSeq++;
    }

    /** 撤回成立：以当前询问代次为下界布防，此后至新询问到达之间的应答一律丢弃 */
    private void armUndoResponseBarrier() {
        undoBarrierSeq = questionSeq;
        undoBarrierDropped = false;
        mainHandler.removeCallbacks(undoBarrierFailsafe);
        mainHandler.postDelayed(undoBarrierFailsafe, UNDO_BARRIER_TIMEOUT_MS);
    }

    /** 解除应答屏障（撤回被拒/超时：局面未变，界面上的询问照常应答） */
    private void releaseUndoResponseBarrier() {
        undoBarrierSeq = -1L;
        mainHandler.removeCallbacks(undoBarrierFailsafe);
    }

    /**
     * 重派发最近一条询问快照（对齐 {@link #replayLastGameMsg()} 的 RETRY 重建路径）：
     * 走同一套解析→弹窗→（可按时）自动应答链，使选择 UI 与被丢掉的应答都重新获得机会。
     */
    private void restorePendingQuestionUi() {
        int type = lastQuestionType;
        byte[] body = lastQuestionBody;
        if (type < 0 || body == null) return;
        ByteBuffer buf = ByteBuffer.wrap(body);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        pendingMsgs.offer(() -> dispatchGameMsg(type, buf));
        drainPendingMsgs();
    }

    /**
     * 应答出口拦截（{@code GameActions.sendResponse}）：屏障已布防且询问代次尚未前进，
     * 说明本次应答来自已被撤销的旧询问，丢弃即可——回退后的新局面会在服务端重挂询问，
     * 正常应答流程随即恢复。屏障只压住「新询问到达之前」这一段，不会误伤新询问的合法应答。
     */
    public boolean isResponseBlocked() {
        long barrier = undoBarrierSeq;
        if (barrier < 0 || questionSeq > barrier) return false;
        // 记下确实丢过一次：服务端若拒收撤回，这些被吞的应答需要一次重派发机会
        undoBarrierDropped = true;
        return true;
    }

    /** 本次布防期间是否丢弃过出口应答（决定撤回被拒后要不要重派发询问） */
    private volatile boolean undoBarrierDropped;

    /**
     * 捕获当前询问代次令牌，供「延迟若干毫秒之后才发出应答」的路径（放弃连锁的随机等待）
     * 在回调触发时自检：屏障拦得住新询问到达之前的应答，而这类定时器可能恰在新询问到达之后
     * 才到期（彼时代次已前进、屏障失效），却仍带着旧询问的意志去答复新局面，故必须由发起方把
     * 代次快照进闭包，到期发现代次已变即放弃本次应答。
     */
    public long captureQuestionToken() {
        return questionSeq;
    }

    /** 令牌所对应的询问是否已被更晚到达的询问取代（true = 该次应答作废） */
    public boolean isQuestionStale(long token) {
        return token != questionSeq;
    }

    // === 询问丢失自愈：答完上一步却收不到下一条询问时的看门狗 ===

    /**
     * 最近一次收到「挂给本席位的询问」或「等对方」提示（MSG_SELECT_* / MSG_ANNOUNCE_* /
     * 猜拳 / MSG_WAITING）的时刻。 正常的服务端在每次收到应答后必会下发二者之一，
     * 所以它们一到就等于界面重新有了可应答项，不需要任何求援。
     */
    private volatile long lastAskRxAt;
    /** 最近一次真正向服务端发出 CTOS_RESPONSE 的时刻（被应答屏障丢弃的不算）。 */
    private volatile long lastResponseTxAt;
    private int askProbeCount;
    private long askProbeAt;
    private boolean askWatchdogRunning;
    /** 应答发出后多久仍收不到任何询问/等待提示，即判定询问丢在半路。局域网里这个间隔约等于一次往返的数十倍。 */
    private static final long ASK_STUCK_GRACE_MS = 2500L;
    /** 同一次卡死最多求援几次（用完仍无回包则交服务端保活心跳与日志，不再刷盘）。 */
    private static final int ASK_PROBE_MAX = 3;
    /** 两次求援之间的最小间隔。 */
    private static final long ASK_PROBE_INTERVAL_MS = 1500L;
    /** 看门狗复查周期。 */
    private static final long ASK_WATCHDOG_TICK_MS = 500L;

    private final Runnable askWatchdogTask = new Runnable() {
        @Override
        public void run() {
            if (!askWatchdogRunning) {
                return;
            }
            long tx = lastResponseTxAt;
            if (tx == 0L || tx <= lastAskRxAt || !canProbeAsk() || askProbeCount >= ASK_PROBE_MAX) {
                // 新询问（或等待提示）已到手、或已不在决斗中、或求援已用尽：停止复查
                askWatchdogRunning = false;
                return;
            }
            long now = System.currentTimeMillis();
            if (now - tx >= ASK_STUCK_GRACE_MS && now - askProbeAt >= ASK_PROBE_INTERVAL_MS) {
                askProbeCount++;
                askProbeAt = now;
                Log.w(TAG, "no question or waiting hint arrived " + (now - tx)
                        + "ms after our response: ask server to re-hang (try " + askProbeCount + ")");
                client.sendAskResend();
            }
            mainHandler.postDelayed(this, ASK_WATCHDOG_TICK_MS);
        }
    };

    /** 本端可以发求援包：联机决斗中、非回放/残局/观战，且服务端认得这个包号。 */
    private boolean canProbeAsk() {
        return duelStarted && !replayMode && !isSingleMode && !isSpectator()
                && (isHost || serverCapsAskResend);
    }

    /** 由 {@link GameActions} 在实际发出 CTOS_RESPONSE 后回调：开始看门狗计时。 */
    public void notifyResponseSent() {
        // 本方有了新的操作（应答真实发出，未被屏障丢弃）：解除撤回一次性闩锁。此后服务端
        // 处理完这条应答会重新下发 STOC_UNDO_STATE，按钮按真实可撤回状态恢复显示
        undoBlockedUntilNewAction = false;
        lastResponseTxAt = System.currentTimeMillis();
        if (!canProbeAsk() || askWatchdogRunning) {
            return;
        }
        askWatchdogRunning = true;
        mainHandler.postDelayed(askWatchdogTask, ASK_WATCHDOG_TICK_MS);
    }

    /** 询问/等待提示到达：界面重新有事可做，求援计数作废。 */
    private void notifyAskReceived() {
        lastAskRxAt = System.currentTimeMillis();
        askProbeCount = 0;
    }

    /** 新一局开始（STOC_DUEL_START）：作废上一局的应答/询问时戳与求援计数，避免局间残留误发求援包。 */
    public void resetAskWatchdog() {
        lastAskRxAt = 0L;
        lastResponseTxAt = 0L;
        askProbeCount = 0;
        askProbeAt = 0L;
        askWatchdogRunning = false;
        mainHandler.removeCallbacks(askWatchdogTask);
    }

    /** 这条消息是否让本界面重新有了可应答项：挂给本席位的询问，或告知「轮到对方」的等待提示。 */
    private static boolean isAskOrWaitingMessage(int msgType) {
        if (msgType == GameMessage.Waiting.value()) {
            return true;
        }
        if (msgType >= GameMessage.SelectBattleCmd.value()
                && msgType <= GameMessage.SelectUnselectCard.value()) {
            return true;
        }
        // 宣言类与猜拳同样要本席位应答（消息号在 10..26 之外，服务端一样会挂出它们）
        return msgType == GameMessage.RockPaperScissors.value()
                || (msgType >= GameMessage.AnnounceRace.value()
                && msgType <= GameMessage.AnnounceNumber.value());
    }

    /**
     * 本席位当前是否真的可撤回：房间支持撤回（{@link #canUndo()}）、服务端确认存在
     * 可回退的动作锚点（{@link #undoAvailable}），且本方尚未处于「已撤回、等待新操作」
     * 的闩锁期（{@link #undoBlockedUntilNewAction}）。UI 仅在三者同时成立时显示撤回按钮；
     * 闩锁期内服务端复发的 state=1 会被这里压住，按钮保持隐藏。
     */
    public boolean isUndoPromptActive() {
        return canUndo() && undoAvailable && !undoBlockedUntilNewAction;
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

    /**
     * 强制重新派发一次状态回调（不做状态去重）：用于 solo 连续换 side 等“状态未变但需
     * 重建界面”的场景——setState 对相同状态会早退不回调，此处直接走 onStateChanged 分发。
     */
    public void redispatchState(GameState state) {
        if (listener != null) listener.onStateChanged(state);
    }

    // === 游戏消息串行闸门（原 DuelClient.ClientListener.onGameMsg 投递口，实现见 StocHandler） ===

    /**
     * STOC_GAME_MSG 入队（对齐 duelclient.cpp ClientAnalyze 的调用点）：
     * 入队后由闸门串行派发：动画消息会关闭闸门，暂缓后续消息（对齐 C++ WaitFrameSignal 阻塞语义）
     */
    public void enqueueGameMsg(int msgType, ByteBuffer data) {
        data.order(ByteOrder.LITTLE_ENDIAN);
        // 询问自愈：只要收到挂给本席位的询问或「等对方」提示，就说明服务端仍在正常推进对局，
        // 看门狗的求援计时就此作废（否则一次正常的长考会被误认为卡死）
        if (isAskOrWaitingMessage(msgType)) {
            notifyAskReceived();
        }
        // 对齐 duelclient.cpp L1298-1303：除 MSG_RETRY 外每条消息均缓存一份快照，
        // 供服务端下发 RETRY（无效应答）后重放重建选择 UI（服务端不会重发原 SELECT）
        if (msgType != GameMessage.Retry.value()) {
            ByteBuffer snapshot = data.duplicate();
            byte[] body = new byte[snapshot.remaining()];
            snapshot.get(body);
            lastGameMsgType = msgType;
            lastGameMsgBody = body;
            retryReplayCount = 0;
            // 单独留存最近一条询问（MSG_SELECT_* = 10..26）快照：撤回被拒/超时时据此重派发询问
            if (msgType >= GameMessage.SelectBattleCmd.value()
                    && msgType <= GameMessage.SelectUnselectCard.value()) {
                lastQuestionType = msgType;
                lastQuestionBody = body;
            }
            recordMsgFrame(msgType, body);
        }
        // 入队后由闸门串行派发：动画消息会关闭闸门，暂缓后续消息（对齐 C++ WaitFrameSignal 阻塞语义）
        pendingMsgs.offer(() -> dispatchGameMsg(msgType, data));
        drainPendingMsgs();
    }

    /** 上一条非 RETRY 游戏消息缓存（对齐 duelclient.cpp last_successful_msg）与重放次数守卫 */
    private int lastGameMsgType = -1;
    private byte[] lastGameMsgBody;
    private int retryReplayCount = 0;

    // === 逐局 MSG 录制（STOC_REPLAY 保存时合并出双兼容 V2 文件，见 ReplayMsgMerger） ===

    /** 当前局已录帧（每帧 = [消息号字节]+payload，与服务端→players[0] 的完整引擎消息同构） */
    private final List<byte[]> msgSegFrames = new ArrayList<>();
    /** 已完结的分局段（match 三局每局一段），与 STOC_REPLAY 到达顺序 FIFO 配对 */
    private final List<List<byte[]>> msgSegDone = new ArrayList<>();
    private final Object msgRecLock = new Object();

    /**
     * 录制一条引擎消息（网络线程在 enqueueGameMsg 快照处调用，与响应流同序）：
     * MSG_START 分局（上一段非空则先完结入队），MSG_WIN/MSG_MATCH_KILL/MSG_DUEL_WINNER 完结本段；
     * MSG_RETRY 不入帧（对齐 C++ 录像不记 retry；其本地重放经 replayLastGameMsg 不经此入口，不会重复）
     */
    private void recordMsgFrame(int msgType, byte[] body) {
        synchronized (msgRecLock) {
            // 撤回作废旧段：丢弃已录帧，直到下一局 MSG_START 才重新开段
            if (msgSegInvalid) {
                msgSegFrames.clear();
                if (msgType != GameMessage.Start.value()) {
                    return;
                }
                msgSegInvalid = false;
            }
            if (msgType == GameMessage.Start.value() && !msgSegFrames.isEmpty()) {
                msgSegDone.add(new ArrayList<>(msgSegFrames));
                msgSegFrames.clear();
            }
            byte[] frame = new byte[body.length + 1];
            frame[0] = (byte) msgType;
            System.arraycopy(body, 0, frame, 1, body.length);
            msgSegFrames.add(frame);
            if (msgType == GameMessage.Win.value() || msgType == GameMessage.MatchKill.value()
                    || msgType == GameMessage.DuelWinner.value()) {
                msgSegDone.add(new ArrayList<>(msgSegFrames));
                msgSegFrames.clear();
            }
        }
    }

    /** 取最早已完结的 MSG 段（与到过的 STOC_REPLAY 按序配对）；无完结段时退而取当前段 */
    public List<byte[]> takeRecordedMsgSegment() {
        synchronized (msgRecLock) {
            if (!msgSegDone.isEmpty()) return msgSegDone.remove(0);
            if (!msgSegFrames.isEmpty()) {
                List<byte[]> cur = new ArrayList<>(msgSegFrames);
                msgSegFrames.clear();
                return cur;
            }
        }
        return java.util.Collections.emptyList();
    }

    /** 新一场决斗开始（STOC_DUEL_START）：丢弃上一场残留段，防串局 */
    public void resetMsgRecording() {
        synchronized (msgRecLock) {
            msgSegFrames.clear();
            msgSegDone.clear();
            msgSegInvalid = false;
        }
    }

    /** 本局消息帧流是否已因撤回而作废（作废期间不再累计帧，直到下一局 MSG_START） */
    private boolean msgSegInvalid;

    /**
     * 撤回成功后作废本局的 MSG 尾段录制（见 {@link #onUndoAck}）：被撤销的动画帧已写入段内、
     * 随后又是 reload/refresh 重同步帧，与重建后的响应段不再对齐，强行保存会得到错位的增强录像。
     */
    void invalidateCurrentMsgSegment() {
        synchronized (msgRecLock) {
            msgSegInvalid = true;
            msgSegFrames.clear();
        }
    }

    /**
     * 回放聊天伪帧消息号：ocgcore common.h 消息号表之外（0xF1，引擎永不产出），本工程专用。
     * 帧格式 [0xF1][playerType(1B)][UTF-8 文本]，随引擎 MSG 帧一并录进录像 V2 消息流尾段——
     * libygo（ocgcore+script 重跑）只顺序消费响应段、不读尾段，天然保持兼容可播；
     * 无引擎回放由 ReplaySource::next 在帧界流中拦截、ReplayPlayer 独立派发显示（不投喂管线）。
     */
    public static final int REPLAY_CHAT_FRAME = 0xF1;

    /**
     * 录制一条聊天/观战发言为 0xF1 伪帧（EngineCallbackDelegate.onChatReceived 调用，
     * 实况唯一的聊天派发入口，覆盖对局玩家/队友/观战/系统全部类型）：
     * 回放模式丢弃（防自录循环）；当前段在录则追加段尾（与引擎帧按到达序交错，回放时序还原）；
     * 本局已 MSG_WIN 完结而 STOC_REPLAY 尚未取走时追加最近完结段（决胜局后 "gg" 类聊天入录像）；
     * 决斗外（大厅/两段之间无待存录像）无对应录像可依附，丢弃。
     */
    public void recordChatFrame(int playerType, String message) {
        if (replayMode || message == null || message.isEmpty()) return;
        byte[] text = message.getBytes(StandardCharsets.UTF_8);
        byte[] frame = new byte[text.length + 2];
        frame[0] = (byte) REPLAY_CHAT_FRAME;
        frame[1] = (byte) playerType;
        System.arraycopy(text, 0, frame, 2, text.length);
        synchronized (msgRecLock) {
            if (!msgSegFrames.isEmpty()) {
                msgSegFrames.add(frame);
            } else if (!msgSegDone.isEmpty()) {
                msgSegDone.get(msgSegDone.size() - 1).add(frame);
            }
        }
    }

    /**
     * 收到 MSG_RETRY 后重放上一条消息（对齐 duelclient.cpp L1351-1404 的
     * ClientAnalyze(last_successful_msg)）：重建被无效应答打断的选择 UI。
     * 连续重放设上限防“自动非法应答 ↔ 服务端 RETRY”热循环，收到新消息即清零。
     */
    public void replayLastGameMsg() {
        if (lastGameMsgType < 0 || lastGameMsgBody == null) {
            // 服务端现在会直接重发询问（见 DuelAnalyzer 的 MSG_RETRY 分支），本方法仅剩兜底作用；
            // 兜底也拿不到任何快照时界面将再也等不到询问，此处必须落盘现场而非静默 return
            if (lastQuestionType >= 0 && lastQuestionBody != null) {
                restorePendingQuestionUi();
                return;
            }
            Log.w(TAG, "retry: no cached message to replay (undo cleared it and no question snapshot)");
            return;
        }
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
        } catch (RuntimeException | Error e) {
            // 崩溃挂钩：主线程派发链路（含回放投喂的每条消息、观战与联机对局）异常先带消息号
            // 落盘 ygocore/log，再原样抛出交给全局 CrashHandler，既不改变崩溃行为又留下现场
            CrashHandler.getInstance().report("dispatchGameMsg type=" + msgType
                    + (replayMode ? " (replay step)" : " (live)"), e);
            throw e;
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
                // 观战「切换视角」：延迟到消息消费点执行（对齐 duelclient.cpp is_swapping →
                // ClientAnalyze 内 ReplaySwap），与消息处理同在主线程派发链上，不与字段读写竞争
                if (pendingSpectatorSwap) {
                    pendingSpectatorSwap = false;
                    performSpectatorSwap();
                }
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
    public boolean isAnyAnimationBusy() {
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

    /** 待派发消息队列是否仍有存量（回放投喂线程据此等待实况管线消化完再喂下一条） */
    public boolean hasPendingMsgs() {
        return !pendingMsgs.isEmpty();
    }

    /** 队列已空且主线程未处于派发中：实况侧场地数据已追上游标位置（回放切片前对齐卡数用） */
    public boolean isMsgQueueIdle() {
        return pendingMsgs.isEmpty() && !dispatchingMsg;
    }

    /**
     * 回放快进专用：强制重开动画闸门并在主线程一次性排空 pendingMsgs（忽略动画占用）。
     * 快进期间 replaySkip+instantPlace 已保证消息处理不产生动画/特效，排空在单个主线程
     * 消息周期内完成；投喂线程随后轮询 hasPendingMsgs()==false 确认落点。
     */
    public void drainReplayQueueNow() {
        mainHandler.post(() -> {
            animGateClosed = false;
            animHoldUntilMs = 0;
            mainHandler.removeCallbacks(animGatePoller);
            mainHandler.removeCallbacks(animGateFailsafe);
            dispatchingMsg = true;
            try {
                Runnable r;
                while ((r = pendingMsgs.poll()) != null) {
                    r.run();
                }
            } finally {
                dispatchingMsg = false;
            }
        });
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

    /** 回放模式：录像消息经 ReplayPlayer 切片后投入本引擎实况管线渲染。SELECT 询问/胜负结算
     *  在 GameMessageParser 侧抑制（应答已录制在文件里，弹选择窗会悬挂流程） */
    public boolean replayMode = false;
    /**
     * 残局（single mode）模式：本地引擎直驱残局 lua（{@link SingleModeRunner}），消息投喂本引擎
     * 实况管线。与 {@code replayMode} 互斥：残局需真实交互（弹选择窗、路由应答到引擎、显示投降），
     * 故不置 replayMode；由 {@link GameActions#sendResponse} / {@code EngineCallbackDelegate} 按本标志分支。
     */
    public boolean isSingleMode = false;

    /**
     * 局域网纯单人模式（房主一人同时操控双方）：服务端 HostInfo.mode bit4=0x10 时，本连接收到
     * 双方完整信息，select 消息均弹本方 UI，回合切换自动 performSpectatorSwap 翻转视角，
     * 卡片可点击性 isSelfSide 恒为 true。由 {@code EngineCallbackDelegate.onJoinGame} 解析 STOC_JOIN_GAME
     * 中的 mode 字段置位；不影响 replayMode/isSingleMode 的现有含义。
     */
    public boolean soloMode = false;
    /** Solo 模式下四个席位展示的卡组名（index 0..3）：由 PlayerWaitingDialog 在房主为
     *  席位 1/2/3 选卡时回填，enterDuelingUI 会优先用 slot0/slot1 名称写回 GameTopInfo，
     *  代替默认玩家昵称。非 solo 模式不写入。 */
    public final String[] soloSeatDeckNames = new String[]{"", "", "", ""};
    /** Solo 模式下四个席位选定的卡组文件路径（index 0..3）：与 {@link #soloSeatDeckNames} 同源，
     *  由 PlayerWaitingDialog 回填；match 换 side 时按席位顺序载入对应卡组供编辑。非 solo 不写入。 */
    public final String[] soloSeatDeckPaths = new String[]{"", "", "", ""};
    /** Solo match 换 side 会话内已弹出的换 side 界面计数：每次进入 SIDING 载入
     *  {@link #soloSeatDeckPaths} 的下一席位卡组并自增，新局开局（onDuelStart）复位为 0。 */
    public int soloSideSession = 0;
    /** 回放快进重排中（undo/restart/跳回合）：drawspec 覆盖层与长动画派发丢弃，配合
     *  GameField.instantPlace 即时落位与音效静默，令闸门不阻塞、队列单帧排空 */
    public boolean replaySkip = false;

    public boolean isStarted() { return duelStarted; }
    public boolean isInDuel() { return inDuel; }
    public boolean isSiding() { return siding; }
    /** 本局我方是否先攻（对齐 dInfo.isFirst） */
    public boolean isDuelFirst() { return duelIsFirst; }

    public int localPlayer(int player) {
        return duelIsFirst ? player : 1 - player;
    }

    /**
     * 回放态专用：从录像文件头部（{@link ReplayReader} 解出的 .yrp 保存的双方全部卡片 code，
     * 已按本机卡表归一）直取卡组(0x01)/额外卡组(0x40)卡码列表，供堆叠区查看弹窗不经实况
     * ClientCard 回填展开全部卡面（修复回填时序/索引脆弱导致的里侧卡时隐时现）。
     * viewPlayer 为本地视角容器索引（0=我方/1=对方）；非回放模式、非卡组/额外区域或
     * 头部无数据时返回 null，调用方回落到实况区域列表，实时决斗/观战行为不变。
     */
    public List<Integer> getReplayZoneCodes(int viewPlayer, int location) {
        if (!replayMode || replayPlayer == null) return null;
        return replayPlayer.getReplayZoneCodes(viewPlayer, location);
    }

    /**
     * 观战「切换视角」请求（对齐 event_handler.cpp BUTTON_REPLAY_SWAP 观战分支 →
     * DuelClient::SwapField 仅置 is_swapping，实际交换延迟到消息消费点）：
     * 实况对局且非回放才有效；置位后投递一次排空，闸门持有期内则在动画结束后执行
     */
    volatile boolean pendingSpectatorSwap;

    public void requestSpectatorSwap() {
        if (replayMode || !inDuel) return;
        pendingSpectatorSwap = true;
        mainHandler.post(this::drainPendingMsgs);
    }

    /**
     * 观战视角交换（对齐 client_field.cpp ClientField::ReplaySwap，与 ReplayPlayer.performSwapField
     * 实况版同构）：① duelIsFirst（dInfo.isFirst）翻转——后续消息的 localPlayer 映射随视角翻转；
     * ② field.swapField() 对调双方各区列表并逐卡重算 controler（含超量素材/连锁/disabledField 高低位）；
     * ③ 昵称/LP 对调 + currentPlayer 翻转，血条/卡数/回合高亮按新视角重取。
     * 在主线程派发链（drainPendingMsgs）内调用。
     */
    private void performSpectatorSwap() {
        duelIsFirst = !duelIsFirst;
        field.swapField();
        PlayerInfo a = playerInfos[0];
        PlayerInfo b = playerInfos[1];
        String tmpName = a.name;
        a.name = b.name;
        b.name = tmpName;
        // tag 队友名随视角一并左右对调（对齐 C++ ReplaySwap 的 hostname_tag↔clientname_tag swap）
        String tmpNameTag = a.nameTag;
        a.nameTag = b.nameTag;
        b.nameTag = tmpNameTag;
        int tmpLp = a.lp;
        a.lp = b.lp;
        b.lp = tmpLp;
        int tmpStart = a.startLp;
        a.startLp = b.startLp;
        b.startLp = tmpStart;
        field.currentPlayer = 1 - field.currentPlayer;
        // dInfo.lp 是视角索引的血条显示值：容器已对调，直接按 players[].lp 重新对齐
        field.dInfo.lp[0] = field.players[0].lp;
        field.dInfo.lp[1] = field.players[1].lp;
        field.refreshAllCards();
        if (listener != null) {
            listener.onFieldChanged();
            listener.onPlayerInfoUpdated(0);
            listener.onPlayerInfoUpdated(1);
            // 回合方高亮（LPBarFrame 彩色/灰色与名字色）随视角翻转重刷
            listener.onTurnStarted(field.currentPlayer);
            // 切视角同步左右对调双方聊天内容
            listener.onViewpointSwapped();
        }
    }

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
