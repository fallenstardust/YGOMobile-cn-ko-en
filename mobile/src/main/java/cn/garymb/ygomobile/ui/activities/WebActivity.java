package cn.garymb.ygomobile.ui.activities;

import static cn.garymb.ygomobile.utils.DownloadUtil.TYPE_DOWNLOAD_EXCEPTION;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;

import com.tencent.smtt.sdk.DownloadListener;
import com.tencent.smtt.sdk.ValueCallback;
import com.tencent.smtt.sdk.WebChromeClient;
import com.tencent.smtt.sdk.WebView;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.file.FileActivity;
import cn.garymb.ygomobile.ui.file.FileOpenType;
import cn.garymb.ygomobile.ui.mycard.MyCard;
import cn.garymb.ygomobile.ui.plus.DefWebChromeClient;
import cn.garymb.ygomobile.ui.plus.DefWebViewClient;
import cn.garymb.ygomobile.ui.widget.WebViewPlus;
import cn.garymb.ygomobile.utils.DownloadUtil;
import cn.garymb.ygomobile.utils.FileUtils;
import cn.garymb.ygomobile.utils.LogUtil;
import cn.garymb.ygomobile.utils.YGOUtil;
import ocgcore.DataManager;
import ocgcore.data.Card;

public class WebActivity extends BaseActivity implements View.OnClickListener {
    private static String TAG = "WebActivity";
    private static final int FILE_CHOOSER_REQUEST = 100;
    private ValueCallback<Uri[]> mFilePathCallback;
    /* 全局存储了扩展卡版本号，会被其他activity使用 */
    private static String exCardVer;
    private WebViewPlus mWebViewPlus;
    private String mUrl;
    private String mTitle;
    private ImageButton btn_context_search;
    private LinearLayout find_in_page;
    private EditText et_context_keyword;
    private ImageButton btn_context_search_close, btn_context_search_last, btn_context_search_next;

