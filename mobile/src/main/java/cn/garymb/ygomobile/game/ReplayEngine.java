package cn.garymb.ygomobile.game;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.audio.SoundManager;
import cn.garymb.ygomobile.engine.NativeScriptBootstrap;
import cn.garymb.ygomobile.engine.OcgDuelEngine;
import cn.garymb.ygomobile.network.YGOProtocol;
import ocgcore.enums.CardLocation;
import ocgcore.enums.DuelPhase;

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
        /** 录像播放召唤动画（对齐正常决斗 drawspec）：summonType 取 SummonAnimationManager 常量 */
        void onReplaySummonAnimation(int code, int summonType);
        /** 录像播放阶段文字提示（case 101 DrawSpec showText） */
        void onReplayPhaseText(int textCode);
        /** 纯消息模式（MSG 流录像）连锁发动：选卡高亮 + 发动大图（对齐实况 onChainAnimation） */
        void onReplayChainAnimation(int code, int controler, int location, int sequence);
        /** 纯消息模式效果无效/失效：居中卡片 + 无效图标（对齐实况 onNegatedAnimation） */
        void onReplayNegateAnimation(int code);
        /** 回合切换：供顶部信息栏刷新回合数与回合方高亮（对齐实况 onTurnStarted） */
        void onReplayTurnChanged(int turn, int currentPlayer);
    }

    private ReplayState state = ReplayState.IDLE;
    private final GameField field;
    private final SoundManager soundManager;
    /** 实况管线宿主引擎：纯消息（msgMode）回放把切片后的消息投喂给它的全套动画管线；
     *  旧格式引擎重跑路径不依赖。由构造方（ReplayModeDialog/ConnectionManager）经 setEngine 接入 */
    private GameEngine engine;
    private ReplayListener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private ReplayReader.ReplayData replayData;
    /** 响应记录流（uniform 模式消息区全部为 [uint8 len][response] 记录，非消息字节流）。 */
    private ByteBuffer replayBuffer;
    /** 纯消息模式：录像文件内含主机视角 MSG 字节流，不启动 ocgcore 引擎，动画/卡面全由消息驱动 */
    private boolean msgMode;
    /** 纯消息模式的消息流游标缓冲（逐条消费，undo/restart 时重置为 {@link #originalMsgBytes} 副本）。 */
    private ByteBuffer msgStream;
    private byte[] originalMsgBytes;
    /** 动画持闸截止时刻（对齐 GameEngine.animHoldUntilMs）：pauseable 消息后节奏取 max(800ms, 余量)。 */
    private volatile long animHoldUntilMs;
    /** 连锁卡码序列（对齐 DuelEventHandler.chainCodes）：MSG_CHAINING 入列，供 NEGATED/DISABLED 取码。 */
    private final List<Integer> chainCodes = new ArrayList<>();
    /** 当前正在消费的单条引擎消息体缓冲，供 skipBytes 推进游标。 */
    private ByteBuffer msgBuf;
    private long pduel = 0L;
    private volatile boolean replayWinSeen = false;
    private Thread replayThread;
    private volatile boolean isRunning = false;
    private volatile boolean isPaused = false;
    private volatile boolean skipForward = false;
    /** 步进模式：>0 时处理 N 个可暂停消息后自动暂停（对齐 C++ ReplayMode 单步前进） */
    private volatile int stepsRemaining = 0;
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
    /** undo/restart 目标步为 0 时的特殊落点：消费并重投 MSG_START（重建初始场）后即停 */
    private boolean landAtStart = false;
    private byte[] originalResponseBytes = null;
    /** 回放异常终止原因（对齐 replay_mode.cpp "Error occurs." 与 EndDuel 1501 提示），自然结束为 null。 */
    private volatile String lastErrorMessage;

    public String getLastErrorMessage() { return lastErrorMessage; }

    public ReplayEngine(GameField field, SoundManager soundManager) {
        this.field = field;
        this.soundManager = soundManager;
    }

    /** 接入实况管线宿主引擎（msgMode 回放必需；旧格式重跑路径可缺省） */
    public void setEngine(GameEngine engine) {
        this.engine = engine;
    }

    public void setListener(ReplayListener listener) {
        this.listener = listener;
    }

    /** 摘除 UI 监听：重复进入回放时先于 {@link #stop()} 调用，使旧实例剩余回调静默 */
    public void detachListener() {
        this.listener = null;
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

            // 含 MSG 流的纯消息录像完全脱离引擎回放（不重跑 ocgcore+script）；旧格式仍需
            // 重跑引擎复现消息流（C++ ReplayMode::ReplayThread），先引导卡片/脚本资源
            msgMode = replayData.msgBuffer != null;
            if (!msgMode && !NativeScriptBootstrap.ensureEngineReady()) {
                lastErrorMessage = "决斗引擎或卡片脚本加载失败";
                setState(ReplayState.ERROR);
                mainHandler.post(() -> {
                    if (listener != null) listener.onReplayHintMessage("决斗引擎或卡片脚本加载失败");
                });
                return;
            }

            field.clear();
            if (!msgMode) {
                // 旧格式引擎重跑：手工建初始场；纯消息模式由 MSG_START 经实况管线
                // messageParser.onStart 统一建场（与 LAN 对局完全同路）
                setupInitialField();
            }

            replayBuffer = replayData.replayBuffer;
            replayBuffer.order(ByteOrder.LITTLE_ENDIAN);
            // 快照响应记录区（undo/restart 时 Rewind 重放，对齐 cur_replay.Rewind）
            originalResponseBytes = new byte[replayBuffer.remaining()];
            ((ByteBuffer) replayBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)).get(originalResponseBytes);
            replayWinSeen = false;
            if (msgMode) {
                // 纯消息模式：快照并切出消息流，从起点开始逐条回放
                ByteBuffer ms = replayData.msgBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN);
                originalMsgBytes = new byte[ms.remaining()];
                ms.get(originalMsgBytes);
                msgStream = ByteBuffer.wrap(originalMsgBytes).order(ByteOrder.LITTLE_ENDIAN);
                chainCodes.clear();
                mapReplayDeckCodes();
                startReplaySession();
            }

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

    /** 回放卡组/额外卡面回填：MSG_START 仅按数量建卡（code=0 背面），此处把 yrp 头部的
     *  卡组/额外卡码写进对应 ClientCard，使双方卡组/额外可直接点开查看正面（需求1）；
     *  初始场建立后及每次快进落点后均需调用（重排会重建卡对象） */
    private void applyReplayDeckCodes() {
        if (replayData == null || replayData.decks.isEmpty()) return;
        applyOneDeckCodes(0, replayData.decks.get(0));
        int p1Index = replayData.isTag ? 2 : 1;
        if (replayData.decks.size() > p1Index) {
            applyOneDeckCodes(1, replayData.decks.get(p1Index));
        }
        field.refreshAllCards();
        notifyField();
    }

    private void applyOneDeckCodes(int player, ReplayReader.DeckInfo deckInfo) {
        if (deckInfo == null) return;
        writeZoneCodes(field.players[player].deck, deckInfo.main);
        writeZoneCodes(field.players[player].extra, deckInfo.extra);
    }

    /** 先行码录像：头部卡组/额外卡码换成正式码（仅纯消息模式；旧格式引擎重跑需保留
     *  原卡码以按 seed 复现），与消息流侧 mapMessage 保持同一映射口径 */
    private void mapReplayDeckCodes() {
        if (replayData == null) return;
        for (ReplayReader.DeckInfo deck : replayData.decks) {
            if (deck == null) continue;
            ReplayCodeMapper.mapDeck(deck.main);
            ReplayCodeMapper.mapDeck(deck.extra);
        }
    }

    /** 按索引同序对齐（与 setupDeckForPlayer/field.initial 的序列语义一致） */
    private void writeZoneCodes(List<GameField.ClientCard> list, List<Integer> codes) {
        if (list == null || codes == null) return;
        int n = Math.min(list.size(), codes.size());
        for (int i = 0; i < n; i++) {
            GameField.ClientCard card = list.get(i);
            if (card == null) continue;
            card.code = codes.get(i);
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
            if (msgMode) {
                // 纯消息回放：不重建引擎决斗，逐条消费录像内 MSG 流（refresh* 系列因 pduel==0 自动空转）
                soundManager.playBGM(SoundManager.BGM.DUEL);
                replayMsgLoop();
            } else {
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
            }
        } catch (Exception e) {
            Log.e(TAG, "Replay loop error", e);
        }

        endReplayDuel();
        if (msgMode) {
            // 纯消息回放结束（MSG_WIN / 流尽 / 切片失败）兜底还原快进与回放标志：
            // 快进途中结束时 isSkipping 仍为真，不还原会让实况侧持续静默/即时落位
            clearReplayFlags();
        }
        isRunning = false;
        setState(ReplayState.FINISHED);
        final boolean winShown = replayWinSeen;
        mainHandler.post(() -> {
            soundManager.stopBGM();
            // 回放自然播放完毕（未经 MSG_WIN 判定胜负）：winner=-1 表示无结果，UI 不显示胜负文字
            if (!winShown && listener != null) listener.onReplayFinished(-1, 0);
        });
    }

    /**
     * 纯消息模式主循环（切片投喂器）：从 msgStream 按 common.h 长度表逐条切出消息体，
     * 经 engine.enqueueGameMsg 投入实况管线（pendingMsgs+动画闸门→GameMessageParser→
     * DuelEventHandler），卡片动画/音效/LP浮字/召唤与连锁大图全部由实况侧产生。
     * 节奏：仅「可见步」（移动/召唤/连锁/攻击/回合阶段切换等）等待管线排空并留地板间隔，
     * 其余消息瞬流过；undo/restart/跳回合走 replaySkip+instantPlace 快进重排，落点即暂停。
     */
    private void replayMsgLoop() {
        if (engine == null) {
            lastErrorMessage = "回放管线未接入实况引擎";
            mainHandler.post(() -> {
                if (listener != null) listener.onReplayHintMessage("回放初始化失败：实况引擎未就绪");
            });
            return;
        }
        // 跳回合快进（startTurn>1 由 loadAndPlay 置 isSkipping+skipTurn）
        if (isSkipping) {
            beginInstantSkip();
        }
        long fedSincePump = 0;
        while (isRunning) {
            if (isPaused && !isRestarting) {
                try { Thread.sleep(100); } catch (InterruptedException e) { break; }
                continue;
            }
            if (isRestarting) {
                performRestart();
                fedSincePump = 0;
                continue;
            }
            if (isSwapping) {
                performSwapField();
                isSwapping = false;
            }
            if (msgStream == null || !msgStream.hasRemaining()) {
                return; // 消息流自然播毕
            }
            int msgType = msgStream.get() & 0xFF;
            if (msgType == 5) { // MSG_WIN：结算不走实况管线（messageParser.onWin 回放侧已抑制），
                // 仍由 ReplayEngine 判定结束并通知 UI
                if (msgStream.remaining() < 2) return;
                int winner = msgStream.get() & 0xFF;
                int reason = msgStream.get() & 0xFF;
                replayWinSeen = true;
                if (!isSkipping) {
                    soundManager.stopBGM();
                    mainHandler.post(() -> {
                        if (listener != null) listener.onReplayFinished(winner, reason);
                    });
                }
                return;
            }
            int bodyStart = msgStream.position();
            if (!sliceMsgBody(msgType, msgStream)) {
                // 消息体不完整/未知消息：游标已不可靠，排空已投喂消息后就地止步（已渲染部分保持有效）
                Log.w(TAG, "replayMsgLoop: slice failed at msgType=" + msgType);
                engine.drainReplayQueueNow();
                waitForPendingDrain();
                return;
            }
            int bodyEnd = msgStream.position();
            if (!isNoFeedMsg(msgType)) {
                byte[] body = new byte[bodyEnd - bodyStart];
                ((ByteBuffer) msgStream.duplicate().order(ByteOrder.LITTLE_ENDIAN)
                        .position(bodyStart).limit(bodyEnd)).get(body);
                // 先行码→正式码运行时映射（正式码原样返回），投喂前就地替换副本，不改录像文件
                ReplayCodeMapper.mapMessage(msgType, body, this::updateBlockCount);
                feedMessage(msgType, body);
                fedSincePump++;
            }
            if (msgType == 4) {
                // MSG_START 经实况管线建场（field.initial）后回填卡组/额外卡面（快进/正常路径均适用）
                waitForDispatchDrain();
                applyReplayDeckCodes();
            }
            boolean visible = isVisibleStep(msgType);
            if (isSkipping && skipStep == 0 && !landAtStart) {
                // 跳回合快进：可见步照常计步（保持步号与正常播放一致），不等动画不暂停
                if (visible) currentStep++;
                if (msgType == 40 && skipTurn > 0 && --skipTurn == 0) {
                    endInstantSkip();
                    isSkipping = false;
                    mainHandler.post(() -> {
                        if (listener != null) listener.onReplayHintMessage("快进结束，从当前回合开始正常播放");
                    });
                }
                if (fedSincePump >= 512) { engine.drainReplayQueueNow(); fedSincePump = 0; }
                continue;
            }
            if (isSkipping) {
                // undo/restart 快进重排：不计数，到达目标可见步（或初始场落点）即停下还原
                if (skipStep > 0) {
                    if (visible && --skipStep == 0) landInstantSkip();
                } else if (landAtStart && msgType == 4) {
                    landInstantSkip(); // 重投 MSG_START 重建初始场后即停（回到 0 步）
                }
                if (fedSincePump >= 512) { engine.drainReplayQueueNow(); fedSincePump = 0; }
                continue;
            }
            if (!visible) continue; // 非可见步瞬流过（需求2：无卡片移动的消息不再停顿）
            currentStep++;
            if (stepsRemaining > 0) {
                stepsRemaining--;
                if (stepsRemaining == 0) {
                    isPaused = true;
                    setState(ReplayState.PAUSED);
                }
            }
            // 等实况管线（动画闸门+pendingMsgs）消化完本步再投喂下一条，动画完成信号由实况闸门提供
            waitForPipelineIdle();
            if (stepsRemaining == 0 && !isPaused && !isRestarting) {
                try { Thread.sleep(field.animationSpeed > 1f ? 40L : 80L); }
                catch (InterruptedException e) { return; }
            }
        }
    }

    // === msgMode 切片投喂 helper ===

    /** 开始回放会话：置实况管线回放标志；录制者视角恒等映射（duelIsFirst=true），
     *  yrp 头部双方昵称写入 playerInfos（本地视角索引）供顶部信息栏显示 */
    private void startReplaySession() {
        if (engine == null) return;
        engine.replayMode = true;
        engine.duelIsFirst = true;
        engine.inDuel = false;
        if (replayData != null) {
            for (int p = 0; p < 2 && p < replayData.playerNames.size()
                    && p < engine.playerInfos.length; p++) {
                engine.playerInfos[p].name = replayData.playerNames.get(p);
            }
        }
    }

    /** 退出/停止回放：还原实况管线全部回放标志并复位回放控制状态（MSG_START 重放前也调用以清旧游标） */
    private void clearReplayFlags() {
        if (engine != null) {
            engine.replayMode = false;
            engine.replaySkip = false;
            engine.field.instantPlace = false;
            engine.duelIsFirst = true;
        }
        soundManager.setEffectsSuppressed(false);
        isSkipping = false;
        skipStep = 0;
        skipTurn = 0;
        landAtStart = false;
        stepsRemaining = 0;
    }

    /** 快进三标志开启：实况侧抑制动画/特效/音效，消息处理即时落位 */
    private void beginInstantSkip() {
        engine.replaySkip = true;
        field.instantPlace = true;
        soundManager.setEffectsSuppressed(true);
    }

    /** 快进三标志还原：恢复正常播放节奏（引擎侧 animHold 残留一并清零） */
    private void endInstantSkip() {
        engine.replaySkip = false;
        field.instantPlace = false;
        soundManager.setEffectsSuppressed(false);
        engine.animHoldUntilMs = 0;
    }

    /** 投喂线程调用：强制排空实况队列并等待消化完毕（落点/终止前保证已投喂消息全部渲染） */
    private void drainAndSettle() {
        engine.drainReplayQueueNow();
        waitForPendingDrain();
    }

    /** 快进落点：排空、还原标志、暂停并回填卡组卡面 */
    private void landInstantSkip() {
        engine.drainReplayQueueNow();
        waitForPendingDrain();
        endInstantSkip();
        isSkipping = false;
        currentStep = restartFromStep;
        applyReplayDeckCodes();
        pause();
    }

    /** 主线程投递消息进实况管线（enqueueGameMsg 的队列/闸门均为主线程态，必须 post） */
    private void feedMessage(int msgType, byte[] body) {
        engine.mainHandler.post(() ->
                engine.enqueueGameMsg(msgType, ByteBuffer.wrap(body).order(ByteOrder.LITTLE_ENDIAN)));
    }

    /** 等待实况管线空闲：pendingMsgs 排空且动画闸门开启（动画/特效全部播完）；restart 抢占时提前返回 */
    private void waitForPipelineIdle() {
        long deadline = System.currentTimeMillis() + 8000L;
        while (isRunning && !isRestarting) {
            if (!engine.hasPendingMsgs() && !engine.isAnyAnimationBusy()) return;
            if (System.currentTimeMillis() > deadline) return; // 动画兜底超时，防止卡死
            try { Thread.sleep(16); } catch (InterruptedException e) { return; }
        }
    }

    /** 仅等待队列消化（不要求动画空闲），快进落点/终止收尾用 */
    private void waitForPendingDrain() {
        long deadline = System.currentTimeMillis() + 3000L;
        while (isRunning && engine.hasPendingMsgs()
                && System.currentTimeMillis() < deadline) {
            try { Thread.sleep(10); } catch (InterruptedException e) { return; }
        }
    }

    /** SELECT/询问类与 RETRY/WAITING/REQUEST_DECK：切片推进游标但不投喂实况管线 */
    private boolean isNoFeedMsg(int msgType) {
        if (msgType == 1 || msgType == 3 || msgType == 8) return true;
        return (msgType >= 10 && msgType <= 26) || msgType == 132
                || (msgType >= 140 && msgType <= 143);
    }

    /** 可见步（pauseable 收窄）：有卡片移动/大图/回合阶段推进的步才计步并等待节奏 */
    private boolean isVisibleStep(int msgType) {
        switch (msgType) {
            case 40: // NEW_TURN
            case 41: // NEW_PHASE
            case 50: // MOVE
            case 53: // POS_CHANGE
            case 55: // SWAP
            case 60: case 61: case 62: case 63: case 64: case 65: // SUMMONING~FLIPSUMMONED
            case 70: case 71: // CHAINING / CHAINED
            case 90: // DRAW
            case 110: // ATTACK
                return true;
            default:
                return false;
        }
    }

    /**
     * 按 common.h 消息长度表推进游标切出消息体（与 gframe replay_mode.cpp ReplayAnalyze 同源，
     * SELECT 系长度与 selectWithResponse 一致）。投喂给实况管线的 body 为切片区间 [bodyStart,bodyEnd)。
     * @return false 表示消息体不完整或未知消息（游标不可靠，应终止循环）
     */
    private boolean sliceMsgBody(int msgType, ByteBuffer buf) {
        switch (msgType) {
            case 1: // RETRY
            case 3: // WAITING
            case 61: case 63: case 65: // SUMMONED / SPSUMMONED / FLIPSUMMONED
            case 74: // CHAIN_END
            case 112: case 113: case 114: // ATTACK_DISABLED / DAMAGE_STEP_START / DAMAGE_STEP_END
            case 170: // MATCH_KILL（replay_mode.cpp 不消费 body）
                return true;
            case 2: // HINT: type(1) player(1) data(4)
            case 165: // PLAYER_HINT
                return takeFixed(buf, 6);
            case 4: // START: player+duelrule+lp0+lp1+deckc/extrac*2（本机录制的 MSG_START 体为 18 字节）
                return takeFixed(buf, 18);
            case 8: // REQUEST_DECK: player
                return takeFixed(buf, 1);
            case 6: // UPDATE_DATA: player loc + 区域卡数×[clen(含自身)][payload]（与 parseUpdateData 同口径）
                return walkUpdateDataBlocks(buf);
            case 7: // UPDATE_CARD: player loc seq + 单块
                return takeFixed(buf, 3) && takeQueryBlock(buf);
            case 10: { // SELECT_BATTLECMD: player cnt + cnt*11 + cnt2*8 + 2
                if (!takeFixed(buf, 2)) return false;
                if (!takeFixed(buf, lastU8(buf) * 11 + 1)) return false;
                return takeFixed(buf, lastU8(buf) * 8 + 2);
            }
            case 11: { // SELECT_IDLECMD: player + 5组(cnt*7) + cnt*11 + 3
                if (!takeFixed(buf, 1)) return false;
                for (int t = 0; t < 5; t++) {
                    if (!takeFixed(buf, 1)) return false;
                    if (!takeFixed(buf, lastU8(buf) * 7)) return false;
                }
                if (!takeFixed(buf, 1)) return false;
                return takeFixed(buf, lastU8(buf) * 11 + 3);
            }
            case 12: return takeFixed(buf, 13); // SELECT_EFFECTYN
            case 13: return takeFixed(buf, 5);  // SELECT_YESNO
            case 14: // SELECT_OPTION: player cnt + cnt*4
                return takeFixed(buf, 2) && takeFixed(buf, lastU8(buf) * 4);
            case 15: case 20: // SELECT_CARD / SELECT_TRIBUTE: player + 3 + cnt + cnt*8
                return takeFixed(buf, 5) && takeFixed(buf, lastU8(buf) * 8);
            case 16: // SELECT_CHAIN: player cnt + 9 + cnt*14
                return takeFixed(buf, 2) && takeFixed(buf, 9 + lastU8(buf) * 14);
            case 17: // SORT_CHAIN: player cnt + cnt*7
                return takeFixed(buf, 2) && takeFixed(buf, lastU8(buf) * 7);
            case 18: case 19: case 24: // PLACE / POSITION / DISFIELD: player + 5
                return takeFixed(buf, 6);
            case 22: // SELECT_COUNTER: player + 4 + cnt + cnt*9
                return takeFixed(buf, 6) && takeFixed(buf, lastU8(buf) * 9);
            case 23: { // SELECT_SUM: op player + 6 + cnt + cnt*11 + cnt2*11
                if (!takeFixed(buf, 9)) return false;
                if (!takeFixed(buf, lastU8(buf) * 11 + 1)) return false;
                return takeFixed(buf, lastU8(buf) * 11);
            }
            case 25: // SORT_CARD: player cnt + cnt*7
                return takeFixed(buf, 2) && takeFixed(buf, lastU8(buf) * 7);
            case 26: { // SELECT_UNSELECT_CARD: player + 4 + cnt + cnt*8 + cnt2*8
                if (!takeFixed(buf, 6)) return false;
                if (!takeFixed(buf, lastU8(buf) * 8 + 1)) return false;
                return takeFixed(buf, lastU8(buf) * 8);
            }
            case 30: case 42: // CONFIRM_DECKTOP / CONFIRM_EXTRATOP: player cnt + cnt*7
                return takeFixed(buf, 2) && takeFixed(buf, lastU8(buf) * 7);
            case 31: // CONFIRM_CARDS: player skip cnt + cnt*7
                return takeFixed(buf, 3) && takeFixed(buf, lastU8(buf) * 7);
            case 32: return takeFixed(buf, 1); // SHUFFLE_DECK
            case 33: // SHUFFLE_HAND: player cnt + cnt*4
                return takeFixed(buf, 2) && takeFixed(buf, lastU8(buf) * 4);
            case 34: case 35: // REFRESH_DECK / SWAP_GRAVE_DECK: player
                return takeFixed(buf, 1);
            case 37: return true; // REVERSE_DECK（无 body，replay_mode.cpp 不推进）
            case 36: // SHUFFLE_SET_CARD: player cnt + cnt*8
                return takeFixed(buf, 2) && takeFixed(buf, lastU8(buf) * 8);
            case 38: return takeFixed(buf, 6); // DECK_TOP: player seq code
            case 39: // SHUFFLE_EXTRA: player cnt + cnt*4
                return takeFixed(buf, 2) && takeFixed(buf, lastU8(buf) * 4);
            case 40: return takeFixed(buf, 1); // NEW_TURN
            case 41: return takeFixed(buf, 2); // NEW_PHASE
            case 50: return takeFixed(buf, 16); // MOVE
            case 53: return takeFixed(buf, 9);  // POS_CHANGE
            case 54: return takeFixed(buf, 8);  // SET
            case 55: return takeFixed(buf, 16); // SWAP
            case 56: return takeFixed(buf, 4);  // FIELD_DISABLED
            case 60: case 62: case 64: // SUMMONING / SPSUMMONING / FLIPSUMMONING
                return takeFixed(buf, 8);
            case 70: return takeFixed(buf, 16); // CHAINING
            case 71: case 72: case 73: case 75: case 76: // CHAINED~CHAIN_DISABLED
                return takeFixed(buf, 1);
            case 80: case 81: // CARD_SELECTED / RANDOM_SELECTED: player cnt + cnt*4
                return takeFixed(buf, 2) && takeFixed(buf, lastU8(buf) * 4);
            case 83: // BECOME_TARGET: cnt + cnt*4
                return takeFixed(buf, 1) && takeFixed(buf, lastU8(buf) * 4);
            case 90: // DRAW: player cnt + cnt*4
                return takeFixed(buf, 2) && takeFixed(buf, lastU8(buf) * 4);
            case 91: case 92: case 94: case 100: // DAMAGE / RECOVER / LPUPDATE / PAY_LPCOST
                return takeFixed(buf, 5);
            case 93: return takeFixed(buf, 8); // EQUIP
            case 95: return takeFixed(buf, 4); // UNEQUIP
            case 96: case 97: // CARD_TARGET / CANCEL_TARGET
                return takeFixed(buf, 8);
            case 101: case 102: // ADD/REMOVE_COUNTER
                return takeFixed(buf, 7);
            case 110: return takeFixed(buf, 8); // ATTACK
            case 111: return takeFixed(buf, 26); // BATTLE
            case 120: return takeFixed(buf, 8); // MISSED_EFFECT
            case 130: case 131: // TOSS_COIN / TOSS_DICE: player cnt + cnt*1
                return takeFixed(buf, 2) && takeFixed(buf, lastU8(buf));
            case 132: return takeFixed(buf, 1); // ROCK_PAPER_SCISSORS
            case 133: return takeFixed(buf, 1); // HAND_RES
            case 140: case 141: return takeFixed(buf, 6); // ANNOUNCE_RACE / ATTRIB
            case 142: case 143: // ANNOUNCE_CARD / NUMBER: player cnt + cnt*4
                return takeFixed(buf, 2) && takeFixed(buf, lastU8(buf) * 4);
            case 160: return takeFixed(buf, 9); // CARD_HINT
            case 161: { // TAG_SWAP: player mainc extrac pcount handc + 顶部码4 + handc*4 + extrac*4
                // 与 replay_mode.cpp `pbuf += pbuf[2]*4 + pbuf[4]*4 + 9` 同口径（body[2]=额外数、body[4]=手卡数）
                if (!takeFixed(buf, 5)) return false;
                int extraCount = buf.get(buf.position() - 3) & 0xFF;
                int handCount = lastU8(buf);
                return takeFixed(buf, extraCount * 4 + handCount * 4 + 4);
            }
            case 162: return sliceReloadField(buf); // RELOAD_FIELD（变长，含空位标记）
            case 163: case 164: { // AI_NAME / SHOW_HINT: uint16 len + str + nul
                if (!takeFixed(buf, 2)) return false;
                return takeFixed(buf, lastU16(buf) + 1);
            }
            default:
                Log.w(TAG, "sliceMsgBody: unknown msg " + msgType);
                lastErrorMessage = "未知回放消息 " + msgType;
                return false;
        }
    }

    /** 切片时读取刚消费的最后一字节（均为 cnt/val 类字段） */
    private int lastU8(ByteBuffer buf) {
        return buf.get(buf.position() - 1) & 0xFF;
    }

    /** 切片时读取刚消费的两个字节（uint16 小端长度，如 AI_NAME / SHOW_HINT 的字符串长） */
    private int lastU16(ByteBuffer buf) {
        int p = buf.position();
        return (buf.get(p - 2) & 0xFF) | ((buf.get(p - 1) & 0xFF) << 8);
    }

    /** 定长推进：不足则判定游标不可靠（消息体截断） */
    private boolean takeFixed(ByteBuffer buf, int n) {
        if (n < 0 || buf.remaining() < n) return false;
        buf.position(buf.position() + n);
        return true;
    }

    /** MSG_RELOAD_FIELD 变长切片：player + 2×(4 + 7×(1+3标记) + 8×(1+2标记) + 6) + 1，
     *  与 processMessage case 162 / gframe 定长口径一致（非空位才有附加字节） */
    private boolean sliceReloadField(ByteBuffer buf) {
        if (!takeFixed(buf, 1)) return false;
        for (int p = 0; p < 2; p++) {
            if (!takeFixed(buf, 4)) return false;
            for (int s = 0; s < 7; s++) {
                if (!takeFixed(buf, 1)) return false;
                if (lastU8(buf) != 0 && !takeFixed(buf, 2)) return false;
            }
            for (int s = 0; s < 8; s++) {
                if (!takeFixed(buf, 1)) return false;
                if (lastU8(buf) != 0 && !takeFixed(buf, 1)) return false;
            }
            if (!takeFixed(buf, 6)) return false;
        }
        return takeFixed(buf, 1);
    }

    /** UPDATE_DATA 体切片：player(1) + location(1) 后按区域条目逐个消费自界长块；
     *  条目数与实况 CommandDataParser.parseUpdateData 完全同构（固定槽位区域整列计数，
     *  动态列表只计实际存在的卡），切片前先等实况侧消化完，保证两侧卡数一致 */
    private boolean walkUpdateDataBlocks(ByteBuffer buf) {
        // 区域卡数取自实况侧当前场（切片前等其追上游标），否则块数估计与实况 parse 不一致→游标错位
        waitForDispatchDrain();
        if (buf.remaining() < 2) return false;
        int player = buf.get() & 0xFF;
        int location = buf.get() & 0xFF;
        List<GameField.ClientCard> list = field.players[engine.localPlayer(player)]
                .getLocationList(location);
        if (list == null) return false;
        boolean fixedSlots = (location == 0x04 || location == 0x08);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i) == null && !fixedSlots) continue;
            if (!takeQueryBlock(buf)) return false;
        }
        return true;
    }

    /** 切片 UPDATE_DATA 前等实况管线消化完已投喂消息（有限等待，保证区域卡数一致） */
    private void waitForDispatchDrain() {
        long deadline = System.currentTimeMillis() + 2000L;
        while (isRunning && !engine.isMsgQueueIdle()
                && System.currentTimeMillis() < deadline) {
            try { Thread.sleep(5); } catch (InterruptedException e) { return; }
        }
    }

    /** [int32 clen(含自身)][payload...] 单块游标推进 */
    private boolean takeQueryBlock(ByteBuffer buf) {
        if (buf.remaining() < 4) return false;
        int clen = buf.getInt();
        int adv = clen - 4;
        if (adv < 0 || buf.remaining() < adv) return false;
        buf.position(buf.position() + adv);
        return true;
    }

    /** UPDATE_DATA 的 query 块数（与 walkUpdateDataBlocks / 实况 parseUpdateData 同口径）：
     *  固定槽位区域（怪兽区/魔法区）整列计数，动态列表只计实际存在的卡 */
    private int updateBlockCount(int player, int location) {
        List<GameField.ClientCard> list = field.players[engine.localPlayer(player)]
                .getLocationList(location);
        if (list == null) return 0;
        if (location == 0x04 || location == 0x08) return list.size();
        int count = 0;
        for (GameField.ClientCard card : list) {
            if (card != null) count++;
        }
        return count;
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
            boolean pauseable = pauseableForMode(msgType);
            if (!processMessage(msgType, buf)) {
                return false;
            }
            if (!pauseable) {
                continue;
            }
            if (!afterPauseableMessage(msgType)) {
                return false;
            }
        }
        return true;
    }

    /**
     * 可暂停消息后的统一节奏（引擎重跑与纯消息两模式共用）：快进计数/步进自动暂停/
     * 回合快跳与 800ms 节奏；纯消息模式下额外等待长动画余量（animHoldUntilMs），
     * 使洗牌/连抽/攻击弧不被下一条消息抢先。@return false 表示终止回放。
     */
    private boolean afterPauseableMessage(int msgType) {
        if (skipStep > 0) {
            skipStep--;
            if (skipStep == 0) {
                // 上一步回退：已快进到目标步，停在该步（步进模式）
                isSkipping = false;
                currentStep = restartFromStep;
                pause();
                notifyField();
                // 快进期间回合/阶段/LP 等 UI 派发全部静默，落点后一次性补发，
                // 顶部信息栏与详情面板同步到回退目标步的真实状态
                final int sTurn = field.turnCount;
                final int sCur = field.currentPlayer;
                final int sPhase = field.currentPhase;
                mainHandler.post(() -> {
                    if (listener == null) return;
                    listener.onReplayTurnChanged(sTurn, sCur);
                    listener.onReplayPhaseChanged(sPhase);
                    listener.onReplayPlayerInfoUpdated(0);
                    listener.onReplayPlayerInfoUpdated(1);
                });
            }
            return true;
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
                return true;
            }
        }

        // 步进前进：处理完 N 个可暂停消息后自动暂停
        if (stepsRemaining > 0) {
            stepsRemaining--;
            if (stepsRemaining == 0) {
                isPaused = true;
                setState(ReplayState.PAUSED);
                notifyField();
            }
            return true;
        }

        if (!skipForward && !isSkipping && !isPaused) {
            // 节奏下限：纯消息模式收紧为 300ms——实况里消息间隔由玩家思考时间决定，
            // 回放按 800ms 统一等待会明显拖沓（洗牌/连锁等长动画已由 animHoldUntilMs
            // 持闸兜底）；旧引擎重跑路径维持原 800ms 节奏不变
            long sleepMs = msgMode ? 300L : 800L;
            if (msgMode) {
                long animRemain = animHoldUntilMs - System.currentTimeMillis();
                if (animRemain > sleepMs) {
                    sleepMs = animRemain;
                }
            }
            // quick_animation 开启（animationSpeed>1）时步间隔同倍缩短
            float spd = field.animationSpeed;
            if (spd > 1f) {
                sleepMs = (long) (sleepMs / spd);
            }
            try {
                Thread.sleep(sleepMs);
            } catch (InterruptedException e) {
                return false;
            }
        }
        return true;
    }

    /** 纯消息模式下 MSG_RETRY(1)/MSG_WAITING(3)/MSG_HINT(2)/MSG_UPDATE_DATA(6)/
     *  MSG_UPDATE_CARD(7)/MSG_REQUEST_DECK(8) 不参与步进计数与节奏停顿：服务端每个动作
     *  都连发多条 UPDATE/HINT，若逐条停顿 800ms 会把回放拖得极慢（用户所报“每一步间隔过长”根因），
     *  且这些消息本身不改变可见场面节奏 */
    private boolean pauseableForMode(int msgType) {
        if (msgMode && (msgType == 1 || msgType == 3 || msgType == 2
                || msgType == 6 || msgType == 7 || msgType == 8)) return false;
        return isPauseable(msgType);
    }

    private boolean isPauseable(int msgType) {
        // 对齐 replay_mode.cpp ReplayAnalyze 的 pauseable=false 集合（这些消息不计步/不暂停）；
        // 消息号取 common.h 真值：MSG_SET=54、MSG_FIELD_DISABLED=56（旧代码写 52/54 导致 SET/FIELD_DISABLED
        // 走 default 未识别，游标错位后后续消息全部错解析——即用户报“回放无法显示卡片和动画”根因）
        switch (msgType) {
            case 54: // MSG_SET
            case 56: // MSG_FIELD_DISABLED
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

        if (msgMode) {
            // 纯消息模式：排空实况队列后 msgStream 回到起点快进重排，无需重跑引擎；
            // MSG_START 重投即由实况管线重建初始场（landAtStart 落点），目标步>0 时
            // replayMsgLoop 的 isSkipping 分支投喂至落点后 landInstantSkip 暂停
            drainAndSettle();
            msgStream = ByteBuffer.wrap(originalMsgBytes).order(ByteOrder.LITTLE_ENDIAN);
            chainCodes.clear();
            replayWinSeen = false;
            currentStep = 0;
            skipStep = Math.max(0, restartTargetStep);
            restartFromStep = skipStep;
            restartTargetStep = 0;
            landAtStart = skipStep == 0;
            beginInstantSkip();
            return;
        }

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
        field.refreshAllCards();
        notifyField();
    }

    private boolean processMessage(int msgType, ByteBuffer buf) {
        if (buf == null) return false;
        msgBuf = buf;

        try {
            switch (msgType) {
                case 1: // MSG_RETRY
                    if (msgMode) break; // 实况客户端收到 RETRY 仅重新询问；回放无需应答，静默跳过
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
                    applyShuffleDeck(sdPlayer, msgMode && !isSkipping);
                    // 对齐 replay_mode.cpp L458：洗牌后整库重查卡面（纯消息模式由后续 UPDATE 提供）
                    refreshDeck(sdPlayer);
                    break;

                case 33: // MSG_SHUFFLE_HAND
                    if (buf.remaining() < 2) return false;
                    int shPlayer = buf.get() & 0xFF;
                    int shCount = buf.get() & 0xFF;
                    if (msgMode) {
                        // 卡码由消息体提供（对手视角已遮蔽为 0，与实况一致），携入洗手卡动画延迟换面
                        int[] shCodes = new int[shCount];
                        for (int i = 0; i < shCount && buf.remaining() >= 4; i++) shCodes[i] = buf.getInt();
                        applyShuffleHandMsg(shPlayer, shCount, shCodes);
                    } else {
                        skipBytes(shCount * 4);
                        applyShuffleHand(shPlayer);
                    }
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

                case 53: { // MSG_POS_CHANGE（common.h=53，replay_mode.cpp L540-543 pbuf+=9）
                    if (buf.remaining() < 9) return false;
                    int pcCode = buf.getInt();
                    int pcCtrl = buf.get() & 0xFF;
                    int pcLoc = buf.get() & 0xFF;
                    int pcSeq = buf.get() & 0xFF;
                    int pcOld = buf.get() & 0xFF;
                    int pcNew = buf.get() & 0xFF;
                    onPosChange(pcCode, pcCtrl, pcLoc, pcSeq, pcOld, pcNew);
                    break;
                }

                case 54: { // MSG_SET（common.h=54，replay_mode.cpp L545-549 pbuf+=8：code + ctrl loc seq pos）
                    if (buf.remaining() < 8) return false;
                    int setCode = buf.getInt();
                    int setCtrl = buf.get() & 0xFF;
                    int setLoc = buf.get() & 0xFF;
                    int setSeq = buf.get() & 0xFF;
                    buf.get(); // pos
                    onSet(setCode, setCtrl, setLoc, setSeq);
                    break;
                }

                case 55: { // MSG_SWAP（common.h=55，replay_mode.cpp L551-554 pbuf+=16）
                    if (buf.remaining() < 16) return false;
                    buf.getInt(); int sw1c = buf.get() & 0xFF; int sw1l = buf.get() & 0xFF; int sw1s = buf.get() & 0xFF; buf.get();
                    buf.getInt(); int sw2c = buf.get() & 0xFF; int sw2l = buf.get() & 0xFF; int sw2s = buf.get() & 0xFF; buf.get();
                    onSwap(sw1c, sw1l, sw1s, sw2c, sw2l, sw2s);
                    break;
                }

                case 56: { // MSG_FIELD_DISABLED（common.h=56，replay_mode.cpp L556-560 pbuf+=4）
                    if (buf.remaining() < 4) return false;
                    int fdMask = buf.getInt();
                    onFieldDisabled(fdMask);
                    break;
                }

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
                case 71: {
                    int chCount = buf.get() & 0xFF;
                    onChained(chCount);
                    replayRefresh();
                    break;
                }
                case 72: skipBytes(1); onChainSolving(0); break;
                case 73: skipBytes(1); onChainSolved(0); replayRefresh(); break;
                case 74: onChainEnd(); replayRefresh(); break;
                case 75: {
                    int negCount = buf.get() & 0xFF;
                    onChainNegated(negCount);
                    break;
                }
                case 76: {
                    int disCount = buf.get() & 0xFF;
                    onChainDisabled(disCount);
                    break;
                }

                case 80: case 81: { // MSG_CARD_SELECTED / MSG_RANDOM_SELECTED
                    skipBytes(1);
                    int csCount = buf.get() & 0xFF;
                    skipBytes(csCount * 4);
                    break;
                }
                case 83: { // MSG_BECOME_TARGET（common.h=83）：count(1) + count×[c1 l1 s1 ss1] → 加入 current_chain.target
                    int btCount = buf.get() & 0xFF;
                    ByteBuffer btData = buf.slice().order(ByteOrder.LITTLE_ENDIAN);
                    if (buf.remaining() >= btCount * 4) buf.position(buf.position() + btCount * 4);
                    onBecomeTarget(btCount, btData);
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

                case 101: case 102: { // ADD/REMOVE_COUNTER：type(2) + ctrl loc seq + count(2)
                    if (msgMode) {
                        // 纯消息模式卡面不经引擎重查，计数器直接由消息体维护
                        if (buf.remaining() < 7) return false;
                        int ctrType = buf.getShort() & 0xFFFF;
                        int ctrCtrl = buf.get() & 0xFF;
                        int ctrLoc = buf.get() & 0xFF;
                        int ctrSeq = buf.get() & 0xFF;
                        int ctrCount = buf.getShort() & 0xFFFF;
                        if (msgType == 101) onAddCounter(ctrType, ctrCtrl, ctrLoc, ctrSeq, ctrCount);
                        else onRemoveCounter(ctrType, ctrCtrl, ctrLoc, ctrSeq, ctrCount);
                    } else {
                        skipBytes(7);
                    }
                    break;
                }

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

                case 160: { // MSG_CARD_HINT（common.h=160，duelclient.cpp L4094-4109）
                    if (buf.remaining() < 9) return false;
                    int chCtrl = buf.get() & 0xFF;
                    int chLoc = buf.get() & 0xFF;
                    int chSeq = buf.get() & 0xFF;
                    buf.get(); // subseq
                    int chType = buf.get() & 0xFF;
                    int chVal = buf.getInt();
                    onCardHint(chCtrl, chLoc, chSeq, chType, chVal);
                    break;
                }
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
        if (msgMode) {
            // 纯消息模式：SELECT 消息体已按长度表消耗，动画/卡面全部来自消息流本身，
            // 不读响应记录也不喂引擎（避免响应耗尽/锁步风险）
            return true;
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
            if (msgMode) {
                // 纯消息模式：卡面数据由消息体 [clen][flag...] 块提供（与 LAN 客户端
                // CommandDataParser.parseUpdateData 同一应用路径），并推进游标避免后续消息错位
                applyFieldQueryBuf(player & 1, location, buf);
            }
            onUpdateData(player, location, buf);
        } else { // UPDATE_CARD
            int seq = buf.get() & 0xFF;
            if (msgMode) {
                applyUpdateCardBuf(player & 1, location, seq, buf);
            }
            onUpdateCard(player, location, seq, buf);
        }
    }

    /** UPDATE_DATA 块序列应用（按区域卡片列表逐块消费，与 applyFieldQuery 同格式） */
    private void applyFieldQueryBuf(int player, int location, ByteBuffer data) {
        java.util.List<GameField.ClientCard> list = field.players[player].getLocationList(location);
        if (list == null) return;
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

    /** UPDATE_CARD 单卡块 [clen][flag...]：updateQuery 后推进游标 */
    private void applyUpdateCardBuf(int player, int location, int seq, ByteBuffer data) {
        if (data.remaining() < 4) return;
        int len = data.getInt();
        int next = data.position() + Math.max(0, len - 4);
        if (next > data.limit()) next = data.limit();
        if (len > 8) {
            GameField.ClientCard card = field.getCard(player, location & 0x7f, seq);
            if (card != null) {
                ByteBuffer sub = data.slice().order(ByteOrder.LITTLE_ENDIAN);
                sub.limit(Math.min(sub.limit(), len - 4));
                card.updateQuery(sub);
            }
        }
        data.position(next);
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
        applyFieldQueryBuf(player, location, ByteBuffer.wrap(blocks).order(ByteOrder.LITTLE_ENDIAN));
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
        // 退出回放：还原实况管线全部回放标志（replayMode/replaySkip/instantPlace/音效静默）
        clearReplayFlags();
        setState(ReplayState.FINISHED);
    }

    public void skipAhead() {
        // 步进前进：处理下一个可暂停消息（带动画）后自动暂停，对齐 C++ ReplayMode 单步执行
        if (isPaused && isRunning) {
            stepsRemaining = 1;
            isPaused = false;
            setState(ReplayState.PLAYING);
        }
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
        // 纯消息模式不弹顶部提示（用户要求：回放不显示消息提示），仅留日志
        if (msgMode) {
            Log.d(TAG, "hint(type=" + type + ", player=" + player + ", data=" + data + ")");
            return;
        }
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
        notifyField();
    }
    @Override public void onWin(int player, int reason) {
        // 快进重排途中遇到 WIN（目标步在末尾之前不可能，仅防直播尾粘连）：不弹结果
        if (isSkipping) return;
        soundManager.stopBGM();
        // 直接把 MSG_WIN 的胜者与胜利原因透传给 UI，由阶段文字（case 101）显示胜负 + !victory 原因
        mainHandler.post(() -> { if (listener != null) listener.onReplayFinished(player, reason); });
    }
    @Override public void onUpdateData(int player, int location, ByteBuffer data) {
        notifyField();
    }
    @Override public void onUpdateCard(int player, int location, int sequence, ByteBuffer data) {
        notifyField();
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
    @Override public void onShuffleDeck(ByteBuffer data) {
        int player = data.get() & 0xFF;
        applyShuffleDeck(player, msgMode && !isSkipping);
    }
    @Override public void onShuffleHand(ByteBuffer data) {
        int player = data.get() & 0xFF;
        int count = data.get() & 0xFF;
        if (data.remaining() >= count * 4) data.position(data.position() + count * 4);
        applyShuffleHand(player);
    }
    // 引擎重跑模式不播洗牌动画（对齐 C++ 录像快进跳过动画分支），仅音效 + 刷面；
    // 纯消息模式由 processMessage 传入 withAnim=true 走实况同款动画
    private void applyShuffleDeck(int player) { applyShuffleDeck(player, false); }
    private void applyShuffleDeck(int player, boolean withAnim) {
        if (!withAnim) {
            playSfx(SoundManager.SFX.SHUFFLE);
            notifyField();
            return;
        }
        applyShuffleDeckAnim(player);
    }
    /** 移植实况 DuelEventHandler.onShuffleDeck（duelclient.cpp MSG_SHUFFLE_DECK L2620-2657）：
     *  倒转先正向排布→清卡面背面呈现→ 5 轮抖动→恢复倒转回位 */
    private void applyShuffleDeckAnim(int player) {
        final int p = player & 1;
        final List<GameField.ClientCard> deck = field.players[p].deck;
        if (deck.size() < 2) {
            notifyField();
            return;
        }
        final boolean rev = field.deckReversed;
        if (rev) {
            field.deckReversed = false;
            for (GameField.ClientCard c : deck) {
                if (c != null) field.moveCardAnimated(c, 10);
            }
        }
        final long preDelay = rev ? 170L : 0L;
        mainHandler.postDelayed(() -> {
            for (GameField.ClientCard c : deck) {
                if (c != null) {
                    c.setCode(0);
                    c.is_reversed = false;
                }
            }
            playSfx(SoundManager.SFX.SHUFFLE);
            for (GameField.ClientCard c : deck) {
                if (c != null) field.startDeckShake(c);
            }
            if (rev) {
                field.deckReversed = true;
                for (GameField.ClientCard c : deck) {
                    if (c != null) field.moveCardAnimated(c, 10, 30);
                }
            }
            animHoldUntilMs = System.currentTimeMillis() + preDelay + 30L * 17L + 100L;
            notifyField();
        }, preDelay);
    }
    private void applyShuffleHand(int player) { playSfx(SoundManager.SFX.SHUFFLE); notifyField(); }

    /** 移植实况 DuelEventHandler.onShuffleHand（duelclient.cpp MSG_SHUFFLE_HAND L2659-2701）：
     *  聚拢→停留→回新布局关键帧动画，停留段末换入新卡面；回放不做对手手卡翻面揭示
     *  （C++ replay is_replay_need_flip=false 与实况 DuelEventHandler flip 判据一致） */
    private void applyShuffleHandMsg(int player, int count, int[] newCodes) {
        final int p = player & 1;
        if (isSkipping || count == 0) {
            notifyField();
            return;
        }
        if (count > 1) playSfx(SoundManager.SFX.SHUFFLE);
        final List<GameField.ClientCard> hand = field.players[p].hand;
        int maxTotal = 0;
        for (GameField.ClientCard c : hand) {
            if (c == null) continue;
            field.startHandShuffle(c, false);
            maxTotal = Math.max(maxTotal, c.animTotalFrame);
        }
        if (maxTotal > 0) {
            int returnStart = maxTotal >= 31 ? 26 : 21;
            final long revealDelay = (returnStart - 1L) * 17L;
            mainHandler.postDelayed(() -> {
                int idx = 0;
                for (GameField.ClientCard c : hand) {
                    if (c == null) continue;
                    if (idx < count) c.setCode(newCodes[idx] & 0x7fffffff);
                    c.clearDescHints();
                    idx++;
                }
                notifyField();
            }, revealDelay);
            animHoldUntilMs = System.currentTimeMillis() + (maxTotal + 5L) * 17L;
        } else {
            int idx = 0;
            for (GameField.ClientCard c : hand) {
                if (c == null) continue;
                if (idx < count) c.setCode(newCodes[idx] & 0x7fffffff);
                c.clearDescHints();
                idx++;
            }
            notifyField();
        }
    }
    @Override public void onRefreshDeck(int player) { notifyField(); }
    @Override public void onSwapGraveDeck(int player) { notifyField(); }
    @Override public void onShuffleSetCard(int player, int count, ByteBuffer data) { notifyField(); }
    @Override public void onReverseDeck(int player) { notifyField(); }
    @Override public void onDeckTop(int player, int code) { notifyField(); }
    @Override public void onNewTurn(int player) {
        field.currentPlayer = player;
        field.turnCount++;
        playSfx(SoundManager.SFX.NEXT_TURN);
        // 回合切换即时刷新顶部信息栏（纯数字回合数 + 回合方高亮，对齐实况 onTurnStarted）；
        // 快进重排期间静默，落点后由 skipStep 统一补发同步
        final int turn = field.turnCount;
        final int cur = field.currentPlayer;
        if (!isSkipping) mainHandler.post(() -> {
            if (listener != null) listener.onReplayTurnChanged(turn, cur);
        });
        notifyField();
    }
    @Override public void onNewPhase(int phase) {
        field.currentPhase = phase;
        playSfx(SoundManager.SFX.PHASE);
        if (!isSkipping) mainHandler.post(() -> {
            if (listener != null) {
                listener.onReplayPhaseChanged(phase);
                // 对齐 EngineCallbackDelegate.onPhaseChanged：录像也显示阶段文字 drawspec
                int textCode = replayPhaseTextCode(phase);
                if (textCode > 0) listener.onReplayPhaseText(textCode);
            }
        });
    }

    /** 录像阶段消息体 phase 値 → SpecEffectOverlay showText 的 case 101 code（对齐 duelclient.cpp L2905-2929） */
    private static int replayPhaseTextCode(int phase) {
        DuelPhase dp = DuelPhase.valueOf(phase);
        if (dp == null) return 0;
        switch (dp) {
            case Draw: return 4;
            case Standby: return 5;
            case Main1: return 6;
            case BattleStart: return 7;
            case Main2: return 8;
            case End: return 9;
            default: return 0;
        }
    }
    @Override public void onMove(int code, int oc, int ol, int os, int opos, int nc, int nl, int ns, int pos, int reason) {
        boolean oldOv = (ol & 0x80) != 0, newOv = (nl & 0x80) != 0;
        // 快进（isSkipping）等价 C++ isReplaySkiping：直接换位不播动画（duelclient.cpp L3012-3015）
        boolean skipAnim = isSkipping;
        if (newOv && !oldOv) {
            // 作为超量素材叠放（duelclient.cpp L3055-3095）：不本地改写 position（旧实现用 cp
            // 覆盖致表侧素材变背面），仅新建兜底卡时置表侧；宿主在怪兽区才播 10 帧堆叠动画
            GameField.ClientCard card = field.getCard(oc, ol & 0x7f, os);
            if (card == null) card = new GameField.ClientCard();
            if (code != 0) card.code = code;
            if (card.position == 0) card.position = GameField.POS_FACEUP;
            GameField.ClientCard olcard = field.attachOverlayMaterial(card, oc, ol & 0x7f, os, nc, nl & 0x7f, ns);
            if (olcard != null && olcard.location == 0x04) {
                if (!skipAnim) field.moveCardAnimated(card, 10);
                else field.setCardPos(card);
            }
        } else if (oldOv && !newOv) {
            // 素材离叠（L3096-3124）：detach 内已逐素材 MoveCard(2) 重排，此处本体 10 帧飞向新区域
            GameField.ClientCard card = field.detachOverlayMaterial(oc, ol & 0x7f, os, opos, nc, nl & 0x7f, ns, pos);
            if (card != null) {
                if (code != 0) card.code = code;
                if (!skipAnim) field.moveCardAnimated(card, 10);
                else field.setCardPos(card);
            }
        } else if (oldOv && newOv) {
            // 素材在两只超量怪兽间转移（L3125-3153）：旧实现落入通用分支导致素材滞留
            GameField.ClientCard src = field.getCard(oc, ol & 0x7f, os);
            GameField.ClientCard dst = field.getCard(nc, nl & 0x7f, ns);
            if (src != null && dst != null && opos >= 0 && opos < src.overlayed.size()) {
                GameField.ClientCard m = src.overlayed.remove(opos);
                for (int i = 0; i < src.overlayed.size(); i++) {
                    GameField.ClientCard s = src.overlayed.get(i);
                    if (s == null) continue;
                    s.sequence = i;
                    if (!skipAnim) field.moveCardAnimated(s, 2);
                }
                // 源宿主素材减少 → 堆顶下降，宿主回落（目标 Z = 0.02+0.01×素材数）
                if (!skipAnim && src.location == 0x04) field.moveCardAnimated(src, 2);
                if (m != null) {
                    dst.overlayed.add(m);
                    m.overlayTarget = dst;
                    m.controler = nc;
                    m.sequence = dst.overlayed.size() - 1;
                    if (!skipAnim) field.moveCardAnimated(m, 10);
                    // 目标宿主素材增加 → 堆顶抬高，宿主上移
                    if (!skipAnim && dst.location == 0x04) field.moveCardAnimated(dst, 10);
                }
            }
        } else if (nl == 0) {
            // 离场消失（cl==0，L2973-2990）：移除后淡出，播完由 GameFieldMotion purge
            GameField.ClientCard card = field.removeCard(oc, ol, os);
            if (card != null) {
                if (code != 0 && card.code != code) card.code = code;
                card.clearTarget();
                card.is_hovered = false;
                if (!skipAnim) {
                    field.fadeCard(card, 5, GameField.APPEAR_FRAME);
                    field.fadingCards.add(card);
                }
            }
        } else if (ol == 0) {
            // 登场出现（pl==0，L2959-2972）：入区定位后从 alpha 5 淡入（卡组/额外登场等
            // “凭空出现”的卡片自此有完整的淡入动画）
            GameField.ClientCard card = new GameField.ClientCard();
            card.owner = nc;
            card.code = code;
            card.position = pos;
            field.addCard(nc, nl, ns, card);
            field.setCardPos(card);
            if (!skipAnim) {
                card.curAlpha = 5;
                field.fadeCard(card, 255, GameField.APPEAR_FRAME);
            }
        } else {
            GameField.ClientCard card = field.getCard(oc, ol, os);
            if (card == null) card = new GameField.ClientCard();
            // 镜像 DuelEventHandler：SetCode 条件与时序对齐 duelclient.cpp MSG_MOVE L2994/L3018-3020
            //（仅 code!=0 或回额外才改码；先移除、后覆写 position，保 removeCard(0x40) 的
            // isFaceUp/extraPCount 记账读到的是旧表示）
            if (card.code != code && (code != 0 || nl == 0x40))
                card.setCode(code);
            field.removeCard(oc, ol, os);
            card.position = pos;
            field.addCard(nc, nl, ns, card);
            // 卡组→墓地/除外/额外/场上、额外→场上等全部普通移动：镜像 DuelEventHandler.onMove
            // 同套动画分支（旧实现无任何动画，即用户所报“缺少移动动画”的根因）
            if (skipAnim) {
                field.setCardPos(card);
            } else if (ol == nl && oc == nc && (nl & 0x71) != 0) {
                // 同区重排抖动（L3022-3030）：先 5 帧横移 ±0.3 再 5 帧回位
                field.moveCardAnimated(card, 10);
                card.animJitterX = oc == 1 ? 0.3f : -0.3f;
            } else if (nl == 0x04 && !card.overlayed.isEmpty()) {
                // 带素材怪兽移动：素材先重排到新格下方，本体延迟 10 帧再落上（L3032-3037）
                field.moveOverlayMaterials(card, 10);
                field.moveCardAnimated(card, 10, 10);
            } else {
                field.moveCardAnimated(card, 10);
            }
        }
        // 手卡增删后重排双方手卡（对应 C++ cl==0x2 全手卡 MoveCard / pl==0x2 来源手卡跟动）
        if (!skipAnim && ((ol & 0x7f) == 0x02 || (nl & 0x7f) == 0x02)) {
            field.updateHandLayout(0, 10);
            field.updateHandLayout(1, 10);
        }
        // 音效对齐 duelclient.cpp MSG_MOVE L2952-2957：仅非快进且真正移动时，除外播 BANISHED、
        // 效果破坏入墓播 DESTROYED；召唤/特召音效由独立的 MSG_SUMMONING/SP_SUMMONING 触发
        //（旧实现对每条 MSG_MOVE 无条件播 SUMMON，任何移动都响召唤音）
        if (!skipAnim && nl != ol) {
            if ((nl & 0x20) != 0) {
                playSfx(SoundManager.SFX.BANISHED);
            } else if ((reason & 0x2) != 0 && (nl & 0x10) != 0) {
                playSfx(SoundManager.SFX.DESTROYED);
            }
        }
        notifyField();
    }
    @Override public void onPosChange(int code, int ctrl, int loc, int seq, int oldPos, int newPos) {
        GameField.ClientCard card = field.getCard(ctrl, loc, seq);
        if (card != null) {
            // 镜像 DuelEventHandler.onPosChange（duelclient.cpp MSG_POS_CHANGE L3165-3174）：
            // 翻开清指示物/对象链接，卡码变化更新，再 MoveCard(10) 播里侧翻开/攻守互转转体动画
            if ((oldPos & GameField.POS_FACEUP) != 0 && (newPos & GameField.POS_FACEDOWN) != 0) {
                card.counters.clear();
                card.clearTarget();
            }
            if (code != 0 && card.code != code)
                card.setCode(code);
            card.position = newPos;
            if (!isSkipping) field.moveCardAnimated(card, 10);
            else field.setCardPos(card);
        }
        notifyField();
    }
    @Override public void onSet(int code, int ctrl, int loc, int seq) {
        // 对齐 duelclient.cpp MSG_SET：仅音效 + 事件提示，卡片由先到的 MSG_MOVE 已入区；
        // 旧实现在此处 new 卡覆盖：新卡 cur*=0 落世界原点被底板深度吞掉，盖卡看不见卡背
        GameField.ClientCard card = field.getCard(ctrl, loc, seq);
        if (card != null) {
            if (card.curX == 0f && card.curY == 0f && card.curZ == 0f) {
                // 兜底：极少数路径卡已在列表却从未定位，补一次定位（不覆盖 code/position）
                field.setCardPos(card);
            }
        } else {
            card = new GameField.ClientCard();
            card.owner = ctrl;
            card.code = code;
            card.position = GameField.POS_FACEDOWN;
            field.addCard(ctrl, loc, seq, card);
            field.setCardPos(card);
        }
        playSfx(SoundManager.SFX.SET);
        notifyField();
    }
    @Override public void onSwap(int c1c, int c1l, int c1s, int c2c, int c2l, int c2s) {
        GameField.ClientCard c1 = field.getCard(c1c, c1l, c1s);
        GameField.ClientCard c2 = field.getCard(c2c, c2l, c2s);
        field.addCard(c1c, c1l, c1s, c2);
        field.addCard(c2c, c2l, c2s, c1);
        // 对齐 duelclient.cpp MSG_SWAP L3210-3215：互换后两本体及各自素材全部 MoveCard(10)
        if (!isSkipping) {
            if (c1 != null) field.moveCardAnimated(c1, 10);
            if (c2 != null) field.moveCardAnimated(c2, 10);
            field.moveOverlayMaterials(c1, 10);
            field.moveOverlayMaterials(c2, 10);
        } else {
            if (c1 != null) field.setCardPos(c1);
            if (c2 != null) field.setCardPos(c2);
        }
        notifyField();
    }
    @Override public void onFieldDisabled(int disabledMask) {
        // 录像视角不做本地化（与 onEquip/onCardTarget 等同一策略，ctrl/players 恒为录制侧），
        // 直接存录制侧掩码供不可用格子交叉线绘制
        field.disabledField = disabledMask & 0xFFFFFFFFL;
        notifyField();
    }
    @Override public void onSummoning(int code, int ctrl, int loc, int seq) {
        playSfx(SoundManager.SFX.SUMMON);
        // 对齐 SummonAnimationManager.onSummoning：录像也触发 drawspec 居中卡片动画
        final int c = code;
        if (!isSkipping) mainHandler.post(() -> { if (listener != null) listener.onReplaySummonAnimation(c, SummonAnimationManager.SUMMON_NORMAL); });
    }
    @Override public void onSummoned() { notifyField(); }
    @Override public void onSpSummoning(int code, int ctrl, int loc, int seq) {
        playSfx(SoundManager.SFX.SPECIAL_SUMMON);
        final int c = code;
        if (!isSkipping) mainHandler.post(() -> { if (listener != null && c != 0) listener.onReplaySummonAnimation(c, SummonAnimationManager.SUMMON_SPECIAL); });
    }
    @Override public void onSpSummoned() { notifyField(); }
    @Override public void onFlipSummoning(int code, int ctrl, int loc, int seq) {
        playSfx(SoundManager.SFX.FLIP);
        final int c = code;
        if (!isSkipping) mainHandler.post(() -> { if (listener != null) listener.onReplaySummonAnimation(c, SummonAnimationManager.SUMMON_FLIP); });
    }
    @Override public void onFlipSummoned() { notifyField(); }

    @Override
    public void onChaining(int code, int pcc, int pcl, int pcs, int subs, int cc, int cl, int cs, int desc) {
        playSfx(SoundManager.SFX.ACTIVATE);
        // 连锁卡码序列（供 MSG_CHAIN_NEGATED/DISABLED 按 ct-1 取被无效卡码，对齐实况 chainCodes）
        chainCodes.add(code);
        // duelclient.cpp MSG_CHAINING L3345/L3366-3371：录像同样维护 current_chain，
        // 使卡片列表的「在连锁%d发动 / 被连锁%d的[%ls]选择为对象」状态标签在回放中一致
        field.currentChain = new GameField.ChainInfo();
        field.currentChain.chainCard = field.getCard(pcc & 1, pcl, pcs, subs);
        // 移植实况 DuelEventHandler.onChaining L3346-3349：发动卡（手卡/里侧）卡码揭示时
        // SetCode + MoveCard(10) 播背面→正面翻面转动动画，并派发发动大图 overlay
        GameField.ClientCard chainCard = field.currentChain.chainCard;
        if (msgMode && !isSkipping && chainCard != null && chainCard.code != code) {
            chainCard.setCode(code);
            field.moveCardAnimated(chainCard, 10);
            animHoldUntilMs = System.currentTimeMillis() + 1500L;
        }
        field.currentChain.code = code;
        field.currentChain.desc = desc;
        field.currentChain.controler = cc & 1;
        field.currentChain.location = cl;
        field.currentChain.sequence = cs;
        if (msgMode && !isSkipping) {
            final int fCode = code;
            final int fCtrl = cc & 1;
            mainHandler.post(() -> {
                if (listener != null) listener.onReplayChainAnimation(fCode, fCtrl, cl, cs);
            });
        }
    }

    @Override public void onChained(int chainCount) {
        if (field.currentChain != null && !field.chains.contains(field.currentChain)) {
            // 连锁图标位置快照（移植实况 onChained / duelclient.cpp MSG_CHAINED L3408）：
            // 此刻卡片尚未因结算离开原位，图标此后固定在此处直到连锁消失
            GameField.ClientCard cc = field.currentChain.chainCard;
            if (cc != null) {
                field.currentChain.iconX = cc.curX;
                field.currentChain.iconY = cc.curY;
                field.currentChain.iconZ = cc.curZ;
                field.currentChain.iconPosCaptured = true;
            }
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
        chainCodes.clear();
        field.currentChain = new GameField.ChainInfo();
        notifyField();
    }
    @Override public void onChainNegated(int chainCount) {
        playSfx(SoundManager.SFX.NEGATE);
        postReplayNegateVisual(chainCount);
    }
    @Override public void onChainDisabled(int chainCount) {
        playSfx(SoundManager.SFX.NEGATE);
        postReplayNegateVisual(chainCount);
    }
    /** 无效/失效卡片居中动画（移植 SummonAnimationManager.postNegatedAnimation：ct 为 1 基连锁序号） */
    private void postReplayNegateVisual(int chainCount) {
        if (!msgMode || isSkipping) return;
        final int code = (chainCount >= 1 && chainCount <= chainCodes.size())
                ? chainCodes.get(chainCount - 1) : 0;
        if (code == 0) return;
        animHoldUntilMs = System.currentTimeMillis() + 1500L;
        mainHandler.post(() -> {
            if (listener != null) listener.onReplayNegateAnimation(code);
        });
    }
    @Override public void onDraw(int player, int count, int[] codes) {
        // 纯消息模式非快进：逐张 5 帧节拍抽入（移植实况 onDraw/drawOneCard），消除同帧齐发
        if (msgMode && !isSkipping) {
            onDrawStaged(player & 1, count, codes);
            return;
        }
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
                if (hc == null) continue;
                // 快进重排直接落位不动画（与 onMove 的 skipAnim 同一策略）
                if (isSkipping) field.setCardPos(hc);
                else field.moveCardAnimated(hc, 10);
            }
        }
        playSfx(SoundManager.SFX.DRAW);
        notifyField();
    }

    /**
     * 逐张抽卡动画（移植 DuelEventHandler.onDraw L669-725，duelclient.cpp MSG_DRAW L3519-3557）：
     * 先给卡组顶被抽卡设卡码，再按 5 帧节拍逐张入手并让已有手卡重排让位。
     */
    private void onDrawStaged(final int p, int count, final int[] codes) {
        final int deckLoc = CardLocation.Deck.value();
        int top = field.getCardCount(p, deckLoc) - 1;
        for (int i = 0; i < count; i++) {
            GameField.ClientCard pcard = field.getCard(p, deckLoc, top - i);
            if (pcard != null && (!field.deckReversed || codes[i] != 0)) {
                pcard.setCode(codes[i] & 0x7fffffff);
            }
        }
        final long stepMs = 5L * 17L;
        for (int i = 0; i < count; i++) {
            final int idx = i;
            mainHandler.postDelayed(() -> drawOneCardMsg(p, deckLoc, codes, idx), idx * stepMs);
        }
        // 抽卡展示持闸：最后一张延迟 (count-1)*5 帧启动 + 10 帧飞行 + 尾帧余量
        animHoldUntilMs = System.currentTimeMillis() + ((count - 1L) * 5L + 15L) * 17L;
        notifyField();
    }

    /** 单张抽卡（onDrawStaged 按 5 帧节拍调度，移植实况 drawOneCard） */
    private void drawOneCardMsg(int p, int deckLoc, int[] codes, int idx) {
        int t = field.getCardCount(p, deckLoc) - 1;
        if (t < 0) return;
        GameField.ClientCard pcard = field.removeCard(p, deckLoc, t);
        if (pcard == null) {
            pcard = new GameField.ClientCard();
            pcard.owner = p;
            pcard.controler = p;
            if (idx < codes.length) pcard.setCode(codes[idx] & 0x7fffffff);
        }
        field.addCard(p, CardLocation.Hand.value(), 0, pcard);
        for (GameField.ClientCard hc : field.players[p].hand) {
            if (hc != null) field.moveCardAnimated(hc, 10);
        }
        playSfx(SoundManager.SFX.DRAW);
        notifyField();
    }
    @Override public void onDamage(int player, int amount) {
        field.players[player].lp -= amount;
        if (field.players[player].lp < 0) field.players[player].lp = 0;
        if (isSkipping) {
            // 快进重排：LP 直接落值不播浮字动画（等价 C++ isReplaySkiping）
            field.dInfo.lp[player] = field.players[player].lp;
            return;
        }
        field.startLpChange(player, field.players[player].lp, 0xFFFF0000, "-" + amount, true);
        playSfx(SoundManager.SFX.DAMAGE);
        mainHandler.post(() -> { if (listener != null) listener.onReplayPlayerInfoUpdated(player); });
    }
    @Override public void onRecover(int player, int amount) {
        field.players[player].lp += amount;
        if (isSkipping) {
            field.dInfo.lp[player] = field.players[player].lp;
            return;
        }
        field.startLpChange(player, field.players[player].lp, 0xFF00FF00, "+" + amount, true);
        playSfx(SoundManager.SFX.RECOVER);
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
        // 对齐实况 DuelEventHandler.onEquip L372：装备音效（旧实现缺失）
        playSfx(SoundManager.SFX.EQUIP);
        notifyField();
    }
    @Override public void onLpUpdate(int player, int lp) {
        field.players[player].lp = lp;
        if (isSkipping) {
            field.dInfo.lp[player] = lp;
            return;
        }
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
        if (isSkipping) {
            field.dInfo.lp[player] = field.players[player].lp;
            return;
        }
        field.startLpChange(player, field.players[player].lp, 0, null, false);
        mainHandler.post(() -> { if (listener != null) listener.onReplayPlayerInfoUpdated(player); });
    }
    @Override public void onAddCounter(int type, int ctrl, int loc, int seq, int count) {
        // 对齐实况 DuelEventHandler.onAddCounter：卡片计数器写入 + 音效（纯消息模式下
        // 卡面不经引擎重查，计数器只能由此消息维护）
        GameField.ClientCard card = field.getCard(ctrl & 1, loc, seq);
        if (card != null) {
            card.counters.put(type, count);
        }
        playSfx(SoundManager.SFX.COUNTER_ADD);
        notifyField();
    }
    @Override public void onRemoveCounter(int type, int ctrl, int loc, int seq, int count) {
        GameField.ClientCard card = field.getCard(ctrl & 1, loc, seq);
        if (card != null) {
            card.counters.remove(type);
        }
        playSfx(SoundManager.SFX.COUNTER_REMOVE);
        notifyField();
    }
    @Override public void onAttack(int ac, int al, int as, int dc, int dl, int ds) {
        // 移植实况 DuelEventHandler.onAttack（duelclient.cpp MSG_ATTACK L3830-3864）：
        // 有目标攻怪音效 ATTACK + 绿色攻击弧（GameFieldView 约 0.9s 内绘流动弧），
        // 直接攻击音效 DIRECT_ATTACK 且弧落到对方场地一侧（arcTarget=null）
        GameField.ClientCard atkCard = field.getCard(ac & 1, al, as);
        GameField.ClientCard defCard = dl != 0 ? field.getCard(dc & 1, dl, ds) : null;
        if (dl != 0) {
            playSfx(SoundManager.SFX.ATTACK);
        } else {
            playSfx(SoundManager.SFX.DIRECT_ATTACK);
        }
        if (!isSkipping) {
            field.arcAttacker = atkCard;
            field.arcTarget = defCard;
            field.arcStartMs = System.currentTimeMillis();
            // 对齐实况 WaitFrameSignal(40)+ATTACK_HOLD_MS：弧光展示期内后续消息不得抢先
            animHoldUntilMs = System.currentTimeMillis() + 900L;
        }
        notifyField();
    }
    @Override public void onBattle(int aa, boolean ap, int da, boolean dp) {}
    @Override public void onAttackDisabled() {}
    @Override public void onDamageStepStart() {}
    @Override public void onDamageStepEnd() { notifyField(); }
    @Override public void onMissedEffect(int code, int ctrl, int loc, int seq, int effectId) {}
    @Override public void onTossCoin(int player, int count, ByteBuffer results) { playSfx(SoundManager.SFX.COIN); }
    @Override public void onTossDice(int player, int count, ByteBuffer results) { playSfx(SoundManager.SFX.DICE); }
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
    @Override public void onAiName(String name) {
        Log.i(TAG, "AI name: " + name);
    }
    @Override public void onShowHint(String hint) {
        // 纯消息模式静默（同上），保留日志
        if (msgMode) {
            Log.d(TAG, "showHint: " + hint);
            return;
        }
        mainHandler.post(() -> { if (listener != null) listener.onReplayHintMessage(hint); });
    }
    @Override public void onMatchKill(int code) {}
    @Override public void onCustomMsg(String msg) {}
    @Override public void onDuelWinner(int player, int reason) { onWin(player, reason); }

    private void notifyField() {
        // 快进/回退重放期间不派发刷帧：上一步对用户应无感（内部不可见地重放，
        // 落点后统一刷一次），而非看着从头播一遍
        if (isSkipping) return;
        mainHandler.post(() -> { if (listener != null) listener.onReplayFieldChanged(); });
    }

    /** 音效派发：快进/回退重放期间全部静默（否则回退一次会把整局音效快进重放） */
    private void playSfx(SoundManager.SFX sfx) {
        if (isSkipping) return;
        soundManager.playSoundEffect(sfx);
    }
}
