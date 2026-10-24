package cn.garymb.ygomobile.network.server;

import java.util.Arrays;

import cn.garymb.ygomobile.engine.OcgDuelEngine;
import cn.garymb.ygomobile.network.YGOProtocol;

/**
 * Engine message analyzer, base layer of the split inheritance chain
 * (DuelAnalyzerBase <- DuelAnalyzer). Owns the shared masks / state
 * fields, the collaborator wiring points, the thin delegation surface
 * toward broadcaster, zone refresh and the undo guard, the byte
 * utilities, and the two largest self-contained question cases of the
 * analyze() switch hoisted verbatim as int-returning handlers (they
 * end in "return 1;" only, so extraction is behaviour-preserving).
 */
abstract class DuelAnalyzerBase implements YGOProtocol {

    protected static final String TAG = "DuelAnalyzer";

    static final int REFRESH_MZONE_FLAG = 0x881fff;
    static final int REFRESH_SZONE_FLAG = 0x681fff;
    static final int REFRESH_HAND_FLAG = 0x681fff;
    static final int REFRESH_GRAVE_FLAG = 0x81fff;
    static final int REFRESH_EXTRA_FLAG = 0xe81fff;
    static final int REFRESH_SINGLE_FLAG = 0xf81fff;
    protected static final int SHUFFLE_HAND_FLAG = 0x781fff;
    protected static final int SHUFFLE_SET_FLAG = 0x181fff;

    /** 撤回全量重同步专用 Mzone 掩码：{@link #REFRESH_MZONE_FLAG} 之上追加 {@code QUERY_OVERLAY_CARD}，补发素材卡码（对齐 ocgapi.cpp，否则叠放素材渲染成卡背）。 */
    static final int RESYNC_MZONE_FLAG = REFRESH_MZONE_FLAG | OcgDuelEngine.QUERY_OVERLAY_CARD;

    static final int QUERY_CODE_POSITION = OcgDuelEngine.QUERY_CODE | OcgDuelEngine.QUERY_POSITION;
    static final int MSG_HEADER_LEN = 3; // MSG_UPDATE_DATA + player + location
    static final int CARD_HEADER_LEN = 4; // MSG_UPDATE_CARD + player + location + sequence

    ServerDuel owner;
    GameRoom room;
    boolean soloMode;

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
    protected static final int RETRY_STREAK_LIMIT = 3;
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
    DuelViewBroadcaster broadcaster;
    DuelZoneRefresh zoneRefresh;
    UndoQuestionGuard undoGuard;

    /** 进入/退出静默重放：清零捕获槽，后续 analyze 只录不发（实现见 UndoQuestionGuard）。 */
    void beginSilentReplay() { undoGuard.beginSilentReplay(); }
    void endSilentReplay() { undoGuard.endSilentReplay(); }

    long pduel() { return owner.pduel; }

    /**
     * MSG_SELECT_BATTLECMD payload walk, zone refresh and question
     * hang. Body moved verbatim from the analyze() switch case.
     */
    int selectBattleCmd(byte[] msg, int start, int cursor) {
        int player;
        int count;
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

    /**
     * MSG_SELECT_IDLECMD payload walk, zone refresh and question
     * hang. Body moved verbatim from the analyze() switch case.
     */
    int selectIdleCmd(byte[] msg, int start, int cursor) {
        int player;
        int count;
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
    protected void recordFrame(byte[] full) { broadcaster.recordFrame(full); }
    protected void sendDualViewAndRecord(int ownerPlayer, byte[] full, byte[] masked) { broadcaster.sendDualViewAndRecord(ownerPlayer, full, masked); }
    void sendToPlayer(ServerConnection dp, byte[] data) { broadcaster.sendToPlayer(dp, data); }
    void sendToObservers(byte[] data) { broadcaster.sendToObservers(data); }
    void broadcastMsg(byte[] data) { broadcaster.broadcastMsg(data); }
    protected void emit(ServerConnection dp, byte[] data) { broadcaster.emit(dp, data); }
    protected void emitObservers(byte[] data) { broadcaster.emitObservers(data); }

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
    protected static byte[] defaultPassAnswer(int qtype) { return UndoQuestionGuard.defaultPassAnswer(qtype); }

    protected static byte[] range(byte[] msg, int from, int to) {
        return Arrays.copyOfRange(msg, from, Math.min(to, msg.length));
    }

    /** 遍历 count 个选卡项，逐个把非应答方可见的卡码清零（SELECT_CARD/UNSELECT/SUM 共用），返回新游标。 */
    protected static int maskCodesForOpponent(byte[] msg, int cursor, int count, int player) {
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
