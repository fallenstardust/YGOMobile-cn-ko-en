package cn.garymb.ygomobile.utils;


import android.content.Context;
import android.content.res.AssetManager;
import android.os.Build;
import android.text.TextUtils;
import android.util.Log;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Locale;
import java.util.zip.ZipFile;

import cn.garymb.ygomobile.Constants;

public class IOUtils {
    private static final String TAG = "ioUtils";

    public static void close(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void closeZip(ZipFile closeable) {
        if (closeable == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            try {
                closeable.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void delete(File file) {
        if (file == null || !file.exists()) return;
        if (file.isFile()) {
            file.delete();
        } else if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File f : files) {
                    delete(f);
                }
            }
            file.delete();
        }
    }

    public static boolean rename(String src, String to) {
        return new File(src).renameTo(new File(to));
    }

    public static String tirmName(String name, String ex) {
        if (name.toLowerCase(Locale.US).endsWith(ex)) {
            int i = name.lastIndexOf(".");
            if (i >= 0) {
                return name.substring(0, i);
            }
        }
        return name;
    }

    public static String join(String path1, String path2) {
        if (TextUtils.isEmpty(path1)) {
            return path2;
        }
        if (TextUtils.isEmpty(path2)) {
            return path1;
        }
        if (!path1.endsWith("/")) {
            path1 += "/";
        }
        if (path2.startsWith("/")) {
            path2 = path2.substring(1);
        }
        return path1 + path2;
    }

    public static String getName(String path) {
        return new File(path).getName();
    }

    public static boolean copyFile(AssetManager mgr, String path, File file, boolean update) {
        if (Constants.DEBUG)
            Log.d(TAG, "copyFile:" + path + "-->" + file.getAbsolutePath());
        InputStream inputStream = null;
        boolean ret = false;
        try {
            inputStream = mgr.open(path);
            if(update || !file.exists()) {
                createFolderByFile(file);
                ret = copyToFile(mgr.open(path), file.getAbsolutePath());
            }else{
                ret = true;
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            close(inputStream);
        }
        return ret;
    }

    public static int copyFolder(AssetManager mgr, String srcDir, String dstDir, boolean update) {
        String[] files = null;
        try {
            files = mgr.list(srcDir);
        } catch (IOException e) {
            return 0;
        }
        if (files == null || files.length == 0) {
            if (Constants.DEBUG)
                Log.w(TAG, "copy dir:" + srcDir + "-->" + dstDir+", not files");
            return 0;
        }
        File dir = new File(dstDir);
        createFolder(dir);
        if (Constants.DEBUG)
            Log.i(TAG, "copy dir:" + srcDir + "-->" + dstDir+", count="+files.length);
        int count = 0;
        for (String file : files) {
            String assetPath = join(srcDir, file);
            File toPath = new File(dstDir, file);
            if (update || !toPath.exists()) {
                if (Constants.DEBUG)
                    Log.d(TAG, "copy file:" + assetPath + "-->" + toPath.getAbsolutePath());
                InputStream inputStream = null;
                try {
                    inputStream = mgr.open(assetPath);
                    if (copyToFile(inputStream, toPath.getAbsolutePath())) {
                        count++;
                    }
                }catch (IOException e){
                    e.printStackTrace();
                }finally {
                    close(inputStream);
                }
            } else {
                count++;
                if (Constants.DEBUG)
                    Log.d(TAG, "copy ignore:" + assetPath + "-->" + toPath.getAbsolutePath());
            }
        }
        return count;
    }

    public static void createFolderByFile(File file) {
        File dir = file.getParentFile();
        if (dir != null && !dir.exists()) {
            dir.mkdirs();
        }
    }

    public static boolean createFolder(File file) {
        if (!file.exists()) {
            return file.mkdirs();
        }
        return false;
    }

    public static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] cache = new byte[1024 * 8];
        int len;
        while ((len = in.read(cache)) != -1) {
            out.write(cache, 0, len);
        }
    }

    public static boolean hasAssets(AssetManager mgr, String name) {
        try {
            mgr.open(name);
        } catch (IOException e) {
            return false;
        }
        return true;
    }

    public static boolean copyToFile(InputStream in, String file) {
        FileOutputStream outputStream = null;
        try {
            outputStream = new FileOutputStream(file);
            copy(in, outputStream);
        } catch (Exception e) {
            return false;
        } finally {
            close(outputStream);
            close(in);
        }
        return true;
    }

    public static boolean createNoMedia(String path) {
        File file = new File(path);
        createFolder(file);
        if (file.isDirectory()) {
            //
            File n = new File(file, ".nomedia");
            if (n.exists()) {
                return true;
            }
            try {
                n.createNewFile();
                return true;
            } catch (IOException e) {
            }
        }
        return false;
    }


    public static File asFile(InputStream is, String outPath) throws IOException {
        OutputStream os = null;
        try {
            os = new FileOutputStream(outPath);
            int len = 0;
            byte[] buffer = new byte[8192];

            while ((len = is.read(buffer)) != -1) {
                os.write(buffer, 0, len);
            }
        } finally {
            if (os != null)
                os.close();
            is.close();
        }
        return new File(outPath);
    }


}
