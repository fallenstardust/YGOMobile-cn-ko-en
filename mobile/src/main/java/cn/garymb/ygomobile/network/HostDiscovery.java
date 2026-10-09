package cn.garymb.ygomobile.network;

import android.util.Log;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * LAN UDP 广播发现（自 DuelClient 平移，逻辑零改）：向 255.255.255.255 发 CLIENT_ID 请求，
 * 收集 SERVER_ID 回包并解析 [version][hostPort][ip][name(20 UTF-16)]。
 * DuelClient.discoverHosts 保留一行委托，调用点不改动。
 */
class HostDiscovery implements YGOProtocol {
    // 保持拆分前日志标识，便于与旧版日志比对
    private static final String TAG = "DuelClient";

    interface HostDiscoveryListener {
        void onHostFound(String host, int port, String name, int[] hostInfo);
        void onDiscoveryComplete();
    }

    static void discoverHosts(int port, int timeoutMs, HostDiscoveryListener listener) {
        Thread thread = new Thread(() -> {
            try {
                DatagramSocket ds = new DatagramSocket();
                ds.setBroadcast(true);
                ds.setSoTimeout(timeoutMs);

                byte[] request = new byte[]{(byte) (NETWORK_CLIENT_ID & 0xFF),
                        (byte) ((NETWORK_CLIENT_ID >> 8) & 0xFF)};
                DatagramPacket sendPkt = new DatagramPacket(request, request.length,
                        InetAddress.getByName("255.255.255.255"), port);
                ds.send(sendPkt);

                byte[] recvBuf = new byte[256];
                long startTime = System.currentTimeMillis();
                while (System.currentTimeMillis() - startTime < timeoutMs) {
                    try {
                        DatagramPacket recvPkt = new DatagramPacket(recvBuf, recvBuf.length);
                        ds.receive(recvPkt);
                        ByteBuffer buf = ByteBuffer.wrap(recvPkt.getData(), 0, recvPkt.getLength());
                        buf.order(ByteOrder.LITTLE_ENDIAN);
                        if (buf.remaining() < 72) continue;

                        int identifier = buf.getShort() & 0xFFFF;
                        if (identifier != NETWORK_SERVER_ID) continue;

                        int version = buf.getShort() & 0xFFFF;
                        int hostPort = buf.getShort() & 0xFFFF;
                        buf.position(buf.position() + 2);
                        int ip = buf.getInt();
                        StringBuilder nameBuilder = new StringBuilder();
                        for (int i = 0; i < 20 && buf.remaining() >= 2; i++) {
                            char c = buf.getChar();
                            if (c == 0) {
                                buf.position(buf.position() + (19 - i) * 2);
                                break;
                            }
                            nameBuilder.append(c);
                        }
                        String hostAddr = recvPkt.getAddress().getHostAddress();
                        int[] hostInfo = new int[]{version, hostPort};
                        final String fname = nameBuilder.toString();
                        final String fhost = hostAddr;
                        if (listener != null) {
                            listener.onHostFound(fhost, hostPort, fname, hostInfo);
                        }
                    } catch (SocketTimeoutException e) {
                        break;
                    }
                }
                ds.close();
            } catch (Exception e) {
                Log.e(TAG, "Host discovery failed", e);
            } finally {
                if (listener != null) {
                    listener.onDiscoveryComplete();
                }
            }
        }, "HostDiscovery");
        thread.setDaemon(true);
        thread.start();
    }
}
