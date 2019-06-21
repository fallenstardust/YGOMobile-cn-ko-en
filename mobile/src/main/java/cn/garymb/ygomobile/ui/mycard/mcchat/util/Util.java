package cn.garymb.ygomobile.ui.mycard.mcchat.util;

import android.app.Activity;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.ui.plus.DialogPlus;
import cn.garymb.ygomobile.ui.plus.ServiceDuelAssistant;
import cn.garymb.ygomobile.utils.PermissionUtil;

public class Util {
    //提示
    public static void show(Context context, String message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }

    //关闭输入法
    public static void closeKeyboard(Activity activity) {
        InputMethodManager inputMethodManager = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow((activity).getCurrentFocus().getWindowToken()
                , InputMethodManager.HIDE_NOT_ALWAYS);
    }


    //复制字符串到剪贴板
    public static void fzMessage(Context context, String message) {
        ClipboardManager cmb = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        cmb.setText(message);//复制命令
    }

    public static void startDuelService(Context context) {
        if (AppsSettings.get().isServiceDuelAssistant()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                DialogPlus dialogPlus = PermissionUtil.isNotificationPermission(context);
                if (dialogPlus == null)
                    context.startForegroundService(new Intent(context, ServiceDuelAssistant.class));
                else
                    dialogPlus.show();
            } else {
                context.startService(new Intent(context, ServiceDuelAssistant.class));
            }
        }
    }

}
