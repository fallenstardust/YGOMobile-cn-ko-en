package cn.garymb.ygomobile.utils;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.core.content.FileProvider;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import cn.garymb.ygomobile.Constants;


public class FileUtils {

    public static boolean isExist(String path){
        return path != null && new File(path).exists();
    }

    public static String getFileExpansion(String path){
        int index = path.lastIndexOf(".");
        if(index>0){
            return path.substring(index+1).toLowerCase();
        }
        return "";
    }

    public static void closeQuietly(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception ignored) {
            }
        }
    }

    public static String readToString(String fileName) throws IOException {
        InputStream is = null;
        try {
            is = new FileInputStream(fileName);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            int i;
            while ((i = is.read()) != -1) {
                baos.write(i);
            }
            return baos.toString();
        } finally {
            closeQuietly(is);
        }
    }
    public static Uri toUri(Context context, File file) {
        return FileProvider.getUriForFile(context, context.getPackageName() + ".gamefiles", file);
    }

    public static boolean deleteFile(File file) {
        if (file.isFile()) {
            try {
                file.delete();
            } catch (Throwable e) {
                return false;
            }
        }
        return true;
    }

    public static List<String> readLines(String file, String encoding) {
        InputStreamReader in = null;
        FileInputStream inputStream = null;
        List<String> lines = new ArrayList<>();
        if(encoding == null){
            encoding = "utf-8";
        }
        try {
            inputStream = new FileInputStream(file);
            in = new InputStreamReader(inputStream, encoding);
            BufferedReader reader = new BufferedReader(in);
            String line = null;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        } catch (Throwable e) {
            //ignore
        } finally {
            IOUtils.close(in);
            IOUtils.close(inputStream);
        }
        return lines;
    }

    public static boolean writeLines(String file, List<String> lines, String encoding, String newLine) {
        if(encoding == null){
            encoding = "utf-8";
        }
        FileOutputStream outputStream = null;
        File tmp = new File(file + ".tmp");
        boolean ok = false;
        try {
            byte[] newL = newLine.getBytes(encoding);
            if (lines != null) {
                outputStream = new FileOutputStream(tmp);
                for (String line : lines) {
                    if (line != null) {
                        outputStream.write(line.getBytes(encoding));
                    }
                    outputStream.write(newL);
                }
                outputStream.flush();
                ok = true;
            }
        } catch (Exception e) {
            return false;
        } finally {
            IOUtils.close(outputStream);
        }
        if (ok) {
            File f = new File(file);
            if (f.exists()) {
                f.delete();
            }
            tmp.renameTo(f);
        }
        return true;
    }

    public static void copyFile(InputStream in, File out) throws IOException {
        FileOutputStream outputStream = null;
        IOUtils.createFolder(out.getParentFile());
        try {
            outputStream = new FileOutputStream(out);
            copy(in, outputStream);
        } finally {
            IOUtils.close(outputStream);
        }
    }

    public static boolean copyFile(File in, File out) {
        FileOutputStream outputStream = null;
        FileInputStream inputStream = null;
        try {
            IOUtils.createFolder(out.getParentFile());
            inputStream = new FileInputStream(in);
            outputStream = new FileOutputStream(out);
            copy(inputStream, outputStream);
        } catch (Throwable e) {
            Log.e(Constants.TAG, "copy file", e);
            return false;
        } finally {
            IOUtils.close(outputStream);
            IOUtils.close(inputStream);
        }
        return true;
    }

    public static void copyFile(String oldPath, String newPath) throws IOException {
        FileInputStream fis = new FileInputStream(oldPath);
        FileOutputStream fos = new FileOutputStream(newPath);
        byte[] buf = new byte[1024];
        int len;
        while ((len = fis.read(buf)) != -1) {
            fos.write(buf, 0, len);
        }
        fos.close();
        fis.close();

    }

    public static void moveFile(String oldPath, String newPath) throws IOException {
        FileInputStream fis = new FileInputStream(oldPath);
        FileOutputStream fos = new FileOutputStream(newPath);
        byte[] buf = new byte[1024];
        int len = 0;
        while ((len = fis.read(buf)) != -1) {
            fos.write(buf, 0, len);
        }
        fos.close();
        fis.close();
        //删除文件
        File file = new File(oldPath);
        if (file.exists() && file.isFile()) {
            file.delete();
        }

    }

    public static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] data = new byte[1024 * 8];
        int len;
        while ((len = in.read(data)) != -1) {
            out.write(data, 0, len);
        }
    }

    //复制文件夹全部文件
    public static void copyDir(String oldPath, String newPath, boolean isReplaced) throws IOException {
        File file = new File(oldPath);
        //文件名称列表
        String[] filePath = file.list();

        if (filePath == null)
            return;

        IOUtils.createFolder(new File(newPath));

        for (String path : filePath) {
            File src = new File(oldPath, path);
            File dst = new File(newPath, path);
            if (src.isDirectory()) {
                copyDir(src.getPath(), dst.getPath(), false);
            } else if (src.isFile()) {
                if (!dst.exists() || isReplaced) {
                    copyFile(src.getPath(), dst.getPath());
                }
            }
        }
    }

    //删除文件（String Path）
    public static void delFile(String s) {
        File file = new File(s);

        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files == null) return;
            for (File f : files) {
                deleteFile(f);
            }
            //file.delete();
        } else if (file.exists()) {
            file.delete();
        }

    }

    /**
     * 将 content Uri 指向的文件复制到目标文件，目标已存在则直接覆盖。
     * 适用于 FileProvider 分享过来的单个文件。
     *
     * @return true 复制成功
     */
    public static boolean copyUriToFile(Context context, Uri srcUri, File destFile) {
        InputStream in = null;
        try {
            in = context.getContentResolver().openInputStream(srcUri);
            if (in == null) {
                Log.e(Constants.TAG, "copyUriToFile: cannot open uri " + srcUri);
                return false;
            }
            copyFile(in, destFile);
            return true;
        } catch (Exception e) {
            Log.e(Constants.TAG, "copyUriToFile failed: " + destFile, e);
            return false;
        } finally {
            closeQuietly(in);
        }
    }

    /**
     * 将 content Uri 指向的 zip 解压到目标目录，已存在的文件直接覆盖。
     * 适用于 FileProvider 分享过来的 zip 包。
     *
     * @return true 解压成功；任意条目出错仍会继续处理其余条目，整体返回 false
     */
    public static boolean unzipFromUri(Context context, Uri zipUri, File destDir) {
        InputStream is = null;
        ZipInputStream zis = null;
        boolean result = true;
        try {
            is = context.getContentResolver().openInputStream(zipUri);
            if (is == null) {
                Log.e(Constants.TAG, "unzipFromUri: cannot open uri " + zipUri);
                return false;
            }
            IOUtils.createFolder(destDir);
            String destCanonical = destDir.getCanonicalPath();
            zis = new ZipInputStream(is);
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                File outFile = new File(destDir, entry.getName());
                String outCanonical = outFile.getCanonicalPath();
                // 防 zip slip：目标必须仍在解压根目录内
                if (!outCanonical.equals(destCanonical)
                        && !outCanonical.startsWith(destCanonical + File.separator)) {
                    Log.w(Constants.TAG, "unzipFromUri: skip unsafe entry " + entry.getName());
                    zis.closeEntry();
                    continue;
                }
                if (entry.isDirectory()) {
                    if (!outFile.exists()) {
                        outFile.mkdirs();
                    }
                } else {
                    File parent = outFile.getParentFile();
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs();
                    }
                    FileOutputStream fos = new FileOutputStream(outFile);
                    try {
                        copy(zis, fos);
                    } finally {
                        closeQuietly(fos);
                    }
                }
                zis.closeEntry();
            }
        } catch (Exception e) {
            Log.e(Constants.TAG, "unzipFromUri failed", e);
            result = false;
        } finally {
            closeQuietly(zis);
            closeQuietly(is);
        }
        return result;
    }

    //删除整个目录（含子目录与目录本身）；是文件就删文件
    public static void deleteDir(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                deleteDir(f);
            }
        }
        dir.delete();
    }

    //目录下所有文件的总字节数；是文件就返回文件大小，不存在返回 0
    public static long getDirSize(File dir) {
        File[] files = dir.listFiles();
        if (files == null) {
            return dir.length();
        }
        long size = 0;
        for (File f : files) {
            size += getDirSize(f);
        }
        return size;
    }
}
