package cn.garymb.ygomobile.utils;

import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import cn.garymb.ygomobile.core.IrrlichtBridge;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class DownloadUtil {

    private static DownloadUtil downloadUtil;
    private final OkHttpClient okHttpClient;
    //暂时关闭
    private static final boolean ENABLE_CACHE = false;
    private static final Map<String, Call> cache = new HashMap<>();

    public static DownloadUtil get() {
        if (downloadUtil == null) {
            downloadUtil = new DownloadUtil();
        }
        return downloadUtil;
    }

    public DownloadUtil() {
        okHttpClient = new OkHttpClient();
    }


    /**
     * @param url          下载连接
     * @param destFileDir  下载的文件储存目录
     * @param destFileName 下载文件名称
     * @param listener     下载监听
     */

    public void download(final String url, final String destFileDir, final String destFileName, final OnDownloadListener listener) {
        if (ENABLE_CACHE) {
            synchronized (cache) {
                Call old = cache.get(url);
                if (old != null) {
                    Log.w(IrrlichtBridge.TAG, "exist download task by:" + url);
                    return;
                }
            }
        }
        Request request = new Request.Builder()
                .url(url)
                .build();

   /*   OkHttpClient client = new OkHttpClient();

        try {
            Response response = client.newCall(request).execute();
        } catch (IOException e) {
            e.printStackTrace();
        }*/

        //异步请求
        Call call = okHttpClient.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                // 下载失败监听回调
                listener.onDownloadFailed(e);
                if (ENABLE_CACHE) {
                    synchronized (cache) {
                        cache.remove(url);
                    }
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if(!response.isSuccessful()){
                    listener.onDownloadFailed(new Exception("error:"+response.code()));
                    return;
                }
                String contentLen = response.header("Content-Length");
                final long contentLength = (contentLen == null || contentLen.length() == 0) ? 0 : Long.parseLong(contentLen);
                InputStream is = null;
                byte[] buf = new byte[2048];
                int len = 0;
                FileOutputStream out = null;

                //储存下载文件的目录
                File dir = new File(destFileDir);
                if (!dir.exists()) {
                    dir.mkdirs();
                }
                File file = new File(dir, destFileName);
                boolean saved = false;
                try {
                    is = response.body().byteStream();
                    long total = response.body().contentLength();
                    if(contentLength > 0 && total != contentLength){
                        listener.onDownloadFailed(new Exception("file length[" + total + "] < " + contentLen));
                    } else {
                        out = new FileOutputStream(file);
                        long sum = 0;
                        while ((len = is.read(buf)) != -1) {
                            out.write(buf, 0, len);
                            sum += len;
                            int progress = (int) (sum * 1.0f / total * 100);
                            //下载中更新进度条
                            listener.onDownloading(progress);
                        }
                        out.flush();
                        saved = true;
                    }
                } catch (Exception ex) {
                    listener.onDownloadFailed(ex);
                } finally {
                    IOUtils.close(out);
                    IOUtils.close(is);
                }
                if (saved) {
					if (contentLength > 0 && file.length() < contentLength) {
						listener.onDownloadFailed(new Exception("file length[" + file.length() + "] < " + contentLen));
					} else {
						listener.onDownloadSuccess(file);
					}
                }
                if (ENABLE_CACHE) {
                    synchronized (cache) {
                        cache.remove(url);
                    }
                }
            }
        });
    }


    public static interface OnDownloadListener {

        /**
         * 下载成功之后的文件
         */
        void onDownloadSuccess(File file);

        /**
         * 下载进度
         */
        void onDownloading(int progress);

        /**
         * 下载异常信息
         */

        void onDownloadFailed(Exception e);
    }
}

