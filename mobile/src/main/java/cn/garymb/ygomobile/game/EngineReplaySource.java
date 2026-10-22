package cn.garymb.ygomobile.game;

import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.engine.NativeScriptBootstrap;
import cn.garymb.ygomobile.engine.OcgDuelEngine;

/**
 * 旧格式来源：录像文件里只有玩家响应记录、没有引擎消息流，于是按 gframe
 * {@code replay_mode.cpp::ReplayThread} 的口径重跑一次决斗——用录像头的 seed/卡组/参数
 * 重建 {@code pduel}，由 ocgcore 以相同随机序列重新产生消息流，遇到 SELECT 类消息时把
 * 录制的响应 {@code set_responseb} 喂回去，使重跑过程与原对局逐步同步。
 *
 * <p>与旧实现的本质差别：重跑出来的消息<b>不再由回放自己的渲染分支处理</b>，而是与纯消息
 * 来源一样交给 {@link ReplayPlayer} 投喂 GameEngine 实况管线，因此回放（含旧格式录像）的
 * 动画队列、音效、大图、LP 浮字与联机/观战完全同路。
 *
 * <p>卡组仍以<b>原始卡码</b>喂引擎（seed 复现依赖原码，先行号录像亦然），显示侧的号段归一
 * 由 {@link ReplayPlayer} 在回填卡面与投喂消息时完成，两条路径互不污染。
 *
 * <p>ocgcore 原始消息流几乎不产 MSG_UPDATE_DATA（实况里由本仓库服务器 DuelAnalyzer 合成），
 * 而 gframe 重跑时在 ReplayAnalyze 各 case 后主动 query_field_card/query_card 合成卡片数据
 * （replay_mode.cpp::ReplayRefresh 家族），否则卡面等级/攻守/link/灵摆刻度文本全空。
 * 本实现在同样的触发消息点把查询结果包装成合成 UPDATE_DATA/UPDATE_CARD 消息入队，
 * 紧随触发消息投喂实况管线（卡码在源头就地归一）。
 */
final class EngineReplaySource extends ReplaySource {

    private static final String TAG = "EngineReplaySrc";

    /** set_responseb 要求引擎可读的完整响应缓冲（SIZE_RETURN_VALUE=256，同 ServerDuel） */
    private static final int RESPONSE_BUF_LEN = 256;

    /**
     * 转码采集预算：防异常会话（引擎空转等）导致 capturedFrames 无限增长 OOM
     *（曾因 MSG_RETRY 后引擎持续吐消息、pump 无节奏空转采到千万条帧而 OOM）；
     * 超预算即停采并放弃本会话转码，播放本身不受影响。
     */
    private static final int CAPTURE_FRAME_LIMIT = 200000;
    private static final long CAPTURE_BYTE_LIMIT = 32L * 1024 * 1024;

    // ==== 需区域刷新的消息号（replay_mode.cpp::ReplayAnalyze 各 case 的 ReplayRefresh 调用点） ====
    private static final int MSG_RETRY = 1, MSG_WAITING = 3, MSG_SELECT_BATTLECMD = 10,
            MSG_SELECT_IDLECMD = 11,
            MSG_SHUFFLE_DECK = 32, MSG_SWAP_GRAVE_DECK = 35, MSG_REVERSE_DECK = 37,
            MSG_NEW_PHASE = 41, MSG_MOVE = 50, MSG_SUMMONED = 61, MSG_SPSUMMONED = 63,
            MSG_FLIPSUMMONED = 65, MSG_CHAINED = 71, MSG_CHAIN_SOLVED = 73, MSG_CHAIN_END = 74,
            MSG_DAMAGE_STEP_START = 113, MSG_DAMAGE_STEP_END = 114,
            MSG_TAG_SWAP = 161, MSG_RELOAD_FIELD = 162;

    // ==== 查询掩码（replay_mode.h 默认参 / replay_mode.cpp::ReplayReload） ====
    private static final int FLAG_FIELD = 0xF81FFF;    // ReplayRefresh / ReplayRefreshSingle
    private static final int FLAG_POOL = 0x181FFF;     // ReplayRefreshDeck/Extra/Grave
    private static final int FLAG_RELOAD = 0xFFDFFF;   // ReplayReload
    private static final int LOCATION_OVERLAY = 0x10;

