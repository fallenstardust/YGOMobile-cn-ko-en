package cn.garymb.ygomobile.render;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.util.AttributeSet;
import android.view.Display;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.WindowManager;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import cn.garymb.ygomobile.game.GameField;
import cn.garymb.ygomobile.loader.ImageLoader;

/**
 * 决斗场渲染视图（路线 B：OpenGL ES 3.0 真 3D 透视）。
 * <p>
 * 世界坐标系直接采用 gframe 场地坐标：X∈[-0.8,8.7]，Y∈[-3.9,3.9]（+Y 朝向我方），Z 为高度。
 * 棋盘/区域槽/堆叠区/双方手卡/场上卡全部在同一透视相机内绘制，近大远小由 GPU 完成；
 * 卡片位置与旋转直接取 {@link GameField.ClientCard} 的 curX/curY/curZ/curRot*，
 * 与 ClientField::GetCardLocation 数值完全一致。
 * <p>
 * 公开 API 与原 Canvas 版保持一致，调用方（GameFieldViewController/GameFieldController/布局 XML）无需修改。
 */
public class GameFieldView extends GLSurfaceView implements GLSurfaceView.Renderer {

    private static final String TAG = "GameFieldView";

    public interface OnCardClickListener {
        void onCardClick(int player, int location, int sequence);

        void onZoneClick(int player, int location, int sequence);

        void onFieldLongPress(int player, int location, int sequence);
    }

    // === 场地坐标常量（materials.cpp）===
    private static final float FIELD_X_MIN = -0.8f;
    private static final float FIELD_X_MAX = 8.7f;
    private static final float FIELD_Y_MIN = -3.9f;
    private static final float FIELD_Y_MAX = 3.9f;
    // 卡片世界尺寸（场地单位，177:254 等比）
    private static final float CARD_W = 0.8f;
    private static final float CARD_H = 1.16f;
    // 区域槽尺寸
    private static final float ZONE_W = 1.0f;
    private static final float ZONE_H = 1.15f;
    private static final float PILE_W = 0.8f;
    private static final float PILE_H = 1.2f;

    // === 相机（可按设备效果微调）===
    private static final float EYE_X = 3.95f, EYE_Y = 8.4f, EYE_Z = 6.2f;
    private static final float LOOK_X = 3.95f, LOOK_Y = 0.2f, LOOK_Z = 0f;

    // === 着色器（ES 3.0 / GLSL 300 es）===
    private static final String VS =
            "#version 300 es\n" +
            "layout(location=0) in vec2 aPos;\n" +
            "layout(location=1) in vec2 aUV;\n" +
            "uniform mat4 uMVP;\n" +
            "out vec2 vUV;\n" +
            "void main(){ vUV=aUV; gl_Position=uMVP*vec4(aPos,0.0,1.0); }\n";

    private static final String FS_TEX =
            "#version 300 es\n" +
            "precision mediump float;\n" +
            "in vec2 vUV;\n" +
            "uniform sampler2D uTex;\n" +
            "uniform vec4 uTint;\n" +
            "out vec4 fragColor;\n" +
            "void main(){ fragColor=texture(uTex,vUV)*uTint; }\n";

    private static final String FS_COLOR =
            "#version 300 es\n" +
            "precision mediump float;\n" +
            "uniform vec4 uColor;\n" +
            "out vec4 fragColor;\n" +
            "void main(){ fragColor=uColor; }\n";

    // 单位矩形（XY 平面，法线 +Z；uv 保证 Bitmap 顶部对应卡片顶部）
    private static final float[] QUAD = {
            -0.5f, -0.5f, 0f, 0f,
            0.5f, -0.5f, 1f, 0f,
            -0.5f, 0.5f, 0f, 1f,
            0.5f, 0.5f, 1f, 1f,
    };

    private static final long COVER_SELF_KEY = -1L;
    private static final long COVER_OPP_KEY = -2L;

    // === 业务状态（公开 API 写入，GL 线程读取）===
    private volatile GameField field;
    private volatile ImageLoader imageLoader;
    private volatile OnCardClickListener cardClickListener;
    private volatile int highlightFieldMask = 0;
    private volatile int selectedPlayer = -1;
    private volatile int selectedLocation = -1;
    private volatile int selectedSequence = -1;
    // 动画倍率：1=原速，2=2 倍速（时间驱动，帧率无关）
    private volatile float animSpeedMultiplier = 1f;

