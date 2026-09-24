package cn.garymb.ygomobile.game;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.ui.dialogs.CardDisplayDialog;
import cn.garymb.ygomobile.ui.dialogs.CardSelectDialog;
import cn.garymb.ygomobile.ui.dialogs.DuelLogDialog;

/**
 * ShowDialogUtil 的卡片选择协作类（game 包，与门面同包，经包级私有直连 activity / imageLoader /
 * engine / panel / fieldCtl / sendResponseInt / selectTitleText）：忠实移植 duelclient.cpp
 * MSG_SELECT_CARD / MSG_SELECT_TRIBUTE / MSG_SELECT_SUM / MSG_SELECT_UNSELECT_CARD / MSG_SORT_CARD
 * 的解析、场上 / 手牌分流（对齐 duelclient.cpp L1934-1961 / L2404-2405 的可选候选判定）、
 * 有效码解析（对齐 client_field.cpp ShowSelectCard L465-475）、以及响应编码（playerop.cpp）。
 * 分流命中场上模式时转 {@link GameFieldController} 直接点选，否则用现有 {@link CardSelectDialog}
 * 链式 builder 弹窗。{@link ShowDialogUtil} 保留同名 public 方法一行转发至此，对外调用点零改动。
 */
class CardSelectController {

    private final ShowDialogUtil util;

    CardSelectController(ShowDialogUtil util) {
        this.util = util;
    }

    /** 弹窗弹出期间被标记 is_selectable 的场上卡（蚂蚁线高亮同步来源），dismiss 时精确还原 */
    private final List<GameField.ClientCard> dialogMarkedCards = new ArrayList<>();

    /**
     * 选择类 CardSelectDialog 弹出时同步高亮场上/手卡候选：弹窗是本端的选择 UI，
     * 但候选卡在对局画面上也应显示黄色蚂蚁线（SelectionOutlineRenderer 按
     * selectableCards + is_selectable 绘制）。仅标记本次新增，dialogMarkedCards
     * 记录供 dismiss 精确还原，不连锁/场上直接选择的既有状态
     */
    private void markFieldCardsForDialog(List<CardSelectDialog.CardItem> items) {
        GameEngine eng = util.engine();
        GameField f = (eng != null) ? eng.getField() : null;
        if (f == null || items == null) return;
        for (CardSelectDialog.CardItem it : items) {
            GameField.ClientCard card = f.getCard(eng.localPlayer(it.controler),
                    it.location, it.sequence, it.subSeq);
            if (card == null || card.is_selectable) continue;
            card.is_selectable = true;
            card.is_selected = false;
            f.selectableCards.add(card);
            dialogMarkedCards.add(card);
        }
    }

    /** 对话框关闭：仅撤销 markFieldCardsForDialog 打的标记并从渲染源移除 */
    private void clearFieldCardsForDialog() {
        if (dialogMarkedCards.isEmpty()) return;
        GameEngine eng = util.engine();
        GameField f = (eng != null) ? eng.getField() : null;
        for (GameField.ClientCard c : dialogMarkedCards) {
            if (c != null) {
                c.is_selectable = false;
                if (f != null) f.selectableCards.remove(c);
            }
        }
        dialogMarkedCards.clear();
    }

    /**
     * 场上分流诊断日志——候选卡仍在手牌/墓地等以外的区域、或字段解析失败导致回退弹窗时，
     * 每个 return false 分支记录原因与卡片详情（logcat 过滤 "FieldSelect" 可定位失败守卫）
     */
    private static void fsLog(String msg) {
        android.util.Log.i("FieldSelect", msg);
    }

    /**
     * 场上/手牌直接选择模式（对齐 gframe drawing.cpp DrawCard L638-643 的卡片选择轮廓）：
     * 当候选卡全部位于手牌(0x02)/怪兽区(0x04)/魔陷区(0x08)且非超量素材时，不弹 CardSelectDialog，
     * 改由 GameFieldController 在场上高亮虚线框供直接点击选择
     *
     * @return true=已进入场上选择模式，调用方无需再弹窗
     */
    private boolean tryFieldCardSelect(List<CardSelectDialog.CardItem> items,
                                       int min, int max, boolean cancelable) {
        return tryFieldCardSelect(items, min, max, cancelable, false);
    }

