package cn.garymb.ygomobile.network.server;

import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.engine.OcgDuelEngine;
import cn.garymb.ygomobile.network.YGOProtocol;

/**
 * 决斗引擎生命周期，移植 {@code Classes/gframe/single_duel.cpp} 的开局/应答/计时/结束
 * （TPResult 起 → create_duel_v2/set_player_info/new_card/start_duel → Process 循环 →
 * GetResponse/TimeConfirm/TimerTick/Surrender/EndDuel/DuelEndProc）。
 *
 * <p>引擎消息解析（Analyze）与区域刷新（Refresh*）遮蔽逻辑委托给 {@link DuelAnalyzer}；房间共享状态
 * （players/pplayer/observers/hostInfo/decks/duelStage/match 系列/timeLimit）读写自 {@link GameRoom}。
 * 引擎非线程安全，本类所有方法均在 {@link LanGameServer} 的单线程房间执行器上串行调用；
 * {@link #startDuel} 内联运行 {@link #process} 直到引擎等待玩家应答。
 */
final class ServerDuel implements YGOProtocol {

    private static final String TAG = "ServerDuel";

    final GameRoom room;
    final DuelAnalyzer analyzer;
    long pduel = 0L;
    YrpWriter replay;
    int lastReplayResponseSize = 0;

    private ScheduledExecutorService timer;
    private ScheduledFuture<?> timerTask;

    ServerDuel(GameRoom room) {
        this.room = room;
        this.analyzer = new DuelAnalyzer(this);
    }

    // ==================================================================
    // 开局：TPResult → 建决斗 → 装载 → start_duel → process
    // ==================================================================

