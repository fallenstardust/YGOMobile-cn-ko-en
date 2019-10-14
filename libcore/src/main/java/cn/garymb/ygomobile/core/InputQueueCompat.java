package cn.garymb.ygomobile.core;

import android.util.Log;
import android.view.InputEvent;
import android.view.InputQueue;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import cn.garymb.ygomobile.GameApplication;
import me.weishu.reflection.Reflection;

public class InputQueueCompat {

    public interface FinishedInputEventCallback {
        void onFinishedInputEvent(Object token, boolean handled);
    }

    private InputQueue mInputQueue;
    private Method _sendInputEvent;
    private Method _dispose;
    private Class FinishedInputEventCallback;

    static {
        Reflection.unseal(GameApplication.get());
    }

    @SuppressWarnings("JavaReflectionMemberAccess")
    public InputQueueCompat() throws Exception {
        mInputQueue = InputQueue.class.newInstance();
        FinishedInputEventCallback = Class.forName(InputQueue.class.getName() + "$FinishedInputEventCallback");
        // public void sendInputEvent(InputEvent e, Object token, boolean predispatch,
        //        FinishedInputEventCallback callback)
        try {
            _dispose = InputQueue.class.getMethod("dispose");
        } catch (Throwable e) {
            //ignore
        }
        _sendInputEvent = InputQueue.class.getMethod("sendInputEvent", InputEvent.class, Object.class, boolean.class, FinishedInputEventCallback);
    }

    public InputQueue getInputQueue() {
        return mInputQueue;
    }

    public boolean sendInputEvent(InputEvent e, Object token, boolean predispatch,
                                  final FinishedInputEventCallback callback) {
        try {
            Log.d("ygomobile", "sendInputEvent:" + e);
            _sendInputEvent.invoke(mInputQueue, e, token, predispatch, Proxy.newProxyInstance(FinishedInputEventCallback.class.getClassLoader(), new Class[]{FinishedInputEventCallback}, new InvocationHandler() {
                @Override
                public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                    if ("onFinishedInputEvent".equals(method.getName())) {
                        callback.onFinishedInputEvent(args[0], (boolean) args[1]);
                    }
                    return method.invoke(proxy, args);
                }
            }));
        } catch (Throwable ex) {
            Log.e("ygomobile", "sendInputEvent", ex);
            return false;
        }
        return true;
    }

    public void release() {
        if (_dispose != null) {
            try {
                _dispose.invoke(mInputQueue);
            } catch (Throwable e) {
                e.printStackTrace();
            }
        }
    }
}