    /**
     * @param forceFieldMode 回合结束强制弃牌（HINT_SELECTMSG 501，ocgcore processor.cpp L1257-1288）：
     *                       豁免手牌拥挤回退，候选全在手牌也走场上蚂蚁线直选
     */
    private boolean tryFieldCardSelect(List<CardSelectDialog.CardItem> items,
                                       int min, int max, boolean cancelable, boolean forceFieldMode) {
        GameFieldController ctl = util.fieldCtl();
        GameEngine eng = util.engine();
        GameField f = (eng != null) ? eng.getField() : null;
        if (ctl == null || eng == null || f == null || items == null || items.isEmpty()) {
            fsLog("tryFieldCardSelect SKIP: ctl=" + ctl + " eng=" + eng + " f=" + f
                    + " items=" + (items == null ? "null" : items.size()));
            return false;
        }
        if (!fieldCandidatesSelectable("tryFieldCardSelect", eng, f, items, forceFieldMode)) return false;
        fsLog("tryFieldCardSelect FIELD MODE: count=" + items.size() + " min=" + min + " max=" + max);
        ctl.beginCardSelect(items, min, max, cancelable);
        // 完成/取消按钮沿用 CardDetailPanel（currentSelectType 已由 YGOProActivity 设为 15/20）
        util.panel().updateCancelOrFinishButton(min == 0, cancelable, false);
        return true;
    }

    /**
     * 场上/手牌直接选择候选合法性检查（MSG_SELECT_CARD 与 MSG_SELECT_UNSELECT_CARD 共用，
     * 对齐 duelclient.cpp L1945-1961 / L2009-2051 的 panelmode 判定）：任一候选为超量素材、
     * 场地外（卡组/墓地/除外/额外，即 (loc&0xf1)!=0 等价判定 loc&0xe==0）、场上解析不到，
     * 或手牌拥挤（该方手牌≥10 张且手牌内候选超过 1 张）→ false 回退弹窗；
     * forceFieldMode=true（回合结束弃牌 501）时拥挤回退豁免，强制场选
     */
    private boolean fieldCandidatesSelectable(String tag, GameEngine eng, GameField f,
                                              List<CardSelectDialog.CardItem> items) {
        return fieldCandidatesSelectable(tag, eng, f, items, false);
    }

    private boolean fieldCandidatesSelectable(String tag, GameEngine eng, GameField f,
                                              List<CardSelectDialog.CardItem> items,
                                              boolean forceFieldMode) {
        // duelclient.cpp L1934：手牌数以消息解析前的实时张数为基准
        int[] handCount = { f.getCardCount(0, 0x02), f.getCardCount(1, 0x02) };
        int[] selectInHand = new int[2];
        for (CardSelectDialog.CardItem it : items) {
            int loc = it.location;
            if ((loc & 0x80) != 0) {
                fsLog(tag + " SKIP: overlay material code=" + it.code
                        + " ctrl=" + it.controler + " loc=0x" + Integer.toHexString(loc) + " seq=" + it.sequence);
                return false;
            }
            if ((loc & 0xe) == 0) {
                fsLog(tag + " SKIP: out-of-field code=" + it.code
                        + " ctrl=" + it.controler + " loc=0x" + Integer.toHexString(loc) + " seq=" + it.sequence);
                return false;
            }
            int lp = eng.localPlayer(it.controler);
            if (f.getCard(lp, loc & 0x7f, it.sequence) == null) {
                fsLog(tag + " SKIP: getCard null code=" + it.code
                        + " ctrl=" + it.controler + "->" + lp + " loc=0x" + Integer.toHexString(loc & 0x7f)
                        + " seq=" + it.sequence);
                return false;
            }
            // duelclient.cpp L1957-1961：该方手牌≥ 10 张且候选在手牌内超过 1 张 → 拥挤，回退弹窗；
            // 回合结束强制弃牌(501)按用户要求豁免：候选即全部手牌，必须走场上直选不弹窗
            if (!forceFieldMode && (loc & 0x02) != 0 && handCount[lp] >= 10 && ++selectInHand[lp] > 1) {
                fsLog(tag + " SKIP: hand crowded player=" + lp + " handCount=" + handCount[lp]);
                return false;
            }
        }
        return true;
    }

