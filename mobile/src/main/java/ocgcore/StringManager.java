package ocgcore;

import android.text.TextUtils;
import android.util.Log;
import android.util.SparseArray;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.utils.IOUtils;
import cn.garymb.ygomobile.utils.StringUtils;
import ocgcore.data.CardSet;
import ocgcore.enums.CardAttribute;
import ocgcore.enums.CardCategory;
import ocgcore.enums.CardOt;
import ocgcore.enums.CardRace;
import ocgcore.enums.CardType;
import ocgcore.enums.LimitType;

public class StringManager implements Closeable {
    private static final String PRE_SYSTEM = "!system";
    private static final String PRE_SETNAME = "!setname";
    private final SparseArray<String> mSystem = new SparseArray<>();
    private final List<CardSet> mCardSets = new ArrayList<>();

    StringManager() {

    }

    @Override
    public void close() {
        mSystem.clear();
        mCardSets.clear();
    }

    public boolean load() {
        mSystem.clear();
        mCardSets.clear();
        File stringFile = new File(AppsSettings.get().getResourcePath(), Constants.CORE_STRING_PATH);
        boolean rs1 = loadFile(stringFile.getAbsolutePath());
        boolean rs2 = true;
        boolean res3 = true;
        if (AppsSettings.get().isReadExpansions()) {
            File stringFile2 = new File(AppsSettings.get().getExpansionsPath(), Constants.CORE_STRING_PATH);
            rs2 = loadFile(stringFile2.getAbsolutePath());
            File[] files = AppsSettings.get().getExpansionsPath().listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile() && (file.getName().endsWith(".zip") || file.getName().endsWith(".ypk"))) {
                        Log.e("StringManager", "读取压缩包");
                        try {
                            ZipFile zipFile = new ZipFile(file.getAbsoluteFile());
                            ZipEntry entry = zipFile.getEntry(Constants.CORE_STRING_PATH);
                            if (entry != null) {
                                res3 &= loadFile(zipFile.getInputStream(entry));
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                            res3 = false;
                        }
                    }
                }
            }
        }
        return rs1 && rs2 && res3;
    }

    public boolean loadFile(InputStream inputStream) {

        InputStreamReader in = null;
        try {
            in = new InputStreamReader(inputStream, "utf-8");
            BufferedReader reader = new BufferedReader(in);
            String line = null;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#") || (!line.startsWith(PRE_SYSTEM) && !line.startsWith(PRE_SETNAME))) {
                    continue;
                }
                String[] words = line.split("[\t ]+");
//
                if (words.length >= 3) {
                    if (PRE_SETNAME.equals(words[0])) {
//                        System.out.println(Arrays.toString(words));
                        //setcode
                        long id = toNumber(words[1]);
                        CardSet cardSet = new CardSet(id, words[2]);
                        int i = mCardSets.indexOf(cardSet);
                        if (i >= 0) {
                            CardSet cardSet1 = mCardSets.get(i);
                            cardSet1.setName(cardSet.getName());
                        } else {
                            mCardSets.add(cardSet);
                        }
                    } else {
                        mSystem.put((int) toNumber(words[1]), words[2]);
                    }
                }
            }
        } catch (Exception e) {

        } finally {
            IOUtils.close(inputStream);
            IOUtils.close(in);
        }
        Collections.sort(mCardSets, CardSet.NAME_ASC);
        return true;
    }

    public boolean loadFile(String path) {
        if (path == null || path.length() == 0) {
            return false;
        }
        File file = new File(path);
        if (file.isDirectory() || !file.exists()) {
            return false;
        }
        InputStreamReader in = null;
        FileInputStream inputStream = null;
        try {
            inputStream = new FileInputStream(file);
            in = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
            BufferedReader reader = new BufferedReader(in);
            String line = null;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#") || (!line.startsWith(PRE_SYSTEM) && !line.startsWith(PRE_SETNAME))) {
                    continue;
                }
                String[] words = line.split("[\t ]+");
//
                if (words.length >= 3) {
                    if (PRE_SETNAME.equals(words[0])) {
//                        System.out.println(Arrays.toString(words));
                        //setcode
                        long id = toNumber(words[1]);
                        CardSet cardSet;
                        if (words.length >= 5) {
                            cardSet = new CardSet(id, words[2] + " " + words[3]);
                        } else {
                            cardSet = new CardSet(id, words[2]);
                        }
                        int i = mCardSets.indexOf(cardSet);
                        if (i >= 0) {
                            CardSet cardSet1 = mCardSets.get(i);
                            cardSet1.setName(cardSet.getName());
                        } else {
                            mCardSets.add(cardSet);
                        }
                    } else {
                        mSystem.put((int) toNumber(words[1]), words[2]);
                    }
                }
            }
        } catch (Exception e) {

        } finally {
            IOUtils.close(inputStream);
            IOUtils.close(in);
        }
        Collections.sort(mCardSets, CardSet.NAME_ASC);
        return true;
    }

    public SparseArray<String> getSystem() {
        return mSystem;
    }

    public List<CardSet> getCardSets() {
        return mCardSets;
    }

    public String getSetName(long key) {
        CardSet cardSet = new CardSet(key, null);
        int i = mCardSets.indexOf(cardSet);
        if (i >= 0) {
            return mCardSets.get(i).getName();
        }
        return String.format("0x%x", key);
    }

    public long getSetCode(String key) {
        for (int i = 0; i < mCardSets.size(); i++) {
            CardSet cardSet = mCardSets.get(i);
            String[] setNames = cardSet.getName().split("\\|");
            if (setNames[0].equalsIgnoreCase(key)) {
                return cardSet.getCode();
            }
        }
        return 0;
    }

    /**
     * @param index 索引
     * @param def 默认值
     */
    public String getSystemString(Integer index, String def){
        if(index <= 0){
            return def;
        }
        try {
            String str = mSystem.get(index);
            if (TextUtils.isEmpty(str)) {
                return def;
            }
            return StringUtils.toDBC(str);
        } catch (Exception e) {
            return def;
        }
    }

    public String getLimitString(long id) {
        LimitType value = LimitType.valueOf(id);
        if(value == null){
            return String.valueOf(id);
        }
        return getSystemString(value.getLanguageIndex(), value.name());
    }

    public String getTypeString(long id) {
        CardType value = CardType.valueOf(id);
        if(value == null){
            return String.valueOf(id);
        }
        return getSystemString(value.getLanguageIndex(), value.name());
    }

    public String getAttributeString(long id) {
        CardAttribute value = CardAttribute.valueOf(id);
        if(value == null){
            return String.valueOf(id);
        }
        return getSystemString(value.getLanguageIndex(), value.name());
    }

    public String getRaceString(long id) {
        CardRace value = CardRace.valueOf(id);
        if(value == null){
            return String.format("0x%x", id);
        }
        return getSystemString(value.getLanguageIndex(), value.name());
    }

    public String getOtString(int ot) {
        CardOt value = CardOt.valueOf(ot);
        if(value == null){
            return String.valueOf(ot);
        }
        return getSystemString(value.getLanguageIndex(), value.name());
    }

    public String getCategoryString(long id) {
        CardCategory value = CardCategory.valueOf(id);
        if(value == null){
            return String.valueOf(id);
        }
        return getSystemString(value.getLanguageIndex(), value.name());
    }

    private long toNumber(String str) {
        long i = 0;
        try {
            if (str.startsWith("0x")) {
                i = Long.parseLong(str.replace("0x", ""), 0x10);
            } else {
                i = Long.parseLong(str);
            }
        } catch (Exception e) {

        }
        return i;
    }
}
