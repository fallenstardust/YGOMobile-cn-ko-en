package cn.garymb.ygomobile.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cn.garymb.ygomobile.Constants;
import ocgcore.CardManager;
import ocgcore.DataManager;
import ocgcore.LimitManager;
import ocgcore.data.Card;
import ocgcore.data.LimitList;
import ocgcore.enums.LimitType;

/** 人机卡组工具：按 bot.conf 显示名与房间禁卡表筛选 windbot/Decks 下的人机卡组，供大厅 /ai 列表与主机端随机召唤共用。 */
public final class AiDeckUtil {

    /** 人机卡组选项：displayName 为 bot.conf 感叹号行的人机名，deckName 为空串表示“随机-不指定”。 */
    public static final class AiDeckOption {
        public final String displayName;
        public final String deckName;

        public AiDeckOption(String displayName, String deckName) {
            this.displayName = displayName;
            this.deckName = deckName;
        }
    }

    private static final String AI_DECK_PREFIX = "AI_";
    private static final Pattern BOT_DECK_PATTERN = Pattern.compile("Deck=('([^']*)'|(\\S+))");
    /** 卡组名尾部罗马数字归一表（Rank V ↔ Rank5、Level VIII ↔ Level8），与 Decks 文件名对齐。 */
    private static final String[][] ROMAN_NUMERALS = {
            {"viii", "8"}, {"vii", "7"}, {"iii", "3"}, {"ii", "2"},
            {"iv", "4"}, {"vi", "6"}, {"v", "5"}, {"i", "1"}
    };
    private static final Random RANDOM = new Random();

    private AiDeckUtil() {
    }

    /** 列举 Decks 下在 bot.conf 有感叹号人机名、且符合禁卡表的人机卡组（按显示名排序；无显示名的卡组不返回）。 */
    public static List<AiDeckOption> loadNamedCompliantDecks(File aiDeckDir, File botConf, LimitList limitList) {
        List<AiDeckOption> result = new ArrayList<>();
        Map<String, String> displayNames = loadBotDeckDisplayNames(botConf);
        File[] files = listYdkFiles(aiDeckDir);
        if (files == null) return result;
        for (File file : files) {
            String deckName = toDeckName(file);
            if (deckName.isEmpty()) continue;
            String displayName = displayNames.get(normalizeDeckKey(deckName));
            // 用户要求：bot.conf 中没有感叹号人机名的对应卡组不加入列表
            if (displayName == null || displayName.isEmpty()) continue;
            if (limitList != null && !isDeckCompliant(file, limitList)) continue;
            result.add(new AiDeckOption(displayName, deckName));
        }
        result.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.displayName, b.displayName));
        return result;
    }

    /** 随机挑一个“有 bot.conf 人机名且符合禁卡表”的卡组名（主机端处理纯 /ai 用）；无可用卡组返回 null。 */
    public static String pickRandomDeckName(File aiDeckDir, File botConf, LimitList limitList) {
        List<AiDeckOption> options = loadNamedCompliantDecks(aiDeckDir, botConf, limitList);
        if (options.isEmpty()) return null;
        return options.get(RANDOM.nextInt(options.size())).deckName;
    }

    /** 按 lflist 哈希反查禁限表（常规表优先、其次 Genesys 表）；0/未收录/空表返回 null（无禁限）。 */
    public static LimitList resolveLimitList(int lflist) {
        if (lflist == 0) return null;
        LimitManager lm = DataManager.get().getLimitManager();
        if (lm == null) return null;
        String name = lm.getLimitNameByHash(lflist);
        LimitList list = name != null ? lm.getLimit(name) : null;
        if (list == null) {
            name = lm.getGenesysLimitNameByHash(lflist);
            list = name != null ? lm.getGenesysLimit(name) : null;
        }
        if (list != null && list.getCodeList().isEmpty()) return null;
        return list;
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

    private static File[] listYdkFiles(File aiDeckDir) {
        return aiDeckDir != null ? aiDeckDir.listFiles((dir, name) ->
                name.toLowerCase(Locale.ROOT).endsWith(Constants.YDK_FILE_EX)) : null;
    }

    /** ydk 文件名去掉扩展名与 AI_ 前缀，得到 /ai 命令使用的卡组名。 */
    private static String toDeckName(File file) {
        String fileName = file.getName();
        String baseName = fileName.substring(0, fileName.length() - Constants.YDK_FILE_EX.length());
        return baseName.startsWith(AI_DECK_PREFIX) ? baseName.substring(AI_DECK_PREFIX.length()) : baseName;
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
