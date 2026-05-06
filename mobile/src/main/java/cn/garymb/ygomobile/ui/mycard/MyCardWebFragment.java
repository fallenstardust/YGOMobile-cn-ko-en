package cn.garymb.ygomobile.ui.mycard;

import android.annotation.SuppressLint;
import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.tencent.smtt.sdk.WebChromeClient;
import com.tencent.smtt.sdk.WebSettings;
import com.tencent.smtt.sdk.WebView;

import java.io.File;
import java.text.MessageFormat;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.base.BaseFragemnt;
import cn.garymb.ygomobile.lite.BuildConfig;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.home.HomeActivity;
import cn.garymb.ygomobile.utils.YGOUtil;

/**
 * 通用Web页面Fragment，用于显示MyCard相关的网页内容
 * 支持进度条显示、文件下载等功能
 */
public class MyCardWebFragment extends BaseFragemnt {

    private static final String TAG = "MyCardWebFragment";
    private static final String ARG_URL = "url";
    private static final String ARG_TITLE = "title";

    private MyCardWebView mWebView;
    private ProgressBar mProgressBar;
    private ImageView mBackButton;
    private TextView mTitleText;
    private MyCard mMyCard;
    private HomeActivity homeActivity;

    private String mUrl;
    private String mTitle;

    /**
     * 创建MyCardWebFragment实例
     * @param url 要加载的URL
     * @param title 页面标题
     * @return MyCardWebFragment实例
     */
    public static MyCardWebFragment newInstance(String url, String title) {
        MyCardWebFragment fragment = new MyCardWebFragment();
        Bundle args = new Bundle();
        args.putString(ARG_URL, url);
        args.putString(ARG_TITLE, title);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            mUrl = getArguments().getString(ARG_URL);
            mTitle = getArguments().getString(ARG_TITLE);
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        homeActivity = (HomeActivity) getActivity();
        View view = inflater.inflate(R.layout.mycard_web_fragment, container, false);
        initView(view);
        return view;
    }

    private void initView(View view) {
        mMyCard = new MyCard(getActivity());
        mWebView = view.findViewById(R.id.wv_web);
        mProgressBar = view.findViewById(R.id.progressBar);
        mBackButton = view.findViewById(R.id.btn_back);
        mTitleText = view.findViewById(R.id.tv_title);

        // 设置标题
        if (!TextUtils.isEmpty(mTitle) && mTitleText != null) {
            mTitleText.setText(mTitle);
        }

        // 设置返回按钮点击事件
        if (mBackButton != null) {
            mBackButton.setOnClickListener(v -> {
                if (mWebView != null && mWebView.canGoBack()) {
                    mWebView.goBack();
                } else {
                    // 如果不能返回，则关闭当前Fragment
                    if (homeActivity != null) {
                        homeActivity.onBackPressed();
                    }
                }
            });
        }

        if (TextUtils.isEmpty(mUrl)) {
            YGOUtil.showTextToast("URL不能为空");
            if (homeActivity != null) {
                homeActivity.onBackPressed();
            }
            return;
        }

        // 配置WebView
        setupWebView();

        // 加载页面
        mWebView.loadUrl(mUrl);

        Log.d(TAG, "加载URL: " + mUrl);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings settings = mWebView.getSettings();

        // 启用JavaScript和DOM存储
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setSupportZoom(false);

        // 设置User-Agent
        settings.setUserAgentString(settings.getUserAgentString() + MessageFormat.format(
                " YGOMobile/{0} ({1} {2,number,#})",
                YGOUtil.s(R.string.app_name),
                BuildConfig.APPLICATION_ID,
                BuildConfig.VERSION_CODE
        ));

        // 设置WebViewClient
        mWebView.setWebViewClient(mMyCard.getWebViewClient());

        // 设置文件下载监听 - 使用系统DownloadManager
        mWebView.setDownloadListener((url, userAgent, contentDisposition, mimetype, contentLength) -> {
            Log.d(TAG, "检测到下载: " + url);
            handleFileDownload(url, contentDisposition, mimetype);
        });

        // 设置WebChromeClient处理进度条和标题
        mWebView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                if (mProgressBar != null) {
                    if (newProgress == 100) {
                        mProgressBar.setVisibility(View.GONE);
                    } else {
                        if (View.GONE == mProgressBar.getVisibility()) {
                            mProgressBar.setVisibility(View.VISIBLE);
                        }
                        mProgressBar.setProgress(newProgress);
                    }
                }
                super.onProgressChanged(view, newProgress);
            }

