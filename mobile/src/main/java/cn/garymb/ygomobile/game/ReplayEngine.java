package cn.garymb.ygomobile.game;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;

import cn.garymb.ygomobile.audio.SoundManager;
import cn.garymb.ygomobile.engine.NativeScriptBootstrap;
import cn.garymb.ygomobile.engine.OcgDuelEngine;
import cn.garymb.ygomobile.network.YGOProtocol;
import ocgcore.enums.CardLocation;

public class ReplayEngine implements GameMessageParser.MessageHandler {
    private static final String TAG = "ReplayEngine";

    public enum ReplayState {
        IDLE, LOADING, PLAYING, PAUSED, FINISHED, ERROR
    }

    public interface ReplayListener {
        void onReplayStateChanged(ReplayState state);
        void onReplayFieldChanged();
        void onReplayPlayerInfoUpdated(int player);
        void onReplayPhaseChanged(int phase);
        void onReplayHintMessage(String hint);
        void onReplayFinished(int winner, int reason);
    }

    private ReplayState state = ReplayState.IDLE;
    private final GameField field;
    private final SoundManager soundManager;
    private ReplayListener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private ReplayReader.ReplayData replayData;
    /** 响应记录流（uniform 模式消息区全部为 [uint8 len][response] 记录，非消息字节流）。 */
    private ByteBuffer replayBuffer;
    /** 当前正在消费的单条引擎消息体缓冲，供 skipBytes 推进游标。 */
    private ByteBuffer msgBuf;
    private long pduel = 0L;
    private volatile boolean replayWinSeen = false;
    private Thread replayThread;
    private volatile boolean isRunning = false;
    private volatile boolean isPaused = false;
    private volatile boolean skipForward = false;
    private volatile boolean rewindToStart = false;
    private volatile boolean isRestarting = false;
    private volatile boolean isSwapping = false;
    private int currentStep = 0;
    private int totalSteps = 0;
    private int skipStep = 0;
    private int skipTurn = 0;
    /** undo/restart 重放时目标落点步（在重置 currentStep 前捕获），landing 后写回 currentStep。 */
    private volatile int restartTargetStep = 0;
    private int restartFromStep = 0;
    private boolean isSkipping = false;
    private byte[] originalResponseBytes = null;
    /** 回放异常终止原因（对齐 replay_mode.cpp "Error occurs." 与 EndDuel 1501 提示），自然结束为 null。 */
    private volatile String lastErrorMessage;

    public String getLastErrorMessage() { return lastErrorMessage; }

    public ReplayEngine(GameField field, SoundManager soundManager) {
        this.field = field;
        this.soundManager = soundManager;
    }

    public void setListener(ReplayListener listener) {
        this.listener = listener;
    }

    public ReplayState getState() { return state; }
    public GameField getField() { return field; }
    public ReplayReader.ReplayData getReplayData() { return replayData; }

    private void setState(ReplayState newState) {
        this.state = newState;
        mainHandler.post(() -> {
            if (listener != null) listener.onReplayStateChanged(newState);
        });
    }

    public void loadAndPlay(String replayPath) {
        loadAndPlay(replayPath, 1);
    }

