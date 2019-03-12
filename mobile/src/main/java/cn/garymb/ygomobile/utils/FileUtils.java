package cn.garymb.ygomobile.utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;


public class FileUtils {

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

    public static void copyFile(String in, FileOutputStream outputStream) {
        FileInputStream inputStream = null;
        byte[] data = new byte[1024 * 8];
        try {
            inputStream = new FileInputStream(in);
            int len;
            while ((len = inputStream.read(data)) != -1) {
                outputStream.write(data, 0, len);
            }
        } catch (Throwable e) {
            //
        } finally {
            IOUtils.close(outputStream);
            IOUtils.close(inputStream);
        }
    }
}
