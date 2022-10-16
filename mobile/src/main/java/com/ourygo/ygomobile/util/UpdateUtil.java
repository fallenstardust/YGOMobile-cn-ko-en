package com.ourygo.ygomobile.util;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;

import com.feihua.dialogutils.util.DialogUtils;
import com.google.gson.Gson;
import com.ourygo.ygomobile.OYApplication;
import com.ourygo.ygomobile.base.listener.OnUpdateListener;
import com.ourygo.ygomobile.bean.OYResult;
import com.ourygo.ygomobile.bean.UpdateInfo;

import android.app.Activity;
import android.content.Context;
import android.text.TextUtils;
import cn.garymb.ygomobile.utils.SystemUtils;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

/**
 * Create By feihua  On 2022/10/15
 */
public class UpdateUtil {

    public static void checkUpdate(Activity activity, boolean noUpdateToast) {
        if (noUpdateToast)
            DialogUtils.getInstance(activity).dialogj1(null,"检查更新中");
        findUpdateInfo(new OnUpdateListener() {
            @Override
            public void onUpdate(UpdateInfo updateInfo, String exception) {
                activity.runOnUiThread(() -> {
                    if (noUpdateToast)
                        DialogUtils.getInstance(activity).dis();
                    if (!TextUtils.isEmpty(exception)) {
                        if (noUpdateToast) {
                            OYUtil.show("检查更新失败，原因为：" + exception);
                            return;
                        }
                    }

                    if (updateInfo == null) {
                        if (noUpdateToast)
                            OYUtil.show("喵已经是最新版本了~");
                        return;
                    }
                    OYDialogUtil.dialogUpdate(activity,updateInfo);
                });
            }
        });
    }

    private static void findUpdateInfo(OnUpdateListener onUpdateListener) {
        Map<String, Object> map = new HashMap<>();
        map.put(Record.ARG_NAME, "ymoy");
        map.put(Record.ARG_VERSION, SystemUtils.getVersion(OYApplication.get()));
        OkhttpUtil.post(Record.URL_UPDATE_APP, map, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                onUpdateListener.onUpdate(null, e.toString());
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String json = response.body().string();
                try {
                    JSONObject jsonObject = new JSONObject(json);
                    OYResult oyResult = new OYResult(jsonObject);
                    if (oyResult.isException()) {
                        onUpdateListener.onUpdate(null, oyResult.getException());
                        return;
                    }
                    if (oyResult.getData()==null){
                        onUpdateListener.onUpdate(null,null);
                        return;
                    }
                    onUpdateListener.onUpdate(
                            new Gson().fromJson(oyResult.getData().toString(), UpdateInfo.class), null);
                } catch (JSONException e) {
                    onUpdateListener.onUpdate(null, e.toString());
                }
            }
        });
    }

}
