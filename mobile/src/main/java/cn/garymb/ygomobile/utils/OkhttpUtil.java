package cn.garymb.ygomobile.utils;

import android.text.TextUtils;
import android.util.Log;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.ConnectionPool;
import okhttp3.FormBody;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class OkhttpUtil {

    // private static OkHttpClient okHttpClient;
    private static final OkHttpClient client = new OkHttpClient.Builder()
            // customize timeouts as needed
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            // keep-alive and connection pool defaults
            .connectionPool(new ConnectionPool(2, 20, TimeUnit.SECONDS))
            .build();


    public static void get(String address, Callback callback) {
        get(address, null, null, callback);
    }

    public static void get(String address, Map<String, Object> map, Callback callback) {
        get(address, map, null, callback);
    }

    public static void get(String address, Map<String, Object> map, String cookie, Callback callback) {

        HttpUrl.Builder httpBuilder = HttpUrl.parse(address).newBuilder();
        if (map != null) {
            for (Map.Entry<String, Object> param : map.entrySet()) {
                httpBuilder.addQueryParameter(param.getKey(), param.getValue().toString());
            }
        }

        Request.Builder request = new Request.Builder()
                .url(httpBuilder.build());
        Log.e("OkhttpUtil", "为" + httpBuilder.build());
        if (!TextUtils.isEmpty(cookie)) {
            request.addHeader("cookie", cookie);
        }
        client.newCall(request.build()).enqueue(callback);
    }

    public static Response synchronousGet(String address, Map<String, Object> paramMap, Map<String, String> headers) throws IOException {

        HttpUrl.Builder httpBuilder = HttpUrl.parse(address).newBuilder();
        if (paramMap != null) {
            for (Map.Entry<String, Object> param : paramMap.entrySet()) {
                httpBuilder.addQueryParameter(param.getKey(), param.getValue().toString());
            }
        }

        Request.Builder request = new Request.Builder()
                .url(httpBuilder.build());
        Log.e("OkhttpUtil", "get " + httpBuilder.build());
        if (headers != null) {
            for (Map.Entry<String, String> header : headers.entrySet()) {
                request.addHeader(header.getKey(), header.getValue().toString());
            }
        }
//        if (!TextUtils.isEmpty(cookie)) {
//            request.addHeader("cookie", cookie);
//        }
        return client.newCall(request.build()).execute();
    }

    public static void del(String address, Map<String, Object> map, String cookie, Callback callback) {

        HttpUrl.Builder httpBuilder = HttpUrl.parse(address).newBuilder();
        if (map != null) {
            for (Map.Entry<String, Object> param : map.entrySet()) {
                httpBuilder.addQueryParameter(param.getKey(), param.getValue().toString());
            }
        }

        Request.Builder request = new Request.Builder()
                .delete()
                .url(httpBuilder.build());
        Log.e("OkhttpUtil", "删除为" + httpBuilder.build());
        if (!TextUtils.isEmpty(cookie)) {
            request.addHeader("cookie", cookie);
        }
        client.newCall(request.build()).enqueue(callback);
    }

    public static String getSession(Response response) {
        Headers headers = response.headers();     //response为okhttp请求后的响应
        List<String> cookies = headers.values("Set-Cookie");

        if (cookies.size() != 0) {
            String session = cookies.get(0);
            return session.substring(0, session.indexOf(";"));
        }
        return null;
    }

    public static void post(String url, String json, Callback callback) {
        post(url, json, null, callback);
    }

    public static void post(String url, String json, String cookie, Callback callback) {
        RequestBody requestBody = FormBody.create(MediaType.parse("application/json; charset=utf-8"), json);
        Request.Builder request = new Request.Builder().url(url);//请求的url
        if (TextUtils.isEmpty(json))
            request.post(okhttp3.internal.Util.EMPTY_REQUEST);
        else
            request.post(requestBody);
        if (!TextUtils.isEmpty(cookie)) {
            request.addHeader("cookie", cookie);
        }
        client.newCall(request.build()).enqueue(callback);
    }

    public static void cancelTag(Object tag) {
        for (Call call : client.dispatcher().queuedCalls()) {
            if (tag.equals(call.request().tag())) {
                call.cancel();
            }
        }
        for (Call call : client.dispatcher().runningCalls()) {
            if (tag.equals(call.request().tag())) {
                call.cancel();
            }
        }
    }

    /**
     * 阻塞方法，POST推送json
     *
     * @param url
     * @param json    可以传入null或空字符串，均代表不需要发送json
     * @param headers 可以传入null
     */
    public static Response postJson(String url, String json, Map<String, String> headers) throws IOException {

        Request.Builder request = new Request.Builder().url(url);//请求的url
        if (json == null || TextUtils.isEmpty(json)) {
            request.post(okhttp3.internal.Util.EMPTY_REQUEST);
        } else {
            RequestBody requestBody = FormBody.create(MediaType.parse("application/json; charset=utf-8"), json);
            request.post(requestBody);

        }

        if (headers != null) {
            for (Map.Entry<String, String> header : headers.entrySet()) {
                request.addHeader(header.getKey(), header.getValue().toString());
            }
        }

        Log.e("OkhttpUtil", json + " 状态 " + request.build());
        return client.newCall(request.build()).execute();
    }

    /**
     * 将byte[]类型的十六进制数据（不进行解码）转为字符串格式。如，byte[]中存储的值为0xab78，则转换后的字符串的内容为“ab78”，
     * byte[]中存储的值为0xb78，则转换后的字符串的内容为“0b78”
     * 可用于将byte中的数据不做改变地打印到log中。
     *
     * @param buf
     * @return
     */
    public static String parseByte2HexStr(byte[] buf) {
        if (null == buf) {
            return null;
        }

        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < buf.length; i++) {
            String hex = Integer.toHexString(buf[i] & 0xFF);
            if (hex.length() == 1) {
                hex = '0' + hex;
            }
            sb.append(hex.toUpperCase());
        }
        return sb.toString();
    }
}