    /** 合成刷新消息的块数无需按实况区域卡数推断：自界长块走到消息体末尾即全部 */
    private static final ReplayCodeMapper.BlockCountProvider UNBOUNDED =
            (player, location) -> Integer.MAX_VALUE;

    private long pduel;
    /** 响应记录流游标（uniform 模式消息区全部为 [uint8 len][response] 记录） */
    private ByteBuffer responses;
    /** 响应记录区快照，rewind（undo/restart 重跑）时回到起点 */
    private byte[] originalResponses;
    /**
     * 停止重跑（refill 不再推进引擎）。两类终止共用此标志，语义差异只在是否置 lastError：
     * 响应记录耗尽/停滞均不置错→与 C++ ReadReplayResponse 失败同样落入
     * 正常收尾（FINISHED→弹「录像播放结束」）；MSG_RETRY（同一询问被拒后重新等待）
     * 喂回下一条响应记录继续播放，不走此终止路径。
     */
    private boolean fatal;
    /**
     * 转码固化采集：重跑产生的全部消息帧（[消息号][原始卡码体]，含合成刷新帧），
     * 均在卡码归一（ReplayCodeMapper）之前抓取副本，保证写回文件的流与 ocgcore 原输出同构；
     * startDuel（含 rewind 重建决斗）时清空重采，播放成功后由 ReplayPlayer 转写为 V2 消息流录像。
     */
    private final List<byte[]> capturedFrames = new ArrayList<>();
    /** 已采集帧字节总数（含消息号）与预算熔断标志（见 CAPTURE_*） */
    private long capturedBytes;
    private boolean captureAbandoned;

    EngineReplaySource(ReplayPlayer player) {
        super(player);
    }

    @Override
    boolean engineDriven() {
        return true;
    }

    @Override
    boolean open() {
        data = player.getReplayData();
        if (data == null) {
            setLastError("录像数据为空");
            return false;
        }
        if (!NativeScriptBootstrap.ensureEngineReady()) {
            setLastError("决斗引擎或卡片脚本加载失败");
            return false;
        }
        ByteBuffer rp = data.replayBuffer;
        if (rp != null) {
            rp.order(ByteOrder.LITTLE_ENDIAN);
            originalResponses = new byte[rp.remaining()];
            ((ByteBuffer) rp.duplicate().order(ByteOrder.LITTLE_ENDIAN)).get(originalResponses);
        }
        attach((ByteBuffer) null);      // 消息缓冲全部由 refill() 从引擎现取
        return startDuel();
    }

