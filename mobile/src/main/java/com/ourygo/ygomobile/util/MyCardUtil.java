package com.ourygo.ygomobile.util;


import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import com.ourygo.ygomobile.base.listener.OnMcMatchListener;
import com.ourygo.ygomobile.base.listener.OnMyCardNewsQueryListener;
import com.ourygo.ygomobile.base.listener.OnUserDuelInfoQueryListener;
import com.ourygo.ygomobile.bean.OYHeader;
import com.ourygo.ygomobile.bean.YGOServer;

import org.json.JSONException;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import cn.garymb.ygomobile.ui.mycard.bean.McUser;
import cn.garymb.ygomobile.utils.FileLogUtil;
import mono.embeddinator.Obj;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class MyCardUtil {

    public static final int MATCH_TYPE_ATHLETIC = 0;
    public static final int MATCH_TYPE_ENTERTAIN = 1;

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
                    onMyCardNewsQueryListener.onMyCardNewsQuery(JsonUtil.getMyCardNewsList(json), null);
                } catch (JSONException e) {
                    onMyCardNewsQueryListener.onMyCardNewsQuery(null, e.toString());
                }
            }
        });
    }

    public static void findUserDuelInfo(String userName, OnUserDuelInfoQueryListener onUserDuelInfoQueryListener) {
        Map<String, Object> map = new HashMap<>();
        map.put(Record.ARG_USERNAME, userName);

        OkhttpUtil.get(Record.MYCARD_USER_DUEL_URL, map, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                onUserDuelInfoQueryListener.onUserDuelInfoQuery(null, e.toString());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String json = response.body().string();
                onUserDuelInfoQueryListener.onUserDuelInfoQuery(JsonUtil.getUserDuelInfo(json), null);
            }
        });
    }

    public static String getMyCardNewsData(String time) {
        return time.substring(time.indexOf("-") + 1, time.indexOf("T"));
    }

    public static String getAvatarUrl(String userName) {
        return "https://api.moecube.com/accounts/users/" + userName + ".png";
    }

    public static void cancelMatch() {
        OkhttpUtil.cancelTag(Record.ARG_ARENA);
    }

    public static void startMatch(McUser mcUser, int matchType, OnMcMatchListener onMcMatchListener) {
        if (TextUtils.isEmpty(mcUser.getUsername()) || mcUser.getExternal_id() == 0) {
            onMcMatchListener.onMcMatch(null, null, "用户信息为空，请退出重新登录");
            return;
        }

        Uri.Builder uri=Uri.parse(Record.URL_MC_MATCH).buildUpon();
        uri.appendQueryParameter(Record.ARG_LOCALE, Record.ARG_ZH_CN);
        switch (matchType) {
            case MATCH_TYPE_ATHLETIC:
                uri.appendQueryParameter(Record.ARG_ARENA, Record.ARG_ATHLEIC);
                break;
            case MATCH_TYPE_ENTERTAIN:
                uri.appendQueryParameter(Record.ARG_ARENA, Record.ARG_ENTERTAIN);
                break;
            default:
                onMcMatchListener.onMcMatch(null, null, "未知匹配类型");
                return;
        }
        OYHeader oyHeader = new OYHeader(OYHeader.HEADER_POSITION_AUTHORIZATION, "Basic " + OYUtil.message2Base64(mcUser.getUsername() + ":" + mcUser.getExternal_id()));


        OkhttpUtil.post(uri.toString(), null, oyHeader, Record.ARG_ARENA,30, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.e("MyCardUtil", e.getMessage() + "失败 " + e);
                try {
                    FileLogUtil.write("失败 " + e);
                } catch (IOException ioException) {
                    ioException.printStackTrace();
                }
                String message = e.getMessage();
                if (!TextUtils.isEmpty(message) && message.equals("Canceled"))
                    return;
                if (!TextUtils.isEmpty(message) && message.equals("timeout")) {
                    cancelMatch();
                    onMcMatchListener.onMcMatch(null,null,null);
                    return;
                }
                onMcMatchListener.onMcMatch(null, null, e.toString());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String body = response.body().string();
                Log.e("MyCardUtil","匹配成功"+body);
                if (TextUtils.isEmpty(body)) {
                    onMcMatchListener.onMcMatch(null, null, "匹配失败");
                    return;
                }
                try {
                    YGOServer ygoServer = JsonUtil.getMatchYGOServer(body);
                    ygoServer.setPlayerName(mcUser.getUsername());
                    onMcMatchListener.onMcMatch(ygoServer, ygoServer.getPassword(), null);
                } catch (JSONException e) {
                    onMcMatchListener.onMcMatch(null, null, "" + e);
                }


                Log.e("MyCardUtil", "内容 " + body);
                FileLogUtil.write("内容 " + body);
            }
        });
    }


}