    /**
     * MSG_SELECT_UNSELECT_CARD 场上分流（连接召唤手续逐步选素材的所在消息）：
     * gframe L1985-2079 对其套用与 MSG_SELECT_CARD 相同的 panelmode 判定，
     * 候选（count1 可选 + count2 已确认素材）全在场/手时不弹窗——场上可选卡画行进虚线、
     * 预选素材画实线框，点击任意可选卡立即应答
     */
    private boolean tryFieldUnselectCardSelect(List<CardSelectDialog.CardItem> items, int selectableCount,
                                               int min, int max, boolean finishable, boolean cancelable) {
        GameFieldController ctl = util.fieldCtl();
        GameEngine eng = util.engine();
        GameField f = (eng != null) ? eng.getField() : null;
        if (ctl == null || eng == null || f == null || items == null || items.isEmpty()) {
            fsLog("tryFieldUnselect SKIP: ctl=" + ctl + " eng=" + eng + " f=" + f
                    + " items=" + (items == null ? "null" : items.size()));
            return false;
        }
        if (!fieldCandidatesSelectable("tryFieldUnselect", eng, f, items, false)) return false;
        fsLog("tryFieldUnselect FIELD MODE: count=" + items.size() + " selectable=" + selectableCount
                + " min=" + min + " max=" + max);
        ctl.beginUnselectCardSelect(items, selectableCount, min, max, finishable, cancelable);
        return true;
    }

    /**
     * 合计选择（MSG_SELECT_SUM，召唤手续常用：同调/仪式等按等级合计解放）场上分流：
     * 对齐 duelclient.cpp L2404-2405 可选候选 `(l & 0xe) == 0 → 弹窗`（手牌参与不弹窗），
     * 叠加保守约束：must 候选与超量素材也需场上可解析才走场上模式
     *
     * @return true=已进入场上合计选择模式，调用方无需再弹窗
     */
    private boolean tryFieldSumSelect(List<CardSelectDialog.CardItem> mustCards,
                                      List<CardSelectDialog.CardItem> items,
                                      int selectMode, int sumVal, int min, int max) {
        GameFieldController ctl = util.fieldCtl();
        GameEngine eng = util.engine();
        GameField f = (eng != null) ? eng.getField() : null;
        if (ctl == null || eng == null || f == null || items == null || items.isEmpty()) {
            fsLog("tryFieldSumSelect SKIP: ctl=" + ctl + " eng=" + eng + " f=" + f
                    + " items=" + (items == null ? "null" : items.size()));
            return false;
        }
        for (CardSelectDialog.CardItem it : items) {
            int loc = it.location;
            if ((loc & 0x80) != 0) {
                fsLog("tryFieldSumSelect SKIP: overlay material code=" + it.code
                        + " ctrl=" + it.controler + " loc=0x" + Integer.toHexString(loc) + " seq=" + it.sequence);
                return false;   // 超量素材场上无法直接点击 → 弹窗
            }
            if ((loc & 0xe) == 0) {
                fsLog("tryFieldSumSelect SKIP: out-of-field code=" + it.code
                        + " ctrl=" + it.controler + " loc=0x" + Integer.toHexString(loc) + " seq=" + it.sequence);
                return false;   // 卡组/墓地/额外/除外等 → 弹窗
            }
            if (f.getCard(eng.localPlayer(it.controler), loc & 0x7f, it.sequence) == null) {
                fsLog("tryFieldSumSelect SKIP: getCard null code=" + it.code
                        + " ctrl=" + it.controler + " loc=0x" + Integer.toHexString(loc & 0x7f) + " seq=" + it.sequence);
                return false;
            }
        }
        if (mustCards != null) {
            for (CardSelectDialog.CardItem it : mustCards) {
                int loc = it.location;
                if ((loc & 0xe) == 0) {
                    fsLog("tryFieldSumSelect SKIP: must out-of-field code=" + it.code
                            + " ctrl=" + it.controler + " loc=0x" + Integer.toHexString(loc) + " seq=" + it.sequence);
                    return false;
                }
                if (f.getCard(eng.localPlayer(it.controler), loc & 0x7f, it.sequence) == null) {
                    fsLog("tryFieldSumSelect SKIP: must getCard null code=" + it.code
                            + " ctrl=" + it.controler + " loc=0x" + Integer.toHexString(loc & 0x7f) + " seq=" + it.sequence);
                    return false;
                }
            }
        }
        fsLog("tryFieldSumSelect FIELD MODE: must=" + (mustCards == null ? 0 : mustCards.size())
                + " opt=" + items.size() + " mode=" + selectMode + " sum=" + sumVal + " min=" + min + " max=" + max);
        ctl.beginSumSelect(mustCards, items, selectMode, sumVal, min, max);
        return true;
    }

