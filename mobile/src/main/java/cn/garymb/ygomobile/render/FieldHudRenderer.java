package cn.garymb.ygomobile.render;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.opengl.GLES30;
import android.opengl.Matrix;

import java.util.HashMap;
import java.util.List;

import cn.garymb.ygomobile.game.GameField;

/**
 * 屏幕像素正交 HUD 通道绘制（GameFieldView「区域数字 / 多色自适应宽度 HUD 文字」分栏）：
 * 堆叠区数量、总攻击力数字、场上怪兽 ATK/DEF/link 值、等级/阶级、灵摆刻度值。
 * <p>
 * 与阶段按钮同一套正交投影（关深度测试），落点由 {@link GameField} 格子几何 + 相机投影解算，
 * 数值取自 client_card.cpp 计算串，逐行对齐 drawing.cpp DrawStatus/DrawCard。独占派生文字纹理
 * 键序列 {@code numLabelKeys}（单色数字）/{@code statLabelKeys}（多色分段），复用
 * {@link FieldTextureManager} 缓存与门面屏幕绘制原语。
 */
final class FieldHudRenderer {

    private final GameFieldView view;

    /** CardType.Xyz = 0x800000（阶级玫红）、CardType.Tuner = 0x1000（等级黄），对齐 drawing.cpp DrawStatus */
    private static final int TYPE_XYZ = 0x800000;
    private static final int TYPE_TUNER = 0x1000;

    // === 多色自适应宽度 HUD 文字（ATK/DEF、等级/阶级、灵摆刻度）===
    // 与 makeNumberBitmap 的区别：位图宽度随文本增长而非缩放字号，
    // 保证「1500/2000」这类长串的字高与堆叠数量数字完全一致。
    private static final float STAT_TEXT_SIZE = 44f;
    private static final int STAT_BMP_H = 64;
    private static final int STAT_PAD_X = 6;
    private static final Paint STAT_MEASURE_PAINT = makeStatMeasurePaint();

    // 文字水平对齐方式（drawScreenTextAligned）
    private static final int ALIGN_CENTER = 0;
    private static final int ALIGN_LEFT = 1;
    private static final int ALIGN_RIGHT = 2;
    /** 攻守/等级数字尺寸 */
    private static final float STAT_SIZE_SCALE = 0.85f;
    private static final float STAT_SNUG_PX = 2f;

    // 屏幕空间数字文字纹理键（区域计数 / 总攻击力数字共用，键含颜色；负值递减独立键域）
    private final HashMap<String, Long> numLabelKeys = new HashMap<>();
    private long numLabelKeySeq = -100000L;
    private final HashMap<String, Long> statLabelKeys = new HashMap<>();
    private long statLabelKeySeq = -50000000L;

    FieldHudRenderer(GameFieldView view) {
        this.view = view;
    }

    /**
     * 屏幕像素正交空间绘制区域堆叠数量与总攻击力数字，天然垂直于观看视线（与阶段按钮同方案）。
     * 区域数字贴在卡组/额外/墓地/除外格子「靠近摄像头的底边」（+y 侧缘）外侧；总攻击力数字叠在 bar 中心。
     */
    void drawFieldNumbers(GameField f) {
        int w = view.viewW, h = view.viewH;
        if (w <= 1 || h <= 1 || f == null) return;
        Matrix.orthoM(view.mOrthoVP, 0, 0f, w, h, 0f, -1f, 1f);
        GLES30.glDisable(GLES30.GL_DEPTH_TEST);
        for (int p = 0; p < 2; p++) {
            for (int loc : new int[]{0x01, 0x40, 0x10, 0x20}) {
                int cnt = f.getCardCount(p, loc);
                if (cnt <= 0) continue;
                float[] r = GameField.getPileRect(p, loc);
                if (r == null) continue;
                float nearY = r[1] + r[3] / 2f;              // 靠摄像头的 +y 侧缘
                float farY = r[1] - r[3] / 2f;
                float[] top = view.projectWorldPoint(FieldGeometry.mirrorX(r[0]), nearY, 0.02f);
                float[] bot = view.projectWorldPoint(FieldGeometry.mirrorX(r[0]), farY, 0.02f);
                if (top == null || bot == null) continue;
                float pilePx = Math.abs(top[1] - bot[1]);
                float hpx = Math.max(10f, Math.min(40f, pilePx * 0.30f));
                float[] anchor = view.projectWorldPoint(FieldGeometry.mirrorX(r[0]), nearY + 0.14f, 0.02f);
                drawScreenNumber(anchor, pileCountLabel(f, p, loc, cnt), 0xFFFFFF00, hpx);
            }
        }
        boolean mr4 = f.dInfo.duelRule >= 4;
        for (int p = 0; p < 2; p++) {
            if (f.dInfo.totalAttack[p] <= 0) continue;
            float[] rc = FieldGeometry.totalAtkRect(p, mr4);
            float cxw = (FieldGeometry.fx(rc[0]) + FieldGeometry.fx(rc[2])) / 2f;
            float cyw = (rc[1] + rc[3]) / 2f;
            float[] a = view.projectWorldPoint(FieldGeometry.mirrorX(cxw), cyw, 0.012f);
            float[] b = view.projectWorldPoint(FieldGeometry.mirrorX(cxw), rc[3], 0.012f);
            float[] c2 = view.projectWorldPoint(FieldGeometry.mirrorX(cxw), rc[1], 0.012f);
            float hpx = 14f;
            if (b != null && c2 != null) hpx = Math.max(10f, Math.min(40f, Math.abs(b[1] - c2[1]) * 0.8f));
            drawScreenNumber(a, String.valueOf(f.dInfo.totalAttack[p]), f.dInfo.totalAttackColor[p], hpx);
        }
        GLES30.glEnable(GLES30.GL_DEPTH_TEST);
    }

