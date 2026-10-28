package cn.garymb.ygomobile.loader;

import android.graphics.drawable.Drawable;
import android.util.Log;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.bumptech.glide.RequestBuilder;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.lite.BuildConfig;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.utils.IOUtils;
import cn.garymb.ygomobile.utils.glide.GlideCompat;
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

    private static final String TAG = ImageLoader.class.getSimpleName();
    //全局共享的zip缓存：所有ImageLoader实例复用已打开的ZipFile，
    //避免每次bindImage都重新new ZipFile解析zip头
    private static final Map<String, ZipFile> ZIP_FILE_CACHE = new ConcurrentHashMap<>();

    public ImageLoader() {
    }

    public ImageLoader(boolean useCache) {
    }

    /**
     * 打开zip(包括ypk)并全局缓存已打开的ZipFile，可被Glide后台线程并发调用。
     */
    @Nullable
    private static ZipFile openZip(File file) {
        if (file == null || !file.isFile()) {
            return null;
        }
        String key = file.getAbsolutePath();
        ZipFile zipFile = ZIP_FILE_CACHE.get(key);
        if (zipFile != null) {
            return zipFile;
        }
        try {
            zipFile = new ZipFile(file);
        } catch (Throwable e) {
            return null;
        }
        ZipFile old = ZIP_FILE_CACHE.putIfAbsent(key, zipFile);
        if (old != null) {
            IOUtils.closeZip(zipFile);
            return old;
        }
        return zipFile;
    }

    public void resume() {

    }

    public void clearZipCache() {
        //关闭zip
        for (ZipFile zipFile : ZIP_FILE_CACHE.values()) {
            IOUtils.closeZip(zipFile);
        }
        ZIP_FILE_CACHE.clear();
    }

    @Override
    public void close() {
        if (BuildConfig.DEBUG_MODE) {
            Log.d(TAG, "close and clean cache");
        }
        clearZipCache();
    }

    /**
     * 按卡号查找并读取卡图数据。
     * 该方法由CardImageFetcher在Glide后台线程调用，请勿在主线程调用；
     * 亦供TextureLoader后台解码线程复用，保证GL卡图与bindImage同源同字节。
     * <p>查找顺序与 cdb/script 统一优先级一致（见 NativeScriptBootstrap、
     * AppsSettings#makeCdbList/#makeZipList）：expansions/pics 散文件 ＞ 卡包
     * zip/ypk（按文件名字典序倒序，靠后名包优先）＞ pics.zip ＞ 主 pics/ 散文件
     *（参考 image_manager.cpp GetImage 的 expansions 散文件优先、game.cpp 档案
     * 先挂载者优先且后接物理目录）。
     */
    @Nullable
    public static byte[] findCardImageData(long code) {
        String name = Constants.CORE_IMAGE_PATH + "/" + code;
        String name_ex = Constants.CORE_EXPANSIONS_IMAGE_PATH + "/" + code;
        String resourcePath = AppsSettings.get().getResourcePath();
        //1.expansions/pics散文件（最高优先级）
        for (String ex : Constants.IMAGE_EX) {
            byte[] data = readFile(new File(resourcePath, name_ex + ex));
            if (data != null) {
                return data;
            }
        }
        //2.卡包档案（zip/ypk）倒序遍历：字典序靠后的包优先
        File[] files = AppsSettings.get().getExpansionFiles();
        if (files != null) {
            for (int i = files.length - 1; i >= 0; i--) {
                ZipFile zipFile = openZip(files[i]);
                if (zipFile == null) {
                    continue;
                }
                byte[] data = readZipEntry(zipFile, name);
                if (data != null) {
                    return data;
                }
            }
        }
        //3.pics.zip（主资源）
        ZipFile pics = openZip(new File(resourcePath, Constants.CORE_PICS_ZIP));
        if (pics != null) {
            byte[] data = readZipEntry(pics, name);
            if (data != null) {
                return data;
            }
        }
        //4.主 pics/ 散文件（最低优先级）
        for (String ex : Constants.IMAGE_EX) {
            byte[] data = readFile(new File(resourcePath, name + ex));
            if (data != null) {
                return data;
            }
        }
        return null;
    }

    @Nullable
    private static byte[] readZipEntry(ZipFile zipFile, String nameWithoutEx) {
        InputStream inputStream = null;
        try {
            for (String ex : Constants.IMAGE_EX) {
                ZipEntry entry = zipFile.getEntry(nameWithoutEx + ex);
                if (entry != null) {
                    inputStream = zipFile.getInputStream(entry);
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    IOUtils.copy(inputStream, outputStream);
                    return outputStream.toByteArray();
                }
            }
        } catch (Exception e) {
            //按未找到处理，由Glide显示error占位图
        } finally {
            IOUtils.close(inputStream);
        }
        return null;
    }

    /**
     * 按场地魔法卡号查找场地背景图数据（image_manager.cpp GetTextureField 同源路径）：
     * 与 {@link #findCardImageData} 同一统一优先级——expansions/pics/field 散文件 ＞
     * 卡包 zip/ypk（倒序，靠后名包优先）＞ pics.zip ＞ 主 pics/field 散文件。
     */
    @Nullable
    public static byte[] findFieldImageData(long code) {
        String name = Constants.CORE_IMAGE_PATH + "/" + Constants.CORE_IMAGE_FIELD_PATH + "/" + code;
        String name_ex = Constants.CORE_EXPANSIONS_IMAGE_PATH + "/" + Constants.CORE_IMAGE_FIELD_PATH + "/" + code;
        String resourcePath = AppsSettings.get().getResourcePath();
        for (String ex : Constants.IMAGE_EX) {
            byte[] data = readFile(new File(resourcePath, name_ex + ex));
            if (data != null) {
                return data;
            }
        }
        File[] files = AppsSettings.get().getExpansionFiles();
        if (files != null) {
            for (int i = files.length - 1; i >= 0; i--) {
                ZipFile zipFile = openZip(files[i]);
                if (zipFile == null) {
                    continue;
                }
                byte[] data = readZipEntry(zipFile, name);
                if (data != null) {
                    return data;
                }
            }
        }
        ZipFile pics = openZip(new File(resourcePath, Constants.CORE_PICS_ZIP));
        if (pics != null) {
            byte[] data = readZipEntry(pics, name);
            if (data != null) {
                return data;
            }
        }
        for (String ex : Constants.IMAGE_EX) {
            byte[] data = readFile(new File(resourcePath, name + ex));
            if (data != null) {
                return data;
            }
        }
        return null;
    }

    @Nullable
    private static byte[] readFile(File file) {
        InputStream inputStream = null;
        try {
            inputStream = new FileInputStream(file);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            IOUtils.copy(inputStream, outputStream);
            return outputStream.toByteArray();
        } catch (Exception e) {
            return null;
        } finally {
            IOUtils.close(inputStream);
        }
    }

    public void bindImage(ImageView imageview, Card card, @NonNull Type type) {
        if (card == null) {
            imageview.setImageResource(R.drawable.unknown);
        } else {
            bindImage(imageview, card.Code, null, type);
        }
    }


    public void bindImage(ImageView imageview, long code, @NonNull Type type) {
        bindImage(imageview, code, null, type);
    }

    public File getImageFile(long code) {
        for (String ex : Constants.IMAGE_EX) {
            //expansions 散文件优先，与 findCardImageData 统一优先级一致
            File file_ex = new File(AppsSettings.get().getResourcePath(), Constants.CORE_EXPANSIONS_IMAGE_PATH + "/" + code + ex);
            if (file_ex.exists()) {
                return file_ex;
            }
            File file = new File(AppsSettings.get().getResourcePath(), Constants.CORE_IMAGE_PATH + "/" + code + ex);
            if (file.exists()) {
                return file;
            }
        }
        return null;
    }

    public void bindImage(ImageView imageview, long code, Drawable pre, @NonNull Type type) {
        if (BuildConfig.DEBUG_MODE) {
            Log.v(TAG, "bind image:" + code + ", type=" + type);
        }
        try {
            //通过CardImageModelLoader在Glide后台线程完成zip/文件查找与解码，
            //主线程不再有任何磁盘IO；不预设unknown、不用过渡动画，内存缓存命中时卡图立即显示
            RequestBuilder<Drawable> resource = GlideCompat.with(imageview.getContext())
                    .load(new CardImageModel(code));
            if (pre != null) {
                resource.placeholder(pre);
            } else {
                resource.placeholder(R.drawable.unknown);
            }
            resource.error(R.drawable.unknown);
            resource.into(imageview);
        } catch (Exception e) {
            Log.e(TAG, "$", e);
        }
    }

}
