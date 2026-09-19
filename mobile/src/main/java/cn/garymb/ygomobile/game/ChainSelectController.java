package cn.garymb.ygomobile.game;

import android.os.Handler;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.render.CardDetailPanel;
import cn.garymb.ygomobile.ui.dialogs.YesOrNoDialog;
import ocgcore.DataManager;

/**
 * ShowDialogUtil 的连锁询问协作类（game 包，与门面同包，经包级私有直连 activity / engine / panel /
 * fieldCtl / sendResponseInt / sysText）：忠实移植 duelclient.cpp MSG_SELECT_CHAIN（L2080-2181）的
 * 解析、场上可发动卡片高亮、时点按钮自动放弃 / 自动连锁决策、panelmode 与强制连锁分流，以及配套的
 * 场上点击发动模式（enter/exit）、放弃 / 发动应答、取消恢复。
 * 仍通过 {@link YesOrNoDialog} 的连锁静态工厂弹窗，保持 UI 组件与协议状态机的解耦。
 * {@link ShowDialogUtil} 保留同名 public 方法一行转发至此，对外调用点零改动。
 */
class ChainSelectController {

    private final ShowDialogUtil util;

    ChainSelectController(ShowDialogUtil util) {
        this.util = util;
    }

    public void showChainSelectDialog(ByteBuffer data) {
        // duelclient.cpp L2080-2085：selecting_player(1) count(1) specount(1) hint0(4) hint1(4)
        // 注意首字节是 selecting_player，必须先跳过，否则 count/specount 与后续条目全部错位
        if (data == null || data.remaining() < 11) {
            util.sendResponseInt(-1);
            return;
        }
        data.get(); // selecting_player
        int count = data.get() & 0xFF;
        int specount = data.get() & 0xFF;
        int hint0 = data.getInt();
        int hint1 = data.getInt();
        // duelclient.cpp L2091：specount == 0x7f 表示这是诱发类效果询问（select_trigger）
        boolean selectTrigger = (specount == 0x7f);

        GameEngine e = util.engine();
        GameField field = e != null ? e.getField() : null;
        // 清理上一条指令（idle/battle）遗留的命令标记与上次连锁高亮，避免脏状态残留
        if (e != null) e.clearCommandFlags();
        clearChainSelect();

        List<String> chainOptions = new ArrayList<>();
        List<Integer> chainFlags = new ArrayList<>();
        boolean chainForced = false;
        boolean contiExist = false;
        boolean panelmode = false;
        for (int i = 0; i < count && data.remaining() >= 14; i++) {
            int flag = data.get() & 0xFF;
            int forced = data.get() & 0xFF;
            flag |= forced << 8;
            int code = data.getInt();
            int ctrl = data.get() & 0xFF;
            int loc = data.get() & 0xFF;
            int seq = data.get() & 0xFF;
            int subSeq = data.get() & 0xFF;
            int desc = data.getInt();

            // duelclient.cpp L2110-2117：forced → chain_forced；flag & EDESC_OPERATION → conti_exist
            if (forced != 0) chainForced = true;
            boolean conti = (flag & 0x1) != 0;
            if (conti) contiExist = true;
            // duelclient.cpp L2133-2134：LOCATION_OVERLAY 连锁项 → panelmode（overlay 单元无法在场上单独点击）
            if ((loc & 0x80) != 0) panelmode = true;

            // 填充场上卡片状态（对齐 duelclient.cpp L2106-2135）：把可发动卡片高亮为 is_selectable
            //（GameFieldView 渲染黄色脉冲）并挂 COMMAND_ACTIVATE，使玩家点击高亮卡片经
            // onCardClick→CmdMenuDialog 发动；连锁发动响应仅发送连锁项索引（index=i）
            if (field != null && e != null) {
                int localCtrl = e.localPlayer(ctrl & 1);
                GameField.ClientCard card = field.getCard(localCtrl, loc, seq, subSeq);
                if (card != null) {
                    card.is_selected = false;
                    card.is_selectable = true;
                    card.cmdFlag |= GameEngine.COMMAND_ACTIVATE;
                    int pureLoc = loc & 0x7f;
                    if (conti) {
                        card.chain_code = code;
                        field.contiCards.add(card);
                        field.contiAct = true;
                    }
                    if (pureLoc == 0x01) {
                        card.setCode(code);
                        field.deckAct[localCtrl] = true;
                    } else if (pureLoc == 0x10) {
                        field.graveAct[localCtrl] = true;
                    } else if (pureLoc == 0x20) {
                        field.removeAct[localCtrl] = true;
                    } else if (pureLoc == 0x40) {
                        field.extraAct[localCtrl] = true;
                    }
                    field.activatableCards.add(card);
                    e.activatableCards.add(new GameEngine.CmdCardInfo(card, code, desc, flag, i));
                }
            }

            String cardName = util.activity.getCardDisplayName(code);
            // 连锁描述同样可能为卡片脚本提示文字（卡号*16+n），统一走 getDesc
            String descStr = desc > 0 ? DataManager.get().getDesc(desc, "效果") : "效果";
            chainOptions.add(cardName + " - " + descStr);
            chainFlags.add(flag);
        }

        AppsSettings settings = AppsSettings.get();
        CardDetailPanel p = util.panel();
        // 左侧面板时点按钮三态（对齐 gframe mainGame->ignore_chain / always_chain / chain_when_avail）
        boolean ignoreChain = p != null && p.isIgnoreChain();
        boolean alwaysChain = p != null && p.isAlwaysChain();
        boolean chainWhenAvail = p != null && p.isChainWhenAvail();

        // duelclient.cpp L2137-2145：时点按钮决定的"自动放弃连锁"
        // 非诱发类询问 && 非强制连锁 && (忽略时点 || ((无可连锁项 || 无诱发项) && 未开显示时点))
        //                          && (无可连锁项 || 未开可用时点)
        if (!selectTrigger && !chainForced
                && (ignoreChain || ((count == 0 || specount == 0) && !alwaysChain))
                && (count == 0 || !chainWhenAvail)) {
            clearChainSelect();
            if (p != null) p.setSelectType(-1);
            // chkWaitChain：勾选且未开"忽略时点"时随机等待 20-40 帧（约 320-640ms）再应答，避免暴露秒过
            Handler mainHandler = util.mainHandler;
            Random random = util.random;
            if (settings.getIntSettings("chkWaitChain", 0) == 1 && !ignoreChain) {
                mainHandler.postDelayed(() -> util.sendResponseInt(-1), 320 + random.nextInt(321));
            } else {
                util.sendResponseInt(-1);
            }
            return;
        }

        // duelclient.cpp L2146-2157：chkAutoChain && chain_forced && !(always_chain || chain_when_avail)
        // → 自动发动首个必发（forced）连锁项
        if (settings.getIntSettings("chkAutoChain", 0) == 1 && chainForced
                && !(alwaysChain || chainWhenAvail)) {
            int autoIndex = -1;
            for (int i = 0; i < chainFlags.size(); i++) {
                if ((chainFlags.get(i) >> 8) != 0) {
                    autoIndex = i;
                    break;
                }
            }
            clearChainSelect();
            if (p != null) p.setSelectType(-1);
            util.sendResponseInt(autoIndex);
            return;
        }

        // duelclient.cpp L2173-2174：count == 0 且未自动放弃（说明开了"显示时点"）
        // → 弹 sys201 + sys202 询问，而不是直接应答 -1
        if (chainOptions.isEmpty()) {
            YesOrNoDialog.showChainEmptyQuery(util.activity);
            return;
        }

        // panelmode（overlay 连锁项）不进入场上点击模式；强制连锁不可取消
        YesOrNoDialog.setChainForcedMode(chainForced && !panelmode);

        // duelclient.cpp L2164-2170：panelmode → 保留列表对话框（overlay 单元无法在场上单独点击发动）
        if (panelmode) {
            clearChainSelect();
            YesOrNoDialog.showChainListDialog(util.activity, contiExist, selectTrigger, chainForced, chainOptions, chainFlags);
            return;
        }

        // duelclient.cpp L2172-2181：强制连锁不弹询问窗，直接进入场上点击发动模式（不允许取消/放弃）
        if (chainForced) {
            YesOrNoDialog.setChainQueryDialog(null);
            enterChainFieldMode(false);
            return;
        }

        // 非强制连锁：弹出 wQuery 式是/否询问窗（stQMessage 文案），场上可发动卡片已高亮（黄色脉冲）。
        String queryText = selectTrigger
                ? util.sysText(222, "是否要发动诱发类效果？") + "\n" + util.sysText(223, "稍后将询问其他可以发动的效果。")
                : util.sysText(203, "是否要进行连锁？");
        YesOrNoDialog.showChainQuery(util.activity, queryText);
    }

