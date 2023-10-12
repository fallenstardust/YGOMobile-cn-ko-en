package com.ourygo.ygomobile.util

import android.app.Activity
import android.util.Log
import cn.garymb.ygodata.YGOGameOptions
import cn.garymb.ygomobile.App
import cn.garymb.ygomobile.AppsSettings
import cn.garymb.ygomobile.Constants
import cn.garymb.ygomobile.YGOStarter
import cn.garymb.ygomobile.bean.ServerInfo
import cn.garymb.ygomobile.ui.plus.VUiKit
import cn.garymb.ygomobile.utils.IOUtils
import cn.garymb.ygomobile.utils.StringUtils
import cn.garymb.ygomobile.utils.SystemUtils
import cn.garymb.ygomobile.utils.XmlUtils
import com.file.zip.ZipEntry
import com.file.zip.ZipFile
import com.ourygo.ygomobile.base.listener.OnLfListQueryListener
import com.ourygo.ygomobile.base.listener.OnYGOServerListQueryListener
import com.ourygo.ygomobile.bean.Lflist
import com.ourygo.ygomobile.bean.Replay
import com.ourygo.ygomobile.bean.YGOServer
import com.ourygo.ygomobile.bean.YGOServerList
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date

object YGOUtil {
    //加入游戏
    @JvmStatic
    @JvmOverloads
    fun joinGame(
        activity: Activity?,
        serverInfo: ServerInfo? = null,
        password: String? = null,
        request: Int = 0
    ) {
        var options: YGOGameOptions? = null
        serverInfo?.let {
            options = YGOGameOptions().apply {
                mServerAddr = it.serverAddr
                mUserName = it.playerName
                mPort = it.port
                mRoomName = password
            }
        }
        YGOStarter.startGame(request, activity, options)
    }

    //获取服务器列表
    @JvmStatic
    fun getYGOServerList(onYGOServerListQueryListener: OnYGOServerListQueryListener) {
        val xmlFile = File(App.get().filesDir, Constants.SERVER_FILE)
        Log.e("YGOUtil", xmlFile.exists().toString() + "获取列表" + xmlFile.absolutePath)
        VUiKit.defer().`when`<YGOServerList> {
            val assetList = readList(App.get().assets.open(Constants.ASSET_SERVER_LIST))
            val fileList = (if (xmlFile.exists()) readList(FileInputStream(xmlFile)) else null)
                ?: return@`when` assetList
            if (fileList.vercode < assetList!!.vercode) {
                xmlFile.delete()
                return@`when` assetList
            }
            fileList
        }.done { list: YGOServerList? ->
            list?.let {
                onYGOServerListQueryListener.onYGOServerListQuery(it)
            }
        }
    }

