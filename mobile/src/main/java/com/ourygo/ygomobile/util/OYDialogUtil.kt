package com.ourygo.ygomobile.util

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.os.Handler
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import cn.garymb.ygomobile.AppsSettings
import cn.garymb.ygomobile.Constants
import cn.garymb.ygomobile.bean.Deck
import cn.garymb.ygomobile.lite.R
import cn.garymb.ygomobile.ui.cards.deck.DeckUtils
import cn.garymb.ygomobile.utils.FileUtils
import com.feihua.dialogutils.adapter.IconTextRecyclerViewAdapter
import com.feihua.dialogutils.bean.ItemData
import com.feihua.dialogutils.util.DialogUtils
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.ourygo.ygomobile.OYApplication
import com.ourygo.ygomobile.adapter.RoomSpinnerAdapter
import com.ourygo.ygomobile.base.listener.OnSetBgListener
import com.ourygo.ygomobile.bean.UpdateInfo
import com.ourygo.ygomobile.bean.YGOServer
import com.ourygo.ygomobile.bean.YGOServerList
import com.ourygo.ygomobile.ui.activity.DeckManagementActivity
import com.ourygo.ygomobile.util.FileUtil.copyFile
import com.ourygo.ygomobile.util.OYUtil.show
import com.ourygo.ygomobile.util.YGOUtil.getYGOServerList
import java.io.File
import java.io.IOException

object OYDialogUtil {
    const val DECK_TYPE_MESSAGE = 0
    const val DECK_TYPE_DECK = 1
    const val DECK_TYPE_PATH = 2
    const val BG_TYPE_DUEL = 0
    const val BG_TYPE_MENU = 1
    const val BG_TYPE_DECK = 2

    @JvmStatic
    fun dialogAiList(activity: Activity?, ygoServer: YGOServer?) {
        val builder: Dialog = BottomSheetDialog(activity!!)
        val view = LayoutInflater.from(activity).inflate(R.layout.room_ai_dialog, null)
        builder.setContentView(view)
        val window = builder.window
        window?.findViewById<View>(R.id.design_bottom_sheet)
            ?.setBackgroundResource(android.R.color.transparent)
        builder.show()
        val itemDataList: MutableList<ItemData> = ArrayList()
        itemDataList.add(ItemData.toItemData(R.drawable.ic_ai, "悠悠"))
        itemDataList.add(ItemData.toItemData(R.drawable.ic_ai, "悠悠王"))
        itemDataList.add(ItemData.toItemData(R.drawable.ic_ai, "琪露诺"))
        itemDataList.add(ItemData.toItemData(R.drawable.ic_ai, "谜之剑士LV4"))
        itemDataList.add(ItemData.toItemData(R.drawable.ic_ai, "复制植物"))
        itemDataList.add(ItemData.toItemData(R.drawable.ic_ai, "尼亚"))
        itemDataList.add(ItemData.toItemData(R.drawable.ic_ai, "永远之魂"))
        itemDataList.add(ItemData.toItemData(R.drawable.ic_ai, "比特机灵"))
        itemDataList.add(ItemData.toItemData(R.drawable.ic_ai, "复制梁龙"))
        itemDataList.add(ItemData.toItemData(R.drawable.ic_ai, "奇異果"))
        itemDataList.add(ItemData.toItemData(R.drawable.ic_ai, "奇魔果"))
        itemDataList.add(ItemData.toItemData(R.drawable.ic_ai, "MAX龍果"))
        itemDataList.add(ItemData.toItemData(R.drawable.ic_ai, "幻煌果"))
        itemDataList.add(ItemData.toItemData(R.drawable.ic_ai, "燃血鬥士"))
        itemDataList.add(ItemData.toItemData(R.drawable.ic_ai, "報社鬥士"))
        itemDataList.add(ItemData.toItemData(R.drawable.ic_ai, "我太帅了"))
        itemDataList.add(ItemData.toItemData(R.drawable.ic_ai, "玻璃女巫"))
        val rv_new_file_list = view.findViewById<RecyclerView>(R.id.rv_list)
        rv_new_file_list.layoutManager = LinearLayoutManager(activity)
        val nFAdp = IconTextRecyclerViewAdapter(itemDataList, true)
        rv_new_file_list.adapter = nFAdp
        nFAdp.setOnITItemClickListener { position: Int ->
            builder.dismiss()
            YGOUtil.joinGame(activity, ygoServer, "AI#" + itemDataList[position].name)
        }
    }

