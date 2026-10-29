package cn.garymb.ygomobile.render.gamefieldview;
import cn.garymb.ygomobile.render.card.CardOverlayRenderer;
import cn.garymb.ygomobile.render.field.FieldBoardRenderer;
import cn.garymb.ygomobile.render.field.FieldCardRenderer;
import cn.garymb.ygomobile.render.field.FieldGeometry;
import cn.garymb.ygomobile.render.field.FieldHudRenderer;
import cn.garymb.ygomobile.render.field.FieldTextureManager;
import cn.garymb.ygomobile.render.field.GLQuadBatch;
import cn.garymb.ygomobile.render.card.SelectionOutlineRenderer;

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
import cn.garymb.ygomobile.game.gamefield.GameField;
import cn.garymb.ygomobile.game.gamefield.ClientCard;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.utils.YGOUtil;
import cn.garymb.ygomobile.utils.CrashHandler;


/**
 * Base layer of the split inheritance chain (GameFieldViewBase <- GameFieldView):
 * owns the GL view state, the nested listener interfaces, the collaborator
 * handles, every public setter/getter API, the touch-dispatch forwarders and the
 * thin card-drawing delegates. The concrete view constructors, the collaborator
 * wiring (which pass {@code this} to the renderers) and the GLSurfaceView.Renderer
 * frame loop plus the high-refresh-rate attach hook live in the leaf class.
 */
abstract class GameFieldViewBase extends GLSurfaceView implements GLSurfaceView.Renderer {

    protected static final String TAG = "GameFieldView";

    public interface OnCardClickListener {
        void onCardClick(int player, int location, int sequence, float tapX, float tapY);

        void onZoneClick(int player, int location, int sequence, float tapX, float tapY);

        /** 点击未命中卡片（空白处/场内阶段按钮/无卡区域）：宿主据此隐藏状态悬浮标签 */
        void onFieldBlankClick(float tapX, float tapY);

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
    // 场地视觉倍率（等比总缩放）：1.0=全部可交互格子/堆叠区恰好完整入镜；<1 裁掉边缘换取更大的卡片；>1 留更多边距
    protected static final float FIELD_ZOOM = 1.0f;
    // 宽度（横向拉伸）/ 长度（纵向拉伸）：叠加在 field_zoom 上的各向独立乘数，1.0=不额外拉伸（默认画面与旧一致）
    protected static final float FIELD_WIDTH = 1.0f;
    protected static final float FIELD_LENGTH = 1.0f;
    // 上下高度（整体垂直平移）：叠加在离轴量 c 上的偏移 tan，0=不平移
    protected static final float FIELD_HEIGHT = 0f;
    // 我方手卡手动微调偏置基准（正值=再向场地外侧后移）；基准后移量由 solveCamera 按俯仰角动态解算
    protected static final float HAND_SELF_Y_SHIFT = 0f;

    // === 相机视角参数上下界与默认值（public：设置页与控制器读写）===
    public static final float DEFAULT_CAMERA_ELEVATION = 52f;
    public static final float MIN_CAMERA_ELEVATION = 35f;
    public static final float MAX_CAMERA_ELEVATION = 75f;
    /**
     * 横屏 HUD 纵向名义占地（dp）：血条行下限 50dp + 提示行 40dp + 聊天输入行 35dp。
     * <p>两处共用同一真值：{@link FieldCamera} 用它换算全面屏的仰角自适应抬升量（只按设备
     * 纵向 dp 高度算名义挤占度，不读实测内缩），GameTopInfoManager 用它算横屏 HUD 等比内缩
     * 系数——后者正是为放大卡片而回收实测内缩，必须与仰角解耦，否则回收净空会把仰角跌回
     * 设置值（用户规格：全面屏倾斜度保持现状）。
     * <p><b>2026-10-18 更新</b>：tv_hint_message 从固定 30dp→wrap_content+10sp，提示栏占地减小；
     * 原预算 125dp= 血条 50+ 提示 30+ 输入框 30+ 冗余 15，现调整为 115dp（减 10dp）以回收更多
     * 纵向空间给决斗场取景，使卡片与场地在 GameFieldView 中最大化显示（对齐 C++ libygomobile.so）。
     */
    public static final float LANDSCAPE_HUD_NOMINAL_DP = 115f;
    protected static final String PREF_CAMERA_ELEVATION = "camera_elevation";
    // 视点到注视点距离默认值（与俯仰角共同决定相机位置）
    protected static final float CAMERA_DISTANCE = 7.6f;

