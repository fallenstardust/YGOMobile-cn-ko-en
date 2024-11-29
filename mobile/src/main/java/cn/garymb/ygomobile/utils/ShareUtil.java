package cn.garymb.ygomobile.utils;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.text.TextUtils;
import android.widget.Toast;

import java.io.File;
import java.util.List;

import cn.garymb.ygomobile.lite.R;

public class ShareUtil {
    public static boolean shareText(Context context, String title, String text, String pkgName) {
        boolean ok = false;
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TEXT, text);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (!TextUtils.isEmpty(pkgName)) {
            List<ResolveInfo> matches = context.getPackageManager().queryIntentActivities(intent, 0);
            for (ResolveInfo info : matches) {
                if (info.activityInfo.packageName.toLowerCase().startsWith(pkgName)) {
                    intent.setPackage(info.activityInfo.packageName);
                    try {
                        context.startActivity(intent);
                        ok = true;
                    } catch (Exception e) {
                    }
                    break;
                }
            }
            if (!ok) {
                YGOUtil.showTextToast(context.getString(R.string.no_share_app));
            }
            return ok;
        }
        intent.setPackage(null);
        try {
            context.startActivity(Intent.createChooser(intent, title));
            ok = true;
        } catch (Exception e) {
        }
        return ok;
    }

    public static boolean shareImage(Context context, String title, String imgPath, String pkgName) {
        boolean ok = false;
        Intent intent = new Intent(Intent.ACTION_SEND);
        File f = new File(imgPath);
        if (f != null && f.exists() && f.isFile()) {
            intent.setType("image/jpg");
            Uri u = Uri.fromFile(f);
            intent.putExtra(Intent.EXTRA_STREAM, u);
        }
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (!TextUtils.isEmpty(pkgName)) {
            List<ResolveInfo> matches = context.getPackageManager().queryIntentActivities(intent, 0);
            for (ResolveInfo info : matches) {
                if (info.activityInfo.packageName.toLowerCase().startsWith(pkgName)) {
                    intent.setPackage(info.activityInfo.packageName);
                    try {
                        context.startActivity(intent);
                        ok = true;
                    } catch (Exception e) {
                    }
                    break;
                }
            }
            if (!ok) {
                YGOUtil.showTextToast(R.string.no_share_app);
            }
            return ok;
        }
        intent.setPackage(null);
        try {
            context.startActivity(Intent.createChooser(intent, title));
            ok = true;
        } catch (Exception e) {
        }
        return ok;
    }

}
