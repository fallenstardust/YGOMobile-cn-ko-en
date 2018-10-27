/*
 * IrrlichtBridge.java
 *
 *  Created on: 2014年3月18日
 *      Author: mabin
 */
package cn.garymb.ygomobile.core;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

import static cn.garymb.ygomobile.utils.ByteUtils.byte2int;
import static cn.garymb.ygomobile.utils.ByteUtils.byte2uint;

/**
 * @author mabin
 */
public final class IrrlichtBridge {
    public static final String ACTION_START = "cn.garymb.ygomobile.game.start";
    public static final String ACTION_STOP = "cn.garymb.ygomobile.game.stop";
    public static final String EXTRA_PID = "extras.mypid";
    public static int gPid;
    static {
        try {
            System.loadLibrary("YGOMobile");
        }catch (Throwable e){
            //ignore
        }
    }

    private IrrlichtBridge() {

    }

    public static int sNativeHandle;

    public static native byte[] nativeBpgImage(byte[] data);

    private static native void nativeInsertText(int handle, String text);

    private static native void nativeRefreshTexture(int handle);

    private static native void nativeIgnoreChain(int handle, boolean begin);

    private static native void nativeReactChain(int handle, boolean begin);

    private static native void nativeCancelChain(int handle);

    private static native void nativeSetCheckBoxesSelection(int handle, int idx);

    private static native void nativeSetComboBoxSelection(int handle, int idx);

    private static native void nativeJoinGame(int handle, ByteBuffer buffer, int length);

    private static final boolean DEBUG = false;
    private static final String TAG = IrrlichtBridge.class.getSimpleName();

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

    public static void cancelChain() {
        nativeCancelChain(sNativeHandle);
    }

    public static void ignoreChain(boolean begin) {
        nativeIgnoreChain(sNativeHandle, begin);
    }

    public static void reactChain(boolean begin) {
        nativeReactChain(sNativeHandle, begin);
    }

    public static void insertText(String text) {
        nativeInsertText(sNativeHandle, text);
    }

    public static void setComboBoxSelection(int idx) {
        nativeSetComboBoxSelection(sNativeHandle, idx);
    }

    public static void refreshTexture() {
        nativeRefreshTexture(sNativeHandle);
    }

    public static void setCheckBoxesSelection(int idx) {
        nativeSetCheckBoxesSelection(sNativeHandle, idx);
    }

    public static void joinGame(ByteBuffer options, int length) {
        nativeJoinGame(sNativeHandle, options, length);
    }

    public interface IrrlichtApplication {
        String getCardImagePath();

        void saveSetting(String key, String value);

        String getFontPath();

        String getSetting(String key);

        int getIntSetting(String key,int def);

        void saveIntSetting(String key,int value);

        float getScreenWidth();

        float getScreenHeight();

        void playSoundEffect(String path);
		
		void runWindbot(String args);
		
//        float getSmallerSize();
//        float getXScale();
//        float getYScale();
//        float getDensity();
    }

    public interface IrrlichtHost {
        void toggleOverlayView(boolean isShow);

        ByteBuffer getInitOptions();

        ByteBuffer getNativeInitOptions();

        void toggleIME(String hint, boolean isShow);

        void showComboBoxCompat(String[] items, boolean isShow, int mode);

        void performHapticFeedback();

        /**
         * 签名
         */
        byte[] performTrick();

        int getLocalAddress();

        void setNativeHandle(int nativeHandle);
    }
}
