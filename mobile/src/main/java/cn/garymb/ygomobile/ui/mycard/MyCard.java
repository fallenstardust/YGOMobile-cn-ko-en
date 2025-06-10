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
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygodata.YGOGameOptions;
import cn.garymb.ygomobile.App;
import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.YGOStarter;
import cn.garymb.ygomobile.bean.events.DeckFile;
import cn.garymb.ygomobile.ui.mycard.bean.McUser;
import cn.garymb.ygomobile.ui.mycard.mcchat.management.UserManagement;
import cn.garymb.ygomobile.ui.plus.DefWebViewClient;
import cn.garymb.ygomobile.utils.DeckUtil;
import cn.garymb.ygomobile.utils.JsonUtil;
import cn.garymb.ygomobile.utils.OkhttpUtil;
import cn.garymb.ygomobile.utils.SharedPreferenceUtil;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

public class MyCard {

    private static final String mHomeUrl = "https://mycard.world/mobile/";
    private static final String mArenaUrl = "https://mycard.world/ygopro/arena/";
    public static final String mCommunityReportUrl = "https://ygobbs2.com/t/bug%E5%8F%8D%E9%A6%88/";
    private static final String mCommunityUrl = "https://ygobbs2.com/login";
    public static final String mCompetitionUrl = "https://event.ygobbs2.com/";
    private static final String HOST_MC = "mycard.world";
    public static final String MC_MAIN_URL = "https://mycard.world/mobile/ygopro/lobby";

    public static final String MYCARD_NEWS_URL = "https://sapi.moecube.com:444/apps.json";
    public static final String MYCARD_POST_URL = "https://ygobbs2.com/t/";
    public static final String ARG_ID = "id";
    public static final String ARG_TITLE = "title";


    public static final String ARG_MC_NAME = "name";
    public static final String ARG_YGOPRO = "ygopro";
    public static final String ARG_ZH_CN = "zh-CN";
    public static final String ARG_IMAGE = "image";
    public static final String ARG_UPDATE_AT = "updated_at";
    public static final String ARG_URL = "url";
    public static final String ARG_NEWS = "news";
    public static final String URL_MC_SIGN_UP = "https://accounts.moecube.com/signup";
    public static final String URL_MC_LOGOUT = "https://accounts.moecube.com/signin";
    private final DefWebViewClient mDefWebViewClient = new DefWebViewClient() {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
//                if (url.startsWith(return_sso_url)) {
//
//
//                    String sso = Uri.parse(url).getQueryParameter("sso");
//                    String data = new String(Base64.decode(Uri.parse(url).getQueryParameter("sso"), Base64.NO_WRAP), UTF_8);
//                    Uri info = new Uri.Builder().encodedQuery(data).build();
//                    mUser.external_id = Integer.parseInt(info.getQueryParameter("external_id"));
//                    mUser.username = info.getQueryParameter("username");
//                    mUser.name = info.getQueryParameter("name");
//                    mUser.email = info.getQueryParameter("email");
//                    mUser.avatar_url = info.getQueryParameter("avatar_url");
//                    mUser.admin = info.getBooleanQueryParameter("admin", false);
//                    mUser.moderator = info.getBooleanQueryParameter("moderator", false);
//                    lastModified.edit().putString("user_external_id", mUser.external_id + "").apply();
//                    lastModified.edit().putString("user_name", mUser.username).apply();
//                    //UserManagement.setUserName(mUser.username);
//                    //UserManagement.setUserPassword(mUser.external_id+"");
//                    mUser.login = true;
//                    if (getMyCardListener() != null) {
//                        getMyCardListener().onLogin(mUser.name, mUser.avatar_url, null);
//                    }
//                    return false;
//                }
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

    public static String getMCLogoutUrl(){
        String home="return_sso_url="+Uri.encode(mHomeUrl);
        String base64=Base64.encodeToString(home.getBytes(), Base64.NO_WRAP);
        Uri.Builder uri = Uri.parse(URL_MC_LOGOUT)
                .buildUpon();
        uri.appendQueryParameter("sso",base64);
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
        void onLogin(McUser mcUser,String exception);

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
        public void loginUser(String userInfo,String exception){
            McUser mcUser = null;
            if (TextUtils.isEmpty(exception)) {
                mcUser = new Gson().fromJson(userInfo, McUser.class);
                UserManagement.getDx().setMcUser(mcUser);
                Log.i("seesee", userInfo.substring(userInfo.lastIndexOf(":")+2,userInfo.lastIndexOf("\"")));
                SharedPreferenceUtil.setServerToken(userInfo.substring(userInfo.lastIndexOf(":")+2,userInfo.lastIndexOf("\"")));
                SharedPreferenceUtil.setServerUserId(mcUser.getExternal_id());
            }
            if (mListener!=null)
                mListener.onLogin(mcUser,exception);
        }

        @JavascriptInterface
        public void updateUser(String name, String headurl, String status) {
            McUser mcUser=UserManagement.getDx().getMcUser();
            if (mcUser==null)
                mcUser=new McUser();
            mcUser.setUsername(name);
            mcUser.setAvatar_url(headurl);
            mcUser.setEmail(status);
            UserManagement.getDx().setMcUser(mcUser);
            if (mListener != null) {
                mListener.onUpdate(name, headurl, status);
            }
        }

        @JavascriptInterface
        public void logoutUser(String message){
            UserManagement.getDx().logout();
            if (mListener!=null) {
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
        return new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).getAbsolutePath(), "YGOMobile OY").getAbsolutePath();
    }

    public static String getImageCachePath() {
        return App.get().getExternalFilesDir("cache/image").getAbsolutePath();
    }

    public static String getCachePath() {
        return App.get().getExternalFilesDir("cache").getAbsolutePath();
    }
}
