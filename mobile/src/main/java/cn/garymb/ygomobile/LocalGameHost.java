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
            w1 = actW;
            h1 = actH;
        }
        maxW = Math.max(w1, h1);
        maxH = Math.min(w1, h1);
        if(immerSiveMode){
            maxW -= config.getNotouchHeight();
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
        dlg.setTitle("Report");
        dlg.setMessage("You need to collect the data of your model and the settings of the full screen / screen, and send the screenshot of the current interface to the author.");
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
        ((TextView) dlg.findViewById(R.id.tv_model)).setText(Build.MODEL + "/" + Build.PRODUCT);
        ((TextView) dlg.findViewById(R.id.tv_android)).setText(Build.VERSION.RELEASE);
        ((TextView) dlg.findViewById(R.id.tv_rom)).setText(String.valueOf(RomIdentifier.getRomInfo(activity).getRom()));
        ((TextView) dlg.findViewById(R.id.tv_rom_ver)).setText(RomIdentifier.getRomInfo(activity).getVersion());
        ((TextView) dlg.findViewById(R.id.tv_cut_screen)).setText(ScreenUtil.hasNotchInformation(activity) ? "Yes" : "No");
        ((TextView) dlg.findViewById(R.id.tv_nav_bar)).setText(ScreenUtil.isNavigationBarShown(activity) ? "Yes" : "No");
        ((TextView) dlg.findViewById(R.id.tv_screen_size)).setText(String.format("real:%dx%d, cur=%dx%d, game=%dx%d, notouch=%d",
                size.getFullW(), size.getFullH(), size.getActW(), size.getActH(), size.getWidth(), size.getHeight(), config.getNotouchHeight()));
        dlg.findViewById(R.id.btn_ok).setOnClickListener((v) -> {
            dlg.dismiss();
        });
        dlg.show();
    }
}
