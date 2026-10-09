package cn.garymb.ygomobile.game;

import android.util.Log;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Random;

import cn.garymb.ygomobile.audio.SoundManager;
import cn.garymb.ygomobile.engine.NativeScriptBootstrap;
import cn.garymb.ygomobile.engine.OcgDuelEngine;
import cn.garymb.ygomobile.network.YGOProtocol;
import cn.garymb.ygomobile.utils.CrashHandler;

/**
 * 残局（single mode）播放器：直驱本地 {@link OcgDuelEngine} 跑残局 lua，并把引擎产出的
 * 消息流投喂 {@link GameEngine} 的实况管线，使解残局与联机/观战/回放共用同一套渲染、动画、
 * 音效与选择弹窗链路。严格对齐 {@code Classes/gframe/single_mode.cpp}（SinglePlayThread /
 * SinglePlayAnalyze / SinglePlayRefresh 家族）。
 *
 * <p>与 C++ 的关键一致点：
 * <ul>
 *   <li>残局 lua 在 {@code preload_script} 时<b>立即执行</b>（Debug.SetAIName / ShowHint /
 *       ReloadFieldBegin / AddCard / SetPlayerInfo / ReloadFieldEnd），其输出缓冲
 *       （MSG_AI_NAME / MSG_SHOW_HINT / MSG_RELOAD_FIELD）在 {@code start_duel} <b>之前</b>
 *       先 {@code get_message} 取出并分析一次；</li>
 *   <li>{@code start_duel} 的 opt 仅 {@code DUEL_RETURN_DECK_TOP}（0x80），决斗规则由脚本
 *       自带的 rule=5 决定（opt&gt;&gt;16==0 不覆盖）；</li>
 *   <li>各消息点在同样的触发消息处按同样的 flag 查询卡片数据（Refresh/RefreshDeck/…/Reload），
 *       包装成合成 UPDATE_DATA/UPDATE_CARD 紧随触发消息投喂管线，补齐卡面数值。</li>
 * </ul>
 *
 * <p>与 C++ 的差异（本工程管线所致，均为等价适配）：
 * <ul>
 *   <li>ocgcore 不产 MSG_START，而 Java 管线依赖它建立卡片容器并切 DUELING 状态，故预载后
 *       按残局布局<b>合成一条 MSG_START</b>（rule=5，双方卡组/额外数取 query_field_count）；</li>
 *   <li>C++ 单线程内用 {@code singleSignal.Wait()} 阻塞等待应答，Java 改为应答队列：UI 经
 *       {@code engine.sendResponse → submitResponse} 入队，pump 线程在 {@code process} 循环间
 *       排空并 {@code set_responseb}，引擎随即推进；</li>
 *   <li>MSG_SHOW_HINT（C++ 模态弹窗）近似为非阻塞提示（{@code onNoticeMessage}）。</li>
 * </ul>
 */
public final class SingleModeRunner {

    private static final String TAG = "SingleModeRunner";

    /** set_responseb 要求引擎可读的完整响应缓冲（SIZE_RETURN_VALUE=256，同 ServerDuel） */
    private static final int RESPONSE_BUF_LEN = 256;

    // ==== 引擎消息号（common.h；本地私有，避免跨包常量依赖） ====
    private static final int MSG_RETRY = 1, MSG_HINT = 2, MSG_WAITING = 3, MSG_START = 4,
            MSG_WIN = 5, MSG_REQUEST_DECK = 8,
            MSG_SELECT_BATTLECMD = 10, MSG_SELECT_IDLECMD = 11,
            MSG_SHUFFLE_DECK = 32, MSG_SWAP_GRAVE_DECK = 35, MSG_REVERSE_DECK = 37,
            MSG_NEW_PHASE = 41, MSG_MOVE = 50, MSG_SUMMONED = 61, MSG_SPSUMMONED = 63,
            MSG_FLIPSUMMONED = 65, MSG_CHAINED = 71, MSG_CHAIN_SOLVED = 73, MSG_CHAIN_END = 74,
            MSG_DAMAGE_STEP_START = 113, MSG_DAMAGE_STEP_END = 114,
            MSG_TAG_SWAP = 161, MSG_RELOAD_FIELD = 162, MSG_AI_NAME = 163, MSG_SHOW_HINT = 164,
            MSG_MATCH_KILL = 170;

