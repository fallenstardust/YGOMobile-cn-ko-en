package cn.garymb.ygomobile.engine;

import android.content.Context;
import android.os.SystemClock;
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
import cn.garymb.ygomobile.utils.CrashHandler;
import cn.garymb.ygomobile.utils.IOUtils;

/**
 * native 决斗引擎（libygoengine.so）的启动引导：脚本与卡包物化 + 引擎初始化。
 *
 * <p>JNI 侧（ygo_engine_jni.cpp::engineScriptReader）的卡片脚本取法（对齐 C++
 * data_manager.cpp::ScriptReaderEx 在 prefer_expansion_script=1 时的“expansions 散文件
 * ＞ 卡包档案 ＞ 主脚本”语义）：① {@code <root>/expansions/script/*.lua}（散调试文件与
 * 已物化的卡包脚本）② {@code <root>/scripts.zip}（主脚本档案：native 扫一遍中央目录建
 * “文件名→偏移/长度/方法”索引，按需 pread+inflate 就地取，不再落地解压出上万个 lua）
 * ③ {@code <root>/script/*.lua}（仅作老安装残留/手工调试的兜底）；
 * 卡片数据库仅认传入的实体 cdb 路径列表，按“优先级从低到高、后加载覆盖”装载（对齐
 * game.cpp 先 LoadDB(mDbList) 后 LoadExpansions() 的净效果：扩展覆盖主库同名卡）。
 *
 * <p>由此，资源根目录（ygocore/）与 expansions/ 下的每张卡包压缩包（.zip/.ypk）
 * 必须物化两样东西，否则扩展包卡片在主卡片数据/脚本完备时仍会因引擎侧
 * <b>既无卡片数据（type=0 无法召唤）又无脚本（无法发动效果）</b>而只能盖放：
 * <ul>
 *   <li>包内 script/ 的 cXXX.lua → {@code <root>/expansions/script/}；</li>
 *   <li>包内全部 *.cdb → {@code <root>/expansions/cdb/<包名>_<序号>.cdb}，由
 *       {@code AppsSettings#makeCdbList} 纳入引擎列表。</li>
 * </ul>
 * 同种数据源的统一读取优先级（高→低）：expansions 散文件 ＞ 卡包 zip/ypk
 *（文件名字典序，靠后的包覆盖靠前的包）＞ 主资源档案（cards.cdb / pics.zip /
 * scripts.zip）＞ 主散目录，cdb/script/pics 三类一致（参考 game.cpp、data_manager.cpp、
 * image_manager.cpp）。
 *
 * <p>局域网主机（LanGameServer 监听就绪后的后台引导）与旧格式录像回放（EngineReplaySource）
 * 均经 {@link #ensureEngineReady()} 完成“物化卡包 → OcgDuelEngine.init”引导；主脚本不再
 * 解压落地，Java 只负责把 {@code scripts.zip} 拷到资源根目录（{@link #copyScriptsZipFromAssets}），
 * 档案内容变化时 native 侧按 size+mtime 自查重建索引；旧格式回放、MSG_RETRY 重派与开局
 * 因此必然读同一份档案字节，不存在两套脚本版本；卡包增删/变更后 cdb 列表变化时热重载
 * （{@code reloadCards}），无需重启应用。
 */
public final class NativeScriptBootstrap {
    private static final String TAG = "NativeScriptBootstrap";

    /** 已成功 init 的资源根目录；相同路径重复调用直接放行（引擎全局状态幂等）。 */
    private static volatile String sInitedRootPath;
    /** 上次 init/reloadCards 实际使用的 cdb 列表，用于检测卡包物化后的列表变化。 */
    private static volatile String[] sInitedCdbPaths = new String[0];

    /** 卡包物化标记的版本前缀：标记内容=本行指纹+该包历次解出的文件清单；
     * 旧版单行标记（无前缀）指纹一致时走快速路径：脚本只记清单不重写盘，仅补物化
     * cdb；指纹不符或无标记才全量重解。 */
    private static final String PACK_MARK_PREFIX = "packv2 ";

    private NativeScriptBootstrap() {
    }

