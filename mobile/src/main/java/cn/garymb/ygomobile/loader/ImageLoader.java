package cn.garymb.ygomobile.loader;

import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.Log;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.DrawableTypeRequest;
import com.bumptech.glide.load.ResourceDecoder;
import com.bumptech.glide.load.engine.Resource;
import com.bumptech.glide.load.engine.bitmap_recycle.LruBitmapPool;
import com.bumptech.glide.load.model.ImageVideoWrapper;
import com.bumptech.glide.load.resource.bitmap.BitmapResource;
import com.bumptech.glide.load.resource.gifbitmap.GifBitmapWrapper;
import com.bumptech.glide.load.resource.gifbitmap.GifBitmapWrapperResource;
import com.bumptech.glide.signature.MediaStoreSignature;
import com.bumptech.glide.signature.StringSignature;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.core.IrrlichtBridge;
import cn.garymb.ygomobile.lite.BuildConfig;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.utils.FileUtils;
import cn.garymb.ygomobile.utils.IOUtils;
import cn.garymb.ygomobile.utils.NetUtils;

import static com.bumptech.glide.Glide.with;

public class ImageLoader implements Closeable {
    public static final ImageLoader sImageLoader = new ImageLoader();

    public static ImageLoader get() {
        return sImageLoader;
    }

    private static class Cache{
        private final byte[] data;
        private final String name;

        public Cache(byte[] data, String name) {
            this.data = data;
            this.name = name;
        }
    }

    private final boolean useCache;
    private static final String TAG = ImageLoader.class.getSimpleName();
    private final Map<String, ZipFile> zipFileCache = new ConcurrentHashMap<>();
    private final LruBitmapPool mLruBitmapPool = new LruBitmapPool(128);
    private final Map<Long, Cache> zipDataCache = new ConcurrentHashMap<>();
    private ZipFile mDefaultZipFile;
    private File mPicsFile;
    public ImageLoader(){
        this(false);
    }

    public ImageLoader(boolean useCache) {
        this.useCache = useCache;
    }

    private ZipFile openPicsZip() {
        if(mPicsFile == null) {
            mPicsFile = new File(AppsSettings.get().getResourcePath(), Constants.CORE_PICS_ZIP);
        }
        if (mDefaultZipFile == null) {
            if (mPicsFile.exists()) {
                try {
                    mDefaultZipFile = new ZipFile(mPicsFile);
                } catch (IOException e) {
                    //Ignore
                }
            }
        }
        return mDefaultZipFile;
    }

    public void resume(){

    }

    public void pause(){
        //关闭zip
        for (ZipFile zipFile : zipFileCache.values()) {
            IOUtils.closeZip(zipFile);
        }
        zipFileCache.clear();
        if (mDefaultZipFile != null) {
            IOUtils.closeZip(mDefaultZipFile);
        }
    }

    @Override
    public void close() {
        if (BuildConfig.DEBUG_MODE) {
            Log.d(TAG, "close and clean cache");
        }
        pause();
        zipDataCache.clear();
    }

    private void bind(final byte[] data, String name, ImageView imageview, Drawable pre, int[] size) {
        if (BuildConfig.DEBUG_MODE) {
            Log.v(TAG, "bind data:" + name + ", size=" + (size == null ? "null" : size[0] + "x" + size[1]));
        }
        bindT(data, name, imageview, pre, size);
    }

    private void bind(final Uri uri, String name, ImageView imageview, Drawable pre, int[] size) {
        if (BuildConfig.DEBUG_MODE) {
            Log.v(TAG, "bind uri:" + name + ", size=" + (size == null ? "null" : size[0] + "x" + size[1]));
        }
        bindT(uri, name, imageview, pre, size);
    }

    private <T> void setDefaults(@NonNull DrawableTypeRequest<T> resource, String name,
                                 @Nullable com.bumptech.glide.load.Key signature,
                                 @Nullable Drawable pre,
                                 @Nullable int[] size) {
        if (pre != null) {
            resource.placeholder(pre);
        } else {
            resource.placeholder(R.drawable.unknown);
        }
        resource.error(R.drawable.unknown);
        resource.animate(R.anim.push_in);
        if (size != null) {
            resource.override(size[0], size[1]);
        }
        if(signature != null) {
            resource.signature(signature);
        }
        String ex = FileUtils.getFileExpansion(name);
        if ("bpg".equals(ex)) {
            resource.decoder(new BpgResourceDecoder(name, mLruBitmapPool));
        }
    }

    private <T> void bindT(final T data, String name, ImageView imageview, Drawable pre, int[] size) {
        try {
            DrawableTypeRequest<T> resource = with(imageview.getContext()).load(data);
            if (size == null) {
                setDefaults(resource, name, new StringSignature(name), pre, size);
                resource.signature(new StringSignature(name));
            } else {
                setDefaults(resource, name, new StringSignature(name + ":" + size[0] + "x" + size[1]), pre, size);
            }
            resource.into(imageview);
        } catch (Exception e) {
            Log.e(TAG, "$", e);
        }
    }

    private void bind(final File file, ImageView imageview, Drawable pre, int[] size) {
        if (BuildConfig.DEBUG_MODE) {
            Log.v(TAG, "bind file:" + file.getPath() + ", size=" + (size == null ? "null" : size[0] + "x" + size[1]));
        }
        try {
            DrawableTypeRequest<File> resource = with(imageview.getContext()).load(file);
            long key = size == null ? 0:(size[0] * size[1]);
            setDefaults(resource, file.getName(),
                    new MediaStoreSignature("image/*",
                            file.lastModified() + key, 0), pre, size);
            resource.into(imageview);
        } catch (Exception e) {
            Log.e(TAG, "$", e);
        }
    }

