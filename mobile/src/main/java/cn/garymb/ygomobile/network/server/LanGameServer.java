package cn.garymb.ygomobile.network.server;

import android.util.Log;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.NativeInitOptions;
import cn.garymb.ygomobile.engine.NativeScriptBootstrap;
import cn.garymb.ygomobile.engine.OcgDuelEngine;
import cn.garymb.ygomobile.network.BufferIO;
import cn.garymb.ygomobile.network.YGOProtocol;

/**
 * 纯 Java 局域网决斗主机，移植 {@code Classes/gframe/netserver.cpp} 的单房模型：
 * <ul>
 *   <li>TCP：{@link ServerSocket}（默认 7911）accept 循环，每连接一个 {@link ServerConnection}；
 *       读线程仅分包，所有房间状态改动经 {@link #post} 投递到单线程执行器串行执行；</li>
 *   <li>UDP：bind 7920 收 {@code HostRequest}（identifier=={@link #NETWORK_CLIENT_ID}），
 *       以 {@code room} 的房名/HostInfo 组 72 字节 {@code HostPacket} 单播回请求者 IP:7921，
 *       与客户端 {@code LanDiscoveryManager.parseHostPacket} 严格对偶；</li>
 *   <li>引擎：{@link #start} 前引导 {@link OcgDuelEngine}（读 cards.cdb + script/），
 *       不可用时直接返回 {@code false}，由调用方给出失败反馈。</li>
 * </ul>
 *
 * <p>路由与 C++ {@code HandleCTOSPacket} 对齐：{@code PLAYER_INFO} 设连接昵称、
 * {@code CREATE_GAME} 惰性建 {@link GameRoom}（本机客户端以普通玩家身份连回并发此包即成房主）、
 * {@code JOIN_GAME} 加入，其余全部交 {@link GameRoom#handlePacket}。
 */
public final class LanGameServer implements YGOProtocol {

    private static final String TAG = "LanGameServer";

    /** 默认 TCP 决斗端口（对齐 netserver server_port / ConnectionManager 硬编码 7911）。 */
    public static final int DEFAULT_PORT = 7911;
    /** UDP 发现应答监听端口（客户端广播目标端口）。 */
    private static final int BROADCAST_RECV_PORT = 7920;
    /** 客户端接收应答的端口（单播回此处）。 */
    private static final int BROADCAST_SEND_PORT = 7921;

    private final Object lifecycleLock = new Object();

    private volatile boolean running;
    private volatile boolean listening;
    private int serverPort = DEFAULT_PORT;

    private ServerSocket serverSocket;
    private DatagramSocket udpSocket;
    private Thread acceptThread;
    private Thread udpThread;

    /** 单线程房间执行器：决斗引擎非线程安全，所有 CTOS/timer/断开均在此串行。 */
    private ExecutorService roomExecutor;

    /** 房间状态，volatile 以便 UDP 广播线程读取最新值。 */
    private volatile GameRoom room;

    /** 活跃连接，供停止时统一关闭。 */
    private final List<ServerConnection> connections = new CopyOnWriteArrayList<>();

    /**
     * 启动服务器：引导引擎、打开 TCP/UDP、拉起后台线程。
     *
     * @param port 监听端口，&lt;=0 时用 {@link #DEFAULT_PORT}
     * @return 成功返回 true；引擎不可用/加载失败/端口占用返回 false（已自行清理）
     */
    public boolean start(int port) {
        synchronized (lifecycleLock) {
            if (running) {
                return true;
            }
            serverPort = port > 0 ? port : DEFAULT_PORT;
            if (!OcgDuelEngine.isAvailable()) {
                Log.e(TAG, "libygoengine.so 不可用，无法建立主机");
                return false;
            }
            if (!bootstrapEngine()) {
                Log.e(TAG, "引擎卡片/脚本加载失败");
                return false;
            }
            roomExecutor = Executors.newSingleThreadExecutor(daemonFactory("YgoRoomExec"));
            try {
                serverSocket = new ServerSocket(serverPort);
                serverSocket.setReuseAddress(true);
            } catch (IOException e) {
                Log.e(TAG, "TCP 端口 " + serverPort + " 打开失败", e);
                cleanup();
                return false;
            }
            try {
                udpSocket = new DatagramSocket(BROADCAST_RECV_PORT);
                udpSocket.setBroadcast(true);
                udpSocket.setReuseAddress(true);
            } catch (IOException e) {
                // UDP 发现失败不阻断 TCP 对战：仅关闭广播，主机仍可直连 IP 加入。
                Log.w(TAG, "UDP 7920 绑定失败，局域网自动发现不可用", e);
                udpSocket = null;
            }
            running = true;
            listening = true;
            acceptThread = new Thread(this::acceptLoop, "LanAccept");
            acceptThread.setDaemon(true);
            acceptThread.start();
            if (udpSocket != null) {
                udpThread = new Thread(this::broadcastLoop, "LanBroadcast");
                udpThread.setDaemon(true);
                udpThread.start();
            }
            Log.i(TAG, "局域网主机已启动，端口 " + serverPort);
            return true;
        }
    }

