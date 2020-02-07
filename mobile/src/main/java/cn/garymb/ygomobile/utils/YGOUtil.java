package cn.garymb.ygomobile.utils;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import com.ourygo.assistant.util.PermissionUtil;
import com.ourygo.assistant.util.Util;

import cn.garymb.ygomobile.App;
import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.plus.DialogPlus;

public class YGOUtil {

    //提示
    public static void show(String message) {
        Toast.makeText(App.get(), message, Toast.LENGTH_SHORT).show();
    }

    public static int c(int colorId) {
        return ContextCompat.getColor(App.get(), colorId);
    }

    public static String s(int stringId) {
        return App.get().getResources().getString(stringId);
    }

    /**
     * 根据卡密获取高清图下载地址
     *
     * @param code 卡密
     * @return 高清图url
     */
    public static String getCardImageDetailUrl(int code) {
        return "https://cdn01.moecube.com/images/ygomobile/" + code + ".jpg";
    }


    //关闭输入法
    public static void closeKeyboard(Activity activity) {
        InputMethodManager inputMethodManager = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (inputMethodManager == null)
            return;
        View view = activity.getCurrentFocus();
        if (view == null)
            return;
        inputMethodManager.hideSoftInputFromWindow(view.getWindowToken()
                , InputMethodManager.HIDE_NOT_ALWAYS);
    }


    //复制字符串到剪贴板
    public static void copyMessage(Context context, String message) {
        ClipboardManager cmb = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cmb == null)
            return;
        cmb.setPrimaryClip(ClipData.newPlainText(null, message));//复制命令
    }

    public static void startDuelService(Context context) {
        if (AppsSettings.get().isServiceDuelAssistant()) {
            if (!Util.startDuelService(context)) {
                getNotificationPermissionDialog(context).show();
            }
        }
    }


    //判断是否有悬浮窗权限
    public static boolean isServicePermission(Context context, boolean isIntentPermission) {
        if (!PermissionUtil.isServicePermission(context)) {
            if (isIntentPermission) {
                DialogPlus dialog = new DialogPlus(context);
                dialog.setTitle(R.string.tip);
                dialog.setMessage(R.string.SYSTEM_ALERT_WINDOW);
                dialog.setLeftButtonText(R.string.to_open);
                dialog.setRightButtonText(R.string.Cancel);
                dialog.setLeftButtonListener(new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        dialog.dismiss();
                        context.startActivity(Util.getServicePermissionIntent(context));
                    }
                });
                dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialogInterface) {
                        Log.e("YGOUtil","当前版本"+Build.VERSION.SDK_INT);
                        Log.e("YGOUtil","o的版本"+Build.VERSION_CODES.O);
                        if (Build.VERSION.SDK_INT>=Build.VERSION_CODES.O&&!PermissionUtil.isNotificationListenerEnabled(context)) {
                            getNotificationPermissionDialog(context).show();
                        }
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
        return true;
    }

    public static DialogPlus getNotificationPermissionDialog(Context context) {

            DialogPlus dialog = new DialogPlus(context);
            dialog.setTitle(R.string.tip);
            dialog.setMessage(R.string.EXPAND_STATUS_BAR);
            dialog.setLeftButtonText(R.string.to_open);
            dialog.setRightButtonText(R.string.Cancel);
            dialog.setLeftButtonListener(new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    // 根据isOpened结果，判断是否需要提醒用户跳转AppInfo页面，去打开App通知权限
                    context.startActivity(Util.getNotificationPermissionInitent(context));
                    dialog.dismiss();
                }
            });

            dialog.setRightButtonListener(new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialogInterface, int i) {
                    dialog.dismiss();
                }
            });
            return dialog;
    }


}



