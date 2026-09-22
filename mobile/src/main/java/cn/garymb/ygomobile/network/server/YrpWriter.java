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
 * 置 {@link #REPLAY_MSG_STREAM_V2} 标志并把该流以【逐帧 [uint32 帧长][帧字节] + [uint32 总长]】
 * 的自描述段追加在响应记录流之后（数据段尾部）：
 * C++ replay.cpp 的 ReadNextResponse 只按引擎需要顺序消费响应、MSG_WIN 后不再读尾部，
 * 故产物同时兼容 gframe C++ replaymode 与 java MsgStreamReplaySource 回放；
 * 未录入消息流的旧格式文件不含该标志，回放端自动回退引擎重跑路径。
 *
 * <p>产物为完整 .yrp 字节：ExtendedReplayHeader(80 字节) + LZMA 压缩流。
 */
public final class YrpWriter {

    public static final int REPLAY_COMPRESSED = 0x1;
    public static final int REPLAY_TAG = 0x2;
    public static final int REPLAY_SINGLE_MODE = 0x8;
    public static final int REPLAY_UNIFORM = 0x10;
    /** 旧 V1 扩展位（已废弃写出）：msg 段插在 decks 与 responses 之间，C++ 会把帧长字节
     *  当响应长度消费导致全盘错位；仅供读取历史文件 */
    public static final int REPLAY_MSG_STREAM = 0x20;
    /** 自定义扩展位 V2：响应记录流之后含逐帧成帧的主机视角 MSG 流 + [uint32 总长] 尾部自描述（C++ 不读尾部，天然透明） */
    public static final int REPLAY_MSG_STREAM_V2 = 0x40;
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
    /** 扩展头尾字段（header_version 与 value1..3）：新录制用默认值；旧录像转码时
     *  经 {@link #setExtendedHeaderValues} 原样回写源文件值，保证 C++（ocgcore+script 重跑）
     *  侧拿到的头部字节与原文件一致，跨端传看不因重写而偏离 */
    private int headerVersion = 1;
    private int value1;
    private int value2;
    private int value3;

    /** 初始状态段（names/params/decks），决斗开始前一次性写入。 */
    private final ByteArrayOutputStream base = new ByteArrayOutputStream();
    /** 逐条玩家响应记录段（[uint8 len][data]），MSG_RETRY 时由 removeData 回滚尾部。 */
    private final ByteArrayOutputStream responses = new ByteArrayOutputStream();
    /** 主机视角引擎 MSG 字节流（含服务端合成的 UPDATE 刷新消息），每次 writeMessage = 一帧。 */
    private final java.util.List<byte[]> msgFrames = new java.util.ArrayList<>();

    public YrpWriter(int[] seedSequence, int version, int flag, int startTime) {
        if (seedSequence == null || seedSequence.length != 8) {
            throw new IllegalArgumentException("seedSequence must have 8 elements");
        }
        this.seedSequence = seedSequence.clone();
        this.version = version;
        this.flag = flag;
        this.startTime = startTime;
    }

    /** 转码专用：用源录像的 header_version/value1..3 替换默认值（新录制无需调用）。 */
    public void setExtendedHeaderValues(int headerVersion, int value1, int value2, int value3) {
        this.headerVersion = headerVersion;
        this.value1 = value1;
        this.value2 = value2;
        this.value3 = value3;
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
     * 整段追加已有的响应记录流快照（已是 [uint8 len][data] 逐条拼接格式）：
     * 旧格式录像重跑成功后转码固化为 V2 文件时原样搬运响应段，不逐条重写。
     */
    public void writeResponseRaw(byte[] stream) {
        if (stream != null && stream.length > 0) {
            responses.write(stream, 0, stream.length);
        }
    }

    /**
     * 记录一条发给主机（players[0]）的完整 STOC_GAME_MSG 字节（一帧一条引擎消息），
     * 与响应记录互相独立追加；build 时若非空则置 {@link #REPLAY_MSG_STREAM_V2} 并逐帧成帧。
     */
    public void writeMessage(byte[] data, int len) {
        if (data == null || len <= 0) {
            return;
        }
        msgFrames.add(Arrays.copyOf(data, len));
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

    /** 小端 uint32 字节序列（android.jar 低版本 ByteBuffer.clear() 返回 Buffer 无法链式 putInt，单独成方法） */
    private static byte[] int32Le(int value) {
        ByteBuffer b = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(value);
        return b.array();
    }

    /** 组装完整 .yrp 文件字节：ExtendedReplayHeader + LZMA 压缩流。 */
    public byte[] build() {
        byte[] resp = responses.toByteArray();
        // V2 数据段布局：base(names/params/decks) + 响应记录流 + msgBlob + [uint32 blobLen]。
        // msgBlob = 逐帧 [uint32 帧长][帧字节]，blobLen 为不含末尾 4 字节的 msgBlob 总长：
        // ① C++ ReadNextResponse 只顺序消费引擎需要的响应、WIN 后不读尾，自定义尾段天然忽略；
        // ② Java 经末 4 字节 blobLen 反推 msg 段起点，无需知道响应长度；
        // ③ 逐帧长度前缀消除“无帧界拼接 + 切片长度表”的游标耦合（UPDATE_DATA 块数错位报“消息号0”根因）。
        int blobLen = 0;
        for (byte[] f : msgFrames) {
            blobLen += 4 + f.length;
        }
        byte[] baseBytes = base.toByteArray();
        int rawLen = baseBytes.length + resp.length + (blobLen > 0 ? blobLen + 4 : 0);
        ByteArrayOutputStream rawStream = new ByteArrayOutputStream(rawLen);
        rawStream.write(baseBytes, 0, baseBytes.length);
        rawStream.write(resp, 0, resp.length);
        if (blobLen > 0) {
            flag |= REPLAY_MSG_STREAM_V2;
            for (byte[] f : msgFrames) {
                rawStream.write(int32Le(f.length), 0, 4);
                rawStream.write(f, 0, f.length);
            }
            rawStream.write(int32Le(blobLen), 0, 4);
        }
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
        header.putInt(headerVersion);    // header_version（新录制默认 1；转码时保留源值）
        header.putInt(value1);
        header.putInt(value2);
        header.putInt(value3);

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