    // === GL 资源 ===
    private int texProg, colorProg;
    private int texLocMVP, texLocTint, texLocTex;
    private int colorLocMVP, colorLocColor;
    private int vao;
    private final HashMap<Long, Integer> textures = new HashMap<>();
    private final Set<Long> requested = Collections.newSetFromMap(new ConcurrentHashMap<Long, Boolean>());
    private final ConcurrentLinkedQueue<PendingUpload> pendingUploads = new ConcurrentLinkedQueue<>();
    private ExecutorService texExecutor = Executors.newSingleThreadExecutor();

    // === 矩阵与相机缓存 ===
    private final float[] mProj = new float[16];
    private final float[] mView = new float[16];
    private final float[] mVP = new float[16];
    private final float[] mMVP = new float[16];
    private final float[] mModel = new float[16];
    private final float[] mModelTmp = new float[16];
    private final Object camLock = new Object();
    private final float[] pickInvVP = new float[16];
    private volatile int viewW = 1, viewH = 1;

    private long lastFrameNs = 0;
    private long animTimeMs = 0;
    private GestureDetector gestureDetector;

    private static class PendingUpload {
        final long key;
        final Bitmap bitmap;

        PendingUpload(long key, Bitmap bitmap) {
            this.key = key;
            this.bitmap = bitmap;
        }
    }

    public GameFieldView(Context context) {
        super(context);
        init();
    }

    public GameFieldView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        setEGLContextClientVersion(3);
        setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        setRenderer(this);
        setRenderMode(RENDERMODE_CONTINUOUSLY);
        try {
            setPreserveEGLContextOnPause(true);
        } catch (Throwable ignored) {
        }

