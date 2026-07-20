package cn.garymb.ygomobile.game;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import cn.garymb.ygomobile.audio.SoundManager;
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
        void onReplayFinished(String result);
    }

    private ReplayState state = ReplayState.IDLE;
    private final GameField field;
    private final SoundManager soundManager;
    private ReplayListener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private ReplayReader.ReplayData replayData;
    private ByteBuffer replayBuffer;
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
    private boolean isSkipping = false;
    private byte[] originalReplayBytes = null;

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
                setState(ReplayState.ERROR);
                mainHandler.post(() -> {
                    if (listener != null) listener.onReplayHintMessage("无法加载录像文件");
                });
                return;
            }

            originalReplayBytes = replayData.replayBuffer.array().clone();

            field.clear();
            setupInitialField();

            replayBuffer = replayData.replayBuffer;
            replayBuffer.order(ByteOrder.LITTLE_ENDIAN);

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
    }

    private void replayLoop(int startTurn) {
        try {
            while (isRunning && replayBuffer != null && replayBuffer.remaining() > 0) {
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

                if (replayBuffer.remaining() < 1) break;
                int msgType = replayBuffer.get() & 0xFF;

                boolean pauseable = isPauseable(msgType);
                
                if (!processMessage(msgType)) {
                    break;
                }

                if (pauseable && skipStep > 0) {
                    skipStep--;
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
                    try { Thread.sleep(800); } catch (InterruptedException e) { break; }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Replay loop error", e);
        }

        isRunning = false;
        setState(ReplayState.FINISHED);
        mainHandler.post(() -> {
            soundManager.stopBGM();
            if (listener != null) listener.onReplayFinished("录像回放结束");
        });
    }

    private boolean isPauseable(int msgType) {
        switch (msgType) {
            case 52: // MSG_SET
            case 54: // MSG_FIELD_DISABLED
            case 60: // MSG_SUMMONING
            case 61: // MSG_SUMMONED
            case 62: // MSG_SPSUMMONING
            case 63: // MSG_SPSUMMONED
            case 64: // MSG_FLIPSUMMONING
            case 65: // MSG_FLIPSUMMONED
            case 70: // MSG_CHAINING
            case 71: // MSG_CHAINED
            case 72: // MSG_CHAIN_SOLVING
            case 73: // MSG_CHAIN_SOLVED
            case 74: // MSG_CHAIN_END
            case 75: // MSG_CHAIN_NEGATED
            case 76: // MSG_CHAIN_DISABLED
                return false;
            default:
                return true;
        }
    }

    private void performRestart() {
        isRestarting = false;
        isSkipping = true;
        
        replayBuffer = ByteBuffer.wrap(originalReplayBytes).order(ByteOrder.LITTLE_ENDIAN);
        
        field.clear();
        setupInitialField();
        
        currentStep = 0;
        skipStep = Math.max(0, currentStep - 1);
        
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

    private boolean processMessage(int msgType) {
        ByteBuffer buf = replayBuffer;
        if (buf == null || buf.remaining() < 0) return false;

        try {
            switch (msgType) {
                case 1: // MSG_RETRY
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
                    if (buf.remaining() < 6) return false;
                    int lp = buf.getInt();
                    int startHand = buf.get() & 0xFF;
                    int drawCount = buf.get() & 0xFF;
                    onStart(lp, startHand, drawCount);
                    break;
                }

                case 5: { // MSG_WIN
                    if (buf.remaining() < 2) return false;
                    int winner = buf.get() & 0xFF;
                    int reason = buf.get() & 0xFF;
                    onWin(winner, reason);
                    return false;
                }

                case 6: // MSG_UPDATE_DATA
                case 7: // MSG_UPDATE_CARD
                    handleUpdate(msgType);
                    break;

                case 8: // MSG_REQUEST_DECK
                    buf.get();
                    break;

                case 10: // MSG_SELECT_BATTLECMD
                case 11: // MSG_SELECT_IDLECMD
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
                    skipReplayResponse(msgType);
                    break;

                case 30: // MSG_CONFIRM_DECKTOP
                case 31: // MSG_CONFIRM_CARDS
                case 42: // MSG_CONFIRM_EXTRATOP
                    skipConfirm(msgType);
                    break;

                case 32: // MSG_SHUFFLE_DECK
                    if (buf.remaining() < 1) return false;
                    int sdPlayer = buf.get() & 0xFF;
                    onShuffleDeck(sdPlayer);
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
                    break;

                case 50: // MSG_MOVE
                    if (buf.remaining() < 16) return false;
                    int mCode = buf.getInt();
                    int mOldCtrl = buf.get() & 0xFF;
                    int mOldLoc = buf.get() & 0xFF;
                    int mOldSeq = buf.get() & 0xFF;
                    buf.get(); // old pos
                    int mNewCtrl = buf.get() & 0xFF;
                    int mNewLoc = buf.get() & 0xFF;
                    int mNewSeq = buf.get() & 0xFF;
                    int mPos = buf.get() & 0xFF;
                    int mReason = buf.getInt();
                    onMove(mCode, mOldCtrl, mOldLoc, mOldSeq, mNewCtrl, mNewLoc, mNewSeq, mPos, mReason);
                    break;

                case 53: // MSG_POS_CHANGE
                    if (buf.remaining() < 9) return false;
                    int pcCode = buf.getInt();
                    int pcCtrl = buf.get() & 0xFF;
                    int pcLoc = buf.get() & 0xFF;
                    int pcSeq = buf.get() & 0xFF;
                    int pcOld = buf.get() & 0xFF;
                    int pcNew = buf.get() & 0xFF;
                    onPosChange(pcCode, pcCtrl, pcLoc, pcSeq, pcOld, pcNew);
                    break;

                case 54: // MSG_SET
                    if (buf.remaining() < 8) return false;
                    int setCode = buf.getInt();
                    int setCtrl = buf.get() & 0xFF;
                    int setLoc = buf.get() & 0xFF;
                    int setSeq = buf.get() & 0xFF;
                    buf.get(); // padding
                    buf.get();
                    buf.get();
                    buf.get();
                    onSet(setCode, setCtrl, setLoc, setSeq);
                    break;

                case 55: // MSG_SWAP
                    if (buf.remaining() < 16) return false;
                    buf.getInt(); int sw1c = buf.get() & 0xFF; int sw1l = buf.get() & 0xFF; int sw1s = buf.get() & 0xFF; buf.get();
                    buf.getInt(); int sw2c = buf.get() & 0xFF; int sw2l = buf.get() & 0xFF; int sw2s = buf.get() & 0xFF; buf.get();
                    onSwap(sw1c, sw1l, sw1s, sw2c, sw2l, sw2s);
                    break;

                case 56: // MSG_FIELD_DISABLED
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
                case 61: onSummoned(); break;
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
                case 63: onSpSummoned(); break;
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
                case 65: onFlipSummoned(); break;

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
                case 71: skipBytes(1); onChained(0); break;
                case 72: skipBytes(1); onChainSolving(0); break;
                case 73: skipBytes(1); onChainSolved(0); break;
                case 74: onChainEnd(); break;
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

                case 90: // MSG_DRAW
                    if (buf.remaining() < 2) return false;
                    int dPlayer = buf.get() & 0xFF;
                    int dCount = buf.get() & 0xFF;
                    skipBytes(dCount * 4);
                    onDraw(dPlayer, dCount);
                    break;

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

                case 93: // MSG_EQUIP
                    if (buf.remaining() < 8) return false;
                    buf.getInt();
                    int eqCtrl = buf.get() & 0xFF; int eqLoc = buf.get() & 0xFF; int eqSeq = buf.get() & 0xFF;
                    int tCtrl = buf.get() & 0xFF; int tLoc = buf.get() & 0xFF; int tSeq = buf.get() & 0xFF;
                    onEquip(0, eqCtrl, eqLoc, eqSeq, tCtrl, tLoc, tSeq);
                    break;

                case 94: // MSG_LPUPDATE
                    if (buf.remaining() < 5) return false;
                    int lpPlayer = buf.get() & 0xFF;
                    int lpVal = buf.getInt();
                    onLpUpdate(lpPlayer, lpVal);
                    break;

                case 95: skipBytes(4); onUnequip(0, 0, 0); break;
                case 96: case 97: skipBytes(8); break; // CARD_TARGET / CANCEL_TARGET

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
                case 113: onDamageStepStart(); break; // MSG_DAMAGE_STEP_START
                case 114: onDamageStepEnd(); break; // MSG_DAMAGE_STEP_END

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

                case 132: // MSG_ROCK_PAPER_SCISSORS
                    skipBytes(1);
                    break;

                case 133: // MSG_HAND_RES
                    skipBytes(1);
                    break;

                case 140: case 141: skipBytes(6); break; // ANNOUNCE_RACE / ATTRIB
                case 142: case 143: { // ANNOUNCE_CARD / NUMBER
                    int anP = buf.get() & 0xFF;
                    int anC = buf.get() & 0xFF;
                    skipBytes(anC * 4);
                    break;
                }

                case 160: skipBytes(9); break; // CARD_HINT
                case 165: skipBytes(6); break; // PLAYER_HINT

                case 170: // MSG_MATCH_KILL
                    skipBytes(4);
                    break;

                case 161: { // MSG_TAG_SWAP
                    int tsPlayer = buf.get() & 0xFF;
                    buf.get();
                    int tsMainCount = buf.get() & 0xFF;
                    buf.get();
                    int tsExtraCount = buf.get() & 0xFF;
                    skipBytes(tsMainCount * 4 + tsExtraCount * 4);
                    onTagSwap(tsPlayer);
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
                    break;
                }

                case 163: { // MSG_AI_NAME
                    int aiLen = buf.getShort() & 0xFFFF;
                    if (buf.remaining() < aiLen + 1) return false;
                    byte[] aiBytes = new byte[aiLen];
                    buf.get(aiBytes);
                    buf.get();
                    onAiName(new String(aiBytes, java.nio.charset.StandardCharsets.UTF_8));
                    break;
                }
                case 164: { // MSG_SHOW_HINT
                    int shLen = buf.getShort() & 0xFFFF;
                    if (buf.remaining() < shLen + 1) return false;
                    byte[] shBytes = new byte[shLen];
                    buf.get(shBytes);
                    buf.get();
                    onShowHint(new String(shBytes, java.nio.charset.StandardCharsets.UTF_8));
                    break;
                }

                default:
                    Log.w(TAG, "Unknown replay msg: " + msgType);
                    break;
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing replay msg " + msgType, e);
            return false;
        }

        return true;
    }

    private void skipBytes(int count) {
        if (replayBuffer != null && replayBuffer.remaining() >= count) {
            replayBuffer.position(replayBuffer.position() + count);
        }
    }

    private void skipReplayResponse(int msgType) {
        ByteBuffer buf = replayBuffer;
        switch (msgType) {
            case 10: { // SELECT_BATTLECMD
                buf.get(); int cnt = buf.get() & 0xFF;
                skipBytes(cnt * 11);
                int cnt2 = buf.get() & 0xFF;
                skipBytes(cnt2 * 8 + 2);
                break;
            }
            case 11: { // SELECT_IDLECMD
                buf.get();
                for (int t = 0; t < 5; t++) {
                    int c = buf.get() & 0xFF; skipBytes(c * 7);
                }
                int c6 = buf.get() & 0xFF; skipBytes(c6 * 11 + 3);
                break;
            }
            case 12: buf.get(); skipBytes(12); break; // SELECT_EFFECTYN
            case 13: buf.get(); skipBytes(4); break;  // SELECT_YESNO
            case 14: buf.get(); int oc = buf.get() & 0xFF; skipBytes(oc * 4); break; // SELECT_OPTION
            case 15: case 20: // SELECT_CARD / SELECT_TRIBUTE
                buf.get(); skipBytes(3);
                int cc = buf.get() & 0xFF; skipBytes(cc * 8);
                break;
            case 16: // SELECT_CHAIN
                buf.get(); int chc = buf.get() & 0xFF;
                skipBytes(9 + chc * 14);
                break;
            case 17: // SORT_CHAIN
                buf.get(); int scC = buf.get() & 0xFF; skipBytes(scC * 7);
                break;
            case 18: case 24: buf.get(); skipBytes(5); break; // SELECT_PLACE / DISFIELD
            case 19: buf.get(); skipBytes(5); break; // SELECT_POSITION
            case 22: // SELECT_COUNTER
                buf.get(); skipBytes(4);
                int ccnt = buf.get() & 0xFF; skipBytes(ccnt * 9);
                break;
            case 23: // SELECT_SUM
                buf.get(); buf.get();
                skipBytes(6);
                int sc1 = buf.get() & 0xFF; skipBytes(sc1 * 11);
                int sc2 = buf.get() & 0xFF; skipBytes(sc2 * 11);
                break;
            case 25: // SORT_CARD
                buf.get(); int scc = buf.get() & 0xFF; skipBytes(scc * 7);
                break;
            case 26: { // SELECT_UNSELECT_CARD
                buf.get();
                skipBytes(4);
                int uc1 = buf.get() & 0xFF; skipBytes(uc1 * 8);
                int uc2 = buf.get() & 0xFF; skipBytes(uc2 * 8);
                break;
            }
        }
    }

    private void skipConfirm(int msgType) {
        ByteBuffer buf = replayBuffer;
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

    private void handleUpdate(int msgType) {
        ByteBuffer buf = replayBuffer;
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
    @Override public void onStart(int lp, int startHand, int drawCount) {
        field.players[0].lp = lp;
        field.players[1].lp = lp;
        soundManager.playBGM(SoundManager.BGM.DUEL);
        mainHandler.post(() -> { if (listener != null) listener.onReplayFieldChanged(); });
    }
    @Override public void onWin(int player, int reason) {
        String result;
        if (player == 2) result = "平局";
        else result = "玩家 " + (player + 1) + " 获胜";
        if (reason == 0) result += " (LP归零)";
        else if (reason == 1) result += " (卡组抽完)";
        soundManager.stopBGM();
        String finalResult = result;
        mainHandler.post(() -> { if (listener != null) listener.onReplayFinished(finalResult); });
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
    @Override public void onConfirmDecktop(int player, int count, ByteBuffer data) {}
    @Override public void onConfirmCards(int player, int count, ByteBuffer data) {}
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
    @Override public void onMove(int code, int oc, int ol, int os, int nc, int nl, int ns, int pos, int reason) {
        GameField.ClientCard card = field.getCard(oc, ol, os);
        if (card == null) card = new GameField.ClientCard();
        card.code = code;
        card.position = pos;
        field.removeCard(oc, ol, os);
        field.addCard(nc, nl, ns, card);
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
    @Override public void onFieldDisabled(int ctrl, int loc, int seq) {}
    @Override public void onSummoning(int code, int ctrl, int loc, int seq) { soundManager.playSoundEffect(SoundManager.SFX.SUMMON); }
    @Override public void onSummoned() { notifyField(); }
    @Override public void onSpSummoning(int code, int ctrl, int loc, int seq) { soundManager.playSoundEffect(SoundManager.SFX.SPECIAL_SUMMON); }
    @Override public void onSpSummoned() { notifyField(); }
    @Override public void onFlipSummoning(int code, int ctrl, int loc, int seq) { soundManager.playSoundEffect(SoundManager.SFX.FLIP); }
    @Override public void onFlipSummoned() { notifyField(); }

    @Override
    public void onChaining(int code, int pcc, int pcl, int pcs, int subs, int cc, int cl, int cs, int desc) {
        soundManager.playSoundEffect(SoundManager.SFX.ACTIVATE);
    }

    @Override public void onChained(int chainCount) { notifyField(); }
    @Override public void onChainSolving(int chainCount) {}
    @Override public void onChainSolved(int chainCount) { notifyField(); }
    @Override public void onChainEnd() { notifyField(); }
    @Override public void onChainNegated(int chainCount) { soundManager.playSoundEffect(SoundManager.SFX.NEGATE); }
    @Override public void onChainDisabled(int chainCount) { soundManager.playSoundEffect(SoundManager.SFX.NEGATE); }
    @Override public void onDraw(int player, int count) { soundManager.playSoundEffect(SoundManager.SFX.DRAW); notifyField(); }
    @Override public void onDamage(int player, int amount) {
        field.players[player].lp -= amount;
        if (field.players[player].lp < 0) field.players[player].lp = 0;
        soundManager.playSoundEffect(SoundManager.SFX.DAMAGE);
        mainHandler.post(() -> { if (listener != null) listener.onReplayPlayerInfoUpdated(player); });
    }
    @Override public void onRecover(int player, int amount) {
        field.players[player].lp += amount;
        soundManager.playSoundEffect(SoundManager.SFX.RECOVER);
        mainHandler.post(() -> { if (listener != null) listener.onReplayPlayerInfoUpdated(player); });
    }
    @Override public void onEquip(int ec, int ecl, int el, int es, int tc, int tl, int ts) { notifyField(); }
    @Override public void onLpUpdate(int player, int lp) {
        field.players[player].lp = lp;
        mainHandler.post(() -> { if (listener != null) listener.onReplayPlayerInfoUpdated(player); });
    }
    @Override public void onUnequip(int ctrl, int loc, int seq) { notifyField(); }
    @Override public void onCardTarget(int c1c, int c1l, int c1s, int c2c, int c2l, int c2s) {}
    @Override public void onCancelTarget(int c1c, int c1l, int c1s, int c2c, int c2l, int c2s) {}
    @Override public void onPayLpCost(int player, int cost) {
        field.players[player].lp -= cost;
        if (field.players[player].lp < 0) field.players[player].lp = 0;
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
    @Override public void onCardHint(int type, int data) {}
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
