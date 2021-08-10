package cn.garymb.ygomobile.ui.activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;

import java.io.File;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.core.IrrlichtBridge;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.utils.FileUtils;

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
        String title = intent.getStringExtra(IrrlichtBridge.EXTRA_SHARE_FILE);
        String ext = intent.getStringExtra(IrrlichtBridge.EXTRA_SHARE_TYPE);
        //TODO
        String sharePath = "";
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        shareIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (ext.equals("yrp")) {
            sharePath = AppsSettings.get().getReplayDir() + "/" + title;
            shareIntent.setType("*/*");
        } else if (ext.equals("lua")) {
            sharePath = AppsSettings.get().getSingleDir() + "/" + title;
            shareIntent.setType("*/*");
        } else if (ext.equals(("jpg"))) {
            sharePath = AppsSettings.get().getCardImagePath() + "/" + title;
            shareIntent.setType("image/jpeg");
        }
        File shareFile = new File(sharePath);
        shareIntent.putExtra(Intent.EXTRA_STREAM, FileUtils.toUri(this, shareFile));
        try {
            startActivity(Intent.createChooser(shareIntent, getString(R.string.send)));
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.sending_failed) + e, Toast.LENGTH_SHORT).show();
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