    void startDuel(ServerConnection dp, int tp) {
        GameRoom.HostInfo hi = room.hostInfo;
        boolean tag = room.tagMode;
        room.duelStage = DUEL_STAGE_DUELING;
        boolean swapped = false;
        room.pplayer[0] = room.players[0];
        room.pplayer[1] = room.players[1];
        // 选对方先攻时交换座位（含各自卡组）；TAG 下 4 席位同一连接，只整体交换两队卡组
        boolean chooseFirst = tp != 0;
        if ((chooseFirst && dp.type == 1) || (!chooseFirst && dp.type == 0)) {
            if (tag) {
                swapTagDecks();
            } else {
                swapPlayersAndDecks();
            }
            swapped = true;
        }
        dp.state = CTOS_RESPONSE;

        int[] seed = new int[8];
        Random rnd = new Random();
        for (int i = 0; i < 8; i++) {
            seed[i] = rnd.nextInt();
        }
        int startTime = (int) (System.currentTimeMillis() / 1000L);
        int replayFlag = YrpWriter.REPLAY_UNIFORM | (tag ? YrpWriter.REPLAY_TAG : 0);
        replay = new YrpWriter(seed, Constants.PRO_VERSION, replayFlag, startTime);
        replay.writeName(room.players[0].name);
        replay.writeName(room.players[1].name);
        if (tag) {
            // TAG 录像头需 4 个席位名；solo 下协议不上传各席位卡组名，服务器不可知，统一写房主名
            replay.writeName(room.players[0].name);
            replay.writeName(room.players[1].name);
        }

        // no_shuffle 时保持卡组原序（引擎以 DUEL_PSEUDO_SHUFFLE 处理），否则服务端洗牌
        if (hi.noShuffleDeck == 0) {
            java.util.Collections.shuffle(room.decks[0].main);
            java.util.Collections.shuffle(room.decks[1].main);
            if (tag) {
                java.util.Collections.shuffle(room.decks[2].main);
                java.util.Collections.shuffle(room.decks[3].main);
            }
        }
        room.timeLimit[0] = hi.timeLimit;
        room.timeLimit[1] = hi.timeLimit;
        room.timeElapsed = 0;

        pduel = OcgDuelEngine.createDuelV2(seed);
        if (pduel == 0L) {
            Log.e(TAG, "createDuelV2 失败：引擎不可用或卡数据未加载");
            room.duelStage = DUEL_STAGE_END;
            return;
        }
        OcgDuelEngine.setPlayerInfo(pduel, 0, hi.startLp, hi.startHand, hi.drawCount);
        OcgDuelEngine.setPlayerInfo(pduel, 1, hi.startLp, hi.startHand, hi.drawCount);
        int opt = (hi.duelRule << 16);
        if (hi.noShuffleDeck != 0) {
            opt |= OcgDuelEngine.DUEL_PSEUDO_SHUFFLE;
        }
        if (tag) {
            opt |= OcgDuelEngine.DUEL_TAG_MODE;
        }
        replay.writeInt32(hi.startLp);
        replay.writeInt32(hi.startHand);
        replay.writeInt32(hi.drawCount);
        replay.writeInt32(opt);

        if (tag) {
            // 装载顺序对齐 TagDuel::TPResult：引擎玩家 0 = decks[0](single)+decks[1](tag)，
            // 引擎玩家 1 = decks[3](single)+decks[2](tag)。
            loadOneZone(room.decks[0].main, 0, OcgDuelEngine.LOCATION_DECK);
            loadOneZone(room.decks[0].extra, 0, OcgDuelEngine.LOCATION_EXTRA);
            loadOneZoneTag(room.decks[1].main, 0, OcgDuelEngine.LOCATION_DECK);
            loadOneZoneTag(room.decks[1].extra, 0, OcgDuelEngine.LOCATION_EXTRA);
            loadOneZone(room.decks[3].main, 1, OcgDuelEngine.LOCATION_DECK);
            loadOneZone(room.decks[3].extra, 1, OcgDuelEngine.LOCATION_EXTRA);
            loadOneZoneTag(room.decks[2].main, 1, OcgDuelEngine.LOCATION_DECK);
            loadOneZoneTag(room.decks[2].extra, 1, OcgDuelEngine.LOCATION_EXTRA);
        } else {
            loadDeckToEngine(room.decks[0], 0);
            loadDeckToEngine(room.decks[1], 1);
        }

        // MSG_START（19 字节）
        byte[] startBuf = new byte[19];
        ByteBuffer sb = ByteBuffer.wrap(startBuf).order(ByteOrder.LITTLE_ENDIAN);
        sb.put((byte) EngineMessage.MSG_START);
        sb.put((byte) 0); // player（对 players[0] 而言）
        sb.put((byte) hi.duelRule);
        sb.putInt(hi.startLp);
        sb.putInt(hi.startLp);
        sb.putShort((short) OcgDuelEngine.queryFieldCount(pduel, 0, OcgDuelEngine.LOCATION_DECK));
        sb.putShort((short) OcgDuelEngine.queryFieldCount(pduel, 0, OcgDuelEngine.LOCATION_EXTRA));
        sb.putShort((short) OcgDuelEngine.queryFieldCount(pduel, 1, OcgDuelEngine.LOCATION_DECK));
        sb.putShort((short) OcgDuelEngine.queryFieldCount(pduel, 1, OcgDuelEngine.LOCATION_EXTRA));
        // 录像：手工 MSG_START 不经 DuelAnalyzer.sendToPlayer，主机视角变体单独入消息流
        replay.writeMessage(startBuf, 19);
        if (room.soloMode) {
            // Solo mode: players[0] == players[1] (same connection). Send only the player=0
            // variant once so client's onStart runs exactly once and duelIsFirst stays true.
            // Perspective will auto-flip on turn changes via solo branch in DuelEventHandler.
            room.sendGameMsg(room.players[0], Arrays.copyOf(startBuf, 19));
            startBuf[1] = (byte) (swapped ? 0x11 : 0x10);
        } else {
            room.sendGameMsg(room.players[0], Arrays.copyOf(startBuf, 19));
            startBuf[1] = 1;
            room.sendGameMsg(room.players[1], Arrays.copyOf(startBuf, 19));
            startBuf[1] = (byte) (swapped ? 0x11 : 0x10);
        }
        for (ServerConnection o : room.observers) {
            o.send(STOC_GAME_MSG, Arrays.copyOf(startBuf, 19));
        }

        analyzer.refreshExtra(0);
        analyzer.refreshExtra(1);
        OcgDuelEngine.startDuel(pduel, opt);
        if (hi.timeLimit != 0) {
            startTimer();
        }
        process();
    }

    private void swapPlayersAndDecks() {
        ServerConnection t = room.players[0];
        room.players[0] = room.players[1];
        room.players[1] = t;
        room.players[0].type = 0;
        room.players[1].type = 1;
        PlayerDeck d = room.decks[0];
        room.decks[0] = room.decks[1];
        room.decks[1] = d;
    }

    /** TAG 选后攻时整队交换卡组槽位（对齐 TagDuel::TPResult 的 pdeck0↔2/pdeck1↔3）。 */
    private void swapTagDecks() {
        PlayerDeck a = room.decks[0];
        room.decks[0] = room.decks[2];
        room.decks[2] = a;
        PlayerDeck b = room.decks[1];
        room.decks[1] = room.decks[3];
        room.decks[3] = b;
    }