        gestureDetector = new GestureDetector(getContext(), new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                handleTap(e.getX(), e.getY());
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                handleLongPress(e.getX(), e.getY());
            }
        });
    }

    // ==================== 公开 API（与原 Canvas 版兼容）====================

    public void setField(GameField field) {
        this.field = field;
        requestRender();
    }

    public void setImageLoader(ImageLoader imageLoader) {
        this.imageLoader = imageLoader;
    }

    public void setCardClickListener(OnCardClickListener listener) {
        this.cardClickListener = listener;
    }

    public void setHighlightFieldMask(int mask) {
        this.highlightFieldMask = mask;
        requestRender();
    }

    public void setSelectedCard(int player, int location, int sequence) {
        selectedPlayer = player;
        selectedLocation = location;
        selectedSequence = sequence;
        requestRender();
    }

    public void clearSelection() {
        selectedPlayer = -1;
        selectedLocation = -1;
        selectedSequence = -1;
        requestRender();
    }

    /** 连续渲染模式自带动画循环，保留接口仅为兼容 */
    public void startAnimationLoop() {
    }

    /** 动画倍率：1=原速，2=2 倍速（时间驱动，任意刷新率下速度一致） */
    public void setAnimationSpeed(float multiplier) {
        animSpeedMultiplier = Math.max(0.25f, multiplier);
    }

    // 以下接口在 GL 版中无对应渲染对象，保留空实现确保调用方编译通过
    public void addChainLine(float x1, float y1, float x2, float y2, int color) {
    }

    public void clearChainLines() {
    }

    public void animateLpChange(int player, int fromLp, int toLp) {
    }

    public void syncDisplayLp() {
    }

    @Override
    public void invalidate() {
        super.invalidate();
        requestRender();
    }

    // ==================== GLSurfaceView.Renderer ====================

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES30.glClearColor(0.02f, 0.03f, 0.08f, 1f);
        GLES30.glEnable(GLES30.GL_DEPTH_TEST);
        GLES30.glDisable(GLES30.GL_CULL_FACE);

        texProg = createProgram(VS, FS_TEX);
        colorProg = createProgram(VS, FS_COLOR);
        texLocMVP = GLES30.glGetUniformLocation(texProg, "uMVP");
        texLocTint = GLES30.glGetUniformLocation(texProg, "uTint");
        texLocTex = GLES30.glGetUniformLocation(texProg, "uTex");
        colorLocMVP = GLES30.glGetUniformLocation(colorProg, "uMVP");
        colorLocColor = GLES30.glGetUniformLocation(colorProg, "uColor");

        FloatBuffer fb = ByteBuffer.allocateDirect(QUAD.length * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        fb.put(QUAD).position(0);
        int[] vaos = new int[1], vbos = new int[1];
        GLES30.glGenVertexArrays(1, vaos, 0);
        vao = vaos[0];
        GLES30.glGenBuffers(1, vbos, 0);
        GLES30.glBindVertexArray(vao);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbos[0]);
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, fb.capacity() * 4, fb, GLES30.GL_STATIC_DRAW);
        GLES30.glEnableVertexAttribArray(0);
        GLES30.glVertexAttribPointer(0, 2, GLES30.GL_FLOAT, false, 16, 0);
        GLES30.glEnableVertexAttribArray(1);
        GLES30.glVertexAttribPointer(1, 2, GLES30.GL_FLOAT, false, 16, 8);
        GLES30.glBindVertexArray(0);

        // 上下文（重新）创建：纹理缓存全部失效，重新按需加载
        textures.clear();
        requested.clear();
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int w, int h) {
        viewW = w;
        viewH = h;
        GLES30.glViewport(0, 0, w, h);

        // 自适应视场角：窄屏（竖屏）时自动加大垂直 FOV，保证场地宽度完整入镜
        float aspect = (float) w / Math.max(1, h);
        float halfTan = (float) Math.tan(Math.toRadians(26.0));
        float need = 0.52f / Math.max(0.25f, aspect);
        if (need > halfTan) halfTan = need;
        if (halfTan > 1.6f) halfTan = 1.6f;
        float fovy = (float) Math.toDegrees(2.0 * Math.atan(halfTan));
        Matrix.perspectiveM(mProj, 0, fovy, aspect, 0.5f, 100f);
        Matrix.setLookAtM(mView, 0, EYE_X, EYE_Y, EYE_Z, LOOK_X, LOOK_Y, LOOK_Z, 0f, 0f, 1f);
        Matrix.multiplyMM(mVP, 0, mProj, 0, mView, 0);

        synchronized (camLock) {
            Matrix.invertM(pickInvVP, 0, mVP, 0);
        }
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        long now = System.nanoTime();
        float dt = lastFrameNs == 0 ? 1f / 60f : (now - lastFrameNs) / 1e9f;
        lastFrameNs = now;
        if (dt > 0.1f) dt = 0.1f;
        animTimeMs = System.currentTimeMillis();

        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT | GLES30.GL_DEPTH_BUFFER_BIT);
        try {
            drainUploads();
        } catch (Throwable ignored) {
        }

        GameField f = field;
        if (f != null) {
            // 引擎回调在网络线程结构性增删卡列表，GL 线程并发读取偶发 CME/IOOBE；
            // 全部吞掉保证渲染线程永不崩溃（本帧跳过，下一帧自动恢复）
            try {
                // 时间驱动动画：帧率越高推进越细，速度恒定；animationSpeed 即本帧推进量
                f.animationSpeed = dt * 60f * animSpeedMultiplier;
                f.updateCardAnimation(1);
            } catch (Throwable ignored) {
            }
        }
        if (f == null) return;

        // 不透明底盘
        GLES30.glDisable(GLES30.GL_BLEND);
        GLES30.glDepthMask(true);
        drawBoard();

        // 半透明：槽位/卡片/高亮
        GLES30.glEnable(GLES30.GL_BLEND);
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA);
        drawZoneSlots();
        try {
            drawFieldCards(f);
        } catch (Throwable ignored) {
        }
        GLES30.glDepthMask(false);
        drawHighlights();
        GLES30.glDepthMask(true);
    }

    // ==================== 绘制 ====================

    private void drawBoard() {
        // 外围底板
        drawFlatQuad((FIELD_X_MIN + FIELD_X_MAX) / 2f, (FIELD_Y_MIN + FIELD_Y_MAX) / 2f, 0f,
                FIELD_X_MAX - FIELD_X_MIN + 3.2f, FIELD_Y_MAX - FIELD_Y_MIN + 2.4f,
                0.03f, 0.05f, 0.10f, 1f);
        // 场地内区
        drawFlatQuad((FIELD_X_MIN + FIELD_X_MAX) / 2f, 0f, 0.001f,
                FIELD_X_MAX - FIELD_X_MIN, FIELD_Y_MAX - FIELD_Y_MIN,
                0.05f, 0.09f, 0.16f, 1f);
        // 中线
        drawFlatQuad((FIELD_X_MIN + FIELD_X_MAX) / 2f, 0f, 0.002f,
                FIELD_X_MAX - FIELD_X_MIN, 0.04f, 0f, 0.78f, 0.94f, 0.25f);
    }

    private void drawZoneSlots() {
        float pulse = 0.10f + 0.08f * (float) Math.sin(animTimeMs * 0.002);
        for (int p = 0; p < 2; p++) {
            for (int i = 0; i < 7; i++) {
                float[] c = zoneCenter(p, 0x04, i);
                drawFlatQuad(c[0], c[1], 0.004f, ZONE_W, ZONE_H, 0f, 0.78f, 0.94f, pulse);
            }
            for (int i = 0; i <= 5; i++) {
                float[] c = zoneCenter(p, 0x08, i);
                drawFlatQuad(c[0], c[1], 0.004f, ZONE_W, ZONE_H, 0f, 0.78f, 0.94f, pulse);
            }
            for (int loc : new int[]{0x01, 0x10, 0x20, 0x40}) {
                float[] c = pileCenter(p, loc);
                if (c == null) continue;
                drawFlatQuad(c[0], c[1], 0.004f, PILE_W, PILE_H, 0f, 0.78f, 0.94f, pulse);
            }
        }
    }

    private void drawHighlights() {
        int mask = highlightFieldMask;
        if (mask == 0) return;
        float a = 0.30f + 0.12f * (float) Math.sin(animTimeMs * 0.005);
        for (int i = 0; i < 7; i++) {
            if ((mask & (1 << i)) != 0) drawZoneGlow(0, 0x04, i, a);
            if ((mask & (1 << (16 + i))) != 0) drawZoneGlow(1, 0x04, i, a);
        }
        for (int i = 0; i < 6; i++) {
            if ((mask & (1 << (8 + i))) != 0) drawZoneGlow(0, 0x08, i, a);
            if ((mask & (1 << (24 + i))) != 0) drawZoneGlow(1, 0x08, i, a);
        }
    }

    private void drawZoneGlow(int player, int loc, int seq, float alpha) {
        float[] c = zoneCenter(player, loc, seq);
        drawFlatQuad(c[0], c[1], 0.03f, ZONE_W, ZONE_H, 0f, 1f, 0.39f, alpha);
    }

    private void drawFieldCards(GameField f) {
        for (int p = 0; p < 2; p++) {
            drawCardList(f.players[p].monsterZone);
            drawCardList(f.players[p].spellZone);
            drawPile(f.players[p].deck);
            drawPile(f.players[p].grave);
            drawPile(f.players[p].removed);
            drawPile(f.players[p].extra);
        }
        // 手卡最后绘制（半透明排序靠上）
        for (int p = 1; p >= 0; p--) {
            drawCardList(f.players[p].hand);
        }
        drawCardList(f.overlayCards);
    }

    /** 线程安全遍历：索引式 + 全量兜底，网络线程并发增删时最多丢一帧 */
    private void drawCardList(List<GameField.ClientCard> list) {
        if (list == null) return;
        try {
            for (int i = 0, n = list.size(); i < n; i++) {
                GameField.ClientCard c;
                try {
                    c = list.get(i);
                } catch (Throwable e) {
                    continue;
                }
                drawCard(c);
            }
        } catch (Throwable ignored) {
        }
    }

    private void drawPile(List<GameField.ClientCard> pile) {
        if (pile == null) return;
        try {
            int n = 0;
            for (int i = 0, s = pile.size(); i < s; i++) {
                try {
                    if (pile.get(i) != null) n++;
                } catch (Throwable e) {
                    break;
                }
            }
            int skip = Math.max(0, n - 24); // 堆叠区只画最上面 24 张
            for (int i = 0, s = pile.size(); i < s; i++) {
                GameField.ClientCard c;
                try {
                    c = pile.get(i);
                } catch (Throwable e) {
                    continue;
                }
                if (c == null) continue;
                if (skip-- > 0) continue;
                drawCard(c);
            }
        } catch (Throwable ignored) {
        }
    }

    /** 双面卡片：背面 cover + 正面卡图；位置/旋转直接来自 getCardLocation 动画值 */
    private void drawCard(GameField.ClientCard c) {
        if (c == null) return;
        float alpha = Math.max(0f, Math.min(1f, c.curAlpha / 255f));
        if (alpha <= 0.01f) return;

        Matrix.setIdentityM(mModel, 0);
        Matrix.translateM(mModel, 0, c.curX, c.curY, c.curZ);
        Matrix.rotateM(mModel, 0, (float) Math.toDegrees(c.curRotX), 1f, 0f, 0f);
        Matrix.rotateM(mModel, 0, (float) Math.toDegrees(c.curRotY), 0f, 1f, 0f);
        Matrix.rotateM(mModel, 0, (float) Math.toDegrees(c.curRotZ), 0f, 0f, 1f);
        Matrix.scaleM(mModel, 0, CARD_W, CARD_H, 1f);

        int glow = pickGlowColor(c);
        if (glow != 0) drawGlow(glow, alpha);

        // 背面（cover），沿卡片局部 -Z 微偏移避免共面闪烁
        System.arraycopy(mModel, 0, mModelTmp, 0, 16);
        Matrix.translateM(mModelTmp, 0, mModelTmp, 0, 0f, 0f, -0.002f);
        Matrix.rotateM(mModelTmp, 0, 180f, 0f, 1f, 0f);
        int coverTex = obtainCover(c.owner != 0);
        if (coverTex > 0) {
            drawQuadTex(mModelTmp, coverTex, alpha);
        } else {
            drawQuadColor(mModelTmp, 0.24f, 0.18f, 0.13f, alpha);
        }

        // 正面
        int code = c.code != 0 ? c.code : (c.is_moving ? c.chain_code : 0);
        if (c.isFaceUp() && code > 0) {
            int tex = obtainTexture(code, pendulumMode(c), pendulumScale(c));
            if (tex > 0) {
                drawQuadTex(mModel, tex, alpha);
            } else {
                drawQuadColor(mModel, 0.35f, 0.35f, 0.40f, alpha);
            }
        } else {
            drawQuadColor(mModel, 0.16f, 0.12f, 0.10f, alpha);
        }
    }

    private int pickGlowColor(GameField.ClientCard c) {
        if (c.controler == selectedPlayer && c.location == selectedLocation
                && c.sequence == selectedSequence) return 0xFFFFFF00;
        if (c.is_selected) return 0xFFFFFF00;
        if (c.is_highlighting) return 0xFF00FFFF;
        if (c.is_showequip || c.is_showtarget || c.is_showchaintarget) return 0xFFFF4444;
        if (c.is_selectable) return 0xFFFFD700;
        return 0;
    }

    private void drawGlow(int color, float cardAlpha) {
        System.arraycopy(mModel, 0, mModelTmp, 0, 16);
        Matrix.translateM(mModelTmp, 0, mModelTmp, 0, 0f, 0f, -0.004f);
        Matrix.scaleM(mModelTmp, 0, mModelTmp, 0, 1.10f, 1.10f, 1f);
        float a = cardAlpha * (0.55f + 0.30f * (float) Math.sin(animTimeMs * 0.005));
        drawQuadColor(mModelTmp, ((color >> 16) & 0xFF) / 255f,
                ((color >> 8) & 0xFF) / 255f, (color & 0xFF) / 255f, a);
    }

    private void drawFlatQuad(float cx, float cy, float z, float w, float h,
                              float r, float g, float b, float a) {
        Matrix.setIdentityM(mModel, 0);
        Matrix.translateM(mModel, 0, cx, cy, z);
        Matrix.scaleM(mModel, 0, w, h, 1f);
        drawQuadColor(mModel, r, g, b, a);
    }

    private void drawQuadTex(float[] model, int texId, float alpha) {
        if (texId <= 0) return;
        GLES30.glUseProgram(texProg);
        Matrix.multiplyMM(mMVP, 0, mVP, 0, model, 0);
        GLES30.glUniformMatrix4fv(texLocMVP, 1, false, mMVP, 0);
        GLES30.glUniform4f(texLocTint, 1f, 1f, 1f, alpha);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texId);
        GLES30.glUniform1i(texLocTex, 0);
        GLES30.glBindVertexArray(vao);
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4);
        GLES30.glBindVertexArray(0);
    }

    private void drawQuadColor(float[] model, float r, float g, float b, float a) {
        GLES30.glUseProgram(colorProg);
        Matrix.multiplyMM(mMVP, 0, mVP, 0, model, 0);
        GLES30.glUniformMatrix4fv(colorLocMVP, 1, false, mMVP, 0);
        GLES30.glUniform4f(colorLocColor, r, g, b, a);
        GLES30.glBindVertexArray(vao);
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4);
        GLES30.glBindVertexArray(0);
    }

    // ==================== 纹理（多线程：工作线程解码 → GL 线程上传）====================

    private static long texKey(int code, int mode, int scale) {
        return ((long) mode << 48) | ((long) scale << 40) | (code & 0xFFFFFFFFL);
    }

    private int obtainTexture(int code, int mode, int scale) {
        long key = texKey(code, mode, scale);
        Integer id = textures.get(key);
        if (id != null) return id;
        requestTexture(key, code, mode, scale);
        return -1;
    }

    private int obtainCover(boolean opponent) {
        long key = opponent ? COVER_OPP_KEY : COVER_SELF_KEY;
        Integer id = textures.get(key);
        if (id != null) return id;
        if (!requested.add(key)) return -1;
        try {
            texExecutor().execute(() -> {
                Bitmap b = null;
                try {
                    Bitmap src = TextureLoader.get().getCardCover(opponent);
                    if (src != null && !src.isRecycled()) b = src.copy(Bitmap.Config.ARGB_8888, false);
                } catch (Throwable ignored) {
                }
                if (b == null) b = makeSolidBitmap(opponent ? 0xFF3A2820 : 0xFF28303A);
                pendingUploads.offer(new PendingUpload(key, b));
            });
        } catch (Throwable t) {
            requested.remove(key);
        }
        return -1;
    }

    private void requestTexture(final long key, final int code, final int mode, final int scale) {
        if (!requested.add(key)) return;
        final ImageLoader il = imageLoader;
        try {
            texExecutor().execute(() -> {
                Bitmap bmp = null;
                try {
                    if (il != null) {
                        Bitmap base = decodeCardArt(il, code);
                        if (base != null) {
                            bmp = (mode == 0) ? base : compositePendulum(base, mode == 1, scale);
                        }
                    }
                } catch (Throwable ignored) {
                }
                if (bmp == null) {
                    Bitmap u = null;
                    try {
                        u = TextureLoader.get().getUnknownCard();
                    } catch (Throwable ignored) {
                    }
                    bmp = (u != null && !u.isRecycled())
                            ? u.copy(Bitmap.Config.ARGB_8888, false)
                            : makeSolidBitmap(0xFF555560);
                }
                pendingUploads.offer(new PendingUpload(key, bmp));
            });
        } catch (Throwable t) {
            requested.remove(key);
        }
    }

    /** 线程池被 shutdown 后（视图 detach 过）懒重建，避免 RejectedExecutionException 炸 GL 线程 */
    private synchronized ExecutorService texExecutor() {
        if (texExecutor == null || texExecutor.isShutdown()) {
            texExecutor = Executors.newSingleThreadExecutor();
        }
        return texExecutor;
    }

    private static Bitmap decodeCardArt(ImageLoader il, int code) {
        File f = il.getImageFile(code);
        if (f != null && f.exists()) {
            Bitmap b = BitmapFactory.decodeFile(f.getAbsolutePath());
            if (b != null) return b;
        }
        return null;
    }

    /** 灵摆刻度合成：卡图上叠加 lscale/rscale 贴图 + 角标刻度值（与 Canvas 版行为一致） */
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

    private void drainUploads() {
        PendingUpload up;
        while ((up = pendingUploads.poll()) != null) {
            if (up.bitmap == null || up.bitmap.isRecycled()) continue;
            int[] ids = new int[1];
            GLES30.glGenTextures(1, ids, 0);
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, ids[0]);
            GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, up.bitmap, 0);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE);
            GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE);
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0);
            textures.put(up.key, ids[0]);
            up.bitmap.recycle();
        }
    }

    // ==================== 触摸拾取（射线反投影）====================

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (gestureDetector != null) {
            gestureDetector.onTouchEvent(event);
        }
        return true;
    }

    private float[] buildRay(float sx, float sy) {
        float[] inv = new float[16];
        int w, h;
        synchronized (camLock) {
            System.arraycopy(pickInvVP, 0, inv, 0, 16);
            w = viewW;
            h = viewH;
        }
        float nx = 2f * sx / w - 1f;
        float ny = 1f - 2f * sy / h;
        float[] p0 = unproject(inv, nx, ny, -1f);
        float[] p1 = unproject(inv, nx, ny, 1f);
        if (p0 == null || p1 == null) return null;
        float dx = p1[0] - p0[0], dy = p1[1] - p0[1], dz = p1[2] - p0[2];
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-6f) return null;
        return new float[]{p0[0], p0[1], p0[2], dx / len, dy / len, dz / len};
    }

    private static float[] unproject(float[] inv, float nx, float ny, float nz) {
        float[] v = {nx, ny, nz, 1f};
        float[] o = new float[4];
        Matrix.multiplyMV(o, 0, inv, 0, v, 0);
        if (Math.abs(o[3]) < 1e-6f) return null;
        return new float[]{o[0] / o[3], o[1] / o[3], o[2] / o[3]};
    }

    private static boolean planeHit(float[] ray, float zPlane, float[] out) {
        if (Math.abs(ray[5]) < 1e-6f) return false;
        float t = (zPlane - ray[2]) / ray[5];
        if (t < 0) return false;
        out[0] = ray[0] + ray[3] * t;
        out[1] = ray[1] + ray[4] * t;
        return true;
    }

    private void handleTap(float x, float y) {
        OnCardClickListener listener = cardClickListener;
        GameField f = field;
        if (listener == null || f == null) return;
        float[] ray = buildRay(x, y);
        if (ray == null) return;

        try {
            // 高亮选区优先（与 Canvas 版 handleTap 语义一致）
            int mask = highlightFieldMask;
            if (mask != 0) {
                float[] g = new float[2];
                if (planeHit(ray, 0.02f, g)) {
                    for (int p = 0; p < 2; p++) {
                        for (int loc : new int[]{0x04, 0x08}) {
                            int max = (loc == 0x04) ? GameField.MAX_MONSTER_ZONE : GameField.MAX_SPELL_ZONE;
                            for (int i = 0; i < max; i++) {
                                if (!zoneContains(p, loc, i, g[0], g[1])) continue;
                                int bit = zoneBitPos(p, loc, i);
                                if (bit >= 0 && (mask & (1 << bit)) != 0) {
                                    listener.onZoneClick(p, loc, i);
                                    return;
                                }
                            }
                        }
                    }
                }
            }

            int[] hit = hitCard(ray, f);
            if (hit != null) {
                setSelectedCard(hit[0], hit[1], hit[2]);
                listener.onCardClick(hit[0], hit[1], hit[2]);
            } else {
                clearSelection();
            }
        } catch (Throwable ignored) {
        }
    }

    private void handleLongPress(float x, float y) {
        OnCardClickListener listener = cardClickListener;
        GameField f = field;
        if (listener == null || f == null) return;
        float[] ray = buildRay(x, y);
        if (ray == null) return;
        try {
            int[] hit = hitCard(ray, f);
            if (hit != null) {
                listener.onFieldLongPress(hit[0], hit[1], hit[2]);
            }
        } catch (Throwable ignored) {
        }
    }

    /** 命中顺序与 Canvas 版一致：每方 怪兽/魔陷区 → 手牌 → 堆叠区 */
    private int[] hitCard(float[] ray, GameField f) {
        try {
            float[] g = new float[2];
            boolean gOk = planeHit(ray, 0.02f, g);
            float[] hp = new float[2];
            boolean hOk = planeHit(ray, 0.45f, hp);

            for (int p = 0; p < 2; p++) {
                if (gOk) {
                    for (int loc : new int[]{0x04, 0x08}) {
                        int max = (loc == 0x04) ? GameField.MAX_MONSTER_ZONE : GameField.MAX_SPELL_ZONE;
                        for (int i = 0; i < max; i++) {
                            if (zoneContains(p, loc, i, g[0], g[1]) && f.getCard(p, loc, i) != null) {
                                return new int[]{p, loc, i};
                            }
                        }
                    }
                }
                if (hOk) {
                    List<GameField.ClientCard> hand = f.players[p].hand;
                    for (int i = hand.size() - 1; i >= 0; i--) {
                        GameField.ClientCard c;
                        try {
                            c = hand.get(i);
                        } catch (Throwable e) {
                            continue;
                        }
                        if (c == null) continue;
                        if (Math.abs(hp[0] - c.curX) <= 0.45f && Math.abs(hp[1] - c.curY) <= 0.9f) {
                            return new int[]{p, 0x02, i};
                        }
                    }
                }
                if (gOk) {
                    for (int loc : new int[]{0x01, 0x40, 0x10, 0x20}) {
                        float[] c = pileCenter(p, loc);
                        if (c == null) continue;
                        if (Math.abs(g[0] - c[0]) <= PILE_W / 2f && Math.abs(g[1] - c[1]) <= PILE_H / 2f) {
                            int count = f.getCardCount(p, loc);
                            if (count > 0) return new int[]{p, loc, count - 1};
                        }
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    // ==================== 场地几何（与 GameField/旧版 getZoneRectLocalF 一致）====================

    private static float[] zoneCenter(int player, int loc, int seq) {
        if (loc == 0x04) {
            if (player == 0) {
                float cx = seq < 5 ? 1.75f + 1.1f * seq : (seq == 5 ? 2.85f : 5.05f);
                float cy = seq < 5 ? 1.4f : 0f;
                return new float[]{cx, cy};
            }
            float cx = seq < 5 ? 6.15f - 1.1f * seq : (seq == 5 ? 5.05f : 2.85f);
            float cy = seq < 5 ? -1.4f : 0f;
            return new float[]{cx, cy};
        }
        if (player == 0) {
            float cx = seq < 5 ? 1.75f + 1.1f * seq
                    : (seq == 5 ? 0.6f : (seq == 6 ? 0.6f : 8.3f));
            float cy = seq < 5 ? 2.6f : (seq == 5 ? 2.0f : 0.7f);
            return new float[]{cx, cy};
        }
        float cx = seq < 5 ? 6.15f - 1.1f * seq
                : (seq == 5 ? 7.3f : (seq == 6 ? 7.3f : -0.4f));
        float cy = seq < 5 ? -2.6f : (seq == 5 ? -2.0f : -0.7f);
        return new float[]{cx, cy};
    }

    private static float[] pileCenter(int player, int loc) {
        if (player == 0) {
            if (loc == 0x01) return new float[]{7.3f, 3.3f};
            if (loc == 0x10) return new float[]{7.3f, 2.0f};
            if (loc == 0x20) return new float[]{7.3f, 0.7f};
            if (loc == 0x40) return new float[]{0.6f, 3.3f};
            return null;
        }
        if (loc == 0x01) return new float[]{0.6f, -3.3f};
        if (loc == 0x10) return new float[]{0.6f, -2.0f};
        if (loc == 0x20) return new float[]{0.6f, -0.7f};
        if (loc == 0x40) return new float[]{7.3f, -3.3f};
        return null;
    }

    private static boolean zoneContains(int player, int loc, int seq, float x, float y) {
        float[] c = zoneCenter(player, loc, seq);
        return Math.abs(x - c[0]) <= ZONE_W / 2f && Math.abs(y - c[1]) <= ZONE_H / 2f;
    }

    private static int zoneBitPos(int player, int location, int sequence) {
        int base = (player == 0) ? 0 : 16;
        if (location == 0x04) return base + sequence;
        if (location == 0x08) {
            if (sequence < 6) return base + 8 + sequence;
            if (sequence == 6) return base + 14;
            if (sequence == 7) return base + 15;
        }
        return -1;
    }

    private static int pendulumMode(GameField.ClientCard c) {
        if (c.location == 0x08 && (c.sequence == 6 || c.sequence == 7) && c.isFaceUp() && c.code != 0) {
            return c.sequence == 6 ? 1 : 2;
        }
        return 0;
    }

    private static int pendulumScale(GameField.ClientCard c) {
        return Math.max(0, Math.min(13, c.sequence == 6 ? c.lScale : c.rScale));
    }

    // ==================== 着色器工具 / 高刷新率 / 生命周期 ====================

    private static int loadShader(int type, String src) {
        int s = GLES30.glCreateShader(type);
        GLES30.glShaderSource(s, src);
        GLES30.glCompileShader(s);
        int[] st = new int[1];
        GLES30.glGetShaderiv(s, GLES30.GL_COMPILE_STATUS, st, 0);
        if (st[0] == 0) {
            String log = GLES30.glGetShaderInfoLog(s);
            GLES30.glDeleteShader(s);
            throw new RuntimeException("Shader compile failed: " + log);
        }
        return s;
    }

    private static int createProgram(String vs, String fs) {
        int p = GLES30.glCreateProgram();
        GLES30.glAttachShader(p, loadShader(GLES30.GL_VERTEX_SHADER, vs));
        GLES30.glAttachShader(p, loadShader(GLES30.GL_FRAGMENT_SHADER, fs));
        GLES30.glLinkProgram(p);
        int[] st = new int[1];
        GLES30.glGetProgramiv(p, GLES30.GL_LINK_STATUS, st, 0);
        if (st[0] == 0) {
            String log = GLES30.glGetProgramInfoLog(p);
            GLES30.glDeleteProgram(p);
            throw new RuntimeException("Program link failed: " + log);
        }
        return p;
    }

    /** 请求屏幕最高刷新率显示模式（高刷屏跑满 90/120/144Hz 的前提） */
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        requestHighRefreshRate();
    }

    private void requestHighRefreshRate() {
        try {
            Context ctx = getContext();
            if (!(ctx instanceof Activity)) return;
            Activity act = (Activity) ctx;
            Display display = act.getWindowManager().getDefaultDisplay();
            Display.Mode[] modes = display.getSupportedModes();
            Display.Mode best = null;
            for (Display.Mode m : modes) {
                if (best == null || m.getRefreshRate() > best.getRefreshRate()) best = m;
            }
            if (best == null) return;
            WindowManager.LayoutParams lp = act.getWindow().getAttributes();
            if (lp.preferredDisplayModeId != best.getModeId()) {
                lp.preferredDisplayModeId = best.getModeId();
                act.getWindow().setAttributes(lp);
            }
        } catch (Throwable ignored) {
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        texExecutor.shutdownNow();
        super.onDetachedFromWindow();
    }
}