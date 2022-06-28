package cn.garymb.ygomobile.ui.activities;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import cn.garymb.ygomobile.lite.R;
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
    public boolean startPermissionsActivity() {
        return startPermissionsActivity(getPermissions());
    }

    /**
     * 权限申请
     *
     * @param permissions 要申请的权限列表
     * @return 是否满足权限申请条件
     */
    public boolean startPermissionsActivity(String[] permissions) {
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
