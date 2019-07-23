package cn.garymb.ygomobile;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.os.Environment;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.util.Log;

import org.json.JSONArray;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import cn.garymb.ygomobile.ui.preference.PreferenceFragmentPlus;
import cn.garymb.ygomobile.utils.FileLogUtil;
import cn.garymb.ygomobile.utils.SystemUtils;

import static cn.garymb.ygomobile.Constants.CORE_EXPANSIONS;
import static cn.garymb.ygomobile.Constants.CORE_SYSTEM_PATH;
import static cn.garymb.ygomobile.Constants.DEF_PREF_FONT_SIZE;
import static cn.garymb.ygomobile.Constants.DEF_PREF_NOTCH_HEIGHT;
import static cn.garymb.ygomobile.Constants.DEF_PREF_ONLY_GAME;
import static cn.garymb.ygomobile.Constants.DEF_PREF_READ_EX;
import static cn.garymb.ygomobile.Constants.PREF_DEF_IMMERSIVE_MODE;
import static cn.garymb.ygomobile.Constants.PREF_DEF_SENSOR_REFRESH;
import static cn.garymb.ygomobile.Constants.PREF_FONT_SIZE;
import static cn.garymb.ygomobile.Constants.PREF_IMMERSIVE_MODE;
import static cn.garymb.ygomobile.Constants.PREF_LOCK_SCREEN;
import static cn.garymb.ygomobile.Constants.PREF_NOTCH_HEIGHT;
import static cn.garymb.ygomobile.Constants.PREF_ONLY_GAME;
import static cn.garymb.ygomobile.Constants.PREF_READ_EX;
import static cn.garymb.ygomobile.Constants.PREF_SENSOR_REFRESH;

public class AppsSettings {
    private static final String PREF_VERSION = "app_version";
    private static AppsSettings sAppsSettings;
    private Context context;
    private PreferenceFragmentPlus.SharedPreferencesPlus mSharedPreferences;
    private float mScreenHeight, mScreenWidth, mDensity;


    private AppsSettings(Context context) {
        this.context = context;
        mSharedPreferences = PreferenceFragmentPlus.SharedPreferencesPlus.create(context, context.getPackageName() + ".settings");
        mSharedPreferences.setAutoSave(true);
        Log.e("YGOMobileLog", "初始化类地址:  " + System.identityHashCode(this));
        update(context);
    }

    public static void init(Context context) {
        if (sAppsSettings == null) {
            sAppsSettings = new AppsSettings(context);
        }
    }

    public static AppsSettings get() {
        return sAppsSettings;
    }

    public File getSystemConfig() {
        return new File(getResourcePath(), CORE_SYSTEM_PATH);
    }