    /**
     * 引导引擎：主脚本走 {@code scripts.zip} 直读（无需落地），物化卡包后调用
     * {@link OcgDuelEngine#init}。
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
            final String extractRoot = rootPath;
            long t0 = SystemClock.uptimeMillis();
            // 主脚本已不需解压，本处只物化卡包；档案拷贝晚于本次 init 也没关系，
            // native 侧每次读脚本都按 size+mtime 自查索引
            // cdb 列表须在物化完成后解算（makeCdbList 扫描物化产物目录）
            try {
                // 扩展卡包物化：ygocore/ 与 expansions/ 下全部 .zip/.ypk 的 script/*.lua →
                // <root>/expansions/script/，包内 *.cdb → <root>/expansions/cdb/；否则
                // 卡包卡片因引擎无数据无脚本只能盖放（见类注释）
                ensureExpansionScriptsExtracted(extractRoot);
            } catch (Throwable t) {
                Log.e(TAG, "卡包物化异常", t);
            }
            long t1 = SystemClock.uptimeMillis();
            NativeInitOptions options = AppsSettings.get().getNativeInitOptions();
            rootPath = options.mWorkPath;
            String[] cdbPaths = options.mDbList.toArray(new String[0]);
            long t2 = SystemClock.uptimeMillis();
            // 诊断：脚本三档来源的当前态——卡包物化区、主脚本档案、老安装残留实体目录
            File mainScriptDir = new File(rootPath, Constants.CORE_SCRIPT_PATH);
            String[] mainScriptFiles = mainScriptDir.list();
            File scriptsZip = new File(rootPath, Constants.CORE_SCRIPTS_ZIP);
            Log.i(TAG, "engine bootstrap root=" + rootPath
                    + " scripts.zip=" + (scriptsZip.isFile() ? scriptsZip.length() + "B" : "缺失")
                    + " 残留script/条目=" + (mainScriptFiles == null ? 0 : mainScriptFiles.length)
                    + " expansions/script/init.lua exists=" + new File(new File(rootPath,
                    Constants.CORE_EXPANSIONS + "/" + Constants.CORE_SCRIPT_PATH),
                    "init.lua").exists()
                    + " packcdb=" + listPackCdbCount(rootPath));
            // 分段耗时：开局卡顿定位用（预热未完成时 startDuel 闸门会同步等到这里全部结束）
            Log.i(TAG, "engine bootstrap 耗时(ms) 卡包物化=" + (t1 - t0)
                    + " 选项解算=" + (t2 - t1));
            if (rootPath.equals(sInitedRootPath)) {
                // 已初始化：卡包增删/变更导致 cdb 列表变化时热重载引擎卡数据
                if (!Arrays.equals(sInitedCdbPaths, cdbPaths)) {
                    long r0 = SystemClock.uptimeMillis();
                    OcgDuelEngine.reloadCards(cdbPaths);
                    sInitedCdbPaths = cdbPaths;
                    Log.i(TAG, "卡片数据库列表变化，热重载引擎卡数据，耗时="
                            + (SystemClock.uptimeMillis() - r0) + "ms");
                }
                return true;
            }
            long i0 = SystemClock.uptimeMillis();
            if (OcgDuelEngine.init(rootPath, cdbPaths)) {
                Log.i(TAG, "引擎 init 完成，耗时=" + (SystemClock.uptimeMillis() - i0)
                        + "ms cdb数=" + cdbPaths.length);
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
     * 后台预热入口：在创建时刻立即起子线程执行 {@link #ensureEngineReady()}
     *（物化卡包 → cdb 加载），把开局闸门需要等待的引导
     * 全量提前到进程/界面创建时刻，用后续全部自然操作耗时（资源检查/导航/
     * 建房/猜拳/选先后攻）覆盖引导总时长。瞬态失败（如首次安装时资源目录
     * 尚未就绪）每 3 秒重试一次、至多 3 次；native 库缺失（非 ARM ABI/未打包）
     * 属永久不可用不重试。ensureEngineReady 同步幂等，多个预热点（App 创建/
     * YGOProActivity 创建/建主引导）并发时串行合并互不重复；失败不阻断任何
     * 流程，开局闸门仍会作最后一次同步引导。
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
     * 主脚本由 native 直接读这个档案，不再解压成 {@code script/} 下的散文件。
     *
     * @param update true 才做“内容变了就重写”；false 时目标存在就不动（正常启动靠它零开销）
     */
    public static void copyScriptsZipFromAssets(Context context, String assetZip, File resDir,
            boolean update, IOUtils.CopyProgress progress) throws IOException {
        IOUtils.copyFileFromAssetsIfChanged(context, assetZip, resDir, update, progress);
    }