    @JvmStatic
    fun dialogDASaveDeck(activity: Activity?, deckMessage: String?, deckType: Int) {
        dialogDASaveDeck(activity, deckMessage, null, deckType)
    }

    @JvmStatic
    fun dialogDASaveDeck(activity: Activity?, deckMessage: String?, deck: Deck?, deckType: Int) {
        val du = DialogUtils.getInstance(activity)
        val v = du.dialogBottomSheet(R.layout.da_save_deck_dialog)
        val dialog = du.dialog
        val tv_save_deck: TextView
        tv_save_deck = v.findViewById(R.id.tv_save_deck)
        tv_save_deck.setOnClickListener { v12: View? ->
            dialog.dismiss()
            du.dialogj1(null, "卡组保存中，请稍等")
            when (deckType) {
                DECK_TYPE_MESSAGE ->                     //如果是卡组文本
                    try {
                        //以当前时间戳作为卡组名保存卡组
                        val file = DeckUtils.save(
                            OYUtil.s(R.string.rename_deck) + System.currentTimeMillis(),
                            deckMessage
                        )
                        du.dis()
                        openYdk(activity, file)
                    } catch (e: IOException) {
                        e.printStackTrace()
                        OYUtil.show(OYUtil.s(R.string.save_failed_bcos) + e)
                    }

                DECK_TYPE_DECK -> {
                    if (deck == null) {
                        OYUtil.show("卡组信息为空，无法保存")
                        return@setOnClickListener
                    }
                    Log.e(
                        "OYDialogUtil",
                        "数量" + deck.mainCount + " " + deck.extraCount + " " + deck.sideCount
                    )
                    val file = deck.saveTemp(AppsSettings.get().deckDir)
                    if (!deck.isCompleteDeck) {
                        OYUtil.show("当前卡组缺少完整信息，将只显示已有卡片")
                    }
                    //                    try {
//                        FileUtil.copyFile(file.getAbsolutePath(), AppsSettings.get().getDeckDir(), false);
                    du.dis()
                    openYdk(activity, file)
                }

                DECK_TYPE_PATH -> try {
                    val file1 = File(
                        copyFile(
                            deckMessage!!, AppsSettings.get().deckDir, false
                        )
                    )
                    du.dis()
                    openYdk(activity, file1)
                } catch (e: IOException) {
                    OYUtil.show(OYUtil.s(R.string.save_failed_bcos) + e)
                    e.printStackTrace()
                }

                else -> du.dis()
            }
        }
    }

    private fun openYdk(context: Activity?, ydkFile: File) {
        val dialogUtils = DialogUtils.getInstance(context)
        val v = dialogUtils.dialogt(ydkFile.name, "卡组保存成功，是否打开?")
        val b1: Button
        val b2: Button
        b1 = v[0] as Button
        b2 = v[1] as Button
        b1.text = "取消"
        b2.text = "打开"
        b1.setOnClickListener { v1: View? -> dialogUtils.dis() }
        b2.setOnClickListener { v12: View? ->
            dialogUtils.dis()
            var name = ydkFile.name
            if (name.endsWith(".ydk")) name = name.substring(0, name.lastIndexOf("."))
            IntentUtil.startYGODeck(context, name)
        }
    }

    fun dialogDAJoinRoom(activity: Activity?, serverInfo: YGOServer, password: String?) {
        val du = DialogUtils.getInstance(activity)
        val v = du.dialogBottomSheet(R.layout.da_join_room_dialog)
        val tv_join_room: TextView
        val tv_host: TextView
        val tv_port: TextView
        val tv_password: TextView
        tv_join_room = v.findViewById(R.id.tv_join_room)
        tv_host = v.findViewById(R.id.tv_host)
        tv_port = v.findViewById(R.id.tv_port)
        tv_password = v.findViewById(R.id.tv_password)
        tv_host.text = serverInfo.serverAddr
        tv_port.text = serverInfo.port.toString() + ""
        tv_password.text = password
        tv_join_room.setOnClickListener { v12: View? ->
            if (!OYApplication.isIsInitRes()) {
                OYUtil.show("请等待资源加载完毕后加入游戏")
                return@setOnClickListener
            }
            OYUtil.closeKeyboard(du.dialog)
            du.dis()
            YGOUtil.joinGame(activity, serverInfo, password)
        }
    }

