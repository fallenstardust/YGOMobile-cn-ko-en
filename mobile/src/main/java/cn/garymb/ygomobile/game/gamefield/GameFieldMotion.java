package cn.garymb.ygomobile.game.gamefield;
import cn.garymb.ygomobile.game.replay.ReplayPosSnap;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import ocgcore.enums.CardPosition;

import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.game.gamefield.ClientCard;
import cn.garymb.ygomobile.utils.YGOUtil;

/**
 * GameField 的动画 / HUD 协作类：LP 变化动画状态机（Game::lpframe/lpplayer/lpd/...）、
 * 时间/卡片计数显示（Game::RefreshTimeDisplay / ClientField::RefreshCardCountDisplay）、
 * 逐帧卡片移动 / 淡入淡出插值（缓动 + WaitFrameSignal 串行延迟），以及手卡布局重排。
 * 由门面 {@link GameField} 持有（field.motion），LP 状态字段与 animationSpeed 保留在门面，
 * 经包级私有直连；落点计算经门面 field.getCardLocation 转几何协作类。
 */
public class GameFieldMotion extends GameFieldMotionBase {

    /**
     * Constructor (package entry of the split chain, called by GameField;
     * the field assignment lives in the base layer).
     */
    GameFieldMotion(GameField field) {
        super(field);
    }

    // === 回放「上一步」逆向倒放（渲染层补间，不动模型字段） ===

    /** 逆向补间帧数（对齐正向 MoveCard(10)；速度随 field.animationSpeed/倍速自动同步）。 */
    protected static final int REVERSE_FRAMES = 10;
    /** 位移/旋转匹配阈值：世界距离与角度差均小于此值视为未位移（保持静止），避免无谓补间。 */
    protected static final float REVERSE_MOVE_EPS = 0.01f;

    /**
     * 抓取当前场上各区显示卡的渲染位置快照（回放「上一步」重算前记录步骤 N 的显示态）。
     * 卡组牌堆卡（{@code displayCode()==0}）与无名卡跳过——逆向起点只需紧邻的 N 显示态，
     * 落点在 N-1 权威态由 {@link #applyReverseAnimation} 重算后遍历各区（含卡组）匹配补间。
     * 在投喂线程读 {@code cur*}（GL 线程写）属良性竞争，与 {@code GameField.isAnimating()} 同口径。
     */
    public List<ReplayPosSnap> collectDisplayPositions() {
        List<ReplayPosSnap> snaps = new ArrayList<>();
        for (int p = 0; p < 2; p++) {
            collectList(snaps, field.players[p].hand);
            collectList(snaps, field.players[p].monsterZone);
            collectList(snaps, field.players[p].spellZone);
            collectList(snaps, field.players[p].grave);
            collectList(snaps, field.players[p].removed);
            collectList(snaps, field.players[p].extra);
        }
        collectList(snaps, field.overlayCards);
        return snaps;
    }

    protected static void collectList(List<ReplayPosSnap> out, List<ClientCard> list) {
        if (list == null) return;
        for (ClientCard c : list) {
            if (c == null) continue;
            int code = c.displayCode();
            if (code == 0) continue;
            out.add(new ReplayPosSnap(code, c.owner,
                    c.curX, c.curY, c.curZ, c.curRotX, c.curRotY, c.curRotZ));
        }
    }

