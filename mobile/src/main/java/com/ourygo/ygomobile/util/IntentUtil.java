package com.ourygo.ygomobile.util;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.text.TextUtils;

import com.ourygo.ygomobile.ui.activity.WebActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.YGOStarter;
import cn.garymb.ygomobile.lite.R;

public class IntentUtil {

    /*
     *根据包名和入口名应用跳转,返回跳转的intent,适用于无MainActivity的应用等
     *packageName:应用包名
     *activity:入口名
     */
    public static Intent getAppIntent(String packageName, String activity) {
        Intent intent = new Intent();
        intent.setComponent(new ComponentName(packageName, activity));
        return intent;
    }

    //应用跳转
    public static Intent getAppIntent(Context context, String packageName) {
        Context c = context;
        PackageManager pm = c.getPackageManager();
        return pm.getLaunchIntentForPackage(packageName);
    }

    //决斗跳转
    public static void duelIntent(Context context, String ip, int dk, String name, String password) {
        Intent intent1 = new Intent("ygomobile.intent.action.GAME");
        intent1.putExtra("host", ip);
        intent1.putExtra("port", dk);
        intent1.putExtra("user", name);
        intent1.putExtra("room", password);
        intent1.setPackage("cn.garymb.ygomobile");
        intent1.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent1);
    }

    public static void deckEditIntent(Context context, String deckPath) {
        Intent intent1 = new Intent("ygomobile.intent.action.DECK");
        intent1.putExtra(Intent.EXTRA_TEXT, deckPath);
        context.startActivity(intent1);
    }


    //Android获取一个用于打开APK文件的intent
    public static Intent getApkFileIntent(Context context, String param) {

        Intent intent = new Intent();
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setAction(Intent.ACTION_VIEW);
        Uri uri = Uri.fromFile(new File(param));
        intent.setDataAndType(uri, "application/vnd.android.package-archive");
        return intent;
    }

    public static Intent getUrlIntent(String url) {
        return new Intent(Intent.ACTION_VIEW, Uri.parse(url));
    }

    public static Intent getEZIntent(Context context) {
        Intent intent;
        intent = getAppIntent(context, "");
        if (intent == null) {
            OYUtil.show("请下载OURYGO-EZ后食用");
            intent = getUrlIntent(Record.DOWNLOAD_URL_EZ);
        }
        return intent;
    }

    public static void startYGOReplay(Activity activity) {
        startYGOReplay(activity, null);
    }

    public static void startYGOReplay(Activity activity, String replayName) {
        startYGOReplay(activity, replayName, false);
    }

    public static void startYGOReplay(Activity activity, String replayName, boolean isKeep) {
        if (TextUtils.isEmpty(replayName)) {
            if (isKeep)
                YGOStarter.startGame(activity, null, "-k", "-r");
            else
                YGOStarter.startGame(activity, null, "-r");
        } else {
            YGOStarter.startGame(activity, null, "-r", replayName);
        }
    }

    public static void startYGOGame(Activity activity) {
        YGOStarter.startGame(activity, null);
    }

    public static void startYGOEndgame(Activity activity) {
        startYGOEndgame(activity, null);
    }

    public static void startYGOEndgame(Activity activity, String endgameName) {
        startYGOEndgame(activity, endgameName, false);
    }

    public static void startYGOEndgame(Activity activity, String endgameName, boolean isKeep) {
        if (TextUtils.isEmpty(endgameName)) {
            if (isKeep)
                YGOStarter.startGame(activity, null, "-k", "-s");
            else
                YGOStarter.startGame(activity, null, "-s");
        } else {
            YGOStarter.startGame(activity, null, "-s", endgameName);
        }
    }

    public static void startYGODeck(Activity activity) {
        startYGODeck(activity, null, null);
    }

    public static void startYGODeck(Activity activity, String deckName) {
        startYGODeck(activity, null, deckName);
    }

    public static void startYGODeck(Activity activity, String deckCategary, String deckName) {
        List<String> list = new ArrayList<>();

        if (!TextUtils.isEmpty(deckCategary)&&!OYUtil.s(R.string.category_Uncategorized).equals(deckCategary)) {
            list.add(Record.YGO_ARG_DECK_CATEGORY);
            list.add(deckCategary);
        }

        if (TextUtils.isEmpty(deckName)) {
            list.add("-k");
            list.add("-d");
        } else {
            list.add("-d");
            list.add(deckName);
        }

        String[] ss = new String[list.size()];
        for (int i = 0; i < list.size(); i++)
            ss[i] = list.get(i);
        YGOStarter.startGame(activity, null, ss);
    }

    public static Intent getWebIntent(Context context, String url) {
        Intent intent = new Intent(context, WebActivity.class);
        intent.putExtra(WebActivity.ARG_URL, url);

        return intent;
    }

}
