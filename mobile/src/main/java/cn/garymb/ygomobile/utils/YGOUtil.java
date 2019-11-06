package cn.garymb.ygomobile.utils;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Application;
import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import java.util.List;

import cn.garymb.ygomobile.App;
import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ourygo.service.DuelAssistantService;
import cn.garymb.ygomobile.ourygo.util.DuelAssistantManagement;
import cn.garymb.ygomobile.ui.plus.DialogPlus;
import libwindbot.windbot.game.Duel;

public class YGOUtil {

    //提示
    public static void show(String message) {
        Toast.makeText(App.get(), message, Toast.LENGTH_SHORT).show();
    }

    public static int c(int colorId){
        return ContextCompat.getColor(App.get(),colorId);
    }
    public static String s(int stringId){
        return App.get().getResources().getString(stringId);
    }


    //关闭输入法
    public static void closeKeyboard(Activity activity) {
        InputMethodManager inputMethodManager = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        inputMethodManager.hideSoftInputFromWindow((activity).getCurrentFocus().getWindowToken()
                , InputMethodManager.HIDE_NOT_ALWAYS);
    }


    //复制字符串到剪贴板
    public static void copyMessage(Context context, String message) {
        ClipboardManager cmb = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        cmb.setText(message);//复制命令
    }

    public static String getCopyMessage(){
        ClipboardManager cm = (ClipboardManager) App.get().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clipData = cm.getPrimaryClip();
        if (clipData == null)
            return null;
        CharSequence cs = clipData.getItemAt(0).getText();
        final String clipMessage;
        if (cs != null) {
            clipMessage = cs.toString();
        } else {
            clipMessage = null;
        }
        return clipMessage;
    }


    public static void startDuelService(Context context) {
        if (AppsSettings.get().isServiceDuelAssistant()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                DialogPlus dialogPlus = PermissionUtil.isNotificationPermission(context);
                if (dialogPlus == null)
                    context.startForegroundService(new Intent(context, DuelAssistantService.class));
                else
                    dialogPlus.show();
            } else {
                context.startService(new Intent(context, DuelAssistantService.class));
            }
        }
        DuelAssistantManagement.getInstance().setStart(true);
    }

    public static boolean isServiceExisted(Context context, String className) {
        ActivityManager activityManager = (ActivityManager) context
                .getSystemService(Context.ACTIVITY_SERVICE);
        List<ActivityManager.RunningServiceInfo> serviceList = activityManager
                .getRunningServices(Integer.MAX_VALUE);

        if (!(serviceList.size() > 0)) {
            return false;
        }

        for (int i = 0; i < serviceList.size(); i++) {
            ActivityManager.RunningServiceInfo serviceInfo = serviceList.get(i);
            ComponentName serviceName = serviceInfo.service;

            if (serviceName.getClassName().equals(className)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isContextExisted(Context context) {
        if (context != null) {
            if (context instanceof Activity) {
                if (!((Activity)context).isFinishing()) {
                    return true;
                }
            } else if (context instanceof Service) {
                if (isServiceExisted(context, context.getClass().getName())) {
                    return true;
                }
            } else if (context instanceof Application) {
                return true;
            }
        }
        return false;
    }

    //决斗跳转
    public static void duelIntent(Context context, String ip, int port, String name, String password) {
        Intent intent1 = new Intent("ygomobile.intent.action.GAME");
        intent1.putExtra("host", ip);
        intent1.putExtra("port", port);
        intent1.putExtra("user", name);
        intent1.putExtra("room", password);
        //intent1.setPackage("cn.garymb.ygomobile");
        intent1.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent1);
    }

}
