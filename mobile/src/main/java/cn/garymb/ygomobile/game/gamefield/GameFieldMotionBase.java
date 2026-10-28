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
 * Base layer of the split inheritance chain (GameFieldMotionBase <- GameFieldMotion):
 * owns the field handle, the LP animation state machine, the time / card-count
 * display refreshers, the per-frame card animation engine and the move / bounce
 * primitives. The replay reverse-tween and the deck-shake / hand-shuffle / reveal
 * starters live in the leaf class.
 */
abstract class GameFieldMotionBase {

    protected final GameField field;


    /**
     * Constructor body anchor kept verbatim so the final field is
     * assigned exactly once from the leaf forwarding constructor.
     */
    protected GameFieldMotionBase(GameField field) {
        this.field = field;
    }

    /**
     * 触发 LP 变化动画（duelclient.cpp MSG_DAMAGE/RECOVER/LPUPDATE/PAY_LPCOST）
     * @param showText true=伤害/回复（先 30 帧浮字再扣减）；false=LPUPDATE/支付（立即扣减）
     */
    public void startLpChange(int player, int finalLp, int color, String text, boolean showText) {
        if (field.instantPlace) {
            // 回放快进重排：LP 直接到位，不播浮字/扣血动画（闸门不阻塞）
            field.dInfo.lp[player] = finalLp;
            field.lpPending = false;
            field.lpcstring = "";
            return;
        }
        field.lpplayer = player;
        field.lpFinal = finalLp;
        field.lpd = (field.dInfo.lp[player] - finalLp) / 10;
        if (showText && text != null) {
            field.lpccolor = color;
            field.lpcstring = text;
            field.lpDelay = 30;
            field.lpframe = 0;
        } else {
            field.lpccolor = 0;
            field.lpcstring = "";
            field.lpDelay = 0;
            field.lpframe = 10;
        }
        field.lpPending = true;
    }

    // 限时/卡数/攻击力对比色：原 0xFFRRGGBB 硬编码集中到 colors.xml（R.color.*），
    // 本类每帧刷新故缓存为类级常量，避免逐帧重复查资源
    protected static final int TIME_GREEN = YGOUtil.c(R.color.pure_green);
    protected static final int TIME_YELLOW = YGOUtil.c(R.color.yellow);
    protected static final int TIME_ORANGE = YGOUtil.c(R.color.timer_orange);
    protected static final int TIME_RED = YGOUtil.c(R.color.pure_red);
    protected static final int TIME_WHITE = YGOUtil.c(R.color.white);
    protected static final int COUNT_AHEAD = TIME_YELLOW;
    protected static final int COUNT_BEHIND = YGOUtil.c(R.color.alert_red_orange);
    protected static final int COUNT_EQUAL = TIME_WHITE;

    /** 每帧调用（等价 DrawMisc L974-979 的推进） */
    public void updateLpAnimation() {
        if (!field.lpPending) return;
        if (field.lpDelay > 0) {
            field.lpDelay--;
            if (field.lpDelay == 0) field.lpframe = 10;
            return;
        }
        if (field.lpframe > 0) {
            field.dInfo.lp[field.lpplayer] -= field.lpd;
            int a = (field.lpccolor >>> 24) - 0x19;
            if (a < 0) a = 0;
            field.lpccolor = (a << 24) | (field.lpccolor & 0x00FFFFFF);
            field.lpframe--;
        }
        if (field.lpframe <= 0) {
            field.dInfo.lp[field.lpplayer] = field.lpFinal;
            field.lpcstring = "";
            field.lpPending = false;
        }
    }

    public boolean isLpAnimating() {
        return field.lpPending;
    }

    /** Game::RefreshTimeDisplay 忠实移植（game.cpp L1679-1695） */
    public void refreshTimeDisplay() {
        for (int i = 0; i < 2; i++) {
            if (field.dInfo.timeLeft[i] > 0 && field.dInfo.timeLimit > 0) {
                if (field.dInfo.timeLeft[i] >= field.dInfo.timeLimit / 2)
                    field.dInfo.timeColor[i] = TIME_GREEN;
                else if (field.dInfo.timeLeft[i] >= field.dInfo.timeLimit / 3)
                    field.dInfo.timeColor[i] = TIME_YELLOW;
                else if (field.dInfo.timeLeft[i] >= field.dInfo.timeLimit / 6)
                    field.dInfo.timeColor[i] = TIME_ORANGE;
                else
                    field.dInfo.timeColor[i] = TIME_RED;
            } else {
                field.dInfo.timeColor[i] = TIME_WHITE;
            }
        }
    }

