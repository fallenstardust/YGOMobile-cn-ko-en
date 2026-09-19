package cn.garymb.ygomobile.render;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
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
 * 本类是渲染门面：持有 GL 上下文生命周期、卡片绘制核心与底层绘制原语，并按 {@code // ===} 功能分栏
 * 委派给同包协作类——{@link FieldGeometry}（几何单一真值）、{@link FieldCamera}（相机解算）、
 * {@link FieldTextureManager}（纹理）、{@link FieldBoardRenderer}（底板/格子/总攻击力/act/conti）、
 * {@link CardOverlayRenderer}（状态图标/连锁/攻击弧）、{@link FieldHudRenderer}（数字/属性文字/灵摆刻度）、
 * {@link SelectionOutlineRenderer}（高亮虚线/选择轮廓）、{@link PhaseButtonRenderer}（阶段按钮）、
 * {@link FieldTouchPicker}（射线拾取）、{@link FieldEditPreview}（设计时预览）。协作类经构造注入本视图，
 * 以同包包级私有直连共享 GL 状态与绘制原语。
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

        /** 长按命中卡片：x/y 为视图内触点坐标（状态悬浮标签锚定用） */
        void onFieldLongPress(int player, int location, int sequence, float x, float y);

        /** 长按手势结束（抬手/取消）：宿主据此隐藏状态悬浮标签 */
        void onFieldLongPressEnd();
    }

    /**
     * 场内绘制的阶段按钮点击回调（当前阶段按钮仅作指示，不产生回调）
     */
    public interface OnPhaseButtonListener {
        void onPhaseNextClicked();

        void onPhaseEpClicked();
    }

    // === 堆叠区厚度（问题4）：可见层在 PILE_BASE_Z 之上按张数均匀抬升，形成侧视厚度 ===
    // 卡片核心 drawPile 与 FieldBoardRenderer 的 act 图标抬升共用，故集中于门面（包级静态）。
    static final float PILE_BASE_Z = 0.02f;
    static final int PILE_MAX_LAYERS = 14;
    static final float PILE_LAYER_THICK = 0.012f;

    // 选中手卡抬高量
    private static final float HAND_LIFT = 0.3f;
    // 场地视觉倍率：1.0=全部可交互格子/堆叠区恰好完整入镜；<1 裁掉边缘换取更大的卡片；>1 留更多边距
    private static final float FIELD_ZOOM = 1.0f;
    // 我方手卡手动微调偏置基准（正值=再向场地外侧后移）；基准后移量由 solveCamera 按俯仰角动态解算
    private static final float HAND_SELF_Y_SHIFT = 0f;

    // === 相机视角参数上下界与默认值（public：设置页与控制器读写）===
    public static final float DEFAULT_CAMERA_ELEVATION = 52f;
    public static final float MIN_CAMERA_ELEVATION = 35f;
    public static final float MAX_CAMERA_ELEVATION = 75f;
    private static final String PREF_CAMERA_ELEVATION = "camera_elevation";
    // 视点到注视点距离默认值（与俯仰角共同决定相机位置）
    private static final float CAMERA_DISTANCE = 7.6f;

    // === XML 可调参数（declare-styleable GameFieldView，常量兜底默认值）===
    float cameraDistance = CAMERA_DISTANCE;
    float fieldZoom = FIELD_ZOOM;
    float handSelfYShift = HAND_SELF_Y_SHIFT;
    // XML 是否显式声明了俯仰角（声明时优先于游戏内保存的设置）
    private boolean elevationFromXml = false;
    // 设计时预览开关（仅 isInEditMode 生效）
    boolean previewShowZones = true;
    boolean previewShowHand = true;
    boolean previewShowLabels = true;
    float cameraElevationDeg = DEFAULT_CAMERA_ELEVATION;
    private volatile boolean cameraDirty = false;

    // === 着色器（ES 3.0 / GLSL 300 es）：纹理贴图（含 UV 翻转子矩形）与纯色 ===
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

    // === 业务状态（公开 API 写入，GL 线程读取；部分供协作类包级直连）===
    volatile GameField field;
    private volatile ImageLoader imageLoader;
    volatile OnCardClickListener cardClickListener;
    volatile int highlightFieldMask = 0;
    private volatile int selectedPlayer = -1;
    private volatile int selectedLocation = -1;
    private volatile int selectedSequence = -1;
    // 动画倍率：1=原速，2=2 倍速（时间驱动，帧率无关）
    private volatile float animSpeedMultiplier = 1f;

    // === 阶段按钮（场内绘制：主线程写状态，GL 线程读取绘制/命中；由 PhaseButtonRenderer 读取）===
    volatile OnPhaseButtonListener phaseButtonListener;
    volatile boolean phaseCurrentVisible = false;
    volatile String phaseCurrentLabel = "";
    volatile String phaseNextLabel = "";
    volatile boolean phaseEpVisible = false;
    // 模态对话框（是/否、卡片选择/确认、命令菜单）显示期间禁用三个阶段按钮
    volatile boolean phaseButtonsEnabled = true;

    // === GL 资源（着色器程序句柄 / uniform 位置 / 单位矩形 VAO，仅底层绘制原语使用）===
    private int texProg, colorProg;
    private int texLocMVP, texLocTint, texLocTex, texLocFlipU, texLocFlipV, texLocUVRect;
    private int colorLocMVP, colorLocColor;
    private int vao;

    // === 矩阵 scratch：mModel/mMVP/mModelTmp 供卡片核心与底层原语；mOrthoVP 供 HUD/阶段按钮屏幕绘制 ===
    final float[] mMVP = new float[16];
    final float[] mModel = new float[16];
    final float[] mModelTmp = new float[16];
    final float[] mOrthoVP = new float[16];

    volatile int viewW = 1, viewH = 1;
    // 顶部内缩像素（问题1）：由控制器传入 gameTopInfo 实测高度，FieldCamera 据此把对方手卡压到其下
    volatile float topInsetPx = 0f;
    // 相机重建通知（GL 线程重建相机后，覆盖层需重新锚定阶段按钮位置）
    volatile Runnable onCameraChangedListener;

    private long lastFrameNs = 0;
    volatile long animTimeMs = 0;

    // === 协作类（按功能分栏委派，构造注入本视图，同包包级私有直连共享 GL 状态）===
    FieldCamera cam;
    FieldTextureManager tex;
    FieldBoardRenderer board;
    CardOverlayRenderer overlays;
    FieldHudRenderer hud;
    SelectionOutlineRenderer outlines;
    PhaseButtonRenderer phase;
    FieldTouchPicker picker;
    FieldEditPreview preview;

    public GameFieldView(Context context) {
        super(context);
        wire();
        if (!isInEditMode()) init();
    }

    public GameFieldView(Context context, AttributeSet attrs) {
        super(context, attrs);
        readXmlAttributes(context, attrs);
        wire();
        if (!isInEditMode()) init();
    }

    /**
     * 装配 10 个协作类（FieldGeometry 为纯静态工具无需实例）。所有协作类仅持有本视图引用、
     * 通过包级私有直连读取共享 GL 状态，构造顺序无依赖（真正使用时机在 onSurfaceCreated/onDrawFrame/
     * onTouchEvent/draw 之后）。
     */
    private void wire() {
        cam = new FieldCamera(this);
        tex = new FieldTextureManager(this);
        board = new FieldBoardRenderer(this);
        overlays = new CardOverlayRenderer(this);
        hud = new FieldHudRenderer(this);
        outlines = new SelectionOutlineRenderer(this);
        phase = new PhaseButtonRenderer(this);
        picker = new FieldTouchPicker(this);
        preview = new FieldEditPreview(this);
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
        setEGLConfigChooser(new FieldCamera.DepthConfigChooser());
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

    /** 当前选中（移动端语义=点击，对位桌面悬停 hovered_card）并联动展示关联图标的卡片 */
    volatile GameField.ClientCard markedCard;

    /**
     * event_handler.cpp 悬停触发 SetShowMark 的点击版：先对上一张解除装备/对象/连锁对象
     * 图标标记，再对新点击卡置位；由 FieldTouchPicker 在卡片命中处调用
     */
    void applyShowMarks(GameField.ClientCard card) {
        GameField f = field;
        if (f == null) return;
        GameField.ClientCard old = markedCard;
        if (old == card) return;
        if (old != null) {
            try {
                f.setShowMark(old, false);
            } catch (Throwable ignored) {
            }
        }
        markedCard = card;
        if (card != null) {
            try {
                f.setShowMark(card, true);
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * 当前选中的场上格子（仅 MZONE 0x04 / SZONE 0x08）：供
     * {@link SelectionOutlineRenderer#drawSelFieldOverlay} 绘制 selfield 与连接箭头；无则 null
     */
    int[] selectedFieldZone() {
        int p = selectedPlayer;
        int loc = selectedLocation;
        int seq = selectedSequence;
        if (p < 0 || p > 1 || seq < 0) return null;
        if (loc != 0x04 && loc != 0x08) return null;
        return new int[]{p, loc, seq};
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
        return projectWorldPoint(FieldGeometry.FIELD_CENTER_X, 0f, 0f);
    }

    /**
     * 世界坐标（绘制空间：x 需为镜像后坐标，与 drawCard/drawZoneSlots 传入 mVP 前一致）
     * 投影到屏幕像素坐标；相机未就绪返回 null。camLock 保护，任意线程可调用
     */
    public float[] projectWorldPoint(float x, float y, float z) {
        return cam.projectWorldPoint(x, y, z);
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
        return cam.computeOpponentHandTopScreenY();
    }

    /**
     * 长按气泡标签锚定用：把指定卡在屏幕上绘制的实际四边形投影为包围盒，
     * 返回 {centerX, topY, bottomY}（相对本 View 左上角，像素）；相机未就绪 / 取卡失败返回 null。
     * 拾取 / 锚定逻辑归 {@link FieldTouchPicker}。
     */
    public float[] getCardScreenBounds(int player, int location, int sequence) {
        return picker.getCardScreenBounds(player, location, sequence);
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

    // === 触摸拾取入口：手势识别在 GameFieldViewController，回调转发给 FieldTouchPicker ===

    /**
     * 点按手势统一入口：由其识别出手势后回调本入口完成拾取与回调分发，拾取逻辑不变
     */
    public void dispatchTap(float x, float y) {
        picker.handleTap(x, y);
    }

    /**
     * 长按手势统一入口（见 {@link #dispatchTap}）
     */
    public void dispatchLongPress(float x, float y) {
        picker.handleLongPress(x, y);
    }

    /**
     * 长按结束入口：由控制器在 ACTION_UP/CANCEL 时调用，转发给业务方隐藏状态标签
     */
    public void dispatchLongPressEnd() {
        picker.dispatchLongPressEnd();
    }

    /**
     * 兜底消费：手势识别已迁移至 GameFieldViewController 的 OnTouchListener，
     * 此处仅防止事件穿透到下层控件
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return true;
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

        // 需求3：攻击弧 3D 逐顶点色程序与动态 VAO/VBO（CardOverlayRenderer 独占）
        overlays.initArrow();

        // 上下文（重新）创建：纹理缓存全部失效，重新按需加载；阶段标签键序列复位
        tex.clearAll();
        phase.clearLabelKeys();
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int w, int h) {
        viewW = w;
        viewH = h;
        GLES30.glViewport(0, 0, w, h);
        cam.updateCamera();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        // 折叠屏展开/折叠、分屏拖拽：View 尺寸变化后兜底重建相机（onSurfaceChanged 未覆盖时的保险）
        cameraDirty = true;
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
            cam.updateCamera();
        }

        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT | GLES30.GL_DEPTH_BUFFER_BIT);
        try {
            tex.drainUploads();
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
            board.drawFieldBoard(f);
        } catch (Throwable ignored) {
        }
        board.drawZoneSlots(f);
        try {
            board.drawTotalAttackBars(f);
        } catch (Throwable ignored) {
        }
        // 点击格子的 selfield / 连接箭头点亮（drawing.cpp DrawBackGround 悬停块，z=0.006 绘于卡片之下）
        try {
            outlines.drawSelFieldOverlay(f);
        } catch (Throwable ignored) {
        }
        try {
            drawFieldCards(f);
        } catch (Throwable ignored) {
        }
        try {
            overlays.drawFieldCardOverlays(f);
            board.drawZoneActHints(f);
            board.drawContiGrid(f);
        } catch (Throwable ignored) {
        }
        GLES30.glDepthMask(false);
        outlines.drawHighlights();
        outlines.drawCardSelectOutlines(f);
        phase.drawPhaseButtons();
        try {
            hud.drawFieldNumbers(f);
        } catch (Throwable ignored) {
        }
        try {
            hud.drawFieldCardTexts(f);
        } catch (Throwable ignored) {
        }
        try {
            overlays.drawChainIcons(f);
        } catch (Throwable ignored) {
        }
        try {
            overlays.drawAttackArc(f);
        } catch (Throwable t) {
            Log.w("GameFieldView", "drawAttackArc failed", t);
        }
        GLES30.glDepthMask(true);
    }

    // ==================== 卡片绘制核心（位置取 getCardLocation 动画值并做 X 镜像）====================

    /**
     * 构建卡片模型矩阵（drawCard 与选择轮廓共用，避免姿态计算分叉）：
     * 手卡走相机 billboard，场上卡按 Y→X→Z 旋转，末尾统一 scale(CARD_W, CARD_H)
     */
    void buildCardModel(GameField.ClientCard c, float[] out) {
        buildCardModel(c, out, 0f);
    }

    private void buildCardModel(GameField.ClientCard c, float[] out, float zBias) {
        Matrix.setIdentityM(out, 0);
        if (c.location == 0x02) {
            Matrix.translateM(out, 0, FieldGeometry.mirrorX(c.curX),
                    handY(c) + cam.mCamRot[5] * handLift(c), c.curZ + handLiftZ(c));
            Matrix.multiplyMM(mModelTmp, 0, out, 0, cam.mCamRot, 0);
            System.arraycopy(mModelTmp, 0, out, 0, 16);
            Matrix.scaleM(out, 0, FieldGeometry.CARD_W, FieldGeometry.CARD_H, 1f);
        } else {
            boolean isPile = c.location == 0x01 || c.location == 0x10
                    || c.location == 0x20 || c.location == 0x40;
            float jx = isPile ? ((c.sequence % 3) - 1) * 0.012f : 0f;
            float jy = isPile ? (((c.sequence / 3) % 3) - 1) * 0.012f : 0f;
            Matrix.translateM(out, 0, FieldGeometry.mirrorX(c.curX) + jx, c.curY + jy, c.curZ + zBias);
            Matrix.rotateM(out, 0, (float) Math.toDegrees(-c.curRotY), 0f, 1f, 0f);
            Matrix.rotateM(out, 0, (float) Math.toDegrees(c.curRotX), 1f, 0f, 0f);
            Matrix.rotateM(out, 0, (float) Math.toDegrees(-c.curRotZ), 0f, 0f, 1f);
            Matrix.scaleM(out, 0, FieldGeometry.CARD_W, FieldGeometry.CARD_H, 1f);
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
                int tex = obtainTexture(code, FieldGeometry.pendulumMode(c), FieldGeometry.pendulumScale(c));
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
            int tex = obtainTexture(code, FieldGeometry.pendulumMode(c), FieldGeometry.pendulumScale(c));
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
    boolean isFrontFacing(float[] model) {
        float nx = model[8], ny = model[9], nz = model[10];
        return (cam.camEyeX - model[12]) * nx + (cam.camEyeY - model[13]) * ny + (cam.camEyeZ - model[14]) * nz >= 0f;
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
    float handY(GameField.ClientCard c) {
        return c.curY - (c.controler == 0 ? cam.selfHandShift : 0f);
    }

    /**
     * 选中手卡抬升量：未选中为 0，选中为 HAND_LIFT（沿相机 up 轴抬升，绘制/命中共用）
     */
    float handLift(GameField.ClientCard c) {
        return isSelectedCard(c) ? HAND_LIFT : 0f;
    }

    /**
     * 选中手卡抬升的 Z 分量：沿相机 up 轴抬升，Z 分量 = mCamRot[6] × 抬升量
     */
    float handLiftZ(GameField.ClientCard c) {
        return cam.mCamRot[6] * handLift(c);
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

    // 卡图 / 卡背纹理由 FieldTextureManager 提供，门面卡片核心经此薄委派访问（保持 drawCard 原样）
    private int obtainTexture(int code, int mode, int scale) {
        return tex.obtainTexture(code, mode, scale);
    }

    private int obtainCover(boolean opponent) {
        return tex.obtainCover(opponent);
    }

    // ==================== 底层绘制原语（单位矩形 + 世界 / 屏幕正交投影，供各 renderer 复用）====================

    void drawFlatQuad(float cx, float cy, float z, float w, float h,
                      float r, float g, float b, float a) {
        Matrix.setIdentityM(mModel, 0);
        Matrix.translateM(mModel, 0, cx, cy, z);
        Matrix.scaleM(mModel, 0, w, h, 1f);
        drawQuadColor(mModel, r, g, b, a);
    }

    void drawQuadTex(float[] model, int texId, float alpha) {
        drawQuadTex(model, texId, alpha, 1f, 0f);
    }

    void drawQuadTex(float[] model, int texId, float alpha, float flipU, float flipV) {
        drawQuadTexUV(model, texId, alpha, flipU, flipV, 0f, 0f, 1f, 1f);
    }

    void drawQuadTexUV(float[] model, int texId, float alpha, float flipU, float flipV,
                       float offU, float offV, float scU, float scV) {
        if (texId <= 0) return;
        GLES30.glUseProgram(texProg);
        Matrix.multiplyMM(mMVP, 0, cam.mVP, 0, model, 0);
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

    void drawQuadColor(float[] model, float r, float g, float b, float a) {
        GLES30.glUseProgram(colorProg);
        Matrix.multiplyMM(mMVP, 0, cam.mVP, 0, model, 0);
        GLES30.glUniformMatrix4fv(colorLocMVP, 1, false, mMVP, 0);
        GLES30.glUniform4f(colorLocColor, r, g, b, a);
        glBindQuadVao();
    }

    /** 屏幕像素正交空间绘纯色四边形（阶段按钮底板，供 PhaseButtonRenderer 复用） */
    void drawScreenQuadColor(float cx, float cy, float w, float h,
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

    /** 屏幕像素正交空间绘贴图四边形（阶段按钮标签 / HUD 数字，供各 renderer 复用） */
    void drawScreenQuadTex(float cx, float cy, float w, float h, int texId, float alpha) {
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

    /** 供 CardOverlayRenderer 构建攻击弧 3D 逐顶点色程序（同包包级私有） */
    static int createProgram(String vs, String fs) {
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
     * 布局编辑器下无法启动 GL/EGL，这里用 Canvas 绘制等效预览（FieldEditPreview 复用同一相机解算）；
     * 运行期委托 super.draw。绘制预览异常时兜底提示，不影响编辑器加载。
     */
    @Override
    public void draw(Canvas canvas) {
        if (!isInEditMode()) {
            super.draw(canvas);
            return;
        }
        try {
            preview.drawEditPreview(canvas);
        } catch (Throwable t) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setColor(0xFFFF8080);
            p.setTextSize(12f * getResources().getDisplayMetrics().density);
            canvas.drawText("GameFieldView preview error: " + t, 8f, 16f, p);
        }
    }

    /**
     * 请求屏幕最高刷新率显示模式（高刷屏跑满 90/120/144Hz 的前提）
     */
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        FieldCamera.applyHighRefreshRate(this);
    }

    @Override
    protected void onDetachedFromWindow() {
        tex.shutdown();
        super.onDetachedFromWindow();
    }
}
