package cn.garymb.ygomobile.ui.mycard;

import android.annotation.SuppressLint;
import android.os.Bundle;
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
import cn.garymb.ygomobile.utils.DownloadUtil;
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
        View view = inflater.inflate(R.layout.fragment_mycard_web, container, false);
        initView(view);
        return view;
    }

    private void initView(View view) {
        mMyCard = new MyCard(getActivity());
        mWebView = view.findViewById(R.id.wv_web);
        mProgressBar = view.findViewById(R.id.progressBar);
        mTitleText = view.findViewById(R.id.tv_title);

        // 设置标题
        if (!TextUtils.isEmpty(mTitle) && mTitleText != null) {
            mTitleText.setText(mTitle);
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

        // 设置文件下载监听 - 使用DownloadUtil
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
     * 处理文件下载 - 使用DownloadUtil
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
        
        // 显示开始下载的提示
        String message = getSaveMessage(extension, fileName);
        YGOUtil.showTextToast(message);

        // 使用DownloadUtil进行下载
        DownloadUtil.get().download(url, saveDir, fileName, new DownloadUtil.OnDownloadListener() {
            @Override
            public void onDownloadSuccess(File file) {
                Log.d(TAG, "下载成功: " + file.getAbsolutePath());
                
                // 根据文件类型显示不同的成功提示
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        String successMsg = getSuccessMessage(extension, fileName);
                        YGOUtil.showTextToast(successMsg);
                    });
                }
            }

            @Override
            public void onDownloading(int progress) {
                // 可以在这里更新进度条或其他UI
                Log.d(TAG, "下载进度: " + progress + "%");
            }

            @Override
            public void onDownloadFailed(Exception e) {
                Log.e(TAG, "下载失败: " + e.getMessage());
                
                if (getActivity() != null) {
                    getActivity().runOnUiThread(() -> {
                        YGOUtil.showTextToast("下载失败: " + e.getMessage());
                    });
                }
            }
        });
    }

    /**
     * 获取下载成功的提示信息
     */
    private String getSuccessMessage(String extension, String fileName) {
        if (Constants.YDK_FILE_EX.equalsIgnoreCase(extension)) {
            return "卡组文件下载成功: " + fileName;
        } else if (Constants.YRP_FILE_EX.equalsIgnoreCase(extension)) {
            return "录像文件下载成功: " + fileName;
        } else if (Constants.YPK_FILE_EX.equalsIgnoreCase(extension)) {
            return "扩展包文件下载成功: " + fileName;
        } else {
            return "文件下载成功: " + fileName;
        }
    }

    /**
     * 从URL或Content-Disposition中提取文件名
     */
    private String extractFileName(String url, String contentDisposition) {
        String fileName = null;

        // 优先从Content-Disposition中提取
        if (!TextUtils.isEmpty(contentDisposition)) {
            // 先尝试提取 filename*=UTF-8 编码的文件名（支持中文）
            if (contentDisposition.contains("filename*=")) {
                fileName = extractUtf8FileName(contentDisposition);
            }
            
            // 如果没有找到 UTF-8 编码的文件名，再尝试普通的 filename
            if (TextUtils.isEmpty(fileName) && contentDisposition.contains("filename=")) {
                int index = contentDisposition.indexOf("filename=");
                fileName = contentDisposition.substring(index + 9).trim();
                // 去除引号
                fileName = fileName.replace("\"", "").replace("'", "");
                // 如果有分号，只取分号前面的部分
                int semicolonIndex = fileName.indexOf(';');
                if (semicolonIndex != -1) {
                    fileName = fileName.substring(0, semicolonIndex).trim();
                }
            }
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
                // 去除URL锚点
                int hashMark = fileName.indexOf('#');
                if (hashMark != -1) {
                    fileName = fileName.substring(0, hashMark);
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
     * 从Content-Disposition中提取UTF-8编码的文件名
     * 格式: filename*=UTF-8''encoded_text
     */
    private String extractUtf8FileName(String contentDisposition) {
        try {
            int startIndex = contentDisposition.indexOf("filename*=");
            if (startIndex == -1) {
                return null;
            }
            
            // 获取 filename*= 后面的内容
            String value = contentDisposition.substring(startIndex + 10).trim();
            
            // 检查是否是 UTF-8 编码格式: UTF-8''encoded_text
            if (value.toUpperCase().startsWith("UTF-8")) {
                // 找到两个单引号的位置
                int firstQuote = value.indexOf('\'');
                if (firstQuote != -1) {
                    int secondQuote = value.indexOf('\'', firstQuote + 1);
                    if (secondQuote != -1) {
                        // 提取编码后的文本部分
                        String encodedText = value.substring(secondQuote + 1);
                        
                        // 去除可能的分号和后续内容
                        int semicolonIndex = encodedText.indexOf(';');
                        if (semicolonIndex != -1) {
                            encodedText = encodedText.substring(0, semicolonIndex);
                        }
                        
                        // 去除首尾空格和引号
                        encodedText = encodedText.trim();
                        if (encodedText.startsWith("\"") && encodedText.endsWith("\"")) {
                            encodedText = encodedText.substring(1, encodedText.length() - 1);
                        }
                        
                        // URL解码，将 %XX 形式的编码转换为中文字符
                        return java.net.URLDecoder.decode(encodedText, "UTF-8");
                    }
                }
            } else {
                // 如果不是 UTF-8 格式，直接返回去掉引号的内容
                value = value.replace("\"", "").replace("'", "");
                int semicolonIndex = value.indexOf(';');
                if (semicolonIndex != -1) {
                    value = value.substring(0, semicolonIndex).trim();
                }
                return value;
            }
        } catch (Exception e) {
            Log.e(TAG, "解析UTF-8文件名失败: " + e.getMessage());
        }
        
        return null;
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
     * @return 保存目录的绝对路径
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
            return AppsSettings.get().getResourcePath() + File.separator + "Downloads";
        }
    }

    /**
     * 获取保存提示信息
     */
    private String getSaveMessage(String extension, String fileName) {
        if (Constants.YDK_FILE_EX.equalsIgnoreCase(extension)) {
            return "卡组文件开始下载: " + fileName;
        } else if (Constants.YRP_FILE_EX.equalsIgnoreCase(extension)) {
            return "录像文件开始下载: " + fileName;
        } else if (Constants.YPK_FILE_EX.equalsIgnoreCase(extension)) {
            return "扩展包文件开始下载: " + fileName;
        } else {
            return "文件开始下载: " + fileName;
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

