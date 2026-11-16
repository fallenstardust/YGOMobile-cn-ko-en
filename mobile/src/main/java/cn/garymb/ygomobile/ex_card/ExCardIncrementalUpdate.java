package cn.garymb.ygomobile.ex_card;

import com.github.luben.zstd.ZstdInputStream;
import com.google.gson.Gson;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.io.StringReader;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.lite.BuildConfig;
import cn.garymb.ygomobile.utils.FileUtils;
import cn.garymb.ygomobile.utils.LogUtil;
import cn.garymb.ygomobile.utils.MD5Util;
import cn.garymb.ygomobile.utils.TxEndpoint;
import cn.garymb.ygomobile.utils.UnzipUtils;
import okhttp3.Call;
import okhttp3.EventListener;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;
import org.xmlpull.v1.XmlPullParserFactory;

/**
 * 先行卡包增量更新，做法同 MyCard 客户端：取新版本的校验清单，与本地旧包逐条目比对 sha256，
 * 向发布接口请求覆盖有变化文件的 .tar.zst（服务端发布时预先打好），下载后与旧包合成新包。
 *
 * <p>失败分三类，处理不同：
 * <ul>
 *     <li>网络问题（换哪台服务器都一样）：在这里重试，用尽回报 {@link Listener#onNetworkFailure}，
 *     已下载的部分留在缓存里下次续传，不改下完整包，手机网络不稳时不多耗流量；</li>
 *     <li>正在发版（清单与 version.txt 暂时不是同一版）：回报 {@link Listener#onTryLater}，什么都不下；</li>
 *     <li>服务器或数据问题（发布接口出错、内容对不上）：回报 {@link Listener#onFallback}，由调用方
 *     问用户要不要改从 CDN 下完整包。</li>
 * </ul>
 * 怎么区分见 {@link #retry} 与 {@link #serverSide}。全部工作与回调都在后台线程。
 *
 * <p>日志与错误信息都写明：哪一步、哪个对象、网络还是服务器、期望与实际、接下来怎么办。
 */
public final class ExCardIncrementalUpdate {

    private static final String TAG = "ExCardIncremental";
    /** MyCard 发布接口里的应用 id */
    private static final String APP_ID = "ygopro-super-pre";
    /** 清单路径的前缀，ypk 条目名没有 */
    private static final String ROOT = "expansions/";

    private static final Pattern VERSION = Pattern.compile("ygopro-super-pre-([0-9A-Za-z._-]+)\\.ypk\\s*$");
    /** 包的 sha256 也用作本地文件名，只收 64 位小写十六进制 */
    private static final Pattern SHA256_HEX = Pattern.compile("[0-9a-f]{64}");
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final int BUFFER_SIZE = 64 * 1024;
    /** 连续这么多次失败且没有进展就放弃，退避 1、2、4、8 秒 */
    private static final int ATTEMPTS = 5;
    private static final int READ_TIMEOUT_S = 30;
    /** 挑包请求平时不到 1 秒就回；读超时放宽作兜底，响应只有几 KB，多等不费流量 */
    private static final int UPDATE_READ_TIMEOUT_S = 90;

    /** 回调都在后台线程；界面显示什么由调用方决定 */
    public interface Listener {
        enum Stage {
            /** 取版本号与校验清单 */
            CHECKING,
            /** 与本地旧包逐条目比对 */
            COMPARING,
            /** 向发布接口要覆盖变化文件的包 */
            REQUESTING,
            /** 合成新包 */
            MERGING
        }

        void onStage(Stage stage);

        /** 开始下载：{@code files} 个有变化的文件，共 {@code bytes} 字节 */
        void onDownloadStart(int files, long bytes);

        /** 下载进度 0-100 */
        void onProgress(int percent);

        /** 失败了，稍后重试；{@code serverSide} 为服务器问题，否则为网络问题（次数与等待时间写在日志里） */
        void onRetry(boolean serverSide);

        /** 新包已合成到 {@code ypk}，按原有流程替换即可 */
        void onSuccess(File ypk);

        /** 增量走不通（服务器或数据问题），可以改下完整包，先问用户；{@code reason} 已写进日志 */
        void onFallback(String reason);

        /** 网络问题，重试用尽；已下载的 {@code keptBytes} 字节留在缓存里，下次接着用 */
        void onNetworkFailure(Exception e, long keptBytes);

