package cn.garymb.ygomobile.utils;

import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 卡组选择器工具类
 * 负责读取本地deck文件夹和windbot/Decks文件夹下的卡组文件
 */
public class DeckSelectorUtil {
    private static final String TAG = "DeckSelectorUtil";

    /**
     * 卡组分类信息
     */
    public static class DeckCategory {
        public String categoryName;      // 分类名称（子文件夹名）
        public List<DeckItem> deckList;  // 该分类下的卡组列表

        public DeckCategory(String categoryName) {
            this.categoryName = categoryName;
            this.deckList = new ArrayList<>();
        }
    }

    /**
     * 卡组项信息
     */
    public static class DeckItem {
        public String deckName;          // 卡组文件名（不含扩展名）
        public String deckPath;          // 完整路径

        public DeckItem(String deckName, String deckPath) {
            this.deckName = deckName;
            this.deckPath = deckPath;
        }

        @Override
        public String toString() {
            return deckName;
        }
    }

    /**
     * 读取指定目录下的所有卡组文件和分类
     *
     * @param rootDir 根目录（如 deck 或 windbot/Decks）
     * @return 卡组分类列表
     */
    public static List<DeckCategory> loadDeckCategories(File rootDir) {
        List<DeckCategory> categories = new ArrayList<>();

        if (!rootDir.exists() || !rootDir.isDirectory()) {
            Log.w(TAG, "Deck directory not found: " + rootDir.getAbsolutePath());
            return categories;
        }

        File[] files = rootDir.listFiles();
        if (files == null || files.length == 0) {
            Log.w(TAG, "No files in deck directory: " + rootDir.getAbsolutePath());
            return categories;
        }

        // 未分类卡组（直接在根目录的ydk文件）
        DeckCategory uncategorized = new DeckCategory("未分类卡组");

        for (File file : files) {
            if (file.isDirectory()) {
                // 子文件夹作为分类
                DeckCategory category = new DeckCategory(file.getName());
                loadDecksFromDirectory(file, category.deckList);

                if (!category.deckList.isEmpty()) {
                    categories.add(category);
                }
            } else if (file.getName().toLowerCase().endsWith(".ydk")) {
                // 根目录的ydk文件归为未分类
                String deckName = file.getName().replace(".ydk", "");
                uncategorized.deckList.add(new DeckItem(deckName, file.getAbsolutePath()));
            }
        }

        // 如果有未分类卡组，添加到列表最前面
        if (!uncategorized.deckList.isEmpty()) {
            Collections.sort(uncategorized.deckList, (a, b) ->
                a.deckName.compareToIgnoreCase(b.deckName));
            categories.add(0, uncategorized);
        }

        // 按分类名排序（除了"未分类卡组"始终在最前）
        if (categories.size() > 1) {
            List<DeckCategory> sorted = new ArrayList<>();
            sorted.add(categories.get(0)); // 保持"未分类卡组"在最前

            List<DeckCategory> rest = categories.subList(1, categories.size());
            rest.sort((a, b) -> a.categoryName.compareToIgnoreCase(b.categoryName));
            sorted.addAll(rest);

            categories = sorted;
        }

        Log.i(TAG, "Loaded " + categories.size() + " deck categories from " + rootDir.getAbsolutePath());
        return categories;
    }

    /**
     * 将指定目录下的ydk文件直接加载为单个分类（不递归子目录），
     * 用于"卡包展示"这类"目录即分类"的场景
     *
     * @param dir          目录
     * @param categoryName 分类显示名
     * @return 卡组分类
     */
    public static DeckCategory loadSingleCategory(File dir, String categoryName) {
        DeckCategory category = new DeckCategory(categoryName);
        loadDecksFromDirectory(dir, category.deckList);
        return category;
    }

    /**
     * 从指定目录加载所有ydk文件
     *
     * @param dir       目录
     * @param deckList  存储结果的列表
     */
    private static void loadDecksFromDirectory(File dir, List<DeckItem> deckList) {
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isFile() && file.getName().toLowerCase().endsWith(".ydk")) {
                String deckName = file.getName().replace(".ydk", "");
                deckList.add(new DeckItem(deckName, file.getAbsolutePath()));
            }
        }

        // 按文件名排序
        deckList.sort((a, b) -> a.deckName.compareToIgnoreCase(b.deckName));
    }
}