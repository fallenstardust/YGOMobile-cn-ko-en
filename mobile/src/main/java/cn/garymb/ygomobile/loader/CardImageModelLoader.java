package cn.garymb.ygomobile.loader;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.model.ModelLoader;
import com.bumptech.glide.load.model.ModelLoaderFactory;
import com.bumptech.glide.load.model.MultiModelLoaderFactory;

import java.nio.ByteBuffer;

/**
 * 卡图ModelLoader：把卡号模型交给CardImageFetcher在后台线程读取。
 */
public class CardImageModelLoader implements ModelLoader<CardImageModel, ByteBuffer> {

    public static class Factory implements ModelLoaderFactory<CardImageModel, ByteBuffer> {
        @NonNull
        @Override
        public ModelLoader<CardImageModel, ByteBuffer> build(@NonNull MultiModelLoaderFactory multiFactory) {
            return new CardImageModelLoader();
        }

        @Override
        public void teardown() {
        }
    }

    @Nullable
    @Override
    public LoadData<ByteBuffer> buildLoadData(@NonNull CardImageModel model, int width, int height,
                                              @NonNull Options options) {
        return new LoadData<>(model, new CardImageFetcher(model.getCode()));
    }

    @Override
    public boolean handles(@NonNull CardImageModel model) {
        return true;
    }
}