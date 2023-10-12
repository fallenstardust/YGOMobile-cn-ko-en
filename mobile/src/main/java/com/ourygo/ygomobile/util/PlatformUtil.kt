package com.ourygo.ygomobile.util

import android.content.Context

/**
 * Create By feihua  On 2021/10/18
 */
object PlatformUtil {
    const val PACKAGE_WECHAT = "com.tencent.mm"
    const val PACKAGE_MOBILE_QQ = "com.tencent.mobileqq"
    const val PACKAGE_QZONE = "com.qzone"
    const val PACKAGE_SINA = "com.sina.weibo"

    // 判断是否安装指定app
    fun isInstallApp(context: Context, app_package: String?): Boolean {
        val packageManager = context.packageManager
        val pInfo = packageManager.getInstalledPackages(0)
        if (pInfo != null) {
            for (i in pInfo.indices) {
                val pn = pInfo[i].packageName
                if (app_package == pn) {
                    return true
                }
            }
        }
        return false
    }
}