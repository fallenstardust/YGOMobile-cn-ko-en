package com.ourygo.ygomobile.ui.activity

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.text.TextUtils
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import cn.garymb.ygomobile.AppsSettings
import cn.garymb.ygomobile.Constants
import cn.garymb.ygomobile.bean.events.DeckFile
import cn.garymb.ygomobile.core.IrrlichtBridge
import cn.garymb.ygomobile.lite.R
import cn.garymb.ygomobile.loader.CardLoader
import cn.garymb.ygomobile.loader.DeckLoader
import cn.garymb.ygomobile.loader.ImageLoader
import cn.garymb.ygomobile.utils.DeckUtil
import com.chad.library.adapter.base.BaseQuickAdapter
import com.feihua.dialogutils.util.DialogUtils
import com.ourygo.ygomobile.adapter.DeckListBQAdapter
import com.ourygo.ygomobile.util.HandlerUtil
import com.ourygo.ygomobile.util.IntentUtil
import com.ourygo.ygomobile.util.LogUtil
import com.ourygo.ygomobile.util.OYUtil
import com.ourygo.ygomobile.util.Record
import com.ourygo.ygomobile.util.ShareUtil
import com.ourygo.ygomobile.util.SharedPreferenceUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Create By feihua  On 2021/11/10
 */
