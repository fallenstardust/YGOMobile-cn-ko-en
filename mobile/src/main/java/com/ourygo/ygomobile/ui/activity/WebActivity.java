package com.ourygo.ygomobile.ui.activity;


import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ProgressBar;

import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;


import com.feihua.dialogutils.util.DialogUtils;

import com.ourygo.ygomobile.OYApplication;
import com.ourygo.ygomobile.util.CookieUtil;
import com.ourygo.ygomobile.util.HandlerUtil;
import com.ourygo.ygomobile.util.IntentUtil;
import com.ourygo.ygomobile.util.OYUtil;
import com.ourygo.ygomobile.util.Record;
import com.ourygo.ygomobile.util.SharedPreferenceUtil;
import com.tencent.smtt.export.external.interfaces.IX5WebChromeClient;
import com.tencent.smtt.export.external.interfaces.JsPromptResult;
import com.tencent.smtt.export.external.interfaces.JsResult;
import com.tencent.smtt.export.external.interfaces.SslError;
import com.tencent.smtt.export.external.interfaces.SslErrorHandler;
import com.tencent.smtt.sdk.DownloadListener;
import com.tencent.smtt.sdk.TbsVideo;
import com.tencent.smtt.sdk.ValueCallback;
import com.tencent.smtt.sdk.WebChromeClient;
import com.tencent.smtt.sdk.WebView;
import com.tencent.smtt.sdk.WebViewClient;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.activities.BaseActivity;
import cn.garymb.ygomobile.ui.mycard.mcchat.management.UserManagement;
import cn.garymb.ygomobile.utils.DownloadUtil;

public class WebActivity extends BaseActivity implements Toolbar.OnMenuItemClickListener {

    public static final String ARG_URL = "url";
    private static final int TYPE_IMAGE_SAVE_OK = 0;
    private static final int TYPE_IMAGE_SAVE_ING = 1;
    private static final int TYPE_IMAGE_SAVE_EXCEPTION = 2;
    private static final int FILECHOOSER_RESULTCODE = 0;
    private String homeUrl;
    private WebView wv_web;
    private ProgressBar web_pro;
    private ValueCallback<Uri[]> mUploadCallbackAboveL;
    private ValueCallback<Uri> mUploadMessage;

    private boolean isShowCopyUrl;
    private boolean isShowUrlIntent;
    private DialogUtils du;
    private Toolbar currentToolbar;

