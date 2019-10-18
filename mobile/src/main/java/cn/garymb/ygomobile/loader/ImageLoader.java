package cn.garymb.ygomobile.loader;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.Log;
import android.widget.ImageView;

import com.bumptech.glide.DrawableTypeRequest;
import com.bumptech.glide.load.ResourceDecoder;
import com.bumptech.glide.load.engine.Resource;
import com.bumptech.glide.load.engine.bitmap_recycle.LruBitmapPool;
import com.bumptech.glide.load.model.ImageVideoWrapper;
import com.bumptech.glide.load.resource.bitmap.BitmapResource;
import com.bumptech.glide.load.resource.gifbitmap.GifBitmapWrapper;
import com.bumptech.glide.load.resource.gifbitmap.GifBitmapWrapperResource;
import com.bumptech.glide.request.target.GlideDrawableImageViewTarget;
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
import cn.garymb.ygomobile.core.BpgImage;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.utils.BitmapUtil;
import cn.garymb.ygomobile.utils.IOUtils;
import cn.garymb.ygomobile.utils.MD5Util;
import cn.garymb.ygomobile.utils.NetUtils;

import static cn.garymb.ygomobile.Constants.CORE_SKIN_BG_SIZE;
import static com.bumptech.glide.Glide.with;

public class ImageLoader implements Closeable {
    private static final String TAG = ImageLoader.class.getSimpleName();
    private ZipFile mZipFile;
    private List<ZipFile> zipFileList;
    private LruBitmapPool mLruBitmapPool;
    //    private ExecutorService mExecutorService = Executors.newSingleThreadExecutor();
    private boolean isClose = false;
    private Context mContext;
    private static final Map<Context, ImageLoader> IMAGE_LOADER_MAP = new ConcurrentHashMap<>();

    public static ImageLoader get(Context context) {
        ImageLoader imageLoader = IMAGE_LOADER_MAP.get(context);
        if (imageLoader == null) {
            synchronized (IMAGE_LOADER_MAP) {
                imageLoader = IMAGE_LOADER_MAP.get(context);
                if (imageLoader == null) {
                    imageLoader = new ImageLoader(context);
                    IMAGE_LOADER_MAP.put(context, imageLoader);
                }
            }
        }
        return imageLoader;
    }

    public static void onDestory(Context context) {
        synchronized (IMAGE_LOADER_MAP) {
            IMAGE_LOADER_MAP.remove(context);
        }
    }

    private ImageLoader(Context context) {
        mContext = context;
        mLruBitmapPool = new LruBitmapPool(100);
        zipFileList=new ArrayList<>();
    }

    private class BpgResourceDecoder implements ResourceDecoder<ImageVideoWrapper, GifBitmapWrapper> {
        String id;

        private BpgResourceDecoder(String id) {
            this.id = id;
        }

        @Override
        public Resource<GifBitmapWrapper> decode(ImageVideoWrapper source, int width, int height) throws IOException {
//            Log.i("kk", "decode source:"+source);
            Bitmap bitmap = BpgImage.getBpgImage(source.getStream(), Bitmap.Config.RGB_565);
//            Log.i("kk", "decode bitmap:"+bitmap);
            BitmapResource resource = new BitmapResource(bitmap, mLruBitmapPool);
            return new GifBitmapWrapperResource(new GifBitmapWrapper(resource, null));
        }

        @Override
        public String getId() {
            return id;
        }
    }

    @Override
    public void close() throws IOException {
        isClose = true;
//        if (!mExecutorService.isShutdown()) {
//            mExecutorService.shutdown();
//        }
    }

    private Bitmap loadImage(String path, int w, int h) {
        File file = new File(path);
        if (file.exists()) {
            return BitmapUtil.getBitmapFromFile(file.getAbsolutePath(), CORE_SKIN_BG_SIZE[0], CORE_SKIN_BG_SIZE[1]);
        }
        return null;
    }

    private void bind(byte[] data, ImageView imageview, boolean isbpg, long code, Drawable pre, boolean isBig) {
        DrawableTypeRequest<byte[]> resource = with(mContext).load(data);
        if (pre != null) {
            resource.placeholder(pre);
        } else {
            resource.placeholder(R.drawable.unknown);
        }
        resource.error(R.drawable.unknown);
        resource.animate(R.anim.push_in);
//        if(isbpg){
//            resource.override(Constants.CORE_SKIN_CARD_COVER_SIZE[0], Constants.CORE_SKIN_CARD_COVER_SIZE[1]);
//        }
        resource.signature(new StringSignature(MD5Util.getStringMD5(data.length + "_" + code + "_" + isBig)));
        if (isbpg) {
            resource.decoder(new BpgResourceDecoder("bpg@" + code));
        }
        resource.into(imageview);
    }

