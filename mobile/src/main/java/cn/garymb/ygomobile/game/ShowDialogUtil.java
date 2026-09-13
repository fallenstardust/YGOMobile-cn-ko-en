package cn.garymb.ygomobile.game;

import android.os.Handler;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.YGOProActivity;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.render.CardDetailPanel;
import cn.garymb.ygomobile.ui.dialogs.AnnounceAttributeDialog;
import cn.garymb.ygomobile.ui.dialogs.AnnounceCardDialog;
import cn.garymb.ygomobile.ui.dialogs.AnnounceNumberDialog;
import cn.garymb.ygomobile.ui.dialogs.AnnounceRaceDialog;
import cn.garymb.ygomobile.ui.dialogs.CardDisplayDialog;
import cn.garymb.ygomobile.ui.dialogs.CardSelectDialog;
import cn.garymb.ygomobile.ui.dialogs.DuelLogDialog;
import cn.garymb.ygomobile.ui.dialogs.FirstOrSecondDialog;
import cn.garymb.ygomobile.ui.dialogs.OptionDialog;
import cn.garymb.ygomobile.ui.dialogs.PosSelectDialog;
import cn.garymb.ygomobile.ui.dialogs.RPSDialog;
import cn.garymb.ygomobile.ui.dialogs.YesOrNoDialog;
import ocgcore.DataManager;

/**
 * 决斗中所有选择/确认对话框的统一管理类，从 YGOProActivity 迁移而来，
 * 由 YGOProActivity 通过 getDialogUtil() 统一调用。
 */
public class ShowDialogUtil {

    private static final String TAG = "ShowDialogUtil";


    private final YGOProActivity activity;
    private final ImageLoader imageLoader;
    private final Handler mainHandler;
    private final Random random = new Random();

    private RPSDialog handSelectDialog;
    private boolean rpsResultShown;
    private int lastHandSent;
    // 猜拳结果动画正在播放：期间到达的 MSG_SELECT_HAND 先挂起，待动画结束回调再决定是否重显弹窗，
    // 避免动画未播完就因通讯结果抢先重新显示 RPSDialog
    private boolean rpsAnimating;
    private boolean pendingHandSelect;
    private FirstOrSecondDialog tpSelectDialog;
    private AnnounceRaceDialog announceRaceDialog;
    private AnnounceAttributeDialog announceAttributeDialog;
    private AnnounceCardDialog announceCardDialog;
    private AnnounceNumberDialog announceNumberDialog;

    public ShowDialogUtil(YGOProActivity activity, ImageLoader imageLoader, Handler mainHandler) {
        this.activity = activity;
        this.imageLoader = imageLoader;
        this.mainHandler = mainHandler;
    }

    // === 依赖桥接 ===

    private GameEngine engine() {
        return activity.getEngine();
    }

    private CardDetailPanel panel() {
        return activity.getCardDetailPanel();
    }

    private GameFieldController fieldCtl() {
        return activity.getFieldCtl();
    }

    private void sendResponseInt(int value) {
        activity.sendResponseInt(value);
    }

    // === 通用辅助 ===

    /**
     * 选择类对话框标题：优先使用 MSG_HINT(HINT_SELECTMSG) 通讯下发的索引调用
     * StringManager.getSystemString，无索引时按 gframe 缺省值兜底
     * （duelclient.cpp：选卡/总合 560、解放 531、排序 205），消费后清零
     */
    private String selectTitleText(int defIndex, String defText) {
        GameField f = engine() != null ? engine().getField() : null;
        int hint = (f != null) ? f.selectHint : 0;
        if (f != null) f.selectHint = 0;
        return DataManager.get().getStringManager()
                .getSystemString(hint > 0 ? hint : defIndex, defText);
    }

    /**
     * 系统字符串（对齐 gframe dataManager.GetSysString(index)）
     */
    private String sysText(int index, String defText) {
        return DataManager.get().getStringManager().getSystemString(index, defText);
    }

    // === 猜拳 / 先后攻 ===

    public void showHandSelectDialog() {
        if (rpsResultShown) {
            // 猜拳已分出胜负（非平局）：不再显示 RPSDialog；
            // 若服务器仍下发 MSG_SELECT_HAND，自动复用上次出的手势应答，避免协议等待卡死
            if (lastHandSent >= RPSDialog.HAND_SCISSORS && lastHandSent <= RPSDialog.HAND_PAPER
                    && engine() != null) {
                engine().sendHandResult(lastHandSent);
            }
            return;
        }
        if (rpsAnimating) {
            // 动画播放中：挂起本次请求，待动画结束回调（见 onHandResult）再重新进入本方法显示，
            // 保证弹窗一定在动画播完之后才出现（平局重显），不被通讯结果抢先
            pendingHandSelect = true;
            return;
        }
        if (handSelectDialog != null) {
            // 已显示、或已创建正在等待布局完成后显示（延迟显示分支）：复用，绝不创建第二个实例，
            // 否则前一个弹窗会被布局回调显示出来却无人持有引用，点击 dismiss 关不掉 → 残留遮挡
            if (handSelectDialog.isShowing()) return;
            // 已关闭的旧实例：兜底 dismiss 清理后重建
            handSelectDialog.dismiss();
            handSelectDialog = null;
        }
        RPSDialog dialog = new RPSDialog(activity);
        handSelectDialog = dialog;
        dialog.setCancelable(false)
                .setOnResultListener(result -> {
                    lastHandSent = result;
                    // 先隐藏弹窗再发送协议：即使发送过程出现异常，弹窗也已在点击瞬间关闭
                    dialog.dismiss();
                    engine().sendHandResult(result);
                });
        dialog.show();
    }

