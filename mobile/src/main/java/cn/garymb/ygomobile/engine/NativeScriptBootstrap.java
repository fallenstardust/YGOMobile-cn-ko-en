package cn.garymb.ygomobile.engine;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.NativeInitOptions;
import cn.garymb.ygomobile.utils.CrashHandler;
import cn.garymb.ygomobile.utils.IOUtils;

/**
 * native 决斗引擎（libygoengine.so）的启动引导：档案直读下的引擎初始化。
 *
 * <p>本类不再解压任何东西。两套 C++ 读取通道都直接从档案里取数据（对齐
 * {@code Classes/gframe/game.cpp::LoadExpansions} 与
 * {@code Classes/gframe/data_manager.cpp::ScriptReaderEx}/{@code LoadDB}）：
 * <ul>
 *   <li>libygoengine.so（本类引导）：{@code ygo_engine_jni.cpp} 自己扫
 *       {@code <root>/*.zip|*.ypk} 与 {@code <root>/expansions/*.zip|*.ypk} 的中央目录建索引，
 *       按需 pread+inflate 就地读包内 {@code script/*.lua} 与 {@code *.cdb}；主脚本走
 *       {@code <root>/scripts.zip} 同一套机制；</li>
 *   <li>libYGOMobile.so（gframe）：靠 {@code AppsSettings#makeZipList} 把同一批档案挂进
 *       irrlicht FileArchives，脚本与 cdb 由 gframe 就地读。</li>
 * </ul>
 *
 * <p>读取优先级（高→低）与 cdb/pics 统一口径一致：
 * {@code <expansions>/script|*.cdb} 散调试文件 ＞ 卡包 zip/ypk（按包名字典序，靠后的包优先）
 * ＞ 主资源档案（{@code cards.cdb} / {@code scripts.zip}）＞ 主散目录。
 * 因此卡包档案内 cdb 与 script 的效力恒高于 {@code ygocore/cards.cdb} 与
 * {@code scripts.zip}，且都不需要落地解压。
 *
 * <p>Java 侧只负责三件事：① 把包内 {@code scripts.zip} 拷到资源根目录
 *（{@link #copyScriptsZipFromAssets}）；② 一次性清掉旧版本"卡包物化"落地的残留
 *（{@link #pruneLegacyMaterialized}，残留副本会盖住档案里的新内容，必须清）；
 * ③ 引导与热重载：卡包增删/覆写不改变 cdb 列表，故用
 *{@link #packsFingerprint} 单独判定并触发 {@code reloadCards}（native 侧重扫档案），
 * 无需重启应用。
 */
public final class NativeScriptBootstrap {
    private static final String TAG = "NativeScriptBootstrap";

    /** 已成功 init 的资源根目录；相同路径重复调用直接放行（引擎全局状态幂等）。 */
    private static volatile String sInitedRootPath;
    /** 上次 init/reloadCards 实际使用的主资源 cdb 列表，用于检测数据库变化。 */
    private static volatile String[] sInitedCdbPaths = new String[0];
    /** 上次 init/reloadCards 实际使用的卡包档案指纹，用于检测卡包增删/覆写。 */
    private static volatile String sInitedPacksFp = "";
    /** 旧版物化残留清理只做一次（进程内首次引导时）。 */
    private static volatile boolean sLegacyPruned;

    /** 旧版卡包物化标记文件名前缀，仅用于按清单回收历史产物。 */
    private static final String MARK_PREFIX = ".mark_";

    private NativeScriptBootstrap() {
    }

