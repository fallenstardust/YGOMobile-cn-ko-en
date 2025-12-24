package cn.garymb.ygomobile;

import static cn.garymb.ygomobile.Constants.ASSETS_EN;
import static cn.garymb.ygomobile.Constants.ASSETS_ES;
import static cn.garymb.ygomobile.Constants.ASSETS_JP;
import static cn.garymb.ygomobile.Constants.ASSETS_KOR;
import static cn.garymb.ygomobile.Constants.ASSETS_PT;
import static cn.garymb.ygomobile.Constants.BOT_CONF;
import static cn.garymb.ygomobile.Constants.CORE_BOT_CONF_PATH;
import static cn.garymb.ygomobile.Constants.CORE_DECK_PATH;
import static cn.garymb.ygomobile.Constants.CORE_EXPANSIONS;
import static cn.garymb.ygomobile.Constants.CORE_PACK_PATH;
import static cn.garymb.ygomobile.Constants.CORE_REPLAY_PATH;
import static cn.garymb.ygomobile.Constants.CORE_STRING_PATH;
import static cn.garymb.ygomobile.Constants.CORE_SYSTEM_PATH;
import static cn.garymb.ygomobile.Constants.DATABASE_NAME;
import static cn.garymb.ygomobile.Constants.DEF_PREF_FONT_SIZE;
import static cn.garymb.ygomobile.Constants.DEF_PREF_KEEP_SCALE;
import static cn.garymb.ygomobile.Constants.DEF_PREF_NOTCH_HEIGHT;
import static cn.garymb.ygomobile.Constants.DEF_PREF_ONLY_GAME;
import static cn.garymb.ygomobile.Constants.DEF_PREF_READ_EX;
import static cn.garymb.ygomobile.Constants.PREF_DEF_DATA_LANGUAGE;
import static cn.garymb.ygomobile.Constants.PREF_DEF_IMMERSIVE_MODE;
import static cn.garymb.ygomobile.Constants.PREF_DEF_KEY_WORDS_SPLIT;
import static cn.garymb.ygomobile.Constants.PREF_DEF_SENSOR_REFRESH;
import static cn.garymb.ygomobile.Constants.PREF_FONT_SIZE;
import static cn.garymb.ygomobile.Constants.PREF_IMMERSIVE_MODE;
import static cn.garymb.ygomobile.Constants.PREF_KEEP_SCALE;
import static cn.garymb.ygomobile.Constants.PREF_LOCK_SCREEN;
import static cn.garymb.ygomobile.Constants.PREF_NOTCH_HEIGHT;
import static cn.garymb.ygomobile.Constants.PREF_ONLY_GAME;
import static cn.garymb.ygomobile.Constants.PREF_READ_EX;
import static cn.garymb.ygomobile.Constants.PREF_SENSOR_REFRESH;
import static cn.garymb.ygomobile.Constants.PREF_WINDOW_TOP_BOTTOM;
import static cn.garymb.ygomobile.Constants.WINDBOT_DECK_PATH;
import static cn.garymb.ygomobile.Constants.WINDBOT_PATH;
import static cn.garymb.ygomobile.Constants.YDK_FILE_EX;
import static cn.garymb.ygomobile.ui.home.ResCheckTask.getDatapath;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Point;
import android.text.TextUtils;
import android.util.Log;
import android.view.WindowManager;
import android.widget.ImageView;

import androidx.annotation.Nullable;

import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.signature.MediaStoreSignature;

import org.json.JSONArray;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import cn.garymb.ygomobile.core.IrrlichtBridge;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.settings.SharedPreferencesPlus;
import cn.garymb.ygomobile.utils.DeckUtil;
import cn.garymb.ygomobile.utils.FileUtils;
import cn.garymb.ygomobile.utils.IOUtils;
import cn.garymb.ygomobile.utils.YGOUtil;
import cn.garymb.ygomobile.utils.glide.GlideCompat;

/**
 * 静态类
 */
public class AppsSettings {
    private static final String TAG = "AppsSettings";
    private static final String PREF_VERSION = "app_version";
    private static AppsSettings sAppsSettings;
    private final Point mScreenSize = new Point();
    private final Point mRealScreenSize = new Point();
    private final Context context;
    private final SharedPreferencesPlus mSharedPreferences;
    private float mDensity;

