package cn.garymb.ygomobile.ui.plus;

import android.util.Log;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;

import cn.garymb.ygomobile.lite.BuildConfig;

public class DefWebChromeClient extends WebChromeClient {
    @Override
    public void onConsoleMessage(String message, int lineNumber, String sourceID) {
        super.onConsoleMessage(message, lineNumber, sourceID);
        if (BuildConfig.DEBUG)
            Log.i("webview", sourceID + ":" + lineNumber + "\n" + message);
    }

    @Override
    public boolean onConsoleMessage(ConsoleMessage consoleMessage) {
        if (BuildConfig.DEBUG)
            Log.i("webview", consoleMessage.sourceId() + ":" + consoleMessage.lineNumber() + "\n" + consoleMessage.message());
        return true;
    }
}
