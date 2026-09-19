package cn.garymb.ygomobile.game;

import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.List;

import ocgcore.enums.CardLocation;

import cn.garymb.ygomobile.game.GameField.ChainInfo;
import cn.garymb.ygomobile.game.GameField.ClientCard;
import cn.garymb.ygomobile.game.GameField.PendingOverlay;
import cn.garymb.ygomobile.game.GameField.PlayerField;

/**
 * GameField 的卡片增删改协作类：忠实移植 client_field.cpp / duelclient.cpp 的 AddCard/RemoveCard/
 * MoveCard/UpdateCard/超量素材挂接与摘取，以及命令标记 / 选择状态清理（clearCommandFlag/clearSelect/
 * clearChainSelect）、交换场地（swapField）。由门面 {@link GameField} 持有（field.cards），
 * 共享状态经包级私有直连门面，跨组动画 / 落点经门面转发（field.moveCardAnimated/setCardPos/getCardLocation）。
 */
class GameFieldCards {

    private final GameField field;

    GameFieldCards(GameField field) {
        this.field = field;
    }

    public void addCard(int controler, int location, int sequence, ClientCard card) {
        if (controler < 0 || controler > 1) return;
        if (card != null) {
            card.controler = controler;
            card.location = location;
            card.sequence = sequence;
        }
        List<ClientCard> list = field.players[controler].getLocationList(location);
        if (list == null) return;
        while (list.size() <= sequence) list.add(null);
        switch (location) {
            case 0x01: {
                if (sequence == 0 && !list.isEmpty() && list.get(0) != null) {
                    list.add(0, card);
                } else {
                    list.set(sequence, card);
                }
                field.resetSequence(list, true);
                if (card != null) {
                    card.is_reversed = false;
                    card.clearData();
                    card.clearTarget();
                }
                break;
            }
            case 0x02: {
                // C++ AddCard(LOCATION_HAND)：push_back —— 追加到第一个空位而非覆盖
                int idx = -1;
                for (int i = 0; i < list.size(); i++) {
                    if (list.get(i) == null) { idx = i; break; }
                }
                if (idx < 0) list.add(card);
                else list.set(idx, card);
                field.resetSequence(list, false);
                break;
            }
            case 0x04: {
                list.set(sequence, card);
                // 怪兽入格后补挂先到的待挂素材，使其飞向本格并正面叠放
                flushPendingOverlays(controler, sequence, card);
                break;
            }
            case 0x08: {
                list.set(sequence, card);
                break;
            }
            case 0x10: {
                list.set(sequence, card);
                if (card != null) card.sequence = sequence;
                break;
            }
            case 0x20: {
                list.set(sequence, card);
                if (card != null) card.sequence = sequence;
                break;
            }
            case 0x40: {
                // 对齐 C++ AddCard(LOCATION_EXTRA)（client_field.cpp L211-221）：额外卡组堆叠约定
                // 里侧在下方、表侧集中于上方（高 index = 高 z = 堆顶）。表侧进入 push_back 到堆顶；
                // 里侧回去（融合/同调/超量/连接以里侧返回额外卡组）插到 faceup_begin = 实际张数 -
                // 表侧数，即里侧组最上方、表侧组正下方，而非整堆最上方。
                // 注意：Java 的 extra 是定长 null 填充列表，list.size() 含尾部空位，
                // 用 size() 代替实际张数会把里侧回插的卡顶到堆最上方（旧 Bug）。
                boolean faceUp = card != null && card.isFaceUp();
                int count = 0;
                for (int i = 0; i < list.size(); i++) {
                    if (list.get(i) != null) count++;
                }
                int at = (field.extraPCount[controler] == 0 || faceUp)
                        ? count                            // 等价 push_back：追加到实际末尾
                        : count - field.extraPCount[controler];  // 里侧组顶部（第一张表侧之下）
                if (at < 0) at = 0;
                if (count >= list.size()) list.add(null);  // 兼容列表曾被撑短：先腾出末尾槽位
                // 从密区末尾起逐格右移一格，再把卡片插入 at（保持定长 null 填充结构）
                for (int i = count; i > at; i--) {
                    list.set(i, list.get(i - 1));
                }
                list.set(at, card);
                field.resetSequence(list, true);
                if (faceUp) {
                    field.extraPCount[controler]++;
                }
                break;
            }
        }
    }

