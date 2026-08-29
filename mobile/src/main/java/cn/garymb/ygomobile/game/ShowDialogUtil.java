package cn.garymb.ygomobile.game;

import android.os.Handler;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.YGOProActivity;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.render.CardDetailPanel;
import cn.garymb.ygomobile.ui.dialogs.CardDisplayDialog;
import cn.garymb.ygomobile.ui.dialogs.CardSelectDialog;
import cn.garymb.ygomobile.ui.dialogs.FirstOrSecondDialog;
import cn.garymb.ygomobile.ui.dialogs.OptionDialog;
import cn.garymb.ygomobile.ui.dialogs.PosSelectDialog;
import cn.garymb.ygomobile.ui.dialogs.RPSDialog;
import cn.garymb.ygomobile.ui.dialogs.YesOrNoDialog;
import ocgcore.DataManager;
import ocgcore.data.Card;

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
    private FirstOrSecondDialog tpSelectDialog;
    private PosSelectDialog posSelectDialog;
    private OptionDialog optionDialog;

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

    private int getResId(String name, String type) {
        return activity.getResources().getIdentifier(name, type, activity.getPackageName());
    }

    private View inflateSelectLayout() {
        View contentView = activity.getLayoutInflater().inflate(R.layout.dialog_game_select, null);
        contentView.findViewById(getResId("tv_select_title", "id")).setVisibility(View.GONE);
        contentView.findViewById(getResId("tv_select_hint", "id")).setVisibility(View.GONE);
        contentView.findViewById(getResId("layout_select_buttons", "id")).setVisibility(View.GONE);
        return contentView;
    }

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
        if (handSelectDialog != null && handSelectDialog.isShowing()) return;
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

    /** 新对局进入猜拳阶段时重置结果抑制状态（由 YGOProActivity onStateChanged(HAND_SELECT) 调用） */
    public void resetRpsResultState() {
        rpsResultShown = false;
        lastHandSent = 0;
    }

    /** STOC_HAND_RESULT：播放猜拳结果动画（本方手势自底上升、对方手势倒置自 layout_game_right 顶部下降） */
    public void onHandResult(int myHand, int oppHand) {
        // 仅分出胜负（非平局）时抑制后续 RPSDialog 显示；
        // 平局（手势相同）不置位，服务器重发 MSG_SELECT_HAND 时仍弹窗供玩家再次出拳
        if (myHand != oppHand) {
            rpsResultShown = true;
        }
        if (handSelectDialog != null) {
            handSelectDialog.playResultAnimation(myHand, oppHand);
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
        showYesNoQuery(DataManager.get().getDesc(descId, "是否发动效果？"));
    }

    /**
     * MSG_SELECT_OPTION（duelclient.cpp L1912-1920）：player(1) + count(1) + count×desc(4)。
     * 选项经 DataManager.getDesc 解析：<=0x7ff 为系统字符串，否则 卡号*16+n
     * 取 cdb 缓存进 Card.Stras 的脚本提示文字（str1~str16）；
     * 标题取系统字符串 555（"Select an option."）。
     * 点击选项发送 CTOS_RESPONSE（int32 索引，playerop.cpp select_option 校验范围）。
     */
    public void showOptionDialog(ByteBuffer data) {
        if (data == null || data.remaining() < 2) {
            return;
        }
        data.get(); // selecting_player
        int count = data.get() & 0xFF;
        List<String> options = new ArrayList<>();
        for (int i = 0; i < count && data.remaining() >= 4; i++) {
            int descId = data.getInt();
            options.add(DataManager.get().getDesc(descId, "Option " + (i + 1)));
        }
        if (options.isEmpty()) {
            // 无可解析选项时兜底应答 0，避免通讯挂起（core 侧会校验索引合法性）
            sendResponseInt(0);
            return;
        }
        if (optionDialog != null && optionDialog.isShowing()) return;
        OptionDialog dialog = new OptionDialog(activity);
        optionDialog = dialog;
        dialog.setTitle(optionTitleText())
                .setOptions(options)
                .setOnOptionSelectedListener(this::sendResponseInt)
                .setOnDismissListener(() -> optionDialog = null);
        dialog.show();
    }

    /** 选项弹窗标题：系统字符串 555（strings.conf "!system 555 Select an option."）；消费 selectHint 避免残留影响后续选卡标题 */
    private String optionTitleText() {
        GameField f = engine() != null ? engine().getField() : null;
        if (f != null) f.selectHint = 0;
        return DataManager.get().getStringManager().getSystemString(555, "请选择一项");
    }

    public void showEffectYnDialog(ByteBuffer data) {
        // duelclient.cpp L1868-1901：player(1) code(4) c(1) l(1) s(1) flag(1) desc(4)
        if (data == null || data.remaining() < 13) {
            showYesNoQuery("是否发动效果？");
            return;
        }
        data.get(); // selecting_player
        int code = data.getInt();
        data.position(data.position() + 4); // c, l, s, flag
        int desc = data.getInt();
        String cardName = activity.getCardDisplayName(code);
        String message;
        if (desc != 0) {
            String raw = DataManager.get().getDesc(desc, "");
            if (raw.contains("%s")) {
                // 对齐 C++ myswprintf(GetSysString(desc), GetName(code))
                try {
                    message = String.format(raw, cardName);
                } catch (Exception e) {
                    message = raw;
                }
            } else if (!raw.isEmpty()) {
                message = raw;
            } else {
                message = "是否发动「" + cardName + "」的效果？";
            }
        } else {
            message = "是否发动「" + cardName + "」的效果？";
        }
        showYesNoQuery(message);
    }

    /** 是/否确认弹窗公共构建：是=1 否=0（MSG_SELECT_YESNO / MSG_SELECT_EFFECTYN 应答） */
    private void showYesNoQuery(String message) {
        YesOrNoDialog dialog = new YesOrNoDialog(activity);
        dialog.setTitle("确认")
                .setMessage(message)
                .setType(YesOrNoDialog.TYPE_YES_NO)
                .setPositiveButtonText("是")
                .setNegativeButtonText("否")
                .setPositiveButton(v -> {
                    sendResponseInt(1);
                    panel().hideCancelOrFinishButton();
                })
                .setNegativeButton(v -> {
                    sendResponseInt(0);
                    panel().hideCancelOrFinishButton();
                })
                .setCancelable(false)
                .setOnDismissListener(() -> {
                    panel().hideCancelOrFinishButton();
                    panel().setCurrentDialog(null);
                });
        panel().showCancelOrFinishButton("否");
        dialog.show();
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
        if (data == null || data.remaining() < 8) return;
        int code = data.getInt();
        int positions = data.getInt() & 0x0F;
        // 单一形式兜底（正常路径已在 GameEngine.onSelectPosition 拦截自动应答）
        if (positions == 0x1 || positions == 0x2 || positions == 0x4 || positions == 0x8) {
            sendResponseInt(positions);
            return;
        }
        if (positions == 0) return;
        if (posSelectDialog != null && posSelectDialog.isShowing()) return;
        PosSelectDialog dialog = new PosSelectDialog(activity, imageLoader);
        posSelectDialog = dialog;
        dialog.setTitle(DataManager.get().getStringManager()
                        .getSystemString(561, "选择表示形式"))
                .setOnPositionSelectedListener(pos -> {
                    // 先隐藏弹窗再发送协议：core 随后将卡按所选形式放上场并同步场地状态
                    dialog.dismiss();
                    sendResponseInt(pos);
                });
        dialog.show(code, positions);
    }

    // === 卡组选择 / SIDE ===

    public void showDeckSelectDialog() {
        File deckDir = new File(AppsSettings.get().getResourcePath(), Constants.CORE_DECK_PATH);
        File[] deckFiles = deckDir.exists()
                ? deckDir.listFiles((dir, name) -> name.endsWith(Constants.YDK_FILE_EX))
                : null;

        List<String> deckNames = new ArrayList<>();
        if (deckFiles != null && deckFiles.length > 0) {
            for (File f : deckFiles) {
                deckNames.add(f.getName().replace(Constants.YDK_FILE_EX, ""));
            }
        } else {
            deckNames.add("（暂无卡组文件）");
        }

        final File[] finalDeckFiles = deckFiles;
        YesOrNoDialog dialog = new YesOrNoDialog(activity);
        dialog.setTitle("选择卡组");
        View contentView = inflateSelectLayout();
        dialog.setContentView(contentView);
        LinearLayout layoutOptions = contentView.findViewById(getResId("layout_options", "id"));

        for (int i = 0; i < deckNames.size(); i++) {
            Button btn = new Button(activity);
            btn.setText(deckNames.get(i));
            btn.setTextColor(0xFFFFFFFF);
            btn.setBackgroundColor(0xFF335577);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = 4;
            btn.setLayoutParams(lp);
            final int pos = i;
            btn.setOnClickListener(v -> {
                if (finalDeckFiles != null && pos < finalDeckFiles.length) {
                    loadAndSendDeck(finalDeckFiles[pos]);
                }
                dialog.dismiss();
            });
            layoutOptions.addView(btn);
        }

        dialog.setType(YesOrNoDialog.TYPE_MESSAGE)
                .setPositiveButtonText("取消")
                .setPositiveButton(v -> engine().disconnect())
                .setCancelable(false);
        dialog.show();
    }

    private void loadAndSendDeck(File ydkFile) {
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

    public void showSideSelectDialog() {
        activity.showHintMessage("副卡组替换 - 请在大厅中选择卡组");
    }

    // === 连锁 ===

    public void showChainSelectDialog(ByteBuffer data) {
        if (data == null || data.remaining() < 2) {
            sendResponseInt(-1);
            return;
        }
        int count = data.get() & 0xFF;
        int specount = data.get() & 0xFF;
        if (data.remaining() < 8) {
            sendResponseInt(-1);
            return;
        }
        int hint0 = data.getInt();
        int hint1 = data.getInt();

        List<String> chainOptions = new ArrayList<>();
        List<Integer> chainFlags = new ArrayList<>();
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

            String cardName = activity.getCardDisplayName(code);
            // 连锁描述同样可能为卡片脚本提示文字（卡号*16+n），统一走 getDesc
            String descStr = desc > 0 ? DataManager.get().getDesc(desc, "效果") : "效果";
            chainOptions.add(cardName + " - " + descStr);
            chainFlags.add(flag);
        }

        if (chainOptions.isEmpty()) {
            sendResponseInt(-1);
            return;
        }

        boolean hasForced = false;
        for (int f : chainFlags) {
            if ((f & 0x100) != 0) {
                hasForced = true;
                break;
            }
        }

        AppsSettings settings = AppsSettings.get();
        // 对齐 gframe duelclient.cpp：chkAutoChain && chain_forced && !(always_chain || chain_when_avail)
        // → 自动发动首个强制连锁；勾选 chkDefaultShowChain（决斗开始时总是连锁）后不再自动应答
        boolean autoChain = settings.getIntSettings("chkAutoChain", 0) == 1
                && settings.getIntSettings("chkDefaultShowChain", 0) == 0;
        if (hasForced && autoChain) {
            for (int i = 0; i < chainFlags.size(); i++) {
                if ((chainFlags.get(i) & 0x100) != 0) {
                    sendResponseInt(i);
                    return;
                }
            }
        }

        YesOrNoDialog dialog = new YesOrNoDialog(activity);
        panel().setCurrentDialog(dialog);
        dialog.setTitle("连锁选择");
        View contentView = inflateSelectLayout();
        dialog.setContentView(contentView);
        LinearLayout layoutOptions = contentView.findViewById(getResId("layout_options", "id"));

        for (int i = 0; i < chainOptions.size(); i++) {
            Button btn = new Button(activity);
            btn.setText(chainOptions.get(i));
            btn.setTextColor(0xFFFFFFFF);
            btn.setBackgroundColor((chainFlags.get(i) & 0x100) != 0 ? 0xFFAA3333 : 0xFF335577);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = 4;
            btn.setLayoutParams(lp);
            final int idx = i;
            btn.setOnClickListener(v -> {
                sendResponseInt(idx);
                dialog.dismiss();
            });
            layoutOptions.addView(btn);
        }

        dialog.setType(YesOrNoDialog.TYPE_MESSAGE)
                .setPositiveButtonText("不连锁")
                .setPositiveButton(v -> {
                    panel().hideCancelOrFinishButton();
                    // 对齐 gframe chkWaitChain：勾选时跳过连锁前随机等待 20-40 帧（约 320-640ms）
                    if (settings.getIntSettings("chkWaitChain", 0) == 1) {
                        mainHandler.postDelayed(() -> sendResponseInt(-1), 320 + random.nextInt(321));
                    } else {
                        sendResponseInt(-1);
                    }
                })
                .setCancelable(false)
                .setOnDismissListener(() -> {
                    panel().hideCancelOrFinishButton();
                    panel().setCurrentDialog(null);
                });
        if (!hasForced) {
            panel().showCancelOrFinishButton("不连锁");
        }
        dialog.show();
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
        if (data == null || data.remaining() < 5) {
            sendResponseInt(0);
            return;
        }
        int player = data.get() & 0xFF;
        int counterType = data.getShort() & 0xFFFF;
        int count = data.get() & 0xFF;
        int descId = data.remaining() >= 4 ? data.getInt() : 0;

        YesOrNoDialog dialog = new YesOrNoDialog(activity);
        dialog.setTitle("选择指示物数量");
        View contentView = inflateSelectLayout();
        dialog.setContentView(contentView);
        LinearLayout layoutOptions = contentView.findViewById(getResId("layout_options", "id"));

        for (int i = 1; i <= count; i++) {
            Button btn = new Button(activity);
            btn.setText(String.valueOf(i));
            btn.setTextColor(0xFFFFFFFF);
            btn.setBackgroundColor(0xFF335577);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = 4;
            btn.setLayoutParams(lp);
            final int val = i;
            btn.setOnClickListener(v -> {
                sendResponseInt(val);
                dialog.dismiss();
            });
            layoutOptions.addView(btn);
        }
        dialog.setCancelable(false);
        dialog.show();
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

    public void showAnnounceRaceDialog() {
        String[] races = {"战士", "魔法师", "炎", "水", "雷", "岩石", "植物", "兽",
                "兽战士", "恐龙", "昆虫", "爬虫", "海龙", "鱼", "机械", "超能",
                "幻神兽", "创造神", "龙"};
        YesOrNoDialog dialog = new YesOrNoDialog(activity);
        dialog.setTitle("选择种族");
        View contentView = inflateSelectLayout();
        dialog.setContentView(contentView);
        LinearLayout layoutOptions = contentView.findViewById(getResId("layout_options", "id"));

        for (int i = 0; i < races.length; i++) {
            Button btn = new Button(activity);
            btn.setText(races[i]);
            btn.setTextColor(0xFFFFFFFF);
            btn.setBackgroundColor(0xFF335577);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = 4;
            btn.setLayoutParams(lp);
            final int raceBit = 1 << i;
            btn.setOnClickListener(v -> {
                sendResponseInt(raceBit);
                dialog.dismiss();
            });
            layoutOptions.addView(btn);
        }
        dialog.setCancelable(false);
        dialog.show();
    }

    public void showAnnounceAttribDialog() {
        String[] attribs = {"光", "暗", "水", "炎", "地", "风", "神"};
        YesOrNoDialog dialog = new YesOrNoDialog(activity);
        dialog.setTitle("选择属性");
        View contentView = inflateSelectLayout();
        dialog.setContentView(contentView);
        LinearLayout layoutOptions = contentView.findViewById(getResId("layout_options", "id"));

        for (int i = 0; i < attribs.length; i++) {
            Button btn = new Button(activity);
            btn.setText(attribs[i]);
            btn.setTextColor(0xFFFFFFFF);
            btn.setBackgroundColor(0xFF335577);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = 4;
            btn.setLayoutParams(lp);
            final int attrBit = 1 << i;
            btn.setOnClickListener(v -> {
                sendResponseInt(attrBit);
                dialog.dismiss();
            });
            layoutOptions.addView(btn);
        }
        dialog.setCancelable(false);
        dialog.show();
    }

    public void showAnnounceCardDialog(ByteBuffer data) {
        YesOrNoDialog dialog = new YesOrNoDialog(activity);
        dialog.setTitle("宣言卡片");

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (10 * activity.getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);

        EditText input = new EditText(activity);
        input.setHint("输入卡片名称搜索...");
        input.setTextColor(0xFFFFFFFF);
        input.setHintTextColor(0xFF888888);
        input.setSingleLine(true);
        root.addView(input);

        LinearLayout resultLayout = new LinearLayout(activity);
        resultLayout.setOrientation(LinearLayout.VERTICAL);
        root.addView(resultLayout);

        final int[] selectedCode = {0};
        final TextView tvSelected = new TextView(activity);
        tvSelected.setTextColor(0xFFFFFF00);
        tvSelected.setTextSize(13);
        tvSelected.setText("未选择卡片");
        root.addView(tvSelected);

        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                resultLayout.removeAllViews();
                String query = s.toString().trim();
                if (query.length() < 2) return;
                SparseArray<Card> allCards = DataManager.get().getCardManager().getAllCards();
                List<Card> results = new ArrayList<>();
                for (int i = 0; i < allCards.size(); i++) {
                    Card c = allCards.valueAt(i);
                    if (c.containsName(query)) {
                        results.add(c);
                        if (results.size() >= 10) break;
                    }
                }
                int limit = Math.min(results.size(), 10);
                for (int i = 0; i < limit; i++) {
                    Card c = results.get(i);
                    Button btn = new Button(activity);
                    btn.setText(c.Name + " [" + c.Code + "]");
                    btn.setTextColor(0xFFFFFFFF);
                    btn.setBackgroundColor(0xFF335577);
                    btn.setTextSize(12);
                    btn.setSingleLine(true);
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
                    lp.bottomMargin = 2;
                    btn.setLayoutParams(lp);
                    final int code = c.Code;
                    btn.setOnClickListener(v -> {
                        selectedCode[0] = code;
                        tvSelected.setText("已选择: " + c.Name);
                    });
                    resultLayout.addView(btn);
                }
            }
        });

        ScrollView scrollView = new ScrollView(activity);
        scrollView.addView(root);
        dialog.setContentView(scrollView);
        dialog.setType(YesOrNoDialog.TYPE_YES_NO)
                .setPositiveButtonText("确认")
                .setNegativeButtonText("取消")
                .setPositiveButton(v -> {
                    if (selectedCode[0] > 0) {
                        sendResponseInt(selectedCode[0]);
                    }
                })
                .setNegativeButton(v -> sendResponseInt(0))
                .setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
                .setCancelable(false);
        dialog.show();
    }

    public void showAnnounceNumberDialog(ByteBuffer data) {
        if (data == null || data.remaining() < 1) {
            sendResponseInt(0);
            return;
        }
        int count = data.get() & 0xFF;
        List<Integer> numbers = new ArrayList<>();
        for (int i = 0; i < count && data.remaining() >= 4; i++) {
            numbers.add(data.getInt());
        }
        if (numbers.isEmpty()) {
            sendResponseInt(0);
            return;
        }

        YesOrNoDialog dialog = new YesOrNoDialog(activity);
        dialog.setTitle("选择数字");
        View contentView = inflateSelectLayout();
        dialog.setContentView(contentView);
        LinearLayout layoutOptions = contentView.findViewById(getResId("layout_options", "id"));

        for (int i = 0; i < numbers.size(); i++) {
            Button btn = new Button(activity);
            btn.setText(String.valueOf(numbers.get(i)));
            btn.setTextColor(0xFFFFFFFF);
            btn.setBackgroundColor(0xFF335577);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = 4;
            btn.setLayoutParams(lp);
            final int val = numbers.get(i);
            btn.setOnClickListener(v -> {
                sendResponseInt(val);
                dialog.dismiss();
            });
            layoutOptions.addView(btn);
        }
        dialog.setCancelable(false);
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
        // duelclient.cpp L2519-2560：player skip_panel count + n×[code4 ctrl1 loc1 seq1]
        // 纯展示：OK 仅关闭（无响应数据），对齐 C++ BUTTON_CARD_SEL_OK 的 actionSignal.Set()
        if (data == null) {
            panel().hideCancelOrFinishButton();
            return;
        }
        int count = data.remaining() / 7;
        List<CardDisplayDialog.CardItem> items = new ArrayList<>();
        for (int i = 0; i < count && data.remaining() >= 7; i++) {
            int code = data.getInt();
            int ctrl = data.get() & 0xFF;
            int loc = data.get() & 0xFF;
            int seq = data.get() & 0xFF;
            items.add(new CardDisplayDialog.CardItem(code, ctrl, loc, seq, 0));
        }
        if (items.isEmpty()) {
            panel().hideCancelOrFinishButton();
            return;
        }
        CardDisplayDialog dialog = new CardDisplayDialog(activity, imageLoader);
        panel().setCardDisplayDialog(dialog);
        dialog.setTitle("确认 " + items.size() + " 张卡片")
                .setCards(items)
                .setCardClickListener(this::showCardInfoFromItem)
                .setOnDismissListener(() -> {
                    panel().hideCancelOrFinishButton();
                    panel().setCardDisplayDialog(null);
                })
                .show();
    }

    public void showCardInfoFromItem(CardDisplayDialog.CardItem item) {
        GameField.ClientCard card = new GameField.ClientCard();
        card.code = item.code;
        card.controler = (item.controler == engine().localPlayer(0)) ? 0 : 1;
        card.location = item.location;
        card.sequence = item.sequence;
        card.position = 0x1;
        activity.showCardInfoPanel(card);
    }

    // === 决斗结果 / 结束 ===

    public void showResultDialog(String result) {
        YesOrNoDialog dialog = new YesOrNoDialog(activity);
        dialog.setTitle("决斗结果")
                .setMessage(result)
                .setPositiveButton(v -> activity.finish())
                .setCancelable(false);
        dialog.show();
    }

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
        panel().dismissOpenDialogs();
    }

    // === 通用列表对话框 ===

    public interface OnItemPickedListener {
        void onPicked(int which);
    }

    public void showListDialog(String title, String[] items, OnItemPickedListener listener) {
        YesOrNoDialog dialog = new YesOrNoDialog(activity);
        dialog.setTitle(title);
        View contentView = inflateSelectLayout();
        dialog.setContentView(contentView);
        LinearLayout layoutOptions = contentView.findViewById(getResId("layout_options", "id"));
        for (int i = 0; i < items.length; i++) {
            Button btn = new Button(activity);
            btn.setText(items[i]);
            btn.setTextColor(0xFFFFFFFF);
            btn.setBackgroundColor(0xFF006688);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = 8;
            btn.setLayoutParams(lp);
            final int idx = i;
            btn.setOnClickListener(v -> {
                if (listener != null) listener.onPicked(idx);
                dialog.dismiss();
            });
            layoutOptions.addView(btn);
        }
        dialog.setCancelable(false);
        dialog.show();
    }
}