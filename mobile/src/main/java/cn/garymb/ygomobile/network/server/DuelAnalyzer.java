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
 * 引擎消息解析器，移植 {@code Classes/gframe/single_duel.cpp} 的
 * {@code Analyze}/{@code Refresh*}/{@code WriteUpdateData}/{@code WaitforResponse}：
 * 逐条消费 ocgcore 消息流，按 is_host 语义过滤/遮蔽后广播 {@code STOC_GAME_MSG}，
 * 并在需要玩家应答时挂起等待。
 *
 * <p>不持有决斗状态：{@code pduel}/{@code room}/{@code replay}/{@code lastReplayResponseSize}
 * 均读写自宿主 {@link ServerDuel}（同包 package-private 访问）。所有方法在
 * {@link LanGameServer} 的单线程房间执行器上串行调用。
 *
 * <p>遮蔽规则严格对齐 C++：位置整型在查询块 {@code block+12}（字段卡 {@code field}）或
 * 单卡数据 offset 12（{@code RefreshSingle}）；暗盖卡发对手前置零，{@code POS_REVEAL} 位对双方均剥除。
 */
final class DuelAnalyzer implements YGOProtocol {

    private static final String TAG = "DuelAnalyzer";

    private static final int REFRESH_MZONE_FLAG = 0x881fff;
    private static final int REFRESH_SZONE_FLAG = 0x681fff;
    private static final int REFRESH_HAND_FLAG = 0x681fff;
    private static final int REFRESH_GRAVE_FLAG = 0x81fff;
    private static final int REFRESH_EXTRA_FLAG = 0xe81fff;
    private static final int REFRESH_SINGLE_FLAG = 0xf81fff;
    private static final int SHUFFLE_HAND_FLAG = 0x781fff;
    private static final int SHUFFLE_SET_FLAG = 0x181fff;

    /**
     * 撤回全量重同步专用的 Mzone 掩码：常规 {@link #REFRESH_MZONE_FLAG} 之上追加
     * {@code QUERY_OVERLAY_CARD}，把超量素材的卡码随刷新补发给客户端。
     *
     * <p>MSG_RELOAD_FIELD（{@code query_field_info}，ocgapi.cpp L310-353）对怪兽区只写
     * 「姿态 + 素材数量」，既不写素材卡码也不写素材姿态；桌面版实时对局无需该位，因为客户端
     * 早已从逐张素材的 MSG_MOVE 认得它们。而撤回走的是全量重载，客户端只能新建 code=0 的素材
     * 占位卡，缺这次补发就会把叠放的素材一律渲染成卡背，玩家无从确认叠的是什么卡。
     * 素材本就是双方可见的公开信息（对手侧也须收到），故对 full/masked 两份视图同时生效。
     */
    private static final int RESYNC_MZONE_FLAG = REFRESH_MZONE_FLAG | OcgDuelEngine.QUERY_OVERLAY_CARD;

    private static final int QUERY_CODE_POSITION = OcgDuelEngine.QUERY_CODE | OcgDuelEngine.QUERY_POSITION;
    private static final int MSG_HEADER_LEN = 3; // MSG_UPDATE_DATA + player + location
    private static final int CARD_HEADER_LEN = 4; // MSG_UPDATE_CARD + player + location + sequence

    private final ServerDuel owner;
    private final GameRoom room;
    private final boolean soloMode;

    // ==================================================================
    // 撤回（undo）支持：静默重放与重同步
    // ==================================================================

