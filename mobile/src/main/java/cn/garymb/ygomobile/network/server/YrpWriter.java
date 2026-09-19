package cn.garymb.ygomobile.network.server;

import org.tukaani.xz.LZMA2Options;
import org.tukaani.xz.LZMAOutputStream;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * YRP2（REPLAY_UNIFORM）录像写入器，{@code cn.garymb.ygomobile.game.ReplayReader} 的写端对偶，
 * 移植 {@code Classes/gframe/replay.cpp}（BeginRecord/WriteHeader/WriteData/WriteInt32/WriteResponse/
 * RemoveData/EndRecord）与 {@code single_duel.cpp::TPResult/GetResponse/EndDuel} 的调用序列。
 *
 * <p>_uniform 模式_只记录初始状态（双方名、参数、卡组）与逐条玩家响应，不记录引擎 MSG 字节流；
 * 回放端用相同 seed_sequence 重跑引擎复现，故本类只缓冲这些定长/变长记录，结束时整体 LZMA 压缩。
 *
 * <p>产物为完整 .yrp 字节：ExtendedReplayHeader(80 字节) + LZMA 压缩流。
 */
public final class YrpWriter {

    public static final int REPLAY_COMPRESSED = 0x1;
    public static final int REPLAY_TAG = 0x2;
    public static final int REPLAY_SINGLE_MODE = 0x8;
    public static final int REPLAY_UNIFORM = 0x10;
    public static final int REPLAY_ID_YRP2 = 0x32707279;

    /** ReplayHeader + ExtendedReplayHeader 追加字段 = 80 字节（对齐 replay.h 结构体大小）。 */
    private static final int HEADER_SIZE = 80;

    // 与 replay.cpp LzmaCompress(... 5, 0x1U<<24, 3, 0, 2, ...) 对齐的解码参数。
    private static final int LC = 3;
    private static final int LP = 0;
    private static final int PB = 2;
    private static final int DICT_SIZE = 0x1 << 24;

    private final int[] seedSequence;
    private final int version;
    private int flag;
    private final int startTime;

    /** 未压缩记录流（names/params/decks/responses）。 */
    private final ByteArrayOutputStream record = new ByteArrayOutputStream();

    public YrpWriter(int[] seedSequence, int version, int flag, int startTime) {
        if (seedSequence == null || seedSequence.length != 8) {
            throw new IllegalArgumentException("seedSequence must have 8 elements");
        }
        this.seedSequence = seedSequence.clone();
        this.version = version;
        this.flag = flag;
        this.startTime = startTime;
    }

    public void writeInt32(int value) {
        ByteBuffer b = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(value);
        record.write(b.array(), 0, 4);
    }

    public void writeData(byte[] src, int off, int len) {
        record.write(src, off, len);
    }

    /** 写入 40 字节玩家名（UTF-16LE，20 码元，不足补 0），对齐 WriteData(name, 40)。 */
    public void writeName(String name) {
        ByteBuffer b = ByteBuffer.allocate(40).order(ByteOrder.LITTLE_ENDIAN);
        int n = Math.min(name.length(), 20);
        for (int i = 0; i < n; i++) {
            b.putChar(name.charAt(i));
        }
        for (int i = n; i < 20; i++) {
            b.putChar('\0');
        }
        record.write(b.array(), 0, 40);
    }

    /**
     * 记录一条玩家响应：uint8 长度 + 原始响应字节。返回值即写入的总字节数，
     * 供 {@link #removeData(int)} 在 MSG_RETRY 时回滚（对齐 single_duel 的 last_replay_response_size）。
     */
    public int writeResponse(byte[] data, int len) {
        if (data == null || len <= 0) {
            return 0;
        }
        int rl = Math.min(len, 0xFF);
        record.write(rl & 0xFF);
        record.write(data, 0, rl);
        return 1 + rl;
    }

    /** 从记录流尾部移除 length 字节（MSG_RETRY 回滚上一条响应）。 */
    public void removeData(int length) {
        if (length <= 0) {
            return;
        }
        byte[] cur = record.toByteArray();
        int newLen = Math.max(0, cur.length - length);
        record.reset();
        record.write(cur, 0, newLen);
    }

    /** 组装完整 .yrp 文件字节：ExtendedReplayHeader + LZMA 压缩流。 */
    public byte[] build() {
        byte[] raw = record.toByteArray();
        flag |= REPLAY_COMPRESSED;
        // props：byte0 = (pb*5+lc)*9+lp；byte1..4 = dictSize 小端；byte5..7 保留 0
        byte[] props = new byte[8];
        props[0] = (byte) ((PB * 5 + LC) * 9 + LP);
        ByteBuffer db = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        db.putInt(DICT_SIZE);
        System.arraycopy(db.array(), 0, props, 1, 4);

        ByteBuffer header = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN);
        // ReplayHeader
        header.putInt(REPLAY_ID_YRP2);
        header.putInt(version);
        header.putInt(flag);
        header.putInt(seedSequence[0]);   // base.seed（v1 兼容字段，取首个种子）
        header.putInt(raw.length);         // datasize = 未压缩记录长度
        header.putInt(startTime);
        header.put(props);
        // ExtendedReplayHeader 追加
        for (int s : seedSequence) {
            header.putInt(s);
        }
        header.putInt(1);                  // header_version
        header.putInt(0);                  // value1
        header.putInt(0);                  // value2
        header.putInt(0);                  // value3

        byte[] compressed = lzmaCompress(raw);

        ByteBuffer out = ByteBuffer.allocate(HEADER_SIZE + compressed.length).order(ByteOrder.LITTLE_ENDIAN);
        out.put(header.array());
        out.put(compressed);
        return out.array();
    }

    private static byte[] lzmaCompress(byte[] raw) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            // tukaani 的裸 LZMA1 写端：LZMAOutputStream 读取 LZMA2Options 的 dict/lc/lp/pb/mode，
            // 仅输出 LZMA1 压缩体（不含属性头），与 replay.cpp 的裸 LZMA 输出一致；
            // props 头由 build() 依据同样的 LC/LP/PB/DICT 手工写入，与 ReplayReader 解码对偶。
            LZMA2Options options = new LZMA2Options();
            options.setMode(LZMA2Options.MODE_NORMAL);
            options.setDictSize(DICT_SIZE);
            options.setLc(LC);
            options.setLp(LP);
            options.setPb(PB);
            // 传真实长度：不写结束标记（size>=0 时 LZMAOutputStream 不追加 end marker）
            LZMAOutputStream lzma = new LZMAOutputStream(bos, options, raw.length);
            lzma.write(raw);
            lzma.finish();
            lzma.flush();
            lzma.close();
            return bos.toByteArray();
        } catch (IOException e) {
            // 理论上内存流不会抛；退回未压缩（COMPRESSED 标志已在 build 置位，此处保持）
            return Arrays.copyOf(raw, raw.length);
        }
    }
}
