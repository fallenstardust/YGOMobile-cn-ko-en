package cn.garymb.ygomobile.ui.activities;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;

import cn.garymb.ygomobile.core.IrrlichtBridge;

public class ShareFileActivity extends BaseActivity{
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        //TODO setContentView

        doIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
    }

    private void doIntent(Intent intent){
        String type = intent.getStringExtra(IrrlichtBridge.EXTRA_SHARE_TYPE);
        String path = intent.getStringExtra(IrrlichtBridge.EXTRA_SHARE_FILE);
        //TODO
    }
}
