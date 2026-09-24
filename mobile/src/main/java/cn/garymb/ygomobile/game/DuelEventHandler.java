package cn.garymb.ygomobile.game;

import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.audio.SoundManager;
import cn.garymb.ygomobile.ui.dialogs.DuelLogDialog;
import ocgcore.DataManager;
import ocgcore.StringManager;
import ocgcore.enums.CardLocation;

/**
 * === 场地事件消息处理（MSG_* 场地/连锁/LP/攻击等事件，对齐 gframe duelclient.cpp ClientAnalyze） ===
 * 自 GameEngine 拆分而来。实现 GameMessageParser.MessageHandler：
 * - 场地事件类方法在此直接实现；
 * - 选择/提示类方法（实现已合并至 GameMessageParser）转发 engine.messageParser；
 * - 召唤动画类方法（拆入 SummonAnimationManager）转发 engine.summonAnim。
 * - 卡组/手卡堆动画方法（拆入 DeckHandMotionManager）转发 engine.deckMotion。
 */
public class DuelEventHandler implements GameMessageParser.MessageHandler {
    // 保持拆分前日志标识，便于与旧版日志比对
    private static final String TAG = "GameEngine";

    private final GameEngine engine;

    /**
     * 连锁卡码序列（对齐 gframe dField.chains[].code）：MSG_CHAINING 依次入列，
     * 供 MSG_CHAIN_NEGATED/DISABLED 按 ct-1 取被无效的卡码；MSG_CHAIN_END 清空。
     * 仅在通讯（网络）线程访问，无需同步。
     */
    public final List<Integer> chainCodes = new ArrayList<>();

    public DuelEventHandler(GameEngine engine) {
        this.engine = engine;
    }

    // ==== 选择/提示类转发（实现在 GameMessageParser） ====

    @Override
    public void onRetry() { engine.messageParser.onRetry(); }

    @Override
    public void onHint(int type, int player, int data) { engine.messageParser.onHint(type, player, data); }

    @Override
    public void onWaiting() { engine.messageParser.onWaiting(); }

    @Override
    public void onStart(int playerType, int duelRule, int lp0, int lp1, int deck0, int extra0, int deck1, int extra1) {
        engine.messageParser.onStart(playerType, duelRule, lp0, lp1, deck0, extra0, deck1, extra1);
    }

    @Override
    public void onWin(int player, int reason) { engine.messageParser.onWin(player, reason); }

    @Override
    public void onUpdateData(int player, int location, ByteBuffer data) { engine.messageParser.onUpdateData(player, location, data); }

    @Override
    public void onUpdateCard(int player, int location, int sequence, ByteBuffer data) {
        engine.messageParser.onUpdateCard(player, location, sequence, data);
    }

    @Override
    public void onRequestDeck(int player) { engine.messageParser.onRequestDeck(player); }

    @Override
    public void onSelectBattleCmd(ByteBuffer data) { engine.messageParser.onSelectBattleCmd(data); }

    @Override
    public void onSelectIdleCmd(ByteBuffer data) { engine.messageParser.onSelectIdleCmd(data); }

    @Override
    public void onSelectEffectYn(ByteBuffer data) { engine.messageParser.onSelectEffectYn(data); }

    @Override
    public void onSelectYesNo(ByteBuffer data) { engine.messageParser.onSelectYesNo(data); }

    @Override
    public void onSelectOption(ByteBuffer data) { engine.messageParser.onSelectOption(data); }

    @Override
    public void onSelectCard(ByteBuffer data) { engine.messageParser.onSelectCard(data); }

    @Override
    public void onSelectChain(ByteBuffer data) { engine.messageParser.onSelectChain(data); }

    @Override
    public void onSelectPlace(int player, int count, int fieldMask) { engine.messageParser.onSelectPlace(player, count, fieldMask); }

    @Override
    public void onSelectPosition(int player, int code, int positions) { engine.messageParser.onSelectPosition(player, code, positions); }

    @Override
    public void onSelectTribute(ByteBuffer data) { engine.messageParser.onSelectTribute(data); }

    @Override
    public void onSortChain(ByteBuffer data) { engine.messageParser.onSortChain(data); }

    @Override
    public void onSelectCounter(ByteBuffer data) { engine.messageParser.onSelectCounter(data); }

    @Override
    public void onSelectSum(ByteBuffer data) { engine.messageParser.onSelectSum(data); }

    @Override
    public void onSelectDisfield(int player, int count, int fieldMask) { engine.messageParser.onSelectDisfield(player, count, fieldMask); }

    @Override
    public void onSortCard(ByteBuffer data) { engine.messageParser.onSortCard(data); }

    @Override
    public void onSelectUnselectCard(ByteBuffer data) { engine.messageParser.onSelectUnselectCard(data); }

    // ==== 召唤动画类转发（实现在 SummonAnimationManager） ====

