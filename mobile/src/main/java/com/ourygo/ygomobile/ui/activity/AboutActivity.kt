package com.ourygo.ygomobile.ui.activity

import android.os.Bundle
import android.util.Log
import android.view.View
import cn.garymb.ygomobile.lite.R
import cn.garymb.ygomobile.lite.databinding.AboutActivityBinding
import cn.garymb.ygomobile.ui.activities.BaseActivity
import com.feihua.dialogutils.bean.UpdateLog
import com.feihua.dialogutils.util.DialogUtils
import com.ourygo.ygomobile.util.IntentUtil
import com.ourygo.ygomobile.util.OYUtil
import com.ourygo.ygomobile.util.Record

class AboutActivity : BaseActivity() {
    private lateinit var binding: AboutActivityBinding
    private val du by lazy {
        DialogUtils.getInstance(this)
    }

    private val updateList by lazy {
        arrayListOf(
            UpdateLog.toUpdateLog(
                "1.2.11",
                """
                更新ygo内核
                更新卡包TTP1+VJ
                """.trimIndent()
            ),
            UpdateLog.toUpdateLog(
                "1.2.10",
                """
                更新ygo内核
                更新卡包DBJH+VX05+VJ
                OCG禁卡表更新至2025.4
                """.trimIndent()
            ),
            UpdateLog.toUpdateLog(
                "1.2.9",
                """
                更新ygo内核
                更新萌卡的资源
                更新卡包QCTB+25PP+25PR+VJ
                其他优化
                """.trimIndent()
            ),
            UpdateLog.toUpdateLog(
                "1.2.8",
                """
                修复点击卡片选项取消后阶段按钮置灰不恢复的问题
                修复本地AI不准备的问题
                """.trimIndent()
            ),
            UpdateLog.toUpdateLog(
                "1.2.7",
                """
                更新ygo内核
                更新卡包AC04+AJ
                OCG禁卡表更新至2024.7
                关于界面增加备案号标识
                其他优化
                """.trimIndent()
            ),
            UpdateLog.toUpdateLog(
                "1.2.6",
                """
                更新ygo内核
                更新卡包1202+T1201+DBVS+SR14+WPP4+VJ
                OCG禁卡表更新至2023.10
                TCG禁卡表更新至2023.9
                增加扩展卡包.ini文件服务器记录
                其他优化
                """.trimIndent()
            ),
            UpdateLog.toUpdateLog(
                "1.2.5", """
                修复卡组码内容缺失时有几率无法检测的问题
                修复卡包卡组分享卡组码异常的问题
                优化卡组分享交互
                """.trimIndent()
            ),
            UpdateLog.toUpdateLog(
                "1.2.4", """
                更新ygo内核
                更新卡包DP28+AC03+SD46+VJ
                OCG禁卡表更新至2023.7
                TCG禁卡表更新至2023.6
                """.trimIndent()
            ),
            UpdateLog.toUpdateLog(
                "1.2.3", """
               更新ygo内核
               更新卡包1111+T1110+VJ
               软件名简化为YGO-OY
               其他优化
               """.trimIndent()
            ),

            UpdateLog.toUpdateLog(
                "1.2.2", """
               更新ygo内核
               更新卡包WPP3+VJ
               修复服务器列表切换显示模式后点击无反应的问题
               暂时去除本地AI
               其他优化
               """.trimIndent()
            ),

            UpdateLog.toUpdateLog(
                "1.2.1", """
               更新ygo内核
               更新卡包DBAD+VJ+YCSW
               OCG禁卡表更新至2022.10.1
               修复分享卡组码崩溃的问题
               其他优化
               """.trimIndent()
            ),

            UpdateLog.toUpdateLog(
                "1.2.0", """
               更新ygo内核
               更新卡包SR13+T1109
               服务器列表可选择表格布局
               修复部分机型对话框关闭时输入法未关闭的问题
               MC萌卡邮箱未验证时支持跳转验证邮箱
               其他优化
               """.trimIndent()
            ),

            UpdateLog.toUpdateLog(
                "1.1.4", """
               更新ygo内核
               更新卡包DP27+VP22+T1108+AC02+VJ
               OCG禁卡表更新至2022.7.1
               TCG禁卡表更新至2022.5.17
               其他优化
               """.trimIndent()
            ),

            UpdateLog.toUpdateLog(
                "1.1.3", """
                         更新ygo内核
                         更新卡包1109+KC01+VJ
                         """.trimIndent()
            ),

            UpdateLog.toUpdateLog(
                "1.1.2", """
                        更新ygo内核
                        更新卡包DBTM+VX+VJ
                        """.trimIndent()
            ),

            UpdateLog.toUpdateLog(
                "1.1.1", """
               更新ygo内核
               更新卡包HC01+T1107+VJ
               修复卡组码不完整时的闪退问题
               """.trimIndent()
            ),

            UpdateLog.toUpdateLog(
                "1.1.0", """
               更新ygo内核
               更新卡包1108+VJ
               适配平板布局
               设置背景时可选择背景模糊
               修复卡组分类排序错误的问题
               其他优化
               """.trimIndent()
            ),

            UpdateLog.toUpdateLog(
                "1.0.3", """
                    更新ygo内核
                    更新卡包22PP+SSB1+VJ
                    """.trimIndent()
            ),

            UpdateLog.toUpdateLog(
                "1.0.2", """
               更新ygo内核
               更新卡包SD43
               更新2021.1 OCG禁卡表
               """.trimIndent()
            ),
            UpdateLog.toUpdateLog(
                "1.0.1",
                """
                更新ygo内核
                新卡DP+T1106+VJ
                其他优化 
                """.trimIndent()
            ),
            UpdateLog.toUpdateLog(
                "1.0",
                "初始功能"
            )

        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = AboutActivityBinding.inflate(layoutInflater)
        setContentView(binding.root)
        initView()
    }

    private fun initView() {
        initToolbar(OYUtil.s(R.string.about))

        // 使用 View Binding 初始化视图
        binding.ivIcon.setOnClickListener(object : View.OnClickListener {
            private var clickFirstTime: Long = 0
            private var clickNum = 0
            override fun onClick(v: View) {
                if (clickFirstTime + 1000 > System.currentTimeMillis()) {
                    Log.e("AboutActivity", "加一$clickNum")
                    clickFirstTime = System.currentTimeMillis()
                    clickNum++
                    if (clickNum >= 5) {
                        clickNum = 0
                        return
                    }
                } else {
                    Log.e("AboutActivity", "重置")
                    clickNum = 0
                    clickFirstTime = System.currentTimeMillis()
                }
            }
        })

        binding.tvCheckUpdate.setOnClickListener { checkUpdate() }
        binding.tvQqGroup.setOnClickListener {
            OYUtil.joinQQGroup(
                this@AboutActivity,
                Record.ARG_QQ_GROUP_KEY
            )
        }
        binding.llVersion.setOnClickListener {
            val v = du.dialogUpdateLog(OYUtil.s(R.string.update_log), updateList)
            v[1].setOnClickListener { du.dis() }
        }
        binding.tvRecord.setOnClickListener {
            startActivity(IntentUtil.getUrlIntent("https://beian.miit.gov.cn/"))
        }
    }

    private fun checkUpdate() {
        OYUtil.checkUpdate(this, true)
    }

}