    /**
     * 解析用于显示的卡片有效代码（对齐 client_field.cpp ShowSelectCard L465-475：以 pcard->code 决定卡图/卡背）：
     * 通讯带真实码时直接使用（对齐 duelclient.cpp `if(code != 0) pcard->SetCode(code)`）；
     * 通讯码掩码后为 0（联机核心对"对方已知 id 的卡"隐藏信息）时，回退场上 ClientCard 的已知码，
     * 从而显示卡图而非卡背；场上也无码（真正未知的盖卡）时返回 0，仍按卡背显示。
     */
    private int resolveCardCode(int msgCode, int ctrl, int loc, int seq, int subSeq) {
        if ((msgCode & 0x7fffffff) != 0) return msgCode;
        GameEngine eng = util.engine();
        GameField f = (eng != null) ? eng.getField() : null;
        if (f != null) {
            GameField.ClientCard pcard = f.getCard(eng.localPlayer(ctrl), loc, seq, subSeq);
            if (pcard != null) return pcard.code;
        }
        return msgCode;
    }

    public void showCardSelectDialog(ByteBuffer data) {
        // duelclient.cpp L1923-1984：player(1) cancelable(1) min(1) max(1) count(1) + n×[code4 ctrl1 loc1 seq1 subseq1]
        if (data == null || data.remaining() < 5) {
            util.sendResponseInt(0);
            return;
        }
        int player = data.get() & 0xFF;
        int cancelable = data.get() & 0xFF;
        int min = data.get() & 0xFF;
        int max = data.get() & 0xFF;
        int count = data.get() & 0xFF;
        List<CardSelectDialog.CardItem> items = new ArrayList<>();
        for (int i = 0; i < count && data.remaining() >= 8; i++) {
            int rawCode = data.getInt();
            int ctrl = data.get() & 0xFF;
            int loc = data.get() & 0xFF;
            int seq = data.get() & 0xFF;
            int subSeq = data.get() & 0xFF;
            int code = resolveCardCode(rawCode, ctrl, loc, seq, subSeq);
            items.add(new CardSelectDialog.CardItem(code, ctrl, loc, seq, subSeq, i));
        }
        if (items.isEmpty()) {
            util.sendResponseInt(0);
            return;
        }
        // 回合结束弃牌判据：MSG_HINT(HINT_SELECTMSG, tp, 501) 紧随 MSG_SELECT_CARD
        //（ocgcore processor.cpp L1257-1288：hd>6 → hint 501 → SELECT_CARD min=max=hd-6）。
        // 此时无论手牌多拥挤都不弹 CardSelectDialog：血条下已显示 getSystemString(501) 提示
        //（onSelectCard → selectRangeHint → postDuelHint），候选手牌画蚂蚁线直选，达数自动丢弃。
        GameField selField = (util.engine() != null) ? util.engine().getField() : null;
        final boolean discardByRule = selField != null && selField.selectHint == 501;
        if (tryFieldCardSelect(items, min, max, cancelable != 0, discardByRule)) {
            // 场选模式不经过 selectTitleText，此处消费并清零 selectHint
            if (selField != null) selField.selectHint = 0;
            return;
        }
        if (discardByRule) {
            // 501 硬要求不走弹窗：异常候选仅告警，等待服务端重试/重新下发；
            // 本会话不再进入 selectTitleText，此处消费并清零 selectHint
            if (selField != null) selField.selectHint = 0;
            fsLog("discard(501) field-select unavailable, suppress dialog: count=" + items.size()
                    + " min=" + min + " max=" + max);
            return;
        }
        markFieldCardsForDialog(items);
        final List<CardSelectDialog.CardItem> cardInfos = items;
        CardSelectDialog dialog = new CardSelectDialog(util.activity, util.imageLoader);
        util.panel().setCardSelectDialog(dialog);
        dialog.setMode(CardSelectDialog.MODE_SELECT)
                .setTitle(util.selectTitleText(560, "选择卡片") + "(" + min + "-" + max + ")")
                .setCards(items)
                .setSelectRange(min, max)
                .setCancelable(cancelable != 0)
                // 我方协议索引：localPlayer 为对合映射，localPlayer(0) = 我方对应的协议玩家（先攻=0/后攻=1）
                .setLocalPlayer(util.engine().localPlayer(0))
                .setListener(new CardSelectDialog.OnCardSelectListener() {
                    @Override
                    public void onCardClicked(int index) {
                        util.panel().updateCancelOrFinishButton(dialog.isReady(),
                                dialog.isCancelable(), dialog.getSelectedCount() > 0);
                    }

                    @Override
                    public void onCardsSelected(List<Integer> selectedIndices) {
                        sendCardSelectResponse(cardInfos, selectedIndices);
                    }

                    @Override
                    public void onCancel() {
                        util.sendResponseInt(-1);
                    }
                })
                .setOnDismissListener(() -> {
                    clearFieldCardsForDialog();
                    util.panel().hideCancelOrFinishButton();
                    util.panel().setCardSelectDialog(null);
                })
                .show();
        // 关闭统一由 cardDetailPanel 的 cancel/finish 按钮承担（对齐 gframe ShowCancelOrFinishButton）
        util.panel().updateCancelOrFinishButton(min == 0, cancelable != 0, false);
    }

