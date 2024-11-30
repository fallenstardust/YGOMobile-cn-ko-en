package cn.garymb.ygomobile.utils;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.ourygo.lib.duelassistant.util.PermissionUtil;
import com.ourygo.lib.duelassistant.util.Util;

import org.jdeferred.android.AndroidDeferredManager;

import java.time.Duration;

import cn.garymb.ygomobile.App;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.plus.DialogPlus;
import cn.garymb.ygomobile.ui.plus.VUiKit;

public class YGOUtil {
    private static Toast mToast;

    //提示
    public static void showTextToast(int resId) {
        showTextToast(s(resId));
    }

    public static void showTextToast(String message) {
        showTextToast(Gravity.BOTTOM, message, Toast.LENGTH_SHORT);
    }

    public static void showTextToast(int resId, int duration) {
        showTextToast(s(resId), duration);
    }

    public static void showTextToast(String message, int duration) {
        showTextToast(Gravity.BOTTOM, message, duration);
    }

    public static void showTextToast(int gravity, String message) {
        showTextToast(gravity, message, Toast.LENGTH_SHORT);
    }

    public static void showTextToast(int gravity, int resId, int duration) {
        showTextToast(gravity, s(resId), duration);
    }

    public static void showTextToast(int gravity, String message, int duration) {
        mToast = Toast.makeText(App.get(), message, duration);
        mToast.setGravity(gravity,0, 0);
        mToast.setText(message);
        mToast.show();
    }

    public static void show(int id, Object... args) {
        Context context = App.get();
        final String str = args.length == 0 ? context.getString(id) : context.getString(id, args);
        VUiKit.post(() -> {
            if (mToast == null) {
                showTextToast(str);
            } else {
                mToast.setText(str);
            }
            mToast.show();
        });
    }

    public static void show(String str) {
        VUiKit.post(() -> {
            if (mToast == null) {
                showTextToast(str);
            } else {
                mToast.setText(str);
            }
            mToast.show();
        });
    }

    public static int getScreenWidth() {
        return Resources.getSystem().getDisplayMetrics().widthPixels;
    }

    public static int getScreenHeiget() {
        return Resources.getSystem().getDisplayMetrics().heightPixels;
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
        return "https://cdn02.moecube.com:444/ygomobile-images/" + code + ".jpg?version=3.0.0";
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

    public static boolean isVisBottom(RecyclerView recyclerView){
        LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
        //屏幕中最后一个可见子项的position
        int lastVisibleItemPosition = layoutManager.findLastCompletelyVisibleItemPosition();
        //当前屏幕所看到的子项个数
        int visibleItemCount = layoutManager.getChildCount();
        //当前RecyclerView的所有子项个数
        int totalItemCount = layoutManager.getItemCount();
        //RecyclerView的滑动状态
        int state = recyclerView.getScrollState();

        final int offset = recyclerView.computeVerticalScrollOffset();
        final int range = recyclerView.computeVerticalScrollRange() - recyclerView.computeVerticalScrollExtent();
        return visibleItemCount > 0 && lastVisibleItemPosition >= totalItemCount - 3 && state == RecyclerView.SCROLL_STATE_IDLE;
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
                        Log.e("YGOUtil", "当前版本" + Build.VERSION.SDK_INT);
                        Log.e("YGOUtil", "o的版本" + Build.VERSION_CODES.O);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !PermissionUtil.isNotificationListenerEnabled(context)) {
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

    /**
     * dp转px
     */
    public static int dp2px(float dp) {
        if(dp == 0){
            return 0;
        }
        float density = App.get().getResources().getDisplayMetrics().density;
        int px = Math.round(dp * density);// 4.9->5 4.4->4
        return px;
    }

    public static float px2dp(int px) {
        float density = App.get().getResources().getDisplayMetrics().density;
        float dp = px / density;
        return dp;
    }
}



