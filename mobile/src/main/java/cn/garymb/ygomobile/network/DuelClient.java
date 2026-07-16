package cn.garymb.ygomobile.network;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.utils.LogUtil;

public class DuelClient implements YGOProtocol {
    private static final String TAG = "DuelClient";
    private static final int CONNECT_TIMEOUT = 5000;
    private static final int BUFFER_SIZE = 0x20000;

    public interface ClientListener {
        void onConnected();
        void onDisconnected();
        void onError(String message);
        void onPacketReceived(int proto, ByteBuffer data);
        void onChatMessage(String player, String message);
        void onPlayerEnter(String name, int pos);
        void onPlayerChange(int status);
        void onWatchChange(int watchCount);
        void onDuelStart();
        void onDuelEnd();
        void onGameMsg(int msgType, ByteBuffer data);
        void onHandSelect();
        void onTPSelect();
        void onHandResult(int res1, int res2);
        void onChangeSide();
        void onWaitingSide();
        void onTimeLimit(int player, int leftTime);
        void onErrorMsg(int msg, int code);
        void onTypeChange(int type);
        void onJoinGame(int lflist, int rule, int mode, int duelRule,
                        int noCheckDeck, int noShuffleDeck,
                        int startLp, int startHand, int drawCount, int timeLimit);
    }

