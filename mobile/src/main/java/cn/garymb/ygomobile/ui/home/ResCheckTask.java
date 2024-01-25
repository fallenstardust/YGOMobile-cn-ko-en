package cn.garymb.ygomobile.ui.home;

import static cn.garymb.ygomobile.Constants.ASSETS_EN;
import static cn.garymb.ygomobile.Constants.ASSETS_ES;
import static cn.garymb.ygomobile.Constants.ASSETS_JP;
import static cn.garymb.ygomobile.Constants.ASSETS_KOR;
import static cn.garymb.ygomobile.Constants.ASSETS_PATH;
import static cn.garymb.ygomobile.Constants.BOT_CONF;
import static cn.garymb.ygomobile.Constants.CORE_BOT_CONF_PATH;
import static cn.garymb.ygomobile.Constants.CORE_LIMIT_PATH;
import static cn.garymb.ygomobile.Constants.CORE_SINGLE_PATH;
import static cn.garymb.ygomobile.Constants.CORE_STRING_PATH;
import static cn.garymb.ygomobile.Constants.CORE_SYSTEM_PATH;
import static cn.garymb.ygomobile.Constants.DATABASE_NAME;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.List;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.AppsSettings.languageEnum;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.plus.DialogPlus;
import cn.garymb.ygomobile.utils.FileUtils;
import cn.garymb.ygomobile.utils.IOUtils;
import cn.garymb.ygomobile.utils.LogUtil;
import cn.garymb.ygomobile.utils.SystemUtils;
import libwindbot.windbot.WindBot;
import ocgcore.CardManager;
import ocgcore.DataManager;

