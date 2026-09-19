package cn.garymb.ygomobile.game;

import android.os.Handler;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import cn.garymb.ygomobile.YGOProActivity;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.render.CardDetailPanel;
import cn.garymb.ygomobile.ui.dialogs.AnnounceAttributeDialog;
import cn.garymb.ygomobile.ui.dialogs.AnnounceCardDialog;
import cn.garymb.ygomobile.ui.dialogs.AnnounceNumberDialog;
import cn.garymb.ygomobile.ui.dialogs.AnnounceRaceDialog;
import cn.garymb.ygomobile.ui.dialogs.CardDisplayDialog;
import cn.garymb.ygomobile.ui.dialogs.FirstOrSecondDialog;
import cn.garymb.ygomobile.ui.dialogs.OptionDialog;
import cn.garymb.ygomobile.ui.dialogs.PosSelectDialog;
import cn.garymb.ygomobile.ui.dialogs.RPSDialog;
import cn.garymb.ygomobile.ui.dialogs.YesOrNoDialog;
import ocgcore.DataManager;

/**
 * 决斗中所有选择/确认对话框的统一管理类，从 YGOProActivity 迁移而来，
 * 由 YGOProActivity 通过 getDialogUtil() 统一调用。
 *
 * <p>本类是门面：保留全部对外 public 方法签名（对外调用点零改动），把实现按职责分流：
 * <ul>
 *   <li>纯视图 dialog 的 ByteBuffer 解析下沉到各自 ui.dialogs 静态工厂（OptionDialog /
 *       YesOrNoDialog / PosSelectDialog / Announce{Race,Attribute,Card,Number}Dialog），一行转发；</li>
 *   <li>深度耦合 GameField/GameFieldController/GameEngine 的连锁编排下沉到 {@link ChainSelectController}、
 *       卡片选择/合计/排序/反选/确认展示下沉到 {@link CardSelectController}（同 game 包协作类），一行转发；</li>
 *   <li>猜拳/先后攻生命周期、卡组装载发送、场上命令转发、弹窗统一关闭等体量小的编排保留在本类。</li>
 * </ul>
 * 协作类经本门面包级私有的 {@code activity}/{@code imageLoader}/{@code mainHandler}/{@code random}/
 * {@code engine()}/{@code panel()}/{@code fieldCtl()}/{@code sendResponseInt()}/{@code selectTitleText()}/
 * {@code sysText()} 直连共享状态与底层方法，不引入 Context 对象。
 */
public class ShowDialogUtil {

    private static final String TAG = "ShowDialogUtil";


    // === 供同包协作类直连的共享依赖（包级私有） ===

    final YGOProActivity activity;
    final ImageLoader imageLoader;
    final Handler mainHandler;
    final Random random = new Random();

    private RPSDialog handSelectDialog;
    private boolean rpsResultShown;
    private int lastHandSent;
    // 猜拳结果动画正在播放：期间到达的 MSG_SELECT_HAND 先挂起，待动画结束回调再决定是否重显弹窗，
    // 避免动画未播完就因通讯结果抢先重新显示 RPSDialog
    private boolean rpsAnimating;
    private boolean pendingHandSelect;
    private FirstOrSecondDialog tpSelectDialog;

    // === 同包协作类（构造时注入 this，经包级私有直连上述依赖） ===

    final ChainSelectController chainCtl;
    final CardSelectController cardCtl;

    public ShowDialogUtil(YGOProActivity activity, ImageLoader imageLoader, Handler mainHandler) {
        this.activity = activity;
        this.imageLoader = imageLoader;
        this.mainHandler = mainHandler;
        this.chainCtl = new ChainSelectController(this);
        this.cardCtl = new CardSelectController(this);
    }

    // === 依赖桥接（包级私有，供协作类直连） ===

    GameEngine engine() {
        return activity.getEngine();
    }

    CardDetailPanel panel() {
        return activity.getCardDetailPanel();
    }

    GameFieldController fieldCtl() {
        return activity.getFieldCtl();
    }

    void sendResponseInt(int value) {
        activity.sendResponseInt(value);
    }

    // === 通用辅助（包级私有，供协作类直连） ===