    public void showTributeSelectDialog(ByteBuffer data) {
        // duelclient.cpp L2300-2342：player(1) cancelable(1) min(1) max(1) count(1) + n×[code4 ctrl1 loc1 seq1 t1]
        if (data == null || data.remaining() < 5) {
            util.sendResponseInt(0);
            return;
        }
        int player = data.get() & 0xFF;
        int cancelable = data.get() & 0xFF;
        int min = data.get() & 0xFF;
        int max = data.get() & 0xFF;
        int count = data.get() & 0xFF;
        List<CardSelectDialog.CardItem> items = new ArrayList<>();
        for (int i = 0; i < count && data.remaining() >= 8; i++) {
            int rawCode = data.getInt();
            int ctrl = data.get() & 0xFF;
            int loc = data.get() & 0xFF;
            int seq = data.get() & 0xFF;
            int tributeValue = data.get() & 0xFF;
            int code = resolveCardCode(rawCode, ctrl, loc, seq, 0);
            items.add(new CardSelectDialog.CardItem(code, ctrl, loc, seq, 0, i, tributeValue));
        }
        if (items.isEmpty()) {
            util.sendResponseInt(0);
            return;
        }
        if (tryFieldCardSelect(items, min, max, cancelable != 0)) return;
        markFieldCardsForDialog(items);
        final List<CardSelectDialog.CardItem> cardInfos = items;
        CardSelectDialog dialog = new CardSelectDialog(util.activity, util.imageLoader);
        util.panel().setCardSelectDialog(dialog);
        dialog.setMode(CardSelectDialog.MODE_SELECT)
                .setTitle(util.selectTitleText(531, "解放选择") + "(" + min + "-" + max + ")")
                .setCards(items)
                .setSelectRange(min, max)
                .setCancelable(cancelable != 0)
                .setValueVisible(true)
                .setLocalPlayer(util.engine().localPlayer(0))
                .setListener(new CardSelectDialog.OnCardSelectListener() {
                    @Override
                    public void onCardClicked(int index) {
                        util.panel().updateCancelOrFinishButton(dialog.isReady(),
                                dialog.isCancelable(), dialog.getSelectedCount() > 0);
                    }

                    @Override
                    public void onCardsSelected(List<Integer> selectedIndices) {
                        sendCardSelectResponse(cardInfos, selectedIndices);
                    }

                    @Override
                    public void onCancel() {
                        util.sendResponseInt(-1);
                    }
                })
                .setOnDismissListener(() -> {
                    clearFieldCardsForDialog();
                    util.panel().hideCancelOrFinishButton();
                    util.panel().setCardSelectDialog(null);
                })
                .show();
        util.panel().updateCancelOrFinishButton(min == 0, cancelable != 0, false);
    }

