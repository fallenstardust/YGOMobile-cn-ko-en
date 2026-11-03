package cn.garymb.ygomobile.game.gamefield;
import cn.garymb.ygomobile.game.engine.GameEngine;

import android.util.Log;

import java.nio.ByteBuffer;

import cn.garymb.ygomobile.audio.SoundManager;
import ocgcore.enums.CardLocation;

/**
 * MSG_MOVE / MSG_POS_CHANGE / MSG_SET / MSG_SWAP / MSG_FIELD_DISABLED 的场地落位实现
 * （自 DuelEventHandler 平移，逻辑零改；对齐 gframe duelclient.cpp ClientAnalyze 各分支）。
 */
public class MoveEventApplier {

    private static final String TAG = "GameField";

    /**
     * ocgcore common.h L161-162：{@code REASON_DESTROY=0x1}（被破坏）、{@code REASON_RELEASE=0x2}（作为解放/解放代价）。
     * <p>
     * 旧实现把破坏音效误判在 0x2 上（当年注释也写错为“REASON_DESTROY=0x2”），于是只有「以解放/释放
     * 为代价离开原区域」的移动会响销毁音；而脚本里真正的破坏一律带 0x1（常与 REASON_EFFECT 0x40、
     * REASON_BATTLE 0x20 组合），包括手卡被破坏（如针虫的巢窟类）、卡组/额外被破坏送墓、魔陷区的
     * 灵摆怪（刻度卡）被破坏等，均静默无声——即用户反馈的“一些破坏场景没有音效”。
     */
    private static final int REASON_DESTROY = 0x1;

    private final GameEngine engine;

    public MoveEventApplier(GameEngine engine) {
        this.engine = engine;
    }

    /**
     * 「源卡查无此卡」时，本条 MSG_MOVE 是否允许新建一张卡片落位。
     * <p>C++ 端只有 {@code pl == 0} 的新建分支会 {@code CreateCard}（duelclient.cpp L2962-2975），
     * 普通移动分支拿到 {@code GetCard} 的结果就直接用，<b>从不为查无此卡凭空造卡</b>——它的模型里
     * 源卡恒在（开局发牌与各次入手都已按 pl==0 / MSG_DRAW 建过）。本端唯一会缺源卡的情形是观战席位
     * 中途接入：它没赶上开局发牌，之后对方「从手卡」的移动在本地无卡可搬；此时照旧造一张卡塞进
     * 目的地，就留下一张卡码为 0、点不动、也再没有任何消息把它移走的空卡（用户反馈的「场上横向摆放
     * 没卡图的空卡」与「对方除外区竖着的里侧影」）。
     * <p>故只在下述两种情形仍建卡：①本条消息带真实卡码（公开信息，等同 pl==0 的新建语义，观战照常
     * 看得到对手从手卡召唤/特召/盖放的怪）；②目的地是手卡（张数由 MSG_UPDATE_DATA 的块数经
     * {@code reconcileHandCount} 对平、卡码由 MSG_CONFIRM_CARDS 补写，不会永久残留）。
     * 其余（遮蔽移动落到场上/墓地/除外/卡组/额外）一律忽略这条移动，宁可不画也不留幽灵卡。
     */
    private static boolean canMaterializeMissingSource(int code, int newLoc) {
        return code != 0 || (newLoc & 0x7f) == CardLocation.Hand.value();
    }

