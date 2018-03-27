package cn.garymb.ygomobile.ui.plus;

import android.webkit.WebView;
import android.webkit.WebViewClient;

public class DefWebViewClient extends WebViewClient {
    @Override
    public boolean shouldOverrideUrlLoading(WebView view, String url) {
        return false;
    }
}
