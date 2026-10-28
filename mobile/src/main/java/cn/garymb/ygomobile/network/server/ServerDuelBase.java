package cn.garymb.ygomobile.network.server;

import android.os.SystemClock;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.engine.NativeScriptBootstrap;
import cn.garymb.ygomobile.engine.OcgDuelEngine;
import cn.garymb.ygomobile.network.YGOProtocol;


/**
 * Duel engine lifecycle server-side, base layer of the split
 * inheritance chain (ServerDuelBase <- ServerDuel). Owns every state
 * field, the UndoStep snapshot model, the duel start / message
 * pump / end pipeline, response handling, the undo state probes and
 * the round timer plumbing. The room and analyzer references lose
 * their finality here because the leaf constructor (locked to the
 * concrete ServerDuel type by DuelAnalyzer) assigns them.
 */
abstract class ServerDuelBase implements YGOProtocol {

    protected static final String TAG = "ServerDuel";

    GameRoom room;
    DuelAnalyzer analyzer;
    long pduel = 0L;
    YrpWriter replay;
    int lastReplayResponseSize = 0;

    // === 撤回（undo）状态：本局开局的确定性要素与已应用的玩家应答历史 ===

    /** 开局 seed 副本：撤回靠它重建引擎并重放历史应答（ocgcore 在同一 seed 下是确定的）。 */
    protected int[] undoSeed;
    /** 开局 start_duel 的 option（duelRule 位移 + PSEUDO_SHUFFLE / TAG 位）。 */
    protected int undoOpt;
    /** 开局写入录像的 MSG_START 帧副本（19 字节），重建重放时按原顺序补回消息流。 */
    protected byte[] undoStartFrame;
    /** 重放装载卡组时冻结录像 base 段写入（卡组名/卡码已在首次开局录好，不可重复写）。 */
    protected boolean baseRecordFrozen;
    /** 已应用的玩家应答快照栈（栈顶 = 最近一步；撤回以其中的「动作锚点」为单位整段回退）。 */
    protected final ArrayList<UndoStep> undoSteps = new ArrayList<>();

    /**
     * 一次玩家应答的可回退快照：应答字节 + 应答方 + 应答前的计时三元组 + 该应答所属的询问类型。
     * {@code anchorType} 非 0（{@code MSG_SELECT_IDLECMD} / {@code MSG_SELECT_BATTLECMD}）即表明这一步
     * 是玩家主动的一次动作宣言（召唤/反转召唤/特殊召唤/盖卡/发动效果/攻击宣言/切换阶段），
     * 也就是撤回的目标点；其余应答都是该动作的处理过程，不单独构成可撤回单位。
     * 录像不需要在此存锚点——撤回靠 {@link YrpWriter#resetRecordStream()} + 静默重放整段重录。
     */
    protected static final class UndoStep {
        final byte[] resb;
        final int len;
        final int responder;
        final int timeLimit0;
        final int timeLimit1;
        final int timeElapsed;
        /** 本步应答所属的行动宣言询问类型；0 = 普通处理步骤（连锁/选格/表示形式等）。 */
        final int anchorType;

        UndoStep(byte[] resb, int len, int responder, int timeLimit0, int timeLimit1,
                 int timeElapsed, int anchorType) {
            this.resb = resb;
            this.len = len;
            this.responder = responder;
            this.timeLimit0 = timeLimit0;
            this.timeLimit1 = timeLimit1;
            this.timeElapsed = timeElapsed;
            this.anchorType = anchorType;
        }
    }

    protected ScheduledExecutorService timer;
    protected ScheduledFuture<?> timerTask;

    // ==================================================================
    // 开局：TPResult → 建决斗 → 装载 → start_duel → process
    // ==================================================================

    void startDuel(ServerConnection dp, int tp) {
        // 引擎就绪闸门：LanGameServer.start 为了握手即时，把解压脚本/加载 cdb 的引导
        // 改到了监听就绪后的后台线程；开局是引擎真正的首个使用时点，在此同步幂等引导
        // 兜底——已就绪立即返回，预热/后台引导仍在进行时阻塞至完成，失败则重试一次，
        // 仍失败终止开局（本方法在单线程房间执行器上串行，短暂阻塞不影响已建立连接）
        long gateStart = SystemClock.uptimeMillis();
        boolean engineReady = NativeScriptBootstrap.ensureEngineReady();
        long gateWait = SystemClock.uptimeMillis() - gateStart;
        if (gateWait > 300) {
            // 预热未完成：猜拳后同步跑了完整引导（解压/物化/加载），分段耗时见
            // NativeScriptBootstrap 的「engine bootstrap 耗时」/「引擎 init 完成」日志
            Log.w(TAG, "startDuel：等待引擎引导 " + gateWait + "ms（预热未完成，开局发牌延迟）");
        }
        if (!engineReady) {
            Log.e(TAG, "startDuel：引擎引导失败（脚本解压或卡数据库加载未完成），终止开局");
            room.duelStage = DUEL_STAGE_END;
            return;
        }
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
        // 撤回要素快照：seed / option 定稿后即锁定，重建时须与首跑完全一致
        undoSeed = Arrays.copyOf(seed, seed.length);
        undoOpt = opt;
        undoStartFrame = null;
        undoSteps.clear();
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
        // 撤回重建需按原顺序补回这一帧（此后 startBuf[1] 会被改写，故在此处取副本）
        undoStartFrame = Arrays.copyOf(startBuf, 19);
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
        // 新开局无任何已录动作，撤回提示初始为「不可撤回」
        sendUndoStateToAll();
    }

