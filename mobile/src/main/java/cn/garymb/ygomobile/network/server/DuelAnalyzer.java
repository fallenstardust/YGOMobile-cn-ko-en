package cn.garymb.ygomobile.network.server;

import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import cn.garymb.ygomobile.engine.OcgDuelEngine;
import cn.garymb.ygomobile.network.YGOProtocol;

/**
 * 引擎消息解析器，移植 {@code gframe/single_duel.cpp}：逐条消费 ocgcore 消息流，按 is_host 遮蔽后广播 {@code STOC_GAME_MSG}；不持有决斗状态，均读写自宿主 {@link ServerDuel}。
 */
final class DuelAnalyzer implements YGOProtocol {

    private static final String TAG = "DuelAnalyzer";

    static final int REFRESH_MZONE_FLAG = 0x881fff;
    static final int REFRESH_SZONE_FLAG = 0x681fff;
    static final int REFRESH_HAND_FLAG = 0x681fff;
    static final int REFRESH_GRAVE_FLAG = 0x81fff;
    static final int REFRESH_EXTRA_FLAG = 0xe81fff;
    static final int REFRESH_SINGLE_FLAG = 0xf81fff;
    private static final int SHUFFLE_HAND_FLAG = 0x781fff;
    private static final int SHUFFLE_SET_FLAG = 0x181fff;

    /** 撤回全量重同步专用 Mzone 掩码：{@link #REFRESH_MZONE_FLAG} 之上追加 {@code QUERY_OVERLAY_CARD}，补发素材卡码（对齐 ocgapi.cpp，否则叠放素材渲染成卡背）。 */
    static final int RESYNC_MZONE_FLAG = REFRESH_MZONE_FLAG | OcgDuelEngine.QUERY_OVERLAY_CARD;

    static final int QUERY_CODE_POSITION = OcgDuelEngine.QUERY_CODE | OcgDuelEngine.QUERY_POSITION;
    static final int MSG_HEADER_LEN = 3; // MSG_UPDATE_DATA + player + location
    static final int CARD_HEADER_LEN = 4; // MSG_UPDATE_CARD + player + location + sequence

    final ServerDuel owner;
    final GameRoom room;
    final boolean soloMode;

    // 撤回（undo）支持：静默重放与重同步

    /** 静默重放态：{@link ServerDuel} 为撤回重建引擎并逐条重放历史应答时置真——出站被压制但录制/遮蔽语义不变。 */
    boolean silent;
    /** 只发不录态：撤回后全量重同步属传输层补偿，不写进录像。 */
    boolean recordSuppressed;
    /** 静默重放期间捕获的终端询问消息原字节（撤回后原样重发给待应答方）。 */
    byte[] silentTerminal;
    /** waitforResponse 已置位、等待下一条 sendToPlayer 作为终端询问的标记。 */
    boolean silentWaitPending;
    /** 最近一次下发给某席位的询问报文原字节（MSG_SELECT_*，首字节 10..26）；询问权威源在服务端，RETRY 时靠它原样重发。 */
    byte[] liveQuestion;
    /** {@link #liveQuestion} 挂给的协议席位（-1=无），由 {@link #waitforResponse(int)} 随每次挂起一并记下。 */
    int liveQuestionResponder = -1;
    /** 本次询问挂出的时刻（毫秒），询问保活据此判断「挂了但迟迟没有答复」。 */
    long liveQuestionAtMs;
    /** 本询问已被原样重发的次数（上限由调用方给定，防止与客户端的坏应答形成乒乓）。 */
    int liveQuestionRedeliveries;
    /** 本询问是否来自撤回的延后重挂；保活心跳只复查这一路（撤回是唯一会抹掉再重挂已挂询问的操作）。 */
    boolean liveQuestionFromUndo;
    /** 询问代次：每挂起一次自增，与 {@link #liveQuestionGeneration} 配对才判定 liveQuestion 是否为引擎当前在等的那条。 */
    int askGeneration;
    /** {@link #liveQuestion} 所属的等待代次（-1 = 无）。 */
    int liveQuestionGeneration = -1;
    /** 同一条询问被连续判非法的次数（只在 MSG_RETRY 累加），用于识别「对端已答不出这道题」的死局。 */
    int retryStreak;
    /** {@link #retryStreak} 对应的那一条询问字节（用于判断「还是同一道题」）。 */
    byte[] lastRetryAsk;
    /** 本次 {@link #sendToPlayer} 是否为 RETRY / 自愈之后的原样重发（重发不该把重试计数清零）。 */
    boolean resendingAfterRetry;
    /** 已施过熔断重答的询问代次：同一条询问至多代替它答一次，绝不反复代答。 */
    int deadlockBreakGeneration = -1;
    /** 连续非法应答达到这个次数即启动熔断重答（正常的客户端不会连续三次答错同一道题）。 */
    private static final int RETRY_STREAK_LIMIT = 3;
    /** 撤回重同步待重挂询问的席位（-1 = 无待重挂）；见 {@link #hangUndoTerminalQuestion()}。 */
    int undoAskResponder = -1;
    /** 静默重放期间统计的回合数（客户端 field.turnCount 需与之对齐，MSG_RELOAD_FIELD 不带回合号）。 */
    int silentTurnCount;
    /** 最后一条 MSG_NEW_TURN 的协议侧玩家索引 / 最后一条 MSG_NEW_PHASE 的阶段值。 */
    int silentTurnPlayer;
    int silentPhase;

