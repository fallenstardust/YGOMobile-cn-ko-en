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
}