    @SuppressLint("HandlerLeak")
    Handler handler = new Handler() {

        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case DownloadUtil.TYPE_DOWNLOAD_ING:
                    break;
                case DownloadUtil.TYPE_DOWNLOAD_EXCEPTION:
                    YGOUtil.showTextToast(getString(R.string.tip_download_failed));
                    break;
                case DownloadUtil.TYPE_DOWNLOAD_OK:
                    if (msg.obj.toString().endsWith(Constants.YDK_FILE_EX)) {
                        YGOUtil.showTextToast(Gravity.TOP, getString(R.string.tip_download_OK) + getString(R.string.deck_list), Toast.LENGTH_SHORT);
                    } else if (msg.obj.toString().endsWith(Constants.YRP_FILE_EX)) {
                        YGOUtil.showTextToast(Gravity.TOP, getString(R.string.tip_download_OK) + getString(R.string.replay_list), Toast.LENGTH_SHORT);
                    } else if (msg.obj.toString().endsWith(Constants.YPK_FILE_EX) || msg.obj.toString().endsWith(Constants.CORE_LIMIT_PATH)) {
                        YGOUtil.showTextToast(Gravity.TOP, getString(R.string.ypk_installed) + getString(R.string.restart_app), Toast.LENGTH_SHORT);
                        DataManager.get().load(true);
                    } else {
                        YGOUtil.showTextToast(Gravity.TOP, getString(R.string.tip_download_OK) + AppsSettings.get().getResourcePath(), Toast.LENGTH_LONG);
                    }
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
        find_in_page = $(R.id.find_in_page);
        et_context_keyword = $(R.id.context_keyword);
        initButton();
        //mWebViewPlus.enableHtml5();
        mWebViewPlus.setWebChromeClient(new DefWebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, WebChromeClient.FileChooserParams fileChooserParams) {
                LogUtil.i(TAG, "openFileChooser: " + fileChooserParams.getMode());
                mFilePathCallback = filePathCallback;
                openFileChooseProcess(fileChooserParams.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE);
                return true;
            }
        });
        mWebViewPlus.setWebViewClient(new DefWebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return super.shouldOverrideUrlLoading(view, url);
            }
        });
        if (doIntent(getIntent())) {
            mWebViewPlus.loadUrl(mUrl);
        }
        TextView.OnEditorActionListener searchListener = (v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                InputMethodManager imm = (InputMethodManager) this.getSystemService(Context.INPUT_METHOD_SERVICE);
                imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                mWebViewPlus.findAllAsync(et_context_keyword.getText().toString());
                return true;
            }
            return false;
        };
        et_context_keyword.setOnEditorActionListener(searchListener);
        //设置网页下载监听
        mWebViewPlus.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {
                String fileName = "";
                try {
                    //从contentDisposition获取文件名并转换urlcode为UTF-8
                    fileName = URLDecoder.decode(contentDisposition.substring(contentDisposition.lastIndexOf("''") + 2), "UTF-8");
                } catch (UnsupportedEncodingException e) {
                    throw new RuntimeException(e);
                }
                String destFileDir = "";
                if (fileName.endsWith(Constants.YDK_FILE_EX)) {
                    destFileDir = AppsSettings.get().getDeckDir();
                } else if (fileName.endsWith(Constants.YRP_FILE_EX)) {
                    destFileDir = AppsSettings.get().getReplayDir();
                } else if (fileName.endsWith(Constants.CORE_LIMIT_PATH) || fileName.endsWith(Constants.YPK_FILE_EX)) {
                    destFileDir = AppsSettings.get().getExpansionsPath().getPath();
                } else if (fileName.endsWith(Constants.LUA_FILE_EX)) {
                    destFileDir = AppsSettings.get().getSingleDir();
                } else {//萌卡还有些什么文件格式后续可以添加
                    destFileDir = AppsSettings.get().getResourcePath();
                }

                File file = new File(destFileDir + "/" + fileName);
                DownloadUtil.get().download(url, destFileDir, file.getName(), new DownloadUtil.OnDownloadListener() {
                    @Override
                    public void onDownloadSuccess(File file) {
                        Message message = new Message();
                        message.what = DownloadUtil.TYPE_DOWNLOAD_OK;
                        message.obj = file.getName();
                        handler.sendMessage(message);
                    }


                    @Override
                    public void onDownloading(int progress) {
                        Message message = new Message();
                        message.what = DownloadUtil.TYPE_DOWNLOAD_ING;
                        message.arg1 = progress;
                        handler.sendMessage(message);
                    }

                    @Override
                    public void onDownloadFailed(Exception e) {
                        //下载失败后删除下载的文件
                        FileUtils.deleteFile(file);
                        Message message = new Message();
                        message.what = TYPE_DOWNLOAD_EXCEPTION;
                        handler.sendMessage(message);
                    }
                });
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILE_CHOOSER_REQUEST) {
            if (resultCode == RESULT_OK) {
                if (mFilePathCallback != null) {
                    if (data != null && data.getClipData() != null) {
                        //有选择多个文件
                        int count = data.getClipData().getItemCount();
                        LogUtil.i(TAG, "url count ：  " + count);
                        Uri[] uris = new Uri[count];
                        int currentItem = 0;
                        while (currentItem < count) {
                            Uri fileUri = data.getClipData().getItemAt(currentItem).getUri();
                            uris[currentItem] = fileUri;
                            currentItem = currentItem + 1;
                        }
                        mFilePathCallback.onReceiveValue(uris);
                    } else {
                        Uri result = data == null ? null : data.getData();
                        LogUtil.e(TAG, "" + result);
                        mFilePathCallback.onReceiveValue(new Uri[]{result});
                    }
                    mFilePathCallback = null;
                }
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
        find_in_page.setVisibility(View.GONE);
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

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.web_text_search:
                find_in_page.setVisibility(View.VISIBLE);
                break;
            case R.id.ib_last:
                mWebViewPlus.findNext(false);//为false时表示上一项
                break;
            case R.id.ib_next:
                mWebViewPlus.findNext(true);//为true时表示下一项
                break;
            case R.id.web_text_search_close:
                find_in_page.setVisibility(View.GONE);
                et_context_keyword.getText().clear();//清除输入内容
                mWebViewPlus.clearMatches();//清除页面上的高亮项：
                break;

        }
    }

    public void initButton() {
        btn_context_search = $(R.id.web_text_search);
        btn_context_search.setOnClickListener(this);

        btn_context_search_last = $(R.id.ib_last);
        btn_context_search_last.setOnClickListener(this);

        btn_context_search_next = $(R.id.ib_next);
        btn_context_search_next.setOnClickListener(this);

        btn_context_search_close = $(R.id.web_text_search_close);
        btn_context_search_close.setOnClickListener(this);
    }

    public static void open(Context context, String title, String url) {
        Intent intent = new Intent(context, WebActivity.class);
        intent.putExtra(Intent.EXTRA_STREAM, url);
        intent.putExtra(Intent.EXTRA_TEXT, title);
        context.startActivity(intent);
    }

    private void openFileChooseProcess(boolean isMulti) {
        LogUtil.e(TAG, mWebViewPlus.getUrl());

        if (mWebViewPlus.getUrl().contains(MyCard.mCommunityReportUrl)) {
            Intent intent = FileActivity.getIntent(getActivity(), getString(R.string.dialog_select_file), null, AppsSettings.get().getReplayDir(), false, FileOpenType.SelectFile);
            startActivityForResult(intent, FILE_CHOOSER_REQUEST);
        } else if (mWebViewPlus.getUrl().equals(MyCard.mCompetitionUrl)) {
            Intent intent = FileActivity.getIntent(getActivity(), getString(R.string.dialog_select_file), null, AppsSettings.get().getDeckDir(), false, FileOpenType.SelectFile);
            startActivityForResult(intent, FILE_CHOOSER_REQUEST);
        } else {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.addCategory(Intent.CATEGORY_DEFAULT);
            intent.setType("*/*");
            if (isMulti) {
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            }
            startActivityForResult(Intent.createChooser(intent, getString(R.string.dialog_select_file)), FILE_CHOOSER_REQUEST);
        }

    }

    public static void openFAQ(Context context, Card cardInfo) {
        String uri = Constants.WIKI_SEARCH_URL + String.format("%08d", cardInfo.getCode()) + "#faq";
        WebActivity.open(context, cardInfo.Name, uri);
    }
}