    public void update(Context context) {
        mDensity = context.getResources().getDisplayMetrics().density;
        mScreenHeight = context.getResources().getDisplayMetrics().heightPixels;
        mScreenWidth = context.getResources().getDisplayMetrics().widthPixels;

        if (isImmerSiveMode() && context instanceof Activity) {
            DisplayMetrics dm = SystemUtils.getHasVirtualDisplayMetrics((Activity) context);
            if (dm != null) {

                int height = Math.max(dm.widthPixels, dm.heightPixels);
                Log.e("YGOMobileLog", "类地址" + System.identityHashCode(this));

                int notchHeight = getNotchHeight();

                try {
                    FileLogUtil.writeAndTime("是否沉浸:  " + isImmerSiveMode());
                    FileLogUtil.writeAndTime("原始长:  " + mScreenHeight);
                    FileLogUtil.writeAndTime("原始宽:  " + mScreenWidth);
                    FileLogUtil.writeAndTime("界面长:  " + dm.heightPixels);
                    FileLogUtil.writeAndTime("界面宽:  " + dm.widthPixels);
                    FileLogUtil.writeAndTime("刘海长:  " + notchHeight);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                height -= notchHeight;

                try {
                    FileLogUtil.writeAndTime("处理后height值：  " + height);
                } catch (IOException e) {
                    e.printStackTrace();
                }

                if (mScreenHeight > mScreenWidth) {
                    mScreenHeight = height;
                } else {
                    mScreenWidth = height;
                }
                try {
                    FileLogUtil.writeAndTime("转换后长:  " + mScreenHeight);
                    FileLogUtil.writeAndTime("转换后宽:  " + mScreenWidth);
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }
        }
    }

    public int getAppVersion() {
        return mSharedPreferences.getInt(PREF_VERSION, 0);
    }

    public void setAppVersion(int ver) {
        mSharedPreferences.putInt(PREF_VERSION, ver);
    }


    public PreferenceFragmentPlus.SharedPreferencesPlus getSharedPreferences() {
        return mSharedPreferences;
    }

    public float getSmallerSize() {
        return mScreenHeight < mScreenWidth ? mScreenHeight : mScreenWidth;
    }

    public float getScreenWidth() {
        return Math.min(mScreenWidth, mScreenHeight);
    }

    public boolean isDialogDelete() {
        return true;// mSharedPreferences.getBoolean(PREF_DECK_DELETE_DILAOG, PREF_DEF_DECK_DELETE_DILAOG);
    }

    public int getNotchHeight() {
        return mSharedPreferences.getInt(PREF_NOTCH_HEIGHT, DEF_PREF_NOTCH_HEIGHT);
    }

    public void setNotchHeight(int height) {
        mSharedPreferences.putInt(PREF_NOTCH_HEIGHT, height);
    }

    public int getFontSize() {
        return mSharedPreferences.getInt(PREF_FONT_SIZE, DEF_PREF_FONT_SIZE);
    }

    public boolean isOnlyGame() {
        return mSharedPreferences.getBoolean(PREF_ONLY_GAME, DEF_PREF_ONLY_GAME);
    }

    /***
     * 是否使用额外卡库
     */
    public boolean isReadExpansions() {
        return mSharedPreferences.getBoolean(PREF_READ_EX, DEF_PREF_READ_EX);
    }

    public boolean isUseDeckManagerV2() {
        return false;//mSharedPreferences.getBoolean(PREF_DECK_MANAGER_V2, DEF_PREF_DECK_MANAGER_V2);
    }

    public float getXScale() {
        return getScreenHeight() / (float) Constants.CORE_SKIN_BG_SIZE[0];
    }

    public float getYScale() {
        return getScreenWidth() / (float) Constants.CORE_SKIN_BG_SIZE[1];
    }

    public float getScreenHeight() {
        return Math.max(mScreenWidth, mScreenHeight);
    }

    /**
     * 游戏配置
     */
    public NativeInitOptions getNativeInitOptions() {
        NativeInitOptions options = new NativeInitOptions();
        options.mWorkPath = getResourcePath();
        makeCdbList(options.mDbList);
        makeZipList(options.mArchiveList);
        options.mCardQuality = getCardQuality();
        options.mIsFontAntiAliasEnabled = isFontAntiAlias();
        options.mIsPendulumScaleEnabled = isPendulumScale();
        options.mIsSoundEffectEnabled = isSoundEffect();
        options.mOpenglVersion = getOpenglVersion();
        if (Constants.DEBUG) {
            Log.i("Irrlicht", "option=" + options);
        }
        return options;
    }

    public File getDataBaseFile() {
        return new File(getDataBasePath(), Constants.DATABASE_NAME);
    }

    private void makeCdbList(List<String> pathList) {
        if (isReadExpansions()) {
            File expansionsDir = getExpansionsPath();
            if (expansionsDir.exists()) {
                File[] cdbs = expansionsDir.listFiles(file -> {
                    return file.isFile() && file.getName().toLowerCase(Locale.US).endsWith(".cdb");
                });
                if (cdbs != null) {
                    try {
                        Arrays.sort(cdbs, (file, t1) -> {
                            return file.getName().compareTo(t1.getName());
                        });
                    } catch (Exception e) {
                        //
                    }
                    for (File file : cdbs) {
                        Log.i("合法的数据库才会加载", "菜菜辛苦了");
                        //if (CardManager.checkDataBase(file)) {
                        //合法数据库才会加载
                        pathList.add(file.getAbsolutePath());
                        //}
                    }
                }
            }
        }
        pathList.add(getDataBaseFile().getAbsolutePath());
    }

    public File getExpansionsPath() {
        return new File(getResourcePath(), CORE_EXPANSIONS);
    }

    private void makeZipList(List<String> pathList) {
        pathList.add(new File(getResourcePath(), Constants.CORE_PICS_ZIP).getAbsolutePath());
        pathList.add(new File(getResourcePath(), Constants.CORE_SCRIPTS_ZIP).getAbsolutePath());
        //
        if (isReadExpansions()) {
            File expansionsDir = getExpansionsPath();
            if (expansionsDir.exists()) {
                File[] zips = expansionsDir.listFiles(new FileFilter() {
                    @Override
                    public boolean accept(File file) {
                        return file.isFile() && file.getName().toLowerCase(Locale.US).endsWith(".zip");
                    }
                });
                if (zips != null) {
                    for (File file : zips) {
                        pathList.add(file.getAbsolutePath());
                    }
                }
            }
        }
    }

    /***
     * 音效
     */
    public boolean isSoundEffect() {
        return mSharedPreferences.getBoolean(Constants.PREF_SOUND_EFFECT, Constants.PREF_DEF_SOUND_EFFECT);
    }

    /***
     * 音效
     */
    public void setSoundEffect(boolean soundEffect) {
        mSharedPreferences.putBoolean(Constants.PREF_SOUND_EFFECT, soundEffect);
    }

    /***
     * 决斗助手
     */
    public boolean isServiceDuelAssistant() {
        return mSharedPreferences.getBoolean(Constants.PREF_START_SERVICEDUELASSISTANT, Constants.PREF_DEF_START_SERVICEDUELASSISTANT);
    }

    public void setServiceDuelAssistant(boolean serviceDuelAssiatant) {
        mSharedPreferences.putBoolean(Constants.PREF_START_SERVICEDUELASSISTANT, serviceDuelAssiatant);
    }

    /***
     * 摇摆数字
     */
    public boolean isPendulumScale() {
        return mSharedPreferences.getBoolean(Constants.PREF_PENDULUM_SCALE, Constants.PREF_DEF_PENDULUM_SCALE);
    }

    /***
     * 摇摆数字
     */
    public void setPendulumScale(boolean pendulumScale) {
        mSharedPreferences.putBoolean(Constants.PREF_PENDULUM_SCALE, pendulumScale);
    }

    /***
     * opengl版本
     */
    public int getOpenglVersion() {
        try {
            return Integer.valueOf(mSharedPreferences.getString(Constants.PREF_OPENGL_VERSION, "" + Constants.PREF_DEF_OPENGL_VERSION));
        } catch (Exception e) {
            return Constants.PREF_DEF_OPENGL_VERSION;
        }
    }

    /***
     * opengl版本
     */
    public void setOpenglVersion(int openglVersion) {
        mSharedPreferences.putString(Constants.PREF_OPENGL_VERSION, "" + openglVersion);
    }

    /***
     * 字体抗锯齿
     */
    public boolean isFontAntiAlias() {
        return mSharedPreferences.getBoolean(Constants.PREF_FONT_ANTIALIAS, Constants.PREF_DEF_FONT_ANTIALIAS);
    }

    /***
     * 字体抗锯齿
     */
    public void setFontAntiAlias(boolean fontAntiAlias) {
        mSharedPreferences.putBoolean(Constants.PREF_FONT_ANTIALIAS, fontAntiAlias);
    }

    /***
     * 图片质量
     */
    public int getCardQuality() {
        try {
            return Integer.valueOf(mSharedPreferences.getString(Constants.PREF_IMAGE_QUALITY, "" + Constants.PREF_DEF_IMAGE_QUALITY));
        } catch (Exception e) {
            return Constants.PREF_DEF_IMAGE_QUALITY;
        }
    }

    /***
     * 图片质量
     */
    public void setCardQuality(int quality) {
        mSharedPreferences.putString(Constants.PREF_IMAGE_QUALITY, "" + quality);
    }

    /***
     * 图片文件夹
     */
    public String getCardImagePath() {
        return new File(getResourcePath(), Constants.CORE_IMAGE_PATH).getAbsolutePath();
    }

    /***
     * log文件夹
     */
    public String getMobileLogPath() {
        return new File(getResourcePath(), Constants.MOBILE_LOG).getAbsolutePath();
    }

    /***
     * 卡组分享图片文件夹
     */
    public String getDeckSharePath() {
        return new File(getResourcePath(), Constants.MOBILE_DECK_SHARE).getAbsolutePath();
    }

    /***
     * 当前数据库文件夹
     */
    public String getDataBasePath() {
        if (isUseExtraCards()) {
            return getResourcePath();
        } else {
            //返回游戏根目录，即ygocore文件夹
            return getResourcePath();
            // return getDataBaseDefault();
        }
    }

    public boolean isLockSreenOrientation() {
        return mSharedPreferences.getBoolean(PREF_LOCK_SCREEN, Constants.PREF_DEF_LOCK_SCREEN);
    }

    public void setLockSreenOrientation(boolean lockSreenOrientation) {
        mSharedPreferences.putBoolean(PREF_LOCK_SCREEN, lockSreenOrientation);
    }

    /***
     * 内置数据库文件夹
     */
    @SuppressLint("WrongConstant")
    public String getDataBaseDefault() {
        return context.getDir("game", Context.MODE_MULTI_PROCESS).getPath();
    }

    /***
     * 是否优先使用外置数据
     */
    public boolean isUseExtraCards() {
        return mSharedPreferences.getBoolean(Constants.PREF_USE_EXTRA_CARD_CARDS, Constants.PREF_DEF_USE_EXTRA_CARD_CARDS);
    }

    /***
     * 设置是否优先使用外置数据
     */
    public void setUseExtraCards(boolean useExtraCards) {
        mSharedPreferences.putBoolean(Constants.PREF_USE_EXTRA_CARD_CARDS, useExtraCards);
    }

    public String getCoreSkinPath() {
        return new File(getResourcePath(), Constants.CORE_SKIN_PATH).getAbsolutePath();
    }

    /***
     * 字体路径
     */
    public String getFontPath() {
        return mSharedPreferences.getString(Constants.PREF_GAME_FONT, getFontDefault());
    }

    /***
     * 字体路径
     */
    public void setFontPath(String font) {
        mSharedPreferences.putString(Constants.PREF_GAME_FONT, font);
    }

    /**
     * 默认字体
     */
    private String getFontDefault() {
        return new File(getFontDirPath(), Constants.DEFAULT_FONT_NAME).getAbsolutePath();
    }

    /***
     * 字体目录
     */
    public String getFontDirPath() {
        return new File(getResourcePath(), Constants.FONT_DIRECTORY).getAbsolutePath();
    }

    /***
     * 游戏根目录
     */
    public String getResourcePath() {
        String defPath;
        try {
            defPath = new File(Environment.getExternalStorageDirectory(), Constants.PREF_DEF_GAME_DIR).getAbsolutePath();
        } catch (Exception e) {
            defPath = new File(context.getFilesDir(), Constants.PREF_DEF_GAME_DIR).getAbsolutePath();
        }
        return mSharedPreferences.getString(Constants.PREF_GAME_PATH, defPath);
    }

    public void setResourcePath(String path) {
        if (TextUtils.equals(path, getResourcePath())) return;
        mSharedPreferences.putString(Constants.PREF_GAME_PATH, path);
    }

    //获取卡组文件夹
    public String getDeckDir() {
        return new File(getResourcePath(), Constants.CORE_DECK_PATH).getAbsolutePath();
    }

    //获取ai卡组文件夹
    public String getAiDeckDir() {
        return new File(getResourcePath(), Constants.WINDBOT_PATH + "/Decks").getAbsolutePath();
    }

    //获取新卡卡包文件夹
    public String getPackDeckDir() {
        return new File(getResourcePath(), "pack").getAbsolutePath();
    }

    //获取临时存放卡组的目录
    public String getCacheDeckDir() {
        return context.getExternalFilesDir("cacheDeck").getAbsolutePath();
    }

    //获取残局文件夹
    public String getSingleDir() {
        return new File(getResourcePath(), Constants.CORE_SINGLE_PATH).getAbsolutePath();
    }

    /**
     * 隐藏底部导航栏
     */
    public boolean isImmerSiveMode() {
        return mSharedPreferences.getBoolean(PREF_IMMERSIVE_MODE, PREF_DEF_IMMERSIVE_MODE);
    }

    public boolean isSensorRefresh() {
        return mSharedPreferences.getBoolean(PREF_SENSOR_REFRESH, PREF_DEF_SENSOR_REFRESH);
    }

    /***
     * 获得最后卡组绝对路径
     */
    public String getLastDeckPath() {
        return mSharedPreferences.getString(Constants.PREF_LAST_YDK, Constants.PREF_DEF_LAST_YDK);
    }

    /***
     * 保存最后卡组绝对路径
     */
    public void setLastDeckPath(String name) {
        if (TextUtils.equals(name, getCurLastDeck())) {
            //一样
            return;
        }
        mSharedPreferences.putString(Constants.PREF_LAST_YDK, name);
    }

    public String getCurLastDeck() {
        return mSharedPreferences.getString(Constants.PREF_LAST_YDK, null);
    }

    /***
     * 获得最后卡组分类名
     */
    public String getLastCategory() {
        return mSharedPreferences.getString(Constants.PREF_LAST_CATEGORY, Constants.PREF_DEF_LAST_CATEGORY);
    }

    /***
     * 保存最后卡组分类名
     */
    public void setLastCategory(String name) {
        if (TextUtils.equals(name, getCurLastCategory())) {
            //一样
            return;
        }
        mSharedPreferences.putString(Constants.PREF_LAST_CATEGORY, name);
    }

    public String getCurLastCategory() {
        return mSharedPreferences.getString(Constants.PREF_LAST_CATEGORY, null);
    }

    public void saveIntSettings(String key, int value) {
        mSharedPreferences.putInt(Constants.PREF_START + key, value);
    }

    public int getIntSettings(String key, int def) {
        int v = mSharedPreferences.getInt(Constants.PREF_START + key, def);
        if (v == def) {
            Log.d("kk", "default " + key + "=" + getVersionString(v));
        }
        return v;
    }
    /* public int resetGameVersion() {
   *   int version = GameConfig.getVersion();
   *    if (getIntSettings(Constants.PREF_GAME_VERSION, 0) == 0) {
   *        //用户没设置过版本号
   *        return version;
   *    }
   *    saveIntSettings(Constants.PREF_GAME_VERSION, GameConfig.getVersion());
   *    return version;
   * }
   *
   *  public int getGameVersion() {
   *     return getIntSettings(Constants.PREF_GAME_VERSION, GameConfig.getVersion());
   *}
   *
   * public void setGameVersion(int v) {
   *    saveIntSettings(Constants.PREF_GAME_VERSION, v);
    }*/

    public String getVersionString(int value) {
        int last = (value & 0xf);
        int m = ((value >> 4) & 0xff);
        int b = ((value >> 12) & 0xff);
        return String.format("%X.%03X.%X", b, m, last);
    }

   /* public int getVersionValue(String str) {
        str = str.trim().toLowerCase(Locale.US);
        int v = -1;
        if(str.contains(".")){
            String[] vas = str.split("\\.");
            if(vas.length<3){
                return -1;
            }
            try {
                int last = Integer.parseInt(vas[2]);
                int m = Integer.parseInt(vas[1])<<4;
                int b = Integer.parseInt(vas[0])<<12;
                v = last+m+b;
            }catch (Exception e){

            }
        }else{
            try {
                if (str.startsWith("0x")) {
                    str = str.substring(2);
                }
                v = Integer.parseInt(str, 16);
            } catch (Exception e) {
            }
        }
        return v;
    }*/

    public void saveSettings(String key, String value) {
        if ("lastdeck".equals(key)) {
            setLastDeckPath(value);
        } else {
            mSharedPreferences.putString(Constants.PREF_START + key, value);
        }
    }

    public String getSettings(String key) {
        if ("lastdeck".equals(key)) {
            String val = getLastDeckPath();
            return val;
        }
        return mSharedPreferences.getString(Constants.PREF_START + key, null);
    }

    public void saveCategorySettings(String key, String value) {
        if ("lastcategory".equals(key)) {
            setLastCategory(value);
        } else {
            mSharedPreferences.putString(Constants.PREF_START + key, value);
        }
    }

    public String getCategorySettings(String key) {
        if ("lastcategory".equals(key)) {
            String val = getLastCategory();
            return val;
        }
        return mSharedPreferences.getString(Constants.PREF_START + key, null);
    }

    public List<String> getLastRoomList() {
        List<String> names = new ArrayList<>();
        String json = mSharedPreferences.getString(Constants.PREF_LAST_ROOM_LIST, null);
        if (!TextUtils.isEmpty(json)) {
            try {
                JSONArray array = new JSONArray(json);
                int count = array.length();
                for (int i = 0; i < count; i++) {
                    names.add(array.optString(i));
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
//        Log.i("kk", "read:" + names);
        return names;
    }

    public void setLastRoomList(List<String> _names) {
        JSONArray array = new JSONArray();
        if (_names != null) {
            int count = _names.size();
            int max = Math.min(count, Constants.LAST_ROOM_MAX);
            for (int i = 0; i < max; i++) {
                array.put(_names.get(i));
            }
        }
//        Log.i("kk", "saveTemp:" + array);
        mSharedPreferences.putString(Constants.PREF_LAST_ROOM_LIST, array.toString());
    }
}
