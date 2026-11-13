package cn.garymb.ygomobile.ui.dialogs.lobby;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.adapters.SimpleListAdapter;
import cn.garymb.ygomobile.utils.BotUtil;
import cn.garymb.ygomobile.utils.DraggablePopupHelper;
import ocgcore.CardManager;
import ocgcore.DataManager;
import ocgcore.data.Card;
import ocgcore.data.LimitList;
import ocgcore.enums.LimitType;

/** 大厅 “/ai” 人机卡组选择弹窗：按房间禁卡表过滤人机卡组，显示 bot.conf 感叹号行的人机名，确定后回调去 AI_ 前缀的卡组名。 */
class AiDeckSelectDialog {

    interface OnDeckSelectedListener {
        void onDeckSelected(String deckName);
    }

    /** 人机卡组选项：displayName 为 bot.conf 感叹号行的人机名（随资料语言），deckName 为 /ai 命令实际发送的卡组名。 */
    static final class AiDeckOption {
        final String displayName;
        final String deckName;

        AiDeckOption(String displayName, String deckName) {
            this.displayName = displayName;
            this.deckName = deckName;
        }
    }

    private static final int DIALOG_WIDTH_DP = 240;
    private static final int DIALOG_HEIGHT_DP = 300;
    private static final String AI_DECK_PREFIX = "AI_";
    private static final Pattern BOT_DECK_PATTERN = Pattern.compile("Deck=('([^']*)'|(\\S+))");
    /** 卡组名尾部罗马数字归一表（Rank V ↔ Rank5、Level VIII ↔ Level8），与 Decks 文件名对齐。 */
    private static final String[][] ROMAN_NUMERALS = {
            {"viii", "8"}, {"vii", "7"}, {"iii", "3"}, {"ii", "2"},
            {"iv", "4"}, {"vi", "6"}, {"v", "5"}, {"i", "1"}
    };

    private final Context context;
    private PopupWindow popupWindow;
    private DraggablePopupHelper draggableHelper;
    private SimpleListAdapter adapter;
    private final List<AiDeckOption> options = new ArrayList<>();
    private OnDeckSelectedListener listener;
    private int selectedPosition = -1;

    AiDeckSelectDialog(Context context) {
        this.context = context;
    }

    void setOnDeckSelectedListener(OnDeckSelectedListener listener) {
        this.listener = listener;
    }

    void show(View anchorView, List<AiDeckOption> deckOptions, String banlistName) {
        dismiss();
        options.clear();
        if (deckOptions != null) options.addAll(deckOptions);
        selectedPosition = -1;

        View contentView = LayoutInflater.from(context).inflate(R.layout.popup_window_ai_deck_select, null);
        TextView tvTitle = contentView.findViewById(R.id.tv_ai_deck_title);
        // 标题按房间禁卡表动态显示：符合<卡组表名>的人机列表（N/A 表示无禁限）
        tvTitle.setText(context.getString(R.string.ai_deck_select_title,
                banlistName == null ? "N/A" : banlistName));
        ListView listView = contentView.findViewById(R.id.lv_ai_deck_list);
        Button btnOk = contentView.findViewById(R.id.btn_ai_deck_ok);

        List<String> displayNames = new ArrayList<>(options.size());
        for (AiDeckOption option : options) displayNames.add(option.displayName);
        adapter = new SimpleListAdapter(context);
        adapter.set(displayNames);
        adapter.setSelectedPosition(-1);
        listView.setAdapter(adapter);
        listView.setOnItemClickListener((parent, view, position, id) -> {
            selectedPosition = position;
            adapter.setSelectedPosition(position);
            btnOk.setEnabled(true);
        });
        btnOk.setEnabled(false);
        btnOk.setOnClickListener(v -> {
            if (selectedPosition < 0 || selectedPosition >= options.size()) return;
            String deckName = options.get(selectedPosition).deckName;
            dismiss();
            if (listener != null) listener.onDeckSelected(deckName);
        });
        contentView.findViewById(R.id.btn_ai_deck_cancel).setOnClickListener(v -> dismiss());

        float density = context.getResources().getDisplayMetrics().density;
        int width = (int) (DIALOG_WIDTH_DP * density);
        int height = (int) (DIALOG_HEIGHT_DP * density);
        popupWindow = new PopupWindow(contentView, width, height, true);
        popupWindow.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popupWindow.setOutsideTouchable(false);
        popupWindow.setFocusable(false);
        popupWindow.setAnimationStyle(R.style.PopupCenterAnimation);

        draggableHelper = new DraggablePopupHelper(context, "ai_deck_select_dialog");
        draggableHelper.setupDraggablePopup(popupWindow, contentView, width, height);
        draggableHelper.showPopup(popupWindow, anchorView, Gravity.CENTER, 0, 0);
    }

