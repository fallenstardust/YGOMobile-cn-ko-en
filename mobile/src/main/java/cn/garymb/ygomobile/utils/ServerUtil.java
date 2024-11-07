package cn.garymb.ygomobile.utils;

import static cn.garymb.ygomobile.Constants.ASSET_SERVER_LIST;
import static cn.garymb.ygomobile.Constants.URL_CN_DATAVER;
import static cn.garymb.ygomobile.Constants.URL_PRE_CARD;
import static cn.garymb.ygomobile.Constants.URL_SUPERPRE_CN_FILE;
import static cn.garymb.ygomobile.utils.StringUtils.isHost;
import static cn.garymb.ygomobile.utils.StringUtils.isNumeric;
import static cn.garymb.ygomobile.utils.WebParseUtil.isValidIP;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.file.zip.ZipEntry;
import com.file.zip.ZipFile;

import org.greenrobot.eventbus.EventBus;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.bean.ServerInfo;
import cn.garymb.ygomobile.bean.ServerList;
import cn.garymb.ygomobile.bean.events.ExCardEvent;
import cn.garymb.ygomobile.lite.BuildConfig;
import cn.garymb.ygomobile.ui.home.ServerListManager;
import cn.garymb.ygomobile.ui.plus.VUiKit;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class ServerUtil {
    private static final String TAG = ServerUtil.class.getSimpleName();
    /* 存储了当前先行卡是否需要更新的状态，UI逻辑直接读取该变量就能获知是否已安装先行卡 */
    public volatile static ExCardState exCardState = ExCardState.UNCHECKED;//TODO 可能有并发问题
    public volatile static String serverExCardVersion = "";
    private volatile static int failCounter = 0;

    /**
     * 初始化本地先行卡版本的状态，
     * 比对服务器的先行卡版本号与本地先行卡版本号，
     * 更新全局变量exCardVersion（如删除先行卡、重新安装先行卡等）
     */
    public static void initExCardState() {
        String oldVer = SharedPreferenceUtil.getExpansionDataVer();
        LogUtil.i(TAG, "server util, old pre-card version:" + oldVer);
        String URL_DATAVER = URL_CN_DATAVER;
        URL_DATAVER = (AppsSettings.get().getDataLanguage() == AppsSettings.languageEnum.Chinese.code) ? URL_CN_DATAVER : "https://github.com/DaruKani/TransSuperpre/blob/main/" + getLanguageId() + "/version.txt";
        Log.w("seesee", URL_DATAVER);
        OkhttpUtil.get(URL_DATAVER, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                exCardState = ExCardState.ERROR;
                serverExCardVersion = "";
                LogUtil.e(TAG, BuildConfig.VERSION_NAME);
                LogUtil.i(TAG, "network failed, pre-card version:" + exCardState);
                if (failCounter < 10) {
                    LogUtil.i(TAG, "network failed, retry fetch pre-card version:");
                    failCounter++;
                    initExCardState();
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                failCounter = 0;//充值计数器
                String newVer = response.body().string();
                /* 服务器有点怪，返回的版本号带个\n，要去掉 */
                if (newVer.endsWith("\n")) {
                    newVer = newVer.substring(0, newVer.length() - 1);
                }
                serverExCardVersion = newVer;


                LogUtil.i(TAG, "ServerUtil fetch pre-card version:" + newVer);
                if (!TextUtils.isEmpty(newVer)) {

                    if (!newVer.equals(oldVer)) {//如果oldVer为null，也会触发
                        exCardState = ExCardState.NEED_UPDATE;
                    } else {
                        exCardState = ExCardState.UPDATED;
                    }
                } else {
                    exCardState = ExCardState.ERROR;
                }

                /* 通知homeFragment更新图标 */
                EventBus.getDefault().postSticky(new ExCardEvent(ExCardEvent.EventType.exCardPackageChange));
            }
        });
    }

    /**
     * 解析zip或者ypk的file下内置的txt文件里的服务器name、host、prot
     *
     * @param context
     * @param file
     */
    public static void loadServerInfoFromZipOrYpk(Context context, File file) {
        if (file.getName().endsWith(".zip") || file.getName().endsWith(".ypk")) {
            LogUtil.e("GameUriManager", "读取压缩包");
            try {
                String serverName = null, serverDesc = null, serverHost = null, serverPort = null;
                ZipFile zipFile = new ZipFile(file.getAbsoluteFile(), "GBK");
                Enumeration<ZipEntry> entris = zipFile.getEntries();
                ZipEntry entry;
                StringBuilder content = new StringBuilder();
                while (entris.hasMoreElements()) {
                    entry = entris.nextElement();
                    if (!entry.isDirectory()) {
                        if (entry.getName().endsWith(".ini")) {
                            InputStreamReader in = new InputStreamReader(zipFile.getInputStream(entry), StandardCharsets.UTF_8);
                            BufferedReader reader = new BufferedReader(in);
                            String line = null;
                            while ((line = reader.readLine()) != null) {
                                if (line.startsWith("[YGOProExpansionPack]") ||
                                        line.startsWith("FileName") ||
                                        line.startsWith("PackName") ||
                                        line.startsWith("PackAuthor") ||
                                        line.startsWith("PackHomePage") ||
                                        line.startsWith("[YGOMobileAddServer]")) {
                                    continue;
                                }
                                if (line.startsWith("ServerName")) {
                                    String[] words = line.trim().split("[\t| =]+");
                                    if (words.length >= 2) {
                                        serverName = words[1];
                                    }
                                }
                                if (line.startsWith("ServerDesc")) {
                                    String[] words = line.trim().split("[\t| =]+");
                                    if (words.length >= 2) {
                                        serverDesc = words[1];
                                    }
                                }
                                if (line.startsWith("ServerHost")) {
                                    String[] words = line.trim().split("[\t| =]+");
                                    if (words.length >= 2) {
                                        serverHost = words[1];
                                    }
                                }
                                if (line.startsWith("ServerPort")) {
                                    String[] words = line.trim().split("[\t| =]+");
                                    if (words.length >= 2) {
                                        serverPort = words[1];
                                    }
                                }
                            }
                            if (serverName != null && (isHost(serverHost) || isValidIP(serverHost)) && isNumeric(serverPort)) {
                                AddServer(context, serverName, serverDesc, serverHost, Integer.valueOf(serverPort), Constants.PlayerName);
                            } else {
                                Log.w(TAG, "can't parse ex-server properly");
                            }
                        }
                    }
                }
                zipFile.close();

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 读取xmlFile指定的本地文件server_list.xml和apk资源文件（assets）下的serverlist.xml，返回其中版本最新的
     *
     * @param context
     * @param xmlFile 指定的本地文件server_list.xml
     * @return
     * @throws IOException
     */
    private static ServerList mergeServerList(Context context, File xmlFile) throws IOException {
        /* 读取apk中assets文件夹下的serverlist.xml文件 */
        ServerList assetList = ServerListManager.readList(context.getAssets().open(ASSET_SERVER_LIST));


        ServerList fileList = xmlFile.exists() ? ServerListManager.readList(new FileInputStream(xmlFile)) : null;
        if (fileList == null) {
            return assetList;
        }
        /* 如果apk下assets中的版本号更大，则返回assets下的server列表 */
        if (fileList.getVercode() < assetList.getVercode()) {
            xmlFile.delete();
            return assetList;
        }
        return fileList;
    }

    public static void refreshServer(Context context) throws IOException {
        /* 读取apk中assets文件夹下的serverlist.xml文件 */
        ServerList assetList = ServerListManager.readList(context.getAssets().open(ASSET_SERVER_LIST));
        /* 读取本地文件server_list.xml */
        File xmlFile = new File(context.getFilesDir(), Constants.SERVER_FILE);//读取文件路径下的server_list.xml
        ServerList fileList = xmlFile.exists() ? ServerListManager.readList(new FileInputStream(xmlFile)) : null;

        if (fileList == null) {
            return;
        }
        for (int i = 0; i < assetList.getServerInfoList().size(); i++) {

            String assetName = assetList.getServerInfoList().get(i).getName();
            String assetDesc = assetList.getServerInfoList().get(i).getDesc();
            String assetAddr = assetList.getServerInfoList().get(i).getServerAddr();
            int assetPort = assetList.getServerInfoList().get(i).getPort();

            /*考虑到fileList的serverinfo其他信息被用户修改过，专门只比较域名地址和端口来视为相同的server来补充备注*/
            for (int j = 0; j < fileList.getServerInfoList().size(); j++) {
                String fileAddr = fileList.getServerInfoList().get(j).getServerAddr();
                int filePort = fileList.getServerInfoList().get(j).getPort();
                String fileDesc = fileList.getServerInfoList().get(j).getDesc();

                //IP相同port相同则为已存在相同serverInfo
                if (assetAddr.equals(fileAddr) && (assetPort == filePort)) {
                    if (!assetDesc.equals(fileDesc)) {
                        fileList.getServerInfoList().get(j).setDesc(assetDesc);
                    }
                }
            }

            if (!fileList.getServerInfoList().contains(assetList.getServerInfoList().get(i))) {
                AddServer(context, assetName, assetDesc, assetAddr, assetPort, Constants.PlayerName);
            }

        }
        saveItems(context, xmlFile, fileList.getServerInfoList());
    }

    /**
     * 从资源文件serverlist.xml（或本地文件server_list.xml)解析服务器列表，并将新添加的服务器信息（name，addr，port）合并到服务器列表中。
     *
     * @param name
     * @param Addr
     * @param port
     * @param playerName
     */
    public static void AddServer(Context context, String name, String desc, String Addr, int port, String playerName) {

        /* 读取本地文件server_list.xml */
        File xmlFile = new File(context.getFilesDir(), Constants.SERVER_FILE);//读取文件路径下的server_list.xml
        VUiKit.defer().when(() -> {
            return mergeServerList(context, xmlFile);
        }).done((list) -> {
            List<ServerInfo> serverInfos = new ArrayList<>();
            ServerInfo mServerInfo = new ServerInfo();
            mServerInfo.setName(name);
            mServerInfo.setDesc(desc);
            mServerInfo.setServerAddr(Addr);
            mServerInfo.setPort(port);
            mServerInfo.setPlayerName(playerName);


            boolean hasServer = false;
            if (list != null) {
                serverInfos.clear();
                serverInfos.addAll(list.getServerInfoList());
                for (int i = 0; i < serverInfos.size(); i++) {
                    if (mServerInfo.getServerAddr().equals(serverInfos.get(i).getServerAddr()) && mServerInfo.getPort() == serverInfos.get(i).getPort()) {//域名端口相同则视为已存在相同的服务器入口
                        hasServer = true;
                        break;
                    } else {
                        hasServer = false;
                    }
                }
                if (!hasServer && !serverInfos.contains(mServerInfo)) {//todo serverInfos.contains(mServerInfo)好像没必要
                    serverInfos.add(mServerInfo);
                }
            }
            saveItems(context, xmlFile, serverInfos);
        });
    }

    /**
     * 将最新的服务器列表存储到本地文件server_list.xml中
     */
    public static void saveItems(Context context, File xmlFile, List<ServerInfo> serverInfos) {
        OutputStream outputStream = null;
        try {
            outputStream = new FileOutputStream(xmlFile);
            XmlUtils.get().saveXml(new ServerList(SystemUtils.getVersion(context), serverInfos), outputStream);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            IOUtils.close(outputStream);
        }
    }

    public static boolean isPreServer(int port, String addr) {
        return (port == Constants.PORT_Mycard_Super_Pre_Server && (addr.equals(Constants.URL_Mycard_Super_Pre_Server) || addr.equals(Constants.URL_Mycard_Super_Pre_Server_2)));
    }

    private static String getLanguageId() {
        String id ="";
        if (AppsSettings.get().getDataLanguage() == AppsSettings.languageEnum.Korean.code)
            id = "KR";
        if (AppsSettings.get().getDataLanguage() == AppsSettings.languageEnum.English.code)
            id = "EN";
        if (AppsSettings.get().getDataLanguage() == AppsSettings.languageEnum.Spanish.code)
            id = "ES";
        if (AppsSettings.get().getDataLanguage() == AppsSettings.languageEnum.Japanese.code)
            id = "JP";
        return id;
    }

    public static String downloadUrl() {
        String url;
        url = (AppsSettings.get().getDataLanguage() == AppsSettings.languageEnum.Chinese.code)
                ? URL_SUPERPRE_CN_FILE : "https://raw.githubusercontent.com/DaruKani/TransSuperpre/refs/heads/main/" + getLanguageId() + "/ygopro-super-pre.ypk";
        Log.w("seesee",url);
        return url;
    }

    public static String preCardListJson() {
        String json;
        json = (AppsSettings.get().getDataLanguage() == AppsSettings.languageEnum.Chinese.code)
                ? URL_PRE_CARD : "https://raw.githubusercontent.com/DaruKani/TransSuperpre/refs/heads/main/" + getLanguageId() + "/test-release.json";
        Log.w("seesee",json);
        return json;
    }
    public enum ExCardState {
        /* 已安装最新版扩展卡，扩展卡不是最新版本，无法查询到服务器版本 */
        UNCHECKED, UPDATED, NEED_UPDATE, ERROR
    }

}
