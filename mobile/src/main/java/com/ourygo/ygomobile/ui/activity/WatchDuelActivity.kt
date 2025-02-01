package com.ourygo.ygomobile.ui.activity

import android.os.Bundle
import android.util.Log
import android.view.View
import cn.garymb.ygomobile.bean.ServerInfo
import com.chad.library.adapter.base.BaseQuickAdapter
import com.ourygo.ygomobile.adapter.DuelRoomBQAdapter
import com.ourygo.ygomobile.base.listener.OnDuelRoomListener
import com.ourygo.ygomobile.bean.DuelRoom
import com.ourygo.ygomobile.util.McUserManagement
import com.ourygo.ygomobile.util.OYUtil
import com.ourygo.ygomobile.util.Record
import com.ourygo.ygomobile.util.WatchDuelManagement
import com.ourygo.ygomobile.util.YGOUtil.joinGame

/**
 * Create By feihua  On 2021/11/3
 */
class WatchDuelActivity : ListAndUpdateActivity(), OnDuelRoomListener {
    private val TAG: String = "WatchDuelActivity"
    private val duelManagement by lazy {
        WatchDuelManagement.instance
    }
    private val duelRoomBQAdapter by lazy {
        DuelRoomBQAdapter(this, ArrayList())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initView()
    }

    private fun initView() {
        duelManagement.addListener(this)
        rv_list.adapter = duelRoomBQAdapter
        duelRoomBQAdapter.setOnItemClickListener { _: BaseQuickAdapter<*, *>?, _: View?, position: Int ->
            val duelRoom = duelRoomBQAdapter.getItem(position)
            Log.e("WatchActivity", "密码" + duelRoom.id)
            Log.e("WatchActivity", "用户id" + McUserManagement.instance.user!!.external_id)
            val password = OYUtil.getWatchDuelPassword(
                duelRoom.id,
                McUserManagement.instance.user!!.external_id
            )
            val serverInfo = ServerInfo()
            when (duelRoom.arenaType) {
                DuelRoom.TYPE_ARENA_MATCH -> {
                    serverInfo.serverAddr = Record.HOST_MC_MATCH
                    serverInfo.port = Record.PORT_MC_MATCH
                }

                DuelRoom.TYPE_ARENA_FUN, DuelRoom.TYPE_ARENA_AI, DuelRoom.TYPE_ARENA_FUN_MATCH, DuelRoom.TYPE_ARENA_FUN_SINGLE, DuelRoom.TYPE_ARENA_FUN_TAG -> {
                    serverInfo.serverAddr = Record.HOST_MC_OTHER
                    serverInfo.port = Record.PORT_MC_OTHER
                }

                else -> {
                    OYUtil.show("未知房间，请更新软件后进入")
                    return@setOnItemClickListener
                }
            }
            serverInfo.playerName = McUserManagement.instance.user!!.username
            joinGame(this@WatchDuelActivity, serverInfo, password)
        }
        initToolbar("观战")
        onRefresh()
    }

    override fun onRefresh() {
        super.onRefresh()
        duelManagement.start()
    }

    override fun onDestroy() {
        duelManagement.closeConnect()
        super.onDestroy()
    }

    override fun onInit(duelRoomList: List<DuelRoom>) {
        srl_update.isRefreshing = false
        srl_update.isEnabled = false
        duelRoomBQAdapter.addData(duelRoomList)
        initToolbar("观战（" + duelRoomBQAdapter.data.size + "）")
    }

    override fun onCreate(duelRoomList: List<DuelRoom>) {
        duelRoomBQAdapter.addData(duelRoomList)
        initToolbar("观战（" + duelRoomBQAdapter.data.size + "）")
    }

    override fun onUpdate(duelRoomList: List<DuelRoom>) {}
    override fun onDelete(duelRoomList: List<DuelRoom>) {
        duelRoomBQAdapter.remove(duelRoomList)
        initToolbar("观战（" + duelRoomBQAdapter.data.size + "）")
    }

    override fun isListenerEffective(): Boolean {
        return OYUtil.isContextExisted(this)
    }
}