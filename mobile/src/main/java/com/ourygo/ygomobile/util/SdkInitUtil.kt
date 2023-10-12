package com.ourygo.ygomobile.util

import android.util.Log
import cn.garymb.ygomobile.App
import com.tencent.smtt.sdk.QbSdk
import com.tencent.smtt.sdk.QbSdk.PreInitCallback

class SdkInitUtil private constructor() {
    private var isInitX5WebView = false
    fun initX5WebView() {
        if (!isInitX5WebView) {
            val cb: PreInitCallback = object : PreInitCallback {
                override fun onViewInitFinished(arg0: Boolean) {
                    //x5內核初始化完成的回调，为true表示x5内核加载成功，否则表示x5内核加载失败，会自动切换到系统内核。
                    Log.e("SdkInitUtil", "加载情况$arg0")
                    isInitX5WebView = arg0
                    //  Toast.makeText(getActivity(), "加载成功", Toast.LENGTH_LONG).show();
                }

                override fun onCoreInitFinished() {}
            }
            //x5内核初始化接口
            QbSdk.initX5Environment(App.get(), cb)
        }
    }

    companion object {
        @JvmStatic
        val instance = SdkInitUtil()
    }
}