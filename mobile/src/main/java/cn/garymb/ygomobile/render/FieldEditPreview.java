package cn.garymb.ygomobile.render;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

import java.util.Locale;

/**
 * 设计时预览（XML 布局编辑器，{@code isInEditMode}，GameFieldView「设计时预览」分栏）。
 * <p>
 * 布局编辑器下无法启动 GL/EGL（android.opengl 为 stub），这里用 Canvas 绘制等效预览：相机与运行期
 * {@link FieldCamera#updateCamera()} 共用同一套 {@link FieldCamera#solveCamera(int, int)} 解算
 * （纯 java.lang.Math 还原外参/内参），区域/堆叠区/手卡坐标与运行期 {@link FieldGeometry} 完全同源，
 * 参数全部取门面 XML 属性值，编辑器中改属性即时可见（所见即所得）。门面 {@code draw(Canvas)} 在
 * edit mode 时委托本类并保留 try/catch 兜底。
 */
final class FieldEditPreview {

    private final GameFieldView view;

    FieldEditPreview(GameFieldView view) {
        this.view = view;
    }

    /**
     * 设计时预览绘制入口（由门面 draw(Canvas) 在 isInEditMode 时调用）
     */
    void drawEditPreview(Canvas canvas) {
        int w = view.getWidth(), h = view.getHeight();
        if (w <= 1 || h <= 1) return;
        float density = view.getResources().getDisplayMetrics().density;
        // 设计时底色（运行期为透明 GL 面透出背景；预览用实色底保证可读性）
        canvas.drawColor(0xFF0B1118);
        EditCamera cam = computeEditCamera(w, h);
        if (cam == null) return;

        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeWidth(Math.max(1f, density));

        // 场地底板（运行期 drawFieldBoard 的兜底底色同色系）
        float boardCX = (FieldGeometry.FIELD_X_MIN + FieldGeometry.FIELD_X_MAX) / 2f;
        float boardW = FieldGeometry.FIELD_X_MAX - FieldGeometry.FIELD_X_MIN, boardH = FieldGeometry.FIELD_Y_MAX - FieldGeometry.FIELD_Y_MIN;
        fill.setColor(0xFF14212C);
        drawWorldQuad(canvas, fill, cam, w, h, boardCX, 0f, 0f, boardW, boardH);
        line.setColor(0xFF2E4A5E);
        drawWorldQuad(canvas, line, cam, w, h, boardCX, 0f, 0f, boardW, boardH);

        if (view.previewShowZones) {
            // 区域槽位：与运行期 drawZoneSlots 同一套区域遍历（每方 怪兽7 + 魔陷6 + 堆叠区4）
            fill.setColor(0x3300C8F0);
            line.setColor(0x8800C8F0);
            for (int p = 0; p < 2; p++) {
                for (int i = 0; i < 7; i++) {
                    float[] c = FieldGeometry.zoneCenter(p, 0x04, i);
                    drawWorldQuad(canvas, fill, cam, w, h, c[0], c[1], 0f, FieldGeometry.ZONE_W, FieldGeometry.ZONE_H);
                    drawWorldQuad(canvas, line, cam, w, h, c[0], c[1], 0f, FieldGeometry.ZONE_W, FieldGeometry.ZONE_H);
                }
                for (int i = 0; i <= 5; i++) {
                    float[] c = FieldGeometry.zoneCenter(p, 0x08, i);
                    drawWorldQuad(canvas, fill, cam, w, h, c[0], c[1], 0f, FieldGeometry.ZONE_W, FieldGeometry.ZONE_H);
                    drawWorldQuad(canvas, line, cam, w, h, c[0], c[1], 0f, FieldGeometry.ZONE_W, FieldGeometry.ZONE_H);
                }
                for (int loc : new int[]{0x01, 0x10, 0x20, 0x40}) {
                    float[] c = FieldGeometry.pileCenter(p, loc);
                    if (c == null) continue;
                    drawWorldQuad(canvas, fill, cam, w, h, c[0], c[1], 0f, FieldGeometry.PILE_W, FieldGeometry.PILE_H);
                    drawWorldQuad(canvas, line, cam, w, h, c[0], c[1], 0f, FieldGeometry.PILE_W, FieldGeometry.PILE_H);
                }
            }
        }

        if (view.previewShowLabels) {
            Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
            text.setColor(0xCCFFFFFF);
            text.setTextSize(8f * density);
            text.setTextAlign(Paint.Align.CENTER);
            drawLabel(canvas, text, cam, w, h, FieldGeometry.pileCenter(0, 0x01), "卡组");
            drawLabel(canvas, text, cam, w, h, FieldGeometry.pileCenter(0, 0x10), "墓地");
            drawLabel(canvas, text, cam, w, h, FieldGeometry.pileCenter(0, 0x20), "除外");
            drawLabel(canvas, text, cam, w, h, FieldGeometry.pileCenter(0, 0x40), "额外");
            drawLabel(canvas, text, cam, w, h, FieldGeometry.pileCenter(1, 0x01), "卡组");
            drawLabel(canvas, text, cam, w, h, FieldGeometry.pileCenter(1, 0x10), "墓地");
            drawLabel(canvas, text, cam, w, h, FieldGeometry.pileCenter(1, 0x20), "除外");
            drawLabel(canvas, text, cam, w, h, FieldGeometry.pileCenter(1, 0x40), "额外");
            drawLabel(canvas, text, cam, w, h, FieldGeometry.zoneCenter(0, 0x08, 5), "场地");
            drawLabel(canvas, text, cam, w, h, FieldGeometry.zoneCenter(1, 0x08, 5), "场地");
        }

        if (view.previewShowHand) {
            // 示例手卡：每方 5 张，坐标同运行期 getCardLocation(LOCATION_HAND)，
            // 我方行位用 solveCamera 解算出的动态后移量（与运行期完全一致）
            float spacing = 0.95f;
            for (int i = 0; i < 5; i++) {
                drawHandCard(canvas, cam, w, h, 3.95f - spacing * 2f + i * spacing,
                        FieldCamera.SELF_HAND_Y - cam.selfHandShift, FieldCamera.HAND_Z, 0xFF33516E);
                drawHandCard(canvas, cam, w, h, 3.95f + spacing * 2f - i * spacing,
                        FieldCamera.OPP_HAND_Y, FieldCamera.HAND_Z, 0xFF5C4433);
            }
        }

        // 参数读数：调整 XML 属性时便于对照
        Paint info = new Paint(Paint.ANTI_ALIAS_FLAG);
        info.setColor(0xAAFFFFFF);
        info.setTextSize(9f * density);
        canvas.drawText(String.format(Locale.US,
                        "GameFieldView 设计预览  俯仰角=%.0f° 视点距离=%.2f 场地倍率=%.2f 手卡后移=%.3f(解算) 视高=%.1f°",
                        view.cameraElevationDeg, view.cameraDistance, view.fieldZoom, cam.selfHandShift,
                        Math.toDegrees(2.0 * Math.atan(cam.halfH))),
                6f * density, h - 6f * density, info);
    }

