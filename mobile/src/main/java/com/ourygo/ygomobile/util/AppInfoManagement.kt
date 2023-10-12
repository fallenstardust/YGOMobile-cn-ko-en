package com.ourygo.ygomobile.util

import android.content.Context
import cn.garymb.ygomobile.App
import cn.garymb.ygomobile.utils.SystemUtils
import com.ourygo.ygomobile.OYApplication

/**
 * Create By feihua  On 2022/8/21
 */
object AppInfoManagement {
    private var isNewVersion = false
    fun isNewVersion(): Boolean {
        if (isNewVersion) return true
        val sh = App.get().getSharedPreferences("AppVersion", Context.MODE_PRIVATE)
        val vercode = SystemUtils.getVersion(OYApplication.get())
        val vn = sh.getInt("versionCode", 0)
        isNewVersion = if (vn < vercode) {
            sh.edit().putInt("versionCode", vercode).apply()
            true
        } else {
            false
        }
        return isNewVersion
    }

    fun setNewVersion(newVersion: Boolean) {
        isNewVersion = newVersion
    }

    //软件关闭时一定要调用
    fun close() {
        isNewVersion = false
    }
}