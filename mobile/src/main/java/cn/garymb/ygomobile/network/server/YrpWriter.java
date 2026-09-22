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
 * <p>除 _uniform 模式的初始状态（双方名、参数、卡组）与逐条玩家响应外，若决斗期间通过
 * {@link #writeMessage(byte[], int)} 实时录入了主机视角的引擎 MSG 字节流，则在 build 时
 * 置 {@link #REPLAY_MSG_STREAM} 标志并把该流以 [uint32 长度][原始流] 段插入响应记录之前，
 * 回放端（{@code MsgStreamReplaySource}）可完全脱离 ocgcore/script 重跑直接按消息流回放；
 * 未录入消息流的旧格式文件不含该标志，回放端自动回退引擎重跑路径。
 *
 * <p>产物为完整 .yrp 字节：ExtendedReplayHeader(80 字节) + LZMA 压缩流。
 */
public final class YrpWriter {

    public static final int REPLAY_COMPRESSED = 0x1;
    public static final int REPLAY_TAG = 0x2;
    public static final int REPLAY_SINGLE_MODE = 0x8;
    public static final int REPLAY_UNIFORM = 0x10;
    /** 自定义扩展位：数据段内含 [uint32 长度 + 主机视角 MSG 字节流]（C++ 端仅用到 0x10，此位安全） */
    public static final int REPLAY_MSG_STREAM = 0x20;
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

    /** 初始状态段（names/params/decks），决斗开始前一次性写入。 */
    private final ByteArrayOutputStream base = new ByteArrayOutputStream();
    /** 逐条玩家响应记录段（[uint8 len][data]），MSG_RETRY 时由 removeData 回滚尾部。 */
    private final ByteArrayOutputStream responses = new ByteArrayOutputStream();
    /** 主机视角引擎 MSG 字节流（含服务端合成的 UPDATE 刷新消息），决斗期间实时追加。 */
    private final ByteArrayOutputStream messages = new ByteArrayOutputStream();

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
        base.write(b.array(), 0, 4);
    }

    public void writeData(byte[] src, int off, int len) {
        base.write(src, off, len);
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
        base.write(b.array(), 0, 40);
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
        responses.write(rl & 0xFF);
        responses.write(data, 0, rl);
        return 1 + rl;
    }

    /**
     * 记录一条发给主机（players[0]）的完整 STOC_GAME_MSG 字节（含单条或多条消息拼接），
     * 与响应记录互相独立追加；build 时若非空则置 {@link #REPLAY_MSG_STREAM} 标志。
     */
    public void writeMessage(byte[] data, int len) {
        if (data == null || len <= 0) {
            return;
        }
        messages.write(data, 0, len);
    }

    /** 从响应记录流尾部移除 length 字节（MSG_RETRY 回滚上一条响应）。 */
    public void removeData(int length) {
        if (length <= 0) {
            return;
        }
        byte[] cur = responses.toByteArray();
        int newLen = Math.max(0, cur.length - length);
        responses.reset();
        responses.write(cur, 0, newLen);
    }

    /** 组装完整 .yrp 文件字节：ExtendedReplayHeader + LZMA 压缩流。 */
    public byte[] build() {
        byte[] msg = messages.toByteArray();
        byte[] resp = responses.toByteArray();
        // 数据段布局：base(names/params/decks) + [uint32 msgLen][msg 流] + 响应记录流
        // （用 toByteArray+write 而非 writeTo(OutputStream)，后者声明受检 IOException）
        byte[] baseBytes = base.toByteArray();
        ByteArrayOutputStream rawStream = new ByteArrayOutputStream(baseBytes.length + 4 + msg.length + resp.length);
        rawStream.write(baseBytes, 0, baseBytes.length);
        if (msg.length > 0) {
            flag |= REPLAY_MSG_STREAM;
            ByteBuffer lb = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
            rawStream.write(lb.putInt(msg.length).array(), 0, 4);
            rawStream.write(msg, 0, msg.length);
        }
        rawStream.write(resp, 0, resp.length);
        byte[] raw = rawStream.toByteArray();
        flag |= REPLAY_COMPRESSED;
        // props：byte0 = LZMA1 属性字节，编码约定 lc + 9*lp + 45*pb（与 XZ LZMAInputStream、
        //   C++ LzmaDec 的解码 lc=prop%9、lp=(prop/9)%5、pb=(prop/9)/5 对偶）；
        //   byte1..4 = dictSize 小端；byte5..7 保留 0。
        byte[] props = new byte[8];
        props[0] = (byte) (LC + 9 * LP + 45 * PB);
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

    /** LZMA_ALONE 头部字节数：1(属性) + 4(dictSize) + 8(未压缩长度)。 */
    private static final int LZMA_ALONE_HEADER_SIZE = 13;

    private static byte[] lzmaCompress(byte[] raw) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try {
            // tukaani 的 LZMAOutputStream 输出的是 LZMA_ALONE 格式：13 字节头(属性+dict+长度) + 裸 LZMA1 压缩体。
            // 而 replay.cpp 的 LzmaCompress 只产出裸 LZMA1 压缩体，属性/dict 单独存于 .yrp 头 props[]，
            // ReplayReader 也用 LZMAInputStream(in, -1, propsByte, dictSize) 从裸流解码。
            // 故这里必须剥掉 13 字节头，只保留压缩体，才能与 ReplayReader/C++ 对偶。
            LZMA2Options options = new LZMA2Options();
            options.setMode(LZMA2Options.MODE_NORMAL);
            options.setDictSize(DICT_SIZE);
            options.setLc(LC);
            options.setLp(LP);
            options.setPb(PB);
            // 传真实长度：size>=0 时 LZMAOutputStream 不追加 end marker，ReplayReader 按 datasize 读取
            LZMAOutputStream lzma = new LZMAOutputStream(bos, options, raw.length);
            lzma.write(raw);
            lzma.finish();
            // 不调用 flush()：LZMAOutputStream 不支持 flush，调用会抛 XZIOException
            lzma.close();
            byte[] alone = bos.toByteArray();
            if (alone.length <= LZMA_ALONE_HEADER_SIZE) {
                return new byte[0];
            }
            return Arrays.copyOfRange(alone, LZMA_ALONE_HEADER_SIZE, alone.length);
        } catch (IOException e) {
            // 理论上内存流不会抛；退回未压缩（COMPRESSED 标志已在 build 置位，此处保持）
            return Arrays.copyOf(raw, raw.length);
        }
    }
}