    // ==== 查询掩码（single_mode.h 默认参 / SinglePlayReload） ====
    private static final int FLAG_FIELD = 0xF81FFF;    // SinglePlayRefresh / SinglePlayRefreshSingle
    private static final int FLAG_POOL = 0x181FFF;     // RefreshDeck/Extra/Grave
    private static final int FLAG_RELOAD = 0xFFDFFF;   // SinglePlayReload
    private static final int LOCATION_OVERLAY = 0x80;

    // ==== 区域（对齐 single_mode.cpp 的 Refresh 家族） ====
    private static final int LOC_DECK = 0x01, LOC_HAND = 0x02, LOC_MZONE = 0x04, LOC_SZONE = 0x08,
            LOC_GRAVE = 0x10, LOC_REMOVED = 0x20, LOC_EXTRA = 0x40;

    private final GameEngine engine;

    private volatile Thread pump;
    private volatile boolean running;
    private volatile boolean finished;
    /** 正常结算（收到 MSG_WIN / 玩家投降合成 WIN）后置 true：收尾走 DUEL_END 结果流程；
     *  失败或用户中途退出则保持 false，收尾直接回 IDLE，不弹结果框 */
    private volatile boolean endWithResult;
    private volatile long pduel;

    /** UI 应答队列：{@code submitResponse} 入队，pump 线程在 process 循环间排空并喂引擎 */
    private final ArrayDeque<byte[]> responseQueue = new ArrayDeque<>();

    /** 合成 UPDATE_DATA 切片块的区域卡数提供者；引擎原生流不产 6/7，仅为满足 slice 签名 */
    private final ReplayMessageSlicer.ZoneBlocks zones =
            (player, location) -> pduel == 0L ? 0
                    : OcgDuelEngine.queryFieldCount(pduel, player, location);

    SingleModeRunner(GameEngine engine) {
        this.engine = engine;
    }

    // ==================== 生命周期 ====================

    /** 启动一局残局（pump 线程直驱引擎）。{@code luaPath} 为残局脚本绝对路径。
     * @param luaPath 残局脚本路径
     * @param noShuffleToDeck 是否启用「不洗切时回卡组改为回顶端」标志：勾选时使用
     *        {@code OcgDuelEngine.DUEL_RETURN_DECK_TOP(0x80)} 而非默认 0 */
    public void start(String luaPath, boolean noShuffleToDeck) {
        if (running) {
            Log.w(TAG, "残局已在运行，忽略重复 start");
            return;
        }
        engine.isSingleMode = true;
        running = true;
        finished = false;
        endWithResult = false;
        Thread t = new Thread(() -> runPump(luaPath, noShuffleToDeck), "SingleModePump");
        CrashHandler.getInstance().hookThread(t, "残局 - 引擎驱动");
        pump = t;
        t.start();
    }

    /** 停止 pump（disconnect / release 调用，幂等）：不作为「结算收尾」，收尾走 IDLE。 */
    public void stop() {
        running = false;
        Thread t = pump;
        if (t != null) {
            t.interrupt();
        }
    }

    /** 投降（对齐 menu_handler BUTTON_SURRENDER / ServerDuel.surrender）：合成我方判负 WIN 后收尾。 */
    public void quit() {
        if (!running) return;
        endWithResult = true;
        // MSG_WIN body：[winner=1(AI 胜), reason=0(投降)]
        feedMessage(MSG_WIN, new byte[]{1, 0});
        finished = true;
        running = false;
        Thread t = pump;
        if (t != null) {
            t.interrupt();
        }
    }

    /** UI 应答入口（由 GameActions.sendResponse 在残局态转发）：补齐 256 字节缓冲后入队。 */
    public void submitResponse(byte[] responseData) {
        if (!running || pduel == 0L) return;
        byte[] resb = new byte[RESPONSE_BUF_LEN];
        int len = Math.min(responseData == null ? 0 : responseData.length, RESPONSE_BUF_LEN - 1);
        if (len > 0) System.arraycopy(responseData, 0, resb, 0, len);
        synchronized (responseQueue) {
            responseQueue.offer(resb);
            responseQueue.notifyAll();   // 唤醒阻塞在 waitForResponse 的 pump 线程
        }
    }

