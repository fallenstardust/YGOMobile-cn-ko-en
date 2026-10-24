package cn.garymb.ygomobile.game.gamefield;
import cn.garymb.ygomobile.game.engine.GameEngine;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.render.card.CardDetailPanel;
import cn.garymb.ygomobile.ui.dialogs.duel.CardSelectDialog;
import ocgcore.DataManager;
import ocgcore.enums.CardType;
/**
 * 场上/手牌选择会话协作类（由 GameFieldController 按 // === 分栏拆分而来）：
 * 放置区域选择、场上/手牌直接选择、场上/手牌合计选择（MSG_SELECT_SUM）。
 * 通过包级私有直连门面 GameFieldController 的共享状态（engine/viewController/activity）
 * 与底层提示/命令菜单原语（ctl.showHint/ctl.showCardCommandMenu/ctl.dismissCmdMenu）。
 */
class FieldSelectManager extends FieldSelectManagerBase {

    /**
     * Constructor (package entry of the split chain, called by GameFieldController;
     * the ctl assignment lives in the base layer).
     */
    FieldSelectManager(GameFieldController ctl) {
        super(ctl);
    }

    // === 放置区域选择 ===

    void beginPlaceSelect(boolean isDisfield) {
        // 灵摆召唤的位置选择先播放“左灵摆刻度卡蚂蚁线 → 右灵摆刻度卡蚂蚁线 →
        // 场上可放置区蚂蚁线供选点”的预演（对齐用户描述的灵摆召唤动画）；非灵摆召唤直接
        // 进入真正的位置选择会话（参考实现：C++ 无此分阶段动画，于本移植版自行编排）
        if (!isDisfield && startPendulumScalePrelude()) {
            return; // 预演结束后会自动回调 doBeginPlaceSelect
        }
        doBeginPlaceSelect(isDisfield);
    }

    /**
     * 若本次 MSG_SELECT_PLACE 为灵摆召唤（灵摆召唤方两侧灵摆区都立着灵摆怪、
     * 且放置掩码含主怪兽区），则先对左刻度（spellZone[6]）后对右刻度（spellZone[7]）施加
     * 行进蚂蚁线（复用 revealHighlightCards），再高亮场上可放置区。
     *
     * @return true=已启动预演（真正的选择会话将在预演末尾开启）；false=非灵摆召唤，需直接选择
     */
    protected boolean startPendulumScalePrelude() {
        GameEngine engine = ctl.engine;
        if (engine == null) return false;
        GameField field = engine.getField();
        int summonLocal = engine.localPlayer(engine.selectFieldPlayer & 1);
        if (summonLocal < 0 || summonLocal > 1) return false;
        GameField.PlayerField pf = field.players[summonLocal];
        if (pf == null) return false;
        // 本移植版灵摆区固定 spellZone[6]=左刻度、spellZone[7]=右刻度（见 tryAutoPlaceSelect 注释）
        ClientCard left = asActivePendulumScale(pf, 6);
        ClientCard right = asActivePendulumScale(pf, 7);
        if (left == null || right == null) return false; // 灵摆召唤需两侧刻度都立着
        // 放置掩码须含主怪兽区（0-4 我方 / 16-20 对方），排除魔陷/灵摆区放置
        int mask = engine.selectFieldMask;
        if ((mask & (0x1f | 0x1f0000)) == 0) return false;
        final long stepMs = 480L;
        // 预演共耗时约 1s（两个 480ms 台阶），其间本次询问可能被撤回（局面回退、询问重挂）：
        // 捕获询问代次令牌，到期时代次已变则整段预演作废——否则它会拿已失效的 selectFieldMask
        // 开启一个旧询问语义的选格会话，玩家随后的点击会把选格应答发进新到达的其他询问里
        final long token = engine.captureQuestionToken();
        field.revealHighlightCards.clear();
        field.revealHighlightCards.add(left);
        if (ctl.viewController != null) ctl.viewController.invalidate();
        engine.mainHandler.postDelayed(() -> {
            if (engine.isQuestionStale(token)) {
                field.revealHighlightCards.clear();
                if (ctl.viewController != null) ctl.viewController.invalidate();
                return;
            }
            field.revealHighlightCards.clear();
            field.revealHighlightCards.add(right);
            if (ctl.viewController != null) ctl.viewController.invalidate();
            engine.mainHandler.postDelayed(() -> {
                field.revealHighlightCards.clear();
                if (ctl.viewController != null) ctl.viewController.invalidate();
                if (engine.isQuestionStale(token)) return;
                doBeginPlaceSelect(false);
            }, stepMs);
        }, stepMs);
        return true;
    }

