package cn.garymb.ygomobile.render.field;
import cn.garymb.ygomobile.render.gamefieldview.GameFieldView;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.opengl.GLES30;
import android.opengl.GLUtils;

import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.utils.YGOUtil;

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
 * 独占 GL 纹理缓存（{@code textures}）、去重请求集（{@code requested}）、上传队列
 * （{@code pendingUploads}）与单线程解码池（{@code texExecutor}）。卡图/场地/场地魔法/卡背等通用纹理由本类直接提供；图标/数字/阶段标签等
 * 派生纹理的 obtain* 随各自 renderer，复用本类暴露的缓存访问点与 {@link #texExecutor()}/
 * {@link #offerUpload} 完成同样的异步上传（同包包级私有直连）。
 */
public final class FieldTextureManager {

    private final GameFieldView view;

    private final HashMap<Long, Integer> textures = new HashMap<>();
    private final Set<Long> requested = Collections.newSetFromMap(new ConcurrentHashMap<Long, Boolean>());
    // 请求去重靠 requested 集合（每个纹理键只会排入一次解码任务，任务必定给出卡图或 unknown），
    // 原先的「限频重试 + 尝试次数」状态已无用：解码结论不再靠时序猜
    private final ConcurrentLinkedQueue<PendingUpload> pendingUploads = new ConcurrentLinkedQueue<>();
    private ExecutorService texExecutor = Executors.newSingleThreadExecutor();

    private static final long COVER_SELF_KEY = -1L;
    private static final long COVER_OPP_KEY = -2L;
    /** unknown.jpg 纹理的专用缓存键（负数键与 COVER_* 同一命名空间，避开真实卡码） */
    private static final long UNKNOWN_KEY = -3L;

    public static class PendingUpload {
        final long key;
        final Bitmap bitmap;
        // 位图所有权：true=本任务独占副本（上传后需 recycle），false=TextureLoader 共享缓存（禁止 recycle）
        final boolean owned;

        public PendingUpload(long key, Bitmap bitmap, boolean owned) {
            this.key = key;
            this.bitmap = bitmap;
            this.owned = owned;
        }
    }

    public FieldTextureManager(GameFieldView view) {
        this.view = view;
    }

    // 供各 renderer 的专用 obtain* 复用的缓存访问点（同包包级私有）
    public final Map<Long, Integer> texCache() {
        return textures;
    }

    public final boolean beginRequest(long key) {
        return requested.add(key);
    }

    public final void cancelRequest(long key) {
        requested.remove(key);
    }

    public final void offerUpload(PendingUpload up) {
        pendingUploads.offer(up);
    }

    /**
     * 线程池被 shutdown 后（视图 detach 过）懒重建，避免 RejectedExecutionException 炸 GL 线程
     */
    public final synchronized ExecutorService texExecutor() {
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
                if (b == null) b = makeSolidBitmap(opponent ? YGOUtil.c(R.color.cover_fallback_opp) : YGOUtil.c(R.color.cover_fallback_self));
                pendingUploads.offer(new PendingUpload(key, b, true));
            });
        } catch (Throwable t) {
            requested.remove(key);
        }
        return -1;
    }

    /**
     * 卡图纹理统一入口（TextureLoader 全源解码 + LRU 缓存）：
     * ① 卡码为 0（未公开/被遮蔽）→ 返回 -1，由渲染层兜底画卡背；
     * ② 卡表查无此码 → 立即同步返回 unknown 纹理（不等异步、不先上灰色）；
     * ③ GL 纹理缓存命中 → 直接返回；
     * ④ TextureLoader 位图缓存命中（已被预读/之前解码过）→ GL 线程同步上传，零占位帧；
     * ⑤ 均未命中 → 工作线程做一次确定性查找，有图即上传、无图直接落 unknown（不再多次重试）
     */
    int obtainTexture(int code, int mode, int scale) {
        // code<=0 是「未公开/被遮蔽」的卡（对手手卡、暗盖、卡组）而不是「查无此码」：
        // 给它贴 unknown 会把卡背位变成一张朝上的卡面，改变遮蔽语义
        if (code <= 0) return -1;
        long key = texKey(code, mode, scale);
        Integer id = textures.get(key);
        if (id != null) return id;
        // 卡表不认得该码：本帧直接落 unknown（用户反馈的「先灰一阵、几秒后才 unknown」
        // 就是旧实现按 2000ms 限频重试 3 次才落地造成的）
        if (isUnknownToCardTable(code)) {
            int unk = obtainUnknown();
            if (unk > 0) {
                textures.put(key, unk);
                return unk;
            }
            return -1;
        }
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
        requestTexture(key, code, mode, scale);
        return -1;
    }

    /**
     * 卡表是否认得该码。判据与 {@code ReplayCodeMapper.cardTableHas} / CardDetailPanel 一致
     *（真卡必有非零 type 位），但**不能**直接调 {@code CardManager.getCard}：它对查不到的码
     * 会 new 存根并 put 回内部 SparseArray，而本方法在 GL 渲染线程每帧可能被调，与卡表
     * 加载线程并发写同一非线程安全容器会写坏它，故只走 indexOfKey/valueAt 只读路径。
     * 卡表未加载完（条目数 0）时不作判定，防决斗刚开场把全部卡误显 unknown。
     */
    private static boolean isUnknownToCardTable(int code) {
        try {
            ocgcore.CardManager cm = ocgcore.DataManager.get().getCardManager();
            if (cm == null || cm.getCount() == 0) return false;
            android.util.SparseArray<ocgcore.data.Card> table = cm.getAllCards();
            int idx = table.indexOfKey(code);
            if (idx < 0) return true;
            ocgcore.data.Card c = table.valueAt(idx);
            return c == null || c.Type == 0;
        } catch (Throwable t) {
            return false;
        }
    }

    /** unknown.jpg 纹理：首次使用时在 GL 线程同步解码上传并永久缓存（后续帧直接命中） */
    private int obtainUnknown() {
        Integer cached = textures.get(UNKNOWN_KEY);
        if (cached != null) return cached;
        try {
            Bitmap u = TextureLoader.get().getUnknownCard();
            if (u != null && !u.isRecycled()) {
                int id = uploadBitmap(u.copy(Bitmap.Config.ARGB_8888, false));
                if (id > 0) {
                    textures.put(UNKNOWN_KEY, id);
                    return id;
                }
            }
        } catch (Throwable ignored) {
        }
        return -1;
    }

    /**
     * 解码任务：在工作线程同步查一次图源（{@code getCardBitmapSync} 给出确定答案），
     * 有图上传卡图、确实无图才上传 unknown，因此每个纹理键只需请求一次。
     */
    private void requestTexture(final long key, final int code, final int mode, final int scale) {
        if (!requested.add(key)) return;
        try {
            texExecutor().execute(() -> {
                Bitmap bmp = null;
                boolean owned = false;
                try {
                    Bitmap base = TextureLoader.get().getCardBitmapSync(code & 0xFFFFFFFFL);
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
                } else {
                    Bitmap u = null;
                    try {
                        u = TextureLoader.get().getUnknownCard();
                    } catch (Throwable ignored) {
                    }
                    bmp = (u != null && !u.isRecycled())
                            ? u.copy(Bitmap.Config.ARGB_8888, false)
                            : makeSolidBitmap(YGOUtil.c(R.color.tex_missing_gray));
                    pendingUploads.offer(new PendingUpload(key, bmp, true));
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
        p.setColor(YGOUtil.c(R.color.scale_number_cyan));
        p.setFakeBoldText(true);
        p.setShadowLayer(3f, 1f, 1f, YGOUtil.c(R.color.black));
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

    public void drainUploads() {
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
    public void clearAll() {
        textures.clear();
        requested.clear();
    }

    /**
     * 视图 detach：关闭解码线程池（后续 texExecutor() 会懒重建）
     */
    public void shutdown() {
        texExecutor.shutdownNow();
    }
}
