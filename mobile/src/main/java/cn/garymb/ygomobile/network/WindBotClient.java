package cn.garymb.ygomobile.network;

import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;

public class WindBotClient {
    private static final String TAG = "WindBotClient";
    private static final int WIND_BOT_PORT = 2399;
    private static final int CONNECT_TIMEOUT = 10000;

    public interface BotListener {
        void onBotConnected();
        void onBotDisconnected();
        void onBotError(String error);
    }

    private Socket socket;
    private InputStream input;
    private OutputStream output;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private BotListener listener;
    private Thread readThread;

    public void setListener(BotListener listener) {
        this.listener = listener;
    }

    public boolean isConnected() {
        return connected.get();
    }

    public boolean startBot(String host, int gamePort, String botName,
                             String deckFile, String botAI) {
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, gamePort), CONNECT_TIMEOUT);
            socket.setTcpNoDelay(true);
            input = socket.getInputStream();
            output = socket.getOutputStream();
            connected.set(true);

            sendBotHandshake(botName, deckFile, botAI);

            readThread = new Thread(this::botReadLoop, "WindBot-Read");
            readThread.setDaemon(true);
            readThread.start();

            if (listener != null) listener.onBotConnected();
            return true;
        } catch (IOException e) {
            Log.e(TAG, "Bot connect failed", e);
            if (listener != null) listener.onBotError("Bot连接失败: " + e.getMessage());
            return false;
        }
    }

    private void sendBotHandshake(String botName, String deckFile, String botAI) throws IOException {
        ByteBuffer buf = ByteBuffer.allocate(0x10000);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.putShort((short) 0);
        buf.put((byte) YGOProtocol.CTOS_PLAYER_INFO);
        BufferIO.writeUTF16(buf, botName, 20);
        int pos = buf.position();
        int dataLen = pos - 2;
        buf.putShort(0, (short) dataLen);

        byte[] packet = new byte[pos];
        buf.flip();
        buf.get(packet);
        output.write(packet);
        output.flush();
    }

    private void botReadLoop() {
        byte[] headerBuf = new byte[2];
        try {
            while (connected.get()) {
                int read = readFully(input, headerBuf, 0, 2);
                if (read < 2) break;

                int packetLen = (headerBuf[0] & 0xFF) | ((headerBuf[1] & 0xFF) << 8);
                if (packetLen <= 0 || packetLen > 0x20000) break;

                byte[] data = new byte[packetLen];
                read = readFully(input, data, 0, packetLen);
                if (read < packetLen) break;

                handleBotPacket(data);
            }
        } catch (Exception e) {
            if (connected.get()) {
                Log.e(TAG, "Bot read error", e);
            }
        } finally {
            disconnect();
            if (listener != null) listener.onBotDisconnected();
        }
    }

    private void handleBotPacket(byte[] data) {
        ByteBuffer buf = ByteBuffer.wrap(data);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        int proto = buf.get() & 0xFF;
        // Bot AI logic here - forward game messages to AI engine
    }

    private int readFully(InputStream is, byte[] buf, int off, int len) throws IOException {
        int total = 0;
        while (total < len) {
            int r = is.read(buf, off + total, len - total);
            if (r < 0) return total;
            total += r;
        }
        return total;
    }

    public void sendResponse(byte[] responseData) {
        if (!connected.get() || output == null) return;
        try {
            ByteBuffer buf = BufferIO.createPacket(YGOProtocol.CTOS_RESPONSE);
            buf.put(responseData);
            byte[] packet = BufferIO.finalizePacket(buf);
            synchronized (this) {
                output.write(packet);
                output.flush();
            }
        } catch (IOException e) {
            Log.e(TAG, "Bot send response failed", e);
        }
    }

    public void sendUpdateDeck(List<Integer> main, List<Integer> extra, List<Integer> side) {
        if (!connected.get() || output == null) return;
        try {
            ByteBuffer buf = BufferIO.createPacket(YGOProtocol.CTOS_UPDATE_DECK);
            buf.putInt(main.size());
            buf.putInt(side.size());
            for (int code : main) buf.putInt(code);
            for (int code : extra) buf.putInt(code);
            for (int code : side) buf.putInt(code);
            byte[] packet = BufferIO.finalizePacket(buf);
            synchronized (this) {
                output.write(packet);
                output.flush();
            }
        } catch (IOException e) {
            Log.e(TAG, "Bot send deck failed", e);
        }
    }

    public void disconnect() {
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
    }

    public static String getBotDeckPath() {
        return AppsSettings.get().getResourcePath() + "/"
                + Constants.WINDBOT_PATH + "/" + Constants.WINDBOT_DECK_PATH;
    }

    public static File getBotConfig() {
        return new File(AppsSettings.get().getResourcePath() + "/"
                + Constants.WINDBOT_PATH, Constants.BOT_CONF);
    }
}
