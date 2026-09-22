package cn.garymb.ygomobile.game;

import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

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
 */
final class EngineReplaySource extends ReplaySource {

    private static final String TAG = "EngineReplaySrc";

    /** set_responseb 要求引擎可读的完整响应缓冲（SIZE_RETURN_VALUE=256，同 ServerDuel） */
    private static final int RESPONSE_BUF_LEN = 256;

    private long pduel;
    /** 响应记录流游标（uniform 模式消息区全部为 [uint8 len][response] 记录） */
    private ByteBuffer responses;
    /** 响应记录区快照，rewind（undo/restart 重跑）时回到起点 */
    private byte[] originalResponses;
    /** 引擎请求重放（应答与录像不一致）或响应耗尽：终止重跑，不当作正常播毕 */
    private boolean fatal;

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
        responses = originalResponses == null ? null
                : ByteBuffer.wrap(originalResponses).order(ByteOrder.LITTLE_ENDIAN);
        ReplayReader.ExtendedReplayHeader eh = data.header;
        pduel = eh.base.id == ReplayReader.REPLAY_ID_YRP2
                ? OcgDuelEngine.createDuelV2(eh.seedSequence)
                : OcgDuelEngine.createDuel(eh.base.seed);   // YRP1：C++ 用 mt19937(seed) 首输出，此处用原 seed
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
        return true;
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
                // 引擎既不产消息也不结束：响应记录与引擎期望失步，停止重跑避免空转
                setLastError("录像响应记录与引擎不同步（重跑停滞于步数="
                        + player.getCurrentStep() + "）");
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
     * SELECT/询问类消息切完后喂回录制响应（对齐 ReadReplayResponse）；
     * MSG_RETRY 说明应答非法、重跑无法继续，置错终止。
     */
    @Override
    protected void onSliced(int msgType) {
        if (msgType == 1) {
            setLastError("Error occurs.（引擎请求重放 MSG_RETRY，步数=" + player.getCurrentStep() + "）");
            fatal = true;
            return;
        }
        if (ReplayMessageSlicer.isSelectable(msgType)) {
            feedRecordedResponse();
        }
    }

    /** 从响应记录流读一条 [uint8 len][data] 并 set_responseb */
    private void feedRecordedResponse() {
        ByteBuffer rp = responses;
        if (rp == null || pduel == 0L || !rp.hasRemaining()) {
            setLastError("录像响应记录提前耗尽（步数=" + player.getCurrentStep() + "）");
            fatal = true;
            return;
        }
        int len = rp.get() & 0xFF;
        if (len > rp.remaining()) len = rp.remaining();
        byte[] resb = new byte[RESPONSE_BUF_LEN];
        rp.get(resb, 0, len);
        OcgDuelEngine.setResponseB(pduel, resb);
    }

    /** 上一步/从头重放：结束当前决斗、响应记录回到起点、以同一 seed 重建决斗（对齐 ReplayMode::Restart） */
    @Override
    void rewind() {
        endDuel();
        attach((ByteBuffer) null);
        if (!startDuel()) {
            Log.w(TAG, "rewind: restart duel failed - " + getLastError());
        }
    }

    @Override
    void close() {
        endDuel();
        responses = null;
        originalResponses = null;
        super.close();
    }

    private void endDuel() {
        if (pduel != 0L) {
            OcgDuelEngine.endDuel(pduel);
            pduel = 0L;
        }
    }
}