    /**
     * 静默重放态：{@link ServerDuel} 为撤回而上重建引擎并逐条重放历史应答时置真——
     * 所有 STOC_GAME_MSG / STOC_TIME_LIMIT 出站被压制（客户端只在重同步阶段看到回退后的
     * 局面，不重播被撤销的动画），但录制与遮蔽语义完全不变，使重放产物与原录像逐帧一致。
     */
    boolean silent;
    /**
     * 只发不录态：撤回后的全量重同步（MSG_RELOAD_FIELD / 各区域 refresh / 重新挂回的询问）
     * 属于传输层补偿，不是对局进展，不能再写进录像。
     */
    boolean recordSuppressed;
    /** 静默重放期间捕获的终端询问消息原字节（撤回后原样重发给待应答方）。 */
    byte[] silentTerminal;
    /** waitforResponse 已置位、等待下一条 sendToPlayer 作为终端询问的标记。 */
    private boolean silentWaitPending;
    /**
     * 最近一次真正下发给某个席位的询问报文（MSG_SELECT_*，首字节 10..26）。
     * 引擎回 RETRY 时靠它把询问原样重发给服务端认定的应答方，不再依赖客户端自己缓存的
     * 「上一条消息」——撤回会清掉客户端那份缓存（被撤销的询问绝不能再被应答），且客户端的
     * 重放有次数上限，一旦落空界面就再也等不到询问，双方永久互等（表现为连锁处理不下去、
     * 决斗卡死）。询问的权威来源因此回到服务端。
     */
    private byte[] liveQuestion;
    /**
     * {@link #liveQuestion} 挂给的协议席位（-1 = 无）。由 {@link #waitforResponse(int)} 随每次挂起
     * 询问一并记下，因此它始终与「引擎此刻在等谁」同步。
     */
    int liveQuestionResponder = -1;
    /** 本次询问挂出的时刻（毫秒），询问保活据此判断「挂了但迟迟没有答复」。 */
    private long liveQuestionAtMs;
    /** 本询问已被原样重发的次数（上限由调用方给定，防止与客户端的坏应答形成乒乓）。 */
    private int liveQuestionRedeliveries;
    /**
     * 本询问是否来自撤回的延后重挂。保活心跳只复查这一路询问：撤回是本题唯一会把「已挂出的询问」
     * 凭空抹掉再重挂的操作，风险集中于此；而正常对局里玩家长时间思考（选卡、连锁取舍）是合法行为，
     * 绝不能因为超时就把询问重发一次、把已经打开的选择窗再弹一遍。
     */
    private boolean liveQuestionFromUndo;
    /**
     * 询问代次：每挂起一次等待自增一次。与 {@link #liveQuestionGeneration} 配对即可判定
     * {@link #liveQuestion} 究竟是不是「引擎此刻在等的那一条」——这一步不能省：
     * {@code MSG_ANNOUNCE_RACE/ATTRIB/CARD/NUMBER} 与 {@code MSG_ROCK_PAPER_SCISSORS} 同样要
     * 玩家应答（也走 waitforResponse），但它们的消息号不在 10..26 之内、不会被记成询问，此时
     * liveQuestion 里剩的还是上一条询问的字节；若无代次校验就重发，等于把一道早已不存在的旧题
     * 挂到正在等别的答复的席位上，客户端会弹出对不上号的窗并给出非法应答。
     */
    private int askGeneration;
    /** {@link #liveQuestion} 所属的等待代次（-1 = 无）。 */
    private int liveQuestionGeneration = -1;
    /**
     * 同一条询问被连续判非法的次数（只在 analyze 的 MSG_RETRY 分支累加，一条新询问挂出时归零）。
     * 它用来识别「对端已经答不出这道题」的死局：一道题被逐字节相同地重问、答复又反复非法，
     * 说明那一头的场面认知与本局脱节，再重问下去也永远等不到合法答复，决斗就此永久停住。
     */
    private int retryStreak;
    /** {@link #retryStreak} 对应的那一条询问字节（用于判断「还是同一道题」）。 */
    private byte[] lastRetryAsk;
    /** 本次 {@link #sendToPlayer} 是否为 RETRY / 自愈之后的原样重发（重发不该把重试计数清零）。 */
    private boolean resendingAfterRetry;
    /** 已施过熔断重答的询问代次：同一条询问至多代替它答一次，绝不反复代答。 */
    private int deadlockBreakGeneration = -1;
    /** 连续非法应答达到这个次数即启动熔断重答（正常的客户端不会连续三次答错同一道题）。 */
    private static final int RETRY_STREAK_LIMIT = 3;
    /** 撤回重同步待重挂询问的席位（-1 = 无待重挂）；见 {@link #hangUndoTerminalQuestion()}。 */
    int undoAskResponder = -1;
    /** 静默重放期间统计的回合数（客户端 field.turnCount 需与之对齐，MSG_RELOAD_FIELD 不带回合号）。 */
    int silentTurnCount;
    /** 最后一条 MSG_NEW_TURN 的协议侧玩家索引 / 最后一条 MSG_NEW_PHASE 的阶段值。 */
    int silentTurnPlayer;
    int silentPhase;

    /**
     * 当前挂起的询问是否为「行动宣言」询问（{@code MSG_SELECT_IDLECMD} / {@code MSG_SELECT_BATTLECMD}，
     * 其余值为 0）：对它的应答就是玩家主动做出的一次动作（召唤 / 反转召唤 / 特殊召唤 / 盖卡 /
     * 发动效果 / 攻击宣言 / 切换阶段），撤回必须以这种应答为锚点整段回退——一个动作之后的
     * 连锁、选格、表示形式等询问都是该动作的处理过程，不单独构成可撤回单位。
     * 由 {@link ServerDuel#getResponse} 消费后清零；MSG_RETRY 重问同一询问时由被弹出的快照回填。
     */
    int pendingActionQuestion;

    DuelAnalyzer(ServerDuel owner) {
        this.owner = owner;
        this.room = owner.room;
        this.soloMode = room.soloMode;
    }

    /** 进入静默重放：清零捕获槽，后续 analyze 只录不发。 */
    void beginSilentReplay() {
        silentTerminal = null;
        silentWaitPending = false;
        undoAskResponder = -1;
        // 引擎即将重建：旧的 liveQuestion 属于要被抹掉的那一段，保活绝不能再把它重发出去
        liveQuestion = null;
        liveQuestionGeneration = -1;
        liveQuestionFromUndo = false;
        liveQuestionResponder = -1;
        silentTurnCount = 0;
        silentTurnPlayer = 0;
        silentPhase = 0;
        silent = true;
    }

    /** 退出静默重放。 */
    void endSilentReplay() {
        silent = false;
        silentWaitPending = false;
    }

