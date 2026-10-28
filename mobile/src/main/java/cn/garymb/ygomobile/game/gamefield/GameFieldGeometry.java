package cn.garymb.ygomobile.game.gamefield;

import cn.garymb.ygomobile.game.gamefield.ClientCard;

/**
 * GameField 的坐标几何协作类：忠实移植 ClientField::GetCardLocation（MR4，rule=1；坐标真值来自
 * materials.cpp），以及场地底板 / 格子 / 堆叠区矩形。几何常量与对外 public static 入口仍保留在
 * 门面 {@link GameField}（render 包静态依赖 GameField 的 fx/getZoneRect/getPileRect 与 FIELD/ZONE/PILE 前缀常量），
 * 方法体转由此类实现，门面同名 static 入口一行转发至此。
 */
public class GameFieldGeometry {

    private final GameField field;

    /**
     * 超量素材相对宿主怪兽的纵向露出量（世界单位）。C++ client_field.cpp 原值为 0.05，
     * 仅露出宿主下缘一线、素材卡显示不完整；此处按需求放大以在宿主下方露出更完整的素材卡，
     * 兼顾数量可辨与不越入相邻魔陷行，可据实机观感微调。
     */
    private static final float OVERLAY_PEEK = 0.16f;

    /** 当前对局 master rule ≥4（field3 布局）？静态镜像见 GameField.sDuelRule */
    private static boolean mr4() {
        return GameField.sDuelRule >= 4;
    }

    /** 对方手卡沿纵轴额外向远离场地中心方向平移量（Y 世界单位）：因场地区等外扩后
     *  对方手卡更易遮挡魔陷区格子，按需求整体外推减少遮挡。
     *  横屏决斗场位置调整后本偏移会让对方场上魔陷行与手卡间距显大，按需求收窄至 0.15
     *  以拉近两者、仅保留少量防遮挡余量；改动需同步 FieldCamera.OPP_HAND_Y(-3.4-本值)。 */
    private static final float OPP_HAND_PUSH = 0.15f;

    /** 手牌触到侧列带宽封顶时的最小可见露出量（世界单位）：极端张数下宁可卡片间
     *  层叠更多，也不允许一张手卡被完全盖住（C++ 无此下限，其 gframe 手卡可完全叠掩） */
    private static final float HAND_MIN_GAP = 0.1f;

    GameFieldGeometry(GameField field) {
        this.field = field;
    }