    private AppsSettings(Context context) {
        this.context = context;
        mSharedPreferences = SharedPreferencesPlus.create(context, context.getPackageName() + ".settings");
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
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        //应用尺寸
        wm.getDefaultDisplay().getSize(mScreenSize);
        //屏幕尺寸
        wm.getDefaultDisplay().getRealSize(mRealScreenSize);
        mDensity = context.getResources().getDisplayMetrics().density;
    }

    public int getAppVersion() {
        return mSharedPreferences.getInt(PREF_VERSION, 0);
    }

    public void setAppVersion(int ver) {
        mSharedPreferences.putInt(PREF_VERSION, ver);
    }

    public SharedPreferencesPlus getSharedPreferences() {
        return mSharedPreferences;
    }

    public float getSmallerSize() {
        float w = getScreenWidth();
        float h = getScreenHeight();
        return h < w ? h : w;
    }

    public boolean isDialogDelete() {
        return false;// mSharedPreferences.getBoolean(PREF_DECK_DELETE_DILAOG, PREF_DEF_DECK_DELETE_DILAOG);
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

    public float getXScale(int w, int h) {
        //曲面屏
        if (isKeepScale()) {
            float sx = getScreenHeight() / w;
            float sy = getScreenWidth() / h;
            return Math.min(sx, sy);
        }
        return getScreenHeight() / w;
    }

    public float getYScale(int w, int h) {
        if (isKeepScale()) {
            //固定比例，取最小值
            float sx = getScreenHeight() / w;
            float sy = getScreenWidth() / h;
            return Math.min(sx, sy);
        }
        return getScreenWidth() / h;
    }

    public boolean isKeepScale() {
        return mSharedPreferences.getBoolean(PREF_KEEP_SCALE, DEF_PREF_KEEP_SCALE);
    }

    public int getScreenPadding() {
        //ListPreference都是string
        String str = mSharedPreferences.getString(PREF_WINDOW_TOP_BOTTOM, null);
        if (!TextUtils.isEmpty(str) && TextUtils.isDigitsOnly(str)) {
            return Integer.parseInt(str);
        }
        return 0;
    }

    public float getScreenWidth() {
        int w, h;
        if (isImmerSiveMode()) {
            w = mRealScreenSize.x;
            h = mRealScreenSize.y;
        } else {
            w = mScreenSize.x;
            h = mScreenSize.y;
        }
        int ret = Math.min(w, h);
        //测试代码，曲面屏左右2变需要留空白，但是游戏画面比例不对，需要修改c那边代码
        int fix_h = YGOUtil.dp2px(getScreenPadding());
        Log.d(IrrlichtBridge.TAG, "screen padding=" + fix_h);
        return ret - fix_h * 2;
    }

    public float getScreenHeight() {
        int w, h;
        if (isImmerSiveMode()) {
            w = mRealScreenSize.x;
            h = mRealScreenSize.y;
        } else {
            w = mScreenSize.x;
            h = mScreenSize.y;
        }
        int ret = Math.max(w, h);
        if (isImmerSiveMode()) {
            //刘海高度
            ret -= getNotchHeight();
        }
        return ret;
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
        options.mOpenglVersion = getOpenglVersion();
        if (Constants.DEBUG) {
            Log.i("Irrlicht", "option=" + options);
        }
        return options;
    }

    public File getDatabaseFile() {
        return new File(getDataBasePath(), Constants.DATABASE_NAME);
    }


    public File[] getExpansionFiles() {
        return new File(getResourcePath(), Constants.CORE_EXPANSIONS)
                .listFiles((file) -> {
                    if (!file.isFile()) {
                        return false;
                    }
                    String s_name = file.getName().toLowerCase();
                    return s_name.endsWith(".zip") || s_name.endsWith(Constants.YPK_FILE_EX);
                });
    }

    private void makeCdbList(List<String> pathList) {
        if (isReadExpansions()) {
            File expansionsDir = getExpansionsPath();
            if (expansionsDir.exists()) {
                File[] cdbs = expansionsDir.listFiles(file -> file.isFile() && file.getName().toLowerCase(Locale.US).endsWith(".cdb"));
                if (cdbs != null) {
                    try {
                        Arrays.sort(cdbs, (file, t1) -> {
                            return file.getName().compareTo(t1.getName());
                        });
                    } catch (Exception e) {
                        //
                    }
                    for (File file : cdbs) {
                        //if (CardManager.checkDataBase(file)) {
                        //合法数据库才会加载
                        pathList.add(file.getAbsolutePath());
                        //}
                    }
                }
            }
        }
        pathList.add(getDatabaseFile().getAbsolutePath());
    }

    /**
     * 返回扩展卡路径，在app-specific external storage下
     *
     * @return 扩展卡路径
     */
    public File getExpansionsPath() {
        return new File(getResourcePath(), CORE_EXPANSIONS);
    }

    private void makeZipList(List<String> pathList) {
        if (isReadExpansions()) {
            File expansionsDir = getExpansionsPath();
            if (expansionsDir.exists()) {
                File[] files = getExpansionFiles();
                if (files != null) {
                    for (File file : files) {
                        pathList.add(file.getAbsolutePath());
                    }
                }
            }
        }
        pathList.add(new File(getResourcePath(), Constants.CORE_PICS_ZIP).getAbsolutePath());
        pathList.add(new File(getResourcePath(), Constants.CORE_SCRIPTS_ZIP).getAbsolutePath());
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
     * 关键字分隔方法
     */
    public void setKeyWordsSplit(int split) {
        mSharedPreferences.putString(Constants.PREF_KEY_WORDS_SPLIT, "" + split);
    }

    /***
     * 关键字分隔方法
     */
    public int getKeyWordsSplit() {
        try {
            return Integer.valueOf(mSharedPreferences.getString(Constants.PREF_KEY_WORDS_SPLIT, "" + PREF_DEF_KEY_WORDS_SPLIT));
        } catch (Exception e) {
            return PREF_DEF_KEY_WORDS_SPLIT;
        }
    }

    /***
     * 资料语言
     */
    public void setDataLanguage(int language) {
        mSharedPreferences.putString(Constants.PREF_DATA_LANGUAGE, "" + language);
    }

    /***
     * 资料语言
     */
    public int getDataLanguage() {
        try {
            return Integer.valueOf(mSharedPreferences.getString(Constants.PREF_DATA_LANGUAGE, "" + PREF_DEF_DATA_LANGUAGE));
        } catch (Exception e) {
            return PREF_DEF_DATA_LANGUAGE;
        }
    }

    /**
     * 根据卡密获取卡图的路径
     *
     * @param code 卡密
     * @return
     */
    public String getCardImagePath(int code) {
        return new File(getCardImagePath(), code + ".jpg").getAbsolutePath();
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

    public String getSoundPath() {
        return new File(getResourcePath(), Constants.CORE_SOUND_PATH).getAbsolutePath();
    }

    public String getCoreSkinPath() {
        return new File(getResourcePath(), Constants.CORE_SKIN_PATH).getAbsolutePath();
    }

    public String getAvatarPath() {
        return new File(getResourcePath(), Constants.CORE_AVATAR_PATH).getAbsolutePath();
    }

    public String getCoverPath() {
        return new File(getResourcePath(), Constants.CORE_COVER_PATH).getAbsolutePath();
    }

    public String getBgPath() {
        return new File(getResourcePath(), Constants.CORE_BG_PATH).getAbsolutePath();
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
     * 返回存储游戏资源的根目录，为app-specific external storage
     * 优先返回sharedPreference中存储的设置值，该值为空时返回context.getExternalFilesDir()
     */
    public String getResourcePath() {
        String defPath;
        /* 注意，调用的函数context.getExternalFilesDir()获取的是外部存储目录，只是安卓系统会将一部分内部存储模拟
        外部存储，此时返回的/storage/emulated/0其实是指向内部存储的一部分的链接，但在语义上它是external storage。
         context.getExternalFilesDir()的部分注释：If a shared storage device is emulated (as determined
          by Environment.isExternalStorageEmulated(File)),
          it's contents are backed by a private user data partition, which means there is little benefit
           to storing data here instead of the private directories returned by getFilesDir(), etc.
           可以用Environment.isExternalStorageEmulated()验证，nova10实测返回值为true
        To put it simply, the Android storage/emulated/0 folder is the full name of the root
        directory that you access all your files from in the file explorer on your Android device.
        However, as its name suggets, this folder is emulated storage, which means that it is merely
         a link to the actual internal storage of your device's operating system. This is done for security reasons.
         */

        defPath = new File(String.valueOf(context.getExternalFilesDir(Constants.PREF_DEF_GAME_DIR))).getAbsolutePath();
        return mSharedPreferences.getString(Constants.PREF_GAME_PATH, defPath);
    }

    public void setResourcePath(String path) {
        if (TextUtils.equals(path, getResourcePath())) return;
        mSharedPreferences.putString(Constants.PREF_GAME_PATH, path);
    }

    /**
     * @return 录像文件夹
     */
    public String getReplayDir() {
        return new File(getResourcePath(), CORE_REPLAY_PATH).getAbsolutePath();
    }

    //获取卡组文件夹
    public String getDeckDir() {
        return new File(getResourcePath(), CORE_DECK_PATH).getAbsolutePath();
    }

    //获取ai卡组文件夹
    public String getAiDeckDir() {
        return new File(getResourcePath(), WINDBOT_PATH + "/Decks").getAbsolutePath();
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

    /**
     * 保存最后禁卡表名
     *
     */
    public void setLastLimit(String limitName) {
        App.get().saveSetting("lastlimit", limitName);
        mSharedPreferences.putString(Constants.PREF_LAST_LIMIT, limitName);
    }

    /**
     * 获取最后选择的限制条件
     *
     * @return 返回存储的限制条件字符串，如果未找到则返回默认限制条件
     */
    public String getLastLimit() {
        String limitName = App.get().getSetting("lastlimit");
        return limitName == null || TextUtils.isEmpty(limitName) ?
                mSharedPreferences.getString(Constants.PREF_LAST_LIMIT, Constants.PREF_DEF_LAST_LIMIT) : limitName;
    }

    public void setLastGenesysLimit(String limitName) {
        App.get().saveSetting("lastGenesysLimit", limitName);
        mSharedPreferences.putString(Constants.PREF_LAST_GENESYS_LIMIT, limitName);
    }

    public String getLastGenesysLimit() {
        String limitName = App.get().getSetting("lastGenesysLimit");
        return limitName == null || TextUtils.isEmpty(limitName) ?
                mSharedPreferences.getString(Constants.PREF_LAST_GENESYS_LIMIT, Constants.PREF_DEF_LAST_GENESYS_LIMIT) : limitName;
    }
    public void setGenesysMode(int value) {
        App.get().saveIntSetting("lastGenesysMode", value);
        mSharedPreferences.putInt(Constants.PREF_LAST_GENESYS_MODE, value);
    }
    public int getGenesysMode () {
        return mSharedPreferences.getInt(Constants.PREF_LAST_GENESYS_MODE, Constants.PREF_DEF_LAST_GENESYS_MODE);
    }

    /**
     * 获得（最后）上次打开的卡组的绝对路径
     * setCurDeck()方法负责设置上次打开的卡组的路径
     *
     */
    public @Nullable
    String getLastDeckPath() {
        String path;
        if (TextUtils.equals(context.getString(R.string.category_pack), getLastCategory())) {
            path = Paths.get(getResourcePath(), CORE_PACK_PATH, getLastDeckName() + YDK_FILE_EX).toString();
        } else if (TextUtils.equals(context.getString(R.string.category_windbot_deck), getLastCategory())) {
            path = Paths.get(getResourcePath(), WINDBOT_PATH, WINDBOT_DECK_PATH, getLastDeckName() + YDK_FILE_EX).toString();
        } else if (TextUtils.equals(context.getString(R.string.category_Uncategorized), getLastCategory())) {
            path = Paths.get(getResourcePath(), CORE_DECK_PATH, getLastDeckName() + YDK_FILE_EX).toString();
        } else {
            path = Paths.get(getResourcePath(), CORE_DECK_PATH, getLastCategory(), getLastDeckName() + YDK_FILE_EX).toString();
        }
        Log.e(TAG, "拼接最后路径" + path);
        return path;
    }

    //保存最后卡组绝对路径、分类、卡组名
    public void setLastDeckPath(String path) {
        Log.e(TAG, "设置最后路径" + path);
        if (TextUtils.equals(path, getLastDeckPath())) {
            //一样
            return;
        }
        //保存最后分类名
        mSharedPreferences.putString(Constants.PREF_LAST_CATEGORY, DeckUtil.getDeckTypeName(path));
        //保存最后卡组名
        File lastDeck = new File(path);
        String lastDeckName = IOUtils.tirmName(lastDeck.getName(), YDK_FILE_EX);
        mSharedPreferences.putString(Constants.PREF_LAST_YDK, lastDeckName);
    }

    //获得最后分类名
    public String getLastCategory() {
        return mSharedPreferences.getString(Constants.PREF_LAST_CATEGORY, Constants.PREF_DEF_LAST_CATEGORY);
    }

    //获得最后卡组名
    public String getLastDeckName() {
        return mSharedPreferences.getString(Constants.PREF_LAST_YDK, Constants.PREF_DEF_LAST_YDK);
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
        if ("lastdeck".equals(key)) mSharedPreferences.putString(Constants.PREF_LAST_YDK, value);
        if ("lastcategory".equals(key))
            mSharedPreferences.putString(Constants.PREF_LAST_CATEGORY, value);
        if ("lastLimit".equals(key)) setLastLimit(value);
        mSharedPreferences.putString(Constants.PREF_START + key, value);
    }

    public String getSettings(String key) {
        if ("lastdeck".equals(key)) return getLastDeckName();
        if ("lastcategory".equals(key)) return getLastCategory();
        if ("lastLimit".equals(key)) return getLastLimit();
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

    public void setImage(String outFile, int outWidth, int outHeight, ImageView imageView) {
        File img = new File(outFile);
        if (img.exists()) {
            GlideCompat.with(context).load(img)
                    .signature(new MediaStoreSignature("image/*", img.lastModified(), 0))
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .override(outWidth, outHeight)
                    .into(imageView);
        }
    }

    public enum keyWordsSplitEnum {
        Percent(0, "%%"),
        Space(1, "Space");

        public Integer code;
        public String name;

        keyWordsSplitEnum(Integer code, String name) {
            this.code = code;
            this.name = name;
        }
    }

    public enum languageEnum {
        //todo 逐步将设置语言的代码都更改为languageEnum
        Chinese(0, "zh"),
        Korean(1, "ko"),
        English(2, "en"),
        Spanish(3, "es"),
        Japanese(4, "jp"),
        Portuguese(5, "pt");

        public Integer code;
        public String name;

        languageEnum(Integer code, String name) {
            this.code = code;
            this.name = name;
        }
    }

    public void copyCnData() throws IOException {
        //复制数据库
        copyCdbFile(getDatapath(DATABASE_NAME));
        //复制游戏配置文件
        IOUtils.copyFilesFromAssets(context, getDatapath("conf") + "/" + CORE_STRING_PATH, getResourcePath(), true);
        IOUtils.copyFilesFromAssets(context, getDatapath("conf") + "/" + BOT_CONF, getResourcePath(), true);
        //替换换行符
        String stringConfPath = new File(getResourcePath(), CORE_STRING_PATH).getAbsolutePath();
        String botConfPath = new File(getResourcePath(), BOT_CONF).getAbsolutePath();
        fixString(stringConfPath);
        fixString(botConfPath);
        //设置语言为0=中文
        setDataLanguage(languageEnum.Chinese.code);
    }

    public void copyKorData() throws IOException {
        String korStringConf = ASSETS_KOR + getDatapath("conf") + "/" + CORE_STRING_PATH;
        String korBotConf = ASSETS_KOR + getDatapath("conf") + "/" + CORE_BOT_CONF_PATH;
        String korCdb = ASSETS_KOR + getDatapath(DATABASE_NAME);
        //复制数据库
        copyCdbFile(korCdb);
        //复制游戏配置文件
        IOUtils.copyFilesFromAssets(context, korStringConf, getResourcePath(), true);
        IOUtils.copyFilesFromAssets(context, korBotConf, getResourcePath(), true);
        //替换换行符
        replaceLineFeed();
        //设置语言为1=Korean
        setDataLanguage(languageEnum.Korean.code);
    }

    public void copyEnData() throws IOException {
        String enStringConf = ASSETS_EN + getDatapath("conf") + "/" + CORE_STRING_PATH;
        String enBotConf = ASSETS_EN + getDatapath("conf") + "/" + CORE_BOT_CONF_PATH;
        String enCdb = ASSETS_EN + getDatapath(DATABASE_NAME);
        //复制数据库
        copyCdbFile(enCdb);
        //复制人机资源
        IOUtils.copyFilesFromAssets(context, getDatapath(Constants.WINDBOT_PATH), getResourcePath(), true);
        //复制游戏配置文件
        IOUtils.copyFilesFromAssets(context, enStringConf, getResourcePath(), true);
        IOUtils.copyFilesFromAssets(context, enBotConf, getResourcePath(), true);
        replaceLineFeed();
        //设置语言为2=English
        setDataLanguage(languageEnum.English.code);
    }

    public void copyEsData() throws IOException {
        String esStringConf = ASSETS_ES + getDatapath("conf") + "/" + CORE_STRING_PATH;
        String esBotConf = ASSETS_ES + getDatapath("conf") + "/" + CORE_BOT_CONF_PATH;
        String esCdb = ASSETS_ES + getDatapath(DATABASE_NAME);
        //复制数据库
        copyCdbFile(esCdb);
        //复制人机资源
        IOUtils.copyFilesFromAssets(context, getDatapath(Constants.WINDBOT_PATH), getResourcePath(), true);
        //复制游戏配置文件
        IOUtils.copyFilesFromAssets(context, esStringConf, getResourcePath(), true);
        IOUtils.copyFilesFromAssets(context, esBotConf, getResourcePath(), true);
        //替换换行符
        replaceLineFeed();
        //设置语言为3=Spanish
        setDataLanguage(languageEnum.Spanish.code);
    }

    public void copyJpData() throws IOException {
        String jpStringConf = ASSETS_JP + getDatapath("conf") + "/" + CORE_STRING_PATH;
        String jpBotConf = ASSETS_JP + getDatapath("conf") + "/" + CORE_BOT_CONF_PATH;
        String jpCdb = ASSETS_JP + getDatapath(DATABASE_NAME);
        //复制数据库
        copyCdbFile(jpCdb);
        //复制人机资源
        IOUtils.copyFilesFromAssets(context, getDatapath(Constants.WINDBOT_PATH), getResourcePath(), true);
        //复制游戏配置文件
        IOUtils.copyFilesFromAssets(context, jpStringConf, getResourcePath(), true);
        IOUtils.copyFilesFromAssets(context, jpBotConf, getResourcePath(), true);
        //替换换行符
        replaceLineFeed();
        //设置语言为4=Japanese
        setDataLanguage(languageEnum.Japanese.code);
    }

    public void copyPtData() throws IOException {
        String ptStringConf = ASSETS_PT + getDatapath("conf") + "/" + CORE_STRING_PATH;
        String ptBotConf = ASSETS_PT + getDatapath("conf") + "/" + CORE_BOT_CONF_PATH;
        String ptCdb = ASSETS_PT + getDatapath(DATABASE_NAME);
        //复制数据库
        copyCdbFile(ptCdb);
        //复制人机资源
        IOUtils.copyFilesFromAssets(context, getDatapath(Constants.WINDBOT_PATH), getResourcePath(), true);
        //复制游戏配置文件
        IOUtils.copyFilesFromAssets(context, ptStringConf, getResourcePath(), true);
        IOUtils.copyFilesFromAssets(context, ptBotConf, getResourcePath(), true);
        //替换换行符
        replaceLineFeed();
        //设置语言为5=Portuguese
        setDataLanguage(languageEnum.Portuguese.code);
    }

    private void replaceLineFeed() {
        //替换换行符
        String stringConfPath = new File(getResourcePath(), CORE_STRING_PATH).getAbsolutePath();
        String botConfPath = new File(getResourcePath(), BOT_CONF).getAbsolutePath();
        fixString(stringConfPath);
        fixString(botConfPath);
    }

    public void fixString(String stringPath) {
        List<String> lines = FileUtils.readLines(stringPath, Constants.DEF_ENCODING);
        FileUtils.writeLines(stringPath, lines, Constants.DEF_ENCODING, "\n");
    }

    public void copyCdbFile(String cdbPath) throws IOException {
        File dbFile = new File(getDataBasePath(), DATABASE_NAME);
        if (dbFile.exists()) dbFile.delete();//如果数据库存在先删除
        IOUtils.copyFilesFromAssets(context, cdbPath, getDataBasePath(), true);
    }
}