    public void resetTimeTick() {
        field.lastTimeTickMs = 0;
    }

    /** 本地每秒倒计时（game.cpp 主循环 L1659-1664，STOC_TIME_LIMIT 到达时被resetTimeTick 校正） */
    public void tickTime(long nowMs) {
        if (field.dInfo.timeLimit <= 0 || field.dInfo.timePlayer < 0 || field.dInfo.timePlayer > 1) return;
        if (field.lastTimeTickMs == 0) {
            field.lastTimeTickMs = nowMs;
            return;
        }
        boolean changed = false;
        while (nowMs - field.lastTimeTickMs >= 1000) {
            field.lastTimeTickMs += 1000;
            if (field.dInfo.timeLeft[field.dInfo.timePlayer] > 0) {
                field.dInfo.timeLeft[field.dInfo.timePlayer]--;
                changed = true;
            }
        }
        if (changed) refreshTimeDisplay();
    }

    /** ClientField::RefreshCardCountDisplay 忠实移植（client_field.cpp L1583-1624） */
    public void refreshCardCountDisplay() {
        for (int p = 0; p < 2; p++) {
            int count = 0;
            int total = 0;
            for (ClientCard c : field.players[p].hand) {
                if (c != null) count++;
            }
            for (ClientCard c : field.players[p].monsterZone) {
                if (c != null) {
                    count++;
                    if (c.position == CardPosition.FaceUpAttack.value() && c.attack > 0)
                        total += c.attack;
                }
            }
            for (ClientCard c : field.players[p].spellZone) {
                if (c != null) count++;
            }
            field.dInfo.cardCount[p] = count;
            field.dInfo.totalAttack[p] = total;
        }
        if (field.dInfo.cardCount[0] > field.dInfo.cardCount[1]) {
            field.dInfo.cardCountColor[0] = TIME_YELLOW;
            field.dInfo.cardCountColor[1] = COUNT_BEHIND;
        } else if (field.dInfo.cardCount[1] > field.dInfo.cardCount[0]) {
            field.dInfo.cardCountColor[1] = TIME_YELLOW;
            field.dInfo.cardCountColor[0] = COUNT_BEHIND;
        } else {
            field.dInfo.cardCountColor[0] = TIME_WHITE;
            field.dInfo.cardCountColor[1] = TIME_WHITE;
        }
        if (field.dInfo.totalAttack[0] > field.dInfo.totalAttack[1]) {
            field.dInfo.totalAttackColor[0] = TIME_YELLOW;
            field.dInfo.totalAttackColor[1] = COUNT_BEHIND;
        } else if (field.dInfo.totalAttack[1] > field.dInfo.totalAttack[0]) {
            field.dInfo.totalAttackColor[1] = TIME_YELLOW;
            field.dInfo.totalAttackColor[0] = COUNT_BEHIND;
        } else {
            field.dInfo.totalAttackColor[0] = TIME_WHITE;
            field.dInfo.totalAttackColor[1] = TIME_WHITE;
        }
    }