    /**
     * 新对局进入猜拳阶段时重置结果抑制状态（由 YGOProActivity onStateChanged(HAND_SELECT) 调用）
     */
    public void resetRpsResultState() {
        rpsResultShown = false;
        lastHandSent = 0;
        rpsAnimating = false;
        pendingHandSelect = false;
    }

    /**
     * STOC_HAND_RESULT：播放猜拳结果动画（本方手势自底上升、对方手势倒置自 layout_game_right 顶部下降）。
     * 动画期间置 rpsAnimating，期间到达的 MSG_SELECT_HAND 会被 showHandSelectDialog 挂起；
     * 动画完全结束后在回调里清除标志，并按需（平局）重新显示弹窗——胜负则因 rpsResultShown 不再显示。
     */
    public void onHandResult(int myHand, int oppHand) {
        // 仅分出胜负（非平局）时抑制后续 RPSDialog 显示；
        // 平局（手势相同）不置位，服务器重发 MSG_SELECT_HAND 时仍弹窗供玩家再次出拳
        if (myHand != oppHand) {
            rpsResultShown = true;
        }
        if (handSelectDialog != null) {
            rpsAnimating = true;
            handSelectDialog.playResultAnimation(myHand, oppHand, () -> {
                rpsAnimating = false;
                if (pendingHandSelect) {
                    pendingHandSelect = false;
                    // 动画播完后才重新显示；若已分胜负，showHandSelectDialog 内部会自动应答并跳过显示
                    showHandSelectDialog();
                }
            });
        }
    }

    public void showTPSelectDialog() {
        if (tpSelectDialog != null && tpSelectDialog.isShowing()) return;
        FirstOrSecondDialog dialog = new FirstOrSecondDialog(activity);
        tpSelectDialog = dialog;
        dialog.setOnSelectListener(first -> {
            // FirstOrSecondDialog 已在点击瞬间关闭，这里发送 CTOS_TP_RESULT（1=先攻 0=后攻）；
            // 服务端 SingleDuel::TPResult 可能按结果换座，MSG_START 的 playertype 决定我方先后攻身份
            engine().sendTPResult(first);
        });
        dialog.show();
    }

    // === 是/否 / 选项 / 效果确认 ===

    public void showYesNoDialog(ByteBuffer data) {
        // duelclient.cpp L1902-1910：player(1) + desc(4)，desc 走 GetDesc（系统字符串或卡片脚本提示文字）
        int descId = 0;
        if (data != null && data.remaining() >= 5) {
            data.get(); // selecting_player
            descId = data.getInt();
        }
        YesOrNoDialog.showYesNoQuery(activity, DataManager.get().getDesc(descId, "是否发动效果？"));
    }

    /**
     * MSG_SELECT_OPTION（duelclient.cpp L1912-1920）：player(1) + count(1) + count×desc(4)。
     * 选项经 DataManager.getDesc 解析：<=0x7ff 为系统字符串，否则 卡号*16+n
     * 取 cdb 缓存进 Card.Stras 的脚本提示文字（str1~str16）；
     * 标题取系统字符串 555（"Select an option."）。
     * 点击选项发送 CTOS_RESPONSE（int32 索引，playerop.cpp select_option 校验范围）。
     */
    public void showOptionDialog(ByteBuffer data) {
        OptionDialog.showOptionDialog(activity, data);
    }

    public void showEffectYnDialog(ByteBuffer data) {
        YesOrNoDialog.showEffectYnDialog(activity, data);
    }

    // === 场上命令 / 位置 / 表示形式 ===

    public void showBattleCmdDialog(ByteBuffer data) {
        // 场上命令模式：不再弹模态对话框。点击场上卡片弹攻击/发动菜单，
        // 进 M2/结束用阶段按钮（btnPhaseNext/btnEp 已按 selectType==10 响应 2/3）
        fieldCtl().beginBattleCommand();
    }

