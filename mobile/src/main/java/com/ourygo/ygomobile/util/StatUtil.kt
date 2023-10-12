package com.ourygo.ygomobile.util

import android.content.Context
import android.util.Log
import com.umeng.analytics.MobclickAgent

object StatUtil {
    fun onResume(name: String) {
//		SdkInitManagement.getInstance().initUmengSdk();
////		MobclickAgent.onPageStart(context.getClass().getName());
//		MobclickAgent.onResume(context);
        MobclickAgent.onPageStart(name)
        Log.e("UMLog", "Fragment参数$name")
    }

    fun onPause(name: String) {
//		SdkInitManagement.getInstance().initUmengSdk();
////		MobclickAgent.onPageEnd(context.getClass().getName());
//		MobclickAgent.onPause(context);
        MobclickAgent.onPageEnd(name)
        Log.e("UMLog", "Fragment关闭参数$name")
    }

    @JvmOverloads
    fun onResume(context: Context, isFragmentActivity: Boolean = false) {
        if (!isFragmentActivity) {
            MobclickAgent.onPageStart(context.javaClass.name)
            Log.e("UMLog", "调用状态" + context.javaClass.name)
        }
        //		SdkInitManagement.getInstance().initUmengSdk();
        MobclickAgent.onResume(context)

//		MobclickAgent.onResume(context);
    }

    @JvmOverloads
    fun onPause(context: Context, isFragmentActivity: Boolean = false) {
        if (!isFragmentActivity) {
            MobclickAgent.onPageEnd(context.javaClass.name)
            Log.e("UMLog", "关闭状态" + context.javaClass.name)
        }
        //		SdkInitManagement.getInstance().initUmengSdk();
        MobclickAgent.onPause(context)

//		MobclickAgent.onPause(context);
    }

    fun login(userID: String?) {
//		SdkInitManagement.getInstance().initUmengSdk();
//		MobclickAgent.onProfileSignIn(userID);
    }

    fun logout() {
//		SdkInitManagement.getInstance().initUmengSdk();
//		MobclickAgent.onProfileSignOff();
    }

    fun onEvent(context: Context?, eventID: String?, map: Map<String?, String?>?) {
//		SdkInitManagement.getInstance().initUmengSdk();
//		MobclickAgent.onEvent(context, eventID, map);
    }

    @JvmStatic
    fun onKillProcess(context: Context?) {
        MobclickAgent.onKillProcess(context)
        //		SdkInitManagement.getInstance().initUmengSdk();
//		MobclickAgent.onKillProcess(context);
    }

    fun onLogin(nameID: String?) {
        MobclickAgent.onProfileSignIn(nameID)
    }

    fun onLogout() {
        MobclickAgent.onProfileSignOff()
    }
}