    private long pduel() {
        return owner.pduel;
    }

    // ==================================================================
    // 引擎消息解析主循环
    // ==================================================================

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
                    // 询问权威源在服务端：直接把当前询问重发给应答方，使界面必定重新挂起可应答项；
                    // 仅在没有留存询问时才退回 C++ 语义（1 字节 MSG_RETRY，靠客户端重放自身缓存）。
                    // 须在 waitforResponse 之前取：它会推进代次，取晚了这个配对判断就恒为否
                    byte[] ask = hasLiveAsk() ? liveQuestion : null;
                    int rseat = room.lastResponse;
                    retryStreak = (ask != null && Arrays.equals(ask, lastRetryAsk)) ? retryStreak + 1 : 1;
                    lastRetryAsk = ask;
                    Log.w(TAG, "MSG_RETRY: invalid answer from seat " + rseat
                            + " for question type=" + (ask == null ? -1 : (ask[0] & 0xFF))
                            + " streak=" + retryStreak);
                    // 熔断：同一道题连续 RETRY_STREAK_LIMIT 次给出非法答复 —— 只代非本方的对手席位
                    // （人机局里就是 AI、联机局里就是对方客户端；本方席位由询问求援通道自行取回），
                    // 且只代答「放弃也必定合法」的那几类询问。代答之后引擎在同一轮 process 循环里
                    // 继续推进，不再往这个席位重问第四遍
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
                    count = msg[cursor] & 0xFF;
                    cursor++;
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
                    if (silent) {
                        silentPhase = (msg[start + 1] & 0xFF) | ((msg[start + 2] & 0xFF) << 8);
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
                    // 效果把手牌外的卡加入手卡（非抽卡入手）必须向对手公开卡码：
                    // PSCT 与实卡规则要求“以非抽卡方式入手的卡需向对手展示”，客户端据此
                    // 才能把对方的那张卡翻面确认（见 DeckHandMotionManager.applyMoveToHandReveal）。
                    // 正常抽卡由引擎发 MSG_DRAW、不经此分支，故不会泄露抽卡信息；
                    // pl 已含手卡时不公开（同手卡内的位置/表示形式变动不算“入手”）。
                    // 公开仅限这一瞬间：随后的 MSG_SHUFFLE_HAND / refreshHand 会把里侧手卡卡码重新清零。
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
                    // 对齐 single_duel.cpp L994-996：C++ BufferIO::Write 会自行推进 pbuf 4 字节（清零 code），
                    // 再 pbuf += 4 跳过 ctrl/loc/seq/position，共消费 engType+8 字节、发包 9 字节。
                    // Java writeInt32 不移游标，此处必须 cursor += 8；旧值 +=4 导致游标错位、
                    // 后续消息被误解析（等待消息被跳过 → process() 空转占死 roomExecutor → 卡死）。
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

    // ==================================================================
    // 等待应答
    // ==================================================================

    private void waitforResponse(int playerid) {
        room.lastResponse = playerid;
        if (silent) {
            // 静默重放：仅记录“引擎此刻在等谁的哪条询问”（应答方已由上面 room.lastResponse 记下），
            // 不改连接状态、不发 MSG_WAITING/限时包
            silentWaitPending = true;
            return;
        }
        // 新的询问正挂出去：待重挂标记与保活计数随之换一个宿主（保活只管这一条询问）
        askGeneration++;
        liveQuestionResponder = playerid;
        liveQuestionAtMs = System.currentTimeMillis();
        liveQuestionRedeliveries = 0;
        liveQuestionFromUndo = false;
        if (soloMode && room.players[0] == room.players[1]) {
            // Solo mode: same connection; no MSG_WAITING needed, just set state
            room.players[playerid].state = CTOS_RESPONSE;
            return;
        }
        byte[] waiting = new byte[]{(byte) EngineMessage.MSG_WAITING};
        if (room.players[1 - playerid] != null) {
            room.players[1 - playerid].send(STOC_GAME_MSG, waiting);
        }
        if (room.hostInfo.timeLimit != 0) {
            byte[] sctl = timeLimitPayload(playerid, room.timeLimit[playerid]);
            if (room.players[0] != null) {
                room.players[0].send(STOC_TIME_LIMIT, sctl);
            }
            if (room.players[1] != null) {
                room.players[1].send(STOC_TIME_LIMIT, sctl);
            }
            room.players[playerid].state = CTOS_TIME_CONFIRM;
        } else {
            room.players[playerid].state = CTOS_RESPONSE;
        }
    }

    private static byte[] timeLimitPayload(int player, int left) {
        ByteBuffer b = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        b.put((byte) player);
        b.put((byte) 0);
        b.putShort((short) left);
        return b.array();
    }

    // ==================================================================
    // 区域刷新（Refresh*）
    // ==================================================================

    void refreshMzone(int player) {
        refreshMzone(player, REFRESH_MZONE_FLAG, 1);
    }

    void refreshMzone(int player, int flag, int useCache) {
        refreshMaskedZone(player, OcgDuelEngine.LOCATION_MZONE, flag, useCache);
    }

    void refreshSzone(int player) {
        refreshSzone(player, REFRESH_SZONE_FLAG, 1);
    }

    void refreshSzone(int player, int flag, int useCache) {
        refreshMaskedZone(player, OcgDuelEngine.LOCATION_SZONE, flag, useCache);
    }

    /** Mzone/Szone 通用：遍历收集暗置段→先发本人→清零暗置段→发对手+观战。 */
    private void refreshMaskedZone(int player, int location, int flag, int useCache) {
        byte[] blocks = OcgDuelEngine.queryFieldCard(pduel(), player, location, flag, useCache);
        int len = blocks.length;
        List<int[]> hidden = new ArrayList<>();
        int qpos = 0;
        int qlen = 0;
        while (qlen < len && qpos + 4 <= len) {
            int clen = readInt32(blocks, qpos);
            qpos += 4;
            qlen += clen;
            if (clen <= EngineMessage.LEN_HEADER) {
                continue;
            }
            int position = EngineMessage.getPosition(blocks, qpos + 8);
            boolean hide = EngineMessage.shouldHideFacedownCode(position);
            EngineMessage.stripRevealFlag(blocks, qpos + 8);
            if (hide) {
                hidden.add(new int[]{qpos, clen});
            }
            qpos += clen - 4;
        }
        byte[] full = updateDataPayload(player, location, blocks, len);
        for (int[] seg : hidden) {
            zeroRange(blocks, seg[0], seg[1] - 4);
        }
        byte[] masked = updateDataPayload(player, location, blocks, len);
        sendDualViewAndRecord(player, full, masked);
    }

    void refreshHand(int player) {
        refreshHand(player, REFRESH_HAND_FLAG, 1);
    }

    void refreshHand(int player, int flag, int useCache) {
        byte[] blocks = OcgDuelEngine.queryFieldCard(pduel(), player, OcgDuelEngine.LOCATION_HAND, flag, useCache);
        int len = blocks.length;
        byte[] full = updateDataPayload(player, OcgDuelEngine.LOCATION_HAND, blocks, len);
        int qpos = 0;
        int qlen = 0;
        while (qlen < len && qpos + 4 <= len) {
            int slen = readInt32(blocks, qpos);
            qpos += 4;
            qlen += slen;
            if (slen <= EngineMessage.LEN_HEADER) {
                continue;
            }
            int position = EngineMessage.getPosition(blocks, qpos + 8);
            if ((position & EngineMessage.POS_FACEUP) == 0) {
                zeroRange(blocks, qpos, slen - 4);
            }
            qpos += slen - 4;
        }
        byte[] masked = updateDataPayload(player, OcgDuelEngine.LOCATION_HAND, blocks, len);
        sendDualViewAndRecord(player, full, masked);
    }

    void refreshGrave(int player) {
        refreshGrave(player, REFRESH_GRAVE_FLAG, 1);
    }

    void refreshGrave(int player, int flag, int useCache) {
        byte[] blocks = OcgDuelEngine.queryFieldCard(pduel(), player, OcgDuelEngine.LOCATION_GRAVE, flag, useCache);
        broadcastMsg(updateDataPayload(player, OcgDuelEngine.LOCATION_GRAVE, blocks, blocks.length));
    }

    void refreshExtra(int player) {
        refreshExtra(player, REFRESH_EXTRA_FLAG, 1);
    }

    void refreshExtra(int player, int flag, int useCache) {
        byte[] blocks = OcgDuelEngine.queryFieldCard(pduel(), player, OcgDuelEngine.LOCATION_EXTRA, flag, useCache);
        sendToPlayer(room.players[player],
                updateDataPayload(player, OcgDuelEngine.LOCATION_EXTRA, blocks, blocks.length));
    }

    void refreshSingle(int player, int location, int sequence) {
        refreshSingle(player, location, sequence, REFRESH_SINGLE_FLAG);
    }

    void refreshSingle(int player, int location, int sequence, int flag) {
        byte[] blocks = OcgDuelEngine.queryCard(pduel(), player, location, sequence, flag, 0);
        int len = blocks.length;
        if (len <= EngineMessage.LEN_HEADER) {
            sendToPlayer(room.players[player], updateCardPayload(player, location, sequence, blocks, len));
            return;
        }
        int position = EngineMessage.getPosition(blocks, 12);
        // 手卡位置在本端口是里侧（POS_FACEDOWN），但“效果入手需向对手展示”已经由 MSG_MOVE
        // 分支公开了卡码；紧随其后的这次 refreshSingle 若仍按里侧清零，会把刚解除的卡码
        // 再次盖掉，对手侧永远拿不到入手卡的卡面 → 翻面确认无从渲染。故手卡不按里侧遮蔽。
        // （C++ single_duel.cpp RefreshSingle L1586-1616 对非场上位置一律 hide，此处为有意增强）
        boolean hide = (position & EngineMessage.POS_FACEDOWN) != 0
                && (location & EngineMessage.LOCATION_HAND) == 0;
        if ((location & EngineMessage.LOCATION_ONFIELD) != 0) {
            hide = EngineMessage.shouldHideFacedownCode(position);
            EngineMessage.stripRevealFlag(blocks, 12);
        }
        byte[] full = updateCardPayload(player, location, sequence, blocks, len);
        int sendLen = len;
        if (hide) {
            writeInt32(blocks, 0, 16);
            writeInt32(blocks, 4, QUERY_CODE_POSITION);
            writeInt32(blocks, 8, 0); // 清零 code，保留 12..15 姿态
            sendLen = 16;
        }
        byte[] masked = updateCardPayload(player, location, sequence, blocks, sendLen);
        sendDualViewAndRecord(player, full, masked);
    }

    // ==================================================================
    // 发送 / 字节工具
    // ==================================================================

    /**
     * 录制一帧权威（未遮蔽）引擎消息：联机录像保存双方完整卡码，使纯消息流回放
     * 能正面显示对方手牌/暗盖/除外卡；仅由需要替代 players[0] 遮蔽视角的分支调用。
     */
    private void recordFrame(byte[] full) {
        if (recordSuppressed) {
            return;
        }
        if (owner.replay != null) {
            owner.replay.writeMessage(full, full.length);
        }
    }

    /**
     * 双视角发送并录制完整帧：owner 收 full、对手与观战收 masked；录像仅存 full 一条。
     * 取代这些分支原先的 sendToPlayer(owner, full) + sendToPlayer(opp, masked) +
     * sendToObservers(masked)，使录像不再泄漏 players[0] 遮蔽视图；实时发送语义与原逻辑一致。
     */
    private void sendDualViewAndRecord(int ownerPlayer, byte[] full, byte[] masked) {
        recordFrame(full);
        ServerConnection op = room.players[ownerPlayer];
        ServerConnection opp = room.players[1 - ownerPlayer];
        if (soloMode && op == opp) {
            // Solo mode: same connection is both players; send full only (masked would overwrite)
            emit(op, full);
            emitObservers(masked);
            return;
        }
        emit(op, full);
        emit(opp, masked);
        emitObservers(masked);
    }

    void sendToPlayer(ServerConnection dp, byte[] data) {
        if (dp == null) {
            return;
        }
        // 录像：广播类与发给 players[0] 的提示/选择消息在此按主机视角录制（本就含完整
        // 公开信息）；含隐藏卡码的遮蔽类分支不经此路径，改由 sendDualViewAndRecord 录全量帧
        if (!recordSuppressed && dp == room.players[0] && owner.replay != null) {
            owner.replay.writeMessage(data, data.length);
        }
        if (silent) {
            // 紧接 waitforResponse 的那条终端询问被留存，供撤回后原样重发
            if (silentWaitPending) {
                silentTerminal = data;
                silentWaitPending = false;
            }
            return;
        }
        // 记下这条询问（首字节即引擎消息号，MSG_SELECT_* 编号连续取 10..26 区间）
        if (data != null && data.length > 0) {
            int qtype = data[0] & 0xFF;
            if (qtype >= EngineMessage.MSG_SELECT_BATTLECMD && qtype <= EngineMessage.MSG_SELECT_UNSELECT_CARD) {
                liveQuestion = data;
                liveQuestionGeneration = askGeneration;
                if (!resendingAfterRetry) {
                    // 挂出的是一条新询问：上一条询问的连续非法计数与熔断记录一并作废
                    retryStreak = 0;
                    lastRetryAsk = null;
                    deadlockBreakGeneration = -1;
                }
            }
            resendingAfterRetry = false;
        }
        dp.send(STOC_GAME_MSG, data);
    }

    void sendToObservers(byte[] data) {
        emitObservers(data);
    }

    void broadcastMsg(byte[] data) {
        sendToPlayer(room.players[0], data);
        // Solo mode: players[1] == players[0] is the same connection. Sending to both
        // slots would deliver animation-bearing messages (new phase / summoning / turn)
        // twice to the host, so SpecEffectOverlay queues and plays each effect twice.
        // Dedupe by connection identity so a solo host receives exactly one copy.
        if (room.players[1] != room.players[0]) {
            sendToPlayer(room.players[1], data);
        }
        emitObservers(data);
    }

    /** 统一出站口：静默重放（撤回）期只录不发。 */
    private void emit(ServerConnection dp, byte[] data) {
        if (dp != null && !silent) {
            dp.send(STOC_GAME_MSG, data);
        }
    }

    /** 观战统一出站口：静默重放期只录不发。 */
    private void emitObservers(byte[] data) {
        if (silent) {
            return;
        }
        for (ServerConnection o : room.observers) {
            o.send(STOC_GAME_MSG, data);
        }
    }

    // ==================================================================
    // 撤回后的全量重同步
    // ==================================================================

    /**
     * 撤回成功后把回退后的局面一次性推给全体（对齐“全量重载场地位”语义）：
     * ① MSG_RELOAD_FIELD（query_field_info 产物，不含卡码，无遮蔽问题，广播双方与观战）；
     * ② Mzone/Szone/Hand/Grave/Extra 各自 refresh，沿用既有遮蔽语义（对手手牌/暗盖不给卡码，
     *    Extra 仅本人为）；其中 Mzone 用 {@link #RESYNC_MZONE_FLAG} 额外携素材卡码，否则重载后
     *    超量素材会停在无卡码的占位状态而渲染成卡背；③ 把两席位状态一律复位并把待重挂询问的席位
     *    记下（{@link #undoAskResponder}），询问由 {@link #hangUndoTerminalQuestion()} 在一段延时之后
     *    单独挂回。全程 recordSuppressed：重同步消息不属于对局进展，不写录像。
     *
     * <p>本阶段的所有查询一律 {@code use_cache = 0}：ocgcore {@code card::get_infos}（card.cpp
     * L236-383）在 use_cache=1 时会把与 q_cache 相同的 ALIAS/TYPE/LEVEL/RANK/ATTACK/DEFENSE/STATUS
     * 等字段**清位省略**，而静默重放早已用同一批掩码查过全场、缓存值与回退后的终态一致，于是这次
     * 刷新几乎只剩卡码与姿态。实时流程里这没问题（客户端的 ClientCard 是逐步累积出来的旧对象），但
     * 撤回走的是 MSG_RELOAD_FIELD 全量重载——客户端按占位新建**全零** ClientCard，被省略的字段无人
     * 补齐，等级/阶级文本与攻击力/守备力文本（{@code GameField.updateQuery} 里由 TYPE/LEVEL/RANK/
     * ATTACK/DEFENSE 现场拼出）便一律空白。故重同步必须发全量查询。
     *
     * <p>MSG_RELOAD_FIELD 不携回合号/阶段，回合与阶段由 {@link ServerDuel} 随 STOC_UNDO_ACK
     * 载荷（{@link #silentTurnCount}/{@link #silentTurnPlayer}/{@link #silentPhase}）先行告知客户端。
     */
    void resyncAfterUndo(byte[] reloadField) {
        recordSuppressed = true;
        try {
            broadcastMsg(reloadField);
            // 素材卡码只可能来自这次 Mzone 刷新（reload 载荷不含），故此处用带 QUERY_OVERLAY_CARD
            // 的掩码；实时流程的 refreshMzone 仍用原掩码，录像与逐帧序列不受影响。
            // use_cache 全部取 0（见类注释②）：只改本轮这次刷新的详尽程度，不触碰实时流程掩码与录像
            refreshMzone(0, RESYNC_MZONE_FLAG, 0);
            refreshMzone(1, RESYNC_MZONE_FLAG, 0);
            refreshSzone(0, REFRESH_SZONE_FLAG, 0);
            refreshSzone(1, REFRESH_SZONE_FLAG, 0);
            refreshHand(0, REFRESH_HAND_FLAG, 0);
            refreshHand(1, REFRESH_HAND_FLAG, 0);
            refreshGrave(0, REFRESH_GRAVE_FLAG, 0);
            refreshGrave(1, REFRESH_GRAVE_FLAG, 0);
            refreshExtra(0, REFRESH_EXTRA_FLAG, 0);
            refreshExtra(1, REFRESH_EXTRA_FLAG, 0);
            int responder = room.lastResponse;
            byte[] terminal = silentTerminal;
            if (terminal == null || room.players[responder] == null) {
                return;
            }
            // 状态门复位：撤回可能把待应答席位从 A 转移到 B（被丢弃的那段里 A 正被询问着，
            // 玩家是在被问的状态下按的撤回）。A 的连接状态残留在 CTOS_RESPONSE/CTOS_TIME_CONFIRM，
            // 其后的迟到应答——被撤销弹窗未取消的自动应答、按旧命令列表编出的点击——能通过
            // GameRoom.handlePacket 的状态门，被 getResponse 以 responder=dp.type 当作 B 的应答喂给
            // 引擎：轻则非法应答引发 MSG_RETRY 风暴（表现为通讯中断、无法继续），重则旧列表里的
            // 合法指令（切换阶段等）直接作用到新局面上（表现为凭空进入下个回合）。
            // C++ WaitforResponse（single_duel.cpp L1470-1483）无需处理这一情况，因为只有撤回会让
            // 「被询问而未应答」的席位凭空失去待答问题，故此复位是本工程撤回路径特有的补齐。
            for (ServerConnection pl : room.players) {
                if (pl != null) {
                    pl.state = ServerConnection.STATE_NONE;
                }
            }
            // 询问不在此刻挂回，只记下席位交 hangUndoTerminalQuestion() 延后执行：状态复位到询问
            // 挂出之间留出一个往返窗口，被丢弃那一段里已在路上的应答（人机局 AI 异步算完的连锁
            // 答复尤其）全部落进 STATE_NONE 而被状态门丢弃；若同步挂回，这条迟到的旧答复会立刻
            // 被当成对新询问的应答喂进引擎，连锁项与新局面南辕北辙，后面再也对不上
            undoAskResponder = responder;
        } finally {
            recordSuppressed = false;
        }
    }

    /**
     * 撤回重同步的第二步：把静默期捕获的终端询问真正挂回待应答席位
     * （{@link #resyncAfterUndo(byte[])} 记下 {@link #undoAskResponder} 后由 {@link ServerDuel} 延后调用）。
     */
    void hangUndoTerminalQuestion() {
        int responder = undoAskResponder;
        undoAskResponder = -1;
        if (responder < 0 || responder >= room.players.length) {
            return;
        }
        byte[] terminal = silentTerminal;
        ServerConnection dp = room.players[responder];
        if (terminal == null || dp == null) {
            return;
        }
        recordSuppressed = true;
        try {
            waitforResponse(responder);
            sendToPlayer(dp, terminal);
            // 重挂出去的询问的锚点属性：这里必须整值重写而不是只补 IDLE/BATTLE CMD——静默重放里
            // 的 IDLE/BATTLE 分支也会给它赋值（那一段里每一条行动询问都赋一次），重放到回退点后
            // 停下时手上留的是中途某条询问的残留值；若终端询问不是行动询问（例如选卡、连锁），
            // 残留值会把紧接着的那个普通应答错记成可回退锚点，下一次撤回就落错位置
            pendingActionQuestion =
                    terminal[0] == (byte) EngineMessage.MSG_SELECT_IDLECMD
                            ? EngineMessage.MSG_SELECT_IDLECMD
                            : terminal[0] == (byte) EngineMessage.MSG_SELECT_BATTLECMD
                            ? EngineMessage.MSG_SELECT_BATTLECMD : 0;
            // 本条询问来自撤回重挂：交给保活心跳复查（见 beatPendingQuestion），直到它被真正答掉
            liveQuestionFromUndo = true;
            Log.i(TAG, "undo: re-hang question type=" + (terminal[0] & 0xFF)
                    + " responder=" + responder);
        } finally {
            recordSuppressed = false;
        }
    }

    /**
     * {@link #liveQuestion} 是否就是引擎此刻正在等的这一条询问（代次与当前等待配对）。
     * 不配对的情形包括：上一条询问早已被答掉、以及本次等待属于 ANNOUNCE_*／猜拳这类
     * 不被记为询问的等待——此时手上的字节都是旧题，一律不得重发。
     */
    boolean hasLiveAsk() {
        return liveQuestion != null && liveQuestionGeneration == askGeneration;
    }

    /**
     * 「必定合法、且含义等于放弃」的应答，供 {@link #retryStreak} 熔断时代替僵住的席位作答：
     * <ul>
     *   <li>{@code MSG_SELECT_CHAIN}：int32 -1 = 不连锁（playerop.cpp L380 据此结束连锁）；</li>
     *   <li>{@code MSG_SELECT_EFFECTYN} / {@code MSG_SELECT_YESNO}：int32 0 = 否。</li>
     * </ul>
     * 其余询问的 0 号应答都是一个真实选择（菜单首项、第一张候选卡……）而非放弃，替玩家选它
     * 等于改判棋局，故返回 null 表示这题没有安全的放弃答案，熔断不介入、照旧重问。
     */
    private static byte[] defaultPassAnswer(int qtype) {
        if (qtype != EngineMessage.MSG_SELECT_CHAIN
                && qtype != EngineMessage.MSG_SELECT_EFFECTYN
                && qtype != EngineMessage.MSG_SELECT_YESNO) {
            return null;
        }
        byte[] resb = new byte[EngineMessage.SIZE_RETURN_VALUE];
        if (qtype == EngineMessage.MSG_SELECT_CHAIN) {
            Arrays.fill(resb, 0, 4, (byte) 0xff); // int32 -1
        }
        return resb;
    }

    /**
     * 引擎此刻是否仍在等 {@link #room}.lastResponse 那个席位答它手上那个询问。
     * 以连接状态为准（而非另设标志）：{@link ServerDuel#getResponse} 收到合法应答时会把它
     * 改回 STATE_NONE，因此“仍是 CTOS_RESPONSE / CTOS_TIME_CONFIRM”等价于“这个问题还没人答”。
     */
    private boolean isPendingQuestionUnanswered() {
        if (silent || !hasLiveAsk()) {
            return false;
        }
        int seat = room.lastResponse;
        if (seat < 0 || seat >= room.players.length) {
            return false;
        }
        ServerConnection dp = room.players[seat];
        if (dp == null) {
            return false;
        }
        return dp.state == CTOS_RESPONSE || dp.state == CTOS_TIME_CONFIRM;
    }

    /**
     * 把当前挂起的询问原样重发给引擎正在等待的席位（自愈，不写录像、不推进引擎）。
     *
     * <p>两种需要它的现场：
     * <ul>
     *   <li>撤回留白期内客户端把答复发早了——两席位都是 STATE_NONE，应答被状态门吞掉；引擎的问题
     *       并未答掉，而客户端以为自己已经答过、已经把弹窗收起，界面从此不再挂任何询问（点卡弹不出
     *       发动/召唤/盖放菜单，决斗推不下去）。这里连状态一并重新挂回，状态门随即重新放行该席位的答复。</li>
     *   <li>询问在链路上丢了（或服务端在发出询问的中途抛异常而没挂完）：席位状态本就是待应答，
     *       只需重发一次询问。</li>
     * </ul>
     *
     * @param reason 落盘原因，便于事后从日志区分两条自愈路径
     * @return true = 确实重发了一份询问
     */
    boolean rehangPendingQuestion(String reason) {
        if (silent || !hasLiveAsk()) {
            return false;
        }
        int seat = room.lastResponse;
        if (seat < 0 || seat >= room.players.length) {
            return false;
        }
        ServerConnection dp = room.players[seat];
        if (dp == null) {
            return false;
        }
        byte[] ask = liveQuestion;
        boolean fromUndo = liveQuestionFromUndo;
        int delivered = liveQuestionRedeliveries;
        recordSuppressed = true;
        try {
            if (dp.state != CTOS_RESPONSE && dp.state != CTOS_TIME_CONFIRM) {
                // 应答被状态门吞掉的形态：席位已是 STATE_NONE，不先把等待态挂回去，重发的询问得同样被吞
                waitforResponse(seat);
            }
            // 经统一出站口重发（而不是直接 dp.send）：它会把询问与当前代次重新配对，
            // 否则重发一次之后就再不算作「当前询问」，后续的自愈全部失效；标记为原样重发，
            // 免得把这条询问已有的连续非法计数清零
            resendingAfterRetry = true;
            sendToPlayer(dp, ask);
        } finally {
            recordSuppressed = false;
        }
        // 重发计数须扛过 waitforResponse 的归零（它只对新询问归零），否则被吞一次的应答
        // 会反复触发重发，与一直在发坏应答的客户端形成乒乓
        liveQuestionResponder = seat;
        liveQuestionRedeliveries = delivered + 1;
        liveQuestionAtMs = System.currentTimeMillis();
        liveQuestionFromUndo = fromUndo;
        Log.w(TAG, "question re-hang (" + reason + "): type=" + (ask[0] & 0xFF)
                + " seat=" + seat + " try=" + liveQuestionRedeliveries);
        return true;
    }

    /** 重发份数是否已用尽（全部自愈路径共用同一个上限，避免与客户端形成乒乓）。 */
    boolean canRedeliverQuestion(int maxRedeliveries) {
        return hasLiveAsk() && liveQuestionRedeliveries < maxRedeliveries;
    }

    /**
     * 询问保活（由 {@link ServerDuel} 的定时任务每秒复查）：只对撤回重挂的那一条询问生效——
     * 它超过 graceMs 仍未得到任何答复时原样重发一次，最多 maxRedeliveries 次。
     *
     * <p>为什么只管这一路：撤回是唯一会把已挂出的询问抹掉再重挂的操作，重挂任务本身又跑在
     * 另一个线程上（回投到房间执行器），它可能因执行器已关闭、代次作废、或客户端侧把询问包
     * 吞在动画闸门里而落空；一旦落空就是永久卡死。正常对局的询问不超时重发，因为玩家长考是合法行为。
     */
    void beatPendingQuestion(long nowMs, long graceMs, int maxRedeliveries) {
        if (!liveQuestionFromUndo || !canRedeliverQuestion(maxRedeliveries)) {
            return;
        }
        if (nowMs - liveQuestionAtMs < graceMs) {
            return;
        }
        if (!isPendingQuestionUnanswered()) {
            // 已被答掉（或局面已不再等待）：保活使命完成，后续询问不再属于撤回那一路
            liveQuestionFromUndo = false;
            return;
        }
        rehangPendingQuestion("undo keep-alive");
    }

    private static byte[] updateDataPayload(int player, int location, byte[] blocks, int len) {
        byte[] d = new byte[len + MSG_HEADER_LEN];
        d[0] = (byte) EngineMessage.MSG_UPDATE_DATA;
        d[1] = (byte) player;
        d[2] = (byte) location;
        System.arraycopy(blocks, 0, d, MSG_HEADER_LEN, len);
        return d;
    }

    private static byte[] updateCardPayload(int player, int location, int sequence, byte[] blocks, int len) {
        byte[] d = new byte[len + CARD_HEADER_LEN];
        d[0] = (byte) EngineMessage.MSG_UPDATE_CARD;
        d[1] = (byte) player;
        d[2] = (byte) location;
        d[3] = (byte) sequence;
        System.arraycopy(blocks, 0, d, CARD_HEADER_LEN, len);
        return d;
    }

    private static byte[] range(byte[] msg, int from, int to) {
        return Arrays.copyOfRange(msg, from, Math.min(to, msg.length));
    }

    private static int readInt32(byte[] b, int o) {
        return (b[o] & 0xFF) | ((b[o + 1] & 0xFF) << 8) | ((b[o + 2] & 0xFF) << 16) | ((b[o + 3] & 0xFF) << 24);
    }

    private static void writeInt32(byte[] b, int o, int v) {
        b[o] = (byte) (v & 0xFF);
        b[o + 1] = (byte) ((v >> 8) & 0xFF);
        b[o + 2] = (byte) ((v >> 16) & 0xFF);
        b[o + 3] = (byte) ((v >> 24) & 0xFF);
    }

    private static void zeroRange(byte[] b, int off, int count) {
        if (count > 0) {
            Arrays.fill(b, off, off + count, (byte) 0);
        }
    }
}
