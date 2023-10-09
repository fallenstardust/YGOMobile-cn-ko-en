package com.ourygo.ygomobile.ui.activity

import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.AsyncTask
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager.widget.ViewPager
import androidx.viewpager.widget.ViewPager.OnPageChangeListener
import cn.garymb.ygomobile.AppsSettings
import cn.garymb.ygomobile.Constants
import cn.garymb.ygomobile.GameUriManager
import cn.garymb.ygomobile.bean.Deck
import cn.garymb.ygomobile.lite.R
import cn.garymb.ygomobile.ui.activities.BaseActivity
import cn.garymb.ygomobile.ui.home.ResCheckTask
import cn.garymb.ygomobile.ui.home.ResCheckTask.ResCheckListener
import cn.garymb.ygomobile.ui.mycard.mcchat.management.ServiceManagement
import cn.garymb.ygomobile.utils.FileLogUtil
import cn.garymb.ygomobile.utils.ScreenUtil
import com.chad.library.adapter.base.BaseQuickAdapter
import com.feihua.dialogutils.util.DialogUtils
import com.ourygo.lib.duelassistant.listener.OnDuelAssistantListener
import com.ourygo.lib.duelassistant.util.ClipManagement
import com.ourygo.lib.duelassistant.util.DuelAssistantManagement
import com.ourygo.lib.duelassistant.util.YGODAUtil
import com.ourygo.ygomobile.adapter.FmPagerAdapter
import com.ourygo.ygomobile.adapter.VerTabBQAdapter
import com.ourygo.ygomobile.bean.FragmentData
import com.ourygo.ygomobile.bean.YGOServerList
import com.ourygo.ygomobile.ui.fragment.MainFragment
import com.ourygo.ygomobile.ui.fragment.McLayoutFragment
import com.ourygo.ygomobile.ui.fragment.MyCardFragment
import com.ourygo.ygomobile.ui.fragment.MyCardWebFragment
import com.ourygo.ygomobile.ui.fragment.OtherFunctionFragment
import com.ourygo.ygomobile.util.AppInfoManagement
import com.ourygo.ygomobile.util.IntentUtil
import com.ourygo.ygomobile.util.LogUtil
import com.ourygo.ygomobile.util.OYDialogUtil
import com.ourygo.ygomobile.util.OYUtil
import com.ourygo.ygomobile.util.Record
import com.ourygo.ygomobile.util.SdkInitUtil
import com.ourygo.ygomobile.util.SharedPreferenceUtil
import com.ourygo.ygomobile.util.YGOUtil.getYGOServerList
import com.ourygo.ygomobile.view.OYTabLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException

class OYMainActivity : BaseActivity(), OnDuelAssistantListener {
    private var tl_tab: OYTabLayout? = null

    //    private VerticalTabLayout vtab;
    private lateinit var rv_tab: RecyclerView
    private lateinit var vp_pager: ViewPager
    private lateinit var iv_card_query: ImageView
    private val fragmentList by lazy {
        ArrayList<FragmentData>()
    }
    private var mainFragment: MainFragment? = null
    private var myCardWebFragment: MyCardWebFragment? = null
    private val myCardFragment: MyCardFragment? = null
    private var mcLayoutFragment: McLayoutFragment? = null
    private var otherFunctionFragment: OtherFunctionFragment? = null
    private lateinit var mResCheckTask: ResCheckTask
    private val duelAssistantManagement by lazy {
        DuelAssistantManagement.getInstance()
    }
    private var dialogUtils: DialogUtils? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.e("OyMainActivity", "创建" + (savedInstanceState != null))
        if (isHorizontal) setContentView(R.layout.oy_main_horizontal_activity) else setContentView(R.layout.oy_main_activity)
        isFragmentActivity = true
        LogUtil.time(TAG, "0")
        lifecycleScope.launch(Dispatchers.IO) {
            SdkInitUtil.getInstance().initX5WebView()
            initBugly()
        }
        LogUtil.time(TAG, "1")
        initView()
        initData()
        LogUtil.time(TAG, "2")
        checkNotch()
        LogUtil.time(TAG, "3")
        //        checkRes();
        LogUtil.time(TAG, "4")
        LogUtil.printSumTime(TAG)
    }

    private val handler: Handler = Handler(Looper.getMainLooper()) { true }

    fun initBugly() {
//        Bugly.init(this, OYApplication.BUGLY_ID, false);
        if (OYUtil.isTodayFirstStart()) //检测是否有更新,不提示
            OYUtil.checkUpdate(this, false)
    }

    protected fun checkResourceDownload(listener: ResCheckListener?) {
        mResCheckTask = ResCheckTask(this, listener)
        mResCheckTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR)
        //        if (Build.VERSION.SDK_INT >= 11) {