    private void loadDeckToEngine(PlayerDeck deck, int player) {
        loadOneZone(deck.main, player, OcgDuelEngine.LOCATION_DECK);
        loadOneZone(deck.extra, player, OcgDuelEngine.LOCATION_EXTRA);
    }

    private void loadOneZone(List<Integer> cards, int player, int location) {
        replay.writeInt32(cards.size());
        // 逆序装载并写录像（对齐 single_duel.cpp load 的 rbegin→rend）
        for (int i = cards.size() - 1; i >= 0; i--) {
            int code = cards.get(i);
            OcgDuelEngine.newCard(pduel, code, player, player, location, 0,
                    OcgDuelEngine.POS_FACEDOWN_DEFENSE);
            replay.writeInt32(code);
        }
    }

    /** TAG 队友共享卡组装载：new_tag_card(duel, code, owner, location)，逆序并写录像。 */
    private void loadOneZoneTag(List<Integer> cards, int owner, int location) {
        replay.writeInt32(cards.size());
        for (int i = cards.size() - 1; i >= 0; i--) {
            int code = cards.get(i);
            OcgDuelEngine.newTagCard(pduel, code, owner, location);
            replay.writeInt32(code);
        }
    }

    // ==================================================================
    // 引擎消息主循环
    // ==================================================================

    void process() {
        if (pduel == 0L) {
            return;
        }
        int engFlag = 0;
        int stop = 0;
        while (stop == 0) {
            if (engFlag == OcgDuelEngine.PROCESSOR_END) {
                break;
            }
            int result = OcgDuelEngine.process(pduel);
            int engLen = result & OcgDuelEngine.PROCESSOR_BUFFER_LEN;
            engFlag = result & OcgDuelEngine.PROCESSOR_FLAG;
            if (engLen > 0) {
                byte[] msg = OcgDuelEngine.getMessage(pduel);
                stop = analyzer.analyze(msg, msg.length);
            }
        }
        if (stop == 2) {
            duelEndProc();
        }
    }

    private void duelEndProc() {
        if (!room.matchMode) {
            room.broadcastToEmpty(STOC_DUEL_END, null);
            room.duelStage = DUEL_STAGE_END;
        } else {
            int[] winc = {0, 0, 0};
            for (int i = 0; i < room.duelCount; i++) {
                winc[room.matchResult[i]]++;
            }
            if (room.matchKill != 0
                    || winc[0] == 2 || (winc[0] == 1 && winc[2] == 2)
                    || winc[1] == 2 || (winc[1] == 1 && winc[2] == 2)
                    || winc[2] == 3 || (winc[0] == 1 && winc[1] == 1 && winc[2] == 1)) {
                room.broadcastToEmpty(STOC_DUEL_END, null);
                room.duelStage = DUEL_STAGE_END;
            } else {
                if (room.players[0] != room.pplayer[0]) {
                    swapPlayersAndDecks();
                }
                room.ready[0] = false;
                room.ready[1] = false;
                room.players[0].state = CTOS_UPDATE_DECK;
                room.players[1].state = CTOS_UPDATE_DECK;
                room.soloSideCount = 0;
                java.util.Arrays.fill(room.soloSideFilled, false);
                room.players[0].send(STOC_CHANGE_SIDE, null);
                // solo：同一连接不能一次叠两个换 side 编辑器，第二份由 updateDeck 提交后逐份引导
                if (room.players[1] != room.players[0]) {
                    room.players[1].send(STOC_CHANGE_SIDE, null);
                }
                for (ServerConnection o : room.observers) {
                    o.send(STOC_WAITING_SIDE, null);
                }
                room.duelStage = DUEL_STAGE_SIDING;
            }
        }
    }

    /** match 换边后双方就绪：直接以当前先攻方进入下一局（复用 first-go 选择）。 */
    void startNextDuelFromSide() {
        room.players[room.tpPlayer].send(STOC_SELECT_TP, null);
        room.players[1 - room.tpPlayer].state = ServerConnection.STATE_NONE;
        room.players[room.tpPlayer].state = CTOS_TP_RESULT;
        room.duelStage = DUEL_STAGE_FIRSTGO;
    }

    // ==================================================================
    // 玩家应答 / 计时 / 投降 / 结束
    // ==================================================================

