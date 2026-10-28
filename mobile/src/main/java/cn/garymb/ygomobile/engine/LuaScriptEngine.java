package cn.garymb.ygomobile.engine;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.utils.IOUtils;

public class LuaScriptEngine {
    private static final String TAG = "LuaScriptEngine";
    private final Map<String, byte[]> scriptCache = new ConcurrentHashMap<>();
    private ZipFile scriptsZip;
    private boolean initialized = false;

    private static LuaScriptEngine instance;

    public static LuaScriptEngine get() {
        if (instance == null) {
            synchronized (LuaScriptEngine.class) {
                if (instance == null) {
                    instance = new LuaScriptEngine();
                }
            }
        }
        return instance;
    }

    private LuaScriptEngine() {
    }

    public void init() {
        if (initialized) return;
        String resourcePath = AppsSettings.get().getResourcePath();
        File zipFile = new File(resourcePath, Constants.CORE_SCRIPTS_ZIP);
        if (zipFile.exists()) {
            try {
                scriptsZip = new ZipFile(zipFile);
                Log.i(TAG, "scripts.zip loaded: " + zipFile.getAbsolutePath());
            } catch (IOException e) {
                Log.e(TAG, "Failed to open scripts.zip", e);
            }
        }
        initialized = true;
    }

    public byte[] loadScript(int cardCode) {
        String scriptName = "c" + cardCode + ".lua";
        return loadScriptByName(scriptName);
    }

    public byte[] loadSingleScript(String fileName) {
        return loadScriptByName(fileName);
    }

    /**
     * 按统一优先级加载脚本（高→低），与 JNI 侧 engineScriptReader、cdb/pics 保持一致：
     * <ol>
     *   <li>{@code <root>/expansions/script/} 散文件——含 NativeScriptBootstrap 物化的卡包
     *       cXXX.lua 与 expansions 调试散文件（最高，对齐 data_manager.cpp
     *       prefer_expansion_script=1）；</li>
     *   <li>expansions 目录内卡包 zip/ypk（按文件名倒序遍历，靠后名包优先）；</li>
     *   <li>主资源 scripts.zip；</li>
     *   <li>主散目录 {@code <root>/script/}、{@code <root>/single/}（最低）。</li>
     * </ol>
     */
    public byte[] loadScriptByName(String name) {
        if (scriptCache.containsKey(name)) {
            return scriptCache.get(name);
        }

        byte[] data;

        //1.expansions/script 散文件（卡包脚本物化区，最高优先级）
        data = loadFromExpansionsScriptDir(name);
        if (data != null) {
            scriptCache.put(name, data);
            return data;
        }

        //2.expansions 目录内卡包 zip/ypk（倒序）
        data = loadFromExpansions(name);
        if (data != null) {
            scriptCache.put(name, data);
            return data;
        }

        //3.主资源 scripts.zip
        data = loadFromZip(name);
        if (data != null) {
            scriptCache.put(name, data);
            return data;
        }

        //4.主散目录 script/、single/
        data = loadFromFile(name);
        if (data != null) {
            scriptCache.put(name, data);
            return data;
        }

        Log.w(TAG, "Script not found: " + name);
        return null;
    }

    /**
     * 读取 {@code <root>/expansions/script/} 下的散 lua（由 {@link NativeScriptBootstrap}
     * 把卡包 script/*.lua 与顶层散 lua 按包名升序覆写物化而来，已体现“靠后名包优先”，
     * 此处只需直接查文件）。未物化时目录不存在，返回 null。
     */
    private byte[] loadFromExpansionsScriptDir(String name) {
        String resourcePath = AppsSettings.get().getResourcePath();
        File scriptFile = new File(new File(new File(resourcePath,
                Constants.CORE_EXPANSIONS), Constants.CORE_SCRIPT_PATH), name);
        if (!scriptFile.isFile()) {
            return null;
        }
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(scriptFile);
            return readStream(fis);
        } catch (IOException e) {
            Log.e(TAG, "Failed to read expansion script file: " + scriptFile.getAbsolutePath(), e);
        } finally {
            IOUtils.close(fis);
        }
        return null;
    }

    private byte[] loadFromExpansions(String name) {
        String resourcePath = AppsSettings.get().getResourcePath();
        File expansionsDir = new File(resourcePath, Constants.CORE_EXPANSIONS);
        if (expansionsDir.exists()) {
            File[] zipFiles = expansionsDir.listFiles(f ->
                    f.isFile() && (f.getName().endsWith(".zip") || f.getName().endsWith(".ypk")));
            if (zipFiles != null) {
                //按文件名升序排序后倒序遍历：字典序靠后的包优先（与统一优先级一致）
                Arrays.sort(zipFiles, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
                for (int i = zipFiles.length - 1; i >= 0; i--) {
                    File zipFile = zipFiles[i];
                    ZipFile zf = null;
                    try {
                        zf = new ZipFile(zipFile);
                        ZipEntry entry = zf.getEntry("script/" + name);
                        if (entry == null) {
                            entry = zf.getEntry(name);
                        }
                        if (entry != null) {
                            return readStream(zf.getInputStream(entry));
                        }
                    } catch (IOException e) {
                        // ignore
                    } finally {
                        if (zf != null) {
                            try { zf.close(); } catch (IOException e) { /* ignore */ }
                        }
                    }
                }
            }
        }
        return null;
    }

    private byte[] loadFromFile(String name) {
        String resourcePath = AppsSettings.get().getResourcePath();
        File scriptFile = new File(resourcePath + "/" + Constants.CORE_SCRIPT_PATH, name);
        if (scriptFile.exists()) {
            FileInputStream fis = null;
            try {
                fis = new FileInputStream(scriptFile);
                return readStream(fis);
            } catch (IOException e) {
                Log.e(TAG, "Failed to read script file: " + scriptFile.getAbsolutePath(), e);
            } finally {
                IOUtils.close(fis);
            }
        }

        File singleFile = new File(resourcePath + "/" + Constants.CORE_SINGLE_PATH, name);
        if (singleFile.exists()) {
            FileInputStream fis = null;
            try {
                fis = new FileInputStream(singleFile);
                return readStream(fis);
            } catch (IOException e) {
                Log.e(TAG, "Failed to read single file: " + singleFile.getAbsolutePath(), e);
            } finally {
                IOUtils.close(fis);
            }
        }
        return null;
    }

    private byte[] loadFromZip(String name) {
        if (scriptsZip == null) return null;
        InputStream is = null;
        try {
            ZipEntry entry = scriptsZip.getEntry("script/" + name);
            if (entry == null) {
                entry = scriptsZip.getEntry(name);
            }
            if (entry != null) {
                is = scriptsZip.getInputStream(entry);
                return readStream(is);
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to read script from zip: " + name, e);
        } finally {
            IOUtils.close(is);
        }
        return null;
    }

    private byte[] readStream(InputStream is) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int len;
        while ((len = is.read(buffer)) != -1) {
            baos.write(buffer, 0, len);
        }
        return baos.toByteArray();
    }

    public boolean hasScript(int cardCode) {
        return loadScript(cardCode) != null;
    }

    public void clearCache() {
        scriptCache.clear();
    }

    public void release() {
        clearCache();
        if (scriptsZip != null) {
            try {
                scriptsZip.close();
            } catch (IOException e) {
                // ignore
            }
            scriptsZip = null;
        }
        initialized = false;
    }
}