class DeckManagementActivity : ListAndUpdateActivity() {
    private lateinit var deckListAdp: DeckListBQAdapter
    private var imageLoader: ImageLoader? = null
    private val dialogUtils: DialogUtils by lazy {
        DialogUtils.getInstance(this)
    }
    var handler: Handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            super.handleMessage(msg)
            when (msg.what) {
                SHARE_DECK_URI_OK -> {
                    dialogUtils.dis()
                    ShareUtil.share(this@DeckManagementActivity, msg.obj.toString())
                }

                SHARE_DECK_FILE_OK -> {}
                DECK_LIST_LOAD_OK -> {
                    LogUtil.time("DeckManagement", "刷新完毕")
                    deckListAdp.removeAllHeaderView()
                    initHeadView()
                    deckListAdp.setList(msg.obj as List<DeckFile?>)
                    srl_update.isRefreshing = false
                }
            }
        }
    }
    private val headerView: View by lazy {
        LayoutInflater.from(this).inflate(R.layout.deck_management_header, null)
    }
    private val visitView: View by lazy {
        LayoutInflater.from(this).inflate(R.layout.deck_management_header1, null)
    }
    private var tv_download: TextView? = null
    private var tv_close: TextView? = null
    private var tv_visit: TextView? = null
    private var tv_close_visit: TextView? = null
    private val scope by lazy {
        CoroutineScope(Job())
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initView()
    }

    private fun initView() {
        LogUtil.setLastTime()
        LogUtil.time("DeckManagement", "组件初始化")
        imageLoader = ImageLoader()
        LogUtil.time("DeckManagement", "图片初始化")
        deckListAdp = DeckListBQAdapter(imageLoader, ArrayList())
        rv_list.adapter = deckListAdp
        deckListAdp.addChildClickViewIds(R.id.iv_edit, R.id.iv_share, R.id.iv_del)
        deckListAdp.setOnItemChildClickListener { adapter: BaseQuickAdapter<*, *>?, view: View, position: Int ->
            val deckFile = deckListAdp.getItem(position)
            when (view.id) {
                R.id.iv_edit -> IntentUtil.startYGODeck(
                    this@DeckManagementActivity,
                    deckFile.typeName,
                    deckFile.name
                )

                R.id.iv_share -> shareDeck(deckFile)
                R.id.iv_del -> {
                    deckListAdp.removeAt(position)
                    OYUtil.snackShow(toolbar, "删除成功")
                    scope.launch(Dispatchers.IO) {
                        deckFile.pathFile.delete()
                    }
                }
            }
        }
        initToolbar("卡组管理")
        LogUtil.time("DeckManagement", "其他初始化")
        onRefresh()
    }

    fun initHeadView() {
        if (SharedPreferenceUtil.isShowEz && !OYUtil.isApp(Record.PACKAGE_NAME_EZ)) {
            tv_download = headerView.findViewById<TextView?>(R.id.tv_download)?.apply {
                setOnClickListener {
                    startActivity(
                        IntentUtil
                            .getWebIntent(this@DeckManagementActivity, "http://ez.ourygo.top/")
                    )
                }
            }
            tv_close = headerView.findViewById<TextView?>(R.id.tv_close)?.apply {
                setOnClickListener {
                    SharedPreferenceUtil.setIsShowEz(false)
                    deckListAdp.removeHeaderView(headerView)
                }
            }
            deckListAdp.addHeaderView(headerView)
        }
        if (SharedPreferenceUtil.isShowVisitDeck) {
            tv_visit = visitView.findViewById<TextView?>(R.id.tv_visit)?.apply {
                setOnClickListener {
                    dialogUtils.dialogt1(
                        null, "YGO-OY储存路径为内部储存/ygocore，如果你之前有使用过原版" +
                                "，可以打开原版软件，点击下边栏的卡组选项——功能菜单——备份/还原来导入或导出原版ygo中的卡组"
                    )
                    val tv_message = dialogUtils.messageTextView
                    tv_message.setLineSpacing(OYUtil.dp2px(3f).toFloat(), 1f)
                }
            }
            tv_close_visit = visitView.findViewById<TextView?>(R.id.tv_close_visit)?.apply {
                SharedPreferenceUtil.setShowVisitDeck(false)
                deckListAdp.removeHeaderView(visitView)
            }
            deckListAdp.addHeaderView(visitView)
        }
    }

    private fun shareDeck(deckFile: DeckFile) {
        dialogUtils.dialogj1(null, "卡组码生成中，请稍等")
//        val itemDataList: MutableList<ItemData> = java.util.ArrayList()
//        itemDataList.add(ItemData())
//        dialogUtils.dialogBottomSheetListIconText(
//            "分享方式",
//            listOf<ItemData>(
//                ItemData.toItemData(R.drawable.ic_deck_code, "卡组码分享"),
//                ItemData.toItemData(R.drawable.ic_file, "文件分享")
//            ),
//            true
//        ).setOnITItemClickListener { position: Int ->
//            dialogUtils.dis()
//            when (position) {
//                0 -> shareDeck2Uri(deckFile)
//                1 -> shareDeck2File(deckFile)
//            }
//        }

        Thread {
            val deck = DeckLoader.readDeck(
                CardLoader(this@DeckManagementActivity),
                deckFile.pathFile,
                null
            ).toDeck()
            val message = deck.toUri().toString()
            Log.d(
                "feihua", "卡组路径："
                        + deckFile.pathFile
                        + "  " + deck.deckCount
                        + "  " + deck.mainCount
                        + "  " + deck.extraCount
                        + "  " + deck.sideCount
            )
            val message1 = """
                点击或复制打开YGO查看卡组《${deckFile.name}》：
                $message
                """.trimIndent()
            runOnUiThread {
                dialogUtils.dis()
                dialogUtils.dialogBottomSheet(R.layout.deck_share_dialog).apply {
                    ImageLoader().bindImage(
                        findViewById(R.id.iv_icon),
                        deckFile.firstCode.toLong(),
                        ImageLoader.Type.small
                    )
                    findViewById<TextView>(R.id.tv_name).text = deckFile.fileName
                    findViewById<LinearLayout>(R.id.ll_share_file)
                        .setOnClickListener {
                            shareDeck2File(deckFile)
                        }
                    findViewById<TextView>(R.id.tv_deck_code).apply {
                        text = message
                        setOnClickListener { startActivity(IntentUtil.getUrlIntent(message)) }
                    }
                    findViewById<TextView>(R.id.tv_copy).setOnClickListener {
                        OYUtil.copyMessage(message1)
                        OYUtil.show("已复制卡组码到剪贴板")
                        dialogUtils.dis()
                    }

                }
            }
        }.start()


    }

    private fun shareDeck2File(deckFile: DeckFile) {
        dialogUtils.dis()
        val category = deckFile.pathFile.parent
        val fname = deckFile.fileName
        val intent = Intent(IrrlichtBridge.ACTION_SHARE_FILE)
        intent.addCategory(Intent.CATEGORY_DEFAULT)
        intent.putExtra(IrrlichtBridge.EXTRA_SHARE_TYPE, "ydk")
        if (TextUtils.equals(category, AppsSettings.get().deckDir)) {
            intent.putExtra(IrrlichtBridge.EXTRA_SHARE_FILE, fname)
        } else if (TextUtils.equals(category, AppsSettings.get().packDeckDir)) {
            intent.putExtra(IrrlichtBridge.EXTRA_SHARE_FILE, Constants.CORE_PACK_PATH + "/" + fname)
        } else {
            val cname = DeckUtil.getDeckTypeName(deckFile.pathFile.absolutePath)
            intent.putExtra(IrrlichtBridge.EXTRA_SHARE_FILE, "$cname/$fname")
        }
        intent.setPackage(packageName)
        try {
            startActivity(intent)
        } catch (e: Throwable) {
            Toast.makeText(context, "dev error:not found activity.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun shareDeck2Uri(deckFile: DeckFile) {

        Thread {
            val deck = DeckLoader.readDeck(
                CardLoader(this@DeckManagementActivity),
                deckFile.pathFile,
                null
            ).toDeck()
            var message = deck.toUri().toString()
            Log.d(
                "feihua",
                "卡组路径：" + deckFile.pathFile + "  " + deck.deckCount + "  " + deck.mainCount + "  " + deck.extraCount + "  " + deck.sideCount
            )
            message = """
                点击或复制打开YGO查看卡组《${deckFile.name}》：
                $message
                """.trimIndent()
            HandlerUtil.sendMessage(handler, SHARE_DECK_URI_OK, message)
        }.start()
    }

    override fun onRefresh() {
        super.onRefresh()
        LogUtil.time("DeckManagement", "开始刷新")
        Thread { HandlerUtil.sendMessage(handler, DECK_LIST_LOAD_OK, DeckUtil.getDeckAllList()) }
            .start()
    }

    companion object {
        private const val SHARE_DECK_URI_OK = 0
        private const val SHARE_DECK_FILE_OK = 1
        private const val DECK_LIST_LOAD_OK = 2
    }
}