    /** 取灵摆区指定序号上已揭示的灵摆怪（非空、code!=0、TYPE_PENDULUM），否则 null */
    protected static ClientCard asActivePendulumScale(GameField.PlayerField pf, int seq) {
        if (pf.spellZone.size() <= seq) return null;
        ClientCard c = pf.spellZone.get(seq);
        if (c == null || c.code == 0) return null;
        // ClientCard.type may remain 0: C++ ClientCard::SetCode queries local DB for type,
        // but this Java port's setCode only writes code. Fallback to DB lookup to align.
        long type = c.type;
        if (type == 0) {
            ocgcore.data.Card cardData = DataManager.get().getCardManager().getCard(c.code);
            if (cardData != null) type = cardData.Type;
        }
        if ((type & CardType.Pendulum.getId()) == 0) return null;
        return c;
    }

    protected void doBeginPlaceSelect(boolean isDisfield) {
        isPlaceSelecting = true;
        int mask = ctl.engine.selectFieldMask;
        int count = ctl.engine.selectFieldCount;
        placeSelectRemain = count > 0 ? count : 1;
        placeSelectCancelable = count == 0;
        selectedFieldBits = 0;
        ctl.viewController.highlightField(mask);
        // 提示栏文本由 GameMessageParser 的 onSelectPlace/onSelectDisfield 走 strings.conf
        // 系统字符串下发，此处不再叠加硬编码引导（对齐 gframe：仅 stHintMsg 一处来源）
        // count==0（select_cancelable）→ 显示「取消」按钮（对齐 gframe ShowCancelOrFinishButton(1)）
        CardDetailPanel panel = ctl.activity.getCardDetailPanel();
        if (panel != null) panel.updateCancelOrFinishButton(false, placeSelectCancelable, false);
    }

    /**
     * 自动放置（复刻 gframe duelclient.cpp MSG_SELECT_PLACE 自动放置 L2211-2264）：
     * chkMAutoPos/chkSTAutoPos 勾选且可选区域含怪兽区/魔陷区时，按优先级
     *（我方怪兽→我方魔陷→我方灵摆→对方怪兽→对方魔陷→对方灵摆）选区自动应答
     * byte[3]{player, location, sequence}；chkRandomPos 决定普通区是否随机取位
     *
     * @return true=已自动放置并应答，false=需弹窗手动选择
     */
    boolean tryAutoPlaceSelect() {
        if (ctl.engine == null) return false;
        // 多区域（count>1）需玩家逐个点选，自动放置只适用于单区域
        if (ctl.engine.selectFieldCount > 1) return false;
        AppsSettings settings = AppsSettings.get();
        int mask = ctl.engine.selectFieldMask;
        // 对齐 gframe 条件：怪兽区可选(0x7f007f)看 chkMAutoPos，否则看 chkSTAutoPos
        if ((mask & 0x7f007f) != 0) {
            if (settings.getIntSettings("chkMAutoPos", 0) != 1) return false;
        } else {
            if (settings.getIntSettings("chkSTAutoPos", 0) != 1) return false;
        }

        int filter;
        int respLocation;
        int respPlayer;
        boolean pzone;
        if ((mask & 0x7f) != 0) {
            respPlayer = ctl.engine.localPlayer(0);
            respLocation = 0x04;
            filter = mask & 0x7f;
            pzone = false;
        } else if ((mask & 0x3f00) != 0) {
            respPlayer = ctl.engine.localPlayer(0);
            respLocation = 0x08;
            filter = (mask >> 8) & 0x3f;
            pzone = false;
        } else if ((mask & 0xc000) != 0) {
            respPlayer = ctl.engine.localPlayer(0);
            respLocation = 0x08;
            filter = (mask >> 14) & 0x3;
            pzone = true;
        } else if ((mask & 0x7f0000) != 0) {
            respPlayer = ctl.engine.localPlayer(1);
            respLocation = 0x04;
            filter = (mask >> 16) & 0x7f;
            pzone = false;
        } else if ((mask & 0x3f000000) != 0) {
            respPlayer = ctl.engine.localPlayer(1);
            respLocation = 0x08;
            filter = (mask >> 24) & 0x3f;
            pzone = false;
        } else if ((mask & 0xc0000000) != 0) {
            respPlayer = ctl.engine.localPlayer(1);
            respLocation = 0x08;
            filter = (mask >>> 30) & 0x3;
            pzone = true;
        } else {
            return false;
        }

        int seq;
        if (!pzone) {
            if (settings.getIntSettings("chkRandomPos", 0) == 1) {
                // 随机取位（对齐 gframe chkRandomPos：dist(0,6) 直到命中可选位）
                do {
                    seq = random.nextInt(7);
                } while ((filter & (1 << seq)) == 0);
            } else {
                // 固定次序（对齐 gframe：0x40→6, 0x20→5, 0x4→2, 0x2→1, 0x8→3, 0x1→0, 0x10→4）
                if ((filter & 0x40) != 0) seq = 6;
                else if ((filter & 0x20) != 0) seq = 5;
                else if ((filter & 0x4) != 0) seq = 2;
                else if ((filter & 0x2) != 0) seq = 1;
                else if ((filter & 0x8) != 0) seq = 3;
                else if ((filter & 0x1) != 0) seq = 0;
                else seq = 4;
            }
        } else {
            // 灵摆区：序列固定 6(左)/7(右)，gframe 不对灵摆区随机取位
            seq = (filter & 0x1) != 0 ? 6 : 7;
        }

        ByteBuffer buf = ByteBuffer.allocate(3);
        buf.put((byte) respPlayer);
        buf.put((byte) respLocation);
        buf.put((byte) seq);
        ctl.engine.sendResponse(buf.array());
        return true;
    }

