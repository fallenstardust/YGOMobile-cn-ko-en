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
        GameFieldController ctl = util.fieldCtl();
        GameEngine eng = util.engine();
        GameField f = (eng != null) ? eng.getField() : null;
        if (ctl == null || eng == null || f == null || items == null || items.isEmpty()) {
            fsLog("tryFieldCardSelect SKIP: ctl=" + ctl + " eng=" + eng + " f=" + f
                    + " items=" + (items == null ? "null" : items.size()));
            return false;
        }
        // duelclient.cpp L1934：手牌数以消息解析前的实时张数为基准
        int[] handCount = { f.getCardCount(0, 0x02), f.getCardCount(1, 0x02) };
        int[] selectInHand = new int[2];
        for (CardSelectDialog.CardItem it : items) {
            int loc = it.location;
            if ((loc & 0x80) != 0) {
                fsLog("tryFieldCardSelect SKIP: overlay material code=" + it.code
                        + " ctrl=" + it.controler + " loc=0x" + Integer.toHexString(loc) + " seq=" + it.sequence);
                return false;
            }
            if ((loc & 0xe) == 0) {
                fsLog("tryFieldCardSelect SKIP: out-of-field code=" + it.code
                        + " ctrl=" + it.controler + " loc=0x" + Integer.toHexString(loc) + " seq=" + it.sequence);
                return false;
            }
            int lp = eng.localPlayer(it.controler);
            if (f.getCard(lp, loc & 0x7f, it.sequence) == null) {
                fsLog("tryFieldCardSelect SKIP: getCard null code=" + it.code
                        + " ctrl=" + it.controler + "->" + lp + " loc=0x" + Integer.toHexString(loc & 0x7f)
                        + " seq=" + it.sequence);
                return false;
            }
            // duelclient.cpp L1957-1961：该方手牌≥ 10 张且候选在手牌内超过 1 张 → 拥挤，回退弹窗
            if ((loc & 0x02) != 0 && handCount[lp] >= 10 && ++selectInHand[lp] > 1) {
                fsLog("tryFieldCardSelect SKIP: hand crowded player=" + lp + " handCount=" + handCount[lp]);
                return false;
            }
        }
        fsLog("tryFieldCardSelect FIELD MODE: count=" + items.size() + " min=" + min + " max=" + max);
        ctl.beginCardSelect(items, min, max, cancelable);
        // 完成/取消按钮沿用 CardDetailPanel（currentSelectType 已由 YGOProActivity 设为 15/20）
        util.panel().updateCancelOrFinishButton(min == 0, cancelable, false);
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
        if (tryFieldCardSelect(items, min, max, cancelable != 0)) return;
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