    /** 当前挂起询问是否为「行动宣言」询问（IDLECMD/BATTLECMD）：撤回以此为锚点整段回退，由 {@link ServerDuel#getResponse} 消费后清零。 */
    int pendingActionQuestion;
    /** 最近一条 MSG_NEW_PHASE 的阶段值（协议侧位掩码），live 与重放均更新；供 {@link #isPhaseActivationAnchor} 判定 DP/SP。 */
    int livePhase;
    /** 最近一条 MSG_NEW_TURN 的当前回合玩家（协议侧，-1=未定）。live 与重放均更新。 */
    int liveTurnPlayer = -1;
    /** 是否正处于连锁结算中（MSG_CHAINING 置真、CHAIN_END/新回合/新阶段复位）；DP/SP 发动询问仅在不在连锁中才计为锚点。 */
    boolean liveChainActive;

    // 协作类（同包，持 analyzer 反向引用，共享状态读写本体）
    final DuelViewBroadcaster broadcaster;
    final DuelZoneRefresh zoneRefresh;
    final UndoQuestionGuard undoGuard;

    DuelAnalyzer(ServerDuel owner) {
        this.owner = owner;
        this.room = owner.room;
        this.soloMode = room.soloMode;
        this.broadcaster = new DuelViewBroadcaster(this);
        this.zoneRefresh = new DuelZoneRefresh(this);
        this.undoGuard = new UndoQuestionGuard(this);
    }

    /** 进入/退出静默重放：清零捕获槽，后续 analyze 只录不发（实现见 UndoQuestionGuard）。 */
    void beginSilentReplay() { undoGuard.beginSilentReplay(); }
    void endSilentReplay() { undoGuard.endSilentReplay(); }

    long pduel() { return owner.pduel; }

    // 引擎消息解析主循环

