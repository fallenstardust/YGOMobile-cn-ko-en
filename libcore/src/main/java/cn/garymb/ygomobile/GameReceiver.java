package cn.garymb.ygomobile;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import cn.garymb.ygomobile.core.IrrlichtBridge;

import static cn.garymb.ygomobile.core.IrrlichtBridge.ACTION_START;
import static cn.garymb.ygomobile.core.IrrlichtBridge.ACTION_STOP;

public class GameReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (ACTION_START.equals(action)) {
            //
            IrrlichtBridge.gPid = intent.getIntExtra(IrrlichtBridge.EXTRA_PID, 0);
//            Log.w("ygo", "pid=" + IrrlichtBridge.gPid);
        } else if (ACTION_STOP.equals(action)) {
            int pid = intent.getIntExtra(IrrlichtBridge.EXTRA_PID, 0);
            if (pid == 0 && IrrlichtBridge.gPid != 0) {
                pid = IrrlichtBridge.gPid;
//                Log.w("ygo", "will kill last pid=" + pid);
            }
            if (pid == 0) {
                pid = android.os.Process.myPid();
//                Log.w("ygo", "will kill now pid=" + pid);
            }
            try {
//                Log.w("ygo", "kill pid=" + pid);
                android.os.Process.killProcess(pid);
            } catch (Exception e) {
//ignore
            }
        }
    }
}
