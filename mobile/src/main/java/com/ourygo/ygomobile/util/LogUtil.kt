package com.ourygo.ygomobile.util

import android.util.Log

/**
 * Create By feihua  On 2020/4/12
 */
object LogUtil {
    private var lastTime: Long = 0
    var sumTime: Long = 0
        private set
    private const val isDebug = true
    @JvmStatic
    fun time(tag: String, message: String) {
        val time = System.currentTimeMillis()
        if (lastTime == 0L) lastTime = time
        Log.e("【time】$tag", message + "  " + (time - lastTime))
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
        Log.e("【time】$tag", "SumTime：  " + sumTime)
    }

    fun printSumTimeAndClear(tag: String?) {
        Log.e(tag, "SumTime：  " + sumTimeAndClear)
    }

    val sumTimeAndClear: Long
        get() {
            val sumTime1 = sumTime
            sumTime = 0
            return sumTime1
        }

    @JvmStatic
    fun e(tag: String?, message: String?) {
        if (isDebug) Log.e(tag, message!!)
    }

    @JvmStatic
    fun d(tag: String?, message: String?) {
        if (isDebug) Log.d(tag, message!!)
    }
}