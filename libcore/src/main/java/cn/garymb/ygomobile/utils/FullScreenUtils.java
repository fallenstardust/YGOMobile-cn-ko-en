package cn.garymb.ygomobile.utils;

import android.app.Activity;
import android.os.Build;
import android.view.View;

public class FullScreenUtils {
    private boolean isFullscreen;
    private Activity activity;
    private static final int windowsFlags =
            Build.VERSION.SDK_INT >=Build.VERSION_CODES.KITKAT ? (
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY) :
                    View.SYSTEM_UI_FLAG_LOW_PROFILE;

    public FullScreenUtils(Activity activity) {
        this.activity = activity;
    }

    public FullScreenUtils(Activity activity, boolean isFullscreen) {
        this.activity = activity;
        this.isFullscreen = isFullscreen;
    }

    public boolean isFullscreen() {
        return isFullscreen;
    }

    public void setFullscreen(boolean fullscreen) {
        isFullscreen = fullscreen;
    }

    public void fullscreen() {
        if (isFullscreen() && activity != null) {
            activity.getWindow().getDecorView().setSystemUiVisibility(windowsFlags);
        }
    }

    public void onCreate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && isFullscreen() && activity != null) {
            activity.getWindow().getDecorView().setOnSystemUiVisibilityChangeListener(new View.OnSystemUiVisibilityChangeListener() {

                @Override
                public void onSystemUiVisibilityChange(int visibility) {
                    if ((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                        activity.getWindow().getDecorView().setSystemUiVisibility(windowsFlags);
                    }
                }
            });
        }
    }
}