    public void updateCardAnimation(int frame) {
        for (int p = 0; p < 2; p++) {
            updateListAnimation(field.players[p].deck);
            updateListAnimation(field.players[p].hand);
            updateListAnimation(field.players[p].monsterZone);
            updateListAnimation(field.players[p].spellZone);
            updateListAnimation(field.players[p].grave);
            updateListAnimation(field.players[p].removed);
            updateListAnimation(field.players[p].extra);
        }
        // 手卡翻面进度逐帧推进：与 aniFrame 关键帧解耦（手卡渲染走 billboard，翻面只能是
        // handFlipT 驱动的挤压 + 换面），故本推进不受 aniFrame>0 限制，每帧都跑
        updateHandFlip(field.players[0].hand);
        updateHandFlip(field.players[1].hand);
        updateListAnimation(field.overlayCards);
        // 淡出卡已脱离区域列表（duelclient.cpp MSG_MOVE cl==0：FadeCard 播完才
        // RemoveCard+DestroyCard），单独驱动并在动画结束后从暂存列表移除
        updateListAnimation(field.fadingCards);
        for (int i = field.fadingCards.size() - 1; i >= 0; i--) {
            ClientCard c = field.fadingCards.get(i);
            if (c == null || c.aniFrame <= 0)
                field.fadingCards.remove(i);
        }
    }

    /**
     * 场地是否仍有卡片动画在播放（对齐 gframe draw loop 中 aniFrame>0 的移动/淡入淡出卡片）：
     * 遍历与 updateCardAnimation 完全相同的区域列表，任一 ClientCard 的 aniFrame>0 即视为动画进行中。
     * 供 GameEngine 统一动画屏障判断「GameFieldView 卡片移动是否播完」，实现与特效/弹窗相同的串行序列。
     * 说明：aniFrame 由 GL 渲染线程逐帧递减，本方法在主线程读取，属良性数据竞争
     * （最坏多等一个轮询周期即 16ms），无需加锁以免与渲染线程争用。
     */
    public boolean isAnimating() {
        try {
            for (int p = 0; p < 2; p++) {
                if (isListAnimating(field.players[p].deck)
                        || isListAnimating(field.players[p].hand)
                        || isListAnimating(field.players[p].monsterZone)
                        || isListAnimating(field.players[p].spellZone)
                        || isListAnimating(field.players[p].grave)
                        || isListAnimating(field.players[p].removed)
                        || isListAnimating(field.players[p].extra)) {
                    return true;
                }
            }
            return isListAnimating(field.overlayCards) || isListAnimating(field.fadingCards);
        } catch (Throwable ignored) {
            return false;
        }
    }

