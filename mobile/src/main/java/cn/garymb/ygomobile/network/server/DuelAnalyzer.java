package cn.garymb.ygomobile.network.server;

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

    private static final int REFRESH_MZONE_FLAG = 0x881fff;
    private static final int REFRESH_SZONE_FLAG = 0x681fff;
    private static final int REFRESH_HAND_FLAG = 0x681fff;
    private static final int REFRESH_GRAVE_FLAG = 0x81fff;
    private static final int REFRESH_EXTRA_FLAG = 0xe81fff;
    private static final int REFRESH_SINGLE_FLAG = 0xf81fff;
    private static final int SHUFFLE_HAND_FLAG = 0x781fff;
    private static final int SHUFFLE_SET_FLAG = 0x181fff;

    private static final int QUERY_CODE_POSITION = OcgDuelEngine.QUERY_CODE | OcgDuelEngine.QUERY_POSITION;
    private static final int MSG_HEADER_LEN = 3; // MSG_UPDATE_DATA + player + location
    private static final int CARD_HEADER_LEN = 4; // MSG_UPDATE_CARD + player + location + sequence

    private final ServerDuel owner;
    private final GameRoom room;

    DuelAnalyzer(ServerDuel owner) {
        this.owner = owner;
        this.room = owner.room;
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
                    if (owner.lastReplayResponseSize != 0) {
                        owner.replay.removeData(owner.lastReplayResponseSize);
                        owner.lastReplayResponseSize = 0;
                    }
                    waitforResponse(room.lastResponse);
                    sendToPlayer(room.players[room.lastResponse], range(msg, start, cursor));
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
                        sendToPlayer(room.players[1 - player], slice);
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
                    sendToPlayer(room.players[player], range(msg, start, cursor));
                    for (int i = 0; i < count; i++) {
                        writeInt32(msg, codeBase + i * 4, 0);
                    }
                    byte[] masked = range(msg, start, cursor);
                    sendToPlayer(room.players[1 - player], masked);
                    sendToObservers(masked);
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
                    sendToPlayer(room.players[player], range(msg, start, cursor));
                    for (int i = 0; i < count; i++) {
                        writeInt32(msg, codeBase + i * 4, 0);
                    }
                    byte[] masked = range(msg, start, cursor);
                    sendToPlayer(room.players[1 - player], masked);
                    sendToObservers(masked);
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
                    room.timeLimit[0] = room.hostInfo.timeLimit;
                    room.timeLimit[1] = room.hostInfo.timeLimit;
                    broadcastMsg(range(msg, start, cursor));
                    break;
                }
                case EngineMessage.MSG_NEW_PHASE: {
                    cursor += 2;
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
                    cursor = body + 16;
                    sendToPlayer(room.players[cc], range(msg, start, cursor));
                    if ((cl & (OcgDuelEngine.LOCATION_GRAVE | EngineMessage.LOCATION_OVERLAY)) == 0
                            && (((cl & (OcgDuelEngine.LOCATION_DECK | OcgDuelEngine.LOCATION_HAND)) != 0) || hide)) {
                        writeInt32(msg, body, 0);
                    }
                    byte[] opp = range(msg, start, cursor);
                    sendToPlayer(room.players[1 - cc], opp);
                    sendToObservers(opp);
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
                    writeInt32(msg, cursor, 0);
                    cursor += 4;
                    broadcastMsg(range(msg, start, cursor));
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
                    sendToPlayer(room.players[cc], range(msg, start, cursor));
                    if (hide) {
                        writeInt32(msg, body, 0);
                    }
                    byte[] opp = range(msg, start, cursor);
                    sendToPlayer(room.players[1 - cc], opp);
                    sendToObservers(opp);
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
                    sendToPlayer(room.players[player], slice);
                    sendToPlayer(room.players[1], slice);
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
                    sendToPlayer(room.players[player], range(msg, start, cursor));
                    int p = codeBase;
                    for (int i = 0; i < count; i++) {
                        if ((msg[p + 3] & 0x80) == 0) {
                            writeInt32(msg, p, 0);
                        }
                        p += 4;
                    }
                    byte[] masked = range(msg, start, cursor);
                    sendToPlayer(room.players[1 - player], masked);
                    sendToObservers(masked);
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
        sendToPlayer(room.players[player], updateDataPayload(player, location, blocks, len));
        for (int[] seg : hidden) {
            zeroRange(blocks, seg[0], seg[1] - 4);
        }
        byte[] opp = updateDataPayload(player, location, blocks, len);
        sendToPlayer(room.players[1 - player], opp);
        sendToObservers(opp);
    }

    void refreshHand(int player) {
        refreshHand(player, REFRESH_HAND_FLAG, 1);
    }

    void refreshHand(int player, int flag, int useCache) {
        byte[] blocks = OcgDuelEngine.queryFieldCard(pduel(), player, OcgDuelEngine.LOCATION_HAND, flag, useCache);
        int len = blocks.length;
        sendToPlayer(room.players[player],
                updateDataPayload(player, OcgDuelEngine.LOCATION_HAND, blocks, len));
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
        byte[] opp = updateDataPayload(player, OcgDuelEngine.LOCATION_HAND, blocks, len);
        sendToPlayer(room.players[1 - player], opp);
        sendToObservers(opp);
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
        boolean hide = (position & EngineMessage.POS_FACEDOWN) != 0;
        if ((location & EngineMessage.LOCATION_ONFIELD) != 0) {
            hide = EngineMessage.shouldHideFacedownCode(position);
            EngineMessage.stripRevealFlag(blocks, 12);
        }
        sendToPlayer(room.players[player], updateCardPayload(player, location, sequence, blocks, len));
        int sendLen = len;
        if (hide) {
            writeInt32(blocks, 0, 16);
            writeInt32(blocks, 4, QUERY_CODE_POSITION);
            writeInt32(blocks, 8, 0); // 清零 code，保留 12..15 姿态
            sendLen = 16;
        }
        byte[] opp = updateCardPayload(player, location, sequence, blocks, sendLen);
        sendToPlayer(room.players[1 - player], opp);
        sendToObservers(opp);
    }

    // ==================================================================
    // 发送 / 字节工具
    // ==================================================================

    private void sendToPlayer(ServerConnection dp, byte[] data) {
        if (dp != null) {
            dp.send(STOC_GAME_MSG, data);
        }
    }

    private void sendToObservers(byte[] data) {
        for (ServerConnection o : room.observers) {
            o.send(STOC_GAME_MSG, data);
        }
    }

    private void broadcastMsg(byte[] data) {
        sendToPlayer(room.players[0], data);
        sendToPlayer(room.players[1], data);
        sendToObservers(data);
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
