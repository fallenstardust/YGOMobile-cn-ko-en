package cn.garymb.ygomobile.render;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.opengl.GLES30;
import android.opengl.GLUtils;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 纹理管理（多线程：工作线程解码 → GL 线程上传），由 GameFieldView 的「纹理」分栏拆出。
 * <p>
 * 独占 GL 纹理缓存（{@code textures}）、去重请求集（{@code requested}）、解码失败限频重试状态
 * （{@code texRetryTime}/{@code texRetries}）、上传队列（{@code pendingUploads}）与单线程解码池
 * （{@code texExecutor}）。卡图/场地/场地魔法/卡背等通用纹理由本类直接提供；图标/数字/阶段标签等
 * 派生纹理的 obtain* 随各自 renderer，复用本类暴露的缓存访问点与 {@link #texExecutor()}/
 * {@link #offerUpload} 完成同样的异步上传（同包包级私有直连）。
 */
final class FieldTextureManager {

    private final GameFieldView view;

    private final HashMap<Long, Integer> textures = new HashMap<>();
    private final Set<Long> requested = Collections.newSetFromMap(new ConcurrentHashMap<Long, Boolean>());
    // 解码失败重试状态（仅 GL 线程访问）：上次请求时间 + 已尝试次数
    private final HashMap<Long, Long> texRetryTime = new HashMap<>();
    private final HashMap<Long, Integer> texRetries = new HashMap<>();
    private final ConcurrentLinkedQueue<PendingUpload> pendingUploads = new ConcurrentLinkedQueue<>();
    private ExecutorService texExecutor = Executors.newSingleThreadExecutor();

    private static final long COVER_SELF_KEY = -1L;
    private static final long COVER_OPP_KEY = -2L;

    static class PendingUpload {
        final long key;
        final Bitmap bitmap;
        // 位图所有权：true=本任务独占副本（上传后需 recycle），false=TextureLoader 共享缓存（禁止 recycle）
        final boolean owned;

        PendingUpload(long key, Bitmap bitmap, boolean owned) {
            this.key = key;
            this.bitmap = bitmap;
            this.owned = owned;
        }
    }

    FieldTextureManager(GameFieldView view) {
        this.view = view;
    }

    // 供各 renderer 的专用 obtain* 复用的缓存访问点（同包包级私有）
    final Map<Long, Integer> texCache() {
        return textures;
    }

    final boolean beginRequest(long key) {
        return requested.add(key);
    }

    final void cancelRequest(long key) {
        requested.remove(key);
    }

    final void offerUpload(PendingUpload up) {
        pendingUploads.offer(up);
    }

    /**
     * 线程池被 shutdown 后（视图 detach 过）懒重建，避免 RejectedExecutionException 炸 GL 线程
     */
    final synchronized ExecutorService texExecutor() {
        if (texExecutor == null || texExecutor.isShutdown()) {
            texExecutor = Executors.newSingleThreadExecutor();
        }
        return texExecutor;
    }

    private static long texKey(int code, int mode, int scale) {
        return ((long) mode << 48) | ((long) scale << 40) | (code & 0xFFFFFFFFL);
    }

    private static long fieldTexKey(int rule, boolean transparent) {
        return -10L - (long) rule * 2L - (transparent ? 1L : 0L);
    }

    /**
     * 场地底板纹理（drawing.cpp L369：drawField ? tFieldTransparent[rule] : tField[rule]）：
     * 首次请求时工作线程解码，下一帧 drainUploads 上传；未就绪返回 -1，由 drawFieldBoard 兜底底色填充
     */
    int obtainFieldTexture(int rule, boolean transparent) {
        long key = fieldTexKey(rule, transparent);
        Integer id = textures.get(key);
        if (id != null) return id;
        if (!requested.add(key)) return -1;
        try {
            texExecutor().execute(() -> {
                Bitmap b = null;
                try {
                    Bitmap src = TextureLoader.get().getFieldTexture(rule, transparent);
                    if (src != null && !src.isRecycled())
                        b = src.copy(Bitmap.Config.ARGB_8888, false);
                } catch (Throwable ignored) {
                }
                if (b != null) {
                    pendingUploads.offer(new PendingUpload(key, b, true));
                } else {
                    // 解码失败：释放请求标记，后续帧可重试
                    requested.remove(key);
                }
            });
        } catch (Throwable t) {
            requested.remove(key);
        }
        return -1;
    }

    private static long fieldSpellTexKey(int code) {
        return -1000000000L - code;
    }

    /**
     * 场地魔法背景图纹理（TextureLoader 全源解码 + LRU 缓存），异步上传，缺失不重试
     */
    int obtainFieldSpellTexture(int code) {
        if (code <= 0) return -1;
        long key = fieldSpellTexKey(code);
        Integer id = textures.get(key);
        if (id != null) return id;
        if (!requested.add(key)) return -1;
        try {
            texExecutor().execute(() -> {
                Bitmap b = null;
                try {
                    Bitmap src = TextureLoader.get().getFieldSpellBitmap(code);
                    if (src != null && !src.isRecycled())
                        b = src.copy(Bitmap.Config.ARGB_8888, false);
                } catch (Throwable ignored) {
                }
                if (b != null) pendingUploads.offer(new PendingUpload(key, b, true));
            });
        } catch (Throwable t) {
            requested.remove(key);
        }
        return -1;
    }

    int obtainCover(boolean opponent) {
        long key = opponent ? COVER_OPP_KEY : COVER_SELF_KEY;
        Integer id = textures.get(key);
        if (id != null) return id;
        if (!requested.add(key)) return -1;
        try {
            texExecutor().execute(() -> {
                Bitmap b = null;
                try {
                    Bitmap src = TextureLoader.get().getCardCover(opponent);
                    if (src != null && !src.isRecycled())
                        b = src.copy(Bitmap.Config.ARGB_8888, false);
                } catch (Throwable ignored) {
                }
                if (b == null) b = makeSolidBitmap(opponent ? 0xFF3A2820 : 0xFF28303A);
                pendingUploads.offer(new PendingUpload(key, b, true));
            });
        } catch (Throwable t) {
            requested.remove(key);
        }
        return -1;
    }

    /**
     * 卡图纹理统一入口（TextureLoader 全源解码 + LRU 缓存）：
     * ① GL 纹理缓存命中 → 直接返回；
     * ② TextureLoader 位图缓存命中（已被预读/之前解码过）→ GL 线程同步上传，零占位帧；
     * ③ 均未命中 → 触发 TextureLoader 异步解码，限频等待，连续 3 次落空才永久回退 unknown
     */
    int obtainTexture(int code, int mode, int scale) {
        long key = texKey(code, mode, scale);
        Integer id = textures.get(key);
        if (id != null) return id;
        try {
            Bitmap cached = TextureLoader.get().getCardBitmap(code & 0xFFFFFFFFL);
            if (cached != null && !cached.isRecycled()) {
                Bitmap base = cached;
                Bitmap bmp = (mode == 0) ? base : compositePendulum(base, mode == 1, scale);
                if (bmp != null) {
                    int newId = uploadBitmap(bmp);
                    if (newId > 0) {
                        if (bmp != base) bmp.recycle();
                        textures.put(key, newId);
                        return newId;
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        long now = view.animTimeMs;
        Long last = texRetryTime.get(key);
        if (last != null && now - last < 2000L) return -1;
        texRetryTime.put(key, now);
        int attempts = texRetries.containsKey(key) ? texRetries.get(key) + 1 : 1;
        texRetries.put(key, attempts);
        requestTexture(key, code, mode, scale, attempts >= 3);
        return -1;
    }

    /**
     * 兜底任务：卡图解码已统一收口到 TextureLoader.getCardBitmap（内部全源查找 + 去重），
     * 本任务只负责在重试窗口后检查结果；lastChance=true 仍缺失时才上传 unknown 终止重试。
     */
    private void requestTexture(final long key, final int code, final int mode,
                                final int scale, final boolean lastChance) {
        if (!requested.add(key)) return;
        try {
            texExecutor().execute(() -> {
                Bitmap bmp = null;
                boolean owned = false;
                try {
                    Bitmap base = TextureLoader.get().getCardBitmap(code & 0xFFFFFFFFL);
                    if (base != null && !base.isRecycled()) {
                        if (mode == 0) {
                            bmp = base;          // 共享缓存位图：上传但不回收
                        } else {
                            bmp = compositePendulum(base, mode == 1, scale);
                            owned = (bmp != base); // 合成副本才归本任务所有
                        }
                    }
                } catch (Throwable ignored) {
                }
                if (bmp != null) {
                    pendingUploads.offer(new PendingUpload(key, bmp, owned));
                } else if (lastChance) {
                    Bitmap u = null;
                    try {
                        u = TextureLoader.get().getUnknownCard();
                    } catch (Throwable ignored) {
                    }
                    bmp = (u != null && !u.isRecycled())
                            ? u.copy(Bitmap.Config.ARGB_8888, false)
                            : makeSolidBitmap(0xFF555560);
                    pendingUploads.offer(new PendingUpload(key, bmp, true));
                } else {
                    // 尚未到最后一次：释放请求标记，等限频窗口过后重试
                    requested.remove(key);
                }
            });
        } catch (Throwable t) {
            requested.remove(key);
        }
    }

    /**
     * 灵摆刻度合成：卡图上叠加 lscale/rscale 贴图 + 角标刻度值（与 Canvas 版行为一致）
     */
    private static Bitmap compositePendulum(Bitmap base, boolean left, int scale) {
        Bitmap bmp = base.isMutable() ? base : base.copy(Bitmap.Config.ARGB_8888, true);
        Canvas cv = new Canvas(bmp);
        Bitmap s = null;
        try {
            s = TextureLoader.get().getScaleTexture(left, scale);
        } catch (Throwable ignored) {
        }
        if (s != null && !s.isRecycled()) {
            float w = bmp.getWidth() * 0.92f;
            float h = w * s.getHeight() / (float) s.getWidth();
            float maxH = bmp.getHeight() * 0.92f;
            if (h > maxH) {
                h = maxH;
                w = h * s.getWidth() / (float) s.getHeight();
            }
            cv.drawBitmap(s, null, new android.graphics.Rect(
                    (int) ((bmp.getWidth() - w) / 2f), (int) ((bmp.getHeight() - h) / 2f),
                    (int) ((bmp.getWidth() + w) / 2f), (int) ((bmp.getHeight() + h) / 2f)), null);
        }
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTextSize(bmp.getWidth() * 0.2f);
        p.setColor(0xFF40E0FF);
        p.setFakeBoldText(true);
        p.setShadowLayer(3f, 1f, 1f, 0xFF000000);
        if (left) {
            p.setTextAlign(Paint.Align.LEFT);
            cv.drawText(String.valueOf(scale), bmp.getWidth() * 0.12f, bmp.getHeight() * 0.13f, p);
        } else {
            p.setTextAlign(Paint.Align.RIGHT);
            cv.drawText(String.valueOf(scale), bmp.getWidth() * 0.88f, bmp.getHeight() * 0.13f, p);
        }
        return bmp;
    }

    private static Bitmap makeSolidBitmap(int color) {
        Bitmap b = Bitmap.createBitmap(64, 92, Bitmap.Config.ARGB_8888);
        b.eraseColor(color);
        return b;
    }

    void drainUploads() {
        PendingUpload up;
        while ((up = pendingUploads.poll()) != null) {
            if (up.bitmap == null || up.bitmap.isRecycled()) continue;
            int id = uploadBitmap(up.bitmap);
            if (id > 0) textures.put(up.key, id);
            if (up.owned && !up.bitmap.isRecycled()) up.bitmap.recycle();
        }
    }

    /**
     * Bitmap → GL 纹理（GL 线程专用）：快路径同步上传与 drainUploads 共用
     */
    private int uploadBitmap(Bitmap bmp) {
        Bitmap src = bmp;
        // RGB_565 奇数宽位图 rowBytes 有 4 字节对齐填充，按 packed 行长上传会逐行错位形成斜向错切；
        // 统一转 ARGB_8888（rowBytes=width*4 恒对齐）兜底，正常路径解码已是 ARGB_8888 不触发
        if (src.getConfig() == Bitmap.Config.RGB_565 && (src.getRowBytes() != src.getWidth() * 2)) {
            src = src.copy(Bitmap.Config.ARGB_8888, false);
        }
        int[] ids = new int[1];
        GLES30.glGenTextures(1, ids, 0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, ids[0]);
        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, src, 0);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0);
        if (src != bmp) src.recycle();
        return ids[0];
    }

    /**
     * 上下文（重新）创建：纹理缓存全部失效，重新按需加载（由门面 onSurfaceCreated 调用）
     */
    void clearAll() {
        textures.clear();
        requested.clear();
        texRetryTime.clear();
        texRetries.clear();
    }

    /**
     * 视图 detach：关闭解码线程池（后续 texExecutor() 会懒重建）
     */
    void shutdown() {
        texExecutor.shutdownNow();
    }
}
