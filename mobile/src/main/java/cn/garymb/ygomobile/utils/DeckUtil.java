package cn.garymb.ygomobile.utils;

import static cn.garymb.ygomobile.Constants.YDK_FILE_EX;
import static cn.garymb.ygomobile.ui.home.HomeActivity.pre_code_list;
import static cn.garymb.ygomobile.ui.home.HomeActivity.released_code_list;
import static cn.garymb.ygomobile.utils.ComparisonTableUtil.newIDsArray;
import static cn.garymb.ygomobile.utils.ComparisonTableUtil.oldIDsArray;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FilterInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.bean.DeckType;
import cn.garymb.ygomobile.bean.events.DeckFile;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.cards.deck.DeckItemType;
import cn.garymb.ygomobile.ui.cards.deck_square.api_response.MyOnlineDeckDetail;
import cn.garymb.ygomobile.ui.cards.deck.MyDeckItem;
import cn.hutool.core.util.ArrayUtil;

public class DeckUtil {
    private static final String TAG = "DeckUtil";
    private final static Comparator<DeckFile> nameCom = new Comparator<DeckFile>() {
        @Override
        public int compare(DeckFile ydk1, DeckFile ydk2) {

            if (!ydk1.getTypeName().equals(YGOUtil.s(R.string.category_Uncategorized))
                    && ydk2.getTypeName().equals(YGOUtil.s(R.string.category_Uncategorized)))
                return 1;
            else if (ydk1.getTypeName().equals(YGOUtil.s(R.string.category_Uncategorized))
                    && !ydk2.getTypeName().equals(YGOUtil.s(R.string.category_Uncategorized)))
                return -1;

            int id = ydk1.getTypeName().compareTo(ydk2.getTypeName());
            if (id == 0)
                return ydk1.getName().compareTo(ydk2.getName());
            else
                return id;
        }
    };

    private final static Comparator<DeckFile> dateInNameCom = new Comparator<DeckFile>() {
        @Override
        public int compare(DeckFile ydk1, DeckFile ydk2) {
            return ydk2.getName().compareTo(ydk1.getName());
        }
    };

    private final static Comparator<DeckFile> dateCom = new Comparator<DeckFile>() {
        @Override
        public int compare(DeckFile ydk1, DeckFile ydk2) {
            return ydk2.getDate().compareTo(ydk1.getDate());
        }
    };