    public void showIdleCmdDialog(ByteBuffer data) {
        // 场上命令模式：不再弹模态对话框。点击手牌/场上卡片弹召唤/盖放/发动菜单，
        // 进 BP/结束用阶段按钮（btnPhaseNext/btnEp 已按 selectType==11 响应 6/7）
        fieldCtl().beginIdleCommand();
    }

    public void showPlaceSelectDialog(boolean isDisfield) {
        fieldCtl().beginPlaceSelect(isDisfield);
    }

    /**
     * MSG_SELECT_POSITION：data 为 GameEngine 打包的 code(4) + positions(4)。
     * 标题取系统字符串 561（对齐 game.cpp wPosSelect 的 GetSysString(561)）；
     * 选择后发送 CTOS_RESPONSE，core 按所选形式把卡放上场并下发场地更新同步状态。
     */
    public void showPositionSelectDialog(ByteBuffer data) {
        PosSelectDialog.showPositionSelectDialog(activity, imageLoader, data);
    }

    // === 卡组选择 / SIDE ===

    public void showDeckSelectDialog() {
        YesOrNoDialog.showDeckSelectDialog(activity);
    }

    public void loadAndSendDeck(File ydkFile) {
        new Thread(() -> {
            List<Integer> main = new ArrayList<>();
            List<Integer> extra = new ArrayList<>();
            List<Integer> side = new ArrayList<>();

            try (BufferedReader reader = new BufferedReader(new FileReader(ydkFile))) {
                String line;
                int section = 0;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    if (line.equalsIgnoreCase("#main")) {
                        section = 1;
                        continue;
                    }
                    if (line.equalsIgnoreCase("#extra")) {
                        section = 2;
                        continue;
                    }
                    if (line.equalsIgnoreCase("!side")) {
                        section = 3;
                        continue;
                    }
                    try {
                        int code = Integer.parseInt(line);
                        switch (section) {
                            case 1:
                                main.add(code);
                                break;
                            case 2:
                                extra.add(code);
                                break;
                            case 3:
                                side.add(code);
                                break;
                        }
                    } catch (NumberFormatException e) { /* skip */ }
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to load deck: " + ydkFile.getName(), e);
                mainHandler.post(() -> activity.showHintMessage("卡组加载失败"));
                return;
            }

            engine().sendDeckUpdate(main, extra, side);
            mainHandler.post(() -> {
                activity.showHintMessage("卡组已发送: " + main.size() + "+" + extra.size() + "+" + side.size());
            });
        }, "DeckLoad").start();
    }

    // === 连锁 ===

    public void showChainSelectDialog(ByteBuffer data) {
        // duelclient.cpp L2080-2085：selecting_player(1) count(1) specount(1) hint0(4) hint1(4)
        // 注意首字节是 selecting_player，必须先跳过，否则 count/specount 与后续条目全部错位
        if (data == null || data.remaining() < 11) {
            sendResponseInt(-1);
            return;
        }
        data.get(); // selecting_player
        int count = data.get() & 0xFF;
        int specount = data.get() & 0xFF;
        int hint0 = data.getInt();
        int hint1 = data.getInt();
        // duelclient.cpp L2091：specount == 0x7f 表示这是诱发类效果询问（select_trigger）
        boolean selectTrigger = (specount == 0x7f);

        GameEngine e = engine();
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

            String cardName = activity.getCardDisplayName(code);
            // 连锁描述同样可能为卡片脚本提示文字（卡号*16+n），统一走 getDesc
            String descStr = desc > 0 ? DataManager.get().getDesc(desc, "效果") : "效果";
            chainOptions.add(cardName + " - " + descStr);
            chainFlags.add(flag);
        }

        AppsSettings settings = AppsSettings.get();
        CardDetailPanel p = panel();
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
            if (settings.getIntSettings("chkWaitChain", 0) == 1 && !ignoreChain) {
                mainHandler.postDelayed(() -> sendResponseInt(-1), 320 + random.nextInt(321));
            } else {
                sendResponseInt(-1);
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
            sendResponseInt(autoIndex);
            return;
        }

        // duelclient.cpp L2173-2174：count == 0 且未自动放弃（说明开了"显示时点"）
        // → 弹 sys201 + sys202 询问，而不是直接应答 -1
        if (chainOptions.isEmpty()) {
            YesOrNoDialog.showChainEmptyQuery(activity);
            return;
        }

        // panelmode（overlay 连锁项）不进入场上点击模式；强制连锁不可取消
        YesOrNoDialog.setChainForcedMode(chainForced && !panelmode);

        // duelclient.cpp L2164-2170：panelmode → 保留列表对话框（overlay 单元无法在场上单独点击发动）
        if (panelmode) {
            clearChainSelect();
            YesOrNoDialog.showChainListDialog(activity, contiExist, selectTrigger, chainForced, chainOptions, chainFlags);
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
                ? sysText(222, "是否要发动诱发类效果？") + "\n" + sysText(223, "稍后将询问其他可以发动的效果。")
                : sysText(203, "是否要进行连锁？");
        YesOrNoDialog.showChainQuery(activity, queryText);
    }

