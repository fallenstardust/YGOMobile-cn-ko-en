package cn.garymb.ygomobile.network;

import android.content.Context;
import android.net.wifi.WifiManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.GameApplication;

public class LanDiscoveryManager implements YGOProtocol {
    private static final String TAG = "LanDiscoveryManager";

    private static final int BROADCAST_SEND_PORT = 7920;
    private static final int BROADCAST_RECV_PORT = 7921;
    private static final int DISCOVERY_TIMEOUT_MS = 3000;
    private static final int PRO_VERSION = 0x1362;
    private static final int DEFAULT_DUEL_RULE = 5;

    public static class HostEntry {
        public String ip;
        public int port;
        public String roomName;
        public int version;
        public int lflist;
        public int rule;
        public int mode;
        public int duelRule;
        public int noCheckDeck;
        public int noShuffleDeck;
        public int startLp;
        public int startHand;
        public int drawCount;
        public int timeLimit;

        public String getDisplayText() {
            String banlistName = lflist != 0 ? "Banlist#" + lflist : "N/A";
            String ruleName = getRuleName(rule);
            String modeName = getModeName(mode);
            boolean isDefault = (drawCount == 1 && startHand == 5 && startLp == 8000
                    && noCheckDeck == 0 && noShuffleDeck == 0 && duelRule == DEFAULT_DUEL_RULE);
            String ruleSetting = isDefault ? "默认规则" : "自定义规则";
            return "[" + banlistName + "][" + ruleName + "][" + modeName + "][" + ruleSetting + "]" + roomName;
        }

        public String getDetailText() {
            StringBuilder sb = new StringBuilder();
            sb.append(roomName);
            sb.append(" | ").append(ip).append(":").append(port);
            sb.append(" | LP:").append(startLp);
            sb.append(" | ").append(getModeName(mode));
            if (timeLimit > 0) {
                sb.append(" | ").append(timeLimit).append("s");
            }
            return sb.toString();
        }

        private static String getRuleName(int rule) {
            switch (rule) {
                case 0:
                    return "大师规则4";
                case 1:
                    return "大师规则2020";
                case 2:
                    return "新大师规则";
                case 3:
                    return "大师规则";
                default:
                    return "规则" + rule;
            }
        }

        private static String getModeName(int mode) {
            switch (mode) {
                case 0:
                    return "单局模式";
                case 1:
                    return "三局两胜";
                case 2:
                    return "TAG";
                default:
                    return "未知模式";
            }
        }
    }

    public interface DiscoveryListener {
        void onDiscoveryStarted();

        void onHostFound(HostEntry host);

        void onDiscoveryFinished();

        void onDiscoveryError(String message);
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final AtomicBoolean isDiscovering = new AtomicBoolean(false);
    private Thread discoveryThread;

    public boolean isDiscovering() {
        return isDiscovering.get();
    }

    private static WifiManager.MulticastLock sMulticastLock;

    /**
     * 建立局域网主机时必须持有 MulticastLock，否则 Android 的 Wi-Fi 驱动会过滤掉
     * 客户端发来的广播 HostRequest（目的地址 255.255.255.255），
     * 导致 native NetServer 绑定的 UDP 7920 收不到发现请求、无法回复，主机因此不可被发现。
     */
    public static synchronized void acquireHostMulticastLock() {
        try {
            if (sMulticastLock != null && sMulticastLock.isHeld()) return;
            Context ctx = GameApplication.get();
            if (ctx == null) return;
            WifiManager wm = (WifiManager) ctx.getApplicationContext()
                    .getSystemService(Context.WIFI_SERVICE);
            if (wm == null) return;
            sMulticastLock = wm.createMulticastLock("ygo-lan-host");
            sMulticastLock.setReferenceCounted(false);
            sMulticastLock.acquire();
            Log.i(TAG, "MulticastLock acquired for LAN host broadcast");
        } catch (Exception e) {
            Log.w(TAG, "acquireHostMulticastLock failed", e);
        }
    }

    public static synchronized void releaseHostMulticastLock() {
        try {
            if (sMulticastLock != null && sMulticastLock.isHeld()) {
                sMulticastLock.release();
                Log.i(TAG, "MulticastLock released");
            }
            sMulticastLock = null;
        } catch (Exception e) {
            Log.w(TAG, "releaseHostMulticastLock failed", e);
        }
    }

