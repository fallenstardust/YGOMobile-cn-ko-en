package cn.garymb.ygomobile.network.server;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.ArrayDeque;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * 局域网服务器侧的单条客户端连接，等价 {@code Classes/gframe/network.h} 的 {@code DuelPlayer}
 * （socket + name + type + state）与 {@code netserver.cpp::ServerEchoRead} 的分包读线程。
 *
 * <p>读线程只负责按 2 字节小端长度前缀切出完整包并转交 {@link Sink}（由 {@code GameRoom} 串行处理），
 * 自身不做任何房间状态改动；发送经 {@link #send} 同步写入 socket，保证包序。
 *
 * <p>type：0/1=决斗位（{@code NETPLAYER_TYPE_PLAYER1/PLAYER2}）、7=观战、0xff=未分配；
 * state：等待应答的 CTOS 类型，0xff=自由。与 {@code single_duel.cpp} 的 {@code dp->type/state} 对偶。
 */
public final class ServerConnection {

    private static final String TAG = "ServerConnection";
    private static final int BUFFER_SIZE = 0x20000;

    public static final int TYPE_NONE = 0xff;
    /** 自由状态：可发送任意 CTOS（对齐 C++ 新连接 DuelPlayer.state 默认 0）。 */
    public static final int STATE_FREE = 0x0;
    /** 阻塞状态：除投降/聊天外拒绝一切 CTOS（等待对方应答）。 */
    public static final int STATE_NONE = 0xff;

    /** 连接事件回调，全部在 {@code GameRoom} 的单线程执行器上被调用。 */
    public interface Sink {
        void onPacket(ServerConnection conn, int proto, ByteBuffer body);

        void onDisconnect(ServerConnection conn);
    }

    private final Socket socket;
    private final Sink sink;
    private InputStream input;
    private OutputStream output;
    private volatile boolean running;
    private Thread readThread;

    /** 玩家名（协议内 20 码元 UTF-16）。 */
    public String name = "";
    /** 座位类型，见 {@code YGOProtocol.NETPLAYER_TYPE_*}。 */
    public int type = TYPE_NONE;
    /** 等待应答状态，见 {@code YGOProtocol.CTOS_*}、{@link #STATE_FREE}（自由）或 {@link #STATE_NONE}（阻塞）。 */
    public int state = STATE_FREE;
    /** 是否房主（对齐 C++ host_player 判定，仅用于 STOC_TYPE_CHANGE 的 0x10 位）。 */
    public boolean host;
    /**
     * 对局进行中该席位已掉线、正在宽限期内等重连（本工程扩展）：连接对象仍留在 {@code players[seat]}
     * 里所以下行链路无需改造，但写一个已断的 socket 只会每包刷一条失败日志，故置位后 {@link #send}
     * 直接丢弃；重连接回时清除（新连接对象本身不会置位）。
     */
    public boolean linkDetached;
    /**
     * 撤回整局重放的答复欠账：重放期间服务端把人机已见过的每条询问又发了一遍，它对这些询问的答复
     * 一律晚于撤回落地，不能当成对新挂出询问的应答喂进引擎。故重放每挂出一条询问在此计数，
     * 它的 CTOS_RESPONSE 一律丢弃并递减（见 {@code GameRoom#handlePacket}），同时延后重挂直到欠账清零。
     */
    public int replayAnswerDebt;

    /**
     * 被压住的人机操作播报队列（等它的应答被引擎接受后再逐条中继）：WindBot 会在一个决策点把
     * 整轮的行动说明一次发完（先发聊天、后发应答），立即中继会让台词早于实际卡片操作、
     * 且一步步的计划会一股脑提前弹出。这里改为排队，每个被接受的应答只放行一条，
     * 使台词与实际操作节点逐一对齐。只在人机席位使用。
     */
    public final ArrayDeque<PendingChat> pendingAiChats = new ArrayDeque<>();

    /** 一条被压住的人机操作播报（载荷供中继，文本供录像帧）。 */
    public static final class PendingChat {
        public final byte[] payload;
        public final String text;

        PendingChat(byte[] payload, String text) {
            this.payload = payload;
            this.text = text;
        }
    }

    public ServerConnection(Socket socket, Sink sink) throws IOException {
        this.socket = socket;
        this.sink = sink;
        this.socket.setTcpNoDelay(true);
        this.socket.setKeepAlive(true);
        this.input = socket.getInputStream();
        this.output = socket.getOutputStream();
    }

    public void start() {
        running = true;
        readThread = new Thread(this::readLoop, "ServerConn-Read");
        readThread.setDaemon(true);
        readThread.start();
    }

    private void readLoop() {
        byte[] header = new byte[2];
        try {
            while (running) {
                if (!readFully(header, 0, 2)) {
                    break;
                }
                int len = (header[0] & 0xFF) | ((header[1] & 0xFF) << 8);
                if (len <= 0 || len > BUFFER_SIZE) {
                    Log.w(TAG, "非法包长: " + len);
                    break;
                }
                byte[] body = new byte[len];
                if (!readFully(body, 0, len)) {
                    break;
                }
                ByteBuffer buf = ByteBuffer.wrap(body).order(ByteOrder.LITTLE_ENDIAN);
                int proto = buf.get() & 0xFF;
                final ByteBuffer payload = buf.slice().order(ByteOrder.LITTLE_ENDIAN);
                sink.onPacket(this, proto, payload);
            }
        } catch (IOException e) {
            if (running) {
                Log.w(TAG, "连接读结束: " + e.getMessage());
            }
        } finally {
            running = false;
            // 断开回调不得掀掉本 finally：它把任务投递到房间执行器，主机停止那一瞬执行器已 shutdown
            // 会抛 RejectedExecutionException，穿出 finally 会使 closeQuietly 不执行（socket 泄漏）
            // 并让读线程带异常终止；这里兜住一切异常，保证连接一定被关闭
            try {
                sink.onDisconnect(this);
            } catch (Throwable t) {
                Log.w(TAG, "断开回调投递失败: " + t);
            }
            closeQuietly();
        }
    }

    private boolean readFully(byte[] buf, int off, int len) throws IOException {
        int total = 0;
        while (total < len) {
            int r = input.read(buf, off + total, len - total);
            if (r < 0) {
                return false;
            }
            total += r;
        }
        return true;
    }

    /** 发送一个无载荷 STOC 包（仅 proto 字节）。 */
    public void sendEmpty(int proto) {
        send(proto, null);
    }

    /** 发送 STOC 包：[uint16 小端长度(=1+载荷长)][proto][载荷]。 */
    public void send(int proto, byte[] payload) {
        // 掉线宽限期内的席位：socket 已不可用，丢弃而不写（对齐 C++ 对已断 DuelPlayer 不再续写）
        if (linkDetached) {
            return;
        }
        int plen = 1 + (payload == null ? 0 : payload.length);
        byte[] pkt = new byte[2 + plen];
        pkt[0] = (byte) (plen & 0xFF);
        pkt[1] = (byte) ((plen >> 8) & 0xFF);
        pkt[2] = (byte) proto;
        if (payload != null && payload.length > 0) {
            System.arraycopy(payload, 0, pkt, 3, payload.length);
        }
        try {
            synchronized (this) {
                if (output != null) {
                    output.write(pkt);
                    output.flush();
                }
            }
        } catch (IOException e) {
            Log.w(TAG, "发送失败，关闭连接: " + e.getMessage());
            running = false;
            closeQuietly();
        }
    }

    /** 重发上一条已缓存包（对齐 C++ ReSendToPlayer 语义的简化：本实现每处显式重发）。 */
    public boolean isAlive() {
        return running && socket != null && !socket.isClosed();
    }

    public void close() {
        running = false;
        closeQuietly();
    }

    private void closeQuietly() {
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
    }
}