            @Override
            public void onReceivedTitle(WebView view, String title) {
                // 更新标题栏
                if (!TextUtils.isEmpty(title) && mTitleText != null) {
                    mTitleText.setText(title);
                }
                super.onReceivedTitle(view, title);
            }
        });
    }

    /**
     * 处理文件下载 - 使用系统DownloadManager
     * @param url 下载链接
     * @param contentDisposition Content-Disposition头
     * @param mimetype MIME类型
     */
    private void handleFileDownload(String url, String contentDisposition, String mimetype) {
        // 从URL或Content-Disposition中提取文件名
        String fileName = extractFileName(url, contentDisposition);
        String extension = getFileExtension(fileName).toLowerCase();

        Log.d(TAG, "下载文件: " + fileName + ", 扩展名: " + extension);

        // 根据扩展名确定保存路径
        String saveDir = getSaveDirectoryPath(extension);
        
        // 创建DownloadManager请求
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
        
        // 设置下载参数
        request.setTitle(fileName);
        request.setDescription("正在下载: " + fileName);
        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
        request.setAllowedOverRoaming(false);
        request.setMimeType(mimetype);
        
        // 设置保存路径
        request.setDestinationInExternalPublicDir(saveDir, fileName);
        
        // 允许媒体扫描
        request.allowScanningByMediaScanner();
        
        // 获取DownloadManager服务
        DownloadManager downloadManager = (DownloadManager) getActivity().getSystemService(Context.DOWNLOAD_SERVICE);
        
        if (downloadManager != null) {
            try {
                // 开始下载
                long downloadId = downloadManager.enqueue(request);
                Log.d(TAG, "下载任务已创建, ID: " + downloadId);
                
                // 显示提示信息
                String message = getSaveMessage(extension, fileName);
                YGOUtil.showTextToast(message);
            } catch (Exception e) {
                Log.e(TAG, "启动下载失败", e);
                YGOUtil.showTextToast("下载失败: " + e.getMessage());
            }
        } else {
            Log.e(TAG, "DownloadManager服务不可用");
            YGOUtil.showTextToast("下载服务不可用");
        }
    }

    /**
     * 从URL或Content-Disposition中提取文件名
     */
    private String extractFileName(String url, String contentDisposition) {
        String fileName = null;

        // 优先从Content-Disposition中提取
        if (!TextUtils.isEmpty(contentDisposition) && contentDisposition.contains("filename=")) {
            int index = contentDisposition.indexOf("filename=");
            fileName = contentDisposition.substring(index + 9).trim();
            // 去除引号
            fileName = fileName.replace("\"", "").replace("'", "");
        }

        // 如果Content-Disposition中没有，从URL中提取
        if (TextUtils.isEmpty(fileName)) {
            int lastSlash = url.lastIndexOf('/');
            if (lastSlash != -1 && lastSlash < url.length() - 1) {
                fileName = url.substring(lastSlash + 1);
                // 去除URL参数
                int questionMark = fileName.indexOf('?');
                if (questionMark != -1) {
                    fileName = fileName.substring(0, questionMark);
                }
            }
        }

        // 如果仍然没有文件名，使用默认名称
        if (TextUtils.isEmpty(fileName)) {
            fileName = "download_" + System.currentTimeMillis();
        }

        return fileName;
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String fileName) {
        if (TextUtils.isEmpty(fileName)) {
            return "";
        }
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex != -1 && dotIndex < fileName.length() - 1) {
            return fileName.substring(dotIndex);
        }
        return "";
    }

    /**
     * 根据文件扩展名获取保存目录路径
     * @return 相对于外部存储公共目录的路径
     */
    private String getSaveDirectoryPath(String extension) {
        // 根据扩展名选择不同的保存目录
        if (Constants.YDK_FILE_EX.equalsIgnoreCase(extension)) {
            // .ydk 卡组文件
            return AppsSettings.get().getDeckDir();
        } else if (Constants.YRP_FILE_EX.equalsIgnoreCase(extension)) {
            // .yrp 录像文件
            return AppsSettings.get().getReplayDir();
        } else if (Constants.YPK_FILE_EX.equalsIgnoreCase(extension)) {
            // .ypk 扩展包文件
            return AppsSettings.get().getExpansionsPath().getAbsolutePath();
        } else {
            // 其他文件保存到系统Download文件夹
            return Environment.DIRECTORY_DOWNLOADS;
        }
    }

    /**
     * 获取保存提示信息
     */
    private String getSaveMessage(String extension, String fileName) {
        if (Constants.YDK_FILE_EX.equalsIgnoreCase(extension)) {
            return "卡组文件已开始下载: " + fileName;
        } else if (Constants.YRP_FILE_EX.equalsIgnoreCase(extension)) {
            return "录像文件已开始下载: " + fileName;
        } else if (Constants.YPK_FILE_EX.equalsIgnoreCase(extension)) {
            return "扩展包文件已开始下载: " + fileName;
        } else {
            return "文件已开始下载: " + fileName;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mWebView != null) {
            mWebView.onResume();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mWebView != null) {
            mWebView.onPause();
        }
    }

    @Override
    public void onFirstUserVisible() {

    }

    @Override
    public void onUserVisible() {

    }

    @Override
    public void onFirstUserInvisible() {

    }

    @Override
    public void onUserInvisible() {

    }

    @Override
    public void onBackHome() {

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mWebView != null) {
            mWebView.destroy();
        }
    }

    @Override
    public boolean onBackPressed() {
        // 如果WebView可以返回，则返回上一页
        if (mWebView != null && mWebView.canGoBack()) {
            mWebView.goBack();
            return true;
        }
        return false;
    }
}

