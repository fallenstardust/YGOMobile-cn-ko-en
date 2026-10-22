package cn.garymb.ygomobile.render.gamefieldview;
import cn.garymb.ygomobile.render.field.FieldGeometry;
import cn.garymb.ygomobile.render.field.FieldTextureManager;

import android.opengl.GLES30;
import android.opengl.Matrix;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;

import androidx.core.content.ContextCompat;

import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.utils.YGOUtil;

import java.util.HashMap;

/** 阶段按钮布局 / 绘制 / 点击命中（GameFieldView「阶段按钮」分栏）：三个按钮（当前阶段 / 下一阶段 / EP）在屏幕像素正交空间绘制 9-patch 底板与标签纹理（平行屏幕），锚定于左额外怪兽区外侧 / 场地中轴 / 右额外怪兽区外侧，尺寸以同排怪兽格的实测屏幕宽为基准等比缩放（随 GameFieldView 实际宽高与相机取景同步放大缩小，不再受 density 单一因素支配）；绘制（GL 线程）与点击命中（主线程）共用同一确定性 phaseRects 布局保证所见即所点，复用屏幕绘制原语 GLQuadBatch#drawScreenQuadColor/drawScreenQuadTex（经门面 view.quad）与 FieldTextureManager 纹理缓存，并独占派生纹理键序列（标签 phaseLabelKeys、底板 phaseBgKeys），上下文重建时由 clearLabelKeys() 全部清理。 */
final class PhaseButtonRenderer {

    private final GameFieldView view;

    // 按钮宽高比例沿用原 37:21 视觉比例（旧实现高度写死 21dp×density，宽度写死 37dp×density）
    private static final float PHASE_BTN_RATIO = 37f / 21f;
    /** 宽度基准 = 同排怪兽格实测屏幕宽的比例：旧实现取 37dp × density，而 density 与 GameFieldView 实际宽高无关，高分辨率／大屏上格子被相机放大而按钮仍固定像素（用户反馈「没有随着 gamefieldview 实际宽高放大」的根因），改为按投影像素解算，手机上与原 37dp 等效、屏幕越大按钮越大 */
    private static final float PHASE_BTN_W_ZONE = 0.86f;
    /** 最左（当前阶段）与最右（EP）按钮相对额外怪兽区外缘再向外平移的距离（世界单位，原值） */
    private static final float PHASE_BTN_MARGIN = 0.24f;
    /** 两额外怪兽区内侧间隙的下限像素：下一阶段按钮宽度封顶于此间隙（原实现值） */
    private static final float PHASE_BTN_GAP_FLOOR_PX = 24f;
    /** 底板纹理按宽度 3dp 一档量化：同一档复用同一张 9-patch 位图，尺寸可缩放但缓存不无限增长 */
    private static final float PHASE_BG_STEP_DP = 3f;
    private static final int PHASE_CURRENT = 0, PHASE_NEXT = 1, PHASE_EP = 2;

    // 阶段按钮标签文字纹理键（仅 GL 线程访问，负值递减，与卡图/场地/卡背键域不冲突）
    private final HashMap<String, Long> phaseLabelKeys = new HashMap<>();
    private long phaseLabelKeySeq = -1000L;
    // 9-patch 底板纹理键（同样仅 GL 线程访问，独立负值键域）
    private final HashMap<String, Long> phaseBgKeys = new HashMap<>();
    private long phaseBgKeySeq = -5000L;
    // 按下态（主线程 ACTION_DOWN/UP 写、GL 线程读）：命中的按钮序号，-1 为无
    private volatile int pressedIdx = -1;

    PhaseButtonRenderer(GameFieldView view) {
        this.view = view;
    }

    /** 上下文（重新）创建：阶段纹理（标签 + 底板）缓存全部失效，键序列复位（由门面 onSurfaceCreated 调用） */
    void clearLabelKeys() {
        phaseLabelKeys.clear();
        phaseBgKeys.clear();
    }

