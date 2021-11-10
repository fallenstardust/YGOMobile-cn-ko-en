package com.ourygo.ygomobile.util;

import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;


public class HandlerUtil {

    public static void sendMessage(Handler handler, String exception, int ok, Object okObject, int no) {
        Message message = new Message();
        if (TextUtils.isEmpty(exception)) {
            message.what = ok;
            message.obj = okObject;
        } else {
            message.what = no;
            message.obj = exception;
        }
        handler.sendMessage(message);
    }

    public static void sendMessage(Handler handler, String exception, int ok, int no) {
        sendMessage(handler, exception, ok, null, no);
    }

    public static void sendMessage(Handler handler, int what, Object object) {
        Message message = new Message();
        message.obj = object;
        message.what = what;
        handler.sendMessage(message);
    }
}