    /** 重建决斗（对齐 ReplayMode::StartDuel）：seed/卡组/参数取录像头，本身不产消息 */
    private boolean startDuel() {
        fatal = false;
        synthQueue.clear();
        capturedFrames.clear();
        capturedBytes = 0;
        captureAbandoned = false;
        responses = originalResponses == null ? null
                : ByteBuffer.wrap(originalResponses).order(ByteOrder.LITTLE_ENDIAN);
        ReplayReader.ExtendedReplayHeader eh = data.header;
        pduel = eh.base.id == ReplayReader.REPLAY_ID_YRP2
                ? OcgDuelEngine.createDuelV2(eh.seedSequence)
                : OcgDuelEngine.createDuel(mt19937FirstOutput(eh.base.seed));   // YRP1：C++ 用 mt19937(seed) 首输出作 seed（replay_mode.cpp L169-171）
        if (pduel == 0L) {
            setLastError("创建决斗失败（引擎未就绪或 seed 无效）");
            return false;
        }
        OcgDuelEngine.setPlayerInfo(pduel, 0, data.params.startLp,
                data.params.startHand, data.params.drawCount);
        OcgDuelEngine.setPlayerInfo(pduel, 1, data.params.startLp,
                data.params.startHand, data.params.drawCount);
        if (data.isSingleMode) {
            // replay_mode.cpp L213-219：残局回放预载 ./single/xxx.lua
            if (OcgDuelEngine.preloadScript(pduel, "./single/" + data.scriptName) == 0) {
                endDuel();
                setLastError("残局脚本加载失败：" + data.scriptName);
                return false;
            }
        } else if (data.isTag && data.decks.size() >= 4) {
            // L193-211：0/2 为主将 new_card，1/3 为替补 new_tag_card
            loadDeckToEngine(data.decks.get(0), 0, false);
            loadDeckToEngine(data.decks.get(1), 0, true);
            loadDeckToEngine(data.decks.get(2), 1, false);
            loadDeckToEngine(data.decks.get(3), 1, true);
        } else {
            for (int i = 0; i < data.decks.size() && i < 2; i++) {
                loadDeckToEngine(data.decks.get(i), i, false);
            }
        }
        OcgDuelEngine.startDuel(pduel, data.params.duelFlag);
        if (!data.isSingleMode) {
            // replay_mode.cpp L88-91：重跑启动先查双方卡组/额外卡面（旧格式看卡组与卡面数据底座）
            refreshZone(0, OcgDuelEngine.LOCATION_DECK, FLAG_POOL);
            refreshZone(1, OcgDuelEngine.LOCATION_DECK, FLAG_POOL);
            refreshZone(0, OcgDuelEngine.LOCATION_EXTRA, FLAG_POOL);
            refreshZone(1, OcgDuelEngine.LOCATION_EXTRA, FLAG_POOL);
        }
        return true;
    }

    /**
     * std::mt19937 首输出（init_genrand + 一轮 twist + temper）：YRP1 录像的 create_duel
     * 种子必须是 mt19937(录像 seed) 的第一个输出（replay_mode.cpp L169-171），直传原 seed
     * 会让洗牌/掷骰全偏离 → 响应记录失步 → 「MSG_RETRY / 响应提前耗尽」。
     */
    static int mt19937FirstOutput(int seed) {
        final long mask = 0xFFFFFFFFL, factor = 1812433253L, matrixA = 0x9908B0DFL;
        int[] mt = new int[624];
        mt[0] = seed;
        for (int i = 1; i < 624; i++) {
            long prev = mt[i - 1] & mask;
            mt[i] = (int) ((factor * (prev ^ (prev >>> 30)) + i) & mask);
        }
        for (int i = 0; i < 624; i++) {
            long y = ((long) mt[i] & 0x80000000L) | ((long) mt[(i + 1) % 624] & 0x7FFFFFFFL);
            long v = (mt[(i + 397) % 624] & mask) ^ (y >>> 1);
            if ((y & 1) != 0) v ^= matrixA;
            mt[i] = (int) v;
        }
        long y = mt[0] & mask;
        y ^= y >>> 11;
        y = (y ^ ((y << 7) & 0x9D2C5680L)) & mask;
        y = (y ^ ((y << 15) & 0xEFC60000L)) & mask;
        y ^= y >>> 18;
        return (int) y;
    }

    private void loadDeckToEngine(ReplayReader.DeckInfo deck, int player, boolean tagMate) {
        if (deck == null) return;
        if (tagMate) {
            for (int code : deck.main) {
                OcgDuelEngine.newTagCard(pduel, code, player, OcgDuelEngine.LOCATION_DECK);
            }
            for (int code : deck.extra) {
                OcgDuelEngine.newTagCard(pduel, code, player, OcgDuelEngine.LOCATION_EXTRA);
            }
            return;
        }
        for (int code : deck.main) {
            OcgDuelEngine.newCard(pduel, code, player, player, OcgDuelEngine.LOCATION_DECK, 0,
                    OcgDuelEngine.POS_FACEDOWN_DEFENSE);
        }
        for (int code : deck.extra) {
            OcgDuelEngine.newCard(pduel, code, player, player, OcgDuelEngine.LOCATION_EXTRA, 0,
                    OcgDuelEngine.POS_FACEDOWN_DEFENSE);
        }
    }

