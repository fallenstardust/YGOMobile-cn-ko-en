package ocgcore;

import android.util.Log;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.ui.plus.VUiKit;

public class PackManager implements Closeable {
    private static final String TAG = PackManager.class.getSimpleName();
    private static final PackManager sPackManager = new PackManager();
    private final List<Map.Entry<String, List<Integer>>> packList = new ArrayList<>();

    public static PackManager get() {
        return sPackManager;
    }

    public PackManager() {
    }

    @Override
    public void close() {
        // 如果有需要清理的资源，在这里处理
        packList.clear();
    }

    public boolean load() {
        packList.clear();
        boolean rs1 = loadFile(AppsSettings.get().getResourcePath() + "/" + Constants.CORE_PACK_PATH);
        boolean rs2 = loadFile(AppsSettings.get().getExpansionsPath() + "/" + Constants.CORE_PACK_PATH);
        boolean res3 = loadFile(AppsSettings.get().getCacheDeckDir());
        return rs1 && rs2 && res3;
    }

    public boolean loadFile(String path) {
        if (path == null || path.isEmpty()) {
            Log.e(TAG, "Invalid path provided.");
            return false;
        }
        File[] fileList = new File(path).listFiles();

        if (fileList == null || fileList.length == 0) {
            Log.w(TAG, "No files found in the directory: " + path);
            return false;
        }

        VUiKit.defer().when(() -> {
            for (File packYdk : fileList) {
                if (packYdk.isFile() && packYdk.getName().endsWith(Constants.YDK_FILE_EX)) {
                    try {
                        processFile(packYdk);
                    } catch (IOException e) {
                        Log.e(TAG, "Error processing file: " + packYdk.getAbsolutePath(), e);
                    }
                }
            }
            return packList;
        }).done((list) -> {
            Log.i(TAG, "Loaded " + list.size() + " files.");
            Log.i(TAG, toString()); // 在异步任务完成后打印packList
        }).fail((error) -> {
            Log.e(TAG, "Error loading files: " + error.getMessage());
        });

        return true;
    }

    private void processFile(File file) throws IOException {
        List<Integer> ids = new ArrayList<>();
        try (FileInputStream inputStream = new FileInputStream(file);
             InputStreamReader in = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
             BufferedReader reader = new BufferedReader(in)) {

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#")) {
                    continue;
                }
                try {
                    int id = Integer.parseInt(line.trim());
                    ids.add(id);
                } catch (NumberFormatException e) {
                    Log.w(TAG, "Skipping invalid line in file " + file.getName() + ": " + line);
                }
            }
            if (!ids.isEmpty()) {
                packList.add(new AbstractMap.SimpleEntry<>(file.getName(), ids));
            }
        }
    }

    /**
     * 通过给定的ID在packList中查找对应的String名称。
     *
     * @param id 要查找的ID。
     * @return 如果找到匹配项，则返回对应的文件名；否则返回null。
     */
    public String findPackNameById(Integer id) {
        for (Map.Entry<String, List<Integer>> entry : packList) {
            if (entry.getValue().contains(id)) {
                return entry.getKey().substring(0, entry.getKey().lastIndexOf(Constants.YDK_FILE_EX));
            }
        }
        return null; // 如果没有找到匹配项，则返回null
    }

    /**
     * 通过给定的ID在packList中查找对应的String名称。
     *
     * @param packName 要查找的ID。
     * @return 如果找到匹配项，则返回对应的文件名；否则返回null。
     */
    public List<Integer> findIdsByPackName(String packName) {
        List<Integer> mList = new ArrayList<>();
        for (Map.Entry<String, List<Integer>> entry : packList) {
            if (entry.getKey().contains(packName)) {
                mList.addAll(entry.getValue());
                return mList;
            }
        }
        return null; // 如果没有找到匹配项，则返回null
    }

    /**
     * 将packList的内容转换为字符串表示。
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("PackList content:\n");
        for (int i = 0; i < packList.size(); i++) {
            Map.Entry<String, List<Integer>> entry = packList.get(i);
            sb.append("Entry ").append(i + 1).append(": ").append(entry.getKey()).append(" -> [");
            sb.append(String.join(", ", entry.getValue().stream().map(String::valueOf).toArray(String[]::new)));
            sb.append("]\n");
        }
        return sb.toString();
    }

}