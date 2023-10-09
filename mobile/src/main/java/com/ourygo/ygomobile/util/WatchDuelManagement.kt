package com.ourygo.ygomobile.util

import android.os.Handler
import android.os.Looper
import android.os.Message
import com.ourygo.ygomobile.base.listener.OnDuelRoomListener
import com.ourygo.ygomobile.bean.DuelRoom
import org.java_websocket.enums.ReadyState
import org.java_websocket.handshake.ServerHandshake
import org.json.JSONException
import java.net.URI

/**
 * Create By feihua  On 2021/11/3
 */
class WatchDuelManagement private constructor() {
    //    public McWatchDuelSocketClient client;
    private val onDuelRoomListenerList by lazy {
        ArrayList<OnDuelRoomListener>()
    }
    private var isStart = false
    private val urlList = arrayListOf(
        Record.URL_MC_WATCH_DUEL_MATCH,
        Record.URL_MC_WATCH_DUEL_FUN
    )
    private val mcWatchDuelSocketClientList by lazy {
        ArrayList<McWatchDuelSocketClient>()
    }
    private val handler: Handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            when (msg.what) {
                HANDLE_SEND_MESSAGE -> {
                    val message = msg.obj as String
                    try {
                        val duelRoomList = JsonUtil.getDuelRoomList(message)
                        val event = JsonUtil.getDuelRoomEvent(message)
                        onDuelRoomList(event, duelRoomList)
                    } catch (e: JSONException) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }
    private val heartBeatRunnable: Runnable = object : Runnable {
        override fun run() {
            var i = 0
            while (i < mcWatchDuelSocketClientList.size) {
                val client = mcWatchDuelSocketClientList[i]
//                if (client != null) {
                if (client.isClosed) {
                    reconnectWs(client)
                    LogUtil.e(TAG, "心跳包检测WebSocket连接状态：已关闭 " + client.uri)
                } else if (client.isOpen) {
                    LogUtil.d(TAG, "心跳包检测WebSocket连接状态：已连接 " + client.uri)
                } else {
                    LogUtil.e(TAG, "心跳包检测WebSocket连接状态：已断开 " + client.uri)
                }
//                } else {
                //如果client已为空，重新初始化连接
//                    initSocketClient();
//                    mcWatchDuelSocketClientList.removeAt(i)
//                    i--
//                    LogUtil.e(
//                        TAG,
//                        "心跳包检测WebSocket连接状态：client已为空，剩余数量 " + mcWatchDuelSocketClientList.size
//                    )
//            }
                i++
            }

            //每隔一定的时间，对长连接进行一次心跳检测
            handler.postDelayed(this, HEART_BEAT_RATE)
        }
    }


    fun addListener(onDuelRoomListener: OnDuelRoomListener) {
        onDuelRoomListenerList.add(onDuelRoomListener)
    }

    fun removeListener(onDuelRoomListener: OnDuelRoomListener) {
        onDuelRoomListenerList.remove(onDuelRoomListener)
    }

    private fun onDuelRoomList(event: String, duelRoomList: List<DuelRoom>) {
        var i = 0
        while (i < onDuelRoomListenerList.size) {
            val ul = onDuelRoomListenerList[i]
            if (ul.isListenerEffective) {
                when (event) {
                    DuelRoom.EVENT_INIT -> ul.onInit(duelRoomList)
                    DuelRoom.EVENT_CREATE -> ul.onCreate(duelRoomList)
                    DuelRoom.EVENT_UPDATE -> ul.onUpdate(duelRoomList)
                    DuelRoom.EVENT_DELETE -> ul.onDelete(duelRoomList)
                }
            } else {
                onDuelRoomListenerList.removeAt(i)
                i--
            }
            i++
        }
    }

    fun start() {
        if (isStart) return
        isStart = true
        if (mcWatchDuelSocketClientList.size != 0) return
        //初始化WebSocket
        initSocketClient()
        handler.postDelayed(heartBeatRunnable, HEART_BEAT_RATE) //开启心跳检测
        isStart = false
    }

    private fun initSocketClient() {
        for (urlString in urlList) {
            object : McWatchDuelSocketClient(URI.create(urlString)) {
                override fun onMessage(message: String) {
                    //message就是接收到的消息
                    LogUtil.e(TAG, "WebSocketService收到的消息：$message")
                    HandlerUtil.sendMessage(handler, HANDLE_SEND_MESSAGE, message)
                }

                override fun onOpen(handShakeData: ServerHandshake) { //在webSocket连接开启时调用
                    LogUtil.e(TAG, "WebSocket 连接成功")
                }

                override fun onClose(code: Int, reason: String, remote: Boolean) { //在连接断开时调用
                    if (remote) {
                        LogUtil.e(TAG, "onClose() 主动断开连接$remote $reason")
                        return
                    }
                    LogUtil.e(TAG, "onClose() 连接断开_reason：$remote $reason")
                    handler.removeCallbacks(heartBeatRunnable)
                    handler.postDelayed(heartBeatRunnable, CLOSE_RECON_TIME) //开启心跳检测
                }

                override fun onError(ex: Exception) { //在连接出错时调用
                    LogUtil.e(TAG, "onError() 连接出错：" + ex.message)
                    handler.removeCallbacks(heartBeatRunnable)
                    handler.postDelayed(heartBeatRunnable, CLOSE_RECON_TIME) //开启心跳检测
                }
            }.apply {
                connect(this)
                mcWatchDuelSocketClientList.add(this)
            }
        }
    }

    /**
     * 连接WebSocket
     */
    private fun connect(client: McWatchDuelSocketClient) {
        Thread {
            //括号里是client没有初试过，用别的方法判断也可以，反正一定要判断到 client没有初始化过。
            if (!client.isOpen) {
                LogUtil.e("WatchDuelMan", "状态" + client.readyState)
                if (client.readyState == ReadyState.NOT_YET_CONNECTED) {
                    try {
                        //connectBlocking多出一个等待操作，会先连接再发送，否则未连接发送会报错
                        client.connectBlocking()
                    } catch (e: InterruptedException) {
                        e.printStackTrace()
                    }
                } else if (client.readyState == ReadyState.CLOSING || client.readyState == ReadyState.CLOSED) {
                    client.reconnect()
                }
            }
        }.start()
    }

    /**
     * 发送消息
     */
    fun sendMsg(client: McWatchDuelSocketClient, msg: String) {
        LogUtil.e(TAG, "发送的消息：$msg")
        try {
            client.send(msg)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 断开连接
     */
    fun closeConnect() {
        handler.removeCallbacks(heartBeatRunnable)
        for (client in mcWatchDuelSocketClientList) {
            try {
                client.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        mcWatchDuelSocketClientList.clear()
    }

    /**
     * 开启重连
     */
    private fun reconnectWs(client: McWatchDuelSocketClient) {
        handler.removeCallbacks(heartBeatRunnable)
        object : Thread() {
            override fun run() {
                try {
                    LogUtil.e(TAG, "开启重连")
                    client.reconnectBlocking()
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
            }
        }.start()
    }

    companion object {
        private const val HANDLE_SEND_MESSAGE = 0
        val instance = WatchDuelManagement()
        private val TAG = WatchDuelManagement::class.java.simpleName
        private const val GRAY_SERVICE_ID = 1001
        private const val CLOSE_RECON_TIME: Long = 1000 //连接断开或者连接错误立即重连
        private const val HEART_BEAT_RATE = (10 * 1000).toLong() //每隔10秒进行一次对长连接的心跳检测

    }
}