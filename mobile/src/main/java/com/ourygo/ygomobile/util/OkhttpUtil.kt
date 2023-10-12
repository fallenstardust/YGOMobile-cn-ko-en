package com.ourygo.ygomobile.util

import android.text.TextUtils
import android.util.Log
import com.ourygo.ygomobile.bean.OYHeader
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.internal.Util
import java.io.File
import java.util.concurrent.TimeUnit

object OkhttpUtil {
    private lateinit var okHttpClient: OkHttpClient
    fun post(address: String, map: Map<String, Any>?, callback: Callback) {
        post(address, map, null, null, 0, callback)
    }

    fun post(
        address: String,
        map: Map<String, Any>?,
        oyHeader: OYHeader?,
        tag: String?,
        timeout: Int,
        callback: Callback
    ) {
        post(address, map, null, oyHeader, tag, timeout, callback)
    }

    fun post(
        address: String,
        map: Map<String, Any>?,
        cookie: String?,
        oyHeader: OYHeader?,
        tag: String?,
        timeout: Int,
        callback: Callback
    ) {
        okHttpClient = OkHttpClient()
        if (timeout != 0) okHttpClient = okHttpClient.newBuilder()
            .connectTimeout(timeout.toLong(), TimeUnit.SECONDS) //设置连接超时时间
            .readTimeout(timeout.toLong(), TimeUnit.SECONDS) //设置读取超时时间
            .build()
        val builder1 = MultipartBody.Builder()
        if (map != null) {
            builder1.setType(MultipartBody.FORM)
            for ((key, value: Any) in map) {
                if (value is List<*>) {
                    for (mObject in value) {
                        if (mObject is File) {
                            builder1.addFormDataPart(
                                key, mObject.name,
                                RequestBody.create(MediaType.parse("multipart/form-data"), mObject)
                            )
                        } else {
                            builder1.addFormDataPart(key, value.toString())
                        }
                    }
                } else if (value is File) { //如果请求的值是文件
                    //MediaType.parse("application/octet-stream")以二进制的形式上传文件
                    builder1.addFormDataPart(
                        key, value.name,
                        RequestBody.create(MediaType.parse("multipart/form-data"), value)
                    )
                } else if (value is Array<*> && value.isArrayOf<String>()) {
                    for (mObject in value) {
                        if (mObject is File) {
                            builder1.addFormDataPart(
                                key, mObject.name,
                                RequestBody.create(MediaType.parse("multipart/form-data"), mObject)
                            )
                        } else {
                            Log.e("OkHttpUtil", key + "添加数组" + mObject)
                            builder1.addFormDataPart(key, mObject.toString())
                        }
                    }
                    //                    Log.e("OkhttpUtil","添加数组"+new Gson().toJson(value));
                } else {
                    //如果请求的值是string类型
                    builder1.addFormDataPart(key, value.toString())
                }
            }
        }
        var request = Request.Builder()
            .url(address)
        if (oyHeader != null) request = request.header(oyHeader.name, oyHeader.value)

//        request.addHeader("Connection", "keep-alive");
        if (!TextUtils.isEmpty(tag)) request = request.tag(tag)
        if (map != null) request.post(builder1.build()) else request.post(Util.EMPTY_REQUEST)


//        Log.e("OkhttpUtil","post请求："+builder1.build().toString());
        if (!TextUtils.isEmpty(cookie)) {
            request.addHeader("cookie", cookie.toString())
        }
        okHttpClient.newCall(request.build()).enqueue(callback)
    }

    fun put(address: String?, map: Map<String, Any>?, cookie: String?, callback: Callback) {
        val client = OkHttpClient.Builder()
        val builder1 = MultipartBody.Builder()
        if (map != null) {
            builder1.setType(MultipartBody.FORM)
            for ((key, value) in map) {
                if (value is List<*>) {
                    for (mObject in value) {
                        if (mObject is File) {
                            builder1.addFormDataPart(
                                key, mObject.name,
                                RequestBody.create(MediaType.parse("multipart/form-data"), mObject)
                            )
                        } else {
                            builder1.addFormDataPart(key, value.toString())
                        }
                    }
                } else if (value is File) { //如果请求的值是文件
                    //MediaType.parse("application/octet-stream")以二进制的形式上传文件
                    builder1.addFormDataPart(
                        key, value.name,
                        RequestBody.create(MediaType.parse("multipart/form-data"), value)
                    )
                } else if (value is Array<*> && value.isArrayOf<String>()) {
                    for (mObject in value) {
                        if (mObject is File) {
                            builder1.addFormDataPart(
                                key, mObject.name,
                                RequestBody.create(MediaType.parse("multipart/form-data"), mObject)
                            )
                        } else {
                            Log.e("OkHttpUtil", key + "添加数组" + mObject)
                            builder1.addFormDataPart(key, mObject.toString())
                        }
                    }
                    //                    Log.e("OkhttpUtil","添加数组"+new Gson().toJson(value));
                } else {
                    //如果请求的值是string类型
                    builder1.addFormDataPart(key, value.toString())
                }
            }
        }
        val request = Request.Builder()
            .url(address.toString())
        if (map != null) request.put(builder1.build()) else request.put(Util.EMPTY_REQUEST)

//        Log.e("OkhttpUtil","post请求："+builder1.build().toString());
        if (!TextUtils.isEmpty(cookie)) {
            request.addHeader("cookie", cookie.toString())
        }
        client.build().newCall(request.build()).enqueue(callback)
    }


