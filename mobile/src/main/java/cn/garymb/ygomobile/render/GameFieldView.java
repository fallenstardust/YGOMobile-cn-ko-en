package cn.garymb.ygomobile.render;

import android.app.Activity;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.util.AttributeSet;
import android.view.Display;
import android.view.MotionEvent;
import android.view.WindowManager;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.opengles.GL10;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.game.GameField;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;

/**
 * 与 ClientField::GetCardLocation 数值完全一致。
 * <p>
 * 公开 API 与原 Canvas 版保持一致，调用方（GameFieldViewController/GameFieldController/布局 XML）无需修改。
 * <p>
 * 监听收口：卡片/区域点击与长按、阶段按钮、相机变化等所有 listener 均由
 * {@link GameFieldViewController} 实现并注册，手势识别（GestureDetector）同样由控制器持有，
 * 本视图仅保留拾取入口（dispatchTap/dispatchLongPress）与回调触发。
 * <p>
 * XML 可调参数（declare-styleable GameFieldView）：field_camera_elevation / field_camera_distance /
 * field_zoom / field_hand_shift 与预览开关。XML 显式声明的值即运行期正式值，所见即所得；
 * 布局编辑器（isInEditMode）下无法启动 GL，改由 Canvas 绘制等效透视预览。
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

    // === 场地坐标常量：底板矩形与格子尺寸全部取自 GameField（materials.cpp 唯一真值）。
    // 卡片落点、格子、底板贴图网格共用 fx()/恒等 Y 同一仿射映射，三者必然完美重叠 ===
    private static final float FIELD_CENTER_X = 3.95f;
    private static final float FIELD_X_MIN = GameField.fieldBoardMinX();
    private static final float FIELD_X_MAX = GameField.fieldBoardMaxX();
    private static final float FIELD_Y_MIN = GameField.FIELD_TEX_Y_MIN;
    private static final float FIELD_Y_MAX = GameField.FIELD_TEX_Y_MAX;
    // 卡片世界尺寸：严格 177:254 比例
    private static final float CARD_W = 0.8f;
    private static final float CARD_H = 0.8f * 254f / 177f;
    // 区域槽尺寸：与 GameField 格子同源（materials.cpp 1.1×1.2 / 0.8×1.2 经 fx 缩放）
    private static final float ZONE_W = GameField.ZONE_W;
    private static final float ZONE_H = GameField.ZONE_H;
    private static final float PILE_W = GameField.PILE_W;
    private static final float PILE_H = GameField.PILE_H;

    /**
     * 与 GameField.fx 同一映射：格子/卡片/底板共用，保证命中与绘制不偏
     */
    private static float fx(float x) {
        return GameField.fx(x);
    }

    /**
     * 空间 X 镜像：引擎场地坐标约定 +X→屏幕右（layout_game_right：zone_p0_m0 最左、DECK 右列），
     * 而 +Y 侧相机天然把 +X 映射到屏幕左，故绘制时翻转所有几何的 X（拾取仍用真实坐标），
     * 复现桌面版方位。
     */
    private static float mirrorX(float x) {
        return FIELD_X_MIN + FIELD_X_MAX - x;
    }

    // === 相机参数（俯仰角可由设置调整，默认俯视 52°：比 60° 更平，配合顶部内缩避免对方手卡顶到 gameTopInfo）===
    public static final float DEFAULT_CAMERA_ELEVATION = 52f;
    public static final float MIN_CAMERA_ELEVATION = 35f;
    public static final float MAX_CAMERA_ELEVATION = 75f;
    private static final String PREF_CAMERA_ELEVATION = "camera_elevation";
    // 视点到注视点距离：与俯仰角共同决定相机位置（越高越俯视）
    private static final float CAMERA_DISTANCE = 7.6f;
    private static final float CAM_X = 3.95f;
    private static final float CAM_LOOK_Y = 0.3f;

    // === 取景内容真值（全部由 GameField 几何推导，随俯仰角/屏幕宽高动态解算，不再硬编码锚点）===
    /** 手卡 billboard 所在平面高度（gframe getCardLocation：LOCATION_HAND z=0.5） */
    private static final float HAND_Z = 0.5f;
    /** 我方 / 对方手卡行的场地 y（gframe：4.0 / -3.4） */
    private static final float SELF_HAND_Y = 4.0f;
    private static final float OPP_HAND_Y = -3.4f;
    /** 我方近端魔陷区外缘 y：szoneCY(0,&lt;5)=2.6 + ZONE_H/2=0.6，手卡屏幕上缘不得越过此线 */
    private static final float SZONE_NEAR_Y = 3.2f;
    /** 手卡屏幕上缘与魔陷区外缘之间保留的间隙（沿视线投影到地面后度量） */
    private static final float HAND_CLEAR_GAP = 0.06f;
    /** 纵向取景锚点相对手卡极值的安全裕量倍率 */
    private static final float ANCHOR_FOV_MARGIN = 1.04f;
    /** 可交互内容外侧安全边距（世界单位） */
    private static final float CONTENT_PAD = 0.12f;
    /** 视点距离夹取：过近透视畸变过大，过远画面变平且深度精度下降 */
    private static final float MIN_CAM_D = 5.0f;
    private static final float MAX_CAM_D = 14.0f;
    /** 近/远裁剪面：由 0.5/100 收紧到 1/60，同等深度位宽下精度提升约 20 倍 */
    private static final float CAM_NEAR = 1.0f;
    private static final float CAM_FAR = 60.0f;

    // === 堆叠区厚度（问题4）：可见层在 PILE_BASE_Z 之上按张数均匀抬升，形成侧视厚度 ===
    private static final float PILE_BASE_Z = 0.02f;
    private static final int PILE_MAX_LAYERS = 14;
    private static final float PILE_LAYER_THICK = 0.012f;

    // === 总攻击力 bar（问题5）：raw 场地坐标（x 经 fx 映射、y 直用）===
    // rule>3（MR4）：贴各自场地区(szone seq5)靠场地中心一侧的上沿之上，留 0.1 间隙，
    //   既不压场地区格子(0.05~1.15 × 1.4~2.6 / 6.75~7.85 × -2.6~-1.4)，
    //   也不压最左怪兽格(x≥1.2 / x≤6.7)；方向对齐 drawing.cpp vTotalAtkop 的中心朝向。
    // rule<=3（MR3）：无额外怪兽区，落在额外怪兽区位置（materials.cpp vTotalAtkmeT/opT）。
    private static final float[] TOTAL_ATK_ME_MR4 = {0.15f, 0.6f, 1.05f, 1.3f};
    private static final float[] TOTAL_ATK_OP_MR4 = {6.85f, -1.3f, 7.75f, -0.6f};
    private static final float[] TOTAL_ATK_ME_MR3 = {2.5f, 0.95f, 3.5f, 1.65f};
    private static final float[] TOTAL_ATK_OP_MR3 = {4.45f, 0.4f, 5.45f, 1.1f};
    private static final long TOTAL_ATK_KEY = -3L;

    /**
     * 需要完整入镜的可交互内容（怪兽区 / 魔陷区 / 堆叠区）→ {横向半宽(相对场地中轴), 所在 y 行}。
     * 底板贴图的空白边距不计入：横向取景只保证“能点到的东西”不被裁掉，
     * 从而在同样的屏幕宽高比下让卡片尽可能大（底板近端两角允许略微出画）。
     */
    private static final float[][] CONTENT_RECTS = buildContentRects();

    private static float[][] buildContentRects() {
        float[][] tmp = new float[64][];
        int[] piles = {0x01, 0x10, 0x20, 0x40};
        int n = 0;
        for (int p = 0; p < 2; p++) {
            for (int i = 0; i < GameField.MAX_MONSTER_ZONE && n < tmp.length; i++)
                n = addContentRect(tmp, n, GameField.getZoneRect(p, 0x04, i));
            for (int i = 0; i < GameField.MAX_SPELL_ZONE && n < tmp.length; i++)
                n = addContentRect(tmp, n, GameField.getZoneRect(p, 0x08, i));
            for (int loc : piles) {
                if (n >= tmp.length) break;
                n = addContentRect(tmp, n, GameField.getPileRect(p, loc));
            }
        }
        float[][] out = new float[n][];
        for (int i = 0; i < n; i++) out[i] = tmp[i];
        return out;
    }

    private static int addContentRect(float[][] dst, int idx, float[] rect) {
        if (rect == null) return idx;
        float half = rect[2] * 0.5f;
        float hw = Math.max(Math.abs(rect[0] + half - CAM_X), Math.abs(rect[0] - half - CAM_X)) + CONTENT_PAD;
        dst[idx] = new float[]{hw, rect[1]};
        return idx + 1;
    }

    // 选中手卡抬高量
    private static final float HAND_LIFT = 0.3f;
    // 对方手卡确认：翻面展示时长与洗切动画时长
    private static final long OPP_REVEAL_MS = 2500L;
    private static final long SHUFFLE_MS = 700L;
    // 场地视觉倍率：1.0=全部可交互格子/堆叠区恰好完整入镜；&lt;1 裁掉边缘换取更大的卡片；&gt;1 留更多边距
    private static final float FIELD_ZOOM = 1.0f;
    // === XML 可调参数（declare-styleable GameFieldView，常量兜底默认值）===
    // 视点到注视点距离：与俯仰角共同决定相机位置（越高越俯视）
    private float cameraDistance = CAMERA_DISTANCE;
    // 场地视觉倍率（运行期实际取值，可被 XML 覆盖）
    private float fieldZoom = FIELD_ZOOM;
    // 我方手卡手动微调偏置（正值=再向场地外侧后移）；基准后移量由 solveCamera 按俯仰角动态解算
    private static final float HAND_SELF_Y_SHIFT = 0f;
    private float handSelfYShift = HAND_SELF_Y_SHIFT;
    // XML 是否显式声明了俯仰角（声明时优先于游戏内保存的设置）
    private boolean elevationFromXml = false;
    // 设计时预览开关（仅 isInEditMode 生效）
    private boolean previewShowZones = true;
    private boolean previewShowHand = true;
    private boolean previewShowLabels = true;
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
                    "uniform vec4 uUVRect;\n" +
                    "out vec2 vUV;\n" +
                    "void main(){ vec2 uv=vec2(mix(aUV.x,1.0-aUV.x,uFlipU),mix(aUV.y,1.0-aUV.y,uFlipV)); vUV=uUVRect.xy+uv*uUVRect.zw; gl_Position=uMVP*vec4(aPos,0.0,1.0); }\n";

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
    // 模态对话框（是/否、卡片选择/确认、命令菜单）显示期间禁用三个阶段按钮
    private volatile boolean phaseButtonsEnabled = true;

    // === GL 资源 ===
    private int texProg, colorProg;
    private int texLocMVP, texLocTint, texLocTex, texLocFlipU, texLocFlipV, texLocUVRect;
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

    // 屏幕空间数字文字纹理键（区域计数 / 总攻击力数字共用，键含颜色；负值递减独立键域）
    private final HashMap<String, Long> numLabelKeys = new HashMap<>();
    private long numLabelKeySeq = -100000L;
    private final HashMap<String, Long> statLabelKeys = new HashMap<>();
    private long statLabelKeySeq = -50000000L;

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
    // 视点位置：drawCard 判定卡片正/背面朝向用（仅 GL 线程读写）
    private float camEyeX = CAM_X, camEyeY = 0f, camEyeZ = 1f;
    // 我方手卡行动态后移量：solveCamera 写入，绘制与触摸命中共用（主线程也会读）
    private volatile float selfHandShift = 0f;
    // 顶部内缩像素（问题1）：由控制器传入 gameTopInfo 实测高度，离轴视锥据此把对方手卡压到其下
    private volatile float topInsetPx = 0f;
    private final Object camLock = new Object();
    private final float[] pickInvVP = new float[16];
    private volatile int viewW = 1, viewH = 1;
    // 相机重建通知（GL 线程重建相机后，覆盖层需重新锚定阶段按钮位置）
    private volatile Runnable onCameraChangedListener;

    private long lastFrameNs = 0;
    private long animTimeMs = 0;

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
        if (!isInEditMode()) init();
    }

    public GameFieldView(Context context, AttributeSet attrs) {
        super(context, attrs);
        readXmlAttributes(context, attrs);
        if (!isInEditMode()) init();
    }

    /**
     * 解析 XML 布局参数（declare-styleable GameFieldView）：
     * XML 显式声明的值同时作为设计时预览与运行期的正式值，参数调校所见即所得
     */
    private void readXmlAttributes(Context context, AttributeSet attrs) {
        if (attrs == null) return;
        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.GameFieldView);
        try {
            cameraDistance = ta.getFloat(R.styleable.GameFieldView_field_camera_distance, CAMERA_DISTANCE);
            fieldZoom = ta.getFloat(R.styleable.GameFieldView_field_zoom, FIELD_ZOOM);
            handSelfYShift = ta.getFloat(R.styleable.GameFieldView_field_hand_shift, HAND_SELF_Y_SHIFT);
            previewShowZones = ta.getBoolean(R.styleable.GameFieldView_field_preview_zones, true);
            previewShowHand = ta.getBoolean(R.styleable.GameFieldView_field_preview_hand, true);
            previewShowLabels = ta.getBoolean(R.styleable.GameFieldView_field_preview_labels, true);
            if (ta.hasValue(R.styleable.GameFieldView_field_camera_elevation)) {
                cameraElevationDeg = Math.max(MIN_CAMERA_ELEVATION, Math.min(MAX_CAMERA_ELEVATION,
                        ta.getFloat(R.styleable.GameFieldView_field_camera_elevation, DEFAULT_CAMERA_ELEVATION)));
                elevationFromXml = true;
            }
        } finally {
            ta.recycle();
        }
    }

    private void init() {
        setEGLContextClientVersion(3);
        // 24bit 深度：16bit 在远视点下连 0.001~0.003 的层间距都分辨不出（手卡叠放/堆叠层/正反面）
        setEGLConfigChooser(new DepthConfigChooser());
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
            // XML 显式声明优先；否则取用户保存的设置
            if (!elevationFromXml) {
                cameraElevationDeg = AppsSettings.get().getIntSettings(PREF_CAMERA_ELEVATION, Math.round(DEFAULT_CAMERA_ELEVATION));
                cameraElevationDeg = Math.max(MIN_CAMERA_ELEVATION, Math.min(MAX_CAMERA_ELEVATION, cameraElevationDeg));
            }
        } catch (Throwable ignored) {
        }
    }

    // ==================== 公开 API（与原 Canvas 版兼容）====================

    public void setField(GameField field) {
        this.field = field;
        requestRender();
    }

    public void setImageLoader(ImageLoader imageLoader) {
        this.imageLoader = imageLoader;
    }

    /**
     * 卡片/区域点击与长按监听注册口：监听已迁移至 GameFieldViewController，
     * 仅由控制器注册本实现，视图拾取后的回调统一打给控制器
     */
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
     * 场内阶段按钮点击监听（下一阶段/结束阶段；当前阶段按钮仅指示不可点击）；
     * 仅由 GameFieldViewController 注册本实现
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

    /** 三个阶段按钮可用性（模态对话框显示期间禁用：不可点击且变暗） */
    public void setPhaseButtonsEnabled(boolean enabled) {
        if (phaseButtonsEnabled == enabled) return;
        phaseButtonsEnabled = enabled;
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

    /**
     * 顶部内缩像素（问题1）：传入 gameTopInfo（layout_top_info）的实测高度，
     * 相机以离轴视锥把对方手卡上缘压到该高度之下，确保对方手卡不遮挡顶部信息条。
     * 随屏幕旋转 / 折叠屏 / 顶部条高度变化调用即可自适应重建相机。
     */
    public void setTopInsetPx(float px) {
        float v = Math.max(0f, px);
        if (Math.abs(v - topInsetPx) < 0.5f) return;
        topInsetPx = v;
        cameraDirty = true;
        requestRender();
        Runnable l = onCameraChangedListener;
        if (l != null) post(l);
    }

    /**
     * 对方手卡屏幕上缘的屏幕 y（像素，相对本 View 左上角）：供覆盖层把聊天信息与中央提示文本
     * 锚定在「对方手卡正上方」。相机未就绪时回退为当前顶部内缩量。
     */
    public float getOpponentHandTopScreenY() {
        float th = (float) Math.toRadians(cameraElevationDeg);
        float hY = CARD_H * 0.5f * (float) Math.sin(th);
        float hZ = CARD_H * 0.5f * (float) Math.cos(th);
        float[] s = projectWorldPoint(CAM_X, OPP_HAND_Y - hY, HAND_Z + hZ);
        return s != null ? s[1] : topInsetPx;
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
        if (!isInEditMode()) requestRender();
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
        texLocUVRect = GLES30.glGetUniformLocation(texProg, "uUVRect");
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

    /**
     * EGL 配置：优先 RGBA8888 + 24bit 深度，个别设备无该配置时按 24→16→0 逐级回落，避免直接黑屏
     */
    private static final class DepthConfigChooser implements GLSurfaceView.EGLConfigChooser {
        private static final int[] DEPTH_CANDIDATES = {24, 16, 0};

        @Override
        public EGLConfig chooseConfig(EGL10 egl, EGLDisplay display) {
            int[] num = new int[1];
            int[] got = new int[1];
            for (int depth : DEPTH_CANDIDATES) {
                int[] attrs = {
                        EGL10.EGL_RED_SIZE, 8,
                        EGL10.EGL_GREEN_SIZE, 8,
                        EGL10.EGL_BLUE_SIZE, 8,
                        EGL10.EGL_ALPHA_SIZE, 8,
                        EGL10.EGL_DEPTH_SIZE, depth,
                        EGL10.EGL_STENCIL_SIZE, 0,
                        EGL10.EGL_NONE
                };
                if (!egl.eglChooseConfig(display, attrs, null, 0, num) || num[0] <= 0) continue;
                EGLConfig[] configs = new EGLConfig[num[0]];
                if (!egl.eglChooseConfig(display, attrs, configs, configs.length, num)) continue;
                for (EGLConfig config : configs) {
                    if (config == null) continue;
                    if (egl.eglGetConfigAttrib(display, config, EGL10.EGL_DEPTH_SIZE, got) && got[0] >= depth) {
                        return config;
                    }
                }
            }
            throw new IllegalArgumentException("No RGBA8888 EGL config available");
        }
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int w, int h) {
        viewW = w;
        viewH = h;
        GLES30.glViewport(0, 0, w, h);
        updateCamera();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        // 折叠屏展开/折叠、分屏拖拽：View 尺寸变化后兜底重建相机（onSurfaceChanged 未覆盖时的保险）
        cameraDirty = true;
    }

    /**
     * 相机解算结果：运行期 GL 与设计时 Canvas 预览共用同一份解算，保证布局编辑器所见即所得
     */
    private static final class CameraSolve {
        boolean valid;
        float eyeY, eyeZ;       // 视点（eyeX 恒为 CAM_X）
        float dirY, dirZ;       // 视线前向（YZ 平面内单位向量）
        float tanV;             // tan(fovy/2)（对称基准）
        float selfHandShift;    // 我方手卡行后移量（含 XML 手动微调）
        float frustumHH;        // 离轴视锥竖向半高 tan（含顶部内缩后放大）
        float frustumC;         // 离轴视锥竖向中心偏移 tan（>0 = 视锥上移，内容整体下压）
    }

    /**
     * 由「俯仰角 + GameFieldView 实际宽高」完整解算相机，全部随屏幕尺寸动态变化：
     * <ol>
     * <li>我方手卡行 y 按俯仰角解析求解，使其屏幕上缘沿视线落到地面时恰好停在近端魔陷区外缘之外，
     * 任何俯仰角下都不会遮挡魔法陷阱区（固定后移量做不到这点）；</li>
     * <li>纵向取景锚点 = 对方手卡屏幕上缘 / 我方手卡屏幕下缘（billboard 极值，随俯仰角与手卡行变化），
     * FOV 取两锚点夹角 × 裕量；</li>
     * <li>横向按「可交互内容」逐行求 max(半宽/该行深度)，已满足即停 → 保留尽量近的视点，
     * 卡片尽可能大；宽高比过小（折叠屏展开/竖屏）后退也无法容纳时放大 FOV 兜底，任何宽高比都不丢格子。</li>
     * </ol>
     */
    private CameraSolve solveCamera(int w, int h) {
        CameraSolve s = new CameraSolve();
        if (w <= 1 || h <= 1) return s;
        float aspect = (float) w / h;
        float th = (float) Math.toRadians(cameraElevationDeg);
        float cth = (float) Math.cos(th), sth = (float) Math.sin(th);
        if (sth < 1e-3f || cth < 1e-3f) return s;
        // 手卡平行屏幕，相机 up≈(0,-sinθ,cosθ)：卡片半高在世界 Y/Z 上的投影
        float hY = CARD_H * 0.5f * sth;
        float hZ = CARD_H * 0.5f * cth;
        float topZ = HAND_Z + hZ;      // 手卡屏幕上缘的世界 z
        float bottomZ = HAND_Z - hZ;   // 手卡屏幕下缘的世界 z

        float D = Math.max(MIN_CAM_D, Math.min(MAX_CAM_D, cameraDistance));
        float eyeY = CAM_LOOK_Y + cth * D, eyeZ = sth * D;
        float dY = 0f, dZ = -1f, tanV = 0.5f, selfCy = SELF_HAND_Y;
        for (int i = 0; i < 8; i++) {
            eyeY = CAM_LOOK_Y + cth * D;
            eyeZ = sth * D;
            selfCy = solveSelfHandY(eyeY, eyeZ, hY, topZ);
            float v1y = (OPP_HAND_Y - hY) - eyeY, v1z = topZ - eyeZ;
            float v2y = (selfCy + hY) - eyeY, v2z = bottomZ - eyeZ;
            float l1 = (float) Math.sqrt(v1y * v1y + v1z * v1z);
            float l2 = (float) Math.sqrt(v2y * v2y + v2z * v2z);
            if (l1 < 1e-4f || l2 < 1e-4f) break;
            float d1y = v1y / l1, d1z = v1z / l1, d2y = v2y / l2, d2z = v2z / l2;
            float dot = Math.max(-1f, Math.min(1f, d1y * d2y + d1z * d2z));
            tanV = (float) Math.tan(Math.acos(dot) * 0.5 * ANCHOR_FOV_MARGIN);
            float by = d1y + d2y, bz = d1z + d2z;
            float bl = (float) Math.sqrt(by * by + bz * bz);
            if (bl < 1e-4f) break;
            dY = by / bl;
            dZ = bz / bl;
            s.valid = true;
            if (contentHalfTan(eyeY, eyeZ, dY, dZ) * fieldZoom <= tanV * aspect) break;
            if (D >= MAX_CAM_D - 1e-3f) break;
            D = Math.min(MAX_CAM_D, D * 1.35f);
        }
        if (!s.valid) return s;

        float need = contentHalfTan(eyeY, eyeZ, dY, dZ) * fieldZoom;
        if (need / aspect > tanV) tanV = need / aspect;
        if (tanV > 1.6f) tanV = 1.6f;

        // 顶部内缩（问题1）：用离轴视锥把「对方手卡上缘」锚到屏幕顶部内缩线 ndcTop 之下，
        // 我方手卡下缘仍锚到 ndc=-1（屏幕底），从而在不裁掉任何一方的前提下为 gameTopInfo 让出顶部空间。
        // 由对称半角 tanV 解离轴参数：hh = 2·tanV/(1+ndcTop)，c = hh - tanV（推导见类注释）。
        float inset = Math.max(0f, Math.min(topInsetPx, h * 0.45f));
        float ndcTop = (h > 1f) ? (1f - 2f * inset / h) : 1f;
        if (ndcTop < 0.05f) ndcTop = 0.05f;
        float hh = 2f * tanV / (1f + ndcTop);
        s.frustumHH = hh;
        s.frustumC = hh - tanV;

        float shift = SELF_HAND_Y - selfCy + handSelfYShift;
        s.eyeY = eyeY;
        s.eyeZ = eyeZ;
        s.dirY = dY;
        s.dirZ = dZ;
        s.tanV = tanV;
        s.selfHandShift = Math.max(-2.5f, Math.min(2.5f, shift));
        return s;
    }

    /**
     * 解算我方手卡行的场地 y：手卡是 billboard，其屏幕上缘 (cy-hY, HAND_Z+hZ) 沿视线落到地面(z=0)
     * 的交点必须落在近端魔陷区外缘之外 HAND_CLEAR_GAP。视线越斜（俯仰角越小）所需后移量越大，
     * 因此必须随相机动态求解，不能用固定常量。
     */
    private static float solveSelfHandY(float eyeY, float eyeZ, float hY, float topZ) {
        if (eyeZ < 1e-3f) return SELF_HAND_Y;
        float dz = eyeZ - topZ;
        if (dz < 0.2f) return SELF_HAND_Y + 2.0f;
        float cy = eyeY + hY - (eyeY - SZONE_NEAR_Y - HAND_CLEAR_GAP) * dz / eyeZ;
        return Math.max(3.4f, Math.min(6.5f, cy));
    }

    /**
     * 所有可交互内容中 max(半宽 / 该行沿视线深度)，即容纳全部内容所需的 tan(fovx/2)
     */
    private static float contentHalfTan(float eyeY, float eyeZ, float dY, float dZ) {
        float need = 0f;
        for (int i = 0; i < CONTENT_RECTS.length; i++) {
            float[] r = CONTENT_RECTS[i];
            float depth = (r[1] - eyeY) * dY + (0f - eyeZ) * dZ;
            if (depth < 0.2f) continue;
            float t = r[0] / depth;
            if (t > need) need = t;
        }
        return need;
    }

    private void updateCamera() {
        CameraSolve s = solveCamera(viewW, viewH);
        if (!s.valid) return;
        float aspect = (float) viewW / viewH;

        camEyeX = CAM_X;
        camEyeY = s.eyeY;
        camEyeZ = s.eyeZ;
        selfHandShift = s.selfHandShift;

        // 离轴（顶部内缩）透视视锥：left/right 对称、bottom/top 非对称，横向半宽 = 竖向半高 × aspect
        float near = CAM_NEAR;
        float fTop = (s.frustumC + s.frustumHH) * near;
        float fBottom = (s.frustumC - s.frustumHH) * near;
        float fRight = s.frustumHH * aspect * near;
        Matrix.frustumM(mProj, 0, -fRight, fRight, fBottom, fTop, near, CAM_FAR);
        Matrix.setLookAtM(mView, 0, CAM_X, s.eyeY, s.eyeZ,
                CAM_X, s.eyeY + s.dirY, s.eyeZ + s.dirZ, 0f, 0f, 1f);
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
            drawTotalAttackBars(f);
        } catch (Throwable ignored) {
        }
        try {
            drawFieldCards(f);
        } catch (Throwable ignored) {
        }
        GLES30.glDepthMask(false);
        drawHighlights();
        drawCardSelectOutlines(f);
        drawPhaseButtons();
        try {
            drawFieldNumbers(f);
        } catch (Throwable ignored) {
        }
        try {
            drawFieldCardTexts(f);
        } catch (Throwable ignored) {
        }
        GLES30.glDepthMask(true);
    }

    // ==================== 绘制 ====================

    /**
     * 场地底板：对齐 gframe drawing.cpp L326/L369 ——
     * rule=(duel_rule>=4)?1:0 选 field3/field2；显示场地魔法卡时改用 field-transparent 版。
     * 底板矩形取 materials.cpp vField（-1..9 × -4..4）经 fx 映射，与格子/卡片同仿射，网格完美重叠。
     * 其余区域保持透明，透出窗口背景。
     */
    private void drawFieldBoard(GameField f) {
        int rule = (f.dInfo.duelRule >= 4) ? 1 : 0;
        int code1 = fieldSpellCode(f, 0);
        int code2 = fieldSpellCode(f, 1);
        boolean transparent = code1 > 0 || code2 > 0;
        // 场地魔法背景图先于底板绘制（z=-0.01 底板之下），对齐 drawing.cpp DrawBackGround
        if (transparent) drawFieldSpellArt(code1, code2);
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
     * gframe DrawBackGround 语义：取该方场地魔法区(szone seq5)正面场地卡 code，无则 0
     */
    private static int fieldSpellCode(GameField f, int player) {
        try {
            List<GameField.ClientCard> sz = f.players[player].spellZone;
            if (sz.size() > 5) {
                GameField.ClientCard c = sz.get(5);
                if (c != null && c.isFaceUp() && c.code != 0) return c.code;
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }

    /**
     * 场地魔法背景图（image_manager.cpp GetTextureField + materials.cpp vFieldSpell*）：
     * 单方/双方同码 → 整幅 vFieldSpell；双方异码 → 各画半幅（vFieldSpell1/2 的 uv 子矩形）
     */
    private void drawFieldSpellArt(int code1, int code2) {
        float x0 = fx(GameField.FIELD_SPELL_X_MIN);
        float x1 = fx(GameField.FIELD_SPELL_X_MAX);
        float w = x1 - x0;
        float cx = mirrorX((x0 + x1) / 2f);
        if (code1 > 0 && code2 > 0 && code1 != code2) {
            drawFieldSpellRect(obtainFieldSpellTexture(code1),
                    0.8f, 3.2f, 1f, 0.2f, 0.8f, 0.63636f, 0.36364f, cx, w);
            drawFieldSpellRect(obtainFieldSpellTexture(code2),
                    -3.2f, -0.8f, 1f, 1f, -0.36364f, 0.63636f, -0.43636f, cx, w);
        } else {
            drawFieldSpellRect(obtainFieldSpellTexture(code1 > 0 ? code1 : code2),
                    GameField.FIELD_SPELL_Y_MIN, GameField.FIELD_SPELL_Y_MAX,
                    1f, 0f, 1f, 0f, 1f, cx, w);
        }
    }

    private void drawFieldSpellRect(int tex, float yMin, float yMax, float flipU,
                                    float offU, float scU, float offV, float scV,
                                    float cx, float w) {
        if (tex <= 0) return;
        Matrix.setIdentityM(mModel, 0);
        Matrix.translateM(mModel, 0, cx, (yMin + yMax) / 2f, -0.01f);
        Matrix.scaleM(mModel, 0, w, yMax - yMin, 1f);
        drawQuadTexUV(mModel, tex, 1f, flipU, 0f, offU, offV, scU, scV);
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

    // === 问题5：总攻击力 bar（drawing.cpp L399-422 + materials.cpp vTotalAtk*）===
    private static float[] totalAtkRect(int p, boolean mr4) {
        if (p == 0) return mr4 ? TOTAL_ATK_ME_MR4 : TOTAL_ATK_ME_MR3;
        return mr4 ? TOTAL_ATK_OP_MR4 : TOTAL_ATK_OP_MR3;
    }

    /**
     * 总攻击力 bar：duel_rule>=4 用 vTotalAtkme/op，否则 vTotalAtkmeT/opT；
     * raw x 经 fx 映射、y 直用，绘制空间再做 X 镜像，贴 totalAtk.png，仅 total_attack>0 时绘制。
     */
    private void drawTotalAttackBars(GameField f) {
        if (f == null) return;
        boolean mr4 = f.dInfo.duelRule >= 4;
        int tex = obtainTotalAtkTexture();
        for (int p = 0; p < 2; p++) {
            if (f.dInfo.totalAttack[p] <= 0) continue;
            float[] rc = totalAtkRect(p, mr4);
            float x0 = fx(rc[0]), x1 = fx(rc[2]);
            float cxw = (x0 + x1) / 2f;
            float cyw = (rc[1] + rc[3]) / 2f;
            float w = Math.abs(x1 - x0), h = Math.abs(rc[3] - rc[1]);
            Matrix.setIdentityM(mModel, 0);
            Matrix.translateM(mModel, 0, mirrorX(cxw), cyw, 0.006f);
            Matrix.scaleM(mModel, 0, w, h, 1f);
            if (tex > 0) drawQuadTex(mModel, tex, 1f);
            else drawQuadColor(mModel, 0.85f, 0.62f, 0.12f, 0.80f);
        }
    }

    /**
     * 问题3+5：屏幕像素正交空间绘制区域堆叠数量与总攻击力数字，天然垂直于观看视线（与阶段按钮同方案）。
     * 区域数字贴在卡组/额外/墓地/除外格子「靠近摄像头的底边」（+y 侧缘）外侧；总攻击力数字叠在 bar 中心。
     */
    private void drawFieldNumbers(GameField f) {
        int w = viewW, h = viewH;
        if (w <= 1 || h <= 1 || f == null) return;
        Matrix.orthoM(mOrthoVP, 0, 0f, w, h, 0f, -1f, 1f);
        GLES30.glDisable(GLES30.GL_DEPTH_TEST);
        for (int p = 0; p < 2; p++) {
            for (int loc : new int[]{0x01, 0x40, 0x10, 0x20}) {
                int cnt = f.getCardCount(p, loc);
                if (cnt <= 0) continue;
                float[] r = GameField.getPileRect(p, loc);
                if (r == null) continue;
                float nearY = r[1] + r[3] / 2f;              // 靠摄像头的 +y 侧缘
                float farY = r[1] - r[3] / 2f;
                float[] top = projectWorldPoint(mirrorX(r[0]), nearY, 0.02f);
                float[] bot = projectWorldPoint(mirrorX(r[0]), farY, 0.02f);
                if (top == null || bot == null) continue;
                float pilePx = Math.abs(top[1] - bot[1]);
                float hpx = Math.max(10f, Math.min(40f, pilePx * 0.30f));
                float[] anchor = projectWorldPoint(mirrorX(r[0]), nearY + 0.14f, 0.02f);
                drawScreenNumber(anchor, pileCountLabel(f, p, loc, cnt), 0xFFFFFF00, hpx);
            }
        }
        boolean mr4 = f.dInfo.duelRule >= 4;
        for (int p = 0; p < 2; p++) {
            if (f.dInfo.totalAttack[p] <= 0) continue;
            float[] rc = totalAtkRect(p, mr4);
            float cxw = (fx(rc[0]) + fx(rc[2])) / 2f;
            float cyw = (rc[1] + rc[3]) / 2f;
            float[] a = projectWorldPoint(mirrorX(cxw), cyw, 0.012f);
            float[] b = projectWorldPoint(mirrorX(cxw), rc[3], 0.012f);
            float[] c2 = projectWorldPoint(mirrorX(cxw), rc[1], 0.012f);
            float hpx = 14f;
            if (b != null && c2 != null) hpx = Math.max(10f, Math.min(40f, Math.abs(b[1] - c2[1]) * 0.8f));
            drawScreenNumber(a, String.valueOf(f.dInfo.totalAttack[p]), f.dInfo.totalAttackColor[p], hpx);
        }
        GLES30.glEnable(GLES30.GL_DEPTH_TEST);
    }

    /**
     * 堆叠区数量文字：额外卡组与除外区在总数后追加括号区分表侧/里侧。
     * - 额外卡组(0x40)：显示「总数(表侧)」——表侧数=extraPCount[p]（对齐 drawing.cpp L1130-1131
     *   extra.size() 与 (extra_p_count)）；例：表侧10、里侧5、总数15 → "15(10)"。
     * - 除外区(0x20)：显示「总数(里侧)」——里侧数=总数-表侧数，表侧数=removed 中 isFaceUp() 计数；
     *   例：表侧10、里侧5、总数15 → "15(5)"。
     * 括号内数量为 0 时只显示总数（全区同朝向，无需区分）；卡组(0x01)/墓地(0x10) 维持总数。
     */
    private static String pileCountLabel(GameField f, int p, int loc, int total) {
        if (loc == 0x40) {
            int faceUp = clampCount(f.extraPCount[p], total);
            return faceUp > 0 ? total + "(" + faceUp + ")" : String.valueOf(total);
        }
        if (loc == 0x20) {
            int faceUp = clampCount(countFaceUp(f, p, 0x20), total);
            int faceDown = total - faceUp;
            return faceDown > 0 ? total + "(" + faceDown + ")" : String.valueOf(total);
        }
        return String.valueOf(total);
    }

    /** 统计某区列表中表侧（正面朝上）卡片数，对齐 ClientCard.isFaceUp 的 position 位判定 */
    private static int countFaceUp(GameField f, int p, int loc) {
        try {
            List<GameField.ClientCard> list = f.players[p].getLocationList(loc);
            if (list == null) return 0;
            int n = 0;
            for (GameField.ClientCard c : list) {
                if (c != null && c.isFaceUp()) n++;
            }
            return n;
        } catch (Throwable t) {
            return 0;
        }
    }

    private static int clampCount(int v, int max) {
        if (v < 0) return 0;
        return Math.min(v, max);
    }

    // 场上怪兽 攻击力/守备力/link 值 + 灵摆刻度值 文字显示
    // 数值取自 client_card.cpp 计算的 atkString/defString/linkString/lscString/rscString；
    // 落点取自 client_field.cpp GetCardLocation 的格子几何；灵摆刻度显示对齐 drawing.cpp DrawCard。

    /** CardType.Pendulum 位（ocgcore.enums.CardType.Pendulum = 0x1000000） */
    private static final int TYPE_PENDULUM = 0x1000000;
    /** CardType.Xyz = 0x800000（阶级玫红）、CardType.Tuner = 0x1000（等级黄），对齐 drawing.cpp DrawStatus */
    private static final int TYPE_XYZ = 0x800000;
    private static final int TYPE_TUNER = 0x1000;

    /**
     * 场上卡片数值文字（正交 HUD 通道，关深度测试，与 drawFieldNumbers 同一套投影）：
     * - 表侧怪兽：攻击表示 → 左下攻击力/右下守备力；守备表示 → 左下守备力/右下攻击力；
     *   连接怪兽 → 左下攻击力/右下 link 值（连接怪兽 defString 已为 "-"）。
     * - 灵摆刻度：rule>=4 时魔陷区最左(seq0)左上角显示左刻度、最右(seq4)右上角显示右刻度；
     *   rule<4 时用 seq6/seq7（对齐 drawing.cpp DrawCard 的灵摆刻度分支）。
     * 移动中的卡片跳过（对齐 DrawCard is_moving 提前返回），字号随格子投影像素高度自适应。
     */
    private void drawFieldCardTexts(GameField f) {
        int w = viewW, h = viewH;
        if (w <= 1 || h <= 1 || f == null) return;
        Matrix.orthoM(mOrthoVP, 0, 0f, w, h, 0f, -1f, 1f);
        GLES30.glDisable(GLES30.GL_DEPTH_TEST);
        boolean mr4 = f.dInfo.duelRule >= 4;
        for (int p = 0; p < 2; p++) {
            List<GameField.ClientCard> mz = f.players[p].monsterZone;
            for (int seq = 0; seq < mz.size(); seq++) {
                GameField.ClientCard c;
                try { c = mz.get(seq); } catch (Throwable e) { continue; }
                if (c == null || !c.isFaceUp() || c.is_moving) continue;
                drawMonsterStatTexts(p, seq, c);
            }
            drawPendulumScaleText(f, p, mr4 ? 0 : 6, true);
            drawPendulumScaleText(f, p, mr4 ? 4 : 7, false);
        }
        GLES30.glEnable(GLES30.GL_DEPTH_TEST);
    }

    private void drawMonsterStatTexts(int p, int seq, GameField.ClientCard c) {
        float[] r = GameField.getZoneRect(p, 0x04, seq);
        if (r == null) return;
        float cx = r[0], cy = r[1], hw = r[2] / 2f, hh = r[3] / 2f;
        // 四角 + 中心投影（world +y 靠相机 → 屏幕更下）
        float[] nearL = projectWorldPoint(mirrorX(cx - hw), cy + hh, 0.02f);
        float[] nearR = projectWorldPoint(mirrorX(cx + hw), cy + hh, 0.02f);
        float[] farL = projectWorldPoint(mirrorX(cx - hw), cy - hh, 0.02f);
        float[] farR = projectWorldPoint(mirrorX(cx + hw), cy - hh, 0.02f);
        float[] center = projectWorldPoint(mirrorX(cx), cy, 0.02f);
        if (nearL == null || nearR == null || farL == null || farR == null || center == null) return;
        // 字号对齐堆叠数量（0.30 系数）后整体再缩小一号（攻守/等级数字比原来小 1 号）
        float cardHpx = Math.abs(center[1] - nearL[1]) * 2f;
        float hpx = Math.max(10f, Math.min(40f, cardHpx * 0.30f)) * STAT_SIZE_SCALE;
        boolean ours = (p == 0);

        // ATK/DEF（连接怪兽为 ATK/L‑n）紧贴「视角相对下沿」外侧显示——
        // 我方数字上沿贴卡片下边缘（屏幕下缘外侧），对方数字底边贴卡片下边缘（屏幕上缘外侧）。
        // 本轮再朝卡片方向贴近 STAT_SNUG_PX 像素（我方攻守上抬、对方攻守下降）。
        float[] eL = ours ? nearL : farL;
        float[] eR = ours ? nearR : farR;
        float edgeX = (eL[0] + eR[0]) / 2f;
        float edgeY = (eL[1] + eR[1]) / 2f;
        String[] parts;
        int[] colors;
        if (c.isLink()) {
            parts = new String[]{nz(c.atkString), "/", nz(c.linkString)};
            colors = new int[]{statValueColor(c.attack, c.baseAttack), 0xFFFFFFFF, 0xFF99FFFF};
        } else {
            parts = new String[]{nz(c.atkString), "/", nz(c.defString)};
            colors = new int[]{statValueColor(c.attack, c.baseAttack), 0xFFFFFFFF,
                    statValueColor(c.defense, c.baseDefense)};
        }
        // 数字中心移到卡片外侧半个字高处（远离卡片中心方向）：贴边而不压卡面
        float statY = edgeY - Math.signum(center[1] - edgeY) * (hpx / 2f);
        statY += Math.signum(center[1] - statY) * STAT_SNUG_PX;   // 再贴近卡片 2px
        drawScreenText(edgeX, statY, parts, colors, hpx);

        // 等级(L*,白/调律黄)或阶级(R*,玫红)紧贴「视角相对上沿」外侧的角显示。
        // 对齐 drawing.cpp DrawStatus：我方在卡片左上角（屏幕上缘左角、左对齐）；
        // 对方卡旋转 180°，其等级在卡主视角右上角 = 屏幕下缘左角、左对齐
        //（原实现取屏幕下缘右角=卡主视角左上角，故本轮改为左角）。
        if (c.lvString != null && !c.lvString.isEmpty()) {
            float[] tl = ours
                    ? (farL[0] <= farR[0] ? farL : farR)      // 我方：屏幕上缘左角
                    : (nearL[0] <= nearR[0] ? nearL : nearR); // 对方：屏幕下缘左角（=卡主视角右上角）
            int lvColor = (c.type & TYPE_XYZ) != 0 ? 0xFFFF80FF
                    : (c.type & TYPE_TUNER) != 0 ? 0xFFFFFF00 : 0xFFFFFFFF;
            // 竖直方向移到卡片外侧半个字高处，再朝卡片方向贴近 STAT_SNUG_PX（我方下移、对方上移）
            float lvY = tl[1] - Math.signum(center[1] - tl[1]) * (hpx / 2f);
            lvY += Math.signum(center[1] - lvY) * STAT_SNUG_PX;
            // 水平方向朝卡片中心贴近 STAT_SNUG_PX（我方等级右移 2px）
            float lvX = tl[0] + Math.signum(center[0] - tl[0]) * STAT_SNUG_PX;
            drawScreenTextAligned(lvX, lvY, new String[]{c.lvString}, new int[]{lvColor}, hpx, ALIGN_LEFT);
        }
    }

    private void drawPendulumScaleText(GameField f, int p, int seq, boolean leftScale) {
        GameField.ClientCard c;
        try { c = f.players[p].spellZone.get(seq); } catch (Throwable e) { return; }
        if (c == null || !c.isFaceUp() || c.is_moving) return;
        if ((c.type & TYPE_PENDULUM) == 0) return;
        if (c.equipTarget != null) return;                    // 对齐 drawing.cpp !equipTarget
        String txt = leftScale ? c.lscString : c.rscString;   // 左区左刻度 / 右区右刻度
        if (txt == null || txt.isEmpty()) return;
        float[] r = GameField.getZoneRect(p, 0x08, seq);
        if (r == null) return;
        float cx = r[0], cy = r[1], hw = r[2] / 2f, hh = r[3] / 2f;
        float[] nearL = projectWorldPoint(mirrorX(cx - hw), cy + hh, 0.02f);
        float[] nearR = projectWorldPoint(mirrorX(cx + hw), cy + hh, 0.02f);
        float[] farL = projectWorldPoint(mirrorX(cx - hw), cy - hh, 0.02f);
        float[] farR = projectWorldPoint(mirrorX(cx + hw), cy - hh, 0.02f);
        float[] center = projectWorldPoint(mirrorX(cx), cy, 0.02f);
        if (nearL == null || nearR == null || farL == null || farR == null || center == null) return;
        // 字号对齐堆叠数量
        float cardHpx = Math.abs(center[1] - nearL[1]) * 2f;
        float hpx = Math.max(10f, Math.min(40f, cardHpx * 0.30f));
        boolean ours = (p == 0);
        // 左刻度→视角左上角、右刻度→视角右上角（对方旋转 180° 后左右/上下互换）
        float[] corner;
        if (ours) {
            float[] l = farL[0] <= farR[0] ? farL : farR;
            float[] rr = farL[0] <= farR[0] ? farR : farL;
            corner = leftScale ? l : rr;
        } else {
            float[] l = nearL[0] >= nearR[0] ? nearL : nearR;   // 对方视角左上 = 屏幕右
            float[] rr = nearL[0] >= nearR[0] ? nearR : nearL;  // 对方视角右上 = 屏幕左
            corner = leftScale ? l : rr;
        }
        float tx = corner[0] + Math.signum(center[0] - corner[0]) * hpx * 0.9f;
        float ty = corner[1] + Math.signum(center[1] - corner[1]) * hpx * 0.8f;
        // 刻度白色（对齐 drawing.cpp 灵摆刻度 0xffffffff）
        drawScreenText(tx, ty, new String[]{txt}, new int[]{0xFFFFFFFF}, hpx);
    }
    private void drawScreenNumber(float[] screenXY, String text, int color, float heightPx) {
        if (screenXY == null || text == null || text.isEmpty()) return;
        int tex = obtainNumberTexture(text, color);
        if (tex <= 0) return;
        drawScreenQuadTex(screenXY[0], screenXY[1], heightPx * 2f, heightPx, tex, 1f);
    }

    private int obtainTotalAtkTexture() {
        Integer id = textures.get(TOTAL_ATK_KEY);
        if (id != null) return id;
        if (!requested.add(TOTAL_ATK_KEY)) return -1;
        try {
            texExecutor().execute(() -> {
                Bitmap b = null;
                try {
                    Bitmap src = TextureLoader.get().getTotalAtkTexture();
                    if (src != null && !src.isRecycled()) b = src.copy(Bitmap.Config.ARGB_8888, false);
                } catch (Throwable ignored) {
                }
                if (b != null) pendingUploads.offer(new PendingUpload(TOTAL_ATK_KEY, b, true));
                else requested.remove(TOTAL_ATK_KEY);
            });
        } catch (Throwable t) {
            requested.remove(TOTAL_ATK_KEY);
        }
        return -1;
    }

    private int obtainNumberTexture(String text, int color) {
        String k = text + "|" + color;
        Long key = numLabelKeys.get(k);
        if (key == null) {
            key = numLabelKeySeq--;
            numLabelKeys.put(k, key);
            try {
                pendingUploads.offer(new PendingUpload(key, makeNumberBitmap(text, color), true));
            } catch (Throwable ignored) {
            }
        }
        Integer id = textures.get(key);
        return id != null ? id : -1;
    }

    private static Bitmap makeNumberBitmap(String text, int color) {
        int w = 128, h = 64;
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTextSize(44f);
        p.setFakeBoldText(true);
        float tw = p.measureText(text);
        if (tw > w * 0.92f) p.setTextSize(44f * (w * 0.92f) / tw);
        p.setColor(color | 0xFF000000);
        p.setTextAlign(Paint.Align.CENTER);
        p.setShadowLayer(3f, 1f, 1f, 0xC0000000);
        cv.drawText(text, w / 2f, h / 2f - (p.ascent() + p.descent()) / 2f, p);
        return bmp;
    }

    // === 多色自适应宽度 HUD 文字（ATK/DEF、等级/阶级、灵摆刻度）===
    // 与 makeNumberBitmap 的区别：位图宽度随文本增长而非缩放字号，
    // 保证「1500/2000」这类长串的字高与堆叠数量数字完全一致。
    private static final float STAT_TEXT_SIZE = 44f;
    private static final int STAT_BMP_H = 64;
    private static final int STAT_PAD_X = 6;
    private static final Paint STAT_MEASURE_PAINT = makeStatMeasurePaint();

    private static Paint makeStatMeasurePaint() {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTextSize(STAT_TEXT_SIZE);
        p.setFakeBoldText(true);
        return p;
    }

    /** 多段文字合成位图的宽高比（宽随文本增长、字高恒定），用于绘制时保持不拉伸 */
    private static float statTextAspect(String[] parts) {
        float tw = 0f;
        for (String s : parts) tw += STAT_MEASURE_PAINT.measureText(s);
        int w = (int) Math.ceil(tw) + STAT_PAD_X * 2;
        if (w < STAT_BMP_H) w = STAT_BMP_H;
        return (float) w / (float) STAT_BMP_H;
    }

    private int obtainStatTexture(String[] parts, int[] colors) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) sb.append(parts[i]).append('#').append(colors[i]).append('|');
        String k = sb.toString();
        Long key = statLabelKeys.get(k);
        if (key == null) {
            key = statLabelKeySeq--;
            statLabelKeys.put(k, key);
            try {
                pendingUploads.offer(new PendingUpload(key, makeColoredTextBitmap(parts, colors), true));
            } catch (Throwable ignored) {
            }
        }
        Integer id = textures.get(key);
        return id != null ? id : -1;
    }

    private static Bitmap makeColoredTextBitmap(String[] parts, int[] colors) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTextSize(STAT_TEXT_SIZE);
        p.setFakeBoldText(true);
        float tw = 0f;
        for (String s : parts) tw += p.measureText(s);
        int w = (int) Math.ceil(tw) + STAT_PAD_X * 2;
        if (w < STAT_BMP_H) w = STAT_BMP_H;
        int h = STAT_BMP_H;
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        p.setTextAlign(Paint.Align.LEFT);
        p.setShadowLayer(3f, 1f, 1f, 0xFF000000);
        float baseline = h / 2f - (p.ascent() + p.descent()) / 2f;
        float x = (w - tw) / 2f;
        for (int i = 0; i < parts.length; i++) {
            p.setColor(colors[i] | 0xFF000000);
            cv.drawText(parts[i], x, baseline, p);
            x += p.measureText(parts[i]);
        }
        return bmp;
    }

    /** 在屏幕点绘制多色分段文字：字高 heightPx，宽 = heightPx × 文本宽高比，整体居中于 (cx,cy) */
    private void drawScreenText(float cx, float cy, String[] parts, int[] colors, float heightPx) {
        if (parts == null || parts.length == 0) return;
        int tex = obtainStatTexture(parts, colors);
        if (tex <= 0) return;
        float w = heightPx * statTextAspect(parts);
        drawScreenQuadTex(cx, cy, w, heightPx, tex, 1f);
    }

    // 文字水平对齐方式（drawScreenTextAligned）
    private static final int ALIGN_CENTER = 0;
    private static final int ALIGN_LEFT = 1;
    private static final int ALIGN_RIGHT = 2;
    /** 攻守/等级数字尺寸 */
    private static final float STAT_SIZE_SCALE = 0.85f;
    private static final float STAT_SNUG_PX = 2f;

    /**
     * 水平对齐绘制多色文字：ALIGN_LEFT 时 anchorX 为文字左边缘、ALIGN_RIGHT 时为右边缘、
     * ALIGN_CENTER 时为文字中心；竖直方向始终以 cy 为文字中心。
     */
    private void drawScreenTextAligned(float anchorX, float cy, String[] parts, int[] colors,
                                       float heightPx, int align) {
        if (parts == null || parts.length == 0) return;
        int tex = obtainStatTexture(parts, colors);
        if (tex <= 0) return;
        float w = heightPx * statTextAspect(parts);
        float cx = anchorX;
        if (align == ALIGN_LEFT) cx = anchorX + w / 2f;
        else if (align == ALIGN_RIGHT) cx = anchorX - w / 2f;
        drawScreenQuadTex(cx, cy, w, heightPx, tex, 1f);
    }

    /** 攻/守数值颜色（对齐 drawing.cpp DrawStatus：高于原值黄、低于原值粉、等于白） */
    private static int statValueColor(int cur, int base) {
        if (cur > base) return 0xFFFFFF00;
        if (cur < base) return 0xFFFF2090;
        return 0xFFFFFFFF;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    // === 可选格子高亮：环绕格子的虚线行进动画（drawing.cpp DrawSelectionLine + game.cpp linePattern/stippleMask）===
    private static final int STIPPLE_MASK = 0x0f0f;
    private static final float DASH_PX = 4f;
    private static final float OUTLINE_PX = 2.5f;
    private static final float MARCH_PX_PER_SEC = 48f;

    private void drawHighlights() {
        int mask = highlightFieldMask;
        if (mask == 0) return;
        float phase = animTimeMs * 0.001f * MARCH_PX_PER_SEC;
        for (int p = 0; p < 2; p++) {
            float r = p == 0 ? 0f : 1f;
            float g = p == 0 ? 1f : 0f;
            float b = p == 0 ? 1f : 0f;
            for (int i = 0; i < 7; i++) {
                if ((mask & (1 << zoneBitPos(p, 0x04, i))) != 0)
                    drawZoneMarching(p, 0x04, i, phase, r, g, b);
            }
            for (int i = 0; i < 8; i++) {
                int bit = zoneBitPos(p, 0x08, i);
                if (bit >= 0 && (mask & (1 << bit)) != 0)
                    drawZoneMarching(p, 0x08, i, phase, r, g, b);
            }
        }
    }

    /**
     * 单格虚线行进框：四角投影到屏幕取像素边长，按 16bit stipple(0x0f0f) 沿周长走像素，
     * “亮”段换算回世界坐标画粗线段；patternCursor 跨边累积、phase 随时间推进 → 蚂蚁线环绕运动
     */
    private void drawZoneMarching(int player, int loc, int seq, float phase,
                                  float r, float g, float b) {
        float[] rect = GameField.getZoneRect(player, loc, seq);
        if (rect == null) return;
        float cx = mirrorX(rect[0]), cy = rect[1];
        float hw = rect[2] / 2f, hh = rect[3] / 2f;
        float x0 = cx - hw, x1 = cx + hw, y0 = cy - hh, y1 = cy + hh;
        // 角点顺序对齐 C++ v[0..3]，边序对齐 edgeStart/edgeEnd
        float[] qx = {x0, x1, x0, x1};
        float[] qy = {y0, y0, y1, y1};
        int[] es = {0, 1, 3, 2};
        int[] ee = {1, 3, 2, 0};
        float[] sx = new float[4], sy = new float[4];
        for (int i = 0; i < 4; i++) {
            float[] s = projectWorldPoint(qx[i], qy[i], 0.03f);
            if (s == null) return;
            sx[i] = s[0];
            sy[i] = s[1];
        }
        float patternCursor = 0f;
        for (int i = 0; i < 4; i++) {
            int a = es[i], d = ee[i];
            float worldLen = (i == 0 || i == 2) ? rect[2] : rect[3];
            float screenLen = (float) Math.hypot(sx[d] - sx[a], sy[d] - sy[a]);
            if (screenLen < 1f || worldLen < 1e-4f) continue;
            float thick = OUTLINE_PX * worldLen / screenLen;
            float c = 0f;
            while (c < screenLen) {
                boolean on = ((STIPPLE_MASK >> ((int) (phase + patternCursor + c) & 0xf)) & 1) != 0;
                float runEnd = c + 1f;
                while (runEnd < screenLen
                        && ((((STIPPLE_MASK >> ((int) (phase + patternCursor + runEnd) & 0xf)) & 1) != 0) == on)) {
                    runEnd += 1f;
                }
                if (runEnd > screenLen) runEnd = screenLen;
                if (on) {
                    float t0 = c / screenLen, t1 = runEnd / screenLen;
                    drawDash(qx[a] + (qx[d] - qx[a]) * t0, qy[a] + (qy[d] - qy[a]) * t0,
                            qx[a] + (qx[d] - qx[a]) * t1, qy[a] + (qy[d] - qy[a]) * t1,
                            thick, r, g, b);
                }
                c = runEnd;
            }
            patternCursor = (patternCursor + screenLen) % 16f;
        }
    }

    /** 单段虚线：边在世界空间轴对齐，水平边给厚度作高、垂直边给厚度作宽 */
    private void drawDash(float x0, float y0, float x1, float y1, float thick,
                          float r, float g, float b) {
        float w = Math.abs(x1 - x0);
        float h = Math.abs(y1 - y0);
        if (h < 1e-4f) {
            h = thick;
        } else {
            w = thick;
        }
        drawFlatQuad((x0 + x1) / 2f, (y0 + y1) / 2f, 0.03f, w, h, r, g, b, 0.95f);
    }

    // === 卡片选择轮廓：黄色蚂蚁线（对齐 drawing.cpp DrawSelectionLine + DrawCard L638-643）===
    private final float[] mOutlineModel = new float[16];
    private final float[] mOutlineTmp = new float[16];
    private final float[] mDashLocal = new float[16];
    private final float[] mDashWorld = new float[16];

    /**
     * 场上/手牌直接选择模式：为每张 is_selectable 的卡片绘制黄色轮廓线，
     * 未选中(is_selected=false)为虚线行进、已选中为实线（对齐 gframe stipple=!is_selected）
     */
    private void drawCardSelectOutlines(GameField f) {
        List<GameField.ClientCard> list = f.selectableCards;
        if (list == null || list.isEmpty()) return;
        float phase = animTimeMs * 0.001f * MARCH_PX_PER_SEC;
        for (int i = 0, n = list.size(); i < n; i++) {
            GameField.ClientCard c;
            try {
                c = list.get(i);
            } catch (Throwable e) {
                continue;
            }
            if (c == null || !c.is_selectable) continue;
            if (c.curAlpha <= 2f) continue;
            try {
                drawCardMarchingOutline(c, c.is_selected, phase);
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * 单卡轮廓蚂蚁线：构建卡片模型矩阵，四角投影到屏幕取像素边长，按 16bit stipple(0x0f0f)
     * 沿周长走像素；亮段在卡片局部空间构建细矩形经卡片矩阵变换绘制（兼容手卡 billboard/守备旋转）。
     * 线宽按局部 x/y 轴的世界缩放换算，保证屏幕像素宽度恒定
     */
    private void drawCardMarchingOutline(GameField.ClientCard c, boolean solid, float phase) {
        buildCardModel(c, mOutlineModel);
        float sx = (float) Math.sqrt(mOutlineModel[0] * mOutlineModel[0]
                + mOutlineModel[1] * mOutlineModel[1] + mOutlineModel[2] * mOutlineModel[2]);
        float sy = (float) Math.sqrt(mOutlineModel[4] * mOutlineModel[4]
                + mOutlineModel[5] * mOutlineModel[5] + mOutlineModel[6] * mOutlineModel[6]);
        if (sx < 1e-5f || sy < 1e-5f) return;
        // 轮廓线始终抬到朝向相机的一侧，否则盖放卡（背面朝相机）的轮廓会被卡背遮住
        float outZ = isFrontFacing(mOutlineModel) ? 0.002f : -0.002f;
        float[] lx = {-0.5f, 0.5f, -0.5f, 0.5f};
        float[] ly = {-0.5f, -0.5f, 0.5f, 0.5f};
        int[] es = {0, 1, 3, 2};
        int[] ee = {1, 3, 2, 0};
        float[] px = new float[4], py = new float[4];
        float[] v = new float[4];
        for (int i = 0; i < 4; i++) {
            v[0] = lx[i]; v[1] = ly[i]; v[2] = 0f; v[3] = 1f;
            Matrix.multiplyMV(mOutlineTmp, 0, mOutlineModel, 0, v, 0);
            float[] s = projectWorldPoint(mOutlineTmp[0], mOutlineTmp[1], mOutlineTmp[2]);
            if (s == null) return;
            px[i] = s[0]; py[i] = s[1];
        }
        float patternCursor = 0f;
        for (int i = 0; i < 4; i++) {
            int a = es[i], d = ee[i];
            float screenLen = (float) Math.hypot(px[d] - px[a], py[d] - py[a]);
            if (screenLen < 1f) continue;
            float dlx = lx[d] - lx[a], dly = ly[d] - ly[a];
            boolean horizontal = Math.abs(dlx) >= Math.abs(dly);
            float alongScale = horizontal ? sx : sy;
            float perpScale = horizontal ? sy : sx;
            float thickLocal = OUTLINE_PX * alongScale / (perpScale * screenLen);
            float ang = (float) Math.toDegrees(Math.atan2(dly, dlx));
            float cursor = 0f;
            while (cursor < screenLen) {
                boolean on = solid
                        || ((STIPPLE_MASK >> ((int) (phase + patternCursor + cursor) & 0xf)) & 1) != 0;
                float runEnd = cursor + 1f;
                if (!solid) {
                    while (runEnd < screenLen
                            && ((((STIPPLE_MASK >> ((int) (phase + patternCursor + runEnd) & 0xf)) & 1) != 0) == on)) {
                        runEnd += 1f;
                    }
                } else {
                    runEnd = screenLen;
                }
                if (runEnd > screenLen) runEnd = screenLen;
                if (on) {
                    float t0 = cursor / screenLen, t1 = runEnd / screenLen;
                    float cx2 = lx[a] + dlx * (t0 + t1) * 0.5f;
                    float cy2 = ly[a] + dly * (t0 + t1) * 0.5f;
                    float segLen = (t1 - t0);
                    Matrix.setIdentityM(mDashLocal, 0);
                    Matrix.translateM(mDashLocal, 0, cx2, cy2, outZ);
                    Matrix.rotateM(mDashLocal, 0, ang, 0f, 0f, 1f);
                    Matrix.scaleM(mDashLocal, 0, segLen, thickLocal, 1f);
                    Matrix.multiplyMM(mDashWorld, 0, mOutlineModel, 0, mDashLocal, 0);
                    drawQuadColor(mDashWorld, 1f, 1f, 0f, 0.95f);
                }
                cursor = runEnd;
            }
            patternCursor = (patternCursor + screenLen) % 16f;
        }
    }

    /**
     * 构建卡片模型矩阵（drawCard 与选择轮廓共用，避免姿态计算分叉）：
     * 手卡走相机 billboard，场上卡按 Y→X→Z 旋转，末尾统一 scale(CARD_W, CARD_H)
     */
    private void buildCardModel(GameField.ClientCard c, float[] out) {
        buildCardModel(c, out, 0f);
    }

    private void buildCardModel(GameField.ClientCard c, float[] out, float zBias) {
        Matrix.setIdentityM(out, 0);
        if (c.location == 0x02) {
            Matrix.translateM(out, 0, mirrorX(c.curX),
                    handY(c) + mCamRot[5] * handLift(c), c.curZ + handLiftZ(c));
            Matrix.multiplyMM(mModelTmp, 0, out, 0, mCamRot, 0);
            System.arraycopy(mModelTmp, 0, out, 0, 16);
            Matrix.scaleM(out, 0, CARD_W, CARD_H, 1f);
        } else {
            boolean isPile = c.location == 0x01 || c.location == 0x10
                    || c.location == 0x20 || c.location == 0x40;
            float jx = isPile ? ((c.sequence % 3) - 1) * 0.012f : 0f;
            float jy = isPile ? (((c.sequence / 3) % 3) - 1) * 0.012f : 0f;
            Matrix.translateM(out, 0, mirrorX(c.curX) + jx, c.curY + jy, c.curZ + zBias);
            Matrix.rotateM(out, 0, (float) Math.toDegrees(-c.curRotY), 0f, 1f, 0f);
            Matrix.rotateM(out, 0, (float) Math.toDegrees(c.curRotX), 1f, 0f, 0f);
            Matrix.rotateM(out, 0, (float) Math.toDegrees(-c.curRotZ), 0f, 0f, 1f);
            Matrix.scaleM(out, 0, CARD_W, CARD_H, 1f);
        }
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
            if (n == 0) return;
            // 问题4：GameField 对 seq>18 的 curZ 封顶，旧实现绘制的顶部多层全部共面 → 无厚度且 z-fighting。
            // 改为仅绘最上 PILE_MAX_LAYERS 层，并在 PILE_BASE_Z 之上按张数均匀抬升显式层高，形成随数量增长的厚度。
            int layers = Math.min(n, PILE_MAX_LAYERS);
            int skip = n - layers;
            float thick = layers * PILE_LAYER_THICK;
            float step = layers > 1 ? thick / (layers - 1) : 0f;
            int drawn = 0;
            for (int i = 0, s = pile.size(); i < s; i++) {
                GameField.ClientCard c;
                try {
                    c = pile.get(i);
                } catch (Throwable e) {
                    continue;
                }
                if (c == null) continue;
                if (skip-- > 0) continue;
                float targetZ = PILE_BASE_Z + drawn * step;
                drawCard(c, targetZ - c.curZ);
                drawn++;
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * 单面卡片绘制（对齐 gframe drawing.cpp：每张卡只画朝向相机的那一面）。
     * 位置取 getCardLocation 动画值并做 X 镜像；旋转按 Y→X→Z 合成、Y/Z 轴取反
     * （空间镜像使绕 Y/Z 旋转反向），保证 gframe 各位置的面朝：
     * 对方暗手牌（rotX+rotY=π）卡背朝相机、守备/盖放/堆叠区朝向均正确。
     * <p>
     * 不再正反两面同绘：两面只差 0.002 的层间距，在远视点下不足一个深度台阶，
     * 会出现“半张卡图 + 半张卡背”的 z-fighting，盖放卡还会被底板吞掉；
     * 单面绘制同时把 overdraw 减半。
     */
    private void drawCard(GameField.ClientCard c) {
        drawCard(c, 0f);
    }

    private void drawCard(GameField.ClientCard c, float zBias) {
        if (c == null) return;
        float alpha = Math.max(0f, Math.min(1f, c.curAlpha / 255f));
        if (alpha <= 0.01f) return;

        boolean isHand = c.location == 0x02;
        buildCardModel(c, mModel, zBias);
        boolean front = isFrontFacing(mModel);

        int glow = pickGlowColor(c);
        if (glow != 0) drawGlow(glow, alpha, front);

        int code = c.code != 0 ? c.code : (c.is_moving ? c.chain_code : 0);
        if (isHand) {
            // 手卡为 billboard，恒正面朝向相机
            if (code > 0 && (c.controler == 0 || c.isFaceUp())) {
                int tex = obtainTexture(code, pendulumMode(c), pendulumScale(c));
                if (tex > 0) {
                    drawQuadTex(mModel, tex, alpha, 0f, 1f);
                } else {
                    drawQuadColor(mModel, 0.35f, 0.35f, 0.40f, alpha);
                }
            } else {
                drawCoverQuad(mModel, true, alpha, 0f, 1f);
            }
            return;
        }

        boolean faceUp = c.isFaceUp();
        if (!front) {
            // 背面朝向相机（盖放/守备盖放/卡组背面）：绕局部 Y 翻 180° 后绘卡背，
            // 卡背自身正面朝相机，贴图方向与 gframe 一致且不与任何面共面
            System.arraycopy(mModel, 0, mModelTmp, 0, 16);
            Matrix.rotateM(mModelTmp, 0, 180f, 0f, 1f, 0f);
            drawCoverQuad(mModelTmp, c.owner != 0, alpha, 1f, 0f);
        } else if (faceUp && code > 0) {
            int tex = obtainTexture(code, pendulumMode(c), pendulumScale(c));
            if (tex > 0) {
                drawQuadTex(mModel, tex, alpha);
            } else {
                drawQuadColor(mModel, 0.35f, 0.35f, 0.40f, alpha);
            }
        } else {
            // 正面朝向相机但非表侧表示（卡组顶等）：gframe 同样贴卡背材质
            drawCoverQuad(mModel, c.owner != 0, alpha, 1f, 0f);
        }
    }

    /**
     * 卡片正面（局部 +Z）是否朝向视点：model 第 3 列为正面法线（Z 缩放恒为 1，仍是单位向量）、
     * 第 4 列为卡片中心
     */
    private boolean isFrontFacing(float[] model) {
        float nx = model[8], ny = model[9], nz = model[10];
        return (camEyeX - model[12]) * nx + (camEyeY - model[13]) * ny + (camEyeZ - model[14]) * nz >= 0f;
    }

    private void drawCoverQuad(float[] model, boolean opponent, float alpha, float flipU, float flipV) {
        int coverTex = obtainCover(opponent);
        if (coverTex > 0) {
            drawQuadTex(model, coverTex, alpha, flipU, flipV);
        } else {
            drawQuadColor(model, 0.24f, 0.18f, 0.13f, alpha);
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
     * 我方手卡后移量：基准量由 solveCamera 按俯仰角动态解算（保证不遮挡魔陷区），
     * 再叠加 XML field_hand_shift 的手动微调；对方手卡保持 gframe 原位
     */
    private float handY(GameField.ClientCard c) {
        return c.curY - (c.controler == 0 ? selfHandShift : 0f);
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

    private void drawGlow(int color, float cardAlpha, boolean front) {
        System.arraycopy(mModel, 0, mModelTmp, 0, 16);
        // 背面朝向相机时偏移取反，保证光晕恒落在卡片之下（否则会给卡背染色）
        Matrix.translateM(mModelTmp, 0, mModelTmp, 0, 0f, 0f, front ? -0.004f : 0.004f);
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
        drawQuadTexUV(model, texId, alpha, flipU, flipV, 0f, 0f, 1f, 1f);
    }

    private void drawQuadTexUV(float[] model, int texId, float alpha, float flipU, float flipV,
                               float offU, float offV, float scU, float scV) {
        if (texId <= 0) return;
        GLES30.glUseProgram(texProg);
        Matrix.multiplyMM(mMVP, 0, mVP, 0, model, 0);
        GLES30.glUniformMatrix4fv(texLocMVP, 1, false, mMVP, 0);
        GLES30.glUniform4f(texLocTint, 1f, 1f, 1f, alpha);
        GLES30.glUniform1f(texLocFlipU, flipU);
        GLES30.glUniform1f(texLocFlipV, flipV);
        GLES30.glUniform4f(texLocUVRect, offU, offV, scU, scV);
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
        boolean enabled = phaseButtonsEnabled;
        float a = enabled ? 1f : 0.4f;   // 禁用时整体变暗
        float cx = rect[0], cy = rect[1], bw = rect[2], bh = rect[3];
        drawScreenQuadColor(cx, cy, bw + 3f, bh + 3f, 0.04f, 0.08f, 0.12f, 0.92f * a);
        if (pressed) {
            drawScreenQuadColor(cx, cy, bw, bh, 0.15f, 0.22f, 0.32f, 0.94f * a);
        } else if (enabled) {
            drawScreenQuadColor(cx, cy, bw, bh, 0.30f, 0.44f, 0.58f, 0.90f);
        } else {
            drawScreenQuadColor(cx, cy, bw, bh, 0.16f, 0.24f, 0.32f, 0.90f);
        }
        int tex = obtainPhaseLabelTexture(label);
        if (tex > 0) {
            // 标签位图固定 256×80：按 3.2:1 铺展，宽度封顶按钮内宽（短文字两侧留透明区）
            float tw = Math.min(bh * 3.2f, bw * 0.96f);
            drawScreenQuadTex(cx, cy, tw, tw / 3.2f, tex, a);
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
        GLES30.glUniform4f(texLocUVRect, 0f, 0f, 1f, 1f);
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

    /**
     * 场地底板纹理（drawing.cpp L369：drawField ? tFieldTransparent[rule] : tField[rule]）：
     * 首次请求时工作线程解码，下一帧 drainUploads 上传；未就绪返回 -1，由 drawFieldBoard 兜底底色填充
     */
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
    private int obtainFieldSpellTexture(int code) {
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

    /**
     * 点按手势统一入口：手势识别（GestureDetector）已迁移至 GameFieldViewController，
     * 由其识别出手势后回调本入口完成拾取与回调分发，拾取逻辑不变
     */
    public void dispatchTap(float x, float y) {
        handleTap(x, y);
    }

    /**
     * 长按手势统一入口（见 {@link #dispatchTap}）
     */
    public void dispatchLongPress(float x, float y) {
        handleLongPress(x, y);
    }

    /**
     * 兜底消费：手势识别已迁移至 GameFieldViewController 的 OnTouchListener，
     * 此处仅防止事件穿透到下层控件
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
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
        float[] r = GameField.getZoneRect(player, loc, seq);
        return r == null ? new float[]{FIELD_CENTER_X, 0f} : new float[]{r[0], r[1]};
    }

    private static float[] pileCenter(int player, int loc) {
        float[] r = GameField.getPileRect(player, loc);
        return r == null ? null : new float[]{r[0], r[1]};
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

    // ==================== 设计时预览（XML 布局编辑器，isInEditMode）====================

    /**
     * 布局编辑器下无法启动 GL/EGL，这里用 Canvas 绘制等效预览：
     * 相机与运行期 updateCamera 同一套解算（纯 Java 实现，不依赖 android.opengl），
     * 区域/堆叠区/手卡坐标与运行期 zoneCenter/pileCenter/getCardLocation 完全同源，
     * 参数全部取 XML 属性值，编辑器中改属性即时可见
     */
    @Override
    public void draw(Canvas canvas) {
        if (!isInEditMode()) {
            super.draw(canvas);
            return;
        }
        try {
            drawEditPreview(canvas);
        } catch (Throwable t) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(0xFFFF8080);
            p.setTextSize(12f * getResources().getDisplayMetrics().density);
            canvas.drawText("GameFieldView preview error: " + t, 8f, 16f, p);
        }
    }

    private void drawEditPreview(Canvas canvas) {
        int w = getWidth(), h = getHeight();
        if (w <= 1 || h <= 1) return;
        float density = getResources().getDisplayMetrics().density;
        // 设计时底色（运行期为透明 GL 面透出背景；预览用实色底保证可读性）
        canvas.drawColor(0xFF0B1118);
        EditCamera cam = computeEditCamera(w, h);
        if (cam == null) return;

        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(Math.max(1f, density));

        // 场地底板（运行期 drawFieldBoard 的兜底底色同色系）
        float boardCX = (FIELD_X_MIN + FIELD_X_MAX) / 2f;
        float boardW = FIELD_X_MAX - FIELD_X_MIN, boardH = FIELD_Y_MAX - FIELD_Y_MIN;
        fill.setColor(0xFF14212C);
        drawWorldQuad(canvas, fill, cam, w, h, boardCX, 0f, 0f, boardW, boardH);
        line.setColor(0xFF2E4A5E);
        drawWorldQuad(canvas, line, cam, w, h, boardCX, 0f, 0f, boardW, boardH);

        if (previewShowZones) {
            // 区域槽位：与运行期 drawZoneSlots 同一套区域遍历（每方 怪兽7 + 魔陷6 + 堆叠区4）
            fill.setColor(0x3300C8F0);
            line.setColor(0x8800C8F0);
            for (int p = 0; p < 2; p++) {
                for (int i = 0; i < 7; i++) {
                    float[] c = zoneCenter(p, 0x04, i);
                    drawWorldQuad(canvas, fill, cam, w, h, c[0], c[1], 0f, ZONE_W, ZONE_H);
                    drawWorldQuad(canvas, line, cam, w, h, c[0], c[1], 0f, ZONE_W, ZONE_H);
                }
                for (int i = 0; i <= 5; i++) {
                    float[] c = zoneCenter(p, 0x08, i);
                    drawWorldQuad(canvas, fill, cam, w, h, c[0], c[1], 0f, ZONE_W, ZONE_H);
                    drawWorldQuad(canvas, line, cam, w, h, c[0], c[1], 0f, ZONE_W, ZONE_H);
                }
                for (int loc : new int[]{0x01, 0x10, 0x20, 0x40}) {
                    float[] c = pileCenter(p, loc);
                    if (c == null) continue;
                    drawWorldQuad(canvas, fill, cam, w, h, c[0], c[1], 0f, PILE_W, PILE_H);
                    drawWorldQuad(canvas, line, cam, w, h, c[0], c[1], 0f, PILE_W, PILE_H);
                }
            }
        }

        if (previewShowLabels) {
            Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
            text.setColor(0xCCFFFFFF);
            text.setTextSize(8f * density);
            text.setTextAlign(Paint.Align.CENTER);
            drawLabel(canvas, text, cam, w, h, pileCenter(0, 0x01), "卡组");
            drawLabel(canvas, text, cam, w, h, pileCenter(0, 0x10), "墓地");
            drawLabel(canvas, text, cam, w, h, pileCenter(0, 0x20), "除外");
            drawLabel(canvas, text, cam, w, h, pileCenter(0, 0x40), "额外");
            drawLabel(canvas, text, cam, w, h, pileCenter(1, 0x01), "卡组");
            drawLabel(canvas, text, cam, w, h, pileCenter(1, 0x10), "墓地");
            drawLabel(canvas, text, cam, w, h, pileCenter(1, 0x20), "除外");
            drawLabel(canvas, text, cam, w, h, pileCenter(1, 0x40), "额外");
            drawLabel(canvas, text, cam, w, h, zoneCenter(0, 0x08, 5), "场地");
            drawLabel(canvas, text, cam, w, h, zoneCenter(1, 0x08, 5), "场地");
        }

        if (previewShowHand) {
            // 示例手卡：每方 5 张，坐标同运行期 getCardLocation(LOCATION_HAND)，
            // 我方行位用 solveCamera 解算出的动态后移量（与运行期完全一致）
            float spacing = 0.95f;
            for (int i = 0; i < 5; i++) {
                drawHandCard(canvas, cam, w, h, 3.95f - spacing * 2f + i * spacing,
                        SELF_HAND_Y - cam.selfHandShift, HAND_Z, 0xFF33516E);
                drawHandCard(canvas, cam, w, h, 3.95f + spacing * 2f - i * spacing,
                        OPP_HAND_Y, HAND_Z, 0xFF5C4433);
            }
        }

        // 参数读数：调整 XML 属性时便于对照
        Paint info = new Paint(Paint.ANTI_ALIAS_FLAG);
        info.setColor(0xAAFFFFFF);
        info.setTextSize(9f * density);
        canvas.drawText(String.format(Locale.US,
                        "GameFieldView 设计预览  俯仰角=%.0f° 视点距离=%.2f 场地倍率=%.2f 手卡后移=%.3f(解算) 视高=%.1f°",
                        cameraElevationDeg, cameraDistance, fieldZoom, cam.selfHandShift,
                        Math.toDegrees(2.0 * Math.atan(cam.halfH))),
                6f * density, h - 6f * density, info);
    }

    /**
     * 预览手卡：运行期手卡为平行屏幕的 billboard，此处按投影中心画 177:254 比例圆角矩形，
     * 尺寸取卡片世界宽度 CARD_W 的投影像素宽估算
     */
    private void drawHandCard(Canvas canvas, EditCamera cam, int w, int h,
                              float x, float y, float z, int color) {
        float[] c = projectEdit(cam, w, h, mirrorX(x), y, z);
        float[] e = projectEdit(cam, w, h, mirrorX(x + CARD_W / 2f), y, z);
        if (c == null || e == null) return;
        float halfW = Math.max(2f, Math.abs(e[0] - c[0]));
        float halfH = halfW * CARD_H / CARD_W;
        float radius = halfW * 0.12f;
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(color);
        canvas.drawRoundRect(c[0] - halfW, c[1] - halfH, c[0] + halfW, c[1] + halfH, radius, radius, fill);
        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        line.setStyle(Paint.Style.STROKE);
        line.setColor(0xAAFFFFFF);
        line.setStrokeWidth(Math.max(1f, getResources().getDisplayMetrics().density * 0.75f));
        canvas.drawRoundRect(c[0] - halfW, c[1] - halfH, c[0] + halfW, c[1] + halfH, radius, radius, line);
    }

    private void drawLabel(Canvas canvas, Paint text, EditCamera cam, int w, int h,
                           float[] center, String label) {
        if (center == null) return;
        float[] s = projectEdit(cam, w, h, mirrorX(center[0]), center[1], 0.02f);
        if (s == null) return;
        canvas.drawText(label, s[0], s[1] + text.getTextSize() / 3f, text);
    }

    /**
     * 世界矩形（中心+宽高，先做与运行期一致的 X 镜像）→ 投影四角 → Canvas 四边形
     */
    private void drawWorldQuad(Canvas canvas, Paint paint, EditCamera cam, int w, int h,
                               float cx, float cy, float z, float qw, float qh) {
        float x0 = mirrorX(cx - qw / 2f), x1 = mirrorX(cx + qw / 2f);
        float y0 = cy - qh / 2f, y1 = cy + qh / 2f;
        float[] p1 = projectEdit(cam, w, h, x0, y0, z);
        float[] p2 = projectEdit(cam, w, h, x1, y0, z);
        float[] p3 = projectEdit(cam, w, h, x1, y1, z);
        float[] p4 = projectEdit(cam, w, h, x0, y1, z);
        if (p1 == null || p2 == null || p3 == null || p4 == null) return;
        Path path = new Path();
        path.moveTo(p1[0], p1[1]);
        path.lineTo(p2[0], p2[1]);
        path.lineTo(p3[0], p3[1]);
        path.lineTo(p4[0], p4[1]);
        path.close();
        canvas.drawPath(path, paint);
    }

    /**
     * 设计时预览相机：与运行期 updateCamera 共用 {@link #solveCamera(int, int)}
     * （编辑器中 android.opengl 为 stub，不可用；此处只用 java.lang.Math 还原同一套外参/内参）
     */
    private static final class EditCamera {
        float eyeX, eyeY, eyeZ;      // 相机位置
        float fx, fy, fz;            // 视线前向轴
        float rx, ry, rz;            // 屏幕右轴
        float ux, uy, uz;            // 屏幕上轴
        float halfH, halfW;          // 竖向半高 tan（离轴=frustumHH）与横向半宽 tan（=halfH*aspect）
        float frustumC;              // 离轴视锥竖向中心偏移 tan
        float selfHandShift;         // 我方手卡行后移量
    }

    private EditCamera computeEditCamera(int w, int h) {
        CameraSolve s = solveCamera(w, h);
        if (!s.valid) return null;
        float aspect = (float) w / h;
        EditCamera cam = new EditCamera();
        cam.eyeX = CAM_X;
        cam.eyeY = s.eyeY;
        cam.eyeZ = s.eyeZ;
        cam.fx = 0f;
        cam.fy = s.dirY;
        cam.fz = s.dirZ;
        cam.rx = s.dirY < 0f ? -1f : 1f;
        cam.ry = 0f;
        cam.rz = 0f;
        cam.ux = 0f;
        cam.uy = -cam.rx * s.dirZ;
        cam.uz = cam.rx * s.dirY;
        cam.halfH = s.frustumHH;
        cam.halfW = s.frustumHH * aspect;
        cam.frustumC = s.frustumC;
        cam.selfHandShift = s.selfHandShift;
        return cam;
    }

    private static float[] projectEdit(EditCamera cam, int w, int h, float x, float y, float z) {
        float vx = x - cam.eyeX, vy = y - cam.eyeY, vz = z - cam.eyeZ;
        float zc = vx * cam.fx + vy * cam.fy + vz * cam.fz;
        if (zc <= 0.05f) return null;
        float xc = vx * cam.rx + vy * cam.ry + vz * cam.rz;
        float yc = vx * cam.ux + vy * cam.uy + vz * cam.uz;
        float nx = xc / (zc * cam.halfW);
        float ny = ((yc / zc) - cam.frustumC) / cam.halfH;
        return new float[]{(nx + 1f) * 0.5f * w, (1f - ny) * 0.5f * h};
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