package cn.garymb.ygomobile.ui.home;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.util.Log;

import androidx.annotation.StringRes;

import org.jdeferred.DeferredCallable;
import org.jdeferred.DeferredFutureTask;
import org.jdeferred.android.DeferredAsyncTask;

import java.io.File;
import java.util.List;

import cn.garymb.ygomobile.AppsSettings;
import cn.garymb.ygomobile.Constants;
import cn.garymb.ygomobile.lite.R;
import cn.garymb.ygomobile.ui.plus.DialogPlus;
import cn.garymb.ygomobile.ui.plus.VUiKit;
import cn.garymb.ygomobile.utils.FileUtils;
import cn.garymb.ygomobile.utils.IOUtils;
import cn.garymb.ygomobile.utils.SystemUtils;
import ocgcore.CardManager;
import ocgcore.ConfigManager;
import ocgcore.DataManager;

import static cn.garymb.ygomobile.Constants.DATABASE_NAME;
import static cn.garymb.ygomobile.Constants.DEBUG;

public class ResCheckTask extends DeferredCallable<Integer, String> {
    private static final String TAG = "ResCheckTask";
    public static final int ERROR_NONE = 0;
    public static final int ERROR_CORE_CONFIG = -1;
    public static final int ERROR_COPY = -2;
    public static final int ERROR_CORE_CONFIG_LOST = -3;
    public static final int ERROR_CORE_OTHER = -4;

    private AppsSettings mSettings;
    @SuppressLint("StaticFieldLeak")
    private final Activity mActivity;
    private boolean isNewVersion;
    private volatile int mProgress;
    private long minTime;

    public ResCheckTask(Activity context, boolean isNewVersion, long minTime) {
        mActivity = context;
        mSettings = AppsSettings.get();
        this.isNewVersion = isNewVersion;
        this.minTime = minTime;
    }

    public int getProgress() {
        return mProgress;
    }

    private String getString(@StringRes int resId) {
        return mActivity.getString(resId);
    }

    private String getString(@StringRes int resId, Object... formatArgs) {
        return mActivity.getString(resId, formatArgs);
    }

    private void setMessage(String msg) {
        notify(msg);
    }