    protected void swapPlayersAndDecks() {
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
    protected void swapTagDecks() {
        PlayerDeck a = room.decks[0];
        room.decks[0] = room.decks[2];
        room.decks[2] = a;
        PlayerDeck b = room.decks[1];
        room.decks[1] = room.decks[3];
        room.decks[3] = b;
    }

    protected void loadDeckToEngine(PlayerDeck deck, int player) {
        loadOneZone(deck.main, player, OcgDuelEngine.LOCATION_DECK);
        loadOneZone(deck.extra, player, OcgDuelEngine.LOCATION_EXTRA);
    }

    protected void loadOneZone(List<Integer> cards, int player, int location) {
        if (!baseRecordFrozen) {
            replay.writeInt32(cards.size());
        }
        // 逆序装载并写录像（对齐 single_duel.cpp load 的 rbegin→rend）
        for (int i = cards.size() - 1; i >= 0; i--) {
            int code = cards.get(i);
            OcgDuelEngine.newCard(pduel, code, player, player, location, 0,
                    OcgDuelEngine.POS_FACEDOWN_DEFENSE);
            if (!baseRecordFrozen) {
                replay.writeInt32(code);
            }
        }
    }

    /** TAG 队友共享卡组装载：new_tag_card(duel, code, owner, location)，逆序并写录像。 */
    protected void loadOneZoneTag(List<Integer> cards, int owner, int location) {
        if (!baseRecordFrozen) {
            replay.writeInt32(cards.size());
        }
        for (int i = cards.size() - 1; i >= 0; i--) {
            int code = cards.get(i);
            OcgDuelEngine.newTagCard(pduel, code, owner, location);
            if (!baseRecordFrozen) {
                replay.writeInt32(code);
            }
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

    protected void duelEndProc() {
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
        // 撤回快照：在写录像与扣减计时之前记下这一步（应答字节 + 应答方 + 计时回滚点 +
        // 是否行动宣言锚点）；引擎若回以 MSG_RETRY（应答不合法重问）则由 discardPendingUndoStep() 弹出
        int anchorType = analyzer.pendingActionQuestion;
        analyzer.pendingActionQuestion = 0;
        // DP/SP 的发动询问（EFFECTYN/CHAIN）只有答出「肯定发动」才算行动锚点：EFFECTYN 答 1、
        // CHAIN 答 ≥0（选中连锁项）才是本方发起的新动作；答「否」(0)/「不连锁」(-1) 是被动放弃，
        // 不构成可重做的动作、不记锚点——保证本方 DP/SP/M1 没有任何发动/召唤操作时撤回按钮不显示。
        // 主要/战斗阶段的 IDLECMD/BATTLECMD 应答（含切阶段按钮）不受此限：切阶段正是
        // BP/M2/EP「回退到前一阶段」的锚点。
        if (anchorType == EngineMessage.MSG_SELECT_EFFECTYN
                || anchorType == EngineMessage.MSG_SELECT_CHAIN) {
            int choice = ByteBuffer.wrap(resb, 0, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
            if (anchorType == EngineMessage.MSG_SELECT_EFFECTYN ? choice != 1 : choice < 0) {
                anchorType = 0;
            }
        }
        undoSteps.add(new UndoStep(resb, len, responder,
                room.timeLimit[0], room.timeLimit[1], room.timeElapsed, anchorType));
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
        // 本步（或其后的处理）可能新增了可撤回的动作锚点，刷新各席位的撤回提示状态
        sendUndoStateToAll();
    }

    /**
     * 重试熔断落地（{@link DuelAnalyzer} 在 MSG_RETRY 调用）：代那个对同一询问连续非法应答的席位向引擎喂「放弃」，使决斗继续而非永久互等。
     * 回退只倒退服务端引擎、对端客户端局面无法倒退，回退后重发同名效果的旧答复恒非法——本席位手上无询问、点卡无菜单，故须代答。
     * 代答在录像与撤回栈各占一步（与真实答复逐字节同形、非行动锚点）；若引擎判非法，{@link #discardPendingUndoStep()} 与录像回退会一并弹掉。
     */
    void applyForcedPass(int seat, byte[] resb) {
        if (pduel == 0L || seat < 0 || seat >= room.players.length || room.players[seat] == null) {
            return;
        }
        int len = 4;
        undoSteps.add(new UndoStep(resb, len, seat,
                room.timeLimit[0], room.timeLimit[1], room.timeElapsed, 0));
        lastReplayResponseSize = replay.writeResponse(resb, len);
        OcgDuelEngine.setResponseB(pduel, resb);
        room.players[seat].state = ServerConnection.STATE_NONE;
        if (room.hostInfo.timeLimit != 0) {
            if (room.timeLimit[seat] >= room.timeElapsed) {
                room.timeLimit[seat] -= room.timeElapsed;
            } else {
                room.timeLimit[seat] = 0;
            }
            room.timeElapsed = 0;
        }
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

    /**
     * 栈中最近动作锚点（不分席位）的下标，须由请求者宣言才返回否则 -1；solo 下任意锚点算本方。
     * 人机局 AI 以独立连接(type=1)入座按应答席位严格区分，不把 AI 召唤当本方可撤回；对方在我方锚点后再新宣言时不越过对方翻出我方旧锚点——窗口只认「最后动手的是不是我」。
     */
    protected int lastIndexOfOwnAnchor(ServerConnection requester) {
        int anchor = lastIndexOfAnyAnchor();
        if (anchor < 0) {
            return -1;
        }
        if (room.soloMode || undoSteps.get(anchor).responder == requester.type) {
            return anchor;
        }
        return -1;
    }

    /** 栈中最近一个动作锚点下标（不分席位），没有则 -1。 */
    protected int lastIndexOfAnyAnchor() {
        for (int i = undoSteps.size() - 1; i >= 0; i--) {
            if (undoSteps.get(i).anchorType != 0) {
                return i;
            }
        }
        return -1;
    }

    /** 本连接当前是否有可撤回的动作（决定客户端撤回图标是否闪动发光提示）：仅当最近一个动作锚点由本方宣言。 */
    protected boolean canUndoFor(ServerConnection conn) {
        if (conn == null || conn.type > 1) {
            return false;
        }
        return lastIndexOfOwnAnchor(conn) >= 0;
    }

    /**
     * 按连接身份下发 STOC_UNDO_STATE（solo 下一条连接占两席位，去重只发一份）。
     * 观战连接不显示撤回入口，故不下发。
     */
    protected void sendUndoStateToAll() {
        ServerConnection p0 = room.players[0];
        ServerConnection p1 = room.players[1];
        if (p0 != null) {
            p0.send(STOC_UNDO_STATE, new byte[]{(byte) (canUndoFor(p0) ? 1 : 0)});
        }
        if (p1 != null && p1 != p0) {
            p1.send(STOC_UNDO_STATE, new byte[]{(byte) (canUndoFor(p1) ? 1 : 0)});
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

    protected void startTimer() {
        stopTimer();
        timer = Executors.newSingleThreadScheduledExecutor();
        timerTask = timer.scheduleWithFixedDelay(
                () -> room.server.post(this::timerTick), 1000L, 1000L, TimeUnit.MILLISECONDS);
    }

    // === 撤回后的询问延后重挂 ===

    /** 询问重挂前的留白：足够覆盖一次局域网往返，使被丢弃那段的在路上的应答先落地并被状态门吞掉。 */
    protected static final long UNDO_HANG_DELAY_MS = 400L;
    /** 保活心跳周期。 */
    protected static final long QUESTION_KEEPALIVE_TICK_MS = 1000L;
    /**
     * 重挂后多久没有任何答复即视为落空。取得比“人看一眼牌再点”略长，避免把正常询问重发一遍；
     * 它只对撤回重挂的那条询问生效，所以不会干扰其它对局节奏。
     */
    protected static final long QUESTION_KEEPALIVE_GRACE_MS = 3000L;
    /** 同一条询问最多重发几份（重发后仍无答复则交给日志与下一轮玩家操作，不再刷盘）。 */
    protected static final int QUESTION_KEEPALIVE_MAX_REDELIVERIES = 3;
    /**
     * 客户端主动索取重发（{@link #onAskResendRequest}）时的份数上限：取得比保活心跳略高，
     * 因为它是「客户端确实已经答完并且什么也没等到」的直接证据，而非心跳的超时推测。
     */
    protected static final int ASK_RESEND_MAX_REDELIVERIES = 5;
    /** 撤回重挂询问用的单次定时器（限时为 0 时 {@link #timer} 不存在，不能复用）。 */
    protected ScheduledExecutorService undoHangTimer;
    /** 撤回重挂询问的保活任务（与重挂共用同一个单次定时器，随撤回代次作废）。 */
    protected ScheduledFuture<?> questionKeepAliveTask;
    /** 重挂代次：连续多次撤回时旧任务自动作废，只会挂出最后一次撤回的询问。 */
    protected volatile int undoHangGen;

    protected void stopTimer() {
        if (timerTask != null) {
            timerTask.cancel(false);
            timerTask = null;
        }
        if (timer != null) {
            timer.shutdownNow();
            timer = null;
        }
        // 撤回的延后重挂同理作废（局面已不存在，不得再向客户端挂出询问）
        undoHangGen++;
        if (questionKeepAliveTask != null) {
            questionKeepAliveTask.cancel(false);
            questionKeepAliveTask = null;
        }
        if (undoHangTimer != null) {
            undoHangTimer.shutdownNow();
            undoHangTimer = null;
        }
    }
}
