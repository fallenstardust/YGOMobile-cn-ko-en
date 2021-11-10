package com.ourygo.ygomobile.util;

import android.os.Handler;
import android.os.Message;

import androidx.annotation.NonNull;

import com.ourygo.ygomobile.base.listener.OnDuelRoomListener;
import com.ourygo.ygomobile.bean.DuelRoom;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.enums.ReadyState;
import org.java_websocket.handshake.ServerHandshake;
import org.json.JSONException;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * Create By feihua  On 2021/11/3
 */
public class WatchDuelManagement {
    private static final int HANDLE_SEND_MESSAGE = 0;

    private static final WatchDuelManagement ourInstance = new WatchDuelManagement();
    private final static String TAG = WatchDuelManagement.class.getSimpleName();
    private final static int GRAY_SERVICE_ID = 1001;
    private static final long CLOSE_RECON_TIME = 1000;//连接断开或者连接错误立即重连
    private static final long HEART_BEAT_RATE = 10 * 1000;//每隔10秒进行一次对长连接的心跳检测
    //    public McWatchDuelSocketClient client;
    private List<OnDuelRoomListener> onDuelRoomListenerList;
    private boolean isStart = false;
    private List<String> urlList;
    private List<McWatchDuelSocketClient> mcWatchDuelSocketClientList;

    private Handler handler = new Handler() {
        @Override
        public void handleMessage(@NonNull Message msg) {
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

    private Runnable heartBeatRunnable = new Runnable() {
        @Override
        public void run() {
            for (int i=0;i<mcWatchDuelSocketClientList.size();i++) {
                McWatchDuelSocketClient client =mcWatchDuelSocketClientList.get(i);
                if (client != null) {
                    if (client.isClosed()) {
                        reconnectWs(client);
                        LogUtil.e(TAG, "心跳包检测WebSocket连接状态：已关闭 " + client.getURI());
                    } else if (client.isOpen()) {
                        LogUtil.d(TAG, "心跳包检测WebSocket连接状态：已连接 " + client.getURI());
                    } else {
                        LogUtil.e(TAG, "心跳包检测WebSocket连接状态：已断开 " + client.getURI());
                    }
                } else {
                    //如果client已为空，重新初始化连接
//                    initSocketClient();
                    mcWatchDuelSocketClientList.remove(i);
                    i--;
                    LogUtil.e(TAG, "心跳包检测WebSocket连接状态：client已为空，剩余数量 " + mcWatchDuelSocketClientList.size());
                }
            }

            //每隔一定的时间，对长连接进行一次心跳检测
            handler.postDelayed(this, HEART_BEAT_RATE);
        }
    };


    private WatchDuelManagement() {
        onDuelRoomListenerList = new ArrayList<>();
        urlList = new ArrayList<>();
        mcWatchDuelSocketClientList = new ArrayList<>();

        urlList.add(Record.URL_MC_WATCH_DUEL_MATCH);
        urlList.add(Record.URL_MC_WATCH_DUEL_FUN);
    }

    public static WatchDuelManagement getInstance() {
        return ourInstance;
    }

    public void addListener(OnDuelRoomListener onDuelRoomListener) {
        onDuelRoomListenerList.add(onDuelRoomListener);
    }

    public void removeListener(OnDuelRoomListener onDuelRoomListener) {
        onDuelRoomListenerList.remove(onDuelRoomListener);
    }

    private void onDuelRoomList(String event, List<DuelRoom> duelRoomList) {
        for (int i = 0; i < onDuelRoomListenerList.size(); i++) {
            OnDuelRoomListener ul = onDuelRoomListenerList.get(i);
            if (ul != null && ul.isListenerEffective()) {

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
        }
    }

    public void start() {
        if (isStart)
            return;
        isStart = true;
        if (mcWatchDuelSocketClientList.size() != 0)
            return;
        //初始化WebSocket
        initSocketClient();
        handler.postDelayed(heartBeatRunnable, HEART_BEAT_RATE);//开启心跳检测
        isStart = false;
    }

    private void initSocketClient() {

        for (String urlString : urlList) {
            URI uri = URI.create(urlString);

            McWatchDuelSocketClient client = new McWatchDuelSocketClient(uri) {
                @Override
                public void onMessage(String message) {
                    //message就是接收到的消息
                    LogUtil.e(TAG, "WebSocketService收到的消息：" + message);
                    HandlerUtil.sendMessage(handler, HANDLE_SEND_MESSAGE, message);
                }

                @Override
                public void onOpen(ServerHandshake handShakeData) {//在webSocket连接开启时调用
                    LogUtil.e(TAG, "WebSocket 连接成功");
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {//在连接断开时调用
                    if (remote) {
                        LogUtil.e(TAG, "onClose() 主动断开连接" + remote + " " + reason);

                        return;
                    }
                    LogUtil.e(TAG, "onClose() 连接断开_reason：" + remote + " " + reason);

                    handler.removeCallbacks(heartBeatRunnable);
                    handler.postDelayed(heartBeatRunnable, CLOSE_RECON_TIME);//开启心跳检测
                }

                @Override
                public void onError(Exception ex) {//在连接出错时调用
                    LogUtil.e(TAG, "onError() 连接出错：" + ex.getMessage());

                    handler.removeCallbacks(heartBeatRunnable);
                    handler.postDelayed(heartBeatRunnable, CLOSE_RECON_TIME);//开启心跳检测
                }
            };
            connect(client);
            mcWatchDuelSocketClientList.add(client);
        }


    }

    /**
     * 连接WebSocket
     */
    private void connect(McWatchDuelSocketClient client) {
        new Thread() {
            @Override
            public void run() {

                //括号里是client没有初试过，用别的方法判断也可以，反正一定要判断到 client没有初始化过。
                if (client == null) {
                    return;
                }
                if (!client.isOpen()) {
                    LogUtil.e("WatchDuelMan", "状态" + client.getReadyState());
                    if (client.getReadyState().equals(ReadyState.NOT_YET_CONNECTED)) {
                        try {
                            //connectBlocking多出一个等待操作，会先连接再发送，否则未连接发送会报错
                            client.connectBlocking();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    } else if (client.getReadyState().equals(ReadyState.CLOSING) || client.getReadyState().equals(ReadyState.CLOSED)) {
                        client.reconnect();
                    }
                }
            }
        }.start();
    }

    /**
     * 发送消息
     */
    public void sendMsg(McWatchDuelSocketClient client,String msg) {
        if (null != client) {
            LogUtil.e(TAG, "发送的消息：" + msg);
            try {
                client.send(msg);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 断开连接
     */
    public void closeConnect() {
        handler.removeCallbacks(heartBeatRunnable);
        for (McWatchDuelSocketClient client:mcWatchDuelSocketClientList){
            try {
                if (null != client) {
                    client.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        mcWatchDuelSocketClientList.clear();
    }

    /**
     * 开启重连
     */
    private void reconnectWs(McWatchDuelSocketClient client) {
        handler.removeCallbacks(heartBeatRunnable);
        new Thread() {
            @Override
            public void run() {
                try {
                    LogUtil.e(TAG, "开启重连");
                    client.reconnectBlocking();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }

}
