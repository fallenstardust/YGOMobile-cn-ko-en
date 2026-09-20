package cn.garymb.ygomobile.render;

import android.graphics.Bitmap;
import android.opengl.GLES30;
import android.opengl.Matrix;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.List;

import cn.garymb.ygomobile.game.GameEngine;
import cn.garymb.ygomobile.game.GameField;

/**
 * 场上卡片状态叠加图标 / 连锁图标 / 攻击宣言绿色弧形流动动画（GameFieldView「场上卡片状态图标」分栏）。
 * <p>
 * 逐行对齐 gframe drawing.cpp（DrawCard 状态图标 L653-719、DrawMisc 连锁 L883-902、攻击弧
 * GenArrow + attack_sv 流动窗口），materials.cpp vSymbol/vNegate/vPScale/vChainNum。独占攻击弧
 * 3D 逐顶点色程序与动态 VAO/VBO（{@link #initArrow()} 由门面 onSurfaceCreated 调用），
 * 复用门面底层绘制原语与 {@link FieldTextureManager} 纹理缓存。
 */
final class CardOverlayRenderer {

    private final GameFieldView view;

    // === 场上卡片状态图标 / 连锁图标（materials.cpp vSymbol/vNegate/vPScale + drawing.cpp DrawCard/DrawMisc）===
    // 键域必须避开 fieldTexKey(-10..-13) 与 scale(-200..)，否则 number 等图标会误取场地底板纹理
    private static final long EQUIP_TEX_KEY = -50L;
    private static final long TARGET_TEX_KEY = -51L;
    private static final long CHAIN_TARGET_TEX_KEY = -52L;
    private static final long NEGATED_TEX_KEY = -53L;
    private static final long CHAIN_TEX_KEY = -54L;
    private static final long CHAIN_NUM_TEX_KEY = -55L;
    private static final long ATTACK_TEX_KEY = -56L;
    private static final int IC_EQUIP = 0, IC_TARGET = 1, IC_CHAIN_TARGET = 2, IC_NEGATED = 3, IC_CHAIN = 4, IC_ATTACK = 5;
    // tAttack 攻击可宣言标记（drawing.cpp L685-693）：atkdy=sin(atkframe)，atkframe+=0.1/帧@60fps≈6rad/s；
    // 相对卡片(0.7×1.0)沿场地纵轴偏移 (atkdy/4+0.35)*卡高，指向敌方一侧，对方半场绕法线转 180°
    private static final float ATTACK_BOB_RAD_PER_MS = 0.006f;
    private static final float ATTACK_OFF_BASE = 0.35f;
    // ocgcore common.h：STATUS_DISABLED=0x1 STATUS_FORBIDDEN=0x4000000 LOCATION_ONFIELD=0xc POS_FACEUP=0x5
    private static final int STATUS_DISABLED = 0x0001;
    private static final int STATUS_FORBIDDEN = 0x4000000;
    private static final int LOCATION_ONFIELD = 0x0c;
    private static final int POS_FACEUP = 0x5;
    // number.png 连锁序号精灵 5 列网格单元（对齐 materials.cpp vChainNum 0.19375×0.2421875）
    private static final float CHAIN_NUM_U_CELL = 0.19375f;
    private static final float CHAIN_NUM_V_CELL = 0.2421875f;
    // vSymbol(0.7×0.7)=满卡宽×0.7卡高；vNegate(0.5×0.5)相对卡片 0.714 宽×0.5 高、中心略偏下
    private static final float SYMBOL_H_FRAC = 0.7f;
    private static final float NEGATE_W_FRAC = 0.5f / 0.7f;
    private static final float NEGATE_H_FRAC = 0.5f;
    private static final float NEGATE_Y_OFF_FRAC = -0.03f;
    // z 层阶梯：卡片 curZ → 灵摆刻度图 +0.02 → 状态图标 +0.03（刻度+0.01）
    // → 攻击箭头 +0.04，逐层不共面防条栅
    private static final float SCALE_Z_OFF = 0.02f;
    private static final float ICON_Z_OFF = 0.03f;
    /** CardType.Pendulum 位（ocgcore.enums.CardType.Pendulum = 0x1000000） */
    private static final int TYPE_PENDULUM = 0x1000000;

