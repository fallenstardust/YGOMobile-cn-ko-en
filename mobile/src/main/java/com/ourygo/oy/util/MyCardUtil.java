package com.ourygo.oy.util;


import com.ourygo.oy.base.listener.OnMyCardNewsQueryListener;

import org.json.JSONException;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class MyCardUtil {

    //获取mc新闻列表
    public static void findMyCardNews(OnMyCardNewsQueryListener onMyCardNewsQueryListener) {
        OkhttpUtil.get(Record.MYCARD_NEWS_URL, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                onMyCardNewsQueryListener.onMyCardNewsQuery(null, e.toString());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String json = response.body().string();
                try {
                    onMyCardNewsQueryListener.onMyCardNewsQuery(JsonUtil.getMyCardNewsList(json),null);
                } catch (JSONException e) {
                    onMyCardNewsQueryListener.onMyCardNewsQuery(null,e.toString());
                }
            }
        });
    }

    public static String getMyCardNewsData(String time){
        return time.substring(time.indexOf("-")+1,time.indexOf("T"));
    }


}
