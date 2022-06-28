package cn.garymb.ygomobile.utils;

import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.ui.mycard.McNews;
import cn.garymb.ygomobile.ui.mycard.MyCard;

public class JsonUtil {


    //解析mc新闻列表
    public static List<McNews> getMyCardNewsList(String json) throws JSONException {
        JSONArray jsonArray = new JSONArray(json);
        JSONObject newsJson = null;
        List<McNews> mcNewsList = new ArrayList<>();
        for (int i = 0; i < jsonArray.length(); i++) {
            String id = jsonArray.getJSONObject(i).getString(MyCard.ARG_ID);
            if (!TextUtils.isEmpty(id) && id.equals(MyCard.ARG_YGOPRO)) {
                newsJson = jsonArray.getJSONObject(i);
            }
        }
        if (newsJson == null)
            return mcNewsList;
        JSONArray newsArray = newsJson.getJSONObject(MyCard.ARG_NEWS).getJSONArray(MyCard.ARG_ZH_CN);
        for (int i = 0; i < newsArray.length(); i++) {
            JSONObject jsonObject1 = newsArray.getJSONObject(i);
            McNews mcNews = new McNews();
            mcNews.setNews_url(jsonObject1.getString(MyCard.ARG_URL));
            mcNews.setImage_url(jsonObject1.getString(MyCard.ARG_IMAGE));
            mcNews.setTitle(jsonObject1.getString(MyCard.ARG_TITLE));
            mcNews.setCreate_time(jsonObject1.getString(MyCard.ARG_UPDATE_AT));
            mcNewsList.add(mcNews);
        }
        return mcNewsList;
    }
/*
    public static McDuelInfo getUserDuelInfo(String json) {
        return new Gson().fromJson(json, McDuelInfo.class);
    }


    public static String getDuelRoomEvent(String json) throws JSONException {
        return new JSONObject(json).getString(MyCard.ARG_EVENT);
    }

    public static List<DuelRoom> getDuelRoomList(String json) throws JSONException {
        JSONObject jsonObject = new JSONObject(json);
        List<DuelRoom> duelRoomList = new ArrayList<>();

        switch (getDuelRoomEvent(json)){
            case DuelRoom.EVENT_INIT:
            case DuelRoom.EVENT_CREATE:
                JSONArray jsonArray=jsonObject.getJSONArray(MyCard.ARG_DATA);
                for (int i = 0; i < jsonArray.length(); i++) {
                    DuelRoom duelRoom=new Gson().fromJson(jsonArray.getJSONObject(i).toString(), DuelRoom.class);
                    duelRoom.setArena(duelRoom.getArena());
                    duelRoom.setArenaType(duelRoom.getArena(),duelRoom.getId(),duelRoom.getOptions());
                    duelRoomList.add(duelRoom);
                }
                break;
            case DuelRoom.EVENT_DELETE:
                DuelRoom duelRoom=new DuelRoom();
                duelRoom.setId(jsonObject.getString(MyCard.ARG_DATA));
                duelRoom.setTitle(jsonObject.getString(MyCard.ARG_DATA));
                duelRoomList.add(duelRoom);
                break;
        }

        return duelRoomList;
    }

    public static YGOServer getMatchYGOServer(String body) throws JSONException {
        JSONObject jsonObject=new JSONObject(body);
        YGOServer ygoServer=new YGOServer();
        ygoServer.setServerAddr(jsonObject.getString(MyCard.ARG_ADDRESS));
        ygoServer.setPort(jsonObject.getInt(MyCard.ARG_PORT));
        ygoServer.setPassword(jsonObject.getString(MyCard.ARG_MC_PASSWORD));
        return ygoServer;
    }*/
}
