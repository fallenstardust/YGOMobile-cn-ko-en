package cn.garymb.ygomobile.utils;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import androidx.core.content.ContextCompat;

import cn.garymb.ygomobile.App;
import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.ui.plus.DialogPlus;
import com.ourygo.assistant.service.DuelAssistantService;
import com.ourygo.assistant.util.Util;

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

    /**
     * 根据卡密获取高清图下载地址
     * @param code  卡密
     * @return  高清图url
     */
    public static String getCardImageDetailUrl(int code){
        return "https://code.mycard.moe/fallenstardust/ygoimage/raw/master/"+code+".jpg";
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
        if (cmb==null)
            return;
        cmb.setPrimaryClip(ClipData.newPlainText(null, message));//复制命令
    }

    public static void startDuelService(Context context) {
        if (AppsSettings.get().isServiceDuelAssistant()) {
            Util.startDuelService(context);
        }
    }

}