    protected void updateListAnimation(List<ClientCard> list) {
        for (ClientCard pcard : list) {
            // 抬跳动画用独立计数器，不占 aniFrame：循环门控放宽到两者之一进行中
            if (pcard == null || (pcard.aniFrame <= 0 && !pcard.is_bounce)) continue;
            // 启动延迟：对齐 C++ 素材先飞、本体/后续卡 WaitFrameSignal 后再动的串行时序；
            // 延迟期内 aniFrame 不扣减，统一动画屏障（isAnimating）持续等待，消息不会提前推进
            if (pcard.animDelayFrame > 0) {
                pcard.animDelayFrame -= field.animationSpeed;
                continue;
            }
            // 卡组抖动（duelclient.cpp MSG_SHUFFLE_DECK L2637-2650）：5 轮 ×(3 帧随机位移抖开
            // + 3 帧 MoveCard(3) 回基线)，三角波 u 乘每轮随机量 deckShakeDx[round]
            if (pcard.is_deck_shake) {
                int total = Math.max(1, pcard.animTotalFrame);
                int el = Math.max(0, Math.min(total, total - (int) pcard.aniFrame));
                int round = Math.min(4, el / 6);
                int f = el % 6;
                // 三角波：f 0..2 抖开(0.33→1)，f 3..5 回基线(0.67→0)，每轮末精确回到 animToX
                float u = f < 3 ? (f + 1) / 3f : (5 - f) / 3f;
                pcard.curX = pcard.animToX + pcard.deckShakeDx[round] * u;
            }
            // 洗手卡（duelclient.cpp MSG_SHUFFLE_HAND L2662-2699）：全局时序——初始停顿 5 帧
            // → 向中线 X=3.9 聚拢 5 帧 → 停留(主线程换入新卡面) → 回新布局 5 帧，共 26 帧。
            // 位置仅沿 X 移动（C++ dPos Y=0,Z=0），旋转保持布局基准；对手手卡的「翻面盖回」
            // 不再叠加 curRot* 关键帧（手卡渲染是相机 billboard，curRot* 无视觉意义），
            // 而由 updateHandFlip 依换入后的卡码推进 handFlipT 自动翻回卡背。
            if (pcard.is_hand_shuffle) {
                int total = Math.max(1, pcard.animTotalFrame);
                int el = Math.max(0, Math.min(total, total - (int) pcard.aniFrame));
                int gatherStart = 5;
                int holdStart = gatherStart + 5;
                int returnStart = total - 5;
                float curv;
                if (el <= gatherStart) curv = 0f;
                else if (el <= holdStart) curv = (el - gatherStart) / 5f;
                else if (el <= returnStart) curv = 1f;
                else curv = 1f - (el - returnStart) / (float) Math.max(1, total - returnStart);
                pcard.curX = pcard.hsToX + (pcard.hsGatherX - pcard.hsToX) * curv;
                pcard.curY = pcard.hsToY;
                pcard.curZ = pcard.hsToZ;
                pcard.curRotX = pcard.hsToRotX;
                pcard.curRotY = pcard.hsToRotY;
            }
            // 线性插值：严格对齐 drawing.cpp DrawCard 的 curPos += dPos（dPos=(target-cur)/frame）——
            // gframe 卡片移动/淡入淡出均为每帧等速线性累加，无缓动。按剩余帧比例线性求值以在
            // 变帧率下保持恒定速度（dt*60 驱动的 aniFrame 递减）。
            if (pcard.is_moving && !pcard.is_deck_shake && !pcard.is_hand_shuffle) {
                int total = Math.max(1, pcard.animTotalFrame);
                if (pcard.animJitterX != 0f) {
                    // 同区重排抖动（duelclient.cpp MSG_MOVE L3022-3030）：前 5 帧每帧恒定横移
                    // dPos=animJitterX（±0.3，方向随控制方）且 Y/Z/旋转保持原位，
                    // 后 5 帧从偏移终点线性回到目标位（MoveCard(5)）
                    int el = Math.max(0, Math.min(total, total - (int) pcard.aniFrame));
                    if (el <= 5) {
                        pcard.curX = pcard.animFromX + pcard.animJitterX * el;
                        pcard.curY = pcard.animFromY;
                        pcard.curZ = pcard.animFromZ;
                        pcard.curRotX = pcard.animFromRotX;
                        pcard.curRotY = pcard.animFromRotY;
                        pcard.curRotZ = pcard.animFromRotZ;
                    } else {
                        float e = Math.min(1f, (el - 5) / 5f);
                        float jx = pcard.animFromX + pcard.animJitterX * 5f;
                        pcard.curX = jx + (pcard.animToX - jx) * e;
                        pcard.curY = pcard.animFromY + (pcard.animToY - pcard.animFromY) * e;
                        pcard.curZ = pcard.animFromZ + (pcard.animToZ - pcard.animFromZ) * e;
                        pcard.curRotX = pcard.animFromRotX + (pcard.animToRotX - pcard.animFromRotX) * e;
                        pcard.curRotY = pcard.animFromRotY + (pcard.animToRotY - pcard.animFromRotY) * e;
                        pcard.curRotZ = pcard.animFromRotZ + (pcard.animToRotZ - pcard.animFromRotZ) * e;
                    }
                } else {
                    float t = Math.min(1f, Math.max(0f, 1f - pcard.aniFrame / (float) total));
                    float e = t;
                    pcard.curX = pcard.animFromX + (pcard.animToX - pcard.animFromX) * e;
                    pcard.curY = pcard.animFromY + (pcard.animToY - pcard.animFromY) * e;
                    pcard.curZ = pcard.animFromZ + (pcard.animToZ - pcard.animFromZ) * e;
                    pcard.curRotX = pcard.animFromRotX + (pcard.animToRotX - pcard.animFromRotX) * e;
                    pcard.curRotY = pcard.animFromRotY + (pcard.animToRotY - pcard.animFromRotY) * e;
                    pcard.curRotZ = pcard.animFromRotZ + (pcard.animToRotZ - pcard.animFromRotZ) * e;
                }
            }
            if (pcard.is_fading) {
                int total = Math.max(1, pcard.animTotalFrame);
                float t = Math.min(1f, Math.max(0f, 1f - pcard.aniFrame / (float) total));
                pcard.curAlpha = pcard.animFromAlpha
                        + (pcard.animToAlpha - pcard.animFromAlpha) * t;
            }
            // 发动效果抬跳（用户规格：明显抬高后落回原高度的短暂小动画，幅度见 BOUNCE_LIFT_Z）：
            // 独立于 aniFrame 的正弦半周期叠加在 bounceBaseZ 上，峰值居中、两端精确回基准。
            // 抬高量与发光强度 bounceGlow 取同一条正弦曲线：围绕卡片矩形边缘的外发光
            // 与抬高同步亮、回落同步淡出（用户规格，颜色随卡种见 FieldCardRenderer）。
            // 必须放在 is_moving 写 curZ 之后：同帧用「基准+抬高」覆盖插值结果——场上卡
            // 移动前后 Z 同为权威位（animFromZ==animToZ==基准），覆盖不破坏移动轨迹。
            if (pcard.is_bounce) {
                pcard.bounceFrame -= field.animationSpeed;
                float bt = pcard.bounceTotal > 0
                        ? 1f - pcard.bounceFrame / (float) pcard.bounceTotal : 1f;
                if (bt >= 1f) {
                    pcard.is_bounce = false;
                    pcard.curZ = pcard.bounceBaseZ;
                    pcard.bounceGlow = 0f;
                } else {
                    float lift = (float) Math.sin(Math.PI * Math.max(0f, bt));
                    pcard.curZ = pcard.bounceBaseZ + BOUNCE_LIFT_Z * lift;
                    pcard.bounceGlow = lift;
                }
            }
            pcard.aniFrame -= field.animationSpeed;
            if (pcard.aniFrame <= 0) {
                pcard.aniFrame = 0;
                if (pcard.is_moving) {
                    pcard.curX = pcard.animToX;
                    pcard.curY = pcard.animToY;
                    pcard.curZ = pcard.animToZ;
                    pcard.curRotX = pcard.animToRotX;
                    pcard.curRotY = pcard.animToRotY;
                    pcard.curRotZ = pcard.animToRotZ;
                }
                if (pcard.is_fading) {
                    pcard.curAlpha = pcard.animToAlpha;
                }
                pcard.is_moving = false;
                pcard.is_fading = false;
                pcard.animJitterX = 0f;
                if (pcard.is_deck_shake) {
                    // 抖动结束精确回基线（防 animationSpeed>1 跳过 u=0 帧）
                    pcard.is_deck_shake = false;
                    pcard.curX = pcard.animToX;
                    pcard.curY = pcard.animToY;
                    pcard.curZ = pcard.animToZ;
                }
                if (pcard.is_hand_shuffle) {
                    // 洗手卡结束：精确落回新布局（聚拢段未走 is_moving，此处统一 snap）
                    pcard.is_hand_shuffle = false;
                    pcard.curX = pcard.hsToX;
                    pcard.curY = pcard.hsToY;
                    pcard.curZ = pcard.hsToZ;
                    pcard.curRotX = pcard.hsToRotX;
                    pcard.curRotY = pcard.hsToRotY;
                }
                pcard.chain_code = 0;
            }
        }
    }

