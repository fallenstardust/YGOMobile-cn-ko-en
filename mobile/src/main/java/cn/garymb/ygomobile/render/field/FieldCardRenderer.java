package cn.garymb.ygomobile.render.field;
import cn.garymb.ygomobile.render.gamefieldview.GameFieldView;

import android.opengl.Matrix;

import java.util.List;

import cn.garymb.ygomobile.game.gamefield.GameField;
import cn.garymb.ygomobile.game.gamefield.ClientCard;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.utils.YGOUtil;

/**
 * 卡片绘制核心（自 GameFieldView 平移，逻辑零改）：位置取 getCardLocation 动画值并做 X 镜像，
 * 与 ClientField::GetCardLocation 数值完全一致。持视图反向引用读取选中态/相机/纹理缓存，
 * GL 图元经 view.quad 调用；handY/handLift/handLiftZ/buildCardModel/isFrontFacing 供
 * FieldTouchPicker 与 SelectionOutlineRenderer 等协作类复用（视图留薄委托）。
 */
public class FieldCardRenderer {

    // 选中手卡抬高量
    private static final float HAND_LIFT = 0.3f;

    // 卡片光晕色（每帧每卡调用，类级缓存资源色值）
    private static final int GLOW_YELLOW = YGOUtil.c(R.color.yellow);
    private static final int GLOW_CYAN = YGOUtil.c(R.color.pure_cyan);
    private static final int GLOW_RED = YGOUtil.c(R.color.glow_red);

    // 发动抬跳矩形边缘外发光按卡种着色（用户规格：怪兽 holo orange bright、
    // 魔法荧光绿、陷阱荧光玫红；取代旧底部白色径向光晕）
    private static final int EDGE_GLOW_MONSTER = YGOUtil.c(R.color.holo_orange_bright);
    private static final int EDGE_GLOW_SPELL = YGOUtil.c(R.color.neon_green);
    private static final int EDGE_GLOW_TRAP = YGOUtil.c(R.color.neon_rose);
    // 卡种位（ocgcore common.h TYPE_SPELL/TYPE_TRAP）：魔法/陷阱显式判位，
    // 怪兽（TYPE_MONSTER=0x1）与类型未加载的卡回退 holo 橙
    private static final int TYPE_SPELL = 0x2;
    private static final int TYPE_TRAP = 0x4;

    // 发动抬跳时卡图峰值提亮量（用户规格：亮度只增加一点点、不要明显让卡图变白）：
    // 乘法提亮峰值 +8%，强度随 bounceGlow 与抬高/边缘发光同一条正弦曲线同起同落
    private static final float BOUNCE_BRIGHT_MAX = 0.08f;

    private final GameFieldView view;

    public FieldCardRenderer(GameFieldView view) {
        this.view = view;
    }

    /**
     * 构建卡片模型矩阵（drawCard 与选择轮廓共用，避免姿态计算分叉）：
     * 手卡走相机 billboard，场上卡按 Y→X→Z 旋转，末尾统一 scale(CARD_W, CARD_H)；
     * 手卡额外按 handFlipT 做 X 轴挤压以呈现「绕竖轴翻面」的视觉效果
     */
    public void buildCardModel(ClientCard c, float[] out) {
        buildCardModel(c, out, 0f);
    }

