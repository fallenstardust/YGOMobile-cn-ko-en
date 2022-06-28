package cn.garymb.ygomobile.utils.glide;

import android.graphics.Bitmap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.load.Options;
import com.bumptech.glide.load.ResourceDecoder;
import com.bumptech.glide.load.engine.Resource;
import com.bumptech.glide.load.engine.bitmap_recycle.LruBitmapPool;
import com.bumptech.glide.load.resource.bitmap.BitmapResource;

import java.io.File;
import java.io.FileInputStream;

import cn.garymb.ygomobile.core.IrrlichtBridge;
import cn.garymb.ygomobile.utils.IOUtils;

public class BpgResourceDecoder implements ResourceDecoder<File, Bitmap> {
    private final LruBitmapPool mLruBitmapPool;

    public BpgResourceDecoder(LruBitmapPool lruBitmapPool) {
        this.mLruBitmapPool = lruBitmapPool;
    }

    @Override
    public boolean handles(@NonNull File source, @NonNull Options options) {
        return source.getName().toLowerCase().endsWith(".bpg");
    }

    @Nullable
    @Override
    public Resource<Bitmap> decode(@NonNull File source, int width, int height, @NonNull Options options) {
//        Log.v("ImageLoader", "decode:" + source);
        FileInputStream input = null;
        Bitmap bitmap = null;
        try {
            input = new FileInputStream(source);
            bitmap = IrrlichtBridge.getBpgImage(input, Bitmap.Config.RGB_565);
        } catch (Throwable e) {
            //Ignore
        } finally {
            IOUtils.close(input);
        }
        if (bitmap == null) {
            return null;
        }
        return new BitmapResource(bitmap, mLruBitmapPool);
    }
}