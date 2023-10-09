package com.ourygo.ygomobile.ui.activity

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cn.garymb.ygomobile.AppsSettings
import cn.garymb.ygomobile.lite.R
import cn.garymb.ygomobile.ui.activities.BaseActivity
import com.chad.library.adapter.base.BaseQuickAdapter
import com.feihua.dialogutils.util.DialogUtils
import com.ourygo.ygomobile.adapter.SettingRecyclerViewAdapter1
import com.ourygo.ygomobile.bean.SettingItem
import com.ourygo.ygomobile.util.ExpansionsUtil
import com.ourygo.ygomobile.util.HandlerUtil
import com.ourygo.ygomobile.util.OYUtil
import com.ourygo.ygomobile.util.Record
import com.ourygo.ygomobile.util.SharedPreferenceUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ocgcore.DataManager
import java.io.File

/**
 * Create By feihua  On 2021/10/23
 */
class ExpansionsSettingActivity : BaseActivity() {
    private lateinit var rvList: RecyclerView
    private lateinit var settingAdp: SettingRecyclerViewAdapter1
    private var currentIsEx = false
    private val du by lazy {
        DialogUtils.getInstance(this)
    }
    var handler: Handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            when (msg.what) {
                HANDLE_START_EXPANSIONS -> {
                    du!!.dis()
                    openExpansions()
                }

                ADVACE_DEL_ALL -> {
                    du!!.dis()
                    if (msg.obj as Boolean) {
                        OYUtil.snackShow(toolbar, "删除成功")
                        removeOther()
                    } else {
                        OYUtil.snackWarning(toolbar, "删除失败")
                    }
                }

                ADVACE_DEL_OK -> {
                    du!!.dis()
                    OYUtil.snackShow(toolbar, "删除成功")
                    val position = msg.obj as Int
                    var islast = false
                    var isNext = false
                    if (position != 0) islast = true
                    if (position != settingAdp.itemCount) isNext = true
                    settingAdp.removeAt(position)
                    if (islast) settingAdp.notifyItemChanged(position - 1)
                    if (isNext) settingAdp.notifyItemChanged(position)
                }

                ADVACE_DEL_EXCEPTION -> {
                    du!!.dis()
                    OYUtil.snackWarning(toolbar, "删除失败")
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.expansions_setting_activity)
        initView()
    }