    // === XML 可调参数（declare-styleable GameFieldView，常量兜底默认值）===
    float cameraDistance = CAMERA_DISTANCE;
    float fieldZoom = FIELD_ZOOM;
    // 宽度（横向拉伸乘数）/ 长度（纵向拉伸乘数）/ 上下高度（垂直平移量），由 FieldCamera 与 FieldEditPreview 直读
    float fieldWidth = FIELD_WIDTH;
    float fieldLength = FIELD_LENGTH;
    float fieldHeight = FIELD_HEIGHT;
    float handSelfYShift = HAND_SELF_Y_SHIFT;
    // XML 是否显式声明了俯仰角（声明时优先于游戏内保存的设置）
    protected boolean elevationFromXml = false;
    // 设计时预览开关（仅 isInEditMode 生效）
    boolean previewShowZones = true;
    boolean previewShowHand = true;
    boolean previewShowLabels = true;
    float cameraElevationDeg = DEFAULT_CAMERA_ELEVATION;
    protected volatile boolean cameraDirty = false;

    // === 业务状态（公开 API 写入，GL 线程读取；部分供协作类包级直连）===
    public volatile GameField field;
    protected volatile ImageLoader imageLoader;
    volatile OnCardClickListener cardClickListener;
    public volatile int highlightFieldMask = 0;
    public volatile int selectedPlayer = -1;
    public volatile int selectedLocation = -1;
    public volatile int selectedSequence = -1;
    // 动画倍率：1=原速，2=2 倍速（时间驱动，帧率无关）
    protected volatile float animSpeedMultiplier = 1f;

    // === 阶段按钮（场内绘制：主线程写状态，GL 线程读取绘制/命中；由 PhaseButtonRenderer 读取）===
    volatile OnPhaseButtonListener phaseButtonListener;
    volatile boolean phaseCurrentVisible = false;
    volatile String phaseCurrentLabel = "";
    volatile String phaseNextLabel = "";
    volatile boolean phaseEpVisible = false;
    // 模态对话框（是/否、卡片选择/确认、命令菜单）显示期间禁用三个阶段按钮
    volatile boolean phaseButtonsEnabled = true;

    // === 矩阵 scratch：mModel/mMVP/mModelTmp 供卡片核心与底层原语；mOrthoVP 供 HUD/阶段按钮屏幕绘制 ===
    public final float[] mMVP = new float[16];
    public final float[] mModel = new float[16];
    public final float[] mModelTmp = new float[16];
    public final float[] mOrthoVP = new float[16];

    public volatile int viewW = 1, viewH = 1;
    // 顶部内缩像素（问题1）：由控制器传入 gameTopInfo 实测高度，FieldCamera 据此把对方手卡压到其下
    volatile float topInsetPx = 0f;
    // 底部内缩像素：由控制器传入聊天输入框（et_chat_input）顶边距视图底边的高度，
    // 横屏作为纵向取景的下锚线（我方手卡底缘不得低于此），使决斗场整体下移而不被输入框遮挡
    volatile float bottomInsetPx = 0f;
    // 相机重建通知（GL 线程重建相机后，覆盖层需重新锚定阶段按钮位置）
    volatile Runnable onCameraChangedListener;

    protected long lastFrameNs = 0;
    public volatile long animTimeMs = 0;

    // FPS 统计——GL 线程逐帧累加，每满 1s 计算一次并经监听回调到主线程刷新 tv_fps
    protected long fpsFrames;
    protected long fpsWindowStartNs;
    protected OnFpsListener onFpsListener;

    // === 协作类（按功能分栏委派，构造注入本视图，同包包级私有直连共享 GL 状态）===
    public FieldCamera cam;
    public FieldTextureManager tex;
    public GLQuadBatch quad;
    public FieldCardRenderer card;
    FieldBoardRenderer board;
    CardOverlayRenderer overlays;
    FieldHudRenderer hud;
    SelectionOutlineRenderer outlines;
    PhaseButtonRenderer phase;
    FieldTouchPicker picker;
    FieldEditPreview preview;


