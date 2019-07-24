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
        } else if(file.isDirectory()){
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

    public static boolean isDirectory(Context context, String assets) {
        String[] files = new String[0];
        try {
            files = context.getAssets().list(assets);
        } catch (IOException e) {

        }
        if (files != null && files.length > 0) {
            return true;
        }
        return false;
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

    public static int copyFilesFromAssets(Context context, String assets, String toPath, boolean update) throws IOException {
        AssetManager am = context.getAssets();
        String[] files = am.list(assets);
        if (files == null) {
            return 0;
        }
        if (files.length == 0) {
            //is file
            String file = getName(assets);
            File tofile = new File(toPath, file);
            if (update || !tofile.exists()) {
                if (Constants.DEBUG)
                    Log.i(TAG, "copy1:" + assets + "-->" + tofile);
                createFolderByFile(tofile);
                InputStream inputStream = null;
                try {
                    inputStream = am.open(assets);
                }catch (Exception e){

                }
                if(inputStream != null) {
                    copyToFile(inputStream, tofile.getAbsolutePath());
                }else{
                    return 0;
                }
            }
            return 1;
        } else {
            int count = 0;
            File toDir = new File(toPath);
            createFolder(toDir);
            for (String file : files) {
                String path = join(assets, file);
                if (isDirectory(context, path)) {
                    if (Constants.DEBUG)
                        Log.i(TAG, "copy dir:" + path + "-->" + join(toPath, file));
                    createFolderByFile(new File(toPath, file));
                    count += copyFilesFromAssets(context, path, join(toPath, file), update);
                } else {
                    File f = new File(join(toPath, file));
                    createFolderByFile(f);
                    if (update || !f.exists()) {
                        if (Constants.DEBUG)
                            Log.d(TAG, "copy2:" + path + "-->" + f.getAbsolutePath());
                        copyToFile(am.open(path), f.getAbsolutePath());
                    } else {
                        if (Constants.DEBUG)
                            Log.d(TAG, "copy ignore:" + path + "-->" + f.getAbsolutePath());
                    }
                    count++;
                }
            }
            return count;
        }
    }
    public static void createFolderByFile(File file) {
        File dir = file.getParentFile();
        if (dir != null && !dir.exists()) {
            dir.mkdirs();
        }
    }
    public static void createFolder(File file) {
        if (!file.exists()) {
            file.mkdirs();
        }
    }

    public static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] cache = new byte[1024 * 8];
        int len;
        while ((len = in.read(cache)) != -1) {
            out.write(cache, 0, len);
        }
    }

    public static boolean hasAssets(Context context, String name) {
        try {
            context.getAssets().open(name);
        } catch (IOException e) {
            return false;
        }
        return true;
    }

    public static void copyToFile(InputStream in, String file) {
        FileOutputStream outputStream = null;
        try {
//            File dir = new File(file).getParentFile();
//            if (dir != null && !dir.exists()) {
//                dir.mkdirs();
//            }
            outputStream = new FileOutputStream(file);
            copy(in, outputStream);
        } catch (Exception e) {

        } finally {
            close(outputStream);
            close(in);
        }
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
            if (os!=null)
            os.close();
            is.close();
        }
        return new File(outPath);
    }



}
