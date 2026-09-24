package cn.garymb.ygomobile.game;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * 回放消息来源抽象：把「录像文件里自带的主机视角 MSG 流」与「旧格式重跑 ocgcore 现产生的
 * 消息流」统一成同一条 <b>(消息号, 消息体)</b> 序列，交给 {@link ReplayPlayer} 逐条投喂
 * GameEngine 的实况管线（卡片动画/音效/LP 浮字/召唤与连锁大图全部由实况侧产生）。
 *
 * <p>两种来源过去各有一套渲染实现（旧格式那套即被删除的 ReplayEngine::processMessage 家族，
 * 约 1900 行），动画队列与观战不一致；统一投喂实况管线后，回放与实况/观战走完全相同的
 * 派发路径，这是本次改造的核心目的。
 *
 * <p>切片统一走 {@link ReplayMessageSlicer}（长度表与 gframe replay_mode.cpp::ReplayAnalyze
 * 同源）。UPDATE_DATA 的消息体是自界长块序列，块数取决于实况侧该区域当前卡片数，
 * 故切片前先经 {@link ReplayPlayer#awaitDispatchDrain()} 等实况管线追上游标，
 * 再用 {@link ReplayPlayer} 作为 {@link ReplayMessageSlicer.ZoneBlocks} 取块数。
 */
abstract class ReplaySource {

    /** 一条已切出消息体的引擎消息（body 不含消息号，小端） */
    static final class Msg {
        final int type;
        final byte[] body;
        /** 引擎重跑源合成的区域刷新消息（gframe ReplayRefresh 族）：卡码已在源头归一，
         *  pump 侧不再按实况区域卡数二次映射 */
        final boolean synthetic;

        Msg(int type, byte[] body) {
            this(type, body, false);
        }

        Msg(int type, byte[] body, boolean synthetic) {
            this.type = type;
            this.body = body;
            this.synthetic = synthetic;
        }
    }

    protected final ReplayPlayer player;
    protected ReplayReader.ReplayData data;

    /** 当前待消费的消息缓冲：1 字节消息号 + 变/定长消息体，无分隔符，只能按长度表推进 */
    private ByteBuffer cursor;
    /** 引擎重跑源在触发消息切完后合成的刷新消息：先于下一条真实消息消费 */
    protected final Deque<Msg> synthQueue = new ArrayDeque<>();
    private String lastError;

    ReplaySource(ReplayPlayer player) {
        this.player = player;
    }

    /** 建立数据源（读流 / 重跑引擎建局）。@return false 表示失败，原因见 {@link #getLastError()} */
    abstract boolean open();

    /**
     * 当前缓冲耗尽后续取下一段消息缓冲。
     *
     * @return false 表示数据源已播毕（或出错，见 {@link #getLastError()}），{@link #next()} 随之返回 null
     */
    abstract boolean refill();

    /** 旧格式引擎重跑来源（undo/restart 需重跑决斗并复位响应记录），纯消息流为 false */
    abstract boolean engineDriven();

    /**
     * 转码固化素材：引擎重跑期间产生的完整消息帧列表（每帧 = [消息号][原始卡码消息体]，
     * 含合成的 UPDATE_DATA/UPDATE_CARD 刷新帧）。播毕无错时 {@link ReplayPlayer} 据此把录像
     * 重建为带消息流的 V2 文件，此后回放该录像不再需要 ocgcore 重跑。纯消息流无需转码，返回 null。
     */
    java.util.List<byte[]> capturedEngineFrames() {
        return null;
    }

    /**
     * 帧界数据源（V2 逐帧消息流）：当前缓冲边界即消息边界。为 true 时 UPDATE_DATA(6)
     * 的消息体直接取整帧剩余字节，无需 awaitDispatchDrain 与块数切片（录制/回放两侧区域
     * 卡数不一致导致游标错位、报“消息号0”的根源即在此）；默认 false（原始拼接流/引擎重跑）
     */
    protected boolean frameBounded() {
        return false;
    }

    /** 游标回到起点（回放「上一步/从头重放」）：消息流重置缓冲；引擎重跑重建决斗 */
    abstract void rewind();

    void close() {
        cursor = null;
        synthQueue.clear();
    }

    /**
     * 取下一条消息：{@code null} 表示流结束或出错（用 {@link #getLastError()} 区分），
     * 投喂循环据此正常收尾或报「Error occurs.」。
     */
    Msg next() {
        while (player.isPumpAlive()) {
            Msg synth = synthQueue.poll();
            if (synth != null) return synth;
            if (cursor == null || !cursor.hasRemaining()) {
                if (!refill()) return null;
                continue;
            }
            int type = cursor.get() & 0xFF;
            if (frameBounded() && type == 6) {
                // V2 帧界：整帧剩余即本条 UPDATE_DATA 载荷，消费后自然换帧
                byte[] body = new byte[cursor.remaining()];
                cursor.get(body);
                return new Msg(type, body);
            }
            if (type == 6 || type == 7) {
                // UPDATE_DATA 块数依赖实况侧当前区域的卡片数：先等已投喂消息全部消化，
                // 否则两侧块数不一致会让游标错位（后续消息全部错解析）
                player.awaitDispatchDrain();
            }
            int start = cursor.position();
            if (!ReplayMessageSlicer.slice(type, cursor, player)) {
                lastError = "录像消息流解析失败（消息号 " + type + "，步数=" + player.getCurrentStep() + "）";
                return null;
            }
            int end = cursor.position();
            byte[] body = new byte[end - start];
            ((ByteBuffer) cursor.duplicate().order(ByteOrder.LITTLE_ENDIAN)
                    .position(start).limit(end)).get(body);
            onSliced(type, body);
            return new Msg(type, body);
        }
        return null;
    }

    ReplayReader.ReplayData getData() {
        return data;
    }

    String getLastError() {
        return lastError;
    }

    protected void setLastError(String error) {
        this.lastError = error;
    }

    /**
     * 一条消息切完（游标已在消息体末尾、消息体已取出）后的钩子：引擎重跑来源在此把 SELECT
     * 消息对应的响应记录喂回引擎（对齐 replay_mode.cpp::ReadReplayResponse），并按
     * ReplayAnalyze 各 case 的 ReplayRefresh 调用点合成区域刷新消息入队（body 供 MSG_MOVE /
     * TAG_SWAP 等取字段判定刷新目标），纯消息流无需实现。
     */
    protected void onSliced(int msgType, byte[] body) {
    }

    /** 追加一条合成刷新消息（在下一条真实消息之前被 {@link #next()} 取出投喂） */
    protected void enqueueSynthetic(Msg msg) {
        synthQueue.add(msg);
    }

    /** 抽取当前缓冲的一段字节为独立小端缓冲（子类 rewind 时重建游标用） */
    protected void attach(byte[] packed) {
        cursor = packed == null ? null
                : ByteBuffer.wrap(packed).order(ByteOrder.LITTLE_ENDIAN);
    }

    /** 直接挂接已有缓冲（不复制），游标即缓冲当前位置 */
    protected void attach(ByteBuffer buf) {
        cursor = buf == null ? null : buf.order(ByteOrder.LITTLE_ENDIAN);
    }
}