    public ClientCard removeCard(int controler, int location, int sequence) {
        if (controler < 0 || controler > 1) return null;
        List<ClientCard> list = field.players[controler].getLocationList(location);
        if (list == null || sequence < 0 || sequence >= list.size()) return null;
        ClientCard pcard = list.get(sequence);
        switch (location) {
            case 0x01: {
                for (int i = sequence; i < list.size() - 1; i++) {
                    ClientCard next = list.get(i + 1);
                    list.set(i, next);
                    if (next != null) {
                        next.sequence--;
                        next.curZ -= 0.01f;
                    }
                }
                list.set(list.size() - 1, null);
                break;
            }
            case 0x02: {
                // C++ erase：前移压实，保持手卡序号与列表下标一致（getCard 依赖）
                for (int i = sequence; i < list.size() - 1; i++) {
                    list.set(i, list.get(i + 1));
                }
                list.set(list.size() - 1, null);
                field.resetSequence(list, false);
                break;
            }
            case 0x04: {
                list.set(sequence, null);
                break;
            }
            case 0x08: {
                list.set(sequence, null);
                break;
            }
            case 0x10: {
                for (int i = sequence; i < list.size() - 1; i++) {
                    ClientCard next = list.get(i + 1);
                    list.set(i, next);
                    if (next != null) {
                        next.sequence--;
                        next.curZ -= 0.01f;
                    }
                }
                list.set(list.size() - 1, null);
                break;
            }
            case 0x20: {
                for (int i = sequence; i < list.size() - 1; i++) {
                    ClientCard next = list.get(i + 1);
                    list.set(i, next);
                    if (next != null) {
                        next.sequence--;
                        next.curZ -= 0.01f;
                    }
                }
                list.set(list.size() - 1, null);
                break;
            }
            case 0x40: {
                for (int i = sequence; i < list.size() - 1; i++) {
                    ClientCard next = list.get(i + 1);
                    list.set(i, next);
                    if (next != null) {
                        next.sequence--;
                        next.curZ -= 0.01f;
                    }
                }
                list.set(list.size() - 1, null);
                if (pcard != null && pcard.isFaceUp()) {
                    field.extraPCount[controler]--;
                }
                break;
            }
        }
        if (pcard != null) pcard.location = 0;
        return pcard;
    }

    /**
     * 将卡片作为超量素材叠放到 (newCtrl, newLocBase, xyzSeq) 的超量怪兽下方
     *（对齐 duelclient.cpp MSG_MOVE 的 !(pl&OVERLAY) && (cl&OVERLAY) 分支 L3055-3095）：
     * 宿主按消息 loc 字节动态定位——ocgcore get_info_location（card.cpp L389-403）对带
     * overlay_target 的卡返回宿主的 (c, l|0x80, s)，而脚本常在怪兽尚处额外卡组时就执行
     * Overlay，此时 cs 是 EXTRA 序号，硬编码怪兽区查找会失败导致素材永挂不化滞留原格。
     * 先从原区域移除，再挂到目标 overlayed 列表与场地级 overlayCards 绘制列表，
     * 设 overlayTarget/location=OVERLAY/sequence=叠放序号；返回定位到的宿主 olcard（调用方
     * 仅在 olcard.location==MZONE 时播放堆叠动画，对齐 C++ L3086 的条件），
     * 返回 null 表示宿主尚未就位——登记待挂素材，素材仍留在 overlayCards 原地显示，
     * 待怪兽入格由 flushPendingOverlays 按 (ctrl, loc, seq) 三键补挂。
     */
    public ClientCard attachOverlayMaterial(ClientCard pcard, int oldCtrl, int oldLoc, int oldSeq,
                                            int newCtrl, int newLocBase, int xyzSeq) {
        if (pcard == null) return null;
        ClientCard olcard = field.getCard(newCtrl, newLocBase, xyzSeq);
        if (olcard == null) {
            removeCard(oldCtrl, oldLoc, oldSeq);
            pcard.controler = newCtrl;
            pcard.location = CardLocation.Overlay.value();
            pcard.overlayTarget = null;
            pcard.sequence = 0;
            if (!field.overlayCards.contains(pcard)) field.overlayCards.add(pcard);
            field.pendingOverlays.add(new PendingOverlay(pcard, newCtrl, newLocBase, xyzSeq));
            return null;
        }
        removeCard(oldCtrl, oldLoc, oldSeq);
        if (!olcard.overlayed.contains(pcard)) olcard.overlayed.add(pcard);
        if (!field.overlayCards.contains(pcard)) field.overlayCards.add(pcard);
        pcard.overlayTarget = olcard;
        pcard.controler = newCtrl;
        pcard.location = CardLocation.Overlay.value();
        pcard.sequence = olcard.overlayed.size() - 1;
        return olcard;
    }

