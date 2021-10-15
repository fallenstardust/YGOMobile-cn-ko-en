package com.ourygo.assistant.util;

import static cn.garymb.ygomobile.Constants.QUERY_VERSION;
import static cn.garymb.ygomobile.Constants.QUERY_YGO_TYPE;

import android.net.Uri;

import com.ourygo.assistant.base.listener.OnDeRoomListener;

/**
 * Create By feihua  On 2021/9/29
 */
public class YGODAUtil {


    public static void deRoomListener(Uri uri, OnDeRoomListener onDeRoomListener){
        String host = "", password = "";
        int port = 0;

        int version = Record.YGO_ROOM_PROTOCOL_1;

        try {
            String ygoType = uri.getQueryParameter(QUERY_YGO_TYPE);
            if (ygoType.equals(Record.ARG_ROOM)) {
                version = Integer.parseInt(uri.getQueryParameter(QUERY_VERSION));
            }
        } catch (Exception exception) {
          onDeRoomListener.onDeRoom(null,-1,null,"非加房协议");
        }

        switch (version) {
            case Record.YGO_ROOM_PROTOCOL_1:
                try {
                    host = UrlUtil.deURL(uri.getQueryParameter(Record.ARG_HOST));
                } catch (Exception ignored) {
                }
                try {
                    port = Integer.parseInt(UrlUtil.deURL(uri.getQueryParameter(Record.ARG_PORT)));
                } catch (Exception ignored) {
                }
                try {
                    password = UrlUtil.deURL(uri.getQueryParameter(Record.ARG_PASSWORD));
                } catch (Exception ignored) {
                }
                break;
        }
        onDeRoomListener.onDeRoom(host,port,password,null);
    }

}
