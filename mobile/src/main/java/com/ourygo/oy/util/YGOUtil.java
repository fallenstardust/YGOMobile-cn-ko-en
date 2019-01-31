package com.ourygo.oy.util;

import android.app.Activity;
import android.content.Context;

import com.ourygo.oy.base.listener.OnYGOServerListQueryListener;
import com.ourygo.oy.bean.Replay;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import cn.garymb.ygodata.YGOGameOptions;
import cn.garymb.ygomobile.App;
import cn.garymb.ygomobile.AppsSettings;
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


    //加入游戏
    public static void joinGame(Activity activity, ServerInfo serverInfo, String password) {
        YGOGameOptions options = new YGOGameOptions();
        options.mServerAddr = serverInfo.getServerAddr();
        options.mUserName = serverInfo.getPlayerName();
        options.mPort = serverInfo.getPort();
        options.mRoomName = password;
        YGOStarter.startGame(activity, options);
    }

    //获取服务器列表
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


    public static List<Replay> getReplayList(){
        List<Replay> replayList=new ArrayList<>();
        File file=new File(AppsSettings.get().getReplayReplay());

        for (File file1:file.listFiles()){
            if (!file1.isDirectory()&&file1.getName().endsWith(".yrp")){
                Replay replay=new Replay();
                replay.setName(file1.getName().substring(0,file1.getName().indexOf(".yrp")));
                replay.setTime(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(file1.lastModified())));
                replayList.add(replay);
            }
        }

        return replayList;
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