    protected static boolean isListAnimating(List<ClientCard> list) {
        if (list == null) return false;
        for (ClientCard pcard : list) {
            if (pcard != null && pcard.aniFrame > 0) return true;
        }
        return false;
    }

    /**
     * 全场卡片一次性落定对齐（观战跳到当前 / 回放快进的收尾门面）：所有卡吸附到
     * 目标布局并退出**全部**瞬态动画：移动/淡入淡出/卡组抖动/洗手卡聚拢/顶卡揭示/
     * 冻结待码（停在卡组顶坐标等卡码的入手卡）/翻面进度中途态一并清零到位，
     * 淡出幽灵卡（fadingCards）与入手蚂蚁线高亮整体清空——否则解冻/恢复逐帧后
     * 这些残留动画会继续上屏，造成「卡牌倾斜残留手卡上方」「手牌从卡组坐标飞入」
     * 的误导观感。同步清零 chain_code 与常规动画收尾（updateCardAnimation
     * aniFrame 归零分支）、快进落位（moveCardAnimated instantPlace 分支）保持同一语义：
     * chain_code 只服务飞行途中继续绘旧卡面，落定即失效；不在此兜底则残留瞬时码仍会被
     * displayCode() 回退读到，导致点击详情串卡。
     */
    public void refreshAllCards() {
        for (int p = 0; p < 2; p++) {
            for (ClientCard c : field.players[p].deck) {
                if (c != null) settleCard(c);
            }
            for (ClientCard c : field.players[p].hand) {
                if (c != null) settleCard(c);
            }
            for (ClientCard c : field.players[p].monsterZone) {
                if (c != null) settleCard(c);
            }
            for (ClientCard c : field.players[p].spellZone) {
                if (c != null) settleCard(c);
            }
            for (ClientCard c : field.players[p].grave) {
                if (c != null) settleCard(c);
            }
            for (ClientCard c : field.players[p].removed) {
                if (c != null) settleCard(c);
            }
            for (ClientCard c : field.players[p].extra) {
                if (c != null) settleCard(c);
            }
        }
        for (ClientCard c : field.overlayCards) {
            if (c != null) settleCard(c);
        }
        // 淡出暂存卡（已脱离区域列表的离场幽灵）与入手揭示蚂蚁线：落定收尾一并清空，
        // 否则解冻后残影继续淡出/被单独驱动，在上一步落点画面上留下多余卡影
        field.fadingCards.clear();
        field.revealHighlightCards.clear();
    }

