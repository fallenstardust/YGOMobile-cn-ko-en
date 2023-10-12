package com.ourygo.ygomobile.util

import android.net.Uri
import android.text.TextUtils
import android.util.Log
import cn.garymb.ygomobile.ui.mycard.bean.McUser
import cn.garymb.ygomobile.utils.FileLogUtil
import com.ourygo.ygomobile.base.listener.OnMcMatchListener
import com.ourygo.ygomobile.base.listener.OnMyCardNewsQueryListener
import com.ourygo.ygomobile.base.listener.OnUserDuelInfoQueryListener
import com.ourygo.ygomobile.bean.McNews
import com.ourygo.ygomobile.bean.OYHeader
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import org.json.JSONException
import org.litepal.LitePal
import java.io.IOException

object MyCardUtil {
    const val MATCH_TYPE_ATHLETIC = 0
    const val MATCH_TYPE_ENTERTAIN = 1

    //获取mc新闻列表
    @JvmStatic
    fun findMyCardNews(onMyCardNewsQueryListener: OnMyCardNewsQueryListener) {
        OkhttpUtil.get(Record.MYCARD_NEWS_URL, callback = object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onMyCardNewsQueryListener.onMyCardNewsQuery(null, e.toString())
            }

            @Throws(IOException::class)
            override fun onResponse(call: Call, response: Response) {
                val json = response.body()!!.string()
                try {
                    val mcNewsList = JsonUtil.getMyCardNewsList(json)
                    LitePal.deleteAll(McNews::class.java)
                    LitePal.saveAll(mcNewsList)
                    onMyCardNewsQueryListener.onMyCardNewsQuery(mcNewsList, null)
                } catch (e: JSONException) {
                    onMyCardNewsQueryListener.onMyCardNewsQuery(null, e.toString())
                }
            }
        })
    }

    @JvmStatic
    fun findUserDuelInfo(
        userName: String,
        onUserDuelInfoQueryListener: OnUserDuelInfoQueryListener
    ) {
        val map: MutableMap<String?, Any> = HashMap()
        map[Record.ARG_USERNAME] = userName
        OkhttpUtil.get(Record.MYCARD_USER_DUEL_URL, map, callback = object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onUserDuelInfoQueryListener.onUserDuelInfoQuery(null, e.toString())
            }

            @Throws(IOException::class)
            override fun onResponse(call: Call, response: Response) {
                val json = response.body()!!.string()
                onUserDuelInfoQueryListener.onUserDuelInfoQuery(
                    JsonUtil.getUserDuelInfo(json),
                    null
                )
            }
        })
    }

    fun getMyCardNewsData(time: String): String {
        return time.substring(time.indexOf("-") + 1, time.indexOf("T"))
    }

    fun getAvatarUrl(userName: String): String {
        return "https://api.moecube.com/accounts/users/$userName.png"
    }

    @JvmStatic
    fun cancelMatch() {
        OkhttpUtil.cancelTag(Record.ARG_ARENA)
    }

    @JvmStatic
    fun startMatch(mcUser: McUser, matchType: Int, onMcMatchListener: OnMcMatchListener) {
        if (TextUtils.isEmpty(mcUser.username) || mcUser.external_id == 0) {
            onMcMatchListener.onMcMatch(null, null, "用户信息为空，请退出重新登录")
            return
        }
        val uri = Uri.parse(Record.URL_MC_MATCH).buildUpon()
        uri.appendQueryParameter(Record.ARG_LOCALE, Record.ARG_ZH_CN)
        when (matchType) {
            MATCH_TYPE_ATHLETIC -> uri.appendQueryParameter(Record.ARG_ARENA, Record.ARG_ATHLEIC)
            MATCH_TYPE_ENTERTAIN -> uri.appendQueryParameter(Record.ARG_ARENA, Record.ARG_ENTERTAIN)
            else -> {
                onMcMatchListener.onMcMatch(null, null, "未知匹配类型")
                return
            }
        }
        val oyHeader = OYHeader(
            OYHeader.HEADER_POSITION_AUTHORIZATION,
            "Basic " + OYUtil.message2Base64(mcUser.username + ":" + mcUser.external_id)
        )
        OkhttpUtil.post(uri.toString(), null, oyHeader, Record.ARG_ARENA, 30, object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e("MyCardUtil", e.message + "失败 " + e)
                try {
                    FileLogUtil.write("失败 $e")
                } catch (ioException: IOException) {
                    ioException.printStackTrace()
                }
                val message = e.message
                if (!TextUtils.isEmpty(message) && message == "Canceled") return
                if (!TextUtils.isEmpty(message) && message == "timeout") {
                    cancelMatch()
                    onMcMatchListener.onMcMatch(null, null, null)
                    return
                }
                onMcMatchListener.onMcMatch(null, null, e.toString())
            }

            @Throws(IOException::class)
            override fun onResponse(call: Call, response: Response) {
                val body = response.body()!!.string()
                Log.e("MyCardUtil", "匹配成功$body")
                if (TextUtils.isEmpty(body)) {
                    onMcMatchListener.onMcMatch(null, null, "匹配失败")
                    return
                }
                try {
                    val ygoServer = JsonUtil.getMatchYGOServer(body)
                    ygoServer!!.playerName = mcUser.username
                    onMcMatchListener.onMcMatch(ygoServer, ygoServer.password, null)
                } catch (e: JSONException) {
                    onMcMatchListener.onMcMatch(null, null, "" + e)
                }
                Log.e("MyCardUtil", "内容 $body")
                FileLogUtil.write("内容 $body")
            }
        })
    }
}