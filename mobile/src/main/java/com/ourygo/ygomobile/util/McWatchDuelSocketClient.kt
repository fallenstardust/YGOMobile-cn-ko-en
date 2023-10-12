package com.ourygo.ygomobile.util

import org.java_websocket.client.WebSocketClient
import org.java_websocket.drafts.Draft_6455
import org.java_websocket.handshake.ServerHandshake
import java.net.URI
import javax.net.ssl.SSLParameters

/**
 * Create By feihua  On 2021/11/3
 */
open class McWatchDuelSocketClient(serverUri: URI?) : WebSocketClient(serverUri, Draft_6455()) {
    override fun onSetSSLParameters(sslParameters: SSLParameters) {
//        super.onSetSSLParameters(sslParameters);
    }

    override fun onOpen(handShakeData: ServerHandshake) { //在webSocket连接开启时调用
    }

    override fun onMessage(message: String) { //接收到消息时调用
    }

    override fun onClose(code: Int, reason: String, remote: Boolean) { //在连接断开时调用
    }

    override fun onError(ex: Exception) { //在连接出错时调用
    }
}