        /**
         * 正在发版：发布清单与本次检查到的 version.txt 不是同一版（CDN 上两个文件各自缓存 10 分钟，刚发版时会
         * 一新一旧）。什么都没下，几分钟后再试即可；不改下完整包，免得白耗流量或把旧内容记成新版本
         */
        void onTryLater(String reason);
    }

    /** 发布清单与 version.txt 暂时对不上：几分钟后再试 */
    private static final class TryLaterException extends Exception {
        TryLaterException(String message) {
            super(message);
        }
    }

    /** 服务器、数据或协议问题：提议改下完整包 */
    private static final class FallbackException extends Exception {
        FallbackException(String message) {
            super(message);
        }
    }

    /** 网络重试用尽：不改下完整包 */
    private static final class NetworkException extends Exception {
        NetworkException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /** 服务端返回的错误码（可重试的那几种）；重试用尽仍是它就不是网络问题 */
    private static final class HttpException extends IOException {
        HttpException(String message) {
            super(message);
        }
    }

    private interface Attempt<T> {
        T run() throws IOException, FallbackException;
    }

    private static final class Archive {
        String name;
        String sha256;
        String url;
        long size = -1;

        /** 包名是 128 位十六进制，日志里只留开头 */
        String label() {
            return "包 " + (name.length() > 12 ? name.substring(0, 12) + ".." : name) + "（" + size + " 字节）";
        }
    }

    private final File installed;
    private final File output;
    private final File workRoot;
    private final String dataVersion;
    private final Listener listener;

    /** 当前请求走到了哪一步，失败时写进日志，分清是连不上还是连上了等不到回应；请求都在本线程顺序执行 */
    private volatile String phase = "";
    private final EventListener phaseListener = new EventListener() {
        private void at(Call call, String step) {
            phase = step + " " + call.request().url().host();
        }

        @Override public void callStart(Call call) { at(call, "解析域名"); }
        @Override public void connectStart(Call call, InetSocketAddress address, Proxy proxy) { at(call, "连接"); }
        @Override public void secureConnectStart(Call call) { at(call, "TLS 握手"); }
        @Override public void requestHeadersStart(Call call) { at(call, "发送请求"); }
        @Override public void responseHeadersStart(Call call) { at(call, "等待回应"); }
        @Override public void responseBodyStart(Call call) { at(call, "读取响应"); }
    };
    /**
     * 断线靠读超时发现，不开 HTTP/2 PING：慢网上数据塞满链路，PING 的回应排在数据后面赶不回来，OkHttp 会
     * 自己掐断好好的连接（实测 10 KB/s 的蜂窝网络下校验清单连续 5 次被掐断，还被当成服务器问题改下了完整包）。
     */
    private final OkHttpClient client = new OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
            .eventListener(phaseListener)
            .build();
    private final OkHttpClient updateClient = client.newBuilder().readTimeout(UPDATE_READ_TIMEOUT_S, TimeUnit.SECONDS).build();
    /** 区分网络与服务器问题的探测请求：短超时，不改 {@link #phase} */
    private final OkHttpClient probeClient = client.newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .eventListener(EventListener.NONE)
            .build();
    private final String userAgent = "YGOMobile/" + BuildConfig.VERSION_NAME;

    private ExCardIncrementalUpdate(File installed, File output, File cacheDir, String dataVersion, Listener listener) {
        this.installed = installed;
        this.output = output;
        this.workRoot = new File(cacheDir, "superpre-incremental");
        this.dataVersion = dataVersion;
        this.listener = listener;
    }

    /**
     * @param installed   已安装的旧包
     * @param output      新包写到这里（与完整下载同一个临时档案）
     * @param dataVersion 本次检查到的 {@code data/version.txt}，用来确认清单是同一版
     */
    public static void start(File installed, File output, File cacheDir, String dataVersion, Listener listener) {
        new Thread(new ExCardIncrementalUpdate(installed, output, cacheDir, dataVersion, listener)::run, TAG).start();
    }