    /**
     * 引擎推进到下一段消息缓冲（对齐 ReplayThread 的 process + get_message 循环）。
     * 一次 process 的输出可含多条消息，由基类游标逐条切出；缓冲耗尽才再次 process，
     * 保证 getMessage 取到的缓冲在被读完前不会被引擎覆盖。
     */
    @Override
    boolean refill() {
        if (pduel == 0L || fatal) return false;
        int idleSpins = 0;
        while (player.isPumpAlive() && !fatal) {
            int result = OcgDuelEngine.process(pduel);
            int len = result & OcgDuelEngine.PROCESSOR_BUFFER_LEN;
            int flag = result & OcgDuelEngine.PROCESSOR_FLAG;
            if (len > 0) {
                byte[] msg = OcgDuelEngine.getMessage(pduel);
                if (msg != null && msg.length > 0) {
                    attach(msg);
                    return true;
                }
            }
            if (flag == OcgDuelEngine.PROCESSOR_END) {
                return false;   // 决斗自然结束（MSG_WIN 已在消息流里，投喂循环另行收尾）
            }
            if (len == 0 && ++idleSpins > 2000) {
                // 引擎既不产消息也不结束：投降等中断录制的响应记录已到头而引擎仍在等待，
                // 对齐 C++ 主循环终止口径静默收尾（不置 lastError，弹正常结束 dialog）
                Log.i(TAG, "rerun stalled at step=" + player.getCurrentStep()
                        + ", end silently as normal finish");
                fatal = true;
                return false;
            }
            if (len == 0) {
                try {
                    Thread.sleep(2L);
                } catch (InterruptedException e) {
                    return false;
                }
            }
        }
        return false;
    }

    /**
     * SELECT/询问类消息切完后喂回录制响应（对齐 ReadReplayResponse）；MSG_RETRY（同一
     * 询问被拒后的重新等待）喂回下一条响应记录继续重跑，不中断回放。其余按
     * ReplayAnalyze 各 case 的 ReplayRefresh 调用点合成区域刷新消息。
     */
    @Override
    protected void onSliced(int msgType, byte[] body) {
        captureFrame(msgType, body);       // 先于任何卡码映射/响应喂回：采集引擎原码帧
        switch (msgType) {
            case MSG_RETRY:
                // 「要求重新选择」：ocgcore playerop.cpp 各校验分支拒绝应答后仅写 MSG_RETRY
                // 并 return FALSE——不会重发 SELECT 询问（那条只在 step=0 发），而是对同一
                // 询问继续等新应答（handler 以 step=1 复跑校验）。原对局里客户端收到 RETRY
                // 后再发一次应答，且每次已发应答都被录进响应流（一条记录=一次真实应答），
                // 故正确接续就是立即喂回下一条响应记录；若仍被拒会再来一条 RETRY 再消耗
                // 一条记录，消耗量以记录数为界；记录耗尽时 feedRecordedResponse 静默收尾，
                // 不弹异常也不会空转卡死
                Log.i(TAG, "MSG_RETRY at step=" + player.getCurrentStep()
                        + ", feeding next recorded response for the pending query");
                feedRecordedResponse();
                return;
            case MSG_SELECT_BATTLECMD:
            case MSG_SELECT_IDLECMD:
                refreshFieldZones();      // C++：ReplayRefresh() 后 ReadReplayResponse()
                feedRecordedResponse();
                return;
            case MSG_SHUFFLE_DECK:
                refreshZone(playerAt(body), OcgDuelEngine.LOCATION_DECK, FLAG_POOL);
                return;
            case MSG_SWAP_GRAVE_DECK:
                refreshZone(playerAt(body), OcgDuelEngine.LOCATION_GRAVE, FLAG_POOL);
                return;
            case MSG_REVERSE_DECK:
                refreshZone(0, OcgDuelEngine.LOCATION_DECK, FLAG_POOL);
                refreshZone(1, OcgDuelEngine.LOCATION_DECK, FLAG_POOL);
                return;
            case MSG_NEW_PHASE:
            case MSG_SUMMONED:
            case MSG_SPSUMMONED:
            case MSG_FLIPSUMMONED:
            case MSG_CHAINED:
            case MSG_CHAIN_SOLVED:
            case MSG_CHAIN_END:
            case MSG_DAMAGE_STEP_START:
            case MSG_DAMAGE_STEP_END:
                refreshFieldZones();
                return;
            case MSG_MOVE:
                moveRefresh(body);
                return;
            case MSG_TAG_SWAP: {
                int p = playerAt(body);
                refreshZone(p, OcgDuelEngine.LOCATION_DECK, FLAG_POOL);
                refreshZone(p, OcgDuelEngine.LOCATION_EXTRA, FLAG_POOL);
                return;
            }
            case MSG_RELOAD_FIELD:
                refreshReloadAll();
                return;
            default:
                if (ReplayMessageSlicer.isSelectable(msgType)) {
                    feedRecordedResponse();
                }
        }
    }