    /**
     * 堆叠区数量文字：额外卡组与除外区在总数后追加括号区分表侧/里侧。
     * - 额外卡组(0x40)：显示「总数(表侧)」——表侧数=extraPCount[p]（对齐 drawing.cpp L1130-1131
     *   extra.size() 与 (extra_p_count)）；例：表侧10、里侧5、总数15 → "15(10)"。
     * - 除外区(0x20)：显示「总数(里侧)」——里侧数=总数-表侧数，表侧数=removed 中 isFaceUp() 计数；
     *   例：表侧10、里侧5、总数15 → "15(5)"。
     * 括号内数量为 0 时只显示总数（全区同朝向，无需区分）；卡组(0x01)/墓地(0x10) 维持总数。
     */
    private static String pileCountLabel(GameField f, int p, int loc, int total) {
        if (loc == 0x40) {
            int faceUp = clampCount(f.extraPCount[p], total);
            return faceUp > 0 ? total + "(" + faceUp + ")" : String.valueOf(total);
        }
        if (loc == 0x20) {
            int faceUp = clampCount(countFaceUp(f, p, 0x20), total);
            int faceDown = total - faceUp;
            return faceDown > 0 ? total + "(" + faceDown + ")" : String.valueOf(total);
        }
        return String.valueOf(total);
    }

    /** 统计某区列表中表侧（正面朝上）卡片数，对齐 ClientCard.isFaceUp 的 position 位判定 */
    private static int countFaceUp(GameField f, int p, int loc) {
        try {
            List<GameField.ClientCard> list = f.players[p].getLocationList(loc);
            if (list == null) return 0;
            int n = 0;
            for (GameField.ClientCard c : list) {
                if (c != null && c.isFaceUp()) n++;
            }
            return n;
        } catch (Throwable t) {
            return 0;
        }
    }

    private static int clampCount(int v, int max) {
        if (v < 0) return 0;
        return Math.min(v, max);
    }

    /**
     * 场上卡片数值文字（正交 HUD 通道，关深度测试，与 drawFieldNumbers 同一套投影）：
     * - 表侧怪兽：攻击表示 → 左下攻击力/右下守备力；守备表示 → 左下守备力/右下攻击力；
     *   连接怪兽 → 左下攻击力/右下 link 值（连接怪兽 defString 已为 "-"）。
     * - 灵摆刻度：rule>=4 时魔陷区最左(seq0)左上角显示左刻度、最右(seq4)右上角显示右刻度；
     *   rule<4 时用 seq6/seq7（对齐 drawing.cpp DrawCard 的灵摆刻度分支）。
     * 移动中的卡片跳过（对齐 DrawCard is_moving 提前返回），字号随格子投影像素高度自适应。
     */
    void drawFieldCardTexts(GameField f) {
        int w = view.viewW, h = view.viewH;
        if (w <= 1 || h <= 1 || f == null) return;
        Matrix.orthoM(view.mOrthoVP, 0, 0f, w, h, 0f, -1f, 1f);
        GLES30.glDisable(GLES30.GL_DEPTH_TEST);
        boolean mr4 = f.dInfo.duelRule >= 4;
        for (int p = 0; p < 2; p++) {
            List<GameField.ClientCard> mz = f.players[p].monsterZone;
            for (int seq = 0; seq < mz.size(); seq++) {
                GameField.ClientCard c;
                try { c = mz.get(seq); } catch (Throwable e) { continue; }
                if (c == null || !c.isFaceUp() || c.is_moving) continue;
                drawMonsterStatTexts(p, seq, c);
            }
            drawPendulumScaleText(f, p, mr4 ? 0 : 6, true);
            drawPendulumScaleText(f, p, mr4 ? 4 : 7, false);
        }
        GLES30.glEnable(GLES30.GL_DEPTH_TEST);
    }