    private static String zipFingerprint(File zip) {
        return zip.length() + ":" + zip.lastModified();
    }

    /**
     * 档案内与决斗无关的仓库/系统垃圾条目：{@code .git/}、{@code .gitignore}、
     * {@code .gitattributes}、{@code .DS_Store}、{@code Thumbs.db}、{@code desktop.ini}、
     * {@code __MACOSX/}。卡包物化时统一跳过，不落盘也不计入物化清单。
     */
    private static boolean isJunkEntry(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.contains(".git/")
                || lower.contains("__macosx/")
                || lower.endsWith("/.gitignore")
                || lower.equals(".gitignore")
                || lower.endsWith("/.gitattributes")
                || lower.equals(".gitattributes")
                || lower.endsWith("/.ds_store")
                || lower.equals(".ds_store")
                || lower.endsWith("thumbs.db")
                || lower.endsWith("desktop.ini");
    }

    /**
     * 扩展卡包物化引导（修复卡包卡片"只能盖放"的关键）：薄 JNI 引擎只认<b>实体文件</b>——
     * 卡片脚本走 engineScriptReader 的三档查找（{@code <root>/expansions/script/} 优先，
     * 对齐 data_manager.cpp::ScriptReaderEx 的 prefer_expansion_script=1），卡片数据走
     * nativeInit/reloadCards 的实体 cdb 路径列表。因此资源根目录（ygocore/）与
     * expansions/ 下的每张卡包压缩包（.zip/.ypk）必须物化两样东西：
     * <ul>
     *   <li>包内 script/ 的 cXXX.lua（及顶层散 lua）→ {@code <root>/expansions/script/}，
     *       否则卡包卡片效果脚本对引擎不可见（无法召唤/发动）；</li>
     *   <li>包内全部 *.cdb → {@code <root>/expansions/cdb/<包名>_<序号>.cdb}，由
     *       {@code AppsSettings#makeCdbList} 按"主库→包 cdb→散 cdb"
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
     * {@link #pruneOrphanPacks} 据孤儿标记整体清理。旧版单行指纹标记若指纹一致
     * 则脚本走快速路径（只记清单不重写盘，仅补物化 cdb），避免升级首轮全量重解
     * 卡住开局；指纹不符或无标记才整体重解。
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

    /** 收集目录下的卡包压缩包（.zip/.ypk）。资源根目录的 {@code scripts.zip} 是主脚本档案
     * （native 直读），必须排除，否则会被当卡包物化到 expansions/script 而抢在主线脚本之前。 */
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
     * 按名升序即按包字典序）。逐包 v2 标记命中指纹则整体跳过；旧版单行标记指纹
     * 一致时脚本走快速路径（盘上已存在则不重写）；成功后按新清单覆写
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
        // v1 快速路径：旧版单行标记且指纹一致（脚本已按同包内容全量解出）时，
        // lua 条目盘上存在则只记入清单不重写，仅补物化 cdb——消除升级后首轮
        // 全量重解所有包脚本阻塞 startDuel 闸门、猜拳后发牌卡数秒的问题
        boolean scriptsAssumedDone = oldLines.size() == 1
                && !oldLines.get(0).startsWith(PACK_MARK_PREFIX)
                && oldLines.get(0).equals(zipFingerprint(pack));
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
                if (isJunkEntry(name)) {
                    continue;
                }
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
                    if (scriptsAssumedDone) {
                        File done = new File(scriptOut, rel);
                        if (done.isFile()) {
                            if (seen.add(line)) {
                                newLines.add(line);
                            }
                            continue;
                        }
                    }
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
