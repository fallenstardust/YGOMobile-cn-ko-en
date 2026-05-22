package cn.garymb.ygomobile.utils;

import android.text.TextUtils;

import com.google.gson.Gson;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.ui.mycard.bean.McNews;
import cn.garymb.ygomobile.ui.mycard.MyCard;
import cn.garymb.ygomobile.ui.mycard.bean.DuelRoom;
import cn.garymb.ygomobile.ui.mycard.bean.McDuelInfo;
import cn.garymb.ygomobile.ui.mycard.bean.YGOServer;

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

    public static McDuelInfo getUserDuelInfo(String json) {
        return new Gson().fromJson(json, McDuelInfo.class);
    }


    public static String getDuelRoomEvent(String json) throws JSONException {
        return new JSONObject(json).getString(MyCard.ARG_EVENT);
    }

    public static List<DuelRoom> getDuelRoomList(String json) throws JSONException {
        return getDuelRoomList(json, null);
    }

    public static List<DuelRoom> getDuelRoomList(String json, YGOServer server) throws JSONException {
        JSONObject jsonObject = new JSONObject(json);
        List<DuelRoom> duelRoomList = new ArrayList<>();

        switch (getDuelRoomEvent(json)) {
            case DuelRoom.EVENT_INIT:
            case DuelRoom.EVENT_CREATE:
            case DuelRoom.EVENT_UPDATE:
                Object data = jsonObject.get(MyCard.ARG_DATA);
                if (data instanceof JSONArray) {
                    // 处理数组情况
                    JSONArray jsonArray = (JSONArray) data;
                    for (int i = 0; i < jsonArray.length(); i++) {
                        duelRoomList.add(parseDuelRoom(jsonArray.getJSONObject(i), server));
                    }
                } else if (data instanceof JSONObject) {
                    // 处理单个对象情况
                    duelRoomList.add(parseDuelRoom((JSONObject) data, server));
                }
                break;
            case DuelRoom.EVENT_DELETE:
                DuelRoom duelRoom = new DuelRoom();
                duelRoom.setId(jsonObject.getString(MyCard.ARG_DATA));
                duelRoom.setTitle(jsonObject.getString(MyCard.ARG_DATA));
                duelRoom.setServer(server);
                duelRoomList.add(duelRoom);
                break;
        }

        return duelRoomList;
    }

    private static DuelRoom parseDuelRoom(JSONObject roomJson, YGOServer server) {
        DuelRoom duelRoom = new Gson().fromJson(roomJson.toString(), DuelRoom.class);
        duelRoom.setServer(server);
        duelRoom.setArena(duelRoom.getArena());
        duelRoom.setArenaType(duelRoom.getArena(), duelRoom.getId(), duelRoom.getOptions());
        return duelRoom;
    }

    public static List<YGOServer> getYGOServerList(String json) throws JSONException {
        JSONArray apps = new JSONArray(json);
        JSONObject ygoApp = null;
        for (int i = 0; i < apps.length(); i++) {
            JSONObject app = apps.getJSONObject(i);
            if (MyCard.ARG_YGOPRO.equals(app.optString(MyCard.ARG_ID))) {
                ygoApp = app;
                break;
            }
        }

        List<YGOServer> serverList = new ArrayList<>();
        if (ygoApp == null) {
            return serverList;
        }

        JSONObject data = ygoApp.optJSONObject(MyCard.ARG_DATA);
        if (data == null) {
            return serverList;
        }

        JSONArray servers = data.optJSONArray("servers");
        if (servers == null) {
            return serverList;
        }

        Gson gson = new Gson();
        for (int i = 0; i < servers.length(); i++) {
            JSONObject serverJson = servers.getJSONObject(i);
            YGOServer server = gson.fromJson(serverJson.toString(), YGOServer.class);
            server.setId(serverJson.optString(MyCard.ARG_ID, null));
            server.setName(serverJson.optString("name", null));
            server.setServerAddr(serverJson.optString(MyCard.ARG_ADDRESS, null));
            server.setPort(serverJson.optInt(MyCard.ARG_PORT));
            server.setSocketUrl(serverJson.optString(MyCard.ARG_URL, null));
            server.setMatch(optBooleanObject(serverJson, "match"));
            server.setHidden(optBooleanObject(serverJson, "hidden"));
            server.setCustom(optBooleanObject(serverJson, "custom"));
            server.setReplay(optBooleanObject(serverJson, "replay"));
            serverList.add(server);
        }
        return serverList;
    }

    private static Boolean optBooleanObject(JSONObject jsonObject, String key) throws JSONException {
        if (!jsonObject.has(key) || jsonObject.isNull(key)) {
            return null;
        }
        return jsonObject.getBoolean(key);
    }

    public static YGOServer getMatchYGOServer(String body) throws JSONException {
        JSONObject jsonObject = new JSONObject(body);
        YGOServer ygoServer = new YGOServer();
        ygoServer.setServerAddr(jsonObject.getString(MyCard.ARG_ADDRESS));
        ygoServer.setPort(jsonObject.getInt(MyCard.ARG_PORT));
        ygoServer.setPassword(jsonObject.getString(MyCard.ARG_MC_PASSWORD));
        return ygoServer;
    }
}
