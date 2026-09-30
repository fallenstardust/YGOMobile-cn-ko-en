package cn.garymb.ygomobile.render;

import android.opengl.Matrix;

import java.util.List;

import cn.garymb.ygomobile.game.GameField;

/**
 * 卡片绘制核心（自 GameFieldView 平移，逻辑零改）：位置取 getCardLocation 动画值并做 X 镜像，
 * 与 ClientField::GetCardLocation 数值完全一致。持视图反向引用读取选中态/相机/纹理缓存，
 * GL 图元经 view.quad 调用；handY/handLift/handLiftZ/buildCardModel/isFrontFacing 供
 * FieldTouchPicker 与 SelectionOutlineRenderer 等协作类复用（视图留薄委托）。
 */
class FieldCardRenderer {

    // 选中手卡抬高量
    private static final float HAND_LIFT = 0.3f;

    private final GameFieldView view;

    FieldCardRenderer(GameFieldView view) {
        this.view = view;
    }

    /**
     * 构建卡片模型矩阵（drawCard 与选择轮廓共用，避免姿态计算分叉）：
     * 手卡走相机 billboard，场上卡按 Y→X→Z 旋转，末尾统一 scale(CARD_W, CARD_H)；
     * 手卡额外按 handFlipT 做 X 轴挤压以呈现「绕竖轴翻面」的视觉效果
     */
    void buildCardModel(GameField.ClientCard c, float[] out) {
        buildCardModel(c, out, 0f);
    }

