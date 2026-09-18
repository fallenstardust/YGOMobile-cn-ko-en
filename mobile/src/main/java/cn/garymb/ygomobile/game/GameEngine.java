package cn.garymb.ygomobile.game;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.GameApplication;
import cn.garymb.ygomobile.audio.SoundManager;
import cn.garymb.ygomobile.core.IrrlichtBridge;
import cn.garymb.ygomobile.engine.LuaScriptEngine;
import cn.garymb.ygomobile.network.DuelClient;
import cn.garymb.ygomobile.network.LanDiscoveryManager;
import cn.garymb.ygomobile.network.YGOProtocol;
import cn.garymb.ygomobile.render.TextureLoader;
import cn.garymb.ygomobile.ui.dialogs.DuelLogDialog;
import ocgcore.DataManager;
import ocgcore.StringManager;
import ocgcore.data.Card;
import ocgcore.enums.CardLocation;
import ocgcore.enums.CardType;
import ocgcore.enums.GameMessage;

public class GameEngine implements DuelClient.ClientListener, GameMessageParser.MessageHandler {
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

    private GameState state = GameState.IDLE;
    private final DuelClient client;
    private final SoundManager soundManager;
    private final GameField field;
    private final LuaScriptEngine scriptEngine;
    private EngineListener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    /**
     * 连锁卡码序列（对齐 gframe dField.chains[].code）：MSG_CHAINING 依次入列，
     * 供 MSG_CHAIN_NEGATED/DISABLED 按 ct-1 取被无效的卡码；MSG_CHAIN_END 清空。
     * 仅在通讯（网络）线程访问，无需同步。
     */
    private final List<Integer> chainCodes = new ArrayList<>();

    /**
     * 消息串行闸门（对齐 duelclient.cpp ClientAnalyze + WaitFrameSignal 的串行语义）：
     * C++ 网络线程处理到召唤/发动/无效等动画消息时用 WaitFrameSignal 阻塞，直到动画播完才处理
     * 下一条消息（如"是否发动/是否连锁"的询问弹窗）。本工程所有消息经 DuelClient.handlePacket
     * 的 mainHandler.post 投递到主线程处理，无法阻塞网络线程（阻塞主线程会让 Choreographer 停摆、
     * 动画永远播不完 → 死锁），故改为「主线程消息队列 + 动画闸门」：动画消息派发后关闭闸门，
     * 暂缓派发后续消息，待 SpecEffectOverlay 特效队列排空（notifySpecEffectIdle）再继续。
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

    // === stHintMsg 提示栏（对齐 gframe：选择/等待类消息显示，下一条消息隐藏） ===

    /** 等待提示轮换间隔（game.cpp L1624-1633：waitFrame 每 90 帧≈1.5s 轮换 1390/1391/1392） */
    private static final long WAIT_HINT_TICK_MS = 1500;
    private static final int[] WAIT_HINT_SYS = {1390, 1391, 1392};
    private int waitHintIndex;
    private final Runnable waitHintTicker = new Runnable() {
        @Override
        public void run() {
            waitHintIndex = (waitHintIndex + 1) % WAIT_HINT_SYS.length;
            postDuelHint(sysString(WAIT_HINT_SYS[waitHintIndex], "等待行动中..."));
            mainHandler.postDelayed(this, WAIT_HINT_TICK_MS);
        }
    };

    private String sysString(int index, String def) {
        return DataManager.get().getStringManager().getSystemString(index, def);
    }

    /**
     * 写入 event_string（对齐 duelclient.cpp 各事件消息的 myswprintf(event_string, GetSysString(id), args...)）。
     * 无参数时直接取系统字符串；有参数时用 formatSystemString 填充其中的 %ls/%d。
     */
    private void setEventString(int index, String def, Object... args) {
        field.eventString = (args == null || args.length == 0)
                ? sysString(index, def)
                : DataManager.get().formatSystemString(index, def, args);
    }

    private void postDuelHint(String text) {
        mainHandler.post(() -> {
            if (listener != null) listener.onDuelHint(text);
        });
    }

    private void postDuelHintHide() {
        mainHandler.post(() -> {
            if (listener != null) listener.onDuelHintHide();
        });
    }

    /** 停止等待提示动画（对齐 duelclient.cpp L1309：waitFrame = -1） */
    private void stopWaitHint() {
        mainHandler.removeCallbacks(waitHintTicker);
    }

    private String playerName = "Player";
    private int duelStage = YGOProtocol.DUEL_STAGE_BEGIN;
    private int matchResult = 0;
    private int currentMatch = 0;
    private int maxMatch = 1;
    private boolean isHost = false;
    private boolean isBotMode = false;

    /** tag 模式本方是否已发起投降（等待队友回应）：防止对 STOC_TEAMMATE_SURRENDER 自我弹窗与重复发起 */
    private boolean tagSurrenderInitiated = false;
    private ReplayEngine replayEngine;
    private int gameMode = 0;
    private int gameRule = 0;
    private int gameLflist = 0;
    private int gameStartLp = 8000;
    private int gameStartHand = 5;
    private int gameDrawCount = 1;
    private int gameTimeLimit = 0;
    private int gameNoCheckDeck = 0;
    private int gameNoShuffleDeck = 0;

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

