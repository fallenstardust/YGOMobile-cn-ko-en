package cn.garymb.ygomobile.ui.activities;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.Nullable;

import java.io.File;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.core.IrrlichtBridge;
import cn.garymb.ygomobile.lite.R;

public class ShareFileActivity extends BaseActivity{
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //TODO setContentView
        setContentView(R.layout.combobox_compat_layout);
        doIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
    }

    private void doIntent(Intent intent){
        String title = intent.getStringExtra(IrrlichtBridge.EXTRA_SHARE_TYPE);
        String ext = intent.getStringExtra(IrrlichtBridge.EXTRA_SHARE_FILE);
        //TODO
        Toast.makeText(this, title+"."+ext, Toast.LENGTH_LONG).show();
        File shareFile = null;
        if (ext.equals("yrp")) {
            shareFile = new File(AppsSettings.get().getResourcePath() + "/" + Constants.CORE_REPLAY_PATH + "/" + title);
        } else if (ext.equals("lua")) {
            shareFile = new File(AppsSettings.get().getResourcePath()+ "/" + Constants.CORE_SINGLE_PATH + "/" + title);
        }
        Intent shareIntent = new Intent(Intent.ACTION_SEND);
        shareIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(shareFile));
        shareIntent.setType("*/*");//此处可发送多种文件
        startActivity(Intent.createChooser(shareIntent, "分享到"));
    }
}