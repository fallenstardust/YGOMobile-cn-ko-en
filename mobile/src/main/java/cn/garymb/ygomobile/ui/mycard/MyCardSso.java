package cn.garymb.ygomobile.ui.mycard;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.mycard.bean.McUser;
import cn.garymb.ygomobile.ui.mycard.mcchat.management.UserManagement;
import cn.garymb.ygomobile.ui.plus.VUiKit;
import cn.garymb.ygomobile.utils.OkhttpUtil;
import cn.garymb.ygomobile.utils.SharedPreferenceUtil;
import cn.garymb.ygomobile.utils.YGOUtil;
import okhttp3.Response;

public final class MyCardSso {
    private static final String TAG = "MyCardSso";
    private static final String SCHEME = "ygo";
    private static final String CALLBACK_HOST = "mycard-sso";
    private static final String QUERY_SSO = "sso";
    private static final String ACCOUNTS_LOGIN_URL = "https://accounts.moecube.com";

    private MyCardSso() {
    }

    public static boolean isCallbackUri(Uri uri) {
        return uri != null
                && SCHEME.equals(uri.getScheme())
                && CALLBACK_HOST.equals(uri.getHost());
    }

    public static void openLogin(Context context) {
        openAccountsUrl(context, buildAccountsUrl(ACCOUNTS_LOGIN_URL));
    }

    public static void openSignup(Context context) {
        openAccountsUrl(context, buildAccountsUrl(MyCard.URL_MC_SIGN_UP));
    }

    public static void handleCallback(Activity activity, Uri uri, Callback callback) {
        VUiKit.defer().when(() -> validateAndStore(uri)).fail((e) -> {
            Log.e(TAG, "SSO login failed", e);
            YGOUtil.showTextToast(R.string.logining_failed);
            if (callback != null) {
                callback.onFailure(e);
            }
        }).done((mcUser) -> {
            YGOUtil.showTextToast(R.string.login_succeed);
            if (callback != null) {
                callback.onSuccess(mcUser);
            }
        });
    }

    private static Uri buildAccountsUrl(String baseUrl) {
        String returnUrl = new Uri.Builder()
                .scheme(SCHEME)
                .authority(CALLBACK_HOST)
                .build()
                .toString();
        String payload = "return_sso_url=" + Uri.encode(returnUrl);
        String sso = Base64.encodeToString(payload.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        return Uri.parse(baseUrl).buildUpon()
                .appendQueryParameter(QUERY_SSO, sso)
                .build();
    }

    private static void openAccountsUrl(Context context, Uri uri) {
        if (context == null) {
            return;
        }
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        if (!(context instanceof Activity)) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        try {
            context.startActivity(intent);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(context, R.string.no_find_file_selectotr, Toast.LENGTH_SHORT).show();
        }
    }

    private static McUser validateAndStore(Uri callbackUri) throws IOException {
        if (!isCallbackUri(callbackUri)) {
            throw new IOException("invalid sso callback");
        }
        String sso = callbackUri.getQueryParameter(QUERY_SSO);
        if (TextUtils.isEmpty(sso)) {
            throw new IOException("sso payload not found");
        }

        McUser ssoUser = parseSsoUser(sso);
        String token = ssoUser.getToken();
        if (TextUtils.isEmpty(token)) {
            throw new IOException("token not found");
        }

        McUser authUser = fetchAuthUser(token);
        McUser mcUser = mergeUser(ssoUser, authUser, token);
        if (TextUtils.isEmpty(mcUser.getUsername()) || mcUser.getExternal_id() == 0) {
            throw new IOException("invalid auth user");
        }

        SharedPreferenceUtil.setServerToken(mcUser.getToken());
        SharedPreferenceUtil.setServerUserId(mcUser.getExternal_id());
        SharedPreferenceUtil.setMyCardUserName(mcUser.getUsername());
        UserManagement.getDx().setMcUser(mcUser);
        return mcUser;
    }

    private static McUser parseSsoUser(String sso) throws IOException {
        try {
            String query = new String(Base64.decode(sso, Base64.DEFAULT), StandardCharsets.UTF_8);
            Uri params = Uri.parse("https://local/?" + query);
            McUser user = new McUser();
            user.setExternal_id(readInt(params, "external_id"));
            user.setId(readInt(params, "id"));
            user.setUsername(params.getQueryParameter("username"));
            user.setEmail(params.getQueryParameter("email"));
            user.setAvatar_url(firstNonEmpty(params.getQueryParameter("avatar_url"), params.getQueryParameter("avatar")));
            user.setToken(params.getQueryParameter("token"));
            user.setAdmin(Boolean.parseBoolean(params.getQueryParameter("admin")));
            return user;
        } catch (IllegalArgumentException e) {
            throw new IOException("invalid sso payload", e);
        }
    }

    private static McUser fetchAuthUser(String token) throws IOException {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + token);
        Response response = OkhttpUtil.synchronousGet(MyCard.URL_MC_AUTH_USER, null, headers);
        try {
            String responseBody = response.body().string();
            if (!response.isSuccessful()) {
                throw new IOException("authUser failed: " + responseBody);
            }
            JsonObject jsonObject = new JsonParser().parse(responseBody).getAsJsonObject();
            McUser user = new McUser();
            int id = readInt(jsonObject, "id");
            user.setId(id);
            user.setExternal_id(id);
            user.setUsername(readString(jsonObject, "username"));
            user.setEmail(readString(jsonObject, "email"));
            user.setAvatar_url(firstNonEmpty(readString(jsonObject, "avatar_url"), readString(jsonObject, "avatar")));
            user.setAdmin(readBoolean(jsonObject, "admin"));
            return user;
        } finally {
            response.close();
        }
    }

    private static McUser mergeUser(McUser ssoUser, McUser authUser, String token) {
        McUser user = new McUser();
        int id = authUser.getExternal_id() != 0 ? authUser.getExternal_id() : firstNonZero(ssoUser.getExternal_id(), ssoUser.getId());
        user.setId(id);
        user.setExternal_id(id);
        user.setUsername(firstNonEmpty(authUser.getUsername(), ssoUser.getUsername()));
        user.setEmail(firstNonEmpty(authUser.getEmail(), ssoUser.getEmail()));
        user.setAvatar_url(firstNonEmpty(authUser.getAvatar_url(), ssoUser.getAvatar_url()));
        user.setAdmin(authUser.isAdmin() || ssoUser.isAdmin());
        user.setToken(token);
        return user;
    }

    private static int readInt(Uri uri, String key) {
        String value = uri.getQueryParameter(key);
        if (TextUtils.isEmpty(value)) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static int readInt(JsonObject object, String key) {
        String value = readString(object, key);
        if (TextUtils.isEmpty(value)) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static boolean readBoolean(JsonObject object, String key) {
        JsonElement element = object.get(key);
        return element != null && !element.isJsonNull() && element.getAsBoolean();
    }

    private static String readString(JsonObject object, String key) {
        JsonElement element = object.get(key);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        return element.getAsString();
    }

    private static String firstNonEmpty(String first, String second) {
        return !TextUtils.isEmpty(first) ? first : second;
    }

    private static int firstNonZero(int first, int second) {
        return first != 0 ? first : second;
    }

    public interface Callback {
        void onSuccess(McUser mcUser);

        void onFailure(Throwable throwable);
    }
}
