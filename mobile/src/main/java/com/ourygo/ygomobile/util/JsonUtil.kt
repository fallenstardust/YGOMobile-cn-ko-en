package com.ourygo.ygomobile.util

import android.text.TextUtils
import com.google.gson.Gson
import com.ourygo.ygomobile.bean.DuelRoom
import com.ourygo.ygomobile.bean.McDuelInfo
import com.ourygo.ygomobile.bean.McNews
import com.ourygo.ygomobile.bean.YGOServer
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

object JsonUtil {
    const val CODE_OK = 200

    //解析mc新闻列表
    @Throws(JSONException::class)
    fun getMyCardNewsList(json: String?): List<McNews> {
        val jsonArray = JSONArray(json)
        var newsJson: JSONObject? = null
        val mcNewsList: MutableList<McNews> = ArrayList()
        for (i in 0 until jsonArray.length()) {
            val id = jsonArray.getJSONObject(i).getString(Record.ARG_ID)
            if (!TextUtils.isEmpty(id) && id == Record.ARG_YGOPRO) {
                newsJson = jsonArray.getJSONObject(i)
            }
        }
        if (newsJson == null) return mcNewsList
        val newsArray = newsJson.getJSONObject(Record.ARG_NEWS).getJSONArray(Record.ARG_ZH_CN)
        for (i in 0 until newsArray.length()) {
            val jsonObject1 = newsArray.getJSONObject(i)
            val mcNews = McNews()
            mcNews.news_url = jsonObject1.getString(Record.ARG_URL)
            mcNews.image_url = jsonObject1.getString(Record.ARG_IMAGE)
            mcNews.title = jsonObject1.getString(Record.ARG_TITLE)
            mcNews.create_time = jsonObject1.getString(Record.ARG_UPDATE_AT)
            mcNewsList.add(mcNews)
        }
        return mcNewsList
    }

    fun getUserDuelInfo(json: String?): McDuelInfo {
        return Gson().fromJson(json, McDuelInfo::class.java)
    }

    @Throws(JSONException::class)
    fun getDuelRoomEvent(json: String?): String {
        return JSONObject(json).getString(Record.ARG_EVENT)
    }

    @Throws(JSONException::class)
    fun getDuelRoomList(json: String?): List<DuelRoom> {
        val jsonObject = JSONObject(json)
        val duelRoomList: MutableList<DuelRoom> = ArrayList()
        when (getDuelRoomEvent(json)) {
            DuelRoom.EVENT_INIT, DuelRoom.EVENT_CREATE -> {
                val jsonArray = jsonObject.getJSONArray(Record.ARG_DATA)
                var i = 0
                while (i < jsonArray.length()) {
                    val duelRoom =
                        Gson().fromJson(jsonArray.getJSONObject(i).toString(), DuelRoom::class.java)
                    duelRoom.arena = duelRoom.arena
                    duelRoom.setArenaType(duelRoom.arena, duelRoom.id, duelRoom.options)
                    duelRoomList.add(duelRoom)
                    i++
                }
            }

            DuelRoom.EVENT_DELETE -> {
                val duelRoom = DuelRoom()
                duelRoom.id = jsonObject.getString(Record.ARG_DATA)
                duelRoom.title = jsonObject.getString(Record.ARG_DATA)
                duelRoomList.add(duelRoom)
            }
        }
        return duelRoomList
    }

    @Throws(JSONException::class)
    fun getMatchYGOServer(body: String?): YGOServer {
        val jsonObject = JSONObject(body)
        val ygoServer = YGOServer()
        ygoServer.serverAddr = jsonObject.getString(Record.ARG_ADDRESS)
        ygoServer.port = jsonObject.getInt(Record.ARG_PORT)
        ygoServer.password = jsonObject.getString(Record.ARG_MC_PASSWORD)
        return ygoServer
    }
}