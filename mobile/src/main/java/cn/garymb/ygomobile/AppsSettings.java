package cn.garymb.ygomobile;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.text.TextUtils;
import android.util.Log;

import org.json.JSONArray;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.utils.DeckUtil;
import cn.garymb.ygomobile.utils.IOUtils;

import static cn.garymb.ygomobile.Constants.CORE_DECK_PATH;
import static cn.garymb.ygomobile.Constants.CORE_EXPANSIONS;
import static cn.garymb.ygomobile.Constants.CORE_PACK_PATH;
import static cn.garymb.ygomobile.Constants.CORE_SYSTEM_PATH;
import static cn.garymb.ygomobile.Constants.DEF_PREF_FONT_SIZE;
import static cn.garymb.ygomobile.Constants.DEF_PREF_KEEP_SCALE;
import static cn.garymb.ygomobile.Constants.DEF_PREF_NOTCH_HEIGHT;
import static cn.garymb.ygomobile.Constants.DEF_PREF_ONLY_GAME;
import static cn.garymb.ygomobile.Constants.DEF_PREF_READ_EX;
import static cn.garymb.ygomobile.Constants.PREF_DEF_IMMERSIVE_MODE;
import static cn.garymb.ygomobile.Constants.PREF_DEF_SENSOR_REFRESH;
import static cn.garymb.ygomobile.Constants.PREF_FONT_SIZE;
import static cn.garymb.ygomobile.Constants.PREF_IMMERSIVE_MODE;
import static cn.garymb.ygomobile.Constants.PREF_KEEP_SCALE;
import static cn.garymb.ygomobile.Constants.PREF_LOCK_SCREEN;
import static cn.garymb.ygomobile.Constants.PREF_NOTCH_HEIGHT;
import static cn.garymb.ygomobile.Constants.PREF_ONLY_GAME;
import static cn.garymb.ygomobile.Constants.PREF_READ_EX;
import static cn.garymb.ygomobile.Constants.PREF_SENSOR_REFRESH;
import static cn.garymb.ygomobile.Constants.WINDBOT_DECK_PATH;
import static cn.garymb.ygomobile.Constants.WINDBOT_PATH;
import static cn.garymb.ygomobile.Constants.YDK_FILE_EX;

/**
 * 单进程
 */
public class AppsSettings {
    private static final String PREF_VERSION = "app_version";
    private static AppsSettings sAppsSettings;
    private Context context;
    private SharedPreferences mSharedPreferences;

