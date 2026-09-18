package cn.garymb.ygomobile.render;

import cn.garymb.ygomobile.game.GameField;

/**
 * 场地几何与坐标常量（与 GameField/旧版 getZoneRectLocalF 一致）。
 * <p>
 * 底板矩形与格子尺寸全部取自 {@link GameField}（materials.cpp 唯一真值）：卡片落点、格子、
 * 底板贴图网格共用 fx()/恒等 Y 同一仿射映射，三者必然完美重叠。X 镜像与区域/堆叠区取中心
 * 等纯几何函数集中于此，供 {@link GameFieldView} 门面、各 renderer、{@link FieldCamera}、
 * {@link FieldTouchPicker}、{@link FieldEditPreview} 共用，避免重复实现导致绘制/命中分叉。
 */
final class FieldGeometry {

    private FieldGeometry() {
    }

    static final float FIELD_CENTER_X = 3.95f;
    static final float FIELD_X_MIN = GameField.fieldBoardMinX();
    static final float FIELD_X_MAX = GameField.fieldBoardMaxX();
    static final float FIELD_Y_MIN = GameField.FIELD_TEX_Y_MIN;
    static final float FIELD_Y_MAX = GameField.FIELD_TEX_Y_MAX;
    // 卡片世界尺寸：严格 177:254 比例
    static final float CARD_W = 0.8f;
    static final float CARD_H = 0.8f * 254f / 177f;
    // 区域槽尺寸：与 GameField 格子同源（materials.cpp 1.1×1.2 / 0.8×1.2 经 fx 缩放）
    static final float ZONE_W = GameField.ZONE_W;
    static final float ZONE_H = GameField.ZONE_H;
    static final float PILE_W = GameField.PILE_W;
    static final float PILE_H = GameField.PILE_H;

    /**
     * 与 GameField.fx 同一映射：格子/卡片/底板共用，保证命中与绘制不偏
     */
    static float fx(float x) {
        return GameField.fx(x);
    }

    /**
     * 空间 X 镜像：引擎场地坐标约定 +X→屏幕右（layout_game_right：zone_p0_m0 最左、DECK 右列），
     * 而 +Y 侧相机天然把 +X 映射到屏幕左，故绘制时翻转所有几何的 X（拾取仍用真实坐标），
     * 复现桌面版方位。
     */
    static float mirrorX(float x) {
        return FIELD_X_MIN + FIELD_X_MAX - x;
    }

    static float[] zoneCenter(int player, int loc, int seq) {
        float[] r = GameField.getZoneRect(player, loc, seq);
        return r == null ? new float[]{FIELD_CENTER_X, 0f} : new float[]{r[0], r[1]};
    }

    static float[] pileCenter(int player, int loc) {
        float[] r = GameField.getPileRect(player, loc);
        return r == null ? null : new float[]{r[0], r[1]};
    }

    static boolean zoneContains(int player, int loc, int seq, float x, float y) {
        float[] c = zoneCenter(player, loc, seq);
        return Math.abs(x - c[0]) <= ZONE_W / 2f && Math.abs(y - c[1]) <= ZONE_H / 2f;
    }

    static int zoneBitPos(int player, int location, int sequence) {
        int base = (player == 0) ? 0 : 16;
        if (location == 0x04) return base + sequence;
        if (location == 0x08) {
            if (sequence < 6) return base + 8 + sequence;
            if (sequence == 6) return base + 14;
            if (sequence == 7) return base + 15;
        }
        return -1;
    }

    static int pendulumMode(GameField.ClientCard c) {
        if (c.location == 0x08 && (c.sequence == 6 || c.sequence == 7) && c.isFaceUp() && c.code != 0) {
            return c.sequence == 6 ? 1 : 2;
        }
        return 0;
    }

    static int pendulumScale(GameField.ClientCard c) {
        return Math.max(0, Math.min(13, c.sequence == 6 ? c.lScale : c.rScale));
    }

    /** 旋转图标自旋角度（度）：对齐 drawing.cpp act_rot.Z += 0.02/帧 @60fps ≈ 1.2 rad/s ≈ 68.8°/s */
    static float actSpinDegrees(long animTimeMs) {
        return (animTimeMs % 5240L) / 5240f * 360f;
    }

    // === 总攻击力 bar（问题5）：raw 场地坐标（x 经 fx 映射、y 直用）===
    // rule>3（MR4）：贴各自场地区(szone seq5)靠场地中心一侧的上沿之上，留 0.1 间隙，
    //   既不压场地区格子(0.05~1.15 × 1.4~2.6 / 6.75~7.85 × -2.6~-1.4)，
    //   也不压最左怪兽格(x≥1.2 / x≤6.7)；方向对齐 drawing.cpp vTotalAtkop 的中心朝向。
    // rule<=3（MR3）：无额外怪兽区，落在额外怪兽区位置（materials.cpp vTotalAtkmeT/opT）。
    // 供 FieldBoardRenderer（绘制 bar 底图）与 FieldHudRenderer（在 bar 上叠数字）共用同一布局。
    private static final float[] TOTAL_ATK_ME_MR4 = {0.15f, 0.6f, 1.05f, 1.3f};
    private static final float[] TOTAL_ATK_OP_MR4 = {6.85f, -1.3f, 7.75f, -0.6f};
    private static final float[] TOTAL_ATK_ME_MR3 = {2.5f, 0.95f, 3.5f, 1.65f};
    private static final float[] TOTAL_ATK_OP_MR3 = {4.45f, 0.4f, 5.45f, 1.1f};

    static float[] totalAtkRect(int p, boolean mr4) {
        if (p == 0) return mr4 ? TOTAL_ATK_ME_MR4 : TOTAL_ATK_ME_MR3;
        return mr4 ? TOTAL_ATK_OP_MR4 : TOTAL_ATK_OP_MR3;
    }
}
