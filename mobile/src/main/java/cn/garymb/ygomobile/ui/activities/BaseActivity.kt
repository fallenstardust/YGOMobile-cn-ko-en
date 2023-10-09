package cn.garymb.ygomobile.ui.activities

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.content.res.Resources
import android.graphics.Rect
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import cn.garymb.ygomobile.lite.R
import com.ourygo.ygomobile.util.DisplayUtils
import com.ourygo.ygomobile.util.OYUtil
import com.ourygo.ygomobile.util.ScaleUtils
import com.ourygo.ygomobile.util.StatUtil
import com.ourygo.ygomobile.view.OYToolbar
import ocgcore.data.Card

open class BaseActivity : AppCompatActivity() {
    @JvmField
    protected var isFragmentActivity = false
    protected val permissions = arrayOf( //            Manifest.permission.RECORD_AUDIO,
        //Manifest.permission.READ_PHONE_STATE,
        //            Manifest.permission.SYSTEM_ALERT_WINDOW,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_EXTERNAL_STORAGE
    )
    private var mExitAnim = true
    private var mEnterAnim = true
    private var mToast: Toast? = null
    @JvmField
    protected var toolbar: OYToolbar? = null

    //    @Override
    //    public void onConfigurationChanged(@NonNull Configuration newConfig) {
    //        super.onConfigurationChanged(newConfig);
    //
    //        // 获取到屏幕的方向
    //        int orientation = newConfig.orientation;
    //        switch (orientation) {
    //            // 横屏
    //            case Configuration.ORIENTATION_LANDSCAPE:
    //                setContentView(R.layout.ending_horizontal_activity);
    //                break;
    //            // 竖屏
    //            case Configuration.ORIENTATION_PORTRAIT:
    //                setContentView(R.layout.ending_activity);
    //                break;
    //        }
    //    }
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
    }

    @JvmField
    protected var isHorizontal = false
    override fun onCreate(savedInstanceState: Bundle?) {

        /*
        M 是 6.0，6.0修改了新的api，并且就已经支持修改window的刷新率了。
        但是6.0那会儿，也没什么手机支持高刷新率吧，所以也没什么人注意它。
        我更倾向于直接判断 O，也就是 Android 8.0，我觉得这个时候支持高刷新率的手机已经开始了。
        */
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//            // 获取系统window支持的模式
////            val  = window.windowManager.defaultDisplay.supportedModes;
//            Display display = getWindowManager().getDefaultDisplay();
//
//            Surface surface = new Surface(new SurfaceTexture(10));
//            TextView textVie
//            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
//                SurfaceHolder surfaceHolder=new SurfaceHolder() {
//                    @Override
//                    public void addCallback(Callback callback) {
//
//                    }
//
//                    @Override
//                    public void removeCallback(Callback callback) {
//
//                    }
//
//                    @Override
//                    public boolean isCreating() {
//                        return false;
//                    }
//
//                    @Override
//                    public void setType(int type) {
//
//                    }
//
//                    @Override
//                    public void setFixedSize(int width, int height) {
//
//                    }
//
//                    @Override
//                    public void setSizeFromLayout() {
//
//                    }
//
//                    @Override
//                    public void setFormat(int format) {
//
//                    }
//
//                    @Override
//                    public void setKeepScreenOn(boolean screenOn) {
//
//                    }
//
//                    @Override
//                    public Canvas lockCanvas() {
//                        return null;
//                    }
//
//                    @Override
//                    public Canvas lockCanvas(Rect dirty) {
//                        return null;
//                    }
//
//                    @Override
//                    public void unlockCanvasAndPost(Canvas canvas) {
//
//                    }
//
//                    @Override
//                    public Rect getSurfaceFrame() {
//                        return null;
//                    }
//
//                    @Override
//                    public Surface getSurface() {
//                        return null;
//                    }
//                }
//                getWindow().getDecorView().getd.setFrameRate(90, Surface.FRAME_RATE_COMPATIBILITY_DEFAULT);
//            } else {
//
//                Display.Mode[] modes = display.getSupportedModes();
//                Log.e("BaseActivity", "个数" + modes.length);
//                try {
//                    FileLogUtil.writeAndTime("刷新率个数" + modes.length);
//
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//
//                Display.Mode maxMode = null;
//                for (Display.Mode mode : modes) {
//                    if (maxMode == null) {
//                        maxMode = mode;
//                    } else {
//                        if (mode.getRefreshRate() > maxMode.getRefreshRate())
//                            maxMode = mode;
//                    }
//                    try {
//                        FileLogUtil.writeAndTime("" + "状态信息" + mode.getModeId() + "  " + mode.getRefreshRate() + "  " + mode.getPhysicalWidth() + "  " + mode.getPhysicalHeight());
//                    } catch (IOException e) {
//                        e.printStackTrace();
//                    }
//                    Log.e("BaseActivity", "状态信息" + mode.getModeId() + "  " + mode.getRefreshRate() + "  " + mode.getPhysicalWidth() + "  " + mode.getPhysicalHeight());
//                }
////            if (maxMode!=null) {
////                Log.e("BaseACtivity","设置刷新率"+maxMode.getRefreshRate());
//                try {
//                    FileLogUtil.writeAndTime("设置刷新率" + modes[0].getRefreshRate());
//                } catch (IOException e) {
//                    e.printStackTrace();
//                }
//                WindowManager.LayoutParams att = getWindow().getAttributes();
//                att.preferredDisplayModeId = modes[0].getModeId();
//                getWindow().setAttributes(att);
//
//            }
//        }
        super.onCreate(savedInstanceState)
        val darkMode = DisplayUtils.isDarkMode(this)
        DisplayUtils.setSystemBarStyle(
            this, window,
            false, true, false, !darkMode
        )
        if (savedInstanceState != null) {
            //竖屏
            if (ScaleUtils.ScreenOrient(this) == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
//                setContentView(R.layout.ending_activity);
                isHorizontal = false
            } else if (ScaleUtils.ScreenOrient(this) == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
                //横屏
//                setContentView(R.layout.ending_horizontal_activity);
                isHorizontal = true
            }
        } else {
            isHorizontal = if (ScaleUtils.isScreenOriatationPortrait()) {
//                setContentView(R.layout.ending_activity);
                false
            } else {
//                setContentView(R.layout.ending_horizontal_activity);
                true
            }
        }
    }

    protected fun setupActionBar() {
        val toolbar = findViewById<View>(R.id.toolbar) as Toolbar?
        toolbar?.let { setSupportActionBar(it) }
    }

    override fun onResume() {
        super.onResume()
        StatUtil.onResume(this, isFragmentActivity)
    }

    override fun onPause() {
        super.onPause()
        StatUtil.onPause(this, isFragmentActivity)
    }

    override fun getResources(): Resources {
        val res = super.getResources()
        val config = Configuration()
        config.setToDefaults()
        res.updateConfiguration(config, res.displayMetrics)
        return res
    }

    val activity: Activity
        get() = this
    val context: Context
        get() = this

    protected fun <T : View?> `$`(id: Int): T {
        return findViewById<View>(id) as T
    }

    fun setEnterAnimEnable(disableEnterAnim: Boolean) {
        mEnterAnim = disableEnterAnim
    }

    fun setExitAnimEnable(disableExitAnim: Boolean) {
        mExitAnim = disableExitAnim
    }

    protected val activityHeight: Int
        get() {
            val rect = Rect()
            window.decorView.getWindowVisibleDisplayFrame(rect)
            return rect.height()
        }

    fun enableBackHome() {
        val actionBar = supportActionBar
        actionBar?.setDisplayHomeAsUpEnabled(true)
    }

    protected open fun onBackHome() {
        finish()
    }

    protected val statusBarHeight: Int
        get() {
            val rect = Rect()
            window.decorView.getWindowVisibleDisplayFrame(rect)
            return rect.top
        }

    fun initToolbar(title: String?) {
        if (toolbar == null) toolbar = findViewById(R.id.toolbar)
        toolbar!!.setTitle(title)
    }

    fun setTitle(title: String?) {
        if (toolbar != null) {
            toolbar!!.setTitle(title)
        }
    }

    protected fun hideSystemNavBar() {
        if (Build.VERSION.SDK_INT >= 19) {
//            final WindowManager.LayoutParams params = getWindow().getAttributes();
//            params.systemUiVisibility |=
//                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
//                            View.SYSTEM_UI_FLAG_IMMERSIVE |
//                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
//                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
//            getWindow().setAttributes(params);
        }
    }

    fun setActionBarTitle(title: String?) {
        if (TextUtils.isEmpty(title)) {
            return
        }
        val actionBar = supportActionBar
        if (actionBar != null) {
            actionBar.title = title
        }
    }

    fun setActionBarSubTitle(title: String?) {
        if (TextUtils.isEmpty(title)) {
            return
        }
        val actionBar = supportActionBar
        if (actionBar != null) {
            actionBar.subtitle = title
        }
    }

    override fun startActivity(intent: Intent) {
        super.startActivity(intent)
        if (mEnterAnim) {
            setAnim()
        }
    }

    override fun startActivityForResult(intent: Intent, requestCode: Int) {
        super.startActivityForResult(intent, requestCode)
        if (mEnterAnim) {
            setAnim()
        }
    }

    override fun finish() {
        super.finish()
        if (mExitAnim) {
            overridePendingTransition(R.anim.slide_left_in, R.anim.slide_right_out)
        }
    }

    @SuppressLint("RestrictedApi")
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    override fun startActivityForResult(intent: Intent, requestCode: Int, options: Bundle?) {
        super.startActivityForResult(intent, requestCode, options)
        if (mEnterAnim) {
            setAnim()
        }
    }

    private fun setAnim() {
        overridePendingTransition(R.anim.slide_right_in, R.anim.slide_left_out)
    }

    fun setActionBarTitle(rid: Int) {
        setActionBarTitle(getString(rid))
    }
    /**
     * 权限申请
     *
     * @param permissions 要申请的权限列表
     * @return 是否满足权限申请条件
     */
    /**
     * 权限申请
     *
     * @return 是否满足权限申请条件
     */
    protected fun startPermissionsActivity(permissions: Array<String>? = this.permissions): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return false
        return if (permissions == null || permissions.size == 0) false else PermissionsActivity.startActivityForResult(
            this,
            REQUEST_PERMISSIONS,
            *permissions
        )
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackHome()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        // 拒绝时, 关闭页面, 缺少主要权限, 无法运行
        if (requestCode == REQUEST_PERMISSIONS) {
            when (resultCode) {
                PermissionsActivity.PERMISSIONS_DENIED -> onPermission(false)
                PermissionsActivity.PERMISSIONS_GRANTED -> onPermission(true)
            }
        }
    }

    /**
     * 权限申请回调
     *
     * @param isOk 权限申请是否成功
     */
    protected fun onPermission(isOk: Boolean) {
        if (isOk) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + context.packageName)
                    ).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        } else {
            showToast("喵不给我权限让我怎么运行？！")
            finish()
        }
    }

    fun s(id: Int): String {
        return OYUtil.s(id)
    }

    @SuppressLint("ShowToast")
    private fun makeToast(): Toast? {
        if (mToast == null) {
            mToast = Toast.makeText(this, "", Toast.LENGTH_SHORT)
        }
        return mToast
    }

    /**
     * Set how long to show the view for.
     *
     * @see android.widget.Toast.LENGTH_SHORT
     *
     * @see android.widget.Toast.LENGTH_LONG
     */
    fun showToast(id: Int, duration: Int) {
        showToast(getString(id), duration)
    }

    fun showToast(id: Int) {
        showToast(getString(id))
    }

    /**
     * Set how long to show the view for.
     *
     * @see android.widget.Toast.LENGTH_SHORT
     *
     * @see android.widget.Toast.LENGTH_LONG
     */
    @JvmOverloads
    fun showToast(text: CharSequence?, duration: Int = Toast.LENGTH_SHORT) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runOnUiThread { showToast(text, duration) }
            return
        }
        val toast = makeToast()
        toast!!.setText(text)
        toast.duration = duration
        toast.show()
    }

    companion object {
        protected const val REQUEST_PERMISSIONS = 0x1000 + 1
        var enImgs = intArrayOf(
            R.drawable.right_top_1,
            R.drawable.top_1,
            R.drawable.left_top_1,
            R.drawable.right_1,
            0,
            R.drawable.left_1,
            R.drawable.right_bottom_1,
            R.drawable.bottom_1,
            R.drawable.left_bottom_1
        )
        var disImgs = intArrayOf(
            R.drawable.right_top_0,
            R.drawable.top_0,
            R.drawable.left_top_0,
            R.drawable.right_0,
            0,
            R.drawable.left_0,
            R.drawable.right_bottom_0,
            R.drawable.bottom_0,
            R.drawable.left_bottom_0
        )
        var ids = intArrayOf(
            R.id.iv_9,
            R.id.iv_8,
            R.id.iv_7,
            R.id.iv_6,
            0,
            R.id.iv_4,
            R.id.iv_3,
            R.id.iv_2,
            R.id.iv_1
        )

        @JvmStatic
        fun showLinkArrows(cardInfo: Card, view: View) {
            val lk = Integer.toBinaryString(cardInfo.Defense)
            val Linekey = String.format("%09d", lk.toInt())
            for (i in ids.indices) {
                val arrow = Linekey.substring(i, i + 1)
                if (i != 4) {
                    if ("1" == arrow) {
                        view.findViewById<View>(ids[i]).setBackgroundResource(
                            enImgs[i]
                        )
                    } else {
                        view.findViewById<View>(ids[i]).setBackgroundResource(
                            disImgs[i]
                        )
                    }
                }
            }
        }
    }
}