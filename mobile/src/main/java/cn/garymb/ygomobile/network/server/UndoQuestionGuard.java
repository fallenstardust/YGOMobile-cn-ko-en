package cn.garymb.ygomobile.network.server;

import android.util.Log;

import java.util.Arrays;

import cn.garymb.ygomobile.network.YGOProtocol;

/**
 * 撤回后询问守护域实现（自 DuelAnalyzer 平移，逻辑零改）：全量重同步、终端询问延后重挂、
 * 询问保活与自愈重发、连续非法应答熔断代答。共享状态读写宿主 {@link DuelAnalyzer}（同包包私有）。
 * isPhaseActivationAnchor 留在 DuelAnalyzer（analyze 的 case 内联使用），由此处回调。
 */
final class UndoQuestionGuard implements YGOProtocol {

    private static final String TAG = "DuelAnalyzer";

    private final DuelAnalyzer analyzer;

    UndoQuestionGuard(DuelAnalyzer analyzer) {
        this.analyzer = analyzer;
    }

    /**
     * 撤回成功后把回退后的局面一次性推给全体（对齐“全量重载场地位”语义）：
     * ① MSG_RELOAD_FIELD 广播双方与观战；② Mzone/Szone/Hand/Grave/Extra 各自 refresh，沿用既有
     * 遮蔽语义（Mzone 用 {@link DuelAnalyzer#RESYNC_MZONE_FLAG} 额外携素材卡码）；③ 复位两席位
     * 状态并记下待重挂询问席位（{@link DuelAnalyzer#undoAskResponder}），询问由
     * {@link #hangUndoTerminalQuestion()} 延后单独挂回。全程 recordSuppressed：重同步不写录像。
     * <p>本阶段查询一律 use_cache=0：全量重载下客户端按占位新建全零 ClientCard，被省略字段无人
     * 补齐会导致等级/攻击/守备文本空白，故须发全量查询（详见原类注释②）。
     */
    void resyncAfterUndo(byte[] reloadField) {
        analyzer.recordSuppressed = true;
        try {
            analyzer.broadcastMsg(reloadField);
            // 素材卡码只可能来自这次 Mzone 刷新（reload 载荷不含），故此处用带 QUERY_OVERLAY_CARD
            // 的掩码；实时流程的 refreshMzone 仍用原掩码，录像与逐帧序列不受影响。
            analyzer.refreshMzone(0, DuelAnalyzer.RESYNC_MZONE_FLAG, 0);
            analyzer.refreshMzone(1, DuelAnalyzer.RESYNC_MZONE_FLAG, 0);
            analyzer.refreshSzone(0, DuelAnalyzer.REFRESH_SZONE_FLAG, 0);
            analyzer.refreshSzone(1, DuelAnalyzer.REFRESH_SZONE_FLAG, 0);
            analyzer.refreshHand(0, DuelAnalyzer.REFRESH_HAND_FLAG, 0);
            analyzer.refreshHand(1, DuelAnalyzer.REFRESH_HAND_FLAG, 0);
            analyzer.refreshGrave(0, DuelAnalyzer.REFRESH_GRAVE_FLAG, 0);
            analyzer.refreshGrave(1, DuelAnalyzer.REFRESH_GRAVE_FLAG, 0);
            analyzer.refreshExtra(0, DuelAnalyzer.REFRESH_EXTRA_FLAG, 0);
            analyzer.refreshExtra(1, DuelAnalyzer.REFRESH_EXTRA_FLAG, 0);
            int responder = analyzer.room.lastResponse;
            byte[] terminal = analyzer.silentTerminal;
            if (terminal == null || analyzer.room.players[responder] == null) {
                return;
            }
            // 状态门复位：撤回可能把待应答席位从 A 转移到 B，A 的连接状态残留在 CTOS_RESPONSE/
            // CTOS_TIME_CONFIRM，其后的迟到应答能通过状态门被 getResponse 以 responder=dp.type 当作
            // B 的应答喂给引擎（轻则非法应答引发 MSG_RETRY 风暴，重则旧列表里的合法指令直接作用于
            // 新局面）。C++ WaitforResponse 无需处理，此复位是本工程撤回路径特有的补齐。
            for (ServerConnection pl : analyzer.room.players) {
                if (pl != null) {
                    pl.state = ServerConnection.STATE_NONE;
                }
            }
            // 询问不在此刻挂回，只记下席位交 hangUndoTerminalQuestion() 延后执行：状态复位到询问
            // 挂出之间留出一个往返窗口，被丢弃那一段里已在路上的应答全部落进 STATE_NONE 而被状态门丢弃
            analyzer.undoAskResponder = responder;
        } finally {
            analyzer.recordSuppressed = false;
        }
    }

