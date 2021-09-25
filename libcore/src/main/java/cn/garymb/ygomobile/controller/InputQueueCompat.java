package cn.garymb.ygomobile.controller;

import android.os.Looper;
import android.util.Log;
import android.view.InputEvent;
import android.view.InputQueue;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import cn.garymb.ygomobile.utils.AndroidHideApi;

/**
 * 仅测试安卓7-11
 */
public class InputQueueCompat implements InvocationHandler {
    private static final String TAG = "kk-java";
    private static Constructor<InputQueue> InputQueue_ctr;
    private static Method getNativePtr_method;
    private static Class<?> FinishedInputEventCallback_class;
    //sendInputEvent(InputEvent e, Object token, boolean predispatch,
    //                                      FinishedInputEventCallback callback)
    private static Method sendInputEvent_method;
    private FinishedInputEventCallbackCompat finishedInputEventCallbackCompat;

    static {
        AndroidHideApi.enableHideApi();
        try {
            Looper.getMainLooper();
            InputQueue_ctr = InputQueue.class.getDeclaredConstructor();
//            InputQueue_ctr.setAccessible(true);
            getNativePtr_method = InputQueue.class.getMethod("getNativePtr");
//            getNativePtr_method.setAccessible(true);
            FinishedInputEventCallback_class = Class.forName(InputQueue.class.getName() + "$FinishedInputEventCallback");
            sendInputEvent_method = InputQueue.class.getMethod("sendInputEvent", InputEvent.class, Object.class, boolean.class,
                    FinishedInputEventCallback_class);
        } catch (Throwable e) {
            Log.e(TAG, "InputQueueCompat init", e);
        }
    }

    private final InputQueue inputQueue;
    private final Object callback;

    public InputQueueCompat(InputQueue inputQueue) {
        this.inputQueue = inputQueue;
        if(inputQueue != null) {
            callback = Proxy.newProxyInstance(InputQueue.class.getClassLoader(), new Class[]{FinishedInputEventCallback_class},
                    this);
        } else {
            callback = null;
        }
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) {
        //void onFinishedInputEvent(Object token, boolean handled);
        if ("onFinishedInputEvent".equals(method.getName())) {
            Object token = args[0];
            boolean handled = (boolean) args[1];
            onFinishedInputEvent(token, handled);
            return 0;
        }
        return 0;
    }

    public InputQueueCompat() {
        this(create());
    }

    public void setFinishedInputEventCallback(FinishedInputEventCallbackCompat finishedInputEventCallbackCompat) {
        this.finishedInputEventCallbackCompat = finishedInputEventCallbackCompat;
    }

    public boolean isValid() {
        return inputQueue != null && sendInputEvent_method != null && getNativePtr_method != null;
    }

    private static InputQueue create() {
        try {
            return InputQueue_ctr.newInstance();
        } catch (Throwable e) {
            Log.w(TAG, "InputQueue<init>", e);
            return null;
        }
    }

    public long getNativePtr() {
        if (getNativePtr_method == null || inputQueue == null) {
            return 0;
        }
        try {
            Long ret = (Long) getNativePtr_method.invoke(inputQueue);
            if (ret == null) {
                return 0;
            }
            return ret;
        } catch (Throwable e) {
            Log.w(TAG, "getNativePtr", e);
            return 0;
        }
    }

    public InputQueue getInputQueue() {
        return inputQueue;
    }

    public void sendInputEvent(InputEvent e, Object token, boolean predispatch) {

        if (sendInputEvent_method == null) {
            return;
        }
        try {
            Log.d(TAG, "inputQueue:sendInputEvent:" + e);
            sendInputEvent_method.invoke(inputQueue, e, token, predispatch, callback);
        } catch (Throwable ex) {
            Log.w(TAG, "inputQueue:sendInputEvent", ex);
        }
    }

    public void onFinishedInputEvent(Object token, boolean handled) {
        //TODO
        Log.d(TAG, "onFinishedInputEvent:" + token + ", handled=" + handled);
        if(this.finishedInputEventCallbackCompat != null){
            finishedInputEventCallbackCompat.onFinishedInputEvent(token, handled);
        }
    }

    public interface FinishedInputEventCallbackCompat{
        void onFinishedInputEvent(Object token, boolean handled);
    }
}
