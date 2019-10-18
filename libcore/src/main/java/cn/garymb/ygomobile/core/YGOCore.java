package cn.garymb.ygomobile.core;

import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.MotionEvent;

import androidx.annotation.Keep;

import java.nio.ByteBuffer;

public class YGOCore{
    public static final String ACTION_START = "cn.garymb.ygomobile.game.start";
    public static final String ACTION_STOP = "cn.garymb.ygomobile.game.stop";
    public static final String EXTRA_PID = "extras.mypid";
    private static final String TAG = "ygomobile";
    public static boolean DEBUG = false;
    public static int gPid;
    private static YGOCore sYGOCore;

    public static YGOCore getInstance(){
        if(sYGOCore == null){
            synchronized (YGOCore.class){
                if(sYGOCore == null){
                    sYGOCore = new YGOCore();
                }
            }
        }
        return sYGOCore;
    }

    static {
        try {
            System.loadLibrary("YGOMobile");
        } catch (Throwable e) {
            //ignore
        }
    }

    private int nativeAndroidDevice = 0;
    private HandlerThread mWorker;
    private Handler H;

    private YGOCore(){
        mWorker = new HandlerThread("ygopro_core_work");
        mWorker.start();
        H = new Handler(mWorker.getLooper());
    }

    @Keep
    public void release(){
        if(mWorker != null) {
            mWorker.quitSafely();
            mWorker = null;
        }
    }

    @Keep
    public void setNativeAndroidDevice(int nativeAndroidDevice) {
        Log.i(TAG, "setNativeAndroidDevice:" + nativeAndroidDevice);
        this.nativeAndroidDevice = nativeAndroidDevice;
    }

    @Keep
    public boolean sendTouchEvent(final int action,final int x,final int y,final int id) {
        if (nativeAndroidDevice == 0) {
            Log.w(TAG, "sendTouchEvent fail nativeAndroidDevice = 0");
            return false;
        }
        final int eventType = action & MotionEvent.ACTION_MASK;
        boolean touchReceived = true;
        switch (eventType) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_MOVE:
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_CANCEL:
                break;
            default:
                touchReceived = false;
                break;
        }
        if (!touchReceived) {
            return false;
        }
        if (id != 0) {
            return false;
        }
        H.post(new Runnable() {
            @Override
            public void run() {
                Log.i(TAG, "touch " + x + "x" + y);
                sendTouchInner(action, x, y, id);
            }
        });
        return true;
    }

    private void sendTouchInner(int action, int x, int y, int id){
        if (nativeAndroidDevice != 0) {
            nativeSendTouch(nativeAndroidDevice, action, x, y, id);
        }
    }

    @Keep
    public void cancelChain() {
        if (nativeAndroidDevice != 0) {
            nativeCancelChain(nativeAndroidDevice);
        }
    }

    @Keep
    public void ignoreChain(boolean begin) {
        if (nativeAndroidDevice != 0) {
            nativeIgnoreChain(nativeAndroidDevice, begin);
        }
    }

    @Keep
    public void reactChain(boolean begin) {
        if (nativeAndroidDevice != 0) {
            nativeReactChain(nativeAndroidDevice, begin);
        }
    }

    @Keep
    public void insertText(String text) {
        if (nativeAndroidDevice != 0) {
            nativeInsertText(nativeAndroidDevice, text);
        }
    }

    @Keep
    public void setComboBoxSelection(int idx) {
        if (nativeAndroidDevice != 0) {
            nativeSetComboBoxSelection(nativeAndroidDevice, idx);
        }
    }

    @Keep
    public void refreshTexture() {
        if (nativeAndroidDevice != 0) {
            nativeRefreshTexture(nativeAndroidDevice);
        }
    }

    @Keep
    public void setCheckBoxesSelection(int idx) {
        if (nativeAndroidDevice != 0) {
            nativeSetCheckBoxesSelection(nativeAndroidDevice, idx);
        }
    }

    @Keep
    public void joinGame(ByteBuffer options, int length) {
        if (nativeAndroidDevice != 0) {
            nativeJoinGame(nativeAndroidDevice, options, length);
        }
    }

    //插入文本（大概是发送消息）
    private native void nativeInsertText(int device, String text);

    //刷新文字
    private native void nativeRefreshTexture(int device);

    //忽略时点
    private native void nativeIgnoreChain(int device, boolean begin);

    //强制时点
    private native void nativeReactChain(int device, boolean begin);

    //取消连锁
    private native void nativeCancelChain(int device);

    private native void nativeSetCheckBoxesSelection(int device, int idx);

    private native void nativeSetComboBoxSelection(int device, int idx);

    private native void nativeJoinGame(int device, ByteBuffer buffer, int length);

    private native boolean nativeSendTouch(int device, int action, int x, int y, int id);

}
