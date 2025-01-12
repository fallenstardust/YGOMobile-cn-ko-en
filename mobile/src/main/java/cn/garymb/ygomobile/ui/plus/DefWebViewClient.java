package cn.garymb.ygomobile.ui.plus;

import com.ourygo.lib.duelassistant.util.DARecord;
import com.ourygo.lib.duelassistant.util.DuelAssistantManagement;
import com.tencent.smtt.sdk.WebView;
import com.tencent.smtt.sdk.WebViewClient;

public class DefWebViewClient extends WebViewClient {
    private static final int CHECK_ID_WEB_VIEW = 100;
    @Override
    public boolean shouldOverrideUrlLoading(WebView view, String url) {
        if (url.startsWith(DARecord.DECK_URL_PREFIX)) {
            DuelAssistantManagement.getInstance().deckCheck(url, CHECK_ID_WEB_VIEW);
            return true;
        }
        return false;
    }
}
