package cn.garymb.ygomobile.game;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.GameApplication;
import cn.garymb.ygomobile.audio.SoundManager;
import cn.garymb.ygomobile.core.IrrlichtBridge;
import cn.garymb.ygomobile.engine.LuaScriptEngine;
import cn.garymb.ygomobile.network.DuelClient;
import cn.garymb.ygomobile.network.LanDiscoveryManager;
import cn.garymb.ygomobile.network.YGOProtocol;
import ocgcore.enums.CardLocation;

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

        void onChatReceived(String player, String message);

        void onSelectRequired(int selectType, ByteBuffer data);

        void onDuelResult(int winner, int reason);

        void onHintMessage(String hint);

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
    }

    private GameState state = GameState.IDLE;
    private final DuelClient client;
    private final SoundManager soundManager;
    private final GameField field;
    private final LuaScriptEngine scriptEngine;
    private EngineListener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private String playerName = "Player";
    private int duelStage = YGOProtocol.DUEL_STAGE_BEGIN;
    private int matchResult = 0;
    private int currentMatch = 0;
    private int maxMatch = 1;
    private boolean isHost = false;
    private boolean isBotMode = false;
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
        client.sendSurrender();
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
    public void onChatMessage(String player, String message) {
        mainHandler.post(() -> {
            if (listener != null) listener.onChatReceived(player, message);
        });
        soundManager.playSoundEffect(SoundManager.SFX.CHAT);
    }

    @Override
    public void onPlayerEnter(String name, int pos) {
        Log.i(TAG, "Player entered: " + name + " at pos " + pos);
        if (pos < playerInfos.length) {
            playerInfos[pos].name = name;
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
    public void onDuelStart() {
        field.clear();
        duelStage = YGOProtocol.DUEL_STAGE_DUELING;
        setState(GameState.DUELING);
        soundManager.playBGM(SoundManager.BGM.DUEL);
    }

    @Override
    public void onDuelEnd() {
        duelStage = YGOProtocol.DUEL_STAGE_END;
        setState(GameState.DUEL_END);
        soundManager.stopBGM();
    }

    @Override
    public void onGameMsg(int msgType, ByteBuffer data) {
        data.order(ByteOrder.LITTLE_ENDIAN);
        try {
            GameMessageParser.parse(msgType, data, this);
        } catch (BufferUnderflowException e) {
            Log.e(TAG, "Failed to parse game message type=" + msgType + ", remaining=" + data.remaining(), e);
        }
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
    }

    @Override
    public void onChangeSide() {
        duelStage = YGOProtocol.DUEL_STAGE_SIDING;
        setState(GameState.SIDING);
    }

    @Override
    public void onWaitingSide() {
        Log.i(TAG, "Waiting for side change");
    }

    @Override
    public void onTimeLimit(int player, int leftTime) {
        if (field.dInfo.timeLimit <= 0) {
            field.dInfo.timeLimit = Math.max(gameTimeLimit, leftTime);
        }
        field.dInfo.timePlayer = player;
        field.dInfo.timeLeft[player] = leftTime;
        field.resetTimeTick();
        field.refreshTimeDisplay();
        mainHandler.post(() -> {
            if (listener != null) listener.onTimeLimitUpdate(player, leftTime);
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
            case 1:
                hintText = "卡片效果发动";
                break;
            case 2:
                hintText = "请选择";
                break;
            case 3:
                hintText = "等待对方操作";
                break;
            case 5:
                hintText = "当前连锁: " + data;
                break;
            case 6:
                mainHandler.post(() -> {
                    if (listener != null) listener.onHintMessage("提示ID: " + data);
                });
                return;
            case 9:
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
    }

    @Override
    public void onStart(int playerType, int duelRule, int lp0, int lp1,
                        int deck0, int extra0, int deck1, int extra1) {
        field.clear();
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
        } else if (player == client.selfType) {
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
        mainHandler.post(() -> {
            if (listener != null) listener.onSelectRequired(15, data);
        });
    }

    @Override
    public void onSelectChain(ByteBuffer data) {
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
        // 用 localPlayer(与卡牌渲染同一套映射)判断“选择方是否为对方”，player&1 取边以兼容 tag(0/2 先攻,1/3 后攻)。
        if (localPlayer(player & 1) == 1) {
            selectFieldMask = (selectFieldMask >>> 16) | (selectFieldMask << 16);
        }
        mainHandler.post(() -> {
            if (listener != null) listener.onSelectRequired(18, null);
        });
    }

    @Override
    public void onSelectPosition(int player, int code, int positions) {
        mainHandler.post(() -> {
            if (listener != null) listener.onSelectRequired(19, null);
        });
    }

    @Override
    public void onSelectTribute(ByteBuffer data) {
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
        mainHandler.post(() -> {
            if (listener != null) listener.onSelectRequired(22, data);
        });
    }

    @Override
    public void onSelectSum(ByteBuffer data) {
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
        mainHandler.post(() -> {
            if (listener != null) listener.onSelectRequired(26, data);
        });
    }

    @Override
    public void onConfirmDecktop(int player, int count, ByteBuffer data) {
        Log.d(TAG, "ConfirmDecktop: player=" + player + " count=" + count);
    }

    @Override
    public void onConfirmCards(int player, int count, ByteBuffer data) {
        mainHandler.post(() -> {
            if (listener != null) listener.onSelectRequired(27, data);
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
        mainHandler.post(() -> {
            if (listener != null) listener.onFieldChanged();
        });
    }

    @Override
    public void onNewPhase(int phase) {
        field.currentPhase = phase;
        soundManager.playSoundEffect(SoundManager.SFX.PHASE);
        mainHandler.post(() -> {
            if (listener != null) listener.onPhaseChanged(phase);
        });
    }

    @Override
    public void onMove(int code, int oldCtrl, int oldLoc, int oldSeq,
                       int newCtrl, int newLoc, int newSeq, int position, int reason) {
        oldCtrl = localPlayer(oldCtrl);
        newCtrl = localPlayer(newCtrl);
        GameField.ClientCard card = field.getCard(oldCtrl, oldLoc, oldSeq);
        if (card == null) {
            card = new GameField.ClientCard();
        }
        card.code = code;
        card.position = position;
        field.removeCard(oldCtrl, oldLoc, oldSeq);
        field.addCard(newCtrl, newLoc, newSeq, card);
        field.moveCardAnimated(card, 8);
        // 手卡增删后重排双方手卡（数量变化 → 间距变化）
        if (oldLoc == CardLocation.Hand.value() || newLoc == CardLocation.Hand.value()) {
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
        mainHandler.post(() -> {
            if (listener != null) listener.onFieldChanged();
        });
    }

    @Override
    public void onSet(int code, int ctrl, int loc, int seq) {
        ctrl = localPlayer(ctrl);
        GameField.ClientCard card = new GameField.ClientCard();
        card.code = code;
        card.position = 0x2;
        field.addCard(ctrl, loc, seq, card);
        soundManager.playSoundEffect(SoundManager.SFX.SET);
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
    }

    @Override
    public void onSummoned() {
        mainHandler.post(() -> {
            if (listener != null) listener.onFieldChanged();
        });
    }

    @Override
    public void onSpSummoning(int code, int ctrl, int loc, int seq) {
        soundManager.playSoundEffect(SoundManager.SFX.SPECIAL_SUMMON);
    }

    @Override
    public void onSpSummoned() {
        mainHandler.post(() -> {
            if (listener != null) listener.onFieldChanged();
        });
    }

    @Override
    public void onFlipSummoning(int code, int ctrl, int loc, int seq) {
        soundManager.playSoundEffect(SoundManager.SFX.FLIP);
    }

    @Override
    public void onFlipSummoned() {
        mainHandler.post(() -> {
            if (listener != null) listener.onFieldChanged();
        });
    }

    @Override
    public void onChaining(int code, int pcc, int pcl, int pcs, int subs, int cc, int cl, int cs, int desc) {
        soundManager.playSoundEffect(SoundManager.SFX.ACTIVATE);
        mainHandler.post(() -> {
            if (listener != null) listener.onChainAnimation(code, cc, cl, cs);
        });
    }

    @Override
    public void onChained(int code) {
        mainHandler.post(() -> {
            if (listener != null) listener.onFieldChanged();
        });
    }

    @Override
    public void onChainSolving(int chainCount) {
        mainHandler.post(() -> {
            if (listener != null) listener.onFieldChanged();
        });
    }

    @Override
    public void onChainSolved(int chainCount) {
        mainHandler.post(() -> {
            if (listener != null) listener.onFieldChanged();
        });
    }

    @Override
    public void onChainEnd() {
        mainHandler.post(() -> {
            if (listener != null) listener.onFieldChanged();
        });
    }

    @Override
    public void onChainNegated(int chainCount) {
        soundManager.playSoundEffect(SoundManager.SFX.NEGATE);
    }

    @Override
    public void onChainDisabled(int chainCount) {
        soundManager.playSoundEffect(SoundManager.SFX.NEGATE);
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
        mainHandler.post(() -> {
            if (listener != null) {
                listener.onFieldChanged();
                listener.onPlayerInfoUpdated(p);
            }
        });
    }

    @Override
    public void onDamage(int player, int amount) {
        int fin = Math.max(0, field.players[player].lp - amount);
        field.players[player].lp = fin;
        field.startLpChange(player, fin, 0xFFFF0000, "-" + amount, true);
        soundManager.playSoundEffect(SoundManager.SFX.DAMAGE);
        mainHandler.post(() -> {
            if (listener != null) listener.onPlayerInfoUpdated(player);
        });
    }

    @Override
    public void onRecover(int player, int amount) {
        int fin = field.players[player].lp + amount;
        field.players[player].lp = fin;
        field.startLpChange(player, fin, 0xFF00FF00, "+" + amount, true);
        soundManager.playSoundEffect(SoundManager.SFX.RECOVER);
        mainHandler.post(() -> {
            if (listener != null) listener.onPlayerInfoUpdated(player);
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
        field.players[player].lp = lp;
        field.startLpChange(player, lp, 0, null, false);
        mainHandler.post(() -> {
            if (listener != null) listener.onPlayerInfoUpdated(player);
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
        int fin = Math.max(0, field.players[player].lp - cost);
        field.players[player].lp = fin;
        field.startLpChange(player, fin, 0, null, false);
        mainHandler.post(() -> {
            if (listener != null) listener.onPlayerInfoUpdated(player);
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
    }

    @Override
    public void onTossCoin(int player, int count, ByteBuffer results) {
        soundManager.playSoundEffect(SoundManager.SFX.COIN);
    }

    @Override
    public void onTossDice(int player, int count, ByteBuffer results) {
        soundManager.playSoundEffect(SoundManager.SFX.DICE);
    }

    @Override
    public void onAnnounceRace(int player, int count, int availableRaces) {
        mainHandler.post(() -> {
            if (listener != null) listener.onSelectRequired(140, null);
        });
    }

    @Override
    public void onAnnounceAttrib(int player, int count, int availableAttribs) {
        mainHandler.post(() -> {
            if (listener != null) listener.onSelectRequired(141, null);
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
    public void onCardHint(int type, int data) {
        Log.d(TAG, "CardHint: type=" + type + " data=" + data);
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
            GameField.ClientCard card = field.getCard(localPlayer(con & 1), loc, seq);
            if (card != null) {
                card.cmdFlag |= COMMAND_ACTIVATE;
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
        int selectingPlayer = data.get() & 0xFF;
        int count;

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

        count = data.get() & 0xFF;
        for (int i = 0; i < count && data.remaining() >= 7; i++) {
            int code = data.getInt();
            int con = data.get() & 0xFF;
            int loc = data.get() & 0xFF;
            int seq = data.get() & 0xFF;
            GameField.ClientCard card = field.getCard(localPlayer(con & 1), loc, seq);
            if (card != null) {
                card.cmdFlag |= COMMAND_SPSUMMON;
                if (card.code == 0 && code != 0) card.code = code;
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
            GameField.ClientCard card = field.getCard(localPlayer(con & 1), loc, seq);
            if (card != null) {
                card.cmdFlag |= COMMAND_ACTIVATE;
                activatableCards.add(new CmdCardInfo(card, code, desc, flag, i));
            }
        }

        showBP = data.remaining() >= 1 && (data.get() & 0xFF) != 0;
        showEP = data.remaining() >= 1 && (data.get() & 0xFF) != 0;
        showShuffle = data.remaining() >= 1 && (data.get() & 0xFF) != 0;
    }

    /** Game::LocalPlayer：dInfo.isFirst ? player : 1 - player */
    private boolean duelIsFirst = true;

    public int localPlayer(int player) {
        return duelIsFirst ? player : 1 - player;
    }

    public void release() {
        disconnect();
        scriptEngine.release();
    }
}
