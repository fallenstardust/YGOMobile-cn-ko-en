package cn.garymb.ygomobile.ui.activities;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Looper;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.utils.YGOUtil;
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
        Toolbar toolbar = findViewById(R.id.toolbar);
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
        return findViewById(id);
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

    /** 授权后待执行的动作（权限申请是独立 Activity，结果经 onActivityResult → onPermission 回来） */
    private Runnable pendingPermissionAction;

    /**
     * 按需申请权限并在授权后执行动作：只在真正需要访问本地外部文件/图片时调用
     *（如备份/还原卡组、选择头像/卡背/背景、选择字体文件）。
     * 权限已满足则立即执行；缺失则先跳权限申请页，授权通过后由 {@link #onPermission(boolean)}
     * 补执行，不再在页面初始化阶段提前申请。
     *
     * @return 是否已受理（已执行或已挂起等待授权）
     */
    public boolean runWithPermissions(Runnable action) {
        if (action == null) {
            return false;
        }
        if (!startPermissionsActivity()) {
            // 权限已满足（或系统低于 M 无需申请）：直接执行
            action.run();
            return true;
        }
        pendingPermissionAction = action;
        return true;
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
        Runnable pending = pendingPermissionAction;
        pendingPermissionAction = null;
        if (isOk) {
            // 按需申请成功：补执行发起申请时挂起的动作（备份/还原、选头像/卡背/背景等）
            if (pending != null) {
                pending.run();
            }
        } else {
            // 按需申请被拒：只放弃本次动作，不关闭当前页面（用户可稍后在系统设置里授权）
            YGOUtil.showTextToast("喵不给我权限让我怎么运行？！");
        }
    }
}
