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
 * TX 云（cdntx）可用节点查询：以进程生命周期为准只请求一次，取列表第一个主机，
 * 用来替换下载地址里的固定节点 {@value #DEFAULT_HOST}。
 *
 * <p>背景：{@code cdntx.moecube.com} 是 TX 云的固定回源节点，近期 TX 侧风控导致它
 * 不稳定。服务端为此提供了一个“查可用 endpoint 的 API”
 * {@code https://sapi.moecube.com:444/cdntx-endpoint/_endpoint}，返回按可用性排序的
 * 主机 JSON 数组（如 {@code ["cdntx2.moecube.com","cdntx3.moecube.com","cdntx.moecube.com"]}）。
 * 本类在 App 启动时异步取一次并用第一个；取不到（无网、超时、解析失败、列表为空或
 * 全部非法）就沿用原来的固定域名。查询与替换都不阻塞任何调用方：结果没回来时
 * {@link #resolve(String)} 一律返回原 URL。
 *
 * <p>替换只动主机段，协议/端口/路径/查询串保持原样；主机不是
 * {@value #DEFAULT_HOST} 的 URL（如 {@code cdn02.moecube.com:444} 系列）原样返回。
 */
public final class TxEndpoint {

    private static final String TAG = "TxEndpoint";

    /** 固定回源主机：既是查询失败时的默认值，也是被替换的目标主机。 */
    public static final String DEFAULT_HOST = "cdntx.moecube.com";

    /** 可用节点查询接口（sapi 不走 cdntx，因此不受该节点抖动影响）。 */
    private static final String ENDPOINT_API = "https://sapi.moecube.com:444/cdntx-endpoint/_endpoint";

    /** 进程内缓存的可用主机；未查到即保持默认值。 */
    private static volatile String sHost = DEFAULT_HOST;

    /** 落实“打开 app 请求一次”：无论成功失败，本进程内只发一次查询。 */
    private static final AtomicBoolean sRequested = new AtomicBoolean(false);

    private TxEndpoint() {
    }

    /** 当前生效的 cdntx 主机（未查到为 {@link #DEFAULT_HOST}）。 */
    public static String getHost() {
        return sHost;
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
                    // 查询不通：保持默认主机，行为与改动前完全一致
                    Log.w(TAG, "cdntx 可用节点查询失败，沿用默认主机 " + DEFAULT_HOST + ": " + e);
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
                        Log.w(TAG, "cdntx 可用节点读取响应异常，沿用默认主机 " + DEFAULT_HOST, t);
                        ok = false;
                    } finally {
                        response.close();
                    }
                    if (!ok) {
                        Log.w(TAG, "cdntx 可用节点查询响应无效，沿用默认主机 " + DEFAULT_HOST);
                        return;
                    }
                    applyHosts(json);
                }
            });
        } catch (Throwable t) {
            Log.w(TAG, "cdntx 可用节点查询发起失败，沿用默认主机 " + DEFAULT_HOST, t);
        }
    }

    /**
     * 解析接口返回的主机数组并采用第一个合法项。列表为空/全部非法/解析失败时
     * 不改动态主机（保持默认）。
     */
    private static void applyHosts(String json) {
        if (TextUtils.isEmpty(json)) {
            Log.w(TAG, "cdntx 可用节点返回空，沿用默认主机 " + DEFAULT_HOST);
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
            Log.w(TAG, "cdntx 可用节点列表无合法项，沿用默认主机 " + DEFAULT_HOST);
        } catch (Throwable t) {
            Log.w(TAG, "cdntx 可用节点返回非 JSON 数组，沿用默认主机 " + DEFAULT_HOST, t);
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
     * 把 URL 里的固定 cdntx 主机换成查询到的可用主机；其余一律原样返回。
     * 调用方仍应继续用<b>原始</b> URL 作为缓存/ETag 的键，避免主机切换导致键漂移。
     */
    public static String resolve(String url) {
        if (TextUtils.isEmpty(url) || DEFAULT_HOST.equals(sHost)) {
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
        String authority = url.substring(authorityStart, authorityEnd);
        // 带端口的地址不是 cdntx 固定节点（返回的候选都是 443 裸主机），不替换
        if (authority.indexOf(':') >= 0) {
            return url;
        }
        if (!DEFAULT_HOST.equalsIgnoreCase(authority)) {
            return url;
        }
        return url.substring(0, authorityStart) + sHost + url.substring(authorityEnd);
    }
}
