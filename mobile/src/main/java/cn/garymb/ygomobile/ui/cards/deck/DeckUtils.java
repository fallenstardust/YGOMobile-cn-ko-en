package cn.garymb.ygomobile.ui.cards.deck;

import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.bean.Deck;
import cn.garymb.ygomobile.bean.DeckInfo;
import cn.garymb.ygomobile.utils.IOUtils;
import ocgcore.data.Card;

public class DeckUtils {
    public static String getDeckString(Deck deck) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        save(deck, outputStream);
        String str = outputStream.toString();
        IOUtils.close(outputStream);
        return str;
    }

    public static String getDeckString(DeckInfo deck) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        save(deck, outputStream);
        String str = outputStream.toString();
        IOUtils.close(outputStream);
        return str;
    }

    private static boolean save(DeckInfo deck, OutputStream outputStream) {
        OutputStreamWriter writer = null;
        try {
            writer = new OutputStreamWriter(outputStream, "utf-8");
            writer.write("#created by ygomobile".toCharArray());
            writer.write("\n#main".toCharArray());
            for (Card card : deck.getMainCards()) {
                writer.write(("\n" + card.Code).toCharArray());
            }
            writer.write("\n#extra".toCharArray());
            for (Card card : deck.getExtraCards()) {
                writer.write(("\n" + card.Code).toCharArray());
            }
            writer.write("\n!side".toCharArray());
            for (Card card : deck.getSideCards()) {
                writer.write(("\n" + card.Code).toCharArray());
            }
            writer.flush();
            outputStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        } finally {
            IOUtils.close(writer);
        }
        return true;
    }

    private static boolean save(Deck deck, OutputStream outputStream) {
        OutputStreamWriter writer = null;
        try {
            writer = new OutputStreamWriter(outputStream, "utf-8");
            writer.write("#created by ygomobile".toCharArray());
            writer.write("\n#main".toCharArray());
            for (long id : deck.getMainlist()) {
                writer.write(("\n" + id).toCharArray());
            }
            writer.write("\n#extra".toCharArray());
            for (long id : deck.getExtraList()) {
                writer.write(("\n" + id).toCharArray());
            }
            writer.write("\n!side".toCharArray());
            for (long id : deck.getSideList()) {
                writer.write(("\n" + id).toCharArray());
            }
            writer.flush();
            outputStream.flush();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        } finally {
            IOUtils.close(writer);
        }
        return true;
    }

    public static boolean save(DeckInfo deck, File file) {
        if (deck == null) return false;
        FileOutputStream outputStream = null;
        try {
            if (file == null) {
                return false;
            }
            if (file.exists()) {
                file.delete();
            }
            file.createNewFile();
            outputStream = new FileOutputStream(file);
            save(deck, outputStream);
        } catch (Exception e) {
            //ignore
        } finally {
            IOUtils.close(outputStream);
        }
        return true;
    }

    public static boolean save(Deck deck, File file) {
        if (deck == null) return false;
        FileOutputStream outputStream = null;
        try {
            if (file == null) {
                return false;
            }
            if (file.exists()) {
                file.delete();
            }
            file.createNewFile();
            outputStream = new FileOutputStream(file);
            save(deck, outputStream);
        } catch (Exception e) {
            Log.e("DeckUtil","保存出错"+e);
            //ignore
        } finally {
            IOUtils.close(outputStream);
        }
        return true;
    }

    public static File save(String name,String deckMessage) throws IOException {
        FileWriter fw = null;

        //如果文件存在，则重写内容；如果文件不存在，则创建文件
        File f = new File(AppsSettings.get().getDeckDir(),name+".ydk");
        fw = new FileWriter(f, false);

        PrintWriter pw = new PrintWriter(fw);
        pw.println(deckMessage);
        pw.flush();
        fw.flush();
        pw.close();
        fw.close();
        return f;
    }

}
