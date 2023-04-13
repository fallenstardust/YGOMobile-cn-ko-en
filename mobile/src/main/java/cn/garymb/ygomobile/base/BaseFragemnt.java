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

import cn.garymb.ygomobile.utils.ScaleUtils;

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
            //                setContentView(R.layout.ending_activity);
            //                setContentView(R.layout.ending_horizontal_activity);
            isHorizontal = !ScaleUtils.isScreenOriatationPortrait();
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
            //                setContentView(R.layout.ending_activity);
            //                setContentView(R.layout.ending_horizontal_activity);
            isHorizontal = !ScaleUtils.isScreenOriatationPortrait();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (isFirstResume) {
            isFirstResume = false;
            return;
        }
        if (getUserVisibleHint()) {
            onUserVisible();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (getUserVisibleHint()) {
            onUserInvisible();
        }
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
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
    public abstract void onFirstUserVisible();

    /**
     * fragment可见（切换回来或者onResume）
     */
    public abstract void onUserVisible();

    /**
     * 第一次fragment不可见（不建议在此处理事件）
     */
    public abstract void onFirstUserInvisible();

    /**
     * fragment不可见（切换掉或者onPause）
     */
    public abstract void onUserInvisible();

    public abstract void onBackHome();

    public abstract boolean onBackPressed();
}