    /** 加载卡片数据与脚本目录（主库最后覆盖扩展），对齐 AppsSettings 的 cdb 列表顺序。 */
    private boolean bootstrapEngine() {
        try {
            NativeInitOptions options = AppsSettings.get().getNativeInitOptions();
            // native 引擎只读 rootPath/script/ 实体文件，先把 scripts.zip 解压出 script/ 目录
            NativeScriptBootstrap.ensureScriptsExtracted(options.mWorkPath);
            String[] cdbPaths = options.mDbList.toArray(new String[0]);
            return OcgDuelEngine.init(options.mWorkPath, cdbPaths);
        } catch (Throwable t) {
            Log.e(TAG, "引擎初始化异常", t);
            return false;
        }
    }

    // ==================================================================
    // TCP accept + 包路由
    // ==================================================================

    private void acceptLoop() {
        while (running && listening && !serverSocket.isClosed()) {
            try {
                Socket client = serverSocket.accept();
                final ServerConnection conn = new ServerConnection(client, sink);
                connections.add(conn);
                conn.start();
            } catch (IOException e) {
                if (running && listening) {
                    Log.w(TAG, "accept 中断: " + e.getMessage());
                }
                break;
            }
        }
    }

    private final ServerConnection.Sink sink = new ServerConnection.Sink() {
        @Override
        public void onPacket(ServerConnection conn, int proto, ByteBuffer body) {
            post(() -> routePacket(conn, proto, body));
        }

        @Override
        public void onDisconnect(ServerConnection conn) {
            post(() -> handleDisconnect(conn));
        }
    };

    /** 在房间执行器上串行处理一个 CTOS 包（对齐 netserver::HandleCTOSPacket 路由）。 */
    private void routePacket(ServerConnection conn, int proto, ByteBuffer body) {
        switch (proto) {
            case CTOS_PLAYER_INFO: {
                if (body.remaining() >= 40) {
                    conn.name = BufferIO.readUTF16(body, 20);
                }
                break;
            }
            case CTOS_CREATE_GAME: {
                if (room != null) {
                    return; // 已有房间，忽略重复建房
                }
                GameRoom created = new GameRoom(this);
                room = created;
                created.createGame(conn, body);
                break;
            }
            case CTOS_JOIN_GAME: {
                GameRoom r = room;
                if (r == null) {
                    return;
                }
                r.joinGame(conn, body, false);
                break;
            }
            default: {
                GameRoom r = room;
                if (r != null) {
                    r.handlePacket(conn, proto, body);
                }
            }
        }
    }

    private void handleDisconnect(ServerConnection conn) {
        connections.remove(conn);
        GameRoom r = room;
        if (r != null) {
            r.onDisconnect(conn);
        }
    }

    // ==================================================================
    // UDP 发现应答
    // ==================================================================

