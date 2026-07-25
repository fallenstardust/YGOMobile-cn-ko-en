package cn.garymb.ygomobile.render;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.util.LruCache;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;

public class TextureLoader {
    private static final String TAG = "TextureLoader";
    private static final int CACHE_SIZE = 32 * 1024 * 1024;
    private static final int CARD_DECODE_MAX_W = 256;

    private static TextureLoader instance;
    private final LruCache<String, Bitmap> bitmapCache;
    private final Map<String, Bitmap> permanentCache = new ConcurrentHashMap<>();
    private String textureBasePath;

    private final ExecutorService cardDecodeExecutor = Executors.newSingleThreadExecutor();
    private final Set<Long> pendingCards = ConcurrentHashMap.newKeySet();
    private final Map<String, ZipFile> cardZipCache = new ConcurrentHashMap<>();
    private volatile ZipFile picsZipFile;
    private volatile Runnable onCardLoadedListener;

    public static TextureLoader get() {
        if (instance == null) {
            synchronized (TextureLoader.class) {
                if (instance == null) {
                    instance = new TextureLoader();
                }
            }
        }
        return instance;
    }

    private TextureLoader() {
        bitmapCache = new LruCache<String, Bitmap>(CACHE_SIZE) {
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getByteCount();
            }
        };
    }

    public void init() {
        textureBasePath = AppsSettings.get().getResourcePath() + "/" + Constants.CORE_SKIN_PATH;
        preloadPermanentTextures();
    }

    private void preloadPermanentTextures() {
        String[] permanentTextures = {
                "field2.png", "field3.png",
                "field-transparent2.png", "field-transparent3.png",
                "cover.jpg", "cover2.jpg",
                "bg.jpg", "bg_menu.jpg", "bg_deck.jpg",
                "unknown.jpg",
                "lpbarf.png", "lp3.png",
                "attack.png", "chain.png", "chaintarget.png",
                "target.png", "negated.png",
                "number.png", "act.png",
                "me.jpg", "opponent.jpg",
                "selfield.png", "mask.png",
                "totalAtk.png", "tiktok.png"
        };
        for (String name : permanentTextures) {
            Bitmap bmp = loadBitmapFromFile(name);
            if (bmp != null) {
                permanentCache.put(name, bmp);
            }
        }
    }

    public Bitmap getTexture(String name) {
        Bitmap bmp = permanentCache.get(name);
        if (bmp != null) return bmp;

        bmp = bitmapCache.get(name);
        if (bmp != null) return bmp;

        bmp = loadBitmapFromFile(name);
        if (bmp != null) {
            bitmapCache.put(name, bmp);
        }
        return bmp;
    }

    public Bitmap getFieldTexture(boolean transparent) {
        if (transparent) {
            Bitmap bmp = getTexture("field-transparent2.png");
            if (bmp == null) bmp = getTexture("field-transparent3.png");
            return bmp;
        } else {
            Bitmap bmp = getTexture("field2.png");
            if (bmp == null) bmp = getTexture("field3.png");
            return bmp;
        }
    }

    public Bitmap getBackgroundTexture(String type) {
        switch (type) {
            case "menu":
                return getTexture("bg_menu.jpg");
            case "deck":
                return getTexture("bg_deck.jpg");
            default:
                return getTexture("bg.jpg");
        }
    }

    public Bitmap getCardCover() {
        return getTexture("cover.jpg");
    }

    public Bitmap getUnknownCard() {
        return getTexture("unknown.jpg");
    }

    public Bitmap getAvatar(boolean isMe) {
        return getTexture(isMe ? "me.jpg" : "opponent.jpg");
    }

    public Bitmap getLinkMarker(int direction) {
        return getTexture("link_marker_on_" + direction + ".png");
    }

    public Bitmap getScaleTexture(boolean isLeft, int value) {
        String prefix = isLeft ? "lscale_" : "rscale_";
        return getTexture(prefix + value + ".png");
    }

    public Bitmap getExtraTexture(String name) {
        return getTexture("extra/" + name);
    }

    private Bitmap loadBitmapFromFile(String relativePath) {
        File file = new File(textureBasePath, relativePath);
        if (file.exists()) {
            FileInputStream fis = null;
            try {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inPreferredConfig = Bitmap.Config.RGB_565;
                fis = new FileInputStream(file);
                return BitmapFactory.decodeStream(fis, null, opts);
            } catch (Exception e) {
                Log.e(TAG, "Failed to load texture: " + file.getAbsolutePath(), e);
            } finally {
                if (fis != null) {
                    try { fis.close(); } catch (IOException e) { /* ignore */ }
                }
            }
        }
        return null;
    }

    /** 卡图解码完成后的回调（用于视图postInvalidate 重绘） */
    public void setOnCardLoadedListener(Runnable listener) {
        this.onCardLoadedListener = listener;
    }

    /**
     * 按卡码取卡图。命中缓存立即返回；未命中则异步解码（散装 pics、
     * expansions/pics、pics.zip、扩展包 zip/ypk），完成后触发回调重绘。
     */
    public Bitmap getCardBitmap(long code) {
        if (code <= 0) return null;
        String key = "card_" + code;
        Bitmap cached = bitmapCache.get(key);
        if (cached != null) return cached;
        if (pendingCards.add(code)) {
            cardDecodeExecutor.execute(() -> {
                try {
                    Bitmap bmp = decodeCardBitmap(code);
                    if (bmp != null) {
                        bitmapCache.put(key, bmp);
                        Runnable cb = onCardLoadedListener;
                        if (cb != null) cb.run();
                    }
                } finally {
                    pendingCards.remove(code);
                }
            });
        }
        return null;
    }

    private Bitmap decodeCardBitmap(long code) {
        String res = AppsSettings.get().getResourcePath();
        // 1. 散装 pics / expansions/pics（.jpg/.png）
        for (String ex : Constants.IMAGE_EX) {
            File f = new File(res, Constants.CORE_IMAGE_PATH + "/" + code + ex);
            if (f.exists()) return decodeFileSampled(f);
            File fe = new File(res, Constants.CORE_EXPANSIONS_IMAGE_PATH + "/" + code + ex);
            if (fe.exists()) return decodeFileSampled(fe);
        }
        // 2. 扩展包 zip/ypk
        File[] expansions = AppsSettings.get().getExpansionFiles();
        if (expansions != null) {
            for (File file : expansions) {
                if (!file.isFile()) continue;
                ZipFile zip = openCachedZip(file);
                if (zip == null) continue;
                Bitmap bmp = decodeFromZip(zip, code);
                if (bmp != null) return bmp;
            }
        }
        // 3. pics.zip
        if (picsZipFile == null) {
            File zf = new File(res, Constants.CORE_PICS_ZIP);
            if (zf.exists()) {
                try {
                    picsZipFile = new ZipFile(zf);
                } catch (IOException e) {
                    Log.e(TAG, "open pics.zip failed", e);
                }
            }
        }
        if (picsZipFile != null) {
            return decodeFromZip(picsZipFile, code);
        }
        return null;
    }

    private ZipFile openCachedZip(File file) {
        ZipFile zip = cardZipCache.get(file.getAbsolutePath());
        if (zip == null) {
            try {
                zip = new ZipFile(file);
                cardZipCache.put(file.getAbsolutePath(), zip);
            } catch (Throwable e) {
                return null;
            }
        }
        return zip;
    }

    private Bitmap decodeFromZip(ZipFile zip, long code) {
        for (String ex : Constants.IMAGE_EX) {
            ZipEntry entry = zip.getEntry(Constants.CORE_IMAGE_PATH + "/" + code + ex);
            if (entry == null) continue;
            InputStream is = null;
            try {
                is = zip.getInputStream(entry);
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) > 0) bos.write(buf, 0, n);
                byte[] data = bos.toByteArray();
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(data, 0, data.length, opts);
                opts.inSampleSize = sampleSizeFor(opts.outWidth);
                opts.inJustDecodeBounds = false;
                opts.inPreferredConfig = Bitmap.Config.RGB_565;
                return BitmapFactory.decodeByteArray(data, 0, data.length, opts);
            } catch (Exception e) {
                Log.e(TAG, "decode card in zip failed: " + code, e);
            } finally {
                if (is != null) {
                    try { is.close(); } catch (IOException ignored) { }
                }
            }
        }
        return null;
    }

    private Bitmap decodeFileSampled(File file) {
        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
            opts.inSampleSize = sampleSizeFor(opts.outWidth);
            opts.inJustDecodeBounds = false;
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
        } catch (Exception e) {
            Log.e(TAG, "decode card file failed: " + file, e);
            return null;
        }
    }

    private int sampleSizeFor(int srcWidth) {
        int sample = 1;
        while (srcWidth / (sample * 2) >= CARD_DECODE_MAX_W) sample *= 2;
        return sample;
    }

    public Bitmap loadBitmapScaled(String relativePath, int targetWidth, int targetHeight) {
        File file = new File(textureBasePath, relativePath);
        if (!file.exists()) return null;
        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(file.getAbsolutePath(), opts);

            int sampleSize = 1;
            while (opts.outWidth / sampleSize > targetWidth * 2
                    && opts.outHeight / sampleSize > targetHeight * 2) {
                sampleSize *= 2;
            }

            opts.inJustDecodeBounds = false;
            opts.inSampleSize = sampleSize;
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap raw = BitmapFactory.decodeFile(file.getAbsolutePath(), opts);
            if (raw != null && (raw.getWidth() != targetWidth || raw.getHeight() != targetHeight)) {
                Bitmap scaled = Bitmap.createScaledBitmap(raw, targetWidth, targetHeight, true);
                if (scaled != raw) raw.recycle();
                return scaled;
            }
            return raw;
        } catch (Exception e) {
            Log.e(TAG, "Failed to load scaled texture: " + relativePath, e);
        }
        return null;
    }

    public void clearCache() {
        bitmapCache.evictAll();
    }

    public void release() {
        clearCache();
        for (Bitmap bmp : permanentCache.values()) {
            if (bmp != null && !bmp.isRecycled()) {
                bmp.recycle();
            }
        }
        permanentCache.clear();
    }
}
