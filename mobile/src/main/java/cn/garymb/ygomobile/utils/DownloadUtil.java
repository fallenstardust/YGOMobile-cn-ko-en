package cn.garymb.ygomobile.utils;

import static cn.garymb.ygomobile.Constants.URL_GENESYS_LFLIST_DOWNLOAD_LINK;

import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
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
    public static final int TYPE_DOWNLOAD_EXCEPTION = 1;
    public static final int TYPE_DOWNLOAD_ING = 2;
    public static final int TYPE_DOWNLOAD_OK = 3;
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

    // Only the super-pre package uses the chunked download path.
    // This keeps the change scoped to the CDN-sensitive expansion download.
    public static boolean shouldUseChunkedDownload(String url) {
        if (TextUtils.isEmpty(url)) {
            return false;
        }
        String normalizedUrl = url.toLowerCase(Locale.US);
        return normalizedUrl.contains("ygopro-super-pre") || normalizedUrl.contains("superpre");
    }

    // Split a file into a small number of byte ranges so the CDN can serve the
    // download in parallel parts instead of one slow single stream.
    public static List<ChunkRange> buildChunkRanges(long contentLength, int chunkCount) {
        List<ChunkRange> ranges = new ArrayList<>();
        if (contentLength <= 0) {
            ranges.add(new ChunkRange(0, -1));
            return ranges;
        }

        int effectiveChunkCount = Math.max(1, Math.min(chunkCount, 8));
        long remaining = contentLength;
        long chunkSize = (contentLength + effectiveChunkCount - 1) / effectiveChunkCount;
        long start = 0;
        for (int i = 0; i < effectiveChunkCount; i++) {
            long size = Math.min(chunkSize, remaining);
            if (i == effectiveChunkCount - 1) {
                size = remaining;
            }
            if (size <= 0) {
                break;
            }
            long end = start + size - 1;
            ranges.add(new ChunkRange(start, end));
            start = end + 1;
            remaining -= size;
        }

        if (ranges.isEmpty()) {
            ranges.add(new ChunkRange(0, -1));
        }
        return ranges;
    }

    // 添加 ETag 存储工具方法
    private String getSavedETag(String url) {
        return SharedPreferenceUtil.getString("etag_" + url, "");
    }

    private void saveETag(String url, String etag) {
        SharedPreferenceUtil.putString("etag_" + url, etag);
    }

    /**
     * 执行文件下载任务，支持断点续传检测与进度回调。
     *
     * @param url          要下载文件的网络地址
     * @param destFileDir  文件保存的目标目录路径
     * @param destFileName 保存到本地的文件名
     * @param listener     下载过程的监听器，用于接收下载成功、失败和进度更新等事件
     */
    public void download(final String url, final String destFileDir, final String destFileName, final OnDownloadListener listener) {
        // Route super-pre downloads through the chunked path; keep all other downloads on the original flow.
        if (shouldUseChunkedDownload(url)) {
            downloadInChunks(url, destFileDir, destFileName, listener);
            return;
        }

        // 构建带 ETag 的请求
        Request.Builder builder = new Request.Builder().url(url);

        // 添加 If-None-Match 头部（如果有保存的 ETag）
        String savedETag = getSavedETag(url);
        if (url.equals(URL_GENESYS_LFLIST_DOWNLOAD_LINK) && !TextUtils.isEmpty(savedETag)) {//TODO 姑且只对genesys下载地址添加etag，实际需要更完善的逻辑
            Log.d("cc 当前etag", savedETag);
            builder.addHeader("If-None-Match", savedETag);
        }

        // 若启用缓存机制，则检查是否已有相同的下载任务正在进行
        if (ENABLE_CACHE) {
            synchronized (cache) {
                Call old = cache.get(url);
                if (old != null) {
                    Log.w(IrrlichtBridge.TAG, "exist download task by:" + url);
                    return;
                }
            }
        }
        // 构建HTTP请求对象
        Request request = builder.build();

        // 异步执行网络请求
        Call call = okHttpClient.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                // 请求失败时通知监听器并清理缓存记录（如已开启）
                listener.onDownloadFailed(e);
                if (ENABLE_CACHE) {
                    synchronized (cache) {
                        cache.remove(url);
                    }
                }
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (response.code() == 304) {
                    // 内容未修改，无需重新下载
                    listener.onDownloadFailed(new Exception());
                    File destFile = new File(destFileDir, destFileName);
                    if (!destFile.exists()) {//如果目标文件本不存在，但已经保存过etag，则需要重置etag确保能重新下载
                        saveETag(url, "");
                        download(url, destFileDir, destFileName, listener);
                    }
                }
                // 响应无效则直接回调失败
                if (!response.isSuccessful()) {
                    listener.onDownloadFailed(new Exception("error:" + response.code()));
                    return;
                }

                // 保存新的 ETag
                String newETag = response.header("ETag");
                if (!TextUtils.isEmpty(newETag)) {
                    saveETag(url, newETag);
                }

                // 获取响应头中的文件长度信息
                String contentLen = response.header("Content-Length");
                final long contentLength = (contentLen == null || contentLen.isEmpty()) ? 0 : Long.parseLong(contentLen);
                InputStream is = null;
                byte[] buf = new byte[2048];
                int len = 0;
                FileOutputStream out = null;
                // 创建目标文件夹（如果不存在）
                File dir = new File(destFileDir);
                if (!dir.exists()) {
                    dir.mkdirs();
                }
                File file = new File(dir, destFileName);
                boolean saved = false;
                try {
                    is = response.body().byteStream();
                    long total = response.body().contentLength();
                    // 检查实际内容长度是否匹配头部声明长度
                    if (contentLength > 0 && total != contentLength) {
                        listener.onDownloadFailed(new Exception("file length[" + total + "] < " + contentLen));
                    } else {
                        // 开始写入文件数据，并实时更新下载进度
                        out = new FileOutputStream(file, false); // 覆盖模式写入
                        long sum = 0;
                        while ((len = is.read(buf)) != -1) {
                            out.write(buf, 0, len);
                            sum += len;
                            int progress = (int) (sum * 1.0f / total * 100);
                            listener.onDownloading(progress); // 回调当前下载进度
                        }
                        out.flush();
                        saved = true;
                    }
                } catch (Exception ex) {
                    listener.onDownloadFailed(ex); // 出现异常时回调失败接口
                } finally {
                    IOUtils.close(out); // 安全关闭输出流
                    IOUtils.close(is);  // 安全关闭输入流
                }
                // 根据最终结果判断是成功还是失败回调
                if (saved) {
                    if (contentLength > 0 && file.length() < contentLength) {
                        listener.onDownloadFailed(new Exception("file length[" + file.length() + "] < " + contentLen));
                    } else {
                        listener.onDownloadSuccess(file); // 成功完成下载
                    }
                }
                // 清理缓存中对应的URL记录（如有启用）
                if (ENABLE_CACHE) {
                    synchronized (cache) {
                        cache.remove(url);
                    }
                }
            }
        });
    }


    // Probe the remote file first to learn its size, then request the file by byte ranges.
    private void downloadInChunks(final String url, final String destFileDir, final String destFileName, final OnDownloadListener listener) {
        Request.Builder builder = new Request.Builder().head().url(url);
        Request request = builder.build();
        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                downloadSingleFile(url, destFileDir, destFileName, listener);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                try {
                    if (!response.isSuccessful()) {
                        downloadSingleFile(url, destFileDir, destFileName, listener);
                        return;
                    }

                    String contentLen = response.header("Content-Length");
                    long contentLength = (contentLen == null || contentLen.isEmpty()) ? 0 : Long.parseLong(contentLen);
                    if (contentLength <= 0) {
                        downloadSingleFile(url, destFileDir, destFileName, listener);
                        return;
                    }

                    File dir = new File(destFileDir);
                    if (!dir.exists()) {
                        dir.mkdirs();
                    }
                    File file = new File(dir, destFileName);
                    if (file.exists()) {
                        file.delete();
                    }

                    // Four ranges is a good balance for CDN throughput and keeping the code simple.
                    List<ChunkRange> ranges = buildChunkRanges(contentLength, 4);
                    downloadChunksSequentially(url, file, ranges, 0, contentLength, listener);
                } finally {
                    IOUtils.close(response.body());
                }
            }
        });
    }

    // Write each requested byte range into the destination file at the correct offset.
    private void downloadChunksSequentially(final String url,
                                           final File file,
                                           final List<ChunkRange> ranges,
                                           final int index,
                                           final long totalLength,
                                           final OnDownloadListener listener) {
        if (index >= ranges.size()) {
            listener.onDownloadSuccess(file);
            return;
        }

        final ChunkRange range = ranges.get(index);
        Request.Builder builder = new Request.Builder().url(url);
        if (range.end >= 0) {
            builder.addHeader("Range", "bytes=" + range.start + "-" + range.end);
        }
        Request request = builder.build();
        okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                listener.onDownloadFailed(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    listener.onDownloadFailed(new Exception("error:" + response.code()));
                    return;
                }

                try {
                    RandomAccessFile raf = new RandomAccessFile(file, "rw");
                    raf.seek(range.start);
                    InputStream is = response.body().byteStream();
                    byte[] buf = new byte[2048];
                    int len;
                    long sum = 0;
                    while ((len = is.read(buf)) != -1) {
                        raf.write(buf, 0, len);
                        sum += len;
                        if (totalLength > 0) {
                            int progress = (int) ((index * 100 / Math.max(1, ranges.size())) + (sum * 1.0f / Math.max(1, totalLength) * 100 / Math.max(1, ranges.size())));
                            listener.onDownloading(progress);
                        }
                    }
                    raf.close();
                    if (range.end >= 0 && file.length() < totalLength) {
                        downloadChunksSequentially(url, file, ranges, index + 1, totalLength, listener);
                    } else {
                        listener.onDownloadSuccess(file);
                    }
                } catch (Exception ex) {
                    listener.onDownloadFailed(ex);
                } finally {
                    IOUtils.close(response.body());
                }
            }
        });
    }

    // Fallback for servers or responses that do not support range requests.
    private void downloadSingleFile(final String url, final String destFileDir, final String destFileName, final OnDownloadListener listener) {
        Request.Builder builder = new Request.Builder().url(url);
        Request request = builder.build();
        Call call = okHttpClient.newCall(request);
        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                listener.onDownloadFailed(e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                if (!response.isSuccessful()) {
                    listener.onDownloadFailed(new Exception("error:" + response.code()));
                    return;
                }

                String contentLen = response.header("Content-Length");
                final long contentLength = (contentLen == null || contentLen.isEmpty()) ? 0 : Long.parseLong(contentLen);
                InputStream is = null;
                byte[] buf = new byte[2048];
                int len = 0;
                FileOutputStream out = null;
                File dir = new File(destFileDir);
                if (!dir.exists()) {
                    dir.mkdirs();
                }
                File file = new File(dir, destFileName);
                boolean saved = false;
                try {
                    is = response.body().byteStream();
                    long total = response.body().contentLength();
                    if (contentLength > 0 && total != contentLength) {
                        listener.onDownloadFailed(new Exception("file length[" + total + "] < " + contentLen));
                    } else {
                        out = new FileOutputStream(file, false);
                        long sum = 0;
                        while ((len = is.read(buf)) != -1) {
                            out.write(buf, 0, len);
                            sum += len;
                            int progress = (int) (sum * 1.0f / total * 100);
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
            }
        });
    }

    public static class ChunkRange {
        public final long start;
        public final long end;

        public ChunkRange(long start, long end) {
            this.start = start;
            this.end = end;
        }
    }

    public interface OnDownloadListener {

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

