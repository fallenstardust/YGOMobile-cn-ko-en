package cn.garymb.ygomobile.game;

import cn.garymb.ygomobile.game.GameField.ClientCard;

/**
 * GameField 的坐标几何协作类：忠实移植 ClientField::GetCardLocation（MR4，rule=1；坐标真值来自
 * materials.cpp），以及场地底板 / 格子 / 堆叠区矩形。几何常量与对外 public static 入口仍保留在
 * 门面 {@link GameField}（render 包静态依赖 GameField 的 fx/getZoneRect/getPileRect 与 FIELD/ZONE/PILE 前缀常量），
 * 方法体转由此类实现，门面同名 static 入口一行转发至此。
 */
class GameFieldGeometry {

    private final GameField field;

    /**
     * 超量素材相对宿主怪兽的纵向露出量（世界单位）。C++ client_field.cpp 原值为 0.05，
     * 仅露出宿主下缘一线、素材卡显示不完整；此处按需求放大以在宿主下方露出更完整的素材卡，
     * 兼顾数量可辨与不越入相邻魔陷行，可据实机观感微调。
     */
    private static final float OVERLAY_PEEK = 0.16f;

    /**
     * 主怪兽区/魔陷区相邻格子间的额外缝隙（原始单位，与格子步长基准 1.1 同量纲）。
     * 原 5 主格中心步长恰等于格宽 1.1（槽矩形边对边贴合、无间隙），现按需求以中心保持
     * （中轴 3.95 固定、两侧对称外扩）将主 5 格（s<5）步长改为 1.1 + ZONE_GAP，使相邻格之间
     * 出现宽度约 ZONE_GAP×X_SCALE 的缝隙。按需求本间隙进一步统一作用于：额外怪兽区与双方
     * 主怪兽行的纵向间隔、主怪兽行与魔陷行的纵向间隔、场地/额外卡组/墓地/除外/卡组各堆叠格
     * 彼此及其与怪兽区/魔陷区之间（横向邻接用原始值 0.1，纵向邻接用 vg = 0.1×X_SCALE，
     * 使两方向的世界单位缝宽一致）。卡片落点 / 选中框 / 拾取 / 场地槽均派生自本组几何，
     * 会自动跟随。注：本选择有意不跟随 field3.png 烘焙网格线（需接受与底图网格的错位）。
     */
    private static final float ZONE_GAP = 0.1f;

    /** 纵向缝隙的世界单位等价值：X 向经 fx 放大 X_SCALE 倍，Y 向不放大，
     *  故 Y 向间隔需乘 X_SCALE 才能与横向缝隙视觉等宽 */
    private static float vg() {
        return ZONE_GAP * GameField.X_SCALE;
    }

    /** 对方手卡沿纵轴额外向远离场地中心方向平移量（Y 世界单位）：因场地区/魔陷行等
     *  按 ZONE_GAP 外扩后对方手卡更易遮挡魔陷区格子，按需求整体外推减少遮挡 */
    private static final float OPP_HAND_PUSH = 0.35f;

    GameFieldGeometry(GameField field) {
        this.field = field;
    }

    // === ClientField::GetCardLocation 忠实移植（MR4，rule=1；坐标真值来自 materials.cpp）===

    /**
     * 区域规格：怪兽/魔陷区宽 290px、高 254px，格子间 2px 间隔 → 横向间距 292px；
     * X 坐标以场地中心 3.95 为轴按 (292px世界长)/1.1 放大，决斗场更宽且与 field3.png 拉伸适配 */
    static float fx(float x) {
        return 3.95f + (x - 3.95f) * GameField.X_SCALE;
    }

    static float fieldBoardMinX() {
        return fx(GameField.FIELD_TEX_X_MIN);
    }

    static float fieldBoardMaxX() {
        return fx(GameField.FIELD_TEX_X_MAX);
    }

