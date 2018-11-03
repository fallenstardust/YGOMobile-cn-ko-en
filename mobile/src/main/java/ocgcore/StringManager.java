package ocgcore;

import android.text.TextUtils;
import android.util.SparseArray;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.utils.IOUtils;
import cn.garymb.ygomobile.utils.StringUtils;
import ocgcore.data.CardSet;
import ocgcore.enums.CardOt;

public class StringManager implements Closeable {
    private static final String PRE_SYSTEM = "!system";
    private  static final String PRE_SETNAME = "!setname";
    private final SparseArray<String> mSystem = new SparseArray<>();
    private final List<CardSet> mCardSets = new ArrayList<>();

    StringManager() {

    }

    @Override
    public void close(){
        mSystem.clear();
        mCardSets.clear();
    }

    public boolean load() {
        mSystem.clear();
        mCardSets.clear();
        File stringFile = new File(AppsSettings.get().getResourcePath(), Constants.CORE_STRING_PATH);
        boolean rs1 = loadFile(stringFile.getAbsolutePath());
        boolean rs2 = true;
        if (AppsSettings.get().isReadExpansions()) {
            File stringFile2 = new File(AppsSettings.get().getExpansionsPath(), Constants.CORE_STRING_PATH);
            rs2 = loadFile(stringFile2.getAbsolutePath());
        }
        return rs1 && rs2;
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
            in = new InputStreamReader(inputStream, "utf-8");
            BufferedReader reader = new BufferedReader(in);
            String line = null;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#") || (!line.startsWith(PRE_SYSTEM) && !line.startsWith(PRE_SETNAME))) {
                    continue;
                }
                String[] words = line.split("[\t| ]+");
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

    public String getSystemString(int key) {
        return mSystem.get(Integer.valueOf(key));
    }

    public String getSystemString(int start, long value) {
        return getSystemString(start + value2Index(value));
    }

    public String getLimitString(long value) {
//        String str =
//        Log.d("kk", value + "=" + str);
        return getSystemString((int) (Constants.STRING_LIMIT_START + value));
    }

    public String getTypeString(long value) {
        return getSystemString(Constants.STRING_TYPE_START, value);
    }

    public String getAttributeString(long value) {
        return getSystemString(Constants.STRING_ATTRIBUTE_START, value);
    }

    public String getRaceString(long value) {
        String race = getSystemString(Constants.STRING_RACE_START, value);
        if (TextUtils.isEmpty(race)) {
            return String.format("0x%X", value);
        }
        return race;
    }

    public String getOtString(int ot, String def) {
        if (ot == CardOt.All.ordinal()) {
            return "-";
        }
        try {
            String str = getSystemString(Constants.STRING_OT_START + ot);
            if (TextUtils.isEmpty(str)) {
                return def;//String.valueOf(CardOt.values()[ot]);
            }
            return StringUtils.toDBC(str);
        } catch (Exception e) {
            return def;
        }
    }

    public String getCategoryString(long value) {
        return getSystemString(Constants.STRING_CATEGORY_START, value);
    }

    public int value2Index(long type) {
        //0 1 2 3 4
        //1 2 4 8 16
        int i = 0;
        long start;
        do {
            start = (long) Math.pow(2, i);
            if (start == type) {
                return i;
            } else if (start > type) {
                return -1;
            }
            i++;
        }
        while (start < type);
        return i;
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