    /**
     * 回放「上一步」逆向倒放：重算落定后 field 已是 N-1 权威态、各卡 {@code cur*} 已落位（instantPlace +
     * refreshAllCards 保证）。遍历 N-1 各区（含卡组，覆盖抽卡回卡组顶场景）显示卡，按 {@code (code, owner)}
     * 在步骤 N 快照 {@code snapN} 中就近匹配（重复码取位置最近且未消费的一条）：命中且位移或旋转超阈值时，
     * 设 {@code animFrom=快照位(起点/N显示位)、animTo=当前落定位(终点/N-1位)、cur=快照位、is_moving、
     * aniFrame=REVERSE_FRAMES}，交 GL 线程逐帧线性补间回退。匹配不到或位移/旋转≈0 的卡保持静止。
     * 仅改渲染层 {@code cur*}/动画插值字段，不动 {@code location/sequence/position} 模型字段。
     * 须在主线程调用（与正向 moveCardAnimated 同一线程设置，避免与 GL 线程推进竞态）。
     */
    public void applyReverseAnimation(List<ReplayPosSnap> snapN) {
        if (snapN == null || snapN.isEmpty()) return;
        boolean[] consumed = new boolean[snapN.size()];
        for (int p = 0; p < 2; p++) {
            reverseList(field.players[p].deck, snapN, consumed);
            reverseList(field.players[p].hand, snapN, consumed);
            reverseList(field.players[p].monsterZone, snapN, consumed);
            reverseList(field.players[p].spellZone, snapN, consumed);
            reverseList(field.players[p].grave, snapN, consumed);
            reverseList(field.players[p].removed, snapN, consumed);
            reverseList(field.players[p].extra, snapN, consumed);
        }
        reverseList(field.overlayCards, snapN, consumed);
    }

    protected void reverseList(List<ClientCard> list, List<ReplayPosSnap> snapN, boolean[] consumed) {
        if (list == null) return;
        for (ClientCard c : list) {
            if (c == null) continue;
            int code = c.displayCode();
            if (code == 0) continue;
            ReplayPosSnap snap = nearestSnap(snapN, consumed, code, c.owner, c.curX, c.curY, c.curZ);
            if (snap == null) continue;
            float posDist = dist(snap.x, snap.y, snap.z, c.curX, c.curY, c.curZ);
            boolean rotChanged = angleDelta(snap.rotX, c.curRotX) > REVERSE_MOVE_EPS
                    || angleDelta(snap.rotY, c.curRotY) > REVERSE_MOVE_EPS
                    || angleDelta(snap.rotZ, c.curRotZ) > REVERSE_MOVE_EPS;
            if (posDist <= REVERSE_MOVE_EPS && !rotChanged) continue;
            // 当前已落定位置即逆向终点（N-1 权威几何位）；快照位置为逆向起点（步骤 N 显示位）
            c.animToX = c.curX;
            c.animToY = c.curY;
            c.animToZ = c.curZ;
            c.animToRotX = c.curRotX;
            c.animToRotY = c.curRotY;
            c.animToRotZ = c.curRotZ;
            c.animFromX = snap.x;
            c.animFromY = snap.y;
            c.animFromZ = snap.z;
            c.animFromRotX = snap.rotX;
            c.animFromRotY = snap.rotY;
            c.animFromRotZ = snap.rotZ;
            // 旋转终点归一化到起点(快照)附近走最短路径（与正向 moveCardAnimated 同一处理）
            c.animToRotX = normalizeAngleTarget(snap.rotX, c.animToRotX);
            c.animToRotY = normalizeAngleTarget(snap.rotY, c.animToRotY);
            c.animToRotZ = normalizeAngleTarget(snap.rotZ, c.animToRotZ);
            // 起始显示位=快照位：下一帧 updateListAnimation 从 animFrom 起按 aniFrame 收敛到 animTo
            c.curX = snap.x;
            c.curY = snap.y;
            c.curZ = snap.z;
            c.curRotX = snap.rotX;
            c.curRotY = snap.rotY;
            c.curRotZ = snap.rotZ;
            c.animJitterX = 0f;
            c.animDelayFrame = 0f;
            c.is_fading = false;
            c.is_moving = true;
            c.animTotalFrame = REVERSE_FRAMES;
            c.aniFrame = REVERSE_FRAMES;
        }
    }

