package com.ourygo.assistant.util;

import android.content.Context;
import android.content.Intent;
import android.os.Build;

import com.ourygo.assistant.service.DuelAssistantService;

import cn.garymb.ygomobile.ui.plus.DialogPlus;
import cn.garymb.ygomobile.utils.PermissionUtil;

public class Util {
    public static void startDuelService(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
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
    }
}
