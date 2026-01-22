package cn.garymb.ygomobile.loader;

import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.Log;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.RequestBuilder;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.signature.MediaStoreSignature;

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
import cn.garymb.ygomobile.lite.BuildConfig;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.utils.FileUtils;
import cn.garymb.ygomobile.utils.IOUtils;
import cn.garymb.ygomobile.utils.glide.GlideCompat;
import cn.garymb.ygomobile.utils.glide.StringSignature;
import ocgcore.data.Card;

public class ImageLoader implements Closeable {
    public enum Type {
        //origin size
        origin(0),
        //44x64
        @Deprecated
        mini(1),
        //177x254
        small(2),
        //531x762
        middle(3);
        private final int id;

        Type(int id) {
            this.id = id;
        }

        public int getId() {
            return id;
        }
    }

    private static class Cache {
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
    private final Map<Long, ImageLoader.Cache> zipDataCache = new ConcurrentHashMap<>();
    private ZipFile mDefaultZipFile;
    private File mPicsFile;

    public ImageLoader() {
        this(false);
    }

    public ImageLoader(boolean useCache) {
        this.useCache = useCache;
    }

    private ZipFile openPicsZip() {
        if (mPicsFile == null) {
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

    public void resume() {

    }

    public void clearZipCache() {
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
        clearZipCache();
        zipDataCache.clear();
    }

    private void bind(final byte[] data, String name, ImageView imageview, Drawable pre, @NonNull Type type) {
        if (BuildConfig.DEBUG_MODE) {
            Log.v(TAG, "bind data:" + name + ", type=" + type);
        }
        bindT(data, name, imageview, pre, type);
    }

    private void bind(final Uri uri, String name, ImageView imageview, Drawable pre, @NonNull Type type) {
        if (BuildConfig.DEBUG_MODE) {
            Log.v(TAG, "bind uri:" + name + ", type=" + type);
        }
        bindT(uri, name, imageview, pre, type);
    }

    private <T> void setDefaults(@NonNull RequestBuilder<Drawable> resource,
                                 @Nullable com.bumptech.glide.load.Key signature,
                                 @Nullable Drawable pre,
                                 @NonNull Type type) {
        if (pre != null) {
            resource.placeholder(pre);
        } else {
            resource.placeholder(R.drawable.unknown);
        }
        resource.error(R.drawable.unknown);
        resource.transition(DrawableTransitionOptions.withCrossFade());
        //都改为原图
//        if (type != Type.origin) {
//            int[] size = null;
//            switch (type) {
//                case small:
//                    size = Constants.CORE_SKIN_CARD_SMALL_SIZE;
//                    break;
//                case middle:
//                    size = Constants.CORE_SKIN_CARD_MIDDLE_SIZE;
//                    break;
//            }
//            if (size != null) {
//                resource.override(size[0], size[1]);
//            }
//        }
        if (signature != null) {
            resource.signature(signature);
        }
    }

    private <T> void bindT(final T data, String name, ImageView imageview, Drawable pre, @NonNull Type type) {
        try {
            RequestBuilder<Drawable> resource = GlideCompat.with(imageview.getContext()).load(data);
            setDefaults(resource, new StringSignature(name + ":" + type), pre, type);
            resource.into(imageview);
        } catch (Exception e) {
            Log.e(TAG, "$", e);
        }
    }

    private void bind(final File file, ImageView imageview, Drawable pre, @NonNull Type type) {
        if (BuildConfig.DEBUG_MODE) {
            Log.v(TAG, "bind file:" + file.getPath() + ", type=" + type);
        }
        try {
            RequestBuilder<Drawable> resource = GlideCompat.with(imageview.getContext()).load(file);
            setDefaults(resource,
                    new MediaStoreSignature("image/*", file.lastModified(), ImageLoader.Type.origin.getId()),
                    pre, type);
            resource.into(imageview);
        } catch (Exception e) {
            Log.e(TAG, "$", e);
        }
    }

    private boolean bindInZip(ImageView imageView, long code, Drawable pre, ZipFile zipFile, String nameWithEx, @NonNull Type type) {
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
                if (useCache) {
                    zipDataCache.put(code, new Cache(data, nameWithEx));
                }
                bind(data, nameWithEx, imageView, pre, type);
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

    public void bindImage(ImageView imageview, Card card, @NonNull Type type) {
        if (card == null) {
            imageview.setImageResource(R.drawable.unknown);
        } else {
            bindImage(imageview, card.Code, null, type);
        }
    }

    @Deprecated
    public void bindImage(ImageView imageview, long code, @NonNull Type type) {
        bindImage(imageview, code, null, type);
    }

    public File getImageFile(long code) {
        for (String ex : Constants.IMAGE_EX) {
            File file = new File(AppsSettings.get().getResourcePath(), Constants.CORE_IMAGE_PATH + "/" + code + ex);
            if (file.exists()) {
                return file;
            }
            File file_ex = new File(AppsSettings.get().getResourcePath(), Constants.CORE_EXPANSIONS_IMAGE_PATH + "/" + code + ex);
            if (file_ex.exists()) {
                return file_ex;
            }
        }
        return null;
    }

    public void bindImage(ImageView imageview, long code, Drawable pre, @NonNull Type type) {
        if (BuildConfig.DEBUG_MODE) {
            Log.v(TAG, "bind image:" + code + ", type=" + type);
        }
        imageview.setImageResource(R.drawable.unknown);
        String name = Constants.CORE_IMAGE_PATH + "/" + code;
        String name_ex = Constants.CORE_EXPANSIONS_IMAGE_PATH + "/" + code;

        //cache
        if (useCache) {
            Cache cache = zipDataCache.get(code);
            if (cache != null) {
                bind(cache.data, cache.name, imageview, pre, type);
                return;
            }
        }
        //1.zips(包括ypk)
        File[] files = AppsSettings.get().getExpansionFiles();
        cleanInValidZips();
        if (files != null) {
            for (File file : files) {
                if (file.isFile()) {
                    ZipFile zipFile = useCache ? zipFileCache.get(file.getAbsolutePath()) : null;
                    if (zipFile == null) {
                        try {
                            zipFile = new ZipFile(file);
                            if (useCache) {
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
                        if (bindInZip(imageview, code, pre, zipFile, name + ex, type)) {
                            return;
                        }
                    }
                }
            }
        }
        //2.图片文件pics文件夹
        for (String ex : Constants.IMAGE_EX) {
            File file = new File(AppsSettings.get().getResourcePath(), name + ex);
            File file_ex = new File(AppsSettings.get().getResourcePath(), name_ex + ex);
            if (file_ex.exists()) {
                bind(file_ex, imageview, pre, type);
                return;
            } else if (file.exists()) {
                bind(file, imageview, pre, type);
                return;
            }
        }
        //3.pics.zip
        ZipFile pics = openPicsZip();
        if (pics != null) {
            for (String ex : Constants.IMAGE_EX) {
                if (bindInZip(imageview, code, pre, pics, name + ex, type)) {
                    return;
                }
            }
        }

    }

}