    /**
     * 卡组抖动单动画（对齐 duelclient.cpp MSG_SHUFFLE_DECK L2637-2650）：C++ 每轮为每张卡设
     * dPos=(rand*0.4-0.2)/帧、aniFrame=3 抖 3 帧，再 MoveCard(3) 回基线，共 5 轮。
     * 此处合并为一条 30 帧关键帧动画（每轮 6 帧：3 帧抖开 + 3 帧回位）：整段动画期间
     * aniFrame&gt;0，统一动画屏障（GameEngine.drainPendingMsgs）不会提前放行后续消息，
     * 避免分段落启动时动画间隙击穿闸门。随机幅度×3 帧即 C++ 的每轮总位移。
     */
    public void startDeckShake(ClientCard pcard) {
        if (pcard == null) return;
        if (field.instantPlace) {
            setCardPos(pcard);
            pcard.is_deck_shake = false;
            return;
        }
        float[] loc = field.getCardLocation(pcard);
        pcard.animToX = loc[0];
        pcard.animToY = loc[1];
        pcard.animToZ = loc[2];
        Random rnd = new Random();
        for (int i = 0; i < pcard.deckShakeDx.length; i++) {
            pcard.deckShakeDx[i] = (rnd.nextFloat() * 0.4f - 0.2f) * 3f;
        }
        pcard.is_deck_shake = true;
        pcard.animDelayFrame = 0;
        pcard.animTotalFrame = 30;
        pcard.aniFrame = 30;
    }

    /**
     * 洗手卡聚拢单动画（对齐 duelclient.cpp MSG_SHUFFLE_HAND L2662-2699）：全部手卡 5 帧
     * 向中线 X=3.9 聚拢（L2681 dPos=(3.9-curPos.X)/5）；停留段中部外部换入新卡面（对应
     * L2689-2692 SetCode）；最后 5 帧回新布局（L2694-2697 MoveCard(5)），共 26 帧。
     * C++ 在聚拢前的 5 帧翻面（L2672 dRot=(1.322/5, π/5)）在此不重建：手卡渲染是相机
     * billboard、不读 curRot*，旋转关键帧无视觉意义；洗后卡面换入时由 updateHandFlip
     * 依卡码推进 handFlipT 自然完成正面→卡背的翻面。
     */
    public void startHandShuffle(ClientCard pcard) {
        if (pcard == null) return;
        if (field.instantPlace) {
            setCardPos(pcard);
            pcard.is_hand_shuffle = false;
            return;
        }
        float[] loc = field.getCardLocation(pcard);
        pcard.hsToX = loc[0];
        pcard.hsToY = loc[1];
        pcard.hsToZ = loc[2];
        pcard.hsToRotX = loc[3];
        pcard.hsToRotY = loc[4];
        pcard.hsGatherX = 3.9f;
        pcard.is_hand_shuffle = true;
        pcard.animDelayFrame = 0;
        pcard.animTotalFrame = 26;
        pcard.aniFrame = 26;
    }

    /**
     * 卡片经效果入手的揭示：把<b>对方</b>手卡的翻面进度归零（卡背起手），随后 updateHandFlip
     * 依本端已拿到的卡码（公开来源入手的 MSG_MOVE，或随后写码的 MSG_CONFIRM_CARDS）在 5 帧内
     * 翻到正面，供确认“这张卡是什么”；私有入手（卡组抽卡/随机）在本端恒为零码不翻面，
     * 己方卡本就正面（不翻给对方看），flip=false 时不动翻面进度，展示仅由蚂蚁线高亮承担。
     * 不占用 aniFrame / 关键帧：翻面是渲染级进度（见 ClientCard.handFlipT），与卡片飞入手牌的
     * is_moving 动画可并行，也不会打断它；洗切一律等引擎的 MSG_SHUFFLE_HAND，此处不合成。
     */
    public void startHandReveal(ClientCard pcard, boolean flip) {
        if (pcard == null) return;
        if (field.instantPlace) {
            // 回放快进重排：不产生翻面过程，直接按卡码吸附终态
            pcard.handFlipT = pcard.code != 0 ? 1f : 0f;
            return;
        }
        if (flip) {
            pcard.handFlipT = 0f;
        } else if (pcard.handFlipT < 0f) {
            pcard.handFlipT = pcard.code != 0 ? 1f : 0f;
        }
    }

