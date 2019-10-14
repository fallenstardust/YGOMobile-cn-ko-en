package cn.garymb.ygomobile.core;

import android.graphics.Bitmap;

import java.io.InputStream;

@Deprecated
public class IrrlichtBridge {
    public static final String ACTION_START = YGOCore.ACTION_START;
    public static final String ACTION_STOP = YGOCore.ACTION_STOP;
    public static final String EXTRA_PID = YGOCore.EXTRA_PID;

    public static Bitmap getBpgImage(InputStream inputStream, Bitmap.Config config) {
        return YGOCore.getBpgImage(inputStream, config);
    }
}
