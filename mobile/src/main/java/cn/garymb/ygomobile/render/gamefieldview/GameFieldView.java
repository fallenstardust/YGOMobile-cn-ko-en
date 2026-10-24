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
 * XML 可调参数（declare-styleable GameFieldView）：field_camera_elevation（仰角/倾斜度）/
 * field_camera_distance（相机距离）/ field_zoom（等比总缩放）/ field_width（横向拉伸）/ field_length（纵向拉伸）/
 * field_height（上下平移）/ field_hand_shift 与预览开关。XML 显式声明的值即运行期正式值，所见即所得；
 * 布局编辑器（isInEditMode）下无法启动 GL，改由 Canvas 绘制等效透视预览。
 */
public class GameFieldView extends GameFieldViewBase {

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
    protected void wire() {
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
        // 冻结兜底：即便渲染请求被错误调度（CONTINUOUSLY 切换竞态），也不推进动画、不清屏重绘，
        // 让双缓冲上一帧（步骤 N）继续显示；须在更新 lastFrameNs/animationSpeed 之前早退
        if (renderHold) return;
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

    /**
     * 请求屏幕最高刷新率显示模式（高刷屏跑满 90/120/144Hz 的前提）
     */
    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        FieldCamera.applyHighRefreshRate(this);
    }
}