    /** @return 0=继续取消息；1=已挂起等待玩家应答；2=决斗结束（WIN）。 */
    int analyze(byte[] msg, int len) {
        int cursor = 0;
        while (cursor < len) {
            int start = cursor;
            int engType = msg[cursor] & 0xFF;
            cursor++;
            int player;
            int count;
            switch (engType) {
                case EngineMessage.MSG_RETRY: {
                    if (silent) {
                        // 同 seed + 同应答序列是决定性的，静默重放不应出现 RETRY；
                        // 一旦推出则说明重建偏离首跑，按“重建失败”交调用方回滚到撤回前局面
                        silentTerminal = null;
                        return 2;
                    }
                    if (owner.lastReplayResponseSize != 0) {
                        owner.replay.removeData(owner.lastReplayResponseSize);
                        owner.lastReplayResponseSize = 0;
                    }
                    // 应答不合法（引擎重问）：它不构成可撤回的一步，弹出刚压入的快照
                    owner.discardPendingUndoStep();
                    // 询问权威源在服务端：直接把当前询问重发给应答方使界面必定重挂可应答项；仅无留存询问时才退回 C++ 语义（1 字节 MSG_RETRY）。
                    // 须在 waitforResponse 之前取：它会推进代次，取晚了配对判断恒为否
                    byte[] ask = hasLiveAsk() ? liveQuestion : null;
                    int rseat = room.lastResponse;
                    retryStreak = (ask != null && Arrays.equals(ask, lastRetryAsk)) ? retryStreak + 1 : 1;
                    lastRetryAsk = ask;
                    Log.w(TAG, "MSG_RETRY: invalid answer from seat " + rseat
                            + " for question type=" + (ask == null ? -1 : (ask[0] & 0xFF))
                            + " streak=" + retryStreak);
                    // 熔断：同一道题连续 RETRY_STREAK_LIMIT 次非法答复——只代非本方席位答「放弃也合法」的那几类询问，代答后引擎继续推进
                    if (retryStreak >= RETRY_STREAK_LIMIT && ask != null && !room.soloMode
                            && deadlockBreakGeneration != askGeneration
                            && rseat > 0 && rseat < room.players.length
                            && room.players[rseat] != null) {
                        byte[] pass = defaultPassAnswer(ask[0] & 0xFF);
                        if (pass != null) {
                            deadlockBreakGeneration = askGeneration;
                            retryStreak = 0;
                            lastRetryAsk = null;
                            Log.e(TAG, "deadlock breaker: seat " + rseat
                                    + " answered the same question invalidly " + RETRY_STREAK_LIMIT
                                    + " times, feeding a default pass (type=" + (ask[0] & 0xFF) + ")");
                            owner.applyForcedPass(rseat, pass);
                            // 返回 0：交调用方的引擎循环接着把这份应答吃掉并继续处理
                            return 0;
                        }
                    }
                    waitforResponse(rseat);
                    resendingAfterRetry = true;
                    if (ask != null) {
                        sendToPlayer(room.players[rseat], ask);
                    } else {
                        sendToPlayer(room.players[rseat], range(msg, start, cursor));
                    }
                    return 1;
                }
                case EngineMessage.MSG_HINT: {
                    int type = msg[cursor] & 0xFF;
                    cursor++;
                    player = msg[cursor] & 0xFF;
                    cursor++;
                    cursor += 4;
                    byte[] slice = range(msg, start, cursor);
                    switch (type) {
                        case 1:
                        case 2:
                        case 3:
                        case 5:
                            sendToPlayer(room.players[player], slice);
                            break;
                        case 4:
                        case 6:
                        case 7:
                        case 8:
                        case 9:
                        case 11:
                            sendToPlayer(room.players[1 - player], slice);
                            sendToObservers(slice);
                            break;
                        case 10:
                            broadcastMsg(slice);
                            break;
                        default:
                            break;
                    }
                    break;
                }
                case EngineMessage.MSG_WIN: {
                    player = msg[cursor] & 0xFF;
                    cursor++;
                    cursor++; // reason type
                    if (silent) {
                        // 静默重放中不应跑到终局（回退点一定在 WIN 之前）：
                        // 不做任何 match 副作用、不结束录像，交调用方判定重建失败
                        silentTerminal = null;
                        return 2;
                    }
                    broadcastMsg(range(msg, start, cursor));
                    if (player > 1) {
                        room.matchResult[room.duelCount++] = 2;
                        room.tpPlayer = 1 - room.tpPlayer;
                    } else if (room.players[player] == room.pplayer[player]) {
                        room.matchResult[room.duelCount++] = player;
                        room.tpPlayer = 1 - player;
                    } else {
                        room.matchResult[room.duelCount++] = 1 - player;
                        room.tpPlayer = player;
                    }
                    owner.endDuel();
                    return 2;
                }
                case EngineMessage.MSG_SELECT_BATTLECMD: {
                    player = msg[cursor] & 0xFF;
                    cursor++;
                    count = msg[cursor] & 0xFF;
                    cursor++;
                    cursor += count * 11;
                    count = msg[cursor] & 0xFF;
                    cursor++;
                    cursor += count * 8 + 2;
                    refreshMzone(0);
                    refreshMzone(1);
                    refreshSzone(0);
                    refreshSzone(1);
                    refreshHand(0);
                    refreshHand(1);
                    waitforResponse(player);
                    // 战斗指令的应答 = 攻击宣言 / 切 M2 / 结束阶段，是撤回的动作锚点
                    pendingActionQuestion = EngineMessage.MSG_SELECT_BATTLECMD;
                    sendToPlayer(room.players[player], range(msg, start, cursor));
                    return 1;
                }
                case EngineMessage.MSG_SELECT_IDLECMD: {
                    player = msg[cursor] & 0xFF;
                    cursor++;
                    for (int g = 0; g < 5; g++) {
                        count = msg[cursor] & 0xFF;
                        cursor++;
                        cursor += count * 7;
                    }
                    count = msg[cursor] & 0xFF;
                    cursor++;
                    cursor += count * 11 + 3;
                    refreshMzone(0);
                    refreshMzone(1);
                    refreshSzone(0);
                    refreshSzone(1);
                    refreshHand(0);
                    refreshHand(1);
                    waitforResponse(player);
                    // 空闲指令的应答 = 召唤/特召/盖卡/发动/切阶段，是撤回的动作锚点
                    pendingActionQuestion = EngineMessage.MSG_SELECT_IDLECMD;
                    sendToPlayer(room.players[player], range(msg, start, cursor));
                    return 1;
                }
                case EngineMessage.MSG_SELECT_EFFECTYN: {
                    player = msg[cursor] & 0xFF;
                    cursor++;
                    cursor += 12;
                    waitforResponse(player);
                    // DP/SP 本方主动发动的是/否询问：预挂锚点属性（应答为「发动」才真正记锚，见 ServerDuel.getResponse）
                    if (isPhaseActivationAnchor(player, EngineMessage.MSG_SELECT_EFFECTYN)) {
                        pendingActionQuestion = EngineMessage.MSG_SELECT_EFFECTYN;
                    }
                    sendToPlayer(room.players[player], range(msg, start, cursor));
                    return 1;
                }
                case EngineMessage.MSG_SELECT_YESNO: {
                    player = msg[cursor] & 0xFF;
                    cursor++;
                    cursor += 4;
                    waitforResponse(player);
                    sendToPlayer(room.players[player], range(msg, start, cursor));
                    return 1;
                }
                case EngineMessage.MSG_SELECT_OPTION: {
                    player = msg[cursor] & 0xFF;
                    cursor++;
                    count = msg[cursor] & 0xFF;
                    cursor++;
                    cursor += count * 4;
                    waitforResponse(player);
                    sendToPlayer(room.players[player], range(msg, start, cursor));
                    return 1;
                }
                case EngineMessage.MSG_SELECT_CARD:
                case EngineMessage.MSG_SELECT_TRIBUTE: {
                    player = msg[cursor] & 0xFF;
                    cursor++;
                    cursor += 3;
                    count = msg[cursor] & 0xFF;
                    cursor++;
                    cursor = maskCodesForOpponent(msg, cursor, count, player);
                    waitforResponse(player);
                    sendToPlayer(room.players[player], range(msg, start, cursor));
                    return 1;
                }
                case EngineMessage.MSG_SELECT_UNSELECT_CARD: {
                    player = msg[cursor] & 0xFF;
                    cursor++;
                    cursor += 4;
                    count = msg[cursor] & 0xFF;
                    cursor++;
                    cursor = maskCodesForOpponent(msg, cursor, count, player);
                    count = msg[cursor] & 0xFF;
                    cursor++;
                    cursor = maskCodesForOpponent(msg, cursor, count, player);
                    waitforResponse(player);
                    sendToPlayer(room.players[player], range(msg, start, cursor));
                    return 1;
                }
                case EngineMessage.MSG_SELECT_CHAIN: {
                    player = msg[cursor] & 0xFF;
                    cursor++;
                    count = msg[cursor] & 0xFF;
                    cursor++;
                    cursor += 9 + count * 14;
                    waitforResponse(player);
                    // DP/SP 本方主动发动的连锁列表询问：预挂锚点属性（选中连锁项才真正记锚，见 ServerDuel.getResponse）
                    if (isPhaseActivationAnchor(player, EngineMessage.MSG_SELECT_CHAIN)) {
                        pendingActionQuestion = EngineMessage.MSG_SELECT_CHAIN;
                    }
                    sendToPlayer(room.players[player], range(msg, start, cursor));
                    return 1;
                }
                case EngineMessage.MSG_SELECT_PLACE:
                case EngineMessage.MSG_SELECT_DISFIELD: {
                    player = msg[cursor] & 0xFF;
                    cursor++;
                    cursor += 5;
                    waitforResponse(player);
                    sendToPlayer(room.players[player], range(msg, start, cursor));
                    return 1;
                }
                case EngineMessage.MSG_SELECT_POSITION: {
                    player = msg[cursor] & 0xFF;
                    cursor++;
                    cursor += 5;
                    waitforResponse(player);
                    sendToPlayer(room.players[player], range(msg, start, cursor));
                    return 1;
                }
                case EngineMessage.MSG_SELECT_COUNTER: {
                    player = msg[cursor] & 0xFF;
                    cursor++;
                    cursor += 4;
                    count = msg[cursor] & 0xFF;
                    cursor++;
                    cursor += count * 9;
                    waitforResponse(player);
                    sendToPlayer(room.players[player], range(msg, start, cursor));
                    return 1;
                }
                case EngineMessage.MSG_SELECT_SUM: {
                    cursor++; // skip leading byte
                    player = msg[cursor] & 0xFF;
                    cursor++;
                    cursor += 6;
                    count = msg[cursor] & 0xFF;
                    cursor++;
                    cursor += count * 11;
                    count = msg[cursor] & 0xFF;
                    cursor++;
                    cursor += count * 11;
                    waitforResponse(player);
                    sendToPlayer(room.players[player], range(msg, start, cursor));
                    return 1;
                }
                case EngineMessage.MSG_SORT_CARD: {
                    player = msg[cursor] & 0xFF;
                    cursor++;
                    count = msg[cursor] & 0xFF;
                    cursor++;
                    cursor += count * 7;
                    waitforResponse(player);
                    sendToPlayer(room.players[player], range(msg, start, cursor));
                    return 1;
                }
                case EngineMessage.MSG_CONFIRM_DECKTOP:
                case EngineMessage.MSG_CONFIRM_EXTRATOP: {
                    cursor++; // player
                    count = msg[cursor] & 0xFF;
                    cursor++;
                    cursor += count * 7;
                    broadcastMsg(range(msg, start, cursor));
                    break;
                }
                case EngineMessage.MSG_CONFIRM_CARDS: {
                    player = msg[cursor] & 0xFF;
                    cursor++;
                    cursor++; // op byte
                    count = msg[cursor] & 0xFF;
                    cursor++;
                    int locByte = msg[cursor + 5] & 0xFF;
                    cursor += count * 7;
                    byte[] slice = range(msg, start, cursor);
                    if (locByte != OcgDuelEngine.LOCATION_DECK) {
                        sendToPlayer(room.players[player], slice);
                        // Solo dedupe: the two slots share one connection.
                        if (room.players[1 - player] != room.players[player]) {
                            sendToPlayer(room.players[1 - player], slice);
                        }
                        sendToObservers(slice);
                    } else {
                        sendToPlayer(room.players[player], slice);
                    }
                    break;
                }
                case EngineMessage.MSG_SHUFFLE_DECK: {
                    cursor++; // player
                    broadcastMsg(range(msg, start, cursor));
                    break;
                }
                case EngineMessage.MSG_SHUFFLE_HAND: {
                    player = msg[cursor] & 0xFF;
                    cursor++;
                    count = msg[cursor] & 0xFF;
                    cursor++;
                    int codeBase = cursor;
                    cursor += count * 4;
                    byte[] full = range(msg, start, cursor);
                    for (int i = 0; i < count; i++) {
                        writeInt32(msg, codeBase + i * 4, 0);
                    }
                    byte[] masked = range(msg, start, cursor);
                    sendDualViewAndRecord(player, full, masked);
                    refreshHand(player, SHUFFLE_HAND_FLAG, 0);
                    break;
                }
                case EngineMessage.MSG_SHUFFLE_EXTRA: {
                    player = msg[cursor] & 0xFF;
                    cursor++;
                    count = msg[cursor] & 0xFF;
                    cursor++;
                    int codeBase = cursor;
                    cursor += count * 4;
                    byte[] full = range(msg, start, cursor);
                    for (int i = 0; i < count; i++) {
                        writeInt32(msg, codeBase + i * 4, 0);
                    }
                    byte[] masked = range(msg, start, cursor);
                    sendDualViewAndRecord(player, full, masked);
                    refreshExtra(player);
                    break;
                }
                case EngineMessage.MSG_REFRESH_DECK: {
                    cursor++;
                    broadcastMsg(range(msg, start, cursor));
                    break;
                }
                case EngineMessage.MSG_SWAP_GRAVE_DECK: {
                    player = msg[cursor] & 0xFF;
                    cursor++;
                    broadcastMsg(range(msg, start, cursor));
                    refreshGrave(player);
                    break;
                }
                case EngineMessage.MSG_REVERSE_DECK: {
                    broadcastMsg(range(msg, start, cursor));
                    break;
                }
                case EngineMessage.MSG_DECK_TOP: {
                    cursor += 6;
                    broadcastMsg(range(msg, start, cursor));
                    break;
                }
                case EngineMessage.MSG_SHUFFLE_SET_CARD: {
                    int loc = msg[cursor] & 0xFF;
                    cursor++;
                    count = msg[cursor] & 0xFF;
                    cursor++;
                    cursor += count * 8;
                    broadcastMsg(range(msg, start, cursor));
                    if (loc == OcgDuelEngine.LOCATION_MZONE) {
                        refreshMzone(0, SHUFFLE_SET_FLAG, 0);
                        refreshMzone(1, SHUFFLE_SET_FLAG, 0);
                    } else {
                        refreshSzone(0, SHUFFLE_SET_FLAG, 0);
                        refreshSzone(1, SHUFFLE_SET_FLAG, 0);
                    }
                    break;
                }
                case EngineMessage.MSG_NEW_TURN: {
                    refreshMzone(0);
                    refreshMzone(1);
                    refreshSzone(0);
                    refreshSzone(1);
                    refreshHand(0);
                    refreshHand(1);
                    cursor++;
                    // live 与重放均记录当前回合玩家（协议侧）与连锁态，供 DP/SP 发动锚点判定（见 isPhaseActivationAnchor）
                    liveTurnPlayer = msg[start + 1] & 0xFF;
                    liveChainActive = false;
                    if (silent) {
                        // 重放只统计回合数与回合方，不重置限时（由 ServerDuel 从弹出步的快照回填）
                        silentTurnCount++;
                        silentTurnPlayer = msg[start + 1] & 0xFF;
                    } else {
                        room.timeLimit[0] = room.hostInfo.timeLimit;
                        room.timeLimit[1] = room.hostInfo.timeLimit;
                    }
                    broadcastMsg(range(msg, start, cursor));
                    break;
                }
                case EngineMessage.MSG_NEW_PHASE: {
                    cursor += 2;
                    int ph = (msg[start + 1] & 0xFF) | ((msg[start + 2] & 0xFF) << 8);
                    livePhase = ph;
                    // 连锁不跨阶段：阶段切换复位连锁进行标志（防漏收 MSG_CHAIN_END 的防御）
                    liveChainActive = false;
                    if (silent) {
                        silentPhase = ph;
                    }
                    broadcastMsg(range(msg, start, cursor));
                    refreshMzone(0);
                    refreshMzone(1);
                    refreshSzone(0);
                    refreshSzone(1);
                    refreshHand(0);
                    refreshHand(1);
                    break;
                }
                case EngineMessage.MSG_MOVE: {
                    int body = cursor;
                    int pc = msg[body + 4] & 0xFF;
                    int pl = msg[body + 5] & 0xFF;
                    int cc = msg[body + 8] & 0xFF;
                    int cl = msg[body + 9] & 0xFF;
                    int cs = msg[body + 10] & 0xFF;
                    int cp = msg[body + 11] & 0xFF;
                    boolean hide = EngineMessage.shouldHideFacedownCode(cp);
                    if ((cl & EngineMessage.LOCATION_ONFIELD) != 0) {
                        cp = EngineMessage.stripRevealFlag(msg, body + 8);
                    }
                    // 效果将非抽卡入手的卡向对手公开卡码（PSCT：非抽卡入手需展示，客户端据此翻面确认）；
                    // 正常抽卡走 MSG_DRAW 不经此分支，随后 MSG_SHUFFLE_HAND/refreshHand 会重新清零里侧手卡。
                    boolean toHand = (cl & EngineMessage.LOCATION_HAND) != 0
                            && (pl & EngineMessage.LOCATION_HAND) == 0;
                    cursor = body + 16;
                    byte[] full = range(msg, start, cursor);
                    if (!toHand && (cl & (OcgDuelEngine.LOCATION_GRAVE | EngineMessage.LOCATION_OVERLAY)) == 0
                            && (((cl & (OcgDuelEngine.LOCATION_DECK | OcgDuelEngine.LOCATION_HAND)) != 0) || hide)) {
                        writeInt32(msg, body, 0);
                    }
                    byte[] masked = range(msg, start, cursor);
                    sendDualViewAndRecord(cc, full, masked);
                    if (cl != 0 && (cl & EngineMessage.LOCATION_OVERLAY) == 0 && (cl != pl || pc != cc)) {
                        refreshSingle(cc, cl, cs);
                    }
                    break;
                }
                case EngineMessage.MSG_POS_CHANGE: {
                    int body = cursor;
                    int cc = msg[body + 4] & 0xFF;
                    int cl = msg[body + 5] & 0xFF;
                    int cs = msg[body + 6] & 0xFF;
                    int pp = msg[body + 7] & 0xFF;
                    int cp = msg[body + 8] & 0xFF;
                    cursor = body + 9;
                    broadcastMsg(range(msg, start, cursor));
                    if ((pp & EngineMessage.POS_FACEDOWN) != 0 && (cp & EngineMessage.POS_FACEUP) != 0) {
                        refreshSingle(cc, cl, cs);
                    }
                    break;
                }
                case EngineMessage.MSG_SET: {
                    // 对齐 single_duel.cpp L994-996：Java writeInt32 不推游标，此处必须 cursor += 8（旧值 +=4 会错位致后续消息误解析→卡死）。
                    byte[] full = range(msg, start, cursor + 8);
                    recordFrame(full);
                    writeInt32(msg, cursor, 0);
                    cursor += 8;
                    byte[] masked = range(msg, start, cursor);
                    if (room.players[0] != null) {
                        emit(room.players[0], masked);
                    }
                    // Solo dedupe: players[1] may be the same connection as players[0].
                    if (room.players[1] != null && room.players[1] != room.players[0]) {
                        emit(room.players[1], masked);
                    }
                    emitObservers(masked);
                    break;
                }
                case EngineMessage.MSG_SWAP: {
                    int body = cursor;
                    int c1 = msg[body + 4] & 0xFF;
                    int l1 = msg[body + 5] & 0xFF;
                    int s1 = msg[body + 6] & 0xFF;
                    int c2 = msg[body + 12] & 0xFF;
                    int l2 = msg[body + 13] & 0xFF;
                    int s2 = msg[body + 14] & 0xFF;
                    cursor = body + 16;
                    broadcastMsg(range(msg, start, cursor));
                    refreshSingle(c1, l1, s1);
                    refreshSingle(c2, l2, s2);
                    break;
                }
                case EngineMessage.MSG_FIELD_DISABLED: {
                    cursor += 4;
                    broadcastMsg(range(msg, start, cursor));
                    break;
                }
                case EngineMessage.MSG_SUMMONING: {
                    cursor += 8;
                    broadcastMsg(range(msg, start, cursor));
                    break;
                }
                case EngineMessage.MSG_SUMMONED: {
                    broadcastMsg(range(msg, start, cursor));
                    refreshMzone(0);
                    refreshMzone(1);
                    refreshSzone(0);
                    refreshSzone(1);
                    break;
                }
                case EngineMessage.MSG_SPSUMMONING: {
                    int body = cursor;
                    int cc = msg[body + 4] & 0xFF;
                    int cp = msg[body + 7] & 0xFF;
                    boolean hide = EngineMessage.shouldHideFacedownCode(cp);
                    EngineMessage.stripRevealFlag(msg, body + 4);
                    cursor = body + 8;
                    byte[] full = range(msg, start, cursor);
                    if (hide) {
                        writeInt32(msg, body, 0);
                    }
                    byte[] masked = range(msg, start, cursor);
                    sendDualViewAndRecord(cc, full, masked);
                    break;
                }
                case EngineMessage.MSG_SPSUMMONED: {
                    broadcastMsg(range(msg, start, cursor));
                    refreshMzone(0);
                    refreshMzone(1);
                    refreshSzone(0);
                    refreshSzone(1);
                    break;
                }
                case EngineMessage.MSG_FLIPSUMMONING: {
                    int body = cursor;
                    refreshSingle(msg[body + 4] & 0xFF, msg[body + 5] & 0xFF, msg[body + 6] & 0xFF);
                    cursor = body + 8;
                    broadcastMsg(range(msg, start, cursor));
                    break;
                }
                case EngineMessage.MSG_FLIPSUMMONED: {
                    broadcastMsg(range(msg, start, cursor));
                    refreshMzone(0);
                    refreshMzone(1);
                    refreshSzone(0);
                    refreshSzone(1);
                    break;
                }
                case EngineMessage.MSG_CHAINING: {
                    cursor += 16;
                    liveChainActive = true;
                    broadcastMsg(range(msg, start, cursor));
                    break;
                }
                case EngineMessage.MSG_CHAINED: {
                    cursor++;
                    broadcastMsg(range(msg, start, cursor));
                    refreshMzone(0);
                    refreshMzone(1);
                    refreshSzone(0);
                    refreshSzone(1);
                    refreshHand(0);
                    refreshHand(1);
                    break;
                }
                case EngineMessage.MSG_CHAIN_SOLVING: {
                    cursor++;
                    broadcastMsg(range(msg, start, cursor));
                    break;
                }
                case EngineMessage.MSG_CHAIN_SOLVED: {
                    cursor++;
                    broadcastMsg(range(msg, start, cursor));
                    refreshMzone(0);
                    refreshMzone(1);
                    refreshSzone(0);
                    refreshSzone(1);
                    refreshHand(0);
                    refreshHand(1);
                    break;
                }
                case EngineMessage.MSG_CHAIN_END: {
                    liveChainActive = false;
                    broadcastMsg(range(msg, start, cursor));
                    refreshMzone(0);
                    refreshMzone(1);
                    refreshSzone(0);
                    refreshSzone(1);
                    refreshHand(0);
                    refreshHand(1);
                    break;
                }
                case EngineMessage.MSG_CHAIN_NEGATED:
                case EngineMessage.MSG_CHAIN_DISABLED: {
                    cursor++;
                    broadcastMsg(range(msg, start, cursor));
                    break;
                }
                case EngineMessage.MSG_CARD_SELECTED: {
                    cursor++; // player
                    count = msg[cursor] & 0xFF;
                    cursor++;
                    cursor += count * 4;
                    break;
                }
                case EngineMessage.MSG_RANDOM_SELECTED: {
                    player = msg[cursor] & 0xFF;
                    cursor++;
                    count = msg[cursor] & 0xFF;
                    cursor++;
                    cursor += count * 4;
                    byte[] slice = range(msg, start, cursor);
                    if (soloMode) {
                        // Single connection: send once
                        sendToPlayer(room.players[player], slice);
                    } else {
                        sendToPlayer(room.players[player], slice);
                        sendToPlayer(room.players[1], slice);
                    }
                    sendToObservers(slice);
                    break;
                }
                case EngineMessage.MSG_BECOME_TARGET: {
                    count = msg[cursor] & 0xFF;
                    cursor++;
                    cursor += count * 4;
                    broadcastMsg(range(msg, start, cursor));
                    break;
                }
                case EngineMessage.MSG_DRAW: {
                    player = msg[cursor] & 0xFF;
                    cursor++;
                    count = msg[cursor] & 0xFF;
                    cursor++;
                    int codeBase = cursor;
                    cursor += count * 4;
                    byte[] full = range(msg, start, cursor);
                    int p = codeBase;
                    for (int i = 0; i < count; i++) {
                        if ((msg[p + 3] & 0x80) == 0) {
                            writeInt32(msg, p, 0);
                        }
                        p += 4;
                    }
                    byte[] masked = range(msg, start, cursor);
                    sendDualViewAndRecord(player, full, masked);
                    break;
                }
                case EngineMessage.MSG_DAMAGE:
                case EngineMessage.MSG_RECOVER:
                case EngineMessage.MSG_PAY_LPCOST:
                case EngineMessage.MSG_LPUPDATE: {
                    cursor += 5;
                    broadcastMsg(range(msg, start, cursor));
                    break;
                }
                case EngineMessage.MSG_EQUIP:
                case EngineMessage.MSG_CARD_TARGET:
                case EngineMessage.MSG_CANCEL_TARGET:
                case EngineMessage.MSG_ATTACK: {
                    cursor += 8;
                    broadcastMsg(range(msg, start, cursor));
                    break;
                }
                case EngineMessage.MSG_UNEQUIP: {
                    cursor += 4;
                    broadcastMsg(range(msg, start, cursor));
                    break;
                }
                case EngineMessage.MSG_ADD_COUNTER:
                case EngineMessage.MSG_REMOVE_COUNTER: {
                    cursor += 7;
                    broadcastMsg(range(msg, start, cursor));
                    break;
                }
                case EngineMessage.MSG_BATTLE: {
                    cursor += 26;
                    broadcastMsg(range(msg, start, cursor));
                    break;
                }
                case EngineMessage.MSG_ATTACK_DISABLED: {
                    broadcastMsg(range(msg, start, cursor));
                    break;
                }
                case EngineMessage.MSG_DAMAGE_STEP_START:
                case EngineMessage.MSG_DAMAGE_STEP_END: {
                    broadcastMsg(range(msg, start, cursor));
                    refreshMzone(0);
                    refreshMzone(1);
                    break;
                }
                case EngineMessage.MSG_MISSED_EFFECT: {
                    player = msg[cursor] & 0xFF;
                    cursor += 8;
                    sendToPlayer(room.players[player], range(msg, start, cursor));
                    break;
                }
                case EngineMessage.MSG_TOSS_COIN:
                case EngineMessage.MSG_TOSS_DICE: {
                    cursor++; // player
                    count = msg[cursor] & 0xFF;
                    cursor++;
                    cursor += count;
                    broadcastMsg(range(msg, start, cursor));
                    break;
                }
                case EngineMessage.MSG_ROCK_PAPER_SCISSORS: {
                    player = msg[cursor] & 0xFF;
                    cursor++;
                    waitforResponse(player);
                    sendToPlayer(room.players[player], range(msg, start, cursor));
                    return 1;
                }
                case EngineMessage.MSG_HAND_RES: {
                    cursor++;
                    broadcastMsg(range(msg, start, cursor));
                    break;
                }
                case EngineMessage.MSG_ANNOUNCE_RACE:
                case EngineMessage.MSG_ANNOUNCE_ATTRIB: {
                    player = msg[cursor] & 0xFF;
                    cursor++;
                    cursor += 5;
                    waitforResponse(player);
                    sendToPlayer(room.players[player], range(msg, start, cursor));
                    return 1;
                }
                case EngineMessage.MSG_ANNOUNCE_CARD:
                case EngineMessage.MSG_ANNOUNCE_NUMBER: {
                    player = msg[cursor] & 0xFF;
                    cursor++;
                    count = msg[cursor] & 0xFF;
                    cursor++;
                    cursor += count * 4;
                    waitforResponse(player);
                    sendToPlayer(room.players[player], range(msg, start, cursor));
                    return 1;
                }
                case EngineMessage.MSG_CARD_HINT: {
                    cursor += 9;
                    broadcastMsg(range(msg, start, cursor));
                    break;
                }
                case EngineMessage.MSG_PLAYER_HINT: {
                    cursor += 6;
                    broadcastMsg(range(msg, start, cursor));
                    break;
                }
                case EngineMessage.MSG_TAG_SWAP: {
                    // 头 5×u8 = player, mcount, ecount, pcount, hcount；
                    // 载荷 = top card(4B) + hand(hcount×4B) + extra(ecount×4B)（对齐 field::tag_swap 写入序）。
                    int swapPlayer = msg[cursor] & 0xFF;
                    int tsEcount = msg[cursor + 2] & 0xFF;
                    int tsHcount = msg[cursor + 4] & 0xFF;
                    cursor += 5 + 4 + tsHcount * 4 + tsEcount * 4;
                    // solo：4 席位同一连接由房主一人操控，全量下发即无信息泄漏；
                    // sendToPlayer 在 dp==players[0] 时录帧，players[1]==players[0] 故只发一条全量帧。
                    sendToPlayer(room.players[swapPlayer & 1], range(msg, start, cursor));
                    refreshExtra(swapPlayer & 1);
                    refreshMzone(0, 0x81fff, 0);
                    refreshMzone(1, 0x81fff, 0);
                    refreshSzone(0, 0x681fff, 0);
                    refreshSzone(1, 0x681fff, 0);
                    refreshHand(0, 0x781fff, 0);
                    refreshHand(1, 0x781fff, 0);
                    break;
                }
                case EngineMessage.MSG_MATCH_KILL: {
                    int code = readInt32(msg, cursor);
                    cursor += 4;
                    if (room.matchMode) {
                        room.matchKill = code;
                        broadcastMsg(range(msg, start, cursor));
                    }
                    break;
                }
                default:
                    // 未知消息：engType 已消费 1 字节，游标已前进，不会死循环
                    break;
            }
        }
        return 0;
    }