    /**
     * 卡组 / 额外卡组顶卡揭示翻开动画（对齐 duelclient.cpp MSG_CONFIRM_DECKTOP L2464-2481 /
     * MSG_CONFIRM_EXTRATOP L2502-2518 的逐张 dPos+dRot 翻开 + is_moving）：把顶卡从堆叠中横向
     * 滑出（shiftX 随席位，对应 C++ ±0.15）、旋转面朝相机（rotY=π→0）以露出卡面，并抬高 Z 使其
     * 凸于堆顶；期间置 {@code top_reveal=true} 使渲染层对非表侧的卡组 / 额外顶卡也贴卡面（对齐
     * C++ drawing.cpp is_moving 同绘正反两面）。frame 帧后卡片停在揭示姿态（is_moving 归零但停留段
     * 仍亮面），由调用方随后 {@link #moveCardAnimated} 转回原位盖回卡背、并在整段序列末尾清除 top_reveal。
     */
    public void revealTopCardOut(ClientCard pcard, float shiftX, int frame) {
        if (pcard == null || frame <= 0) return;
        if (field.instantPlace) {
            setCardPos(pcard);
            pcard.top_reveal = false;
            return;
        }
        float[] loc = field.getCardLocation(pcard);
        pcard.top_reveal = true;
        pcard.animJitterX = 0f;
        pcard.animDelayFrame = 0;
        pcard.animFromX = pcard.curX;
        pcard.animFromY = pcard.curY;
        pcard.animFromZ = pcard.curZ;
        // 横向滑出堆叠 + 抬高 0.06 使揭示卡凸出，避免与堆顶共面被遮挡
        pcard.animToX = loc[0] + shiftX;
        pcard.animToY = loc[1];
        pcard.animToZ = loc[2] + 0.06f;
        pcard.animFromRotX = pcard.curRotX;
        pcard.animFromRotY = pcard.curRotY;
        pcard.animFromRotZ = pcard.curRotZ;
        pcard.animToRotX = loc[3];
        // 转到面朝相机（rotY=0）露出卡面：卡背起点 rotY≈π→0 为线性最短路径，无需归一化
        pcard.animToRotY = 0f;
        pcard.animToRotZ = loc[5];
        pcard.animTotalFrame = frame;
        pcard.is_moving = true;
        pcard.aniFrame = frame;
    }

