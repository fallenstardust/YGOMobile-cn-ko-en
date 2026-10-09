package cn.garymb.ygomobile.utils;

import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

/**
 * 人机对战工具类
 * 负责解析 bot.conf 配置文件
 */
public class BotUtil {
    private static final String TAG = "BotUtil";

    /**
     * 人机信息数据类
     */
    public static class BotInfo {
        public String displayName;              // 显示名称（!后面的内容）
        public String name;                     // AI内部名称
        public String command;                  // 命令字符串
        public String description;              // 描述
        public String flags;                    // 标志
        public int aiLevel;                     // AI等级
        public boolean supportsDeckSelection;   // 是否支持选择卡组

        @Override
        public String toString() {
            return displayName != null ? displayName : (name != null ? name : "Unknown");
        }
    }

    /**
     * 解析 bot.conf 配置文件
     *
     * @param configFile bot.conf 文件
     * @return AI列表
     */
    public static List<BotInfo> parseBotConfig(File configFile) {
        List<BotInfo> botList = new ArrayList<>();

        if (!configFile.exists()) {
            Log.w(TAG, "Bot config file not found: " + configFile.getAbsolutePath());
            return botList;
        }

        try (BufferedReader reader = new BufferedReader(new FileReader(configFile))) {
            String line;
            BotInfo currentBot = null;

            while ((line = reader.readLine()) != null) {
                line = line.trim();

                // 跳过空行和注释
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                // AI名称行
                if (line.startsWith("!")) {
                    if (currentBot != null) {
                        botList.add(currentBot);
                    }
                    currentBot = new BotInfo();
                    currentBot.displayName = line.substring(1).trim();
                    continue;
                }

                if (currentBot == null) continue;

                // 命令行（包含=的行）
                if (line.contains("=")) {
                    currentBot.command = line;
                    // 尝试从命令中提取name
                    if (line.startsWith("Name=")) {
                        currentBot.name = line.substring(5).split("\\s+")[0];
                    }
                    continue;
                }

                // 描述行
                if (currentBot.description == null || currentBot.description.isEmpty()) {
                    currentBot.description = line;
                    continue;
                }

                // 标志行
                if (line.contains("SUPPORT_") || line.contains("SELECT_DECKFILE") ||
                        line.contains("AI_LV")) {
                    currentBot.flags = line;
                    currentBot.supportsDeckSelection = line.contains("SELECT_DECKFILE");

                    // 提取AI等级
                    if (line.contains("AI_LV1")) currentBot.aiLevel = 1;
                    else if (line.contains("AI_LV2")) currentBot.aiLevel = 2;
                    else if (line.contains("AI_LV3")) currentBot.aiLevel = 3;
                    else if (line.contains("AI_LV4")) currentBot.aiLevel = 4;
                    else if (line.contains("AI_WILD")) currentBot.aiLevel = 5;
                    else if (line.contains("AI_ANTI_META")) currentBot.aiLevel = 6;
                }
            }

            // 添加最后一个AI
            if (currentBot != null) {
                botList.add(currentBot);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to parse bot config", e);
        }

        Log.i(TAG, "Loaded " + botList.size() + " bots from config");
        return botList;
    }
}