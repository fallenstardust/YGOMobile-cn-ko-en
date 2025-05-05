package cn.garymb.ygomobile.ui.plus;

import android.content.Intent;
import android.net.Uri;

import com.ourygo.lib.duelassistant.util.DARecord;
import com.ourygo.lib.duelassistant.util.DuelAssistantManagement;
import com.tencent.smtt.sdk.WebView;
import com.tencent.smtt.sdk.WebViewClient;

public class DefWebViewClient extends WebViewClient {
    public static final int CHECK_ID_WEB_VIEW = 100;
    public static final int CHECK_ID_WEB_VIEW_NEW_ACTIVITY = 101;

    private final int daCheckId;

    public DefWebViewClient() {
        this(CHECK_ID_WEB_VIEW);
    }

    public DefWebViewClient(int daCheckId) {
        super();
        this.daCheckId = daCheckId;
    }

    @Override
    public boolean shouldOverrideUrlLoading(WebView view, String url) {
        if (url.startsWith(DARecord.DECK_URL_PREFIX)) {
            DuelAssistantManagement.getInstance().deckCheck(url, daCheckId);
            return true;
        }
        if (!url.startsWith("http://")
                && !url.startsWith("https://")) {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(Uri.parse(url));
            view.getContext().startActivity(intent);
            return true;
        }
        return false;
    }
}
