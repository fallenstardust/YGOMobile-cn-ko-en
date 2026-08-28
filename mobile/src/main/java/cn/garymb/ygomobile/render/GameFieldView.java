package cn.garymb.ygomobile.render;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.util.AttributeSet;
import android.view.Display;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.WindowManager;

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

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.game.GameField;
import cn.garymb.ygomobile.loader.ImageLoader;

/**
 * 决斗场渲染视图（路线 B：OpenGL ES 3.0 真 3D 透视）。
 * <p>
 * 世界坐标系直接采用 gframe 场地坐标：X∈[-1.25,9.15]（与 field3.png 4:3 版面锁定横向放大），Y∈[-3.9,3.9]（+Y 朝向我方），Z 为高度。
 * 棋盘/区域槽/堆叠区/双方手卡/场上卡全部在同一透视相机内绘制，近大远小由 GPU 完成；
 * 卡片位置与旋转直接取 {@link GameField.ClientCard} 的 curX/curY/curZ/curRot*，
 * 与 ClientField::GetCardLocation 数值完全一致。
 * <p>
 * 公开 API 与原 Canvas 版保持一致，调用方（GameFieldViewController/GameFieldController/布局 XML）无需修改。
 */
public class GameFieldView extends GLSurfaceView implements GLSurfaceView.Renderer {

    private static final String TAG = "GameFieldView";

    public interface OnCardClickListener {
        void onCardClick(int player, int location, int sequence, float tapX, float tapY);

        void onZoneClick(int player, int location, int sequence, float tapX, float tapY);

        void onFieldLongPress(int player, int location, int sequence);
    }

    /**
     * 场内绘制的阶段按钮点击回调（当前阶段按钮仅作指示，不产生回调）
     */
    public interface OnPhaseButtonListener {
        void onPhaseNextClicked();

        void onPhaseEpClicked();
    }

    // === 场地坐标常量（与 field3.png 版面锁定：宽 10.4 = 7.8×880/660，贴图内格子恰为正方形）===
    private static final float FIELD_CENTER_X = 3.95f;
    private static final float X_SCALE = 1.217f / 1.1f;
    private static final float FIELD_X_MIN = -1.25f;
    private static final float FIELD_X_MAX = 9.15f;
    private static final float FIELD_Y_MIN = -3.9f;
    private static final float FIELD_Y_MAX = 3.9f;
    // 卡片世界尺寸：严格 177:254 比例
    private static final float CARD_W = 0.8f;
    private static final float CARD_H = 0.8f * 254f / 177f;
    // 区域槽尺寸：254×254 规格正方形（field3.png 锁定后边长 1.217），怪兽/魔陷/额外怪兽/堆叠区共用
    private static final float ZONE_W = 1.217f;
    private static final float ZONE_H = 1.217f;
    private static final float PILE_W = 1.217f;
    private static final float PILE_H = 1.217f;

    /**
     * 与 GameField.fx 一致：以场地中心为轴的 254×254 规格横向缩放（绘制/拾取共用，保证命中不偏）
     */
    private static float fx(float x) {
        return FIELD_CENTER_X + (x - FIELD_CENTER_X) * X_SCALE;
    }

    /**
     * 空间 X 镜像：引擎场地坐标约定 +X→屏幕右（layout_game_right：zone_p0_m0 最左、DECK 右列），
     * 而 +Y 侧相机天然把 +X 映射到屏幕左，故绘制时翻转所有几何的 X（拾取仍用真实坐标），
     * 复现桌面版方位。
     */
    private static float mirrorX(float x) {
        return FIELD_X_MIN + FIELD_X_MAX - x;
    }

    // === 相机参数（俯仰角可由设置调整，默认俯视 60°）===
    public static final float DEFAULT_CAMERA_ELEVATION = 60f;
    public static final float MIN_CAMERA_ELEVATION = 35f;
    public static final float MAX_CAMERA_ELEVATION = 75f;
    private static final String PREF_CAMERA_ELEVATION = "camera_elevation";
    // 视点到注视点距离：与俯仰角共同决定相机位置（越高越俯视）
    private static final float CAMERA_DISTANCE = 7.6f;
    private static final float CAM_X = 3.95f;
    private static final float CAM_LOOK_Y = 0.3f;
    // 纵向取景锚点：对方手卡上缘贴上缘；下锚点取场地近端，我方手卡后移后底边略高于视图底边
    private static final float ANCHOR_TOP_Y = -3.8f, ANCHOR_TOP_Z = 0.95f;
    private static final float ANCHOR_BOTTOM_Y = 3.9f, ANCHOR_BOTTOM_Z = -0.1f;
    // 我方手卡相对决斗场向后平移量：使手卡底边略高于视图底边
    private static final float HAND_SELF_Y_SHIFT = 0.7f;
    // 选中手卡抬高量
    private static final float HAND_LIFT = 0.3f;
    // 对方手卡确认：翻面展示时长与洗切动画时长
    private static final long OPP_REVEAL_MS = 2500L;
    private static final long SHUFFLE_MS = 700L;
    // 场地视觉倍率：1.0=场地近端左右边缘精确贴合 GameFieldView 左右缘（field3.png 满幅不裁切）
    private static final float FIELD_ZOOM = 1.0f;
    private volatile float cameraElevationDeg = DEFAULT_CAMERA_ELEVATION;
    private volatile boolean cameraDirty = false;

    // === 着色器（ES 3.0 / GLSL 300 es）===
    private static final String VS =
            "#version 300 es\n" +
                    "layout(location=0) in vec2 aPos;\n" +
                    "layout(location=1) in vec2 aUV;\n" +
                    "uniform mat4 uMVP;\n" +
                    "uniform float uFlipU;\n" +
                    "uniform float uFlipV;\n" +
                    "out vec2 vUV;\n" +
                    "void main(){ vUV=vec2(mix(aUV.x,1.0-aUV.x,uFlipU),mix(aUV.y,1.0-aUV.y,uFlipV)); gl_Position=uMVP*vec4(aPos,0.0,1.0); }\n";

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

