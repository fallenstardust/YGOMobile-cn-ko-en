package cn.garymb.ygomobile.game;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.render.CardDetailPanel;
import cn.garymb.ygomobile.ui.dialogs.CardSelectDialog;
import ocgcore.DataManager;

/**
 * 场上/手牌选择会话协作类（由 GameFieldController 按 // === 分栏拆分而来）：
 * 放置区域选择、场上/手牌直接选择、场上/手牌合计选择（MSG_SELECT_SUM）。
 * 通过包级私有直连门面 GameFieldController 的共享状态（engine/viewController/activity）
 * 与底层提示/命令菜单原语（ctl.showHint/ctl.showCardCommandMenu/ctl.dismissCmdMenu）。
 */
class FieldSelectManager {

    private final GameFieldController ctl;

    // === 放置区域选择会话 ===
    boolean isPlaceSelecting = false;
    // 对齐 gframe event_handler.cpp MSG_SELECT_PLACE/DISFIELD L1398-1458：
    // 需累计选择 selectFieldCount 个区域后一次性应答（多条 {player,location,sequence}），
    // 而非首个点击即应答——此前只发 1 格，count>1 时响应长度不足被服务端判非法→
    // MSG_RETRY→重放上限后放弃→决斗卡死。selectedFieldBits 累计已选区域位（与
    // getZoneBitPos 同一 32 位编码：位 0-6 我方MZone、8-15 我方SZone、16-22 对方MZone、24-31 对方SZone）。
    int placeSelectRemain = 0;
    boolean placeSelectCancelable = false;
    int selectedFieldBits = 0;
    // === 场上/手牌直接选择会话（MSG_SELECT_CARD/SELECT_TRIBUTE_CARD 候选全在场内时，不弹 CardSelectDialog）===
    boolean isCardSelecting = false;
    int cardSelectMin = 0;
    int cardSelectMax = 0;
    boolean cardSelectCancelable = false;
    final List<Integer> cardSelectClickOrder = new ArrayList<>();
    // === MSG_SELECT_UNSELECT_CARD 场上会话（连接召唤手续逐步选素材：点卡即单卡提交，
    //  count2 已确认素材画实线框；gframe duelclient.cpp L1985-2079）===
    boolean isUnselectSelecting = false;
    // === 场上/手牌合计选择会话（MSG_SELECT_SUM 候选全在场内时不弹 CardSelectDialog，
    //  忠实移植 client_field.cpp CheckSelectSum/ShowSelectSum 的点击-重校验循环）===
    boolean isSumSelecting = false;
    /** 合计选择提示前缀（对齐 ShowSelectSum display_hint：select_hint 消费后的 GetDesc/GetSysString(560)） */
    String sumSelectTitle = "";
    private final Random random = new Random();

    FieldSelectManager(GameFieldController ctl) {
        this.ctl = ctl;
    }

    // === 放置区域选择 ===