    private static int playerAt(byte[] body) {
        return body == null || body.length < 1 ? 0 : body[0] & 0xFF;
    }

    /**
     * MSG_MOVE 刷新（replay_mode.cpp case MSG_MOVE）：落位非覆盖位且非原位同名移动时刷单卡，
     * 卡组内移动（pl==cl==DECK）刷整卡组。body：code(0-3) pc(4) pl(5) ps(6) pp(7) cc(8) cl(9) cs(10)。
     */
    private void moveRefresh(byte[] body) {
        if (body == null || body.length < 16) return;
        int pc = body[4] & 0xFF, pl = body[5] & 0xFF;
        int cc = body[8] & 0xFF, cl = body[9] & 0xFF, cs = body[10] & 0xFF;
        if (cl != 0 && (cl & LOCATION_OVERLAY) == 0 && (pl != cl || pc != cc)) {
            refreshSingle(cc, cl, cs);
        } else if (pl == cl && cl == OcgDuelEngine.LOCATION_DECK) {
            refreshZone(cc, OcgDuelEngine.LOCATION_DECK, FLAG_POOL);
        }
    }

    /** ReplayRefresh() 默认集：双方 怪兽区/魔法区/手牌（flag=0xf81fff） */
    private void refreshFieldZones() {
        int[] locs = {OcgDuelEngine.LOCATION_MZONE, OcgDuelEngine.LOCATION_SZONE,
                OcgDuelEngine.LOCATION_HAND};
        for (int loc : locs) {
            refreshZone(0, loc, FLAG_FIELD);
            refreshZone(1, loc, FLAG_FIELD);
        }
    }

    /** ReplayReload()：全部 7 区域×双方（flag=0xffdfff） */
    private void refreshReloadAll() {
        int[] locs = {OcgDuelEngine.LOCATION_MZONE, OcgDuelEngine.LOCATION_SZONE,
                OcgDuelEngine.LOCATION_HAND, OcgDuelEngine.LOCATION_DECK,
                OcgDuelEngine.LOCATION_EXTRA, OcgDuelEngine.LOCATION_GRAVE,
                OcgDuelEngine.LOCATION_REMOVED};
        for (int loc : locs) {
            refreshZone(0, loc, FLAG_RELOAD);
            refreshZone(1, loc, FLAG_RELOAD);
        }
    }

    /**
     * ReloadLocation 等价：query_field_card 的区域块序列直接拼成合成 UPDATE_DATA 消息体
     * （[player][location][块序列]，与服务器 WriteUpdateData 下发格式同构），卡码就地归一后
     * 入队，紧随触发消息投喂实况管线（管线侧经 parseUpdateData→updateQuery 生成卡面数值文本）。
     */
    private void refreshZone(int enginePlayer, int location, int flag) {
        if (pduel == 0L) return;
        byte[] blocks = OcgDuelEngine.queryFieldCard(pduel, enginePlayer, location, flag, 0);
        if (blocks.length == 0) return;
        byte[] body = new byte[blocks.length + 2];
        body[0] = (byte) enginePlayer;
        body[1] = (byte) location;
        System.arraycopy(blocks, 0, body, 2, blocks.length);
        captureFrame(6, body);             // 采集原码副本（下方 mapMessage 会就地改写 body）
        ReplayCodeMapper.mapMessage(6, body, UNBOUNDED);
        enqueueSynthetic(new Msg(6, body, true));
    }

