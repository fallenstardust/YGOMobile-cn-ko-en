package com.ourygo.ygomobile.util;

import android.util.Log;

import com.ourygo.ygomobile.base.listener.OnDelListener;
import com.ourygo.ygomobile.base.listener.OnExpansionsListQueryListener;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.AppsSettings;

/**
 * Create By feihua  On 2021/10/23
 */
public class ExpansionsUtil {
    public static void findExpansionsList(OnExpansionsListQueryListener onExpansionsListQueryListener) {
        File[] ypks = new File(AppsSettings.get().getExpansionsPath().getAbsolutePath()).listFiles();
        List<File> fileList = new ArrayList<>();
        if (ypks != null) {
            boolean isOther = false;
            for (File file : ypks) {
                if (!file.isFile()) {
                    isOther = true;
                    continue;
                }
                if (!file.getName().endsWith(".ypk")) {
                    isOther = true;
                    continue;
                }
                fileList.add(file);
            }
            if (isOther)
                fileList.add(new File(Record.ARG_OTHER));
        }
        onExpansionsListQueryListener.onExpansionsListQuery(fileList);
    }

    public static void delExpansionsAll(OnDelListener onDelListener) {
        new Thread(() -> {
            boolean b = true;
            File file = new File(AppsSettings.get().getExpansionsPath().getAbsolutePath());
            if (file.isDirectory()) {
                b &= FileUtil.delFile(file.getAbsolutePath());
            }
            onDelListener.onDel(file.getAbsolutePath(), b);
        }).start();
    }

    public static void delExpansions(File cardFile, OnDelListener onDelListener) {
        Log.e("ExpansionsUtil","执行"+cardFile.getAbsolutePath());
        if (!cardFile.getName().equals(Record.ARG_OTHER)) {
            boolean isOk = true;
            isOk &= FileUtil.delFile(cardFile.getAbsolutePath());
            onDelListener.onDel(cardFile.getAbsolutePath(), isOk);
            return;
        }

        new Thread(() -> {
            boolean isOk = true;
            String path = "";
            File[] fileList = new File(AppsSettings.get().getExpansionsPath().getAbsolutePath()).listFiles();
            if (fileList != null) {
                for (File file : fileList) {
                    if (!file.isFile() || !file.getName().endsWith(".ypk")) {
                        isOk &= FileUtil.delFile(file.getAbsolutePath());

                    }
                }

                if (fileList.length != 0)
                    path = fileList[0].getAbsolutePath();
            }
            onDelListener.onDel(path, isOk);
        }).start();

    }

}