    /**
     * 格子矩形 {cx, cy, w, h}（materials.cpp vFieldMzone/vFieldSzone 中心经 fx 缩放）：
     * 与 getCardLocation 同源，保证卡片落点恒为对应格子的宽度中心
     */
    static float[] getZoneRect(int controler, int location, int sequence) {
        if (location == 0x04) {
            return new float[]{mzoneCX(controler, sequence), mzoneCY(controler, sequence), GameField.ZONE_W, GameField.ZONE_H};
        }
        if (location == 0x08) {
            // 场地区（szone seq5）改为与墓地堆叠区一致的长方形（PILE_W×PILE_H），其余魔陷格保持 ZONE_W×ZONE_H
            if (sequence == 5) {
                return new float[]{szoneCX(controler, sequence), szoneCY(controler, sequence), GameField.PILE_W, GameField.PILE_H};
            }
            return new float[]{szoneCX(controler, sequence), szoneCY(controler, sequence), GameField.ZONE_W, GameField.ZONE_H};
        }
        return null;
    }

    /** 堆叠区矩形 {cx, cy, w, h}（materials.cpp vFieldDeck/Grave/Remove/Extra 中心，
     *  按 ZONE_GAP 统一间隙外扩：侧列与魔陷行最外格留 0.1 原始横向间隙，
     *  列内相邻格纵向间隔留 vg，与怪兽/魔陷区缝隙等宽） */
    static float[] getPileRect(int controler, int location) {
        float cx;
        float cy;
        switch (location) {
            case 0x01:
                cx = fx(controler == 0 ? 7.4f : 0.5f);
                cy = controler == 0 ? 3.2f + vg() : -(3.2f + vg());
                break;
            case 0x10:
                cx = fx(controler == 0 ? 7.4f : 0.5f);
                cy = controler == 0 ? 2.0f : -2.0f;
                break;
            case 0x20:
                cx = fx(controler == 0 ? 7.4f : 0.5f);
                cy = controler == 0 ? 0.8f - vg() : -(0.8f - vg());
                break;
            case 0x40:
                cx = fx(controler == 0 ? 0.5f : 7.4f);
                cy = controler == 0 ? 3.2f + vg() : -(3.2f + vg());
                break;
            default:
                return null;
        }
        return new float[]{cx, cy, GameField.PILE_W, GameField.PILE_H};
    }

    private static float mzoneCX(int c, int s) {
        float step = 1.1f + ZONE_GAP;
        // 额外怪兽区(s5/s6)与主怪兽行第 2/4 列(seq1/seq3 = 3.95∓step)严格对齐：
        // 旧值 ±1.1 未含 ZONE_GAP，间隙统一后与主怪兽列错位，现改用同一 step 消除偏差
        if (c == 0) return fx(s < 5 ? 3.95f + (s - 2) * step : (s == 5 ? 3.95f - step : 3.95f + step));
        return fx(s < 5 ? 3.95f + (2 - s) * step : (s == 5 ? 3.95f + step : 3.95f - step));
    }

    private static float mzoneCY(int c, int s) {
        // 额外怪兽区行固定 Y=0（行高 1.2、上缘 0.6），主怪兽行按与额外怪兽区间隙
        // = vg 内收：中心 = 0.6 + 0.6 + vg = 1.2 + vg（原 1.4，间隙 0.2 偏大）
        if (s >= 5) return 0f;
        return c == 0 ? 1.2f + vg() : -(1.2f + vg());
    }

    private static float szoneCX(int c, int s) {
        float step = 1.1f + ZONE_GAP;
        if (c == 0) {
            if (s < 5) return fx(3.95f + (s - 2) * step);
            if (s == 5) return fx(0.5f);
            if (s == 6) return fx(0.5f);
            return fx(8.45f);
        }
        if (s < 5) return fx(3.95f + (2 - s) * step);
        if (s == 5) return fx(7.4f);
        if (s == 6) return fx(7.4f);
        return fx(-0.55f);
    }

    private static float szoneCY(int c, int s) {
        // 纵向间隔统一为 vg（与横向缝隙世界等宽）：魔陷行（s<5）距主怪兽行
        // (1.2+vg) 再隔 1.2+vg → 2.4+2vg；场地区(s==5)保持 ±2.0 与墓地堆叠同高；
        // 灵摆列(s>=6)距场地区下缘隔 vg → 0.8-vg，与除外堆叠同排。
        if (c == 0) {
            if (s < 5) return 2.4f + 2 * vg();
            if (s == 5) return 2.0f;
            return 0.8f - vg();
        }
        if (s < 5) return -(2.4f + 2 * vg());
        if (s == 5) return -2.0f;
        return -(0.8f - vg());
    }