    void beginPlaceSelect(boolean isDisfield) {
        isPlaceSelecting = true;
        int mask = ctl.engine.selectFieldMask;
        int count = ctl.engine.selectFieldCount;
        placeSelectRemain = count > 0 ? count : 1;
        placeSelectCancelable = count == 0;
        selectedFieldBits = 0;
        ctl.viewController.highlightField(mask);
        String msg = isDisfield ? "请选择要禁用的区域" : "请选择放置位置";
        if (placeSelectCancelable) {
            msg += "（可不选，点「取消」跳过）";
        } else if (placeSelectRemain > 1) {
            msg += "（需选择 " + placeSelectRemain + " 个区域）";
        }
        ctl.showHint(msg, 3000);
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

    // === 区域点击处理（来自 DuelFieldManager） ===

    void onZoneClick(int player, int location, int sequence, float tapX, float tapY) {
        if (ctl.engine == null) return;
        GameField field = ctl.engine.getField();
        if (isPlaceSelecting) {
            handlePlaceSelection(player, location, sequence);
            return;
        }
        GameField.ClientCard card = field.getCard(player, location, sequence);
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
        if (isPile && card != null && card.code > 0) {
            ctl.activity.showCardInfoPanel(card);
            return;
        }
        // 场上卡片点击：查看信息
        if (card != null && card.code > 0) {
            ctl.activity.showCardInfoPanel(card);
        }
    }

    /** 回放态堆叠区查看：取该区首张已知卡作视角侧样本（controler/location 与 players[] 同索引），
     *  交由列表弹窗展开全部卡片；整区无已知卡码时返回 false 回落原有点单卡逻辑 */
    private boolean showReplayPileView(GameField field, int player, int location) {
        List<GameField.ClientCard> list = field.players[player].getLocationList(location);
        if (list == null || list.isEmpty()) return false;
        GameField.ClientCard sample = null;
        for (GameField.ClientCard c : list) {
            if (c != null && c.code > 0) {
                sample = c;
                break;
            }
        }
        if (sample == null) return false;
        ctl.showReplayPileView(sample);
        return true;
    }

    private void handlePlaceSelection(int player, int location, int sequence) {
        int bitPos = getZoneBitPos(player, location, sequence);
        if (bitPos < 0 || (ctl.engine.selectFieldMask & (1 << bitPos)) == 0) {
            ctl.showHint("该区域不可选择", 3000);
            return;
        }
        // 点击已选区域 → 取消该位（对齐 gframe：selected_field 位取反，select_min 回升）
        if ((selectedFieldBits & (1 << bitPos)) != 0) {
            selectedFieldBits &= ~(1 << bitPos);
            placeSelectRemain++;
            ctl.showHint("还需选择 " + placeSelectRemain + " 个区域", 2500);
            return;
        }
        selectedFieldBits |= (1 << bitPos);
        placeSelectRemain--;
        if (placeSelectRemain > 0) {
            ctl.showHint("还需选择 " + placeSelectRemain + " 个区域", 2500);
            return;
        }
        // 数量满足：按 gframe 固定次序组装多条应答并发送
        byte[] resp = buildPlaceResponse(selectedFieldBits);
        finishPlaceSelect();
        ctl.engine.sendResponse(resp);
    }

    /** 结束放置区域选择会话：清状态、去高亮、隐藏 cancelOrFinish（联动恢复洗切手卡） */
    private void finishPlaceSelect() {
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
    private byte[] buildPlaceResponse(int bits) {
        int myP = ctl.engine.localPlayer(0);
        int oppP = ctl.engine.localPlayer(1);
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        for (int i = 0; i < 7; i++) if ((bits & (1 << i)) != 0) { out.write(myP); out.write(0x04); out.write(i); }
        for (int i = 0; i < 8; i++) if ((bits & (1 << (8 + i))) != 0) { out.write(myP); out.write(0x08); out.write(i); }
        for (int i = 0; i < 7; i++) if ((bits & (1 << (16 + i))) != 0) { out.write(oppP); out.write(0x04); out.write(i); }
        for (int i = 0; i < 8; i++) if ((bits & (1 << (24 + i))) != 0) { out.write(oppP); out.write(0x08); out.write(i); }
        return out.toByteArray();
    }

    private int getZoneBitPos(int player, int location, int sequence) {
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
            GameField.ClientCard card = field.getCard(lp, it.location & 0x7f, it.sequence);
            if (card == null) continue;
            card.is_selectable = true;
            card.is_selected = false;
            card.select_seq = it.selectSeq;
            field.selectableCards.add(card);
        }
        isCardSelecting = true;
        if (ctl.viewController != null) ctl.viewController.invalidate();
        ctl.showHint("点击高亮的卡片进行选择", 3000);
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
            GameField.ClientCard card = field.getCard(ctl.engine.localPlayer(it.controler),
                    it.location & 0x7f, it.sequence);
            if (card == null) continue;
            card.is_selectable = true;
            // count2（i≥selectableCount）预选中 → 实线框（gframe L2043）
            card.is_selected = i >= selectableCount;
            card.select_seq = it.selectSeq;
            field.selectableCards.add(card);
        }
        if (ctl.viewController != null) ctl.viewController.invalidate();
        ctl.showHint("点击高亮的卡片进行选择", 3000);
        CardDetailPanel panel = ctl.activity.getCardDetailPanel();
        if (panel != null) {
            // gframe L2067-2077：finishable → 「完成」，否则 cancelable → 「取消」，皆无 → 隐藏
            panel.updateCancelOrFinishButton(finishable, cancelable, false);
        }
    }