    /**
     * 对齐 C++ ClientField::ClearChainSelect()：清掉场上卡片的可发动高亮与选择标记，
     * 并清空 engine/field 两侧可发动列表（field.clearChainSelect 复位标记但不清 activatableCards 本身）
     */
    public void clearChainSelect() {
        GameEngine e = engine();
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
        GameFieldController ctl = fieldCtl();
        if (ctl != null) ctl.beginChainCommand();
        CardDetailPanel p = panel();
        if (p != null) {
            p.setSelectType(16);
            if (showCancelButton) {
                p.showCancelOrFinishButton(sysText(1295, "取消操作"));
            }
        }
    }

    /**
     * 退出场上点击发动模式：复位命令上下文、关闭残留命令菜单与询问窗引用
     */
    private void exitChainFieldMode() {
        YesOrNoDialog.setChainForcedMode(false);
        YesOrNoDialog.setChainQueryDialog(null);
        GameFieldController ctl = fieldCtl();
        if (ctl != null) ctl.endChainCommand();
    }

    /**
     * 放弃连锁（应答 -1），对齐 event_handler.cpp BUTTON_NO / CancelOrFinish 的 SetResponseI(-1)：
     * 退出场上模式、清高亮、隐藏「取消操作」按钮并复位选择类型
     */
    public void finishChainPass() {
        exitChainFieldMode();
        clearChainSelect();
        CardDetailPanel p = panel();
        if (p != null) {
            p.hideCancelOrFinishButton();
            p.setSelectType(-1);
        }
        sendResponseInt(-1);
    }

    /**
     * 发动指定连锁项（由 CmdMenuDialog 连锁分支调用），对齐 event_handler.cpp L833-841
     * MSG_SELECT_CHAIN → SetResponseI(index)：连锁发动响应仅发送连锁项索引（区别于 idle 的 (index<<16)+5）
     */
    public void activateChainOption(int index) {
        exitChainFieldMode();
        clearChainSelect();
        CardDetailPanel p = panel();
        if (p != null) {
            p.hideCancelOrFinishButton();
            p.setSelectType(-1);
        }
        sendResponseInt(index);
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
        CardDetailPanel p = panel();
        if (p != null) {
            p.hideCancelOrFinishButton();
            p.setCurrentDialog(q);
        }
        GameFieldController ctl = fieldCtl();
        if (ctl != null) ctl.endChainCommand();
        q.show();
        return true;
    }

    public void showSortChainDialog(ByteBuffer data) {
        if (data == null || data.remaining() < 1) {
            sendResponseInt(0);
            return;
        }
        int count = data.get() & 0xFF;
        if (count <= 1) {
            sendResponseInt(0);
            return;
        }
        activity.showHintMessage("连锁排序: 自动排序");
        sendResponseInt(0);
    }

    // === 卡片选择类 ===

    public void showCardSelectDialog(ByteBuffer data) {
        // duelclient.cpp L1923-1984：player(1) cancelable(1) min(1) max(1) count(1) + n×[code4 ctrl1 loc1 seq1 subseq1]
        if (data == null || data.remaining() < 5) {
            sendResponseInt(0);
            return;
        }
        int player = data.get() & 0xFF;
        int cancelable = data.get() & 0xFF;
        int min = data.get() & 0xFF;
        int max = data.get() & 0xFF;
        int count = data.get() & 0xFF;
        List<CardSelectDialog.CardItem> items = new ArrayList<>();
        for (int i = 0; i < count && data.remaining() >= 8; i++) {
            int code = data.getInt();
            int ctrl = data.get() & 0xFF;
            int loc = data.get() & 0xFF;
            int seq = data.get() & 0xFF;
            int subSeq = data.get() & 0xFF;
            items.add(new CardSelectDialog.CardItem(code, ctrl, loc, seq, subSeq, i));
        }
        if (items.isEmpty()) {
            sendResponseInt(0);
            return;
        }
        final List<CardSelectDialog.CardItem> cardInfos = items;
        CardSelectDialog dialog = new CardSelectDialog(activity, imageLoader);
        panel().setCardSelectDialog(dialog);
        dialog.setMode(CardSelectDialog.MODE_SELECT)
                .setTitle(selectTitleText(560, "选择卡片") + "(" + min + "-" + max + ")")
                .setCards(items)
                .setSelectRange(min, max)
                .setCancelable(cancelable != 0)
                // 我方协议索引：localPlayer 为对合映射，localPlayer(0) = 我方对应的协议玩家（先攻=0/后攻=1）
                .setLocalPlayer(engine().localPlayer(0))
                .setListener(new CardSelectDialog.OnCardSelectListener() {
                    @Override
                    public void onCardClicked(int index) {
                        panel().updateCancelOrFinishButton(dialog.isReady(),
                                dialog.isCancelable(), dialog.getSelectedCount() > 0);
                    }

                    @Override
                    public void onCardsSelected(List<Integer> selectedIndices) {
                        sendCardSelectResponse(cardInfos, selectedIndices);
                    }

                    @Override
                    public void onCancel() {
                        sendResponseInt(-1);
                    }
                })
                .setOnDismissListener(() -> {
                    panel().hideCancelOrFinishButton();
                    panel().setCardSelectDialog(null);
                })
                .show();
        // 关闭统一由 cardDetailPanel 的 cancel/finish 按钮承担（对齐 gframe ShowCancelOrFinishButton）
        panel().updateCancelOrFinishButton(min == 0, cancelable != 0, false);
    }

