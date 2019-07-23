package cn.garymb.ygomobile.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;

import cn.garymb.ygomobile.AppsSettings;

public class FileLogUtil {

    private static int writeNum=0;

    //获取配置文件路径
    public static File getConfigFile() {
        return new File(AppsSettings.get().getResourcePath(), "YGOMobile.log");
    }

    public static void clear() throws IOException {
        write("",false);
    }


    public static void writeAndTime(String message) throws IOException {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("MM-dd HH:mm");// HH:mm:ss
        // 获取当前时间
        Date date = new Date(System.currentTimeMillis());
        //追加内容写入
        write(simpleDateFormat.format(date) + "：   " + message);
    }

    public static void write(String message) throws IOException {
        write(message,true);
    }

    public static void write(String message,boolean append) throws IOException {
        FileWriter fw = null;

        //如果文件存在，则追加内容；如果文件不存在，则创建文件
        File f = getConfigFile();
        fw = new FileWriter(f, append);

        PrintWriter pw = new PrintWriter(fw);
        if (writeNum==0)
            pw.println();
        pw.println(message);
        writeNum++;
        pw.flush();
        fw.flush();
        pw.close();
        fw.close();

    }

    public static String read() throws IOException {
        String encoding = "UTF-8";
        File file = getConfigFile();
        Long filelength = file.length();
        byte[] filecontent = new byte[filelength.intValue()];
        FileInputStream in = new FileInputStream(file);
        in.read(filecontent);
        in.close();

        return new String(filecontent, encoding);
    }

}