    // === 攻击宣言绿色弧形流动动画（materials.cpp GenArrow + drawing.cpp L1504-1513）===
    // 逐顶点 3D 位置 + RGBA 颜色，用透视 mVP 绘制一条从攻击者越过目标、拱起于场地上方的绿带，
    // 颜色 alpha 随流动窗口沿弧滑动（对齐 attack_sv 窗口，C++ 基绿 0xc000ff00）。
    private static final String VS_ARROW =
            "#version 300 es\n" +
                    "layout(location=0) in vec3 aPos;\n" +
                    "layout(location=1) in vec4 aColor;\n" +
                    "uniform mat4 uMVP;\n" +
                    "out vec4 vColor;\n" +
                    "void main(){ vColor=aColor; gl_Position=uMVP*vec4(aPos,1.0); }\n";

    private static final String FS_ARROW =
            "#version 300 es\n" +
                    "precision mediump float;\n" +
                    "in vec4 vColor;\n" +
                    "out vec4 fragColor;\n" +
                    "void main(){ fragColor=vColor; }\n";

    // GenArrow 共 40 顶点（i=0..18 两侧绿带 + 36/37 箭头翼 + 38/39 箭头尖），stride=3+4=7 float
    private static final int ARROW_MAX_VERTS = 40;
    private static final int ARROW_STRIDE_FLOATS = 7;
    private static final long ATTACK_ARC_MS = 900L;   // 对齐 WaitFrameSignal(40)≈667ms，略放宽
    // drawing.cpp L1510：每帧仅绘制 12 个顶点（6 对绿带）的滑动窗口；此处把窗口起点按展示时长
    // 连续推进（消除 C++ attack_sv 每帧+4 离散跳档的掉帧观感），沿弧循环跳跃 3 次。
    private static final int ARROW_WINDOW_VERTS = 12;
    private static final int ARROW_JUMP_TIMES = 3;
    private static final float ARROW_COLOR_A = 0xc0 / 255f; // materials.cpp GenArrow 0xc000ff00 alpha

    // 攻击弧 3D 逐顶点色程序 / 动态 VAO-VBO / 顶点暂存缓冲（仅 GL 线程访问）
    private int arrowProg, arrowLocMVP;
    private int arrowVao, arrowVbo;
    private FloatBuffer arrowBuf;
    // 攻击弧显示期间需隐藏的 tAttack(attack.png) 浮动箭头：即当前弧线起点攻击者
    private GameField.ClientCard hideAttackCard;

    CardOverlayRenderer(GameFieldView view) {
        this.view = view;
    }

