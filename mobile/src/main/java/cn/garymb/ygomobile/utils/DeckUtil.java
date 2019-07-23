package cn.garymb.ygomobile.utils;

import android.content.Context;
import android.os.Build;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.bean.DeckType;
import cn.garymb.ygomobile.bean.events.DeckFile;
import cn.garymb.ygomobile.lite.R;

public class DeckUtil {

    public static List<DeckType> getDeckTypeList(Context context) {
        List<DeckType> deckTypeList = new ArrayList<>();
        deckTypeList.add(new DeckType(context.getResources().getString(R.string.category_pack), AppsSettings.get().getPackDeckDir()));
        deckTypeList.add(new DeckType(context.getResources().getString(R.string.category_windbot_deck), AppsSettings.get().getAiDeckDir()));
        deckTypeList.add(new DeckType(context.getResources().getString(R.string.category_Uncategorized), AppsSettings.get().getDeckDir()));

        File[] files = new File(AppsSettings.get().getDeckDir()).listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    deckTypeList.add(new DeckType(f.getName(), f.getAbsolutePath()));
                }
            }
        }
        return deckTypeList;
    }

    public static List<DeckFile> getDeckList(String path) {
        List<DeckFile> deckList = new ArrayList<>();
        File[] files = new File(path).listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && file.getName().endsWith(".ydk")) {
                    deckList.add(new DeckFile(file));
                }
            }
        }
        return deckList;
    }

    public static List<DeckFile> getExpansionsDeckList() throws IOException {
        AppsSettings appsSettings = AppsSettings.get();
        List<DeckFile> deckList = new ArrayList<>();
        File[] files = new File(appsSettings.getExpansionsPath(), Constants.CORE_DECK_PATH).listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && file.getName().endsWith(".ydk")) {
                    deckList.add(new DeckFile(file));
                }
            }
        }
        files = appsSettings.getExpansionsPath().listFiles();
        for (File file : files) {
            if (file.isFile() && file.getName().endsWith(".zip")) {
                ZipFile zipFile = new ZipFile(file.getAbsoluteFile());
                Enumeration entries = zipFile.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = (ZipEntry) entries.nextElement();
                    if (entry.getName().endsWith(".ydk")) {
                        String name = entry.getName();
                        name = name.substring(name.lastIndexOf("/"), name.length());
                        InputStream inputStream = zipFile.getInputStream(entry);
                        deckList.add(new DeckFile(
                                IOUtils.asFile(inputStream,
                                        appsSettings.getCacheDeckDir() + "/" + name)));
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    IOUtils.close(zipFile);
                }
            }

        }
        return deckList;
    }
}