    void handleCardSelection(int player, int location, int sequence) {
        if (ctl.engine == null) return;
        GameField field = ctl.engine.getField();
        GameField.ClientCard card = field.getCard(player, location, sequence);
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
            ctl.showHint("该卡片不可选择", 2000);
            return;
        }
        if (card.is_selected) {
            card.is_selected = false;
            field.selectedCards.remove(card);
            cardSelectClickOrder.remove(Integer.valueOf(card.select_seq));
        } else {
            if (cardSelectMax > 0 && cardSelectClickOrder.size() >= cardSelectMax) {
                ctl.showHint("已达到最大可选数量", 2000);
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

    private boolean confirmCardSelect() {
        if (!isCardSelecting || ctl.engine == null) return false;
        if (cardSelectClickOrder.size() < cardSelectMin) {
            ctl.showHint("至少需要选择 " + cardSelectMin + " 张卡片", 2000);
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
        return true;
    }

    private boolean cancelCardSelect() {
        if (!isCardSelecting || isSumSelecting) return false;
        if (cardSelectCancelable && cardSelectClickOrder.isEmpty()) {
            ctl.activity.sendResponseInt(-1);
            endCardSelect();
            return true;
        }
        return false;
    }

    void endCardSelect() {
        isCardSelecting = false;
        isSumSelecting = false;
        isUnselectSelecting = false;
        cardSelectClickOrder.clear();
        if (ctl.engine != null) {
            GameField field = ctl.engine.getField();
            field.clearSelect();
            field.selectableCards.clear();
            field.selectedCards.clear();
            field.selectsumAll.clear();
            field.selectsumCards.clear();
        }
        if (ctl.viewController != null) ctl.viewController.invalidate();
        CardDetailPanel panel = ctl.activity.getCardDetailPanel();
        if (panel != null) panel.hideCancelOrFinishButton();
    }

    // === 场上/手牌合计选择（MSG_SELECT_SUM，召唤手续常用：同调素材/仪式 cost 等）===

    /**
     * 进入场上合计选择模式（对齐 duelclient.cpp MSG_SELECT_SUM L2370-2413 + ShowSelectSum(false)）：
     * must 卡预置 selected_cards（select_seq=0，不可点取消），可选卡进 selectsum_all（select_seq=消息索引）；
     * 首次 checkSelectSum 后若无剩余可选分歧即自动应答，否则场上蚂蚁线/实线框直接点击选择
     */
    void beginSumSelect(List<CardSelectDialog.CardItem> mustItems, List<CardSelectDialog.CardItem> optItems,
                        int selectMode, int sumVal, int min, int max) {
        if (ctl.engine == null || optItems == null || optItems.isEmpty()) return;
        GameField field = ctl.engine.getField();
        field.clearSelect();
        field.selectableCards.clear();
        field.selectedCards.clear();
        field.selectsumAll.clear();
        field.selectsumCards.clear();
        cardSelectClickOrder.clear();
        isSumSelecting = true;
        isUnselectSelecting = false;
        isCardSelecting = true;
        cardSelectCancelable = false;
        field.mustSelectCount = mustItems == null ? 0 : mustItems.size();
        field.selectMode = selectMode;
        field.selectSumval = sumVal;
        field.selectMin = min;
        field.selectMax = max;
        // 消费 select_hint（duelclient.cpp L2408-2409），提示前缀整个会话固定，cur/target 随点击刷新
        int hint = field.selectHint;
        field.selectHint = 0;
        sumSelectTitle = hint > 0 ? DataManager.get().getDesc(hint, "选择卡片")
                : DataManager.get().getStringManager().getSystemString(560, "选择卡片");
        if (mustItems != null) {
            for (CardSelectDialog.CardItem it : mustItems) {
                GameField.ClientCard card = field.getCard(ctl.engine.localPlayer(it.controler), it.location & 0x7f, it.sequence);
                if (card == null) continue;
                card.opParam = it.opParam;
                card.select_seq = 0;
                field.selectedCards.add(card);
            }
        }
        for (CardSelectDialog.CardItem it : optItems) {
            GameField.ClientCard card = field.getCard(ctl.engine.localPlayer(it.controler), it.location & 0x7f, it.sequence);
            if (card == null) continue;
            card.opParam = it.opParam;
            card.select_seq = it.selectSeq;
            field.selectsumAll.add(card);
        }
        showSelectSumSession();
    }

    /** 镜像 ClientField::ShowSelectSum(panelmode=false)：重校验 → 满足且无分歧时自动应答，否则刷新提示/按钮/高亮 */
    private void showSelectSumSession() {
        GameField field = ctl.engine.getField();
        boolean ready = checkSelectSum();
        // C++ L1080-1088：已凑成且无其它可选分歧 → SetResponseSelectedCards + SendResponse
        if (ready && (field.selectsumCards.isEmpty() || field.selectableCards.isEmpty())) {
            sendSumSelectResponse();
            return;
        }
        field.selectReady = ready;
        // 提示对齐 client_field.cpp L1092-1107：%ls(%ls/%ls)，cur 区间同值省写，mode1 目标带 +
        String cur = field.selectCurvalL == field.selectCurvalH
                ? String.valueOf(field.selectCurvalL)
                : field.selectCurvalL + "-" + field.selectCurvalH;
        String target = field.selectMode == 0
                ? String.valueOf(field.selectSumval)
                : field.selectSumval + "+";
        ctl.showDuelHint(sumSelectTitle + "(" + cur + "/" + target + ")");
        CardDetailPanel panel = ctl.activity.getCardDetailPanel();
        if (panel != null) {
            // SUM 无 cancelable：就绪时显「完成选择」，未就绪隐藏（ShowCancelOrFinishButton(2/0)）
            panel.updateCancelOrFinishButton(ready, false, !cardSelectClickOrder.isEmpty());
        }
        if (ctl.viewController != null) ctl.viewController.invalidate();
    }

    /** SUM 模式场上点击（event_handler.cpp L1557-1566）：切换选中后重新进入 ShowSelectSum 校验循环 */
    private void handleSumSelectionClick(GameField.ClientCard card) {
        if (card == null || !card.is_selectable) {
            // must 卡（is_selectable=false）与不在当前可行解集合内的卡不可点（C++ 静默忽略）
            ctl.showHint("该卡片不可选择", 2000);
            return;
        }
        GameField field = ctl.engine.getField();
        if (card.is_selected) {
            card.is_selected = false;
            field.selectedCards.remove(card);
            cardSelectClickOrder.remove(Integer.valueOf(card.select_seq));
        } else {
            card.is_selected = true;
            field.selectedCards.add(card);
            cardSelectClickOrder.add(card.select_seq);
        }
        showSelectSumSession();
    }

    /** SUM 应答编码（event_handler.cpp L2974-2992 SetResponseSelectedCards，must 卡 select_seq=0 占位） */
    private void sendSumSelectResponse() {
        if (ctl.engine == null) return;
        GameField field = ctl.engine.getField();
        int must = field.mustSelectCount;
        ByteBuffer buf = ByteBuffer.allocate(1 + must + cardSelectClickOrder.size());
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.put((byte) (must + cardSelectClickOrder.size()));
        for (int i = 0; i < must; i++) {
            buf.put((byte) 0);
        }
        for (int seq : cardSelectClickOrder) {
            buf.put((byte) seq);
        }
        ctl.engine.sendResponse(buf.array());
        endCardSelect();
    }

    /** ClientField::CheckSelectSum 忠实移植（client_field.cpp L1124-1225）：
     *  重算已选累计区间、动态过滤当前可点的卡（点击后仍在某个合法完整解内），返回是否已凑成 */
    private boolean checkSelectSum() {
        GameField f = ctl.engine.getField();
        for (GameField.ClientCard sc : f.selectsumAll) {
            sc.is_selectable = false;
            sc.is_selected = false;
        }
        List<GameField.ClientCard> selable = new ArrayList<>(f.selectsumAll);
        f.selectCurvalL = 0;
        f.selectCurvalH = 0;
        for (int i = 0; i < f.selectedCards.size(); i++) {
            GameField.ClientCard c = f.selectedCards.get(i);
            c.is_selectable = i >= f.mustSelectCount;
            c.is_selected = true;
            selable.remove(c);
            // C++ L1141-1142 此处为裸解码（不走 get_sum_params），仅用于 cur 提示显示
            int op1 = c.opParam & 0xffff;
            int op2 = c.opParam >>> 16;
            int opmin = (op2 > 0 && op1 > op2) ? op2 : op1;
            int opmax = Math.max(op1, op2);
            f.selectCurvalL += opmin;
            f.selectCurvalH += opmax;
        }
        f.selectsumCards.clear();
        boolean ret;
        if (f.selectMode == 0) {
            ret = checkSelSumS(selable, 0, f.selectSumval);
        } else {
            ret = checkSelSumGreater(selable);
        }
        f.selectableCards.clear();
        for (GameField.ClientCard sc : f.selectsumCards) {
            sc.is_selectable = true;
            f.selectableCards.add(sc);
        }
        for (GameField.ClientCard sc : f.selectedCards) {
            f.selectableCards.add(sc);
        }
        return ret;
    }

    /** ClientField::get_sum_params（L1247-1254）：高 16 位含 0x8000 标志时为单一值（op2=0） */
    private static int[] sumParams(int opParam) {
        int op1 = opParam & 0xffff;
        int op2 = (opParam >>> 16) & 0xffff;
        if ((op2 & 0x8000) != 0) {
            op1 = opParam & 0x7fffffff;
            op2 = 0;
        }
        return new int[]{op1, op2};
    }

    /** check_sel_sum_s（L1267-1285）：对已选卡（含 must）枚举 op1/op2 取法逼近目标值 */
    private boolean checkSelSumS(List<GameField.ClientCard> left, int index, int acc) {
        if (acc < 0) return false;
        GameField f = ctl.engine.getField();
        if (index == f.selectedCards.size()) {
            if (acc == 0) {
                int count = f.selectedCards.size() - f.mustSelectCount;
                return count >= f.selectMin && count <= f.selectMax;
            }
            checkSelSumT(left, acc);
            return false;
        }
        int[] p = sumParams(f.selectedCards.get(index).opParam);
        boolean res1 = checkSelSumS(left, index + 1, acc - p[0]);
        boolean res2 = p[1] > 0 && checkSelSumS(left, index + 1, acc - p[1]);
        return res1 || res2;
    }

    /** check_sel_sum_t（L1286-1300）：未凑成时找出「加入后存在完整解」的候选卡 */
    private void checkSelSumT(List<GameField.ClientCard> left, int acc) {
        GameField f = ctl.engine.getField();
        int count = f.selectedCards.size() + 1 - f.mustSelectCount;
        for (GameField.ClientCard sit : new ArrayList<>(left)) {
            if (f.selectsumCards.contains(sit)) continue;
            List<GameField.ClientCard> test = new ArrayList<>(left);
            test.remove(sit);
            int[] p = sumParams(sit.opParam);
            if (checkSum(test, 0, acc - p[0], count)
                    || (p[1] > 0 && checkSum(test, 0, acc - p[1], count))) {
                f.selectsumCards.add(sit);
            }
        }
    }

    /** check_sum（L1301-1314）：在剩余卡中是否存在子集凑出 acc（张数落在 [min,max]） */
    private boolean checkSum(List<GameField.ClientCard> list, int index, int acc, int count) {
        GameField f = ctl.engine.getField();
        if (acc == 0) return count >= f.selectMin && count <= f.selectMax;
        if (acc < 0 || index == list.size()) return false;
        int[] p = sumParams(list.get(index).opParam);
        if ((p[0] == acc || (p[1] > 0 && p[1] == acc))
                && count + 1 >= f.selectMin && count + 1 <= f.selectMax)
            return true;
        index++;
        return (acc > p[0] && checkSum(list, index, acc - p[0], count + 1))
                || (p[1] > 0 && acc > p[1] && checkSum(list, index, acc - p[1], count + 1))
                || checkSum(list, index, acc, count);
    }

    /** CheckSelectSum mode1（sum≥，L1160-1224）：已选最小累计达标即就绪；
     *  候选卡按 op1/op2 分别判定「加入后可能恰好越线」 */
    private boolean checkSelSumGreater(List<GameField.ClientCard> selable) {
        GameField f = ctl.engine.getField();
        int mm = -1, mx = -1, max = 0, sumc = 0;
        for (GameField.ClientCard sc : f.selectedCards) {
            int[] p = sumParams(sc.opParam);
            int opmin = (p[1] > 0 && p[0] > p[1]) ? p[1] : p[0];
            int opmax = Math.max(p[0], p[1]);
            if (mm == -1 || opmin < mm) mm = opmin;
            if (mx == -1 || opmax < mx) mx = opmax;
            sumc += opmin;
            max += opmax;
        }
        if (f.selectSumval <= sumc) return true;
        boolean ret = f.selectSumval <= max && f.selectSumval > max - mx;
        for (GameField.ClientCard sc : selable) {
            int[] p = sumParams(sc.opParam);
            if (sumGreaterCandidate(selable, sc, p[0], sumc, mm)) {
                f.selectsumCards.add(sc);
            } else if (p[1] != 0 && sumGreaterCandidate(selable, sc, p[1], sumc, mm)) {
                f.selectsumCards.add(sc);
            }
        }
        return ret;
    }

    /** mode1 单候选判定（L1182-1213）：按值 m 加入后，要么直接越线且回撤不破线，要么余量可由剩余卡补齐 */
    private boolean sumGreaterCandidate(List<GameField.ClientCard> selable, GameField.ClientCard sc,
                                        int m, int sumc, int mm) {
        GameField f = ctl.engine.getField();
        int sums = sumc + m;
        int ms = (mm == -1 || m < mm) ? m : mm;
        if (sums >= f.selectSumval) {
            return sums - ms < f.selectSumval;
        }
        List<GameField.ClientCard> left = new ArrayList<>(selable);
        left.remove(sc);
        return checkMin(left, 0, f.selectSumval - sums, f.selectSumval - sums + ms - 1);
    }

    /** check_min（L1255-1266）：left 中存在某卡（单张或组合）的最小值落入 [min,max] */
    private boolean checkMin(List<GameField.ClientCard> left, int index, int min, int max) {
        if (index == left.size()) return false;
        int[] p = sumParams(left.get(index).opParam);
        int m = (p[1] > 0 && p[0] > p[1]) ? p[1] : p[0];
        if (m >= min && m <= max) return true;
        index++;
        return (min > m && checkMin(left, index, min - m, max - m))
                || checkMin(left, index, min, max);
    }
}