    // 单位矩形（XY 平面，法线 +Z；v=0 在局部 -Y 边即 Bitmap 顶部；u 为标准布局。
    // 场地卡 uFlipU=1 抵消相机 +X→屏幕左 镜像；手卡 billboard 局部+Y=屏幕上方，
    // 需 uFlipU=0 + uFlipV=1 恢复正向贴图）
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

    // === 阶段按钮（场内绘制：主线程写状态，GL 线程读取绘制/命中）===
    private volatile OnPhaseButtonListener phaseButtonListener;
    private volatile boolean phaseCurrentVisible = false;
    private volatile String phaseCurrentLabel = "";
    private volatile String phaseNextLabel = "";
    private volatile boolean phaseEpVisible = false;

    // === GL 资源 ===
    private int texProg, colorProg;
    private int texLocMVP, texLocTint, texLocTex, texLocFlipU, texLocFlipV;
    private int colorLocMVP, colorLocColor;
    private int vao;
    private final HashMap<Long, Integer> textures = new HashMap<>();
    private final Set<Long> requested = Collections.newSetFromMap(new ConcurrentHashMap<Long, Boolean>());
    // 解码失败重试状态（仅 GL 线程访问）：上次请求时间 + 已尝试次数
    private final HashMap<Long, Long> texRetryTime = new HashMap<>();
    private final HashMap<Long, Integer> texRetries = new HashMap<>();
    private final ConcurrentLinkedQueue<PendingUpload> pendingUploads = new ConcurrentLinkedQueue<>();
    private ExecutorService texExecutor = Executors.newSingleThreadExecutor();
    // 阶段按钮标签文字纹理键（仅 GL 线程访问，负值递减，与卡图/场地/卡背键域不冲突）
    private final HashMap<String, Long> phaseLabelKeys = new HashMap<>();
    private long phaseLabelKeySeq = -1000L;

    // === 矩阵与相机缓存 ===
    private final float[] mProj = new float[16];
    private final float[] mView = new float[16];
    private final float[] mVP = new float[16];
    private final float[] mMVP = new float[16];
    private final float[] mModel = new float[16];
    private final float[] mModelTmp = new float[16];

    // 阶段按钮屏幕像素正交投影（按钮平行屏幕，与透视相机解耦）
    private final float[] mOrthoVP = new float[16];
    // 相机姿态矩阵（view 旋转部分的转置）：手卡 billboard 平行屏幕用
    private final float[] mCamRot = new float[16];
    private final Object camLock = new Object();
    private final float[] pickInvVP = new float[16];
    private volatile int viewW = 1, viewH = 1;
    // 相机重建通知（GL 线程重建相机后，覆盖层需重新锚定阶段按钮位置）
    private volatile Runnable onCameraChangedListener;

    private long lastFrameNs = 0;
    private long animTimeMs = 0;
    private GestureDetector gestureDetector;

    private static class PendingUpload {
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
        // 透明背景：GL Surface 置顶合成，透明像素处透出窗口背景（bg.jpg）与 HUD
        setZOrderOnTop(true);
        getHolder().setFormat(PixelFormat.TRANSLUCENT);
        setRenderer(this);
        setRenderMode(RENDERMODE_CONTINUOUSLY);
        try {
            setPreserveEGLContextOnPause(true);
        } catch (Throwable ignored) {
        }

