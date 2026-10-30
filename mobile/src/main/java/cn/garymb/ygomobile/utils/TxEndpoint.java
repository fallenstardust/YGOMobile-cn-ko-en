package cn.garymb.ygomobile.utils;

import android.text.TextUtils;
import android.util.Log;

import org.json.JSONArray;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * TX 云可用节点查询：以进程生命周期为准只请求一次，取列表第一个主机，用来替换
 * 下载地址里的固定源站（{@value #DEFAULT_HOST} 与 {@value #CDN02_AUTHORITY}）。
 *
 * <p>背景：{@code cdntx.moecube.com} 与 {@code cdn02.moecube.com:444} 都是同一套 TX 资
 * 源桶（先行卡包/版本文件/卡图/lflist）的固定入口，近期 TX 侧风控导致它们不稳定。
 * 服务端为此提供了一个“查可用 endpoint 的 API”
 * {@code https://sapi.moecube.com:444/cdntx-endpoint/_endpoint}，返回按可用性排序的主机
 * JSON 数组（如 {@code ["cdntx2.moecube.com","cdntx3.moecube.com","cdntx.moecube.com"]}）。
 * 本类在 App 启动时异步取一次并用第一个；取不到（无网、超时、解析失败、列表为空或
 * 全部非法）就沿用原来的固定域名。查询与替换都不阻塞任何调用方。
 *
 * <p><b>查询结果未回来前不改写任何 URL</b>（sHost 为 null），保证启动初期与查询失败时的
 * 行为与改动前逐字一致；结果回来后，两类固定源站都改到同一个可用主机。
 *
 * <p>替换只动主机（含端口）段，协议/路径/查询串保持原样；候选节点都是 443 裸主机，所以
 * {@code :444} 会被去掉。其他主机（如 {@code ygom.top}、{@code raw.githubusercontent.com}、
 * {@code sapi.moecube.com}）原样返回。
 */
public final class TxEndpoint {

    private static final String TAG = "TxEndpoint";

    /** cdntx 固定节点主机：查询失败时的默认值，也是被替换的目标之一。 */
    public static final String DEFAULT_HOST = "cdntx.moecube.com";

    /** cdn02 固定站点（带端口）：同为 TX 资源桶的固定入口，与 cdntx 一起被替换。 */
    public static final String CDN02_AUTHORITY = "cdn02.moecube.com:444";

    /**
     * 可被替换的固定 authority（参{@link #resolve(String)}）：cdntx 固定节点与 cdn02
     * 站点（带/不带端口两种写法）。已验证两侧指向同一 TX 资源桶，同一资源路径内容一致。
     */
    private static final String[] SWAPPABLE_AUTHORITIES = {
            DEFAULT_HOST,
            CDN02_AUTHORITY,
            "cdn02.moecube.com"
    };

    /** 可用节点查询接口（sapi 不走 cdntx，因此不受该节点抖动影响）。 */
    private static final String ENDPOINT_API = "https://sapi.moecube.com:444/cdntx-endpoint/_endpoint";

    /** 查询到的可用主机；<b>null 表示尚未查到</b>（此时 {@link #resolve(String)} 不改写任何 URL）。 */
    private static volatile String sHost;

    /** 落实“打开 app 请求一次”：无论成功失败，本进程内只发一次查询。 */
    private static final AtomicBoolean sRequested = new AtomicBoolean(false);

    private TxEndpoint() {
    }

    /** 当前生效的固定节点替换主机；未查到为 {@link #DEFAULT_HOST}。 */
    public static String getHost() {
        String host = sHost;
        return host == null ? DEFAULT_HOST : host;
    }

    /** 是否已拿到可用节点（未拿到时 {@link #resolve(String)} 一律原样返回）。 */
    public static boolean isResolved() {
        return sHost != null;
    }

    /**
     * 异步查询可用节点。可在任意线程调用（App.onCreate 直接用），立即返回；
     * 重复调用只发一次请求。失败静默回落默认主机。
     */
    public static void refreshAsync() {
        if (!sRequested.compareAndSet(false, true)) {
            return;
        }
        try {
            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(8, TimeUnit.SECONDS)
                    .readTimeout(8, TimeUnit.SECONDS)
                    .build();
            Request request = new Request.Builder()
                    .url(ENDPOINT_API)
                    .header("User-Agent", "YGOMobile")
                    .header("Accept", "application/json, text/plain, */*")
                    .get()
                    .build();
            client.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    // 查询不通：保持原固定源站，行为与改动前完全一致
                    Log.w(TAG, "cdntx 可用节点查询失败，保持原固定源站（cdntx/cdn02）不改写" + ": " + e);
                }

                @Override
                public void onResponse(Call call, Response response) {
                    String json = null;
                    boolean ok = false;
                    try {
                        ok = response.isSuccessful() && response.body() != null;
                        if (ok) {
                            json = response.body().string();
                        }
                    } catch (Throwable t) {
                        Log.w(TAG, "cdntx 可用节点读取响应异常，保持原固定源站（cdntx/cdn02）不改写", t);
                        ok = false;
                    } finally {
                        response.close();
                    }
                    if (!ok) {
                        Log.w(TAG, "cdntx 可用节点查询响应无效，保持原固定源站（cdntx/cdn02）不改写");
                        return;
                    }
                    applyHosts(json);
                }
            });
        } catch (Throwable t) {
            Log.w(TAG, "cdntx 可用节点查询发起失败，保持原固定源站（cdntx/cdn02）不改写", t);
        }
    }

    /**
     * 解析接口返回的主机数组并采用第一个合法项。列表为空/全部非法/解析失败时保持
     * “未查到”状态，即 {@link #resolve(String)} 不改写任何下载地址。
     */
    private static void applyHosts(String json) {
        if (TextUtils.isEmpty(json)) {
            Log.w(TAG, "cdntx 可用节点返回空，保持原固定源站（cdntx/cdn02）不改写");
            return;
        }
        try {
            JSONArray array = new JSONArray(json.trim());
            for (int i = 0; i < array.length(); i++) {
                String host = normalizeHost(array.optString(i));
                if (host != null) {
                    sHost = host;
                    Log.i(TAG, "cdntx 可用节点采用第 " + (i + 1) + " 个: " + host
                            + "（候选 " + array.length() + " 个）");
                    return;
                }
            }
            Log.w(TAG, "cdntx 可用节点列表无合法项，保持原固定源站（cdntx/cdn02）不改写");
        } catch (Throwable t) {
            Log.w(TAG, "cdntx 可用节点返回非 JSON 数组，保持原固定源站（cdntx/cdn02）不改写", t);
        }
    }

    /** 主机合法性校验：只允许裸主机名，去掉首尾空白与多余的 scheme/端口/路径。 */
    private static String normalizeHost(String raw) {
        if (raw == null) {
            return null;
        }
        String host = raw.trim().toLowerCase(Locale.ROOT);
        if (host.isEmpty() || host.length() > 255) {
            return null;
        }
        // 只要裸主机：带 scheme/端口/路径/查询/空白/非法字符的一律视为非法项
        for (int i = 0; i < host.length(); i++) {
            char c = host.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')
                    || c == '.' || c == '-' || c == '_';
            if (!ok) {
                return null;
            }
        }
        if (!host.contains(".")) {
            return null;
        }
        return host;
    }

    /**
     * 把 URL 里的固定 cdntx / cdn02 源站换成查询到的可用主机；其余一律原样返回。
     * 调用方仍应继续用<b>原始</b> URL 作为缓存/ETag 的键，避免主机切换导致键漂移。
     */
    public static String resolve(String url) {
        String host = sHost;
        if (TextUtils.isEmpty(url) || host == null) {
            // 尚未拿到可用节点：不重写，保持原有固定域名（与改动前行为逐字一致）
            return url;
        }
        int schemeEnd = url.indexOf("://");
        if (schemeEnd < 0) {
            return url;
        }
        int authorityStart = schemeEnd + 3;
        int authorityEnd = url.length();
        for (int i = authorityStart; i < url.length(); i++) {
            char c = url.charAt(i);
            if (c == '/' || c == '?' || c == '#') {
                authorityEnd = i;
                break;
            }
        }
        String authority = url.substring(authorityStart, authorityEnd).toLowerCase(Locale.ROOT);
        for (String swappable : SWAPPABLE_AUTHORITIES) {
            if (swappable.equals(authority)) {
                // 候选节点都是 443 裸主机，所以 cdn02 的 :444 端口在此被去掉
                return url.substring(0, authorityStart) + host + url.substring(authorityEnd);
            }
        }
        return url;
    }
}
