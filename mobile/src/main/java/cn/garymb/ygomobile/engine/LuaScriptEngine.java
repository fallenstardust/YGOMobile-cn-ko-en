package cn.garymb.ygomobile.engine;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
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

    public byte[] loadScriptByName(String name) {
        if (scriptCache.containsKey(name)) {
            return scriptCache.get(name);
        }

        byte[] data = null;

        data = loadFromExpansions(name);
        if (data != null) {
            scriptCache.put(name, data);
            return data;
        }

        data = loadFromFile(name);
        if (data != null) {
            scriptCache.put(name, data);
            return data;
        }

        data = loadFromZip(name);
        if (data != null) {
            scriptCache.put(name, data);
            return data;
        }

        Log.w(TAG, "Script not found: " + name);
        return null;
    }

    private byte[] loadFromExpansions(String name) {
        String resourcePath = AppsSettings.get().getResourcePath();
        File expansionsDir = new File(resourcePath, Constants.CORE_EXPANSIONS);
        if (expansionsDir.exists()) {
            File[] zipFiles = expansionsDir.listFiles(f ->
                    f.isFile() && (f.getName().endsWith(".zip") || f.getName().endsWith(".ypk")));
            if (zipFiles != null) {
                for (File zipFile : zipFiles) {
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
