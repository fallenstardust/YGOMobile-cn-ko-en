package com.ourygo.ygomobile.util

import android.app.Activity
import android.app.ActivityManager
import android.app.Application
import android.app.Dialog
import android.app.Service
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import android.telephony.TelephonyManager
import android.text.Html
import android.text.Spanned
import android.text.TextUtils
import android.util.Base64
import android.view.View
import android.view.ViewPropertyAnimator
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.R
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ItemDecoration
import cn.garymb.ygomobile.App
import com.feihua.dialogutils.util.DialogUtils
import com.ourygo.ygomobile.OYApplication
import com.ourygo.ygomobile.bean.CardBag
import org.json.JSONObject
import java.util.Calendar

object OYUtil {
    private const val URL_AIFAFIAN = "httpgetNewCardBags://afdian.net/@ourygo"

    @JvmStatic
    fun initToolbar(activity: AppCompatActivity, toolbar: Toolbar, s: String?, isBack: Boolean) {
        toolbar.title = s
        activity.setSupportActionBar(toolbar)
        if (isBack) {
            toolbar.setNavigationIcon(R.drawable.abc_ic_ab_back_material) //  context.getResources().getDrawable(R.drawable.abc_ic_ab_back_mtrl_am_alpha));
            toolbar.setNavigationOnClickListener { p1: View? -> activity.finish() }
        }
    }

    //显示虚拟键盘
    fun showKeyboard(v: View) {
        v.requestFocus()
        val imm = v.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        // imm.showSoftInput(v,InputMethodManager.SHOW_FORCED);
        imm.toggleSoftInput(0, InputMethodManager.HIDE_NOT_ALWAYS)
    }

    //关闭输入法
    fun closeKeyboard(activity: Activity?) {
        if (activity == null) return
        val inputMethodManager =
            activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val view = activity.currentFocus
        if (view != null) inputMethodManager.hideSoftInputFromWindow(
            view.windowToken,
            InputMethodManager.HIDE_NOT_ALWAYS
        )
    }

    //关闭输入法
    @JvmStatic
    fun closeKeyboard(dialog: Dialog?) {
        if (dialog == null) return
        val inputMethodManager =
            dialog.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val view = dialog.currentFocus
        if (view != null) inputMethodManager.hideSoftInputFromWindow(
            view.windowToken,
            InputMethodManager.HIDE_NOT_ALWAYS
        )
    }

    //复制字符串到剪贴板
    @JvmStatic
    fun copyMessage(s: String?) {
        val cmb = App.get().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cmb.text = s //复制命令
    }

    @JvmStatic
    fun show(toastMessage: String?) {
        Toast.makeText(App.get(), toastMessage, Toast.LENGTH_SHORT).show()
    }

    @JvmStatic
    fun s(string: Int): String {
        return App.get().resources.getString(string)
    }

    @JvmStatic
    fun c(color: Int): Int {
        return ContextCompat.getColor(App.get(), color)
    }

    fun px(dimen: Int): Int {
        return OYApplication.get().resources.getDimensionPixelOffset(dimen)
    }

    @JvmStatic
    fun dp(dimen: Int): Int {
        return ScaleUtils.px2dp(px(dimen))
    }

    @JvmStatic
    fun sp(dimen: Int): Int {
        return ScaleUtils.px2sp(px(dimen).toFloat())
    }

    @JvmStatic
    fun snackShow(v: View?, toastMessage: String?) {
        SnackbarUtil.ShortSnackbar(
            v,
            toastMessage,
            c(cn.garymb.ygomobile.lite.R.color.colorAccent),
            SnackbarUtil.white
        ).show()
    }

    fun snackWarning(v: View?, toastMessage: String?) {
        SnackbarUtil.ShortSnackbar(v, toastMessage, SnackbarUtil.white, SnackbarUtil.red).show()
    }

    fun getNameText(name: String?): Spanned {
        return Html.fromHtml(name)
    }

    fun getMessageText(message: String): Spanned {
        return Html.fromHtml(message.replace("\\n".toRegex(), "<br>"))
    }

    val iMEI: String
        /**
         * 获取手机IMEI号
         */
        get() {
            val telephonyManager = App.get()
                .getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            return telephonyManager.deviceId
        }