    @SuppressLint("HandlerLeak")
    Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case TYPE_IMAGE_SAVE_OK:
                    du.dis();
                    String path = msg.obj.toString();
                    OYUtil.snackShow(wv_web, "已保存到" + path);
                    OYApplication.get().sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(new File(path))));
                    break;
                case TYPE_IMAGE_SAVE_EXCEPTION:
                    du.dis();
                    OYUtil.snackExceptionToast(WebActivity.this, wv_web, "下载失败", msg.obj.toString());
                    break;
                case TYPE_IMAGE_SAVE_ING:
                    du.setMessage("图片下载中" + msg.obj + "%");
                    break;
            }
        }
    };
    private boolean isCheckSchoolLogin;
    private WebViewClient client = new WebViewClient() {
        /**
         * 防止加载网页时调起系统浏览器
         */
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            if (urlCanLoad(url.toLowerCase())) {
                Log.e("WebView", "加载URL");
                view.loadUrl(url);
            } else {
                Log.e("WebActivity", "跳转应用");
                Intent intent;
                try {
                    intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
//                    intent.addCategory(Intent.CATEGORY_BROWSABLE);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    startActivity(intent);
                    Log.e("WebActivity", "跳转完毕");
                } catch (Exception e) {
                    Log.e("WebActivity", "跳转失败" + e);
                    //e.printStackTrace();
                }

            }
            return true;
        }

        @Override
        public void onReceivedSslError(WebView webView, SslErrorHandler sslErrorHandler, SslError sslError) {
            sslErrorHandler.proceed();
        }
    };

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.web_activity);
        isCheckSchoolLogin = true;
        homeUrl = getIntent().getStringExtra(ARG_URL);
        isShowUrlIntent = getIntent().getBooleanExtra("isShowUrlIntent", true);
        isShowCopyUrl = getIntent().getBooleanExtra("isShowCopyUrl", true);
        initView();
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    private void initView() {
        wv_web = findViewById(R.id.wv_web);
        web_pro = findViewById(R.id.web_pro);
        du = DialogUtils.getInstance(this);
        currentToolbar=findViewById(R.id.toolbar);

        OYUtil.initToolbar(this, currentToolbar, "", true);
        wv_web.loadUrl(homeUrl);
        setWebView();
        currentToolbar.setOnMenuItemClickListener(this);
        wv_web.setWebViewClient(client);
        wv_web.setDownloadListener(new WebViewDownloadListener());
        wv_web.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {

                final WebView.HitTestResult hitTestResult = wv_web.getHitTestResult();
                // 如果是图片类型或者是带有图片链接的类型
                if (hitTestResult.getType() == WebView.HitTestResult.IMAGE_TYPE ||
                        hitTestResult.getType() == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
                    final String imageUrl = hitTestResult.getExtra();//获取图片
                    final String name;
                    int start = imageUrl.lastIndexOf("/");
                    if (start != -1)
                        name = imageUrl.substring(start + 1);
                    else
                        name = new SimpleDateFormat("OYW_yyyyMMddHHmmss", Locale.getDefault()).format(new Date()) + ".jpg";
                    View[] views = du.dialogt(name, "保存图片到本地？");
                    Button b1, b2;
                    b1 = (Button) views[0];
                    b2 = (Button) views[1];
                    b1.setText("取消");
                    b2.setText("保存");
                    b1.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            du.dis();
                        }
                    });
                    b2.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            du.dialogj1(null, "下载中，请稍等");
                            DownloadUtil.get().download(imageUrl, Record.getImagePath(WebActivity.this), name, new DownloadUtil.OnDownloadListener() {
                                @Override
                                public void onDownloadSuccess(File file) {
                                    HandlerUtil.sendMessage(handler, TYPE_IMAGE_SAVE_OK, file.getAbsolutePath());
                                }

                                @Override
                                public void onDownloading(int progress) {
                                    HandlerUtil.sendMessage(handler, TYPE_IMAGE_SAVE_ING, progress);
                                }

                                @Override
                                public void onDownloadFailed(Exception e) {
                                    HandlerUtil.sendMessage(handler, TYPE_IMAGE_SAVE_EXCEPTION, e);
                                }
                            });
                        }
                    });


                    return true;

                }
                return false;
            }
        });


    }

    public Uri getImageUri(Context inContext, Bitmap inImage) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        inImage.compress(Bitmap.CompressFormat.JPEG, 100, bytes);

        String path = MediaStore.Images.Media.insertImage(inContext.getContentResolver(), inImage, "Title", null);
        return Uri.parse(path);
    }

    public Bitmap webData2bitmap(String data) {
        byte[] imageBytes = Base64.decode(data.split(",")[1], Base64.DEFAULT);
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.length);
    }

    private void setWebView() {
        wv_web.setWebChromeClient(new WebChromeClient() {


            private View myView;

            @Override
            public boolean onJsAlert(final WebView view, String url, final String message, final JsResult result) {
                runOnUiThread(() -> {
                    Button b2;
                    b2 = du.dialogt1("网页提示", message);
                    b2.setText("确定");
                    b2.setOnClickListener(v -> {
                        result.confirm();//这里必须调用，否则页面会阻塞造成假死
                        du.dis();
                    });
                });
                du.setCanceledOnTouchOutside(false);
                return true;
            }

            /**
             * 处理confirm弹出框
             */
            @Override
            public boolean onJsConfirm(WebView view, String url, String message,
                                       final JsResult result) {

                View[] vv = du.dialogt("网页提示", message);
                Button b1, b2;
                b1 = (Button) vv[0];
                b2 = (Button) vv[1];
                b1.setText("取消");
                b2.setText("确定");
                b1.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        du.dis();
                        result.cancel();
                    }
                });
                b2.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        result.confirm();//这里必须调用，否则页面会阻塞造成假死
                        du.dis();
                    }
                });
                du.setCanceledOnTouchOutside(false);
                return true;
                //如果采用下面的代码会另外再弹出个消息框，目前不知道原理
                //return super.onJsConfirm(view, url, message, result);
            }

            /**
             * 处理prompt弹出框
             */
            @Override
            public boolean onJsPrompt(WebView view, String url, String message,
                                      String defaultValue, JsPromptResult result) {
                result.confirm();
                return super.onJsPrompt(view, url, message, message, result);
            }

            @Override
            public void onReceivedTitle(WebView view, String title) {
                super.onReceivedTitle(view, title);
                if (TextUtils.isEmpty(title))
                    currentToolbar.setTitle("网址加载中");
                else
                    currentToolbar.setTitle(title);
            }

            @Override
            public void onProgressChanged(WebView view, int progress) {
                if (progress != 100) {
                    web_pro.setProgress(progress);
                    web_pro.setVisibility(View.VISIBLE);
                } else {
                    web_pro.setVisibility(View.GONE);
                }
            }

            //WebChromeClient的几个方法：
            public void openFileChooser(ValueCallback<Uri> uploadMsg) {
                mUploadMessage = uploadMsg;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                startActivityForResult(Intent.createChooser(i, "File Chooser"), FILECHOOSER_RESULTCODE);
            }

            public void openFileChooser(ValueCallback uploadMsg, String acceptType) {
                this.openFileChooser(uploadMsg);
            }

            public void openFileChooser(ValueCallback<Uri> uploadMsg, String acceptType, String capture) {
                this.openFileChooser(uploadMsg);
            }

            // For Android 5.0+
            public boolean onShowFileChooser(android.webkit.WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                mUploadCallbackAboveL = filePathCallback;
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                startActivityForResult(
                        Intent.createChooser(i, "File Browser"),
                        FILECHOOSER_RESULTCODE);
                return true;
            }

            //自定义视频播放  如果需要启用这个，需要设置x5,自己实现全屏播放。目前的使用的x5的视频播放
