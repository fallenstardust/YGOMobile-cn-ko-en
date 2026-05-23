package cn.garymb.ygomobile.utils;

import android.text.TextUtils;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.ui.mycard.bean.McDuelResult;
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
        JSONObject jsonObject = new JSONObject(json);
        List<DuelRoom> duelRoomList = new ArrayList<>();

        switch (getDuelRoomEvent(json)) {
            case DuelRoom.EVENT_INIT:
            case DuelRoom.EVENT_CREATE:
                Object data = jsonObject.get(MyCard.ARG_DATA);
                if (data instanceof JSONArray) {
                    // 处理数组情况
                    JSONArray jsonArray = (JSONArray) data;
                    for (int i = 0; i < jsonArray.length(); i++) {
                        DuelRoom duelRoom = new Gson().fromJson(jsonArray.getJSONObject(i).toString(), DuelRoom.class);
                        duelRoom.setArena(duelRoom.getArena());
                        duelRoom.setArenaType(duelRoom.getArena(), duelRoom.getId(), duelRoom.getOptions());
                        duelRoomList.add(duelRoom);
                    }
                } else if (data instanceof JSONObject) {
                    // 处理单个对象情况
                    JSONObject roomJson = (JSONObject) data;
                    DuelRoom duelRoom = new Gson().fromJson(roomJson.toString(), DuelRoom.class);
                    duelRoom.setArena(duelRoom.getArena());
                    duelRoom.setArenaType(duelRoom.getArena(), duelRoom.getId(), duelRoom.getOptions());
                    duelRoomList.add(duelRoom);
                }
                break;
            case DuelRoom.EVENT_DELETE:
                DuelRoom duelRoom = new DuelRoom();
                duelRoom.setId(jsonObject.getString(MyCard.ARG_DATA));
                duelRoom.setTitle(jsonObject.getString(MyCard.ARG_DATA));
                duelRoomList.add(duelRoom);
                break;
        }

        return duelRoomList;
    }

    public static YGOServer getMatchYGOServer(String body) throws JSONException {
        JSONObject jsonObject = new JSONObject(body);
        YGOServer ygoServer = new YGOServer();
        ygoServer.setServerAddr(jsonObject.getString(MyCard.ARG_ADDRESS));
        ygoServer.setPort(jsonObject.getInt(MyCard.ARG_PORT));
        ygoServer.setPassword(jsonObject.getString(MyCard.ARG_MC_PASSWORD));
        return ygoServer;
    }

    public static List<YGOServer> getMyCardServers(String json) {
        List<YGOServer> servers = new ArrayList<>();
        JsonArray apps = JsonParser.parseString(json).getAsJsonArray();
        for (JsonElement appElement : apps) {
            JsonObject app = appElement.getAsJsonObject();
            if (!MyCard.ARG_YGOPRO.equals(getString(app, MyCard.ARG_ID))) {
                continue;
            }
            JsonObject data = app.getAsJsonObject(MyCard.ARG_DATA);
            if (data == null || !data.has("servers")) {
                return servers;
            }
            JsonArray serverArray = data.getAsJsonArray("servers");
            for (JsonElement serverElement : serverArray) {
                JsonObject serverJson = serverElement.getAsJsonObject();
                YGOServer server = new YGOServer();
                server.setId(getString(serverJson, MyCard.ARG_ID));
                server.setName(getString(serverJson, MyCard.ARG_MC_NAME));
                server.setUrl(getString(serverJson, MyCard.ARG_URL));
                server.setServerAddr(getString(serverJson, MyCard.ARG_ADDRESS));
                server.setPort(getInt(serverJson, MyCard.ARG_PORT));
                server.setHidden(getBoolean(serverJson, "hidden"));
                server.setCustom(getBoolean(serverJson, "custom"));
                server.setReplay(getBoolean(serverJson, "replay"));
                if (serverJson.has("windbot") && serverJson.get("windbot").isJsonArray()) {
                    List<String> windbots = new ArrayList<>();
                    for (JsonElement windbot : serverJson.getAsJsonArray("windbot")) {
                        if (!windbot.isJsonNull()) {
                            windbots.add(windbot.getAsString());
                        }
                    }
                    server.setWindbot(windbots);
                }
                servers.add(server);
            }
            return servers;
        }
        return servers;
    }

    public static McDuelResult getLatestDuelResult(String json) {
        JsonObject response = JsonParser.parseString(json).getAsJsonObject();
        if (!response.has(MyCard.ARG_DATA) || !response.get(MyCard.ARG_DATA).isJsonArray()) {
            return null;
        }
        JsonArray data = response.getAsJsonArray(MyCard.ARG_DATA);
        if (data.size() == 0) {
            return null;
        }
        return new Gson().fromJson(data.get(0), McDuelResult.class);
    }

    private static String getString(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsString() : null;
    }

    private static int getInt(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsInt() : 0;
    }

    private static boolean getBoolean(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() && object.get(key).getAsBoolean();
    }
}
