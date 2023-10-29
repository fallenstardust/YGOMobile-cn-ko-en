package cn.garymb.ygomobile.ui.activities;

import static cn.garymb.ygomobile.Constants.URL_YGO233_ADVANCE;
import static cn.garymb.ygomobile.Constants.URL_YGO233_FILE;
import static cn.garymb.ygomobile.Constants.URL_YGO233_FILE_ALT;
import static cn.garymb.ygomobile.utils.DownloadUtil.TYPE_DOWNLOAD_EXCEPTION;
import static cn.garymb.ygomobile.utils.ServerUtil.AddServer;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;

import com.tencent.smtt.sdk.ValueCallback;
import com.tencent.smtt.sdk.WebChromeClient;
import com.tencent.smtt.sdk.WebView;

import java.io.File;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.file.FileActivity;
import cn.garymb.ygomobile.ui.file.FileOpenType;
import cn.garymb.ygomobile.ui.home.MainActivity;
import cn.garymb.ygomobile.ui.mycard.MyCard;
import cn.garymb.ygomobile.ui.plus.DefWebChromeClient;
import cn.garymb.ygomobile.ui.widget.WebViewPlus;
import cn.garymb.ygomobile.utils.DownloadUtil;
import cn.garymb.ygomobile.utils.FileUtils;
import cn.garymb.ygomobile.utils.LogUtil;
import cn.garymb.ygomobile.utils.SharedPreferenceUtil;
import cn.garymb.ygomobile.utils.UnzipUtils;
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
    private Button btn_download;
    private int FailedCount;
    @SuppressLint("HandlerLeak")
    Handler handler = new Handler() {

        @Override
        public void handleMessage(@NonNull Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case DownloadUtil.TYPE_DOWNLOAD_ING:
                    btn_download.setText(msg.arg1 + "%");
                    break;
                case DownloadUtil.TYPE_DOWNLOAD_EXCEPTION:
                    ++FailedCount;
                    if (FailedCount <= 2) {
                        Toast.makeText(getActivity(), R.string.Ask_to_Change_Other_Way, Toast.LENGTH_SHORT).show();
                        downloadfromWeb(URL_YGO233_FILE_ALT);
                    }
                    YGOUtil.showTextToast("error" + msg.obj);
                    break;
                case UnzipUtils.ZIP_READY:
                    btn_download.setText(R.string.title_use_ex);
                    break;
                case UnzipUtils.ZIP_UNZIP_OK:
                    if (!AppsSettings.get().isReadExpansions()) {
                        Intent startSetting = new Intent(getContext(), MainActivity.class);
                        startSetting.putExtra("flag", 4);
                        startActivity(startSetting);
                        Toast.makeText(getContext(), R.string.ypk_go_setting, Toast.LENGTH_LONG).show();
                    } else {
                        DataManager.get().load(true);
                        Toast.makeText(getContext(), R.string.ypk_installed, Toast.LENGTH_LONG).show();
                    }
                    String servername = "";
                    if (AppsSettings.get().getDataLanguage() == AppsSettings.languageEnum.Chinese.code)
                        servername = "23333先行服务器";
                    if (AppsSettings.get().getDataLanguage() == AppsSettings.languageEnum.Korean.code)
                        servername = "YGOPRO 사전 게시 중국서버";
                    if (AppsSettings.get().getDataLanguage() == AppsSettings.languageEnum.English.code)
                        servername = "Mercury23333 OCG/TCG Pre-release";
                    if (AppsSettings.get().getDataLanguage() == AppsSettings.languageEnum.Spanish.code)
                        servername = "Mercury23333 OCG/TCG Pre-release";
                    AddServer(getActivity(), servername, "s1.ygo233.com", 23333, "Knight of Hanoi");
                    btn_download.setVisibility(View.GONE);
                    SharedPreferenceUtil.setExpansionDataVer(WebActivity.exCardVer);
                    break;
                case UnzipUtils.ZIP_UNZIP_EXCEPTION:
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
        if (doIntent(getIntent())) {
            mWebViewPlus.loadUrl(mUrl);
            if (mUrl.startsWith(URL_YGO233_ADVANCE)) {
                btn_download.setVisibility(View.VISIBLE);
            } else {
                btn_download.setVisibility(View.GONE);
            }
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
            case R.id.web_btn_download_prerelease:
                downloadfromWeb(URL_YGO233_FILE);
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

        btn_download = $(R.id.web_btn_download_prerelease);
        btn_download.setOnClickListener(this);
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

    private void downloadfromWeb(String fileUrl) {
        File file = new File(AppsSettings.get().getResourcePath() + "-preRlease.zip");
        if (file.exists()) {
            FileUtils.deleteFile(file);
        }
        DownloadUtil.get().download(fileUrl, file.getParent(), file.getName(), new DownloadUtil.OnDownloadListener() {
            @Override
            public void onDownloadSuccess(File file) {
                Message message = new Message();
                message.what = UnzipUtils.ZIP_READY;
                try {
                    File ydks = new File(AppsSettings.get().getDeckDir());
                    File[] subYdks = ydks.listFiles();
                    for (File files : subYdks) {
                        if (files.getName().contains("-") && files.getName().contains(" new cards"))
                            files.delete();
                    }
                    UnzipUtils.upZipSelectFile(file, AppsSettings.get().getResourcePath(), ".ypk");
                } catch (Exception e) {
                    message.what = UnzipUtils.ZIP_UNZIP_EXCEPTION;
                } finally {
                    message.what = UnzipUtils.ZIP_UNZIP_OK;
                }
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
                message.obj = e.toString();
                handler.sendMessage(message);
            }
        });

    }
}