    /**
     * 返回 {x, y, z, rotX, rotY, rotZ}，与 ClientField::GetCardLocation 一致
     */
    float[] getCardLocation(ClientCard pcard) {
        float[] t = new float[6];
        int controler = pcard.controler;
        int sequence = pcard.sequence;
        int location = pcard.location;
        boolean facedown = (pcard.position & GameField.POS_FACEDOWN) != 0;
        boolean defense = (pcard.position & GameField.POS_DEFENSE) != 0;
        boolean faceup = (pcard.position & GameField.POS_FACEUP) != 0;

        switch (location) {
            case 0x01: { // LOCATION_DECK
                float[] pr = getPileRect(controler, 0x01);
                t[0] = pr[0];
                t[1] = pr[1];
                // 堆叠 Z 严格对齐 C++ client_field.cpp（GetCardLocation/ResetSequence 均 0.01+0.01×seq）：
                // 旧实现 0.012 系数 + min(seq,18) 封顶与本类 resetSequence 不一致，落堆动画目标 Z 与
                // 堆内其余卡错位，回插/抽离时产生共面与层序漂移
                t[2] = 0.01f + 0.01f * sequence;
                boolean back = (field.deckReversed == pcard.is_reversed);
                t[4] = back ? GameField.PI : 0f;
                t[5] = controler == 0 ? 0f : GameField.PI;
                break;
            }
            case 0:
            case 0x02: { // LOCATION_HAND
                int count = field.getCardCount(controler, 0x02);
                if (count <= 0) count = 1;
                float spacing = handSpacing(count);
                if (controler == 0) {
                    t[0] = 3.95f - spacing * (count - 1) / 2f + sequence * spacing;
                    if (pcard.is_hovered) {
                        t[1] = 3.84f;
                        t[2] = 0.656f + 0.001f * sequence;
                    } else {
                        t[1] = 4.0f;
                        t[2] = 0.5f + 0.001f * sequence;
                    }
                    if (pcard.code != 0) {
                        t[3] = -0.798056f;
                        t[4] = 0f;
                    } else {
                        t[3] = 0.798056f;
                        t[4] = GameField.PI;
                    }
                } else {
                    t[0] = 3.95f + spacing * (count - 1) / 2f - sequence * spacing;
                    // 对方手卡整体沿纵轴外推 OPP_HAND_PUSH（悬停位同幅平移保持相对关系）
                    if (pcard.is_hovered) {
                        t[1] = -3.56f - OPP_HAND_PUSH;
                        t[2] = 0.656f - 0.001f * sequence;
                    } else {
                        t[1] = -3.4f - OPP_HAND_PUSH;
                        t[2] = 0.5f - 0.001f * sequence;
                    }
                    if (pcard.code == 0) {
                        t[3] = 0.798056f;
                        t[4] = GameField.PI;
                    } else {
                        t[3] = -0.798056f;
                        t[4] = 0f;
                    }
                }
                break;
            }
            case 0x04: { // LOCATION_MZONE
                t[0] = mzoneCX(controler, sequence);
                t[1] = mzoneCY(controler, sequence);
                // 超量宿主高度随素材数抬高：C++ mzone_buttom=0.02 固定不抬，但本项目素材
                // 露出量放大后固定 0.02 会让下层素材与格子槽共面；按需求每张素材计
                // 0.01f 厚度，宿主坐在素材堆顶上，素材逐层露出完整矩形
                t[2] = 0.02f + 0.01f * pcard.overlayed.size();
                if (controler == 0) {
                    if (defense) {
                        t[5] = -GameField.PI / 2f;
                        t[4] = facedown ? GameField.PI + 0.001f : 0f;
                    } else {
                        t[5] = 0f;
                        t[4] = facedown ? GameField.PI : 0f;
                    }
                } else {
                    if (defense) {
                        t[5] = GameField.PI / 2f;
                        t[4] = facedown ? GameField.PI + 0.001f : 0f;
                    } else {
                        t[5] = GameField.PI;
                        t[4] = facedown ? GameField.PI : 0f;
                    }
                }
                break;
            }
            case 0x08: { // LOCATION_SZONE
                t[0] = szoneCX(controler, sequence);
                t[1] = szoneCY(controler, sequence);
                t[2] = 0.01f;
                t[4] = facedown ? GameField.PI : 0f;
                t[5] = controler == 0 ? 0f : GameField.PI;
                break;
            }
            case 0x10: { // LOCATION_GRAVE
                float[] pr = getPileRect(controler, 0x10);
                t[0] = pr[0];
                t[1] = pr[1];
                t[2] = 0.01f + 0.01f * sequence;
                t[5] = controler == 0 ? 0f : GameField.PI;
                break;
            }
            case 0x20: { // LOCATION_REMOVED
                float[] pr = getPileRect(controler, 0x20);
                t[0] = pr[0];
                t[1] = pr[1];
                t[2] = 0.01f + 0.01f * sequence;
                t[4] = faceup ? 0f : GameField.PI;
                t[5] = controler == 0 ? 0f : GameField.PI;
                break;
            }
            case 0x40: { // LOCATION_EXTRA
                float[] pr = getPileRect(controler, 0x40);
                t[0] = pr[0];
                t[1] = pr[1];
                t[2] = 0.01f + 0.01f * sequence;
                t[4] = faceup ? 0f : GameField.PI;
                t[5] = controler == 0 ? 0f : GameField.PI;
                break;
            }
            case 0x80: { // LOCATION_OVERLAY
                ClientCard target = pcard.overlayTarget;
                if (target == null || target.location != 0x04) {
                    // 目标超量怪兽尚未就位——保持卡片当前姿态（对齐 C++ GetCardLocation
                    // 提前 return 不改 t/r 的语义），避免待挂素材被甩到世界原点
                    t[0] = pcard.curX;
                    t[1] = pcard.curY;
                    t[2] = pcard.curZ;
                    t[3] = pcard.curRotX;
                    t[4] = pcard.curRotY;
                    t[5] = pcard.curRotZ;
                    return t;
                }
                int oseq = target.sequence;
                int mseq = Math.max(0, Math.min(sequence, GameField.MAX_LAYER_COUNT - 1));
                // C++ GetCardLocation LOCATION_OVERLAY（client_field.cpp L1014-1036）：每枚素材按序号
                // 沿宿主卡横向逐张偏移 -0.12+0.06*mseq、并沿 Z 逐张抬高 material_height，形成扇形叠放。
                // 纵向露出量在 C++ 中为 ±0.05（仅露出宿主下缘一线），本项目按需求放大为 OVERLAY_PEEK，
                // 使宿主下方每枚素材卡露出更完整、数量直观可数；素材目标位由宿主 overlayTarget 的
                // sequence 实时推导，故宿主移动到别的格子时素材自动跟随叠放到新格下方。
                float dx = 0.12f - 0.06f * mseq;
                if (target.controler == 0) {
                    t[0] = mzoneCX(0, oseq) - dx;
                    t[1] = mzoneCY(0, oseq) + OVERLAY_PEEK;
                    t[5] = 0f;
                } else {
                    t[0] = mzoneCX(1, oseq) + dx;
                    t[1] = mzoneCY(1, oseq) - OVERLAY_PEEK;
                    t[5] = GameField.PI;
                }
                // z 阶梯：每枚素材矩形厚 0.01f（C++ material_height=0.003 不足以盖过格子槽
                // 0.004/selfield 0.01，会被区域格子重叠遮挡），首枚底面抬到 0.01 高于格子槽，
                // 逐枚叠加后整体位于怪兽区域格子之上，叠放中每张素材矩形都显示完整
                t[2] = 0.01f + 0.01f * mseq;
                break;
            }
        }
        return t;
    }

    /** 手卡间距：小于7张保留些许间距(0.95>卡宽0.8)，大于等于7张开始层叠，越多越密 */
    private static float handSpacing(int count) {
        if (count < 7) return 0.95f;
        return Math.max(0.55f, Math.min(0.72f, 5.0f / count));
    }
}
