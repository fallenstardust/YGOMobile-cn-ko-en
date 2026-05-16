package cn.garymb.ygomobile.ui.mycard.watchDuel;

import android.text.TextUtils;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import org.java_websocket.enums.ReadyState;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONException;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.ui.mycard.MyCard;
import cn.garymb.ygomobile.ui.mycard.base.OnDuelRoomListener;
import cn.garymb.ygomobile.ui.mycard.bean.DuelRoom;
import cn.garymb.ygomobile.ui.mycard.bean.YGOServer;
import cn.garymb.ygomobile.utils.HandlerUtil;
import cn.garymb.ygomobile.utils.JsonUtil;
import cn.garymb.ygomobile.utils.LogUtil;

/**
 * Create By feihua  On 2021/11/3
 * Converted from Kotlin to Java
 * Management for waiting duel rooms
 */
public class WaitingDuelManagement {

    private static final String TAG = WaitingDuelManagement.class.getSimpleName();
    private static final int HANDLE_SEND_MESSAGE = 0;
    private static final long CLOSE_RECON_TIME = 1000;
    private static final long HEART_BEAT_RATE = 10 * 1000;

    private static volatile WaitingDuelManagement instance;

    private ArrayList<OnDuelRoomListener> onDuelRoomListenerList;
    private boolean isStart;
    private ArrayList<McWatchDuelSocketClient> mcWatchDuelSocketClientList;
    private final ArrayList<DuelRoom> rooms;
    private Handler handler;
    private Runnable heartBeatRunnable;

    private WaitingDuelManagement() {
        onDuelRoomListenerList = new ArrayList<>();
        isStart = false;

        mcWatchDuelSocketClientList = new ArrayList<>();
        rooms = new ArrayList<>();

        initHandler();
        initHeartBeatRunnable();
    }

    public static WaitingDuelManagement getInstance() {
        if (instance == null) {
            synchronized (WaitingDuelManagement.class) {
                if (instance == null) {
                    instance = new WaitingDuelManagement();
                }
            }
        }
        return instance;
    }

