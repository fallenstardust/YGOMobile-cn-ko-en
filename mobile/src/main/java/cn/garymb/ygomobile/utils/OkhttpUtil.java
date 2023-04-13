package cn.garymb.ygomobile.utils;

import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import cn.garymb.ygomobile.bean.Header;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.FormBody;
import okhttp3.Headers;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class OkhttpUtil {

    private static OkHttpClient okHttpClient;

    public static void post(String address, Map<String, Object> map, Callback callback) {
        post(address, map, null, null, 0, callback);
    }

    public static void post(String address, Map<String, Object> map, Header oyHeader, String tag, int timeout, Callback callback) {
        post(address, map, null, oyHeader, tag, timeout, callback);
    }

    public static void post(String address, Map<String, Object> map, String cookie, Header oyHeader, String tag, int timeout, Callback callback) {
        okHttpClient = new OkHttpClient();

        if (timeout != 0)
            okHttpClient = okHttpClient.newBuilder().connectTimeout(timeout, TimeUnit.SECONDS)//设置连接超时时间
                    .readTimeout(timeout, TimeUnit.SECONDS)//设置读取超时时间
                    .build();

        MultipartBody.Builder builder1 = new MultipartBody.Builder();
        if (map != null) {
            builder1.setType(MultipartBody.FORM);
            for (Map.Entry<String, Object> stringObjectEntry : map.entrySet()) {
                String key = stringObjectEntry.getKey();
                Object value = stringObjectEntry.getValue();
                if (value instanceof List) {
                    List list = (List) value;
                    for (Object object : list) {
                        if (object instanceof File) {
                            File file = (File) object;
                            builder1.addFormDataPart(key
                                    , file.getName(),
                                    RequestBody.create(MediaType.parse("multipart/form-data"), file));
                        } else {
                            builder1.addFormDataPart(key, value.toString());
                        }
                    }
                } else if (value instanceof File) {//如果请求的值是文件
                    File file = (File) value;
                    //MediaType.parse("application/octet-stream")以二进制的形式上传文件
                    builder1.addFormDataPart(key, file.getName(),
                            RequestBody.create(MediaType.parse("multipart/form-data"), file));
                } else if (value instanceof String[]) {

                    String[] list = (String[]) value;
                    for (Object object : list) {
                        if (object instanceof File) {
                            File file = (File) object;
                            builder1.addFormDataPart(key
                                    , file.getName(),
                                    RequestBody.create(MediaType.parse("multipart/form-data"), file));
                        } else {
                            Log.e("OkHttpUtil", key + "添加数组" + object.toString());
                            builder1.addFormDataPart(key, object.toString());
                        }
                    }
//                    Log.e("OkhttpUtil","添加数组"+new Gson().toJson(value));
                } else {
                    //如果请求的值是string类型
                    builder1.addFormDataPart(key, value.toString());
                }
            }
        }
        Request.Builder request = new Request.Builder()
                .url(address);
        if (oyHeader != null)
            request = request.header(oyHeader.getName(), oyHeader.getValue());

//        request.addHeader("Connection", "keep-alive");

        if (!TextUtils.isEmpty(tag))
            request = request.tag(tag);
        if (map != null)
            request.post(builder1.build());
        else
            request.post(okhttp3.internal.Util.EMPTY_REQUEST);


//        Log.e("OkhttpUtil","post请求："+builder1.build().toString());
        if (!TextUtils.isEmpty(cookie)) {
            request.addHeader("cookie", cookie);
        }
        okHttpClient.newCall(request.build()).enqueue(callback);
    }


    public static void put(String address, Map<String, Object> map, String cookie, Callback callback) {
        OkHttpClient.Builder client = new OkHttpClient.Builder();

        MultipartBody.Builder builder1 = new MultipartBody.Builder();
        if (map != null) {
            builder1.setType(MultipartBody.FORM);
            for (Map.Entry<String, Object> stringObjectEntry : map.entrySet()) {
                String key = stringObjectEntry.getKey();
                Object value = stringObjectEntry.getValue();
                if (value instanceof List) {
                    List list = (List) value;
                    for (Object object : list) {
                        if (object instanceof File) {
                            File file = (File) object;
                            builder1.addFormDataPart(key
                                    , file.getName(),
                                    RequestBody.create(MediaType.parse("multipart/form-data"), file));
                        } else {
                            builder1.addFormDataPart(key, value.toString());
                        }
                    }
                } else if (value instanceof File) {//如果请求的值是文件
                    File file = (File) value;
                    //MediaType.parse("application/octet-stream")以二进制的形式上传文件
                    builder1.addFormDataPart(key, file.getName(),
                            RequestBody.create(MediaType.parse("multipart/form-data"), file));
                } else if (value instanceof String[]) {

                    String[] list = (String[]) value;
                    for (Object object : list) {
                        if (object instanceof File) {
                            File file = (File) object;
                            builder1.addFormDataPart(key
                                    , file.getName(),
                                    RequestBody.create(MediaType.parse("multipart/form-data"), file));
                        } else {
                            Log.e("OkHttpUtil", key + "添加数组" + object.toString());
                            builder1.addFormDataPart(key, object.toString());
                        }
                    }
//                    Log.e("OkhttpUtil","添加数组"+new Gson().toJson(value));
                } else {
                    //如果请求的值是string类型
                    builder1.addFormDataPart(key, value.toString());
                }
            }
        }
        Request.Builder request = new Request.Builder()
                .url(address);
        if (map != null)
            request.put(builder1.build());
        else
            request.put(okhttp3.internal.Util.EMPTY_REQUEST);

//        Log.e("OkhttpUtil","post请求："+builder1.build().toString());
        if (!TextUtils.isEmpty(cookie)) {
            request.addHeader("cookie", cookie);
        }
        client.build().newCall(request.build()).enqueue(callback);
    }


    public static void get(String address, Callback callback) {
        get(address, null, null, callback);
    }

    public static void get(String address, Map<String, Object> map, Callback callback) {
        get(address, map, null, callback);
    }

    public static void get(String address, Map<String, Object> map, String cookie, Callback callback) {
        OkHttpClient client = new OkHttpClient();

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

    public static void del(String address, Map<String, Object> map, String cookie, Callback callback) {
        OkHttpClient client = new OkHttpClient();

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
        OkHttpClient okHttpClient = new OkHttpClient();
        RequestBody requestBody = FormBody.create(MediaType.parse("application/json; charset=utf-8")
                , json);
        Request.Builder request = new Request.Builder()
                .url(url);//请求的url
        if (TextUtils.isEmpty(json))
            request.post(okhttp3.internal.Util.EMPTY_REQUEST);
        else
            request.post(requestBody);
        if (!TextUtils.isEmpty(cookie)) {
            request.addHeader("cookie", cookie);
        }
        okHttpClient.newCall(request.build()).enqueue(callback);
    }

    public static void cancelTag(Object tag) {
        for (Call call : okHttpClient.dispatcher().queuedCalls()) {
            if (tag.equals(call.request().tag())) {
                call.cancel();
            }
        }
        for (Call call : okHttpClient.dispatcher().runningCalls()) {
            if (tag.equals(call.request().tag())) {
                call.cancel();
            }
        }
    }

    public static void postJson(String url, String json, Callback callback) {
        postJson(url, json, null, null, null, 0, callback);
    }

    public static void postJson(String url, String json, String cookie, Header oyHeader, String tag, int timeout, Callback callback) {
        okHttpClient = new OkHttpClient();

        if (timeout != 0)
            okHttpClient = okHttpClient.newBuilder().connectTimeout(timeout, TimeUnit.SECONDS)//设置连接超时时间
                    .readTimeout(timeout, TimeUnit.SECONDS)//设置读取超时时间
                    .build();


        RequestBody requestBody = FormBody.create(MediaType.parse("application/json; charset=utf-8")
                , json);
        Request.Builder request = new Request.Builder()
                .url(url);//请求的url
        if (TextUtils.isEmpty(json))
            request.post(okhttp3.internal.Util.EMPTY_REQUEST);
        else
            request.post(requestBody);

        if (oyHeader != null)
            request = request.header(oyHeader.getName(), oyHeader.getValue());

        if (!TextUtils.isEmpty(tag))
            request = request.tag(tag);

        if (!TextUtils.isEmpty(cookie)) {
            request.addHeader("cookie", cookie);
        }
        Log.e("OkhttpUtil",json+" 状态 "+ request.build());
        okHttpClient.newCall(request.build()).enqueue(callback);
    }


}
