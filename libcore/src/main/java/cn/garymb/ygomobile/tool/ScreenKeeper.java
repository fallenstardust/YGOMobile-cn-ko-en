package cn.garymb.ygomobile.tool;

import android.content.Context;
import android.os.PowerManager;

public class ScreenKeeper {
    //电池管理
    private PowerManager mPM;
    private PowerManager.WakeLock mLock;
    private String TAG = "ScreenKeeper";

    public ScreenKeeper(Context context){
        mPM = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
    }

    public void keep(){
        if (mLock == null) {
            mLock = mPM.newWakeLock(PowerManager.SCREEN_DIM_WAKE_LOCK, TAG);
        }
        mLock.acquire();
    }

    public void release(){
        if (mLock != null) {
            if (mLock.isHeld()) {
                mLock.release();
            }
        }
    }
}