    private void run() {
        long startTime = System.currentTimeMillis();
        try {
            listener.onStage(Listener.Stage.CHECKING);
            String tag = get("latest-tag.txt", TxEndpoint.resolve(Constants.URL_SUPERPRE_LATEST_TAG)).trim();
            Matcher m = VERSION.matcher(tag);
            if (!m.find()) {
                throw new FallbackException("latest-tag.txt 里没有 ygopro-super-pre-<版本>.ypk：\"" + abbreviate(tag) + "\"");
            }
            String version = m.group(1);
            File workDir = new File(workRoot, version);
            File[] stale = workRoot.listFiles();
            if (stale != null) {
                for (File dir : stale) {
                    if (!dir.equals(workDir)) {
                        FileUtils.deleteDir(dir);
                    }
                }
            }

            Map<String, String> checksums = new LinkedHashMap<>();//清单路径 -> sha256，目录行没有 sha256
            String list = get("校验清单 " + version, Constants.URL_MYCARD_RELEASE_API + "checksums/" + APP_ID + "/" + version);
            for (String line : list.split("\n")) {
                String[] parts = line.split(" {2}", 2);
                if (parts.length == 2 && !parts[0].isEmpty()) {
                    checksums.put(parts[1], parts[0]);
                }
            }
            String versionHash = checksums.get(ROOT + "version.txt");
            if (versionHash == null) {
                throw new FallbackException("校验清单 " + version + "（" + checksums.size() + " 个文件）里没有 " + ROOT + "version.txt");
            }
            if (!versionHash.equals(MD5Util.getStringSHA256(dataVersion + "\n")) && !versionHash.equals(MD5Util.getStringSHA256(dataVersion))) {
                throw new TryLaterException("校验清单 " + version + " 不是本次检查到的那一版：清单里 version.txt 的 sha256 是 "
                        + versionHash.substring(0, 12) + "..，本次检查到的 version.txt 是 \"" + dataVersion + "\"");
            }

            //ypk 里应有的条目：expansions/ 下的文件，去掉顶层 .json/.txt（版本与列表文件，完整 ypk 里也没有）
            Map<String, String> target = new LinkedHashMap<>();
            for (Map.Entry<String, String> e : checksums.entrySet()) {
                String name = e.getKey().startsWith(ROOT) ? e.getKey().substring(ROOT.length()) : "";
                if (!name.isEmpty() && !(name.indexOf('/') < 0 && (name.endsWith(".json") || name.endsWith(".txt")))) {
                    target.put(name, e.getValue());
                }
            }
            listener.onStage(Listener.Stage.COMPARING);
            Map<String, String> local;
            try {
                local = hashZip(installed);
            } catch (IOException e) {
                throw new FallbackException("读不了已装的旧包 " + installed + "：" + describe(e));
            }
            Map<String, String> needed = new LinkedHashMap<>();//条目名 -> sha256
            for (Map.Entry<String, String> e : target.entrySet()) {
                if (!e.getValue().equals(local.get(e.getKey()))) {
                    needed.put(e.getKey(), e.getValue());
                }
            }
            LogUtil.i(TAG, "版本 " + version + "：" + target.size() + " 个文件里有 " + needed.size()
                    + " 个与已装旧包不同" + elapsed(startTime));

            List<File> files = new ArrayList<>();
            if (!needed.isEmpty()) {
                List<String> paths = new ArrayList<>();
                for (String name : needed.keySet()) {
                    paths.add(ROOT + name);
                }
                listener.onStage(Listener.Stage.REQUESTING);
                List<Archive> archives = requestArchives(version, paths);
                if (!workDir.isDirectory() && !workDir.mkdirs()) {
                    throw new FallbackException("建不了下载目录 " + workDir);
                }
                long total = 0;
                for (Archive a : archives) {
                    total += a.size;
                }
                listener.onDownloadStart(needed.size(), total);
                long done = 0;
                for (Archive a : archives) {
                    files.add(download(a, workDir, done, total));
                    done += a.size;
                }
                LogUtil.i(TAG, "下载完成：" + archives.size() + " 个包，共 " + total + " 字节" + elapsed(startTime));
            }
            listener.onStage(Listener.Stage.MERGING);
            rebuild(target, needed, files);
            LogUtil.i(TAG, "新包已合成：" + output + "，" + target.size() + " 个文件" + elapsed(startTime));
            FileUtils.deleteDir(workRoot);
            listener.onSuccess(output);
        } catch (NetworkException e) {
            long kept = FileUtils.getDirSize(workRoot);
            LogUtil.e(TAG, e.getMessage() + "；已下载的 " + kept + " 字节留在 " + workRoot + "，下次接着用");
            listener.onNetworkFailure(e, kept);
        } catch (TryLaterException e) {
            LogUtil.w(TAG, "正在发版，什么都没下，几分钟后再试：" + e.getMessage());
            listener.onTryLater(e.getMessage());
        } catch (FallbackException e) {
            LogUtil.e(TAG, "增量更新走不通，问用户要不要改下完整包：" + e.getMessage());
            cleanUpForFallback();
            listener.onFallback(e.getMessage());
        } catch (Exception | LinkageError e) {
            //LinkageError：zstd 原生库加载失败（UnsatisfiedLinkError）时完整下载仍可用；其他 Error 照常抛出
            LogUtil.e(TAG, "增量更新意外出错，问用户要不要改下完整包", e);
            cleanUpForFallback();
            listener.onFallback(describe(e));
        }
    }

