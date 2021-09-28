package cn.garymb.ygomobile.ui.activities;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.Display;
import android.view.MenuItem;
import android.view.Surface;
import android.view.SurfaceControl;
import android.view.SurfaceHolder;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import java.io.IOException;

import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.utils.FileLogUtil;
import ocgcore.data.Card;


public class BaseActivity extends AppCompatActivity {
    protected final static int REQUEST_PERMISSIONS = 0x1000 + 1;
    public static int[] enImgs = new int[]{
            R.drawable.right_top_1,
            R.drawable.top_1,
            R.drawable.left_top_1,
            R.drawable.right_1,
            0,
            R.drawable.left_1,
            R.drawable.right_bottom_1,
            R.drawable.bottom_1,
            R.drawable.left_bottom_1
    };
    public static int[] disImgs = new int[]{
            R.drawable.right_top_0,
            R.drawable.top_0,
            R.drawable.left_top_0,
            R.drawable.right_0,
            0,
            R.drawable.left_0,
            R.drawable.right_bottom_0,
            R.drawable.bottom_0,
            R.drawable.left_bottom_0,
    };
    public static int[] ids = new int[]{
            R.id.iv_9,
            R.id.iv_8,
            R.id.iv_7,
            R.id.iv_6,
            0,
            R.id.iv_4,
            R.id.iv_3,
            R.id.iv_2,
            R.id.iv_1
    };
    protected final String[] PERMISSIONS = {
//            Manifest.permission.RECORD_AUDIO,
            //Manifest.permission.READ_PHONE_STATE,
//            Manifest.permission.SYSTEM_ALERT_WINDOW,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE,
    };
    private boolean mExitAnim = true;
    private boolean mEnterAnim = true;
    private Toast mToast;

    public static void showLinkArrows(Card cardInfo, View view) {
        String lk = Integer.toBinaryString(cardInfo.Defense);
        String Linekey = String.format("%09d", Integer.parseInt(lk));
        for (int i = 0; i < ids.length; i++) {
            String arrow = Linekey.substring(i, i + 1);
            if (i != 4) {
                if ("1".equals(arrow)) {
                    view.findViewById(ids[i]).setBackgroundResource(enImgs[i]);
                } else {
                    view.findViewById(ids[i]).setBackgroundResource(disImgs[i]);
                }
            }
        }
    }

