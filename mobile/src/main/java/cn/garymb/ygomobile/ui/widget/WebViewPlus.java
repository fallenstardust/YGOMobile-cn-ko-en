package cn.garymb.ygomobile.ui.widget;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.AttributeSet;
import android.webkit.JavascriptInterface;

import cn.garymb.ygomobile.ui.mycard.X5WebView;

public class WebViewPlus extends X5WebView {
    public WebViewPlus(Context context) {
        super(context);
    }

    public WebViewPlus(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * 版本号 JavaScript 接口
     */
    public void addVersionJavaScriptInterface() {
        addJavascriptInterface(new VersionJavaScriptInterface(getContext()), "AppInterface");
    }

    /**
     * 提供版本号的 JavaScript 接口类
     */
    private static class VersionJavaScriptInterface {
        private Context mContext;

        VersionJavaScriptInterface(Context context) {
            mContext = context;
        }

        @JavascriptInterface
        public String getVersionName() {
            try {
                PackageManager packageManager = mContext.getPackageManager();
                PackageInfo packageInfo = packageManager.getPackageInfo(mContext.getPackageName(), 0);
                return packageInfo.versionName;
            } catch (PackageManager.NameNotFoundException e) {
                return "Unknown";
            }
        }
    }
}
