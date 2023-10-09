package cn.garymb.ygomobile.base

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.ourygo.ygomobile.util.ScaleUtils
import com.ourygo.ygomobile.util.StatUtil

/**
 * Create By feihua  On 2020/4/7
 */
abstract class BaseFragemnt : Fragment() {
    @JvmField
    protected var isHorizontal = false
    private var isPrepared = false
    private var isFirstResume = true

    /**
     * 第一次onResume中的调用onUserVisible避免操作与onFirstUserVisible操作重复
     */
    private var isFirstVisible = true
    private var isFirstInvisible = true
    private var isTakeFirst = false

    @JvmField
    protected var isStat = true
    private var isLastVisible = false
    private var hidden = false
    private var isFirst = true

    /**
     * Fragment 是否在前台。
     *
     * @return
     */
    private var isResuming = false
    private var isViewDestroyed = false
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        // 获取到屏幕的方向
        val orientation = newConfig.orientation
        when (orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> isHorizontal = true
            Configuration.ORIENTATION_PORTRAIT -> isHorizontal = false
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        isLastVisible = false
        hidden = false
        isFirst = true
        isViewDestroyed = false
        if (savedInstanceState != null) {
            //竖屏
            if (ScaleUtils.ScreenOrient(activity) == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
//                setContentView(R.layout.ending_activity);
                isHorizontal = false
            } else if (ScaleUtils.ScreenOrient(activity) == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
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
        return super.onCreateView(inflater, container, savedInstanceState)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initPrepare()
        Log.e("BaseFragment", "创建")
        if (savedInstanceState != null) {
            //竖屏
            if (ScaleUtils.ScreenOrient(activity) == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
//                setContentView(R.layout.ending_activity);
                isHorizontal = false
            } else if (ScaleUtils.ScreenOrient(activity) == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
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

    override fun onResume() {
        super.onResume()
        isResuming = true
        //        Log.e("BaseFragment",isTakeFirst+" "+isVisible()+" "+getUserVisibleHint()+" 显示"+getClass().getName());
        if (isFirstResume) {
            isFirstResume = false
            if (!isTakeFirst && userVisibleHint) {
                isTakeFirst = true
                onFirstUserVisible()
            }
            return
        }
        if (userVisibleHint) {
            tryToChangeVisibility(true)
        }
    }

    override fun onPause() {
        super.onPause()
        isResuming = false
        //        Log.e("BaseFragment","隐藏"+getClass().getName());
        if (userVisibleHint) {
//            onUserInvisible();
            tryToChangeVisibility(false)
        }
    }

    override fun setUserVisibleHint(isVisibleToUser: Boolean) {
//        Log.e("BaseFragment",getClass().getName()+"显示情况1"+isVisibleToUser+" "+getUserVisibleHint());
        isTakeFirst = true
        super.setUserVisibleHint(isVisibleToUser)
        if (isVisibleToUser) {
            if (isFirstVisible) {
                isFirstVisible = false
                initPrepare()
            } else {
                onUserVisible()
            }
        } else {
            if (isFirstInvisible) {
                isFirstInvisible = false
                onFirstUserInvisible()
            } else {
                onUserInvisible()
            }
        }
    }

    @Synchronized
    fun initPrepare() {
        if (isPrepared) {
            onFirstUserVisible()
        } else {
            isPrepared = true
        }
    }

    /**
     * 第一次fragment可见（进行初始化工作）
     */
    open fun onFirstUserVisible() {
        isLastVisible = true
        isFirst = false
        Log.e("BaseFragment", "第一次可见" + javaClass.name)
        if (isStat) StatUtil.onResume(javaClass.name)
    }

    /**
     * fragment可见（切换回来或者onResume）
     */
    fun onUserVisible() {
        isLastVisible = true
        isFirst = false
        Log.e("BaseFragment", "可见" + javaClass.name)
        if (isStat) StatUtil.onResume(javaClass.name)
    }

    /**
     * 第一次fragment不可见（不建议在此处理事件）
     */
    fun onFirstUserInvisible() {}

    /**
     * fragment不可见（切换掉或者onPause）
     */
    fun onUserInvisible() {
        isLastVisible = false
        Log.e("BaseFragment", "暂停" + javaClass.name)
        StatUtil.onPause(javaClass.name)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        isViewDestroyed = true
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        onHiddenChangedClient(hidden)
    }

    fun onHiddenChangedClient(hidden: Boolean) {
        this.hidden = hidden
        tryToChangeVisibility(!hidden)
        if (isAdded) {
            val fragments = childFragmentManager.fragments
            for (fragment in fragments) {
                if (fragment != null && fragment is BaseFragemnt) {
                    fragment.onHiddenChangedClient(hidden)
                }
            }
        }
    }

    private fun tryToChangeVisibility(tryToShow: Boolean) {
        // 上次可见
        if (isLastVisible) {
            if (tryToShow) {
                return
            }
            if (!isFragmentVisible) {
//                onFragmentPause();
                onUserInvisible()
                isLastVisible = false
            }
            // 上次不可见
        } else {
            val tryToHide = !tryToShow
            if (tryToHide) {
                return
            }
            if (isFragmentVisible) {
                if (isFirst) onFirstUserVisible() else onUserVisible()
                //                onFragmentResume(isFirst, isViewDestroyed);
                isLastVisible = true
                isFirst = false
            }
        }
    }

    val isFragmentVisible: Boolean
        /**
         * Fragment是否可见
         *
         * @return
         */
        get() = if (isResuming
            && userVisibleHint
            && !hidden
        ) {
            true
        } else false

    companion object {
        //    void onVisible(boolean isVisible);
        private val TAG = BaseFragemnt::class.java.simpleName
    }
}