package cn.garymb.ygomobile.render;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.opengl.GLES30;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;

import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.game.GameField;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.utils.CrashHandler;

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
 * {@link FieldTouchPicker}（射线拾取）、{@link FieldEditPreview}（设计时预览）、
 * {@link GLQuadBatch}（底层四边形绘制原语）、{@link FieldCardRenderer}（卡片绘制核心）。协作类经构造注入本视图，
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

        /** 点击场地中央 conti_act（待效果结算）堆叠：宿主弹出「效果处理」命令菜单
         *（对齐 gframe event_handler.cpp POSITION_HINT 悬停菜单） */
        void onContiActClick(float tapX, float tapY);
    }

    /**
     * 场内绘制的阶段按钮点击回调（当前阶段按钮仅作指示，不产生回调）
     */
    public interface OnPhaseButtonListener {
        void onPhaseNextClicked();

        void onPhaseEpClicked();
    }

    // === 堆叠区厚度（问题4）：卡组/墓地/除外/额外每张卡按真实 sequence 的线性 curZ
    //（GameFieldGeometry：0.01+0.01×seq，不封顶）绘制，堆顶 z = 0.01×张数，侧视厚度随张数
    // 线性增长（对齐 client_field.cpp：每张卡 Z 抬升 0.01）。旧实现把可见层封顶 14 层并重排
    // 均匀层高（0.012/层），导致 60/40/15 张视觉厚度几乎相同，故移除该封顶。

    // 选中手卡抬高量等手卡动画参数已随卡片绘制核心平移至 FieldCardRenderer
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

    // === 业务状态（公开 API 写入，GL 线程读取；部分供协作类包级直连）===
    volatile GameField field;
    private volatile ImageLoader imageLoader;
    volatile OnCardClickListener cardClickListener;
    volatile int highlightFieldMask = 0;
    volatile int selectedPlayer = -1;
    volatile int selectedLocation = -1;
    volatile int selectedSequence = -1;
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

    // FPS 统计——GL 线程逐帧累加，每满 1s 计算一次并经监听回调到主线程刷新 tv_fps
    private long fpsFrames;
    private long fpsWindowStartNs;
    private OnFpsListener onFpsListener;

    // === 协作类（按功能分栏委派，构造注入本视图，同包包级私有直连共享 GL 状态）===
    FieldCamera cam;
    FieldTextureManager tex;
    GLQuadBatch quad;
    FieldCardRenderer card;
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
     * 装配全部协作类（FieldGeometry 为纯静态工具无需实例）。所有协作类仅持有本视图引用、
     * 通过包级私有直连读取共享 GL 状态，构造顺序无依赖（真正使用时机在 onSurfaceCreated/onDrawFrame/
     * onTouchEvent/draw 之后）。
     */
    private void wire() {
        cam = new FieldCamera(this);
        tex = new FieldTextureManager(this);
        quad = new GLQuadBatch(this);
        card = new FieldCardRenderer(this);
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

    /** 帧率回调接口（每秒一次，已在主线程触发） */
    public interface OnFpsListener {
        void onFps(int fps);
    }

    /** 注册 FPS 监听，由 Activity 在布局绑定时调用 */
    public void setOnFpsListener(OnFpsListener listener) {
        this.onFpsListener = listener;
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
        // GL 渲染线程由 GLSurfaceView 内部创建、上下文重建时会新建，因此在首次进入回调时挂钩；
        // 本类 onDrawFrame 对绘制逐块 try/catch，仍漏出的异常（未保护调用/EGL 失败）靠此落盘
        CrashHandler.getInstance().hookThread(Thread.currentThread(), "游戏-GL渲染线程");
        GLES30.glClearColor(0f, 0f, 0f, 0f);
        GLES30.glEnable(GLES30.GL_DEPTH_TEST);
        GLES30.glDisable(GLES30.GL_CULL_FACE);

        quad.init();

        // 攻击弧 3D 逐顶点色程序与动态 VAO/VBO（CardOverlayRenderer 独占）
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

        // 帧率统计窗口，每累计满 1 秒把整窗口平均帧率回调到主线程
        if (fpsWindowStartNs == 0) fpsWindowStartNs = now;
        fpsFrames++;
        long fpsElapsed = now - fpsWindowStartNs;
        if (fpsElapsed >= 1_000_000_000L) {
            final int fps = (int) (fpsFrames * 1_000_000_000L / fpsElapsed);
            fpsFrames = 0;
            fpsWindowStartNs = now;
            final OnFpsListener l = onFpsListener;
            if (l != null) post(() -> l.onFps(fps));
        }

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
            // 手卡抬高动画：逐帧线性推进每张手卡的 handLiftAnim（目标=当前选中卡），
            // 使点击抬高/回落呈约 5 帧的线性缓动，对齐 drawing.cpp 手卡移动动画样式
            try {
                card.updateHandLift(dt);
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
        // 不可用格子对角交叉线（drawing.cpp L424-455 disabled_field，z=0.006 高于格子槽、低于卡）
        try {
            board.drawDisabledZones(f);
        } catch (Throwable ignored) {
        }
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
            card.drawFieldCards(f);
        } catch (Throwable ignored) {
        }
        try {
            overlays.drawFieldCardOverlays(f);
            overlays.drawActivatableDots(f);
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

    // ==================== 卡片绘制核心：已平移至 FieldCardRenderer（下列为协作类依赖的薄委托）====================
    
    /** 构建卡片模型矩阵：委托 {@link FieldCardRenderer#buildCardModel}（选择轮廓/可发动绿点与拾取共用姿态） */
    void buildCardModel(GameField.ClientCard c, float[] out) {
        card.buildCardModel(c, out);
    }
    
    /** 卡片正面是否朝向视点：委托 {@link FieldCardRenderer#isFrontFacing} */
    boolean isFrontFacing(float[] model) {
        return card.isFrontFacing(model);
    }
    
    /** 我方手卡后移量：委托 {@link FieldCardRenderer#handY}（拾取与绘制同源） */
    float handY(GameField.ClientCard c) {
        return card.handY(c);
    }
    
    /** 选中手卡抬升量：委托 {@link FieldCardRenderer#handLift} */
    float handLift(GameField.ClientCard c) {
        return card.handLift(c);
    }
    
    /** 选中手卡抬升的 Z 分量：委托 {@link FieldCardRenderer#handLiftZ} */
    float handLiftZ(GameField.ClientCard c) {
        return card.handLiftZ(c);
    }
    
    // ==================== 底层绘制原语与着色器工具：已平移至 GLQuadBatch（view.quad 直接调用）====================

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