    private AppsSettings(Context context) {
        this.context = context;
        String name = context.getPackageName() + ".settings";
        mSharedPreferences = context.getSharedPreferences(name, Context.MODE_PRIVATE | Context.MODE_MULTI_PROCESS);
        Log.e("YGOMobileLog", "初始化类地址:  " + System.identityHashCode(this));
        //
        LocalConfig.getInstance(context).updateFromOld(mSharedPreferences);
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

    public int getAppVersion() {
        return mSharedPreferences.getInt(PREF_VERSION, 0);
    }

    public void setAppVersion(int ver) {
        mSharedPreferences.edit().putInt(PREF_VERSION, ver).apply();
    }

    public SharedPreferences getSharedPreferences() {
        return mSharedPreferences;
    }

    public boolean isDialogDelete() {
        return true;// mSharedPreferences.getBoolean(PREF_DECK_DELETE_DILAOG, PREF_DEF_DECK_DELETE_DILAOG);
    }

    public int getNotchHeight() {
        return mSharedPreferences.getInt(PREF_NOTCH_HEIGHT, DEF_PREF_NOTCH_HEIGHT);
    }

    public void setNotchHeight(int height) {
        mSharedPreferences.edit().putInt(PREF_NOTCH_HEIGHT, height).apply();
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

    public boolean isKeepScale() {
        return mSharedPreferences.getBoolean(PREF_KEEP_SCALE, DEF_PREF_KEEP_SCALE);
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
        options.mFontFile = getFontPath();
        options.mResDir = getResourcePath();
        options.mImageDir = getCardImagePath();
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
        mSharedPreferences.edit().putBoolean(Constants.PREF_SOUND_EFFECT, soundEffect).apply();
    }

    /***
     * 决斗助手
     */
    public boolean isServiceDuelAssistant() {
        return mSharedPreferences.getBoolean(Constants.PREF_START_SERVICEDUELASSISTANT, Constants.PREF_DEF_START_SERVICEDUELASSISTANT);
    }

    public void setServiceDuelAssistant(boolean serviceDuelAssiatant) {
        mSharedPreferences.edit().putBoolean(Constants.PREF_START_SERVICEDUELASSISTANT, serviceDuelAssiatant).apply();
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
        mSharedPreferences.edit().putBoolean(Constants.PREF_PENDULUM_SCALE, pendulumScale).apply();
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
        mSharedPreferences.edit().putString(Constants.PREF_OPENGL_VERSION, "" + openglVersion).apply();
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
        mSharedPreferences.edit().putBoolean(Constants.PREF_FONT_ANTIALIAS, fontAntiAlias).apply();
    }

    /***
     * 图片质量
     */
    public int getCardQuality() {
        try {
            String val = mSharedPreferences.getString(Constants.PREF_IMAGE_QUALITY, "" + Constants.PREF_DEF_IMAGE_QUALITY);
            if(val == null){
                return Constants.PREF_DEF_IMAGE_QUALITY;
            }
            return Integer.valueOf(val);
        } catch (Exception e) {
            return Constants.PREF_DEF_IMAGE_QUALITY;
        }
    }

    /***
     * 图片质量
     */
    public void setCardQuality(int quality) {
        mSharedPreferences.edit().putString(Constants.PREF_IMAGE_QUALITY, "" + quality).apply();
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

    public boolean isLockScreenOrientation() {
        return mSharedPreferences.getBoolean(PREF_LOCK_SCREEN, Constants.PREF_DEF_LOCK_SCREEN);
    }

    public void setLockScreenOrientation(boolean lockScreenOrientation) {
        mSharedPreferences.edit().putBoolean(PREF_LOCK_SCREEN, lockScreenOrientation).apply();
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
        mSharedPreferences.edit().putBoolean(Constants.PREF_USE_EXTRA_CARD_CARDS, useExtraCards).apply();
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
        mSharedPreferences.edit().putString(Constants.PREF_GAME_FONT, font).apply();
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
        mSharedPreferences.edit().putString(Constants.PREF_GAME_PATH, path).apply();
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

    public boolean isHideHwNotouch() {
        return mSharedPreferences.getBoolean(Constants.PREF_HW_HIDE_HOTTOUCH, Constants.PREF_DEF_PREF_HW_HIDE_HOTTOUCH);
    }

    //获得最后卡组绝对路径
    public String getLastDeckPath() {
        String path;
        if (TextUtils.equals(context.getString(R.string.category_pack), getLastCategory())) {
            path = getResourcePath() + "/" + CORE_PACK_PATH + "/" + getLastDeckName() + YDK_FILE_EX;
        } else if (TextUtils.equals(context.getString(R.string.category_windbot_deck), getLastCategory())) {
            path = getResourcePath() + "/" + WINDBOT_PATH + "/" + WINDBOT_DECK_PATH + "/" + getLastDeckName() + YDK_FILE_EX;
        } else if (TextUtils.equals(context.getString(R.string.category_Uncategorized), getLastCategory())) {
            path = getResourcePath() + "/" + CORE_DECK_PATH + "/" + getLastDeckName() + YDK_FILE_EX;
        } else {
            path = getResourcePath() + "/" + CORE_DECK_PATH + "/" + getLastCategory() + "/" + getLastDeckName() + YDK_FILE_EX;
        }
        Log.e("Appsettings", "拼接最后路径" + path);
        return path;
    }

    //保存最后卡组绝对路径、分类、卡组名
    public void setLastDeckPath(String path) {
        Log.e("Appsettings", "设置最后路径" + path);
        if (TextUtils.equals(path, getLastDeckPath())) {
            //一样
            return;
        }
        //保存最后分类名
        LocalConfig.getInstance(context).setLastCategory(DeckUtil.getDeckTypeName(path));
        //保存最后卡组名
        File lastDeck = new File(path);
        String lastDeckName = IOUtils.tirmName(lastDeck.getName(), YDK_FILE_EX);
        LocalConfig.getInstance(context).setLastDeck(lastDeckName);
    }

    //获得最后分类名
    public String getLastCategory() {
        return LocalConfig.getInstance(context).getLastCategory();
    }

    //获得最后卡组名
    public String getLastDeckName() {
        return LocalConfig.getInstance(context).getLastDeck();
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
        mSharedPreferences.edit().putString(Constants.PREF_LAST_ROOM_LIST, array.toString()).apply();
    }
}