    /**
     * 攻击弧 3D 逐顶点色程序与动态 VAO/VBO（每帧 glBufferSubData 更新顶点）：由门面 onSurfaceCreated 调用。
     */
    void initArrow() {
        arrowProg = GameFieldView.createProgram(VS_ARROW, FS_ARROW);
        arrowLocMVP = GLES30.glGetUniformLocation(arrowProg, "uMVP");
        int[] avaos = new int[1], avbos = new int[1];
        GLES30.glGenVertexArrays(1, avaos, 0);
        arrowVao = avaos[0];
        GLES30.glGenBuffers(1, avbos, 0);
        arrowVbo = avbos[0];
        arrowBuf = ByteBuffer.allocateDirect(ARROW_MAX_VERTS * ARROW_STRIDE_FLOATS * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        GLES30.glBindVertexArray(arrowVao);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, arrowVbo);
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, ARROW_MAX_VERTS * ARROW_STRIDE_FLOATS * 4,
                null, GLES30.GL_DYNAMIC_DRAW);
        GLES30.glEnableVertexAttribArray(0);
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, ARROW_STRIDE_FLOATS * 4, 0);
        GLES30.glEnableVertexAttribArray(1);
        GLES30.glVertexAttribPointer(1, 4, GLES30.GL_FLOAT, false, ARROW_STRIDE_FLOATS * 4, 3 * 4);
        GLES30.glBindVertexArray(0);
    }

    /**
     * 场上卡片正上方的状态叠加图标（对齐 drawing.cpp DrawCard L653-719）。
     * 装备/对象/连锁对象/效果无效按 C++ 严格 else-if 优先级，灵摆规则>=4 最左/最右魔陷区
     * 的灵摆卡整卡叠加 lscale/rscale 刻度图片。仅遍历怪兽区/魔陷区（场上卡）。
     */
    void drawFieldCardOverlays(GameField f) {
        if (f == null) return;
        // 攻击宣言弧线显示期间隐藏攻击者的 attack.png 浮动箭头（对齐 C++：
        // MSG_ATTACK 时 attacker 的 cmdFlag 已随询问结束清空，弧线期间不再绘制 tAttack）
        hideAttackCard = null;
        if (f.arcAttacker != null) {
            long el = view.animTimeMs - f.arcStartMs;
            if (el >= 0 && el <= ATTACK_ARC_MS) hideAttackCard = f.arcAttacker;
        }
        boolean mr4 = f.dInfo.duelRule >= 4;
        for (int p = 0; p < 2; p++) {
            overlayCardList(f.players[p].monsterZone, mr4);
            overlayCardList(f.players[p].spellZone, mr4);
        }
    }

    private void overlayCardList(List<GameField.ClientCard> list, boolean mr4) {
        if (list == null) return;
        try {
            for (int i = 0, n = list.size(); i < n; i++) {
                GameField.ClientCard c;
                try {
                    c = list.get(i);
                } catch (Throwable e) {
                    continue;
                }
                if (c == null) continue;
                overlayCardStatus(c, mr4);
            }
        } catch (Throwable ignored) {
        }
    }

    private void overlayCardStatus(GameField.ClientCard c, boolean mr4) {
        if (c.is_moving) return;
        // z 层：装备/对象/连锁对象/无效图标在灵摆刻度图（lscale，curZ+0.02）上再高 0.01f
        if (c.is_showequip) {
            drawFieldIcon(c, obtainIconTexture(EQUIP_TEX_KEY, IC_EQUIP), 1f, SYMBOL_H_FRAC, 0f, ICON_Z_OFF);
        } else if (c.is_showtarget) {
            drawFieldIcon(c, obtainIconTexture(TARGET_TEX_KEY, IC_TARGET), 1f, SYMBOL_H_FRAC, 0f, ICON_Z_OFF);
        } else if (c.is_showchaintarget) {
            drawFieldIcon(c, obtainIconTexture(CHAIN_TARGET_TEX_KEY, IC_CHAIN_TARGET), 1f, SYMBOL_H_FRAC, 0f, ICON_Z_OFF);
        } else if ((c.status & (STATUS_DISABLED | STATUS_FORBIDDEN)) != 0
                && (c.location & LOCATION_ONFIELD) != 0 && (c.position & POS_FACEUP) != 0) {
            drawFieldIcon(c, obtainIconTexture(NEGATED_TEX_KEY, IC_NEGATED),
                    NEGATE_W_FRAC, NEGATE_H_FRAC, NEGATE_Y_OFF_FRAC * FieldGeometry.CARD_H, ICON_Z_OFF);
        }
        if (mr4 && (c.type & TYPE_PENDULUM) != 0 && (c.location & 0x08) != 0
                && c.isFaceUp() && (c.sequence == 0 || c.sequence == 4)) {
            boolean left = c.sequence == 0;
            int tex = obtainScaleIcon(left, clampScale(left ? c.lScale : c.rScale));
            if (tex > 0) drawFieldIcon(c, tex, 1f, 1f, 0f, SCALE_Z_OFF);
        }
        // 可攻击宣言的怪兽：在其上方绘制上下浮动的 tAttack 箭头（对齐 drawing.cpp L685-693）；
        // 攻击弧线显示期间的攻击者隐藏该贴图，改由滑动的绿色箭头动画表达
        if ((c.cmdFlag & GameEngine.COMMAND_ATTACK) != 0 && c != hideAttackCard) {
            drawAttackIcon(c);
        }
    }

    /**
     * 场上可攻击怪兽的 tAttack 动画箭头：沿场地纵轴朝敌方一侧上下浮动，对方半场绕法线转 180°，
     * 尺寸与装备/对象图标一致（vSymbol 0.7×0.7）。对齐 drawing.cpp DrawCard 攻击命令标记分支。
     */
    private void drawAttackIcon(GameField.ClientCard c) {
        int tex = obtainIconTexture(ATTACK_TEX_KEY, IC_ATTACK);
        if (tex <= 0) return;
        // animTimeMs 为绝对毫秒时间戳，须以 double 计算相位，否则 float 在 ~1e10 量级丢精度导致抖动
        float dy = (float) Math.sin((double) view.animTimeMs * ATTACK_BOB_RAD_PER_MS);
        float mag = (dy / 4f + ATTACK_OFF_BASE) * FieldGeometry.CARD_H;
        float yOff = (c.controler == 0 ? -1f : 1f) * mag;
        float rotDeg = c.controler == 0 ? 0f : 180f;
        Matrix.setIdentityM(view.mModel, 0);
        Matrix.translateM(view.mModel, 0, FieldGeometry.mirrorX(c.curX), c.curY + yOff, c.curZ + ICON_Z_OFF + 0.01f);
        if (rotDeg != 0f) Matrix.rotateM(view.mModel, 0, rotDeg, 0f, 0f, 1f);
        Matrix.scaleM(view.mModel, 0, FieldGeometry.CARD_W, FieldGeometry.CARD_H * SYMBOL_H_FRAC, 1f);
        view.drawQuadTex(view.mModel, tex, 1f);
    }

    private static int clampScale(int s) {
        return Math.max(0, Math.min(13, s));
    }

    /** 卡片正上方绘一枚世界坐标正立（不随卡片旋转）的叠加图标，尺寸按卡片足迹分数换算；
     *  zOff 区分层：灵摆刻度图 SCALE_Z_OFF，状态/无效图标 ICON_Z_OFF（=刻度+0.01，不共面） */
    private void drawFieldIcon(GameField.ClientCard c, int tex, float wFrac, float hFrac, float yOff, float zOff) {
        if (tex <= 0) return;
        Matrix.setIdentityM(view.mModel, 0);
        Matrix.translateM(view.mModel, 0, FieldGeometry.mirrorX(c.curX), c.curY + yOff, c.curZ + zOff);
        Matrix.scaleM(view.mModel, 0, FieldGeometry.CARD_W * wFrac, FieldGeometry.CARD_H * hFrac, 1f);
        view.drawQuadTex(view.mModel, tex, 1f);
    }

    /**
     * 连锁进行中的 chain 动画 + number 序号，绘制在连锁源卡片（含手卡）上方
     * （对齐 drawing.cpp DrawMisc L883-902）。世界空间绘制：图标/序号平铺于场地平面
     * （vSymbol/vChainNum 为 XY 平面四边形），chain 图标绕世界 Z 轴自旋（act_rot），
     * number 序号不自旋，与 conti_act、灵摆刻度贴图一致，随相机俯仰呈斜向透视（平行于卡片）。
     * 图标位置取 ChainInfo 的位置快照（MSG_CHAINED 时捕捉）：发动卡后续离开原位（入墓/回手等）
     * 时图标不跟随移动，停留在原地直到连锁消失（符合游戏王规则）。
     * 遍历 chains，遇 solved break。
     */
    void drawChainIcons(GameField f) {
        if (f == null) return;
        List<GameField.ChainInfo> chains = f.chains;
        if (chains == null || chains.isEmpty()) return;
        // 对齐 drawing.cpp L878：仅当连锁数 > 1，或勾选「只有连锁1也显示连锁动画」(draw_single_chain) 时才显示
        try {
            if (chains.size() <= 1
                    && cn.garymb.ygomobile.AppsSettings.get().getIntSettings("draw_single_chain", 0) != 1)
                return;
        } catch (Throwable ignored) {
        }
        int chainTex = obtainIconTexture(CHAIN_TEX_KEY, IC_CHAIN);
        int numTex = obtainChainNumberTexture();
        if (chainTex <= 0 && numTex <= 0) return;
        GLES30.glDisable(GLES30.GL_DEPTH_TEST);
        float spin = FieldGeometry.actSpinDegrees(view.animTimeMs);
        int n;
        try {
            n = chains.size();
        } catch (Throwable e) {
            n = 0;
        }
        for (int i = 0; i < n; i++) {
            GameField.ChainInfo ch;
            try {
                ch = chains.get(i);
            } catch (Throwable e) {
                continue;
            }
            if (ch == null) continue;
            if (ch.solved) break;
            GameField.ClientCard card = ch.chainCard;
            if (card == null) {
                try {
                    card = f.getCard(ch.controler, ch.location, ch.sequence);
                } catch (Throwable e) {
                    card = null;
                }
            }
            if (card == null) continue;
            // 位置快照：onChained 未捕捉到时（chainCard 当时为 null）首绘时补捕一次，
            // 此后固定在该处，不随卡片移动（入墓/回手等）而偏移
            if (!ch.iconPosCaptured) {
                ch.iconX = card.curX;
                ch.iconY = card.curY;
                ch.iconZ = card.curZ;
                ch.iconPosCaptured = true;
            }
            float cx = FieldGeometry.mirrorX(ch.iconX);
            float cy = ch.iconY;
            float cz = ch.iconZ + 0.05f;
            // chain 图标：vSymbol 0.7×0.7（=CARD_W 见方），绕场地法线（世界 Z）自旋
            if (chainTex > 0) {
                Matrix.setIdentityM(view.mModel, 0);
                Matrix.translateM(view.mModel, 0, cx, cy, cz);
                Matrix.rotateM(view.mModel, 0, spin, 0f, 0f, 1f);
                Matrix.scaleM(view.mModel, 0, FieldGeometry.CARD_W, FieldGeometry.CARD_W, 1f);
                view.drawQuadTex(view.mModel, chainTex, 0.92f);
            }
            // number 序号：vChainNum 0.7×0.7 按 it.setScale(0.6) → 0.42 见方，取 number.png 5 列网格子矩形，不自旋
            if (numTex > 0) {
                float numSz = FieldGeometry.CARD_W * 0.6f;
                float offU = CHAIN_NUM_U_CELL * (i % 5);
                float offV = CHAIN_NUM_V_CELL * (i / 5);
                Matrix.setIdentityM(view.mModel, 0);
                Matrix.translateM(view.mModel, 0, cx, cy, cz + 0.01f);
                Matrix.scaleM(view.mModel, 0, numSz, numSz, 1f);
                view.drawQuadTexUV(view.mModel, numTex, 1f, 1f, 0f, offU, offV, CHAIN_NUM_U_CELL, CHAIN_NUM_V_CELL);
            }
        }
        GLES30.glEnable(GLES30.GL_DEPTH_TEST);
    }

    /**
     * 攻击宣言绿色弧形流动动画（materials.cpp GenArrow L243-258 +
     * drawing.cpp L1504-1513 attack_sv 窗口流动 + duelclient.cpp MSG_ATTACK L3817-3866 锚点/旋转解算）。
     * 从攻击者到目标构建一条在场地上方拱起（中点最高）的弧形绿带，逐顶点 alpha 随流动窗口
     * 从攻击者向目标滑动（对应 C++ attack_sv 0→28），显示约 0.9s 后自动清除。关深度测试置顶。
     */
    void drawAttackArc(GameField f) {
        if (f == null || arrowProg == 0 || arrowBuf == null) return;
        GameField.ClientCard atk = f.arcAttacker;
        if (atk == null) return;
        long elapsed = view.animTimeMs - f.arcStartMs;
        if (elapsed < 0 || elapsed > ATTACK_ARC_MS) {
            f.arcAttacker = null;
            f.arcTarget = null;
            return;
        }
        // 攻击弧显示期间攻击者的 tAttack(attack.png) 浮动箭头我方/对方都绘制——
        // 对方攻击手收不到 battle cmd（cmdFlag 恒为 0），此处统一补绘；overlayCardStatus
        // 已用 hideAttackCard 抑制该卡常规绘制，避免我方攻击手重复叠加。
        try {
            drawAttackIcon(atk);
        } catch (Throwable ignored) {
        }
        // 弧线端点（渲染空间，X 已镜像）。有目标取目标卡当前坐标；直接攻击落到
        // 被攻击方一侧的固定点（duelclient.cpp L3850-3853：xd=场地中心 3.95，yd=ca==0?-3.5:3.5）。
        // 两端点均随攻击者 curX/curY 变化，故箭头角度天然随格子与目标实时改变。
        float ax = FieldGeometry.mirrorX(atk.curX), ay = atk.curY;
        float dx, dy;
        if (f.arcTarget != null) {
            dx = FieldGeometry.mirrorX(f.arcTarget.curX);
            dy = f.arcTarget.curY;
        } else {
            dx = FieldGeometry.mirrorX(FieldGeometry.FIELD_CENTER_X);
            dy = (atk.controler == 0) ? -3.5f : 3.5f;
        }
        float vx = ax - dx, vy = ay - dy;
        float len = (float) Math.sqrt(vx * vx + vy * vy);
        if (len < 1e-3f) {
            f.arcAttacker = null;
            f.arcTarget = null;
            return;
        }
        float sy = len * 0.5f;
        // 局部坐标轴：+Y=目标→攻击者（对应 C++ 局部 Y），X=带宽（场地平面内垂直），Z=拱起（世界 +Z）
        float uYx = vx / len, uYy = vy / len;
        float uXx = -uYy, uXy = uYx;
        float mx = (ax + dx) * 0.5f, my = (ay + dy) * 0.5f;

        // 构建与 materials.cpp GenArrow 完全一致的 40 顶点带体（顶点序 0..39），
        // 逐帧用滑动窗口只绘其中 12 个顶点，形成亮绿箭头沿弧跳跃的效果。
        arrowBuf.position(0);
        for (int i = 0; i < 18; i++) {   // 带体 18 对 = 顶点 0..35，ay=1.0..-0.7
            float ayf = 1.0f - 0.1f * i;
            float along = ayf * sy;
            float arch = 2.0f * (1.0f - ayf * ayf);   // C++ -2*(ay*ay-1)，中点最高 2.0
            float ccx = mx + uYx * along, ccy = my + uYy * along;
            putArrow(ccx + uXx * 0.1f, ccy + uXy * 0.1f, arch, 0f, 1f, 0f);
            putArrow(ccx - uXx * 0.1f, ccy - uXy * 0.1f, arch, 0f, 1f, 0f);
        }
        // 箭头翼（顶点 36/37）：i=17 处加宽到 0.2，沿轴/拱高各 -0.01（C++ vArrow[36]/[37]）
        float alongW = -0.7f * sy, archW = 2.0f * (1.0f - 0.49f) - 0.01f;
        float ccxW = mx + uYx * alongW, ccyW = my + uYy * alongW;
        putArrow(ccxW + uXx * 0.2f, ccyW + uXy * 0.2f, archW, 0.5f, 1f, 0.5f);
        putArrow(ccxW - uXx * 0.2f, ccyW - uXy * 0.2f, archW, 0.5f, 1f, 0.5f);
        // 箭头尖（顶点 38/39）：目标脚下，白色（C++ vArrow[38]/[39]=0xc0ffffff）
        putArrow(dx, dy, 0.02f, 1f, 1f, 1f);
        putArrow(dx, dy, 0.02f, 1f, 1f, 1f);

        // 滑动窗口起点连续推进（去掉 C++ attack_sv 每帧+4 的离散跳档以消除掉帧观感），
        // 展示期内沿弧循环跳跃 ARROW_JUMP_TIMES 次；sv∈[0, 40-12]=[0,28]
        double loop = (elapsed / (double) ATTACK_ARC_MS) * ARROW_JUMP_TIMES;
        double phase = loop - Math.floor(loop);              // [0,1)
        int maxSv = ARROW_MAX_VERTS - ARROW_WINDOW_VERTS;    // 28
        int sv = (int) (phase * (maxSv + 1));
        if (sv > maxSv) sv = maxSv;

        GLES30.glUseProgram(arrowProg);
        GLES30.glUniformMatrix4fv(arrowLocMVP, 1, false, view.cam.mVP, 0);
        arrowBuf.position(0);
        GLES30.glBindVertexArray(arrowVao);
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, arrowVbo);
        GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, 0, ARROW_MAX_VERTS * ARROW_STRIDE_FLOATS * 4, arrowBuf);
        GLES30.glDisable(GLES30.GL_DEPTH_TEST);
        GLES30.glDrawArrays(GLES30.GL_TRIANGLE_STRIP, sv, ARROW_WINDOW_VERTS);
        GLES30.glEnable(GLES30.GL_DEPTH_TEST);
        GLES30.glBindVertexArray(0);
    }

    /** 写入一个箭头顶点：位置（渲染空间）+ RGB + 恒定 alpha 0.75（对齐 GenArrow 0xc000ff00） */
    private void putArrow(float x, float y, float z, float r, float g, float b) {
        arrowBuf.put(x).put(y).put(z).put(r).put(g).put(b).put(ARROW_COLOR_A);
    }

    /** 场上状态/连锁图标纹理：首次异步上传，未就绪返回 -1 */
    private int obtainIconTexture(long key, final int which) {
        FieldTextureManager tex = view.tex;
        Integer id = tex.texCache().get(key);
        if (id != null) return id;
        if (!tex.beginRequest(key)) return -1;
        try {
            tex.texExecutor().execute(() -> {
                Bitmap b = null;
                try {
                    TextureLoader tl = TextureLoader.get();
                    Bitmap src;
                    switch (which) {
                        case IC_TARGET:
                            src = tl.getTargetTexture();
                            break;
                        case IC_CHAIN_TARGET:
                            src = tl.getChainTargetTexture();
                            break;
                        case IC_NEGATED:
                            src = tl.getNegatedTexture();
                            break;
                        case IC_CHAIN:
                            src = tl.getChainTexture();
                            break;
                        case IC_ATTACK:
                            src = tl.getAttackTexture();
                            break;
                        case IC_EQUIP:
                        default:
                            src = tl.getEquipTexture();
                            break;
                    }
                    if (src != null && !src.isRecycled()) b = src.copy(Bitmap.Config.ARGB_8888, false);
                } catch (Throwable ignored) {
                }
                if (b != null) tex.offerUpload(new FieldTextureManager.PendingUpload(key, b, true));
                else tex.cancelRequest(key);
            });
        } catch (Throwable t) {
            tex.cancelRequest(key);
        }
        return -1;
    }

    /** number.png 连锁序号整图纹理（5 列网格，按 UV 子矩形取样） */
    private int obtainChainNumberTexture() {
        FieldTextureManager tex = view.tex;
        Integer id = tex.texCache().get(CHAIN_NUM_TEX_KEY);
        if (id != null) return id;
        if (!tex.beginRequest(CHAIN_NUM_TEX_KEY)) return -1;
        try {
            tex.texExecutor().execute(() -> {
                Bitmap b = null;
                try {
                    Bitmap src = TextureLoader.get().getNumberTexture();
                    if (src != null && !src.isRecycled()) b = src.copy(Bitmap.Config.ARGB_8888, false);
                } catch (Throwable ignored) {
                }
                if (b != null) tex.offerUpload(new FieldTextureManager.PendingUpload(CHAIN_NUM_TEX_KEY, b, true));
                else tex.cancelRequest(CHAIN_NUM_TEX_KEY);
            });
        } catch (Throwable t) {
            tex.cancelRequest(CHAIN_NUM_TEX_KEY);
        }
        return -1;
    }

    /** 灵摆刻度图片纹理（extra/lscale_X.png|rscale_X.png），按 (left,value) 独立键异步上传 */
    private int obtainScaleIcon(final boolean left, final int value) {
        FieldTextureManager tex = view.tex;
        long key = -200L - (left ? value : value + 100);
        Integer id = tex.texCache().get(key);
        if (id != null) return id;
        if (!tex.beginRequest(key)) return -1;
        try {
            tex.texExecutor().execute(() -> {
                Bitmap b = null;
                try {
                    Bitmap src = TextureLoader.get().getScaleTexture(left, value);
                    if (src != null && !src.isRecycled()) b = src.copy(Bitmap.Config.ARGB_8888, false);
                } catch (Throwable ignored) {
                }
                if (b != null) tex.offerUpload(new FieldTextureManager.PendingUpload(key, b, true));
                else tex.cancelRequest(key);
            });
        } catch (Throwable t) {
            tex.cancelRequest(key);
        }
        return -1;
    }
}