    /**
     * 场上里侧卡的确认揭示（对齐 duelclient.cpp MSG_CONFIRM_CARDS field_confirm 分支 L2580-2597
     * 的 dRot 翻开 + 停留段 is_highlighting + MoveCard 盖回）：从卡组 / 墓地 / 除外区 / 额外卡组
     * 直接里侧盖放到场上格子的卡，确认消息（MSG_CONFIRM_CARDS，本端服务器对非卡组来源双发不解
     * 遮蔽，见 DuelAnalyzer L289-295）携真实卡码到达后，在本格原地按 C++ 的 dRot 轴向把卡翻开露出
     * 卡面供双方确认（MZONE 里侧攻击与 SZONE 绕 Y 轴转 π 竖着翻开；MZONE 里侧守备绕 X 轴转 π，
     * 面朝相机但保持横置——对齐 duelclient.cpp L2584-2587，不得把横卡扶正），停留段后由调用方
     * {@code moveCardAnimated} 依新姿态目标转回卡背盖回（同 revealTopCardOut 的「翻回盖背」收尾）。
     * 起点取卡片当前姿态，对表侧卡调用为无害（其恒为 FaceUp 走 else 分支 rotY→0 即表侧本态）。
     * <p>抬高 loc[2]+0.03 使翻开段凸于格底板，避免里侧落位 Z（怪兽格含素材堆 0.02+0.01n、
     * 魔陷格 0.01）与卡面共面被深度剔除；调用方随后的 moveCardAnimated 回写权威 Z。
     */
    public void revealFieldCard(ClientCard pcard, int frame) {
        if (pcard == null || frame <= 0) return;
        if (field.instantPlace) {
            // 回放快进重排：只同步落位，不排程翻开动画
            setCardPos(pcard);
            pcard.top_reveal = false;
            return;
        }
        float[] loc = field.getCardLocation(pcard);
        pcard.top_reveal = true;
        pcard.animJitterX = 0f;
        pcard.animDelayFrame = 0;
        pcard.animFromX = pcard.curX;
        pcard.animFromY = pcard.curY;
        pcard.animFromZ = pcard.curZ;
        // 原地翻开：不平移（本格展示），仅微抬 Z 防与底板共面
        pcard.animToX = pcard.curX;
        pcard.animToY = pcard.curY;
        pcard.animToZ = loc[2] + 0.03f;
        pcard.animFromRotX = pcard.curRotX;
        pcard.animFromRotY = pcard.curRotY;
        pcard.animFromRotZ = pcard.curRotZ;
        // 翻开轴向对齐 duelclient.cpp MSG_CONFIRM_CARDS field_confirm L2580-2597：只绕单轴转 π，
        // 其余两轴保持当前值——里侧盖放 rotY≈π（守备另带 rotZ=±π/2 横置）：
        // 怪兽区里侧守备（position==0x8）绕 X 轴转 π（C++ dRot=(π/5,0,0)），卡面朝向相机但
        // 保持横置展示，绝不归零 rotZ 把横卡扶正（用户反馈：翻开确认被竖着展示）；
        // 怪兽区里侧攻击（position==0x2，C++ dRot=(0,π/5,0)）与魔陷区（L2594 同）绕 Y 轴转 π 竖着翻开
        if (pcard.location == 0x04 && pcard.position == 0x8) {
            pcard.animToRotX = pcard.curRotX + (float) Math.PI;
            pcard.animToRotY = normalizeAngleTarget(pcard.curRotY, pcard.curRotY);
            pcard.animToRotZ = normalizeAngleTarget(pcard.curRotZ, pcard.curRotZ);
        } else {
            pcard.animToRotX = normalizeAngleTarget(pcard.curRotX, pcard.curRotX);
            pcard.animToRotY = normalizeAngleTarget(pcard.curRotY, 0f);
            pcard.animToRotZ = normalizeAngleTarget(pcard.curRotZ, pcard.curRotZ);
        }
        pcard.animTotalFrame = frame;
        pcard.is_moving = true;
        pcard.aniFrame = frame;
    }

    public void setAnimationSpeed(float speed) {
        field.animationSpeed = Math.max(0.25f, speed);
    }

    /**
     * 手卡数量变化后重排该方手卡：其余卡用 frame 帧动画移到新间距位置；
     * 正在移动的卡不打断（保持自己的动画，目标位置已与新布局一致）
     */
    public void updateHandLayout(int controler, int frame) {
        List<ClientCard> hand = field.players[controler].hand;
        for (ClientCard c : hand) {
            if (c == null) continue;
            float[] loc = field.getCardLocation(c);
            // 正在移动/淡入淡出的卡不打断：淡入卡（pl==0 登场）保持自己的淡入节奏，
            // 位置由手牌布局重排动画接管会在下次重排时自然对齐；冻结待码的入手卡
            // （pendingFaceupAdd，客机遮蔽入手等确认码）保持在卡组顶坐标不被拽入手牌
            if (c.is_moving || c.is_fading || c.pendingFaceupAdd) continue;
            if (frame > 0 && (Math.abs(loc[0] - c.curX) > 0.001f
                    || Math.abs(loc[1] - c.curY) > 0.001f)) {
                moveCardAnimated(c, frame);
            } else {
                c.curX = loc[0];
                c.curY = loc[1];
                c.curZ = loc[2];
                c.curRotX = loc[3];
                c.curRotY = loc[4];
                c.curRotZ = loc[5];
            }
        }
    }
}