public class ResCheckTask extends AsyncTask<Void, Integer, Integer> {
    public static final int ERROR_NONE = 0;
    public static final int ERROR_CORE_CONFIG = -1;
    public static final int ERROR_COPY = -2;
    public static final int ERROR_CORE_CONFIG_LOST = -3;
    private static final String TAG = "ResCheckTask";
    private final AppsSettings mSettings;
    private final Context mContext;
    private final ResCheckListener mListener;
    private final Handler handler;
    protected int mError = ERROR_NONE;
    MessageReceiver mReceiver = new MessageReceiver();
    Handler han = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case 0:
                    checkWindbot();
                    break;
            }
        }
    };
    private DialogPlus dialog = null;
    private boolean isNewVersion;

    @SuppressWarnings("deprecation")
    public ResCheckTask(Context context, ResCheckListener listener) {
        mContext = context;
        mListener = listener;
        handler = new Handler(context.getMainLooper());
        mSettings = AppsSettings.get();
        checkWindbot();
    }

    public static String getDatapath(String path) {
        if (TextUtils.isEmpty(ASSETS_PATH)) {
            return path;
        }
        if (path.startsWith(ASSETS_PATH)) {
            return path;
        }
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        return ASSETS_PATH + path;
    }

    public static boolean checkDataBase(String path) {
        if (!new File(path).exists()) {
            return false;
        }
        SQLiteDatabase db = null;
        try {
            db = SQLiteDatabase.openDatabase(path, null,
                    SQLiteDatabase.OPEN_READWRITE);
            Cursor cursor = db.rawQuery("select * from datas,texts where datas.id=texts.id limit 1;", null);
            if (cursor == null) {
                return false;
            }
            cursor.close();
        } catch (Exception e) {
            return false;
        } finally {
            IOUtils.close(db);
        }
        return true;
    }

    public static void doSomeTrickOnDatabase(String myPath)
            throws SQLiteException {
        SQLiteDatabase db = null;
        db = SQLiteDatabase.openDatabase(myPath, null,
                SQLiteDatabase.OPEN_READWRITE);
        try {
            db.rawQuery("select * from datas where datas._id = 0;", null);
            db.close();
            return;
        } catch (Exception e) {

        }
        try {
            db.beginTransaction();
            db.execSQL("ALTER TABLE datas RENAME TO datas_backup;");
            db.execSQL("CREATE TABLE datas (_id integer PRIMARY KEY, ot integer, alias integer, setcode integer, type integer,"
                    + " atk integer, def integer, level integer, race integer, attribute integer, category integer);");
            db.execSQL("INSERT INTO datas (_id, ot, alias, setcode, type, atk, def, level, race, attribute, category) "
                    + "SELECT id, ot, alias, setcode, type, atk, def, level, race, attribute, category FROM datas_backup;");
            db.execSQL("DROP TABLE datas_backup;");
            db.execSQL("ALTER TABLE texts RENAME TO texts_backup;");
            db.execSQL("CREATE TABLE texts (_id integer PRIMARY KEY, name varchar(128), \"desc\" varchar(1024),"
                    + " str1 varchar(256), str2 varchar(256), str3 varchar(256), str4 varchar(256), str5 varchar(256),"
                    + " str6 varchar(256), str7 varchar(256), str8 varchar(256), str9 varchar(256), str10 varchar(256),"
                    + " str11 varchar(256), str12 varchar(256), str13 varchar(256), str14 varchar(256), str15 varchar(256), str16 varchar(256));");
            db.execSQL("INSERT INTO texts (_id, name, \"desc\", str1, str2, str3, str4, str5, str6, str7, str8, str9, str10, str11, str12, str13, str14, str15, str16)"
                    + " SELECT id, name, \"desc\", str1, str2, str3, str4, str5, str6, str7, str8, str9, str10, str11, str12, str13, str14, str15, str16 FROM texts_backup;");
            db.execSQL("DROP TABLE texts_backup;");
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        if (db != null) {
            db.close();
        }
    }

    public void unregisterMReceiver() {
        mContext.unregisterReceiver(mReceiver);
    }

    /**
     * 查询sharedPreference中的版本号和package的版本号，比较后得出是否需要更新，将结果存入isNewVersion。
     */
    @Override
    protected void onPreExecute() {
        super.onPreExecute();
        dialog = DialogPlus.show(mContext, null, mContext.getString(R.string.check_res));
        int vercode = SystemUtils.getVersion(mContext);
        if (mSettings.getAppVersion() < vercode) {//刚安装app时，mSettings.getAppVersion()返回值为0
            mSettings.setAppVersion(vercode);
            isNewVersion = true;
        } else {
            isNewVersion = false;
        }
    }

    @Override
    protected void onPostExecute(final Integer result) {
        super.onPostExecute(result);
        //关闭异常
        if (dialog.isShowing()) {
            try {
                dialog.dismiss();
            } catch (Exception e) {

            }
        }
        if (mListener != null) {
            mListener.onResCheckFinished(result, isNewVersion);
        }
    }

    private void setMessage(String msg) {
        handler.post(() -> {
            dialog.setMessage(msg);
        });
    }

    /**
     * 清除下载缓存
     * 如果是新安装ygomobile，则将资源文件从assets拷贝到游戏目录中。游戏目录为app-specific external storage
     *
     * @param params The parameters of the task.
     * @return
     */
    @Override
    protected Integer doInBackground(Void... params) {
        Log.d(TAG, "check start");
        boolean needsUpdate = isNewVersion;
        //清空下载缓存
        File imgDir = new File(mSettings.getCardImagePath());
        File[] files = imgDir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.endsWith(".tmp");
            }
        });
        if (files != null) {
            for (File file : files) {
                FileUtils.deleteFile(file);
            }
        }
        //core config
        setMessage(mContext.getString(R.string.check_things, mContext.getString(R.string.core_config)));
        //res
        try {
            String resPath = mSettings.getResourcePath();
            //创建游戏目录
            IOUtils.createNoMedia(resPath);
            //检查文件夹
            checkDirs();
            if (mSettings.isUseExtraCards()) {
                //自定义数据库无效，则用默认的
                if (!CardManager.checkDataBase(mSettings.getDatabaseFile())) {
                    mSettings.setUseExtraCards(false);
                }
            }
            //如果是新版本
            if (needsUpdate) {
                LogUtil.i(TAG, "needsUpdate");
                //复制卡组
                File deckFiles = new File(mSettings.getDeckDir());
                if (deckFiles.list().length == 0) {
                    setMessage(mContext.getString(R.string.check_things, mContext.getString(R.string.tip_new_deck)));
                    IOUtils.copyFilesFromAssets(mContext, getDatapath(Constants.CORE_DECK_PATH),
                            mSettings.getDeckDir(), needsUpdate);
                }
                //复制卡包
                File pack = new File(AppsSettings.get().getPackDeckDir());
                File[] subYdks = pack.listFiles();
                for (File packs : subYdks) {
                    packs.delete();
                }
                IOUtils.copyFilesFromAssets(mContext, getDatapath(Constants.CORE_PACK_PATH),
                        AppsSettings.get().getPackDeckDir(), needsUpdate);
            }
            String[] sound1 = mContext.getAssets().list(getDatapath(Constants.CORE_SOUND_PATH));
            String[] sound2 = new File(mSettings.getSoundPath()).list();

            String[] textures1 = mContext.getAssets().list(getDatapath(Constants.CORE_SKIN_PATH));
            String[] textures2 = new File(mSettings.getCoreSkinPath()).list();

            //复制资源文件夹
            //如果sound文件夹不存在/sound资源数量不够/是更新则复制,但是不强制复制
            if (sound2 == null || (sound1 != null && sound1.length > sound2.length) || needsUpdate) {
                setMessage(mContext.getString(R.string.check_things, mContext.getString(R.string.game_sound)));
                IOUtils.copyFilesFromAssets(mContext, getDatapath(Constants.CORE_SOUND_PATH),
                        mSettings.getSoundPath(), false);
            }
            //如果textures文件夹不存在/textures资源数量不够/是更新则复制,但是不强制复制
            if (textures2 == null || (textures1 != null && textures1.length > textures2.length) || needsUpdate) {
                setMessage(mContext.getString(R.string.check_things, mContext.getString(R.string.game_skins)));
                IOUtils.copyFilesFromAssets(mContext, getDatapath(Constants.CORE_SKIN_PATH),
                        mSettings.getCoreSkinPath(), false);
            }
            //复制字体
            setMessage(mContext.getString(R.string.check_things, mContext.getString(R.string.font_files)));
            IOUtils.copyFilesFromAssets(mContext, getDatapath(Constants.FONT_DIRECTORY),
                    mSettings.getFontDirPath(), needsUpdate);
            //复制脚本压缩包
            if (IOUtils.hasAssets(mContext, getDatapath(Constants.CORE_SCRIPTS_ZIP))) {
                setMessage(mContext.getString(R.string.check_things, mContext.getString(R.string.scripts)));
                IOUtils.copyFilesFromAssets(mContext, getDatapath(Constants.CORE_SCRIPTS_ZIP),
                        resPath, needsUpdate);
            }
            //复制卡图压缩包
            if (IOUtils.hasAssets(mContext, getDatapath(Constants.CORE_PICS_ZIP))) {
                setMessage(mContext.getString(R.string.check_things, mContext.getString(R.string.images)));
                IOUtils.copyFilesFromAssets(mContext, getDatapath(Constants.CORE_PICS_ZIP),
                        resPath, needsUpdate);
            }
            //复制人机资源
            IOUtils.copyFilesFromAssets(mContext, getDatapath(Constants.WINDBOT_PATH), mSettings.getResourcePath(), needsUpdate);
            //根据系统语言复制特定资料文件
            String language = mContext.getResources().getConfiguration().locale.getLanguage();
            if (!language.isEmpty()) {
                if (mSettings.getDataLanguage() == -1) {
                    if (language.equals(languageEnum.Chinese.name)) {
                        copyCnData(true);
                    } else if (language.equals(languageEnum.Korean.name)) {
                        copyKorData(true);
                    } else if (language.equals(languageEnum.Spanish.name)) {
                        copyEsData(true);
                    } else if (language.equals(languageEnum.Japanese)){
                        copyJpData(true);
                    } else {
                        copyEnData(true);
                    }
                } else {
                    if (mSettings.getDataLanguage() == languageEnum.Chinese.code) copyCnData(true);
                    if (mSettings.getDataLanguage() == languageEnum.Korean.code) copyKorData(true);
                    if (mSettings.getDataLanguage() == languageEnum.English.code) copyEnData(true);
                    if (mSettings.getDataLanguage() == languageEnum.Spanish.code) copyEsData(true);
                    if (mSettings.getDataLanguage() == languageEnum.Japanese.code) copyJpData(true);
                }
            }
            han.sendEmptyMessage(0);

            loadData();
        } catch (Exception e) {
            Log.e(TAG, "ERROR COPY", e);
            return ERROR_COPY;
        }
        return ERROR_NONE;
    }

    public int copyCnData(Boolean needsUpdate) throws IOException {
        //复制数据库
        copyCdbFile(getDatapath(DATABASE_NAME), needsUpdate);
        //复制残局
        setMessage(mContext.getString(R.string.check_things, mContext.getString(R.string.single_lua)));
        IOUtils.copyFilesFromAssets(mContext, getDatapath(CORE_SINGLE_PATH), mSettings.getSingleDir(), needsUpdate);
        //复制游戏配置文件
        copyCoreConfig(getDatapath("conf") + "/" + CORE_STRING_PATH,
                getDatapath("conf") + "/" + BOT_CONF,
                mSettings.getResourcePath(), needsUpdate);
        mSettings.setDataLanguage(languageEnum.Chinese.code);
        return ERROR_NONE;
    }

    public int copyEnData(Boolean needsUpdate) throws IOException {
        String enStringConf = ASSETS_EN + getDatapath("conf") + "/" + CORE_STRING_PATH;
        String enBotConf = ASSETS_EN + getDatapath("conf") + "/" + CORE_BOT_CONF_PATH;
        String enCdb = ASSETS_EN + getDatapath(DATABASE_NAME);
        String enSingle = ASSETS_EN + getDatapath(CORE_SINGLE_PATH);
        //复制数据库
        copyCdbFile(enCdb, true);
        //复制残局
        setMessage(mContext.getString(R.string.check_things, mContext.getString(R.string.single_lua)));
        IOUtils.copyFilesFromAssets(mContext, enSingle, mSettings.getSingleDir(), needsUpdate);
        //复制游戏配置文件
        copyCoreConfig(enStringConf, enBotConf, mSettings.getResourcePath(), needsUpdate);
        mSettings.setDataLanguage(languageEnum.English.code);
        return ERROR_NONE;
    }

    public int copyKorData(Boolean needsUpdate) throws IOException {
        String korStringConf = ASSETS_KOR + getDatapath("conf") + "/" + CORE_STRING_PATH;
        String korBotConf = ASSETS_KOR + getDatapath("conf") + "/" + CORE_BOT_CONF_PATH;
        String korCdb = ASSETS_KOR + getDatapath(DATABASE_NAME);
        String korSingle = ASSETS_EN + getDatapath(CORE_SINGLE_PATH);
        //复制数据库
        copyCdbFile(korCdb, true);
        //复制残局
        setMessage(mContext.getString(R.string.check_things, mContext.getString(R.string.single_lua)));
        IOUtils.copyFilesFromAssets(mContext, korSingle, mSettings.getSingleDir(), needsUpdate);
        //复制游戏配置文件
        copyCoreConfig(korStringConf, korBotConf, mSettings.getResourcePath(), needsUpdate);
        mSettings.setDataLanguage(languageEnum.Korean.code);
        return ERROR_NONE;
    }

    public int copyEsData(Boolean needsUpdate) throws IOException {
        String esStringConf = ASSETS_ES + getDatapath("conf") + "/" + CORE_STRING_PATH;
        String esBotConf = ASSETS_ES + getDatapath("conf") + "/" + CORE_BOT_CONF_PATH;
        String esCdb = ASSETS_ES + getDatapath(DATABASE_NAME);
        String enSingle = ASSETS_EN + getDatapath(CORE_SINGLE_PATH);
        //复制数据库
        copyCdbFile(esCdb, true);
        //复制残局
        setMessage(mContext.getString(R.string.check_things, mContext.getString(R.string.single_lua)));
        IOUtils.copyFilesFromAssets(mContext, enSingle, mSettings.getSingleDir(), needsUpdate);
        //复制游戏配置文件
        copyCoreConfig(esStringConf, esBotConf, mSettings.getResourcePath(), needsUpdate);
        mSettings.setDataLanguage(languageEnum.Spanish.code);
        return ERROR_NONE;
    }

    public int copyJpData(Boolean needsUpdate) throws IOException {
        String jpStringConf = ASSETS_JP + getDatapath("conf") + "/" + CORE_STRING_PATH;
        String jpBotConf = ASSETS_JP + getDatapath("conf") + "/" + CORE_BOT_CONF_PATH;
        String jpCdb = ASSETS_JP + getDatapath(DATABASE_NAME);
        String enSingle = ASSETS_EN + getDatapath(CORE_SINGLE_PATH);
        //复制数据库
        copyCdbFile(jpCdb, true);
        //复制残局
        setMessage(mContext.getString(R.string.check_things, mContext.getString(R.string.single_lua)));
        IOUtils.copyFilesFromAssets(mContext, enSingle, mSettings.getSingleDir(), needsUpdate);
        //复制游戏配置文件
        copyCoreConfig(jpStringConf, jpBotConf, mSettings.getResourcePath(), needsUpdate);
        mSettings.setDataLanguage(languageEnum.Spanish.code);
        mSettings.setDataLanguage(languageEnum.Japanese.code);
        return ERROR_NONE;
    }

    private void loadData() {
        setMessage(mContext.getString(R.string.loading));
        DataManager.get().load(false);
    }

    void copyCdbFile(String assetPath, boolean needsUpdate) throws IOException {
        File dbFile = new File(mSettings.getDataBasePath(), DATABASE_NAME);
        //如果数据库存在
        if (dbFile.exists()) {
            //如果是更新或者数据库大小小于1m
            if (needsUpdate || dbFile.length() < 1024 * 1024)
                dbFile.delete();
            else
                return;
        }
        setMessage(mContext.getString(R.string.check_things, mContext.getString(R.string.cards_cdb)));
        IOUtils.copyFilesFromAssets(mContext, assetPath, mSettings.getDataBasePath(), needsUpdate);
    }

    private void checkDirs() {
        String[] dirs = {
                //脚本文件夹
                Constants.CORE_SCRIPT_PATH,
                //残局文件夹
                CORE_SINGLE_PATH,
                //卡组文件夹
                Constants.CORE_DECK_PATH,
                //pack文件夹
                Constants.CORE_PACK_PATH,
                //录像文件夹
                Constants.CORE_REPLAY_PATH,
                //字体文件夹
                Constants.FONT_DIRECTORY,
                //资源文件夹
                Constants.CORE_SKIN_PATH,
                //卡图文件夹
                Constants.CORE_IMAGE_PATH,
                //log文件夹
                Constants.MOBILE_LOG,
                //卡组分享截图文件夹
                Constants.MOBILE_DECK_SHARE,
                //额外卡库文件夹
                Constants.CORE_EXPANSIONS,
                //人机资源文件夹
                Constants.WINDBOT_PATH
        };
        File dirFile = null;
        for (String dir : dirs) {
            dirFile = new File(mSettings.getResourcePath(), dir);
            IOUtils.createFolder(dirFile);
        }
    }

    private String getCurVersion(File verPath) {
        if (!verPath.exists()) {
            Log.e(TAG, "check core config no exists " + verPath);
            return null;
        }
        String[] files = verPath.list();
        if (files == null) {
            return null;
        }
        for (String file : files) {
            File f = new File(verPath, file);
            if (f.isDirectory()) {
                return f.getName();
            } else {
                Log.e(TAG, "check core config is file " + f.getAbsolutePath());
            }
        }
        return null;
    }

    private int copyCoreConfig(String assetStringPath, String assetBotPath, String toPath, boolean needsUpdate) throws IOException {
        File systemfile = new File(mSettings.getResourcePath(), CORE_SYSTEM_PATH);
        File stringfile = new File(mSettings.getResourcePath(), CORE_STRING_PATH);
        File botfile = new File(mSettings.getResourcePath(), BOT_CONF);
        if (!systemfile.exists()) {
            IOUtils.copyFilesFromAssets(mContext, getDatapath("conf") + "/" + CORE_SYSTEM_PATH, toPath, false);
        }
        IOUtils.copyFilesFromAssets(mContext, getDatapath("conf") + "/" + CORE_LIMIT_PATH, toPath, needsUpdate);
        IOUtils.copyFilesFromAssets(mContext, assetStringPath, toPath, needsUpdate);
        IOUtils.copyFilesFromAssets(mContext, assetBotPath, toPath, needsUpdate);
        //替换换行符
        fixString(stringfile.getAbsolutePath());
        fixString(botfile.getAbsolutePath());
        return ERROR_NONE;
    }

    private void fixString(String stringfile) {
        List<String> lines = FileUtils.readLines(stringfile, Constants.DEF_ENCODING);
        FileUtils.writeLines(stringfile, lines, Constants.DEF_ENCODING, "\n");
    }

    public void checkWindbot() {
        Log.i("路径", mContext.getFilesDir().getPath());
        Log.i("路径2", mSettings.getDataBasePath() + "/" + DATABASE_NAME);
        try {
            WindBot.initAndroid(mSettings.getResourcePath(),
                    mSettings.getDataBasePath() + "/" + DATABASE_NAME,
                    mSettings.getResourcePath() + "/" + CORE_BOT_CONF_PATH);
        } catch (Throwable e) {
            e.printStackTrace();
        }
        IntentFilter filter = new IntentFilter();
        filter.addAction("RUN_WINDBOT");
        mContext.registerReceiver(mReceiver, filter);
    }

    public interface ResCheckListener {
        void onResCheckFinished(int result, boolean isNewVersion);
    }

    public class MessageReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals("RUN_WINDBOT")) {
                String args = intent.getStringExtra("args");
                WindBot.runAndroid(args);
            }
        }
    }
}