    val replayList: List<Replay>
        get() {
            val replayList: MutableList<Replay> = ArrayList()
            val file = File(AppsSettings.get().replayDir)
            for (file1 in file.listFiles()!!) {
                if (!file1.isDirectory && file1.name.endsWith(".yrp")) {
                    val replay = Replay()
                    replay.name = file1.name.substring(0, file1.name.indexOf(".yrp"))
                    replay.time =
                        SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date(file1.lastModified()))
                    replayList.add(replay)
                }
            }
            return replayList
        }

    private fun readList(`in`: InputStream): YGOServerList? {
        var list: YGOServerList? = null
        try {
            list = XmlUtils.get().getObject(YGOServerList::class.java, `in`)
        } catch (_: Exception) {
        } finally {
            IOUtils.close(`in`)
        }
        return list
    }

    /**
     * 添加服务器信息
     * @param ygoService 服务器信息
     */
    fun addYGOServer(ygoService: YGOServer) {
        getYGOServerList { serverList: YGOServerList ->
            val ygoServers = serverList.serverInfoList
            ygoServers.add(ygoService)
            setYGOServer(ygoServers)
        }
    }

    /**
     * 保存服务器列表
     * @param ygoServers 服务器列表
     */
    @JvmStatic
    fun setYGOServer(ygoServers: List<YGOServer>?) {
        val xmlFile = File(App.get().filesDir, Constants.SERVER_FILE)
        Log.e("YGOUtil", "路径" + xmlFile.absolutePath)
        var outputStream: OutputStream? = null
        try {
            outputStream = FileOutputStream(xmlFile)
            XmlUtils.get()
                .saveXml(YGOServerList(SystemUtils.getVersion(App.get()), ygoServers), outputStream)
            Log.e("YGOUtil", "保存成功")
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("YGOUtil", "保存失败")
        } finally {
            IOUtils.close(outputStream)
        }
    }

    @JvmStatic
    fun findLfListListener(onLfListQueryListener: OnLfListQueryListener) {
        OkhttpUtil.get(Record.YGO_LFLIST_URL, callback = object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                onLfListQueryListener.onLflistQuery(null, e.toString())
            }

            @Throws(IOException::class)
            override fun onResponse(call: Call, response: Response) {
                val lflist = response.body()!!.string()
                val lflistHeader = lflist.substring(1, lflist.indexOf("\n"))
                var index = 0
                val lflistNameList: MutableList<Lflist> = ArrayList()
                while (index < lflistHeader.length) {
                    var end: Int
                    val start: Int = lflistHeader.indexOf("[", index)
                    if (start != -1) {
                        end = lflistHeader.indexOf("]", start)
                        if (end != -1) {
                            var lflistName = lflistHeader.substring(start + 1, end)
                            var type = Lflist.TYPE_OCG
                            //获取禁卡表名和禁卡表类型的分割符位置
                            val typeStart = lflistName.indexOf(" ")
                            //如果存在并且不在最后一个即分割
                            if (typeStart != -1 && typeStart != lflistName.length - 1) {
                                val typeName = lflistName.substring(typeStart + 1)
                                lflistName = lflistName.substring(0, typeStart)
                                type = if (typeName == "TCG") {
                                    Lflist.TYPE_TCG
                                } else {
                                    Lflist.TYPE_OCG
                                }
                            }
                            lflistNameList.add(Lflist.toLflist(lflistName, "", type))
                            index = end
                        } else {
                            break
                        }
                    } else {
                        break
                    }
                }
                onLfListQueryListener.onLflistQuery(lflistNameList, null)
            }
        })
    }

    /**
     * 解析zip或者ypk的file下内置的txt文件里的服务器name、host、prot
     * @param file 扩展卡文件
     */
    @JvmStatic
    fun loadServerInfoFromZipOrYpk(file: File) {
        if (file.name.endsWith(".zip") || file.name.endsWith(".ypk")) {
            try {
                var serverName: String? = null
                var serverHost: String? = null
                var serverPort: String? = null
                val zipFile = ZipFile(file.absoluteFile, "GBK")
                val entries = zipFile.entries
                var entry: ZipEntry
                while (entries.hasMoreElements()) {
                    entry = entries.nextElement()
                    if (!entry.isDirectory) {
                        if (entry.name.endsWith(".ini")) {
                            val inputStream = InputStreamReader(
                                zipFile.getInputStream(entry),
                                StandardCharsets.UTF_8
                            )
                            val reader = BufferedReader(inputStream)
                            var line: String?
                            while (reader.readLine().also { line = it } != null) {
                                line?.apply {
                                    if (startsWith("[YGOProExpansionPack]") ||
                                        startsWith("FileName") ||
                                        startsWith("PackName") ||
                                        startsWith("PackAuthor") ||
                                        startsWith("PackHomePage") ||
                                        startsWith("[YGOMobileAddServer]")
                                    ) {
                                        return@apply
                                    }
                                    if (startsWith("ServerName")) {
                                        val words = trim { it <= ' ' }.split("[\t| =]+".toRegex())
                                            .dropLastWhile { it.isEmpty() }
                                            .toTypedArray()
                                        if (words.size >= 2) {
                                            serverName = words[1]
                                        }
                                    }
                                    if (startsWith("ServerHost")) {
                                        val words = trim { it <= ' ' }.split("[\t| =]+".toRegex())
                                            .dropLastWhile { it.isEmpty() }
                                            .toTypedArray()
                                        if (words.size >= 2) {
                                            serverHost = words[1]
                                        }
                                    }
                                    if (startsWith("ServerPort")) {
                                        val words = trim { it <= ' ' }.split("[\t| =]+".toRegex())
                                            .dropLastWhile { it.isEmpty() }
                                            .toTypedArray()
                                        if (words.size >= 2) {
                                            serverPort = words[1]
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if (serverName != null
                    && (StringUtils.isHost(serverHost) || StringUtils.isValidIP(serverHost))
                    && StringUtils.isNumeric(serverPort)
                ) {
                    getYGOServerList {
                        for (serviceInfo in it.serverInfoList) {
                            //如果ip和端口都已经存在，则不再添加该服务器
                            if (serverHost == serviceInfo.serverAddr
                                && serverPort == serviceInfo.port.toString()
                            ) {
                                return@getYGOServerList
                            }
                        }
                        addYGOServer(YGOServer.toYGOServer(serverName).apply {
                            serverAddr = serverHost
                            port = Integer.valueOf(serverPort!!)
                        })
                    }
//                    AddServer(context, serverName, serverHost, Integer.valueOf(serverPort), "Knight of Hanoi");

                } else {
                    OYUtil.show("无法解析当前先行卡服务器信息")
                }
                zipFile.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }
}