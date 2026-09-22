package cn.garymb.ygomobile.game;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * 纯消息来源：录像文件（YRP2 + {@code REPLAY_MSG_STREAM} 标志）内含主机视角的完整 MSG 流，
 * 直接按长度表逐条消费即可，不启动 ocgcore、不需要响应记录（结果已固化在消息流里）。
 *
 * <p>对应 gframe {@code replay_mode.cpp::ReplayAnalyze} 中 replay 带消息流时的分支：
 * 该序列与 LAN 客户端收到的 STOC_GAME_MSG 等价，故投喂实况管线后画面与观战完全一致。
 */
final class MsgStreamReplaySource extends ReplaySource {

    /** 消息流快照（ReplayReader 的缓冲带游标状态，快照后方可反复 rewind 重放） */
    private byte[] stream;

    MsgStreamReplaySource(ReplayPlayer player) {
        super(player);
    }

    @Override
    boolean engineDriven() {
        return false;
    }

    @Override
    boolean open() {
        data = player.getReplayData();
        if (data == null || data.msgBuffer == null) {
            setLastError("录像文件内不含引擎消息流");
            return false;
        }
        ByteBuffer ms = data.msgBuffer.duplicate().order(ByteOrder.LITTLE_ENDIAN);
        stream = new byte[ms.remaining()];
        ms.get(stream);
        attach(stream);
        return true;
    }

    /** 整条消息流是一次性载入的完整缓冲，读尽即代表录像播毕 */
    @Override
    boolean refill() {
        return false;
    }

    @Override
    void rewind() {
        attach(stream);
    }

    @Override
    void close() {
        stream = null;
        super.close();
    }
}