    /**
     * Forwarding constructors: the leaf view is inflated by Android with a
     * Context (or Context + AttributeSet); the collaborator wiring runs in the
     * leaf subclass where {@code this} has the concrete GameFieldView type.
     */
    protected GameFieldViewBase(Context context) {
        super(context);
    }

    protected GameFieldViewBase(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * 解析 XML 布局参数（declare-styleable GameFieldView）：
     * XML 显式声明的值同时作为设计时预览与运行期的正式值，参数调校所见即所得
     */
    protected void readXmlAttributes(Context context, AttributeSet attrs) {
        if (attrs == null) return;
        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.GameFieldView);
        try {
            cameraDistance = ta.getFloat(R.styleable.GameFieldView_field_camera_distance, CAMERA_DISTANCE);
            fieldZoom = ta.getFloat(R.styleable.GameFieldView_field_zoom, FIELD_ZOOM);
            fieldWidth = ta.getFloat(R.styleable.GameFieldView_field_width, FIELD_WIDTH);
            fieldLength = ta.getFloat(R.styleable.GameFieldView_field_length, FIELD_LENGTH);
            fieldHeight = ta.getFloat(R.styleable.GameFieldView_field_height, FIELD_HEIGHT);
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

    protected void init() {
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

    /**
     * 取消「点击卡片」产生的全部选中视觉（用户规格：点其它按钮/聊天输入框/其它弹窗等
     * 卡片以外的位置即取消选中）：除金色呼吸外框与手卡抬升所需的选中三元组外，一并解除
     * 点击时联动的装备/对象/连锁对象图标标记（{@code applyShowMarks} 留下的红色光晕），
     * 否则只清外框而图标残留，看起来仍像处于被选中态。不触及通讯选择态
     * （field.selectedCards 与蚂蚁线），那些由各自的应答会话管理。
     */
    public void clearClickSelection() {
        clearSelection();
        applyShowMarks(null);
    }

    /** 当前选中（移动端语义=点击，对位桌面悬停 hovered_card）并联动展示关联图标的卡片 */
    volatile ClientCard markedCard;

    /**
     * event_handler.cpp 悬停触发 SetShowMark 的点击版：先对上一张解除装备/对象/连锁对象
     * 图标标记，再对新点击卡置位；由 FieldTouchPicker 在卡片命中处调用
     */
    void applyShowMarks(ClientCard card) {
        GameField f = field;
        if (f == null) return;
        ClientCard old = markedCard;
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
    public int[] selectedFieldZone() {
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
     * 逆向倒放渲染冻结标志：{@code true} 期间丢弃一切 {@link #requestRender()}（含经 {@code invalidate()}
     * 与各 setter 触发的渲染请求）并在 {@link #onDrawFrame} 入口早退，使画面真正停留在步骤 N 的最后一帧。
     * 仅靠 {@code RENDERMODE_WHEN_DIRTY} 不足以冻结——该模式只关闭自动逐帧渲染，不拦截显式 requestRender，
     * 而重算 N-1 权威态途中派发的阶段/LP/卡数消息会经实况回调触发 setPhaseDisplay 等 → requestRender →
     * 把逼近 N-1 的中间态画上屏（用户所见的「从头重播」），随后落点的逆向补间被淹没。由主线程经
     * {@code GameFieldController.setRenderHold} 设置，GL 线程读取，故 volatile。
     */
    protected volatile boolean renderHold;

    /**
     * 回放「上一步」逆向倒放的渲染冻结：{@code hold=true} 置冻结标志并切 RENDERMODE_WHEN_DIRTY（GL 不再
     * 主动刷新，且所有显式 requestRender 被丢弃，从 MSG_START 重算 N-1 权威态期间画面冻结在步骤 N，
     * 无中间态闪烁）；{@code hold=false} 清标志并恢复 RENDERMODE_CONTINUOUSLY + requestRender（逐帧驱动
     * 逆向补间）。setRenderMode 线程安全；由主线程 {@code GameFieldController.setRenderHold} 转发调用。
     */
    public void setRenderHold(boolean hold) {
        if (hold) {
            renderHold = true;
            setRenderMode(RENDERMODE_WHEN_DIRTY);
        } else {
            renderHold = false;
            setRenderMode(RENDERMODE_CONTINUOUSLY);
            requestRender();
        }
    }

    /**
     * 冻结期丢弃渲染请求：GLSurfaceView 默认 requestRender 会调度一帧 onDrawFrame，逆向倒放重算期间
     * 若放行则中间态上屏。renderHold=true 时直接空转（super.requestRender 不调度），画面保持步骤 N。
     */
    @Override
    public void requestRender() {
        if (renderHold) return;
        super.requestRender();
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
     * 顶部内缩像素（上锚线）：传入顶部 HUD（血条行 + 其下聊天/提示行）的实测底边高，
     * 相机以离轴视锥把底板远缘与对方手卡上缘压到该高度之下，确保不遮挡顶部信息条与
     * tv_hint_message（本 View 为 setZOrderOnTop 的 GL 曲面，不透内容会盖住其下的普通 View）。
     * 调用方必须传入与提示显隐无关的恒定量（提示栏恒占位 + 固定高），否则决斗场会因提示
     * 的显示/隐藏而反复重解取景（用户反馈的界面晃动）。横竖屏均生效；随屏幕旋转 / 折叠屏 /
     * HUD 高度变化再次调用即自适应重建相机。
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
     * 底部内缩像素：传入聊天输入框顶边距决斗场视图底边的高度（可另加少量间隙），
     * 相机以此为下锚线，使我方手卡底缘落在输入框上方、纵向富余留在顶部（决斗场下移）。
     * 输入框高度变化 / 旋转 / 折叠屏切换都会再次调用而自适应重建相机；横竖屏均生效。
     */
    public void setBottomInsetPx(float px) {
        float v = Math.max(0f, px);
        if (Math.abs(v - bottomInsetPx) < 0.5f) return;
        bottomInsetPx = v;
        cameraDirty = true;
        requestRender();
        Runnable l = onCameraChangedListener;
        if (l != null) post(l);
    }

    /**
     * 对方手卡屏幕上缘的屏幕 y（像素，相对本 View 左上角）：只读查询原语，供需要贴对方
     * 手卡定位的覆盖层使用（聊天/提示行已不再据此平移，提示栏恒在 gameTopInfo 布局位置）。
     * 相机未就绪时回退为当前顶部内缩量。
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
     * 阶段按钮按下态入口：控制器在 ACTION_DOWN 时调用，命中的按钮即刻换按下图（sbutton_p），
     * 与后续点击命中同一判定，使按下反馈与实际响应一致
     */
    public void dispatchPhaseButtonPressStart(float x, float y) {
        if (phase != null) phase.handlePhaseButtonPress(x, y);
    }

    /** 阶段按钮按下态结束入口（ACTION_UP/CANCEL） */
    public void dispatchPhaseButtonPressEnd() {
        if (phase != null) phase.clearPhaseButtonPress();
    }

    /**
     * 兜底消费：手势识别已迁移至 GameFieldViewController 的 OnTouchListener，
     * 此处仅防止事件穿透到下层控件
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return true;
    }

    // ==================== 卡片绘制核心：已平移至 FieldCardRenderer（下列为协作类依赖的薄委托）====================
    
    /** 构建卡片模型矩阵：委托 {@link FieldCardRenderer#buildCardModel}（选择轮廓/可发动绿点与拾取共用姿态） */
    void buildCardModel(ClientCard c, float[] out) {
        card.buildCardModel(c, out);
    }
    
    /** 卡片正面是否朝向视点：委托 {@link FieldCardRenderer#isFrontFacing} */
    boolean isFrontFacing(float[] model) {
        return card.isFrontFacing(model);
    }
    
    /** 我方手卡后移量：委托 {@link FieldCardRenderer#handY}（拾取与绘制同源） */
    float handY(ClientCard c) {
        return card.handY(c);
    }
    
    /** 选中手卡抬升量：委托 {@link FieldCardRenderer#handLift} */
    float handLift(ClientCard c) {
        return card.handLift(c);
    }
    
    /** 选中手卡抬升的 Z 分量：委托 {@link FieldCardRenderer#handLiftZ} */
    float handLiftZ(ClientCard c) {
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
            p.setColor(YGOUtil.c(R.color.preview_error_salmon));
            p.setTextSize(12f * getResources().getDisplayMetrics().density);
            canvas.drawText("GameFieldView preview error: " + t, 8f, 16f, p);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        tex.shutdown();
        super.onDetachedFromWindow();
    }
}