    /**
     * 对齐 C++ ClientField::ClearChainSelect()：清掉场上卡片的可发动高亮与选择标记，
     * 并清空 engine/field 两侧可发动列表（field.clearChainSelect 复位标记但不清 activatableCards 本身）
     */
    public void clearChainSelect() {
        GameEngine e = util.engine();
        if (e == null) return;
        GameField f = e.getField();
        if (f != null) {
            f.clearChainSelect();
            f.activatableCards.clear();
        }
        e.activatableCards.clear();
    }

    /**
     * 进入场上点击发动模式（对齐 event_handler.cpp BUTTON_YES + MSG_SELECT_CHAIN：HideElement(wQuery)
     * 后等待玩家点击场上高亮卡片发动）：设 cmdContext=CHAIN 使 CmdMenuDialog 走连锁响应编码（仅发索引）。
     *
     * @param showCancelButton 非强制连锁显示「取消操作」按钮（sys1295），点击可重新弹出询问窗
     */
    public void enterChainFieldMode(boolean showCancelButton) {
        GameFieldController ctl = util.fieldCtl();
        if (ctl != null) ctl.beginChainCommand();
        CardDetailPanel p = util.panel();
        if (p != null) {
            p.setSelectType(16);
            if (showCancelButton) {
                p.showCancelOrFinishButton(util.sysText(1295, "取消操作"));
            }
        }
    }