    /**
     * 超量怪兽入格后补挂待挂素材——按到达顺序挂到 overlayed、绑定 overlayTarget/序号，
     * 各自播放 10 帧移动动画，使素材从原位置飞向该怪兽区格子并正面叠放
     *（对齐 duelclient.cpp MSG_MOVE 素材入 overlay 分支的 MoveCard(pcard, 10)）。
     */
    private void flushPendingOverlays(int ctrl, int seq, ClientCard olcard) {
        if (olcard == null || field.pendingOverlays.isEmpty()) return;
        Iterator<PendingOverlay> it = field.pendingOverlays.iterator();
        while (it.hasNext()) {
            PendingOverlay po = it.next();
            if (po == null || po.ctrl != ctrl || po.loc != 0x04 || po.seq != seq) continue;
            it.remove();
            ClientCard m = po.card;
            if (m == null) continue;
            if (!olcard.overlayed.contains(m)) olcard.overlayed.add(m);
            if (!field.overlayCards.contains(m)) field.overlayCards.add(m);
            m.overlayTarget = olcard;
            m.controler = ctrl;
            m.location = CardLocation.Overlay.value();
            m.sequence = olcard.overlayed.size() - 1;
            field.moveCardAnimated(m, 10);
        }
    }

    /**
     * 从 (oldCtrl, oldLocBase, xyzSeq) 的超量怪兽下方取出第 subSeq 张素材送入新区域
     *（对齐 duelclient.cpp MSG_MOVE 的 (pl&OVERLAY) && !(cl&OVERLAY) 分支 L3096-3124）：
     * 宿主按消息 pl 字节动态定位——超量怪兽离场时其自身 MSG_MOVE 先到达（可能已入墓地，
     * pl&0x7f=GRAVE），硬编码怪兽区查找会返回 null 导致素材无离场动画。
     * 从 overlayed 与场地级 overlayCards 移除、清 overlayTarget、加入新区域，
     * 其余素材重排序号并各自播放 2 帧归位动画；返回被取出的素材卡供调用方播放离场动画。
     */
    public ClientCard detachOverlayMaterial(int oldCtrl, int oldLocBase, int xyzSeq, int subSeq,
                                            int newCtrl, int newLoc, int newSeq, int newPos) {
        ClientCard olcard = field.getCard(oldCtrl, oldLocBase, xyzSeq);
        if (olcard == null) return null;
        if (subSeq < 0 || subSeq >= olcard.overlayed.size()) return null;
        ClientCard pcard = olcard.overlayed.get(subSeq);
        if (pcard == null) return null;
        olcard.overlayed.remove(subSeq);
        pcard.overlayTarget = null;
        pcard.position = newPos;
        field.overlayCards.remove(pcard);
        addCard(newCtrl, newLoc, newSeq, pcard);
        for (int i = 0; i < olcard.overlayed.size(); i++) {
            ClientCard m = olcard.overlayed.get(i);
            if (m == null) continue;
            m.sequence = i;
            field.moveCardAnimated(m, 2);
        }
        return pcard;
    }

    /**
     * 带超量素材的怪兽移动到怪兽区时，素材随本体重排到新格下方（对齐 duelclient.cpp
     * MSG_MOVE L3032-3038：cl==0x4 且 overlayed 非空时逐素材 MoveCard(10) + WaitFrameSignal(10)，
     * 本体延迟后再落上）；移动到其他区域时 C++ 同样让素材逐张 MoveCard（跟随本体目标格），
     * 这里统一为各素材播放 frame 帧移动动画——素材的目标位由 getCardLocation 的
     * LOCATION_OVERLAY 分支依 overlayTarget 实时求出，本体已入新格则素材飞向新格下方。
     */
    public void moveOverlayMaterials(ClientCard monster, int frame) {
        if (monster == null || monster.overlayed.isEmpty()) return;
        for (int i = 0; i < monster.overlayed.size(); i++) {
            ClientCard m = monster.overlayed.get(i);
            if (m != null) field.moveCardAnimated(m, frame);
        }
    }

