package com.ourygo.ygomobile.util

import android.app.Activity
import android.text.TextUtils
import cn.garymb.ygomobile.utils.SystemUtils
import com.feihua.dialogutils.util.DialogUtils
import com.google.gson.Gson
import com.ourygo.ygomobile.OYApplication
import com.ourygo.ygomobile.base.listener.OnUpdateListener
import com.ourygo.ygomobile.bean.OYResult
import com.ourygo.ygomobile.bean.UpdateInfo
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException

/**
 * Create By feihua  On 2022/10/15
 */
object UpdateUtil {
    fun checkUpdate(activity: Activity, noUpdateToast: Boolean) {
        if (noUpdateToast) DialogUtils.getInstance(activity).dialogj1(null, "检查更新中")
        findUpdateInfo { updateInfo, exception ->
            activity.runOnUiThread {
                if (noUpdateToast) DialogUtils.getInstance(activity).dis()
                if (!TextUtils.isEmpty(exception)) {
                    if (noUpdateToast) {
                        OYUtil.show("检查更新失败，原因为：$exception")
                        return@runOnUiThread
                    }
                }
                if (updateInfo == null) {
                    if (noUpdateToast) OYUtil.show("喵已经是最新版本了~")
                    return@runOnUiThread
                }
                OYDialogUtil.dialogUpdate(activity, updateInfo)
            }
        }
    }

    private fun findUpdateInfo(onUpdateListener: OnUpdateListener) {
        val map: MutableMap<String, Any> = HashMap()
        map[Record.ARG_NAME] = "ymoy"
        map[Record.ARG_VERSION] = SystemUtils.getVersion(OYApplication.get())
        OkhttpUtil.post(Record.URL_UPDATE_APP, map = map, callback = object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onUpdateListener.onUpdate(null, e.toString())
            }

            @Throws(IOException::class)
            override fun onResponse(call: Call, response: Response) {
                val json = response.body()!!.string()
                try {
                    val jsonObject = JSONObject(json)
                    val oyResult = OYResult(jsonObject)
                    if (oyResult.isException) {
                        onUpdateListener.onUpdate(null, oyResult.exception)
                        return
                    }
                    if (oyResult.data == null) {
                        onUpdateListener.onUpdate(null, null)
                        return
                    }
                    onUpdateListener.onUpdate(
                        Gson().fromJson(oyResult.data.toString(), UpdateInfo::class.java), null
                    )
                } catch (e: JSONException) {
                    onUpdateListener.onUpdate(null, e.toString())
                }
            }
        })
    }
}