        try {
            cameraElevationDeg = AppsSettings.get().getIntSettings(
                    PREF_CAMERA_ELEVATION, Math.round(DEFAULT_CAMERA_ELEVATION));
            cameraElevationDeg = Math.max(MIN_CAMERA_ELEVATION,
                    Math.min(MAX_CAMERA_ELEVATION, cameraElevationDeg));
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

    /**
     * 连续渲染模式自带动画循环，保留接口仅为兼容
     */
    public void startAnimationLoop() {
    }

    /**
     * 相机/表面重建监听：阶段按钮覆盖层据此重新锚定（GL 线程触发，回调经 post 切回主线程）
     */
    public void setOnCameraChangedListener(Runnable listener) {
        this.onCameraChangedListener = listener;
    }

    /**
     * 场地中轴（双方怪兽区之间，世界坐标 (FIELD_CENTER_X, 0, 0)，X 镜像对中轴无影响）
     * 投影到屏幕像素坐标（相对本 View 左上角），供阶段按钮行锚定；相机未就绪返回 null
     */
    public float[] projectFieldMidline() {
        return projectWorldPoint(FIELD_CENTER_X, 0f, 0f);
    }

    /**
     * 世界坐标（绘制空间：x 需为镜像后坐标，与 drawCard/drawZoneSlots 传入 mVP 前一致）
     * 投影到屏幕像素坐标；相机未就绪返回 null。camLock 保护，任意线程可调用
     */
    public float[] projectWorldPoint(float x, float y, float z) {
        float[] vp = new float[16];
        int w, h;
        synchronized (camLock) {
            System.arraycopy(mVP, 0, vp, 0, 16);
            w = viewW;
            h = viewH;
        }
        if (w <= 1 || h <= 1) return null;
        float[] v = {x, y, z, 1f};
        float[] o = new float[4];
        Matrix.multiplyMV(o, 0, vp, 0, v, 0);
        if (Math.abs(o[3]) < 1e-6f) return null;
        float nx = o[0] / o[3], ny = o[1] / o[3];
        return new float[]{(nx + 1f) * 0.5f * w, (1f - ny) * 0.5f * h};
    }

    /**
     * 场内阶段按钮点击监听（下一阶段/结束阶段；当前阶段按钮仅指示不可点击）
     */
    public void setPhaseButtonListener(OnPhaseButtonListener listener) {
        this.phaseButtonListener = listener;
    }

    /**
     * 场内阶段按钮显示状态（一次全量下发）：
     * 当前阶段按钮（常按下样式、不可点击）+ 下一阶段按钮（空文本=隐藏）+ 结束阶段按钮显隐
     */
    public void setPhaseDisplay(boolean currentVisible, String currentLabel,
                                String nextLabel, boolean epVisible) {
        phaseCurrentVisible = currentVisible;
        phaseCurrentLabel = currentLabel == null ? "" : currentLabel;
        phaseNextLabel = nextLabel == null ? "" : nextLabel;
        phaseEpVisible = epVisible;
        requestRender();
    }

    /**
     * 动画倍率：1=原速，2=2 倍速（时间驱动，任意刷新率下速度一致）
     */
    public void setAnimationSpeed(float multiplier) {
        animSpeedMultiplier = Math.max(0.25f, multiplier);
    }

    /**
     * 视角参数：俯仰角（度，35~75），越大越接近正俯视；持久化到设置并即时生效
     */
    public void setCameraElevation(float degrees) {
        float v = Math.max(MIN_CAMERA_ELEVATION, Math.min(MAX_CAMERA_ELEVATION, degrees));
        cameraElevationDeg = v;
        try {
            AppsSettings.get().saveIntSettings(PREF_CAMERA_ELEVATION, Math.round(v));
        } catch (Throwable ignored) {
        }
        cameraDirty = true;
        requestRender();
    }

    public float getCameraElevation() {
        return cameraElevationDeg;
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
        GLES30.glClearColor(0f, 0f, 0f, 0f);
        GLES30.glEnable(GLES30.GL_DEPTH_TEST);
        GLES30.glDisable(GLES30.GL_CULL_FACE);

        texProg = createProgram(VS, FS_TEX);
        colorProg = createProgram(VS, FS_COLOR);
        texLocMVP = GLES30.glGetUniformLocation(texProg, "uMVP");
        texLocTint = GLES30.glGetUniformLocation(texProg, "uTint");
        texLocTex = GLES30.glGetUniformLocation(texProg, "uTex");
        texLocFlipU = GLES30.glGetUniformLocation(texProg, "uFlipU");
        texLocFlipV = GLES30.glGetUniformLocation(texProg, "uFlipV");
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
        texRetryTime.clear();
        texRetries.clear();
        phaseLabelKeys.clear();
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int w, int h) {
        viewW = w;
        viewH = h;
        GLES30.glViewport(0, 0, w, h);
        updateCamera();
    }

    /**
     * 由俯仰角参数重建相机：eye 按(俯仰角, 距离)球面布置；注视方向取
     * “对方手卡上缘/己方手卡下缘”两锚点方向的角平分线，FOV 取两锚点夹角；
     * 视点距离迭代求解，使场地近端左右边缘尽量贴合屏幕左右缘（场地宽度与
     * GameFieldView 实际宽度一致），竖屏/超宽屏受几何限制时取最优近似。
     * 拾取矩阵与手卡 billboard 姿态同步重建。
     */
    private void updateCamera() {
        int w = viewW, h = viewH;
        if (w <= 1 || h <= 1) return;
        float aspect = (float) w / h;
        float th = (float) Math.toRadians(cameraElevationDeg);
        float cth = (float) Math.cos(th), sth = (float) Math.sin(th);

        float D = CAMERA_DISTANCE;
        float eyeY = 0, eyeZ = 0, dY = 0, dZ = -1, fovy = 60f;
        for (int i = 0; i < 6; i++) {
            eyeY = CAM_LOOK_Y + cth * D;
            eyeZ = sth * D;
            float v1y = ANCHOR_TOP_Y - eyeY, v1z = ANCHOR_TOP_Z - eyeZ;
            float v2y = ANCHOR_BOTTOM_Y - eyeY, v2z = ANCHOR_BOTTOM_Z - eyeZ;
            float l1 = (float) Math.sqrt(v1y * v1y + v1z * v1z);
            float l2 = (float) Math.sqrt(v2y * v2y + v2z * v2z);
            if (l1 < 1e-4f || l2 < 1e-4f) break;
            float d1y = v1y / l1, d1z = v1z / l1, d2y = v2y / l2, d2z = v2z / l2;
            float dot = Math.max(-1f, Math.min(1f, d1y * d2y + d1z * d2z));
            fovy = (float) Math.toDegrees(Math.acos(dot)) * 1.04f;
            float tanH = (float) Math.tan(Math.toRadians(fovy * 0.5f)) * aspect;
            float by = d1y + d2y, bz = d1z + d2z;
            float bl = (float) Math.sqrt(by * by + bz * bz);
            if (bl < 1e-4f) break;
            dY = by / bl;
            dZ = bz / bl;
            // 场地近端(y=FIELD_Y_MAX)沿视线深度 depth(D)=K+b*D 为线性，令 半宽*ZOOM/depth==tanH 解出 D
            float K = (FIELD_Y_MAX - CAM_LOOK_Y) * dY;
            float b = -(cth * dY + sth * dZ);
            if (b > 1e-4f && tanH > 1e-4f) {
                D = ((FIELD_X_MAX - FIELD_X_MIN) * 0.5f * FIELD_ZOOM / tanH - K) / b;
                D = Math.max(4.2f, Math.min(14f, D));
            }
        }
        eyeY = CAM_LOOK_Y + cth * D;
        eyeZ = sth * D;
        float v1y = ANCHOR_TOP_Y - eyeY, v1z = ANCHOR_TOP_Z - eyeZ;
        float v2y = ANCHOR_BOTTOM_Y - eyeY, v2z = ANCHOR_BOTTOM_Z - eyeZ;
        float l1 = (float) Math.sqrt(v1y * v1y + v1z * v1z);
        float l2 = (float) Math.sqrt(v2y * v2y + v2z * v2z);
        if (l1 < 1e-4f || l2 < 1e-4f) return;
        float d1y = v1y / l1, d1z = v1z / l1, d2y = v2y / l2, d2z = v2z / l2;
        float dot = Math.max(-1f, Math.min(1f, d1y * d2y + d1z * d2z));
        fovy = (float) Math.toDegrees(Math.acos(dot)) * 1.04f;
        float halfTan = (float) Math.tan(Math.toRadians(fovy * 0.5f));
        float by = d1y + d2y, bz = d1z + d2z;
        float bl = (float) Math.sqrt(by * by + bz * bz);
        if (bl < 1e-4f) return;
        dY = by / bl;
        dZ = bz / bl;
        // 横向兜底：场地按 ZOOM 倍宽度入镜（更近的视觉）
        float depthNear = (FIELD_Y_MAX - eyeY) * dY + (0f - eyeZ) * dZ;
        float needH = (FIELD_X_MAX - FIELD_X_MIN) * 0.5f * FIELD_ZOOM / Math.max(0.5f, depthNear);
        if (needH / aspect > halfTan) halfTan = needH / aspect;
        if (halfTan > 1.6f) halfTan = 1.6f;
        fovy = (float) Math.toDegrees(2.0 * Math.atan(halfTan));

        Matrix.perspectiveM(mProj, 0, fovy, aspect, 0.5f, 100f);
        Matrix.setLookAtM(mView, 0, CAM_X, eyeY, eyeZ,
                CAM_X, eyeY + dY, eyeZ + dZ, 0f, 0f, 1f);
        Matrix.multiplyMM(mVP, 0, mProj, 0, mView, 0);

        synchronized (camLock) {
            Matrix.invertM(pickInvVP, 0, mVP, 0);
        }

        // 手卡 billboard 姿态：view 旋转部分的转置（列=相机 right/up/backward 轴）
        mCamRot[0] = mView[0];
        mCamRot[1] = mView[4];
        mCamRot[2] = mView[8];
        mCamRot[3] = 0f;
        mCamRot[4] = mView[1];
        mCamRot[5] = mView[5];
        mCamRot[6] = mView[9];
        mCamRot[7] = 0f;
        mCamRot[8] = mView[2];
        mCamRot[9] = mView[6];
        mCamRot[10] = mView[10];
        mCamRot[11] = 0f;
        mCamRot[12] = 0f;
        mCamRot[13] = 0f;
        mCamRot[14] = 0f;
        mCamRot[15] = 1f;

        // 相机重建完成：通知覆盖层重新定位阶段按钮（post 到主线程执行）
        Runnable camListener = onCameraChangedListener;
        if (camListener != null) post(camListener);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        long now = System.nanoTime();
        float dt = lastFrameNs == 0 ? 1f / 60f : (now - lastFrameNs) / 1e9f;
        lastFrameNs = now;
        if (dt > 0.1f) dt = 0.1f;
        animTimeMs = System.currentTimeMillis();

        if (cameraDirty) {
            cameraDirty = false;
            updateCamera();
        }

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

        GLES30.glEnable(GLES30.GL_BLEND);
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA);
        try {
            drawFieldBoard(f);
        } catch (Throwable ignored) {
        }
        drawZoneSlots();
        try {
            drawFieldCards(f);
        } catch (Throwable ignored) {
        }
        GLES30.glDepthMask(false);
        drawHighlights();
        drawPhaseButtons();
        GLES30.glDepthMask(true);
    }

