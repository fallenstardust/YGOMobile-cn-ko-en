package cn.garymb.ygomobile.utils.glide;

import com.bumptech.glide.load.model.GlideUrl;

import cn.garymb.ygomobile.utils.TxEndpoint;

/**
 * 请求地址与缓存键分离的 GlideUrl：实际请求打到查询到的可用 TX 节点，而缓存键固定用
 * 服务端给出的<b>原始规范地址</b>，使先行卡图不会因为服务端把可用节点列表换序（生效
 * 主机变化）而整批缓存失效、全量重下。
 *
 * <p>原理（Glide 4.12 已核对源码）：{@link GlideUrl#getCacheKey()} 是 public 非 final，
 * 且 {@code toString()}/{@code equals()}/{@code hashCode()}/{@code updateDiskCacheKey()}
 * 全部由它派生；内存缓存键 {@code EngineKey} 的 equals/hashCode 直接用
 * {@code model.equals()}/{@code model.hashCode()}，磁盘缓存键经
 * {@code updateDiskCacheKey → getCacheKeyBytes → getCacheKey} 取值。所以只覆盖这一个方法
 * 就能同时稳定两级缓存，无需注册 AppGlideModule、无需替换 HTTP 栈——本工程未引入
 * okhttp3-integration，Glide 用默认的 HttpUrlFetcher，它取
 * {@code toStringUrl()}/{@code toURL()} 发请求，与缓存键互不相干。
 */
public class HostAgnosticGlideUrl extends GlideUrl {

    /** 稳定缓存键：始终为原始规范地址（含固定源站主机），与生效主机无关。 */
    private final String cacheKey;

    /**
     * @param canonicalUrl 服务端 JSON 里的原始地址；只作为缓存键，不参与实际请求
     */
    public HostAgnosticGlideUrl(String canonicalUrl) {
        // 请求地址：固定源站（cdntx / cdn02）换成查到的可用节点，未查到或非固定源站原样
        super(TxEndpoint.resolve(canonicalUrl));
        this.cacheKey = canonicalUrl;
    }

    @Override
    public String getCacheKey() {
        return cacheKey;
    }
}