    /**
     * 撤回重同步的第二步：把静默期捕获的终端询问真正挂回待应答席位
     * （{@link #resyncAfterUndo(byte[])} 记下 {@link DuelAnalyzer#undoAskResponder} 后由 {@link ServerDuel} 延后调用）。
     */
    void hangUndoTerminalQuestion() {
        int responder = analyzer.undoAskResponder;
        analyzer.undoAskResponder = -1;
        if (responder < 0 || responder >= analyzer.room.players.length) {
            return;
        }
        byte[] terminal = analyzer.silentTerminal;
        ServerConnection dp = analyzer.room.players[responder];
        if (terminal == null || dp == null) {
            return;
        }
        analyzer.recordSuppressed = true;
        try {
            analyzer.waitforResponse(responder);
            analyzer.sendToPlayer(dp, terminal);
            // 重挂出去的询问的锚点属性：这里必须整值重写而不是只补 IDLE/BATTLE CMD——静默重放里
            // 的 IDLE/BATTLE 分支也会给它赋值，重放到回退点停下时手上留的是中途某条询问的残留值；
            // 若终端询问不是行动询问，残留值会把紧接着的那个普通应答错记成可回退锚点，下一次撤回就落错位置
            int tq = terminal[0] & 0xFF;
            if (tq == EngineMessage.MSG_SELECT_IDLECMD) {
                analyzer.pendingActionQuestion = EngineMessage.MSG_SELECT_IDLECMD;
            } else if (tq == EngineMessage.MSG_SELECT_BATTLECMD) {
                analyzer.pendingActionQuestion = EngineMessage.MSG_SELECT_BATTLECMD;
            } else if (isPhaseActivationAnchor(responder, tq)) {
                // 回退点是 DP/SP 的发动询问：该询问本身就是本次撤回的锚点，须同样恢复其锚点属性
                analyzer.pendingActionQuestion = tq;
            } else {
                analyzer.pendingActionQuestion = 0;
            }
            // 本条询问来自撤回重挂：交给保活心跳复查（见 beatPendingQuestion），直到它被真正答掉
            analyzer.liveQuestionFromUndo = true;
            Log.i(TAG, "undo: re-hang question type=" + (terminal[0] & 0xFF)
                    + " responder=" + responder);
        } finally {
            analyzer.recordSuppressed = false;
        }
    }

    /**
     * {@link DuelAnalyzer#liveQuestion} 是否就是引擎此刻正在等的这一条询问（代次与当前等待配对）。
     * 不配对的情形包括：上一条询问早已被答掉、以及本次等待属于 ANNOUNCE_*／猜拳这类不被记为询问
     * 的等待——此时手上的字节都是旧题，一律不得重发。
     */
    boolean hasLiveAsk() {
        return analyzer.liveQuestion != null && analyzer.liveQuestionGeneration == analyzer.askGeneration;
    }

    /**
     * 引擎此刻是否仍在等 {@link DuelAnalyzer#room}.lastResponse 那个席位答它手上那个询问。
     * 以连接状态为准：{@link ServerDuel#getResponse} 收到合法应答时会把它改回 STATE_NONE，
     * 因此“仍是 CTOS_RESPONSE / CTOS_TIME_CONFIRM”等价于“这个问题还没人答”。
     */
    private boolean isPendingQuestionUnanswered() {
        if (analyzer.silent || !hasLiveAsk()) {
            return false;
        }
        int seat = analyzer.room.lastResponse;
        if (seat < 0 || seat >= analyzer.room.players.length) {
            return false;
        }
        ServerConnection dp = analyzer.room.players[seat];
        if (dp == null) {
            return false;
        }
        return dp.state == CTOS_RESPONSE || dp.state == CTOS_TIME_CONFIRM;
    }

    /**
     * 把当前挂起的询问原样重发给引擎正在等待的席位（自愈，不写录像、不推进引擎）。两种现场需要它：
     * ①撤回留白期内客户端把答复发早了——两席位都是 STATE_NONE，应答被状态门吞掉，这里连状态一并
     * 重新挂回；②询问在链路上丢了：席位本就是待应答，只需重发一次。
     *
     * @param reason 落盘原因，便于事后从日志区分两条自愈路径
     * @return true = 确实重发了一份询问
     */
    boolean rehangPendingQuestion(String reason) {
        if (analyzer.silent || !hasLiveAsk()) {
            return false;
        }
        int seat = analyzer.room.lastResponse;
        if (seat < 0 || seat >= analyzer.room.players.length) {
            return false;
        }
        ServerConnection dp = analyzer.room.players[seat];
        if (dp == null) {
            return false;
        }
        byte[] ask = analyzer.liveQuestion;
        boolean fromUndo = analyzer.liveQuestionFromUndo;
        int delivered = analyzer.liveQuestionRedeliveries;
        analyzer.recordSuppressed = true;
        try {
            if (dp.state != CTOS_RESPONSE && dp.state != CTOS_TIME_CONFIRM) {
                // 应答被状态门吞掉的形态：席位已是 STATE_NONE，不先把等待态挂回去，重发的询问得同样被吞
                analyzer.waitforResponse(seat);
            }
            // 经统一出站口重发（而不是直接 dp.send）：它会把询问与当前代次重新配对；标记为原样重发，
            // 免得把这条询问已有的连续非法计数清零
            analyzer.resendingAfterRetry = true;
            analyzer.sendToPlayer(dp, ask);
        } finally {
            analyzer.recordSuppressed = false;
        }
        // 重发计数须扛过 waitforResponse 的归零（它只对新询问归零），否则被吞一次的应答
        // 会反复触发重发，与一直在发坏应答的客户端形成乒乓
        analyzer.liveQuestionResponder = seat;
        analyzer.liveQuestionRedeliveries = delivered + 1;
        analyzer.liveQuestionAtMs = System.currentTimeMillis();
        analyzer.liveQuestionFromUndo = fromUndo;
        Log.w(TAG, "question re-hang (" + reason + "): type=" + (ask[0] & 0xFF)
                + " seat=" + seat + " try=" + analyzer.liveQuestionRedeliveries);
        return true;
    }

