package cn.garymb.ygomobile.utils;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.bean.Deck;
import cn.garymb.ygomobile.bean.DeckType;
import cn.garymb.ygomobile.bean.events.DeckFile;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ourygo.service.DuelAssistantService;
import cn.garymb.ygomobile.ui.cards.DeckManagerActivity;
import cn.garymb.ygomobile.ui.cards.deck.DeckUtils;

public class DeckUtil {

    public static List<DeckType> getDeckTypeList(Context context) {
        List<DeckType> deckTypeList = new ArrayList<>();
        deckTypeList.add(new DeckType(YGOUtil.s(R.string.category_pack), AppsSettings.get().getPackDeckDir()));
        deckTypeList.add(new DeckType(YGOUtil.s(R.string.category_windbot_deck), AppsSettings.get().getAiDeckDir()));
        deckTypeList.add(new DeckType(YGOUtil.s(R.string.category_Uncategorized), AppsSettings.get().getDeckDir()));

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
        Collections.sort(deckList, nameCom);
        return deckList;
    }

    /**
     * 根据卡组绝对路径获取卡组分类名称
     * @param deckPath
     * @return
     */
    public static String getDeckTypeName(String deckPath) {
        File file = new File(deckPath);
        if (file.exists()) {
            String name = file.getParentFile().getName();
            String lastName = file.getParentFile().getParentFile().getName();
            if (name.equals("pack") || name.equals("cacheDeck")) {
                //卡包
                return YGOUtil.s(R.string.category_pack);
            } else if (name.equals("Decks")&&lastName.equals(Constants.WINDBOT_PATH)) {
                //ai卡组
                return YGOUtil.s(R.string.category_windbot_deck);
            } else if (name.equals("deck") && lastName.equals(Constants.PREF_DEF_GAME_DIR)) {
                //如果是deck并且上一个目录是ygocore的话，保证不会把名字为deck的卡包识别为未分类
                return YGOUtil.s(R.string.category_Uncategorized);
            } else {
               return name;
            }
        }
        return null;
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
        Collections.sort(deckList, nameCom);
        return deckList;
    }

    static Comparator nameCom = new Comparator<DeckFile>() {
        @Override
        public int compare(DeckFile ydk1, DeckFile ydk2) {
            return ydk1.getName().compareTo(ydk2.getName());
        }
    };

    public static void saveDeck(Context context,String deckMessage,boolean isUrl){
        if (isUrl) {
            Deck deckInfo = new Deck(YGOUtil.s(R.string.rename_deck) + System.currentTimeMillis(), Uri.parse(deckMessage));
            File file = deckInfo.saveTemp(AppsSettings.get().getDeckDir());
            Intent startdeck = new Intent(context, DeckManagerActivity.getDeckManager());
            startdeck.putExtra(Intent.EXTRA_TEXT, file.getAbsolutePath());
            startdeck.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(startdeck);
        } else {
            //如果是卡组文本
            try {
                //以当前时间戳作为卡组名保存卡组
                File file = DeckUtils.save(YGOUtil.s(R.string.rename_deck) + System.currentTimeMillis(), deckMessage);
                Intent startdeck = new Intent(context, DeckManagerActivity.getDeckManager());
                startdeck.putExtra(Intent.EXTRA_TEXT, file.getAbsolutePath());
                startdeck.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(startdeck);
            } catch (IOException e) {
                e.printStackTrace();
                Toast.makeText(context, YGOUtil.s(R.string.save_failed_bcos) + e, Toast.LENGTH_SHORT).show();
            }
        }
    }

}
