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
 * Base layer of the split inheritance chain (FieldSelectManagerBase <-
 * FieldSelectManager): owns the shared select-session state fields, the
 * facade handle {@code ctl}, the common {@code endCardSelect} teardown, and
 * the whole MSG_SELECT_SUM (field/hand sum-select) click-revalidate engine.
 * The place-select and direct card-select session entry points live in the
 * leaf class and call down into these primitives.
 */
abstract class FieldSelectManagerBase {

    protected final GameFieldController ctl;

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
    protected final Random random = new Random();

    /**
     * Constructor body anchor kept verbatim so the final ctl field is
     * assigned exactly once from the leaf forwarding constructor.
     */
    protected FieldSelectManagerBase(GameFieldController ctl) {
        this.ctl = ctl;
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
                ClientCard card = field.getCard(ctl.engine.localPlayer(it.controler), it.location & 0x7f, it.sequence);
                if (card == null) continue;
                card.opParam = it.opParam;
                card.select_seq = 0;
                field.selectedCards.add(card);
            }
        }
        for (CardSelectDialog.CardItem it : optItems) {
            ClientCard card = field.getCard(ctl.engine.localPlayer(it.controler), it.location & 0x7f, it.sequence);
            if (card == null) continue;
            card.opParam = it.opParam;
            card.select_seq = it.selectSeq;
            field.selectsumAll.add(card);
        }
        showSelectSumSession();
    }

    /** 镜像 ClientField::ShowSelectSum(panelmode=false)：重校验 → 满足且无分歧时自动应答，否则刷新提示/按钮/高亮 */
    protected void showSelectSumSession() {
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
    protected void handleSumSelectionClick(ClientCard card) {
        if (card == null || !card.is_selectable) {
            // must 卡（is_selectable=false）与不在当前可行解集合内的卡不可点（C++ 静默忽略）
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
    protected void sendSumSelectResponse() {
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
    protected boolean checkSelectSum() {
        GameField f = ctl.engine.getField();
        for (ClientCard sc : f.selectsumAll) {
            sc.is_selectable = false;
            sc.is_selected = false;
        }
        List<ClientCard> selable = new ArrayList<>(f.selectsumAll);
        f.selectCurvalL = 0;
        f.selectCurvalH = 0;
        for (int i = 0; i < f.selectedCards.size(); i++) {
            ClientCard c = f.selectedCards.get(i);
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
        for (ClientCard sc : f.selectsumCards) {
            sc.is_selectable = true;
            f.selectableCards.add(sc);
        }
        for (ClientCard sc : f.selectedCards) {
            f.selectableCards.add(sc);
        }
        return ret;
    }

    /** ClientField::get_sum_params（L1247-1254）：高 16 位含 0x8000 标志时为单一值（op2=0） */
    protected static int[] sumParams(int opParam) {
        int op1 = opParam & 0xffff;
        int op2 = (opParam >>> 16) & 0xffff;
        if ((op2 & 0x8000) != 0) {
            op1 = opParam & 0x7fffffff;
            op2 = 0;
        }
        return new int[]{op1, op2};
    }

    /** check_sel_sum_s（L1267-1285）：对已选卡（含 must）枚举 op1/op2 取法逼近目标值 */
    protected boolean checkSelSumS(List<ClientCard> left, int index, int acc) {
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
    protected void checkSelSumT(List<ClientCard> left, int acc) {
        GameField f = ctl.engine.getField();
        int count = f.selectedCards.size() + 1 - f.mustSelectCount;
        for (ClientCard sit : new ArrayList<>(left)) {
            if (f.selectsumCards.contains(sit)) continue;
            List<ClientCard> test = new ArrayList<>(left);
            test.remove(sit);
            int[] p = sumParams(sit.opParam);
            if (checkSum(test, 0, acc - p[0], count)
                    || (p[1] > 0 && checkSum(test, 0, acc - p[1], count))) {
                f.selectsumCards.add(sit);
            }
        }
    }

    /** check_sum（L1301-1314）：在剩余卡中是否存在子集凑出 acc（张数落在 [min,max]） */
    protected boolean checkSum(List<ClientCard> list, int index, int acc, int count) {
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
    protected boolean checkSelSumGreater(List<ClientCard> selable) {
        GameField f = ctl.engine.getField();
        int mm = -1, mx = -1, max = 0, sumc = 0;
        for (ClientCard sc : f.selectedCards) {
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
        for (ClientCard sc : selable) {
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
    protected boolean sumGreaterCandidate(List<ClientCard> selable, ClientCard sc,
                                        int m, int sumc, int mm) {
        GameField f = ctl.engine.getField();
        int sums = sumc + m;
        int ms = (mm == -1 || m < mm) ? m : mm;
        if (sums >= f.selectSumval) {
            return sums - ms < f.selectSumval;
        }
        List<ClientCard> left = new ArrayList<>(selable);
        left.remove(sc);
        return checkMin(left, 0, f.selectSumval - sums, f.selectSumval - sums + ms - 1);
    }

    /** check_min（L1255-1266）：left 中存在某卡（单张或组合）的最小值落入 [min,max] */
    protected boolean checkMin(List<ClientCard> left, int index, int min, int max) {
        if (index == left.size()) return false;
        int[] p = sumParams(left.get(index).opParam);
        int m = (p[1] > 0 && p[0] > p[1]) ? p[1] : p[0];
        if (m >= min && m <= max) return true;
        index++;
        return (min > m && checkMin(left, index, min - m, max - m))
                || checkMin(left, index, min, max);
    }
}