    public void showSumSelectDialog(ByteBuffer data) {
        // duelclient.cpp L2370-2414 + playerop.cpp L661-688：
        // select_mode(1) player(1) sumval(4) min(1) max(1) must_count(1)
        // + must×[code4 ctrl1 loc1 seq1 opParam4]（11字节/条，无subSeq）
        // + count(1) + n×[code4 ctrl1 loc1 seq1 opParam4]
        if (data == null || data.remaining() < 9) {
            util.sendResponseInt(0);
            return;
        }
        int selectMode = data.get() & 0xFF;
        int player = data.get() & 0xFF;
        int sumVal = data.getInt();
        int min = data.get() & 0xFF;
        int max = data.get() & 0xFF;
        int mustCount = data.get() & 0xFF;
        List<CardSelectDialog.CardItem> mustCards = new ArrayList<>();
        for (int i = 0; i < mustCount && data.remaining() >= 11; i++) {
            int rawCode = data.getInt();
            int ctrl = data.get() & 0xFF;
            int loc = data.get() & 0xFF;
            int seq = data.get() & 0xFF;
            int opParam = data.getInt();
            int code = resolveCardCode(rawCode, ctrl, loc, seq, 0);
            mustCards.add(new CardSelectDialog.CardItem(code, ctrl, loc, seq, 0, 0, opParam));
        }
        int count = data.remaining() >= 1 ? (data.get() & 0xFF) : 0;
        List<CardSelectDialog.CardItem> items = new ArrayList<>();
        for (int i = 0; i < count && data.remaining() >= 11; i++) {
            int rawCode = data.getInt();
            int ctrl = data.get() & 0xFF;
            int loc = data.get() & 0xFF;
            int seq = data.get() & 0xFF;
            int opParam = data.getInt();
            int code = resolveCardCode(rawCode, ctrl, loc, seq, 0);
            items.add(new CardSelectDialog.CardItem(code, ctrl, loc, seq, 0, i, opParam));
        }
        if (items.isEmpty()) {
            // 无可选卡：直接发送 must 占位（对齐 C++ ShowSelectSum 自动提交）
            byte[] resp = new byte[1 + mustCount];
            resp[0] = (byte) mustCount;
            util.engine().sendResponse(resp);
            return;
        }
        // 候选全在场/手卡：不弹窗，场上蚂蚁线直接选择（duelclient.cpp L2404-2405 分流真值）
        if (tryFieldSumSelect(mustCards, items, selectMode, sumVal, min, max)) return;
        markFieldCardsForDialog(mustCards);
        markFieldCardsForDialog(items);
        final List<CardSelectDialog.CardItem> cardInfos = items;
        final int fMustCount = mustCount;
        CardSelectDialog dialog = new CardSelectDialog(util.activity, util.imageLoader);
        util.panel().setCardSelectDialog(dialog);
        dialog.setMode(CardSelectDialog.MODE_SUM)
                .setTitle(util.selectTitleText(560, "选择卡片") + "(" + sumVal + ")")
                .setCards(items)
                .setMustCards(mustCards)
                .setSelectRange(min, max)
                .setSumValue(sumVal, selectMode)
                .setValueVisible(true)
                .setLocalPlayer(util.engine().localPlayer(0))
                .setListener(new CardSelectDialog.OnCardSelectListener() {
                    @Override
                    public void onCardClicked(int index) {
                        util.panel().updateCancelOrFinishButton(dialog.isReady(), false,
                                dialog.getSelectedCount() > 0);
                    }

                    @Override
                    public void onCardsSelected(List<Integer> selectedIndices) {
                        sendSumResponse(selectedIndices, fMustCount);
                    }

                    @Override
                    public void onCancel() {
                        util.sendResponseInt(-1);
                    }
                })
                .setOnDismissListener(() -> {
                    clearFieldCardsForDialog();
                    util.panel().hideCancelOrFinishButton();
                    util.panel().setCardSelectDialog(null);
                })
                .show();
        util.panel().updateCancelOrFinishButton(dialog.isReady(), false, false);
    }

    public void showSortCardDialog(ByteBuffer data) {
        // duelclient.cpp L2416-2442：player(1) count(1) + n×[code4 ctrl1 loc1 seq1]（7字节/条，无subSeq）
        if (data == null || data.remaining() < 2) {
            return;
        }
        int player = data.get() & 0xFF;
        int count = data.get() & 0xFF;
        if (count <= 1) {
            byte[] resp = new byte[count];
            for (int i = 0; i < count; i++) resp[i] = (byte) i;
            util.engine().sendResponse(resp);
            return;
        }
        List<CardSelectDialog.CardItem> items = new ArrayList<>();
        for (int i = 0; i < count && data.remaining() >= 7; i++) {
            int rawCode = data.getInt();
            int ctrl = data.get() & 0xFF;
            int loc = data.get() & 0xFF;
            int seq = data.get() & 0xFF;
            int code = resolveCardCode(rawCode, ctrl, loc, seq, 0);
            items.add(new CardSelectDialog.CardItem(code, ctrl, loc, seq, 0, i));
        }
        if (items.size() < count) {
            byte[] resp = new byte[items.size()];
            for (int i = 0; i < resp.length; i++) resp[i] = (byte) i;
            util.engine().sendResponse(resp);
            return;
        }
        CardSelectDialog dialog = new CardSelectDialog(util.activity, util.imageLoader);
        util.panel().setCardSelectDialog(dialog);
        dialog.setMode(CardSelectDialog.MODE_SORT)
                .setTitle(util.selectTitleText(205, "卡片排序"))
                .setCards(items)
                .setListener(new CardSelectDialog.OnCardSelectListener() {
                    @Override
                    public void onSorted(int[] respBuf) {
                        // playerop.cpp L776-788：响应 = 排列（0..n-1 无重复）
                        byte[] resp = new byte[respBuf.length];
                        for (int i = 0; i < respBuf.length; i++) {
                            resp[i] = (byte) respBuf[i];
                        }
                        util.engine().sendResponse(resp);
                    }
                })
                .setOnDismissListener(() -> {
                    util.panel().hideCancelOrFinishButton();
                    util.panel().setCardSelectDialog(null);
                })
                .show();
    }