    /**
     * 引导引擎：全部档案直读（无解压），调用 {@link OcgDuelEngine#init}。
     *
     * @return 引擎可用且初始化成功返回 true
     */
    public static synchronized boolean ensureEngineReady() {
        if (!OcgDuelEngine.isAvailable()) {
            Log.e(TAG, "libygoengine.so 不可用");
            return false;
        }
        try {
            long t0 = SystemClock.uptimeMillis();
            // 旧版把卡包 script/*.lua 与包内 cdb 解压落地到 expansions/script、expansions/cdb。
            // 现在两套 C++ 都能就地读档案，落地不仅白占空间，残留旧副本还会以最高优先级盖住
            // 档案里的新内容，因此首轮一次性回收
            pruneLegacyMaterialized(AppsSettings.get().getResourcePath());
            long t1 = SystemClock.uptimeMillis();
            NativeInitOptions options = AppsSettings.get().getNativeInitOptions();
            final String rootPath = options.mWorkPath;
            String[] cdbPaths = options.mDbList.toArray(new String[0]);
            // 卡包列表变化不会改变 mDbList，单独算指纹才能触发热重载
            final String packs = packsFingerprint(rootPath);
            final boolean readExpansions = AppsSettings.get().isReadExpansions();
            long t2 = SystemClock.uptimeMillis();
            // 诊断：档案直读三段来源的当前态
            Log.i(TAG, "engine bootstrap root=" + rootPath
                    + " scripts.zip=" + sizeOrMissing(new File(rootPath, Constants.CORE_SCRIPTS_ZIP))
                    + " cards.cdb=" + sizeOrMissing(new File(rootPath, Constants.DATABASE_NAME))
                    + " 卡包=" + countOf(collectPacks(rootPath))
                    + " readExpansions=" + readExpansions
                    + " 主cdb数=" + cdbPaths.length);
            // 分段耗时：开局卡顿定位用（预热未完成时 startDuel 闸门会同步等到这里全部结束）
            Log.i(TAG, "engine bootstrap 耗时(ms) 旧物化清理=" + (t1 - t0)
                    + " 选项解算=" + (t2 - t1));
            if (rootPath.equals(sInitedRootPath)) {
                // 已初始化：主 cdb 列表变化或卡包增删/覆写时热重载引擎卡数据
                // （native reloadCards 会重扫档案，故包内容变了也不必重启应用）
                if (!Arrays.equals(sInitedCdbPaths, cdbPaths) || !packs.equals(sInitedPacksFp)) {
                    long r0 = SystemClock.uptimeMillis();
                    OcgDuelEngine.reloadCards(cdbPaths, readExpansions);
                    sInitedCdbPaths = cdbPaths;
                    sInitedPacksFp = packs;
                    Log.i(TAG, "卡片数据库/卡包档案变化，热重载引擎卡数据，耗时="
                            + (SystemClock.uptimeMillis() - r0) + "ms");
                }
                return true;
            }
            long i0 = SystemClock.uptimeMillis();
            if (OcgDuelEngine.init(rootPath, cdbPaths, readExpansions)) {
                Log.i(TAG, "引擎 init 完成，耗时=" + (SystemClock.uptimeMillis() - i0)
                        + "ms cdb数=" + cdbPaths.length + " 卡包数=" + countOf(collectPacks(rootPath)));
                sInitedRootPath = rootPath;
                sInitedCdbPaths = cdbPaths;
                sInitedPacksFp = packs;
                return true;
            }
            Log.e(TAG, "OcgDuelEngine.init 失败: " + rootPath);
            return false;
        } catch (Throwable t) {
            Log.e(TAG, "引擎引导异常", t);
            return false;
        }
    }

    /**
     * 后台预热入口：在创建时刻立即起子线程执行 {@link #ensureEngineReady()}
     *（旧残留清理 → cdb 装载），把开局闸门需要等待的引导全量提前到进程/界面创建时刻，
     * 用后续全部自然操作耗时（资源检查/导航/建房/猜拳/选先后攻）覆盖引导总时长。瞬态失败
     *（如首次安装时资源目录尚未就绪）每 3 秒重试一次、至多 3 次；native 库缺失（非 ARM
     * ABI/未打包）属永久不可用不重试。ensureEngineReady 同步幂等，多个预热点（App 创建/
     * YGOProActivity 创建/建主引导）并发时串行合并互不重复；失败不阻断任何流程，开局闸门
     * 仍会作最后一次同步引导。
     *
     * @param threadName 子线程名（logcat 定位用）
     * @param scene      崩溃诊断场景锚点（CrashHandler.hookThread）与日志标识
     */
    public static void warmUpAsync(String threadName, String scene) {
        Thread t = new Thread(() -> {
            Log.i(TAG, "引擎预热开始（" + scene + "，进程启动后 "
                    + (SystemClock.uptimeMillis() - android.os.Process.getStartUptimeMillis())
                    + "ms）");
            for (int attempt = 1; attempt <= 4; attempt++) {
                if (ensureEngineReady()) {
                    Log.i(TAG, "引擎预热完成（" + scene + "，第 " + attempt + " 次尝试）");
                    return;
                }
                if (!OcgDuelEngine.isAvailable()) {
                    Log.e(TAG, "引擎预热取消（" + scene + "）：libygoengine.so 不可用");
                    return;
                }
                Log.w(TAG, "引擎预热第 " + attempt + "/4 次失败（" + scene + "），3 秒后重试");
                try {
                    Thread.sleep(3000L);
                } catch (InterruptedException e) {
                    return;
                }
            }
        }, threadName);
        t.setDaemon(true);
        CrashHandler.getInstance().hookThread(t, scene);
        t.start();
    }

    /**
     * 把包内 {@code scripts.zip} 落到资源根目录：单条大档案一次流式拷贝，只在内容变化时重写
     *（保留 mtime，native 侧的 size+mtime 自查因此不会反复重建索引），进度按字节上报。
     * 主脚本与卡包脚本都由 native 直接读档案，不再解压成 {@code script/} 下的散文件。
     *
     * @param update true 才做“内容变了就重写”；false 时目标存在就不动（正常启动靠它零开销）
     */
    public static void copyScriptsZipFromAssets(Context context, String assetZip, File resDir,
            boolean update, IOUtils.CopyProgress progress) throws IOException {
        IOUtils.copyFileFromAssetsIfChanged(context, assetZip, resDir, update, progress);
    }