    operator fun get(
        address: String,
        map: Map<String?, Any>? = null,
        cookie: String? = null,
        callback: Callback
    ) {
        val client = OkHttpClient()
        val httpBuilder = HttpUrl.parse(address)!!.newBuilder()
        if (map != null) {
            for ((key, value) in map) {
                httpBuilder.addQueryParameter(key.toString(), value.toString())
            }
        }
        val request = Request.Builder()
            .url(httpBuilder.build())
        Log.e("OkhttpUtil", "为" + httpBuilder.build().toString())
        if (!TextUtils.isEmpty(cookie)) {
            request.addHeader("cookie", cookie.toString())
        }
        client.newCall(request.build()).enqueue(callback)
    }

    fun del(address: String, map: Map<String?, Any>?, cookie: String?, callback: Callback) {
        val client = OkHttpClient()
        val httpBuilder = HttpUrl.parse(address)!!.newBuilder()
        if (map != null) {
            for ((key, value) in map) {
                httpBuilder.addQueryParameter(key.toString(), value.toString())
            }
        }
        val request = Request.Builder()
            .delete()
            .url(httpBuilder.build())
        Log.e("OkhttpUtil", "删除为" + httpBuilder.build().toString())
        if (!TextUtils.isEmpty(cookie)) {
            request.addHeader("cookie", cookie.toString())
        }
        client.newCall(request.build()).enqueue(callback)
    }

    fun getSession(response: Response): String? {
        val headers = response.headers() //response为okhttp请求后的响应
        val cookies = headers.values("Set-Cookie")
        if (cookies.size != 0) {
            val session = cookies[0]
            return session.substring(0, session.indexOf(";"))
        }
        return null
    }

    fun post(url: String, json: String, callback: Callback) {
        post(url, json, null, callback)
    }

    fun post(url: String, json: String, cookie: String?, callback: Callback) {
        val okHttpClient = OkHttpClient()
        val requestBody = FormBody.create(
            MediaType.parse("application/json; charset=utf-8"), json
        )
        val request = Request.Builder()
            .url(url) //请求的url
        if (TextUtils.isEmpty(json)) request.post(Util.EMPTY_REQUEST) else request.post(requestBody)
        if (!TextUtils.isEmpty(cookie)) {
            request.addHeader("cookie", cookie.toString())
        }
        okHttpClient.newCall(request.build()).enqueue(callback)
    }

    fun cancelTag(tag: Any?) {
        for (call in okHttpClient.dispatcher().queuedCalls()) {
            if (tag == call.request().tag()) {
                call.cancel()
            }
        }
        for (call in okHttpClient.dispatcher().runningCalls()) {
            if (tag == call.request().tag()) {
                call.cancel()
            }
        }
    }

    fun postJson(url: String, json: String, callback: Callback) {
        postJson(url, json, null, null, null, 0, callback)
    }

    fun postJson(
        url: String,
        json: String,
        cookie: String?,
        oyHeader: OYHeader?,
        tag: String?,
        timeout: Int,
        callback: Callback
    ) {
        okHttpClient = OkHttpClient()
        if (timeout != 0) okHttpClient = okHttpClient.newBuilder()
            .connectTimeout(timeout.toLong(), TimeUnit.SECONDS) //设置连接超时时间
            .readTimeout(timeout.toLong(), TimeUnit.SECONDS) //设置读取超时时间
            .build()
        val requestBody = FormBody.create(
            MediaType.parse("application/json; charset=utf-8"), json
        )
        val request = Request.Builder()
            .url(url).apply {
                if (TextUtils.isEmpty(json)) post(Util.EMPTY_REQUEST) else post(requestBody)
                oyHeader?.let {
                    header(it.name, it.value)
                }
                if (!TextUtils.isEmpty(tag)) tag(tag)
                if (!TextUtils.isEmpty(cookie)) {
                    addHeader("cookie", cookie.toString())
                }
            }
        Log.e("OkhttpUtil", json + " 状态 " + request.build().toString())
        okHttpClient.newCall(request.build()).enqueue(callback)
    }
}