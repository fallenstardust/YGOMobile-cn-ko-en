package cn.garymb.ygomobile.network.server;

import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
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
            sink.onDisconnect(this);
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