    @Override
    public Integer call() {
        if (Constants.DEBUG)
            Log.d(TAG, "check start");
        long time = System.currentTimeMillis();
        boolean needsUpdate = isNewVersion;
        mProgress = 0;
        //core config
        setMessage(getString(R.string.check_things, getString(R.string.core_config)));
        int error = ERROR_NONE;
        //res
        try {
            AssetManager assetManager = mActivity.getAssets();
            String resPath = mSettings.getResourcePath();
            if (Constants.DEBUG)
                Log.d(TAG, "createNoMedia");
            //创建游戏目录
            IOUtils.createNoMedia(resPath);
            mProgress = 1;
            if (Constants.DEBUG)
                Log.d(TAG, "checkDirs");
            //检查文件夹
            checkDirs();
            mProgress = 3;
            if (Constants.DEBUG)
                Log.d(TAG, "copyCoreConfig");
            //复制游戏配置文件
            int err = copyCoreConfig(assetManager, resPath, needsUpdate);
            if (err == ERROR_NONE) {
                mProgress = 5;
                if (AppsSettings.get().isUseExtraCards()) {
                    if (Constants.DEBUG)
                        Log.d(TAG, "setUseExtraCards");
                    //自定义数据库无效，则用默认的
                    if (!CardManager.checkDataBase(AppsSettings.get().getDataBaseFile())) {
                        AppsSettings.get().setUseExtraCards(false);
                    }
                }
                mProgress = 10;
                //设置字体
                ConfigManager systemConf = DataManager.openConfig(mSettings.getSystemConfig());
                systemConf.setFontSize(mSettings.getFontSize());
                if (Constants.DEBUG)
                    Log.d(TAG, "setFontSize:" + mSettings.getFontSize());
                systemConf.close();
                mProgress = 20;
                //如果是新版本
                if (needsUpdate) {
                    if (Constants.DEBUG)
                        Log.d(TAG, "copy ydk/pack/single");
                    //复制卡组
                    setMessage(getString(R.string.check_things, getString(R.string.tip_new_deck)));
                    IOUtils.copyFolder(assetManager, Constants.ASSET_DECK_DIR_PATH,
                            mSettings.getDeckDir(), needsUpdate);
                    //复制卡包
                    IOUtils.copyFolder(assetManager, Constants.ASSET_PACK_DIR_PATH,
                            mSettings.get().getPackDeckDir(), needsUpdate);
                    //复制残局
                    setMessage(getString(R.string.check_things, getString(R.string.single_lua)));
                    IOUtils.copyFolder(assetManager, Constants.ASSET_SINGLE_DIR_PATH,
                            mSettings.getSingleDir(), needsUpdate);
                }
                mProgress = 30;
                String[] textures1 = mActivity.getAssets().list(Constants.ASSET_SKIN_DIR_PATH);
                String[] textures2 = new File(mSettings.getCoreSkinPath()).list();

                //复制资源文件夹
                //如果textures文件夹不存在/textures资源数量不够/是更新则复制,但是不强制复制
                if (textures2 == null || (textures1 != null && textures1.length > textures2.length) || needsUpdate) {
                    if (Constants.DEBUG)
                        Log.d(TAG, "copy skin");
                    setMessage(getString(R.string.check_things, getString(R.string.game_skins)));
                    IOUtils.copyFolder(assetManager, Constants.ASSET_SKIN_DIR_PATH,
                            mSettings.getCoreSkinPath(), false);//防止覆盖用户的卡背
                }
                mProgress = 40;
                if (Constants.DEBUG)
                    Log.d(TAG, "copy font");
                //复制字体
                setMessage(getString(R.string.check_things, getString(R.string.font_files)));
                IOUtils.copyFolder(assetManager, Constants.ASSET_FONTS_DIR_PATH,
                        mSettings.getFontDirPath(), needsUpdate);
                mProgress = 50;
                //复制脚本压缩包
                if (IOUtils.hasAssets(assetManager, Constants.ASSET_SCRIPTS_FILE_PATH)) {
                    if (Constants.DEBUG)
                        Log.d(TAG, "copy scripts.zip");
                    setMessage(getString(R.string.check_things, getString(R.string.scripts)));
                    IOUtils.copyFile(assetManager, Constants.ASSET_SCRIPTS_FILE_PATH,
                            new File(resPath, Constants.CORE_SCRIPTS_ZIP), needsUpdate);
                }
                mProgress = 60;
                //复制数据库
                copyCdbFile(assetManager, needsUpdate);
                //复制卡图压缩包
                if (IOUtils.hasAssets(assetManager, Constants.ASSET_PICS_FILE_PATH)) {
                    if (Constants.DEBUG)
                        Log.d(TAG, "copy pics.zip");
                    setMessage(getString(R.string.check_things, getString(R.string.images)));
                    IOUtils.copyFile(assetManager, Constants.ASSET_PICS_FILE_PATH,
                            new File(resPath, Constants.CORE_PICS_ZIP), needsUpdate);
                }
                mProgress = 80;
                if (Constants.DEBUG)
                    Log.d(TAG, "copy windbot");
                //复制人机资源
                IOUtils.copyFolder(assetManager, Constants.ASSET_WINDBOT_DECK_DIR_PATH,
                        new File(resPath, Constants.LIB_WINDBOT_DECK_PATH).getPath(), needsUpdate);
                IOUtils.copyFolder(assetManager, Constants.ASSET_WINDBOT_DIALOG_DIR_PATH,
                        new File(resPath, Constants.LIB_WINDBOT_DIALOG_PATH).getPath(), needsUpdate);
                mProgress = 90;
                loadData();
                mProgress = 99;
            }
        } catch (Exception e) {
            if (Constants.DEBUG)
                Log.e(TAG, "check", e);
            error = ERROR_COPY;
        }
        long tc = (minTime -(System.currentTimeMillis() - time));
        if(tc >= 0){
            try {
                Thread.sleep(tc);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return error;
    }

    private void loadData() {
        setMessage(getString(R.string.loading));
        if (Constants.DEBUG)
            Log.d(TAG, "loadData");
        //复制人机资源
        DataManager.get().load(false);
    }

    private void copyCdbFile(AssetManager mgr, boolean needsUpdate) {
        File dbFile = new File(mSettings.getDataBasePath(), DATABASE_NAME);
        //如果数据库存在
        if (dbFile.exists()) {
            //如果是更新或者数据库大小小于1m
            if (needsUpdate || dbFile.length() < 1024 * 1024)
                dbFile.delete();
            else
                return;
        }
        setMessage(mActivity.getString(R.string.check_things, mActivity.getString(R.string.cards_cdb)));
        IOUtils.copyFile(mgr, Constants.ASSET_CARDS_CDB_FILE_PATH, dbFile, needsUpdate);
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

    private void checkDirs() {
        String[] dirs = {
                //脚本文件夹
                Constants.CORE_SCRIPT_PATH,
                //残局文件夹
                Constants.CORE_SINGLE_PATH,
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
            if (!dirFile.exists()) {
                dirFile.mkdirs();
            }
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

    private int copyCoreConfig(AssetManager assetManager, String toPath, boolean needsUpdate) {
        try {
            int count = IOUtils.copyFolder(assetManager, Constants.ASSET_CONF_PATH, toPath, needsUpdate);
            if (count < 3) {
                return ERROR_CORE_CONFIG_LOST;
            }
            //替换换行符
            File stringfile = new File(AppsSettings.get().getResourcePath(), Constants.CORE_STRING_PATH);
            File botfile = new File(AppsSettings.get().getResourcePath(), Constants.BOT_CONF);
            fixString(stringfile.getAbsolutePath());
            fixString(botfile.getAbsolutePath());
            return ERROR_NONE;
        } catch (Throwable e) {
            if (Constants.DEBUG)
                Log.e(TAG, "copy", e);
            return ERROR_COPY;
        }
    }

    private void fixString(String stringfile) {
        String encoding = "utf-8";
        List<String> lines = FileUtils.readLines(stringfile, encoding);
        FileUtils.writeLines(stringfile, lines, encoding, "\n");
    }

    public interface Callback {
        void onCompleted(int result, boolean isNewVersion);
    }
}
