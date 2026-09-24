package cn.garymb.ygomobile.game;

import java.util.List;
import java.util.Random;

import ocgcore.enums.CardPosition;

import cn.garymb.ygomobile.game.GameField.ClientCard;

/**
 * GameField 的动画 / HUD 协作类：LP 变化动画状态机（Game::lpframe/lpplayer/lpd/...）、
 * 时间/卡片计数显示（Game::RefreshTimeDisplay / ClientField::RefreshCardCountDisplay）、
 * 逐帧卡片移动 / 淡入淡出插值（缓动 + WaitFrameSignal 串行延迟），以及手卡布局重排。
 * 由门面 {@link GameField} 持有（field.motion），LP 状态字段与 animationSpeed 保留在门面，
 * 经包级私有直连；落点计算经门面 field.getCardLocation 转几何协作类。
 */
class GameFieldMotion {

    private final GameField field;

    GameFieldMotion(GameField field) {
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
                    field.dInfo.timeColor[i] = 0xFF00FF00;
                else if (field.dInfo.timeLeft[i] >= field.dInfo.timeLimit / 3)
                    field.dInfo.timeColor[i] = 0xFFFFFF00;
                else if (field.dInfo.timeLeft[i] >= field.dInfo.timeLimit / 6)
                    field.dInfo.timeColor[i] = 0xFFFF7F00;
                else
                    field.dInfo.timeColor[i] = 0xFFFF0000;
            } else {
                field.dInfo.timeColor[i] = 0xFFFFFFFF;
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
            field.dInfo.cardCountColor[0] = 0xFFFFFF00;
            field.dInfo.cardCountColor[1] = 0xFFFF2A00;
        } else if (field.dInfo.cardCount[1] > field.dInfo.cardCount[0]) {
            field.dInfo.cardCountColor[1] = 0xFFFFFF00;
            field.dInfo.cardCountColor[0] = 0xFFFF2A00;
        } else {
            field.dInfo.cardCountColor[0] = 0xFFFFFFFF;
            field.dInfo.cardCountColor[1] = 0xFFFFFFFF;
        }
        if (field.dInfo.totalAttack[0] > field.dInfo.totalAttack[1]) {
            field.dInfo.totalAttackColor[0] = 0xFFFFFF00;
            field.dInfo.totalAttackColor[1] = 0xFFFF2A00;
        } else if (field.dInfo.totalAttack[1] > field.dInfo.totalAttack[0]) {
            field.dInfo.totalAttackColor[1] = 0xFFFFFF00;
            field.dInfo.totalAttackColor[0] = 0xFFFF2A00;
        } else {
            field.dInfo.totalAttackColor[0] = 0xFFFFFFFF;
            field.dInfo.totalAttackColor[1] = 0xFFFFFFFF;
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

    private void updateListAnimation(List<ClientCard> list) {
        for (ClientCard pcard : list) {
            if (pcard == null || pcard.aniFrame <= 0) continue;
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
            // →(仅对手)翻面 5 帧 → 向中线 X=3.9 聚拢 5 帧 → 停留(主线程换入新卡面) → 回新布局
            // 5 帧。含翻面 global total=31（停顿5+翻面5+聚拢5+停留11+回位5），否则 26（无翻面段）。
            // 位置仅沿 X 移动（C++ dPos Y=0,Z=0），旋转保持布局基准，翻面段叠加 rotX/rotY。
            if (pcard.is_hand_shuffle) {
                int total = Math.max(1, pcard.animTotalFrame);
                int el = Math.max(0, Math.min(total, total - (int) pcard.aniFrame));
                boolean flipGlobal = total >= 31;
                int gatherStart = flipGlobal ? 10 : 5;
                int holdStart = gatherStart + 5;
                int returnStart = flipGlobal ? 26 : 21;
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
                if (pcard.hsFlip) {
                    float flipv;
                    if (el <= 5) flipv = 0f;
                    else if (el <= 10) flipv = (el - 5) / 5f;
                    else if (el <= returnStart) flipv = 1f;
                    else flipv = 1f - (el - returnStart) / (float) Math.max(1, total - returnStart);
                    pcard.curRotX += pcard.hsFlipRotX * flipv;
                    pcard.curRotY += pcard.hsFlipRotY * flipv;
                }
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

    private static boolean isListAnimating(List<ClientCard> list) {
        if (list == null) return false;
        for (ClientCard pcard : list) {
            if (pcard != null && pcard.aniFrame > 0) return true;
        }
        return false;
    }

    public void refreshAllCards() {
        for (int p = 0; p < 2; p++) {
            for (ClientCard c : field.players[p].deck) {
                if (c != null) {
                    setCardPos(c);
                    c.is_moving = false;
                }
            }
            for (ClientCard c : field.players[p].hand) {
                if (c != null) {
                    setCardPos(c);
                    c.is_moving = false;
                }
            }
            for (ClientCard c : field.players[p].monsterZone) {
                if (c != null) {
                    setCardPos(c);
                    c.is_moving = false;
                }
            }
            for (ClientCard c : field.players[p].spellZone) {
                if (c != null) {
                    setCardPos(c);
                    c.is_moving = false;
                }
            }
            for (ClientCard c : field.players[p].grave) {
                if (c != null) {
                    setCardPos(c);
                    c.is_moving = false;
                }
            }
            for (ClientCard c : field.players[p].removed) {
                if (c != null) {
                    setCardPos(c);
                    c.is_moving = false;
                }
            }
            for (ClientCard c : field.players[p].extra) {
                if (c != null) {
                    setCardPos(c);
                    c.is_moving = false;
                }
            }
        }
        for (ClientCard c : field.overlayCards) {
            if (c != null) {
                setCardPos(c);
                c.is_moving = false;
            }
        }
    }

    void setCardPos(ClientCard pcard) {
        float[] loc = field.getCardLocation(pcard);
        pcard.curX = loc[0];
        pcard.curY = loc[1];
        pcard.curZ = loc[2];
        pcard.curRotX = loc[3];
        pcard.curRotY = loc[4];
        pcard.curRotZ = loc[5];
    }

    public void moveCardAnimated(ClientCard pcard, int frame) {
        moveCardAnimated(pcard, frame, 0);
    }

    /**
     * delay 帧后启动的 frame 帧移动动画（对齐 duelclient.cpp MSG_MOVE L3032-3046：
     * 素材 MoveCard(10)+WaitFrameSignal(10) 后本体才 MoveCard(10)）。
     */
    public void moveCardAnimated(ClientCard pcard, int frame, int delay) {
        if (pcard == null || frame <= 0) return;
        if (field.instantPlace) {
            // 回放快进重排：直接落位不产生 aniFrame，动画闸门天然不阻塞
            setCardPos(pcard);
            pcard.is_moving = false;
            pcard.animDelayFrame = 0;
            return;
        }
        float[] loc = field.getCardLocation(pcard);

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
    private static float normalizeAngleTarget(float from, float to) {
        float diff = (to - from) % (float) (Math.PI * 2);
        if (diff > Math.PI) diff -= (float) (Math.PI * 2);
        if (diff < -Math.PI) diff += (float) (Math.PI * 2);
        return from + diff;
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
     * 洗手卡聚拢/翻面单动画（对齐 duelclient.cpp MSG_SHUFFLE_HAND L2662-2699）：
     * 对手手卡（flip=true，player==1 且非回放非单机）先 5 帧翻面（rotX+1.322、rotY+π 揭示
     * 新卡面）；随后全部手卡 5 帧向中线 X=3.9 聚拢（L2681 dPos=(3.9-curPos.X)/5）；
     * 停留段中部外部换入新卡面（对应 L2689-2692 SetCode）；最后 5 帧回新布局（L2694-2697
     * MoveCard(5)）。无翻面段 25 帧，含翻面段 30 帧。
     */
    public void startHandShuffle(ClientCard pcard, boolean flip) {
        if (pcard == null) return;
        if (field.instantPlace) {
            setCardPos(pcard);
            pcard.is_hand_shuffle = false;
            return;
        }
        float[] loc = field.getCardLocation(pcard);
        pcard.hsFromX = pcard.curX;
        pcard.hsFromY = pcard.curY;
        pcard.hsFromZ = pcard.curZ;
        pcard.hsFromRotX = pcard.curRotX;
        pcard.hsFromRotY = pcard.curRotY;
        pcard.hsToX = loc[0];
        pcard.hsToY = loc[1];
        pcard.hsToZ = loc[2];
        pcard.hsToRotX = loc[3];
        pcard.hsToRotY = loc[4];
        pcard.hsGatherX = 3.9f;
        pcard.hsFlipRotX = 1.322f;
        pcard.hsFlipRotY = (float) Math.PI;
        pcard.hsFlip = flip && pcard.code != 0; // L2669：仅 code!=0 的卡参与翻面
        pcard.is_hand_shuffle = true;
        pcard.animDelayFrame = 0;
        // 全局时序统一：flip 为真时整列多一段 5 帧翻面（L2677-2678 if(flip) Wait(5)）
        pcard.animTotalFrame = flip ? 31 : 26;
        pcard.aniFrame = pcard.animTotalFrame;
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
            // 位置由手牌布局重排动画接管会在下次重排时自然对齐
            if (c.is_moving || c.is_fading) continue;
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