    /**
     * 获取本机在局域网中的 IPv4 地址，供建立主机后向其他玩家展示，
     * 以便在 UDP 广播可被……路由器手动输入连接。
     */
    public static String getLocalIpAddress() {
        try {
            for (Enumeration<NetworkInterface> en = NetworkInterface.getNetworkInterfaces();
                 en.hasMoreElements(); ) {
                NetworkInterface intf = en.nextElement();
                if (!intf.isUp() || intf.isLoopback()) continue;
                for (Enumeration<InetAddress> addrs = intf.getInetAddresses();
                     addrs.hasMoreElements(); ) {
                    InetAddress addr = addrs.nextElement();
                    if (!addr.isLoopbackAddress() && addr instanceof Inet4Address) {
                        return addr.getHostAddress();
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "getLocalIpAddress failed", e);
        }
        return null;
    }

    public void startDiscovery(DiscoveryListener listener) {
        if (isDiscovering.compareAndSet(false, true)) {
            mainHandler.post(() -> {
                if (listener != null) listener.onDiscoveryStarted();
            });
            discoveryThread = new Thread(() -> runDiscovery(listener), "LanDiscovery");
            discoveryThread.setDaemon(true);
            discoveryThread.start();
        }
    }

    public void stopDiscovery() {
        isDiscovering.set(false);
        if (discoveryThread != null) {
            discoveryThread.interrupt();
            discoveryThread = null;
        }
    }

    private void runDiscovery(DiscoveryListener listener) {
        DatagramSocket recvSocket = null;
        DatagramSocket sendSocket = null;
        try {
            recvSocket = new DatagramSocket(BROADCAST_RECV_PORT);
            recvSocket.setSoTimeout(DISCOVERY_TIMEOUT_MS);

            Set<String> foundHosts = new HashSet<>();
            List<HostEntry> results = new ArrayList<>();

            sendSocket = new DatagramSocket();
            sendSocket.setBroadcast(true);

            byte[] request = new byte[2];
            request[0] = (byte) (NETWORK_CLIENT_ID & 0xFF);
            request[1] = (byte) ((NETWORK_CLIENT_ID >> 8) & 0xFF);
            DatagramPacket sendPkt = new DatagramPacket(request, request.length,
                    InetAddress.getByName("255.255.255.255"), BROADCAST_SEND_PORT);
            sendSocket.send(sendPkt);
            Log.i(TAG, "Broadcast request sent to port " + BROADCAST_SEND_PORT);

            byte[] recvBuf = new byte[256];
            long startTime = System.currentTimeMillis();

            while (isDiscovering.get() && (System.currentTimeMillis() - startTime) < DISCOVERY_TIMEOUT_MS) {
                try {
                    DatagramPacket recvPkt = new DatagramPacket(recvBuf, recvBuf.length);
                    recvSocket.receive(recvPkt);

                    if (recvPkt.getLength() < 72) {
                        Log.w(TAG, "Packet too short: " + recvPkt.getLength());
                        continue;
                    }

                    HostEntry entry = parseHostPacket(recvPkt.getData(), recvPkt.getLength(),
                            recvPkt.getAddress());
                    if (entry == null) continue;

                    String hostKey = entry.ip + ":" + entry.port;
                    if (foundHosts.contains(hostKey)) continue;

                    foundHosts.add(hostKey);
                    results.add(entry);
                    Log.i(TAG, "Found host: " + hostKey + " [" + entry.roomName + "]");

                    final HostEntry foundEntry = entry;
                    mainHandler.post(() -> {
                        if (listener != null) listener.onHostFound(foundEntry);
                    });
                } catch (SocketTimeoutException e) {
                    break;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Discovery error", e);
            mainHandler.post(() -> {
                if (listener != null) listener.onDiscoveryError("发现失败: " + e.getMessage());
            });
        } finally {
            if (recvSocket != null && !recvSocket.isClosed()) recvSocket.close();
            if (sendSocket != null && !sendSocket.isClosed()) sendSocket.close();
            isDiscovering.set(false);
            mainHandler.post(() -> {
                if (listener != null) listener.onDiscoveryFinished();
            });
        }
    }

    private HostEntry parseHostPacket(byte[] data, int length, InetAddress senderAddr) {
        if (length < 72) return null;

        ByteBuffer buf = ByteBuffer.wrap(data, 0, length);
        buf.order(ByteOrder.LITTLE_ENDIAN);

        int identifier = buf.getShort() & 0xFFFF;
        if (identifier != NETWORK_SERVER_ID) {
            return null;
        }

        int version = buf.getShort() & 0xFFFF;
        if (version != Constants.PRO_VERSION) {
            Log.w(TAG, "Version mismatch: " + String.format("0x%04X", version));
            return null;
        }

        int port = buf.getShort() & 0xFFFF;
        buf.position(buf.position() + 2);
        buf.getInt();

        StringBuilder nameBuilder = new StringBuilder();
        for (int i = 0; i < 20 && buf.remaining() >= 2; i++) {
            char c = buf.getChar();
            if (c == 0) {
                buf.position(buf.position() + (19 - i) * 2);
                break;
            }
            nameBuilder.append(c);
        }

        if (buf.remaining() < 20) return null;

        int lflist = buf.getInt();
        int rule = buf.get() & 0xFF;
        int mode = buf.get() & 0xFF;
        int duelRule = buf.get() & 0xFF;
        int noCheckDeck = buf.get() & 0xFF;
        int noShuffleDeck = buf.get() & 0xFF;
        buf.position(buf.position() + 2);
        int startLp = buf.getInt();
        int startHand = buf.get() & 0xFF;
        int drawCount = buf.get() & 0xFF;
        int timeLimit = buf.getShort() & 0xFFFF;

        HostEntry entry = new HostEntry();
        entry.ip = senderAddr.getHostAddress();
        entry.port = port;
        entry.version = version;
        entry.roomName = nameBuilder.toString();
        entry.lflist = lflist;
        entry.rule = rule;
        entry.mode = mode;
        entry.duelRule = duelRule;
        entry.noCheckDeck = noCheckDeck;
        entry.noShuffleDeck = noShuffleDeck;
        entry.startLp = startLp;
        entry.startHand = startHand;
        entry.drawCount = drawCount;
        entry.timeLimit = timeLimit;
        return entry;
    }
}