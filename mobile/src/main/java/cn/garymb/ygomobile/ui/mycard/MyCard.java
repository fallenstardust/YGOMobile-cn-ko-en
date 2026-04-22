package cn.garymb.ygomobile.ui.mycard;

import static junit.framework.Assert.assertEquals;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.webkit.JavascriptInterface;

import com.google.gson.Gson;
import com.tencent.smtt.sdk.WebView;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.garymb.ygodata.YGOGameOptions;
import cn.garymb.ygomobile.App;
import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.YGOStarter;
import cn.garymb.ygomobile.bean.OYHeader;
import cn.garymb.ygomobile.bean.events.DeckFile;
import cn.garymb.ygomobile.ui.mycard.base.OnMcMatchListener;
import cn.garymb.ygomobile.ui.mycard.base.OnUserDuelInfoQueryListener;
import cn.garymb.ygomobile.ui.mycard.bean.McUser;
import cn.garymb.ygomobile.ui.mycard.bean.YGOServer;
import cn.garymb.ygomobile.ui.mycard.mcchat.management.UserManagement;
import cn.garymb.ygomobile.ui.plus.DefWebViewClient;
import cn.garymb.ygomobile.ui.plus.VUiKit;
import cn.garymb.ygomobile.utils.DeckUtil;
import cn.garymb.ygomobile.utils.FileLogUtil;
import cn.garymb.ygomobile.utils.JsonUtil;
import cn.garymb.ygomobile.utils.OkhttpUtil;
import cn.garymb.ygomobile.utils.SharedPreferenceUtil;
import cn.garymb.ygomobile.utils.YGOUtil;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class MyCard {

    public static final String mHomeUrl = "https://mycard.world/mobile/";
    private static final String mArenaUrl = "https://mycard.world/ygopro/arena/";
    public static final String mCommunityReportUrl = "https://ygobbs2.com/t/bug%E5%8F%8D%E9%A6%88/";
    private static final String mCommunityUrl = "https://ygobbs2.com/login";
    public static final String mCompetitionUrl = "https://event.ygobbs2.com/";
    private static final String HOST_MC = "mycard.world";
    public static final String MC_MAIN_URL = "https://mycard.world/mobile/ygopro/lobby";

    public static final String MYCARD_NEWS_URL = "https://sapi.moecube.com:444/apps.json";
    public static final String MYCARD_USER_DUEL_URL = "https://sapi.moecube.com:444/ygopro/arena/user";
    public static final String URL_MC_WATCH_DUEL_FUN = "wss://tiramisu.moecube.com:7923/?filter=started";
    public static final String URL_MC_WATCH_DUEL_MATCH = "wss://tiramisu.moecube.com:8923/?filter=started";
    public static final String URL_MC_MATCH = "https://api.moecube.com/ygopro/match";
    public static final String MYCARD_POST_URL = "https://ygobbs2.com/t/";
    public static final String ARG_ID = "id";
    public static final String ARG_TITLE = "title";
    public static final String HOST_MC_MATCH = "tiramisu.moecube.com";
    public static final String HOST_MC_OTHER = "tiramisu.moecube.com";
    public static final int PORT_MC_MATCH = 8911;
    public static final int PORT_MC_OTHER = 7911;
    public static final String URI_ROOM_HOST = "room.ourygo.top";
    public static final String ARG_MC_NAME = "name";
    public static final String ARG_USERNAME = "username";
    public static final String ARG_YGOPRO = "ygopro";
    public static final String ARG_ZH_CN = "zh-CN";
    public static final String ARG_IMAGE = "image";
    public static final String ARG_UPDATE_AT = "updated_at";
    public static final String ARG_URL = "url";
    public static final String ARG_NEWS = "news";
    public static final String ARG_EVENT = "event";
    public static final String ARG_DATA = "data";
    public static final String ARG_ADDRESS = "address";
    public static final String ARG_PORT = "port";
    public static final String ARG_MC_PASSWORD = "password";
    public static final String ARG_ARENA = "arena";
    public static final String ARG_LOCALE = "locale";
    public static final String ARG_ATHLETIC = "athletic";
    public static final String ARG_ENTERTAIN = "entertain";
    public static int U16_SECRET = 0;
    public static final String URL_MC_SIGN_UP = "https://accounts.moecube.com/signup";
    public static final String URL_MC_LOGOUT = "https://accounts.moecube.com/signin";
    public static final String URL_MC_AUTH_USER = "https://sapi.moecube.com:444/accounts/authUser";
    public static final String URL_MC_ATHLETIC_RATE = "https://sapi.moecube.com:444/ygopro/analytics/matchup/type?source=mycard-athletic";
    public static final String URL_DECK_TYPE_ANALYTICS = "https://sapi.moecube.com:444/ygopro/analytics/deck/type";
    public static final int MATCH_TYPE_ATHLETIC = 0;
    public static final int MATCH_TYPE_ENTERTAIN = 1;

    private final DefWebViewClient mDefWebViewClient = new DefWebViewClient() {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return super.shouldOverrideUrlLoading(view, url);
        }
    };
    private final SharedPreferences lastModified;
    private MyCardListener mMyCardListener;
    private final Activity mContext;

    public MyCard(Activity context) {
        mContext = context;
        lastModified = context.getSharedPreferences("lastModified", Context.MODE_PRIVATE);
    }

    public static void findUserDuelInfo(String userName, OnUserDuelInfoQueryListener onUserDuelInfoQueryListener) {
        Map<String, Object> map = new HashMap<>();
        map.put(ARG_USERNAME, userName);

        OkhttpUtil.get(MYCARD_USER_DUEL_URL, map, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                onUserDuelInfoQueryListener.onUserDuelInfoQuery(null, e.toString());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String json = response.body().string();
                onUserDuelInfoQueryListener.onUserDuelInfoQuery(
                        JsonUtil.getUserDuelInfo(json),
                        null
                );
            }
        });
    }

    public static void cancelMatch() {
        OkhttpUtil.cancelTag(ARG_ARENA);
    }

    public static void startMatch(McUser mcUser, int matchType, OnMcMatchListener onMcMatchListener) {
        if (TextUtils.isEmpty(mcUser.getUsername()) || mcUser.getExternal_id() == 0) {
            onMcMatchListener.onMcMatch(null, null, "用户信息为空，请退出重新登录");
            return;
        }

        Uri.Builder uriBuilder = Uri.parse(URL_MC_MATCH).buildUpon();
        uriBuilder.appendQueryParameter(ARG_LOCALE, ARG_ZH_CN);

        switch (matchType) {
            case MATCH_TYPE_ATHLETIC:
                uriBuilder.appendQueryParameter(ARG_ARENA, ARG_ATHLETIC);
                break;
            case MATCH_TYPE_ENTERTAIN:
                uriBuilder.appendQueryParameter(ARG_ARENA, ARG_ENTERTAIN);
                break;
            default:
                onMcMatchListener.onMcMatch(null, null, "未知匹配类型");
                return;
        }

        new Thread(() -> {
            try {
                String token = SharedPreferenceUtil.getServerToken();
                if (TextUtils.isEmpty(token)) {
                    throw new Exception("token not found");
                }

                int u16SecretStr = MyCard.getUserU16Secret(token);
                if (u16SecretStr == 0) {
                    throw new Exception("获取u16Secret失败");
                }

                U16_SECRET = u16SecretStr;
                String authHeader = "Basic " + YGOUtil.message2Base64(mcUser.getUsername() + ":" + U16_SECRET);
                Log.i("MyCard", "U16_SECRET: " + U16_SECRET);
                OYHeader oyHeader = new OYHeader(OYHeader.HEADER_POSITION_AUTHORIZATION, authHeader);

                OkhttpUtil.post(uriBuilder.toString(), null, oyHeader, ARG_ARENA, 30, new Callback() {
                    @Override
                    public void onFailure(Call call, IOException e) {
                        Log.e("MyCard", e.getMessage() + "失败 " + e);
                        try {
                            FileLogUtil.write("失败 " + e);
                        } catch (IOException ioException) {
                            ioException.printStackTrace();
                        }

                        String message = e.getMessage();
                        if (!TextUtils.isEmpty(message) && message.equals("Canceled")) {
                            return;
                        }
                        if (!TextUtils.isEmpty(message) && message.equals("timeout")) {
                            cancelMatch();
                            onMcMatchListener.onMcMatch(null, null, null);
                            return;
                        }
                        onMcMatchListener.onMcMatch(null, null, e.toString());
                    }

                    @Override
                    public void onResponse(Call call, Response response) throws IOException {
                        String body = response.body().string();
                        Log.e("MyCard", "匹配成功" + body);

                        if (TextUtils.isEmpty(body)) {
                            onMcMatchListener.onMcMatch(null, null, "匹配失败");
                            return;
                        }

                        try {
                            YGOServer ygoServer = JsonUtil.getMatchYGOServer(body);
                            if (ygoServer != null) {
                                ygoServer.setPlayerName(mcUser.getUsername());
                                onMcMatchListener.onMcMatch(ygoServer, ygoServer.getPassword(), null);
                            } else {
                                onMcMatchListener.onMcMatch(null, null, "匹配失败");
                            }
                        } catch (JSONException e) {
                            onMcMatchListener.onMcMatch(null, null, "" + e);
                        }

                        Log.e("MyCard", "内容 " + body);
                        FileLogUtil.write("内容 " + body);
                    }
                });
            } catch (Exception e) {
                Log.e("MyCard", "匹配失败: " + e);
            }
        }).start();
    }

    public static int getUserU16Secret(String token) throws IOException {
        if (token == null || token.isEmpty()) {
            throw new IOException("token not found");
        }

        String url = "https://sapi.moecube.com:444/accounts/authUser";

        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + token);

        Response response = OkhttpUtil.synchronousGet(url, null, headers);
        String responseBody = response.body().string();

        if (!response.isSuccessful()) {
            throw new IOException("获取用户密钥失败: " + responseBody);
        }

        Gson gson = new Gson();
        McUser.U16SecretResponse secretResponse = gson.fromJson(responseBody, McUser.U16SecretResponse.class);

        if (secretResponse == null || secretResponse.getU16Secret() == 0) {
            throw new IOException("获取用户密钥失败: 返回数据无效");
        }

        return secretResponse.getU16Secret();
    }

    public static String getMCLogoutUrl() {
        String home = "return_sso_url=" + Uri.encode(mHomeUrl);
        String base64 = Base64.encodeToString(home.getBytes(), Base64.NO_WRAP);
        Uri.Builder uri = Uri.parse(URL_MC_LOGOUT)
                .buildUpon();
        uri.appendQueryParameter("sso", base64);
        return uri.build().toString();
    }

    //获取mc新闻列表
    public static void findMyCardNews(OnMyCardNewsQueryListener onMyCardNewsQueryListener) {
        OkhttpUtil.get(MYCARD_NEWS_URL, new Callback() {
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

    public interface OnMyCardNewsQueryListener {
        void onMyCardNewsQuery(List<McNews> mcNewsList, String exception);
    }

    private static String byteArrayToHexString(byte[] array) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : array) {
            int intVal = b & 0xff;
            if (intVal < 0x10)
                hexString.append("0");
            hexString.append(Integer.toHexString(intVal));
        }
        return hexString.toString();
    }

    public String getArenaUrl() {
        return mArenaUrl;
    }

    public MyCardListener getMyCardListener() {
        return mMyCardListener;
    }

    public DefWebViewClient getWebViewClient() {
        return mDefWebViewClient;
    }

    public String getHomeUrl() {
        return mHomeUrl;
    }

    public String getBBSUrl() {
        return mCommunityUrl;
    }

    public String getMcHost() {
        return HOST_MC;
    }

    public String getMcMainUrl() {
        return MC_MAIN_URL;
    }

    @SuppressLint("AddJavascriptInterface")
    public void attachWeb(MyCardWebView webView, MyCardListener myCardListener) {
        mMyCardListener = myCardListener;
        webView.setWebViewClient(getWebViewClient());
        webView.addJavascriptInterface(new MyCard.Ygopro(mContext, myCardListener), "ygopro");
    }

    public interface MyCardListener {
        void onLogin(McUser mcUser, String exception);

        void onUpdate(String name, String icon, String statu);

        void backHome();

        void share(String text);

        void onHome();

        /**
         *
         * @param message 退出登录的提示，web端传
         */
        void onLogout(String message);

    }

    public class Ygopro {
        Activity activity;
        MyCardListener mListener;

        private final AppsSettings settings = AppsSettings.get();

        private Ygopro(Activity activity, MyCardListener listener) {
            this.activity = activity;
            mListener = listener;
        }

        @JavascriptInterface
        public void backHome() {
            if (mListener != null) {
                activity.runOnUiThread(mListener::backHome);
            }
        }

        @JavascriptInterface
        public void share(String text) {
            if (mListener != null) {
                activity.runOnUiThread(() -> {
                    mListener.share(text);
                });
            }
        }

        @JavascriptInterface
        public void join(String host, int port, String name, String room) {
            try {
                final YGOGameOptions options = new YGOGameOptions();
                options.mServerAddr = host;
                options.mUserName = name;
                options.mPort = port;
                options.mRoomName = room;
                Log.d("webview", "options=" + options);
                activity.runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        YGOStarter.startGame(activity, options);
                    }
                });
            } catch (Exception e) {
                Log.e("webview", "startGame", e);
            }
        }

        /*
         * 列目录
         * path: 文件夹路径
         * return: 文件名数组的 JSON 字符串
         * 失败抛异常或返回空数组
         */
        @JavascriptInterface
        public String readdir(String path) {
            File file = new File(settings.getResourcePath(), path);
            String[] result = file.list();
            List<DeckFile> deckFileList = new ArrayList<>();
            for (File file1 : file.listFiles()) {
                if (file1.isDirectory()) {
                    deckFileList.addAll(DeckUtil.getDeckList(file1.getAbsolutePath()));
                } else {
//                    if (file.getName().endsWith(".ydk"))
                    deckFileList.add(new DeckFile(file1.getAbsolutePath()));
                }
            }
            int deckPathLenght = file.getAbsolutePath().length();
            List<String> deckList = new ArrayList<>();
            for (DeckFile deckFile : deckFileList) {
                deckList.add(deckFile.getPath().substring(deckPathLenght + 1));
            }
            return new JSONArray(deckList).toString();
        }

        /*
         * 读取文件内容
         * path: 文件绝对路径
         * return: 文件内容的 base64
         * 读取失败抛异常
         */
        @JavascriptInterface
        public String readFile(String path) throws IOException {
            File file = new File(settings.getResourcePath(), path);
            byte[] result = new byte[(int) file.length()];
            BufferedInputStream stream = new BufferedInputStream(new FileInputStream(file));
            assertEquals(result.length, stream.read(result, 0, result.length));
            stream.close();
            return Base64.encodeToString(result, Base64.NO_WRAP);
        }

        /*
         * 写入内容到指定文件
         * path: 文件路径
         * data: 文件内容的 base64
         * 写入失败抛异常
         */
        @JavascriptInterface
        public void writeFile(String path, String data) throws IOException {
            File file = new File(settings.getResourcePath(), path);
            FileOutputStream stream = new FileOutputStream(file);
            stream.write(Base64.decode(data, Base64.NO_WRAP));
            stream.close();
        }

        /*
         * 删除文件
         * 删除失败返回 false
         */
        @JavascriptInterface
        public boolean unlink(String path) {
            File file = new File(settings.getResourcePath(), path);
            lastModified.edit().remove(path).apply();
            return file.delete();
        }

        /*
         * 获取文件修改时间
         * path: 文件绝对路径
         * return: 修改时间
         * 文件不存在返回 0
         */
        @JavascriptInterface
        public long getFileLastModified(String path) {
            File file = new File(settings.getResourcePath(), path);
            return getWrappedLastModified(path, file.lastModified());
        }

        @JavascriptInterface
        public void loginUser(String userInfo, String exception) {
            McUser mcUser = null;
            if (TextUtils.isEmpty(exception)) {
                mcUser = new Gson().fromJson(userInfo, McUser.class);
                UserManagement.getDx().setMcUser(mcUser);//登录后，mcUser信息存在此
                //另外保存一份token和id信息作为其他登录验证场景调用
                SharedPreferenceUtil.setServerToken(mcUser.getToken());
                SharedPreferenceUtil.setServerUserId(mcUser.getExternal_id());
                SharedPreferenceUtil.setMyCardUserName(mcUser.getUsername());
            }
            if (mListener != null)
                mListener.onLogin(mcUser, exception);
        }

        @JavascriptInterface
        public void updateUser(String name, String headurl, String status) {
            McUser mcUser = UserManagement.getDx().getMcUser();
            if (mcUser == null)
                mcUser = new McUser();
            mcUser.setUsername(name);
            mcUser.setAvatar_url(headurl);
            mcUser.setEmail(status);
            UserManagement.getDx().setMcUser(mcUser);
            if (mListener != null) {
                mListener.onUpdate(name, headurl, status);
            }
        }

        @JavascriptInterface
        public void logoutUser(String message) {
            UserManagement.getDx().logout();
            if (mListener != null) {
                mListener.onLogout(message);
            }
        }

        /*
         * 设置文件修改时间
         * path: 文件绝对路径
         * time: 时间
         */
        @JavascriptInterface
        public void setFileLastModified(String path, long time) {
            File file = new File(settings.getResourcePath(), path);
            if (file.setLastModified(time)) {
                removeWrappedLastModified(path);
            } else {
                setWrappedLastModified(path, file.lastModified(), time);
            }
        }


        // 由于 Android 上设置文件修改时间是不可靠的，这里做个wrap，如果设置失败，就自己存一份。
        private void setWrappedLastModified(String path, long origin, long wrapped) {
            lastModified.edit()
                    .putLong("ORIGIN_" + path, origin)
                    .putLong("WRAPPED_" + path, wrapped)
                    .apply();
        }

        private long getWrappedLastModified(String path, long origin) {
            if (lastModified.getLong("ORIGIN_" + path, 0) == origin) {
                return lastModified.getLong("WRAPPED_" + path, 0);
            } else {
                removeWrappedLastModified(path);
                return origin;
            }
        }

        private void removeWrappedLastModified(String path) {
            if (lastModified.contains("ORIGIN_" + path)) {
                lastModified.edit()
                        .remove("ORIGIN_" + path)
                        .remove("WRAPPED_" + path)
                        .apply();
            }
        }

    }

    public static String getMycardPostUrl(String id) {
        return MYCARD_POST_URL + id;
    }

    public static String getImagePath(Context context) {
//        return context.getExternalFilesDir("image").getAbsolutePath();
        return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getAbsolutePath(), "YGOMobile").getAbsolutePath();
    }

    public static String getImageCachePath() {
        return App.get().getExternalFilesDir("cache/image").getAbsolutePath();
    }

    public static String getCachePath() {
        return App.get().getExternalFilesDir("cache").getAbsolutePath();
    }
}