    public void showTributeSelectDialog(ByteBuffer data) {
        // duelclient.cpp L2300-2342：player(1) cancelable(1) min(1) max(1) count(1) + n×[code4 ctrl1 loc1 seq1 t1]
        if (data == null || data.remaining() < 5) {
            sendResponseInt(0);
            return;
        }
        int player = data.get() & 0xFF;
        int cancelable = data.get() & 0xFF;
        int min = data.get() & 0xFF;
        int max = data.get() & 0xFF;
        int count = data.get() & 0xFF;
        List<CardSelectDialog.CardItem> items = new ArrayList<>();
        for (int i = 0; i < count && data.remaining() >= 8; i++) {
            int code = data.getInt();
            int ctrl = data.get() & 0xFF;
            int loc = data.get() & 0xFF;
            int seq = data.get() & 0xFF;
            int tributeValue = data.get() & 0xFF;
            items.add(new CardSelectDialog.CardItem(code, ctrl, loc, seq, 0, i, tributeValue));
        }
        if (items.isEmpty()) {
            sendResponseInt(0);
            return;
        }
        final List<CardSelectDialog.CardItem> cardInfos = items;
        CardSelectDialog dialog = new CardSelectDialog(activity, imageLoader);
        panel().setCardSelectDialog(dialog);
        dialog.setMode(CardSelectDialog.MODE_SELECT)
                .setTitle(selectTitleText(531, "解放选择") + "(" + min + "-" + max + ")")
                .setCards(items)
                .setSelectRange(min, max)
                .setCancelable(cancelable != 0)
                .setValueVisible(true)
                .setLocalPlayer(engine().localPlayer(0))
                .setListener(new CardSelectDialog.OnCardSelectListener() {
                    @Override
                    public void onCardClicked(int index) {
                        panel().updateCancelOrFinishButton(dialog.isReady(),
                                dialog.isCancelable(), dialog.getSelectedCount() > 0);
                    }

                    @Override
                    public void onCardsSelected(List<Integer> selectedIndices) {
                        sendCardSelectResponse(cardInfos, selectedIndices);
                    }

                    @Override
                    public void onCancel() {
                        sendResponseInt(-1);
                    }
                })
                .setOnDismissListener(() -> {
                    panel().hideCancelOrFinishButton();
                    panel().setCardSelectDialog(null);
                })
                .show();
        panel().updateCancelOrFinishButton(min == 0, cancelable != 0, false);
    }

    public void showCounterSelectDialog(ByteBuffer data) {
        YesOrNoDialog.showCounterSelectDialog(activity, data);
    }

    public void showSumSelectDialog(ByteBuffer data) {
        // duelclient.cpp L2370-2414 + playerop.cpp L661-688：
        // select_mode(1) player(1) sumval(4) min(1) max(1) must_count(1)
        // + must×[code4 ctrl1 loc1 seq1 opParam4]（11字节/条，无subSeq）
        // + count(1) + n×[code4 ctrl1 loc1 seq1 opParam4]
        if (data == null || data.remaining() < 9) {
            sendResponseInt(0);
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
            int code = data.getInt();
            int ctrl = data.get() & 0xFF;
            int loc = data.get() & 0xFF;
            int seq = data.get() & 0xFF;
            int opParam = data.getInt();
            mustCards.add(new CardSelectDialog.CardItem(code, ctrl, loc, seq, 0, 0, opParam));
        }
        int count = data.remaining() >= 1 ? (data.get() & 0xFF) : 0;
        List<CardSelectDialog.CardItem> items = new ArrayList<>();
        for (int i = 0; i < count && data.remaining() >= 11; i++) {
            int code = data.getInt();
            int ctrl = data.get() & 0xFF;
            int loc = data.get() & 0xFF;
            int seq = data.get() & 0xFF;
            int opParam = data.getInt();
            items.add(new CardSelectDialog.CardItem(code, ctrl, loc, seq, 0, i, opParam));
        }
        if (items.isEmpty()) {
            // 无可选卡：直接发送 must 占位（对齐 C++ ShowSelectSum 自动提交）
            byte[] resp = new byte[1 + mustCount];
            resp[0] = (byte) mustCount;
            engine().sendResponse(resp);
            return;
        }
        final List<CardSelectDialog.CardItem> cardInfos = items;
        final int fMustCount = mustCount;
        CardSelectDialog dialog = new CardSelectDialog(activity, imageLoader);
        panel().setCardSelectDialog(dialog);
        dialog.setMode(CardSelectDialog.MODE_SUM)
                .setTitle(selectTitleText(560, "选择卡片") + "(" + sumVal + ")")
                .setCards(items)
                .setMustCards(mustCards)
                .setSelectRange(min, max)
                .setSumValue(sumVal, selectMode)
                .setValueVisible(true)
                .setLocalPlayer(engine().localPlayer(0))
                .setListener(new CardSelectDialog.OnCardSelectListener() {
                    @Override
                    public void onCardClicked(int index) {
                        panel().updateCancelOrFinishButton(dialog.isReady(), false,
                                dialog.getSelectedCount() > 0);
                    }

                    @Override
                    public void onCardsSelected(List<Integer> selectedIndices) {
                        sendSumResponse(selectedIndices, fMustCount);
                    }

                    @Override
                    public void onCancel() {
                        sendResponseInt(-1);
                    }
                })
                .setOnDismissListener(() -> {
                    panel().hideCancelOrFinishButton();
                    panel().setCardSelectDialog(null);
                })
                .show();
        panel().updateCancelOrFinishButton(dialog.isReady(), false, false);
    }

