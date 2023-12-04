package cn.garymb.ygomobile.ui.activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.widget.Toast;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.YGOStarter;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.home.MainActivity;

public class LogoActivity extends Activity {
    Handler handler;
    Runnable runnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        hideBottomUIMenu();
        setContentView(R.layout.activity_logo);
        if (AppsSettings.get().isOnlyGame()) {
            YGOStarter.startGame(this, null);
            finish();
            return;
        } else {
           // File file = new File(AppsSettings.get().getDeckDir(), "1.ydk");
           // Uri uri = FileUtils.toUri(this, file);
           // Log.w("kk-test", file.getAbsolutePath() + "->" + uri);
            handler = new Handler();
            runnable = new Runnable() {
                @Override
                public void run() {
                    startActivity(new Intent(LogoActivity.this, MainActivity.class));
                    finish();
                }
            };
            handler.postDelayed(runnable, 1000);
            Toast.makeText(LogoActivity.this, R.string.logo_text, Toast.LENGTH_SHORT).show();
        }
        if (!isTaskRoot()) {
            finish();
        }

    }

    protected void hideBottomUIMenu() {
        //隐藏虚拟按键，并且全屏
        if (Build.VERSION.SDK_INT > 11 && Build.VERSION.SDK_INT < 19) { // lower api
            View v = this.getWindow().getDecorView();
            v.setSystemUiVisibility(View.GONE);
        } else if (Build.VERSION.SDK_INT >= 19) {
            //for new api versions.
            View decorView = getWindow().getDecorView();
            int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN;
            decorView.setSystemUiVisibility(uiOptions);
        }
    }
}

