package cn.garymb.ygomobile.loader;

import androidx.annotation.NonNull;

import com.bumptech.glide.load.Key;

import java.io.File;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Objects;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;

/**
 * 卡图Glide加载模型(卡号 -> pics中的图片数据，含zip/ypk内)。
 * 同时作为缓存Key：同一卡号+同一资源目录+同一本地图片文件版本共享Glide缓存；
 * 下载新卡图后本地文件lastModified变化，缓存Key随之变化，Glide会重新加载，
 * 保证当前显示的图片立即更新为新下载的高清卡图。
 */
public class CardImageModel implements Key {
    private final long code;
    private final String resourcePath;
    private final long localVersion;

    public CardImageModel(long code) {
        this.code = code;
        this.resourcePath = AppsSettings.get().getResourcePath();
        this.localVersion = buildLocalVersion(code, resourcePath);
    }

    public long getCode() {
        return code;
    }

    /**
     * 本地pics/expansions/pics图片文件的版本号(各候选文件lastModified组合)。
     * 文件不存在时lastModified()返回0；仅做轻量stat，不读取文件内容。
     */
    private static long buildLocalVersion(long code, String resourcePath) {
        long version = 0;
        for (String ex : Constants.IMAGE_EX) {
            version = version * 31
                    + new File(resourcePath, Constants.CORE_IMAGE_PATH + "/" + code + ex).lastModified();
            version = version * 31
                    + new File(resourcePath, Constants.CORE_EXPANSIONS_IMAGE_PATH + "/" + code + ex).lastModified();
        }
        return version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CardImageModel that = (CardImageModel) o;
        return code == that.code
                && localVersion == that.localVersion
                && Objects.equals(resourcePath, that.resourcePath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(code, resourcePath, localVersion);
    }

    @Override
    public void updateDiskCacheKey(@NonNull MessageDigest messageDigest) {
        messageDigest.update(ByteBuffer.allocate(16).putLong(code).putLong(localVersion).array());
        if (resourcePath != null) {
            messageDigest.update(resourcePath.getBytes(CHARSET));
        }
    }

    @Override
    public String toString() {
        return "CardImageModel{code=" + code + ", v=" + localVersion + '}';
    }
}