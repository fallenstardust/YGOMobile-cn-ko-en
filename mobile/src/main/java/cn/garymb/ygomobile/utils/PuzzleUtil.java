package cn.garymb.ygomobile.utils;

import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 残局工具类
 * 负责读取和解析残局lua文件
 */
public class PuzzleUtil {
    private static final String TAG = "PuzzleUtil";

    /**
     * 残局信息数据类
     */
    public static class PuzzleInfo {
        public String fileName;      // 文件名（不含扩展名）
        public String filePath;      // 完整路径
        public String description;   // 从lua文件中读取的描述文字

        public PuzzleInfo(String fileName, String filePath, String description) {
            this.fileName = fileName;
            this.filePath = filePath;
            this.description = description;
        }

        @Override
        public String toString() {
            return fileName;
        }
    }

    /**
     * 读取指定目录下的所有残局lua文件
     *
     * @param singleDir 残局文件夹路径
     * @return 残局信息列表，按文件名排序
     */
    public static List<PuzzleInfo> loadPuzzleFiles(File singleDir) {
        List<PuzzleInfo> puzzleList = new ArrayList<>();

        if (!singleDir.exists() || !singleDir.isDirectory()) {
            Log.w(TAG, "Single mode directory not found: " + singleDir.getAbsolutePath());
            return puzzleList;
        }

        File[] luaFiles = singleDir.listFiles((dir, name) -> name.endsWith(".lua"));
        if (luaFiles == null || luaFiles.length == 0) {
            Log.w(TAG, "No puzzle files found in: " + singleDir.getAbsolutePath());
            return puzzleList;
        }

        for (File file : luaFiles) {
            String fileName = file.getName();
            String displayName = fileName.replace(".lua", "");
            String description = readLuaDescription(file);
            
            PuzzleInfo puzzle = new PuzzleInfo(displayName, file.getAbsolutePath(), description);
            puzzleList.add(puzzle);
        }

        // 按文件名排序
        Collections.sort(puzzleList, (a, b) -> a.fileName.compareToIgnoreCase(b.fileName));

        Log.i(TAG, "Loaded " + puzzleList.size() + " puzzle files");
        return puzzleList;
    }

    /**
     * 从lua文件中读取--[[message ... ]]中的描述文字，保留换行
     *
     * @param luaFile lua文件
     * @return 描述文字，如果未找到则返回空字符串
     */
    public static String readLuaDescription(File luaFile) {
        StringBuilder message = new StringBuilder();
        boolean inMessage = false;

        try (BufferedReader reader = new BufferedReader(new FileReader(luaFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // 查找 --[[message 开头
                if (line.startsWith("--[[message")) {
                    // 检查是否在同一行结束
                    if (line.length() <= 13) {
                        // --[[message]] 或 --[[message 单独一行
                        inMessage = true;
                        continue;
                    } else {
                        // --[[message xxxxx]] 在同一行
                        int end = line.indexOf("]]", 11);
                        if (end > 11) {
                            message.append(line.substring(12, end).trim());
                            break;
                        } else {
                            // 只有开始标记，没有结束标记
                            inMessage = true;
                            message.append(line.substring(12).trim());
                            continue;
                        }
                    }
                }

                // 在message块内
                if (inMessage) {
                    // 查找结束标记 ]]
                    if (line.startsWith("]]")) {
                        break;
                    }
                    // 保留每一行的内容，并在末尾添加换行符
                    if (message.length() > 0) {
                        message.append("\n");
                    }
                    message.append(line.trim());
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Failed to read lua description: " + luaFile.getName(), e);
        }

        return message.toString().trim();
    }
}