    /** 阶段按钮布局（锚点与历史实现一致）：当前阶段→左侧额外怪兽区左缘外侧、下一阶段→两额外怪兽区正中（场地中轴）、结束阶段→右额外怪兽区右缘外侧；尺寸仍以同排怪兽格实测屏幕宽为基准（PHASE_BTN_W_ZONE），宽度封顶于两额外区内侧间隙减 10px，高度按 37:21 由宽度反推——手机上与原 37×21dp 等效而 GameFieldView 变大时同比变大；绘制与命中共用同一确定性布局保证所见即所点 */
    private float[][] phaseRects(boolean curVisible, boolean nextVisible, boolean epVisible) {
        float[][] out = new float[3][];
        // 两个额外怪兽区（怪兽区 seq5/6，绘制空间已镜像：seq5 呈现在屏幕左、seq6 在屏幕右），与阶段按钮同处 Y=0 一行（GameFieldGeometry.mzoneCY 对 s>=5 固定 0）
        float emzL = FieldGeometry.mirrorX(FieldGeometry.zoneCenter(0, 0x04, 5)[0]);
        float emzR = FieldGeometry.mirrorX(FieldGeometry.zoneCenter(0, 0x04, 6)[0]);
        float halfZoneW = FieldGeometry.ZONE_W / 2f;
        // 中轴行像素比例：中轴左右各 0.5 世界单位采样
        float[] s0 = view.projectWorldPoint(FieldGeometry.FIELD_CENTER_X - 0.5f, 0f, 0f);
        float[] s1 = view.projectWorldPoint(FieldGeometry.FIELD_CENTER_X + 0.5f, 0f, 0f);
        if (s0 == null || s1 == null) return out;
        float pxPerWorld = Math.abs(s1[0] - s0[0]);
        if (pxPerWorld < 1e-3f) return out;
        // 两额外怪兽区内侧间隙的像素宽：下一阶段按钮宽度封顶于此；格宽用作尺寸缩放基准
        float[] lIn = view.projectWorldPoint(emzL - halfZoneW, 0f, 0f);
        float[] lOut = view.projectWorldPoint(emzL + halfZoneW, 0f, 0f);
        float[] rIn = view.projectWorldPoint(emzR + halfZoneW, 0f, 0f);
        float[] rOut = view.projectWorldPoint(emzR - halfZoneW, 0f, 0f);
        float gapPx = (lIn != null && rIn != null) ? Math.abs(lIn[0] - rIn[0]) : Float.MAX_VALUE;
        float zonePxW = 0f;
        if (lIn != null && lOut != null) zonePxW = Math.abs(lOut[0] - lIn[0]);
        if (rIn != null && rOut != null) zonePxW = Math.max(zonePxW, Math.abs(rIn[0] - rOut[0]));
        if (zonePxW < 1f) return out;
        float bw = Math.min(zonePxW * PHASE_BTN_W_ZONE,
                Math.max(PHASE_BTN_GAP_FLOOR_PX, gapPx - 10f));
        if (bw < 1f) return out;
        float bh = bw / PHASE_BTN_RATIO;
        float halfW = bw / pxPerWorld / 2f;
        // 最左（当前阶段）与最右（EP）按钮向外平移的间距（世界单位，原值）
        float margin = PHASE_BTN_MARGIN;
        if (curVisible) {
            // 左侧额外怪兽区左缘再向外（屏幕更左 = 绘制空间 x 更大）
            float ax = emzL + halfZoneW + margin + halfW;
            ax = Math.min(ax, FieldGeometry.FIELD_X_MAX - 0.02f - halfW);
            float[] s = view.projectWorldPoint(ax, 0f, 0f);
            if (s != null) out[PHASE_CURRENT] = new float[]{s[0], s[1], bw, bh};
        }
        if (nextVisible) {
            // 严格居中于两额外怪兽区内侧缘的屏幕像素中点：场地世界对称中心 FIELD_CENTER_X(3.95) 与两额外区内侧的屏幕中点在透视投影下并不完全重合（一侧略偏），沿用世界中心投影会表现为 BP 按钮偏离间隙正中（用户反馈 BP 偏右），改用实测两内侧像素中点作锚、与上方 gapPx 同源，保证视觉精确居中
            if (lIn != null && rIn != null) {
                float cx = (lIn[0] + rIn[0]) / 2f;
                float cy = (lIn[1] + rIn[1]) / 2f;
                out[PHASE_NEXT] = new float[]{cx, cy, bw, bh};
            } else {
                float[] s = view.projectWorldPoint(FieldGeometry.FIELD_CENTER_X, 0f, 0f);
                if (s != null) out[PHASE_NEXT] = new float[]{s[0], s[1], bw, bh};
            }
        }
        if (epVisible) {
            // 右侧额外怪兽区右缘再向外（屏幕更右 = 绘制空间 x 更小）
            float ax = emzR - halfZoneW - margin - halfW;
            ax = Math.max(ax, FieldGeometry.FIELD_X_MIN + 0.02f + halfW);
            float[] s = view.projectWorldPoint(ax, 0f, 0f);
            if (s != null) out[PHASE_EP] = new float[]{s[0], s[1], bw, bh};
        }
        return out;
    }