    /** 单卡落定：吸附权威几何并退出全部瞬态动画标记（refreshAllCards 的统一执行体） */
    protected void settleCard(ClientCard c) {
        setCardPos(c);
        c.is_moving = false;
        c.chain_code = 0;
        c.aniFrame = 0;
        c.animDelayFrame = 0;
        c.animJitterX = 0f;
        c.is_fading = false;
        c.is_deck_shake = false;
        c.is_hand_shuffle = false;
        // 抬跳与落定同步收尾：吸附权威几何后清标记并把被拾高的 Z 拉回基准（setCardPos 已写回），
        // 发光强度一并归零，避免观战/快进落定时边缘发光残留
        c.is_bounce = false;
        c.bounceGlow = 0f;
        c.top_reveal = false;
        // 冻结待码的入手卡（客机遮蔽入手停在卡组顶坐标）强制落位入手，
        // 其 350ms 超时兜底回调以 pendingFaceupAdd==false 为首行早退，不再迟到拽动手牌
        c.pendingFaceupAdd = false;
        c.revealHeld = false;
        c.curAlpha = 255f;
        // 手卡翻面进度吸附到卡码决定的终态：残留中途进度会在解冻后继续翻面，
        // 看起来像手牌重新从卡组抽出/盖回
        if (c.location == 0x02)
            c.handFlipT = c.code != 0 ? 1f : 0f;
    }

    void setCardPos(ClientCard pcard) {
        float[] loc = field.getCardLocation(pcard);
        pcard.curX = loc[0];
        pcard.curY = loc[1];
        pcard.curZ = loc[2];
        pcard.curRotX = loc[3];
        pcard.curRotY = loc[4];
        pcard.curRotZ = loc[5];
        // 首次进入手牌的卡在此吸附翻面终态（addCard 的手卡分支不调 setCardPos，故主要
        // 服务回放 / initial 等直接落位路径）；已参与翻面的卡不在此抢进度，否则会把
        // startHandReveal 刚置 0 的卡背起手姿态直接抹成正面
        if (pcard.location == 0x02 && pcard.handFlipT < 0f)
            pcard.handFlipT = pcard.code != 0 ? 1f : 0f;
    }

    public void moveCardAnimated(ClientCard pcard, int frame) {
        moveCardAnimated(pcard, frame, 0, 0f);
    }

    public void moveCardAnimated(ClientCard pcard, int frame, int delay) {
        moveCardAnimated(pcard, frame, delay, 0f);
    }