    fun joinQQGroup(context: Context, key: String): Boolean {
        var key = key
        val intent = Intent()
        if (key.indexOf("mqqopensdkapi") == -1) {
            key =
                "mqqopensdkapi://bizAgent/qm/qr?url=http%3A%2F%2Fqm.qq.com%2Fcgi-bin%2Fqm%2Fqr%3Ffrom%3Dapp%26p%3Dandroid%26k%3D$key"
        }
        intent.data = Uri.parse(key)
        // 此Flag可根据具体产品需要自定义，如设置，则在加群界面按返回，返回手Q主界面，不设置，按返回会返回到呼起产品界面    //intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            // 未安装手Q或安装的版本不支持
            false
        }
    }

    //是否是新版本
    //    public static boolean getIsNewVersion() {
    //        String versionName = App.get().getResources().getString(R.string.app_version_name);
    //        SharedPreferences sh = App.get().getSharedPreferences("AppVersion", App.get().MODE_PRIVATE);
    //        int vercode = SystemUtils.getVersion(OYApplication.get());
    //        int vn = sh.getInt("versionCode", 0);
    //        if (vn<vercode) {
    //            sh.edit().putString("versionName", versionName).commit();
    //            return true;
    //        } else {
    //            return false;
    //        }
    //    }
    @JvmStatic
    fun snackExceptionToast(context: Context?, view: View?, toast: String?, exception: String?) {
        SnackbarUtil.ShortSnackbar(view, toast, SnackbarUtil.white, SnackbarUtil.red)
            .setActionTextColor(c(cn.garymb.ygomobile.lite.R.color.black))
            .setAction(s(cn.garymb.ygomobile.lite.R.string.start_exception)) {
                val du = DialogUtils.getInstance(context)
                val b1 = du.dialogt1(toast, exception)
                b1.setText(cn.garymb.ygomobile.lite.R.string.copy_exception)
                b1.setOnClickListener {
                    copyMessage(exception)
                    du.dis()
                    // TODO: Implement this method
                }
                // TODO: Implement this method
            }.show()
    }

    fun isServiceExisted(context: Context, className: String): Boolean {
        val activityManager = context
            .getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val serviceList = activityManager
            .getRunningServices(Int.MAX_VALUE)
        if (serviceList.size <= 0) {
            return false
        }
        for (i in serviceList.indices) {
            val serviceInfo = serviceList[i]
            val serviceName = serviceInfo.service
            if (serviceName.className == className) {
                return true
            }
        }
        return false
    }

    @JvmStatic
    fun isContextExisted(context: Context?): Boolean {
        if (context != null) {
            if (context is Activity) {
                if (!context.isFinishing) {
                    return true
                }
            } else if (context is Service) {
                if (isServiceExisted(context, context.javaClass.name)) {
                    return true
                }
            } else if (context is Application) {
                return true
            }
        }
        return false
    }

    /**
     * 根据手机的分辨率从 dp 的单位 转成为 px(像素)
     */
    @JvmStatic
    fun dp2px(dpValue: Float): Int {
        val scale = App.get().resources.displayMetrics.density
        return (dpValue * scale + 0.5f).toInt()
    }

    /**
     * 根据手机的分辨率从 px(像素) 的单位 转成为 dp
     */
    fun px2dp(pxValue: Float): Int {
        val scale = App.get().resources.displayMetrics.density
        return (pxValue / scale + 0.5f).toInt()
    }

    fun message2Base64(message: String): String {
        return Base64.encodeToString(message.toByteArray(), Base64.NO_WRAP)
    }

    fun message2Base64URL(message: String): String {
        return Base64.encodeToString(
            message.toByteArray(),
            Base64.NO_WRAP or Base64.NO_PADDING or Base64.URL_SAFE
        )
    }

    class test3 : WatchDuelManagement.test1() {

    }

    fun base642Message(base64: String?): String {
        return base64 ?: ""
    }

    @JvmStatic
    fun getRadiusBackground(color: Int): Drawable {
        val drawable = GradientDrawable()
        drawable.shape = GradientDrawable.RECTANGLE
        //        drawable.setGradientType(GradientDrawable.RECTANGLE);
        drawable.cornerRadius =
            OYApplication.get().resources.getDimension(cn.garymb.ygomobile.lite.R.dimen.corner_radius)
        drawable.setColor(color)
        return drawable
    }

    @JvmStatic
    fun getArray(id: Int): Array<String?> {
        return OYApplication.get().resources.getStringArray(id)
    }

    fun getWatchDuelPassword(password: String, userId: Int): String {
        val bytes = ByteArray(6)
        bytes[1] = (3 shl 4).toByte()
        var checksum = 0
        for (i in 1 until bytes.size) {
            checksum -= bytes[i].toInt()
        }
        bytes[0] = (checksum and 0xff).toByte()
        val secret = userId % 65535 + 1
        var i = 0
        while (i < bytes.size) {
            var x = 0
            x = x or (bytes[i].toInt() and 0xff)
            x = x or (bytes[i + 1].toInt() shl 8)
            x = x xor secret
            bytes[i] = (x and 0xff).toByte()
            bytes[i + 1] = (x shr 8).toByte()
            i += 2
        }
        val messageString = Base64.encodeToString(bytes, Base64.NO_WRAP)
        return messageString + password
    }

    @JvmStatic
    fun isApp(packageName: String?): Boolean {
        if (TextUtils.isEmpty(packageName)) {
            return false
        }
        try {
            val info = OYApplication.get().packageManager.getPackageInfo(
                packageName!!, 0
            )
            if (info != null) return true
        } catch (e: PackageManager.NameNotFoundException) {
        }
        return false
    }

    /**
     * map对象转换为json
     *
     * @param map
     * @return json字符串
     */
    fun map2jsonStr(map: Map<String?, String?>?): String {
        return JSONObject(map).toString()
    }

    /**
     * map对象转换为json
     *
     * @param map
     * @return json字符串
     */
    fun mapObejct2jsonStr(map: Map<String?, Any?>?): String {
        return JSONObject(map).toString()
    }

    fun startAifadian(context: Context) {
        context.startActivity(IntentUtil.getUrlIntent(URL_AIFAFIAN))
    }

    fun checkUpdate(activity: Activity, b: Boolean) {
        UpdateUtil.checkUpdate(activity, b)
        //        Beta.checkUpgrade(isManual, !b);
    }

    fun getFileSizeText(fileSize: Long): String {
        val dx: String
        val ddx1 = fileSize / 1024 / 1024
        if (ddx1 < 1) {
            dx = (fileSize / 1024 % 1024).toString() + "K"
        } else {
            var iii = (fileSize / 1024 % 1024).toString() + ""
            when (iii.length) {
                1 -> iii = "00$iii"
                2 -> iii = "0$iii"
            }
            iii = iii.substring(0, 2)
            dx = ddx1.toString() + "." + iii + "M"
        }
        return dx
    }

    fun blurBitmap(bitmap: Bitmap, radius: Float, context: Context?): Bitmap {
        //Create renderscript
        val rs = RenderScript.create(context)
        //Create allocation from Bitmap
        val allocation = Allocation.createFromBitmap(rs, bitmap)
        val t = allocation.type
        //Create allocation with the same type
        val blurredAllocation = Allocation.createTyped(rs, t)
        //Create script
        val blurScript = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))
        //Set blur radius (maximum 25.0)
        blurScript.setRadius(radius)
        //Set input for script
        blurScript.setInput(allocation)
        //Call script for output allocation
        blurScript.forEach(blurredAllocation)
        //Copy script result into bitmap
        blurredAllocation.copyTo(bitmap)
        //Destroy everything to free memory
        allocation.destroy()
        blurredAllocation.destroy()
        blurScript.destroy()
        t.destroy()
        rs.destroy()
        return bitmap
    }

    fun rsBlur(
        context: Context?,
        source: Bitmap,
        radius: Int
    ): Bitmap {
        //(1)
        val renderScript = RenderScript.create(context)

//        Log.i(TAG,"scale size:"+inputBmp.getWidth()+"*"+inputBmp.getHeight());

        // Allocate memory for Renderscript to work with
        //(2)
        val input = Allocation.createFromBitmap(renderScript, source)
        val output = Allocation.createTyped(renderScript, input.type)
        //(3)
        // Load up an instance of the specific script that we want to use.
        val scriptIntrinsicBlur = ScriptIntrinsicBlur.create(
            renderScript,
            Element.U8_4(renderScript)
        )
        //(4)
        scriptIntrinsicBlur.setInput(input)
        //(5)
        // Set the blur radius
        scriptIntrinsicBlur.setRadius(radius.toFloat())
        //(6)
        // Start the ScriptIntrinisicBlur
        scriptIntrinsicBlur.forEach(output)
        //(7)
        // Copy the output to the blurred bitmap
        output.copyTo(source)
        //(8)
        renderScript.destroy()
        return source
    }

    val newCardBag: CardBag
        get() = newCardBagList[0]
    private val newCardBagList: List<CardBag>
        get() {
           return arrayListOf(
               CardBag(
                   "AC04",
                   "",
                   "AC04"
               ),
               CardBag(
                   "AGOV",
                   "",
                   "AGOV"
               ),
                CardBag(
                    "SD46 王者归来",
                    "杰克的塔玛希回来了",
                    "SD46"
                ),
                CardBag(
                    "1111 哥布林版舞台旋转来临",
                    "K语言甚至让你读不懂他的效果",
                    "1111"
                ),
                CardBag(
                    "WPP3 三幻神加强！",
                    "幻神专属卡片助你再魂一把",
                    "WPP3+VJ"
                ),
                CardBag(
                    "DBAD 消防栓带妖精",
                    "效果强力，令人绝望！",
                    "DBAD+VJ+YCSW"
                ),
                CardBag(
                    "SR13 恶魔之门，暗黑界回归！",
                    "暗黑界的龙神王，珠泪新打手",
                    "SR13+T1109"
                )
            )
        }

    private fun createViewPropertyAnimatorRT(view: View): Any? {
        try {
            val animRtCalzz = Class.forName("android.view.ViewPropertyAnimatorRT")
            val animRtConstructor = animRtCalzz.getDeclaredConstructor(
                View::class.java
            )
            animRtConstructor.isAccessible = true
            return animRtConstructor.newInstance(view)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun setViewPropertyAnimatorRT(animator: ViewPropertyAnimator, rt: Any) {
        try {
            val animClazz = Class.forName("android.view.ViewPropertyAnimator")
            val animRtField = animClazz.getDeclaredField("mRTBackend")
            animRtField.isAccessible = true
            animRtField[animator] = rt
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @JvmStatic
    fun onStartBefore(viewPropertyAnimator: ViewPropertyAnimator, view: View) {
        val `object` = createViewPropertyAnimatorRT(view)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P && `object` != null) {
            setViewPropertyAnimatorRT(viewPropertyAnimator, `object`)
        }
    }

    val isTodayFirstStart: Boolean
        /**
         * 是否是今天第一次打开软件
         */
        get() {
            val todayStartTime = SharedPreferenceUtil.todayStartTime
            val calendar = Calendar.getInstance()
            calendar.timeInMillis = todayStartTime
            val calendar1 = Calendar.getInstance()
            if (calendar[Calendar.YEAR] == calendar1[Calendar.YEAR] && calendar[Calendar.MONTH] == calendar1[Calendar.MONTH] && calendar[Calendar.DAY_OF_MONTH] == calendar1[Calendar.DAY_OF_MONTH]) {
                return false
            }
            SharedPreferenceUtil.todayStartTime = System.currentTimeMillis()
            return true
        }

    class MyItemDecoration : ItemDecoration() {
        private var mDividerHeight = 0

        /**
         * @param outRect 边界
         * @param view    recyclerView ItemView
         * @param parent  recyclerView
         * @param state   recycler 内部数据管理
         */
        override fun getItemOffsets(
            outRect: Rect,
            view: View,
            parent: RecyclerView,
            state: RecyclerView.State
        ) {
            //设定底部边距为1px
            //outRect.set(0, 0, 0, 3);
            //第一个ItemView不需要在上面绘制分割线
            if (parent.getChildAdapterPosition(view) != 0) {
                //这里直接硬编码为1px
                outRect.top = 1
                mDividerHeight = 1
            }
        }

        override fun onDraw(c: Canvas, parent: RecyclerView) {
            // TODO: Implement this method
            super.onDraw(c, parent)
            val childCount = parent.childCount
            for (i in 0 until childCount) {
                val view = parent.getChildAt(i)
                val index = parent.getChildAdapterPosition(view)
                //第一个ItemView不需要绘制
                if (index == 0) {
                    continue
                }
                val dividerTop = (view.top - mDividerHeight).toFloat()
                val dividerLeft = parent.paddingLeft.toFloat()
                val dividerBottom = view.top.toFloat()
                val dividerRight = (parent.width - parent.paddingRight).toFloat()
                val mPaint = Paint()
                mPaint.color = c(cn.garymb.ygomobile.lite.R.color.colorDivider)
                c.drawRect(dividerLeft, dividerTop, dividerRight, dividerBottom, mPaint)
            }
        }
    }
}