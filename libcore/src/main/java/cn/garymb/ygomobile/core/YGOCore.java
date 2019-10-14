package cn.garymb.ygomobile.core;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.view.InputEvent;
import android.view.InputQueue;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.Arrays;

import cn.garymb.ygomobile.NativeInitOptions;
import cn.garymb.ygomobile.lib.BuildConfig;

import static cn.garymb.ygomobile.utils.ByteUtils.byte2int;
import static cn.garymb.ygomobile.utils.ByteUtils.byte2uint;

public class YGOCore implements InputQueueCompat.FinishedInputEventCallback {
    public static final String ACTION_START = "cn.garymb.ygomobile.game.start";
    public static final String ACTION_STOP = "cn.garymb.ygomobile.game.stop";
    public static final String EXTRA_PID = "extras.mypid";
    private static final String TAG = "ygomobile";
    public static boolean DEBUG = false;
    public static int gPid;

    static {
        try {
            System.loadLibrary("YGOMobile");
        } catch (Throwable e) {
            //ignore
        }
    }

    private int nativeAndroidDevice = 0;
    private final Context mContext;
    private AssetManager mAssetManager;
    private boolean mInit = false;
//    private InputQueueCompat mInputQueue;
    private HandlerThread mWorker;
    private Handler H;

    public interface IActivityHost {
        Object getNativeGameHost();
    }

    public YGOCore(Context context) throws Exception {
        this(context, context.getAssets());
    }

    public YGOCore(Context context, AssetManager mAssetManager) throws Exception {
        this.mContext = context;
        this.mAssetManager = mAssetManager;
//        mInputQueue = new InputQueueCompat();
        mWorker = new HandlerThread("ygopro_work");
        mWorker.start();
        H = new Handler(mWorker.getLooper());
    }

    public void release(){
        mWorker.stop();
    }

//    public InputQueue getInputQueue() {
//        return mInputQueue.getInputQueue();
//    }

    public void setNativeAndroidDevice(int nativeAndroidDevice) {
        Log.i(TAG, "setNativeAndroidDevice:" + nativeAndroidDevice);
        this.nativeAndroidDevice = nativeAndroidDevice;
    }

    @Override
    public void onFinishedInputEvent(Object token, boolean handled) {

    }

    public static Bitmap getBpgImage(InputStream inputStream, Bitmap.Config config) {
        ByteArrayOutputStream outputStream = null;
        try {
            outputStream = new ByteArrayOutputStream();
            byte[] tmp = new byte[4096];
            int len = 0;
            while ((len = inputStream.read(tmp)) != -1) {
                outputStream.write(tmp, 0, len);
            }
            //解码前
            byte[] bpg = outputStream.toByteArray();
            return getBpgImage(bpg, config);
        } catch (Exception e) {
            if (DEBUG)
                Log.e(TAG, "zip image", e);
        } finally {
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    /***
     *
     */
    public static Bitmap getBpgImage(byte[] bpg, Bitmap.Config config) {
        try {
            //解码后
            byte[] data = nativeBpgImage(bpg);
            int start = 8;
            int w = byte2int(Arrays.copyOfRange(data, 0, 4));
            int h = byte2int(Arrays.copyOfRange(data, 4, 8));
            if (w < 0 || h < 0) {
                if (DEBUG)
                    Log.e(TAG, "zip image:w=" + w + ",h=" + h);
                return null;
            }
            int index = 0;
            int[] colors = new int[(data.length - start) / 3];
            for (int i = 0; i < colors.length; i++) {
                index = start + i * 3;
                colors[i] = Color.rgb(byte2uint(data[index + 0]), byte2uint(data[index + 1]), byte2uint(data[index + 2]));
            }
            return Bitmap.createBitmap(colors, w, h, config);
        } catch (Throwable e) {
            if (DEBUG)
                Log.e(TAG, "zip image", e);
            return null;
        }
    }

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

    public void sendKeyEvent(final KeyEvent event) {
//        if (nativeAndroidDevice != 0) {
//            H.post(new Runnable() {
//                @Override
//                public void run() {
//                    sendTouch(event.getAction(), event);
//                }
//            });
//        }
    }

    private void sendTouchInner(int action, int x, int y, int id){
        if (nativeAndroidDevice != 0) {
            nativeSendTouch(nativeAndroidDevice, action, x, y, id);
        }
    }

    public void cancelChain() {
        if (nativeAndroidDevice != 0) {
            nativeCancelChain(nativeAndroidDevice);
        }
    }

    public void ignoreChain(boolean begin) {
        if (nativeAndroidDevice != 0) {
            nativeIgnoreChain(nativeAndroidDevice, begin);
        }
    }

    public void reactChain(boolean begin) {
        if (nativeAndroidDevice != 0) {
            nativeReactChain(nativeAndroidDevice, begin);
        }
    }

    public void insertText(String text) {
        if (nativeAndroidDevice != 0) {
            nativeInsertText(nativeAndroidDevice, text);
        }
    }

    public void setComboBoxSelection(int idx) {
        if (nativeAndroidDevice != 0) {
            nativeSetComboBoxSelection(nativeAndroidDevice, idx);
        }
    }

    public void refreshTexture() {
        if (nativeAndroidDevice != 0) {
            nativeRefreshTexture(nativeAndroidDevice);
        }
    }

    public void setCheckBoxesSelection(int idx) {
        if (nativeAndroidDevice != 0) {
            nativeSetCheckBoxesSelection(nativeAndroidDevice, idx);
        }
    }

    public void joinGame(ByteBuffer options, int length) {
        if (nativeAndroidDevice != 0) {
            nativeJoinGame(nativeAndroidDevice, options, length);
        }
    }

    //显示卡图
    public static native byte[] nativeBpgImage(byte[] data);

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