    boolean cancelPlaceSelect() {
        if (!isPlaceSelecting) return false;
        // 协议应答需要协议侧玩家索引：localPlayer 为对合映射，
        // localPlayer(0) 即我方对应的协议索引（先攻=0/后攻=1），
        // selfType 是座位号，换座场景下不能直接使用
        ByteBuffer buf = ByteBuffer.allocate(3);
        buf.put((byte) ctl.engine.localPlayer(0));
        buf.put((byte) 0);
        buf.put((byte) 0);
        ctl.engine.sendResponse(buf.array());
        finishPlaceSelect();
        return true;
    }

    /**
     * 撤回后无应答复位一切进行中的选择会话（选格 / 选卡 / 合计 / UNSELECT）。
     * 与 {@link #cancelPlaceSelect()} 的区别：本方法只清状态，绝不对服务端发任何应答——
     * 这些会话由已被撤销的询问建立，任其存活则下一次点击会把旧询问语义的应答（选格三元组、
     * 选卡索引）发进回退后的新局面 → 非法应答 → MSG_RETRY。回退后的新局面若仍是个询问，
     * 会由新到达的 SELECT 重新开启自己的会话。
     */
    void abortSelectSessions() {
        if (isPlaceSelecting) finishPlaceSelect();
        if (isCardSelecting || isUnselectSelecting || isSumSelecting) endCardSelect();
    }

    // === 区域点击处理（来自 DuelFieldManager） ===

    void onZoneClick(int player, int location, int sequence, float tapX, float tapY) {
        if (ctl.engine == null) return;
        GameField field = ctl.engine.getField();
        if (isPlaceSelecting) {
            handlePlaceSelection(player, location, sequence);
            return;
        }
        ClientCard card = field.getCard(player, location, sequence);
        if (card != null && card.cmdFlag != 0) {
            ctl.showCardCommandMenu(card, tapX, tapY);
            return;
        }
        // 无可执行命令：关闭残留的命令菜单，只显示卡片信息
        ctl.dismissCmdMenu();
        // 堆叠区点击：查看卡片信息
        boolean isPile = (location == 0x01 || location == 0x10
                || location == 0x20 || location == 0x40);
        // 回放态（纯消息驱动）：无选择通讯故不下发 cmdFlag，双方卡组/额外/墓地/除外
        // 恒为公开信息（卡码由 MSG_START 回填 + MOVE 携带），点堆叠区直接弹整列表正面查看
        if (ctl.engine.replayMode && isPile && showReplayPileView(field, player, location)) {
            return;
        }
        // 卡码门槛统一用 displayCode：场上里侧卡一经公开（POS_REVEAL 解遮蔽 / 泡影类
        // POS_CHANGE 携码）后即便被遮蔽刷新洗回 code，仍按 chain_code 备份弹详情
        if (isPile && card != null && card.displayCode() > 0) {
            ctl.activity.showCardInfoPanel(card);
            return;
        }
        // 场上卡片点击：查看信息
        if (card != null && card.displayCode() > 0) {
            ctl.activity.showCardInfoPanel(card);
        }
    }