    // === 等待应答（实现见 DuelViewBroadcaster） ===
    void waitforResponse(int playerid) { broadcaster.waitforResponse(playerid); }

    // === 区域刷新（实现见 DuelZoneRefresh） ===
    void refreshMzone(int player) { zoneRefresh.refreshMzone(player); }
    void refreshMzone(int player, int flag, int useCache) { zoneRefresh.refreshMzone(player, flag, useCache); }
    void refreshSzone(int player) { zoneRefresh.refreshSzone(player); }
    void refreshSzone(int player, int flag, int useCache) { zoneRefresh.refreshSzone(player, flag, useCache); }
    void refreshHand(int player) { zoneRefresh.refreshHand(player); }
    void refreshHand(int player, int flag, int useCache) { zoneRefresh.refreshHand(player, flag, useCache); }
    void refreshGrave(int player) { zoneRefresh.refreshGrave(player); }
    void refreshGrave(int player, int flag, int useCache) { zoneRefresh.refreshGrave(player, flag, useCache); }
    void refreshExtra(int player) { zoneRefresh.refreshExtra(player); }
    void refreshExtra(int player, int flag, int useCache) { zoneRefresh.refreshExtra(player, flag, useCache); }
    void refreshSingle(int player, int location, int sequence) { zoneRefresh.refreshSingle(player, location, sequence); }
    void refreshSingle(int player, int location, int sequence, int flag) { zoneRefresh.refreshSingle(player, location, sequence, flag); }

