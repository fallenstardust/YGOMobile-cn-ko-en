package cn.garymb.ygomobile.ui.activities;

import android.content.Intent;
import android.content.res.AssetManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.daimajia.numberprogressbar.NumberProgressBar;

import java.io.File;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.YGOStarter;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.home.MainActivity;
import cn.garymb.ygomobile.ui.home.ResCheckTask;
import cn.garymb.ygomobile.ui.plus.VUiKit;
import cn.garymb.ygomobile.utils.IOUtils;
import cn.garymb.ygomobile.utils.SystemUtils;
import libwindbot.windbot.WindBot;
import ocgcore.CardManager;
import ocgcore.ConfigManager;
import ocgcore.DataManager;

import static cn.garymb.ygomobile.Constants.CORE_BOT_CONF_PATH;
import static cn.garymb.ygomobile.Constants.DATABASE_NAME;

public class LogoActivity extends BaseActivity {
    public static final String EXTRA_NEW_VERSION = "isNew";
    public static final String EXTRA_ERROR = "error";
    private View ly_loading;
    private TextView tv_loading;
    private NumberProgressBar number_progress_bar;
    private static final String TAG = "ResCheckTask";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        int windowsFlags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE/* 系统UI变化不触发relayout */
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION/* 导航栏悬浮在布局上面 */
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN/* 状态栏悬浮在布局上面 */
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION/* 隐藏导航栏 */
                | View.SYSTEM_UI_FLAG_FULLSCREEN/* 隐藏状态栏 */
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY/* 沉浸模式 */;
        getWindow().getDecorView().setSystemUiVisibility(windowsFlags);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_logo);
        number_progress_bar = findViewById(R.id.number_progress_bar);
        ly_loading = findViewById(R.id.ly_loading);
        tv_loading = findViewById(R.id.tv_loading);
        if (AppsSettings.get().isOnlyGame()) {
            YGOStarter.startGame(this, null);
            finish();
            return;
        }
        if (!isTaskRoot()) {
            finish();
        }
    }

    private void startLoad() {
        if(ly_loading.getVisibility() == View.VISIBLE){
            return;
        }
        ly_loading.setVisibility(View.VISIBLE);
        int vercode = SystemUtils.getVersion(this);
        boolean isNewVersion;
        int oldVer = AppsSettings.get().getAppVersion();
        Log.i("ygomobile", "check version old=" + oldVer + ", new=" + vercode);
        if (oldVer < vercode) {
            AppsSettings.get().setAppVersion(vercode);
            isNewVersion = true;
        } else {
            isNewVersion = false;
        }
        ResCheckTask resCheckTask = new ResCheckTask(this, isNewVersion, 1000);
        number_progress_bar.setMax(100);
        VUiKit.defer()
                .when(resCheckTask)
                .progress((str) -> {
                    tv_loading.setText(str);
                    number_progress_bar.setProgress(resCheckTask.getProgress());
                })
                .fail((e) -> {
                    Log.e(TAG, "check res", e);
                    onCheckCompleted(isNewVersion, ResCheckTask.ERROR_CORE_OTHER);
                })
                .done((err) -> {
                    tv_loading.setText(R.string.tip_load_ok);
                    number_progress_bar.setProgress(100);
                    onCheckCompleted(isNewVersion, err);
                });
    }

    private void onCheckCompleted(boolean isNew, int err) {
        initWindBot();
        Intent intent = new Intent(this, MainActivity.class);
        intent.putExtra(EXTRA_NEW_VERSION, isNew);
        intent.putExtra(EXTRA_ERROR, err);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    private void initWindBot() {
        Log.i("kk", "files=" + getFilesDir().getPath());
        Log.i("kk", "cdb=" + AppsSettings.get().getDataBasePath() + "/" + DATABASE_NAME);
        try {
            WindBot.initAndroid(AppsSettings.get().getResourcePath(),
                    AppsSettings.get().getDataBasePath() + "/" + DATABASE_NAME,
                    AppsSettings.get().getResourcePath() + "/" + CORE_BOT_CONF_PATH);
        } catch (Throwable e) {
            Log.e("kk", "init windbot", e);
        }
    }

    @Override
    public void finish() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            finishAndRemoveTask();
        } else {
            super.finish();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // 拒绝时, 关闭页面, 缺少主要权限, 无法运行
        if (requestCode == REQUEST_PERMISSIONS && resultCode == PermissionsActivity.PERMISSIONS_DENIED) {
            finish();
        } else {
            startLoad();
        }
    }

  /*  @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        if(BuildConfig.DEBUG){
            startActivity(new Intent(LogoActivity.this, MainActivity.class));
            finish();
            return;
        }
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_logo);
        ImageView image = (ImageView) $(R.id.logo);
        AlphaAnimation anim = new AlphaAnimation(0.1f, 1.0f);
        anim.setDuration(Constants.LOG_TIME);
        anim.setAnimationListener(new AnimationListener() {

            @Override
            public void onAnimationStart(Animation arg0) {
                Toast.makeText(LogoActivity.this, R.string.logo_text, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onAnimationRepeat(Animation arg0) {

            }

            @Override
            public void onAnimationEnd(Animation arg0) {
                startActivity(new Intent(LogoActivity.this, MainActivity.class));
                finish();
            }
        });
        image.setAnimation(anim);
        anim.start();
    }*/
}

