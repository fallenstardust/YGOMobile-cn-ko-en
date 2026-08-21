package cn.garymb.ygomobile.loader;

import androidx.annotation.NonNull;

import com.bumptech.glide.Priority;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.data.DataFetcher;

import java.io.FileNotFoundException;
import java.nio.ByteBuffer;

/**
 * 在Glide后台线程完成卡图查找与读取，避免阻塞主线程。
 */
public class CardImageFetcher implements DataFetcher<ByteBuffer> {
    private final long code;

    public CardImageFetcher(long code) {
        this.code = code;
    }

    @Override
    public void loadData(@NonNull Priority priority, @NonNull DataCallback<? super ByteBuffer> callback) {
        byte[] data = ImageLoader.findCardImageData(code);
        if (data != null) {
            callback.onDataReady(ByteBuffer.wrap(data));
        } else {
            callback.onLoadFailed(new FileNotFoundException("card image not found: " + code));
        }
    }

    @Override
    public void cleanup() {
    }

    @Override
    public void cancel() {
    }

    @NonNull
    @Override
    public Class<ByteBuffer> getDataClass() {
        return ByteBuffer.class;
    }

    @NonNull
    @Override
    public DataSource getDataSource() {
        return DataSource.LOCAL;
    }
}