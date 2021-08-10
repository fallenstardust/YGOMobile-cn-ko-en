package cn.garymb.ygomobile.ui.activities;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.core.content.FileProvider;

import java.io.File;
import java.util.List;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.core.IrrlichtBridge;
import cn.garymb.ygomobile.utils.FileUtils;

import static cn.garymb.ygomobile.Constants.CORE_REPLAY_PATH;

public class ShareFileActivity extends Activity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //TODO setContentView
        doIntent(getIntent());
        hideBottomUIMenu();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
    }

    private void doIntent(Intent intent) {
        String type = intent.getStringExtra(IrrlichtBridge.EXTRA_SHARE_TYPE);
        String path = intent.getStringExtra(IrrlichtBridge.EXTRA_SHARE_FILE);

        File file;
        String title;
        String mineType;
        if("yrp".equals(type)){
            file = new File(AppsSettings.get().getReplayDir(), path);
            title= "分享录像";
            mineType = "text/plain";
        } else if("ydk".equals(type)){
            file = new File(AppsSettings.get().getDeckDir(), path);
            title= "分享卡组";
            mineType = "text/plain";
        } else if("jpg".equals(type)){
            file = new File(AppsSettings.get().getDeckDir(), path);
            title= "分享图片";
            mineType = "image/*";
        } else {
            finish();
            return;
        }
        Uri uri = FileUtils.toUri(this, file);
//        Log.d("kk-test", "file="+file+", canRead="+(file.exists() && file.canRead()));
//        Log.d("kk-test", "uri="+uri);
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
        shareIntent.setType(mineType);
        try{
//            Log.d("kk-test", "uri="+uri);
//            ParcelFileDescriptor pfd = getContentResolver().openFileDescriptor(uri, "r");
//            if(pfd != null){
//                pfd.close();
//                Log.d("kk-test", "open ok");
//            }
//            List<ResolveInfo> resInfoList = this.getPackageManager()
//                    .queryIntentActivities(shareIntent, 0);
//            for (ResolveInfo resolveInfo : resInfoList) {
//                String packageName = resolveInfo.activityInfo.packageName;
//                this.grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
//            }
            startActivity(Intent.createChooser(shareIntent, title));
        }catch (Throwable e){
            Log.w("kk-test", "open uri error:"+uri, e);
            Toast.makeText(this, "没有可以分享的应用", Toast.LENGTH_SHORT).show();
        }
        finish();
    }

    protected void hideBottomUIMenu() {
        //隐藏虚拟按键，并且全屏
        if (Build.VERSION.SDK_INT > 11 && Build.VERSION.SDK_INT < 19) { // lower api
            View v = this.getWindow().getDecorView();
            v.setSystemUiVisibility(View.GONE);
        } else if (Build.VERSION.SDK_INT >= 19) {
            //for new api versions.
            View decorView = getWindow().getDecorView();
            int uiOptions = View.SYSTEM_UI_FLAG_FULLSCREEN;
//            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
//                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN;
            decorView.setSystemUiVisibility(uiOptions);
        }
    }
}
