package com.ourygo.ygomobile.util

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.TextUtils
import cn.garymb.ygomobile.YGOStarter
import cn.garymb.ygomobile.lite.R
import com.ourygo.ygomobile.ui.activity.WebActivity
import java.io.File

object IntentUtil {
    /*
     *根据包名和入口名应用跳转,返回跳转的intent,适用于无MainActivity的应用等
     *packageName:应用包名
     *activity:入口名
     */
    fun getAppIntent(packageName: String?, activity: String?): Intent {
        val intent = Intent()
        intent.component = ComponentName(packageName!!, activity!!)
        return intent
    }

    //应用跳转
    @JvmStatic
    fun getAppIntent(context: Context, packageName: String?): Intent? {
        val pm = context.packageManager
        return pm.getLaunchIntentForPackage(packageName!!)
    }

    //决斗跳转
    fun duelIntent(context: Context, ip: String?, dk: Int, name: String?, password: String?) {
        val intent1 = Intent("ygomobile.intent.action.GAME")
        intent1.putExtra("host", ip)
        intent1.putExtra("port", dk)
        intent1.putExtra("user", name)
        intent1.putExtra("room", password)
        intent1.setPackage("cn.garymb.ygomobile")
        intent1.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent1)
    }

    fun deckEditIntent(context: Context, deckPath: String?) {
        val intent1 = Intent("ygomobile.intent.action.DECK")
        intent1.putExtra(Intent.EXTRA_TEXT, deckPath)
        context.startActivity(intent1)
    }

    //Android获取一个用于打开APK文件的intent
    fun getApkFileIntent(param: String): Intent {
        val intent = Intent()
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.action = Intent.ACTION_VIEW
        val uri = Uri.fromFile(File(param))
        intent.setDataAndType(uri, "application/vnd.android.package-archive")
        return intent
    }

    @JvmStatic
    fun getUrlIntent(url: String?): Intent {
        return Intent(Intent.ACTION_VIEW, Uri.parse(url))
    }

    @JvmStatic
    fun getEZIntent(context: Context): Intent {
        var  intent: Intent?
        intent = getAppIntent(context, "")
        if (intent == null) {
            OYUtil.show("请下载OURYGO-EZ后食用")
            intent = getUrlIntent(Record.DOWNLOAD_URL_EZ)
        }
        return intent
    }

    @JvmOverloads
    @JvmStatic
    fun startYGOReplay(activity: Activity?, replayName: String? = null, isKeep: Boolean = false) {
        if (TextUtils.isEmpty(replayName)) {
            if (isKeep) YGOStarter.startGame(activity, null, "-k", "-r") else YGOStarter.startGame(
                activity,
                null,
                "-r"
            )
        } else {
            YGOStarter.startGame(activity, null, "-r", replayName)
        }
    }

    fun startYGOGame(activity: Activity?) {
        YGOStarter.startGame(activity, null)
    }

    @JvmOverloads
    @JvmStatic
    fun startYGOEndgame(activity: Activity?, endgameName: String? = null, isKeep: Boolean = false) {
        if (TextUtils.isEmpty(endgameName)) {
            if (isKeep) YGOStarter.startGame(activity, null, "-k", "-s") else YGOStarter.startGame(
                activity,
                null,
                "-s"
            )
        } else {
            YGOStarter.startGame(activity, null, "-s", endgameName)
        }
    }


    @JvmOverloads
    @JvmStatic
    fun startYGODeck(activity: Activity?, deckCategary: String? = null, deckName: String? = null) {
        val list: MutableList<String?> = ArrayList()
        if (!TextUtils.isEmpty(deckCategary) && OYUtil.s(R.string.category_Uncategorized) != deckCategary) {
            list.add(Record.YGO_ARG_DECK_CATEGORY)
            list.add(deckCategary)
        }
        if (TextUtils.isEmpty(deckName)) {
            list.add("-k")
            list.add("-d")
        } else {
            list.add("-d")
            list.add(deckName)
        }
        val ss = arrayOfNulls<String>(list.size)
        for (i in list.indices) ss[i] = list[i]
        YGOStarter.startGame(activity, null, *ss)
    }

    @JvmStatic
    fun getWebIntent(context: Context?, url: String?): Intent {
        val intent = Intent(context, WebActivity::class.java)
        intent.putExtra(WebActivity.ARG_URL, url)
        return intent
    }
}