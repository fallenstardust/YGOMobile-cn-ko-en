package cn.garymb.ygomobile.ui.mycard.watchDuel;

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
import cn.garymb.ygomobile.utils.HandlerUtil;
import cn.garymb.ygomobile.utils.JsonUtil;
import cn.garymb.ygomobile.utils.LogUtil;

/**
 * Create By feihua  On 2021/11/3
 * Converted from Kotlin to Java
 */
public class WatchDuelManagement {

    private static final String TAG = WatchDuelManagement.class.getSimpleName();
    private static final int HANDLE_SEND_MESSAGE = 0;
    private static final int GRAY_SERVICE_ID = 1001;
    private static final long CLOSE_RECON_TIME = 1000; // 连接断开或者连接错误立即重连
    private static final long HEART_BEAT_RATE = 10 * 1000; // 每隔10秒进行一次对长连接的心跳检测

    private static volatile WatchDuelManagement instance;

    private ArrayList<OnDuelRoomListener> onDuelRoomListenerList;
    private boolean isStart;
    private List<String> urlList;
    private ArrayList<McWatchDuelSocketClient> mcWatchDuelSocketClientList;
    private Handler handler;
    private Runnable heartBeatRunnable;

    private WatchDuelManagement() {
        onDuelRoomListenerList = new ArrayList<>();
        isStart = false;

        urlList = new ArrayList<>();
        urlList.add(MyCard.URL_MC_WATCH_DUEL_MATCH);
        urlList.add(MyCard.URL_MC_WATCH_DUEL_FUN);

        mcWatchDuelSocketClientList = new ArrayList<>();

        initHandler();
        initHeartBeatRunnable();
    }

    public static WatchDuelManagement getInstance() {
        if (instance == null) {
            synchronized (WatchDuelManagement.class) {
                if (instance == null) {
                    instance = new WatchDuelManagement();
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
                        String message = (String) msg.obj;
                        try {
                            List<DuelRoom> duelRoomList = JsonUtil.getDuelRoomList(message);
                            String event = JsonUtil.getDuelRoomEvent(message);
                            onDuelRoomList(event, duelRoomList);
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

                // 每隔一定的时间，对长连接进行一次心跳检测
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

    public void start() {
        if (isStart) return;
        isStart = true;
        if (mcWatchDuelSocketClientList.size() != 0) return;

        // 初始化WebSocket
        initSocketClient();
        handler.postDelayed(heartBeatRunnable, HEART_BEAT_RATE); // 开启心跳检测
        isStart = false;
    }

    private void initSocketClient() {
        for (String urlString : urlList) {
            McWatchDuelSocketClient client = new McWatchDuelSocketClient(URI.create(urlString)) {
                @Override
                public void onMessage(String message) {
                    // message就是接收到的消息
                    LogUtil.e(TAG, "WebSocketService收到的消息：" + message);
                    HandlerUtil.sendMessage(handler, HANDLE_SEND_MESSAGE, message);
                }

                @Override
                public void onOpen(ServerHandshake handShakeData) {
                    // 在webSocket连接开启时调用
                    LogUtil.e(TAG, "WebSocket 连接成功");
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    // 在连接断开时调用
                    if (remote) {
                        LogUtil.e(TAG, "onClose() 主动断开连接" + remote + " " + reason);
                        return;
                    }
                    LogUtil.e(TAG, "onClose() 连接断开_reason：" + remote + " " + reason);
                    handler.removeCallbacks(heartBeatRunnable);
                    handler.postDelayed(heartBeatRunnable, CLOSE_RECON_TIME); // 开启心跳检测
                }

                @Override
                public void onError(Exception ex) {
                    // 在连接出错时调用
                    LogUtil.e(TAG, "onError() 连接出错：" + ex.getMessage());
                    handler.removeCallbacks(heartBeatRunnable);
                    handler.postDelayed(heartBeatRunnable, CLOSE_RECON_TIME); // 开启心跳检测
                }
            };

            connect(client);
            mcWatchDuelSocketClientList.add(client);
        }
    }

    /**
     * 连接WebSocket
     */
    private void connect(final McWatchDuelSocketClient client) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                // 括号里是client没有初试过，用别的方法判断也可以，反正一定要判断到 client没有初始化过。
                if (!client.isOpen()) {
                    LogUtil.e("WatchDuelMan", "状态" + client.getReadyState());
                    if (client.getReadyState() == ReadyState.NOT_YET_CONNECTED) {
                        try {
                            // connectBlocking多出一个等待操作，会先连接再发送，否则未连接发送会报错
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

    /**
     * 发送消息
     */
    public void sendMsg(McWatchDuelSocketClient client, String msg) {
        LogUtil.e(TAG, "发送的消息：" + msg);
        try {
            client.send(msg);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 断开连接
     */
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
    }

    /**
     * 开启重连
     */
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
}