    /** 重发份数是否已用尽（全部自愈路径共用同一个上限，避免与客户端形成乒乓）。 */
    boolean canRedeliverQuestion(int maxRedeliveries) {
        return hasLiveAsk() && analyzer.liveQuestionRedeliveries < maxRedeliveries;
    }

    /**
     * 询问保活（由 {@link ServerDuel} 的定时任务每秒复查）：只对撤回重挂的那一条询问生效——
     * 它超过 graceMs 仍未得到任何答复时原样重发一次，最多 maxRedeliveries 次。正常对局的询问
     * 不超时重发，因为玩家长考是合法行为。
     */
    void beatPendingQuestion(long nowMs, long graceMs, int maxRedeliveries) {
        if (!analyzer.liveQuestionFromUndo || !canRedeliverQuestion(maxRedeliveries)) {
            return;
        }
        if (nowMs - analyzer.liveQuestionAtMs < graceMs) {
            return;
        }
        if (!isPendingQuestionUnanswered()) {
            // 已被答掉（或局面已不再等待）：保活使命完成，后续询问不再属于撤回那一路
            analyzer.liveQuestionFromUndo = false;
            return;
        }
        rehangPendingQuestion("undo keep-alive");
    }

    /**
     * 「必定合法、且含义等于放弃」的应答，供 {@link DuelAnalyzer#retryStreak} 熔断时代替僵住的席位作答：
     * CHAIN = int32 -1（不连锁），EFFECTYN/YESNO = int32 0（否）。其余询问的 0 号应答都是真实选择
     * 而非放弃，故返回 null 表示这题没有安全的放弃答案，熔断不介入、照旧重问。
     */
    static byte[] defaultPassAnswer(int qtype) {
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
     * 进入静默重放：清零捕获槽，后续 analyze 只录不发（ServerDuel 为撤回上重建引擎并逐条重放历史应答时调用）。
     */
    void beginSilentReplay() {
        analyzer.silentTerminal = null;
        analyzer.silentWaitPending = false;
        analyzer.undoAskResponder = -1;
        // 静默重放从开局重写引擎状态：连锁进行标志先复位，livePhase/liveTurnPlayer 随后由重放消息流重新导出
        analyzer.liveChainActive = false;
        // 引擎即将重建：旧的 liveQuestion 属于要被抹掉的那一段，保活绝不能再把它重发出去
        analyzer.liveQuestion = null;
        analyzer.liveQuestionGeneration = -1;
        analyzer.liveQuestionFromUndo = false;
        analyzer.liveQuestionResponder = -1;
        analyzer.silentTurnCount = 0;
        analyzer.silentTurnPlayer = 0;
        analyzer.silentPhase = 0;
        analyzer.silent = true;
    }

    /** 退出静默重放。 */
    void endSilentReplay() {
        analyzer.silent = false;
        analyzer.silentWaitPending = false;
    }

    /**
     * 这条发动询问是否为回合玩家在抽卡/准备阶段（DP/SP）发起的新连锁（可整段撤回的动作锚点）。
     * 仅当同时满足三条：①应答方是当前回合玩家；②当前阶段为 DP 或 SP；③此刻不在连锁结算中。
     * 只标记「具备锚点资格」，是否落锚由应答内容裁决（见 ServerDuel.getResponse）。
     */
    boolean isPhaseActivationAnchor(int responderPlayer, int questionType) {
        if (questionType != EngineMessage.MSG_SELECT_CHAIN
                && questionType != EngineMessage.MSG_SELECT_EFFECTYN) {
            return false;
        }
        if (analyzer.liveChainActive || analyzer.liveTurnPlayer < 0 || responderPlayer != analyzer.liveTurnPlayer) {
            return false;
        }
        if ((analyzer.livePhase & (EngineMessage.PHASE_DRAW | EngineMessage.PHASE_STANDBY)) == 0) {
            return false;
        }
        if (!analyzer.silent) {
            // 留一条可核对的足迹：DP/SP 发动询问被记为锚点时打点，实测若按钮不亮可先查 logcat 有无此行
            Log.i(TAG, "DP/SP activation anchor: responder=" + responderPlayer
                    + " question=" + questionType + " phase=0x" + Integer.toHexString(analyzer.livePhase));
        }
        return true;
    }
}
