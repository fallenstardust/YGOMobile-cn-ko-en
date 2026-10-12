package cn.garymb.ygomobile.engine;

import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.NativeInitOptions;

/**
 * native 决斗引擎（libygoengine.so）的启动引导：脚本与卡包物化 + 引擎初始化。
 *
 * <p>JNI 侧（ygo_engine_jni.cpp::engineScriptReader）只认资源根目录下的<b>实体文件</b>：
 * 卡片脚本优先 {@code root/expansions/script/*.lua}，其次 {@code root/script/*.lua}
 *（对齐 C++ data_manager.cpp::ScriptReaderEx 在 prefer_expansion_script=1 时的
 * “expansions 散文件 > 卡包档案 > 主脚本”语义）；卡片数据库仅认传入的实体 cdb
 * 路径列表，按“优先级从低到高、后加载覆盖”装载（对齐 game.cpp 先 LoadDB(mDbList)
 * 后 LoadExpansions() 的净效果：扩展覆盖主库同名卡）。
 *
 * <p>由此，资源根目录（ygocore/）与 expansions/ 下的每张卡包压缩包（.zip/.ypk）
 * 必须物化两样东西，否则扩展包卡片在主卡片数据/脚本完备时仍会因引擎侧
 * <b>既无卡片数据（type=0 无法召唤）又无脚本（无法发动效果）</b>而只能盖放：
 * <ul>
 *   <li>包内 script/ 的 cXXX.lua → {@code <root>/expansions/script/}；</li>
 *   <li>包内全部 *.cdb → {@code <root>/expansions/cdb/<包名>_<序号>.cdb}，由
 *       {@link cn.garymb.ygomobile.AppsSettings#makeCdbList} 纳入引擎列表。</li>
 * </ul>
 * 同种数据源的统一读取优先级（高→低）：expansions 散文件 ＞ 卡包 zip/ypk
 *（文件名字典序，靠后的包覆盖靠前的包）＞ 主资源（cards.cdb / scripts.zip / pics.zip）
 * ＞ 主散目录，cdb/script/pics 三类一致（参考 game.cpp、data_manager.cpp、
 * image_manager.cpp）。
 *
 * <p>局域网主机（LanGameServer 监听就绪后的后台引导）与旧格式录像回放（EngineReplaySource）
 * 均经 {@link #ensureEngineReady()} 完成“解压脚本 → 物化卡包 → OcgDuelEngine.init”引导；
 * 卡包增删/变更后 cdb 列表变化时热重载（{@code reloadCards}），无需重启应用。
 */
public final class NativeScriptBootstrap {
    private static final String TAG = "NativeScriptBootstrap";

    /** 已成功 init 的资源根目录；相同路径重复调用直接放行（引擎全局状态幂等）。 */
    private static volatile String sInitedRootPath;
    /** 上次 init/reloadCards 实际使用的 cdb 列表，用于检测卡包物化后的列表变化。 */
    private static volatile String[] sInitedCdbPaths = new String[0];

    /** 卡包物化标记的版本前缀：标记内容=本行指纹+该包历次解出的文件清单，
     * 旧版单行标记（无前缀）视为未解压，自动重解一次以补齐 cdb 并启用清单清理。 */
    private static final String PACK_MARK_PREFIX = "packv2 ";

    private NativeScriptBootstrap() {
    }

