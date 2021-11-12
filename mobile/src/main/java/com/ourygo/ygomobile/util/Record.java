package com.ourygo.ygomobile.util;

import android.content.Context;
import android.os.Environment;

import java.io.File;

public class Record {
    public static final String DOWNLOAD_URL_EZ="http://t.cn/EchWyLi";

    public static final String MYCARD_NEWS_URL="https://api.mycard.moe/apps.json";
    public static final String MYCARD_POST_URL="https://ygobbs.com/t/";
    public static final String YGO_LFLIST_URL="https://raw.githubusercontent.com/moecube/ygopro/server/lflist.conf";

    public static final String ARG_TOPIC_LIST="topic_list";
    public static final String ARG_TOPICS="topics";
    public static final String ARG_ID="id";
    public static final String ARG_TITLE="title";
    public static final String ARG_IMAGE_URL="image_url";
    public static final String ARG_CREATE_TIME="created_at";
    public static final String ARG_OTHER="other";


    public static final String ARG_MC_NAME="name";
    public static final String ARG_MC_PASSWORD="password";
    public static final String ARG_YGOPRO = "ygopro";
    public static final String ARG_ZH_CN = "zh-CN";
    public static final String ARG_IMAGE = "image";
    public static final String ARG_UPDATE_AT = "updated_at";
    public static final String ARG_URL = "url";
    public static final String ARG_NEWS = "news";
    public static final String ARG_USERNAME = "username";
    public static final String MYCARD_USER_DUEL_URL = "https://sapi.moecube.com:444/ygopro/arena/user";

    public static final String ACTION_OPEN_MYCARD = "ygomobile.intent.action.MYCARD";
    public static final String ARG_QQ_GROUP_KEY = "zv5xSt-Zu739mNbsBfZ9Qn_-esYqHaT9";
    public static final String URL_MC_LOGIN = "https://accounts.moecube.com/";
    public static final String ARG_SSO = "sso";
    public static final String URL_MC_WATCH_DUEL_FUN = "wss://tiramisu.mycard.moe:7923/?filter=started";
    public static final String URL_MC_WATCH_DUEL_MATCH = "wss://tiramisu.mycard.moe:8923/?filter=started";
    public static final String URL_MC_MATCH = "https://api.mycard.moe/ygopro/match";
    public static final String ARG_EVENT = "event";
    public static final String ARG_DATA = "data";
    public static final String HOST_MC_MATCH = "tiramisu.mycard.moe";
    public static final String HOST_MC_OTHER = "tiramisu.mycard.moe";
    public static final int PORT_MC_MATCH = 8911;
    public static final int PORT_MC_OTHER = 7911;
    public static final String ARG_LOCALE = "locale";
    public static final String ARG_ARENA = "arena";
    public static final String ARG_ATHLEIC = "athletic";
    public static final String ARG_ENTERTAIN = "entertain";
    public static final String ARG_ADDRESS = "address";
    public static final String ARG_PORT = "port";
    public static final String PACKAGE_NAME_EZ = "com.ourygo.ez";

    public static String getMycardPostUrl(String id){
        return MYCARD_POST_URL+id;
    }

    public static String getImagePath(Context context) {
//        return context.getExternalFilesDir("image").getAbsolutePath();
        return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getAbsolutePath(),"YGOMobile OY").getAbsolutePath();
    }

}
