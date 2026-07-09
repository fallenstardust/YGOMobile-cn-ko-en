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
    private int currentStep = 0;
    private int totalSteps = 0;

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

            field.clear();
            setupInitialField();

            replayBuffer = replayData.replayBuffer;
            replayBuffer.order(ByteOrder.LITTLE_ENDIAN);

            setState(ReplayState.PLAYING);
            isRunning = true;
            mainHandler.post(() -> {
                String info = buildReplayInfo();
                if (listener != null) listener.onReplayHintMessage(info);
            });

            replayThread = new Thread(this::replayLoop, "ReplayThread");
            replayThread.setDaemon(true);
            replayThread.start();
        }, "ReplayLoad").start();
    }

    private String buildReplayInfo() {
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

    private void replayLoop() {
        try {
            while (isRunning && replayBuffer != null && replayBuffer.remaining() > 0) {
                if (isPaused) {
                    try { Thread.sleep(100); } catch (InterruptedException e) { break; }
                    continue;
                }

                if (replayBuffer.remaining() < 1) break;
                int msgType = replayBuffer.get() & 0xFF;

                if (!processMessage(msgType)) {
                    break;
                }

                currentStep++;

                if (!skipForward) {
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

    private boolean processMessage(int msgType) {
        ByteBuffer buf = replayBuffer;
        if (buf == null || buf.remaining() < 0) return false;

        try {
            switch (msgType) {
                case 0: // MSG_RETRY
                    mainHandler.post(() -> {
                        if (listener != null) listener.onReplayHintMessage("录像错误: Retry");
                    });
                    return false;

                case 1: // MSG_HINT
                    if (buf.remaining() < 6) return false;
                    int hintType = buf.get() & 0xFF;
                    int hintPlayer = buf.get() & 0xFF;
                    int hintData = buf.getInt();
                    GameMessageParser.parse(msgType, createSubBuffer(0), this);
                    break;

                case 2: // MSG_WAITING
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

                case 16: // MSG_SELECT_BATTLECMD
                case 17: // MSG_SELECT_IDLECMD
                case 18: // MSG_SELECT_EFFECTYN
                case 19: // MSG_SELECT_YESNO
                case 20: // MSG_SELECT_OPTION
                case 21: // MSG_SELECT_CARD
                case 22: // MSG_SELECT_TRIBUTE
                case 23: // MSG_SELECT_CHAIN
                case 24: // MSG_SELECT_PLACE / DISFIELD
                case 25: // MSG_SELECT_POSITION
                case 26: // MSG_SELECT_COUNTER
                case 27: // MSG_SELECT_SUM
                case 28: // MSG_SORT_CARD
                case 29: // MSG_SORT_CHAIN
                    skipReplayResponse(msgType);
                    break;

                case 30: // MSG_CONFIRM_DECKTOP
                case 31: // MSG_CONFIRM_CARDS
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
                    if (buf.remaining() < 5) return false;
                    int dtPlayer = buf.get() & 0xFF;
                    int dtCode = buf.getInt();
                    onDeckTop(dtPlayer, dtCode);
                    break;

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

                case 51: // MSG_POS_CHANGE
                    if (buf.remaining() < 9) return false;
                    int pcCode = buf.getInt();
                    int pcCtrl = buf.get() & 0xFF;
                    int pcLoc = buf.get() & 0xFF;
                    int pcSeq = buf.get() & 0xFF;
                    int pcOld = buf.get() & 0xFF;
                    int pcNew = buf.get() & 0xFF;
                    onPosChange(pcCode, pcCtrl, pcLoc, pcSeq, pcOld, pcNew);
                    break;

                case 52: // MSG_SET
                    if (buf.remaining() < 8) return false;
                    int setCode = buf.getInt();
                    int setCtrl = buf.get() & 0xFF;
                    int setLoc = buf.get() & 0xFF;
                    int setSeq = buf.get() & 0xFF;
                    buf.getInt(); // padding or extra
                    onSet(setCode, setCtrl, setLoc, setSeq);
                    break;

                case 53: // MSG_SWAP
                    if (buf.remaining() < 16) return false;
                    buf.getInt(); int sw1c = buf.get() & 0xFF; int sw1l = buf.get() & 0xFF; int sw1s = buf.get() & 0xFF;
                    buf.getInt(); int sw2c = buf.get() & 0xFF; int sw2l = buf.get() & 0xFF; int sw2s = buf.get() & 0xFF;
                    onSwap(sw1c, sw1l, sw1s, sw2c, sw2l, sw2s);
                    break;

                case 54: // MSG_FIELD_DISABLED
                    skipBytes(4);
                    break;

                case 60: // MSG_SUMMONING
                    if (buf.remaining() < 8) return false;
                    int sumCode = buf.getInt();
                    int sumCtrl = buf.get() & 0xFF;
                    int sumLoc = buf.get() & 0xFF;
                    int sumSeq = buf.get() & 0xFF;
                    buf.getInt();
                    onSummoning(sumCode, sumCtrl, sumLoc, sumSeq);
                    break;
                case 61: onSummoned(); break;
                case 62: skipBytes(8); onSpSummoning(0,0,0,0); break;
                case 63: onSpSummoned(); break;
                case 64: skipBytes(8); onFlipSummoning(0,0,0,0); break;
                case 65: onFlipSummoned(); break;

                case 70: // MSG_CHAINING
                    if (buf.remaining() < 16) return false;
                    int chCode = buf.getInt();
                    int chCtrl = buf.get() & 0xFF;
                    int chLoc = buf.get() & 0xFF;
                    int chSeq = buf.get() & 0xFF;
                    int chCount = buf.get() & 0xFF;
                    skipBytes(7);
                    onChaining(chCode, chCtrl, chLoc, chSeq, chCount);
                    break;
                case 71: skipBytes(1); onChained(0); break;
                case 72: skipBytes(1); onChainSolving(0); break;
                case 73: skipBytes(1); onChainSolved(0); break;
                case 74: onChainEnd(); break;
                case 75: skipBytes(1); onChainNegated(0); break;
                case 76: skipBytes(1); onChainDisabled(0); break;

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

                case 98: // MSG_PAY_LPCOST
                    if (buf.remaining() < 5) return false;
                    int costPlayer = buf.get() & 0xFF;
                    int costAmt = buf.getInt();
                    onPayLpCost(costPlayer, costAmt);
                    break;

                case 99: case 100: // ADD_COUNTER / REMOVE_COUNTER
                    skipBytes(7);
                    break;

                case 101: // MSG_ATTACK
                    if (buf.remaining() < 8) return false;
                    int aCtrl = buf.get() & 0xFF; int aLoc = buf.get() & 0xFF; int aSeq = buf.get() & 0xFF; buf.get();
                    int defCtrl = buf.get() & 0xFF; int defLoc = buf.get() & 0xFF; int defSeq = buf.get() & 0xFF; buf.get();
                    onAttack(aCtrl, aLoc, aSeq, defCtrl, defLoc, defSeq);
                    break;

                case 102: // MSG_BATTLE
                    skipBytes(26);
                    break;

                case 103: onAttackDisabled(); break; // MSG_ATTACK_DISABLED
                case 104: onDamageStepStart(); break;
                case 105: onDamageStepEnd(); break;

                case 110: skipBytes(8); break; // MSG_MISSED_EFFECT
                case 111: { // MSG_TOSS_COIN
                    int tcP = buf.get() & 0xFF;
                    int tcC = buf.get() & 0xFF;
                    skipBytes(tcC);
                    onTossCoin(tcP, tcC, null);
                    break;
                }
                case 112: { // MSG_TOSS_DICE
                    int tdP = buf.get() & 0xFF;
                    int tdC = buf.get() & 0xFF;
                    skipBytes(tdC);
                    onTossDice(tdP, tdC, null);
                    break;
                }

                case 130: case 131: skipBytes(5); break; // ANNOUNCE_RACE / ATTRIB
                case 132: case 133: { // ANNOUNCE_CARD / NUMBER
                    int anP = buf.get() & 0xFF;
                    int anC = buf.get() & 0xFF;
                    skipBytes(anC * 4);
                    break;
                }

                case 140: skipBytes(9); break; // CARD_HINT
                case 141: skipBytes(6); break; // PLAYER_HINT

                case 160: { // MSG_TAG_SWAP
                    int tsPlayer = buf.get() & 0xFF;
                    buf.get();
                    int tsMainCount = buf.get() & 0xFF;
                    buf.get();
                    int tsExtraCount = buf.get() & 0xFF;
                    skipBytes(tsMainCount * 4 + tsExtraCount * 4);
                    onTagSwap(tsPlayer);
                    break;
                }

                case 161: { // MSG_RELOAD_FIELD
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
            case 16: { // SELECT_BATTLECMD
                buf.get(); int cnt = buf.get() & 0xFF;
                skipBytes(cnt * 11);
                int cnt2 = buf.get() & 0xFF;
                skipBytes(cnt2 * 8 + 2);
                break;
            }
            case 17: { // SELECT_IDLECMD
                buf.get();
                for (int t = 0; t < 5; t++) {
                    int c = buf.get() & 0xFF; skipBytes(c * 7);
                }
                int c6 = buf.get() & 0xFF; skipBytes(c6 * 11 + 3);
                break;
            }
            case 18: buf.get(); skipBytes(12); break; // SELECT_EFFECTYN
            case 19: buf.get(); skipBytes(4); break;  // SELECT_YESNO
            case 20: buf.get(); int oc = buf.get() & 0xFF; skipBytes(oc * 4); break; // SELECT_OPTION
            case 21: case 22: // SELECT_CARD / TRIBUTE
                buf.get(); skipBytes(3);
                int cc = buf.get() & 0xFF; skipBytes(cc * 8);
                break;
            case 23: // SELECT_CHAIN
                buf.get(); int chc = buf.get() & 0xFF;
                skipBytes(9 + chc * 14);
                break;
            case 24: buf.get(); skipBytes(5); break; // SELECT_PLACE/DISFIELD
            case 25: buf.get(); skipBytes(5); break; // SELECT_POSITION
            case 26: // SELECT_COUNTER
                buf.get(); skipBytes(4);
                int ccnt = buf.get() & 0xFF; skipBytes(ccnt * 9);
                break;
            case 27: // SELECT_SUM
                buf.get(); buf.get();
                skipBytes(6);
                int sc1 = buf.get() & 0xFF; skipBytes(sc1 * 11);
                int sc2 = buf.get() & 0xFF; skipBytes(sc2 * 11);
                break;
            case 28: case 29: // SORT_CARD / SORT_CHAIN
                buf.get(); int scc = buf.get() & 0xFF; skipBytes(scc * 7);
                break;
        }
    }

    private void skipConfirm(int msgType) {
        ByteBuffer buf = replayBuffer;
        if (msgType == 30) { // CONFIRM_DECKTOP
            buf.get(); int cnt = buf.get() & 0xFF;
            skipBytes(cnt * 7);
        } else { // CONFIRM_CARDS
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
    @Override public void onChaining(int code, int ctrl, int loc, int seq, int chainCount) { soundManager.playSoundEffect(SoundManager.SFX.ACTIVATE); }
    @Override public void onChained(int code) { notifyField(); }
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