    public void bind(final File file, ImageView imageview, boolean isbpg, long code, Drawable pre, boolean isBig) {
        try {
            DrawableTypeRequest<File> resource = with(mContext).load(file);
            if (pre != null) {
                resource.placeholder(pre);
            } else {
                resource.placeholder(R.drawable.unknown);
            }
            resource.error(R.drawable.unknown);
            resource.animate(R.anim.push_in);
            resource.signature(new StringSignature(MD5Util.getStringMD5(file.length() + code + "_" + isBig)));
            if (isbpg) {
                resource.decoder(new BpgResourceDecoder("bpg@" + code));
            }
            resource.into(imageview);
        } catch (Exception e) {
            Log.e(TAG, "$", e);
        }
    }

    private void bind(final String url, ImageView imageview, long code, Drawable pre, boolean isBig) {
        DrawableTypeRequest<Uri> resource = with(mContext).load(Uri.parse(url));
        if (pre != null) {
            resource.placeholder(pre);
        } else {
            resource.placeholder(R.drawable.unknown);
        }
        resource.error(R.drawable.unknown);
        resource.override(Constants.CORE_SKIN_CARD_COVER_SIZE[0], Constants.CORE_SKIN_CARD_COVER_SIZE[1]);
        resource.signature(new StringSignature("" + code));
        resource.into(new GlideDrawableImageViewTarget(imageview));
    }

    public void bindImage(ImageView imageview, long code) {
        bindImage(imageview, code, null);
    }

    public void bindImage(ImageView imageview, long code, Drawable pre) {
        bindImage(imageview, code, pre, false);
    }


    public void bindImage(ImageView imageview, long code, Drawable pre, boolean isBig) {
        String name = Constants.CORE_IMAGE_PATH + "/" + code;
        String name_ex = Constants.CORE_EXPANSIONS_IMAGE_PATH + "/" + code;
        String path = AppsSettings.get().getResourcePath();
        boolean bind = false;
        File zip = new File(path, Constants.CORE_PICS_ZIP);
        List<File> zipList=new ArrayList<>();
        for (String ex : Constants.IMAGE_EX) {
            File file = new File(AppsSettings.get().getResourcePath(), name + ex);
            File file_ex = new File(AppsSettings.get().getResourcePath(), name_ex + ex);
            if (file_ex.exists()) {
                bind(file_ex, imageview, Constants.BPG.equals(ex), code, pre, isBig);
                bind = true;
                return;				
            } else
            if (file.exists()) {
                bind(file, imageview, Constants.BPG.equals(ex), code, pre, isBig);
                bind = true;
                return;
            }
        }
        if (zip.exists()) {
            ZipEntry entry = null;
            InputStream inputStream = null;
            ByteArrayOutputStream outputStream = null;
            try {
                if (mZipFile == null) {
                    mZipFile = new ZipFile(zip);
                }
                for (String ex : Constants.IMAGE_EX) {
                    entry = mZipFile.getEntry(name + ex);
                    if (entry != null) {
                        inputStream = mZipFile.getInputStream(entry);
                        outputStream = new ByteArrayOutputStream();
                        IOUtils.copy(inputStream, outputStream);
                        bind(outputStream.toByteArray(), imageview, Constants.BPG.equals(ex), code, pre, isBig);
                        bind = true;
                        break;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                IOUtils.close(inputStream);
            }
        }
        if (!bind) {
            File[] files = new File(AppsSettings.get().getResourcePath(), Constants.CORE_EXPANSIONS).listFiles();
            if (files != null) {
                for (File file :files) {
                    if (file.isFile() && file.getName().endsWith(".zip")) {
                        ZipEntry entry = null;
                        InputStream inputStream = null;
                        ByteArrayOutputStream outputStream = null;
                        try {
                            ZipFile zipFile = null;
                            for(ZipFile zipFile1:zipFileList){
                                if (zipFile1.getName().equals(file.getAbsolutePath())){
                                    zipFile=zipFile1;
                                    break;
                                }
                            }
                            if (zipFile==null){
                                zipFile=new ZipFile(file.getAbsoluteFile());
                                zipFileList.add(zipFile);
                            }
                            for (String ex : Constants.IMAGE_EX) {
                                entry = zipFile.getEntry(name + ex);
                                if (entry != null) {
                                    inputStream = zipFile.getInputStream(entry);
                                    outputStream = new ByteArrayOutputStream();
                                    IOUtils.copy(inputStream, outputStream);
                                    bind(outputStream.toByteArray(), imageview, Constants.BPG.equals(ex), code, pre, isBig);
                                    bind = true;
                                    break;
                                }
                            }
                            if (bind)
                                break;
                        } catch (Exception e) {
                            e.printStackTrace();
                        } finally {
                            IOUtils.close(inputStream);
                        }
                    }
                }
            }
        }
        if (!bind) {
            if (Constants.NETWORK_IMAGE && NetUtils.isWifiConnected(imageview.getContext())) {
                bind(String.format(Constants.IMAGE_URL, "" + code), imageview, code, pre, isBig);
            } else {
                imageview.setImageResource(R.drawable.unknown);
            }
        }
    }
}
