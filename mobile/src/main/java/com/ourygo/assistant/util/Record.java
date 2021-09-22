package com.ourygo.assistant.util;

import cn.garymb.ygomobile.Constants;

public class Record {

    //卡查关键字
    public static final String[] CARD_SEARCH_KEY = new String[]{"?", "？"};
    //加房关键字
    public static final String[] PASSWORD_PREFIX = {
            "M,", "m,",
            "T,",
            "PR,", "pr,",
            "AI,", "ai,",
            "LF2,", "lf2,",
            "M#", "m#",
            "T#", "t#",
            "PR#", "pr#",
            "NS#", "ns#",
            "S#", "s#",
            "AI#", "ai#",
            "LF2#", "lf2#",
            "R#", "r#"
    };

    public static final String ROOM_PREFIX="room:{";

    public static final String ROOM_END="}";

    //卡组复制
    public static final String[] DeckTextKey = new String[]{"#main"};

    //卡组url前缀
    public static final String DECK_URL_PREFIX = "ygo://deck";
    public static final String ARG_PORT = "port";
    public static final String ARG_HOST = "host";
    public static final String ARG_PASSWORD = "password";
}
