package com.ourygo.ygomobile.util;

import android.util.Log;

import com.tencent.smtt.sdk.QbSdk;

import cn.garymb.ygomobile.App;

public class SdkInitUtil {
    private static final SdkInitUtil ourInstance = new SdkInitUtil();

    public static SdkInitUtil getInstance() {
        return ourInstance;
    }

    private SdkInitUtil() {
    }


    private boolean isInitX5WebView=false;

    public void initX5WebView() {
        if (!isInitX5WebView) {
            QbSdk.PreInitCallback cb = new QbSdk.PreInitCallback() {
                @Override
                public void onViewInitFinished(boolean arg0) {
                    //x5內核初始化完成的回调，为true表示x5内核加载成功，否则表示x5内核加载失败，会自动切换到系统内核。
//                    Log.e("SdkInitUtil", "加载情况" + arg0);
                    isInitX5WebView=arg0;
                    //  Toast.makeText(getActivity(), "加载成功", Toast.LENGTH_LONG).show();
                }

                @Override
                public void onCoreInitFinished() {
                }
            };
            //x5内核初始化接口
            QbSdk.initX5Environment(App.get(), cb);
        }
    }
}