    public void showUnselectCardDialog(ByteBuffer data) {
        // duelclient.cpp L1986-2080：player(1) finishable(1) cancelable(1) min(1) max(1)
        // count1(1) + count1×[code4 ctrl1 loc1 seq1 subseq1] + count2(1) + count2×[code4 ctrl1 loc1 seq1 subseq1]
        // 点击任意卡立即提交 [1, seq]；finishable 时 OK=发送-1；cancelable 时取消=发送-1
        if (data == null || data.remaining() < 6) {
            util.sendResponseInt(-1);
            return;
        }
        int player = data.get() & 0xFF;
        boolean finishable = (data.get() & 0xFF) != 0;
        boolean cancelable = (data.get() & 0xFF) != 0;
        int min = data.get() & 0xFF;
        int max = data.get() & 0xFF;
        int count1 = data.get() & 0xFF;
        List<CardSelectDialog.CardItem> items = new ArrayList<>();
        int seqIndex = 0;
        for (int i = 0; i < count1 && data.remaining() >= 8; i++) {
            int rawCode = data.getInt();
            int ctrl = data.get() & 0xFF;
            int loc = data.get() & 0xFF;
            int seq = data.get() & 0xFF;
            int subSeq = data.get() & 0xFF;
            int code = resolveCardCode(rawCode, ctrl, loc, seq, subSeq);
            items.add(new CardSelectDialog.CardItem(code, ctrl, loc, seq, subSeq, seqIndex++));
        }
        int count2 = data.remaining() >= 1 ? (data.get() & 0xFF) : 0;
        for (int i = 0; i < count2 && data.remaining() >= 8; i++) {
            int rawCode = data.getInt();
            int ctrl = data.get() & 0xFF;
            int loc = data.get() & 0xFF;
            int seq = data.get() & 0xFF;
            int subSeq = data.get() & 0xFF;
            int code = resolveCardCode(rawCode, ctrl, loc, seq, subSeq);
            items.add(new CardSelectDialog.CardItem(code, ctrl, loc, seq, subSeq, seqIndex++));
        }
        if (items.isEmpty()) {
            util.sendResponseInt(-1);
            return;
        }
        // 候选全在场/手：不弹窗，场上虚线/实线直接点选（duelclient.cpp L1985-2079 同 SELECT_CARD 分流）
        if (tryFieldUnselectCardSelect(items, count1, min, max, finishable, cancelable)) return;
        // 仅可选段（count1）画蚂蚁线；已确认素材段（count2）在弹窗里预选中，场上不重复标记
        markFieldCardsForDialog(items.subList(0, Math.min(count1, items.size())));
        boolean[] preSelected = new boolean[items.size()];
        for (int i = count1; i < items.size(); i++) {
            preSelected[i] = true;
        }
        final List<CardSelectDialog.CardItem> cardInfos = items;
        CardSelectDialog dialog = new CardSelectDialog(util.activity, util.imageLoader);
        util.panel().setCardSelectDialog(dialog);
        dialog.setMode(CardSelectDialog.MODE_UNSELECT)
                .setTitle(util.selectTitleText(560, "选择卡片") + "(" + min + "-" + max + ")")
                .setCards(items)
                .setPreSelected(preSelected)
                .setSelectRange(min, max)
                .setCancelable(cancelable)
                .setFinishable(finishable)
                .setLocalPlayer(util.engine().localPlayer(0))
                .setListener(new CardSelectDialog.OnCardSelectListener() {
                    @Override
                    public void onCardClicked(int index) {
                        // event_handler.cpp L882-896：点击即提交 [1, select_seq]
                        ByteBuffer buf = ByteBuffer.allocate(2);
                        buf.order(ByteOrder.LITTLE_ENDIAN);
                        buf.put((byte) 1);
                        if (index >= 0 && index < cardInfos.size()) {
                            buf.put((byte) cardInfos.get(index).selectSeq);
                        }
                        util.engine().sendResponse(buf.array());
                    }

                    @Override
                    public void onCancel() {
                        util.sendResponseInt(-1);
                    }
                })
                .setOnDismissListener(() -> {
                    clearFieldCardsForDialog();
                    util.panel().hideCancelOrFinishButton();
                    util.panel().setCardSelectDialog(null);
                })
                .show();
        // 关闭统一由 cardDetailPanel 的 cancel/finish 按钮承担（对齐 gframe ShowCancelOrFinishButton）
        if (finishable) {
            util.panel().showCancelOrFinishButton("完成");
        } else if (cancelable) {
            util.panel().showCancelOrFinishButton("取消");
        } else {
            util.panel().hideCancelOrFinishButton();
        }
    }

