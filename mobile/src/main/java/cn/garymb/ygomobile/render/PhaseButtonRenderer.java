package cn.garymb.ygomobile.render;

import android.opengl.GLES30;
import android.opengl.Matrix;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;

import java.util.HashMap;

/**
 * 阶段按钮布局 / 绘制 / 点击命中（GameFieldView「阶段按钮」分栏）。
 * <p>
 * 三个按钮（当前阶段 / 下一阶段 / EP）在屏幕像素正交空间绘制底板与标签纹理（平行屏幕、固定像素
 * 尺寸），锚定于 左额外怪兽区外侧 / 场地中轴 / 右额外怪兽区外侧，避免遮挡额外怪兽区。绘制
 * （GL 线程）与点击命中（主线程）共用同一确定性 {@link #phaseRects} 布局，保证所见即所点。
 * 复用门面屏幕绘制原语 {@link GameFieldView#drawScreenQuadColor}/{@link GameFieldView#drawScreenQuadTex}
 * 与 {@link FieldTextureManager} 纹理缓存；独占派生标签纹理键序列 {@code phaseLabelKeys}
 * （上下文重建时由门面 {@link #clearLabelKeys()} 清理，保持原行为）。
 */
final class PhaseButtonRenderer {

    private final GameFieldView view;

    // 阶段按钮屏幕尺寸（dp）：按钮平行屏幕，与两个额外怪兽区错开摆放（左/中/右三个锚点）
    private static final float PHASE_BTN_W_DP = 32f;
    private static final float PHASE_BTN_H_DP = 18f;
    private static final int PHASE_CURRENT = 0, PHASE_NEXT = 1, PHASE_EP = 2;

    // 阶段按钮标签文字纹理键（仅 GL 线程访问，负值递减，与卡图/场地/卡背键域不冲突）
    private final HashMap<String, Long> phaseLabelKeys = new HashMap<>();
    private long phaseLabelKeySeq = -1000L;

    PhaseButtonRenderer(GameFieldView view) {
        this.view = view;
    }

    /** 上下文（重新）创建：阶段标签纹理缓存全部失效，键序列复位（由门面 onSurfaceCreated 调用） */
    void clearLabelKeys() {
        phaseLabelKeys.clear();
    }

    /**
     * 阶段按钮布局（避免遮挡额外怪兽区）：
     * 当前阶段按钮 → 左侧额外怪兽区左缘外侧；下一阶段按钮 → 两个额外怪兽区正中（场地中轴）；
     * 结束阶段按钮 → 右侧额外怪兽区右缘外侧。按钮像素宽按中轴行像素比例折算世界半宽定位，
     * 并封顶于两额外怪兽区内侧空隙，确保不压任何场上格子。
     * 绘制（GL 线程）与点击命中（主线程）共用同一确定性布局，保证所见即所点
     */
    private float[][] phaseRects(boolean curVisible, boolean nextVisible, boolean epVisible) {
        float[][] out = new float[3][];
        float d = view.getResources().getDisplayMetrics().density;
        // 两个额外怪兽区（怪兽区 seq5/6，绘制空间已镜像：seq5 呈现在屏幕左、seq6 在屏幕右）
        float emzL = FieldGeometry.mirrorX(FieldGeometry.zoneCenter(0, 0x04, 5)[0]);
        float emzR = FieldGeometry.mirrorX(FieldGeometry.zoneCenter(0, 0x04, 6)[0]);
        // 中轴行像素比例：中轴左右各 0.5 世界单位采样
        float[] s0 = view.projectWorldPoint(FieldGeometry.FIELD_CENTER_X - 0.5f, 0f, 0f);
        float[] s1 = view.projectWorldPoint(FieldGeometry.FIELD_CENTER_X + 0.5f, 0f, 0f);
        if (s0 == null || s1 == null) return out;
        float pxPerWorld = Math.abs(s1[0] - s0[0]);
        if (pxPerWorld < 1e-3f) return out;
        // 两额外怪兽区内侧空隙的像素宽：下一阶段按钮宽度封顶于此
        float[] eL = view.projectWorldPoint(emzL - FieldGeometry.ZONE_W / 2f, 0f, 0f);
        float[] eR = view.projectWorldPoint(emzR + FieldGeometry.ZONE_W / 2f, 0f, 0f);
        float gapPx = (eL != null && eR != null) ? Math.abs(eL[0] - eR[0]) : Float.MAX_VALUE;
        float bw = Math.min(PHASE_BTN_W_DP * d, Math.max(24f, gapPx - 10f));
        float bh = PHASE_BTN_H_DP * d;
        float halfW = bw / pxPerWorld / 2f;
        float margin = 0.12f;
        if (curVisible) {
            // 左侧额外怪兽区左缘再向外（屏幕更左 = 绘制空间 x 更大）
            float ax = emzL + FieldGeometry.ZONE_W / 2f + margin + halfW;
            ax = Math.min(ax, FieldGeometry.FIELD_X_MAX - 0.1f - halfW);
            float[] s = view.projectWorldPoint(ax, 0f, 0f);
            if (s != null) out[PHASE_CURRENT] = new float[]{s[0], s[1], bw, bh};
        }
        if (nextVisible) {
            float[] s = view.projectWorldPoint(FieldGeometry.FIELD_CENTER_X, 0f, 0f);
            if (s != null) out[PHASE_NEXT] = new float[]{s[0], s[1], bw, bh};
        }
        if (epVisible) {
            // 右侧额外怪兽区右缘再向外（屏幕更右 = 绘制空间 x 更小）
            float ax = emzR - FieldGeometry.ZONE_W / 2f - margin - halfW;
            ax = Math.max(ax, FieldGeometry.FIELD_X_MIN + 0.1f + halfW);
            float[] s = view.projectWorldPoint(ax, 0f, 0f);
            if (s != null) out[PHASE_EP] = new float[]{s[0], s[1], bw, bh};
        }
        return out;
    }