    @JvmStatic
    fun dialogJoinRoom(activity: Activity?, serverInfo: YGOServer?) {
        val du = DialogUtils.getInstance(activity)
        val v = du.dialogBottomSheet(R.layout.dialog_join_room)
        val dialog = du.dialog
        val tv_join_room: TextView
        val iv_close: ImageView
        val et_password: EditText
        val sp_room: Spinner
        val iv_switch: ImageView
        tv_join_room = v.findViewById(R.id.tv_join_room)
        iv_close = v.findViewById(R.id.iv_close)
        et_password = v.findViewById(R.id.et_password)
        sp_room = v.findViewById(R.id.sp_room)
        iv_switch = v.findViewById(R.id.iv_switch)
        iv_switch.setOnClickListener { v14: View? -> sp_room.performClick() }
        du.dialog.setOnDismissListener { dialog1: DialogInterface? -> OYUtil.closeKeyboard(du.dialog) }
        getYGOServerList { serverList: YGOServerList ->
            val serverInfoList = serverList.serverInfoList
            var position = 0
            if (serverInfo != null) for (i in serverInfoList.indices) {
                if (serverInfoList[i].name == serverInfo.name) position = i
            }
            //            roomSpinnerAdapter.setNewData(serverInfoList);
            val roomSpinnerAdapter = RoomSpinnerAdapter(activity, serverInfoList)
            sp_room.adapter = roomSpinnerAdapter
            sp_room.setSelection(position)
        }
        iv_close.setOnClickListener { v1: View? -> dialog.dismiss() }
        Handler().postDelayed({ OYUtil.showKeyboard(et_password) }, 100)
        et_password.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                if (TextUtils.isEmpty(s)) {
                    tv_join_room.text = OYUtil.s(R.string.random_match)
                } else {
                    tv_join_room.text = OYUtil.s(R.string.join_room)
                }
            }