    /** ReplayRefreshSingle 等价：query_card 单块包成合成 UPDATE_CARD（[p][loc][seq][块]） */
    private void refreshSingle(int enginePlayer, int location, int sequence) {
        if (pduel == 0L) return;
        byte[] block = OcgDuelEngine.queryCard(pduel, enginePlayer, location, sequence, FLAG_FIELD, 0);
        if (block.length == 0) return;
        byte[] body = new byte[block.length + 3];
        body[0] = (byte) enginePlayer;
        body[1] = (byte) location;
        body[2] = (byte) sequence;
        System.arraycopy(block, 0, body, 3, block.length);
        captureFrame(7, body);             // 采集原码副本（下方 mapMessage 会就地改写 body）
        ReplayCodeMapper.mapMessage(7, body, UNBOUNDED);
        enqueueSynthetic(new Msg(7, body, true));
    }

    /** 从响应记录流读一条 [uint8 len][data] 并 set_responseb */
    private void feedRecordedResponse() {
        ByteBuffer rp = responses;
        if (rp == null || pduel == 0L || !rp.hasRemaining()) {
            // 响应记录耗尽（投降/中断等提前终止的对局，录制者后续操作不再产生响应记录，
            // 重跑引擎却仍会走到 SELECT 询问）：对齐 replay_mode.cpp::ReadReplayResponse
            // 返回 false → ReplayAnalyze false → 主循环退出 → EndDuel 弹 sysString 1501
            // 「录像播放结束」——属正常收尾，不置 lastError（ERROR 只留给真错误）
            Log.i(TAG, "recorded responses exhausted at step=" + player.getCurrentStep()
                    + ", treat as normal end (align C++ EndDuel)");
            fatal = true;
            return;
        }
        int len = rp.get() & 0xFF;
        if (len > rp.remaining()) len = rp.remaining();
        byte[] resb = new byte[RESPONSE_BUF_LEN];
        rp.get(resb, 0, len);
        OcgDuelEngine.setResponseB(pduel, resb);
    }

    /** 追加一条转码帧：[消息号][体副本]，与 LAN 录制 YrpWriter.writeMessage 的单帧一消息同构 */
    private void captureFrame(int msgType, byte[] body) {
        // 无实质内容、可被引擎无限重发的消息不进转码流（既无画面意义，也是空转时
        // 采集暴涨的直接来源；纯消息流回放侧对它们本来就 isNoFeed 丢弃）
        if (msgType == MSG_RETRY || msgType == MSG_WAITING) return;
        if (captureAbandoned) return;
        if (capturedFrames.size() >= CAPTURE_FRAME_LIMIT
                || capturedBytes + body.length + 1 > CAPTURE_BYTE_LIMIT) {
            Log.w(TAG, "transcode capture budget exceeded, transcode disabled for this session");
            captureAbandoned = true;
            capturedFrames.clear();
            capturedBytes = 0;
            return;
        }
        byte[] frame = new byte[body.length + 1];
        frame[0] = (byte) msgType;
        System.arraycopy(body, 0, frame, 1, body.length);
        capturedFrames.add(frame);
        capturedBytes += frame.length;
    }

    @Override
    java.util.List<byte[]> capturedEngineFrames() {
        return capturedFrames;
    }

    /** 上一步/从头重放：结束当前决斗、响应记录回到起点、以同一 seed 重建决斗（对齐 ReplayMode::Restart） */
    @Override
    void rewind() {
        endDuel();
        attach((ByteBuffer) null);
        synthQueue.clear();
        if (!startDuel()) {
            Log.w(TAG, "rewind: restart duel failed - " + getLastError());
        }
    }

    @Override
    void close() {
        endDuel();
        responses = null;
        originalResponses = null;
        capturedFrames.clear();
        capturedBytes = 0;
        super.close();
    }

    private void endDuel() {
        if (pduel != 0L) {
            OcgDuelEngine.endDuel(pduel);
            pduel = 0L;
        }
    }
}
