package cn.garymb.ygomobile;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.graphics.Point;
import android.net.Uri;
import android.os.Build;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import org.minidns.record.A;

import java.io.File;

import cn.garymb.ygomobile.core.YGOCore;
import cn.garymb.ygomobile.interfaces.GameConfig;
import cn.garymb.ygomobile.interfaces.GameHost;
import cn.garymb.ygomobile.interfaces.GameSize;
import cn.garymb.ygomobile.lite.BuildConfig;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.plus.DialogPlus;
import cn.garymb.ygomobile.ui.plus.VUiKit;
import cn.garymb.ygomobile.utils.ScreenUtil;
import cn.garymb.ygomobile.utils.rom.RomIdentifier;
import libwindbot.windbot.WindBot;

import static cn.garymb.ygomobile.Constants.CORE_BOT_CONF_PATH;
import static cn.garymb.ygomobile.Constants.DATABASE_NAME;
import static cn.garymb.ygomobile.Constants.URL_FEEDBACK;

class LocalGameHost extends GameHost {
    private Context context;
    private SharedPreferences settings;
    private boolean mInitBot = false;
    private GameSize mGameSize;

    LocalGameHost(Context context) {
        super(context);
        this.context = context;
        settings = context.getSharedPreferences("ygo_settings", Context.MODE_PRIVATE);
        if (!GameApplication.isGameProcess()) {
            Log.e("kk", "GameHost don't running in game process.");
        }
    }

    @Override
    public String getSetting(String key) {
        if (YGOCore.CONF_LAST_DECK.equals(key)) {
            return LocalConfig.getInstance(context).getLastDeck();
        } else if (YGOCore.CONF_LAST_CATEGORY.equals(key)) {
            return LocalConfig.getInstance(context).getLastCategory();
        } else {
            return settings.getString(key, "");
        }
    }

    @Override
    public int getIntSetting(String key, int def) {
        return settings.getInt(key, def);
    }

    @SuppressLint("ApplySharedPref")
    @Override
    public void saveIntSetting(String key, int value) {
        settings.edit().putInt(key, value).commit();
    }

    @SuppressLint("ApplySharedPref")
    @Override
    public void saveSetting(String key, String value) {
        if (YGOCore.CONF_LAST_DECK.equals(key)) {
            LocalConfig.getInstance(context).setLastDeck(value);
        } else if (YGOCore.CONF_LAST_CATEGORY.equals(key)) {
            LocalConfig.getInstance(context).setLastCategory(value);
        } else {
            settings.edit().putString(key, value).commit();
        }
    }

    @Override
    public void initWindbot(NativeInitOptions options, GameConfig config) {
//        if (options.mDbList.size() == 0) {
//            return;
//        }
//        String cdb = options.mDbList.get(0);
//        Log.i("kk", "cdb=" + cdb);
//        try {
//            WindBot.initAndroid(AppsSettings.get().getResourcePath(),
//                    cdb,
//                    options.mResDir + "/" + CORE_BOT_CONF_PATH);
//            mInitBot = true;
//        } catch (Throwable e) {
//            e.printStackTrace();
//            Log.i("kk", "initAndroid", e);
//        }
    }

    @Override
    public void runWindbot(String cmd) {
//        if (mInitBot) {
//            WindBot.runAndroid(cmd);
//        } else {
//            VUiKit.show(context, "run bot error");
//        }
        Intent intent = new Intent(Constants.WINDBOT_ACTION);
        intent.putExtra("args", cmd);
        intent.setPackage(context.getPackageName());
        context.sendBroadcast(intent);
    }


    @Override
    public AssetManager getGameAsset() {
        return context.getAssets();
    }