//            mResCheckTask.exefcuteOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
//        } else {
//            mResCheckTask.execute();
//        }
    }

    private fun checkNotch() {
        ScreenUtil.findNotchInformation(this@OYMainActivity) { isNotch: Boolean, notchHeight: Int, phoneType: Int ->
            try {
                FileLogUtil.writeAndTime("检查刘海$isNotch   $notchHeight")
            } catch (e: IOException) {
                e.printStackTrace()
            }
            AppsSettings.get().notchHeight = notchHeight
        }
    }

    override fun onResume() {
        super.onResume()
        duelAssistantCheck()
    }

    private fun duelAssistantCheck() {
        if (AppsSettings.get().isServiceDuelAssistant) {
            handler.postDelayed({
                try {
                    FileLogUtil.writeAndTime("主页决斗助手检查")
                } catch (e: IOException) {
                    e.printStackTrace()
                }
                duelAssistantManagement!!.checkClip(ID_MAINACTIVITY)
            }, 500)
        }
    }

    private fun checkRes() {
        checkResourceDownload { error: Int, isNew: Boolean -> }
    }

    private fun initDuelAssistant() {
        duelAssistantManagement.init(applicationContext)
        duelAssistantManagement.addDuelAssistantListener(this)
        //        YGOUtil.startDuelService(this);
    }

    fun selectMycard() {
        if (isHorizontal) {
//            vtab.setTabSelected(1);
            vp_pager.currentItem = 1
        } else {
            tl_tab!!.currentTab = 1
        }
    }

    private fun initView() {
        if (isHorizontal) {
//            vtab = findViewById(R.id.vTab);
            rv_tab = findViewById(R.id.rv_tab)
            rv_tab.setLayoutManager(LinearLayoutManager(this))
        } else {
            tl_tab = findViewById(R.id.tl_tab)
            iv_card_query = findViewById(R.id.iv_card_query)
        }
        vp_pager = findViewById(R.id.vp_pager)
        LogUtil.time(TAG, "1.2")
        //        String mcName = SharedPreferenceUtil.getMyCardUserName();
//        refreshMyCardUser(mcName);
        mainFragment = MainFragment()
        myCardWebFragment = MyCardWebFragment()
        //        myCardFragment = new MyCardFragment();
        mcLayoutFragment = McLayoutFragment()
        otherFunctionFragment = OtherFunctionFragment()
        LogUtil.time(TAG, "1.3")
        dialogUtils = DialogUtils.getInstance(this)
        fragmentList.add(
            FragmentData.toFragmentData(
                s(R.string.homepage),
                R.drawable.ic_home_gray,
                mainFragment
            )
        )
        fragmentList.add(
            FragmentData.toFragmentData(
                s(R.string.mycard),
                R.drawable.ic_mycard,
                mcLayoutFragment
            )
        )
        fragmentList.add(
            FragmentData.toFragmentData(
                s(R.string.other_funstion),
                R.drawable.ic_other_gray,
                otherFunctionFragment
            )
        )
    }

    fun initData() {
        vp_pager.adapter = FmPagerAdapter(supportFragmentManager, fragmentList)
        //        tl_tab.setTabMode(TabLayout.MODE_FIXED);
        //缓存两个页面
        vp_pager.offscreenPageLimit = 3
        LogUtil.time(TAG, "1.4")
        //TabLayout加载viewpager
        if (isHorizontal) {
            val verTabBQAdapter = VerTabBQAdapter(fragmentList)
            rv_tab.adapter = verTabBQAdapter
            verTabBQAdapter.setOnItemClickListener { adapter: BaseQuickAdapter<*, *>?, view: View?, position: Int ->
                if (position != verTabBQAdapter.selectPosition) {
                    verTabBQAdapter.selectPosition = position
                    vp_pager.currentItem = position
                }
            }
            vp_pager.addOnPageChangeListener(object : OnPageChangeListener {
                override fun onPageScrolled(
                    position: Int,
                    positionOffset: Float,
                    positionOffsetPixels: Int
                ) {
                }

                override fun onPageSelected(position: Int) {
                    Log.e("OYMainac", "滑动监听$position")
                    verTabBQAdapter.selectPosition = position
                }

                override fun onPageScrollStateChanged(state: Int) {}
            })
            //            vtab.setupWithViewPager(vp_pager);
//
//            vtab.setTabAdapter(new TabAdapter() {
//                @Override
//                public int getCount() {
//                    return 1;
//                }
//
//                @Override
//                public ITabView.TabBadge getBadge(int position) {
//                    return null;
//                }
//
//                @Override
//                public ITabView.TabIcon getIcon(int position) {
//                    return new ITabView.TabIcon.Builder().setIcon(R.drawable.ic_lizi,R.drawable.ic_lizi).build();
//                }
//
//                @Override
//                public ITabView.TabTitle getTitle(int position) {
//                    return new QTabView.TabTitle.Builder().setContent("首页").build();
//                }
//
//                @Override
//                public int getBackground(int position) {
//                    return 0;
//                }
//            });
//            vtab.setTabSelected(0);
        } else {
            LogUtil.time(TAG, "1.4.1")
            tl_tab!!.setViewPager(vp_pager)
            LogUtil.time(TAG, "1.4.2")
            tl_tab!!.currentTab = 0
            LogUtil.time(TAG, "1.5")
            iv_card_query.setOnClickListener { view: View? ->
                startActivity(
                    IntentUtil.getWebIntent(
                        this@OYMainActivity,
                        Record.YGO_CARD_QUERY_URL
                    )
                )
            }
        }
        initDuelAssistant()
        checkIntent()
        if (SharedPreferenceUtil.isFristStart()) {
            val views = dialogUtils!!.dialogt(
                null,
                """欢迎使用YGO-OY,本软件为YGOMobile原版的简约探索版，这里有正在探索的功能，但相对没有原版稳定，你可以选择下载原版使用，下载地址：https://www.pgyer.com/ygomobilecn

如果你觉得好用，可以对我们进行支持，每一份支持都将帮助我们更好的建设平台"""
            )
            val dialog = dialogUtils!!.dialog
            val b1: Button
            val b2: Button
            b1 = views[0] as Button
            b2 = views[1] as Button
            b1.text = "取消"
            b2.text = "支持我们"
            b1.setOnClickListener { v: View? -> dialog.dismiss() }
            b2.setOnClickListener { v: View? ->
                dialog.dismiss()
                OYUtil.startAifadian(this@OYMainActivity)
            }
            val tv_message = dialogUtils!!.messageTextView
            tv_message.setLineSpacing(OYUtil.dp2px(3f).toFloat(), 1f)
            SharedPreferenceUtil.setFirstStart(false)
            SharedPreferenceUtil.setNextAifadianNum(SharedPreferenceUtil.getAppStartTimes() + (10 + (Math.random() * 20).toInt()))
            dialog.setOnDismissListener { dialog12: DialogInterface? ->
                val b3 = dialogUtils!!.dialogt1(
                    "卡组导入提示", "YGO-OY储存路径为内部储存/ygocore，如果你之前有使用过原版" +
                            "，可以打开原版软件，点击下边栏的卡组选项——功能菜单——备份/还原来导入或导出原版ygo中的卡组"
                )
                val dialog1 = dialogUtils!!.dialog
                b3.setOnClickListener({ v: View? -> dialog1.dismiss() })
                val tv_message1 = dialogUtils!!.messageTextView
                tv_message1.setLineSpacing(OYUtil.dp2px(3f).toFloat(), 1f)
                dialog1.setOnDismissListener { }
            }
        }
        if (SharedPreferenceUtil.getNextAifadianNum() == SharedPreferenceUtil.getAppStartTimes()) {
            val views1 = dialogUtils!!.dialogt(
                null,
                "如果喵觉得软件好用，可以对我们进行支持，每一份支持都将帮助我们更好的建设平台"
            )
            val dialog1 = dialogUtils!!.dialog
            val b11: Button
            val b21: Button
            b11 = views1[0] as Button
            b21 = views1[1] as Button
            b11.text = "取消"
            b21.text = "支持我们"
            b11.setOnClickListener { v: View? -> dialogUtils!!.dis() }
            b21.setOnClickListener { v: View? ->
                dialog1.dismiss()
                OYUtil.startAifadian(this@OYMainActivity)
            }
        }
    }

    private fun checkIntent() {
        val intent = intent

//        if (!Intent.ACTION_VIEW.equals(intent.getAction()))
//            return;
        if (intent.data != null) {
            val uri = getIntent().data
            if (Constants.URI_ROOM == uri!!.host) {
                duelAssistantManagement!!.setLastMessage(ClipManagement.getInstance().clipMessage)
                YGODAUtil.deRoomListener(uri) { host1: String?, port: Int, password: String?, exception: String? ->
                    if (TextUtils.isEmpty(
                            exception
                        )
                    ) joinDARoom(
                        host1,
                        port,
                        password
                    ) else OYUtil.snackExceptionToast(
                        this@OYMainActivity,
                        vp_pager,
                        "加入房间失败",
                        exception
                    )
                }
                return
            } else if (Constants.URI_DECK == uri.host) {
                duelAssistantManagement!!.setLastMessage(ClipManagement.getInstance().clipMessage)
            }
        }
        GameUriManager(this).doIntent(intent)
    }

    fun refreshMyCardUser(name: String?) {
//        if (TextUtils.isEmpty(name)) {
//            tv_name.setText(getString(R.string.no_login));
//            iv_avatar.setImageResource(R.drawable.avatar);
//            iv_avatar.setOnClickListener(new View.OnClickListener() {
//                @Override
//                public void onClick(View v) {
//                    vp_pager.setCurrentItem(1);
//                }
//            });
//            tv_name.setOnClickListener(new View.OnClickListener() {
//                @Override
//                public void onClick(View v) {
//                    vp_pager.setCurrentItem(1);
//                }
//            });
//        } else {
//            tv_name.setText(name);
//            ImageUtil.setImage(this, MyCardUtil.getAvatarUrl(name), iv_avatar);
//        }
    }

    override fun onBackPressed() {
        if (isHorizontal) {
//            if (vtab.getSelectedTabPosition() != 0) {
//                vtab.setTabSelected(0);
//                return;
//            }
            if (vp_pager.currentItem != 0) {
                vp_pager.currentItem = 0
                return
            }
        } else {
            if (tl_tab!!.currentTab != 0) {
                tl_tab!!.currentTab = 0
                return
            }
        }
        super.onBackPressed()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Log.e("MainF", "$requestCode 返回1 $resultCode")
    }

    override fun onJoinRoom(host: String, port: Int, password: String, id: Int) {
        if (id == ID_MAINACTIVITY) {
            joinDARoom(host, port, password)
        }
    }

    fun joinDARoom(host: String?, port: Int, password: String?) {
//        YGODAUtil.deDeckListener(password, (uri1, mainList, exList, sideList, isCompleteDeck, exception) -> {
//            LogUtil.e("feihua","解析结果："+uri1
//                    +" \nmainList: "+mainList.size()
//                    +" \nexList: "+exList.size()
//                    +" \nsideList: "+sideList.size()
//                    +" \nisCompleteDeck: "+isCompleteDeck
//                    +" \nexception: "+exception
//            );
//            if (!TextUtils.isEmpty(exception)){
//                cn.garymb.ygomobile.utils.YGOUtil.show("卡组解析失败，原因为："+exception);
//                return;
//            }
//            Deck deckInfo = new Deck(uri,mainList,exList,sideList);
//            deckInfo.setCompleteDeck(isCompleteDeck);
//            OYDialogUtil.dialogDASaveDeck(activity,uri.toString(),deckInfo,OYDialogUtil.DECK_TYPE_DECK);
//        });
        getYGOServerList { serverList: YGOServerList ->
            val ygoServer = serverList.serverInfoList[0]
            if (!TextUtils.isEmpty(host)) {
                ygoServer.serverAddr = host
                ygoServer.port = port
            }
            OYDialogUtil.dialogDAJoinRoom(this@OYMainActivity, ygoServer, password)
        }
    }

    override fun onCardQuery(key: String, id: Int) {}
    override fun onSaveDeck(
        uri: Uri,
        mainList: List<Int>,
        exList: List<Int>,
        sideList: List<Int>,
        isCompleteDeck: Boolean,
        exception: String,
        id: Int
    ) {
        Log.e("feihua", "主页解析")
        saveDeck(uri, mainList, exList, sideList, isCompleteDeck, exception)
    }

    override fun isListenerEffective(): Boolean {
        return OYUtil.isContextExisted(this)
    }

    private fun saveDeck(
        uri: Uri?,
        mainList: List<Int>,
        exList: List<Int>,
        sideList: List<Int>,
        isCompleteDeck: Boolean,
        exception: String
    ) {
        if (!TextUtils.isEmpty(exception)) {
            OYUtil.show("卡组解析失败，原因为：$exception")
            return
        }
        val deckInfo: Deck
        deckInfo = if (uri != null) {
            Deck(uri, mainList, exList, sideList)
        } else {
            Deck(
                getString(R.string.rename_deck) + System.currentTimeMillis(),
                mainList,
                exList,
                sideList
            )
        }
        deckInfo.isCompleteDeck = isCompleteDeck
        OYDialogUtil.dialogDASaveDeck(
            this,
            uri.toString() + "",
            deckInfo,
            OYDialogUtil.DECK_TYPE_DECK
        )
    }

    override fun onDestroy() {
        ServiceManagement.getDx().disClass()
        AppInfoManagement.getInstance().close()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "TIME-MainActivity"
        private const val ID_MAINACTIVITY = 0
    }
}