    /**
     * delay 帧后启动的 frame 帧移动动画（对齐 duelclient.cpp MSG_MOVE L3032-3046：
     * 素材 MoveCard(10)+WaitFrameSignal(10) 后本体才 MoveCard(10)）。
     * jitterX 非 0 时为同区重排滑出滑回（L3025-3033：±0.3/帧滑 5 帧再 MoveCard(5) 回位），
     * 必须在 is_moving/aniFrame 曝光给渲染线程之前原子设置：旧实现先启动动画后补
     * animJitterX，渲染线程可能抢先以 jitter=0 线性收敛，滑出被吞。
     */
    public void moveCardAnimated(ClientCard pcard, int frame, int delay, float jitterX) {
        if (pcard == null || frame <= 0) return;
        if (field.instantPlace) {
            // 回放快进重排：直接落位不产生 aniFrame，动画闸门天然不阻塞
            setCardPos(pcard);
            pcard.is_moving = false;
            pcard.animDelayFrame = 0;
            pcard.animJitterX = 0f;
            // 与常规动画收尾（updateCardAnimation 中 aniFrame 归零分支 pcard.chain_code=0，
            // 对齐 drawing.cpp DrawCard L604）保持同一落定语义：chain_code 只是飞行途中
            // （is_moving）继续绘旧卡面的瞬时备份，卡片落定即失效。快进落位不产生 aniFrame，
            // 若不在此清零，本对象先前一次飞行经 setCode(0) 备份进 chain_code 的卡码会残留到
            // 落定卡上；观战跳到当前后排空多条 msg、同一对象可能先后承载不同卡，残留的
            // chain_code 遂成为「别的卡片」的卡码，使落定的里侧卡 displayCode() 取到错码 →
            // 点击详情显示成另一张卡（呼吸卡图同现 unknown）。真正公开码始终存于 code，
            // 由 updateQuery 的 isRevealedFieldFaceDown 保护不被遮蔽洗零，清 chain_code 不伤它。
            pcard.chain_code = 0;
            return;
        }
        float[] loc = field.getCardLocation(pcard);

        pcard.animJitterX = jitterX;
        pcard.animDelayFrame = Math.max(0, delay);
        pcard.animFromX = pcard.curX;
        pcard.animFromY = pcard.curY;
        pcard.animFromZ = pcard.curZ;
        pcard.animToX = loc[0];
        pcard.animToY = loc[1];
        pcard.animToZ = loc[2];

        pcard.animFromRotX = pcard.curRotX;
        pcard.animFromRotY = pcard.curRotY;
        pcard.animFromRotZ = pcard.curRotZ;
        pcard.animToRotX = normalizeAngleTarget(pcard.curRotX, loc[3]);
        pcard.animToRotY = normalizeAngleTarget(pcard.curRotY, loc[4]);
        pcard.animToRotZ = normalizeAngleTarget(pcard.curRotZ, loc[5]);

        pcard.animTotalFrame = frame;
        pcard.is_moving = true;
        pcard.aniFrame = frame;
    }

    /** 将目标角度归一化到起点 ±π 内，保证旋转走最短路径 */
    protected static float normalizeAngleTarget(float from, float to) {
        float diff = (to - from) % (float) (Math.PI * 2);
        if (diff > Math.PI) diff -= (float) (Math.PI * 2);
        if (diff < -Math.PI) diff += (float) (Math.PI * 2);
        return from + diff;
    }

    // === 发动效果抬跳小动画（用户规格） ===

    /**
     * 抬跳抬高高度：curZ 即卡片「抬高高度」轴（revealTopCardOut 用 loc[2]+0.06 抬高使揭示卡
     * 凸出即同轴佐证）。原值 0.05→0.15 后用户仍反馈不够明显，进一步提到 0.3（约卡宽三分之二，
     * 抬到最高点在画面上明显脱开卡格浮起），抬起/落回更易辨识。
     */
    public static final float BOUNCE_LIFT_Z = 0.3f;
    /** 抬跳总帧数（约 18 帧 ≈ 0.3 秒：正弦半周期拾起再落回，短暂不干扰后续操作） */
    protected static final int BOUNCE_FRAMES = 18;