    public void applyMove(int code, int oldCtrl, int oldLoc, int oldSeq, int oldPos,
                   int newCtrl, int newLoc, int newSeq, int position, int reason) {
        oldCtrl = engine.localPlayer(oldCtrl);
        newCtrl = engine.localPlayer(newCtrl);
        boolean oldOverlay = (oldLoc & 0x80) != 0;
        boolean newOverlay = (newLoc & 0x80) != 0;

        if (newOverlay && !oldOverlay) {
            // 作为超量素材叠放到超量怪兽下方（duelclient.cpp L3055-3095）
            ClientCard card = engine.field.getCard(oldCtrl, oldLoc & 0x7f, oldSeq);
            if (card == null) {
                // 源卡不在本端模型里：只有带卡码（或目的地为手卡）的消息才允许建卡，其余按
                // canMaterializeMissingSource 的判据忽略，避免凭空造出一张永远移不走的 0 码素材
                if (!canMaterializeMissingSource(code, newLoc)) return;
                card = new ClientCard();
                card.owner = oldCtrl;
            }
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
            ClientCard olcard = engine.field.attachOverlayMaterial(card, oldCtrl, oldLoc & 0x7f, oldSeq,
                    newCtrl, newLoc & 0x7f, newSeq);
            if (olcard != null && olcard.location == CardLocation.MonsterZone.value()) {
                engine.field.moveCardAnimated(card, 10);
            }
        } else if (oldOverlay && !newOverlay) {
            // 超量素材离场（duelclient.cpp L3096-3124）：oldSeq=超量怪兽格、oldPos=素材序号；
            // 宿主按消息 pl 字节动态定位（GetCard(pc, pl & 0x7f, ps)）——怪兽自身 MSG_MOVE
            // 先到时宿主可能已在墓地等区域，硬编码怪兽区查找会导致素材无离场动画
            ClientCard card = engine.field.detachOverlayMaterial(
                    oldCtrl, oldLoc & 0x7f, oldSeq, oldPos, newCtrl, newLoc & 0x7f, newSeq, position);
            if (card != null) {
                if (code != 0) card.code = code;
                engine.field.moveCardAnimated(card, 10);
            }
        } else if (oldOverlay) {
            // 素材在两只超量怪兽间转移（duelclient.cpp L3125-3153）：两侧宿主同样按消息 loc 字节
            // 动态定位（GetCard(pc, pl & 0x7f, ps) / GetCard(cc, cl & 0x7f, cs)）
            ClientCard src = engine.field.getCard(oldCtrl, oldLoc & 0x7f, oldSeq);
            ClientCard dst = engine.field.getCard(newCtrl, newLoc & 0x7f, newSeq);
            if (src != null && dst != null && oldPos >= 0 && oldPos < src.overlayed.size()) {
                ClientCard m = src.overlayed.remove(oldPos);
                for (int i = 0; i < src.overlayed.size(); i++) {
                    ClientCard s = src.overlayed.get(i);
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
            // 清效果对象链接后淡出到 alpha 5（APPEAR_FRAME 帧），播完由 GameFieldMotion purge；
            // 旧实现直接丢弃卡片，场上/墓地卡片消失没有任何淡出过程
            ClientCard card = engine.field.removeCard(oldCtrl, oldLoc, oldSeq);
            if (card != null) {
                if (code != 0 && card.code != code)
                    card.code = code;
                card.clearTarget();
                card.is_hovered = false;
                // removeCard 已把 location 置 0，而 getCardLocation 的 case 0 与手卡共用分支（同
                // client_field.cpp L853-854），淡出期间渲染层必须按离场前的区域走：否则手卡/场上卡
                // 会改走 3D 分支按手卡姿态斜立在手卡行列外，形成一张「没排齐的斜虚影」
                card.fadeLocation = oldLoc & 0x7f;
                // 对象/连锁对象/装备图标随卡离场关掉（同 C++ 移除前对 hovered 卡的 SetShowMark 语义）
                card.is_showequip = false;
                card.is_showtarget = false;
                card.is_showchaintarget = false;
                engine.field.fadeCard(card, 5, GameField.APPEAR_FRAME);
                engine.field.fadingCards.add(card);
            }
        } else if (oldLoc == 0) {
            // 登场出现（pl==0，duelclient.cpp MSG_MOVE L2959-2972）：新建卡片入区后先定位到
            // 目标格，再从 alpha 5 淡入到 255（C++ GetCardLocation 置 curPos + FadeCard(255, appear)）；
            // 旧实现虽新建但无淡入，卡片从全透明区直接以实色闪现
            ClientCard card = new ClientCard();
            card.owner = newCtrl;
            card.code = code;
            card.position = position;
            engine.field.addCard(newCtrl, newLoc, newSeq, card);
            engine.field.setCardPos(card);
            card.curAlpha = 5;
            engine.field.fadeCard(card, 255, GameField.APPEAR_FRAME);
        } else {
            ClientCard card = engine.field.getCard(oldCtrl, oldLoc, oldSeq);
            if (card == null) {
                // C++ 的普通移动分支（duelclient.cpp L2994-3023）拿到 GetCard 结果直接用，绝不为
                // 查无此卡造卡；本端一旦凭空造一张塞进目的地，就长出点不动也移不走的 0 码幽灵卡
                //（场上横向空卡、对方除外区里侧竖影）。判据与被忽略原因见 canMaterializeMissingSource。
                if (!canMaterializeMissingSource(code, newLoc)) {
                    Log.i(TAG, "MSG_MOVE ignored: source card absent (mid-join spectator) src="
                            + oldCtrl + "/0x" + Integer.toHexString(oldLoc) + "/" + oldSeq
                            + " dst=" + newCtrl + "/0x" + Integer.toHexString(newLoc) + "/" + newSeq);
                    return;
                }
                card = new ClientCard();
                card.owner = oldCtrl;
            }
            // 对齐 duelclient.cpp MSG_MOVE L2994：仅 code!=0 或回额外卡组(cl==0x40，服务端里侧回插
            // 时 code=0)才 SetCode；SetCode(0) 会把原卡码存入 chain_code（飞行中仍按原卡面绘制），
            // 旧实现无条件覆写 code 使其它隐藏信息移动误清卡码
            if (card.code != code && (code != 0 || newLoc == 0x40))
                card.setCode(code);
            // duelclient.cpp MSG_MOVE 普通移动分支 L2999-3014 的逐顶清理：
            // 离开场上（旧位置为 MZONE/SZONE 且换区）时指示物随卡失效；换区时摘除效果对象
            // 链接并断开装备关系；无论哪种移动一律关掉卡上的装备/对象/连锁对象图标与悬停态——
            // 旧实现一个都不清，图标与对象链会跨移区残留（点已无关的旧对象链卡仍亮 target 图标）
            boolean locChanged = newLoc != oldLoc;
            if ((oldLoc & 0x0C) != 0 && locChanged) card.counters.clear();
            if (locChanged) {
                card.clearTarget();
                if (card.equipTarget != null) {
                    card.equipTarget.equipped.remove(card);
                    card.equipTarget.is_showequip = false;
                    card.equipTarget = null;
                }
            }
            card.is_hovered = false;
            card.is_showequip = false;
            card.is_showtarget = false;
            card.is_showchaintarget = false;
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
                // 对方「非手卡→手卡」且消息与卡对象均无卡码（遮蔽型服务器的卡组/未知来源入手）：
                // 真实卡码由紧随其后的 MSG_CONFIRM_CARDS 送达。此时不启动飞行动画（也不关动画闸门，
                // 使确认消息尽快派发），把卡冻结在卡组顶坐标等待写码后直接正面飞入；
                // 已有卡码（墓地/除外/场上表侧等公开区来源，主机侧不遮蔽也不泄露）仍走正常揭示飞行，行为不变
                boolean freezeForConfirm = newLoc == CardLocation.Hand.value()
                        && (oldLoc & 0x7f) != CardLocation.Hand.value()
                        && code == 0 && card.code == 0 && newCtrl == 1
                        && !engine.field.instantPlace;
                if (freezeForConfirm) {
                    card.pendingFaceupAdd = true;
                } else {
                    // 飞行起点兜底：异常/新建路径的卡从未定位过（cur*=0）时先从旧区域
                    // 落位，避免从世界原点飞向墓地/除外而无可见飞行过程
                    if (card.curX == 0f && card.curY == 0f && card.curZ == 0f && oldLoc != 0)
                        engine.field.setCardPosForMove(card, oldCtrl, oldLoc & 0x7f, oldSeq);
                    engine.field.moveCardAnimated(card, 10);
                }
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
        // 揭示范围以「本端收到的 MSG_MOVE 是否带真实卡码」为准：主机侧对对手一律遮蔽入手卡码
        //（DuelAnalyzer MSG_MOVE 对齐 single_duel.cpp L969-971），因此对手「卡组→手卡」的私有入手
        // 到本端恒为 code==0，只走上面的 freezeForConfirm（等 MSG_CONFIRM_CARDS 补码或超时背面入位），
        // 不会在此翻面亮出对手刚拿到的卡；只有公开来源（墓地/除外/场上等）与回放/单人全量视角才进入本揭示。
        // 洗切不在此处播放：引擎会为「非抽卡入手」
        // 置 shuffle_hand_check 并发出 MSG_SHUFFLE_HAND，由 DeckHandMotionManager.applyShuffleHand 唯一播放一次。
        // 例外（用户规格，对齐 C++）：从场上离场的里侧表示卡回手不做任何翻面/高亮确认——gframe 的
        // MSG_MOVE 分支（duelclient.cpp L2994-2998）对里侧回手卡只 SetCode、绝无确认动画，离场翻开确认
        // 只挂在 MSG_CONFIRM_CARDS 的 field_confirm 分支（L2571-2608，针对回到场上格子的卡），从不挂在
        // 回到手卡的移动上；且旧卡场上姿态为里侧时信息已不再公开，无论本端是否已知卡码一律静默背面入手
        //（对方视角下遮蔽 MSG_MOVE 本就零码，此处同时约束己方全量视角不弹蚂蚁线高亮）。
        {
            int oldLocBase = oldLoc & 0x7f;
            int newLocBase = newLoc & 0x7f;
            boolean leftFieldFacedown = (oldLocBase & CardLocation.Onfield.value()) != 0
                    && (oldPos & GameField.POS_FACEDOWN) != 0;
            if (newLocBase == CardLocation.Hand.value() && oldLocBase != CardLocation.Hand.value()
                    && !leftFieldFacedown) {
                // 只对本次入手的那张卡揭示：对方卡从卡背翻到正面供对手确认、我方卡本就正面
                // 不翻给对方看，两者均施加行进蚂蚁线高亮，展示结束后由引擎的洗切接管
                ClientCard arriving = engine.field.getCard(newCtrl, newLocBase, newSeq);
                if (arriving != null && arriving.pendingFaceupAdd) {
                    // 冻结待码中：揭示窗口交给写入卡码的 MSG_CONFIRM_CARDS 触发（见
                    // applyMoveToHandReveal 释放分支）；部分服务器不发确认时 350ms 超时兜底，
                    // 释放冻结照旧背面入位，卡不致卡在卡组顶
                    arriving.revealHeld = true;
                    final ClientCard frozen = arriving;
                    final int viewSeat = newCtrl;
                    engine.mainHandler.postDelayed(() -> {
                        if (!frozen.pendingFaceupAdd) return;
                        frozen.pendingFaceupAdd = false;
                        frozen.revealHeld = false;
                        engine.field.moveCardAnimated(frozen, 6);
                        engine.field.updateHandLayout(viewSeat, 6);
                        engine.mainHandler.post(() -> {
                            if (engine.listener != null) engine.listener.onFieldChanged();
                        });
                    }, 350);
                } else {
                    engine.deckMotion.applyMoveToHandReveal(newCtrl, arriving);
                }
            }
        }
        // 音效严格对齐 duelclient.cpp MSG_MOVE L2955-2960：仅在真正发生移动（pl!=cl）时，
        // 目的地含除外位（LOCATION_REMOVED=0x20）播 BANISHED，否则 reason 带破坏位 REASON_DESTROY 播 DESTROYED。
        // C++ 此分支不要求目的地是墓地（破坏回手/回卡组等同样播 DESTROYED），也没有 SUMMON 分支——
        // 召唤/特殊召唤音效由 MSG_SUMMONING/MSG_SPSUMMONING（SummonAnimationManager）负责，此处重复播会错音。
        if (newLoc != oldLoc) {
            if ((newLoc & CardLocation.Removed.value()) != 0) {
                engine.soundManager.playSoundEffect(SoundManager.SFX.BANISHED);
            } else if ((reason & REASON_DESTROY) != 0) {
                engine.soundManager.playSoundEffect(SoundManager.SFX.DESTROYED);
            }
        }

        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onFieldChanged();
        });
    }

    public void applyPosChange(int code, int ctrl, int loc, int seq, int oldPos, int newPos) {
        ctrl = engine.localPlayer(ctrl);
        ClientCard card = engine.field.getCard(ctrl, loc, seq);
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

    public void applySet(int code, int ctrl, int loc, int seq) {
        ctrl = engine.localPlayer(ctrl);
        // 对齐 gframe duelclient.cpp MSG_SET（L3179-3189）：仅播音效 + 事件串，绝不新建/替换卡片。
        // 卡片已由先到的 MSG_MOVE(onMove) 放入并定位到格子；旧实现在此 new 了一张 cur*=0 的卡，
        // 而 addCard 对 MZONE/SZONE 只做 list.set 不调 setCardPos，新卡落在世界原点、与底板共面被
        // 深度吞掉，于是里侧守备怪兽 / 盖放魔陷卡都看不到卡背矩形。
        ClientCard card = engine.field.getCard(ctrl, loc, seq);
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

    public void applySwap(int c1ctrl, int c1loc, int c1seq, int c2ctrl, int c2loc, int c2seq) {
        c1ctrl = engine.localPlayer(c1ctrl);
        c2ctrl = engine.localPlayer(c2ctrl);
        ClientCard c1 = engine.field.getCard(c1ctrl, c1loc, c1seq);
        ClientCard c2 = engine.field.getCard(c2ctrl, c2loc, c2seq);
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

    public void applyFieldDisabled(int disabledMask) {
        // 对齐 duelclient.cpp MSG_FIELD_DISABLED L3226-3231：读入协议侧掩码后，
        // 本地为后攻（!dInfo.isFirst）时高低 16 位换位（协议 p0/p1 ↔ 本地 我方/对方），
        // 存 dField.disabled_field 供交叉线绘制；MSG_SWAP 时由 swapField 同步换位
        int disabled = disabledMask;
        if (!engine.isDuelFirst()) disabled = (disabled >>> 16) | (disabled << 16);
        engine.field.applyDisabledField(disabled);
        engine.mainHandler.post(() -> {
            if (engine.listener != null) engine.listener.onFieldChanged();
        });
    }
}