    /** 回放态堆叠区查看：取该区首张已知卡（实况卡码或前卡码 chain_code）作视角侧样本
     *  （controler/location 与 players[] 同索引），交由列表弹窗展开全部卡片；
     *  卡组/额外整区无已知卡码时仍问录像头部权威卡码（ReplayPlayer.getReplayZoneCodes）：
     *  头部有数据即弹窗展开全部，不再回落「无卡可看」（修复回填时序竞争导致的时灵时不灵） */
    protected boolean showReplayPileView(GameField field, int player, int location) {
        List<ClientCard> list = field.players[player].getLocationList(location);
        if (list == null || list.isEmpty()) return false;
        ClientCard sample = null;
        for (ClientCard c : list) {
            if (c == null) continue;
            if (sample == null) sample = c;
            if (c.code > 0 || c.chain_code > 0) {
                sample = c;
                break;
            }
        }
        if (sample == null) return false;
        if (sample.code <= 0 && sample.chain_code <= 0) {
            List<Integer> auth = ctl.engine.getReplayZoneCodes(player, location);
            if (auth == null || auth.isEmpty()) return false;
        }
        ctl.showReplayPileView(sample);
        return true;
    }

    protected void handlePlaceSelection(int player, int location, int sequence) {
        int bitPos = getZoneBitPos(player, location, sequence);
        if (bitPos < 0 || (ctl.engine.selectFieldMask & (1 << bitPos)) == 0) {
            return;
        }
        // 点击已选区域 → 取消该位（对齐 gframe：selected_field 位取反，select_min 回升）
        if ((selectedFieldBits & (1 << bitPos)) != 0) {
            selectedFieldBits &= ~(1 << bitPos);
            placeSelectRemain++;
            return;
        }
        selectedFieldBits |= (1 << bitPos);
        placeSelectRemain--;
        if (placeSelectRemain > 0) {
            return;
        }
        // 数量满足：按 gframe 固定次序组装多条应答并发送
        byte[] resp = buildPlaceResponse(selectedFieldBits);
        finishPlaceSelect();
        ctl.engine.sendResponse(resp);
    }

    /** 结束放置区域选择会话：清状态、去高亮、隐藏 cancelOrFinish（联动恢复洗切手卡） */
    protected void finishPlaceSelect() {
        isPlaceSelecting = false;
        selectedFieldBits = 0;
        placeSelectRemain = 0;
        ctl.viewController.clearHighlight();
        CardDetailPanel panel = ctl.activity.getCardDetailPanel();
        if (panel != null) panel.hideCancelOrFinishButton();
    }

    /**
     * 组装放置/禁用区域应答：对齐 gframe event_handler.cpp L1411-1448 的固定次序——
     * 我方 MZone(位0-6) → 我方 SZone(位8-15) → 对方 MZone(位16-22) → 对方 SZone(位24-31)，
     * 每个置位区域追加 3 字节 {协议玩家索引, LOCATION, sequence}。
     */
    protected byte[] buildPlaceResponse(int bits) {
        int myP = ctl.engine.localPlayer(0);
        int oppP = ctl.engine.localPlayer(1);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        for (int i = 0; i < 7; i++) if ((bits & (1 << i)) != 0) { out.write(myP); out.write(0x04); out.write(i); }
        for (int i = 0; i < 8; i++) if ((bits & (1 << (8 + i))) != 0) { out.write(myP); out.write(0x08); out.write(i); }
        for (int i = 0; i < 7; i++) if ((bits & (1 << (16 + i))) != 0) { out.write(oppP); out.write(0x04); out.write(i); }
        for (int i = 0; i < 8; i++) if ((bits & (1 << (24 + i))) != 0) { out.write(oppP); out.write(0x08); out.write(i); }
        return out.toByteArray();
    }