    public void updateCard(int controler, int location, int sequence, ByteBuffer data) {
        ClientCard pcard = field.getCard(controler, location, sequence);
        if (pcard != null && data.remaining() >= 4) {
            int len = data.getInt();
            if (len > 4 && data.remaining() >= len - 4) {
                pcard.updateQuery(data);
            }
        }
        // 超量素材登记表重建（reload/init 路径）：QUERY_OVERLAY_CARD 仅填充各怪兽的 overlayed，
        // 据此重建场地级 overlayCards 绘制列表并绑定 overlayTarget/序号，随后统一 setCardPos 归位
        field.overlayCards.clear();
        for (int p = 0; p < 2; p++) {
            for (ClientCard m : field.players[p].monsterZone) {
                if (m == null || m.overlayed.isEmpty()) continue;
                for (int i = 0; i < m.overlayed.size(); i++) {
                    ClientCard o = m.overlayed.get(i);
                    if (o == null) continue;
                    o.overlayTarget = m;
                    o.controler = p;
                    o.location = CardLocation.Overlay.value();
                    o.sequence = i;
                    field.overlayCards.add(o);
                }
            }
        }
        for (ClientCard c : field.overlayCards) {
            if (c != null) {
                field.setCardPos(c);
                c.is_moving = false;
            }
        }
    }

    public void moveCard(ClientCard pcard, int frame) {
        field.moveCardAnimated(pcard, frame);
    }

    public void fadeCard(ClientCard pcard, int alpha, int frame) {
        if (pcard == null) return;
        pcard.animFromAlpha = pcard.curAlpha;
        pcard.animToAlpha = alpha;
        pcard.animTotalFrame = frame;
        pcard.is_fading = true;
        pcard.aniFrame = frame;
    }

    /**
     * duelclient.cpp MSG_CARD_HINT L4094-4109 的 desc_hints 部分：
     * 以三参 GetCard 定位卡片（超量素材取不到 → 与 C++ 一致直接忽略本条提示）。
     * 其余 chtype（CHINT_TURN 等）由调用方按动画需求另行处理。
     */
    public void applyCardHint(int localControler, int location, int sequence, int hintType, int value) {
        ClientCard pcard = field.getCard(localControler, location, sequence);
        if (pcard == null) return;
        if (hintType == GameField.CHINT_DESC_ADD) {
            pcard.addDescHint(value);
        } else if (hintType == GameField.CHINT_DESC_REMOVE) {
            pcard.removeDescHint(value);
        }
    }

    /** duelclient.cpp MSG_BECOME_TARGET L3500：current_chain.target.insert(pcard) */
    public void addChainTarget(int localControler, int location, int sequence) {
        ClientCard pcard = field.getCard(localControler, location, sequence);
        if (pcard == null || field.currentChain == null || field.currentChain.targets == null) return;
        if (!field.currentChain.targets.contains(pcard)) field.currentChain.targets.add(pcard);
    }

    /**
     * event_handler.cpp SetShowMark L2901-2918：对目标卡联动开关其关联图标标记。
     * 装备关系（equipTarget / equipped 集合）→ is_showequip；
     * 永续对象关系（cardTarget 对位 targetCards 及其反向 ownerTarget）→ is_showtarget；
     * 连锁对象（chain_card ↔ target 双向）→ is_showchaintarget。
     * 桌面版由悬停触发，移动端语义为“点击卡片时展示”，由 FieldTouchPicker 挂接。
     */
    public void setShowMark(ClientCard pcard, boolean enable) {
        if (pcard == null) return;
        if (pcard.equipTarget != null) pcard.equipTarget.is_showequip = enable;
        for (ClientCard c : pcard.equipped) c.is_showequip = enable;
        for (ClientCard c : pcard.targetCards) c.is_showtarget = enable;
        for (ClientCard c : pcard.ownerTarget) c.is_showtarget = enable;
        for (ChainInfo ch : field.chains) {
            if (pcard == ch.chainCard) {
                for (ClientCard t : ch.targets) t.is_showchaintarget = enable;
            }
            if (ch.targets.contains(pcard) && ch.chainCard != null) {
                ch.chainCard.is_showchaintarget = enable;
            }
        }
    }

