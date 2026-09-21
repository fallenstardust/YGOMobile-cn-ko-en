package cn.garymb.ygomobile.render;

import android.graphics.Bitmap;
import android.opengl.Matrix;

import java.util.List;

import cn.garymb.ygomobile.game.GameField;

/**
 * 场地底板 / 区域槽 / 总攻击力 bar / 区域发动提示 / conti 隐形格子绘制（GameFieldView「绘制」分栏前半）。
 * 逐行对齐 gframe drawing.cpp（DrawBackGround / DrawMisc / vTotalAtk* / DrawSelectionLine 无关），
 * 复用门面底层绘制原语与 {@link FieldTextureManager} 纹理缓存、{@link FieldCamera} 视投影矩阵。
 */
final class FieldBoardRenderer {

    private final GameFieldView view;

    // === 总攻击力 bar 底图纹理键（矩形布局单一真值见 FieldGeometry.totalAtkRect）===
    private static final long TOTAL_ATK_KEY = -3L;
    // 区域发动 / conti_act 旋转提示图标（materials: act.png），对齐 drawing.cpp DrawMisc 的 tAct + act_rot
    private static final long ACT_TEX_KEY = -4L;
    // conti_cards 隐形格子最多堆叠显示层数（对齐 drawing.cpp 中部场地空隙的可视堆叠）
    private static final int MAX_CONTI_LAYERS = 5;

    FieldBoardRenderer(GameFieldView view) {
        this.view = view;
    }

    /**
     * 场地底板：对齐 gframe drawing.cpp L326/L369 ——
     * rule=(duel_rule>=4)?1:0 选 field3/field2；显示场地魔法卡时改用 field-transparent 版。
     * 底板矩形取 materials.cpp vField（-1..9 × -4..4）经 fx 映射，与格子/卡片同仿射，网格完美重叠。
     * 其余区域保持透明，透出窗口背景。
     */
    void drawFieldBoard(GameField f) {
        int rule = (f.dInfo.duelRule >= 4) ? 1 : 0;
        int code1 = fieldSpellCode(f, 0);
        int code2 = fieldSpellCode(f, 1);
        boolean transparent = code1 > 0 || code2 > 0;
        // 场地魔法背景图先于底板绘制（z=-0.01 底板之下），对齐 drawing.cpp DrawBackGround
        if (transparent) drawFieldSpellArt(code1, code2);
        int tex = view.tex.obtainFieldTexture(rule, transparent);
        // 因魔陷行新增纵向缝隙、卡区整体更外扩，场地底板贴图矩形随之稍微外扩（中心不变、四边各向外
        // 放大 BOARD_MARGIN），使卡片区仍完整落在底板内；底板网格与格子的错位已在需求中明确忽略。
        final float boardMargin = 0.25f;
        float w = (FieldGeometry.FIELD_X_MAX - FieldGeometry.FIELD_X_MIN) + boardMargin * 2f;
        float h = (FieldGeometry.FIELD_Y_MAX - FieldGeometry.FIELD_Y_MIN) + boardMargin * 2f;
        float cx = FieldGeometry.mirrorX((FieldGeometry.FIELD_X_MIN + FieldGeometry.FIELD_X_MAX) / 2f);
        if (tex > 0) {
            Matrix.setIdentityM(view.mModel, 0);
            Matrix.translateM(view.mModel, 0, cx, 0f, 0f);
            Matrix.scaleM(view.mModel, 0, w, h, 1f);
            view.drawQuadTex(view.mModel, tex, 1f);
        } else {
            // 贴图加载完成前的兜底：半透明底色，不遮挡窗口背景
            view.drawFlatQuad(cx, 0f, 0f, w, h, 0.05f, 0.09f, 0.16f, 0.55f);
        }
    }