    /**
     * 退出场上点击发动模式：复位命令上下文、关闭残留命令菜单与询问窗引用
     */
    private void exitChainFieldMode() {
        YesOrNoDialog.setChainForcedMode(false);
        YesOrNoDialog.setChainQueryDialog(null);
        GameFieldController ctl = util.fieldCtl();
        if (ctl != null) ctl.endChainCommand();
    }

    /**
     * 放弃连锁（应答 -1），对齐 event_handler.cpp BUTTON_NO / CancelOrFinish 的 SetResponseI(-1)：
     * 退出场上模式、清高亮、隐藏「取消操作」按钮并复位选择类型
     */
    public void finishChainPass() {
        exitChainFieldMode();
        clearChainSelect();
        CardDetailPanel p = util.panel();
        if (p != null) {
            p.hideCancelOrFinishButton();
            p.setSelectType(-1);
        }
        util.sendResponseInt(-1);
    }

    /**
     * 发动指定连锁项（由 CmdMenuDialog 连锁分支调用），对齐 event_handler.cpp L833-841
     * MSG_SELECT_CHAIN → SetResponseI(index)：连锁发动响应仅发送连锁项索引（区别于 idle 的 (index<<16)+5）
     */
    public void activateChainOption(int index) {
        exitChainFieldMode();
        clearChainSelect();
        CardDetailPanel p = util.panel();
        if (p != null) {
            p.hideCancelOrFinishButton();
            p.setSelectType(-1);
        }
        util.sendResponseInt(index);
    }

    /**
     * 「取消操作」按钮在连锁（selectType 16）下的处理，对齐 event_handler.cpp L3170-3197
     * CancelOrFinish(MSG_SELECT_CHAIN)：强制连锁不可取消；询问窗可见则应答 -1；
     * 询问窗已隐藏（场上点击模式）则重新弹出询问窗（PopupElement(wQuery)），实现「暂时隐藏」后可恢复。
     *
     * @return true=已按连锁语义消费本次点击；false=无连锁询问窗（panelmode 列表），交由调用方走默认 -1
     */
    public boolean handleChainCancel() {
        if (YesOrNoDialog.isChainForcedMode()) return true;
        YesOrNoDialog q = YesOrNoDialog.getChainQueryDialog();
        if (q == null) return false;
        if (q.isShowing()) {
            finishChainPass();
            return true;
        }
        CardDetailPanel p = util.panel();
        if (p != null) {
            p.hideCancelOrFinishButton();
            p.setCurrentDialog(q);
        }
        GameFieldController ctl = util.fieldCtl();
        if (ctl != null) ctl.endChainCommand();
        q.show();
        return true;
    }

    public void showSortChainDialog(ByteBuffer data) {
        if (data == null || data.remaining() < 1) {
            util.sendResponseInt(0);
            return;
        }
        int count = data.get() & 0xFF;
        if (count <= 1) {
            util.sendResponseInt(0);
            return;
        }
        util.activity.showHintMessage("连锁排序: 自动排序");
        util.sendResponseInt(0);
    }
}