    private void initHandler() {
        handler = new Handler(Looper.getMainLooper()) {
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);

                switch (msg.what) {
                    case HANDLE_SEND_MESSAGE:
                        RoomSocketMessage socketMessage = (RoomSocketMessage) msg.obj;
                        try {
                            List<DuelRoom> duelRoomList = JsonUtil.getDuelRoomList(socketMessage.message, socketMessage.server);
                            String event = JsonUtil.getDuelRoomEvent(socketMessage.message);
                            applyRoomEvent(event, duelRoomList, socketMessage.server);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        break;
                }
            }
        };
    }

    private void initHeartBeatRunnable() {
        heartBeatRunnable = new Runnable() {
            @Override
            public void run() {
                int i = 0;
                while (i < mcWatchDuelSocketClientList.size()) {
                    McWatchDuelSocketClient client = mcWatchDuelSocketClientList.get(i);

                    if (client.isClosed()) {
                        reconnectWs(client);
                        LogUtil.e(TAG, "心跳包检测WebSocket连接状态：已关闭 " + client.getUri());
                    } else if (client.isOpen()) {
                        LogUtil.d(TAG, "心跳包检测WebSocket连接状态：已连接 " + client.getUri());
                    } else {
                        LogUtil.e(TAG, "心跳包检测WebSocket连接状态：已断开 " + client.getUri());
                    }

                    i++;
                }

                handler.postDelayed(this, HEART_BEAT_RATE);
            }
        };
    }

    public void addListener(OnDuelRoomListener onDuelRoomListener) {
        onDuelRoomListenerList.add(onDuelRoomListener);
    }

    public void removeListener(OnDuelRoomListener onDuelRoomListener) {
        onDuelRoomListenerList.remove(onDuelRoomListener);
    }

    private void onDuelRoomList(String event, List<DuelRoom> duelRoomList) {
        int i = 0;
        while (i < onDuelRoomListenerList.size()) {
            OnDuelRoomListener ul = onDuelRoomListenerList.get(i);
            if (ul.isListenerEffective()) {
                switch (event) {
                    case DuelRoom.EVENT_INIT:
                        ul.onInit(duelRoomList);
                        break;
                    case DuelRoom.EVENT_CREATE:
                        ul.onCreate(duelRoomList);
                        break;
                    case DuelRoom.EVENT_UPDATE:
                        ul.onUpdate(duelRoomList);
                        break;
                    case DuelRoom.EVENT_DELETE:
                        ul.onDelete(duelRoomList);
                        break;
                }
            } else {
                onDuelRoomListenerList.remove(i);
                i--;
            }
            i++;
        }
    }

    private void applyRoomEvent(String event, List<DuelRoom> duelRoomList, YGOServer server) {
        switch (event) {
            case DuelRoom.EVENT_INIT:
                removeRoomsFromServer(server);
                upsertRooms(duelRoomList);
                break;
            case DuelRoom.EVENT_CREATE:
            case DuelRoom.EVENT_UPDATE:
                upsertRooms(duelRoomList);
                break;
            case DuelRoom.EVENT_DELETE:
                removeRooms(duelRoomList);
                break;
        }
        onDuelRoomList(DuelRoom.EVENT_INIT, new ArrayList<>(rooms));
    }

    private void upsertRooms(List<DuelRoom> duelRoomList) {
        if (duelRoomList == null) {
            return;
        }
        for (DuelRoom room : duelRoomList) {
            removeRoom(room);
            rooms.add(room);
        }
    }

    private void removeRooms(List<DuelRoom> duelRoomList) {
        if (duelRoomList == null) {
            return;
        }
        for (DuelRoom room : duelRoomList) {
            removeRoom(room);
        }
    }

    private void removeRoom(DuelRoom room) {
        for (int i = rooms.size() - 1; i >= 0; i--) {
            if (isSameRoom(rooms.get(i), room)) {
                rooms.remove(i);
            }
        }
    }

    private void removeRoomsFromServer(YGOServer server) {
        for (int i = rooms.size() - 1; i >= 0; i--) {
            if (isSameServer(rooms.get(i).getServer(), server)) {
                rooms.remove(i);
            }
        }
    }

    private boolean isSameRoom(DuelRoom left, DuelRoom right) {
        if (left == null || right == null || TextUtils.isEmpty(left.getId()) || TextUtils.isEmpty(right.getId())) {
            return false;
        }
        return left.getId().equals(right.getId()) && isSameServer(left.getServer(), right.getServer());
    }

    private boolean isSameServer(YGOServer left, YGOServer right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        if (!TextUtils.isEmpty(left.getId()) && !TextUtils.isEmpty(right.getId())) {
            return left.getId().equals(right.getId());
        }
        return TextUtils.equals(left.getServerAddr(), right.getServerAddr()) && left.getPort() == right.getPort();
    }

    public void start() {
        if (isStart) return;
        isStart = true;
        if (!mcWatchDuelSocketClientList.isEmpty()) {
            isStart = false;
            return;
        }

        new Thread(new Runnable() {
            @Override
            public void run() {
                List<YGOServer> servers;
                try {
                    servers = MyCard.getCustomServers();
                } catch (Exception e) {
                    LogUtil.e(TAG, "加载自定义房间服务器失败：" + e.getMessage());
                    servers = new ArrayList<>();
                    servers.add(MyCard.getDefaultCustomServer(null));
                }

                List<YGOServer> finalServers = servers;
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (!mcWatchDuelSocketClientList.isEmpty()) {
                            isStart = false;
                            return;
                        }

                        initSocketClient(finalServers);
                        handler.postDelayed(heartBeatRunnable, HEART_BEAT_RATE);
                        isStart = false;
                    }
                });
            }
        }).start();
    }

    private void initSocketClient(List<YGOServer> servers) {
        for (YGOServer server : servers) {
            String urlString = getRoomSocketUrl(server);
            McWatchDuelSocketClient client = new McWatchDuelSocketClient(URI.create(urlString)) {
                @Override
                public void onMessage(String message) {
                    LogUtil.e(TAG, "WebSocketService收到的消息：" + message);
                    HandlerUtil.sendMessage(handler, HANDLE_SEND_MESSAGE, new RoomSocketMessage(message, server));
                }

                @Override
                public void onOpen(ServerHandshake handShakeData) {
                    LogUtil.e(TAG, "WebSocket 连接成功");
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    if (remote) {
                        LogUtil.e(TAG, "onClose() 主动断开连接" + remote + " " + reason);
                        return;
                    }
                    LogUtil.e(TAG, "onClose() 连接断开_reason：" + remote + " " + reason);
                    handler.removeCallbacks(heartBeatRunnable);
                    handler.postDelayed(heartBeatRunnable, CLOSE_RECON_TIME);
                }

                @Override
                public void onError(Exception ex) {
                    LogUtil.e(TAG, "onError() 连接出错：" + ex.getMessage());
                    handler.removeCallbacks(heartBeatRunnable);
                    handler.postDelayed(heartBeatRunnable, CLOSE_RECON_TIME);
                }
            };

            connect(client);
            mcWatchDuelSocketClientList.add(client);
        }
    }

    private String getRoomSocketUrl(YGOServer server) {
        String socketUrl = server == null ? null : server.getSocketUrl();
        if (TextUtils.isEmpty(socketUrl)) {
            socketUrl = MyCard.URL_MC_JOIN_DUEL_MATCH;
        }
        if (socketUrl.contains("filter=")) {
            return socketUrl;
        }
        return socketUrl + (socketUrl.contains("?") ? "&" : "?") + "filter=waiting";
    }

    private void connect(final McWatchDuelSocketClient client) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                if (!client.isOpen()) {
                    LogUtil.e("WaitingDuelMan", "状态" + client.getReadyState());
                    if (client.getReadyState() == ReadyState.NOT_YET_CONNECTED) {
                        try {
                            client.connectBlocking();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    } else if (client.getReadyState() == ReadyState.CLOSING ||
                            client.getReadyState() == ReadyState.CLOSED) {
                        client.reconnect();
                    }
                }
            }
        }).start();
    }

    public void sendMsg(McWatchDuelSocketClient client, String msg) {
        LogUtil.e(TAG, "发送的消息：" + msg);
        try {
            client.send(msg);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void closeConnect() {
        handler.removeCallbacks(heartBeatRunnable);
        for (McWatchDuelSocketClient client : mcWatchDuelSocketClientList) {
            try {
                client.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        mcWatchDuelSocketClientList.clear();
        rooms.clear();
        isStart = false;
    }

    private void reconnectWs(final McWatchDuelSocketClient client) {
        handler.removeCallbacks(heartBeatRunnable);
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    LogUtil.e(TAG, "开启重连");
                    client.reconnectBlocking();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    private static class RoomSocketMessage {
        private final String message;
        private final YGOServer server;

        private RoomSocketMessage(String message, YGOServer server) {
            this.message = message;
            this.server = server;
        }
    }
}
