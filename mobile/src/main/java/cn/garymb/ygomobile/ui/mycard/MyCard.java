package cn.garymb.ygomobile.ui.mycard;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.webkit.JavascriptInterface;

import com.tencent.smtt.sdk.WebView;

import org.json.JSONArray;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Arrays;

import cn.garymb.ygodata.YGOGameOptions;
import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.YGOStarter;
import cn.garymb.ygomobile.ui.cards.DeckManagerActivity;
import cn.garymb.ygomobile.ui.plus.DefWebViewClient;

import static junit.framework.Assert.assertEquals;

public class MyCard {

    private static final String mHomeUrl = "https://mycard.moe/mobile/";
    private static final String mArenaUrl = "https://mycard.moe/ygopro/arena/";
    private static final String mCommunityUrl = "https://ygobbs.com/login";
    private static final String return_sso_url = "https://mycard.moe/mobile/?";
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private final DefWebViewClient mDefWebViewClient;
    private final User mUser = new User();
    private final SharedPreferences lastModified;
    private MyCardListener mMyCardListener;
    private Activity mContext;

    public MyCard(Activity context) {
        mContext = context;
        lastModified = context.getSharedPreferences("lastModified", Context.MODE_PRIVATE);
        mDefWebViewClient = new DefWebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url.startsWith(return_sso_url)) {
                    String sso = Uri.parse(url).getQueryParameter("sso");
                    String data = new String(Base64.decode(Uri.parse(url).getQueryParameter("sso"), Base64.NO_WRAP), UTF_8);
                    Uri info = new Uri.Builder().encodedQuery(data).build();
                    mUser.external_id = Integer.parseInt(info.getQueryParameter("external_id"));
                    mUser.username = info.getQueryParameter("username");
                    mUser.name = info.getQueryParameter("name");
                    mUser.email = info.getQueryParameter("email");
                    mUser.avatar_url = info.getQueryParameter("avatar_url");
                    mUser.admin = info.getBooleanQueryParameter("admin", false);
                    mUser.moderator = info.getBooleanQueryParameter("moderator", false);
                    lastModified.edit().putString("user_external_id", mUser.external_id + "").apply();
                    lastModified.edit().putString("user_name", mUser.username).apply();
                    //UserManagement.setUserName(mUser.username);
                    //UserManagement.setUserPassword(mUser.external_id+"");
                    mUser.login = true;
                    if (getMyCardListener() != null) {
                        getMyCardListener().onLogin(mUser.name, mUser.avatar_url, null);
                    }
                    return false;
                }
                return super.shouldOverrideUrlLoading(view, url);
            }
        };
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

//    public String getLoginUrl() throws NoSuchAlgorithmException, InvalidKeyException {
//        Uri.Builder payloadBuilder = new Uri.Builder();
//        payloadBuilder.appendQueryParameter("return_sso_url", return_sso_url);
//        byte[] payload = Base64.encode(payloadBuilder.build().getQuery().getBytes(UTF_8), Base64.NO_WRAP);
//
//        Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
//        SecretKeySpec secret_key = new SecretKeySpec(key.getBytes(UTF_8), "HmacSHA256");
//        sha256_HMAC.init(secret_key);
//        String signature = byteArrayToHexString(sha256_HMAC.doFinal(payload));
//        Uri.Builder requestBuilder = Uri.parse(sso_url).buildUpon();
//        requestBuilder.appendQueryParameter("sso", new String(payload, UTF_8));
//        requestBuilder.appendQueryParameter("sig", signature);
//        return requestBuilder.build().toString();
//    }

    public String getBBSUrl() {
        return mCommunityUrl;
    }

    @SuppressLint("AddJavascriptInterface")
    public void attachWeb(MyCardWebView webView, MyCardListener myCardListener) {
        mMyCardListener = myCardListener;
        webView.setWebViewClient(getWebViewClient());
        webView.addJavascriptInterface(new MyCard.Ygopro(mContext, myCardListener), "ygopro");
        String name = lastModified.getString("user_name", null);
        String headurl = lastModified.getString("user_avatar_url", null);
        if (mMyCardListener != null) {
            if (!TextUtils.isEmpty(name)) {
                mMyCardListener.onLogin(name, headurl, null);
            }
        }
    }

    public interface MyCardListener {
        void onLogin(String name, String icon, String statu);

        void watchReplay();

        void puzzleMode();

        void openDrawer();

        void closeDrawer();

        void backHome();

        void share(String text);

        void onHome();

    }

    public static class User {
        int external_id;
        String username;
        String name;
        String email;
        String avatar_url;
        boolean admin;
        boolean moderator;
        boolean login;

        public User() {

        }

        public String getJID() {
            return username + "@mycard.moe";
        }

        public String getPassword() {
            return String.valueOf(external_id);
        }

        public String getConference() {
            return "ygopro_china_north@conference.mycard.moe";
        }
    }

    public class Ygopro {
        Activity activity;
        MyCardListener mListener;

        private AppsSettings settings = AppsSettings.get();

        private Ygopro(Activity activity, MyCardListener listener) {
            this.activity = activity;
            mListener = listener;
        }

        @JavascriptInterface
        public void edit_deck() {
            activity.startActivity(new Intent(activity, DeckManagerActivity.class));
        }

        @JavascriptInterface
        public void watch_replay() {
            if (mListener != null) {
                activity.runOnUiThread(mListener::watchReplay);
            }
        }

        @JavascriptInterface
        public void puzzle_mode() {
            if (mListener != null) {
                activity.runOnUiThread(mListener::puzzleMode);
            }
        }

        @JavascriptInterface
        public void openDrawer() {
            if (mListener != null) {
                activity.runOnUiThread(mListener::openDrawer);
            }
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
        public void closeDrawer() {
            if (mListener != null) {
                activity.runOnUiThread(mListener::closeDrawer);
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
            return new JSONArray(Arrays.asList(result)).toString();
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
        public void updateUser(String name, String headurl, String status) {
            if (mListener != null) {
                mUser.name = name;
                mUser.avatar_url = headurl;
                mUser.login = true;
                lastModified.edit()
                        .putString("user_name", name)
                        .putString("user_avatar_url", headurl)
                        .apply();
                mListener.onLogin(name, headurl, status);
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
}
