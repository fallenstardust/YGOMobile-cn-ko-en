package ocgcore;

import android.os.Build;
import android.util.Log;
import android.util.SparseArray;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.loader.CardLoader;
import cn.garymb.ygomobile.ui.plus.VUiKit;
import cn.garymb.ygomobile.utils.DeckUtil;
import cn.garymb.ygomobile.utils.IOUtils;
import ocgcore.data.Card;

public class PackManager implements Closeable {
    private static final String TAG = PackManager.class.getSimpleName();
    private static final PackManager sPackManager = new PackManager();
    private final List<Map.Entry<String, List<Integer>>> packList = new ArrayList<>();
    /**
     * 卡号 → 最早发售日期。日期取自卡包 ydk 的文件名前缀（形如 “2024-04-01 1205.ydk”），
     * 同一张卡在多个包复刻时只留最早的那次；
     * “2024-misc.ydk”这类整年零散包只知道年份，按 {@code yyyy0001} 编码（月份位为 0）。
     * 不在任何卡包里的卡没有记录（查询时不保留）。
     */
    private final Map<Integer, Integer> cardReleaseDates = new ConcurrentHashMap<>();
    /**
     * 全部卡包的发售日期节点（yyyyMMdd，升序去重）：搜索结果的时间轴就按这些节点吸附滑动，
     * 首尾节点即时间轴跨度。只知年份的包按当年 1 月 1 日入节点。
     */
    private final NavigableSet<Integer> releaseDateNodes = new ConcurrentSkipListSet<>();
    /**
     * 发售日期（yyyyMMdd，已归一）→ 当天发售的卡包名（ydk 文件名去掉日期前缀）。
     * 日历里可选日期下方的小字用；同一天有多个包就按顺序存多个
     */
    private final Map<Integer, List<String>> packNamesByDate = new ConcurrentHashMap<>();

    public static PackManager get() {
        return sPackManager;
    }

    public PackManager() {
    }

    @Override
    public void close() {
        // 如果有需要清理的资源，在这里处理
        packList.clear();
        cardReleaseDates.clear();
        releaseDateNodes.clear();
        packNamesByDate.clear();
    }

    public boolean load() {
        packList.clear();
        cardReleaseDates.clear();
        releaseDateNodes.clear();
        packNamesByDate.clear();
        boolean rs1 = loadFile(AppsSettings.get().getResourcePath() + "/" + Constants.CORE_PACK_PATH);
        boolean rs2 = loadFile(AppsSettings.get().getExpansionsPath() + "/" + Constants.CORE_PACK_PATH);
        // 卡包 ydk 不再解压到 cacheDeck：就地读 expansions 下 zip/ypk 里的 pack/*.ydk
        boolean rs3 = loadArchivePackYdks();
        return rs1 && rs2 && rs3;
    }