    public void clearCommandFlag() {
        for (ClientCard c : field.activatableCards)
            if (c != null) {
                c.cmdFlag = 0;
                c.chain_code = 0;
                c.is_selectable = false;
                c.is_selected = false;
            }
        for (ClientCard c : field.summonableCards)
            if (c != null) {
                c.cmdFlag = 0;
                c.is_selectable = false;
                c.is_selected = false;
            }
        for (ClientCard c : field.spsummonableCards)
            if (c != null) {
                c.cmdFlag = 0;
                c.is_selectable = false;
                c.is_selected = false;
            }
        for (ClientCard c : field.msetableCards)
            if (c != null) {
                c.cmdFlag = 0;
                c.is_selectable = false;
                c.is_selected = false;
            }
        for (ClientCard c : field.ssetableCards)
            if (c != null) {
                c.cmdFlag = 0;
                c.is_selectable = false;
                c.is_selected = false;
            }
        for (ClientCard c : field.reposableCards)
            if (c != null) {
                c.cmdFlag = 0;
                c.is_selectable = false;
                c.is_selected = false;
            }
        for (ClientCard c : field.attackableCards)
            if (c != null) {
                c.cmdFlag = 0;
                c.is_selectable = false;
                c.is_selected = false;
            }
        for (int i = 0; i < 2; i++) {
            field.deckAct[i] = false;
            field.extraAct[i] = false;
            field.graveAct[i] = false;
            field.removeAct[i] = false;
            field.pzoneAct[i] = false;
        }
        field.contiCards.clear();
        field.contiAct = false;
        field.activatableCards.clear();
        field.summonableCards.clear();
        field.spsummonableCards.clear();
        field.msetableCards.clear();
        field.ssetableCards.clear();
        field.reposableCards.clear();
        field.attackableCards.clear();
    }

    public void clearSelect() {
        for (ClientCard c : field.selectableCards) {
            if (c != null) {
                c.is_selectable = false;
                c.is_selected = false;
            }
        }
        for (ClientCard c : field.selectedCards) {
            if (c != null) {
                c.is_selectable = false;
                c.is_selected = false;
            }
        }
        for (ClientCard c : field.selectsumAll) {
            if (c != null) {
                c.is_selectable = false;
                c.is_selected = false;
            }
        }
        for (ClientCard c : field.selectsumCards) {
            if (c != null) {
                c.is_selectable = false;
                c.is_selected = false;
            }
        }
    }

    public void clearChainSelect() {
        for (ClientCard c : field.activatableCards) {
            if (c != null) {
                c.cmdFlag = 0;
                c.chain_code = 0;
                c.is_selectable = false;
                c.is_selected = false;
            }
        }
        for (int i = 0; i < 2; i++) {
            field.deckAct[i] = false;
            field.extraAct[i] = false;
            field.graveAct[i] = false;
            field.removeAct[i] = false;
            field.pzoneAct[i] = false;
        }
        field.contiCards.clear();
        field.contiAct = false;
    }

    public void swapField() {
        PlayerField temp = field.players[0];
        field.players[0] = field.players[1];
        field.players[1] = temp;
        for (int p = 0; p < 2; p++) {
            updateCardControler(field.players[p], p);
        }
        int tmpExtraP = field.extraPCount[0];
        field.extraPCount[0] = field.extraPCount[1];
        field.extraPCount[1] = tmpExtraP;
        for (ClientCard card : field.overlayCards) {
            if (card != null) card.controler = 1 - card.controler;
        }
        for (ChainInfo ch : field.chains) {
            ch.controler = 1 - ch.controler;
        }
        field.disabledField = (field.disabledField >> 16) | (field.disabledField << 16);
    }

    public static boolean clientCardSort(ClientCard c1, ClientCard c2) {
        if (c1.is_selected != c2.is_selected)
            return !c1.is_selected;
        int cp1 = c1.overlayTarget != null ? c1.overlayTarget.controler : c1.controler;
        int cp2 = c2.overlayTarget != null ? c2.overlayTarget.controler : c2.controler;
        if (cp1 != cp2) return cp1 < cp2;
        if (c1.location != c2.location) return c1.location < c2.location;
        if (c1.location == CardLocation.Overlay.value()) {
            if (c1.overlayTarget != c2.overlayTarget)
                return c1.overlayTarget.sequence < c2.overlayTarget.sequence;
            else
                return c1.sequence < c2.sequence;
        } else if (c1.location == CardLocation.Deck.value()) {
            return c1.sequence > c2.sequence;
        } else if ((c1.location & (CardLocation.Grave.value() | CardLocation.Removed.value() | CardLocation.Extra.value())) != 0) {
            return c1.sequence > c2.sequence;
        } else {
            return c1.sequence < c2.sequence;
        }
    }

    private void updateCardControler(PlayerField playerField, int controler) {
        updateListControler(playerField.deck, controler);
        updateListControler(playerField.hand, controler);
        updateListControler(playerField.monsterZone, controler);
        updateListControler(playerField.spellZone, controler);
        updateListControler(playerField.grave, controler);
        updateListControler(playerField.removed, controler);
        updateListControler(playerField.extra, controler);
        if (playerField.fieldSpell != null) {
            playerField.fieldSpell.controler = controler;
        }
    }

    private void updateListControler(List<ClientCard> list, int controler) {
        for (ClientCard card : list) {
            if (card != null) {
                card.controler = controler;
            }
        }
    }
}