            override fun afterTextChanged(s: Editable) {
                if (TextUtils.isEmpty(s)) {
                    tv_join_room.text = OYUtil.s(R.string.random_match)
                } else {
                    tv_join_room.text = OYUtil.s(R.string.join_room)
                }
            }
        })
        et_password.setOnKeyListener { v13: View?, keyCode: Int, event: KeyEvent? ->
            if (keyCode == KeyEvent.KEYCODE_ENTER) {
                if (!OYApplication.isIsInitRes()) {
                    OYUtil.show("请等待资源加载完毕后加入游戏")
                    return@setOnKeyListener true
                }
                OYUtil.closeKeyboard(dialog)
                dialog.dismiss()
                YGOUtil.joinGame(
                    activity,
                    (sp_room.adapter as RoomSpinnerAdapter).getItem(sp_room.selectedItemPosition),
                    et_password.text.toString().trim { it <= ' ' })
                return@setOnKeyListener true
            }
            false
        }
        tv_join_room.setOnClickListener { v12: View? ->
            if (!OYApplication.isIsInitRes()) {
                show("请等待资源加载完毕后加入游戏")
                return@setOnClickListener
            }
            OYUtil.closeKeyboard(dialog)
            dialog.dismiss()
            YGOUtil.joinGame(
                activity,
                (sp_room.adapter as RoomSpinnerAdapter).getItem(sp_room.selectedItemPosition),
                et_password.text.toString().trim()
            )

        }
    }

    @JvmStatic
    fun dialogcreateRoom(activity: Activity, serverInfo: YGOServer) {
        val du = DialogUtils.getInstance(activity)
        val v = du.dialogBottomSheet(R.layout.dialog_create_room)
        val tv_room: TextView
        val tv_ip: TextView
        val tv_port: TextView
        val tv_mode: TextView
        val tv_lflist: TextView
        val tv_password: TextView
        val bt_copy_room: TextView
        val tv_join_room: TextView
        val iv_close: ImageView
        val iv_share_qq: ImageView
        val iv_share_wechat: ImageView
        val iv_share_more: ImageView
        val rl_room: RelativeLayout
        tv_room = v.findViewById(R.id.tv_room)
        tv_ip = v.findViewById(R.id.tv_ip)
        tv_port = v.findViewById(R.id.tv_port)
        tv_mode = v.findViewById(R.id.tv_mode)
        tv_lflist = v.findViewById(R.id.tv_lflist)
        tv_password = v.findViewById(R.id.tv_password)
        bt_copy_room = v.findViewById(R.id.bt_copy_room)
        iv_close = v.findViewById(R.id.iv_close)
        rl_room = v.findViewById(R.id.rl_room)
        iv_share_qq = v.findViewById(R.id.iv_share_qq)
        iv_share_wechat = v.findViewById(R.id.iv_share_wechat)
        iv_share_more = v.findViewById(R.id.iv_share_more)
        tv_join_room = v.findViewById(R.id.tv_join_room)
        tv_room.text = serverInfo.name
        tv_ip.text = serverInfo.serverAddr
        tv_port.text = "" + serverInfo.port
        var pa = OYUtil.message2Base64URL(System.currentTimeMillis().toString() + "")
        pa = pa!!.substring(pa.length - 7)
        when (serverInfo.mode) {
            YGOServer.MODE_ONE -> {
                tv_mode.text = OYUtil.s(R.string.duel_mode_one)
                tv_password.text = pa
            }

            YGOServer.MODE_MATCH -> {
                tv_mode.text = OYUtil.s(R.string.duel_mode_match)
                tv_password.text = "M#$pa"
            }

            YGOServer.MODE_TAG -> {
                tv_mode.text = OYUtil.s(R.string.duel_mode_tag)
                tv_password.text = "T#$pa"
            }

            else -> {
                tv_mode.text = OYUtil.s(R.string.duel_mode_one)
                tv_password.text = pa
            }
        }
        tv_join_room.setOnClickListener { v1: View? ->
            if (!OYApplication.isIsInitRes()) {
                OYUtil.show("请等待资源加载完毕后加入游戏")
                return@setOnClickListener
            }
            du.dis()
            YGOUtil.joinGame(activity, serverInfo, tv_password.text.toString())
        }
        bt_copy_room.setOnClickListener { v13: View? ->
//            if (!OYApplication.isIsInitRes()) {
//                OYUtil.show("请等待资源加载完毕后加入游戏");
//                return;
//            }
            var message = serverInfo.toUri(tv_password.text.toString())
            message = """
                房间密码：${tv_password.text}
                点击或复制打开YGO加入决斗：$message
                """.trimIndent()
            OYUtil.copyMessage(message)
            OYUtil.show(OYUtil.s(R.string.copy_ok))
            du.dis()
        }
        iv_share_qq.setOnClickListener { v14: View? ->
            var message = serverInfo.toUri(tv_password.text.toString())
            message = """
                房间密码：${tv_password.text}
                点击或复制打开YGO加入决斗：$message
                """.trimIndent()
            OYUtil.copyMessage(message)
            ShareUtil.Companion.shareQQ(activity, message)
            //            OYUtil.show(OYUtil.s(R.string.copy_ok));
            du.dis()
        }
        iv_share_wechat.setOnClickListener { v15: View? ->
            val message = serverInfo.toUri(tv_password.text.toString())
            OYUtil.copyMessage(message)
            ShareUtil.Companion.shareWechatFriend(activity, message)
            du.dis()
        }
        iv_share_more.setOnClickListener { v15: View? ->
            var message = serverInfo.toUri(tv_password.text.toString())
            message = """
                房间密码：${tv_password.text}
                点击或复制打开YGO加入决斗：$message
                """.trimIndent()
            OYUtil.copyMessage(message)
            ShareUtil.Companion.share(activity, message)
            du.dis()
        }
        iv_close.setOnClickListener { v12: View? -> du.dis() }
    }

    @JvmStatic
    fun dialogSetBg(
        context: Context?,
        imagePath: String?,
        bgList: IntArray,
        onSetBgListener: OnSetBgListener
    ) {
        val dialogUtils = DialogUtils.getInstance(context)
        val dialogView = dialogUtils.dialogBottomSheet(R.layout.set_bg_dialog, true)
        val sc_blur = dialogView.findViewById<SwitchCompat>(R.id.sc_blur)
        val cb_duel = dialogView.findViewById<CheckBox>(R.id.cb_duel)
        val cb_menu = dialogView.findViewById<CheckBox>(R.id.cb_menu)
        val cb_deck = dialogView.findViewById<CheckBox>(R.id.cb_deck)
        val iv_bg = dialogView.findViewById<ImageView>(R.id.iv_bg)
        val tv_set = dialogView.findViewById<TextView>(R.id.tv_set)

//        ImageUtil.(context, imagePath, iv_bg, System.currentTimeMillis() + "");
        val nameList: MutableList<String> = ArrayList()
        for (bg in bgList) {
            when (bg) {
                BG_TYPE_DUEL -> {
                    cb_duel.isChecked = true
                    nameList.add(Constants.CORE_SKIN_BG)
                }

                BG_TYPE_MENU -> {
                    cb_menu.isChecked = true
                    nameList.add(Constants.CORE_SKIN_BG_MENU)
                }

                BG_TYPE_DECK -> {
                    cb_deck.isChecked = true
                    nameList.add(Constants.CORE_SKIN_BG_DECK)
                }
            }
        }
        cb_duel.setOnCheckedChangeListener { buttonView: CompoundButton?, isChecked: Boolean ->
            if (isChecked) {
                cb_duel.isChecked = true
                nameList.add(Constants.CORE_SKIN_BG)
            } else {
                cb_duel.isChecked = false
                nameList.remove(Constants.CORE_SKIN_BG)
            }
        }
        cb_menu.setOnCheckedChangeListener { buttonView: CompoundButton?, isChecked: Boolean ->
            if (isChecked) {
                cb_menu.isChecked = true
                nameList.add(Constants.CORE_SKIN_BG_MENU)
            } else {
                cb_menu.isChecked = false
                nameList.remove(Constants.CORE_SKIN_BG_MENU)
            }
        }
        cb_deck.setOnCheckedChangeListener { buttonView: CompoundButton?, isChecked: Boolean ->
            if (isChecked) {
                cb_deck.isChecked = true
                nameList.add(Constants.CORE_SKIN_BG_DECK)
            } else {
                cb_deck.isChecked = false
                nameList.remove(Constants.CORE_SKIN_BG_DECK)
            }
        }
        sc_blur.setOnCheckedChangeListener { buttonView: CompoundButton?, isChecked: Boolean ->
            if (isChecked) {
                ImageUtil.showBlur(
                    context,
                    imagePath,
                    iv_bg,
                    System.currentTimeMillis().toString() + ""
                )
            } else {
                ImageUtil.show(
                    context,
                    imagePath,
                    iv_bg,
                    System.currentTimeMillis().toString() + ""
                )
            }
        }
        sc_blur.isChecked = true
        tv_set.setOnClickListener { v: View? ->
            dialogUtils.dis()
            dialogUtils.dialogj1(null, "设置中，请稍等")
            if (sc_blur.isChecked) {
                ImageUtil.getBlurImage(
                    context,
                    imagePath
                ) { imagePath1: String?, exception: String? ->
                    try {
                        for (name in nameList) {
                            FileUtils.copyFile(
                                imagePath1,
                                File(AppsSettings.get().coreSkinPath, name).absolutePath
                            )
                        }
                        dialogUtils.dis()
                        onSetBgListener.onSetBg(null)
                    } catch (e: IOException) {
                        dialogUtils.dis()
                        onSetBgListener.onSetBg(e.toString())
                    }
                }
            } else {
                try {
                    for (name in nameList) {
                        FileUtils.copyFile(
                            imagePath,
                            File(AppsSettings.get().coreSkinPath, name).absolutePath
                        )
                    }
                    dialogUtils.dis()
                    onSetBgListener.onSetBg(null)
                } catch (e: IOException) {
                    dialogUtils.dis()
                    onSetBgListener.onSetBg(e.toString())
                }
            }
        }
    }

    @JvmStatic
    fun dialogNewDeck(context: Context) {
        val dialogUtils = DialogUtils.getInstance(context)
        val view = dialogUtils.dialogBottomSheet(R.layout.new_deck_dialog)
        val dialog = dialogUtils.dialog
        val tv_edit: TextView
        val tv_open_new_deck: TextView
        val tv_title: TextView
        val tv_message: TextView
        val iv_close: ImageView
        val iv_image: ImageView
        val cardBag = OYUtil.newCardBag
        tv_edit = view.findViewById(R.id.tv_edit)
        iv_close = view.findViewById(R.id.iv_close)
        tv_open_new_deck = view.findViewById(R.id.tv_open_new_deck)
        tv_title = view.findViewById(R.id.tv_title)
        tv_message = view.findViewById(R.id.tv_message)
        iv_image = view.findViewById(R.id.iv_image)
        tv_title.text = cardBag!!.title
        tv_message.text = cardBag!!.message
        iv_close.setOnClickListener { view1: View? -> dialog.dismiss() }
        tv_edit.setOnClickListener { view12: View? ->
            dialog.dismiss()
            when (SharedPreferenceUtil.deckEditType) {
                SharedPreferenceUtil.DECK_EDIT_TYPE_LOCAL -> IntentUtil.startYGODeck(context as Activity)
                SharedPreferenceUtil.DECK_EDIT_TYPE_DECK_MANAGEMENT -> context.startActivity(
                    Intent(
                        context,
                        DeckManagementActivity::class.java
                    )
                )

                SharedPreferenceUtil.DECK_EDIT_TYPE_OURYGO_EZ -> if (OYUtil.isApp(Record.PACKAGE_NAME_EZ)) context.startActivity(
                    IntentUtil.getAppIntent(context, Record.PACKAGE_NAME_EZ)
                ) else context.startActivity(
                    IntentUtil.getWebIntent(
                        context,
                        "http://ez.ourygo.top/"
                    )
                )
            }
        }
        tv_open_new_deck.setOnClickListener { v: View? ->
            dialog.dismiss()
            IntentUtil.startYGODeck(
                context as Activity,
                OYUtil.s(R.string.category_pack),
                cardBag!!.deckName
            )
        }
    }

    //自动更新对话框
    fun dialogUpdate(context: Context, updateInfo: UpdateInfo?) {
        if (updateInfo == null) return
        val dialogUtils = DialogUtils.getInstance(context)
        val dialogView = dialogUtils.dialogBottomSheet(R.layout.update_dialog)
        val iv_close: ImageView
        val tv_title: TextView
        val tv_update: TextView
        val tv_code: TextView
        val tv_version: TextView
        val tv_size: TextView
        val tv_message: TextView
        iv_close = dialogView.findViewById(R.id.iv_close)
        tv_title = dialogView.findViewById(R.id.tv_title)
        tv_update = dialogView.findViewById(R.id.tv_update)
        tv_code = dialogView.findViewById(R.id.tv_code)
        tv_version = dialogView.findViewById(R.id.tv_version)
        tv_size = dialogView.findViewById(R.id.tv_size)
        tv_message = dialogView.findViewById(R.id.tv_message)
        val code = updateInfo.code
        tv_title.text = updateInfo.title
        tv_message.text = updateInfo.message
        tv_version.text = updateInfo.versionName
        tv_size.text = OYUtil.getFileSizeText(updateInfo.size)
        if (TextUtils.isEmpty(code)) {
            tv_code.visibility = View.GONE
        } else {
            tv_code.visibility = View.VISIBLE
            tv_code.text = "验证码：$code"
        }
        tv_code.setOnClickListener { view: View? ->
            OYUtil.copyMessage(code)
            OYUtil.show("已复制验证码到剪贴板")
        }
        iv_close.setOnClickListener { dialogUtils.dis() }
        tv_update.setOnClickListener { view: View? ->
            dialogUtils.dis()
            context.startActivity(IntentUtil.getUrlIntent(updateInfo.url))
            if (!TextUtils.isEmpty(code)) {
                OYUtil.copyMessage(code)
                OYUtil.show("已复制验证码到剪贴板")
            }
        }
    }
}