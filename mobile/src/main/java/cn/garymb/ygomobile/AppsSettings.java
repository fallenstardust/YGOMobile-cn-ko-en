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
public class AppsSettings extends AppsSettingsBase {

    private AppsSettings(Context context) {
        super(context);
    }

    public static void init(Context context) {
        if (sAppsSettings == null) {
            sAppsSettings = new AppsSettings(context);
        }
    }

    public static AppsSettings get() {
        return sAppsSettings;
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
            path = getResourcePath() + "/" + CORE_PACK_PATH + "/" + getLastDeckName() + YDK_FILE_EX;
        } else if (TextUtils.equals(context.getString(R.string.category_windbot_deck), getLastCategory())) {
            path = getResourcePath() + "/" + WINDBOT_PATH + "/" + WINDBOT_DECK_PATH + "/" + getLastDeckName() + YDK_FILE_EX;
        } else if (TextUtils.equals(context.getString(R.string.category_Uncategorized), getLastCategory())) {
            path = getResourcePath() + "/" + CORE_DECK_PATH + "/" + getLastDeckName() + YDK_FILE_EX;
        } else {
            path = getResourcePath() + "/" + CORE_DECK_PATH + "/" + getLastCategory() + "/" + getLastDeckName() + YDK_FILE_EX;
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

    protected void replaceLineFeed() {
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