    /** 阶段按钮点击命中（优先于卡片/区域拾取）：当前阶段按钮无回调但吞掉点击，避免误触场地 */
    boolean handlePhaseButtonTap(float x, float y) {
        int idx = phaseIndexAt(x, y);
        if (idx < 0) return false;
        GameFieldView.OnPhaseButtonListener l = view.phaseButtonListener;
        if (l != null) {
            if (idx == PHASE_NEXT) l.onPhaseNextClicked();
            else if (idx == PHASE_EP) l.onPhaseEpClicked();
        }
        return true;
    }

    /** ACTION_DOWN：命中哪个阶段按钮就让它进入按下态（底板换 9-patch 按下图），与 handlePhaseButtonTap 走同一 phaseIndexAt 判定，按下反馈与点击结果一致 */
    boolean handlePhaseButtonPress(float x, float y) {
        pressedIdx = phaseIndexAt(x, y);
        return pressedIdx >= 0;
    }

    /** ACTION_UP / CANCEL：退出按下态 */
    void clearPhaseButtonPress() {
        pressedIdx = -1;
    }

    /** 命中的阶段按钮序号（无命中返回 -1）：绘制与命中共用 phaseRects，所见即所点 */
    private int phaseIndexAt(float x, float y) {
        String cur = view.phaseCurrentLabel, next = view.phaseNextLabel;
        boolean curV = view.phaseCurrentVisible && !cur.isEmpty();
        boolean nextV = !next.isEmpty();
        boolean epV = view.phaseEpVisible;
        if (!curV && !nextV && !epV) return -1;
        float[][] rects = phaseRects(curV, nextV, epV);
        if (rects[PHASE_NEXT] != null && inPhaseRect(rects[PHASE_NEXT], x, y)) return PHASE_NEXT;
        if (rects[PHASE_EP] != null && inPhaseRect(rects[PHASE_EP], x, y)) return PHASE_EP;
        if (rects[PHASE_CURRENT] != null && inPhaseRect(rects[PHASE_CURRENT], x, y))
            return PHASE_CURRENT;
        return -1;
    }

    private static boolean inPhaseRect(float[] rect, float x, float y) {
        return Math.abs(x - rect[0]) <= rect[2] / 2f && Math.abs(y - rect[1]) <= rect[3] / 2f;
    }