    // === 发送 / 录制（实现见 DuelViewBroadcaster） ===
    private void recordFrame(byte[] full) { broadcaster.recordFrame(full); }
    private void sendDualViewAndRecord(int ownerPlayer, byte[] full, byte[] masked) { broadcaster.sendDualViewAndRecord(ownerPlayer, full, masked); }
    void sendToPlayer(ServerConnection dp, byte[] data) { broadcaster.sendToPlayer(dp, data); }
    void sendToObservers(byte[] data) { broadcaster.sendToObservers(data); }
    void broadcastMsg(byte[] data) { broadcaster.broadcastMsg(data); }
    private void emit(ServerConnection dp, byte[] data) { broadcaster.emit(dp, data); }
    private void emitObservers(byte[] data) { broadcaster.emitObservers(data); }

    // === 撤回后的全量重同步（实现见 UndoQuestionGuard） ===
    void resyncAfterUndo(byte[] reloadField) { undoGuard.resyncAfterUndo(reloadField); }
    void hangUndoTerminalQuestion() { undoGuard.hangUndoTerminalQuestion(); }
    boolean hasLiveAsk() { return undoGuard.hasLiveAsk(); }
    boolean rehangPendingQuestion(String reason) { return undoGuard.rehangPendingQuestion(reason); }
    boolean canRedeliverQuestion(int maxRedeliveries) { return undoGuard.canRedeliverQuestion(maxRedeliveries); }
    void beatPendingQuestion(long nowMs, long graceMs, int maxRedeliveries) { undoGuard.beatPendingQuestion(nowMs, graceMs, maxRedeliveries); }
    /** DP/SP 发动询问是否为可整段撤回的动作锚点（实现见 UndoQuestionGuard）。 */
    boolean isPhaseActivationAnchor(int responderPlayer, int questionType) {
        return undoGuard.isPhaseActivationAnchor(responderPlayer, questionType);
    }
    private static byte[] defaultPassAnswer(int qtype) { return UndoQuestionGuard.defaultPassAnswer(qtype); }