//如果是点击h5 vedio标签的播放，需要自己实现全屏播放
            @Override
            public void onShowCustomView(View view, IX5WebChromeClient.CustomViewCallback customViewCallback) {
                super.onShowCustomView(view, customViewCallback);
                ViewGroup parent = (ViewGroup) wv_web.getParent();
                parent.removeView(wv_web);

                // 设置背景色为黑色
                view.setBackgroundColor(WebActivity.this.getResources().getColor(R.color.black));
                parent.addView(view);
                myView = view;
                currentToolbar.setVisibility(View.GONE);
                setFullScreen();
            }

            @Override
            public void onHideCustomView() {
                super.onHideCustomView();
                if (myView != null) {
                    ViewGroup parent = (ViewGroup) myView.getParent();
                    parent.removeView(myView);
                    parent.addView(wv_web);
                    myView = null;
                    currentToolbar.setVisibility(View.VISIBLE);
                    quitFullScreen();
                }
            }

        });
    }

    //Activity的方法：
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILECHOOSER_RESULTCODE) {
            if (null == mUploadMessage && null == mUploadCallbackAboveL)
                return;
            Uri result = data == null || resultCode != RESULT_OK ? null : data.getData();
            if (mUploadCallbackAboveL != null) {
                onActivityResultAboveL(requestCode, resultCode, data);
            } else if (mUploadMessage != null) {
                mUploadMessage.onReceiveValue(result);
                mUploadMessage = null;
            }
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void onActivityResultAboveL(int requestCode, int resultCode, Intent data) {
        if (requestCode != FILECHOOSER_RESULTCODE
                || mUploadCallbackAboveL == null) {
            return;
        }
        Uri[] results = null;
        if (resultCode == Activity.RESULT_OK) {
            if (data != null) {
                String dataString = data.getDataString();
                ClipData clipData = data.getClipData();
                if (clipData != null) {
                    results = new Uri[clipData.getItemCount()];
                    for (int i = 0; i < clipData.getItemCount(); i++) {
                        ClipData.Item item = clipData.getItemAt(i);

                        results[i] = item.getUri();
                    }
                }

                if (dataString != null)
                    results = new Uri[]{Uri.parse(dataString)};
            }
        }
        mUploadCallbackAboveL.onReceiveValue(results);
        mUploadCallbackAboveL = null;
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.web_menu, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (isShowUrlIntent)
            menu.findItem(R.id.menu_web).setVisible(true);
        else
            menu.findItem(R.id.menu_web).setVisible(false);

        if (isShowCopyUrl)
            menu.findItem(R.id.menu_copy).setVisible(true);
        else
            menu.findItem(R.id.menu_copy).setVisible(false);

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_copy:
                OYUtil.copyMessage(wv_web.getUrl());
                OYUtil.snackShow(wv_web, "已复制到剪贴板");
                break;
            case R.id.menu_web:
                startActivity(IntentUtil.getUrlIntent(wv_web.getUrl()));
                break;
        }
        return true;
    }

    public void setCopyUrlShow(boolean isShow) {
        isShowCopyUrl = isShow;
        invalidateOptionsMenu();
    }

    public void setUrlIntentShow(boolean isShow) {
        isShowUrlIntent = isShow;
        invalidateOptionsMenu();
    }

    /**
     * 列举正常情况下能正常加载的网页url
     *
     * @param url
     * @return
     */
    private boolean urlCanLoad(String url) {
        Log.e("WebActivity", "判断url" + url);
        return url.startsWith("http://") || url.startsWith("https://") ||
                url.startsWith("ftp://") || url.startsWith("file://");
    }

    /**
     * 打开第三方app。如果没安装则跳转到应用市场
     *
     * @param url
     */
    private void startThirdpartyApp(String url) {
        try {
            Intent intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME); // 注释1
//            if (getPackageManager().resolveActivity(intent, 0) == null) {  // 如果手机还没安装app，则跳转到应用市场
//                intent = new Intent(Intent.ACTION_VIEW, Uri.parse
//                        ("market://details?id=" + intent.getPackage())); // 注释2
//            }
            if (intent != null)
                startActivity(intent);
        } catch (Exception e) {
            Log.e("WebActivity", e.getMessage());
        }
    }

    @Override
    public void onBackPressed() {
        if (wv_web.canGoBack()) {
            wv_web.goBack();
        } else {
            super.onBackPressed();
        }
    }

    /**
     * 播放设置    已经开启x5全屏  小窗播放  页内播放等。
     */
    protected void configPlaySetting() {
        Bundle data = new Bundle();
//true表示标准全屏，false表示X5全屏；不设置默认false，
        data.putBoolean("standardFullScreen", false);
//false：关闭小窗；true：开启小窗；不设置默认true，
        data.putBoolean("supportLiteWnd", true);
//1：以页面内开始播放，2：以全屏开始播放；不设置默认：1
        data.putInt("DefaultVideoScreen", 1);
        wv_web.getX5WebViewExtension().invokeMiscMethod("setVideoParams", data);
//        standardFullScreen 全屏设置
//
//        设置为true时，我们会回调WebChromeClient的onShowCustomView方法，由开发者自己实现全屏展示；
//
//        设置为false时，由我们实现全屏展示，我们实现全屏展示需要满足下面两个条件：
//
//        a. 我们 Webview初始化的Context必须是Activity类型的Context
//
//        b. 我们 Webview 所在的Activity要声明这个属性
//
//        android:configChanges="orientation|screenSize|keyboardHidden"
//        如果不满足这两个条件，standardFullScreen 自动置为 true
//        supportLiteWnd 小窗播放设置
//
//        前提standardFullScreen=false，这个条件才生效
//
//        设置为 true， 开启小窗功能
//
//        设置为 false，不使用小窗功能
//
//        DefaultVideoScreen 初始播放形态设置
//
//        a、以页面内形态开始播放
//
//        b、以全屏形态开始播放

    }

    /**
     * 设置全屏
     */
    private void setFullScreen() {
        WebActivity.this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
    }

    /**
     * 退出全屏
     */
    private void quitFullScreen() {
// 声明当前屏幕状态的参数并获取
        final WindowManager.LayoutParams attrs = WebActivity.this.getWindow().getAttributes();
        attrs.flags &= (~WindowManager.LayoutParams.FLAG_FULLSCREEN);
        WebActivity.this.getWindow().setAttributes(attrs);
        WebActivity.this.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
    }

    /**
     * 播放视频  传入视频的url地址
     *
     * @return
     */
    protected boolean playVideoByTbs(String videoUrl) {
        if (TbsVideo.canUseTbsPlayer(WebActivity.this)) {
            //播放器是否可以使用
            Bundle xtraData = new Bundle();
            xtraData.putInt("screenMode", 102);//全屏设置 和控制栏设置
            TbsVideo.openVideo(WebActivity.this, videoUrl, xtraData);
            return true;
        } else {
            return false;
        }
    }

    /**
     * 下载文件监听器
     */
    private class WebViewDownloadListener implements DownloadListener {
        @Override
        public void onDownloadStart(final String url, String userAgent, String contentDisposition, String mimetype, long contentLength) {
            Log.e("WebActivity", "--- onDownloadStart ---" + " url = " + url + ", userAgent = " + userAgent + ", " +
                    "contentDisposition = " + contentDisposition + ", mimetype = " + mimetype + ", contentLength = " + contentLength);
//            downloadByBrowser(url);
            String[] ss=url.split("/");
           View[] v=du.dialogt("下载文件","是否下载"+ss[ss.length-1]);
           Button b1,b2;
           b1= (Button) v[0];
           b2= (Button) v[1];
           b1.setText("取消");
           b2.setText("下载");
           b1.setOnClickListener(v1 -> du.dis());
           b2.setOnClickListener(v12 -> {
               du.dis();
               startActivity(IntentUtil.getUrlIntent(url));
           });

        }
    }


}