    /**
     * 引导引擎：确保 script/ 目录已就绪后调用 {@link OcgDuelEngine#init}。
     *
     * @return 引擎可用且初始化成功返回 true
     */
    public static synchronized boolean ensureEngineReady() {
        if (!OcgDuelEngine.isAvailable()) {
            Log.e(TAG, "libygoengine.so 不可用");
            return false;
        }
        try {
            // 先物化再取 NativeInitOptions：makeCdbList 要扫描卡包物化产物目录
            // <expansions>/cdb/，顺序颠倒会漏掉本轮新增的卡包数据库
            String rootPath = AppsSettings.get().getResourcePath();
            ensureScriptsExtracted(rootPath);
            // 扩展卡包物化：ygocore/ 与 expansions/ 下全部 .zip/.ypk 的 script/*.lua →
            // <root>/expansions/script/，包内 *.cdb → <root>/expansions/cdb/；否则
            // 卡包卡片因引擎无数据无脚本只能盖放（见类注释）
            ensureExpansionScriptsExtracted(rootPath);
            NativeInitOptions options = AppsSettings.get().getNativeInitOptions();
            rootPath = options.mWorkPath;
            String[] cdbPaths = options.mDbList.toArray(new String[0]);
            // 诊断：内置引擎（LAN 建主/残局/旧格式回放）的真实脚本根目录——卡片 lua 脚本
            // 优先读 <root>/expansions/script/（散调试文件+卡包物化区），其次 <root>/script/
            //（主，scripts.zip 解出）；scripts.zip 指纹变化时 ensureScriptsExtracted 会
            // 用 zip 内容覆写主脚本区（手改主脚本会被冲掉）
            Log.i(TAG, "engine bootstrap root=" + rootPath
                    + " script/init.lua exists=" + new File(new File(rootPath,
                    Constants.CORE_SCRIPT_PATH), "init.lua").exists()
                    + " expansions/script/init.lua exists=" + new File(new File(rootPath,
                    Constants.CORE_EXPANSIONS + "/" + Constants.CORE_SCRIPT_PATH),
                    "init.lua").exists()
                    + " packcdb=" + listPackCdbCount(rootPath)
                    + " scripts.zip exists=" + new File(rootPath,
                    Constants.CORE_SCRIPTS_ZIP).exists());
            if (rootPath.equals(sInitedRootPath)) {
                // 已初始化：卡包增删/变更导致 cdb 列表变化时热重载引擎卡数据
                if (!Arrays.equals(sInitedCdbPaths, cdbPaths)) {
                    Log.i(TAG, "卡片数据库列表变化，热重载引擎卡数据");
                    OcgDuelEngine.reloadCards(cdbPaths);
                    sInitedCdbPaths = cdbPaths;
                }
                return true;
            }
            if (OcgDuelEngine.init(rootPath, cdbPaths)) {
                sInitedRootPath = rootPath;
                sInitedCdbPaths = cdbPaths;
                return true;
            }
            Log.e(TAG, "OcgDuelEngine.init 失败: " + rootPath);
            return false;
        } catch (Throwable t) {
            Log.e(TAG, "引擎引导异常", t);
            return false;
        }
    }

    /** 诊断用：已物化的卡包 cdb 个数（不改变任何状态）。 */
    private static int listPackCdbCount(String resourcePath) {
        File[] fs = new File(new File(new File(resourcePath, Constants.CORE_EXPANSIONS),
                Constants.CORE_PACK_CDB_DIR), "").listFiles(
                f -> f.isFile() && f.getName().toLowerCase(Locale.ROOT).endsWith(".cdb"));
        return fs == null ? 0 : fs.length;
    }