    private static byte[] range(byte[] msg, int from, int to) {
        return Arrays.copyOfRange(msg, from, Math.min(to, msg.length));
    }

    /** 遍历 count 个选卡项，逐个把非应答方可见的卡码清零（SELECT_CARD/UNSELECT/SUM 共用），返回新游标。 */
    private static int maskCodesForOpponent(byte[] msg, int cursor, int count, int player) {
        for (int i = 0; i < count; i++) {
            int codeOff = cursor;
            cursor += 4;
            int c = msg[cursor] & 0xFF;
            cursor++;
            cursor += 3;
            if (c != player) {
                writeInt32(msg, codeOff, 0);
            }
        }
        return cursor;
    }

    static int readInt32(byte[] b, int o) {
        return (b[o] & 0xFF) | ((b[o + 1] & 0xFF) << 8) | ((b[o + 2] & 0xFF) << 16) | ((b[o + 3] & 0xFF) << 24);
    }

    static void writeInt32(byte[] b, int o, int v) {
        b[o] = (byte) (v & 0xFF);
        b[o + 1] = (byte) ((v >> 8) & 0xFF);
        b[o + 2] = (byte) ((v >> 16) & 0xFF);
        b[o + 3] = (byte) ((v >> 24) & 0xFF);
    }

    static void zeroRange(byte[] b, int off, int count) {
        if (count > 0) {
            Arrays.fill(b, off, off + count, (byte) 0);
        }
    }
}
