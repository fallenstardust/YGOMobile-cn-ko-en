package com.ourygo.ygomobile.util

import android.util.Log
import cn.garymb.ygomobile.App
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException

object FileUtil {
    //------------------------------------文件相关方法------------------------------------------------------------------------
    //获取指定目录下所有文件夹的绝对路径
    fun getFolder(s: String?): List<String> {
        val list: MutableList<String> = ArrayList()
        val f = File(s).listFiles()
        for (ff in f) {
            if (ff.isDirectory) {
                list.add(ff.absolutePath)
            }
        }
        return list
    }

    //获取路径文件名
    fun getFilename(s: String?): String? {
        if (s != null) {
            val ss = s.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            return ss[ss.size - 1]
        }
        return null
    }

    //删除文件
    fun delFile(s: String?): Boolean {
        val file = File(s)
        return if (file.isDirectory) {
            var b = true
            for (ss in file.listFiles()) {
                Log.e("删除", "删除" + ss.path)
                b = b and delFile(ss.path)
            }
            b = b and file.delete()
            b
        } else {
            //如果文件路径所对应的文件存在,并且是一个文件,则直接删除
            if (file.exists() && file.isFile) {
                Log.e("正在删除", "正在删除" + file.path)
                if (file.delete()) {
                    true
                } else {
                    false
                }
            } else {
                false
            }
        }
    }

    //重命名文件
    fun reFileName(Path: String, name: String): String? {
        val file = File(Path)
        var rePath: String? = null
        if (file.exists()) {
            //获取文件名以外的路径
            val abb = Path.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            var lj = ""
            for (i in 0 until abb.size - 1) {
                lj += abb[i] + "/"
            }
            rePath = lj + name
            //重命名
            file.renameTo(File(rePath))
        }
        return rePath
    }

    @JvmStatic
	@Throws(FileNotFoundException::class, IOException::class)
    fun copyFile(oldPath: String, newPath: String, isname: Boolean): String {

        //判断复制后的路径是否含有文件名,如果没有则加上
        var newPath = newPath
        if (!isname) {
            //由于newPath是路径加文件名,所以获取要复制的文件名与复制后的路径组成新的newPath
            val abb = oldPath.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            newPath = newPath + "/" + abb[abb.size - 1]
        }
        val fis = FileInputStream(oldPath)
        val fos = FileOutputStream(newPath)
        val buf = ByteArray(1024)
        var len = 0
        while (fis.read(buf).also { len = it } != -1) {
            fos.write(buf, 0, len)
        }
        fos.close()
        fis.close()
        return newPath
    }

    @Throws(FileNotFoundException::class, IOException::class)
    fun moveFile(oldPath: String, newPath: String, isname: Boolean) {

        //判断复制后的路径是否含有文件名,如果没有则加上
        var newPath = newPath
        if (!isname) {
            //由于newPath是路径加文件名,所以获取要复制的文件名与复制后的路径组成新的newPath
            val abb = oldPath.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            newPath = newPath + "/" + abb[abb.size - 1]
        }
        val fis = FileInputStream(oldPath)
        val fos = FileOutputStream(newPath)
        val buf = ByteArray(1024)
        var len = 0
        while (fis.read(buf).also { len = it } != -1) {
            fos.write(buf, 0, len)
        }
        fos.close()
        fis.close()
        //删除文件
        val file = File(oldPath)
        if (file.exists() && file.isFile) {
            file.delete()
        }
    }

    /*
	 *检查文件夹是否存在,不存在则创建
	 *path:文件夹路径
	 */
    fun directoryCreate(path: String?) {
        val f = File(path)
        if (!f.exists() || !f.isDirectory) {
            f.mkdir()
        }
    }

    /*
	 *检查文件是否存在,不存在则创建
	 *path:文件路径
	 */
    @Throws(IOException::class)
    fun fileCreate(path: String?) {
        val f = File(path)
        if (!f.exists()) {
            f.createNewFile()
        }
    }

    /*复制asseta文件夹里面的文件到指定文件夹
	 *name:文件名
	 **path:要复制到的文件夹
	 *
	 */
    @Throws(FileNotFoundException::class, IOException::class)
    fun copyAssets(name: String, path: String) {


        //assets中文件名字
        //拿到输入流
        val `in` = App.get().assets.open(name)
        //打开输出流
        val out = FileOutputStream(path + name)
        var len = -1
        val bytes = ByteArray(1024)
        //不断读取输入流
        while (`in`.read(bytes).also { len = it } != -1) {
            //写到输出流中
            out.write(bytes, 0, len)
        }
        out.close()
        `in`.close()
    }
}