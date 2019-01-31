package com.ourygo.oy.util;

public class Record {

    public static final String MYCARD_NEWS_URL="https://ygobbs.com/top/quarterly.json";
    public static final String MYCARD_POST_URL="https://ygobbs.com/t/";

    public static final String ARG_TOPIC_LIST="topic_list";
    public static final String ARG_TOPICS="topics";
    public static final String ARG_ID="id";
    public static final String ARG_TITLE="title";
    public static final String ARG_IMAGE_URL="image_url";
    public static final String ARG_CREATE_TIME="created_at";




    public static String getMycardPostUrl(String id){
        return MYCARD_POST_URL+id;
    }

}
