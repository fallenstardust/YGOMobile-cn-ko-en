package cn.garymb.ygomobile.game;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.audio.SoundManager;
import cn.garymb.ygomobile.engine.LuaScriptEngine;
import cn.garymb.ygomobile.network.DuelClient;
import cn.garymb.ygomobile.network.WindBotClient;
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
    }

    private GameState state = GameState.IDLE;
    private final DuelClient client;
    private WindBotClient botClient;
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
        new Thread(() -> {
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
        Log.i(TAG, "Starting bot duel: " + botCommand);
        isBotMode = true;
        botClient = new WindBotClient();
        botClient.setListener(new WindBotClient.BotListener() {
            @Override
            public void onBotConnected() {
                Log.i(TAG, "Bot connected");
            }
            @Override
            public void onBotDisconnected() {
                Log.i(TAG, "Bot disconnected");
            }
            @Override
            public void onBotError(String error) {
                Log.e(TAG, "Bot error: " + error);
                mainHandler.post(() -> {
                    if (listener != null) listener.onHintMessage("AI错误: " + error);
                });
            }
        });
        new Thread(() -> {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException e) { /* ignore */ }
            botClient.startBot(host, port, "WindBot", deckFile, botCommand);
        }, "BotConnect").start();
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
        if (botClient != null) {
            botClient.disconnect();
            botClient = null;
        }
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
        if (botClient != null && botClient.isConnected()) {
            botClient.sendUpdateDeck(main, extra, side);
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
        if (pos < 2) {
            playerInfos[pos].name = name;
        }
        soundManager.playSoundEffect(SoundManager.SFX.PLAYER_ENTER);
    }

    @Override
    public void onPlayerChange(int status) {
        Log.i(TAG, "Player change: " + String.format("0x%02X", status));
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
        GameMessageParser.parse(msgType, data, this);
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
            case YGOProtocol.ERRMSG_DECKERROR:
                errorMsg = "卡组验证错误: code=" + code;
                break;
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
    }

    @Override
    public void onTypeChange(int type) {
        Log.i(TAG, "Type changed to: " + type);
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
    public void onStart(int lp, int startHand, int drawCount) {
        field.clear();
        playerInfos[0].lp = lp;
        playerInfos[1].lp = lp;
        playerInfos[0].startLp = lp;
        playerInfos[1].startLp = lp;
        field.players[0].lp = lp;
        field.players[1].lp = lp;
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
        parseUpdateData(player, location, data);
        mainHandler.post(() -> {
            if (listener != null) listener.onFieldChanged();
        });
    }

    @Override
    public void onUpdateCard(int player, int location, int sequence, ByteBuffer data) {
        parseUpdateCard(player, location, sequence, data);
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
        selectFieldPlayer = player;
        selectFieldCount = count;
        selectFieldMask = ~fieldMask;
        if (player != client.selfType) {
            selectFieldMask = ((selectFieldMask >> 16) | (selectFieldMask << 16));
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
        selectFieldPlayer = player;
        selectFieldCount = count;
        selectFieldMask = ~fieldMask;
        if (player != client.selfType) {
            selectFieldMask = ((selectFieldMask >> 16) | (selectFieldMask << 16));
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
    public void onConfirmDecktop(int player, int count, ByteBuffer data) {
        Log.d(TAG, "ConfirmDecktop: player=" + player + " count=" + count);
    }

    @Override
    public void onConfirmCards(int player, int count, ByteBuffer data) {
        Log.d(TAG, "ConfirmCards: player=" + player + " count=" + count);
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
        field.currentPlayer = player;
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
        GameField.ClientCard card = field.getCard(oldCtrl, oldLoc, oldSeq);
        if (card == null) {
            card = new GameField.ClientCard();
        }
        card.code = code;
        card.position = position;
        field.removeCard(oldCtrl, oldLoc, oldSeq);
        field.addCard(newCtrl, newLoc, newSeq, card);

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
    public void onFieldDisabled(int ctrl, int loc, int seq) {
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
    public void onChaining(int code, int ctrl, int loc, int seq, int chainCount) {
        soundManager.playSoundEffect(SoundManager.SFX.ACTIVATE);
        mainHandler.post(() -> {
            if (listener != null) listener.onChainAnimation(code, ctrl, loc, seq);
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
    public void onDraw(int player, int count) {
        soundManager.playSoundEffect(SoundManager.SFX.DRAW);
        mainHandler.post(() -> {
            if (listener != null) {
                listener.onFieldChanged();
                listener.onPlayerInfoUpdated(player);
            }
        });
    }

    @Override
    public void onDamage(int player, int amount) {
        field.players[player].lp -= amount;
        if (field.players[player].lp < 0) field.players[player].lp = 0;
        soundManager.playSoundEffect(SoundManager.SFX.DAMAGE);
        mainHandler.post(() -> {
            if (listener != null) listener.onPlayerInfoUpdated(player);
        });
    }

    @Override
    public void onRecover(int player, int amount) {
        field.players[player].lp += amount;
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
        field.players[player].lp -= cost;
        if (field.players[player].lp < 0) field.players[player].lp = 0;
        mainHandler.post(() -> {
            if (listener != null) listener.onPlayerInfoUpdated(player);
        });
    }

    @Override
    public void onAddCounter(int type, int ctrl, int loc, int seq, int count) {
        GameField.ClientCard card = field.getCard(ctrl, loc, seq);
        if (card != null) {
            card.counters.add(new int[]{type, count});
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
            card.counters.removeIf(c -> c[0] == type);
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

    private void parseUpdateData(int player, int location, ByteBuffer data) {
        if (data.remaining() < 4) return;
        int count = data.getInt();
        List<GameField.ClientCard> list = field.players[player].getLocationList(location);
        if (list == null) return;

        for (int i = 0; i < count && data.remaining() >= 4; i++) {
            int flag = data.getInt();
            GameField.ClientCard card = new GameField.ClientCard();
            parseCardQuery(card, flag, data);
            while (list.size() <= i) list.add(null);
            list.set(i, card);
            if (card != null) {
                card.controler = player;
                card.location = location;
                card.sequence = i;
            }
        }
    }

    private void parseUpdateCard(int player, int location, int sequence, ByteBuffer data) {
        if (data.remaining() < 4) return;
        int flag = data.getInt();
        GameField.ClientCard card = field.getCard(player, location, sequence);
        if (card == null) {
            if (flag == 0) return;
            card = new GameField.ClientCard();
            field.addCard(player, location, sequence, card);
        }
        parseCardQuery(card, flag, data);
    }

    private void parseCardQuery(GameField.ClientCard card, int flag, ByteBuffer data) {
        if ((flag & 0x01) != 0 && data.remaining() >= 4) card.code = data.getInt();
        if ((flag & 0x02) != 0 && data.remaining() >= 4) card.position = data.getInt();
        if ((flag & 0x04) != 0 && data.remaining() >= 4) card.alias = data.getInt();
        if ((flag & 0x08) != 0 && data.remaining() >= 4) card.type = data.getInt();
        if ((flag & 0x10) != 0 && data.remaining() >= 4) card.level = data.getInt();
        if ((flag & 0x20) != 0 && data.remaining() >= 4) card.rank = data.getInt();
        if ((flag & 0x40) != 0 && data.remaining() >= 4) card.attribute = data.getInt();
        if ((flag & 0x80) != 0 && data.remaining() >= 4) card.race = data.getInt();
        if ((flag & 0x100) != 0 && data.remaining() >= 4) card.attack = data.getInt();
        if ((flag & 0x200) != 0 && data.remaining() >= 4) card.defense = data.getInt();
        if ((flag & 0x400) != 0 && data.remaining() >= 4) card.baseAttack = data.getInt();
        if ((flag & 0x800) != 0 && data.remaining() >= 4) card.baseDefense = data.getInt();
        if ((flag & 0x1000) != 0 && data.remaining() >= 4) card.reason = data.getInt();
        if ((flag & 0x40000) != 0 && data.remaining() >= 4) card.owner = data.getInt();
        if ((flag & 0x80000) != 0 && data.remaining() >= 4) card.isDisabled = data.getInt() != 0;
        if ((flag & 0x100000) != 0 && data.remaining() >= 4) card.isPublic = data.getInt() != 0;
        if ((flag & 0x200000) != 0 && data.remaining() >= 4) card.lScale = data.getInt();
        if ((flag & 0x400000) != 0 && data.remaining() >= 4) card.rScale = data.getInt();
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
            GameField.ClientCard card = field.getCard(con, loc, seq);
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
            GameField.ClientCard card = field.getCard(con, loc, seq);
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
            GameField.ClientCard card = field.getCard(con, loc, seq);
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
            GameField.ClientCard card = field.getCard(con, loc, seq);
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
            GameField.ClientCard card = field.getCard(con, loc, seq);
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
            GameField.ClientCard card = field.getCard(con, loc, seq);
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
            GameField.ClientCard card = field.getCard(con, loc, seq);
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
            GameField.ClientCard card = field.getCard(con, loc, seq);
            if (card != null) {
                card.cmdFlag |= COMMAND_ACTIVATE;
                activatableCards.add(new CmdCardInfo(card, code, desc, flag, i));
            }
        }

        showBP = data.remaining() >= 1 && (data.get() & 0xFF) != 0;
        showEP = data.remaining() >= 1 && (data.get() & 0xFF) != 0;
        showShuffle = data.remaining() >= 1 && (data.get() & 0xFF) != 0;
    }

    public void release() {
        disconnect();
        scriptEngine.release();
    }
}