    private void buildCardModel(ClientCard c, float[] out, float zBias) {
        Matrix.setIdentityM(out, 0);
        if (c.location == 0x02) {
            Matrix.translateM(out, 0, FieldGeometry.mirrorX(c.curX),
                    handY(c) + view.cam.mCamRot[5] * handLift(c), c.curZ + handLiftZ(c));
            Matrix.multiplyMM(view.mModelTmp, 0, out, 0, view.cam.mCamRot, 0);
            System.arraycopy(view.mModelTmp, 0, out, 0, 16);
            Matrix.scaleM(out, 0, FieldGeometry.CARD_W * handFlipSqueeze(c), FieldGeometry.CARD_H, 1f);
        } else {
            boolean isPile = c.location == 0x01 || c.location == 0x10
                    || c.location == 0x20 || c.location == 0x40;
            float jx = isPile ? ((c.sequence % 3) - 1) * 0.012f : 0f;
            float jy = isPile ? (((c.sequence / 3) % 3) - 1) * 0.012f : 0f;
            Matrix.translateM(out, 0, FieldGeometry.mirrorX(c.curX) + jx, c.curY + jy, c.curZ + zBias);
            Matrix.rotateM(out, 0, (float) Math.toDegrees(-c.curRotY), 0f, 1f, 0f);
            Matrix.rotateM(out, 0, (float) Math.toDegrees(c.curRotX), 1f, 0f, 0f);
            Matrix.rotateM(out, 0, (float) Math.toDegrees(-c.curRotZ), 0f, 0f, 1f);
            Matrix.scaleM(out, 0, FieldGeometry.CARD_W, FieldGeometry.CARD_H, 1f);
        }
    }

    /**
     * 手卡翻面的 X 轴挤压系数：handFlipT 由 1（正面）→ 0.5（侧立）→ 0（卡背）对应绕竖轴
     * 转半圈的投影宽度，先压到 0 再展开；留 0.06 下限避免完全退化成一条线（billboard 无厚度）。
     * handFlipT<0（尚未参与翻面）不挤压。
     */
    static float handFlipSqueeze(ClientCard c) {
        if (c.handFlipT < 0f) return 1f;
        return Math.max(0.06f, Math.abs(c.handFlipT - 0.5f) * 2f);
    }

    /** 手卡当前该贴卡面还是卡背：翻面进度过半才算正面（未参与翻面时按卡码直接判定） */
    static boolean handShowsFace(ClientCard c) {
        return c.handFlipT < 0f || c.handFlipT >= 0.5f;
    }

    public void drawFieldCards(GameField f) {
        for (int p = 0; p < 2; p++) {
            drawCardList(f.players[p].monsterZone);
            drawCardList(f.players[p].spellZone);
            drawPile(f.players[p].deck);
            drawPile(f.players[p].grave);
            drawPile(f.players[p].removed);
            drawPile(f.players[p].extra);
        }
        // 手卡最后绘制（半透明排序靠上）
        for (int p = 1; p >= 0; p--) {
            drawCardList(f.players[p].hand);
        }
        drawCardList(f.overlayCards);
        // 离场淡出卡已脱离区域列表（cl==0：FadeCard 播完才 RemoveCard），单独绘制直到淡出完成
        drawCardList(f.fadingCards);
    }

    /**
     * 线程安全遍历：索引式 + 全量兜底，网络线程并发增删时最多丢一帧
     */
    private void drawCardList(List<ClientCard> list) {
        if (list == null) return;
        try {
            for (int i = 0, n = list.size(); i < n; i++) {
                ClientCard c;
                try {
                    c = list.get(i);
                } catch (Throwable e) {
                    continue;
                }
                drawCard(c);
            }
        } catch (Throwable ignored) {
        }
    }

