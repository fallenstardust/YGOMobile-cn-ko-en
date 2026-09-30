package cn.garymb.ygomobile.game;

import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import cn.garymb.ygomobile.network.server.YrpWriter;
import cn.garymb.ygomobile.utils.CrashHandler;

/**
 * 转码固化（自 ReplayPlayer 拆分，逻辑零改）：重跑成功的旧格式录像重写为带消息流的 V2 文件。
 * TAG 由调用方传入，保持拆分前日志标识。
 */
class ReplayTranscoder {

    /**
     * 把本次重跑采集的完整消息帧流（含合成 UPDATE 刷新帧与头部合成的 MSG_START 帧）用
     * {@link YrpWriter} 重建为 V2 带流录像并原地替换（原文件改名 *.yrp.bak 备份）：
     * 下次播放同一文件时 {@code ReplayReader} 解出 msgFrames → 直接走 MsgStreamReplaySource，
     * 零引擎、秒载、不再可能遇 MSG_RETRY；尾段设 V2 标志对 C++ gframe 回放透明（它只顺序
     * 消费响应不读尾部，YrpWriter 同源设计）。残局/tag/YRP1/播放失败不转码（素材不完整或
     * 种子构造不对称，写回会破坏兼容性）。后台守护线程执行，失败仅记日志不影响已完成的播放。
     */
    static void transcodeToMsgStream(String tag, final int msgStart, final String path, final ReplayReader.ReplayData d,
                                     final byte[] responses, final List<byte[]> frames) {
        if (path == null || path.isEmpty() || d == null) return;
        Thread t = new Thread(() -> {
            try {
                doTranscode(tag, msgStart, path, d, responses, frames);
            } catch (Throwable e) {
                Log.w(tag, "replay transcode failed: " + path, e);
            }
        }, "ReplayTranscode");
        t.setDaemon(true);
        CrashHandler.getInstance().hookThread(t, "回放-转码固化");
        t.start();
    }

    private static void doTranscode(String tag, int msgStart, String path, ReplayReader.ReplayData d,
                                    byte[] responses, List<byte[]> frames) throws IOException {
        File file = new File(path);
        if (!file.isFile()) return;
        YrpWriter w = new YrpWriter(d.header.seedSequence, d.header.base.version,
                d.header.base.flag, d.header.base.startTime);
        // 扩展头尾字段原样保留（header_version/value1..3）：C++（libygomobile.so 的
        // ocgcore+script 重跑）侧只按位检查已知 flag、顺序消费响应不读尾部，种子序列/
        // 参数/卡组/响应与原文件逐字节一致即可照常重跑——转码产物同时支持
        // Java 零引擎播放与 C++ 跨端传看
        w.setExtendedHeaderValues(d.header.headerVersion, d.header.value1,
                d.header.value2, d.header.value3);
        for (String name : d.playerNames) {
            w.writeName(name == null ? "" : name);
        }
        w.writeInt32(d.params.startLp);
        w.writeInt32(d.params.startHand);
        w.writeInt32(d.params.drawCount);
        w.writeInt32(d.params.duelFlag);
        // 卡组段按文件原序回写（readInfo 读入顺序即文件字节序；ServerDuel 录制时的
        // 逆序装载只发生在写入前，与转码无关）
        for (ReplayReader.DeckInfo deck : d.decks) {
            w.writeInt32(deck.main.size());
            for (Integer c : deck.main) w.writeInt32(c == null ? 0 : c);
            w.writeInt32(deck.extra.size());
            for (Integer c : deck.extra) w.writeInt32(c == null ? 0 : c);
        }
        w.writeResponseRaw(responses);
        // 重跑不产 MSG_START（gframe 由 dField.Initial 建场）：按 ServerDuel 19 字节模板
        // 合成头帧，使转码产物在纯消息流路径下也能经实况管线建初始场并回填卡组卡面
        byte[] startFrame = buildStartFrame(msgStart, d);
        w.writeMessage(startFrame, startFrame.length);
        for (byte[] f : frames) {
            w.writeMessage(f, f.length);
        }
        byte[] out = w.build();
        File bak = new File(file.getParentFile(), file.getName() + ".bak");
        if (bak.exists() && !bak.delete()) return;
        if (!file.renameTo(bak)) return;      // 无法备份：不动原文件
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(out);
        } catch (IOException e) {
            if (!file.exists()) bak.renameTo(file);   // 写失败回滚备份，原录像不丢
            throw e;
        }
        Log.i(tag, "replay transcoded to msg-stream V2: " + path
                + " (frames=" + (frames.size() + 1) + ", backup=" + bak.getName() + ")");
    }

    /** MSG_START 头帧（ServerDuel L104-117 同模板）：[4][player=0][duelRule][lp0][lp1][deck/extra 数×4] */
    private static byte[] buildStartFrame(int msgStart, ReplayReader.ReplayData d) {
        byte[] buf = new byte[19];
        ByteBuffer sb = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN);
        sb.put((byte) msgStart);
        sb.put((byte) 0);
        sb.put((byte) (d.params.duelFlag >> 16));
        sb.putInt(d.params.startLp);
        sb.putInt(d.params.startLp);
        ReplayReader.DeckInfo p0 = d.decks.size() > 0 ? d.decks.get(0) : null;
        ReplayReader.DeckInfo p1 = d.decks.size() > 1 ? d.decks.get(1) : null;
        sb.putShort((short) (p0 == null ? 0 : p0.main.size()));
        sb.putShort((short) (p0 == null ? 0 : p0.extra.size()));
        sb.putShort((short) (p1 == null ? 0 : p1.main.size()));
        sb.putShort((short) (p1 == null ? 0 : p1.extra.size()));
        return buf;
    }
}