    private void cleanUpForFallback() {
        FileUtils.deleteDir(workRoot);
        output.delete();
    }

    /** 服务端挑出覆盖这些文件的包；可能挑中完整包，不比现有 ypk 小就直接下完整 ypk */
    private List<Archive> requestArchives(String version, List<String> paths) throws Exception {
        String what = "挑包请求 " + version + "（" + paths.size() + " 个文件）";
        String xml = call(what, new Request.Builder().url(Constants.URL_MYCARD_RELEASE_API + "update/" + APP_ID + "/" + version)
                .post(RequestBody.create(JSON, new Gson().toJson(paths))));
        List<Archive> archives;
        try {
            archives = parseMetalink(xml);
        } catch (XmlPullParserException | NumberFormatException e) {
            throw new FallbackException(what + "：metalink 解析不了（" + e.getMessage() + "）：\"" + abbreviate(xml) + "\"");
        }
        long total = 0;
        for (Archive a : archives) {
            boolean hasSize = a.size >= 0, hasHash = a.sha256 != null && SHA256_HEX.matcher(a.sha256).matches(), hasUrl = a.url != null;
            if (a.name == null || !hasSize || !hasHash || !hasUrl) {
                throw new FallbackException(what + "：metalink 条目 " + (a.name == null ? "（没有 name）" : abbreviate(a.name)) + " 缺少"
                        + (hasSize ? "" : " <size>") + (hasHash ? "" : " sha-256 <hash>") + (hasUrl ? "" : " <url>"));
            }
            total += a.size;
        }
        if (archives.isEmpty()) {
            throw new FallbackException(what + "：metalink 里没有包：\"" + abbreviate(xml) + "\"");
        }
        if (total >= installed.length()) {
            throw new FallbackException(what + "：" + archives.size() + " 个包共 " + total
                    + " 字节，不比已装的完整包（" + installed.length() + " 字节）小");
        }
        return archives;
    }

    /** metalink 里每个 {@code <file>} 的 name、size、sha-256 与第一个 url；缺的字段留空，由调用方检查 */
    private static List<Archive> parseMetalink(String xml) throws XmlPullParserException, IOException {
        XmlPullParser parser = XmlPullParserFactory.newInstance().newPullParser();
        parser.setInput(new StringReader(xml));
        List<Archive> archives = new ArrayList<>();
        Archive a = null;
        for (int event = parser.getEventType(); event != XmlPullParser.END_DOCUMENT; event = parser.next()) {
            if (event == XmlPullParser.END_TAG && "file".equals(parser.getName())) {
                a = null;
            } else if (event == XmlPullParser.START_TAG && "file".equals(parser.getName())) {
                a = new Archive();
                a.name = parser.getAttributeValue(null, "name");
                archives.add(a);
            } else if (event == XmlPullParser.START_TAG && a != null) {
                switch (parser.getName()) {
                    case "size":
                        a.size = Long.parseLong(parser.nextText().trim());
                        break;
                    case "hash":
                        if ("sha-256".equals(parser.getAttributeValue(null, "type"))) {
                            a.sha256 = parser.nextText().trim();
                        }
                        break;
                    case "url":
                        if (a.url == null) {
                            a.url = parser.nextText().trim();
                        }
                        break;
                }
            }
        }
        return archives;
    }

