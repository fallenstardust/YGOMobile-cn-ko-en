package com.ourygo.ygomobile.util;

import android.app.Activity;
import android.util.Log;

import com.ourygo.ygomobile.base.listener.OnLfListQueryListener;
import com.ourygo.ygomobile.base.listener.OnYGOServerListQueryListener;
import com.ourygo.ygomobile.bean.Lflist;
import com.ourygo.ygomobile.bean.Replay;
import com.ourygo.ygomobile.bean.YGOServer;
import com.ourygo.ygomobile.bean.YGOServerList;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
import cn.garymb.ygomobile.ui.plus.VUiKit;
import cn.garymb.ygomobile.utils.IOUtils;
import cn.garymb.ygomobile.utils.SystemUtils;
import cn.garymb.ygomobile.utils.XmlUtils;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

import static cn.garymb.ygomobile.Constants.ASSET_SERVER_LIST;

public class YGOUtil {

    public static void joinGame(Activity activity){
        joinGame(activity,null,null);
    }

    //加入游戏
    public static void joinGame(Activity activity, ServerInfo serverInfo, String password) {
        YGOGameOptions options =null;
        if (serverInfo!=null){
            options=new YGOGameOptions();
            options.mServerAddr = serverInfo.getServerAddr();
            options.mUserName = serverInfo.getPlayerName();
            options.mPort = serverInfo.getPort();
            options.mRoomName = password;
        }
        YGOStarter.startGame(activity, options);
    }

    //获取服务器列表
    public static void getYGOServerList(OnYGOServerListQueryListener onYGOServerListQueryListener){
        File xmlFile = new File(App.get().getFilesDir(), Constants.SERVER_FILE);
        VUiKit.defer().when(() -> {
            YGOServerList assetList = readList(App.get().getAssets().open(ASSET_SERVER_LIST));
            YGOServerList fileList = xmlFile.exists() ? readList(new FileInputStream(xmlFile)) : null;
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

                onYGOServerListQueryListener.onYGOServerListQuery(list);

            }
        });
    }


    public static List<Replay> getReplayList(){
        List<Replay> replayList=new ArrayList<>();
        File file=new File(AppsSettings.get().getReplayDir());

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

    private static YGOServerList readList(InputStream in) {
        YGOServerList list = null;
        try {
            list = XmlUtils.get().getObject(YGOServerList.class, in);
        } catch (Exception e) {

        } finally {
            IOUtils.close(in);
        }
        return list;
    }

    
    public void addYGOServer(YGOServer ygoService){
        getYGOServerList(new OnYGOServerListQueryListener() {
            @Override
            public void onYGOServerListQuery(YGOServerList serverList) {
                List<YGOServer> ygoServers=serverList.getServerInfoList();
                ygoServers.add(ygoService);
                setYGOServer(ygoServers);
            }
        });
    }


    public void setYGOServer(List<YGOServer> ygoServers){
        File xmlFile = new File(App.get().getFilesDir(), Constants.SERVER_FILE);
        OutputStream outputStream = null;
        try {
            outputStream = new FileOutputStream(xmlFile);
            XmlUtils.get().saveXml(new YGOServerList(SystemUtils.getVersion(App.get()), ygoServers), outputStream);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            IOUtils.close(outputStream);
        }
    }


    public static void findLfListListener(OnLfListQueryListener onLfListQueryListener){
        OkhttpUtil.get(Record.YGO_LFLIST_URL, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
               onLfListQueryListener.onLflistQuery(null,e.toString());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
               String lflist=response.body().string();
               String lflistHeader=lflist.substring(1,lflist.indexOf("\n"));
               int index=0;
               List<Lflist> lflistNameList=new ArrayList<>();
               while (index<lflistHeader.length()){
                   int start,end;
                   start=lflistHeader.indexOf("[",index);
                   if (start!=-1){
                       end=lflistHeader.indexOf("]",start);
                       if (end!=-1){
                           String lflistName=lflistHeader.substring(start+1,end);
                           int type=Lflist.TYPE_OCG;
                           //获取禁卡表名和禁卡表类型的分割符位置
                           int typeStart=lflistName.indexOf(" ");
                           //如果存在并且不在最后一个即分割
                           if (typeStart!=-1&&typeStart!=lflistName.length()-1){
                               String typeName=lflistName.substring(typeStart+1);
                               lflistName=lflistName.substring(0,typeStart);
                               if (typeName.equals("TCG")){
                                   type=Lflist.TYPE_TCG;
                               }else {
                                   type=Lflist.TYPE_OCG;
                               }
                           }
                           lflistNameList.add(Lflist.toLflist(lflistName,"",type));
                           index=end;
                       }else {
                           break;
                       }
                   }else {
                       break;
                   }
               }
               onLfListQueryListener.onLflistQuery(lflistNameList,null);
            }
        });

    }

}
