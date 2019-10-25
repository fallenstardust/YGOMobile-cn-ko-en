package cn.garymb.ygomobile;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Process;
import android.util.Log;

import cn.garymb.ygomobile.core.YGOCore;

/***
 * 解决重启游戏卡图变黑
 */
public class AutoQuitReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (YGOCore.ACTION_END.equals(intent.getAction())) {
            int pid = intent.getIntExtra(YGOCore.EXTRA_PID, -1);
            if (pid != 0) {
                try {
                    Log.e("ygomobile", "stop game pid=" + pid);
                    Process.killProcess(pid);
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            }
        } else if (YGOCore.ACTION_START.equals(intent.getAction())) {

        }
    }
}
