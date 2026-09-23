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
    /** 灵摆刻度/怪兽等级数字抬升系数（×字高 hpx）：把文字从「竖中心坐在顶角」上移到「底边坐在顶角」，
     *  使文字抬高到卡片矩形顶点外缘、与同角位的可发动绿点对齐。正交屏 y 向下，故沿顶角背离
     *  卡片中心的一侧偏移。 */
    private static final float SCALE_TOP_LIFT = 0.5f;

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
     * 区域数字贴在本堆格子「靠近摄像头的底边」(+y 侧缘)内侧近端（原贴外缘外侧，
     * 会压到邻区堆叠上导致归属看错）；总攻击力数字叠在 bar 中心。
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
                // 锚点从近端外侧 0.14 改为贴进本堆近端内侧 0.05：数字紧跟自己的堆叠，
                // 不再压向场地中心方向相邻堆的上缘造成视觉误判
                float[] anchor = view.projectWorldPoint(FieldGeometry.mirrorX(r[0]), nearY - 0.05f, 0.02f);
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
     * - 灵摆刻度：文字贴卡片矩形「屏幕外侧顶角顶点」——屏幕左侧卡左上角(左对齐)、
     *   屏幕右侧卡右上角(右对齐)；rule>=4 用 seq0/seq4，rule<4 用 seq6/seq7（MR3 刻度已烘焙
     *   进卡图纹理，此处仅补文字定位分支）。怪兽等级对准屏幕上所见卡片矩形的顶角顶点
     *   （文字中心即顶点：我方=左上角、对方倒置卡=右上角，见 {@link #drawMonsterStatTexts}）。
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
        float cx = r[0], cy = r[1];
        // 文字锚定到「卡片矩形」而非更大的格子矩形——按卡片自身足迹取半宽半高；
        // 守备表示卡片绕 Z 旋转 90°，其世界 X/Y 半 extent 对调。
        boolean defense = (c.position & GameField.POS_DEFENSE) != 0;
        float hx = (defense ? FieldGeometry.CARD_H : FieldGeometry.CARD_W) * 0.5f;
        float hy = (defense ? FieldGeometry.CARD_W : FieldGeometry.CARD_H) * 0.5f;
        // 四角 + 中心投影（world +y 靠相机 → 屏幕更下）
        float[] nearL = view.projectWorldPoint(FieldGeometry.mirrorX(cx - hx), cy + hy, 0.02f);
        float[] nearR = view.projectWorldPoint(FieldGeometry.mirrorX(cx + hx), cy + hy, 0.02f);
        float[] farL = view.projectWorldPoint(FieldGeometry.mirrorX(cx - hx), cy - hy, 0.02f);
        float[] farR = view.projectWorldPoint(FieldGeometry.mirrorX(cx + hx), cy - hy, 0.02f);
        float[] center = view.projectWorldPoint(FieldGeometry.mirrorX(cx), cy, 0.02f);
        if (nearL == null || nearR == null || farL == null || farR == null || center == null) return;
        // 字号基准：恒按攻击表示的 CARD_H 竖向投影测量——守备表示卡片 hx/hy 对调（长轴
        // 变横向），若继续用 footprint 的 nearL 量高则基于 CARD_W，守备的 ATK/DEF 与等级
        // 文字会明显小于攻击表示；文字位置仍锚定旋转后矩形的当前四角（下方 near/far）
        float[] hTop = view.projectWorldPoint(FieldGeometry.mirrorX(cx), cy - FieldGeometry.CARD_H * 0.5f, 0.02f);
        float[] hBot = view.projectWorldPoint(FieldGeometry.mirrorX(cx), cy + FieldGeometry.CARD_H * 0.5f, 0.02f);
        if (hTop == null || hBot == null) return;
        float cardHpx = Math.abs(hBot[1] - hTop[1]);
        float hpx = Math.max(10f, Math.min(40f, cardHpx * 0.30f)) * STAT_SIZE_SCALE;
        boolean ours = (p == 0);

        // ATK/DEF（连接值为 ATK/L-n）居中压在卡片「视角相对底边」线上——字高一半在卡上、
        // 一半垂在卡外，与卡片略微重叠而不整体显示在卡面内。
        float[] eL = ours ? nearL : farL;
        float[] eR = ours ? nearR : farR;
        float edgeX = (eL[0] + eR[0]) / 2f;
        float edgeY = (eL[1] + eR[1]) / 2f;
        String[] parts;
        int[] colors;
        boolean[] boldFlags;
        if (c.isLink()) {
            parts = new String[]{nz(c.atkString), "/", nz(c.linkString)};
            colors = new int[]{statValueColor(c.attack, c.baseAttack), 0xFFFFFFFF, 0xFF99FFFF};
            boldFlags = new boolean[]{true, false, false};
        } else {
            parts = new String[]{nz(c.atkString), "/", nz(c.defString)};
            colors = new int[]{statValueColor(c.attack, c.baseAttack), 0xFFFFFFFF,
                    statValueColor(c.defense, c.baseDefense)};
            boldFlags = defense ? new boolean[]{false, false, true}
                               : new boolean[]{true, false, false};
        }
        drawScreenText(edgeX, edgeY, parts, colors, boldFlags, hpx);

        // 等级(L*/调律黄/阶级攻瑰红)对准「屏幕上所见卡片矩形」的顶角顶点：文字竖中心即顶点
        //（不做底边坐角抬升），我方=左上角 farL 左对齐向右延伸，对方卡旋转 180° 倒置，
        // 其所见矩形的右上角 farR 右对齐向左延伸。
        if (c.lvString != null && !c.lvString.isEmpty()) {
            float[] corner = ours ? farL : farR;
            int lvColor = (c.type & TYPE_XYZ) != 0 ? 0xFFFF80FF
                    : (c.type & TYPE_TUNER) != 0 ? 0xFFFFFF00 : 0xFFFFFFFF;
            drawScreenTextAligned(corner[0], corner[1],
                    new String[]{c.lvString}, new int[]{lvColor}, hpx,
                    ours ? ALIGN_LEFT : ALIGN_RIGHT);
        }
    }

    /** 灵摆刻度数字：贴卡片矩形屏幕外侧顶角顶点（左侧卡=左上角、右侧卡=右上角），
     *  与 {@link CardOverlayRenderer} 绿点角位互补（右侧卡绿点让位到左上角）。
     *  屏幕侧按格子中心 mirrorX 后与场地中轴比较；守备表示卡片足迹 hx/hy 对调；
     *  字号与攻守文字同源（CARD_H 竖向投影，不受守备旋转影响） */
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
        float cx = r[0], cy = r[1];
        // 锚到卡片矩形而非更大的格子矩形；守备表示绕 Z 转 90°，世界 X/Y 半 extent 对调
        boolean defense = (c.position & GameField.POS_DEFENSE) != 0;
        float hx = (defense ? FieldGeometry.CARD_H : FieldGeometry.CARD_W) * 0.5f;
        float hy = (defense ? FieldGeometry.CARD_W : FieldGeometry.CARD_H) * 0.5f;
        // 屏幕顶边恒为远端（−y 侧，远离相机）；绘制空间经 mirrorX 后 +x=屏幕左、−x=屏幕右，
        // 故 mirrorX(cx−hx)（变换后 x 较大）落在屏幕左上角、mirrorX(cx+hx) 落在屏幕右上角。
        float[] screenTopLeft = view.projectWorldPoint(FieldGeometry.mirrorX(cx - hx), cy - hy, 0.02f);
        float[] screenTopRight = view.projectWorldPoint(FieldGeometry.mirrorX(cx + hx), cy - hy, 0.02f);
        if (screenTopRight == null || screenTopLeft == null) return;
        // 字号基准：恒按 CARD_H 竖向投影测量（与 drawMonsterStatTexts 同源），
        // 守备表示下不自适应 footprint，避免刻度文字大小随表示变化
        float[] hTop = view.projectWorldPoint(FieldGeometry.mirrorX(cx), cy - FieldGeometry.CARD_H * 0.5f, 0.02f);
        float[] hBot = view.projectWorldPoint(FieldGeometry.mirrorX(cx), cy + FieldGeometry.CARD_H * 0.5f, 0.02f);
        if (hTop == null || hBot == null) return;
        float cardHpx = Math.abs(hBot[1] - hTop[1]);
        float hpx = Math.max(10f, Math.min(40f, cardHpx * 0.30f));
        // 屏幕侧：格子中心 mirrorX 后 > 中轴 = 屏幕左（我方 seq0/5、对方 seq6/7 带），
        // < 中轴 = 屏幕右（我方 seq6/7 带、对方 seq0/5）；左侧卡贴左上角顶点左对齐、
        // 右侧卡贴右上角顶点右对齐；再把文字上移 hpx×SCALE_TOP_LIFT，使其底边坐在顶角顶点上（抬高到顶点）
        boolean screenLeft = FieldGeometry.mirrorX(cx) > FieldGeometry.FIELD_CENTER_X;
        float[] corner = screenLeft ? screenTopLeft : screenTopRight;
        int align = screenLeft ? ALIGN_LEFT : ALIGN_RIGHT;
        // 刻度白色（对齐 drawing.cpp 灵摆刻度 0xffffffff）
        drawScreenTextAligned(corner[0], corner[1] - hpx * SCALE_TOP_LIFT,
                new String[]{txt}, new int[]{0xFFFFFFFF}, hpx, align);
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
    private static float statTextAspect(String[] parts, boolean[] boldFlags) {
        float tw = 0f;
        for (int i = 0; i < parts.length; i++) {
            if (boldFlags != null && i < boldFlags.length && boldFlags[i])
                STAT_MEASURE_PAINT.setFakeBoldText(true);
            else
                STAT_MEASURE_PAINT.setFakeBoldText(false);
            tw += STAT_MEASURE_PAINT.measureText(parts[i]);
        }
        STAT_MEASURE_PAINT.setFakeBoldText(true);
        int w = (int) Math.ceil(tw) + STAT_PAD_X * 2;
        if (w < STAT_BMP_H) w = STAT_BMP_H;
        return (float) w / (float) STAT_BMP_H;
    }

    private int obtainStatTexture(String[] parts, int[] colors, boolean[] boldFlags) {
        FieldTextureManager tex = view.tex;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length; i++) {
            sb.append(parts[i]).append('#').append(colors[i]);
            if (boldFlags != null && i < boldFlags.length && boldFlags[i]) sb.append('B');
            sb.append('|');
        }
        String k = sb.toString();
        Long key = statLabelKeys.get(k);
        if (key == null) {
            key = statLabelKeySeq--;
            statLabelKeys.put(k, key);
            try {
                tex.offerUpload(new FieldTextureManager.PendingUpload(key, makeColoredTextBitmap(parts, colors, boldFlags), true));
            } catch (Throwable ignored) {
            }
        }
        Integer id = tex.texCache().get(key);
        return id != null ? id : -1;
    }

    private static Bitmap makeColoredTextBitmap(String[] parts, int[] colors, boolean[] boldFlags) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTextSize(STAT_TEXT_SIZE);
        float tw = 0f;
        for (int i = 0; i < parts.length; i++) {
            p.setFakeBoldText(boldFlags != null && i < boldFlags.length && boldFlags[i]);
            tw += p.measureText(parts[i]);
        }
        int w = (int) Math.ceil(tw) + STAT_PAD_X * 2;
        if (w < STAT_BMP_H) w = STAT_BMP_H;
        int h = STAT_BMP_H;
        Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(bmp);
        p.setTextAlign(Paint.Align.LEFT);
        p.setShadowLayer(3f, 1f, 1f, 0xFF000000);
        p.setFakeBoldText(true);
        float baseline = h / 2f - (p.ascent() + p.descent()) / 2f;
        float x = (w - tw) / 2f;
        for (int i = 0; i < parts.length; i++) {
            p.setColor(colors[i] | 0xFF000000);
            p.setFakeBoldText(boldFlags != null && i < boldFlags.length && boldFlags[i]);
            cv.drawText(parts[i], x, baseline, p);
            x += p.measureText(parts[i]);
        }
        return bmp;
    }

    /** 在屏幕点绘制多色分段文字：字高 heightPx，宽 = heightPx × 文本宽高比，整体居中于 (cx,cy) */
    private void drawScreenText(float cx, float cy, String[] parts, int[] colors, boolean[] boldFlags, float heightPx) {
        if (parts == null || parts.length == 0) return;
        int tex = obtainStatTexture(parts, colors, boldFlags);
        if (tex <= 0) return;
        float w = heightPx * statTextAspect(parts, boldFlags);
        view.drawScreenQuadTex(cx, cy, w, heightPx, tex, 1f);
    }

    /**
     * 水平对齐绘制多色文字：ALIGN_LEFT 时 anchorX 为文字左边缘、ALIGN_RIGHT 时为右边缘、
     * ALIGN_CENTER 时为文字中心；竖直方向始终以 cy 为文字中心。
     */
    private void drawScreenTextAligned(float anchorX, float cy, String[] parts, int[] colors,
                                       float heightPx, int align) {
        if (parts == null || parts.length == 0) return;
        int tex = obtainStatTexture(parts, colors, null);
        if (tex <= 0) return;
        float w = heightPx * statTextAspect(parts, null);
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
