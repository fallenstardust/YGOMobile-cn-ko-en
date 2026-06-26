package cn.garymb.ygomobile.render;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;
import android.util.LruCache;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;

public class TextureLoader {
    private static final String TAG = "TextureLoader";
    private static final int CACHE_SIZE = 32 * 1024 * 1024;

    private static TextureLoader instance;
    private final LruCache<String, Bitmap> bitmapCache;
    private final Map<String, Bitmap> permanentCache = new ConcurrentHashMap<>();
    private String textureBasePath;

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
