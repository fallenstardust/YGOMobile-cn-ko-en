package com.ourygo.ygomobile.util

import android.util.Log
import cn.garymb.ygomobile.AppsSettings
import com.ourygo.ygomobile.base.listener.OnDelListener
import com.ourygo.ygomobile.base.listener.OnExpansionsListQueryListener
import java.io.File

/**
 * Create By feihua  On 2021/10/23
 */
object ExpansionsUtil {
    fun findExpansionsList(onExpansionsListQueryListener: OnExpansionsListQueryListener) {
        val ypks = File(AppsSettings.get().expansionsPath.absolutePath).listFiles()
        val fileList: MutableList<File> = ArrayList()
        if (ypks != null) {
            var isOther = false
            for (file in ypks) {
                if (!file.isFile) {
                    isOther = true
                    continue
                }
                if (!file.name.endsWith(".ypk")) {
                    isOther = true
                    continue
                }
                fileList.add(file)
            }
            if (isOther) fileList.add(File(Record.ARG_OTHER))
        }
        onExpansionsListQueryListener.onExpansionsListQuery(fileList)
    }

    fun delExpansionsAll(onDelListener: OnDelListener) {
        Thread {
            var b = true
            val file = File(AppsSettings.get().expansionsPath.absolutePath)
            if (file.isDirectory) {
                b = b and FileUtil.delFile(file.absolutePath)
            }
            onDelListener.onDel(file.absolutePath, b)
        }.start()
    }

    fun delExpansions(cardFile: File, onDelListener: OnDelListener) {
        Log.e("ExpansionsUtil", "执行" + cardFile.absolutePath)
        if (cardFile.name != Record.ARG_OTHER) {
            var isOk = true
            isOk = isOk and FileUtil.delFile(cardFile.absolutePath)
            onDelListener.onDel(cardFile.absolutePath, isOk)
            return
        }
        Thread {
            var isOk = true
            var path = ""
            val fileList = File(AppsSettings.get().expansionsPath.absolutePath).listFiles()
            if (fileList != null) {
                for (file in fileList) {
                    if (!file.isFile || !file.name.endsWith(".ypk")) {
                        isOk = isOk and FileUtil.delFile(file.absolutePath)
                    }
                }
                if (fileList.isNotEmpty()) path = fileList[0].absolutePath
            }
            onDelListener.onDel(path, isOk)
        }.start()
    }
}