    /**
     * 预览手卡：运行期手卡为平行屏幕的 billboard，此处按投影中心画 177:254 比例圆角矩形，
     * 尺寸取卡片世界宽度 CARD_W 的投影像素宽估算
     */
    private void drawHandCard(Canvas canvas, EditCamera cam, int w, int h,
                              float x, float y, float z, int color) {
        float[] c = projectEdit(cam, w, h, FieldGeometry.mirrorX(x), y, z);
        float[] e = projectEdit(cam, w, h, FieldGeometry.mirrorX(x + FieldGeometry.CARD_W / 2f), y, z);
        if (c == null || e == null) return;
        float halfW = Math.max(2f, Math.abs(e[0] - c[0]));
        float halfH = halfW * FieldGeometry.CARD_H / FieldGeometry.CARD_W;
        float radius = halfW * 0.12f;
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        fill.setColor(color);
        canvas.drawRoundRect(c[0] - halfW, c[1] - halfH, c[0] + halfW, c[1] + halfH, radius, radius, fill);
        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        line.setStyle(Paint.Style.STROKE);
        line.setColor(0xAAFFFFFF);
        line.setStrokeWidth(Math.max(1f, view.getResources().getDisplayMetrics().density * 0.75f));
        canvas.drawRoundRect(c[0] - halfW, c[1] - halfH, c[0] + halfW, c[1] + halfH, radius, radius, line);
    }

    private void drawLabel(Canvas canvas, Paint text, EditCamera cam, int w, int h,
                           float[] center, String label) {
        if (center == null) return;
        float[] s = projectEdit(cam, w, h, FieldGeometry.mirrorX(center[0]), center[1], 0.02f);
        if (s == null) return;
        canvas.drawText(label, s[0], s[1] + text.getTextSize() / 3f, text);
    }

    /**
     * 世界矩形（中心+宽高，先做与运行期一致的 X 镜像）→ 投影四角 → Canvas 四边形
     */
    private void drawWorldQuad(Canvas canvas, Paint paint, EditCamera cam, int w, int h,
                               float cx, float cy, float z, float qw, float qh) {
        float x0 = FieldGeometry.mirrorX(cx - qw / 2f), x1 = FieldGeometry.mirrorX(cx + qw / 2f);
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
     * 设计时预览相机：与运行期 updateCamera 共用 {@link FieldCamera#solveCamera(int, int)}
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
        FieldCamera.CameraSolve s = view.cam.solveCamera(w, h);
        if (!s.valid) return null;
        float aspect = (float) w / h;
        EditCamera cam = new EditCamera();
        cam.eyeX = FieldCamera.CAM_X;
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
}
