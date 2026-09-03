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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.loader.ImageLoader;

public class TextureLoader {
    private static final String TAG = "TextureLoader";
    private static final int CACHE_SIZE = 32 * 1024 * 1024;
    private static final int CARD_DECODE_MAX_W = 256;
    // 卡面标准比例 177:254，解码后偏离超过 2% 时中心裁剪矫正，防止非标准图源拉伸扭曲
    private static final float CARD_ASPECT = 177f / 254f;

    private static TextureLoader instance;
    private final LruCache<String, Bitmap> bitmapCache;
    private final Map<String, Bitmap> permanentCache = new ConcurrentHashMap<>();
    private String textureBasePath;

    private final ExecutorService cardDecodeExecutor = Executors.newSingleThreadExecutor();
    private final Set<Long> pendingCards = ConcurrentHashMap.newKeySet();
    private volatile Runnable onCardLoadedListener;

    /** 表情图集裁剪缓存，对齐 ImageManager::emoticons */
    private final Map<String, Bitmap> emoticonCache = new ConcurrentHashMap<>();

    /** 表情键名与 4x4 图集网格顺序，对齐 image_manager.cpp emoticonRects / emoticonCodes（公开供 EmotionDialog 复用） */
    public static final String[] EMOTICON_KEYS = {
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
        };
        for (String name : permanentTextures) {
            Bitmap bmp = loadBitmapFromFile(name);
            if (bmp != null) {
                permanentCache.put(name, bmp);
            }
        }
        // extra 功能图标 (tSettings/tLogs/tMute/tPlay/tTalk/tOneX/tDoubleX/tShut/tClose/tEmoticon/tGSC)
        // 均含 alpha 通道，必须按 ARGB_8888 解码（RGB_565 透明区域会变黑底）
        String[] extraIcons = {
                "extra/tsettings.png", "extra/tlogs.png", "extra/tmute.png",
                "extra/tplay.png", "extra/ttalk.png", "extra/tonex.png",
                "extra/tdoublex.png", "extra/tshut.png", "extra/tclose.png",
                "extra/temoticon.png", "extra/gsc.png"
        };
        for (String name : extraIcons) {
            Bitmap bmp = loadBitmapFromFile(name, Bitmap.Config.ARGB_8888);
            if (bmp != null) {
                permanentCache.put(name, bmp);
            }
        }
        // 灵摆刻度 (tLScale/tRScale[0..13])，含透明通道，必须 ARGB_8888
        for (int i = 0; i < 14; i++) {
            String l = "extra/lscale_" + i + ".png";
            String r = "extra/rscale_" + i + ".png";
            Bitmap lb = loadBitmapFromFile(l, Bitmap.Config.ARGB_8888);
            if (lb != null) permanentCache.put(l, lb);
            Bitmap rb = loadBitmapFromFile(r, Bitmap.Config.ARGB_8888);
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

    /**
     * 对齐 gframe drawing.cpp L326/L369：rule=(duel_rule>=4)?1:0，
     * rule=1→field3/field-transparent3，rule=0→field2/field-transparent2，缺失时回退另一版本。
     * transparent 版含 alpha 通道，必须按 ARGB_8888 解码（getTexture 默认 RGB_565 会丢透明通道）。
     */
    public Bitmap getFieldTexture(int rule, boolean transparent) {
        String first, second;
        if (transparent) {
            first = rule >= 1 ? "field-transparent3.png" : "field-transparent2.png";
            second = rule >= 1 ? "field-transparent2.png" : "field-transparent3.png";
        } else {
            first = rule >= 1 ? "field3.png" : "field2.png";
            second = rule >= 1 ? "field2.png" : "field3.png";
        }
        Bitmap bmp = getFieldBitmapArgb(first);
        if (bmp == null) bmp = getFieldBitmapArgb(second);
        return bmp;
    }

    private Bitmap getFieldBitmapArgb(String name) {
        return getBitmapArgb(name);
    }

    /** ARGB_8888 解码缓存：rowBytes=width*4 恒对齐，杜绝 RGB_565 奇数宽行填充造成的斜向错切 */
    private Bitmap getBitmapArgb(String name) {
        Bitmap bmp = bitmapCache.get(name);
        if (bmp != null) return bmp;
        bmp = loadBitmapFromFile(name, Bitmap.Config.ARGB_8888);
        if (bmp != null) bitmapCache.put(name, bmp);
        return bmp;
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
        return getBitmapArgb("cover.jpg");
    }

    /**
     * 对齐 ImageManager::tCover[0/1]：己方 cover.jpg，对方 cover2.jpg，缺失时回退 cover.jpg。
     * 卡背为177奇数宽，RGB_565行对齐填充会导致GL上传斜向错切，故按 ARGB_8888 解码。
     */
    public Bitmap getCardCover(boolean opponent) {
        if (opponent) {
            Bitmap bmp = getBitmapArgb("cover2.jpg");
            if (bmp != null) return bmp;
        }
        return getBitmapArgb("cover.jpg");
    }

    public Bitmap getUnknownCard() {
        return getBitmapArgb("unknown.jpg");
    }

    public Bitmap getAvatar(boolean isMe) {
        return getTexture(isMe ? "me.jpg" : "opponent.jpg");
    }

    public Bitmap getLinkMarker(int direction) {
        return getTexture("link_marker_on_" + direction + ".png");
    }

    public Bitmap getScaleTexture(boolean isLeft, int value) {
        if (value < 0 || value > 13) return null;
        String name = "extra/" + (isLeft ? "lscale_" : "rscale_") + value + ".png";
        Bitmap bmp = permanentCache.get(name);
        if (bmp != null) return bmp;
        bmp = bitmapCache.get(name);
        if (bmp != null) return bmp;
        bmp = loadBitmapFromFile(name, Bitmap.Config.ARGB_8888);
        if (bmp != null) bitmapCache.put(name, bmp);
        return bmp;
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

    /**
     * lpbarf.png LP 框行裁剪（drawing.cpp L996-1003）：
     * 305x280 图集共 4 行 70px，row0=我方回合彩色、row1=我方非回合灰色、
     * row2=对方非回合灰色、row3=对方回合彩色
     */
    public Bitmap getLpBarFrameRow(int row) {
        if (row < 0 || row > 3) return null;
        String key = "lpbarf_row_" + row;
        Bitmap cached = permanentCache.get(key);
        if (cached != null) return cached;
        Bitmap sheet = getLpBarFrame();
        if (sheet == null || sheet.getWidth() <= 0 || sheet.getHeight() < (row + 1) * 70) return null;
        try {
            Bitmap bmp = Bitmap.createBitmap(sheet, 0, row * 70, sheet.getWidth(), 70);
            if (bmp != null) permanentCache.put(key, bmp);
            return bmp;
        } catch (Exception e) {
            Log.e(TAG, "crop lpbarf failed: row " + row, e);
            return null;
        }
    }

    /**
     * lp3.png 血条颜色行裁剪（drawing.cpp L936-972）：
     * 每行 60px 共 5 种颜色，LP 超过初始上限时按层数循环换色
     */
    public Bitmap getLpBarColorRow(int index) {
        int row = ((index % 5) + 5) % 5;
        String key = "lp3_row_" + row;
        Bitmap cached = permanentCache.get(key);
        if (cached != null) return cached;
        Bitmap sheet = getLpBar();
        if (sheet == null || sheet.getWidth() <= 0 || sheet.getHeight() < (row + 1) * 60) return null;
        try {
            Bitmap bmp = Bitmap.createBitmap(sheet, 0, row * 60, sheet.getWidth(), 60);
            if (bmp != null) permanentCache.put(key, bmp);
            return bmp;
        } catch (Exception e) {
            Log.e(TAG, "crop lp3 failed: row " + row, e);
            return null;
        }
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

    /** tSettings：设置按钮图标 */
    public Bitmap getSettingsTexture() {
        return getTexture("extra/tsettings.png");
    }

    /** tLogs：决斗日志按钮图标 */
    public Bitmap getLogsTexture() {
        return getTexture("extra/tlogs.png");
    }

    /** tMute / tPlay：声音静音/开启图标 */
    public Bitmap getMuteTexture() {
        return getTexture("extra/tmute.png");
    }

    public Bitmap getPlayTexture() {
        return getTexture("extra/tplay.png");
    }

    /** tTalk：聊天按钮图标 */
    public Bitmap getTalkTexture() {
        return getTexture("extra/ttalk.png");
    }

    /** tShut：停用聊天图标 */
    public Bitmap getShutTexture() {
        return getTexture("extra/tshut.png");
    }

    /** tOneX：决斗速度 1x 图标 */
    public Bitmap getOneXTexture() {
        return getTexture("extra/tonex.png");
    }

    /** tDoubleX：决斗速度 2x 图标（对齐 gframe imgQuickAnimation 快速动画状态） */
    public Bitmap getDoubleXTexture() {
        return getTexture("extra/tdoublex.png");
    }

    /** tEmoticon：表情入口按钮图标 */
    public Bitmap getEmoticonTexture() {
        return getTexture("extra/temoticon.png");
    }

    /** emoticons：按键名取裁剪后的表情，如 "&laugh"、"&angry" */
    public Bitmap getEmoticon(String key) {
        return emoticonCache.get(key);
    }

    private Bitmap loadBitmapFromFile(String relativePath) {
        return loadBitmapFromFile(relativePath, Bitmap.Config.RGB_565);
    }

    private Bitmap loadBitmapFromFile(String relativePath, Bitmap.Config config) {
        File file = new File(textureBasePath, relativePath);
        if (file.exists()) {
            FileInputStream fis = null;
            try {
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inPreferredConfig = config;
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
     * 按卡码取卡图。命中缓存立即返回；未命中则异步解码，完成后触发回调重绘。
     * 数据源与 ImageLoader.bindImage 完全同源（findCardImageData 全源查找），
     * 解码配置对齐 Glide 默认 ARGB_8888，手卡/场上卡共用同一 256px 降采样，尺寸一致。
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

    /** 卡图解码：直接走 ImageLoader 全源字节（与 bindImage 同源），ARGB_8888 + 256px 降采样 */
    private Bitmap decodeCardBitmap(long code) {
        byte[] data;
        try {
            data = ImageLoader.findCardImageData(code);
        } catch (Throwable t) {
            return null;
        }
        if (data == null || data.length == 0) return null;
        try {
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, opts);
            opts.inSampleSize = sampleSizeFor(opts.outWidth);
            opts.inJustDecodeBounds = false;
            opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
            return ensureCardAspect(BitmapFactory.decodeByteArray(data, 0, data.length, opts));
        } catch (Throwable t) {
            Log.e(TAG, "decode card failed: " + code, t);
            return null;
        }
    }

    private int sampleSizeFor(int srcWidth) {
        int sample = 1;
        while (srcWidth / (sample * 2) >= CARD_DECODE_MAX_W) sample *= 2;
        return sample;
    }

    /** 非标准比例图源（方形纯画图等）中心裁剪为 177:254，保证贴到卡面不扭曲 */
    private Bitmap ensureCardAspect(Bitmap bmp) {
        if (bmp == null) return null;
        int w = bmp.getWidth(), h = bmp.getHeight();
        if (w <= 0 || h <= 0) return bmp;
        if (Math.abs(w - h * CARD_ASPECT) / (h * CARD_ASPECT) < 0.02f) return bmp;
        int tw = w;
        int th = Math.round(w / CARD_ASPECT);
        if (th > h) {
            th = h;
            tw = Math.round(h * CARD_ASPECT);
        }
        if (tw <= 0 || th <= 0 || tw > w || th > h) return bmp;
        Bitmap cropped = Bitmap.createBitmap(bmp, (w - tw) / 2, (h - th) / 2, tw, th);
        if (cropped != bmp) bmp.recycle();
        return cropped;
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