    public GameEngine(SoundManager soundManager) {
        this.client = new DuelClient();
        this.soundManager = soundManager;
        this.field = new GameField();
        this.scriptEngine = LuaScriptEngine.get();
        client.setListener(this);
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

    public static final int COMMAND_ACTIVATE = 0x0001;
    public static final int COMMAND_SUMMON   = 0x0002;
    public static final int COMMAND_SPSUMMON = 0x0004;
    public static final int COMMAND_MSET     = 0x0008;
    public static final int COMMAND_SSET     = 0x0010;
    public static final int COMMAND_REPOS    = 0x0020;
    public static final int COMMAND_ATTACK   = 0x0040;

    // === 召唤动画类型（onSummonAnimation 的 summonType 参数，对齐 duelclient.cpp showcard=5/7） ===
    public static final int SUMMON_NORMAL = 0;   // 通常召唤（MSG_SUMMONING，case 7 翻面）
    public static final int SUMMON_SPECIAL = 1;  // 特殊召唤（MSG_SPSUMMONING，case 5 放大淡入）
    public static final int SUMMON_FLIP = 2;     // 反转召唤（MSG_FLIPSUMMONING，case 7 翻面）

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
    private void resetFieldCommandHints() {
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

    // === Connection ===

    public void connectToServer(String host, int port, boolean createGame,
                                String roomName, String password,
                                int rule, int mode, int duelRule,
                                int startLp, int startHand, int drawCount, int timeLimit,
                                boolean noCheckDeck, boolean noShuffleDeck) {
        setState(GameState.CONNECTING);
        this.isHost = createGame;
        this.maxMatch = (mode == YGOProtocol.MODE_MATCH) ? 3 : 1;

        new Thread(() -> {
            boolean connected = client.connect(host, port);
            if (!connected) {
                setState(GameState.DISCONNECTED);
                return;
            }
            client.sendExternalAddress(host);
            client.sendPlayerInfo(playerName);
            client.sendJoinGame(0x1362, password);
        }, "GameConnect").start();
    }

    public void startLocalServer() {
        Log.i(TAG, "Starting local server...");
        setState(GameState.CONNECTING);
        this.isHost = true;
        this.maxMatch = 1;
        new Thread(() -> {
            boolean serverStarted = IrrlichtBridge.startGameServer(7911);
            if (!serverStarted) {
                Log.w(TAG, "NetServer may already be running");
            }
            LanDiscoveryManager.acquireHostMulticastLock();
            try { Thread.sleep(500); } catch (InterruptedException e) { /* ignore */ }
            boolean connected = client.connect("127.0.0.1", 7911);
            if (!connected) {
                setState(GameState.DISCONNECTED);
                return;
            }
            client.sendPlayerInfo(playerName);
            client.sendCreateGame(0, 0, 0, 5,
                    false, false,
                    8000, 5, 1, 0,
                    "Local Game", "");
        }, "LocalServer").start();
    }

    public void startLocalServerWithSettings(int lflist, int rule, int mode, int duelRule,
                                              boolean noCheckDeck, boolean noShuffleDeck,
                                              int startLp, int startHand, int drawCount, int timeLimit,
                                              String roomName, String password) {
        Log.i(TAG, "Starting local server with settings: " + roomName);
        setState(GameState.CONNECTING);
        this.isHost = true;
        this.maxMatch = (mode == YGOProtocol.MODE_MATCH) ? 3 : 1;
        new Thread(() -> {
            boolean serverStarted = IrrlichtBridge.startGameServer(7911);
            if (!serverStarted) {
                Log.w(TAG, "NetServer may already be running, trying to connect anyway");
            }
            LanDiscoveryManager.acquireHostMulticastLock();
            try { Thread.sleep(500); } catch (InterruptedException e) { /* ignore */ }
            boolean connected = client.connect("127.0.0.1", 7911);
            if (!connected) {
                setState(GameState.DISCONNECTED);
                return;
            }
            client.sendPlayerInfo(playerName);
            client.sendCreateGame(lflist, rule, mode, duelRule,
                    noCheckDeck, noShuffleDeck,
                    startLp, startHand, drawCount, timeLimit,
                    roomName, password);
        }, "LocalServer").start();
    }

    public void startSingleMode(String luaPath) {
        Log.i(TAG, "Starting single mode: " + luaPath);
        byte[] scriptData = scriptEngine.loadSingleScript(new File(luaPath).getName());
        if (scriptData == null || scriptData.length == 0) {
            Log.e(TAG, "Failed to load single mode script: " + luaPath);
            setState(GameState.IDLE);
            mainHandler.post(() -> {
                if (listener != null) listener.onHintMessage("无法加载残局脚本: " + new File(luaPath).getName());
            });
            return;
        }
        setState(GameState.CONNECTING);
        mainHandler.post(() -> {
            if (listener != null) listener.onHintMessage("正在加载残局...");
        });
        isBotMode = false;
        new Thread(() -> {
            boolean serverStarted = IrrlichtBridge.startGameServer(7911);
            if (!serverStarted) {
                Log.w(TAG, "NetServer may already be running, trying to connect anyway");
            }
            try { Thread.sleep(500); } catch (InterruptedException e) { /* ignore */ }
            boolean connected = client.connect("127.0.0.1", 7911);
            if (!connected) {
                setState(GameState.DISCONNECTED);
                return;
            }
            client.sendPlayerInfo(playerName);
            client.sendCreateGame(0, 0, 1, 5,
                    true, false,
                    8000, 5, 1, 0,
                    "Single Play", "");
        }, "SingleMode").start();
    }

    public void startBotDuel(String host, int port, String botCommand, String deckFile) {
        Log.i(TAG, "Starting bot duel via native WindBot: " + botCommand);
        isBotMode = true;
        setState(GameState.CONNECTING);

        new Thread(() -> {
            boolean serverStarted = IrrlichtBridge.startGameServer(port);
            if (!serverStarted) {
                Log.w(TAG, "NetServer may already be running, trying to connect anyway");
            }
            try { Thread.sleep(800); } catch (InterruptedException e) { /* ignore */ }

            boolean connected = client.connect(host, port);
            if (!connected) {
                setState(GameState.DISCONNECTED);
                mainHandler.post(() -> {
                    if (listener != null) listener.onHintMessage("无法连接到本地游戏服务器"); });
                return;
            }
            client.sendPlayerInfo(playerName);
            client.sendCreateGame(0, 0, 0, 5,
                    true, false,
                    8000, 5, 1, 0,
                    "Bot Duel", "");

            try { Thread.sleep(1500); } catch (InterruptedException e) { /* ignore */ }

            String windbotArgs = "WindBotHost:" + host + " Port:" + port
                    + " Name:WindBot"                    + (botCommand != null && !botCommand.isEmpty() ? " " + botCommand : "");
            Log.i(TAG, "Launching WindBot: " + windbotArgs);

            mainHandler.post(() -> {
                try {
                    Intent intent = new Intent();
                    intent.putExtra("args", windbotArgs);
                    intent.setAction("RUN_WINDBOT");
                    GameApplication.get().sendBroadcast(intent);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to launch WindBot", e);
                    if (listener != null) listener.onHintMessage("启动AI失败: " + e.getMessage());
                }
            });
        }, "BotDuel").start();
    }

    /**
     * 启动 WindBot 连接到指定主机并加入房间。
     * 用于人机对战：本地已通过 startLocalServerWithSettings 建立主机后，
     * 让 AI 作为第二名玩家加入，主机端在 player waiting 页面即可看到其加入。
     *
     * @param deckFile 为 P2(WindBot) 指定的卡组文件绝对路径；非空时通过 DeckFile 参数
     *                 覆盖 AI 自带卡组（对应 WindBot 内部 Deck.Load(DeckFile ?? Executor.Deck)）。
     */
    public void launchWindBot(String host, int port, String botCommand, String deckFile) {
        isBotMode = true;
        new Thread(() -> {
            try { Thread.sleep(1500); } catch (InterruptedException e) { /* ignore */ }
            // WindBot.RunAndroid 以空格拆分参数(保留单引号片段)，再以 '=' 拆 key/value。
            // 因此所有参数必须是 Key=Value 形式；含空格的值需用单引号包裹。
            StringBuilder sb = new StringBuilder();
            sb.append("Host=").append(host)
              .append(" Port=").append(port)
              .append(" Name=WindBot");
            if (botCommand != null && !botCommand.isEmpty()) {
                sb.append(' ').append(botCommand);
            }
            if (deckFile != null && !deckFile.isEmpty()) {
                // DeckFile 覆盖 AI 默认卡组，作为 P2 实际使用的卡组
                sb.append(" DeckFile='").append(deckFile).append('\'');
            }
            String windbotArgs = sb.toString();
            Log.i(TAG, "Launching WindBot: " + windbotArgs);
            mainHandler.post(() -> {
                try {
                    Intent intent = new android.content.Intent();
                    intent.putExtra("args", windbotArgs);
                    intent.setAction("RUN_WINDBOT");
                    GameApplication.get().sendBroadcast(intent);
                } catch (Exception e) {
                    Log.e(TAG, "Failed to launch WindBot", e);
                    if (listener != null) listener.onHintMessage("启动AI失败: " + e.getMessage());
                }
            });
        }, "WindBotLauncher").start();
    }

    public void loadReplay(String replayPath) {
        Log.i(TAG, "Loading replay: " + replayPath);
        if (replayEngine == null) {
            replayEngine = new ReplayEngine(field, soundManager);
        }
        setState(GameState.CONNECTING);
        replayEngine.loadAndPlay(replayPath);
        setState(GameState.DUELING);
    }

    public void pauseReplay() {
        if (replayEngine != null) replayEngine.pause();
    }

    public void resumeReplay() {
        if (replayEngine != null) replayEngine.resume();
    }

    public void stopReplay() {
        if (replayEngine != null) replayEngine.stop();
        setState(GameState.IDLE);
    }

    public void skipReplayAhead() {
        if (replayEngine != null) replayEngine.skipAhead();
    }

    public void disconnect() {
        client.disconnect();
        if (isHost) {
            try {
                IrrlichtBridge.stopGameServer();
                Log.i(TAG, "Local game server stopped");
            } catch (Exception e) {
                Log.w(TAG, "Failed to stop local game server", e);
            }
        }
        LanDiscoveryManager.releaseHostMulticastLock();
        setState(GameState.DISCONNECTED);
    }

    // === Lobby Actions ===

    public void sendReady() {
        client.sendReady();
    }

    public void sendNotReady() {
        client.sendNotReady();
    }

    public void sendStart() {
        client.sendStart();
    }

    public void sendKick(int pos) {
        client.sendKick(pos);
    }

    public void sendChat(String message) {
        client.sendChat(message);
    }

    public void sendSurrender() {
        if (isTagMode()) tagSurrenderInitiated = true;
        client.sendSurrender();
    }

    /** 是否 tag 双人模式（gameMode == MODE_TAG） */
    public boolean isTagMode() {
        return gameMode == YGOProtocol.MODE_TAG;
    }
    /** tag 模式：本方已发起投降、正在等待队友回应 */
    public boolean isSurrenderPending() {
        return tagSurrenderInitiated;
    }

    @Override
    public void onTeammateSurrender() {
        // tag_duel.cpp Surrender 会把 STOC_TEAMMATE_SURRENDER 同时发给发起方与队友；
        // 发起方只是知会（已在等待队友，不再弹窗），未发起的队友才弹出“是否同意投降”确认框
        if (tagSurrenderInitiated) return;
        mainHandler.post(() -> {
            if (listener != null) listener.onTeammateSurrenderRequest();
        });
    }


    public void sendToDuelist() {
        client.sendToDuelist();
    }

    public void sendToObserver() {
        client.sendToObserver();
    }

    // === Game Actions ===

    public void sendHandResult(int result) {
        client.sendHandResult(result);
    }

    public void sendTPResult(boolean chooseFirst) {
        client.sendTPResult(chooseFirst);
    }

    public void sendDeckUpdate(List<Integer> main, List<Integer> extra, List<Integer> side) {
        client.sendUpdateDeck(main, extra, side);
        // 预读本方卡组全部卡图（TextureLoader 后台解码 + LRU 缓存、内部去重），
        // 对局开始后我方抽卡/召唤直接带图，消除灰色占位闪现
        try {
            TextureLoader tl = TextureLoader.get();
            for (List<Integer> deck : new List[]{main, extra, side}) {
                if (deck == null) continue;
                for (Integer code : deck) {
                    if (code != null && code > 0) tl.getCardBitmap(code & 0xFFFFFFFFL);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    public void sendResponse(byte[] responseData) {
        client.sendResponse(responseData);
    }

    public void sendTimeConfirm() {
        client.sendTimeConfirm();
    }

    public int getSelfType() {
        return client.selfType;
    }

    public String getPlayerName() {
        return playerName;
    }

    // === State Management ===

    private void setState(GameState newState) {
        if (this.state == newState) return;
        this.state = newState;
        mainHandler.post(() -> {
            if (listener != null) listener.onStateChanged(newState);
        });
    }

    // === DuelClient.ClientListener ===

    @Override
    public void onConnected() {
        Log.i(TAG, "Connected to server");
    }

    @Override
    public void onDisconnected() {
        Log.i(TAG, "Disconnected from server");
        if (state != GameState.DUEL_END) {
            setState(GameState.DISCONNECTED);
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
        mainHandler.post(() -> {
            if (listener != null) listener.onChatReceived(playerType, message);
        });
        soundManager.playSoundEffect(SoundManager.SFX.CHAT);
    }

    @Override
    public void onPlayerEnter(String name, int pos) {
        Log.i(TAG, "Player entered: " + name + " at pos " + pos);
        if (pos < playerInfos.length) {
            playerInfos[pos].name = name;
        }
        // 座位 0-3 全量记录：tag 模式下 pos1/pos3 为双方 tag 同伴，聊天昵称需要
        if (pos >= 0 && pos < seatNames.length) {
            seatNames[pos] = name;
        }
        soundManager.playSoundEffect(SoundManager.SFX.PLAYER_ENTER);
        mainHandler.post(() -> {
            if (listener != null) listener.onPlayerEnter(name, pos);
        });
    }

    @Override
    public void onPlayerChange(int status) {
        Log.i(TAG, "Player change: " + String.format("0x%02X", status));
        mainHandler.post(() -> {
            if (listener != null) listener.onPlayerChange(status);
        });
    }

    @Override
    public void onWatchChange(int watchCount) {
        Log.i(TAG, "Watch count changed: " + watchCount);
        mainHandler.post(() -> {
            if (listener != null) listener.onWatchChange(watchCount);
        });
    }

    @Override
    public void onDeckCount(int deck0, int extra0, int side0, int deck1, int extra1, int side1) {
        // STOC_DECK_COUNT 在 STOC_DUEL_START 之后、MSG_START 之前下发双方卡组/额外/副卡组数量，
        // 供猜拳阶段在场地展示「卡组堆叠 / 额外卡组堆叠 / 除外区堆叠(显示副卡组数量)」。
        // 对齐 duelclient.cpp STOC_DECK_COUNT L584-598：C++ 用字面量 Initial(0)/Initial(1)（本地视角），
        // 故此处不经 localPlayer 映射；field 已由 onDuelStart 清空，这里只填充不重复 clear。
        field.initial(0, deck0, extra0, side0);
        field.initial(1, deck1, extra1, side1);
        mainHandler.post(() -> {
            if (listener != null) listener.onFieldChanged();
        });
    }

    @Override
    public void onDuelStart() {
        field.clear();
        duelStarted = true;
        inDuel = false;
        siding = false;
        tagSurrenderInitiated = false;
        duelStage = YGOProtocol.DUEL_STAGE_DUELING;
        setState(GameState.DUELING);
        soundManager.playBGM(SoundManager.BGM.DUEL);
        // 对局开场清理残留提示（对齐 game.cpp CloseGameWindow L2426 stHintMsg->setVisible(false)）
        stopWaitHint();
        postDuelHintHide();
    }

    @Override
    public void onDuelEnd() {
        duelStarted = false;
        inDuel = false;
        siding = false;
        tagSurrenderInitiated = false;
        duelStage = YGOProtocol.DUEL_STAGE_END;
        setState(GameState.DUEL_END);
        soundManager.stopBGM();
        stopWaitHint();
        postDuelHintHide();
    }

    @Override
    public void onReplay(byte[] data) {
        Log.i(TAG, "Replay data received from server, size=" + data.length);
        mainHandler.post(() -> {
            if (listener != null) listener.onReplayData(data);
        });
    }

    @Override
    public void onGameMsg(int msgType, ByteBuffer data) {
        data.order(ByteOrder.LITTLE_ENDIAN);
        // 入队后由闸门串行派发：动画消息会关闭闸门，暂缓后续消息（对齐 C++ WaitFrameSignal 阻塞语义）
        pendingMsgs.offer(() -> dispatchGameMsg(msgType, data));
        drainPendingMsgs();
    }

    /** 实际派发单条通讯消息（对齐 duelclient.cpp ClientAnalyze 主体） */
    private void dispatchGameMsg(int msgType, ByteBuffer data) {
        // 对齐 duelclient.cpp L1307-1311：除 MSG_WAITING/MSG_CARD_SELECTED 外，
        // 每条通讯消息开始先停止等待动画并隐藏 stHintMsg（提示栏随通讯推进实时显隐）
        if (msgType != GameMessage.Waiting.value() && msgType != GameMessage.CardSelected.value()) {
            stopWaitHint();
            postDuelHintHide();
        }
        try {
            GameMessageParser.parse(msgType, data, this);
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

    @Override
    public void onHandSelect() {
        setState(GameState.HAND_SELECT);
        mainHandler.post(() -> {
            if (listener != null) listener.onSelectRequired(0, null);
        });
    }

    @Override
    public void onTPSelect() {
        setState(GameState.TP_SELECT);
        mainHandler.post(() -> {
            if (listener != null) listener.onSelectRequired(1, null);
        });
    }

    @Override
    public void onHandResult(int res1, int res2) {
        Log.i(TAG, "Hand result: " + res1 + " vs " + res2);
        // 对齐 duelclient.cpp L528：STOC_HAND_RESULT 公布猜拳结果时隐藏提示
        postDuelHintHide();
        // STOC_HAND_RESULT 按服务器视角下发 player0/player1 手势，转换为本方视角
        int self = client.selfType;
        final int myHand = (self == 1) ? res2 : res1;
        final int oppHand = (self == 1) ? res1 : res2;
        mainHandler.post(() -> {
            if (listener != null) listener.onHandResult(myHand, oppHand);
        });
    }

    @Override
    public void onChangeSide() {
        duelStarted = false;
        inDuel = false;
        siding = true;
        duelStage = YGOProtocol.DUEL_STAGE_SIDING;
        setState(GameState.SIDING);
    }

    @Override
    public void onWaitingSide() {
        inDuel = false;
        Log.i(TAG, "Waiting for side change");
        // 对齐 duelclient.cpp L575-580：STOC_WAITING_SIDE 显示"等待换备卡"
        stopWaitHint();
        postDuelHint(sysString(1409, "等待对方换备卡..."));
    }

    @Override
    public void onTimeLimit(int player, int leftTime) {
        // 协议侧玩家索引统一转本地视角（0=我方），我方为后攻时倒计时也落入我方布局
        final int p = localPlayer(player & 1);
        if (field.dInfo.timeLimit <= 0) {
            field.dInfo.timeLimit = Math.max(gameTimeLimit, leftTime);
        }
        field.dInfo.timePlayer = p;
        field.dInfo.timeLeft[p] = leftTime;
        field.resetTimeTick();
        field.refreshTimeDisplay();
        mainHandler.post(() -> {
            if (listener != null) listener.onTimeLimitUpdate(p, leftTime);
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
                mainHandler.post(() -> {
                    if (listener != null) listener.onDeckError(errorType, cardCode);
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
        mainHandler.post(() -> {
            if (listener != null) listener.onHintMessage(finalMsg);
        });
    }

    @Override
    public void onTypeChange(int type) {
        Log.i(TAG, "Type changed to: " + type);
        mainHandler.post(() -> {
            if (listener != null) listener.onTypeChange(type);
        });
        setState(GameState.LOBBY);
    }

    @Override
    public void onJoinGame(int lflist, int rule, int mode, int duelRule,
                           int noCheckDeck, int noShuffleDeck,
                           int startLp, int startHand, int drawCount, int timeLimit) {
        playerInfos[0].startLp = startLp;
        playerInfos[1].startLp = startLp;
        playerInfos[0].lp = startLp;
        playerInfos[1].lp = startLp;
        this.maxMatch = (mode == YGOProtocol.MODE_MATCH) ? 3 : 1;
        this.gameMode = mode;
        this.gameRule = rule;
        this.gameLflist = lflist;
        this.gameStartLp = startLp;
        this.gameStartHand = startHand;
        this.gameDrawCount = drawCount;
        this.gameTimeLimit = timeLimit;
        this.gameNoCheckDeck = noCheckDeck;
        this.gameNoShuffleDeck = noShuffleDeck;
        field.dInfo.timeLimit = timeLimit;
        field.dInfo.startLp = startLp;
        field.dInfo.lp[0] = startLp;
        field.dInfo.lp[1] = startLp;
        mainHandler.post(() -> {
            if (listener != null) listener.onJoinGame(lflist, rule, mode, duelRule,
                    noCheckDeck, noShuffleDeck,
                    startLp, startHand, drawCount, timeLimit);
        });
        setState(GameState.LOBBY);
    }

    // === GameMessageParser.MessageHandler ===

    @Override
    public void onRetry() {
        Log.w(TAG, "Retry message received");
    }

    @Override
    public void onHint(int type, int player, int data) {
        String hintText = "";
        switch (type) {
            // HINT_EVENT（对齐 duelclient.cpp L1445-1447）：静默写入 event_string=GetDesc(data)，不弹提示
            case 1:
                field.eventString = DataManager.get().getDesc(data, "");
                return;
            case 2:
                hintText = "请选择";
                break;
            case 3:
                // HINT_SELECTMSG：保存下一条选择对话框标题的 sys 字符串索引，
                // 消费语义与 gframe select_hint 一致（duelclient.cpp L1458-1461）
                field.selectHint = data;
                return;
            // HINT_OPSELECTED（对齐 duelclient.cpp L1463-1472）：记录"已选择"日志
            case 4:
                DuelLogDialog.addOpSelectedLog(data);
                return;
            case 5:
                hintText = "当前连锁: " + data;
                break;
            // HINT_RACE（对齐 duelclient.cpp L1480-1490）：宣告种族选择记入日志
            case 6:
                DuelLogDialog.addSelectedRaceLog(data);
                return;
            // HINT_ATTRIB（对齐 duelclient.cpp L1491-1501）：宣告属性选择记入日志
            case 7:
                DuelLogDialog.addSelectedAttributeLog(data);
                return;
            // HINT_CODE（对齐 duelclient.cpp L1502-1511）：宣言卡名记入日志（sys1511「玩家宣言了」），携带卡代码供点击查看
            case 8:
                DuelLogDialog.addLog(DuelLogDialog.formatDeclared(DataManager.get().getName(data)), data);
                return;
            // HINT_NUMBER（对齐 duelclient.cpp L1512-1521）：宣告数字记入日志
            case 9:
                DuelLogDialog.addLog(DuelLogDialog.sysFormat(1512, "已选择数字：%d", data));
                soundManager.playSoundEffect(SoundManager.SFX.NEGATE);
                return;
            default:
                hintText = "Hint type=" + type + " data=" + data;
                break;
        }
        final String finalHint = hintText;
        mainHandler.post(() -> {
            if (listener != null) listener.onHintMessage(finalHint);
        });
    }

    @Override
    public void onWaiting() {
        Log.d(TAG, "Waiting...");
        // 对齐 duelclient.cpp L1610-1616 + game.cpp L1624-1633：waitFrame=0，显示"等待行动中..."并轮换
        waitHintIndex = 0;
        postDuelHint(sysString(1390, "等待行动中..."));
        mainHandler.removeCallbacks(waitHintTicker);
        mainHandler.postDelayed(waitHintTicker, WAIT_HINT_TICK_MS);
    }

    @Override
    public void onStart(int playerType, int duelRule, int lp0, int lp1, int deck0, int extra0, int deck1, int extra1) {
        field.clear();
        inDuel = true;
        siding = false;
        field.dInfo.duelRule = duelRule;
        duelIsFirst = (playerType & 1) == 0;
        int p0 = localPlayer(0);
        int p1 = localPlayer(1);
        playerInfos[p0].lp = lp0;
        playerInfos[p1].lp = lp1;
        playerInfos[p0].startLp = lp0;
        playerInfos[p1].startLp = lp1;
        field.players[p0].lp = lp0;
        field.players[p1].lp = lp1;
        field.dInfo.startLp = Math.max(lp0, lp1);
        field.dInfo.lp[p0] = lp0;
        field.dInfo.lp[p1] = lp1;
        // ClientField::Initial：为双方卡组/额外创建全部 ClientCard（背面朝下、带堆叠高度）
        field.initial(p0, deck0, extra0, 0);
        field.initial(p1, deck1, extra1, 0);
        setState(GameState.DUELING);
        mainHandler.post(() -> {
            if (listener != null) {
                listener.onFieldChanged();
                listener.onPlayerInfoUpdated(0);
                listener.onPlayerInfoUpdated(1);
            }
        });
    }

    @Override
    public void onWin(int player, int reason) {
        if (player == 2) {
            soundManager.playBGM(SoundManager.BGM.ALL);
        } else if (isSelfSide(player)) {
            soundManager.playBGM(SoundManager.BGM.WIN);
        } else {
            soundManager.playBGM(SoundManager.BGM.LOSE);
        }
        currentMatch++;
        mainHandler.post(() -> {
            if (listener != null) listener.onDuelResult(player, reason);
        });
    }

    @Override
    public void onUpdateData(int player, int location, ByteBuffer data) {
        parseUpdateData(localPlayer(player), location, data);
        mainHandler.post(() -> {
            if (listener != null) listener.onFieldChanged();
        });
    }

    @Override
    public void onUpdateCard(int player, int location, int sequence, ByteBuffer data) {
        parseUpdateCard(localPlayer(player), location, sequence, data);
        mainHandler.post(() -> {
            if (listener != null) listener.onFieldChanged();
        });
    }

    @Override
    public void onRequestDeck(int player) {
        setState(GameState.DECK_SELECT);
    }

    @Override
    public void onSelectBattleCmd(ByteBuffer data) {
        parseBattleCmd(data);
        mainHandler.post(() -> {
            if (listener != null) listener.onSelectRequired(10, null);
        });
    }

    @Override
    public void onSelectIdleCmd(ByteBuffer data) {
        parseIdleCmd(data);
        mainHandler.post(() -> {
            if (listener != null) listener.onSelectRequired(11, null);
        });
    }

    @Override
    public void onSelectEffectYn(ByteBuffer data) {
        mainHandler.post(() -> {
            if (listener != null) listener.onSelectRequired(12, data);
        });
    }

    @Override
    public void onSelectYesNo(ByteBuffer data) {
        mainHandler.post(() -> {
            if (listener != null) listener.onSelectRequired(13, data);
        });
    }

    @Override
    public void onSelectOption(ByteBuffer data) {
        mainHandler.post(() -> {
            if (listener != null) listener.onSelectRequired(14, data);
        });
    }

    @Override
    public void onSelectCard(ByteBuffer data) {
        // 对齐 duelclient.cpp L1964-1974：非 panelmode 时 stHintMsg 显示"提示(min-max)"
        String hint = selectRangeHint(data, 560, "选择卡片");
        if (hint != null) postDuelHint(hint);
        mainHandler.post(() -> {
            if (listener != null) listener.onSelectRequired(15, data);
        });
    }

    @Override
    public void onSelectChain(ByteBuffer data) {
        // 对齐 duelclient.cpp L2158-2163：存在"发动并作为连锁"项(EDESC_OPERATION=1)时用 556，否则 550
        boolean contiExist = false;
        try {
            ByteBuffer dup = data.duplicate();
            dup.order(ByteOrder.LITTLE_ENDIAN);
            dup.get(); // selecting_player
            int count = dup.get() & 0xFF;
            dup.get(); // specount
            dup.getInt(); // hint0
            dup.getInt(); // hint1
            for (int i = 0; i < count && dup.remaining() >= 14; i++) {
                int flag = dup.get() & 0xFF;
                dup.get(); // forced
                dup.getInt(); // code
                dup.position(dup.position() + 4); // c l s ss
                dup.getInt(); // desc
                if ((flag & 0x1) != 0) contiExist = true;
            }
        } catch (Exception ignored) {
        }
        postDuelHint(sysString(contiExist ? 556 : 550,
                contiExist ? "选择发动效果并作为连锁" : "选择发动效果"));
        mainHandler.post(() -> {
            if (listener != null) listener.onSelectRequired(16, data);
        });
    }

    @Override
    public void onSelectPlace(int player, int count, int fieldMask) {
        clearCommandFlags();
        selectFieldPlayer = player;
        selectFieldCount = count;
        selectFieldMask = ~fieldMask;
        // fieldMask 相对选择方：低 16 位 = 选择方自己的半场。
        // 只有选择方与我不同半场时才交换高低 16 位，保证低 16 位始终是我方半场（下半区）。
        // 用 localPlayer(与卡牌渲染同一套映射)判断"选择方是否为对方"，player&1 取边以兼容 tag(0/2 先攻,1/3 后攻)。
        if (localPlayer(player & 1) == 1) {
            selectFieldMask = (selectFieldMask >>> 16) | (selectFieldMask << 16);
        }
        // 对齐 duelclient.cpp L2199-2208：MSG_SELECT_PLACE 提示 sys569「请选择[%ls]的位置」（select_hint 此时是卡号）/ sys560
        if (field.selectHint > 0) {
            postDuelHint(DataManager.get().formatSystemString(569, "请选择[%s]的位置",
                    DataManager.get().getName(field.selectHint)));
        } else {
            postDuelHint(sysString(560, "请选择放置位置"));
        }
        field.selectHint = 0;
        mainHandler.post(() -> {
            if (listener != null) listener.onSelectRequired(18, null);
        });
    }

    @Override
    public void onSelectPosition(int player, int code, int positions) {
        positions &= 0x0F;
        // duelclient.cpp L2275-2278：仅一种表示形式可选时直接以该形式应答，不弹窗
        if (positions == 0x1 || positions == 0x2 || positions == 0x4 || positions == 0x8) {
            ByteBuffer resp = ByteBuffer.allocate(4);
            resp.order(ByteOrder.LITTLE_ENDIAN);
            resp.putInt(positions);
            client.sendResponse(resp.array());
            return;
        }
        // 打包 code(4) + positions(4) 传给 UI 层：用于显示卡图与按位掩码显示形式按钮
        ByteBuffer data = ByteBuffer.allocate(8);
        data.order(ByteOrder.LITTLE_ENDIAN);
        data.putInt(code);
        data.putInt(positions);
        data.flip();
        mainHandler.post(() -> {
            if (listener != null) listener.onSelectRequired(19, data);
        });
    }

    @Override
    public void onSelectTribute(ByteBuffer data) {
        // 对齐 duelclient.cpp L2330-2335：stHintMsg 显示"提示(min-max)"（hint 优先 selectHint，默认 531）
        String hint = selectRangeHint(data, 531, "解放选择");
        if (hint != null) postDuelHint(hint);
        mainHandler.post(() -> {
            if (listener != null) listener.onSelectRequired(20, data);
        });
    }

    @Override
    public void onSortChain(ByteBuffer data) {
        mainHandler.post(() -> {
            if (listener != null) listener.onSelectRequired(21, data);
        });
    }

    @Override
    public void onSelectCounter(ByteBuffer data) {
        // 对齐 duelclient.cpp L2362-2365：stHintMsg 显示 GetSysString(204)（移除 N 个指示物）
        String hint = counterHint(data);
        if (hint != null) postDuelHint(hint);
        mainHandler.post(() -> {
            if (listener != null) listener.onSelectRequired(22, data);
        });
    }

    @Override
    public void onSelectSum(ByteBuffer data) {
        // 对齐 client_field.cpp L1090-1115 ShowSelectSum：display_hint = GetDesc(select_hint) 或 GetSysString(560)
        int hint = field.selectHint;
        postDuelHint(hint > 0 ? DataManager.get().getDesc(hint, "选择卡片") : sysString(560, "选择卡片"));
        mainHandler.post(() -> {
            if (listener != null) listener.onSelectRequired(23, data);
        });
    }

    @Override
    public void onSelectDisfield(int player, int count, int fieldMask) {
        clearCommandFlags();
        selectFieldPlayer = player;
        selectFieldCount = count;
        selectFieldMask = ~fieldMask;
        if (localPlayer(player & 1) == 1) {
            selectFieldMask = (selectFieldMask >>> 16) | (selectFieldMask << 16);
        }
        // 对齐 duelclient.cpp L2205-2210：MSG_SELECT_DISFIELD 提示 GetDesc(select_hint ?: 570)
        int hint = field.selectHint > 0 ? field.selectHint : 570;
        postDuelHint(DataManager.get().getDesc(hint, "请选择要禁用的区域"));
        mainHandler.post(() -> {
            if (listener != null) listener.onSelectRequired(24, null);
        });
    }

    @Override
    public void onSortCard(ByteBuffer data) {
        mainHandler.post(() -> {
            if (listener != null) listener.onSelectRequired(25, data);
        });
    }

    @Override
    public void onSelectUnselectCard(ByteBuffer data) {
        // 对齐 duelclient.cpp L2053-2065：stHintMsg 显示"提示(min-max)"
        String hint = selectRangeHint(data, 560, "选择卡片");
        if (hint != null) postDuelHint(hint);
        mainHandler.post(() -> {
            if (listener != null) listener.onSelectRequired(26, data);
        });
    }

    // === 提示栏文字生成（对齐 gframe stHintMsg 各调用点的格式化） ===

    /**
     * 生成选择类消息提示（duelclient.cpp L1965/L2056/L2331："%ls(%d-%d)"）。
     * 用 duplicate 解析不推进原缓冲（UI 侧仍需读取同一数据）；
     * selectHint 只读取不消费（消费仍由 UI 侧对话框标题承担）
     */
    private String selectRangeHint(ByteBuffer data, int defIndex, String defText) {
        try {
            ByteBuffer dup = data.duplicate();
            dup.order(ByteOrder.LITTLE_ENDIAN);
            dup.get(); // selecting_player
            dup.get(); // cancelable（UNSELECT 为 finishable 位，同位置）
            int min = dup.get() & 0xFF;
            int max = dup.get() & 0xFF;
            int hint = field.selectHint;
            String title = hint > 0 ? DataManager.get().getDesc(hint, defText) : sysString(defIndex, defText);
            return title + "(" + min + "-" + max + ")";
        } catch (Exception e) {
            return null;
        }
    }

    /** 解析 player(1) counter_type(2) counter_count(2)，对齐 duelclient.cpp L2362：GetSysString(204) */
    private String counterHint(ByteBuffer data) {
        try {
            ByteBuffer dup = data.duplicate();
            dup.order(ByteOrder.LITTLE_ENDIAN);
            dup.get(); // selecting_player
            int counterType = dup.getShort() & 0xFFFF;
            int count = dup.getShort() & 0xFFFF;
            DataManager dm = DataManager.get();
            return dm.formatSystemString(204, "请取除%d个[%s]", count, dm.getCounterName(counterType));
        } catch (Exception e) {
            return null;
        }
    }

    /** 对齐 dataManager.GetName（duelclient.cpp L2201：GetName(select_hint)） */
    private String getCardDisplayName(int code) {
        if (code <= 0) return "?";
        Card card = DataManager.get().getCardManager().getCard(code);
        return card != null && card.Name != null ? card.Name : "?";
    }

    @Override
    public void onConfirmDecktop(int player, int count, ByteBuffer data) {
        Log.d(TAG, "ConfirmDecktop: player=" + player + " count=" + count);
        // 对齐 duelclient.cpp MSG_CONFIRM_DECKTOP L2443-2480：翻开卡组上方N张卡记入日志
        DuelLogDialog.addLog(DuelLogDialog.sysFormat(207, "翻开卡组上方%d张卡：", count));

        for (int i = 0; i < count && data.remaining() >= 7; i++) {
            int code = data.getInt() & 0x7fffffff;
            data.position(data.position() + 3);
            DuelLogDialog.addLog("*[" + DataManager.get().getName(code) + "]", code);
        }
    }

    @Override
    // Deleted:public void onConfirmCards(int player, int count, ByteBuffer data) {
    public void onConfirmCards(int player, int skipPanel, int count, ByteBuffer data) {
        // 对齐 duelclient.cpp MSG_CONFIRM_CARDS L2518-2545：确认N张卡记入日志
        DuelLogDialog.addLog(DuelLogDialog.sysFormat(208, "确认%d张卡：", count));

        int start = data.position();
        for (int i = 0; i < count && data.remaining() >= 7; i++) {
            int code = data.getInt() & 0x7fffffff;
            data.position(data.position() + 3);
            DuelLogDialog.addLog("*[" + DataManager.get().getName(code) + "]", code);
        }
        // 日志读取后回退缓冲位置，供下方确认面板复用条目数据
        data.position(start);
        // 前置 1 字节 skipPanel 转发 UI（duelclient.cpp L2607：skip_panel 时不弹面板）
        ByteBuffer packed = ByteBuffer.allocate(1 + data.remaining()).order(ByteOrder.LITTLE_ENDIAN);
        packed.put((byte) skipPanel);
        packed.put(data);
        packed.flip();
        mainHandler.post(() -> {
            if (listener != null) listener.onSelectRequired(27, packed);
        });
    }

    @Override
    public void onShuffleDeck(int player) {
        soundManager.playSoundEffect(SoundManager.SFX.SHUFFLE);
        mainHandler.post(() -> {
            if (listener != null) listener.onFieldChanged();
        });
    }

    @Override
    public void onShuffleHand(int player) {
        // duelclient.cpp MSG_SHUFFLE_HAND L2689-2692：逐张 SetCode 后 desc_hints.clear()
        final int p = localPlayer(player & 1);
        for (GameField.ClientCard c : field.players[p].hand) {
            if (c != null) c.clearDescHints();
        }
        soundManager.playSoundEffect(SoundManager.SFX.SHUFFLE);
        mainHandler.post(() -> {
            if (listener != null) listener.onFieldChanged();
        });
    }

    @Override
    public void onRefreshDeck(int player) {
        mainHandler.post(() -> {
            if (listener != null) listener.onFieldChanged();
        });
    }

    @Override
    public void onSwapGraveDeck(int player) {
        mainHandler.post(() -> {
            if (listener != null) listener.onFieldChanged();
        });
    }

    @Override
    public void onShuffleSetCard(int player, int count, ByteBuffer data) {
        mainHandler.post(() -> {
            if (listener != null) listener.onFieldChanged();
        });
    }

    @Override
    public void onReverseDeck(int player) {
        mainHandler.post(() -> {
            if (listener != null) listener.onFieldChanged();
        });
    }

    @Override
    public void onDeckTop(int player, int code) {
        mainHandler.post(() -> {
            if (listener != null) listener.onFieldChanged();
        });
    }

    @Override
    public void onNewTurn(int player) {
        field.currentPlayer = localPlayer(player);
        field.turnCount++;
        soundManager.playSoundEffect(SoundManager.SFX.NEXT_TURN);
        final int localCurrent = field.currentPlayer;
        mainHandler.post(() -> {
            if (listener != null) {
                listener.onFieldChanged();
                listener.onTurnStarted(localCurrent);
            }
        });
    }

    @Override
    public void onNewPhase(int phase) {
        field.currentPhase = phase;
        soundManager.playSoundEffect(SoundManager.SFX.PHASE);
        // 同步派发（不再 mainHandler.post）：使阶段文字（case 101）在本次消息派发期间即入队
        // SpecEffectOverlay（startCard→startLoop 立即置 running），统一动画屏障才能检测到「阶段文字在播」并关闭闸门，
        // 令当前阶段内的卡片移动、阶段按钮文字变化、可选择外框、连锁/conti_act 动画等后续消息排在阶段文字之后，
        // 对齐 duelclient.cpp MSG_NEW_PHASE 的 showcard=101 + WaitFrameSignal(40)（overlay 阶段文字恰 40 帧）。
        if (listener != null) listener.onPhaseChanged(phase);
    }

    @Override
    public void onMove(int code, int oldCtrl, int oldLoc, int oldSeq, int oldPos,
                       int newCtrl, int newLoc, int newSeq, int position, int reason) {
        oldCtrl = localPlayer(oldCtrl);
        newCtrl = localPlayer(newCtrl);
        boolean oldOverlay = (oldLoc & 0x80) != 0;
        boolean newOverlay = (newLoc & 0x80) != 0;

        if (newOverlay && !oldOverlay) {
            // 作为超量素材叠放到超量怪兽下方（duelclient.cpp L3055-3095）
            GameField.ClientCard card = field.getCard(oldCtrl, oldLoc & 0x7f, oldSeq);
            if (card == null) card = new GameField.ClientCard();
            if (code != 0) card.code = code;
            // 对齐 duelclient.cpp MSG_MOVE 素材入 overlay 分支（L3055-3095）：C++ 此分支绝不改写
            // pcard->position，素材保留其在场上的表侧表示，叠放后正面朝上。旧实现用协议 cp 覆盖 position
            //（该字节对 overlay 移动常为 0），使表侧素材被 isFaceUp() 判为里侧 → 「原地变背面 / 卡背」；
            // 且素材本应在怪兽格下方叠放，而非留在原格或被甩走。仅当卡片为兜底新建（position 仍为默认 0）
            // 时显式置表侧，避免渲染成卡背。
            if (card.position == 0) card.position = GameField.POS_FACEUP;
            if (field.attachOverlayMaterial(card, oldCtrl, oldLoc & 0x7f, oldSeq, newCtrl, newSeq)) {
                field.moveCardAnimated(card, 10);
            }
        } else if (oldOverlay && !newOverlay) {
            // 超量素材离场（duelclient.cpp L3096-3124）：oldSeq=超量怪兽格、oldPos=素材序号
            GameField.ClientCard card = field.detachOverlayMaterial(
                    oldCtrl, oldSeq, oldPos, newCtrl, newLoc & 0x7f, newSeq, position);
            if (card != null) {
                if (code != 0) card.code = code;
                field.moveCardAnimated(card, 10);
            }
        } else if (oldOverlay) {
            // 素材在两只超量怪兽间转移（duelclient.cpp L3125+）
            GameField.ClientCard src = field.getCard(oldCtrl, CardLocation.MonsterZone.value(), oldSeq);
            GameField.ClientCard dst = field.getCard(newCtrl, CardLocation.MonsterZone.value(), newSeq);
            if (src != null && dst != null && oldPos >= 0 && oldPos < src.overlayed.size()) {
                GameField.ClientCard m = src.overlayed.remove(oldPos);
                for (int i = 0; i < src.overlayed.size(); i++) {
                    GameField.ClientCard s = src.overlayed.get(i);
                    if (s == null) continue;
                    s.sequence = i;
                    field.moveCardAnimated(s, 2);
                }
                if (m != null) {
                    dst.overlayed.add(m);
                    m.overlayTarget = dst;
                    m.controler = newCtrl;
                    m.sequence = dst.overlayed.size() - 1;
                    field.moveCardAnimated(m, 10);
                }
            }
        } else {
            GameField.ClientCard card = field.getCard(oldCtrl, oldLoc, oldSeq);
            if (card == null) card = new GameField.ClientCard();
            card.code = code;
            card.position = position;
            field.removeCard(oldCtrl, oldLoc, oldSeq);
            field.addCard(newCtrl, newLoc, newSeq, card);
            field.moveCardAnimated(card, 8);
        }

        // 手卡增删后重排双方手卡（数量变化 → 间距变化）
        if ((oldLoc & 0x7f) == CardLocation.Hand.value() || (newLoc & 0x7f) == CardLocation.Hand.value()) {
            field.updateHandLayout(0, 10);
            field.updateHandLayout(1, 10);
        }
        if (newLoc == CardLocation.Removed.value()) {
            soundManager.playSoundEffect(SoundManager.SFX.BANISHED);
        } else if (newLoc == CardLocation.Grave.value()) {
            soundManager.playSoundEffect(SoundManager.SFX.DESTROYED);
        } else if (newLoc == CardLocation.MonsterZone.value() && oldLoc == 0) {
            soundManager.playSoundEffect(SoundManager.SFX.SUMMON);
        }

        mainHandler.post(() -> {
            if (listener != null) listener.onFieldChanged();
        });
    }

    @Override
    public void onPosChange(int code, int ctrl, int loc, int seq, int oldPos, int newPos) {
        ctrl = localPlayer(ctrl);
        GameField.ClientCard card = field.getCard(ctrl, loc, seq);
        if (card != null) {
            card.position = newPos;
        }
        if ((oldPos & 0xA) != 0 && (newPos & 0x5) != 0) {
            soundManager.playSoundEffect(SoundManager.SFX.FLIP);
        }
        setEventString(1600, "卡片改变了表示形式");
        mainHandler.post(() -> {
            if (listener != null) listener.onFieldChanged();
        });
    }

    @Override
    public void onSet(int code, int ctrl, int loc, int seq) {
        ctrl = localPlayer(ctrl);
        // 对齐 gframe duelclient.cpp MSG_SET（L3179-3189）：仅播音效 + 事件串，绝不新建/替换卡片。
        // 卡片已由先到的 MSG_MOVE(onMove) 放入并定位到格子；旧实现在此 new 了一张 cur*=0 的卡，
        // 而 addCard 对 MZONE/SZONE 只做 list.set 不调 setCardPos，新卡落在世界原点、与底板共面被
        // 深度吞掉，于是里侧守备怪兽 / 盖放魔陷卡都看不到卡背矩形。
        GameField.ClientCard card = field.getCard(ctrl, loc, seq);
        if (card != null && card.curX == 0f && card.curY == 0f && card.curZ == 0f) {
            // 兜底：极少数回放/重载路径卡已在列表却从未定位，补一次定位（不覆盖 code/position）
            field.moveCardAnimated(card, 1);
        }
        soundManager.playSoundEffect(SoundManager.SFX.SET);
        setEventString(1601, "盖放了卡片");
        mainHandler.post(() -> {
            if (listener != null) listener.onFieldChanged();
        });
    }

    @Override
    public void onSwap(int c1ctrl, int c1loc, int c1seq, int c2ctrl, int c2loc, int c2seq) {
        GameField.ClientCard c1 = field.getCard(c1ctrl, c1loc, c1seq);
        GameField.ClientCard c2 = field.getCard(c2ctrl, c2loc, c2seq);
        field.addCard(c1ctrl, c1loc, c1seq, c2);
        field.addCard(c2ctrl, c2loc, c2seq, c1);
        setEventString(1602, "卡的控制权改变了");
        mainHandler.post(() -> {
            if (listener != null) listener.onFieldChanged();
        });
    }

    @Override
    public void onFieldDisabled(int disabledMask) {
        mainHandler.post(() -> {
            if (listener != null) listener.onFieldChanged();
        });
    }

    @Override
    public void onSummoning(int code, int ctrl, int loc, int seq) {
        soundManager.playSoundEffect(SoundManager.SFX.SUMMON);
        // duelclient.cpp MSG_SUMMONING L3250/3252-3258：event_string=sys1603「[%ls]召唤中」+ showcard=7（翻面进入）
        setEventString(1603, "[%s]召唤中", DataManager.get().getName(code));
        postSummonAnimation(code, SUMMON_NORMAL);
    }

    @Override
    public void onSummoned() {
        setEventString(1604, "怪兽召唤成功");
        mainHandler.post(() -> {
            if (listener != null) listener.onFieldChanged();
        });
    }

    @Override
    public void onSpSummoning(int code, int ctrl, int loc, int seq) {
        soundManager.playSoundEffect(SoundManager.SFX.SPECIAL_SUMMON);
        // duelclient.cpp MSG_SPSUMMONING L3286-3290：event_string=sys1605「[%ls]特殊召唤中」+ if(code) showcard=5（放大淡入）
        setEventString(1605, "[%s]特殊召唤中", DataManager.get().getName(code));
        if (code != 0) postSummonAnimation(code, SUMMON_SPECIAL);
    }

    @Override
    public void onSpSummoned() {
        setEventString(1606, "怪兽特殊召唤成功");
        mainHandler.post(() -> {
            if (listener != null) listener.onFieldChanged();
        });
    }

    @Override
    public void onFlipSummoning(int code, int ctrl, int loc, int seq) {
        soundManager.playSoundEffect(SoundManager.SFX.FLIP);
        // duelclient.cpp MSG_FLIPSUMMONING L3314-3320：event_string=sys1607「[%ls]反转召唤中」+ showcard=7（翻面进入）
        setEventString(1607, "[%s]反转召唤中", DataManager.get().getName(code));
        postSummonAnimation(code, SUMMON_FLIP);
    }

    @Override
    public void onFlipSummoned() {
        setEventString(1608, "怪兽反转召唤成功");
        mainHandler.post(() -> {
            if (listener != null) listener.onFieldChanged();
        });
    }

    /** 召唤类卡片居中动画（对齐 duelclient.cpp showcard=5/7）：同步派发即入队特效，
     *  闸门由 drainPendingMsgs 的统一动画屏障检测关闭，无需在此显式关闭 */
    private void postSummonAnimation(int code, int summonType) {
        if (listener == null) return;
        listener.onSummonAnimation(code, summonType);
    }

    @Override
    public void onChaining(int code, int pcc, int pcl, int pcs, int subs, int cc, int cl, int cs, int desc) {
        soundManager.playSoundEffect(SoundManager.SFX.ACTIVATE);
        // 记录连锁卡码（对齐 gframe MSG_CHAINED 将 current_chain.code 压入 dField.chains），
        // 供 MSG_CHAIN_NEGATED/DISABLED 按 ct-1 取被无效的卡码
        chainCodes.add(code);
        // 协议侧 controler 转本地索引，保证连锁高亮落在正确的半场
        final int localCc = localPlayer(cc & 1);
        // duelclient.cpp MSG_CHAINING L3345/L3366-3374：四参 GetCard 取发动卡并填充 current_chain，
        // 其后 MSG_BECOME_TARGET 写入 current_chain.target，MSG_CHAINED 再压入 chains；
        // 供 ClientField::ShowCardInfoInList（event_handler.cpp L2937-2947）生成连锁状态标签
        field.currentChain = new GameField.ChainInfo();
        field.currentChain.chainCard = field.getCard(localPlayer(pcc & 1), pcl, pcs, subs);
        field.currentChain.code = code;
        field.currentChain.desc = desc;
        field.currentChain.controler = localCc;
        field.currentChain.location = cl;
        field.currentChain.sequence = cs;
        if (listener != null) {
            // 发动动画入队即由统一动画屏障关闭闸门，播完再处理后续消息（对齐 C++ MSG_CHAINING 的 WaitFrameSignal(30)）
            listener.onChainAnimation(code, localCc, cl, cs);
        }
    }

    @Override
    public void onChained(int chainCount) {
        // 对齐 duelclient.cpp MSG_CHAINED L3406：event_string=sys1609「[%ls]的效果发动」，卡名取 current_chain.code
        //（即最近一次 onChaining 压入 chainCodes 的卡码）
        int curCode = chainCodes.isEmpty() ? 0 : chainCodes.get(chainCodes.size() - 1);
        setEventString(1609, "[%s]的效果发动", DataManager.get().getName(curCode));
        // duelclient.cpp MSG_CHAINED L3408：chains.push_back(current_chain)。
        // C++ 为值拷贝，Java 用引用语义（同一对象入列），使效果处理期追加的目标同样能体现在状态标签上
        if (field.currentChain != null && !field.chains.contains(field.currentChain)) {
            field.chains.add(field.currentChain);
        }
    }

    @Override
    public void onChainSolving(int chainCount) {

    }

    @Override
    public void onChainSolved(int chainCount) {

    }

    @Override
    public void onChainEnd() {
        // 对齐 duelclient.cpp MSG_CHAIN_END L3442：chains.clear()
        chainCodes.clear();
        field.chains.clear();
        field.currentChain = new GameField.ChainInfo();
        mainHandler.post(() -> {
            if (listener != null) listener.onFieldChanged();
        });
    }

    @Override
    public void onChainNegated(int chainCount) {
        soundManager.playSoundEffect(SoundManager.SFX.NEGATE);
        postNegatedAnimation(chainCount);
    }

    @Override
    public void onChainDisabled(int chainCount) {
        soundManager.playSoundEffect(SoundManager.SFX.NEGATE);
        postNegatedAnimation(chainCount);
    }

    /**
     * 效果无效卡片居中动画（对齐 duelclient.cpp MSG_CHAIN_NEGATED/DISABLED L3450-3452：
     * showcardcode = chains[ct-1].code, showcarddif=0, showcard=3）。
     * ct 为 1 基连锁序号；取不到卡码时不播放。
     */
    private void postNegatedAnimation(int chainCount) {
        final int code = (chainCount >= 1 && chainCount <= chainCodes.size())
                ? chainCodes.get(chainCount - 1) : 0;
        if (code == 0 || listener == null) return;
        // 无效动画入队即由统一动画屏障关闭闸门，播完再处理后续消息
        // （对齐 C++ MSG_CHAIN_NEGATED/DISABLED 的 WaitFrameSignal(30)）
        listener.onNegatedAnimation(code);
    }

    @Override
    public void onDraw(int player, int count, int[] codes) {
        // duelclient.cpp MSG_DRAW L3519-3547
        final int p = localPlayer(player);
        int deckLoc = CardLocation.Deck.value();
        int handLoc = CardLocation.Hand.value();
        // 1) 给被抽的卡组顶设卡码
        int top = field.getCardCount(p, deckLoc) - 1;
        for (int i = 0; i < count; i++) {
            GameField.ClientCard pcard = field.getCard(p, deckLoc, top - i);
            if (pcard != null && (!field.deckReversed || codes[i] != 0)) {
                pcard.setCode(codes[i] & 0x7fffffff);
            }
        }
        // 2) 逐张从卡组顶移除 → 加入手卡 → 全部手卡重新布局（MoveCard 10 帧）
        for (int i = 0; i < count; i++) {
            int t = field.getCardCount(p, deckLoc) - 1;
            GameField.ClientCard pcard = field.removeCard(p, deckLoc, t);
            if (pcard == null) {
                pcard = new GameField.ClientCard();
                pcard.owner = p;
                pcard.controler = p;
                if (i < codes.length) pcard.setCode(codes[i] & 0x7fffffff);
            }
            field.addCard(p, handLoc, 0, pcard);
            for (GameField.ClientCard hc : field.players[p].hand) {
                if (hc != null) field.moveCardAnimated(hc, 10);
            }
        }
        soundManager.playSoundEffect(SoundManager.SFX.DRAW);
        setEventString(p == 0 ? 1611 : 1612, p == 0 ? "我方抽了%d张卡" : "对方抽了%d张卡", count);
        mainHandler.post(() -> {
            if (listener != null) {
                listener.onFieldChanged();
                listener.onPlayerInfoUpdated(p);
            }
        });
    }

    @Override
    public void onDamage(int player, int amount) {
        // 协议侧玩家 → 本地视角索引（0=我方）：我方为后攻时伤害/回复正确落到对应半场
        // 与顶部信息栏左半边（我方）布局保持一致
        int p = localPlayer(player & 1);
        int fin = Math.max(0, field.players[p].lp - amount);
        field.players[p].lp = fin;
        field.startLpChange(p, fin, 0xFFFF0000, "-" + amount, true);
        soundManager.playSoundEffect(SoundManager.SFX.DAMAGE);
        setEventString(p == 0 ? 1613 : 1614, p == 0 ? "我方受到%d伤害" : "对方受到%d伤害", amount);
        mainHandler.post(() -> {
            if (listener != null) listener.onPlayerInfoUpdated(p);
        });
    }

    @Override
    public void onRecover(int player, int amount) {
        int p = localPlayer(player & 1);
        int fin = field.players[p].lp + amount;
        field.players[p].lp = fin;
        field.startLpChange(p, fin, 0xFF00FF00, "+" + amount, true);
        soundManager.playSoundEffect(SoundManager.SFX.RECOVER);
        setEventString(p == 0 ? 1615 : 1616, p == 0 ? "我方回复%d基本分" : "对方回复%d基本分", amount);
        mainHandler.post(() -> {
            if (listener != null) listener.onPlayerInfoUpdated(p);
        });
    }

    @Override
    public void onEquip(int eqCode, int eqCtrl, int eqLoc, int eqSeq,
                        int tCtrl, int tLoc, int tSeq) {
        GameField.ClientCard equipCard = field.getCard(eqCtrl, eqLoc, eqSeq);
        GameField.ClientCard target = field.getCard(tCtrl, tLoc, tSeq);
        if (equipCard != null && target != null) {
            equipCard.equipCard = target;
        }
        soundManager.playSoundEffect(SoundManager.SFX.EQUIP);
        mainHandler.post(() -> {
            if (listener != null) listener.onFieldChanged();
        });
    }

    @Override
    public void onLpUpdate(int player, int lp) {
        int p = localPlayer(player & 1);
        field.players[p].lp = lp;
        field.startLpChange(p, lp, 0, null, false);
        mainHandler.post(() -> {
            if (listener != null) listener.onPlayerInfoUpdated(p);
        });
    }

    @Override
    public void onUnequip(int ctrl, int loc, int seq) {
        GameField.ClientCard card = field.getCard(ctrl, loc, seq);
        if (card != null) {
            card.equipCard = null;
        }
        mainHandler.post(() -> {
            if (listener != null) listener.onFieldChanged();
        });
    }

    @Override
    public void onCardTarget(int c1ctrl, int c1loc, int c1seq, int c2ctrl, int c2loc, int c2seq) {
        GameField.ClientCard c1 = field.getCard(c1ctrl, c1loc, c1seq);
        GameField.ClientCard c2 = field.getCard(c2ctrl, c2loc, c2seq);
        if (c1 != null && c2 != null) {
            c1.targetCards.add(c2);
        }
        mainHandler.post(() -> {
            if (listener != null) listener.onFieldChanged();
        });
    }

    @Override
    public void onCancelTarget(int c1ctrl, int c1loc, int c1seq, int c2ctrl, int c2loc, int c2seq) {
        GameField.ClientCard c1 = field.getCard(c1ctrl, c1loc, c1seq);
        GameField.ClientCard c2 = field.getCard(c2ctrl, c2loc, c2seq);
        if (c1 != null && c2 != null) {
            c1.targetCards.remove(c2);
        }
        mainHandler.post(() -> {
            if (listener != null) listener.onFieldChanged();
        });
    }

    @Override
    public void onPayLpCost(int player, int cost) {
        int p = localPlayer(player & 1);
        int fin = Math.max(0, field.players[p].lp - cost);
        field.players[p].lp = fin;
        // 对齐 duelclient.cpp MSG_PAY_LPCOST L3755-3763：SFX.DAMAGE + lpccolor=0xff0000ff（蓝）、lpcstring="-cost"、
        // WaitFrameSignal(30)+lpframe=10+WaitFrameSignal(11)——支付基本分同样先浮字再扣减；startLpChange(showText=true)
        // 同步置 lpPending，统一动画屏障据此暂缓后续消息（如随后场上怪兽被破坏离场的 MSG_MOVE）
        soundManager.playSoundEffect(SoundManager.SFX.DAMAGE);
        field.startLpChange(p, fin, 0xFF0000FF, "-" + cost, true);
        mainHandler.post(() -> {
            if (listener != null) listener.onPlayerInfoUpdated(p);
        });
    }

    @Override
    public void onAddCounter(int type, int ctrl, int loc, int seq, int count) {
        GameField.ClientCard card = field.getCard(ctrl, loc, seq);
        if (card != null) {
            card.counters.put(type, count);
        }
        soundManager.playSoundEffect(SoundManager.SFX.COUNTER_ADD);
        mainHandler.post(() -> {
            if (listener != null) listener.onFieldChanged();
        });
    }

    @Override
    public void onRemoveCounter(int type, int ctrl, int loc, int seq, int count) {
        GameField.ClientCard card = field.getCard(ctrl, loc, seq);
        if (card != null) {
            card.counters.remove(type);
        }
        soundManager.playSoundEffect(SoundManager.SFX.COUNTER_REMOVE);
        mainHandler.post(() -> {
            if (listener != null) listener.onFieldChanged();
        });
    }

    @Override
    public void onAttack(int aCtrl, int aLoc, int aSeq, int dCtrl, int dLoc, int dSeq) {
        soundManager.playSoundEffect(SoundManager.SFX.ATTACK);
        // 对齐 duelclient.cpp MSG_ATTACK L3830-3847：有攻击对象(dLoc!=0)写 sys1619「[%ls]攻击[%ls]」，
        // 否则写 sys1620「[%ls]直接攻击」；卡名经 GetName(code)，协议侧 controler 需转本地索引
        GameField.ClientCard atkCard = field.getCard(localPlayer(aCtrl & 1), aLoc, aSeq);
        String atkName = atkCard != null ? DataManager.get().getName(atkCard.code) : "";
        if (dLoc != 0) {
            GameField.ClientCard defCard = field.getCard(localPlayer(dCtrl & 1), dLoc, dSeq);
            String defName = defCard != null ? DataManager.get().getName(defCard.code) : "";
            setEventString(1619, "[%s]攻击[%s]", atkName, defName);
        } else {
            setEventString(1620, "[%s]直接攻击", atkName);
        }
        mainHandler.post(() -> {
            if (listener != null) listener.onFieldChanged();
        });
    }

    @Override
    public void onBattle(int atkAtk, boolean atkPos, int defAtk, boolean defPos) {
        Log.d(TAG, "Battle: " + atkAtk + " vs " + defAtk);
    }

    @Override
    public void onAttackDisabled() {
        // 对齐 duelclient.cpp MSG_ATTACK_DISABLED L3910：sys1621「攻击被无效」（无占位符，C++ 传入的卡名参数被忽略）
        setEventString(1621, "攻击被无效");
        mainHandler.post(() -> {
            if (listener != null) listener.onFieldChanged();
        });
    }

    @Override
    public void onDamageStepStart() {
        Log.d(TAG, "Damage step start");
    }

    @Override
    public void onDamageStepEnd() {
        mainHandler.post(() -> {
            if (listener != null) listener.onFieldChanged();
        });
    }

    @Override
    public void onMissedEffect(int code, int ctrl, int loc, int seq, int effectId) {
        Log.w(TAG, "Missed effect: code=" + code + " effectId=" + effectId);
        // 对齐 duelclient.cpp MSG_MISSED_EFFECT L3919-3924：sys1622「[%ls]错过时点」用 GetName(code) 填充，携带卡代码
        DuelLogDialog.addLog(DuelLogDialog.sysFormat(1622, "[%s]错过时点", DataManager.get().getName(code)), code);
    }

    @Override
    public void onTossCoin(int player, int count, ByteBuffer results) {
        soundManager.playSoundEffect(SoundManager.SFX.COIN);
        // 对齐 duelclient.cpp MSG_TOSS_COIN L3926-3946：掷硬币结果记入日志
        StringManager sm = DataManager.get().getStringManager();
        StringBuilder sb = new StringBuilder(sm.getSystemString(1623, "掷硬币："));
        for (int i = 0; i < count && results.remaining() >= 1; i++) {
            int res = results.get() & 0xFF;
            sb.append('[')
                    .append(res != 0 ? sm.getSystemString(60, "正面") : sm.getSystemString(61, "反面"))
                    .append(']');
        }
        DuelLogDialog.addLog(sb.toString());
    }

    @Override
    public void onTossDice(int player, int count, ByteBuffer results) {
        soundManager.playSoundEffect(SoundManager.SFX.DICE);
        // 对齐 duelclient.cpp MSG_TOSS_DICE L3948-3968：掷骰子结果记入日志
        StringBuilder sb = new StringBuilder(
                DataManager.get().getStringManager().getSystemString(1624, "掷骰子："));
        for (int i = 0; i < count && results.remaining() >= 1; i++) {
            sb.append('[').append(results.get() & 0xFF).append(']');
        }
        DuelLogDialog.addLog(sb.toString());
    }

    @Override
    public void onAnnounceRace(int player, int count, int availableRaces) {
        // duelclient.cpp MSG_ANNOUNCE_RACE L3996-4014：player 已消费，打包 count+available 转发
        ByteBuffer buf = ByteBuffer.allocate(5).order(ByteOrder.LITTLE_ENDIAN);
        buf.put((byte) count);
        buf.putInt(availableRaces);
        buf.flip();
        mainHandler.post(() -> {
            if (listener != null) listener.onSelectRequired(140, buf);
        });
    }

    @Override
    public void onAnnounceAttrib(int player, int count, int availableAttribs) {
        // duelclient.cpp MSG_ANNOUNCE_ATTRIB L4015-4033：同上，打包 count+available 转发
        ByteBuffer buf = ByteBuffer.allocate(5).order(ByteOrder.LITTLE_ENDIAN);
        buf.put((byte) count);
        buf.putInt(availableAttribs);
        buf.flip();
        mainHandler.post(() -> {
            if (listener != null) listener.onSelectRequired(141, buf);
        });
    }

    @Override
    public void onAnnounceCard(int player, ByteBuffer data) {
        mainHandler.post(() -> {
            if (listener != null) listener.onSelectRequired(142, data);
        });
    }

    @Override
    public void onAnnounceNumber(int player, ByteBuffer data) {
        mainHandler.post(() -> {
            if (listener != null) listener.onSelectRequired(143, data);
        });
    }

    @Override
    public void onCardHint(int player, int location, int sequence, int hintType, int value) {
        // duelclient.cpp MSG_CARD_HINT L4094-4109：CHINT_DESC_ADD/REMOVE 维护卡片 desc_hints
        field.applyCardHint(localPlayer(player & 1), location, sequence, hintType, value);
    }

    @Override
    public void onBecomeTarget(int count, ByteBuffer data) {
        if (data == null || count <= 0) return;
        // duelclient.cpp MSG_BECOME_TARGET L3493-3500：每条 4 字节，第 4 字节（subseq）读入即弃，
        // 三参 GetCard 后写入 current_chain.target
        for (int i = 0; i < count && data.remaining() >= 4; i++) {
            int ctrl = data.get() & 0xFF;
            int loc = data.get() & 0xFF;
            int seq = data.get() & 0xFF;
            data.get();
            field.addChainTarget(localPlayer(ctrl & 1), loc, seq);
        }
        mainHandler.post(() -> {
            if (listener != null) listener.onFieldChanged();
        });
    }

    @Override
    public void onTagSwap(int player) {
        mainHandler.post(() -> {
            if (listener != null) listener.onFieldChanged();
        });
    }

    @Override
    public void onReloadField() {
        mainHandler.post(() -> {
            if (listener != null) listener.onFieldChanged();
        });
    }

    @Override
    public void onAiName(String name) {
        Log.i(TAG, "AI name: " + name);
    }

    @Override
    public void onShowHint(String hint) {
        mainHandler.post(() -> {
            if (listener != null) listener.onHintMessage(hint);
        });
    }

    @Override
    public void onMatchKill(int code) {
        matchResult = 3;
    }

    @Override
    public void onCustomMsg(String msg) {
        mainHandler.post(() -> {
            if (listener != null) listener.onHintMessage(msg);
        });
    }

    @Override
    public void onDuelWinner(int player, int reason) {
        onWin(player, reason);
    }

    // === Data parsing helpers ===

    /**
     * MSG_UPDATE_DATA（client_field.cpp UpdateFieldCard真值格式）：
     * 无 count 字段，按列表条目遍历；每条先读 int32 len（含自身 4 字节），
     * len>8 才有 query 数据（query 内首个 int32 是 flag），随后跳到 len-4 处。
     * 固定槽位列表（怪兽区/魔法区）空位也有len=4 条目；动态列表只发实际卡。
     */
    private void parseUpdateData(int player, int location, ByteBuffer data) {
        List<GameField.ClientCard> list = field.players[player].getLocationList(location);
        if (list == null) return;
        boolean fixedSlots = (location == 0x04 || location == 0x08);
        for (int i = 0; i < list.size(); i++) {
            GameField.ClientCard card = list.get(i);
            if (card == null && !fixedSlots) continue;
            if (data.remaining() < 4) break;
            int len = data.getInt();
            int next = data.position() + (len - 4);
            if (next < data.position() || next > data.limit()) break;
            if (len > 8 && card != null) {
                ByteBuffer sub = data.slice().order(ByteOrder.LITTLE_ENDIAN);
                sub.limit(Math.min(sub.limit(), len - 4));
                card.updateQuery(sub);
            }
            data.position(next);
        }
    }

    /**
     * MSG_UPDATE_CARD（client_field.cpp UpdateCard 真值格式）：
     * int32 len 前缀，len>8 时才解析 query（对现有卡对象更新，绝不整卡替换）
     */
    private void parseUpdateCard(int player, int location, int sequence, ByteBuffer data) {
        if (data.remaining() < 4) return;
        int len = data.getInt();
        if (len <= 8) return;
        GameField.ClientCard card = field.getCard(player, location, sequence);
        if (card == null) {
            card = new GameField.ClientCard();
            card.controler = player;
            card.location = location;
            card.sequence = sequence;
            field.addCard(player, location, sequence, card);
        }
        ByteBuffer sub = data.slice().order(ByteOrder.LITTLE_ENDIAN);
        sub.limit(Math.min(sub.limit(), len - 4));
        card.updateQuery(sub);
    }

    private void parseBattleCmd(ByteBuffer data) {
        clearCommandFlags();
        resetFieldCommandHints();
        int selectingPlayer = data.get() & 0xFF;
        int count = data.get() & 0xFF;
        for (int i = 0; i < count && data.remaining() >= 9; i++) {
            int code = data.getInt();
            int con = data.get() & 0xFF;
            int loc = data.get() & 0xFF;
            int seq = data.get() & 0xFF;
            int desc = data.getInt();
            int flag = 0;
            if ((code & 0x80000000) != 0) {
                flag = 1;
                code &= 0x7fffffff;
            }
            int lpl = localPlayer(con & 1);
            GameField.ClientCard card = field.getCard(lpl, loc, seq);
            if (card != null) {
                // 对齐 duelclient.cpp L1704-1716：EDESC_OPERATION → conti_act（回合结束待结算），
                // 否则 COMMAND_ACTIVATE 并按所在区域置墓地/除外/额外发动提示
                if (flag != 0) {
                    card.chain_code = code;
                    field.contiCards.add(card);
                    field.contiAct = true;
                } else {
                    card.cmdFlag |= COMMAND_ACTIVATE;
                    if (card.location == 0x10) {
                        field.graveAct[lpl] = true;
                    } else if (card.location == 0x20) {
                        field.removeAct[lpl] = true;
                    } else if (card.location == 0x40) {
                        field.extraAct[lpl] = true;
                    }
                }
                activatableCards.add(new CmdCardInfo(card, code, desc, flag, i));
            }
        }
        count = data.get() & 0xFF;
        for (int i = 0; i < count && data.remaining() >= 8; i++) {
            int code = data.getInt();
            int con = data.get() & 0xFF;
            int loc = data.get() & 0xFF;
            int seq = data.get() & 0xFF;
            int diratt = data.get() & 0xFF;
            GameField.ClientCard card = field.getCard(localPlayer(con & 1), loc, seq);
            if (card != null) {
                card.cmdFlag |= COMMAND_ATTACK;
                attackableCards.add(new CmdCardInfo(card, code, 0, 0, i));
            }
        }
        showM2 = data.remaining() >= 1 && (data.get() & 0xFF) != 0;
        showEP = data.remaining() >= 1 && (data.get() & 0xFF) != 0;
    }

    private void parseIdleCmd(ByteBuffer data) {
        clearCommandFlags();
        resetFieldCommandHints();
        int selectingPlayer = data.get() & 0xFF;
        int count;

        // 第1段 summonable_cards（duelclient.cpp L1750-1760）：仅置 COMMAND_SUMMON 并入 summonableCards，
        // 应答编码 i<<16（event_handler.cpp BUTTON_CMD_SUMMON L595-605）。
        // 修复：此前误按特召段解析（置 COMMAND_SPSUMMON + 入 spsummonableCards），
        // 导致手卡可通常召唤的怪兽只显示「特殊召唤」且应答 op=1 越出 spsummon_list 而无效果。
        count = data.get() & 0xFF;
        for (int i = 0; i < count && data.remaining() >= 7; i++) {
            int code = data.getInt();
            int con = data.get() & 0xFF;
            int loc = data.get() & 0xFF;
            int seq = data.get() & 0xFF;
            GameField.ClientCard card = field.getCard(localPlayer(con & 1), loc, seq);
            if (card != null) {
                card.cmdFlag |= COMMAND_SUMMON;
                summonableCards.add(new CmdCardInfo(card, code, 0, 0, i));
            }
        }

        // 第2段 spsummonable_cards（duelclient.cpp L1761-1785）：COMMAND_SPSUMMON + 按区域置发动提示，
        // 卡组项需补明卡码（对应 pcard->SetCode(code)），应答编码 (i<<16)+1。
        count = data.get() & 0xFF;
        for (int i = 0; i < count && data.remaining() >= 7; i++) {
            int code = data.getInt();
            int con = data.get() & 0xFF;
            int loc = data.get() & 0xFF;
            int seq = data.get() & 0xFF;
            int lpl = localPlayer(con & 1);
            GameField.ClientCard card = field.getCard(lpl, loc, seq);
            if (card != null) {
                card.cmdFlag |= COMMAND_SPSUMMON;
                if (card.location == CardLocation.Deck.value()) {
                    card.code = code;
                    field.deckAct[lpl] = true;
                } else if (card.location == CardLocation.Grave.value()) {
                    field.graveAct[lpl] = true;
                } else if (card.location == CardLocation.Removed.value()) {
                    field.removeAct[lpl] = true;
                } else if (card.location == CardLocation.Extra.value()) {
                    field.extraAct[lpl] = true;
                } else {
                    // duelclient.cpp L1780-1784：灵摆区（duel_rule>=4 为 seq0，否则 seq6）且未被装备占用
                    int leftSeq = field.dInfo.duelRule >= 4 ? 0 : 6;
                    if (card.location == CardLocation.SpellZone.value() && card.sequence == leftSeq
                            && (card.type & CardType.Pendulum.getId()) != 0 && card.equipTarget == null) {
                        field.pzoneAct[lpl] = true;
                    }
                }
                spsummonableCards.add(new CmdCardInfo(card, code, 0, 0, i));
            }
        }

        count = data.get() & 0xFF;
        for (int i = 0; i < count && data.remaining() >= 7; i++) {
            int code = data.getInt();
            int con = data.get() & 0xFF;
            int loc = data.get() & 0xFF;
            int seq = data.get() & 0xFF;
            GameField.ClientCard card = field.getCard(localPlayer(con & 1), loc, seq);
            if (card != null) {
                card.cmdFlag |= COMMAND_REPOS;
                reposableCards.add(new CmdCardInfo(card, code, 0, 0, i));
            }
        }

        count = data.get() & 0xFF;
        for (int i = 0; i < count && data.remaining() >= 7; i++) {
            int code = data.getInt();
            int con = data.get() & 0xFF;
            int loc = data.get() & 0xFF;
            int seq = data.get() & 0xFF;
            GameField.ClientCard card = field.getCard(localPlayer(con & 1), loc, seq);
            if (card != null) {
                card.cmdFlag |= COMMAND_MSET;
                msetableCards.add(new CmdCardInfo(card, code, 0, 0, i));
            }
        }

        count = data.get() & 0xFF;
        for (int i = 0; i < count && data.remaining() >= 7; i++) {
            int code = data.getInt();
            int con = data.get() & 0xFF;
            int loc = data.get() & 0xFF;
            int seq = data.get() & 0xFF;
            GameField.ClientCard card = field.getCard(localPlayer(con & 1), loc, seq);
            if (card != null) {
                card.cmdFlag |= COMMAND_SSET;
                ssetableCards.add(new CmdCardInfo(card, code, 0, 0, i));
            }
        }

        count = data.get() & 0xFF;
        for (int i = 0; i < count && data.remaining() >= 11; i++) {
            int code = data.getInt();
            int con = data.get() & 0xFF;
            int loc = data.get() & 0xFF;
            int seq = data.get() & 0xFF;
            int desc = data.getInt();
            int flag = 0;
            if ((code & 0x80000000) != 0) {
                flag = 1;
                code &= 0x7fffffff;
            }
            int lpl = localPlayer(con & 1);
            GameField.ClientCard card = field.getCard(lpl, loc, seq);
            if (card != null) {
                // 对齐 duelclient.cpp L1837-1849：EDESC_OPERATION → conti_act，否则按区域置提示
                if (flag != 0) {
                    card.chain_code = code;
                    field.contiCards.add(card);
                    field.contiAct = true;
                } else {
                    card.cmdFlag |= COMMAND_ACTIVATE;
                    if (card.location == 0x10) {
                        field.graveAct[lpl] = true;
                    } else if (card.location == 0x20) {
                        field.removeAct[lpl] = true;
                    } else if (card.location == 0x40) {
                        field.extraAct[lpl] = true;
                    }
                }
                activatableCards.add(new CmdCardInfo(card, code, desc, flag, i));
            }
        }

        showBP = data.remaining() >= 1 && (data.get() & 0xFF) != 0;
        showEP = data.remaining() >= 1 && (data.get() & 0xFF) != 0;
        showShuffle = data.remaining() >= 1 && (data.get() & 0xFF) != 0;
    }

    /** Game::LocalPlayer：dInfo.isFirst ? player : 1 - player */
    private boolean duelIsFirst = true;

    /** 对局状态标志，对齐 gframe dInfo.isStarted / dInfo.isInDuel / Game.is_siding，
     *  供聊天 ChatLocalPlayer 的分边与昵称判定使用。转换点（对齐 duelclient.cpp）：
     *  STOC_DUEL_START(L858)→started；MSG_START(L1627)→inDuel；
     *  STOC_CHANGE_SIDE(L541/545)→siding 且 started=false；STOC_WAITING_SIDE(L576)/STOC_DUEL_END(L1008)→复位 */
    private boolean duelStarted = false;
    private boolean inDuel = false;
    private boolean siding = false;

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
