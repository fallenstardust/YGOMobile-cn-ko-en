package ocgcore;

import android.content.res.AssetManager;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.GameApplication;
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
     * 卡号 → 最早发售日期（{@code yyyyMMdd}），取自内置 pack.db.sql 总表：这是日期查询的
     * <b>首选来源</b>，逐卡精确到日，解决了 ydk 文件名里 {@code xxxx-misc} 只能知道年份的问题。
     * 同一张卡在多个包复刻时只留最早那次；{@link #cardReleaseDates}（ydk 推的）仅作 sql
     * 未收录卡（如扩展包独有卡）的兜底。
     */
    private final Map<Integer, Integer> sqlCardReleaseDates = new ConcurrentHashMap<>();
    /**
     * 卡号 → 最初那次发售所属的真实卡包名（pack.db.sql 的包名列）：与 {@link #sqlCardReleaseDates}
     * 同一次最早记录写入，供卡详情“发售日期 + 包名”展示。只知年份/未入 sql 的卡无记录。
     */
    private final Map<Integer, String> sqlCardFirstPack = new ConcurrentHashMap<>();
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

    /**
     * pack.db.sql 行尾的发售日字段：{@code ,'yyyy-MM-dd');}。只锁行尾的日期，
     * 不拆中间可能含逗号/转义单引号的包名、稀有度字段。
     */
    private static final Pattern SQL_ROW_DATE =
            Pattern.compile("'(\\d{4})-(\\d{2})-(\\d{2})'\\);\\s*$");
    /** pack.db.sql 行首的卡密字段：{@code INSERT INTO pack VALUES(卡密,}。 */
    private static final Pattern SQL_ROW_ID =
            Pattern.compile("^INSERT\\s+INTO\\s+pack\\s+VALUES\\s*\\(\\s*(\\d+)\\s*,");
    /**
     * pack.db.sql 里的一个单引号字符串字段：{@code '...'}，内部转义的 {@code ''} 归为字段内容。
     * 按行内出现顺序依次是 包号、包名、稀有度、发售日；用它的第 2 个（包名）作日历小字，
     * 不拆含逗号/转义引号的字段。
     */
    private static final Pattern SQL_QUOTED_FIELD =
            Pattern.compile("'((?:[^']|'')*)'");

    public PackManager() {
    }

    @Override
    public void close() {
        // 如果有需要清理的资源，在这里处理
        packList.clear();
        cardReleaseDates.clear();
        sqlCardReleaseDates.clear();
        sqlCardFirstPack.clear();
        releaseDateNodes.clear();
        packNamesByDate.clear();
    }

    public boolean load() {
        packList.clear();
        cardReleaseDates.clear();
        sqlCardReleaseDates.clear();
        sqlCardFirstPack.clear();
        releaseDateNodes.clear();
        packNamesByDate.clear();
        // 先同步读内置 pack.db.sql 建立逐卡精确发售日总表（日期查询的首选来源）
        boolean rsSql = loadPackReleaseSql();
        boolean rs1 = loadFile(AppsSettings.get().getResourcePath() + "/" + Constants.CORE_PACK_PATH);
        boolean rs2 = loadFile(AppsSettings.get().getExpansionsPath() + "/" + Constants.CORE_PACK_PATH);
        // 卡包 ydk 不再解压到 cacheDeck：就地读 expansions 下 zip/ypk 里的 pack/*.ydk
        boolean rs3 = loadArchivePackYdks();
        return rsSql && rs1 && rs2 && rs3;
    }

    /**
     * 读内置 asset {@code data/pack.db.sql}，逐行取（卡密, 日期）建立 {@link #sqlCardReleaseDates}：
     * 同一张卡多行（多包复刻）只留最早那次，并把每个真实发售日并入时间轴节点。
     * <p>
     * 只直读 apk 内的静态总表，不依赖 ydk 那套拷贝/档案机制；sql 不存在或读失败时记录日志
     * 并返回 false（此时日期全退回 ydk 推的 {@link #cardReleaseDates}）。
     */
    private boolean loadPackReleaseSql() {
        GameApplication app = GameApplication.get();
        if (app == null) {
            Log.w(TAG, "pack.db.sql skipped: no application context");
            return false;
        }
        AssetManager assets = app.getAssets();
        String assetPath = Constants.ASSETS_PATH + Constants.CORE_PACK_RELEASE_SQL;
        int rows = 0;
        try (InputStream is = assets.open(assetPath);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                int date = parseSqlRow(line);
                if (date <= 0) {
                    continue;
                }
                Matcher idM = SQL_ROW_ID.matcher(line);
                if (!idM.find()) {
                    continue;
                }
                int code;
                try {
                    code = (int) Long.parseLong(idM.group(1));
                } catch (NumberFormatException e) {
                    continue;
                }
                String packName = extractQuotedField(line, 1);
                recordSqlReleaseDate(code, date, packName);
                releaseDateNodes.add(date);
                recordSqlPackName(date, packName);
                rows++;
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to read pack.db.sql from assets: " + assetPath, e);
            return false;
        }
        if (rows == 0) {
            Log.w(TAG, "pack.db.sql parsed but matched no card rows");
        } else {
            Log.i(TAG, "pack.db.sql: indexed " + sqlCardReleaseDates.size() + " cards from " + rows + " rows.");
        }
        return rows > 0;
    }

    /**
     * 从 pack.db.sql 的一行 INSERT 里取行尾的 {@code 'yyyy-MM-dd'} 并编为 {@code yyyyMMdd}；
     * 非 INSERT 行或日期字段不合格式时返回 0。
     */
    private static int parseSqlRow(String line) {
        Matcher m = SQL_ROW_DATE.matcher(line);
        if (!m.find()) {
            return 0;
        }
        try {
            int year = Integer.parseInt(m.group(1));
            int month = Integer.parseInt(m.group(2));
            int day = Integer.parseInt(m.group(3));
            if (year <= 0 || month < 1 || month > 12 || day < 1 || day > 31) {
                return 0;
            }
            return year * 10000 + month * 100 + day;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * 记下 sql 总表里卡号的最早发售日期（同卡多行只留最早），并把写下该最早日那次的包名
     * 一并记入 {@link #sqlCardFirstPack}。加载在本方法内同步完成，但仍用并发容器 + 同步写，
     * 避免与后续 ydk 加载线、重加载并发时丢更新
     */
    private synchronized void recordSqlReleaseDate(int code, int date, String packName) {
        Integer old = sqlCardReleaseDates.get(code);
        if (old == null || date < old) {
            sqlCardReleaseDates.put(code, date);
            if (packName != null && !packName.isEmpty()) {
                sqlCardFirstPack.put(code, packName);
            }
        }
    }

    /**
     * 记下某一天发售的真实卡包名（日历里的日期小字用），取自 pack.db.sql 的包名列：
     * 同一天多个包按出现顺序存多个，重名只留一个。空名不入表。
     */
    private synchronized void recordSqlPackName(int date, String packName) {
        if (packName == null || packName.isEmpty()) {
            return;
        }
        List<String> names = packNamesByDate.get(date);
        if (names == null) {
            names = new ArrayList<>();
            packNamesByDate.put(date, names);
        }
        if (!names.contains(packName)) {
            names.add(packName);
        }
    }

    /**
     * 取 pack.db.sql 一行里第 {@code index} 个单引号字段的值（{@code ''} 还原为 {@code '}）；
     * 不足该序号时返回 null。
     */
    private static String extractQuotedField(String line, int index) {
        Matcher m = SQL_QUOTED_FIELD.matcher(line);
        int seen = 0;
        while (m.find()) {
            if (seen == index) {
                return m.group(1).replace("''", "'");
            }
            seen++;
        }
        return null;
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
                // ydk 日期只作 sql 未收录卡的排序/过滤兜底；时间轴节点与日历小字改由 pack.db.sql 提供
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

    /**
     * 某一天发售的卡包名（日历里的日期小字用），同一天多个包返回多个；
     * 没有包的一天返回空表。日期均取自 pack.db.sql 的真实发售日，键与 {@link #getReleaseDateNodes()} 一致。
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
     * 时间轴的滑动节点：pack.db.sql 里出现过的真实发售日（yyyyMMdd，升序去重）。
     * <p>
     * 逐卡精确到日，不再有 ydk 那种“只知年份按 1 月 1 日入节点”的人造节点；sql 还没读到时返回空数组。
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
     * 取一张卡生效的发售日（原始编码）：首取 {@link #sqlCardReleaseDates}（逐卡精确），
     * sql 未收录时退回 {@link #cardReleaseDates}（ydk 文件名推，misc 包只知年份）。
     * 两边都没有时返回 null。
     */
    private Integer effectiveReleaseDate(int code) {
        Integer date = sqlCardReleaseDates.get(code);
        return date != null ? date : cardReleaseDates.get(code);
    }

    /**
     * 排序用的发售日期：yyyyMMdd，首取 sql 的精确日，只知年份的 ydk 包按当年 1 月 1 日归一；
     * 不在任何卡包/sql 里的卡返回 0（排序时这类卡放到最后）
     */
    public int getCardReleaseDate(int code) {
        Integer date = effectiveReleaseDate(code);
        return date == null ? 0 : normalizeReleaseDate(date);
    }

    /**
     * 卡详情“发售日期”用的文本：把生效的最初发售日 {@code yyyyMMdd} 格式化为 {@code yyyy-MM-dd}；
     * 只知道年份（misc 兜底）时只到年 {@code yyyy}；无任何记录时返回空串。
     */
    public String getCardReleaseDateText(int code) {
        Integer date = effectiveReleaseDate(code);
        return (date == null || date <= 0) ? "" : formatReleaseDate(date);
    }

    /**
     * 把发售日编码 {@code yyyyMMdd} 格式化为 {@code yyyy-MM-dd}；只知道年份（yyyy0001）时
     * 只到年 {@code yyyy}。调用方保证 {@code date>0}。
     */
    private static String formatReleaseDate(int date) {
        int year = date / 10000;
        int month = (date / 100) % 100;
        int day = date % 100;
        if (month < 1 || month > 12 || day < 1 || day > 31) {
            // 只知年份的 ydk 编码（yyyy0001）：不拼不存在的月日
            return String.format(java.util.Locale.US, "%04d", year);
        }
        return String.format(java.util.Locale.US, "%04d-%02d-%02d", year, month, day);
    }

    /**
     * 某张卡的发售日期是否落在 [startDate, endDate] 内（均为 yyyyMMdd，两端都含）。
     * <p>
     * 首取 sql 精确日；退回 ydk 时，xxxx-misc 这类只知年份的卡只要范围与那一年有交集就保留，
     * 不钉死在 1 月 1 日。没有任何卡包/sql 记录的卡返回 false（无日期可比）。
     */
    public boolean isCardReleasedBetween(int code, int startDate, int endDate) {
        Integer date = effectiveReleaseDate(code);
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
     * 异画卡（{@code alias>0}、与主卡描述相同）在 {@link CardManager} 里被 {@code setRealCode(原卡)}
     * 合并，{@code card.getCode()} 因此回的是原卡卡密。发售日期要按异画<b>自己</b>的卡密
     * （{@code card.Code}，即 cdb 原始 passcode）优先查，查不到再退回合并后的 {@code getCode()}。
     * 没日期时返回 null。
     */
    private Integer resolveCardReleaseDate(Card card) {
        if (card == null) {
            return null;
        }
        Integer date = effectiveReleaseDate(card.Code);
        if (date == null) {
            int merged = card.getCode();
            if (merged != card.Code) {
                date = effectiveReleaseDate(merged);
            }
        }
        return date;
    }

    /** 同 {@link #getCardReleaseDate(int)}，但按异画自身卡密优先（见 {@link #resolveCardReleaseDate}）。 */
    public int getCardReleaseDate(Card card) {
        Integer date = resolveCardReleaseDate(card);
        return date == null ? 0 : normalizeReleaseDate(date);
    }

    /** 同 {@link #isCardReleasedBetween(int, int, int)}，但按异画自身卡密优先。 */
    public boolean isCardReleasedBetween(Card card, int startDate, int endDate) {
        Integer date = resolveCardReleaseDate(card);
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
     * 卡详情“卡包展示”位的文本（按 {@link Card} 传入，异画按自身卡密优先）：
     * sql 有日期且有包名时分两行——上行日期、下行包名（包名取相对字号 0.5）；
     * 只有 ydk 日期或无日期时退回 ydk 包名（ydk 包名本带日期前缀，单行显、不另拼日期）。
     */
    public CharSequence getCardPackDisplayText(Card card) {
        if (card == null) {
            return "";
        }
        Integer date = resolveCardReleaseDate(card);
        if (date != null && date > 0) {
            String pack = resolveCardFirstPack(card);
            String dateText = formatReleaseDate(date);
            if (pack != null && !pack.isEmpty()) {
                return buildDatePackText(dateText, pack);
            }
            // sql 有日期但无 sql 包名（极少）：退回 ydk 包名，仍无则只显日期
            String ydk = findPackNameForCard(card);
            return ydk == null || ydk.isEmpty() ? dateText : ydk;
        }
        String ydk = findPackNameForCard(card);
        return ydk == null ? "" : ydk;
    }

    /**
     * 拼两行展示文本：上行 {@code dateText}（正常字号），下行 {@code pack}（{@link android.text.style.RelativeSizeSpan}
     * 相对字号 0.5，长包名换行也不会把段落撑得过宽）。
     */
    private static CharSequence buildDatePackText(String dateText, String pack) {
        android.text.SpannableStringBuilder sb = new android.text.SpannableStringBuilder();
        sb.append(dateText);
        sb.append('\n');
        int start = sb.length();
        sb.append(pack);
        sb.setSpan(new android.text.style.RelativeSizeSpan(0.5f), start, sb.length(),
                android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return sb;
    }

    /**
     * 取异画卡最初发售所属的真实包名：先按自身卡密 {@code card.Code}，查不到再按合并后的
     * {@code card.getCode()}。无 sql 记录时返回 null。
     */
    private String resolveCardFirstPack(Card card) {
        String pack = sqlCardFirstPack.get(card.Code);
        if (pack == null) {
            int merged = card.getCode();
            if (merged != card.Code) {
                pack = sqlCardFirstPack.get(merged);
            }
        }
        return pack;
    }

    /** 按卡密在 ydk 包成员表里找包名：先自身 {@code card.Code}，再合并后的 {@code card.getCode()}。 */
    private String findPackNameForCard(Card card) {
        String pack = findPackNameById(card.Code);
        if (pack == null && card.getCode() != card.Code) {
            pack = findPackNameById(card.getCode());
        }
        return pack;
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