package com.ourygo.ygomobile.util

import android.nfc.Tag
import android.util.Log

/**
 * Create By feihua  On 2020/4/12
 */
object LogUtil {
    private const val TAG = "YGOOY"
    private var lastTime: Long = 0
    var sumTime: Long = 0
        private set
    private const val isDebug = true

    @JvmStatic
    fun time(tag: String, message: String) {
        val time = System.currentTimeMillis()
        if (lastTime == 0L) lastTime = time
        d("【time】$tag", message + "  " + (time - lastTime))
        sumTime += time - lastTime
        lastTime = time
    }

    fun setLastTime(lastTime: Long) {
        LogUtil.lastTime = lastTime
    }

    fun setLastTime() {
        lastTime = System.currentTimeMillis()
    }

    @JvmStatic
    fun printSumTime(tag: String) {
        d("【time】$tag", "SumTime:  $sumTime")
    }

    fun printSumTimeAndClear(tag: String?) {
        d(tag, "SumTime:  $sumTimeAndClear")
    }

    val sumTimeAndClear: Long
        get() {
            val sumTime1 = sumTime
            sumTime = 0
            return sumTime1
        }

    @JvmStatic
    fun e(tag: String?, message: String?) {
        if (isDebug) Log.e(TAG, "$tag $message")
    }

    @JvmStatic
    fun d(tag: String?, message: String?) {
        if (isDebug) Log.d(TAG, "$tag $message")
    }
}