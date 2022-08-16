package cn.garymb.ygomobile.base;

import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.ourygo.ygomobile.util.ScaleUtils;
import com.ourygo.ygomobile.util.StatUtil;

import java.util.List;

/**
 * Create By feihua  On 2020/4/7
 */
public abstract class BaseFragemnt extends Fragment {
//    void onVisible(boolean isVisible);


    private static final String TAG = BaseFragemnt.class.getSimpleName();
    protected boolean isHorizontal = false;
    private boolean isPrepared;
    private boolean isFirstResume = true;
    /**
     * 第一次onResume中的调用onUserVisible避免操作与onFirstUserVisible操作重复
     */
    private boolean isFirstVisible = true;
    private boolean isFirstInvisible = true;
    private boolean isTakeFirst=false;

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // 获取到屏幕的方向
        int orientation = newConfig.orientation;
        switch (orientation) {
            // 横屏
            case Configuration.ORIENTATION_LANDSCAPE:
                isHorizontal = true;
                break;
            // 竖屏
            case Configuration.ORIENTATION_PORTRAIT:
                isHorizontal = false;
                break;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        isLastVisible = false;
        hidden = false;
        isFirst = true;
        isViewDestroyed = false;

        if (savedInstanceState != null) {
            //竖屏
            if (ScaleUtils.ScreenOrient(getActivity()) == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
//                setContentView(R.layout.ending_activity);
                isHorizontal = false;

            } else if (ScaleUtils.ScreenOrient(getActivity()) == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
                //横屏
//                setContentView(R.layout.ending_horizontal_activity);
                isHorizontal = true;
            }
        } else {
            if (ScaleUtils.isScreenOriatationPortrait()) {
//                setContentView(R.layout.ending_activity);
                isHorizontal = false;
            } else {
//                setContentView(R.layout.ending_horizontal_activity);
                isHorizontal = true;
            }
        }
        return super.onCreateView(inflater, container, savedInstanceState);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        initPrepare();
        Log.e("BaseFragment","创建");
        if (savedInstanceState != null) {
            //竖屏
            if (ScaleUtils.ScreenOrient(getActivity()) == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
//                setContentView(R.layout.ending_activity);
                isHorizontal = false;

            } else if (ScaleUtils.ScreenOrient(getActivity()) == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
                //横屏
//                setContentView(R.layout.ending_horizontal_activity);
                isHorizontal = true;
            }
        } else {
            if (ScaleUtils.isScreenOriatationPortrait()) {
//                setContentView(R.layout.ending_activity);
                isHorizontal = false;
            } else {
//                setContentView(R.layout.ending_horizontal_activity);
                isHorizontal = true;
            }
        }
    }


    @Override
    public void onResume() {
        super.onResume();
        isResuming = true;
//        Log.e("BaseFragment",isTakeFirst+" "+isVisible()+" "+getUserVisibleHint()+" 显示"+getClass().getName());
        if (isFirstResume) {
            isFirstResume = false;
            if (!isTakeFirst&&getUserVisibleHint()) {
                isTakeFirst=true;
                onFirstUserVisible();
            }
            return;
        }

        if (getUserVisibleHint()) {
            tryToChangeVisibility(true);
        }


    }

    @Override
    public void onPause() {
        super.onPause();
        isResuming = false;
//        Log.e("BaseFragment","隐藏"+getClass().getName());
        if (getUserVisibleHint()) {
//            onUserInvisible();
            tryToChangeVisibility(false);
        }
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
//        Log.e("BaseFragment",getClass().getName()+"显示情况1"+isVisibleToUser+" "+getUserVisibleHint());
        isTakeFirst=true;
        super.setUserVisibleHint(isVisibleToUser);
        if (isVisibleToUser) {
            if (isFirstVisible) {
                isFirstVisible = false;
                initPrepare();
            } else {
                onUserVisible();
            }
        } else {
            if (isFirstInvisible) {
                isFirstInvisible = false;
                onFirstUserInvisible();
            } else {
                onUserInvisible();
            }
        }
    }

    public synchronized void initPrepare() {
        if (isPrepared) {
            onFirstUserVisible();
        } else {
            isPrepared = true;
        }
    }

    /**
     * 第一次fragment可见（进行初始化工作）
     */
    public void onFirstUserVisible(){
        isLastVisible=true;
        isFirst=false;
        Log.e("BaseFragment","第一次可见"+getClass().getName());
        StatUtil.onResume(getClass().getName());

    }

    /**
     * fragment可见（切换回来或者onResume）
     */
    public void onUserVisible(){
        isLastVisible=true;
        isFirst=false;
        Log.e("BaseFragment","可见"+getClass().getName());
        StatUtil.onResume(getClass().getName());
    }

    /**
     * 第一次fragment不可见（不建议在此处理事件）
     */
    public void onFirstUserInvisible(){

    }

    /**
     * fragment不可见（切换掉或者onPause）
     */
    public void onUserInvisible(){
        isLastVisible=false;
        Log.e("BaseFragment","暂停"+getClass().getName());
        StatUtil.onPause(getClass().getName());
    }

    private boolean isLastVisible = false;
    private boolean hidden = false;
    private boolean isFirst = true;
    private boolean isResuming = false;
    private boolean isViewDestroyed = false;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }


    @Override
    public void onDestroyView() {
        super.onDestroyView();
        isViewDestroyed = true;
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        onHiddenChangedClient(hidden);
    }

    public void onHiddenChangedClient(boolean hidden) {
        this.hidden = hidden;
        tryToChangeVisibility(!hidden);
        if (isAdded()) {
            List<Fragment> fragments = getChildFragmentManager().getFragments();
            if (fragments != null) {
                for (Fragment fragment : fragments) {
                    if (fragment!=null&&fragment instanceof BaseFragemnt) {
                        ((BaseFragemnt) fragment).onHiddenChangedClient(hidden);
                    }
                }
            }
        }
    }

    private void tryToChangeVisibility(boolean tryToShow) {
        // 上次可见
        if (isLastVisible) {
            if (tryToShow) {
                return;
            }
            if (!isFragmentVisible()) {
//                onFragmentPause();
                onUserInvisible();
                isLastVisible = false;
            }
            // 上次不可见
        } else {
            boolean tryToHide = !tryToShow;
            if (tryToHide) {
                return;
            }
            if (isFragmentVisible()) {
                if (isFirst)
                    onFirstUserVisible();
                else
                    onUserVisible();
//                onFragmentResume(isFirst, isViewDestroyed);
                isLastVisible = true;
                isFirst = false;
            }
        }
    }

    /**
     * Fragment是否可见
     *
     * @return
     */
    public boolean isFragmentVisible() {
        if (isResuming()
                && getUserVisibleHint()
                && !hidden) {
            return true;
        }
        return false;
    }

    /**
     * Fragment 是否在前台。
     *
     * @return
     */
    private boolean isResuming() {
        return isResuming;
    }

}
