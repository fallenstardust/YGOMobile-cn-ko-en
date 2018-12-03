package cn.garymb.ygomobile.utils;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.support.v4.app.NotificationManagerCompat;

import cn.garymb.ygomobile.App;
import cn.garymb.ygomobile.ui.plus.DialogPlus;

public class PermissionUtil {

    //判断应用是否开启了通知权限
    public static boolean isNotificationListenerEnabled(Context context) {
        return NotificationManagerCompat.from(context).areNotificationsEnabled();
    }

    public static DialogPlus isNotificationPermission(Context context){
        if(!isNotificationListenerEnabled(context)){
            DialogPlus dialog = new DialogPlus(context);
            dialog.setTitle("权限缺失提示");
            dialog.setMessage("喵没给通知权限呢~\n开启通知权限可以快速关闭决斗助手功能");
            dialog.setLeftButtonText("去开启");
            dialog.setRightButtonText("取消");
            dialog.setLeftButtonListener(new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    // 根据isOpened结果，判断是否需要提醒用户跳转AppInfo页面，去打开App通知权限
                    Intent intent = new Intent();
                    intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    Uri uri = Uri.fromParts("package", App.get().getPackageName(), null);
                    intent.setData(uri);
                    context.startActivity(intent);

                    dialog.dismiss();
                }
            });

            dialog.setRightButtonListener(new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    dialog.dismiss();
                }
            });
            dialog.show();
            return dialog;
        }
        return null;
    }

    //判断是否有悬浮窗权限
    public static boolean isServicePermission(Context context, boolean isIntentPermission) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(context)) {
                if (isIntentPermission) {
                    DialogPlus dialog = new DialogPlus(context);
                    dialog.setTitle("权限缺失提示");
                    dialog.setMessage("喵没给悬浮窗权限呢~\n开启悬浮窗权限可进行快速加入决斗房间");
                    dialog.setLeftButtonText("去开启");
                    dialog.setRightButtonText("取消");
                    dialog.setLeftButtonListener(new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            context.startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION));
                            dialog.dismiss();
                        }
                    });
                    dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                        @Override
                        public void onDismiss(DialogInterface dialogInterface) {
                            isNotificationPermission(context);
                        }
                    });
                    dialog.setRightButtonListener(new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            dialog.dismiss();
                        }
                    });
                    dialog.show();
                }
                return false;
            }
        }
        return true;
    }



}