    private void sendCardSelectResponse(List<CardSelectDialog.CardItem> cardInfos, List<Integer> selectedIndices) {
        // C++ SetResponseSelectedCards：respbuf[0]=len，其后按点击顺序填充 select_seq
        ByteBuffer buf = ByteBuffer.allocate(1 + selectedIndices.size());
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.put((byte) selectedIndices.size());
        for (int idx : selectedIndices) {
            if (idx >= 0 && idx < cardInfos.size()) {
                buf.put((byte) cardInfos.get(idx).selectSeq);
            }
        }
        util.engine().sendResponse(buf.array());
    }

    private void sendSumResponse(List<Integer> selectedIndices, int mustCount) {
        // playerop.cpp L697-712：总数 ∈ [min+mcount, max+mcount]；
        // 前 mcount 个值核心忽略（must 占位），其后为可选卡在可选列表中的 index
        ByteBuffer buf = ByteBuffer.allocate(1 + mustCount + selectedIndices.size());
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.put((byte) (mustCount + selectedIndices.size()));
        for (int i = 0; i < mustCount; i++) {
            buf.put((byte) 0);
        }
        for (int idx : selectedIndices) {
            buf.put((byte) idx);
        }
        util.engine().sendResponse(buf.array());
    }

    public void showConfirmCardsDialog(ByteBuffer data) {
        // duelclient.cpp L2518-2617：GameEngine 转发 skipPanel(1) + n×[code4 ctrl1 loc1 seq1]
        // 纯展示：OK 仅关闭（无响应数据），对齐 C++ BUTTON_CARD_SEL_OK 的 actionSignal.Set()
        if (data == null || data.remaining() < 1) {
            util.panel().hideCancelOrFinishButton();
            return;
        }
        int skipPanel = data.get() & 0xFF;
        List<CardDisplayDialog.CardItem> items = new ArrayList<>();
        while (data.remaining() >= 7) {
            int rawCode = data.getInt();
            int ctrl = data.get() & 0xFF;
            int loc = data.get() & 0xFF;
            int seq = data.get() & 0xFF;
            // duelclient.cpp L2546-2566：仅卡组/额外（l & 0x41）的卡进入面板确认
            if ((loc & 0x41) != 0) {
                int code = resolveCardCode(rawCode, ctrl, loc, seq, 0);
                items.add(new CardDisplayDialog.CardItem(code, ctrl, loc, seq, 0));
            }
        }
        // skip_panel 或面板卡不足 2 张（单张走场上翻卡动画路径）时不弹面板
        if (skipPanel != 0 || items.size() <= 1) {
            util.panel().hideCancelOrFinishButton();
            return;
        }
        CardDisplayDialog dialog = new CardDisplayDialog(util.activity, util.imageLoader);
        util.panel().setCardDisplayDialog(dialog);
        // duelclient.cpp L2611：标题取系统字符串 208（"确认%d张卡"）
        dialog.setTitle(DuelLogDialog.sysFormat(208, "确认%d张卡", items.size()))
                .setCards(items)
                .setLocalPlayer(util.engine().localPlayer(0))
                .setCardClickListener(this::showCardInfoFromItem)
                .setOnDismissListener(() -> {
                    util.panel().hideCancelOrFinishButton();
                    util.panel().setCardDisplayDialog(null);
                })
                .show();
    }

    public void showCardInfoFromItem(CardDisplayDialog.CardItem item) {
        GameField.ClientCard card = new GameField.ClientCard();
        // code 可能带 0x80000000 翻面标志位，掩码后再使用（对齐 duelclient.cpp code & 0x7fffffff）
        card.code = item.code & 0x7fffffff;
        card.controler = (item.controler == util.engine().localPlayer(0)) ? 0 : 1;
        card.location = item.location;
        card.sequence = item.sequence;
        card.position = 0x1;
        util.activity.showCardInfoPanel(card);
    }
}
