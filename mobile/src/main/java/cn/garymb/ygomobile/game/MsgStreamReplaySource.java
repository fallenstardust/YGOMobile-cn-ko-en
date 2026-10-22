package cn.garymb.ygomobile.game;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

/**
 * 纯消息来源：录像文件（YRP2 + {@code REPLAY_MSG_STREAM_V2}/{@code REPLAY_MSG_STREAM} 标志）
 * 内含主机视角的完整 MSG 流，直接按长度表逐条消费即可，不启动 ocgcore、不需要响应记录
 * （结果已固化在消息流里）。
 *
 * <p>两种数据形态：
 * <ul>
 *   <li><b>V2（当前写出格式）</b>：{@code data.msgFrames} 逐帧列表，每帧 = 一条完整引擎消息
 *   （含消息号），帧界天然隔离消息——UPDATE_DATA 整帧消费不参与块数切片（{@link #frameBounded()}），
 *   个别消息体消费不完也绝不串位，帧尽即录像播毕；</li>
 *   <li><b>V1（历史 Java 录制）</b>：{@code data.msgBuffer} 无帧界原始拼接，整段载入后按
 *   {@link ReplayMessageSlicer} 长度表推进游标。</li>
 * </ul>
 *
 * <p>对应 gframe {@code replay_mode.cpp::ReplayAnalyze} 中 replay 带消息流时的分支：
 * 该序列与 LAN 客户端收到的 STOC_GAME_MSG 等价，故投喂实况管线后画面与观战完全一致。
 */
final class MsgStreamReplaySource extends ReplaySource {

    /** V1 消息流快照（ReplayReader 的缓冲带游标状态，快照后方可反复 rewind 重放） */
    private byte[] stream;
    /** V2 逐帧列表（null = 非 V2 文件） */
    private List<byte[]> frames;
    /** V2 下一待取帧下标 */
    private int frameIndex;

    MsgStreamReplaySource(ReplayPlayer player) {
        super(player);
    }

    @Override
    boolean engineDriven() {
        return false;
    }

    @Override
    protected boolean frameBounded() {
        return frames != null;
    }

    @Override
    boolean open() {
        data = player.getReplayData();
        if (data == null) {
            setLastError("录像数据未加载");
            return false;
        }
        if (data.msgFrames != null) {
            frames = data.msgFrames;
            frameIndex = 0;
            attach((ByteBuffer) null);
            return true;
        }
        if (data.msgBuffer == null) {
            setLastError("录像文件内不含引擎消息流");
            return false;
        }
        ByteBuffer ms = data.msgBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        stream = new byte[ms.remaining()];
        ms.get(stream);
        attach(stream);
        return true;
    }

    /**
     * V2：取下一帧挂为游标缓冲（帧尽即录像播毕）；V1：整条消息流是一次性载入的完整
     * 缓冲，读尽即代表播毕。
     */
    @Override
    boolean refill() {
        if (frames != null) {
            if (frameIndex >= frames.size()) return false;
            ByteBuffer fb = ByteBuffer.wrap(frames.get(frameIndex++)).order(ByteOrder.LITTLE_ENDIAN);
            attach(fb);
            return true;
        }
        return false;
    }

    @Override
    void rewind() {
        if (frames != null) {
            frameIndex = 0;
            attach((ByteBuffer) null);
        } else {
            attach(stream);
        }
    }

    @Override
    void close() {
        stream = null;
        frames = null;
        super.close();
    }
}
