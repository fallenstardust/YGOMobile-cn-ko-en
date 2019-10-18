package cn.garymb.ygomobile;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import cn.garymb.ygomobile.core.YGOCore;

import static cn.garymb.ygomobile.core.YGOCore.ACTION_START;
import static cn.garymb.ygomobile.core.YGOCore.ACTION_STOP;

public class GameReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (ACTION_START.equals(action)) {
            //
            YGOCore.gPid = intent.getIntExtra(YGOCore.EXTRA_PID, 0);
            if (GameApplication.isDebug()) {
                Log.w("ygo", "pid=" + YGOCore.gPid);
            }
        } else if (ACTION_STOP.equals(action)) {
            int pid = intent.getIntExtra(YGOCore.EXTRA_PID, 0);
            if (pid == 0 && YGOCore.gPid != 0) {
                pid = YGOCore.gPid;
                if (GameApplication.isDebug()) {
                    Log.w("ygo", "will kill last pid=" + pid);
                }
            }
            if (pid == 0) {
                pid = android.os.Process.myPid();
                if (GameApplication.isDebug()) {
                    Log.w("ygo", "will kill now pid=" + pid);
                }
            }
            try {
                if (GameApplication.isDebug()) {
                    Log.w("ygo", "kill pid=" + pid);
                }
                android.os.Process.killProcess(pid);
            } catch (Exception ignore) { }
        }
    }
}
