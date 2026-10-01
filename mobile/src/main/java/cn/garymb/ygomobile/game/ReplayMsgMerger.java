package cn.garymb.ygomobile.game;

import android.util.Log;

import org.tukaani.xz.LZMA2Options;
import org.tukaani.xz.LZMAInputStream;
import org.tukaani.xz.LZMAOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;

import cn.garymb.ygomobile.network.server.YrpWriter;

/**
 * 客户端收到的联机录像（STOC_REPLAY 包体 = 完整 .yrp 字节）与本地逐局录制的引擎 MSG 流
 * （{@link GameEngine#takeRecordedMsgSegment()}，见 recordMsgFrame）的双兼容合并器：
 * 在原文件数据段尾部追加【逐帧 [uint32 帧长][帧字节] + [uint32 blobLen]】自描述段并置
 * {@link YrpWriter#REPLAY_MSG_STREAM_V2}(0x40) 标志位，数据段布局与
 * {@link YrpWriter#build()} 完全一致：base(names/params/decks) + 响应记录流 + msgBlob + [uint32 blobLen]。
 *
 * <p>头部字段偏移（小端）：id=0, version=4, flag=8, seed=12, datasize=16, startTime=20, props=24..31，
 * YRP1 头 32B；YRP2 另有 48B 扩展（seedSequence[8]/headerVersion/value1..3）共 80B。
 *
 * <p>原文件头（id/version/seed/startTime/props，YRP2 另含 seedSequence/headerVersion/value1..3）
 * 与 base、响应段全部逐字节保留，仅回写 flag |= 0x40 与 datasize（未压缩长度），因此：
 * <ul>
 * <li>libygomobile（依赖 ocgcore+script 的 C++ replay_mode 路径）：ReadNextResponse 只按引擎需要
 * 顺序消费响应、MSG_WIN 后不再读尾部，追加段对其完全透明；未知 flag 位被忽略（replay.cpp 仅测
 * COMPRESSED/TAG/UNIFORM/SINGLE_MODE 位）——照常重放出赛；</li>
 * <li>YGOProActivity（本工程 {@link MsgStreamReplaySource}）：{@link ReplayReader} 识别 V2 位后经
 * 末 4 字节 blobLen 反推 msg 段起点逐帧消费，无需 ocgcore 与 script 即可播放。</li>
 * </ul>
 *
 * <p>解压/压缩以原文件 props 声明的 LZMA1 参数（lc/lp/pb/dictSize）对偶进行；
 * 任一环节失败（帧为空、文件已有 V2 段、头非法、LZMA 异常）都原样返回输入字节，保证原录像可用优先。
 */
public final class ReplayMsgMerger {
    private static final String TAG = "ReplayMsgMerger";

    /** ReplayHeader(32B)；YRP2 另有 48B 扩展字段 = 80B（对齐 ReplayReader/YrpWriter） */
    private static final int BASE_HEADER_SIZE = 32;
    private static final int EXTENDED_HEADER_SIZE = 80;
    /** LZMA_ALONE 头：1(属性) + 4(dictSize) + 8(未压缩长度)，tukaani 输出需剥掉 */
    private static final int LZMA_ALONE_HEADER_SIZE = 13;

    private ReplayMsgMerger() {
    }

