package cn.garymb.ygomobile.ui.activities;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.utils.FileLogUtil;

/**
 * 权限获取页面
 * <p/>
 * Created by wangchenlong on 16/1/26.
 */
public class PermissionsActivity extends AppCompatActivity {

    public static final int PERMISSIONS_GRANTED = 0; // 权限授权
    public static final int PERMISSIONS_DENIED = 1; // 权限拒绝

    private static final int PERMISSION_REQUEST_CODE = 0; // 系统权限管理页面的参数
    private static final String EXTRA_PERMISSIONS =
            "me.chunyu.clwang.permission.extra_permission"; // 权限参数
    private static final String PACKAGE_URL_SCHEME = "package:"; // 方案

    private PermissionsChecker mChecker; // 权限检测器
    private boolean isRequireCheck; // 是否需要系统权限检测

    // 启动当前权限页面的公开接口
    public static boolean startActivityForResult(Activity activity, int requestCode, String... permissions) {
        if (permissions == null || permissions.length == 0) return false;
        PermissionsChecker checker = PermissionsChecker.getPermissionsChecker(activity);
        if (checker.lacksPermissions(permissions)) {
            Intent intent = new Intent(activity, PermissionsActivity.class);
            intent.putExtra(EXTRA_PERMISSIONS, permissions);
            ActivityCompat.startActivityForResult(activity, intent, requestCode, null);
            return true;
        }
            return false;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getIntent() == null || !getIntent().hasExtra(EXTRA_PERMISSIONS)) {
            try {
                FileLogUtil.writeAndTime("所有权限已获取");
            } catch (IOException e) {
                e.printStackTrace();
            }
            allPermissionsGranted();
        }else {
            mChecker = PermissionsChecker.getPermissionsChecker(this);
            isRequireCheck = true;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (isRequireCheck) {
            String[] permissions = getPermissions();
            if (mChecker.lacksPermissions(permissions)) {
                requestPermissions(permissions); // 请求权限
                try {
                    FileLogUtil.writeAndTime("onResume请求权限");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                allPermissionsGranted(); // 全部权限都已获取
                try {
                    FileLogUtil.writeAndTime("onResume所有权限已获取");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        } else {
            isRequireCheck = true;
        }
    }

    // 返回传递的权限参数
    private String[] getPermissions() {
        return getIntent().getStringArrayExtra(EXTRA_PERMISSIONS);
    }

    // 请求权限兼容低版本
    private void requestPermissions(String... permissions) {
        ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
    }

    // 全部权限均已获取
    private void allPermissionsGranted() {
        setResult(PERMISSIONS_GRANTED);
        finish();
    }

    /**
     * 用户权限处理,
     * 如果全部获取, 则直接过.
     * 如果权限缺失, 则提示Dialog.
     *
     * @param requestCode  请求码
     * @param permissions  权限
     * @param grantResults 结果
     */
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PERMISSION_REQUEST_CODE && hasAllPermissionsGranted(grantResults)) {
            try {
                FileLogUtil.writeAndTime("权限请求回调：所有权限已获取");
            } catch (IOException e) {
                e.printStackTrace();
            }
            isRequireCheck = true;
            allPermissionsGranted();
        } else {
            try {
                FileLogUtil.writeAndTime("权限请求回调：权限未得到");
            } catch (IOException e) {
                e.printStackTrace();
            }
            isRequireCheck = false;
            showMissingPermissionDialog(getNoPermission(permissions,grantResults));
        }
    }

    //获取未同意的权限
    private List<String> getNoPermission(String[] permissions, int[] grantResults){
        List<String> permissionList=new ArrayList<>();
        for (int i=0;i<grantResults.length;i++) {
            int grantResult = grantResults[i];
            if (grantResult == PackageManager.PERMISSION_DENIED) {
               permissionList.add(permissions[i]);
            }
        }
        return permissionList;
    }

    // 含有全部的权限
    private boolean hasAllPermissionsGranted(@NonNull int[] grantResults) {
        for (int grantResult : grantResults) {
            if (grantResult == PackageManager.PERMISSION_DENIED) {
                return false;
            }
        }
        return true;
    }

    // 显示缺失权限提示
    private void showMissingPermissionDialog(List<String > permissionList) {
        AlertDialog.Builder builder = new AlertDialog.Builder(PermissionsActivity.this);
        builder.setTitle(R.string.help);
        String noPermission="";
        for (String s:permissionList)
            noPermission+="\n"+s;
        builder.setMessage(getString(R.string.string_help_text)+noPermission);

        // 拒绝, 退出应用
        builder.setNegativeButton(R.string.quit, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                setResult(PERMISSIONS_DENIED);
                finish();
            }
        });

        builder.setPositiveButton(R.string.settings, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                startAppSettings();
            }
        });
        Dialog dialog = builder.show();
        dialog.setCanceledOnTouchOutside(false);
        dialog.setOnCancelListener((v) -> {
            finish();
        });
    }

    // 启动应用的设置
    private void startAppSettings() {
        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.parse(PACKAGE_URL_SCHEME + getPackageName()));
        startActivity(intent);
    }
}

/**
 * 检查权限的工具类
 * <p/>
 * Created by wangchenlong on 16/1/26.
 */
class PermissionsChecker {
    private final Context mContext;
    private static  PermissionsChecker sPermissionsChecker;

    public static PermissionsChecker getPermissionsChecker(Context context) {
        if(sPermissionsChecker==null){
            synchronized (PermissionsChecker.class){
                if(sPermissionsChecker==null){
                    sPermissionsChecker=new PermissionsChecker(context);
                }
            }
        }
        return sPermissionsChecker;
    }

    private PermissionsChecker(Context context) {
        mContext = context.getApplicationContext();
    }

    // 判断权限集合
    public boolean lacksPermissions(String... permissions) {
        for (String permission : permissions) {
            if (lacksPermission(permission)) {
                return true;
            }
        }
        return false;
    }

    // 判断是否缺少权限
    private boolean lacksPermission(String permission) {
        return ContextCompat.checkSelfPermission(mContext, permission) ==
                PackageManager.PERMISSION_DENIED;
    }
}