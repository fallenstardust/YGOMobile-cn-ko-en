package cn.garymb.ygomobile.utils;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

public class AlipayPayUtils {
    public static boolean openAlipayPayPage(Context context, String url) {
        Toast.makeText(context, "有心就好", Toast.LENGTH_SHORT).show();
        return true;
    }

    /**
     * 发送一个intent
     *
     * @param context
     * @param s
     */
    private static void openUri(Context context, String s) {
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(s));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }
}
