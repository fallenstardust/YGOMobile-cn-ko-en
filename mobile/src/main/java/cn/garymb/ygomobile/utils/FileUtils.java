package cn.garymb.ygomobile.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;


public class FileUtils {

    public static boolean deleteFile(File file){
        if(file.isFile()){
            try {
                file.delete();
            }catch (Throwable e){
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
            if(!dir.exists()){
                dir.mkdirs();
            }
            outputStream = new FileOutputStream(out);
            copy(in, outputStream);
        } catch (Throwable e) {
            e.printStackTrace();
        }finally {
            IOUtils.close(outputStream);
            IOUtils.close(in);
        }
    }

    public static void copyFile(File in, File out) {
        FileOutputStream outputStream = null;
        FileInputStream inputStream = null;
        try {
            File dir = out.getParentFile();
            if(!dir.exists()){
                dir.mkdirs();
            }
            inputStream = new FileInputStream(in);
            outputStream = new FileOutputStream(out);
            copy(inputStream, outputStream);
        } catch (Throwable e) {
            e.printStackTrace();
        }finally {
            IOUtils.close(outputStream);
            IOUtils.close(inputStream);
        }
    }

    public static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] data = new byte[1024 * 8];
        int len;
        while ((len = in.read(data)) != -1) {
            out.write(data, 0, len);
        }
    }
}
