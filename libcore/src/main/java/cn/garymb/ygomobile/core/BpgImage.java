package cn.garymb.ygomobile.core;

import android.graphics.Bitmap;
import android.graphics.Color;
import android.util.Log;

import androidx.annotation.Keep;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import cn.garymb.ygomobile.GameApplication;

import static android.content.ContentValues.TAG;


public class BpgImage {

    @Keep
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
            Log.e(TAG, "zip bpg image", e);
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

    @Keep
    public static Bitmap getBpgImage(byte[] bpg, Bitmap.Config config) {
        try {
            //解码后
            byte[] data = nativeBpgImage(bpg);
            int start = 8;
            int w = byte2int(Arrays.copyOfRange(data, 0, 4));
            int h = byte2int(Arrays.copyOfRange(data, 4, 8));
            if (w < 0 || h < 0) {
                if (GameApplication.isDebug()) {
                    Log.e(TAG, "zip image:w=" + w + ",h=" + h);
                }
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
            Log.e(TAG, "zip bpg image", e);
            return null;
        }
    }

    private static int byte2uint(byte b) {
        int i = b;
        if (b < 0) {
            i = 0xff + 1 + b;
        }
        return i;
    }

    private static int byte2int(byte[] res) {
        String str = String.format("%02x%02x%02x%02x", res[3], res[2], res[1], res[0]);
        return Integer.parseInt(str, 16);
    }

    //显示卡图
    private static native byte[] nativeBpgImage(byte[] data);

}
