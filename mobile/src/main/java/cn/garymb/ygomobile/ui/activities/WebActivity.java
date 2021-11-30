package cn.garymb.ygomobile.ui.activities;

import static cn.garymb.ygomobile.Constants.URL_YGO233_ADVANCE;
import static cn.garymb.ygomobile.Constants.URL_YGO233_FILE;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;

import java.io.File;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.preference.SettingsActivity;
import cn.garymb.ygomobile.ui.widget.WebViewPlus;
import cn.garymb.ygomobile.utils.DownloadUtil;
import cn.garymb.ygomobile.utils.FileUtils;
import cn.garymb.ygomobile.utils.UnzipUtils;
import cn.garymb.ygomobile.utils.YGOUtil;
import ocgcore.data.Card;

public class WebActivity extends BaseActivity {
    private WebViewPlus mWebViewPlus;
    private String mUrl;
    private String mTitle;
    private Button btn_download;
    private static final int TYPE_DOWNLOAD_EXCEPTION = 1;
    private static final int TYPE_DOWNLOAD_ING = 2;
    private static final int ZIP_READY = 600;
    private static final int ZIP_UPDATE_PATH_PROGRESS = 601;
    private static final int ZIP_UNZIP_OK = 602;
    private static final int ZIP_UNZIP_EXCEPTION = 603;
    @SuppressLint("HandlerLeak")
    Handler handler = new Handler() {

        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case TYPE_DOWNLOAD_ING:
                    btn_download.setText(msg.arg1 + "%");
                    break;
                case TYPE_DOWNLOAD_EXCEPTION:
                    YGOUtil.show("error" + msg.obj);
                    break;
                case ZIP_READY:
                    btn_download.setText(R.string.title_use_ex);
                    break;
                case ZIP_UPDATE_PATH_PROGRESS:
                    btn_download.setText(msg.obj.toString());
                    break;
                case ZIP_UNZIP_OK:
                    Intent startSetting = new Intent(getContext(), SettingsActivity.class);
                    startActivity(startSetting);
                    Toast.makeText(getContext(), R.string.ypk_go_setting, Toast.LENGTH_LONG).show();
                    break;
                case ZIP_UNZIP_EXCEPTION:
                    Toast.makeText(getContext(), getString(R.string.install_failed_bcos) + msg.obj, Toast.LENGTH_SHORT).show();
                    break;

            }
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_webbrowser);
        final Toolbar toolbar = $(R.id.toolbar);
        setSupportActionBar(toolbar);
        enableBackHome();
        mWebViewPlus = $(R.id.webbrowser);
        initButton();
        /*mWebViewPlus.enableHtml5();
        mWebViewPlus.setWebChromeClient(new DefWebChromeClient() {
            @Override
            public void onReceivedTitle(WebView view, String title) {
                super.onReceivedTitle(view, title);
                if (toolbar != null) {
                    toolbar.setSubtitle(title);
                } else {
                    setTitle(title);
                }
            }
        });*/
        if (doIntent(getIntent())) {
            mWebViewPlus.loadUrl(mUrl);
            if (mUrl.equals(URL_YGO233_ADVANCE)) {
                btn_download.setVisibility(View.VISIBLE);
            } else {
                btn_download.setVisibility(View.GONE);
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (doIntent(intent)) {
            mWebViewPlus.loadUrl(mUrl);
        }
    }

    private boolean doIntent(Intent intent) {
        if (intent.hasExtra(Intent.EXTRA_TEXT)) {
            mTitle = intent.getStringExtra(Intent.EXTRA_TEXT);
            setTitle(mTitle);
        }
        if (intent.hasExtra(Intent.EXTRA_STREAM)) {
            mUrl = intent.getStringExtra(Intent.EXTRA_STREAM);
            return true;
        }
        return false;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackHome();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onBackHome() {
        if (mWebViewPlus.canGoBack()) {
            mWebViewPlus.goBack();
        } else {
            finish();
        }
    }

    @Override
    public void onBackPressed() {
        if (mWebViewPlus.canGoBack()) {
            mWebViewPlus.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onResume() {
        mWebViewPlus.resumeTimers();
        //mWebViewPlus.onShow();
        super.onResume();
    }

    @Override
    protected void onPause() {
        mWebViewPlus.pauseTimers();
        //mWebViewPlus.onHide();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        mWebViewPlus.stopLoading();
        mWebViewPlus.setWebChromeClient(null);
        mWebViewPlus.setWebViewClient(null);
        //mWebViewPlus.onDestroy();
        super.onDestroy();
    }

    public static void open(Context context, String title, String url) {
        Intent intent = new Intent(context, WebActivity.class);
        intent.putExtra(Intent.EXTRA_STREAM, url);
        intent.putExtra(Intent.EXTRA_TEXT, title);
        context.startActivity(intent);
    }

    public static void openFAQ(Context context, Card cardInfo) {
        String uri = Constants.WIKI_SEARCH_URL + String.format("%08d", cardInfo.getCode()) + "#faq";
        WebActivity.open(context, cardInfo.Name, uri);
    }

    public void initButton() {
        btn_download = $(R.id.web_btn_download_prerelease);
        btn_download.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                downloadfromWeb();
            }
        });
    }

    private void downloadfromWeb() {
        File file = new File(AppsSettings.get().getResourcePath() + ".zip");
        if (file.exists()) {
            FileUtils.deleteFile(file);
        }
        DownloadUtil.get().download(URL_YGO233_FILE, file.getParent(), file.getName(), new DownloadUtil.OnDownloadListener() {
            @Override
            public void onDownloadSuccess(File file) {
                Message message = new Message();
                message.what = ZIP_READY;
                try {
                    UnzipUtils.unZipFolder(file.toString(), AppsSettings.get().getResourcePath());
                } catch (Exception e) {
                    message.what = ZIP_UNZIP_EXCEPTION;
                }
                handler.sendMessage(message);
            }


            @Override
            public void onDownloading(int progress) {
                Message message = new Message();
                message.what = TYPE_DOWNLOAD_ING;
                message.arg1 = progress;
                handler.sendMessage(message);
            }

            @Override
            public void onDownloadFailed(Exception e) {
                //下载失败后删除下载的文件
                FileUtils.deleteFile(file);
//                downloadCardImage(code, file);
                Message message = new Message();
                message.what = TYPE_DOWNLOAD_EXCEPTION;
                message.obj = e.toString();
                handler.sendMessage(message);
            }
        });

    }
}
