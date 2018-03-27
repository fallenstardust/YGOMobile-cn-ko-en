package cn.garymb.ygomobile.ui.plus;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.util.AttributeSet;
import android.util.Log;
import android.webkit.GeolocationPermissions;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.xwalk.core.XWalkDownloadListener;
import org.xwalk.core.XWalkNavigationItem;
import org.xwalk.core.XWalkPreferences;
import org.xwalk.core.XWalkResourceClient;
import org.xwalk.core.XWalkUIClient;
import org.xwalk.core.XWalkView;

import cn.garymb.ygomobile.lite.BuildConfig;

public class XWebView extends XWalkView {
    static {
        XWalkPreferences.setValue(XWalkPreferences.REMOTE_DEBUGGING, BuildConfig.DEBUG);
        XWalkPreferences.setValue(XWalkPreferences.ENABLE_THEME_COLOR, true);
        XWalkPreferences.setValue(XWalkPreferences.ANIMATABLE_XWALK_VIEW, true);
        XWalkPreferences.setValue(XWalkPreferences.ALLOW_UNIVERSAL_ACCESS_FROM_FILE, true);
    }

    public XWebView(Context context) {
        super(context);
    }

    public XWebView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public XWebView(Context context, Activity activity) {
        super(context, activity);
    }

    public void setWebViewClient(WebViewClient webViewClient) {
        if (webViewClient == null) return;
        setResourceClient(new XWalkResourceClient(this) {
            @Override
            public void onLoadStarted(XWalkView view, String url) {
                super.onLoadStarted(view, url);
                webViewClient.onPageStarted(null, url, null);
            }

            @Override
            public void onLoadFinished(XWalkView view, String url) {
                super.onLoadFinished(view, url);
                webViewClient.onPageFinished(null, url);
            }

            @Override
            public boolean shouldOverrideUrlLoading(XWalkView view, String url) {
                return webViewClient.shouldOverrideUrlLoading(null, url);
            }

        });
    }

    public void setWebChromeClient(final WebChromeClient webChromeClient) {
        if (webChromeClient == null) return;
        setUIClient(new XWalkUIClient(this) {
            @Override
            public boolean onConsoleMessage(XWalkView view, String message, int lineNumber, String sourceId, ConsoleMessageType messageType) {
                if (BuildConfig.DEBUG) {
                    Log.i("webview", sourceId + ":" + lineNumber + "\n" + message);
                }
                return super.onConsoleMessage(view, message, lineNumber, sourceId, messageType);
            }

            @Override
            public void onReceivedTitle(XWalkView view, String title) {
                super.onReceivedTitle(view, title);
                webChromeClient.onReceivedTitle(null, title);
            }

        });
    }

    public boolean canGoBack() {
        return getNavigationHistory().canGoBack();
    }

    public void enableHtml5() {
        getSettings().setSaveFormData(true);
        getSettings().setDatabaseEnabled(true);
        getSettings().setDomStorageEnabled(true);

        // HTML5 configuration settings.
//        getSettings().setAppCacheMaxSize(Long.MAX_VALUE);
//        getSettings().setAppCachePath(context.getDir("appcache", Context.MODE_PRIVATE).getPath());
//        getSettings().setDatabasePath(context.getDir("databases", Context.MODE_PRIVATE).getPath());
//        getSettings().setGeolocationDatabasePath(context.getDir("geolocation", 0).getPath());

        getSettings().setJavaScriptCanOpenWindowsAutomatically(true);
        setScrollBarStyle(WebView.SCROLLBARS_OUTSIDE_OVERLAY);
        setHorizontalScrollBarEnabled(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ECLAIR_MR1) {
            GeolocationPermissions.getInstance();
        }
        getSettings().setAllowContentAccess(true);
        getSettings().setAllowFileAccess(true);

        setDownloadListener(new XWalkDownloadListener(getContext()) {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimetype,
                                        long contentLength) {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    getContext().startActivity(intent);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public void loadUrl(String url) {
        super.load(url, null);
    }

    public void loadData(String data, String mimeType, String encoding) {
        super.load(null, data);
    }

    public void goBack() {
        int index = getNavigationHistory().getCurrentIndex();
        if (getNavigationHistory().hasItemAt(index - 1)) {
            XWalkNavigationItem item = getNavigationHistory().getItemAt(index - 1);
//            item.getUrl();
            if (item != null) {
                loadUrl(item.getUrl());
            }
        }
    }
}
