package com.ourygo.ygomobile.util

import android.util.Log

object DebugUtil {
    private var lastTime: Long = 0
    fun time(tag: String?) {
        val currentTime = System.currentTimeMillis()
        Log.e(tag, (currentTime - lastTime).toString() + "")
        lastTime = currentTime
    }
}