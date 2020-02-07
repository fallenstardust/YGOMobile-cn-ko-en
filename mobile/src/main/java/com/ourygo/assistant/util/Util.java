package com.ourygo.assistant.util;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Application;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;

import com.ourygo.assistant.service.DuelAssistantService;

import java.util.List;


public class Util {

    public static boolean startDuelService(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (PermissionUtil.isNotificationListenerEnabled(context))
                    context.startForegroundService(new Intent(context, DuelAssistantService.class));
                else
                    return false;
            } else {
                context.startService(new Intent(context, DuelAssistantService.class));
            }
        }
        return true;
    }

    public static Intent getNotificationPermissionInitent(Context context){
        Intent intent = new Intent();
        intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts("package", context.getPackageName(), null);
        intent.setData(uri);
        return intent;
    }

    public static Intent getServicePermissionIntent(Context context){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:"+context.getPackageName()));
        }
        return null;
    }

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

}