    /**
     * 发动效果抬跳：卡片矩形抬高 {@link #BOUNCE_LIFT_Z} 高度并落回原高度的短暂小动画，
     * 同时围绕该卡矩形边缘的外发光随抬高一起亮一下（强度曲线 bounceGlow 与抬高量同步，见
     * updateListAnimation 抬跳段；颜色随卡种，渲染层 FieldCardRenderer 处理），供玩家视觉确认怪兽区/额外怪兽区/魔陷区是哪张卡在
     * 发动效果（触发点：DuelEventHandler.onChaining 按 location & (0x04|0x08) 判定）。
     * 独立计数器不占 aniFrame→不持统一动画闸门；可与翻面/移动动画并行（仅叠加 Z）；
     * 卡组抖动/洗手卡的关键帧轨迹整帧写 curZ，不参与以免被覆盖。
     */
    public void bounceCard(ClientCard pcard) {
        if (pcard == null || field.instantPlace) return;
        if (pcard.is_deck_shake || pcard.is_hand_shuffle) return;
        if (pcard.is_bounce) {
            // 动画途中再次发动：以原基准重新起跳，不重采基准（此刻 curZ 带拾高量）
            pcard.bounceFrame = pcard.bounceTotal;
            return;
        }
        // 基准取移动终态高度（飞行途中发动）或当前高度；场上卡两种情形 Z 都等于权威位
        pcard.bounceBaseZ = pcard.is_moving ? pcard.animToZ : pcard.curZ;
        pcard.bounceTotal = BOUNCE_FRAMES;
        pcard.bounceFrame = BOUNCE_FRAMES;
        pcard.is_bounce = true;
    }

    /** 未消费快照中按 (code, owner) 匹配、位置距离最近的一条；无匹配返回 null（就地标记消费） */
    protected static ReplayPosSnap nearestSnap(List<ReplayPosSnap> snaps, boolean[] consumed,
                                             int code, int owner, float x, float y, float z) {
        int best = -1;
        float bestDist = Float.MAX_VALUE;
        for (int i = 0; i < snaps.size(); i++) {
            if (consumed[i]) continue;
            ReplayPosSnap s = snaps.get(i);
            if (s.code != code || s.owner != owner) continue;
            float d = dist(s.x, s.y, s.z, x, y, z);
            if (d < bestDist) {
                bestDist = d;
                best = i;
            }
        }
        if (best < 0) return null;
        consumed[best] = true;
        return snaps.get(best);
    }

    protected static float dist(float x1, float y1, float z1, float x2, float y2, float z2) {
        float dx = x1 - x2, dy = y1 - y2, dz = z1 - z2;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** 两角度的最小夹角差（归一化到 [0, π]） */
    protected static float angleDelta(float a, float b) {
        float d = (a - b) % (float) (Math.PI * 2);
        if (d > Math.PI) d -= (float) (Math.PI * 2);
        if (d < -Math.PI) d += (float) (Math.PI * 2);
        return Math.abs(d);
    }

    /**
     * 逐帧推进一侧手卡的翻面进度：目标恒由卡码决定（code!=0 → 正面，否则卡背），以
     * 0.2/帧的恒定速率靠拢（5 帧翻完，对应 duelclient.cpp MSG_SHUFFLE_HAND L2672 的
     * dRot 步进 1.322/5 与 π/5）；handFlipT<0 为尚未参与翻面，首帧直接吸附目标值，
     * 因而正常抽卡 / 开局握手牌不会多出一次翻面动画。
     */
    protected void updateHandFlip(List<ClientCard> hand) {
        if (hand == null) return;
        float step = 0.2f * field.animationSpeed;
        for (ClientCard pcard : hand) {
            if (pcard == null) continue;
            float target = pcard.code != 0 ? 1f : 0f;
            if (pcard.handFlipT < 0f) {
                pcard.handFlipT = target;
                continue;
            }
            if (pcard.handFlipT < target) {
                pcard.handFlipT = Math.min(target, pcard.handFlipT + step);
            } else if (pcard.handFlipT > target) {
                pcard.handFlipT = Math.max(target, pcard.handFlipT - step);
            }
        }
    }
}