    private fun initView() {
        rvList.layoutManager = LinearLayoutManager(this)
        initToolbar("扩展卡包")
        currentIsEx = AppsSettings.get().isReadExpansions
        val settingItemList: MutableList<SettingItem> = ArrayList()
        var settingItem: SettingItem = SettingItem.toSettingItem(
            ID_EXPANSIONS_SWITCH,
            "扩展卡包",
            SettingItem.ITEM_SWITCH,
            GROUP_SWITCH
        )
        settingItem.setObject(currentIsEx)
        settingItemList.add(settingItem)
        settingItem = SettingItem.toSettingItem(ID_DEL_ALL, "删除所有扩展卡包", GROUP_SWITCH)
        settingItem.isNext = false
        settingItem.nameColor = OYUtil.c(R.color.red)
        settingItemList.add(settingItem)
        settingAdp = SettingRecyclerViewAdapter1(this, settingItemList)
        rvList.adapter = settingAdp
        settingAdp.setOnSettingCheckListener { id: Int, isCheck: Boolean ->
            Log.e("ExpansionsSettin", "额外卡包$isCheck")
            when (id) {
                ID_EXPANSIONS_SWITCH -> {
                    SharedPreferenceUtil.setReadExpansions(isCheck)
                    if (isCheck) {
                        du.dialogj1(null, "启用中，请稍等")
                        //设置使用额外卡库后重新加载卡片数据
                        lifecycleScope.launch(Dispatchers.IO) {
                            DataManager.get().load(true)
                            handler.sendEmptyMessage(HANDLE_START_EXPANSIONS)
                        }
                    } else {
                        closeExpansions()
                    }
                }
            }
        }
        settingAdp.setOnItemClickListener { _: BaseQuickAdapter<*, *>?, _: View?, position: Int ->
            val settingItem1 = settingAdp.getItem(position)
            when (settingItem1.id) {
                ID_DEL_ALL -> {
                    du.dialogj1(null, "删除中，请稍等")
                    ExpansionsUtil.delExpansionsAll { _: String?, isOk: Boolean ->
                        Log.e("ExpansionsUtil", "接收回调$isOk")
                        HandlerUtil.sendMessage(handler, ADVACE_DEL_ALL, isOk)
                    }
                }
            }
        }
        toolbar!!.setOnclickListener { onBackPressed() }
        settingAdp.addChildClickViewIds(R.id.tv_message)
        settingAdp.setOnItemChildClickListener { _: BaseQuickAdapter<*, *>?, view: View, position: Int ->
            val settingItem1 = settingAdp.getItem(position)
            when (settingItem1.id) {
                ID_EXPANSIONS_CARD -> when (view.id) {
                    R.id.tv_message -> {
                        Log.e("Expansion", "删除回调")
                        du.dialogj1(null, "删除中，请稍等")
                        ExpansionsUtil.delExpansions(settingItem1.getObject() as File) { _: String?, isOk: Boolean ->
                            HandlerUtil.sendMessage(
                                handler,
                                if (isOk) "" else "删除失败",
                                ADVACE_DEL_OK,
                                position,
                                ADVACE_DEL_EXCEPTION
                            )
                        }
                    }
                }
            }
        }
        if (currentIsEx) openExpansions()
    }

    override fun onBackPressed() {
        val resultCode =
            if (currentIsEx == AppsSettings.get().isReadExpansions) RESULT_CANCELED else RESULT_OK
        setResult(resultCode)
        super.onBackPressed()
    }

    private fun closeExpansions() {
        removeOther()
    }

    private fun openExpansions() {
        loadingExpansions()
    }

    private fun removeOther() {
        val settingItemList: ArrayList<SettingItem> = ArrayList(
            settingAdp.data
        )
        while (settingItemList.size > 2) {
            settingItemList.removeAt(settingItemList.size - 1)
        }
        settingAdp.setNewInstance(settingItemList)
    }

    private fun loadingExpansions() {
        val mSettingItem =
            SettingItem.toSettingItem(ID_EXPANSIONS_CARD, "删除所有扩展卡包", GROUP_CARD, "已安装")
        mSettingItem.isNext = false
        mSettingItem.messageColor = OYUtil.c(R.color.red)
        mSettingItem.message = "删除"
        mSettingItem.isContent = false
        mSettingItem.isLoading = true
        settingAdp.addData(mSettingItem)
        ExpansionsUtil.findExpansionsList { fileList: List<File> ->
            val settingItemList: MutableList<SettingItem> = ArrayList()
            for (file in fileList) {
                var name = file.name
                if (name == Record.ARG_OTHER) name = "其他卡包"
                val settingItem1 =
                    SettingItem.toSettingItem(ID_EXPANSIONS_CARD, name, GROUP_CARD, "已安装")
                settingItem1.isNext = false
                settingItem1.messageColor = OYUtil.c(R.color.red)
                settingItem1.message = "删除"
                settingItem1.setObject(file)
                settingItemList.add(settingItem1)
            }
            removeOther()
            settingAdp.addData(settingItemList)
        }
    }

    companion object {
        private const val ID_EXPANSIONS_SWITCH = 0
        private const val ID_DEL_ALL = 1
        private const val ID_EXPANSIONS_CARD = 2
        private const val GROUP_SWITCH = 0
        private const val GROUP_CARD = 1
        private const val HANDLE_START_EXPANSIONS = 0
        private const val ADVACE_DEL_ALL = 1
        private const val ADVACE_DEL_OK = 2
        private const val ADVACE_DEL_EXCEPTION = 3
    }
}