    protected int getZoneBitPos(int player, int location, int sequence) {
        // player 为本地方位索引(0=我方,1=对方)，mask 已归一化：0-15=我方, 16-31=对方
        int base = (player == 0) ? 0 : 16;
        if (location == 0x04) return base + sequence;
        if (location == 0x08) {
            if (sequence < 6) return base + 8 + sequence;
            if (sequence == 6) return base + 14;
            if (sequence == 7) return base + 15;
        }
        return -1;
    }

    // === 场上/手牌直接选择（对齐 gframe drawing.cpp DrawCard 卡片选择轮廓，不弹 CardSelectDialog）===

    /**
     * 进入场上选择模式：候选卡标记 is_selectable（GameFieldView 绘制黄色虚线行进轮廓），
     * 玩家直接点击场上/手牌卡片切换选中；达到 max 自动应答，或点「完成选择」按钮应答
     */
    void beginCardSelect(List<CardSelectDialog.CardItem> items, int min, int max, boolean cancelable) {
        if (ctl.engine == null || items == null) return;
        GameField field = ctl.engine.getField();
        field.clearSelect();
        field.selectableCards.clear();
        field.selectedCards.clear();
        field.selectsumAll.clear();
        field.selectsumCards.clear();
        cardSelectClickOrder.clear();
        isSumSelecting = false;
        isUnselectSelecting = false;
        cardSelectMin = min;
        cardSelectMax = max;
        cardSelectCancelable = cancelable;
        for (CardSelectDialog.CardItem it : items) {
            // 消息里的 ctrl 是协议索引，getCard 需本地索引（localPlayer 为对合映射）
            int lp = ctl.engine.localPlayer(it.controler);
            ClientCard card = field.getCard(lp, it.location & 0x7f, it.sequence);
            if (card == null) continue;
            card.is_selectable = true;
            card.is_selected = false;
            card.select_seq = it.selectSeq;
            field.selectableCards.add(card);
        }
        isCardSelecting = true;
        if (ctl.viewController != null) ctl.viewController.invalidate();
    }

    /**
     * 进入 UNSELECT 场上直接选择会话（MSG_SELECT_UNSELECT_CARD，连接召唤手续逐步选素材）：
     * 对齐 duelclient.cpp L2003-2051——count1 可选卡 is_selectable/未选中（行进虚线），
     * count2 已确认素材 is_selectable 且 is_selected（实线框，不入 selected_cards，同 gframe
     * selected_cards 仅存玩家点选卡片的语义）；点击任意可选卡立即应答（event_handler.cpp
     * L1520-1535），完成/取消按钮应答 -1（gframe L2067-2077 select_cancelable=finishable||cancelable）
     */
    void beginUnselectCardSelect(List<CardSelectDialog.CardItem> items, int selectableCount,
                                 int min, int max, boolean finishable, boolean cancelable) {
        if (ctl.engine == null || items == null || items.isEmpty()) return;
        GameField field = ctl.engine.getField();
        field.clearSelect();
        field.selectableCards.clear();
        field.selectedCards.clear();
        field.selectsumAll.clear();
        field.selectsumCards.clear();
        cardSelectClickOrder.clear();
        isSumSelecting = false;
        isUnselectSelecting = true;
        isCardSelecting = true;
        cardSelectMin = min;
        cardSelectMax = max;
        // gframe L1989：select_cancelable = finishable || cancelable
        cardSelectCancelable = finishable || cancelable;
        for (int i = 0; i < items.size(); i++) {
            CardSelectDialog.CardItem it = items.get(i);
            ClientCard card = field.getCard(ctl.engine.localPlayer(it.controler),
                    it.location & 0x7f, it.sequence);
            if (card == null) continue;
            card.is_selectable = true;
            // count2（i≥selectableCount）预选中 → 实线框（gframe L2043）
            card.is_selected = i >= selectableCount;
            card.select_seq = it.selectSeq;
            field.selectableCards.add(card);
        }
        if (ctl.viewController != null) ctl.viewController.invalidate();
        CardDetailPanel panel = ctl.activity.getCardDetailPanel();
        if (panel != null) {
            // gframe L2067-2077：finishable → 「完成」，否则 cancelable → 「取消」，皆无 → 隐藏
            panel.updateCancelOrFinishButton(finishable, cancelable, false);
        }
    }

