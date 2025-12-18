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
import java.util.Arrays;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.utils.IOUtils;
import cn.garymb.ygomobile.utils.LogUtil;
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
        if (mLimitNames.isEmpty()) {
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
        // 清理旧数据，不让缓存干扰读取结果
        mLimitLists.clear();
        mLimitNames.clear();
        boolean expansion_rs2 = true;
        boolean expansion_zip_rs1 = true;
        boolean default_res3 = true;
        // 如果需要读取扩展包数据，则加载扩展包中的限制文件
        if (AppsSettings.get().isReadExpansions()) {

            File[] files = AppsSettings.get().getExpansionsPath().listFiles();
            if (files != null) {
                for (File file : files) {
                    // 1.读取扩展卡压缩包中的lflist.conf文件
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
                                        expansion_zip_rs1 &= loadFile(zipFile.getInputStream(entry));
                                    }
                                }
                            }
                        } catch (IOException e) {
                            LogUtil.e("LimitManager", "读取压缩包失败", e);
                            default_res3 = false;
                        }
                    }
                    if (file.isFile() && file.getName().contains("lflist") && file.getName().endsWith(".conf")) {
                        expansion_rs2 = loadFile(file);
                    }
                }
            }
        }

        // 3.加载主资源路径(ygocore文件夹）下的lflist.conf文件对象，这是内置默认文件
        File ygocore_lflist = new File(AppsSettings.get().getResourcePath(), Constants.CORE_LIMIT_PATH);
        default_res3 = loadFile(ygocore_lflist);
        LimitList blank_list = new LimitList("N/A");
        mLimitLists.put("N/A", blank_list);
        mLimitNames.add("N/A");
        ++mCount;
        Log.e("LimitManager加载情况", "rs1=" + expansion_zip_rs1 + "  rs2=" + expansion_rs2 + "  res3=" + default_res3);
        return expansion_zip_rs1 && expansion_rs2 && default_res3;
    }

    /**
     * 从输入流加载配置文件数据，主要运用于读取压缩包的禁卡表
     *
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
                } else if (line.startsWith("$")) {
                    // 去掉$前缀并按空格分割
                    String[] words = line.substring(1).trim().split("[\t| ]+");
                    if (words[0].equals("genesys")) {//查询到genesys上限分值前缀识别文本
                        Integer creditLimit = toNumber(words[1]);  // 提取上限值（通常为100）并转换为整数
                        // 将creditType和creditLimit存储到LimitList对象中
                        if (tmp != null) {
                            tmp.addCreditLimit(creditLimit);
                        }
                    }
                } else if (tmp != null) {
                    // 解析限制项配置
                    String[] words = line.trim().split("[\t| ]+");
                    if (words.length >= 2) {
                        if (words[1].equals("$genesys")) {
                            tmp.addCredits(toNumber(words[0]), toNumber(words[2]));//保存genesys行的卡牌id和信用分值
                        } else {
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
            }
        } catch (Exception e) {
            Log.e("kk", "limit", e);
        } finally {
            IOUtils.close(inputStream);
            IOUtils.close(in);
        }

        // 更新限制列表计数
        mCount = mLimitLists.size();
        Log.e("LimitManager", "限制列表数量：" + mCount);
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
                } else if (line.startsWith("$")) {
                    // 去掉$前缀并按空格分割
                    String[] words = line.substring(1).trim().split("[\t| ]+");
                    if (words[0].equals("genesys")) {//查询到genesys上限分值前缀识别文本
                        Integer creditLimit = toNumber(words[1]);  // 提取上限值（通常为100）并转换为整数
                        // 将creditType和creditLimit存储到LimitList对象中
                        if (tmp != null) {
                            tmp.addCreditLimit(creditLimit);
                        }
                    }
                } else if (tmp != null) {
                    String[] words = line.trim().split("[\t| ]+");
                    if (words.length >= 2) {
                        if (words[1].equals("$genesys")) {
                            tmp.addCredits(toNumber(words[0]), toNumber(words[2]));//保存genesys行的卡牌id和信用分值
                        } else {
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