    public void loadAndPlay(String replayPath, int startTurn) {
        setState(ReplayState.LOADING);
        new Thread(() -> {
            replayData = ReplayReader.loadReplay(replayPath);
            if (replayData == null) {
                lastErrorMessage = "无法加载录像文件";
                setState(ReplayState.ERROR);
                mainHandler.post(() -> {
                    if (listener != null) listener.onReplayHintMessage("无法加载录像文件");
                });
                return;
            }

            // 回放需要重跑引擎复现消息流（C++ ReplayMode::ReplayThread），先引导卡片/脚本资源
            if (!NativeScriptBootstrap.ensureEngineReady()) {
                lastErrorMessage = "决斗引擎或卡片脚本加载失败";
                setState(ReplayState.ERROR);
                mainHandler.post(() -> {
                    if (listener != null) listener.onReplayHintMessage("决斗引擎或卡片脚本加载失败");
                });
                return;
            }

            field.clear();
            setupInitialField();

            replayBuffer = replayData.replayBuffer;
            replayBuffer.order(ByteOrder.LITTLE_ENDIAN);
            // 快照响应记录区（undo/restart 时 Rewind 重放，对齐 cur_replay.Rewind）
            originalResponseBytes = new byte[replayBuffer.remaining()];
            ((ByteBuffer) replayBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)).get(originalResponseBytes);
            replayWinSeen = false;

            setState(ReplayState.PLAYING);
            isRunning = true;
            this.skipTurn = startTurn > 0 ? startTurn - 1 : 0;
            this.isSkipping = (this.skipTurn > 0);
            
            mainHandler.post(() -> {
                String info = buildReplayInfo(startTurn);
                if (listener != null) listener.onReplayHintMessage(info);
            });

            replayThread = new Thread(() -> replayLoop(startTurn), "ReplayThread");
            replayThread.setDaemon(true);
            replayThread.start();
        }, "ReplayLoad").start();
    }

    private String buildReplayInfo() {
        return buildReplayInfo(1);
    }

    private String buildReplayInfo(int startTurn) {
        StringBuilder sb = new StringBuilder();
        sb.append("录像回放\n");
        for (int i = 0; i < replayData.playerNames.size(); i++) {
            if (i > 0) sb.append(" vs ");
            sb.append(replayData.playerNames.get(i));
        }
        sb.append("\nLP: ").append(replayData.params.startLp);
        sb.append(" | 手牌: ").append(replayData.params.startHand);
        sb.append(" | 抽卡: ").append(replayData.params.drawCount);
        if (replayData.isTag) sb.append(" [双打]");
        if (replayData.isSingleMode) sb.append(" [残局]");
        if (startTurn > 1) sb.append(" | 从第").append(startTurn).append("回合开始");
        return sb.toString();
    }

    private void setupInitialField() {
        int startLp = replayData.params.startLp;
        field.players[0].lp = startLp;
        field.players[1].lp = startLp;
        field.dInfo.startLp = startLp;
        field.dInfo.lp[0] = startLp;
        field.dInfo.lp[1] = startLp;

        if (!replayData.isSingleMode && !replayData.decks.isEmpty()) {
            setupDeckForPlayer(0, replayData.decks.get(0));
            if (replayData.decks.size() > 1) {
                setupDeckForPlayer(1, replayData.decks.get(replayData.isTag ? 2 : 1));
            }
        }

        for (int p = 0; p < 2; p++) {
            if (p < replayData.playerNames.size()) {
                final int playerIndex = p;
                mainHandler.post(() -> {
                    if (listener != null) listener.onReplayPlayerInfoUpdated(playerIndex);
                });
            }
        }
    }

    private void setupDeckForPlayer(int player, ReplayReader.DeckInfo deckInfo) {
        List<GameField.ClientCard> deckList = field.players[player].deck;
        for (int i = 0; i < deckList.size(); i++) deckList.set(i, null);

        for (int i = 0; i < deckInfo.main.size() && i < deckList.size(); i++) {
            GameField.ClientCard card = new GameField.ClientCard();
            card.code = 0;
            card.position = 0x2;
            card.controler = player;
            card.location = CardLocation.Deck.value();
            card.sequence = i;
            deckList.set(i, card);
        }

        List<GameField.ClientCard> extraList = field.players[player].extra;
        for (int i = 0; i < extraList.size(); i++) extraList.set(i, null);

        for (int i = 0; i < deckInfo.extra.size() && i < extraList.size(); i++) {
            GameField.ClientCard card = new GameField.ClientCard();
            card.code = 0;
            card.position = 0x2;
            card.controler = player;
            card.location = CardLocation.Extra.value();
            card.sequence = i;
            extraList.set(i, card);
        }
        // 对齐 replay_mode.cpp StartDuel 的 dField.Initial(player, mainc, extrac)
        field.initial(player, deckInfo.main.size(), deckInfo.extra.size(), 0);
    }

    private void replayLoop(int startTurn) {
        try {
            // 对齐 ReplayMode::ReplayThread：重建决斗→逐条 process/get_message→分析，
            // 消息流由引擎以相同 seed 重新产生，文件里只有玩家响应记录
            if (!startReplayDuel()) {
                lastErrorMessage = "回放引擎启动失败（卡数据或脚本缺失）";
                mainHandler.post(() -> {
                    if (listener != null) listener.onReplayHintMessage("回放引擎启动失败（卡数据或脚本缺失）");
                });
                isRunning = false;
                setState(ReplayState.ERROR);
                return;
            }
            if (!replayData.isSingleMode) {
                // 对齐 ReplayThread L88-91：开局先刷两名玩家的卡组/额外卡面
                refreshDeck(0); refreshDeck(1); refreshExtra(0); refreshExtra(1);
            }
            soundManager.playBGM(SoundManager.BGM.DUEL);
            while (isRunning) {
                if (isPaused && !isRestarting) {
                    try { Thread.sleep(100); } catch (InterruptedException e) { break; }
                    continue;
                }

                if (isRestarting) {
                    performRestart();
                    continue;
                }

                if (isSwapping) {
                    performSwapField();
                    isSwapping = false;
                }

                int result = OcgDuelEngine.process(pduel);
                int len = result & OcgDuelEngine.PROCESSOR_BUFFER_LEN;
                int flag = result & OcgDuelEngine.PROCESSOR_FLAG;
                if (len > 0) {
                    byte[] msg = OcgDuelEngine.getMessage(pduel);
                    if (!replayAnalyze(msg)) {
                        break;
                    }
                }
                if (flag == OcgDuelEngine.PROCESSOR_END) {
                    break;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Replay loop error", e);
        }

        endReplayDuel();
        isRunning = false;
        setState(ReplayState.FINISHED);
        final boolean winShown = replayWinSeen;
        mainHandler.post(() -> {
            soundManager.stopBGM();
            // 回放自然播放完毕（未经 MSG_WIN 判定胜负）：winner=-1 表示无结果，UI 不显示胜负文字
            if (!winShown && listener != null) listener.onReplayFinished(-1, 0);
        });
    }

    /** 重建决斗（对齐 ReplayMode::StartDuel）：seed/卡组/参数取录像头，不写任何消息。 */
    private boolean startReplayDuel() {
        ReplayReader.ExtendedReplayHeader eh = replayData.header;
        if (eh.base.id == ReplayReader.REPLAY_ID_YRP2) {
            pduel = OcgDuelEngine.createDuelV2(eh.seedSequence);
        } else {
            // YRP1 旧格式：C++ 以 mt19937(seed) 首个输出建局，此处直接用原 seed
            pduel = OcgDuelEngine.createDuel(eh.base.seed);
        }
        if (pduel == 0L) {
            return false;
        }
        field.dInfo.duelRule = replayData.params.duelFlag >> 16; // replay_mode.cpp L175
        OcgDuelEngine.setPlayerInfo(pduel, 0, replayData.params.startLp,
                replayData.params.startHand, replayData.params.drawCount);
        OcgDuelEngine.setPlayerInfo(pduel, 1, replayData.params.startLp,
                replayData.params.startHand, replayData.params.drawCount);
        if (replayData.isSingleMode) {
            // L213-219：残局回放预载 ./single/xxx.lua
            if (OcgDuelEngine.preloadScript(pduel, "./single/" + replayData.scriptName) == 0) {
                OcgDuelEngine.endDuel(pduel);
                pduel = 0L;
                return false;
            }
        } else if (replayData.isTag && replayData.decks.size() >= 4) {
            // L193-211：0/2 为主将 new_card，1/3 为替补 new_tag_card
            loadDeckToEngine(replayData.decks.get(0), 0, false);
            loadDeckToEngine(replayData.decks.get(1), 0, true);
            loadDeckToEngine(replayData.decks.get(2), 1, false);
            loadDeckToEngine(replayData.decks.get(3), 1, true);
        } else {
            for (int i = 0; i < replayData.decks.size() && i < 2; i++) {
                loadDeckToEngine(replayData.decks.get(i), i, false);
            }
        }
        OcgDuelEngine.startDuel(pduel, replayData.params.duelFlag);
        return true;
    }

    private void loadDeckToEngine(ReplayReader.DeckInfo deck, int player, boolean tagMate) {
        if (tagMate) {
            for (int code : deck.main) {
                OcgDuelEngine.newTagCard(pduel, code, player, OcgDuelEngine.LOCATION_DECK);
            }
            for (int code : deck.extra) {
                OcgDuelEngine.newTagCard(pduel, code, player, OcgDuelEngine.LOCATION_EXTRA);
            }
        } else {
            for (int code : deck.main) {
                OcgDuelEngine.newCard(pduel, code, player, player, OcgDuelEngine.LOCATION_DECK, 0,
                        OcgDuelEngine.POS_FACEDOWN_DEFENSE);
            }
            for (int code : deck.extra) {
                OcgDuelEngine.newCard(pduel, code, player, player, OcgDuelEngine.LOCATION_EXTRA, 0,
                        OcgDuelEngine.POS_FACEDOWN_DEFENSE);
            }
        }
    }

    private void endReplayDuel() {
        if (pduel != 0L) {
            OcgDuelEngine.endDuel(pduel);
            pduel = 0L;
        }
    }

    /**
     * 分析一批引擎消息（ packed 多条），对齐 ReplayMode::ReplayAnalyze：逐条按消息体
     * 长度表推进游标，SELECT 类从响应流读记录喂引擎，显示类交 processMessage；
     * 可暂停消息计数并做步进/快进/800ms 节奏控制。
     */
    private boolean replayAnalyze(byte[] data) {
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        while (buf.hasRemaining()) {
            if (!isRunning) {
                return false;
            }
            if (isSwapping) {
                performSwapField();
                isSwapping = false;
            }
            int msgType = buf.get() & 0xFF;
            boolean pauseable = isPauseable(msgType);
            if (!processMessage(msgType, buf)) {
                return false;
            }
            if (!pauseable) {
                continue;
            }

            if (skipStep > 0) {
                skipStep--;
                if (skipStep == 0) {
                    // 上一步回退：已快进到目标步，停在该步（步进模式）
                    isSkipping = false;
                    currentStep = restartFromStep;
                    pause();
                    notifyField();
                }
                continue;
            }

            currentStep++;

            if (msgType == 40) { // MSG_NEW_TURN
                if (isSkipping && skipTurn > 0) {
                    skipTurn--;
                    if (skipTurn == 0) {
                        isSkipping = false;
                        mainHandler.post(() -> {
                            if (listener != null) listener.onReplayHintMessage("快进结束，从当前回合开始正常播放");
                        });
                    }
                    continue;
                }
            }

            if (!skipForward && !isSkipping) {
                try { Thread.sleep(800); } catch (InterruptedException e) { return false; }
            }
        }
        return true;
    }

    private boolean isPauseable(int msgType) {
        // 对齐 replay_mode.cpp ReplayAnalyze 的 pauseable=false 集合（这些消息不计步/不暂停）
        switch (msgType) {
            case 52: // MSG_SET
            case 54: // MSG_FIELD_DISABLED
            case 60: // MSG_SUMMONING
            case 62: // MSG_SPSUMMONING
            case 64: // MSG_FLIPSUMMONING
            case 72: // MSG_CHAIN_SOLVING
            case 73: // MSG_CHAIN_SOLVED
            case 74: // MSG_CHAIN_END
            case 80: // MSG_CARD_SELECTED
            case 81: // MSG_RANDOM_SELECTED
            case 93: // MSG_EQUIP
            case 95: // MSG_UNEQUIP
            case 96: // MSG_CARD_TARGET
            case 97: // MSG_CANCEL_TARGET
            case 111: // MSG_BATTLE
            case 112: // MSG_ATTACK_DISABLED
            case 113: // MSG_DAMAGE_STEP_START
            case 114: // MSG_DAMAGE_STEP_END
                return false;
            default:
                return true;
        }
    }

    private void performRestart() {
        isRestarting = false;
        isSkipping = true;

        // 对齐 ReplayMode::Restart：end_duel → Rewind（响应流回到起点）→ 重新 StartDuel 重跑
        endReplayDuel();
        replayBuffer = ByteBuffer.wrap(originalResponseBytes).order(ByteOrder.LITTLE_ENDIAN);

        field.clear();
        setupInitialField();

        if (!startReplayDuel()) {
            isRunning = false;
            return;
        }
        if (!replayData.isSingleMode) {
            // 对齐 ReplayThread L121-124：restart 重跑后同样先刷卡组/额外卡面
            refreshDeck(0); refreshDeck(1); refreshExtra(0); refreshExtra(1);
        }

        currentStep = 0;
        skipStep = Math.max(0, restartTargetStep);
        restartFromStep = skipStep;
        restartTargetStep = 0;

        if (skipStep == 0) {
            isSkipping = false;
            pause();
            notifyField();
        }
    }

    private void performSwapField() {
        field.swapField();
        notifyField();
    }

    private boolean processMessage(int msgType, ByteBuffer buf) {
        if (buf == null) return false;
        msgBuf = buf;

        try {
            switch (msgType) {
                case 1: // MSG_RETRY
                    lastErrorMessage = "Error occurs.（引擎请求重放 MSG_RETRY，步数=" + currentStep + "）";
                    mainHandler.post(() -> {
                        if (listener != null) listener.onReplayHintMessage("录像错误: Retry");
                    });
                    return false;

                case 2: // MSG_HINT
                    if (buf.remaining() < 6) return false;
                    int hintType = buf.get() & 0xFF;
                    int hintPlayer = buf.get() & 0xFF;
                    int hintData = buf.getInt();
                    onHint(hintType, hintPlayer, hintData);
                    break;

                case 3: // MSG_WAITING
                    break;

                case 4: { // MSG_START
                    if (buf.remaining() < 16) return false;
                    int playerType = buf.get() & 0xFF;
                    int duelRule = buf.get() & 0xFF;
                    int lp0 = buf.getInt();
                    int lp1 = buf.getInt();
                    int deck0 = buf.getShort() & 0xFFFF;
                    int extra0 = buf.getShort() & 0xFFFF;
                    int deck1 = buf.getShort() & 0xFFFF;
                    int extra1 = buf.getShort() & 0xFFFF;
                    onStart(playerType, duelRule, lp0, lp1, deck0, extra0, deck1, extra1);
                    break;
                }

                case 5: { // MSG_WIN
                    if (buf.remaining() < 2) return false;
                    int winner = buf.get() & 0xFF;
                    int reason = buf.get() & 0xFF;
                    replayWinSeen = true;
                    onWin(winner, reason);
                    return false;
                }

                case 6: // MSG_UPDATE_DATA
                case 7: // MSG_UPDATE_CARD
                    handleUpdate(msgType, buf);
                    break;

                case 8: // MSG_REQUEST_DECK
                    buf.get();
                    break;

                case 10: // MSG_SELECT_BATTLECMD
                case 11: // MSG_SELECT_IDLECMD
                    // 对齐 replay_mode.cpp L340/L357：战斗/主要阶段选择前 ReplayRefresh 刷全部卡面
                    replayRefresh();
                    if (!selectWithResponse(msgType, buf)) return false;
                    break;
                case 12: // MSG_SELECT_EFFECTYN
                case 13: // MSG_SELECT_YESNO
                case 14: // MSG_SELECT_OPTION
                case 15: // MSG_SELECT_CARD
                case 16: // MSG_SELECT_CHAIN
                case 17: // MSG_SORT_CHAIN
                case 18: // MSG_SELECT_PLACE
                case 19: // MSG_SELECT_POSITION
                case 20: // MSG_SELECT_TRIBUTE
                case 22: // MSG_SELECT_COUNTER
                case 23: // MSG_SELECT_SUM
                case 24: // MSG_SELECT_DISFIELD
                case 25: // MSG_SORT_CARD
                case 26: // MSG_SELECT_UNSELECT_CARD
                    // 引擎产生的 SELECT 消息带完整消息体：推进游标后从响应流读记录喂引擎
                    if (!selectWithResponse(msgType, buf)) return false;
                    break;

                case 30: // MSG_CONFIRM_DECKTOP
                case 31: // MSG_CONFIRM_CARDS
                case 42: // MSG_CONFIRM_EXTRATOP
                    skipConfirm(msgType, buf);
                    break;

                case 32: // MSG_SHUFFLE_DECK
                    if (buf.remaining() < 1) return false;
                    int sdPlayer = buf.get() & 0xFF;
                    onShuffleDeck(sdPlayer);
                    // 对齐 replay_mode.cpp L458：洗牌后整库重查卡面
                    refreshDeck(sdPlayer);
                    break;

                case 33: // MSG_SHUFFLE_HAND
                    if (buf.remaining() < 2) return false;
                    int shPlayer = buf.get() & 0xFF;
                    int shCount = buf.get() & 0xFF;
                    skipBytes(shCount * 4);
                    onShuffleHand(shPlayer);
                    break;

                case 34: // MSG_REFRESH_DECK
                    skipBytes(1);
                    onRefreshDeck(0);
                    break;

                case 35: // MSG_SWAP_GRAVE_DECK
                    if (buf.remaining() < 1) return false;
                    int sgPlayer = buf.get() & 0xFF;
                    onSwapGraveDeck(sgPlayer);
                    // 对齐 replay_mode.cpp L483：墓地/卡组互换后重查墓地卡面
                    refreshGrave(sgPlayer);
                    break;

                case 36: // MSG_SHUFFLE_SET_CARD
                    skipBytes(1);
                    if (buf.remaining() < 1) return false;
                    int sscCount = buf.get() & 0xFF;
                    skipBytes(sscCount * 8);
                    onShuffleSetCard(0, sscCount, null);
                    break;

                case 37: // MSG_REVERSE_DECK
                    onReverseDeck(0);
                    // 对齐 replay_mode.cpp L488-489：反转卡组后双方卡组重查
                    refreshDeck(0);
                    refreshDeck(1);
                    break;

                case 38: // MSG_DECK_TOP
                    if (buf.remaining() < 6) return false;
                    int dtPlayer = buf.get() & 0xFF;
                    int dtCode = buf.getInt();
                    buf.get();
                    onDeckTop(dtPlayer, dtCode);
                    break;

                case 39: { // MSG_SHUFFLE_EXTRA
                    skipBytes(1);
                    if (buf.remaining() < 1) return false;
                    int seCount = buf.get() & 0xFF;
                    skipBytes(seCount * 4);
                    notifyField();
                    break;
                }

                case 40: // MSG_NEW_TURN
                    if (buf.remaining() < 1) return false;
                    int ntPlayer = buf.get() & 0xFF;
                    onNewTurn(ntPlayer);
                    break;

                case 41: // MSG_NEW_PHASE
                    if (buf.remaining() < 2) return false;
                    int phase = buf.getShort() & 0xFFFF;
                    onNewPhase(phase);
                    // 对齐 replay_mode.cpp L520：新阶段 ReplayRefresh
                    replayRefresh();
                    break;

                case 50: // MSG_MOVE
                    if (buf.remaining() < 16) return false;
                    int mCode = buf.getInt();
                    int mOldCtrl = buf.get() & 0xFF;
                    int mOldLoc = buf.get() & 0xFF;
                    int mOldSeq = buf.get() & 0xFF;
                    int mOldPos = buf.get() & 0xFF; // 超量素材离场时为素材序号
                    int mNewCtrl = buf.get() & 0xFF;
                    int mNewLoc = buf.get() & 0xFF;
                    int mNewSeq = buf.get() & 0xFF;
                    int mPos = buf.get() & 0xFF;
                    int mReason = buf.getInt();
                    onMove(mCode, mOldCtrl, mOldLoc, mOldSeq, mOldPos, mNewCtrl, mNewLoc, mNewSeq, mPos, mReason);
                    // 对齐 replay_mode.cpp L534-537：移动后刷目标卡/同区卡组
                    if (mNewLoc != 0 && (mNewLoc & 0x80) == 0
                            && (mOldLoc != mNewLoc || mOldCtrl != mNewCtrl)) {
                        refreshSingle(mNewCtrl & 1, mNewLoc, mNewSeq, REFRESH_FLAG_ALL);
                    } else if (mOldLoc == mNewLoc && mNewLoc == OcgDuelEngine.LOCATION_DECK) {
                        refreshDeck(mNewCtrl);
                    }
                    break;

                case 51: // MSG_POS_CHANGE（common.h：51，replay_mode.cpp pbuf+=9）
                    if (buf.remaining() < 9) return false;
                    int pcCode = buf.getInt();
                    int pcCtrl = buf.get() & 0xFF;
                    int pcLoc = buf.get() & 0xFF;
                    int pcSeq = buf.get() & 0xFF;
                    int pcOld = buf.get() & 0xFF;
                    int pcNew = buf.get() & 0xFF;
                    onPosChange(pcCode, pcCtrl, pcLoc, pcSeq, pcOld, pcNew);
                    break;

                case 52: // MSG_SET（pbuf+=8：code + ctrl loc seq pos）
                    if (buf.remaining() < 8) return false;
                    int setCode = buf.getInt();
                    int setCtrl = buf.get() & 0xFF;
                    int setLoc = buf.get() & 0xFF;
                    int setSeq = buf.get() & 0xFF;
                    buf.get(); // pos
                    onSet(setCode, setCtrl, setLoc, setSeq);
                    break;

                case 53: // MSG_SWAP（pbuf+=16）
                    if (buf.remaining() < 16) return false;
                    buf.getInt(); int sw1c = buf.get() & 0xFF; int sw1l = buf.get() & 0xFF; int sw1s = buf.get() & 0xFF; buf.get();
                    buf.getInt(); int sw2c = buf.get() & 0xFF; int sw2l = buf.get() & 0xFF; int sw2s = buf.get() & 0xFF; buf.get();
                    onSwap(sw1c, sw1l, sw1s, sw2c, sw2l, sw2s);
                    break;

                case 54: // MSG_FIELD_DISABLED（pbuf+=4）
                    skipBytes(4);
                    break;

                case 60: { // MSG_SUMMONING
                    if (buf.remaining() < 8) return false;
                    int sumCode = buf.getInt();
                    int sumCtrl = buf.get() & 0xFF;
                    int sumLoc = buf.get() & 0xFF;
                    int sumSeq = buf.get() & 0xFF;
                    buf.get(); // pos
                    onSummoning(sumCode, sumCtrl, sumLoc, sumSeq);
                    break;
                }
                case 61: onSummoned(); replayRefresh(); break;
                case 62: { // MSG_SPSUMMONING
                    if (buf.remaining() < 8) return false;
                    int spCode = buf.getInt();
                    int spCtrl = buf.get() & 0xFF;
                    int spLoc = buf.get() & 0xFF;
                    int spSeq = buf.get() & 0xFF;
                    buf.get(); // pos
                    onSpSummoning(spCode, spCtrl, spLoc, spSeq);
                    break;
                }
                case 63: onSpSummoned(); replayRefresh(); break;
                case 64: { // MSG_FLIPSUMMONING
                    if (buf.remaining() < 8) return false;
                    int flCode = buf.getInt();
                    int flCtrl = buf.get() & 0xFF;
                    int flLoc = buf.get() & 0xFF;
                    int flSeq = buf.get() & 0xFF;
                    buf.get(); // pos
                    onFlipSummoning(flCode, flCtrl, flLoc, flSeq);
                    break;
                }
                case 65: onFlipSummoned(); replayRefresh(); break;

                case 70: // MSG_CHAINING
                    if (buf.remaining() < 16) return false;
                    int chCode = buf.getInt();
                    int chPcc = buf.get() & 0xFF;
                    int chPcl = buf.get() & 0xFF;
                    int chPcs = buf.get() & 0xFF;
                    int chSubs = buf.get() & 0xFF;
                    int chCc = buf.get() & 0xFF;
                    int chCl = buf.get() & 0xFF;
                    int chCs = buf.get() & 0xFF;
                    int chDesc = buf.getInt();
                    int chCt = buf.get() & 0xFF;
                    onChaining(chCode, chPcc, chPcl, chPcs, chSubs, chCc, chCl, chCs, chDesc);
                    break;
                case 71: skipBytes(1); onChained(0); replayRefresh(); break;
                case 72: skipBytes(1); onChainSolving(0); break;
                case 73: skipBytes(1); onChainSolved(0); replayRefresh(); break;
                case 74: onChainEnd(); replayRefresh(); break;
                case 75: skipBytes(1); onChainNegated(0); break;
                case 76: skipBytes(1); onChainDisabled(0); break;

                case 80: case 81: { // MSG_CARD_SELECTED / MSG_RANDOM_SELECTED
                    skipBytes(1);
                    int csCount = buf.get() & 0xFF;
                    skipBytes(csCount * 4);
                    break;
                }
                case 83: { // MSG_BECOME_TARGET
                    int btCount = buf.get() & 0xFF;
                    skipBytes(btCount * 4);
                    break;
                }

                case 90: { // MSG_DRAW
                    if (buf.remaining() < 2) return false;
                    int dPlayer = buf.get() & 0xFF;
                    int dCount = buf.get() & 0xFF;
                    int[] dCodes = new int[dCount];
                    for (int i = 0; i < dCount && buf.remaining() >= 4; i++) dCodes[i] = buf.getInt();
                    onDraw(dPlayer, dCount, dCodes);
                    break;
                }

                case 91: // MSG_DAMAGE
                    if (buf.remaining() < 5) return false;
                    int dmgPlayer = buf.get() & 0xFF;
                    int dmgAmt = buf.getInt();
                    onDamage(dmgPlayer, dmgAmt);
                    break;

                case 92: // MSG_RECOVER
                    if (buf.remaining() < 5) return false;
                    int rcvPlayer = buf.get() & 0xFF;
                    int rcvAmt = buf.getInt();
                    onRecover(rcvPlayer, rcvAmt);
                    break;

                case 93: // MSG_EQUIP：c1 l1 s1 pos c2 l2 s2 pos（duelclient.cpp L3619-3627）
                    if (buf.remaining() < 8) return false;
                    int eqCtrl = buf.get() & 0xFF; int eqLoc = buf.get() & 0xFF; int eqSeq = buf.get() & 0xFF;
                    buf.get(); // position
                    int tCtrl = buf.get() & 0xFF; int tLoc = buf.get() & 0xFF; int tSeq = buf.get() & 0xFF;
                    buf.get(); // position
                    onEquip(0, eqCtrl, eqLoc, eqSeq, tCtrl, tLoc, tSeq);
                    break;

                case 94: // MSG_LPUPDATE
                    if (buf.remaining() < 5) return false;
                    int lpPlayer = buf.get() & 0xFF;
                    int lpVal = buf.getInt();
                    onLpUpdate(lpPlayer, lpVal);
                    break;

                case 95: { // MSG_UNEQUIP：c l s pos
                    if (buf.remaining() < 4) return false;
                    int unCtrl = buf.get() & 0xFF; int unLoc = buf.get() & 0xFF; int unSeq = buf.get() & 0xFF;
                    buf.get(); // position
                    onUnequip(unCtrl, unLoc, unSeq);
                    break;
                }

                case 96: case 97: { // MSG_CARD_TARGET / MSG_CANCEL_TARGET：c1 l1 s1 pos c2 l2 s2 pos
                    if (buf.remaining() < 8) return false;
                    int c1ctrl = buf.get() & 0xFF; int c1loc = buf.get() & 0xFF; int c1seq = buf.get() & 0xFF;
                    buf.get(); // position
                    int c2ctrl = buf.get() & 0xFF; int c2loc = buf.get() & 0xFF; int c2seq = buf.get() & 0xFF;
                    buf.get(); // position
                    if (msgType == 96) onCardTarget(c1ctrl, c1loc, c1seq, c2ctrl, c2loc, c2seq);
                    else onCancelTarget(c1ctrl, c1loc, c1seq, c2ctrl, c2loc, c2seq);
                    break;
                }

                case 100: // MSG_PAY_LPCOST
                    if (buf.remaining() < 5) return false;
                    int costPlayer = buf.get() & 0xFF;
                    int costAmt = buf.getInt();
                    onPayLpCost(costPlayer, costAmt);
                    break;

                case 101: case 102: // ADD_COUNTER / REMOVE_COUNTER
                    skipBytes(7);
                    break;

                case 110: // MSG_ATTACK
                    if (buf.remaining() < 8) return false;
                    int aCtrl = buf.get() & 0xFF; int aLoc = buf.get() & 0xFF; int aSeq = buf.get() & 0xFF; buf.get();
                    int defCtrl = buf.get() & 0xFF; int defLoc = buf.get() & 0xFF; int defSeq = buf.get() & 0xFF; buf.get();
                    onAttack(aCtrl, aLoc, aSeq, defCtrl, defLoc, defSeq);
                    break;

                case 111: // MSG_BATTLE
                    skipBytes(26);
                    break;

                case 112: onAttackDisabled(); break; // MSG_ATTACK_DISABLED
                case 113: onDamageStepStart(); replayRefresh(); break; // MSG_DAMAGE_STEP_START
                case 114: onDamageStepEnd(); replayRefresh(); break; // MSG_DAMAGE_STEP_END

                case 120: skipBytes(8); break; // MSG_MISSED_EFFECT
                case 130: { // MSG_TOSS_COIN
                    if (buf.remaining() < 2) return false;
                    int tcP = buf.get() & 0xFF;
                    int tcC = buf.get() & 0xFF;
                    skipBytes(tcC);
                    onTossCoin(tcP, tcC, null);
                    break;
                }
                case 131: { // MSG_TOSS_DICE
                    if (buf.remaining() < 2) return false;
                    int tdP = buf.get() & 0xFF;
                    int tdC = buf.get() & 0xFF;
                    skipBytes(tdC);
                    onTossDice(tdP, tdC, null);
                    break;
                }

                case 132: // MSG_ROCK_PAPER_SCISSORS：player，随后是响应记录
                    if (!selectWithResponse(132, buf)) return false;
                    break;

                case 133: // MSG_HAND_RES
                    skipBytes(1);
                    break;

                case 140: case 141: case 142: case 143: // ANNOUNCE_*：均等待录像中的响应
                    if (!selectWithResponse(msgType, buf)) return false;
                    break;

                case 160: skipBytes(9); break; // CARD_HINT
                case 165: skipBytes(6); break; // PLAYER_HINT

                case 170: // MSG_MATCH_KILL
                    skipBytes(4);
                    break;

                case 161: { // MSG_TAG_SWAP：固定 9 字节 + main*4 + extra*4（C++ pbuf+=pbuf[2]*4+pbuf[4]*4+9）
                    int tsPlayer = buf.get() & 0xFF;
                    buf.get();
                    int tsMainCount = buf.get() & 0xFF;
                    buf.get();
                    int tsExtraCount = buf.get() & 0xFF;
                    skipBytes(4);
                    skipBytes(tsMainCount * 4 + tsExtraCount * 4);
                    onTagSwap(tsPlayer);
                    // 对齐 replay_mode.cpp L802-803：双打换人后卡组/额外重查
                    refreshDeck(tsPlayer);
                    refreshExtra(tsPlayer);
                    break;
                }

                case 162: { // MSG_RELOAD_FIELD
                    skipBytes(1);
                    for (int p = 0; p < 2; p++) {
                        skipBytes(4);
                        for (int s = 0; s < 7; s++) {
                            int val = buf.get() & 0xFF;
                            if (val != 0) skipBytes(2);
                        }
                        for (int s = 0; s < 8; s++) {
                            int val = buf.get() & 0xFF;
                            if (val != 0) skipBytes(1);
                        }
                        skipBytes(6);
                    }
                    skipBytes(1);
                    onReloadField();
                    // 对齐 replay_mode.cpp L824-825：RELOAD_FIELD 全区域重查 + 刷新全部卡面
                    replayReload();
                    break;
                }

                case 163: { // MSG_AI_NAME
                    int aiLen = buf.getShort() & 0xFFFF;
                    if (buf.remaining() < aiLen + 1) return false;
                    byte[] aiBytes = new byte[aiLen];
                    buf.get(aiBytes);
                    buf.get();
                    onAiName(new String(aiBytes, StandardCharsets.UTF_8));
                    break;
                }
                case 164: { // MSG_SHOW_HINT
                    int shLen = buf.getShort() & 0xFFFF;
                    if (buf.remaining() < shLen + 1) return false;
                    byte[] shBytes = new byte[shLen];
                    buf.get(shBytes);
                    buf.get();
                    onShowHint(new String(shBytes, StandardCharsets.UTF_8));
                    break;
                }

                default:
                    Log.w(TAG, "Unknown replay msg: " + msgType);
                    lastErrorMessage = "未知回放消息 " + msgType + "（回放游标可能错位，步数=" + currentStep + "）";
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing replay msg " + msgType, e);
            lastErrorMessage = "处理回放消息 " + msgType + " 异常：" + e;
            return false;
        }

        return true;
    }

    private void skipBytes(int count) {
        if (msgBuf != null && msgBuf.remaining() >= count) {
            msgBuf.position(msgBuf.position() + count);
        }
    }

    /**
     * SELECT/ANNOUNCE 类消息：按 replay_mode.cpp::ReplayAnalyze 的长度表推进引擎消息游标，
     * 然后从响应记录流读一条 [uint8 len][data] 喂给 set_responseb（对应 ReadReplayResponse）。
     */
    private boolean selectWithResponse(int msgType, ByteBuffer buf) {
        switch (msgType) {
            case 10: { // SELECT_BATTLECMD: player + cnt*11 + cnt2*8 + 2
                buf.get();
                int bc1 = buf.get() & 0xFF;
                skipBytes(bc1 * 11);
                int bc2 = buf.get() & 0xFF;
                skipBytes(bc2 * 8 + 2);
                break;
            }
            case 11: { // SELECT_IDLECMD: player + 5组(cnt*7) + cnt*11+3
                buf.get();
                for (int t = 0; t < 5; t++) {
                    int c = buf.get() & 0xFF;
                    skipBytes(c * 7);
                }
                int ic = buf.get() & 0xFF;
                skipBytes(ic * 11 + 3);
                break;
            }
            case 12: // SELECT_EFFECTYN: player + 12
                skipBytes(13);
                break;
            case 13: // SELECT_YESNO: player + 4
                skipBytes(5);
                break;
            case 14: // SELECT_OPTION: player + cnt + cnt*4
                buf.get();
                int oc = buf.get() & 0xFF;
                skipBytes(oc * 4);
                break;
            case 15: case 20: // SELECT_CARD / SELECT_TRIBUTE: player + 3 + cnt*8
                buf.get();
                skipBytes(3);
                int cc = buf.get() & 0xFF;
                skipBytes(cc * 8);
                break;
            case 16: // SELECT_CHAIN: player + cnt + 9 + cnt*14
                buf.get();
                int chc = buf.get() & 0xFF;
                skipBytes(9 + chc * 14);
                break;
            case 17: // SORT_CHAIN（C++ 回放未列，按 duelclient 消息长 player + cnt*7）
                buf.get();
                int scC = buf.get() & 0xFF;
                skipBytes(scC * 7);
                break;
            case 18: case 19: case 24: // PLACE / POSITION / DISFIELD: player + 5
                skipBytes(6);
                break;
            case 22: // SELECT_COUNTER: player + 4 + cnt*9
                buf.get();
                skipBytes(4);
                int ctr = buf.get() & 0xFF;
                skipBytes(ctr * 9);
                break;
            case 23: { // SELECT_SUM: sumtype + player + 6 + 两组 cnt*11
                buf.get();
                buf.get();
                skipBytes(6);
                int s1 = buf.get() & 0xFF;
                skipBytes(s1 * 11);
                int s2 = buf.get() & 0xFF;
                skipBytes(s2 * 11);
                break;
            }
            case 25: // SORT_CARD: player + cnt + cnt*7
                buf.get();
                int scc = buf.get() & 0xFF;
                skipBytes(scc * 7);
                break;
            case 26: { // SELECT_UNSELECT_CARD: player + 4 + 两组 cnt*8
                buf.get();
                skipBytes(4);
                int u1 = buf.get() & 0xFF;
                skipBytes(u1 * 8);
                int u2 = buf.get() & 0xFF;
                skipBytes(u2 * 8);
                break;
            }
            case 132: // ROCK_PAPER_SCISSORS: player
                skipBytes(1);
                break;
            case 140: case 141: // ANNOUNCE_RACE / ATTRIB: player + 5
                skipBytes(6);
                break;
            case 142: case 143: // ANNOUNCE_CARD / NUMBER: player + cnt + cnt*4
                buf.get();
                int ac = buf.get() & 0xFF;
                skipBytes(ac * 4);
                break;
            default:
                Log.w(TAG, "Unhandled select msg: " + msgType);
                return false;
        }
        return feedRecordedResponse();
    }

    /** 从响应记录流读 [uint8 len][data] 并 set_responseb（对齐 ReadReplayResponse）。 */
    private boolean feedRecordedResponse() {
        ByteBuffer rp = replayBuffer;
        if (rp == null || pduel == 0L || !rp.hasRemaining()) {
            lastErrorMessage = "录像响应记录提前耗尽（步数=" + currentStep + "）";
            Log.w(TAG, "响应记录提前耗尽，结束回放");
            return false;
        }
        int len = rp.get() & 0xFF;
        if (len > rp.remaining()) {
            len = rp.remaining();
        }
        // set_responseb 需指向引擎可读的完整响应缓冲（SIZE_RETURN_VALUE=256，同 ServerDuel）
        byte[] resb = new byte[256];
        rp.get(resb, 0, len);
        OcgDuelEngine.setResponseB(pduel, resb);
        return true;
    }

    private void skipConfirm(int msgType, ByteBuffer buf) {
        if (msgType == 30) { // CONFIRM_DECKTOP
            buf.get(); int cnt = buf.get() & 0xFF;
            skipBytes(cnt * 7);
        } else if (msgType == 42) { // CONFIRM_EXTRATOP
            buf.get(); int cnt = buf.get() & 0xFF;
            skipBytes(cnt * 7);
        } else { // CONFIRM_CARDS (31)
            buf.get(); buf.get();
            int cnt = buf.get() & 0xFF;
            skipBytes(cnt * 7);
        }
    }

    private void handleUpdate(int msgType, ByteBuffer buf) {
        int player = buf.get() & 0xFF;
        int location = buf.get() & 0xFF;
        if (msgType == 6) { // UPDATE_DATA
            onUpdateData(player, location, buf);
        } else { // UPDATE_CARD
            int seq = buf.get() & 0xFF;
            onUpdateCard(player, location, seq, buf);
        }
    }

    private ByteBuffer createSubBuffer(int size) {
        return ByteBuffer.allocate(0).order(ByteOrder.LITTLE_ENDIAN);
    }

    // === 引擎查询刷新卡面（对齐 replay_mode.cpp ReplayRefresh 全家与 ReplayReload L862-917）===
    // 录像消息流由引擎重跑产生，但显示卡片（code/属性/攻守等）不在消息体内，
    // 必须像 C++ 一样在阶段/召唤/连锁/移动等节点用 query_field_card/query_card
    // 以特定 flag 重查引擎并刷进 GameField，否则画面永远只有卡背。
    private static final int REFRESH_FLAG_ALL = 0xf81fff;   // ReplayRefresh/ReplayRefreshSingle 默认
    private static final int REFRESH_FLAG_ZONE = 0x181fff;  // Deck/Extra/Grave 默认
    private static final int RELOAD_FLAG = 0xffdfff;        // ReplayReload

    /** 对齐 ReloadLocation：query_field_card 整区域重查后按 UpdateFieldCard 格式应用 */
    private void reloadLocation(int player, int location, int flag) {
        if (pduel == 0L) return;
        byte[] blocks = OcgDuelEngine.queryFieldCard(pduel, player, location, flag, 0);
        if (blocks == null || blocks.length == 0) return;
        applyFieldQuery(player & 1, location, blocks);
    }

    /** 对齐 ClientField::UpdateFieldCard：blocks 为 [int32 clen(含自身)][int32 flag][...] * N，
     *  怪兽/魔陷区为固定槽位列表（空位也有 clen=4 条目），与 LAN 客户端 parseUpdateData 同格式 */
    private void applyFieldQuery(int player, int location, byte[] blocks) {
        java.util.List<GameField.ClientCard> list = field.players[player].getLocationList(location);
        if (list == null) return;
        ByteBuffer data = ByteBuffer.wrap(blocks).order(ByteOrder.LITTLE_ENDIAN);
        boolean fixedSlots = (location == OcgDuelEngine.LOCATION_MZONE
                || location == OcgDuelEngine.LOCATION_SZONE);
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

    /** 对齐 ReplayRefresh(flag=0xf81fff)：双方 MZONE/SZONE/HAND 六区域重查 */
    private void replayRefresh() {
        if (pduel == 0L) return;
        reloadLocation(0, OcgDuelEngine.LOCATION_MZONE, REFRESH_FLAG_ALL);
        reloadLocation(1, OcgDuelEngine.LOCATION_MZONE, REFRESH_FLAG_ALL);
        reloadLocation(0, OcgDuelEngine.LOCATION_SZONE, REFRESH_FLAG_ALL);
        reloadLocation(1, OcgDuelEngine.LOCATION_SZONE, REFRESH_FLAG_ALL);
        reloadLocation(0, OcgDuelEngine.LOCATION_HAND, REFRESH_FLAG_ALL);
        reloadLocation(1, OcgDuelEngine.LOCATION_HAND, REFRESH_FLAG_ALL);
        notifyField();
    }

    /** 对齐 ReplayRefreshDeck（flag=0x181fff） */
    private void refreshDeck(int player) {
        reloadLocation(player & 1, OcgDuelEngine.LOCATION_DECK, REFRESH_FLAG_ZONE);
        notifyField();
    }

    /** 对齐 ReplayRefreshExtra（flag=0x181fff） */
    private void refreshExtra(int player) {
        reloadLocation(player & 1, OcgDuelEngine.LOCATION_EXTRA, REFRESH_FLAG_ZONE);
        notifyField();
    }

    /** 对齐 ReplayRefreshGrave（flag=0x181fff） */
    private void refreshGrave(int player) {
        reloadLocation(player & 1, OcgDuelEngine.LOCATION_GRAVE, REFRESH_FLAG_ZONE);
        notifyField();
    }

    /** 对齐 ReplayRefreshSingle(flag=0xf81fff)：query_card 单卡重查 + UpdateCard 格式应用 */
    private void refreshSingle(int player, int location, int sequence, int flag) {
        if (pduel == 0L) return;
        byte[] q = OcgDuelEngine.queryCard(pduel, player, location, sequence, flag, 0);
        if (q == null || q.length < 12) return;
        ByteBuffer data = ByteBuffer.wrap(q).order(ByteOrder.LITTLE_ENDIAN);
        int len = data.getInt();
        if (len <= 8) return;
        GameField.ClientCard card = field.getCard(player & 1, location, sequence);
        if (card == null) return;
        ByteBuffer sub = data.slice().order(ByteOrder.LITTLE_ENDIAN);
        sub.limit(Math.min(sub.limit(), len - 4));
        card.updateQuery(sub);
    }

    /** 对齐 ReplayReload：双方七区域 flag=0xffdfff 全量重查 + RefreshAllCards */
    private void replayReload() {
        if (pduel == 0L) return;
        for (int p = 0; p < 2; p++) {
            reloadLocation(p, OcgDuelEngine.LOCATION_MZONE, RELOAD_FLAG);
            reloadLocation(p, OcgDuelEngine.LOCATION_SZONE, RELOAD_FLAG);
            reloadLocation(p, OcgDuelEngine.LOCATION_HAND, RELOAD_FLAG);
            reloadLocation(p, OcgDuelEngine.LOCATION_DECK, RELOAD_FLAG);
            reloadLocation(p, OcgDuelEngine.LOCATION_EXTRA, RELOAD_FLAG);
            reloadLocation(p, OcgDuelEngine.LOCATION_GRAVE, RELOAD_FLAG);
            reloadLocation(p, OcgDuelEngine.LOCATION_REMOVED, RELOAD_FLAG);
        }
        field.refreshAllCards();
        notifyField();
    }

    // === Control ===

    public void pause() {
        isPaused = true;
        setState(ReplayState.PAUSED);
    }

    public void resume() {
        isPaused = false;
        setState(ReplayState.PLAYING);
    }

    public void stop() {
        isRunning = false;
        isPaused = false;
        if (replayThread != null) {
            replayThread.interrupt();
        }
        setState(ReplayState.FINISHED);
    }

    public void skipAhead() {
        skipForward = true;
        new Thread(() -> {
            try { Thread.sleep(100); } catch (InterruptedException e) { /* */ }
            skipForward = false;
        }).start();
    }

    public void undo() {
        if (skipStep > 0 || currentStep == 0) {
            return;
        }
        // 回到上一步：捕获当前步-1 作为重放落点，从头重放到该步后暂停
        restartTargetStep = currentStep - 1;
        isRestarting = true;
        resume();
    }

    public void swapField() {
        if (isPaused) {
            performSwapField();
        } else {
            isSwapping = true;
        }
    }

    public void restart() {
        restartTargetStep = 0;
        isRestarting = true;
        resume();
    }

    public int getCurrentStep() { return currentStep; }
    public int getTotalSteps() { return totalSteps; }
    public boolean isSkipping() { return isSkipping; }

    // === MessageHandler (for any forwarded messages) ===

    @Override public void onRetry() {}
    @Override public void onHint(int type, int player, int data) {
        mainHandler.post(() -> { if (listener != null) listener.onReplayHintMessage("提示: " + data); });
    }
    @Override public void onWaiting() {}
    @Override public void onStart(int playerType, int duelRule, int lp0, int lp1,
                                  int deck0, int extra0, int deck1, int extra1) {
        field.clear();
        field.dInfo.duelRule = duelRule;
        field.players[0].lp = lp0;
        field.players[1].lp = lp1;
        field.dInfo.startLp = Math.max(lp0, lp1);
        field.dInfo.lp[0] = lp0;
        field.dInfo.lp[1] = lp1;
        field.initial(0, deck0, extra0, 0);
        field.initial(1, deck1, extra1, 0);
        soundManager.playBGM(SoundManager.BGM.DUEL);
        mainHandler.post(() -> { if (listener != null) listener.onReplayFieldChanged(); });
    }
    @Override public void onWin(int player, int reason) {
        soundManager.stopBGM();
        // 直接把 MSG_WIN 的胜者与胜利原因透传给 UI，由阶段文字（case 101）显示胜负 + !victory 原因
        mainHandler.post(() -> { if (listener != null) listener.onReplayFinished(player, reason); });
    }
    @Override public void onUpdateData(int player, int location, ByteBuffer data) {
        mainHandler.post(() -> { if (listener != null) listener.onReplayFieldChanged(); });
    }
    @Override public void onUpdateCard(int player, int location, int sequence, ByteBuffer data) {
        mainHandler.post(() -> { if (listener != null) listener.onReplayFieldChanged(); });
    }
    @Override public void onRequestDeck(int player) {}
    @Override public void onSelectBattleCmd(ByteBuffer data) {}
    @Override public void onSelectIdleCmd(ByteBuffer data) {}
    @Override public void onSelectEffectYn(ByteBuffer data) {}
    @Override public void onSelectYesNo(ByteBuffer data) {}
    @Override public void onSelectOption(ByteBuffer data) {}
    @Override public void onSelectCard(ByteBuffer data) {}
    @Override public void onSelectChain(ByteBuffer data) {}
    @Override public void onSelectPlace(int player, int count, int fieldMask) {}
    @Override public void onSelectPosition(int player, int code, int positions) {}
    @Override public void onSelectTribute(ByteBuffer data) {}
    @Override public void onSortChain(ByteBuffer data) {}
    @Override public void onSelectCounter(ByteBuffer data) {}
    @Override public void onSelectSum(ByteBuffer data) {}
    @Override public void onSelectDisfield(int player, int count, int fieldMask) {}
    @Override public void onSortCard(ByteBuffer data) {}
    @Override public void onSelectUnselectCard(ByteBuffer data) {}
    @Override public void onConfirmDecktop(int player, int count, ByteBuffer data) {}

    @Override public void onConfirmCards(int player, int skipPanel, int count, ByteBuffer data) {}
    @Override public void onShuffleDeck(int player) { soundManager.playSoundEffect(SoundManager.SFX.SHUFFLE); notifyField(); }
    @Override public void onShuffleHand(int player) { soundManager.playSoundEffect(SoundManager.SFX.SHUFFLE); notifyField(); }
    @Override public void onRefreshDeck(int player) { notifyField(); }
    @Override public void onSwapGraveDeck(int player) { notifyField(); }
    @Override public void onShuffleSetCard(int player, int count, ByteBuffer data) { notifyField(); }
    @Override public void onReverseDeck(int player) { notifyField(); }
    @Override public void onDeckTop(int player, int code) { notifyField(); }
    @Override public void onNewTurn(int player) {
        field.currentPlayer = player;
        field.turnCount++;
        soundManager.playSoundEffect(SoundManager.SFX.NEXT_TURN);
        notifyField();
    }
    @Override public void onNewPhase(int phase) {
        field.currentPhase = phase;
        soundManager.playSoundEffect(SoundManager.SFX.PHASE);
        mainHandler.post(() -> { if (listener != null) listener.onReplayPhaseChanged(phase); });
    }
    @Override public void onMove(int code, int oc, int ol, int os, int opos, int nc, int nl, int ns, int pos, int reason) {
        boolean oldOv = (ol & 0x80) != 0, newOv = (nl & 0x80) != 0;
        if (newOv && !oldOv) {
            GameField.ClientCard card = field.getCard(oc, ol & 0x7f, os);
            if (card == null) card = new GameField.ClientCard();
            if (code != 0) card.code = code;
            card.position = pos;
            // 宿主按消息 loc 字节动态定位（duelclient.cpp L3069/L3097），仅宿主在怪兽区时跟动画
            GameField.ClientCard olcard = field.attachOverlayMaterial(card, oc, ol & 0x7f, os, nc, nl & 0x7f, ns);
            if (olcard != null && olcard.location == 0x04) {
                field.moveCardAnimated(card, 1);
            }
        } else if (oldOv && !newOv) {
            GameField.ClientCard card = field.detachOverlayMaterial(oc, ol & 0x7f, os, opos, nc, nl & 0x7f, ns, pos);
            if (card != null) {
                if (code != 0) card.code = code;
                field.moveCardAnimated(card, 1);
            }
        } else {
            GameField.ClientCard card = field.getCard(oc, ol, os);
            if (card == null) card = new GameField.ClientCard();
            card.code = code;
            card.position = pos;
            field.removeCard(oc, ol, os);
            field.addCard(nc, nl, ns, card);
        }
        soundManager.playSoundEffect(SoundManager.SFX.SUMMON);
        notifyField();
    }
    @Override public void onPosChange(int code, int ctrl, int loc, int seq, int oldPos, int newPos) {
        GameField.ClientCard card = field.getCard(ctrl, loc, seq);
        if (card != null) card.position = newPos;
        notifyField();
    }
    @Override public void onSet(int code, int ctrl, int loc, int seq) {
        GameField.ClientCard card = new GameField.ClientCard();
        card.code = code; card.position = 0x2;
        field.addCard(ctrl, loc, seq, card);
        notifyField();
    }
    @Override public void onSwap(int c1c, int c1l, int c1s, int c2c, int c2l, int c2s) {
        GameField.ClientCard c1 = field.getCard(c1c, c1l, c1s);
        GameField.ClientCard c2 = field.getCard(c2c, c2l, c2s);
        field.addCard(c1c, c1l, c1s, c2);
        field.addCard(c2c, c2l, c2s, c1);
        notifyField();
    }
    @Override public void onFieldDisabled(int disabledMask) {}
    @Override public void onSummoning(int code, int ctrl, int loc, int seq) { soundManager.playSoundEffect(SoundManager.SFX.SUMMON); }
    @Override public void onSummoned() { notifyField(); }
    @Override public void onSpSummoning(int code, int ctrl, int loc, int seq) { soundManager.playSoundEffect(SoundManager.SFX.SPECIAL_SUMMON); }
    @Override public void onSpSummoned() { notifyField(); }
    @Override public void onFlipSummoning(int code, int ctrl, int loc, int seq) { soundManager.playSoundEffect(SoundManager.SFX.FLIP); }
    @Override public void onFlipSummoned() { notifyField(); }

    @Override
    public void onChaining(int code, int pcc, int pcl, int pcs, int subs, int cc, int cl, int cs, int desc) {
        soundManager.playSoundEffect(SoundManager.SFX.ACTIVATE);
        // duelclient.cpp MSG_CHAINING L3345/L3366-3371：录像同样维护 current_chain，
        // 使卡片列表的「在连锁%d发动 / 被连锁%d的[%ls]选择为对象」状态标签在回放中一致
        field.currentChain = new GameField.ChainInfo();
        field.currentChain.chainCard = field.getCard(pcc & 1, pcl, pcs, subs);
        field.currentChain.code = code;
        field.currentChain.desc = desc;
        field.currentChain.controler = cc & 1;
        field.currentChain.location = cl;
        field.currentChain.sequence = cs;
    }

    @Override public void onChained(int chainCount) {
        if (field.currentChain != null && !field.chains.contains(field.currentChain)) {
            field.chains.add(field.currentChain);
        }
        notifyField();
    }
    @Override public void onChainSolving(int chainCount) {}
    @Override public void onChainSolved(int chainCount) { notifyField(); }
    @Override public void onChainEnd() {
        // duelclient.cpp MSG_CHAIN_END L3436-3442：逐链清 is_showchaintarget 后再 clear
        for (GameField.ChainInfo ch : field.chains) {
            for (GameField.ClientCard t : ch.targets) t.is_showchaintarget = false;
            if (ch.chainCard != null) ch.chainCard.is_showchaintarget = false;
        }
        field.chains.clear();
        field.currentChain = new GameField.ChainInfo();
        notifyField();
    }
    @Override public void onChainNegated(int chainCount) { soundManager.playSoundEffect(SoundManager.SFX.NEGATE); }
    @Override public void onChainDisabled(int chainCount) { soundManager.playSoundEffect(SoundManager.SFX.NEGATE); }
    @Override public void onDraw(int player, int count, int[] codes) {
        for (int i = 0; i < count; i++) {
            GameField.ClientCard pcard = field.getCard(player, 0x01, field.getCardCount(player, 0x01) - 1 - i);
            if (pcard != null && (!field.deckReversed || codes[i] != 0)) pcard.setCode(codes[i] & 0x7fffffff);
        }
        for (int i = 0; i < count; i++) {
            GameField.ClientCard pcard = field.removeCard(player, 0x01, field.getCardCount(player, 0x01) - 1);
            if (pcard == null) {
                pcard = new GameField.ClientCard();
                pcard.owner = player;
                pcard.controler = player;
                if (i < codes.length) pcard.setCode(codes[i] & 0x7fffffff);
            }
            field.addCard(player, 0x02, 0, pcard);
            for (GameField.ClientCard hc : field.players[player].hand) {
                if (hc != null) field.moveCardAnimated(hc, 10);
            }
        }
        soundManager.playSoundEffect(SoundManager.SFX.DRAW);
        notifyField();
    }
    @Override public void onDamage(int player, int amount) {
        field.players[player].lp -= amount;
        if (field.players[player].lp < 0) field.players[player].lp = 0;
        field.startLpChange(player, field.players[player].lp, 0xFFFF0000, "-" + amount, true);
        soundManager.playSoundEffect(SoundManager.SFX.DAMAGE);
        mainHandler.post(() -> { if (listener != null) listener.onReplayPlayerInfoUpdated(player); });
    }
    @Override public void onRecover(int player, int amount) {
        field.players[player].lp += amount;
        field.startLpChange(player, field.players[player].lp, 0xFF00FF00, "+" + amount, true);
        soundManager.playSoundEffect(SoundManager.SFX.RECOVER);
        mainHandler.post(() -> { if (listener != null) listener.onReplayPlayerInfoUpdated(player); });
    }
    @Override public void onEquip(int ec, int ecl, int el, int es, int tc, int tl, int ts) {
        // duelclient.cpp MSG_EQUIP：录像视角不本地化，直接按 ctrl&1 维护装备双向关系
        GameField.ClientCard pc1 = field.getCard(ecl & 1, el, es);
        GameField.ClientCard pc2 = field.getCard(tc & 1, tl, ts);
        if (pc1 != null && pc2 != null) {
            if (pc1.equipTarget != null) {
                pc1.is_showequip = false;
                pc1.equipTarget.is_showequip = false;
                pc1.equipTarget.equipped.remove(pc1);
            }
            pc1.equipCard = pc2;
            pc1.equipTarget = pc2;
            if (!pc2.equipped.contains(pc1)) pc2.equipped.add(pc1);
        }
        notifyField();
    }
    @Override public void onLpUpdate(int player, int lp) {
        field.players[player].lp = lp;
        field.startLpChange(player, lp, 0, null, false);
        mainHandler.post(() -> { if (listener != null) listener.onReplayPlayerInfoUpdated(player); });
    }
    @Override public void onUnequip(int ctrl, int loc, int seq) {
        GameField.ClientCard card = field.getCard(ctrl & 1, loc, seq);
        if (card != null) {
            if (card.equipTarget != null) {
                card.equipTarget.equipped.remove(card);
                card.equipTarget.is_showequip = false;
                card.is_showequip = false;
                card.equipTarget = null;
            }
            card.equipCard = null;
        }
        notifyField();
    }
    @Override public void onCardTarget(int c1c, int c1l, int c1s, int c2c, int c2l, int c2s) {
        // duelclient.cpp MSG_CARD_TARGET L3708-3709：双向登记 cardTarget / ownerTarget
        GameField.ClientCard c1 = field.getCard(c1c & 1, c1l, c1s);
        GameField.ClientCard c2 = field.getCard(c2c & 1, c2l, c2s);
        if (c1 != null && c2 != null) {
            if (!c1.targetCards.contains(c2)) c1.targetCards.add(c2);
            if (!c2.ownerTarget.contains(c1)) c2.ownerTarget.add(c1);
        }
        notifyField();
    }
    @Override public void onCancelTarget(int c1c, int c1l, int c1s, int c2c, int c2l, int c2s) {
        GameField.ClientCard c1 = field.getCard(c1c & 1, c1l, c1s);
        GameField.ClientCard c2 = field.getCard(c2c & 1, c2l, c2s);
        if (c1 != null && c2 != null) {
            c1.targetCards.remove(c2);
            c2.ownerTarget.remove(c1);
            c2.is_showtarget = false;
        }
        notifyField();
    }
    @Override public void onPayLpCost(int player, int cost) {
        field.players[player].lp -= cost;
        if (field.players[player].lp < 0) field.players[player].lp = 0;
        field.startLpChange(player, field.players[player].lp, 0, null, false);
        mainHandler.post(() -> { if (listener != null) listener.onReplayPlayerInfoUpdated(player); });
    }
    @Override public void onAddCounter(int type, int ctrl, int loc, int seq, int count) {}
    @Override public void onRemoveCounter(int type, int ctrl, int loc, int seq, int count) {}
    @Override public void onAttack(int ac, int al, int as, int dc, int dl, int ds) { soundManager.playSoundEffect(SoundManager.SFX.ATTACK); }
    @Override public void onBattle(int aa, boolean ap, int da, boolean dp) {}
    @Override public void onAttackDisabled() {}
    @Override public void onDamageStepStart() {}
    @Override public void onDamageStepEnd() { notifyField(); }
    @Override public void onMissedEffect(int code, int ctrl, int loc, int seq, int effectId) {}
    @Override public void onTossCoin(int player, int count, ByteBuffer results) { soundManager.playSoundEffect(SoundManager.SFX.COIN); }
    @Override public void onTossDice(int player, int count, ByteBuffer results) { soundManager.playSoundEffect(SoundManager.SFX.DICE); }
    @Override public void onAnnounceRace(int player, int count, int availableRaces) {}
    @Override public void onAnnounceAttrib(int player, int count, int availableAttribs) {}
    @Override public void onAnnounceCard(int player, ByteBuffer data) {}
    @Override public void onAnnounceNumber(int player, ByteBuffer data) {}
    @Override public void onCardHint(int player, int location, int sequence, int hintType, int value) {
        // duelclient.cpp MSG_CARD_HINT L4094-4109：desc_hints 计数维护
        field.applyCardHint(player & 1, location, sequence, hintType, value);
    }
    @Override public void onBecomeTarget(int count, ByteBuffer data) {
        if (data == null || count <= 0) { notifyField(); return; }
        for (int i = 0; i < count && data.remaining() >= 4; i++) {
            int ctrl = data.get() & 0xFF;
            int loc = data.get() & 0xFF;
            int seq = data.get() & 0xFF;
            data.get(); // subseq：C++ 读取即弃
            field.addChainTarget(ctrl & 1, loc, seq);
        }
        notifyField();
    }
    @Override public void onTagSwap(int player) { notifyField(); }
    @Override public void onReloadField() { notifyField(); }
    @Override public void onAiName(String name) {}
    @Override public void onShowHint(String hint) {
        mainHandler.post(() -> { if (listener != null) listener.onReplayHintMessage(hint); });
    }
    @Override public void onMatchKill(int code) {}
    @Override public void onCustomMsg(String msg) {}
    @Override public void onDuelWinner(int player, int reason) { onWin(player, reason); }

    private void notifyField() {
        mainHandler.post(() -> { if (listener != null) listener.onReplayFieldChanged(); });
    }
}