    /**
     * 阻塞至有应答入队（对齐 C++ singleSignal.Wait）：running/finished 变化或中断即返回，
     * 由 pump 主循环下一轮 {@code drainResponses} 取出喂引擎。超时唤醒仅用于响应停止/退出。
     */
    private void waitForResponse() {
        synchronized (responseQueue) {
            while (running && !finished && responseQueue.isEmpty()) {
                try {
                    responseQueue.wait(200);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    // ==================== pump 主循环 ====================

    private void runPump(String luaPath, boolean noShuffleToDeck) {
        try {
            if (!NativeScriptBootstrap.ensureEngineReady()) {
                fail("决斗引擎或卡片脚本加载失败");
                return;
            }
            long duel = createDuel();
            if (duel == 0L) {
                fail("创建决斗失败（引擎未就绪）");
                return;
            }
            pduel = duel;
            // 初始 LP/起手（对齐 SinglePlayThread：8000 / 5 / 1），脚本会用 Debug.SetPlayerInfo 覆写
            OcgDuelEngine.setPlayerInfo(duel, 0, 8000, 5, 1);
            OcgDuelEngine.setPlayerInfo(duel, 1, 8000, 5, 1);
            engine.playerInfos[0].name = engine.playerName;
            engine.playerInfos[1].name = "";

            String fileName = new File(luaPath).getName();
            String script = "./single/" + fileName;
            if (OcgDuelEngine.preloadScript(duel, script) == 0) {
                fail("无法加载残局脚本：" + fileName);
                return;
            }

            // ocgcore 不产 MSG_START：按残局布局合成一条，触发管线建容器 + 切 DUELING
            feedSyntheticStart();

            // 预载缓冲分析（在 start_duel 之前）：AI_NAME / SHOW_HINT / RELOAD_FIELD
            byte[] preloaded = OcgDuelEngine.getMessage(duel);
            if (preloaded != null && preloaded.length > 0) {
                pumpAnalyze(preloaded);
            }
            if (!running || finished) {
                return;   // 预载阶段即被终止（退出 / MATCH_KILL 等）
            }

            // 根据用户设置选择是否使用 DUEL_RETURN_DECK_TOP 标志
            int opt = noShuffleToDeck ? OcgDuelEngine.DUEL_RETURN_DECK_TOP : 0;
            OcgDuelEngine.startDuel(duel, opt);

            while (running && !finished) {
                drainResponses();
                int result = OcgDuelEngine.process(duel);
                int len = result & OcgDuelEngine.PROCESSOR_BUFFER_LEN;
                int flag = result & OcgDuelEngine.PROCESSOR_FLAG;
                if (len > 0) {
                    byte[] msg = OcgDuelEngine.getMessage(duel);
                    if (msg != null && msg.length > 0) {
                        pumpAnalyze(msg);
                        throttle();   // 与实况动画/弹窗消化保持大致同步，防队列无界堆积
                    }
                }
                if (flag == OcgDuelEngine.PROCESSOR_END) {
                    break;   // 引擎自然结束（MSG_WIN 已在流里，pumpAnalyze 已置 finished）
                }
                // 引擎停在需要玩家应答的 SELECT（PROCESSOR_WAITING 且本批消息非空）：绝不可继续调
                // 用 process()，否则引擎会以未更新的 returns 反复重校验同一询问，逐轮输出
                // MSG_RETRY（日志里 replayLastGameMsg 反复 msgType=6 的根因）。必须阻塞至应答入队
                //（对齐 C++ SinglePlayAnalyze 在 ClientAnalyze(SELECT) 后 singleSignal.Wait 的语义）。
                // 确认类消息后的纯 PROCESSOR_WAIT 屏障返回 WAITING 时缓冲区已空(len==0)，不落入此分支。
                if (flag == OcgDuelEngine.PROCESSOR_WAITING && len > 0) {
                    waitForResponse();
                } else if (len == 0) {
                    sleep(2);   // 无消息且非等应答（确认屏障）：下轮 process 自然推进
                }
            }
        } catch (Throwable t) {
            CrashHandler.getInstance().report("残局-引擎驱动", t);
            Log.e(TAG, "残局 pump 异常", t);
        } finally {
            long d = pduel;
            if (d != 0L) {
                OcgDuelEngine.endDuel(d);
                pduel = 0L;
            }
            postCleanup();
        }
    }

    private long createDuel() {
        Random rnd = new Random();
        int[] seed = new int[8];
        for (int i = 0; i < 8; i++) {
            seed[i] = rnd.nextInt();
        }
        return OcgDuelEngine.createDuelV2(seed);
    }

    /** 切出一条引擎消息缓冲区（[消息号][体] 可含多条），逐条交给 {@link #handle}。 */
    private void pumpAnalyze(byte[] buf) {
        ByteBuffer bb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN);
        while (bb.hasRemaining() && running && !finished) {
            int type = bb.get() & 0xFF;
            int bodyStart = bb.position();
            if (!ReplayMessageSlicer.slice(type, bb, zones)) {
                // 未知消息号 / 消息体截断：游标不可靠，终止本局（对齐 slice 返回 false 语义）
                Log.w(TAG, "slice failed at msg " + type + ", stop single mode");
                finished = true;
                return;
            }
            byte[] body = Arrays.copyOfRange(buf, bodyStart, bb.position());
            handle(type, body);
        }
    }

    /**
     * 单条消息处理（对齐 SinglePlayAnalyze 各 case）：投喂实况管线 + 在同样的触发点合成刷新消息；
     * AI_NAME / SHOW_HINT 由本类直接处理（UTF-8 解码），不投喂管线（管线按 UTF-16 解析，编码不符）。
     */
    private void handle(int type, byte[] body) {
        switch (type) {
            // SELECT_BATTLECMD / SELECT_IDLECMD：对齐 SinglePlayRefresh 先于 ClientAnalyze——
            // 先注入区域刷新（合成 UPDATE_DATA），再投喂 SELECT 本身，使 SELECT 成为管线缓存
            // 的最后一条非 Retry 消息，MSG_RETRY 重放方能重建选择窗（而非误重放 UPDATE_DATA）
            case MSG_SELECT_BATTLECMD:
            case MSG_SELECT_IDLECMD:
                refreshFieldZones();
                feedMessage(type, body);
                return;
            case MSG_AI_NAME: {
                String name = decodeUtf8Payload(body);
                engine.playerInfos[1].name = name;   // 残局：引擎 P0=我方、P1=AI(对手)
                engine.mainHandler.post(() -> {
                    if (engine.listener != null) engine.listener.onPlayerInfoUpdated(1);
                });
                return;
            }
            case MSG_SHOW_HINT: {
                String hint = decodeUtf8Payload(body);
                engine.mainHandler.post(() -> {
                    if (engine.listener != null) engine.listener.onNoticeMessage(hint);
                });
                return;
            }
            case MSG_HINT:
                // 对齐 SinglePlayAnalyze：仅 player==0 才 ClientAnalyze（对手操作提示不展示）
                if (body.length >= 2 && (body[1] & 0xFF) != 0) return;
                feedMessage(type, body);
                return;
            case MSG_MATCH_KILL:
                // 引擎仅写 1 字节、无体；C++ 不进 ClientAnalyze。投喂管线会读 int 越界，故吞掉
                return;
            case MSG_WAITING:
            case MSG_REQUEST_DECK:
                return;   // 单机无真实等待方/无需展示卡组，投喂会悬挂等待提示或误切 DECK_SELECT
            case MSG_WIN:
                feedMessage(type, body);
                endWithResult = true;
                finished = true;   // 结算是终止点（对齐 SinglePlayAnalyze case MSG_WIN return false）
                return;
            default:
                break;
        }
        // SELECT 类与全部场地/动画/提示消息：投喂实况管线（残局态 replayMode=false → 正常弹选择窗）
        feedMessage(type, body);
        applyRefreshAfter(type, body);
    }

    // ==================== 区域刷新（SinglePlayRefresh 家族，同 EngineReplaySource 口径） ====================

    /** 在触发消息投喂后合成刷新消息（紧随其后入队，管线按序消化后再渲染选择窗） */
    private void applyRefreshAfter(int type, byte[] body) {
        switch (type) {
            case MSG_NEW_PHASE:
            case MSG_SUMMONED:
            case MSG_SPSUMMONED:
            case MSG_FLIPSUMMONED:
            case MSG_CHAINED:
            case MSG_CHAIN_SOLVED:
            case MSG_DAMAGE_STEP_START:
            case MSG_DAMAGE_STEP_END:
                refreshFieldZones();
                return;
            case MSG_CHAIN_END:
                refreshFieldZones();
                refreshZone(0, LOC_DECK, FLAG_POOL);
                refreshZone(1, LOC_DECK, FLAG_POOL);
                return;
            case MSG_SHUFFLE_DECK:
                refreshZone(playerAt(body), LOC_DECK, FLAG_POOL);
                return;
            case MSG_SWAP_GRAVE_DECK:
                refreshZone(playerAt(body), LOC_GRAVE, FLAG_POOL);
                return;
            case MSG_REVERSE_DECK:
                refreshZone(0, LOC_DECK, FLAG_POOL);
                refreshZone(1, LOC_DECK, FLAG_POOL);
                return;
            case MSG_TAG_SWAP: {
                int p = playerAt(body);
                refreshZone(p, LOC_DECK, FLAG_POOL);
                refreshZone(p, LOC_EXTRA, FLAG_POOL);
                return;
            }
            case MSG_MOVE:
                moveRefresh(body);
                return;
            case MSG_RELOAD_FIELD:
                refreshReloadAll();
                engine.mainHandler.post(() -> {
                    if (engine.listener != null) engine.listener.onFieldChanged();
                });
                return;
            default:
                return;
        }
    }

    /** SinglePlayRefresh 默认集：双方 怪兽区/魔法区/手牌（flag=0xf81fff） */
    private void refreshFieldZones() {
        int[] locs = {LOC_MZONE, LOC_SZONE, LOC_HAND};
        for (int loc : locs) {
            refreshZone(0, loc, FLAG_FIELD);
            refreshZone(1, loc, FLAG_FIELD);
        }
    }

    /** SinglePlayReload：全部 7 区域×双方（flag=0xffdfff） */
    private void refreshReloadAll() {
        int[] locs = {LOC_MZONE, LOC_SZONE, LOC_HAND, LOC_DECK, LOC_EXTRA, LOC_GRAVE, LOC_REMOVED};
        for (int loc : locs) {
            refreshZone(0, loc, FLAG_RELOAD);
            refreshZone(1, loc, FLAG_RELOAD);
        }
    }

    /** MSG_MOVE 刷新（SinglePlayAnalyze case MSG_MOVE）：落位非覆盖位且非原位同名移动才刷单卡 */
    private void moveRefresh(byte[] body) {
        if (body == null || body.length < 16) return;
        int pc = body[4] & 0xFF, pl = body[5] & 0xFF;
        int cc = body[8] & 0xFF, cl = body[9] & 0xFF, cs = body[10] & 0xFF;
        if (cl != 0 && (cl & LOCATION_OVERLAY) == 0 && (pl != cl || pc != cc)) {
            refreshSingle(cc, cl, cs);
        }
    }

    /** ReloadLocation 等价：query_field_card 块序列包成合成 UPDATE_DATA（[p][loc][块…]） */
    private void refreshZone(int enginePlayer, int location, int flag) {
        long d = pduel;
        if (d == 0L) return;
        byte[] blocks = OcgDuelEngine.queryFieldCard(d, enginePlayer, location, flag, 0);
        if (blocks.length == 0) return;
        byte[] body = new byte[blocks.length + 2];
        body[0] = (byte) enginePlayer;
        body[1] = (byte) location;
        System.arraycopy(blocks, 0, body, 2, blocks.length);
        feedMessage(6, body);
    }

    /** SinglePlayRefreshSingle 等价：query_card 单块包成合成 UPDATE_CARD（[p][loc][seq][块]） */
    private void refreshSingle(int enginePlayer, int location, int sequence) {
        long d = pduel;
        if (d == 0L) return;
        byte[] block = OcgDuelEngine.queryCard(d, enginePlayer, location, sequence, FLAG_FIELD, 0);
        if (block.length == 0) return;
        byte[] body = new byte[block.length + 3];
        body[0] = (byte) enginePlayer;
        body[1] = (byte) location;
        body[2] = (byte) sequence;
        System.arraycopy(block, 0, body, 3, block.length);
        feedMessage(7, body);
    }

    // ==================== 合成 MSG_START ====================

    /**
     * 合成 MSG_START（18 字节体，对齐 ServerDuel 手工 START / gframe 无 START 的补偿）：
     * playerType=0（我方=引擎 P0，先攻视角恒等）、rule=5（脚本自带大师规则2020）、
     * LP 先取 8000 基线（紧随其后的 RELOAD_FIELD 会覆写为残局实际值）、
     * 双方卡组/额外数取预载后的 query_field_count（=残局实际布局）。
     */
    private void feedSyntheticStart() {
        long d = pduel;
        int deck0 = OcgDuelEngine.queryFieldCount(d, 0, LOC_DECK);
        int extra0 = OcgDuelEngine.queryFieldCount(d, 0, LOC_EXTRA);
        int deck1 = OcgDuelEngine.queryFieldCount(d, 1, LOC_DECK);
        int extra1 = OcgDuelEngine.queryFieldCount(d, 1, LOC_EXTRA);
        byte[] body = new byte[18];
        ByteBuffer sb = ByteBuffer.wrap(body).order(ByteOrder.LITTLE_ENDIAN);
        sb.put((byte) 0);       // playerType：对 player0 而言，(0&1)==0 → duelIsFirst=true
        sb.put((byte) 5);       // duelRule（残局脚本 Debug.ReloadFieldBegin 固定为 5）
        sb.putInt(8000);        // lp0
        sb.putInt(8000);        // lp1
        sb.putShort((short) deck0);
        sb.putShort((short) extra0);
        sb.putShort((short) deck1);
        sb.putShort((short) extra1);
        engine.mainHandler.post(() -> {
            engine.duelStarted = true;
            engine.duelStage = YGOProtocol.DUEL_STAGE_DUELING;
            engine.soundManager.playBGM(SoundManager.BGM.DUEL);
            engine.enqueueGameMsg(MSG_START, ByteBuffer.wrap(body).order(ByteOrder.LITTLE_ENDIAN));
        });
    }

    // ==================== 应答排空与投喂 ====================

    private void drainResponses() {
        long d = pduel;
        if (d == 0L) return;
        while (true) {
            byte[] resb;
            synchronized (responseQueue) {
                resb = responseQueue.poll();
            }
            if (resb == null) return;
            OcgDuelEngine.setResponseB(d, resb);
        }
    }

    /** 主线程投递消息进实况管线（队列/动画闸门均为主线程态，必须 post；post 天然保序） */
    private void feedMessage(int msgType, byte[] body) {
        engine.mainHandler.post(() ->
                engine.enqueueGameMsg(msgType, ByteBuffer.wrap(body).order(ByteOrder.LITTLE_ENDIAN)));
    }

    /**
     * 投喂节流：等待实况管线消化完已投递消息（弹窗显示 / 动画推进）再继续驱动引擎，
     * 使解残局的动画节奏与联机一致，并防主线程队列无界堆积。有界等待（超时/终止即返回）。
     */
    private void throttle() {
        long deadline = System.currentTimeMillis() + 1500L;
        while (running && !finished && engine.hasPendingMsgs()
                && System.currentTimeMillis() < deadline) {
            sleep(8);
        }
    }

    // ==================== 收尾 ====================

    private void fail(String message) {
        Log.w(TAG, "single mode failed: " + message);
        endWithResult = false;
        running = false;
        finished = true;
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onNoticeMessage(message);
        });
    }

    private void postCleanup() {
        running = false;
        engine.mainHandler.post(() -> {
            engine.duelStarted = false;
            engine.inDuel = false;
            engine.siding = false;
            engine.duelStage = YGOProtocol.DUEL_STAGE_END;
            engine.isSingleMode = false;
            if (endWithResult) {
                // 复用联机结算链：DUEL_END → closeGameButtons + disconnect + 结果框 → 回主菜单（重开列表重试）
                engine.setState(GameEngine.GameState.DUEL_END);
            } else {
                engine.setState(GameEngine.GameState.IDLE);
            }
        });
    }

    // ==================== 小工具 ====================

    private static int playerAt(byte[] body) {
        return body == null || body.length < 1 ? 0 : body[0] & 0xFF;
    }

    /** AI_NAME / SHOW_HINT 负载：{@code [uint16 len(LE)][utf8 bytes][nul]} → 取 UTF-8 字符串 */
    private static String decodeUtf8Payload(byte[] body) {
        if (body == null || body.length < 2) return "";
        int len = (body[0] & 0xFF) | ((body[1] & 0xFF) << 8);
        int off = 2;
        if (len < 0 || off + len > body.length) len = Math.max(0, body.length - off);
        return new String(body, off, len, StandardCharsets.UTF_8);
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
