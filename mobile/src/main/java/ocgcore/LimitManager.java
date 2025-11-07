package ocgcore;


import android.util.Log;

import androidx.annotation.Nullable;

import com.file.zip.ZipEntry;
import com.file.zip.ZipFile;

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
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.utils.IOUtils;
import ocgcore.data.CardSet;
import ocgcore.data.LimitList;

public class LimitManager implements Closeable {
    /* key为时间，如“2023.7” ，value为禁止卡、限制卡、准限制卡的列表 */
    private final Map<String, LimitList> mLimitLists = new HashMap<>();
    /* 只存储key的列表，其元素形如“2023.7” */
    private final List<String> mLimitNames = new ArrayList<>();
    private int mCount;

    public LimitManager() {

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

    public @Nullable LimitList getLimit(String name) {
        return mLimitLists.get(name);
    }

    public LimitList getTopLimit() {
        if (mLimitNames.size() == 0) {
            return null;
        }
        return mLimitLists.get(mLimitNames.get(0));
    }

    /**
     * 加载禁卡表lflist.conf数据
     *
     * @return boolean 加载成功返回true，否则返回false
     */
    public boolean load() {
        // 创建主资源路径下的限制文件对象
        File stringFile = new File(AppsSettings.get().getResourcePath(), Constants.CORE_LIMIT_PATH);
        boolean rs1 = true;
        boolean rs2 = true;
        boolean res3 = true;
        // 如果需要读取扩展包数据，则加载扩展包中的限制文件
        if (AppsSettings.get().isReadExpansions()) {
            File stringFile2 = new File(AppsSettings.get().getExpansionsPath(), Constants.CORE_LIMIT_PATH);
            rs1 = loadFile(stringFile2);
            File[] files = AppsSettings.get().getExpansionsPath().listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile() && (file.getName().endsWith(".zip") || file.getName().endsWith(Constants.YPK_FILE_EX))) {
                        Log.e("LimitManager", "读取压缩包");
                        try {
                            ZipFile zipFile = new ZipFile(file.getAbsoluteFile(), "GBK");
                            Enumeration<ZipEntry> entris = zipFile.getEntries();
                            ZipEntry entry;
                            while (entris.hasMoreElements()) {
                                entry = entris.nextElement();
                                if (!entry.isDirectory()) {
                                    if (entry.getName().contains("lflist") && entry.getName().endsWith(".conf")) {
                                        rs2 &= loadFile(zipFile.getInputStream(entry));
                                    }
                                }
                            }
                        } catch (IOException e) {
                            e.printStackTrace();
                            res3 = false;
                        }
                    }
                }
            }
        }
        res3 = loadFile(stringFile);
        LimitList blank_list = new LimitList("N/A");
        mLimitLists.put("N/A", blank_list);
        mLimitNames.add("N/A");
        ++mCount;
        return rs1 && rs2 && res3;
    }
        /**
     * 从输入流加载配置文件数据
     * @param inputStream 配置文件的输入流
     * @return 加载成功返回true
     */
    public boolean loadFile(InputStream inputStream) {

        InputStreamReader in = null;
        try {
            in = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
            BufferedReader reader = new BufferedReader(in);
            String line = null;
            String name = null;
            LimitList tmp = null;

            // 逐行读取配置文件内容
            while ((line = reader.readLine()) != null) {
                // 跳过注释行
                if (line.startsWith("#")) {
                    continue;
                }

                // 处理新的限制列表定义
                if (line.startsWith("!")) {
                    name = line.substring(1);
                    tmp = new LimitList(name);
                    mLimitLists.put(name, tmp);
                    mLimitNames.add(name);
                } else if (tmp != null) {
                    // 解析限制项配置
                    String[] words = line.trim().split("[\t| ]+");
                    if (words.length >= 2) {
                        int id = toNumber(words[0]);
                        int count = toNumber(words[1]);
                        // 根据count值添加不同类型的限制
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

        // 更新限制列表计数
        mCount = mLimitLists.size();
        return true;
    }


    /**
     * 解析限制卡配置文件lflist.conf的内容
     *
     * @param file
     * @return
     */
    public boolean loadFile(File file) {
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
                        int count = toNumber(words[1]);
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
