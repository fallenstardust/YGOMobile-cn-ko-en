package com.ourygo.oy.util;

import android.app.Activity;
import android.content.Context;

import com.ourygo.oy.base.listener.OnYGOServerListQueryListener;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

import cn.garymb.ygodata.YGOGameOptions;
import cn.garymb.ygomobile.App;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.YGOStarter;
import cn.garymb.ygomobile.bean.ServerInfo;
import cn.garymb.ygomobile.bean.ServerList;
import cn.garymb.ygomobile.ui.plus.ServiceDuelAssistant;
import cn.garymb.ygomobile.ui.plus.VUiKit;
import cn.garymb.ygomobile.utils.IOUtils;
import cn.garymb.ygomobile.utils.XmlUtils;

import static cn.garymb.ygomobile.Constants.ASSET_SERVER_LIST;

public class YGOUtil {


    public static void joinGame(Activity activity, ServerInfo serverInfo, String password) {
        YGOGameOptions options = new YGOGameOptions();
        options.mServerAddr = serverInfo.getServerAddr();
        options.mUserName = serverInfo.getPlayerName();
        options.mPort = serverInfo.getPort();
        options.mRoomName = password;
        YGOStarter.startGame(activity, options);
    }

    public static void getYGOServerList(OnYGOServerListQueryListener onYGOServerListQueryListener){
        File xmlFile = new File(App.get().getFilesDir(), Constants.SERVER_FILE);
        VUiKit.defer().when(() -> {
            ServerList assetList = readList(App.get().getAssets().open(ASSET_SERVER_LIST));
            ServerList fileList = xmlFile.exists() ? readList(new FileInputStream(xmlFile)) : null;
            if (fileList == null) {
                return assetList;
            }
            if (fileList.getVercode() < assetList.getVercode()) {
                xmlFile.delete();
                return assetList;
            }
            return fileList;
        }).done((list) -> {
            if (list != null) {

                ServerInfo serverInfo = list.getServerInfoList().get(0);
                onYGOServerListQueryListener.onYGOServerListQuery(list);

            }
        });
    }

    private static ServerList readList(InputStream in) {
        ServerList list = null;
        try {
            list = XmlUtils.get().getObject(ServerList.class, in);
        } catch (Exception e) {

        } finally {
            IOUtils.close(in);
        }
        return list;
    }


}
