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
            return new float[]{szoneCX(controler, sequence), szoneCY(controler, sequence), GameField.ZONE_W, GameField.ZONE_H};
        }
        return null;
    }

    /** 堆叠区矩形 {cx, cy, w, h}（materials.cpp vFieldDeck/Grave/Remove/Extra 中心） */
    static float[] getPileRect(int controler, int location) {
        float cx;
        float cy;
        switch (location) {
            case 0x01:
                cx = fx(controler == 0 ? 7.3f : 0.6f);
                cy = controler == 0 ? 3.3f : -3.3f;
                break;
            case 0x10:
                cx = fx(controler == 0 ? 7.3f : 0.6f);
                cy = controler == 0 ? 2.0f : -2.0f;
                break;
            case 0x20:
                cx = fx(controler == 0 ? 7.3f : 0.6f);
                cy = controler == 0 ? 0.7f : -0.7f;
                break;
            case 0x40:
                cx = fx(controler == 0 ? 0.6f : 7.3f);
                cy = controler == 0 ? 3.3f : -3.3f;
                break;
            default:
                return null;
        }
        return new float[]{cx, cy, GameField.PILE_W, GameField.PILE_H};
    }

    private static float mzoneCX(int c, int s) {
        if (c == 0) return fx(s < 5 ? 1.75f + 1.1f * s : (s == 5 ? 2.85f : 5.05f));
        return fx(s < 5 ? 6.15f - 1.1f * s : (s == 5 ? 5.05f : 2.85f));
    }

    private static float mzoneCY(int c, int s) {
        if (s >= 5) return 0f;
        return c == 0 ? 1.4f : -1.4f;
    }

    private static float szoneCX(int c, int s) {
        if (c == 0) {
            if (s < 5) return fx(1.75f + 1.1f * s);
            if (s == 5) return fx(0.6f);
            if (s == 6) return fx(0.6f);
            return fx(8.3f);
        }
        if (s < 5) return fx(6.15f - 1.1f * s);
        if (s == 5) return fx(7.3f);
        if (s == 6) return fx(7.3f);
        return fx(-0.4f);
    }

    private static float szoneCY(int c, int s) {
        if (c == 0) {
            if (s < 5) return 2.6f;
            if (s == 5) return 2.0f;
            return 0.7f;
        }
        if (s < 5) return -2.6f;
        if (s == 5) return -2.0f;
        return -0.7f;
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
                t[2] = 0.01f + 0.012f * Math.min(sequence, 18);
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
                    if (pcard.is_hovered) {
                        t[1] = -3.56f;
                        t[2] = 0.656f - 0.001f * sequence;
                    } else {
                        t[1] = -3.4f;
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
                t[2] = 0.02f;
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
                t[2] = 0.01f + 0.012f * Math.min(sequence, 18);
                t[5] = controler == 0 ? 0f : GameField.PI;
                break;
            }
            case 0x20: { // LOCATION_REMOVED
                float[] pr = getPileRect(controler, 0x20);
                t[0] = pr[0];
                t[1] = pr[1];
                t[2] = 0.01f + 0.012f * Math.min(sequence, 18);
                t[4] = faceup ? 0f : GameField.PI;
                t[5] = controler == 0 ? 0f : GameField.PI;
                break;
            }
            case 0x40: { // LOCATION_EXTRA
                float[] pr = getPileRect(controler, 0x40);
                t[0] = pr[0];
                t[1] = pr[1];
                t[2] = 0.01f + 0.012f * Math.min(sequence, 18);
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
                // 沿宿主卡横向逐张偏移 -0.12+0.06*mseq、并沿 Z 逐张抬高 material_height，形成扇形叠放，
                // 使场上素材数量直观可数（对齐 C++，不再对 <3 张收拢为纯堆叠）。
                float dx = 0.12f - 0.06f * mseq;
                if (target.controler == 0) {
                    t[0] = mzoneCX(0, oseq) - dx;
                    t[1] = mzoneCY(0, oseq) + 0.05f;
                    t[5] = 0f;
                } else {
                    t[0] = mzoneCX(1, oseq) + dx;
                    t[1] = mzoneCY(1, oseq) - 0.05f;
                    t[5] = GameField.PI;
                }
                // z 阶梯 0.001+0.003*mseq（对齐 overlay_buttom=0.001 / material_height=0.003）：
                // 错开格子槽 0.004 / selfield 0.01 / SZONE 卡 0.01 共面条栅，且恒低于 MZONE 宿主卡 0.02
                t[2] = 0.001f + mseq * 0.003f;
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