    private void buildCardModel(GameField.ClientCard c, float[] out, float zBias) {
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
    static float handFlipSqueeze(GameField.ClientCard c) {
        if (c.handFlipT < 0f) return 1f;
        return Math.max(0.06f, Math.abs(c.handFlipT - 0.5f) * 2f);
    }

    /** 手卡当前该贴卡面还是卡背：翻面进度过半才算正面（未参与翻面时按卡码直接判定） */
    static boolean handShowsFace(GameField.ClientCard c) {
        return c.handFlipT < 0f || c.handFlipT >= 0.5f;
    }

    void drawFieldCards(GameField f) {
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
    private void drawCardList(List<GameField.ClientCard> list) {
        if (list == null) return;
        try {
            for (int i = 0, n = list.size(); i < n; i++) {
                GameField.ClientCard c;
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

    private void drawPile(List<GameField.ClientCard> pile) {
        if (pile == null) return;
        try {
            // 堆叠厚度严格对齐 C++ client_field.cpp：每张卡按真实 sequence 的线性 curZ
            //（GameFieldGeometry：0.01+0.01×seq，不封顶）逐张绘制，堆顶 z = 0.01×张数，
            // 侧视厚度随张数线性增长（60 张 ≈0.60 > 40 张 ≈0.40 ≈ 15 张 ≈0.15 的 3 倍）。
            // 飞行中的卡保留自身动画的 Z 插值（从来源堆高度飞至目标堆高度），绘出立体弧线。
            for (int i = 0, s = pile.size(); i < s; i++) {
                GameField.ClientCard c;
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
    private void drawCard(GameField.ClientCard c) {
        drawCard(c, 0f);
    }

    private void drawCard(GameField.ClientCard c, float zBias) {
        if (c == null) return;
        float alpha = Math.max(0f, Math.min(1f, c.curAlpha / 255f));
        if (alpha <= 0.01f) return;

        boolean isHand = c.location == 0x02;
        buildCardModel(c, view.mModel, zBias);
        boolean front = isFrontFacing(view.mModel);

        int glow = pickGlowColor(c);
        if (glow != 0) drawGlow(glow, alpha, front);

        int code = c.code != 0 ? c.code : (c.is_moving ? c.chain_code : 0);
        if (isHand) {
            // 手卡为 billboard，恒正面朝向相机，故正/背面不能靠面朝判定，只能由翻面进度
            // handFlipT 与卡码共同决定：翻过半（>=0.5）才贴卡面，翻面途中先绘卡背再绘卡面，
            // 与 buildCardModel 的 X 轴挤压合成「把卡翻过来」的完整过程。
            // 对齐 client_field.cpp GetCardLocation 手卡分支 L866-895：手卡正/背面最终仅由
            // code 决定（code!=0 → 正面）——录像由本地引擎重跑产生消息，双方手卡 code 均已知
            // → 对方手卡自然正面展示；实时对局服务端已把对方暗手卡 code 清零（DuelAnalyzer
            // MSG_DRAW/MSG_SHUFFLE_HAND/refreshHand），未解除遮蔽的卡仍为卡背。
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
        // 模型由 getCardLocation 的 OVERLAY 分支单独求出（恒平放朝上），故此处不依赖 position
        // 一律视为表侧；否则一个 position 未被补齐的素材占位会带着卡码却被画成卡背
        //（C++ Game::DrawCard 只看 m22 与卡码、从不读 position）
        boolean faceUp = c.isFaceUp() || c.isOverlayMaterial();
        if (!front) {
            // 背面朝向相机（盖放/守备盖放/卡组背面）：绕局部 Y 翻 180° 后绘卡背，
            // 卡背自身正面朝相机，贴图方向与 gframe 一致且不与任何面共面
            System.arraycopy(view.mModel, 0, view.mModelTmp, 0, 16);
            Matrix.rotateM(view.mModelTmp, 0, 180f, 0f, 1f, 0f);
            drawCoverQuad(view.mModelTmp, c.owner != 0, alpha, 1f, 0f);
        } else if (faceUp && code > 0) {
            int tex = obtainTexture(code, FieldGeometry.pendulumMode(c), FieldGeometry.pendulumScale(c));
            if (tex > 0) {
                view.quad.drawQuadTex(view.mModel, tex, alpha);
            } else {
                view.quad.drawQuadColor(view.mModel, 0.35f, 0.35f, 0.40f, alpha);
            }
        } else {
            // 正面朝向相机但非表侧表示（卡组顶等）：gframe 同样贴卡背材质
            drawCoverQuad(view.mModel, c.owner != 0, alpha, 1f, 0f);
        }
    }

    /**
     * 卡片正面（局部 +Z）是否朝向视点：model 第 3 列为正面法线（Z 缩放恒为 1，仍是单位向量）、
     * 第 4 列为卡片中心
     */
    boolean isFrontFacing(float[] model) {
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

    private int pickGlowColor(GameField.ClientCard c) {
        if (isSelectedCard(c)) return 0xFFFFFF00;
        if (c.is_selected) return 0xFFFFFF00;
        if (c.is_highlighting) return 0xFF00FFFF;
        if (c.is_showequip || c.is_showtarget || c.is_showchaintarget) return 0xFFFF4444;
        // is_selectable 不再绘制金色脉冲外框：候选/可发动高亮统一改由
        // SelectionOutlineRenderer.drawCardSelectOutlines 的黄色蚂蚁线轮廓承担
        return 0;
    }

    boolean isSelectedCard(GameField.ClientCard c) {
        return c.controler == view.selectedPlayer && c.location == view.selectedLocation
                && c.sequence == view.selectedSequence;
    }

    /**
     * 我方手卡后移量：基准量由 solveCamera 按俯仰角动态解算（保证不遮挡魔陷区），
     * 再叠加 XML field_hand_shift 的手动微调；对方手卡保持 gframe 原位
     */
    float handY(GameField.ClientCard c) {
        return c.curY - (c.controler == 0 ? view.cam.selfHandShift : 0f);
    }

    /**
     * 逐帧推进双方手卡的抬高动画进度：向目标（当前选中卡→1，其余→0）以恒定速率线性靠拢，
     * 速率取 1/0.083s≈12/s，对应 client_card.cpp 手卡 MoveCard(5)（60fps 下 5 帧 ≈ 0.083s 完成）。
     * 新选中的卡渐升、上一个被取消的卡渐降，二者进度独立过渡不跳变。
     */
    void updateHandLift(float dt) {
        GameField f = view.field;
        if (f == null) return;
        float step = dt * 12f;
        for (int p = 0; p < 2; p++) {
            List<GameField.ClientCard> hand = f.players[p].hand;
            if (hand == null) continue;
            try {
                for (int i = 0, n = hand.size(); i < n; i++) {
                    GameField.ClientCard c;
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
    float handLift(GameField.ClientCard c) {
        return HAND_LIFT * c.handLiftAnim;
    }

    /**
     * 选中手卡抬升的 Z 分量：沿相机 up 轴抬升，Z 分量 = mCamRot[6] × 抬升量
     */
    float handLiftZ(GameField.ClientCard c) {
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

    // 卡图 / 卡背纹理由 FieldTextureManager 提供
    private int obtainTexture(int code, int mode, int scale) {
        return view.tex.obtainTexture(code, mode, scale);
    }

    private int obtainCover(boolean opponent) {
        return view.tex.obtainCover(opponent);
    }
}