    /**
     * 从卡包档案（zip/ypk）里就地读 pack/*.ydk 建立包名→卡号表。
     * <p>
     * 旧实现读的是解压到 cacheDeck 的那批副本，而副本只增不减：每更替一次压缩包就多一批
     * 没人认领的过时 ydk，既占空间又会让包名表混进旧包。现在既不生成也不再依赖。
     *
     * @return 至少读到一个包时为 true（与旧版“目录里有文件”同一语义）
     */
    private boolean loadArchivePackYdks() {
        final int[] loaded = new int[1];
        DeckUtil.forEachPackYdk((ydkPath, entryName) -> {
            InputStream in = null;
            try {
                in = DeckUtil.openYdk(ydkPath);
                // 包名键保的是 ydk 文件名，卡详按包名反查的口径不变
                processStream(DeckUtil.ydkFileName(ydkPath), in);
                loaded[0]++;
            } catch (IOException | IllegalArgumentException e) {
                Log.e(TAG, "Error reading pack ydk in archive: " + ydkPath, e);
            } finally {
                IOUtils.close(in);
            }
        });
        if (loaded[0] == 0) {
            Log.w(TAG, "No pack ydk found in expansion archives");
        }
        return loaded[0] > 0;
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
            Log.i(TAG, "pack: Loaded " + list.size() + " files.");
            //Log.i(TAG, toString()); // 在异步任务完成后打印packList
        }).fail((error) -> {
            Log.e(TAG, "Error loading files: " + error.getMessage());
        });

        return true;
    }

    /**
     * 处理文件并提取其中的ID列表
     * 该方法读取指定文件，解析其中的数字ID（跳过以#开头的注释行），
     * 并将有效的ID添加到packList中
     *
     * @param file 需要处理的文件对象
     * @throws IOException 文件读取过程中可能抛出的IO异常
     */
    private void processFile(File file) throws IOException {
        try (FileInputStream inputStream = new FileInputStream(file)) {
            processStream(file.getName(), inputStream);
        }
    }

    /**
     * 从任意 ydk 流（实体文件或压缩包条目）解析卡号列表并归到指定包名下。
     * 关流责任在调用方（本方法只读不关）
     */
    private void processStream(String packFileName, InputStream inputStream) throws IOException {
        List<Integer> ids = new ArrayList<>();
        InputStreamReader in = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
        BufferedReader reader = new BufferedReader(in);
        String line;
        while ((line = reader.readLine()) != null) {
            if (line.startsWith("#")) {
                continue;
            }
            try {
                int id = Integer.parseInt(line.trim());
                ids.add(id);
            } catch (NumberFormatException e) {
                Log.w(TAG, "Skipping invalid line in file " + packFileName + ": " + line);
            }
        }
        // 将解析出的ID列表添加到packList中，如果ID列表不为空
        if (!ids.isEmpty()) {
            packList.add(new AbstractMap.SimpleEntry<>(packFileName, ids));
            int releaseDate = parseReleaseDate(packFileName);
            if (releaseDate > 0) {
                // 这个包的发售日期就是时间轴上的一个滑动节点
                int node = normalizeReleaseDate(releaseDate);
                releaseDateNodes.add(node);
                recordPackName(node, packFileName);
                for (Integer id : ids) {
                    recordReleaseDate(id, releaseDate);
                }
            }
        }
    }

    /**
     * 记下卡号的最早发售日期（同卡多包复刻只留最早的一次）。
     * 加载走的是后台线程（实体目录由 VUiKit.defer 异步读、压缩包同步读），
     * 所以这里既用并发容器也用同步方法兜住两个加载线的同时写入
     */
    private synchronized void recordReleaseDate(int code, int date) {
        Integer old = cardReleaseDates.get(code);
        if (old == null || date < old) {
            cardReleaseDates.put(code, date);
        }
    }

    /** 记下某一天发售的卡包名（同一天多个包按加载顺序排列，重名只留一个） */
    private synchronized void recordPackName(int date, String packFileName) {
        List<String> names = packNamesByDate.get(date);
        if (names == null) {
            names = new ArrayList<>();
            packNamesByDate.put(date, names);
        }
        String name = packDisplayName(packFileName);
        if (!name.isEmpty() && !names.contains(name)) {
            names.add(name);
        }
    }

    /**
     * 卡包显示名：ydk 文件名去掉扩展名与日期前缀（“2024-04-01 1205.ydk” → “1205”），
     * 没有空格分隔的（如 “2024-misc”）原样保留
     */
    private static String packDisplayName(String packFileName) {
        if (packFileName == null) {
            return "";
        }
        String name = packFileName;
        int dot = name.lastIndexOf(Constants.YDK_FILE_EX);
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        if (name.length() > 5 && name.charAt(4) == '-') {
            int space = name.indexOf(' ', 5);
            if (space > 0 && space + 1 < name.length()) {
                return name.substring(space + 1).trim();
            }
        }
        return name;
    }

    /**
     * 某一天发售的卡包名（日历里的日期小字用），同一天多个包返回多个；
     * 没有包的一天返回空表
     */
    public synchronized Map<Integer, List<String>> getPackNamesByDate() {
        Map<Integer, List<String>> copy = new ConcurrentHashMap<>(packNamesByDate.size() * 2);
        for (Map.Entry<Integer, List<String>> entry : packNamesByDate.entrySet()) {
            List<String> names = new ArrayList<>(entry.getValue());
            java.util.Collections.sort(names);
            copy.put(entry.getKey(), names);
        }
        return copy;
    }

    /**
     * 从卡包 ydk 文件名解析发售日期（yyyyMMdd）。
     * <p>
     * 约定格式：{@code yyyy-MM-dd 包名.ydk}；只到月的 {@code yyyy-MM 包名.ydk} 取当月 1 日；
     * 整年零散卡 {@code yyyy-misc.ydk} 只知道年份，按 {@link #encodeReleaseYear(int)} 编码，
     * 过滤时按「整年任意一天在范围内」判定。前缀不是日期的文件返回 0（不参与过滤）。
     */
    private static int parseReleaseDate(String packFileName) {
        if (packFileName == null || packFileName.length() < 7 || packFileName.charAt(4) != '-') {
            return 0;
        }
        try {
            int year = Integer.parseInt(packFileName.substring(0, 4));
            if (year <= 0) {
                return 0;
            }
            if (packFileName.length() >= 10 && packFileName.charAt(7) == '-') {
                int month = Integer.parseInt(packFileName.substring(5, 7));
                int day = Integer.parseInt(packFileName.substring(8, 10));
                if (month >= 1 && month <= 12 && day >= 1 && day <= 31) {
                    return year * 10000 + month * 100 + day;
                }
                return 0;
            }
            if (packFileName.regionMatches(5, "misc", 0, 4)) {
                // xxxx-misc：该文件里的卡都属于这一年，但具体日期未知
                return encodeReleaseYear(year);
            }
            int month = Integer.parseInt(packFileName.substring(5, 7));
            if (month >= 1 && month <= 12) {
                return year * 10000 + month * 100 + 1;
            }
        } catch (NumberFormatException e) {
            Log.w(TAG, "Skip pack date: " + packFileName);
        }
        return 0;
    }

    /** “只知道年份”的发售日期编码：{@code yyyy0001}（月份位为 0，与真实日期区分开） */
    private static int encodeReleaseYear(int year) {
        return year * 10000 + 1;
    }

    private static boolean isYearOnlyRelease(int date) {
        return date > 0 && date % 10000 == 1;
    }

    /** 只知道年份的日期按当年 1 月 1 日归一，用于时间轴节点与跨度端点 */
    private static int normalizeReleaseDate(int date) {
        return isYearOnlyRelease(date) ? date / 10000 * 10000 + 101 : date;
    }

    /**
     * 时间轴的滑动节点：全部卡包的发售日期（yyyyMMdd，升序去重）。
     * <p>
     * xxxx-misc 这类只知道年份的包按当年 1 月 1 日入节点，但过滤仍按整年判定（见
     * {@link #isCardReleasedBetween}）；卡包还没读到时返回空数组。
     */
    public int[] getReleaseDateNodes() {
        int[] nodes = new int[releaseDateNodes.size()];
        int i = 0;
        // 载入线程随时还在往节点集里补新节点，弱一致迭代器可能给出比 size() 更多的元素，
        // 这里按数组容量截断，宁可少一个节点也不越界
        for (Integer date : releaseDateNodes) {
            if (i >= nodes.length) {
                break;
            }
            nodes[i++] = date;
        }
        return i == nodes.length ? nodes : Arrays.copyOf(nodes, i);
    }

    /**
     * 排序用的发售日期：yyyyMMdd，只知年份的包按当年 1 月 1 日归一；
     * 不在任何卡包里的卡返回 0（排序时这类卡放到最后）
     */
    public int getCardReleaseDate(int code) {
        Integer date = cardReleaseDates.get(code);
        return date == null ? 0 : normalizeReleaseDate(date);
    }

    /**
     * 某张卡的发售日期是否落在 [startDate, endDate] 内（均为 yyyyMMdd，两端都含）。
     * <p>
     * xxxx-misc 里的卡只知道年份：只要范围与那一年有交集（滑块扫到该年份的任一天）就保留，
     * 不把它钉死在 1 月 1 日。没有任何卡包记录的卡返回 false（无日期可比）。
     */
    public boolean isCardReleasedBetween(int code, int startDate, int endDate) {
        Integer date = cardReleaseDates.get(code);
        if (date == null) {
            return false;
        }
        if (isYearOnlyRelease(date)) {
            int year = date / 10000;
            return startDate <= year * 10000 + 1231 && endDate >= year * 10000 + 101;
        }
        return date >= startDate && date <= endDate;
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
     * 根据给定的ID查找其所属的packName下的所有ID。
     *
     * @param id 给定的ID。
     * @return 包含给定ID所属的packName下的所有ID的列表。
     */
    public List<Integer> getAllIdsById(Integer id) {
        String packName = findPackNameById(id);
        if (packName == null) {
            Log.w(TAG, "No pack found for ID: " + id);
            return new ArrayList<>();
        }
        return findIdsByPackName(packName);
    }
    /**
     * 从cardLoader查询收藏的卡片
     *
     * @param cardLoader
     * @return 排序后的列表
     */
    public List<Card> getCards(CardLoader cardLoader, Integer code) {
        List<Integer> mList = getAllIdsById(code);
        SparseArray<Card> id = cardLoader.readCards(mList, false);
        List<Card> list = new ArrayList<>();
        if (id != null) {
            for (int i = 0; i < id.size(); i++) {
                list.add(id.valueAt(i));
            }
        }
        return list;
    }
    /**
     * 从cardLoader查询收藏的卡片
     *
     * @param cardLoader
     * @return 排序后的列表
     */
    public List<Card> getCards(CardLoader cardLoader, String packName) {
        List<Integer> mList = findIdsByPackName(packName);
        SparseArray<Card> id = cardLoader.readCards(mList, false);
        List<Card> list = new ArrayList<>();
        if (id != null) {
            for (int i = 0; i < id.size(); i++) {
                list.add(id.valueAt(i));
            }
        }
        return list;
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                sb.append(String.join(", ", entry.getValue().stream().map(String::valueOf).toArray(String[]::new)));
            }
            sb.append("]\n");
        }
        return sb.toString();
    }

}