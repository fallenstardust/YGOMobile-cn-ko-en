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

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.YGOProActivity;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.loader.ImageLoader;
import cn.garymb.ygomobile.render.CardDetailPanel;
import cn.garymb.ygomobile.ui.dialogs.CardDisplayDialog;
import cn.garymb.ygomobile.ui.dialogs.CardSelectDialog;
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

    private RPSDialog handSelectDialog;
    private YesOrNoDialog tpSelectDialog;

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
        if (handSelectDialog != null && handSelectDialog.isShowing()) return;
        RPSDialog dialog = new RPSDialog(activity);
        handSelectDialog = dialog;
        dialog.setTitle("猜拳决定先手")
                .setCancelable(false)
                .setOnResultListener(result -> {
                    engine().sendHandResult(result);
                    dialog.dismiss();
                });
        dialog.show();
    }

    public void showTPSelectDialog() {
        if (tpSelectDialog != null && tpSelectDialog.isShowing()) return;
        YesOrNoDialog dialog = new YesOrNoDialog(activity);
        tpSelectDialog = dialog;
        dialog.setTitle("先攻选择")
                .setMessage("是否选择先攻？")
                .setType(YesOrNoDialog.TYPE_YES_NO)
                .setPositiveButtonText("先攻")
                .setNegativeButtonText("后攻")
                .setPositiveButton(v -> engine().sendTPResult(true))
                .setNegativeButton(v -> engine().sendTPResult(false))
                .setCancelable(false);
        dialog.show();
    }

    // === 是/否 / 选项 / 效果确认 ===

    public void showYesNoDialog(ByteBuffer data) {
        int descId = 0;
        if (data != null && data.remaining() >= 4) {
            descId = data.getInt();
        }
        String desc = descId > 0
                ? DataManager.get().getStringManager().getSystemString(descId, "是否发动效果？")
                : "是否发动效果？";

        YesOrNoDialog dialog = new YesOrNoDialog(activity);
        dialog.setTitle("确认")
                .setMessage(desc)
                .setType(YesOrNoDialog.TYPE_YES_NO)
                .setPositiveButtonText("是")
                .setNegativeButtonText("否")
                .setPositiveButton(v -> {
                    sendResponseInt(1);
                    panel().hideCancelOrFinishButton();
                    dialog.dismiss();
                })
                .setNegativeButton(v -> {
                    sendResponseInt(0);
                    panel().hideCancelOrFinishButton();
                    dialog.dismiss();
                })
                .setCancelable(false)
                .setOnDismissListener(() -> {
                    panel().hideCancelOrFinishButton();
                    panel().setCurrentDialog(null);  // 若需要保留引用可调整
                });
        panel().showCancelOrFinishButton("否");
        dialog.show();
    }

    public void showOptionDialog(ByteBuffer data) {
        if (data == null) return;
        int count = data.get() & 0xFF;
        List<String> options = new ArrayList<>();
        for (int i = 0; i < count && data.remaining() >= 4; i++) {
            int descId = data.getInt();
            String str = DataManager.get().getStringManager().getSystemString(descId, "Option " + (i + 1));
            options.add(str);
        }

        YesOrNoDialog dialog = new YesOrNoDialog(activity);
        dialog.setTitle("请选择");
        View contentView = inflateSelectLayout();
        dialog.setContentView(contentView);
        LinearLayout layoutOptions = contentView.findViewById(getResId("layout_options", "id"));
        for (int i = 0; i < options.size(); i++) {
            Button btn = new Button(activity);
            btn.setText(options.get(i));
            btn.setTextColor(0xFFFFFFFF);
            btn.setBackgroundColor(0xFF006688);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = 8;
            btn.setLayoutParams(lp);
            final int idx = i;
            btn.setOnClickListener(v -> {
                sendResponseInt(idx);
                dialog.dismiss();
            });
            layoutOptions.addView(btn);
        }
        dialog.setCancelable(false);
        dialog.show();
    }

    public void showEffectYnDialog(ByteBuffer data) {
        showYesNoDialog(data);
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

    public void showPositionSelectDialog() {
        showListDialog("选择表示形式", new String[]{
                "表侧攻击表示", "里侧攻击表示",
                "表侧守备表示", "里侧守备表示"
        }, which -> {
            int pos;
            switch (which) {
                case 0:
                    pos = 0x1;
                    break;
                case 1:
                    pos = 0x2;
                    break;
                case 2:
                    pos = 0x4;
                    break;
                case 3:
                    pos = 0x8;
                    break;
                default:
                    pos = 0x1;
                    break;
            }
            sendResponseInt(pos);
        });
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
            String descStr = desc > 0 ? DataManager.get().getStringManager().getSystemString(desc, "效果") : "效果";
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

        if (hasForced) {
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
                    sendResponseInt(-1);
                    panel().hideCancelOrFinishButton();
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
                .setLocalPlayer(engine().getClient().selfType)
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
                .setLocalPlayer(engine().getClient().selfType)
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
                .setLocalPlayer(engine().getClient().selfType)
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
                .setLocalPlayer(engine().getClient().selfType)
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
        card.controler = (item.controler == engine().getClient().selfType) ? 0 : 1;
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

    public void showDuelEndDialog() {
        YesOrNoDialog dialog = new YesOrNoDialog(activity);
        dialog.setTitle("决斗结束")
                .setMessage("本次决斗已结束")
                .setType(YesOrNoDialog.TYPE_YES_NO)
                .setPositiveButtonText("确定")
                .setNegativeButtonText("继续等待")
                .setPositiveButton(v -> activity.finish())
                .setCancelable(false);
        dialog.show();
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