    private boolean bindInZip(ImageView imageView, long code, Drawable pre, ZipFile zipFile, String nameWithEx, int[] size) {
        ZipEntry entry;
        InputStream inputStream = null;
        ByteArrayOutputStream outputStream;
        boolean bind = false;
        try {
            entry = zipFile.getEntry(nameWithEx);
            if (entry != null) {
                inputStream = zipFile.getInputStream(entry);
                outputStream = new ByteArrayOutputStream();
                IOUtils.copy(inputStream, outputStream);
                byte[] data = outputStream.toByteArray();
                if(useCache){
                    zipDataCache.put(code, new Cache(data, nameWithEx));
                }
                bind(data, nameWithEx, imageView, pre, size);
                bind = true;
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            IOUtils.close(inputStream);
        }
        return bind;
    }

    private void cleanInValidZips() {
        List<String> removes = new ArrayList<>();
        for (String old : zipFileCache.keySet()) {
            if (!FileUtils.isExist(old)) {
                removes.add(old);
            }
        }
        for (String key : removes) {
            zipFileCache.remove(key);
        }
    }


    /**
     * 177x254
     */
    public void bindImage(ImageView imageview, long code) {
        bindImage(imageview, code, null, Constants.CORE_SKIN_CARD_MINI_SIZE);
    }

    /**
     * @param big true则是原始大小
     */
    @Deprecated
    public void bindImage(ImageView imageview, long code, Drawable pre, boolean big) {
        bindImage(imageview, code, pre, big ? null : Constants.CORE_SKIN_CARD_MINI_SIZE);
    }

    public void bindImage(ImageView imageview, long code, Drawable pre, int[] size) {
        if (BuildConfig.DEBUG_MODE) {
            Log.v(TAG, "bind image:" + code + ", size=" + (size == null ? "null" : size[0] + "x" + size[1]));
        }
        String name = Constants.CORE_IMAGE_PATH + "/" + code;
        String name_ex = Constants.CORE_EXPANSIONS_IMAGE_PATH + "/" + code;
        //1.图片文件
        for (String ex : Constants.IMAGE_EX) {
            File file = new File(AppsSettings.get().getResourcePath(), name + ex);
            File file_ex = new File(AppsSettings.get().getResourcePath(), name_ex + ex);
            if (file_ex.exists()) {
                bind(file_ex, imageview, pre, size);
                return;
            } else if (file.exists()) {
                bind(file, imageview, pre, size);
                return;
            }
        }
        //cache
        if(useCache) {
            Cache cache = zipDataCache.get(code);
            if (cache != null) {
                bind(cache.data, cache.name, imageview, pre, size);
                return;
            }
        }
        //2.zip
        {
            ZipFile pics = openPicsZip();
            if (pics != null) {
                for (String ex : Constants.IMAGE_EX) {
                    if (bindInZip(imageview, code, pre, pics, name + ex, size)) {
                        return;
                    }
                }
            }
        }
        //3.
        //zips
        File[] files = new File(AppsSettings.get().getResourcePath(), Constants.CORE_EXPANSIONS)
                .listFiles((dir, name1) -> name1.endsWith(".zip") || name1.endsWith(".ypk"));

        cleanInValidZips();

        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    ZipFile zipFile = useCache ?  zipFileCache.get(file.getAbsolutePath()) : null;
                    if (zipFile == null) {
                        try {
                            zipFile = new ZipFile(file);
                            if(useCache) {
                                zipFileCache.put(file.getAbsolutePath(), zipFile);
                            }
                        } catch (Throwable e) {
                            //Ignore
                        }
                    }
                    if (zipFile == null) {
                        continue;
                    }
                    for (String ex : Constants.IMAGE_EX) {
                        if (bindInZip(imageview, code, pre, zipFile, name + ex, size)) {
                            return;
                        }
                    }
                }
            }
        }
        //4 http
        if (Constants.NETWORK_IMAGE && NetUtils.isWifiConnected(imageview.getContext())) {
            bind(Uri.parse(String.format(Constants.IMAGE_URL, "" + code)), code + ".jpg", imageview, pre, size);
        } else {
            imageview.setImageResource(R.drawable.unknown);
        }
    }

    private static class BpgResourceDecoder implements ResourceDecoder<ImageVideoWrapper, GifBitmapWrapper> {
        private final String id;
        private final LruBitmapPool mLruBitmapPool;

        private BpgResourceDecoder(String id, LruBitmapPool lruBitmapPool) {
            this.id = id;
            this.mLruBitmapPool = lruBitmapPool;
        }

        @Override
        public Resource<GifBitmapWrapper> decode(ImageVideoWrapper source, int width, int height) {
            Bitmap bitmap = IrrlichtBridge.getBpgImage(source.getStream(), Bitmap.Config.RGB_565);
            if (bitmap == null) {
                return null;
            }
            BitmapResource resource = new BitmapResource(bitmap, mLruBitmapPool);
            return new GifBitmapWrapperResource(new GifBitmapWrapper(resource, null));
        }

        @Override
        public String getId() {
            return id;
        }
    }
}