    /**
     * 资源根目录（ygocore/）与 {@code expansions/} 下的全部卡包档案（.zip/.ypk，ypk 只是
     * 换了扩展名的 zip），按包文件名升序返回；排除主资源档案 pics.zip / scripts.zip 与隐藏
     * 临时档案，口径与 native 侧 {@code collectPacksIn} 一致。
     */
    public static List<File> collectPacks(String resourcePath) {
        List<File> out = new ArrayList<>();
        if (resourcePath == null || resourcePath.isEmpty()) {
            return out;
        }
        for (File dir : new File[]{new File(resourcePath),
                new File(resourcePath, Constants.CORE_EXPANSIONS)}) {
            File[] files = dir.listFiles(f -> f.isFile() && isPackName(f.getName()));
            if (files != null) {
                out.addAll(Arrays.asList(files));
            }
        }
        out.sort((a, b) -> a.getName().toLowerCase(Locale.ROOT)
                .compareTo(b.getName().toLowerCase(Locale.ROOT)));
        return out;
    }

    /** 是否卡包档案：zip/ypk，且不是主资源档案 pics.zip / scripts.zip，也不是隐藏临时档。 */
    private static boolean isPackName(String name) {
        if (name.startsWith(".")) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.equals(Constants.CORE_PICS_ZIP) || lower.equals(Constants.CORE_SCRIPTS_ZIP)) {
            return false;
        }
        return lower.endsWith(".zip") || lower.endsWith(Constants.YPK_FILE_EX);
    }

    /**
     * 卡包档案指纹：包名 + 大小 + mtime 串接。任一个包被新增、删除或就地覆写都会变化，
     * 用于触发 {@code reloadCards}（native 重扫档案）。
     */
    private static String packsFingerprint(String resourcePath) {
        StringBuilder sb = new StringBuilder(128);
        for (File f : collectPacks(resourcePath)) {
            sb.append(f.getName()).append(':').append(f.length())
                    .append(':').append(f.lastModified()).append(';');
        }
        return sb.toString();
    }

    /**
     * 一次性回收旧版“卡包物化”落地产物（native 现已就地读档案，残留副本会以最高优先级
     * 盖住档案里的新内容）：
     * <ol>
     *   <li>按 {@code <expansions>/script/.mark_<包名>} 清单删除当初解出的脚本与 cdb
     *      ——包已被删除的孤儿标记也照清单回收；</li>
     *   <li>再补一轮“内容逐字节等同包内同名条目”的清扫，覆盖更早期无清单标记的残留；
     *      手工调试用的差异文件一律保留；</li>
     *   <li>删除标记文件本身；{@code <expansions>/cdb/} 是物化专用目录（散 cdb 档落在
     *      {@code expansions/} 根），整体清空。</li>
     * </ol>
     * 仅在进程内首次引导时执行，失败只记日志，不阻断任何流程。
     */
    private static synchronized void pruneLegacyMaterialized(String resourcePath) {
        if (sLegacyPruned || resourcePath == null || resourcePath.isEmpty()) {
            return;
        }
        sLegacyPruned = true;
        try {
            File expDir = new File(resourcePath, Constants.CORE_EXPANSIONS);
            File scriptDir = new File(expDir, Constants.CORE_SCRIPT_PATH);
            File cdbDir = new File(expDir, Constants.CORE_PACK_CDB_DIR);
            File[] marks = scriptDir.listFiles(f -> f.isFile()
                    && f.getName().startsWith(MARK_PREFIX));
            int deleted = 0;
            if (marks != null && marks.length > 0) {
                for (File mark : marks) {
                    // v2 标记带 s:/d: 清单；v1 标记只有单行指纹，无清单可依
                    for (String line : readMarkerLines(mark)) {
                        File f = resolveMarkerEntry(line, scriptDir, cdbDir);
                        if (f != null && f.isFile() && f.delete()) {
                            deleted++;
                        }
                    }
                    if (mark.delete()) {
                        deleted++;
                    }
                }
            }
            deleted += pruneMaterializedCopies(scriptDir);
            deleted += pruneDirContents(cdbDir);
            if (deleted > 0) {
                Log.i(TAG, "已回收旧版卡包物化残留 " + deleted + " 项（现改为档案直读）");
            }
        } catch (Throwable t) {
            Log.w(TAG, "清理旧版卡包物化残留失败（不影响档案直读）", t);
        }
    }

    /**
     * 无清单可依的历史残留兜底：删除 {@code <expansions>/script/} 下与任一存活卡包内同名
     * 条目<b>逐字节相同</b>的散 lua（相同⇒必然是当初物化出来的副本）；内容有差异的文件
     * 视为手工调试覆写，保留。返回删除数。
     */
    private static int pruneMaterializedCopies(File scriptDir) {
        File[] loose = scriptDir.listFiles(f -> f.isFile()
                && !f.getName().startsWith(MARK_PREFIX)
                && f.getName().toLowerCase(Locale.ROOT).endsWith(".lua"));
        if (loose == null || loose.length == 0) {
            return 0;
        }
        // 包名升序遍历、后包覆写前包，与物化当年的覆写次序一致
        List<File> packs = collectPacks(scriptDir.getParentFile().getParent());
        Map<String, long[]> baseline = new HashMap<>();
        for (File pack : packs) {
            ZipFile zf = null;
            try {
                zf = new ZipFile(pack);
                Enumeration<? extends ZipEntry> entries = zf.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry e = entries.nextElement();
                    if (e.isDirectory()) {
                        continue;
                    }
                    String rel = scriptRelOf(e.getName());
                    if (rel == null) {
                        continue;
                    }
                    baseline.put(rel, new long[]{e.getSize(), e.getCrc()});
                }
            } catch (IOException ex) {
                // 档案读不到就不据此判定，保守跳过该包
            } finally {
                if (zf != null) {
                    try {
                        zf.close();
                    } catch (IOException ignored) {
                        // ignore
                    }
                }
            }
        }
        int deleted = 0;
        for (File f : loose) {
            long[] ref = baseline.get(f.getName());
            if (ref == null || f.length() != ref[0]) {
                continue;   // 大小都不等，必然不是物化副本
            }
            if (crc32Equals(f, ref[1])) {
                if (f.delete()) {
                    deleted++;
                }
            }
        }
        return deleted;
    }

    /** 逐个读文件算 CRC32 与档案条目比对（一次性迁移，代价可接受）。 */
    private static boolean crc32Equals(File f, long expectedCrc) {
        java.util.zip.CRC32 crc = new java.util.zip.CRC32();
        try (InputStream is = new FileInputStream(f)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = is.read(buf)) > 0) {
                crc.update(buf, 0, n);
            }
        } catch (IOException e) {
            return false;
        }
        return crc.getValue() == expectedCrc;
    }

    /** 清空目录内全部文件（不删目录本身，避免权限/并发问题）；返回删除数。 */
    private static int pruneDirContents(File dir) {
        File[] files = dir.listFiles();
        if (files == null) {
            return 0;
        }
        int n = 0;
        for (File f : files) {
            if (f.isDirectory()) {
                n += pruneDirContents(f);
                if (f.delete()) {
                    n++;
                }
            } else if (f.delete()) {
                n++;
            }
        }
        return n;
    }

    /**
     * 档案条目布局归一：大小写不敏感查找 "script/" 段取尾段（兼容 script/、./script/、
     * 任意前缀 script/ 布局），顶层散 lua（无目录段）直取；其余目录下的文件返回 null。
     */
    private static String scriptRelOf(String name) {
        String path = name.replace('\\', '/');
        while (path.startsWith("./")) {
            path = path.substring(2);
        }
        int idx = indexOfIgnoreCase(path, "script/");
        if (idx >= 0) {
            return path.substring(idx + "script/".length());
        }
        if (path.indexOf('/') < 0 && path.toLowerCase(Locale.ROOT).endsWith(".lua")) {
            return path;
        }
        return null;
    }

    private static int indexOfIgnoreCase(String s, String needle) {
        for (int i = 0; i + needle.length() <= s.length(); i++) {
            if (s.regionMatches(true, i, needle, 0, needle.length())) {
                return i;
            }
        }
        return -1;
    }

    /** 把清单行（s:脚本相对路径 / d:物化 cdb 文件名）解析为物化产物文件。 */
    private static File resolveMarkerEntry(String line, File scriptDir, File cdbDir) {
        if (line.startsWith("s:")) {
            return new File(scriptDir, line.substring(2));
        }
        if (line.startsWith("d:")) {
            return new File(cdbDir, line.substring(2));
        }
        return null;
    }

    /** 读标记全部行（UTF-8，逐行 trim、跳过空行）；缺失/异常返回空列表。 */
    private static List<String> readMarkerLines(File marker) {
        if (!marker.isFile()) {
            return Collections.emptyList();
        }
        List<String> out = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(new FileInputStream(marker), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    out.add(line);
                }
            }
        } catch (IOException e) {
            return Collections.emptyList();
        }
        return out;
    }

    private static String sizeOrMissing(File f) {
        return f.isFile() ? f.length() + "B" : "缺失";
    }

    private static int countOf(List<?> list) {
        return list == null ? 0 : list.size();
    }
}