    private void drawMonsterStatTexts(int p, int seq, GameField.ClientCard c) {
        float[] r = GameField.getZoneRect(p, 0x04, seq);
        if (r == null) return;
        float cx = r[0], cy = r[1], hw = r[2] / 2f, hh = r[3] / 2f;
        // 四角 + 中心投影（world +y 靠相机 → 屏幕更下）
        float[] nearL = view.projectWorldPoint(FieldGeometry.mirrorX(cx - hw), cy + hh, 0.02f);
        float[] nearR = view.projectWorldPoint(FieldGeometry.mirrorX(cx + hw), cy + hh, 0.02f);
        float[] farL = view.projectWorldPoint(FieldGeometry.mirrorX(cx - hw), cy - hh, 0.02f);
        float[] farR = view.projectWorldPoint(FieldGeometry.mirrorX(cx + hw), cy - hh, 0.02f);
        float[] center = view.projectWorldPoint(FieldGeometry.mirrorX(cx), cy, 0.02f);
        if (nearL == null || nearR == null || farL == null || farR == null || center == null) return;
        // 字号对齐堆叠数量（0.30 系数）后整体再缩小一号（攻守/等级数字比原来小 1 号）
        float cardHpx = Math.abs(center[1] - nearL[1]) * 2f;
        float hpx = Math.max(10f, Math.min(40f, cardHpx * 0.30f)) * STAT_SIZE_SCALE;
        boolean ours = (p == 0);

        // ATK/DEF（连接怪兽为 ATK/L‑n）紧贴「视角相对下沿」外侧显示——
        // 我方数字上沿贴卡片下边缘（屏幕下缘外侧），对方数字底边贴卡片下边缘（屏幕上缘外侧）。
        // 本轮再朝卡片方向贴近 STAT_SNUG_PX 像素（我方攻守上抬、对方攻守下降）。
        float[] eL = ours ? nearL : farL;
        float[] eR = ours ? nearR : farR;
        float edgeX = (eL[0] + eR[0]) / 2f;
        float edgeY = (eL[1] + eR[1]) / 2f;
        String[] parts;
        int[] colors;
        if (c.isLink()) {
            parts = new String[]{nz(c.atkString), "/", nz(c.linkString)};
            colors = new int[]{statValueColor(c.attack, c.baseAttack), 0xFFFFFFFF, 0xFF99FFFF};
        } else {
            parts = new String[]{nz(c.atkString), "/", nz(c.defString)};
            colors = new int[]{statValueColor(c.attack, c.baseAttack), 0xFFFFFFFF,
                    statValueColor(c.defense, c.baseDefense)};
        }
        // 数字中心移到卡片外侧半个字高处（远离卡片中心方向）：贴边而不压卡面
        float statY = edgeY - (float) Math.signum(center[1] - edgeY) * (hpx / 2f);
        statY += (float) Math.signum(center[1] - statY) * STAT_SNUG_PX;   // 再贴近卡片 2px
        drawScreenText(edgeX, statY, parts, colors, hpx);

        // 等级(L*,白/调律黄)或阶级(R*,玫红)紧贴「视角相对上沿」外侧的角显示。
        // 对齐 drawing.cpp DrawStatus：我方在卡片左上角（屏幕上缘左角、左对齐）；
        // 对方卡旋转 180°，其等级在卡主视角右上角 = 屏幕下缘左角、左对齐
        //（原实现取屏幕下缘右角=卡主视角左上角，故本轮改为左角）。
        if (c.lvString != null && !c.lvString.isEmpty()) {
            float[] tl = ours
                    ? (farL[0] <= farR[0] ? farL : farR)      // 我方：屏幕上缘左角
                    : (nearL[0] <= nearR[0] ? nearL : nearR); // 对方：屏幕下缘左角（=卡主视角右上角）
            int lvColor = (c.type & TYPE_XYZ) != 0 ? 0xFFFF80FF
                    : (c.type & TYPE_TUNER) != 0 ? 0xFFFFFF00 : 0xFFFFFFFF;
            // 竖直方向移到卡片外侧半个字高处，再朝卡片方向贴近 STAT_SNUG_PX（我方下移、对方上移）
            float lvY = tl[1] - (float) Math.signum(center[1] - tl[1]) * (hpx / 2f);
            lvY += (float) Math.signum(center[1] - lvY) * STAT_SNUG_PX;
            // 水平方向朝卡片中心贴近 STAT_SNUG_PX（我方等级右移 2px）
            float lvX = tl[0] + (float) Math.signum(center[0] - tl[0]) * STAT_SNUG_PX;
            drawScreenTextAligned(lvX, lvY, new String[]{c.lvString}, new int[]{lvColor}, hpx, ALIGN_LEFT);
        }
    }

