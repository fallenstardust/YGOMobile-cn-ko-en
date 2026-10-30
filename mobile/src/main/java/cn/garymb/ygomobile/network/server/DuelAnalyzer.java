package cn.garymb.ygomobile.network.server;

import android.util.Log;

import java.util.Arrays;

import cn.garymb.ygomobile.engine.OcgDuelEngine;
import ocgcore.DataManager;

/**
 * 引擎消息解析器，移植 {@code gframe/single_duel.cpp}：逐条消费 ocgcore 消息流，按 is_host 遮蔽后广播 {@code STOC_GAME_MSG}；不持有决斗状态，均读写自宿主 {@link ServerDuel}。
 */
final class DuelAnalyzer extends DuelAnalyzerBase {

    DuelAnalyzer(ServerDuel owner) {
        this.owner = owner;
        this.room = owner.room;
        this.soloMode = room.soloMode;
        this.broadcaster = new DuelViewBroadcaster(this);
        this.zoneRefresh = new DuelZoneRefresh(this);
        this.undoGuard = new UndoQuestionGuard(this);
    }

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
                            sendToPlayer(room.players[1 - player], slice);
                            sendToObservers(slice);
                            break;
                        // HINT_ZONE(11)：掩码自带双方座位列位，标注须双方与观战同见；
                        // 旧规则只发 players[1-player] 使人机局发动方本人收不到格色标注
                        //（纯单人 players[1]==players[0] 同连接才碰巧可见）。case 0 为兼容：
                        // 服务端 constant.lua 缺 HINT_ZONE 常量时 lua_tointeger(nil) 降级
                        // 以 type=0 下发（旧规则落 default 被整体丢弃）。broadcastMsg 对
                        // solo 同连接按身份去重，并经 sendToPlayer(players[0]) 录入录像帧
                        case 0:
                        case 11:
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
                case EngineMessage.MSG_SELECT_BATTLECMD: return selectBattleCmd(msg, start, cursor);
                case EngineMessage.MSG_SELECT_IDLECMD: return selectIdleCmd(msg, start, cursor);
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
                    // 引擎只在 core.deck_reversed 真值变化时发本消息（processor.cpp case 13），故跟着取反
                    // 即为引擎当前状态；live 与静默重放共用本方法，重放后其值就是回退点该有的状态，
                    // 供撤回重同步补发翻转（见 UndoQuestionGuard.resyncAfterUndo）
                    liveDeckReversed = !liveDeckReversed;
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
                    int pp = msg[body + 7] & 0xFF;
                    boolean hide = EngineMessage.shouldHideFacedownCode(cp);
                    if ((cl & EngineMessage.LOCATION_ONFIELD) != 0) {
                        cp = EngineMessage.stripRevealFlag(msg, body + 8);
                    }
                    // 遮蔽基准对齐 single_duel.cpp L969-971：目标是卡组/手卡（非墓地/超量素材）的
                    // 移动一律对对手清零卡码。「用卡的效果从卡组抽卡/随机堆顶入手」属私有信息，
                    // 只有脚本显式公开的检索才由 MSG_CONFIRM_CARDS 通道把卡码送达双方；旧实现按
                    // 「任意非手卡→手卡」豁免入手方向，使整个卡组入手都 broadcast 了真实卡码
                    //（用户反馈的信息泄露）。
                    // 例外（本端主机相对 C++ 的有意增强，不造成泄露）：来源已是公开区（墓地/除外/
                    // 场上表侧/超量素材）的入手保留卡码，供对手端播「卡背→正面」确认动画——
                    // 这些卡的身份本来就可查，隐藏它们只会丢失动画而换不到任何保密收益。
                    boolean fromPublicZone = (pl & (OcgDuelEngine.LOCATION_GRAVE
                            | OcgDuelEngine.LOCATION_REMOVED | EngineMessage.LOCATION_OVERLAY)) != 0
                            || ((pl & EngineMessage.LOCATION_ONFIELD) != 0
                            && (pp & EngineMessage.POS_FACEUP) != 0);
                    boolean revealPublicToHand = (cl & EngineMessage.LOCATION_HAND) != 0 && fromPublicZone;
                    cursor = body + 16;
                    byte[] full = range(msg, start, cursor);
                    if (!revealPublicToHand
                            && (cl & (OcgDuelEngine.LOCATION_GRAVE | EngineMessage.LOCATION_OVERLAY)) == 0
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
                    // 静默重放同步统计「有效效果发动」条数（判据与客户端 EffectActivationLedger.record
                    // 一致：code>0 且 desc>MAX_STRING_ID 才登记账本），撤回 ACK 携回供客户端把回退
                    // 后缀之外的发动账本条目剪除——被撤回抹掉的「使用过的效果」即时恢复未使用态。
                    // 体布局（GameMessageParser.Chaining）：code(4)@body，pcc/pcl/pcs/subs/cc/cl/cs(各1)，
                    // desc(4)@body+11，ct(1)；body = start+1（msg[start] 为消息类型字节）
                    if (silent) {
                        int chCode = readInt32(msg, cursor);
                        int chDesc = readInt32(msg, cursor + 11);
                        if (chCode > 0 && (chDesc & 0xFFFFFFFFL) > DataManager.MAX_STRING_ID) {
                            silentChainEvents++;
                        }
                    }
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
}