    /** 阶段按钮绘制：三个按钮分别锚定左额外怪兽区外侧 / 场地中轴 / 右额外怪兽区外侧，在屏幕像素正交空间内绘制 9-patch 底板与文字纹理（平行屏幕、尺寸随屏幕缩放），置于全部场地内容之上（关闭深度测试） */
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
        drawPhaseButton(rects[PHASE_CURRENT], cur, PHASE_CURRENT);
        drawPhaseButton(rects[PHASE_NEXT], next, PHASE_NEXT);
        drawPhaseButton(rects[PHASE_EP], "EP", PHASE_EP);
        GLES30.glEnable(GLES30.GL_DEPTH_TEST);
    }

    /** 单个阶段按钮：9-patch 底板 + 标签文字；底板最左（当前阶段）恒用按下图 sbutton_p，中/右按 button3_bg 的 selector 语义取图（手指按下 sbutton_p，否则 sbutton），纹理尚未上传时退回描边 + 纯色底板，按钮不出现空帧 */
    private void drawPhaseButton(float[] rect, String label, int idx) {
        if (rect == null || label == null || label.isEmpty()) return;
        boolean enabled = view.phaseButtonsEnabled;
        float a = enabled ? 1f : 0.4f;   // 禁用时整体变暗
        float cx = rect[0], cy = rect[1], bw = rect[2], bh = rect[3];
        boolean pressed = idx == PHASE_CURRENT || pressedIdx == idx;
        int bg = obtainPhaseBgTexture(pressed ? R.drawable.sbutton_p : R.drawable.sbutton, bw);
        if (bg > 0) {
            view.quad.drawScreenQuadTex(cx, cy, bw, bh, bg, a);
        } else {
            view.quad.drawScreenQuadColor(cx, cy, bw + 3f, bh + 3f, 0.04f, 0.08f, 0.12f, 0.92f * a);
            if (pressed) {
                view.quad.drawScreenQuadColor(cx, cy, bw, bh, 0.15f, 0.22f, 0.32f, 0.94f * a);
            } else if (enabled) {
                view.quad.drawScreenQuadColor(cx, cy, bw, bh, 0.30f, 0.44f, 0.58f, 0.90f);
            } else {
                view.quad.drawScreenQuadColor(cx, cy, bw, bh, 0.16f, 0.24f, 0.32f, 0.90f);
            }
        }
        int tex = obtainPhaseLabelTexture(label);
        if (tex > 0) {
            // 标签位图固定 256×80：按 3.2:1 铺展，宽度封顶按钮内宽（短文字两侧留透明区）
            float tw = Math.min(bh * 3.2f, bw * 0.96f);
            view.quad.drawScreenQuadTex(cx, cy, tw, tw / 3.2f, tex, a);
        }
    }

    /** 9-patch 按钮底板纹理（仅 GL 线程调用）：按宽度量化档位缓存，首次出现时把 Drawable 绘制到同尺寸 ARGB_8888 位图入队上传（.9 拉伸块保证边角不随缩放变形），上传未完成前返回 -1 由 drawPhaseButton 兜底纯色底板 */
    private int obtainPhaseBgTexture(int resId, float bwPx) {
        float d = view.getResources().getDisplayMetrics().density;
        int bucket = Math.max(1, Math.round(bwPx / (PHASE_BG_STEP_DP * d)));
        String key = resId + "b" + bucket;
        Long k = phaseBgKeys.get(key);
        if (k == null) {
            k = phaseBgKeySeq--;
            phaseBgKeys.put(key, k);
            int w = Math.max(8, Math.round(bucket * PHASE_BG_STEP_DP * d));
            int h = Math.max(4, Math.round(w / PHASE_BTN_RATIO));
            try {
                view.tex.offerUpload(new FieldTextureManager.PendingUpload(k,
                        makeButtonBgBitmap(resId, w, h), true));
            } catch (Throwable ignored) {
            }
        }
        Integer id = view.tex.texCache().get(k);
        return id != null ? id : -1;
    }

    /** 把 9-patch 按钮底图按目标像素尺寸绘制成位图（四角与边框随拉伸块保持原像素） */
    private Bitmap makeButtonBgBitmap(int resId, int w, int h) {
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        try {
            Drawable dr = ContextCompat.getDrawable(view.getContext(), resId);
            if (dr != null) {
                dr.setBounds(0, 0, w, h);
                dr.draw(cv);
            }
        } catch (Throwable ignored) {
        }
        return bmp;
    }

    /** 阶段标签文字纹理（仅 GL 线程调用）：首次出现时 Canvas 生成位图入队、下一帧 drainUploads 上传，上传完成前仅绘制底板兜底 */
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
        p.setColor(YGOUtil.c(R.color.white));
        p.setTextAlign(Paint.Align.CENTER);
        p.setShadowLayer(3f, 1f, 1f, YGOUtil.c(R.color.black_c0));
        cv.drawText(text, w / 2f, h / 2f - (p.ascent() + p.descent()) / 2f, p);
        return bmp;
    }
}