    // === 响应编码 ===

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
        engine().sendResponse(buf.array());
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
        engine().sendResponse(buf.array());
    }

    // === 宣言类 ===

    /**
     * MSG_ANNOUNCE_RACE（duelclient.cpp L3996-4014）：player(1)+count(1)+available(4)，
     * GameEngine 已消费 player 并打包 count+available。勾选数达到 count 时
     * 自动应答种族位掩码（event_handler.cpp CHECK_RACE L1003-1015）。
     * 标题取 select_hint，缺省 563（对齐 duelclient.cpp L4006）。
     */
    public void showAnnounceRaceDialog(ByteBuffer data) {
        if (data == null || data.remaining() < 5) return;
        int count = data.get() & 0xFF;
        int available = data.getInt();
        if (announceRaceDialog != null) announceRaceDialog.dismiss();
        AnnounceRaceDialog dialog = new AnnounceRaceDialog(activity);
        announceRaceDialog = dialog;
        dialog.setTitle(selectTitleText(563, "请选择要宣言的种族"))
                .setAvailableMask(available)
                .setAnnounceCount(count)
                .setOnRaceSelectedListener(this::sendResponseInt)
                .setOnDismissListener(() -> announceRaceDialog = null);
        dialog.show();
    }

    /**
     * MSG_ANNOUNCE_ATTRIB（duelclient.cpp L4015-4033）：同上结构，7 属性；
     * 勾选数达到 count 自动应答属性位掩码（CHECK_ATTRIBUTE L989-1001），缺省标题 562。
     */
    public void showAnnounceAttribDialog(ByteBuffer data) {
        if (data == null || data.remaining() < 5) return;
        int count = data.get() & 0xFF;
        int available = data.getInt();
        if (announceAttributeDialog != null) announceAttributeDialog.dismiss();
        AnnounceAttributeDialog dialog = new AnnounceAttributeDialog(activity);
        announceAttributeDialog = dialog;
        dialog.setTitle(selectTitleText(562, "选择属性"))
                .setAvailableMask(available)
                .setAnnounceCount(count)
                .setOnAttributeSelectedListener(this::sendResponseInt)
                .setOnDismissListener(() -> announceAttributeDialog = null);
        dialog.show();
    }

    /**
     * MSG_ANNOUNCE_CARD（duelclient.cpp L4034-4050）：player(1)+count(1)+count×opcode(4)，
     * opcodes 供 is_declarable 过滤（client_field.cpp L1358-1499）；
     * 确定发送卡号（BUTTON_ANCARD_OK L486-493），缺省标题 564。
     */
    public void showAnnounceCardDialog(ByteBuffer data) {
        if (data == null || data.remaining() < 1) return;
        int count = data.get() & 0xFF;
        List<Integer> opcodes = new ArrayList<>();
        for (int i = 0; i < count && data.remaining() >= 4; i++) {
            opcodes.add(data.getInt());
        }
        if (announceCardDialog != null) announceCardDialog.dismiss();
        AnnounceCardDialog dialog = new AnnounceCardDialog(activity);
        announceCardDialog = dialog;
        dialog.setTitle(selectTitleText(564, "宣言卡片"))
                .setOpcodes(opcodes)
                .setOnCardDeclaredListener(this::sendResponseInt)
                .setOnDismissListener(() -> announceCardDialog = null);
        dialog.show();
    }

    /**
     * MSG_ANNOUNCE_NUMBER（duelclient.cpp L4051-4093）：player(1)+count(1)+count×value(4)。
     * 响应为选中项索引（BUTTON_ANNUMBER_OK L480-484：SetResponseI(cbANNumber->getSelected())），
     * 缺省标题 565。
     */
    public void showAnnounceNumberDialog(ByteBuffer data) {
        if (data == null || data.remaining() < 1) {
            sendResponseInt(0);
            return;
        }
        int count = data.get() & 0xFF;
        List<Integer> values = new ArrayList<>();
        for (int i = 0; i < count && data.remaining() >= 4; i++) {
            values.add(data.getInt());
        }
        if (values.isEmpty()) {
            sendResponseInt(0);
            return;
        }
        if (announceNumberDialog != null) announceNumberDialog.dismiss();
        AnnounceNumberDialog dialog = new AnnounceNumberDialog(activity);
        announceNumberDialog = dialog;
        dialog.setTitle(selectTitleText(565, "选择数字"))
                .setValues(values)
                .setOnNumberSelectedListener(this::sendResponseInt)
                .setOnDismissListener(() -> announceNumberDialog = null);
        dialog.show();
    }

    // === 排序 / 反选 / 确认展示 ===

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
            engine().sendResponse(resp);
            return;
        }
        List<CardSelectDialog.CardItem> items = new ArrayList<>();
        for (int i = 0; i < count && data.remaining() >= 7; i++) {
            int code = data.getInt();
            int ctrl = data.get() & 0xFF;
            int loc = data.get() & 0xFF;
            int seq = data.get() & 0xFF;
            items.add(new CardSelectDialog.CardItem(code, ctrl, loc, seq, 0, i));
        }
        if (items.size() < count) {
            byte[] resp = new byte[items.size()];
            for (int i = 0; i < resp.length; i++) resp[i] = (byte) i;
            engine().sendResponse(resp);
            return;
        }
        CardSelectDialog dialog = new CardSelectDialog(activity, imageLoader);
        panel().setCardSelectDialog(dialog);
        dialog.setMode(CardSelectDialog.MODE_SORT)
                .setTitle(selectTitleText(205, "卡片排序"))
                .setCards(items)
                .setListener(new CardSelectDialog.OnCardSelectListener() {
                    @Override
                    public void onSorted(int[] respBuf) {
                        // playerop.cpp L776-788：响应 = 排列（0..n-1 无重复）
                        byte[] resp = new byte[respBuf.length];
                        for (int i = 0; i < respBuf.length; i++) {
                            resp[i] = (byte) respBuf[i];
                        }
                        engine().sendResponse(resp);
                    }
                })
                .setOnDismissListener(() -> {
                    panel().hideCancelOrFinishButton();
                    panel().setCardSelectDialog(null);
                })
                .show();
    }

    public void showUnselectCardDialog(ByteBuffer data) {
        // duelclient.cpp L1986-2080：player(1) finishable(1) cancelable(1) min(1) max(1)
        // count1(1) + count1×[code4 ctrl1 loc1 seq1 subseq1] + count2(1) + count2×[code4 ctrl1 loc1 seq1 subseq1]
        // 点击任意卡立即提交 [1, seq]；finishable 时 OK=发送-1；cancelable 时取消=发送-1
        if (data == null || data.remaining() < 6) {
            sendResponseInt(-1);
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
            int code = data.getInt();
            int ctrl = data.get() & 0xFF;
            int loc = data.get() & 0xFF;
            int seq = data.get() & 0xFF;
            int subSeq = data.get() & 0xFF;
            items.add(new CardSelectDialog.CardItem(code, ctrl, loc, seq, subSeq, seqIndex++));
        }
        int count2 = data.remaining() >= 1 ? (data.get() & 0xFF) : 0;
        for (int i = 0; i < count2 && data.remaining() >= 8; i++) {
            int code = data.getInt();
            int ctrl = data.get() & 0xFF;
            int loc = data.get() & 0xFF;
            int seq = data.get() & 0xFF;
            int subSeq = data.get() & 0xFF;
            items.add(new CardSelectDialog.CardItem(code, ctrl, loc, seq, subSeq, seqIndex++));
        }
        if (items.isEmpty()) {
            sendResponseInt(-1);
            return;
        }
        boolean[] preSelected = new boolean[items.size()];
        for (int i = count1; i < items.size(); i++) {
            preSelected[i] = true;
        }
        final List<CardSelectDialog.CardItem> cardInfos = items;
        CardSelectDialog dialog = new CardSelectDialog(activity, imageLoader);
        panel().setCardSelectDialog(dialog);
        dialog.setMode(CardSelectDialog.MODE_UNSELECT)
                .setTitle(selectTitleText(560, "选择卡片") + "(" + min + "-" + max + ")")
                .setCards(items)
                .setPreSelected(preSelected)
                .setSelectRange(min, max)
                .setCancelable(cancelable)
                .setFinishable(finishable)
                .setLocalPlayer(engine().localPlayer(0))
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
                        engine().sendResponse(buf.array());
                    }

                    @Override
                    public void onCancel() {
                        sendResponseInt(-1);
                    }
                })
                .setOnDismissListener(() -> {
                    panel().hideCancelOrFinishButton();
                    panel().setCardSelectDialog(null);
                })
                .show();
        // 关闭统一由 cardDetailPanel 的 cancel/finish 按钮承担（对齐 gframe ShowCancelOrFinishButton）
        if (finishable) {
            panel().showCancelOrFinishButton("完成");
        } else if (cancelable) {
            panel().showCancelOrFinishButton("取消");
        } else {
            panel().hideCancelOrFinishButton();
        }
    }

    public void showConfirmCardsDialog(ByteBuffer data) {
        // duelclient.cpp L2518-2617：GameEngine 转发 skipPanel(1) + n×[code4 ctrl1 loc1 seq1]
        // 纯展示：OK 仅关闭（无响应数据），对齐 C++ BUTTON_CARD_SEL_OK 的 actionSignal.Set()
        if (data == null || data.remaining() < 1) {
            panel().hideCancelOrFinishButton();
            return;
        }
        int skipPanel = data.get() & 0xFF;
        List<CardDisplayDialog.CardItem> items = new ArrayList<>();
        while (data.remaining() >= 7) {
            int code = data.getInt();
            int ctrl = data.get() & 0xFF;
            int loc = data.get() & 0xFF;
            int seq = data.get() & 0xFF;
            // duelclient.cpp L2546-2566：仅卡组/额外（l & 0x41）的卡进入面板确认
            if ((loc & 0x41) != 0) {
                items.add(new CardDisplayDialog.CardItem(code, ctrl, loc, seq, 0));
            }
        }
        // skip_panel 或面板卡不足 2 张（单张走场上翻卡动画路径）时不弹面板
        if (skipPanel != 0 || items.size() <= 1) {
            panel().hideCancelOrFinishButton();
            return;
        }
        CardDisplayDialog dialog = new CardDisplayDialog(activity, imageLoader);
        panel().setCardDisplayDialog(dialog);
        // duelclient.cpp L2611：标题取系统字符串 208（"确认%d张卡"）
        dialog.setTitle(DuelLogDialog.sysFormat(208, "确认%d张卡", items.size()))
                .setCards(items)
                .setLocalPlayer(engine().localPlayer(0))
                .setCardClickListener(this::showCardInfoFromItem)
                .setOnDismissListener(() -> {
                    panel().hideCancelOrFinishButton();
                    panel().setCardDisplayDialog(null);
                })
                .show();
    }

    public void showCardInfoFromItem(CardDisplayDialog.CardItem item) {
        GameField.ClientCard card = new GameField.ClientCard();
        // code 可能带 0x80000000 翻面标志位，掩码后再使用（对齐 duelclient.cpp code & 0x7fffffff）
        card.code = item.code & 0x7fffffff;
        card.controler = (item.controler == engine().localPlayer(0)) ? 0 : 1;
        card.location = item.location;
        card.sequence = item.sequence;
        card.position = 0x1;
        activity.showCardInfoPanel(card);
    }

    // === 决斗结果 / 结束 ===

    /**
     * 连接断开 / 决斗结束时统一关闭所有可能残留的选择类对话框，
     * 避免遗留弹窗遮挡重新显示的局域网主界面（由 YGOProActivity returnToLanMain 调用）
     */
    public void dismissOpenGameDialogs() {
        if (handSelectDialog != null) {
            handSelectDialog.dismiss();
            handSelectDialog = null;
        }
        if (tpSelectDialog != null) {
            tpSelectDialog.dismiss();
            tpSelectDialog = null;
        }
        rpsResultShown = false;
        lastHandSent = 0;
        // 连锁场上点击模式复位：关闭询问窗、退出命令上下文并清除高亮，避免脏状态残留到下一局
        YesOrNoDialog.dismissChainQuery();
        GameFieldController chainCtl = fieldCtl();
        if (chainCtl != null) chainCtl.endChainCommand();
        clearChainSelect();
        panel().dismissOpenDialogs();
    }

    /**
     * 退出对战（layout_game_right 隐藏）时关闭可能残留的宣言类对话框：
     * 宣言属性 / 宣言数字 / 宣言种族（由 YGOProActivity hideGameUI 调用）
     */
    public void dismissAnnounceDialogs() {
        if (announceAttributeDialog != null) {
            announceAttributeDialog.dismiss();
            announceAttributeDialog = null;
        }
        if (announceNumberDialog != null) {
            announceNumberDialog.dismiss();
            announceNumberDialog = null;
        }
        if (announceRaceDialog != null) {
            announceRaceDialog.dismiss();
            announceRaceDialog = null;
        }
    }
}