    private void drawPile(List<ClientCard> pile) {
        if (pile == null) return;
        try {
            // 堆叠厚度严格对齐 C++ client_field.cpp：每张卡按真实 sequence 的线性 curZ
            //（GameFieldGeometry：0.01+0.01×seq，不封顶）逐张绘制，堆顶 z = 0.01×张数，
            // 侧视厚度随张数线性增长（60 张 ≈0.60 > 40 张 ≈0.40 ≈ 15 张 ≈0.15 的 3 倍）。
            // 飞行中的卡保留自身动画的 Z 插值（从来源堆高度飞至目标堆高度），绘出立体弧线。
            for (int i = 0, s = pile.size(); i < s; i++) {
                ClientCard c;
                try {
                    c = pile.get(i);
                } catch (Throwable e) {
                    continue;
                }
                if (c == null) continue;
                drawCard(c);
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * 单面卡片绘制（对齐 gframe drawing.cpp：每张卡只画朝向相机的那一面）。
     * 位置取 getCardLocation 动画值并做 X 镜像；旋转按 Y→X→Z 合成、Y/Z 轴取反
     * （空间镜像使绕 Y/Z 旋转反向），保证 gframe 各位置的面朝：
     * 对方暗手牌（rotX+rotY=π）卡背朝相机、守备/盖放/堆叠区朝向均正确。
     * <p>
     * 不再正反两面同绘：两面只差 0.002 的层间距，在远视点下不足一个深度台阶，
     * 会出现“半张卡图 + 半张卡背”的 z-fighting，盖放卡还会被底板吞掉；
     * 单面绘制同时把 overdraw 减半。
     */
    private void drawCard(ClientCard c) {
        drawCard(c, 0f);
    }

    private void drawCard(ClientCard c, float zBias) {
        if (c == null) return;
        float alpha = Math.max(0f, Math.min(1f, c.curAlpha / 255f));
        if (alpha <= 0.01f) return;

        boolean isHand = c.location == 0x02;
        buildCardModel(c, view.mModel, zBias);
        boolean front = isFrontFacing(view.mModel);

        int glow = pickGlowColor(c);
        // is_highlighting（灵摆刻度闪动 / MSG_BECOME_TARGET）的青色改为围绕卡片的边缘光环：
        // 旧实现用 drawGlow 画 1.10× 的整块同色底衬，卡面淡到近透明时会突兀地露出满屏蓝底；
        // 光环只沿矩形边界发光、卡面以内透明，卡身闪没时仍余一圈青边（对齐 C++ drawing.cpp
        // L646-651 的 is_highlighting 走独立描边材质 DrawSelectionLine 而非填充底框）。
        // 亮度不随卡面淡出而取 max(alpha,0.85)：C++ 描边不受 curAlpha(dim) 影响，闪动才看得见
        if (glow != 0) {
            if (c.is_highlighting) drawHighlightGlow(glow, Math.max(alpha, 0.85f), front);
            else drawGlow(glow, alpha, front);
        }
        // 发动抬跳矩形边缘外发光（用户规格：围绕该卡矩形边缘的发光环随抬高亮起、
        // 回落恢复原状；颜色随卡种——怪兽 holo 橙/魔法荧光绿/陷阱荧光玫红）：
        // 强度 c.bounceGlow 由动画层每帧写入，与抬高量同一条正弦半周期曲线，严格同步
        if (c.is_bounce && c.bounceGlow > 0.01f) drawBounceGlow(c, c.bounceGlow, alpha, front);

        int code = c.code != 0 ? c.code : (c.is_moving ? c.chain_code : 0);
        if (isHand) {
            // 手卡为 billboard，恒正面朝向相机，故正/背面不能靠面朝判定，只能由翻面进度
            // handFlipT 与卡码共同决定：翻过半（>=0.5）才贴卡面，翻面途中先绘卡背再绘卡面，
            // 与 buildCardModel 的 X 轴挤压合成「把卡翻过来」的完整过程。
            // 对齐 client_field.cpp GetCardLocation 手卡分支 L866-895：手卡正/背面最终仅由
            // code 决定（code!=0 → 正面）。本工程自建录像经 DuelViewBroadcaster.recordFrame(full)
            // 只录未遮蔽全量帧（MSG_DRAW/MSG_MOVE/refreshHand 含双方手卡真实卡码），纯消息流
            // 回放不重跑引擎亦能把对方手卡 code 写入→正面展示，切视角后卡码随卡对象迁移仍保持正面；
            // 实况对局服务端已把对方暗手卡 code 清零（DuelAnalyzer 遮蔽分支），未解除遮蔽的卡仍为卡背。
            if (code > 0 && handShowsFace(c)) {
                int tex = obtainTexture(code, FieldGeometry.pendulumMode(c), FieldGeometry.pendulumScale(c));
                if (tex > 0) {
                    view.quad.drawQuadTex(view.mModel, tex, alpha, 0f, 1f);
                } else {
                    view.quad.drawQuadColor(view.mModel, 0.35f, 0.35f, 0.40f, alpha);
                }
            } else {
                drawCoverQuad(view.mModel, true, alpha, 0f, 1f);
            }
            return;
        }

        // 超量素材恒为表侧（引擎从不下发素材自身的姿态，只给宿主的），而叠放下层的素材
        // 模型由 getCardLocation 的 OVERLAY 分支单独求出（恒平放朝上），故素材不依赖 position
        // 也一律按朝向判定画卡面；否则一个 position 未被补齐的素材占位会带着卡码却被画成卡背
        //（C++ Game::DrawCard 只看 m22 与卡码、从不读 position，本端与之对齐）
        if (!front) {
            // 背面朝向相机（盖放/守备盖放/卡组背面）：绕局部 Y 翻 180° 后绘卡背，
            // 卡背自身正面朝相机，贴图方向与 gframe 一致且不与任何面共面
            System.arraycopy(view.mModel, 0, view.mModelTmp, 0, 16);
            Matrix.rotateM(view.mModelTmp, 0, 180f, 0f, 1f, 0f);
            // 已公开卡码的场上里侧卡：卡背与卡图交替半透明
            //（波峰：卡背淡至 60%、卡图浮现至 40%；波谷：卡背恢复实显、卡图隐去），
            // 幅度与频率均收敛（频率为格子呼吸的一半）避免造成「卡被翻开/发动」的错觉，
            // 一眼可辨这是一张背面卡
            boolean reveal = isBreathRevealCard(c);
            float wave = reveal ? revealWave() : 1f;
            drawCoverQuad(view.mModelTmp, c.owner != 0,
                    alpha * (reveal ? 1f - 0.4f * wave : 1f), 1f, 0f);
            if (reveal) {
                // 呼吸卡图统一取 displayCode()（本卡公开的卡码），与 isBreathRevealCard 的
                // displayCode()>0 判据及点击详情（CardInfoBinder.showCard）同源。
                // 不能传上方局部 code：它对已落位的遮蔽洗码卡恒为 0（局部 code 只在
                // is_moving 时取 chain_code），obtainTexture(0) 会把 unknown 卡图画成呼吸
                //（用户反馈：观战跳到当前后，里侧公开卡的呼吸动画显示 unknown）
                drawBreathReveal(c, c.displayCode(), alpha, wave);
            }
        } else if (code > 0) {
            // 正面朝向相机且已拿到卡码即贴卡面（对齐 C++ Game::DrawCard 只看 m22 与卡码）：
            // 顶卡揭示中的卡组/额外卡（top_reveal）虽非表侧但已转到面朝相机且携真实卡码，须亮出卡面；
            // 天变地异翻转后的卡组顶卡同理——它的 position 仍是里侧（ocgcore 翻转不改姿态，
            // 朝向全由 deckReversed != is_reversed 的 rotY 决定），旧的 faceUp||top_reveal 判据
            // 使它即便拿到卡码也画成卡背，故此处只按「朝向 + 有码」判定。
            // 放宽不影响任何背面卡：MZONE/SZONE 盖放、EXTRA 里侧、未翻转卡组在几何上恒为
            // !front，全部由上方卡背分支消化
            int tex = obtainTexture(code, FieldGeometry.pendulumMode(c), FieldGeometry.pendulumScale(c));
            if (tex > 0) {
                // 抬跳同步卡图微提亮（用户规格：与边缘发光同步的亮度增加再回到原亮度，
                // 只一点点不变白）：乘法提亮峰值 +8%，峰值与抬高最高点对齐、两端回原亮度；
                // 非抬跳期走原入口不受影响
                float bright = c.is_bounce ? 1f + BOUNCE_BRIGHT_MAX * c.bounceGlow : 1f;
                if (bright > 1f) {
                    view.quad.drawQuadTexBright(view.mModel, tex, alpha, bright);
                } else {
                    view.quad.drawQuadTex(view.mModel, tex, alpha);
                }
            } else {
                view.quad.drawQuadColor(view.mModel, 0.35f, 0.35f, 0.40f, alpha);
            }
        } else {
            // 正面朝向相机但尚无卡码（遮蔽的卡组顶、未下发的堆叠卡等）：C++ 此时贴的是空纹理，
            // 本端兜底绘卡背，避免翻转卡组里除顶卡外的其余卡在朝上面前出现空白矩形
            drawCoverQuad(view.mModel, c.owner != 0, alpha, 1f, 0f);
        }
    }

    /**
     * 卡片正面（局部 +Z）是否朝向视点：model 第 3 列为正面法线（Z 缩放恒为 1，仍是单位向量）、
     * 第 4 列为卡片中心
     */
    public boolean isFrontFacing(float[] model) {
        float nx = model[8], ny = model[9], nz = model[10];
        return (view.cam.camEyeX - model[12]) * nx + (view.cam.camEyeY - model[13]) * ny
                + (view.cam.camEyeZ - model[14]) * nz >= 0f;
    }

    private void drawCoverQuad(float[] model, boolean opponent, float alpha, float flipU, float flipV) {
        int coverTex = obtainCover(opponent);
        if (coverTex > 0) {
            view.quad.drawQuadTex(model, coverTex, alpha, flipU, flipV);
        } else {
            view.quad.drawQuadColor(model, 0.24f, 0.18f, 0.13f, alpha);
        }
    }

    /**
     * 里侧卡交替呼吸波：频率为格子呼吸脉冲（FieldBoardRenderer.drawZoneSlots
     * 的 {@code sin(animTimeMs * 0.002)}）的一半（乘数 0.002→0.001），
     * 更缓的明灭不致误读为翻开发动；animTimeMs 为大数值绝对时间戳，必须先以
     * double 求正弦再降回 float，防 float 丢精度使动画恒灭
     */
    private float revealWave() {
        return 0.5f + 0.5f * (float) Math.sin((double) view.animTimeMs * 0.001);
    }

        // 本卡是否为应浮现卡图的「已公开卡码的场上里侧卡」（不含手卡/堆叠区/移动中卡片）：手卡严禁使用 displayCode()，只读 code。若对手抽卡的 code 被服务器遮蔽为 0，而对象残留旧 code（如从 reuse deck card 或 setCode(0) backup），此处的 isBreathRevealCard 对 location!=0x0C 一律 false，不会触发呼吸。
    private static boolean isBreathRevealCard(ClientCard c) {
        return (c.location & 0x0C) != 0 && !c.isFaceUp() && !c.isOverlayMaterial()
                && !c.is_moving && c.displayCode() > 0;
    }

    /**
     * 已公开里侧卡的呼吸卡图叠加：画在卡背之前 0.004 的共面平面上（双翻转模型的局部
     * +z 经两次 180° 后等于世界 +z，即朝相机侧：卡背恒全 alpha 时叠在其后的卡图会被
     * 深度剔除，必须更近视点才能透出），alpha 随 reveal 呼吸波在 0↔0.4 摆动，
     * 与卡背的 1→0.6 淡出此消彼长（频率为格子呼吸的一半，见 {@link #revealWave()}）
     */
    private void drawBreathReveal(ClientCard c, int code, float alpha, float wave) {
        System.arraycopy(view.mModel, 0, view.mModelTmp, 0, 16);
        Matrix.rotateM(view.mModelTmp, 0, 180f, 0f, 1f, 0f);
        Matrix.translateM(view.mModelTmp, 0, 0f, 0f, 0.004f);
        int tex = obtainTexture(code, FieldGeometry.pendulumMode(c), FieldGeometry.pendulumScale(c));
        if (tex > 0) {
            view.quad.drawQuadTex(view.mModelTmp, tex, alpha * 0.4f * wave, 1f, 0f);
        }
    }

    private int pickGlowColor(ClientCard c) {
        if (isSelectedCard(c)) return GLOW_YELLOW;
        if (c.is_selected) return GLOW_YELLOW;
        if (c.is_highlighting) return GLOW_CYAN;
        if (c.is_showequip || c.is_showtarget || c.is_showchaintarget) return GLOW_RED;
        // is_selectable 不再绘制金色脉冲外框：候选/可发动高亮统一改由
        // SelectionOutlineRenderer.drawCardSelectOutlines 的黄色蚂蚁线轮廓承担
        return 0;
    }

    boolean isSelectedCard(ClientCard c) {
        return c.controler == view.selectedPlayer && c.location == view.selectedLocation
                && c.sequence == view.selectedSequence;
    }

    /**
     * 我方手卡后移量：基准量由 solveCamera 按俯仰角动态解算（保证不遮挡魔陷区），
     * 再叠加 XML field_hand_shift 的手动微调；对方手卡保持 gframe 原位
     */
    public float handY(ClientCard c) {
        return c.curY - (c.controler == 0 ? view.cam.selfHandShift : 0f);
    }

    /**
     * 逐帧推进双方手卡的抬高动画进度：向目标（当前选中卡→1，其余→0）以恒定速率线性靠拢，
     * 速率取 1/0.083s≈12/s，对应 client_card.cpp 手卡 MoveCard(5)（60fps 下 5 帧 ≈ 0.083s 完成）。
     * 新选中的卡渐升、上一个被取消的卡渐降，二者进度独立过渡不跳变。
     */
    public void updateHandLift(float dt) {
        GameField f = view.field;
        if (f == null) return;
        float step = dt * 12f;
        for (int p = 0; p < 2; p++) {
            List<ClientCard> hand = f.players[p].hand;
            if (hand == null) continue;
            try {
                for (int i = 0, n = hand.size(); i < n; i++) {
                    ClientCard c;
                    try {
                        c = hand.get(i);
                    } catch (Throwable e) {
                        continue;
                    }
                    if (c == null) continue;
                    float target = isSelectedCard(c) ? 1f : 0f;
                    if (c.handLiftAnim < target) {
                        c.handLiftAnim = Math.min(target, c.handLiftAnim + step);
                    } else if (c.handLiftAnim > target) {
                        c.handLiftAnim = Math.max(target, c.handLiftAnim - step);
                    }
                }
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * 选中手卡抬升量：按动画进度 c.handLiftAnim(0..1) 线性插值（对齐 drawing.cpp 手卡
     * MoveCard(5) 的线性抬升），而非按选中状态瞬时跳变；进度由 updateHandLift 逐帧推进。
     */
    public float handLift(ClientCard c) {
        return HAND_LIFT * c.handLiftAnim;
    }

    /**
     * 选中手卡抬升的 Z 分量：沿相机 up 轴抬升，Z 分量 = mCamRot[6] × 抬升量
     */
    public float handLiftZ(ClientCard c) {
        return view.cam.mCamRot[6] * handLift(c);
    }

    private void drawGlow(int color, float cardAlpha, boolean front) {
        System.arraycopy(view.mModel, 0, view.mModelTmp, 0, 16);
        // 背面朝向相机时偏移取反，保证光晕恒落在卡片之下（否则会给卡背染色）
        Matrix.translateM(view.mModelTmp, 0, view.mModelTmp, 0, 0f, 0f, front ? -0.004f : 0.004f);
        Matrix.scaleM(view.mModelTmp, 0, view.mModelTmp, 0, 1.10f, 1.10f, 1f);
        float a = cardAlpha * (0.55f + 0.30f * (float) Math.sin(view.animTimeMs * 0.005));
        view.quad.drawQuadColor(view.mModelTmp, ((color >> 16) & 0xFF) / 255f,
                ((color >> 8) & 0xFF) / 255f, (color & 0xFF) / 255f, a);
    }

    /**
     * is_highlighting 的围绕卡片边缘的青色光环（取代旧整块 1.10× 同色底衬）：经
     * GLQuadBatch.drawQuadRectEdgeGlow 以「卡片半边长=1」逐像素求到矩形边缘的距离——
     * 边界处最亮、向外连续淡到全透，卡面以内不发光，故卡身淡到近透明时只余一圈青边、
     * 不再露出满屏蓝底；深度偏移比常规 drawGlow 更深（-0.008），与抬跳光环同屏不共面 z-fighting；
     * 背面朝向相机时偏移取反，保证光环恒落在卡片之下
     */
    private void drawHighlightGlow(int color, float cardAlpha, boolean front) {
        System.arraycopy(view.mModel, 0, view.mModelTmp, 0, 16);
        Matrix.translateM(view.mModelTmp, 0, view.mModelTmp, 0, 0f, 0f, front ? -0.008f : 0.008f);
        // 晕带宽度（卡片半边长单位）固定 0.16；四边形放大倍率再留 0.06 余量使 alpha 在边界前已淡到零
        float w = 0.16f;
        float k = 1.0f + w + 0.06f;
        Matrix.scaleM(view.mModelTmp, 0, view.mModelTmp, 0, k, k, 1f);
        view.quad.drawQuadRectEdgeGlow(view.mModelTmp,
                ((color >> 16) & 0xFF) / 255f, ((color >> 8) & 0xFF) / 255f, (color & 0xFF) / 255f,
                cardAlpha, k, w);
    }

    /**
     * 发动抬跳的矩形边缘外发光（用户规格：围绕该卡矩形边缘的发光环随抬高亮起、
     * 回落恢复原状，取代旧底部白色径向光晕）：经 GLQuadBatch.drawQuadRectEdgeGlow
     * 以「卡片半边长=1」逐像素求到矩形边缘的距离——边界处最亮、向外连续淡到全透，
     * 四角圆角化无棱角，卡面以内由后绘卡图遮挡；亮度与晕带宽度均由强度驱动——
     * 抬到最高最亮最扩散，落回基准即淡到零（曲线与抬高量同源，见 ClientCard.bounceGlow）。
     * 颜色随卡种（pickBounceGlowColor）；深度偏移比常规 {@link #drawGlow} 更深（-0.008），
     * 两圈光晕同屏时不共面 z-fighting；背面朝向相机时偏移取反，保证发光恒落在卡片之下
     */
    private void drawBounceGlow(ClientCard c, float glow, float cardAlpha, boolean front) {
        System.arraycopy(view.mModel, 0, view.mModelTmp, 0, 16);
        Matrix.translateM(view.mModelTmp, 0, view.mModelTmp, 0, 0f, 0f, front ? -0.008f : 0.008f);
        // 发光带宽（卡片半边长单位）随强度由 0.14 增至 0.30；四边形放大倍率再留 0.06
        // 余量，使 alpha 在四边形边界前已淡到零，不外露硬边
        float w = 0.14f + 0.16f * glow;
        float k = 1.0f + w + 0.06f;
        Matrix.scaleM(view.mModelTmp, 0, view.mModelTmp, 0, k, k, 1f);
        int color = pickBounceGlowColor(c);
        view.quad.drawQuadRectEdgeGlow(view.mModelTmp,
                ((color >> 16) & 0xFF) / 255f, ((color >> 8) & 0xFF) / 255f, (color & 0xFF) / 255f,
                Math.min(1f, 0.95f * glow) * cardAlpha, k, w);
    }

    /** 边缘发光颜色按卡种（用户规格）：0x2 魔法→荧光绿，0x4 陷阱→荧光玫红，
     *  其余（怪兽及 type 尚未由 refresh 加载的卡回退）→holo orange bright */
    private static int pickBounceGlowColor(ClientCard c) {
        if ((c.type & TYPE_SPELL) != 0) return EDGE_GLOW_SPELL;
        if ((c.type & TYPE_TRAP) != 0) return EDGE_GLOW_TRAP;
        return EDGE_GLOW_MONSTER;
    }

    // 卡图 / 卡背纹理由 FieldTextureManager 提供
    private int obtainTexture(int code, int mode, int scale) {
        return view.tex.obtainTexture(code, mode, scale);
    }

    private int obtainCover(boolean opponent) {
        return view.tex.obtainCover(opponent);
    }
}
