package cn.garymb.ygomobile;


import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.request.target.ViewTarget;
import com.bumptech.glide.signature.StringSignature;

import java.io.File;
import java.util.HashMap;

import cn.garymb.ygodata.YGOGameOptions;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.plus.ViewTargetPlus;
import cn.garymb.ygomobile.utils.ComponentUtils;


public class YGOStarter {
    private static Bitmap mLogo;

    private static void setFullScreen(Activity activity, ActivityShowInfo activityShowInfo) {
        activity.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        if (activity instanceof AppCompatActivity) {
            ActionBar actionBar = ((AppCompatActivity) activity).getSupportActionBar();
            if (actionBar != null) {
                actionBar.hide();
            }
        } else {
            android.app.ActionBar actionBar = activity.getActionBar();
            if (actionBar != null) {
                actionBar.hide();
            }
        }
    }

    private static void quitFullScreen(Activity activity, ActivityShowInfo activityShowInfo) {
        if (activity instanceof AppCompatActivity) {
            ActionBar actionBar = ((AppCompatActivity) activity).getSupportActionBar();
            if (activityShowInfo.hasSupperbar && actionBar != null) {
                actionBar.show();
            }
        } else {
            android.app.ActionBar actionBar = activity.getActionBar();
            if (activityShowInfo.hasBar && actionBar != null) {
                actionBar.show();
            }
        }
        final WindowManager.LayoutParams attrs = activity.getWindow().getAttributes();
        attrs.flags &= (~WindowManager.LayoutParams.FLAG_FULLSCREEN);
        activity.getWindow().setAttributes(attrs);
        activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
    }

    private static void showLoadingBg(Activity activity) {
        ActivityShowInfo activityShowInfo = Infos.get(activity);
        if (activityShowInfo == null) {
            return;
        }
        activityShowInfo.isRunning = true;
//        Log.i("checker", "show:" + activity);
//        activityShowInfo.oldRequestedOrientation = activity.getRequestedOrientation();
        activityShowInfo.rootOld = activityShowInfo.mRoot.getBackground();
        activityShowInfo.mContentView.setVisibility(View.INVISIBLE);
        //读取当前的背景图，如果卡的话，可以考虑缓存bitmap
        File bgfile = new File(AppsSettings.get().getCoreSkinPath(), Constants.CORE_SKIN_BG);
        if (bgfile.exists()) {
            Glide.with(activity.getApplicationContext()).load(bgfile)
                    .signature(new StringSignature(bgfile.getName() + bgfile.lastModified()))
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .into(activityShowInfo.mViewTarget);
        } else {
            Glide.with(activity.getApplicationContext()).load(R.drawable.bg).into(activityShowInfo.mViewTarget);
        }
        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);//强制为横屏
        setFullScreen(activity, activityShowInfo);
    }

    private static void hideLoadingBg(Activity activity, ActivityShowInfo activityShowInfo) {
        mLogo = null;
        activityShowInfo.mContentView.setVisibility(View.VISIBLE);
        if (Build.VERSION.SDK_INT >= 16) {
            activityShowInfo.mRoot.setBackground(activityShowInfo.rootOld);
        } else {
            activityShowInfo.mRoot.setBackgroundDrawable(activityShowInfo.rootOld);
        }
        activity.setRequestedOrientation(activityShowInfo.oldRequestedOrientation);
        quitFullScreen(activity, activityShowInfo);
    }

    public static ActivityShowInfo onCreated(Activity activity) {
        ActivityShowInfo activityShowInfo = Infos.get(activity);
        if (activityShowInfo == null) {
            activityShowInfo = new ActivityShowInfo();
            Infos.put(activity, activityShowInfo);
//            Log.i("checker", "init:" + activity);
        }
        activityShowInfo.oldRequestedOrientation = activity.getRequestedOrientation();
//        Log.w("checker", "activityShowInfo.oldRequestedOrientation=" + activityShowInfo.oldRequestedOrientation);
        if (activityShowInfo.oldRequestedOrientation == ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
            activityShowInfo.oldRequestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
        }
        activityShowInfo.mRoot = activity.getWindow().getDecorView();
        activityShowInfo.mViewTarget = new ViewTargetPlus(activityShowInfo.mRoot);
        activityShowInfo.mContentView = activityShowInfo.mRoot.findViewById(android.R.id.content);
        activityShowInfo.rootOld = activityShowInfo.mRoot.getBackground();
        if (activity instanceof AppCompatActivity) {
            ActionBar actionBar = ((AppCompatActivity) activity).getSupportActionBar();
            if (actionBar != null) {
                activityShowInfo.hasSupperbar = actionBar.isShowing();
            }
        } else {
            android.app.ActionBar actionBar = activity.getActionBar();
            if (actionBar != null) {
                activityShowInfo.hasBar = actionBar.isShowing();
            }
        }
        return activityShowInfo;
    }

    public static void onDestroy(Activity activity) {
        Infos.remove(activity);
    }

    public static void onResumed(Activity activity) {
        ActivityShowInfo activityShowInfo = Infos.get(activity);
//        Log.i("checker", "resume:" + activity);
        if (activityShowInfo == null) {
            return;
        }
        if (!activityShowInfo.isFirst) {
            hideLoadingBg(activity, activityShowInfo);
        }
        activityShowInfo.isFirst = false;
        activityShowInfo.isRunning = false;
    }

    private static long lasttime = 0;

    public static void startGame(Activity activity, YGOGameOptions options) {
        if (System.currentTimeMillis() - lasttime >= 1000) {
            lasttime = System.currentTimeMillis();
            showLoadingBg(activity);
            if (!ComponentUtils.isActivityRunning(activity, new ComponentName(activity, YGOMobileActivity.class))) {
                //random tips
                String[] tipsList = activity.getResources().getStringArray(R.array.tips);
                int x = (int) (Math.random() * tipsList.length);
                String tips = tipsList[x];
                Toast.makeText(activity, tips, Toast.LENGTH_LONG).show();
//            } else {
//               options = null;
           }
            Intent intent = new Intent(activity, YGOMobileActivity.class);
            if (options != null) {
                intent.putExtra(YGOGameOptions.YGO_GAME_OPTIONS_BUNDLE_KEY, options);
                intent.putExtra(YGOGameOptions.YGO_GAME_OPTIONS_BUNDLE_TIME, System.currentTimeMillis());
            }
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
        }
    }

    private static HashMap<Activity, ActivityShowInfo> Infos = new HashMap<>();

    private static class ActivityShowInfo {
        View mRoot;
        ViewTarget mViewTarget;
        boolean hasSupperbar;
        boolean hasBar;
        View mContentView;
        Drawable rootOld;
        boolean isFirst = true;
        int oldRequestedOrientation;
        boolean isRunning = false;
    }
}