    void dismiss() {
        if (popupWindow != null && popupWindow.isShowing()) {
            popupWindow.dismiss();
        }
        popupWindow = null;
    }

    /** 扫描人机卡组目录，返回符合禁卡表的卡组选项（显示名取 bot.conf 感叹号行，无对应条目时回退卡组名）。 */
    static List<AiDeckOption> loadCompliantDecks(File aiDeckDir, File botConf, LimitList limitList) {
        List<AiDeckOption> result = new ArrayList<>();
        Map<String, String> botDisplayNames = loadBotDeckDisplayNames(botConf);
        File[] files = aiDeckDir != null ? aiDeckDir.listFiles((dir, name) ->
                name.toLowerCase(Locale.ROOT).endsWith(Constants.YDK_FILE_EX)) : null;
        if (files == null) return result;
        for (File file : files) {
            String fileName = file.getName();
            String baseName = fileName.substring(0, fileName.length() - Constants.YDK_FILE_EX.length());
            String deckName = baseName.startsWith(AI_DECK_PREFIX)
                    ? baseName.substring(AI_DECK_PREFIX.length()) : baseName;
            if (deckName.isEmpty()) continue;
            if (limitList != null && !isDeckCompliant(file, limitList)) continue;
            String displayName = botDisplayNames.get(normalizeDeckKey(deckName));
            if (displayName == null || displayName.isEmpty()) displayName = deckName;
            result.add(new AiDeckOption(displayName, deckName));
        }
        result.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.displayName, b.displayName));
        return result;
    }

    /** 从运行时 bot.conf（随资料语言复制）建立“规范化卡组名 → 人机显示名”映射。 */
    private static Map<String, String> loadBotDeckDisplayNames(File botConf) {
        Map<String, String> displayNames = new HashMap<>();
        if (botConf == null || !botConf.isFile()) return displayNames;
        for (BotUtil.BotInfo bot : BotUtil.parseBotConfig(botConf)) {
            String deckName = extractDeckName(bot.command);
            if (deckName == null || deckName.isEmpty()
                    || bot.displayName == null || bot.displayName.isEmpty()) continue;
            String key = normalizeDeckKey(deckName);
            if (!key.isEmpty() && !displayNames.containsKey(key)) {
                displayNames.put(key, bot.displayName);
            }
        }
        return displayNames;
    }

    /** 从 bot.conf 命令行（如 Name=谜之剑士LV4 Deck=Dragunity Dialog=...）取 Deck 值，支持单引号含空格。 */
    private static String extractDeckName(String command) {
        if (command == null) return null;
        Matcher matcher = BOT_DECK_PATTERN.matcher(command);
        if (!matcher.find()) return null;
        return matcher.group(2) != null ? matcher.group(2) : matcher.group(3);
    }

    /** 卡组名规范化：去非字母数字并小写，尾部罗马数字转阿拉伯数字（Rank V→rank5、Level VIII→level8）。 */
    private static String normalizeDeckKey(String name) {
        String key = name.toLowerCase(Locale.ROOT).replaceAll("[^0-9a-z]", "");
        for (String[] roman : ROMAN_NUMERALS) {
            String token = roman[0];
            if (key.endsWith(token) && key.length() > token.length()
                    && Character.isLetter(key.charAt(key.length() - token.length() - 1))) {
                key = key.substring(0, key.length() - token.length()) + roman[1];
                break;
            }
        }
        return key;
    }

    /** 卡组是否满足禁卡表：按主/额外/副卡组合并计数，逐卡检查禁/限/准限张数。 */
    private static boolean isDeckCompliant(File ydkFile, LimitList limitList) {
        CardManager cardManager = DataManager.get().getCardManager();
        // 卡数据尚未就绪时不作为不符合禁卡表的依据
        if (cardManager == null) return true;
        Map<Integer, Integer> counts = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(ydkFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) continue;
                int code;
                try {
                    code = Integer.parseInt(line);
                } catch (NumberFormatException e) {
                    continue;
                }
                Card card = cardManager.getCard(code);
                // 卡数据未收录时不作为不符合禁卡表的依据
                if (card == null || card.Code == 0) continue;
                int count = counts.merge(card.getGameCode(), 1, Integer::sum);
                if (count > maxAllowed(card, limitList)) return false;
            }
        } catch (Exception e) {
            return false;
        }
        return true;
    }

    private static int maxAllowed(Card card, LimitList limitList) {
        if (limitList.check(card, LimitType.Forbidden)) return 0;
        if (limitList.check(card, LimitType.Limit)) return 1;
        if (limitList.check(card, LimitType.SemiLimit)) return 2;
        return Constants.CARD_MAX_COUNT;
    }
}