    /**
     * 生成卡组类型的list
     *
     * @param context
     * @return
     */
    public static List<DeckType> getDeckTypeList(Context context) {
        List<DeckType> deckTypeList = new ArrayList<>();
        deckTypeList.add(new DeckType(YGOUtil.s(R.string.category_pack), AppsSettings.get().getPackDeckDir(), DeckType.ServerType.LOCAL));
        deckTypeList.add(new DeckType(YGOUtil.s(R.string.category_windbot_deck), AppsSettings.get().getAiDeckDir(), DeckType.ServerType.LOCAL));
        deckTypeList.add(new DeckType(YGOUtil.s(R.string.category_Uncategorized), AppsSettings.get().getDeckDir(), DeckType.ServerType.LOCAL));


        File[] files = new File(AppsSettings.get().getDeckDir()).listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    deckTypeList.add(new DeckType(f.getName(), f.getAbsolutePath()));
                }
            }
        }
        return deckTypeList;
    }

    public static List<DeckFile> getDeckList(String path) {
        List<DeckFile> deckList = new ArrayList<>();
        File[] files = new File(path).listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && file.getName().endsWith(YDK_FILE_EX)) {
                    deckList.add(new DeckFile(file));
                }
            }
        }
        if (path.equals(AppsSettings.get().getPackDeckDir())) {
            Collections.sort(deckList, dateInNameCom);
        } else {
            Collections.sort(deckList, nameCom);
        }
        return deckList;
    }

    public static List<DeckFile> getDeckAllList() {
        return getDeckAllList(AppsSettings.get().getDeckDir());
    }

    public static List<DeckFile> getDeckAllList(String path) {
        return getDeckAllList(path, false);
    }

    public static List<DeckFile> getDeckAllList(String path, boolean isDir) {
        List<DeckFile> deckList = new ArrayList<>();
        File[] files = new File(path).listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deckList.addAll(getDeckAllList(file.getAbsolutePath(), true));
                }
                if (file.isFile() && file.getName().endsWith(YDK_FILE_EX)) {
                    deckList.add(new DeckFile(file));
                }
            }
        }
        if (!isDir) {
            Log.e("DeckUtil", "路径 " + path);
            Log.e("DeckUtil", "路径1 " + AppsSettings.get().getPackDeckDir());
            if (path.equals(AppsSettings.get().getPackDeckDir())) {
                Collections.sort(deckList, dateCom);
            } else {
                Collections.sort(deckList, nameCom);
            }
        }
        return deckList;
    }

    /**
     * 根据卡组绝对路径获取卡组分类名称
     *
     * @param deckPath
     * @return
     */
    public static String getDeckTypeName(String deckPath) {
        if (isArchiveYdk(deckPath)) {
            // 压缩包内的 pack/*.ydk：分类归到“卡包展示”，与磁盘上的 pack 目录同口径
            return YGOUtil.s(R.string.category_pack);
        }
        File file = new File(deckPath);
        String name = file.getParentFile().getName();
        String lastName = file.getParentFile().getParentFile().getName();
        if (name.equals("pack") || name.equals("cacheDeck")) {
            return YGOUtil.s(R.string.category_pack);
        } else if (name.equals("Decks") && lastName.equals(Constants.WINDBOT_PATH)) {
            return YGOUtil.s(R.string.category_windbot_deck);
        } else if (name.equals("deck") && lastName.equals(Constants.PREF_DEF_GAME_DIR)) {
            return YGOUtil.s(R.string.category_Uncategorized);
        } else {
            return name;
        }
    }

    /**
     * 压缩包内 ydk 的引用路径分隔符：{@code <压缩包绝对路径>!/pack/xxx.ydk}。
     * <p>
     * 整串仍能当普通路径传递（{@code new File(它).getName()} 依旧得到 xxx.ydk），但它不对应
     * 磁盘实体文件，因此读写一律要过 {@link #openYdk(String)} 与 {@link #ydkExists(String)}，
     * 不能再直接 {@code file.exists()} 或 {@code new FileInputStream}。
     * 这么做的目的：卡包 ydk 不再解压到 cacheDeck（旧做法每换一次压缩包就多一批没人认领的过时 ydk）。
     */
    public static final String YDK_ARCHIVE_SEP = "!/";

    /** 卡包 ydk 在压缩包内的目录约定（ygocore 下各 zip/ypk 都是 pack/xxx.ydk） */
    private static final String PACK_DIR_NAME = "pack";

    /** 本进程内是否已清过旧版解压残留 */
    private static boolean sCacheDeckPurged;

    /** 是否压缩包内的 ydk 引用路径 */
    public static boolean isArchiveYdk(String path) {
        return path != null && path.contains(YDK_ARCHIVE_SEP);
    }

    private static String archiveYdkPath(String archivePath, String entryName) {
        return archivePath + YDK_ARCHIVE_SEP + entryName;
    }

    /** 引用路径拆成 (压缩包路径, 包内条目名)；非引用路径返回 null */
    private static String[] splitArchiveYdk(String path) {
        int sep = path.indexOf(YDK_ARCHIVE_SEP);
        if (sep < 0) {
            return null;
        }
        return new String[]{path.substring(0, sep), path.substring(sep + YDK_ARCHIVE_SEP.length())};
    }

    /** ydk 的文件名（含 .ydk）：压缩包条目取条目最后一段，实体文件取路径最后一段 */
    public static String ydkFileName(String path) {
        if (path == null || path.isEmpty()) {
            return "";
        }
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }

    /** ydk 的显示名（去 .ydk），实体文件与压缩包条目同一口径 */
    public static String ydkDisplayName(String path) {
        return IOUtils.tirmName(ydkFileName(path), YDK_FILE_EX);
    }

    /**
     * 按 ydk 文件名在卡包档案里找引用路径，找不到返回 null。
     * 用于升级过渡：旧版本把“最后卡组”存成解压到 cacheDeck 的实体路径，
     * 那些文件现在不再生成，按同名回到档案里的 pack/xxx.ydk
     */
    public static String findPackYdkByName(String wantedFileName) {
        if (wantedFileName == null || wantedFileName.isEmpty()) {
            return null;
        }
        final String[] found = new String[1];
        forEachPackYdk((ydkPath, entryName) -> {
            if (found[0] == null && wantedFileName.equalsIgnoreCase(ydkFileName(entryName))) {
                found[0] = ydkPath;
            }
        });
        return found[0];
    }

    /** 按 UTF-8 打开档案，条目名编码不合法时退回 GBK（与旧版解压那一段的兜底一致） */
    private static ZipFile openArchive(File file) throws IOException {
        ZipFile zipFile = null;
        try {
            zipFile = new ZipFile(file, StandardCharsets.UTF_8);
            zipFile.entries().hasMoreElements();
        } catch (IllegalArgumentException e) {
            IOUtils.close(zipFile);
            zipFile = new ZipFile(file, Charset.forName("GBK"));
        }
        return zipFile;
    }

    /**
     * 打开任意 ydk：磁盘实体文件直接开流；压缩包引用路径就地读包内条目，不解压。
     * 返回的流 close() 时会连带关闭 ZipFile，调用方只管关流，不会漏文件句柄。
     */
    public static InputStream openYdk(String path) throws IOException {
        if (path == null || path.isEmpty()) {
            throw new IOException("空的 ydk 路径");
        }
        String[] split = splitArchiveYdk(path);
        if (split == null) {
            return new FileInputStream(path);
        }
        final ZipFile zipFile = openArchive(new File(split[0]));
        boolean handedOff = false;
        try {
            ZipEntry entry = zipFile.getEntry(split[1]);
            if (entry == null) {
                throw new IOException("压缩包内没有该条目: " + split[1]);
            }
            InputStream in = zipFile.getInputStream(entry);
            handedOff = true;
            return new FilterInputStream(in) {
                @Override
                public void close() throws IOException {
                    try {
                        super.close();
                    } finally {
                        IOUtils.close(zipFile);
                    }
                }
            };
        } finally {
            if (!handedOff) {
                IOUtils.close(zipFile);
            }
        }
    }

    /** ydk 是否还读得到：实体文件看 exists，压缩包看条目是否还在（换包后旧引用自然判失效） */
    public static boolean ydkExists(String path) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        String[] split = splitArchiveYdk(path);
        if (split == null) {
            return new File(path).isFile();
        }
        ZipFile zipFile = null;
        try {
            File archive = new File(split[0]);
            if (!archive.isFile()) {
                return false;
            }
            zipFile = openArchive(archive);
            return zipFile.getEntry(split[1]) != null;
        } catch (IOException | IllegalArgumentException e) {
            Log.w(TAG, "检查 ydk 是否存在失败: " + path, e);
            return false;
        } finally {
            IOUtils.close(zipFile);
        }
    }

    /** 遍历一个档案内 ydk 条目的访问器；交付的是引用路径，不交付流，因此不会因中途异常泄句柄 */
    public interface PackYdkVisitor {
        void visit(String ydkPath, String entryName);
    }

    /** 条目是否位于包内 pack/ 目录下（按路径段比对，避免 mypack/ 一类误命中） */
    private static boolean isUnderPackDir(String entryName) {
        String[] segments = entryName.split("/");
        // 最后一段是文件名，只看它之前的目录段
        for (int i = 0; i < segments.length - 1; i++) {
            if (PACK_DIR_NAME.equalsIgnoreCase(segments[i])) {
                return true;
            }
        }
        return false;
    }

    /**
     * 遍历 expansions 下所有 zip/ypk 里的 ydk，只交付引用路径（不解压到 cacheDeck）。
     * <p>
     * 优先只取包内 {@code pack/} 目录下的 ydk（用户指定的打包约定）；某个包里找不到 pack/ 目录时
     * 退回该包内任意 ydk，保持旧版可见范围，不会因为收紧规则而少包。
     * 单个档案打不开（不是 zip、编码异常等）只跳过，不能拖垮整个卡包列表。
     */
    public static void forEachPackYdk(PackYdkVisitor visitor) {
        File[] files = AppsSettings.get().getExpansionFiles();
        if (files == null || visitor == null) {
            return;
        }
        for (File file : files) {
            ZipFile zipFile = null;
            try {
                if (!file.isFile()) {
                    continue;
                }
                zipFile = openArchive(file);
                List<String> packEntries = new ArrayList<>();
                List<String> otherEntries = new ArrayList<>();
                Enumeration<?> entries = zipFile.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = (ZipEntry) entries.nextElement();
                    String name = entry.getName();
                    if (entry.isDirectory() || !name.toLowerCase(Locale.US).endsWith(YDK_FILE_EX)) {
                        continue;
                    }
                    if (isUnderPackDir(name)) {
                        packEntries.add(name);
                    } else {
                        otherEntries.add(name);
                    }
                }
                List<String> picked = packEntries.isEmpty() ? otherEntries : packEntries;
                String archivePath = file.getAbsolutePath();
                for (String entryName : picked) {
                    visitor.visit(archiveYdkPath(archivePath, entryName), entryName);
                }
            } catch (IOException | IllegalArgumentException e) {
                Log.w(TAG, "读取卡包档案失败: " + file.getAbsolutePath(), e);
            } finally {
                IOUtils.close(zipFile);
            }
        }
    }

    /**
     * 一次性清掉旧版解压到 cacheDeck 的 ydk 残留（每进程只做一次）。
     * 卡包 ydk 现在一律就地从压缩包读，该目录已没有任何写入方，留下的都是过时副本。
     * 只删该目录下的 *.ydk：它本身是 app 私有外部缓存目录，不碰其它文件。
     */
    private static void purgeCacheDeckLeftovers() {
        if (sCacheDeckPurged) {
            return;
        }
        sCacheDeckPurged = true;
        File[] leftovers = new File(AppsSettings.get().getCacheDeckDir())
                .listFiles(f -> f.isFile() && f.getName().toLowerCase(Locale.US).endsWith(YDK_FILE_EX));
        if (leftovers == null) {
            return;
        }
        for (File leftover : leftovers) {
            if (!leftover.delete()) {
                Log.w(TAG, "清理旧解压 ydk 失败: " + leftover.getAbsolutePath());
            }
        }
    }

    //获取扩展卡的列表：expansions/deck 下的实体 ydk + 卡包档案内 pack/ 的 ydk（引用路径，不解压）
    public static List<DeckFile> getExpansionsDeckList() throws IOException {
        AppsSettings appsSettings = AppsSettings.get();
        List<DeckFile> deckList = new ArrayList<>();
        File[] files = new File(appsSettings.getExpansionsPath(), Constants.CORE_DECK_PATH).listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && file.getName().endsWith(YDK_FILE_EX)) {
                    deckList.add(new DeckFile(file));
                }
            }
        }
        forEachPackYdk((ydkPath, entryName) -> deckList.add(new DeckFile(ydkPath)));
        purgeCacheDeckLeftovers();
        Collections.sort(deckList, nameCom);
        return deckList;
    }

    public static int getFirstCardCode(String deckPath) {
        InputStreamReader in = null;
        try {
            // 实体文件与压缩包内 ydk 都走同一个入口
            in = new InputStreamReader(openYdk(deckPath), StandardCharsets.UTF_8);
            BufferedReader reader = new BufferedReader(in);
            String line = null;
            DeckItemType type = DeckItemType.Space;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("!side")) {
                    type = DeckItemType.SideCard;
                    continue;
                }
                if (line.startsWith("#")) {
                    if (line.startsWith("#main")) {
                        type = DeckItemType.MainCard;
                    } else if (line.startsWith("#extra")) {
                        type = DeckItemType.ExtraCard;
                    }
                    continue;
                }
                line = line.trim();
                if (line.length() == 0 || !TextUtils.isDigitsOnly(line)) {
                    if (Constants.DEBUG)
                        Log.w("kk", "read not number " + line);
                    continue;
                }
                Integer id = Integer.parseInt(line);
                if (released_code_list.contains(id)) {//先查看id对应的卡片密码是否在正式数组中存在
                    id = pre_code_list.get(released_code_list.indexOf(id));//替换成对应先行数组里的code
                }//执行完后变成先行密码，如果constants对照表里存在该密码，则如下又转换一次，所以发布app后必须及时更新在线对照表
                if (ArrayUtil.contains(oldIDsArray, id)) {
                    id = ArrayUtil.get(newIDsArray, ArrayUtil.indexOf(oldIDsArray, id));
                }
                return id;
            }
        } catch (IOException e) {
            Log.e("deckreader", "read 2", e);
        } finally {
            IOUtils.close(in);
        }

        return -1;
    }

    //将MyOnlineDeckDetail转MyDeckItem类型list，有时候会需要用到
    public static List<MyDeckItem> toDeckItemList(List<MyOnlineDeckDetail> serverDecks) {
        List<MyDeckItem> myOnlineDecks = new ArrayList<>();
        for (MyOnlineDeckDetail detail : serverDecks) {
            MyDeckItem item = new MyDeckItem();
            item.setDeckName(detail.getDeckName());
            item.setDeckType(detail.getDeckType());
            // 从在线卡组转本地卡组没有卡组路径，需要根据同分类同名拼接一个
            item.setDeckPath(AppsSettings.get().getDeckDir() + "/" + (detail.getDeckType().isEmpty() ? "" : detail.getDeckType() + "/") + detail.getDeckName() + Constants.YDK_FILE_EX);
            item.setDeckId(detail.getDeckId());
            item.setUserId(detail.getUserId());
            item.setDeckCoverCard1(detail.getDeckCoverCard1());
            item.setUpdateTimestamp(detail.getDeckUpdateDate());
            item.setPublic(detail.isPublic());
            item.setDelete(detail.isDelete());
            myOnlineDecks.add(item);
        }
        return myOnlineDecks;
    }

    //读取file指定的ydk文件，返回其内包含的deckId。如果不包含deckId，返回null
    public static String getDeckId(File file) {
        return file == null ? null : getDeckId(file.getAbsolutePath());
    }

    /**
     * 读取 ydk 内的 deckId，实体文件与压缩包引用都适用。
     * 原先用 file.exists()/isFile() 守门，对压缩包内 ydk（不存在实体文件）会直接打“文件不存在”
     * 并返回 null，改走 ydkExists/openYdk 后两种来源同一口径
     */
    public static String getDeckId(String ydkPath) {
        if (!ydkExists(ydkPath)) {
            LogUtil.w(TAG, "ydk 不可读: " + ydkPath);
            return null;
        }

        String deckId = null;
        Integer userId = null;
        InputStream ydkStream = null;
        try {
            ydkStream = openYdk(ydkPath);
            InputStreamReader in = new InputStreamReader(ydkStream, StandardCharsets.UTF_8);
            BufferedReader reader = new BufferedReader(in);

            String line = null;
            while ((line = reader.readLine()) != null) {
                // LogUtil.i(TAG, line);
                if (line.startsWith("###")) {//注意，先判断###，后判断##。因为###会包括##的情况
                    try {
                        line = line.replace("#", "");
                        if (!line.isEmpty()) {
                            userId = Integer.parseInt(line.replaceAll("###", ""));
                        }
                    } catch (NumberFormatException e) {
                        LogUtil.e(TAG, "getId(77): integer" + line + "parse error" + e.toString());
                    }

                } else if (line.startsWith("##")) {
                    deckId = line.replace("#", "");

                }

            }

        } catch (IOException e) {
            LogUtil.e(TAG, "read 1", e);
        } finally {
            IOUtils.close(ydkStream);
        }
        return deckId;
    }

    /**
     * 查询卡组目录下的所有ydk文件（包含子文件夹）
     *
     * @return 包含所有ydk文件的File数组
     */
    public static File[] getAllYdk() {
        File dir = new File(AppsSettings.get().getResourcePath(), Constants.CORE_DECK_PATH);
        if (!dir.exists() || !dir.isDirectory()) {
            return new File[0];
        }
        // 使用ArrayList存储结果，方便动态添加
        ArrayList<File> ydkFiles = new ArrayList<>();
        // 递归遍历目录和子目录
        findYdkFiles(dir, ydkFiles);
        // 将ArrayList转换为File数组
        return ydkFiles.toArray(new File[0]);
    }

    /**
     * 递归查找指定目录下的所有YDK文件
     *
     * @param dir      当前查找的目录
     * @param ydkFiles 存储找到的YDK文件
     */
    private static void findYdkFiles(File dir, ArrayList<File> ydkFiles) {
        // 获取目录下的所有文件和子目录
        File[] files = dir.listFiles();
        if (files == null) {
            return; // 目录不可访问或为空
        }

        for (File file : files) {
            if (file.isDirectory()) {
                // 如果是子目录，递归查找
                findYdkFiles(file, ydkFiles);
            } else {
                // 如果是文件，检查是否为YDK文件
                String fileName = file.getName().toLowerCase(Locale.US);
                if (fileName.endsWith(Constants.YDK_FILE_EX)) {
                    processYdkLineBreaks(file);
                    ydkFiles.add(file);
                }
            }
        }
    }

    /**
     * 处理单个YDK文件中的换行符，将
、\n、/n、\\r\n替换为实际换行（\r
）
     * 确保文本中的换行标记能真实换行显示
     */
    private static void processYdkLineBreaks(File ydkFile) {
        long originalLastModified = ydkFile.lastModified();//记录原始修改时间，修改后要覆盖回去

        FileInputStream fis = null;
        FileOutputStream fos = null;
        try {
            // 1. 读取文件原始内容（包含各种换行标记）
            fis = new FileInputStream(ydkFile);
            InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.UTF_8);
            StringBuilder contentBuilder = new StringBuilder();
            char[] buffer = new char[1024];
            int bytesRead;
            while ((bytesRead = isr.read(buffer)) != -1) {
                contentBuilder.append(buffer, 0, bytesRead);
            }
            String content = processYdkLineBreaks(contentBuilder.toString());

            // 将处理后的内容写回文件
            fos = new FileOutputStream(ydkFile);
            OutputStreamWriter osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
            osw.write(content);
            osw.flush();

        } catch (IOException e) {
            LogUtil.e(TAG, "处理YDK文件换行符失败：" + ydkFile.getAbsolutePath(), e);
        } finally {
            IOUtils.close(fis);
            IOUtils.close(fos);
            // 覆盖文件修改时间为一开始的时间
            ydkFile.setLastModified(originalLastModified);
        }
    }

    /**
     * 处理文本中的换行符，将
、\n、/n、\\r\n替换为实际换行（\r
）
     * 确保文本中的换行标记能真实换行显示
     *
     * @param content 需要处理的原始文本内容
     * @return 处理后的文本内容
     */
    private static String processYdkLineBreaks(String content) {
        if (content == null) {
            return null;
        }

        // 先处理转义的\\r\\n（文本中显示为"\r\n"的字符串）
        content = content.replace("\\r\\n", "\r\n");
        // 处理转义的\\n（文本中显示为"\n"的字符串）
        content = content.replace("\\n", "\r\n");
        // 处理错误的/n（斜杠+n）
        content = content.replace("/n", "\r\n");
        // 处理标准换行符\n（确保统一为Windows风格的\r\n）
        // 先移除可能存在的重复\r，避免出现\r\r\n的情况
        content = content.replace("\r", "");
        // 再将所有\n替换为\r\n
        content = content.replace("\n", "\r\n");

        // 去除末尾可能多余的空行，然后添加一个标准换行
        return content.trim() + "\r\n";
    }

    //读取卡组目录下的所有ydk文件，解析ydk文件（包括从ydk文件内容中读取deckId），生成List<MyDeckItem>解析结果
    public static List<MyDeckItem> getMyDeckItems() {
        List<MyDeckItem> result = new ArrayList<>();
        File[] files = getAllYdk();
        for (File file : files) {
            MyDeckItem item = getMyDeckItem(file);
            result.add(item);
        }
        return result;
    }

    public static MyDeckItem getMyDeckItem(File ydk) {
        MyDeckItem item = new MyDeckItem();
        item.setDeckName(ydk.getName());
        //如果是 deck 并且上一个目录是 ygocore 的话，保证不会把名字为 deck 的卡包识别为未分类
        if (ydk.getParentFile().getName().equals(Constants.CORE_DECK_PATH) && ydk.getParentFile().getParentFile().getName().equals(Constants.PREF_DEF_GAME_DIR)) {
            item.setDeckType("");
        } else {
            item.setDeckType(ydk.getParentFile().getName());
        }
        item.setUpdateTimestamp(ydk.lastModified());
        item.setDeckPath(ydk.getPath());
        item.setDeckCoverCard1(DeckUtil.getFirstCardCode(ydk.getPath()));
        item.setDelete(false); // 初始化 isDelete 为 false，都是已存在的卡组

        String deckId = getDeckId(ydk);
        if (deckId != null) {
            item.setDeckId(deckId);
        }
        return item;
    }

    // 读取deckPath对应的卡组文件到string，将卡组id、用户id设置到string中
    //注意，不更改ydk文件内容
    //下载卡组后，保存卡组之前
    //上传卡组前，对于未存在卡组id的卡组，自动填入新的卡组id、用户id
    public static String setDeckId(String deckPath, Integer userId, String deckId) {
        StringBuilder contentBuilder = new StringBuilder();
        boolean userIdFlag = false;
        boolean deckIdFlag = false;

        FileInputStream inputStream = null;
        try {
            inputStream = new FileInputStream(deckPath);

            InputStreamReader in = null;
            in = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
            BufferedReader reader = new BufferedReader(in);

            String line = null;
            while ((line = reader.readLine()) != null) {
                /* 如果文件中已经存在deckId，则将其替换 */
                if (line.contains("##") && !line.contains("###")) {
                    deckIdFlag = true;
                    line = "##" + deckId;
                } else if (line.contains("###")) {//存在userId，将其替换
                    userIdFlag = true;
                    line = "###" + userId;
                }
                contentBuilder.append(line);
                contentBuilder.append('\n'); // Add line break
            }

        } catch (Exception e) {
            LogUtil.e(TAG, "read 1", e);
        } finally {
            IOUtils.close(inputStream);
        }

        if (!deckIdFlag) {//原始ydk中不存在deckId，添加deckId行
            contentBuilder.append("\n");
            contentBuilder.append("##" + deckId);
        }

        if (!userIdFlag) {//原始ydk中不存在userId，添加userId行
            contentBuilder.append("\n");
            contentBuilder.append("###" + userId);
        }

        String content = contentBuilder.toString();

        return content;
    }

    public static boolean saveFile(File file, String content, long modificationTime) {
        FileOutputStream fos = null;
        try {
            // 确保父目录存在
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                boolean dirsCreated = parentDir.mkdirs(); // 创建所有缺失的父目录
                if (!dirsCreated) {
                    LogUtil.e(TAG, "无法创建文件目录: " + parentDir.getAbsolutePath());
                    return false;
                }
            }

            // 创建文件对象（如果文件不存在，会自动创建）
            if (!file.exists()) {
                boolean fileCreated = file.createNewFile();
                if (!fileCreated) {
                    LogUtil.e(TAG, "无法创建文件: " + file.getAbsolutePath());
                    return false;
                }
            }

            // 创建文件输出流
            fos = new FileOutputStream(file);
            // 写入内容
            content = processYdkLineBreaks(content);
            fos.write(content.getBytes(StandardCharsets.UTF_8)); // 使用 UTF-8 编码
            fos.flush();

            // 设置指定的最后修改时间
            file.setLastModified(modificationTime);
        } catch (Exception e) {
            LogUtil.e(TAG, "保存文件失败", e);
            return false;
        } finally {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException e) {
                }
            }
        }
        return true;
    }

    /**
     *
     * @param fileFullPath     文件的完整路径
     * @param content
     * @param modificationTime
     * @return
     */
    public static boolean saveFileToPath(String fileFullPath, String content, long modificationTime) {
        File file = new File(fileFullPath);
        return saveFile(file, content, modificationTime);
    }

    /**
     * 保存文件到指定路径，并设置指定的最后修改时间
     *
     * @param fileParentPath   保存文件的父目录路径
     * @param fileName         文件名
     * @param content          文件内容
     * @param modificationTime 最后修改时间（毫秒时间戳）
     * @return 保存是否成功
     */
    public static boolean saveFileToPath(String fileParentPath, String fileName, String content, long modificationTime) {

        File file = new File(fileParentPath, fileName);
        return saveFile(file, content, modificationTime);
    }

    /**
     * 将 Unix 时间戳转换为 GMT 格式的日期字符串
     *
     * @param timestamp 时间戳（毫秒）
     * @return GMT 格式的日期字符串（例如：Thu, 04 Jul 2025 08:00:55 GMT）
     */
    public static String convertToGMTDate(long timestamp) {
        try {
            // 创建格式化器并设置时区为 GMT
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.CHINA);
            sdf.setTimeZone(TimeZone.getTimeZone("GMT"));

            // 格式化时间戳
            return sdf.format(new Date(timestamp));
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