    private Socket socket;
    private InputStream input;
    private OutputStream output;
    private Thread readThread;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private ClientListener listener;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService sendExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "DuelClient-Send");
        t.setDaemon(true);
        return t;
    });

    public int selfType = -1;

    public void setListener(ClientListener listener) {
        this.listener = listener;
    }

    public boolean isConnected() {
        return connected.get();
    }

    public boolean connect(String host, int port) {
        if (connected.get()) {
            disconnect();
        }
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT);
            socket.setTcpNoDelay(true);
            socket.setKeepAlive(true);
            input = socket.getInputStream();
            output = socket.getOutputStream();
            connected.set(true);
            running.set(true);

            readThread = new Thread(this::readLoop, "DuelClient-Read");
            readThread.setDaemon(true);
            readThread.start();

            mainHandler.post(() -> {
                if (listener != null) listener.onConnected();
            });
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Connect failed", e);
            mainHandler.post(() -> {
                if (listener != null) listener.onError("连接失败: " + e.getMessage());
            });
            return false;
        }
    }

    public void disconnect() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        connected.set(false);
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (IOException e) {
            // ignore
        }
        socket = null;
        input = null;
        output = null;
        mainHandler.post(() -> {
            if (listener != null) listener.onDisconnected();
        });
    }

    private void readLoop() {
        byte[] headerBuf = new byte[2];
        try {
            while (running.get()) {
                int read = readFully(input, headerBuf, 0, 2);
                if (read < 2) break;

                int packetLen = (headerBuf[0] & 0xFF) | ((headerBuf[1] & 0xFF) << 8);
                if (packetLen <= 0 || packetLen > BUFFER_SIZE) {
                    Log.e(TAG, "Invalid packet length: " + packetLen);
                    break;
                }

                byte[] data = new byte[packetLen];
                read = readFully(input, data, 0, packetLen);
                if (read < packetLen) break;

                handlePacket(data);
            }
        } catch (java.net.SocketException e) {
            if (running.get()) {
                Log.w(TAG, "Connection aborted by remote: " + e.getMessage());
            }
        } catch (java.io.EOFException e) {
            if (running.get()) {
                Log.w(TAG, "Connection closed by remote (EOF)");
            }
        } catch (Exception e) {
            if (running.get()) {
                Log.e(TAG, "Read loop error", e);
            }
        } finally {
            if (running.get()) {
                disconnect();
            }
        }
    }

    private int readFully(InputStream is, byte[] buf, int off, int len) throws IOException {
        int total = 0;
        while (total < len) {
            int r = is.read(buf, off + total, len - total);
            if (r < 0) {
                if (total == 0) return -1;
                return total;
            }
            total += r;
        }
        return total;
    }

    private void handlePacket(byte[] data) {
        ByteBuffer buf = ByteBuffer.wrap(data);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        int proto = buf.get() & 0xFF;

        if (Constants.DEBUG) {
            LogUtil.d(TAG, "◀ RECV [" + stocName(proto) + " 0x" + Integer.toHexString(proto) + "] len=" + data.length + " data=" + bytesToHex(data));
        }

        mainHandler.post(() -> {
            if (listener == null) return;
            switch (proto) {
                case STOC_GAME_MSG:
                    handleGameMsg(buf);
                    break;
                case STOC_ERROR_MSG:
                    handleErrorMsg(buf);
                    break;
                case STOC_SELECT_HAND:
                    listener.onHandSelect();
                    break;
                case STOC_SELECT_TP:
                    listener.onTPSelect();
                    break;
                case STOC_HAND_RESULT:
                    listener.onHandResult(buf.get() & 0xFF, buf.get() & 0xFF);
                    break;
                case STOC_TYPE_CHANGE:
                    handleTypeChange(buf);
                    break;
                case STOC_JOIN_GAME:
                    handleJoinGame(buf);
                    break;
                case STOC_DUEL_START:
                    listener.onDuelStart();
                    break;
                case STOC_DUEL_END:
                    listener.onDuelEnd();
                    break;
                case STOC_CHANGE_SIDE:
                    listener.onChangeSide();
                    break;
                case STOC_WAITING_SIDE:
                    listener.onWaitingSide();
                    break;
                case STOC_TIME_LIMIT:
                    handleTimeLimit(buf);
                    break;
                case STOC_CHAT:
                    handleChat(buf);
                    break;
                case STOC_HS_PLAYER_ENTER:
                    handlePlayerEnter(buf);
                    break;
                case STOC_HS_PLAYER_CHANGE:
                    listener.onPlayerChange(buf.get() & 0xFF);
                    break;
                case STOC_HS_WATCH_CHANGE:
                    handleWatchChange(buf);
                    break;
                default:
                    listener.onPacketReceived(proto, buf);
                    break;
            }
        });
    }

    private void handleGameMsg(ByteBuffer buf) {
        if (buf.remaining() < 1) return;
        int msgType = buf.get() & 0xFF;
        if (listener != null) {
            listener.onGameMsg(msgType, buf);
        }
    }

    private void handleErrorMsg(ByteBuffer buf) {
        if (buf.remaining() < 5) return;
        int msg = buf.get() & 0xFF;
        buf.position(buf.position() + 3);
        int code = buf.getInt();
        if (listener != null) {
            listener.onErrorMsg(msg, code);
        }
    }

    private void handleTypeChange(ByteBuffer buf) {
        if (buf.remaining() < 1) return;
        selfType = buf.get() & 0xFF;
        if (listener != null) {
            listener.onTypeChange(selfType);
        }
    }

    private void handleJoinGame(ByteBuffer buf) {
        if (buf.remaining() < 20) return;
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
        if (listener != null) {
            listener.onJoinGame(lflist, rule, mode, duelRule,
                    noCheckDeck, noShuffleDeck,
                    startLp, startHand, drawCount, timeLimit);
        }
    }

    private void handleTimeLimit(ByteBuffer buf) {
        if (buf.remaining() < 4) return;
        int player = buf.get() & 0xFF;
        buf.position(buf.position() + 1);
        int leftTime = buf.getShort() & 0xFFFF;
        if (listener != null) {
            listener.onTimeLimit(player, leftTime);
        }
    }

    private void handleChat(ByteBuffer buf) {
        if (buf.remaining() < 2) return;
        int playerType = buf.getShort() & 0xFFFF;
        int remaining = buf.remaining();
        if (remaining < 2) return;
        int charCount = remaining / 2;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < charCount && buf.remaining() >= 2; i++) {
            char c = buf.getChar();
            if (c == 0) break;
            sb.append(c);
        }
        String player = playerType < 2 ? "Player" + (playerType + 1) : "Observer";
        if (listener != null) {
            listener.onChatMessage(player, sb.toString());
        }
    }

    private void handlePlayerEnter(ByteBuffer buf) {
        if (buf.remaining() < 41) return;
        StringBuilder nameBuilder = new StringBuilder();
        for (int i = 0; i < 20 && buf.remaining() >= 2; i++) {
            char c = buf.getChar();
            if (c == 0) {
                buf.position(buf.position() + (19 - i) * 2);
                break;
            }
            nameBuilder.append(c);
        }
        int pos = buf.get() & 0xFF;
        if (listener != null) {
            listener.onPlayerEnter(nameBuilder.toString(), pos);
        }
    }

    private void handleWatchChange(ByteBuffer buf) {
        if (buf.remaining() < 2) return;
        int watchCount = buf.getShort() & 0xFFFF;
        if (listener != null) {
            listener.onWatchChange(watchCount);
        }
    }

    // === Send methods ===

    public void sendExternalAddress(String address) {
        ByteBuffer buf = BufferIO.createPacket(CTOS_EXTERNAL_ADDRESS);
        buf.position(buf.position() + 4);
        BufferIO.writeUTF16(buf, address, address.length() * 2);
        sendRaw(BufferIO.finalizePacket(buf));
    }

    public void sendPlayerInfo(String playerName) {
        ByteBuffer buf = BufferIO.createPacket(CTOS_PLAYER_INFO);
        BufferIO.writeUTF16(buf, playerName, 40);
        sendRaw(BufferIO.finalizePacket(buf));
    }

    public void sendCreateGame(int lflist, int rule, int mode, int duelRule,
                                boolean noCheckDeck, boolean noShuffleDeck,
                                int startLp, int startHand, int drawCount, int timeLimit,
                                String name, String pass) {
        ByteBuffer buf = BufferIO.createPacket(CTOS_CREATE_GAME);
        buf.putInt(lflist);
        buf.put((byte) rule);
        buf.put((byte) mode);
        buf.put((byte) duelRule);
        buf.put((byte) (noCheckDeck ? 1 : 0));
        buf.put((byte) (noShuffleDeck ? 1 : 0));
        buf.position(buf.position() + 2);
        buf.putInt(startLp);
        buf.put((byte) startHand);
        buf.put((byte) drawCount);
        buf.putShort((short) timeLimit);
        BufferIO.writeUTF16(buf, name, 20);
        BufferIO.writeUTF16(buf, pass, 20);
        sendRaw(BufferIO.finalizePacket(buf));
    }

    public void sendJoinGame(int version, String pass) {
        ByteBuffer buf = BufferIO.createPacket(CTOS_JOIN_GAME);
        buf.putShort((short) version);
        buf.position(buf.position() + 6);
        BufferIO.writeUTF16(buf, pass, 40);
        sendRaw(BufferIO.finalizePacket(buf));
    }

    public void sendHandResult(int result) {
        ByteBuffer buf = BufferIO.createPacket(CTOS_HAND_RESULT);
        buf.put((byte) result);
        sendRaw(BufferIO.finalizePacket(buf));
    }

    public void sendTPResult(boolean chooseFirst) {
        ByteBuffer buf = BufferIO.createPacket(CTOS_TP_RESULT);
        buf.put((byte) (chooseFirst ? 1 : 0));
        sendRaw(BufferIO.finalizePacket(buf));
    }

    public void sendUpdateDeck(List<Integer> main, List<Integer> extra, List<Integer> side) {
        ByteBuffer buf = BufferIO.createPacket(CTOS_UPDATE_DECK);
        buf.putInt(main.size() + extra.size());
        buf.putInt(side.size());
        for (int code : main) buf.putInt(code);
        for (int code : extra) buf.putInt(code);
        for (int code : side) buf.putInt(code);
        sendRaw(BufferIO.finalizePacket(buf));
    }

    public void sendResponse(byte[] responseData) {
        ByteBuffer buf = BufferIO.createPacket(CTOS_RESPONSE);
        buf.put(responseData);
        sendRaw(BufferIO.finalizePacket(buf));
    }

    public void sendSurrender() {
        ByteBuffer buf = BufferIO.createPacket(CTOS_SURRENDER);
        sendRaw(BufferIO.finalizePacket(buf));
    }

    public void sendLeaveGame() {
        ByteBuffer buf = BufferIO.createPacket(CTOS_LEAVE_GAME);
        sendRaw(BufferIO.finalizePacket(buf));
    }

    public void sendChat(String message) {
        ByteBuffer buf = BufferIO.createPacket(CTOS_CHAT);
        byte[] bytes = message.getBytes(StandardCharsets.UTF_16LE);
        int charCount = Math.min(message.length(), 255);
        for (int i = 0; i < charCount; i++) {
            buf.putChar(message.charAt(i));
        }
        buf.putChar('\0');
        sendRaw(BufferIO.finalizePacket(buf));
    }

    public void sendReady() {
        ByteBuffer buf = BufferIO.createPacket(CTOS_HS_READY);
        sendRaw(BufferIO.finalizePacket(buf));
    }

    public void sendNotReady() {
        ByteBuffer buf = BufferIO.createPacket(CTOS_HS_NOTREADY);
        sendRaw(BufferIO.finalizePacket(buf));
    }

    public void sendToDuelist() {
        ByteBuffer buf = BufferIO.createPacket(CTOS_HS_TODUELIST);
        sendRaw(BufferIO.finalizePacket(buf));
    }

    public void sendToObserver() {
        ByteBuffer buf = BufferIO.createPacket(CTOS_HS_TOOBSERVER);
        sendRaw(BufferIO.finalizePacket(buf));
    }

    public void sendKick(int pos) {
        ByteBuffer buf = BufferIO.createPacket(CTOS_HS_KICK);
        buf.put((byte) pos);
        sendRaw(BufferIO.finalizePacket(buf));
    }

    public void sendStart() {
        ByteBuffer buf = BufferIO.createPacket(CTOS_HS_START);
        sendRaw(BufferIO.finalizePacket(buf));
    }

    public void sendTimeConfirm() {
        ByteBuffer buf = BufferIO.createPacket(CTOS_TIME_CONFIRM);
        sendRaw(BufferIO.finalizePacket(buf));
    }

    private void sendRaw(byte[] data) {
        if (!connected.get() || output == null) return;

        if (Constants.DEBUG) {
            int proto = (data.length > 0) ? (data[0] & 0xFF) : -1;
            LogUtil.d(TAG, "▶ SEND [" + ctosName(proto) + " 0x" + Integer.toHexString(proto >= 0 ? proto : 0) + "] len=" + data.length + " data=" + bytesToHex(data));
        }

        sendExecutor.execute(() -> {
            try {
                synchronized (this) {
                    output.write(data);
                    output.flush();
                }
            } catch (IOException e) {
                Log.e(TAG, "Send failed", e);
                disconnect();
            }
        });
    }

    private static String bytesToHex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return "";
        StringBuilder sb = new StringBuilder();
        int limit = Math.min(bytes.length, 128);
        for (int i = 0; i < limit; i++) {
            if (i > 0) sb.append(' ');
            sb.append(String.format("%02X", bytes[i] & 0xFF));
        }
        if (bytes.length > 128) sb.append("...");
        return sb.toString();
    }

    private static String stocName(int proto) {
        switch (proto) {
            case STOC_GAME_MSG: return "STOC_GAME_MSG";
            case STOC_ERROR_MSG: return "STOC_ERROR_MSG";
            case STOC_SELECT_HAND: return "STOC_SELECT_HAND";
            case STOC_SELECT_TP: return "STOC_SELECT_TP";
            case STOC_HAND_RESULT: return "STOC_HAND_RESULT";
            case STOC_CHANGE_SIDE: return "STOC_CHANGE_SIDE";
            case STOC_WAITING_SIDE: return "STOC_WAITING_SIDE";
            case STOC_CREATE_GAME: return "STOC_CREATE_GAME";
            case STOC_JOIN_GAME: return "STOC_JOIN_GAME";
            case STOC_TYPE_CHANGE: return "STOC_TYPE_CHANGE";
            case STOC_DUEL_START: return "STOC_DUEL_START";
            case STOC_DUEL_END: return "STOC_DUEL_END";
            case STOC_REPLAY: return "STOC_REPLAY";
            case STOC_TIME_LIMIT: return "STOC_TIME_LIMIT";
            case STOC_CHAT: return "STOC_CHAT";
            case STOC_HS_PLAYER_ENTER: return "STOC_HS_PLAYER_ENTER";
            case STOC_HS_PLAYER_CHANGE: return "STOC_HS_PLAYER_CHANGE";
            case STOC_HS_WATCH_CHANGE: return "STOC_HS_WATCH_CHANGE";
            default: return "UNKNOWN_STOC";
        }
    }

    private static String ctosName(int proto) {
        switch (proto) {
            case CTOS_RESPONSE: return "CTOS_RESPONSE";
            case CTOS_UPDATE_DECK: return "CTOS_UPDATE_DECK";
            case CTOS_HAND_RESULT: return "CTOS_HAND_RESULT";
            case CTOS_TP_RESULT: return "CTOS_TP_RESULT";
            case CTOS_PLAYER_INFO: return "CTOS_PLAYER_INFO";
            case CTOS_CREATE_GAME: return "CTOS_CREATE_GAME";
            case CTOS_JOIN_GAME: return "CTOS_JOIN_GAME";
            case CTOS_LEAVE_GAME: return "CTOS_LEAVE_GAME";
            case CTOS_SURRENDER: return "CTOS_SURRENDER";
            case CTOS_TIME_CONFIRM: return "CTOS_TIME_CONFIRM";
            case CTOS_CHAT: return "CTOS_CHAT";
            case CTOS_EXTERNAL_ADDRESS: return "CTOS_EXTERNAL_ADDRESS";
            case CTOS_HS_TODUELIST: return "CTOS_HS_TODUELIST";
            case CTOS_HS_TOOBSERVER: return "CTOS_HS_TOOBSERVER";
            case CTOS_HS_READY: return "CTOS_HS_READY";
            case CTOS_HS_NOTREADY: return "CTOS_HS_NOTREADY";
            case CTOS_HS_KICK: return "CTOS_HS_KICK";
            case CTOS_HS_START: return "CTOS_HS_START";
            default: return "UNKNOWN_CTOS";
        }
    }

    // === LAN Discovery ===

    public interface HostDiscoveryListener {
        void onHostFound(String host, int port, String name, int[] hostInfo);
        void onDiscoveryComplete();
    }

    public static void discoverHosts(int port, int timeoutMs, HostDiscoveryListener listener) {
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