    /**
     * 把一段逐局引擎消息（每帧 = 完整消息含首字节消息号）以 V2 尾段形式并入 .yrp 字节。
     *
     * @param yrp     通讯发来的原始 .yrp 包体
     * @param frames  本局录制消息帧；为空或文件已含 V2 段时原样返回
     * @return 双兼容 .yrp 字节（失败时为原始字节，调用方无需回退逻辑）
     */
    public static byte[] appendMsgFrames(byte[] yrp, List<byte[]> frames) {
        if (yrp == null || yrp.length < BASE_HEADER_SIZE + 1 || frames == null || frames.isEmpty()) {
            return yrp;
        }
        try {
            ByteBuffer buf = ByteBuffer.wrap(yrp).order(ByteOrder.LITTLE_ENDIAN);
            int id = buf.getInt();
            int version = buf.getInt();
            int flag = buf.getInt();
            if (id != ReplayReader.REPLAY_ID_YRP1 && id != ReplayReader.REPLAY_ID_YRP2) {
                return yrp;
            }
            if ((flag & YrpWriter.REPLAY_MSG_STREAM_V2) != 0) {
                // 已是 V2（如本地房主 YrpWriter 产物），不重复追加
                return yrp;
            }
            // 头字段顺序与 ReplayReader.parseReplay 严格对偶：
            // id(0) version(4) flag(8) seed(12) datasize(16) startTime(20) props(24..31)；
            // 曾把 seed 误当 datasize、props 从 20 起读，外部服务端录像合并必败（聊天因此丢失）
            buf.getInt(); // seed 原样保留，不参与合并
            int datasize = buf.getInt();
            if (datasize <= 0 || datasize > (1 << 26)) {
                return yrp;
            }
            buf.getInt(); // startTime 原样保留
            byte[] props = new byte[8];
            buf.get(props);
            int headerSize = (id == ReplayReader.REPLAY_ID_YRP2) ? EXTENDED_HEADER_SIZE : BASE_HEADER_SIZE;
            if (yrp.length <= headerSize || buf.remaining() < 8) {
                // 头短于声明的 headerSize / props 越界：非法包体，原样返回
                return yrp;
            }
            byte[] body = Arrays.copyOfRange(yrp, headerSize, yrp.length);

            boolean compressed = (flag & ReplayReader.REPLAY_COMPRESSED) != 0;
            // props 解码约定（与 C++ LzmaDec / tukaani 对偶）：byte0 = lc + 9*lp + 45*pb；byte1..4 = dictSize 小端
            int prop = props[0] & 0xFF;
            int lc = prop % 9;
            int lp = (prop / 9) % 5;
            int pb = (prop / 9) / 5;
            int dictSize = ByteBuffer.wrap(props, 1, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();

            byte[] raw;
            if (compressed) {
                raw = lzmaDecompress(body, datasize, props[0], dictSize);
                if (raw == null) {
                    Log.e(TAG, "LZMA decompress failed, keep original replay bytes");
                    return yrp;
                }
            } else {
                raw = body;
            }

            // 追加 V2 尾段：逐帧 [uint32 帧长][帧字节] + [uint32 blobLen]（blobLen 不含尾 4 字节）
            int blobLen = 0;
            for (byte[] f : frames) {
                if (f != null && f.length > 0) {
                    blobLen += 4 + f.length;
                }
            }
            if (blobLen == 0) {
                return yrp;
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream(raw.length + blobLen + 4);
            out.write(raw);
            for (byte[] f : frames) {
                if (f == null || f.length == 0) continue;
                out.write(int32Le(f.length));
                out.write(f);
            }
            out.write(int32Le(blobLen));
            byte[] newRaw = out.toByteArray();

            byte[] newBody;
            if (compressed) {
                newBody = lzmaCompress(newRaw, lc, lp, pb, dictSize);
                if (newBody == null) {
                    Log.e(TAG, "LZMA recompress failed, keep original replay bytes");
                    return yrp;
                }
            } else {
                newBody = newRaw;
            }

            // 仅 patch 头内 flag 与 datasize 两个字段，其余字节（含 YRP2 扩展段）原样
            byte[] header = Arrays.copyOf(yrp, headerSize);
            ByteBuffer hb = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN);
            hb.position(8);
            hb.putInt(flag | YrpWriter.REPLAY_MSG_STREAM_V2);
            hb.position(16);
            hb.putInt(newRaw.length);

            ByteArrayOutputStream fos = new ByteArrayOutputStream(header.length + newBody.length);
            fos.write(header);
            fos.write(newBody);
            Log.i(TAG, "Merged " + frames.size() + " msg frames into replay (v"
                    + Integer.toHexString(version) + ", id=" + Integer.toHexString(id)
                    + "), datasize " + datasize + " -> " + newRaw.length);
            return fos.toByteArray();
        } catch (Exception e) {
            Log.e(TAG, "appendMsgFrames failed, keep original replay bytes", e);
            return yrp;
        }
    }

    /** 与 ReplayReader.lzmaDecompressTukaani 同款：裸 LZMA1 流（无 ALONE 头），按 datasize 读取 */
    private static byte[] lzmaDecompress(byte[] compressed, int expectedSize, byte propsByte, int dictSize) {
        try {
            LZMAInputStream in = new LZMAInputStream(
                    new ByteArrayInputStream(compressed), -1L, propsByte, dictSize);
            byte[] output = new byte[expectedSize];
            int total = 0;
            while (total < expectedSize) {
                int r = in.read(output, total, expectedSize - total);
                if (r < 0) break;
                total += r;
            }
            in.close();
            return total == expectedSize ? output : null;
        } catch (Exception e) {
            Log.e(TAG, "lzmaDecompress error", e);
            return null;
        }
    }

    /** 与 YrpWriter.lzmaCompress 对偶：压缩参数取原文件 props（保证回写后 props 字节仍与流匹配），剥 13 字节 ALONE 头 */
    private static byte[] lzmaCompress(byte[] raw, int lc, int lp, int pb, int dictSize) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            LZMA2Options options = new LZMA2Options();
            options.setMode(LZMA2Options.MODE_NORMAL);
            options.setDictSize(dictSize);
            options.setLc(lc);
            options.setLp(lp);
            options.setPb(pb);
            // size >= 0：不写 end marker（ReplayReader/C++ 均按 datasize 消费）
            LZMAOutputStream lzma = new LZMAOutputStream(bos, options, raw.length);
            lzma.write(raw);
            lzma.finish();
            lzma.close();
            byte[] alone = bos.toByteArray();
            if (alone.length <= LZMA_ALONE_HEADER_SIZE) {
                return null;
            }
            return Arrays.copyOfRange(alone, LZMA_ALONE_HEADER_SIZE, alone.length);
        } catch (Exception e) {
            Log.e(TAG, "lzmaCompress error", e);
            return null;
        }
    }

    private static byte[] int32Le(int value) {
        return ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(value).array();
    }
}