    // ==================== 绘制 ====================

    /**
     * 场地底板：对齐 gframe drawing.cpp L326/L369 ——
     * rule=(duel_rule>=4)?1:0 选 field3/field2；显示场地魔法卡时改用 field-transparent 版。
     * 其余区域保持透明，透出窗口背景。
     */
    private void drawFieldBoard(GameField f) {
        int rule = (f.dInfo.duelRule >= 4) ? 1 : 0;
        boolean transparent = fieldSpellDisplayed(f);
        int tex = obtainFieldTexture(rule, transparent);
        float w = FIELD_X_MAX - FIELD_X_MIN;
        float h = FIELD_Y_MAX - FIELD_Y_MIN;
        float cx = mirrorX((FIELD_X_MIN + FIELD_X_MAX) / 2f);
        if (tex > 0) {
            Matrix.setIdentityM(mModel, 0);
            Matrix.translateM(mModel, 0, cx, 0f, 0f);
            Matrix.scaleM(mModel, 0, w, h, 1f);
            drawQuadTex(mModel, tex, 1f);
        } else {
            // 贴图加载完成前的兜底：半透明底色，不遮挡窗口背景
            drawFlatQuad(cx, 0f, 0f, w, h, 0.05f, 0.09f, 0.16f, 0.55f);
        }
    }