    @Override
    public GameSize getGameSize(Activity activity, GameConfig config) {
        boolean immerSiveMode = config.isImmerSiveMode();
        boolean keepScale = config.isKeepScale();
        int maxW, maxH;
        int fullW, fullH, actW, actH;
        Point size = new Point();
        activity.getWindowManager().getDefaultDisplay().getRealSize(size);
        fullW = size.x;
        fullH = size.y;
        actW = activity.getWindowManager().getDefaultDisplay().getWidth();
        actH = activity.getWindowManager().getDefaultDisplay().getHeight();
        int w1, h1;
        if (immerSiveMode) {
            w1 = fullW;
            h1 = fullH;
        } else {
            //全面屏，非沉浸模式，自动隐藏虚拟键，需要适配
            w1 = actW;
            h1 = actH;
        }
        maxW = Math.max(w1, h1);
        maxH = Math.min(w1, h1);
        int notchHeight = config.getNotchHeight();
        if (notchHeight > 0 && immerSiveMode) {
            maxW -= notchHeight;
        }
        Log.i("kk", "real=" + fullW + "x" + fullH + ",cur=" + actW + "x" + actH + ",use=" + maxW + "x" + maxH);
        float sx, sy, scale;
        int gw, gh;
        if (keepScale) {
            sx = (float) maxW / YGOCore.GAME_WIDTH;
            sy = (float) maxH / YGOCore.GAME_HEIGHT;
            scale = Math.min(sx, sy);
            gw = (int) (YGOCore.GAME_WIDTH * scale);
            gh = (int) (YGOCore.GAME_HEIGHT * scale);
        } else {
            gw = maxW;
            gh = maxH;
        }
        Log.i("kk", "game=" + gw + "x" + gh);
        //fix touch point
        int left = (maxW - gw) / 2;
        int top = (maxH - gh) / 2;
        if (notchHeight > 0 && !immerSiveMode) {
            //left += (fullW - actW) / 2;
            //fix touch
            //left = (maxW - gw - config.getNotchHeight()) / 2;
        }
        Log.i("kk", "touch fix=" + left + "x" + top);
        //if(huawei and liuhai){
        // left-=liuhai
        // }
        GameSize gameSize = new GameSize(gw, gh, left, top);
        gameSize.setScreen(fullW, fullH, actW, actH);
        mGameSize = gameSize;
        return gameSize;
    }

    @Override
    public boolean isDebugMode() {
        return BuildConfig.DEBUG;
    }

    @Override
    public void onBeforeCreate(Activity activity) {
//        if(AppsSettings.get().isHideHwNotouch()){
//            HwNotchSizeUtil.setFullScreenWindowLayoutInDisplayCutout(activity);
//        }
    }

    @Override
    public void onAfterCreate(Activity activity) {

    }

    @Override
    public void onGameExit(Activity activity) {

    }

    @Override
    public void onGameReport(Activity activity, GameConfig config) {
        DialogPlus dlg = new DialogPlus(activity);
        dlg.setTitle(R.string.tip);
        dlg.setMessage(R.string.user_notice);
        dlg.setLeftButtonListener((d, id) -> {
            //
            dlg.dismiss();
            showDialog(activity, config);
        });
        dlg.setRightButtonListener((d, id) -> {
            dlg.dismiss();
        });
        dlg.show();
    }

    @SuppressLint({"DefaultLocale", "SetTextI18n"})
    private void showDialog(Activity activity, GameConfig config) {
        DialogPlus dlg = new DialogPlus(activity);
        dlg.setView(R.layout.dialog_report);
        GameSize size = mGameSize;
        if (size == null) {
            size = getGameSize(activity, config);
            Log.i("kk", "gen size " + size);
        }
        ((TextView) dlg.findViewById(R.id.tv_version)).setText(BuildConfig.VERSION_NAME + "/" + BuildConfig.VERSION_CODE);
        ((TextView) dlg.findViewById(R.id.tv_model)).setText(Build.MODEL + "/" + Build.PRODUCT);
        ((TextView) dlg.findViewById(R.id.tv_android)).setText(Build.VERSION.RELEASE + " (" + Build.VERSION.SDK_INT + ")");
        ((TextView) dlg.findViewById(R.id.tv_rom)).setText(String.valueOf(RomIdentifier.getRomInfo(activity)));
        ((TextView) dlg.findViewById(R.id.tv_cut_screen)).setText((config.getNotchHeight() > 0) ? "Yes/" + config.getNotchHeight() : "No");
        if (ScreenUtil.hasNavigationBar(activity)) {
            ((TextView) dlg.findViewById(R.id.tv_nav_bar)).setText("Yes/" + (ScreenUtil.isNavigationBarShown(activity) ? "Show" : "Hide"));
        } else {
            ((TextView) dlg.findViewById(R.id.tv_nav_bar)).setText("No");
        }

        ((TextView) dlg.findViewById(R.id.tv_screen_size)).setText(String.format("r:%dx%d,a=%dx%d,k=%s, g=%dx%d,c=%dx%d",
                size.getFullW(), size.getFullH(), size.getActW(), size.getActH(), config.isKeepScale() ? "Y" : "N", size.getWidth(), size.getHeight(), size.getTouchX(), size.getTouchY()));
        dlg.findViewById(R.id.btn_report).setOnClickListener((v) -> {
            Intent intent = new Intent();
            intent.setAction(Intent.ACTION_VIEW);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.setData(Uri.parse(Constants.URL_FEEDBACK));
            context.startActivity(intent);
            dlg.dismiss();
        });
        dlg.findViewById(R.id.btn_cancel).setOnClickListener((v) -> {
            dlg.dismiss();
        });
        dlg.show();
    }
}