    private void broadcastLoop() {
        byte[] buf = new byte[256];
        while (running && listening && udpSocket != null && !udpSocket.isClosed()) {
            try {
                DatagramPacket req = new DatagramPacket(buf, buf.length);
                udpSocket.receive(req);
                if (req.getLength() < 2) {
                    continue;
                }
                int identifier = (buf[0] & 0xFF) | ((buf[1] & 0xFF) << 8);
                GameRoom r = room;
                if (identifier == NETWORK_CLIENT_ID && r != null) {
                    byte[] hp = buildHostPacket(r);
                    DatagramPacket resp =
                            new DatagramPacket(hp, hp.length, req.getAddress(), BROADCAST_SEND_PORT);
                    udpSocket.send(resp);
                }
            } catch (IOException e) {
                if (running && listening) {
                    Log.w(TAG, "UDP 广播循环结束: " + e.getMessage());
                }
                break;
            }
        }
    }

    /** 组 72 字节 HostPacket（对齐 network.h HostPacket 与 C++ BroadcastEvent）。 */
    private byte[] buildHostPacket(GameRoom r) {
        ByteBuffer b = ByteBuffer.allocate(72).order(ByteOrder.LITTLE_ENDIAN);
        b.putShort((short) NETWORK_SERVER_ID);
        b.putShort((short) Constants.PRO_VERSION);
        b.putShort((short) serverPort);
        b.putShort((short) 0); // padding[2]
        b.putInt(0);           // ipaddr（对齐 C++ BroadcastEvent 未赋值）
        ByteBuffer name = ByteBuffer.allocate(40).order(ByteOrder.LITTLE_ENDIAN);
        BufferIO.writeUTF16(name, r.roomName == null ? "" : r.roomName, 20);
        b.put(name.array());
        b.put(r.hostInfo.toBytes());
        return b.array();
    }

    // ==================================================================
    // 生命周期 / 执行器投递
    // ==================================================================

    /** 投递一个任务到单线程房间执行器；执行器已停止时静默丢弃。 */
    public void post(Runnable task) {
        ExecutorService exec = roomExecutor;
        if (exec == null || exec.isShutdown()) {
            return;
        }
        exec.execute(() -> {
            try {
                task.run();
            } catch (Throwable t) {
                Log.e(TAG, "房间任务异常", t);
            }
        });
    }

    /** 停止监听：不再接受新连接与新发现请求（对局中调用），已连接的房间继续运行。 */
    public void stopListen() {
        listening = false;
        closeQuietly(serverSocket);
        closeQuietly(udpSocket);
    }

    /** 完全停止服务器：结束在跑的对局、关闭全部连接与线程、停止执行器。对齐 {@code NETServer::StopServer}。 */
    public void stopServer() {
        synchronized (lifecycleLock) {
            if (!running) {
                return;
            }
            running = false;
            listening = false;
            closeQuietly(serverSocket);
            closeQuietly(udpSocket);
            post(() -> {
                GameRoom r = room;
                if (r != null && r.duel != null) {
                    r.duel.endDuel();
                }
                for (ServerConnection c : connections) {
                    c.close();
                }
            });
            for (ServerConnection c : connections) {
                c.close();
            }
            connections.clear();
            if (roomExecutor != null) {
                roomExecutor.shutdown();
            }
            room = null;
            Log.i(TAG, "局域网主机已停止");
        }
    }

    /** {@link #stopServer()} 的别名，保持对外生命周期 API 简洁。 */
    public void stop() {
        stopServer();
    }

    public boolean isRunning() {
        return running;
    }

    public int getPort() {
        return serverPort;
    }

    /** 供 UI 展示当前房间信息（可能为 null）。 */
    public GameRoom getRoom() {
        return room;
    }

    /** 房间第二名玩家（人机模式下的 AI）是否已入座，供启动端做加入超时判定。 */
    public boolean isSecondPlayerJoined() {
        GameRoom r = room;
        return r != null && r.players[1] != null;
    }

    private void cleanup() {
        closeQuietly(serverSocket);
        closeQuietly(udpSocket);
        if (roomExecutor != null) {
            roomExecutor.shutdownNow();
        }
        serverSocket = null;
        udpSocket = null;
        roomExecutor = null;
        running = false;
        listening = false;
    }

    private static void closeQuietly(java.io.Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (IOException ignored) {
            }
        }
    }

    private static ThreadFactory daemonFactory(String name) {
        return r -> {
            Thread t = new Thread(r, name);
            t.setDaemon(true);
            return t;
        };
    }
}