    void getResponse(ServerConnection dp, byte[] resp) {
        if (pduel == 0L) {
            return;
        }
        // Solo mode: dp.type is always 0 (same connection), but engine may wait for player 1's response
        int responder = room.soloMode ? room.lastResponse : dp.type;
        int len = Math.min(resp.length, EngineMessage.SIZE_RETURN_VALUE - 1);
        byte[] resb = new byte[EngineMessage.SIZE_RETURN_VALUE];
        System.arraycopy(resp, 0, resb, 0, len);
        lastReplayResponseSize = replay.writeResponse(resb, len);
        OcgDuelEngine.setResponseB(pduel, resb);
        room.players[responder].state = ServerConnection.STATE_NONE;
        if (room.hostInfo.timeLimit != 0) {
            if (room.timeLimit[responder] >= room.timeElapsed) {
                room.timeLimit[responder] -= room.timeElapsed;
            } else {
                room.timeLimit[responder] = 0;
            }
            room.timeElapsed = 0;
        }
        process();
        lastReplayResponseSize = 0;
    }

    void timeConfirm(ServerConnection dp) {
        if (room.hostInfo.timeLimit == 0 || dp.type != room.lastResponse) {
            return;
        }
        room.players[room.lastResponse].state = CTOS_RESPONSE;
        if (room.timeElapsed < 10) {
            room.timeElapsed = 0;
        }
    }

    void surrender(ServerConnection dp) {
        if (dp.type > 1 || pduel == 0L) {
            return;
        }
        int player = dp.type;
        byte[] wbuf = new byte[]{(byte) EngineMessage.MSG_WIN, (byte) (1 - player), (byte) 0};
        room.broadcastGameMsg(wbuf);
        if (replay != null) {
            replay.writeMessage(wbuf, wbuf.length);
        }
        if (room.players[player] == room.pplayer[player]) {
            room.matchResult[room.duelCount++] = 1 - player;
            room.tpPlayer = player;
        } else {
            room.matchResult[room.duelCount++] = player;
            room.tpPlayer = 1 - player;
        }
        endDuel();
        duelEndProc();
    }

    /** 计时到点（由 {@link LanGameServer} 的单线程执行器每秒投递一次）。 */
    void timerTick() {
        if (pduel == 0L) {
            return;
        }
        room.timeElapsed++;
        if (room.timeElapsed >= room.timeLimit[room.lastResponse]) {
            int player = room.lastResponse;
            byte[] wbuf = new byte[]{(byte) EngineMessage.MSG_WIN, (byte) (1 - player), (byte) 0x3};
            room.broadcastGameMsg(wbuf);
            if (replay != null) {
                replay.writeMessage(wbuf, wbuf.length);
            }
            if (room.players[player] == room.pplayer[player]) {
                room.matchResult[room.duelCount++] = 1 - player;
                room.tpPlayer = player;
            } else {
                room.matchResult[room.duelCount++] = player;
                room.tpPlayer = 1 - player;
            }
            endDuel();
            duelEndProc();
        }
    }

    void onOpponentLeft(ServerConnection dp) {
        if (room.hostPlayer == dp) {
            room.hostPlayer = null;
        }
        for (int i = 0; i < 2; i++) {
            if (room.players[i] == dp) {
                room.players[i] = null;
                room.ready[i] = false;
            }
            if (room.pplayer[i] == dp) {
                room.pplayer[i] = null;
            }
        }
        room.observers.remove(dp);
        if (pduel != 0L) {
            endDuel();
            room.duelStage = DUEL_STAGE_END;
        }
    }

    void endDuel() {
        if (pduel == 0L) {
            return;
        }
        byte[] yrp = replay.build();
        if (room.players[0] != null) {
            room.players[0].send(STOC_REPLAY, yrp);
        }
        // Solo mode: players[1] == players[0] is the same connection; send replay once.
        if (room.players[1] != null && room.players[1] != room.players[0]) {
            room.players[1].send(STOC_REPLAY, yrp);
        }
        for (ServerConnection o : room.observers) {
            o.send(STOC_REPLAY, yrp);
        }
        stopTimer();
        OcgDuelEngine.endDuel(pduel);
        pduel = 0L;
    }

    private void startTimer() {
        stopTimer();
        timer = Executors.newSingleThreadScheduledExecutor();
        timerTask = timer.scheduleWithFixedDelay(
                () -> room.server.post(this::timerTick), 1000L, 1000L, TimeUnit.MILLISECONDS);
    }

    private void stopTimer() {
        if (timerTask != null) {
            timerTask.cancel(false);
            timerTask = null;
        }
        if (timer != null) {
            timer.shutdownNow();
            timer = null;
        }
    }
}
