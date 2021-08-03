/*
 * IrrlichtBridge.java
 *
 *  Created on: 2014年3月18日
 *      Author: mabin
 */
package cn.garymb.ygomobile.core;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

import cn.garymb.ygodata.YGOGameOptions;

import static cn.garymb.ygomobile.utils.ByteUtils.byte2int;
import static cn.garymb.ygomobile.utils.ByteUtils.byte2uint;

/**
 * @author mabin
 */
public final class IrrlichtBridge {
    public static final String ACTION_OPEN_GAME_HOME = "ygomobile.intent.action.GAME";
    /**
     * @see #EXTRA_SHARE_FILE
     * @see #EXTRA_SHARE_TYPE
     */
    public static final String ACTION_SHARE_FILE = "cn.garymb.ygomobile.game.shared.file";
    public static final String EXTRA_SHARE_FILE = Intent.EXTRA_STREAM;
    public static final String EXTRA_SHARE_TYPE = Intent.EXTRA_TITLE;
    public static final String EXTRA_PID = "extras.mypid";
    public static final String EXTRA_ARGV = "extras.argv";
    public static final String EXTRA_ARGV_TIME_OUT = "extras.argv_timeout";
    public static final String EXTRA_GAME_EXIT_TIME = "game_exit_time";
    public static final String EXTRA_TASK_ID = "extras.taskid";

    public static final String TAG = "ygo-java";

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

    public static long sNativeHandle;
    //显示卡图
    public static native byte[] nativeBpgImage(byte[] data);
    //插入文本（大概是发送消息）
    private static native void nativeInsertText(long handle, String text);
    //刷新文字
    private static native void nativeRefreshTexture(long handle);
    //忽略时点
    private static native void nativeIgnoreChain(long handle, boolean begin);
    //强制时点
    private static native void nativeReactChain(long handle, boolean begin);
    //取消连锁
    private static native void nativeCancelChain(long handle);

    private static native void nativeSetCheckBoxesSelection(long handle, int idx);

    private static native void nativeSetComboBoxSelection(long handle, int idx);

    private static native void nativeJoinGame(long handle, ByteBuffer buffer, int length);

    private static native void nativeSetInputFix(long handle, int x, int y);

    private static final boolean DEBUG = false;

    public static void setArgs(Intent intent, String[] args) {
        intent.putExtra(EXTRA_ARGV, args);
        intent.putExtra(EXTRA_ARGV_TIME_OUT, (System.currentTimeMillis() + 15 * 1000));
    }

    public static String[] getArgs(Intent intent){
        long time = intent.getLongExtra(EXTRA_ARGV_TIME_OUT, 0);
        if(time > System.currentTimeMillis()){
            return intent.getStringArrayExtra(EXTRA_ARGV);
        }
        return null;
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

    public static void setInputFix(int x, int y){
        nativeSetInputFix(sNativeHandle, x, y);
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
		
		void runWindbot(String args);

        float getXScale();

        float getYScale();
		
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

        void shareFile(String title, String path);

        void performHapticFeedback();

        /**
         * 签名
         */
        byte[] performTrick();

        int getLocalAddress();

        void setNativeHandle(long nativeHandle);

        int getPositionX();

        int getPositionY();

        void onGameExit();
    }
}
