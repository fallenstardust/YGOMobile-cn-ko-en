package cn.garymb.ygomobile.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;


public class FileUtils {

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
        try {
            inputStream = new FileInputStream(file);
            in = new InputStreamReader(inputStream, encoding);
            BufferedReader reader = new BufferedReader(in);
            String line = null;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        } catch (Exception e) {

        } finally {
            IOUtils.close(in);
            IOUtils.close(inputStream);
        }
        return lines;
    }

    public static boolean writeLines(String file, List<String> lines, String encoding, String newLine) {
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

    public static void copyFile(InputStream in, File out) {
        FileOutputStream outputStream = null;
        try {
            File dir = out.getParentFile();
            if (!dir.exists()) {
                dir.mkdirs();
            }
            outputStream = new FileOutputStream(out);
            copy(in, outputStream);
        } catch (Throwable e) {
            e.printStackTrace();
        } finally {
            IOUtils.close(outputStream);
            IOUtils.close(in);
        }
    }

    public static void copyFile(File in, File out) {
        FileOutputStream outputStream = null;
        FileInputStream inputStream = null;
        try {
            File dir = out.getParentFile();
            if (!dir.exists()) {
                dir.mkdirs();
            }
            inputStream = new FileInputStream(in);
            outputStream = new FileOutputStream(out);
            copy(inputStream, outputStream);
        } catch (Throwable e) {
            e.printStackTrace();
        } finally {
            IOUtils.close(outputStream);
            IOUtils.close(inputStream);
        }
    }

    public static void copyFile(String oldPath, String newPath, boolean isName) throws FileNotFoundException, IOException {

        //判断复制后的路径是否含有文件名,如果没有则加上
        if (!isName) {
            //由于newPath是路径加文件名,所以获取要复制的文件名与复制后的路径组成新的newPath
            String abb[] = oldPath.split("/");
            newPath = newPath + "/" + abb[abb.length - 1];
        }

        FileInputStream fis = new FileInputStream(oldPath);
        FileOutputStream fos = new FileOutputStream(newPath);
        byte[] buf = new byte[1024];
        int len = 0;
        while ((len = fis.read(buf)) != -1) {
            fos.write(buf, 0, len);
        }
        fos.close();
        fis.close();

    }


    public static void moveFile(String oldPath, String newPath, boolean isName) throws FileNotFoundException, IOException {

        //判断复制后的路径是否含有文件名,如果没有则加上
        if (!isName) {
            //由于newPath是路径加文件名,所以获取要复制的文件名与复制后的路径组成新的newPath
            String abb[] = oldPath.split("/");
            newPath = newPath + "/" + abb[abb.length - 1];
        }

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

        if (!(new File(newPath)).exists()) {
            (new File(newPath)).mkdir();
        }

        for (int i = 0; i < filePath.length; i++) {
            if ((new File(oldPath + file.separator + filePath[i])).isDirectory()) {
                copyDir(oldPath + file.separator + filePath[i], newPath + file.separator + filePath[i], false);
            }

            if (new File(oldPath + file.separator + filePath[i]).isFile()) {
                if (!(new File(newPath + file.separator + filePath[i]).exists()) || isReplaced)
                    copyFile(oldPath + file.separator + filePath[i], newPath + file.separator + filePath[i], true);
            }

        }
    }

    //删除文件（String Path）
    public static void delFile(String s) {
        File file = new File(s);

        if (file.isDirectory()) {
            File[] files = file.listFiles();
            for (int i = 0; i < files.length; i++) {
                File f = files[i];
                deleteFile(f);
            }
            //file.delete();
        } else if (file.exists()) {
            file.delete();
        }

    }
}
