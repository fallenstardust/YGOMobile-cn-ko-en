package ocgcore;

import android.support.annotation.NonNull;
import android.util.Log;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.utils.IOUtils;
import ocgcore.data.LimitList;

public class LimitManager implements Closeable {
    private final Map<String, LimitList> mLimitLists = new HashMap<>();
    private final List<String> mLimitNames = new ArrayList<>();
    private int mCount;

    LimitManager() {

    }

    @Override
    public void close() {
        mLimitNames.clear();
        mLimitLists.clear();
    }

    public int getCount() {
        return mCount;
    }

    public Map<String, LimitList> getLimitLists() {
        return mLimitLists;
    }

    public List<String> getLimitNames() {
        return mLimitNames;
    }

    public @NonNull LimitList getLimit(String name) {
        return mLimitLists.get(name);
    }

    public LimitList getTopLimit() {
        if (mLimitNames.size() == 0) {
            return null;
        }
        return mLimitLists.get(mLimitNames.get(0));
    }

    public boolean load() {
        File stringFile = new File(AppsSettings.get().getResourcePath(), Constants.CORE_LIMIT_PATH);
        boolean rs1 = loadFile(stringFile);
        boolean rs2 = true;
        if (AppsSettings.get().isReadExpansions()) {
            File stringFile2 = new File(AppsSettings.get().getExpansionsPath(), Constants.CORE_CUSTOM_LIMIT_PATH);
            rs2 = loadFile(stringFile2);
        }
        return rs1 && rs2;
    }

    public boolean loadFile(File file) {
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
            String name = null;
            LimitList tmp = null;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#")) {
                    continue;
                }
                if (line.startsWith("!")) {
                    name = line.substring(1);
                    tmp = new LimitList(name);
                    mLimitLists.put(name, tmp);
                    mLimitNames.add(name);
                } else if (tmp != null) {
                    String[] words = line.trim().split("[\t| ]+");
                    if (words.length >= 2) {
                        int id = toNumber(words[0]);
                        int count = (int) toNumber(words[1]);
                        switch (count) {
                            case 0:
                                tmp.addForbidden(id);
                                break;
                            case 1:
                                tmp.addLimit(id);
                                break;
                            case 2:
                                tmp.addSemiLimit(id);
                                break;
                        }
                    }

                }
            }
        } catch (Exception e) {
            Log.e("kk", "limit", e);
        } finally {
            IOUtils.close(inputStream);
            IOUtils.close(in);
        }
        mCount = mLimitLists.size();
        return true;
    }

    private int toNumber(String str) {
        int i = 0;
        try {
            if (str.startsWith("0x")) {
                i = Integer.parseInt(str.replace("0x", ""), 0x10);
            } else {
                i = Integer.parseInt(str);
            }
        } catch (Exception e) {

        }
        return i;
    }
}
