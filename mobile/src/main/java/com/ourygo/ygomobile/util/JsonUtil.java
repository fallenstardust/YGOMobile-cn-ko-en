package com.ourygo.ygomobile.util;

import com.ourygo.ygomobile.bean.MyCardNews;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class JsonUtil {


    //解析mc新闻列表
    public static List<MyCardNews> getMyCardNewsList(String json) throws JSONException {
        JSONObject jsonObject = new JSONObject(json).getJSONObject(Record.ARG_TOPIC_LIST);
        List<MyCardNews> myCardNewsList = new ArrayList<>();
        JSONArray jsonArray = jsonObject.getJSONArray(Record.ARG_TOPICS);
        for (int i = 0; i < jsonArray.length(); i++) {
            JSONObject jsonObject1 = jsonArray.getJSONObject(i);
            MyCardNews myCardNews = new MyCardNews();
            myCardNews.setId(jsonObject1.getString(Record.ARG_ID));
            myCardNews.setImage_url(jsonObject1.getString(Record.ARG_IMAGE_URL));
            myCardNews.setTitle(jsonObject1.getString(Record.ARG_TITLE));
            myCardNews.setCreate_time(jsonObject1.getString(Record.ARG_CREATE_TIME));
            myCardNewsList.add(myCardNews);
        }
        return myCardNewsList;
    }

}
