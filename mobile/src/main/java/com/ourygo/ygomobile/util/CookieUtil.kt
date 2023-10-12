package com.ourygo.ygomobile.util

import android.os.Build
import android.util.Log
import com.ourygo.ygomobile.OYApplication
import com.tencent.smtt.sdk.CookieManager
import com.tencent.smtt.sdk.CookieSyncManager

object CookieUtil {
    /**
     * 为指定的url添加cookie
     * @param url  url
     * @param cookieContent cookie内容
     */
    fun setCookie(url: String?, cookieContent: String?) {
        val cm = CookieManager.getInstance()
        val csm = CookieSyncManager.createInstance(OYApplication.get())
        cm.setAcceptCookie(true)
        cm.setCookie(url, cookieContent)

        /* //api21以上提供了回调接口来确认cookie是否设置成功
        cm.setCookie(url, cookieContent, new ValueCallback<Boolean>() {
            @Override
            public void onReceiveValue(Boolean value) {
            }
        });*/if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP) {
            cm.flush()
        } else {
            csm.sync()
        }
    }

    // 移除指定url关联的所有cookie
    fun remove(url: String?) {
        val cm = CookieManager.getInstance()
        Log.e("Cookie", "内容" + cm.getCookie(url))
        for (cookie in cm.getCookie(url).split("; ".toRegex()).dropLastWhile { it.isEmpty() }
            .toTypedArray()) {
            cm.setCookie(url, cookie.split("=".toRegex()).dropLastWhile { it.isEmpty() }
                .toTypedArray()[0] + "=")
        }
        // 写入磁盘
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().flush()
        } else {
            CookieSyncManager.getInstance().sync()
        }
    }

    // sessionOnly 为true表示移除所有会话cookie，否则移除所有cookie
    fun remove(sessionOnly: Boolean) {
        Log.e("CookUtil", "准备删除")
        val cm = CookieManager.getInstance()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            if (sessionOnly) {
                cm.removeSessionCookies(null)
                Log.e("CookUtil", "删除cookie1")
            } else {
                cm.removeAllCookies(null)
                Log.e("CookUtil", "删除cookie")
            }
        } else {
            if (sessionOnly) {
                cm.removeSessionCookie()
                Log.e("CookUtil", "删除cookie2")
            } else {
                cm.removeAllCookie()
                Log.e("CookUtil", "删除cookie3")
            }
        }
        // 写入磁盘
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().flush()
        } else {
            CookieSyncManager.getInstance().sync()
        }
    }
}