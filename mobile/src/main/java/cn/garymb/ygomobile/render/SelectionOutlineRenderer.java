package cn.garymb.ygomobile.render;

import android.opengl.Matrix;

import java.util.List;

import cn.garymb.ygomobile.game.GameField;

/**
 * 可选格子高亮虚线 / 卡片选择轮廓蚂蚁线（GameFieldView「可选格子高亮」「卡片选择轮廓」分栏）。
 * <p>
 * 逐行对齐 gframe drawing.cpp DrawSelectionLine + game.cpp linePattern/stippleMask（16bit
 * 0x0f0f 蚂蚁线）：四角投影到屏幕取像素边长，沿周长走像素，亮段换算回世界/卡片局部空间画粗线段，
 * patternCursor 跨边累积、phase 随时间推进 → 行进蚂蚁线。复用门面底层绘制原语、
 * {@link GameFieldView#buildCardModel}（与卡片绘制共用姿态）、{@link FieldGeometry} 几何。
 */
final class SelectionOutlineRenderer {

    private final GameFieldView view;

    // === 可选格子高亮：环绕格子的虚线行进动画（drawing.cpp DrawSelectionLine + game.cpp linePattern/stippleMask）===
    private static final int STIPPLE_MASK = 0x0f0f;
    private static final float OUTLINE_PX = 2.5f;
    private static final float MARCH_PX_PER_SEC = 48f;

    // === 卡片选择轮廓：黄色蚂蚁线（对齐 drawing.cpp DrawSelectionLine + DrawCard L638-643）===
    private final float[] mOutlineModel = new float[16];
    private final float[] mOutlineTmp = new float[16];
    private final float[] mDashLocal = new float[16];
    private final float[] mDashWorld = new float[16];

    SelectionOutlineRenderer(GameFieldView view) {
        this.view = view;
    }

    void drawHighlights() {
        int mask = view.highlightFieldMask;
        if (mask == 0) return;
        float phase = view.animTimeMs * 0.001f * MARCH_PX_PER_SEC;
        for (int p = 0; p < 2; p++) {
            float r = p == 0 ? 0f : 1f;
            float g = p == 0 ? 1f : 0f;
            float b = p == 0 ? 1f : 0f;
            for (int i = 0; i < 7; i++) {
                if ((mask & (1 << FieldGeometry.zoneBitPos(p, 0x04, i))) != 0)
                    drawZoneMarching(p, 0x04, i, phase, r, g, b);
            }
            for (int i = 0; i < 8; i++) {
                int bit = FieldGeometry.zoneBitPos(p, 0x08, i);
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
        float cx = FieldGeometry.mirrorX(rect[0]), cy = rect[1];
        float hw = rect[2] / 2f, hh = rect[3] / 2f;
        float x0 = cx - hw, x1 = cx + hw, y0 = cy - hh, y1 = cy + hh;
        // 角点顺序对齐 C++ v[0..3]，边序对齐 edgeStart/edgeEnd
        float[] qx = {x0, x1, x0, x1};
        float[] qy = {y0, y0, y1, y1};
        int[] es = {0, 1, 3, 2};
        int[] ee = {1, 3, 2, 0};
        float[] sx = new float[4], sy = new float[4];
        for (int i = 0; i < 4; i++) {
            float[] s = view.projectWorldPoint(qx[i], qy[i], 0.03f);
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
        view.drawFlatQuad((x0 + x1) / 2f, (y0 + y1) / 2f, 0.03f, w, h, r, g, b, 0.95f);
    }

    /**
     * 场上/手牌直接选择模式：为每张 is_selectable 的卡片绘制黄色轮廓线，
     * 未选中(is_selected=false)为虚线行进、已选中为实线（对齐 gframe stipple=!is_selected）
     */
    void drawCardSelectOutlines(GameField f) {
        List<GameField.ClientCard> list = f.selectableCards;
        if (list == null || list.isEmpty()) return;
        float phase = view.animTimeMs * 0.001f * MARCH_PX_PER_SEC;
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
        view.buildCardModel(c, mOutlineModel);
        float sx = (float) Math.sqrt(mOutlineModel[0] * mOutlineModel[0]
                + mOutlineModel[1] * mOutlineModel[1] + mOutlineModel[2] * mOutlineModel[2]);
        float sy = (float) Math.sqrt(mOutlineModel[4] * mOutlineModel[4]
                + mOutlineModel[5] * mOutlineModel[5] + mOutlineModel[6] * mOutlineModel[6]);
        if (sx < 1e-5f || sy < 1e-5f) return;
        // 轮廓线始终抬到朝向相机的一侧，否则盖放卡（背面朝相机）的轮廓会被卡背遮住
        float outZ = view.isFrontFacing(mOutlineModel) ? 0.002f : -0.002f;
        float[] lx = {-0.5f, 0.5f, -0.5f, 0.5f};
        float[] ly = {-0.5f, -0.5f, 0.5f, 0.5f};
        int[] es = {0, 1, 3, 2};
        int[] ee = {1, 3, 2, 0};
        float[] px = new float[4], py = new float[4];
        float[] v = new float[4];
        for (int i = 0; i < 4; i++) {
            v[0] = lx[i]; v[1] = ly[i]; v[2] = 0f; v[3] = 1f;
            Matrix.multiplyMV(mOutlineTmp, 0, mOutlineModel, 0, v, 0);
            float[] s = view.projectWorldPoint(mOutlineTmp[0], mOutlineTmp[1], mOutlineTmp[2]);
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
                    view.drawQuadColor(mDashWorld, 1f, 1f, 0f, 0.95f);
                }
                cursor = runEnd;
            }
            patternCursor = (patternCursor + screenLen) % 16f;
        }
    }
}
