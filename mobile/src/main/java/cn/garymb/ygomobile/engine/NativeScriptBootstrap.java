package cn.garymb.ygomobile.engine;

import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.NativeInitOptions;

/**
 * native 决斗引擎（libygoengine.so）的启动引导。
 *
 * <p>JNI 侧（ygo_engine_jni.cpp::engineScriptReader）只认资源根目录下的<b>实体文件</b>：
 * {@code root/script/*.lua} 与 {@code root/expansions/script/*.lua}（对齐 C++ gframe 以
 * spmemvfs 挂载 scripts.zip 后的目录语义）。而设备资源目录默认只有打包的 scripts.zip，
 * 因此初始化前必须先把 zip 解压出 script/ 目录，否则 ocgcore 读不到 init.lua 与卡片效果
 * 脚本——表现为卡片无法发动效果（局域网对战）与回放无法重跑引擎。
 *
 * <p>局域网主机（LanGameServer.bootstrapEngine）与旧格式录像回放（EngineReplaySource）均经
 * {@link #ensureEngineReady()} 完成"解压脚本 → OcgDuelEngine.init"引导。
 */
public final class NativeScriptBootstrap {
    private static final String TAG = "NativeScriptBootstrap";

    /** 已成功 init 的资源根目录；相同路径重复调用直接放行（引擎全局状态幂等）。 */
    private static volatile String sInitedRootPath;

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
            NativeInitOptions options = AppsSettings.get().getNativeInitOptions();
            String rootPath = options.mWorkPath;
            ensureScriptsExtracted(rootPath);
            if (rootPath.equals(sInitedRootPath)) {
                return true;
            }
            String[] cdbPaths = options.mDbList.toArray(new String[0]);
            if (OcgDuelEngine.init(rootPath, cdbPaths)) {
                sInitedRootPath = rootPath;
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
                    byte[] buf = new byte[8192];
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

    private static String readMarker(File marker) {
        if (!marker.isFile()) {
            return null;
        }
        try (FileInputStream fis = new FileInputStream(marker)) {
            byte[] buf = new byte[(int) Math.min(marker.length(), 128)];
            int n = fis.read(buf);
            return n <= 0 ? null : new String(buf, 0, n, StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            return null;
        }
    }

    private static void writeMarker(File marker, String fingerprint) {
        File parent = marker.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            return;
        }
        try (OutputStream os = new FileOutputStream(marker)) {
            os.write(fingerprint.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            Log.w(TAG, "写入脚本解压标记失败", e);
        }
    }
}