    @Override
    public void onSummoning(int code, int controler, int location, int sequence) {
        engine.summonAnim.onSummoning(code, controler, location, sequence);
    }

    @Override
    public void onSpSummoning(int code, int controler, int location, int sequence) {
        engine.summonAnim.onSpSummoning(code, controler, location, sequence);
    }

    @Override
    public void onFlipSummoning(int code, int controler, int location, int sequence) {
        engine.summonAnim.onFlipSummoning(code, controler, location, sequence);
    }

    // ==== 场地事件实现 ====

    @Override
    public void onConfirmDecktop(int player, int count, ByteBuffer data) {
        engine.deckMotion.applyConfirmDecktop(player, count, data);
    }

    @Override
    public void onConfirmCards(int player, int skipPanel, int count, ByteBuffer data) {
        engine.deckMotion.applyConfirmCards(player, skipPanel, count, data);
    }

    @Override
    public void onShuffleDeck(ByteBuffer data) {
        engine.deckMotion.applyShuffleDeck(data);
    }

    @Override
    public void onShuffleHand(ByteBuffer data) {
        engine.deckMotion.applyShuffleHand(data);
    }

    @Override
    public void onRefreshDeck(int player) {
        engine.deckMotion.postFieldChanged();
    }

    @Override
    public void onSwapGraveDeck(int player) {
        engine.deckMotion.postFieldChanged();
    }

    @Override
    public void onShuffleSetCard(int player, int count, ByteBuffer data) {
        engine.deckMotion.postFieldChanged();
    }

    @Override
    public void onReverseDeck(int player) {
        engine.deckMotion.postFieldChanged();
    }

    @Override
    public void onDeckTop(int player, int code) {
        engine.deckMotion.postFieldChanged();
    }

    @Override
    public void onNewTurn(int player) {
        engine.field.currentPlayer = engine.localPlayer(player);
        engine.field.turnCount++;
        engine.soundManager.playSoundEffect(SoundManager.SFX.NEXT_TURN);
        final int localCurrent = engine.field.currentPlayer;
        engine.mainHandler.post(() -> {
            if (engine.listener != null) {
                engine.listener.onFieldChanged();
                engine.listener.onTurnStarted(localCurrent);
            }
        });
    }

    @Override
    public void onNewPhase(int phase) {
        engine.field.currentPhase = phase;
        engine.soundManager.playSoundEffect(SoundManager.SFX.PHASE);
        // 同步派发（不再 mainHandler.post）：使阶段文字（case 101）在本次消息派发期间即入队
        // SpecEffectOverlay（startCard→startLoop 立即置 running），统一动画屏障才能检测到「阶段文字在播」并关闭闸门，
        // 令当前阶段内的卡片移动、阶段按钮文字变化、可选择外框、连锁/conti_act 动画等后续消息排在阶段文字之后，
        // 对齐 duelclient.cpp MSG_NEW_PHASE 的 showcard=101 + WaitFrameSignal(40)（overlay 阶段文字恰 40 帧）。
        if (engine.listener != null) engine.listener.onPhaseChanged(phase);
    }