    /**
     * gframe drawField 语义：任一方场地魔法区(szone seq5)存在正面场地卡时，用 transparent 版底图
     */
    private static boolean fieldSpellDisplayed(GameField f) {
        try {
            for (int p = 0; p < 2; p++) {
                List<GameField.ClientCard> sz = f.players[p].spellZone;
                if (sz.size() > 5) {
                    GameField.ClientCard c = sz.get(5);
                    if (c != null && c.isFaceUp() && c.code != 0) return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private void drawZoneSlots() {
        float pulse = 0.10f + 0.08f * (float) Math.sin(animTimeMs * 0.002);
        for (int p = 0; p < 2; p++) {
            for (int i = 0; i < 7; i++) {
                float[] c = zoneCenter(p, 0x04, i);
                drawFlatQuad(mirrorX(c[0]), c[1], 0.004f, ZONE_W, ZONE_H, 0f, 0.78f, 0.94f, pulse);
            }
            for (int i = 0; i <= 5; i++) {
                float[] c = zoneCenter(p, 0x08, i);
                drawFlatQuad(mirrorX(c[0]), c[1], 0.004f, ZONE_W, ZONE_H, 0f, 0.78f, 0.94f, pulse);
            }
            for (int loc : new int[]{0x01, 0x10, 0x20, 0x40}) {
                float[] c = pileCenter(p, loc);
                if (c == null) continue;
                drawFlatQuad(mirrorX(c[0]), c[1], 0.004f, PILE_W, PILE_H, 0f, 0.78f, 0.94f, pulse);
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
        drawFlatQuad(mirrorX(c[0]), c[1], 0.03f, ZONE_W, ZONE_H, 0f, 1f, 0.39f, alpha);
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

    /**
     * 线程安全遍历：索引式 + 全量兜底，网络线程并发增删时最多丢一帧
     */
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
            int skip = Math.max(0, n - 20); // 堆叠区绘制最上 20 层呈现厚度，z 间距已在 GameField 封顶
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

    /**
     * 双面卡片：背面 cover + 正面卡图。
     * 位置取 getCardLocation 动画值并做 X 镜像；旋转按 Y→X→Z 合成、Y/Z 轴取反
     * （空间镜像使绕 Y/Z 旋转反向），保证 gframe 各位置的面朝：
     * 对方暗手牌（rotX+rotY=π）cover 朝相机、守备/盖放/堆叠区朝向均正确。
     */
    private void drawCard(GameField.ClientCard c) {
        if (c == null) return;
        float alpha = Math.max(0f, Math.min(1f, c.curAlpha / 255f));
        if (alpha <= 0.01f) return;

        boolean isHand = c.location == 0x02;
        if (isHand) {
            // 手卡平行屏幕：姿态取相机旋转；我方手卡后移、选中抬高（沿相机 up 轴，Y/Z 分量与命中检测一致）
            Matrix.setIdentityM(mModel, 0);
            Matrix.translateM(mModel, 0, mirrorX(c.curX),
                    handY(c) + mCamRot[5] * handLift(c), c.curZ + handLiftZ(c));
            Matrix.multiplyMM(mModelTmp, 0, mModel, 0, mCamRot, 0);
            System.arraycopy(mModelTmp, 0, mModel, 0, 16);
            Matrix.scaleM(mModel, 0, CARD_W, CARD_H, 1f);
        } else {
            // 堆叠区各层加确定性微错位，侧面露出卡边呈现厚度
            boolean isPile = c.location == 0x01 || c.location == 0x10
                    || c.location == 0x20 || c.location == 0x40;
            float jx = isPile ? ((c.sequence % 3) - 1) * 0.012f : 0f;
            float jy = isPile ? (((c.sequence / 3) % 3) - 1) * 0.012f : 0f;
            Matrix.setIdentityM(mModel, 0);
            Matrix.translateM(mModel, 0, mirrorX(c.curX) + jx, c.curY + jy, c.curZ);
            Matrix.rotateM(mModel, 0, (float) Math.toDegrees(-c.curRotY), 0f, 1f, 0f);
            Matrix.rotateM(mModel, 0, (float) Math.toDegrees(c.curRotX), 1f, 0f, 0f);
            Matrix.rotateM(mModel, 0, (float) Math.toDegrees(-c.curRotZ), 0f, 0f, 1f);
            Matrix.scaleM(mModel, 0, CARD_W, CARD_H, 1f);
        }

        int glow = pickGlowColor(c);
        if (glow != 0) drawGlow(glow, alpha);

        int code = c.code != 0 ? c.code : (c.is_moving ? c.chain_code : 0);
        if (isHand) {
            if (code > 0 && (c.controler == 0 || c.isFaceUp())) {
                int tex = obtainTexture(code, pendulumMode(c), pendulumScale(c));
                if (tex > 0) {
                    drawQuadTex(mModel, tex, alpha, 0f, 1f);
                } else {
                    drawQuadColor(mModel, 0.35f, 0.35f, 0.40f, alpha);
                }
            } else {
                // 对方手卡：卡背正对相机
                System.arraycopy(mModel, 0, mModelTmp, 0, 16);
                Matrix.translateM(mModelTmp, 0, mModelTmp, 0, 0f, 0f, 0.002f);
                int coverTex = obtainCover(true);
                if (coverTex > 0) {
                    drawQuadTex(mModelTmp, coverTex, alpha, 0f, 1f);
                } else {
                    drawQuadColor(mModelTmp, 0.24f, 0.18f, 0.13f, alpha);
                }
            }
            return;
        }

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

        // 正面（手牌区 position 不含 POS_FACEUP 位，code>0 时同样按正面渲染）
        boolean faceUp = c.isFaceUp() || c.location == 0x02;
        if (faceUp && code > 0) {
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
        if (isSelectedCard(c)) return 0xFFFFFF00;
        if (c.is_selected) return 0xFFFFFF00;
        if (c.is_highlighting) return 0xFF00FFFF;
        if (c.is_showequip || c.is_showtarget || c.is_showchaintarget) return 0xFFFF4444;
        if (c.is_selectable) return 0xFFFFD700;
        return 0;
    }

    private boolean isSelectedCard(GameField.ClientCard c) {
        return c.controler == selectedPlayer && c.location == selectedLocation
                && c.sequence == selectedSequence;
    }

    /**
     * 我方手卡后移 HAND_SELF_Y_SHIFT，对方手卡保持原位
     */
    private static float handY(GameField.ClientCard c) {
        return c.curY - (c.controler == 0 ? HAND_SELF_Y_SHIFT : 0f);
    }

    /**
     * 选中手卡抬升量：未选中为 0，选中为 HAND_LIFT（沿相机 up 轴抬升，绘制/命中共用）
     */
    private float handLift(GameField.ClientCard c) {
        return isSelectedCard(c) ? HAND_LIFT : 0f;
    }

    /**
     * 选中手卡抬升的 Z 分量：沿相机 up 轴抬升，Z 分量 = mCamRot[6] × 抬升量
     */
    private float handLiftZ(GameField.ClientCard c) {
        return mCamRot[6] * handLift(c);
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
        drawQuadTex(model, texId, alpha, 1f, 0f);
    }

    private void drawQuadTex(float[] model, int texId, float alpha, float flipU, float flipV) {
        if (texId <= 0) return;
        GLES30.glUseProgram(texProg);
        Matrix.multiplyMM(mMVP, 0, mVP, 0, model, 0);
        GLES30.glUniformMatrix4fv(texLocMVP, 1, false, mMVP, 0);
        GLES30.glUniform4f(texLocTint, 1f, 1f, 1f, alpha);
        GLES30.glUniform1f(texLocFlipU, flipU);
        GLES30.glUniform1f(texLocFlipV, flipV);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texId);
        GLES30.glUniform1i(texLocTex, 0);
        glBindQuadVao();
    }

    private void glBindQuadVao() {
        GLES30.glBindVertexArray(vao);
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, 0, 4);
        GLES30.glBindVertexArray(0);
    }

    private void drawQuadColor(float[] model, float r, float g, float b, float a) {
        GLES30.glUseProgram(colorProg);
        Matrix.multiplyMM(mMVP, 0, mVP, 0, model, 0);
        GLES30.glUniformMatrix4fv(colorLocMVP, 1, false, mMVP, 0);
        GLES30.glUniform4f(colorLocColor, r, g, b, a);
        glBindQuadVao();
    }

    // 阶段按钮屏幕尺寸（dp）：按钮平行屏幕，与两个额外怪兽区错开摆放（左/中/右三个锚点）
    private static final float PHASE_BTN_W_DP = 32f;
    private static final float PHASE_BTN_H_DP = 18f;
    // Deleted:private static final float PHASE_BTN_GAP_DP = 16f;
    private static final int PHASE_CURRENT = 0, PHASE_NEXT = 1, PHASE_EP = 2;

    /**
     * 阶段按钮布局（避免遮挡额外怪兽区）：
     * 当前阶段按钮 → 左侧额外怪兽区左缘外侧；下一阶段按钮 → 两个额外怪兽区正中（场地中轴）；
     * 结束阶段按钮 → 右侧额外怪兽区右缘外侧。按钮像素宽按中轴行像素比例折算世界半宽定位，
     * 并封顶于两额外怪兽区内侧空隙，确保不压任何场上格子。
     * 绘制（GL 线程）与点击命中（主线程）共用同一确定性布局，保证所见即所点
     */
    private float[][] phaseRects(boolean curVisible, boolean nextVisible, boolean epVisible) {
        float[][] out = new float[3][];
        float d = getResources().getDisplayMetrics().density;
        // 两个额外怪兽区（怪兽区 seq5/6，绘制空间已镜像：seq5 呈现在屏幕左、seq6 在屏幕右）
        float emzL = mirrorX(zoneCenter(0, 0x04, 5)[0]);
        float emzR = mirrorX(zoneCenter(0, 0x04, 6)[0]);
        // 中轴行像素比例：中轴左右各 0.5 世界单位采样
        float[] s0 = projectWorldPoint(FIELD_CENTER_X - 0.5f, 0f, 0f);
        float[] s1 = projectWorldPoint(FIELD_CENTER_X + 0.5f, 0f, 0f);
        if (s0 == null || s1 == null) return out;
        float pxPerWorld = Math.abs(s1[0] - s0[0]);
        if (pxPerWorld < 1e-3f) return out;
        // 两额外怪兽区内侧空隙的像素宽：下一阶段按钮宽度封顶于此
        float[] eL = projectWorldPoint(emzL - ZONE_W / 2f, 0f, 0f);
        float[] eR = projectWorldPoint(emzR + ZONE_W / 2f, 0f, 0f);
        float gapPx = (eL != null && eR != null) ? Math.abs(eL[0] - eR[0]) : Float.MAX_VALUE;
        float bw = Math.min(PHASE_BTN_W_DP * d, Math.max(24f, gapPx - 10f));
        float bh = PHASE_BTN_H_DP * d;
        float halfW = bw / pxPerWorld / 2f;
        float margin = 0.12f;
        if (curVisible) {
            // 左侧额外怪兽区左缘再向外（屏幕更左 = 绘制空间 x 更大）
            float ax = emzL + ZONE_W / 2f + margin + halfW;
            ax = Math.min(ax, FIELD_X_MAX - 0.1f - halfW);
            float[] s = projectWorldPoint(ax, 0f, 0f);
            if (s != null) out[PHASE_CURRENT] = new float[]{s[0], s[1], bw, bh};
        }
        if (nextVisible) {
            float[] s = projectWorldPoint(FIELD_CENTER_X, 0f, 0f);
            if (s != null) out[PHASE_NEXT] = new float[]{s[0], s[1], bw, bh};
        }
        if (epVisible) {
            // 右侧额外怪兽区右缘再向外（屏幕更右 = 绘制空间 x 更小）
            float ax = emzR - ZONE_W / 2f - margin - halfW;
            ax = Math.max(ax, FIELD_X_MIN + 0.1f + halfW);
            float[] s = projectWorldPoint(ax, 0f, 0f);
            if (s != null) out[PHASE_EP] = new float[]{s[0], s[1], bw, bh};
        }
        return out;
    }

    /**
     * 阶段按钮点击命中（优先于卡片/区域拾取）：当前阶段按钮无回调但吞掉点击，避免误触场地
     */
    private boolean handlePhaseButtonTap(float x, float y) {
        OnPhaseButtonListener l = phaseButtonListener;
        if (l == null) return false;
        String cur = phaseCurrentLabel, next = phaseNextLabel;
        boolean curV = phaseCurrentVisible && !cur.isEmpty();
        boolean nextV = !next.isEmpty();
        boolean epV = phaseEpVisible;
        if (!curV && !nextV && !epV) return false;
        float[] anchor = projectFieldMidline();
        if (anchor == null) return false;
        float[][] rects = phaseRects(curV, nextV, epV);
        if (rects[PHASE_NEXT] != null && inPhaseRect(rects[PHASE_NEXT], x, y)) {
            l.onPhaseNextClicked();
            return true;
        }
        if (rects[PHASE_EP] != null && inPhaseRect(rects[PHASE_EP], x, y)) {
            l.onPhaseEpClicked();
            return true;
        }
        return rects[PHASE_CURRENT] != null && inPhaseRect(rects[PHASE_CURRENT], x, y);
    }

    private static boolean inPhaseRect(float[] rect, float x, float y) {
        return Math.abs(x - rect[0]) <= rect[2] / 2f && Math.abs(y - rect[1]) <= rect[3] / 2f;
    }

    /**
     * 阶段按钮绘制：三个按钮分别锚定 左额外怪兽区外侧 / 场地中轴 / 右额外怪兽区外侧，
     * 在屏幕像素正交空间内绘制底板与文字纹理（平行屏幕、固定像素尺寸，不受透视影响），
     * 置于全部场地内容之上（关闭深度测试）
     */
    private void drawPhaseButtons() {
        int w = viewW, h = viewH;
        if (w <= 1 || h <= 1) return;
        String cur = phaseCurrentLabel, next = phaseNextLabel;
        boolean curV = phaseCurrentVisible && !cur.isEmpty();
        boolean nextV = !next.isEmpty();
        boolean epV = phaseEpVisible;
        if (!curV && !nextV && !epV) return;
        float[][] rects = phaseRects(curV, nextV, epV);
        if (rects[PHASE_CURRENT] == null && rects[PHASE_NEXT] == null && rects[PHASE_EP] == null)
            return;
        // 屏幕像素正交投影：y 向下与触摸坐标一致，quad 顶点布局下贴图无需翻转
        Matrix.orthoM(mOrthoVP, 0, 0f, w, h, 0f, -1f, 1f);
        GLES30.glDisable(GLES30.GL_DEPTH_TEST);
        drawPhaseButton(rects[PHASE_CURRENT], cur, true);
        drawPhaseButton(rects[PHASE_NEXT], next, false);
        drawPhaseButton(rects[PHASE_EP], "EP", false);
        GLES30.glEnable(GLES30.GL_DEPTH_TEST);
    }

    /**
     * 单个阶段按钮：描边 + 底板（当前阶段按钮恒按按下态配色）+ 标签文字
     */
    private void drawPhaseButton(float[] rect, String label, boolean pressed) {
        if (rect == null || label == null || label.isEmpty()) return;
        float cx = rect[0], cy = rect[1], bw = rect[2], bh = rect[3];
        drawScreenQuadColor(cx, cy, bw + 3f, bh + 3f, 0.04f, 0.08f, 0.12f, 0.92f);
        if (pressed) {
            drawScreenQuadColor(cx, cy, bw, bh, 0.15f, 0.22f, 0.32f, 0.94f);
        } else {
            drawScreenQuadColor(cx, cy, bw, bh, 0.30f, 0.44f, 0.58f, 0.90f);
        }
        int tex = obtainPhaseLabelTexture(label);
        if (tex > 0) {
            // 标签位图固定 256×80：按 3.2:1 铺展，宽度封顶按钮内宽（短文字两侧留透明区）
            float tw = Math.min(bh * 3.2f, bw * 0.96f);
            drawScreenQuadTex(cx, cy, tw, tw / 3.2f, tex, 1f);
        }
    }

    private void drawScreenQuadColor(float cx, float cy, float w, float h,
                                     float r, float g, float b, float a) {
        GLES30.glUseProgram(colorProg);
        Matrix.setIdentityM(mModel, 0);
        Matrix.translateM(mModel, 0, cx, cy, 0f);
        Matrix.scaleM(mModel, 0, w, h, 1f);
        Matrix.multiplyMM(mMVP, 0, mOrthoVP, 0, mModel, 0);
        GLES30.glUniformMatrix4fv(colorLocMVP, 1, false, mMVP, 0);
        GLES30.glUniform4f(colorLocColor, r, g, b, a);
        glBindQuadVao();
    }

    private void drawScreenQuadTex(float cx, float cy, float w, float h, int texId, float alpha) {
        if (texId <= 0) return;
        GLES30.glUseProgram(texProg);
        Matrix.setIdentityM(mModel, 0);
        Matrix.translateM(mModel, 0, cx, cy, 0f);
        Matrix.scaleM(mModel, 0, w, h, 1f);
        Matrix.multiplyMM(mMVP, 0, mOrthoVP, 0, mModel, 0);
        GLES30.glUniformMatrix4fv(texLocMVP, 1, false, mMVP, 0);
        GLES30.glUniform4f(texLocTint, 1f, 1f, 1f, alpha);
        GLES30.glUniform1f(texLocFlipU, 0f);
        GLES30.glUniform1f(texLocFlipV, 0f);
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0);
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texId);
        GLES30.glUniform1i(texLocTex, 0);
        glBindQuadVao();
    }

    /**
     * 阶段标签文字纹理（仅 GL 线程调用）：首次出现时 Canvas 生成位图入队，
     * 下一帧 drainUploads 上传；上传完成前仅绘制底板兜底
     */
    private int obtainPhaseLabelTexture(String text) {
        Long key = phaseLabelKeys.get(text);
        if (key == null) {
            key = phaseLabelKeySeq--;
            phaseLabelKeys.put(text, key);
            try {
                pendingUploads.offer(new PendingUpload(key, makePhaseLabelBitmap(text), true));
            } catch (Throwable ignored) {
            }
        }
        Integer id = textures.get(key);
        return id != null ? id : -1;
    }

    private static Bitmap makePhaseLabelBitmap(String text) {
        int w = 256, h = 80;
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTextSize(54f);
        p.setFakeBoldText(true);
        float tw = p.measureText(text);
        if (tw > w * 0.9f) p.setTextSize(54f * (w * 0.9f) / tw);
        p.setColor(0xFFFFFFFF);
        p.setTextAlign(Paint.Align.CENTER);
        p.setShadowLayer(3f, 1f, 1f, 0xC0000000);
        cv.drawText(text, w / 2f, h / 2f - (p.ascent() + p.descent()) / 2f, p);
        return bmp;
    }

    // ==================== 纹理（多线程：工作线程解码 → GL 线程上传）====================

    private static long texKey(int code, int mode, int scale) {
        return ((long) mode << 48) | ((long) scale << 40) | (code & 0xFFFFFFFFL);
    }

    private static long fieldTexKey(int rule, boolean transparent) {
        return -10L - (long) rule * 2L - (transparent ? 1L : 0L);
    }

    private int obtainFieldTexture(int rule, boolean transparent) {
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
                // b==null（文件缺失）时不上传：requested 保留，之后走颜色兜底、不重复请求
                if (b != null) pendingUploads.offer(new PendingUpload(key, b, true));
            });
        } catch (Throwable t) {
            requested.remove(key);
        }
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
    private int obtainTexture(int code, int mode, int scale) {
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
        long now = animTimeMs;
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
     * 线程池被 shutdown 后（视图 detach 过）懒重建，避免 RejectedExecutionException 炸 GL 线程
     */
    private synchronized ExecutorService texExecutor() {
        if (texExecutor == null || texExecutor.isShutdown()) {
            texExecutor = Executors.newSingleThreadExecutor();
        }
        return texExecutor;
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

    private void drainUploads() {
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
        // 场内阶段按钮命中优先（当前阶段按钮也吞掉点击）
        if (handlePhaseButtonTap(x, y)) return;
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
                    // 绘制做了 X 镜像，命中点镜像还原后才能与真实 zone 坐标比较
                    g[0] = mirrorX(g[0]);
                    for (int p = 0; p < 2; p++) {
                        for (int loc : new int[]{0x04, 0x08}) {
                            int max = (loc == 0x04) ? GameField.MAX_MONSTER_ZONE : GameField.MAX_SPELL_ZONE;
                            for (int i = 0; i < max; i++) {
                                if (!zoneContains(p, loc, i, g[0], g[1])) continue;
                                int bit = zoneBitPos(p, loc, i);
                                if (bit >= 0 && (mask & (1 << bit)) != 0) {
                                    listener.onZoneClick(p, loc, i, x, y);
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
                listener.onCardClick(hit[0], hit[1], hit[2], x, y);
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

    /**
     * 命中顺序与 Canvas 版一致：每方 怪兽/魔陷区 → 手牌 → 堆叠区
     */
    private int[] hitCard(float[] ray, GameField f) {
        try {
            float[] g = new float[2];
            boolean gOk = planeHit(ray, 0.02f, g);
            // 绘制做了 X 镜像：命中点镜像还原到真实场地坐标后再与卡数据比较
            if (gOk) g[0] = mirrorX(g[0]);

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
                // 手卡平行屏幕：按各卡所在 y 平面求交（抬高沿相机 up 轴，需计入其 y/z 分量）
                List<GameField.ClientCard> hand = f.players[p].hand;
                for (int i = hand.size() - 1; i >= 0; i--) {
                    GameField.ClientCard c;
                    try {
                        c = hand.get(i);
                    } catch (Throwable e) {
                        continue;
                    }
                    if (c == null) continue;
                    float lift = handLift(c);
                    float[] hz = new float[2];
                    if (!planeHitY(ray, handY(c) + mCamRot[5] * lift, hz)) continue;
                    hz[0] = mirrorX(hz[0]);
                    if (Math.abs(hz[0] - c.curX) <= 0.45f
                            && Math.abs(hz[1] - (c.curZ + mCamRot[6] * lift)) <= 0.75f) {
                        return new int[]{p, 0x02, i};
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

    private static boolean planeHitY(float[] ray, float yPlane, float[] outXZ) {
        if (Math.abs(ray[4]) < 1e-6f) return false;
        float t = (yPlane - ray[1]) / ray[4];
        if (t < 0) return false;
        outXZ[0] = ray[0] + ray[3] * t;
        outXZ[1] = ray[2] + ray[5] * t;
        return true;
    }

    // ==================== 场地几何（与 GameField/旧版 getZoneRectLocalF 一致）====================

    private static float[] zoneCenter(int player, int loc, int seq) {
        if (loc == 0x04) {
            if (player == 0) {
                float cx = fx(seq < 5 ? 1.75f + 1.1f * seq : (seq == 5 ? 2.85f : 5.05f));
                float cy = seq < 5 ? 1.4f : 0f;
                return new float[]{cx, cy};
            }
            float cx = fx(seq < 5 ? 6.15f - 1.1f * seq : (seq == 5 ? 5.05f : 2.85f));
            float cy = seq < 5 ? -1.4f : 0f;
            return new float[]{cx, cy};
        }
        if (player == 0) {
            float cx = fx(seq < 5 ? 1.75f + 1.1f * seq
                    : (seq == 5 ? 0.6f : (seq == 6 ? 0.6f : 8.3f)));
            float cy = seq < 5 ? 2.56f : (seq == 5 ? 2.0f : 0.7f);
            return new float[]{cx, cy};
        }
        float cx = fx(seq < 5 ? 6.15f - 1.1f * seq
                : (seq == 5 ? 7.3f : (seq == 6 ? 7.3f : -0.4f)));
        float cy = seq < 5 ? -2.56f : (seq == 5 ? -2.0f : -0.7f);
        return new float[]{cx, cy};
    }

    private static float[] pileCenter(int player, int loc) {
        if (player == 0) {
            if (loc == 0x01) return new float[]{fx(7.3f), 3.3f};
            if (loc == 0x10) return new float[]{fx(7.3f), 2.0f};
            if (loc == 0x20) return new float[]{fx(7.3f), 0.7f};
            if (loc == 0x40) return new float[]{fx(0.6f), 3.3f};
            return null;
        }
        if (loc == 0x01) return new float[]{fx(0.6f), -3.3f};
        if (loc == 0x10) return new float[]{fx(0.6f), -2.0f};
        if (loc == 0x20) return new float[]{fx(0.6f), -0.7f};
        if (loc == 0x40) return new float[]{fx(7.3f), -3.3f};
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

    /**
     * 请求屏幕最高刷新率显示模式（高刷屏跑满 90/120/144Hz 的前提）
     */
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