    /** 下到 {@code <sha256>.tar.zst}，残缺的断点续传；内容不符重下一次，再不符就是服务端数据问题 */
    private File download(Archive a, File workDir, long done, long total) throws Exception {
        File file = new File(workDir, a.sha256 + ".tar.zst");
        String got = null;
        for (int attempt = 0; attempt < 2; attempt++) {
            retry(a.label(), HttpUrl.parse(a.url).host(), file::length, () -> {
                fetch(a, file, done, total);
                return null;
            });
            got = MD5Util.getStreamSHA256(new FileInputStream(file));
            if (a.sha256.equals(got)) {
                return file;
            }
            LogUtil.w(TAG, a.label() + "：sha256 是 " + got.substring(0, 12) + "..，metalink 里是 "
                    + a.sha256.substring(0, 12) + ".." + (attempt == 0 ? "；重新下载" : ""));
            file.delete();
        }
        throw new FallbackException(a.label() + "：下了两次，sha256 都是 " + got.substring(0, 12)
                + "..，metalink 里是 " + a.sha256.substring(0, 12) + "..");
    }

    private void fetch(Archive a, File file, long done, long total) throws IOException, FallbackException {
        long have = file.length() <= a.size ? file.length() : 0;
        if (have == a.size) {
            return;
        }
        Request.Builder request = new Request.Builder().url(a.url).header("User-Agent", userAgent);
        if (have > 0) {
            request.header("Range", "bytes=" + have + "-");
        }
        try (Response response = client.newCall(request.build()).execute()) {
            if (response.code() == 416 && have > 0) {
                //服务器上的文件比本地残片还短：残片不可信，删掉后重试时从头下这个小包，不必改下完整包
                file.delete();
                throw new IOException("续传 " + have + " 字节处返回 HTTP 416，丢掉残片从头下");
            }
            checkStatus(a.label(), response);
            if (have > 0) {
                LogUtil.i(TAG, a.label() + "：从第 " + have + " 字节续传，HTTP " + response.code()
                        + (response.code() == 206 ? "" : "（不支持续传，从头下）"));
            }
            if (response.code() != 206) {
                have = 0;
            }
            try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
                raf.setLength(have);
                raf.seek(have);
                InputStream in = response.body().byteStream();
                byte[] buf = new byte[BUFFER_SIZE];
                int n;
                while ((n = in.read(buf)) != -1 && have + n <= a.size) {
                    raf.write(buf, 0, n);
                    have += n;
                    listener.onProgress((int) Math.min(99, (done + have) * 100 / total));
                }
            }
            if (have != a.size) {
                throw new IOException("连接在第 " + have + " 字节断开，共 " + a.size + " 字节");
            }
        }
    }

    private String get(String what, String url) throws Exception {
        return call(what, new Request.Builder().url(url));
    }

    private String call(String what, Request.Builder builder) throws Exception {
        Request request = builder.header("User-Agent", userAgent).build();
        OkHttpClient c = "POST".equals(request.method()) ? updateClient : client;
        return retry(what, request.url().host(), null, () -> {
            try (Response response = c.newCall(request).execute()) {
                checkStatus(what, response);
                return response.body().string();
            }
        });
    }

    /** 4xx 说明这条路不通，不再重试；5xx、408、429 可以重试 */
    private static void checkStatus(String what, Response response) throws IOException, FallbackException {
        int code = response.code();
        String status = response.request().method() + " " + response.request().url().host() + " 返回 HTTP " + code;
        if (code >= 400 && code < 500 && code != 408 && code != 429) {
            throw new FallbackException(what + "：" + status);
        }
        if (!response.isSuccessful() || response.body() == null) {
            throw new HttpException(status);
        }
    }