    // === ClientField::GetCardLocation 忠实移植（坐标真值来自 materials.cpp）===
    // 格子布局真值 = vFieldMzone/vFieldSzone/vFieldDeck/Grave/Remove/Extra（rule 维按
    // duel_rule 选 0/1，对应 field2.png / field3.png 烘焙网格线）：主怪兽/魔陷列中心
    // x = 1.75 + 1.1·seq（p1 镜像 6.15 − 1.1·seq），格边对边严丝合缝（步长 = 格宽 1.1）；
    // 主怪兽行 y=±1.4、魔陷行 y=±2.6、额外怪兽区 y=0（x=2.85/5.05）。
    // 历史上此处曾按需求加 ZONE_GAP=0.1 统一缝隙并内收行位，实测导致卡片/格子槽与
    // 底图网格错位，现整体还原 C++ 值；横向仍经 fx() 等比拉伸（底板贴图同映射）。

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
            // materials.cpp：魔陷主列 0-4 为 1.1×1.2 方格；场地区(seq5)与 MR<4 额外灵摆列
            // (seq6/7)均为 0.8×1.2 竖格（与侧列堆叠格同宽）
            if (sequence >= 5) {
                return new float[]{szoneCX(controler, sequence), szoneCY(controler, sequence), GameField.PILE_W, GameField.PILE_H};
            }
            return new float[]{szoneCX(controler, sequence), szoneCY(controler, sequence), GameField.ZONE_W, GameField.ZONE_H};
        }
        return null;
    }

    /** 堆叠区矩形 {cx, cy, w, h}：materials.cpp vFieldDeck/Grave/Remove/Extra 中心经 fx 缩放。
     *  MR4(rule1)：墓地与除外同列上下两格（±2.0 / ±0.7）；MR<4(rule0)：墓地下移 ±0.7、
     *  除外改画外侧凸格（p0 x=8.3 / p1 x=-0.4），与 field2.png 印刷一致 */
    static float[] getPileRect(int controler, int location) {
        boolean mr4 = mr4();
        float cx;
        float cy;
        switch (location) {
            case 0x01:
                cx = fx(controler == 0 ? 7.3f : 0.6f);
                cy = controler == 0 ? 3.3f : -3.3f;
                break;
            case 0x10:
                cx = fx(controler == 0 ? 7.3f : 0.6f);
                cy = controler == 0 ? (mr4 ? 2.0f : 0.7f) : -(mr4 ? 2.0f : 0.7f);
                break;
            case 0x20:
                cx = fx(controler == 0 ? (mr4 ? 7.3f : 8.3f) : (mr4 ? 0.6f : -0.4f));
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
        // materials.cpp vFieldMzone：主 5 格中心 1.75+1.1·i（p1 镜像 6.15−1.1·i），
        // 额外怪兽区 seq5/seq6 = 2.85/5.05（p1 对调），与主格同列对齐
        if (c == 0)
            return fx(s < 5 ? 1.75f + 1.1f * s : (s == 5 ? 2.85f : 5.05f));
        return fx(s < 5 ? 6.15f - 1.1f * s : (s == 5 ? 5.05f : 2.85f));
    }

    private static float mzoneCY(int c, int s) {
        // 额外怪兽区行中心 Y=0（-0.6~0.6）；主怪兽行中心 ±1.4（0.8~2.0），与底板网格线一致
        if (s >= 5) return 0f;
        return c == 0 ? 1.4f : -1.4f;
    }

    private static float szoneCX(int c, int s) {
        if (c == 0) {
            if (s < 5) return fx(1.75f + 1.1f * s);
            if (s == 7) return fx(mr4() ? 8.3f : 7.3f);
            return fx(0.6f);
        }
        if (s < 5) return fx(6.15f - 1.1f * s);
        if (s == 7) return fx(mr4() ? -0.4f : 0.6f);
        return fx(7.3f);
    }

    private static float szoneCY(int c, int s) {
        // materials.cpp vFieldSzone[rule]：主魔陷行 ±2.6（2.0~3.2，与主怪兽行共边 2.0）；
        // 场地区 seq5：MR4 与魔陷同排 ±2.0，MR<3 在侧列底格 ±0.7；
        // 额外灵摆 seq6/7（仅 MR<4 使用）：±2.0
        float sign = c == 0 ? 1f : -1f;
        if (s < 5) return sign * 2.6f;
        if (s == 5) return sign * (mr4() ? 2.0f : 0.7f);
        return sign * (mr4() ? 0.7f : 2.0f);
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
                // 手牌整排宽度硬边界（用户规格）：最左/最右卡不得越入两侧侧列——限制在本侧
                // 卡组格内沿（fx(7.3)−PW/2，即卡组格左下角）与额外卡组格内沿
                // （fx(0.6)＋PW/2，即额外格右下角）之间，整排恒夹在两侧列格中间的净空带；
                // 带宽对两席、横竖屏均对称于中心 3.95，世界坐标封顶与相机无关。参照 C++
                // client_field.cpp GetCardLocation LOCATION_HAND 的 count>6 时把整排压进固定区间
                // t->X=1.9+seq*4.0/(count-1)：张数多时间距同步收窄、层叠面积增大，
                // 但保留最小可见露出量 HAND_MIN_GAP，不完全遮挡其余手卡
                if (count > 1) {
                    float band = (fx(7.3f) - GameField.PILE_W / 2f)
                            - (fx(0.6f) + GameField.PILE_W / 2f);
                    float fit = (band - 0.8f) / (count - 1); // 0.8 = 手卡卡宽（世界单位）
                    spacing = Math.max(Math.min(HAND_MIN_GAP, fit), Math.min(spacing, fit));
                }
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

    /** 手卡间距：小于7张保留些许间距(0.95>卡宽0.8)，大于等于7张开始层叠，越多越密；
     *  最终间距另受手牌整排在侧列格带宽内的封顶限制（见 getCardLocation LOCATION_HAND） */
    private static float handSpacing(int count) {
        if (count < 7) return 0.95f;
        return Math.max(0.55f, Math.min(0.72f, 5.0f / count));
    }

    /**
     * 忠实移植 ClientField::GetChainLocation（client_field.cpp L761-816）：连锁序号 / chain 旋转图标
     * 的锚点坐标（{x, y, z}，与卡片落点同一 fx 映射的引擎世界坐标，绘制时由 CardOverlayRenderer 统一
     * mirrorX 翻转）。关键差异——{@code LOCATION_HAND} 用手卡固定锚点而**非发动卡当前 xyz**：
     * 从手卡发动并支付代价把该卡送去墓地/除外/回卡组等一切使其离开手牌的场合，卡片对象会飞向落堆
     * 位置，若图标跟随卡片则连锁数字会跟着跑到墓地/除外区；按需求锚定在「发动方魔法陷阱区后中央」
     * （X=场地中心 fx(3.95)、Y=魔陷行靠本方边缘一侧 3.15、Z=0.03），使图标脱离卡片对象、卡离场也不移动。
     * 其余区域与 C++ 同源：MZONE/SZONE 取格子矩形中心、Z=0.03；DECK/GRAVE/REMOVED/EXTRA 取堆叠区
     * 矩形中心、Z=张数×0.01+0.03。location&0x7f 为 0 或 OVERLAY(0x80) 等无对应锚点时返回 null，
     * 调用方回退用卡片当前坐标。
     */
    float[] getChainLocation(int controler, int location, int sequence) {
        int loc = location & 0x7f;
        float[] t = new float[3];
        switch (loc) {
            case 0x01: { // LOCATION_DECK
                float[] pr = getPileRect(controler, 0x01);
                if (pr == null) return null;
                t[0] = pr[0];
                t[1] = pr[1];
                t[2] = field.getCardCount(controler, 0x01) * 0.01f + 0.03f;
                return t;
            }
            case 0x02: { // LOCATION_HAND：固定锚点（魔陷区后中央），不随卡片对象离场移动
                t[0] = fx(3.95f);
                t[1] = controler == 0 ? 3.15f : -3.15f;
                t[2] = 0.03f;
                return t;
            }
            case 0x04: // LOCATION_MZONE
            case 0x08: { // LOCATION_SZONE
                float[] zr = getZoneRect(controler, loc, sequence);
                if (zr == null) return null;
                t[0] = zr[0];
                t[1] = zr[1];
                t[2] = 0.03f;
                return t;
            }
            case 0x10: // LOCATION_GRAVE
            case 0x20: // LOCATION_REMOVED
            case 0x40: { // LOCATION_EXTRA
                float[] pr = getPileRect(controler, loc);
                if (pr == null) return null;
                t[0] = pr[0];
                t[1] = pr[1];
                t[2] = field.getCardCount(controler, loc) * 0.01f + 0.03f;
                return t;
            }
            default:
                return null;
        }
    }
}