    private void drawPendulumScaleText(GameField f, int p, int seq, boolean leftScale) {
        GameField.ClientCard c;
        try { c = f.players[p].spellZone.get(seq); } catch (Throwable e) { return; }
        if (c == null || !c.isFaceUp() || c.is_moving) return;
        if ((c.type & 0x1000000) == 0) return;                // CardType.Pendulum
        if (c.equipTarget != null) return;                    // 对齐 drawing.cpp !equipTarget
        String txt = leftScale ? c.lscString : c.rscString;   // 左区左刻度 / 右区右刻度
        if (txt == null || txt.isEmpty()) return;
        float[] r = GameField.getZoneRect(p, 0x08, seq);
        if (r == null) return;
        float cx = r[0], cy = r[1], hw = r[2] / 2f, hh = r[3] / 2f;
        float[] nearL = view.projectWorldPoint(FieldGeometry.mirrorX(cx - hw), cy + hh, 0.02f);
        float[] nearR = view.projectWorldPoint(FieldGeometry.mirrorX(cx + hw), cy + hh, 0.02f);
        float[] farL = view.projectWorldPoint(FieldGeometry.mirrorX(cx - hw), cy - hh, 0.02f);
        float[] farR = view.projectWorldPoint(FieldGeometry.mirrorX(cx + hw), cy - hh, 0.02f);
        float[] center = view.projectWorldPoint(FieldGeometry.mirrorX(cx), cy, 0.02f);
        if (nearL == null || nearR == null || farL == null || farR == null || center == null) return;
        // 字号对齐堆叠数量
        float cardHpx = Math.abs(center[1] - nearL[1]) * 2f;
        float hpx = Math.max(10f, Math.min(40f, cardHpx * 0.30f));
        boolean ours = (p == 0);
        // 左刻度→视角左上角、右刻度→视角右上角（对方旋转 180° 后左右/上下互换）
        float[] corner;
        if (ours) {
            float[] l = farL[0] <= farR[0] ? farL : farR;
            float[] rr = farL[0] <= farR[0] ? farR : farL;
            corner = leftScale ? l : rr;
        } else {
            float[] l = nearL[0] >= nearR[0] ? nearL : nearR;   // 对方视角左上 = 屏幕右
            float[] rr = nearL[0] >= nearR[0] ? nearR : nearL;  // 对方视角右上 = 屏幕左
            corner = leftScale ? l : rr;
        }
        float tx = corner[0] + (float) Math.signum(center[0] - corner[0]) * hpx * 0.9f;
        float ty = corner[1] + (float) Math.signum(center[1] - corner[1]) * hpx * 0.8f;
        // 刻度白色（对齐 drawing.cpp 灵摆刻度 0xffffffff）
        drawScreenText(tx, ty, new String[]{txt}, new int[]{0xFFFFFFFF}, hpx);
    }

    private void drawScreenNumber(float[] screenXY, String text, int color, float heightPx) {
        if (screenXY == null || text == null || text.isEmpty()) return;
        int tex = obtainNumberTexture(text, color);
        if (tex <= 0) return;
        view.drawScreenQuadTex(screenXY[0], screenXY[1], heightPx * 2f, heightPx, tex, 1f);
    }

    private int obtainNumberTexture(String text, int color) {
        FieldTextureManager tex = view.tex;
        String k = text + "|" + color;
        Long key = numLabelKeys.get(k);
        if (key == null) {
            key = numLabelKeySeq--;
            numLabelKeys.put(k, key);
            try {
                tex.offerUpload(new FieldTextureManager.PendingUpload(key, makeNumberBitmap(text, color), true));
            } catch (Throwable ignored) {
            }
        }
        Integer id = tex.texCache().get(key);
        return id != null ? id : -1;
    }

