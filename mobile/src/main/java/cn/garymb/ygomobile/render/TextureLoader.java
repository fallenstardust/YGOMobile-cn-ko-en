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

    /** 表情图集裁剪缓存，对齐 ImageManager::emoticons */
    private final Map<String, Bitmap> emoticonCache = new ConcurrentHashMap<>();

    /** 表情键名与 4x4 图集网格顺序，对齐 image_manager.cpp emoticonRects */
    private static final String[] EMOTICON_KEYS = {
            "&laugh", "&ridiculous", "&stick_tongue", "&reluctant",
            "&sweat", "&confused", "&surprised", "&bawl",
            "&angry", "&rage", "&sneaky", "&obedient",
            "&good", "&cool", "&despise", "&shy"
    };

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
        preloadEmoticons();
    }

    /**
     * 与 image_manager.cpp ImageManager::Initial 加载清单对齐：
     * 场地绘制素材 / LP条/ 手牌背景 / 图标 / 连接标记 / 灵摆刻度 / extra 功能图标
     */
    private void preloadPermanentTextures() {
        String[] permanentTextures = {
                // 场地与背景 (tField / tFieldTransparent / tBackGround*)
                "field2.png", "field3.png",
                "field-transparent2.png", "field-transparent3.png",
                "bg.jpg", "bg_menu.jpg", "bg_deck.jpg",
                // 卡背与未知卡图 (tCover / tUnknown)
                "cover.jpg", "cover2.jpg",
                "unknown.jpg",
                // 场上状态标记 (tAct/tAttack/tChain/tNegated/tNumber/tEquip/tTarget/tChainTarget/tMask)
                "act.png", "attack.png", "chain.png", "negated.png",
                "number.png", "equip.png", "target.png", "chaintarget.png",
                "mask.png",
                // LP 条 (tLPBar/tLPFrame/tLPBarFrame)
                "lp3.png", "lpf.png", "lpbarf.png",
                // 禁限/OT/卡片类型图标 (tLim/tOT/tCardType)
                "icon_lim.png", "ot.png", "cardtype.png",
                // 手牌背景 (tHand[0..2])
                "f1.jpg", "f2.jpg", "f3.jpg",
                // 攻击合计/选择区域/计时 (tTotalAtk/tSelField/tClock)
                "totalAtk.png", "selfield.png", "tiktok.png",
                // 头像 (tAvatar)
                "me.jpg", "opponent.jpg",
                // 连接标记 (tSelFieldLinkArrows[1..4,6..9])
                "link_marker_on_1.png", "link_marker_on_2.png",
                "link_marker_on_3.png", "link_marker_on_4.png",
                "link_marker_on_6.png", "link_marker_on_7.png",
                "link_marker_on_8.png", "link_marker_on_9.png",
                // extra 功能图标 (tSettings/tLogs/tMute/tPlay/tTalk/tOneX/tDoubleX/tShut/tClose/tEmoticon/tGSC)
                "extra/tsettings.png", "extra/tlogs.png", "extra/tmute.png",
                "extra/tplay.png", "extra/ttalk.png", "extra/tonex.png",
                "extra/tdoublex.png", "extra/tshut.png", "extra/tclose.png",
                "extra/temoticon.png", "extra/gsc.png"
        };
        for (String name : permanentTextures) {
            Bitmap bmp = loadBitmapFromFile(name);
            if (bmp != null) {
                permanentCache.put(name, bmp);
            }
        }
        // 灵摆刻度 (tLScale/tRScale[0..13])
        for (int i = 0; i < 14; i++) {
            String l = "extra/lscale_" + i + ".png";
            String r = "extra/rscale_" + i + ".png";
            Bitmap lb = loadBitmapFromFile(l);
            if (lb != null) permanentCache.put(l, lb);
            Bitmap rb = loadBitmapFromFile(r);
            if (rb != null) permanentCache.put(r, rb);
        }
    }

    /**
     * 裁剪 extra/emoticons.png 4x4 表情图集，对齐 ImageManager 中
     * emoticonRects 的裁剪逻辑（每格为图集的 1/4 宽 x 1/4 高）
     */
    private void preloadEmoticons() {
        Bitmap sheet = loadBitmapFromFile("extra/emoticons.png");
        if (sheet == null) return;
        int cellW = sheet.getWidth() / 4;
        int cellH = sheet.getHeight() / 4;
        if (cellW <= 0 || cellH <= 0) return;
        for (int i = 0; i < EMOTICON_KEYS.length; i++) {
            int col = i % 4;
            int row = i / 4;
            try {
                Bitmap cell = Bitmap.createBitmap(sheet, col * cellW, row * cellH, cellW, cellH);
                emoticonCache.put(EMOTICON_KEYS[i], cell);
            } catch (Exception e) {
                Log.e(TAG, "crop emoticon failed: " + EMOTICON_KEYS[i], e);
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

    /**
     * 对齐 ImageManager::tCover[0/1]：己方 cover.jpg，对方 cover2.jpg，缺失时回退 cover.jpg
     */
    public Bitmap getCardCover(boolean opponent) {
        if (opponent) {
            Bitmap bmp = getTexture("cover2.jpg");
            if (bmp != null) return bmp;
        }
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

    // === 与 image_manager.cpp 字段一一对应的语义化 getter，供 GameFieldView 绘制取用 ===

    /** tAct：可发动效果指示 */
    public Bitmap getActTexture() {
        return getTexture("act.png");
    }

    /** tAttack：攻击指示箭头 */
    public Bitmap getAttackTexture() {
        return getTexture("attack.png");
    }

    /** tChain：连锁标记 */
    public Bitmap getChainTexture() {
        return getTexture("chain.png");
    }

    /** tNegated：效果无效标记 */
    public Bitmap getNegatedTexture() {
        return getTexture("negated.png");
    }

    /** tNumber：连锁序号数字条 */
    public Bitmap getNumberTexture() {
        return getTexture("number.png");
    }

    /** tEquip：装备指示 */
    public Bitmap getEquipTexture() {
        return getTexture("equip.png");
    }

    /** tTarget：对象指示 */
    public Bitmap getTargetTexture() {
        return getTexture("target.png");
    }

    /** tChainTarget：连锁对象指示 */
    public Bitmap getChainTargetTexture() {
        return getTexture("chaintarget.png");
    }

    /** tMask：遮罩 */
    public Bitmap getMaskTexture() {
        return getTexture("mask.png");
    }

    /** tLPBar / tLPFrame / tLPBarFrame：LP 条、LP 框 */
    public Bitmap getLpBar() {
        return getTexture("lp3.png");
    }

    public Bitmap getLpFrame() {
        return getTexture("lpf.png");
    }

    public Bitmap getLpBarFrame() {
        return getTexture("lpbarf.png");
    }

    /** tLim：禁限图标  tOT：OT 图标  tCardType：卡片类型条 */
    public Bitmap getLimitIcon() {
        return getTexture("icon_lim.png");
    }

    public Bitmap getOtIcon() {
        return getTexture("ot.png");
    }

    public Bitmap getCardTypeTexture() {
        return getTexture("cardtype.png");
    }

    /** tHand[0..2]：猜拳手势f1/f2/f3.jpg，index 取 0-2 */
    public Bitmap getHandTexture(int index) {
        if (index < 0 || index > 2) return null;
        return getTexture("f" + (index + 1) + ".jpg");
    }

    /** tTotalAtk：攻击合计  tSelField：可选区域高亮  tClock：计时 */
    public Bitmap getTotalAtkTexture() {
        return getTexture("totalAtk.png");
    }

    public Bitmap getSelFieldTexture() {
        return getTexture("selfield.png");
    }

    public Bitmap getClockTexture() {
        return getTexture("tiktok.png");
    }

    /** tGSC */
    public Bitmap getGscTexture() {
        return getTexture("extra/gsc.png");
    }

    /** emoticons：按键名取裁剪后的表情，如 "&laugh"、"&angry" */
    public Bitmap getEmoticon(String key) {
        return emoticonCache.get(key);
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
        for (Bitmap bmp : emoticonCache.values()) {
            if (bmp != null && !bmp.isRecycled()) {
                bmp.recycle();
            }
        }
        emoticonCache.clear();
    }
}
