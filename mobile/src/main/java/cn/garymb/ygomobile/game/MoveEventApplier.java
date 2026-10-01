package cn.garymb.ygomobile.game;

import java.nio.ByteBuffer;

import cn.garymb.ygomobile.audio.SoundManager;
import ocgcore.enums.CardLocation;

/**
 * MSG_MOVE / MSG_POS_CHANGE / MSG_SET / MSG_SWAP / MSG_FIELD_DISABLED 的场地落位实现
 * （自 DuelEventHandler 平移，逻辑零改；对齐 gframe duelclient.cpp ClientAnalyze 各分支）。
 */
class MoveEventApplier {

    private final GameEngine engine;

    MoveEventApplier(GameEngine engine) {
        this.engine = engine;
    }

    void applyMove(int code, int oldCtrl, int oldLoc, int oldSeq, int oldPos,
                   int newCtrl, int newLoc, int newSeq, int position, int reason) {
        oldCtrl = engine.localPlayer(oldCtrl);
        newCtrl = engine.localPlayer(newCtrl);
        boolean oldOverlay = (oldLoc & 0x80) != 0;
        boolean newOverlay = (newLoc & 0x80) != 0;

        if (newOverlay && !oldOverlay) {
            // 作为超量素材叠放到超量怪兽下方（duelclient.cpp L3055-3095）
            GameField.ClientCard card = engine.field.getCard(oldCtrl, oldLoc & 0x7f, oldSeq);
            if (card == null) card = new GameField.ClientCard();
            if (code != 0) card.code = code;
            // 对齐 duelclient.cpp MSG_MOVE 素材入 overlay 分支（L3055-3095）：C++ 此分支绝不改写
            // pcard->position，素材保留其在场上的表侧表示，叠放后正面朝上。旧实现用协议 cp 覆盖 position
            //（该字节对 overlay 移动常为 0），使表侧素材被 isFaceUp() 判为里侧 → 「原地变背面 / 卡背」；
            // 且素材本应在怪兽格下方叠放，而非留在原格或被甩走。仅当卡片为兜底新建（position 仍为默认 0）
            // 时显式置表侧，避免渲染成卡背。
            if (card.position == 0) card.position = GameField.POS_FACEUP;
            // 宿主按消息 cl 字节动态定位（L3069 GetCard(cc, cl & 0x7f, cs)）：脚本可在怪兽尚处
            // 额外卡组时执行 Overlay，此时 cs 是 EXTRA 序号；仅当宿主已在怪兽区时播堆叠动画
            //（L3086 if (olcard->location == LOCATION_MZONE)），否则由 flushPendingOverlays 待怪兽入格补挂
            GameField.ClientCard olcard = engine.field.attachOverlayMaterial(card, oldCtrl, oldLoc & 0x7f, oldSeq,
                    newCtrl, newLoc & 0x7f, newSeq);
            if (olcard != null && olcard.location == CardLocation.MonsterZone.value()) {
                engine.field.moveCardAnimated(card, 10);
            }
        } else if (oldOverlay && !newOverlay) {
            // 超量素材离场（duelclient.cpp L3096-3124）：oldSeq=超量怪兽格、oldPos=素材序号；
            // 宿主按消息 pl 字节动态定位（GetCard(pc, pl & 0x7f, ps)）——怪兽自身 MSG_MOVE
            // 先到时宿主可能已在墓地等区域，硬编码怪兽区查找会导致素材无离场动画
            GameField.ClientCard card = engine.field.detachOverlayMaterial(
                    oldCtrl, oldLoc & 0x7f, oldSeq, oldPos, newCtrl, newLoc & 0x7f, newSeq, position);
            if (card != null) {
                if (code != 0) card.code = code;
                engine.field.moveCardAnimated(card, 10);
            }
        } else if (oldOverlay) {
            // 素材在两只超量怪兽间转移（duelclient.cpp L3125-3153）：两侧宿主同样按消息 loc 字节
            // 动态定位（GetCard(pc, pl & 0x7f, ps) / GetCard(cc, cl & 0x7f, cs)）
            GameField.ClientCard src = engine.field.getCard(oldCtrl, oldLoc & 0x7f, oldSeq);
            GameField.ClientCard dst = engine.field.getCard(newCtrl, newLoc & 0x7f, newSeq);
            if (src != null && dst != null && oldPos >= 0 && oldPos < src.overlayed.size()) {
                GameField.ClientCard m = src.overlayed.remove(oldPos);
                for (int i = 0; i < src.overlayed.size(); i++) {
                    GameField.ClientCard s = src.overlayed.get(i);
                    if (s == null) continue;
                    s.sequence = i;
                    engine.field.moveCardAnimated(s, 2);
                }
                // 源宿主素材减少 → 堆顶下降，宿主回落（目标 Z = 0.02+0.01×素材数）
                if (src.location == CardLocation.MonsterZone.value())
                    engine.field.moveCardAnimated(src, 2);
                if (m != null) {
                    dst.overlayed.add(m);
                    m.overlayTarget = dst;
                    m.controler = newCtrl;
                    m.sequence = dst.overlayed.size() - 1;
                    engine.field.moveCardAnimated(m, 10);
                    // 目标宿主素材增加 → 堆顶抬高，宿主上移
                    if (dst.location == CardLocation.MonsterZone.value())
                        engine.field.moveCardAnimated(dst, 10);
                }
            }
        } else if (newLoc == 0) {
            // 离场消失（cl==0，duelclient.cpp MSG_MOVE L2973-2990）：先从区域移除，
            // 清效果对象链接后淡出到 alpha 5（APPEAR_FRAME 帧），播完由 GameFieldMotion  purge；
            // 旧实现直接丢弃卡片，场上/墓地卡片消失没有任何淡出过程
            GameField.ClientCard card = engine.field.removeCard(oldCtrl, oldLoc, oldSeq);
            if (card != null) {
                if (code != 0 && card.code != code)
                    card.code = code;
                card.clearTarget();
                card.is_hovered = false;
                engine.field.fadeCard(card, 5, GameField.APPEAR_FRAME);
                engine.field.fadingCards.add(card);
            }
        } else if (oldLoc == 0) {
            // 登场出现（pl==0，duelclient.cpp MSG_MOVE L2959-2972）：新建卡片入区后先定位到
            // 目标格，再从 alpha 5 淡入到 255（C++ GetCardLocation 置 curPos + FadeCard(255, appear)）；
            // 旧实现虽新建但无淡入，卡片从全透明区直接以实色闪现
            GameField.ClientCard card = new GameField.ClientCard();
            card.owner = newCtrl;
            card.code = code;
            card.position = position;
            engine.field.addCard(newCtrl, newLoc, newSeq, card);
            engine.field.setCardPos(card);
            card.curAlpha = 5;
            engine.field.fadeCard(card, 255, GameField.APPEAR_FRAME);
        } else {
            GameField.ClientCard card = engine.field.getCard(oldCtrl, oldLoc, oldSeq);
            if (card == null) card = new GameField.ClientCard();
            // 对齐 duelclient.cpp MSG_MOVE L2994：仅 code!=0 或回额外卡组(cl==0x40，服务端里侧回插
            // 时 code=0)才 SetCode；SetCode(0) 会把原卡码存入 chain_code（飞行中仍按原卡面绘制），
            // 旧实现无条件覆写 code 使其它隐藏信息移动误清卡码
            if (card.code != code && (code != 0 || newLoc == 0x40))
                card.setCode(code);
            // C++ L3018-3020 时序：RemoveCard → 改 position → AddCard。旧实现先覆写 position
            // 再移除，使额外卡组抽出时 removeCard(0x40) 的 isFaceUp 判定读到新值，
            // extraPCount 不递减而漂移；后续里侧回插点 (count-extraPCount) 落在错误层序，
            // 堆叠顺序与服务端分叉 → 额外堆顶部错卡、无法点击弹命令菜单
            engine.field.removeCard(oldCtrl, oldLoc, oldSeq);
            card.position = position;
            engine.field.addCard(newCtrl, newLoc, newSeq, card);
            // 同区重排（duelclient.cpp MSG_MOVE L3022-3030：pl==cl && pc==cc && cl&0x71）：
            // 先 5 帧每帧横移 ±0.3（对方手卡向右、我方向左抖开），再 5 帧回到新位置，
            // 形成 gframe 标志性的抽卡/缩手抖动；animJitterX 驱动两阶段轨迹（合并 10 帧）。
            // 带超量素材的怪兽移动到怪兽区时（L3032-3046），素材先同帧飞向新格下方重排
            //（逐素材 MoveCard(10)），WaitFrameSignal(10) 素材全部到位后本体才落上去
            //（本体延迟 10 帧）——消除「素材盖在怪兽上面」的共面观感；其余普通移动本体
            // 10 帧。addCard 0x04 内的 flushPendingOverlays 已把先到的待挂素材挂入 overlayed，此处一并跟动。
            if (oldLoc == newLoc && oldCtrl == newCtrl && (newLoc & 0x71) != 0) {
                // 堆叠区（DECK/GRAVE/REMOVED/EXTRA，0x71）卡发动/成为对象时同区重序：
                // jitterX 随动画启动原子设置（先动画后赋值的旧写法会被渲染线程抢先以
                // jitter=0 收敛，滑出被吞）
                engine.field.moveCardAnimated(card, 10, 0, oldCtrl == 1 ? 0.3f : -0.3f);
            } else if (newLoc == CardLocation.MonsterZone.value() && !card.overlayed.isEmpty()) {
                engine.field.moveOverlayMaterials(card, 10);
                engine.field.moveCardAnimated(card, 10, 10);
            } else {
                // 飞行起点兜底：异常/新建路径的卡从未定位过（cur*=0）时先从旧区域
                // 落位，避免从世界原点飞向墓地/除外而无可见飞行过程
                if (card.curX == 0f && card.curY == 0f && card.curZ == 0f && oldLoc != 0)
                    engine.field.setCardPosForMove(card, oldCtrl, oldLoc & 0x7f, oldSeq);
                engine.field.moveCardAnimated(card, 10);
            }
        }

        // 手卡增删后重排双方手卡（数量变化 → 间距变化）
        if ((oldLoc & 0x7f) == CardLocation.Hand.value() || (newLoc & 0x7f) == CardLocation.Hand.value()) {
            engine.field.updateHandLayout(0, 10);
            engine.field.updateHandLayout(1, 10);
        }
        // 卡片从任意非手卡区域（卡组 / 墓地 / 除外 / 额外 / 场上 / 超量素材）经效果加入手卡时，
        // 把入手的卡亮出到手牌展示片刻。普通抽卡走 MSG_DRAW 不经本 MSG_MOVE 分支，故这里不会
        // 误挂在正常抽卡上；newCtrl 已在方法开头经 localPlayer 转为本地视角索引。
        // 揭示范围与服务端解遮蔽条件严格对齐（DuelAnalyzer MSG_MOVE：toHand = cl&HAND && !(pl&HAND)
        // 即对「任意非手卡→手卡」的移动到对手公开真实卡码），旧实现只覆盖卡组/墓地/除外/额外
        // 四个来源，导致对方「场上怪兽/魔陷回手」「超量素材回手」等入手不播揭示动画、直接背面入手
        //（用户反馈：对面加入手卡的卡必须全部展示动画）。洗切不在此处播放：引擎会为「非抽卡入手」
        // 置 shuffle_hand_check 并发出 MSG_SHUFFLE_HAND，由 DeckHandMotionManager.applyShuffleHand 唯一播放一次。
        {
            int oldLocBase = oldLoc & 0x7f;
            int newLocBase = newLoc & 0x7f;
            if (newLocBase == CardLocation.Hand.value() && oldLocBase != CardLocation.Hand.value()) {
                // 只对本次入手的那张卡揭示：对方卡从卡背翻到正面供对手确认、我方卡本就正面
                // 不翻给对方看，两者均施加行进蚂蚁线高亮，展示结束后由引擎的洗切接管
                GameField.ClientCard arriving = engine.field.getCard(newCtrl, newLocBase, newSeq);
                engine.deckMotion.applyMoveToHandReveal(newCtrl, arriving);
            }
        }
        // 音效严格对齐 duelclient.cpp MSG_MOVE L2952-2957：仅在真正发生移动（pl!=cl）时，
        // 除外（目标含 LOCATION_REMOVED=0x20）播 BANISHED，否则因效果破坏（REASON_DESTROY=0x2）播 DESTROYED。
        // C++ 此分支不要求目的地是墓地（破坏回手/回卡组等同样播 DESTROYED），也没有 SUMMON 分支——
        // 召唤/特殊召唤音效由 MSG_SUMMONING/MSG_SPSUMMONING（SummonAnimationManager）负责，此处重复播会错音。
        if (newLoc != oldLoc) {
            if ((newLoc & CardLocation.Removed.value()) != 0) {
                engine.soundManager.playSoundEffect(SoundManager.SFX.BANISHED);
            } else if ((reason & 0x2) != 0) {
                engine.soundManager.playSoundEffect(SoundManager.SFX.DESTROYED);
            }
        }

        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onFieldChanged();
        });
    }

    void applyPosChange(int code, int ctrl, int loc, int seq, int oldPos, int newPos) {
        ctrl = engine.localPlayer(ctrl);
        GameField.ClientCard card = engine.field.getCard(ctrl, loc, seq);
        if (card != null) {
            // 对齐 duelclient.cpp MSG_POS_CHANGE L3165-3168：正面→里侧时清除指示物与效果对象链接
            if ((oldPos & GameField.POS_FACEUP) != 0 && (newPos & GameField.POS_FACEDOWN) != 0) {
                card.counters.clear();
                card.clearTarget();
            }
            // 对齐 L3169-3171：卡码变化则更新，再写入新表示形式
            if (code != 0 && card.code != code)
                card.setCode(code);
            card.position = newPos;
            // 对齐 L3174 MoveCard(pcard, 10)：由 getCardLocation 依据新 position 求目标姿态
            //（里侧翻开 curRotY、攻击↔守备 curRotZ），normalizeAngleTarget 取最短路径，
            // 播放里侧翻开 / 攻守互转的卡片转动动画而非瞬时切换。C++ 此处不播音效，故不加声音。
            engine.field.moveCardAnimated(card, 10);
        }
        engine.hintManager.setEventString(1600, "卡片改变了表示形式");
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onFieldChanged();
        });
    }

    void applySet(int code, int ctrl, int loc, int seq) {
        ctrl = engine.localPlayer(ctrl);
        // 对齐 gframe duelclient.cpp MSG_SET（L3179-3189）：仅播音效 + 事件串，绝不新建/替换卡片。
        // 卡片已由先到的 MSG_MOVE(onMove) 放入并定位到格子；旧实现在此 new 了一张 cur*=0 的卡，
        // 而 addCard 对 MZONE/SZONE 只做 list.set 不调 setCardPos，新卡落在世界原点、与底板共面被
        // 深度吞掉，于是里侧守备怪兽 / 盖放魔陷卡都看不到卡背矩形。
        GameField.ClientCard card = engine.field.getCard(ctrl, loc, seq);
        if (card != null && card.curX == 0f && card.curY == 0f && card.curZ == 0f) {
            // 兜底：极少数回放/重载路径卡已在列表却从未定位，补一次定位（不覆盖 code/position）
            engine.field.moveCardAnimated(card, 1);
        }
        engine.soundManager.playSoundEffect(SoundManager.SFX.SET);
        engine.hintManager.setEventString(1601, "盖放了卡片");
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onFieldChanged();
        });
    }

    void applySwap(int c1ctrl, int c1loc, int c1seq, int c2ctrl, int c2loc, int c2seq) {
        c1ctrl = engine.localPlayer(c1ctrl);
        c2ctrl = engine.localPlayer(c2ctrl);
        GameField.ClientCard c1 = engine.field.getCard(c1ctrl, c1loc, c1seq);
        GameField.ClientCard c2 = engine.field.getCard(c2ctrl, c2loc, c2seq);
        engine.field.addCard(c1ctrl, c1loc, c1seq, c2);
        engine.field.addCard(c2ctrl, c2loc, c2seq, c1);
        // 对齐 duelclient.cpp MSG_SWAP L3210-3215：互换后两本体及各自超量素材全部同帧
        // MoveCard(10)——旧实现一帧动画都不播，卡片瞬移且带素材怪兽的素材留在原地。
        // 素材目标位由 getCardLocation 依 overlayTarget 实时求出，本体已入新格则飞向新格下方。
        if (c1 != null) engine.field.moveCardAnimated(c1, 10);
        if (c2 != null) engine.field.moveCardAnimated(c2, 10);
        engine.field.moveOverlayMaterials(c1, 10);
        engine.field.moveOverlayMaterials(c2, 10);
        engine.hintManager.setEventString(1602, "卡的控制权改变了");
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onFieldChanged();
        });
    }

    void applyFieldDisabled(int disabledMask) {
        // 对齐 duelclient.cpp MSG_FIELD_DISABLED L3226-3231：读入协议侧掩码后，
        // 本地为后攻（!dInfo.isFirst）时高低 16 位换位（协议 p0/p1 ↔ 本地 我方/对方），
        // 存 dField.disabled_field 供交叉线绘制；MSG_SWAP 时由 swapField 同步换位
        int disabled = disabledMask;
        if (!engine.isDuelFirst()) disabled = (disabled >>> 16) | (disabled << 16);
        engine.field.disabledField = disabled & 0xFFFFFFFFL;
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onFieldChanged();
        });
    }
}