    /**
     * 阶段按钮点击命中（优先于卡片/区域拾取）：当前阶段按钮无回调但吞掉点击，避免误触场地
     */
    boolean handlePhaseButtonTap(float x, float y) {
        GameFieldView.OnPhaseButtonListener l = view.phaseButtonListener;
        if (l == null) return false;
        String cur = view.phaseCurrentLabel, next = view.phaseNextLabel;
        boolean curV = view.phaseCurrentVisible && !cur.isEmpty();
        boolean nextV = !next.isEmpty();
        boolean epV = view.phaseEpVisible;
        if (!curV && !nextV && !epV) return false;
        float[] anchor = view.projectFieldMidline();
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
    void drawPhaseButtons() {
        int w = view.viewW, h = view.viewH;
        if (w <= 1 || h <= 1) return;
        String cur = view.phaseCurrentLabel, next = view.phaseNextLabel;
        boolean curV = view.phaseCurrentVisible && !cur.isEmpty();
        boolean nextV = !next.isEmpty();
        boolean epV = view.phaseEpVisible;
        if (!curV && !nextV && !epV) return;
        float[][] rects = phaseRects(curV, nextV, epV);
        if (rects[PHASE_CURRENT] == null && rects[PHASE_NEXT] == null && rects[PHASE_EP] == null)
            return;
        // 屏幕像素正交投影：y 向下与触摸坐标一致，quad 顶点布局下贴图无需翻转
        Matrix.orthoM(view.mOrthoVP, 0, 0f, w, h, 0f, -1f, 1f);
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
        boolean enabled = view.phaseButtonsEnabled;
        float a = enabled ? 1f : 0.4f;   // 禁用时整体变暗
        float cx = rect[0], cy = rect[1], bw = rect[2], bh = rect[3];
        view.drawScreenQuadColor(cx, cy, bw + 3f, bh + 3f, 0.04f, 0.08f, 0.12f, 0.92f * a);
        if (pressed) {
            view.drawScreenQuadColor(cx, cy, bw, bh, 0.15f, 0.22f, 0.32f, 0.94f * a);
        } else if (enabled) {
            view.drawScreenQuadColor(cx, cy, bw, bh, 0.30f, 0.44f, 0.58f, 0.90f);
        } else {
            view.drawScreenQuadColor(cx, cy, bw, bh, 0.16f, 0.24f, 0.32f, 0.90f);
        }
        int tex = obtainPhaseLabelTexture(label);
        if (tex > 0) {
            // 标签位图固定 256×80：按 3.2:1 铺展，宽度封顶按钮内宽（短文字两侧留透明区）
            float tw = Math.min(bh * 3.2f, bw * 0.96f);
            view.drawScreenQuadTex(cx, cy, tw, tw / 3.2f, tex, a);
        }
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
                view.tex.offerUpload(new FieldTextureManager.PendingUpload(key, makePhaseLabelBitmap(text), true));
            } catch (Throwable ignored) {
            }
        }
        Integer id = view.tex.texCache().get(key);
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
}