    /**
     * 资源根目录缺少 {@code script/init.lua}，或 {@code scripts.zip} 自上次解压后已更新
     * （比对 大小+lastModified 指纹标记）时，把 zip 重新解压到资源根目录（zip 条目自带
     * script/ 、single/ 前缀，直接按相对路径覆写落盘）。
     *
     * <p>必须比对指纹而不能只看 init.lua 存在：JNI 侧 engineScriptReader 只认实体文件，而
     * C++ gframe 的 ScriptReaderEx 优先读 zip 挂载——资源升级后若不解压更新，Java 引擎重跑
     * 用的是陈旧脚本、行为与录制时代分叉，是旧格式回放中途 MSG_RETRY 的根因之一。
     */
    public static void ensureScriptsExtracted(String resourcePath) {
        if (resourcePath == null || resourcePath.isEmpty()) {
            return;
        }
        File root = new File(resourcePath);
        File scriptDir = new File(root, Constants.CORE_SCRIPT_PATH);
        File initLua = new File(scriptDir, "init.lua");
        File zip = new File(root, Constants.CORE_SCRIPTS_ZIP);
        if (!zip.exists()) {
            if (!initLua.exists()) {
                Log.w(TAG, "scripts.zip 不存在，无法解压脚本目录: " + zip.getAbsolutePath());
            }
            return;
        }
        File marker = new File(scriptDir, ".scripts_zip_mark");
        if (initLua.exists() && zipFingerprint(zip).equals(readMarker(marker))) {
            return;
        }
        ZipFile zf = null;
        boolean ok = false;
        try {
            zf = new ZipFile(zip);
            String rootCanonical = root.getCanonicalPath();
            Enumeration<? extends ZipEntry> entries = zf.entries();
            int count = 0;
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                File out = new File(root, entry.getName());
                // 防 zip slip：目标必须落在资源根目录内
                if (!out.getCanonicalPath().startsWith(rootCanonical + File.separator)) {
                    Log.w(TAG, "跳过非法条目: " + entry.getName());
                    continue;
                }
                File parent = out.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    continue;
                }
                try (InputStream is = zf.getInputStream(entry);
                        OutputStream os = new FileOutputStream(out)) {
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = is.read(buf)) > 0) {
                        os.write(buf, 0, n);
                    }
                }
                count++;
            }
            ok = true;
            Log.i(TAG, "scripts.zip 解压完成，共 " + count + " 个文件 → " + rootCanonical);
        } catch (IOException e) {
            Log.e(TAG, "解压 scripts.zip 失败", e);
        } finally {
            if (zf != null) {
                try {
                    zf.close();
                } catch (IOException e) {
                    // ignore
                }
            }
            if (ok) {
                writeMarker(marker, zipFingerprint(zip));   // 指纹未落盘则下次仍重解，自愈
            }
        }
    }

    private static String zipFingerprint(File zip) {
        return zip.length() + ":" + zip.lastModified();
    }

    /**
     * 扩展卡包物化引导（修复卡包卡片"只能盖放"的关键）：薄 JNI 引擎只认<b>实体文件</b>——
     * 脚本走 engineScriptReader 的两级查找（{@code <root>/expansions/script/} 优先，
     * 对齐 data_manager.cpp::ScriptReaderEx 的 prefer_expansion_script=1），卡片数据走
     * nativeInit/reloadCards 的实体 cdb 路径列表。因此资源根目录（ygocore/）与
     * expansions/ 下的每张卡包压缩包（.zip/.ypk）必须物化两样东西：
     * <ul>
     *   <li>包内 script/ 的 cXXX.lua（及顶层散 lua）→ {@code <root>/expansions/script/}，
     *       否则卡包卡片效果脚本对引擎不可见（无法召唤/发动）；</li>
     *   <li>包内全部 *.cdb → {@code <root>/expansions/cdb/<包名>_<序号>.cdb}，由
     *       {@link cn.garymb.ygomobile.AppsSettings#makeCdbList} 按"主库→包 cdb→散 cdb"
     *       低→高优先级纳入引擎——此前包内 cdb 从未进引擎，卡片 type=0 被规则层
     *       拒绝召唤/发动，是只解脚本仍修不好"只能盖放"的根因。</li>
     * </ul>
     *
     * <p>包间优先级：按包文件名字典序<b>升序</b>迭代、后包直接覆写前包同名产物，
     * 即"靠后的包优先"——与 makeZipList（倒序挂载档案）/ImageLoader（倒序查包）/
     * CardManager（升序后读覆盖）统一为同一优先级体系：expansions 散文件 ＞
     * 卡包 zip/ypk（后名优先）＞ 主资源 ＞ 主散目录。
     *
     * <p>逐包物化标记落在 {@code <expansions>/script/.mark_<包名>}：首行
     * {@code packv2 + 大小:lastModified 指纹}（未变更整体跳过），其后为该包历次解出的
     * 文件清单（s:脚本相对路径 / d:物化 cdb 文件名），包内容变更时按清单删除已
     * 消失的旧产物、包被删除（或 {@code isReadExpansions} 关闭）时由
     * {@link #pruneOrphanPacks} 据孤儿标记整体清理。旧版单行指纹标记因无 packv2
     * 前缀视为未解压，自动重解一次以补齐 cdb 并启用清单清理。
     */
    public static void ensureExpansionScriptsExtracted(String resourcePath) {
        if (resourcePath == null || resourcePath.isEmpty()) {
            return;
        }
        File root = new File(resourcePath);
        File expDir = new File(root, Constants.CORE_EXPANSIONS);
        File scriptOut = new File(expDir, Constants.CORE_SCRIPT_PATH);
        File cdbOut = new File(expDir, Constants.CORE_PACK_CDB_DIR);
        List<File> archives = new ArrayList<>();
        if (AppsSettings.get().isReadExpansions()) {
            collectScriptArchives(root, archives);
            collectScriptArchives(expDir, archives);
            archives.sort((a, b) -> a.getName().toLowerCase(Locale.ROOT)
                    .compareTo(b.getName().toLowerCase(Locale.ROOT)));
        }
        // 已删除的包 / 关闭读取扩展：先按标记清单清掉孤儿物化产物（空列表=全清）
        int pruned = pruneOrphanPacks(archives, scriptOut, cdbOut);
        int lua = 0;
        int cdb = 0;
        for (File pack : archives) {
            int[] n = extractPackContents(pack, scriptOut, cdbOut);
            lua += n[0];
            cdb += n[1];
        }
        if (lua + cdb + pruned > 0) {
            Log.i(TAG, "扩展卡包物化完成：" + lua + " 个 lua、" + cdb + " 个 cdb、"
                    + pruned + " 项清理 → " + expDir.getAbsolutePath());
        }
    }

    /** 收集目录下的卡包压缩包（.zip/.ypk，排除主脚本包 scripts.zip 与非普通文件）。 */
    private static void collectScriptArchives(File dir, List<File> out) {
        if (dir == null || !dir.isDirectory()) {
            return;
        }
        File[] files = dir.listFiles(f -> f.isFile()
                && !Constants.CORE_SCRIPTS_ZIP.equalsIgnoreCase(f.getName())
                && (f.getName().toLowerCase(Locale.ROOT).endsWith(".zip")
                || f.getName().toLowerCase(Locale.ROOT).endsWith(".ypk")));
        if (files != null) {
            for (File f : files) {
                out.add(f);
            }
        }
    }

    /**
     * 物化单个卡包：.lua 条目（script/ 前缀或顶层散 lua，经 {@link #scriptRelOf} 归一）
     * 解到 scriptOut，全部 *.cdb 条目解到 cdbOut（命名 {@code <包名>_<序号>.cdb}，保证
     * 按名升序即按包字典序）。逐包 v2 标记命中指纹则整体跳过；成功后按新清单覆写
     * 标记，并删除旧清单中本次不再生成的产物（包内文件被移除时同步清理）。
     *
     * @return {@code {本次落盘 lua 数, 本次落盘 cdb 数}}，跳过/失败时为 {0, 0}
     */
    private static int[] extractPackContents(File pack, File scriptOut, File cdbOut) {
        int[] ret = new int[2];
        if ((!scriptOut.exists() && !scriptOut.mkdirs())
                || (!cdbOut.exists() && !cdbOut.mkdirs())) {
            Log.w(TAG, "无法创建卡包物化目录: " + scriptOut.getAbsolutePath() + " / "
                    + cdbOut.getAbsolutePath());
            return ret;
        }
        File marker = new File(scriptOut, ".mark_" + pack.getName());
        List<String> oldLines = readMarkerLines(marker);
        String fpLine = PACK_MARK_PREFIX + zipFingerprint(pack);
        if (!oldLines.isEmpty() && fpLine.equals(oldLines.get(0))) {
            return ret;
        }
        List<String> newLines = new ArrayList<>();
        newLines.add(fpLine);
        Set<String> seen = new HashSet<>();
        int luaCount = 0;
        int cdbCount = 0;
        int cdbIdx = 0;
        ZipFile zf = null;
        boolean ok = false;
        try {
            zf = new ZipFile(pack);
            String scriptCanonical = scriptOut.getCanonicalPath();
            String cdbCanonical = cdbOut.getCanonicalPath();
            Enumeration<? extends ZipEntry> entries = zf.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName().replace('\\', '/');
                String lower = name.toLowerCase(Locale.ROOT);
                String line;
                File out;
                String canonicalBase;
                if (lower.endsWith(".lua")) {
                    // 条目布局归一：script/x.lua 与 ./script/x.lua 取尾段、顶层散 x.lua
                    // 直取；其余目录（pics/ 等）嵌套路径一律不入扩展脚本目录
                    String rel = scriptRelOf(name);
                    if (rel == null || rel.isEmpty()) {
                        continue;
                    }
                    line = "s:" + rel;
                    out = new File(scriptOut, rel);
                    canonicalBase = scriptCanonical;
                } else if (lower.endsWith(".cdb")) {
                    // 包内 cdb 全部物化：薄 JNI 引擎只认实体文件，卡片数据缺失
                    // (type=0) 时规则层直接拒绝召唤/发动——这是"只能盖放"的根因
                    line = "d:" + pack.getName() + "_" + (cdbIdx++) + ".cdb";
                    out = new File(cdbOut, line.substring(2));
                    canonicalBase = cdbCanonical;
                } else {
                    continue;
                }
                // 防 zip slip：目标必须落在对应物化目录内
                if (!out.getCanonicalPath().startsWith(canonicalBase + File.separator)) {
                    Log.w(TAG, "跳过非法条目: " + pack.getName() + "!" + name);
                    continue;
                }
                File parent = out.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    continue;
                }
                try (InputStream is = zf.getInputStream(entry);
                        OutputStream os = new FileOutputStream(out)) {
                    byte[] buf = new byte[65536];
                    int n;
                    while ((n = is.read(buf)) > 0) {
                        os.write(buf, 0, n);
                    }
                }
                if (lower.endsWith(".lua")) {
                    luaCount++;
                } else {
                    cdbCount++;
                }
                if (seen.add(line)) {
                    newLines.add(line);
                }
            }
            ok = true;
        } catch (IOException e) {
            Log.e(TAG, "物化扩展卡包失败: " + pack.getAbsolutePath(), e);
        } finally {
            if (zf != null) {
                try {
                    zf.close();
                } catch (IOException e) {
                    // ignore
                }
            }
            if (ok) {
                // 包内被移除的旧产物：按上一版清单删除本次未再生成的条目
                for (String line : oldLines) {
                    if (line.startsWith(PACK_MARK_PREFIX) || newLines.contains(line)) {
                        continue;
                    }
                    File stale = resolveMarkerEntry(line, scriptOut, cdbOut);
                    if (stale != null && stale.isFile() && stale.delete()) {
                        Log.i(TAG, "清理包内已移除产物: " + stale.getName());
                    }
                }
                writeMarkerLines(marker, newLines);   // 标记未落盘则下次仍重解，自愈
                ret[0] = luaCount;
                ret[1] = cdbCount;
            }
        }
        return ret;
    }

    /**
     * 清理孤儿物化产物：物化标记 {@code .mark_<包名>} 对应的卡包已不在存活列表
     * （包被删除 / {@code isReadExpansions} 关闭传入空列表）时，按其清单删除物化
     * 出的脚本与 cdb，再删除标记本身。
     *
     * @return 删除的产物文件数
     */
    private static int pruneOrphanPacks(List<File> alive, File scriptOut, File cdbOut) {
        File[] marks = scriptOut.listFiles(f -> f.isFile()
                && f.getName().startsWith(".mark_")
                && (f.getName().toLowerCase(Locale.ROOT).endsWith(".zip")
                || f.getName().toLowerCase(Locale.ROOT).endsWith(".ypk")));
        if (marks == null || marks.length == 0) {
            return 0;
        }
        Set<String> aliveNames = new HashSet<>();
        for (File f : alive) {
            aliveNames.add(f.getName().toLowerCase(Locale.ROOT));
        }
        int deleted = 0;
        for (File m : marks) {
            String packName = m.getName().substring(".mark_".length());
            if (aliveNames.contains(packName.toLowerCase(Locale.ROOT))) {
                continue;
            }
            for (String line : readMarkerLines(m)) {
                File f = resolveMarkerEntry(line, scriptOut, cdbOut);
                if (f != null && f.isFile() && f.delete()) {
                    deleted++;
                }
            }
            m.delete();
        }
        return deleted;
    }

    /** 把清单行（s:脚本相对路径 / d:物化 cdb 文件名）解析为物化产物文件。 */
    private static File resolveMarkerEntry(String line, File scriptOut, File cdbOut) {
        if (line.startsWith("s:")) {
            return new File(scriptOut, line.substring(2));
        }
        if (line.startsWith("d:")) {
            return new File(cdbOut, line.substring(2));
        }
        return null;
    }

    /**
     * lua 条目路径归一：大小写不敏感查找 "script/" 段取尾段（兼容 script/、./script/、
     * 任意前缀 script/ 布局），顶层散 lua（无目录段）直取；其余目录下的文件返回 null。
     */
    private static String scriptRelOf(String name) {
        int idx = indexOfIgnoreCase(name, "script/");
        if (idx >= 0) {
            return name.substring(idx + "script/".length());
        }
        if (name.indexOf('/') < 0) {
            return name;
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

    private static String readMarker(File marker) {
        List<String> lines = readMarkerLines(marker);
        return lines.isEmpty() ? null : lines.get(0);
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

    private static void writeMarker(File marker, String fingerprint) {
        List<String> lines = new ArrayList<>(1);
        lines.add(fingerprint);
        writeMarkerLines(marker, lines);
    }

    /** 覆写标记全部行（UTF-8，每行一文件）；父目录自动创建。 */
    private static void writeMarkerLines(File marker, List<String> lines) {
        File parent = marker.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            return;
        }
        try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(marker), StandardCharsets.UTF_8))) {
            for (String line : lines) {
                w.write(line);
                w.newLine();
            }
        } catch (IOException e) {
            Log.w(TAG, "写入卡包物化标记失败", e);
        }
    }
}
