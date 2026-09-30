package cn.garymb.ygomobile.network.server;

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

    // === 撤回（undo）状态：本局开局的确定性要素与已应用的玩家应答历史 ===

    /** 开局 seed 副本：撤回靠它重建引擎并重放历史应答（ocgcore 在同一 seed 下是确定的）。 */
    private int[] undoSeed;
    /** 开局 start_duel 的 option（duelRule 位移 + PSEUDO_SHUFFLE / TAG 位）。 */
    private int undoOpt;
    /** 开局写入录像的 MSG_START 帧副本（19 字节），重建重放时按原顺序补回消息流。 */
    private byte[] undoStartFrame;
    /** 重放装载卡组时冻结录像 base 段写入（卡组名/卡码已在首次开局录好，不可重复写）。 */
    private boolean baseRecordFrozen;
    /** 已应用的玩家应答快照栈（栈顶 = 最近一步；撤回以其中的「动作锚点」为单位整段回退）。 */
    private final ArrayList<UndoStep> undoSteps = new ArrayList<>();

    /**
     * 一次玩家应答的可回退快照：应答字节 + 应答方 + 应答前的计时三元组 + 该应答所属的询问类型。
     * {@code anchorType} 非 0（{@code MSG_SELECT_IDLECMD} / {@code MSG_SELECT_BATTLECMD}）即表明这一步
     * 是玩家主动的一次动作宣言（召唤/反转召唤/特殊召唤/盖卡/发动效果/攻击宣言/切换阶段），
     * 也就是撤回的目标点；其余应答都是该动作的处理过程，不单独构成可撤回单位。
     * 录像不需要在此存锚点——撤回靠 {@link YrpWriter#resetRecordStream()} + 静默重放整段重录。
     */
    private static final class UndoStep {
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
    private void loadOneZoneTag(List<Integer> cards, int owner, int location) {
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
     * 重试熔断的落地（由 {@link DuelAnalyzer} 在 MSG_RETRY 分支调用）：代替那个对同一条询问
     * 连续给出非法应答的席位，向引擎喂一份「放弃」答复，使决斗继续而不是永久互等。
     *
     * <p>为什么需要它：本局的回退只倒退服务端的引擎，对端客户端自己那份场面（人机局的 AI、
     * 第三方 gframe 客户端都按收到的消息流维护自己的局面）无法倒退；回退后重新发动同一个效果时，
     * 它按旧局面编出的答复就不再合法，引擎反复回 MSG_RETRY，而那个席位永远答不上来——本席位
     * 界面手上没有任何询问，点卡弹不出命令菜单，决斗推不下去。
     *
     * <p>这份代答在录像与撤回栈里各占一步（与真实答复逐字节同形，不是行动锚点），
     * 所以静默重放往回退之前重建时仍能逐帧对齐；若引擎把它也判非法，既有的
     * {@link #discardPendingUndoStep()} 与录像回退同样会把它一并弹掉。
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

    // ==================================================================
    // 撤回操作（undo）：本工程扩展——局域网房间内回退「最近一个完整动作」
    // ==================================================================

    /**
     * CTOS_UNDO：把局面回退到本席位最近一个动作宣言之前（引擎按开局 seed 重建 + 重放更早的应答）。
     * 可连续多次撤回，直到 {@link #undoSteps} 中再无动作锚点（不能越过本局第一个动作，
     * 也不支持跨局 match 换 side 后的往局回撤——每局一个新 {@code ServerDuel}，栈天然隔离）。
     *
     * <p>撤回单位：不是逐条应答，而是一个完整动作。栈中只有应答
     * {@code MSG_SELECT_IDLECMD} / {@code MSG_SELECT_BATTLECMD}，以及回合玩家在抽卡/准备阶段发动时以
     * {@code MSG_SELECT_CHAIN} / {@code MSG_SELECT_EFFECTYN} 挂出的那几步是「动作锚点」，分别对应
     * 召唤 / 反转召唤 / 特殊召唤 / 盖卡 / 发动效果 / 攻击宣言 / 切换阶段；锚点之后的连锁询问、
     * 选格、表示形式、对方应答全属于该动作的处理过程，故一次撤回会连带丢弃多步，回到玩家做出
     * 这个动作之前的空闲询问，使其能重新对卡片进行这些操作。
     *
     * <p>权限：栈中**最近一个**动作锚点由请求者席位宣言时才回退（谁操作谁撤回；我方发动后对方在
     * 连锁中的应答不是锚点、不关闭本方撤回窗口，但对方一旦做出新的动作宣言，本方撤回窗口随即
     * 关闭，绝不越过对方的操作去回退我方更早的动作）；solo 一条连接占两席位，任何锚点都算本方；
     * 观战连接（type &gt; 1）一律拒绝。
     *
     * <p>同步：不重发 MSG_START（客户端 onStart 会 {@code field.clear()} 把回合数归零），
     * 而是先发 STOC_UNDO_ACK（带回合数/当前回合玩家/阶段）再发 MSG_RELOAD_FIELD 全量重载
     * + 各区域 refresh + 重新挂回待应答询问；重同步过程不写录像。
     *
     * <p>录像：清空已录的应答/消息帧，由静默重放整段重录，产物与“那一步从未做过”逐帧等价。
     */
    void undoLastResponse(ServerConnection requester) {
        if (requester == null || requester.type > 1 || pduel == 0L
                || room.duelStage != DUEL_STAGE_DUELING || undoSteps.isEmpty()) {
            Log.i(TAG, "undo 拒绝：duelStage=" + room.duelStage + " pduel=" + pduel
                    + " steps=" + undoSteps.size() + " requesterType="
                    + (requester == null ? -1 : requester.type));
            denyUndo(requester);
            return;
        }
        // 回退目标 = 栈中最近一个动作锚点，且必须由本席位宣言（该锚点及其之后的全部步骤一并丢弃）；
        // 最近锚点属对方（单纯对方操作）或栈中根本无锚点（只有抽卡/连锁应答等非宣言步骤）时拒绝。
        int target = lastIndexOfOwnAnchor(requester);
        if (target < 0) {
            Log.i(TAG, "undo 不受理：最近锚点非本方宣言或栈中无动作锚点（steps=" + undoSteps.size()
                    + ", requester=" + requester.type + "）");
            denyUndo(requester);
            return;
        }
        // 对方席位此刻正握着待应答询问（人机局的 AI 正在算连锁、或对方玩家正在选卡）时原则上不受理撤回：
        // 回退点落在本方的行动询问上，而对方那个询问并不在重挂范围内，撤回会把它凭空抹掉；对方（尤其
        // 是独立进程的 AI）无从得知自己的询问已被撤销，它会把算完的应答打到重挂出来的新局面上——
        // 错拍应答使引擎喂入非法值引发 retry 风暴或直接走偏。solo（一条连接占两席位）与本方自己被
        // 询问均不受此限。
        //
        // 例外（防御性放行）：当待答席位正是本次要回退的那个锚点的应答方，且该锚点就是
        // 栈中最后一步（玩家就盯着自己那个行动询问按了撤回），重挂出去的询问与它手上那条逐字节相同，
        // 它的答复对新局面依旧合法有效，此时放行撤回不会造成错拍。
        UndoStep targetStep = undoSteps.get(target);
        boolean askIsRollbackTarget = target == undoSteps.size() - 1
                && room.lastResponse == targetStep.responder;
        ServerConnection asking = room.lastResponse >= 0 && room.lastResponse < room.players.length
                ? room.players[room.lastResponse] : null;
        if (asking != null && asking != requester && !askIsRollbackTarget
                && (asking.state == CTOS_RESPONSE || asking.state == CTOS_TIME_CONFIRM)) {
            Log.i(TAG, "undo 拒绝：对方席位 " + room.lastResponse + " 正在待应答，不得打断其询问");
            denyUndo(requester);
            return;
        }
        UndoStep step = targetStep;
        // 计时快照：回退到 step 应答之前的值；重建失败时恢复回现值
        int tl0 = room.timeLimit[0];
        int tl1 = room.timeLimit[1];
        int te = room.timeElapsed;
        ArrayList<UndoStep> before = new ArrayList<>(undoSteps);
        undoSteps.subList(target, undoSteps.size()).clear();
        int keep = undoSteps.size();
        replay.resetRecordStream();
        room.timeLimit[0] = step.timeLimit0;
        room.timeLimit[1] = step.timeLimit1;
        room.timeElapsed = step.timeElapsed;
        if (rebuildAndReplay(keep)) {
            Log.i(TAG, "undo 成功：回退 " + (before.size() - keep) + " 步（重放到第 " + keep
                    + " 步为止），回合="
                    + analyzer.silentTurnCount + " player=" + analyzer.silentTurnPlayer
                    + " phase=" + Integer.toHexString(analyzer.silentPhase));
            sendUndoAckToAll(UNDO_ACK_OK, analyzer.silentTurnCount,
                    analyzer.silentTurnPlayer, analyzer.silentPhase);
            analyzer.resyncAfterUndo(OcgDuelEngine.queryFieldInfo(pduel));
            scheduleUndoQuestionHang();
            sendUndoStateToAll();
            return;
        }
        // 回退重建失败（理论上不应发生）：把弹掉的步骤全部加回去，按撤回前的局面整体重建，局面不丢
        Log.e(TAG, "undo 回退重建失败，按撤回前局面重建");
        undoSteps.clear();
        undoSteps.addAll(before);
        replay.resetRecordStream();
        room.timeLimit[0] = tl0;
        room.timeLimit[1] = tl1;
        room.timeElapsed = te;
        if (rebuildAndReplay(undoSteps.size())) {
            sendUndoAckToAll(UNDO_ACK_REBUILT, analyzer.silentTurnCount,
                    analyzer.silentTurnPlayer, analyzer.silentPhase);
            analyzer.resyncAfterUndo(OcgDuelEngine.queryFieldInfo(pduel));
            scheduleUndoQuestionHang();
            sendUndoStateToAll();
        } else {
            // 两次重建均失败：引擎句柄已不可用，只能告知客户端撤回无效
            Log.e(TAG, "undo 兜底重建也失败：引擎重建异常，本局不可继续");
            denyUndo(requester);
        }
    }

    /**
     * 栈中最近一个动作锚点（不分席位）的下标，且必须由请求者席位宣言才返回，否则 -1；solo 下任何锚点都算本方。
     * 人机（WindBot）局的 AI 以独立连接入座（type=1），故按应答席位严格区分，
     * 不会把 AI 的召唤当成人类玩家自己的可撤回动作；反之，对方在我方锚点之后做出过新的动作宣言时，
     * 本方法不越过对方锚点去翻出我方更早的旧锚点——撤回窗口只认「最后一个动手的是不是我」。
     * 连锁中对方的应答不是锚点（anchorType=0），不影响本方「最后操作」的成立。
     */
    private int lastIndexOfOwnAnchor(ServerConnection requester) {
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
    private int lastIndexOfAnyAnchor() {
        for (int i = undoSteps.size() - 1; i >= 0; i--) {
            if (undoSteps.get(i).anchorType != 0) {
                return i;
            }
        }
        return -1;
    }

    /** 本连接当前是否有可撤回的动作（决定客户端撤回图标是否闪动发光提示）：仅当最近一个动作锚点由本方宣言。 */
    private boolean canUndoFor(ServerConnection conn) {
        if (conn == null || conn.type > 1) {
            return false;
        }
        return lastIndexOfOwnAnchor(conn) >= 0;
    }

    /**
     * 按连接身份下发 STOC_UNDO_STATE（solo 下一条连接占两席位，去重只发一份）。
     * 观战连接不显示撤回入口，故不下发。
     */
    private void sendUndoStateToAll() {
        ServerConnection p0 = room.players[0];
        ServerConnection p1 = room.players[1];
        if (p0 != null) {
            p0.send(STOC_UNDO_STATE, new byte[]{(byte) (canUndoFor(p0) ? 1 : 0)});
        }
        if (p1 != null && p1 != p0) {
            p1.send(STOC_UNDO_STATE, new byte[]{(byte) (canUndoFor(p1) ? 1 : 0)});
        }
    }

    /**
     * 按开局 seed 重建引擎，静默（只录不发）重放前 {@code keep} 条应答，回到“做过 keep 步”的局面。
     * 重放过程逐条重新写录像，与首跑序列一致（同 seed + 同应答的决定性引擎）。
     *
     * @return true = 重建并重放成功，且已回到“等待应答”状态
     *         （{@link DuelAnalyzer#silentTerminal} 已捕获待重发的询问消息）
     */
    private boolean rebuildAndReplay(int keep) {
        if (undoSeed == null || undoStartFrame == null || replay == null) {
            return false;
        }
        OcgDuelEngine.endDuel(pduel);
        pduel = 0L;
        long fresh = OcgDuelEngine.createDuelV2(undoSeed);
        if (fresh == 0L) {
            return false;
        }
        pduel = fresh;
        GameRoom.HostInfo hi = room.hostInfo;
        analyzer.beginSilentReplay();
        boolean ok;
        try {
            OcgDuelEngine.setPlayerInfo(pduel, 0, hi.startLp, hi.startHand, hi.drawCount);
            OcgDuelEngine.setPlayerInfo(pduel, 1, hi.startLp, hi.startHand, hi.drawCount);
            // 卡组装载：录像 base 段（双方卡组名与卡码）已在首次开局写好，只喂引擎不再写
            baseRecordFrozen = true;
            try {
                if (room.tagMode) {
                    // 装载顺序与 {@link #startDuel} 严格一致（洗牌后的 room.decks 序未变）
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
            } finally {
                baseRecordFrozen = false;
            }
            // 消息流：开局的 MSG_START 帧与两次 extra 刷新在原录像中存在，清流后需按原序补回
            replay.writeMessage(undoStartFrame, undoStartFrame.length);
            analyzer.refreshExtra(0);
            analyzer.refreshExtra(1);
            OcgDuelEngine.startDuel(pduel, undoOpt);
            ok = processSilent();
            for (int i = 0; ok && i < keep; i++) {
                UndoStep s = undoSteps.get(i);
                replay.writeResponse(s.resb, s.len);
                OcgDuelEngine.setResponseB(pduel, s.resb);
                ok = processSilent();
            }
            // 回退点必须是“引擎在等应答”，否则无询问可重挂，客户端会挂在无操作局面
            ok = ok && analyzer.silentTerminal != null;
        } finally {
            analyzer.endSilentReplay();
        }
        return ok;
    }

    /**
     * 静默版 {@link #process()}：取消息循环完全同构，但跑到终局（MSG_WIN，回退点不应在终局之后）
     * 则返回 false 交调用方判定重建失败。调用前必须已处于静默态。
     */
    private boolean processSilent() {
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
        return stop != 2;
    }

    /** MSG_RETRY：本次应答未被引擎接受（重问），它不构成可撤回的一步，弹出快照。 */
    void discardPendingUndoStep() {
        // 静默重放（撤回重建）中的 RETRY 不得动快照栈：那一步早已定格，弹了会错位
        if (analyzer.silent) {
            return;
        }
        int n = undoSteps.size();
        if (n > 0) {
            UndoStep popped = undoSteps.remove(n - 1);
            // 重问的仍是同一个询问：它若是行动宣言（IDLE/BATTLE CMD），锚点属性须随被弹掉的
            // 那一步一并回填，否则玩家改口后的正确应答会被记成普通步骤而撤不回
            analyzer.pendingActionQuestion = popped.anchorType;
        }
    }

    private void denyUndo(ServerConnection requester) {
        if (requester != null) {
            requester.send(STOC_UNDO_ACK, undoAckPayload(UNDO_ACK_DENIED, 0, 0, 0));
        }
    }

    /** 把撤回结果推给全体在场连接（solo 下一个连接占两席位，按连接身份去重）。 */
    private void sendUndoAckToAll(int result, int turn, int player, int phase) {
        byte[] ack = undoAckPayload(result, turn, player, phase);
        ServerConnection p0 = room.players[0];
        ServerConnection p1 = room.players[1];
        if (p0 != null) {
            p0.send(STOC_UNDO_ACK, ack);
        }
        if (p1 != null && p1 != p0) {
            p1.send(STOC_UNDO_ACK, ack);
        }
        for (ServerConnection o : room.observers) {
            o.send(STOC_UNDO_ACK, ack);
        }
    }

    /** STOC_UNDO_ACK 载荷：{@code [result(1B)][turn(1B)][currentPlayer(1B)][phase(u16 LE)]}。 */
    private static byte[] undoAckPayload(int result, int turn, int player, int phase) {
        ByteBuffer b = ByteBuffer.allocate(5).order(ByteOrder.LITTLE_ENDIAN);
        b.put((byte) result);
        b.put((byte) turn);
        b.put((byte) player);
        b.putShort((short) phase);
        return b.array();
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

    // === 撤回后的询问延后重挂 ===

    /** 询问重挂前的留白：足够覆盖一次局域网往返，使被丢弃那段的在路上的应答先落地并被状态门吞掉。 */
    private static final long UNDO_HANG_DELAY_MS = 400L;
    /** 保活心跳周期。 */
    private static final long QUESTION_KEEPALIVE_TICK_MS = 1000L;
    /**
     * 重挂后多久没有任何答复即视为落空。取得比“人看一眼牌再点”略长，避免把正常询问重发一遍；
     * 它只对撤回重挂的那条询问生效，所以不会干扰其它对局节奏。
     */
    private static final long QUESTION_KEEPALIVE_GRACE_MS = 3000L;
    /** 同一条询问最多重发几份（重发后仍无答复则交给日志与下一轮玩家操作，不再刷盘）。 */
    private static final int QUESTION_KEEPALIVE_MAX_REDELIVERIES = 3;
    /**
     * 客户端主动索取重发（{@link #onAskResendRequest}）时的份数上限：取得比保活心跳略高，
     * 因为它是「客户端确实已经答完并且什么也没等到」的直接证据，而非心跳的超时推测。
     */
    private static final int ASK_RESEND_MAX_REDELIVERIES = 5;
    /** 撤回重挂询问用的单次定时器（限时为 0 时 {@link #timer} 不存在，不能复用）。 */
    private ScheduledExecutorService undoHangTimer;
    /** 撤回重挂询问的保活任务（与重挂共用同一个单次定时器，随撤回代次作废）。 */
    private ScheduledFuture<?> questionKeepAliveTask;
    /** 重挂代次：连续多次撤回时旧任务自动作废，只会挂出最后一次撤回的询问。 */
    private volatile int undoHangGen;

    /**
     * 延后把回退点的待应答询问挂回席位（重载与刷新已在 {@link DuelAnalyzer#resyncAfterUndo}
     * 里发完，两席位状态也已复位）。不立刻重挂的理由：
     * <ul>
     *   <li>被丢弃那一段里可能已有应答在路上——人机局的 AI 作为独立进程，它针对旧连锁询问算完的
     *       答复不受本机应答屏障约束，晚到一步就会被当成对新询问的应答喂进引擎；</li>
     *   <li>客户端刚收到全量重载，旧弹窗的关闭与自动应答的取消都在主线程队列上排队，先挂询问
     *       会让这些残留在新询问到达前后抢发出去。</li>
     * </ul>
     * 留白期间两席位都是 STATE_NONE，任何应答都被状态门丢弃；留白结束后才挂出询问，
     * 此时界面上只可能存在本次新局面派下来的弹窗与命令列表，应答链路重新干净。
     * 重挂任务回投到单线程房间执行器上，与应答/撤回串行；代次比较使连续撤回时旧任务作废。
     */
    private void scheduleUndoQuestionHang() {
        if (analyzer.undoAskResponder < 0) {
            return;
        }
        final int gen = ++undoHangGen;
        ScheduledExecutorService exec = undoHangTimer;
        if (exec == null || exec.isShutdown()) {
            exec = Executors.newSingleThreadScheduledExecutor();
            undoHangTimer = exec;
        }
        exec.schedule(() -> room.server.post(() -> {
            if (gen != undoHangGen || pduel == 0L || room.duelStage != DUEL_STAGE_DUELING) {
                return;
            }
            analyzer.hangUndoTerminalQuestion();
        }), UNDO_HANG_DELAY_MS, TimeUnit.MILLISECONDS);
        // 重挂之后开一路保活：延后重挂跑在另一个线程上（回投房间执行器），它可能因执行器已关、
        // 代次作废、或客户端把询问包吞在动画闸门里而落空；落空就是永久卡死（界面无询问、点卡弹不
        // 出发动/召唤/盖放菜单，但墓地/除外区这类本地列表照旧可看）
        startUndoQuestionKeepAlive(exec, gen);
    }

    /**
     * 撤回重挂询问的保活：每秒复查一次，仅对「撤回重挂出去且仍无人应答」那一条询问生效，
     * 超过 {@link #QUESTION_KEEPALIVE_GRACE_MS} 即原样重发（最多
     * {@link #QUESTION_KEEPALIVE_MAX_REDELIVERIES} 次）。留白期（{@code undoAskResponder} 仍待重挂）
     * 跳过，以免把属于被撤销那一段的旧询问抢先挂回。
     */
    private void startUndoQuestionKeepAlive(ScheduledExecutorService exec, final int gen) {
        if (questionKeepAliveTask != null) {
            questionKeepAliveTask.cancel(false);
        }
        questionKeepAliveTask = exec.scheduleWithFixedDelay(() -> room.server.post(() -> {
            if (gen != undoHangGen || pduel == 0L || room.duelStage != DUEL_STAGE_DUELING) {
                return;
            }
            if (analyzer.undoAskResponder >= 0) {
                return;
            }
            analyzer.beatPendingQuestion(System.currentTimeMillis(),
                    QUESTION_KEEPALIVE_GRACE_MS, QUESTION_KEEPALIVE_MAX_REDELIVERIES);
        }), UNDO_HANG_DELAY_MS + QUESTION_KEEPALIVE_TICK_MS,
                QUESTION_KEEPALIVE_TICK_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * 待答席位的应答被状态门吞掉时由 {@link GameRoom#handlePacket} 回调（自愈入口）。
     *
     * <p>这是“撤回后再发动一次就推不下去”的真正形状：客户端认为自己已经把那个询问答了（弹窗已收、
     * 命令列表已清），而服务端引擎那一问并未答掉——于是界面不再挂任何询问，点卡弹不出发动/召唤/盖放，
     * 而墓地/除外/额外属于本地列表所以照旧能看，双方永久互等。把当前询问连等待态一并重挂即可恢复；
     * 本方法不改写引擎、不写录像，只重发一份询问字节。
     */
    void onResponseDropped(ServerConnection conn) {
        if (pduel == 0L || room.duelStage != DUEL_STAGE_DUELING
                || conn == null || conn.type < 0 || conn.type > 1) {
            return;
        }
        // 撤回的延后重挂仍在路上：此刻被吞的应答本就由那次重挂接管，不可拿撤回前的旧询问抢先挂回
        if (analyzer.undoAskResponder >= 0) {
            return;
        }
        // 只修“引擎此刻等的正是这个席位”：其余情形的被吞应答属于已被回退的那一段，本就应当丢弃
        if (room.lastResponse != conn.type) {
            return;
        }
        if (!analyzer.canRedeliverQuestion(QUESTION_KEEPALIVE_MAX_REDELIVERIES)) {
            return;
        }
        Log.w(TAG, "awaited seat " + conn.type
                + " response swallowed by state gate: re-hang its question");
        analyzer.rehangPendingQuestion("dropped response");
    }

    /**
     * CTOS_ASK_RESEND：客户端索取「引擎此刻挂在我这个席位上的那条询问」的重发
     * （本工程扩展，仅在服务端声明 {@code HOST_CAP_ASK_RESEND} 时客户端才会发）。
     *
     * <p>它补掉了保活心跳与状态门自愈都管不到的那一类：询问确实被服务端挂出、席位状态也正常，
     * 但那一条报文没能到达客户端（或到了客户端被吞），于是客户端界面手上没有询问、服务端引擎
     * 在等一个永远不会来的答复——双方永久互等，症状是点卡片弹不出发动/召唤/盖放菜单，而墓地、
     * 除外区、额外卡组这类本地列表照旧可看。心跳只管撤回重挂那一条询问（怕打扰长考的玩家），
     * 而客户端自己知道「我刚答完、之后一个字也没收到」，由它来触发这一步既精确又不会误伤正常节奏。
     *
     * <p>与应答、撤回同在这条单线程房间执行器上串行，故不会与 {@code getResponse} 抢引擎；
     * 询问是否仍是「当前这一条」由 {@link DuelAnalyzer#hasLiveAsk()} 的代次配对把关，过期的旧询问
     * 绝不会被挂回。
     */
    void onAskResendRequest(ServerConnection conn) {
        if (pduel == 0L || room.duelStage != DUEL_STAGE_DUELING) {
            return;
        }
        // solo 一条连接占两席位，引擎等待方按 room.lastResponse 计（与 getResponse 同一套换算）
        int responder = room.soloMode ? room.lastResponse : (conn == null ? -1 : conn.type);
        if (conn == null || conn.type < 0 || conn.type > 1 || responder != room.lastResponse) {
            Log.i(TAG, "ask-resend from seat " + (conn == null ? -1 : conn.type)
                    + " ignored: engine is waiting on " + room.lastResponse);
            return;
        }
        if (analyzer.undoAskResponder >= 0) {
            // 撤回的延后重挂已在路上，那次重挂本就负责把询问挂出来
            return;
        }
        if (!analyzer.canRedeliverQuestion(ASK_RESEND_MAX_REDELIVERIES)) {
            Log.w(TAG, "ask-resend denied: no live question cached for seat " + responder
                    + " (or redelivery limit reached)");
            return;
        }
        analyzer.rehangPendingQuestion("client ask-resend");
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