    /**
     * 选择类对话框标题：优先使用 MSG_HINT(HINT_SELECTMSG) 通讯下发的索引调用
     * StringManager.getSystemString，无索引时按 gframe 缺省值兜底
     * （duelclient.cpp：选卡/总合 560、解放 531、排序 205），消费后清零
     */
    String selectTitleText(int defIndex, String defText) {
        GameField f = engine() != null ? engine().getField() : null;
        int hint = (f != null) ? f.selectHint : 0;
        if (f != null) f.selectHint = 0;
        return DataManager.get().getStringManager()
                .getSystemString(hint > 0 ? hint : defIndex, defText);
    }

    /**
     * 系统字符串（对齐 gframe dataManager.GetSysString(index)）
     */
    String sysText(int index, String defText) {
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
        // duelclient.cpp L1902-1910：player(1) + desc(4) 解析下沉至 YesOrNoDialog 静态工厂
        YesOrNoDialog.showYesNoDialog(activity, data);
    }

    /**
     * MSG_SELECT_OPTION（duelclient.cpp L1912-1920）：选项解析与应答下沉至 OptionDialog 静态工厂。
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

    // === 连锁（编排下沉 ChainSelectController） ===

    public void showChainSelectDialog(ByteBuffer data) {
        chainCtl.showChainSelectDialog(data);
    }

    public void clearChainSelect() {
        chainCtl.clearChainSelect();
    }

    public void enterChainFieldMode(boolean showCancelButton) {
        chainCtl.enterChainFieldMode(showCancelButton);
    }

    public void finishChainPass() {
        chainCtl.finishChainPass();
    }

    public void activateChainOption(int index) {
        chainCtl.activateChainOption(index);
    }

    public boolean handleChainCancel() {
        return chainCtl.handleChainCancel();
    }

    public void showSortChainDialog(ByteBuffer data) {
        chainCtl.showSortChainDialog(data);
    }

    // === 卡片选择类（编排下沉 CardSelectController） ===

    public void showCardSelectDialog(ByteBuffer data) {
        cardCtl.showCardSelectDialog(data);
    }

    public void showTributeSelectDialog(ByteBuffer data) {
        cardCtl.showTributeSelectDialog(data);
    }

    public void showCounterSelectDialog(ByteBuffer data) {
        YesOrNoDialog.showCounterSelectDialog(activity, data);
    }

    public void showSumSelectDialog(ByteBuffer data) {
        cardCtl.showSumSelectDialog(data);
    }

    public void showSortCardDialog(ByteBuffer data) {
        cardCtl.showSortCardDialog(data);
    }

    public void showUnselectCardDialog(ByteBuffer data) {
        cardCtl.showUnselectCardDialog(data);
    }

    public void showConfirmCardsDialog(ByteBuffer data) {
        cardCtl.showConfirmCardsDialog(data);
    }

    public void showCardInfoFromItem(CardDisplayDialog.CardItem item) {
        cardCtl.showCardInfoFromItem(item);
    }

    // === 宣言类（解析下沉各 Announce*Dialog 静态工厂） ===

    public void showAnnounceRaceDialog(ByteBuffer data) {
        AnnounceRaceDialog.showAnnounceRaceDialog(activity, data);
    }

    public void showAnnounceAttribDialog(ByteBuffer data) {
        AnnounceAttributeDialog.showAnnounceAttribDialog(activity, data);
    }

    public void showAnnounceCardDialog(ByteBuffer data) {
        AnnounceCardDialog.showAnnounceCardDialog(activity, data);
    }

    public void showAnnounceNumberDialog(ByteBuffer data) {
        AnnounceNumberDialog.showAnnounceNumberDialog(activity, data);
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
        GameFieldController fieldController = fieldCtl();
        if (fieldController != null) fieldController.endChainCommand();
        clearChainSelect();
        panel().dismissOpenDialogs();
    }

    /**
     * 退出对战（layout_game_right 隐藏）时关闭可能残留的宣言类对话框：
     * 宣言属性 / 宣言数字 / 宣言种族（由 YGOProActivity hideGameUI 调用）。
     * 各宣言弹窗的实例引用与去重已下沉至对应 ui.dialogs 静态工厂的 current，此处调用其 dismissCurrent()。
     */
    public void dismissAnnounceDialogs() {
        AnnounceAttributeDialog.dismissCurrent();
        AnnounceNumberDialog.dismissCurrent();
        AnnounceRaceDialog.dismissCurrent();
    }
}