    private static Bitmap makeNumberBitmap(String text, int color) {
        int w = 128, h = 64;
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTextSize(44f);
        p.setFakeBoldText(true);
        float tw = p.measureText(text);
        if (tw > w * 0.92f) p.setTextSize(44f * (w * 0.92f) / tw);
        p.setColor(color | 0xFF000000);
        p.setTextAlign(Paint.Align.CENTER);
        p.setShadowLayer(3f, 1f, 1f, 0xC0000000);
        cv.drawText(text, w / 2f, h / 2f - (p.ascent() + p.descent()) / 2f, p);
        return bmp;
    }

    private static Paint makeStatMeasurePaint() {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTextSize(STAT_TEXT_SIZE);
        p.setFakeBoldText(true);
        return p;
    }

    /** 多段文字合成位图的宽高比（宽随文本增长、字高恒定），用于绘制时保持不拉伸 */
    private static float statTextAspect(String[] parts) {
        float tw = 0f;
        for (String s : parts) tw += STAT_MEASURE_PAINT.measureText(s);
        int w = (int) Math.ceil(tw) + STAT_PAD_X * 2;
        if (w < STAT_BMP_H) w = STAT_BMP_H;
        return (float) w / (float) STAT_BMP_H;
    }

    private int obtainStatTexture(String[] parts, int[] colors) {
        FieldTextureManager tex = view.tex;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) sb.append(parts[i]).append('#').append(colors[i]).append('|');
        String k = sb.toString();
        Long key = statLabelKeys.get(k);
        if (key == null) {
            key = statLabelKeySeq--;
            statLabelKeys.put(k, key);
            try {
                tex.offerUpload(new FieldTextureManager.PendingUpload(key, makeColoredTextBitmap(parts, colors), true));
            } catch (Throwable ignored) {
            }
        }
        Integer id = tex.texCache().get(key);
        return id != null ? id : -1;
    }

    private static Bitmap makeColoredTextBitmap(String[] parts, int[] colors) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTextSize(STAT_TEXT_SIZE);
        p.setFakeBoldText(true);
        float tw = 0f;
        for (String s : parts) tw += p.measureText(s);
        int w = (int) Math.ceil(tw) + STAT_PAD_X * 2;
        if (w < STAT_BMP_H) w = STAT_BMP_H;
        int h = STAT_BMP_H;
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        p.setTextAlign(Paint.Align.LEFT);
        p.setShadowLayer(3f, 1f, 1f, 0xFF000000);
        float baseline = h / 2f - (p.ascent() + p.descent()) / 2f;
        float x = (w - tw) / 2f;
        for (int i = 0; i < parts.length; i++) {
            p.setColor(colors[i] | 0xFF000000);
            cv.drawText(parts[i], x, baseline, p);
            x += p.measureText(parts[i]);
        }
        return bmp;
    }

    /** 在屏幕点绘制多色分段文字：字高 heightPx，宽 = heightPx × 文本宽高比，整体居中于 (cx,cy) */
    private void drawScreenText(float cx, float cy, String[] parts, int[] colors, float heightPx) {
        if (parts == null || parts.length == 0) return;
        int tex = obtainStatTexture(parts, colors);
        if (tex <= 0) return;
        float w = heightPx * statTextAspect(parts);
        view.drawScreenQuadTex(cx, cy, w, heightPx, tex, 1f);
    }

    /**
     * 水平对齐绘制多色文字：ALIGN_LEFT 时 anchorX 为文字左边缘、ALIGN_RIGHT 时为右边缘、
     * ALIGN_CENTER 时为文字中心；竖直方向始终以 cy 为文字中心。
     */
    private void drawScreenTextAligned(float anchorX, float cy, String[] parts, int[] colors,
                                       float heightPx, int align) {
        if (parts == null || parts.length == 0) return;
        int tex = obtainStatTexture(parts, colors);
        if (tex <= 0) return;
        float w = heightPx * statTextAspect(parts);
        float cx = anchorX;
        if (align == ALIGN_LEFT) cx = anchorX + w / 2f;
        else if (align == ALIGN_RIGHT) cx = anchorX - w / 2f;
        view.drawScreenQuadTex(cx, cy, w, heightPx, tex, 1f);
    }

    /** 攻/守数值颜色（对齐 drawing.cpp DrawStatus：高于原值黄、低于原值粉、等于白） */
    private static int statValueColor(int cur, int base) {
        if (cur > base) return 0xFFFFFF00;
        if (cur < base) return 0xFFFF2090;
        return 0xFFFFFFFF;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