    /**
     * 失败重试，连续 {@link #ATTEMPTS} 次都没有进展才放弃；{@code progress}（已下字节数）有增长就
     * 重新计数，断线频繁但每次都能多下一点时会一直续传下去。
     *
     * <p>每次失败都判断是网络还是服务器的问题（{@link #serverSide}），写进日志；放弃时，最后这一串
     * 失败全在服务器一侧才提议改下完整包（CDN 上的完整包换了一台服务器），只要有一次是网络问题就按
     * 网络失败停下，保留已下载的部分，不为网络问题多耗流量。
     */
    private <T> T retry(String what, String host, LongSupplier progress, Attempt<T> attempt)
            throws NetworkException, FallbackException, InterruptedException {
        String last = null;
        IOException lastError = null;
        boolean allServerSide = true;
        long before = progress == null ? 0 : progress.getAsLong();
        for (int failures = 0; failures < ATTEMPTS; ) {
            phase = "";
            try {
                return attempt.run();
            } catch (IOException e) {
                String during = phase;
                //超时只取消这个请求，连接还留在池里；不清掉的话 1 秒后的重试又会拿到这条可能已死的连接
                //（CDN 走 HTTP/2，隧道场景实测因此多等了两次 30 秒）
                client.connectionPool().evictAll();//updateClient、probeClient 与 client 共用这个连接池
                String verdict = serverSide(e, host);
                boolean server = verdict != null;
                long now = progress == null ? 0 : progress.getAsLong();
                if (now > before) {
                    failures = 1;
                    allServerSide = server;
                } else {
                    failures++;
                    allServerSide &= server;
                }
                last = (server ? "服务器问题：" : "网络问题：") + describe(e)
                        + (e instanceof HttpException || during.isEmpty() ? "" : "（" + during + " 时）")
                        + (server ? (e instanceof HttpException ? "" : "；" + verdict) : lastProbe.isEmpty() ? "" : "；" + lastProbe);
                lastError = e;
                String gained = now > before ? "，这次多下了 " + (now - before) + " 字节" : "";
                before = now;
                if (failures < ATTEMPTS) {
                    long wait = 1000L << (failures - 1);
                    LogUtil.w(TAG, what + "：" + last + "（第 " + failures + "/" + ATTEMPTS + " 次" + gained
                            + "，" + wait / 1000 + " 秒后重试）");
                    listener.onRetry(server);
                    Thread.sleep(wait);
                } else {
                    LogUtil.w(TAG, what + "：" + last + "（第 " + failures + "/" + ATTEMPTS + " 次，放弃）");
                }
            }
        }
        if (allServerSide) {
            throw new FallbackException(what + "：连续 " + ATTEMPTS + " 次都是服务器问题，最后一次：" + last);
        }
        throw new NetworkException(what + "：连续 " + ATTEMPTS + " 次没有进展，最后一次：" + last, lastError);
    }

    /** 最近一次探测的结果，供日志 */
    private String lastProbe = "";

    /**
     * 是不是服务器的问题：
     * <ul>
     *     <li>HTTP 错误码：服务器问题；</li>
     *     <li>域名解析失败：网络问题。断网时解析失败的结果会被缓存约 10 秒，恢复后这台主机仍查不到、
     *     另一台已解析过的却通了，实测隧道场景里因此误判过；</li>
     *     <li>其他（连接被拒、超时、断流）：换一台主机发个只要响应头的小请求，通了说明网络没问题，
     *     问题出在刚才那台服务器（服务没起来、只收不回）；不通就是网络问题。</li>
     * </ul>
     * 慢网上大请求超时、小探测却能通，也会判成服务器问题；所以改下完整包前要用户确认
     * （{@link Listener#onFallback}），不会自己去下。
     *
     * @return 服务器的问题时返回依据，网络问题返回 null
     */
    private String serverSide(IOException e, String failedHost) {
        if (e instanceof HttpException) {
            return "服务器返回了错误码";
        }
        if (e instanceof UnknownHostException) {
            lastProbe = "";
            return null;
        }
        //发布接口连不上探 CDN 上的小文件，CDN 连不上探发布接口
        String url = failedHost.equals(HttpUrl.parse(Constants.URL_MYCARD_RELEASE_API).host())
                ? TxEndpoint.resolve(Constants.URL_CN_DATAVER)
                : Constants.URL_MYCARD_RELEASE_API + "apps.json";
        Request probe = new Request.Builder().url(url).head().header("User-Agent", userAgent).build();
        try (Response response = probeClient.newCall(probe).execute()) {
            lastProbe = "网络是通的（" + probe.url().host() + " 回了 HTTP " + response.code() + "）";
            return lastProbe;
        } catch (IOException probeError) {
            lastProbe = probe.url().host() + " 也连不上（" + describe(probeError) + "）";
            return null;
        }
    }

    /** 异常写成人话；哪一步失败由 {@link #phase} 补充 */
    private static String describe(Throwable e) {
        if (e instanceof SocketTimeoutException) {
            return "超时";
        }
        if (e instanceof UnknownHostException) {
            return "域名解析失败";
        }
        if (e instanceof HttpException || e instanceof FallbackException) {
            return e.getMessage();
        }
        return e.getClass().getSimpleName() + (e.getMessage() == null ? "" : "（" + e.getMessage() + "）");
    }