    void handleCardSelection(int player, int location, int sequence) {
        if (ctl.engine == null) return;
        GameField field = ctl.engine.getField();
        ClientCard card = field.getCard(player, location, sequence);
        // 合计选择（SUM）模式：交给专用点击-重校验循环（event_handler.cpp L1557-1566）
        if (isSumSelecting) {
            handleSumSelectionClick(card);
            return;
        }
        // UNSELECT 场上模式（连接手续逐步选素材，event_handler.cpp L1520-1535）：
        // 点击任意 is_selectable 卡 → 立即应答 [1, select_seq]，无效点击静默忽略（同 C++ break）
        if (isUnselectSelecting) {
            if (card != null && card.is_selectable) {
                ByteBuffer buf = ByteBuffer.allocate(2);
                buf.order(ByteOrder.LITTLE_ENDIAN);
                buf.put((byte) 1);
                buf.put((byte) card.select_seq);
                ctl.engine.sendResponse(buf.array());
                endCardSelect();
            }
            return;
        }
        if (card == null || !card.is_selectable) {
            return;
        }
        if (card.is_selected) {
            card.is_selected = false;
            field.selectedCards.remove(card);
            cardSelectClickOrder.remove(Integer.valueOf(card.select_seq));
        } else {
            if (cardSelectMax > 0 && cardSelectClickOrder.size() >= cardSelectMax) {
                return;
            }
            card.is_selected = true;
            field.selectedCards.add(card);
            cardSelectClickOrder.add(card.select_seq);
        }
        if (ctl.viewController != null) ctl.viewController.invalidate();
        // 达到要求数量自动应答通讯
        if (cardSelectMax > 0 && cardSelectClickOrder.size() >= cardSelectMax) {
            confirmCardSelect();
            return;
        }
        // 对齐 event_handler.cpp L1475-1479：已达 min 且候选全部选中 → 无需再等，自动应答
        if (cardSelectClickOrder.size() >= cardSelectMin
                && cardSelectClickOrder.size() >= field.selectableCards.size()) {
            confirmCardSelect();
            return;
        }
        CardDetailPanel panel = ctl.activity.getCardDetailPanel();
        if (panel != null) {
            panel.updateCancelOrFinishButton(cardSelectClickOrder.size() >= cardSelectMin,
                    cardSelectCancelable, !cardSelectClickOrder.isEmpty());
        }
    }

    /** 「完成选择」按钮：达到 min 即应答，否则可取消时应答 -1 */
    boolean finishCardSelect() {
        if (!isCardSelecting) return false;
        // UNSELECT 场上会话：无部分确认（单卡点击即提交），完成/取消统一应答 -1
        //（event_handler.cpp L968-971：UNSELECT 的 CancelOrFinish = 发送 -1）
        if (isUnselectSelecting) {
            return cancelCardSelect();
        }
        // SUM 模式：仅在 selectReady 时应答（event_handler.cpp L3156-3158 CancelOrFinish），SUM 不可取消
        if (isSumSelecting) {
            if (ctl.engine.getField().selectReady) {
                sendSumSelectResponse();
                return true;
            }
            return false;
        }
        if (cardSelectClickOrder.size() >= cardSelectMin) {
            return confirmCardSelect();
        }
        return cancelCardSelect();
    }

    protected boolean confirmCardSelect() {
        if (!isCardSelecting || ctl.engine == null) return false;
        if (cardSelectClickOrder.size() < cardSelectMin) {
            return false;
        }
        // C++ SetResponseSelectedCards：respbuf[0]=len，其后按点击顺序填 select_seq
        ByteBuffer buf = ByteBuffer.allocate(1 + cardSelectClickOrder.size());
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.put((byte) cardSelectClickOrder.size());
        for (int seq : cardSelectClickOrder) {
            buf.put((byte) seq);
        }
        ctl.engine.sendResponse(buf.array());
        endCardSelect();
        // 立即停止蚂蚁线动画（先于网络应答）
        CardDetailPanel panel = ctl.activity.getCardDetailPanel();
        if (panel != null) {
            panel.stopAntsHighlightImmediately();
        }
        return true;
    }

    protected boolean cancelCardSelect() {
        if (!isCardSelecting || isSumSelecting) return false;
        if (cardSelectCancelable && cardSelectClickOrder.isEmpty()) {
            ctl.activity.sendResponseInt(-1);
            endCardSelect();
            return true;
        }
        return false;
    }
}
