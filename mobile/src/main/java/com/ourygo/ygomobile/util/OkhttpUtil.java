package com.ourygo.ygomobile.util;

import android.text.TextUtils;

import java.io.File;
import java.util.Map;

import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

public class OkhttpUtil 
{
	
	public static void post(String address, Map<String,Object> map, Callback callback) {
        OkHttpClient.Builder client = new OkHttpClient.Builder();
		
		MultipartBody.Builder builder1 = new MultipartBody.Builder();
		if (map != null) {
			builder1.setType(MultipartBody.FORM);
			for (Map.Entry<String, Object> stringObjectEntry : map.entrySet()) {
				String key = stringObjectEntry.getKey();
				Object value = stringObjectEntry.getValue();
				if (value instanceof File) {//如果请求的值是文件
					File file = (File) value;
					
					//MediaType.parse("application/octet-stream")以二进制的形式上传文件
					builder1.addFormDataPart(key, ((File) value).getName(),
											 RequestBody.create(MediaType.parse("multipart/form-data"), file));
				} else {
					//如果请求的值是string类型
					builder1.addFormDataPart(key, value.toString());
				}
			}
		}
        Request.Builder request = new Request.Builder()
			.url(address)
			.post(builder1.build());
		String cookie=SharedPreferenceUtil.getHttpSessionId();
		if(!TextUtils.isEmpty(cookie)){
			request.addHeader("cookie",cookie);
		}
		
        client.build().newCall(request.build()).enqueue(callback);
    }
	
	public static void get(String address, Callback callback)
    {
        OkHttpClient client = new OkHttpClient();
        
        Request.Builder request = new Request.Builder()
			.url(address);
		String cookie=SharedPreferenceUtil.getHttpSessionId();
		if(!TextUtils.isEmpty(cookie)){
			request.addHeader("cookie",cookie);
		}
        client.newCall(request.build()).enqueue(callback);
    }
}
