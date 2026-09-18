package cn.garymb.ygomobile.render;

import android.opengl.Matrix;

import java.util.List;

import cn.garymb.ygomobile.game.GameField;

/**
 * 触摸拾取（射线反投影，GameFieldView「触摸拾取」分栏）。
 * <p>
 * 手势识别（GestureDetector）已迁移至 GameFieldViewController，其识别出手势后经门面
 * {@code dispatchTap}/{@code dispatchLongPress}/{@code dispatchLongPressEnd} 回调本类完成拾取与
 * 业务回调分发，拾取逻辑与旧 Canvas 版一致。绘制做了 X 镜像：命中点须镜像还原到真实场地坐标
 * 后再与卡数据比较。射线反投影用 {@link FieldCamera#pickInvVPSnapshot()} 的视逆投影快照，
 * 手卡 billboard 命中沿用相机 up 轴抬升量；阶段按钮命中优先转发达 {@link PhaseButtonRenderer}。
 */
final class FieldTouchPicker {

    private final GameFieldView view;

    FieldTouchPicker(GameFieldView view) {
        this.view = view;
    }

    /**
     * 点按手势统一入口：先试场内阶段按钮命中，再走高亮选区 / 卡片拾取
     */
    void handleTap(float x, float y) {
        // 场内阶段按钮命中优先（当前阶段按钮也吞掉点击）
        if (view.phase.handlePhaseButtonTap(x, y)) return;
        GameFieldView.OnCardClickListener listener = view.cardClickListener;
        GameField f = view.field;
        if (listener == null || f == null) return;
        float[] ray = buildRay(x, y);
        if (ray == null) return;

        try {
            // 高亮选区优先（与 Canvas 版 handleTap 语义一致）
            int mask = view.highlightFieldMask;
            if (mask != 0) {
                float[] g = new float[2];
                if (planeHit(ray, 0.02f, g)) {
                    // 绘制做了 X 镜像，命中点镜像还原后才能与真实 zone 坐标比较
                    g[0] = FieldGeometry.mirrorX(g[0]);
                    for (int p = 0; p < 2; p++) {
                        for (int loc : new int[]{0x04, 0x08}) {
                            int max = (loc == 0x04) ? GameField.MAX_MONSTER_ZONE : GameField.MAX_SPELL_ZONE;
                            for (int i = 0; i < max; i++) {
                                if (!FieldGeometry.zoneContains(p, loc, i, g[0], g[1])) continue;
                                int bit = FieldGeometry.zoneBitPos(p, loc, i);
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
                view.setSelectedCard(hit[0], hit[1], hit[2]);
                listener.onCardClick(hit[0], hit[1], hit[2], x, y);
            } else {
                view.clearSelection();
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * 长按手势统一入口
     */
    void handleLongPress(float x, float y) {
        GameFieldView.OnCardClickListener listener = view.cardClickListener;
        GameField f = view.field;
        if (listener == null || f == null) return;
        float[] ray = buildRay(x, y);
        if (ray == null) return;
        try {
            int[] hit = hitCard(ray, f);
            if (hit != null) {
                listener.onFieldLongPress(hit[0], hit[1], hit[2], x, y);
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * 长按结束入口：由控制器在 ACTION_UP/CANCEL 时调用，转发给业务方隐藏状态标签
     */
    void dispatchLongPressEnd() {
        GameFieldView.OnCardClickListener listener = view.cardClickListener;
        if (listener != null) {
            listener.onFieldLongPressEnd();
        }
    }

    /**
     * 长按气泡标签锚定用：把指定卡在屏幕上绘制的实际四边形投影为包围盒，
     * 返回 {centerX, topY, bottomY}（相对视图左上角，像素）；相机未就绪 / 取卡失败返回 null。
     * 复用与 drawCard 同一套卡片姿态（含手卡 billboard、对方卡 180° 翻转），
     * projectWorldPoint 已线程安全，可在 UI 线程调用。
     */
    float[] getCardScreenBounds(int player, int location, int sequence) {
        GameField f = view.field;
        if (f == null) return null;
        GameField.ClientCard c;
        try {
            c = f.getCard(player, location, sequence);
        } catch (Throwable e) {
            c = null;
        }
        if (c == null) return null;
        float[] model = new float[16];
        buildCardModelForPicking(c, model);
        float[][] corners = {
                {-0.5f, -0.5f}, {0.5f, -0.5f}, {-0.5f, 0.5f}, {0.5f, 0.5f},
        };
        float minX = Float.MAX_VALUE, maxX = -Float.MAX_VALUE;
        float minY = Float.MAX_VALUE, maxY = -Float.MAX_VALUE;
        for (float[] k : corners) {
            float wx = model[0] * k[0] + model[4] * k[1] + model[12];
            float wy = model[1] * k[0] + model[5] * k[1] + model[13];
            float wz = model[2] * k[0] + model[6] * k[1] + model[14];
            float[] s = view.projectWorldPoint(wx, wy, wz);
            if (s == null) return null;
            if (s[0] < minX) minX = s[0];
            if (s[0] > maxX) maxX = s[0];
            if (s[1] < minY) minY = s[1];
            if (s[1] > maxY) maxY = s[1];
        }
        return new float[]{(minX + maxX) / 2f, minY, maxY};
    }

    /**
     * buildCardModel 的 UI 线程安全副本：不复用 GL 线程 scratch（mModelTmp），
     * 并在 camLock 下取 mCamRot 快照，供 getCardScreenBounds 复现卡片绘制姿态。
     */
    private void buildCardModelForPicking(GameField.ClientCard c, float[] out) {
        Matrix.setIdentityM(out, 0);
        if (c.location == 0x02) {
            float[] cam = new float[16];
            synchronized (view.cam.camLock) {
                System.arraycopy(view.cam.mCamRot, 0, cam, 0, 16);
            }
            Matrix.translateM(out, 0, FieldGeometry.mirrorX(c.curX),
                    view.handY(c) + cam[5] * view.handLift(c), c.curZ + view.handLiftZ(c));
            float[] tmp = new float[16];
            Matrix.multiplyMM(tmp, 0, out, 0, cam, 0);
            System.arraycopy(tmp, 0, out, 0, 16);
            Matrix.scaleM(out, 0, FieldGeometry.CARD_W, FieldGeometry.CARD_H, 1f);
        } else {
            Matrix.translateM(out, 0, FieldGeometry.mirrorX(c.curX), c.curY, c.curZ);
            Matrix.rotateM(out, 0, (float) Math.toDegrees(-c.curRotY), 0f, 1f, 0f);
            Matrix.rotateM(out, 0, (float) Math.toDegrees(c.curRotX), 1f, 0f, 0f);
            Matrix.rotateM(out, 0, (float) Math.toDegrees(-c.curRotZ), 0f, 0f, 1f);
            Matrix.scaleM(out, 0, FieldGeometry.CARD_W, FieldGeometry.CARD_H, 1f);
        }
    }

    private float[] buildRay(float sx, float sy) {
        float[] inv = view.cam.pickInvVPSnapshot();
        int w = view.viewW;
        int h = view.viewH;
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

    /**
     * 命中顺序与 Canvas 版一致：每方 怪兽/魔陷区 → 手牌 → 堆叠区
     */
    private int[] hitCard(float[] ray, GameField f) {
        try {
            float[] g = new float[2];
            boolean gOk = planeHit(ray, 0.02f, g);
            // 绘制做了 X 镜像：命中点镜像还原到真实场地坐标后再与卡数据比较
            if (gOk) g[0] = FieldGeometry.mirrorX(g[0]);

            float[] cam = view.cam.mCamRot;
            for (int p = 0; p < 2; p++) {
                if (gOk) {
                    for (int loc : new int[]{0x04, 0x08}) {
                        int max = (loc == 0x04) ? GameField.MAX_MONSTER_ZONE : GameField.MAX_SPELL_ZONE;
                        for (int i = 0; i < max; i++) {
                            if (FieldGeometry.zoneContains(p, loc, i, g[0], g[1]) && f.getCard(p, loc, i) != null) {
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
                    float lift = view.handLift(c);
                    float[] hz = new float[2];
                    if (!planeHitY(ray, view.handY(c) + cam[5] * lift, hz)) continue;
                    hz[0] = FieldGeometry.mirrorX(hz[0]);
                    if (Math.abs(hz[0] - c.curX) <= 0.45f
                            && Math.abs(hz[1] - (c.curZ + cam[6] * lift)) <= 0.75f) {
                        return new int[]{p, 0x02, i};
                    }
                }
                if (gOk) {
                    for (int loc : new int[]{0x01, 0x40, 0x10, 0x20}) {
                        float[] c = FieldGeometry.pileCenter(p, loc);
                        if (c == null) continue;
                        if (Math.abs(g[0] - c[0]) <= FieldGeometry.PILE_W / 2f && Math.abs(g[1] - c[1]) <= FieldGeometry.PILE_H / 2f) {
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
}
