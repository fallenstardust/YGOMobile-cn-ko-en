package com.ourygo.ygomobile.util

import android.os.Handler
import android.os.Message
import android.text.TextUtils

object HandlerUtil {
    @JvmStatic
    fun sendMessage(handler: Handler, exception: String?, ok: Int, okObject: Any?, no: Int) {
        val message = Message()
        if (TextUtils.isEmpty(exception)) {
            message.what = ok
            message.obj = okObject
        } else {
            message.what = no
            message.obj = exception
        }
        handler.sendMessage(message)
    }

    fun sendMessage(handler: Handler, exception: String?, ok: Int, no: Int) {
        sendMessage(handler, exception, ok, null, no)
    }

    @JvmStatic
    fun sendMessage(handler: Handler, what: Int, `object`: Any?) {
        val message = Message()
        message.obj = `object`
        message.what = what
        handler.sendMessage(message)
    }
}