    protected String[] getPermissions() {
        return PERMISSIONS;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {

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

        super.onCreate(savedInstanceState);
    }

    protected void setupActionBar() {
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        if (toolbar != null) {
            setSupportActionBar(toolbar);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    public Resources getResources() {
        Resources res = super.getResources();
        Configuration config = new Configuration();
        config.setToDefaults();
        res.updateConfiguration(config, res.getDisplayMetrics());
        return res;
    }

    public Activity getActivity() {
        return this;
    }

    public Context getContext() {
        return this;
    }

    protected <T extends View> T $(int id) {
        return (T) findViewById(id);
    }

    public void setEnterAnimEnable(boolean disableEnterAnim) {
        this.mEnterAnim = disableEnterAnim;
    }

    public void setExitAnimEnable(boolean disableExitAnim) {
        this.mExitAnim = disableExitAnim;
    }

    protected int getActivityHeight() {
        Rect rect = new Rect();
        getWindow().getDecorView().getWindowVisibleDisplayFrame(rect);
        return rect.height();
    }

    public void enableBackHome() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
//            View view = $(R.id.btn_back);
//            if (view != null) {
//                view.setVisibility(View.VISIBLE);
//                view.setOnClickListener((v) -> {
//                    onBackHome();
//                });
//            }
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    protected void onBackHome() {
        finish();
    }

    protected int getStatusBarHeight() {
        Rect rect = new Rect();
        getWindow().getDecorView().getWindowVisibleDisplayFrame(rect);
        return rect.top;
    }

//    @Override
//    public void onWindowFocusChanged(boolean hasFocus) {
//        if (hasFocus) {
//            hideSystemNavBar();
//        }
//        super.onWindowFocusChanged(hasFocus);
//    }

    protected void hideSystemNavBar() {
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

    public void setActionBarTitle(String title) {
        if (TextUtils.isEmpty(title)) {
            return;
        }
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setTitle(title);
        }
    }

    public void setActionBarSubTitle(String title) {
        if (TextUtils.isEmpty(title)) {
            return;
        }
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setSubtitle(title);
        }
    }

    @Override
    public void startActivity(Intent intent) {
        super.startActivity(intent);
        if (mEnterAnim) {
            setAnim();
        }
    }

    @Override
    public void startActivityForResult(Intent intent, int requestCode) {
        super.startActivityForResult(intent, requestCode);
        if (mEnterAnim) {
            setAnim();
        }
    }

    @Override
    public void finish() {
        super.finish();
        if (mExitAnim) {
            overridePendingTransition(R.anim.slide_left_in, R.anim.slide_right_out);
        }
    }

    @SuppressLint("RestrictedApi")
    @RequiresApi(api = Build.VERSION_CODES.JELLY_BEAN)
    @Override
    public void startActivityForResult(Intent intent, int requestCode, @Nullable Bundle options) {
        super.startActivityForResult(intent, requestCode, options);
        if (mEnterAnim) {
            setAnim();
        }
    }

    private void setAnim() {
        overridePendingTransition(R.anim.slide_right_in, R.anim.slide_left_out);
    }

    public void setActionBarTitle(int rid) {
        setActionBarTitle(getString(rid));
    }

    /**
     * 权限申请
     *
     * @return 是否满足权限申请条件
     */
    protected boolean startPermissionsActivity() {
        return startPermissionsActivity(getPermissions());
    }

    /**
     * 权限申请
     *
     * @param permissions 要申请的权限列表
     * @return 是否满足权限申请条件
     */
    protected boolean startPermissionsActivity(String[] permissions) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M)
            return false;
        if (permissions == null || permissions.length == 0)
            return false;
        return PermissionsActivity.startActivityForResult(this, REQUEST_PERMISSIONS, permissions);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackHome();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // 拒绝时, 关闭页面, 缺少主要权限, 无法运行
        if (requestCode == REQUEST_PERMISSIONS) {
            switch (resultCode) {
                case PermissionsActivity.PERMISSIONS_DENIED:
                    onPermission(false);
                    break;
                case PermissionsActivity.PERMISSIONS_GRANTED:
                    onPermission(true);
                    break;
            }
        }
    }

    /**
     * 权限申请回调
     *
     * @param isOk 权限申请是否成功
     */
    protected void onPermission(boolean isOk) {
        if (isOk) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !getContext().getPackageManager().canRequestPackageInstalls()) {
                getContext().startActivity(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getContext().getPackageName())).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            }
        } else {
            showToast("喵不给我权限让我怎么运行？！");
            finish();
        }
    }

    @SuppressLint("ShowToast")
    private Toast makeToast() {
        if (mToast == null) {
            mToast = Toast.makeText(this, "", Toast.LENGTH_SHORT);
        }
        return mToast;
    }

    /**
     * Set how long to show the view for.
     *
     * @see android.widget.Toast#LENGTH_SHORT
     * @see android.widget.Toast#LENGTH_LONG
     */
    public void showToast(int id, int duration) {
        showToast(getString(id), duration);
    }

    public void showToast(CharSequence text) {
        showToast(text, Toast.LENGTH_SHORT);
    }

    public void showToast(int id) {
        showToast(getString(id));
    }

    /**
     * Set how long to show the view for.
     *
     * @see android.widget.Toast#LENGTH_SHORT
     * @see android.widget.Toast#LENGTH_LONG
     */
    public void showToast(CharSequence text, int duration) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runOnUiThread(() -> showToast(text, duration));
            return;
        }
        Toast toast = makeToast();
        toast.setText(text);
        toast.setDuration(duration);
        toast.show();
    }
}