    @Override
    public void onMove(int code, int oldCtrl, int oldLoc, int oldSeq, int oldPos,
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
                engine.field.moveCardAnimated(card, 10);
                card.animJitterX = oldCtrl == 1 ? 0.3f : -0.3f;
            } else if (newLoc == CardLocation.MonsterZone.value() && !card.overlayed.isEmpty()) {
                engine.field.moveOverlayMaterials(card, 10);
                engine.field.moveCardAnimated(card, 10, 10);
            } else {
                engine.field.moveCardAnimated(card, 10);
            }
        }

        // 手卡增删后重排双方手卡（数量变化 → 间距变化）
        if ((oldLoc & 0x7f) == CardLocation.Hand.value() || (newLoc & 0x7f) == CardLocation.Hand.value()) {
            engine.field.updateHandLayout(0, 10);
            engine.field.updateHandLayout(1, 10);
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

    @Override
    public void onPosChange(int code, int ctrl, int loc, int seq, int oldPos, int newPos) {
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

    @Override
    public void onSet(int code, int ctrl, int loc, int seq) {
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

    @Override
    public void onSwap(int c1ctrl, int c1loc, int c1seq, int c2ctrl, int c2loc, int c2seq) {
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

    @Override
    public void onFieldDisabled(int disabledMask) {
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

    @Override
    public void onSummoned() {
        engine.hintManager.setEventString(1604, "怪兽召唤成功");
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onFieldChanged();
        });
    }

    @Override
    public void onSpSummoned() {
        engine.hintManager.setEventString(1606, "怪兽特殊召唤成功");
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onFieldChanged();
        });
    }

    @Override
    public void onFlipSummoned() {
        engine.hintManager.setEventString(1608, "怪兽反转召唤成功");
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onFieldChanged();
        });
    }

    @Override
    public void onChaining(int code, int pcc, int pcl, int pcs, int subs, int cc, int cl, int cs, int desc) {
        engine.soundManager.playSoundEffect(SoundManager.SFX.ACTIVATE);
        // 记录连锁卡码（对齐 gframe MSG_CHAINED 将 current_chain.code 压入 dField.chains），
        // 供 MSG_CHAIN_NEGATED/DISABLED 按 ct-1 取被无效的卡码
        chainCodes.add(code);
        // 协议侧 controler 转本地索引，保证连锁高亮落在正确的半场
        final int localCc = engine.localPlayer(cc & 1);
        // duelclient.cpp MSG_CHAINING L3345/L3366-3374：四参 GetCard 取发动卡并填充 current_chain，
        // 其后 MSG_BECOME_TARGET 写入 current_chain.target，MSG_CHAINED 再压入 chains；
        // 供 ClientField::ShowCardInfoInList（event_handler.cpp L2937-2947）生成连锁状态标签
        engine.field.currentChain = new GameField.ChainInfo();
        engine.field.currentChain.chainCard = engine.field.getCard(engine.localPlayer(pcc & 1), pcl, pcs, subs);
        // 对齐 duelclient.cpp MSG_CHAINING L3346-3349：发动卡卡码变化（手卡/场上里侧卡翻开揭示）
        // 时 SetCode + MoveCard(pcard, 10)，由 code 0→真实卡码驱动 curRotY 插值，播放反面到正面的转动动画。
        GameField.ClientCard chainCard = engine.field.currentChain.chainCard;
        if (chainCard != null && chainCard.code != code) {
            chainCard.setCode(code);
            engine.field.moveCardAnimated(chainCard, 10);
        }
        engine.field.currentChain.code = code;
        engine.field.currentChain.desc = desc;
        engine.field.currentChain.controler = localCc;
        engine.field.currentChain.location = cl;
        engine.field.currentChain.sequence = cs;
        if (engine.listener != null) {
            // 发动动画入队即由统一动画屏障关闭闸门，播完再处理后续消息（对齐 C++ MSG_CHAINING 的 WaitFrameSignal(30)）
            engine.listener.onChainAnimation(code, localCc, cl, cs);
        }
    }

    @Override
    public void onChained(int chainCount) {
        // 对齐 duelclient.cpp MSG_CHAINED L3406：event_string=sys1609「[%ls]的效果发动」，卡名取 current_chain.code
        //（即最近一次 onChaining 压入 chainCodes 的卡码）
        int curCode = chainCodes.isEmpty() ? 0 : chainCodes.get(chainCodes.size() - 1);
        engine.hintManager.setEventString(1609, "[%s]的效果发动", DataManager.get().getName(curCode));
        // duelclient.cpp MSG_CHAINED L3408：chains.push_back(current_chain)。
        // C++ 为值拷贝，Java 用引用语义（同一对象入列），使效果处理期追加的目标同样能体现在状态标签上
        if (engine.field.currentChain != null && !engine.field.chains.contains(engine.field.currentChain)) {
            // 连锁图标位置快照：此刻卡片尚未因结算离开原位，图标此后固定在此处直到连锁消失
            GameField.ClientCard cc = engine.field.currentChain.chainCard;
            if (cc != null) {
                engine.field.currentChain.iconX = cc.curX;
                engine.field.currentChain.iconY = cc.curY;
                engine.field.currentChain.iconZ = cc.curZ;
                engine.field.currentChain.iconPosCaptured = true;
            }
            engine.field.chains.add(engine.field.currentChain);
        }
    }

    @Override
    public void onChainSolving(int chainCount) {

    }

    @Override
    public void onChainSolved(int chainCount) {

    }

    @Override
    public void onChainEnd() {
        // 对齐 duelclient.cpp MSG_CHAIN_END L3436-3442：逐链清除对象卡与发动卡的
        // is_showchaintarget 后再 chains.clear()
        for (GameField.ChainInfo ch : engine.field.chains) {
            for (GameField.ClientCard t : ch.targets) t.is_showchaintarget = false;
            if (ch.chainCard != null) ch.chainCard.is_showchaintarget = false;
        }
        chainCodes.clear();
        engine.field.chains.clear();
        engine.field.currentChain = new GameField.ChainInfo();
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onFieldChanged();
        });
    }

    @Override
    public void onChainNegated(int chainCount) {
        engine.soundManager.playSoundEffect(SoundManager.SFX.NEGATE);
        engine.summonAnim.postNegatedAnimation(chainCount);
    }

    @Override
    public void onChainDisabled(int chainCount) {
        engine.soundManager.playSoundEffect(SoundManager.SFX.NEGATE);
        engine.summonAnim.postNegatedAnimation(chainCount);
    }

    @Override
    public void onDraw(int player, int count, int[] codes) {
        // duelclient.cpp MSG_DRAW L3519-3557
        final int p = engine.localPlayer(player);
        final int deckLoc = CardLocation.Deck.value();
        final int handLoc = CardLocation.Hand.value();
        // 1) 给被抽的卡组顶设卡码（对齐 L3529-3534：deck_reversed 且 code=0 时不揭码）
        int top = engine.field.getCardCount(p, deckLoc) - 1;
        for (int i = 0; i < count; i++) {
            GameField.ClientCard pcard = engine.field.getCard(p, deckLoc, top - i);
            if (pcard != null && (!engine.field.deckReversed || codes[i] != 0)) {
                pcard.setCode(codes[i] & 0x7fffffff);
            }
        }
        // 2) 起手 N 张 / 单独抽卡动画：严格对齐 C++ L3542-3552 的逐张时序——每次从卡组顶取一张、
        //    入手卡、让该手卡从卡组顶飞入并顺带把已在手中的卡重排让位，随后 WaitFrameSignal(5)
        //    再抽下一张。旧实现在同一同步循环里对每张重复 moveCardAnimated 并覆盖 animDelayFrame，
        //    令所有卡同时起步、抽卡逐张感被抹平（即“缺少抽卡动画”的根因）。这里把每张卡的入手
        //    按 5 帧节拍用 postDelayed 依次排布，主线程逐帧渲染即可看到一张张抽入的动画。
        final long stepMs = 5L * 17L;
        if (engine.field.instantPlace) {
            // 回放快进重排：同步逐张入手，不按 5 帧节拍排布调度、不持闸
            //（postDelayed 回调会逃逸到排空之后破坏快进落点一致性）
            for (int i = 0; i < count; i++) drawOneCard(p, deckLoc, handLoc, codes, i);
        } else {
            for (int i = 0; i < count; i++) {
                final int idx = i;
                engine.mainHandler.postDelayed(() -> drawOneCard(p, deckLoc, handLoc, codes, idx), idx * stepMs);
            }
            // 抽卡展示动画持闸：最后一张延迟 (count-1)*5 帧启动 + 10 帧飞行 + 尾帧余量
            engine.animHoldUntilMs = System.currentTimeMillis() + ((count - 1) * 5L + 15L) * 17L;
        }
        engine.hintManager.setEventString(p == 0 ? 1611 : 1612, p == 0 ? "我方抽了%d张卡" : "对方抽了%d张卡", count);
        engine.mainHandler.post(() -> {
            if (engine.listener != null) {
                engine.listener.onFieldChanged();
                engine.listener.onPlayerInfoUpdated(p);
            }
        });
    }

    /**
     * 单张抽卡（onDraw 按 5 帧节拍调度调用，对齐 duelclient.cpp MSG_DRAW 循环体 L3543-3550）：
     * 从卡组顶移除 → 加入手卡 → 该卡从卡组位飞入 + 已有手卡重排让位（10 帧）→ 播放抽卡音效。
     */
    private void drawOneCard(int p, int deckLoc, int handLoc, int[] codes, int idx) {
        int t = engine.field.getCardCount(p, deckLoc) - 1;
        if (t < 0) return;
        GameField.ClientCard pcard = engine.field.removeCard(p, deckLoc, t);
        if (pcard == null) {
            pcard = new GameField.ClientCard();
            pcard.owner = p;
            pcard.controler = p;
            if (idx < codes.length) pcard.setCode(codes[idx] & 0x7fffffff);
        }
        engine.field.addCard(p, handLoc, 0, pcard);
        for (GameField.ClientCard hc : engine.field.players[p].hand) {
            if (hc != null) engine.field.moveCardAnimated(hc, 10);
        }
        engine.soundManager.playSoundEffect(SoundManager.SFX.DRAW);
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onFieldChanged();
        });
    }

    @Override
    public void onDamage(int player, int amount) {
        // 协议侧玩家 → 本地视角索引（0=我方）：我方为后攻时伤害/回复正确落到对应半场
        // 与顶部信息栏左半边（我方）布局保持一致
        int p = engine.localPlayer(player & 1);
        int fin = Math.max(0, engine.field.players[p].lp - amount);
        engine.field.players[p].lp = fin;
        engine.field.startLpChange(p, fin, 0xFFFF0000, "-" + amount, true);
        engine.soundManager.playSoundEffect(SoundManager.SFX.DAMAGE);
        engine.hintManager.setEventString(p == 0 ? 1613 : 1614, p == 0 ? "我方受到%d伤害" : "对方受到%d伤害", amount);
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onPlayerInfoUpdated(p);
        });
    }

    @Override
    public void onRecover(int player, int amount) {
        int p = engine.localPlayer(player & 1);
        int fin = engine.field.players[p].lp + amount;
        engine.field.players[p].lp = fin;
        engine.field.startLpChange(p, fin, 0xFF00FF00, "+" + amount, true);
        engine.soundManager.playSoundEffect(SoundManager.SFX.RECOVER);
        engine.hintManager.setEventString(p == 0 ? 1615 : 1616, p == 0 ? "我方回复%d基本分" : "对方回复%d基本分", amount);
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onPlayerInfoUpdated(p);
        });
    }

    @Override
    public void onEquip(int eqCode, int eqCtrl, int eqLoc, int eqSeq,
                        int tCtrl, int tLoc, int tSeq) {
        // duelclient.cpp MSG_EQUIP L3619-3652：旧装备关系解除（equipped 集合摘除、
        // 集合空时清 is_showequip），再登记新关系 pc1->equipTarget=pc2、pc2->equipped.add(pc1)
        GameField.ClientCard pc1 = engine.field.getCard(engine.localPlayer(eqCtrl & 1), eqLoc, eqSeq);
        GameField.ClientCard pc2 = engine.field.getCard(engine.localPlayer(tCtrl & 1), tLoc, tSeq);
        if (pc1 != null && pc2 != null) {
            if (pc1.equipTarget != null) {
                pc1.is_showequip = false;
                pc1.equipTarget.is_showequip = false;
                pc1.equipTarget.equipped.remove(pc1);
            }
            pc1.equipCard = pc2; // 兼容旧字段（HUD 图标的装备卡指向）
            pc1.equipTarget = pc2;
            if (!pc2.equipped.contains(pc1)) pc2.equipped.add(pc1);
        }
        engine.soundManager.playSoundEffect(SoundManager.SFX.EQUIP);
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onFieldChanged();
        });
    }

    @Override
    public void onLpUpdate(int player, int lp) {
        int p = engine.localPlayer(player & 1);
        engine.field.players[p].lp = lp;
        engine.field.startLpChange(p, lp, 0, null, false);
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onPlayerInfoUpdated(p);
        });
    }

    @Override
    public void onUnequip(int ctrl, int loc, int seq) {
        // duelclient.cpp MSG_UNEQUIP L3671-3691：从被装备卡 equipped 集合摘除，集合空时清 is_showequip
        GameField.ClientCard card = engine.field.getCard(engine.localPlayer(ctrl & 1), loc, seq);
        if (card != null) {
            if (card.equipTarget != null) {
                card.equipTarget.equipped.remove(card);
                card.equipTarget.is_showequip = false;
                card.is_showequip = false;
                card.equipTarget = null;
            }
            card.equipCard = null;
        }
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onFieldChanged();
        });
    }

    @Override
    public void onCardTarget(int c1ctrl, int c1loc, int c1seq, int c2ctrl, int c2loc, int c2seq) {
        // duelclient.cpp MSG_CARD_TARGET L3692-3717：c1->cardTarget.insert(c2); c2->ownerTarget.insert(c1)
        GameField.ClientCard c1 = engine.field.getCard(engine.localPlayer(c1ctrl & 1), c1loc, c1seq);
        GameField.ClientCard c2 = engine.field.getCard(engine.localPlayer(c2ctrl & 1), c2loc, c2seq);
        if (c1 != null && c2 != null) {
            if (!c1.targetCards.contains(c2)) c1.targetCards.add(c2);
            if (!c2.ownerTarget.contains(c1)) c2.ownerTarget.add(c1);
        }
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onFieldChanged();
        });
    }

    @Override
    public void onCancelTarget(int c1ctrl, int c1loc, int c1seq, int c2ctrl, int c2loc, int c2seq) {
        // duelclient.cpp MSG_CANCEL_TARGET L3718-3743：双向摘除，并对位清理悬停态图标标记
        GameField.ClientCard c1 = engine.field.getCard(engine.localPlayer(c1ctrl & 1), c1loc, c1seq);
        GameField.ClientCard c2 = engine.field.getCard(engine.localPlayer(c2ctrl & 1), c2loc, c2seq);
        if (c1 != null && c2 != null) {
            c1.targetCards.remove(c2);
            c2.ownerTarget.remove(c1);
            c2.is_showtarget = false;
        }
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onFieldChanged();
        });
    }

    @Override
    public void onPayLpCost(int player, int cost) {
        int p = engine.localPlayer(player & 1);
        int fin = Math.max(0, engine.field.players[p].lp - cost);
        engine.field.players[p].lp = fin;
        // 对齐 duelclient.cpp MSG_PAY_LPCOST L3755-3763：SFX.DAMAGE + lpccolor=0xff0000ff（蓝）、lpcstring="-cost"、
        // WaitFrameSignal(30)+lpframe=10+WaitFrameSignal(11)——支付基本分同样先浮字再扣减；startLpChange(showText=true)
        // 同步置 lpPending，统一动画屏障据此暂缓后续消息（如随后场上怪兽被破坏离场的 MSG_MOVE）
        engine.soundManager.playSoundEffect(SoundManager.SFX.DAMAGE);
        engine.field.startLpChange(p, fin, 0xFF0000FF, "-" + cost, true);
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onPlayerInfoUpdated(p);
        });
    }

    @Override
    public void onAddCounter(int type, int ctrl, int loc, int seq, int count) {
        GameField.ClientCard card = engine.field.getCard(ctrl, loc, seq);
        if (card != null) {
            card.counters.put(type, count);
        }
        engine.soundManager.playSoundEffect(SoundManager.SFX.COUNTER_ADD);
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onFieldChanged();
        });
    }

    @Override
    public void onRemoveCounter(int type, int ctrl, int loc, int seq, int count) {
        GameField.ClientCard card = engine.field.getCard(ctrl, loc, seq);
        if (card != null) {
            card.counters.remove(type);
        }
        engine.soundManager.playSoundEffect(SoundManager.SFX.COUNTER_REMOVE);
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onFieldChanged();
        });
    }

    @Override
    public void onAttack(int aCtrl, int aLoc, int aSeq, int dCtrl, int dLoc, int dSeq) {
        // 对齐 duelclient.cpp MSG_ATTACK L3830-3860：有目标攻怪音效 ATTACK，直接攻击音效 DIRECT_ATTACK
        GameField.ClientCard atkCard = engine.field.getCard(engine.localPlayer(aCtrl & 1), aLoc, aSeq);
        if (atkCard == null) {
            // 留痕诊断：攻击者卡查不到时弧线无起点、整条不绘（duelclient.cpp L3822 attacker 总可查到）
            Log.w(TAG, "onAttack: attacker card not found ctrl=" + aCtrl + " loc=" + aLoc + " seq=" + aSeq);
        }
        String atkName = atkCard != null ? DataManager.get().getName(atkCard.code) : "";
        boolean skipArc = engine.field.instantPlace; // 回放快进重排：不写弧线、不持闸
        if (dLoc != 0) {
            engine.soundManager.playSoundEffect(SoundManager.SFX.ATTACK);
            GameField.ClientCard defCard = engine.field.getCard(engine.localPlayer(dCtrl & 1), dLoc, dSeq);
            String defName = defCard != null ? DataManager.get().getName(defCard.code) : "";
            engine.hintManager.setEventString(1619, "[%s]攻击[%s]", atkName, defName);
            // 记录绿色攻击弧端点（攻击者→目标），GameFieldView 在约 0.9s 内绘制流动弧
            if (!skipArc) {
                engine.field.arcAttacker = atkCard;
                engine.field.arcTarget = defCard;
                engine.field.arcStartMs = System.currentTimeMillis();
            }
        } else {
            engine.soundManager.playSoundEffect(SoundManager.SFX.DIRECT_ATTACK);
            engine.hintManager.setEventString(1620, "[%s]直接攻击", atkName);
            // 直接攻击：无目标卡，弧落到对方手牌行一侧（duelclient.cpp L3850-3853）
            if (!skipArc) {
                engine.field.arcAttacker = atkCard;
                engine.field.arcTarget = null;
                engine.field.arcStartMs = System.currentTimeMillis();
            }
        }
        // 对齐 duelclient.cpp MSG_ATTACK L3864 WaitFrameSignal(40)：弧光展示期内关闭闸门，
        // 后续伤害步骤/询问弹窗不得抢先于弧线展示（直接攻击无卡片动画屏障，旧实现弧被弹窗
        // 遮挡几乎不可见；攻怪因伴随 MSG_MOVE 动画而受影响较小）
        if (!skipArc) {
            engine.animHoldUntilMs = System.currentTimeMillis() + GameEngine.ATTACK_HOLD_MS;
        }
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onFieldChanged();
        });
    }

    @Override
    public void onBattle(int atkAtk, boolean atkPos, int defAtk, boolean defPos) {
        Log.d(TAG, "Battle: " + atkAtk + " vs " + defAtk);
    }

    @Override
    public void onAttackDisabled() {
        // 对齐 duelclient.cpp MSG_ATTACK_DISABLED L3910：sys1621「攻击被无效」（无占位符，C++ 传入的卡名参数被忽略）
        engine.hintManager.setEventString(1621, "攻击被无效");
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onFieldChanged();
        });
    }

    @Override
    public void onDamageStepStart() {
        Log.d(TAG, "Damage step start");
    }

    @Override
    public void onDamageStepEnd() {
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onFieldChanged();
        });
    }

    @Override
    public void onMissedEffect(int code, int ctrl, int loc, int seq, int effectId) {
        Log.w(TAG, "Missed effect: code=" + code + " effectId=" + effectId);
        // 对齐 duelclient.cpp MSG_MISSED_EFFECT L3919-3924：sys1622「[%ls]错过时点」用 GetName(code) 填充，携带卡代码
        DuelLogDialog.addLog(DuelLogDialog.sysFormat(1622, "[%s]错过时点", DataManager.get().getName(code)), code);
    }

    @Override
    public void onTossCoin(int player, int count, ByteBuffer results) {
        engine.soundManager.playSoundEffect(SoundManager.SFX.COIN);
        // 对齐 duelclient.cpp MSG_TOSS_COIN L3926-3946：掷硬币结果记入日志
        StringManager sm = DataManager.get().getStringManager();
        StringBuilder sb = new StringBuilder(sm.getSystemString(1623, "掷硬币："));
        for (int i = 0; i < count && results.remaining() >= 1; i++) {
            int res = results.get() & 0xFF;
            sb.append('[')
                    .append(res != 0 ? sm.getSystemString(60, "正面") : sm.getSystemString(61, "反面"))
                    .append(']');
        }
        DuelLogDialog.addLog(sb.toString());
    }

    @Override
    public void onTossDice(int player, int count, ByteBuffer results) {
        engine.soundManager.playSoundEffect(SoundManager.SFX.DICE);
        // 对齐 duelclient.cpp MSG_TOSS_DICE L3948-3968：掷骰子结果记入日志
        StringBuilder sb = new StringBuilder(
                DataManager.get().getStringManager().getSystemString(1624, "掷骰子："));
        for (int i = 0; i < count && results.remaining() >= 1; i++) {
            sb.append('[').append(results.get() & 0xFF).append(']');
        }
        DuelLogDialog.addLog(sb.toString());
    }

    @Override
    public void onAnnounceRace(int player, int count, int availableRaces) {
        // duelclient.cpp MSG_ANNOUNCE_RACE L3996-4014：player 已消费，打包 count+available 转发
        ByteBuffer buf = ByteBuffer.allocate(5).order(ByteOrder.LITTLE_ENDIAN);
        buf.put((byte) count);
        buf.putInt(availableRaces);
        buf.flip();
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onSelectRequired(140, buf);
        });
    }

    @Override
    public void onAnnounceAttrib(int player, int count, int availableAttribs) {
        // duelclient.cpp MSG_ANNOUNCE_ATTRIB L4015-4033：同上，打包 count+available 转发
        ByteBuffer buf = ByteBuffer.allocate(5).order(ByteOrder.LITTLE_ENDIAN);
        buf.put((byte) count);
        buf.putInt(availableAttribs);
        buf.flip();
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onSelectRequired(141, buf);
        });
    }

    @Override
    public void onAnnounceCard(int player, ByteBuffer data) {
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onSelectRequired(142, data);
        });
    }

    @Override
    public void onAnnounceNumber(int player, ByteBuffer data) {
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onSelectRequired(143, data);
        });
    }

    @Override
    public void onCardHint(int player, int location, int sequence, int hintType, int value) {
        // duelclient.cpp MSG_CARD_HINT L4094-4109：CHINT_DESC_ADD/REMOVE 维护卡片 desc_hints
        engine.field.applyCardHint(engine.localPlayer(player & 1), location, sequence, hintType, value);
    }

    @Override
    public void onBecomeTarget(int count, ByteBuffer data) {
        if (data == null || count <= 0) return;
        // duelclient.cpp MSG_BECOME_TARGET L3493-3500：每条 4 字节，第 4 字节（subseq）读入即弃，
        // 三参 GetCard 后写入 current_chain.target
        for (int i = 0; i < count && data.remaining() >= 4; i++) {
            int ctrl = data.get() & 0xFF;
            int loc = data.get() & 0xFF;
            int seq = data.get() & 0xFF;
            data.get();
            engine.field.addChainTarget(engine.localPlayer(ctrl & 1), loc, seq);
        }
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onFieldChanged();
        });
    }

    @Override
    public void onTagSwap(int player) {
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onFieldChanged();
        });
    }

    @Override
    public void onReloadField(ByteBuffer data) {
        // duelclient.cpp MSG_RELOAD_FIELD L4287-4441：断线重连时服务端下发全场快照，
        // 重建双方 场/手/卡组/墓地/除外/额外(含里侧与表侧个数) 全部卡片与连锁信息。
        // 隐藏卡不下发卡码（新建 ClientCard code=0 背面占位，与 C++ CreateCard 一致）。
        if (data == null) return;
        final GameField field = engine.field;
        field.clear();
        if (data.remaining() < 1) {
            Log.w(TAG, "onReloadField: truncated payload");
            return;
        }
        field.dInfo.duelRule = data.get() & 0xFF;
        for (int i = 0; i < 2; i++) {
            int p = engine.localPlayer(i);
            if (data.remaining() < 4) {
                Log.w(TAG, "onReloadField: truncated at player " + i);
                break;
            }
            int lp = data.getInt();
            field.dInfo.lp[p] = lp;
            field.players[p].lp = lp;
            engine.playerInfos[p].lp = lp;
            // 怪兽区 7 格：present(1) → [position(1) + overlayCnt(1) + 素材占位]
            for (int seq = 0; seq < GameField.MAX_MONSTER_ZONE; seq++) {
                if (data.remaining() < 1) return;
                int present = data.get() & 0xFF;
                if (present == 0) continue;
                if (data.remaining() < 2) return;
                GameField.ClientCard card = new GameField.ClientCard();
                field.addCard(p, CardLocation.MonsterZone.value(), seq, card);
                card.position = data.get() & 0xFF;
                int ovc = data.get() & 0xFF;
                for (int xyz = 0; xyz < ovc; xyz++) {
                    // C++ L4316-4328：素材仅作占位（不读卡码），sequence 为 overlayed 内索引
                    GameField.ClientCard x = new GameField.ClientCard();
                    card.overlayed.add(x);
                    field.overlayCards.add(x);
                    x.overlayTarget = card;
                    x.location = CardLocation.Overlay.value();
                    x.sequence = card.overlayed.size() - 1;
                    x.owner = p;
                    x.controler = p;
                }
            }
            // 魔法陷阱区 8 格：present(1) → [position(1)]（无叠放字段）
            for (int seq = 0; seq < GameField.MAX_SPELL_ZONE; seq++) {
                if (data.remaining() < 1) return;
                int present = data.get() & 0xFF;
                if (present == 0) continue;
                if (data.remaining() < 1) return;
                GameField.ClientCard card = new GameField.ClientCard();
                field.addCard(p, CardLocation.SpellZone.value(), seq, card);
                card.position = data.get() & 0xFF;
            }
            // deck/hand/grave/removed/extra 五区：各 cnt(1) + cnt 张占位卡（C++ 不读卡详情与 position）
            int[] zoneLocs = {CardLocation.Deck.value(), CardLocation.Hand.value(),
                    CardLocation.Grave.value(), CardLocation.Removed.value(), CardLocation.Extra.value()};
            for (int zone = 0; zone < zoneLocs.length; zone++) {
                if (data.remaining() < 1) return;
                int cnt = data.get() & 0xFF;
                for (int seq = 0; seq < cnt; seq++) {
                    GameField.ClientCard card = new GameField.ClientCard();
                    card.owner = p;
                    card.controler = p;
                    field.addCard(p, zoneLocs[zone], seq, card);
                }
            }
            // 额外卡组表侧张数（在上方 extra 占位卡全部入堆后赋值，避免 addCard 自增干扰）
            if (data.remaining() < 1) return;
            field.extraPCount[p] = data.get() & 0xFF;
        }
        // C++ L4378：RefreshAllCards 重算全场落点（场上/手卡/墓地/除外/额外摆放位置即此恢复）
        field.refreshAllCards();
        // 链信息：cnt(1) + cnt×[code(4) pcc pcl pcs subs cc cl cs(各1) desc(4)]
        int chainCnt = 0;
        if (data.remaining() >= 1) {
            chainCnt = data.get() & 0xFF;
        }
        GameField.ChainInfo lastChain = null;
        for (int i = 0; i < chainCnt; i++) {
            if (data.remaining() < 15) {
                Log.w(TAG, "onReloadField: truncated chain " + i);
                break;
            }
            int code = data.getInt();
            int pcc = engine.localPlayer(data.get() & 0xFF);
            int pcl = data.get() & 0xFF;
            int pcs = data.get() & 0xFF;
            int subs = data.get() & 0xFF;
            int cc = engine.localPlayer(data.get() & 0xFF);
            int cl = data.get() & 0xFF;
            int cs = data.get() & 0xFF;
            int desc = data.getInt();
            GameField.ChainInfo ci = new GameField.ChainInfo();
            ci.chainCard = field.getCard(pcc, pcl, pcs, subs);
            ci.code = code;
            ci.desc = desc;
            ci.controler = cc;
            ci.location = cl;
            ci.sequence = cs;
            ci.solved = false;
            field.chains.add(ci);
            lastChain = ci;
        }
        // C++ L4433-4436：存在链时写入事件提示串（sys 1609 + 最后一条链卡名）
        if (lastChain != null) {
            field.eventString = DataManager.get().formatSystemString(1609,
                    "【%s】的连锁发动", DataManager.get().getName(lastChain.code));
        }
        engine.mainHandler.post(() -> {
            if (engine.listener != null) {
                engine.listener.onFieldChanged();
                engine.listener.onPlayerInfoUpdated(0);
                engine.listener.onPlayerInfoUpdated(1);
            }
        });
    }

    @Override
    public void onAiName(String name) {
        Log.i(TAG, "AI name: " + name);
    }

    @Override
    public void onShowHint(String hint) {
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onHintMessage(hint);
        });
    }

    @Override
    public void onMatchKill(int code) {
        engine.matchResult = 3;
    }

    @Override
    public void onCustomMsg(String msg) {
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onHintMessage(msg);
        });
    }

    @Override
    public void onDuelWinner(int player, int reason) {
        engine.messageParser.onWin(player, reason);
    }
}

