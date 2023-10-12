package com.ourygo.ygomobile.util

import android.content.Context
import android.os.Environment
import com.ourygo.ygomobile.OYApplication
import java.io.File

object Record {
    const val DOWNLOAD_URL_EZ = "http://t.cn/EchWyLi"
    const val MYCARD_NEWS_URL = "https://api.mycard.moe/apps.json"
    const val MYCARD_POST_URL = "https://ygobbs.com/t/"
    const val mycardInactiveEmailUrl = "https://accounts.moecube.com/signin"
    const val YGO_LFLIST_URL = "https://raw.githubusercontent.com/moecube/ygopro/server/lflist.conf"
    const val YGO_CARD_QUERY_URL = "https://ygocdb.com/"
    const val URL_OURYGO_API_HOME = "http://api.ourygo.top/"
    const val URL_UPDATE_APP = URL_OURYGO_API_HOME + "/index.php/home/Update/checkUpdate"
    const val YGO_ARG_DECK_CATEGORY = "--deck-category"
    const val ARG_TOPIC_LIST = "topic_list"
    const val ARG_TOPICS = "topics"
    const val ARG_ID = "id"
    const val ARG_TITLE = "title"
    const val ARG_IMAGE_URL = "image_url"
    const val ARG_CREATE_TIME = "created_at"
    const val ARG_OTHER = "other"
    const val ARG_MC_NAME = "name"
    const val ARG_MC_PASSWORD = "password"
    const val ARG_YGOPRO = "ygopro"
    const val ARG_ZH_CN = "zh-CN"
    const val ARG_IMAGE = "image"
    const val ARG_UPDATE_AT = "updated_at"
    const val ARG_URL = "url"
    const val ARG_NEWS = "news"
    const val ARG_USERNAME = "username"
    const val MYCARD_USER_DUEL_URL = "https://sapi.moecube.com:444/ygopro/arena/user"
    const val ACTION_OPEN_MYCARD = "ygomobile.intent.action.MYCARD"
    const val ARG_QQ_GROUP_KEY = "zv5xSt-Zu739mNbsBfZ9Qn_-esYqHaT9"
    const val URL_MC_LOGIN = "https://accounts.moecube.com/"
    const val ARG_SSO = "sso"
    const val URL_MC_WATCH_DUEL_FUN = "wss://tiramisu.mycard.moe:7923/?filter=started"
    const val URL_MC_WATCH_DUEL_MATCH = "wss://tiramisu.mycard.moe:8923/?filter=started"
    const val URL_MC_MATCH = "https://api.mycard.moe/ygopro/match"
    const val ARG_EVENT = "event"
    const val ARG_DATA = "data"
    const val HOST_MC_MATCH = "tiramisu.mycard.moe"
    const val HOST_MC_OTHER = "tiramisu.mycard.moe"
    const val PORT_MC_MATCH = 8911
    const val PORT_MC_OTHER = 7911
    const val ARG_LOCALE = "locale"
    const val ARG_ARENA = "arena"
    const val ARG_ATHLEIC = "athletic"
    const val ARG_ENTERTAIN = "entertain"
    const val ARG_ADDRESS = "address"
    const val ARG_PORT = "port"
    const val PACKAGE_NAME_EZ = "com.ourygo.ez"
    const val ARG_UPDATE = "update"
    const val ARG_CODE = "code"
    const val ARG_MESSAGE = "message"
    const val ARG_NAME = "name"
    const val ARG_VERSION = "version"
    const val URI_ROOM_HOST = "room.ourygo.top"
    @JvmStatic
    fun getMycardPostUrl(id: String): String {
        return MYCARD_POST_URL + id
    }

    @JvmStatic
    fun getImagePath(context: Context?): String {
//        return context.getExternalFilesDir("image").getAbsolutePath();
        return File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).absolutePath,
            "YGO-OY"
        ).absolutePath
    }

//    @JvmStatic
    val imageCachePath: String
        get() = OYApplication.get().getExternalFilesDir("cache/image")!!.absolutePath
    val cachePath: String
        get() = OYApplication.get().getExternalFilesDir("cache")!!.absolutePath
}