    /**
     * 写新包：先从下载的包里取有变化的文件（逐个校验），再从旧包拷贝没变的条目与目录条目，
     * 新版本没有的条目不再写入。
     *
     * <p>包是服务端 {@code tar --zstd -cf} 产出的 GNU tar（{@link UnzipUtils#readTar}）；服务端按组打包，
     * 没请求的文件跳过。
     */
    private void rebuild(Map<String, String> target, Map<String, String> needed, List<File> archives) throws Exception {
        Set<String> written = new HashSet<>();
        byte[] buf = new byte[BUFFER_SIZE];
        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(output), BUFFER_SIZE));
             ZipFile zip = new ZipFile(installed)) {
            for (File archive : archives) {
                String label = "包 " + archive.getName().substring(0, 12) + "..";
                List<String> mismatches = new ArrayList<>();
                try (InputStream in = new ZstdInputStream(new BufferedInputStream(new FileInputStream(archive), BUFFER_SIZE))) {
                    UnzipUtils.readTar(in, (path, size, content) -> {
                        String name = path.startsWith(ROOT) ? path.substring(ROOT.length()) : null;
                        String expected = name != null ? needed.get(name) : null;
                        if (expected == null || !written.add(name)) {
                            return;
                        }
                        MessageDigest md = MD5Util.newSHA256();
                        zos.putNextEntry(new ZipEntry(name));
                        int n;
                        while ((n = content.read(buf)) != -1) {
                            md.update(buf, 0, n);
                            zos.write(buf, 0, n);
                        }
                        zos.closeEntry();
                        String got = MD5Util.toHexString(md.digest());
                        if (!expected.equals(got)) {
                            mismatches.add(path + " 的 sha256 是 " + got.substring(0, 12) + "..，校验清单里是 " + expected.substring(0, 12) + "..");
                        }
                    });
                } catch (IOException e) {
                    throw new FallbackException(label + "：" + describe(e));
                }
                if (!mismatches.isEmpty()) {
                    throw new FallbackException(label + "：" + mismatches.get(0)
                            + (mismatches.size() > 1 ? "，另有 " + (mismatches.size() - 1) + " 个也不符" : ""));
                }
            }
            if (written.size() != needed.size()) {
                List<String> missing = new ArrayList<>();
                for (String name : needed.keySet()) {
                    if (!written.contains(name)) {
                        missing.add(ROOT + name);
                    }
                }
                throw new FallbackException(needed.size() + " 个变化文件里有 " + missing.size() + " 个不在下载的 "
                        + archives.size() + " 个包里，例如 " + missing.subList(0, Math.min(3, missing.size())));
            }
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry old = entries.nextElement();
                if ((old.isDirectory() || target.containsKey(old.getName())) && written.add(old.getName())) {
                    ZipEntry entry = new ZipEntry(old.getName());
                    entry.setTime(old.getTime());
                    zos.putNextEntry(entry);
                    try (InputStream in = zip.getInputStream(old)) {
                        int n;
                        while ((n = in.read(buf)) != -1) {
                            zos.write(buf, 0, n);
                        }
                    }
                    zos.closeEntry();
                }
            }
        }
        if (!written.containsAll(target.keySet())) {
            List<String> missing = new ArrayList<>(target.keySet());
            missing.removeAll(written);
            throw new FallbackException("合成的新包缺了 " + target.size() + " 个文件里的 " + missing.size()
                    + " 个，例如 " + missing.subList(0, Math.min(3, missing.size())));
        }
    }

    private static Map<String, String> hashZip(File file) throws Exception {
        Map<String, String> hashes = new LinkedHashMap<>();
        try (ZipFile zip = new ZipFile(file)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.isDirectory()) {
                    hashes.put(entry.getName(), MD5Util.getStreamSHA256(zip.getInputStream(entry)));
                }
            }
        }
        return hashes;
    }

    private static String abbreviate(String s) {
        String oneLine = s.replace('\n', ' ').trim();
        return oneLine.length() > 80 ? oneLine.substring(0, 80) + ".." : oneLine;
    }

    private static String elapsed(long startTime) {
        return "（" + (System.currentTimeMillis() - startTime) + " 毫秒）";
    }
}