    /**
     * gframe DrawBackGround 语义：取该方场地魔法区(szone seq5)正面场地卡 code，无则 0
     */
    private static int fieldSpellCode(GameField f, int player) {
        try {
            List<GameField.ClientCard> sz = f.players[player].spellZone;
            if (sz.size() > 5) {
                GameField.ClientCard c = sz.get(5);
                if (c != null && c.isFaceUp() && c.code != 0) return c.code;
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }

    /**
     * 场地魔法背景图（image_manager.cpp GetTextureField + materials.cpp vFieldSpell*）：
     * 单方/双方同码 → 整幅 vFieldSpell；双方异码 → 各画半幅（vFieldSpell1/2 的 uv 子矩形）
     */
    private void drawFieldSpellArt(int code1, int code2) {
        float x0 = FieldGeometry.fx(GameField.FIELD_SPELL_X_MIN);
        float x1 = FieldGeometry.fx(GameField.FIELD_SPELL_X_MAX);
        float w = x1 - x0;
        float cx = FieldGeometry.mirrorX((x0 + x1) / 2f);
        if (code1 > 0 && code2 > 0 && code1 != code2) {
            drawFieldSpellRect(view.tex.obtainFieldSpellTexture(code1),
                    0.8f, 3.2f, 1f, 0.2f, 0.8f, 0.63636f, 0.36364f, cx, w);
            drawFieldSpellRect(view.tex.obtainFieldSpellTexture(code2),
                    -3.2f, -0.8f, 1f, 1f, -0.36364f, 0.63636f, -0.43636f, cx, w);
        } else {
            drawFieldSpellRect(view.tex.obtainFieldSpellTexture(code1 > 0 ? code1 : code2),
                    GameField.FIELD_SPELL_Y_MIN, GameField.FIELD_SPELL_Y_MAX,
                    1f, 0f, 1f, 0f, 1f, cx, w);
        }
    }

    private void drawFieldSpellRect(int tex, float yMin, float yMax, float flipU,
                                    float offU, float scU, float offV, float scV,
                                    float cx, float w) {
        if (tex <= 0) return;
        Matrix.setIdentityM(view.mModel, 0);
        Matrix.translateM(view.mModel, 0, cx, (yMin + yMax) / 2f, -0.01f);
        Matrix.scaleM(view.mModel, 0, w, yMax - yMin, 1f);
        view.drawQuadTexUV(view.mModel, tex, 1f, flipU, 0f, offU, offV, scU, scV);
    }

    /**
     * 青色脉冲空格子槽：布局按 duel_rule 分流（对齐 drawing.cpp DrawBackGround L458-460 的
     * 规则过滤：MR<4 怪兽区仅 seq0-4、魔法陷阱区含左右额外格 seq6/7；MR≥4 怪兽区 7 格、
     * 魔法陷阱区仅 seq0-5），与 field2/field3 底板贴图印刷的格子对齐；
     * duelRule 已在 STOC_JOIN_GAME（猜拳前）写入 dInfo，见 EngineCallbackDelegate#onJoinGame
     */
    void drawZoneSlots(GameField f) {
        float pulse = 0.10f + 0.08f * (float) Math.sin(view.animTimeMs * 0.002);
        boolean mr4 = f.dInfo.duelRule >= 4;
        for (int p = 0; p < 2; p++) {
            for (int i = 0; i < (mr4 ? 7 : 5); i++) {
                float[] c = FieldGeometry.zoneCenter(p, 0x04, i);
                view.drawFlatQuad(FieldGeometry.mirrorX(c[0]), c[1], 0.004f,
                        FieldGeometry.ZONE_W, FieldGeometry.ZONE_H, 0f, 0.78f, 0.94f, pulse);
            }
            for (int i = 0; i <= 5; i++) {
                float[] c = FieldGeometry.zoneCenter(p, 0x08, i);
                // 场地区（seq5）绘制为与墓地一致的长方形（PILE_W×PILE_H），其余魔陷格保持方格
                float zw = (i == 5) ? FieldGeometry.PILE_W : FieldGeometry.ZONE_W;
                float zh = (i == 5) ? FieldGeometry.PILE_H : FieldGeometry.ZONE_H;
                view.drawFlatQuad(FieldGeometry.mirrorX(c[0]), c[1], 0.004f,
                        zw, zh, 0f, 0.78f, 0.94f, pulse);
            }
            if (!mr4) {
                // MR<4：field2 底板额外魔法陷阱格（左下/右下，几何真值见 GameFieldGeometry s==6/7 分支）
                for (int i = 6; i <= 7; i++) {
                    float[] c = FieldGeometry.zoneCenter(p, 0x08, i);
                    view.drawFlatQuad(FieldGeometry.mirrorX(c[0]), c[1], 0.004f,
                            FieldGeometry.ZONE_W, FieldGeometry.ZONE_H, 0f, 0.78f, 0.94f, pulse);
                }
            }
            for (int loc : new int[]{0x01, 0x10, 0x20, 0x40}) {
                float[] c = FieldGeometry.pileCenter(p, loc);
                if (c == null) continue;
                view.drawFlatQuad(FieldGeometry.mirrorX(c[0]), c[1], 0.004f,
                        FieldGeometry.PILE_W, FieldGeometry.PILE_H, 0f, 0.78f, 0.94f, pulse);
            }
        }
    }

    private static float[] totalAtkRect(int p, boolean mr4) {
        return FieldGeometry.totalAtkRect(p, mr4);
    }

    /**
     * 总攻击力 bar：duel_rule>=4 用 vTotalAtkme/op，否则 vTotalAtkmeT/opT；
     * raw x 经 fx 映射、y 直用，绘制空间再做 X 镜像，贴 totalAtk.png，仅 total_attack>0 时绘制。
     */
    void drawTotalAttackBars(GameField f) {
        if (f == null) return;
        boolean mr4 = f.dInfo.duelRule >= 4;
        int tex = obtainTotalAtkTexture();
        for (int p = 0; p < 2; p++) {
            if (f.dInfo.totalAttack[p] <= 0) continue;
            float[] rc = totalAtkRect(p, mr4);
            float x0 = FieldGeometry.fx(rc[0]), x1 = FieldGeometry.fx(rc[2]);
            float cxw = (x0 + x1) / 2f;
            float cyw = (rc[1] + rc[3]) / 2f;
            float w = Math.abs(x1 - x0), h = Math.abs(rc[3] - rc[1]);
            Matrix.setIdentityM(view.mModel, 0);
            Matrix.translateM(view.mModel, 0, FieldGeometry.mirrorX(cxw), cyw, 0.006f);
            Matrix.scaleM(view.mModel, 0, w, h, 1f);
            if (tex > 0) view.drawQuadTex(view.mModel, tex, 1f);
            else view.drawQuadColor(view.mModel, 0.85f, 0.62f, 0.12f, 0.80f);
        }
    }

    private int obtainTotalAtkTexture() {
        FieldTextureManager tex = view.tex;
        Integer id = tex.texCache().get(TOTAL_ATK_KEY);
        if (id != null) return id;
        if (!tex.beginRequest(TOTAL_ATK_KEY)) return -1;
        try {
            tex.texExecutor().execute(() -> {
                Bitmap b = null;
                try {
                    Bitmap src = TextureLoader.get().getTotalAtkTexture();
                    if (src != null && !src.isRecycled()) b = src.copy(Bitmap.Config.ARGB_8888, false);
                } catch (Throwable ignored) {
                }
                if (b != null) tex.offerUpload(new FieldTextureManager.PendingUpload(TOTAL_ATK_KEY, b, true));
                else tex.cancelRequest(TOTAL_ATK_KEY);
            });
        } catch (Throwable t) {
            tex.cancelRequest(TOTAL_ATK_KEY);
        }
        return -1;
    }

    /** 旋转图标自旋角度（度）：单一真值见 {@link FieldGeometry#actSpinDegrees(long)} */
    private float actSpinDegrees() {
        return FieldGeometry.actSpinDegrees(view.animTimeMs);
    }

    /**
     * 我方墓地/除外/卡组/额外在有可发动/可特召项（graveAct/removeAct/deckAct/extraAct 置位）时，
     * 在该堆叠格正上方绘制一个旋转的 act 图标（对齐 drawing.cpp deck_act/grave_act/remove_act/extra_act
     * 分支在 vFieldDeck/Grave/Remove/Extra 中心上方绘制旋转 vActivate）。
     * 灵摆召唤可用（pzoneAct）时另在左灵摆刻度魔陷格（MR4=seq0，否则 seq6）上方绘制同款旋转
     * act 图标（对齐 drawing.cpp pzone_act 分支 L838-847：vFieldSzone 中心绘旋转 vActivate）。
     * z 层阶梯：背景 field3 z=0 → selfield/link marker 0.01 → 卡上图标层
     * （SZONE 卡 0.01+0.03=0.04，MZONE 卡 0.02+0.03=0.05）→ act/conti_act 提示 0.06 置顶。
     */
    void drawZoneActHints(GameField f) {
        if (f == null) return;
        boolean any = false;
        for (int p = 0; p < 2; p++) {
            if (f.deckAct[p] || f.graveAct[p] || f.removeAct[p] || f.extraAct[p]
                    || f.pzoneAct[p]) {
                any = true;
                break;
            }
        }
        if (!any) return;
        int tex = obtainActTexture();
        if (tex <= 0) return;
        float spin = actSpinDegrees();
        float sz = FieldGeometry.ZONE_W;
        int leftSeq = f.dInfo.duelRule >= 4 ? 0 : 6;
        for (int p = 0; p < 2; p++) {
            drawActIconOverPile(tex, f, p, 0x01, f.deckAct[p], sz, spin);
            drawActIconOverPile(tex, f, p, 0x10, f.graveAct[p], sz, spin);
            drawActIconOverPile(tex, f, p, 0x20, f.removeAct[p], sz, spin);
            drawActIconOverPile(tex, f, p, 0x40, f.extraAct[p], sz, spin);
            if (f.pzoneAct[p]) {
                float[] c = FieldGeometry.zoneCenter(p, 0x08, leftSeq);
                // 0.06：高于 selfield(0.01) 与卡上图标层(≤0.05)，act 提示置顶
                if (c != null) drawActIconAt(tex, FieldGeometry.mirrorX(c[0]), c[1], 0.06f, sz, spin);
            }
        }
    }

    private void drawActIconOverPile(int tex, GameField f, int p, int loc, boolean on, float sz, float spin) {
        if (!on) return;
        float[] c = FieldGeometry.pileCenter(p, loc);
        if (c == null) return;
        // 按该堆叠当前显示厚度抬到「最高层之上一层」：Java drawPile 顶部 z =
        // PILE_BASE_Z + layers*PILE_LAYER_THICK，再加一层作为图标高度。
        // （对齐 drawing.cpp deck/grave/remove/extra_act 的 pile.size()*0.01+0.02 语义，
        //   但换算到本工程的堆叠模型，避免被卡片堆压住）
        int cnt = f.getCardCount(p, loc);
        int layers = Math.min(Math.max(cnt, 1), GameFieldView.PILE_MAX_LAYERS);
        float z = GameFieldView.PILE_BASE_Z + layers * GameFieldView.PILE_LAYER_THICK + GameFieldView.PILE_LAYER_THICK;
        drawActIconAt(tex, FieldGeometry.mirrorX(c[0]), c[1], z, sz, spin);
    }

    /** 在场地上方绘制一枚绕场地法线（世界 Z）自旋的 act 图标（平铺于场地平面、朝向相机） */
    private void drawActIconAt(int tex, float cx, float cy, float z, float sz, float spin) {
        Matrix.setIdentityM(view.mModel, 0);
        Matrix.translateM(view.mModel, 0, cx, cy, z);
        Matrix.rotateM(view.mModel, 0, spin, 0f, 0f, 1f);
        Matrix.scaleM(view.mModel, 0, sz, sz, 1f);
        view.drawQuadTex(view.mModel, tex, 0.92f);
    }

    /**
     * 战斗阶段按钮（PHASE_NEXT，锚定场地中心 (FIELD_CENTER_X,0)）所在场地中部空隙处，
     * 绘制一个「隐形格子」——堆叠显示 conti_cards（通讯中回合结束仍需效果结算的卡），
     * 并在其上方绘制旋转 act 图标（对齐 drawing.cpp conti_act 分支：vFieldContiAct 中心堆叠卡面 + 旋转 vActivate）。
     */
    void drawContiGrid(GameField f) {
        if (f == null || !f.contiAct) return;
        List<GameField.ClientCard> cards = f.contiCards;
        if (cards == null || cards.isEmpty()) return;
        float cx = FieldGeometry.FIELD_CENTER_X;
        float cy = 0f;
        float cardW = 0.9f;
        float cardH = 0.9f * 254f / 177f;
        int n = 0;
        for (int i = 0, s = cards.size(); i < s && n < MAX_CONTI_LAYERS; i++) {
            GameField.ClientCard c;
            try {
                c = cards.get(i);
            } catch (Throwable e) {
                continue;
            }
            if (c == null) continue;
            int code = c.code != 0 ? c.code : c.chain_code;
            float z = 0.03f + 0.01f * n;
            Matrix.setIdentityM(view.mModel, 0);
            Matrix.translateM(view.mModel, 0, cx, cy, z);
            Matrix.scaleM(view.mModel, 0, cardW, cardH, 1f);
            if (code > 0) {
                int ct = view.tex.obtainTexture(code, 0, 0);
                if (ct > 0) view.drawQuadTex(view.mModel, ct, 1f);
                else view.drawQuadColor(view.mModel, 0.35f, 0.35f, 0.40f, 1f);
            } else {
                int cover = view.tex.obtainCover(false);
                if (cover > 0) view.drawQuadTex(view.mModel, cover, 1f);
            }
            n++;
        }
        int tex = obtainActTexture();
        if (tex > 0) {
            float spin = actSpinDegrees();
            // conti_act 置顶：不低于图标/自选场层阶梯（selfield 0.01、图标≤0.05）+0.01，
            // 且在 conti 卡堆（0.03+0.01n）之上一层
            float actZ = Math.max(0.06f, 0.03f + 0.01f * n + 0.03f);
            drawActIconAt(tex, cx, cy, actZ, cardW, spin);
        }
    }

    /** act 提示图标纹理（act.png）：仿 obtainTotalAtkTexture，首次请求异步上传，未就绪返回 -1 */
    private int obtainActTexture() {
        FieldTextureManager tex = view.tex;
        Integer id = tex.texCache().get(ACT_TEX_KEY);
        if (id != null) return id;
        if (!tex.beginRequest(ACT_TEX_KEY)) return -1;
        try {
            tex.texExecutor().execute(() -> {
                Bitmap b = null;
                try {
                    Bitmap src = TextureLoader.get().getActTexture();
                    if (src != null && !src.isRecycled()) b = src.copy(Bitmap.Config.ARGB_8888, false);
                } catch (Throwable ignored) {
                }
                if (b != null) tex.offerUpload(new FieldTextureManager.PendingUpload(ACT_TEX_KEY, b, true));
                else tex.cancelRequest(ACT_TEX_KEY);
            });
        } catch (Throwable t) {
            tex.cancelRequest(ACT_TEX_KEY);
        }
        return -1;
    }
}
